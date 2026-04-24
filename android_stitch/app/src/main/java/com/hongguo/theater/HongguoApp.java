package com.hongguo.theater;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.utils.AdPreloader;
import com.hongguo.theater.utils.AdSkipCache;
import com.hongguo.theater.utils.ExoPlayerCache;

import java.lang.ref.WeakReference;

public class HongguoApp extends Application {

    private static HongguoApp instance;
    private static WeakReference<Activity> currentActivityRef;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(@NonNull Activity activity) {
                currentActivityRef = new WeakReference<>(activity);
            }
            @Override public void onActivityPaused(@NonNull Activity activity) {
                if (currentActivityRef != null && currentActivityRef.get() == activity) {
                    currentActivityRef = null;
                }
            }
            @Override public void onActivityCreated(@NonNull Activity a, @Nullable Bundle s) {}
            @Override public void onActivityStarted(@NonNull Activity a) {}
            @Override public void onActivityStopped(@NonNull Activity a) {}
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle o) {}
            @Override public void onActivityDestroyed(@NonNull Activity a) {}
        });
        ApiClient.init(this);
        ExoPlayerCache.init(this);
        // 主线程空闲后再拉广告与预缓存，减轻与首页 /banners、/home、/dramas 并发争用
        Looper.myQueue().addIdleHandler(() -> {
            AdPreloader.warmupThenPreload(HongguoApp.this);
            AdSkipCache.prefetchIfStale(HongguoApp.this);
            return false;
        });
    }

    public static HongguoApp getInstance() {
        return instance;
    }

    @Nullable
    public static Activity getCurrentActivity() {
        return currentActivityRef != null ? currentActivityRef.get() : null;
    }
}
