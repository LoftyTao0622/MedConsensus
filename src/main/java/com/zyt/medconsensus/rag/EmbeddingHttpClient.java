package com.zyt.medconsensus.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EmbeddingHttpClient {

    private final AppEmbeddingProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public EmbeddingHttpClient(AppEmbeddingProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            throw new IllegalStateException("缺少 app.embedding.api-key 或环境变量 API_KEY");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("input", texts);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(trimTrailingSlash(properties.getBaseUrl()) + "/embeddings"))
                    .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload),
                            StandardCharsets.UTF_8
                    ))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Embedding API failed. status="
                        + response.statusCode() + ", body=" + response.body());
            }

            return parseEmbeddings(response.body());
        } catch (Exception exception) {
            throw new IllegalStateException("调用 embedding 模型失败", exception);
        }
    }

    private List<float[]> parseEmbeddings(String responseBody) throws Exception {
        JsonNode data = objectMapper.readTree(responseBody).path("data");
        List<IndexedEmbedding> embeddings = new ArrayList<>();
        for (JsonNode item : data) {
            JsonNode embeddingNode = item.path("embedding");
            float[] vector = new float[embeddingNode.size()];
            for (int i = 0; i < embeddingNode.size(); i++) {
                vector[i] = (float) embeddingNode.get(i).asDouble();
            }
            embeddings.add(new IndexedEmbedding(item.path("index").asInt(embeddings.size()), vector));
        }
        return embeddings.stream()
                .sorted(Comparator.comparingInt(IndexedEmbedding::index))
                .map(IndexedEmbedding::embedding)
                .toList();
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private record IndexedEmbedding(int index, float[] embedding) {
    }
}
