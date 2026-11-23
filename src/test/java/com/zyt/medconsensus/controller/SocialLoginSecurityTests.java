package com.zyt.medconsensus.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.auth.social.wechat.enabled=false", "app.auth.social.qq.enabled=false"})
@AutoConfigureMockMvc
class SocialLoginSecurityTests {
    @Autowired MockMvc mvc;

    @Test
    void anonymousLoginPageCanDiscoverProvidersButBindingActionsRequireCsrfAndPendingIdentity() throws Exception {
        var response = mvc.perform(get("/api/auth/social/providers"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.providers").isEmpty()).andReturn().getResponse();
        Cookie csrf = response.getCookie("XSRF-TOKEN");
        assertThat(csrf).isNotNull();
        for (String path : new String[]{"/api/auth/social/bind", "/api/auth/social/cancel"}) {
            String body = "{\"phone\":\"13900000000\",\"password\":\"local-test\",\"bindingId\":\"unknown\"}";
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                            .cookie(csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                    .andExpect(status().isUnauthorized());
        }
    }
}
