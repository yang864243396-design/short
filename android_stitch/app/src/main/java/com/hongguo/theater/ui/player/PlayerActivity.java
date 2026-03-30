package com.hongguo.theater.ui.player;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.utils.FormatUtils;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayerActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private TextView tvTitle, tvDesc, tvEpisodeInfo, tvLikeCount, tvCommentCount;
    private ImageView btnLike, btnComment, btnCollect, btnShare, btnBack;
    private TextView btnEpisodes;

    private long dramaId;
    private Drama drama;
    private List<Episode> episodes;
    private int currentEpisodeIndex = 0;

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

        btnBack.setOnClickListener(v -> finish());

        btnEpisodes.setOnClickListener(v -> {
            if (drama != null && episodes != null) {
                EpisodeBottomSheet sheet = EpisodeBottomSheet.newInstance(
                        drama.getTitle(), episodes, currentEpisodeIndex);
                sheet.setOnEpisodeClickListener(position -> {
                    currentEpisodeIndex = position;
                    playEpisode(position);
                });
                sheet.show(getSupportFragmentManager(), "episodes");
            }
        });

        btnComment.setOnClickListener(v -> {
            if (episodes != null && currentEpisodeIndex < episodes.size()) {
                long episodeId = episodes.get(currentEpisodeIndex).getId();
                CommentBottomSheet sheet = CommentBottomSheet.newInstance(episodeId);
                sheet.show(getSupportFragmentManager(), "comments");
            }
        });

        btnLike.setOnClickListener(v -> {
            if (episodes != null && currentEpisodeIndex < episodes.size()) {
                ApiClient.getService().likeEpisode(episodes.get(currentEpisodeIndex).getId())
                        .enqueue(new Callback<ApiResponse<Void>>() {
                            @Override
                            public void onResponse(@NonNull Call<ApiResponse<Void>> call, @NonNull Response<ApiResponse<Void>> r) {}
                            @Override
                            public void onFailure(@NonNull Call<ApiResponse<Void>> call, @NonNull Throwable t) {}
                        });
            }
        });

        playerView.setOnClickListener(v -> {
            if (player != null) {
                if (player.isPlaying()) player.pause();
                else player.play();
            }
        });
    }

    @OptIn(markerClass = UnstableApi.class)
    private void initPlayer() {
        player = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(player);
        playerView.setUseController(false);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_ENDED) {
                    playNextEpisode();
                }
            }
        });
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
                    if (!episodes.isEmpty()) {
                        playEpisode(0);
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Episode>>> call, @NonNull Throwable t) {}
        });
    }

    private void playEpisode(int index) {
        if (episodes == null || index >= episodes.size()) return;
        currentEpisodeIndex = index;
        Episode ep = episodes.get(index);

        String url = ApiClient.getStreamUrl(ep.getId());
        player.setMediaItem(MediaItem.fromUri(Uri.parse(url)));
        player.prepare();
        player.play();

        tvEpisodeInfo.setText(String.format("第 %d / %d 集",
                ep.getEpisodeNumber(), drama != null ? drama.getTotalEpisodes() : episodes.size()));
        tvLikeCount.setText(ep.getLikeCountText());
        tvCommentCount.setText(ep.getCommentCountText());
    }

    private void playNextEpisode() {
        if (episodes != null && currentEpisodeIndex < episodes.size() - 1) {
            playEpisode(currentEpisodeIndex + 1);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
