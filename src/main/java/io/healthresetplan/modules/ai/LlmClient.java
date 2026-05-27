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

    /**
     * 图片分析（视觉模型）。
     *
     * @param imageBase64 图片 base64 编码（不含 data URI 前缀）
     * @param mimeType    MIME 类型，如 image/jpeg、image/png
     * @param prompt      文字提示词
     * @return 模型返回的文本内容
     */
    default String analyzeImage(String imageBase64, String mimeType, String prompt) {
        throw new UnsupportedOperationException("Provider '" + provider() + "' 不支持图像分析");
    }
}
