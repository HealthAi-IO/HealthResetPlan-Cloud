package io.healthresetplan.modules.membership.gateway;

import java.util.Map;

/**
 * 支付网关抽象，多渠道共用此接口。
 *
 * <p>当前支持渠道（待实现）：wechat / alipay / apple_iap</p>
 * <p>各渠道在 {@code modules/membership/gateway/impl/} 下实现。</p>
 */
public interface PaymentGateway {

    /** 渠道标识，与 payment_order.channel 一致 */
    String channel();

    /**
     * 发起预支付，返回给客户端用于唤起支付 UI 的凭证（二维码 URL / prepay_id / 签名参数等）。
     */
    PrepayResult prepay(PrepayRequest request);

    /**
     * 校验支付回调签名，防伪造。
     */
    boolean verifyCallback(Map<String, String> headers, String body);

    /**
     * 解析回调报文，提取渠道订单号与支付状态。
     */
    CallbackResult parseCallback(Map<String, String> headers, String body);

    record PrepayRequest(
            String orderNo,
            String userId,
            int amountFen,
            String subject,
            String notifyUrl
    ) {}

    record PrepayResult(
            /** 支付凭证（JSON 序列化后直接透传给客户端） */
            Map<String, Object> credential
    ) {}

    record CallbackResult(
            String channelOrderNo,
            boolean paid
    ) {}
}
