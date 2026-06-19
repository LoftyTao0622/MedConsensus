package com.zyt.medconsensus.agent;

import com.zyt.medconsensus.agent.schema.PatientExplanationResultSchema;
import com.zyt.medconsensus.dto.MessageHistoryDto;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.llm.validation.LlmJsonValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PatientExplanationAgent {

    private static final String SYSTEM_PROMPT = """
            你是 PatientExplanationAgent，只负责用通俗中文解释医生已经发布给患者的诊断报告。
            你不能新增、修改、否定或推翻报告中的诊断；不能提出新的诊断结论。
            你不能开药，不能建议新增、停用、更换药物，也不能调整剂量、频次或疗程。
            可以解释报告中已经写明的医学术语、检查意义、治疗说明和风险提醒，但不得补充报告没有写明的方案。
            如果患者问题超出报告内容，requiresDoctor 必须为 true，并明确建议咨询发布报告的医生。
            如果患者描述胸痛、呼吸困难、意识改变、严重过敏、持续高热等紧急信号，在 urgentWarning 中提醒立即急诊。
            只输出 JSON：answer, requiresDoctor, urgentWarning。urgentWarning 无内容时返回空字符串。
            """;

    private final MultiModelGateway modelGateway;
    private final LlmJsonValidator llmJsonValidator;

    public PatientExplanationAgent(
            MultiModelGateway modelGateway,
            LlmJsonValidator llmJsonValidator
    ) {
        this.modelGateway = modelGateway;
        this.llmJsonValidator = llmJsonValidator;
    }

    public ExplanationOutcome explain(
            MultiModelGateway.ModelSpec modelSpec,
            String reportContext,
            List<MessageHistoryDto> history,
            String question
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (MessageHistoryDto item : history) {
            messages.add(Map.of(
                    "role", "assistant".equals(item.role()) ? "assistant" : "user",
                    "content", item.content()
            ));
        }
        messages.add(Map.of(
                "role", "user",
                "content", "已发布报告：\n" + reportContext + "\n\n患者本轮问题：" + question
        ));

        String content = modelGateway.chat(modelSpec, SYSTEM_PROMPT, messages);
        if (!StringUtils.hasText(content)) {
            return fallback();
        }

        try {
            PatientExplanationResultSchema result =
                    llmJsonValidator.parseAndValidate(content, PatientExplanationResultSchema.class);
            if (containsUnsafeInstruction(result.answer())) {
                return fallback();
            }
            return new ExplanationOutcome(
                    result.answer().trim(),
                    result.requiresDoctor(),
                    result.urgentWarning().trim()
            );
        } catch (Exception exception) {
            return fallback();
        }
    }

    private boolean containsUnsafeInstruction(String answer) {
        String normalized = answer == null ? "" : answer.replaceAll("\\s+", "");
        return normalized.contains("建议停药")
                || normalized.contains("建议加量")
                || normalized.contains("建议减量")
                || normalized.contains("改用")
                || normalized.contains("诊断为")
                || normalized.contains("处方");
    }

    private ExplanationOutcome fallback() {
        return new ExplanationOutcome(
                "我目前无法在不超出已发布报告的前提下准确解释这个问题。请联系发布报告的医生，由医生结合你的实际情况说明。",
                true,
                ""
        );
    }

    public record ExplanationOutcome(
            String answer,
            boolean requiresDoctor,
            String urgentWarning
    ) {
    }
}
