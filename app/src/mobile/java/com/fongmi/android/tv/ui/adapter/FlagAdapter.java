package com.fongmi.android.tv.ui.adapter;

import android.view.View;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FlagAdapter extends RecyclerView.Adapter<FlagAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<Flag> mItems;
    private boolean tmdbStyle;
    private boolean tmdbLight = true;

    public FlagAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public interface OnClickListener {

        void onItemClick(Flag item);
    }

    public void setTmdbStyle(boolean tmdbStyle) {
        if (this.tmdbStyle == tmdbStyle) return;
        this.tmdbStyle = tmdbStyle;
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setTmdbLight(boolean tmdbLight) {
        if (this.tmdbLight == tmdbLight) return;
        this.tmdbLight = tmdbLight;
        notifyItemRangeChanged(0, getItemCount());
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

    public int indexOf(Flag flag) {
        if (flag == null) return -1;
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i) == flag) return i;
        return mItems.indexOf(flag);
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public Flag get(int position) {
        return mItems.get(position);
    }

    public List<Flag> getItems() {
        return mItems;
    }

    public Flag getActivated() {
        return mItems.isEmpty() ? new Flag() : mItems.get(getPosition());
    }

    public void setSelected(Flag flag) {
        if (mItems.isEmpty() || flag == null) return;
        int position = indexOf(flag);
        if (position == -1) position = 0;
        for (int i = 0; i < mItems.size(); i++) mItems.get(i).setSelected(i == position);
        notifyItemRangeChanged(0, getItemCount());
    }

    public void toggle(Episode episode) {
        for (Flag item : mItems) item.toggle(item.isSelected(), episode);
    }

    public void reverse() {
        for (Flag item : mItems) Collections.reverse(item.getEpisodes());
    }

    public boolean isEmpty() {
        return getItemCount() == 0;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return tmdbStyle ? 1 : 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.adapter_flag_tmdb : R.layout.adapter_flag;
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Flag item = mItems.get(position);
        holder.text.setText(item.getShow());
        holder.text.setSelected(item.isSelected());
        applyTmdbTheme(holder.text);
        holder.text.setOnClickListener(v -> listener.onItemClick(item));
    }

    private void applyTmdbTheme(TextView text) {
        if (!tmdbStyle) return;
        text.setBackgroundResource(tmdbLight ? R.drawable.selector_tmdb_flag_item : R.drawable.selector_tmdb_flag_item_dark);
        text.setTextColor(ContextCompat.getColorStateList(text.getContext(), tmdbLight ? R.color.selector_tmdb_flag_text : R.color.selector_tmdb_flag_text_dark));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView text;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.text = itemView.findViewById(R.id.text);
        }
    }
}
