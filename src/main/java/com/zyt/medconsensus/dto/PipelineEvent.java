package com.zyt.medconsensus.dto;

public record PipelineEvent(
        Long userId,
        String sessionId,
        String stage,
        String message,
        int progress,
        String timestamp
) {
}
