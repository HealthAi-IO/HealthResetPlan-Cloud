package io.healthresetplan.modules.ai.impl;

import io.healthresetplan.modules.ai.LlmClient;
import io.healthresetplan.modules.ai.LlmProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 注册各家大模型客户端 Bean。
 *
 * <p>名称形如 {@code deepseekLlmClient}，与 {@link io.healthresetplan.modules.ai.LlmClientFactory} 约定一致。</p>
 */
@Configuration
public class LlmClientsConfig {

    @Bean("deepseekLlmClient")
    public LlmClient deepseekLlmClient(LlmProperties properties) {
        LlmProperties.ProviderConfig cfg = properties.getProviders().get("deepseek");
        if (cfg == null || cfg.getBaseUrl() == null) {
            return null;
        }
        return new OpenAiCompatibleLlmClient("deepseek", cfg) {};
    }

    @Bean("doubaoLlmClient")
    public LlmClient doubaoLlmClient(LlmProperties properties) {
        LlmProperties.ProviderConfig cfg = properties.getProviders().get("doubao");
        if (cfg == null || cfg.getBaseUrl() == null) {
            return null;
        }
        return new OpenAiCompatibleLlmClient("doubao", cfg) {};
    }

    @Bean("qwenLlmClient")
    public LlmClient qwenLlmClient(LlmProperties properties) {
        LlmProperties.ProviderConfig cfg = properties.getProviders().get("qwen");
        if (cfg == null || cfg.getBaseUrl() == null) {
            return null;
        }
        return new OpenAiCompatibleLlmClient("qwen", cfg) {};
    }

    /** qwen-vl：通义千问视觉语言模型，用于体检报告图片解析 */
    @Bean("qwenVlLlmClient")
    public LlmClient qwenVlLlmClient(LlmProperties properties) {
        LlmProperties.ProviderConfig cfg = properties.getProviders().get("qwen-vl");
        if (cfg == null || cfg.getBaseUrl() == null) {
            return null;
        }
        return new OpenAiCompatibleLlmClient("qwen-vl", cfg) {};
    }
}
