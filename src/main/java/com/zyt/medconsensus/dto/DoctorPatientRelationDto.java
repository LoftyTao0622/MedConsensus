package com.zyt.medconsensus.dto;

public record DoctorPatientRelationDto(
        Long id,
        Long doctorId,
        String doctorName,
        String department,
        String title,
        Long patientAccountId,
        String patientName,
        String patientPhone,
        Long patientRecordId,
        String status,
        String updatedAt
) {
}
