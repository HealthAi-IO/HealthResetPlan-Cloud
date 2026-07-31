package io.healthresetplan.modules.ai.chat;

import java.util.List;

/**
 * AI 对话请求。
 *
 * <p>客户端负责维护历史，每次请求带入完整消息列表（含历史）。</p>
 *
 * @param provider       大模型提供方（可选，默认 doubao）
 * @param messages       消息列表，格式同 OpenAI：[{"role":"user","content":"..."}]
 * @param profileSummary 用户健康档案摘要（可选，用于构建 system prompt）
 */
public record AiChatRequest(
        String provider,
        List<ChatMessage> messages,
        String profileSummary
) {
    public record ChatMessage(String role, String content) {}
}
