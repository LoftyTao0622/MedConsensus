package com.zyt.medconsensus.dto;

public record DoctorConsultationDto(
        Long id,
        String sessionId,
        Long patientAccountId,
        Long patientRecordId,
        String patientName,
        String patientPhone,
        String status,
        String evidenceStatus,
        String evidenceFileName,
        String evidenceText,
        String evidenceSourceId,
        boolean reportReady,
        Long reportId,
        boolean reportPublished,
        String updatedAt
) {
}
