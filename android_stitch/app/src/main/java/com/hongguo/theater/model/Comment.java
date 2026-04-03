package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class Comment {

    @SerializedName("id")
    private long id;

    @SerializedName("user_id")
    private long userId;

    @SerializedName("nickname")
    private String username;

    @SerializedName("avatar")
    private String avatar;

    @SerializedName("content")
    private String content;

    @SerializedName("likes_count")
    private int likeCount;

    @SerializedName("liked")
    private boolean isLiked;

    @SerializedName("created_at")
    private String createdAt;

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getAvatar() { return avatar; }
    public String getContent() { return content; }
    public int getLikeCount() { return likeCount; }
    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public String getCreatedAt() { return createdAt; }

    public String getLikeCountText() {
        if (likeCount >= 1000) {
            return String.format("%.1fk", likeCount / 1000.0);
        }
        return String.valueOf(likeCount);
    }
}
