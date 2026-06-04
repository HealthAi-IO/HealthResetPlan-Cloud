package io.healthresetplan.modules.ai.oneapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 多厂商直连配置。
 *
 * <p>通过 {@code app.ai.*} 注入，本地密钥写在 application-local.yml（不提交 git）。</p>
 *
 * <pre>
 * app:
 *   ai:
 *     chat-order: [qwen, doubao, deepseek]   # 对话模型优先级
 *     vision-provider: qwen                   # OCR 视觉模型
 *     daily-limit: 30
 *     timeout-seconds: 90
 *     providers:
 *       qwen:
 *         base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
 *         api-key: sk-xxx
 *         model: qwen-turbo
 *       doubao:
 *         base-url: https://ark.cn-beijing.volces.com/api/v3
 *         api-key: ark-xxx
 *         model: doubao-lite-32k
 *       deepseek:
 *         base-url: https://api.deepseek.com
 *         api-key: sk-xxx
 *         model: deepseek-chat
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class OneApiProperties {

    /** 各厂商配置，key 为厂商名（qwen / doubao / deepseek 等） */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /** 对话/计划生成的模型尝试顺序 */
    private List<String> chatOrder = List.of("qwen", "doubao", "deepseek");

    /** 视觉模型厂商（体检报告 OCR） */
    private String visionProvider = "qwen";

    /** 单用户每日最大 AI 请求次数 */
    private int dailyLimit = 30;

    /** HTTP 请求超时秒数 */
    private int timeoutSeconds = 90;

    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }

    public List<String> getChatOrder() { return chatOrder; }
    public void setChatOrder(List<String> chatOrder) { this.chatOrder = chatOrder; }

    public String getVisionProvider() { return visionProvider; }
    public void setVisionProvider(String visionProvider) { this.visionProvider = visionProvider; }

    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }

    // ── 单个厂商配置 ──────────────────────────────────────────

    public static class ProviderConfig {
        /** OpenAI 兼容 API 的 base URL（不含 /v1 后缀） */
        private String baseUrl;
        /** 该厂商的 API Key */
        private String apiKey;
        /** 模型名，如 qwen-turbo / doubao-lite-32k / deepseek-chat */
        private String model;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public boolean isConfigured() {
            return baseUrl != null && !baseUrl.isBlank()
                && apiKey != null && !apiKey.isBlank();
        }
    }
}
