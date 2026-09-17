package com.fongmi.android.tv.lab;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

public final class LabGroupAdapter extends RecyclerView.Adapter<LabGroupAdapter.Holder> {

    public static class Row {
        public final String id;
        public final String name;

        public Row(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public interface Listener {
        void onGroup(String id);
    }

    private final List<Row> rows = new ArrayList<>();
    private final Listener listener;
    private int selected = 0;

    public LabGroupAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRows(List<Row> data) {
        rows.clear();
        rows.addAll(data);
        selected = 0;
        notifyDataSetChanged();
    }

    public void select(int position) {
        int old = selected;
        selected = position;
        notifyItemChanged(old);
        notifyItemChanged(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_lab_group_tab, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        holder.text.setText(row.name);
        holder.text.setActivated(position == selected);
        holder.itemView.setOnClickListener(v -> {
            select(position);
            if (listener != null) listener.onGroup(row.id);
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.text);
        }
    }
}
