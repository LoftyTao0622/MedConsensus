package com.zyt.medconsensus.dto;

public record AuthResponse(
        Long id,
        String username,
        Integer age,
        String weight,
        String phone,
        String gender,
        String createdAt,
        String updatedAt
) {
}
