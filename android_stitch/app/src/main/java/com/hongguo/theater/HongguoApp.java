package com.hongguo.theater;

import android.app.Application;

import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.utils.ExoPlayerCache;

public class HongguoApp extends Application {

    private static HongguoApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        ApiClient.init(this);
        ExoPlayerCache.init(this);
    }

    public static HongguoApp getInstance() {
        return instance;
    }
}
