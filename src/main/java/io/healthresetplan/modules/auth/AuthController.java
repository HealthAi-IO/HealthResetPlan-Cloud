package io.healthresetplan.modules.auth;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.auth.dto.LoginRequest;
import io.healthresetplan.modules.auth.dto.RefreshRequest;
import io.healthresetplan.modules.auth.dto.RegisterRequest;
import io.healthresetplan.modules.auth.dto.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
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

    @PostMapping("/refresh")
    public R<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req, HttpServletRequest httpReq) {
        return R.ok(authService.refresh(req, httpReq));
    }

    @PostMapping("/logout")
    public R<Void> logout(@Valid @RequestBody RefreshRequest req) {
        authService.logout(req.getRefreshToken());
        return R.ok();
    }
}
