package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientMessageRequest(
        @NotBlank @Size(max = 4000) String message,
        @jakarta.validation.constraints.Positive Integer expectedRevision
) {
    public PatientMessageRequest(String message) { this(message, null); }
}
