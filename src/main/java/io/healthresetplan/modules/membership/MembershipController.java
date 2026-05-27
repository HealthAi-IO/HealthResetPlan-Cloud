package io.healthresetplan.modules.membership;

import io.healthresetplan.common.result.R;
import io.healthresetplan.modules.membership.dto.CreateOrderRequest;
import io.healthresetplan.modules.membership.dto.CreateOrderResponse;
import io.healthresetplan.modules.membership.dto.MembershipStatusResponse;
import io.healthresetplan.modules.membership.entity.MembershipPlan;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/membership")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    /** 获取所有上架套餐 */
    @GetMapping("/plans")
    public R<List<MembershipPlan>> plans() {
        return R.ok(membershipService.listPlans());
    }

    /** 获取当前用户会员状态 */
    @GetMapping("/status")
    public R<MembershipStatusResponse> status() {
        return R.ok(membershipService.getStatus(currentUserId()));
    }

    /** 创建支付订单 */
    @PostMapping("/orders")
    public R<CreateOrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        return R.ok(membershipService.createOrder(req, currentUserId()));
    }

    /**
     * 支付回调（支付宝 / 微信 POST 到此地址）。
     * 此接口无需 JWT 认证，由支付网关签名保障安全。
     */
    @PostMapping("/callback/{channel}")
    public String callback(@PathVariable String channel,
                           HttpServletRequest request) throws IOException {
        Map<String, String> headers = extractHeaders(request);
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        membershipService.handleCallback(channel, headers, body);
        return "success";
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        return request.getHeaderNames().asIterator().next() == null
                ? Map.of()
                : java.util.Collections.list(request.getHeaderNames()).stream()
                        .collect(Collectors.toMap(h -> h, request::getHeader, (a, b) -> a));
    }

    private String currentUserId() {
        return (String) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
