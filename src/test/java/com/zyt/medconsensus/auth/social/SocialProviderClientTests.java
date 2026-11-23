package com.zyt.medconsensus.auth.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class SocialProviderClientTests {
    private final SocialLoginProperties properties = new SocialLoginProperties();
    private final HttpClient http = mock(HttpClient.class);
    private final ArrayDeque<String> replies = new ArrayDeque<>();
    private final List<URI> calls = new ArrayList<>();
    private SocialProviderClient client;

    @BeforeEach
    void setup() throws Exception {
        configure(properties.getWechat(), "wechat");
        configure(properties.getQq(), "qq");
        client = new SocialProviderClient(properties, new ObjectMapper(), http);
        when(http.<byte[]>send(any(), any())).thenAnswer(invocation -> {
            HttpRequest request = invocation.getArgument(0);
            assertThat(request.method()).isEqualTo("GET");
            calls.add(request.uri());
            @SuppressWarnings("unchecked")
            HttpResponse<byte[]> response = mock(HttpResponse.class);
            when(response.statusCode()).thenReturn(200);
            when(response.body()).thenReturn(replies.remove().getBytes(StandardCharsets.UTF_8));
            return response;
        });
    }

    private void configure(SocialLoginProperties.Provider provider, String name) {
        provider.setEnabled(true);
        provider.setAppId("123456");
        provider.setAppSecret("secret+/test");
        provider.setRedirectUri("https://example.test/api/auth/social/" + name + "/callback");
    }

    @Test
    void wechatExchangesWebsiteCodeForOpenId() {
        URI auth = client.authorizationUri("wechat", "state+123");
        assertThat(auth.getHost()).isEqualTo("open.weixin.qq.com");
        assertThat(auth.getPath()).isEqualTo("/connect/qrconnect");
        assertThat(auth.getFragment()).isEqualTo("wechat_redirect");
        assertThat(params(auth)).isEqualTo(Map.of("appid", "123456", "response_type", "code",
                "scope", "snsapi_login", "state", "state+123", "redirect_uri", properties.getWechat().getRedirectUri()));
        replies.add("{\"access_token\":\"token\",\"openid\":\"wx-user\",\"unionid\":\"different-id\"}");
        assertThat(client.exchange("wechat", "code&A=1")).isEqualTo(new SocialProviderClient.Identity("wechat", "123456", "wx-user"));
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).getHost()).isEqualTo("api.weixin.qq.com");
        assertThat(calls.get(0).getPath()).isEqualTo("/sns/oauth2/access_token");
        assertThat(params(calls.get(0))).isEqualTo(Map.of("appid", "123456", "secret", "secret+/test",
                "code", "code&A=1", "grant_type", "authorization_code"));
    }

    @Test
    void qqExchangesTokenAndChecksItsApplication() {
        URI auth = client.authorizationUri("qq", "s");
        assertThat(auth.getHost()).isEqualTo("graph.qq.com");
        assertThat(auth.getPath()).isEqualTo("/oauth2.0/authorize");
        assertThat(params(auth)).isEqualTo(Map.of("response_type", "code", "client_id", "123456",
                "state", "s", "redirect_uri", properties.getQq().getRedirectUri()));
        replies.add("{\"access_token\":\"token+&\",\"openid\":\"not-from-me\"}");
        replies.add("{\"client_id\":\"123456\",\"openid\":\"qq-user\"}");
        assertThat(client.exchange("qq", "code")).isEqualTo(new SocialProviderClient.Identity("qq", "123456", "qq-user"));
        assertThat(calls).extracting(URI::getHost).containsExactly("graph.qq.com", "graph.qq.com");
        assertThat(calls).extracting(URI::getPath).containsExactly("/oauth2.0/token", "/oauth2.0/me");
        assertThat(params(calls.get(0))).isEqualTo(Map.of("client_id", "123456", "client_secret", "secret+/test",
                "code", "code", "grant_type", "authorization_code", "fmt", "json", "redirect_uri", properties.getQq().getRedirectUri()));
        assertThat(params(calls.get(1))).isEqualTo(Map.of("access_token", "token+&", "fmt", "json"));
        replies.add("{\"access_token\":\"token\"}");
        replies.add("{\"client_id\":\"other-app\",\"openid\":\"qq-user\"}");
        assertThatThrownBy(() -> client.exchange("qq", "code")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsErrorEnvelopesAndMissingOpenIdWithoutEchoingSecrets() throws Exception {
        replies.add("{\"errcode\":40029,\"errmsg\":\"sensitive-code\",\"openid\":\"ignored\",\"access_token\":\"ignored\"}");
        assertThatThrownBy(() -> client.exchange("wechat", "sensitive-code"))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("sensitive-code").hasNoCause();
        replies.add("{\"access_token\":\"token\",\"unionid\":\"union-only\"}");
        assertThatThrownBy(() -> client.exchange("wechat", "code")).isInstanceOf(ResponseStatusException.class);
        doThrow(new IOException("secret+/test")).when(http).send(any(), any());
        assertThatThrownBy(() -> client.exchange("wechat", "code"))
                .isInstanceOf(ResponseStatusException.class).hasMessageNotContaining("secret+/test").hasNoCause();
    }

    @Test
    void misconfiguredProviderIsUnavailableIndependently() {
        var provider = properties.getWechat();
        for (String uri : List.of("", "/relative", "http://example.test/api/auth/social/wechat/callback",
                "https://user@example.test/api/auth/social/wechat/callback", properties.getQq().getRedirectUri(),
                provider.getRedirectUri() + "?state=constant", provider.getRedirectUri() + "#fragment")) {
            provider.setRedirectUri(uri);
            assertThat(client.enabled("wechat")).isFalse();
        }
        configure(provider, "wechat");
        provider.setAppSecret(" ");
        assertThat(client.enabled("wechat")).isFalse();
        assertThat(client.enabled("qq")).isTrue();
        assertThat(client.enabled("typo")).isFalse();
        assertThatThrownBy(() -> client.exchange("wechat", "code")).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(http);
    }

    private Map<String, String> params(URI uri) {
        return Arrays.stream(uri.getRawQuery().split("&")).map(pair -> pair.split("=", 2)).collect(
                Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
