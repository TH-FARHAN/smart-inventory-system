package com.bxostock.app;

import com.bxostock.app.util.BxoDialog;

import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.Supplier;
import com.bxostock.app.model.SupplierRequest;
import com.bxostock.app.util.ApiError;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SuppliersFragment extends BaseListFragment<Supplier> {
    @Override protected String searchHint() { return "Search suppliers"; }
    @Override protected String actionLabel() { return "Add"; }
    @Override protected void onAction() { showForm(null); }

    @Override
    protected Call<List<Supplier>> request(String kw) {
        return ApiClient.get(requireContext()).suppliers();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    @Override
    protected boolean matches(Supplier s, String k) {
        if (k.isEmpty()) return true;
        return (nz(s.name) + " " + nz(s.phone) + " " + nz(s.email)).toLowerCase(Locale.ROOT).contains(k);
    }

    @Override
    protected Row toRow(Supplier s) {
        Row r = new Row();
        r.title = s.name;
        r.sub = nz(s.phone).isEmpty() ? (nz(s.email).isEmpty() ? "No contact details" : s.email)
                : s.phone + (nz(s.email).isEmpty() ? "" : " \u00B7 " + s.email);
        return r;
    }

    @Override
    protected void onItemClick(final Supplier s) {
        new BxoDialog(requireContext())
                .setTitle(s.name)
                .setMessage("Phone: " + dash(s.phone) + "\nEmail: " + dash(s.email) + "\nAddress: " + dash(s.address))
                .setPositiveButton("Close", null)
                .setNegativeButton("Edit", (d, w) -> showForm(s))
                .show();
    }

    private static String dash(String s) { return nz(s).isEmpty() ? "-" : s; }

    private static String orNull(EditText e) {
        String s = e.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    private void showForm(final Supplier existing) {
        View v = getLayoutInflater().inflate(R.layout.dialog_customer, null);
        final EditText n = v.findViewById(R.id.dName);
        final EditText p = v.findViewById(R.id.dPhone);
        final EditText e = v.findViewById(R.id.dEmail);
        final EditText a = v.findViewById(R.id.dAddress);
        if (existing != null) {
            n.setText(existing.name);
            p.setText(existing.phone);
            e.setText(existing.email);
            a.setText(existing.address);
        }
        final AlertDialog d = new BxoDialog(requireContext())
                .setTitle(existing == null ? "Add supplier" : "Edit supplier")
                .setView(v)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (n.getText().toString().trim().isEmpty()) { n.setError("Name is required"); return; }
            SupplierRequest req = new SupplierRequest();
            req.name = n.getText().toString().trim();
            req.phone = orNull(p);
            req.email = orNull(e);
            req.address = orNull(a);
            Call<Supplier> call = existing == null
                    ? ApiClient.get(requireContext()).addSupplier(req)
                    : ApiClient.get(requireContext()).updateSupplier(existing.id, req);
            call.enqueue(new Callback<Supplier>() {
                @Override
                public void onResponse(Call<Supplier> c, Response<Supplier> r) {
                    if (!isAdded()) return;
                    if (r.isSuccessful()) {
                        d.dismiss();
                        Toast.makeText(requireContext(), "Supplier saved.", Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        String m = r.code() == 409 ? "This supplier already exists." : ApiError.message(r);
                        Toast.makeText(requireContext(), m, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Supplier> c, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}