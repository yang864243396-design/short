package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class HotSearch {

    @SerializedName("rank")
    private int rank;

    @SerializedName("keyword")
    private String keyword;

    @SerializedName("heat")
    private long heat;

    @SerializedName("badge")
    private String badge;

    public int getRank() { return rank; }
    public String getKeyword() { return keyword; }
    public long getHeat() { return heat; }
    public String getBadge() { return badge; }

    public String getHeatText() {
        if (heat >= 10000) {
            return String.format("%.1fw", heat / 10000.0);
        }
        return String.valueOf(heat);
    }
}
