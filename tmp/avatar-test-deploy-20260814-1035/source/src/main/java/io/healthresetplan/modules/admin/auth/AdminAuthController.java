package io.healthresetplan.modules.admin.auth;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.admin.auth.dto.AdminLoginRequest;
import io.healthresetplan.modules.admin.auth.dto.AdminRefreshRequest;
import io.healthresetplan.modules.admin.auth.dto.AdminTokenResponse;
import io.healthresetplan.modules.admin.auth.dto.TotpDisableRequest;
import io.healthresetplan.modules.admin.auth.dto.TotpEnableRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public R<AdminTokenResponse> login(@Valid @RequestBody AdminLoginRequest request,
                                       HttpServletRequest httpRequest) {
        return R.ok(adminAuthService.login(request, httpRequest));
    }

    @PostMapping("/refresh")
    public R<AdminTokenResponse> refresh(@Valid @RequestBody AdminRefreshRequest request,
                                         HttpServletRequest httpRequest) {
        return R.ok(adminAuthService.refresh(request.getRefreshToken(), httpRequest));
    }

    @PostMapping("/logout")
    public R<Void> logout(@RequestBody(required = false) AdminRefreshRequest request,
                          HttpServletRequest httpRequest) {
        adminAuthService.logout(request == null ? "" : request.getRefreshToken(), httpRequest);
        return R.ok();
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(Authentication authentication) {
        return R.ok(adminAuthService.profile(adminId(authentication)));
    }

    @PostMapping("/totp/setup")
    public R<Map<String, Object>> setupTotp(Authentication authentication) {
        return R.ok(adminAuthService.createTotpSetup(adminId(authentication)));
    }

    @PostMapping("/totp/enable")
    public R<Void> enableTotp(@Valid @RequestBody TotpEnableRequest request,
                              Authentication authentication,
                              HttpServletRequest httpRequest) {
        adminAuthService.enableTotp(
                adminId(authentication), request.getSecret(), request.getCode(), httpRequest);
        return R.ok();
    }

    @PostMapping("/totp/disable")
    public R<Void> disableTotp(@Valid @RequestBody TotpDisableRequest request,
                               Authentication authentication,
                               HttpServletRequest httpRequest) {
        adminAuthService.disableTotp(adminId(authentication), request.getCode(), httpRequest);
        return R.ok();
    }

    @GetMapping("/sessions")
    public R<List<Map<String, Object>>> sessions(Authentication authentication) {
        return R.ok(adminAuthService.sessions(adminId(authentication)));
    }

    @DeleteMapping("/sessions/{sessionId}")
    public R<Void> revokeSession(@PathVariable long sessionId,
                                 Authentication authentication,
                                 HttpServletRequest httpRequest) {
        adminAuthService.revokeSession(adminId(authentication), sessionId, httpRequest);
        return R.ok();
    }

    private long adminId(Authentication authentication) {
        String principal = authentication == null ? "" : String.valueOf(authentication.getPrincipal());
        if (!principal.startsWith("admin:")) {
            throw new io.healthresetplan.common.exception.BusinessException(40100, "请先登录管理员账号");
        }
        return Long.parseLong(principal.substring("admin:".length()));
    }
}
