package com.hongguo.theater.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;

@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerCache {

    private static final String TAG = "ExoPlayerCache";
    private static final long MAX_CACHE_SIZE = 200 * 1024 * 1024;
    private static final long MAX_FILE_SIZE = Long.MAX_VALUE;

    /** 供 {@link Util#getUserAgent}，避免每次建 Factory 重复计算 */
    @Nullable
    private static Context appContext;
    @Nullable
    private static String resolvedUserAgent;

    private static SimpleCache cache;
    @Nullable
    private static OkHttpClient streamHttpClient;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Future<?> currentPrecacheTask;
    private static volatile CacheWriter activeCacheWriter;

    public static synchronized void init(Context context) {
        appContext = context.getApplicationContext();
        if (cache != null) return;
        File cacheDir = new File(appContext.getCacheDir(), "video_cache");
        clearDirIfCorrupt(cacheDir);
        LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE);
        StandaloneDatabaseProvider dbProvider = new StandaloneDatabaseProvider(appContext);
        cache = new SimpleCache(cacheDir, evictor, dbProvider);
    }

    private static void clearDirIfCorrupt(File dir) {
        File marker = new File(dir, ".cache_v4");
        if (dir.exists() && !marker.exists()) {
            Log.w(TAG, "Clearing old/corrupt cache");
            deleteRecursive(dir);
            dir.mkdirs();
            try { marker.createNewFile(); } catch (Exception ignored) {}
        } else if (!dir.exists()) {
            dir.mkdirs();
            try { marker.createNewFile(); } catch (Exception ignored) {}
        }
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    private static synchronized OkHttpClient streamHttpClient() {
        if (streamHttpClient == null) {
            // 与 Api 层分离：无日志/JSON 拦截器，专用于大文件拉流；连接池 + HTTP/2 对首包/多段更友好
            streamHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(65, TimeUnit.SECONDS)
                    .writeTimeout(20, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .followRedirects(true)
                    .followSslRedirects(true)
                    .addInterceptor(chain -> {
                        okhttp3.Request.Builder b = chain.request().newBuilder();
                        if (PrefsManager.isLoggedIn()) {
                            String t = PrefsManager.getToken();
                            if (t != null && !t.isEmpty()) {
                                b.header("Authorization", "Bearer " + t);
                            }
                        }
                        return chain.proceed(b.build());
                    })
                    .build();
        }
        return streamHttpClient;
    }

    private static synchronized String userAgent() {
        if (resolvedUserAgent == null && appContext != null) {
            resolvedUserAgent = Util.getUserAgent(appContext, "HongguoTheater");
        }
        return resolvedUserAgent != null ? resolvedUserAgent : "HongguoTheater";
    }

    /**
     * OkHttp 上游（替代 DefaultHttpDataSource）：冷连接、keep-alive 与 TLS 调度通常优于 HttpURLConnection，
     * 与 iOS URLSession 拉流体感更接近。
     */
    private static HttpDataSource.Factory createUpstreamHttpFactory() {
        // Media3 1.2.x 的 OkHttpDataSource.Factory 无 setAllowCrossProtocolRedirects；
        // 重定向交由 OkHttpClient（followRedirects / followSslRedirects）处理即可。
        return new OkHttpDataSource.Factory(streamHttpClient())
                .setUserAgent(userAgent());
    }

    /**
     * 刷剧竖滑 Feed：首帧与中段折中。
     * 过低的 bufferForPlayback（如 300ms 级）易导致全程「贴底缓冲」→ 周期性微小停顿，体感比 iOS「只卡首帧」更差。
     */
    public static DefaultLoadControl createFeedLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(22_000, 64_000, 950, 3_800)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    /**
     * 全屏剧场：比 Media3 默认 2.5s 起播略快，但避免全程 underrun 式卡顿。
     */
    public static DefaultLoadControl createFullscreenLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(48_000, 120_000, 1_350, 6_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    /**
     * @deprecated 请使用 {@link #createFullscreenLoadControl()} 或 {@link #createFeedLoadControl()}
     */
    @Deprecated
    public static DefaultLoadControl createVideoLoadControl() {
        return createFullscreenLoadControl();
    }

    public static DataSource.Factory getDataSourceFactory(Context context) {
        init(context);
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(createUpstreamHttpFactory())
                .setCacheWriteDataSinkFactory(
                        () -> new CacheDataSink(cache, MAX_FILE_SIZE))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static void precache(Context context, String url) {
        cancelPrecache();
        init(context);

        currentPrecacheTask = executor.submit(() -> {
            try {
                CacheDataSource dataSource = new CacheDataSource(
                        cache,
                        createUpstreamHttpFactory().createDataSource(),
                        null,
                        new CacheDataSink(cache, MAX_FILE_SIZE),
                        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                        null);

                DataSpec dataSpec = new DataSpec(Uri.parse(url));
                activeCacheWriter = new CacheWriter(dataSource, dataSpec, null, null);
                activeCacheWriter.cache();
                Log.d(TAG, "Precache complete: " + url);
            } catch (Exception e) {
                if (!Thread.currentThread().isInterrupted()) {
                    Log.w(TAG, "Precache failed: " + e.getMessage());
                }
            } finally {
                activeCacheWriter = null;
            }
        });
    }

    public static void cancelPrecache() {
        CacheWriter writer = activeCacheWriter;
        if (writer != null) {
            writer.cancel();
        }
        if (currentPrecacheTask != null) {
            currentPrecacheTask.cancel(true);
            currentPrecacheTask = null;
        }
    }

    public static synchronized void release() {
        cancelPrecache();
        if (cache != null) {
            cache.release();
            cache = null;
        }
    }
}
