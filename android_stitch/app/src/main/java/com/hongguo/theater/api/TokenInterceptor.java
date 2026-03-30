package com.hongguo.theater.api;

import androidx.annotation.NonNull;

import com.hongguo.theater.utils.PrefsManager;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class TokenInterceptor implements Interceptor {

    @NonNull
    @Override
    public Response intercept(@NonNull Chain chain) throws IOException {
        Request original = chain.request();
        String token = PrefsManager.getToken();

        if (token != null && !token.isEmpty()) {
            Request authorized = original.newBuilder()
                    .header("Authorization", "Bearer " + token)
                    .build();
            return chain.proceed(authorized);
        }

        return chain.proceed(original);
    }
}
