package com.bxostock.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.Product;
import com.bxostock.app.model.StockRequest;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Picker;
import com.bxostock.app.util.Session;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StockFragment extends Fragment {
    private Picker<Product> picker;
    private TextInputEditText etQty, etReason;
    private TextView tvInfo, tvMessage;
    private View btnIn, btnOut;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_stock, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        etQty = v.findViewById(R.id.etQty);
        etReason = v.findViewById(R.id.etReason);
        tvInfo = v.findViewById(R.id.tvInfo);
        tvMessage = v.findViewById(R.id.tvMessage);
        btnIn = v.findViewById(R.id.btnIn);
        btnOut = v.findViewById(R.id.btnOut);
        picker = new Picker<>((AutoCompleteTextView) v.findViewById(R.id.acProduct), this::showInfo);
        btnIn.setOnClickListener(x -> submit(true));
        btnOut.setOnClickListener(x -> submit(false));
        loadProducts();
    }

    private void showInfo(Product p) {
        tvInfo.setText("In stock: " + p.currentStock + " (minimum " + p.minimumStock + ")");
    }

    private void loadProducts() {
        ApiClient.get(requireContext()).products(null).enqueue(new Callback<List<Product>>() {
            @Override
            public void onResponse(Call<List<Product>> c, Response<List<Product>> r) {
                if (!isAdded()) return;
                if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                    return;
                }
                if (!r.isSuccessful() || r.body() == null) {
                    error(ApiError.message(r));
                    return;
                }
                List<Product> active = new ArrayList<>();
                for (Product p : r.body()) if (p.active) active.add(p);
                picker.set(active, p -> p.sku + " \u00B7 " + p.name);
            }

            @Override
            public void onFailure(Call<List<Product>> c, Throwable t) {
                if (isAdded()) error(ApiError.NETWORK);
            }
        });
    }

    private void submit(final boolean in) {
        tvMessage.setVisibility(View.GONE);
        Product p = picker.selected();
        if (p == null) {
            error("Choose a product from the list.");
            return;
        }
        int qty;
        try {
            qty = Integer.parseInt(etQty.getText().toString().trim());
        } catch (NumberFormatException e) {
            qty = 0;
        }
        if (qty < 1) {
            error("Enter a quantity of at least 1.");
            return;
        }
        StockRequest req = new StockRequest();
        req.productId = p.id;
        req.quantity = qty;
        String reason = etReason.getText().toString().trim();
        req.reason = reason.isEmpty() ? null : reason;

        busy(true);
        Call<Product> call = in ? ApiClient.get(requireContext()).stockIn(req)
                : ApiClient.get(requireContext()).stockOut(req);
        call.enqueue(new Callback<Product>() {
            @Override
            public void onResponse(Call<Product> c, Response<Product> r) {
                if (!isAdded()) return;
                busy(false);
                if (r.isSuccessful() && r.body() != null) {
                    showInfo(r.body());
                    Toast.makeText(requireContext(), "Stock updated. Now in stock: " + r.body().currentStock,
                            Toast.LENGTH_LONG).show();
                    etQty.setText("");
                    etReason.setText("");
                    loadProducts();
                } else if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                } else {
                    error(ApiError.message(r));
                }
            }

            @Override
            public void onFailure(Call<Product> c, Throwable t) {
                if (!isAdded()) return;
                busy(false);
                error(ApiError.NETWORK);
            }
        });
    }

    private void busy(boolean on) {
        btnIn.setEnabled(!on);
        btnOut.setEnabled(!on);
    }

    private void error(String msg) {
        tvMessage.setText(msg);
        tvMessage.setVisibility(View.VISIBLE);
    }
}