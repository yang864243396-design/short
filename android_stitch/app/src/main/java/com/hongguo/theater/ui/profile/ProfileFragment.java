package com.hongguo.theater.ui.profile;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.model.Episode;
import com.hongguo.theater.model.User;
import com.hongguo.theater.model.WatchHistory;
import com.hongguo.theater.ui.auth.LoginActivity;
import com.hongguo.theater.utils.PrefsManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private ImageView ivAvatar;
    private TextView tvUsername, tvLoginHint, btnLogout, tvListEmpty;
    private View layoutLoggedIn, layoutNotLoggedIn, profileHeader;
    private TabLayout tabLayout;
    private RecyclerView recyclerView;
    private ProfileListAdapter adapter;

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

        refreshUI();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshUI();
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
            loadTabData(tabLayout.getSelectedTabPosition());
        } else {
            tvUsername.setText("未登录");
            tvLoginHint.setVisibility(View.VISIBLE);
            ivAvatar.setImageResource(R.drawable.ic_profile);
            adapter.clearData();
            showEmptyIfNeeded(false);
        }
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
                }
            }
            @Override
            public void onFailure(@NonNull Call<ApiResponse<User>> call, @NonNull Throwable t) {}
        });
    }

    private void loadAvatarImage(String avatarUrl) {
        if (!isAdded()) return;
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            String fullUrl = avatarUrl.startsWith("http") ? avatarUrl
                    : BuildConfig.BASE_URL.replace("/api/v1/", "") + avatarUrl;
            Glide.with(this).load(fullUrl)
                    .placeholder(R.drawable.ic_profile)
                    .error(R.drawable.ic_profile)
                    .circleCrop()
                    .into(ivAvatar);
        } else {
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
