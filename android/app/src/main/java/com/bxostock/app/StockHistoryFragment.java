package com.bxostock.app;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.Movement;
import com.bxostock.app.util.BxoDialog;
import com.bxostock.app.util.Fmt;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;

/** Stock movement history (stock in / out, orders, returns, purchases). Same data as the web History tab. */
public class StockHistoryFragment extends BaseListFragment<Movement> {
    @Override protected String searchHint() { return "Search product, SKU, reason or user"; }

    @Override
    protected Call<List<Movement>> request(String kw) {
        return ApiClient.get(requireContext()).movements(null);
    }

    @Override
    protected boolean matches(Movement m, String k) {
        if (k.isEmpty()) return true;
        String hay = (m.productName + " " + m.sku + " " + m.reason + " " + m.userName + " " + m.type)
                .toLowerCase(Locale.ROOT);
        return hay.contains(k);
    }

    @Override
    protected Row toRow(Movement m) {
        boolean in = "STOCK_IN".equals(m.type);
        Row r = new Row();
        r.title = m.productName;
        r.sub = (m.reason == null || m.reason.isEmpty() ? "-" : m.reason) + " · " + m.userName;
        r.right = (in ? "+" : "-") + m.quantity;
        r.rightSub = Fmt.date(m.createdAt);
        r.warn = !in;
        return r;
    }

    @Override
    protected void onItemClick(Movement m) {
        boolean in = "STOCK_IN".equals(m.type);
        new BxoDialog(requireContext())
                .setTitle(m.productName)
                .setMessage("SKU: " + m.sku
                        + "\nType: " + (in ? "Stock in" : "Stock out")
                        + "\nQuantity: " + m.quantity
                        + "\nStock: " + m.previousStock + " → " + m.updatedStock
                        + "\nReason: " + (m.reason == null || m.reason.isEmpty() ? "-" : m.reason)
                        + "\nBy: " + m.userName
                        + "\nDate: " + Fmt.date(m.createdAt))
                .setPositiveButton("Close", null)
                .show();
    }
}
