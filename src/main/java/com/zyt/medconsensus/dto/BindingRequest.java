package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BindingRequest(
        @NotBlank
        @Size(max = 20)
        String inviteCode
) {
}
