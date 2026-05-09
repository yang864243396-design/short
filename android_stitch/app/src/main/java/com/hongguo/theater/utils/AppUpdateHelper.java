package com.hongguo.theater.utils;

import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.hongguo.theater.BuildConfig;
import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.model.ReleaseCheckPayload;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.regex.Pattern;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * 冷启动版本检查：失败或无数据静默；仅版本与后台不一致时弹窗；
 * 全屏遮罩 + 中间卡片（对齐规格「全屏阻塞」）；应用内下载 APK。
 */
public final class AppUpdateHelper {

    private static final Pattern VERSION_RE = Pattern.compile("^\\d+\\.\\d+\\.\\d+$");
    private static final OkHttpClient HTTP = new OkHttpClient.Builder().build();

    private AppUpdateHelper() {}

    public static void checkOnColdStart(AppCompatActivity activity) {
        if (activity.isFinishing()) return;
        ApiClient.getService().getReleaseCheck("android").enqueue(new Callback<ApiResponse<ReleaseCheckPayload>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<ReleaseCheckPayload>> call, @NonNull Response<ApiResponse<ReleaseCheckPayload>> response) {
                if (activity.isFinishing()) return;
                if (!response.isSuccessful()) return;
                ApiResponse<ReleaseCheckPayload> body = response.body();
                if (body == null || !body.isSuccess()) return;
                ReleaseCheckPayload payload = body.getData();
                if (payload == null) return;
                String remote = payload.getVersion();
                String url = payload.getDownloadUrl();
                if (remote == null || url == null || remote.isEmpty() || url.isEmpty()) return;

                String local;
                try {
                    PackageInfo pi = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
                    local = pi.versionName != null ? pi.versionName : "";
                } catch (Exception e) {
                    return;
                }

                if (!shouldPromptUpdate(local, remote)) return;

                new Handler(Looper.getMainLooper()).post(() -> showFullScreenUpdate(activity, payload, url));
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<ReleaseCheckPayload>> call, @NonNull Throwable t) {
                // 静默
            }
        });
    }

    static boolean shouldPromptUpdate(String localRaw, String remoteRaw) {
        String remote = remoteRaw != null ? remoteRaw.trim() : "";
        if (!VERSION_RE.matcher(remote).matches()) return false;
        String local = localRaw != null ? localRaw.trim() : "";
        if (!VERSION_RE.matcher(local).matches()) {
            return true;
        }
        return !local.equals(remote);
    }

    private static void showFullScreenUpdate(AppCompatActivity activity, ReleaseCheckPayload payload, String apkUrl) {
        if (activity.isFinishing()) return;
        boolean force = payload.isForceUpdate();
        String notes = payload.getReleaseNotes();
        String ver = payload.getVersion() != null ? payload.getVersion() : "";
        String msgBody = (notes != null && !notes.isEmpty()) ? notes : ("发现新版本 " + ver);

        Dialog dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_app_update);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0f);
        }

        dialog.setCancelable(!force);
        dialog.setCanceledOnTouchOutside(false);

        View scrim = dialog.findViewById(R.id.app_update_root);
        if (scrim != null) {
            scrim.setOnClickListener(v -> { /* 全屏遮罩吞点击，不关闭（非强制也依赖按钮关闭） */ });
        }

        ((TextView) dialog.findViewById(R.id.app_update_subtitle)).setText("新版本 " + ver);
        ((TextView) dialog.findViewById(R.id.app_update_message)).setText(msgBody);

        View btnOk = dialog.findViewById(R.id.app_update_btn_ok);
        btnOk.setOnClickListener(v -> {
            downloadAndInstall(activity, apkUrl);
            if (!force) {
                dialog.dismiss();
            }
        });

        View btnCancel = dialog.findViewById(R.id.app_update_btn_cancel);
        if (force) {
            btnCancel.setVisibility(View.GONE);
        } else {
            btnCancel.setVisibility(View.VISIBLE);
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }

        if (force) {
            dialog.setOnKeyListener((d, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                    return true;
                }
                return false;
            });
        }

        dialog.show();
    }

    private static void downloadAndInstall(AppCompatActivity activity, String apkUrl) {
        new Thread(() -> {
            File out = new File(activity.getCacheDir(), "hongguo_update.apk");
            try {
                Request req = new Request.Builder().url(apkUrl).build();
                try (okhttp3.Response res = HTTP.newCall(req).execute()) {
                    if (!res.isSuccessful() || res.body() == null) return;
                    try (InputStream in = res.body().byteStream(); FileOutputStream fos = new FileOutputStream(out)) {
                        byte[] buf = new byte[8192];
                        int n;
                        while ((n = in.read(buf)) >= 0) {
                            fos.write(buf, 0, n);
                        }
                    }
                }
            } catch (Exception e) {
                return;
            }
            new Handler(Looper.getMainLooper()).post(() -> {
                if (activity.isFinishing()) return;
                try {
                    Uri uri = FileProvider.getUriForFile(activity, BuildConfig.APPLICATION_ID + ".fileprovider", out);
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    intent.setDataAndType(uri, "application/vnd.android.package-archive");
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } catch (Exception ignored) {
                }
            });
        }).start();
    }
}
