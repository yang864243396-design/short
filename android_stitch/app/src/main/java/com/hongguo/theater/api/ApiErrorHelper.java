package com.hongguo.theater.api;

import com.google.gson.Gson;
import com.hongguo.theater.model.ApiResponse;

import retrofit2.Response;

/**
 * Retrofit 在 HTTP 4xx/5xx 时 {@link Response#body()} 多为 null，需从 {@link Response#errorBody()} 解析 JSON 里的 message。
 */
public final class ApiErrorHelper {

    private static final Gson GSON = new Gson();

    private ApiErrorHelper() {}

    public static String parseMessage(Response<?> response, String fallback) {
        if (response.body() instanceof ApiResponse) {
            ApiResponse<?> r = (ApiResponse<?>) response.body();
            if (r.getMessage() != null && !r.getMessage().isEmpty()) {
                return r.getMessage();
            }
        }
        if (response.errorBody() != null) {
            try {
                String s = response.errorBody().string();
                ApiResponse<?> r = GSON.fromJson(s, ApiResponse.class);
                if (r != null && r.getMessage() != null && !r.getMessage().isEmpty()) {
                    return r.getMessage();
                }
            } catch (Exception ignored) {
            }
        }
        return fallback;
    }
}
