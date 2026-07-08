package io.healthresetplan.modules.auth;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.auth.dto.LoginRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeResponse;
import io.healthresetplan.modules.auth.dto.PasswordResetRequest;
import io.healthresetplan.modules.auth.dto.RefreshRequest;
import io.healthresetplan.modules.auth.dto.RegisterRequest;
import io.healthresetplan.modules.auth.dto.SmsLoginCodeRequest;
import io.healthresetplan.modules.auth.dto.SmsLoginRequest;
import io.healthresetplan.modules.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public R<TokenResponse> register(@Valid @RequestBody RegisterRequest req, HttpServletRequest httpReq) {
        return R.ok(authService.register(req, httpReq));
    }

    @PostMapping("/login")
    public R<TokenResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        return R.ok(authService.login(req, httpReq));
    }

    @PostMapping("/sms/send-code")
    public R<PasswordResetCodeResponse> sendSmsLoginCode(@Valid @RequestBody SmsLoginCodeRequest req) {
        return R.ok(authService.sendSmsLoginCode(req));
    }

    @PostMapping("/sms/login")
    public R<TokenResponse> smsLogin(@Valid @RequestBody SmsLoginRequest req, HttpServletRequest httpReq) {
        return R.ok(authService.smsLogin(req, httpReq));
    }

    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest httpReq) {
        return R.ok(authService.refresh(req, httpReq));
    }

    @PostMapping("/logout")
    public R<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return R.ok();
    }

    @PostMapping("/cancel-account")
    public R<Void> cancelAccount() {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.cancelAccount(userId);
        return R.ok();
    }

    @PostMapping("/password-reset/send-code")
    public R<PasswordResetCodeResponse> sendPasswordResetCode(@Valid @RequestBody PasswordResetCodeRequest req) {
        return R.ok(authService.sendPasswordResetCode(req));
    }

    @PostMapping("/password-reset/reset")
    public R<Void> resetPassword(@Valid @RequestBody PasswordResetRequest req) {
        authService.resetPassword(req);
        return R.ok();
    }
}
