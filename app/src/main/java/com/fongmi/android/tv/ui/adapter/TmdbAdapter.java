package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.AdapterTmdbItemBinding;
import com.fongmi.android.tv.ui.helper.TmdbRecommendationRows;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.List;

public class TmdbAdapter extends RecyclerView.Adapter<TmdbAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    private final Listener listener;
    private final List<TmdbItem> items;
    private TmdbItem selectedItem;

    public TmdbAdapter(Listener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
    }

    public void setSelectedItem(TmdbItem selectedItem) {
        int previousPosition = getSelectedPosition();
        this.selectedItem = selectedItem;
        int selectedPosition = getSelectedPosition();
        if (previousPosition != RecyclerView.NO_POSITION) notifyItemChanged(previousPosition);
        if (selectedPosition != RecyclerView.NO_POSITION && selectedPosition != previousPosition) notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return findSelectedPosition(items, selectedItem);
    }

    static int findSelectedPosition(List<TmdbItem> items, TmdbItem selectedItem) {
        if (items == null || selectedItem == null) return RecyclerView.NO_POSITION;
        for (int position = 0; position < items.size(); position++) {
            if (TmdbRecommendationRows.sameIdentity(items.get(position), selectedItem)) return position;
        }
        return RecyclerView.NO_POSITION;
    }

    public void setItems(List<TmdbItem> values) {
        int previousSize = items.size();
        if (previousSize > 0) {
            items.clear();
            notifyItemRangeRemoved(0, previousSize);
        }
        if (values != null && !values.isEmpty()) {
            items.addAll(values);
            notifyItemRangeInserted(0, items.size());
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbItem item = items.get(position);
        boolean selected = TmdbRecommendationRows.sameIdentity(item, selectedItem);
        holder.binding.getRoot().setSelected(selected);
        holder.binding.current.setVisibility(selected ? View.VISIBLE : View.GONE);
        holder.binding.title.setText(item.getTitle());
        holder.binding.subtitle.setText(item.getSubtitle());
        holder.binding.overview.setText(item.getOverview());
        ImgUtil.load(item.getTitle(), item.getPosterUrl(), holder.binding.poster);
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbItemBinding binding;

        ViewHolder(@NonNull AdapterTmdbItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            if (!Util.isLeanback()) {
                itemView.setFocusable(false);
                itemView.setFocusableInTouchMode(false);
            }
        }
    }
}
