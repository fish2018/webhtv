package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.AdapterTmdbItemBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class TmdbAdapter extends RecyclerView.Adapter<TmdbAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    private final Listener listener;
    private final List<TmdbItem> items;

    public TmdbAdapter(Listener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
    }

    public void setItems(List<TmdbItem> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbItem item = items.get(position);
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
        }
    }
}
