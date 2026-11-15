package com.bxostock.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
    public interface Click { void onClick(Row row); }

    private static final LruCache<Integer, Bitmap> CACHE = new LruCache<>(40);

    private final List<Row> rows = new ArrayList<>();
    private final Click click;

    public RowAdapter(Click click) { this.click = click; }

    public void set(List<Row> data) {
        rows.clear();
        rows.addAll(data);
        notifyDataSetChanged();
    }

    private static Bitmap decode(String photo) {
        if (photo == null || photo.isEmpty()) return null;
        int key = photo.hashCode();
        Bitmap hit = CACHE.get(key);
        if (hit != null) return hit;
        try {
            String b64 = photo.contains(",") ? photo.substring(photo.indexOf(',') + 1) : photo;
            byte[] bytes = Base64.decode(b64, Base64.DEFAULT);
            Bitmap bm = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bm != null) CACHE.put(key, bm);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    public static void bind(View v, Row r) {
        ((TextView) v.findViewById(R.id.tvTitle)).setText(r.title);
        ((TextView) v.findViewById(R.id.tvSub)).setText(r.sub);
        TextView right = v.findViewById(R.id.tvRight);
        right.setText(r.right == null ? "" : r.right);
        right.setTextColor(Color.parseColor(r.warn ? "#FF6B6B" : "#F2F2F2"));
        TextView rs = v.findViewById(R.id.tvRightSub);
        rs.setText(r.rightSub == null ? "" : r.rightSub);
        rs.setVisibility(r.rightSub == null ? View.GONE : View.VISIBLE);
        rs.setTextColor(Color.parseColor(r.warn ? "#FF6B6B" : "#9A9AA3"));

        View box = v.findViewById(R.id.avatarBox);
        if (box != null) {
            if (r.avatarName == null) {
                box.setVisibility(View.GONE);
            } else {
                box.setVisibility(View.VISIBLE);
                ImageView iv = v.findViewById(R.id.ivAvatar);
                TextView tv = v.findViewById(R.id.tvInitial);
                Bitmap bm = decode(r.photo);
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    iv.setVisibility(View.VISIBLE);
                    tv.setVisibility(View.GONE);
                } else {
                    iv.setVisibility(View.GONE);
                    tv.setVisibility(View.VISIBLE);
                    String n = r.avatarName.trim();
                    tv.setText(n.isEmpty() ? "?" : n.substring(0, 1).toUpperCase());
                }
            }
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new VH(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_row, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        final Row r = rows.get(position);
        bind(h.itemView, r);
        h.itemView.setOnClickListener(v -> click.onClick(r));
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class VH extends RecyclerView.ViewHolder {
        VH(View v) { super(v); }
    }
}