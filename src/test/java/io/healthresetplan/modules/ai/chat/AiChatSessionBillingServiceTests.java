package io.healthresetplan.modules.ai.chat;

import io.healthresetplan.common.persistence.ExpiringStateStore;
import io.healthresetplan.modules.ai.AiUsageLimiter;
import io.healthresetplan.modules.membership.MembershipService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

class AiChatSessionBillingServiceTests {

    private static final String USER_ID = "user-1";
    private static final String SESSION_ID = "12345678-1234-1234-1234-123456789012";

    @Test
    void activeSessionCanContinueWithoutRemainingCredit() {
        ExpiringStateStore stateStore = mock(ExpiringStateStore.class);
        MembershipService membershipService = mock(MembershipService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        when(stateStore.get(anyString())).thenReturn("3");

        AiChatSessionBillingService service = new AiChatSessionBillingService(
                stateStore, membershipService, usageLimiter);

        assertTrue(service.canUse(USER_ID, SESSION_ID));
        verify(membershipService, never()).hasFeature(anyString(), anyString());
    }

    @Test
    void includedReplyDoesNotConsumeAnotherCredit() {
        ExpiringStateStore stateStore = mock(ExpiringStateStore.class);
        MembershipService membershipService = mock(MembershipService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        when(stateStore.get(anyString())).thenReturn("1");

        AiChatSessionBillingService service = new AiChatSessionBillingService(
                stateStore, membershipService, usageLimiter);
        service.completeSuccessfulReply(USER_ID, SESSION_ID);

        verify(stateStore).increment(anyString(), eq(1L), any());
        verify(membershipService, never()).consume(anyString(), anyString());
        verify(usageLimiter, never()).consume(anyString(), any());
    }

    @Test
    void firstSuccessfulReplyConsumesOneCredit() {
        ExpiringStateStore stateStore = mock(ExpiringStateStore.class);
        MembershipService membershipService = mock(MembershipService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        when(membershipService.consume(USER_ID, "ai_chat")).thenReturn(true);

        AiChatSessionBillingService service = new AiChatSessionBillingService(
                stateStore, membershipService, usageLimiter);
        service.completeSuccessfulReply(USER_ID, SESSION_ID);

        verify(usageLimiter).consume(USER_ID, AiUsageLimiter.Type.CHAT);
        verify(membershipService).consume(USER_ID, "ai_chat");
        verify(stateStore).put(anyString(), eq("1"), any());
    }

    @Test
    void duplicateRequestOnlyConsumesOnce() {
        ExpiringStateStore stateStore = mock(ExpiringStateStore.class);
        MembershipService membershipService = mock(MembershipService.class);
        AiUsageLimiter usageLimiter = mock(AiUsageLimiter.class);
        when(stateStore.putIfAbsent(anyString(), eq("completed"), any()))
                .thenReturn(true, false);
        when(membershipService.consume(USER_ID, "ai_chat")).thenReturn(true);
        AiChatSessionBillingService service = new AiChatSessionBillingService(
                stateStore, membershipService, usageLimiter);
        String requestId = "87654321-1234-1234-1234-123456789012";

        service.completeSuccessfulReply(USER_ID, SESSION_ID, requestId);
        service.completeSuccessfulReply(USER_ID, SESSION_ID, requestId);

        verify(membershipService, times(1)).consume(USER_ID, "ai_chat");
        verify(usageLimiter, times(1)).consume(USER_ID, AiUsageLimiter.Type.CHAT);
    }
}
