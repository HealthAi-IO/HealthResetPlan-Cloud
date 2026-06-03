package io.healthresetplan.modules.ai.oneapi;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * OneAPI 统一接入配置。
 *
 * <p>通过 {@code app.ai.one-api.*} 注入，本地覆盖值写在 application-local.yml（不提交 git）。</p>
 */
@Component
@ConfigurationProperties(prefix = "app.ai.one-api")
public class OneApiProperties {

    /** OneAPI 实例地址，如 http://localhost:3000 */
    private String baseUrl = "http://localhost:3000";

    /** OneAPI 令牌（在 OneAPI 控制台 → 令牌 中创建） */
    private String apiKey = "";

    /**
     * 对话 / 计划生成模型优先级列表。
     * 依次尝试，直到成功或全部失败。
     */
    private List<String> chatModels = List.of("qwen-turbo", "doubao-lite", "deepseek-chat");

    /** 视觉模型（体检报告 OCR） */
    private String visionModel = "qwen-vl-plus";

    /** 单用户每日最大 AI 请求次数（与 OneAPI 后台配置保持一致） */
    private int dailyLimit = 30;

    /** HTTP 请求超时秒数 */
    private int timeoutSeconds = 90;

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public List<String> getChatModels() { return chatModels; }
    public void setChatModels(List<String> chatModels) { this.chatModels = chatModels; }

    public String getVisionModel() { return visionModel; }
    public void setVisionModel(String visionModel) { this.visionModel = visionModel; }

    public int getDailyLimit() { return dailyLimit; }
    public void setDailyLimit(int dailyLimit) { this.dailyLimit = dailyLimit; }

    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
}
