package com.bxostock.app;

import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.Category;
import com.bxostock.app.model.CategoryRequest;
import com.bxostock.app.util.ApiError;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CategoriesFragment extends BaseListFragment<Category> {
    @Override protected String searchHint() { return "Search categories"; }
    @Override protected String actionLabel() { return "Add"; }
    @Override protected void onAction() { showAdd(); }

    @Override
    protected Call<List<Category>> request(String kw) {
        return ApiClient.get(requireContext()).categories();
    }

    @Override
    protected boolean matches(Category c, String k) {
        if (k.isEmpty()) return true;
        return c.name.toLowerCase(Locale.ROOT).contains(k)
                || (c.description != null && c.description.toLowerCase(Locale.ROOT).contains(k));
    }

    @Override
    protected Row toRow(Category c) {
        Row r = new Row();
        r.title = c.name;
        r.sub = c.description == null || c.description.isEmpty() ? "No description" : c.description;
        return r;
    }

    private void showAdd() {
        View v = getLayoutInflater().inflate(R.layout.dialog_category, null);
        final EditText n = v.findViewById(R.id.cName);
        final EditText ds = v.findViewById(R.id.cDesc);
        final AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add category")
                .setView(v)
                .setPositiveButton("Create", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            String name = n.getText().toString().trim();
            if (name.isEmpty()) { n.setError("Required"); return; }
            CategoryRequest req = new CategoryRequest();
            req.name = name;
            String desc = ds.getText().toString().trim();
            req.description = desc.isEmpty() ? null : desc;
            ApiClient.get(requireContext()).addCategory(req).enqueue(new Callback<Category>() {
                @Override
                public void onResponse(Call<Category> c, Response<Category> r) {
                    if (!isAdded()) return;
                    if (r.isSuccessful()) {
                        d.dismiss();
                        Toast.makeText(requireContext(), "Category created.", Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        String msg = r.code() == 409 ? "This category already exists." : ApiError.message(r);
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Category> c, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}