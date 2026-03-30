package com.hongguo.theater.ui.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.model.HotSearch;

import java.util.ArrayList;
import java.util.List;

public class HotSearchAdapter extends RecyclerView.Adapter<HotSearchAdapter.ViewHolder> {

    private final Context context;
    private final List<HotSearch> items = new ArrayList<>();

    public HotSearchAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<HotSearch> data) {
        items.clear();
        if (data != null) items.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_hot_search, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HotSearch hs = items.get(position);
        holder.rank.setText(String.format("%02d", hs.getRank()));

        int rankColor;
        if (hs.getRank() <= 3) {
            rankColor = context.getColor(hs.getRank() == 1 ? R.color.rank_gold :
                    hs.getRank() == 2 ? R.color.rank_silver : R.color.rank_bronze);
        } else {
            rankColor = context.getColor(R.color.on_surface_variant);
        }
        holder.rank.setTextColor(rankColor);

        holder.keyword.setText(hs.getKeyword());
        holder.heat.setText("热度 " + hs.getHeatText());

        if (hs.getBadge() != null && !hs.getBadge().isEmpty()) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(hs.getBadge());
            int badgeColor;
            switch (hs.getBadge()) {
                case "热": badgeColor = context.getColor(R.color.badge_hot); break;
                case "新": badgeColor = context.getColor(R.color.badge_new); break;
                case "升": badgeColor = context.getColor(R.color.badge_rising); break;
                default: badgeColor = context.getColor(R.color.on_surface_variant); break;
            }
            holder.badge.setTextColor(badgeColor);
        } else {
            holder.badge.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView rank, keyword, heat, badge;
        ViewHolder(View v) {
            super(v);
            rank = v.findViewById(R.id.hot_rank);
            keyword = v.findViewById(R.id.hot_keyword);
            heat = v.findViewById(R.id.hot_heat);
            badge = v.findViewById(R.id.hot_badge);
        }
    }
}
