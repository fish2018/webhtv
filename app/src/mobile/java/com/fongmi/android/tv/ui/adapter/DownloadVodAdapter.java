package com.fongmi.android.tv.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.DownloadGroup;
import com.fongmi.android.tv.databinding.AdapterDownloadVodBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class DownloadVodAdapter extends RecyclerView.Adapter<DownloadVodAdapter.ViewHolder> {

    private final List<DownloadGroup> mItems;
    private final OnClickListener mListener;

    public DownloadVodAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.mListener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<DownloadGroup> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public interface OnClickListener {

        void onItemClick(DownloadGroup group);

        void onItemDelete(DownloadGroup group);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadGroup group = mItems.get(position);
        ImgUtil.load(group.getVodName(), group.getVodPic(), holder.binding.image);
        holder.binding.name.setText(group.getVodName());
        holder.binding.badge.setText(group.hasActive() ? ResUtil.getString(R.string.download_active) : group.getBadge());
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(group));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            mListener.onItemDelete(group);
            return true;
        });
        holder.binding.delete.setOnClickListener(v -> mListener.onItemDelete(group));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterDownloadVodBinding binding;

        public ViewHolder(@NonNull AdapterDownloadVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
