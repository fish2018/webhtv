package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.setting.InterfaceOrderStore;
import com.fongmi.android.tv.databinding.AdapterConfigBinding;

import java.util.List;

public class ConfigAdapter extends RecyclerView.Adapter<ConfigAdapter.ViewHolder> {

    private final OnClickListener listener;
    private List<Config> mItems;
    private boolean readOnly;
    private boolean protectCurrent;
    private Config current;

    public ConfigAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onTextClick(Config item);

        boolean onTextLongClick(ViewHolder holder);

        void onEditClick(Config item);

        void onDeleteClick(Config item);
    }

    public ConfigAdapter readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public ConfigAdapter protectCurrent(boolean protectCurrent) {
        this.protectCurrent = protectCurrent;
        return this;
    }

    public ConfigAdapter addAll(int type) {
        return addAll(type, null);
    }

    public ConfigAdapter addAll(int type, Config current) {
        this.current = current;
        mItems = type == 0 ? InterfaceOrderStore.sortVodConfigs(Config.getAll(type)) : Config.getAll(type);
        String currentUrl = current == null ? null : current.getUrl();
        if (!readOnly && !protectCurrent && !TextUtils.isEmpty(currentUrl)) mItems.removeIf(item -> TextUtils.equals(item.getUrl(), currentUrl));
        return this;
    }

    private boolean isCurrent(Config item) {
        if (!protectCurrent || current == null) return false;
        if (current.getId() > 0 && item.getId() == current.getId()) return true;
        return current.getType() == item.getType()
                && !TextUtils.isEmpty(current.getInterfaceKey())
                && TextUtils.equals(current.getInterfaceKey(), item.getInterfaceKey());
    }

    public boolean isProtectedCurrent(Config item) {
        return isCurrent(item);
    }

    public int remove(Config item) {
        int position = mItems.indexOf(item);
        if (position == -1) return -1;
        item.delete();
        mItems.remove(position);
        if (item.getType() == 0) InterfaceOrderStore.saveVodConfigs(mItems);
        notifyItemRemoved(position);
        return getItemCount();
    }

    public boolean drag(int from, int to) {
        if (from < 0 || to < 0 || from >= mItems.size() || to >= mItems.size() || from == to) return false;
        Config item = mItems.remove(from);
        mItems.add(to, item);
        notifyItemMoved(from, to);
        if (mItems.size() > 0) InterfaceOrderStore.saveVodConfigs(mItems);
        return true;
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterConfigBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Config item = mItems.get(position);
        boolean current = isCurrent(item);
        holder.binding.text.setText(item.getDesc());
        holder.binding.text.setEnabled(!current);
        holder.binding.text.setFocusable(!current);
        holder.binding.text.setOnClickListener(v -> {
            if (!current) listener.onTextClick(item);
        });
        holder.binding.text.setOnLongClickListener(v -> !current && listener.onTextLongClick(holder));
        holder.binding.edit.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        holder.binding.edit.setOnClickListener(v -> listener.onEditClick(item));
        holder.binding.delete.setVisibility(readOnly ? View.GONE : View.VISIBLE);
        holder.binding.delete.setAlpha(current ? 0.38f : 1f);
        holder.binding.delete.setOnClickListener(v -> listener.onDeleteClick(item));
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterConfigBinding binding;

        public ViewHolder(@NonNull AdapterConfigBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
