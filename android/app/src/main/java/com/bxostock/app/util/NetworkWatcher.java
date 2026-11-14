package com.bxostock.app.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.material.snackbar.Snackbar;

/** Shows a red bar when the internet is off and a green one when it returns. Works on any screen. */
public class NetworkWatcher implements DefaultLifecycleObserver {
    private final AppCompatActivity activity;
    private final ConnectivityManager cm;
    private ConnectivityManager.NetworkCallback callback;
    private Snackbar offlineBar;
    private boolean wasOffline = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private NetworkWatcher(AppCompatActivity activity) {
        this.activity = activity;
        this.cm = (ConnectivityManager) activity.getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /** Call once in onCreate, right after setContentView. */
    public static void attach(AppCompatActivity activity) {
        activity.getLifecycle().addObserver(new NetworkWatcher(activity));
    }

    @Override
    public void onStart(@NonNull LifecycleOwner owner) {
        if (cm == null) return;
        if (cm.getActiveNetwork() == null) showOffline();
        callback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                activity.runOnUiThread(() -> showOnline());
            }

            @Override
            public void onLost(@NonNull Network network) {
                // wait a moment: switching WiFi to mobile data also fires onLost
                handler.postDelayed(() -> {
                    if (cm.getActiveNetwork() == null) showOffline();
                }, 700);
            }
        };
        cm.registerDefaultNetworkCallback(callback);
    }

    @Override
    public void onStop(@NonNull LifecycleOwner owner) {
        handler.removeCallbacksAndMessages(null);
        if (cm != null && callback != null) {
            try {
                cm.unregisterNetworkCallback(callback);
            } catch (Exception ignored) {
            }
            callback = null;
        }
        if (offlineBar != null) offlineBar.dismiss();
        wasOffline = false;
    }

    private void showOffline() {
        if (activity.isFinishing() || wasOffline) return;
        wasOffline = true;
        offlineBar = Snackbar.make(activity.findViewById(android.R.id.content),
                "You are offline. Please turn on your internet.", Snackbar.LENGTH_INDEFINITE);
        offlineBar.setBackgroundTint(0xFFDC2626).setTextColor(0xFFFFFFFF);
        offlineBar.show();
    }

    private void showOnline() {
        if (!wasOffline || activity.isFinishing()) return;
        wasOffline = false;
        if (offlineBar != null) offlineBar.dismiss();
        Snackbar ok = Snackbar.make(activity.findViewById(android.R.id.content),
                "Back online", Snackbar.LENGTH_SHORT);
        ok.setBackgroundTint(0xFF16A34A).setTextColor(0xFFFFFFFF);
        ok.show();
    }
}