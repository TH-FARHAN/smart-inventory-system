package com.bxostock.app;

import com.bxostock.app.util.BxoDialog;

import android.app.Activity;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.OrderResponse;
import com.bxostock.app.model.ReturnRequest;
import com.bxostock.app.model.ReturnResponse;
import com.bxostock.app.model.Returnable;
import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Fmt;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ReturnDialog {
    private static final String[] REASONS = {"Damaged item", "Wrong item", "Expired item", "Customer changed mind", "Other"};

    public static void show(final Activity a, final OrderResponse o, final Runnable onDone) {
        ApiClient.get(a).returnable(o.id).enqueue(new Callback<List<Returnable>>() {
            @Override
            public void onResponse(Call<List<Returnable>> c, Response<List<Returnable>> r) {
                if (a.isFinishing()) return;
                if (!r.isSuccessful() || r.body() == null) {
                    Toast.makeText(a, ApiError.message(r), Toast.LENGTH_LONG).show();
                    return;
                }
                build(a, o, r.body(), onDone);
            }

            @Override
            public void onFailure(Call<List<Returnable>> c, Throwable t) {
                Toast.makeText(a, ApiError.NETWORK, Toast.LENGTH_LONG).show();
            }
        });
    }

    private static void build(final Activity a, final OrderResponse o, final List<Returnable> lines, final Runnable onDone) {
        float d = a.getResources().getDisplayMetrics().density;
        int pad = (int) (20 * d);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(pad, pad / 2, pad, 0);

        final List<Returnable> shown = new ArrayList<>();
        final List<EditText> inputs = new ArrayList<>();
        for (Returnable l : lines) {
            if (l.remaining <= 0) continue;
            TextView t = new TextView(a);
            t.setText(l.productName + "  (" + Fmt.money(l.unitPrice) + " each, can return " + l.remaining + ")");
            t.setPadding(0, (int) (10 * d), 0, 0);
            box.addView(t);
            EditText e = new EditText(a);
            e.setInputType(InputType.TYPE_CLASS_NUMBER);
            e.setHint("0");
            box.addView(e);
            shown.add(l);
            inputs.add(e);
        }
        if (shown.isEmpty()) {
            Toast.makeText(a, "Nothing left to return on this order.", Toast.LENGTH_LONG).show();
            return;
        }
        TextView rl = new TextView(a);
        rl.setText("Reason");
        rl.setPadding(0, (int) (14 * d), 0, 0);
        box.addView(rl);
        final Spinner sp = new Spinner(a);
        sp.setAdapter(new ArrayAdapter<>(a, android.R.layout.simple_spinner_dropdown_item, REASONS));
        box.addView(sp);
        ScrollView sv = new ScrollView(a);
        sv.addView(box);

        final AlertDialog dlg = new BxoDialog(a)
                .setTitle("Return items \u00B7 Order #" + o.id)
                .setView(sv)
                .setPositiveButton("Confirm return", null)
                .setNegativeButton("Cancel", null)
                .create();
        dlg.show();
        dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x -> {
            ReturnRequest req = new ReturnRequest();
            req.orderId = o.id;
            req.reason = (String) sp.getSelectedItem();
            for (int i = 0; i < shown.size(); i++) {
                String s = inputs.get(i).getText().toString().trim();
                int q = 0;
                try {
                    q = s.isEmpty() ? 0 : Integer.parseInt(s);
                } catch (NumberFormatException ex) {
                    q = -1;
                }
                if (q < 0 || q > shown.get(i).remaining) {
                    inputs.get(i).setError("0 to " + shown.get(i).remaining);
                    return;
                }
                if (q > 0) req.items.add(new ReturnRequest.Item(shown.get(i).productId, q));
            }
            if (req.items.isEmpty()) {
                Toast.makeText(a, "Enter a quantity for at least one item.", Toast.LENGTH_SHORT).show();
                return;
            }
            ApiClient.get(a).createReturn(req).enqueue(new Callback<ReturnResponse>() {
                @Override
                public void onResponse(Call<ReturnResponse> c, Response<ReturnResponse> r) {
                    if (r.isSuccessful() && r.body() != null) {
                        dlg.dismiss();
                        Toast.makeText(a, "Return recorded. Refund " + Fmt.money(r.body().refundAmount)
                                + ". Stock restored.", Toast.LENGTH_LONG).show();
                        if (onDone != null) onDone.run();
                    } else {
                        Toast.makeText(a, ApiError.message(r), Toast.LENGTH_LONG).show();
                    }
                }

                @Override
                public void onFailure(Call<ReturnResponse> c, Throwable t) {
                    Toast.makeText(a, ApiError.NETWORK, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}