package io.healthresetplan.modules.ai.oneapi;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.errors.AuthenticationException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import io.healthresetplan.common.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * OneAPI 统一大模型服务。
 *
 * <p>使用 OpenAI 官方 Java SDK，连接本地 OneAPI 实例。
 * 对话模型按优先级降级：qwen-turbo → doubao-lite → deepseek-chat。</p>
 *
 * <h3>异常约定</h3>
 * <ul>
 *   <li>{@code 42901} — 用户每日请求已超限（Redis 计数或 OneAPI 返回 429）</li>
 *   <li>{@code 40101} — API Key 失效（OneAPI 返回 401）</li>
 *   <li>{@code 50301} — 所有模型均不可用</li>
 * </ul>
 */
@Service
public class OneApiService {

    private static final Logger log = LoggerFactory.getLogger(OneApiService.class);

    /** Redis key 前缀：ai:daily:{userId}:{date} */
    private static final String DAILY_KEY_PREFIX = "ai:daily:";

    private final OneApiProperties props;
    private final StringRedisTemplate redis;

    private OpenAIClient client;

    public OneApiService(OneApiProperties props, StringRedisTemplate redis) {
        this.props = props;
        this.redis = redis;
    }

    @PostConstruct
    void initClient() {
        client = OpenAIOkHttpClient.builder()
                .apiKey(props.getApiKey().isBlank() ? "sk-placeholder" : props.getApiKey())
                .baseUrl(props.getBaseUrl() + "/v1")
                .timeout(Duration.ofSeconds(props.getTimeoutSeconds()))
                .build();
        log.info("OneApiService 初始化完成，base={} models={}",
                props.getBaseUrl(), props.getChatModels());
    }

    // ── 非流式对话（返回完整回复文本） ────────────────────────────

    /**
     * @param userId   当前用户 ID，用于计算每日配额
     * @param messages 消息列表（含 system/user/assistant）
     * @return AI 回复文本
     * @throws BusinessException 42901 超限 / 40101 key 失效 / 50301 服务不可用
     */
    public String complete(String userId, List<ChatCompletionMessageParam> messages) {
        checkAndIncrementDailyLimit(userId);

        for (String model : props.getChatModels()) {
            try {
                ChatCompletion resp = client.chat().completions().create(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(2048L)
                                .build());

                String content = resp.choices().get(0).message().content().orElse("");
                log.info("OneAPI complete ok model={} userId={} tokens={}",
                        model, userId, resp.usage().map(u -> u.totalTokens()).orElse(0L));
                return content;

            } catch (RateLimitException e) {
                log.warn("OneAPI 429 model={} userId={}", model, userId);
                // OneAPI 全局限流 → 直接抛出，不再尝试下一个模型
                throw new BusinessException(42901,
                        "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），明日自动重置");
            } catch (AuthenticationException e) {
                log.error("OneAPI 401 key 失效 model={}", model);
                throw new BusinessException(40101, "AI 服务密钥失效，请联系管理员");
            } catch (Exception e) {
                log.warn("OneAPI 模型 {} 不可用，尝试下一个：{}", model, e.getMessage());
                // 继续尝试下一个模型
            }
        }

        throw new BusinessException(50301, "所有 AI 模型均暂时不可用，请稍后重试");
    }

    // ── 流式对话（SSE token by token） ───────────────────────────

    /**
     * 流式输出，token 通过 {@code tokenConsumer} 逐字推送。
     *
     * @param userId        用户 ID
     * @param messages      消息列表
     * @param tokenConsumer 接收每个 token 的回调
     * @param onDone        流结束时调用（正常完成）
     */
    public void stream(String userId,
                       List<ChatCompletionMessageParam> messages,
                       Consumer<String> tokenConsumer,
                       Runnable onDone) {
        checkAndIncrementDailyLimit(userId);

        for (String model : props.getChatModels()) {
            try {
                try (var streamResp = client.chat().completions().createStreaming(
                        ChatCompletionCreateParams.builder()
                                .model(model)
                                .messages(messages)
                                .maxCompletionTokens(2048L)
                                .build())) {

                    streamResp.stream().forEach(chunk ->
                            extractToken(chunk).forEach(tokenConsumer));

                    log.info("OneAPI stream done model={} userId={}", model, userId);
                    onDone.run();
                    return;
                }

            } catch (RateLimitException e) {
                log.warn("OneAPI 流式 429 model={} userId={}", model, userId);
                throw new BusinessException(42901,
                        "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），明日自动重置");
            } catch (AuthenticationException e) {
                log.error("OneAPI 流式 401 key 失效 model={}", model);
                throw new BusinessException(40101, "AI 服务密钥失效，请联系管理员");
            } catch (BusinessException e) {
                throw e; // 已处理的业务异常直接抛出
            } catch (Exception e) {
                log.warn("OneAPI 流式模型 {} 不可用，尝试下一个：{}", model, e.getMessage());
            }
        }

        throw new BusinessException(50301, "所有 AI 模型均暂时不可用，请稍后重试");
    }

    // ── 视觉分析（体检报告 OCR） ─────────────────────────────────

    /**
     * 使用视觉模型分析图片（Base64）。
     *
     * @param userId      用户 ID（计入每日配额）
     * @param imageBase64 图片 Base64（不含 data URI 头）
     * @param mimeType    MIME 类型，如 image/jpeg
     * @param prompt      文字提示词
     * @return 模型返回的文字内容
     */
    public String analyzeImage(String userId, String imageBase64, String mimeType, String prompt) {
        checkAndIncrementDailyLimit(userId);

        String model = props.getVisionModel();
        try {
            // 使用 OpenAI 多模态消息格式
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

            ChatCompletion resp = client.chat().completions().create(
                    ChatCompletionCreateParams.builder()
                            .model(model)
                            .messages(List.of(userMsg))
                            .maxCompletionTokens(4096L)
                            .build());

            return resp.choices().get(0).message().content().orElse("");

        } catch (RateLimitException e) {
            throw new BusinessException(42901, "今日 AI 请求次数已达上限，明日自动重置");
        } catch (AuthenticationException e) {
            throw new BusinessException(40101, "AI 服务密钥失效，请联系管理员");
        } catch (UnexpectedStatusCodeException e) {
            log.error("视觉分析失败 model={} status={}", model, e.statusCode());
            throw new BusinessException(50301, "OCR 识别失败，请检查视觉模型配置");
        }
    }

    // ── 消息构建工具方法 ─────────────────────────────────────────

    /** 快速构建 system 消息 */
    public static ChatCompletionMessageParam systemMsg(String content) {
        return ChatCompletionMessageParam.ofSystem(
                com.openai.models.chat.completions.ChatCompletionSystemMessageParam.builder()
                        .content(content)
                        .build());
    }

    /** 快速构建 user 消息 */
    public static ChatCompletionMessageParam userMsg(String content) {
        return ChatCompletionMessageParam.ofUser(
                com.openai.models.chat.completions.ChatCompletionUserMessageParam.builder()
                        .content(content)
                        .build());
    }

    /** 快速构建 assistant 消息 */
    public static ChatCompletionMessageParam assistantMsg(String content) {
        return ChatCompletionMessageParam.ofAssistant(
                com.openai.models.chat.completions.ChatCompletionAssistantMessageParam.builder()
                        .content(content)
                        .build());
    }

    // ── 每日配额（Redis） ────────────────────────────────────────

    private void checkAndIncrementDailyLimit(String userId) {
        if (userId == null || userId.isBlank()) return;

        String key = DAILY_KEY_PREFIX + userId + ":" + LocalDate.now();
        String val = redis.opsForValue().get(key);
        long count = val == null ? 0 : Long.parseLong(val);

        if (count >= props.getDailyLimit()) {
            throw new BusinessException(42901,
                    "今日 AI 请求次数已达上限（" + props.getDailyLimit() + " 次），明日自动重置");
        }

        redis.opsForValue().increment(key);
        redis.expire(key, 25, TimeUnit.HOURS); // 稍微超过 24h，防止时区边界丢失
    }

    /** 查询当前用户今日已使用次数 */
    public long getDailyCount(String userId) {
        String key = DAILY_KEY_PREFIX + userId + ":" + LocalDate.now();
        String val = redis.opsForValue().get(key);
        return val == null ? 0 : Long.parseLong(val);
    }

    // ── 工具 ─────────────────────────────────────────────────────

    private Iterable<String> extractToken(ChatCompletionChunk chunk) {
        return chunk.choices().stream()
                .map(c -> c.delta().content())
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .toList();
    }
}
