package com.zyt.medconsensus.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zyt.medconsensus.graphkg.MedicalGraphReasoningService;
import com.zyt.medconsensus.mapper.FinalDiagnosisRecordMapper;
import com.zyt.medconsensus.mapper.PatientBasicInfoMapper;
import com.zyt.medconsensus.observability.LangSmithTracingService;
import com.zyt.medconsensus.service.CaseImportService;
import com.zyt.medconsensus.service.CollectorAgentService;
import com.zyt.medconsensus.service.PatientSkillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MedicalWorkspaceControllerRoleTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        MedicalWorkspaceController controller = new MedicalWorkspaceController(
                org.mockito.Mockito.mock(SimpMessagingTemplate.class),
                org.mockito.Mockito.mock(CollectorAgentService.class),
                org.mockito.Mockito.mock(PatientBasicInfoMapper.class),
                org.mockito.Mockito.mock(FinalDiagnosisRecordMapper.class),
                org.mockito.Mockito.mock(LangSmithTracingService.class),
                org.mockito.Mockito.mock(PatientSkillService.class),
                org.mockito.Mockito.mock(CaseImportService.class),
                org.mockito.Mockito.mock(MedicalGraphReasoningService.class)
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void patientCannotAccessDoctorWorkspace() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_USER_ID", 21L);
        session.setAttribute("CURRENT_USER_ROLE", "PATIENT");

        mockMvc.perform(get("/api/workspace/patients").session(session))
                .andExpect(status().isForbidden());
    }
}
