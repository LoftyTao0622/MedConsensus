package com.zyt.medconsensus.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.agent.CollectorAgent;
import com.zyt.medconsensus.agent.DiagnosisAgent;
import com.zyt.medconsensus.agent.ReviewerAgent;
import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.ConsultationResponse;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.DoctorReviewRequest;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.MessageHistoryDto;
import com.zyt.medconsensus.dto.PipelineEvent;
import com.zyt.medconsensus.dto.SessionDetailResponse;
import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import com.zyt.medconsensus.graph.DiagnosisGraphState;
import com.zyt.medconsensus.graphkg.MedicalGraphPath;
import com.zyt.medconsensus.graphkg.MedicalGraphReasoningService;
import com.zyt.medconsensus.llm.AiWorkflowProperties;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.mapper.FinalDiagnosisRecordMapper;
import com.zyt.medconsensus.observability.LangSmithTracingService;
import com.zyt.medconsensus.service.CollectorAgentService;
import com.zyt.medconsensus.tool.MedicalWorkflowTools;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.springframework.dao.DataAccessException;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CollectorAgentServiceImpl implements CollectorAgentService {

    private static final String NODE_COLLECT = "collect";
    private static final String NODE_ASSESS = "assess";
    private static final String NODE_ASK_MORE = "ask_more_info";
    private static final String NODE_DIAGNOSE = "diagnose";
    private static final String NODE_REVIEW = "review";
    private static final String NODE_DECIDE = "decide";
    private static final String NODE_HUMAN = "human_review";
    private static final String NODE_FINALIZE = "finalize";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final TypeReference<List<SessionSnapshot>> SESSION_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<MessageSnapshot>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<DiagnosticResponse> DIAGNOSIS_TYPE = new TypeReference<>() {
    };

    private final AiWorkflowProperties properties;
    private final MultiModelGateway modelGateway;
    private final MedicalWorkflowTools tools;
    private final CollectorAgent collectorAgent;
    private final DiagnosisAgent diagnosisAgent;
    private final ReviewerAgent reviewerAgent;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final FinalDiagnosisRecordMapper finalDiagnosisRecordMapper;
    private final LangSmithTracingService tracingService;
    private final MedicalGraphReasoningService graphReasoningService;
    private final CompiledGraph<DiagnosisGraphState> workflow;

    public CollectorAgentServiceImpl(
            AiWorkflowProperties properties,
            MultiModelGateway modelGateway,
            MedicalWorkflowTools tools,
            CollectorAgent collectorAgent,
            DiagnosisAgent diagnosisAgent,
            ReviewerAgent reviewerAgent,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            SimpMessagingTemplate messagingTemplate,
            FinalDiagnosisRecordMapper finalDiagnosisRecordMapper,
            LangSmithTracingService tracingService,
            MedicalGraphReasoningService graphReasoningService
    ) {
        this.properties = properties;
        this.modelGateway = modelGateway;
        this.tools = tools;
        this.collectorAgent = collectorAgent;
        this.diagnosisAgent = diagnosisAgent;
        this.reviewerAgent = reviewerAgent;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.messagingTemplate = messagingTemplate;
        this.finalDiagnosisRecordMapper = finalDiagnosisRecordMapper;
        this.tracingService = tracingService;
        this.graphReasoningService = graphReasoningService;
        this.workflow = compileWorkflow();
        // One normal pass already visits multiple nodes; allow room for a retry_collect round-trip.
        this.workflow.setMaxIterations(16);
    }

    @Override
    public ConsultationResponse organize(Long userId, String sessionId, String userMessage) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }
        if (!StringUtils.hasText(userMessage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入咨询内容");
        }

        String resolvedSessionId = StringUtils.hasText(sessionId) ? sessionId : newSessionId();
        List<MessageSnapshot> history = loadMessageSnapshots(userId, resolvedSessionId);
        history.add(new MessageSnapshot("user", userMessage.trim()));
        saveMessageSnapshots(userId, resolvedSessionId, history);

        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put("userId", userId);
        initialState.put("sessionId", resolvedSessionId);
        initialState.put("userMessage", userMessage.trim());
        initialState.put("memory", history.stream().map(MessageSnapshot::content).toList());
        initialState.put("retryCount", 0);

        DiagnosisGraphState result = tracingService.traceWorkflow(
                "MedConsenus Diagnosis Workflow",
                userId,
                resolvedSessionId,
                userMessage,
                () -> workflow.invoke(initialState)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "工作流执行失败"))
        );

        SessionSnapshot sessionSnapshot = upsertSession(
                userId,
                resolvedSessionId,
                result.value("title", summarizeTitle(userMessage)),
                result.value("sessionStatus", "病情整理中"),
                LocalDateTime.now().format(TIME_FORMATTER)
        );

        String assistantMessage = result.value("finalConclusion", "已完成病情整理。");
        history.add(new MessageSnapshot("assistant", assistantMessage));
        saveMessageSnapshots(userId, resolvedSessionId, history);

        List<String> analysis = stringListValue(result, "structuredAnalysis");
        List<String> suggestions = stringListValue(result, "suggestions");
        List<DiagnosticResponse.ReviewerScore> reviewers = reviewerScoresForResponse(result, "reviewers");

        DiagnosticResponse diagnosis = new DiagnosticResponse(
                result.value("finalConclusion", assistantMessage),
                result.value("finalConfidence", 0.5d),
                result.value("riskLevel", "待评估"),
                analysis,
                suggestions,
                reviewers
        );
        saveDiagnosisSnapshot(userId, resolvedSessionId, diagnosis);

        return new ConsultationResponse(
                resolvedSessionId,
                result.value("chiefComplaint", userMessage.trim()),
                new ChatSessionDto(
                        sessionSnapshot.id(),
                        sessionSnapshot.title(),
                        sessionSnapshot.status(),
                        sessionSnapshot.updatedAt()
                ),
                diagnosis,
                history.stream().map(MessageSnapshot::content).toList()
        );
    }

    @Override
    public List<ChatSessionDto> loadSessions(Long userId) {
        if (userId == null) {
            return List.of();
        }

        return loadSessionSnapshots(userId).stream()
                .map(item -> new ChatSessionDto(item.id(), item.title(), item.status(), item.updatedAt()))
                .toList();
    }

    @Override
    public List<String> loadMemory(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return List.of();
        }

        return loadMessageSnapshots(userId, sessionId).stream()
                .map(message -> message.role().toUpperCase() + ": " + message.content())
                .toList();
    }

    @Override
    public DiagnosticResponse loadLatestDiagnosis(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }

        return loadSessionSnapshots(userId).stream()
                .map(session -> loadDiagnosisSnapshot(userId, session.id()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElseGet(this::emptyDiagnosis);
    }

    @Override
    public SessionDetailResponse loadSessionDetail(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话不存在");
        }

        SessionSnapshot snapshot = loadSessionSnapshots(userId).stream()
                .filter(item -> item.id().equals(sessionId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到会话"));

        List<MessageHistoryDto> history = loadMessageSnapshots(userId, sessionId).stream()
                .map(item -> new MessageHistoryDto(item.role(), item.content()))
                .toList();

        DiagnosticResponse diagnosis = loadDiagnosisSnapshot(userId, sessionId);
        FinalDiagnosisRecordDto finalRecord = finalDiagnosisRecordMapper.findByUserIdAndSessionId(userId, sessionId)
                .map(this::toFinalRecordDto)
                .orElse(null);

        return new SessionDetailResponse(
                snapshot.id(),
                snapshot.title(),
                snapshot.status(),
                snapshot.updatedAt(),
                diagnosis,
                history,
                finalRecord
        );
    }

    @Override
    public FinalDiagnosisRecordDto saveDoctorReview(Long userId, DoctorReviewRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }
        if (!StringUtils.hasText(request.getSessionId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少会话标识");
        }

        FinalDiagnosisRecord record = finalDiagnosisRecordMapper.findByUserIdAndSessionId(userId, request.getSessionId())
                .orElseGet(FinalDiagnosisRecord::new);

        record.setUserId(userId);
        record.setSessionId(request.getSessionId());
        record.setChiefComplaint(request.getChiefComplaint());
        record.setAiConclusion(request.getAiConclusion());
        record.setDoctorOpinion(blankToNull(request.getOpinion()));
        record.setFinalConclusion(
                StringUtils.hasText(request.getOpinion()) ? request.getOpinion().trim() : request.getAiConclusion()
        );
        record.setRiskLevel(request.getRiskLevel());
        record.setConfidence(request.getConfidence());
        record.setReviewStatus(StringUtils.hasText(request.getOpinion()) ? "DOCTOR_OVERRIDDEN" : "AI_CONFIRMED");

        FinalDiagnosisRecord saved = finalDiagnosisRecordMapper.save(record);
        upsertSession(
                userId,
                request.getSessionId(),
                StringUtils.hasText(request.getChiefComplaint()) ? summarizeTitle(request.getChiefComplaint()) : "已完成诊断",
                "已形成最终结论",
                LocalDateTime.now().format(TIME_FORMATTER)
        );
        return toFinalRecordDto(saved);
    }

    @Override
    public void deleteSession(Long userId, String sessionId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }
        if (!StringUtils.hasText(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少会话标识");
        }

        List<SessionSnapshot> sessions = new ArrayList<>(loadSessionSnapshots(userId));
        boolean removed = sessions.removeIf(session -> session.id().equals(sessionId));
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到会话");
        }

        if (sessions.isEmpty()) {
            redisTemplate.delete(sessionKey(userId));
        } else {
            writeJson(sessionKey(userId), sessions);
        }

        try {
            deleteRedisSessionArtifacts(userId, sessionId);
        } catch (DataAccessException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 会话删除失败");
        }

        finalDiagnosisRecordMapper.deleteByUserIdAndSessionId(userId, sessionId);
    }

    private CompiledGraph<DiagnosisGraphState> compileWorkflow() {
        try {
            StateGraph<DiagnosisGraphState> graph = new StateGraph<>(DiagnosisGraphState::new);

            graph.addNode(NODE_COLLECT, AsyncNodeAction.node_async(this::collectNode));
            graph.addNode(NODE_ASSESS, AsyncNodeAction.node_async(this::assessNode));
            graph.addNode(NODE_ASK_MORE, AsyncNodeAction.node_async(this::askMoreInfoNode));
            graph.addNode(NODE_DIAGNOSE, AsyncNodeAction.node_async(this::diagnoseNode));
            graph.addNode(NODE_REVIEW, AsyncNodeAction.node_async(this::reviewNode));
            graph.addNode(NODE_DECIDE, AsyncNodeAction.node_async(this::decisionNode));
            graph.addNode(NODE_HUMAN, AsyncNodeAction.node_async(this::humanReviewNode));
            graph.addNode(NODE_FINALIZE, AsyncNodeAction.node_async(this::finalizeNode));

            graph.addEdge(StateGraph.START, NODE_COLLECT);
            graph.addEdge(NODE_COLLECT, NODE_ASSESS);
            graph.addConditionalEdges(
                    NODE_ASSESS,
                    AsyncEdgeAction.edge_async(state -> state.value("assessmentRoute", "more_info")),
                    Map.of(
                            "more_info", NODE_ASK_MORE,
                            "sufficient", NODE_DIAGNOSE
                    )
            );
            graph.addEdge(NODE_DIAGNOSE, NODE_REVIEW);
            graph.addEdge(NODE_REVIEW, NODE_DECIDE);
            graph.addConditionalEdges(
                    NODE_DECIDE,
                    AsyncEdgeAction.edge_async(state -> state.value("decisionRoute", "human_review")),
                    Map.of(
                            "retry_collect", NODE_COLLECT,
                            "human_review", NODE_HUMAN,
                            "finalize", NODE_FINALIZE
                    )
            );
            graph.addEdge(NODE_ASK_MORE, StateGraph.END);
            graph.addEdge(NODE_HUMAN, StateGraph.END);
            graph.addEdge(NODE_FINALIZE, StateGraph.END);

            return graph.compile();
        } catch (Exception exception) {
            throw new IllegalStateException("无法编译 Diagnosis LangGraph 工作流", exception);
        }
    }

    private Map<String, Object> collectNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_COLLECT, state, () -> {
            emit("COLLECTOR", "信息收集/病情整理 Agent 正在归纳用户输入与会话记忆", 12);

            String latestInput = state.userMessage();
            List<String> memory = stringListValue(state, "memory");

            CollectorAgent.CollectorOutcome result = collectorAgent.collect(
                    collectorSpec(),
                    latestInput,
                    memory
            );

            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("title", result.title());
            updates.put("chiefComplaint", result.chiefComplaint());
            updates.put("collectorSummary", result.summary());
            updates.put("structuredAnalysis", result.structuredAnalysis());
            updates.put("followUpQuestions", result.followUpQuestions());
            updates.put("riskLevel", tools.assessRisk(String.join(" ", memory)).riskLevel());
            return updates;
        });
    }

    private Map<String, Object> assessNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_ASSESS, state, () -> {
            emit("SUFFICIENCY", "正在判断当前病情信息是否足以进入初步诊断", 25);

            MedicalWorkflowTools.SufficiencyResult result =
                    tools.assessInformationSufficiency(state.userMessage(), state.memory());

            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("sufficient", result.sufficient());
            updates.put("assessmentRoute", result.sufficient() ? "sufficient" : "more_info");
            updates.put("missingItems", result.missingItems());
            updates.put("informationSufficiencyAnalysis", informationSufficiencyAnalysis(result));
            return updates;
        });
    }

    private Map<String, Object> askMoreInfoNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_ASK_MORE, state, () -> {
            emit("COLLECTOR", "当前信息仍不充分，系统准备回到信息收集 Agent 继续追问", 40);

            List<String> followUp = stringListValue(state, "followUpQuestions");
            List<String> missingItems = stringListValue(state, "missingItems");

            List<String> mergedSuggestions = new ArrayList<>(followUp);
            if (mergedSuggestions.isEmpty()) {
                mergedSuggestions.addAll(missingItems);
            }

            List<String> structuredAnalysis = new ArrayList<>(
                    stringListValue(state, "informationSufficiencyAnalysis")
            );
            structuredAnalysis.addAll(stringListValue(state, "structuredAnalysis"));

            return Map.of(
                    "sessionStatus", "待补充信息",
                    "finalConclusion", "当前信息尚不足以进入稳定诊断，请继续补充病情依据后再次整理。",
                    "structuredAnalysis", structuredAnalysis,
                    "suggestions", mergedSuggestions,
                    "finalConfidence", 0.42d,
                    "riskLevel", state.value("riskLevel", "待评估")
            );
        });
    }

    private Map<String, Object> diagnoseNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_DIAGNOSE, state, () -> {
            emit("GRAPH_RAG", "Neo4j 医学知识图谱正在执行症状→疾病→治疗/检查多跳推理", 48);

            List<MedicalGraphPath> graphPaths = graphReasoningService.reasonBySymptoms(graphQueryTerms(state));
            List<String> graphEvidence = graphPaths.stream()
                    .map(this::formatGraphEvidence)
                    .toList();

            emit("DIAGNOSIS", "Diagnosis Agent 正结合 Neo4j GraphRAG 依据生成初步诊断建议", 52);

            DiagnosisAgent.DiagnosisOutcome result = diagnosisAgent.diagnose(
                    primaryDiagnosisSpec(),
                    state.value("chiefComplaint", ""),
                    state.value("collectorSummary", ""),
                    stringListValue(state, "structuredAnalysis"),
                    graphEvidence
            );

            return Map.of(
                    "preliminaryConclusion", result.conclusion(),
                    "preliminaryConfidence", result.confidence(),
                    "riskLevel", result.riskLevel(),
                    "structuredAnalysis", mergeGraphEvidence(
                            mergeInformationGuide(result.structuredAnalysis(), state),
                            graphEvidence
                    ),
                    "suggestions", result.suggestions()
            );
        });
    }

    private List<String> informationSufficiencyAnalysis(MedicalWorkflowTools.SufficiencyResult result) {
        List<String> analysis = new ArrayList<>();
        analysis.add(result.sufficient()
                ? "信息充足性：当前信息已达到进入 AI 初步诊断的最低要求。"
                : "信息充足性：当前信息不足，建议先补充关键病情信息后再进入稳定诊断。");
        analysis.add("信息充足度评分：" + String.format(java.util.Locale.ROOT, "%.0f%%", result.score() * 100));
        analysis.addAll(result.missingItems());
        analysis.addAll(MedicalWorkflowTools.PATIENT_INFORMATION_GUIDE);
        return analysis;
    }

    private List<String> mergeInformationGuide(List<String> analysis, DiagnosisGraphState state) {
        List<String> merged = new ArrayList<>(stringListValue(state, "informationSufficiencyAnalysis"));
        merged.addAll(analysis);
        return merged;
    }

    private List<String> graphQueryTerms(DiagnosisGraphState state) {
        List<String> terms = new ArrayList<>();
        addIfText(terms, state.value("chiefComplaint", ""));
        addIfText(terms, state.value("collectorSummary", ""));
        stringListValue(state, "structuredAnalysis").forEach(item -> addIfText(terms, item));
        addKnownGraphTerms(terms, String.join(" ", terms));
        return terms.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(12)
                .toList();
    }

    private void addKnownGraphTerms(List<String> terms, String text) {
        Map<String, List<String>> aliases = Map.of(
                "肥胖", List.of("肥胖", "肥胖症", "超重", "体重增加", "体重明显增加", "身上肉很多"),
                "食量大", List.of("食量大", "饭量大", "能吃", "吃得多", "吃起来不节制"),
                "不爱运动", List.of("不爱运动", "缺乏运动", "不喜欢运动", "懒", "叫他运动也不愿意"),
                "吃完饭就躺", List.of("吃完饭就躺", "吃完就躺", "床上吃零食")
        );
        aliases.forEach((term, words) -> {
            if (words.stream().anyMatch(text::contains)) {
                terms.add(term);
            }
        });
    }

    private void addIfText(List<String> terms, String value) {
        if (StringUtils.hasText(value)) {
            terms.add(value.trim());
        }
    }

    private String formatGraphEvidence(MedicalGraphPath path) {
        return "图谱路径：症状[" + path.symptom()
                + "] -> 疾病[" + path.disease()
                + "] -> 治疗" + path.treatments()
                + " / 检查" + path.examinations()
                + "，图谱置信度=" + String.format(java.util.Locale.ROOT, "%.2f", path.confidence());
    }

    private List<String> mergeGraphEvidence(List<String> analysis, List<String> graphEvidence) {
        List<String> merged = new ArrayList<>(analysis);
        if (graphEvidence.isEmpty()) {
            merged.add("Neo4j 医学知识图谱暂未命中明确的症状-疾病-治疗路径，本轮诊断主要依据患者主诉和模型推理。");
        } else {
            merged.addAll(graphEvidence);
        }
        return merged;
    }

    private Map<String, Object> reviewNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_REVIEW, state, () -> {
            emit("REVIEWERS", "Reviewer 并行评审中：Kimi / GLM / Qwen 主模型正在独立复核", 68);

            CompletableFuture<Map<String, Object>> qwen = reviewerTask(
                    "Qwen3.6-Plus",
                    properties.getReviewers().getQwen(),
                    state
            );
            CompletableFuture<Map<String, Object>> kimi = reviewerTask(
                    "Kimi K2.6",
                    properties.getReviewers().getKimi(),
                    state
            );
            CompletableFuture<Map<String, Object>> glm = reviewerTask(
                    "GLM",
                    properties.getReviewers().getGlm(),
                    state
            );

            List<Map<String, Object>> reviewers = CompletableFuture.allOf(qwen, kimi, glm)
                    .thenApply(unused -> List.of(qwen.join(), kimi.join(), glm.join()))
                    .join();

            return Map.of("reviewers", reviewers);
        });
    }

    private Map<String, Object> decisionNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_DECIDE, state, () -> {
            emit("DECISION", "Decision Layer 正在进行 Voting、Confidence 和 Risk Control 决策", 82);

            List<Map<String, Object>> reviewers = reviewerStateListValue(state, "reviewers");
            MedicalWorkflowTools.RiskResult risk =
                    tools.assessRisk(state.value("collectorSummary", state.userMessage()));
            MedicalWorkflowTools.VotingResult voting =
                    tools.vote(reviewers, state.value("preliminaryConfidence", 0.5d), risk);

            String decisionHint = modelGateway.chat(
                    decisionSpec(),
                    """
                            你是 Decision Layer，请根据主诊断结论、Reviewer 评分和风险等级判断下一步路线。
                            仅返回一个关键词：finalize / retry_collect / human_review。
                            """,
                    List.of(Map.of(
                            "role", "user",
                            "MedContent", "结论：" + state.value("preliminaryConclusion", "")
                                    + "\n风险：" + risk.riskLevel()
                                    + "\n投票结果：" + reviewers.toString()
                                    + "\n当前建议路线：" + voting.route()
                    ))
            );

            String route = normalizeDecisionRoute(decisionHint, voting.route(), state.retryCount());
            return Map.of(
                    "riskLevel", risk.riskLevel(),
                    "finalConfidence", voting.confidence(),
                    "decisionRoute", route,
                    "retryCount", route.equals("retry_collect") ? state.retryCount() + 1 : state.retryCount()
            );
        });
    }

    private Map<String, Object> humanReviewNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_HUMAN, state, () -> {
            emit("HUMAN_REVIEW", "置信度偏低或风险较高，工作流已转入人工审核环节", 94);

            return Map.of(
                    "sessionStatus", "待医生定夺",
                    "finalConclusion", state.value("preliminaryConclusion", "系统建议转人工审核。"),
                    "structuredAnalysis", appendSuggestion(
                            state.value("structuredAnalysis", List.of()),
                            "当前 AI 初步诊断已保留并提交给医生，即使置信度偏低也需由医生结合病历最终定夺。"
                    ),
                    "suggestions", appendSuggestion(
                            state.value("suggestions", List.of()),
                            "当前结果可信度或风险等级不足以直接面向患者输出，已转交医生审核定夺。"
                    )
            );
        });
    }

    private Map<String, Object> finalizeNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_FINALIZE, state, () -> {
            emit("FINALIZE", "系统已生成初步诊断建议，等待后续医生审核或病人查看", 100);

            return Map.of(
                    "sessionStatus", "AI 初诊已生成",
                    "finalConclusion", state.value("preliminaryConclusion", "已生成初步诊断建议。")
            );
        });
    }

    private CompletableFuture<Map<String, Object>> reviewerTask(
            String displayName,
            AiWorkflowProperties.Reviewer reviewer,
            DiagnosisGraphState state
    ) {
        return CompletableFuture.supplyAsync(() -> {
            return reviewerAgent.review(
                    displayName,
                    reviewerSpec(reviewer),
                    reviewer.getWeight(),
                    state.value("chiefComplaint", ""),
                    state.value("collectorSummary", ""),
                    state.value("preliminaryConclusion", ""),
                    state.value("preliminaryConfidence", 0.5d)
            );
        });
    }

    private String normalizeDecisionRoute(String modelDecision, String fallbackRoute, int retryCount) {
        String decision = modelDecision == null ? "" : modelDecision.toLowerCase();
        if (decision.contains("human_review")) {
            return "human_review";
        }
        if (decision.contains("retry_collect") && retryCount < 1) {
            return "retry_collect";
        }
        if (decision.contains("finalize")) {
            return "finalize";
        }
        if (fallbackRoute.equals("retry_collect") && retryCount >= 1) {
            return "human_review";
        }
        return fallbackRoute;
    }

    private List<String> appendSuggestion(List<String> suggestions, String extra) {
        List<String> merged = new ArrayList<>(suggestions);
        merged.add(extra);
        return merged;
    }

    private MultiModelGateway.ModelSpec collectorSpec() {
        return new MultiModelGateway.ModelSpec(
                defaultIfBlank(properties.getCollector().getApiKey(), properties.getApiKey()),
                defaultIfBlank(properties.getCollector().getBaseUrl(), properties.getBaseUrl()),
                properties.getCollector().getModel(),
                properties.getCollector().getTemperature()
        );
    }

    private MultiModelGateway.ModelSpec primaryDiagnosisSpec() {
        return new MultiModelGateway.ModelSpec(
                properties.getApiKey(),
                properties.getBaseUrl(),
                properties.getChat().getOptions().getModel(),
                properties.getChat().getOptions().getTemperature()
        );
    }

    private MultiModelGateway.ModelSpec reviewerSpec(AiWorkflowProperties.Reviewer reviewer) {
        return new MultiModelGateway.ModelSpec(
                defaultIfBlank(reviewer.getApiKey(), properties.getApiKey()),
                defaultIfBlank(reviewer.getBaseUrl(), properties.getBaseUrl()),
                reviewer.getModel(),
                reviewer.getTemperature()
        );
    }

    private MultiModelGateway.ModelSpec decisionSpec() {
        return new MultiModelGateway.ModelSpec(
                defaultIfBlank(properties.getDecision().getApiKey(), properties.getApiKey()),
                defaultIfBlank(properties.getDecision().getBaseUrl(), properties.getBaseUrl()),
                properties.getDecision().getModel(),
                properties.getDecision().getTemperature()
        );
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void emit(String stage, String message, int progress) {
        messagingTemplate.convertAndSend("/topic/pipeline", new PipelineEvent(
                stage,
                message,
                progress,
                LocalDateTime.now().toString()
        ));
    }

    @SuppressWarnings("unchecked")
    private List<String> stringListValue(DiagnosisGraphState state, String key) {
        Object value = state.value(key, List.of());
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> reviewerStateListValue(DiagnosisGraphState state, String key) {
        Object value = state.value(key, List.of());
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private List<DiagnosticResponse.ReviewerScore> reviewerScoresForResponse(DiagnosisGraphState state, String key) {
        return reviewerStateListValue(state, key).stream()
                .map(this::toReviewerScore)
                .toList();
    }

    private DiagnosticResponse.ReviewerScore toReviewerScore(Map<String, Object> reviewer) {
        return new DiagnosticResponse.ReviewerScore(
                String.valueOf(reviewer.getOrDefault("name", "Reviewer")),
                numberValue(reviewer.get("score")),
                numberValue(reviewer.get("weight"))
        );
    }

    private Map<String, Object> reviewerState(String name, double score, double weight) {
        Map<String, Object> reviewer = new LinkedHashMap<>();
        reviewer.put("name", name);
        reviewer.put("score", score);
        reviewer.put("weight", weight);
        return reviewer;
    }

    private double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                return 0d;
            }
        }
        return 0d;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String summarizeTitle(String message) {
        String cleaned = message.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 12 ? cleaned : cleaned.substring(0, 12) + "...";
    }

    private String newSessionId() {
        return "chat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private SessionSnapshot upsertSession(Long userId, String sessionId, String title, String status, String updatedAt) {
        List<SessionSnapshot> sessions = new ArrayList<>(loadSessionSnapshots(userId));
        SessionSnapshot snapshot = new SessionSnapshot(sessionId, title, status, updatedAt);
        sessions.removeIf(session -> session.id().equals(sessionId));
        sessions.add(0, snapshot);
        writeJson(sessionKey(userId), sessions);
        return snapshot;
    }

    private List<SessionSnapshot> loadSessionSnapshots(Long userId) {
        return readJson(sessionKey(userId), SESSION_LIST_TYPE, new ArrayList<>());
    }

    private List<MessageSnapshot> loadMessageSnapshots(Long userId, String sessionId) {
        return readJson(memoryKey(userId, sessionId), MESSAGE_LIST_TYPE, new ArrayList<>());
    }

    private void saveMessageSnapshots(Long userId, String sessionId, List<MessageSnapshot> history) {
        writeJson(memoryKey(userId, sessionId), history);
    }

    private void saveDiagnosisSnapshot(Long userId, String sessionId, DiagnosticResponse diagnosis) {
        writeJson(diagnosisKey(userId, sessionId), diagnosis);
    }

    private DiagnosticResponse loadDiagnosisSnapshot(Long userId, String sessionId) {
        return readJson(diagnosisKey(userId, sessionId), DIAGNOSIS_TYPE, null);
    }

    private DiagnosticResponse emptyDiagnosis() {
        return new DiagnosticResponse(
                "等待系统生成诊断建议",
                0d,
                "待评估",
                List.of("暂无 AI 诊断结果。请先发起新咨询并提交病情信息。"),
                List.of("提交病情后，系统会返回真实的 AI 初诊、风险等级和医疗建议。"),
                List.of()
        );
    }

    private <T> T readJson(String key, TypeReference<T> type, T fallback) {
        String json = redisTemplate.opsForValue().get(key);
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            return fallback;
        }
    }

    private void writeJson(String key, Object value) {
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Redis 会话写入失败");
        }
    }

    private String sessionKey(Long userId) {
        return "medconsenus:collector:sessions:" + userId;
    }

    private String memoryKey(Long userId, String sessionId) {
        return "medconsenus:collector:memory:" + userId + ":" + sessionId;
    }

    private String diagnosisKey(Long userId, String sessionId) {
        return "medconsenus:collector:diagnosis:" + userId + ":" + sessionId;
    }

    private void deleteRedisSessionArtifacts(Long userId, String sessionId) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(memoryKey(userId, sessionId));
        keys.add(diagnosisKey(userId, sessionId));
        keys.addAll(scanKeys("medconsenus:collector:*:" + userId + ":" + sessionId));
        keys.addAll(scanKeys("medconsenus:*:" + userId + ":" + sessionId));

        if (!keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private Set<String> scanKeys(String pattern) {
        return redisTemplate.execute((RedisCallback<Set<String>>) connection -> {
            Set<String> keys = new LinkedHashSet<>();
            ScanOptions options = ScanOptions.scanOptions()
                    .match(pattern)
                    .count(100)
                    .build();
            try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(new String(cursor.next(), java.nio.charset.StandardCharsets.UTF_8));
                }
            }
            return keys;
        });
    }

    private FinalDiagnosisRecordDto toFinalRecordDto(FinalDiagnosisRecord record) {
        return new FinalDiagnosisRecordDto(
                record.getId(),
                record.getSessionId(),
                record.getChiefComplaint(),
                record.getAiConclusion(),
                record.getDoctorOpinion(),
                record.getFinalConclusion(),
                record.getRiskLevel(),
                record.getConfidence(),
                record.getReviewStatus(),
                record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null
        );
    }

    private record SessionSnapshot(
            String id,
            String title,
            String status,
            String updatedAt
    ) {
    }

    private record MessageSnapshot(
            String role,
            String content
    ) {
    }
}
