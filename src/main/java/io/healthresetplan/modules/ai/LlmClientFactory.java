package io.healthresetplan.modules.ai;

import io.healthresetplan.common.exception.BusinessException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 大模型客户端工厂。
 */
@Component
@Configuration
@EnableConfigurationProperties(LlmProperties.class)
public class LlmClientFactory {

    private final LlmProperties properties;
    private final Map<String, LlmClient> clients;

    public LlmClientFactory(LlmProperties properties, Map<String, LlmClient> clients) {
        this.properties = properties;
        this.clients = clients;
    }

    public LlmClient get(String provider) {
        String key = provider == null || provider.isBlank() ? properties.getDefaultProvider() : provider;
        LlmClient client = clients.get(key + "LlmClient");
        if (client == null) {
            for (LlmClient c : clients.values()) {
                if (key.equals(c.provider())) {
                    return c;
                }
            }
            throw new BusinessException(50301, "AI 提供方未配置：" + key);
        }
        return client;
    }

    public LlmClient getDefault() {
        return get(properties.getDefaultProvider());
    }
}
