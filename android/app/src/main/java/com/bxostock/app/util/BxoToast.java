package com.bxostock.app.util;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;

/** A styled in-app message bar in the BxoStock theme (replaces the grey system Toast). */
public class BxoToast {
    public static void show(View anchor, String message) {
        if (anchor == null) return;
        Snackbar sb = Snackbar.make(anchor, message, Snackbar.LENGTH_SHORT);
        View v = sb.getView();
        v.setBackgroundColor(Color.parseColor("#161618"));
        TextView tv = v.findViewById(com.google.android.material.R.id.snackbar_text);
        if (tv != null) {
            tv.setTextColor(Color.parseColor("#F2F2F2"));
            tv.setCompoundDrawablesWithIntrinsicBounds(com.bxostock.app.R.drawable.logo_icon, 0, 0, 0);
            tv.setCompoundDrawablePadding(24);
        }
        sb.show();
    }
}