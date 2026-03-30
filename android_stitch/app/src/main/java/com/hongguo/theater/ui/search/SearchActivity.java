package com.hongguo.theater.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.HotSearch;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchActivity extends AppCompatActivity {

    private EditText editSearch;
    private RecyclerView recyclerHotSearch, recyclerResults, recyclerSuggest;
    private View layoutDefault, layoutResults;
    private HotSearchAdapter hotSearchAdapter;
    private SearchResultAdapter resultAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        editSearch = findViewById(R.id.edit_search);
        recyclerHotSearch = findViewById(R.id.recycler_hot_search);
        recyclerResults = findViewById(R.id.recycler_results);
        recyclerSuggest = findViewById(R.id.recycler_suggest);
        layoutDefault = findViewById(R.id.layout_default);
        layoutResults = findViewById(R.id.layout_results);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.btn_clear_history).setOnClickListener(v -> {
            ApiClient.getService().clearSearchHistory().enqueue(new Callback<ApiResponse<Void>>() {
                @Override public void onResponse(@NonNull Call<ApiResponse<Void>> c, @NonNull Response<ApiResponse<Void>> r) {}
                @Override public void onFailure(@NonNull Call<ApiResponse<Void>> c, @NonNull Throwable t) {}
            });
        });

        hotSearchAdapter = new HotSearchAdapter(this);
        recyclerHotSearch.setLayoutManager(new LinearLayoutManager(this));
        recyclerHotSearch.setAdapter(hotSearchAdapter);

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
                    layoutDefault.setVisibility(View.VISIBLE);
                    layoutResults.setVisibility(View.GONE);
                }
            }
        });

        loadHotSearch();
        loadSuggest();
    }

    private void loadHotSearch() {
        ApiClient.getService().getHotSearch().enqueue(new Callback<ApiResponse<List<HotSearch>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<HotSearch>>> call,
                                   @NonNull Response<ApiResponse<List<HotSearch>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    hotSearchAdapter.setData(response.body().getData());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse<List<HotSearch>>> c, @NonNull Throwable t) {}
        });
    }

    private void loadSuggest() {
        ApiClient.getService().getSearchSuggest().enqueue(new Callback<ApiResponse<List<Drama>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Drama>>> call,
                                   @NonNull Response<ApiResponse<List<Drama>>> response) {
                // handled via suggest adapter
            }
            @Override public void onFailure(@NonNull Call<ApiResponse<List<Drama>>> c, @NonNull Throwable t) {}
        });
    }

    private void doSearch(String keyword) {
        if (keyword.isEmpty()) return;
        layoutDefault.setVisibility(View.GONE);
        layoutResults.setVisibility(View.VISIBLE);

        ApiClient.getService().search(keyword).enqueue(new Callback<ApiResponse<List<Drama>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Drama>>> call,
                                   @NonNull Response<ApiResponse<List<Drama>>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    resultAdapter.setData(response.body().getData());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse<List<Drama>>> c, @NonNull Throwable t) {}
        });
    }
}
