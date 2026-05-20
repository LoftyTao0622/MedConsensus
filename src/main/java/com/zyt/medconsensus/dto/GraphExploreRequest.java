package com.zyt.medconsensus.dto;

import jakarta.validation.constraints.NotBlank;

public record GraphExploreRequest(
        @NotBlank(message = "查询内容不能为空")
        String query
) {
}
