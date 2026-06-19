package com.zyt.medconsensus.controller;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zyt.medconsensus.service.PatientPortalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PatientPortalControllerTests {

    private PatientPortalService patientPortalService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        patientPortalService = org.mockito.Mockito.mock(PatientPortalService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PatientPortalController(patientPortalService))
                .build();
    }

    @Test
    void patientCanReadOwnDashboard() throws Exception {
        MockHttpSession session = session(12L, "PATIENT");

        mockMvc.perform(get("/api/patient/dashboard").session(session))
                .andExpect(status().isOk());

        verify(patientPortalService).dashboard(12L);
    }

    @Test
    void doctorCannotReadPatientDashboard() throws Exception {
        mockMvc.perform(get("/api/patient/dashboard").session(session(7L, "DOCTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCannotReadDoctorCollaboration() throws Exception {
        mockMvc.perform(get("/api/workspace/collaboration").session(session(12L, "PATIENT")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patientCanOpenPublishedReportExplanationChat() throws Exception {
        MockHttpSession session = session(12L, "PATIENT");

        mockMvc.perform(get("/api/patient/reports/5/explanations").session(session))
                .andExpect(status().isOk());

        verify(patientPortalService).loadExplanationHistory(12L, 5L);
    }

    @Test
    void doctorCannotOpenPatientReportExplanationChat() throws Exception {
        mockMvc.perform(get("/api/patient/reports/5/explanations").session(session(7L, "DOCTOR")))
                .andExpect(status().isForbidden());
    }

    private MockHttpSession session(Long userId, String role) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("CURRENT_USER_ID", userId);
        session.setAttribute("CURRENT_USER_ROLE", role);
        return session;
    }
}
