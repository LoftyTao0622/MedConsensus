package com.zyt.medconsensus.dto;

import java.util.List;

public record ConsultationResponse(
        String sessionId,
        String chiefComplaint,
        ChatSessionDto session,
        DiagnosticResponse diagnosis,
        List<String> memory
) {
}
