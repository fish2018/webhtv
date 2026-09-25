package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.databinding.AdapterFlagBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlagAdapter extends RecyclerView.Adapter<FlagAdapter.ViewHolder> {

    private final OnClickListener mListener;
    private final List<Flag> mItems;
    private View.OnKeyListener keyListener;
    private int nextFocusDown;

    public FlagAdapter(OnClickListener listener) {
        mListener = listener;
        mItems = new ArrayList<>();
        nextFocusDown = R.id.episode;
    }

    public void addAll(List<Flag> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void add(Flag item) {
        mItems.add(item);
        notifyItemInserted(mItems.size() - 1);
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public Flag get(int position) {
        return mItems.get(position);
    }

    public List<Flag> getItems() {
        return mItems;
    }

    public int indexOf(Flag item) {
        if (item == null) return -1;
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i) == item) return i;
        return mItems.indexOf(item);
    }

    public int getPosition() {
        int position = getSelectedPosition();
        return position == RecyclerView.NO_POSITION ? 0 : position;
    }

    public int getSelectedPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return RecyclerView.NO_POSITION;
    }

    public Flag getActivated() {
        return mItems.isEmpty() ? new Flag() : mItems.get(getPosition());
    }

    public void setSelected(Flag item) {
        if (mItems.isEmpty() || item == null) return;
        int position = indexOf(item);
        if (position == -1) position = 0;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
    }

    public void toggle(Episode item) {
        // 线路焦点回调可能发生在 RecyclerView 布局期间，此处只同步选集状态，不通知线路列表。
        int flagPosition = getPosition();
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).toggle(flagPosition == i, item);
    }

    public void reverse() {
        for (Flag flag : mItems) Collections.reverse(flag.getEpisodes());
        notifyDataSetChanged();
    }

    public void setNextFocusDown(int nextFocusDown) {
        if (this.nextFocusDown == nextFocusDown) return;
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    public void setOnKeyListener(View.OnKeyListener keyListener) {
        this.keyListener = keyListener;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterFlagBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Flag item = mItems.get(position);
        holder.binding.text.setText(item.getShow());
        holder.binding.text.setSelected(item.isSelected());
        holder.binding.text.setNextFocusDownId(nextFocusDown);
        holder.binding.text.setOnKeyListener(keyListener);
        holder.binding.getRoot().setOnClickListener(v -> mListener.onItemClick(item));
    }

    public interface OnClickListener {

        void onItemClick(Flag item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterFlagBinding binding;

        ViewHolder(@NonNull AdapterFlagBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
