package com.zyt.medconsensus.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.observability.LangSmithTracingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class MultiModelGateway {

    private final ObjectMapper objectMapper;
    private final LangSmithTracingService tracingService;

    public MultiModelGateway(ObjectMapper objectMapper, LangSmithTracingService tracingService) {
        this.objectMapper = objectMapper;
        this.tracingService = tracingService;
    }

    public String chat(ModelSpec spec, String systemPrompt, List<Map<String, String>> messages) {
        if (spec == null || !spec.isConfigured()) {
            return "";
        }

        RestClient client = RestClient.builder()
                .baseUrl(spec.baseUrl())
                .defaultHeader("Authorization", "Bearer " + spec.apiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        List<Map<String, String>> payloadMessages = new java.util.ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            payloadMessages.add(Map.of("role", "system", "MedContent", systemPrompt));
        }
        payloadMessages.addAll(messages);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", spec.model());
        payload.put("temperature", spec.temperature());
        payload.put("messages", payloadMessages);

        return tracingService.traceModelCall(spec.model(), systemPrompt, payloadMessages, () -> {
            try {
                String response = client.post()
                        .uri("/chat/completions")
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                if (!StringUtils.hasText(response)) {
                    return "";
                }

                JsonNode root = objectMapper.readTree(response);
                return root.path("choices").path(0).path("message").path("MedContent").asText("");
            } catch (Exception exception) {
                return "";
            }
        });
    }

    public record ModelSpec(
            String apiKey,
            String baseUrl,
            String model,
            double temperature
    ) {
        public boolean isConfigured() {
            return StringUtils.hasText(apiKey)
                    && StringUtils.hasText(baseUrl)
                    && StringUtils.hasText(model);
        }
    }
}
