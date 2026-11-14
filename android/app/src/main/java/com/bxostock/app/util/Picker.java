package com.bxostock.app.util;

import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Picker<T> {
    public interface Labeler<T> { String label(T item); }
    public interface OnPick<T> { void picked(T item); }

    private final AutoCompleteTextView view;
    private final Map<String, T> map = new LinkedHashMap<>();

    public Picker(AutoCompleteTextView view, final OnPick<T> onPick) {
        this.view = view;
        view.setThreshold(1);
        view.setOnItemClickListener((parent, v, pos, id) -> {
            T item = map.get((String) parent.getItemAtPosition(pos));
            if (item != null && onPick != null) onPick.picked(item);
        });
    }

    public void set(List<T> items, Labeler<T> labeler) {
        map.clear();
        for (T it : items) {
            String label = labeler.label(it);
            String key = label;
            int n = 2;
            while (map.containsKey(key)) key = label + " (" + (n++) + ")";
            map.put(key, it);
        }
        view.setAdapter(new ArrayAdapter<>(view.getContext(),
                android.R.layout.simple_dropdown_item_1line, new ArrayList<>(map.keySet())));
    }

    /** The item whose label is currently typed or picked, or null. */
    public T selected() {
        return map.get(view.getText().toString().trim());
    }

    public void clear() {
        view.setText("", false);
    }
}