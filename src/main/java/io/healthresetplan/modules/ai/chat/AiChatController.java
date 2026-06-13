package io.healthresetplan.modules.ai.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.result.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/chat")
public class AiChatController {

    private static final Logger log = LoggerFactory.getLogger(AiChatController.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AiChatService chatService;
    private final TaskExecutor taskExecutor;

    public AiChatController(AiChatService chatService, TaskExecutor taskExecutor) {
        this.chatService = chatService;
        this.taskExecutor = taskExecutor;
    }

    /** 非流式对话（兼容旧客户端） */
    @PostMapping
    public R<AiChatResponse> chat(@RequestBody AiChatRequest req) {
        String userId = currentUserId();
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
    public SseEmitter chatStream(@RequestBody AiChatRequest req) {
        String userId = currentUserId();
        // 90 秒超时，与 OneAPI 请求超时对齐
        SseEmitter emitter = new SseEmitter(180_000L);

        // 将阻塞的 LLM 调用放入异步线程，避免占用 Tomcat 线程
        taskExecutor.execute(() -> {
            try {
                chatService.streamChat(userId, req,
                        token -> {
                            try {
                                String json = MAPPER.writeValueAsString(Map.of("token", token));
                                emitter.send(SseEmitter.event().data(json));
                            } catch (Exception e) {
                                log.warn("SSE send 失败：{}", e.getMessage());
                            }
                        },
                        () -> {
                            try {
                                emitter.send(SseEmitter.event().name("done").data("[DONE]"));
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

        return emitter;
    }

    /** 查询当前用户今日 AI 使用次数 */
    @GetMapping("/daily-usage")
    public R<Map<String, Object>> dailyUsage() {
        String userId = currentUserId();
        long used = chatService.getDailyCount(userId);
        int limit = chatService.getDailyLimit();
        return R.ok(Map.of("used", used, "limit", limit, "remaining", Math.max(0, limit - used)));
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
