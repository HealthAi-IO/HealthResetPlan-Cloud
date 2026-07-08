package io.healthresetplan.modules.membership.gateway.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment.alipay")
public class AlipayProperties {

    private boolean enabled = false;
    private String appId;
    private String privateKey;
    private String alipayPublicKey;
    private String notifyUrl;
    private String gatewayUrl = "https://openapi.alipay.com/gateway.do";
    private String charset = "UTF-8";
    private String signType = "RSA2";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getPrivateKey() { return privateKey; }
    public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

    public String getAlipayPublicKey() { return alipayPublicKey; }
    public void setAlipayPublicKey(String alipayPublicKey) { this.alipayPublicKey = alipayPublicKey; }

    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

    public String getGatewayUrl() { return gatewayUrl; }
    public void setGatewayUrl(String gatewayUrl) { this.gatewayUrl = gatewayUrl; }

    public String getCharset() { return charset; }
    public void setCharset(String charset) { this.charset = charset; }

    public String getSignType() { return signType; }
    public void setSignType(String signType) { this.signType = signType; }

    public boolean isReady() {
        return enabled
                && hasText(appId)
                && hasText(privateKey)
                && hasText(alipayPublicKey)
                && hasText(notifyUrl);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
