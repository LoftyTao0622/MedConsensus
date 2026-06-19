package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PatientMessageRequest(
        @NotBlank @Size(max = 4000) String message
) {
}
