package com.hongguo.theater.utils;

import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 播放页辅助判断：卡顿更可能来自「视频拉流/网络缓冲」还是「HTTP 接口慢或失败」。
 * 仅作启发式结论，需结合 log与后台监控。
 */
public final class PlaybackDiagnostics {

    private static final long API_SLOW_MS = 2500;
    private static final long FIRST_FRAME_SLOW_MS = 5000;
    private static final int REBUFFER_CONCERN = 2;
    private static final long LOADING_ACCUM_CONCERN_MS = 12_000;

    private static final class ApiRecord {
        final String name;
        final long durationMs;
        final boolean ok;
        final int httpCode;
        @Nullable final String err;

        ApiRecord(String name, long durationMs, boolean ok, int httpCode, @Nullable String err) {
            this.name = name;
            this.durationMs = durationMs;
            this.ok = ok;
            this.httpCode = httpCode;
            this.err = err;
        }
    }

    private final List<ApiRecord> apiRecords = new ArrayList<>();
    private final java.util.Map<String, Long> apiStarts = new java.util.HashMap<>();

    private int lastPlaybackState = Player.STATE_IDLE;
    private long loadingAccumMs;
    private long loadingSince = -1;
    private int rebufferCount;
    private long prepareStartedAt = -1;
    private boolean awaitingReadyAfterPrepare;
    /** 最近一次从 prepare 到 READY 的耗时（毫秒），-1 表示尚未记录 */
    private long lastFirstFrameReadyMs = -1;

    @Nullable private String lastPlayerErrorSummary;
    private int lastPlayerErrorCode = PlaybackException.ERROR_CODE_UNSPECIFIED;
    private long lastBitrateEstimate = -1;

    public void reset() {
        apiRecords.clear();
        apiStarts.clear();
        lastPlaybackState = Player.STATE_IDLE;
        loadingAccumMs = 0;
        loadingSince = -1;
        rebufferCount = 0;
        prepareStartedAt = -1;
        awaitingReadyAfterPrepare = false;
        lastFirstFrameReadyMs = -1;
        lastPlayerErrorSummary = null;
        lastPlayerErrorCode = PlaybackException.ERROR_CODE_UNSPECIFIED;
        lastBitrateEstimate = -1;
    }

    public void apiBegin(@NonNull String name) {
        apiStarts.put(name, SystemClock.elapsedRealtime());
    }

    public void apiEnd(@NonNull String name, boolean ok, int httpCode, @Nullable String err) {
        Long start = apiStarts.remove(name);
        long d = start == null ? -1L : (SystemClock.elapsedRealtime() - start);
        apiRecords.add(new ApiRecord(name, d, ok, httpCode, err));
    }

    public void onPrepareStarted() {
        prepareStartedAt = SystemClock.elapsedRealtime();
        awaitingReadyAfterPrepare = true;
    }

    public void onPlaybackStateChanged(int state, boolean playWhenReady) {
        if (state == Player.STATE_BUFFERING) {
            if (lastPlaybackState == Player.STATE_READY && playWhenReady) {
                rebufferCount++;
            }
        }
        if (state == Player.STATE_READY && awaitingReadyAfterPrepare && prepareStartedAt > 0) {
            lastFirstFrameReadyMs = SystemClock.elapsedRealtime() - prepareStartedAt;
            awaitingReadyAfterPrepare = false;
        }
        lastPlaybackState = state;
    }

    public void onIsLoadingChanged(boolean isLoading) {
        long now = SystemClock.elapsedRealtime();
        if (isLoading) {
            if (loadingSince < 0) {
                loadingSince = now;
            }
        } else {
            if (loadingSince >= 0) {
                loadingAccumMs += (now - loadingSince);
                loadingSince = -1;
            }
        }
    }

    /** Activity 销毁前把未结束的 loading 段计入累计 */
    public void flushLoading() {
        onIsLoadingChanged(false);
    }

    public void onPlayerError(@NonNull PlaybackException error) {
        lastPlayerErrorSummary = error.getMessage();
        lastPlayerErrorCode = error.errorCode;
    }

    public void onBandwidthSample(long bitrateEstimate) {
        if (bitrateEstimate > 0) {
            lastBitrateEstimate = bitrateEstimate;
        }
    }

    @NonNull
    public String buildReport() {
        StringBuilder sb = new StringBuilder(512);

        sb.append("【接口】\n");
        if (apiRecords.isEmpty()) {
            sb.append("暂无记录\n");
        } else {
            for (ApiRecord r : apiRecords) {
                String time = r.durationMs < 0 ? "?" : (r.durationMs + "ms");
                sb.append(String.format(Locale.CHINA, "· %s: %s, http=%d, ok=%s",
                        r.name, time, r.httpCode, r.ok));
                if (!TextUtils.isEmpty(r.err)) {
                    sb.append("\n  ").append(r.err);
                }
                sb.append('\n');
            }
        }

        sb.append("\n【视频/网络】\n");
        sb.append(String.format(Locale.CHINA, "· 起播到可播耗时: %s\n",
                lastFirstFrameReadyMs < 0 ? "—" : (lastFirstFrameReadyMs + "ms")));
        sb.append(String.format(Locale.CHINA, "· 播放中重新缓冲次数: %d\n", rebufferCount));
        sb.append(String.format(Locale.CHINA, "· 累计处于加载中: %dms\n", loadingAccumMs + currentLoadingDelta()));
        if (lastBitrateEstimate > 0) {
            sb.append(String.format(Locale.CHINA, "· 估计码率: %.2f Mbps\n",
                    lastBitrateEstimate / 1_000_000.0));
        }
        if (!TextUtils.isEmpty(lastPlayerErrorSummary)) {
            sb.append(String.format(Locale.CHINA, "· 最近播放错误[%d]: %s\n",
                    lastPlayerErrorCode, lastPlayerErrorSummary));
        }

        sb.append("\n【结论（启发式）】\n");
        sb.append(synthesizeVerdict());
        return sb.toString();
    }

    private long currentLoadingDelta() {
        if (loadingSince < 0) return 0;
        return SystemClock.elapsedRealtime() - loadingSince;
    }

    @NonNull
    private String synthesizeVerdict() {
        long epMs = durationFor("episode_list");
        long dramaMs = durationFor("drama_detail");
        boolean apiSlow = (epMs >= API_SLOW_MS) || (dramaMs >= API_SLOW_MS);
        boolean epFail = !successFor("episode_list");

        long loadingTotal = loadingAccumMs + currentLoadingDelta();
        boolean streamConcern = rebufferCount >= REBUFFER_CONCERN
                || loadingTotal >= LOADING_ACCUM_CONCERN_MS
                || (lastFirstFrameReadyMs >= FIRST_FRAME_SLOW_MS)
                || isStreamishError(lastPlayerErrorCode);

        if (epFail) {
            return "分集列表接口失败或异常，首屏无法起播，优先查接口/网关。";
        }
        if (streamConcern && !apiSlow) {
            return "更可能为视频拉流或网络缓冲（重缓冲多、加载累计久或起播慢）。可排查 CDN、弱网、片源码率。";
        }
        if (apiSlow && !streamConcern) {
            return "更可能为接口偏慢（详情/分集耗时长），影响进页与切集体验。可排查服务端 RT、DB、payload。";
        }
        if (apiSlow && streamConcern) {
            return "接口与视频侧均偏慢，建议两端同时排查。";
        }
        if (!TextUtils.isEmpty(lastPlayerErrorSummary)) {
            return "有过播放错误，请结合上方错误码；若无缓冲问题则也可能是片源或解码。";
        }
        return "本次会话未明显偏向一侧；若仍卡顿，请在卡顿时打开本页观察「重新缓冲」是否增加。";
    }

    private long durationFor(String name) {
        for (int i = apiRecords.size() - 1; i >= 0; i--) {
            ApiRecord r = apiRecords.get(i);
            if (name.equals(r.name) && r.durationMs >= 0) {
                return r.durationMs;
            }
        }
        return -1;
    }

    private boolean successFor(String name) {
        for (int i = apiRecords.size() - 1; i >= 0; i--) {
            ApiRecord r = apiRecords.get(i);
            if (name.equals(r.name)) {
                return r.ok;
            }
        }
        return true;
    }

    private static boolean isStreamishError(int code) {
        return code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
                || code == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
                || code == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
                || code == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE
                || code == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                || code == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
                || code == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED
                || code == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE
                || code == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
                || code == PlaybackException.ERROR_CODE_TIMEOUT
                || code == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED
                || code == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED;
    }
}
