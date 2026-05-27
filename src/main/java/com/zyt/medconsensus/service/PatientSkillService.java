package com.zyt.medconsensus.service;

import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.entity.PatientBasicInfo;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class PatientSkillService {

    private static final DateTimeFormatter EVENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_SKILL_CHARS = 12000;

    private final Path skillDirectory;
    private final ReentrantLock lock = new ReentrantLock();

    public PatientSkillService(@Value("${app.patient-skill.directory:partiality}") String skillDirectory) {
        this.skillDirectory = Path.of(skillDirectory).toAbsolutePath().normalize();
    }

    public String loadSkill(ConsultationRequest request) {
        String patientName = request == null ? null : request.getPatientName();
        if (!StringUtils.hasText(patientName)) {
            return "";
        }

        Path path = skillPath(patientName);
        if (!Files.exists(path)) {
            return "";
        }

        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (content.length() <= MAX_SKILL_CHARS) {
                return content;
            }
            return content.substring(content.length() - MAX_SKILL_CHARS);
        } catch (IOException exception) {
            return "";
        }
    }

    public String buildCurrentContext(ConsultationRequest request, String existingSkill) {
        List<String> context = new ArrayList<>();
        if (StringUtils.hasText(existingSkill)) {
            context.add("【既往患者个人 skill】\n" + existingSkill.trim());
        }

        List<String> uploaded = uploadedPatientFacts(request);
        if (!uploaded.isEmpty()) {
            context.add("【本次上传患者信息】\n" + String.join("\n", uploaded));
        }

        String categorySnapshot = categorySnapshot(request);
        if (StringUtils.hasText(categorySnapshot)) {
            context.add("【本次信息涉及的长期画像维度】\n" + categorySnapshot);
        }

        return String.join("\n\n", context);
    }

    public void recordConsultation(
            Long userId,
            String sessionId,
            ConsultationRequest request,
            String collectorSummary,
            List<String> structuredAnalysis,
            DiagnosticResponse diagnosis
    ) {
        if (request == null || !StringUtils.hasText(request.getPatientName())) {
            return;
        }

        lock.lock();
        try {
            Files.createDirectories(skillDirectory);
            Path path = skillPath(request.getPatientName());
            if (!Files.exists(path)) {
                Files.writeString(path, initialSkillDocument(request), StandardCharsets.UTF_8);
            }
            Files.writeString(path, consultationEntry(userId, sessionId, request, collectorSummary, structuredAnalysis, diagnosis),
                    StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("患者个人 skill 写入失败", exception);
        } finally {
            lock.unlock();
        }
    }

    public Path skillPath(String patientName) {
        return skillDirectory.resolve(toEnglishFileName(patientName) + ".md").normalize();
    }

    public void recordPatientProfile(PatientBasicInfo patient, boolean created) {
        if (patient == null || !StringUtils.hasText(patient.getPatientName())) {
            return;
        }

        lock.lock();
        try {
            Files.createDirectories(skillDirectory);
            Path path = skillPath(patient.getPatientName());
            if (!Files.exists(path)) {
                Files.writeString(path, initialSkillDocument(patient), StandardCharsets.UTF_8);
            }
            Files.writeString(path, profileEntry(patient, created), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("患者个人 skill 写入失败", exception);
        } finally {
            lock.unlock();
        }
    }

    private String initialSkillDocument(ConsultationRequest request) {
        String name = textOrDefault(request.getPatientName(), "Unknown Patient");
        return initialSkillDocument(
                name,
                request.getPatientAge(),
                request.getPatientGender(),
                request.getPatientWeight(),
                request.getChiefComplaint()
        );
    }

    private String initialSkillDocument(PatientBasicInfo patient) {
        return initialSkillDocument(
                patient.getPatientName(),
                patient.getAge() == null ? "" : String.valueOf(patient.getAge()),
                patient.getGender(),
                patient.getWeight() == null ? "" : patient.getWeight().toPlainString(),
                patient.getChiefComplaint()
        );
    }

    private String initialSkillDocument(String name, String age, String gender, String weight, String chiefComplaint) {
        String patientName = textOrDefault(name, "Unknown Patient");
        return "# Patient Skill: " + patientName + "\n\n"
                + "> This markdown file is maintained after each consultation and injected into Collector/Diagnosis agents as patient-specific skill memory.\n\n"
                + "## Stable Profile\n\n"
                + "### Basic Information\n"
                + profileLine("Age", age)
                + profileLine("Gender", gender)
                + profileLine("Weight", weight)
                + emptyProfileLine("Occupation")
                + "\n### Medical History\n"
                + emptyProfileLine("Hypertension / Hyperlipidemia / Diabetes")
                + emptyProfileLine("Heart disease")
                + emptyProfileLine("Surgery history")
                + "\n### Medication History\n"
                + emptyProfileLine("Current medications")
                + emptyProfileLine("Drug allergies")
                + "\n### Family History\n"
                + emptyProfileLine("Family diseases")
                + "\n### Lifestyle\n"
                + emptyProfileLine("Smoking")
                + emptyProfileLine("Alcohol")
                + emptyProfileLine("Exercise")
                + emptyProfileLine("Sleep")
                + "\n### Initial Chief Complaint\n"
                + profileLine("Chief complaint", chiefComplaint)
                + "\n## Consultation Timeline\n";
    }

    private String profileEntry(PatientBasicInfo patient, boolean created) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n### ")
                .append(LocalDateTime.now().format(EVENT_TIME_FORMATTER))
                .append(created ? " - Patient profile created" : " - Patient profile updated")
                .append("\n\n");
        builder.append("- Patient ID: ").append(patient.getId() == null ? "unknown" : patient.getId()).append("\n");
        builder.append("- Doctor/User ID: ").append(patient.getDoctorId() == null ? "unknown" : patient.getDoctorId()).append("\n");
        addLine(builder, "Name", patient.getPatientName());
        addLine(builder, "Gender", patient.getGender());
        if (patient.getAge() != null) {
            builder.append("- Age: ").append(patient.getAge()).append("\n");
        }
        if (patient.getWeight() != null) {
            builder.append("- Weight: ").append(patient.getWeight().toPlainString()).append("\n");
        }
        addLine(builder, "Phone", patient.getPhone());
        addLine(builder, "Chief complaint", patient.getChiefComplaint());
        return builder.toString();
    }

    private String consultationEntry(
            Long userId,
            String sessionId,
            ConsultationRequest request,
            String collectorSummary,
            List<String> structuredAnalysis,
            DiagnosticResponse diagnosis
    ) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n### ")
                .append(LocalDateTime.now().format(EVENT_TIME_FORMATTER))
                .append(" - ")
                .append(textOrDefault(sessionId, "unknown-session"))
                .append("\n\n");
        builder.append("- Doctor/User ID: ").append(userId == null ? "unknown" : userId).append("\n");
        uploadedPatientFacts(request).forEach(item -> builder.append("- ").append(item).append("\n"));
        addLine(builder, "Chief complaint", request.getChiefComplaint());
        addLine(builder, "Patient input", request.getMessage());
        addLine(builder, "Uploaded evidence file", request.getMedicalEvidenceFileName());
        addLine(builder, "Structured medical evidence", request.getMedicalEvidence());
        addLine(builder, "Collector summary", collectorSummary);

        String categorySnapshot = categorySnapshot(request);
        if (StringUtils.hasText(categorySnapshot)) {
            builder.append("- Longitudinal profile cues:\n");
            categorySnapshot.lines().forEach(line -> builder.append("  - ").append(line).append("\n"));
        }

        if (structuredAnalysis != null && !structuredAnalysis.isEmpty()) {
            builder.append("- Structured analysis:\n");
            structuredAnalysis.forEach(item -> builder.append("  - ").append(sanitizeMarkdownLine(item)).append("\n"));
        }

        if (diagnosis != null) {
            addLine(builder, "Diagnosis conclusion", diagnosis.conclusion());
            builder.append("- Risk level: ").append(textOrDefault(diagnosis.riskLevel(), "unknown")).append("\n");
            builder.append("- Confidence: ").append(String.format(Locale.ROOT, "%.2f", diagnosis.confidence())).append("\n");
            if (diagnosis.suggestions() != null && !diagnosis.suggestions().isEmpty()) {
                builder.append("- Suggestions:\n");
                diagnosis.suggestions().forEach(item -> builder.append("  - ").append(sanitizeMarkdownLine(item)).append("\n"));
            }
        }

        return builder.toString();
    }

    private List<String> uploadedPatientFacts(ConsultationRequest request) {
        if (request == null) {
            return List.of();
        }
        List<String> facts = new ArrayList<>();
        addFact(facts, "Name", request.getPatientName());
        addFact(facts, "Gender", request.getPatientGender());
        addFact(facts, "Age", request.getPatientAge());
        addFact(facts, "Weight", request.getPatientWeight());
        addFact(facts, "Phone", request.getPatientPhone());
        addFact(facts, "Uploaded chief complaint", request.getChiefComplaint());
        return facts;
    }

    private String categorySnapshot(ConsultationRequest request) {
        if (request == null) {
            return "";
        }

        String text = String.join(" ",
                textOrDefault(request.getMessage(), ""),
                textOrDefault(request.getChiefComplaint(), ""),
                textOrDefault(request.getPatientGender(), ""),
                textOrDefault(request.getPatientAge(), ""),
                textOrDefault(request.getPatientWeight(), "")
        );

        List<String> categories = new ArrayList<>();
        if (StringUtils.hasText(request.getPatientAge())
                || StringUtils.hasText(request.getPatientGender())
                || StringUtils.hasText(request.getPatientWeight())
                || containsAny(text, "年龄", "岁", "性别", "男", "女", "体重", "kg", "公斤", "职业", "工作", "工厂", "粉尘", "化工")) {
            categories.add("Basic information: age / gender / weight / occupation cues should be retained.");
        }
        if (containsAny(text, "三高", "高血压", "高血脂", "糖尿病", "心脏病", "冠心病", "手术", "既往史", "病史", "哮喘")) {
            categories.add("Medical history: chronic disease, heart disease, surgery or relevant past history cues detected.");
        }
        if (containsAny(text, "正在服用", "服用", "用药", "药物", "过敏", "青霉素", "头孢", "药物过敏")) {
            categories.add("Medication history: current medication or drug allergy cues detected.");
        }
        if (containsAny(text, "家族史", "父亲", "母亲", "兄弟", "姐妹", "遗传", "家里人", "亲属")) {
            categories.add("Family history: family disease or hereditary risk cues detected.");
        }
        if (containsAny(text, "吸烟", "抽烟", "饮酒", "喝酒", "运动", "锻炼", "睡眠", "熬夜", "失眠", "不爱运动", "吃完饭就躺")) {
            categories.add("Lifestyle: smoking, alcohol, exercise or sleep habit cues detected.");
        }

        return String.join("\n", categories);
    }

    private String toEnglishFileName(String patientName) {
        String normalized = Normalizer.normalize(textOrDefault(patientName, "patient"), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
        if (StringUtils.hasText(normalized)) {
            return normalized;
        }
        return "patient-" + Integer.toHexString(patientName.hashCode());
    }

    private void addFact(List<String> facts, String label, String value) {
        if (StringUtils.hasText(value)) {
            facts.add(label + ": " + sanitizeMarkdownLine(value));
        }
    }

    private void addLine(StringBuilder builder, String label, String value) {
        if (StringUtils.hasText(value)) {
            builder.append("- ").append(label).append(": ").append(sanitizeMarkdownLine(value)).append("\n");
        }
    }

    private String emptyProfileLine(String label) {
        return "- " + label + ": _unknown_\n";
    }

    private String profileLine(String label, String value) {
        return "- " + label + ": "
                + (StringUtils.hasText(value) ? sanitizeMarkdownLine(value) : "_unknown_")
                + "\n";
    }

    private String sanitizeMarkdownLine(String value) {
        return textOrDefault(value, "").replace("\r", " ").replace("\n", " ").trim();
    }

    private String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private boolean containsAny(String text, String... keywords) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
