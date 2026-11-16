package com.bxostock.app;

import com.bxostock.app.util.BxoDialog;

import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.api.ApiService;
import com.bxostock.app.model.Category;
import com.bxostock.app.model.Product;
import com.bxostock.app.model.ProductRequest;
import com.bxostock.app.model.Supplier;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.Picker;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductsFragment extends BaseListFragment<Product> {
    private boolean isAdmin() {
        return "ADMIN".equals(TokenStore.role(requireContext()));
    }

    @Override protected String searchHint() { return "Search name or SKU"; }
    @Override protected String filterLabel() { return "Low stock only"; }

    @Override
    public void onViewCreated(@androidx.annotation.NonNull android.view.View v, @androidx.annotation.Nullable android.os.Bundle s) {
        super.onViewCreated(v, s);
        if (getArguments() != null && getArguments().getBoolean("low")) cbFilter.setChecked(true);
    }
    @Override protected String actionLabel() { return isAdmin() ? "Add" : null; }
    @Override protected void onAction() { openForm(null); }

    @Override
    protected Call<List<Product>> request(String kw) {
        ApiService api = ApiClient.get(requireContext());
        return cbFilter.isChecked() ? api.lowStock() : api.products(kw.isEmpty() ? null : kw);
    }

    @Override
    protected boolean matches(Product p, String k) {
        if (!p.active) return false;
        if (k.isEmpty() || !cbFilter.isChecked()) return true;
        return p.name.toLowerCase(Locale.ROOT).contains(k) || p.sku.toLowerCase(Locale.ROOT).contains(k);
    }

    @Override
    protected Row toRow(Product p) {
        Row r = new Row();
        r.title = p.name;
        r.sub = p.sku + " \u00B7 " + (p.categoryName == null ? "No category" : p.categoryName)
                + " \u00B7 " + Fmt.money(p.sellingPrice);
        r.right = "Stock " + p.currentStock;
        r.rightSub = p.lowStock ? "Low stock" : null;
        r.warn = p.lowStock;
        return r;
    }

    @Override
    protected void onItemClick(final Product p) {
        MaterialAlertDialogBuilder b = new BxoDialog(requireContext())
                .setTitle(p.name)
                .setMessage("SKU: " + p.sku
                        + "\nDescription: " + (p.description == null || p.description.trim().isEmpty() ? "-" : p.description.trim())
                        + "\nCategory: " + (p.categoryName == null ? "-" : p.categoryName)
                        + "\nSupplier: " + (p.supplierName == null ? "-" : p.supplierName)
                        + "\nPurchase price: " + Fmt.money(p.purchasePrice)
                        + "\nSelling price: " + Fmt.money(p.sellingPrice)
                        + "\nIn stock: " + p.currentStock
                        + "\nMinimum stock: " + p.minimumStock
                        + (p.lowStock ? "\n\nLOW STOCK" : ""))
                .setNeutralButton("Close", null);
        if (isAdmin()) {
            b.setPositiveButton("Edit", (d, w) -> openForm(p));
            b.setNegativeButton("Delete", (d, w) -> confirmDelete(p));
        }
        b.show();
    }

    private void confirmDelete(final Product p) {
        new BxoDialog(requireContext())
                .setTitle("Delete product")
                .setMessage("Delete \"" + p.name + "\"? It will no longer appear in the product list.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (d, w) ->
                        ApiClient.get(requireContext()).deleteProduct(p.id).enqueue(new Callback<ResponseBody>() {
                            @Override
                            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                                if (!isAdded()) return;
                                if (r.isSuccessful()) {
                                    Toast.makeText(requireContext(), "Product deleted.", Toast.LENGTH_SHORT).show();
                                    reload();
                                } else {
                                    Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<ResponseBody> c, Throwable t) {
                                if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                            }
                        }))
                .show();
    }

    // load the categories and suppliers first, then show the form
    private void openForm(final Product existing) {
        ApiClient.get(requireContext()).categories().enqueue(new Callback<List<Category>>() {
            @Override
            public void onResponse(Call<List<Category>> c, Response<List<Category>> r) {
                if (!isAdded()) return;
                loadSuppliers(existing, r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<Category>());
            }

            @Override
            public void onFailure(Call<List<Category>> c, Throwable t) {
                if (isAdded()) loadSuppliers(existing, new ArrayList<Category>());
            }
        });
    }

    private void loadSuppliers(final Product existing, final List<Category> cats) {
        ApiClient.get(requireContext()).suppliers().enqueue(new Callback<List<Supplier>>() {
            @Override
            public void onResponse(Call<List<Supplier>> c, Response<List<Supplier>> r) {
                if (!isAdded()) return;
                showForm(existing, cats, r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<Supplier>());
            }

            @Override
            public void onFailure(Call<List<Supplier>> c, Throwable t) {
                if (isAdded()) showForm(existing, cats, new ArrayList<Supplier>());
            }
        });
    }

    private static String text(EditText e) { return e.getText().toString().trim(); }

    private static double num(EditText e) {
        try {
            return Double.parseDouble(text(e));
        } catch (Exception x) {
            return -1;
        }
    }

    private static int whole(EditText e, int fallback) {
        try {
            return Integer.parseInt(text(e));
        } catch (Exception x) {
            return fallback;
        }
    }

    private void showForm(final Product existing, List<Category> cats, List<Supplier> sups) {
        View v = getLayoutInflater().inflate(R.layout.dialog_product, null);
        final EditText name = v.findViewById(R.id.dName);
        final EditText sku = v.findViewById(R.id.dSku);
        final AutoCompleteTextView acCat = v.findViewById(R.id.dCategory);
        final AutoCompleteTextView acSup = v.findViewById(R.id.dSupplier);
        final EditText desc = v.findViewById(R.id.dDescription);
        final EditText purchase = v.findViewById(R.id.dPurchase);
        final EditText selling = v.findViewById(R.id.dSelling);
        final EditText min = v.findViewById(R.id.dMin);
        final EditText opening = v.findViewById(R.id.dOpening);

        final Picker<Category> picker = new Picker<Category>(acCat, null);
        picker.set(cats, c -> c.name);
        final Picker<Supplier> supPicker = new Picker<Supplier>(acSup, null);
        supPicker.set(sups, s -> s.name);

        if (existing != null) {
            name.setText(existing.name);
            sku.setText(existing.sku);
            if (existing.categoryName != null) acCat.setText(existing.categoryName, false);
            if (existing.supplierName != null) acSup.setText(existing.supplierName, false);
            desc.setText(existing.description);
            purchase.setText(String.valueOf(existing.purchasePrice));
            selling.setText(String.valueOf(existing.sellingPrice));
            min.setText(String.valueOf(existing.minimumStock));
            v.findViewById(R.id.tilOpening).setVisibility(View.GONE);
        } else {
            min.setText("0");
            opening.setText("0");
        }

        final AlertDialog d = new BxoDialog(requireContext())
                .setTitle(existing == null ? "Add product" : "Edit product")
                .setView(v)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            if (text(name).isEmpty()) { name.setError("Required"); return; }
            if (text(sku).isEmpty()) { sku.setError("Required"); return; }
            double pp = num(purchase);
            double sp = num(selling);
            if (pp < 0) { purchase.setError("Enter a price"); return; }
            if (sp < 0) { selling.setError("Enter a price"); return; }
            Category cat = picker.selected();
            if (!text(acCat).isEmpty() && cat == null) {
                acCat.setError("Pick a category from the list, or clear it");
                return;
            }
            Supplier sup = supPicker.selected();
            if (!text(acSup).isEmpty() && sup == null) {
                acSup.setError("Pick a supplier from the list, or clear it");
                return;
            }
            ProductRequest req = new ProductRequest();
            req.supplierId = sup == null ? null : sup.id;
            req.name = text(name);
            req.sku = text(sku);
            req.description = text(desc);
            req.categoryId = cat == null ? null : cat.id;
            req.purchasePrice = pp;
            req.sellingPrice = sp;
            req.minimumStock = whole(min, 0);
            ApiService api = ApiClient.get(requireContext());
            Call<Product> call;
            if (existing == null) {
                req.openingStock = whole(opening, 0);
                call = api.addProduct(req);
            } else {
                call = api.updateProduct(existing.id, req);
            }
            call.enqueue(new Callback<Product>() {
                @Override
                public void onResponse(Call<Product> c, Response<Product> r) {
                    if (!isAdded()) return;
                    if (r.isSuccessful()) {
                        d.dismiss();
                        Toast.makeText(requireContext(), existing == null ? "Product added." : "Product updated.",
                                Toast.LENGTH_SHORT).show();
                        reload();
                    } else {
                        Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<Product> c, Throwable t) {
                    if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}