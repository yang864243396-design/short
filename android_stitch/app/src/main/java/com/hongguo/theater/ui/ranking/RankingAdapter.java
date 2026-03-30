package com.hongguo.theater.ui.ranking;

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
import com.hongguo.theater.model.RankItem;
import com.hongguo.theater.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class RankingAdapter extends RecyclerView.Adapter<RankingAdapter.ViewHolder> {

    private final Context context;
    private final List<RankItem> items = new ArrayList<>();

    public RankingAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<RankItem> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_ranking_row, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RankItem item = items.get(position);
        holder.rank.setText(String.valueOf(item.getRank()));

        int rankColor;
        if (item.getRank() == 1) rankColor = context.getColor(R.color.rank_gold);
        else if (item.getRank() == 2) rankColor = context.getColor(R.color.rank_silver);
        else if (item.getRank() == 3) rankColor = context.getColor(R.color.rank_bronze);
        else rankColor = context.getColor(R.color.on_surface_variant);
        holder.rank.setTextColor(rankColor);

        if (item.getDrama() != null) {
            holder.title.setText(item.getDrama().getTitle());
            holder.category.setText(item.getDrama().getCategory() + " " + item.getDrama().getStatusText());
            holder.desc.setText(item.getDrama().getDescription());
            String coverUrl = item.getDrama().getCoverUrl();
            if (coverUrl != null && !coverUrl.isEmpty()) {
                Glide.with(context).load(coverUrl)
                        .placeholder(R.drawable.bg_cover_placeholder)
                        .error(R.drawable.bg_cover_placeholder)
                        .centerCrop().into(holder.cover);
            } else {
                holder.cover.setImageResource(R.drawable.bg_cover_placeholder);
            }
        }
        holder.heat.setText("热度 " + item.getHeatText());
        holder.likes.setText("点赞 " + item.getLikesText());

        holder.itemView.setOnClickListener(v -> {
            if (item.getDrama() != null) {
                Intent intent = new Intent(context, PlayerActivity.class);
                intent.putExtra("drama_id", item.getDrama().getId());
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView rank, title, category, desc, heat, likes;
        ImageView cover;
        ViewHolder(View v) {
            super(v);
            rank = v.findViewById(R.id.rank_number);
            title = v.findViewById(R.id.rank_title);
            category = v.findViewById(R.id.rank_category);
            desc = v.findViewById(R.id.rank_desc);
            heat = v.findViewById(R.id.rank_heat);
            likes = v.findViewById(R.id.rank_likes);
            cover = v.findViewById(R.id.rank_cover);
        }
    }
}
