package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.databinding.AdapterTmdbPhotoBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class TmdbPhotoAdapter extends RecyclerView.Adapter<TmdbPhotoAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(String url);
    }

    private final Listener listener;
    private final List<String> items = new ArrayList<>();
    private boolean light;

    public TmdbPhotoAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<String> values) {
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
        return new ViewHolder(AdapterTmdbPhotoBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        String url = items.get(position);
        TmdbCardFocusHelper.bind(holder.binding.getRoot(), light ? 0xEEFFFFFF : 0xFF16202A, light ? 0x33647480 : 0x33FFFFFF);
        ImgUtil.load("tmdb_still_" + position, url, holder.binding.photo);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(url));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbPhotoBinding binding;

        ViewHolder(@NonNull AdapterTmdbPhotoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
