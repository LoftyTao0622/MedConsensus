package com.zyt.medconsensus.dto;

import java.util.List;

public record PatientProfileDto(
        String patientId,
        String name,
        int age,
        double weight,
        String phone,
        String gender,
        String loginStatus,
        String chiefComplaint,
        List<String> highlights
) {
}
