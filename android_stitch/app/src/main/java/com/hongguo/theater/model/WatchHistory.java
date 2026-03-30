package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class WatchHistory {

    @SerializedName("drama")
    private Drama drama;

    @SerializedName("last_episode")
    private int lastEpisode;

    @SerializedName("progress")
    private float progress;

    @SerializedName("is_finished")
    private boolean isFinished;

    @SerializedName("updated_at")
    private String updatedAt;

    public Drama getDrama() { return drama; }
    public int getLastEpisode() { return lastEpisode; }
    public float getProgress() { return progress; }
    public boolean isFinished() { return isFinished; }
    public String getUpdatedAt() { return updatedAt; }

    public String getProgressText() {
        if (isFinished) {
            return "已看完";
        }
        return String.format("看到 %02d集", lastEpisode);
    }
}
