package io.healthresetplan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.web-push")
public class WebPushProperties {
    private boolean enabled;
    private String publicKey = "";
    private String privateKey = "";
    private String subject = "mailto:support@jkcqplan.com";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getPublicKey() { return publicKey; }
    public void setPublicKey(String publicKey) { this.publicKey = publicKey; }
    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
}
