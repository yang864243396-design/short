package com.hongguo.theater.utils;

import com.hongguo.theater.BuildConfig;

/** 统一把后台返回的图片路径转成可对 Glide 加载的绝对 URL */
public final class ImageUrlUtils {

    private ImageUrlUtils() {}

    public static String resolve(String path) {
        if (path == null) return null;
        String p = path.trim();
        if (p.isEmpty()) return null;
        if (p.startsWith("http://") || p.startsWith("https://")) {
            return p;
        }
        if (p.startsWith("//")) {
            String base = BuildConfig.BASE_URL;
            if (base.startsWith("https")) {
                return "https:" + p;
            }
            return "http:" + p;
        }
        String base = BuildConfig.BASE_URL.replace("/api/v1/", "").trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return base + p;
    }
}
