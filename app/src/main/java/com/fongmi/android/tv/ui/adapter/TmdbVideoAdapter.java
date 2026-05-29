package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.databinding.AdapterTmdbVideoBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class TmdbVideoAdapter extends RecyclerView.Adapter<TmdbVideoAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbVideo item);
    }

    private final Listener listener;
    private final List<TmdbVideo> items = new ArrayList<>();
    private boolean light;

    public TmdbVideoAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbVideo> values) {
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbVideoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbVideo item = items.get(position);
        holder.binding.title.setText(item.getName());
        holder.binding.subtitle.setText(item.getSubtitle());
        holder.binding.subtitle.setVisibility(TextUtils.isEmpty(item.getSubtitle()) ? View.GONE : View.VISIBLE);
        holder.binding.title.setTextColor(light ? 0xFF15202B : 0xFFFFFFFF);
        holder.binding.subtitle.setTextColor(light ? 0x9915202B : 0x99FFFFFF);
        TmdbCardFocusHelper.bind(holder.binding.getRoot(), light ? 0xEEFFFFFF : 0xFF16202A, light ? 0x33647480 : 0x33FFFFFF);
        ImgUtil.load(item.getName(), item.getThumbnailUrl(), holder.binding.thumbnail);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbVideoBinding binding;

        ViewHolder(@NonNull AdapterTmdbVideoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
