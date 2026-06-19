package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.dto.CaseImportResponse;
import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.ConsultationResponse;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.DoctorReviewRequest;
import com.zyt.medconsensus.dto.DoctorStatsResponse;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.GraphExploreRequest;
import com.zyt.medconsensus.dto.GraphExploreResponse;
import com.zyt.medconsensus.dto.MedicalEvidenceAnalysisResponse;
import com.zyt.medconsensus.dto.PatientBasicInfoDto;
import com.zyt.medconsensus.dto.PatientBasicInfoRequest;
import com.zyt.medconsensus.dto.PipelineEvent;
import com.zyt.medconsensus.dto.SessionDetailResponse;
import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import com.zyt.medconsensus.entity.PatientBasicInfo;
import com.zyt.medconsensus.graphkg.MedicalGraphReasoningService;
import com.zyt.medconsensus.graphkg.MedicalGraphPath;
import com.zyt.medconsensus.mapper.FinalDiagnosisRecordMapper;
import com.zyt.medconsensus.mapper.PatientBasicInfoMapper;
import com.zyt.medconsensus.observability.LangSmithTracingService;
import com.zyt.medconsensus.service.CaseImportService;
import com.zyt.medconsensus.service.CollectorAgentService;
import com.zyt.medconsensus.service.PatientSkillService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspace")
public class MedicalWorkspaceController {

    private static final String SESSION_USER_ID = "CURRENT_USER_ID";
    private static final String SESSION_USER_ROLE = "CURRENT_USER_ROLE";

    private final SimpMessagingTemplate messagingTemplate;
    private final CollectorAgentService collectorAgentService;
    private final PatientBasicInfoMapper patientBasicInfoMapper;
    private final FinalDiagnosisRecordMapper finalDiagnosisRecordMapper;
    private final LangSmithTracingService tracingService;
    private final PatientSkillService patientSkillService;
    private final CaseImportService caseImportService;
    private final MedicalGraphReasoningService graphReasoningService;

    private static final List<String> ALLOWED_IMPORT_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    public MedicalWorkspaceController(
            SimpMessagingTemplate messagingTemplate,
            CollectorAgentService collectorAgentService,
            PatientBasicInfoMapper patientBasicInfoMapper,
            FinalDiagnosisRecordMapper finalDiagnosisRecordMapper,
            LangSmithTracingService tracingService,
            PatientSkillService patientSkillService,
            CaseImportService caseImportService,
            MedicalGraphReasoningService graphReasoningService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.collectorAgentService = collectorAgentService;
        this.patientBasicInfoMapper = patientBasicInfoMapper;
        this.finalDiagnosisRecordMapper = finalDiagnosisRecordMapper;
        this.tracingService = tracingService;
        this.patientSkillService = patientSkillService;
        this.caseImportService = caseImportService;
        this.graphReasoningService = graphReasoningService;
    }

    @GetMapping("/patients")
    public List<PatientBasicInfoDto> patients(HttpSession session) {
        Long doctorId = currentUserId(session);
        return patientBasicInfoMapper.findByDoctorIdOrderByUpdateTimeDesc(doctorId).stream()
                .map(this::toPatientDto)
                .toList();
    }

    @PostMapping("/patients")
    public PatientBasicInfoDto createPatient(
            @Valid @RequestBody PatientBasicInfoRequest request,
            HttpSession session
    ) {
        Long doctorId = currentUserId(session);
        PatientBasicInfo patient = new PatientBasicInfo();
        patient.setDoctorId(doctorId);
        applyPatientRequest(patient, request);
        PatientBasicInfo saved = patientBasicInfoMapper.save(patient);
        patientSkillService.recordPatientProfile(saved, true);
        return toPatientDto(saved);
    }

    @PutMapping("/patients/{patientId}")
    public PatientBasicInfoDto updatePatient(
            @PathVariable Long patientId,
            @Valid @RequestBody PatientBasicInfoRequest request,
            HttpSession session
    ) {
        Long doctorId = currentUserId(session);
        PatientBasicInfo patient = patientBasicInfoMapper.findByIdAndDoctorId(patientId, doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "患者不存在"));
        applyPatientRequest(patient, request);
        PatientBasicInfo saved = patientBasicInfoMapper.save(patient);
        patientSkillService.recordPatientProfile(saved, false);
        return toPatientDto(saved);
    }

    @DeleteMapping("/patients/{patientId}")
    public Map<String, Object> deletePatient(@PathVariable Long patientId, HttpSession session) {
        Long doctorId = currentUserId(session);
        if (!patientBasicInfoMapper.existsByIdAndDoctorId(patientId, doctorId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "患者不存在");
        }
        patientBasicInfoMapper.deleteById(patientId);
        return Map.of(
                "success", true,
                "patientId", patientId,
                "message", "患者信息已删除"
        );
    }

    @GetMapping("/sessions")
    public List<ChatSessionDto> sessions(HttpSession session) {
        return collectorAgentService.loadSessions(currentUserId(session));
    }

    @GetMapping("/sessions/{sessionId}")
    public SessionDetailResponse sessionDetail(@PathVariable String sessionId, HttpSession session) {
        return collectorAgentService.loadSessionDetail(currentUserId(session), sessionId);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public Map<String, Object> deleteSession(@PathVariable String sessionId, HttpSession session) {
        collectorAgentService.deleteSession(currentUserId(session), sessionId);
        return Map.of(
                "success", true,
                "sessionId", sessionId,
                "message", "过去会话消息已成功删除"
        );
    }

    @GetMapping("/diagnosis")
    public DiagnosticResponse diagnosis(HttpSession session) {
        return collectorAgentService.loadLatestDiagnosis(currentUserId(session));
    }

    @PostMapping("/consultations")
    public ConsultationResponse collectConsultation(
            @Valid @RequestBody ConsultationRequest request,
            HttpSession session
    ) {
        Long userId = currentUserId(session);
        return collectorAgentService.organize(userId, request);
    }

    @PostMapping("/doctor-review")
    public Map<String, Object> doctorReview(@Valid @RequestBody DoctorReviewRequest request, HttpSession session) {
        FinalDiagnosisRecordDto finalRecord = collectorAgentService.saveDoctorReview(currentUserId(session), request);
        messagingTemplate.convertAndSend("/topic/pipeline", new PipelineEvent(
                "HUMAN_REVIEW_UPDATED",
                "医生已提交人工复核意见",
                100,
                LocalDateTime.now().toString()
        ));

        return Map.of(
                "success", true,
                "message", request.getOpinion() == null || request.getOpinion().isBlank()
                        ? "医生确认 AI 诊断，无异议。"
                        : "医生意见已提交，系统将以人工结论为准。",
                "timestamp", LocalDateTime.now().toString(),
                "finalRecord", finalRecord
        );
    }

    @PostMapping("/simulate")
    public DiagnosticResponse simulate(HttpSession session) {
        currentUserId(session);
        return tracingService.traceWorkflow(
                "MedConsenus Simulate Workflow",
                0L,
                "simulate-demo",
                "simulate",
                () -> {
                    List<PipelineEvent> events = List.of(
                            new PipelineEvent("COLLECTOR", "信息收集 Agent 正在整理主诉与既往史", 18, LocalDateTime.now().toString()),
                            new PipelineEvent("DIAGNOSIS", "Diagnosis Agent 基于 LangGraph + ReAct 生成初步建议", 42, LocalDateTime.now().toString()),
                            new PipelineEvent("REVIEWERS", "Reviewer 并行评审中：GPT / Kimi / GLM", 68, LocalDateTime.now().toString()),
                            new PipelineEvent("DECISION", "Decision Layer 正在执行投票、置信度和风险控制", 86, LocalDateTime.now().toString()),
                            new PipelineEvent("HUMAN_REVIEW", "进入 Human-in-the-loop 医生审核", 100, LocalDateTime.now().toString())
                    );

                    for (PipelineEvent event : events) {
                        messagingTemplate.convertAndSend("/topic/pipeline", event);
                    }

                    return demoResponse("经过多 Agent 评审后，系统建议优先排查社区获得性肺炎，并建议医生结合影像学检查进行最终确认。");
                }
        );
    }

    @PostMapping("/import-case")
    public CaseImportResponse importCase(
            @RequestParam("file") MultipartFile file,
            HttpSession session
    ) throws IOException {
        Long doctorId = currentUserId(session);

        validateImportFile(file);
        return caseImportService.importCase(doctorId, file);
    }

    @PostMapping("/medical-evidence/analyze")
    public MedicalEvidenceAnalysisResponse analyzeMedicalEvidence(
            @RequestParam("file") MultipartFile file,
            HttpSession session
    ) throws IOException {
        currentUserId(session);
        validateImportFile(file);
        return caseImportService.analyzeMedicalEvidence(file);
    }

    @GetMapping("/diagnosis-records")
    public List<FinalDiagnosisRecordDto> diagnosisRecords(HttpSession session) {
        Long doctorId = currentUserId(session);
        return finalDiagnosisRecordMapper.findByUserIdOrderByCreatedAtDesc(doctorId).stream()
                .map(this::toDiagnosisRecordDto)
                .toList();
    }

    @DeleteMapping("/diagnosis-records/{recordId}")
    public Map<String, Object> deleteDiagnosisRecord(@PathVariable Long recordId, HttpSession session) {
        Long doctorId = currentUserId(session);
        FinalDiagnosisRecord record = finalDiagnosisRecordMapper.findById(recordId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "诊断报告不存在"));
        if (!record.getUserId().equals(doctorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权删除该报告");
        }
        finalDiagnosisRecordMapper.deleteById(recordId);
        return Map.of(
                "success", true,
                "recordId", recordId,
                "message", "诊断报告已删除"
        );
    }

    @GetMapping("/doctor-stats")
    public DoctorStatsResponse doctorStats(HttpSession session) {
        Long doctorId = currentUserId(session);
        List<FinalDiagnosisRecord> records = finalDiagnosisRecordMapper.findByUserIdOrderByCreatedAtDesc(doctorId);
        List<PatientBasicInfo> patients = patientBasicInfoMapper.findByDoctorIdOrderByUpdateTimeDesc(doctorId);
        List<ChatSessionDto> sessions = collectorAgentService.loadSessions(doctorId);

        int totalDiagnoses = records.size();
        double averageConfidence = records.stream()
                .filter(r -> r.getConfidence() != null)
                .mapToDouble(FinalDiagnosisRecord::getConfidence)
                .average()
                .orElse(0.0);

        long aiAdopted = records.stream()
                .filter(r -> r.getDoctorOpinion() == null || r.getDoctorOpinion().isBlank())
                .count();
        double aiAdoptionRate = totalDiagnoses > 0 ? (double) aiAdopted / totalDiagnoses : 0.0;

        Map<String, Integer> riskDistribution = records.stream()
                .filter(r -> r.getRiskLevel() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        FinalDiagnosisRecord::getRiskLevel,
                        java.util.stream.Collectors.summingInt(r -> 1)
                ));

        long consistent = records.stream()
                .filter(r -> r.getAiConclusion() != null && r.getFinalConclusion() != null)
                .filter(r -> r.getFinalConclusion().contains(r.getAiConclusion().substring(0, Math.min(20, r.getAiConclusion().length()))))
                .count();
        double diagnosisConsistency = totalDiagnoses > 0 ? (double) consistent / totalDiagnoses : 0.0;

        return new DoctorStatsResponse(
                totalDiagnoses,
                Math.round(averageConfidence * 100.0) / 100.0,
                Math.round(aiAdoptionRate * 100.0) / 100.0,
                riskDistribution,
                sessions.size(),
                patients.size(),
                Math.round(diagnosisConsistency * 100.0) / 100.0
        );
    }

    @PostMapping("/graph-explore")
    public GraphExploreResponse graphExplore(@Valid @RequestBody GraphExploreRequest request, HttpSession session) {
        currentUserId(session);
        List<String> symptoms = List.of(request.query().split("[,，\\s]+"));
        List<MedicalGraphPath> paths = graphReasoningService.reasonBySymptoms(symptoms);

        List<GraphExploreResponse.GraphPathDto> pathDtos = paths.stream()
                .map(p -> new GraphExploreResponse.GraphPathDto(
                        p.symptom(),
                        p.disease(),
                        p.treatments(),
                        p.examinations(),
                        p.confidence()
                ))
                .toList();

        return new GraphExploreResponse(request.query(), pathDtos);
    }

    private FinalDiagnosisRecordDto toDiagnosisRecordDto(FinalDiagnosisRecord record) {
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
                null,
                record.getTreatmentSource(),
                record.getTreatmentAdvice(),
                record.getPatientAccountId(),
                record.getPatientRecordId(),
                record.isPublishedToPatient(),
                record.getPublishedAt() == null ? null : record.getPublishedAt().toString(),
                record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString()
        );
    }

    private DiagnosticResponse demoResponse(String conclusion) {
        return new DiagnosticResponse(
                conclusion,
                0.82,
                "中高风险",
                List.of(
                        "主症状与感染性呼吸系统疾病高度相关。",
                        "目前信息尚可支持初步诊断，但仍建议补充胸片和血氧饱和度。",
                        "Reviewer 投票结果较集中，未出现明显分歧。"
                ),
                List.of(
                        "建议尽快完善血常规、CRP、胸片检查。",
                        "若体温持续升高或呼吸困难加重，应立即转急诊。",
                        "结合既往哮喘病史，谨慎评估是否存在气道高反应。"
                ),
                List.of(
                        new DiagnosticResponse.ReviewerScore("GPT 5.4", 0.84, 0.4, "症状表现典型，同意初步结论"),
                        new DiagnosticResponse.ReviewerScore("Kimi K2.6", 0.79, 0.3, "基本同意，需留意是否有非典型病原体感染"),
                        new DiagnosticResponse.ReviewerScore("GLM", 0.81, 0.3, "同意结论，建议完善辅助检查以排除其他肺部疾病")
                )
        );
    }

    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        Object role = session.getAttribute(SESSION_USER_ROLE);
        if (userId instanceof Long value) {
            if ("PATIENT".equals(role)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "当前账号不是医生");
            }
            return value;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
    }

    private void validateImportFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMPORT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件格式，请上传 PDF、DOCX 或 JPG/PNG 图片");
        }

        if (file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
    }

    private void applyPatientRequest(PatientBasicInfo patient, PatientBasicInfoRequest request) {
        patient.setPatientName(request.getName().trim());
        patient.setGender(request.getGender().trim());
        patient.setAge(request.getAge());
        patient.setWeight(request.getWeight());
        patient.setPhone(blankToNull(request.getPhone()));
        patient.setChiefComplaint(blankToNull(request.getChiefComplaint()));
    }

    private PatientBasicInfoDto toPatientDto(PatientBasicInfo patient) {
        return new PatientBasicInfoDto(
                patient.getId(),
                patient.getPatientName(),
                patient.getGender(),
                patient.getAge(),
                patient.getWeight() == null ? "" : patient.getWeight().toPlainString(),
                patient.getPhone(),
                patient.getChiefComplaint(),
                patient.getCreateTime() == null ? null : patient.getCreateTime().toString(),
                patient.getUpdateTime() == null ? null : patient.getUpdateTime().toString()
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
