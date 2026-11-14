package com.bxostock.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.ChangePasswordRequest;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Session;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.textfield.TextInputEditText;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {
    private TextInputEditText etCurrent, etNew, etConfirm;
    private TextView tvMessage;
    private View btn;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        ((TextView) v.findViewById(R.id.tvName)).setText(TokenStore.name(requireContext()));
        ((TextView) v.findViewById(R.id.tvEmail)).setText(TokenStore.email(requireContext()));
        ((TextView) v.findViewById(R.id.tvRole)).setText(TokenStore.role(requireContext()));
        etCurrent = v.findViewById(R.id.etCurrent);
        etNew = v.findViewById(R.id.etNew);
        etConfirm = v.findViewById(R.id.etConfirm);
        tvMessage = v.findViewById(R.id.tvMessage);
        btn = v.findViewById(R.id.btnChange);
        btn.setOnClickListener(x -> submit());
    }

    private void msg(String text, boolean error) {
        tvMessage.setTextColor(error ? 0xFFFF6B6B : 0xFF3DDC84);
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
    }

    private void submit() {
        String cur = etCurrent.getText().toString();
        String nw = etNew.getText().toString();
        String cf = etConfirm.getText().toString();
        if (cur.isEmpty()) { msg("Enter your current password.", true); return; }
        if (nw.length() < 8) { msg("The new password must be at least 8 characters.", true); return; }
        if (!nw.equals(cf)) { msg("The new passwords do not match.", true); return; }
        ChangePasswordRequest req = new ChangePasswordRequest();
        req.currentPassword = cur;
        req.newPassword = nw;
        btn.setEnabled(false);
        ApiClient.get(requireContext()).changePassword(req).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (!isAdded()) return;
                btn.setEnabled(true);
                if (r.isSuccessful()) {
                    etCurrent.setText("");
                    etNew.setText("");
                    etConfirm.setText("");
                    msg("Password changed. Use the new password next time you sign in.", false);
                } else if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                } else {
                    msg(ApiError.message(r), true);
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (!isAdded()) return;
                btn.setEnabled(true);
                msg(ApiError.NETWORK, true);
            }
        });
    }
}