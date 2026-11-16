package com.bxostock.app.util;

import com.bxostock.app.util.BxoDialog;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Toast;
import com.bxostock.app.R;

import androidx.annotation.RequiresApi;
import androidx.core.content.FileProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PdfExporter {
    public static class Table {
        public String title = "";
        public String subtitle = "";
        public List<String[]> summary = new ArrayList<>();
        public String[] headers;
        public float[] weights;
        public boolean[] right;
        public List<String[]> rows = new ArrayList<>();
    }

    private static final int PW = 595, PH = 842, M = 36;

    private static String fix(String s) {
        // the standard PDF font may not draw the rupee sign
        return s == null ? "" : s.replace("\u20B9", "Rs. ");
    }

    private static class Writer {
        final PdfDocument doc = new PdfDocument();
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        PdfDocument.Page page;
        Canvas c;
        float y;
        int no = 0;

        void newPage() {
            if (page != null) {
                footer();
                doc.finishPage(page);
            }
            no++;
            page = doc.startPage(new PdfDocument.PageInfo.Builder(PW, PH, no).create());
            c = page.getCanvas();
            y = M;
        }

        void footer() {
            p.setFakeBoldText(false);
            p.setColor(0xFF888888);
            p.setTextSize(9);
            p.setTextAlign(Paint.Align.RIGHT);
            c.drawText("Page " + no, PW - M, PH - 20, p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        void text(String s, float x, float size, int color, boolean bold) {
            p.setTextSize(size);
            p.setColor(color);
            p.setFakeBoldText(bold);
            p.setTextAlign(Paint.Align.LEFT);
            c.drawText(fix(s), x, y, p);
        }

        void cells(Table t, String[] vals, int color, boolean bold) {
            float sum = 0;
            for (float w : t.weights) sum += w;
            float total = PW - 2 * M, x = M;
            for (int i = 0; i < vals.length; i++) {
                float w = total * t.weights[i] / sum;
                String s = fix(vals[i]);
                p.setTextSize(9);
                p.setFakeBoldText(bold);
                int n = p.breakText(s, true, w - 8, null);
                if (n < s.length()) s = s.substring(0, Math.max(0, n - 2)) + "..";
                p.setColor(color);
                if (t.right[i]) {
                    p.setTextAlign(Paint.Align.RIGHT);
                    c.drawText(s, x + w - 4, y, p);
                } else {
                    p.setTextAlign(Paint.Align.LEFT);
                    c.drawText(s, x + 4, y, p);
                }
                x += w;
            }
            p.setTextAlign(Paint.Align.LEFT);
        }

        void header(Table t) {
            p.setColor(0xFFFF4D1C);
            p.setStyle(Paint.Style.FILL);
            c.drawRect(M, y - 13, PW - M, y + 6, p);
            cells(t, t.headers, 0xFFFFFFFF, true);
            y += 20;
        }

        void build(Activity a, Table t) {
            newPage();
            y = M + 18;
            try {
                android.graphics.Bitmap bm = android.graphics.BitmapFactory.decodeResource(a.getResources(), R.drawable.logo_icon);
                if (bm != null) c.drawBitmap(bm, null, new android.graphics.RectF(M, y - 22, M + 30, y + 8), p);
            } catch (Exception ignored) { }
            text("BxoStock", M + 38, 22, 0xFFFF4D1C, true);
            y += 24;
            text(t.title, M, 15, 0xFF222222, true);
            y += 16;
            text(t.subtitle, M, 10, 0xFF666666, false);
            y += 13;
            text("Generated: " + new SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ENGLISH).format(new Date()),
                    M, 10, 0xFF666666, false);
            y += 24;
            for (String[] s : t.summary) {
                text(s[0] + ":", M, 11, 0xFF444444, false);
                text(s[1], M + 150, 11, 0xFF111111, true);
                y += 16;
            }
            y += 8;
            header(t);
            for (String[] row : t.rows) {
                if (y + 20 > PH - M) {
                    newPage();
                    y = M + 12;
                    header(t);
                }
                cells(t, row, 0xFF222222, false);
                p.setColor(0xFFDDDDDD);
                c.drawLine(M, y + 6, PW - M, y + 6, p);
                y += 20;
            }
            footer();
            doc.finishPage(page);
        }
    }

    public static void export(final Activity a, final String fileName, Table t) {
        try {
            File dir = new File(a.getCacheDir(), "reports");
            dir.mkdirs();
            File file = new File(dir, fileName);
            Writer w = new Writer();
            w.build(a, t);
            FileOutputStream out = new FileOutputStream(file);
            w.doc.writeTo(out);
            out.close();
            w.doc.close();

            boolean saved = false;
            if (Build.VERSION.SDK_INT >= 29) saved = copyToDownloads(a, file, fileName);
            final Uri uri = FileProvider.getUriForFile(a, a.getPackageName() + ".fileprovider", file);
            new BxoDialog(a)
                    .setTitle("PDF ready")
                    .setMessage(saved ? "Saved in your Downloads folder as\n" + fileName
                            : "The PDF was created. Tap Share to save or send it.")
                    .setNegativeButton("Close", null)
                    .setNeutralButton("Share", (d, x) -> send(a, uri, true))
                    .setPositiveButton("Open", (d, x) -> send(a, uri, false))
                    .show();
        } catch (Exception e) {
            Toast.makeText(a, "Could not create the PDF.", Toast.LENGTH_LONG).show();
        }
    }

    @RequiresApi(29)
    private static boolean copyToDownloads(Activity a, File src, String name) {
        try {
            ContentValues cv = new ContentValues();
            cv.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            cv.put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
            cv.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            Uri u = a.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (u == null) return false;
            try (OutputStream os = a.getContentResolver().openOutputStream(u);
                 FileInputStream in = new FileInputStream(src)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void send(Activity a, Uri uri, boolean share) {
        try {
            Intent i;
            if (share) {
                i = new Intent(Intent.ACTION_SEND);
                i.setType("application/pdf");
                i.putExtra(Intent.EXTRA_STREAM, uri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                i = Intent.createChooser(i, "Share report");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(uri, "application/pdf");
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            }
            a.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(a, "No PDF viewer installed. Use Share instead.", Toast.LENGTH_LONG).show();
        }
    }
}