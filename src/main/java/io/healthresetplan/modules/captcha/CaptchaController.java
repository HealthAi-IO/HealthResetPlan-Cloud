package io.healthresetplan.modules.captcha;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.captcha.dto.CaptchaCreateRequest;
import io.healthresetplan.modules.captcha.dto.CaptchaCreateResponse;
import io.healthresetplan.modules.captcha.dto.CaptchaVerifyRequest;
import io.healthresetplan.modules.captcha.dto.CaptchaVerifyResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @PostMapping("/create")
    public R<CaptchaCreateResponse> create(@Valid @RequestBody CaptchaCreateRequest request) {
        return R.ok(captchaService.create(request));
    }

    @PostMapping("/verify")
    public R<CaptchaVerifyResponse> verify(@Valid @RequestBody CaptchaVerifyRequest request) {
        return R.ok(captchaService.verify(request));
    }
}
