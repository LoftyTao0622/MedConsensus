package com.zyt.medconsensus.dto;

public record ChatSessionDto(
        String id,
        String title,
        String status,
        String updatedAt
) {
}
