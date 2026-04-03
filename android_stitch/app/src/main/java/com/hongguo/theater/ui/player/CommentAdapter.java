package com.hongguo.theater.ui.player;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Comment;
import com.hongguo.theater.utils.FormatUtils;
import com.hongguo.theater.utils.LoginHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CommentAdapter extends RecyclerView.Adapter<CommentAdapter.ViewHolder> {

    private final Context context;
    private final List<Comment> comments = new ArrayList<>();

    public CommentAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Comment> data) {
        comments.clear();
        if (data != null) comments.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_comment, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Comment c = comments.get(position);
        holder.username.setText(c.getUsername());
        holder.content.setText(c.getContent());
        holder.likeCount.setText(c.getLikeCountText());
        holder.time.setText(FormatUtils.formatTimeAgo(c.getCreatedAt()));

        holder.btnLike.setColorFilter(c.isLiked() ? Color.RED : Color.parseColor("#999999"), PorterDuff.Mode.SRC_IN);

        holder.btnLike.setOnClickListener(v -> {
            if (!LoginHelper.requireLogin(context)) return;
            ApiClient.getService().likeComment(c.getId())
                    .enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                               @NonNull Response<ApiResponse<Map<String, Object>>> r) {
                            if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                                Map<String, Object> data = r.body().getData();
                                boolean liked = data != null && Boolean.TRUE.equals(data.get("liked"));
                                c.setLiked(liked);
                                c.setLikeCount(c.getLikeCount() + (liked ? 1 : -1));
                                holder.btnLike.setColorFilter(liked ? Color.RED : Color.parseColor("#999999"), PorterDuff.Mode.SRC_IN);
                                holder.likeCount.setText(c.getLikeCountText());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {}
                    });
        });
    }

    @Override
    public int getItemCount() { return comments.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView username, content, likeCount, time;
        ImageView btnLike;
        ViewHolder(View v) {
            super(v);
            username = v.findViewById(R.id.comment_username);
            content = v.findViewById(R.id.comment_content);
            likeCount = v.findViewById(R.id.comment_like_count);
            time = v.findViewById(R.id.comment_time);
            btnLike = v.findViewById(R.id.btn_comment_like);
        }
    }
}
