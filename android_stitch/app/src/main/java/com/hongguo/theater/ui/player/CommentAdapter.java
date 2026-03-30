package com.hongguo.theater.ui.player;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.model.Comment;
import com.hongguo.theater.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

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
