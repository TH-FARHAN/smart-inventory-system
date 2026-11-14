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

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.UserResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.Session;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StaffPerformanceFragment extends Fragment {
    private static class Agg {
        String email;
        int orders;
        double sales;
        int cancelled;
    }

    private final SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    private List<OrderResponse> orders = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private String preset = "month";
    private TextView tvMessage;
    private GridLayout grid;
    private LinearLayout list;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_staff, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        tvMessage = v.findViewById(R.id.tvMessage);
        grid = v.findViewById(R.id.grid);
        list = v.findViewById(R.id.list);
        ChipGroup chips = v.findViewById(R.id.chipPreset);
        chips.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipMonth) preset = "month";
            else if (id == R.id.chipLast) preset = "last";
            else if (id == R.id.chipYear) preset = "year";
            else preset = "all";
            render();
        });
        load();
    }

    private void message(String text, boolean error) {
        tvMessage.setTextColor(error ? 0xFFFF6B6B : 0xFF9A9AA3);
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
    }

    private void load() {
        message("Loading...", false);
        ApiClient.get(requireContext()).orders().enqueue(new Callback<List<OrderResponse>>() {
            @Override
            public void onResponse(Call<List<OrderResponse>> c, Response<List<OrderResponse>> r) {
                if (!isAdded() || getView() == null) return;
                if (r.code() == 401) { Session.expire(requireActivity(), true); return; }
                if (!r.isSuccessful() || r.body() == null) { message(ApiError.message(r), true); return; }
                orders = r.body();
                loadNames();
            }

            @Override
            public void onFailure(Call<List<OrderResponse>> c, Throwable t) {
                if (isAdded()) message(ApiError.NETWORK, true);
            }
        });
    }

    private void loadNames() {
        ApiClient.get(requireContext()).users().enqueue(new Callback<List<UserResponse>>() {
            @Override
            public void onResponse(Call<List<UserResponse>> c, Response<List<UserResponse>> r) {
                if (!isAdded() || getView() == null) return;
                if (r.isSuccessful() && r.body() != null) {
                    for (UserResponse u : r.body()) names.put(u.email.toLowerCase(Locale.ROOT), u.name);
                }
                render();
            }

            @Override
            public void onFailure(Call<List<UserResponse>> c, Throwable t) {
                if (isAdded() && getView() != null) render();
            }
        });
    }

    private static long sod(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private long[] range() {
        Calendar now = Calendar.getInstance();
        long today = sod((Calendar) now.clone());
        Calendar a = (Calendar) now.clone();
        if (preset.equals("month")) {
            a.set(Calendar.DAY_OF_MONTH, 1);
            return new long[]{sod(a), today};
        }
        if (preset.equals("last")) {
            a.set(Calendar.DAY_OF_MONTH, 1);
            a.add(Calendar.MONTH, -1);
            Calendar e = (Calendar) now.clone();
            e.set(Calendar.DAY_OF_MONTH, 1);
            e.add(Calendar.DAY_OF_YEAR, -1);
            return new long[]{sod(a), sod(e)};
        }
        if (preset.equals("year")) {
            a.set(Calendar.MONTH, Calendar.JANUARY);
            a.set(Calendar.DAY_OF_MONTH, 1);
            return new long[]{sod(a), today};
        }
        return new long[]{0L, today};
    }

    private long parse(String iso) {
        try {
            return parser.parse(iso.substring(0, 19)).getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    private String nameOf(String email) {
        String n = names.get(email);
        return n == null ? email : n;
    }

    private void render() {
        tvMessage.setVisibility(View.GONE);
        grid.removeAllViews();
        list.removeAllViews();
        long[] r = range();
        long lo = r[0], hi = r[1] + 86400000L;

        Map<String, Agg> by = new HashMap<>();
        for (OrderResponse o : orders) {
            long t = parse(o.createdAt);
            if (t < lo || t >= hi) continue;
            String key = o.createdBy == null ? "unknown" : o.createdBy.toLowerCase(Locale.ROOT);
            Agg a = by.get(key);
            if (a == null) {
                a = new Agg();
                a.email = key;
                by.put(key, a);
            }
            if ("CONFIRMED".equals(o.status)) {
                a.orders++;
                a.sales += o.totalAmount;
            } else {
                a.cancelled++;
            }
        }
        List<Agg> rows = new ArrayList<>(by.values());
        Collections.sort(rows, (x, y) -> Double.compare(y.sales, x.sales));

        double total = 0;
        int totalOrders = 0;
        for (Agg a : rows) {
            total += a.sales;
            totalOrders += a.orders;
        }
        addStat("Total sales", Fmt.money(total), 0xFFFF4D1C);
        addStat("Confirmed orders", String.valueOf(totalOrders), 0);
        addStat("Staff who sold", String.valueOf(rows.size()), 0);
        addStat("Top seller", rows.isEmpty() ? "-" : nameOf(rows.get(0).email), 0);

        if (rows.isEmpty()) {
            message("No orders in this period.", false);
            return;
        }
        for (Agg a : rows) {
            int all = a.orders + a.cancelled;
            boolean flag = all >= 5 && (a.cancelled * 100.0 / all) >= 20;
            Row row = new Row();
            row.title = nameOf(a.email);
            row.sub = a.orders + " orders \u00B7 avg " + Fmt.money(a.orders > 0 ? a.sales / a.orders : 0)
                    + (total > 0 ? " \u00B7 " + String.format(Locale.ENGLISH, "%.0f", a.sales * 100 / total) + "%" : "");
            row.right = Fmt.money(a.sales);
            row.rightSub = a.cancelled + " cancelled";
            row.warn = flag;
            View v = getLayoutInflater().inflate(R.layout.item_row, list, false);
            RowAdapter.bind(v, row);
            list.addView(v);
        }
    }

    private void addStat(String label, String value, int color) {
        View v = getLayoutInflater().inflate(R.layout.item_stat, grid, false);
        ((TextView) v.findViewById(R.id.tvLabel)).setText(label);
        TextView val = v.findViewById(R.id.tvValue);
        val.setText(value);
        if (color != 0) val.setTextColor(color);
        int m = (int) (5 * getResources().getDisplayMetrics().density);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(m, m, m, m);
        v.setLayoutParams(lp);
        grid.addView(v);
    }
}