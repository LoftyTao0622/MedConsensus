package com.zyt.medconsensus.agent.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PatientExplanationResultSchema(
        @NotBlank String answer,
        boolean requiresDoctor,
        @NotNull String urgentWarning
) {
}
