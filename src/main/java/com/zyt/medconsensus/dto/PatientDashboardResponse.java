package com.zyt.medconsensus.dto;

import java.util.List;

public record PatientDashboardResponse(
        AuthResponse patient,
        List<DoctorPatientRelationDto> relations,
        List<PatientConsultationDto> consultations,
        List<PatientReportDto> reports
) {
}
