package com.zyt.medconsensus.dto;

import java.time.OffsetDateTime;

public record ReportRevisionDto(
        long recordId, int revision, long doctorId, String sessionId, String chiefComplaint,
        String aiConclusion, String doctorOpinion, String finalConclusion, String riskLevel,
        Double confidence, String treatmentAdvice, OffsetDateTime publishedAt, Long patientRecordId, String patientName
) {}
