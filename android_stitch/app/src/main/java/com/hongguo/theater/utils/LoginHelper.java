package com.hongguo.theater.utils;

import android.app.AlertDialog;
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
        new AlertDialog.Builder(context)
                .setTitle("提示")
                .setMessage("请先登录后再进行操作")
                .setPositiveButton("去登录", (dialog, which) -> {
                    context.startActivity(new Intent(context, LoginActivity.class));
                })
                .setNegativeButton("取消", null)
                .show();
        return false;
    }
}
