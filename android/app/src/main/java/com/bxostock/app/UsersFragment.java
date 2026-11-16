package com.bxostock.app;

import com.bxostock.app.util.BxoDialog;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.CreateUserRequest;
import com.bxostock.app.model.UserResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import okhttp3.ResponseBody;
import com.bxostock.app.model.ResetPasswordRequest;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class UsersFragment extends BaseListFragment<UserResponse> {
    private int status = 0; // 0 = all, 1 = active, 2 = inactive
    private Chip chipAll, chipActive, chipInactive;
    private Map<Long, String> photos = new HashMap<>();
    private List<UserResponse> lastItems = new ArrayList<>();
    private long photosAt = 0;

    @Override protected String searchHint() { return "Search name, email or role"; }
    @Override protected String actionLabel() { return "Add"; }
    @Override protected void onAction() { showAdd(); }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        View rv = v.findViewById(R.id.recycler);
        // if the layout is not what we expect, skip the chips: the list still works
        if (rv == null || !(rv.getParent() instanceof LinearLayout)) return;
        LinearLayout parent = (LinearLayout) rv.getParent();

        ChipGroup group = new ChipGroup(requireContext());
        group.setSingleSelection(true);
        group.setSelectionRequired(true);
        chipAll = makeChip("All");
        chipActive = makeChip("Active");
        chipInactive = makeChip("Inactive");
        group.addView(chipAll);
        group.addView(chipActive);
        group.addView(chipInactive);
        chipAll.setChecked(true);
        group.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            int next = id == chipActive.getId() ? 1 : id == chipInactive.getId() ? 2 : 0;
            if (next != status) {
                status = next;
                reload();
            }
        });

        int m = (int) (4 * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, m, 0, m);
        parent.addView(group, parent.indexOfChild(rv), lp);
    }

    private Chip makeChip(String text) {
        Chip c = new Chip(requireContext());
        c.setId(View.generateViewId());
        c.setCheckable(true);
        c.setText(text);
        return c;
    }

    // loads the list as usual, and refreshes the photos at most every 30 seconds
    @Override
    protected void reload() {
        super.reload();
        if (!isAdded()) return;
        long now = System.currentTimeMillis();
        if (photosAt != 0 && now - photosAt < 30_000) return;
        photosAt = now;
        ApiClient.get(requireContext()).userPhotos().enqueue(new Callback<Map<Long, String>>() {
            @Override
            public void onResponse(Call<Map<Long, String>> c, Response<Map<Long, String>> r) {
                if (!isAdded() || getView() == null || !r.isSuccessful() || r.body() == null) return;
                photos = r.body();
                rebuild();
            }

            @Override
            public void onFailure(Call<Map<Long, String>> c, Throwable t) { }
        });
    }

    private void rebuild() {
        if (adapter == null || lastItems.isEmpty()) return;
        List<Row> rows = new ArrayList<>();
        for (UserResponse u : lastItems) {
            Row row = toRow(u);
            row.tag = u;
            rows.add(row);
        }
        adapter.set(rows);
    }

    @Override
    protected Call<List<UserResponse>> request(String kw) {
        return ApiClient.get(requireContext()).users();
    }

    // the search text filters here; the status chips filter in sort() so the counts stay right
    @Override
    protected boolean matches(UserResponse u, String k) {
        if (k.isEmpty()) return true;
        return u.name.toLowerCase(Locale.ROOT).contains(k)
                || u.email.toLowerCase(Locale.ROOT).contains(k)
                || u.role.toLowerCase(Locale.ROOT).contains(k);
    }

    @Override
    protected void sort(List<UserResponse> items) {
        int a = 0, i = 0;
        for (UserResponse u : items) {
            if (u.active) a++;
            else i++;
        }
        if (chipAll != null) {
            chipAll.setText("All (" + (a + i) + ")");
            chipActive.setText("Active (" + a + ")");
            chipInactive.setText("Inactive (" + i + ")");
        }
        if (status == 1) items.removeIf(u -> !u.active);
        else if (status == 2) items.removeIf(u -> u.active);
        lastItems = new ArrayList<>(items);
    }

    @Override
    protected Row toRow(UserResponse u) {
        Row r = new Row();
        r.title = u.name;
        r.sub = u.email;
        r.right = u.role;
        r.rightSub = u.active ? "Active" : "Inactive";
        r.warn = !u.active;
        r.avatarName = u.name;
        r.photo = photos.get(u.id);
        return r;
    }

    private boolean isMe(UserResponse u) {
        return u.email.equalsIgnoreCase(TokenStore.email(requireContext()));
    }

    @Override
    protected void onItemClick(final UserResponse u) {
        MaterialAlertDialogBuilder b = new BxoDialog(requireContext())
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
        final AlertDialog d = new BxoDialog(requireContext())
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
        new BxoDialog(requireContext())
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
        final AlertDialog d = new BxoDialog(requireContext())
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