package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

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

    @SerializedName("video_path")
    private String videoPath;

    @SerializedName("is_free")
    private boolean isFree;

    /** 非免费集：后台设定的永久解锁所需金币 */
    @SerializedName("unlock_coins")
    private int unlockCoins;

    /** 当前登录用户是否已金币永久解锁（仅 GET /dramas/:id/episodes 在带 Token 时有意义） */
    @SerializedName("coin_unlocked")
    private boolean coinUnlocked;

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

    public String getVideoPath() { return videoPath; }
    public void setVideoPath(String videoPath) { this.videoPath = videoPath; }

    /** 后台已上传本地/存储路径，或接口已下发可播流地址 */
    public boolean hasPlayableVideo() {
        if (videoPath != null && !videoPath.trim().isEmpty()) {
            return true;
        }
        if (streamUrl != null && !streamUrl.trim().isEmpty()) {
            return true;
        }
        if (videoUrl != null && !videoUrl.trim().isEmpty()) {
            return true;
        }
        return false;
    }

    /** 仅保留已上传视频的分集，供播放页选集与切集 */
    public static List<Episode> onlyPlayable(List<Episode> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        List<Episode> out = new ArrayList<>(list.size());
        for (Episode e : list) {
            if (e.hasPlayableVideo()) {
                out.add(e);
            }
        }
        return out;
    }

    public boolean isFree() { return isFree; }

    public int getUnlockCoins() { return unlockCoins; }

    public void setUnlockCoins(int unlockCoins) { this.unlockCoins = unlockCoins; }

    public boolean isCoinUnlocked() { return coinUnlocked; }

    public void setCoinUnlocked(boolean coinUnlocked) { this.coinUnlocked = coinUnlocked; }

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
