package com.hongguo.theater.ui.wallet;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.api.ApiErrorHelper;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.CreateRechargeOrderResponse;
import com.hongguo.theater.model.PayProductItem;
import com.hongguo.theater.model.RechargePackageItem;
import com.hongguo.theater.model.RechargePackagesEnvelope;
import com.hongguo.theater.model.WalletBalance;
import com.hongguo.theater.utils.AdSkipCache;
import com.hongguo.theater.utils.HgDialog;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WalletActivity extends AppCompatActivity {

    private static final int GRID_SPAN = 3;

    private TextView tvWalletBalancePill, tvCoinRate;
    private TextView btnBalanceFlow;
    private RecyclerView recyclerPackages;
    private SwipeRefreshLayout swipeRefresh;
    private NestedScrollView nestedWallet;
    private WalletRechargePackageAdapter packageAdapter;

    private TextView tvSimulateHint;

    /** 最近一次拉取的充值接口数据（含支付方式） */
    @Nullable
    private RechargePackagesEnvelope rechargeEnv;
    @Nullable
    private String pendingMchForQuery;

    /** 与 pendingMch 对应，聚合 pay_order_id；台方单号与 mch 不一致时查单仅靠此也可命中 */
    @Nullable
    private String pendingPayOrderForQuery;

    /** 充值下单请求进行中，防止连点重复创建订单 */
    private boolean rechargeOrderInFlight;

    /** 已外跳系统（浏览器 / 支付 App 等）打开 payUrl，返回本页后在 onResume 中拉查单 */
    private boolean awaitingExternalPayReturn;

    @Nullable
    private AlertDialog orderSubmittingDialog;

    /** 从外跳支付返回时「检测支付中」的 loading，查单结束即关闭 */
    @Nullable
    private AlertDialog payCheckProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wallet);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        btnBalanceFlow = findViewById(R.id.btn_balance_flow);
        tvWalletBalancePill = findViewById(R.id.tv_wallet_balance_pill);
        tvCoinRate = findViewById(R.id.tv_coin_rate);
        recyclerPackages = findViewById(R.id.recycler_recharge_packages);
        swipeRefresh = findViewById(R.id.swipe_wallet);
        nestedWallet = findViewById(R.id.nested_wallet);
        tvSimulateHint = findViewById(R.id.tv_recharge_simulate_hint);

        Runnable remeasure = () -> nestedWallet.post(() -> {
            recyclerPackages.requestLayout();
            nestedWallet.requestLayout();
        });

        packageAdapter = new WalletRechargePackageAdapter();
        packageAdapter.setListener(this::confirmBuyPackage);
        recyclerPackages.setLayoutManager(new GridLayoutManager(this, GRID_SPAN));
        recyclerPackages.setAdapter(packageAdapter);
        recyclerPackages.setNestedScrollingEnabled(false);
        int gap = (int) (getResources().getDisplayMetrics().density * 6);
        recyclerPackages.addItemDecoration(new GridSpacingItemDecoration(GRID_SPAN, gap, true));
        registerRemeasureObserver(packageAdapter, remeasure);

        swipeRefresh.setOnRefreshListener(this::refreshAll);
        swipeRefresh.setColorSchemeResources(R.color.primary);

        btnBalanceFlow.setOnClickListener(v ->
                startActivity(new Intent(WalletActivity.this, WalletTransactionsActivity.class)));

        loadBalance();
        loadRechargePackages();
    }

    private void registerRemeasureObserver(RecyclerView.Adapter<?> a, Runnable remeasure) {
        a.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                remeasure.run();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                remeasure.run();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                remeasure.run();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (awaitingExternalPayReturn && (payReturnHasPendingKey())) {
            awaitingExternalPayReturn = false;
            showPayCheckProgress();
            queryAfterPay(pendingMchForQuery, pendingPayOrderForQuery);
        }
        loadBalance();
        loadRechargePackages();
    }

    @Override
    protected void onDestroy() {
        dismissOrderSubmittingDialog();
        dismissPayCheckProgress();
        super.onDestroy();
    }

    private void showOrderSubmittingDialog() {
        dismissOrderSubmittingDialog();
        orderSubmittingDialog = HgDialog.showLoading(this, getString(R.string.wallet_recharge_submitting));
    }

    private void dismissOrderSubmittingDialog() {
        if (orderSubmittingDialog == null) {
            return;
        }
        try {
            if (orderSubmittingDialog.isShowing()) {
                orderSubmittingDialog.dismiss();
            }
        } catch (Exception ignored) {
        }
        orderSubmittingDialog = null;
    }

    private void showPayCheckProgress() {
        dismissPayCheckProgress();
        payCheckProgressDialog = HgDialog.showLoading(this, getString(R.string.wallet_pay_checking));
    }

    private void dismissPayCheckProgress() {
        if (payCheckProgressDialog == null) {
            return;
        }
        try {
            if (payCheckProgressDialog.isShowing()) {
                payCheckProgressDialog.dismiss();
            }
        } catch (Exception ignored) {
        }
        payCheckProgressDialog = null;
    }

    private boolean payReturnHasPendingKey() {
        return (pendingMchForQuery != null && !pendingMchForQuery.isEmpty())
                || (pendingPayOrderForQuery != null && !pendingPayOrderForQuery.isEmpty());
    }

    private void loadRechargePackages() {
        ApiClient.getService().getRechargePackages().enqueue(new Callback<ApiResponse<RechargePackagesEnvelope>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<RechargePackagesEnvelope>> call,
                                   @NonNull Response<ApiResponse<RechargePackagesEnvelope>> response) {
                if (isFinishing()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    RechargePackagesEnvelope env = response.body().getData();
                    if (env != null) {
                        rechargeEnv = env;
                        tvSimulateHint.setVisibility(View.GONE);
                        List<RechargePackageItem> list = env.getList();
                        packageAdapter.setData(list);
                    }
                } else {
                    packageAdapter.setData(null);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<RechargePackagesEnvelope>> call, @NonNull Throwable t) {
                if (!isFinishing()) {
                    packageAdapter.setData(null);
                }
            }
        });
    }

    private void confirmBuyPackage(RechargePackageItem pkg) {
        int total = pkg.getCoins() + pkg.getBonusCoins();
        String msg = String.format(Locale.getDefault(), "%s\n基础 %d 金币", pkg.getName(), pkg.getCoins());
        if (pkg.getBonusCoins() > 0) {
            msg += String.format(Locale.getDefault(), " + 赠送 %d", pkg.getBonusCoins());
        }
        msg += String.format(Locale.getDefault(), "（共 %d）\n¥%.2f", total, pkg.getPriceYuan());
        HgDialog.showConfirm(
                this,
                getString(R.string.wallet_recharge_section),
                msg,
                getString(android.R.string.ok),
                d -> {
                    if (rechargeEnv != null && rechargeEnv.isLubzfEnabled()) {
                        List<PayProductItem> opts = rechargeEnv.getPayOptions();
                        if (opts.isEmpty()) {
                            Toast.makeText(this, R.string.wallet_no_pay_options, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (opts.size() == 1) {
                            submitRechargeOrder(pkg, opts.get(0).getProductId());
                        } else {
                            showPayMethodPicker(opts, pkg);
                        }
                    } else {
                        submitRechargeOrder(pkg, null);
                    }
                },
                getString(android.R.string.cancel),
                null,
                true,
                null);
    }

    private void showPayMethodPicker(List<PayProductItem> opts, RechargePackageItem pkg) {
        List<String> names = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (PayProductItem p : opts) {
            if (p == null || !p.isEnabled()) continue;
            names.add(p.getName() + " (" + p.getProductId() + ")");
            ids.add(p.getProductId());
        }
        if (ids.isEmpty()) {
            Toast.makeText(this, R.string.wallet_no_pay_options, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.wallet_pick_pay_method)
                .setItems(names.toArray(new String[0]), (dialog, which) -> submitRechargeOrder(pkg, ids.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void submitRechargeOrder(RechargePackageItem pkg, @Nullable String productId) {
        if (rechargeOrderInFlight) {
            Toast.makeText(this, R.string.wallet_recharge_submitting, Toast.LENGTH_SHORT).show();
            return;
        }
        rechargeOrderInFlight = true;
        showOrderSubmittingDialog();
        Map<String, Object> body = new HashMap<>();
        body.put("package_id", (int) pkg.getId());
        if (productId != null && !productId.isEmpty()) {
            body.put("product_id", productId);
        }
        ApiClient.getService().createRechargeOrder(body).enqueue(new Callback<ApiResponse<CreateRechargeOrderResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<CreateRechargeOrderResponse>> call,
                                   @NonNull Response<ApiResponse<CreateRechargeOrderResponse>> response) {
                dismissOrderSubmittingDialog();
                if (isFinishing()) {
                    rechargeOrderInFlight = false;
                    return;
                }
                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    if (BuildConfig.DEBUG) {
                        String dbg = safeDebugApiMessage(response);
                        if (dbg != null) {
                            Log.d("WalletPay", "createRechargeOrder err: " + dbg);
                        }
                    }
                    showPayUserHint(
                            R.string.wallet_pay_result_title,
                            payCreateUserMessageRes(response));
                    rechargeOrderInFlight = false;
                    return;
                }
                CreateRechargeOrderResponse data = response.body().getData();
                if (data == null || data.getOrder() == null) {
                    showPayUserHint(R.string.wallet_pay_result_title, R.string.wallet_pay_create_order_failed);
                    rechargeOrderInFlight = false;
                    return;
                }
                String payUrl = data.getPayUrl();
                if (payUrl != null && !payUrl.isEmpty()) {
                    String mch = data.getMchOrderNo();
                    if (mch == null && data.getOrder() != null) {
                        mch = data.getOrder().getMchOrderNo();
                    }
                    pendingMchForQuery = mch;
                    pendingPayOrderForQuery = null;
                    if (data.getOrder() != null) {
                        String poid = data.getOrder().getPayOrderId();
                        if (poid != null && !poid.isEmpty()) {
                            pendingPayOrderForQuery = poid;
                        }
                    }
                    awaitingExternalPayReturn = true;
                    // 先关 loading，再下一帧外跳，避免与对话框关闭动效「抢一帧」观感卡顿
                    View content = findViewById(android.R.id.content);
                    if (content != null) {
                        final String openUrl = payUrl;
                        content.post(() -> {
                            if (isFinishing()) {
                                rechargeOrderInFlight = false;
                                return;
                            }
                            if (!openPayUrlExternally(openUrl)) {
                                awaitingExternalPayReturn = false;
                                pendingMchForQuery = null;
                                pendingPayOrderForQuery = null;
                            }
                            rechargeOrderInFlight = false;
                        });
                    } else {
                        if (!openPayUrlExternally(payUrl)) {
                            awaitingExternalPayReturn = false;
                            pendingMchForQuery = null;
                            pendingPayOrderForQuery = null;
                        }
                        rechargeOrderInFlight = false;
                    }
                    return;
                }
                HgDialog.showInform(
                        WalletActivity.this,
                        getString(R.string.hg_recharge_success_title),
                        getString(R.string.hg_recharge_success_message),
                        getString(R.string.hg_dialog_got_it),
                        d -> {
                            loadBalance();
                            loadRechargePackages();
                        },
                        true,
                        null);
                rechargeOrderInFlight = false;
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<CreateRechargeOrderResponse>> call, @NonNull Throwable t) {
                dismissOrderSubmittingDialog();
                rechargeOrderInFlight = false;
                if (!isFinishing()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("WalletPay", "createRechargeOrder onFailure", t);
                    }
                    showPayUserHint(R.string.wallet_pay_result_title, R.string.wallet_pay_result_network);
                }
            }
        });
    }

    /**
     * 外跳支付：http(s) 用系统默认浏览器；intent: / 支付宝、微信 等由系统解析。
     */
    private boolean openPayUrlExternally(String payUrl) {
        if (payUrl == null) {
            return false;
        }
        String t = payUrl.trim();
        if (t.isEmpty()) {
            return false;
        }
        Uri u = Uri.parse(t);
        String sc = u.getScheme();
        if (sc != null) {
            String sl = sc.toLowerCase(Locale.ROOT);
            if ("http".equals(sl) || "https".equals(sl)) {
                return openInExternalBrowser(u);
            }
        }
        if (t.toLowerCase(Locale.ROOT).startsWith("intent:")) {
            try {
                Intent i = Intent.parseUri(t, Intent.URI_INTENT_SCHEME);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                return true;
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.wallet_pay_no_app, Toast.LENGTH_LONG).show();
                return false;
            } catch (Exception e) {
                Toast.makeText(this, R.string.wallet_pay_cannot_open, Toast.LENGTH_SHORT).show();
                return false;
            }
        }
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.wallet_pay_no_app, Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, R.string.wallet_pay_cannot_open, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    private boolean openInExternalBrowser(Uri u) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, u);
            i.addCategory(Intent.CATEGORY_BROWSABLE);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.wallet_pay_no_app, Toast.LENGTH_LONG).show();
            return false;
        } catch (Exception e) {
            Toast.makeText(this, R.string.wallet_pay_cannot_open, Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /** 向用户展示支付/下单类提示（禁止直接 Toast 服务端 message，避免长 JSON/堆栈） */
    private void showPayUserHint(int titleRes, int messageRes) {
        if (isFinishing()) {
            return;
        }
        HgDialog.showInform(
                this,
                getString(titleRes),
                getString(messageRes),
                getString(R.string.hg_dialog_got_it),
                d -> { },
                true,
                null);
    }

    private int payCreateUserMessageRes(@NonNull Response<ApiResponse<CreateRechargeOrderResponse>> response) {
        if (!response.isSuccessful()) {
            int c = response.code();
            if (c >= 500) {
                return R.string.wallet_pay_result_server_error;
            }
            if (c == 401) {
                return R.string.wallet_pay_result_unauthorized;
            }
            if (c == 400 || c == 404) {
                return R.string.wallet_pay_create_bad_input;
            }
            return R.string.wallet_pay_create_order_failed;
        }
        if (response.body() == null || !response.body().isSuccess()) {
            return R.string.wallet_pay_create_order_failed;
        }
        return R.string.wallet_pay_create_order_failed;
    }

    private int payQueryUserMessageRes(@NonNull Response<ApiResponse<CreateRechargeOrderResponse>> response) {
        if (!response.isSuccessful()) {
            int c = response.code();
            if (c >= 500) {
                return R.string.wallet_pay_result_server_error;
            }
            if (c == 401) {
                return R.string.wallet_pay_result_unauthorized;
            }
            if (c == 400 || c == 404) {
                return R.string.wallet_pay_result_order_gone;
            }
            return R.string.wallet_pay_result_query_unknown;
        }
        if (response.body() == null || !response.body().isSuccess()) {
            return R.string.wallet_pay_result_query_unknown;
        }
        return R.string.wallet_pay_result_query_unknown;
    }

    @Nullable
    private String safeDebugApiMessage(Response<ApiResponse<CreateRechargeOrderResponse>> response) {
        try {
            return ApiErrorHelper.parseMessage(response, "");
        } catch (Exception e) {
            return null;
        }
    }

    private void queryAfterPay(@Nullable String mch, @Nullable String payOrderId) {
        String mq = (mch != null && !mch.trim().isEmpty()) ? mch.trim() : null;
        String pq = (payOrderId != null && !payOrderId.trim().isEmpty()) ? payOrderId.trim() : null;
        if (mq == null && pq == null) {
            return;
        }
        ApiClient.getService().queryRechargeOrder(mq, pq).enqueue(new Callback<ApiResponse<CreateRechargeOrderResponse>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<CreateRechargeOrderResponse>> call,
                                   @NonNull Response<ApiResponse<CreateRechargeOrderResponse>> response) {
                pendingMchForQuery = null;
                pendingPayOrderForQuery = null;
                dismissPayCheckProgress();
                if (isFinishing()) return;
                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    if (BuildConfig.DEBUG) {
                        String dbg = safeDebugApiMessage(response);
                        if (dbg != null) {
                            Log.d("WalletPay", "queryRechargeOrder err: " + dbg);
                        }
                    }
                    showPayUserHint(
                            R.string.wallet_pay_result_title,
                            payQueryUserMessageRes(response));
                    loadBalance();
                    return;
                }
                CreateRechargeOrderResponse data = response.body().getData();
                if (data == null || data.getOrder() == null) {
                    showPayUserHint(R.string.wallet_pay_result_title, R.string.wallet_pay_result_query_unknown);
                    loadBalance();
                    return;
                }
                String st = data.getOrder().getStatus();
                loadBalance();
                loadRechargePackages();
                if ("paid".equalsIgnoreCase(st)) {
                    HgDialog.showInform(
                            WalletActivity.this,
                            getString(R.string.hg_recharge_success_title),
                            getString(R.string.hg_recharge_success_message),
                            getString(R.string.hg_dialog_got_it),
                            d -> { },
                            true,
                            null);
                    return;
                }
                if ("cancelled".equalsIgnoreCase(st)) {
                    HgDialog.showInform(
                            WalletActivity.this,
                            getString(R.string.wallet_pay_query_cancelled_title),
                            getString(R.string.wallet_pay_query_cancelled_message),
                            getString(R.string.hg_dialog_got_it),
                            d -> { },
                            true,
                            null);
                    return;
                }
                HgDialog.showInform(
                        WalletActivity.this,
                        getString(R.string.wallet_pay_query_pending_title),
                        getString(R.string.wallet_pay_query_pending_message),
                        getString(R.string.hg_dialog_got_it),
                        d -> { },
                        true,
                        null);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<CreateRechargeOrderResponse>> call, @NonNull Throwable t) {
                pendingMchForQuery = null;
                pendingPayOrderForQuery = null;
                dismissPayCheckProgress();
                if (!isFinishing()) {
                    if (BuildConfig.DEBUG) {
                        Log.d("WalletPay", "queryRechargeOrder onFailure", t);
                    }
                    showPayUserHint(R.string.wallet_pay_result_title, R.string.wallet_pay_result_network);
                    loadBalance();
                }
            }
        });
    }

    private void refreshAll() {
        swipeRefresh.setRefreshing(true);
        ApiClient.getService().getWallet().enqueue(new Callback<ApiResponse<WalletBalance>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<WalletBalance>> call,
                                   @NonNull Response<ApiResponse<WalletBalance>> response) {
                applyBalanceResponse(response);
                swipeRefresh.setRefreshing(false);
                loadRechargePackages();
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<WalletBalance>> call, @NonNull Throwable t) {
                Toast.makeText(WalletActivity.this, R.string.network_error, Toast.LENGTH_SHORT).show();
                swipeRefresh.setRefreshing(false);
                loadRechargePackages();
            }
        });
    }

    private void loadBalance() {
        ApiClient.getService().getWallet().enqueue(new Callback<ApiResponse<WalletBalance>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<WalletBalance>> call,
                                   @NonNull Response<ApiResponse<WalletBalance>> response) {
                applyBalanceResponse(response);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<WalletBalance>> call, @NonNull Throwable t) {
            }
        });
    }

    private void applyBalanceResponse(Response<ApiResponse<WalletBalance>> response) {
        if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) return;
        WalletBalance b = response.body().getData();
        AdSkipCache.applyWallet(b, this);
        String unit = b.getCurrencyName();
        tvWalletBalancePill.setText(String.format(Locale.getDefault(), "%d %s", b.getCoins(), unit));
        int cpu = b.getCoinsPerYuan();
        if (cpu > 0) {
            double yuanEach = 1.0 / cpu;
            tvCoinRate.setText(getString(R.string.wallet_coin_rate_format, yuanEach));
            tvCoinRate.setVisibility(View.VISIBLE);
        } else {
            tvCoinRate.setVisibility(View.GONE);
        }
    }
}
