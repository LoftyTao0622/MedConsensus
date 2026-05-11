package com.zyt.medconsensus.dto;

public record AuthResponse(
        Long id,
        String username,
        String phone,
        String department,
        String title,
        String createdAt,
        String updatedAt
) {
}
