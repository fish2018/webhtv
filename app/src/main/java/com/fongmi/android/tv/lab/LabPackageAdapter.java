package com.fongmi.android.tv.lab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

public final class LabPackageAdapter extends RecyclerView.Adapter<LabPackageAdapter.Holder> {

    public interface Listener {
        void onOpen(LabModels.Item item);

        void onLongPress(LabModels.Item item);
    }

    private final List<LabModels.Item> rows = new ArrayList<>();
    private final Listener listener;
    private final Context context;

    public LabPackageAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setRows(List<LabModels.Item> data) {
        rows.clear();
        rows.addAll(data);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.adapter_lab_package, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LabModels.Item item = rows.get(position);
        holder.title.setText(item.name + " " + displayVersion(item));
        holder.info.setText(item.info == null ? "" : item.info);
        String icon = item.icon == null ? "" : item.icon;
        if (icon.isEmpty()) {
            holder.icon.setImageResource(R.drawable.ic_logo);
        } else {
            Glide.with(context).load(icon).placeholder(R.drawable.ic_logo).error(R.drawable.ic_logo).into(holder.icon);
        }
        holder.version.setText(displayVersion(item));
        holder.size.setText(size(item));
        boolean installed = LabEnv.installed(context, item);
        boolean running = running(item);
        String installedVersion = LabConfig.get().getInstalledVersion(item.name);
        boolean hasNew = installed && !installedVersion.isEmpty() && LabEnv.compareVersions(displayVersion(item), installedVersion) > 0;
        holder.newVersion.setVisibility(hasNew ? View.VISIBLE : View.GONE);
        if (!item.available) {
            holder.status.setText("未上线");
            holder.status.setBackgroundResource(R.drawable.shape_lab_unavailable);
        } else if (running) {
            holder.status.setText(R.string.lab_running);
            holder.status.setBackgroundResource(R.drawable.shape_lab_running_tag);
        } else if (installed) {
            holder.status.setText(R.string.lab_installed);
            holder.status.setBackgroundResource(R.drawable.shape_lab_installed);
        } else {
            holder.status.setText(R.string.lab_not_installed);
            holder.status.setBackgroundResource(R.drawable.shape_lab_not_installed);
        }
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(item);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongPress(item);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    private boolean running(LabModels.Item item) {
        if (item.commands == null) return false;
        for (LabModels.Command command : item.commands) {
            if (LabRunner.isRunning(item.name + "/" + command.id)) return true;
        }
        return false;
    }

    private String displayVersion(LabModels.Item item) {
        if (item.downloads != null) {
            for (LabModels.Download download : item.downloads) {
                if (LabEnv.arch().equals(download.arch) && download.version != null && !download.version.isEmpty()) {
                    return download.version;
                }
            }
        }
        return item.version == null ? "" : item.version;
    }

    private String size(LabModels.Item item) {
        if (item.downloads != null) {
            for (LabModels.Download download : item.downloads) {
                if (LabEnv.arch().equals(download.arch) && download.size != null) return download.size;
            }
        }
        return "";
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView info;
        final TextView version;
        final TextView status;
        final TextView size;
        final TextView newVersion;
        final ImageView icon;

        Holder(View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.title);
            info = itemView.findViewById(R.id.info);
            version = itemView.findViewById(R.id.version);
            status = itemView.findViewById(R.id.status);
            size = itemView.findViewById(R.id.size);
            newVersion = itemView.findViewById(R.id.newVersion);
            icon = itemView.findViewById(R.id.icon);
        }
    }
}
