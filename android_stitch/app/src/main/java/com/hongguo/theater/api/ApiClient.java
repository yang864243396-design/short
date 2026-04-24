package com.hongguo.theater.api;

import android.content.Context;

import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.utils.PrefsManager;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {

    private static ApiService apiService;
    private static Retrofit retrofit;

    public static void init(Context context) {
        PrefsManager.init(context);

        // DEBUG 也勿用 Level.BODY，避免全量 URL 参数/JSON 在 Logcat/调试浮层里刷屏，与 UI 易混淆
        HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
        logging.setLevel(BuildConfig.DEBUG
                ? HttpLoggingInterceptor.Level.BASIC
                : HttpLoggingInterceptor.Level.NONE);

        OkHttpClient client = new OkHttpClient.Builder()
                .addInterceptor(new TokenInterceptor())
                .addInterceptor(logging)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .build();

        retrofit = new Retrofit.Builder()
                .baseUrl(BuildConfig.BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        apiService = retrofit.create(ApiService.class);
    }

    public static ApiService getService() {
        return apiService;
    }

    public static String getStreamUrl(long episodeId) {
        return BuildConfig.BASE_URL + "stream/" + episodeId;
    }

    public static String getStreamUrl(Episode episode) {
        String signedUrl = episode.getStreamUrl();
        if (signedUrl != null && !signedUrl.isEmpty()) {
            String base = BuildConfig.BASE_URL.replace("/api/v1/", "");
            return signedUrl.startsWith("http") ? signedUrl : base + signedUrl;
        }
        return getStreamUrl(episode.getId());
    }
}
