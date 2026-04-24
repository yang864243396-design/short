package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class WalletTransaction {

    @SerializedName("id")
    private long id;

    @SerializedName("user_id")
    private long userId;

    @SerializedName("type")
    private String type;

    @SerializedName("amount")
    private int amount;

    @SerializedName("balance_after")
    private int balanceAfter;

    @SerializedName("title")
    private String title;

    @SerializedName("remark")
    private String remark;

    @SerializedName("ref_type")
    private String refType;

    @SerializedName("created_at")
    private String createdAt;

    public long getId() {
        return id;
    }

    public String getType() {
        return type != null ? type : "";
    }

    public int getAmount() {
        return amount;
    }

    public int getBalanceAfter() {
        return balanceAfter;
    }

    public String getTitle() {
        return title != null ? title : "";
    }

    public String getRemark() {
        return remark != null ? remark : "";
    }

    public String getRefType() {
        return refType != null ? refType : "";
    }

    public String getCreatedAt() {
        return createdAt != null ? createdAt : "";
    }
}
