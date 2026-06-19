package com.zyt.medconsensus.dto;

import java.util.List;

public record DoctorCollaborationResponse(
        String inviteCode,
        List<DoctorPatientRelationDto> relations,
        List<DoctorConsultationDto> consultations
) {
}
