package com.hongguo.theater.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.database.StandaloneDatabaseProvider;
import androidx.media3.datasource.AesCipherDataSink;
import androidx.media3.datasource.AesCipherDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.FileDataSource;
import androidx.media3.datasource.cache.CacheDataSink;
import androidx.media3.datasource.cache.CacheDataSource;
import androidx.media3.datasource.cache.CacheWriter;
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor;
import androidx.media3.datasource.cache.SimpleCache;

import java.io.File;
import java.security.SecureRandom;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@OptIn(markerClass = UnstableApi.class)
public class ExoPlayerCache {

    private static final String TAG = "ExoPlayerCache";
    private static final long MAX_CACHE_SIZE = 100 * 1024 * 1024;
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final String PREFS_NAME = "exo_cache_prefs";
    private static final String KEY_SECRET = "cache_secret";

    private static SimpleCache cache;
    private static byte[] cacheSecret;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static Future<?> currentPrecacheTask;
    private static volatile CacheWriter activeCacheWriter;

    public static synchronized void init(Context context) {
        if (cache != null) return;
        cacheSecret = getOrCreateSecret(context);
        File cacheDir = new File(context.getCacheDir(), "video_cache");
        LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(MAX_CACHE_SIZE);
        StandaloneDatabaseProvider dbProvider = new StandaloneDatabaseProvider(context);
        cache = new SimpleCache(cacheDir, evictor, dbProvider);
    }

    private static byte[] getOrCreateSecret(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encoded = prefs.getString(KEY_SECRET, null);
        if (encoded != null) {
            return Base64.decode(encoded, Base64.NO_WRAP);
        }
        byte[] secret = new byte[16];
        new SecureRandom().nextBytes(secret);
        prefs.edit().putString(KEY_SECRET, Base64.encodeToString(secret, Base64.NO_WRAP)).apply();
        return secret;
    }

    public static DataSource.Factory getDataSourceFactory(Context context) {
        init(context);
        DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true);

        DataSource.Factory cacheReadFactory = () ->
                new AesCipherDataSource(cacheSecret, new FileDataSource());

        return new CacheDataSource.Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(httpFactory)
                .setCacheReadDataSourceFactory(cacheReadFactory)
                .setCacheWriteDataSinkFactory(
                        () -> new AesCipherDataSink(cacheSecret,
                                new CacheDataSink(cache, MAX_FILE_SIZE)))
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR);
    }

    public static void precache(Context context, String url) {
        cancelPrecache();
        init(context);

        currentPrecacheTask = executor.submit(() -> {
            try {
                DefaultHttpDataSource.Factory httpFactory = new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true);

                CacheDataSource dataSource = new CacheDataSource(
                        cache,
                        httpFactory.createDataSource(),
                        new AesCipherDataSource(cacheSecret, new FileDataSource()),
                        new AesCipherDataSink(cacheSecret, new CacheDataSink(cache, MAX_FILE_SIZE)),
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
