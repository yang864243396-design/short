package com.hongguo.theater.model;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.Collections;
import java.util.List;

@JsonAdapter(CommentPageJsonAdapter.class)
public class CommentPage {

    @SerializedName("list")
    private List<Comment> list;

    @SerializedName("has_more")
    private boolean hasMore;

    public CommentPage() {
    }

    public CommentPage(List<Comment> list, boolean hasMore) {
        this.list = list;
        this.hasMore = hasMore;
    }

    public List<Comment> getList() {
        return list != null ? list : Collections.emptyList();
    }

    public boolean isHasMore() {
        return hasMore;
    }
}
