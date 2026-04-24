package com.hongguo.theater.ui.auth;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.hongguo.theater.R;
import com.hongguo.theater.api.ApiClient;
import com.hongguo.theater.api.ApiErrorHelper;
import com.hongguo.theater.model.ApiResponse;
import com.hongguo.theater.utils.AdSkipCache;
import com.hongguo.theater.utils.PrefsManager;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    private EditText etUsername, etPassword, etNickname, etCode;
    private TextView btnSubmit, btnSwitchMode, tvModeTitle, btnSendCode;
    private View layoutRegisterCode;
    private boolean isLoginMode = true;

    private CountDownTimer sendCodeTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etNickname = findViewById(R.id.et_nickname);
        etCode = findViewById(R.id.et_code);
        btnSubmit = findViewById(R.id.btn_submit);
        btnSwitchMode = findViewById(R.id.btn_switch_mode);
        tvModeTitle = findViewById(R.id.tv_mode_title);
        btnSendCode = findViewById(R.id.btn_send_code);
        layoutRegisterCode = findViewById(R.id.layout_register_code);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        btnSubmit.setOnClickListener(v -> submit());
        btnSwitchMode.setOnClickListener(v -> toggleMode());
        btnSendCode.setOnClickListener(v -> sendRegisterCode());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sendCodeTimer != null) {
            sendCodeTimer.cancel();
        }
    }

    private void toggleMode() {
        isLoginMode = !isLoginMode;
        if (isLoginMode) {
            tvModeTitle.setText("登录账号");
            btnSubmit.setText(R.string.login);
            btnSwitchMode.setText("没有账号？立即注册");
            etNickname.setVisibility(View.GONE);
            layoutRegisterCode.setVisibility(View.GONE);
        } else {
            tvModeTitle.setText("注册新账号");
            btnSubmit.setText(R.string.register);
            btnSwitchMode.setText("已有账号？立即登录");
            etNickname.setVisibility(View.VISIBLE);
            layoutRegisterCode.setVisibility(View.VISIBLE);
        }
    }

    private void sendRegisterCode() {
        String email = etUsername.getText().toString().trim();
        if (email.isEmpty()) {
            Toast.makeText(this, "请先填写邮箱", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            Toast.makeText(this, "邮箱格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSendCode.setEnabled(false);

        Map<String, String> body = new HashMap<>();
        body.put("email", email);

        ApiClient.getService().sendRegisterCode(body).enqueue(new Callback<ApiResponse<Object>>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse<Object>> call,
                                   @NonNull Response<ApiResponse<Object>> response) {
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    Toast.makeText(LoginActivity.this, "验证码已发送至邮箱", Toast.LENGTH_SHORT).show();
                    startSendCodeCooldown();
                } else {
                    btnSendCode.setEnabled(true);
                    String msg = ApiErrorHelper.parseMessage(response, "发送失败");
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse<Object>> call, @NonNull Throwable t) {
                btnSendCode.setEnabled(true);
                Toast.makeText(LoginActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void startSendCodeCooldown() {
        if (sendCodeTimer != null) {
            sendCodeTimer.cancel();
        }
        sendCodeTimer = new CountDownTimer(60000, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                btnSendCode.setText((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                btnSendCode.setEnabled(true);
                btnSendCode.setText("获取验证码");
            }
        };
        sendCodeTimer.start();
    }

    private void submit() {
        String email = etUsername.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "请输入邮箱和密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            Toast.makeText(this, "邮箱格式不正确", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isLoginMode && password.length() < 6) {
            Toast.makeText(this, "密码至少6位", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isLoginMode) {
            String code = etCode.getText().toString().trim();
            if (code.length() < 4) {
                Toast.makeText(this, "请输入邮箱验证码", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        btnSubmit.setEnabled(false);

        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        if (!isLoginMode) {
            body.put("code", etCode.getText().toString().trim());
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

                    AdSkipCache.prefetchIfStale(LoginActivity.this);

                    setResult(RESULT_OK);
                    finish();
                } else {
                    String msg = ApiErrorHelper.parseMessage(response, "操作失败");
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
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
