package com.zyt.medconsensus.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.entity.DoctorBasicInfo;
import com.zyt.medconsensus.entity.PatientAccount;
import com.zyt.medconsensus.mapper.DoctorBasicInfoMapper;
import com.zyt.medconsensus.mapper.PatientAccountMapper;
import com.zyt.medconsensus.service.impl.DoctorServiceImpl;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthRoleControllerTests {
    private static final String PHONE = "13900000001";
    private static final Long SHARED_ID = 21L;
    private final ObjectMapper json = new ObjectMapper();
    private final DoctorBasicInfoMapper doctors = mock(DoctorBasicInfoMapper.class);
    private final PatientAccountMapper patients = mock(PatientAccountMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        var service = new DoctorServiceImpl(doctors, patients, new BCryptPasswordEncoder(4));
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(service))
                .setControllerAdvice(new ApiExceptionHandler()).build();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ADMIN"})
    void loginAndRegistrationRequireAnExplicitSupportedRole(String role) throws Exception {
        var session = new MockHttpSession();
        for (String path : new String[]{"login", "register"}) {
            submit(path, session, role, "user", "valid-password")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        }
        assertThat(session.getAttribute("CURRENT_USER_ID")).isNull();
        assertThat(session.getAttribute("CURRENT_USER_ROLE")).isNull();
        verifyNoInteractions(doctors, patients);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "ADMIN"})
    void incompleteOrInvalidSessionsAreRejectedWithoutRoleWriteback(String role) throws Exception {
        var session = spy(new MockHttpSession());
        session.setAttribute("CURRENT_USER_ID", SHARED_ID);
        if (role != null) session.setAttribute("CURRENT_USER_ROLE", role);
        clearInvocations(session);

        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        verify(session, never()).setAttribute(eq("CURRENT_USER_ROLE"), any());
        verifyNoInteractions(doctors, patients);
    }

    @Test
    void samePhoneAndNumericIdRemainIndependentAcrossRegistrationLoginAndRestore() throws Exception {
        when(doctors.save(any(DoctorBasicInfo.class))).thenAnswer(invocation -> {
            DoctorBasicInfo doctor = invocation.getArgument(0);
            doctor.setId(SHARED_ID);
            when(doctors.findByPhone(PHONE)).thenReturn(Optional.of(doctor));
            when(doctors.findById(SHARED_ID)).thenReturn(Optional.of(doctor));
            return doctor;
        });
        when(patients.save(any(PatientAccount.class))).thenAnswer(invocation -> {
            PatientAccount patient = invocation.getArgument(0);
            patient.setId(SHARED_ID);
            when(patients.findByPhone(PHONE)).thenReturn(Optional.of(patient));
            when(patients.findById(SHARED_ID)).thenReturn(Optional.of(patient));
            return patient;
        });

        var doctorSession = new MockHttpSession();
        var patientSession = new MockHttpSession();
        expectIdentity(submit("register", doctorSession, "DOCTOR", "doctor", "doctor-pass"), "DOCTOR", "doctor");
        expectIdentity(submit("register", patientSession, "PATIENT", "patient", "patient-pass"), "PATIENT", "patient");
        expectIdentity(mvc.perform(get("/api/auth/me").session(doctorSession)), "DOCTOR", "doctor");
        expectIdentity(mvc.perform(get("/api/auth/me").session(patientSession)), "PATIENT", "patient");

        submit("login", new MockHttpSession(), "PATIENT", null, "doctor-pass").andExpect(status().isUnauthorized());
        submit("login", new MockHttpSession(), "DOCTOR", null, "patient-pass").andExpect(status().isUnauthorized());
        var doctorLogin = new MockHttpSession();
        var patientLogin = new MockHttpSession();
        expectIdentity(submit("login", doctorLogin, "DOCTOR", null, "doctor-pass"), "DOCTOR", "doctor");
        expectIdentity(submit("login", patientLogin, "PATIENT", null, "patient-pass"), "PATIENT", "patient");
        expectIdentity(mvc.perform(get("/api/auth/me").session(doctorLogin)), "DOCTOR", "doctor");
        expectIdentity(mvc.perform(get("/api/auth/me").session(patientLogin)), "PATIENT", "patient");
    }

    private ResultActions submit(String path, MockHttpSession session, String role, String name, String password) throws Exception {
        var body = new HashMap<String, String>(Map.of("phone", PHONE, "password", password));
        if (role != null) body.put("role", role);
        if (name != null && path.equals("register")) body.put("username", name);
        return mvc.perform(post("/api/auth/" + path).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body)));
    }

    private void expectIdentity(ResultActions response, String role, String name) throws Exception {
        response.andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SHARED_ID.intValue()))
                .andExpect(jsonPath("$.phone").value(PHONE))
                .andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.username").value(name));
    }
}
