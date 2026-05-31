package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterCollectBinding;

import java.util.List;

public class CollectAdapter extends BaseDiffAdapter<Collect, CollectAdapter.ViewHolder> {

    private final OnClickListener listener;
    private boolean horizontal;

    public CollectAdapter(OnClickListener listener) {
        this(listener, false);
    }

    public CollectAdapter(OnClickListener listener, boolean horizontal) {
        this.listener = listener;
        this.horizontal = horizontal;
    }

    public interface OnClickListener {

        void onItemClick(int position, Collect item);
    }

    public void add(List<Vod> items) {
        if (getItemCount() == 0) return;
        getItem(0).getList().addAll(items);
    }

    public int getPosition() {
        for (int i = 0; i < getItemCount(); i++) if (getItem(i).isSelected()) return i;
        return 0;
    }

    public Collect getActivated() {
        return getItems().get(getPosition());
    }

    public void setSelected(int position) {
        for (int i = 0; i < getItemCount(); i++) getItem(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        AdapterCollectBinding binding = AdapterCollectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        setItemWidth(binding.getRoot());
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Collect item = getItem(position);
        setItemWidth(holder.binding.getRoot());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setText(item.getSite().getName());
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(position, item));
    }

    private void setItemWidth(View view) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params == null) params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.width = horizontal ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        view.setLayoutParams(params);
    }

    public void setHorizontal(boolean horizontal) {
        if (this.horizontal == horizontal) return;
        this.horizontal = horizontal;
        notifyDataSetChanged();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterCollectBinding binding;

        ViewHolder(@NonNull AdapterCollectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
