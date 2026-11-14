package com.bxostock.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.DashboardResponse;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.Product;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.Session;
import com.bxostock.app.util.TokenStore;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {
    private SwipeRefreshLayout swipe;
    private GridLayout grid;
    private LinearLayout recent, low;
    private TextView tvMessage;
    private Call<DashboardResponse> call;
    private SalesSection sales;
    private boolean admin;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        admin = "ADMIN".equals(TokenStore.role(requireContext()));
        swipe = v.findViewById(R.id.swipe);
        grid = v.findViewById(R.id.grid);
        recent = v.findViewById(R.id.recent);
        low = v.findViewById(R.id.low);
        tvMessage = v.findViewById(R.id.tvMessage);
        ((TextView) v.findViewById(R.id.tvWelcome)).setText("Welcome, " + TokenStore.name(requireContext()));
        swipe.setColorSchemeColors(0xFFFF4D1C);
        swipe.setProgressBackgroundColorSchemeColor(0xFF161618);
        swipe.setOnRefreshListener(this::load);

        if (admin) {
            ViewGroup box = v.findViewById(R.id.salesBox);
            View section = getLayoutInflater().inflate(R.layout.section_sales, box, false);
            box.addView(section);
            sales = new SalesSection(requireContext(), section);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    @Override
    public void onDestroyView() {
        if (call != null) call.cancel();
        super.onDestroyView();
    }

    private void load() {
        if (!isAdded() || getView() == null) return;
        if (call != null) call.cancel();
        swipe.setRefreshing(true);
        tvMessage.setVisibility(View.GONE);
        call = ApiClient.get(requireContext()).dashboard();
        call.enqueue(new Callback<DashboardResponse>() {
            @Override
            public void onResponse(Call<DashboardResponse> c, Response<DashboardResponse> r) {
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                    return;
                }
                if (!r.isSuccessful() || r.body() == null) {
                    show(ApiError.message(r));
                    return;
                }
                render(r.body());
            }

            @Override
            public void onFailure(Call<DashboardResponse> c, Throwable t) {
                if (c.isCanceled() || !isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                show(ApiError.NETWORK);
            }
        });

        if (sales != null) {
            ApiClient.get(requireContext()).orders().enqueue(new Callback<List<OrderResponse>>() {
                @Override
                public void onResponse(Call<List<OrderResponse>> c, Response<List<OrderResponse>> r) {
                    if (!isAdded() || getView() == null) return;
                    if (r.isSuccessful() && r.body() != null) sales.setOrders(r.body());
                }

                @Override
                public void onFailure(Call<List<OrderResponse>> c, Throwable t) { }
            });
        }
    }

    private void render(DashboardResponse d) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("en", "IN"));
        grid.removeAllViews();
        if (admin) addStat("Total sales", Fmt.money(d.totalSales), true);
        addStat("Today's orders", nf.format(d.todaysOrders), false);
        addStat("Confirmed orders", nf.format(d.totalOrders), false);
        addStat("Customers", nf.format(d.totalCustomers), false);
        addStat("Products", nf.format(d.totalProducts), false);
        addStat("Units in stock", nf.format(d.totalStock), false);
        addStat("Low stock items", nf.format(d.lowStockCount), d.lowStockCount > 0);

        recent.removeAllViews();
        if (d.recentOrders == null || d.recentOrders.isEmpty()) {
            addEmpty(recent, "No orders yet.");
        } else {
            for (OrderResponse o : d.recentOrders) {
                Row row = new Row();
                row.title = "#" + o.id + " \u00B7 " + o.customerName;
                row.sub = Fmt.date(o.createdAt);
                row.right = Fmt.money(o.totalAmount);
                row.rightSub = o.status;
                row.warn = "CANCELLED".equals(o.status);
                addRow(recent, row);
            }
        }

        low.removeAllViews();
        if (d.lowStockProducts == null || d.lowStockProducts.isEmpty()) {
            addEmpty(low, "All stock levels are fine.");
        } else {
            for (Product p : d.lowStockProducts) {
                Row row = new Row();
                row.title = p.name;
                row.sub = p.sku;
                row.right = String.valueOf(p.currentStock);
                row.rightSub = "min " + p.minimumStock;
                row.warn = true;
                addRow(low, row);
            }
        }
    }

    private void addRow(LinearLayout parent, Row row) {
        View v = getLayoutInflater().inflate(R.layout.item_row, parent, false);
        RowAdapter.bind(v, row);
        parent.addView(v);
    }

    private void addEmpty(LinearLayout parent, String text) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextColor(0xFF9A9AA3);
        t.setPadding(8, 8, 8, 8);
        parent.addView(t);
    }

    private void addStat(String label, String value, boolean accent) {
        View v = getLayoutInflater().inflate(R.layout.item_stat, grid, false);
        ((TextView) v.findViewById(R.id.tvLabel)).setText(label);
        TextView val = v.findViewById(R.id.tvValue);
        val.setText(value);
        if (accent) val.setTextColor(0xFFFF4D1C);
        int m = (int) (5 * getResources().getDisplayMetrics().density);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(m, m, m, m);
        v.setLayoutParams(lp);
        grid.addView(v);
    }

    private void show(String msg) {
        tvMessage.setText(msg);
        tvMessage.setVisibility(View.VISIBLE);
    }
}