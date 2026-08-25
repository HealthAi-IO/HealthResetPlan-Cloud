package io.healthresetplan.modules.ai.chat;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.persistence.ExpiringStateStore;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.membership.MembershipService;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class AiChatSessionBillingService {

    private static final Duration SESSION_DURATION = Duration.ofMinutes(30);
    private static final Duration REQUEST_DURATION = Duration.ofHours(2);
    private static final int INCLUDED_REPLIES = 10;

    private final ExpiringStateStore stateStore;
    private final MembershipService membershipService;
    private final AiUsageLimiter usageLimiter;

    public AiChatSessionBillingService(
            ExpiringStateStore stateStore,
            MembershipService membershipService,
            AiUsageLimiter usageLimiter) {
        this.stateStore = stateStore;
        this.membershipService = membershipService;
        this.usageLimiter = usageLimiter;
    }

    public boolean canUse(String userId, String sessionId) {
        Long replies = activeReplies(userId, sessionId);
        return (replies != null && replies < INCLUDED_REPLIES)
                || membershipService.hasFeature(userId, "ai_chat");
    }

    public void completeSuccessfulReply(String userId, String sessionId) {
        completeSuccessfulReply(userId, sessionId, null);
    }

    public void completeSuccessfulReply(String userId, String sessionId, String requestId) {
        String requestKey = validRequestId(requestId)
                ? "hrp:ai:chat-request:" + userId + ":" + requestId
                : null;
        if (requestKey != null && !stateStore.putIfAbsent(requestKey, "completed", REQUEST_DURATION)) {
            return;
        }
        try {
            completeSuccessfulReplyOnce(userId, sessionId);
        } catch (RuntimeException ex) {
            if (requestKey != null) stateStore.delete(requestKey);
            throw ex;
        }
    }

    private void completeSuccessfulReplyOnce(String userId, String sessionId) {
        Long replies = activeReplies(userId, sessionId);
        if (replies != null && replies < INCLUDED_REPLIES) {
            stateStore.increment(key(userId, sessionId), 1, SESSION_DURATION);
            return;
        }

        usageLimiter.consume(userId, AiUsageLimiter.Type.CHAT);
        if (!membershipService.consume(userId, "ai_chat")) {
            usageLimiter.release(userId, AiUsageLimiter.Type.CHAT);
            throw new BusinessException(42903, "AI 健康权益已用完，请充值后继续使用");
        }
        if (validSessionId(sessionId)) {
            stateStore.put(key(userId, sessionId), "1", SESSION_DURATION);
        }
    }

    private Long activeReplies(String userId, String sessionId) {
        if (!validSessionId(sessionId)) return null;
        String value = stateStore.get(key(userId, sessionId));
        return value == null ? null : Long.parseLong(value);
    }

    private boolean validSessionId(String sessionId) {
        return sessionId != null && sessionId.matches("[0-9a-fA-F-]{36}");
    }

    private boolean validRequestId(String requestId) {
        return requestId != null && requestId.matches("[0-9a-fA-F-]{36}");
    }

    private String key(String userId, String sessionId) {
        return "hrp:ai:chat-session:" + userId + ":" + sessionId;
    }
}
