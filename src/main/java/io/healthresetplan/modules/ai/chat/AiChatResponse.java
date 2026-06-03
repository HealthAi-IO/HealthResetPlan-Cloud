package io.healthresetplan.modules.ai.chat;

/**
 * AI 对话响应。
 *
 * @param provider         实际使用的模型提供方
 * @param content          AI 回复内容
 * @param promptTokens     输入 token 数
 * @param completionTokens 输出 token 数
 */
public record AiChatResponse(
        String provider,
        String content,
        int promptTokens,
        int completionTokens
) {
}
