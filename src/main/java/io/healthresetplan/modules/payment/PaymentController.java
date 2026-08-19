package io.healthresetplan.modules.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.result.R;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class PaymentController {
    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private final PaymentService service;
    private final ObjectMapper objectMapper;

    public PaymentController(PaymentService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/ai-credits/products")
    public R<?> products() { return R.ok(service.products()); }

    @GetMapping("/ai-credits/balance")
    public R<?> balance() { return R.ok(service.balance(currentUserId())); }

    @GetMapping("/ai-credits/ledger")
    public R<?> ledger() { return R.ok(service.ledger(currentUserId())); }

    @PostMapping("/ai-credits/orders")
    public R<?> createOrder(@RequestBody Map<String, Object> body) {
        return R.ok(service.createOrder(currentUserId(), text(body.get("productCode")), text(body.get("channel"))));
    }

    @GetMapping("/ai-credits/orders/{orderNo}")
    public R<?> order(@PathVariable String orderNo) {
        return R.ok(service.orderStatus(currentUserId(), orderNo));
    }

    @PostMapping("/ai-credits/refunds")
    public R<?> requestRefund(@RequestBody Map<String, Object> body) {
        return R.ok(service.requestRefund(currentUserId(), text(body.get("orderNo")), text(body.get("reason"))));
    }

    @PostMapping(value = "/payments/wechat/notify", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> wechatNotify(@RequestHeader Map<String, String> headers, @RequestBody String body) {
        try {
            PaymentGateway gateway = service.gateway("wechat");
            JsonNode root = objectMapper.readTree(body);
            if (root.path("event_type").asText().startsWith("REFUND")) {
                PaymentGateway.RefundNotification refund = gateway.parseRefundNotification(headers, Collections.emptyMap(), body);
                if (refund.succeeded()) service.completeRefund(refund.refundNo(), refund.channelRefundNo());
            } else {
                service.completePayment(gateway.parseNotification(headers, Collections.emptyMap(), body), "wechat");
            }
            return ResponseEntity.ok("{\"code\":\"SUCCESS\",\"message\":\"成功\"}");
        } catch (Exception ex) {
            log.warn("微信支付回调处理失败 exceptionType={} message={}",
                    ex.getClass().getSimpleName(), ex.getMessage());
            return ResponseEntity.badRequest().body("{\"code\":\"FAIL\",\"message\":\"回调处理失败\"}");
        }
    }

    @PostMapping(value = "/payments/alipay/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String alipayNotify(@RequestParam Map<String, String> form) {
        try {
            service.completePayment(service.gateway("alipay").parseNotification(
                    Collections.emptyMap(), form, ""), "alipay");
            return "success";
        } catch (Exception ex) {
            return "fail";
        }
    }

    private String currentUserId() {
        return String.valueOf(SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
