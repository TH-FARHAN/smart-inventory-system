package com.bxostock.app;

import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.Product;
import com.bxostock.app.model.PurchaseRequest;
import com.bxostock.app.model.PurchaseResponse;
import com.bxostock.app.model.Supplier;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.Picker;
import com.bxostock.app.util.Session;
import com.bxostock.app.util.TokenStore;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NewPurchaseActivity extends AppCompatActivity {
    private static class Line {
        Product p;
        int qty;
        double cost;
    }

    private Picker<Supplier> supplierPicker;
    private Picker<Product> productPicker;
    private TextInputEditText etQty, etCost;
    private TextView tvEmpty, tvTotal, tvMessage;
    private LinearLayout cartBox;
    private View btnPlace;
    private final List<Line> cart = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!TokenStore.isLoggedIn(this)) {
            Session.expire(this, false);
            return;
        }
        setContentView(R.layout.activity_new_purchase);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        etQty = findViewById(R.id.etQty);
        etCost = findViewById(R.id.etCost);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvTotal = findViewById(R.id.tvTotal);
        tvMessage = findViewById(R.id.tvMessage);
        cartBox = findViewById(R.id.cartBox);
        btnPlace = findViewById(R.id.btnPlace);

        supplierPicker = new Picker<Supplier>((AutoCompleteTextView) findViewById(R.id.acSupplier), null);
        productPicker = new Picker<>((AutoCompleteTextView) findViewById(R.id.acProduct),
                p -> etCost.setText(String.valueOf(p.purchasePrice)));
        findViewById(R.id.btnAdd).setOnClickListener(v -> addLine());
        btnPlace.setOnClickListener(v -> place());
        render();

        ApiClient.get(this).suppliers().enqueue(new Callback<List<Supplier>>() {
            @Override
            public void onResponse(Call<List<Supplier>> c, Response<List<Supplier>> r) {
                if (!r.isSuccessful() || r.body() == null) { fail(r); return; }
                supplierPicker.set(r.body(), s -> s.name);
                if (r.body().isEmpty()) err("No suppliers yet. Add a supplier first.");
            }

            @Override
            public void onFailure(Call<List<Supplier>> c, Throwable t) { err(ApiError.NETWORK); }
        });
        ApiClient.get(this).products(null).enqueue(new Callback<List<Product>>() {
            @Override
            public void onResponse(Call<List<Product>> c, Response<List<Product>> r) {
                if (!r.isSuccessful() || r.body() == null) { fail(r); return; }
                List<Product> active = new ArrayList<>();
                for (Product p : r.body()) if (p.active) active.add(p);
                productPicker.set(active, p -> p.sku + " \u00B7 " + p.name + " \u00B7 stock " + p.currentStock);
            }

            @Override
            public void onFailure(Call<List<Product>> c, Throwable t) { err(ApiError.NETWORK); }
        });
    }

    private void addLine() {
        tvMessage.setVisibility(View.GONE);
        Product p = productPicker.selected();
        if (p == null) { err("Choose a product from the list."); return; }
        int qty;
        double cost;
        try {
            qty = Integer.parseInt(etQty.getText().toString().trim());
        } catch (NumberFormatException e) {
            qty = 0;
        }
        try {
            cost = Double.parseDouble(etCost.getText().toString().trim());
        } catch (NumberFormatException e) {
            cost = -1;
        }
        if (qty < 1) { err("Enter a quantity of at least 1."); return; }
        if (cost < 0) { err("Enter the unit cost."); return; }
        Line existing = null;
        for (Line l : cart) if (l.p.id == p.id) existing = l;
        if (existing == null) {
            existing = new Line();
            existing.p = p;
            cart.add(existing);
        }
        existing.qty = qty;
        existing.cost = cost;
        etQty.setText("");
        etCost.setText("");
        productPicker.clear();
        render();
    }

    private void render() {
        cartBox.removeAllViews();
        if (cart.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvTotal.setText("");
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        double sum = 0;
        for (final Line l : cart) {
            View v = getLayoutInflater().inflate(R.layout.item_cart, cartBox, false);
            double sub = l.cost * l.qty;
            sum += sub;
            ((TextView) v.findViewById(R.id.tvName)).setText(l.p.name);
            ((TextView) v.findViewById(R.id.tvDetail)).setText(l.qty + " \u00D7 " + Fmt.money(l.cost));
            ((TextView) v.findViewById(R.id.tvSub)).setText(Fmt.money(sub));
            v.findViewById(R.id.btnRemove).setOnClickListener(x -> {
                cart.remove(l);
                render();
            });
            cartBox.addView(v);
        }
        tvTotal.setText("Total cost: " + Fmt.money(sum));
    }

    private void place() {
        tvMessage.setVisibility(View.GONE);
        Supplier s = supplierPicker.selected();
        if (s == null) { err("Choose a supplier from the list."); return; }
        if (cart.isEmpty()) { err("Add at least one product."); return; }
        PurchaseRequest req = new PurchaseRequest();
        req.supplierId = s.id;
        for (Line l : cart) req.items.add(new PurchaseRequest.Item(l.p.id, l.qty, l.cost));
        btnPlace.setEnabled(false);
        ApiClient.get(this).createPurchase(req).enqueue(new Callback<PurchaseResponse>() {
            @Override
            public void onResponse(Call<PurchaseResponse> c, Response<PurchaseResponse> r) {
                btnPlace.setEnabled(true);
                if (r.isSuccessful() && r.body() != null) {
                    Toast.makeText(NewPurchaseActivity.this, "Purchase #" + r.body().id
                            + " created. Receive it when the goods arrive.", Toast.LENGTH_LONG).show();
                    finish();
                } else {
                    fail(r);
                }
            }

            @Override
            public void onFailure(Call<PurchaseResponse> c, Throwable t) {
                btnPlace.setEnabled(true);
                err(ApiError.NETWORK);
            }
        });
    }

    private void fail(Response<?> r) {
        if (r.code() == 401) Session.expire(this, true);
        else err(ApiError.message(r));
    }

    private void err(String msg) {
        tvMessage.setText(msg);
        tvMessage.setVisibility(View.VISIBLE);
    }
}