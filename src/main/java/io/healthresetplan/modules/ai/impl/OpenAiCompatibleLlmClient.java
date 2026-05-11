package io.healthresetplan.modules.ai.impl;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.LlmClient;
import io.healthresetplan.modules.ai.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * OpenAI 兼容协议的大模型客户端基类。
 *
 * <p>DeepSeek / 豆包 / 通义千问 / 智谱 / Kimi 等都兼容这一协议。</p>
 *
 * <p>注意：传入 prompt 时必须由调用方完成脱敏（去除真实姓名、电话、地址等）。</p>
 */
public abstract class OpenAiCompatibleLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleLlmClient.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final RestClient restClient;
    private final LlmProperties.ProviderConfig config;
    private final String provider;

    protected OpenAiCompatibleLlmClient(String provider, LlmProperties.ProviderConfig config) {
        this.provider = provider;
        this.config = config;
        this.restClient = RestClient.builder()
                .baseUrl(config.getBaseUrl())
                .defaultHeaders(h -> h.set(HttpHeaders.AUTHORIZATION, "Bearer " + config.getApiKey()))
                .build();
    }

    @Override
    public String provider() {
        return provider;
    }

    @Override
    public ChatResponse chat(ChatRequest request) {
        String model = request.model() == null || request.model().isBlank() ? config.getModel() : request.model();

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", request.messages().stream()
                        .map(m -> Map.of("role", m.role(), "content", m.content()))
                        .toList(),
                "temperature", request.temperature() == null ? 0.7 : request.temperature(),
                "max_tokens", request.maxTokens() == null ? 1024 : request.maxTokens()
        );

        try {
            String response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            Map<String, Object> map = OBJECT_MAPPER.readValue(response, Map.class);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) map.getOrDefault("choices", List.of());
            String content = "";
            if (!choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                content = String.valueOf(message != null ? message.get("content") : "");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> usage = (Map<String, Object>) map.getOrDefault("usage", Map.of());
            return new ChatResponse(
                    provider,
                    model,
                    content,
                    new Usage(
                            ((Number) usage.getOrDefault("prompt_tokens", 0)).intValue(),
                            ((Number) usage.getOrDefault("completion_tokens", 0)).intValue(),
                            ((Number) usage.getOrDefault("total_tokens", 0)).intValue()
                    )
            );
        } catch (Exception e) {
            log.error("调用 LLM 失败 provider={} model={}", provider, model, e);
            throw new BusinessException(50301, "AI 服务暂不可用：" + provider);
        }
    }

    @SuppressWarnings("unused")
    protected Duration timeout() {
        return Duration.ofSeconds(60);
    }
}
