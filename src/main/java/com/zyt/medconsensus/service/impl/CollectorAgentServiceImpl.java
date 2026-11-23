package com.zyt.medconsensus.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.agent.CollectorAgent;
import com.zyt.medconsensus.agent.DiagnosisAgent;
import com.zyt.medconsensus.agent.ReviewerAgent;
import com.zyt.medconsensus.agent.TreatmentAgent;
import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.ConsultationResponse;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.DoctorReviewRequest;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.MessageHistoryDto;
import com.zyt.medconsensus.dto.SessionDetailResponse;
import com.zyt.medconsensus.entity.DiseaseMedicine;
import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import com.zyt.medconsensus.graph.DiagnosisGraphState;
import com.zyt.medconsensus.graphkg.MedicalGraphPath;
import com.zyt.medconsensus.graphkg.MedicalGraphReasoningService;
import com.zyt.medconsensus.llm.AiWorkflowProperties;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.mapper.ConsultationSessionStore;
import com.zyt.medconsensus.mapper.DiseaseMedicineMapper;
import org.springframework.transaction.support.TransactionTemplate;
import com.zyt.medconsensus.mapper.FinalDiagnosisRecordMapper;
import com.zyt.medconsensus.mapper.PatientConsultationMapper;
import com.zyt.medconsensus.observability.LangSmithTracingService;
import com.zyt.medconsensus.service.CollectorAgentService;
import com.zyt.medconsensus.service.PatientSkillService;
import com.zyt.medconsensus.service.PipelineEventPublisher;
import com.zyt.medconsensus.tool.MedicalWorkflowTools;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncEdgeAction;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CollectorAgentServiceImpl implements CollectorAgentService {

    private static final Logger auditLog = LoggerFactory.getLogger("medical.session.audit");
    private static final int MAX_HISTORY_MESSAGES = 40;

    private static final String NODE_COLLECT = "collect";
    private static final String NODE_ASSESS = "assess";
    private static final String NODE_ASK_MORE = "ask_more_info";
    private static final String NODE_DIAGNOSE = "diagnose";
    private static final String NODE_REVIEW = "review";
    private static final String NODE_DECIDE = "decide";
    private static final String NODE_HUMAN = "human_review";
    private static final String NODE_FINALIZE = "finalize";

    private static final int RECOMMENDED_MEDICINE_LIMIT = 2;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final AiWorkflowProperties properties;
    private final MultiModelGateway modelGateway;
    private final MedicalWorkflowTools tools;
    private final CollectorAgent collectorAgent;
    private final DiagnosisAgent diagnosisAgent;
    private final ReviewerAgent reviewerAgent;
    private final TreatmentAgent treatmentAgent;
    private final ConsultationSessionStore sessions;
    private final TransactionTemplate transactions;
    private final ObjectMapper objectMapper;
    private final PipelineEventPublisher pipelineEventPublisher;
    private final DiseaseMedicineMapper diseaseMedicineMapper;
    private final FinalDiagnosisRecordMapper finalDiagnosisRecordMapper;
    private final PatientConsultationMapper patientConsultationMapper;
    private final LangSmithTracingService tracingService;
    private final MedicalGraphReasoningService graphReasoningService;
    private final PatientSkillService patientSkillService;
    private final ExecutorService reviewerExecutor;
    private final RedissonClient redissonClient;
    private final CompiledGraph<DiagnosisGraphState> workflow;

    public CollectorAgentServiceImpl(
            AiWorkflowProperties properties,
            MultiModelGateway modelGateway,
            MedicalWorkflowTools tools,
            CollectorAgent collectorAgent,
            DiagnosisAgent diagnosisAgent,
            ReviewerAgent reviewerAgent,
            TreatmentAgent treatmentAgent,
            ConsultationSessionStore sessions,
            TransactionTemplate transactions,
            ObjectMapper objectMapper,
            PipelineEventPublisher pipelineEventPublisher,
            DiseaseMedicineMapper diseaseMedicineMapper,
            FinalDiagnosisRecordMapper finalDiagnosisRecordMapper,
            PatientConsultationMapper patientConsultationMapper,
            LangSmithTracingService tracingService,
            MedicalGraphReasoningService graphReasoningService,
            PatientSkillService patientSkillService,
            @Qualifier("reviewerExecutor") ExecutorService reviewerExecutor,
            RedissonClient redissonClient
    ) {
        this.properties = properties;
        this.modelGateway = modelGateway;
        this.tools = tools;
        this.collectorAgent = collectorAgent;
        this.diagnosisAgent = diagnosisAgent;
        this.reviewerAgent = reviewerAgent;
        this.treatmentAgent = treatmentAgent;
        this.sessions = sessions;
        this.transactions = transactions;
        this.objectMapper = objectMapper;
        this.pipelineEventPublisher = pipelineEventPublisher;
        this.diseaseMedicineMapper = diseaseMedicineMapper;
        this.finalDiagnosisRecordMapper = finalDiagnosisRecordMapper;
        this.patientConsultationMapper = patientConsultationMapper;
        this.tracingService = tracingService;
        this.graphReasoningService = graphReasoningService;
        this.patientSkillService = patientSkillService;
        this.reviewerExecutor = reviewerExecutor;
        this.redissonClient = redissonClient;
        this.workflow = compileWorkflow();
        // 为重新收集信息预留一次完整的工作流迭代。
        this.workflow.setMaxIterations(16);
    }

    @Override
    public ConsultationResponse organize(Long userId, ConsultationRequest request) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "缺少问诊内容");
        }
        String userMessage = request.getMessage();
        if (!StringUtils.hasText(userMessage)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入咨询内容");
        }
        if (StringUtils.hasText(request.getMedicalEvidence()) && !request.isMedicalEvidenceConfirmed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "检查资料尚未由医生确认，不能进入诊断 Agent");
        }
        String resolvedSessionId = StringUtils.hasText(request.getSessionId())
                ? request.getSessionId()
                : newSessionId();
        return withSessionLock(
                userId,
                resolvedSessionId,
                () -> organizeLocked(userId, resolvedSessionId, request)
        );
    }

    private ConsultationResponse organizeLocked(
            Long userId,
            String resolvedSessionId,
            ConsultationRequest request
    ) {
        String userMessage = request.getMessage();
        String workflowMessage = appendMedicalEvidence(
                userMessage.trim(),
                request.getMedicalEvidence(),
                request.getMedicalEvidenceFileName()
        );

        var previous = sessions.get(userId, resolvedSessionId).orElse(null);
        if (previous != null && !java.util.Objects.equals(previous.patientRecordId(), request.getPatientRecordId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前患者档案与问诊记录不一致");
        }
        List<MessageHistoryDto> prior = previous == null ? List.of() : previous.history();
        List<MessageHistoryDto> history = new ArrayList<>(prior.subList(Math.max(0, prior.size() - MAX_HISTORY_MESSAGES + 1), prior.size()));
        MessageHistoryDto patientMessage = new MessageHistoryDto("user", workflowMessage);
        history.add(patientMessage);

        String patientSkill = patientSkillService.buildCurrentContext(request, patientSkillService.loadSkill(request));

        Map<String, Object> initialState = new LinkedHashMap<>();
        initialState.put("userId", userId);
        initialState.put("sessionId", resolvedSessionId);
        initialState.put("userMessage", workflowMessage);
        initialState.put("memory", history.stream().map(this::formatMessageForAgent).toList());
        initialState.put("patientName", request.getPatientName());
        initialState.put("patientSkill", patientSkill);
        initialState.put("retryCount", 0);

        DiagnosisGraphState result = tracingService.traceWorkflow(
                "MedConsenus Diagnosis Workflow",
                userId,
                resolvedSessionId,
                workflowMessage,
                () -> workflow.invoke(initialState)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "工作流执行失败"))
        );

        ChatSessionDto session = new ChatSessionDto(resolvedSessionId,
                sessionTitle(request.getPatientName(), result.value("title", summarizeTitle(workflowMessage))),
                result.value("sessionStatus", "病情整理中"), LocalDateTime.now().format(TIME_FORMATTER),
                StringUtils.hasText(request.getMedicalEvidenceFileName()) ? request.getMedicalEvidenceFileName()
                        : previous == null ? null : previous.evidenceFileName());
        String assistantMessage = result.value("finalConclusion", "已完成病情整理。");
        MessageHistoryDto reply = new MessageHistoryDto("assistant", assistantMessage, "待补充信息".equals(session.status()));
        history.add(reply);

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
        String chiefComplaint = result.value("chiefComplaint", workflowMessage);
        sessions.appendTurn(userId, request.getPatientRecordId(), session, chiefComplaint, List.of(patientMessage, reply), diagnosis,
                request.getMedicalEvidenceId(), request.getMedicalEvidence());
        auditLog.info("session_write userId={} sessionId={} resource=messages count={}", userId, resolvedSessionId, 2);
        patientSkillService.recordConsultation(
                userId,
                resolvedSessionId,
                request,
                result.value("collectorSummary", ""),
                analysis,
                diagnosis
        );

        return new ConsultationResponse(
                resolvedSessionId,
                chiefComplaint,
                session,
                diagnosis,
                history.stream().map(MessageHistoryDto::content).toList()
        );
    }

    private String appendMedicalEvidence(String userMessage, String medicalEvidence, String evidenceFileName) {
        if (!StringUtils.hasText(medicalEvidence)) {
            return userMessage;
        }
        String evidence = medicalEvidence.trim();
        String fileLine = StringUtils.hasText(evidenceFileName)
                ? "文件名：" + evidenceFileName.trim() + "\n"
                : "";
        return userMessage
                + "\n\n【GPT-5.4视觉模型结构化检查资料】\n"
                + fileLine
                + evidence;
    }

    @Override
    public List<ChatSessionDto> loadSessions(Long userId) {
        if (userId == null) {
            return List.of();
        }

        return sessions.list(userId);
    }

    @Override
    public List<String> loadMemory(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return List.of();
        }

        return sessions.get(userId, sessionId).map(ConsultationSessionStore.Session::history).orElse(List.of()).stream()
                .map(message -> message.role().toUpperCase() + ": " + message.content())
                .toList();
    }

    @Override
    public DiagnosticResponse loadLatestDiagnosis(Long userId) {
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }

        DiagnosticResponse latest = sessions.latestDiagnosis(userId);
        return latest == null ? emptyDiagnosis() : latest;
    }

    @Override
    public SessionDetailResponse loadSessionDetail(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会话不存在");
        }

        var snapshot = sessions.get(userId, sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到会话"));
        auditLog.info("session_read userId={} sessionId={} resource=messages", userId, sessionId);
        List<MessageHistoryDto> history = snapshot.history();
        DiagnosticResponse diagnosis = snapshot.diagnosis();
        FinalDiagnosisRecordDto finalRecord = finalDiagnosisRecordMapper.findByUserIdAndSessionId(userId, sessionId)
                .map(this::toFinalRecordDto)
                .orElse(null);

        if (diagnosis == null && finalRecord != null && StringUtils.hasText(finalRecord.aiConclusion())) {
            // 老版本仅长期保存最终报告，可利用该报告中的原初诊字段重新打开复核。
            diagnosis = new DiagnosticResponse(finalRecord.aiConclusion(),
                    finalRecord.confidence() == null ? 0d : finalRecord.confidence(),
                    finalRecord.riskLevel(), List.of(), List.of(), List.of());
        }

        return new SessionDetailResponse(
                snapshot.id(),
                snapshot.title(),
                snapshot.status(),
                snapshot.updatedAt(),
                diagnosis,
                history,
                finalRecord,
                snapshot.patientRecordId()
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

        return withSessionLock(userId, request.getSessionId(), () -> transactions.execute(
                ignored -> saveDoctorReviewLocked(userId, request)));
    }

    private FinalDiagnosisRecordDto saveDoctorReviewLocked(Long userId, DoctorReviewRequest request) {
        String sessionId = request.getSessionId();
        FinalDiagnosisRecord record = finalDiagnosisRecordMapper.findByUserIdAndSessionId(userId, sessionId)
                .orElseGet(FinalDiagnosisRecord::new);
        var session = sessions.get(userId, sessionId).orElse(null);
        DiagnosticResponse diagnosis = session == null ? null : session.diagnosis();
        if (session != null && ("待补充信息".equals(session.status()) || "NEEDS_PATIENT_REPLY".equals(session.status()))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先完成问诊并生成待复核的结论");
        }
        if (diagnosis == null && !StringUtils.hasText(record.getAiConclusion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "未找到可复核的诊断结果");
        }
        String aiConclusion = diagnosis == null ? record.getAiConclusion() : diagnosis.conclusion();
        if (!java.util.Objects.equals(aiConclusion, request.getAiConclusion())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "待复核结论已更新，请刷新问诊详情");
        }
        String chiefComplaint = session != null && StringUtils.hasText(session.chiefComplaint())
                ? session.chiefComplaint() : record.getChiefComplaint();
        String riskLevel = diagnosis == null ? record.getRiskLevel() : diagnosis.riskLevel();
        Double confidence = diagnosis == null ? record.getConfidence() : Double.valueOf(diagnosis.confidence());
        String opinion = blankToNull(request.getOpinion());
        String finalConclusion = opinion == null ? aiConclusion : opinion;
        String reviewStatus = opinion == null ? "AI_CONFIRMED" : "DOCTOR_OVERRIDDEN";
        if (reviewStatus.equals(record.getReviewStatus()) && java.util.Objects.equals(record.getChiefComplaint(), chiefComplaint)
                && java.util.Objects.equals(record.getAiConclusion(), aiConclusion)
                && java.util.Objects.equals(record.getFinalConclusion(), finalConclusion)
                && java.util.Objects.equals(record.getRiskLevel(), riskLevel)
                && java.util.Objects.equals(record.getConfidence(), confidence)) {
            return toFinalRecordDto(record);
        }
        record.setUserId(userId);
        record.setSessionId(sessionId);
        if (session != null) record.setPatientRecordId(session.patientRecordId());
        record.setChiefComplaint(chiefComplaint);
        record.setAiConclusion(aiConclusion);
        record.setRiskLevel(riskLevel);
        record.setConfidence(confidence);
        record.setDoctorOpinion(opinion);
        record.setFinalConclusion(finalConclusion);
        record.setReviewStatus(reviewStatus);
        record.setPublishedToPatient(false);
        record.setPublishedAt(null);
        var patientConsultation = patientConsultationMapper.findByDoctorIdAndSessionId(userId, sessionId).orElse(null);
        if (patientConsultation != null) {
            record.setPatientAccountId(patientConsultation.getPatientAccountId());
            record.setPatientRecordId(patientConsultation.getPatientRecordId());
        }
        emit(userId, request.getSessionId(), "TREATMENT", "Treatment Agent 正在根据医生最终诊断生成开药说明", 100);
        TreatmentAgent.TreatmentOutcome treatment = buildTreatmentAdvice(record);
        record.setTreatmentKeywords(writeJsonString(treatment.keywords()));
        record.setTreatmentSource(treatment.source());
        record.setTreatmentAdvice(treatment.advice());

        FinalDiagnosisRecord saved = finalDiagnosisRecordMapper.saveAndFlush(record);
        if (patientConsultation != null) {
            patientConsultationMapper.transitionSessionStatus(userId, request.getSessionId(), "REPORT_READY");
        }
        sessions.saveReviewStatus(userId, sessionId, record.getPatientRecordId(), record.getChiefComplaint());
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

        withSessionLock(userId, sessionId, () -> {
            if (!sessions.archive(userId, sessionId)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到会话");
            }
            return null;
        });
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
                            "human_review", NODE_HUMAN,
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
            emit(state, "COLLECTOR", "信息收集/病情整理 Agent 正在归纳用户输入与会话记忆", 12);

            String latestInput = state.userMessage();
            List<String> memory = stringListValue(state, "memory");

            CollectorAgent.CollectorOutcome result = collectorAgent.collect(
                    collectorSpec(),
                    latestInput,
                    memory,
                    state.value("patientSkill", "")
            );

            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("title", result.title());
            updates.put("collectorReply", result.reply());
            updates.put("chiefComplaint", result.chiefComplaint());
            updates.put("collectorSummary", result.summary());
            updates.put("structuredAnalysis", result.structuredAnalysis());
            updates.put("followUpQuestions", result.followUpQuestions());
            String patientHistory = memory.stream().filter(message -> message.startsWith("患者："))
                    .collect(java.util.stream.Collectors.joining(" "));
            var risk = tools.assessRisk(patientHistory + " " + result.summary());
            updates.put("riskLevel", risk.riskLevel());
            updates.put("ruleHighRisk", risk.highRisk());
            return updates;
        });
    }

    private Map<String, Object> assessNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_ASSESS, state, () -> {
            emit(state, "SUFFICIENCY", "正在判断当前病情信息是否足以进入初步诊断", 25);

            MedicalWorkflowTools.SufficiencyResult result =
                    tools.assessInformationSufficiency(state.userMessage(), state.memory());

            Map<String, Object> updates = new LinkedHashMap<>();
            updates.put("sufficient", result.sufficient());
            updates.put("assessmentRoute", result.sufficient() ? "sufficient" : "more_info");
            if (!result.sufficient() && state.value("ruleHighRisk", false)) {
                updates.put("assessmentRoute", "human_review");
                updates.put("riskLevel", "高风险");
                updates.put("preliminaryConclusion", "当前资料提示高风险且信息不足，已提交医生复核。");
                updates.put("finalConfidence", 0d);
            }
            updates.put("missingItems", result.missingItems());
            updates.put("informationSufficiencyAnalysis", informationSufficiencyAnalysis(result));
            return updates;
        });
    }

    private Map<String, Object> askMoreInfoNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_ASK_MORE, state, () -> {
            emit(state, "COLLECTOR", "当前信息仍不充分，系统准备回到信息收集 Agent 继续追问", 40);

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
            String reply = state.value("collectorReply", "");
            if (!StringUtils.hasText(reply)) {
                reply = "为了继续整理病情，请补充以下信息：\n"
                        + mergedSuggestions.stream()
                        .map(item -> "• " + item)
                        .collect(java.util.stream.Collectors.joining("\n"));
            }

            return Map.of(
                    "sessionStatus", "待补充信息",
                    "finalConclusion", reply,
                    "structuredAnalysis", structuredAnalysis,
                    "suggestions", mergedSuggestions,
                    "finalConfidence", 0.42d,
                    "riskLevel", state.value("riskLevel", "待评估")
            );
        });
    }

    private Map<String, Object> diagnoseNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_DIAGNOSE, state, () -> {
            emit(state, "GRAPH_RAG", "Neo4j 医学知识图谱正在执行症状→疾病→治疗/检查多跳推理", 48);

            List<MedicalGraphPath> graphPaths = graphReasoningService.reasonBySymptoms(graphQueryTerms(state));
            List<String> graphEvidence = graphPaths.stream()
                    .map(this::formatGraphEvidence)
                    .toList();

            emit(state, "DIAGNOSIS", "Diagnosis Agent 正结合 Neo4j GraphRAG 依据生成初步诊断建议", 52);

            DiagnosisAgent.DiagnosisOutcome result = diagnosisAgent.diagnose(
                    primaryDiagnosisSpec(),
                    state.value("chiefComplaint", ""),
                    state.value("collectorSummary", ""),
                    stringListValue(state, "structuredAnalysis"),
                    graphEvidence,
                    state.value("patientSkill", "")
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
        List<String> rawTerms = new ArrayList<>();
        addIfText(rawTerms, state.value("chiefComplaint", ""));
        addIfText(rawTerms, state.value("collectorSummary", ""));
        stringListValue(state, "structuredAnalysis").forEach(item -> addIfText(rawTerms, item));

        List<String> terms = new ArrayList<>();
        addKnownGraphTerms(terms, String.join(" ", rawTerms) + " " + state.value("patientSkill", ""));
        terms.addAll(rawTerms);
        return terms.stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(12)
                .toList();
    }

    private void addKnownGraphTerms(List<String> terms, String text) {
        Map<String, List<String>> aliases = Map.ofEntries(
                Map.entry("肥胖", List.of("肥胖", "肥胖症", "超重", "体重增加", "体重明显增加", "身上肉很多")),
                Map.entry("食量大", List.of("食量大", "饭量大", "能吃", "吃得多", "吃起来不节制")),
                Map.entry("不爱运动", List.of("不爱运动", "缺乏运动", "不喜欢运动", "懒", "叫他运动也不愿意")),
                Map.entry("吃完饭就躺", List.of("吃完饭就躺", "吃完就躺", "床上吃零食")),
                Map.entry("膝骨关节炎", List.of("膝骨关节炎", "膝关节骨关节炎", "退行性膝关节炎", "慢性关节炎", "骨性关节炎")),
                Map.entry("关节炎", List.of("关节炎", "慢性关节炎", "关节反复疼痛")),
                Map.entry("膝关节疼痛", List.of("膝关节疼痛", "双膝关节反复疼痛", "膝盖疼", "膝痛", "上下楼困难")),
                Map.entry("关节僵硬", List.of("僵硬感", "晨僵", "屈伸不利", "关节屈伸不利")),
                Map.entry("风寒湿痹", List.of("遇冷", "阴雨天", "冬天疼痛", "膝盖发凉", "发凉", "寒湿", "风寒湿痹", "寒湿痹阻", "苔白腻")),
                Map.entry("肝肾不足", List.of("腰膝酸软", "腿软无力", "脉沉细", "长期劳累后症状容易加重")),
                Map.entry("气血不足", List.of("面色偏白", "气短乏力", "容易疲劳", "舌淡"))
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
            emit(state, "REVIEWERS", "Reviewer 并行评审中：GPT / Kimi / GLM 正在独立复核", 68);

            CompletableFuture<Map<String, Object>> gpt = reviewerTask(
                    "GPT 5.4",
                    properties.getReviewers().getGpt(),
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

            List<Map<String, Object>> reviewers = List.of(gpt.join(), kimi.join(), glm.join());
            return Map.of("reviewers", reviewers);
        });
    }

    private Map<String, Object> decisionNode(DiagnosisGraphState state) {
        return tracingService.traceNode(NODE_DECIDE, state, () -> {
            emit(state, "DECISION", "Decision Layer 正在进行 Voting、Confidence 和 Risk Control 决策", 82);

            List<Map<String, Object>> reviewers = reviewerStateListValue(state, "reviewers");
            MedicalWorkflowTools.RiskResult risk =
                    tools.assessRisk(state.userMessage() + " " + state.value("collectorSummary", ""));
            if (state.value("ruleHighRisk", false) || "高风险".equals(state.value("riskLevel", ""))) {
                risk = new MedicalWorkflowTools.RiskResult("高风险", 0.92d, true);
            }
            MedicalWorkflowTools.VotingResult voting =
                    tools.vote(reviewers, state.value("preliminaryConfidence", 0.5d), risk);

            String decisionHint = voting.route().equals("human_review") ? "human_review" : modelGateway.chat(
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
            emit(state, "HUMAN_REVIEW", "置信度偏低或风险较高，工作流已转入人工审核环节", 94);

            return Map.of(
                    "sessionStatus", "待医生定夺",
                    "finalConclusion", state.value("preliminaryConclusion", "系统建议转人工审核。"),
                    "structuredAnalysis", appendSuggestion(
                            state.value("structuredAnalysis", List.of()),
                            "请医生结合病历原文、风险因素与有效评审意见进行复核。"
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
            emit(state, "FINALIZE", "系统已生成初步诊断建议，等待后续医生审核或病人查看", 100);

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
        double weight = reviewer.getWeight();
        try {
            return CompletableFuture.supplyAsync(() -> reviewerAgent.review(displayName, reviewerSpec(reviewer),
                            weight, state.value("chiefComplaint", ""), state.value("collectorSummary", ""),
                            state.value("preliminaryConclusion", "")), reviewerExecutor)
                    .orTimeout(90, TimeUnit.SECONDS)
                    .exceptionally(error -> ReviewerAgent.absent(displayName, weight,
                            error instanceof TimeoutException ? "评审超时" : "评审服务不可用"));
        } catch (java.util.concurrent.RejectedExecutionException exception) {
            return CompletableFuture.completedFuture(ReviewerAgent.absent(displayName, weight, "评审服务繁忙"));
        }
    }

    static String normalizeDecisionRoute(String modelDecision, String fallbackRoute, int retryCount) {
        if ("human_review".equals(fallbackRoute)) {
            return "human_review";
        }
        String decision = modelDecision == null ? "" : modelDecision.trim().toLowerCase(java.util.Locale.ROOT);
        if (decision.equals("human_review")) {
            return "human_review";
        }
        if (decision.equals("retry_collect") && retryCount < 1) {
            return "retry_collect";
        }
        if (decision.equals("finalize")) {
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

    private TreatmentAgent.TreatmentOutcome buildTreatmentAdvice(FinalDiagnosisRecord record) {
        String finalDiagnosis = record.getFinalConclusion();
        List<String> keywords = tools.extractTreatmentKeywords(finalDiagnosis);
        List<DiseaseMedicine> medicines = findDiseaseMedicines(keywords);

        if (!medicines.isEmpty()) {
            return treatmentAgent.fromDatabase(
                    keywords,
                    medicines.stream().map(this::formatMedicineRecommendation).toList(),
                    finalDiagnosis
            );
        }

        return treatmentAgent.inferWithModel(
                treatmentSpec(),
                keywords,
                finalDiagnosis,
                record.getChiefComplaint(),
                record.getRiskLevel()
        );
    }

    private List<DiseaseMedicine> findDiseaseMedicines(List<String> keywords) {
        Map<String, DiseaseMedicine> unique = new LinkedHashMap<>();
        for (String keyword : keywords) {
            diseaseMedicineMapper.findByDiseaseKeyword(keyword, RECOMMENDED_MEDICINE_LIMIT).forEach(medicine -> {
                String key = medicine.getDiseaseName() + "::" + medicine.getMedicineName();
                unique.putIfAbsent(key, medicine);
            });
            if (unique.size() >= RECOMMENDED_MEDICINE_LIMIT) {
                break;
            }
        }
        return unique.values().stream().limit(RECOMMENDED_MEDICINE_LIMIT).toList();
    }

    private String formatMedicineRecommendation(DiseaseMedicine medicine) {
        List<String> parts = new ArrayList<>();
        parts.add("疾病：" + medicine.getDiseaseName());
        parts.add("药物：" + medicine.getMedicineName());
        addLabeledText(parts, "作用", medicine.getMedicineEffect());
        addLabeledText(parts, "用法用量", medicine.getDosageUsage());
        addLabeledText(parts, "禁忌/注意", medicine.getContraindication());
        return String.join("；", parts);
    }

    private void addLabeledText(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "：" + value.trim());
        }
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

    private MultiModelGateway.ModelSpec treatmentSpec() {
        return new MultiModelGateway.ModelSpec(
                defaultIfBlank(properties.getTreatment().getApiKey(), properties.getApiKey()),
                defaultIfBlank(properties.getTreatment().getBaseUrl(), properties.getBaseUrl()),
                properties.getTreatment().getModel(),
                properties.getTreatment().getTemperature()
        );
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private void emit(DiagnosisGraphState state, String stage, String message, int progress) {
        emit(state.userId(), state.sessionId(), stage, message, progress);
    }

    private void emit(Long userId, String sessionId, String stage, String message, int progress) {
        pipelineEventPublisher.publish(userId, sessionId, stage, message, progress);
    }

    private <T> T withSessionLock(Long userId, String sessionId, Supplier<T> action) {
        RLock lock = redissonClient.getLock("medconsenus:lock:session:" + userId + ":" + sessionId);
        boolean acquired = false;
        try {
            acquired = lock.tryLock(0, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该会话正在处理中，请稍后重试");
            }
            return action.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "会话处理被中断，请稍后重试");
        } finally {
            if (acquired && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
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
                reviewer.containsKey("score") ? numberValue(reviewer.get("score")) : null,
                numberValue(reviewer.get("weight")),
                String.valueOf(reviewer.getOrDefault("comment", "")),
                (String) reviewer.get("status")
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

    private String sessionTitle(String patientName, String fallback) {
        if (StringUtils.hasText(patientName)) {
            return patientName.trim();
        }
        return StringUtils.hasText(fallback) ? fallback : "未命名患者";
    }

    private String formatMessageForAgent(MessageHistoryDto message) {
        String role = "user".equals(message.role()) ? "患者" : "病情整理 Agent";
        return role + "：" + message.content();
    }

    private String newSessionId() {
        return "chat-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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

    private String writeJsonString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            return "[]";
        }
    }

    private List<String> readStringList(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            return List.of();
        }
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
                readStringList(record.getTreatmentKeywords()),
                record.getTreatmentSource(),
                record.getTreatmentAdvice(),
                record.getPatientAccountId(),
                record.getPatientRecordId(),
                record.isPublishedToPatient(),
                record.getPublishedAt() != null ? record.getPublishedAt().toString() : null,
                record.getUpdatedAt() != null ? record.getUpdatedAt().toString() : null
        );
    }

}
