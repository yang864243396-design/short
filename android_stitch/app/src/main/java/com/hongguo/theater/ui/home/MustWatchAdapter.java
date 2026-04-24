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

public class MustWatchAdapter extends RecyclerView.Adapter<MustWatchAdapter.ViewHolder> {

    private final Context context;
    private final List<Drama> dramas = new ArrayList<>();

    public MustWatchAdapter(Context context, List<Drama> data) {
        this.context = context;
        if (data != null) dramas.addAll(data);
    }

    public void setData(List<Drama> data) {
        dramas.clear();
        if (data != null) dramas.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_must_watch_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Drama drama = dramas.get(position);
        holder.title.setText(drama.getTitle());
        holder.status.setText(drama.getStatusText());
        String coverUrl = drama.getCoverUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            Glide.with(context).load(coverUrl)
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .centerCrop().into(holder.cover);
        } else {
            Glide.with(context).clear(holder.cover);
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
        TextView title, status;
        ViewHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.card_cover);
            title = v.findViewById(R.id.card_title);
            status = v.findViewById(R.id.card_status);
        }
    }
}
