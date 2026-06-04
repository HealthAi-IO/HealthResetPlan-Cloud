package io.healthresetplan.modules.ai.oneapi;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.UnauthorizedException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import io.healthresetplan.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * AI 多厂商直连服务。
 *
 * <p>使用 OpenAI 官方 Java SDK，各厂商直接连接（无需 OneAPI 代理）。
 * 对话按 {@code app.ai.chat-order} 顺序降级：qwen → doubao → deepseek。</p>
 *
 * <h3>异常约定</h3>
 * <ul>
 *   <li>42901 — 每日请求超限</li>
 *   <li>40101 — API Key 失效</li>
 *   <li>50301 — 所有厂商均不可用</li>
 * </ul>
 */
@Service
public class OneApiService {

    private static final Logger log = LoggerFactory.getLogger(OneApiService.class);

    private final OneApiProperties props;

    /** 每个厂商对应一个 OpenAIClient 实例 */
    private final Map<String, OpenAIClient> clients = new HashMap<>();

    /** 内存每日计数：key = userId:date，value = 请求次数 */
    private final Map<String, AtomicLong> dailyCounters = new ConcurrentHashMap<>();
    private volatile LocalDate counterDate = LocalDate.now();

    public OneApiService(OneApiProperties props) {
        this.props = props;
    }

    @PostConstruct
    void initClients() {
        props.getProviders().forEach((name, cfg) -> {
            if (!cfg.isConfigured()) {
                log.warn("AI 厂商 [{}] 未配置 baseUrl 或 apiKey，跳过", name);
                return;
            }
            // OpenAI SDK 的 baseUrl 需要包含 /v1
            String baseUrl = cfg.getBaseUrl().endsWith("/v1")
                    ? cfg.getBaseUrl()
                    : cfg.getBaseUrl() + "/v1";
            OpenAIClient client = OpenAIOkHttpClient.builder()
                    .apiKey(cfg.getApiKey())
                    .baseUrl(baseUrl)
                    .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                    .build();
            clients.put(name, client);
            log.info("AI 厂商 [{}] 初始化完成，model={}", name, cfg.getModel());
        });

        if (clients.isEmpty()) {
            log.warn("没有配置任何 AI 厂商，AI 功能将不可用。请在 application-local.yml 中填写 Key。");
        }
    }

    // ── 非流式对话 ────────────────────────────────────────────

    public String complete(String userId, List<ChatCompletionMessageParam> messages) {
        checkAndIncrementDailyLimit(userId);

        for (String providerName : props.getChatOrder()) {
            OpenAIClient client = clients.get(providerName);
            if (client == null) continue;

            String model = props.getProviders().get(providerName).getModel();
            try {
                var resp = client.chat().completions().create(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(2048L)
                                .build());

                String content = resp.choices().get(0).message().content().orElse("");
                log.info("AI complete ok provider={} model={} userId={}", providerName, model, userId);
                return content;

            } catch (RateLimitException e) {
                log.warn("AI 429 provider={}", providerName);
                throw new BusinessException(42901,
                        "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），明日自动重置");
            } catch (UnauthorizedException e) {
                log.error("AI 401 Key 失效 provider={}", providerName);
                throw new BusinessException(40101, "AI 服务密钥失效（" + providerName + "），请联系管理员");
            } catch (Exception e) {
                log.warn("AI provider={} 不可用，尝试下一个：{}", providerName, e.getMessage());
            }
        }

        throw new BusinessException(50301, "所有 AI 厂商均暂时不可用，请稍后重试");
    }

    // ── 流式对话（SSE） ───────────────────────────────────────

    public void stream(String userId,
                       List<ChatCompletionMessageParam> messages,
                       Consumer<String> tokenConsumer,
                       Runnable onDone) {
        checkAndIncrementDailyLimit(userId);

        for (String providerName : props.getChatOrder()) {
            OpenAIClient client = clients.get(providerName);
            if (client == null) continue;

            String model = props.getProviders().get(providerName).getModel();
            try {
                try (var streamResp = client.chat().completions().createStreaming(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(2048L)
                                .build())) {

                    streamResp.stream().forEach(chunk ->
                            extractToken(chunk).forEach(tokenConsumer));

                    log.info("AI stream done provider={} userId={}", providerName, userId);
                    onDone.run();
                    return;
                }

            } catch (RateLimitException e) {
                log.warn("AI 流式 429 provider={}", providerName);
                throw new BusinessException(42901,
                        "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），明日自动重置");
            } catch (UnauthorizedException e) {
                log.error("AI 流式 401 Key 失效 provider={}", providerName);
                throw new BusinessException(40101, "AI 服务密钥失效（" + providerName + "），请联系管理员");
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                log.warn("AI 流式 provider={} 不可用，尝试下一个：{}", providerName, e.getMessage());
            }
        }

        throw new BusinessException(50301, "所有 AI 厂商均暂时不可用，请稍后重试");
    }

    // ── 视觉分析（OCR） ───────────────────────────────────────

    public String analyzeImage(String userId, String imageBase64, String mimeType, String prompt) {
        if (userId != null) checkAndIncrementDailyLimit(userId);

        String providerName = props.getVisionProvider();
        OpenAIClient client = clients.get(providerName);
        if (client == null) {
            throw new BusinessException(50301, "视觉模型厂商 [" + providerName + "] 未配置");
        }
        String model = props.getProviders().get(providerName).getModel();

        try {
            var imageContent = com.openai.models.chat.completions.ChatCompletionContentPart
                    .ofImageUrl(com.openai.models.chat.completions.ChatCompletionContentPartImage.builder()
                            .imageUrl(com.openai.models.chat.completions.ChatCompletionContentPartImage.ImageUrl.builder()
                                    .url("data:" + mimeType + ";base64," + imageBase64)
                                    .build())
                            .build());
            var textContent = com.openai.models.chat.completions.ChatCompletionContentPart
                    .ofText(com.openai.models.chat.completions.ChatCompletionContentPartText.builder()
                            .text(prompt)
                            .build());
            var userMsg = ChatCompletionMessageParam.ofUser(
                    com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                            .content(com.openai.models.chat.completions.ChatCompletionUserMessageParam.Content
                                    .ofArrayOfContentParts(List.of(textContent, imageContent)))
                            .build());

            var resp = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .messages(List.of(userMsg))
                            .maxCompletionTokens(4096L)
                            .build());

            return resp.choices().get(0).message().content().orElse("");

        } catch (RateLimitException e) {
            throw new BusinessException(42901, "今日 AI 请求次数已达上限，明日重置");
        } catch (UnauthorizedException e) {
            throw new BusinessException(40101, "视觉模型 Key 失效（" + providerName + "）");
        } catch (UnexpectedStatusCodeException e) {
            log.error("OCR 失败 provider={} status={}", providerName, e.statusCode());
            throw new BusinessException(50301, "OCR 识别失败，请检查视觉模型配置");
        }
    }

    // ── 消息构建工具方法 ──────────────────────────────────────

    public static ChatCompletionMessageParam systemMsg(String content) {
        return ChatCompletionMessageParam.ofSystem(
                com.openai.models.chat.completions.ChatCompletionSystemMessageParam.builder()
                        .content(content).build());
    }

    public static ChatCompletionMessageParam userMsg(String content) {
        return ChatCompletionMessageParam.ofUser(
                com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                        .content(content).build());
    }

    public static ChatCompletionMessageParam assistantMsg(String content) {
        return ChatCompletionMessageParam.ofAssistant(
                com.openai.models.chat.completions.ChatCompletionAssistantMessageParam.builder()
                        .content(content).build());
    }

    // ── 每日配额（内存计数，重启清零） ────────────────────────

    private void checkAndIncrementDailyLimit(String userId) {
        if (userId == null || userId.isBlank()) return;

        // 跨天时清空所有计数
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) {
            dailyCounters.clear();
            counterDate = today;
        }

        AtomicLong counter = dailyCounters.computeIfAbsent(userId, k -> new AtomicLong(0));
        if (counter.get() >= props.getDailyLimit()) {
            throw new BusinessException(42901,
                    "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），明日自动重置");
        }
        counter.incrementAndGet();
    }

    public long getDailyCount(String userId) {
        LocalDate today = LocalDate.now();
        if (!today.equals(counterDate)) return 0;
        AtomicLong counter = dailyCounters.get(userId);
        return counter == null ? 0 : counter.get();
    }

    // ── 工具 ─────────────────────────────────────────────────

    private Iterable<String> extractToken(ChatCompletionChunk chunk) {
        return chunk.choices().stream()
                .map(c -> c.delta().content())
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
    }
}
