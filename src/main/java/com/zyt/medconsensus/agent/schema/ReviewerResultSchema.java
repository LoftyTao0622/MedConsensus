package com.zyt.medconsensus.agent.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewerResultSchema(
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double score,
        @NotBlank String comment
) {
}
