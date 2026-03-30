package com.hongguo.theater.ui.ranking;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.RankItem;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RankingActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private RankingAdapter adapter;
    private String currentType = "hot";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ranking);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        TabLayout tabLayout = findViewById(R.id.tab_layout);
        recyclerView = findViewById(R.id.recycler_ranking);

        adapter = new RankingAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.ranking_hot));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.ranking_rising));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.ranking_rating));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                switch (tab.getPosition()) {
                    case 0: currentType = "hot"; break;
                    case 1: currentType = "rising"; break;
                    case 2: currentType = "rating"; break;
                }
                loadRankings();
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        loadRankings();
    }

    private void loadRankings() {
        ApiClient.getService().getRankings(currentType).enqueue(new Callback<ApiResponse<List<RankItem>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<RankItem>>> call,
                                   @NonNull Response<ApiResponse<List<RankItem>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    adapter.setData(response.body().getData());
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<RankItem>>> call, @NonNull Throwable t) {}
        });
    }
}
