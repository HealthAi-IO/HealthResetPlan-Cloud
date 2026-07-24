package io.healthresetplan.modules.sync;

import io.healthresetplan.common.result.R;
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

    private final BackendSyncService syncService;
    public SyncController(BackendSyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    public R<Map<String, Object>> push(@Valid @RequestBody SyncPushRequest request) {
        String userId = requireCloudSync();
        BackendSyncService.PushResult result = syncService.push(
                userId, request.deviceId(), request.keyFingerprint(), request.items());
        return R.ok(Map.of(
                "accepted", result.accepted(),
                "rejected", result.rejected(),
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

    @GetMapping("/key-status")
    public R<Map<String, Object>> keyStatus(
            @RequestHeader(value = "X-Key-Fingerprint", required = false) String keyFingerprint) {
        String userId = requireCloudSync();
        BackendSyncService.KeyStatus status = syncService.keyStatus(userId, keyFingerprint);
        return R.ok(Map.of(
                "matchingKeyRecords", status.matchingKeyRecords(),
                "otherKeyRecords", status.otherKeyRecords()
        ));
    }

    private String requireCloudSync() {
        return currentUserId();
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
