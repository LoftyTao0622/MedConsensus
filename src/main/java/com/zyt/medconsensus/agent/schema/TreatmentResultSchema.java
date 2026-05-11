package com.zyt.medconsensus.agent.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TreatmentResultSchema(
        @NotNull @Size(min = 1) List<@NotBlank String> keywords,
        @NotNull @Size(min = 1) List<@NotBlank String> recommendations,
        @NotNull @Size(min = 1) List<@NotBlank String> cautions
) {
}
