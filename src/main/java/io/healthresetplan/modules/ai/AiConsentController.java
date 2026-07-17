package io.healthresetplan.modules.ai;

import io.healthresetplan.common.result.R;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai/consent")
public class AiConsentController {
    private final AiConsentService service;
    public AiConsentController(AiConsentService service) { this.service = service; }
    @GetMapping public R<Map<String, Object>> status() { return R.ok(service.status(userId())); }
    @PostMapping public R<Void> accept() { service.accept(userId()); return R.ok(); }
    @DeleteMapping public R<Void> revoke() { service.revoke(userId()); return R.ok(); }
    private String userId() { return String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal()); }
}
