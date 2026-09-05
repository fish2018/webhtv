package com.fongmi.android.tv.lab;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;

import java.util.ArrayList;
import java.util.List;

public final class LabCommandAdapter extends RecyclerView.Adapter<LabCommandAdapter.Holder> {

    public interface Listener {
        void onOpen(LabModels.Item item, LabModels.Command command);

        void onAction(LabModels.Item item, LabModels.Command command);

        void onLongPress(LabModels.Item item, LabModels.Command command);
    }

    private final List<LabModels.Command> rows = new ArrayList<>();
    private final Listener listener;
    private final Context context;
    private LabModels.Item item;

    public LabCommandAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setItem(LabModels.Item item) {
        this.item = item;
        rows.clear();
        if (item != null) {
            if (item.commands != null) rows.addAll(item.commands);
            List<LabCustomCommands.CustomCommand> customs = LabCustomCommands.list(item.name);
            for (int i = 0; i < customs.size(); i++) {
                LabCustomCommands.CustomCommand custom = customs.get(i);
                LabModels.Command command = new LabModels.Command();
                command.id = custom.id != null ? custom.id : ("custom_" + i);
                command.name = custom.name;
                command.description = custom.description;
                command.command = custom.command;
                command.auto_execute = custom.autoExecute;
                rows.add(command);
            }
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.adapter_lab_command_compact, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LabModels.Command command = rows.get(position);
        boolean running = item != null && LabRunner.isRunning(item.name + "/" + command.id);
        holder.name.setText(command.name == null ? command.description : command.name);
        holder.statusDot.setBackgroundResource(running ? R.drawable.shape_lab_status_dot_running : R.drawable.shape_lab_status_dot_idle);
        holder.runningTag.setVisibility(running ? View.VISIBLE : View.GONE);
        holder.btnAction.setText(running ? R.string.lab_stop : R.string.lab_run);
        holder.btnAction.setBackgroundResource(running ? R.drawable.shape_lab_stop_btn : R.drawable.shape_lab_run_btn);
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(item, command);
        });
        holder.btnAction.setOnClickListener(v -> {
            if (listener != null) listener.onAction(item, command);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onLongPress(item, command);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final View statusDot;
        final TextView name;
        final TextView runningTag;
        final TextView btnAction;

        Holder(View itemView) {
            super(itemView);
            statusDot = itemView.findViewById(R.id.statusDot);
            name = itemView.findViewById(R.id.name);
            runningTag = itemView.findViewById(R.id.runningTag);
            btnAction = itemView.findViewById(R.id.btnAction);
        }
    }
}
