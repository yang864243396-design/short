package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class Episode {

    @SerializedName("id")
    private long id;

    @SerializedName("drama_id")
    private long dramaId;

    @SerializedName("episode_number")
    private int episodeNumber;

    @SerializedName("title")
    private String title;

    @SerializedName("video_url")
    private String videoUrl;

    @SerializedName("is_free")
    private boolean isFree;

    @SerializedName("like_count")
    private long likeCount;

    @SerializedName("comment_count")
    private long commentCount;

    @SerializedName("view_count")
    private long viewCount;

    @SerializedName("stream_url")
    private String streamUrl;

    // For feed items
    @SerializedName("drama")
    private Drama drama;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getDramaId() { return dramaId; }
    public void setDramaId(long dramaId) { this.dramaId = dramaId; }

    public int getEpisodeNumber() { return episodeNumber; }
    public void setEpisodeNumber(int episodeNumber) { this.episodeNumber = episodeNumber; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }

    public boolean isFree() { return isFree; }

    public long getLikeCount() { return likeCount; }
    public void setLikeCount(long likeCount) { this.likeCount = likeCount; }

    public long getCommentCount() { return commentCount; }
    public void setCommentCount(long commentCount) { this.commentCount = commentCount; }
    public long getViewCount() { return viewCount; }

    public String getStreamUrl() { return streamUrl; }
    public void setStreamUrl(String streamUrl) { this.streamUrl = streamUrl; }

    public Drama getDrama() { return drama; }
    public void setDrama(Drama drama) { this.drama = drama; }

    public String getLikeCountText() {
        if (likeCount >= 10000) {
            return String.format("%.1fw", likeCount / 10000.0);
        }
        return String.valueOf(likeCount);
    }

    public String getCommentCountText() {
        if (commentCount >= 10000) {
            return String.format("%.1fw", commentCount / 10000.0);
        }
        return String.valueOf(commentCount);
    }
}
