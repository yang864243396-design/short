package com.hongguo.theater.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hongguo.theater.R;
import com.hongguo.theater.model.Drama;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 刷剧 Feed：标题与简介之间的分类 chip行，仅展示分类，最多 {@link #MAX_CATEGORIES} 个。
 */
public final class FeedDramaTagsBinder {

    public static final int MAX_CATEGORIES = 4;

    private FeedDramaTagsBinder() {}

    public static void bind(@Nullable HorizontalScrollView scroll, @Nullable LinearLayout row,
            @Nullable Drama drama, Context context) {
        if (scroll == null || row == null || context == null) {
            return;
        }
        row.removeAllViews();
        if (drama == null) {
            scroll.setVisibility(View.GONE);
            return;
        }
        List<String> labels = collectLabels(drama);
        if (labels.isEmpty()) {
            scroll.setVisibility(View.GONE);
            return;
        }
        scroll.setVisibility(View.VISIBLE);
        scroll.scrollTo(0, 0);

        float d = context.getResources().getDisplayMetrics().density;
        int padH = (int) (8 * d);
        int padV = (int) (4 * d);
        int gap = (int) (6 * d);
        int textColor = ContextCompat.getColor(context, R.color.feed_drama_tag_text);

        for (String label : labels) {
            TextView tv = new TextView(context);
            tv.setText(label);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            tv.setTextColor(textColor);
            tv.setBackgroundResource(R.drawable.bg_feed_drama_tag);
            tv.setPadding(padH, padV, padH, padV);
            tv.setIncludeFontPadding(false);
            tv.setMaxLines(1);
            tv.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(gap);
            row.addView(tv, lp);
        }
    }

    /** 优先 category_list；否则按 category 逗号等拆分；去重；最多 {@link #MAX_CATEGORIES} 个 */
    static List<String> collectLabels(Drama drama) {
        List<String> fromApi = drama.getCategoryList();
        if (fromApi != null && !fromApi.isEmpty()) {
            return normalizeAndCap(fromApi);
        }
        if (TextUtils.isEmpty(drama.getCategory())) {
            return new ArrayList<>();
        }
        List<String> parts = new ArrayList<>();
        for (String part : drama.getCategory().split("[,，、;|]+")) {
            String t = part.trim();
            if (!t.isEmpty()) {
                parts.add(t);
            }
        }
        return normalizeAndCap(parts);
    }

    private static List<String> normalizeAndCap(List<String> raw) {
        Set<String> unique = new LinkedHashSet<>();
        for (String s : raw) {
            if (TextUtils.isEmpty(s)) {
                continue;
            }
            String t = s.trim();
            if (!t.isEmpty()) {
                unique.add(t);
            }
        }
        List<String> out = new ArrayList<>(unique);
        if (out.size() <= MAX_CATEGORIES) {
            return out;
        }
        return new ArrayList<>(out.subList(0, MAX_CATEGORIES));
    }
}
