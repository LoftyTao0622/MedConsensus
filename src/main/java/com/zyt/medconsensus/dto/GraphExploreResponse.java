package com.zyt.medconsensus.dto;

import java.util.List;

public record GraphExploreResponse(
        String query,
        List<GraphPathDto> paths
) {
    public record GraphPathDto(
            String symptom,
            String disease,
            List<String> treatments,
            List<String> examinations,
            double confidence
    ) {
    }
}
