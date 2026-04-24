package com.hongguo.theater.ui.profile;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.content.res.Resources;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.hongguo.theater.HongguoApp;
import com.hongguo.theater.MainActivity;
import com.hongguo.theater.utils.ImageUrlUtils;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.api.ApiErrorHelper;
import com.hongguo.theater.model.AdSkipStatus;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.model.User;
import com.hongguo.theater.model.WatchHistory;
import com.hongguo.theater.ui.auth.LoginActivity;
import com.hongguo.theater.ui.player.PlayerActivity;
import com.hongguo.theater.ui.wallet.WalletActivity;
import com.hongguo.theater.utils.AdSkipCache;
import com.hongguo.theater.utils.FormatUtils;
import com.hongguo.theater.utils.HgDialog;
import com.hongguo.theater.utils.PrefsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private ImageView ivAvatar;
    private TextView tvUsername, tvLoginHint, btnLogout, tvListEmpty, tvWalletSummary;
    private TextView profileTvAdSkipStatus, profileBtnBuyAdSkip;
    private View layoutLoggedIn, layoutNotLoggedIn, profileHeader, rowWallet;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProfileListAdapter adapter;
    private final List<AdSkipStatus.Config> cachedAdSkipConfigs = new ArrayList<>();
    private int cachedWalletCoins;
    /** 权益在期时应购加油包（与 {@link #resolveAdSkipPurchaseConfigs} 一致） */
    private boolean adSkipPurchaseShowBoosterMode;
    /** 用于防抖：与 onResume 重复拉取、切到「我的」时补拉，避免与刚完成的请求打叠 */
    private long lastAdSkipFetchAtMs;
    @Nullable
    private Call<ApiResponse<AdSkipStatus>> adSkipPickerCall;
    @Nullable
    private Call<ApiResponse<AdSkipStatus>> adSkipLoadCall;

    private ActivityResultLauncher<Intent> loginLauncher;
    private ActivityResultLauncher<Intent> pickImageLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        loginLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        refreshUI();
                    }
                });
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) uploadAvatar(uri);
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        profileHeader = view.findViewById(R.id.profile_header);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        tvUsername = view.findViewById(R.id.tv_username);
        tvLoginHint = view.findViewById(R.id.tv_login_hint);
        btnLogout = view.findViewById(R.id.btn_logout);
        layoutLoggedIn = view.findViewById(R.id.layout_logged_in);
        layoutNotLoggedIn = view.findViewById(R.id.layout_not_logged_in);
        tabLayout = view.findViewById(R.id.profile_tabs);
        recyclerView = view.findViewById(R.id.recycler_profile);
        tvListEmpty = view.findViewById(R.id.tv_list_empty);
        rowWallet = view.findViewById(R.id.row_wallet);
        tvWalletSummary = view.findViewById(R.id.tv_wallet_summary);
        profileTvAdSkipStatus = view.findViewById(R.id.profile_tv_ad_skip_status);
        profileBtnBuyAdSkip = view.findViewById(R.id.profile_btn_buy_ad_skip);

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

        profileHeader.setOnClickListener(v -> {
            if (!PrefsManager.isLoggedIn()) {
                openLogin();
            }
        });

        ivAvatar.setOnClickListener(v -> {
            if (PrefsManager.isLoggedIn()) {
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                pickImageLauncher.launch(Intent.createChooser(intent, "选择头像"));
            }
        });

        view.findViewById(R.id.btn_go_login).setOnClickListener(v -> openLogin());

        btnLogout.setOnClickListener(v -> {
            PrefsManager.logout();
            refreshUI();
        });

        rowWallet.setOnClickListener(v -> {
            if (PrefsManager.isLoggedIn()) {
                startActivity(new Intent(requireContext(), WalletActivity.class));
            }
        });

        profileBtnBuyAdSkip.setEnabled(false);
        profileBtnBuyAdSkip.setAlpha(0.45f);
        profileBtnBuyAdSkip.setOnClickListener(v -> openAdSkipTierPicker());

        refreshUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUI();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) return;
        if (!isResumed() || !PrefsManager.isLoggedIn() || profileTvAdSkipStatus == null) return;
        long now = System.currentTimeMillis();
        if (lastAdSkipFetchAtMs > 0 && now - lastAdSkipFetchAtMs < 500L) {
            return;
        }
        loadAdSkipStatus();
    }

    @Override
    public void onDestroyView() {
        if (adSkipPickerCall != null) {
            adSkipPickerCall.cancel();
            adSkipPickerCall = null;
        }
        if (adSkipLoadCall != null) {
            adSkipLoadCall.cancel();
            adSkipLoadCall = null;
        }
        super.onDestroyView();
    }

    private void openLogin() {
        loginLauncher.launch(new Intent(requireContext(), LoginActivity.class));
    }

    private void refreshUI() {
        boolean loggedIn = PrefsManager.isLoggedIn();

        layoutLoggedIn.setVisibility(loggedIn ? View.VISIBLE : View.GONE);
        layoutNotLoggedIn.setVisibility(loggedIn ? View.GONE : View.VISIBLE);
        btnLogout.setVisibility(loggedIn ? View.VISIBLE : View.GONE);

        if (loggedIn) {
            tvLoginHint.setVisibility(View.GONE);
            loadProfile();
            loadAdSkipStatus();
            loadTabData(tabLayout.getSelectedTabPosition());
        } else {
            tvUsername.setText("未登录");
            tvLoginHint.setVisibility(View.VISIBLE);
            ivAvatar.setImageResource(R.drawable.ic_profile);
            adapter.clearData();
            showEmptyIfNeeded(false);
        }
    }

    private void loadAdSkipStatus() {
        if (!PrefsManager.isLoggedIn() || profileTvAdSkipStatus == null) return;
        if (adSkipLoadCall != null) {
            adSkipLoadCall.cancel();
        }
        profileBtnBuyAdSkip.setEnabled(false);
        profileBtnBuyAdSkip.setAlpha(0.45f);
        cachedAdSkipConfigs.clear();
        adSkipLoadCall = ApiClient.getService().getAdSkipStatus();
        adSkipLoadCall.enqueue(new Callback<ApiResponse<AdSkipStatus>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AdSkipStatus>> call,
                                   @NonNull Response<ApiResponse<AdSkipStatus>> response) {
                if (call.isCanceled() || !isAdded()) return;
                if (adSkipLoadCall == call) {
                    adSkipLoadCall = null;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AdSkipStatus d = response.body().getData();
                    if (d != null) {
                        updateUiFromAdSkipStatus(d);
                    } else {
                        showAdSkipSyncFailHint();
                    }
                } else {
                    showAdSkipSyncFailHint();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AdSkipStatus>> call, @NonNull Throwable t) {
                if (call.isCanceled() || !isAdded()) return;
                if (adSkipLoadCall == call) {
                    adSkipLoadCall = null;
                }
                cachedAdSkipConfigs.clear();
                profileBtnBuyAdSkip.setEnabled(false);
                profileBtnBuyAdSkip.setAlpha(0.45f);
                showAdSkipSyncFailHint();
            }
        });
    }

    private void showAdSkipSyncFailHint() {
        if (profileTvAdSkipStatus == null || !isAdded()) return;
        Toast.makeText(requireContext(), R.string.wallet_ad_skip_status_sync_fail, Toast.LENGTH_SHORT).show();
    }

    /** 将服务端免广告状态写入缓存与个人中心展示（不发起网络请求）。 */
    private void updateUiFromAdSkipStatus(@NonNull AdSkipStatus d) {
        cachedWalletCoins = d.getCoins();
        AdSkipCache.applyAdSkipStatus(d, requireContext());
        adSkipPurchaseShowBoosterMode = d.isAdSkipActive();
        if (d.isAdSkipActive() && d.getAdSkipExpiresAt() != null && !d.getAdSkipExpiresAt().isEmpty()) {
            String t = d.getAdSkipExpiresAt().replace('T', ' ');
            if (t.length() > 19) t = t.substring(0, 19);
            profileTvAdSkipStatus.setText(getString(R.string.wallet_ad_skip_status_on, t, d.getAdSkipRemaining()));
        } else {
            profileTvAdSkipStatus.setText(R.string.wallet_ad_skip_status_off);
        }
        cachedAdSkipConfigs.clear();
        List<AdSkipStatus.Config> opts = resolveAdSkipPurchaseConfigs(d);
        if (opts != null) {
            cachedAdSkipConfigs.addAll(opts);
        }
        boolean canBuy = !cachedAdSkipConfigs.isEmpty();
        profileBtnBuyAdSkip.setText(adSkipPurchaseShowBoosterMode
                ? R.string.wallet_buy_ad_skip_booster
                : R.string.wallet_buy_ad_skip);
        profileBtnBuyAdSkip.setEnabled(canBuy);
        profileBtnBuyAdSkip.setAlpha(canBuy ? 1f : 0.45f);
        lastAdSkipFetchAtMs = System.currentTimeMillis();
    }

    /**
     * 无权益/已过期：可购时间包；权益在期：可购加油包。
     * 优先 {@code booster_configs}；否则从 {@code configs} 筛 package_type=booster；
     * 若仍为空但 {@code configs} 非空，则整表作为可购（与后端在期时把 configs 换为加油包表一致，且兼容单条未带 package_type 被默认成 time 的 Gson 问题）。
     */
    @NonNull
    private static List<AdSkipStatus.Config> resolveAdSkipPurchaseConfigs(@NonNull AdSkipStatus d) {
        if (!d.isAdSkipActive()) {
            return d.getTimeConfigs();
        }
        List<AdSkipStatus.Config> boost = d.getBoosterConfigs();
        if (boost != null && !boost.isEmpty()) {
            return new ArrayList<>(boost);
        }
        List<AdSkipStatus.Config> cfg = d.getConfigs();
        if (cfg == null || cfg.isEmpty()) {
            return new ArrayList<>();
        }
        List<AdSkipStatus.Config> typedBoost = new ArrayList<>();
        for (AdSkipStatus.Config c : cfg) {
            if ("booster".equalsIgnoreCase(c.getPackageType())) {
                typedBoost.add(c);
            }
        }
        if (!typedBoost.isEmpty()) {
            return typedBoost;
        }
        return new ArrayList<>(cfg);
    }

    @Nullable
    private static AdSkipStatus.Config findAdSkipConfigById(
            @Nullable List<AdSkipStatus.Config> list, long id) {
        if (list == null) return null;
        for (AdSkipStatus.Config c : list) {
            if (c.getId() == id) {
                return c;
            }
        }
        return null;
    }

    private void openAdSkipTierPicker() {
        if (!PrefsManager.isLoggedIn()) return;
        if (adSkipPickerCall != null) {
            adSkipPickerCall.cancel();
        }
        adSkipPickerCall = ApiClient.getService().getAdSkipStatus();
        adSkipPickerCall.enqueue(new Callback<ApiResponse<AdSkipStatus>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AdSkipStatus>> call,
                                   @NonNull Response<ApiResponse<AdSkipStatus>> response) {
                if (call.isCanceled() || !isAdded()) return;
                if (adSkipPickerCall == call) {
                    adSkipPickerCall = null;
                }
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    AdSkipStatus d = response.body().getData();
                    if (d != null) {
                        updateUiFromAdSkipStatus(d);
                    }
                    if (cachedAdSkipConfigs.isEmpty()) {
                        Toast.makeText(requireContext(), R.string.wallet_ad_skip_no_options, Toast.LENGTH_SHORT).show();
                    } else {
                        showAdSkipTierPickerDialog();
                    }
                } else {
                    Toast.makeText(requireContext(), R.string.wallet_ad_skip_status_sync_fail, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AdSkipStatus>> call, @NonNull Throwable t) {
                if (call.isCanceled() || !isAdded()) return;
                if (adSkipPickerCall == call) {
                    adSkipPickerCall = null;
                }
                Toast.makeText(requireContext(), R.string.wallet_ad_skip_status_sync_fail, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAdSkipTierPickerDialog() {
        if (cachedAdSkipConfigs.isEmpty() || !isAdded()) return;
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ad_skip_tiers, null, false);
        TextView tvDialogTitle = content.findViewById(R.id.tv_ad_skip_dialog_title);
        tvDialogTitle.setText(adSkipPurchaseShowBoosterMode
                ? R.string.wallet_ad_skip_privilege_title_booster
                : R.string.wallet_ad_skip_privilege_title);
        RecyclerView rv = content.findViewById(R.id.recycler_ad_skip_tiers);
        int span = 2;
        rv.setLayoutManager(new GridLayoutManager(requireContext(), span));
        int gap = (int) getResources().getDimension(R.dimen.ad_skip_tier_grid_gap);
        rv.addItemDecoration(new AdSkipTierGridSpacingDecoration(span, gap));
        rv.setVerticalScrollBarEnabled(false);
        rv.setHorizontalScrollBarEnabled(false);
        rv.setItemAnimator(null);
        AdSkipTierPickerAdapter adapter = new AdSkipTierPickerAdapter();
        adapter.setData(cachedAdSkipConfigs);
        rv.setAdapter(adapter);
        int listH = computeAdSkipTierGridListHeightPx(rv, cachedAdSkipConfigs.size(), span);
        ViewGroup.LayoutParams lpRv = rv.getLayoutParams();
        lpRv.height = listH;
        rv.setLayoutParams(lpRv);

        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        TextView tvBalance = content.findViewById(R.id.tv_ad_skip_dialog_balance);
        tvBalance.setText(getString(R.string.wallet_ad_skip_balance_format, nf.format(cachedWalletCoins)));

        final Dialog dialog = new Dialog(requireContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setCanceledOnTouchOutside(true);

        content.findViewById(R.id.btn_ad_skip_dialog_close).setOnClickListener(v -> dialog.dismiss());
        content.findViewById(R.id.tv_ad_skip_dialog_recharge).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), WalletActivity.class));
            dialog.dismiss();
        });
        content.findViewById(R.id.btn_ad_skip_dialog_unlock).setOnClickListener(v -> {
            AdSkipStatus.Config sel = adapter.getSelectedConfig();
            if (sel == null) return;
            dialog.dismiss();
            showAdSkipPayConfirm(sel);
        });

        Window win = dialog.getWindow();
        if (win != null) {
            win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            DisplayMetrics dm = getResources().getDisplayMetrics();
            float density = dm.density;
            int margin = (int) (24 * density);
            int width = Math.min(dm.widthPixels - 2 * margin, (int) (360 * density));
            win.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = win.getAttributes();
            lp.dimAmount = 0.55f;
            win.setAttributes(lp);
        }
        dialog.show();
    }

    /**
     * 套餐网格区域高度：2 列下按行数增高，最多 3 行（6 个套餐），与 {@code ad_skip_dialog_grid_max_height} 对齐；
     * 超过 6 个时高度封顶，由 RecyclerView 内部滚动。
     */
    private static int computeAdSkipTierGridListHeightPx(RecyclerView rv, int itemCount, int span) {
        if (itemCount <= 0 || rv == null) {
            return ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        Resources res = rv.getResources();
        int maxTotal = res.getDimensionPixelSize(R.dimen.ad_skip_dialog_grid_max_height);
        int gap = res.getDimensionPixelSize(R.dimen.ad_skip_tier_grid_gap);
        int rowMin = res.getDimensionPixelSize(R.dimen.ad_skip_tier_grid_row_min_height);
        int pad = rv.getPaddingTop() + rv.getPaddingBottom();
        int innerMax = Math.max(0, maxTotal - pad);
        int rowStride = (innerMax - 2 * gap) / 3;
        rowStride = Math.max(rowStride, rowMin);
        int rows = (itemCount + span - 1) / span;
        int visRows = Math.min(rows, 3);
        int h = pad + visRows * rowStride + Math.max(0, visRows - 1) * gap;
        return Math.min(h, maxTotal);
    }

    private void showAdSkipPayConfirm(@NonNull AdSkipStatus.Config sel) {
        if (!isAdded()) return;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        String priceStr = nf.format(sel.getPriceCoins());
        String balStr = nf.format(cachedWalletCoins);
        String tierLine = FormatUtils.formatAdSkipTierTitle(requireContext(), sel);
        String message = getString(R.string.wallet_ad_skip_pay_confirm_message, tierLine, priceStr, balStr);
        HgDialog.showConfirm(
                requireContext(),
                getString(R.string.wallet_ad_skip_pay_confirm_title),
                message,
                getString(R.string.wallet_ad_skip_confirm_pay),
                d -> {
                    if (!isAdded()) return;
                    validateConfigThenPurchase(sel);
                },
                getString(R.string.common_cancel),
                null,
                true,
                null);
    }

    private void showAdSkipInsufficientDialog(int requiredCoins) {
        if (!isAdded()) return;
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        String msg = getString(
                R.string.wallet_ad_skip_insufficient_message,
                nf.format(requiredCoins),
                nf.format(cachedWalletCoins));
        HgDialog.showConfirm(
                requireContext(),
                getString(R.string.wallet_ad_skip_insufficient_title),
                msg,
                getString(R.string.wallet_ad_skip_go_recharge),
                d -> startActivity(new Intent(requireContext(), WalletActivity.class)),
                getString(R.string.common_cancel),
                null,
                true,
                null);
    }

    private static boolean isInsufficientCoinsMessage(@Nullable String msg) {
        if (msg == null) return false;
        return msg.contains("金币不足") || msg.contains("余额不足");
    }

    /**
     * 支付前拉取最新档位：与本地选择不一致则拒绝下单，提示刷新并展示服务端价格。
     */
    private void validateConfigThenPurchase(@NonNull AdSkipStatus.Config clientSel) {
        ApiClient.getService().getAdSkipStatus().enqueue(new Callback<ApiResponse<AdSkipStatus>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<AdSkipStatus>> call,
                                   @NonNull Response<ApiResponse<AdSkipStatus>> response) {
                if (!isAdded()) return;
                if (!response.isSuccessful() || response.body() == null || !response.body().isSuccess()) {
                    String msg = ApiErrorHelper.parseMessage(response, getString(R.string.network_error));
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    return;
                }
                AdSkipStatus d = response.body().getData();
                if (d == null) {
                    Toast.makeText(requireContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
                    return;
                }
                updateUiFromAdSkipStatus(d);

                AdSkipStatus.Config fresh = findAdSkipConfigById(d.getConfigs(), clientSel.getId());
                if (fresh == null) {
                    HgDialog.showInform(
                            requireContext(),
                            getString(R.string.wallet_ad_skip_price_refreshed_title),
                            getString(R.string.wallet_ad_skip_tier_removed_message),
                            getString(R.string.wallet_ad_skip_reopen_picker),
                            dialog -> openAdSkipTierPicker(),
                            true,
                            null);
                    return;
                }
                if (fresh.getPriceCoins() != clientSel.getPriceCoins()
                        || fresh.getDurationHours() != clientSel.getDurationHours()) {
                    showAdSkipConfigStaleDialog(clientSel, fresh, d);
                    return;
                }
                if (cachedWalletCoins < fresh.getPriceCoins()) {
                    showAdSkipInsufficientDialog(fresh.getPriceCoins());
                    return;
                }
                doPurchaseAdSkip(fresh);
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<AdSkipStatus>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showAdSkipConfigStaleDialog(
            @NonNull AdSkipStatus.Config stale,
            @NonNull AdSkipStatus.Config fresh,
            @NonNull AdSkipStatus full) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        String tierLine = FormatUtils.formatAdSkipTierTitle(requireContext(), fresh);
        String core = getString(
                R.string.wallet_ad_skip_price_refreshed_message,
                tierLine,
                nf.format(stale.getPriceCoins()),
                nf.format(fresh.getPriceCoins()));
        String unit = getString(R.string.wallet_unit_coins_label);
        StringBuilder sb = new StringBuilder(core);
        sb.append("\n\n").append(getString(R.string.wallet_ad_skip_latest_tiers_header));
        for (AdSkipStatus.Config c : full.getConfigs()) {
            sb.append("\n· ")
                    .append(FormatUtils.formatAdSkipTierTitle(requireContext(), c))
                    .append(" — ")
                    .append(nf.format(c.getPriceCoins()))
                    .append(' ')
                    .append(unit);
        }
        HgDialog.showInform(
                requireContext(),
                getString(R.string.wallet_ad_skip_price_refreshed_title),
                sb.toString(),
                getString(R.string.wallet_ad_skip_reopen_picker),
                dialog -> openAdSkipTierPicker(),
                true,
                null);
    }

    private void navigateToProfileAndRefreshAfterAdSkip() {
        android.app.Activity act = getActivity();
        if (act instanceof MainActivity) {
            ((MainActivity) act).openProfileTab();
        }
        loadProfile();
        loadAdSkipStatus();
        android.app.Activity top = HongguoApp.getCurrentActivity();
        if (top instanceof PlayerActivity) {
            ((PlayerActivity) top).applyAdSkipFromCache();
        }
    }

    private void doPurchaseAdSkip(AdSkipStatus.Config sel) {
        if (sel == null) return;
        Map<String, Object> body = new HashMap<>();
        body.put("config_id", sel.getId());
        ApiClient.getService().purchaseAdSkip(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> call,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Map<String, Object> payload = response.body().getData();
                    if (payload != null) {
                        AdSkipCache.applyPurchaseMap(payload, requireContext());
                    }
                    HgDialog.showInform(
                            requireContext(),
                            getString(R.string.hg_ad_skip_purchase_success_title),
                            getString(R.string.hg_ad_skip_purchase_success_message),
                            getString(R.string.hg_dialog_got_it),
                            d -> navigateToProfileAndRefreshAfterAdSkip(),
                            true,
                            null);
                } else {
                    String msg = ApiErrorHelper.parseMessage(response, getString(R.string.network_error));
                    if (isInsufficientCoinsMessage(msg)) {
                        showAdSkipInsufficientDialog(sel.getPriceCoins());
                    } else {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadProfile() {
        ApiClient.getService().getProfile().enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<User>> call,
                                   @NonNull Response<ApiResponse<User>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    User user = response.body().getData();
                    tvUsername.setText(user.getDisplayName());
                    loadAvatarImage(user.getAvatar());
                    tvWalletSummary.setText(getString(R.string.profile_wallet_summary_format, user.getCoins()));
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<User>> call, @NonNull Throwable t) {}
        });
    }

    private void loadAvatarImage(String avatarUrl) {
        if (!isAdded()) return;
        String fullUrl = ImageUrlUtils.resolve(avatarUrl);
        if (fullUrl != null && !fullUrl.isEmpty()) {
            Glide.with(this).load(fullUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivAvatar);
        } else {
            Glide.with(this).clear(ivAvatar);
            ivAvatar.setImageResource(R.drawable.ic_profile);
        }
    }

    private void loadTabData(int tabIndex) {
        if (!PrefsManager.isLoggedIn()) return;
        adapter.clearData();
        showEmptyIfNeeded(false);
        switch (tabIndex) {
            case 0:
                loadHistory();
                break;
            case 1:
                loadCollections();
                break;
            case 2:
                loadLikes();
                break;
        }
    }

    private void loadHistory() {
        ApiClient.getService().getHistory().enqueue(new Callback<ApiResponse<List<WatchHistory>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<WatchHistory>>> call,
                                   @NonNull Response<ApiResponse<List<WatchHistory>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<WatchHistory> data = response.body().getData();
                    adapter.setHistoryData(data);
                    showEmptyIfNeeded(data == null || data.isEmpty());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse<List<WatchHistory>>> call, @NonNull Throwable t) {}
        });
    }

    private void loadCollections() {
        ApiClient.getService().getCollections().enqueue(new Callback<ApiResponse<List<Drama>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Drama>>> call,
                                   @NonNull Response<ApiResponse<List<Drama>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Drama> data = response.body().getData();
                    adapter.setDramaData(data);
                    showEmptyIfNeeded(data == null || data.isEmpty());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse<List<Drama>>> call, @NonNull Throwable t) {}
        });
    }

    private void loadLikes() {
        ApiClient.getService().getLikedEpisodes().enqueue(new Callback<ApiResponse<List<Episode>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<List<Episode>>> call,
                                   @NonNull Response<ApiResponse<List<Episode>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    List<Episode> data = response.body().getData();
                    adapter.setEpisodeData(data);
                    showEmptyIfNeeded(data == null || data.isEmpty());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse<List<Episode>>> call, @NonNull Throwable t) {}
        });
    }

    private void uploadAvatar(Uri uri) {
        try {
            String fileName = "avatar.jpg";
            Cursor cursor = requireContext().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (cursor.moveToFirst() && nameIndex >= 0) {
                    fileName = cursor.getString(nameIndex);
                }
                cursor.close();
            }

            InputStream is = requireContext().getContentResolver().openInputStream(uri);
            if (is == null) return;
            File tempFile = new File(requireContext().getCacheDir(), fileName);
            FileOutputStream fos = new FileOutputStream(tempFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) != -1) fos.write(buf, 0, len);
            fos.close();
            is.close();

            RequestBody body = RequestBody.create(MediaType.parse("image/*"), tempFile);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", tempFile.getName(), body);

            ApiClient.getService().uploadAvatar(part).enqueue(new Callback<ApiResponse<User>>() {
                @Override
                public void onResponse(@NonNull Call<ApiResponse<User>> call,
                                       @NonNull Response<ApiResponse<User>> response) {
                    tempFile.delete();
                    if (!isAdded()) return;
                    if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                        User user = response.body().getData();
                        loadAvatarImage(user.getAvatar());
                        Toast.makeText(requireContext(), "头像更新成功", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(requireContext(), "头像更新失败", Toast.LENGTH_SHORT).show();
                    }
                }
                @Override
                public void onFailure(@NonNull Call<ApiResponse<User>> call, @NonNull Throwable t) {
                    tempFile.delete();
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "网络错误", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(requireContext(), "读取图片失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void showEmptyIfNeeded(boolean empty) {
        tvListEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }
}
