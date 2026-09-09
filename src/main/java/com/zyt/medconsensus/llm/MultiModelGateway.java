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
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component

public class MultiModelGateway {

    private static final Logger log = LoggerFactory.getLogger(MultiModelGateway.class);

    private final ObjectMapper objectMapper;
    private final LangSmithTracingService tracingService;
    private final ConcurrentHashMap<String, RestClient> clientCache = new ConcurrentHashMap<>();


    public MultiModelGateway(ObjectMapper objectMapper, LangSmithTracingService tracingService) {
        this.objectMapper = objectMapper;
        this.tracingService = tracingService;
    }

    public String chat(ModelSpec spec, String systemPrompt, List<Map<String, String>> messages) {
        if (spec == null || !spec.isConfigured()) {
            throw new ModelGatewayException("模型未配置");
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
                    throw new ModelGatewayException("模型返回空响应");
                }

                JsonNode root = objectMapper.readTree(response);
                JsonNode message = root.path("choices").path(0).path("message");
                String content = message.path("content").asText("");
                return StringUtils.hasText(content) ? content : message.path("MedContent").asText("");
            } catch (ModelGatewayException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error("Model call failed model={} baseUrl={}", spec.model(), spec.baseUrl(), exception);
                throw new ModelGatewayException("模型调用失败，请稍后重试", exception);
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
            throw new ModelGatewayException("模型未配置");
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
                    throw new ModelGatewayException("模型返回空响应");
                }

                JsonNode root = objectMapper.readTree(response);
                JsonNode message = root.path("choices").path(0).path("message");
                String content = message.path("content").asText("");
                return StringUtils.hasText(content) ? content : message.path("MedContent").asText("");
            } catch (ModelGatewayException exception) {
                throw exception;
            } catch (Exception exception) {
                log.error("Vision model call failed model={} baseUrl={}", spec.model(), spec.baseUrl(), exception);
                throw new ModelGatewayException("视觉模型调用失败，请稍后重试", exception);
            }
        });
    }

    private RestClient getOrCreateClient(ModelSpec spec) {
        String cacheKey = spec.baseUrl() + "|" + spec.apiKey();
        return clientCache.computeIfAbsent(cacheKey, key ->
                RestClient.builder()
                        .baseUrl(spec.baseUrl())
                        .requestFactory(requestFactory())
                        .defaultHeader("Authorization", "Bearer " + spec.apiKey())
                        .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .build()
        );
    }

    private SimpleClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofSeconds(10));
        factory.setReadTimeout(java.time.Duration.ofSeconds(60));
        return factory;
    }

    public static class ModelGatewayException extends RuntimeException {
        public ModelGatewayException(String message) { super(message); }
        public ModelGatewayException(String message, Throwable cause) { super(message, cause); }
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
