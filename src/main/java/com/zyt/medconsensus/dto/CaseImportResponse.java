package com.zyt.medconsensus.dto;

public record CaseImportResponse(
        PatientBasicInfoDto patient,
        FinalDiagnosisRecordDto diagnosisRecord,
        String extractedSummary,
        String message
) {}
