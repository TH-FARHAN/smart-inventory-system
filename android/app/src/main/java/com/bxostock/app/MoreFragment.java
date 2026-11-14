package com.bxostock.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class MoreFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        ScrollView sv = new ScrollView(requireContext());
        LinearLayout box = new LinearLayout(requireContext());
        box.setOrientation(LinearLayout.VERTICAL);
        int p = (int) (12 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p, p, p);
        sv.addView(box);

        add(inflater, box, "Users", "Create staff accounts, activate or deactivate", new UsersFragment());
        add(inflater, box, "Categories", "Product categories", new CategoriesFragment());
        add(inflater, box, "Reports", "Sales and inventory reports", new ReportsFragment());
        add(inflater, box, "Audit logs", "Latest 100 security and activity events", new AuditLogsFragment());
        return sv;
    }

    private void add(LayoutInflater inflater, LinearLayout box, String title, String sub, final Fragment target) {
        View v = inflater.inflate(R.layout.item_row, box, false);
        Row row = new Row();
        row.title = title;
        row.sub = sub;
        row.right = "\u203A";
        RowAdapter.bind(v, row);
        v.setOnClickListener(x -> ((MainActivity) requireActivity()).open(target));
        box.addView(v);
    }
}