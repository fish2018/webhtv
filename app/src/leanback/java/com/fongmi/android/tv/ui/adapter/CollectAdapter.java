package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterTypeBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class CollectAdapter extends RecyclerView.Adapter<CollectAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Collect> mItems;

    public CollectAdapter(OnClickListener listener) {
        this.listener = listener;
        mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(int position, Collect item);
    }

    public void add(Collect item) {
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    public void add(List<Vod> items) {
        if (mItems.isEmpty()) return;
        mItems.get(0).getList().addAll(items);
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

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public void setSelected(int position) {
        int old = getPosition();
        if (old == position) return;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        if (old >= 0) notifyItemChanged(old);
        notifyItemChanged(position);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTypeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = mItems.get(position);
        holder.binding.text.getLayoutParams().width = ResUtil.dp2px(160);
        holder.binding.text.setSingleLine(true);
        holder.binding.text.setEllipsize(android.text.TextUtils.TruncateAt.MARQUEE);
        holder.binding.text.setMarqueeRepeatLimit(-1);
        holder.binding.text.setHorizontallyScrolling(true);
        holder.binding.getRoot().setSelected(item.isSelected());
        holder.binding.getRoot().setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (listener != null && adapterPosition >= 0) listener.onItemClick(adapterPosition, item);
        });
        holder.binding.text.setText(item.getSite().getName());
        holder.binding.text.setSelected(holder.binding.text.hasFocus() || item.isSelected());
        holder.binding.text.setOnFocusChangeListener((v, hasFocus) -> holder.binding.text.setSelected(hasFocus || item.isSelected()));
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTypeBinding binding;

        ViewHolder(@NonNull AdapterTypeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
