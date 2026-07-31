package io.healthresetplan.modules.auth;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.auth.dto.CancelAccountCodeRequest;
import io.healthresetplan.modules.auth.dto.CancelAccountRequest;
import io.healthresetplan.modules.auth.dto.PhonePasswordLoginRequest;
import io.healthresetplan.modules.auth.dto.PasswordResetCodeResponse;
import io.healthresetplan.modules.auth.dto.PhoneRegisterRequest;
import io.healthresetplan.modules.auth.dto.PhoneRegisterVerifyRequest;
import io.healthresetplan.modules.auth.dto.PhoneVerificationResponse;
import io.healthresetplan.modules.auth.dto.RefreshRequest;
import io.healthresetplan.modules.auth.dto.SmsLoginCodeRequest;
import io.healthresetplan.modules.auth.dto.SmsLoginRequest;
import io.healthresetplan.modules.auth.dto.SetPasswordRequest;
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

    @PostMapping("/sms/register")
    public R<TokenResponse> registerPhone(@Valid @RequestBody PhoneRegisterRequest req, HttpServletRequest httpReq) {
        return R.ok(authService.registerPhone(req, httpReq));
    }

    @PostMapping("/login")
    public R<TokenResponse> loginWithPhonePassword(@Valid @RequestBody PhonePasswordLoginRequest req,
                                                       HttpServletRequest httpReq) {
        return R.ok(authService.loginWithPhonePassword(req, httpReq));
    }

    @PostMapping("/sms/send-code")
    public R<PasswordResetCodeResponse> sendSmsLoginCode(@Valid @RequestBody SmsLoginCodeRequest req) {
        return R.ok(authService.sendSmsLoginCode(req));
    }

    @PostMapping("/sms/verify")
    public R<PhoneVerificationResponse> verifyPhone(@Valid @RequestBody PhoneRegisterVerifyRequest req,
                                                       HttpServletRequest httpReq) {
        return R.ok(authService.verifyPhone(req, httpReq));
    }

    @PostMapping("/password-reset/send-code")
    public R<PasswordResetCodeResponse> sendPasswordResetCode(@Valid @RequestBody io.healthresetplan.modules.auth.dto.PasswordResetCodeRequest req) {
        return R.ok(authService.sendPasswordResetCode(req));
    }

    @PostMapping("/password-reset/reset")
    public R<Void> resetPassword(@Valid @RequestBody io.healthresetplan.modules.auth.dto.PasswordResetRequest req) {
        authService.resetPassword(req);
        return R.ok();
    }

    @PostMapping("/password/set")
    public R<Void> setInitialPassword(@Valid @RequestBody SetPasswordRequest req) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.setInitialPassword(userId, req.getPassword());
        return R.ok();
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
    public R<Void> cancelAccount(@Valid @RequestBody CancelAccountRequest req) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.cancelAccount(userId, req);
        return R.ok();
    }

    @PostMapping("/cancel-account/send-code")
    public R<PasswordResetCodeResponse> sendCancelAccountCode(
            @Valid @RequestBody CancelAccountCodeRequest req) {
        String userId = (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return R.ok(authService.sendCancelAccountCode(userId, req.getPhone()));
    }

    @PostMapping("/account-recovery/send-code")
    public R<PasswordResetCodeResponse> sendAccountRecoveryCode(
            @Valid @RequestBody CancelAccountCodeRequest req) {
        return R.ok(authService.sendAccountRecoveryCode(req.getPhone()));
    }

    @PostMapping("/account-recovery/reactivate")
    public R<TokenResponse> reactivateAccount(
            @Valid @RequestBody CancelAccountRequest req,
            HttpServletRequest httpReq) {
        return R.ok(authService.reactivateAccount(req.getPhone(), req.getCode(), httpReq));
    }

}
