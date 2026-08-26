package io.healthresetplan.modules.membership;

import io.healthresetplan.common.exception.BusinessException;
import io.healthresetplan.modules.payment.PaymentService;
import org.springframework.stereotype.Service;

/**
 * Central billing/credit boundary for AI features.
 */
@Service
public class MembershipService {

    private final PaymentService paymentService;
    private final AiEntitlementService entitlementService;

    public MembershipService(PaymentService paymentService, AiEntitlementService entitlementService) {
        this.paymentService = paymentService;
        this.entitlementService = entitlementService;
    }

    public boolean hasFeature(String userId, String feature) {
        return userId != null && !userId.isBlank()
                && (entitlementService.hasIncluded(userId, feature)
                || paymentService.hasAvailable(userId));
    }

    public boolean billingEnabled() { return true; }

    public boolean consume(String userId, String feature) {
        return entitlementService.consumeIncluded(userId, feature)
                || paymentService.consume(userId, feature);
    }

    public void requireCredit(String userId, String feature) {
        if (!hasFeature(userId, feature)) {
            throw new BusinessException(42903, "AI 健康权益已用完，请充值后继续使用");
        }
    }
}
