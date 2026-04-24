package com.hongguo.theater.api;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.hongguo.theater.HongguoApp;
import com.hongguo.theater.utils.LoginHelper;
import com.hongguo.theater.utils.PrefsManager;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TokenInterceptor implements Interceptor {

    private static final AtomicBoolean loginDialogShowing = new AtomicBoolean(false);

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String token = PrefsManager.getToken();

        Request.Builder b = original.newBuilder();
        if (token != null && !token.isEmpty()) {
            b.header("Authorization", "Bearer " + token);
        }
        // 免广档位/价格以服务端为准，避免中间层 GET 缓存导致后台改价后仍显示旧数据
        String path = original.url().encodedPath();
        if (path != null && path.contains("ad-skip")) {
            b.header("Cache-Control", "no-cache");
        }

        Request request = b.build();

        Response response = chain.proceed(request);

        if (response.code() == 401 && PrefsManager.isLoggedIn()) {
            PrefsManager.logout();
            showLoginDialog();
        }

        return response;
    }

    private void showLoginDialog() {
        if (!loginDialogShowing.compareAndSet(false, true)) return;

        new Handler(Looper.getMainLooper()).post(() -> {
            Activity activity = HongguoApp.getCurrentActivity();
            if (activity != null && !activity.isFinishing()) {
                LoginHelper.showExpiredDialog(activity, () -> loginDialogShowing.set(false));
            } else {
                loginDialogShowing.set(false);
            }
        });
    }

    public static void resetDialogFlag() {
        loginDialogShowing.set(false);
    }
}
