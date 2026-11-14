package com.bxostock.app;

import com.bxostock.app.api.ApiClient;
import com.bxostock.app.model.AuditLog;
import com.bxostock.app.util.Fmt;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;

public class AuditLogsFragment extends BaseListFragment<AuditLog> {
    @Override protected String searchHint() { return "Search user, action, details or IP"; }

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
}