package com.hongguo.theater.model;

import com.google.gson.annotations.SerializedName;

public class User {

    @SerializedName("id")
    private long id;

    @SerializedName("username")
    private String username;

    @SerializedName("nickname")
    private String nickname;

    @SerializedName("avatar")
    private String avatar;

    @SerializedName("coins")
    private int coins;

    public long getId() { return id; }
    public String getUsername() { return username; }
    public String getNickname() { return nickname; }
    public String getAvatar() { return avatar; }
    public int getCoins() { return coins; }

    public String getDisplayName() {
        return nickname != null && !nickname.isEmpty() ? nickname : username;
    }
}
