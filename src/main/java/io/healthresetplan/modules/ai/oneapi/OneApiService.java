package io.healthresetplan.modules.ai.oneapi;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
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
import io.healthresetplan.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final OneApiProperties props;
    private final Map<String, OpenAIClient> clients = new HashMap<>();
    private final Map<String, AtomicLong> dailyCounters = new ConcurrentHashMap<>();
    private volatile LocalDate counterDate = LocalDate.now();

    public OneApiService(OneApiProperties props) {
        this.props = props;
    }

    @PostConstruct
    void initClients() {
        props.getProviders().forEach((name, cfg) -> {
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
            clients.put(name, client);
            log.info("AI provider [{}] initialized, model={}", name, cfg.getModel());
        });

        if (clients.isEmpty()) {
            log.warn("No AI provider is configured. AI features will be unavailable.");
        }
    }

    public String complete(String userId, List<ChatCompletionMessageParam> messages) {
        return complete(userId, messages, null);
    }

    public String complete(String userId, List<ChatCompletionMessageParam> messages, String preferredProvider) {
        checkAndIncrementDailyLimit(userId);

        for (String providerName : providerOrder(preferredProvider)) {
            OpenAIClient client = clients.get(providerName);
            if (client == null) continue;

            String model = props.getProviders().get(providerName).getModel();
            try {
                var response = client.chat().completions().create(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(2048L)
                                .build());

                String content = response.choices().get(0).message().content().orElse("");
                log.info("AI complete ok provider={} model={} userId={}", providerName, model, userId);
                return content;
            } catch (RateLimitException e) {
                log.warn("AI rate limited provider={}", providerName);
                throw new BusinessException(42901,
                        "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），请明日再试");
            } catch (UnauthorizedException e) {
                log.error("AI key unauthorized provider={}", providerName);
            } catch (Exception e) {
                log.warn("AI provider={} unavailable, trying next: {}", providerName, e.getMessage());
            }
        }

        throw new BusinessException(50301, "所有 AI 厂商暂时不可用，请稍后重试");
    }

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
        checkAndIncrementDailyLimit(userId);

        for (String providerName : providerOrder(preferredProvider)) {
            OpenAIClient client = clients.get(providerName);
            if (client == null) continue;

            String model = props.getProviders().get(providerName).getModel();
            try {
                try (var streamResponse = client.chat().completions().createStreaming(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(2048L)
                                .build())) {
                    streamResponse.stream().forEach(chunk -> extractToken(chunk).forEach(tokenConsumer));
                }

                log.info("AI stream done provider={} userId={}", providerName, userId);
                onDone.run();
                return;
            } catch (RateLimitException e) {
                log.warn("AI stream rate limited provider={}", providerName);
                throw new BusinessException(42901,
                        "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），请明日再试");
            } catch (UnauthorizedException e) {
                log.error("AI stream key unauthorized provider={}", providerName);
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("AI stream provider={} unavailable, trying next: {}", providerName, e.getMessage());
            }
        }

        throw new BusinessException(50301, "所有 AI 厂商暂时不可用，请稍后重试");
    }

    public String analyzeImage(String userId, String imageBase64, String mimeType, String prompt) {
        if (userId != null) checkAndIncrementDailyLimit(userId);

        String providerName = props.getVisionProvider();
        OpenAIClient client = clients.get(providerName);
        if (client == null) {
            throw new BusinessException(50301, "视觉模型厂商 [" + providerName + "] 未配置");
        }

        String model = props.getProviders().get(providerName).getModel();
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

            var response = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .messages(List.of(userMessage))
                            .maxCompletionTokens(4096L)
                            .build());

            return response.choices().get(0).message().content().orElse("");
        } catch (RateLimitException e) {
            throw new BusinessException(42901, "今日 AI 请求次数已达上限，请明日再试");
        } catch (UnauthorizedException e) {
            throw new BusinessException(40101, "视觉模型 Key 无效：" + providerName);
        } catch (UnexpectedStatusCodeException e) {
            log.error("OCR failed provider={} status={}", providerName, e.statusCode());
            throw new BusinessException(50301, "OCR 识别失败，请检查视觉模型配置");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("OCR failed provider={}", providerName, e);
            throw new BusinessException(50301, "OCR 识别失败，请稍后重试");
        }
    }

    public String visionProviderLabel() {
        String providerName = props.getVisionProvider();
        var provider = props.getProviders().get(providerName);
        String model = provider != null ? provider.getModel() : "";
        return model == null || model.isBlank() ? providerName : providerName + " / " + model;
    }

    private void ensureVisionCapableModel(String providerName, String model) {
        if (!"qwen".equalsIgnoreCase(providerName)) {
            return;
        }
        String normalized = model == null ? "" : model.toLowerCase();
        if (!normalized.contains("vl")) {
            throw new BusinessException(
                    50301,
                    "报告图片识别需要视觉模型，请将 AI_CHAT_QWEN3_VL_PLUS_MODEL 配置为 qwen3-vl-plus 等视觉模型");
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

    private void checkAndIncrementDailyLimit(String userId) {
        if (userId == null || userId.isBlank()) return;

        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) {
            dailyCounters.clear();
            counterDate = today;
        }

        AtomicLong counter = dailyCounters.computeIfAbsent(userId, ignored -> new AtomicLong(0));
        if (counter.get() >= props.getDailyLimit()) {
            throw new BusinessException(42901,
                    "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），请明日再试");
        }
        counter.incrementAndGet();
    }

    public long getDailyCount(String userId) {
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) return 0;
        AtomicLong counter = dailyCounters.get(userId);
        return counter == null ? 0 : counter.get();
    }

    private List<String> providerOrder(String preferredProvider) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (preferredProvider != null && !preferredProvider.isBlank()) {
            ordered.add(preferredProvider.trim());
        }
        ordered.addAll(props.getChatOrder());
        return new ArrayList<>(ordered);
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
