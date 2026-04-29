package com.zyt.medconsensus.dto;

import java.util.List;

public record SessionDetailResponse(
        String sessionId,
        String title,
        String status,
        String updatedAt,
        DiagnosticResponse diagnosis,
        List<MessageHistoryDto> history,
        FinalDiagnosisRecordDto finalRecord
) {
}
