package com.zyt.medconsensus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.EvidenceSource;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceSourceService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private static final String METADATA = "SELECT id,file_name,recognition_scope,citations_json,original_text,reviewed_text FROM medical_evidence_source e ";

    public EvidenceSourceService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public EvidenceSource save(Long doctorId, Long patientId, String sessionId, Long expectedChart, String expectedSession, MultipartFile file,
                               JsonNode extracted, String originalText) throws IOException {
        String id = UUID.randomUUID().toString();
        String fileName = StringUtils.getFilename(file.getOriginalFilename().replace('\\', '/'));
        String mediaType = switch (StringUtils.getFilenameExtension(fileName).toLowerCase(Locale.ROOT)) {
            case "pdf" -> "application/pdf";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            default -> throw new IllegalArgumentException("不支持的资料格式");
        };
        byte[] bytes = file.getBytes();
        EvidenceLocations locations = EvidenceLocations.locate(mediaType, bytes, extracted);
        jdbc.update("""
                INSERT INTO medical_evidence_source
                (id,doctor_id,patient_account_id,session_id,file_name,media_type,file_data,recognition_scope,citations_json,original_text,expected_chart_id,expected_session_id,used_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,CASE WHEN ? THEN clock_timestamp() ELSE NULL END)
                """, id, doctorId, patientId, sessionId, fileName, mediaType, bytes, locations.scope(),
                json.writeValueAsString(locations.citations()), originalText, expectedChart, expectedSession, patientId == null && sessionId != null);
        return new EvidenceSource(id, fileName, locations.scope(), locations.citations(), originalText, null);
    }

    public void prepareDoctorUpload(Long doctorId, ConsultationRequest request) {
        if (!request.isMedicalEvidenceConfirmed() || !StringUtils.hasText(request.getMedicalEvidence())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先确认资料内容");
        }
        String name = jdbc.query("""
                SELECT file_name FROM medical_evidence_source
                WHERE id=? AND doctor_id=? AND patient_account_id IS NULL AND session_id IS NULL
                  AND (expected_chart_id IS NULL OR expected_chart_id=?)
                  AND (expected_session_id IS NULL OR expected_session_id=?)
                """, (row, index) -> row.getString(1), request.getMedicalEvidenceId(), doctorId, request.getPatientRecordId(), request.getSessionId())
                .stream().findFirst().orElseThrow(this::missing);
        request.setMedicalEvidenceFileName(name);
    }

    public void correctPatientUpload(Long doctorId, String id, String text) {
        int changed = jdbc.update("""
                WITH updated AS (
                    UPDATE patient_consultation c SET evidence_text=?,version=version+1,updated_at=CURRENT_TIMESTAMP
                    WHERE evidence_source_id=? AND doctor_id=? AND evidence_status='PENDING_DOCTOR'
                      AND EXISTS (SELECT 1 FROM medical_evidence_source e WHERE e.id=c.evidence_source_id AND e.used_at IS NULL)
                    RETURNING evidence_source_id
                )
                UPDATE medical_evidence_source SET reviewed_text=?
                WHERE id IN (SELECT evidence_source_id FROM updated) AND doctor_id=?
                """, text, id, doctorId, text, doctorId);
        if (changed == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "该资料不存在或已进入复核流程");
    }

    public List<EvidenceSource> list(long userId, boolean patient, String sessionId) {
        return jdbc.query(METADATA + "WHERE " + owner(patient) + " AND session_id=? ORDER BY created_at,id",
                this::mapSource, userId, sessionId);
    }

    public List<EvidenceSource> forRevision(long userId, boolean patient, long reportId, int revision) {
        String account = patient ? "r.patient_account_id" : "r.doctor_id";
        return jdbc.query(METADATA + """
                JOIN publication_evidence link ON link.source_id=e.id
                JOIN report_publication r ON r.record_id=link.record_id AND r.revision=link.revision
                WHERE r.record_id=? AND r.revision=? AND %s=? ORDER BY e.created_at,e.id
                """.formatted(account),
                this::mapSource, reportId, revision, userId);
    }

    public EvidenceSource find(long userId, boolean patient, String id) {
        return jdbc.query(METADATA + "WHERE " + owner(patient) + " AND id=?", this::mapSource, userId, id)
                .stream().findFirst().orElseThrow(this::missing);
    }

    public FileData original(long userId, boolean patient, String id) {
        return jdbc.query("SELECT file_name,media_type,file_data FROM medical_evidence_source e WHERE "
                        + owner(patient) + " AND id=?",
                (row, index) -> new FileData(row.getString(1), row.getString(2), row.getBytes(3)), userId, id)
                .stream().findFirst().orElseThrow(this::missing);
    }

    private EvidenceSource mapSource(ResultSet row, int index) throws SQLException {
        try {
            return new EvidenceSource(row.getString("id"), row.getString("file_name"), row.getString("recognition_scope"),
                    json.readValue(row.getString("citations_json"), new TypeReference<>() {}),
                    row.getString("original_text"), row.getString("reviewed_text"));
        } catch (IOException exception) {
            throw new SQLException("资料引用读取失败", exception);
        }
    }

    private String owner(boolean patient) {
        if (!patient) return "e.doctor_id=?";
        return """
                ? IN (
                    SELECT e.patient_account_id
                    UNION ALL SELECT r.patient_account_id FROM publication_evidence link
                    JOIN report_publication r ON r.record_id=link.record_id AND r.revision=link.revision
                    WHERE e.patient_account_id IS NULL AND link.source_id=e.id
                )
                """;
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到该检查资料");
    }

    public record FileData(String name, String mediaType, byte[] bytes) {}
}
