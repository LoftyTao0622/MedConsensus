package com.zyt.medconsensus.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.dto.CaseImportResponse;
import com.zyt.medconsensus.dto.FinalDiagnosisRecordDto;
import com.zyt.medconsensus.dto.MedicalEvidenceAnalysisResponse;
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
            你是一个医疗检查资料结构化识别助手。请从以下医疗文档内容中提取患者信息、检查发现和诊断线索，返回严格的 JSON 格式。
            JSON 结构必须包含以下字段：
            {
              "patientName": "患者姓名",
              "gender": "性别（男/女）",
              "age": 年龄（整数，无法判断则为0）,
              "weight": 体重（数字，单位kg，无法判断则为0）,
              "phone": "联系电话（无法判断则为空字符串）",
              "chiefComplaint": "主诉/主要症状描述",
              "modality": "资料类型，如CT/化验单/处方/病历/PDF报告/未知",
              "examType": "检查项目",
              "bodyPart": "检查部位",
              "imagingFindings": ["影像学所见，没有则为空数组"],
              "labFindings": ["实验室/检验结果，没有则为空数组"],
              "keyMeasurements": ["关键数值或测量结果，没有则为空数组"],
              "diagnosis": "资料中明确写出的诊断或模型辅助提炼的疑似诊断线索",
              "impression": "检查印象/结论",
              "riskLevel": "风险等级（低风险/中风险/中高风险/高风险）",
              "redFlags": ["需要医生优先复核的危险信号，没有则为空数组"],
              "confidence": 置信度（0.0到1.0之间的数字）,
              "treatmentAdvice": "治疗建议",
              "summary": "资料内容摘要（100字以内）",
              "doctorReviewRequired": true
            }
            不要把影像识别结果作为最终诊断；无法判断的字段使用空字符串或空数组。
            仅返回合法 JSON，不要输出 Markdown 标记或其他文字。
            """;

    private static final String VISION_PROMPT = """
            你是一个医疗图像和检查报告结构化识别助手。请仔细分析上传资料，可能是 CT 截图、医学影像截图、
            检查报告、处方单、化验单、病历照片或扫描 PDF 页面。返回严格的 JSON 格式。
            JSON 结构必须包含以下字段：
            {
              "patientName": "患者姓名",
              "gender": "性别（男/女）",
              "age": 年龄（整数，无法判断则为0）,
              "weight": 体重（数字，单位kg，无法判断则为0）,
              "phone": "联系电话（无法判断则为空字符串）",
              "chiefComplaint": "主诉/主要症状描述",
              "modality": "资料类型，如CT/化验单/处方/病历/PDF报告/未知",
              "examType": "检查项目",
              "bodyPart": "检查部位",
              "imagingFindings": ["影像学所见，没有则为空数组"],
              "labFindings": ["实验室/检验结果，没有则为空数组"],
              "keyMeasurements": ["关键数值或测量结果，没有则为空数组"],
              "diagnosis": "资料中明确写出的诊断或模型辅助提炼的疑似诊断线索",
              "impression": "检查印象/结论",
              "riskLevel": "风险等级（低风险/中风险/中高风险/高风险）",
              "redFlags": ["需要医生优先复核的危险信号，没有则为空数组"],
              "confidence": 置信度（0.0到1.0之间的数字）,
              "treatmentAdvice": "治疗建议",
              "summary": "资料内容摘要（100字以内）",
              "doctorReviewRequired": true
            }
            不要把影像识别结果作为最终诊断；无法判断的字段使用空字符串或空数组。
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
        JsonNode result = analyzeFile(file);

        PatientBasicInfo patient = createPatient(doctorId, result);
        FinalDiagnosisRecord record = createDiagnosisRecord(doctorId, result);

        patientSkillService.recordPatientProfile(patient, true);

        PatientBasicInfoDto patientDto = toPatientDto(patient);
        FinalDiagnosisRecordDto recordDto = toDiagnosisRecordDto(record);
        String summary = result.has("summary") ? result.get("summary").asText("") : "";

        return new CaseImportResponse(patientDto, recordDto, summary, "病例导入成功");
    }

    public MedicalEvidenceAnalysisResponse analyzeMedicalEvidence(MultipartFile file) throws IOException {
        JsonNode result = analyzeFile(file);
        String summary = result.has("summary") ? result.get("summary").asText("") : "";
        return new MedicalEvidenceAnalysisResponse(
                file.getOriginalFilename(),
                result,
                formatEvidenceForAgent(result),
                summary,
                "PENDING_REVIEW",
                "检查资料识别成功，请医生确认后再提交给诊断 Agent"
        );
    }

    private JsonNode analyzeFile(MultipartFile file) throws IOException {
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
            throw new RuntimeException("GPT-5.4视觉模型未能解析检查资料，请确认模型配置、文件清晰度和文件格式后重试");
        }

        return parseLlmResponse(llmResponse);
    }

    private String processImage(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String base64 = documentParsingService.encodeFileToBase64(is);
            String mimeType = StringUtils.hasText(file.getContentType()) ? file.getContentType() : "image/jpeg";
            String dataUrl = "data:" + mimeType + ";base64," + base64;
            return callVisionWithImages("", List.of(dataUrl));
        }
    }

    private String processPdf(MultipartFile file) throws IOException {
        String text;
        try (InputStream is = file.getInputStream()) {
            text = documentParsingService.extractTextFromPdf(is);
        }

        List<String> pageImages;
        try (InputStream is = file.getInputStream()) {
            pageImages = documentParsingService.renderPdfPagesToPngDataUrls(is, 4);
        }

        if (!pageImages.isEmpty()) {
            return callVisionWithImages(text, pageImages);
        }
        return callLlmWithText(text);
    }

    private String processDocx(MultipartFile file) throws IOException {
        try (InputStream is = file.getInputStream()) {
            String text = documentParsingService.extractTextFromDocx(is);
            return callLlmWithText(text);
        }
    }

    private String callLlmWithText(String text) {
        String truncated = text.length() > 12000 ? text.substring(0, 12000) : text;
        return modelGateway.chat(visionSpec(), SYSTEM_PROMPT, List.of(Map.of(
                "role", "user",
                "content", "以下是医疗文档内容：\n\n" + truncated
        )));
    }

    private String callVisionWithImages(String text, List<String> imageDataUrls) {
        List<Map<String, Object>> contentParts = new ArrayList<>();
        contentParts.add(Map.of("type", "text", "text", VISION_PROMPT));
        if (StringUtils.hasText(text)) {
            String truncated = text.length() > 6000 ? text.substring(0, 6000) : text;
            contentParts.add(Map.of("type", "text", "text", "PDF可提取文本：\n" + truncated));
        }

        for (String imageDataUrl : imageDataUrls) {
            Map<String, Object> imageUrl = new java.util.HashMap<>();
            imageUrl.put("type", "image_url");
            imageUrl.put("image_url", Map.of("url", imageDataUrl));
            contentParts.add(imageUrl);
        }

        return modelGateway.chatVision(visionSpec(), "", contentParts);
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

    private MultiModelGateway.ModelSpec visionSpec() {
        AiWorkflowProperties.Vision vision = properties.getVision();
        return new MultiModelGateway.ModelSpec(
                StringUtils.hasText(vision.getApiKey()) ? vision.getApiKey() : properties.getApiKey(),
                StringUtils.hasText(vision.getBaseUrl()) ? vision.getBaseUrl() : properties.getBaseUrl(),
                vision.getModel(),
                vision.getTemperature()
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
        String diagnosis = diagnosisText(result);
        record.setAiConclusion(diagnosis);
        record.setFinalConclusion(diagnosis);
        record.setRiskLevel(getTextOrDefault(result, "riskLevel", "中风险"));
        record.setConfidence(getDoubleOrDefault(result, "confidence", 0.7));
        record.setReviewStatus("IMPORTED");
        record.setTreatmentAdvice(getTextOrDefault(result, "treatmentAdvice", ""));
        return finalDiagnosisRecordMapper.save(record);
    }

    private String diagnosisText(JsonNode result) {
        String diagnosis = getTextOrDefault(result, "diagnosis", "");
        if (StringUtils.hasText(diagnosis)) {
            return diagnosis;
        }
        return getTextOrDefault(result, "impression", "");
    }

    private String formatEvidenceForAgent(JsonNode result) {
        try {
            String json = objectMapper.writeValueAsString(result);
            String evidence = "医学检查资料结构化识别 JSON：" + json;
            return evidence.length() > 6000 ? evidence.substring(0, 6000) : evidence;
        } catch (Exception exception) {
            String summary = result.has("summary") ? result.get("summary").asText("") : result.toString();
            return "医学检查资料结构化识别摘要：" + summary;
        }
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
