package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterSearchBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Vod> items;
    private final List<Vod> source;
    private final int height;
    private final int width;

    public SearchAdapter(OnClickListener listener, int width, int height) {
        this.listener = listener;
        this.items = new ArrayList<>();
        this.source = new ArrayList<>();
        this.width = width;
        this.height = height;
    }

    public interface OnClickListener {

        void onItemClick(Vod item);

        boolean onItemKey(int position, int keyCode, KeyEvent event);
    }

    public void addAll(List<Vod> items) {
        int start = this.items.size();
        this.items.addAll(items);
        notifyItemRangeInserted(start, items.size());
    }

    public void setItems(List<Vod> items, Runnable runnable) {
        this.items.clear();
        this.items.addAll(items);
        notifyDataSetChanged();
        if (runnable != null) runnable.run();
    }

    public void replaceFirst(List<Vod> items) {
        this.items.clear();
        this.items.addAll(items);
        notifyDataSetChanged();
    }

    public void setSource(List<Vod> items, int visibleCount) {
        source.clear();
        source.addAll(items);
        replaceFirst(new ArrayList<>(source.subList(0, Math.min(source.size(), visibleCount))));
    }

    public boolean ensureLoaded(int position, int preloadCount) {
        if (source.isEmpty()) return false;
        int target = Math.min(source.size(), Math.max(items.size(), position + preloadCount));
        if (target <= items.size()) return false;
        int start = items.size();
        items.addAll(source.subList(start, target));
        notifyItemRangeInserted(start, target - start);
        return true;
    }

    public void appendSource(List<Vod> items, int minVisibleCount) {
        source.addAll(items);
        int target = Math.min(source.size(), Math.max(this.items.size(), minVisibleCount));
        if (target <= this.items.size()) return;
        int start = this.items.size();
        this.items.addAll(source.subList(start, target));
        notifyItemRangeInserted(start, target - start);
    }

    public void clear() {
        this.items.clear();
        this.source.clear();
        notifyDataSetChanged();
    }

    public RequestBuilder<?> getPreloadRequest(int position) {
        if (position < 0 || position >= items.size()) return null;
        Vod item = items.get(position);
        return Glide.with(App.get()).load(ImgUtil.getUrl(item.getPic())).override(width, height).centerCrop();
    }

    public void preload(int start, int count) {
        int end = Math.min(items.size(), start + count);
        for (int i = Math.max(0, start); i < end; i++) {
            RequestBuilder<?> request = getPreloadRequest(i);
            if (request != null) request.preload(width, height);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.getRoot().getLayoutParams().height = height + ResUtil.dp2px(34);
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Vod item = items.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.remark.setText(item.getRemarks());
        holder.binding.site.setVisibility(item.getSiteVisible());
        holder.binding.remark.setVisibility(item.getRemarkVisible());
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        holder.binding.getRoot().setOnKeyListener((v, keyCode, event) -> listener.onItemKey(holder.getBindingAdapterPosition(), keyCode, event));
        ImgUtil.load(item.getName(), item.getPic(), holder.binding.image, width, height);
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        Glide.with(holder.binding.image).clear(holder.binding.image);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchBinding binding;

        ViewHolder(@NonNull AdapterSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
