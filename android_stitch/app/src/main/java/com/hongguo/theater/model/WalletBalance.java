package com.hongguo.theater.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

public class WalletBalance {

    @SerializedName("coins")
    private int coins;

    @SerializedName("currency_name")
    private String currencyName;

    @SerializedName("coins_per_yuan")
    private int coinsPerYuan;

    @SerializedName("balance_yuan")
    private double balanceYuan;

    @SerializedName("ad_skip_expires_at")
    private String adSkipExpiresAt;

    @SerializedName("ad_skip_active")
    private boolean adSkipActive;

    @SerializedName("ad_skip_remaining")
    private int adSkipRemaining;

    public int getAdSkipRemaining() {
        return adSkipRemaining;
    }

    public int getCoins() {
        return coins;
    }

    public String getCurrencyName() {
        return currencyName != null ? currencyName : "金币";
    }

    public int getCoinsPerYuan() {
        return coinsPerYuan > 0 ? coinsPerYuan : 100;
    }

    public double getBalanceYuan() {
        return balanceYuan;
    }

    @Nullable
    public String getAdSkipExpiresAt() {
        return adSkipExpiresAt;
    }

    public boolean isAdSkipActive() {
        return adSkipActive;
    }
}
