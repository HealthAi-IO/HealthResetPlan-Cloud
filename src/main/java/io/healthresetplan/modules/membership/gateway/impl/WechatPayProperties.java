package io.healthresetplan.modules.membership.gateway.impl;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment.wechat")
public class WechatPayProperties {

    private boolean enabled = false;
    private String appId;
    private String mchId;
    private String merchantSerialNumber;
    private String privateKeyPath;
    private String publicKeyId;
    private String publicKeyPath;
    private String apiV3Key;
    private String notifyUrl;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public String getMchId() { return mchId; }
    public void setMchId(String mchId) { this.mchId = mchId; }

    public String getMerchantSerialNumber() { return merchantSerialNumber; }
    public void setMerchantSerialNumber(String merchantSerialNumber) {
        this.merchantSerialNumber = merchantSerialNumber;
    }

    public String getPrivateKeyPath() { return privateKeyPath; }
    public void setPrivateKeyPath(String privateKeyPath) { this.privateKeyPath = privateKeyPath; }

    public String getPublicKeyId() { return publicKeyId; }
    public void setPublicKeyId(String publicKeyId) { this.publicKeyId = publicKeyId; }

    public String getPublicKeyPath() { return publicKeyPath; }
    public void setPublicKeyPath(String publicKeyPath) { this.publicKeyPath = publicKeyPath; }

    public String getApiV3Key() { return apiV3Key; }
    public void setApiV3Key(String apiV3Key) { this.apiV3Key = apiV3Key; }

    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }

    public boolean isReady() {
        return enabled
                && hasText(appId)
                && hasText(mchId)
                && hasText(merchantSerialNumber)
                && hasText(privateKeyPath)
                && hasText(apiV3Key)
                && hasText(notifyUrl);
    }

    public boolean hasPublicKeyConfig() {
        return hasText(publicKeyId) && hasText(publicKeyPath);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
