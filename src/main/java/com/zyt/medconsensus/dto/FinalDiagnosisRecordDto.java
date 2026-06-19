package com.zyt.medconsensus.dto;

import java.util.List;

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
        List<String> treatmentKeywords,
        String treatmentSource,
        String treatmentAdvice,
        Long patientAccountId,
        Long patientRecordId,
        boolean publishedToPatient,
        String publishedAt,
        String updatedAt
) {
}
