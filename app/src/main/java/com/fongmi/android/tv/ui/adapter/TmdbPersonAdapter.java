package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.databinding.AdapterTmdbPersonBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class TmdbPersonAdapter extends RecyclerView.Adapter<TmdbPersonAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbPerson item);
    }

    private final Listener listener;
    private final List<TmdbPerson> items = new ArrayList<>();

    public TmdbPersonAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbPerson> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbPerson item = items.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.subtitle.setText(item.getSubtitle());
        holder.binding.name.setTextColor(0xFFFFFFFF);
        holder.binding.subtitle.setTextColor(0x99FFFFFF);
        TmdbCardFocusHelper.bind(holder.binding.getRoot(), 0xFF16202A, 0x33FFFFFF);
        ImgUtil.load(item.getName(), item.getProfileUrl(), holder.binding.photo);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbPersonBinding binding;

        ViewHolder(@NonNull AdapterTmdbPersonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
