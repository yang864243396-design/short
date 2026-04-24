package com.hongguo.theater.ui.home;

import android.app.Activity;
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

    private final Handler bannerHandler = new Handler(Looper.getMainLooper());
    private Runnable bannerRunnable;

    private MustWatchAdapter cachedRankingAdapter;
    private LinearLayout activeIndicator;
    private ViewPager2 activePager;
    private final ViewPager2.OnPageChangeCallback indicatorCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int pos) {
            if (activeIndicator == null) return;
            for (int i = 0; i < activeIndicator.getChildCount(); i++) {
                View dot = activeIndicator.getChildAt(i);
                boolean active = (i == pos);
                LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) dot.getLayoutParams();
                lp.width = active ? dpToPx(16) : dpToPx(6);
                dot.setLayoutParams(lp);
                dot.setBackgroundResource(active ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            }
        }
    };

    private List<Banner> banners;
    private List<RankItem> rankings;
    private CategoryData categoryData;

    private OnCategoryClickListener categoryClickListener;

    public interface OnCategoryClickListener {
        void onCategoryClick(String category);
    }

    public HomeMainAdapter(Activity activity) {
        this.context = activity;
    }

    public void setCategoryClickListener(OnCategoryClickListener listener) {
        this.categoryClickListener = listener;
    }

    public void setBanners(List<Banner> banners) {
        this.banners = banners;
        int pos = findPosition(BannerData.class);
        if (banners != null && !banners.isEmpty()) {
            if (pos >= 0) {
                items.set(pos, new BannerData(banners));
                notifyItemChanged(pos);
            } else {
                int insertAt = 0;
                items.add(insertAt, new BannerData(banners));
                notifyItemInserted(insertAt);
            }
        } else if (pos >= 0) {
            items.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public void setRankings(List<RankItem> rankings) {
        this.rankings = rankings;
        int pos = findPosition(RankingData.class);
        if (rankings != null && !rankings.isEmpty()) {
            if (pos >= 0) {
                items.set(pos, new RankingData(rankings));
                notifyItemChanged(pos);
            } else {
                int insertAt = findPosition(BannerData.class) >= 0 ? 1 : 0;
                items.add(insertAt, new RankingData(rankings));
                notifyItemInserted(insertAt);
            }
        } else if (pos >= 0) {
            items.remove(pos);
            notifyItemRemoved(pos);
        }
    }

    public void setCategories(List<Category> categories, CategoryChipAdapter adapter) {
        this.categoryData = new CategoryData(categories, adapter);
        int pos = findPosition(CategoryData.class);
        if (pos >= 0) {
            items.set(pos, categoryData);
            notifyItemChanged(pos);
        } else {
            int insertAt = 0;
            if (findPosition(BannerData.class) >= 0) insertAt++;
            if (findPosition(RankingData.class) >= 0) insertAt++;
            items.add(insertAt, categoryData);
            notifyItemInserted(insertAt);
        }
    }

    public void setDramas(List<Drama> dramas) {
        int firstDrama = -1;
        int dramaCount = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof Drama) {
                if (firstDrama < 0) firstDrama = i;
                dramaCount++;
            }
        }
        if (firstDrama >= 0) {
            items.subList(firstDrama, firstDrama + dramaCount).clear();
            notifyItemRangeRemoved(firstDrama, dramaCount);
        }
        if (dramas != null && !dramas.isEmpty()) {
            int insertAt = firstDrama >= 0 ? firstDrama : items.size();
            items.addAll(insertAt, dramas);
            notifyItemRangeInserted(insertAt, dramas.size());
        }
    }

    public void addDramas(List<Drama> dramas) {
        if (dramas != null && !dramas.isEmpty()) {
            int start = items.size();
            items.addAll(dramas);
            notifyItemRangeInserted(start, dramas.size());
        }
    }

    private int findPosition(Class<?> type) {
        for (int i = 0; i < items.size(); i++) {
            if (type.isInstance(items.get(i))) return i;
        }
        return -1;
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
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        super.onViewRecycled(holder);
    }

    @Override
    public int getItemCount() { return items.size(); }

    // --- Banner ---
    private void bindBanner(BannerHolder h, BannerData data) {
        int count = data.banners.size();

        activeIndicator = h.indicator;

        if (h.bannerAdapter == null) {
            h.bannerAdapter = new BannerPagerAdapter(context);
        }
        if (h.pager.getAdapter() != h.bannerAdapter) {
            h.pager.setAdapter(h.bannerAdapter);
        }

        if (h.pager != activePager) {
            if (activePager != null) {
                activePager.unregisterOnPageChangeCallback(indicatorCallback);
            }
            h.pager.registerOnPageChangeCallback(indicatorCallback);
            activePager = h.pager;
        }

        h.bannerAdapter.setData(data.banners);

        // 略增大离屏页，便于相邻轮播图提前走网络与解码（配合 Glide override 尺寸）
        if (count > 0) {
            h.pager.setOffscreenPageLimit(Math.max(1, Math.min(count, 3)));
        }

        h.indicator.removeAllViews();
        for (int i = 0; i < count; i++) {
            View dot = new View(context);
            int w = (i == 0) ? dpToPx(16) : dpToPx(6);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, dpToPx(4));
            p.setMarginStart(i == 0 ? 0 : dpToPx(4));
            dot.setLayoutParams(p);
            dot.setBackgroundResource(i == 0 ? R.drawable.indicator_active : R.drawable.indicator_inactive);
            h.indicator.addView(dot);
        }

        if (count > 1) {
            if (bannerRunnable != null) {
                bannerHandler.removeCallbacks(bannerRunnable);
            }
            bannerRunnable = new Runnable() {
                @Override
                public void run() {
                    int next = (h.pager.getCurrentItem() + 1) % count;
                    h.pager.setCurrentItem(next, true);
                    bannerHandler.postDelayed(this, 5000);
                }
            };
            bannerHandler.postDelayed(bannerRunnable, 5000);
        }
    }

    // --- Ranking ---
    private void bindRanking(RankingHolder h, RankingData data) {
        List<Drama> dramas = new ArrayList<>();
        for (RankItem r : data.rankings) {
            if (r.getDrama() != null) dramas.add(r.getDrama());
        }
        if (cachedRankingAdapter == null) {
            cachedRankingAdapter = new MustWatchAdapter(context, dramas);
            h.recycler.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
            h.recycler.setAdapter(cachedRankingAdapter);
        } else {
            cachedRankingAdapter.setData(dramas);
            if (h.recycler.getAdapter() != cachedRankingAdapter) {
                h.recycler.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false));
                h.recycler.setAdapter(cachedRankingAdapter);
            }
        }
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
        DramaCardTagsHelper.bindTags(h.tags, context, drama);
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
            Glide.with(context).clear(h.cover);
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
        ViewPager2 pager;
        LinearLayout indicator;
        BannerPagerAdapter bannerAdapter;

        BannerHolder(View v) {
            super(v);
            pager = v.findViewById(R.id.banner_pager);
            indicator = v.findViewById(R.id.banner_indicator);
        }
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
        ImageView cover;
        TextView title, heat, status, desc;
        LinearLayout tags;
        DramaHolder(View v) {
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
    static class BannerData { List<Banner> banners; BannerData(List<Banner> b) { banners = b; } }
    static class RankingData { List<RankItem> rankings; RankingData(List<RankItem> r) { rankings = r; } }
    static class CategoryData {
        List<Category> categories;
        CategoryChipAdapter adapter;
        CategoryData(List<Category> c, CategoryChipAdapter a) { categories = c; adapter = a; }
    }
}
