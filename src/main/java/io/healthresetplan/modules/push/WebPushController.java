package io.healthresetplan.modules.push;

import io.healthresetplan.common.result.R;
import io.healthresetplan.config.WebPushProperties;
import io.healthresetplan.modules.push.dto.WebPushSubscriptionRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/push")
public class WebPushController {
    private final WebPushProperties properties;
    private final WebPushSubscriptionService service;

    public WebPushController(WebPushProperties properties, WebPushSubscriptionService service) {
        this.properties = properties;
        this.service = service;
    }

    @GetMapping("/config")
    public R<Map<String, Object>> config() {
        boolean available = properties.isEnabled() && !properties.getPublicKey().isBlank();
        return R.ok(Map.of("enabled", available, "publicKey", available ? properties.getPublicKey() : ""));
    }

    @PutMapping("/subscription")
    public R<Void> subscribe(
            @RequestHeader("X-Device-Id") String deviceId,
            @Valid @RequestBody WebPushSubscriptionRequest request) {
        service.subscribe(currentUserId(), deviceId, request);
        return R.ok();
    }

    @DeleteMapping("/subscription")
    public R<Void> unsubscribe(@RequestHeader("X-Device-Id") String deviceId) {
        service.unsubscribe(currentUserId(), deviceId);
        return R.ok();
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
