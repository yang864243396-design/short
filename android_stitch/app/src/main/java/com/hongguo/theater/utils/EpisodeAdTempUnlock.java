package com.hongguo.theater.utils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 同集 10 分钟窗口（进程内存）：与需求「看广后 10 分钟」「扣次后同集 10 分钟」共用同一状态，减少分支。
 * <ul>
 *   <li>片头广告播完 / 达到最短展示后完成 → {@link #grantAfterAd(long)}</li>
 *   <li>免广 skip_ad（扣次或服务端冷却）→ 同上，仍用 {@link #grantAfterAd(long)}</li>
 * </ul>
 * 登出或 {@link #clear()} 后失效。
 */
public final class EpisodeAdTempUnlock {

    private static final long TEN_MIN_MS = 10 * 60 * 1000L;
    private static final Map<Long, Long> EXPIRY_MS = new ConcurrentHashMap<>();

    private EpisodeAdTempUnlock() {
    }

    /** 看广完成或免广 skip_ad 成功后调用，刷新本集 10 分钟截止时刻。 */
    public static void grantAfterAd(long episodeId) {
        if (episodeId <= 0) return;
        EXPIRY_MS.put(episodeId, System.currentTimeMillis() + TEN_MIN_MS);
    }

    public static boolean isActive(long episodeId) {
        if (episodeId <= 0) return false;
        Long exp = EXPIRY_MS.get(episodeId);
        if (exp == null) return false;
        if (System.currentTimeMillis() >= exp) {
            EXPIRY_MS.remove(episodeId);
            return false;
        }
        return true;
    }

    public static void clear() {
        EXPIRY_MS.clear();
    }
}
