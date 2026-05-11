package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.ConsultationResponse;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.DoctorReviewRequest;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.PatientBasicInfoDto;
import com.zyt.medconsensus.dto.PatientBasicInfoRequest;
import com.zyt.medconsensus.dto.PipelineEvent;
import com.zyt.medconsensus.dto.SessionDetailResponse;
import com.zyt.medconsensus.entity.PatientBasicInfo;
import com.zyt.medconsensus.mapper.PatientBasicInfoMapper;
import com.zyt.medconsensus.observability.LangSmithTracingService;
import com.zyt.medconsensus.service.CollectorAgentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/workspace")
public class MedicalWorkspaceController {

    private static final String SESSION_USER_ID = "CURRENT_USER_ID";

    private final SimpMessagingTemplate messagingTemplate;
    private final CollectorAgentService collectorAgentService;
    private final PatientBasicInfoMapper patientBasicInfoMapper;
    private final LangSmithTracingService tracingService;

    public MedicalWorkspaceController(
            SimpMessagingTemplate messagingTemplate,
            CollectorAgentService collectorAgentService,
            PatientBasicInfoMapper patientBasicInfoMapper,
            LangSmithTracingService tracingService
    ) {
        this.messagingTemplate = messagingTemplate;
        this.collectorAgentService = collectorAgentService;
        this.patientBasicInfoMapper = patientBasicInfoMapper;
        this.tracingService = tracingService;
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
        return toPatientDto(patientBasicInfoMapper.save(patient));
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
        return toPatientDto(patientBasicInfoMapper.save(patient));
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
        return collectorAgentService.organize(
                userId,
                request.getSessionId(),
                request.getMessage(),
                request.getPatientName()
        );
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
    public DiagnosticResponse simulate() {
        return tracingService.traceWorkflow(
                "MedConsenus Simulate Workflow",
                0L,
                "simulate-demo",
                "simulate",
                () -> {
                    List<PipelineEvent> events = List.of(
                            new PipelineEvent("COLLECTOR", "信息收集 Agent 正在整理主诉与既往史", 18, LocalDateTime.now().toString()),
                            new PipelineEvent("DIAGNOSIS", "Diagnosis Agent 基于 LangGraph + ReAct 生成初步建议", 42, LocalDateTime.now().toString()),
                            new PipelineEvent("REVIEWERS", "Reviewer 并行评审中：Kimi / GLM / Qwen", 68, LocalDateTime.now().toString()),
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
                        new DiagnosticResponse.ReviewerScore("Qwen3.6-Plus", 0.84, 0.4),
                        new DiagnosticResponse.ReviewerScore("Kimi K2.6", 0.79, 0.3),
                        new DiagnosticResponse.ReviewerScore("GLM", 0.81, 0.3)
                )
        );
    }

    private Long currentUserId(HttpSession session) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        if (userId instanceof Long value) {
            return value;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
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
