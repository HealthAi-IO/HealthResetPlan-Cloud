package io.healthresetplan.modules.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.user.dto.UpdateProfileRequest;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

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
                "customId", account.getCustomId() != null ? account.getCustomId() : account.getUserId(),
                "phoneTail", account.getPhoneTail() != null ? account.getPhoneTail() : "",
                "nickname", account.getNickname(),
                "avatarUrl", account.getAvatarUrl() != null ? account.getAvatarUrl() : "",
                "hasCloudSync", account.getHasCloudSync() == 1
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

        // customId 查重
        if (StringUtils.hasText(req.getCustomId())) {
            String cid = req.getCustomId().trim();
            Long dup = accountMapper.selectCount(new LambdaQueryWrapper<UserAccount>()
                    .eq(UserAccount::getCustomId, cid)
                    .ne(UserAccount::getUserId, userId));
            if (dup != null && dup > 0) {
                return R.fail(40901, "该展示编号已被占用");
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
            wrapper.set(UserAccount::getCustomId, req.getCustomId().trim());
        }
        accountMapper.update(null, wrapper);

        // 重新查询获取最新值
        account = accountMapper.selectOne(new LambdaQueryWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId));

        return R.ok(Map.of(
                "userId", account != null ? account.getUserId() : userId,
                "customId", account != null && account.getCustomId() != null && !account.getCustomId().isEmpty() ? account.getCustomId() : userId,
                "phoneTail", account != null && account.getPhoneTail() != null ? account.getPhoneTail() : "",
                "nickname", account != null ? account.getNickname() : "",
                "avatarUrl", account != null && account.getAvatarUrl() != null ? account.getAvatarUrl() : "",
                "hasCloudSync", account != null && account.getHasCloudSync() == 1
        ));
    }
}
