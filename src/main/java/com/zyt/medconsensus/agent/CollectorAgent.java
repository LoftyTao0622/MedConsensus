package com.zyt.medconsensus.agent;

import com.zyt.medconsensus.agent.schema.CollectorResultSchema;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.llm.validation.LlmJsonValidator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CollectorAgent {

    private static final String SYSTEM_PROMPT = """
            你是信息收集/病情整理 Agent，请整理患者输入和会话记忆，并返回 JSON。
            字段固定为：title, chiefComplaint, summary, structuredAnalysis, followUpQuestions。
            structuredAnalysis 和 followUpQuestions 必须是字符串数组。
            title 应简洁概括本次咨询主题；chiefComplaint 应保留患者主诉核心信息。
            仅返回合法 JSON，不要输出 Markdown。
            """;

    private final MultiModelGateway modelGateway;
    private final LlmJsonValidator llmJsonValidator;

    public CollectorAgent(MultiModelGateway modelGateway, LlmJsonValidator llmJsonValidator) {
        this.modelGateway = modelGateway;
        this.llmJsonValidator = llmJsonValidator;
    }

    public CollectorOutcome collect(
            MultiModelGateway.ModelSpec modelSpec,
            String latestInput,
            List<String> memory
    ) {
        String content = modelGateway.chat(
                modelSpec,
                SYSTEM_PROMPT,
                List.of(Map.of(
                        "role", "user",
                        "MedContent", "最新输入：" + textOrDefault(latestInput, "")
                                + "\n\n完整会话记忆：\n" + String.join("\n", memory)
                ))
        );

        return parseCollectorResult(content, latestInput);
    }

    private CollectorOutcome parseCollectorResult(String content, String fallbackMessage) {
        if (!StringUtils.hasText(content)) {
            return fallbackCollector(fallbackMessage);
        }

        try {
            CollectorResultSchema result = llmJsonValidator.parseAndValidate(content, CollectorResultSchema.class);
            return new CollectorOutcome(
                    result.title().trim(),
                    result.chiefComplaint().trim(),
                    result.summary().trim(),
                    result.structuredAnalysis(),
                    result.followUpQuestions()
            );
        } catch (Exception exception) {
            return fallbackCollector(fallbackMessage);
        }
    }

    private CollectorOutcome fallbackCollector(String message) {
        return new CollectorOutcome(
                summarizeTitle(message),
                message,
                "已根据患者输入完成病情整理，建议进一步补充病程、检查与既往史。",
                List.of(
                        "系统已提取当前主诉与核心症状。",
                        "建议继续补充起病时间、严重程度、检查结果和既往病史。"
                ),
                List.of(
                        "症状从什么时候开始，近几天有无变化？",
                        "是否做过血常规、胸片、CT 或其他相关检查？",
                        "目前是否已用药，是否存在慢病、过敏史或近期就诊史？"
                )
        );
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String summarizeTitle(String message) {
        String cleaned = textOrDefault(message, "新咨询").replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 12 ? cleaned : cleaned.substring(0, 12) + "...";
    }

    public record CollectorOutcome(
            String title,
            String chiefComplaint,
            String summary,
            List<String> structuredAnalysis,
            List<String> followUpQuestions
    ) {
    }
}
