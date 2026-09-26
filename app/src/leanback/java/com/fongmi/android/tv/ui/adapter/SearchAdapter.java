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
import com.fongmi.android.tv.databinding.AdapterSearchDetailBinding;
import com.fongmi.android.tv.databinding.AdapterSearchTextTvBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_GRID = 0;
    private static final int VIEW_TYPE_TEXT = 1;
    private static final int VIEW_TYPE_DETAIL = 2;

    public static final int MODE_GRID = 1;
    public static final int MODE_LIST = 2;
    public static final int MODE_TEXT = 3;

    private final OnClickListener listener;
    private final List<Vod> items;
    private final List<Vod> source;
    private int height;
    private int width;
    private int mode = MODE_GRID;
    private boolean allMode = true;

    public SearchAdapter(OnClickListener listener, int width, int height) {
        this.listener = listener;
        this.items = new ArrayList<>();
        this.source = new ArrayList<>();
        this.width = width;
        this.height = height;
    }

    public void setAllMode(boolean allMode) {
        this.allMode = allMode;
    }

    public void setMode(int mode) {
        this.mode = mode;
        notifyDataSetChanged();
    }

    public void setSize(int width, int height) {
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
        try {
            int target = Math.min(source.size(), Math.max(items.size(), position + preloadCount));
            if (target <= items.size()) return false;
            int start = items.size();
            items.addAll(source.subList(start, target));
            notifyItemRangeInserted(start, target - start);
            return true;
        } catch (Exception e) {
            android.util.Log.e("SearchAdapter", "ensureLoaded failed", e);
            return false;
        }
    }

    public void appendSource(List<Vod> items, int minVisibleCount) {
        if (items == null || items.isEmpty()) return;
        try {
            source.addAll(items);
            int target = Math.min(source.size(), Math.max(this.items.size(), minVisibleCount));
            if (target <= this.items.size()) return;
            int start = this.items.size();
            this.items.addAll(source.subList(start, target));
            notifyItemRangeInserted(start, target - start);
        } catch (Exception e) {
            android.util.Log.e("SearchAdapter", "appendSource failed", e);
        }
    }

    public void clear() {
        this.items.clear();
        this.source.clear();
        notifyDataSetChanged();
    }

    public RequestBuilder<?> getPreloadRequest(int position) {
        if (mode != MODE_GRID || position < 0 || position >= items.size()) return null;
        Vod item = items.get(position);
        return Glide.with(App.get()).load(ImgUtil.getUrl(item.getPic())).override(width, height).centerCrop();
    }

    public void preload(int start, int count) {
        if (mode != MODE_GRID) return;
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

    @Override
    public int getItemViewType(int position) {
        if (mode == MODE_TEXT) return VIEW_TYPE_TEXT;
        if (mode == MODE_LIST) return VIEW_TYPE_DETAIL;
        return VIEW_TYPE_GRID;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_TEXT) {
            return new TextHolder(AdapterSearchTextTvBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        if (viewType == VIEW_TYPE_DETAIL) {
            return new DetailHolder(AdapterSearchDetailBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }
        GridHolder holder = new GridHolder(AdapterSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.getRoot().getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Vod item = items.get(position);
        if (holder instanceof TextHolder textHolder) {
            textHolder.bind(item);
            return;
        }
        if (holder instanceof DetailHolder detailHolder) {
            detailHolder.bind(item);
            return;
        }
        if (!(holder instanceof GridHolder gridHolder)) return;
        gridHolder.bind(item);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof GridHolder gridHolder) {
            Glide.with(gridHolder.binding.image).clear(gridHolder.binding.image);
            gridHolder.setMarquee(false);
        }
        if (holder instanceof DetailHolder detailHolder) {
            Glide.with(detailHolder.binding.image).clear(detailHolder.binding.image);
            detailHolder.setMarquee(false);
        }
        if (holder instanceof TextHolder textHolder) {
            textHolder.setMarquee(false);
        }
    }

    public class GridHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchBinding binding;

        GridHolder(@NonNull AdapterSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        }

        private void bind(Vod item) {
            bindName(item.getName());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.site.setVisibility(item.getSiteVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.getRoot().setOnKeyListener((v, keyCode, event) -> listener.onItemKey(getBindingAdapterPosition(), keyCode, event));
            ImgUtil.load(item.getName(), item.getPic(), binding.image, width, height);
        }

        private void bindName(String name) {
            Setting.applyTitleMaxLines(binding.name);
            binding.name.setHorizontallyScrolling(Setting.resolveTitleMaxLines() <= 1);
            binding.name.setText(name);
            setMarquee(binding.getRoot().hasFocus());
        }

        private void setMarquee(boolean focused) {
            binding.name.setSelected(focused);
        }
    }

    public class TextHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchTextTvBinding binding;

        TextHolder(@NonNull AdapterSearchTextTvBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        }

        private void bind(Vod item) {
            String suffix;
            if (allMode) {
                String siteName = item.getSiteName();
                suffix = siteName.isEmpty() ? "" : "【" + siteName + "】";
            } else {
                String remark = item.getRemarks();
                suffix = (remark == null || remark.isEmpty()) ? "" : "【" + remark + "】";
            }
            String text = suffix.isEmpty() ? item.getName() : item.getName() + suffix;
            Setting.applyTitleMaxLines(binding.name);
            binding.name.setHorizontallyScrolling(Setting.resolveTitleMaxLines() <= 1);
            binding.name.setText(text);
            setMarquee(binding.getRoot().hasFocus());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.getRoot().setOnKeyListener((v, keyCode, event) -> listener.onItemKey(getBindingAdapterPosition(), keyCode, event));
        }

        private void setMarquee(boolean focused) {
            binding.name.setSelected(focused);
        }
    }

    public class DetailHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchDetailBinding binding;

        DetailHolder(@NonNull AdapterSearchDetailBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        }

        private void bind(Vod item) {
            bindName(item.getName());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.site.setVisibility(item.getSiteVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            binding.getRoot().setOnKeyListener((v, keyCode, event) -> listener.onItemKey(getBindingAdapterPosition(), keyCode, event));
            ImgUtil.load(item.getName(), item.getPic(), binding.image);
        }

        private void bindName(String name) {
            Setting.applyTitleMaxLines(binding.name);
            binding.name.setHorizontallyScrolling(Setting.resolveTitleMaxLines() <= 1);
            binding.name.setText(name);
            setMarquee(binding.getRoot().hasFocus());
        }

        private void setMarquee(boolean focused) {
            binding.name.setSelected(focused);
        }
    }
}
