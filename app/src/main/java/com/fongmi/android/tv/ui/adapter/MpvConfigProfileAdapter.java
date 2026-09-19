package com.fongmi.android.tv.ui.adapter;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterMpvConfigProfileBinding;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.utils.Util;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class MpvConfigProfileAdapter extends RecyclerView.Adapter<MpvConfigProfileAdapter.ViewHolder> {

    public interface Listener {
        void onSelect(MpvConfigStore.ConfigProfile profile);

        void onMore(View anchor, MpvConfigStore.ConfigProfile profile);
    }

    private final List<MpvConfigStore.ConfigProfile> items = new ArrayList<>();
    private final Listener listener;
    private boolean scripts;

    public MpvConfigProfileAdapter(Listener listener) {
        this.listener = listener;
        setHasStableIds(true);
    }

    public void submit(List<MpvConfigStore.ConfigProfile> profiles, boolean scripts) {
        items.clear();
        if (profiles != null) items.addAll(profiles);
        this.scripts = scripts;
        notifyDataSetChanged();
    }

    @Override
    public long getItemId(int position) {
        if (position < 0 || position >= items.size() || items.get(position).id == null) return RecyclerView.NO_ID;
        return items.get(position).id.hashCode() & 0xffffffffL;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterMpvConfigProfileBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        MpvConfigStore.ConfigProfile profile = items.get(position);
        boolean disabled = scripts && !profile.scriptEnabled;
        holder.binding.name.setText(profile.name);
        holder.binding.name.setTextColor(Color.parseColor(disabled ? "#8A8D91" : "#202124"));
        holder.binding.detail.setText(detail(profile));
        holder.binding.detail.setTextColor(Color.parseColor(disabled ? "#9AA0A6" : "#80868B"));
        holder.binding.active.setVisibility(scripts || profile.active ? View.VISIBLE : View.GONE);
        holder.binding.active.setText(scripts
                ? (disabled ? R.string.setting_disable : R.string.setting_enable) : R.string.mpv_config_in_use);
        holder.binding.active.setTextColor(Color.parseColor(scripts ? (disabled ? "#6F7378" : "#137333") : "#0B57D0"));
        holder.binding.active.setBackgroundTintList(scripts
                ? ColorStateList.valueOf(Color.parseColor(disabled ? "#E8EAED" : "#E6F4EA")) : null);
        holder.binding.root.setBackgroundResource(disabled
                ? R.drawable.selector_mpv_script_disabled_card : R.drawable.selector_mpv_profile_card);
        holder.binding.root.setSelected(!scripts && profile.active);
        holder.binding.root.setFocusable(true);
        holder.binding.root.setFocusableInTouchMode(Util.isLeanback());
        holder.binding.more.setFocusable(true);
        holder.binding.more.setFocusableInTouchMode(Util.isLeanback());
        holder.binding.root.setOnClickListener(view -> listener.onSelect(profile));
        holder.binding.more.setOnClickListener(view -> listener.onMore(view, profile));
        holder.binding.root.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) return holder.binding.more.requestFocus();
            return false;
        });
        holder.binding.more.setOnKeyListener((view, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return holder.binding.root.requestFocus();
            return false;
        });
    }

    private String detail(MpvConfigStore.ConfigProfile profile) {
        if (profile.isDefault()) return profile.typeLabel();
        String time = profile.time <= 0 ? "" : DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(profile.time));
        return time.isEmpty() ? profile.typeLabel() : time + " · " + profile.typeLabel();
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterMpvConfigProfileBinding binding;

        ViewHolder(AdapterMpvConfigProfileBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
