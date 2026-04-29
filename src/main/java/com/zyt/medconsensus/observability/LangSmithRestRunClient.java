package com.zyt.medconsensus.observability;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class LangSmithRestRunClient {

    private static final Logger log = LoggerFactory.getLogger(LangSmithRestRunClient.class);

    private final ObjectMapper objectMapper;
    private final LangSmithProperties properties;
    private final HttpClient httpClient;
    private final String apiKey;

    public LangSmithRestRunClient(ObjectMapper objectMapper, LangSmithProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.apiKey = System.getenv("LANGSMITH_API_KEY");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getTimeout())
                .proxy(ProxySelector.getDefault())
                .build();
    }

    public UUID startRun(String name, String runType, UUID parentRunId, Map<String, Object> inputs, Map<String, Object> metadata) {
        if (!isAvailable()) {
            return null;
        }

        UUID runId = UUID.randomUUID();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", runId.toString());
        payload.put("name", name);
        payload.put("run_type", runType);
        payload.put("start_time", Instant.now().toString());
        payload.put("inputs", safeMap(inputs));
        payload.put("extra", Map.of("metadata", safeMap(metadata)));
        payload.put("session_name", properties.getProject());
        payload.put("project_name", properties.getProject());
        payload.put("serialized", Map.of("name", name));
        if (parentRunId != null) {
            payload.put("parent_run_id", parentRunId.toString());
        }

        send("POST", "/runs", payload);
        return runId;
    }

    public void endRun(UUID runId, Map<String, Object> outputs, Throwable error) {
        if (!isAvailable() || runId == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("end_time", Instant.now().toString());
        payload.put("outputs", safeMap(outputs));
        if (error != null) {
            payload.put("error", error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
        send("PATCH", "/runs/" + runId, payload);
    }

    private boolean isAvailable() {
        return properties.isEnabled()
                && StringUtils.hasText(apiKey)
                && StringUtils.hasText(properties.getApiEndpoint());
    }

    private void send(String method, String path, Map<String, Object> payload) {
        try {
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getApiEndpoint() + path))
                    .timeout(properties.getTimeout())
                    .header("Content-Type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("Accept", "application/json");

            if (StringUtils.hasText(properties.getWorkspaceId())) {
                builder.header("x-tenant-id", properties.getWorkspaceId());
            }
            if (StringUtils.hasText(properties.getOrganizationId())) {
                builder.header("x-organization-id", properties.getOrganizationId());
            }

            HttpRequest request = builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 300) {
                log.warn("LangSmith REST run export failed. method={}, path={}, status={}, body={}",
                        method, path, response.statusCode(), response.body());
            }
        } catch (Exception exception) {
            log.warn("LangSmith REST run export preparation failed. method={}, path={}, message={}",
                    method, path, exception.getMessage());
        }
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }
}
