package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class Banner {

    @SerializedName("id")
    private long id;

    @SerializedName("title")
    private String title;

    @SerializedName("image_url")
    private String imageUrl;

    @SerializedName("link_type")
    private String linkType; // "url" or "drama"

    @SerializedName("link_url")
    private String linkUrl;

    @SerializedName("drama_id")
    private long dramaId;

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getImageUrl() { return imageUrl; }
    public String getLinkType() { return linkType; }
    public String getLinkUrl() { return linkUrl; }
    public long getDramaId() { return dramaId; }

    public boolean isDramaLink() {
        return "drama".equals(linkType);
    }
}
