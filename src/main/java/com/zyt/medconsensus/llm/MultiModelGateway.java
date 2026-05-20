package com.zyt.medconsensus.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.observability.LangSmithTracingService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component

public class MultiModelGateway {

    private final ObjectMapper objectMapper;
    private final LangSmithTracingService tracingService;
    private final ConcurrentHashMap<String, RestClient> clientCache = new ConcurrentHashMap<>();


    public MultiModelGateway(ObjectMapper objectMapper, LangSmithTracingService tracingService) {
        this.objectMapper = objectMapper;
        this.tracingService = tracingService;
    }

    public String chat(ModelSpec spec, String systemPrompt, List<Map<String, String>> messages) {
        if (spec == null || !spec.isConfigured()) {
            return "";
        }

        RestClient client = getOrCreateClient(spec);

        List<Map<String, Object>> payloadMessages = new java.util.ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            payloadMessages.add(Map.<String, Object>of("role", "system", "content", systemPrompt));
        }
        messages.stream()
                .map(this::toOpenAiMessage)
                .forEach(payloadMessages::add);

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
                JsonNode message = root.path("choices").path(0).path("message");
                String content = message.path("content").asText("");
                return StringUtils.hasText(content) ? content : message.path("MedContent").asText("");
            } catch (Exception exception) {
                System.err.println("[MultiModelGateway] chat error for model " + spec.model() + ": " + exception.getMessage());
                exception.printStackTrace();
                return "";
            }
        });
    }

    /**
     * Vision-capable chat: supports multimodal content (text + image_url).
     * The contentParts list should contain maps with "type" ("text" or "image_url")
     * and the corresponding data field.
     */
    public String chatVision(ModelSpec spec, String systemPrompt, List<Map<String, Object>> contentParts) {
        if (spec == null || !spec.isConfigured()) {
            return "";
        }

        RestClient client = getOrCreateClient(spec);

        List<Map<String, Object>> payloadMessages = new java.util.ArrayList<>();
        if (StringUtils.hasText(systemPrompt)) {
            payloadMessages.add(Map.<String, Object>of("role", "system", "content", systemPrompt));
        }
        payloadMessages.add(Map.of("role", "user", "content", contentParts));

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
                JsonNode message = root.path("choices").path(0).path("message");
                String content = message.path("content").asText("");
                return StringUtils.hasText(content) ? content : message.path("MedContent").asText("");
            } catch (Exception exception) {
                System.err.println("[MultiModelGateway] chatVision error for model " + spec.model() + ": " + exception.getMessage());
                exception.printStackTrace();
                return "";
            }
        });
    }

    private RestClient getOrCreateClient(ModelSpec spec) {
        String cacheKey = spec.baseUrl() + "|" + spec.apiKey();
        return clientCache.computeIfAbsent(cacheKey, key ->
                RestClient.builder()
                        .baseUrl(spec.baseUrl())
                        .defaultHeader("Authorization", "Bearer " + spec.apiKey())
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .build()
        );
    }

    private Map<String, Object> toOpenAiMessage(Map<String, String> message) {
        String content = message.get("content");
        if (!StringUtils.hasText(content)) {
            content = message.get("MedContent");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("role", message.getOrDefault("role", "user"));
        result.put("content", content == null ? "" : content);
        return result;
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
