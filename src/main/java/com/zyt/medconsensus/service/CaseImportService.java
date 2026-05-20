package com.zyt.medconsensus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.dto.CaseImportResponse;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.PatientBasicInfoDto;
import com.zyt.medconsensus.entity.FinalDiagnosisRecord;
import com.zyt.medconsensus.entity.PatientBasicInfo;
import com.zyt.medconsensus.llm.AiWorkflowProperties;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.mapper.FinalDiagnosisRecordMapper;
import com.zyt.medconsensus.mapper.PatientBasicInfoMapper;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CaseImportService {

    private static final String SYSTEM_PROMPT = """
            你是一个医疗文档解析助手。请从以下医疗文档/图片中提取患者信息和诊断报告，返回严格的 JSON 格式。
            JSON 结构必须包含以下字段：
            {
              "patientName": "患者姓名",
              "gender": "性别（男/女）",
              "age": 年龄（整数，无法判断则为0）,
              "weight": 体重（数字，单位kg，无法判断则为0）,
              "phone": "联系电话（无法判断则为空字符串）",
              "chiefComplaint": "主诉/主要症状描述",
              "diagnosis": "诊断结论",
              "riskLevel": "风险等级（低风险/中风险/中高风险/高风险）",
              "confidence": 置信度（0.0到1.0之间的数字）,
              "treatmentAdvice": "治疗建议",
              "summary": "文档内容摘要（100字以内）"
            }
            仅返回合法 JSON，不要输出 Markdown 标记或其他文字。
            """;

    private static final String IMAGE_PROMPT = """
            你是一个医疗影像/图片解析助手。请仔细分析这张医疗相关的图片（可能是检查报告、处方单、化验单、病历照片等），
            从中提取患者信息和诊断报告，返回严格的 JSON 格式。
            JSON 结构必须包含以下字段：
            {
              "patientName": "患者姓名",
              "gender": "性别（男/女）",
              "age": 年龄（整数，无法判断则为0）,
              "weight": 体重（数字，单位kg，无法判断则为0）,
              "phone": "联系电话（无法判断则为空字符串）",
              "chiefComplaint": "主诉/主要症状描述",
              "diagnosis": "诊断结论",
              "riskLevel": "风险等级（低风险/中风险/中高风险/高风险）",
              "confidence": 置信度（0.0到1.0之间的数字）,
              "treatmentAdvice": "治疗建议",
              "summary": "图片内容摘要（100字以内）"
            }
            仅返回合法 JSON，不要输出 Markdown 标记或其他文字。
            """;

    private final MultiModelGateway modelGateway;
    private final DocumentParsingService documentParsingService;
    private final PatientBasicInfoMapper patientBasicInfoMapper;
    private final FinalDiagnosisRecordMapper finalDiagnosisRecordMapper;
    private final PatientSkillService patientSkillService;
    private final AiWorkflowProperties properties;
    private final ObjectMapper objectMapper;

    public CaseImportService(
            MultiModelGateway modelGateway,
            DocumentParsingService documentParsingService,
            PatientBasicInfoMapper patientBasicInfoMapper,
            FinalDiagnosisRecordMapper finalDiagnosisRecordMapper,
            PatientSkillService patientSkillService,
            AiWorkflowProperties properties,
            ObjectMapper objectMapper
    ) {
        this.modelGateway = modelGateway;
        this.documentParsingService = documentParsingService;
        this.patientBasicInfoMapper = patientBasicInfoMapper;
        this.finalDiagnosisRecordMapper = finalDiagnosisRecordMapper;
        this.patientSkillService = patientSkillService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CaseImportResponse importCase(Long doctorId, MultipartFile file) throws IOException {
        String contentType = file.getContentType();
        String llmResponse;

        if (isImageType(contentType)) {
            llmResponse = processImage(file);
        } else if (isPdfType(contentType)) {
            llmResponse = processPdf(file);
        } else if (isDocxType(contentType)) {
            llmResponse = processDocx(file);
        } else {
            throw new IllegalArgumentException("不支持的文件格式: " + contentType);
        }

        if (!StringUtils.hasText(llmResponse)) {
            throw new RuntimeException("AI 模型未能解析文档内容，请检查文件是否清晰可读");
        }

        JsonNode result = parseLlmResponse(llmResponse);

        PatientBasicInfo patient = createPatient(doctorId, result);
        FinalDiagnosisRecord record = createDiagnosisRecord(doctorId, result);

        patientSkillService.recordPatientProfile(patient, true);

        PatientBasicInfoDto patientDto = toPatientDto(patient);
        FinalDiagnosisRecordDto recordDto = toDiagnosisRecordDto(record);
        String summary = result.has("summary") ? result.get("summary").asText("") : "";

        return new CaseImportResponse(patientDto, recordDto, summary, "病例导入成功");
    }

    private String processImage(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String base64 = documentParsingService.encodeFileToBase64(is);
            String mimeType = file.getContentType();
            String dataUrl = "data:" + mimeType + ";base64," + base64;

            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of("type", "text", "text", IMAGE_PROMPT));

            Map<String, Object> imageUrl = new java.util.HashMap<>();
            imageUrl.put("type", "image_url");
            imageUrl.put("image_url", Map.of("url", dataUrl));
            contentParts.add(imageUrl);

            MultiModelGateway.ModelSpec spec = collectorSpec();
            return modelGateway.chatVision(spec, "", contentParts);
        }
    }

    private String processPdf(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String text = documentParsingService.extractTextFromPdf(is);
            return callLlmWithText(text);
        }
    }

    private String processDocx(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String text = documentParsingService.extractTextFromDocx(is);
            return callLlmWithText(text);
        }
    }

    private String callLlmWithText(String text) {
        String truncated = text.length() > 12000 ? text.substring(0, 12000) : text;
        MultiModelGateway.ModelSpec spec = collectorSpec();
        return modelGateway.chat(spec, SYSTEM_PROMPT, List.of(Map.of(
                "role", "user",
                "content", "以下是医疗文档内容：\n\n" + truncated
        )));
    }

    private JsonNode parseLlmResponse(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            int firstNewline = cleaned.indexOf('\n');
            int lastFence = cleaned.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                cleaned = cleaned.substring(firstNewline + 1, lastFence).trim();
            }
        }
        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            throw new RuntimeException("AI 返回内容解析失败: " + e.getMessage());
        }
    }

    private PatientBasicInfo createPatient(Long doctorId, JsonNode result) {
        PatientBasicInfo patient = new PatientBasicInfo();
        patient.setDoctorId(doctorId);
        patient.setPatientName(getTextOrDefault(result, "patientName", "未知患者"));
        patient.setGender(getTextOrDefault(result, "gender", "未知"));
        patient.setAge(getIntOrDefault(result, "age", 0));
        patient.setWeight(getBigDecimalOrDefault(result, "weight"));
        patient.setPhone(getTextOrNull(result, "phone"));
        patient.setChiefComplaint(getTextOrNull(result, "chiefComplaint"));
        return patientBasicInfoMapper.save(patient);
    }

    private FinalDiagnosisRecord createDiagnosisRecord(Long doctorId, JsonNode result) {
        FinalDiagnosisRecord record = new FinalDiagnosisRecord();
        record.setUserId(doctorId);
        record.setSessionId("import-" + UUID.randomUUID().toString().substring(0, 8));
        record.setChiefComplaint(getTextOrDefault(result, "chiefComplaint", ""));
        record.setAiConclusion(getTextOrDefault(result, "diagnosis", ""));
        record.setFinalConclusion(getTextOrDefault(result, "diagnosis", ""));
        record.setRiskLevel(getTextOrDefault(result, "riskLevel", "中风险"));
        record.setConfidence(getDoubleOrDefault(result, "confidence", 0.7));
        record.setReviewStatus("IMPORTED");
        record.setTreatmentAdvice(getTextOrDefault(result, "treatmentAdvice", ""));
        return finalDiagnosisRecordMapper.save(record);
    }

    private MultiModelGateway.ModelSpec collectorSpec() {
        return new MultiModelGateway.ModelSpec(
                StringUtils.hasText(properties.getCollector().getApiKey()) ? properties.getCollector().getApiKey() : properties.getApiKey(),
                StringUtils.hasText(properties.getCollector().getBaseUrl()) ? properties.getCollector().getBaseUrl() : properties.getBaseUrl(),
                properties.getCollector().getModel(),
                properties.getCollector().getTemperature()
        );
    }

    private boolean isImageType(String contentType) {
        return contentType != null && (contentType.startsWith("image/"));
    }

    private boolean isPdfType(String contentType) {
        return "application/pdf".equals(contentType);
    }

    private boolean isDocxType(String contentType) {
        return "application/vnd.openxmlformats-officedocument.wordprocessingml.document".equals(contentType);
    }

    private String getTextOrDefault(JsonNode node, String field, String defaultValue) {
        JsonNode child = node.get(field);
        return (child != null && child.isTextual() && StringUtils.hasText(child.asText())) ? child.asText() : defaultValue;
    }

    private String getTextOrNull(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && child.isTextual() && StringUtils.hasText(child.asText())) {
            String val = child.asText().trim();
            return val.isEmpty() ? null : val;
        }
        return null;
    }

    private int getIntOrDefault(JsonNode node, String field, int defaultValue) {
        JsonNode child = node.get(field);
        if (child != null && child.isNumber()) {
            return child.asInt(defaultValue);
        }
        return defaultValue;
    }

    private BigDecimal getBigDecimalOrDefault(JsonNode node, String field) {
        JsonNode child = node.get(field);
        if (child != null && child.isNumber()) {
            double val = child.asDouble(0);
            return val > 0 ? BigDecimal.valueOf(val) : null;
        }
        return null;
    }

    private double getDoubleOrDefault(JsonNode node, String field, double defaultValue) {
        JsonNode child = node.get(field);
        if (child != null && child.isNumber()) {
            return child.asDouble(defaultValue);
        }
        return defaultValue;
    }

    private PatientBasicInfoDto toPatientDto(PatientBasicInfo patient) {
        return new PatientBasicInfoDto(
                patient.getId(),
                patient.getPatientName(),
                patient.getGender(),
                patient.getAge(),
                patient.getWeight() == null ? "" : patient.getWeight().toPlainString(),
                patient.getPhone(),
                patient.getChiefComplaint(),
                patient.getCreateTime() == null ? null : patient.getCreateTime().toString(),
                patient.getUpdateTime() == null ? null : patient.getUpdateTime().toString()
        );
    }

    private FinalDiagnosisRecordDto toDiagnosisRecordDto(FinalDiagnosisRecord record) {
        return new FinalDiagnosisRecordDto(
                record.getId(),
                record.getSessionId(),
                record.getChiefComplaint(),
                record.getAiConclusion(),
                record.getDoctorOpinion(),
                record.getFinalConclusion(),
                record.getRiskLevel(),
                record.getConfidence(),
                record.getReviewStatus(),
                null,
                record.getTreatmentSource(),
                record.getTreatmentAdvice(),
                record.getUpdatedAt() == null ? null : record.getUpdatedAt().toString()
        );
    }
}
