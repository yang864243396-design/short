package com.hongguo.theater.ui.theater;

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

public class TheaterAdapter extends RecyclerView.Adapter<TheaterAdapter.ViewHolder> {

    private final Context context;
    private final List<Drama> dramas = new ArrayList<>();

    public TheaterAdapter(Context context) {
        this.context = context;
    }

    public void setData(List<Drama> data) {
        dramas.clear();
        if (data != null) dramas.addAll(data);
        notifyDataSetChanged();
    }

    public void addData(List<Drama> data) {
        if (data != null) {
            int start = dramas.size();
            dramas.addAll(data);
            notifyItemRangeInserted(start, data.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_theater_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Drama drama = dramas.get(position);
        holder.title.setText(drama.getTitle());
        holder.rating.setText(String.valueOf(drama.getRating()));
        if (drama.getCoverUrl() != null) {
            Glide.with(context).load(drama.getCoverUrl()).centerCrop().into(holder.cover);
        }
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("drama_id", drama.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() { return dramas.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, rating;
        ViewHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.theater_cover);
            title = v.findViewById(R.id.theater_title);
            rating = v.findViewById(R.id.theater_rating);
        }
    }
}
