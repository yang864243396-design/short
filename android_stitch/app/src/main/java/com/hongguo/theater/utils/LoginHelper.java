package com.hongguo.theater.utils;

import android.content.Context;
import android.content.Intent;

import com.hongguo.theater.ui.auth.LoginActivity;

public class LoginHelper {

    /**
     * @return true 如果已登录，false 如果未登录（会弹出提示对话框）
     */
    public static boolean requireLogin(Context context) {
        if (PrefsManager.isLoggedIn()) {
            return true;
        }
        HgDialog.showConfirm(
                context,
                "提示",
                "请先登录后再进行操作",
                "去登录",
                d -> context.startActivity(new Intent(context, LoginActivity.class)),
                "取消",
                null,
                true,
                null);
        return false;
    }

    /**
     * Token 过期时由 TokenInterceptor 调用，弹出重新登录提示
     */
    public static void showExpiredDialog(Context context, Runnable onDismiss) {
        HgDialog.showConfirm(
                context,
                "登录已过期",
                "您的登录状态已失效，请重新登录",
                "重新登录",
                d -> context.startActivity(new Intent(context, LoginActivity.class)),
                "取消",
                null,
                false,
                dialog -> {
                    if (onDismiss != null) onDismiss.run();
                });
    }
}
