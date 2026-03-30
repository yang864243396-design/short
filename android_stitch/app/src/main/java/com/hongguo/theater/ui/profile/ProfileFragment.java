package com.hongguo.theater.ui.profile;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.User;
import com.hongguo.theater.model.WatchHistory;
import com.hongguo.theater.utils.PrefsManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private TextView tvUsername;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProfileListAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        tvUsername = view.findViewById(R.id.tv_username);
        tabLayout = view.findViewById(R.id.profile_tabs);
        recyclerView = view.findViewById(R.id.recycler_profile);

        adapter = new ProfileListAdapter(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_history));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_collection));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.tab_liked));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                loadTabData(tab.getPosition());
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadProfile();
        loadTabData(0);
    }

    private void loadProfile() {
        if (!PrefsManager.isLoggedIn()) {
            tvUsername.setText("未登录");
            return;
        }
        ApiClient.getService().getProfile().enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<User>> call,
                                   @NonNull Response<ApiResponse<User>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    User user = response.body().getData();
                    tvUsername.setText(user.getDisplayName());
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<User>> call, @NonNull Throwable t) {}
        });
    }

    private void loadTabData(int tabIndex) {
        if (!PrefsManager.isLoggedIn()) return;
        switch (tabIndex) {
            case 0:
                ApiClient.getService().getHistory().enqueue(new Callback<ApiResponse<List<WatchHistory>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<List<WatchHistory>>> call,
                                           @NonNull Response<ApiResponse<List<WatchHistory>>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            adapter.setHistoryData(response.body().getData());
                        }
                    }
                    @Override public void onFailure(@NonNull Call<ApiResponse<List<WatchHistory>>> call, @NonNull Throwable t) {}
                });
                break;
            case 1:
                ApiClient.getService().getCollections().enqueue(new Callback<ApiResponse<List<Drama>>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<List<Drama>>> call,
                                           @NonNull Response<ApiResponse<List<Drama>>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            adapter.setDramaData(response.body().getData());
                        }
                    }
                    @Override public void onFailure(@NonNull Call<ApiResponse<List<Drama>>> call, @NonNull Throwable t) {}
                });
                break;
            case 2:
                adapter.clearData();
                break;
        }
    }
}
