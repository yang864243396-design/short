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

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                adapter.onPageSelected(position);
                if (position >= adapter.getItemCount() - 3 && !isLoading) {
                    currentPage++;
                    loadFeed(true);
                }
            }
        });

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
