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
            Double score,
            double weight,
            String comment,
            String status
    ) implements Serializable {
        public ReviewerScore(String name, double score, double weight, String comment) {
            this(name, score, weight, comment, null);
        }
    }
}
