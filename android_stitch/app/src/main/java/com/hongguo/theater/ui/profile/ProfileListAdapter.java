package com.hongguo.theater.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hongguo.theater.R;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.model.WatchHistory;
import com.hongguo.theater.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class ProfileListAdapter extends RecyclerView.Adapter<ProfileListAdapter.ViewHolder> {

    private final Context context;
    private final List<Object> items = new ArrayList<>();

    public ProfileListAdapter(Context context) {
        this.context = context;
    }

    public void setHistoryData(List<WatchHistory> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void setDramaData(List<Drama> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void setEpisodeData(List<Episode> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    public void clearData() {
        items.clear();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_profile_list, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object item = items.get(position);
        long dramaId = 0;

        if (item instanceof WatchHistory) {
            WatchHistory wh = (WatchHistory) item;
            if (wh.getDrama() != null) {
                holder.title.setText(wh.getDrama().getTitle());
                loadCover(holder.cover, wh.getDrama().getCoverUrl());
                dramaId = wh.getDrama().getId();
            }
            holder.subtitle.setText(wh.getProgressText());
        } else if (item instanceof Drama) {
            Drama d = (Drama) item;
            holder.title.setText(d.getTitle());
            holder.subtitle.setText(d.getCategory() + " · " + d.getStatusText());
            loadCover(holder.cover, d.getCoverUrl());
            dramaId = d.getId();
        } else if (item instanceof Episode) {
            Episode ep = (Episode) item;
            if (ep.getDrama() != null) {
                holder.title.setText(ep.getDrama().getTitle());
                holder.subtitle.setText("第" + ep.getEpisodeNumber() + "集 · " + ep.getLikeCountText() + "赞");
                loadCover(holder.cover, ep.getDrama().getCoverUrl());
                dramaId = ep.getDrama().getId();
            } else {
                holder.title.setText(ep.getTitle());
                holder.subtitle.setText("第" + ep.getEpisodeNumber() + "集");
                dramaId = ep.getDramaId();
            }
        }

        long finalDramaId = dramaId;
        holder.itemView.setOnClickListener(v -> {
            if (finalDramaId > 0) {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("drama_id", finalDramaId);
                context.startActivity(intent);
            }
        });
    }

    private void loadCover(ImageView iv, String url) {
        if (url != null && !url.isEmpty()) {
            Glide.with(context).load(url)
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .centerCrop().into(iv);
        } else {
            iv.setImageResource(R.drawable.bg_cover_placeholder);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, subtitle;
        ViewHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.profile_item_cover);
            title = v.findViewById(R.id.profile_item_title);
            subtitle = v.findViewById(R.id.profile_item_subtitle);
        }
    }
}
