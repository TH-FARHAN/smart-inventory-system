package com.bxostock.app;

import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.CreateUserRequest;
import com.bxostock.app.model.UserResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import com.bxostock.app.model.ResetPasswordRequest;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UsersFragment extends BaseListFragment<UserResponse> {
    @Override protected String searchHint() { return "Search name, email or role"; }
    @Override protected String actionLabel() { return "Add"; }
    @Override protected void onAction() { showAdd(); }

    @Override
    protected Call<List<UserResponse>> request(String kw) {
        return ApiClient.get(requireContext()).users();
    }

    @Override
    protected boolean matches(UserResponse u, String k) {
        if (k.isEmpty()) return true;
        return u.name.toLowerCase(Locale.ROOT).contains(k)
                || u.email.toLowerCase(Locale.ROOT).contains(k)
                || u.role.toLowerCase(Locale.ROOT).contains(k);
    }

    @Override
    protected Row toRow(UserResponse u) {
        Row r = new Row();
        r.title = u.name;
        r.sub = u.email;
        r.right = u.role;
        r.rightSub = u.active ? "Active" : "Inactive";
        r.warn = !u.active;
        return r;
    }

    private boolean isMe(UserResponse u) {
        return u.email.equalsIgnoreCase(TokenStore.email(requireContext()));
    }

    @Override
    protected void onItemClick(final UserResponse u) {
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(u.name)
                .setMessage("Email: " + u.email + "\nRole: " + u.role
                        + "\nStatus: " + (u.active ? "Active" : "Inactive")
                        + (isMe(u) ? "\n\nThis is your own account." : ""))
                .setNeutralButton("Close", null);
        if (!isMe(u)) {
            b.setNegativeButton("Reset password", (d, w) -> showReset(u));
            if (u.active) {
                b.setPositiveButton("Deactivate", (d, w) -> confirmDeactivate(u));
            } else {
                b.setPositiveButton("Activate", (d, w) -> toggle(u, true));
            }
        }
        b.show();
    }

    private void showReset(final UserResponse u) {
        final EditText e = new EditText(requireContext());
        e.setHint("New password (min 8 characters)");
        e.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        android.widget.FrameLayout box = new android.widget.FrameLayout(requireContext());
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(e);
        final AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Reset password for " + u.name)
                .setView(box)
                .setPositiveButton("Reset", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String pw = e.getText().toString();
            if (pw.length() < 8) { e.setError("At least 8 characters"); return; }
            ResetPasswordRequest req = new ResetPasswordRequest();
            req.newPassword = pw;
            ApiClient.get(requireContext()).resetPassword(u.id, req).enqueue(new Callback<ResponseBody>() {
                @Override
                public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                    if (!isAdded()) return;
                    if (r.isSuccessful()) {
                        d.dismiss();
                        Toast.makeText(requireContext(), "Password reset for " + u.name + ". Account unlocked.",
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<ResponseBody> c, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void confirmDeactivate(final UserResponse u) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Deactivate user")
                .setMessage("Deactivate " + u.name + "? They will be blocked from signing in right away.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Deactivate", (d, w) -> toggle(u, false))
                .show();
    }

    private void toggle(final UserResponse u, final boolean activate) {
        Call<UserResponse> call = activate
                ? ApiClient.get(requireContext()).activateUser(u.id)
                : ApiClient.get(requireContext()).deactivateUser(u.id);
        call.enqueue(new Callback<UserResponse>() {
            @Override
            public void onResponse(Call<UserResponse> c, Response<UserResponse> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) {
                    Toast.makeText(requireContext(), u.name + (activate ? " activated and unlocked." : " deactivated."),
                            Toast.LENGTH_SHORT).show();
                    reload();
                } else {
                    Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<UserResponse> c, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showAdd() {
        View v = getLayoutInflater().inflate(R.layout.dialog_user, null);
        final EditText n = v.findViewById(R.id.uName);
        final EditText e = v.findViewById(R.id.uEmail);
        final EditText p = v.findViewById(R.id.uPassword);
        final RadioGroup rg = v.findViewById(R.id.uRole);
        final AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add user")
                .setView(v)
                .setPositiveButton("Create user", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = n.getText().toString().trim();
            String email = e.getText().toString().trim();
            String pw = p.getText().toString();
            if (name.isEmpty()) { n.setError("Required"); return; }
            if (email.isEmpty() || !email.contains("@")) { e.setError("Enter a valid email"); return; }
            if (pw.length() < 8) { p.setError("At least 8 characters"); return; }
            CreateUserRequest req = new CreateUserRequest();
            req.name = name;
            req.email = email;
            req.password = pw;
            req.role = rg.getCheckedRadioButtonId() == R.id.uAdmin ? "ADMIN" : "STAFF";
            ApiClient.get(requireContext()).addUser(req).enqueue(new Callback<UserResponse>() {
                @Override
                public void onResponse(Call<UserResponse> c, Response<UserResponse> r) {
                    if (!isAdded()) return;
                    if (r.isSuccessful()) {
                        d.dismiss();
                        Toast.makeText(requireContext(), "User created.", Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<UserResponse> c, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}