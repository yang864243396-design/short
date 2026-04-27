package com.hongguo.theater.ui.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;
import androidx.core.view.GestureDetectorCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.MainActivity;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.api.ApiErrorHelper;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.model.WalletBalance;
import com.bumptech.glide.Glide;
import com.hongguo.theater.utils.AdPreloader;
import com.hongguo.theater.utils.AdSkipCache;
import com.hongguo.theater.utils.ImageUrlUtils;
import com.hongguo.theater.utils.DescriptionInlineExpandHelper;
import com.hongguo.theater.utils.EpisodeAdTempUnlock;
import com.hongguo.theater.utils.LoginHelper;
import com.hongguo.theater.utils.ExoPlayerCache;
import com.hongguo.theater.utils.HgDialog;
import com.hongguo.theater.utils.PlaybackDiagnostics;
import com.hongguo.theater.utils.PrefsManager;
import com.hongguo.theater.utils.RankingBadgeUiHelper;
import com.hongguo.theater.ui.wallet.WalletActivity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvRankingBadge, tvTitle, tvDesc, tvEpisodeInfo, tvLikeCount, tvCommentCount;
    private ImageView btnLike, btnComment, btnCollect, btnShare, btnBack;
    private TextView btnEpisodes;

    private ImageView ivCenterPlay;
    /** 解锁广告图片全屏 */
    private ImageView ivAdCreative;
    private SeekBar seekBar;
    private View layoutTopBar;

    private TextView tvAdLabel, btnCloseAd;
    private View layoutVideoArea;
    private View layoutBottomOverlay, layoutPlayerDock, layoutRightButtons;
    private LinearLayout layoutIntroPanel;
    private ScrollView scrollDesc;
    private boolean descExpanded = false;
    /** 剧目简介原文（用于首行末尾内联「展开」） */
    private String storedDramaDescription = "";
    private boolean adCanClose = false;

    private View rootView;
    private GestureDetectorCompat gestureDetector;
    private boolean isSwiping = false;
    /** 跟手竖滑切集（对齐刷剧 ViewPager2：位移跟手 + 松手阈值/速度吸附） */
    private int episodeTouchSlop;
    @Nullable
    private VelocityTracker episodeVelocityTracker;
    private float episodeTouchDownX;
    private float episodeTouchDownY;
    private float episodeDragLastY;
    /** 跟手拖拽的累计位移，避免每帧 read layoutVideoArea.getTranslationY() */
    private float episodeDragTranslationY;
    private boolean episodeDragActive;
    private boolean episodeTouchDecided;
    private final FastOutSlowInInterpolator episodeSlideInterpolator = new FastOutSlowInInterpolator();
    /** 最后一集正常播完后，允许下滑返回刷剧并切到 Feed 中下一部剧 */
    private boolean lastEpisodeEndedAwaitSwipe = false;

    private boolean controlsVisible = true;
    private boolean isUserSeeking = false;
    private static final int CONTROLS_HIDE_DELAY = 3000;
    private static final int PROGRESS_UPDATE_INTERVAL = 200;
    /** 刷剧页 ViewPager2 竖滑相近的切换节奏（时长 + FastOutSlowIn） */
    private static final int EPISODE_SWITCH_DURATION_MS = 320;
    /** 松手时超过屏高的比例则吸附切集（与常见分页手感接近） */
    private static final float EPISODE_SNAP_DISTANCE_FRACTION = 0.20f;
    /** 竖直 fling 速度阈值（px/s），与距离二选一满足即可切换 */
    private static final float EPISODE_SNAP_VELOCITY = 1100f;

    private long dramaId;
    /** 从 Feed「完整观看」进入：同一条流 URL + 进度，命中 ExoPlayerCache，避免首集再拉流 */
    private long feedHandoffEpisodeId;
    private long feedHandoffPositionMs;
    @Nullable
    private String feedHandoffStreamUrl;
    /** 由 playEpisodeDirect 设置，首帧 READY 时 seek 一次后清 -1 */
    private long pendingSeekAfterReadyMs = -1;

    private Drama drama;
    private List<Episode> episodes;
    private int currentEpisodeIndex = 0;
    private boolean currentEpisodeLiked = false;
    private boolean likeRequestInFlight = false;
    private boolean isPlayingAd = false;
    /** 当前是否为图片解锁广告（不计入 ExoPlayer 播放结束逻辑） */
    private boolean adIsImage = false;
    private boolean adCountdownPaused = false;
    private int pendingEpisodeIndex = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());
    @Nullable
    private Runnable adCountdownRunnable;
    /** 服务端免广告权益（广告跳过卡） */
    private final AtomicBoolean adSkipActive = new AtomicBoolean(false);

    private final Runnable hideControlsRunnable = () -> setControlsVisible(false);

    private final Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (isFinishing()) return;
            if (seekBar != null && player != null && !isUserSeeking && !isPlayingAd) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (dur > 0) {
                    seekBar.setProgress((int) (pos * 1000 / dur));
                }
            }
            boolean active = player != null && player.isPlaying() && !isPlayingAd;
            handler.postDelayed(this, active ? PROGRESS_UPDATE_INTERVAL : 800);
        }
    };

    @Nullable
    private Call<ApiResponse<Map<String, Object>>> interactionCall;
    @Nullable
    private Call<ApiResponse<WalletBalance>> adSkipWalletCall;
    @Nullable
    private Call<ApiResponse<Map<String, Object>>> unlockEpisodeCall;

    private final PlaybackDiagnostics playbackDiagnostics = new PlaybackDiagnostics();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player);

        readPlaybackIntent(getIntent());
        if (dramaId <= 0) {
            Toast.makeText(this, "无效的剧集信息", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        episodeTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();

        initViews();
        initPlayer();
        loadDramaData();
        refreshAdSkipStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshAdSkipStatus();
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        long prevDramaId = dramaId;
        long prevHandoffEp = feedHandoffEpisodeId;
        long prevHandoffPos = feedHandoffPositionMs;
        String prevHandoffUrl = feedHandoffStreamUrl;

        readPlaybackIntent(intent);
        if (dramaId <= 0) return;

        if (dramaId != prevDramaId) {
            resetPlayerSessionForNewDrama();
            loadDramaData();
            return;
        }

        boolean handoffChanged = feedHandoffEpisodeId != prevHandoffEp
                || feedHandoffPositionMs != prevHandoffPos
                || !Objects.equals(feedHandoffStreamUrl, prevHandoffUrl);
        if (handoffChanged && episodes != null && !episodes.isEmpty()) {
            playEpisode(computeStartIndexForHandoff());
        }
    }

    private void readPlaybackIntent(@NonNull Intent intent) {
        dramaId = intent.getLongExtra("drama_id", 0);
        feedHandoffEpisodeId = intent.getLongExtra("episode_id", 0L);
        feedHandoffPositionMs = intent.getLongExtra("playback_position_ms", 0L);
        feedHandoffStreamUrl = intent.getStringExtra("handoff_stream_url");
    }

    /**
     * 在栈顶再次 start 播放页（singleTop）时复用同一 Activity/ExoPlayer，避免叠多个全屏播放器。
     */
    private void resetPlayerSessionForNewDrama() {
        if (interactionCall != null) {
            interactionCall.cancel();
            interactionCall = null;
        }
        ExoPlayerCache.cancelPrecache();
        handler.removeCallbacks(hideControlsRunnable);
        cancelAdCountdown();
        isPlayingAd = false;
        adIsImage = false;
        adCanClose = false;
        adCountdownPaused = false;
        pendingEpisodeIndex = -1;
        pendingSeekAfterReadyMs = -1;
        lastEpisodeEndedAwaitSwipe = false;
        drama = null;
        episodes = null;
        currentEpisodeIndex = 0;
        showAdUI(false);
        if (btnCloseAd != null) btnCloseAd.setVisibility(View.GONE);
        clearAdImageOverlay();
        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        if (seekBar != null) seekBar.setProgress(0);
        playbackDiagnostics.reset();
        refreshEpisodeCountButton();
        if (tvTitle != null) tvTitle.setText("");
        RankingBadgeUiHelper.bind(tvRankingBadge, null, this);
        storedDramaDescription = "";
        if (tvDesc != null) {
            tvDesc.setText("");
            tvDesc.setMovementMethod(null);
        }
        resetIntroDescriptionUi();
    }

    /** 按 feedHandoffEpisodeId 解析起始集；找不到则清空 handoff，从第 0 集起 */
    private int computeStartIndexForHandoff() {
        if (episodes == null || episodes.isEmpty()) return 0;
        if (feedHandoffEpisodeId <= 0) return 0;
        for (int i = 0; i < episodes.size(); i++) {
            if (episodes.get(i).getId() == feedHandoffEpisodeId) {
                return i;
            }
        }
        feedHandoffEpisodeId = 0L;
        feedHandoffPositionMs = 0L;
        feedHandoffStreamUrl = null;
        return 0;
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
        tvRankingBadge = findViewById(R.id.tv_ranking_badge);
        tvTitle = findViewById(R.id.tv_title);
        tvDesc = findViewById(R.id.tv_desc);
        tvEpisodeInfo = findViewById(R.id.tv_episode_info);
        tvLikeCount = findViewById(R.id.tv_like_count);
        tvCommentCount = findViewById(R.id.tv_comment_count);
        btnLike = findViewById(R.id.btn_like);
        btnComment = findViewById(R.id.btn_comment);
        btnCollect = findViewById(R.id.btn_collect);
        btnShare = findViewById(R.id.btn_share);
        btnBack = findViewById(R.id.btn_back);
        btnEpisodes = findViewById(R.id.btn_episodes);
        tvAdLabel = findViewById(R.id.tv_ad_label);
        btnCloseAd = findViewById(R.id.btn_close_ad);
        layoutVideoArea = findViewById(R.id.layout_video_area);
        layoutBottomOverlay = findViewById(R.id.layout_bottom_overlay);
        layoutPlayerDock = findViewById(R.id.layout_player_dock);
        layoutRightButtons = findViewById(R.id.layout_right_buttons);
        layoutTopBar = findViewById(R.id.layout_top_bar);
        layoutIntroPanel = findViewById(R.id.layout_intro_panel);
        scrollDesc = findViewById(R.id.scroll_desc);

        ivCenterPlay = findViewById(R.id.iv_center_play);
        ivAdCreative = findViewById(R.id.iv_ad_creative);
        seekBar = findViewById(R.id.seek_bar);

        if (scrollDesc != null) {
            scrollDesc.setVerticalScrollBarEnabled(false);
        }
        resetIntroDescriptionUi();

        btnCloseAd.setOnClickListener(v -> {
            if (adCanClose) onAdFinished();
            else showSkipAdDialog();
        });

        btnBack.setOnClickListener(v -> {
            if (isPlayingAd) {
                if (adCanClose) onAdFinished();
                else showSkipAdDialog();
                return;
            }
            finish();
        });

        btnEpisodes.setOnClickListener(v -> {
            if (isPlayingAd) return;
            if (drama != null && episodes != null && !episodes.isEmpty()) {
                EpisodeBottomSheet sheet = EpisodeBottomSheet.newInstance(
                        drama.getTitle(), episodes, currentEpisodeIndex);
                sheet.setOnEpisodeClickListener(this::playEpisode);
                sheet.show(getSupportFragmentManager(), "episodes");
            } else if (drama != null) {
                Toast.makeText(PlayerActivity.this, "暂无可选集数", Toast.LENGTH_SHORT).show();
            }
        });

        btnComment.setOnClickListener(v -> {
            if (isPlayingAd) return;
            if (!LoginHelper.requireLogin(this)) return;
            if (episodes != null && currentEpisodeIndex < episodes.size()) {
                Episode ep = episodes.get(currentEpisodeIndex);
                CommentBottomSheet sheet = CommentBottomSheet.newInstance(ep.getId(), ep.getCommentCount());
                sheet.setOnCommentPostedListener(() -> {
                    ep.setCommentCount(ep.getCommentCount() + 1);
                    tvCommentCount.setText(ep.getCommentCountText());
                });
                sheet.show(getSupportFragmentManager(), "comments");
            }
        });

        btnLike.setOnClickListener(v -> {
            float[] center = centerInVideoArea(btnLike);
            performLikeAction(false, center[0], center[1]);
        });

        btnCollect.setOnClickListener(v -> {
            if (isPlayingAd) return;
            if (!LoginHelper.requireLogin(this)) return;
            if (episodes != null && currentEpisodeIndex < episodes.size()) {
                ApiClient.getService().collectEpisode(episodes.get(currentEpisodeIndex).getId())
                        .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                            @Override
                            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                                   @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                    Map<String, Object> data = r.body().getData();
                                    boolean collected = data != null && Boolean.TRUE.equals(data.get("collected"));
                                    btnCollect.setColorFilter(collected ? Color.parseColor("#FFC107") : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                    Toast.makeText(PlayerActivity.this,
                                            collected ? "已收藏" : "已取消收藏", Toast.LENGTH_SHORT).show();
                                }
                            }
                            @Override
                            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {}
                        });
            }
        });

        btnShare.setOnClickListener(v -> {
            if (isPlayingAd) return;
            if (!LoginHelper.requireLogin(this)) return;
            if (drama != null) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "推荐你看《" + drama.getTitle() + "》");
                startActivity(Intent.createChooser(shareIntent, "分享到"));
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                isUserSeeking = true;
                handler.removeCallbacks(hideControlsRunnable);
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                if (player != null && player.getDuration() > 0) {
                    long seekPos = player.getDuration() * bar.getProgress() / 1000;
                    player.seekTo(seekPos);
                }
                isUserSeeking = false;
                scheduleHideControls();
            }
        });

        rootView = findViewById(android.R.id.content);
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (isPlayingAd) return false;
                toggleControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (isPlayingAd) return false;
                performLikeAction(true, e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        playerView.setOnTouchListener(this::onPlayerViewTouch);

        ivCenterPlay.setOnClickListener(v -> {
            if (isPlayingAd) return;
            togglePlayPause();
        });

        tvTitle.setOnLongClickListener(v -> {
            int pad = (int) (getResources().getDisplayMetrics().density * 16);
            TextView tv = new TextView(PlayerActivity.this);
            tv.setText(playbackDiagnostics.buildReport());
            tv.setTextSize(12);
            tv.setPadding(pad, pad / 2, pad, pad / 2);
            ScrollView scroll = new ScrollView(PlayerActivity.this);
            scroll.addView(tv);
            new AlertDialog.Builder(PlayerActivity.this)
                    .setTitle("播放诊断（长按标题）")
                    .setView(scroll)
                    .setPositiveButton("关闭", null)
                    .show();
            return true;
        });
    }

    private void performLikeAction(boolean forceLike, float x, float y) {
        if (isPlayingAd) return;
        if (!LoginHelper.requireLogin(this)) return;
        if (episodes == null || currentEpisodeIndex >= episodes.size()) return;
        Episode ep = episodes.get(currentEpisodeIndex);

        if (forceLike && currentEpisodeLiked) {
            showLikeSuccessEffect(x, y);
            pulseLikeButton();
            return;
        }
        if (likeRequestInFlight) return;
        likeRequestInFlight = true;
        ApiClient.getService().likeEpisode(ep.getId())
                .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                           @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                        likeRequestInFlight = false;
                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                            Map<String, Object> data = r.body().getData();
                            boolean liked = data != null && Boolean.TRUE.equals(data.get("liked"));
                            applyLikeState(ep, liked);
                            if (liked) {
                                showLikeSuccessEffect(x, y);
                                pulseLikeButton();
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                        likeRequestInFlight = false;
                    }
                });
    }

    private void applyLikeState(@NonNull Episode ep, boolean liked) {
        if (currentEpisodeLiked != liked) {
            long count = ep.getLikeCount() + (liked ? 1 : -1);
            if (count < 0) count = 0;
            ep.setLikeCount(count);
        }
        currentEpisodeLiked = liked;
        btnLike.setColorFilter(liked ? Color.RED : Color.WHITE, PorterDuff.Mode.SRC_IN);
        tvLikeCount.setText(ep.getLikeCountText());
    }

    private void pulseLikeButton() {
        btnLike.animate().cancel();
        btnLike.setScaleX(1f);
        btnLike.setScaleY(1f);
        btnLike.animate()
                .scaleX(1.24f)
                .scaleY(1.24f)
                .setDuration(110)
                .withEndAction(() -> btnLike.animate().scaleX(1f).scaleY(1f).setDuration(160).start())
                .start();
    }

    private void showLikeSuccessEffect(float x, float y) {
        if (!(layoutVideoArea instanceof FrameLayout)) return;
        FrameLayout overlay = (FrameLayout) layoutVideoArea;
        ImageView heart = new ImageView(this);
        heart.setImageResource(R.drawable.ic_favorite);
        heart.setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        int size = (int) (getResources().getDisplayMetrics().density * 88);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.leftMargin = Math.round(x - size / 2f);
        lp.topMargin = Math.round(y - size / 2f);
        overlay.addView(heart, lp);
        heart.setAlpha(0f);
        heart.setScaleX(0.55f);
        heart.setScaleY(0.55f);
        heart.setRotation((float) (Math.random() * 24f - 12f));

        AnimatorSet enter = new AnimatorSet();
        enter.playTogether(
                ObjectAnimator.ofFloat(heart, View.ALPHA, 0f, 1f),
                ObjectAnimator.ofFloat(heart, View.SCALE_X, 0.55f, 1.15f),
                ObjectAnimator.ofFloat(heart, View.SCALE_Y, 0.55f, 1.15f)
        );
        enter.setDuration(140);

        AnimatorSet exit = new AnimatorSet();
        exit.playTogether(
                ObjectAnimator.ofFloat(heart, View.ALPHA, 1f, 0f),
                ObjectAnimator.ofFloat(heart, View.TRANSLATION_Y, 0f, -140f),
                ObjectAnimator.ofFloat(heart, View.SCALE_X, 1.15f, 1.34f),
                ObjectAnimator.ofFloat(heart, View.SCALE_Y, 1.15f, 1.34f)
        );
        exit.setDuration(520);
        exit.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                overlay.removeView(heart);
            }
        });

        AnimatorSet all = new AnimatorSet();
        all.playSequentially(enter, exit);
        all.start();
    }

    private float[] centerInVideoArea(View target) {
        int[] areaLoc = new int[2];
        int[] targetLoc = new int[2];
        layoutVideoArea.getLocationOnScreen(areaLoc);
        target.getLocationOnScreen(targetLoc);
        return new float[] {
                targetLoc[0] - areaLoc[0] + target.getWidth() / 2f,
                targetLoc[1] - areaLoc[1] + target.getHeight() / 2f
        };
    }

    private void togglePlayPause() {
        if (player == null) return;
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void toggleControls() {
        setControlsVisible(!controlsVisible);
    }

    private void setControlsVisible(boolean visible) {
        controlsVisible = visible;
        float alpha = visible ? 1f : 0f;
        int duration = 200;

        layoutTopBar.animate().alpha(alpha).setDuration(duration).start();
        layoutRightButtons.animate().alpha(alpha).setDuration(duration).start();
        if (layoutBottomOverlay != null) {
            layoutBottomOverlay.animate().alpha(alpha).setDuration(duration).start();
        }

        if (visible) {
            scheduleHideControls();
        } else {
            handler.removeCallbacks(hideControlsRunnable);
        }
    }

    private void scheduleHideControls() {
        handler.removeCallbacks(hideControlsRunnable);
        if (player != null && player.isPlaying()) {
            handler.postDelayed(hideControlsRunnable, CONTROLS_HIDE_DELAY);
        }
    }

    private void showCenterIcon(boolean playing) {
        ivCenterPlay.setImageResource(playing ? R.drawable.ic_pause : R.drawable.ic_play);
        ivCenterPlay.setAlpha(0f);
        ivCenterPlay.animate()
                .alpha(playing ? 0f : 0.9f)
                .setDuration(200)
                .start();
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initPlayer() {
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);

        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter.Builder(this).build();
        bandwidthMeter.addEventListener(new Handler(Looper.getMainLooper()),
                (elapsedMs, bytesTransferred, bitrateEstimate) ->
                        playbackDiagnostics.onBandwidthSample(bitrateEstimate));

        DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(
                ExoPlayerCache.getDataSourceFactory(this));
        player = new ExoPlayer.Builder(this)
                .setBandwidthMeter(bandwidthMeter)
                .setMediaSourceFactory(sourceFactory)
                .setLoadControl(ExoPlayerCache.createVideoLoadControl())
                .build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                playbackDiagnostics.onPlaybackStateChanged(state, player.getPlayWhenReady());
                if (pendingSeekAfterReadyMs > 0 && state == Player.STATE_READY) {
                    long dur = player.getDuration();
                    long seek = pendingSeekAfterReadyMs;
                    pendingSeekAfterReadyMs = -1;
                    if (dur != C.TIME_UNSET && dur > 0 && seek >= dur - 250) {
                        seek = Math.max(0, dur - 250);
                    }
                    if (seek > 0) {
                        player.seekTo(seek);
                    }
                }
                if (state == Player.STATE_ENDED) {
                    if (isPlayingAd) {
                        // 仅视频广告允许「播完」自动解锁；图片无片长，不得以 Exo 结束态代为解锁
                        if (!adIsImage && !adCountdownPaused) {
                            onAdFinished();
                        }
                        return;
                    }
                    boolean wasLastEpisode = episodes != null && !episodes.isEmpty()
                            && currentEpisodeIndex == episodes.size() - 1;
                    playNextEpisode();
                    lastEpisodeEndedAwaitSwipe = wasLastEpisode
                            && player.getPlaybackState() == Player.STATE_ENDED;
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                if (isPlaying) {
                    lastEpisodeEndedAwaitSwipe = false;
                }
                showCenterIcon(isPlaying);
                if (isPlaying) {
                    scheduleHideControls();
                } else {
                    handler.removeCallbacks(hideControlsRunnable);
                    setControlsVisible(true);
                }
            }

            @Override
            public void onIsLoadingChanged(boolean isLoading) {
                playbackDiagnostics.onIsLoadingChanged(isLoading);
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                playbackDiagnostics.onPlayerError(error);
                android.util.Log.e("PlayerActivity", "ExoPlayer error: " + error.getMessage(), error);
                Toast.makeText(PlayerActivity.this,
                        "播放失败: " + error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });

        handler.post(progressUpdateRunnable);
    }

    private void loadDramaData() {
        final long requestDramaId = dramaId;

        playbackDiagnostics.apiBegin("drama_detail");
        ApiClient.getService().getDramaDetail(dramaId).enqueue(new Callback<ApiResponse<Drama>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Drama>> call,
                                   @NonNull Response<ApiResponse<Drama>> response) {
                boolean ok = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                playbackDiagnostics.apiEnd("drama_detail", ok, response.code(),
                        ok ? null : safeErr(response));
                if (requestDramaId != dramaId || isFinishing()) return;
                if (ok) {
                    try {
                        drama = response.body().getData();
                        if (drama != null) {
                            if (tvTitle != null) tvTitle.setText(drama.getTitle());
                            RankingBadgeUiHelper.bind(tvRankingBadge, drama, PlayerActivity.this, true);
                            String desc = drama.getDescription();
                            storedDramaDescription = desc != null ? desc : "";
                            resetIntroDescriptionUi();
                            if (layoutIntroPanel != null) {
                                layoutIntroPanel.requestLayout();
                            }
                            if (scrollDesc != null) {
                                scrollDesc.requestLayout();
                            }
                            if (tvDesc != null) {
                                tvDesc.requestLayout();
                            }
                            scheduleApplyCollapsedDescInline();
                            refreshEpisodeCountButton();
                        }
                    } catch (RuntimeException e) {
                        android.util.Log.e("PlayerActivity", "apply drama detail UI", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Drama>> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                playbackDiagnostics.apiEnd("drama_detail", false, -1, t.getMessage());
            }
        });

        playbackDiagnostics.apiBegin("episode_list");
        ApiClient.getService().getDramaEpisodes(dramaId).enqueue(new Callback<ApiResponse<List<Episode>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Episode>>> call,
                                   @NonNull Response<ApiResponse<List<Episode>>> response) {
                boolean ok = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                playbackDiagnostics.apiEnd("episode_list", ok, response.code(),
                        ok ? null : safeErr(response));
                if (requestDramaId != dramaId || isFinishing()) return;
                if (ok) {
                    try {
                        episodes = Episode.onlyPlayable(response.body().getData());
                        refreshEpisodeCountButton();
                        if (!episodes.isEmpty()) {
                            playEpisode(computeStartIndexForHandoff());
                        } else {
                            Toast.makeText(PlayerActivity.this, "暂无可播放分集", Toast.LENGTH_LONG).show();
                        }
                    } catch (RuntimeException e) {
                        android.util.Log.e("PlayerActivity", "apply episode list", e);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Episode>>> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                playbackDiagnostics.apiEnd("episode_list", false, -1, t.getMessage());
            }
        });
    }

    /** 选集按钮：已加载列表时用「可播集数」，否则用剧目总集数占位 */
    private void refreshEpisodeCountButton() {
        if (btnEpisodes == null) return;
        int count = (episodes != null && !episodes.isEmpty())
                ? episodes.size()
                : (drama != null ? drama.getTotalEpisodes() : 0);
        btnEpisodes.setText(getString(R.string.player_episodes_format, count));
    }

    private void clearIntroPanelFixedHeight() {
        if (layoutIntroPanel == null) return;
        ViewGroup.LayoutParams lp = layoutIntroPanel.getLayoutParams();
        if (lp != null) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            layoutIntroPanel.setLayoutParams(lp);
        }
    }

    private void resetIntroDescriptionUi() {
        descExpanded = false;
        clearIntroPanelFixedHeight();
        if (tvDesc != null) {
            tvDesc.setMaxLines(1);
            tvDesc.setEllipsize(null);
            tvDesc.setMovementMethod(null);
        }
        if (scrollDesc != null) {
            ViewGroup.LayoutParams lp = scrollDesc.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                scrollDesc.setLayoutParams(lp);
            }
            scrollDesc.setVerticalScrollBarEnabled(false);
            scrollDesc.setOnTouchListener((v, event) -> true);
            scrollDesc.scrollTo(0, 0);
        }
    }

    private void scheduleApplyCollapsedDescInline() {
        if (tvDesc == null || isFinishing() || descExpanded) return;
        tvDesc.post(() -> DescriptionInlineExpandHelper.applyCollapsedFirstLineWithRetry(
                tvDesc, storedDramaDescription,
                () -> {
                    if (isPlayingAd) return;
                    setDescExpanded(true);
                }));
    }

    private void setDescExpanded(boolean expanded) {
        descExpanded = expanded;
        if (layoutIntroPanel == null || scrollDesc == null || tvDesc == null || rootView == null) {
            return;
        }
        if (expanded) {
            DescriptionInlineExpandHelper.applyExpandedWithCollapse(tvDesc, storedDramaDescription,
                    () -> {
                        if (isPlayingAd) return;
                        setDescExpanded(false);
                    });
            scrollDesc.setOnTouchListener(null);
            scrollDesc.setVerticalScrollBarEnabled(false);
            ViewTreeObserver obs = layoutIntroPanel.getViewTreeObserver();
            obs.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    layoutIntroPanel.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    applyExpandedDescScrollHeightInternal();
                }
            });
        } else {
            clearIntroPanelFixedHeight();
            ViewGroup.LayoutParams lp = scrollDesc.getLayoutParams();
            if (lp != null) {
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
                scrollDesc.setLayoutParams(lp);
            }
            scrollDesc.setOnTouchListener((v, event) -> true);
            scrollDesc.scrollTo(0, 0);
            scheduleApplyCollapsedDescInline();
        }
        layoutIntroPanel.requestLayout();
    }

    private void applyExpandedDescScrollHeightInternal() {
        if (!descExpanded || scrollDesc == null || layoutIntroPanel == null || rootView == null
                || tvTitle == null || tvDesc == null || isFinishing()) {
            return;
        }
        int maxPanel = Math.max(rootView.getHeight() / 3, 1);
        int w = layoutIntroPanel.getWidth();
        if (w <= 0) {
            return;
        }
        int wm = View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY);
        int hm = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        layoutIntroPanel.measure(wm, hm);
        int natural = layoutIntroPanel.getMeasuredHeight();
        int panelH = Math.min(Math.max(1, natural), maxPanel);

        ViewGroup.LayoutParams ilp = layoutIntroPanel.getLayoutParams();
        if (ilp != null) {
            ilp.height = panelH;
            layoutIntroPanel.setLayoutParams(ilp);
        }

        float d = getResources().getDisplayMetrics().density;
        int marginDesc = (int) (4 * d);
        int marginBtn = (int) (4 * d);
        int padV = layoutIntroPanel.getPaddingTop() + layoutIntroPanel.getPaddingBottom();
        int used = padV + tvTitle.getHeight() + marginDesc + marginBtn;
        int remaining = Math.max(0, panelH - used);
        ViewGroup.LayoutParams lp = scrollDesc.getLayoutParams();
        if (lp == null) {
            return;
        }
        if (remaining < 1) {
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        } else {
            lp.height = remaining;
        }
        scrollDesc.setLayoutParams(lp);
        scrollDesc.scrollTo(0, 0);
    }

    @Nullable
    private static String safeErr(@NonNull Response<?> response) {
        try {
            if (response.errorBody() != null) {
                return response.errorBody().string();
            }
        } catch (Exception ignored) {}
        return "http " + response.code();
    }

    /**
     * 选集起播（扣次在服务端 getAdVideo，不在此）：
     * ① 免费 / 永久解锁 / 同集 10 分钟 → 直接正片；
     * ② 已登录 + 本集要金币 + 未永久解锁：免广可抵片头或次数未同步 (冷路径) → 不弹二选一，否则弹「金币 / 看广告」
     * （{@link com.hongguo.theater.utils.AdSkipCache#prefetchIfStale} 可提前拉次数、缩小「未同步」窗）；
     * ③ 其它（如未登录）→ 直走片头。
     */
    private void playEpisode(int index) {
        if (episodes == null || index < 0 || index >= episodes.size()) return;
        ExoPlayerCache.cancelPrecache();
        Episode ep = episodes.get(index);
        if (canSkipLockedGate(ep)) {
            playEpisodeDirect(index);
            return;
        }
        pendingEpisodeIndex = index;
        boolean needCoinOrAdDialog = PrefsManager.isLoggedIn()
                && ep.getUnlockCoins() > 0
                && !ep.isCoinUnlocked();
        if (needCoinOrAdDialog) {
            if (AdSkipCache.shouldSkipCoinUnlockDialogForAdSkip(this)) {
                loadAndPlayAd();
            } else {
                showLockedEpisodeChoiceDialog(ep);
            }
        } else {
            loadAndPlayAd();
        }
    }

    /**
     * 是否无需片头/弹窗：免费、或已付金币永久解、或同集 10 分钟内（{@link EpisodeAdTempUnlock}）。
     */
    private boolean canSkipLockedGate(@NonNull Episode ep) {
        return ep.isFree() || ep.isCoinUnlocked() || EpisodeAdTempUnlock.isActive(ep.getId());
    }

    private void showLockedEpisodeChoiceDialog(@NonNull Episode ep) {
        String msg = "本集为付费内容。支付 " + ep.getUnlockCoins()
                + " 金币可永久解锁并免广告观看；或观看广告获得 10 分钟内观看权限。";
        HgDialog.showConfirm(
                this,
                "解锁观看",
                msg,
                "支付 " + ep.getUnlockCoins() + " 金币",
                d -> requestCoinUnlock(ep),
                "观看广告",
                d -> loadAndPlayAd(),
                false,
                null);
    }

    private void requestCoinUnlock(@NonNull Episode ep) {
        if (unlockEpisodeCall != null) {
            unlockEpisodeCall.cancel();
        }
        unlockEpisodeCall = ApiClient.getService().unlockEpisodeWithCoins(ep.getId());
        final long eid = ep.getId();
        unlockEpisodeCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                if (call.isCanceled()) return;
                if (unlockEpisodeCall == call) {
                    unlockEpisodeCall = null;
                }
                if (isFinishing()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    int idx = pendingEpisodeIndex;
                    if (idx >= 0 && idx < episodes.size() && episodes.get(idx).getId() == eid) {
                        episodes.get(idx).setCoinUnlocked(true);
                    }
                    Toast.makeText(PlayerActivity.this, R.string.player_coin_pay_success_toast, Toast.LENGTH_SHORT).show();
                    playEpisodeDirect(idx);
                    pendingEpisodeIndex = -1;
                    return;
                }
                String msg = ApiErrorHelper.parseMessage(response, "解锁失败");
                if (response.code() == 400 && msg != null && msg.contains("金币不足")) {
                    showRechargeDialogForEpisodeUnlock();
                    return;
                }
                Toast.makeText(PlayerActivity.this, msg, Toast.LENGTH_SHORT).show();
                pendingEpisodeIndex = -1;
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                if (unlockEpisodeCall == call) {
                    unlockEpisodeCall = null;
                }
                if (!isFinishing()) {
                    Toast.makeText(PlayerActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                }
                pendingEpisodeIndex = -1;
            }
        });
    }

    private void showRechargeDialogForEpisodeUnlock() {
        HgDialog.showConfirm(
                this,
                "提示",
                "金币余额不足，是否前往充值？",
                "去充值",
                d -> startActivity(new Intent(this, WalletActivity.class)),
                "取消",
                d -> { },
                true,
                null);
    }

    private void playPendingEpisodeDirect() {
        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            playEpisodeDirect(pendingEpisodeIndex);
            pendingEpisodeIndex = -1;
        }
    }

    /** 片头已用免广告次数跳过后，静默拉钱包以更新本地剩余次数 */
    private void refreshWalletForAdSkipDeduction() {
        ApiClient.getService().getWallet().enqueue(new Callback<ApiResponse<WalletBalance>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<WalletBalance>> call,
                                   @NonNull Response<ApiResponse<WalletBalance>> response) {
                if (call.isCanceled() || isFinishing()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AdSkipCache.applyWallet(response.body().getData(), PlayerActivity.this);
                    adSkipActive.set(AdSkipCache.isLocallyAdSkipPlayable(PlayerActivity.this));
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<WalletBalance>> call, @NonNull Throwable t) {}
        });
    }

    private void refreshAdSkipStatus() {
        if (!PrefsManager.isLoggedIn()) {
            adSkipActive.set(false);
            return;
        }
        if (AdSkipCache.isLocallyActive(this) && !AdSkipCache.needsServerRefresh(this)) {
            adSkipActive.set(AdSkipCache.isLocallyAdSkipPlayable(this));
            return;
        }
        if (!AdSkipCache.needsServerRefresh(this)) {
            adSkipActive.set(false);
            return;
        }
        if (adSkipWalletCall != null) {
            adSkipWalletCall.cancel();
            adSkipWalletCall = null;
        }
        adSkipWalletCall = ApiClient.getService().getWallet();
        adSkipWalletCall.enqueue(new Callback<ApiResponse<WalletBalance>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<WalletBalance>> call,
                                   @NonNull Response<ApiResponse<WalletBalance>> response) {
                if (call.isCanceled()) return;
                if (adSkipWalletCall == call) {
                    adSkipWalletCall = null;
                }
                if (isFinishing()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AdSkipCache.applyWallet(response.body().getData(), PlayerActivity.this);
                }
                adSkipActive.set(AdSkipCache.isLocallyAdSkipPlayable(PlayerActivity.this));
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<WalletBalance>> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                if (adSkipWalletCall == call) {
                    adSkipWalletCall = null;
                }
                if (!isFinishing()) {
                    adSkipActive.set(false);
                }
            }
        });
    }

    /** 购买跳过卡成功后由钱包页调用：立即按免广告生效，必要时中断当前广告并直播正片 */
    public void applyAdSkipFromCache() {
        if (!PrefsManager.isLoggedIn()) {
            adSkipActive.set(false);
            return;
        }
        adSkipActive.set(AdSkipCache.isLocallyAdSkipPlayable(this));
        if (!adSkipActive.get()) return;
        if (pendingEpisodeIndex < 0 || episodes == null || pendingEpisodeIndex >= episodes.size()) return;
        Episode ep = episodes.get(pendingEpisodeIndex);
        if (ep.isFree() || ep.isCoinUnlocked() || EpisodeAdTempUnlock.isActive(ep.getId())) return;
        if (isPlayingAd) {
            cancelAdCountdown();
            isPlayingAd = false;
            adIsImage = false;
            adCanClose = false;
            adCountdownPaused = false;
            showAdUI(false);
            if (btnCloseAd != null) btnCloseAd.setVisibility(View.GONE);
            clearAdImageOverlay();
            ExoPlayerCache.cancelPrecache();
            if (player != null) player.stop();
        }
        playEpisodeDirect(pendingEpisodeIndex);
        pendingEpisodeIndex = -1;
    }

    private void playEpisodeDirect(int index) {
        if (episodes == null || index < 0 || index >= episodes.size()) return;
        lastEpisodeEndedAwaitSwipe = false;
        currentEpisodeIndex = index;
        Episode ep = episodes.get(index);

        pendingSeekAfterReadyMs = -1;
        String url = ApiClient.getStreamUrl(ep);
        if (feedHandoffEpisodeId != 0 && ep.getId() == feedHandoffEpisodeId) {
            if (feedHandoffStreamUrl != null && !feedHandoffStreamUrl.isEmpty()) {
                url = feedHandoffStreamUrl;
            }
            if (feedHandoffPositionMs > 0L) {
                pendingSeekAfterReadyMs = feedHandoffPositionMs;
            }
            feedHandoffEpisodeId = 0L;
            feedHandoffPositionMs = 0L;
            feedHandoffStreamUrl = null;
        }

        isPlayingAd = false;
        adIsImage = false;
        cancelAdCountdown();
        showAdUI(false);
        clearAdImageOverlay();

        android.util.Log.d("PlayerActivity", "Playing URL: " + url);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        playbackDiagnostics.onPrepareStarted();
        player.prepare();
        player.play();

        if (seekBar != null) seekBar.setProgress(0);

        int denom = episodes != null ? episodes.size() : 0;
        if (denom <= 0 && drama != null) {
            denom = drama.getTotalEpisodes();
        }
        tvEpisodeInfo.setText(String.format("第 %d / %d 集", ep.getEpisodeNumber(), denom));
        tvLikeCount.setText(ep.getLikeCountText());
        tvCommentCount.setText(ep.getCommentCountText());

        currentEpisodeLiked = false;
        likeRequestInFlight = false;
        btnLike.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        btnCollect.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

        if (PrefsManager.isLoggedIn()) {
            loadInteractionState(ep.getId());
            recordWatchHistory(ep.getId());
        }

        precacheNextEpisode(index);
    }

    private void precacheNextEpisode(int currentIndex) {
        if (episodes == null) return;
        int nextIndex = currentIndex + 1;
        if (nextIndex >= episodes.size()) return;

        Episode next = episodes.get(nextIndex);
        String nextUrl = ApiClient.getStreamUrl(next);
        ExoPlayerCache.precache(this, nextUrl);
    }

    private void recordWatchHistory(long episodeId) {
        playbackDiagnostics.apiBegin("record_history");
        ApiClient.getService().recordHistory(episodeId)
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override public void onResponse(@NonNull Call<ApiResponse<Void>> c, @NonNull Response<ApiResponse<Void>> r) {
                        boolean ok = r.isSuccessful() && r.body() != null && r.body().isSuccess();
                        playbackDiagnostics.apiEnd("record_history", ok, r.code(), ok ? null : safeErr(r));
                    }
                    @Override public void onFailure(@NonNull Call<ApiResponse<Void>> c, @NonNull Throwable t) {
                        if (c.isCanceled()) return;
                        playbackDiagnostics.apiEnd("record_history", false, -1, t.getMessage());
                    }
                });
    }

    private void loadInteractionState(long episodeId) {
        if (interactionCall != null) {
            interactionCall.cancel();
            interactionCall = null;
        }
        playbackDiagnostics.apiBegin("interaction");
        interactionCall = ApiClient.getService().getEpisodeInteraction(episodeId);
        final long forEpisode = episodeId;
        interactionCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                           @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                        if (call.isCanceled()) return;
                        boolean ok = r.isSuccessful() && r.body() != null && r.body().isSuccess();
                        playbackDiagnostics.apiEnd("interaction", ok, r.code(), ok ? null : safeErr(r));
                        if (interactionCall == call) {
                            interactionCall = null;
                        }
                        if (isFinishing()) return;
                        if (episodes == null || currentEpisodeIndex >= episodes.size()
                                || episodes.get(currentEpisodeIndex).getId() != forEpisode) {
                            return;
                        }
                        if (ok) {
                            Map<String, Object> data = r.body().getData();
                            if (data != null) {
                                boolean liked = Boolean.TRUE.equals(data.get("liked"));
                                boolean collected = Boolean.TRUE.equals(data.get("collected"));
                                currentEpisodeLiked = liked;
                                btnLike.setColorFilter(liked ? Color.RED : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                btnCollect.setColorFilter(collected ? Color.parseColor("#FFC107") : Color.WHITE, PorterDuff.Mode.SRC_IN);
                            }
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                        if (call.isCanceled()) return;
                        playbackDiagnostics.apiEnd("interaction", false, -1, t.getMessage());
                        if (interactionCall == call) {
                            interactionCall = null;
                        }
                    }
                });
    }

    // region Ad playback
    private void loadAndPlayAd() {
        if (PrefsManager.isLoggedIn() && pendingEpisodeIndex >= 0 && episodes != null
                && pendingEpisodeIndex < episodes.size()) {
            long eid = episodes.get(pendingEpisodeIndex).getId();
            if (EpisodeAdTempUnlock.isActive(eid)) {
                playPendingEpisodeDirect();
                return;
            }
        }
        // 已登录须请求带分集 id 的接口，才能在服务端完成扣次/免广；预缓存未带 episode_id 不可走缓存路径
        if (PrefsManager.isLoggedIn()) {
            fetchAndPlayAd();
            return;
        }
        if (AdPreloader.isReady()) {
            playAdFromCache();
        } else {
            fetchAndPlayAd();
        }
    }

    private void playAdFromCache() {
        String fullUrl = AdPreloader.getFullUrl();
        int duration = AdPreloader.getDuration();
        boolean image = "image".equals(AdPreloader.getMediaType());
        AdPreloader.consume(this);
        if (image) {
            startAdImagePlayback(fullUrl, duration);
        } else {
            startAdPlayback(fullUrl, duration);
        }
    }

    private void fetchAndPlayAd() {
        playbackDiagnostics.apiBegin("ad_video");
        final Long epParam;
        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            epParam = episodes.get(pendingEpisodeIndex).getId();
        } else {
            epParam = null;
        }
        ApiClient.getService().getAdVideo(epParam).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                boolean ok = response.isSuccessful() && response.body() != null && response.body().isSuccess();
                playbackDiagnostics.apiEnd("ad_video", ok, response.code(), ok ? null : safeErr(response));
                if (ok) {
                    Map<String, Object> data = response.body().getData();
                    if (Boolean.TRUE.equals(data.get("skip_ad"))) {
                        if (epParam != null) {
                            EpisodeAdTempUnlock.grantAfterAd(epParam);
                        }
                        if (PrefsManager.isLoggedIn()) {
                            refreshWalletForAdSkipDeduction();
                        }
                        playPendingEpisodeDirect();
                        return;
                    }
                    int duration = 15;
                    Object durObj = data.get("duration");
                    if (durObj instanceof Double) {
                        duration = ((Double) durObj).intValue();
                    } else if (durObj instanceof Integer) {
                        duration = (Integer) durObj;
                    } else if (durObj instanceof Long) {
                        duration = ((Long) durObj).intValue();
                    }

                    // 仅以 media_type 决定图片或视频；解锁倒计时与关闭逻辑共用
                    String mt = "video";
                    Object mtRaw = data.get("media_type");
                    if (mtRaw instanceof String && !((String) mtRaw).isEmpty()) {
                        mt = ((String) mtRaw).toLowerCase().trim();
                    }
                    if ("image".equals(mt)) {
                        String fullImg = ImageUrlUtils.resolve((String) data.get("image_url"));
                        startAdImagePlayback(fullImg, duration);
                    } else {
                        String videoUrl = (String) data.get("video_url");
                        String base = BuildConfig.BASE_URL.replace("/api/v1/", "");
                        String fullUrl;
                        if (videoUrl != null && videoUrl.startsWith("http")) {
                            fullUrl = videoUrl;
                        } else {
                            fullUrl = base + (videoUrl != null ? videoUrl : "");
                        }
                        startAdPlayback(fullUrl, duration);
                    }
                } else {
                    playPendingEpisodeDirect();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                if (call.isCanceled()) return;
                playbackDiagnostics.apiEnd("ad_video", false, -1, t.getMessage());
                playPendingEpisodeDirect();
            }
        });
    }

    /** 仅移除全屏广告图（adIsImage 由调用方维护） */
    private void clearAdImageOverlay() {
        if (ivAdCreative != null) {
            ivAdCreative.setVisibility(View.GONE);
            if (!isFinishing()) {
                Glide.with(PlayerActivity.this).clear(ivAdCreative);
            }
        }
    }

    private void cancelAdCountdown() {
        if (adCountdownRunnable != null) {
            handler.removeCallbacks(adCountdownRunnable);
            adCountdownRunnable = null;
        }
    }

    private void startAdImagePlayback(@Nullable String fullImageUrl, int durationSec) {
        if (TextUtils.isEmpty(fullImageUrl)) {
            playPendingEpisodeDirect();
            return;
        }
        cancelAdCountdown();
        adIsImage = true;
        isPlayingAd = true;
        adCanClose = false;
        adCountdownPaused = false;
        showAdUI(true);
        btnCloseAd.setVisibility(View.VISIBLE);
        btnCloseAd.setText("跳过");
        // 图片无片长，解锁仅依赖后台配置的展示秒数；至少 1 秒避免误判为「立即可关」
        int showSec = Math.max(1, durationSec);
        updateAdCountdown(showSec);

        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            String pendingUrl = ApiClient.getStreamUrl(episodes.get(pendingEpisodeIndex));
            ExoPlayerCache.precache(this, pendingUrl);
        }

        if (player != null) {
            player.stop();
            player.clearMediaItems();
        }
        if (ivAdCreative != null) {
            ivAdCreative.setVisibility(View.VISIBLE);
            Glide.with(this).load(fullImageUrl).centerCrop().into(ivAdCreative);
        }
    }

    private void startAdPlayback(String fullUrl, int durationSec) {
        cancelAdCountdown();
        adIsImage = false;
        if (ivAdCreative != null) {
            ivAdCreative.setVisibility(View.GONE);
            if (!isFinishing()) {
                Glide.with(this).clear(ivAdCreative);
            }
        }
        isPlayingAd = true;
        adCanClose = false;
        adCountdownPaused = false;
        showAdUI(true);
        btnCloseAd.setVisibility(View.VISIBLE);
        btnCloseAd.setText("跳过");
        updateAdCountdown(Math.max(0, durationSec));

        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            String pendingUrl = ApiClient.getStreamUrl(episodes.get(pendingEpisodeIndex));
            ExoPlayerCache.precache(this, pendingUrl);
        }

        player.setMediaItem(MediaItem.fromUri(Uri.parse(fullUrl)));
        playbackDiagnostics.onPrepareStarted();
        player.prepare();
        player.play();
    }

    private void updateAdCountdown(int totalSec) {
        cancelAdCountdown();
        final int[] remaining = {totalSec};
        adCountdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPlayingAd || isFinishing()) return;
                if (adCountdownPaused) {
                    handler.postDelayed(this, 500);
                    return;
                }
                if (remaining[0] > 0) {
                    tvAdLabel.setText("广告 " + remaining[0] + "s");
                    remaining[0]--;
                    handler.postDelayed(this, 1000);
                } else {
                    tvAdLabel.setText("广告");
                    adCanClose = true;
                    btnCloseAd.setText("关闭广告");
                    adCountdownRunnable = null;
                }
            }
        };
        handler.post(adCountdownRunnable);
    }

    private void showSkipAdDialog() {
        adCountdownPaused = true;
        if (player != null && player.isPlaying()) player.pause();
        HgDialog.showConfirm(
                this,
                "提示",
                "关闭广告将无法获得本集 10 分钟观看权限，确定要放弃吗？",
                "继续观看",
                d -> {
                    adCountdownPaused = false;
                    if (!adIsImage && player != null) player.play();
                },
                "放弃解锁",
                d -> {
                    cancelAdCountdown();
                    isPlayingAd = false;
                    adIsImage = false;
                    adCanClose = false;
                    adCountdownPaused = false;
                    showAdUI(false);
                    btnCloseAd.setVisibility(View.GONE);
                    clearAdImageOverlay();
                    pendingEpisodeIndex = -1;
                    ExoPlayerCache.cancelPrecache();
                    if (player != null) player.stop();
                },
                false,
                null);
    }

    private void onAdFinished() {
        cancelAdCountdown();
        isPlayingAd = false;
        adIsImage = false;
        adCanClose = false;
        showAdUI(false);
        btnCloseAd.setVisibility(View.GONE);
        clearAdImageOverlay();
        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            Episode pend = episodes.get(pendingEpisodeIndex);
            EpisodeAdTempUnlock.grantAfterAd(pend.getId());
            playEpisodeDirect(pendingEpisodeIndex);
            pendingEpisodeIndex = -1;
        }
    }

    private void showAdUI(boolean showAd) {
        if (tvAdLabel != null) tvAdLabel.setVisibility(showAd ? View.VISIBLE : View.GONE);
        if (layoutBottomOverlay != null) {
            layoutBottomOverlay.setVisibility(showAd ? View.GONE : View.VISIBLE);
        }
        if (layoutPlayerDock != null) {
            layoutPlayerDock.setVisibility(showAd ? View.GONE : View.VISIBLE);
        }
        if (layoutRightButtons != null) layoutRightButtons.setVisibility(showAd ? View.GONE : View.VISIBLE);
        if (layoutTopBar != null) layoutTopBar.setVisibility(showAd ? View.VISIBLE : View.VISIBLE);
    }
    // endregion

    // region Swipe episodes（跟手拖拽 + 吸附，对齐刷剧 ViewPager2）
    private boolean onPlayerViewTouch(View v, MotionEvent e) {
        if (isPlayingAd || isSwiping) {
            gestureDetector.onTouchEvent(e);
            return true;
        }
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                episodeTouchDownX = e.getX();
                episodeTouchDownY = e.getY();
                episodeDragLastY = e.getY();
                episodeDragTranslationY = getEpisodeSlideTranslationY();
                episodeDragActive = false;
                episodeTouchDecided = false;
                if (episodeVelocityTracker == null) {
                    episodeVelocityTracker = VelocityTracker.obtain();
                } else {
                    episodeVelocityTracker.clear();
                }
                episodeVelocityTracker.addMovement(e);
                gestureDetector.onTouchEvent(e);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (episodeVelocityTracker != null) {
                    episodeVelocityTracker.addMovement(e);
                }
                if (!episodeDragActive) {
                    float dx = e.getX() - episodeTouchDownX;
                    float dy = e.getY() - episodeTouchDownY;
                    if (!episodeTouchDecided
                            && (Math.abs(dx) > episodeTouchSlop || Math.abs(dy) > episodeTouchSlop)) {
                        episodeTouchDecided = true;
                        if (Math.abs(dy) > Math.abs(dx) && canRunEpisodeSlideAnimation()) {
                            episodeDragActive = true;
                            cancelEpisodeSlideAnimations();
                            episodeDragLastY = e.getY();
                            episodeDragTranslationY = getEpisodeSlideTranslationY();
                        }
                    }
                }
                if (episodeDragActive) {
                    float dy = e.getY() - episodeDragLastY;
                    episodeDragLastY = e.getY();
                    episodeDragTranslationY = clampEpisodeDragTranslation(episodeDragTranslationY + dy);
                    setEpisodeSlideTranslationY(episodeDragTranslationY);
                    return true;
                }
                gestureDetector.onTouchEvent(e);
                return true;
            case MotionEvent.ACTION_UP:
                if (episodeVelocityTracker != null) {
                    episodeVelocityTracker.addMovement(e);
                    episodeVelocityTracker.computeCurrentVelocity(1000);
                }
                if (episodeDragActive) {
                    float vy = episodeVelocityTracker != null ? episodeVelocityTracker.getYVelocity() : 0f;
                    settleEpisodeDrag(vy);
                    recycleEpisodeVelocityTracker();
                    episodeDragActive = false;
                    episodeTouchDecided = false;
                    return true;
                }
                gestureDetector.onTouchEvent(e);
                recycleEpisodeVelocityTracker();
                episodeTouchDecided = false;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (episodeDragActive) {
                    runSpringBackEpisodeSlide(getEpisodeSlideTranslationY());
                    episodeDragActive = false;
                }
                recycleEpisodeVelocityTracker();
                episodeTouchDecided = false;
                gestureDetector.onTouchEvent(e);
                return true;
            default:
                if (episodeVelocityTracker != null) {
                    episodeVelocityTracker.addMovement(e);
                }
                gestureDetector.onTouchEvent(e);
                return true;
        }
    }

    private void recycleEpisodeVelocityTracker() {
        if (episodeVelocityTracker != null) {
            episodeVelocityTracker.recycle();
            episodeVelocityTracker = null;
        }
    }

    private float getEpisodeSlideTranslationY() {
        return layoutVideoArea != null ? layoutVideoArea.getTranslationY() : 0f;
    }

    /**
     * 手指下移 → translationY 增大（画面上移露出下方）；首集继续下拉阻尼。
     */
    private float clampEpisodeDragTranslation(float ty) {
        if (rootView == null) return ty;
        int h = Math.max(rootView.getHeight(), 1);
        float max = h * 1.15f;
        if (currentEpisodeIndex == 0 && !lastEpisodeEndedAwaitSwipe && ty > 0) {
            return rubberBandOverscroll(ty, h * 0.4f);
        }
        if (ty > max) return max;
        if (ty < -max) return -max;
        return ty;
    }

    private static float rubberBandOverscroll(float overscroll, float range) {
        if (overscroll <= 0f || range <= 1f) return overscroll;
        return range * (1f - (float) Math.exp(-overscroll / range));
    }

    private void settleEpisodeDrag(float velocityY) {
        float ty = getEpisodeSlideTranslationY();
        int h = rootView != null ? rootView.getHeight() : 0;
        if (h <= 0) {
            resetEpisodeSlideTranslations();
            return;
        }
        float threshold = h * EPISODE_SNAP_DISTANCE_FRACTION;
        boolean wantNext = ty < -threshold || velocityY < -EPISODE_SNAP_VELOCITY;
        boolean wantPrevOrExitDown = ty > threshold || velocityY > EPISODE_SNAP_VELOCITY;

        if (wantNext) {
            if (isOnLastPlayableEpisode()) {
                swipeFromLastEpisodeToNextDramaInFeedInteractive(ty);
            } else {
                swipeToEpisodeInteractive(currentEpisodeIndex + 1, true, ty);
            }
            return;
        }
        if (wantPrevOrExitDown) {
            if (lastEpisodeEndedAwaitSwipe) {
                lastEpisodeEndedAwaitSwipe = false;
                swipeReturnToFeedInteractive(ty);
            } else if (currentEpisodeIndex > 0) {
                swipeToEpisodeInteractive(currentEpisodeIndex - 1, false, ty);
            } else {
                runSpringBackEpisodeSlide(ty);
            }
            return;
        }
        runSpringBackEpisodeSlide(ty);
    }

    private void runSpringBackEpisodeSlide(float fromTy) {
        if (Math.abs(fromTy) < 1.5f) {
            resetEpisodeSlideTranslations();
            return;
        }
        isSwiping = true;
        cancelEpisodeSlideAnimations();
        int h = Math.max(rootView != null ? rootView.getHeight() : 0, 1);
        long dur = computeEpisodeSettleDuration(fromTy, 0f, h);
        runVerticalSlideTogether(fromTy, 0f, dur, episodeSlideInterpolator, () -> isSwiping = false);
    }

    private long computeEpisodeSettleDuration(float fromY, float toY, int height) {
        float span = Math.abs(toY - fromY) / Math.max(height, 1);
        return (long) Math.min(420, Math.max(160, 160 + span * EPISODE_SWITCH_DURATION_MS));
    }

    private void swipeToEpisodeInteractive(int targetIndex, boolean swipeUp, float fromTy) {
        if (episodes == null || targetIndex < 0 || targetIndex >= episodes.size()) {
            Toast.makeText(this, swipeUp ? "已经是最后一集了" : "已经是第一集了", Toast.LENGTH_SHORT).show();
            runSpringBackEpisodeSlide(fromTy);
            return;
        }
        if (!canRunEpisodeSlideAnimation()) {
            playEpisode(targetIndex);
            return;
        }
        isSwiping = true;
        cancelEpisodeSlideAnimations();
        int h = rootView.getHeight();
        float exitY = swipeUp ? -h : h;
        long durOut = computeEpisodeSettleDuration(fromTy, exitY, h);
        runVerticalSlideTogether(fromTy, exitY, durOut, episodeSlideInterpolator, () -> {
            playEpisode(targetIndex);
            setEpisodeSlideTranslationY(-exitY);
            long durIn = computeEpisodeSettleDuration(-exitY, 0f, h);
            runVerticalSlideTogether(-exitY, 0f, durIn, episodeSlideInterpolator, () -> isSwiping = false);
        });
    }

    private void swipeFromLastEpisodeToNextDramaInFeedInteractive(float fromTy) {
        lastEpisodeEndedAwaitSwipe = false;
        if (!canRunEpisodeSlideAnimation()) {
            returnToPlayFeedWithNextDrama();
            return;
        }
        isSwiping = true;
        cancelEpisodeSlideAnimations();
        int h = rootView.getHeight();
        float exitY = -h;
        long dur = computeEpisodeSettleDuration(fromTy, exitY, h);
        runVerticalSlideTogether(fromTy, exitY, dur, episodeSlideInterpolator, () -> {
            isSwiping = false;
            resetEpisodeSlideTranslations();
            returnToPlayFeedWithNextDrama();
        });
    }

    private void swipeReturnToFeedInteractive(float fromTy) {
        if (!canRunEpisodeSlideAnimation()) {
            returnToPlayFeedWithNextDrama();
            return;
        }
        isSwiping = true;
        cancelEpisodeSlideAnimations();
        int h = rootView.getHeight();
        long dur = computeEpisodeSettleDuration(fromTy, h, h);
        runVerticalSlideTogether(fromTy, h, dur, episodeSlideInterpolator, () -> {
            isSwiping = false;
            resetEpisodeSlideTranslations();
            returnToPlayFeedWithNextDrama();
        });
    }

    private boolean isOnLastPlayableEpisode() {
        return episodes != null && !episodes.isEmpty()
                && currentEpisodeIndex == episodes.size() - 1;
    }

    /** 最后一集时上滑：退场动画后回刷剧并切到 Feed 中下一部剧 */
    private void swipeFromLastEpisodeToNextDramaInFeed() {
        lastEpisodeEndedAwaitSwipe = false;
        swipeFromLastEpisodeToNextDramaInFeedInteractive(0f);
    }

    private void swipeToEpisode(int targetIndex, boolean swipeUp) {
        swipeToEpisodeInteractive(targetIndex, swipeUp, 0f);
    }

    private boolean canRunEpisodeSlideAnimation() {
        return layoutVideoArea != null && layoutPlayerDock != null && rootView != null;
    }

    private void cancelEpisodeSlideAnimations() {
        if (layoutVideoArea != null) layoutVideoArea.animate().cancel();
        if (layoutPlayerDock != null) layoutPlayerDock.animate().cancel();
    }

    private void resetEpisodeSlideTranslations() {
        setEpisodeSlideTranslationY(0f);
    }

    private void setEpisodeSlideTranslationY(float y) {
        if (layoutVideoArea != null) layoutVideoArea.setTranslationY(y);
        if (layoutPlayerDock != null) layoutPlayerDock.setTranslationY(y);
    }

    private void runVerticalSlideTogether(float fromY, float toY, long durationMs, Runnable onEnd) {
        runVerticalSlideTogether(fromY, toY, durationMs, episodeSlideInterpolator, onEnd);
    }

    private void runVerticalSlideTogether(
            float fromY, float toY, long durationMs,
            FastOutSlowInInterpolator interpolator,
            Runnable onEnd) {
        if (!canRunEpisodeSlideAnimation()) {
            if (onEnd != null) onEnd.run();
            return;
        }
        setEpisodeSlideTranslationY(fromY);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(
                ObjectAnimator.ofFloat(layoutVideoArea, View.TRANSLATION_Y, fromY, toY),
                ObjectAnimator.ofFloat(layoutPlayerDock, View.TRANSLATION_Y, fromY, toY));
        set.setDuration(durationMs);
        set.setInterpolator(interpolator);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (onEnd != null) onEnd.run();
            }
        });
        set.start();
    }

    private void playNextEpisode() {
        if (episodes != null && currentEpisodeIndex < episodes.size() - 1) {
            playEpisode(currentEpisodeIndex + 1);
        }
    }

    private void returnToPlayFeedWithNextDrama() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        i.putExtra("open_play_tab", true);
        i.putExtra("after_drama_id", dramaId);
        startActivity(i);
        finish();
    }
    // endregion

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        cancelEpisodeSlideAnimations();
        recycleEpisodeVelocityTracker();
        playbackDiagnostics.flushLoading();
        if (interactionCall != null) {
            interactionCall.cancel();
            interactionCall = null;
        }
        if (adSkipWalletCall != null) {
            adSkipWalletCall.cancel();
            adSkipWalletCall = null;
        }
        if (unlockEpisodeCall != null) {
            unlockEpisodeCall.cancel();
            unlockEpisodeCall = null;
        }
        ExoPlayerCache.cancelPrecache();
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
