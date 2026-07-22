package io.healthresetplan.modules.auth;

import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.config.JwtProperties;
import io.healthresetplan.modules.auth.dto.PhoneRegisterRequest;
import io.healthresetplan.modules.sms.SmsVerificationService;
import io.healthresetplan.modules.sync.KeyRetentionService;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import io.healthresetplan.modules.user.mapper.UserCredentialMapper;
import io.healthresetplan.modules.user.mapper.UserKeyMetaMapper;
import io.healthresetplan.modules.user.mapper.UserSessionMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthServiceRegistrationTests {

    @Test
    void consecutiveRegistrationsUseUniqueCustomIds() {
        UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        UserCredentialMapper credentialMapper = mock(UserCredentialMapper.class);
        UserSessionMapper sessionMapper = mock(UserSessionMapper.class);
        JwtUtils jwtUtils = mock(JwtUtils.class);
        JwtProperties jwtProperties = mock(JwtProperties.class);
        PhoneRegistrationTicketService ticketService = mock(PhoneRegistrationTicketService.class);
        List<UserAccount> accounts = new ArrayList<>();

        when(accountMapper.insert(any(UserAccount.class))).thenAnswer(invocation -> {
            accounts.add(invocation.getArgument(0));
            return 1;
        });
        when(accountMapper.selectOne(any())).thenAnswer(invocation -> accounts.get(accounts.size() - 1));
        when(jwtUtils.generateAccessToken(any())).thenReturn("access");
        when(jwtUtils.generateRefreshToken(any())).thenReturn("refresh");
        when(jwtUtils.getRefreshExpiry("refresh")).thenReturn(System.currentTimeMillis() + 60_000);
        when(jwtProperties.getAccessTtlMinutes()).thenReturn(15L);

        AuthService service = new AuthService(
                accountMapper,
                credentialMapper,
                sessionMapper,
                mock(UserKeyMetaMapper.class),
                mock(KeyRetentionService.class),
                jwtUtils,
                jwtProperties,
                mock(SmsVerificationService.class),
                ticketService,
                mock(JdbcTemplate.class)
        );
        HttpServletRequest request = mock(HttpServletRequest.class);

        service.registerPhone(registration("13800138000", "ticket-1"), request);
        service.registerPhone(registration("13900139000", "ticket-2"), request);

        assertEquals(accounts.get(0).getUserId(), accounts.get(0).getCustomId());
        assertEquals(accounts.get(1).getUserId(), accounts.get(1).getCustomId());
        assertNotEquals(accounts.get(0).getCustomId(), accounts.get(1).getCustomId());
    }

    private static PhoneRegisterRequest registration(String phone, String ticket) {
        PhoneRegisterRequest request = new PhoneRegisterRequest();
        request.setPhone(phone);
        request.setRegistrationTicket(ticket);
        request.setNickname("测试用户");
        request.setAgreedToTerms(true);
        request.setAgreementVersion("2026-07-17");
        return request;
    }
}
