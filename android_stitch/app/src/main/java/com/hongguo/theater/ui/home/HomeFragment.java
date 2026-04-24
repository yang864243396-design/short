package com.hongguo.theater.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Banner;
import com.hongguo.theater.model.Category;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.HomeData;
import com.hongguo.theater.ui.search.SearchActivity;
import com.hongguo.theater.utils.ImageUrlUtils;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerMain;
    private HomeMainAdapter mainAdapter;

    private RecyclerView stickyCategories;
    private CategoryChipAdapter categoryChipAdapter;
    private CategoryChipAdapter stickyCategoryAdapter;

    private View layoutEmpty;
    private TextView tvEmptyMsg;
    private ProgressBar progressBar;

    private boolean isFirstLoad = true;
    private String currentCategory = "";
    private int currentPage = 1;
    private boolean isDramaLoading = false;
    private boolean hasMoreDramas = true;
    private static final int PAGE_SIZE = 20;

    /** 首批数据到达时列表常未完成最终测量，Glide 初次加载易失败；刷新可见项触发二次绑定即可恢复 */
    private static final long MAIN_LIST_IMAGE_RETRY_MS = 160;
    private final Runnable mainListImageRetryRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAdded()) return;
            if (recyclerMain == null || mainAdapter == null) return;
            LinearLayoutManager lm = (LinearLayoutManager) recyclerMain.getLayoutManager();
            if (lm == null) return;
            int first = lm.findFirstVisibleItemPosition();
            int last = lm.findLastVisibleItemPosition();
            if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) return;
            for (int i = first; i <= last; i++) {
                mainAdapter.notifyItemChanged(i);
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.search_bar).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SearchActivity.class)));

        stickyCategories = view.findViewById(R.id.sticky_categories);
        stickyCategories.setLayoutManager(
                new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));

        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        swipeRefresh.setColorSchemeColors(getResources().getColor(R.color.primary, null));
        swipeRefresh.setProgressBackgroundColorSchemeColor(
                getResources().getColor(R.color.surface_container, null));
        swipeRefresh.setOnRefreshListener(this::refreshAll);

        recyclerMain = view.findViewById(R.id.recycler_main);
        LinearLayoutManager lm = new LinearLayoutManager(requireContext());
        recyclerMain.setLayoutManager(lm);
        mainAdapter = new HomeMainAdapter(requireActivity());
        recyclerMain.setAdapter(mainAdapter);

        recyclerMain.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateStickyCategory(lm);
                handleLoadMore(lm, dy);
            }
        });

        layoutEmpty = view.findViewById(R.id.layout_empty);
        tvEmptyMsg = view.findViewById(R.id.tv_empty_msg);
        progressBar = view.findViewById(R.id.progress_bar);
        view.findViewById(R.id.btn_retry).setOnClickListener(v -> refreshAll());

        refreshAll();
    }

    @Override
    public void onDestroyView() {
        if (recyclerMain != null) {
            recyclerMain.removeCallbacks(mainListImageRetryRunnable);
        }
        super.onDestroyView();
    }

    private void updateStickyCategory(LinearLayoutManager lm) {
        int catPos = mainAdapter.getCategoryPosition();
        if (catPos < 0) return;

        View catView = lm.findViewByPosition(catPos);
        if (catView != null) {
            boolean scrolledOut = catView.getTop() < 0;
            stickyCategories.setVisibility(scrolledOut ? View.VISIBLE : View.GONE);
        } else {
            int firstVisible = lm.findFirstVisibleItemPosition();
            stickyCategories.setVisibility(firstVisible > catPos ? View.VISIBLE : View.GONE);
        }
    }

    private void handleLoadMore(LinearLayoutManager lm, int dy) {
        if (!isDramaLoading && hasMoreDramas && dy > 0) {
            int total = lm.getItemCount();
            int lastVisible = lm.findLastVisibleItemPosition();
            if (lastVisible >= total - 3) {
                currentPage++;
                loadDramas(true);
            }
        }
    }

    private void refreshAll() {
        if (isFirstLoad) {
            progressBar.setVisibility(View.VISIBLE);
            layoutEmpty.setVisibility(View.GONE);
        }
        swipeRefresh.setRefreshing(!isFirstLoad);

        loadBanners();
        loadHomeData();
    }

    private void loadBanners() {
        ApiClient.getService().getBanners().enqueue(new Callback<ApiResponse<List<Banner>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Banner>>> call,
                                   @NonNull Response<ApiResponse<List<Banner>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().isSuccess() && response.body().getData() != null
                        && !response.body().getData().isEmpty()) {
                    List<Banner> list = response.body().getData();
                    preloadBannerImages(list);
                    mainAdapter.setBanners(list);
                } else {
                    mainAdapter.setBanners(null);
                }
                scheduleVisibleItemsImageRetry();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Banner>>> call, @NonNull Throwable t) {
                if (isAdded()) mainAdapter.setBanners(null);
            }
        });
    }

    private void scheduleVisibleItemsImageRetry() {
        if (recyclerMain == null) return;
        recyclerMain.removeCallbacks(mainListImageRetryRunnable);
        recyclerMain.postDelayed(mainListImageRetryRunnable, MAIN_LIST_IMAGE_RETRY_MS);
    }

    /** 首装无磁盘缓存时提前拉取前几张，减轻 ViewPager2 首次绑定的等待 */
    private void preloadBannerImages(List<Banner> list) {
        if (list == null || list.isEmpty()) return;
        int[] wh = BannerPagerAdapter.bannerTargetSizePx(requireContext());
        int n = Math.min(list.size(), 3);
        for (int i = 0; i < n; i++) {
            String url = ImageUrlUtils.resolve(list.get(i).getImageUrl());
            if (url == null || url.isEmpty()) continue;
            Glide.with(requireContext())
                    .load(url)
                    .override(wh[0], wh[1])
                    .priority(Priority.HIGH)
                    .preload();
        }
    }

    private void loadHomeData() {
        ApiClient.getService().getHome().enqueue(new Callback<ApiResponse<HomeData>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<HomeData>> call,
                                   @NonNull Response<ApiResponse<HomeData>> response) {
                if (!isAdded()) return;
                isFirstLoad = false;
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);

                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    HomeData data = response.body().getData();
                    if (data != null) {
                        layoutEmpty.setVisibility(View.GONE);
                        mainAdapter.setRankings(data.getHotRanking());
                        setupCategories(data.getCategories());
                        currentCategory = "";
                        currentPage = 1;
                        hasMoreDramas = true;
                        scheduleVisibleItemsImageRetry();
                        loadDramas(false);
                        return;
                    }
                }
                showEmpty("服务器返回数据异常");
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<HomeData>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                isFirstLoad = false;
                progressBar.setVisibility(View.GONE);
                swipeRefresh.setRefreshing(false);
                showEmpty("网络连接失败\n" + t.getMessage());
            }
        });
    }

    private void setupCategories(List<Category> categories) {
        if (categories == null || categories.isEmpty()) return;

        if (categoryChipAdapter == null) {
            CategoryChipAdapter.OnChipClickListener listener = cat -> {
                currentCategory = cat;
                currentPage = 1;
                hasMoreDramas = true;
                loadDramas(false);
            };

            categoryChipAdapter = new CategoryChipAdapter(requireContext(), categories, listener);
            stickyCategoryAdapter = new CategoryChipAdapter(requireContext(), categories, listener);

            categoryChipAdapter.setSyncAdapter(stickyCategoryAdapter);
            stickyCategoryAdapter.setSyncAdapter(categoryChipAdapter);

            stickyCategories.setAdapter(stickyCategoryAdapter);
            mainAdapter.setCategories(categories, categoryChipAdapter);
        }
    }

    private void loadDramas(boolean append) {
        isDramaLoading = true;
        final int pageRequested = currentPage;
        ApiClient.getService().getDramas(currentCategory, currentPage, PAGE_SIZE)
                .enqueue(new Callback<ApiResponse<List<Drama>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<List<Drama>>> call,
                                           @NonNull Response<ApiResponse<List<Drama>>> response) {
                        if (!isAdded()) return;
                        isDramaLoading = false;
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            List<Drama> dramas = response.body().getData();
                            if (dramas == null || dramas.size() < PAGE_SIZE) {
                                hasMoreDramas = false;
                            }
                            if (append) {
                                mainAdapter.addDramas(dramas);
                            } else {
                                mainAdapter.setDramas(dramas);
                            }
                            scheduleVisibleItemsImageRetry();
                        } else if (append && pageRequested > 1) {
                            currentPage = pageRequested - 1;
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<List<Drama>>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        isDramaLoading = false;
                        if (append && pageRequested > 1) {
                            currentPage = pageRequested - 1;
                        }
                    }
                });
    }

    private void showEmpty(String msg) {
        layoutEmpty.setVisibility(View.VISIBLE);
        tvEmptyMsg.setText(msg);
    }
}
