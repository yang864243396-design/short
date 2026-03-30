package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class HomeData {

    @SerializedName("banners")
    private List<Drama> banners;

    @SerializedName("must_watch")
    private List<Drama> mustWatch;

    @SerializedName("recommend")
    private List<Drama> recommend;

    @SerializedName("hot_ranking")
    private List<RankItem> hotRanking;

    @SerializedName("categories")
    private List<Category> categories;

    public List<Drama> getBanners() { return banners; }
    public List<Drama> getMustWatch() { return mustWatch; }
    public List<Drama> getRecommend() { return recommend; }
    public List<RankItem> getHotRanking() { return hotRanking; }
    public List<Category> getCategories() { return categories; }
}
