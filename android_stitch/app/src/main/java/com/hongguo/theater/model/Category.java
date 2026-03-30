package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class Category {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("sort")
    private int sort;

    public long getId() { return id; }
    public String getName() { return name; }
    public int getSort() { return sort; }
}
