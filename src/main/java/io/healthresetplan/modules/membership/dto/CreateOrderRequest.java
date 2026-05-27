package io.healthresetplan.modules.membership.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CreateOrderRequest {

    @NotBlank(message = "套餐码不能为空")
    @Pattern(regexp = "^(monthly|yearly)$", message = "planCode 仅支持 monthly 或 yearly")
    private String planCode;

    @NotBlank(message = "支付渠道不能为空")
    @Pattern(regexp = "^(wechat|alipay|apple_iap)$", message = "channel 仅支持 wechat / alipay / apple_iap")
    private String channel;

    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
}
