package com.zyt.medconsensus.auth.social;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties("app.auth.social")
public class SocialLoginProperties {
    private final Provider wechat = new Provider();
    private final Provider qq = new Provider();

    public Provider getWechat() { return wechat; }
    public Provider getQq() { return qq; }

    public static class Provider {
        private boolean enabled;
        private String appId;
        private String appSecret;
        private String redirectUri;

        boolean available(String provider) {
            if (!enabled || !StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)
                    || !StringUtils.hasText(redirectUri)) return false;
            try {
                URI uri = URI.create(redirectUri);
                return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null
                        && uri.getRawUserInfo() == null && uri.getRawQuery() == null && uri.getRawFragment() == null
                        && ("/api/auth/social/" + provider + "/callback").equals(uri.getRawPath());
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAppId() { return appId; }
        public void setAppId(String appId) { this.appId = appId; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
        public String getRedirectUri() { return redirectUri; }
        public void setRedirectUri(String redirectUri) { this.redirectUri = redirectUri; }
    }
}
