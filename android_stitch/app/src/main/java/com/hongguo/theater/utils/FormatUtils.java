package com.hongguo.theater.utils;

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
}
