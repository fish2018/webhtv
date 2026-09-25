package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterThemeBinding;
import com.fongmi.android.tv.setting.Setting;
import com.github.bassaer.library.MDColor;

public class ThemeAdapter extends RecyclerView.Adapter<ThemeAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final int selected;
    private final int[] mItems;

    public ThemeAdapter(OnClickListener listener, int[] items, int selected) {
        this.listener = listener;
        this.selected = selected;
        this.mItems = items;
    }

    public interface OnClickListener {

        void onItemClick(int color);
    }

    @Override
    public int getItemCount() {
        return mItems.length;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterThemeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        int color = mItems[position];
        holder.binding.circle.setBackground(getCircle(color));
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(color));
        holder.binding.check.setVisibility(selected == color ? View.VISIBLE : View.INVISIBLE);
        holder.binding.close.setVisibility(selected != color && color == -1 ? View.VISIBLE : View.GONE);
    }

    private GradientDrawable getCircle(int color) {
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        if (color == 0) circle.setColor(Setting.getWallColor());
        else if (color == -1) circle.setColor(MDColor.GREY_500);
        else circle.setColor(color);
        return circle;
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterThemeBinding binding;

        ViewHolder(@NonNull AdapterThemeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
