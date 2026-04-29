package com.zyt.medconsensus.agent;

import com.zyt.medconsensus.agent.schema.ReviewerResultSchema;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.llm.validation.LlmJsonValidator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ReviewerAgent {

    private static final String SYSTEM_PROMPT = """
            你是 Reviewer，请根据病情整理结果和主模型初诊做独立复核。
            仅返回 JSON：score, comment。score 为 0 到 1 的小数。
            不要输出 Markdown，不要输出额外解释。
            """;

    private final MultiModelGateway modelGateway;
    private final LlmJsonValidator llmJsonValidator;

    public ReviewerAgent(MultiModelGateway modelGateway, LlmJsonValidator llmJsonValidator) {
        this.modelGateway = modelGateway;
        this.llmJsonValidator = llmJsonValidator;
    }

    public Map<String, Object> review(
            String displayName,
            MultiModelGateway.ModelSpec modelSpec,
            double weight,
            String chiefComplaint,
            String collectorSummary,
            String preliminaryConclusion,
            double fallbackScore
    ) {
        String content = modelGateway.chat(
                modelSpec,
                SYSTEM_PROMPT,
                List.of(Map.of(
                        "role", "user",
                        "MedContent", "主诉：" + textOrDefault(chiefComplaint, "")
                                + "\n整理摘要：" + textOrDefault(collectorSummary, "")
                                + "\n主模型初诊：" + textOrDefault(preliminaryConclusion, "")
                ))
        );

        ReviewerOutcome outcome = parseReviewerOutcome(content, fallbackScore);
        Map<String, Object> reviewer = new LinkedHashMap<>();
        reviewer.put("name", displayName);
        reviewer.put("score", outcome.score());
        reviewer.put("weight", weight);
        reviewer.put("comment", outcome.comment());
        return reviewer;
    }

    private ReviewerOutcome parseReviewerOutcome(String content, double fallbackScore) {
        if (!StringUtils.hasText(content)) {
            return new ReviewerOutcome(fallbackScore, "模型未返回有效评审内容，沿用主模型置信度。");
        }

        try {
            ReviewerResultSchema result = llmJsonValidator.parseAndValidate(content, ReviewerResultSchema.class);
            return new ReviewerOutcome(result.score(), result.comment().trim());
        } catch (Exception exception) {
            return new ReviewerOutcome(fallbackScore, "评审 JSON 解析失败，沿用主模型置信度。");
        }
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private record ReviewerOutcome(double score, String comment) {
    }
}
