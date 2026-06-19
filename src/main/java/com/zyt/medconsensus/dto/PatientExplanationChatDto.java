package com.zyt.medconsensus.dto;

import java.util.List;

public record PatientExplanationChatDto(
        Long reportId,
        List<MessageHistoryDto> messages
) {
}
