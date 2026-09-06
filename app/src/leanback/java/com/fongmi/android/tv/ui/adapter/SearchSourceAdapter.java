package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.ItemSearchSourceBinding;
import com.fongmi.android.tv.utils.SearchSourceItem;

import java.util.ArrayList;
import java.util.List;

public class SearchSourceAdapter extends RecyclerView.Adapter<SearchSourceAdapter.ViewHolder> {

    private final List<SearchSourceItem> items = new ArrayList<>();
    private int checked = 0;
    private OnConfigListener configListener;
    private OnModeChangeListener modeChangeListener;

    public interface OnConfigListener {
        void onConfig(SearchSourceItem item);
    }

    public interface OnModeChangeListener {
        void onModeChanged(int mode);
    }

    public void setOnConfigListener(OnConfigListener l) {
        this.configListener = l;
    }

    public void setOnModeChangeListener(OnModeChangeListener l) {
        this.modeChangeListener = l;
    }

    public void setItems(List<SearchSourceItem> items, int checked) {
        this.items.clear();
        this.items.addAll(items);
        this.checked = checked;
        notifyDataSetChanged();
    }

    public int getChecked() {
        return checked;
    }

    private int findPositionOfMode(int mode) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).mode == mode) return i;
        return -1;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSearchSourceBinding binding = ItemSearchSourceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SearchSourceItem item = items.get(position);
        holder.binding.title.setText(item.titleRes);
        holder.binding.subtitle.setText(item.subtitle == null ? "" : item.subtitle);
        holder.binding.subtitle.setVisibility(item.subtitle == null || item.subtitle.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.getRoot().setSelected(checked == item.mode);
        holder.binding.config.setVisibility(item.configurable ? android.view.View.VISIBLE : android.view.View.GONE);
        holder.binding.getRoot().setOnClickListener(v -> {
            int prev = checked;
            checked = item.mode;
            int p1 = findPositionOfMode(prev);
            int p2 = findPositionOfMode(checked);
            if (p1 >= 0) notifyItemChanged(p1);
            if (p2 >= 0) notifyItemChanged(p2);
            if (modeChangeListener != null) modeChangeListener.onModeChanged(checked);
        });
        holder.binding.config.setOnClickListener(v -> {
            if (configListener != null) configListener.onConfig(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemSearchSourceBinding binding;

        ViewHolder(ItemSearchSourceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
