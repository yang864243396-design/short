package com.hongguo.theater.ui.play;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Episode;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PlayFeedFragment extends Fragment {

    private ViewPager2 viewPager;
    private FeedPagerAdapter adapter;
    private int currentPage = 1;
    private boolean isLoading = false;
    /** 冷启动不在首 Tab 请求 Feed，避免与首页接口抢连接与带宽 */
    private boolean initialFeedRequested = false;
    /** 从播放详情「全剧终」回刷剧页时，跳到该剧在 Feed 中的下一部 */
    private long pendingScrollAfterDramaId = 0L;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_play_feed, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewPager = view.findViewById(R.id.feed_pager);
        adapter = new FeedPagerAdapter(requireContext());
        viewPager.setAdapter(adapter);
        viewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        viewPager.setOffscreenPageLimit(1);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.onPageSelected(position);
                if (position >= adapter.getItemCount() - 3 && !isLoading && initialFeedRequested) {
                    currentPage++;
                    loadFeed(true);
                }
            }
        });
    }

    public void setPendingScrollAfterDrama(long dramaId) {
        pendingScrollAfterDramaId = dramaId;
        if (viewPager != null) {
            viewPager.post(this::tryApplyPendingScroll);
        }
    }

    private void tryApplyPendingScroll() {
        if (pendingScrollAfterDramaId <= 0L || viewPager == null || adapter == null) return;
        if (adapter.getItemCount() == 0) return;
        int idx = adapter.findNextDramaStartIndex(pendingScrollAfterDramaId);
        if (idx >= 0) {
            viewPager.setCurrentItem(idx, true);
            pendingScrollAfterDramaId = 0L;
        }
    }

    /** 由 MainActivity 在用户首次切到「刷剧」时调用，不提前预加载 */
    public void ensureInitialFeedLoaded() {
        if (initialFeedRequested) return;
        initialFeedRequested = true;
        loadFeed(false);
    }

    private void loadFeed(boolean append) {
        isLoading = true;
        ApiClient.getService().getFeed(currentPage, 10, 1).enqueue(new Callback<ApiResponse<List<Episode>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Episode>>> call,
                                   @NonNull Response<ApiResponse<List<Episode>>> response) {
                if (!isAdded()) return;
                isLoading = false;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    if (append) {
                        adapter.addData(response.body().getData());
                    } else {
                        adapter.setData(response.body().getData());
                    }
                    tryApplyPendingScroll();
                    if (pendingScrollAfterDramaId > 0L && adapter.getItemCount() > 0
                            && adapter.findNextDramaStartIndex(pendingScrollAfterDramaId) < 0) {
                        pendingScrollAfterDramaId = 0L;
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Episode>>> call, @NonNull Throwable t) {
                if (isAdded()) isLoading = false;
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        adapter.pauseCurrentPlayer();
    }

    @Override
    public void onResume() {
        super.onResume();
        adapter.resumeCurrentPlayer();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter.releaseAllPlayers();
    }
}
