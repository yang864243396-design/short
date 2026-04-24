package com.hongguo.theater.model;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.annotations.SerializedName;
import com.hongguo.theater.utils.ImageUrlUtils;

import java.util.ArrayList;
import java.util.List;

public class Drama {

    private static final int MAX_CATEGORY_TAGS = 3;

    /**
     * 由 {@link JsonElement} 安全解析，避免接口字段类型波动导致 Gson 整对象反序列化失败、播放页闪退。
     */
    public static final class DramaRankingInfo {
        private final String list;
        private final int rank;

        private DramaRankingInfo(String list, int rank) {
            this.list = list != null ? list : "";
            this.rank = rank;
        }

        @Nullable
        static DramaRankingInfo tryParse(@Nullable JsonElement el) {
            if (el == null || el.isJsonNull() || !el.isJsonObject()) {
                return null;
            }
            try {
                JsonObject o = el.getAsJsonObject();
                String list = null;
                if (o.has("list") && !o.get("list").isJsonNull()) {
                    JsonElement le = o.get("list");
                    if (le.isJsonPrimitive()) {
                        list = le.getAsString();
                    }
                }
                int rank = 0;
                if (o.has("rank") && !o.get("rank").isJsonNull()) {
                    rank = parseRankValue(o.get("rank"));
                }
                if (TextUtils.isEmpty(list) || rank <= 0) {
                    return null;
                }
                return new DramaRankingInfo(list, rank);
            } catch (RuntimeException e) {
                return null;
            }
        }

        private static int parseRankValue(JsonElement r) {
            try {
                if (r.isJsonPrimitive()) {
                    JsonPrimitive p = r.getAsJsonPrimitive();
                    if (p.isNumber()) {
                        return (int) Math.round(p.getAsDouble());
                    }
                    if (p.isString()) {
                        return Integer.parseInt(p.getAsString().trim());
                    }
                }
            } catch (RuntimeException ignored) {
            }
            return 0;
        }

        public String getList() {
            return list;
        }

        public int getRank() {
            return rank;
        }
    }

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

    /** 与 {@link #category} 一致，拆成独立项供 UI 展示 */
    @SerializedName("category_list")
    private List<String> categoryList;

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

    @SerializedName("ranking")
    private JsonElement rankingElement;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCoverUrl() {
        return ImageUrlUtils.resolve(coverUrl);
    }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    @Nullable
    public List<String> getCategoryList() { return categoryList; }
    public void setCategoryList(List<String> categoryList) { this.categoryList = categoryList; }

    /**
     * 列表/卡片用：独立标签文案，优先 {@code category_list}，否则将 {@code category} 按常见分隔符拆分，最多 3 条。
     */
    @NonNull
    public List<String> getCategoryLabelsForDisplay() {
        List<String> out = new ArrayList<>();
        if (categoryList != null) {
            for (String s : categoryList) {
                if (s == null) continue;
                String t = s.trim();
                if (t.isEmpty()) continue;
                out.add(t);
                if (out.size() >= MAX_CATEGORY_TAGS) {
                    return out;
                }
            }
        }
        if (!TextUtils.isEmpty(category)) {
            for (String part : category.split("[,，、/|]+")) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                out.add(t);
                if (out.size() >= MAX_CATEGORY_TAGS) {
                    return out;
                }
            }
        }
        return out;
    }

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

    public DramaRankingInfo getRanking() {
        return DramaRankingInfo.tryParse(rankingElement);
    }

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
            return "更新至" + totalEpisodes + "集";
        }
    }
}
