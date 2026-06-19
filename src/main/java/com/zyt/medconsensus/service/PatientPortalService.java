package com.zyt.medconsensus.service;

import com.zyt.medconsensus.dto.BindingRequest;
import com.zyt.medconsensus.dto.DoctorCollaborationResponse;
import com.zyt.medconsensus.dto.DoctorPatientRelationDto;
import com.zyt.medconsensus.dto.MedicalEvidenceAnalysisResponse;
import com.zyt.medconsensus.dto.PatientConsultationDto;
import com.zyt.medconsensus.dto.PatientConsultationRequest;
import com.zyt.medconsensus.dto.PatientDashboardResponse;
import com.zyt.medconsensus.dto.PatientExplanationChatDto;
import com.zyt.medconsensus.dto.PatientMessageRequest;
import java.io.IOException;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

public interface PatientPortalService {

    PatientDashboardResponse dashboard(Long patientAccountId);

    DoctorPatientRelationDto requestBinding(Long patientAccountId, BindingRequest request);

    PatientConsultationDto startConsultation(Long patientAccountId, PatientConsultationRequest request);

    PatientConsultationDto answerQuestion(Long patientAccountId, Long consultationId, PatientMessageRequest request);

    MedicalEvidenceAnalysisResponse uploadEvidence(
            Long patientAccountId,
            Long consultationId,
            MultipartFile file
    ) throws IOException;

    PatientExplanationChatDto loadExplanationHistory(Long patientAccountId, Long reportId);

    PatientExplanationChatDto explainPublishedReport(
            Long patientAccountId,
            Long reportId,
            PatientMessageRequest request
    );

    DoctorCollaborationResponse doctorCollaboration(Long doctorId);

    DoctorPatientRelationDto approveBinding(Long doctorId, Long relationId);

    PatientConsultationDto confirmEvidence(Long doctorId, Long consultationId);

    Map<String, Object> publishReport(Long doctorId, Long recordId);
}
