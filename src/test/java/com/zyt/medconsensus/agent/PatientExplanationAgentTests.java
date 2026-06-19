package com.zyt.medconsensus.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.zyt.medconsensus.agent.schema.PatientExplanationResultSchema;
import com.zyt.medconsensus.llm.MultiModelGateway;
import com.zyt.medconsensus.llm.validation.LlmJsonValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatientExplanationAgentTests {

    @Test
    void rejectsMedicationChangeInstructions() {
        MultiModelGateway gateway = org.mockito.Mockito.mock(MultiModelGateway.class);
        LlmJsonValidator validator = org.mockito.Mockito.mock(LlmJsonValidator.class);
        PatientExplanationAgent agent = new PatientExplanationAgent(gateway, validator);
        MultiModelGateway.ModelSpec spec = new MultiModelGateway.ModelSpec(
                "key",
                "https://example.test",
                "model",
                0.2
        );

        when(gateway.chat(eq(spec), anyString(), any())).thenReturn("{}");
        when(validator.parseAndValidate(anyString(), eq(PatientExplanationResultSchema.class)))
                .thenReturn(new PatientExplanationResultSchema("建议停药并改用另一种药物。", false, ""));

        PatientExplanationAgent.ExplanationOutcome outcome =
                agent.explain(spec, "医生最终结论：已发布结论", List.of(), "药物应该怎么调整？");

        assertThat(outcome.requiresDoctor()).isTrue();
        assertThat(outcome.answer()).doesNotContain("停药", "改用");
    }
}
