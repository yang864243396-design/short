package com.hongguo.theater.utils;

import android.content.Context;
import android.content.SharedPreferences;

public class PrefsManager {

    private static final String PREFS_NAME = "hongguo_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";

    private static SharedPreferences prefs;
    private static Context appContext;

    public static void init(Context context) {
        Context app = context.getApplicationContext();
        appContext = app;
        prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public static String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public static void saveUserId(long userId) {
        long old = prefs.getLong(KEY_USER_ID, 0L);
        prefs.edit().putLong(KEY_USER_ID, userId).apply();
        if (appContext != null && old != userId) {
            AdSkipCache.ensureUser(appContext, userId);
        }
    }

    public static long getUserId() {
        return prefs.getLong(KEY_USER_ID, 0);
    }

    public static void saveUsername(String username) {
        prefs.edit().putString(KEY_USERNAME, username).apply();
    }

    public static String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public static boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public static void logout() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_USERNAME)
                .apply();
        EpisodeAdTempUnlock.clear();
        if (appContext != null) {
            AdSkipCache.clear(appContext);
        }
    }
}
