package com.hongguo.theater.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerCache {

    private static final String TAG = "ExoPlayerCache";
    private static final long MAX_CACHE_SIZE = 200 * 1024 * 1024;
    private static final long MAX_FILE_SIZE = Long.MAX_VALUE;

    private static SimpleCache cache;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Future<?> currentPrecacheTask;
    private static volatile CacheWriter activeCacheWriter;

    public static synchronized void init(Context context) {
        if (cache != null) return;
        File cacheDir = new File(context.getCacheDir(), "video_cache");
        clearDirIfCorrupt(cacheDir);
        LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE);
        StandaloneDatabaseProvider dbProvider = new StandaloneDatabaseProvider(context);
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

    public static DefaultHttpDataSource.Factory createHttpFactory() {
        // 与 Retrofit 一致：无签 /stream/{id} 时服务端 OptionalAuth 需 Bearer；Redis 短时授权也按 user_id 判断
        Map<String, String> headers = new HashMap<>();
        if (PrefsManager.isLoggedIn()) {
            String t = PrefsManager.getToken();
            if (t != null && !t.isEmpty()) {
                headers.put("Authorization", "Bearer " + t);
            }
        }
        return new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(60_000)
                .setDefaultRequestProperties(headers.isEmpty() ? Collections.emptyMap() : headers);
    }

    /**
     * 与全屏、Feed 共用。
     * 此前 minBuffer=20s 低于 Media3 默认（约 50s），缓冲池偏浅，容易播到一半等数据→体感「一卡一卡」；
     * 适当拉高时间型缓冲，略增起播等待，换更稳的连续播放（仍优先时间阈值便于起播）。
     */
    public static DefaultLoadControl createVideoLoadControl() {
        return new DefaultLoadControl.Builder()
                .setBufferDurationsMs(50_000, 120_000, 2_500, 10_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build();
    }

    public static DataSource.Factory getDataSourceFactory(Context context) {
        init(context);
        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(createHttpFactory())
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
                        createHttpFactory().createDataSource(),
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
