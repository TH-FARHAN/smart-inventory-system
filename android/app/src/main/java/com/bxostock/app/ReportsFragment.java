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
import com.bxostock.app.model.InventoryReport;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.Product;
import com.bxostock.app.model.ProductSalesRow;
import com.bxostock.app.model.ReturnLine;
import com.bxostock.app.model.ReturnResponse;
import com.bxostock.app.model.SalesReport;
import com.bxostock.app.model.UserResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.PdfExporter;
import com.bxostock.app.util.Session;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

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

public class ReportsFragment extends Fragment {
    // ---- extra figures built from returns and orders (same maths as the web Reports page) ----
    private static class Prod {
        long id; String name, sku; long sold; long returned; double net;
    }

    private static class Ret {
        long id; String date; long orderId; String name; int qty; double price, refund; String reason;
    }

    private static class Staff {
        String email, name; int orders, cancelled; double sales, refunds, net;
    }

    private boolean salesMode = true;
    private String preset = "month";
    private long from, to;
    private final SimpleDateFormat param = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);
    private final SimpleDateFormat nice = new SimpleDateFormat("d MMM yyyy", Locale.ENGLISH);

    private View salesFilters, customBox;
    private Button btnFrom, btnTo;
    private TextView tvRange, tvMessage;
    private GridLayout grid;
    private LinearLayout list;
    private Call<?> call;
    private SalesReport lastSales;
    private InventoryReport lastInv;
    private View btnPdf;
    private TextInputLayout tilSearch;
    private TextInputEditText etSearch;
    private SalesReport pending; // the sales report whose extra data is still loading

    private List<ReturnResponse> returns = new ArrayList<>();
    private List<OrderResponse> orders = new ArrayList<>();
    private final Map<String, String> names = new HashMap<>();
    private double gross, refunds;
    private final List<Prod> prods = new ArrayList<>();
    private final List<Ret> retLines = new ArrayList<>();
    private final List<Staff> staffRows = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_reports, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        salesFilters = v.findViewById(R.id.salesFilters);
        customBox = v.findViewById(R.id.customBox);
        btnFrom = v.findViewById(R.id.btnFrom);
        btnTo = v.findViewById(R.id.btnTo);
        tvRange = v.findViewById(R.id.tvRange);
        tvMessage = v.findViewById(R.id.tvMessage);
        grid = v.findViewById(R.id.grid);
        list = v.findViewById(R.id.list);
        btnPdf = v.findViewById(R.id.btnPdf);
        tilSearch = v.findViewById(R.id.tilReportSearch);
        etSearch = v.findViewById(R.id.etReportSearch);
        btnPdf.setOnClickListener(x -> exportPdf());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override
            public void afterTextChanged(Editable e) {
                if (salesMode && lastSales != null) renderSales();
                else if (!salesMode && lastInv != null) renderInventory();
            }
        });

        applyPreset("month");

        MaterialButtonToggleGroup toggle = v.findViewById(R.id.toggleType);
        toggle.check(R.id.btnSales);
        toggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            salesMode = id == R.id.btnSales;
            salesFilters.setVisibility(salesMode ? View.VISIBLE : View.GONE);
            etSearch.setText("");
            tilSearch.setHint(salesMode ? "Search products in this report" : "Search by name, SKU or category");
            load();
        });
        tilSearch.setHint("Search products in this report");

        ChipGroup chips = v.findViewById(R.id.chipPreset);
        chips.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            int id = ids.get(0);
            if (id == R.id.chipMonth) applyPreset("month");
            else if (id == R.id.chipLast) applyPreset("last");
            else if (id == R.id.chipYear) applyPreset("year");
            else applyPreset("custom");
            load();
        });
        btnFrom.setOnClickListener(x -> pick(true));
        btnTo.setOnClickListener(x -> pick(false));
        load();
    }

    @Override
    public void onDestroyView() {
        if (call != null) call.cancel();
        super.onDestroyView();
    }

    // ---------- dates ----------
    private static Calendar cal(long ms) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(ms);
        return c;
    }

    private static long startOfDay(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private static long monthStart(int offset) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.add(Calendar.MONTH, offset);
        return startOfDay(c);
    }

    private void applyPreset(String p) {
        preset = p;
        long today = startOfDay(Calendar.getInstance());
        if (p.equals("month")) {
            from = monthStart(0);
            to = today;
        } else if (p.equals("last")) {
            from = monthStart(-1);
            Calendar c = cal(monthStart(0));
            c.add(Calendar.DAY_OF_YEAR, -1);
            to = c.getTimeInMillis();
        } else if (p.equals("year")) {
            Calendar c = Calendar.getInstance();
            c.set(Calendar.MONTH, Calendar.JANUARY);
            c.set(Calendar.DAY_OF_MONTH, 1);
            from = startOfDay(c);
            to = today;
        }
        customBox.setVisibility(p.equals("custom") ? View.VISIBLE : View.GONE);
        btnFrom.setText("From: " + nice.format(from));
        btnTo.setText("To: " + nice.format(to));
        tvRange.setText(nice.format(from) + " to " + nice.format(to));
    }

    private void pick(final boolean isFrom) {
        Calendar c = cal(isFrom ? from : to);
        new DatePickerDialog(requireContext(), (view, y, m, d) -> {
            Calendar n = Calendar.getInstance();
            n.set(y, m, d);
            if (isFrom) from = startOfDay(n);
            else to = startOfDay(n);
            btnFrom.setText("From: " + nice.format(from));
            btnTo.setText("To: " + nice.format(to));
            tvRange.setText(nice.format(from) + " to " + nice.format(to));
            load();
        }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ---------- loading ----------
    private void message(String text, boolean error) {
        tvMessage.setTextColor(error ? 0xFFFF6B6B : 0xFF9A9AA3);
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
    }

    private void load() {
        if (!isAdded() || getView() == null) return;
        if (call != null) call.cancel();
        grid.removeAllViews();
        list.removeAllViews();
        tilSearch.setVisibility(View.GONE);
        if (salesMode && from > to) {
            message("The From date must be before the To date.", true); btnPdf.setVisibility(View.GONE);
            return;
        }
        message("Loading...", false); btnPdf.setVisibility(View.GONE); lastSales = null; lastInv = null; pending = null;
        if (salesMode) {
            Call<SalesReport> c = ApiClient.get(requireContext())
                    .salesReport(param.format(from), param.format(to));
            call = c;
            c.enqueue(new Callback<SalesReport>() {
                @Override
                public void onResponse(Call<SalesReport> cc, Response<SalesReport> r) {
                    if (!isAdded() || getView() == null) return;
                    if (r.code() == 401) { Session.expire(requireActivity(), true); return; }
                    if (!r.isSuccessful() || r.body() == null) { message(ApiError.message(r), true); return; }
                    pending = r.body();
                    loadExtras(r.body());
                }

                @Override
                public void onFailure(Call<SalesReport> cc, Throwable t) {
                    if (cc.isCanceled() || !isAdded() || getView() == null) return;
                    message(ApiError.NETWORK, true);
                }
            });
        } else {
            Call<InventoryReport> c = ApiClient.get(requireContext()).inventoryReport();
            call = c;
            c.enqueue(new Callback<InventoryReport>() {
                @Override
                public void onResponse(Call<InventoryReport> cc, Response<InventoryReport> r) {
                    if (!isAdded() || getView() == null) return;
                    if (r.code() == 401) { Session.expire(requireActivity(), true); return; }
                    if (!r.isSuccessful() || r.body() == null) { message(ApiError.message(r), true); return; }
                    lastInv = r.body();
                    lastSales = null;
                    renderInventory();
                }

                @Override
                public void onFailure(Call<InventoryReport> cc, Throwable t) {
                    if (cc.isCanceled() || !isAdded() || getView() == null) return;
                    message(ApiError.NETWORK, true);
                }
            });
        }
    }

    /** The sales report arrived. Now load returns, orders and user names (the web page does the same). */
    private void loadExtras(final SalesReport d) {
        ApiClient.get(requireContext()).returnsList().enqueue(new Callback<List<ReturnResponse>>() {
            @Override
            public void onResponse(Call<List<ReturnResponse>> c, Response<List<ReturnResponse>> r) {
                returns = r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<ReturnResponse>();
                loadOrders(d);
            }

            @Override
            public void onFailure(Call<List<ReturnResponse>> c, Throwable t) {
                returns = new ArrayList<>();
                loadOrders(d);
            }
        });
    }

    private void loadOrders(final SalesReport d) {
        if (!isAdded() || getView() == null) return;
        ApiClient.get(requireContext()).orders().enqueue(new Callback<List<OrderResponse>>() {
            @Override
            public void onResponse(Call<List<OrderResponse>> c, Response<List<OrderResponse>> r) {
                orders = r.isSuccessful() && r.body() != null ? r.body() : new ArrayList<OrderResponse>();
                loadNames(d);
            }

            @Override
            public void onFailure(Call<List<OrderResponse>> c, Throwable t) {
                orders = new ArrayList<>();
                loadNames(d);
            }
        });
    }

    private void loadNames(final SalesReport d) {
        if (!isAdded() || getView() == null) return;
        ApiClient.get(requireContext()).users().enqueue(new Callback<List<UserResponse>>() {
            @Override
            public void onResponse(Call<List<UserResponse>> c, Response<List<UserResponse>> r) {
                if (r.isSuccessful() && r.body() != null) {
                    for (UserResponse u : r.body()) {
                        if (u.email != null) names.put(u.email.toLowerCase(Locale.ROOT), u.name);
                    }
                }
                done(d);
            }

            @Override
            public void onFailure(Call<List<UserResponse>> c, Throwable t) {
                done(d);
            }
        });
    }

    private void done(SalesReport d) {
        if (!isAdded() || getView() == null || !salesMode || d != pending) return;
        lastSales = d;
        lastInv = null;
        compute(d);
        renderSales();
    }

    // ---------- figures ----------
    private boolean inRange(String iso) {
        String day = iso == null ? "" : (iso.length() >= 10 ? iso.substring(0, 10) : iso);
        return day.compareTo(param.format(from)) >= 0 && day.compareTo(param.format(to)) <= 0;
    }

    private static String day10(String iso) {
        return iso == null ? "-" : iso.substring(0, Math.min(10, iso.length()));
    }

    private String nameOf(String email) {
        String n = names.get(email);
        return n == null ? email : n;
    }

    /** gross sales, refunds, returned products, net revenue per product, and sales per staff member */
    private void compute(SalesReport d) {
        refunds = 0;
        retLines.clear();
        prods.clear();
        staffRows.clear();

        Map<Long, Long> retQty = new HashMap<>();
        Map<Long, Double> retAmt = new HashMap<>();
        List<ReturnResponse> periodReturns = new ArrayList<>();
        for (ReturnResponse r : returns) {
            if (!inRange(r.createdAt)) continue;
            periodReturns.add(r);
            refunds += r.refundAmount;
            if (r.items == null) continue;
            for (ReturnLine l : r.items) {
                Ret x = new Ret();
                x.id = r.id; x.date = r.createdAt; x.orderId = r.orderId; x.name = l.productName;
                x.qty = l.quantity; x.price = l.unitPrice; x.refund = l.subtotal; x.reason = r.reason;
                retLines.add(x);
                Long q = retQty.get(l.productId);
                retQty.put(l.productId, (q == null ? 0 : q) + l.quantity);
                Double a = retAmt.get(l.productId);
                retAmt.put(l.productId, (a == null ? 0 : a) + l.subtotal);
            }
        }

        if (d.byProduct != null) {
            for (ProductSalesRow p : d.byProduct) {
                Prod x = new Prod();
                x.id = p.productId; x.name = p.productName; x.sku = p.sku; x.sold = p.quantitySold;
                Long q = retQty.get(p.productId);
                Double a = retAmt.get(p.productId);
                x.returned = q == null ? 0 : q;
                x.net = p.revenue - (a == null ? 0 : a);
                prods.add(x);
            }
        }

        gross = d.totalSales + refunds;

        Map<Long, String> owner = new HashMap<>();
        for (OrderResponse o : orders) owner.put(o.id, o.createdBy == null ? "unknown" : o.createdBy.toLowerCase(Locale.ROOT));
        Map<String, Staff> by = new HashMap<>();
        for (OrderResponse o : orders) {
            if (!inRange(o.createdAt)) continue;
            Staff s = staff(by, o.createdBy == null ? "unknown" : o.createdBy.toLowerCase(Locale.ROOT));
            if ("CONFIRMED".equals(o.status)) {
                s.orders++;
                s.sales += o.totalAmount;
            } else {
                s.cancelled++;
            }
        }
        for (ReturnResponse r : periodReturns) {
            String k = owner.get(r.orderId);
            staff(by, k == null ? "unknown" : k).refunds += r.refundAmount;
        }
        for (Staff s : by.values()) {
            s.name = nameOf(s.email);
            s.net = s.sales - s.refunds;
            staffRows.add(s);
        }
        Collections.sort(staffRows, (a, b) -> Double.compare(b.net, a.net));
    }

    private static Staff staff(Map<String, Staff> by, String email) {
        Staff s = by.get(email);
        if (s == null) {
            s = new Staff();
            s.email = email;
            by.put(email, s);
        }
        return s;
    }

    // ---------- screens ----------
    private String keyword() {
        return etSearch.getText() == null ? "" : etSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
    }

    private void renderSales() {
        SalesReport d = lastSales;
        if (d == null) return;
        tvMessage.setVisibility(View.GONE);
        grid.removeAllViews();
        list.removeAllViews();
        btnPdf.setVisibility(View.VISIBLE);
        tilSearch.setVisibility(View.VISIBLE);
        addStat("Net sales", Fmt.money(d.totalSales), 0xFFFF4D1C);
        addStat("Gross sales", Fmt.money(gross), 0);
        addStat("Refunds (returns)", Fmt.money(refunds), refunds > 0 ? 0xFFFF6B6B : 0);
        addStat("Orders", String.valueOf(d.totalOrders), 0);
        addStat("Average order", Fmt.money(d.averageOrderValue), 0);

        String k = keyword();
        List<Prod> shown = new ArrayList<>();
        for (Prod p : prods) {
            if (k.isEmpty() || p.name.toLowerCase(Locale.ROOT).contains(k)
                    || (p.sku != null && p.sku.toLowerCase(Locale.ROOT).contains(k))) shown.add(p);
        }
        addHeader("Sales by product \u00B7 " + shown.size() + " items");
        if (shown.isEmpty()) addNote("No sales in this period.");
        for (Prod p : shown) {
            Row row = new Row();
            row.title = p.name;
            row.sub = p.sku + " \u00B7 Sold " + p.sold + " \u00B7 Returned " + p.returned;
            row.right = Fmt.money(p.net);
            row.rightSub = "net revenue";
            addRow(row);
        }

        addHeader("Returned products \u00B7 " + retLines.size() + " items");
        if (retLines.isEmpty()) addNote("No returns in this period.");
        for (Ret r : retLines) {
            Row row = new Row();
            row.title = r.name;
            row.sub = "Return #" + r.id + " \u00B7 Order #" + r.orderId + " \u00B7 " + day10(r.date)
                    + (r.reason == null || r.reason.isEmpty() ? "" : " \u00B7 " + r.reason);
            row.right = Fmt.money(r.refund);
            row.rightSub = r.qty + " \u00D7 " + Fmt.money(r.price);
            row.warn = true;
            addRow(row);
        }

        addHeader("Staff performance \u00B7 " + staffRows.size() + " staff");
        if (staffRows.isEmpty()) addNote("No staff activity in this period.");
        for (Staff s : staffRows) {
            Row row = new Row();
            row.title = s.name;
            row.sub = s.orders + " orders \u00B7 Sales " + Fmt.money(s.sales) + " \u00B7 Refunds " + Fmt.money(s.refunds);
            row.right = Fmt.money(s.net);
            row.rightSub = s.cancelled + " cancelled";
            addRow(row);
        }
    }

    private void renderInventory() {
        InventoryReport d = lastInv;
        if (d == null) return;
        tvMessage.setVisibility(View.GONE);
        grid.removeAllViews();
        list.removeAllViews();
        btnPdf.setVisibility(View.VISIBLE);
        tilSearch.setVisibility(View.VISIBLE);
        addStat("Products", String.valueOf(d.totalProducts), 0);
        addStat("Units in stock", String.valueOf(d.totalUnits), 0);
        addStat("Stock value (cost)", Fmt.money(d.stockValueAtCost), 0);
        addStat("Stock value (selling)", Fmt.money(d.stockValueAtSelling), 0xFFFF4D1C);
        addStat("Low stock items", String.valueOf(d.lowStockCount), d.lowStockCount > 0 ? 0xFFFF6B6B : 0);
        if (d.products == null || d.products.isEmpty()) {
            message("No products found.", false);
            return;
        }
        String k = keyword();
        int count = 0;
        for (Product p : d.products) {
            if (!k.isEmpty() && !(p.name.toLowerCase(Locale.ROOT).contains(k)
                    || p.sku.toLowerCase(Locale.ROOT).contains(k)
                    || (p.categoryName != null && p.categoryName.toLowerCase(Locale.ROOT).contains(k)))) continue;
            count++;
            Row row = new Row();
            row.title = p.name;
            row.sub = p.sku + " \u00B7 " + (p.categoryName == null ? "No category" : p.categoryName)
                    + " \u00B7 cost " + Fmt.money(p.purchasePrice);
            row.right = "Stock " + p.currentStock;
            row.rightSub = p.lowStock ? "Low stock" : Fmt.money(p.sellingPrice);
            row.warn = p.lowStock;
            addRow(row);
        }
        if (count == 0) message("No products found.", false);
    }

    // ---------- PDF ----------
    private static PdfExporter.Table section(String title, String[] headers, float[] weights, boolean[] right) {
        PdfExporter.Table t = new PdfExporter.Table();
        t.title = title;
        t.headers = headers;
        t.weights = weights;
        t.right = right;
        return t;
    }

    private void exportPdf() {
        PdfExporter.Table t = new PdfExporter.Table();
        String name;
        if (lastSales != null) {
            t.title = "Sales report";
            t.subtitle = "Period: " + nice.format(from) + " to " + nice.format(to);
            t.summary.add(new String[]{"Gross sales", Fmt.money(gross)});
            t.summary.add(new String[]{"Refunds (returns)", Fmt.money(refunds)});
            t.summary.add(new String[]{"Net sales", Fmt.money(lastSales.totalSales)});
            t.summary.add(new String[]{"Orders", String.valueOf(lastSales.totalOrders)});
            t.summary.add(new String[]{"Average order", Fmt.money(lastSales.averageOrderValue)});
            t.headers = new String[]{"Product", "SKU", "Sold", "Returned", "Net revenue"};
            t.weights = new float[]{4f, 2.2f, 1f, 1.3f, 2.3f};
            t.right = new boolean[]{false, false, true, true, true};
            for (Prod p : prods) {
                t.rows.add(new String[]{p.name, p.sku, String.valueOf(p.sold), String.valueOf(p.returned), Fmt.money(p.net)});
            }
            if (!retLines.isEmpty()) {
                PdfExporter.Table rt = section("Returned products",
                        new String[]{"Return", "Date", "Order", "Product", "Qty", "Price", "Refund", "Reason"},
                        new float[]{1f, 1.7f, 1f, 3f, 0.8f, 1.7f, 1.7f, 2.2f},
                        new boolean[]{false, false, false, false, true, true, true, false});
                for (Ret r : retLines) {
                    String day = day10(r.date);
                    rt.rows.add(new String[]{"#" + r.id, day, "#" + r.orderId, r.name, String.valueOf(r.qty),
                            Fmt.money(r.price), Fmt.money(r.refund), r.reason == null || r.reason.isEmpty() ? "-" : r.reason});
                }
                t.extra.add(rt);
            }
            if (!staffRows.isEmpty()) {
                PdfExporter.Table st = section("Staff performance",
                        new String[]{"Staff", "Orders", "Sales", "Refunds", "Net sales", "Cancelled"},
                        new float[]{3f, 1.2f, 2.2f, 2.2f, 2.2f, 1.5f},
                        new boolean[]{false, true, true, true, true, true});
                for (Staff s : staffRows) {
                    st.rows.add(new String[]{s.name, String.valueOf(s.orders), Fmt.money(s.sales),
                            Fmt.money(s.refunds), Fmt.money(s.net), String.valueOf(s.cancelled)});
                }
                t.extra.add(st);
            }
            name = "sales-report_" + param.format(from) + "_to_" + param.format(to) + ".pdf";
        } else if (lastInv != null) {
            t.title = "Inventory report";
            t.subtitle = "Current stock levels and value";
            t.summary.add(new String[]{"Products", String.valueOf(lastInv.totalProducts)});
            t.summary.add(new String[]{"Units in stock", String.valueOf(lastInv.totalUnits)});
            t.summary.add(new String[]{"Stock value (cost)", Fmt.money(lastInv.stockValueAtCost)});
            t.summary.add(new String[]{"Stock value (selling)", Fmt.money(lastInv.stockValueAtSelling)});
            t.summary.add(new String[]{"Low stock items", String.valueOf(lastInv.lowStockCount)});
            t.headers = new String[]{"Name", "SKU", "Category", "Stock", "Min", "Cost", "Price"};
            t.weights = new float[]{3.2f, 2f, 2f, 1f, 0.9f, 1.8f, 1.8f};
            t.right = new boolean[]{false, false, false, true, true, true, true};
            if (lastInv.products != null) {
                for (Product p : lastInv.products) {
                    t.rows.add(new String[]{p.name, p.sku, p.categoryName == null ? "-" : p.categoryName,
                            String.valueOf(p.currentStock), String.valueOf(p.minimumStock),
                            Fmt.money(p.purchasePrice), Fmt.money(p.sellingPrice)});
                }
            }
            name = "inventory-report_" + param.format(System.currentTimeMillis()) + ".pdf";
        } else {
            return;
        }
        PdfExporter.export(requireActivity(), name, t);
    }

    // ---------- view helpers ----------
    private void addRow(Row row) {
        View v = getLayoutInflater().inflate(R.layout.item_row, list, false);
        RowAdapter.bind(v, row);
        list.addView(v);
    }

    private void addHeader(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextSize(16);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, pad * 3, pad, pad);
        list.addView(tv);
    }

    private void addNote(String text) {
        TextView tv = new TextView(requireContext());
        tv.setText(text);
        tv.setTextColor(0xFF9A9AA3);
        int pad = (int) (6 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, 0, pad, pad);
        list.addView(tv);
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