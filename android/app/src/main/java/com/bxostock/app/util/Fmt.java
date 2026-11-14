package com.bxostock.app.util;

import java.text.NumberFormat;
import java.util.Locale;

public class Fmt {
    public static String money(double d) {
        NumberFormat nf = NumberFormat.getInstance(new Locale("en", "IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "\u20B9" + nf.format(d);
    }

    public static String date(String iso) {
        if (iso == null) return "-";
        String s = iso.length() > 16 ? iso.substring(0, 16) : iso;
        return s.replace('T', ' ');
    }
}