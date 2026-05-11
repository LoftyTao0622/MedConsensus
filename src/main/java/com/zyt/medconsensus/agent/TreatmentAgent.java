package com.zyt.medconsensus.agent;

import com.zyt.medconsensus.agent.schema.TreatmentResultSchema;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.llm.validation.LlmJsonValidator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class TreatmentAgent {

    private static final String SYSTEM_PROMPT = """
            你是 Treatment Agent。请根据医生最终诊断结果生成给医生参考的开药/治疗说明。
            只输出 JSON：keywords, recommendations, cautions。
            keywords、recommendations、cautions 都必须是字符串数组。
            不要输出 Markdown，不要给出绝对医嘱，不要替代医生判断。
            recommendations 应包含用药方向、检查/随访或非药物处置建议。
            cautions 应包含禁忌、过敏史、孕产/儿童/老年等特殊人群、病情加重需急诊等提醒。
            """;

    private final MultiModelGateway modelGateway;
    private final LlmJsonValidator llmJsonValidator;

    public TreatmentAgent(MultiModelGateway modelGateway, LlmJsonValidator llmJsonValidator) {
        this.modelGateway = modelGateway;
        this.llmJsonValidator = llmJsonValidator;
    }

    public TreatmentOutcome fromDatabase(
            List<String> keywords,
            List<String> databaseRecommendations,
            String finalDiagnosis
    ) {
        List<String> recommendations = new ArrayList<>();
        recommendations.addAll(databaseRecommendations);
        recommendations.add("以上为数据库命中的开药/治疗推荐，请医生结合年龄、体重、过敏史、肝肾功能和当地指南确认剂量与疗程。");

        List<String> cautions = List.of(
                "用药前需确认药物过敏史、妊娠/哺乳状态、儿童或老年剂量调整需求。",
                "若出现持续高热、呼吸困难、胸痛加重、血氧下降或意识改变，应立即转急诊处理。"
        );

        return new TreatmentOutcome(
                keywords,
                recommendations,
                cautions,
                "DATABASE",
                formatAdvice("数据库推荐", finalDiagnosis, merge(recommendations, cautions))
        );
    }

    public TreatmentOutcome inferWithModel(
            MultiModelGateway.ModelSpec modelSpec,
            List<String> keywords,
            String finalDiagnosis,
            String chiefComplaint,
            String riskLevel
    ) {
        String content = modelGateway.chat(
                modelSpec,
                SYSTEM_PROMPT,
                List.of(Map.of(
                        "role", "user",
                        "MedContent", "关键词：" + keywords
                                + "\n最终诊断：" + textOrDefault(finalDiagnosis, "")
                                + "\n主诉：" + textOrDefault(chiefComplaint, "")
                                + "\n风险等级：" + textOrDefault(riskLevel, "待评估")
                ))
        );

        TreatmentResultSchema result = parseTreatmentResult(content, keywords, finalDiagnosis);
        List<String> recommendations = result.recommendations();
        List<String> cautions = result.cautions();

        return new TreatmentOutcome(
                result.keywords(),
                recommendations,
                cautions,
                "MIMO",
                formatAdvice("MiMo-V2.5-Pro 推理", finalDiagnosis, merge(recommendations, cautions))
        );
    }

    private TreatmentResultSchema parseTreatmentResult(
            String content,
            List<String> fallbackKeywords,
            String finalDiagnosis
    ) {
        if (StringUtils.hasText(content)) {
            try {
                return llmJsonValidator.parseAndValidate(content, TreatmentResultSchema.class);
            } catch (Exception ignored) {
                // Fall through to deterministic safety advice.
            }
        }

        return new TreatmentResultSchema(
                fallbackKeywords.isEmpty() ? List.of("未识别明确疾病关键词") : fallbackKeywords,
                List.of(
                        "建议医生依据最终诊断、检查结果和患者基础情况选择对因治疗方案。",
                        "开药前请补充或核对过敏史、当前用药、肝肾功能、妊娠/哺乳状态和特殊人群剂量。"
                ),
                List.of(
                        "当前模型未返回稳定治疗 JSON，系统保留通用安全提醒。",
                        "若症状进展或存在高风险体征，应优先线下复诊或急诊评估。"
                )
        );
    }

    private List<String> merge(List<String> recommendations, List<String> cautions) {
        List<String> merged = new ArrayList<>(recommendations);
        merged.addAll(cautions);
        return merged;
    }

    private String formatAdvice(String source, String finalDiagnosis, List<String> items) {
        StringBuilder builder = new StringBuilder();
        builder.append("来源：").append(source).append("\n");
        builder.append("最终诊断：").append(textOrDefault(finalDiagnosis, "未记录")).append("\n");
        for (String item : items) {
            builder.append("• ").append(item).append("\n");
        }
        return builder.toString().trim();
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    public record TreatmentOutcome(
            List<String> keywords,
            List<String> recommendations,
            List<String> cautions,
            String source,
            String advice
    ) {
    }
}
