package com.zyt.medconsensus.dto;

public record PatientReportDto(
        Long id,
        String sessionId,
        String doctorName,
        String department,
        String title,
        String chiefComplaint,
        String finalConclusion,
        String doctorOpinion,
        String riskLevel,
        String treatmentAdvice,
        String publishedAt
) {
}
