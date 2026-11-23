package com.zyt.medconsensus.service;

import com.zyt.medconsensus.agent.CollectorAgent;
import com.zyt.medconsensus.agent.DiagnosisAgent;
import com.zyt.medconsensus.agent.ReviewerAgent;
import com.zyt.medconsensus.dto.*;
import com.zyt.medconsensus.graphkg.MedicalGraphReasoningService;
import com.zyt.medconsensus.mapper.ConsultationSessionStore;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest
@Transactional
class DiagnosisWorkflowTests {
    @Autowired CollectorAgentService workflow;
    @Autowired ConsultationSessionStore sessions;
    @MockBean CollectorAgent collector;
    @MockBean DiagnosisAgent diagnosis;
    @MockBean ReviewerAgent reviewers;
    @MockBean MedicalGraphReasoningService graph;

    @Test
    void priorRiskSurvivesANewSummaryAndOneFailedReviewer() {
        String sessionId = UUID.randomUUID().toString();
        sessions.appendTurn(3L, null, new ChatSessionDto(sessionId, "测试", "待补充信息", null, null),
                "最初主诉", List.of(new MessageHistoryDto("user", "之前出现过晕厥")), null);
        when(collector.collect(any(), anyString(), anyList(), anyString())).thenReturn(
                new CollectorAgent.CollectorOutcome("已整理", "测试", "补充主诉", "本轮信息充足", List.of(), List.of()));
        when(diagnosis.diagnose(any(), anyString(), anyString(), anyList(), anyList(), anyString())).thenReturn(
                new DiagnosisAgent.DiagnosisOutcome("待核初诊", 0.95, "中低风险", List.of(), List.of()));
        when(reviewers.review(anyString(), any(), anyDouble(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(0);
                    if ("GLM".equals(name)) throw new IllegalStateException("模拟评审服务不可用");
                    return Map.of("name", name, "weight", (double) invocation.getArgument(2),
                            "score", 0.96, "status", "VALID", "comment", "已评审");
                });
        ConsultationRequest request = new ConsultationRequest();
        request.setSessionId(sessionId);
        request.setPatientName("测试");
        request.setMessage("补充资料，已描述持续三天的症状和本次检查结果。");
        var result = workflow.organize(3L, request);
        assertThat(result.session().status()).isEqualTo("待医生定夺");
        assertThat(result.diagnosis().riskLevel()).isEqualTo("高风险");
        assertThat(result.diagnosis().reviewers()).extracting(DiagnosticResponse.ReviewerScore::status)
                .containsExactlyInAnyOrder("VALID", "VALID", "ABSENT");
    }
}
