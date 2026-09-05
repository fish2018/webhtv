package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * TMDB背景幻灯片适配器
 */
public class BackdropAdapter extends RecyclerView.Adapter<BackdropAdapter.ViewHolder> {

    private final List<String> mItems;

    public BackdropAdapter() {
        this.mItems = new ArrayList<>();
    }

    public void setItems(List<String> items) {
        mItems.clear();
        if (items != null && !items.isEmpty()) {
            mItems.addAll(items);
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_backdrop, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String imageUrl = mItems.get(position);
        Glide.with(holder.image.getContext())
                .load(imageUrl)
                .placeholder(R.color.black)
                .error(R.color.black)
                .into(holder.image);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final ImageView image;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.image = itemView.findViewById(R.id.image);
        }
    }
}
