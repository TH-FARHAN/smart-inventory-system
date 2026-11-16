package com.bxostock.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.DashboardResponse;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.Product;
import com.bxostock.app.model.ReturnLine;
import com.bxostock.app.model.ReturnResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.BxoToast;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.Session;
import com.bxostock.app.util.TokenStore;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardFragment extends Fragment {
    private SwipeRefreshLayout swipe;
    private GridLayout grid;
    private LinearLayout recent, low, recentReturns;
    private TextView tvMessage;
    private Call<DashboardResponse> call;
    private Call<List<ReturnResponse>> callReturns;
    private SalesSection sales;
    private boolean admin;
    private DashboardResponse dash;
    private List<ReturnResponse> rets = new ArrayList<>();

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
        recentReturns = v.findViewById(R.id.recentReturns);
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
        if (callReturns != null) callReturns.cancel();
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
                dash = r.body();
                draw();
            }

            @Override
            public void onFailure(Call<DashboardResponse> c, Throwable t) {
                if (c.isCanceled() || !isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                show(ApiError.NETWORK);
            }
        });

        if (callReturns != null) callReturns.cancel();
        callReturns = ApiClient.get(requireContext()).returnsList();
        callReturns.enqueue(new Callback<List<ReturnResponse>>() {
            @Override
            public void onResponse(Call<List<ReturnResponse>> c, Response<List<ReturnResponse>> r) {
                if (!isAdded() || getView() == null) return;
                if (r.isSuccessful() && r.body() != null) {
                    rets = r.body();
                    draw();
                }
            }

            @Override
            public void onFailure(Call<List<ReturnResponse>> c, Throwable t) { }
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

    private static String todayUtc() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        return f.format(new Date());
    }

    private void draw() {
        if (dash == null || !isAdded() || getView() == null) return;
        DashboardResponse d = dash;
        NumberFormat nf = NumberFormat.getInstance(new Locale("en", "IN"));

        int returnsToday = 0;
        double refunded = 0;
        String today = todayUtc();
        for (ReturnResponse r : rets) {
            refunded += r.refundAmount;
            if (r.createdAt != null && r.createdAt.startsWith(today)) returnsToday++;
        }

        grid.removeAllViews();
        if (admin) addStat("Total sales", Fmt.money(d.totalSales), true, () -> openScreen(new ReportsFragment()));
        addStat("Today's orders", nf.format(d.todaysOrders), false,
                () -> openOrders("today", "Showing today's confirmed orders"));
        addStat("Confirmed orders", nf.format(d.totalOrders), false,
                () -> openOrders("confirmed", "Showing confirmed orders"));
        addStat("Customers", nf.format(d.totalCustomers), false, () -> openScreen(new CustomersFragment()));
        addStat("Products", nf.format(d.totalProducts), false, () -> openScreen(new ProductsFragment()));
        addStat("Units in stock", nf.format(d.totalStock), false, () -> openScreen(new ProductsFragment()));
        addStat("Low stock items", nf.format(d.lowStockCount), d.lowStockCount > 0, this::openLowStock);
        addStat("Returns today", nf.format(returnsToday), returnsToday > 0, () -> openScreen(new ReturnsFragment()));
        addStat("Total refunded", Fmt.money(refunded), false, () -> openScreen(new ReturnsFragment()));

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
                addRow(recent, row, () -> openOrders(null, null));
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
                addRow(low, row, this::openLowStock);
            }
        }

        recentReturns.removeAllViews();
        if (rets.isEmpty()) {
            addEmpty(recentReturns, "No returns yet.");
        } else {
            int shown = 0;
            for (ReturnResponse r : rets) {
                if (shown++ >= 5) break;
                StringBuilder items = new StringBuilder();
                if (r.items != null) {
                    for (ReturnLine l : r.items) {
                        if (items.length() > 0) items.append(", ");
                        items.append(l.quantity).append(" \u00D7 ").append(l.productName);
                    }
                }
                Row row = new Row();
                row.title = "Return #" + r.id + " \u00B7 Order #" + r.orderId;
                row.sub = items + "\n" + r.customerName + " \u00B7 " + r.reason;
                row.right = "-" + Fmt.money(r.refundAmount);
                row.rightSub = Fmt.date(r.createdAt);
                row.warn = true;
                addRow(recentReturns, row, () -> openScreen(new ReturnsFragment()));
            }
        }
    }

    private void openScreen(Fragment f) {
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).open(f);
    }

    private void openOrders(String preset, String message) {
        OrdersFragment f = new OrdersFragment();
        if (preset != null) {
            Bundle b = new Bundle();
            b.putString("filter", preset);
            f.setArguments(b);
        }
        openScreen(f);
        if (message != null) BxoToast.show(requireActivity().findViewById(android.R.id.content), message);
    }

    private void openLowStock() {
        ProductsFragment f = new ProductsFragment();
        Bundle b = new Bundle();
        b.putBoolean("low", true);
        f.setArguments(b);
        openScreen(f);
        BxoToast.show(requireActivity().findViewById(android.R.id.content), "Showing low stock products");
    }

    private void addRow(LinearLayout parent, Row row, final Runnable click) {
        View v = getLayoutInflater().inflate(R.layout.item_row, parent, false);
        RowAdapter.bind(v, row);
        v.setOnClickListener(x -> click.run());
        parent.addView(v);
    }

    private void addEmpty(LinearLayout parent, String text) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextColor(0xFF9A9AA3);
        t.setPadding(8, 8, 8, 8);
        parent.addView(t);
    }

    private void addStat(String label, String value, boolean accent, final Runnable click) {
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
        v.setOnClickListener(x -> click.run());
        grid.addView(v);
    }

    private void show(String msg) {
        tvMessage.setText(msg);
        tvMessage.setVisibility(View.VISIBLE);
    }
}