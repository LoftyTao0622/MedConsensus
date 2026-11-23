package com.zyt.medconsensus.service.impl;

import com.zyt.medconsensus.agent.ReviewerAgent;
import com.zyt.medconsensus.tool.MedicalWorkflowTools;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ReviewRoutingTests {
    private final MedicalWorkflowTools tools = new MedicalWorkflowTools();
    private final Map<String, Object> vote = Map.of("status", "VALID", "score", 0.9, "weight", 0.4);
    private final Map<String, Object> missing = ReviewerAgent.absent("模型", 0.3, "评审超时");

    @Test
    void highRiskOverridesModelFinalize() {
        var result = tools.vote(List.of(vote, vote), 0.99, tools.assessRisk("晕厥"));
        assertThat(CollectorAgentServiceImpl.normalizeDecisionRoute("finalize", result.route(), 0))
                .isEqualTo("human_review");
    }

    @Test
    void partialFailureKeepsCompletedVotesAndRequiresMajority() {
        var risk = tools.assessRisk("");
        var partial = tools.vote(List.of(vote, vote, missing), 0.9, risk);
        assertThat(partial.confidence()).isCloseTo(0.9, within(0.0001));
        assertThat(partial.route()).isEqualTo("finalize");
        var insufficient = tools.vote(List.of(vote, missing, missing), 0.99, risk);
        assertThat(insufficient.route()).isEqualTo("human_review");
    }
}
