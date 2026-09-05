package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.databinding.AdapterTrackBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TrackAdapter extends RecyclerView.Adapter<TrackAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Track> mItems;

    public TrackAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Track item);
    }

    public TrackAdapter addAll(List<Track> items) {
        mItems.addAll(items);
        notifyDataSetChanged();
        return this;
    }

    public void prependSelected(Track item) {
        if (item == null) return;
        for (Track existing : mItems) existing.setSelected(false);
        for (int i = 0; i < mItems.size(); i++) {
            if (!sameTrack(mItems.get(i), item)) continue;
            mItems.remove(i);
            break;
        }
        item.setSelected(true);
        mItems.add(0, item);
        notifyDataSetChanged();
    }

    private boolean sameTrack(Track first, Track second) {
        return first != null && second != null && Objects.equals(first.getName(), second.getName()) && Objects.equals(first.getFormat(), second.getFormat());
    }

    public void replaceAll(List<Track> items) {
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public int getSelected() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTrackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Track item = mItems.get(position);
        holder.binding.text.setText(item.getName());
        holder.binding.text.setSelected(item.isSelected());
    }

    public class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private final AdapterTrackBinding binding;

        public ViewHolder(@NonNull AdapterTrackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            listener.onItemClick(mItems.get(getLayoutPosition()).toggle());
        }
    }
}
