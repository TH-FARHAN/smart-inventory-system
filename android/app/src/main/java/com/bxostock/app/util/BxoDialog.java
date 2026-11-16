package com.bxostock.app.util;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.bxostock.app.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** A MaterialAlertDialogBuilder whose title always shows the BxoStock logo. */
public class BxoDialog extends MaterialAlertDialogBuilder {
    private final Context ctx;

    public BxoDialog(Context context) {
        super(context);
        this.ctx = context;
    }

    @Override
    public MaterialAlertDialogBuilder setTitle(CharSequence title) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.dialog_title, null);
        ((TextView) v.findViewById(R.id.dlgTitle)).setText(title);
        return setCustomTitle(v);
    }

    @Override
    public MaterialAlertDialogBuilder setTitle(int titleId) {
        return setTitle(ctx.getText(titleId));
    }
}