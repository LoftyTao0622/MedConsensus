package com.zyt.medconsensus.agent;

import com.zyt.medconsensus.agent.schema.DiagnosisResultSchema;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.llm.validation.LlmJsonValidator;
import com.zyt.medconsensus.tool.MedicalWorkflowTools;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DiagnosisAgent {

    private static final String SYSTEM_PROMPT = """
            你是 Diagnosis Agent，请根据病情整理结果生成初步诊断建议，并以 JSON 返回：
            conclusion, confidence, riskLevel, structuredAnalysis, suggestions。
            confidence 为 0 到 1 的小数；structuredAnalysis、suggestions 为字符串数组。
            如果提供了“患者个人 skill”，它是该患者长期积累的基础信息、病史、用药史、家族史和生活习惯，
            请把它作为诊断推理的个体化背景，明确考虑慢病、过敏、职业暴露、生活习惯等风险修饰因素。
            如果提供了 Neo4j 医学知识图谱路径，请优先结合“症状→疾病→治疗/检查”的多跳推理依据，
            但不要把图谱结果当作唯一结论，仍需结合患者主诉和病情整理综合判断。
            仅返回合法 JSON，不要输出 Markdown。
            """;

    private final MultiModelGateway modelGateway;
    private final LlmJsonValidator llmJsonValidator;
    private final MedicalWorkflowTools tools;

    public DiagnosisAgent(
            MultiModelGateway modelGateway,
            LlmJsonValidator llmJsonValidator,
            MedicalWorkflowTools tools
    ) {
        this.modelGateway = modelGateway;
        this.llmJsonValidator = llmJsonValidator;
        this.tools = tools;
    }

    public DiagnosisOutcome diagnose(
            MultiModelGateway.ModelSpec modelSpec,
            String chiefComplaint,
            String collectorSummary,
            List<String> structuredInformation,
            List<String> graphEvidence,
            String patientSkill
    ) {
        String content = modelGateway.chat(
                modelSpec,
                SYSTEM_PROMPT,
                List.of(Map.of(
                        "role", "user",
                        "MedContent", "主诉：" + textOrDefault(chiefComplaint, "")
                                + "\n整理摘要：" + textOrDefault(collectorSummary, "")
                                + "\n结构化信息：" + structuredInformation
                                + "\n患者个人 skill：\n" + textOrDefault(patientSkill, "暂无")
                                + "\nNeo4j图谱多跳依据：" + graphEvidence
                ))
        );

        return parseDiagnosisResult(content, textOrDefault(chiefComplaint, collectorSummary));
    }

    public DiagnosisOutcome diagnose(
            MultiModelGateway.ModelSpec modelSpec,
            String chiefComplaint,
            String collectorSummary,
            List<String> structuredInformation,
            List<String> graphEvidence
    ) {
        return diagnose(modelSpec, chiefComplaint, collectorSummary, structuredInformation, graphEvidence, "");
    }

    public DiagnosisOutcome diagnose(
            MultiModelGateway.ModelSpec modelSpec,
            String chiefComplaint,
            String collectorSummary,
            List<String> structuredInformation
    ) {
        return diagnose(modelSpec, chiefComplaint, collectorSummary, structuredInformation, List.of());
    }

    private DiagnosisOutcome parseDiagnosisResult(String content, String fallbackComplaint) {
        if (!StringUtils.hasText(content)) {
            return fallbackDiagnosis(fallbackComplaint);
        }

        try {
            DiagnosisResultSchema result = llmJsonValidator.parseAndValidate(content, DiagnosisResultSchema.class);
            return new DiagnosisOutcome(
                    result.conclusion().trim(),
                    result.confidence(),
                    result.riskLevel().trim(),
                    result.structuredAnalysis(),
                    result.suggestions()
            );
        } catch (Exception exception) {
            return fallbackDiagnosis(fallbackComplaint);
        }
    }

    private DiagnosisOutcome fallbackDiagnosis(String complaint) {
        MedicalWorkflowTools.RiskResult risk = tools.assessRisk(complaint);
        return new DiagnosisOutcome(
                "已基于当前病情信息生成初步诊断建议，建议结合进一步检查结果确认。",
                0.71d,
                risk.riskLevel(),
                List.of(
                        "当前症状组合已具备初步诊断参考价值。",
                        "仍需结合实验室检查或影像学结果提升诊断确定性。"
                ),
                List.of(
                        "建议完善基础检查并关注症状变化。",
                        "如出现呼吸困难、持续高热或病情急剧加重，应立即转人工或急诊处理。"
                )
        );
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    public record DiagnosisOutcome(
            String conclusion,
            double confidence,
            String riskLevel,
            List<String> structuredAnalysis,
            List<String> suggestions
    ) {
    }
}
