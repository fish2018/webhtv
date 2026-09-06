package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.ItemSiteCheckBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SourceBlockItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SourceCheckAdapter extends RecyclerView.Adapter<SourceCheckAdapter.ViewHolder> {

    private final List<SourceBlockItem> all = new ArrayList<>();
    private final Set<Integer> blockedSources = new HashSet<>();

    public void setItems(List<SourceBlockItem> items) {
        all.clear();
        all.addAll(items);
        blockedSources.clear();
        int mask = Setting.getSourceBlockMask();
        for (SourceBlockItem item : items) {
            if ((mask & item.source) != 0) blockedSources.add(item.source);
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (SourceBlockItem item : all) blockedSources.add(item.source);
        notifyDataSetChanged();
    }

    public void selectNone() {
        blockedSources.clear();
        notifyDataSetChanged();
    }

    public void selectInvert() {
        Set<Integer> newBlocked = new HashSet<>();
        for (SourceBlockItem item : all) {
            if (!blockedSources.contains(item.source)) newBlocked.add(item.source);
        }
        blockedSources.clear();
        blockedSources.addAll(newBlocked);
        notifyDataSetChanged();
    }

    public int getBlockedCount() {
        return blockedSources.size();
    }

    public int getTotalCount() {
        return all.size();
    }

    public int getMask() {
        int mask = 0;
        for (int src : blockedSources) mask |= src;
        return mask;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSiteCheckBinding binding = ItemSiteCheckBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SourceBlockItem item = all.get(position);
        holder.binding.check.setChecked(blockedSources.contains(item.source));
        holder.binding.name.setText(item.name);
        holder.binding.type.setVisibility(android.view.View.GONE);
        holder.binding.getRoot().setOnClickListener(v -> {
            if (blockedSources.contains(item.source)) blockedSources.remove(item.source);
            else blockedSources.add(item.source);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return all.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemSiteCheckBinding binding;
        ViewHolder(ItemSiteCheckBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
