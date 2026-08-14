package io.healthresetplan.modules.ai.wellness;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.ai.AiConsentService;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ai/wellness")
public class AiWellnessController {

    private final AiWellnessService service;
    private final AiConsentService consentService;

    public AiWellnessController(AiWellnessService service, AiConsentService consentService) {
        this.service = service;
        this.consentService = consentService;
    }

    @PostMapping("/menu/generate")
    public R<AiWellnessResponse> generateMenu(
            @Valid @RequestBody PersonalizedMenuRequest request) {
        String userId = userId();
        consentService.requireActive(userId);
        return R.ok(service.generateMenu(userId, request));
    }

    @PostMapping("/menu/swap")
    public R<AiWellnessResponse> swapMeal(@Valid @RequestBody MenuSwapRequest request) {
        String userId = userId();
        consentService.requireActive(userId);
        return R.ok(service.swapMeal(userId, request));
    }

    @PostMapping("/weekly-report/generate")
    public R<AiWellnessResponse> generateWeeklyReport(
            @Valid @RequestBody WeeklyHealthReportRequest request) {
        String userId = userId();
        consentService.requireActive(userId);
        return R.ok(service.generateWeeklyReport(userId, request));
    }

    private String userId() {
        return (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
    }
}
