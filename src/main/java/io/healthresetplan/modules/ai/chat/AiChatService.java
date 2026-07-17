package io.healthresetplan.modules.ai.chat;

import com.openai.models.chat.completions.ChatCompletionMessageParam;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.ai.MedicalRiskGuard;
import io.healthresetplan.modules.ai.oneapi.OneApiProperties;
import io.healthresetplan.modules.ai.oneapi.OneApiService;
import io.healthresetplan.modules.membership.MembershipService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@Service
public class AiChatService {

    private static final String SYSTEM_PROMPT =
            "你是「健康重启计划」专属健康顾问 AI，负责帮助用户进行日常健康管理。\n\n"
          + "职责：\n"
          + "1. 解答饮食、运动、睡眠、体重管理等健康问题\n"
          + "2. 基于用户健康数据给出个性化建议\n"
          + "3. 积极鼓励用户坚持健康习惯\n\n"
          + "注意：不作诊断；仅在异常指标、症状加重、用药调整或其他风险情形下明确建议就医。"
          + "回答简洁（200字以内），使用简体中文，语气亲切。";

    private final OneApiService oneApiService;
    private final OneApiProperties props;
    private final MembershipService membershipService;
    private final AiUsageLimiter usageLimiter;

    public AiChatService(OneApiService oneApiService,
                         OneApiProperties props,
                         MembershipService membershipService,
                         AiUsageLimiter usageLimiter) {
        this.oneApiService = oneApiService;
        this.props = props;
        this.membershipService = membershipService;
        this.usageLimiter = usageLimiter;
    }

    // ── 非流式 ──────────────────────────────────────────────────

    public AiChatResponse chat(String userId, AiChatRequest req) {
        checkMembership(userId);
        String safetyReply = MedicalRiskGuard.safetyReply(requestText(req));
        if (safetyReply != null) {
            return new AiChatResponse("safety", safetyReply, 0, 0);
        }
        usageLimiter.consume(userId, AiUsageLimiter.Type.CHAT);
        try {
            List<ChatCompletionMessageParam> msgs = buildMessages(req);
            String content = oneApiService.complete(userId, msgs, req.provider());
            return new AiChatResponse(
                    req.provider() == null || req.provider().isBlank() ? "oneapi" : req.provider(),
                    content,
                    0,
                    0
            );
        } catch (RuntimeException e) {
            usageLimiter.release(userId, AiUsageLimiter.Type.CHAT);
            throw e;
        }
    }

    // ── 流式 ────────────────────────────────────────────────────

    public void streamChat(String userId,
                           AiChatRequest req,
                           Consumer<String> tokenConsumer,
                           Runnable onDone) {
        checkMembership(userId);
        String safetyReply = MedicalRiskGuard.safetyReply(requestText(req));
        if (safetyReply != null) {
            tokenConsumer.accept(safetyReply);
            onDone.run();
            return;
        }
        usageLimiter.consume(userId, AiUsageLimiter.Type.CHAT);
        try {
            List<ChatCompletionMessageParam> msgs = buildMessages(req);
            oneApiService.stream(userId, msgs, req.provider(), tokenConsumer, onDone);
        } catch (RuntimeException e) {
            usageLimiter.release(userId, AiUsageLimiter.Type.CHAT);
            throw e;
        }
    }

    // ── 配额查询 ─────────────────────────────────────────────────

    public long getDailyCount(String userId) {
        return usageLimiter.used(userId, AiUsageLimiter.Type.CHAT);
    }

    public int getDailyLimit() {
        return usageLimiter.limit(AiUsageLimiter.Type.CHAT);
    }

    // ── 内部工具 ─────────────────────────────────────────────────

    private void checkMembership(String userId) {
        if (!membershipService.hasFeature(userId, "cloud_sync")) {
            throw new BusinessException(40301, "AI 对话是会员专属功能，请先开通会员");
        }
    }

    private String requestText(AiChatRequest req) {
        if (req.messages() == null) return req.profileSummary();
        StringBuilder text = new StringBuilder(req.profileSummary() == null ? "" : req.profileSummary());
        for (AiChatRequest.ChatMessage message : req.messages()) {
            if (message.content() != null) text.append(' ').append(message.content());
        }
        return text.toString();
    }

    private List<ChatCompletionMessageParam> buildMessages(AiChatRequest req) {
        String sysContent = SYSTEM_PROMPT;
        if (req.profileSummary() != null && !req.profileSummary().isBlank()) {
            sysContent += "\n\n【用户档案】" + req.profileSummary();
        }

        List<ChatCompletionMessageParam> msgs = new ArrayList<>();
        msgs.add(OneApiService.systemMsg(sysContent));

        if (req.messages() != null) {
            // 保留最近 20 条，防止 token 超限
            var history = req.messages();
            if (history.size() > 20) {
                history = history.subList(history.size() - 20, history.size());
            }
            for (AiChatRequest.ChatMessage m : history) {
                msgs.add(switch (m.role()) {
                    case "assistant" -> OneApiService.assistantMsg(m.content());
                    default -> OneApiService.userMsg(m.content());
                });
            }
        }
        return msgs;
    }
}
