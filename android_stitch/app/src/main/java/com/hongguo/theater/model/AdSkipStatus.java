package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

public class AdSkipStatus {

    public static class Config {
        @SerializedName("id")
        private long id;

        @SerializedName("name")
        private String name;

        @SerializedName("package_type")
        private String packageType;

        @SerializedName("duration_hours")
        private int durationHours;

        @SerializedName("skip_count")
        private int skipCount;

        @SerializedName("price_coins")
        private int priceCoins;

        public long getId() {
            return id;
        }

        public String getName() {
            return name != null ? name : "";
        }

        public String getPackageType() {
            return packageType != null ? packageType : "time";
        }

        public int getDurationHours() {
            return durationHours;
        }

        public int getSkipCount() {
            return skipCount;
        }

        public int getPriceCoins() {
            return priceCoins;
        }
    }

    @SerializedName("configs")
    private List<Config> configs;

    @SerializedName("time_configs")
    private List<Config> timeConfigs;

    @SerializedName("booster_configs")
    private List<Config> boosterConfigs;

    @SerializedName("ad_skip_active")
    private boolean adSkipActive;

    @SerializedName("ad_skip_expires_at")
    private String adSkipExpiresAt;

    @SerializedName("ad_skip_remaining")
    private int adSkipRemaining;

    @SerializedName("coins")
    private int coins;

    public List<Config> getConfigs() {
        return configs != null ? configs : Collections.emptyList();
    }

    /** 时间包；若后端未分字段，退化为 {@link #getConfigs()} */
    public List<Config> getTimeConfigs() {
        if (timeConfigs != null && !timeConfigs.isEmpty()) {
            return timeConfigs;
        }
        return getConfigs();
    }

    public List<Config> getBoosterConfigs() {
        return boosterConfigs != null ? boosterConfigs : Collections.emptyList();
    }

    public boolean isAdSkipActive() {
        return adSkipActive;
    }

    public String getAdSkipExpiresAt() {
        return adSkipExpiresAt != null ? adSkipExpiresAt : "";
    }

    public int getCoins() {
        return coins;
    }

    public int getAdSkipRemaining() {
        return adSkipRemaining;
    }
}
