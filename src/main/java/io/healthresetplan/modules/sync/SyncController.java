package io.healthresetplan.modules.sync;

import io.healthresetplan.common.result.R;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 端到端加密同步入口。
 *
 * <p>服务端不持有用户主密钥，因此对 cipher / iv / tag 不做解密，仅做"管道"。</p>
 */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    @PostMapping("/push")
    public R<Map<String, Object>> push(@Valid @RequestBody SyncPushRequest request) {
        // TODO: 校验 JWT、设备 ID、版本号；写入对应表的密文。
        return R.ok(Map.of(
                "accepted", request.items() == null ? 0 : request.items().size(),
                "serverTime", Instant.now().toEpochMilli()
        ));
    }

    @GetMapping("/pull")
    public R<Map<String, Object>> pull(@RequestParam(value = "since", required = false) Long sinceMs,
                                       @RequestParam(value = "limit", defaultValue = "200") int limit) {
        // TODO: 按用户 + sinceMs 增量拉取密文与元数据。
        return R.ok(Map.of(
                "items", List.of(),
                "nextCursor", null,
                "serverTime", Instant.now().toEpochMilli()
        ));
    }
}
