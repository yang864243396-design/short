package com.hongguo.theater.ui.play;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
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
import android.text.TextUtils;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.ui.player.CommentBottomSheet;
import com.hongguo.theater.ui.player.PlayerActivity;
import com.hongguo.theater.utils.DescriptionInlineExpandHelper;
import com.hongguo.theater.utils.LoginHelper;
import com.hongguo.theater.utils.RankingBadgeUiHelper;
import com.hongguo.theater.utils.FeedDramaTagsBinder;
import com.hongguo.theater.utils.ExoPlayerCache;
import com.hongguo.theater.utils.PrefsManager;

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
    /** 快速滑动时取消未返回的互动状态请求，减少无效回调与列表竞争 */
    private Call<ApiResponse<Map<String, Object>>> pendingInteractionCall;

    public FeedPagerAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Episode> data) {
        releaseAllPlayers();
        currentPosition = 0;
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

    /** Feed 中该剧最后一条的下一索引（下一部剧）；无则 -1 */
    public int findNextDramaStartIndex(long dramaId) {
        if (dramaId <= 0) return -1;
        int lastIdx = -1;
        for (int i = 0; i < episodes.size(); i++) {
            if (dramaIdOf(episodes.get(i)) == dramaId) {
                lastIdx = i;
            }
        }
        if (lastIdx < 0) return -1;
        if (lastIdx + 1 < episodes.size()) return lastIdx + 1;
        return -1;
    }

    private static long dramaIdOf(Episode ep) {
        if (ep.getDramaId() > 0) return ep.getDramaId();
        Drama d = ep.getDrama();
        return d != null ? d.getId() : 0L;
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
        holder.playerView.setPlayer(null);
        ExoPlayer toRelease = playerMap.remove(position);
        if (toRelease != null) {
            toRelease.release();
        }

        Episode episode = episodes.get(position);
        Drama drama = episode.getDrama();

        if (drama != null) {
            RankingBadgeUiHelper.bind(holder.rankingBadge, drama, context, true);
            holder.title.setText(drama.getTitle());
            FeedDramaTagsBinder.bind(holder.tagScroll, holder.tagRow, drama, context);
            holder.descBindKey = episode.getId();
            holder.feedDescription = TextUtils.isEmpty(drama.getDescription()) ? "" : drama.getDescription();
            holder.descExpanded = false;
            holder.desc.post(() -> bindFeedDescription(holder));

            int totalEpisodes = drama.getTotalEpisodes();
            if (totalEpisodes > 0) {
                holder.btnViewEpisodes.setText(
                        context.getString(R.string.view_episodes_format, totalEpisodes));
                holder.btnViewEpisodes.setVisibility(View.VISIBLE);
            } else {
                holder.btnViewEpisodes.setVisibility(View.GONE);
            }

            holder.btnViewEpisodes.setOnClickListener(v -> {
                int pos = holder.getBindingAdapterPosition();
                if (pos == RecyclerView.NO_POSITION) return;
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("drama_id", drama.getId());
                intent.putExtra("episode_id", episode.getId());
                // 与 Feed 当前播放器完全一致的字串，确保 SimpleCache 按同一 key 命中、无需重复缓存
                intent.putExtra("handoff_stream_url", ApiClient.getStreamUrl(episode));
                ExoPlayer feedPlayer = playerMap.get(pos);
                if (feedPlayer != null) {
                    intent.putExtra("playback_position_ms", feedPlayer.getCurrentPosition());
                }
                context.startActivity(intent);
            });
        } else {
            RankingBadgeUiHelper.bind(holder.rankingBadge, null, context, true);
            holder.title.setText(episode.getTitle());
            FeedDramaTagsBinder.bind(holder.tagScroll, holder.tagRow, null, context);
            holder.descBindKey = episode.getId();
            holder.feedDescription = "";
            holder.descExpanded = false;
            bindFeedDescription(holder);
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
                .setLoadControl(ExoPlayerCache.createVideoLoadControl())
                .build();
        player.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)));
        player.setRepeatMode(ExoPlayer.REPEAT_MODE_ONE);
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
            player.prepare();
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
            if (pendingInteractionCall != null) {
                pendingInteractionCall.cancel();
                pendingInteractionCall = null;
            }
            pendingInteractionCall = ApiClient.getService().getEpisodeInteraction(episode.getId());
            pendingInteractionCall.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                               @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                            if (call.isCanceled()) return;
                            try {
                                int ap = holder.getBindingAdapterPosition();
                                if (ap != RecyclerView.NO_POSITION && ap < episodes.size()
                                        && episodes.get(ap).getId() == episode.getId()
                                        && r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                    Map<String, Object> data = r.body().getData();
                                    if (data != null) {
                                        boolean liked = Boolean.TRUE.equals(data.get("liked"));
                                        boolean collected = Boolean.TRUE.equals(data.get("collected"));
                                        holder.btnLike.setColorFilter(liked ? Color.RED : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                        holder.btnCollect.setColorFilter(collected ? Color.parseColor("#FFC107") : Color.WHITE, PorterDuff.Mode.SRC_IN);
                                    }
                                }
                            } finally {
                                if (pendingInteractionCall == call) {
                                    pendingInteractionCall = null;
                                }
                            }
                        }
                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                            if (!call.isCanceled() && pendingInteractionCall == call) {
                                pendingInteractionCall = null;
                            }
                        }
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

    private void bindFeedDescription(FeedViewHolder holder) {
        final long bindKey = holder.descBindKey;
        TextView tv = holder.desc;
        String full = holder.feedDescription;
        if (TextUtils.isEmpty(full)) {
            tv.setText("");
            tv.setMovementMethod(null);
            return;
        }
        if (holder.descExpanded) {
            DescriptionInlineExpandHelper.applyExpandedWithCollapse(tv, full, () -> {
                if (holder.descBindKey != bindKey) return;
                holder.descExpanded = false;
                holder.desc.post(() -> bindFeedDescription(holder));
            });
        } else {
            DescriptionInlineExpandHelper.applyCollapsedFirstLineWithRetry(tv, full, () -> {
                if (holder.descBindKey != bindKey) return;
                holder.descExpanded = true;
                bindFeedDescription(holder);
            });
        }
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
                // 仅当前可见页维持 200ms 刷新；离屏页不 post，避免多路 SeekBar 空转
                if (position != currentPosition) {
                    return;
                }
                if (!isUserSeeking[0] && player.getDuration() > 0) {
                    int progress = (int) (player.getCurrentPosition() * 1000 / player.getDuration());
                    seekBar.setProgress(progress);
                }
                handler.postDelayed(this, 200);
            }
        };
        progressRunnables.put(position, runnable);
        if (position == currentPosition) {
            handler.post(runnable);
        }
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
        int pos = holder.getBindingAdapterPosition();
        if (pos != RecyclerView.NO_POSITION) {
            stopProgressUpdate(pos);
            ExoPlayer player = playerMap.remove(pos);
            if (player != null) {
                player.release();
            }
        }
        holder.playerView.setPlayer(null);
    }

    public void onPageSelected(int position) {
        for (Runnable r : progressRunnables.values()) {
            handler.removeCallbacks(r);
        }

        ExoPlayer old = playerMap.get(currentPosition);
        if (old != null) {
            old.pause();
            old.setPlayWhenReady(false);
        }

        currentPosition = position;

        ExoPlayer current = playerMap.get(position);
        if (current != null) {
            current.seekTo(0);
            int state = current.getPlaybackState();
            if (state == Player.STATE_IDLE) {
                current.prepare();
            }
            current.play();
        }

        Runnable tick = progressRunnables.get(position);
        if (tick != null) {
            handler.post(tick);
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
        if (pendingInteractionCall != null) {
            pendingInteractionCall.cancel();
            pendingInteractionCall = null;
        }
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
        TextView rankingBadge, title, desc, likeCount, commentCount;
        HorizontalScrollView tagScroll;
        LinearLayout tagRow;
        TextView btnViewEpisodes;
        /** 当前行简介绑定对应的剧集 id，避免异步点击与复用错位 */
        long descBindKey;
        String feedDescription = "";
        boolean descExpanded;
        ImageView btnLike, btnComment, btnCollect, btnShare;
        SeekBar seekBar;

        FeedViewHolder(View v) {
            super(v);
            playerView = v.findViewById(R.id.player_view);
            rankingBadge = v.findViewById(R.id.feed_ranking_badge);
            title = v.findViewById(R.id.feed_title);
            tagScroll = v.findViewById(R.id.feed_tag_scroll);
            tagRow = v.findViewById(R.id.feed_tag_row);
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
