package io.healthresetplan.modules.ai.plan;

/**
 * AI 健康方案响应。
 *
 * @param provider  实际使用的大模型提供方
 * @param rawJson   LLM 返回的完整 JSON 字符串（客户端解析展示）
 * @param promptTokens   消耗的提示词 token 数
 * @param completionTokens 消耗的输出 token 数
 */
public record AiPlanResponse(
        String provider,
        String rawJson,
        int promptTokens,
        int completionTokens
) {
}
