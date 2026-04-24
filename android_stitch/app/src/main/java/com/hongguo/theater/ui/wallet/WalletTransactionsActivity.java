package com.hongguo.theater.ui.wallet;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.WalletTransaction;
import com.hongguo.theater.model.WalletTransactionsPage;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WalletTransactionsActivity extends AppCompatActivity {

    private static final int PAGE_SIZE = 20;

    private SwipeRefreshLayout swipeRefresh;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private WalletTxListAdapter adapter;
    private LinearLayoutManager layoutManager;

    private final List<WalletTransaction> allTransactions = new ArrayList<>();

    private int currentPage = 1;
    private int totalCount = 0;
    private boolean loadingMore = false;
    /** 上一页接口返回条数，用于判断是否还有下一页 */
    private int lastPageListSize = 0;

    /** 0 全部 1 充值 2 消费 */
    private int tabIndex = 0;
    @Nullable
    private String apiTypeFilter;

    private TextView tabAll;
    private TextView tabRecharge;
    private TextView tabConsume;
    private View underline0;
    private View underline1;
    private View underline2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet_transactions);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        swipeRefresh = findViewById(R.id.swipe_tx);
        recyclerView = findViewById(R.id.recycler_wallet_tx);
        tvEmpty = findViewById(R.id.tv_wallet_tx_empty);

        tabAll = findViewById(R.id.tab_wallet_tx_all);
        tabRecharge = findViewById(R.id.tab_wallet_tx_recharge);
        tabConsume = findViewById(R.id.tab_wallet_tx_consume);
        underline0 = findViewById(R.id.underline_wallet_tx_0);
        underline1 = findViewById(R.id.underline_wallet_tx_1);
        underline2 = findViewById(R.id.underline_wallet_tx_2);

        tabAll.setOnClickListener(v -> selectTab(0, true));
        tabRecharge.setOnClickListener(v -> selectTab(1, true));
        tabConsume.setOnClickListener(v -> selectTab(2, true));

        adapter = new WalletTxListAdapter();
        layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                if (dy <= 0 || loadingMore || swipeRefresh.isRefreshing()) return;
                if (!hasMorePages()) return;
                int last = layoutManager.findLastVisibleItemPosition();
                int n = adapter.getItemCount();
                if (n > 0 && last >= n - 4) {
                    loadNextPage();
                }
            }
        });

        swipeRefresh.setOnRefreshListener(this::refreshFirstPage);
        swipeRefresh.setColorSchemeResources(R.color.wallet_tx_accent);

        selectTab(0, false);
        loadPage(1, true);
    }

    private void selectTab(int index, boolean reload) {
        tabIndex = index;
        if (index == 0) apiTypeFilter = null;
        else if (index == 1) apiTypeFilter = "recharge";
        else apiTypeFilter = "consume";

        int active = ContextCompat.getColor(this, R.color.wallet_tx_accent);
        int inactive = ContextCompat.getColor(this, R.color.on_surface_variant);
        tabAll.setTextColor(index == 0 ? active : inactive);
        tabRecharge.setTextColor(index == 1 ? active : inactive);
        tabConsume.setTextColor(index == 2 ? active : inactive);
        tabAll.setTypeface(null, index == 0 ? Typeface.BOLD : Typeface.NORMAL);
        tabRecharge.setTypeface(null, index == 1 ? Typeface.BOLD : Typeface.NORMAL);
        tabConsume.setTypeface(null, index == 2 ? Typeface.BOLD : Typeface.NORMAL);

        underline0.setBackgroundColor(index == 0 ? active : android.graphics.Color.TRANSPARENT);
        underline1.setBackgroundColor(index == 1 ? active : android.graphics.Color.TRANSPARENT);
        underline2.setBackgroundColor(index == 2 ? active : android.graphics.Color.TRANSPARENT);

        if (reload) {
            refreshFirstPage();
        }
    }

    private boolean hasMorePages() {
        if (totalCount <= 0) return false;
        if (allTransactions.size() >= totalCount) return false;
        if (lastPageListSize > 0 && lastPageListSize < PAGE_SIZE) return false;
        return true;
    }

    private void refreshFirstPage() {
        currentPage = 1;
        loadingMore = false;
        lastPageListSize = 0;
        allTransactions.clear();
        totalCount = 0;
        adapter.setTransactions(allTransactions, false);
        loadPage(1, true);
    }

    private void rebuildListUi() {
        boolean showFooter = !allTransactions.isEmpty() && !hasMorePages();
        adapter.setTransactions(allTransactions, showFooter);
        int allowedCount = adapter.getTransactionRowCount();
        boolean empty = allowedCount == 0;
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void loadPage(int page, boolean replace) {
        if (page == 1 && replace) {
            swipeRefresh.setRefreshing(true);
        }
        ApiClient.getService().getWalletTransactions(page, PAGE_SIZE, apiTypeFilter).enqueue(
                new Callback<ApiResponse<WalletTransactionsPage>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<WalletTransactionsPage>> call,
                                           @NonNull Response<ApiResponse<WalletTransactionsPage>> response) {
                        if (isFinishing()) return;
                        swipeRefresh.setRefreshing(false);
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            WalletTransactionsPage data = response.body().getData();
                            totalCount = data.getTotal();
                            currentPage = data.getPage();
                            List<WalletTransaction> list = data.getList();
                            lastPageListSize = list == null ? 0 : list.size();
                            if (replace) {
                                allTransactions.clear();
                            }
                            if (list != null) {
                                allTransactions.addAll(list);
                            }
                            rebuildListUi();
                        } else if (replace) {
                            allTransactions.clear();
                            rebuildListUi();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<WalletTransactionsPage>> call,
                                          @NonNull Throwable t) {
                        if (isFinishing()) return;
                        swipeRefresh.setRefreshing(false);
                        Toast.makeText(WalletTransactionsActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                        if (replace && allTransactions.isEmpty()) {
                            rebuildListUi();
                        }
                    }
                });
    }

    private void loadNextPage() {
        if (!hasMorePages()) return;
        loadingMore = true;
        int next = currentPage + 1;
        ApiClient.getService().getWalletTransactions(next, PAGE_SIZE, apiTypeFilter).enqueue(
                new Callback<ApiResponse<WalletTransactionsPage>>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse<WalletTransactionsPage>> call,
                                           @NonNull Response<ApiResponse<WalletTransactionsPage>> response) {
                        loadingMore = false;
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            WalletTransactionsPage p = response.body().getData();
                            currentPage = p.getPage();
                            List<WalletTransaction> list = p.getList();
                            lastPageListSize = list == null ? 0 : list.size();
                            if (list != null && !list.isEmpty()) {
                                allTransactions.addAll(list);
                            }
                            rebuildListUi();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse<WalletTransactionsPage>> call,
                                          @NonNull Throwable t) {
                        loadingMore = false;
                    }
                });
    }
}
