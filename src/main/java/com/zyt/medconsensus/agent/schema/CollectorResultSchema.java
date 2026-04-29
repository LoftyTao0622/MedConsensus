package com.zyt.medconsensus.agent.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CollectorResultSchema(
        @NotBlank String title,
        @NotBlank String chiefComplaint,
        @NotBlank String summary,
        @NotNull @Size(min = 1) List<@NotBlank String> structuredAnalysis,
        @NotNull @Size(min = 1) List<@NotBlank String> followUpQuestions
) {
}
