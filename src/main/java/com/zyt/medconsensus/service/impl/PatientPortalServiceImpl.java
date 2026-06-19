package com.zyt.medconsensus.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.agent.PatientExplanationAgent;
import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.dto.BindingRequest;
import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.ConsultationResponse;
import com.zyt.medconsensus.dto.DoctorCollaborationResponse;
import com.zyt.medconsensus.dto.DoctorConsultationDto;
import com.zyt.medconsensus.dto.DoctorPatientRelationDto;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.MedicalEvidenceAnalysisResponse;
import com.zyt.medconsensus.dto.MessageHistoryDto;
import com.zyt.medconsensus.dto.PatientConsultationDto;
import com.zyt.medconsensus.dto.PatientConsultationRequest;
import com.zyt.medconsensus.dto.PatientDashboardResponse;
import com.zyt.medconsensus.dto.PatientExplanationChatDto;
import com.zyt.medconsensus.dto.PatientMessageRequest;
import com.zyt.medconsensus.dto.PatientReportDto;
import com.zyt.medconsensus.dto.SessionDetailResponse;
import com.zyt.medconsensus.entity.DoctorBasicInfo;
import com.zyt.medconsensus.entity.DoctorPatientRelation;
import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import com.zyt.medconsensus.entity.PatientAccount;
import com.zyt.medconsensus.entity.PatientBasicInfo;
import com.zyt.medconsensus.entity.PatientConsultation;
import com.zyt.medconsensus.mapper.DoctorBasicInfoMapper;
import com.zyt.medconsensus.mapper.DoctorPatientRelationMapper;
import com.zyt.medconsensus.mapper.FinalDiagnosisRecordMapper;
import com.zyt.medconsensus.mapper.PatientAccountMapper;
import com.zyt.medconsensus.mapper.PatientBasicInfoMapper;
import com.zyt.medconsensus.mapper.PatientConsultationMapper;
import com.zyt.medconsensus.llm.AiWorkflowProperties;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.service.CaseImportService;
import com.zyt.medconsensus.service.CollectorAgentService;
import com.zyt.medconsensus.service.PatientPortalService;
import com.zyt.medconsensus.service.PatientSkillService;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PatientPortalServiceImpl implements PatientPortalService {

    private static final String RELATION_PENDING = "PENDING";
    private static final String RELATION_ACTIVE = "ACTIVE";
    private static final String EVIDENCE_NONE = "NONE";
    private static final String EVIDENCE_PENDING = "PENDING_DOCTOR";
    private static final String EVIDENCE_CONFIRMED = "CONFIRMED";
    private static final int EXPLANATION_HISTORY_LIMIT = 20;
    private static final TypeReference<List<MessageHistoryDto>> EXPLANATION_HISTORY_TYPE =
            new TypeReference<>() {
            };

    private final PatientAccountMapper patientAccountMapper;
    private final DoctorBasicInfoMapper doctorBasicInfoMapper;
    private final DoctorPatientRelationMapper relationMapper;
    private final PatientBasicInfoMapper patientBasicInfoMapper;
    private final PatientConsultationMapper consultationMapper;
    private final FinalDiagnosisRecordMapper finalDiagnosisRecordMapper;
    private final CollectorAgentService collectorAgentService;
    private final CaseImportService caseImportService;
    private final PatientSkillService patientSkillService;
    private final PatientExplanationAgent patientExplanationAgent;
    private final AiWorkflowProperties aiWorkflowProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public PatientPortalServiceImpl(
            PatientAccountMapper patientAccountMapper,
            DoctorBasicInfoMapper doctorBasicInfoMapper,
            DoctorPatientRelationMapper relationMapper,
            PatientBasicInfoMapper patientBasicInfoMapper,
            PatientConsultationMapper consultationMapper,
            FinalDiagnosisRecordMapper finalDiagnosisRecordMapper,
            CollectorAgentService collectorAgentService,
            CaseImportService caseImportService,
            PatientSkillService patientSkillService,
            PatientExplanationAgent patientExplanationAgent,
            AiWorkflowProperties aiWorkflowProperties,
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper
    ) {
        this.patientAccountMapper = patientAccountMapper;
        this.doctorBasicInfoMapper = doctorBasicInfoMapper;
        this.relationMapper = relationMapper;
        this.patientBasicInfoMapper = patientBasicInfoMapper;
        this.consultationMapper = consultationMapper;
        this.finalDiagnosisRecordMapper = finalDiagnosisRecordMapper;
        this.collectorAgentService = collectorAgentService;
        this.caseImportService = caseImportService;
        this.patientSkillService = patientSkillService;
        this.patientExplanationAgent = patientExplanationAgent;
        this.aiWorkflowProperties = aiWorkflowProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public PatientDashboardResponse dashboard(Long patientAccountId) {
        PatientAccount patient = requirePatient(patientAccountId);
        List<DoctorPatientRelationDto> relations = relationMapper
                .findByPatientAccountIdOrderByUpdatedAtDesc(patientAccountId)
                .stream()
                .map(this::toRelationDto)
                .toList();
        List<PatientConsultationDto> consultations = consultationMapper
                .findByPatientAccountIdOrderByUpdatedAtDesc(patientAccountId)
                .stream()
                .map(this::toPatientConsultationDto)
                .toList();
        List<PatientReportDto> reports = finalDiagnosisRecordMapper
                .findByPatientAccountIdAndPublishedToPatientTrueOrderByPublishedAtDesc(patientAccountId)
                .stream()
                .map(this::toPatientReportDto)
                .toList();
        return new PatientDashboardResponse(toPatientAuthResponse(patient), relations, consultations, reports);
    }

    @Transactional
    @Override
    public DoctorPatientRelationDto requestBinding(Long patientAccountId, BindingRequest request) {
        requirePatient(patientAccountId);
        String inviteCode = request.inviteCode().trim().toUpperCase();
        DoctorBasicInfo doctor = doctorBasicInfoMapper.findByInviteCode(inviteCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该医生邀请码"));

        DoctorPatientRelation relation = relationMapper
                .findByDoctorIdAndPatientAccountId(doctor.getId(), patientAccountId)
                .orElseGet(DoctorPatientRelation::new);
        relation.setDoctorId(doctor.getId());
        relation.setPatientAccountId(patientAccountId);
        if (!RELATION_ACTIVE.equals(relation.getStatus())) {
            relation.setStatus(RELATION_PENDING);
        }
        return toRelationDto(relationMapper.save(relation));
    }

    @Override
    public PatientConsultationDto startConsultation(
            Long patientAccountId,
            PatientConsultationRequest request
    ) {
        DoctorPatientRelation relation = requireActiveRelation(request.relationId(), patientAccountId);
        PatientAccount patient = requirePatient(patientAccountId);
        PatientBasicInfo patientRecord = requirePatientRecord(relation);

        patientRecord.setChiefComplaint(request.message().trim());
        patientBasicInfoMapper.save(patientRecord);
        patientSkillService.recordPatientProfile(patientRecord, false);

        ConsultationRequest agentRequest = buildAgentRequest(patient, patientRecord, null, request.message());
        ConsultationResponse response = collectorAgentService.organize(relation.getDoctorId(), agentRequest);

        PatientConsultation consultation = new PatientConsultation();
        consultation.setPatientAccountId(patientAccountId);
        consultation.setDoctorId(relation.getDoctorId());
        consultation.setPatientRecordId(patientRecord.getId());
        consultation.setSessionId(response.sessionId());
        consultation.setStatus(normalizeConsultationStatus(response.session().status()));
        consultation.setEvidenceStatus(EVIDENCE_NONE);
        return toPatientConsultationDto(consultationMapper.save(consultation));
    }

    @Override
    public PatientConsultationDto answerQuestion(
            Long patientAccountId,
            Long consultationId,
            PatientMessageRequest request
    ) {
        PatientConsultation consultation = consultationMapper
                .findByIdAndPatientAccountId(consultationId, patientAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该问诊"));
        if (!"NEEDS_PATIENT_REPLY".equals(consultation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "本轮病情信息已提交医生，暂不能继续修改");
        }
        PatientAccount patient = requirePatient(patientAccountId);
        PatientBasicInfo patientRecord = patientBasicInfoMapper.findById(consultation.getPatientRecordId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "患者档案不存在"));

        ConsultationRequest agentRequest = buildAgentRequest(
                patient,
                patientRecord,
                consultation.getSessionId(),
                request.message()
        );
        ConsultationResponse response = collectorAgentService.organize(consultation.getDoctorId(), agentRequest);
        consultation.setStatus(normalizeConsultationStatus(response.session().status()));
        return toPatientConsultationDto(consultationMapper.save(consultation));
    }

    @Override
    public MedicalEvidenceAnalysisResponse uploadEvidence(
            Long patientAccountId,
            Long consultationId,
            MultipartFile file
    ) throws IOException {
        PatientConsultation consultation = consultationMapper
                .findByIdAndPatientAccountId(consultationId, patientAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该问诊"));
        MedicalEvidenceAnalysisResponse analysis = caseImportService.analyzeMedicalEvidence(file);
        consultation.setEvidenceStatus(EVIDENCE_PENDING);
        consultation.setEvidenceFileName(analysis.fileName());
        consultation.setEvidenceText(analysis.evidenceText());
        consultation.setStatus("WAITING_DOCTOR_EVIDENCE_REVIEW");
        consultationMapper.save(consultation);
        return analysis;
    }

    @Override
    public PatientExplanationChatDto loadExplanationHistory(Long patientAccountId, Long reportId) {
        requirePublishedReport(patientAccountId, reportId);
        return new PatientExplanationChatDto(
                reportId,
                readExplanationHistory(patientAccountId, reportId)
        );
    }

    @Override
    public PatientExplanationChatDto explainPublishedReport(
            Long patientAccountId,
            Long reportId,
            PatientMessageRequest request
    ) {
        FinalDiagnosisRecord report = requirePublishedReport(patientAccountId, reportId);
        List<MessageHistoryDto> history = new java.util.ArrayList<>(
                readExplanationHistory(patientAccountId, reportId)
        );
        String question = request.message().trim();

        PatientExplanationAgent.ExplanationOutcome outcome = patientExplanationAgent.explain(
                patientExplanationSpec(),
                reportContext(report),
                history,
                question
        );

        history.add(new MessageHistoryDto("user", question));
        history.add(new MessageHistoryDto("assistant", formatExplanation(outcome)));
        if (history.size() > EXPLANATION_HISTORY_LIMIT) {
            history = new java.util.ArrayList<>(
                    history.subList(history.size() - EXPLANATION_HISTORY_LIMIT, history.size())
            );
        }
        writeExplanationHistory(patientAccountId, reportId, history);
        return new PatientExplanationChatDto(reportId, history);
    }

    @Override
    public DoctorCollaborationResponse doctorCollaboration(Long doctorId) {
        DoctorBasicInfo doctor = requireDoctor(doctorId);
        List<DoctorPatientRelationDto> relations = relationMapper.findByDoctorIdOrderByUpdatedAtDesc(doctorId)
                .stream()
                .map(this::toRelationDto)
                .toList();
        List<DoctorConsultationDto> consultations = consultationMapper.findByDoctorIdOrderByUpdatedAtDesc(doctorId)
                .stream()
                .map(this::toDoctorConsultationDto)
                .toList();
        return new DoctorCollaborationResponse(doctor.getInviteCode(), relations, consultations);
    }

    @Transactional
    @Override
    public DoctorPatientRelationDto approveBinding(Long doctorId, Long relationId) {
        DoctorPatientRelation relation = relationMapper.findByIdAndDoctorId(relationId, doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该绑定申请"));
        PatientAccount patient = requirePatient(relation.getPatientAccountId());

        PatientBasicInfo patientRecord = patientBasicInfoMapper
                .findByDoctorIdAndPatientAccountId(doctorId, patient.getId())
                .orElseGet(PatientBasicInfo::new);
        boolean newPatientRecord = patientRecord.getId() == null;
        patientRecord.setDoctorId(doctorId);
        patientRecord.setPatientAccountId(patient.getId());
        patientRecord.setPatientName(patient.getPatientName());
        patientRecord.setPhone(patient.getPhone());
        patientRecord.setGender(StringUtils.hasText(patient.getGender()) ? patient.getGender() : "未填写");
        patientRecord.setAge(patient.getAge());
        patientRecord.setWeight(patient.getWeight());
        PatientBasicInfo savedPatient = patientBasicInfoMapper.save(patientRecord);
        patientSkillService.recordPatientProfile(savedPatient, newPatientRecord);

        relation.setPatientRecordId(savedPatient.getId());
        relation.setStatus(RELATION_ACTIVE);
        return toRelationDto(relationMapper.save(relation));
    }

    @Override
    public PatientConsultationDto confirmEvidence(Long doctorId, Long consultationId) {
        PatientConsultation consultation = consultationMapper.findByIdAndDoctorId(consultationId, doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该患者问诊"));
        if (!EVIDENCE_PENDING.equals(consultation.getEvidenceStatus())
                || !StringUtils.hasText(consultation.getEvidenceText())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前没有待确认的检查资料");
        }

        PatientAccount patient = requirePatient(consultation.getPatientAccountId());
        PatientBasicInfo patientRecord = patientBasicInfoMapper.findById(consultation.getPatientRecordId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "患者档案不存在"));
        ConsultationRequest request = buildAgentRequest(
                patient,
                patientRecord,
                consultation.getSessionId(),
                "患者已补充检查资料，请结合本轮问诊继续评估。"
        );
        request.setMedicalEvidence(consultation.getEvidenceText());
        request.setMedicalEvidenceFileName(consultation.getEvidenceFileName());
        request.setMedicalEvidenceConfirmed(true);

        ConsultationResponse response = collectorAgentService.organize(doctorId, request);
        consultation.setEvidenceStatus(EVIDENCE_CONFIRMED);
        consultation.setStatus(normalizeConsultationStatus(response.session().status()));
        return toPatientConsultationDto(consultationMapper.save(consultation));
    }

    @Transactional
    @Override
    public Map<String, Object> publishReport(Long doctorId, Long recordId) {
        FinalDiagnosisRecord record = finalDiagnosisRecordMapper.findByIdAndUserId(recordId, doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "诊断报告不存在"));
        if (record.getPatientAccountId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该报告未关联患者账号，无法发布");
        }
        boolean activeRelation = relationMapper
                .findByDoctorIdAndPatientAccountId(doctorId, record.getPatientAccountId())
                .filter(item -> RELATION_ACTIVE.equals(item.getStatus()))
                .isPresent();
        if (!activeRelation) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "医患绑定已失效，不能发布报告");
        }

        record.setPublishedToPatient(true);
        record.setPublishedAt(OffsetDateTime.now());
        finalDiagnosisRecordMapper.save(record);
        consultationMapper.findByDoctorIdAndSessionId(doctorId, record.getSessionId()).ifPresent(consultation -> {
            consultation.setStatus("REPORT_PUBLISHED");
            consultationMapper.save(consultation);
        });
        return Map.of("success", true, "message", "报告已发布给患者", "recordId", recordId);
    }

    private ConsultationRequest buildAgentRequest(
            PatientAccount patient,
            PatientBasicInfo patientRecord,
            String sessionId,
            String message
    ) {
        ConsultationRequest request = new ConsultationRequest();
        request.setSessionId(sessionId);
        request.setMessage(message.trim());
        request.setPatientName(patient.getPatientName());
        request.setPatientPhone(patient.getPhone());
        request.setPatientGender(patient.getGender());
        request.setPatientAge(patient.getAge() == null ? "" : patient.getAge().toString());
        request.setPatientWeight(patient.getWeight() == null ? "" : patient.getWeight().toPlainString());
        request.setChiefComplaint(patientRecord.getChiefComplaint());
        return request;
    }

    private PatientConsultationDto toPatientConsultationDto(PatientConsultation consultation) {
        DoctorBasicInfo doctor = doctorBasicInfoMapper.findById(consultation.getDoctorId()).orElse(null);
        SessionDetailResponse detail = safeSessionDetail(consultation);
        FinalDiagnosisRecordDto finalRecord = detail == null ? null : detail.finalRecord();
        return new PatientConsultationDto(
                consultation.getId(),
                consultation.getSessionId(),
                consultation.getDoctorId(),
                doctor == null ? "医生" : doctor.getUsername(),
                doctor == null ? null : doctor.getDepartment(),
                consultation.getStatus(),
                patientQuestion(detail, consultation.getStatus()),
                consultation.getEvidenceStatus(),
                consultation.getEvidenceFileName(),
                finalRecord != null && finalRecord.publishedToPatient(),
                patientConversation(detail, consultation.getStatus()),
                consultation.getUpdatedAt() == null ? null : consultation.getUpdatedAt().toString()
        );
    }

    private DoctorConsultationDto toDoctorConsultationDto(PatientConsultation consultation) {
        PatientAccount patient = patientAccountMapper.findById(consultation.getPatientAccountId()).orElse(null);
        FinalDiagnosisRecord record = finalDiagnosisRecordMapper
                .findByUserIdAndSessionId(consultation.getDoctorId(), consultation.getSessionId())
                .orElse(null);
        return new DoctorConsultationDto(
                consultation.getId(),
                consultation.getSessionId(),
                consultation.getPatientAccountId(),
                consultation.getPatientRecordId(),
                patient == null ? "患者" : patient.getPatientName(),
                patient == null ? null : patient.getPhone(),
                consultation.getStatus(),
                consultation.getEvidenceStatus(),
                consultation.getEvidenceFileName(),
                consultation.getEvidenceText(),
                record != null,
                record != null && record.isPublishedToPatient(),
                consultation.getUpdatedAt() == null ? null : consultation.getUpdatedAt().toString()
        );
    }

    private DoctorPatientRelationDto toRelationDto(DoctorPatientRelation relation) {
        DoctorBasicInfo doctor = doctorBasicInfoMapper.findById(relation.getDoctorId()).orElse(null);
        PatientAccount patient = patientAccountMapper.findById(relation.getPatientAccountId()).orElse(null);
        return new DoctorPatientRelationDto(
                relation.getId(),
                relation.getDoctorId(),
                doctor == null ? "医生" : doctor.getUsername(),
                doctor == null ? null : doctor.getDepartment(),
                doctor == null ? null : doctor.getTitle(),
                relation.getPatientAccountId(),
                patient == null ? "患者" : patient.getPatientName(),
                patient == null ? null : patient.getPhone(),
                relation.getPatientRecordId(),
                relation.getStatus(),
                relation.getUpdatedAt() == null ? null : relation.getUpdatedAt().toString()
        );
    }

    private PatientReportDto toPatientReportDto(FinalDiagnosisRecord record) {
        DoctorBasicInfo doctor = doctorBasicInfoMapper.findById(record.getUserId()).orElse(null);
        return new PatientReportDto(
                record.getId(),
                record.getSessionId(),
                doctor == null ? "医生" : doctor.getUsername(),
                doctor == null ? null : doctor.getDepartment(),
                doctor == null ? null : doctor.getTitle(),
                record.getChiefComplaint(),
                record.getFinalConclusion(),
                record.getDoctorOpinion(),
                record.getRiskLevel(),
                record.getTreatmentAdvice(),
                record.getPublishedAt() == null ? null : record.getPublishedAt().toString()
        );
    }

    private FinalDiagnosisRecord requirePublishedReport(Long patientAccountId, Long reportId) {
        requirePatient(patientAccountId);
        return finalDiagnosisRecordMapper
                .findByIdAndPatientAccountIdAndPublishedToPatientTrue(reportId, patientAccountId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "未找到已发布给你的诊断报告"
                ));
    }

    private MultiModelGateway.ModelSpec patientExplanationSpec() {
        AiWorkflowProperties.Collector config = aiWorkflowProperties.getPatientExplanation();
        return new MultiModelGateway.ModelSpec(
                defaultIfBlank(config.getApiKey(), aiWorkflowProperties.getApiKey()),
                defaultIfBlank(config.getBaseUrl(), aiWorkflowProperties.getBaseUrl()),
                config.getModel(),
                config.getTemperature()
        );
    }

    private String reportContext(FinalDiagnosisRecord report) {
        return "主诉：" + textOrDefault(report.getChiefComplaint(), "未记录")
                + "\n医生最终结论：" + textOrDefault(report.getFinalConclusion(), "未记录")
                + "\n医生说明：" + textOrDefault(report.getDoctorOpinion(), "医生确认诊断结论")
                + "\n风险等级：" + textOrDefault(report.getRiskLevel(), "未记录")
                + "\n已发布治疗与复诊建议：" + textOrDefault(report.getTreatmentAdvice(), "未记录");
    }

    private String formatExplanation(PatientExplanationAgent.ExplanationOutcome outcome) {
        StringBuilder builder = new StringBuilder(outcome.answer());
        if (StringUtils.hasText(outcome.urgentWarning())) {
            builder.append("\n\n重要提醒：").append(outcome.urgentWarning());
        }
        if (outcome.requiresDoctor()) {
            builder.append("\n\n这个问题需要由发布报告的医生结合你的实际情况进一步确认。");
        }
        builder.append("\n\n本解释仅帮助理解已发布报告，不会修改医生诊断或用药方案。");
        return builder.toString();
    }

    private List<MessageHistoryDto> readExplanationHistory(Long patientAccountId, Long reportId) {
        String value = redisTemplate.opsForValue().get(explanationHistoryKey(patientAccountId, reportId));
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, EXPLANATION_HISTORY_TYPE);
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "报告解释会话读取失败");
        }
    }

    private void writeExplanationHistory(
            Long patientAccountId,
            Long reportId,
            List<MessageHistoryDto> history
    ) {
        try {
            redisTemplate.opsForValue().set(
                    explanationHistoryKey(patientAccountId, reportId),
                    objectMapper.writeValueAsString(history)
            );
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "报告解释会话保存失败");
        }
    }

    private String explanationHistoryKey(Long patientAccountId, Long reportId) {
        return "medconsenus:patient:report-explanation:" + patientAccountId + ":" + reportId;
    }

    private String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private SessionDetailResponse safeSessionDetail(PatientConsultation consultation) {
        try {
            return collectorAgentService.loadSessionDetail(consultation.getDoctorId(), consultation.getSessionId());
        } catch (ResponseStatusException exception) {
            return null;
        }
    }

    private String patientQuestion(SessionDetailResponse detail, String status) {
        if (!"NEEDS_PATIENT_REPLY".equals(status) || detail == null) {
            return null;
        }
        if (detail.diagnosis() != null
                && detail.diagnosis().suggestions() != null
                && !detail.diagnosis().suggestions().isEmpty()) {
            return String.join("\n", detail.diagnosis().suggestions());
        }
        if (detail.history() == null) {
            return "请继续补充症状时间、检查结果、既往病史或当前用药。";
        }
        return detail.history().stream()
                .filter(item -> "assistant".equals(item.role()))
                .map(MessageHistoryDto::content)
                .filter(StringUtils::hasText)
                .reduce((first, second) -> second)
                .orElse("请继续补充症状时间、检查结果、既往病史或当前用药。");
    }

    private List<MessageHistoryDto> patientConversation(SessionDetailResponse detail, String status) {
        if (detail == null || detail.history() == null) {
            return List.of();
        }
        if ("NEEDS_PATIENT_REPLY".equals(status)) {
            return detail.history();
        }

        List<MessageHistoryDto> messages = new java.util.ArrayList<>(
                detail.history().stream()
                        .filter(item -> "user".equals(item.role()))
                        .toList()
        );
        messages.add(new MessageHistoryDto(
                "assistant",
                "本轮病情信息已收集并提交医生审核。医生完成复核并发布后，你可以在诊断报告中查看最终结论。"
        ));
        return messages;
    }

    private String normalizeConsultationStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "WAITING_DOCTOR";
        }
        if (status.contains("补充") || status.contains("追问")) {
            return "NEEDS_PATIENT_REPLY";
        }
        if (status.contains("最终") || status.contains("完成")) {
            return "WAITING_REPORT";
        }
        return "WAITING_DOCTOR";
    }

    private PatientAccount requirePatient(Long patientAccountId) {
        if (patientAccountId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }
        return patientAccountMapper.findById(patientAccountId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "患者账号不存在"));
    }

    private DoctorBasicInfo requireDoctor(Long doctorId) {
        return doctorBasicInfoMapper.findById(doctorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "医生账号不存在"));
    }

    private DoctorPatientRelation requireActiveRelation(Long relationId, Long patientAccountId) {
        return relationMapper.findByIdAndPatientAccountId(relationId, patientAccountId)
                .filter(item -> RELATION_ACTIVE.equals(item.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "请先完成医生绑定"));
    }

    private PatientBasicInfo requirePatientRecord(DoctorPatientRelation relation) {
        if (relation.getPatientRecordId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "医生尚未完成患者档案绑定");
        }
        return patientBasicInfoMapper.findByIdAndDoctorId(relation.getPatientRecordId(), relation.getDoctorId())
                .filter(item -> Objects.equals(item.getPatientAccountId(), relation.getPatientAccountId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "患者档案不存在"));
    }

    private AuthResponse toPatientAuthResponse(PatientAccount patient) {
        return new AuthResponse(
                patient.getId(),
                patient.getPatientName(),
                patient.getPhone(),
                "PATIENT",
                null,
                null,
                patient.getGender(),
                patient.getAge(),
                patient.getWeight() == null ? null : patient.getWeight().toPlainString(),
                null,
                patient.getCreatedAt() == null ? null : patient.getCreatedAt().toString(),
                patient.getUpdatedAt() == null ? null : patient.getUpdatedAt().toString()
        );
    }
}
