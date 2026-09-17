package com.fongmi.android.tv.ui.adapter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterTypeBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class CollectAdapter extends RecyclerView.Adapter<CollectAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Collect> mItems;
    private int progressCurrent;
    private int progressTotal;

    public CollectAdapter(OnClickListener listener) {
        this.listener = listener;
        mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(int position, Collect item);

        default boolean onCollectKey(int position, int keyCode, KeyEvent event) {
            return false;
        }
    }

    public void add(Collect item) {
        add(mItems.size(), item);
    }

    public void add(int position, Collect item) {
        int index = Math.max(0, Math.min(position, mItems.size()));
        mItems.add(index, item);
        notifyItemInserted(index);
    }

    public void add(List<Vod> items) {
        if (mItems.isEmpty()) return;
        mItems.get(0).getList().addAll(items);
    }

    public void setProgress(int current, int total) {
        progressTotal = Math.max(0, total);
        progressCurrent = Math.max(0, Math.min(current, progressTotal));
        if (progressTotal > 0 && !mItems.isEmpty()) notifyChanged(0);
    }

    public int findCollectIndex(String siteKey) {
        for (int i = 0; i < mItems.size(); i++) {
            if (mItems.get(i).getSite().getKey().equals(siteKey)) return i;
        }
        return -1;
    }

    public void update(int position, Collect item) {
        if (position < 0 || position >= mItems.size()) return;
        mItems.set(position, item);
        notifyChanged(position);
    }

    public void setItems(List<Collect> items) {
        mItems.clear();
        if (items != null) mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Collect get(int position) {
        return mItems.get(position);
    }

    public Collect getActivated() {
        return mItems.isEmpty() ? Collect.all() : mItems.get(getPosition());
    }

    @Nullable
    public Collect findActivated(String siteKey) {
        return findActivated(mItems, siteKey);
    }

    @Nullable
    static Collect findActivated(List<Collect> items, String siteKey) {
        if (items.isEmpty()) return null;
        Collect activated = items.get(getPosition(items));
        return activated.getSite().getKey().equals(siteKey) ? activated : null;
    }

    public int getPosition() {
        return getPosition(mItems);
    }

    private static int getPosition(List<Collect> items) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
        return 0;
    }

    public void setSelected(int position) {
        int old = getPosition();
        if (old == position) return;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        notifyChanged(old);
        notifyChanged(position);
    }

    private void notifyChanged(int position) {
        if (position < 0 || position >= getItemCount()) return;
        String siteKey = mItems.get(position).getSite().getKey();
        App.post(() -> {
            int index = findCollectIndex(siteKey);
            if (index >= 0) notifyItemChanged(index);
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        boolean isHorizontal = parent instanceof androidx.leanback.widget.HorizontalGridView;
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false), isHorizontal);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = mItems.get(position);
        // 横屏模式使用WRAP_CONTENT，竖屏模式使用固定宽度160dp
        holder.binding.text.getLayoutParams().width = holder.isHorizontal ?
            ViewGroup.LayoutParams.WRAP_CONTENT : ResUtil.dp2px(160);
        // 竖屏模式文字居中，横屏模式文字左对齐
        holder.binding.text.setGravity(holder.isHorizontal ?
            android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL :
            android.view.Gravity.CENTER);
        holder.binding.text.setSingleLine(true);
        holder.binding.text.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        holder.binding.text.setMarqueeRepeatLimit(-1);
        holder.binding.text.setHorizontallyScrolling(true);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (listener != null && adapterPosition >= 0) listener.onItemClick(adapterPosition, item);
        });
        holder.binding.getRoot().setOnKeyListener((v, keyCode, event) -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            return listener != null && adapterPosition >= 0 && listener.onCollectKey(adapterPosition, keyCode, event);
        });
        String name = item.getSite().getDisplayName();
        holder.binding.text.setText("all".equals(item.getSite().getKey()) && progressTotal > 0
            ? name + " " + progressCurrent + "/" + progressTotal
            : name);
        holder.binding.text.setSelected(holder.binding.text.hasFocus() || item.isSelected());
        holder.binding.text.setOnFocusChangeListener((v, hasFocus) -> holder.binding.text.setSelected(hasFocus || item.isSelected()));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;
        private final boolean isHorizontal;

        ViewHolder(@NonNull AdapterTypeBinding binding, boolean isHorizontal) {
            super(binding.getRoot());
            this.binding = binding;
            this.isHorizontal = isHorizontal;
        }
    }
}
