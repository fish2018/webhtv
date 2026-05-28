package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.databinding.AdapterSyncDeviceBinding;

public class SyncDeviceAdapter extends BaseDiffAdapter<Device, SyncDeviceAdapter.ViewHolder> {

    private final OnClickListener listener;

    public SyncDeviceAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterSyncDeviceBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Device item = getItem(position);
        holder.binding.type.setText(item.isMobile() ? R.string.sync_device_mobile : R.string.sync_device_tv);
        holder.binding.name.setText(item.getName());
        holder.binding.host.setText(item.getHost());
        holder.binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
    }

    public interface OnClickListener {

        void onItemClick(Device item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSyncDeviceBinding binding;

        ViewHolder(@NonNull AdapterSyncDeviceBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
