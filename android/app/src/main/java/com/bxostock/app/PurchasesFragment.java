package com.bxostock.app;

import com.bxostock.app.util.BxoDialog;

import android.content.Intent;
import android.widget.Toast;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.PurchaseLine;
import com.bxostock.app.model.PurchaseResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.Session;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PurchasesFragment extends BaseListFragment<PurchaseResponse> {
    @Override protected String searchHint() { return "Search number, supplier or status"; }
    @Override protected String actionLabel() { return "New"; }

    @Override
    protected void onAction() {
        startActivity(new Intent(requireContext(), NewPurchaseActivity.class));
    }

    @Override
    protected Call<List<PurchaseResponse>> request(String kw) {
        return ApiClient.get(requireContext()).purchases();
    }

    @Override
    protected boolean matches(PurchaseResponse p, String k) {
        if (k.isEmpty()) return true;
        return (p.id + " " + p.supplierName + " " + p.status).toLowerCase(Locale.ROOT).contains(k);
    }

    @Override
    protected Row toRow(PurchaseResponse p) {
        Row r = new Row();
        r.title = "#" + p.id + " \u00B7 " + p.supplierName;
        r.sub = Fmt.date(p.createdAt) + " \u00B7 " + (p.items == null ? 0 : p.items.size()) + " item(s)";
        r.right = Fmt.money(p.totalCost);
        r.rightSub = p.status;
        r.warn = "CANCELLED".equals(p.status);
        return r;
    }

    @Override
    protected void onItemClick(final PurchaseResponse p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Supplier: ").append(p.supplierName)
                .append("\nStatus: ").append(p.status)
                .append("\nCreated: ").append(Fmt.date(p.createdAt)).append(" by ").append(p.createdBy);
        if (p.receivedAt != null) sb.append("\nReceived: ").append(Fmt.date(p.receivedAt));
        sb.append("\n\n");
        if (p.items != null) {
            for (PurchaseLine l : p.items) {
                sb.append(l.quantity).append(" \u00D7 ").append(l.productName)
                        .append("  @ ").append(Fmt.money(l.unitCost))
                        .append("  = ").append(Fmt.money(l.subtotal)).append("\n");
            }
        }
        sb.append("\nTotal cost: ").append(Fmt.money(p.totalCost));
        MaterialAlertDialogBuilder b = new BxoDialog(requireContext())
                .setTitle("Purchase #" + p.id)
                .setMessage(sb.toString())
                .setNeutralButton("Close", null);
        if ("PENDING".equals(p.status)) {
            b.setPositiveButton("Receive stock", (d, w) -> confirm(p, true));
            b.setNegativeButton("Cancel order", (d, w) -> confirm(p, false));
        }
        b.show();
    }

    private void confirm(final PurchaseResponse p, final boolean receive) {
        new BxoDialog(requireContext())
                .setTitle(receive ? "Receive stock?" : "Cancel purchase #" + p.id + "?")
                .setMessage(receive ? "The stock of every item will increase." : "This cannot be undone.")
                .setNegativeButton("Back", null)
                .setPositiveButton(receive ? "Receive" : "Cancel order", (d, w) -> {
                    Call<PurchaseResponse> call = receive
                            ? ApiClient.get(requireContext()).receivePurchase(p.id)
                            : ApiClient.get(requireContext()).cancelPurchase(p.id);
                    call.enqueue(new Callback<PurchaseResponse>() {
                        @Override
                        public void onResponse(Call<PurchaseResponse> c, Response<PurchaseResponse> r) {
                            if (!isAdded()) return;
                            if (r.isSuccessful()) {
                                Toast.makeText(requireContext(), receive ? "Stock received and updated." : "Purchase cancelled.",
                                        Toast.LENGTH_SHORT).show();
                                reload();
                            } else if (r.code() == 401) {
                                Session.expire(requireActivity(), true);
                            } else {
                                Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<PurchaseResponse> c, Throwable t) {
                            if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .show();
    }
}