package com.hongguo.theater.ui.search;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hongguo.theater.R;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.ui.home.DramaCardTagsHelper;
import com.hongguo.theater.ui.player.PlayerActivity;

import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<SearchResultAdapter.ViewHolder> {

    private final Context context;
    private final List<Drama> dramas = new ArrayList<>();

    public SearchResultAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Drama> data) {
        dramas.clear();
        if (data != null) dramas.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_drama_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Drama d = dramas.get(position);
        holder.title.setText(d.getTitle());
        DramaCardTagsHelper.bindTags(holder.tags, context, d);
        holder.heat.setText(d.getHeatText() + "热度");
        holder.status.setText(d.getStatusText());
        holder.desc.setText(d.getDescription());
        String cover = d.getCoverUrl();
        if (cover != null && !cover.isEmpty()) {
            Glide.with(context).load(cover)
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .centerCrop().into(holder.cover);
        } else {
            Glide.with(context).clear(holder.cover);
            holder.cover.setImageResource(R.drawable.bg_cover_placeholder);
        }
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("drama_id", d.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return dramas.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, heat, status, desc;
        LinearLayout tags;
        ViewHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.drama_cover);
            title = v.findViewById(R.id.drama_title);
            tags = v.findViewById(R.id.drama_tags);
            heat = v.findViewById(R.id.drama_heat);
            status = v.findViewById(R.id.drama_status);
            desc = v.findViewById(R.id.drama_desc);
        }
    }
}
