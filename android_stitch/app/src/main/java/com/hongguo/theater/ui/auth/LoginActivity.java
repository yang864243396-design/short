package com.hongguo.theater.ui.auth;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.utils.PrefsManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etNickname;
    private TextView btnSubmit, btnSwitchMode, tvModeTitle;
    private boolean isLoginMode = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etNickname = findViewById(R.id.et_nickname);
        btnSubmit = findViewById(R.id.btn_submit);
        btnSwitchMode = findViewById(R.id.btn_switch_mode);
        tvModeTitle = findViewById(R.id.tv_mode_title);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v -> submit());
        btnSwitchMode.setOnClickListener(v -> toggleMode());
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            tvModeTitle.setText("登录账号");
            btnSubmit.setText(R.string.login);
            btnSwitchMode.setText("没有账号？立即注册");
            etNickname.setVisibility(View.GONE);
        } else {
            tvModeTitle.setText("注册新账号");
            btnSubmit.setText(R.string.register);
            btnSwitchMode.setText("已有账号？立即登录");
            etNickname.setVisibility(View.VISIBLE);
        }
    }

    private void submit() {
        String username = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isLoginMode && password.length() < 6) {
            Toast.makeText(this, "密码至少6位", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);

        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);
        if (!isLoginMode) {
            String nickname = etNickname.getText().toString().trim();
            if (!nickname.isEmpty()) body.put("nickname", nickname);
        }

        Call<ApiResponse<Map<String, Object>>> call = isLoginMode
                ? ApiClient.getService().login(body)
                : ApiClient.getService().register(body);

        call.enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Map<String, Object>>> c,
                                   @NonNull Response<ApiResponse<Map<String, Object>>> response) {
                btnSubmit.setEnabled(true);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Map<String, Object> data = response.body().getData();
                    String token = (String) data.get("token");
                    PrefsManager.saveToken(token);

                    @SuppressWarnings("unchecked")
                    Map<String, Object> userMap = (Map<String, Object>) data.get("user");
                    if (userMap != null) {
                        double uid = (double) userMap.get("id");
                        PrefsManager.saveUserId((long) uid);
                        String nick = (String) userMap.get("nickname");
                        if (nick != null) PrefsManager.saveUsername(nick);
                    }

                    setResult(RESULT_OK);
                    finish();
                } else {
                    String msg = "操作失败";
                    if (response.body() != null && response.body().getMessage() != null) {
                        msg = response.body().getMessage();
                    }
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Map<String, Object>>> c, @NonNull Throwable t) {
                btnSubmit.setEnabled(true);
                Toast.makeText(LoginActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
