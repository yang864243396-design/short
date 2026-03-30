package com.hongguo.theater.ui.home;

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
import com.hongguo.theater.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class DramaListAdapter extends RecyclerView.Adapter<DramaListAdapter.ViewHolder> {

    private final Context context;
    private final List<Drama> dramas = new ArrayList<>();

    public DramaListAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Drama> data) {
        dramas.clear();
        if (data != null) dramas.addAll(data);
        notifyDataSetChanged();
    }

    public void addData(List<Drama> data) {
        if (data != null && !data.isEmpty()) {
            int start = dramas.size();
            dramas.addAll(data);
            notifyItemRangeInserted(start, data.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_drama_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Drama drama = dramas.get(position);
        holder.title.setText(drama.getTitle());
        holder.category.setText(drama.getCategory());
        holder.heat.setText(drama.getHeatText() + "热度");
        holder.status.setText(drama.getStatusText());
        holder.desc.setText(drama.getDescription());
        String coverUrl = drama.getCoverUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            Glide.with(context).load(coverUrl)
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .centerCrop().into(holder.cover);
        } else {
            holder.cover.setImageResource(R.drawable.bg_cover_placeholder);
        }
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("drama_id", drama.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return dramas.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, category, heat, status, desc;

        ViewHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.drama_cover);
            title = v.findViewById(R.id.drama_title);
            category = v.findViewById(R.id.drama_category);
            heat = v.findViewById(R.id.drama_heat);
            status = v.findViewById(R.id.drama_status);
            desc = v.findViewById(R.id.drama_desc);
        }
    }
}
