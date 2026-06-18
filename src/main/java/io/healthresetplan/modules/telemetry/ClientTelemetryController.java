package io.healthresetplan.modules.telemetry;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.telemetry.dto.ClientEventRequest;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
public class ClientTelemetryController {

    private final JdbcTemplate jdbc;

    public ClientTelemetryController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/events")
    public R<Void> record(@Valid @RequestBody ClientEventRequest request,
                          Authentication authentication) {
        String userId = authentication == null ? "" : String.valueOf(authentication.getPrincipal());
        if (userId.isBlank() || userId.startsWith("admin:")) {
            throw new BusinessException(40100, "请先登录用户账号");
        }
        jdbc.update("""
                INSERT INTO client_event (
                  user_id, platform, app_version, channel, event_type, device_id, trace_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                value(request.getPlatform()),
                value(request.getAppVersion()),
                request.getChannel() == null || request.getChannel().isBlank()
                        ? "official" : request.getChannel().trim(),
                value(request.getEventType()),
                value(request.getDeviceId()),
                value(request.getTraceId()));
        return R.ok();
    }

    private String value(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}

