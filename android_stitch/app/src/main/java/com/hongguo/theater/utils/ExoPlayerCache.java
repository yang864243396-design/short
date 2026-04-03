package com.hongguo.theater.utils;

import android.content.Context;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;

@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerCache {

    private static final String TAG = "ExoPlayerCache";
    private static final long MAX_CACHE_SIZE = 100 * 1024 * 1024;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    private static SimpleCache cache;

    public static synchronized void init(Context context) {
        if (cache != null) return;
        File cacheDir = new File(context.getCacheDir(), "video_cache");
        clearDirIfCorrupt(cacheDir);
        LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE);
        StandaloneDatabaseProvider dbProvider = new StandaloneDatabaseProvider(context);
        cache = new SimpleCache(cacheDir, evictor, dbProvider);
    }

    private static void clearDirIfCorrupt(File dir) {
        File marker = new File(dir, ".cache_v2");
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

    public static DataSource.Factory getDataSourceFactory(Context context) {
        init(context);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);

        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpFactory)
                .setCacheWriteDataSinkFactory(
                        () -> new CacheDataSink(cache, MAX_FILE_SIZE))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static void precache(Context context, String url) {
        cancelPrecache();
        init(context);

        precacheTask = executor.submit(() -> {
            try {
                DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true);

                CacheDataSource dataSource = new CacheDataSource(
                        cache,
                        httpFactory.createDataSource(),
                        null,
                        new CacheDataSink(cache, MAX_FILE_SIZE),
                        CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR,
                        null);

                androidx.media3.datasource.DataSpec dataSpec =
                        new androidx.media3.datasource.DataSpec(android.net.Uri.parse(url));
                activeCacheWriter = new androidx.media3.datasource.cache.CacheWriter(
                        dataSource, dataSpec, null, null);
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

    private static final java.util.concurrent.ExecutorService executor =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static java.util.concurrent.Future<?> precacheTask;
    private static volatile androidx.media3.datasource.cache.CacheWriter activeCacheWriter;

    public static void cancelPrecache() {
        androidx.media3.datasource.cache.CacheWriter writer = activeCacheWriter;
        if (writer != null) {
            writer.cancel();
        }
        if (precacheTask != null) {
            precacheTask.cancel(true);
            precacheTask = null;
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
