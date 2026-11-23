package com.zyt.medconsensus.dto;

import java.util.List;

public record PatientExplanationChatDto(
        Long reportId,
        List<MessageHistoryDto> messages,
        Integer revision
) {
    public PatientExplanationChatDto(Long reportId, List<MessageHistoryDto> messages) {
        this(reportId, messages, null);
    }
}
