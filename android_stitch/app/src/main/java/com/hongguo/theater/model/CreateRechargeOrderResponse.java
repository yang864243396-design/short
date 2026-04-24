package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class CreateRechargeOrderResponse {

    @SerializedName("order")
    private RechargeOrderItem order;

    @SerializedName("coins")
    private int coins;

    @SerializedName("pay_url")
    private String payUrl;

    @SerializedName("mch_order_no")
    private String mchOrderNo;

    public RechargeOrderItem getOrder() {
        return order;
    }

    public int getCoins() {
        return coins;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public String getMchOrderNo() {
        return mchOrderNo;
    }
}
