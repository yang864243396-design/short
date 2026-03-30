package com.hongguo.theater.ui.home;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.model.Category;

import java.util.ArrayList;
import java.util.List;

public class CategoryChipAdapter extends RecyclerView.Adapter<CategoryChipAdapter.ViewHolder> {

    public interface OnChipClickListener {
        void onClick(String categoryName);
    }

    private final Context context;
    private final List<Category> categories = new ArrayList<>();
    private final OnChipClickListener listener;
    private int selectedPosition = 0;
    private CategoryChipAdapter syncAdapter;
    private boolean isSyncing = false;

    public CategoryChipAdapter(Context context, List<Category> data, OnChipClickListener listener) {
        this.context = context;
        this.listener = listener;

        Category all = new Category();
        this.categories.add(all);
        if (data != null) this.categories.addAll(data);
    }

    public void setSyncAdapter(CategoryChipAdapter other) {
        this.syncAdapter = other;
    }

    public void syncSelection(int position) {
        if (isSyncing) return;
        isSyncing = true;
        int old = selectedPosition;
        selectedPosition = position;
        notifyItemChanged(old);
        notifyItemChanged(selectedPosition);
        isSyncing = false;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_category_chip, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Category cat = categories.get(position);
        String name = (position == 0) ? "全部" : cat.getName();
        holder.text.setText(name);
        holder.text.setSelected(position == selectedPosition);

        holder.text.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos == selectedPosition) return;

            int old = selectedPosition;
            selectedPosition = pos;
            notifyItemChanged(old);
            notifyItemChanged(selectedPosition);

            if (syncAdapter != null) {
                syncAdapter.syncSelection(pos);
            }

            String selected = (selectedPosition == 0) ? "" : categories.get(selectedPosition).getName();
            if (listener != null) listener.onClick(selected);
        });
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text;
        ViewHolder(View v) {
            super(v);
            text = v.findViewById(R.id.chip_text);
        }
    }
}
