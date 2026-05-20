package com.zyt.medconsensus.dto;

import java.util.Map;

public record DoctorStatsResponse(
        int totalDiagnoses,
        double averageConfidence,
        double aiAdoptionRate,
        Map<String, Integer> riskDistribution,
        int todaySessions,
        int totalPatients,
        double diagnosisConsistency
) {
}
