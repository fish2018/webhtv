package com.fongmi.android.tv.ui.adapter;

import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterHomeMenuBinding;
import com.fongmi.android.tv.ui.dialog.BaseGlassDialog;

public class HomeMenuAdapter extends RecyclerView.Adapter<HomeMenuAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final String[] items;
    private final int spanCount;

    public HomeMenuAdapter(OnClickListener listener, String[] items, int spanCount) {
        this.listener = listener;
        this.items = items;
        this.spanCount = spanCount;
    }

    public interface OnClickListener {
        void onItemClick(int position);
    }

    @Override
    public int getItemCount() {
        return items.length;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterHomeMenuBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.binding.text.setText(items[position]);
        holder.binding.text.setBackground(BaseGlassDialog.glassItemBackground());
        holder.binding.text.setOnClickListener(v -> listener.onItemClick(position));
        holder.binding.text.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int row = position / spanCount;
            int col = position % spanCount;
            int totalRows = (int) Math.ceil((double) items.length / spanCount);
            int rowStart = row * spanCount;
            int rowEnd = Math.min(rowStart + spanCount - 1, items.length - 1);
            int target = -1;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && row == 0) {
                target = (totalRows - 1) * spanCount + col;
                if (target >= items.length) target = items.length - 1;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && row == totalRows - 1) {
                target = col;
                if (target >= items.length) target = 0;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && position == rowStart) {
                target = rowEnd;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && position == rowEnd) {
                target = rowStart;
            }
            if (target >= 0 && target != position && target < items.length) {
                RecyclerView rv = (RecyclerView) holder.itemView.getParent();
                RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(target);
                if (vh != null) {
                    vh.itemView.requestFocus();
                    return true;
                }
            }
            return false;
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterHomeMenuBinding binding;

        public ViewHolder(@NonNull AdapterHomeMenuBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
