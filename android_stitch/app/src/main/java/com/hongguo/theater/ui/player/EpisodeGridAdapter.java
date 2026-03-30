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
import com.hongguo.theater.model.Episode;

import java.util.ArrayList;
import java.util.List;

public class EpisodeGridAdapter extends RecyclerView.Adapter<EpisodeGridAdapter.ViewHolder> {

    private final Context context;
    private final List<Episode> allEpisodes;
    private List<Episode> displayEpisodes;
    private int currentIndex;
    private int groupOffset = 0;
    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onClick(int globalPosition);
    }

    public EpisodeGridAdapter(Context context, List<Episode> episodes, int currentIndex,
                              OnItemClickListener listener) {
        this.context = context;
        this.allEpisodes = episodes;
        this.currentIndex = currentIndex;
        this.listener = listener;
        this.displayEpisodes = new ArrayList<>();
        setGroupOffset(0);
    }

    public void setGroupOffset(int offset) {
        this.groupOffset = offset;
        displayEpisodes.clear();
        int end = Math.min(offset + 40, allEpisodes.size());
        for (int i = offset; i < end; i++) {
            displayEpisodes.add(allEpisodes.get(i));
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_episode_grid, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode ep = displayEpisodes.get(position);
        int globalPos = groupOffset + position;

        holder.number.setText(String.valueOf(ep.getEpisodeNumber()));
        holder.itemView.setSelected(globalPos == currentIndex);

        if (!ep.isFree()) {
            holder.lockIcon.setVisibility(View.VISIBLE);
        } else {
            holder.lockIcon.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(globalPos);
        });
    }

    @Override
    public int getItemCount() { return displayEpisodes.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView number;
        ImageView lockIcon;
        ViewHolder(View v) {
            super(v);
            number = v.findViewById(R.id.ep_number);
            lockIcon = v.findViewById(R.id.ep_lock);
        }
    }
}
