package com.fongmi.android.tv.ui.adapter;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.DownloadItem;
import com.fongmi.android.tv.databinding.AdapterDownloadEpisodeListBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 下載詳情頁的劇集列表，顯示每一集的下載進度與操作按鈕。
 */
public class DownloadEpisodeListAdapter extends RecyclerView.Adapter<DownloadEpisodeListAdapter.ViewHolder> {

    private final List<DownloadItem> mItems;
    private final OnClickListener mListener;

    public DownloadEpisodeListAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.mListener = listener;
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<DownloadItem> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public List<DownloadItem> getItems() {
        return mItems;
    }

    /**
     * 只刷新單一項目，避免整表重繪造成閃爍。
     */
    public void update(DownloadItem item) {
        int index = mItems.indexOf(item);
        if (index == -1) return;
        mItems.set(index, item);
        notifyItemChanged(index);
    }

    public interface OnClickListener {

        void onItemClick(DownloadItem item);

        void onItemAction(DownloadItem item);

        void onItemDelete(DownloadItem item);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadEpisodeListBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DownloadItem item = mItems.get(position);
        holder.binding.name.setText(item.getEpisodeName());
        holder.binding.progress.setProgress(item.getProgress());
        holder.binding.progress.setVisibility(item.isDone() ? android.view.View.GONE : android.view.View.VISIBLE);
        holder.binding.state.setText(getState(item));
        holder.binding.speed.setText(getSpeed(item));
        holder.binding.btnAction.setText(getAction(item));
        holder.binding.btnAction.setOnClickListener(v -> mListener.onItemAction(item));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
        holder.binding.getRoot().setOnLongClickListener(v -> {
            mListener.onItemDelete(item);
            return true;
        });
    }

    private String getState(DownloadItem item) {
        switch (item.getState()) {
            case DownloadItem.STATE_DONE:
                return ResUtil.getString(R.string.download_done);
            case DownloadItem.STATE_PAUSED:
                return ResUtil.getString(R.string.download_paused);
            case DownloadItem.STATE_ERROR:
                return ResUtil.getString(R.string.download_failed);
            case DownloadItem.STATE_WAITING:
                return ResUtil.getString(R.string.download_queued);
            default:
                return item.getProgress() + "%";
        }
    }

    private String getAction(DownloadItem item) {
        switch (item.getState()) {
            case DownloadItem.STATE_DONE:
                return ResUtil.getString(R.string.download_play);
            case DownloadItem.STATE_ERROR:
                return ResUtil.getString(R.string.download_retry);
            case DownloadItem.STATE_PAUSED:
                return ResUtil.getString(R.string.download_resume);
            default:
                return ResUtil.getString(R.string.download_pause);
        }
    }

    private String getSpeed(DownloadItem item) {
        if (item.isDone()) return size(item.getCurrent());
        if (!item.isRunning() || item.getSpeed() <= 0) return "";
        return size(item.getSpeed()) + "/s";
    }

    private String size(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + "B";
        if (bytes < 1024 * 1024) return String.format(Locale.getDefault(), "%.1fKB", bytes / 1024f);
        if (bytes < 1024 * 1024 * 1024) return String.format(Locale.getDefault(), "%.1fMB", bytes / 1024f / 1024f);
        return String.format(Locale.getDefault(), "%.2fGB", bytes / 1024f / 1024f / 1024f);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterDownloadEpisodeListBinding binding;

        public ViewHolder(@NonNull AdapterDownloadEpisodeListBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
