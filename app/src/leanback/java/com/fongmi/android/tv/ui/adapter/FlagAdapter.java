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
    private View.OnKeyListener mKeyListener;
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
        return mItems.indexOf(item);
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public Flag getActivated() {
        return mItems.get(getPosition());
    }

    public void setSelected(Flag item) {
        if (indexOf(item) == -1) item.setFlag(mItems.get(0).getFlag());
        for (Flag flag : mItems) flag.setSelected(item);
        notifyItemRangeChanged(0, getItemCount());
    }

    public void toggle(Episode item) {
        int flagPosition = getPosition();
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).toggle(flagPosition == i, item);
        notifyDataSetChanged();
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

    public void setOnKeyListener(View.OnKeyListener keyListener) {
        this.mKeyListener = keyListener;
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
        holder.binding.text.setOnKeyListener(mKeyListener);
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
