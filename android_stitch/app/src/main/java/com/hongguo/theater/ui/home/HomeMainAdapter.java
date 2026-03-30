package com.hongguo.theater.ui.home;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.hongguo.theater.R;
import com.hongguo.theater.model.Banner;
import com.hongguo.theater.model.Category;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.RankItem;
import com.hongguo.theater.ui.player.PlayerActivity;
import com.hongguo.theater.ui.ranking.RankingActivity;

import java.util.ArrayList;
import java.util.List;

public class HomeMainAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    static final int TYPE_BANNER = 0;
    static final int TYPE_RANKING = 1;
    static final int TYPE_CATEGORY = 2;
    static final int TYPE_DRAMA = 3;

    private final Context context;
    private final List<Object> items = new ArrayList<>();

    private List<Banner> banners;
    private List<RankItem> rankings;
    private CategoryData categoryData;

    private OnCategoryClickListener categoryClickListener;

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }

    public HomeMainAdapter(Context context) {
        this.context = context;
    }

    public void setCategoryClickListener(OnCategoryClickListener listener) {
        this.categoryClickListener = listener;
    }

    public void setBanners(List<Banner> banners) {
        this.banners = banners;
        rebuildItems();
    }

    public void setRankings(List<RankItem> rankings) {
        this.rankings = rankings;
        rebuildItems();
    }

    public void setCategories(List<Category> categories, CategoryChipAdapter adapter) {
        this.categoryData = new CategoryData(categories, adapter);
        rebuildItems();
    }

    public void setDramas(List<Drama> dramas) {
        items.removeIf(i -> i instanceof Drama);
        if (dramas != null) items.addAll(dramas);
        notifyDataSetChanged();
    }

    public void addDramas(List<Drama> dramas) {
        if (dramas != null && !dramas.isEmpty()) {
            int start = items.size();
            items.addAll(dramas);
            notifyItemRangeInserted(start, dramas.size());
        }
    }

    private void rebuildItems() {
        List<Drama> existingDramas = new ArrayList<>();
        for (Object o : items) {
            if (o instanceof Drama) existingDramas.add((Drama) o);
        }
        items.clear();
        if (banners != null && !banners.isEmpty()) items.add(new BannerData(banners));
        if (rankings != null && !rankings.isEmpty()) items.add(new RankingData(rankings));
        if (categoryData != null) items.add(categoryData);
        items.addAll(existingDramas);
        notifyDataSetChanged();
    }

    public int getCategoryPosition() {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof CategoryData) return i;
        }
        return -1;
    }

    @Override
    public int getItemViewType(int position) {
        Object item = items.get(position);
        if (item instanceof BannerData) return TYPE_BANNER;
        if (item instanceof RankingData) return TYPE_RANKING;
        if (item instanceof CategoryData) return TYPE_CATEGORY;
        return TYPE_DRAMA;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inf = LayoutInflater.from(context);
        switch (viewType) {
            case TYPE_BANNER:
                return new BannerHolder(inf.inflate(R.layout.item_home_banner_pager, parent, false));
            case TYPE_RANKING:
                return new RankingHolder(inf.inflate(R.layout.item_ranking_section, parent, false));
            case TYPE_CATEGORY:
                return new CategoryHolder(inf.inflate(R.layout.item_category_section, parent, false));
            default:
                return new DramaHolder(inf.inflate(R.layout.item_drama_card, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = items.get(position);
        if (holder instanceof BannerHolder && item instanceof BannerData) {
            bindBanner((BannerHolder) holder, (BannerData) item);
        } else if (holder instanceof RankingHolder && item instanceof RankingData) {
            bindRanking((RankingHolder) holder, (RankingData) item);
        } else if (holder instanceof CategoryHolder && item instanceof CategoryData) {
            bindCategory((CategoryHolder) holder, (CategoryData) item);
        } else if (holder instanceof DramaHolder && item instanceof Drama) {
            bindDrama((DramaHolder) holder, (Drama) item);
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    // --- Banner ---
    private void bindBanner(BannerHolder h, BannerData data) {
        BannerPagerAdapter adapter = new BannerPagerAdapter(context);
        adapter.setData(data.banners);
        h.pager.setAdapter(adapter);

        h.indicator.removeAllViews();
        int count = data.banners.size();
        for (int i = 0; i < count; i++) {
            View dot = new View(context);
            int w = (i == 0) ? dpToPx(16) : dpToPx(6);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, dpToPx(4));
            p.setMarginStart(i == 0 ? 0 : dpToPx(4));
            dot.setLayoutParams(p);
            dot.setBackgroundResource(i == 0 ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            h.indicator.addView(dot);
        }

        h.pager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int pos) {
                for (int i = 0; i < h.indicator.getChildCount(); i++) {
                    View dot = h.indicator.getChildAt(i);
                    boolean active = (i == pos);
                    LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
                    lp.width = active ? dpToPx(16) : dpToPx(6);
                    dot.setLayoutParams(lp);
                    dot.setBackgroundResource(active ? R.drawable.indicator_active : R.drawable.indicator_inactive);
                }
            }
        });

        if (count > 1) {
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable auto = new Runnable() {
                @Override
                public void run() {
                    int next = (h.pager.getCurrentItem() + 1) % count;
                    h.pager.setCurrentItem(next, true);
                    handler.postDelayed(this, 4000);
                }
            };
            handler.postDelayed(auto, 4000);
        }
    }

    // --- Ranking ---
    private void bindRanking(RankingHolder h, RankingData data) {
        List<Drama> dramas = new ArrayList<>();
        for (RankItem r : data.rankings) {
            if (r.getDrama() != null) dramas.add(r.getDrama());
        }
        h.recycler.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
        h.recycler.setAdapter(new MustWatchAdapter(context, dramas));
        h.btnAll.setOnClickListener(v -> context.startActivity(new Intent(context, RankingActivity.class)));
    }

    // --- Category ---
    private void bindCategory(CategoryHolder h, CategoryData data) {
        if (h.recycler.getAdapter() == null && data.adapter != null) {
            h.recycler.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            h.recycler.setAdapter(data.adapter);
        }
    }

    // --- Drama ---
    @SuppressWarnings("unchecked")
    private void bindDrama(DramaHolder h, Drama drama) {
        h.title.setText(drama.getTitle());
        h.category.setText(drama.getCategory());
        h.heat.setText(drama.getHeatText() + "热度");
        h.status.setText(drama.getStatusText());
        h.desc.setText(drama.getDescription());
        String coverUrl = drama.getCoverUrl();
        if (coverUrl != null && !coverUrl.isEmpty()) {
            Glide.with(context).load(coverUrl)
                    .placeholder(R.drawable.bg_cover_placeholder)
                    .error(R.drawable.bg_cover_placeholder)
                    .centerCrop().into(h.cover);
        } else {
            h.cover.setImageResource(R.drawable.bg_cover_placeholder);
        }
        h.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, PlayerActivity.class);
            intent.putExtra("drama_id", drama.getId());
            context.startActivity(intent);
        });
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    // --- ViewHolders ---
    static class BannerHolder extends RecyclerView.ViewHolder {
        ViewPager2 pager; LinearLayout indicator;
        BannerHolder(View v) { super(v); pager = v.findViewById(R.id.banner_pager); indicator = v.findViewById(R.id.banner_indicator); }
    }
    static class RankingHolder extends RecyclerView.ViewHolder {
        RecyclerView recycler; View btnAll;
        RankingHolder(View v) { super(v); recycler = v.findViewById(R.id.recycler_ranking); btnAll = v.findViewById(R.id.btn_view_ranking); }
    }
    static class CategoryHolder extends RecyclerView.ViewHolder {
        RecyclerView recycler;
        CategoryHolder(View v) {
            super(v);
            if (v instanceof RecyclerView) recycler = (RecyclerView) v;
            else recycler = v.findViewById(R.id.recycler_categories);
        }
    }
    static class DramaHolder extends RecyclerView.ViewHolder {
        ImageView cover; TextView title, category, heat, status, desc;
        DramaHolder(View v) { super(v); cover = v.findViewById(R.id.drama_cover); title = v.findViewById(R.id.drama_title); category = v.findViewById(R.id.drama_category); heat = v.findViewById(R.id.drama_heat); status = v.findViewById(R.id.drama_status); desc = v.findViewById(R.id.drama_desc); }
    }

    // --- Data wrappers ---
    static class BannerData { List<Banner> banners; BannerData(List<Banner> b) { banners = b; } }
    static class RankingData { List<RankItem> rankings; RankingData(List<RankItem> r) { rankings = r; } }
    static class CategoryData {
        List<Category> categories;
        CategoryChipAdapter adapter;
        CategoryData(List<Category> c, CategoryChipAdapter a) { categories = c; adapter = a; }
    }
}
