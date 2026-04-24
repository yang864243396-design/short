package com.hongguo.theater.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 广告预加载：仅未登录路径使用；登录后全屏片头必带分集 id，预取 {@code getAdVideo(null)} 不会被使用，故不请求以省流量与无效广告拉取。
 */
public class AdPreloader {

    private static final String TAG = "AdPreloader";

    private static String cachedVideoUrl;
    /** 预加载完成后的绝对 URL：视频流或图片 */
    private static String cachedFullUrl;
    private static int cachedDuration = 15;
    /** "video" | "image" */
    private static String cachedMediaType = "video";
    private static volatile boolean ready = false;
    private static volatile boolean loading = false;

    /**
     * 启动空闲时预取广告；已登录用户不预取（片头以带 episode_id 的接口为准）。
     */
    public static void warmupThenPreload(Context context) {
        final Context app = context.getApplicationContext();
        if (PrefsManager.isLoggedIn()) {
            return;
        }
        preload(app);
    }

    public static void preload(Context context) {
        if (loading || ready) return;
        final Context app = context.getApplicationContext();
        if (PrefsManager.isLoggedIn()) {
            return;
        }
        loading = true;

        ApiClient.getService().getAdVideo(null).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                loading = false;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Map<String, Object> data = response.body().getData();
                    if (Boolean.TRUE.equals(data.get("skip_ad"))) {
                        Log.d(TAG, "skip_ad: no preload");
                        return;
                    }
                    // 仅以 media_type 区分图片/视频；勿根据是否带 video_url 推断
                    String mt = "video";
                    Object mtRaw = data.get("media_type");
                    if (mtRaw instanceof String && !((String) mtRaw).isEmpty()) {
                        mt = ((String) mtRaw).toLowerCase().trim();
                    }
                    cachedMediaType = mt;
                    Object durRaw = data.get("duration");
                    if (durRaw instanceof Double) {
                        cachedDuration = ((Double) durRaw).intValue();
                    } else if (durRaw instanceof Integer) {
                        cachedDuration = (Integer) durRaw;
                    } else if (durRaw instanceof Long) {
                        cachedDuration = ((Long) durRaw).intValue();
                    }
                    if ("image".equals(mt)) {
                        cachedVideoUrl = null;
                        String imagePath = (String) data.get("image_url");
                        cachedFullUrl = ImageUrlUtils.resolve(imagePath);
                        ready = true;
                        Log.d(TAG, "Ad image cached url: " + cachedFullUrl);
                    } else {
                        cachedVideoUrl = (String) data.get("video_url");
                        cachedFullUrl = buildFullUrl(cachedVideoUrl);
                        ExoPlayerCache.precache(context, cachedFullUrl);
                        ready = true;
                        Log.d(TAG, "Ad preloaded: " + cachedFullUrl);
                    }
                } else {
                    Log.w(TAG, "Ad preload API failed");
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                loading = false;
                Log.w(TAG, "Ad preload network error: " + t.getMessage());
            }
        });
    }

    public static boolean isReady() {
        return ready;
    }

    public static String getFullUrl() {
        return cachedFullUrl;
    }

    /** @return "video" 或 "image" */
    public static String getMediaType() {
        return cachedMediaType != null ? cachedMediaType : "video";
    }

    public static int getDuration() {
        return cachedDuration;
    }

    /**
     * 消费预加载的广告后调用，重置状态并触发下一次预加载
     */
    public static void consume(Context context) {
        ready = false;
        cachedVideoUrl = null;
        cachedFullUrl = null;
        cachedMediaType = "video";
        preload(context);
    }

    private static String buildFullUrl(String videoUrl) {
        if (videoUrl == null) return "";
        if (videoUrl.startsWith("http")) return videoUrl;
        String base = BuildConfig.BASE_URL.replace("/api/v1/", "");
        return base + videoUrl;
    }
}
