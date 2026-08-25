package io.healthresetplan.modules.payment;

import io.healthresetplan.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    private static final int TRIAL_CREDITS = 3;
    private final JdbcTemplate jdbc;
    private final PaymentProperties properties;
    private final Map<String, PaymentGateway> gateways;

    public PaymentService(JdbcTemplate jdbc, PaymentProperties properties, List<PaymentGateway> gateways) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.gateways = gateways.stream().collect(Collectors.toMap(PaymentGateway::channel, Function.identity()));
    }

    public List<Map<String, Object>> products() {
        return jdbc.queryForList("""
                SELECT code, name, price_fen, credit_amount
                FROM ai_credit_product
                WHERE status = 1
                ORDER BY sort_order, id
                """);
    }

    @Transactional
    public Map<String, Object> balance(String userId) {
        ensureTrial(userId);
        Map<String, Object> account = jdbc.queryForMap("""
                SELECT balance, granted_total, consumed_total, updated_at
                FROM ai_credit_account WHERE user_id = ?
                """, userId);
        Map<String, Object> response = new LinkedHashMap<>(account);
        Map<String, Object> sources = jdbc.queryForMap("""
                SELECT
                  COALESCE(SUM(CASE WHEN reason IN ('trial', 'grant') AND change_amount > 0 THEN change_amount ELSE 0 END), 0) AS gifted_total,
                  COALESCE(SUM(CASE WHEN reason = 'purchase' AND change_amount > 0 THEN change_amount ELSE 0 END), 0) AS purchased_total
                FROM ai_credit_ledger WHERE user_id = ?
                """, userId);
        response.putAll(sources);
        response.put("trialCredits", TRIAL_CREDITS);
        response.put("channels", Map.of(
                "wechat", gateway("wechat").enabled(),
                "alipay", gateway("alipay").enabled()));
        return response;
    }

    public boolean hasAvailable(String userId) {
        return intValue(balance(userId).get("balance")) > 0;
    }

    @Transactional
    public Map<String, Object> createOrder(String userId, String productCode, String channel) {
        String normalizedChannel = normalize(channel);
        PaymentGateway gateway = gateway(normalizedChannel);
        if (!gateway.enabled()) throw new BusinessException(50310, channelName(normalizedChannel) + "支付尚未开通");
        Map<String, Object> product = one("""
                SELECT code, name, price_fen, credit_amount
                FROM ai_credit_product WHERE code = ? AND status = 1
                """, productCode);
        String orderNo = number("P");
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(properties.getOrderExpireMinutes());
        jdbc.update("""
                INSERT INTO payment_order (
                  order_no, user_id, product_code, product_name, amount_fen, credit_amount,
                  remaining_credit, channel, status, expires_at
                ) VALUES (?, ?, ?, ?, ?, ?, 0, ?, 'created', ?)
                """, orderNo, userId, product.get("code"), product.get("name"),
                intValue(product.get("price_fen")), intValue(product.get("credit_amount")),
                normalizedChannel, expiresAt);
        Map<String, Object> payment;
        try {
            payment = gateway.createPayment(orderNo, String.valueOf(product.get("name")),
                    intValue(product.get("price_fen")), expiresAt);
        } catch (RuntimeException ex) {
            jdbc.update("UPDATE payment_order SET status = 'failed' WHERE order_no = ?", orderNo);
            throw new BusinessException(50311, "支付下单失败，请稍后重试");
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("orderNo", orderNo);
        response.put("expiresAt", expiresAt.toInstant(ZoneOffset.ofHours(8)));
        response.put("payment", payment);
        return response;
    }

    @Transactional
    public void completePayment(PaymentGateway.PaymentNotification notification, String channel) {
        if (!notification.paid()) return;
        Map<String, Object> order = oneForUpdate("SELECT * FROM payment_order WHERE order_no = ? FOR UPDATE",
                notification.orderNo());
        if ("paid".equals(order.get("status"))) return;
        if (!"created".equals(order.get("status"))
                || !channel.equals(order.get("channel"))
                || notification.amountFen() != intValue(order.get("amount_fen"))) {
            throw new BusinessException(40910, "支付订单状态或金额不一致");
        }
        int credits = intValue(order.get("credit_amount"));
        String userId = String.valueOf(order.get("user_id"));
        ensureAccount(userId);
        jdbc.update("""
                UPDATE payment_order
                SET status = 'paid', channel_order_no = ?, paid_at = NOW(3), remaining_credit = ?
                WHERE order_no = ? AND status = 'created'
                """, notification.channelOrderNo(), credits, notification.orderNo());
        addCredits(userId, credits, "purchase", notification.orderNo(), null);
    }

    public Map<String, Object> orderStatus(String userId, String orderNo) {
        Map<String, Object> order = one("""
                SELECT o.order_no, o.product_name, o.amount_fen, o.credit_amount, o.channel, o.status,
                       o.paid_at, o.expires_at, o.refunded_at, o.created_at,
                       r.status AS refund_status, r.failure_reason AS refund_failure_reason
                FROM payment_order o LEFT JOIN payment_refund r ON r.order_no = o.order_no
                WHERE o.order_no = ? AND o.user_id = ?
                """, orderNo, userId);
        if ("created".equals(order.get("status"))
                && LocalDateTime.now().isAfter((LocalDateTime) order.get("expires_at"))) {
            jdbc.update("UPDATE payment_order SET status = 'expired' WHERE order_no = ? AND status = 'created'", orderNo);
            order.put("status", "expired");
        }
        return order;
    }

    public List<Map<String, Object>> ledger(String userId) {
        return jdbc.queryForList("""
                SELECT change_amount, balance_after, reason, source_order_no, feature_code, created_at
                FROM ai_credit_ledger WHERE user_id = ? ORDER BY id DESC LIMIT 100
                """, userId);
    }

    public List<Map<String, Object>> orders(String userId) {
        return jdbc.queryForList("""
                SELECT o.order_no, o.product_name, o.amount_fen, o.credit_amount, o.remaining_credit,
                       o.channel, o.status, o.paid_at, o.expires_at, o.refunded_at, o.created_at,
                       r.status AS refund_status, r.failure_reason AS refund_failure_reason
                FROM payment_order o LEFT JOIN payment_refund r ON r.order_no = o.order_no
                WHERE o.user_id = ? ORDER BY o.id DESC LIMIT 50
                """, userId);
    }

    @Transactional
    public Map<String, Object> requestRefund(String userId, String orderNo, String reason) {
        Map<String, Object> order = oneForUpdate("SELECT * FROM payment_order WHERE order_no = ? AND user_id = ? FOR UPDATE",
                orderNo, userId);
        validateRefundable(order);
        String refundNo = number("R");
        String normalizedReason = requiredReason(reason);
        int credits = intValue(order.get("credit_amount"));
        removeCredits(userId, credits, "refund", orderNo);
        jdbc.update("""
                UPDATE payment_order SET status = 'refund_processing', remaining_credit = 0, refund_reason = ?
                WHERE order_no = ? AND status = 'paid'
                """, normalizedReason, orderNo);
        jdbc.update("""
                INSERT INTO payment_refund (
                  refund_no, order_no, user_id, channel, amount_fen, credit_amount, status, reason
                ) VALUES (?, ?, ?, ?, ?, ?, 'submitting', ?)
                """, refundNo, orderNo, userId, order.get("channel"), order.get("amount_fen"),
                credits, normalizedReason);
        PaymentGateway.RefundResult result;
        try {
            result = gateway(String.valueOf(order.get("channel"))).refund(
                    orderNo, refundNo, intValue(order.get("amount_fen")), normalizedReason);
        } catch (RuntimeException error) {
            jdbc.update("""
                    UPDATE payment_refund SET status = 'needs_manual', failure_reason = ?
                    WHERE refund_no = ? AND status = 'submitting'
                    """, channelError(error), refundNo);
            return Map.of("refundNo", refundNo, "status", "needs_manual");
        }
        if (!result.accepted()) {
            throw new BusinessException(50312, "支付渠道未受理退款，请稍后重试");
        }
        jdbc.update("""
                UPDATE payment_refund SET status = 'processing', channel_refund_no = ?, reviewed_at = NOW(3)
                WHERE refund_no = ? AND status = 'submitting'
                """, result.channelRefundNo(), refundNo);
        if ("alipay".equals(order.get("channel"))) {
            finalizeRefund(refundNo, orderNo, result.channelRefundNo(), normalizedReason);
            return Map.of("refundNo", refundNo, "status", "completed");
        }
        return Map.of("refundNo", refundNo, "status", "processing");
    }

    public List<Map<String, Object>> adminOrders() {
        return jdbc.queryForList("""
                SELECT order_no, user_id, product_name, amount_fen, credit_amount, remaining_credit,
                       channel, channel_order_no, status, paid_at, refunded_at, created_at
                FROM payment_order ORDER BY id DESC LIMIT 500
                """);
    }

    public Map<String, Object> adminSummary() {
        return jdbc.queryForMap("""
                SELECT
                  COUNT(*) AS total_orders,
                  COALESCE(SUM(CASE WHEN status = 'paid' THEN 1 ELSE 0 END), 0) AS paid_orders,
                  COALESCE(SUM(CASE WHEN status = 'paid' THEN amount_fen ELSE 0 END), 0) AS paid_amount_fen,
                  COALESCE(SUM(CASE WHEN created_at >= CURDATE() THEN 1 ELSE 0 END), 0) AS today_orders,
                  (SELECT COUNT(*) FROM payment_refund WHERE status = 'needs_manual') AS manual_refunds
                FROM payment_order
                """);
    }

    public List<Map<String, Object>> adminRefunds() {
        return jdbc.queryForList("""
                SELECT refund_no, order_no, user_id, channel, amount_fen, credit_amount,
                       status, reason, failure_reason, reviewed_by, reviewed_at, completed_at, created_at
                FROM payment_refund ORDER BY id DESC LIMIT 500
                """);
    }

    @Transactional
    public Map<String, Object> grantCredits(String userId, int amount, String reason,
                                            long adminId, String ip, String userAgent) {
        String targetUserId = userId == null ? "" : userId.trim();
        String grantReason = requiredGrantReason(reason);
        if (targetUserId.isBlank() || amount < 1 || amount > 10000) {
            throw new BusinessException(40001, "赠送次数应为 1-10000");
        }
        Map<String, Object> user = one("""
                SELECT user_id, nickname, phone_tail, status
                FROM user_account
                WHERE user_id = ? AND deleted_at IS NULL
                FOR UPDATE
                """, targetUserId);
        if (intValue(user.get("status")) != 1) {
            throw new BusinessException(40917, "用户状态异常，不能赠送权益");
        }
        ensureTrial(targetUserId);
        addCredits(targetUserId, amount, "grant", null, "manual_admin");
        int balance = jdbc.queryForObject(
                "SELECT balance FROM ai_credit_account WHERE user_id = ?", Integer.class, targetUserId);
        String safeUserAgent = userAgent == null ? "" : userAgent.trim();
        jdbc.update("""
                INSERT INTO audit_log (actor_type, actor_id, action, target, ip, user_agent, detail)
                VALUES ('admin', ?, 'ai_credit_granted', ?, ?, ?, ?)
                """, String.valueOf(adminId), "user:" + targetUserId,
                ip == null ? "" : ip.trim(), safeUserAgent.substring(0, Math.min(safeUserAgent.length(), 255)),
                "amount=" + amount + ", balanceAfter=" + balance + ", reason=" + grantReason);
        return Map.of(
                "userId", targetUserId,
                "nickname", String.valueOf(user.getOrDefault("nickname", "")),
                "amount", amount,
                "balance", balance,
                "reason", grantReason);
    }

    @Transactional
    public void approveRefund(String refundNo, long adminId) {
        Map<String, Object> refund = oneForUpdate("SELECT * FROM payment_refund WHERE refund_no = ? FOR UPDATE", refundNo);
        if (!"needs_manual".equals(refund.get("status"))) throw new BusinessException(40911, "该退款不需要人工处理");
        holdRefundCreditsIfNeeded(refund);
        PaymentGateway.RefundResult result = gateway(String.valueOf(refund.get("channel"))).refund(
                String.valueOf(refund.get("order_no")), refundNo, intValue(refund.get("amount_fen")),
                String.valueOf(refund.get("reason")));
        if (!result.accepted()) throw new BusinessException(50312, "支付渠道未受理退款");
        jdbc.update("""
                UPDATE payment_refund SET status = 'processing', channel_refund_no = ?,
                  failure_reason = NULL, reviewed_by = ?, reviewed_at = NOW(3)
                WHERE refund_no = ? AND status = 'needs_manual'
                """, result.channelRefundNo(), adminId, refundNo);
        if ("alipay".equals(refund.get("channel"))) {
            finalizeRefund(refundNo, String.valueOf(refund.get("order_no")), result.channelRefundNo(),
                    String.valueOf(refund.get("reason")));
        }
    }

    @Transactional
    public void completeRefund(String refundNo, String channelRefundNo, int amountFen) {
        Map<String, Object> refund = oneForUpdate("SELECT * FROM payment_refund WHERE refund_no = ? FOR UPDATE", refundNo);
        if ("completed".equals(refund.get("status"))) return;
        if (!"processing".equals(refund.get("status")) || amountFen != intValue(refund.get("amount_fen"))) {
            throw new BusinessException(40911, "退款回调状态或金额不一致");
        }
        holdRefundCreditsIfNeeded(refund);
        finalizeRefund(refundNo, String.valueOf(refund.get("order_no")), channelRefundNo,
                String.valueOf(refund.get("reason")));
    }

    @Transactional
    public void rejectRefund(String refundNo, long adminId, String reason) {
        Map<String, Object> refund = oneForUpdate("SELECT * FROM payment_refund WHERE refund_no = ? FOR UPDATE", refundNo);
        if (!"needs_manual".equals(refund.get("status"))) throw new BusinessException(40911, "该退款不需要人工处理");
        String rejectionReason = requiredReason(reason);
        String orderNo = String.valueOf(refund.get("order_no"));
        if (refundCreditsHeld(orderNo)) {
            restoreRefundCredits(String.valueOf(refund.get("user_id")), intValue(refund.get("credit_amount")), orderNo);
        }
        jdbc.update("""
                UPDATE payment_order SET status = 'refund_rejected', remaining_credit = credit_amount
                WHERE order_no = ? AND status = 'refund_processing'
                """, orderNo);
        jdbc.update("""
                UPDATE payment_refund SET status = 'rejected', failure_reason = ?, reviewed_by = ?, reviewed_at = NOW(3)
                WHERE refund_no = ? AND status = 'needs_manual'
                """, rejectionReason, adminId, refundNo);
    }

    @Transactional
    public boolean consume(String userId, String featureCode) {
        ensureTrial(userId);
        int updated = jdbc.update("""
                UPDATE ai_credit_account
                SET balance = balance - 1, consumed_total = consumed_total + 1, version = version + 1
                WHERE user_id = ? AND balance > 0
                """, userId);
        if (updated != 1) return false;
        jdbc.update("""
                UPDATE payment_order SET remaining_credit = remaining_credit - 1
                WHERE id = (
                  SELECT id FROM (SELECT id FROM payment_order
                    WHERE user_id = ? AND status = 'paid' AND remaining_credit > 0
                    ORDER BY paid_at, id LIMIT 1) selected
                )
                """, userId);
        int balance = jdbc.queryForObject("SELECT balance FROM ai_credit_account WHERE user_id = ?", Integer.class, userId);
        jdbc.update("""
                INSERT INTO ai_credit_ledger (user_id, change_amount, balance_after, reason, feature_code)
                VALUES (?, -1, ?, 'consume', ?)
                """, userId, balance, featureCode);
        return true;
    }

    public PaymentGateway gateway(String channel) {
        PaymentGateway gateway = gateways.get(normalize(channel));
        if (gateway == null) throw new BusinessException(40001, "不支持的支付渠道");
        return gateway;
    }

    private void finalizeRefund(String refundNo, String orderNo, String channelRefundNo, String reason) {
        jdbc.update("""
                UPDATE payment_order SET status = 'refunded', remaining_credit = 0,
                  refund_amount_fen = amount_fen, refund_reason = ?, refunded_at = NOW(3)
                WHERE order_no = ? AND status = 'refund_processing'
                """, reason, orderNo);
        jdbc.update("""
                UPDATE payment_refund SET status = 'completed', channel_refund_no = ?,
                  failure_reason = NULL, completed_at = NOW(3)
                WHERE refund_no = ? AND status IN ('processing', 'needs_manual')
                """, channelRefundNo, refundNo);
    }

    private String channelError(RuntimeException error) {
        return "支付渠道结果不确定，请人工核对后使用原退款单号重试";
    }

    private void holdRefundCreditsIfNeeded(Map<String, Object> refund) {
        String orderNo = String.valueOf(refund.get("order_no"));
        if (refundCreditsHeld(orderNo)) return;
        Map<String, Object> order = oneForUpdate("SELECT * FROM payment_order WHERE order_no = ? FOR UPDATE", orderNo);
        validateRefundable(order);
        removeCredits(String.valueOf(refund.get("user_id")), intValue(refund.get("credit_amount")), "refund", orderNo);
        jdbc.update("""
                UPDATE payment_order SET status = 'refund_processing', remaining_credit = 0,
                  refund_reason = ? WHERE order_no = ? AND status = 'paid'
                """, refund.get("reason"), orderNo);
    }

    private boolean refundCreditsHeld(String orderNo) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM ai_credit_ledger
                WHERE source_order_no = ? AND reason = 'refund'
                """, Integer.class, orderNo);
        return count != null && count > 0;
    }

    private void validateRefundable(Map<String, Object> order) {
        if (!"paid".equals(order.get("status"))) throw new BusinessException(40912, "该订单不可退款");
        LocalDateTime paidAt = (LocalDateTime) order.get("paid_at");
        if (paidAt == null || paidAt.plusDays(properties.getRefundWindowDays()).isBefore(LocalDateTime.now())) {
            throw new BusinessException(40913, "订单已超过 7 天退款期限");
        }
        if (intValue(order.get("remaining_credit")) != intValue(order.get("credit_amount"))) {
            throw new BusinessException(40914, "该订单次数已使用，不支持退款");
        }
    }

    private void ensureTrial(String userId) {
        ensureAccount(userId);
        int updated = jdbc.update("""
                UPDATE ai_credit_account
                SET balance = balance + ?, granted_total = granted_total + ?, trial_granted = 1, version = version + 1
                WHERE user_id = ? AND trial_granted = 0
                """, TRIAL_CREDITS, TRIAL_CREDITS, userId);
        if (updated == 1) {
            int balance = jdbc.queryForObject("SELECT balance FROM ai_credit_account WHERE user_id = ?", Integer.class, userId);
            jdbc.update("""
                    INSERT INTO ai_credit_ledger (user_id, change_amount, balance_after, reason)
                    VALUES (?, ?, ?, 'trial')
                    """, userId, TRIAL_CREDITS, balance);
        }
    }

    private void ensureAccount(String userId) {
        jdbc.update("""
                INSERT INTO ai_credit_account (user_id) VALUES (?)
                ON DUPLICATE KEY UPDATE user_id = VALUES(user_id)
                """, userId);
    }

    private void addCredits(String userId, int amount, String reason, String orderNo, String feature) {
        jdbc.update("""
                UPDATE ai_credit_account
                SET balance = balance + ?, granted_total = granted_total + ?, version = version + 1
                WHERE user_id = ?
                """, amount, amount, userId);
        int balance = jdbc.queryForObject("SELECT balance FROM ai_credit_account WHERE user_id = ?", Integer.class, userId);
        jdbc.update("""
                INSERT INTO ai_credit_ledger (user_id, change_amount, balance_after, reason, source_order_no, feature_code)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, amount, balance, reason, orderNo, feature);
    }

    private void removeCredits(String userId, int amount, String reason, String orderNo) {
        int updated = jdbc.update("""
                UPDATE ai_credit_account SET balance = balance - ?, version = version + 1
                WHERE user_id = ? AND balance >= ?
                """, amount, userId, amount);
        if (updated != 1) throw new BusinessException(40915, "用户剩余次数不足，不能完成退款");
        int balance = jdbc.queryForObject("SELECT balance FROM ai_credit_account WHERE user_id = ?", Integer.class, userId);
        jdbc.update("""
                INSERT INTO ai_credit_ledger (user_id, change_amount, balance_after, reason, source_order_no)
                VALUES (?, ?, ?, ?, ?)
                """, userId, -amount, balance, reason, orderNo);
    }

    private void restoreRefundCredits(String userId, int amount, String orderNo) {
        jdbc.update("""
                UPDATE ai_credit_account SET balance = balance + ?, version = version + 1
                WHERE user_id = ?
                """, amount, userId);
        int balance = jdbc.queryForObject("SELECT balance FROM ai_credit_account WHERE user_id = ?", Integer.class, userId);
        jdbc.update("""
                INSERT INTO ai_credit_ledger (user_id, change_amount, balance_after, reason, source_order_no)
                VALUES (?, ?, ?, 'refund_release', ?)
                """, userId, amount, balance, orderNo);
    }

    private Map<String, Object> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        if (rows.isEmpty()) throw new BusinessException(40401, "记录不存在");
        return new LinkedHashMap<>(rows.get(0));
    }

    private Map<String, Object> oneForUpdate(String sql, Object... args) { return one(sql, args); }
    private int intValue(Object value) { return value instanceof Number number ? number.intValue() : 0; }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(); }
    private String channelName(String value) { return "wechat".equals(value) ? "微信" : "支付宝"; }
    private String requiredReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.length() < 2 || reason.length() > 200) throw new BusinessException(40001, "退款原因需为 2-200 个字符");
        return reason;
    }
    private String requiredGrantReason(String value) {
        String reason = value == null ? "" : value.trim();
        if (reason.length() < 2 || reason.length() > 200) {
            throw new BusinessException(40001, "赠送原因需为 2-200 个字符");
        }
        return reason;
    }
    private String number(String prefix) {
        return prefix + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
