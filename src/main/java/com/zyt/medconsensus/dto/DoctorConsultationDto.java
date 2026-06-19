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
        boolean reportReady,
        boolean reportPublished,
        String updatedAt
) {
}
