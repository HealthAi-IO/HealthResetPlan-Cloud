package io.healthresetplan.modules.ai;

import java.util.List;

/**
 * 大模型客户端抽象。多厂商共用同一份接口，便于切换 / 灰度 / 降级。
 */
public interface LlmClient {

    String provider();

    ChatResponse chat(ChatRequest request);

    record ChatRequest(
            String model,
            List<Message> messages,
            Double temperature,
            Integer maxTokens
    ) {
    }

    record Message(String role, String content) {
        public static Message system(String content) {
            return new Message("system", content);
        }

        public static Message user(String content) {
            return new Message("user", content);
        }

        public static Message assistant(String content) {
            return new Message("assistant", content);
        }
    }

    record ChatResponse(
            String provider,
            String model,
            String content,
            Usage usage
    ) {
    }

    record Usage(int promptTokens, int completionTokens, int totalTokens) {
    }
}
