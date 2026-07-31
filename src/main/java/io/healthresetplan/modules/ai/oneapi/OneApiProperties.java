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
 *     chat-order: [doubao, qwen, glm, deepseek]   # 首项为默认对话模型
 *     vision-provider: qwen                   # OCR 视觉模型
 *     daily-limit: 30
 *     timeout-seconds: 90
 *     providers:
 *       qwen:
 *         base-url: ${AI_CHAT_QWEN_API_BASE}
 *         api-key: ${AI_CHAT_QWEN_API_KEY}
 *         model: ${AI_CHAT_QWEN_MODEL}
 *       doubao:
 *         base-url: ${AI_CHAT_DOUBAO_API_BASE}
 *         api-key: ${AI_CHAT_VOLCENGINE_API_KEY}
 *         model: ${AI_CHAT_DOUBAO_MODEL}
 *       glm:
 *         base-url: ${AI_CHAT_GLM_API_BASE}
 *         api-key: ${AI_CHAT_VOLCENGINE_API_KEY}
 *         model: ${AI_CHAT_GLM_MODEL}
 *       deepseek:
 *         base-url: ${AI_CHAT_DEEPSEEK_API_BASE}
 *         api-key: ${AI_CHAT_VOLCENGINE_API_KEY}
 *         model: ${AI_CHAT_DEEPSEEK_MODEL}
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app.ai")
public class OneApiProperties {

    /** 各厂商配置，key 为厂商名（qwen / doubao / deepseek 等） */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /** 未指定模型时使用列表首项，不用于跨模型自动降级。 */
    private List<String> chatOrder = List.of("qwen", "doubao", "glm", "deepseek");

    /** 视觉模型厂商（体检报告 OCR） */
    private String visionProvider = "qwen";

    /** 单用户每日最大 AI 请求次数 */
    private int dailyLimit = 30;

    /** HTTP 请求超时秒数 */
    private int timeoutSeconds = 90;

    /** 7 天健康规划缓存分钟数；相同档案、目标、模型命中后直接返回。 */
    private int planCacheMinutes = 30;

    /** 7 天健康规划最大输出 token，控制生成时长和截断风险。 */
    private long planMaxCompletionTokens = 4096L;

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

    public int getPlanCacheMinutes() { return planCacheMinutes; }
    public void setPlanCacheMinutes(int planCacheMinutes) { this.planCacheMinutes = planCacheMinutes; }

    public long getPlanMaxCompletionTokens() { return planMaxCompletionTokens; }
    public void setPlanMaxCompletionTokens(long planMaxCompletionTokens) {
        this.planMaxCompletionTokens = planMaxCompletionTokens;
    }

    // ── 单个厂商配置 ──────────────────────────────────────────

    public static class ProviderConfig {
        /** OpenAI 兼容 API 的 base URL（不含 /v1 后缀） */
        private String baseUrl;
        /** 该厂商的 API Key */
        private String apiKey;
        /** AI model name. */
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
