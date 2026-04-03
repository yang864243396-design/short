package com.hongguo.theater.ui.player;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.utils.LoginHelper;
import com.hongguo.theater.utils.ExoPlayerCache;
import com.hongguo.theater.utils.PrefsManager;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvTitle, tvDesc, tvEpisodeInfo, tvLikeCount, tvCommentCount;
    private ImageView btnLike, btnComment, btnCollect, btnShare, btnBack;
    private TextView btnEpisodes;

    private ImageView ivCenterPlay;
    private SeekBar seekBar;
    private View layoutTopBar;

    private TextView tvAdLabel, btnCloseAd;
    private View layoutBottomInfo, layoutRightButtons;
    private boolean adCanClose = false;

    private View rootView;
    private GestureDetectorCompat gestureDetector;
    private boolean isSwiping = false;

    private boolean controlsVisible = true;
    private boolean isUserSeeking = false;
    private static final int CONTROLS_HIDE_DELAY = 3000;
    private static final int PROGRESS_UPDATE_INTERVAL = 200;

    private long dramaId;
    private Drama drama;
    private List<Episode> episodes;
    private int currentEpisodeIndex = 0;
    private boolean isPlayingAd = false;
    private boolean adCountdownPaused = false;
    private int pendingEpisodeIndex = -1;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable hideControlsRunnable = () -> setControlsVisible(false);

    private final Runnable progressUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (player != null && !isUserSeeking && !isPlayingAd) {
                long pos = player.getCurrentPosition();
                long dur = player.getDuration();
                if (dur > 0) {
                    seekBar.setProgress((int) (pos * 1000 / dur));
                }
            }
            handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_player);

        dramaId = getIntent().getLongExtra("drama_id", 0);

        initViews();
        initPlayer();
        loadDramaData();
    }

    private void initViews() {
        playerView = findViewById(R.id.player_view);
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
        layoutBottomInfo = findViewById(R.id.layout_bottom_info);
        layoutRightButtons = findViewById(R.id.layout_right_buttons);
        layoutTopBar = findViewById(R.id.layout_top_bar);

        ivCenterPlay = findViewById(R.id.iv_center_play);
        seekBar = findViewById(R.id.seek_bar);

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
            if (drama != null && episodes != null) {
                EpisodeBottomSheet sheet = EpisodeBottomSheet.newInstance(
                        drama.getTitle(), episodes, currentEpisodeIndex);
                sheet.setOnEpisodeClickListener(this::playEpisode);
                sheet.show(getSupportFragmentManager(), "episodes");
            }
        });

        btnComment.setOnClickListener(v -> {
            if (isPlayingAd) return;
            if (!LoginHelper.requireLogin(this)) return;
            if (episodes != null && currentEpisodeIndex < episodes.size()) {
                Episode ep = episodes.get(currentEpisodeIndex);
                CommentBottomSheet sheet = CommentBottomSheet.newInstance(ep.getId());
                sheet.setOnCommentPostedListener(() -> {
                    ep.setCommentCount(ep.getCommentCount() + 1);
                    tvCommentCount.setText(ep.getCommentCountText());
                });
                sheet.show(getSupportFragmentManager(), "comments");
            }
        });

        btnLike.setOnClickListener(v -> {
            if (isPlayingAd) return;
            if (!LoginHelper.requireLogin(this)) return;
            if (episodes != null && currentEpisodeIndex < episodes.size()) {
                Episode ep = episodes.get(currentEpisodeIndex);
                ApiClient.getService().likeEpisode(ep.getId())
                        .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                            @Override
                            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                                   @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                                if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                    Map<String, Object> data = r.body().getData();
                                    boolean liked = data != null && Boolean.TRUE.equals(data.get("liked"));
                                    btnLike.setColorFilter(liked ? Color.RED : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                    long count = ep.getLikeCount() + (liked ? 1 : -1);
                                    if (count < 0) count = 0;
                                    ep.setLikeCount(count);
                                    tvLikeCount.setText(ep.getLikeCountText());
                                }
                            }
                            @Override
                            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {}
                        });
            }
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
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (isPlayingAd || isSwiping) return false;
                if (e1 == null) return false;
                float dy = e1.getY() - e2.getY();
                float dx = e1.getX() - e2.getX();
                if (Math.abs(dy) > Math.abs(dx) && Math.abs(dy) > 120 && Math.abs(velocityY) > 300) {
                    if (dy > 0) swipeToEpisode(currentEpisodeIndex + 1, true);
                    else swipeToEpisode(currentEpisodeIndex - 1, false);
                    return true;
                }
                return false;
            }

            @Override
            public boolean onDown(MotionEvent e) { return true; }
        });

        playerView.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            return true;
        });

        ivCenterPlay.setOnClickListener(v -> {
            if (isPlayingAd) return;
            togglePlayPause();
        });
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
        layoutBottomInfo.animate().alpha(alpha).setDuration(duration).start();

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
        DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(
                ExoPlayerCache.getDataSourceFactory(this));
        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(sourceFactory)
                .build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    if (isPlayingAd) onAdFinished();
                    else playNextEpisode();
                }
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                showCenterIcon(isPlaying);
                if (isPlaying) {
                    scheduleHideControls();
                } else {
                    handler.removeCallbacks(hideControlsRunnable);
                    setControlsVisible(true);
                }
            }
        });

        handler.post(progressUpdateRunnable);
    }

    private void loadDramaData() {
        ApiClient.getService().getDramaDetail(dramaId).enqueue(new Callback<ApiResponse<Drama>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Drama>> call,
                                   @NonNull Response<ApiResponse<Drama>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    drama = response.body().getData();
                    tvTitle.setText(drama.getTitle());
                    tvDesc.setText(drama.getDescription());
                    btnEpisodes.setText(getString(R.string.player_episodes_format, drama.getTotalEpisodes()));
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Drama>> call, @NonNull Throwable t) {}
        });

        ApiClient.getService().getDramaEpisodes(dramaId).enqueue(new Callback<ApiResponse<List<Episode>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Episode>>> call,
                                   @NonNull Response<ApiResponse<List<Episode>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    episodes = response.body().getData();
                    if (!episodes.isEmpty()) playEpisode(0);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Episode>>> call, @NonNull Throwable t) {}
        });
    }

    private void playEpisode(int index) {
        if (episodes == null || index >= episodes.size()) return;
        ExoPlayerCache.cancelPrecache();
        Episode ep = episodes.get(index);
        if (ep.isFree() || PrefsManager.isEpisodeUnlocked(ep.getId())) {
            playEpisodeDirect(index);
        } else {
            pendingEpisodeIndex = index;
            loadAndPlayAd();
        }
    }

    private void playEpisodeDirect(int index) {
        if (episodes == null || index >= episodes.size()) return;
        currentEpisodeIndex = index;
        Episode ep = episodes.get(index);

        isPlayingAd = false;
        showAdUI(false);

        String url = ApiClient.getStreamUrl(ep);
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();

        seekBar.setProgress(0);

        tvEpisodeInfo.setText(String.format("第 %d / %d 集",
                ep.getEpisodeNumber(), drama != null ? drama.getTotalEpisodes() : episodes.size()));
        tvLikeCount.setText(ep.getLikeCountText());
        tvCommentCount.setText(ep.getCommentCountText());

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
        ApiClient.getService().recordHistory(episodeId)
                .enqueue(new Callback<ApiResponse<Void>>() {
                    @Override public void onResponse(@NonNull Call<ApiResponse<Void>> c, @NonNull Response<ApiResponse<Void>> r) {}
                    @Override public void onFailure(@NonNull Call<ApiResponse<Void>> c, @NonNull Throwable t) {}
                });
    }

    private void loadInteractionState(long episodeId) {
        ApiClient.getService().getEpisodeInteraction(episodeId)
                .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                           @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                        if (isFinishing()) return;
                        if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                            Map<String, Object> data = r.body().getData();
                            if (data != null) {
                                boolean liked = Boolean.TRUE.equals(data.get("liked"));
                                boolean collected = Boolean.TRUE.equals(data.get("collected"));
                                btnLike.setColorFilter(liked ? Color.RED : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                btnCollect.setColorFilter(collected ? Color.parseColor("#FFC107") : Color.WHITE, PorterDuff.Mode.SRC_IN);
                            }
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {}
                });
    }

    // region Ad playback
    private void loadAndPlayAd() {
        ApiClient.getService().getAdVideo().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Map<String, Object> data = response.body().getData();
                    String videoUrl = (String) data.get("video_url");
                    int duration = 15;
                    if (data.get("duration") instanceof Double)
                        duration = ((Double) data.get("duration")).intValue();
                    playAdVideo(videoUrl, duration);
                } else {
                    unlockAndPlay();
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                unlockAndPlay();
            }
        });
    }

    private void playAdVideo(String videoUrl, int durationSec) {
        isPlayingAd = true;
        adCanClose = false;
        adCountdownPaused = false;
        showAdUI(true);
        btnCloseAd.setVisibility(View.VISIBLE);
        btnCloseAd.setText("跳过");
        updateAdCountdown(durationSec);

        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            String pendingUrl = ApiClient.getStreamUrl(episodes.get(pendingEpisodeIndex));
            ExoPlayerCache.precache(this, pendingUrl);
        }

        String base = BuildConfig.BASE_URL.replace("/api/v1/", "");
        String fullUrl;
        if (videoUrl.startsWith("http")) {
            fullUrl = videoUrl;
        } else if (videoUrl.startsWith("/api/")) {
            fullUrl = base + videoUrl;
        } else {
            fullUrl = base + videoUrl;
        }
        player.setMediaItem(MediaItem.fromUri(Uri.parse(fullUrl)));
        player.prepare();
        player.play();
    }

    private void updateAdCountdown(int totalSec) {
        final int[] remaining = {totalSec};
        Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (!isPlayingAd || isFinishing()) return;
                if (adCountdownPaused) { handler.postDelayed(this, 500); return; }
                if (remaining[0] > 0) {
                    tvAdLabel.setText("广告 " + remaining[0] + "s");
                    remaining[0]--;
                    handler.postDelayed(this, 1000);
                } else {
                    tvAdLabel.setText("广告");
                    adCanClose = true;
                    btnCloseAd.setText("关闭广告");
                }
            }
        };
        handler.post(tick);
    }

    private void showSkipAdDialog() {
        adCountdownPaused = true;
        if (player != null && player.isPlaying()) player.pause();
        new AlertDialog.Builder(this)
                .setTitle("提示")
                .setMessage("关闭广告将无法解锁当前集数，确定要放弃解锁吗？")
                .setPositiveButton("继续观看", (d, w) -> { adCountdownPaused = false; if (player != null) player.play(); })
                .setNegativeButton("放弃解锁", (d, w) -> {
                    isPlayingAd = false; adCanClose = false; adCountdownPaused = false;
                    showAdUI(false); btnCloseAd.setVisibility(View.GONE);
                    pendingEpisodeIndex = -1;
                    ExoPlayerCache.cancelPrecache();
                    if (player != null) player.stop();
                })
                .setCancelable(false).show();
    }

    private void onAdFinished() {
        isPlayingAd = false; adCanClose = false;
        showAdUI(false); btnCloseAd.setVisibility(View.GONE);
        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            PrefsManager.unlockEpisode(episodes.get(pendingEpisodeIndex).getId());
            playEpisodeDirect(pendingEpisodeIndex);
            pendingEpisodeIndex = -1;
        }
    }

    private void unlockAndPlay() {
        if (pendingEpisodeIndex >= 0 && episodes != null && pendingEpisodeIndex < episodes.size()) {
            PrefsManager.unlockEpisode(episodes.get(pendingEpisodeIndex).getId());
            playEpisodeDirect(pendingEpisodeIndex);
            pendingEpisodeIndex = -1;
        }
    }

    private void showAdUI(boolean showAd) {
        if (tvAdLabel != null) tvAdLabel.setVisibility(showAd ? View.VISIBLE : View.GONE);
        if (layoutBottomInfo != null) layoutBottomInfo.setVisibility(showAd ? View.GONE : View.VISIBLE);
        if (layoutRightButtons != null) layoutRightButtons.setVisibility(showAd ? View.GONE : View.VISIBLE);
        if (layoutTopBar != null) layoutTopBar.setVisibility(showAd ? View.VISIBLE : View.VISIBLE);
    }
    // endregion

    // region Swipe episodes
    private void swipeToEpisode(int targetIndex, boolean swipeUp) {
        if (episodes == null || targetIndex < 0 || targetIndex >= episodes.size()) {
            Toast.makeText(this, swipeUp ? "已经是最后一集了" : "已经是第一集了", Toast.LENGTH_SHORT).show();
            return;
        }
        isSwiping = true;
        int height = rootView.getHeight();
        float exitY = swipeUp ? -height : height;
        playerView.animate().translationY(exitY).setDuration(200)
                .setListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator animation) {
                        playEpisode(targetIndex);
                        playerView.setTranslationY(-exitY);
                        playerView.animate().translationY(0).setDuration(200)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override public void onAnimationEnd(Animator a) { isSwiping = false; }
                                }).start();
                    }
                }).start();
    }

    private void playNextEpisode() {
        if (episodes != null && currentEpisodeIndex < episodes.size() - 1) {
            playEpisode(currentEpisodeIndex + 1);
        }
    }
    // endregion

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ExoPlayerCache.cancelPrecache();
        handler.removeCallbacksAndMessages(null);
        if (player != null) { player.release(); player = null; }
    }
}
