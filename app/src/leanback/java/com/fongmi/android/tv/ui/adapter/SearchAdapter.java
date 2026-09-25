package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
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
import com.fongmi.android.tv.databinding.AdapterSearchListBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final float LIST_POSTER_RATIO = 0.72f;

    private final OnClickListener listener;
    private final List<Vod> items;
    private final List<Vod> source;
    private final boolean list;
    private final int height;
    private final int width;

    public SearchAdapter(OnClickListener listener, int width, int height, boolean list) {
        this.listener = listener;
        this.items = new ArrayList<>();
        this.source = new ArrayList<>();
        this.width = width;
        this.height = height;
        this.list = list;
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
        return Glide.with(App.get()).load(ImgUtil.getUrl(item.getPic())).override(getImageWidth(), getImageHeight()).centerCrop();
    }

    public void preload(int start, int count) {
        int end = Math.min(items.size(), start + count);
        for (int i = Math.max(0, start); i < end; i++) {
            RequestBuilder<?> request = getPreloadRequest(i);
            if (request != null) request.preload(getImageWidth(), getImageHeight());
        }
    }

    private int getImageWidth() {
        return list ? Math.max(1, (int) (getImageHeight() * LIST_POSTER_RATIO)) : width;
    }

    private int getImageHeight() {
        return height;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (list) return createListHolder(parent);
        GridHolder holder = new GridHolder(AdapterSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.getRoot().getLayoutParams().height = height + ResUtil.dp2px(34);
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    private ListHolder createListHolder(@NonNull ViewGroup parent) {
        ListHolder holder = new ListHolder(AdapterSearchListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.getRoot().getLayoutParams().height = height;
        holder.binding.image.getLayoutParams().width = getImageWidth();
        holder.binding.image.getLayoutParams().height = getImageHeight();
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Vod item = items.get(position);
        if (holder instanceof ListHolder listHolder) {
            listHolder.initView(item);
        } else if (holder instanceof GridHolder gridHolder) {
            gridHolder.initView(item);
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof ListHolder listHolder) {
            Glide.with(listHolder.binding.image).clear(listHolder.binding.image);
            listHolder.setMarquee(false);
        }
        if (holder instanceof GridHolder gridHolder) {
            Glide.with(gridHolder.binding.image).clear(gridHolder.binding.image);
            gridHolder.setMarquee(false);
        }
    }

    public class ListHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchListBinding binding;

        ListHolder(@NonNull AdapterSearchListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnFocusChangeListener((v, hasFocus) -> setMarquee(hasFocus));
        }

        private void initView(Vod item) {
            binding.name.setText(item.getName());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.site.setVisibility(item.getSiteVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.getRoot().setOnKeyListener((v, keyCode, event) -> listener.onItemKey(getBindingAdapterPosition(), keyCode, event));
            setMarquee(binding.getRoot().hasFocus());
            ImgUtil.load(item.getName(), item.getPic(), binding.image, getImageWidth(), getImageHeight());
        }

        private void setMarquee(boolean selected) {
            binding.name.setSelected(selected);
            binding.site.setSelected(selected);
            binding.remark.setSelected(selected);
        }
    }

    public class GridHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchBinding binding;

        GridHolder(@NonNull AdapterSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.name.setSingleLine(true);
            binding.name.setHorizontallyScrolling(true);
            binding.name.setMarqueeRepeatLimit(-1);
            binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        }

        private void initView(Vod item) {
            binding.name.setText(item.getName());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.site.setVisibility(item.getSiteVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.getRoot().setOnKeyListener((v, keyCode, event) -> listener.onItemKey(getBindingAdapterPosition(), keyCode, event));
            setMarquee(binding.getRoot().hasFocus());
            ImgUtil.load(item.getName(), item.getPic(), binding.image, width, height);
        }

        private void setMarquee(boolean focused) {
            binding.name.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
            binding.name.setSelected(focused);
            binding.site.setSelected(focused);
            binding.remark.setSelected(focused);
        }
    }
}
