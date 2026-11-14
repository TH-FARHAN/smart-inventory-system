package com.bxostock.app.util;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

import com.bxostock.app.LoginActivity;

public class Session {
    public static void expire(Activity a, boolean showToast) {
        TokenStore.clear(a);
        if (showToast) {
            Toast.makeText(a, "Session expired. Please sign in again.", Toast.LENGTH_LONG).show();
        }
        Intent i = new Intent(a, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        a.startActivity(i);
        a.finish();
    }
}