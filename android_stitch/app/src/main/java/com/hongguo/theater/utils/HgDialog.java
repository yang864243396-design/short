package com.hongguo.theater.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.hongguo.theater.R;

/**
 * 红果统一弹窗：确认（双按钮，主上次下）与告知（单按钮）。
 */
public final class HgDialog {

    public interface Listener {
        void onClick(@NonNull AlertDialog dialog);
    }

    private HgDialog() {
    }

    /**
     * 确认弹窗：上方为主按钮（如「确定」），下方为次按钮（如「取消」）。
     */
    public static AlertDialog showConfirm(
            @NonNull Context context,
            @Nullable CharSequence title,
            @NonNull CharSequence message,
            @NonNull CharSequence primaryText,
            @Nullable Listener onPrimary,
            @NonNull CharSequence secondaryText,
            @Nullable Listener onSecondary,
            boolean cancelable,
            @Nullable DialogInterface.OnDismissListener onDismiss) {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_hg_sheet, null, false);
        TextView tvTitle = root.findViewById(R.id.hg_dialog_title);
        TextView tvMessage = root.findViewById(R.id.hg_dialog_message);
        TextView btnPrimary = root.findViewById(R.id.hg_dialog_btn_primary);
        TextView btnSecondary = root.findViewById(R.id.hg_dialog_btn_secondary);

        if (title == null || title.toString().trim().isEmpty()) {
            tvTitle.setVisibility(View.GONE);
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) tvMessage.getLayoutParams();
            mlp.topMargin = 0;
            tvMessage.setLayoutParams(mlp);
        } else {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
        }
        tvMessage.setText(message);

        btnPrimary.setText(primaryText);
        btnSecondary.setVisibility(View.VISIBLE);
        btnSecondary.setText(secondaryText);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(root).setCancelable(cancelable).create();
        if (onDismiss != null) {
            dialog.setOnDismissListener(onDismiss);
        }

        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onPrimary != null) {
                onPrimary.onClick(dialog);
            }
        });
        btnSecondary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onSecondary != null) {
                onSecondary.onClick(dialog);
            }
        });

        dialog.show();
        applyDialogWindow(dialog, context);
        return dialog;
    }

    /**
     * 告知弹窗：仅一个主按钮（如「我知道了」）。
     */
    public static AlertDialog showInform(
            @NonNull Context context,
            @Nullable CharSequence title,
            @NonNull CharSequence message,
            @NonNull CharSequence buttonText,
            @Nullable Listener onButton,
            boolean cancelable,
            @Nullable DialogInterface.OnDismissListener onDismiss) {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_hg_sheet, null, false);
        TextView tvTitle = root.findViewById(R.id.hg_dialog_title);
        TextView tvMessage = root.findViewById(R.id.hg_dialog_message);
        TextView btnPrimary = root.findViewById(R.id.hg_dialog_btn_primary);
        TextView btnSecondary = root.findViewById(R.id.hg_dialog_btn_secondary);

        btnSecondary.setVisibility(View.GONE);

        if (title == null || title.toString().trim().isEmpty()) {
            tvTitle.setVisibility(View.GONE);
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) tvMessage.getLayoutParams();
            mlp.topMargin = 0;
            tvMessage.setLayoutParams(mlp);
        } else {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
        }
        tvMessage.setText(message);
        btnPrimary.setText(buttonText);
        /* 告知：主按钮为品牌橙底 + 白字（与确认弹窗的珊瑚底+深字区分） */
        btnPrimary.setTextColor(ContextCompat.getColor(context, R.color.white));
        btnPrimary.setBackgroundResource(R.drawable.ripple_hg_dialog_btn_inform);

        AlertDialog dialog = new AlertDialog.Builder(context).setView(root).setCancelable(cancelable).create();
        if (onDismiss != null) {
            dialog.setOnDismissListener(onDismiss);
        }

        btnPrimary.setOnClickListener(v -> {
            dialog.dismiss();
            if (onButton != null) {
                onButton.onClick(dialog);
            }
        });

        dialog.show();
        applyDialogWindow(dialog, context);
        return dialog;
    }

    /**
     * 提交中等不可取消的 loading，与 {@link #showConfirm} 同款窗口样式（红果圆角白底面板）。
     */
    public static AlertDialog showLoading(
            @NonNull Context context,
            @NonNull CharSequence message) {
        View root = LayoutInflater.from(context).inflate(R.layout.dialog_hg_loading, null, false);
        TextView tvMessage = root.findViewById(R.id.hg_loading_message);
        tvMessage.setText(message);
        AlertDialog dialog = new AlertDialog.Builder(context).setView(root).setCancelable(false).create();
        dialog.show();
        applyDialogWindow(dialog, context);
        return dialog;
    }

    private static void applyDialogWindow(AlertDialog dialog, Context context) {
        Window w = dialog.getWindow();
        if (w == null) {
            return;
        }
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        int pad = (int) (24 * dm.density);
        w.setLayout(dm.widthPixels - pad * 2, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
