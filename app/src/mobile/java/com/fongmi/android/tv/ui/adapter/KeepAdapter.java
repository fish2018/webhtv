package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.databinding.AdapterVodBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.List;

public class KeepAdapter extends BaseDiffAdapter<Keep, KeepAdapter.ViewHolder> {

    private static final Object PAYLOAD_MARQUEE = new Object();
    private final OnClickListener listener;
    private int width, height;
    private int marqueeFirst = RecyclerView.NO_POSITION;
    private int marqueeLast = RecyclerView.NO_POSITION;
    private boolean delete;

    public KeepAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Keep item);

        void onItemDelete(Keep item);

        boolean onLongClick();
    }

    public void setSize(int[] size) {
        this.width = size[0];
        this.height = size[1];
    }

    public void setMarqueeRange(int firstPosition, int lastPosition) {
        if (firstPosition == RecyclerView.NO_POSITION || lastPosition == RecyclerView.NO_POSITION || firstPosition > lastPosition) {
            firstPosition = RecyclerView.NO_POSITION;
            lastPosition = RecyclerView.NO_POSITION;
        }
        if (marqueeFirst == firstPosition && marqueeLast == lastPosition) return;
        int changedFirst = marqueeFirst == RecyclerView.NO_POSITION ? firstPosition : firstPosition == RecyclerView.NO_POSITION ? marqueeFirst : Math.min(marqueeFirst, firstPosition);
        int changedLast = Math.max(marqueeLast, lastPosition);
        marqueeFirst = firstPosition;
        marqueeLast = lastPosition;
        if (changedFirst >= 0 && changedFirst < getItemCount()) {
            changedLast = Math.min(changedLast, getItemCount() - 1);
            notifyItemRangeChanged(changedFirst, changedLast - changedFirst + 1, PAYLOAD_MARQUEE);
        }
    }

    public boolean isDelete() {
        return delete;
    }

    public void setDelete(boolean delete) {
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        Keep.deleteAll();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ViewHolder holder = new ViewHolder(AdapterVodBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().getLayoutParams().width = width;
        holder.binding.image.getLayoutParams().height = height;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Keep item = getItem(position);
        holder.binding.name.setText(item.getVodName());
        holder.setMarquee(isMarqueePosition(position));
        holder.binding.remark.setVisibility(View.GONE);
        holder.binding.site.setVisibility(View.VISIBLE);
        holder.binding.site.setText(item.getSiteName());
        holder.binding.playback.setVisibility(View.GONE);
        holder.binding.progress.setVisibility(View.GONE);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        ImgUtil.load(item.getVodName(), item.getVodPic(), holder.binding.image);
        setClickListener(holder.binding.getRoot(), item);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_MARQUEE)) holder.setMarquee(isMarqueePosition(position));
        else super.onBindViewHolder(holder, position, payloads);
    }

    private boolean isMarqueePosition(int position) {
        return position >= marqueeFirst && position <= marqueeLast;
    }

    private void setClickListener(View root, Keep item) {
        root.setOnLongClickListener(view -> listener.onLongClick());
        root.setOnClickListener(view -> {
            if (isDelete()) listener.onItemDelete(item);
            else listener.onItemClick(item);
        });
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodBinding binding;

        ViewHolder(@NonNull AdapterVodBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void setMarquee(boolean active) {
            binding.name.setSelected(active);
            binding.remark.setSelected(active);
        }
    }
}
