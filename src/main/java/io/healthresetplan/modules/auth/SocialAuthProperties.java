package io.healthresetplan.modules.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.social-auth")
public class SocialAuthProperties {
    private final Wechat wechat = new Wechat();

    public Wechat getWechat() { return wechat; }

    public static class Wechat {
        private boolean enabled;
        private String appId = "";
        private String appSecret = "";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getAppId() { return appId; }
        public void setAppId(String value) { appId = value; }
        public String getAppSecret() { return appSecret; }
        public void setAppSecret(String value) { appSecret = value; }
    }
}
