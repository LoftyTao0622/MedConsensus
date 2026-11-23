package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.MessageHistoryDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConsultationSessionStore {
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final TypeReference<List<MessageHistoryDto>> MESSAGES = new TypeReference<>() {};
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ConsultationSessionStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public record Session(String id, Long patientRecordId, String title, String status, String chiefComplaint,
                          String evidenceFileName, String updatedAt, List<MessageHistoryDto> history,
                          DiagnosticResponse diagnosis, boolean archived) {}

    private static String displayTime(OffsetDateTime time) {
        return time.atZoneSameInstant(java.time.ZoneId.systemDefault()).format(DISPLAY_TIME);
    }

    public List<ChatSessionDto> list(long doctorId) {
        return jdbc.query("""
                SELECT session_id,title,status,evidence_file_name,updated_at FROM consultation_session
                WHERE doctor_id=? AND NOT archived ORDER BY updated_at DESC, session_id
                """, (rs,i) -> new ChatSessionDto(rs.getString("session_id"), rs.getString("title"), rs.getString("status"),
                displayTime(rs.getObject("updated_at", OffsetDateTime.class)), rs.getString("evidence_file_name")), doctorId);
    }

    public Optional<Session> get(long doctorId, String sessionId) {
        return jdbc.query("SELECT * FROM consultation_session WHERE doctor_id=? AND session_id=?", (rs,i) -> {
            try {
                String diagnosis = rs.getString("diagnosis");
                return new Session(sessionId, rs.getObject("patient_record_id", Long.class), rs.getString("title"),
                        rs.getString("status"), rs.getString("chief_complaint"), rs.getString("evidence_file_name"),
                        displayTime(rs.getObject("updated_at", OffsetDateTime.class)),
                        json.readValue(rs.getString("messages"), MESSAGES),
                        diagnosis == null ? null : json.readValue(diagnosis, DiagnosticResponse.class), rs.getBoolean("archived"));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("问诊记录读取失败", e);
            }
        }, doctorId,sessionId).stream().findFirst();
    }

    public DiagnosticResponse latestDiagnosis(long doctorId) {
        return jdbc.query("""
                SELECT diagnosis FROM consultation_session WHERE doctor_id=? AND NOT archived AND diagnosis IS NOT NULL
                ORDER BY updated_at DESC,session_id LIMIT 1
                """, (rs,i) -> {
            try {
                return json.readValue(rs.getString(1), DiagnosticResponse.class);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("诊断记录读取失败", e);
            }
        }, doctorId).stream().findFirst().orElse(null);
    }

    @org.springframework.transaction.annotation.Transactional
    public void appendTurn(long doctorId, Long patientRecordId, ChatSessionDto session, String chiefComplaint,
                           List<MessageHistoryDto> turn, DiagnosticResponse diagnosis,
                           String evidenceId, String reviewedText) {
        if (evidenceId != null) {
            int attached = jdbc.update("""
                    UPDATE medical_evidence_source e SET session_id=?, reviewed_text=?, used_at=clock_timestamp()
                    WHERE id=? AND doctor_id=? AND used_at IS NULL
                      AND (expected_chart_id IS NULL OR expected_chart_id=?)
                      AND (expected_session_id IS NULL OR expected_session_id=?)
                      AND ((patient_account_id IS NULL AND session_id IS NULL)
                           OR (session_id=? AND EXISTS (
                               SELECT 1 FROM patient_consultation c WHERE c.evidence_source_id=e.id
                                 AND c.doctor_id=e.doctor_id AND c.patient_account_id=e.patient_account_id AND c.evidence_status='PROCESSING')))
                    """, session.id(), reviewedText, evidenceId, doctorId, patientRecordId, session.id(), session.id());
            if (attached == 0) throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, "资料已在其他操作中提交");
        }
        appendTurn(doctorId, patientRecordId, session, chiefComplaint, turn, diagnosis);
    }

    public void appendTurn(long doctorId, Long patientRecordId, ChatSessionDto session, String chiefComplaint,
                           List<MessageHistoryDto> turn, DiagnosticResponse diagnosis) {
        try {
            jdbc.update("""
                    INSERT INTO consultation_session(doctor_id,session_id,patient_record_id,title,status,chief_complaint,
                                                     evidence_file_name,messages,diagnosis)
                    VALUES(?,?,?,?,?,?,?,CAST(? AS jsonb),CAST(? AS jsonb))
                    ON CONFLICT(doctor_id,session_id) DO UPDATE SET title=excluded.title,status=excluded.status,
                       chief_complaint=excluded.chief_complaint,evidence_file_name=excluded.evidence_file_name,
                       updated_at=CURRENT_TIMESTAMP, messages=consultation_session.messages || excluded.messages,
                       diagnosis=excluded.diagnosis, archived=false
                    """, doctorId,session.id(),patientRecordId,session.title(),session.status(),chiefComplaint,
                    session.evidenceFileName(),json.writeValueAsString(turn),json.writeValueAsString(diagnosis));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("问诊记录保存失败", e);
        }
    }

    public void saveReviewStatus(long doctorId, String sessionId, Long patientRecordId, String chiefComplaint) {
        jdbc.update("UPDATE final_diagnosis_record SET evidence_cutoff=clock_timestamp() WHERE user_id=? AND session_id=?", doctorId, sessionId);
        jdbc.update("""
                INSERT INTO consultation_session(doctor_id,session_id,patient_record_id,title,status,chief_complaint)
                VALUES(?,?,?,COALESCE(NULLIF(LEFT(CAST(? AS text),24),''),'已完成诊断'),'已形成最终结论',?)
                ON CONFLICT(doctor_id,session_id) DO UPDATE SET status=excluded.status,updated_at=CURRENT_TIMESTAMP
                """,doctorId,sessionId,patientRecordId,chiefComplaint,chiefComplaint);
    }

    public boolean archive(long doctorId, String sessionId) {
        return jdbc.update("UPDATE consultation_session SET archived=true WHERE doctor_id=? AND session_id=?", doctorId,sessionId) > 0;
    }

    // Redis 旧索引可能在多实例同时启动时重复读取；已写入的新数据及已归档的会话均不受影响。
    public void importLegacy(long doctorId, ChatSessionDto session, String messages, String diagnosis, OffsetDateTime updatedAt) {
        jdbc.update("""
                INSERT INTO consultation_session(doctor_id,session_id,title,status,evidence_file_name,messages,diagnosis,updated_at)
                VALUES(?,?,?,?,?,COALESCE(CAST(? AS jsonb),'[]'),CAST(? AS jsonb),?)
                ON CONFLICT(doctor_id,session_id) DO UPDATE SET messages=excluded.messages,diagnosis=excluded.diagnosis,
                    title=excluded.title,status=excluded.status,evidence_file_name=COALESCE(excluded.evidence_file_name,consultation_session.evidence_file_name)
                WHERE consultation_session.diagnosis IS NULL AND consultation_session.messages='[]' AND NOT consultation_session.archived
                """, doctorId,session.id(),session.title(),session.status(),session.evidenceFileName(),messages,diagnosis,updatedAt);
    }
}
