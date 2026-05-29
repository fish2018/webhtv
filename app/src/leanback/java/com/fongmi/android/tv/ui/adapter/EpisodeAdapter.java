package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.databinding.AdapterEpisodeBinding;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Episode> mItems;
    private final int maxWidth;
    private int nextFocusDown;
    private int nextFocusUp;

    public EpisodeAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(48);
    }

    public void addAll(List<Episode> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public Episode getActivated() {
        return mItems.isEmpty() ? new Episode() : mItems.get(getPosition());
    }

    public Episode getNext() {
        int current = getPosition();
        int max = getItemCount() - 1;
        current = ++current > max ? max : current;
        return mItems.get(current);
    }

    public Episode getPrev() {
        int current = getPosition();
        current = --current < 0 ? 0 : current;
        return mItems.get(current);
    }

    public void setNextFocusDown(int nextFocusDown) {
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
    }

    public void setNextFocusUp(int nextFocusUp) {
        this.nextFocusUp = nextFocusUp;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = mItems.get(position);
        holder.binding.text.setMaxWidth(maxWidth);
        holder.binding.text.setNextFocusUpId(nextFocusUp);
        holder.binding.text.setNextFocusDownId(nextFocusDown);
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setText(item.getDesc().concat(item.getDisplayName()));
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    public interface OnClickListener {

        void onItemClick(Episode item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterEpisodeBinding binding;

        ViewHolder(@NonNull AdapterEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
