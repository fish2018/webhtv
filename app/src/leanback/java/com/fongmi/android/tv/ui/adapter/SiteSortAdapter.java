package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterSiteSortBinding;
import com.fongmi.android.tv.setting.SiteBlockSetting;
import com.fongmi.android.tv.setting.SiteOrderStore;

import java.util.ArrayList;
import java.util.List;

public class SiteSortAdapter extends RecyclerView.Adapter<SiteSortAdapter.ViewHolder> {

    private final List<Site> mItems;
    private final OnClickListener listener;

    public interface OnClickListener {
        void onItemClick(Site item);
    }

    public SiteSortAdapter(OnClickListener listener) {
        this.listener = listener;
        this.mItems = new ArrayList<>();
    }

    public void addAll(List<Site> sites) {
        mItems.clear();
        mItems.addAll(sites);
        notifyDataSetChanged();
    }

    public List<Site> getItems() {
        return mItems;
    }

    public void moveUp(int position) {
        if (position <= 0 || position >= mItems.size()) return;
        Site item = mItems.remove(position);
        mItems.add(position - 1, item);
        notifyItemMoved(position, position - 1);
        notifyItemRangeChanged(position - 1, 2);
        saveOrder();
    }

    public void moveToTop(int position) {
        if (position <= 0 || position >= mItems.size()) return;
        Site item = mItems.remove(position);
        mItems.add(0, item);
        notifyItemMoved(position, 0);
        notifyItemRangeChanged(0, position + 1);
        saveOrder();
    }

    public void moveDown(int position) {
        if (position < 0 || position >= mItems.size() - 1) return;
        Site item = mItems.remove(position);
        mItems.add(position + 1, item);
        notifyItemMoved(position, position + 1);
        notifyItemRangeChanged(position, 2);
        saveOrder();
    }

    public void toggleHide(int position) {
        if (position < 0 || position >= mItems.size()) return;
        Site item = mItems.get(position);
        SiteBlockSetting.toggle(item);
        notifyItemChanged(position);
    }

    private void saveOrder() {
        SiteOrderStore.save(mItems);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSiteSortBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Site item = mItems.get(position);
        holder.binding.name.setText(item.getName());
        String typeText = item.isFile() ? item.getFileType() : "API";
        holder.binding.type.setText(typeText);
        boolean hidden = SiteBlockSetting.isBlocked(item);
        holder.binding.toggle.setImageResource(hidden ? R.drawable.ic_player_button_hidden : R.drawable.ic_player_button_visible);
        holder.binding.toggle.setSelected(hidden);
        holder.binding.toggle.setActivated(hidden);
        holder.binding.name.setAlpha(hidden ? 0.4f : 1.0f);
        holder.binding.up.setEnabled(position > 0);
        holder.binding.down.setEnabled(position < mItems.size() - 1);
        holder.binding.top.setEnabled(position > 0);
        holder.binding.up.setAlpha(position > 0 ? 1.0f : 0.3f);
        holder.binding.down.setAlpha(position < mItems.size() - 1 ? 1.0f : 0.3f);
        holder.binding.top.setAlpha(position > 0 ? 1.0f : 0.3f);
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private final AdapterSiteSortBinding binding;

        ViewHolder(@NonNull AdapterSiteSortBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.top.setOnClickListener(v -> moveToTop(getLayoutPosition()));
            binding.up.setOnClickListener(v -> moveUp(getLayoutPosition()));
            binding.down.setOnClickListener(v -> moveDown(getLayoutPosition()));
            binding.toggle.setOnClickListener(v -> toggleHide(getLayoutPosition()));
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(mItems.get(getLayoutPosition())));
        }
    }
}
