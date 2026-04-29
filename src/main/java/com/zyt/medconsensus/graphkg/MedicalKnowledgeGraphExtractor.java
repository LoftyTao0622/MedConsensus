package com.zyt.medconsensus.graphkg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.llm.validation.LlmJsonValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class MedicalKnowledgeGraphExtractor {

    private static final String SYSTEM_PROMPT = """
            你是医学知识图谱抽取器。请从医学问答文本中抽取结构化关系，只输出合法 JSON。
            允许的节点类型：Symptom, Disease, Treatment, Examination, Department, RiskFactor, Diet, Exercise。
            允许的关系：
            - Symptom SUGGESTS Disease
            - Disease HAS_SYMPTOM Symptom
            - Disease TREATED_BY Treatment
            - Disease CHECKED_BY Examination
            - Disease BELONGS_TO Department
            - RiskFactor INCREASES_RISK Disease
            - Diet HELPS_TREAT Disease
            - Exercise HELPS_TREAT Disease
            要求：
            1. 只抽取文本明确支持的医学关系，不要编造。
            2. source/target 使用短中文名，例如“肥胖症”“食量大”“游泳”“医院检查”。
            3. relation 必须使用上面的英文枚举。
            4. 输出格式：{"relations":[{"source":"","sourceType":"","relation":"","target":"","targetType":"","confidence":0.0}]}。
            """;

    private final GraphImportProperties properties;
    private final ObjectMapper objectMapper;
    private final LlmJsonValidator validator;

    public MedicalKnowledgeGraphExtractor(
            GraphImportProperties properties,
            ObjectMapper objectMapper,
            LlmJsonValidator validator
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public KnowledgeGraphExtraction extract(String chunkText) {
        if (!StringUtils.hasText(chunkText)) {
            return new KnowledgeGraphExtraction();
        }
        if (!StringUtils.hasText(properties.getLlmApiKey())) {
            throw new IllegalStateException("缺少 app.graph-import.llm-api-key 或环境变量 API_KEY");
        }

        String content = callLlm(chunkText);
        return validator.parseAndValidate(content, KnowledgeGraphExtraction.class);
    }

    private String callLlm(String chunkText) {
        RestClient client = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getLlmBaseUrl()))
                .defaultHeader("Authorization", "Bearer " + properties.getLlmApiKey())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getLlmModel());
        payload.put("temperature", properties.getLlmTemperature());
        payload.put("messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", "医学文本：\n" + chunkText)
        ));

        try {
            String response = client.post()
                    .uri("/chat/completions")
                    .body(payload)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(response);
            return root.path("choices").path(0).path("message").path("content").asText("");
        } catch (Exception exception) {
            throw new IllegalStateException("医学知识图谱关系抽取失败", exception);
        }
    }

    private String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
