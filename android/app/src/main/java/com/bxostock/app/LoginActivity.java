package com.bxostock.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.CaptchaResponse;
import com.bxostock.app.model.LoginRequest;
import com.bxostock.app.model.LoginResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.button.MaterialButtonToggleGroup;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {
    private EditText etEmail, etPassword, etCaptcha;
    private TextView tvError, tvCaptcha, tvHint, tvSub;
    private View captchaBox, progress;
    private Button btnLogin;
    private MaterialButtonToggleGroup toggle;
    private String captchaId = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (TokenStore.isLoggedIn(this)) {
            goHome();
            return;
        }
        setContentView(R.layout.activity_login);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);
        etCaptcha = findViewById(R.id.etCaptcha);
        tvError = findViewById(R.id.tvError);
        tvCaptcha = findViewById(R.id.tvCaptcha);
        tvHint = findViewById(R.id.tvHint);
        tvSub = findViewById(R.id.tvSub);
        captchaBox = findViewById(R.id.captchaBox);
        progress = findViewById(R.id.progress);
        btnLogin = findViewById(R.id.btnLogin);
        toggle = findViewById(R.id.toggleRole);

        toggle.check(R.id.btnAdmin);
        toggle.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            boolean staff = id == R.id.btnStaff;
            tvSub.setText(staff ? "Cashier or store team access" : "Owner or manager access");
            btnLogin.setText(staff ? "Sign in as Staff" : "Sign in as Admin");
            tvError.setVisibility(View.GONE);
        });

        btnLogin.setOnClickListener(v -> login());
        findViewById(R.id.btnNewCaptcha).setOnClickListener(v -> loadCaptcha());
    }

    private String selectedRole() {
        return toggle.getCheckedButtonId() == R.id.btnStaff ? "STAFF" : "ADMIN";
    }

    private void login() {
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (email.isEmpty() || password.isEmpty()) {
            showError("Enter your email and password.");
            return;
        }
        LoginRequest req = new LoginRequest(email, password);
        if (captchaId != null) {
            req.captchaId = captchaId;
            req.captchaAnswer = etCaptcha.getText().toString().trim();
        }
        setLoading(true);
        ApiClient.get(this).login(req).enqueue(new Callback<LoginResponse>() {
            @Override
            public void onResponse(Call<LoginResponse> call, Response<LoginResponse> r) {
                setLoading(false);
                if (r.isSuccessful() && r.body() != null) {
                    LoginResponse b = r.body();
                    if (!selectedRole().equals(b.role)) {
                        showError("This is " + ("ADMIN".equals(b.role) ? "an Admin" : "a Staff")
                                + " account. Choose the " + ("ADMIN".equals(b.role) ? "Admin" : "Staff") + " tab.");
                        etPassword.setText("");
                        return;
                    }
                    TokenStore.save(LoginActivity.this, b.token, b.name, b.email, b.role);
                    goHome();
                    return;
                }
                showError(ApiError.message(r));
                // 428 = captcha needed. A captcha can only be used once, so
                // after any failed try with a captcha we ask for a new one.
                if (r.code() == 428 || captchaId != null) loadCaptcha();
            }

            @Override
            public void onFailure(Call<LoginResponse> call, Throwable t) {
                setLoading(false);
                showError(ApiError.NETWORK);
            }
        });
    }

    private void loadCaptcha() {
        ApiClient.get(this).captcha().enqueue(new Callback<CaptchaResponse>() {
            @Override
            public void onResponse(Call<CaptchaResponse> call, Response<CaptchaResponse> r) {
                if (r.isSuccessful() && r.body() != null) {
                    captchaId = r.body().captchaId;
                    tvCaptcha.setText(r.body().question);
                    etCaptcha.setText("");
                    captchaBox.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<CaptchaResponse> call, Throwable t) {
                showError(ApiError.NETWORK);
            }
        });
    }

    private void setLoading(boolean on) {
        progress.setVisibility(on ? View.VISIBLE : View.GONE);
        tvHint.setVisibility(on ? View.VISIBLE : View.GONE);
        btnLogin.setEnabled(!on);
        if (on) tvError.setVisibility(View.GONE);
    }

    private void showError(String msg) {
        tvError.setText(msg);
        tvError.setVisibility(View.VISIBLE);
    }

    private void goHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}