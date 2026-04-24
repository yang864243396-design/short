package com.hongguo.theater.utils;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.BackgroundColorSpan;
import android.text.style.ClickableSpan;
import android.text.style.StyleSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.hongguo.theater.R;

/**
 * 简介首行末尾内联「…展开」，展开后全文末尾「收起」。与 {@link com.hongguo.theater.ui.player.PlayerActivity}、Feed 条共用。
 */
public final class DescriptionInlineExpandHelper {

    private static final int WIDTH_RETRY_MAX = 16;
    /** 触控留白：略扩大可点区域（ux-guidelines 建议避免过小热区） */
    private static final String TAP_PAD = "\u00A0";

    private DescriptionInlineExpandHelper() {}

    private static void styleActionLinkRange(SpannableString ss, int spanStart, int spanEnd, TextView tv,
            Runnable onClick) {
        int bg = ContextCompat.getColor(tv.getContext(), R.color.desc_inline_action_bg);
        int fg = ContextCompat.getColor(tv.getContext(), R.color.desc_inline_action_text);
        int glow = ContextCompat.getColor(tv.getContext(), R.color.desc_inline_action_shadow);
        ss.setSpan(new StyleSpan(Typeface.BOLD), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new BackgroundColorSpan(bg), spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ss.setSpan(new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                if (onClick != null) onClick.run();
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(false);
                ds.setColor(fg);
                ds.setShadowLayer(5f, 0f, 0f, glow);
            }
        }, spanStart, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /**
     * 收起态：测量宽度未就绪时会 post 重试。
     */
    public static void applyCollapsedFirstLineWithRetry(TextView tv, String fullText, Runnable onExpand) {
        if (tv == null) return;
        applyCollapsedFirstLine(tv, fullText, onExpand, new int[]{0});
    }

    private static void applyCollapsedFirstLine(TextView tv, String fullText, Runnable onExpand, int[] retry) {
        try {
            if (TextUtils.isEmpty(fullText)) {
                tv.setText("");
                tv.setMovementMethod(null);
                return;
            }
            tv.setMaxLines(1);
            tv.setEllipsize(null);
            int width = tv.getWidth() - tv.getPaddingLeft() - tv.getPaddingRight();
            if (width <= 0) {
                if (retry[0]++ < WIDTH_RETRY_MAX) {
                    tv.post(() -> applyCollapsedFirstLine(tv, fullText, onExpand, retry));
                }
                return;
            }
            TextPaint paint = tv.getPaint();
            String expand = tv.getContext().getString(R.string.player_desc_expand);
            String dots = "\u2026";
            String suffix = dots + TAP_PAD + expand + TAP_PAD;
            float suffixW = paint.measureText(suffix);
            float avail = width - suffixW;
            if (avail < 1f) {
                avail = width * 0.45f;
            }
            float totalW = Layout.getDesiredWidth(fullText, 0, fullText.length(), paint);
            if (totalW <= width) {
                tv.setText(fullText);
                tv.setMovementMethod(null);
                return;
            }
            int lo = 0;
            int hi = fullText.length();
            while (lo < hi) {
                int mid = (lo + hi + 1) / 2;
                float w = Layout.getDesiredWidth(fullText, 0, mid, paint);
                if (w <= avail) {
                    lo = mid;
                } else {
                    hi = mid - 1;
                }
            }
            int cut = Math.max(1, lo);
            String visible = fullText.substring(0, cut);
            SpannableString ss = new SpannableString(visible + suffix);
            int spanStart = visible.length() + dots.length();
            int spanEnd = ss.length();
            styleActionLinkRange(ss, spanStart, spanEnd, tv, onExpand);
            tv.setText(ss);
            tv.setMovementMethod(LinkMovementMethod.getInstance());
            tv.setHighlightColor(Color.TRANSPARENT);
        } catch (RuntimeException e) {
            tv.setText(fullText);
            tv.setMovementMethod(null);
        }
    }

    public static void applyExpandedWithCollapse(TextView tv, String fullText, Runnable onCollapse) {
        if (tv == null || TextUtils.isEmpty(fullText)) return;
        String sep = "  ";
        String collapse = tv.getContext().getString(R.string.player_desc_collapse);
        SpannableString ss = new SpannableString(fullText + sep + TAP_PAD + collapse + TAP_PAD);
        int spanStart = fullText.length() + sep.length();
        int spanEnd = ss.length();
        styleActionLinkRange(ss, spanStart, spanEnd, tv, onCollapse);
        tv.setText(ss);
        tv.setMaxLines(Integer.MAX_VALUE);
        tv.setEllipsize(null);
        tv.setMovementMethod(LinkMovementMethod.getInstance());
        tv.setHighlightColor(Color.TRANSPARENT);
    }
}
