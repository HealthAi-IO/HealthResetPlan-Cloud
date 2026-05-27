package io.healthresetplan.modules.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountMapper accountMapper;

    public UserController(UserAccountMapper accountMapper) {
        this.accountMapper = accountMapper;
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        if (account == null) {
            return R.fail(40401, "用户不存在");
        }
        return R.ok(Map.of(
                "userId", account.getUserId(),
                "nickname", account.getNickname(),
                "hasCloudSync", account.getHasCloudSync() == 1
        ));
    }
}
