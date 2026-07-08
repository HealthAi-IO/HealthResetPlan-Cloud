package io.healthresetplan.modules.membership;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.membership.dto.CreateOrderRequest;
import io.healthresetplan.modules.membership.dto.CreateOrderResponse;
import io.healthresetplan.modules.membership.dto.MembershipStatusResponse;
import io.healthresetplan.modules.membership.entity.MembershipPlan;
import io.healthresetplan.modules.membership.entity.PaymentOrder;
import io.healthresetplan.modules.membership.entity.UserSubscription;
import io.healthresetplan.modules.membership.gateway.PaymentGateway;
import io.healthresetplan.modules.membership.mapper.MembershipPlanMapper;
import io.healthresetplan.modules.membership.mapper.PaymentOrderMapper;
import io.healthresetplan.modules.membership.mapper.UserSubscriptionMapper;
import io.healthresetplan.modules.user.entity.UserAccount;
import io.healthresetplan.modules.user.mapper.UserAccountMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class MembershipService {

    private static final Logger log = LoggerFactory.getLogger(MembershipService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};
    private static final Map<String, String> PROMO_CODES = Map.of(
            "HEALTH30", "monthly",
            "VIP30", "monthly",
            "HEALTH365", "yearly",
            "VIP365", "yearly",
            "TRIAL7", "monthly"
    );
    private static final int APP_PAY_TEST_AMOUNT_FEN = 1;

    private final MembershipPlanMapper planMapper;
    private final UserSubscriptionMapper subscriptionMapper;
    private final PaymentOrderMapper orderMapper;
    private final UserAccountMapper accountMapper;
    /** 支持多渠道：key = channel() */
    private final Map<String, PaymentGateway> gateways;

    public MembershipService(MembershipPlanMapper planMapper,
                              UserSubscriptionMapper subscriptionMapper,
                              PaymentOrderMapper orderMapper,
                              UserAccountMapper accountMapper,
                              List<PaymentGateway> gatewayList) {
        this.planMapper = planMapper;
        this.subscriptionMapper = subscriptionMapper;
        this.orderMapper = orderMapper;
        this.accountMapper = accountMapper;
        this.gateways = gatewayList.stream()
                .collect(Collectors.toMap(PaymentGateway::channel, g -> g));
    }

    // ── 套餐列表 ──────────────────────────────────────────────

    public List<MembershipPlan> listPlans() {
        return planMapper.selectList(new LambdaQueryWrapper<MembershipPlan>()
                .eq(MembershipPlan::getStatus, 1)
                .orderByAsc(MembershipPlan::getSortOrder));
    }

    // ── 会员状态 ──────────────────────────────────────────────

    public MembershipStatusResponse getStatus(String userId) {
        UserSubscription sub = findActiveSub(userId);
        MembershipStatusResponse resp = new MembershipStatusResponse();
        if (sub == null) {
            resp.setActive(false);
            resp.setFeatures(Collections.emptyList());
            return resp;
        }
        resp.setActive(true);
        resp.setPlanCode(sub.getPlanCode());
        resp.setExpiresAt(sub.getExpiresAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        MembershipPlan plan = planMapper.selectOne(new LambdaQueryWrapper<MembershipPlan>()
                .eq(MembershipPlan::getCode, sub.getPlanCode()));
        if (plan != null) {
            resp.setPlanName(plan.getName());
            resp.setFeatures(parseFeatures(plan.getFeatures()));
        }
        return resp;
    }

    /** 判断用户是否拥有云同步权益（实时查询，比 has_cloud_sync 字段更准确）。 */
    public boolean hasCloudSync(String userId) {
        // 应用市场上架版本暂时关闭会员权益限制，所有登录用户均可使用。
        return true;
    }

    public boolean hasFeature(String userId, String feature) {
        // 应用市场上架版本暂时关闭会员权益限制，所有登录用户均可使用。
        return true;
    }

    // ── 创建支付订单 ──────────────────────────────────────────

    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest req, String userId) {
        MembershipPlan plan = planMapper.selectOne(new LambdaQueryWrapper<MembershipPlan>()
                .eq(MembershipPlan::getCode, req.getPlanCode())
                .eq(MembershipPlan::getStatus, 1));
        if (plan == null) {
            throw new BusinessException(40401, "套餐不存在或已下架");
        }

        String orderNo = generateOrderNo(userId);
        int amountFen = ("wechat".equals(req.getChannel()) || "alipay".equals(req.getChannel()))
                ? APP_PAY_TEST_AMOUNT_FEN
                : plan.getPriceFen();
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPlanCode(req.getPlanCode());
        order.setAmountFen(amountFen);
        order.setChannel(req.getChannel());
        order.setChannelOrderNo("");
        order.setStatus("pending");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(order);

        CreateOrderResponse resp = new CreateOrderResponse();
        resp.setOrderNo(orderNo);
        resp.setPlanCode(req.getPlanCode());
        resp.setAmountFen(amountFen);
        resp.setChannel(req.getChannel());

        PaymentGateway gateway = gateways.get(req.getChannel());
        if (gateway != null) {
            try {
                PaymentGateway.PrepayResult result = gateway.prepay(new PaymentGateway.PrepayRequest(
                        orderNo, userId, amountFen, plan.getName(), ""));
                resp.setPayCredential(result.credential());
            } catch (Exception e) {
                log.error("预支付调用失败 channel={} orderNo={}", req.getChannel(), orderNo, e);
            }
        } else {
            log.warn("支付网关未接入 channel={}", req.getChannel());
        }

        return resp;
    }

    // ── 支付回调处理 ──────────────────────────────────────────

    @Transactional
    public void handleCallback(String channel, Map<String, String> headers, String body) {
        PaymentGateway gateway = gateways.get(channel);
        if (gateway == null) {
            log.warn("收到未知渠道回调 channel={}", channel);
            return;
        }
        if (!gateway.verifyCallback(headers, body)) {
            log.warn("支付回调签名校验失败 channel={}", channel);
            throw new BusinessException(40301, "回调签名校验失败");
        }

        PaymentGateway.CallbackResult result = gateway.parseCallback(headers, body);
        if (!result.paid()) return;

        PaymentOrder order = orderMapper.selectOne(new LambdaQueryWrapper<PaymentOrder>()
                .eq(PaymentOrder::getChannel, channel)
                .eq(PaymentOrder::getOrderNo, result.orderNo()));
        if (order == null || !"pending".equals(order.getStatus())) return;
        if (result.amountFen() != null && !result.amountFen().equals(order.getAmountFen())) {
            log.warn("支付回调金额不匹配 channel={} orderNo={}", channel, result.orderNo());
            throw new BusinessException(40003, "支付金额不匹配");
        }

        order.setStatus("paid");
        order.setChannelOrderNo(result.channelOrderNo());
        order.setCallbackRaw(body);
        order.setPaidAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderMapper.updateById(order);

        activateSubscription(order.getUserId(), order.getPlanCode(), channel, order.getOrderNo());
    }

    // ── 管理员直接授权（测试 / 兑换码等场景）────────────────────

    @Transactional
    public void adminGrant(String userId, String planCode) {
        MembershipPlan plan = planMapper.selectOne(new LambdaQueryWrapper<MembershipPlan>()
                .eq(MembershipPlan::getCode, planCode));
        if (plan == null) {
            throw new BusinessException(40401, "套餐不存在");
        }
        activateSubscription(userId, planCode, "admin", "ADMIN-GRANT");
    }

    // ── 定时过期检查（每小时）────────────────────────────────────

    @Transactional
    public MembershipStatusResponse redeemCode(String userId, String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        String planCode = PROMO_CODES.get(normalized);
        if (planCode == null) {
            throw new BusinessException(40002, "激活码无效");
        }

        String orderNo = "PROMO-" + normalized;
        activateSubscription(userId, planCode, "promo", orderNo);
        return getStatus(userId);
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void expireOverdue() {
        List<UserSubscription> overdue = subscriptionMapper.selectList(
                new LambdaQueryWrapper<UserSubscription>()
                        .eq(UserSubscription::getStatus, "active")
                        .lt(UserSubscription::getExpiresAt, LocalDateTime.now()));

        if (overdue.isEmpty()) return;

        for (UserSubscription sub : overdue) {
            sub.setStatus("expired");
            sub.setUpdatedAt(LocalDateTime.now());
            subscriptionMapper.updateById(sub);

            // 过期后若无其他有效订阅，关闭云同步开关
            if (findActiveSub(sub.getUserId()) == null) {
                accountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                        .eq(UserAccount::getUserId, sub.getUserId())
                        .set(UserAccount::getHasCloudSync, 0));
                log.info("会员过期，已关闭云同步 userId={}", sub.getUserId());
            }
        }
    }

    // ── 内部 ──────────────────────────────────────────────────

    private void activateSubscription(String userId, String planCode,
                                      String channel, String orderNo) {
        MembershipPlan plan = planMapper.selectOne(new LambdaQueryWrapper<MembershipPlan>()
                .eq(MembershipPlan::getCode, planCode));
        if (plan == null) {
            log.error("套餐不存在 planCode={} userId={}", planCode, userId);
            throw new BusinessException(40401, "套餐不存在或已下架，请联系客服");
        }

        LocalDateTime now = LocalDateTime.now();
        // 续费时从当前有效期末尾顺延
        UserSubscription existing = findActiveSub(userId);
        LocalDateTime startsAt = (existing != null) ? existing.getExpiresAt() : now;
        LocalDateTime expiresAt = startsAt.plusDays(plan.getDurationDays());

        UserSubscription sub = new UserSubscription();
        sub.setUserId(userId);
        sub.setPlanCode(planCode);
        sub.setStatus("active");
        sub.setStartsAt(startsAt);
        sub.setExpiresAt(expiresAt);
        sub.setPaymentChannel(channel);
        sub.setPaymentOrderNo(orderNo);
        sub.setCreatedAt(now);
        sub.setUpdatedAt(now);
        subscriptionMapper.insert(sub);

        // 激活云同步开关
        accountMapper.update(null, new LambdaUpdateWrapper<UserAccount>()
                .eq(UserAccount::getUserId, userId)
                .set(UserAccount::getHasCloudSync, 1));

        log.info("会员激活 userId={} plan={} expires={}", userId, planCode, expiresAt);
    }

    private UserSubscription findActiveSub(String userId) {
        return subscriptionMapper.selectOne(new LambdaQueryWrapper<UserSubscription>()
                .eq(UserSubscription::getUserId, userId)
                .eq(UserSubscription::getStatus, "active")
                .gt(UserSubscription::getExpiresAt, LocalDateTime.now())
                .orderByDesc(UserSubscription::getExpiresAt)
                .last("LIMIT 1"));
    }

    private List<String> parseFeatures(String featuresJson) {
        try {
            return MAPPER.readValue(featuresJson, LIST_TYPE);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String generateOrderNo(String userId) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String uid = userId.substring(0, Math.min(6, userId.length()));
        int rand = ThreadLocalRandom.current().nextInt(100000, 999999);
        return "HRP" + ts + uid + rand;
    }
}
