package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PatientConsultationRequest(
        @NotNull Long relationId,
        @NotBlank @Size(max = 4000) String message
) {
}
