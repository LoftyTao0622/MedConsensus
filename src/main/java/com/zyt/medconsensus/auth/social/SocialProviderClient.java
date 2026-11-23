package com.zyt.medconsensus.auth.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class SocialProviderClient {
    private static final Logger log = LoggerFactory.getLogger(SocialProviderClient.class);
    private final SocialLoginProperties properties;
    private final ObjectMapper json;
    private final HttpClient http;

    @Autowired
    public SocialProviderClient(SocialLoginProperties properties, ObjectMapper json) {
        this(properties, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    SocialProviderClient(SocialLoginProperties properties, ObjectMapper json, HttpClient http) {
        this.properties = properties;
        this.json = json;
        this.http = http;
        for (String provider : List.of("wechat", "qq")) {
            if (settings(provider).isEnabled() && !enabled(provider)) {
                log.warn("第三方登录配置不完整或回调地址无效，已停用 {}", provider);
            }
        }
    }

    public boolean enabled(String provider) {
        var config = settings(provider);
        return config != null && config.available(provider);
    }

    public URI authorizationUri(String provider, String state) {
        var config = requireProvider(provider);
        if (!StringUtils.hasText(state)) throw new IllegalArgumentException("登录状态缺失");
        if ("wechat".equals(provider)) {
            return URI.create(endpoint("https://open.weixin.qq.com/connect/qrconnect",
                    "appid", config.getAppId(), "redirect_uri", config.getRedirectUri(), "response_type", "code",
                    "scope", "snsapi_login", "state", state) + "#wechat_redirect");
        }
        return endpoint("https://graph.qq.com/oauth2.0/authorize", "response_type", "code",
                "client_id", config.getAppId(), "redirect_uri", config.getRedirectUri(), "state", state);
    }

    public Identity exchange(String provider, String code) {
        var config = requireProvider(provider);
        if (!StringUtils.hasText(code) || code.length() > 2048) throw invalidAuthorization();
        JsonNode identity;
        if ("wechat".equals(provider)) {
            identity = getJson(endpoint("https://api.weixin.qq.com/sns/oauth2/access_token",
                    "appid", config.getAppId(), "secret", config.getAppSecret(), "code", code,
                    "grant_type", "authorization_code"));
            field(identity, "access_token");
        } else {
            JsonNode tokens = getJson(endpoint("https://graph.qq.com/oauth2.0/token", "grant_type", "authorization_code",
                    "client_id", config.getAppId(), "client_secret", config.getAppSecret(), "code", code,
                    "redirect_uri", config.getRedirectUri(), "fmt", "json"));
            identity = getJson(endpoint("https://graph.qq.com/oauth2.0/me",
                    "access_token", field(tokens, "access_token"), "fmt", "json"));
            if (!config.getAppId().equals(field(identity, "client_id"))) throw invalidAuthorization();
        }
        String openId = field(identity, "openid");
        if (openId.length() > 128 || openId.chars().anyMatch(c -> Character.isWhitespace(c) || Character.isISOControl(c))) {
            throw invalidAuthorization();
        }
        return new Identity(provider, config.getAppId(), openId);
    }

    private SocialLoginProperties.Provider settings(String provider) {
        if ("wechat".equals(provider)) return properties.getWechat();
        if ("qq".equals(provider)) return properties.getQq();
        return null;
    }

    private SocialLoginProperties.Provider requireProvider(String provider) {
        var config = settings(provider);
        if (config == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "不支持的登录方式");
        if (!config.available(provider)) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "该登录方式尚未配置");
        return config;
    }

    private static URI endpoint(String address, String... pairs) {
        StringJoiner query = new StringJoiner("&", address + "?", "");
        for (int i = 0; i < pairs.length; i += 2) {
            query.add(pairs[i] + "=" + URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        }
        return URI.create(query.toString());
    }

    private JsonNode getJson(URI uri) {
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw unavailable();
            JsonNode body = json.readTree(response.body());
            if (body == null || !body.isObject()) throw invalidAuthorization();
            if (body.has("error") || (body.has("errcode") && body.get("errcode").asInt(-1) != 0)) throw invalidAuthorization();
            return body;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (IOException exception) {
            // 平台请求地址包含凭据，不向客户端或日志传递底层异常。
            throw unavailable();
        }
    }

    private static String field(JsonNode body, String key) {
        JsonNode value = body.get(key);
        if (value == null || !value.isTextual() || !StringUtils.hasText(value.textValue())) throw invalidAuthorization();
        return value.textValue();
    }

    private static ResponseStatusException unavailable() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "登录平台暂时无法响应");
    }

    private static ResponseStatusException invalidAuthorization() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "登录授权无效，请重试");
    }

    public record Identity(String provider, String appId, String openId) {}
}
