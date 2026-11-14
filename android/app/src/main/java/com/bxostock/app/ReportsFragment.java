package com.bxostock.app;

import android.app.DatePickerDialog;
import android.os.Bundle;
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
import com.bxostock.app.model.Product;
import com.bxostock.app.model.ProductSalesRow;
import com.bxostock.app.model.SalesReport;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.PdfExporter;
import com.bxostock.app.util.Session;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReportsFragment extends Fragment {
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
        btnPdf.setOnClickListener(x -> exportPdf());

        applyPreset("month");

        MaterialButtonToggleGroup toggle = v.findViewById(R.id.toggleType);
        toggle.check(R.id.btnSales);
        toggle.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            salesMode = id == R.id.btnSales;
            salesFilters.setVisibility(salesMode ? View.VISIBLE : View.GONE);
            load();
        });

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
        if (salesMode && from > to) {
            message("The From date must be before the To date.", true); btnPdf.setVisibility(View.GONE);
            return;
        }
        message("Loading...", false); btnPdf.setVisibility(View.GONE); lastSales = null; lastInv = null;
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
                    showSales(r.body());
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
                    showInventory(r.body());
                }

                @Override
                public void onFailure(Call<InventoryReport> cc, Throwable t) {
                    if (cc.isCanceled() || !isAdded() || getView() == null) return;
                    message(ApiError.NETWORK, true);
                }
            });
        }
    }

    private void showSales(SalesReport d) {
        tvMessage.setVisibility(View.GONE);
        grid.removeAllViews();
        list.removeAllViews();
        addStat("Total sales", Fmt.money(d.totalSales), 0xFFFF4D1C); lastSales = d; lastInv = null; btnPdf.setVisibility(View.VISIBLE);
        addStat("Orders", String.valueOf(d.totalOrders), 0);
        addStat("Average order", Fmt.money(d.averageOrderValue), 0);
        if (d.byProduct == null || d.byProduct.isEmpty()) {
            message("No sales in this period.", false);
            return;
        }
        for (ProductSalesRow p : d.byProduct) {
            Row row = new Row();
            row.title = p.productName;
            row.sub = p.sku + " \u00B7 " + p.quantitySold + " sold";
            row.right = Fmt.money(p.revenue);
            addRow(row);
        }
    }

    private void showInventory(InventoryReport d) {
        tvMessage.setVisibility(View.GONE);
        grid.removeAllViews();
        list.removeAllViews();
        addStat("Products", String.valueOf(d.totalProducts), 0); lastInv = d; lastSales = null; btnPdf.setVisibility(View.VISIBLE);
        addStat("Units in stock", String.valueOf(d.totalUnits), 0);
        addStat("Stock value (cost)", Fmt.money(d.stockValueAtCost), 0);
        addStat("Stock value (selling)", Fmt.money(d.stockValueAtSelling), 0xFFFF4D1C);
        addStat("Low stock items", String.valueOf(d.lowStockCount), d.lowStockCount > 0 ? 0xFFFF6B6B : 0);
        if (d.products == null || d.products.isEmpty()) {
            message("No products found.", false);
            return;
        }
        for (Product p : d.products) {
            Row row = new Row();
            row.title = p.name;
            row.sub = p.sku + " \u00B7 " + (p.categoryName == null ? "No category" : p.categoryName)
                    + " \u00B7 cost " + Fmt.money(p.purchasePrice);
            row.right = "Stock " + p.currentStock;
            row.rightSub = p.lowStock ? "Low stock" : Fmt.money(p.sellingPrice);
            row.warn = p.lowStock;
            addRow(row);
        }
    }

    private void exportPdf() {
        PdfExporter.Table t = new PdfExporter.Table();
        String name;
        if (lastSales != null) {
            t.title = "Sales report";
            t.subtitle = "Period: " + nice.format(from) + " to " + nice.format(to);
            t.summary.add(new String[]{"Total sales", Fmt.money(lastSales.totalSales)});
            t.summary.add(new String[]{"Orders", String.valueOf(lastSales.totalOrders)});
            t.summary.add(new String[]{"Average order", Fmt.money(lastSales.averageOrderValue)});
            t.headers = new String[]{"Product", "SKU", "Units sold", "Revenue"};
            t.weights = new float[]{4f, 2.2f, 1.5f, 2.3f};
            t.right = new boolean[]{false, false, true, true};
            if (lastSales.byProduct != null) {
                for (ProductSalesRow p : lastSales.byProduct) {
                    t.rows.add(new String[]{p.productName, p.sku, String.valueOf(p.quantitySold), Fmt.money(p.revenue)});
                }
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

    private void addRow(Row row) {
        View v = getLayoutInflater().inflate(R.layout.item_row, list, false);
        RowAdapter.bind(v, row);
        list.addView(v);
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