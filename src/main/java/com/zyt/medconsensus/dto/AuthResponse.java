package com.zyt.medconsensus.dto;

public record AuthResponse(
        Long id,
        String username,
        String phone,
        String role,
        String department,
        String title,
        String gender,
        Integer age,
        String weight,
        String inviteCode,
        String createdAt,
        String updatedAt
) {
}
