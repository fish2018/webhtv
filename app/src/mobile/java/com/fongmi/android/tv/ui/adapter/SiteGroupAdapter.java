package com.fongmi.android.tv.ui.adapter;

import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SiteGroupAdapter extends RecyclerView.Adapter<SiteGroupAdapter.ViewHolder> {

    private final OnClickListener listener;
    private final List<String> items;
    private String selectedGroup;

    public SiteGroupAdapter(OnClickListener listener) {
        this.listener = listener;
        this.items = new ArrayList<>();
        this.selectedGroup = "";
    }

    public interface OnClickListener {

        void onClick(String group, View view);
    }

    public void submit(List<String> groups, String selectedGroup) {
        this.selectedGroup = selectedGroup == null ? "" : selectedGroup;
        items.clear();
        if (groups != null) items.addAll(groups);
        notifyDataSetChanged();
    }

    public void select(String selectedGroup) {
        String value = selectedGroup == null ? "" : selectedGroup;
        if (Objects.equals(this.selectedGroup, value)) return;
        this.selectedGroup = value;
        notifyItemRangeChanged(0, getItemCount());
    }

    public boolean move(int from, int to) {
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false;
        if (from < 0 || to < 0 || from >= items.size() || to >= items.size() || from == to) return false;
        String group = items.remove(from);
        items.add(to, group);
        notifyItemMoved(from, to);
        return true;
    }

    public List<String> getItems() {
        return new ArrayList<>(items);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        MaterialButton button = new MaterialButton(parent.getContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(ResUtil.dp2px(8));
        button.setLayoutParams(params);
        button.setSingleLine(true);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(ResUtil.dp2px(14), ResUtil.dp2px(6), ResUtil.dp2px(14), ResUtil.dp2px(6));
        button.setTextColor(ContextCompat.getColorStateList(parent.getContext(), R.color.dialog_outlined_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(parent.getContext(), R.color.dialog_outlined_button_bg));
        button.setStrokeColor(ContextCompat.getColorStateList(parent.getContext(), R.color.dialog_outlined_button_stroke));
        return new ViewHolder(button);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String group = items.get(position);
        boolean selected = Objects.equals(group, selectedGroup);
        holder.button.setText(group);
        holder.button.setSelected(selected);
        holder.button.setAlpha(selectedGroup.isEmpty() || selected ? 1.0f : 0.5f);
        holder.button.setContentDescription(holder.button.getContext().getString(R.string.site_group_sort_desc, group));
        holder.button.setOnClickListener(v -> listener.onClick(group, holder.button));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialButton button;

        ViewHolder(MaterialButton button) {
            super(button);
            this.button = button;
        }
    }
}
