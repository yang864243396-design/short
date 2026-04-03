package com.hongguo.theater.ui.play;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Color;
import android.graphics.PorterDuff;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Comment;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.ui.player.CommentBottomSheet;
import com.hongguo.theater.ui.player.PlayerActivity;
import com.hongguo.theater.utils.LoginHelper;
import com.hongguo.theater.utils.ExoPlayerCache;
import com.hongguo.theater.utils.PrefsManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentActivity;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FeedPagerAdapter extends RecyclerView.Adapter<FeedPagerAdapter.FeedViewHolder> {

    private final Context context;
    private final List<Episode> episodes = new ArrayList<>();
    private final Map<Integer, ExoPlayer> playerMap = new HashMap<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Runnable> progressRunnables = new HashMap<>();
    private int currentPosition = 0;

    public FeedPagerAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Episode> data) {
        episodes.clear();
        if (data != null) episodes.addAll(data);
        notifyDataSetChanged();
    }

    public void addData(List<Episode> data) {
        if (data != null) {
            int start = episodes.size();
            episodes.addAll(data);
            notifyItemRangeInserted(start, data.size());
        }
    }

    @NonNull
    @Override
    public FeedViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_feed_video, parent, false);
        return new FeedViewHolder(v);
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onBindViewHolder(@NonNull FeedViewHolder holder, int position) {
        Episode episode = episodes.get(position);
        Drama drama = episode.getDrama();

        if (drama != null) {
            holder.title.setText(drama.getTitle());
            holder.desc.setText(drama.getDescription());

            int totalEpisodes = drama.getTotalEpisodes();
            if (totalEpisodes > 0) {
                holder.btnViewEpisodes.setText(
                        context.getString(R.string.view_episodes_format, totalEpisodes));
                holder.btnViewEpisodes.setVisibility(View.VISIBLE);
            } else {
                holder.btnViewEpisodes.setVisibility(View.GONE);
            }

            holder.btnViewEpisodes.setOnClickListener(v -> {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("drama_id", drama.getId());
                context.startActivity(intent);
            });
        } else {
            holder.title.setText(episode.getTitle());
            holder.desc.setText("");
            holder.btnViewEpisodes.setVisibility(View.GONE);
        }

        holder.likeCount.setText(episode.getLikeCountText());
        holder.commentCount.setText(episode.getCommentCountText());

        holder.seekBar.setProgress(0);

        String videoUrl = ApiClient.getStreamUrl(episode);
        android.util.Log.d("FeedAdapter", "Playing URL: " + videoUrl);
        DefaultMediaSourceFactory sourceFactory = new DefaultMediaSourceFactory(
                ExoPlayerCache.getDataSourceFactory(context));
        ExoPlayer player = new ExoPlayer.Builder(context)
                .setMediaSourceFactory(sourceFactory)
                .build();
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
        player.setRepeatMode(ExoPlayer.REPEAT_MODE_ONE);
        player.prepare();
        holder.playerView.setPlayer(player);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                android.util.Log.e("FeedAdapter", "ExoPlayer error: " + error.getMessage(), error);
                android.widget.Toast.makeText(context,
                        "Feed播放失败: " + error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
            }
        });

        playerMap.put(position, player);
        setupSeekBar(holder.seekBar, player, position);

        if (position == currentPosition) {
            player.play();
        }

        holder.playerView.setOnClickListener(v -> {
            if (player.isPlaying()) {
                player.pause();
            } else {
                player.play();
            }
        });

        holder.btnLike.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        holder.btnCollect.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

        if (PrefsManager.isLoggedIn()) {
            ApiClient.getService().getEpisodeInteraction(episode.getId())
                    .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                               @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Map<String, Object> data = r.body().getData();
                                if (data != null) {
                                    boolean liked = Boolean.TRUE.equals(data.get("liked"));
                                    boolean collected = Boolean.TRUE.equals(data.get("collected"));
                                    holder.btnLike.setColorFilter(liked ? Color.RED : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                    holder.btnCollect.setColorFilter(collected ? Color.parseColor("#FFC107") : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                }
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {}
                    });
        }

        holder.btnLike.setOnClickListener(v -> {
            if (!LoginHelper.requireLogin(context)) return;
            ApiClient.getService().likeEpisode(episode.getId())
                    .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                               @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Map<String, Object> data = r.body().getData();
                                boolean liked = data != null && Boolean.TRUE.equals(data.get("liked"));
                                holder.btnLike.setColorFilter(liked ? Color.RED : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                long count = episode.getLikeCount() + (liked ? 1 : -1);
                                if (count < 0) count = 0;
                                episode.setLikeCount(count);
                                holder.likeCount.setText(episode.getLikeCountText());
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {}
                    });
        });

        holder.btnComment.setOnClickListener(v -> {
            if (!LoginHelper.requireLogin(context)) return;
            if (context instanceof FragmentActivity) {
                CommentBottomSheet sheet = CommentBottomSheet.newInstance(episode.getId());
                sheet.setOnCommentPostedListener(() -> {
                    episode.setCommentCount(episode.getCommentCount() + 1);
                    holder.commentCount.setText(episode.getCommentCountText());
                });
                sheet.show(((FragmentActivity) context).getSupportFragmentManager(), "comments");
            }
        });

        holder.btnCollect.setOnClickListener(v -> {
            if (!LoginHelper.requireLogin(context)) return;
            ApiClient.getService().collectEpisode(episode.getId())
                    .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                               @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Map<String, Object> data = r.body().getData();
                                boolean collected = data != null && Boolean.TRUE.equals(data.get("collected"));
                                holder.btnCollect.setColorFilter(collected ? Color.parseColor("#FFC107") : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                android.widget.Toast.makeText(context,
                                        collected ? "已收藏" : "已取消收藏",
                                        android.widget.Toast.LENGTH_SHORT).show();
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {}
                    });
        });

        holder.btnShare.setOnClickListener(v -> {
            if (!LoginHelper.requireLogin(context)) return;
            String title = drama != null ? drama.getTitle() : episode.getTitle();
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "推荐你看《" + title + "》");
            context.startActivity(Intent.createChooser(shareIntent, "分享到"));
        });
    }

    private void setupSeekBar(SeekBar seekBar, ExoPlayer player, int position) {
        stopProgressUpdate(position);

        final boolean[] isUserSeeking = {false};

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player.getDuration() > 0) {
                    long seekPos = player.getDuration() * progress / 1000;
                    player.seekTo(seekPos);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                isUserSeeking[0] = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                isUserSeeking[0] = false;
            }
        });

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (!isUserSeeking[0] && player.getDuration() > 0) {
                    int progress = (int) (player.getCurrentPosition() * 1000 / player.getDuration());
                    seekBar.setProgress(progress);
                }
                handler.postDelayed(this, 200);
            }
        };
        progressRunnables.put(position, runnable);
        handler.post(runnable);
    }

    private void stopProgressUpdate(int position) {
        Runnable runnable = progressRunnables.remove(position);
        if (runnable != null) {
            handler.removeCallbacks(runnable);
        }
    }

    @Override
    public void onViewRecycled(@NonNull FeedViewHolder holder) {
        super.onViewRecycled(holder);
        int pos = holder.getAdapterPosition();
        stopProgressUpdate(pos);
        if (holder.playerView.getPlayer() != null) {
            ExoPlayer player = playerMap.remove(pos);
            if (player != null) {
                player.release();
            }
        }
    }

    public void onPageSelected(int position) {
        ExoPlayer old = playerMap.get(currentPosition);
        if (old != null) old.pause();

        currentPosition = position;

        ExoPlayer current = playerMap.get(position);
        if (current != null) {
            current.seekTo(0);
            current.play();
        }
    }

    public void pauseCurrentPlayer() {
        ExoPlayer player = playerMap.get(currentPosition);
        if (player != null) player.pause();
    }

    public void resumeCurrentPlayer() {
        ExoPlayer player = playerMap.get(currentPosition);
        if (player != null) player.play();
    }

    public void releaseAllPlayers() {
        for (Map.Entry<Integer, Runnable> entry : progressRunnables.entrySet()) {
            handler.removeCallbacks(entry.getValue());
        }
        progressRunnables.clear();
        for (ExoPlayer p : playerMap.values()) {
            p.release();
        }
        playerMap.clear();
    }

    @Override
    public int getItemCount() { return episodes.size(); }

    static class FeedViewHolder extends RecyclerView.ViewHolder {
        PlayerView playerView;
        TextView title, desc, likeCount, commentCount;
        TextView btnViewEpisodes;
        ImageView btnLike, btnComment, btnCollect, btnShare;
        SeekBar seekBar;

        FeedViewHolder(View v) {
            super(v);
            playerView = v.findViewById(R.id.player_view);
            title = v.findViewById(R.id.feed_title);
            desc = v.findViewById(R.id.feed_desc);
            likeCount = v.findViewById(R.id.feed_like_count);
            commentCount = v.findViewById(R.id.feed_comment_count);
            btnViewEpisodes = v.findViewById(R.id.btn_view_episodes);
            btnLike = v.findViewById(R.id.btn_like);
            btnComment = v.findViewById(R.id.btn_comment);
            btnCollect = v.findViewById(R.id.btn_collect);
            btnShare = v.findViewById(R.id.btn_share);
            seekBar = v.findViewById(R.id.feed_seekbar);
        }
    }
}
