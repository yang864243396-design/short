package com.hongguo.theater.ui.search;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.RankItem;
import com.hongguo.theater.ui.ranking.RankingAdapter;
import com.hongguo.theater.utils.PrefsManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchActivity extends AppCompatActivity {

    private static final int HOME_HOT_RANKING_LIMIT = 10;

    private EditText editSearch;
    private RecyclerView recyclerHotSearch, recyclerResults;
    private View layoutDefault;
    private View layoutSearchHistorySection;
    private com.google.android.material.chip.ChipGroup chipGroupHistory;

    private RankingAdapter hotRankingAdapter;
    private SearchResultAdapter resultAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        editSearch = findViewById(R.id.edit_search);
        recyclerHotSearch = findViewById(R.id.recycler_hot_search);
        recyclerResults = findViewById(R.id.recycler_results);
        layoutDefault = findViewById(R.id.layout_default);
        layoutSearchHistorySection = findViewById(R.id.layout_search_history_section);
        chipGroupHistory = findViewById(R.id.chip_group_search_history);

        findViewById(R.id.btn_back).setOnClickListener(v -> handleNavigateUp());

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (isShowingSearchResults()) {
                    leaveSearchResults();
                } else {
                    finish();
                }
            }
        });
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> {
            if (!PrefsManager.isLoggedIn()) return;
            ApiClient.getService().clearSearchHistory().enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<Void>> c, @NonNull Response<ApiResponse<Void>> r) {
                    if (r.isSuccessful() && r.body() != null && r.body().isSuccess()) {
                        refreshHistoryChips(Collections.emptyList());
                    }
                }
                @Override
                public void onFailure(@NonNull Call<ApiResponse<Void>> c, @NonNull Throwable t) {}
            });
        });

        hotRankingAdapter = new RankingAdapter(this);
        recyclerHotSearch.setLayoutManager(new LinearLayoutManager(this));
        recyclerHotSearch.setAdapter(hotRankingAdapter);

        resultAdapter = new SearchResultAdapter(this);
        recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerResults.setAdapter(resultAdapter);

        editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                doSearch(editSearch.getText().toString().trim());
                return true;
            }
            return false;
        });

        editSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() == 0) {
                    showDefaultPanel();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadHomeHotRanking();
        loadSearchHistory();
    }

    private boolean isShowingSearchResults() {
        return recyclerResults.getVisibility() == View.VISIBLE;
    }

    /** 从结果列表回到默认搜索页（历史、榜单），不关闭 Activity */
    private void leaveSearchResults() {
        hideKeyboard();
        showDefaultPanel();
    }

    private void hideKeyboard() {
        View focus = getCurrentFocus();
        if (focus == null) focus = editSearch;
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
        }
    }

    private void handleNavigateUp() {
        if (isShowingSearchResults()) {
            leaveSearchResults();
        } else {
            finish();
        }
    }

    private void showDefaultPanel() {
        layoutDefault.setVisibility(View.VISIBLE);
        recyclerResults.setVisibility(View.GONE);
    }

    private void refreshHistoryChips(List<String> keywords) {
        chipGroupHistory.removeAllViews();
        if (!PrefsManager.isLoggedIn()) {
            layoutSearchHistorySection.setVisibility(View.GONE);
            return;
        }
        if (keywords == null || keywords.isEmpty()) {
            layoutSearchHistorySection.setVisibility(View.GONE);
            return;
        }
        layoutSearchHistorySection.setVisibility(View.VISIBLE);
        for (String kw : keywords) {
            if (kw == null || kw.isEmpty()) continue;
            Chip chip = new Chip(this);
            chip.setText(kw);
            chip.setCheckable(false);
            chip.setChipBackgroundColorResource(R.color.surface_container);
            chip.setTextColor(getResources().getColor(R.color.on_surface, null));
            chip.setOnClickListener(v -> {
                editSearch.setText(kw);
                editSearch.setSelection(kw.length());
                doSearch(kw);
            });
            chipGroupHistory.addView(chip);
        }
    }

    private void loadSearchHistory() {
        if (!PrefsManager.isLoggedIn()) {
            layoutSearchHistorySection.setVisibility(View.GONE);
            chipGroupHistory.removeAllViews();
            return;
        }
        ApiClient.getService().getSearchHistory().enqueue(new Callback<ApiResponse<List<String>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<String>>> call,
                                   @NonNull Response<ApiResponse<List<String>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<String> data = response.body().getData();
                    refreshHistoryChips(data);
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<String>>> call, @NonNull Throwable t) {}
        });
    }

    /** 与首页「今日热播榜」同源：ranking:hot 缓存，取前 10 条 */
    private void loadHomeHotRanking() {
        ApiClient.getService().getRankings("hot").enqueue(new Callback<ApiResponse<List<RankItem>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<RankItem>>> call,
                                   @NonNull Response<ApiResponse<List<RankItem>>> response) {
                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    return;
                }
                List<RankItem> all = response.body().getData();
                if (all == null || all.isEmpty()) {
                    hotRankingAdapter.setData(Collections.emptyList());
                    return;
                }
                int n = Math.min(HOME_HOT_RANKING_LIMIT, all.size());
                hotRankingAdapter.setData(new ArrayList<>(all.subList(0, n)));
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<RankItem>>> c, @NonNull Throwable t) {}
        });
    }

    private void doSearch(String keyword) {
        if (keyword.isEmpty()) {
            Toast.makeText(this, R.string.search_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        layoutDefault.setVisibility(View.GONE);
        recyclerResults.setVisibility(View.VISIBLE);

        ApiClient.getService().search(keyword).enqueue(new Callback<ApiResponse<List<Drama>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Drama>>> call,
                                   @NonNull Response<ApiResponse<List<Drama>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    resultAdapter.setData(response.body().getData());
                    if (PrefsManager.isLoggedIn()) {
                        loadSearchHistory();
                    }
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<List<Drama>>> c, @NonNull Throwable t) {
                Toast.makeText(SearchActivity.this, "搜索失败", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
