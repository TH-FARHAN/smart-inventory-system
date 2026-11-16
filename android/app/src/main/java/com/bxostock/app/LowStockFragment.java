package com.bxostock.app;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.Product;
import com.bxostock.app.util.BxoDialog;
import com.bxostock.app.util.Fmt;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;

/** Products at or below their minimum stock (same data as the web Inventory > Low stock tab). */
public class LowStockFragment extends BaseListFragment<Product> {
    @Override protected String searchHint() { return "Search name or SKU"; }

    @Override
    protected Call<List<Product>> request(String kw) {
        return ApiClient.get(requireContext()).lowStock();
    }

    @Override
    protected boolean matches(Product p, String k) {
        if (!p.active) return false;
        if (k.isEmpty()) return true;
        return p.name.toLowerCase(Locale.ROOT).contains(k) || p.sku.toLowerCase(Locale.ROOT).contains(k);
    }

    @Override
    protected Row toRow(Product p) {
        Row r = new Row();
        r.title = p.name;
        r.sub = p.sku + " \u00B7 " + (p.categoryName == null ? "No category" : p.categoryName);
        r.right = "Stock " + p.currentStock;
        r.rightSub = "Minimum " + p.minimumStock;
        r.warn = true;
        return r;
    }

    @Override
    protected void onItemClick(Product p) {
        new BxoDialog(requireContext())
                .setTitle(p.name)
                .setMessage("SKU: " + p.sku
                        + "\nCategory: " + (p.categoryName == null ? "-" : p.categoryName)
                        + "\nSupplier: " + (p.supplierName == null ? "-" : p.supplierName)
                        + "\nIn stock: " + p.currentStock
                        + "\nMinimum stock: " + p.minimumStock
                        + "\nPurchase price: " + Fmt.money(p.purchasePrice))
                .setPositiveButton("Close", null)
                .show();
    }
}