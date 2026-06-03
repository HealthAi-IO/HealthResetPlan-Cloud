package io.healthresetplan.modules.sync;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.membership.MembershipService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 客户端加密同步入口。
 *
 * <p>服务端不持有用户主密钥，对 cipher / iv / tag 不做解密，仅做"管道"。</p>
 * <p>push / pull 均需有效的云同步会员权益。</p>
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final MembershipService membershipService;
    private final BackendSyncService syncService;

    public SyncController(MembershipService membershipService, BackendSyncService syncService) {
        this.membershipService = membershipService;
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public R<Map<String, Object>> push(@Valid @RequestBody SyncPushRequest request) {
        String userId = requireCloudSync();
        int accepted = syncService.push(userId, request.deviceId(), request.items());
        return R.ok(Map.of(
                "accepted", accepted,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    @GetMapping("/pull")
    public R<Map<String, Object>> pull(
            @RequestParam(value = "since", required = false) Long sinceMs,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        String userId = requireCloudSync();
        List<SyncPullItem> items = syncService.pull(userId, sinceMs != null ? sinceMs : 0L, limit);
        return R.ok(Map.of(
                "items", items,
                "nextCursor", null,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    /** 校验云同步权益并返回当前用户 ID。 */
    private String requireCloudSync() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!membershipService.hasCloudSync(userId)) {
            throw new BusinessException(40301, "云同步功能需要开通会员，免费版数据仅保存在本地设备");
        }
        return userId;
    }
}
