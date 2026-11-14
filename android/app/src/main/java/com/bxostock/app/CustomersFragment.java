package com.bxostock.app;

import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.api.ApiService;
import com.bxostock.app.model.Customer;
import com.bxostock.app.model.CustomerRequest;
import com.bxostock.app.util.ApiError;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CustomersFragment extends BaseListFragment<Customer> {
    @Override protected String searchHint() { return "Search name, phone or email"; }
    @Override protected String actionLabel() { return "Add"; }

    @Override
    protected Call<List<Customer>> request(String kw) {
        return ApiClient.get(requireContext()).customers(kw.isEmpty() ? null : kw);
    }

    @Override
    protected Row toRow(Customer c) {
        Row r = new Row();
        r.title = c.name;
        String phone = c.phone == null ? "" : c.phone;
        String email = c.email == null || c.email.isEmpty() ? "" : c.email;
        r.sub = phone.isEmpty() ? (email.isEmpty() ? "No contact details" : email)
                : phone + (email.isEmpty() ? "" : " \u00B7 " + email);
        return r;
    }

    @Override protected void onAction() { showForm(null); }

    @Override
    protected void onItemClick(final Customer c) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(c.name)
                .setMessage("Phone: " + dash(c.phone) + "\nEmail: " + dash(c.email)
                        + "\nAddress: " + dash(c.address))
                .setPositiveButton("Close", null)
                .setNegativeButton("Edit", (d, w) -> showForm(c))
                .show();
    }

    private static String dash(String s) { return s == null || s.isEmpty() ? "-" : s; }

    private static String orNull(EditText e) {
        String s = e.getText().toString().trim();
        return s.isEmpty() ? null : s;
    }

    private void showForm(final Customer existing) {
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
        final AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(existing == null ? "Add customer" : "Edit customer")
                .setView(v)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (n.getText().toString().trim().isEmpty()) {
                n.setError("Name is required");
                return;
            }
            CustomerRequest req = new CustomerRequest();
            req.name = n.getText().toString().trim();
            req.phone = orNull(p);
            req.email = orNull(e);
            req.address = orNull(a);
            ApiService api = ApiClient.get(requireContext());
            Call<Customer> call = existing == null ? api.addCustomer(req) : api.updateCustomer(existing.id, req);
            call.enqueue(new Callback<Customer>() {
                @Override
                public void onResponse(Call<Customer> c, Response<Customer> r) {
                    if (!isAdded()) return;
                    if (r.isSuccessful()) {
                        d.dismiss();
                        Toast.makeText(requireContext(), existing == null ? "Customer added." : "Customer updated.",
                                Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Customer> c, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}