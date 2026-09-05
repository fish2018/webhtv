package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.databinding.AdapterVodBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.List;

public class HistoryAdapter extends BaseDiffAdapter<History, HistoryAdapter.ViewHolder> {

    private static final Object PAYLOAD_MARQUEE = new Object();
    private final OnClickListener listener;
    private int width, height;
    private int marqueeFirst = RecyclerView.NO_POSITION;
    private int marqueeLast = RecyclerView.NO_POSITION;
    private boolean animate;
    private boolean delete;

    public HistoryAdapter(OnClickListener listener) {
        this.listener = listener;
        this.animate = true;
    }

    public interface OnClickListener {

        void onItemClick(History item);

        void onItemDelete(History item);

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
        this.animate = false;
        this.delete = delete;
        notifyItemRangeChanged(0, getItemCount());
    }

    @Override
    public void clear() {
        super.clear();
        setDelete(false);
        History.deleteForDisplay();
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
        History item = getItem(position);
        boolean same = item.getVodName().equals(item.getVodRemarks());
        holder.binding.name.setText(item.getVodName());
        holder.binding.site.setText(item.getSiteName());
        holder.binding.remark.setText(item.getVodRemarks());
        holder.setMarquee(isMarqueePosition(position));
        holder.binding.site.setVisibility(item.getSiteVisible());
        holder.binding.playback.setText(item.getPlaybackTimeText());
        holder.binding.playback.setVisibility(!delete && item.hasPlaybackTime() ? View.VISIBLE : View.GONE);
        int duration = (int) Math.min(Integer.MAX_VALUE, Math.max(0, item.getDuration()));
        int progress = (int) Math.min(Integer.MAX_VALUE, Math.max(0, item.getPosition()));
        holder.binding.progress.setMax(duration > 0 ? duration : 1);
        holder.binding.progress.setProgress(duration > 0 ? Math.min(progress, duration) : 0, animate);
        holder.binding.delete.setVisibility(!delete ? View.GONE : View.VISIBLE);
        holder.binding.remark.setVisibility(same ? View.INVISIBLE : View.VISIBLE);
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

    private void setClickListener(View root, History item) {
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
