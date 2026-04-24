package com.hongguo.theater.utils;

import android.content.Context;
import android.content.Intent;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.hongguo.theater.R;
import com.hongguo.theater.model.Drama;
import com.hongguo.theater.ui.ranking.RankingActivity;

/** 刷剧 Feed、全屏看剧页共用的榜单标签 */
public final class RankingBadgeUiHelper {

    private RankingBadgeUiHelper() {}

    /** 仅展示标签，点击不跳转（等同 {@code bind(..., false)}） */
    public static void bind(@Nullable TextView tv, @Nullable Drama drama, Context context) {
        bind(tv, drama, context, false);
    }

    /**
     * @param openRankingOnClick true：点击标签进入 {@link RankingActivity} 并选中对应榜（与 Feed 一致）
     */
    public static void bind(@Nullable TextView tv, @Nullable Drama drama, Context context,
            boolean openRankingOnClick) {
        if (tv == null || context == null) {
            return;
        }
        try {
            if (drama == null) {
                clearBadge(tv);
                return;
            }
            Drama.DramaRankingInfo info = drama.getRanking();
            if (info == null || info.getRank() <= 0 || TextUtils.isEmpty(info.getList())) {
                clearBadge(tv);
                return;
            }
            String listKey = info.getList();
            int boardStrId;
            switch (listKey) {
                case "hot":
                    boardStrId = R.string.ranking_hot;
                    break;
                case "rising":
                    boardStrId = R.string.ranking_rising;
                    break;
                case "rating":
                    boardStrId = R.string.ranking_rating;
                    break;
                default:
                    clearBadge(tv);
                    return;
            }
            String board = context.getString(boardStrId);
            String full = context.getString(R.string.ranking_badge_player_format, board, info.getRank());
            SpannableString ss = new SpannableString(full);
            int accentLen = Math.min(board.length(), full.length());
            if (accentLen > 0) {
                int accent = ContextCompat.getColor(context, R.color.player_ranking_chip_accent);
                ss.setSpan(new ForegroundColorSpan(accent), 0, accentLen, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            tv.setText(ss);
            tv.setVisibility(View.VISIBLE);
            if (openRankingOnClick) {
                tv.setClickable(true);
                final String typeKey = listKey;
                tv.setOnClickListener(v -> {
                    Intent intent = new Intent(context, RankingActivity.class);
                    intent.putExtra(RankingActivity.EXTRA_INITIAL_TYPE, typeKey);
                    context.startActivity(intent);
                });
            } else {
                tv.setOnClickListener(null);
                tv.setClickable(false);
            }
        } catch (RuntimeException e) {
            android.util.Log.e("RankingBadgeUi", "bind", e);
            clearBadge(tv);
        }
    }

    private static void clearBadge(TextView tv) {
        tv.setText("");
        tv.setOnClickListener(null);
        tv.setClickable(false);
        tv.setVisibility(View.GONE);
    }
}
