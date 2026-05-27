package com.zyt.medconsensus.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record MedicalEvidenceAnalysisResponse(
        String fileName,
        JsonNode extracted,
        String evidenceText,
        String summary,
        String status,
        String message
) {}
