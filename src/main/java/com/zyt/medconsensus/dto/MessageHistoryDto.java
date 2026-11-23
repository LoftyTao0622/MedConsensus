package com.zyt.medconsensus.dto;

public record MessageHistoryDto(
        String role,
        String content,
        boolean patientQuestion
) {
    public MessageHistoryDto(String role, String content) { this(role, content, false); }
}
