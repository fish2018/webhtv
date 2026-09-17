package com.fongmi.android.tv.ui.adapter;

import android.annotation.SuppressLint;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterDownloadEpisodeBinding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 下載彈窗中的劇集多選網格。
 */
public class DownloadEpisodeAdapter extends RecyclerView.Adapter<DownloadEpisodeAdapter.ViewHolder> {

    private final List<Episode> mItems;
    private final Set<Integer> mSelected;
    private final Set<Integer> mDownloaded;
    private final OnSelectListener mListener;

    public DownloadEpisodeAdapter(OnSelectListener listener) {
        this.mItems = new ArrayList<>();
        this.mSelected = new HashSet<>();
        this.mDownloaded = new HashSet<>();
        this.mListener = listener;
    }

    public interface OnSelectListener {
        void onSelectChange(int count);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void setItems(List<Episode> items, Set<Integer> downloaded) {
        mItems.clear();
        mItems.addAll(items);
        mSelected.clear();
        mDownloaded.clear();
        mDownloaded.addAll(downloaded);
        notifyDataSetChanged();
        notifySelect();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void selectAll() {
        for (int i = 0; i < mItems.size(); i++) if (!mDownloaded.contains(i)) mSelected.add(i);
        notifyDataSetChanged();
        notifySelect();
    }

    @SuppressLint("NotifyDataSetChanged")
    public void selectNone() {
        mSelected.clear();
        notifyDataSetChanged();
        notifySelect();
    }

    public boolean isAllSelected() {
        int count = 0;
        for (int i = 0; i < mItems.size(); i++) if (!mDownloaded.contains(i)) ++count;
        return count > 0 && mSelected.size() == count;
    }

    public List<Episode> getSelected() {
        List<Episode> result = new ArrayList<>();
        for (int index : mSelected) if (index < mItems.size()) result.add(mItems.get(index));
        return result;
    }

    private void notifySelect() {
        if (mListener != null) mListener.onSelectChange(mSelected.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterDownloadEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = mItems.get(position);
        boolean downloaded = mDownloaded.contains(position);
        boolean selected = mSelected.contains(position);
        holder.binding.text.setText(item.getName());
        // 選中態由 state_activated 驅動背景與文字高亮（selector 只認 activated，不認 selected）
        holder.binding.text.setActivated(selected);
        // 已下載項目禁用：對應 selector 的 state_enabled=false（文字變灰）
        holder.binding.text.setEnabled(!downloaded);
        holder.binding.text.setAlpha(downloaded ? 0.4f : 1f);
        // 跑馬燈：名稱過長時自動橫向滾動展示（參考 v2 實作）
        holder.binding.text.setHorizontallyScrolling(true);
        holder.binding.text.setMarqueeRepeatLimit(-1);
        holder.binding.text.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        holder.binding.text.setSelected(true);
        holder.binding.text.setOnClickListener(v -> {
            if (downloaded) return;
            if (mSelected.contains(position)) mSelected.remove(position);
            else mSelected.add(position);
            notifyItemChanged(position);
            notifySelect();
        });
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        public final AdapterDownloadEpisodeBinding binding;

        public ViewHolder(@NonNull AdapterDownloadEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
