package com.bxostock.app;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RowAdapter extends RecyclerView.Adapter<RowAdapter.VH> {
    public interface Click { void onClick(Row row); }

    private final List<Row> rows = new ArrayList<>();
    private final Click click;

    public RowAdapter(Click click) { this.click = click; }

    public void set(List<Row> data) {
        rows.clear();
        rows.addAll(data);
        notifyDataSetChanged();
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