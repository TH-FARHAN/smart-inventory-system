package com.bxostock.app;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.OrderLine;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.PdfExporter;
import com.bxostock.app.util.Session;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.Collections;
import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OrdersFragment extends BaseListFragment<OrderResponse> {
    private final ActivityResultLauncher<Intent> newOrder = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent d = result.getData();
                if (result.getResultCode() == Activity.RESULT_OK && d != null && getView() != null) {
                    final long id = d.getLongExtra("orderId", -1);
                    double total = d.getDoubleExtra("total", 0);
                    Snackbar.make(getView(), "Order #" + id + " placed \u00B7 " + Fmt.money(total),
                                    Snackbar.LENGTH_LONG)
                            .setAction("UNDO", x -> cancel(id, "Order #" + id + " undone. Stock restored."))
                            .show();
                }
            });

    @Override protected String searchHint() { return "Search order no., customer or staff"; }
    @Override protected String actionLabel() { return "New order"; }
    @Override protected String filterLabel() { return "Hide cancelled orders"; }

    @Override
    protected void onAction() {
        newOrder.launch(new Intent(requireContext(), NewOrderActivity.class));
    }

    @Override
    protected Call<List<OrderResponse>> request(String kw) {
        return ApiClient.get(requireContext()).orders();
    }

    @Override
    protected boolean matches(OrderResponse o, String k) {
        if (cbFilter.isChecked() && "CANCELLED".equals(o.status)) return false;
        if (k.isEmpty()) return true;
        String hay = (o.id + " " + o.customerName + " " + o.createdBy).toLowerCase();
        return hay.contains(k);
    }

    @Override
    protected void sort(List<OrderResponse> items) {
        Collections.sort(items, (a, b) -> Long.compare(b.id, a.id));
    }

    @Override
    protected Row toRow(OrderResponse o) {
        Row r = new Row();
        r.title = "#" + o.id + " \u00B7 " + o.customerName;
        r.sub = Fmt.date(o.createdAt);
        r.right = Fmt.money(o.totalAmount);
        r.rightSub = o.status;
        r.warn = "CANCELLED".equals(o.status);
        return r;
    }

    @Override
    protected void onItemClick(final OrderResponse o) {
        StringBuilder sb = new StringBuilder();
        sb.append("Customer: ").append(o.customerName)
                .append("\nStatus: ").append(o.status)
                .append("\nDate: ").append(Fmt.date(o.createdAt))
                .append("\nBy: ").append(o.createdBy).append("\n\n");
        if (o.items != null) {
            for (OrderLine l : o.items) {
                sb.append(l.quantity).append(" \u00D7 ").append(l.productName)
                        .append("   ").append(Fmt.money(l.subtotal)).append("\n");
            }
        }
        sb.append("\nTotal: ").append(Fmt.money(o.totalAmount));
        MaterialAlertDialogBuilder b = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Order #" + o.id)
                .setMessage(sb.toString())
                .setPositiveButton("Close", null);
        if ("CONFIRMED".equals(o.status)) {
            b.setNegativeButton("Cancel order", (d, w) -> confirmCancel(o));
        }
        b.setNeutralButton("More", (d, w) -> showMore(o));
        b.show();
    }

    private void showMore(final OrderResponse o) {
        final boolean canReturn = "CONFIRMED".equals(o.status);
        String[] items = canReturn ? new String[]{"Bill PDF", "Return items"} : new String[]{"Bill PDF"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Order #" + o.id)
                .setItems(items, (d, which) -> {
                    if (which == 0) exportBill(o);
                    else ReturnDialog.show(requireActivity(), o, this::reload);
                })
                .show();
    }

    private void exportBill(OrderResponse o) {
        PdfExporter.Table t = new PdfExporter.Table();
        t.title = "Bill #" + o.id;
        t.subtitle = "Supermarket bill";
        t.summary.add(new String[]{"Customer", o.customerName == null ? "-" : o.customerName});
        t.summary.add(new String[]{"Date", Fmt.date(o.createdAt)});
        t.summary.add(new String[]{"Served by", o.createdBy == null ? "-" : o.createdBy});
        t.summary.add(new String[]{"Status", o.status == null ? "-" : o.status});
        t.headers = new String[]{"Item", "Qty", "Rate", "Amount"};
        t.weights = new float[]{5f, 1f, 2f, 2.5f};
        t.right = new boolean[]{false, true, true, true};
        if (o.items != null) {
            for (OrderLine l : o.items) {
                t.rows.add(new String[]{l.productName, String.valueOf(l.quantity),
                        Fmt.money(l.unitPrice), Fmt.money(l.subtotal)});
            }
        }
        t.rows.add(new String[]{"TOTAL", "", "", Fmt.money(o.totalAmount)});
        PdfExporter.export(requireActivity(), "bill_order_" + o.id + ".pdf", t);
    }

    private void confirmCancel(final OrderResponse o) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cancel order #" + o.id + "?")
                .setMessage("The stock will be put back.")
                .setNegativeButton("Keep order", null)
                .setPositiveButton("Cancel order", (d, w) ->
                        cancel(o.id, "Order #" + o.id + " cancelled. Stock restored."))
                .show();
    }

    private void cancel(long id, final String okMessage) {
        ApiClient.get(requireContext()).cancelOrder(id).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> c, Response<ResponseBody> r) {
                if (!isAdded()) return;
                if (r.isSuccessful()) {
                    Toast.makeText(requireContext(), okMessage, Toast.LENGTH_SHORT).show();
                    reload();
                } else if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                } else {
                    Toast.makeText(requireContext(), ApiError.message(r), Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> c, Throwable t) {
                if (isAdded()) Toast.makeText(requireContext(), ApiError.NETWORK, Toast.LENGTH_LONG).show();
            }
        });
    }
}