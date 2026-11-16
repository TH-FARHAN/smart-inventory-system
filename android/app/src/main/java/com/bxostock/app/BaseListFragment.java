package com.bxostock.app;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bxostock.app.util.ApiError;
import com.bxostock.app.util.Session;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public abstract class BaseListFragment<T> extends Fragment {
    protected RowAdapter adapter;
    protected TextInputEditText etSearch;
    protected CheckBox cbFilter;
    /** index of the selected status chip (only used when statusChips() returns labels) */
    protected int statusIndex = 0;
    protected View todayBar;
    protected Button btnShowAll;
    private TextView tvMessage;
    private SwipeRefreshLayout swipe;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pending;
    private Call<List<T>> call;

    protected abstract String searchHint();
    protected abstract Call<List<T>> request(String keyword);
    protected abstract Row toRow(T item);

    protected String actionLabel() { return null; }
    protected String filterLabel() { return null; }
    /** Return labels (for example All, Confirmed, Cancelled) to show a row of filter chips above the list. */
    protected String[] statusChips() { return null; }
    protected int initialStatus() { return 0; }
    protected void onAction() { }
    protected void onItemClick(T item) { }
    protected boolean matches(T item, String lowerKeyword) { return true; }
    protected void sort(List<T> items) { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle s) {
        return inflater.inflate(R.layout.fragment_list, container, false);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle s) {
        super.onViewCreated(v, s);
        ((TextInputLayout) v.findViewById(R.id.tilSearch)).setHint(searchHint());
        etSearch = v.findViewById(R.id.etSearch);
        tvMessage = v.findViewById(R.id.tvMessage);
        swipe = v.findViewById(R.id.swipe);
        cbFilter = v.findViewById(R.id.cbFilter);

        todayBar = v.findViewById(R.id.todayBar);
        btnShowAll = v.findViewById(R.id.btnShowAll);
        final String[] chipLabels = statusChips();
        if (chipLabels != null) {
            v.findViewById(R.id.statusScroll).setVisibility(View.VISIBLE);
            final ChipGroup cg = v.findViewById(R.id.chipStatus);
            final int[] ids = new int[chipLabels.length];
            statusIndex = initialStatus();
            for (int i = 0; i < chipLabels.length; i++) {
                Chip c = (Chip) getLayoutInflater().inflate(R.layout.chip_filter, cg, false);
                c.setId(View.generateViewId());
                c.setText(chipLabels[i]);
                ids[i] = c.getId();
                cg.addView(c);
            }
            cg.check(ids[statusIndex]);
            cg.setOnCheckedStateChangeListener((g, checked) -> {
                if (checked.isEmpty()) return;
                for (int i = 0; i < ids.length; i++) if (ids[i] == checked.get(0)) statusIndex = i;
                reload();
            });
        }

        Button btn = v.findViewById(R.id.btnAction);
        if (actionLabel() != null) {
            btn.setText(actionLabel());
            btn.setVisibility(View.VISIBLE);
            btn.setOnClickListener(x -> onAction());
        }
        if (filterLabel() != null) {
            cbFilter.setText(filterLabel());
            cbFilter.setVisibility(View.VISIBLE);
            cbFilter.setOnCheckedChangeListener((b, checked) -> reload());
        }

        adapter = new RowAdapter(row -> onItemClick((T) row.tag));
        RecyclerView rv = v.findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        rv.setAdapter(adapter);

        swipe.setColorSchemeColors(0xFFFF4D1C);
        swipe.setProgressBackgroundColorSchemeColor(0xFF161618);
        swipe.setOnRefreshListener(this::reload);

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence c, int a, int b, int d) { }
            @Override public void onTextChanged(CharSequence c, int a, int b, int d) { }
            @Override
            public void afterTextChanged(Editable e) {
                if (pending != null) handler.removeCallbacks(pending);
                pending = BaseListFragment.this::reload;
                handler.postDelayed(pending, 300);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reload();
    }

    @Override
    public void onDestroyView() {
        if (call != null) call.cancel();
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    protected void reload() {
        if (!isAdded() || getView() == null) return;
        if (call != null) call.cancel();
        swipe.setRefreshing(true);
        tvMessage.setVisibility(View.GONE);
        final String kw = etSearch.getText() == null ? "" : etSearch.getText().toString().trim();
        final String lower = kw.toLowerCase(Locale.ROOT);
        final Call<List<T>> c = request(kw);
        call = c;
        c.enqueue(new Callback<List<T>>() {
            @Override
            public void onResponse(Call<List<T>> cc, Response<List<T>> r) {
                if (!isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                if (r.code() == 401) {
                    Session.expire(requireActivity(), true);
                    return;
                }
                if (!r.isSuccessful() || r.body() == null) {
                    message(ApiError.message(r), true);
                    return;
                }
                List<T> items = new ArrayList<>();
                for (T t : r.body()) if (matches(t, lower)) items.add(t);
                sort(items);
                List<Row> rows = new ArrayList<>();
                for (T t : items) {
                    Row row = toRow(t);
                    row.tag = t;
                    rows.add(row);
                }
                adapter.set(rows);
                if (rows.isEmpty()) message("Nothing found.", false);
            }

            @Override
            public void onFailure(Call<List<T>> cc, Throwable t) {
                if (cc.isCanceled() || !isAdded() || getView() == null) return;
                swipe.setRefreshing(false);
                message(ApiError.NETWORK, true);
            }
        });
    }

    private void message(String text, boolean error) {
        tvMessage.setTextColor(error ? 0xFFFF6B6B : 0xFF9A9AA3);
        tvMessage.setText(text);
        tvMessage.setVisibility(View.VISIBLE);
    }
}