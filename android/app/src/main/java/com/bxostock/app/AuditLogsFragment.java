package com.bxostock.app;

import android.widget.Toast;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.AuditLog;
import com.bxostock.app.util.Fmt;
import com.bxostock.app.util.PdfExporter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;

public class AuditLogsFragment extends BaseListFragment<AuditLog> {
    private List<AuditLog> shown = new ArrayList<>();

    @Override protected String searchHint() { return "Search user, action, details or IP"; }
    @Override protected String actionLabel() { return "PDF"; }

    @Override
    protected Call<List<AuditLog>> request(String kw) {
        return ApiClient.get(requireContext()).auditLogs();
    }

    private static String nz(String s) { return s == null ? "" : s; }

    @Override
    protected boolean matches(AuditLog l, String k) {
        if (k.isEmpty()) return true;
        String hay = (nz(l.actor) + " " + nz(l.action) + " " + nz(l.details) + " " + nz(l.ipAddress))
                .toLowerCase(Locale.ROOT);
        return hay.contains(k);
    }

    // called once per reload with the filtered list, so the PDF matches what is on screen
    @Override
    protected void sort(List<AuditLog> items) {
        shown = new ArrayList<>(items);
    }

    private static boolean bad(String a) {
        return a.contains("FAILED") || a.contains("LOCKED") || a.contains("BLOCKED")
                || a.contains("LIMITED") || a.contains("DEACTIVATED");
    }

    @Override
    protected Row toRow(AuditLog l) {
        Row r = new Row();
        r.title = nz(l.action);
        r.sub = nz(l.actor) + (nz(l.details).isEmpty() ? "" : " \u00B7 " + l.details);
        r.right = Fmt.date(l.createdAt);
        r.warn = bad(nz(l.action));
        return r;
    }

    @Override
    protected void onItemClick(AuditLog l) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(nz(l.action))
                .setMessage("User: " + nz(l.actor)
                        + "\nTime: " + Fmt.date(l.createdAt)
                        + "\nIP: " + (nz(l.ipAddress).isEmpty() ? "-" : l.ipAddress)
                        + "\n\n" + (nz(l.details).isEmpty() ? "No details." : l.details))
                .setPositiveButton("Close", null)
                .show();
    }

    @Override
    protected void onAction() {
        if (shown.isEmpty()) {
            Toast.makeText(requireContext(), "Nothing to export yet.", Toast.LENGTH_SHORT).show();
            return;
        }
        int warn = 0;
        for (AuditLog l : shown) if (bad(nz(l.action))) warn++;
        String filter = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();

        PdfExporter.Table t = new PdfExporter.Table();
        t.title = "Audit report";
        t.subtitle = "Security and activity events";
        t.summary.add(new String[]{"Events", String.valueOf(shown.size())});
        t.summary.add(new String[]{"Warnings", warn + " (failed, locked or blocked)"});
        t.summary.add(new String[]{"Filter", filter.isEmpty() ? "None" : filter});
        t.headers = new String[]{"Time", "User", "Action", "Details", "IP"};
        t.weights = new float[]{2.4f, 3.0f, 2.6f, 3.8f, 2.0f};
        t.right = new boolean[]{false, false, false, false, false};
        for (AuditLog l : shown) {
            t.rows.add(new String[]{Fmt.date(l.createdAt), nz(l.actor), nz(l.action),
                    nz(l.details), nz(l.ipAddress)});
        }
        String name = "audit_report_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(new Date()) + ".pdf";
        PdfExporter.export(requireActivity(), name, t);
    }
}