package com.bxostock.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.bxostock.app.util.NetworkWatcher;

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
    private TextView tvForgot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (TokenStore.isLoggedIn(this)) {
            goHome();
            return;
        }
        setContentView(R.layout.activity_login);
        NetworkWatcher.attach(this);
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
            updateForgot(staff);
            tvError.setVisibility(View.GONE);
        });

        btnLogin.setOnClickListener(v -> login());
        tvForgot = findViewById(R.id.tvForgot);
        updateForgot(false);
        tvForgot.setOnClickListener(v -> {
            if ("ADMIN".equals(selectedRole())) forgotStep1();
        });
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

    private void updateForgot(boolean staff) {
        tvForgot.setText(staff ? "Staff: ask your admin to reset your password." : "Forgot password?");
        tvForgot.setTextColor(staff ? 0xFF9A9AA3 : 0xFFFF4D1C);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show();
    }

    // step 1: ask for the admin email and send the 6-digit code
    private void forgotStep1() {
        final EditText in = new EditText(this);
        in.setHint("Admin email");
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        in.setText(etEmail.getText().toString().trim());
        android.widget.FrameLayout box = new android.widget.FrameLayout(this);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(in);
        final androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Forgot password")
                .setMessage("Administrator accounts only. We will email you a 6-digit code.")
                .setView(box)
                .setPositiveButton("Send code", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        final Button ok = d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(x -> {
            final String email = in.getText().toString().trim();
            if (email.isEmpty() || !email.contains("@")) {
                in.setError("Enter a valid email");
                return;
            }
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("email", email);
            ok.setEnabled(false);
            ApiClient.get(this).forgotPassword(body).enqueue(new Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(Call<okhttp3.ResponseBody> c, Response<okhttp3.ResponseBody> r) {
                    ok.setEnabled(true);
                    if (r.isSuccessful()) {
                        d.dismiss();
                        toast("If this is an administrator account, a code was sent. Check the spam folder too.");
                        forgotStep2(email);
                    } else {
                        toast(ApiError.message(r));
                    }
                }

                @Override
                public void onFailure(Call<okhttp3.ResponseBody> c, Throwable t) {
                    ok.setEnabled(true);
                    toast(ApiError.NETWORK);
                }
            });
        });
    }

    // step 2: enter the code and choose a new password
    private void forgotStep2(final String email) {
        final EditText code = new EditText(this);
        code.setHint("6-digit code");
        code.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(6)});
        final EditText pw = new EditText(this);
        pw.setHint("New password (min 8 characters)");
        pw.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        final EditText pw2 = new EditText(this);
        pw2.setHint("Confirm new password");
        pw2.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        android.widget.LinearLayout box = new android.widget.LinearLayout(this);
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        box.addView(code);
        box.addView(pw);
        box.addView(pw2);
        final androidx.appcompat.app.AlertDialog d = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Enter the code")
                .setMessage("Code sent to " + email + ". It works for 10 minutes.")
                .setView(box)
                .setPositiveButton("Reset password", null)
                .setNeutralButton("Resend code", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        final Button ok = d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE);
        ok.setOnClickListener(x -> {
            String otp = code.getText().toString().trim();
            String p1 = pw.getText().toString();
            String p2 = pw2.getText().toString();
            if (otp.length() != 6) { code.setError("Enter the 6-digit code"); return; }
            if (p1.length() < 8) { pw.setError("At least 8 characters"); return; }
            if (!p1.equals(p2)) { pw2.setError("The passwords do not match"); return; }
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("email", email);
            body.put("otp", otp);
            body.put("newPassword", p1);
            ok.setEnabled(false);
            ApiClient.get(this).resetWithCode(body).enqueue(new Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(Call<okhttp3.ResponseBody> c, Response<okhttp3.ResponseBody> r) {
                    ok.setEnabled(true);
                    if (r.isSuccessful()) {
                        d.dismiss();
                        etEmail.setText(email);
                        etPassword.setText("");
                        toast("Password changed. You can sign in now.");
                    } else {
                        toast(ApiError.message(r));
                    }
                }

                @Override
                public void onFailure(Call<okhttp3.ResponseBody> c, Throwable t) {
                    ok.setEnabled(true);
                    toast(ApiError.NETWORK);
                }
            });
        });
        d.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL).setOnClickListener(x -> {
            java.util.Map<String, String> body = new java.util.HashMap<>();
            body.put("email", email);
            ApiClient.get(this).forgotPassword(body).enqueue(new Callback<okhttp3.ResponseBody>() {
                @Override
                public void onResponse(Call<okhttp3.ResponseBody> c, Response<okhttp3.ResponseBody> r) {
                    toast(r.isSuccessful() ? "If this is an admin account, a new code was sent." : ApiError.message(r));
                }

                @Override
                public void onFailure(Call<okhttp3.ResponseBody> c, Throwable t) {
                    toast(ApiError.NETWORK);
                }
            });
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