package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
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
    private final int spacing;
    private int nextFocusDown;
    private int nextFocusUp;
    private int column;

    public EpisodeAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(48);
        spacing = ResUtil.dp2px(8);
        column = 1;
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

    public int getSelectedPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return RecyclerView.NO_POSITION;
    }

    public int indexOf(Episode item) {
        return mItems.indexOf(item);
    }

    public void notifySelectionChanged(int oldPosition, int newPosition) {
        if (oldPosition != RecyclerView.NO_POSITION) notifyItemChanged(oldPosition);
        if (newPosition != RecyclerView.NO_POSITION && newPosition != oldPosition) notifyItemChanged(newPosition);
    }

    public Episode getActivated() {
        return mItems.isEmpty() ? new Episode() : mItems.get(getPosition());
    }

    public List<Episode> getItems() {
        return mItems;
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
        if (this.nextFocusDown == nextFocusDown) return;
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
    }

    public void setNextFocusUp(int nextFocusUp) {
        if (this.nextFocusUp == nextFocusUp) return;
        this.nextFocusUp = nextFocusUp;
        notifyDataSetChanged();
    }

    public void setColumn(int column) {
        column = Math.max(1, column);
        if (this.column == column) return;
        this.column = column;
        notifyDataSetChanged();
    }

    public int getColumn() {
        return column;
    }

    public static int getColumn(List<Episode> items) {
        int maxTextWidth = 0;
        int maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(48);
        int spacing = ResUtil.dp2px(8);
        int padding = ResUtil.dp2px(40);
        for (Episode item : items) maxTextWidth = Math.max(maxTextWidth, ResUtil.getTextWidth(item.getDesc().concat(item.getName()), 16) + padding);
        for (int candidate : new int[]{8, 6, 5, 4, 3, 2}) {
            int width = (maxWidth - spacing * (candidate - 1)) / candidate;
            if (maxTextWidth <= width) return candidate;
        }
        return 2;
    }

    private int getWidth() {
        return (maxWidth - spacing * (column - 1)) / column;
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
        holder.binding.text.getLayoutParams().width = getWidth();
        holder.binding.text.setNextFocusUpId(position < column && nextFocusUp != 0 ? nextFocusUp : View.NO_ID);
        holder.binding.text.setNextFocusDownId(position >= getItemCount() - column && nextFocusDown != 0 ? nextFocusDown : View.NO_ID);
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
