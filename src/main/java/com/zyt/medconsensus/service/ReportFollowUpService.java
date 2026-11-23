package com.zyt.medconsensus.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportFollowUpService {
    public record Plan(@NotNull @FutureOrPresent LocalDate dueDate,
                       @NotBlank @Size(max = 2000) String instructions) {}
    public record Result(@NotBlank @Size(max = 6000) String result) {}
    public record FollowUp(long id, long recordId, int revision, String originalConclusion,
                           LocalDate dueDate, String instructions, String patientResult,
                           OffsetDateTime submittedAt, OffsetDateTime reviewedAt) {}

    private static final String SELECT = """
            SELECT f.*, p.final_conclusion FROM report_follow_up f
            JOIN report_publication p USING (record_id, revision)
            """;
    private static final RowMapper<FollowUp> ROW = (rs, index) -> new FollowUp(
            rs.getLong("id"), rs.getLong("record_id"), rs.getInt("revision"),
            rs.getString("final_conclusion"), rs.getObject("due_date", LocalDate.class),
            rs.getString("instructions"), rs.getString("patient_result"),
            rs.getObject("submitted_at", OffsetDateTime.class), rs.getObject("reviewed_at", OffsetDateTime.class));
    private final PatientPortalService portal;
    private final JdbcTemplate jdbc;

    public ReportFollowUpService(PatientPortalService portal, JdbcTemplate jdbc) {
        this.portal = portal;
        this.jdbc = jdbc;
    }

    @Transactional
    public void publishWithPlan(long doctorId, long recordId, Plan plan) {
        portal.publishReport(doctorId, recordId);
        // 发布过程持有报告行锁，确保计划引用本次发布的版本。相同请求可以安全重试。
        int inserted = jdbc.update("""
                INSERT INTO report_follow_up(record_id,revision,due_date,instructions)
                SELECT record_id,revision,?,? FROM report_publication WHERE record_id=? AND doctor_id=?
                ORDER BY revision DESC LIMIT 1 ON CONFLICT(record_id,revision) DO NOTHING
                """, plan.dueDate(), plan.instructions().trim(), recordId, doctorId);
        if (inserted == 0) {
            Boolean samePlan = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM report_follow_up WHERE record_id=?
                    AND revision=(SELECT MAX(revision) FROM report_publication WHERE record_id=? AND doctor_id=?)
                    AND due_date=? AND instructions=?)
                    """, Boolean.class, recordId, recordId, doctorId, plan.dueDate(), plan.instructions().trim());
            if (!Boolean.TRUE.equals(samePlan)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "此版本已有其他随访计划，请刷新查看");
            }
        }
    }

    public List<FollowUp> doctorTasks(long doctorId) {
        return jdbc.query(SELECT + " WHERE p.doctor_id=? ORDER BY f.due_date,f.id", ROW, doctorId);
    }

    public List<FollowUp> patientTasks(long patientId) {
        return jdbc.query(SELECT + " WHERE p.patient_account_id=? ORDER BY f.due_date,f.id", ROW, patientId);
    }

    public void submit(long patientId, long id, Result result) {
        int rows = jdbc.update("""
                UPDATE report_follow_up f SET patient_result=?, submitted_at=CURRENT_TIMESTAMP
                FROM report_publication p WHERE (p.record_id,p.revision)=(f.record_id,f.revision)
                AND f.id=? AND p.patient_account_id=? AND f.submitted_at IS NULL
                """, result.result().trim(), id, patientId);
        requireChanged(rows);
    }

    public void complete(long doctorId, long id) {
        int rows = jdbc.update("""
                UPDATE report_follow_up f SET reviewed_at=CURRENT_TIMESTAMP
                FROM report_publication p WHERE (p.record_id,p.revision)=(f.record_id,f.revision)
                AND f.id=? AND p.doctor_id=? AND f.submitted_at IS NOT NULL AND f.reviewed_at IS NULL
                """, id, doctorId);
        requireChanged(rows);
    }

    private void requireChanged(int rows) {
        if (rows == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "随访不存在或状态已变更，请刷新后重试");
        }
    }
}
