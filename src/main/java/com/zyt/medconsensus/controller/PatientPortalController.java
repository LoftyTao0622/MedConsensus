package com.zyt.medconsensus.controller;

import com.zyt.medconsensus.dto.BindingRequest;
import com.zyt.medconsensus.dto.DoctorCollaborationResponse;
import com.zyt.medconsensus.dto.DoctorPatientRelationDto;
import com.zyt.medconsensus.dto.MedicalEvidenceAnalysisResponse;
import com.zyt.medconsensus.dto.PatientConsultationDto;
import com.zyt.medconsensus.dto.PatientConsultationRequest;
import com.zyt.medconsensus.dto.PatientDashboardResponse;
import com.zyt.medconsensus.dto.PatientExplanationChatDto;
import com.zyt.medconsensus.dto.PatientMessageRequest;
import com.zyt.medconsensus.service.PatientPortalService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class PatientPortalController {

    private static final String SESSION_USER_ID = "CURRENT_USER_ID";
    private static final String SESSION_USER_ROLE = "CURRENT_USER_ROLE";
    private static final List<String> ALLOWED_IMPORT_TYPES = List.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/jpeg",
            "image/jpg",
            "image/png"
    );

    private final PatientPortalService patientPortalService;

    public PatientPortalController(PatientPortalService patientPortalService) {
        this.patientPortalService = patientPortalService;
    }

    @GetMapping("/patient/dashboard")
    public PatientDashboardResponse patientDashboard(HttpSession session) {
        return patientPortalService.dashboard(currentPatientId(session));
    }

    @PostMapping("/patient/bindings")
    public DoctorPatientRelationDto requestBinding(
            @Valid @RequestBody BindingRequest request,
            HttpSession session
    ) {
        return patientPortalService.requestBinding(currentPatientId(session), request);
    }

    @PostMapping("/patient/consultations")
    public PatientConsultationDto startConsultation(
            @Valid @RequestBody PatientConsultationRequest request,
            HttpSession session
    ) {
        return patientPortalService.startConsultation(currentPatientId(session), request);
    }

    @PostMapping("/patient/consultations/{consultationId}/messages")
    public PatientConsultationDto answerQuestion(
            @PathVariable Long consultationId,
            @Valid @RequestBody PatientMessageRequest request,
            HttpSession session
    ) {
        return patientPortalService.answerQuestion(currentPatientId(session), consultationId, request);
    }

    @PostMapping("/patient/consultations/{consultationId}/evidence")
    public MedicalEvidenceAnalysisResponse uploadEvidence(
            @PathVariable Long consultationId,
            @RequestParam("file") MultipartFile file,
            HttpSession session
    ) throws IOException {
        validateImportFile(file);
        return patientPortalService.uploadEvidence(currentPatientId(session), consultationId, file);
    }

    @GetMapping("/patient/reports/{reportId}/explanations")
    public PatientExplanationChatDto explanationHistory(
            @PathVariable Long reportId,
            HttpSession session
    ) {
        return patientPortalService.loadExplanationHistory(currentPatientId(session), reportId);
    }

    @PostMapping("/patient/reports/{reportId}/explanations")
    public PatientExplanationChatDto explainPublishedReport(
            @PathVariable Long reportId,
            @Valid @RequestBody PatientMessageRequest request,
            HttpSession session
    ) {
        return patientPortalService.explainPublishedReport(
                currentPatientId(session),
                reportId,
                request
        );
    }

    @GetMapping("/workspace/collaboration")
    public DoctorCollaborationResponse doctorCollaboration(HttpSession session) {
        return patientPortalService.doctorCollaboration(currentDoctorId(session));
    }

    @PostMapping("/workspace/collaboration/bindings/{relationId}/approve")
    public DoctorPatientRelationDto approveBinding(
            @PathVariable Long relationId,
            HttpSession session
    ) {
        return patientPortalService.approveBinding(currentDoctorId(session), relationId);
    }

    @PostMapping("/workspace/collaboration/consultations/{consultationId}/confirm-evidence")
    public PatientConsultationDto confirmEvidence(
            @PathVariable Long consultationId,
            HttpSession session
    ) {
        return patientPortalService.confirmEvidence(currentDoctorId(session), consultationId);
    }

    @PostMapping("/workspace/diagnosis-records/{recordId}/publish")
    public Map<String, Object> publishReport(
            @PathVariable Long recordId,
            HttpSession session
    ) {
        return patientPortalService.publishReport(currentDoctorId(session), recordId);
    }

    private Long currentPatientId(HttpSession session) {
        return currentRoleId(session, "PATIENT", "当前账号不是患者");
    }

    private Long currentDoctorId(HttpSession session) {
        return currentRoleId(session, "DOCTOR", "当前账号不是医生");
    }

    private Long currentRoleId(HttpSession session, String role, String message) {
        Object userId = session.getAttribute(SESSION_USER_ID);
        Object currentRole = session.getAttribute(SESSION_USER_ROLE);
        if (!(userId instanceof Long value)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "当前未登录");
        }
        if (!role.equals(currentRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, message);
        }
        return value;
    }

    private void validateImportFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMPORT_TYPES.contains(contentType.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的文件格式，请上传 PDF、DOCX 或 JPG/PNG 图片");
        }
    }
}
