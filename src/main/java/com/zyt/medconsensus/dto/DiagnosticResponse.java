package com.zyt.medconsensus.dto;

import java.io.Serializable;
import java.util.List;

public record DiagnosticResponse(
        String conclusion,
        double confidence,
        String riskLevel,
        List<String> structuredAnalysis,
        List<String> suggestions,
        List<ReviewerScore> reviewers
) {
    public record ReviewerScore(
            String name,
            double score,
            double weight,
            String comment
    ) implements Serializable {
    }
}
