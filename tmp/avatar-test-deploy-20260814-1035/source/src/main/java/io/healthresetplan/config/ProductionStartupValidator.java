package io.healthresetplan.config;

import io.healthresetplan.modules.ai.oneapi.OneApiProperties;
import io.healthresetplan.modules.sms.SmsProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionStartupValidator implements ApplicationRunner {

    private final JwtProperties jwt;
    private final SmsProperties sms;
    private final OneApiProperties ai;
    private final DataEncryptionProperties dataEncryption;
    private final OssProperties oss;

    public ProductionStartupValidator(
            JwtProperties jwt,
            SmsProperties sms,
            OneApiProperties ai,
            DataEncryptionProperties dataEncryption,
            OssProperties oss) {
        this.jwt = jwt;
        this.sms = sms;
        this.ai = ai;
        this.dataEncryption = dataEncryption;
        this.oss = oss;
    }

    @Override
    public void run(ApplicationArguments args) {
        require(jwt.getSecret(), "JWT_SECRET", 32);
        require(dataEncryption.getKey(), "DATA_ENCRYPTION_KEY", 44);
        require(oss.getAccessKeyId(), "JDCLOUD_OSS_ACCESS_KEY_ID", 8);
        require(oss.getSecretAccessKey(), "JDCLOUD_OSS_SECRET_ACCESS_KEY", 16);
        require(oss.getEndpoint(), "JDCLOUD_OSS_ENDPOINT", 8);
        require(oss.getBucket(), "JDCLOUD_OSS_BUCKET", 3);
        if (!sms.isDebugCodeEnabled() && !sms.isJdcloudReady()) {
            throw new IllegalStateException("生产环境必须关闭短信调试验证码并配置短信服务");
        }
        boolean aiReady = ai.getProviders().values().stream()
                .anyMatch(provider -> hasText(provider.getBaseUrl()) && hasText(provider.getApiKey()) && hasText(provider.getModel()));
        if (!aiReady) throw new IllegalStateException("生产环境至少需要配置一个 AI 服务商密钥");
    }

    private void require(String value, String name, int minimumLength) {
        if (value == null || value.isBlank() || value.trim().length() < minimumLength) {
            throw new IllegalStateException("生产环境缺少或无效配置：" + name);
        }
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
