package com.hongguo.theater.utils;

import android.content.Context;

import com.hongguo.theater.R;
import com.hongguo.theater.model.AdSkipStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class FormatUtils {

    public static String formatCount(long count) {
        if (count >= 100000000) {
            return String.format(Locale.CHINA, "%.1f亿", count / 100000000.0);
        } else if (count >= 10000) {
            return String.format(Locale.CHINA, "%.1fw", count / 10000.0);
        } else if (count >= 1000) {
            return String.format(Locale.CHINA, "%.1fk", count / 1000.0);
        }
        return String.valueOf(count);
    }

    public static String formatTimeAgo(String isoTime) {
        if (isoTime == null) return "";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA);
            Date date = sdf.parse(isoTime);
            if (date == null) return isoTime;

            long diff = System.currentTimeMillis() - date.getTime();
            long minutes = TimeUnit.MILLISECONDS.toMinutes(diff);
            long hours = TimeUnit.MILLISECONDS.toHours(diff);
            long days = TimeUnit.MILLISECONDS.toDays(diff);

            if (minutes < 1) return "刚刚";
            if (minutes < 60) return minutes + "分钟前";
            if (hours < 24) return hours + "小时前";
            if (days < 7) return days + "天前";
            if (days < 30) return (days / 7) + "周前";

            SimpleDateFormat out = new SimpleDateFormat("MM-dd", Locale.CHINA);
            return out.format(date);
        } catch (ParseException e) {
            return isoTime;
        }
    }

    /**
     * 免广告档位：将小时换算为「天」展示。先换算为 {@code hours/24}，再四舍五入保留 1 位小数；
     * 若结果为整数则不带小数（如 30 天、30.4 天）。
     */
    public static String formatAdSkipDurationDaysFromHours(Context ctx, int hours) {
        if (hours <= 0) {
            return ctx.getString(R.string.wallet_ad_skip_tier_days_int, 0);
        }
        BigDecimal days = BigDecimal.valueOf(hours)
                .divide(BigDecimal.valueOf(24), 10, RoundingMode.HALF_UP)
                .setScale(1, RoundingMode.HALF_UP);
        if (days.stripTrailingZeros().scale() <= 0) {
            return ctx.getString(R.string.wallet_ad_skip_tier_days_int, days.intValue());
        }
        return ctx.getString(R.string.wallet_ad_skip_tier_days_decimal, days.doubleValue());
    }

    /** 免广告档位主标题：时间包为「名 + 天」；加油包为「名 + 次数」。 */
    public static String formatAdSkipTierTitle(Context ctx, AdSkipStatus.Config c) {
        if (c == null) return "";
        if ("booster".equals(c.getPackageType())) {
            String name = c.getName();
            int n = c.getSkipCount() > 0 ? c.getSkipCount() : 0;
            if (name != null && !name.trim().isEmpty()) {
                return name.trim() + " · " + n + "次";
            }
            return n + "次";
        }
        String name = c.getName();
        String daysPart = formatAdSkipDurationDaysFromHours(ctx, c.getDurationHours());
        if (c.getSkipCount() > 0) {
            daysPart = daysPart + " · " + c.getSkipCount() + "次";
        }
        if (name != null && !name.trim().isEmpty()) {
            return name.trim() + " " + daysPart;
        }
        return daysPart;
    }
}
