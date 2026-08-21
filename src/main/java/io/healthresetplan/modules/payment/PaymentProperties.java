package io.healthresetplan.modules.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    private int orderExpireMinutes = 15;
    private int refundWindowDays = 7;
    private final Wechat wechat = new Wechat();
    private final Alipay alipay = new Alipay();

    public int getOrderExpireMinutes() { return orderExpireMinutes; }
    public void setOrderExpireMinutes(int value) { orderExpireMinutes = value; }
    public int getRefundWindowDays() { return refundWindowDays; }
    public void setRefundWindowDays(int value) { refundWindowDays = value; }
    public Wechat getWechat() { return wechat; }
    public Alipay getAlipay() { return alipay; }

    public static class Wechat {
        private boolean enabled;
        private String appId = "";
        private String merchantId = "";
        private String merchantSerialNumber = "";
        private String privateKeyPath = "";
        private String platformPublicKeyPath = "";
        private String platformPublicKeyId = "";
        private String apiV3Key = "";
        private String notifyUrl = "https://api.jkcqplan.com/api/v1/payments/wechat/notify";
        private String apiBase = "https://api.mch.weixin.qq.com";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getAppId() { return appId; }
        public void setAppId(String value) { appId = value; }
        public String getMerchantId() { return merchantId; }
        public void setMerchantId(String value) { merchantId = value; }
        public String getMerchantSerialNumber() { return merchantSerialNumber; }
        public void setMerchantSerialNumber(String value) { merchantSerialNumber = value; }
        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String value) { privateKeyPath = value; }
        public String getPlatformPublicKeyPath() { return platformPublicKeyPath; }
        public void setPlatformPublicKeyPath(String value) { platformPublicKeyPath = value; }
        public String getPlatformPublicKeyId() { return platformPublicKeyId; }
        public void setPlatformPublicKeyId(String value) { platformPublicKeyId = value; }
        public String getApiV3Key() { return apiV3Key; }
        public void setApiV3Key(String value) { apiV3Key = value; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String value) { notifyUrl = value; }
        public String getApiBase() { return apiBase; }
        public void setApiBase(String value) { apiBase = value; }
    }

    public static class Alipay {
        private boolean enabled;
        private String appId = "";
        private String privateKeyPath = "";
        private String alipayPublicKeyPath = "";
        private String notifyUrl = "https://api.jkcqplan.com/api/v1/payments/alipay/notify";
        private String gateway = "https://openapi.alipay.com/gateway.do";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public String getAppId() { return appId; }
        public void setAppId(String value) { appId = value; }
        public String getPrivateKeyPath() { return privateKeyPath; }
        public void setPrivateKeyPath(String value) { privateKeyPath = value; }
        public String getAlipayPublicKeyPath() { return alipayPublicKeyPath; }
        public void setAlipayPublicKeyPath(String value) { alipayPublicKeyPath = value; }
        public String getNotifyUrl() { return notifyUrl; }
        public void setNotifyUrl(String value) { notifyUrl = value; }
        public String getGateway() { return gateway; }
        public void setGateway(String value) { gateway = value; }
    }
}
