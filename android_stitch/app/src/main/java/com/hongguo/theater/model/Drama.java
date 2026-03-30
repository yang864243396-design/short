package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;
import com.hongguo.theater.BuildConfig;

public class Drama {

    @SerializedName("id")
    private long id;

    @SerializedName("title")
    private String title;

    @SerializedName("description")
    private String description;

    @SerializedName("cover_url")
    private String coverUrl;

    @SerializedName("category")
    private String category;

    @SerializedName("tags")
    private String tags;

    @SerializedName("total_episodes")
    private int totalEpisodes;

    @SerializedName("current_episode")
    private int currentEpisode;

    @SerializedName("rating")
    private float rating;

    @SerializedName("heat")
    private long heat;

    @SerializedName("status")
    private String status;

    @SerializedName("created_at")
    private String createdAt;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() {
        if (coverUrl != null && coverUrl.startsWith("/")) {
            String base = BuildConfig.BASE_URL.replace("/api/v1/", "");
            return base + coverUrl;
        }
        return coverUrl;
    }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public int getTotalEpisodes() { return totalEpisodes; }
    public void setTotalEpisodes(int totalEpisodes) { this.totalEpisodes = totalEpisodes; }

    public int getCurrentEpisode() { return currentEpisode; }
    public void setCurrentEpisode(int currentEpisode) { this.currentEpisode = currentEpisode; }

    public float getRating() { return rating; }
    public void setRating(float rating) { this.rating = rating; }

    public long getHeat() { return heat; }
    public void setHeat(long heat) { this.heat = heat; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }

    public String getHeatText() {
        if (heat >= 10000000) {
            return String.format("%.1fM", heat / 1000000.0);
        } else if (heat >= 10000) {
            return String.format("%.1fw", heat / 10000.0);
        } else {
            return String.valueOf(heat);
        }
    }

    public String getStatusText() {
        if ("completed".equals(status)) {
            return totalEpisodes + "集全";
        } else {
            return "更新至" + currentEpisode + "集";
        }
    }
}
