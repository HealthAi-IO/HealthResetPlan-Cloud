package io.healthresetplan.modules.membership.dto;

import java.util.Map;

public class CreateOrderResponse {

    private String orderNo;
    private String planCode;
    private int amountFen;
    private String channel;
    /** 透传给客户端，用于唤起支付 UI；网关未接入时为 null */
    private Map<String, Object> payCredential;

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getPlanCode() { return planCode; }
    public void setPlanCode(String planCode) { this.planCode = planCode; }
    public int getAmountFen() { return amountFen; }
    public void setAmountFen(int amountFen) { this.amountFen = amountFen; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Map<String, Object> getPayCredential() { return payCredential; }
    public void setPayCredential(Map<String, Object> payCredential) { this.payCredential = payCredential; }
}
