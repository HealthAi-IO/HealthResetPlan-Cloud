package io.healthresetplan.modules.ai.plan;

import io.healthresetplan.common.result.R;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/plan")
public class AiPlanController {

    private final AiPlanService planService;

    public AiPlanController(AiPlanService planService) {
        this.planService = planService;
    }

    /**
     * AI 生成 7 天健康方案（会员专属）。
     *
     * <p>由 JWT 过滤器保证已登录；会员资格在 Service 层校验。</p>
     */
    @PostMapping("/generate")
    public R<AiPlanResponse> generate(@Valid @RequestBody AiPlanRequest req) {
        String userId = (String) SecurityContextHolder.getContext()
                .getAuthentication().getPrincipal();
        return R.ok(planService.generate(userId, req));
    }
}
