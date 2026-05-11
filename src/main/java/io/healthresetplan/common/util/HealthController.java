package io.healthresetplan.common.util;

import io.healthresetplan.common.result.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 健康检查 / Liveness 探针。
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public R<Map<String, Object>> health() {
        return R.ok(Map.of(
                "service", "health-reset-plan-cloud",
                "status", "ok",
                "ts", System.currentTimeMillis()
        ));
    }
}
