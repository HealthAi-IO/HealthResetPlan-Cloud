package io.healthresetplan.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AccountCancellationPurgeJob {

    private static final Logger log =
            LoggerFactory.getLogger(AccountCancellationPurgeJob.class);

    private final UserAccountMapper accountMapper;
    private final AuthService authService;

    public AccountCancellationPurgeJob(
            UserAccountMapper accountMapper,
            AuthService authService) {
        this.accountMapper = accountMapper;
        this.authService = authService;
    }

    @Scheduled(cron = "${app.account-cancellation-purge-cron:0 15 3 * * *}")
    public void purgeExpiredAccounts() {
        LocalDateTime cutoff =
                LocalDateTime.now().minusDays(AuthService.CANCELLATION_RETENTION_DAYS);
        List<UserAccount> accounts = accountMapper.selectList(
                new LambdaQueryWrapper<UserAccount>()
                        .eq(UserAccount::getStatus, -1)
                        .le(UserAccount::getCancellationRequestedAt, cutoff));
        for (UserAccount account : accounts) {
            try {
                authService.purgeExpiredCancellation(account.getUserId(), cutoff);
            } catch (RuntimeException error) {
                log.error("清理到期注销账号失败 userId={}", account.getUserId(), error);
            }
        }
    }
}
