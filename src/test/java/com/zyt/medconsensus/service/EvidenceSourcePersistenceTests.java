package com.zyt.medconsensus.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.EvidenceSource;
import com.zyt.medconsensus.mapper.ConsultationSessionStore;
import com.zyt.medconsensus.mapper.ReportPublicationStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class EvidenceSourcePersistenceTests {
    @Autowired EvidenceSourceService sources;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired ConsultationSessionStore sessions;
    @Autowired ReportPublicationStore publications;

    @Test
    void originalAndCorrectionsBelongToConsultationAndFreezeAtConfirmation() throws Exception {
        String session = UUID.randomUUID().toString();
        String phone = session.substring(0, 16);
        long doctor = jdbc.queryForObject("INSERT INTO doctor_basic_info(username,password_hash,phone) VALUES(?,'test',?) RETURNING id",
                Long.class, session, phone);
        long patient = jdbc.queryForObject("INSERT INTO patient_account(patient_name,password_hash,phone) VALUES('测试','test',?) RETURNING id",
                Long.class, phone);
        long chart = jdbc.queryForObject("INSERT INTO patient_basic_info(doctor_id,patient_account_id,patient_name,gender) VALUES(?,?,'测试','未知') RETURNING id",
                Long.class, doctor, patient);
        var file = new MockMultipartFile("file", "检查.png", "image/png", new byte[]{1, 2, 3});
        var source = sources.save(doctor, patient, session, chart, session, file,
                json.readTree("{\"keyMeasurements\":[\"CRP 24\"]}"), "CRP 24");
        jdbc.update("""
                INSERT INTO patient_consultation
                (doctor_id,patient_account_id,patient_record_id,session_id,status,evidence_status,evidence_source_id)
                VALUES(?,?,?,?,'WAITING_DOCTOR_EVIDENCE_REVIEW','PENDING_DOCTOR',?)
                """, doctor, patient, chart, session, source.id());

        assertThat(sources.list(doctor, false, session)).containsExactly(source);
        assertThat(sources.list(patient, true, session)).containsExactly(source);
        assertThat(sources.list(patient + 10000, true, session)).isEmpty();
        assertThat(sources.original(patient, true, source.id()).bytes()).containsExactly(1, 2, 3);
        assertThatThrownBy(() -> sources.original(doctor + 10000, false, source.id())).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> sources.correctPatientUpload(doctor + 10000, source.id(), "其他医生")).isInstanceOf(ResponseStatusException.class);
        sources.correctPatientUpload(doctor, source.id(), "CRP 24 mg/L");
        assertThat(sources.find(doctor, false, source.id()).originalText()).isEqualTo("CRP 24");
        assertThat(sources.find(doctor, false, source.id()).reviewedText()).isEqualTo("CRP 24 mg/L");
        assertThat(jdbc.queryForObject("SELECT evidence_text FROM patient_consultation WHERE session_id=?", String.class, session))
                .isEqualTo("CRP 24 mg/L");
        jdbc.update("UPDATE patient_consultation SET evidence_status='CONFIRMED' WHERE session_id=?", session);
        assertThatThrownBy(() -> sources.correctPatientUpload(doctor, source.id(), "已锁定")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void revisionsFreezeOnlyEvidenceUsedBeforeReviewAndPreventDraftReassignment() throws Exception {
        String session = UUID.randomUUID().toString();
        String phone = session.substring(0, 16);
        long doctor = jdbc.queryForObject("INSERT INTO doctor_basic_info(username,password_hash,phone) VALUES(?,'test',?) RETURNING id",
                Long.class, session, phone);
        long patient = jdbc.queryForObject("INSERT INTO patient_account(patient_name,password_hash,phone) VALUES('测试','test',?) RETURNING id",
                Long.class, phone);
        long chart = jdbc.queryForObject("INSERT INTO patient_basic_info(doctor_id,patient_account_id,patient_name,gender) VALUES(?,?,'测试','未知') RETURNING id",
                Long.class, doctor, patient);
        var file = new MockMultipartFile("file", "项目.png", "image/png", new byte[]{1});
        var extracted = json.readTree("{\"keyMeasurements\":[\"CRP\"]}");
        var draft = sources.save(doctor, null, null, chart, session, file, extracted, "CRP");
        var request = new ConsultationRequest();
        request.setMedicalEvidenceId(draft.id());
        request.setMedicalEvidence("手动确认");
        request.setMedicalEvidenceConfirmed(true);
        request.setSessionId(session);
        request.setPatientRecordId(chart + 1);
        assertThatThrownBy(() -> sources.prepareDoctorUpload(doctor, request)).isInstanceOf(ResponseStatusException.class);
        request.setPatientRecordId(chart);
        request.setSessionId("不同问诊");
        assertThatThrownBy(() -> sources.prepareDoctorUpload(doctor, request)).isInstanceOf(ResponseStatusException.class);
        request.setSessionId(session);
        sources.prepareDoctorUpload(doctor, request);
        var summary = new ChatSessionDto(session, "问诊", "待医生定夺", null, draft.fileName());
        var diagnosis = new DiagnosticResponse("初诊", 0.6, "中", List.of(), List.of(), List.of());
        sessions.appendTurn(doctor, chart, summary, "主诉", List.of(), diagnosis, draft.id(), "手动确认");
        long report = jdbc.queryForObject("""
                INSERT INTO final_diagnosis_record(user_id,patient_account_id,patient_record_id,session_id,review_status,published_at)
                VALUES(?,?,?,?,'AI_CONFIRMED',clock_timestamp()) RETURNING id
                """, Long.class, doctor, patient, chart, session);
        sessions.saveReviewStatus(doctor, session, chart, "主诉");
        var later = sources.save(doctor, null, session, chart, session, file, extracted, "后续资料");
        sources.save(doctor, patient, session, chart, session, file, extracted, "待确认资料");
        publications.append(report);
        assertThat(sources.forRevision(patient, true, report, 1)).extracting(EvidenceSource::id).containsExactly(draft.id());
        assertThat(sources.forRevision(patient + 10000, true, report, 1)).isEmpty();
        assertThatThrownBy(() -> sources.original(patient, true, later.id())).isInstanceOf(ResponseStatusException.class);
        sessions.saveReviewStatus(doctor, session, chart, "主诉");
        publications.append(report);
        assertThat(sources.forRevision(doctor, false, report, 2)).extracting(EvidenceSource::id)
                .containsExactlyInAnyOrder(draft.id(), later.id());
        assertThat(sources.forRevision(patient, true, report, 1)).extracting(EvidenceSource::id).containsExactly(draft.id());
    }
}
