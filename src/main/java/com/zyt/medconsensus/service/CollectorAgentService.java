package com.zyt.medconsensus.service;

import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.ConsultationResponse;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.DoctorReviewRequest;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.SessionDetailResponse;
import java.util.List;

public interface CollectorAgentService {

    ConsultationResponse organize(Long userId, String sessionId, String userMessage, String patientName);

    List<ChatSessionDto> loadSessions(Long userId);

    List<String> loadMemory(Long userId, String sessionId);

    DiagnosticResponse loadLatestDiagnosis(Long userId);

    SessionDetailResponse loadSessionDetail(Long userId, String sessionId);

    FinalDiagnosisRecordDto saveDoctorReview(Long userId, DoctorReviewRequest request);

    void deleteSession(Long userId, String sessionId);
}
