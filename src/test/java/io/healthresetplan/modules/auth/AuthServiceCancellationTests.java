package io.healthresetplan.modules.auth;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.util.HashUtils;
import io.healthresetplan.common.util.JwtUtils;
import io.healthresetplan.config.JwtProperties;
import io.healthresetplan.modules.auth.dto.CancelAccountRequest;
import io.healthresetplan.modules.captcha.CaptchaService;
import io.healthresetplan.modules.data.UserDataService;
import io.healthresetplan.modules.files.FileStorageService;
import io.healthresetplan.modules.sms.SmsVerificationService;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.entity.UserCredential;
import io.healthresetplan.modules.user.entity.UserSession;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import io.healthresetplan.modules.user.mapper.UserCredentialMapper;
import io.healthresetplan.modules.user.mapper.UserSessionMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceCancellationTests {

    @Test
    void cancellationRetainsBusinessDataDuringRecoveryPeriod() {
        Fixtures fixtures = new Fixtures(activeAccount());
        CancelAccountRequest request = new CancelAccountRequest();
        request.setPhone("13800138000");
        request.setCode("123456");

        fixtures.service.cancelAccount("user-1", request);

        verify(fixtures.accountMapper).update(any(), any());
        verify(fixtures.sessionMapper).delete(any());
        verify(fixtures.userDataService, never()).delete(any());
    }

    @Test
    void expiredCancellationIsRejectedAndCanBePurged() {
        UserAccount account = cancelledAccount(
                LocalDateTime.now().minusDays(AuthService.CANCELLATION_RETENTION_DAYS + 1));
        Fixtures fixtures = new Fixtures(account);

        BusinessException error = assertThrows(
                BusinessException.class,
                () -> fixtures.service.sendAccountRecoveryCode("13800138000"));
        assertEquals(40303, error.getCode());

        fixtures.service.purgeExpiredCancellation(
                "user-1",
                LocalDateTime.now().minusDays(AuthService.CANCELLATION_RETENTION_DAYS));
        verify(fixtures.userDataService).delete("user-1");
        verify(fixtures.jdbc).update(
                "DELETE FROM user_account WHERE user_id = ?",
                "user-1");
        String executedSql = mockingDetails(fixtures.jdbc).getInvocations().stream()
                .map(invocation -> invocation.getArgument(0, String.class))
                .reduce("", (left, right) -> left + "\n" + right);
        assertFalse(executedSql.contains("device_binding"));
        assertFalse(executedSql.contains("payment_order"));
        assertFalse(executedSql.contains("user_subscription"));
    }

    @Test
    void recoveryWithinThirtyDaysReactivatesAccountAndCreatesSession() {
        UserAccount cancelled =
                cancelledAccount(LocalDateTime.now().minusDays(2));
        UserAccount active = activeAccount();
        Fixtures fixtures = new Fixtures(cancelled);
        when(fixtures.accountMapper.selectOne(any()))
                .thenReturn(cancelled, active);
        when(fixtures.jwtUtils.generateAccessToken("user-1"))
                .thenReturn("access");
        when(fixtures.jwtUtils.generateRefreshToken("user-1"))
                .thenReturn("refresh");
        when(fixtures.jwtUtils.getRefreshExpiry("refresh"))
                .thenReturn(System.currentTimeMillis() + 60_000);
        when(fixtures.jwtProperties.getAccessTtlMinutes()).thenReturn(15L);

        fixtures.service.reactivateAccount(
                "13800138000",
                "123456",
                mock(HttpServletRequest.class));

        verify(fixtures.accountMapper).update(any(), any());
        verify(fixtures.sessionMapper).insert(any(UserSession.class));
    }

    private static UserAccount activeAccount() {
        UserAccount account = new UserAccount();
        account.setUserId("user-1");
        account.setStatus(1);
        return account;
    }

    private static UserAccount cancelledAccount(LocalDateTime requestedAt) {
        UserAccount account = activeAccount();
        account.setStatus(-1);
        account.setCancellationRequestedAt(requestedAt);
        return account;
    }

    private static final class Fixtures {
        private final UserAccountMapper accountMapper = mock(UserAccountMapper.class);
        private final UserCredentialMapper credentialMapper = mock(UserCredentialMapper.class);
        private final UserSessionMapper sessionMapper = mock(UserSessionMapper.class);
        private final UserDataService userDataService = mock(UserDataService.class);
        private final JwtUtils jwtUtils = mock(JwtUtils.class);
        private final JwtProperties jwtProperties = mock(JwtProperties.class);
        private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
        private final AuthService service;

        private Fixtures(UserAccount account) {
            MapperBuilderAssistant assistant =
                    new MapperBuilderAssistant(new MybatisConfiguration(), "");
            TableInfoHelper.initTableInfo(assistant, UserAccount.class);
            TableInfoHelper.initTableInfo(assistant, UserCredential.class);
            TableInfoHelper.initTableInfo(assistant, UserSession.class);

            UserCredential credential = new UserCredential();
            credential.setUserId("user-1");
            credential.setCredType("phone");
            credential.setIdentifierHash(HashUtils.sha256Hex("13800138000"));
            when(credentialMapper.selectOne(any())).thenReturn(credential);
            when(accountMapper.selectOne(any())).thenReturn(account);

            service = new AuthService(
                    accountMapper,
                    credentialMapper,
                    sessionMapper,
                    userDataService,
                    mock(FileStorageService.class),
                    jwtUtils,
                    jwtProperties,
                    mock(SmsVerificationService.class),
                    mock(PhoneRegistrationTicketService.class),
                    mock(PasswordLoginThrottleService.class),
                    mock(CaptchaService.class),
                    jdbc);
        }
    }
}
