package io.healthresetplan.modules.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiChatService chatService;
    private final AiUsageLimiter usageLimiter;
    private final TaskExecutor taskExecutor;
    private final io.healthresetplan.modules.ai.AiConsentService consentService;

    public AiChatController(AiChatService chatService, TaskExecutor taskExecutor, AiUsageLimiter usageLimiter,
                            io.healthresetplan.modules.ai.AiConsentService consentService) {
        this.chatService = chatService;
        this.taskExecutor = taskExecutor;
        this.usageLimiter = usageLimiter;
        this.consentService = consentService;
    }

    /** 非流式对话（兼容旧客户端） */
    @PostMapping
    public R<AiChatResponse> chat(@Valid @RequestBody AiChatRequest req) {
        String userId = currentUserId();
        consentService.requireActive(userId);
        return R.ok(chatService.chat(userId, req));
    }

    /**
     * 流式对话 — SSE (text/event-stream)。
     *
     * <p>事件格式：</p>
     * <pre>
     * data: {"token":"你好"}
     * data: {"token":"，有什么"}
     * event: done
     * data: [DONE]
     *
     * 出错时：
     * event: error
     * data: {"code":42901,"message":"今日请求已达上限"}
     * </pre>
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> chatStream(@Valid @RequestBody AiChatRequest req) {
        String userId = currentUserId();
        consentService.requireActive(userId);
        // 90 秒超时，与 OneAPI 请求超时对齐
        SseEmitter emitter = new SseEmitter(180_000L);
        AtomicBoolean disconnected = new AtomicBoolean(false);
        emitter.onCompletion(() -> disconnected.set(true));
        emitter.onTimeout(() -> disconnected.set(true));
        emitter.onError(error -> disconnected.set(true));

        // 将阻塞的 LLM 调用放入异步线程，避免占用 Tomcat 线程
        taskExecutor.execute(() -> {
            try {
                chatService.streamChat(userId, req,
                        metadata -> send(emitter, "meta", Map.of(
                                "requestId", metadata.requestId() == null ? "" : metadata.requestId(),
                                "contextSources", metadata.contextSources(),
                                "personalized", metadata.personalized())),
                        token -> {
                            if (disconnected.get()) throw new IllegalStateException("SSE client disconnected");
                            try {
                                String json = MAPPER.writeValueAsString(Map.of("token", token));
                                emitter.send(SseEmitter.event().data(json));
                            } catch (Exception e) {
                                throw new IllegalStateException("SSE client disconnected", e);
                            }
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data(Map.of(
                                        "requestId", req.requestId() == null ? "" : req.requestId())));
                                emitter.complete();
                            } catch (Exception e) {
                                emitter.completeWithError(e);
                            }
                        });

            } catch (io.healthresetplan.common.exception.BusinessException e) {
                try {
                    String json = MAPPER.writeValueAsString(
                            Map.of("code", e.getCode(), "message", e.getMessage()));
                    emitter.send(SseEmitter.event().name("error").data(json));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            } catch (Exception e) {
                try {
                    String json = MAPPER.writeValueAsString(
                            Map.of("code", 50301, "message", "AI 服务异常，请稍后重试"));
                    emitter.send(SseEmitter.event().name("error").data(json));
                    emitter.complete();
                } catch (Exception ex) {
                    emitter.completeWithError(ex);
                }
            }
        });

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-transform")
                .header("X-Accel-Buffering", "no")
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(emitter);
    }

    /** 查询当前用户今日 AI 使用次数 */
    @GetMapping("/daily-usage")
    public R<Map<String, Object>> dailyUsage() {
        String userId = currentUserId();
        long used = chatService.getDailyCount(userId);
        int limit = chatService.getDailyLimit();
        Map<String, Object> result = new java.util.LinkedHashMap<>(usageLimiter.usage(userId));
        result.put("used", used);
        result.put("limit", limit);
        result.put("remaining", Math.max(0, limit - used));
        return R.ok(result);
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    private void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data));
        } catch (Exception e) {
            throw new IllegalStateException("SSE client disconnected", e);
        }
    }
}
