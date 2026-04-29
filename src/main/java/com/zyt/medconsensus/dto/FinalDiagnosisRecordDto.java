package com.zyt.medconsensus.dto;

public record FinalDiagnosisRecordDto(
        Long id,
        String sessionId,
        String chiefComplaint,
        String aiConclusion,
        String doctorOpinion,
        String finalConclusion,
        String riskLevel,
        Double confidence,
        String reviewStatus,
        String updatedAt
) {
}
