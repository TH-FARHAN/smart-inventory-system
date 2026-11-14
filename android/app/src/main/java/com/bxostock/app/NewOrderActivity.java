package com.bxostock.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.Customer;
import com.bxostock.app.model.OrderRequest;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.Product;
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

public class NewOrderActivity extends AppCompatActivity {
    private static class Line {
        Product p;
        int qty;
    }

    private Picker<Customer> customerPicker;
    private Picker<Product> productPicker;
    private TextInputEditText etQty;
    private TextView tvStock, tvEmpty, tvTotal, tvMessage;
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
        setContentView(R.layout.activity_new_order);
        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        etQty = findViewById(R.id.etQty);
        tvStock = findViewById(R.id.tvStock);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvTotal = findViewById(R.id.tvTotal);
        tvMessage = findViewById(R.id.tvMessage);
        cartBox = findViewById(R.id.cartBox);
        btnPlace = findViewById(R.id.btnPlace);

        customerPicker = new Picker<Customer>((AutoCompleteTextView) findViewById(R.id.acCustomer), null);
        productPicker = new Picker<>((AutoCompleteTextView) findViewById(R.id.acProduct),
                p -> tvStock.setText("In stock: " + p.currentStock + " \u00B7 " + Fmt.money(p.sellingPrice)));

        findViewById(R.id.btnAdd).setOnClickListener(v -> addLine());
        findViewById(R.id.btnClear).setOnClickListener(v -> {
            cart.clear();
            render();
        });
        btnPlace.setOnClickListener(v -> place());
        render();
        loadCustomers();
        loadProducts();
    }

    private void loadCustomers() {
        ApiClient.get(this).customers(null).enqueue(new Callback<List<Customer>>() {
            @Override
            public void onResponse(Call<List<Customer>> c, Response<List<Customer>> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    fail(r);
                    return;
                }
                customerPicker.set(r.body(), cu -> cu.name + (cu.phone == null || cu.phone.isEmpty() ? "" : " (" + cu.phone + ")"));
                if (r.body().isEmpty()) err("No customers yet. Add a customer first.");
            }

            @Override
            public void onFailure(Call<List<Customer>> c, Throwable t) {
                err(ApiError.NETWORK);
            }
        });
    }

    private void loadProducts() {
        ApiClient.get(this).products(null).enqueue(new Callback<List<Product>>() {
            @Override
            public void onResponse(Call<List<Product>> c, Response<List<Product>> r) {
                if (!r.isSuccessful() || r.body() == null) {
                    fail(r);
                    return;
                }
                List<Product> active = new ArrayList<>();
                for (Product p : r.body()) if (p.active) active.add(p);
                productPicker.set(active, p -> p.sku + " \u00B7 " + p.name + " \u00B7 "
                        + Fmt.money(p.sellingPrice) + " \u00B7 stock " + p.currentStock);
            }

            @Override
            public void onFailure(Call<List<Product>> c, Throwable t) {
                err(ApiError.NETWORK);
            }
        });
    }

    private void addLine() {
        tvMessage.setVisibility(View.GONE);
        Product p = productPicker.selected();
        if (p == null) {
            err("Choose a product from the list.");
            return;
        }
        int qty = parse(etQty.getText().toString().trim());
        if (qty < 1) {
            err("Enter a quantity of at least 1.");
            return;
        }
        Line existing = null;
        for (Line l : cart) if (l.p.id == p.id) existing = l;
        int total = qty + (existing == null ? 0 : existing.qty);
        if (total > p.currentStock) {
            err("Only " + p.currentStock + " of " + p.name + " in stock.");
            return;
        }
        if (existing != null) {
            existing.qty = total;
        } else {
            Line l = new Line();
            l.p = p;
            l.qty = qty;
            cart.add(l);
        }
        etQty.setText("");
        productPicker.clear();
        tvStock.setText("");
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
            double sub = l.p.sellingPrice * l.qty;
            sum += sub;
            ((TextView) v.findViewById(R.id.tvName)).setText(l.p.name);
            ((TextView) v.findViewById(R.id.tvDetail)).setText(l.qty + " \u00D7 " + Fmt.money(l.p.sellingPrice));
            ((TextView) v.findViewById(R.id.tvSub)).setText(Fmt.money(sub));
            v.findViewById(R.id.btnRemove).setOnClickListener(x -> {
                cart.remove(l);
                render();
            });
            cartBox.addView(v);
        }
        tvTotal.setText("Total (estimate): " + Fmt.money(sum));
    }

    private void place() {
        tvMessage.setVisibility(View.GONE);
        Customer cu = customerPicker.selected();
        if (cu == null) {
            err("Choose a customer from the list.");
            return;
        }
        if (cart.isEmpty()) {
            err("Add at least one product.");
            return;
        }
        OrderRequest req = new OrderRequest();
        req.customerId = cu.id;
        for (Line l : cart) req.items.add(new OrderRequest.Item(l.p.id, l.qty));
        btnPlace.setEnabled(false);
        ApiClient.get(this).placeOrder(req).enqueue(new Callback<OrderResponse>() {
            @Override
            public void onResponse(Call<OrderResponse> c, Response<OrderResponse> r) {
                btnPlace.setEnabled(true);
                if (r.isSuccessful() && r.body() != null) {
                    Intent data = new Intent();
                    data.putExtra("orderId", r.body().id);
                    data.putExtra("total", r.body().totalAmount);
                    setResult(RESULT_OK, data);
                    finish();
                } else {
                    fail(r);
                }
            }

            @Override
            public void onFailure(Call<OrderResponse> c, Throwable t) {
                btnPlace.setEnabled(true);
                err(ApiError.NETWORK);
            }
        });
    }

    private int parse(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
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