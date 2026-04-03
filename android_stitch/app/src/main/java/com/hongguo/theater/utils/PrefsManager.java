package com.hongguo.theater.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class PrefsManager {

    private static final String PREFS_NAME = "hongguo_prefs";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_UNLOCKED_PREFIX = "unlocked_episodes_";

    private static SharedPreferences prefs;

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public static String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public static void saveUserId(long userId) {
        prefs.edit().putLong(KEY_USER_ID, userId).apply();
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
    }

    private static String getUnlockedKey() {
        long uid = getUserId();
        return KEY_UNLOCKED_PREFIX + uid;
    }

    public static boolean isEpisodeUnlocked(long episodeId) {
        Set<String> set = prefs.getStringSet(getUnlockedKey(), null);
        return set != null && set.contains(String.valueOf(episodeId));
    }

    public static void unlockEpisode(long episodeId) {
        String key = getUnlockedKey();
        Set<String> set = new HashSet<>(prefs.getStringSet(key, new HashSet<>()));
        set.add(String.valueOf(episodeId));
        prefs.edit().putStringSet(key, set).apply();
    }
}
