package com.zyt.medconsensus.mapper;

import com.zyt.medconsensus.dto.ReportRevisionDto;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ReportPublicationStore {
    private final JdbcTemplate jdbc;
    private static final RowMapper<ReportRevisionDto> ROW = (rs, i) -> new ReportRevisionDto(
            rs.getLong("record_id"), rs.getInt("revision"), rs.getLong("doctor_id"), rs.getString("session_id"),
            rs.getString("chief_complaint"), rs.getString("ai_conclusion"), rs.getString("doctor_opinion"),
            rs.getString("final_conclusion"), rs.getString("risk_level"), rs.getObject("confidence", Double.class),
            rs.getString("treatment_advice"), rs.getObject("published_at", OffsetDateTime.class),
            rs.getObject("patient_record_id", Long.class), rs.getString("patient_name"));

    public ReportPublicationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    // 调用方须在同一事务内先锁定报告，避免并发发布生成两个版本。
    public void append(long recordId) {
        jdbc.update("""
                INSERT INTO report_publication(record_id,revision,doctor_id,patient_account_id,session_id,
                        chief_complaint,ai_conclusion,doctor_opinion,final_conclusion,risk_level,confidence,treatment_advice,published_at,
                        patient_record_id,patient_name)
                SELECT f.id, COALESCE((SELECT MAX(revision) FROM report_publication WHERE record_id=f.id),0)+1,
                       f.user_id,f.patient_account_id,f.session_id,f.chief_complaint,f.ai_conclusion,f.doctor_opinion,
                       f.final_conclusion,f.risk_level,f.confidence,f.treatment_advice,f.published_at,
                       f.patient_record_id,COALESCE(chart.patient_name,account.patient_name)
                FROM final_diagnosis_record f
                LEFT JOIN patient_basic_info chart ON chart.id=f.patient_record_id AND chart.doctor_id=f.user_id
                JOIN patient_account account ON account.id=f.patient_account_id WHERE f.id=?
                """, recordId);
        jdbc.update("""
                INSERT INTO publication_evidence(record_id,revision,source_id)
                SELECT f.id, (SELECT MAX(r.revision) FROM report_publication r WHERE r.record_id=f.id), e.id
                FROM final_diagnosis_record f JOIN medical_evidence_source e
                  ON e.doctor_id=f.user_id AND e.session_id=f.session_id
                WHERE f.id=? AND e.used_at<=f.evidence_cutoff
                """, recordId);
    }

    public List<ReportRevisionDto> forDoctor(long id, long doctorId) {
        return jdbc.query("SELECT * FROM report_publication WHERE record_id=? AND doctor_id=? ORDER BY revision DESC", ROW, id, doctorId);
    }

    public List<ReportRevisionDto> forPatient(long id, long patientId) {
        return jdbc.query("SELECT * FROM report_publication WHERE record_id=? AND patient_account_id=? ORDER BY revision DESC", ROW, id, patientId);
    }

    public Optional<ReportRevisionDto> latest(long id, long patientId) {
        return jdbc.query("SELECT * FROM report_publication WHERE record_id=? AND patient_account_id=? ORDER BY revision DESC LIMIT 1", ROW, id, patientId)
                .stream().findFirst();
    }

    public List<ReportRevisionDto> latestForPatient(long patientId) {
        return jdbc.query("""
                SELECT * FROM (SELECT DISTINCT ON (record_id) * FROM report_publication
                               WHERE patient_account_id=? ORDER BY record_id,revision DESC) latest
                ORDER BY published_at DESC,record_id DESC
                """, ROW, patientId);
    }
}
