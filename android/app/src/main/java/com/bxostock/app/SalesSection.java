package com.bxostock.app;

import android.app.DatePickerDialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.bxostock.app.model.OrderLine;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.util.Fmt;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SalesSection {
    private static final int MAX_BUCKETS = 366;
    private static final long DAY = 86400000L;

    private final Context ctx;
    private final List<OrderResponse> orders = new ArrayList<>();
    private final SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ENGLISH);
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);
    private String preset = "6m";
    private String group = "month";
    private long customFrom, customTo;

    private final ChipGroup chipPreset, chipGroupBy;
    private final View customBox;
    private final Button btnFrom, btnTo;
    private final TextView tvRange, tvWarn;
    private final GridLayout statGrid;
    private final BarChartView chart;
    private final LinearLayout topBox;

    public SalesSection(Context ctx, View root) {
        this.ctx = ctx;
        chipPreset = root.findViewById(R.id.chipPreset);
        chipGroupBy = root.findViewById(R.id.chipGroupBy);
        customBox = root.findViewById(R.id.customBox);
        btnFrom = root.findViewById(R.id.btnFrom);
        btnTo = root.findViewById(R.id.btnTo);
        tvRange = root.findViewById(R.id.tvRange);
        tvWarn = root.findViewById(R.id.tvWarn);
        statGrid = root.findViewById(R.id.statGrid);
        chart = root.findViewById(R.id.chart);
        topBox = root.findViewById(R.id.topBox);

        long now = System.currentTimeMillis();
        customTo = sod(now);
        customFrom = monthStart(now, 0);
        updateButtons();

        chipPreset.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chip8w) { preset = "8w"; setGroup("week"); }
            else if (id == R.id.chip6m) { preset = "6m"; setGroup("month"); }
            else if (id == R.id.chipYear) { preset = "year"; setGroup("month"); }
            else if (id == R.id.chipLastYear) { preset = "lastyear"; setGroup("month"); }
            else { preset = "custom"; }
            customBox.setVisibility(preset.equals("custom") ? View.VISIBLE : View.GONE);
            render();
        });
        chipGroupBy.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipDay) group = "day";
            else if (id == R.id.chipWeek) group = "week";
            else if (id == R.id.chipYearG) group = "year";
            else group = "month";
            render();
        });
        btnFrom.setOnClickListener(v -> pick(true));
        btnTo.setOnClickListener(v -> pick(false));
    }

    public void setOrders(List<OrderResponse> all) {
        orders.clear();
        for (OrderResponse o : all) if ("CONFIRMED".equals(o.status)) orders.add(o);
        render();
    }

    private void setGroup(String g) {
        group = g;
        int id = g.equals("day") ? R.id.chipDay : g.equals("week") ? R.id.chipWeek
                : g.equals("year") ? R.id.chipYearG : R.id.chipMonth;
        chipGroupBy.check(id);
    }

    private void pick(final boolean isFrom) {
        Calendar c = cal(isFrom ? customFrom : customTo);
        new DatePickerDialog(ctx, (view, y, m, d) -> {
            Calendar n = Calendar.getInstance();
            n.set(y, m, d, 0, 0, 0);
            n.set(Calendar.MILLISECOND, 0);
            if (isFrom) customFrom = n.getTimeInMillis();
            else customTo = n.getTimeInMillis();
            updateButtons();
            render();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateButtons() {
        btnFrom.setText("From: " + dayFmt.format(customFrom));
        btnTo.setText("To: " + dayFmt.format(customTo));
    }

    // ---------- date helpers ----------
    private static Calendar cal(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c;
    }

    private static long sod(long ms) {
        Calendar c = cal(ms);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long addDays(long ms, int n) {
        Calendar c = cal(ms);
        c.add(Calendar.DAY_OF_YEAR, n);
        return c.getTimeInMillis();
    }

    private static long startOfWeek(long ms) {
        Calendar c = cal(sod(ms));
        int dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7; // Monday = 0
        c.add(Calendar.DAY_OF_YEAR, -dow);
        return c.getTimeInMillis();
    }

    private static long monthStart(long ms, int offsetMonths) {
        Calendar c = cal(sod(ms));
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, offsetMonths);
        return c.getTimeInMillis();
    }

    private static long yearStart(long ms) {
        Calendar c = cal(sod(ms));
        c.set(Calendar.MONTH, Calendar.JANUARY);
        c.set(Calendar.DAY_OF_MONTH, 1);
        return c.getTimeInMillis();
    }

    private static long bucketStart(long ms, String g) {
        if (g.equals("day")) return sod(ms);
        if (g.equals("week")) return startOfWeek(ms);
        if (g.equals("month")) return monthStart(ms, 0);
        return yearStart(ms);
    }

    private static long nextBucket(long ms, String g) {
        if (g.equals("day")) return addDays(ms, 1);
        if (g.equals("week")) return addDays(ms, 7);
        Calendar c = cal(ms);
        c.add(g.equals("month") ? Calendar.MONTH : Calendar.YEAR, 1);
        return c.getTimeInMillis();
    }

    private static String labelOf(long ms, String g) {
        if (g.equals("month")) return new SimpleDateFormat("MMM yy", Locale.ENGLISH).format(ms);
        if (g.equals("year")) return new SimpleDateFormat("yyyy", Locale.ENGLISH).format(ms);
        return new SimpleDateFormat("d MMM", Locale.ENGLISH).format(ms);
    }

    private long parse(String iso) {
        try {
            return parser.parse(iso.substring(0, 19)).getTime();
        } catch (Exception e) {
            return -1;
        }
    }

    private long[] range() {
        long now = System.currentTimeMillis();
        long today = sod(now);
        if (preset.equals("8w")) return new long[]{addDays(startOfWeek(now), -49), today};
        if (preset.equals("6m")) return new long[]{monthStart(now, -5), today};
        if (preset.equals("year")) return new long[]{yearStart(now), today};
        if (preset.equals("lastyear")) {
            Calendar c = cal(now);
            c.add(Calendar.YEAR, -1);
            return new long[]{yearStart(c.getTimeInMillis()), addDays(yearStart(now), -1)};
        }
        return new long[]{customFrom, customTo};
    }

    private double sum(long start, long end, int[] count) {
        long lo = start, hi = addDays(end, 1);
        double s = 0;
        int n = 0;
        for (OrderResponse o : orders) {
            long t = parse(o.createdAt);
            if (t >= lo && t < hi) { s += o.totalAmount; n++; }
        }
        if (count != null) count[0] = n;
        return s;
    }

    // ---------- drawing ----------
    private void warn(String msg) {
        tvWarn.setText(msg);
        tvWarn.setVisibility(View.VISIBLE);
        statGrid.removeAllViews();
        topBox.removeAllViews();
        chart.setData(new float[0], new String[0]);
    }

    private void render() {
        long[] r = range();
        long start = r[0], end = r[1];
        tvWarn.setVisibility(View.GONE);
        if (start > end) {
            warn("The From date must be before the To date.");
            return;
        }
        int days = (int) Math.round((end - start) / (double) DAY) + 1;
        tvRange.setText(dayFmt.format(start) + " to " + dayFmt.format(end) + " \u00B7 " + days
                + (days == 1 ? " day" : " days"));

        List<Long> starts = new ArrayList<>();
        long c = bucketStart(start, group);
        while (c <= end && starts.size() <= MAX_BUCKETS) {
            starts.add(c);
            c = nextBucket(c, group);
        }
        if (starts.size() > MAX_BUCKETS) {
            warn("Too many bars for this range. Group by Week, Month or Year instead.");
            return;
        }
        float[] vals = new float[starts.size()];
        int[] cnts = new int[starts.size()];
        String[] labels = new String[starts.size()];
        for (int i = 0; i < starts.size(); i++) labels[i] = labelOf(starts.get(i), group);

        long lo = start, hi = addDays(end, 1);
        double cur = 0;
        int count = 0;
        Map<String, Double> byProduct = new HashMap<>();
        for (OrderResponse o : orders) {
            long t = parse(o.createdAt);
            if (t < lo || t >= hi) continue;
            int idx = Collections.binarySearch(starts, bucketStart(t, group));
            if (idx >= 0) { vals[idx] += (float) o.totalAmount; cnts[idx]++; }
            cur += o.totalAmount;
            count++;
            if (o.items != null) {
                for (OrderLine l : o.items) {
                    Double old = byProduct.get(l.productName);
                    byProduct.put(l.productName, (old == null ? 0 : old) + l.subtotal);
                }
            }
        }
        chart.setData(vals, labels, cnts);

        long prevEnd = addDays(start, -1);
        long prevStart = addDays(prevEnd, -(days - 1));
        double prev = sum(prevStart, prevEnd, null);
        double avg = count > 0 ? cur / count : 0;

        statGrid.removeAllViews();
        addStat("Sales in period", Fmt.money(cur), 0xFFFF4D1C);
        addStat("Previous period", Fmt.money(prev), 0);
        if (prev > 0) {
            double ch = (cur - prev) / prev * 100;
            addStat("Change", (ch >= 0 ? "+" : "") + String.format(Locale.ENGLISH, "%.1f", ch) + "%",
                    ch >= 0 ? 0xFF3DDC84 : 0xFFFF6B6B);
        } else {
            addStat("Change", "-", 0);
        }
        addStat("Orders", String.valueOf(count), 0);
        addStat("Average order", Fmt.money(avg), 0);

        // top 5 products
        List<Map.Entry<String, Double>> list = new ArrayList<>(byProduct.entrySet());
        Collections.sort(list, (a, b) -> Double.compare(b.getValue(), a.getValue()));
        topBox.removeAllViews();
        if (list.isEmpty()) {
            TextView t = new TextView(ctx);
            t.setText("No sales in this period.");
            t.setTextColor(0xFF9A9AA3);
            t.setPadding(0, 8, 0, 8);
            topBox.addView(t);
            return;
        }
        double top = list.get(0).getValue();
        for (int i = 0; i < Math.min(5, list.size()); i++) {
            Map.Entry<String, Double> e = list.get(i);
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_top, topBox, false);
            ((TextView) v.findViewById(R.id.tvName)).setText(e.getKey());
            ((TextView) v.findViewById(R.id.tvAmount)).setText(Fmt.money(e.getValue()));
            ((ProgressBar) v.findViewById(R.id.bar)).setProgress(top > 0 ? (int) Math.round(e.getValue() / top * 100) : 0);
            topBox.addView(v);
        }
    }

    private void addStat(String label, String value, int color) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_stat, statGrid, false);
        ((TextView) v.findViewById(R.id.tvLabel)).setText(label);
        TextView val = v.findViewById(R.id.tvValue);
        val.setText(value);
        if (color != 0) val.setTextColor(color);
        int m = (int) (5 * ctx.getResources().getDisplayMetrics().density);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(m, m, m, m);
        v.setLayoutParams(lp);
        statGrid.addView(v);
    }
}