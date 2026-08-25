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

    public MembershipService(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    public boolean hasFeature(String userId, String feature) {
        return userId != null && !userId.isBlank() && paymentService.hasAvailable(userId);
    }

    public boolean billingEnabled() { return true; }

    public boolean consume(String userId, String feature) {
        return paymentService.consume(userId, feature);
    }

    public void requireCredit(String userId, String feature) {
        if (!hasFeature(userId, feature)) {
            throw new BusinessException(42903, "AI 健康权益已用完，请充值后继续使用");
        }
    }
}
