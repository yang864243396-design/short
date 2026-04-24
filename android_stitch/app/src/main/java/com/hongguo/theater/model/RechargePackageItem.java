package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class RechargePackageItem {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("coins")
    private int coins;

    @SerializedName("bonus_coins")
    private int bonusCoins;

    @SerializedName("price_yuan")
    private double priceYuan;

    public long getId() {
        return id;
    }

    public String getName() {
        return name != null ? name : "";
    }

    public int getCoins() {
        return coins;
    }

    public int getBonusCoins() {
        return bonusCoins;
    }

    public double getPriceYuan() {
        return priceYuan;
    }
}
