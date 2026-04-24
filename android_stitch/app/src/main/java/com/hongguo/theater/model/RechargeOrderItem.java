package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class RechargeOrderItem {

    @SerializedName("id")
    private long id;

    @SerializedName("package_id")
    private long packageId;

    @SerializedName("coins")
    private int coins;

    @SerializedName("status")
    private String status;

    @SerializedName("mch_order_no")
    private String mchOrderNo;

    /** 聚合平台支付单号；台方后台展示的常与 mch 不同，查单可单独传此字段 */
    @SerializedName("pay_order_id")
    private String payOrderId;

    public long getId() {
        return id;
    }

    public long getPackageId() {
        return packageId;
    }

    public int getCoins() {
        return coins;
    }

    public String getStatus() {
        return status != null ? status : "";
    }

    public String getMchOrderNo() {
        return mchOrderNo != null ? mchOrderNo : "";
    }

    public String getPayOrderId() {
        return payOrderId != null ? payOrderId : "";
    }
}
