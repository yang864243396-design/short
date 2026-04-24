package com.hongguo.theater.ui.home;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.hongguo.theater.R;
import com.hongguo.theater.model.Drama;

import java.util.List;

/**
 * 剧集卡片上「分类」多标签行：最多 3 个独立标签，与热度行配合由布局将热度置右。
 */
public final class DramaCardTagsHelper {

    private DramaCardTagsHelper() {}

    public static void bindTags(LinearLayout container, Context context, Drama drama) {
        container.removeAllViews();
        if (drama == null) return;
        List<String> labels = drama.getCategoryLabelsForDisplay();
        if (labels.isEmpty()) return;

        int gap = context.getResources().getDimensionPixelSize(R.dimen.spacing_sm);
        float captionPx = context.getResources().getDimension(R.dimen.text_caption);
        int textColor = ContextCompat.getColor(context, R.color.primary_light);

        for (int i = 0; i < labels.size(); i++) {
            TextView tv = new TextView(context);
            tv.setText(labels.get(i));
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, captionPx);
            tv.setTextColor(textColor);
            tv.setBackgroundResource(R.drawable.bg_tag);
            tv.setMaxLines(1);
            tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
            tv.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                lp.setMarginStart(gap);
            }
            container.addView(tv, lp);
        }
    }
}
