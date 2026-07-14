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

    public ProductionStartupValidator(JwtProperties jwt, SmsProperties sms, OneApiProperties ai) {
        this.jwt = jwt;
        this.sms = sms;
        this.ai = ai;
    }

    @Override
    public void run(ApplicationArguments args) {
        require(jwt.getSecret(), "JWT_SECRET", 32);
        require(System.getenv("FILES_STORAGE_PATH"), "FILES_STORAGE_PATH", 1);
        if (sms.isDebugCodeEnabled() || !sms.isJdcloudReady()) {
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
