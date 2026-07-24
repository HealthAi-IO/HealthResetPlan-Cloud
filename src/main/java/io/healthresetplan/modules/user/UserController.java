package io.healthresetplan.modules.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.user.dto.UpdateProfileRequest;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.entity.UserCredential;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import io.healthresetplan.modules.user.mapper.UserCredentialMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private final UserAccountMapper accountMapper;
    private final UserCredentialMapper credentialMapper;

    public UserController(UserAccountMapper accountMapper, UserCredentialMapper credentialMapper) {
        this.accountMapper = accountMapper;
        this.credentialMapper = credentialMapper;
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        if (account == null) {
            return R.fail(40401, "用户不存在");
        }
        boolean hasPassword = hasPassword(userId);
        return R.ok(Map.of(
                "userId", account.getUserId(),
                "customId", account.getCustomId() != null ? account.getCustomId() : account.getUserId(),
                "phoneTail", account.getPhoneTail() != null ? account.getPhoneTail() : "",
                "nickname", account.getNickname(),
                "avatarUrl", account.getAvatarUrl() != null ? account.getAvatarUrl() : "",
                "hasCloudSync", account.getHasCloudSync() == 1,
                "hasPassword", hasPassword
        ));
    }

    @PutMapping("/me")
    public R<Map<String, Object>> updateMe(@RequestBody UpdateProfileRequest req) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        UserAccount account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        if (account == null) {
            return R.fail(40401, "用户不存在");
        }

        // 账户名称全局唯一，按注册时相同的规则标准化。
        if (StringUtils.hasText(req.getCustomId())) {
            String cid = req.getCustomId().trim().toLowerCase(java.util.Locale.ROOT);
            if (!cid.matches("^[\\p{IsHan}A-Za-z0-9_]{3,20}$")) {
                return R.fail(40003, "账户名称仅支持 3-20 位中文、字母、数字或下划线");
            }
            Long dup = accountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getCustomId, cid)
                    .ne(UserAccount::getUserId, userId));
            if (dup != null && dup > 0) {
                return R.fail(40901, "该账户名称已被占用");
            }
        }

        var wrapper = new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId);
        if (StringUtils.hasText(req.getNickname())) {
            wrapper.set(UserAccount::getNickname, req.getNickname().trim());
        }
        if (StringUtils.hasText(req.getAvatarUrl())) {
            wrapper.set(UserAccount::getAvatarUrl, req.getAvatarUrl().trim());
        }
        if (StringUtils.hasText(req.getCustomId())) {
            wrapper.set(UserAccount::getCustomId, req.getCustomId().trim().toLowerCase(java.util.Locale.ROOT));
        }
        accountMapper.update(null, wrapper);

        // 重新查询获取最新值
        account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));
        boolean hasPassword = hasPassword(userId);

        return R.ok(Map.of(
                "userId", account != null ? account.getUserId() : userId,
                "customId", account != null && account.getCustomId() != null && !account.getCustomId().isEmpty() ? account.getCustomId() : userId,
                "phoneTail", account != null && account.getPhoneTail() != null ? account.getPhoneTail() : "",
                "nickname", account != null ? account.getNickname() : "",
                "avatarUrl", account != null && account.getAvatarUrl() != null ? account.getAvatarUrl() : "",
                "hasCloudSync", account != null && account.getHasCloudSync() == 1,
                "hasPassword", hasPassword
        ));
    }

    private boolean hasPassword(String userId) {
        UserCredential credential = credentialMapper.selectOne(new LambdaQueryWrapper<UserCredential>()
                .eq(UserCredential::getUserId, userId)
                .eq(UserCredential::getCredType, "phone")
                .last("LIMIT 1"));
        return credential != null
                && credential.getSecretHash() != null
                && !credential.getSecretHash().isBlank();
    }
}
