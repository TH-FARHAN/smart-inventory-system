package com.bxostock.app;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.ReturnLine;
import com.bxostock.app.model.ReturnResponse;
import com.bxostock.app.util.Fmt;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;

public class ReturnsFragment extends BaseListFragment<ReturnResponse> {
    @Override protected String searchHint() { return "Search return, order, customer or reason"; }

    @Override
    protected Call<List<ReturnResponse>> request(String kw) {
        return ApiClient.get(requireContext()).returnsList();
    }

    @Override
    protected boolean matches(ReturnResponse r, String k) {
        if (k.isEmpty()) return true;
        String hay = (r.id + " " + r.orderId + " " + r.customerName + " " + r.reason + " " + r.createdBy)
                .toLowerCase(Locale.ROOT);
        return hay.contains(k);
    }

    @Override
    protected Row toRow(ReturnResponse r) {
        Row row = new Row();
        row.title = "Return #" + r.id + " \u00B7 Order #" + r.orderId;
        row.sub = r.customerName + " \u00B7 " + r.reason;
        row.right = Fmt.money(r.refundAmount);
        row.rightSub = Fmt.date(r.createdAt);
        return row;
    }

    @Override
    protected void onItemClick(ReturnResponse r) {
        StringBuilder sb = new StringBuilder();
        sb.append("Customer: ").append(r.customerName)
                .append("\nOrder: #").append(r.orderId)
                .append("\nDate: ").append(Fmt.date(r.createdAt))
                .append("\nBy: ").append(r.createdBy)
                .append("\nReason: ").append(r.reason).append("\n\n");
        if (r.items != null) {
            for (ReturnLine l : r.items) {
                sb.append(l.quantity).append(" \u00D7 ").append(l.productName)
                        .append("   ").append(Fmt.money(l.subtotal)).append("\n");
            }
        }
        sb.append("\nRefund: ").append(Fmt.money(r.refundAmount));
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Return #" + r.id)
                .setMessage(sb.toString())
                .setPositiveButton("Close", null)
                .show();
    }
}