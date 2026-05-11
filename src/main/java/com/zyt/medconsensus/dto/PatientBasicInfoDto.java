package com.zyt.medconsensus.dto;

public record PatientBasicInfoDto(
        Long id,
        String name,
        String gender,
        Integer age,
        String weight,
        String phone,
        String chiefComplaint,
        String createdAt,
        String updatedAt
) {
}
