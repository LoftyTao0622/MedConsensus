package com.zyt.medconsensus.dto;

import java.util.List;

public record PatientConsultationDto(
        Long id,
        String sessionId,
        Long doctorId,
        String doctorName,
        String department,
        String status,
        String question,
        String evidenceStatus,
        String evidenceFileName,
        boolean reportPublished,
        List<MessageHistoryDto> messages,
        String updatedAt
) {
}
