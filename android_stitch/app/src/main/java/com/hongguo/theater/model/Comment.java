package com.hongguo.theater.model;

import android.text.TextUtils;

import com.google.gson.annotations.SerializedName;

import com.hongguo.theater.utils.FormatUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Comment {

    @SerializedName("id")
    private long id;

    @SerializedName("user_id")
    private long userId;

    @SerializedName("parent_id")
    private long parentId;

    @SerializedName("reply_to_user_id")
    private long replyToUserId;

    @SerializedName("nickname")
    private String username;

    @SerializedName("avatar")
    private String avatar;

    @SerializedName("reply_to_nickname")
    private String replyToNickname;

    @SerializedName("content")
    private String content;

    @SerializedName("likes_count")
    private int likeCount;

    @SerializedName("liked")
    private boolean isLiked;

    @SerializedName("created_at")
    private String createdAt;

    @SerializedName("time_ago")
    private String timeAgo;

    @SerializedName("replies")
    private List<Comment> replies;

    @SerializedName("reply_count")
    private int replyCount;

    @SerializedName("has_more_replies")
    private boolean hasMoreReplies;

    public long getId() { return id; }
    public long getUserId() { return userId; }
    public long getParentId() { return parentId; }
    public long getReplyToUserId() { return replyToUserId; }
    public String getUsername() { return username; }
    public String getAvatar() { return avatar; }
    public String getReplyToNickname() { return replyToNickname; }
    public String getContent() { return content; }
    public int getLikeCount() { return likeCount; }
    public boolean isLiked() { return isLiked; }
    public void setLiked(boolean liked) { isLiked = liked; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }
    public String getCreatedAt() { return createdAt; }

    public List<Comment> getReplies() {
        return replies != null ? replies : Collections.emptyList();
    }

    public int getReplyCount() {
        return replyCount;
    }

    public void setReplyCount(int replyCount) {
        this.replyCount = replyCount;
    }

    public boolean isHasMoreReplies() {
        return hasMoreReplies;
    }

    public void setHasMoreReplies(boolean hasMoreReplies) {
        this.hasMoreReplies = hasMoreReplies;
    }

    /** 分页追加回复到内存列表（与接口返回的 replies 合并） */
    public void appendRepliesLocal(List<Comment> more) {
        if (more == null || more.isEmpty()) return;
        if (replies == null) {
            replies = new ArrayList<>();
        } else if (!(replies instanceof ArrayList)) {
            replies = new ArrayList<>(replies);
        }
        ((ArrayList<Comment>) replies).addAll(more);
    }

    /**
     * 本地插入一条回复到 {@link #replies}，位置紧跟在被回复项之后。
     * 回复主评时 {@code afterCommentId} 为主评 id，插在子回复列表首位（与列表 UI 一致）。
     */
    public void insertReplyLocalAfter(long afterCommentId, Comment newReply) {
        if (newReply == null) return;
        if (replies == null) {
            replies = new ArrayList<>();
        } else if (!(replies instanceof ArrayList)) {
            replies = new ArrayList<>(replies);
        }
        ArrayList<Comment> list = (ArrayList<Comment>) replies;
        if (afterCommentId == getId()) {
            list.add(0, newReply);
        } else {
            int idx = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).getId() == afterCommentId) {
                    idx = i;
                    break;
                }
            }
            if (idx < 0) {
                list.add(newReply);
            } else {
                list.add(idx + 1, newReply);
            }
        }
        setReplyCount(Math.max(replyCount, list.size()));
    }

    public String getDisplayTime() {
        if (!TextUtils.isEmpty(timeAgo)) {
            return timeAgo;
        }
        return FormatUtils.formatTimeAgo(createdAt);
    }

    public String getLikeCountText() {
        if (likeCount >= 1000) {
            return String.format("%.1fk", likeCount / 1000.0);
        }
        return String.valueOf(likeCount);
    }
}
