package com.hongguo.theater.ui.theater;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.tabs.TabLayout;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Category;
import com.hongguo.theater.model.Drama;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TheaterFragment extends Fragment {

    private TabLayout tabLayout;
    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TheaterAdapter adapter;
    private String currentCategory = "";
    private int currentPage = 1;
    private boolean isLoading = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_theater, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tabLayout = view.findViewById(R.id.tab_layout);
        swipeRefresh = view.findViewById(R.id.swipe_refresh);
        recyclerView = view.findViewById(R.id.recycler_theater);

        swipeRefresh.setColorSchemeColors(getResources().getColor(R.color.primary, null));
        swipeRefresh.setProgressBackgroundColorSchemeColor(
                getResources().getColor(R.color.surface_container, null));
        swipeRefresh.setOnRefreshListener(() -> {
            currentPage = 1;
            loadDramas(false);
        });

        adapter = new TheaterAdapter(requireContext());
        recyclerView.setLayoutManager(new GridLayoutManager(requireContext(), 2));
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                GridLayoutManager lm = (GridLayoutManager) rv.getLayoutManager();
                if (lm != null && !isLoading) {
                    int totalItems = lm.getItemCount();
                    int lastVisible = lm.findLastVisibleItemPosition();
                    if (lastVisible >= totalItems - 4) {
                        currentPage++;
                        loadDramas(true);
                    }
                }
            }
        });

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentCategory = tab.getPosition() == 0 ? "" : tab.getText().toString();
                currentPage = 1;
                loadDramas(false);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadCategories();
    }

    private void loadCategories() {
        ApiClient.getService().getCategories().enqueue(new Callback<ApiResponse<List<Category>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Category>>> call,
                                   @NonNull Response<ApiResponse<List<Category>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    tabLayout.removeAllTabs();
                    tabLayout.addTab(tabLayout.newTab().setText("全部"));
                    for (Category c : response.body().getData()) {
                        tabLayout.addTab(tabLayout.newTab().setText(c.getName()));
                    }
                }
                loadDramas(false);
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Category>>> call, @NonNull Throwable t) {
                if (isAdded()) loadDramas(false);
            }
        });
    }

    private void loadDramas(boolean append) {
        isLoading = true;
        if (!append) swipeRefresh.setRefreshing(true);

        ApiClient.getService().getDramas(currentCategory, currentPage, 20)
                .enqueue(new Callback<ApiResponse<List<Drama>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Drama>>> call,
                                   @NonNull Response<ApiResponse<List<Drama>>> response) {
                if (!isAdded()) return;
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    if (append) {
                        adapter.addData(response.body().getData());
                    } else {
                        adapter.setData(response.body().getData());
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Drama>>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                isLoading = false;
                swipeRefresh.setRefreshing(false);
                Toast.makeText(requireContext(), "加载失败: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}
