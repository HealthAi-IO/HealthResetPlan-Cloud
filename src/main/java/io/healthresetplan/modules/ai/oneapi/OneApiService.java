package io.healthresetplan.modules.ai.oneapi;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionContentPartImage;
import com.openai.models.chat.completions.ChatCompletionContentPartText;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import com.openai.models.ResponseFormatJsonObject;
import io.healthresetplan.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
public class OneApiService {

    private static final Logger log = LoggerFactory.getLogger(OneApiService.class);
    private static final long OCR_MAX_COMPLETION_TOKENS = 8192L;

    private final OneApiProperties props;
    private final JdbcTemplate jdbc;
    private volatile Map<String, OpenAIClient> clients = Map.of();
    private volatile Map<String, OneApiProperties.ProviderConfig> providerConfigs = Map.of();
    private final Map<String, AtomicLong> dailyCounters = new ConcurrentHashMap<>();
    private volatile LocalDate counterDate = LocalDate.now();

    public OneApiService(OneApiProperties props, JdbcTemplate jdbc) {
        this.props = props;
        this.jdbc = jdbc;
    }

    @PostConstruct
    void initClients() {
        reloadClients();
    }

    public synchronized void reloadClients() {
        Map<String, OneApiProperties.ProviderConfig> nextConfigs = loadProviderConfigs();
        Map<String, OpenAIClient> nextClients = new HashMap<>();

        nextConfigs.forEach((name, cfg) -> {
            if (!cfg.isConfigured()) {
                log.warn("AI provider [{}] is not configured, skipped", name);
                return;
            }
            String baseUrl = normalizeBaseUrl(cfg.getBaseUrl());
            OpenAIClient client = OpenAIOkHttpClient.builder()
                    .apiKey(cfg.getApiKey())
                    .baseUrl(baseUrl)
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .build();
            nextClients.put(name, client);
            log.info("AI provider [{}] initialized, model={}", name, cfg.getModel());
        });

        providerConfigs = nextConfigs;
        clients = Map.copyOf(nextClients);

        if (nextClients.isEmpty()) {
            log.warn("No AI provider is configured. AI features will be unavailable.");
        }
    }

    private Map<String, OneApiProperties.ProviderConfig> loadProviderConfigs() {
        Map<String, OneApiProperties.ProviderConfig> configs = new HashMap<>(props.getProviders());
        try {
            jdbc.queryForList("""
                    SELECT provider, base_url, model, api_key_cipher
                    FROM ai_provider_config
                    WHERE deleted_at IS NULL
                      AND status = 1
                      AND api_key_cipher <> ''
                    """).forEach(row -> {
                OneApiProperties.ProviderConfig cfg = new OneApiProperties.ProviderConfig();
                cfg.setBaseUrl(text(row.get("base_url")));
                cfg.setApiKey(text(row.get("api_key_cipher")));
                cfg.setModel(text(row.get("model")));
                configs.put(text(row.get("provider")), cfg);
            });
        } catch (Exception e) {
            log.warn("Load AI providers from database failed, fallback to application config: {}", e.getMessage());
        }
        return configs;
    }

    public String complete(String userId, List<ChatCompletionMessageParam> messages) {
        return complete(userId, messages, null);
    }

    public String complete(String userId, List<ChatCompletionMessageParam> messages, String preferredProvider) {
        return complete(userId, messages, preferredProvider, 2048L);
    }

    public String complete(
            String userId,
            List<ChatCompletionMessageParam> messages,
            String preferredProvider,
            long maxCompletionTokens) {
        return completeWithProvider(userId, messages, preferredProvider, maxCompletionTokens).content();
    }

    public AiCompletion completeWithProvider(
            String userId,
            List<ChatCompletionMessageParam> messages,
            String preferredProvider,
            long maxCompletionTokens) {

        boolean rateLimited = false;
        for (String providerName : providerOrder(preferredProvider)) {
            OpenAIClient client = clients.get(providerName);
            if (client == null) continue;

            String model = providerConfigs.get(providerName).getModel();
            try {
                var response = client.chat().completions().create(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(maxCompletionTokens)
                                .build());

                String content = response.choices().get(0).message().content().orElse("");
                log.info("AI complete ok provider={} model={}", providerName, model);
                return new AiCompletion(providerName, content);
            } catch (RateLimitException e) {
                log.warn("AI provider rate limited provider={}", providerName);
                rateLimited = true;
            } catch (UnauthorizedException e) {
                log.error("AI key unauthorized provider={}", providerName);
            } catch (Exception e) {
                log.warn("AI provider={} unavailable, trying next", providerName);
            }
        }

        if (rateLimited) {
            throw new BusinessException(42902, "AI 服务暂时繁忙，请稍后再试");
        }
        throw new BusinessException(50301, "所有 AI 厂商暂时不可用，请稍后重试");
    }

    public record AiCompletion(String provider, String content) {}

    public void stream(
            String userId,
            List<ChatCompletionMessageParam> messages,
            Consumer<String> tokenConsumer,
            Runnable onDone) {
        stream(userId, messages, null, tokenConsumer, onDone);
    }

    public void stream(
            String userId,
            List<ChatCompletionMessageParam> messages,
            String preferredProvider,
            Consumer<String> tokenConsumer,
            Runnable onDone) {

        boolean rateLimited = false;
        for (String providerName : providerOrder(preferredProvider)) {
            OpenAIClient client = clients.get(providerName);
            if (client == null) continue;

            String model = providerConfigs.get(providerName).getModel();
            try {
                try (var streamResponse = client.chat().completions().createStreaming(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(2048L)
                                .build())) {
                    streamResponse.stream().forEach(chunk -> extractToken(chunk).forEach(tokenConsumer));
                }

                log.info("AI stream done provider={}", providerName);
                onDone.run();
                return;
            } catch (RateLimitException e) {
                log.warn("AI stream provider rate limited provider={}", providerName);
                rateLimited = true;
            } catch (UnauthorizedException e) {
                log.error("AI stream key unauthorized provider={}", providerName);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("AI stream provider={} unavailable, trying next", providerName);
            }
        }

        if (rateLimited) {
            throw new BusinessException(42902, "AI 服务暂时繁忙，请稍后再试");
        }
        throw new BusinessException(50301, "所有 AI 厂商暂时不可用，请稍后重试");
    }

    public VisionCompletion analyzeImage(String userId, String imageBase64, String mimeType, String prompt) {
        BusinessException lastError = null;
        for (String providerName : providerOrder(props.getVisionProvider())) {
            if (!clients.containsKey(providerName)) continue;
            try {
                String content = analyzeImageWithProvider(userId, imageBase64, mimeType, prompt, providerName);
                if (!hasUsableVisionContent(content)) {
                    lastError = new BusinessException(50301, "OCR 模型返回空内容");
                    log.warn("OCR provider={} returned empty content, trying next", providerName);
                    continue;
                }
                String model = providerConfigs.get(providerName).getModel();
                return new VisionCompletion(providerName, model, content);
            } catch (BusinessException e) {
                if (e.getCode() != 40101 && e.getCode() != 42902 && e.getCode() != 50301) {
                    throw e;
                }
                lastError = e;
                log.warn("OCR provider={} unavailable, trying next", providerName);
            }
        }

        if (lastError != null && lastError.getCode() == 42902) {
            throw lastError;
        }
        throw new BusinessException(50301, "No vision model is currently available");
    }

    public record VisionCompletion(String provider, String model, String content) {
        public String label() {
            return model == null || model.isBlank() ? provider : provider + " / " + model;
        }
    }

    static boolean hasUsableVisionContent(String content) {
        return content != null && !content.isBlank();
    }

    private String analyzeImageWithProvider(
            String userId, String imageBase64, String mimeType, String prompt, String providerName) {
        long startedAt = System.currentTimeMillis();

        OpenAIClient client = clients.get(providerName);
        if (client == null) {
            throw new BusinessException(50301, "视觉模型厂商 [" + providerName + "] 未配置");
        }

        String model = providerConfigs.get(providerName).getModel();
        ensureVisionCapableModel(providerName, model);

        try {
            var imageContent = ChatCompletionContentPart.ofImageUrl(
                    ChatCompletionContentPartImage.builder()
                            .imageUrl(ChatCompletionContentPartImage.ImageUrl.builder()
                                    .url("data:" + mimeType + ";base64," + imageBase64)
                                    .build())
                            .build());
            var textContent = ChatCompletionContentPart.ofText(
                    ChatCompletionContentPartText.builder()
                            .text(prompt)
                            .build());
            var userMessage = ChatCompletionMessageParam.ofUser(
                    ChatCompletionUserMessageParam.builder()
                            .content(ChatCompletionUserMessageParam.Content
                                    .ofArrayOfContentParts(List.of(textContent, imageContent)))
                            .build());

            ChatCompletionCreateParams.Builder request = ChatCompletionCreateParams.builder()
                    .model(model)
                    .messages(List.of(userMessage))
                    .maxCompletionTokens(OCR_MAX_COMPLETION_TOKENS)
                    .temperature(0.0);
            if ("qwen".equalsIgnoreCase(providerName)) {
                request.responseFormat(ResponseFormatJsonObject.builder().build())
                        .putAdditionalBodyProperty("enable_thinking", JsonValue.from(false));
            }

            var response = client.chat().completions().create(request.build());

            String content = response.choices().get(0).message().content().orElse("");
            log.info("OCR complete provider={} model={} finishReason={} elapsedMs={}",
                    providerName,
                    model,
                    response.choices().get(0).finishReason().asString(),
                    System.currentTimeMillis() - startedAt);
            return content;
        } catch (RateLimitException e) {
            throw new BusinessException(42902, "AI 服务暂时繁忙，请稍后再试");
        } catch (UnauthorizedException e) {
            throw new BusinessException(40101, "视觉模型 Key 无效：" + providerName);
        } catch (UnexpectedStatusCodeException e) {
            log.error("OCR failed provider={} status={} elapsedMs={}",
                    providerName, e.statusCode(), System.currentTimeMillis() - startedAt);
            throw new BusinessException(50301, "OCR 识别失败，请检查视觉模型配置");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR failed provider={} elapsedMs={}",
                    providerName, System.currentTimeMillis() - startedAt, e);
            throw new BusinessException(50301, "OCR 识别失败，请稍后重试");
        }
    }

    private void ensureVisionCapableModel(String providerName, String model) {
        if (!"qwen".equalsIgnoreCase(providerName)) {
            return;
        }
        String normalized = model == null ? "" : model.toLowerCase();
        if (!normalized.contains("vl") && !normalized.startsWith("qwen3.7-plus")) {
            throw new BusinessException(
                    50301,
                    "报告图片识别需要视觉模型，请将 AI_CHAT_QWEN_MODEL 配置为 qwen3.7-plus 等视觉模型");
        }
    }

    public static ChatCompletionMessageParam systemMsg(String content) {
        return ChatCompletionMessageParam.ofSystem(
                ChatCompletionSystemMessageParam.builder()
                        .content(content)
                        .build());
    }

    public static ChatCompletionMessageParam userMsg(String content) {
        return ChatCompletionMessageParam.ofUser(
                ChatCompletionUserMessageParam.builder()
                        .content(content)
                        .build());
    }

    public static ChatCompletionMessageParam assistantMsg(String content) {
        return ChatCompletionMessageParam.ofAssistant(
                com.openai.models.chat.completions.ChatCompletionAssistantMessageParam.builder()
                        .content(content)
                        .build());
    }

    private List<String> providerOrder(String preferredProvider) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (preferredProvider != null && !preferredProvider.isBlank()) {
            ordered.add(preferredProvider.trim());
        }
        ordered.addAll(props.getChatOrder());
        return new ArrayList<>(ordered);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String normalizeBaseUrl(String rawBaseUrl) {
        return rawBaseUrl == null || rawBaseUrl.isBlank()
                ? rawBaseUrl
                : rawBaseUrl.replaceAll("/+$", "");
    }

    private Iterable<String> extractToken(ChatCompletionChunk chunk) {
        return chunk.choices().stream()
                .map(choice -> choice.delta().content())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
