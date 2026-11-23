package com.zyt.medconsensus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zyt.medconsensus.agent.TreatmentAgent;
import com.zyt.medconsensus.dto.ChatSessionDto;
import com.zyt.medconsensus.dto.ConsultationRequest;
import com.zyt.medconsensus.dto.DiagnosticResponse;
import com.zyt.medconsensus.dto.DoctorReviewRequest;
import com.zyt.medconsensus.dto.MessageHistoryDto;
import com.zyt.medconsensus.mapper.ConsultationSessionStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import jakarta.servlet.http.Cookie;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReportAndSessionPersistenceTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ConsultationSessionStore sessions;
    @Autowired CollectorAgentService collector;
    @Autowired PatientPortalService portal;
    @Autowired ReportFollowUpService followUps;
    @Autowired MockMvc http;
    @MockBean TreatmentAgent treatment;

    long doctorId;
    long patientId;
    long chartId;
    String sessionId;
    ChatSessionDto summary;
    DiagnosticResponse diagnosis;

    @BeforeEach
    void fixtures() {
        sessionId = UUID.randomUUID().toString();
        String phone = sessionId.substring(0, 16);
        doctorId = jdbc.queryForObject("""
                INSERT INTO doctor_basic_info(username,password_hash,phone) VALUES(?,'test',?) RETURNING id
                """, Long.class, sessionId, phone);
        patientId = jdbc.queryForObject("""
                INSERT INTO patient_account(patient_name,password_hash,phone) VALUES('测试患者','test',?) RETURNING id
                """, Long.class, phone);
        chartId = jdbc.queryForObject("""
                INSERT INTO patient_basic_info(doctor_id,patient_account_id,patient_name,gender)
                VALUES(?,?,'测试患者','未填写') RETURNING id
                """, Long.class, doctorId, patientId);
        jdbc.update("""
                INSERT INTO doctor_patient_relation(doctor_id,patient_account_id,patient_record_id,status) VALUES(?,?,?,'ACTIVE')
                """, doctorId,patientId,chartId);
        jdbc.update("""
                INSERT INTO patient_consultation(doctor_id,patient_account_id,patient_record_id,session_id,status)
                VALUES(?,?,?,?,'WAITING_DOCTOR')
                """, doctorId,patientId,chartId,sessionId);
        summary = new ChatSessionDto(sessionId, "测试患者", "待医生定夺", null, null);
        diagnosis = new DiagnosticResponse("服务端初诊", 0.63, "待确认风险", List.of(), List.of(), List.of());
    }

    @Test
    void reviewPublishCorrectAndArchiveKeepPatientVersions() {
        sessions.appendTurn(doctorId,chartId,summary,"原始主诉",List.of(new MessageHistoryDto("user","描述一")),diagnosis);
        when(treatment.inferWithModel(any(), any(), any(), any(), any())).thenReturn(
                new TreatmentAgent.TreatmentOutcome(List.of(), List.of(), List.of(), "测试", "治疗说明"));
        var review = new DoctorReviewRequest();
        review.setSessionId(sessionId);
        review.setChiefComplaint("客户端主诉");
        review.setAiConclusion(diagnosis.conclusion());
        review.setRiskLevel("客户端风险");
        review.setConfidence(1d);
        var confirmed = collector.saveDoctorReview(doctorId,review);
        assertThat(confirmed.chiefComplaint()).isEqualTo("原始主诉");
        assertThat(confirmed.riskLevel()).isEqualTo(diagnosis.riskLevel());
        assertThat(confirmed.confidence()).isEqualTo(diagnosis.confidence());
        long reportId = confirmed.id();
        portal.publishReport(doctorId,reportId);
        portal.publishReport(doctorId,reportId);
        assertThat(portal.patientReportRevisions(patientId,reportId)).hasSize(1);
        assertThat(portal.patientReportRevisions(patientId,reportId).get(0).patientRecordId()).isEqualTo(chartId);
        jdbc.update("UPDATE patient_basic_info SET patient_name='更正姓名' WHERE id=?",chartId);
        assertThat(portal.patientReportRevisions(patientId,reportId).get(0).patientName()).isEqualTo("测试患者");
        assertThat(collector.saveDoctorReview(doctorId,review).publishedToPatient()).isTrue();
        review.setOpinion("医生更正后的内容");
        var corrected = collector.saveDoctorReview(doctorId,review);
        assertThat(corrected.publishedToPatient()).isFalse();
        var before = portal.dashboard(patientId);
        assertThat(before.reports().get(0).finalConclusion()).isEqualTo("服务端初诊");
        assertThat(before.consultations().get(0).reportPublished()).isTrue();
        portal.publishReport(doctorId,reportId);
        assertThat(portal.patientReportRevisions(patientId,reportId)).extracting("finalConclusion")
                .containsExactly("医生更正后的内容","服务端初诊");
        assertThat(portal.patientReportRevisions(patientId + 1000,reportId)).isEmpty();
        assertThat(portal.doctorReportRevisions(doctorId + 1000,reportId)).isEmpty();
        assertThatThrownBy(() -> portal.explainPublishedReport(patientId, reportId,
                new com.zyt.medconsensus.dto.PatientMessageRequest("之前的问题",1)))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("刷新");
        assertThat(portal.loadExplanationHistory(patientId,reportId).revision()).isEqualTo(2);
        collector.deleteSession(doctorId,sessionId);
        assertThat(collector.loadSessions(doctorId)).isEmpty();
        var archived = portal.dashboard(patientId);
        assertThat(archived.reports().get(0).revision()).isEqualTo(2);
        assertThat(archived.consultations().get(0).messages()).contains(new MessageHistoryDto("user","描述一"));
    }

    @Test
    void followUpEndpointsUseSessionIdentityCsrfAndActualHttpStatus() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_USER_ROLE", "PATIENT");
        session.setAttribute("CURRENT_USER_ID", patientId);
        var response = http.perform(get("/api/patient/follow-ups").session(session))
                .andExpect(status().isOk()).andReturn().getResponse();
        Cookie csrf = response.getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        http.perform(get("/api/workspace/follow-ups").session(session)).andExpect(status().isForbidden());
        http.perform(post("/api/patient/follow-ups/123/result").session(session)
                .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue())
                .contentType("application/json").content("{\"result\":\"已复诊\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void followUpRemainsBoundToThePublishedVersionUntilTheDoctorReadsTheResult() {
        long recordId = jdbc.queryForObject("""
                INSERT INTO final_diagnosis_record(user_id,patient_account_id,patient_record_id,session_id,review_status,final_conclusion)
                VALUES(?,?,?,?,'AI_CONFIRMED','首次结论') RETURNING id
                """, Long.class, doctorId, patientId, chartId, sessionId);
        var plan = new ReportFollowUpService.Plan(java.time.LocalDate.now().plusDays(10), "回报复查结果");
        followUps.publishWithPlan(doctorId, recordId, plan);
        followUps.publishWithPlan(doctorId, recordId, plan);
        var task = followUps.patientTasks(patientId).get(0);
        assertThat(followUps.patientTasks(patientId)).hasSize(1);
        assertThat(followUps.patientTasks(patientId + 1000)).isEmpty();
        assertThat(followUps.doctorTasks(doctorId + 1000)).isEmpty();
        var result = new ReportFollowUpService.Result("复查结果已收到");
        assertThatThrownBy(() -> followUps.submit(patientId + 1000, task.id(), result)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> followUps.complete(doctorId, task.id())).isInstanceOf(ResponseStatusException.class);
        followUps.submit(patientId, task.id(), result);
        assertThatThrownBy(() -> followUps.submit(patientId, task.id(), result)).isInstanceOf(ResponseStatusException.class);
        var submitted = followUps.doctorTasks(doctorId).get(0);
        assertThat(submitted.originalConclusion()).isEqualTo("首次结论");
        assertThat(submitted.revision()).isEqualTo(1);
        assertThat(submitted.patientResult()).isEqualTo(result.result());
        followUps.complete(doctorId, task.id());
        assertThat(followUps.patientTasks(patientId).get(0).reviewedAt()).isNotNull();
    }

    @Test
    void patientWaitingForReplyNeverReceivesPrivateDoctorConclusions() {
        jdbc.update("UPDATE patient_consultation SET status='NEEDS_PATIENT_REPLY' WHERE session_id=?", sessionId);
        var waiting = new ChatSessionDto(sessionId,"测试患者","待补充信息",null,null);
        var question = new MessageHistoryDto("assistant","请补充症状时间",true);
        sessions.appendTurn(doctorId,chartId,waiting,"主诉",List.of(
                new MessageHistoryDto("assistant","医生端的未发布初诊"), question),diagnosis);
        assertThat(portal.dashboard(patientId).consultations().get(0).messages()).containsExactly(question);
        sessions.appendTurn(doctorId,chartId,summary,"主诉",List.of(
                new MessageHistoryDto("assistant","更新后的未发布意见")),diagnosis);
        var dto = portal.dashboard(patientId).consultations().get(0);
        assertThat(dto.question()).isNull();
        assertThat(dto.messages()).extracting("content").doesNotContain("医生端的未发布初诊","更新后的未发布意见");
    }

    @Test
    void durableTimelineExceedsPromptLimitAndRejectsPatientSwitch() {
        var messages = new ArrayList<MessageHistoryDto>();
        for (int i = 0; i < 43; i++) messages.add(new MessageHistoryDto("user","问诊信息" + i));
        sessions.appendTurn(doctorId,chartId,summary,"主诉",messages,diagnosis);
        var restored = collector.loadSessionDetail(doctorId,sessionId);
        assertThat(restored.history()).containsExactlyElementsOf(messages);
        assertThat(restored.patientRecordId()).isEqualTo(chartId);
        assertThat(restored.diagnosis()).isEqualTo(diagnosis);
        var request = new ConsultationRequest();
        request.setSessionId(sessionId);
        request.setMessage("继续");
        request.setPatientRecordId(chartId + 1);
        assertThatThrownBy(() -> collector.organize(doctorId,request)).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("患者档案");
    }
}
