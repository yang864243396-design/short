package com.hongguo.theater.ui.home;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.hongguo.theater.R;
import com.hongguo.theater.model.Category;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.HomeData;
import com.hongguo.theater.model.RankItem;
import com.hongguo.theater.ui.player.PlayerActivity;
import com.hongguo.theater.ui.ranking.RankingActivity;

import java.util.ArrayList;
import java.util.List;

public class HomeAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_RANKING = 0;
    private static final int TYPE_CATEGORY = 1;
    private static final int TYPE_DRAMA_CARD = 2;

    private final Context context;
    private final List<Object> items = new ArrayList<>();
    private OnCategoryClickListener categoryClickListener;

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }

    public HomeAdapter(Context context) {
        this.context = context;
    }

    public void setCategoryClickListener(OnCategoryClickListener listener) {
        this.categoryClickListener = listener;
    }

    public void setData(HomeData data, List<Drama> dramaList) {
        items.clear();

        if (data.getHotRanking() != null && !data.getHotRanking().isEmpty()) {
            items.add(new RankingSection(data.getHotRanking()));
        }

        if (data.getCategories() != null && !data.getCategories().isEmpty()) {
            items.add(new CategorySection(data.getCategories()));
        }

        if (dramaList != null) {
            items.addAll(dramaList);
        }

        notifyDataSetChanged();
    }

    public void updateDramas(List<Drama> dramas, boolean append) {
        if (!append) {
            items.removeIf(item -> item instanceof Drama);
        }
        if (dramas != null) {
            items.addAll(dramas);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof RankingSection) return TYPE_RANKING;
        if (item instanceof CategorySection) return TYPE_CATEGORY;
        return TYPE_DRAMA_CARD;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        switch (viewType) {
            case TYPE_RANKING:
                return new RankingSectionHolder(inflater.inflate(R.layout.item_ranking_section, parent, false));
            case TYPE_CATEGORY:
                return new CategorySectionHolder(inflater.inflate(R.layout.item_category_section, parent, false));
            default:
                return new DramaCardHolder(inflater.inflate(R.layout.item_drama_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);

        if (holder instanceof RankingSectionHolder && item instanceof RankingSection) {
            bindRankingSection((RankingSectionHolder) holder, (RankingSection) item);
        } else if (holder instanceof CategorySectionHolder && item instanceof CategorySection) {
            bindCategorySection((CategorySectionHolder) holder, (CategorySection) item);
        } else if (holder instanceof DramaCardHolder && item instanceof Drama) {
            bindDramaCard((DramaCardHolder) holder, (Drama) item);
        }
    }

    private void bindRankingSection(RankingSectionHolder h, RankingSection section) {
        MustWatchAdapter adapter = new MustWatchAdapter(context, getRankingDramas(section.rankings));
        h.recyclerView.setLayoutManager(
                new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        h.recyclerView.setAdapter(adapter);
        h.btnViewRanking.setOnClickListener(v ->
                context.startActivity(new Intent(context, RankingActivity.class)));
    }

    private List<Drama> getRankingDramas(List<RankItem> rankings) {
        List<Drama> dramas = new ArrayList<>();
        for (RankItem r : rankings) {
            if (r.getDrama() != null) {
                dramas.add(r.getDrama());
            }
        }
        return dramas;
    }

    private void bindCategorySection(CategorySectionHolder h, CategorySection section) {
        CategoryChipAdapter adapter = new CategoryChipAdapter(context, section.categories, cat -> {
            if (categoryClickListener != null) {
                categoryClickListener.onCategoryClick(cat);
            }
        });
        h.recyclerView.setLayoutManager(
                new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        h.recyclerView.setAdapter(adapter);
    }

    private void bindDramaCard(DramaCardHolder h, Drama drama) {
        h.title.setText(drama.getTitle());
        DramaCardTagsHelper.bindTags(h.tags, context, drama);
        h.heat.setText(drama.getHeatText() + "热度");
        h.status.setText(drama.getStatusText());
        h.desc.setText(drama.getDescription());
        String cover = drama.getCoverUrl();
        if (cover != null && !cover.isEmpty()) {
            Glide.with(context).load(cover)
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .centerCrop().into(h.cover);
        } else {
            Glide.with(context).clear(h.cover);
            h.cover.setImageResource(R.drawable.bg_cover_placeholder);
        }
        h.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("drama_id", drama.getId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    // --- ViewHolders ---

    static class RankingSectionHolder extends RecyclerView.ViewHolder {
        RecyclerView recyclerView;
        View btnViewRanking;
        RankingSectionHolder(View v) {
            super(v);
            recyclerView = v.findViewById(R.id.recycler_ranking);
            btnViewRanking = v.findViewById(R.id.btn_view_ranking);
        }
    }

    static class CategorySectionHolder extends RecyclerView.ViewHolder {
        RecyclerView recyclerView;
        CategorySectionHolder(View v) {
            super(v);
            if (v instanceof RecyclerView) {
                recyclerView = (RecyclerView) v;
            } else {
                recyclerView = v.findViewById(R.id.recycler_categories);
            }
        }
    }

    static class DramaCardHolder extends RecyclerView.ViewHolder {
        ImageView cover;
        TextView title, heat, status, desc;
        LinearLayout tags;
        DramaCardHolder(View v) {
            super(v);
            cover = v.findViewById(R.id.drama_cover);
            title = v.findViewById(R.id.drama_title);
            tags = v.findViewById(R.id.drama_tags);
            heat = v.findViewById(R.id.drama_heat);
            status = v.findViewById(R.id.drama_status);
            desc = v.findViewById(R.id.drama_desc);
        }
    }

    // --- Data wrappers ---

    static class RankingSection {
        List<RankItem> rankings;
        RankingSection(List<RankItem> rankings) { this.rankings = rankings; }
    }

    static class CategorySection {
        List<Category> categories;
        CategorySection(List<Category> categories) { this.categories = categories; }
    }
}
