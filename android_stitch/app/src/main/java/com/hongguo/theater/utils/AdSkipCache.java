package com.hongguo.theater.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.AdSkipStatus;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.WalletBalance;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地缓存广告跳过权益到期时间：在有效期内避免反复请求服务端校验是否「仍免广告」。
 */
public final class AdSkipCache {

    private static final String PREFS = "hongguo_ad_skip";
    private static final String K_USER = "user_id";
    /** -1 未同步；0 已同步且无有效权益；>0 为权益结束时刻的 wall 时钟毫秒 */
    private static final String K_EXPIRES = "expires_wall_ms";
    /** 免广告剩余次数；-1 未同步 */
    private static final String K_REMAINING = "skip_remaining";

    private static final AtomicBoolean PREFETCH_IN_FLIGHT = new AtomicBoolean(false);

    private AdSkipCache() {}

    private static SharedPreferences p(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void clear(Context ctx) {
        if (ctx == null) return;
        p(ctx).edit().clear().apply();
    }

    /** 切换账号时调用：新用户需重新拉取 */
    public static void ensureUser(Context ctx, long userId) {
        if (ctx == null || userId <= 0) return;
        SharedPreferences pr = p(ctx);
        long stored = pr.getLong(K_USER, Long.MIN_VALUE);
        if (stored != userId) {
            pr.edit().putLong(K_USER, userId).putLong(K_EXPIRES, -1L).putLong(K_REMAINING, -1L).apply();
        }
    }

    public static boolean isLocallyActive(Context ctx) {
        if (ctx == null || !PrefsManager.isLoggedIn()) return false;
        SharedPreferences pr = p(ctx);
        if (pr.getLong(K_USER, Long.MIN_VALUE) != PrefsManager.getUserId()) return false;
        long exp = pr.getLong(K_EXPIRES, -1L);
        return exp > System.currentTimeMillis();
    }

    /** 时间包未过期且（已同步的）剩余次数 &gt; 0，可用于跳片头扣次 */
    public static boolean isLocallyAdSkipPlayable(Context ctx) {
        if (!isLocallyActive(ctx)) return false;
        SharedPreferences pr = p(ctx);
        if (pr.getLong(K_USER, Long.MIN_VALUE) != PrefsManager.getUserId()) return false;
        long rem = pr.getLong(K_REMAINING, -1L);
        if (rem < 0L) return false;
        return rem > 0L;
    }

    /**
     * 付费集是否可不弹「金币 / 看广告」二选一：有可用免广次数；或权益在期但本地次数未同步
     * （尽早 {@link #prefetchIfStale} 可缩小此窗口，未同步时仍由片头 getAdVideo 终裁）。
     * 已同步且剩余为 0 时返回 false，仍弹窗。
     */
    public static boolean shouldSkipCoinUnlockDialogForAdSkip(Context ctx) {
        if (!isLocallyActive(ctx)) return false;
        SharedPreferences pr = p(ctx);
        if (pr.getLong(K_USER, Long.MIN_VALUE) != PrefsManager.getUserId()) return false;
        if (isLocallyAdSkipPlayable(ctx)) return true;
        long rem = pr.getLong(K_REMAINING, -1L);
        return rem < 0L;
    }

    /** 是否需要向服务端同步一次（未知、过期、剩余次数未拉取、或缓存用户与当前不一致） */
    public static boolean needsServerRefresh(Context ctx) {
        if (ctx == null || !PrefsManager.isLoggedIn()) return false;
        SharedPreferences pr = p(ctx);
        if (pr.getLong(K_USER, Long.MIN_VALUE) != PrefsManager.getUserId()) return true;
        long exp = pr.getLong(K_EXPIRES, -1L);
        long rem = pr.getLong(K_REMAINING, -1L);
        long now = System.currentTimeMillis();
        if (exp < 0L) return true;
        if (exp > now && rem < 0L) return true;
        return exp > 0L && now >= exp;
    }

    /**
     * 在冷启动/回前台/登录后尽快拉取免广到期与剩余次数，便于播放页用本地即判定是否弹二选一。
     * 仅当 {@link #needsServerRefresh} 为 true 时发请求，并发去重，失败不提示。
     */
    public static void prefetchIfStale(@Nullable Context ctx) {
        if (ctx == null) return;
        final Context app = ctx.getApplicationContext();
        if (!PrefsManager.isLoggedIn()) return;
        if (!needsServerRefresh(app)) return;
        if (!PREFETCH_IN_FLIGHT.compareAndSet(false, true)) return;
        ApiClient.getService().getAdSkipStatus().enqueue(new Callback<ApiResponse<AdSkipStatus>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AdSkipStatus>> call,
                                   @NonNull Response<ApiResponse<AdSkipStatus>> response) {
                PREFETCH_IN_FLIGHT.set(false);
                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    return;
                }
                AdSkipStatus d = response.body().getData();
                if (d != null) {
                    applyAdSkipStatus(d, app);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AdSkipStatus>> call, @NonNull Throwable t) {
                PREFETCH_IN_FLIGHT.set(false);
            }
        });
    }

    public static void applyWallet(@Nullable WalletBalance w, Context ctx) {
        if (w == null || ctx == null || !PrefsManager.isLoggedIn()) return;
        Context app = ctx.getApplicationContext();
        long uid = PrefsManager.getUserId();
        ensureUser(app, uid);
        long expMs = parseExpiresAt(w.getAdSkipExpiresAt());
        long now = System.currentTimeMillis();
        if (expMs > 0L && expMs <= now) expMs = 0L;
        p(app).edit().putLong(K_EXPIRES, expMs).putLong(K_REMAINING, w.getAdSkipRemaining()).apply();
    }

    public static void applyAdSkipStatus(@Nullable AdSkipStatus d, Context ctx) {
        if (d == null || ctx == null || !PrefsManager.isLoggedIn()) return;
        Context app = ctx.getApplicationContext();
        ensureUser(app, PrefsManager.getUserId());
        long expMs = parseExpiresAt(d.getAdSkipExpiresAt());
        long now = System.currentTimeMillis();
        if (expMs > 0L && expMs <= now) expMs = 0L;
        p(app).edit().putLong(K_EXPIRES, expMs).putLong(K_REMAINING, d.getAdSkipRemaining()).apply();
    }

    public static void applyPurchaseMap(@Nullable Map<String, Object> data, Context ctx) {
        if (data == null || ctx == null || !PrefsManager.isLoggedIn()) return;
        Object raw = data.get("ad_skip_expires_at");
        String iso = null;
        if (raw instanceof String) {
            iso = (String) raw;
        } else if (raw != null) {
            iso = String.valueOf(raw);
        }
        Context app = ctx.getApplicationContext();
        ensureUser(app, PrefsManager.getUserId());
        long expMs = parseExpiresAt(iso);
        long now = System.currentTimeMillis();
        if (expMs > 0L && expMs <= now) expMs = 0L;
        long rem = -1L;
        Object r0 = data.get("ad_skip_remaining");
        if (r0 instanceof Number) {
            rem = ((Number) r0).longValue();
        }
        p(app).edit().putLong(K_EXPIRES, expMs > 0L ? expMs : -1L)
                .putLong(K_REMAINING, rem).apply();
    }

    static long parseExpiresAt(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) return 0L;
        String s = raw.trim();
        if (s.length() < 19 || s.charAt(10) != 'T') return 0L;
        try {
            if (s.endsWith("Z")) {
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = fmt.parse(s, new ParsePosition(0));
                return d != null ? d.getTime() : 0L;
            }
            int len = s.length();
            if (len >= 25) {
                char c6 = s.charAt(len - 6);
                if (c6 == '+' || c6 == '-') {
                    SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
                    Date d = fmt.parse(s, new ParsePosition(0));
                    if (d != null) return d.getTime();
                }
            }
            int dot = s.indexOf('.', 19);
            if (dot > 0) {
                String base = s.substring(0, dot);
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
                fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date d = fmt.parse(base, new ParsePosition(0));
                return d != null ? d.getTime() : 0L;
            }
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date d = fmt.parse(s, new ParsePosition(0));
            return d != null ? d.getTime() : 0L;
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
