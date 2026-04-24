package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class PayProductItem {
    @SerializedName("id")
    private long id;
    @SerializedName("product_id")
    private String productId;
    @SerializedName("name")
    private String name;
    @SerializedName("enabled")
    private boolean enabled;
    @SerializedName("sort")
    private int sort;

    public long getId() {
        return id;
    }

    public String getProductId() {
        return productId != null ? productId : "";
    }

    public String getName() {
        return name != null ? name : "";
    }

    public boolean isEnabled() {
        return enabled;
    }
}
