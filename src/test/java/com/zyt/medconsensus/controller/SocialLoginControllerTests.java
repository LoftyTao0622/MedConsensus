package com.zyt.medconsensus.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zyt.medconsensus.auth.social.SocialProviderClient;
import com.zyt.medconsensus.dto.AuthResponse;
import com.zyt.medconsensus.service.ExternalIdentityService;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.util.UriComponentsBuilder;

class SocialLoginControllerTests {

    private static final String BASE = "/api/auth/social";
    private static final String PHONE = "13800000001";
    private static final String PASSWORD = "test-password";
    private final SocialProviderClient providers = mock(SocialProviderClient.class);
    private final ExternalIdentityService identities = mock(ExternalIdentityService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final MockHttpSession session = new MockHttpSession();
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SocialLoginController(providers, identities))
                .setControllerAdvice(new ApiExceptionHandler()).build();
        when(providers.authorizationUri(anyString(), anyString())).thenAnswer(call -> URI.create(
                "https://login.example/" + call.getArgument(0, String.class) + "?state=" + call.getArgument(1, String.class)));
        when(providers.exchange(anyString(), anyString())).thenAnswer(call -> new SocialProviderClient.Identity(
                call.getArgument(0, String.class), "app-id", "external-subject"));
    }

    @Test
    void disabledProvidersAreOmitted() throws Exception {
        mvc.perform(get(BASE + "/providers")).andExpect(status().isOk())
                .andExpect(jsonPath("$.providers").isArray()).andExpect(jsonPath("$.providers").isEmpty());
    }

    @Test
    void firstAuthorizationBindsOnlyToItsChosenRoleAndRotatesTheSession() throws Exception {
        String bindingId = startBinding("wechat", "PATIENT");
        String previousSessionId = session.getId();
        when(identities.bind("wechat", "app-id", "external-subject", "PATIENT", PHONE, PASSWORD))
                .thenReturn(account("PATIENT"));

        bind(bindingId).andExpect(status().isOk()).andExpect(jsonPath("$.role").value("PATIENT"));

        assertThat(session.getId()).isNotEqualTo(previousSessionId);
        assertThat(session.getAttribute(LoginSession.USER_ID)).isEqualTo(13L);
        assertThat(session.getAttribute(LoginSession.ROLE)).isEqualTo("PATIENT");
        assertThat(session.getAttribute(LoginSession.SOCIAL_FLOW)).isNull();
        bind(bindingId).andExpect(status().is4xxClientError());
        verify(identities).bind("wechat", "app-id", "external-subject", "PATIENT", PHONE, PASSWORD);
    }

    @Test
    void linkedAccountCallbackSignsInAndCannotBeReplayed() throws Exception {
        String state = start("qq", "DOCTOR");
        String previousSessionId = session.getId();
        when(identities.lookup("qq", "app-id", "external-subject", "DOCTOR"))
                .thenReturn(Optional.of(account("DOCTOR")));

        callback("qq", state).andExpect(status().isSeeOther()).andExpect(redirectedUrl("/"));

        assertThat(session.getId()).isNotEqualTo(previousSessionId);
        assertThat(session.getAttribute(LoginSession.USER_ID)).isEqualTo(13L);
        assertThat(session.getAttribute(LoginSession.ROLE)).isEqualTo("DOCTOR");
        callback("qq", state).andExpect(status().isSeeOther()).andExpect(redirectedUrl("/?social=error"));
        verify(providers).exchange("qq", "test-code");
    }

    @ParameterizedTest
    @ValueSource(strings = {"state", "provider", "expiry"})
    void invalidAuthorizationIsRejectedBeforeCodeExchange(String reason) throws Exception {
        String state = start("wechat", "PATIENT");
        String provider = "wechat";
        if (reason.equals("state")) state += "x";
        if (reason.equals("provider")) {
            when(providers.enabled("qq")).thenReturn(true);
            provider = "qq";
        }
        if (reason.equals("expiry")) {
            session.setAttribute(LoginSession.SOCIAL_FLOW,
                    new SocialLoginController.Authorization("wechat", "PATIENT", state, Instant.now().minusSeconds(1)));
        }
        callback(provider, state).andExpect(status().isSeeOther()).andExpect(redirectedUrl("/?social=error"));
        verify(providers, never()).exchange(anyString(), anyString());
        verifyNoInteractions(identities);
        assertThat(session.getAttribute(LoginSession.USER_ID)).isNull();
    }

    @Test
    void replacementMakesTheOldBindingIdUnusable() throws Exception {
        String oldBinding = startBinding("wechat", "PATIENT");
        String replacement = startBinding("qq", "DOCTOR");
        assertThat(replacement).isNotEqualTo(oldBinding);
        bind(oldBinding).andExpect(status().isConflict());
        mvc.perform(post(BASE + "/cancel").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("bindingId", oldBinding)))).andExpect(status().isConflict());
        verify(identities, never()).bind(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        mvc.perform(get(BASE + "/pending").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.bindingId").value(replacement));
    }

    @Test
    void cancelPreventsThePendingBindingFromEstablishingALogin() throws Exception {
        String binding = startBinding("qq", "PATIENT");
        mvc.perform(post(BASE + "/cancel").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("bindingId", binding)))).andExpect(status().is2xxSuccessful());
        bind(binding).andExpect(status().is4xxClientError());
        verify(identities, never()).bind(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
        assertThat(session.getAttribute(LoginSession.USER_ID)).isNull();
    }

    @Test
    void expiredBindingCannotBeViewedOrCompleted() throws Exception {
        when(providers.enabled("wechat")).thenReturn(true);
        session.setAttribute(LoginSession.SOCIAL_FLOW, new SocialLoginController.Pending(
                "wechat", "app-id", "external-subject", "DOCTOR", "expired-binding", Instant.now().minusSeconds(1)));
        mvc.perform(get(BASE + "/pending").session(session)).andExpect(status().isUnauthorized());
        bind("expired-binding").andExpect(status().isUnauthorized());
        assertThat(session.getAttribute(LoginSession.USER_ID)).isNull();
        verifyNoInteractions(identities);
    }

    private String start(String provider, String role) throws Exception {
        when(providers.enabled(provider)).thenReturn(true);
        String url = mvc.perform(get(BASE + "/" + provider + "/start").session(session).param("role", role))
                .andExpect(status().isSeeOther()).andReturn().getResponse().getRedirectedUrl();
        assertThat(url).startsWith("https://login.example/" + provider + "?state=");
        String state = UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state");
        assertThat(state).isNotBlank();
        return state;
    }

    private String startBinding(String provider, String role) throws Exception {
        String location = callback(provider, start(provider, role)).andExpect(status().isSeeOther())
                .andReturn().getResponse().getRedirectedUrl();
        assertThat(location).matches("/\\?social=bind#bindingId=[A-Za-z0-9_-]+");
        String callbackBindingId = URI.create(location).getRawFragment().substring("bindingId=".length());
        var response = mvc.perform(get(BASE + "/pending").session(session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value(provider)).andExpect(jsonPath("$.role").value(role))
                .andExpect(jsonPath("$.bindingId").value(callbackBindingId)).andReturn().getResponse();
        return json.readTree(response.getContentAsByteArray()).get("bindingId").asText();
    }

    private ResultActions callback(String provider, String state) throws Exception {
        return mvc.perform(get(BASE + "/" + provider + "/callback").session(session)
                .param("state", state).param("code", "test-code"));
    }

    private ResultActions bind(String bindingId) throws Exception {
        return mvc.perform(post(BASE + "/bind").session(session).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("bindingId", bindingId, "phone", PHONE, "password", PASSWORD))));
    }

    private AuthResponse account(String role) {
        return new AuthResponse(13L, "已验证用户", PHONE, role, null, null, null, null, null, null, null, null);
    }
}
