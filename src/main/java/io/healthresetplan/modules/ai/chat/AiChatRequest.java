package io.healthresetplan.modules.ai.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        @Size(max = 32) String provider,
        @Size(max = 100) List<@Valid ChatMessage> messages,
        @Size(max = 1000) String profileSummary,
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "sessionId 格式不合法") String sessionId,
        @Pattern(regexp = "^[0-9a-fA-F-]{36}$", message = "requestId 格式不合法") String requestId,
        Boolean personalized
) {
    public record ChatMessage(
            @Pattern(regexp = "^(user|assistant)$", message = "消息角色不合法") String role,
            @NotBlank @Size(max = 4000) String content) {}
}
