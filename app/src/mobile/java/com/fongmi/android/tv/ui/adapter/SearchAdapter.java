package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterSearchBinding;
import com.fongmi.android.tv.databinding.AdapterSearchTextBinding;
import com.fongmi.android.tv.databinding.AdapterVodRectBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ImgUtil;

public class SearchAdapter extends BaseDiffAdapter<Vod, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_LIST = 0;
    private static final int VIEW_TYPE_GRID = 1;
    private static final int VIEW_TYPE_TEXT = 2;

    public static final int MODE_GRID = 1;
    public static final int MODE_LIST = 2;
    public static final int MODE_TEXT = 3;

    private final OnClickListener listener;
    private int mode = MODE_GRID;
    private int[] size = new int[]{0, 0};
    private boolean allMode = true;

    public SearchAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public void setAllMode(boolean allMode) {
        this.allMode = allMode;
    }

    public interface OnClickListener {

        void onItemClick(Vod item);
    }

    public void setMode(int mode, int[] size) {
        this.mode = mode;
        this.size = size;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (mode == MODE_GRID) return VIEW_TYPE_GRID;
        if (mode == MODE_TEXT) return VIEW_TYPE_TEXT;
        return VIEW_TYPE_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GRID) return new GridHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        if (viewType == VIEW_TYPE_TEXT) return new TextHolder(AdapterSearchTextBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        return new ListHolder(AdapterSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Vod item = getItem(position);
        if (holder instanceof GridHolder gridHolder) {
            gridHolder.initView(item);
            return;
        }
        if (holder instanceof TextHolder textHolder) {
            textHolder.initView(item);
            return;
        }
        if (!(holder instanceof ListHolder listHolder)) return;
        listHolder.initView(item);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof GridHolder gridHolder) {
            Glide.with(gridHolder.binding.image).clear(gridHolder.binding.image);
            gridHolder.setMarquee(false);
        }
        if (holder instanceof ListHolder listHolder) {
            Glide.with(listHolder.binding.image).clear(listHolder.binding.image);
            listHolder.setMarquee(false);
        }
        if (holder instanceof TextHolder textHolder) {
            textHolder.setMarquee(false);
        }
    }

    public class ListHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchBinding binding;

        ListHolder(@NonNull AdapterSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setFocusable(true);
            binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        }

        private void initView(Vod item) {
            Setting.applyTitleMaxLines(binding.name);
            binding.name.setHorizontallyScrolling(Setting.resolveTitleMaxLines() <= 1);
            binding.name.setText(item.getName());
            setMarquee(binding.getRoot().hasFocus());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.site.setVisibility(item.getSiteVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            ImgUtil.load(item.getName(), item.getPic(), binding.image);
        }

        private void setMarquee(boolean focused) {
            if (Setting.resolveTitleMaxLines() <= 1) {
                binding.name.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
                binding.name.setSelected(focused);
            }
        }
    }

    public class GridHolder extends RecyclerView.ViewHolder {

        private final AdapterVodRectBinding binding;

        GridHolder(@NonNull AdapterVodRectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setFocusable(true);
            binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
            applySize();
        }

        private void initView(Vod item) {
            applySize();
            Setting.applyTitleMaxLines(binding.name);
            binding.name.setHorizontallyScrolling(Setting.resolveTitleMaxLines() <= 1);
            binding.name.setText(item.getName());
            setMarquee(binding.getRoot().hasFocus());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.year.setVisibility(android.view.View.GONE);
            binding.site.setVisibility(item.getSiteVisible());
            binding.name.setVisibility(item.getNameVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            ImgUtil.load(item.getName(), item.getPic(), binding.image);
        }

        private void applySize() {
            ViewGroup.LayoutParams imageParams = binding.image.getLayoutParams();
            imageParams.height = size[1];
            binding.image.setLayoutParams(imageParams);
            ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
            rootParams.width = size[0];
            if (rootParams instanceof ViewGroup.MarginLayoutParams params) {
                int margin = size.length > 2 ? size[2] : 0;
                params.setMargins(margin, margin, margin, margin);
            }
            binding.getRoot().setLayoutParams(rootParams);
        }

        private void setMarquee(boolean focused) {
            if (Setting.resolveTitleMaxLines() <= 1) {
                binding.name.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
                binding.name.setSelected(focused);
            }
        }
    }

    public class TextHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchTextBinding binding;

        TextHolder(@NonNull AdapterSearchTextBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.getRoot().setFocusable(true);
            binding.getRoot().setOnFocusChangeListener((view, hasFocus) -> setMarquee(hasFocus));
        }

        private void initView(Vod item) {
            Setting.applyTitleMaxLines(binding.name);
            binding.name.setHorizontallyScrolling(Setting.resolveTitleMaxLines() <= 1);
            String suffix;
            if (allMode) {
                String siteName = item.getSiteName();
                suffix = siteName.isEmpty() ? "" : "【" + siteName + "】";
            } else {
                String remark = item.getRemarks();
                suffix = (remark == null || remark.isEmpty()) ? "" : "【" + remark + "】";
            }
            String text = suffix.isEmpty() ? item.getName() : item.getName() + suffix;
            binding.name.setText(text);
            setMarquee(binding.getRoot().hasFocus());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
        }

        private void setMarquee(boolean focused) {
            if (Setting.resolveTitleMaxLines() <= 1) {
                binding.name.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.END);
                binding.name.setSelected(focused);
            }
        }
    }
}
