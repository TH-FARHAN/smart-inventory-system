package com.bxostock.app.util;

import android.content.Context;
import android.content.SharedPreferences;

public class TokenStore {
    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("bxostock", Context.MODE_PRIVATE);
    }

    public static void save(Context c, String token, String name, String email, String role) {
        sp(c).edit().putString("token", token).putString("name", name)
                .putString("email", email).putString("role", role).apply();
    }

    public static String token(Context c) { return sp(c).getString("token", null); }
    public static String name(Context c) { return sp(c).getString("name", ""); }
    public static String email(Context c) { return sp(c).getString("email", ""); }
    public static String role(Context c) { return sp(c).getString("role", ""); }
    public static boolean isLoggedIn(Context c) { return token(c) != null; }
    public static void clear(Context c) { sp(c).edit().clear().apply(); }
}