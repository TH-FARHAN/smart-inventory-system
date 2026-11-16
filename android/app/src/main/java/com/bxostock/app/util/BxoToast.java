package com.bxostock.app.util;

import android.graphics.Color;
import android.view.View;

import com.bxostock.app.R;
import com.google.android.material.snackbar.Snackbar;

/** A styled in-app message bar (message only, shown above the bottom menu). */
public class BxoToast {
    public static void show(View anchor, String message) {
        if (anchor == null) return;
        try {
            Snackbar sb = Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT);
            sb.setBackgroundTint(Color.parseColor("#2A2A2E"));
            sb.setTextColor(Color.parseColor("#F2F2F2"));
            View nav = anchor.getRootView().findViewById(R.id.bottomNav);
            if (nav != null) sb.setAnchorView(nav);
            sb.show();
        } catch (Exception ignored) {
        }
    }
}