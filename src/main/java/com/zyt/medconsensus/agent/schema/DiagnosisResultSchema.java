package com.zyt.medconsensus.agent.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DiagnosisResultSchema(
        @NotBlank String conclusion,
        @DecimalMin("0.0") @DecimalMax("1.0") double confidence,
        @NotBlank String riskLevel,
        @NotNull @Size(min = 1) List<@NotBlank String> structuredAnalysis,
        @NotNull @Size(min = 1) List<@NotBlank String> suggestions
) {
}
