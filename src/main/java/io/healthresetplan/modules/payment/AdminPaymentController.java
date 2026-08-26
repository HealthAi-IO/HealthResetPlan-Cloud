package io.healthresetplan.modules.payment;

import io.healthresetplan.common.result.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {
    private final PaymentService service;
    public AdminPaymentController(PaymentService service) { this.service = service; }

    @GetMapping("/summary")
    public R<?> summary() { return R.ok(service.adminSummary()); }
    @GetMapping("/orders")
    public R<?> orders() { return R.ok(service.adminOrders()); }
    @GetMapping("/refunds")
    public R<?> refunds() { return R.ok(service.adminRefunds()); }
    @PostMapping("/refunds/{refundNo}/approve")
    public R<?> approve(@PathVariable String refundNo) {
        service.approveRefund(refundNo, currentAdminId());
        return R.ok();
    }
    @PostMapping("/refunds/{refundNo}/reject")
    public R<?> reject(@PathVariable String refundNo, @RequestBody Map<String, Object> body) {
        service.rejectRefund(refundNo, currentAdminId(), String.valueOf(body.getOrDefault("reason", "管理员驳回")));
        return R.ok();
    }

    @PostMapping("/credits/grants")
    public R<?> grantCredits(@Valid @RequestBody GrantCreditsRequest body, HttpServletRequest request) {
        return R.ok(service.grantCredits(
                body.userId(), body.amount(), body.reason(), currentAdminId(),
                clientIp(request), request.getHeader("User-Agent")));
    }

    @PostMapping("/vip/extensions")
    public R<?> extendVip(@Valid @RequestBody ExtendVipRequest body, HttpServletRequest request) {
        return R.ok(service.extendVip(
                body.userId(), body.days(), body.reason(), currentAdminId(),
                clientIp(request), request.getHeader("User-Agent")));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded == null || forwarded.isBlank()
                ? request.getRemoteAddr() : forwarded.split(",")[0].trim();
    }

    private long currentAdminId() {
        String principal = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        if (!principal.startsWith("admin:")) throw new IllegalStateException("管理员身份无效");
        return Long.parseLong(principal.substring("admin:".length()));
    }

    public record GrantCreditsRequest(
            @NotBlank @Size(max = 64) String userId,
            @NotNull @Min(1) @Max(10000) Integer amount,
            @NotBlank @Size(min = 2, max = 200) String reason) {}

    public record ExtendVipRequest(
            @NotBlank @Size(max = 64) String userId,
            @NotNull @Min(1) @Max(3650) Integer days,
            @NotBlank @Size(min = 2, max = 200) String reason) {}
}
