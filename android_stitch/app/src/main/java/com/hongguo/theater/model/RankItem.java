package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class RankItem {

    @SerializedName("rank")
    private int rank;

    @SerializedName("drama")
    private Drama drama;

    @SerializedName("heat")
    private long heat;

    @SerializedName("badge")
    private String badge;

    @SerializedName("total_likes")
    private long totalLikes;

    public int getRank() { return rank; }
    public Drama getDrama() { return drama; }
    public long getHeat() { return heat; }
    public String getBadge() { return badge; }
    public long getTotalLikes() { return totalLikes; }

    public String getHeatText() {
        if (heat >= 10000) {
            return String.format("%.1fw", heat / 10000.0);
        }
        return String.valueOf(heat);
    }

    public String getLikesText() {
        if (totalLikes >= 10000) {
            return String.format("%.1fw", totalLikes / 10000.0);
        }
        return String.valueOf(totalLikes);
    }
}
