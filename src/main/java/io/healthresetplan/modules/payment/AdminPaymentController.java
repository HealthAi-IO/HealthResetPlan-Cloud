package io.healthresetplan.modules.payment;

import io.healthresetplan.common.result.R;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {
    private final PaymentService service;
    public AdminPaymentController(PaymentService service) { this.service = service; }

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
    private long currentAdminId() {
        String principal = String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        if (!principal.startsWith("admin:")) throw new IllegalStateException("管理员身份无效");
        return Long.parseLong(principal.substring("admin:".length()));
    }
}
