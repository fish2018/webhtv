package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.ItemSiteCheckBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SiteCheckAdapter extends RecyclerView.Adapter<SiteCheckAdapter.ViewHolder> {

    private List<Site> all = new ArrayList<>();
    private List<Site> filtered = new ArrayList<>();
    private final Set<String> checkedKeys = new HashSet<>();

    public void setItems(List<Site> sites, Set<String> initial) {
        all = new ArrayList<>(sites);
        filtered = new ArrayList<>(sites);
        checkedKeys.clear();
        if (initial != null) checkedKeys.addAll(initial);
        notifyDataSetChanged();
    }

    public void filter(String keyword) {
        if (keyword == null || keyword.isEmpty()) {
            filtered = new ArrayList<>(all);
        } else {
            filtered = new ArrayList<>();
            String k = keyword.toLowerCase();
            for (Site s : all) {
                if (s.getName().toLowerCase().contains(k) || s.getKey().toLowerCase().contains(k)) filtered.add(s);
            }
        }
        notifyDataSetChanged();
    }

    public void selectAll() {
        for (Site s : all) checkedKeys.add(s.getKey());
        notifyDataSetChanged();
    }

    public void selectNone() {
        checkedKeys.clear();
        notifyDataSetChanged();
    }

    public void selectInvert() {
        Set<String> allKeys = new HashSet<>();
        for (Site s : all) allKeys.add(s.getKey());
        Set<String> newChecked = new HashSet<>(allKeys);
        newChecked.removeAll(checkedKeys);
        checkedKeys.clear();
        checkedKeys.addAll(newChecked);
        notifyDataSetChanged();
    }

    public Set<String> getCheckedKeys() {
        return new HashSet<>(checkedKeys);
    }

    public int getCheckedCount() {
        return checkedKeys.size();
    }

    public int getTotalCount() {
        return all.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemSiteCheckBinding binding = ItemSiteCheckBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site site = filtered.get(position);
        holder.binding.check.setChecked(checkedKeys.contains(site.getKey()));
        holder.binding.name.setText(site.getName());
        String typeText = site.getType() == 1 ? "JS" : site.getType() == 2 ? "PY" : "PQ";
        holder.binding.type.setText(typeText);
        holder.binding.getRoot().setOnClickListener(v -> {
            String key = site.getKey();
            if (checkedKeys.contains(key)) checkedKeys.remove(key);
            else checkedKeys.add(key);
            notifyItemChanged(position);
        });
    }

    @Override
    public int getItemCount() {
        return filtered.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ItemSiteCheckBinding binding;

        ViewHolder(ItemSiteCheckBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
