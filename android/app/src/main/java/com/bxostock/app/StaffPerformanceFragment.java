package com.bxostock.app;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.ReturnResponse;
import com.bxostock.app.model.UserResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.Session;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;

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
        double refunds;
    }

    private final SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    private List<OrderResponse> orders = new ArrayList<>();
    private List<ReturnResponse> returns = new ArrayList<>();
    private boolean loaded = false;
    private long customFrom, customTo;
    private final SimpleDateFormat nice = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
    private View customBox;
    private Button btnFrom, btnTo;
    private TextInputEditText etSearch;
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
        customBox = v.findViewById(R.id.customBox);
        btnFrom = v.findViewById(R.id.btnFrom);
        btnTo = v.findViewById(R.id.btnTo);
        etSearch = v.findViewById(R.id.etStaffSearch);
        Calendar m = Calendar.getInstance();
        m.set(Calendar.DAY_OF_MONTH, 1);
        customFrom = sod(m);
        customTo = sod(Calendar.getInstance());
        updateCustomButtons();
        btnFrom.setOnClickListener(x -> pick(true));
        btnTo.setOnClickListener(x -> pick(false));
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void afterTextChanged(Editable e) { if (loaded) render(); }
        });
        ChipGroup chips = v.findViewById(R.id.chipPreset);
        chips.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipMonth) preset = "month";
            else if (id == R.id.chipLast) preset = "last";
            else if (id == R.id.chipYear) preset = "year";
            else if (id == R.id.chipCustom) preset = "custom";
            else preset = "all";
            customBox.setVisibility(preset.equals("custom") ? View.VISIBLE : View.GONE);
            if (loaded) render();
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
                loadReturns();
            }

            @Override
            public void onFailure(Call<List<OrderResponse>> c, Throwable t) {
                if (isAdded()) message(ApiError.NETWORK, true);
            }
        });
    }

    private void loadReturns() {
        ApiClient.get(requireContext()).returnsList().enqueue(new Callback<List<ReturnResponse>>() {
            @Override
            public void onResponse(Call<List<ReturnResponse>> c, Response<List<ReturnResponse>> r) {
                if (!isAdded() || getView() == null) return;
                returns = r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<ReturnResponse>();
                loadNames();
            }

            @Override
            public void onFailure(Call<List<ReturnResponse>> c, Throwable t) {
                if (isAdded() && getView() != null) loadNames();
            }
        });
    }

    private void updateCustomButtons() {
        btnFrom.setText("From: " + nice.format(customFrom));
        btnTo.setText("To: " + nice.format(customTo));
    }

    private void pick(final boolean isFrom) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(isFrom ? customFrom : customTo);
        new DatePickerDialog(requireContext(), (view, y, mo, d) -> {
            Calendar n = Calendar.getInstance();
            n.set(y, mo, d);
            if (isFrom) customFrom = sod(n);
            else customTo = sod(n);
            updateCustomButtons();
            if (loaded) render();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadNames() {
        ApiClient.get(requireContext()).users().enqueue(new Callback<List<UserResponse>>() {
            @Override
            public void onResponse(Call<List<UserResponse>> c, Response<List<UserResponse>> r) {
                if (!isAdded() || getView() == null) return;
                if (r.isSuccessful() && r.body() != null) {
                    for (UserResponse u : r.body()) names.put(u.email.toLowerCase(Locale.ROOT), u.name);
                }
                loaded = true;
                render();
            }

            @Override
            public void onFailure(Call<List<UserResponse>> c, Throwable t) {
                if (isAdded() && getView() != null) {
                    loaded = true;
                    render();
                }
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
        if (preset.equals("custom")) return new long[]{customFrom, customTo};
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
        if (r[0] > r[1]) {
            message("The From date must be before the To date.", true);
            return;
        }
        long lo = r[0], hi = r[1] + 86400000L;

        Map<Long, String> owner = new HashMap<>();
        for (OrderResponse o : orders) owner.put(o.id, o.createdBy == null ? "unknown" : o.createdBy.toLowerCase(Locale.ROOT));

        Map<String, Agg> by = new HashMap<>();
        for (OrderResponse o : orders) {
            long t = parse(o.createdAt);
            if (t < lo || t >= hi) continue;
            Agg a = agg(by, o.createdBy == null ? "unknown" : o.createdBy.toLowerCase(Locale.ROOT));
            if ("CONFIRMED".equals(o.status)) {
                a.orders++;
                a.sales += o.totalAmount;
            } else {
                a.cancelled++;
            }
        }
        // a refund is charged to the staff member who made the original sale
        for (ReturnResponse rr : returns) {
            long t = parse(rr.createdAt);
            if (t < lo || t >= hi) continue;
            String k = owner.get(rr.orderId);
            agg(by, k == null ? "unknown" : k).refunds += rr.refundAmount;
        }
        List<Agg> rows = new ArrayList<>(by.values());
        Collections.sort(rows, (x, y) -> Double.compare(y.sales - y.refunds, x.sales - x.refunds));

        double totalNet = 0, totalRefunds = 0;
        int totalOrders = 0;
        for (Agg a : rows) {
            totalNet += a.sales - a.refunds;
            totalRefunds += a.refunds;
            totalOrders += a.orders;
        }
        addStat("Net sales", Fmt.money(totalNet), 0xFFFF4D1C);
        addStat("Confirmed orders", String.valueOf(totalOrders), 0);
        addStat("Refunds", Fmt.money(totalRefunds), totalRefunds > 0 ? 0xFFFF6B6B : 0);
        addStat("Top seller", rows.isEmpty() ? "-" : nameOf(rows.get(0).email), 0);

        String kw = etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Agg> shown = new ArrayList<>();
        for (Agg a : rows) {
            if (kw.isEmpty() || a.email.contains(kw) || nameOf(a.email).toLowerCase(Locale.ROOT).contains(kw)) shown.add(a);
        }
        if (shown.isEmpty()) {
            message("No orders in this period.", false);
            return;
        }
        for (Agg a : shown) {
            double net = a.sales - a.refunds;
            int all = a.orders + a.cancelled;
            boolean flag = all >= 5 && (a.cancelled * 100.0 / all) >= 20;
            Row row = new Row();
            row.title = nameOf(a.email);
            row.sub = a.orders + " orders \u00B7 sales " + Fmt.money(a.sales) + " \u00B7 refunds " + Fmt.money(a.refunds)
                    + (totalNet > 0 ? " \u00B7 " + String.format(Locale.ENGLISH, "%.0f", Math.max(0, net * 100 / totalNet)) + "%" : "");
            row.right = Fmt.money(net);
            row.rightSub = a.cancelled + " cancelled";
            row.warn = flag;
            View v = getLayoutInflater().inflate(R.layout.item_row, list, false);
            RowAdapter.bind(v, row);
            list.addView(v);
        }
    }

    private static Agg agg(Map<String, Agg> by, String email) {
        Agg a = by.get(email);
        if (a == null) {
            a = new Agg();
            a.email = email;
            by.put(email, a);
        }
        return a;
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