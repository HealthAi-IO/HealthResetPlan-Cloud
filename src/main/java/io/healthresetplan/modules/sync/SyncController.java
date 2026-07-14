package io.healthresetplan.modules.sync;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.membership.MembershipService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        int accepted = syncService.push(userId, request.deviceId(), request.keyFingerprint(), request.items());
        return R.ok(Map.of(
                "accepted", accepted,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    @GetMapping("/pull")
    public R<Map<String, Object>> pull(
            @RequestHeader(value = "X-Key-Fingerprint", required = false) String keyFingerprint,
            @RequestParam(value = "since", required = false) Long sinceMs,
            @RequestParam(value = "until", required = false) Long untilMs,
            @RequestParam(value = "offset", defaultValue = "0") int offset,
            @RequestParam(value = "limit", defaultValue = "200") int limit) {
        String userId = requireCloudSync();
        long serverTime = untilMs != null ? untilMs : Instant.now().toEpochMilli();
        BackendSyncService.PullPage page = syncService.pull(
                userId, keyFingerprint, sinceMs != null ? sinceMs : 0L, serverTime, offset, limit);
        return R.ok(Map.of(
                "items", page.items(),
                "hasMore", page.hasMore(),
                "serverTime", serverTime
        ));
    }

    private String requireCloudSync() {
        String userId = currentUserId();
        if (!membershipService.hasCloudSync(userId)) {
            throw new BusinessException(40301, "云同步功能需要开通会员，免费版数据仅保存在本地设备");
        }
        return userId;
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
