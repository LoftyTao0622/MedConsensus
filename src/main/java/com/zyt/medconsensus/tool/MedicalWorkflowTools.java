package com.zyt.medconsensus.tool;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class MedicalWorkflowTools {

    public static final List<String> PATIENT_INFORMATION_GUIDE = List.of(
            "建议按“主诉 + 持续时间 + 伴随症状 + 检查结果 + 既往史/用药史”的顺序描述病情。",
            "主诉示例：8岁男孩，近1个月体重明显增加，食量大，不爱运动，吃完饭常躺着。",
            "伴随症状可补充：是否发热、咳嗽、疼痛、胸闷、呼吸困难、腹痛、头晕、乏力等。",
            "检查与病史可补充：血常规、影像学、既往疾病、过敏史、当前用药、家族史和近期就诊情况。"
    );

    public SufficiencyResult assessInformationSufficiency(String input, List<String> memory) {
        String text = (input == null ? "" : input) + " " + String.join(" ", memory);
        int score = 0;
        if (containsAny(text, "天", "周", "月", "小时")) {
            score++;
        }
        if (containsAny(text, "发热", "咳嗽", "疼", "胸闷", "呼吸", "头晕", "腹痛",
                "肥胖", "超重", "体重", "食量大", "能吃", "不爱运动", "懒", "吃完饭就躺")) {
            score++;
        }
        if (containsAny(text, "血常规", "胸片", "ct", "核磁", "检查", "化验")) {
            score++;
        }
        if (containsAny(text, "既往", "病史", "高血压", "糖尿病", "哮喘", "过敏")) {
            score++;
        }
        boolean graphReady = containsAny(text, "肥胖", "超重", "体重明显增加", "食量大", "不爱运动", "吃完饭就躺");
        boolean sufficient = (score >= 2 && text.length() >= 20) || (graphReady && text.length() >= 15);
        return new SufficiencyResult(
                sufficient,
                sufficient ? 0.78 : 0.42,
                sufficient
                        ? List.of(
                                "当前信息已包含可进入 AI 初步诊断的核心线索，但医生最终判断仍建议结合检查与病史。",
                                "如需提高诊断可靠性，可继续补充持续时间、检查结果、既往史、过敏史和当前用药。"
                        )
                        : List.of(
                                "缺少症状持续时间，例如几小时、几天、几周或几个月。",
                                "缺少伴随症状，例如是否发热、咳嗽、疼痛、胸闷、呼吸困难、腹痛等。",
                                "缺少检查或化验结果，例如血常规、胸片、CT、B超、血糖、血脂等。",
                                "缺少既往史、过敏史、当前用药或近期就诊情况。"
                        )
        );
    }

    public RiskResult assessRisk(String text) {
        String content = text == null ? "" : text;
        boolean highRisk = containsAny(content, "呼吸困难", "意识模糊", "胸痛加重", "血氧", "晕厥", "持续高热");
        boolean mediumRisk = highRisk || containsAny(content, "胸闷", "反复发热", "哮喘", "肺炎");
        double score = highRisk ? 0.92 : (mediumRisk ? 0.68 : 0.36);
        String level = highRisk ? "高风险" : (mediumRisk ? "中高风险" : "中低风险");
        return new RiskResult(level, score, highRisk);
    }

    public VotingResult vote(List<Map<String, Object>> reviewers, double primaryConfidence, RiskResult risk) {
        double weighted = reviewers.stream()
                .mapToDouble(item -> asDouble(item.get("score")) * asDouble(item.get("weight")))
                .sum();
        double finalConfidence = (weighted * 0.65) + (primaryConfidence * 0.35);
        String route;
        if (risk.highRisk() || finalConfidence < 0.75) {
            route = "human_review";
        } else {
            route = "finalize";
        }
        return new VotingResult(finalConfidence, route);
    }

    public List<String> extractTreatmentKeywords(String text) {
        String content = text == null ? "" : text.trim();
        Map<String, List<String>> aliases = new LinkedHashMap<>();
        aliases.put("发热", List.of("发热", "发烧", "高热", "低热", "体温升高"));
        aliases.put("肺炎", List.of("肺炎", "社区获得性肺炎", "支气管肺炎"));
        aliases.put("咳嗽", List.of("咳嗽", "咳痰", "黄痰", "干咳"));
        aliases.put("哮喘", List.of("哮喘", "喘息", "气道高反应"));
        aliases.put("上呼吸道感染", List.of("上呼吸道感染", "感冒", "咽痛", "鼻塞", "流涕"));
        aliases.put("支气管炎", List.of("支气管炎", "急性支气管炎"));
        aliases.put("糖尿病", List.of("糖尿病", "血糖升高", "高血糖"));
        aliases.put("高血压", List.of("高血压", "血压升高"));
        aliases.put("肥胖", List.of("肥胖", "超重", "体重明显增加"));

        List<String> keywords = new ArrayList<>();
        aliases.forEach((keyword, words) -> {
            if (words.stream().anyMatch(content::contains)) {
                keywords.add(keyword);
            }
        });

        if (keywords.isEmpty() && content.length() > 0) {
            keywords.add(content.length() <= 24 ? content : content.substring(0, 24));
        }
        return keywords.stream().distinct().limit(8).toList();
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException exception) {
                return 0d;
            }
        }
        return 0d;
    }

    private boolean containsAny(String text, String... keywords) {
        String lower = text == null ? "" : text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    public record SufficiencyResult(
            boolean sufficient,
            double score,
            List<String> missingItems
    ) {
    }

    public record RiskResult(
            String riskLevel,
            double riskScore,
            boolean highRisk
    ) {
    }

    public record VotingResult(
            double confidence,
            String route
    ) {
    }
}
