package com.zyt.medconsensus.dto;

public record PipelineEvent(
        String stage,
        String message,
        int progress,
        String timestamp
) {
}
