package com.bxostock.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import com.bxostock.app.util.Fmt;

public class BarChartView extends View {
    private float[] values = new float[0];
    private String[] labels = new String[0];
    private int[] counts = null;
    private int selected = -1;

    private final Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barSel = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint grid = new Paint();
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipBorder = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipTitle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tipText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float d;

    public BarChartView(Context c, AttributeSet a) {
        super(c, a);
        d = c.getResources().getDisplayMetrics().density;
        bar.setColor(0xFFFF4D1C);
        barSel.setColor(0xFFFF9A7A);
        grid.setColor(0xFF2A2A2E);
        grid.setStrokeWidth(d);
        text.setColor(0xFF9A9AA3);
        text.setTextSize(11 * d);
        tipBg.setColor(0xFF161618);
        tipBorder.setColor(0xFF3A3A40);
        tipBorder.setStyle(Paint.Style.STROKE);
        tipBorder.setStrokeWidth(d);
        tipTitle.setColor(0xFF9A9AA3);
        tipTitle.setTextSize(12 * d);
        tipText.setColor(0xFFF2F2F2);
        tipText.setTextSize(13 * d);
        tipText.setFakeBoldText(true);
    }

    // old signature, kept so other screens keep working
    public void setData(float[] v, String[] l) {
        setData(v, l, null);
    }

    public void setData(float[] v, String[] l, int[] orderCounts) {
        values = v;
        labels = l;
        counts = orderCounts;
        selected = -1;
        invalidate();
    }

    private static String shortNum(float n) {
        if (n >= 100000f) return String.format("%.1fL", n / 100000f);
        if (n >= 1000f) return String.format("%.1fk", n / 1000f);
        return String.valueOf(Math.round(n));
    }

    private float left() { return 44 * d; }
    private float right() { return getWidth() - 8 * d; }
    private float top() { return 10 * d; }
    private float bottom() { return getHeight() - 26 * d; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_UP && values.length > 0) {
            float l = left(), r = right();
            float slot = (r - l) / values.length;
            int i = (int) ((e.getX() - l) / slot);
            if (e.getX() >= l && e.getX() <= r && i >= 0 && i < values.length) {
                selected = (i == selected) ? -1 : i;
            } else {
                selected = -1;
            }
            invalidate();
            performClick();
        }
        return true;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int n = values.length;
        float left = left(), right = right(), top = top(), bottom = bottom();
        if (n == 0) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("No data", getWidth() / 2f, getHeight() / 2f, text);
            return;
        }
        float max = 0;
        for (float v : values) if (v > max) max = v;
        if (max <= 0) max = 1;

        text.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 4; i++) {
            float y = bottom - (bottom - top) * i / 4f;
            canvas.drawLine(left, y, right, y, grid);
            canvas.drawText(shortNum(max * i / 4f), left - 6 * d, y + 4 * d, text);
        }

        float slot = (right - left) / n;
        float bw = Math.max(1f, slot * 0.7f);
        for (int i = 0; i < n; i++) {
            float x = left + slot * i + (slot - bw) / 2f;
            float h = (bottom - top) * values[i] / max;
            canvas.drawRect(x, bottom - h, x + bw, bottom, i == selected ? barSel : bar);
        }

        text.setTextAlign(Paint.Align.CENTER);
        int step = Math.max(1, (int) Math.ceil(52 * d / slot));
        for (int i = 0; i < n; i += step) {
            canvas.drawText(labels[i], left + slot * i + slot / 2f, bottom + 17 * d, text);
        }

        if (selected >= 0 && selected < n) {
            drawTooltip(canvas, left, right, top, bottom, slot, max);
        }
    }

    private void drawTooltip(Canvas canvas, float left, float right, float top, float bottom,
                             float slot, float max) {
        float cx = left + slot * selected + slot / 2f;
        float barTop = bottom - (bottom - top) * values[selected] / max;
        String l1 = labels[selected];
        String l2 = Fmt.money(values[selected]);
        String l3 = counts == null ? null
                : counts[selected] + (counts[selected] == 1 ? " order" : " orders");

        float w = Math.max(tipTitle.measureText(l1),
                Math.max(tipText.measureText(l2), l3 == null ? 0 : tipText.measureText(l3))) + 16 * d;
        float lineH = 17 * d;
        float h = lineH * (l3 == null ? 2 : 3) + 8 * d;
        float x = Math.max(left, Math.min(cx - w / 2f, right - w));
        float y = Math.max(0, barTop - h - 8 * d);

        RectF box = new RectF(x, y, x + w, y + h);
        canvas.drawRoundRect(box, 8 * d, 8 * d, tipBg);
        canvas.drawRoundRect(box, 8 * d, 8 * d, tipBorder);
        float tx = x + 8 * d;
        float ty = y + 4 * d + 13 * d;
        canvas.drawText(l1, tx, ty, tipTitle);
        canvas.drawText(l2, tx, ty + lineH, tipText);
        if (l3 != null) canvas.drawText(l3, tx, ty + 2 * lineH, tipText);
    }
}