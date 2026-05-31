package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.AdapterTmdbRailItemBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TmdbRailAdapter extends RecyclerView.Adapter<TmdbRailAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    private final Listener listener;
    private final List<TmdbItem> items = new ArrayList<>();

    public TmdbRailAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbItem> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbRailItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbItem item = items.get(position);
        CardMeta meta = CardMeta.from(item.getSubtitle());
        holder.binding.title.setText(item.getTitle());
        holder.binding.subtitle.setText(meta.subtitle);
        holder.binding.subtitle.setVisibility(TextUtils.isEmpty(meta.subtitle) ? View.GONE : View.VISIBLE);
        holder.binding.rating.setText(meta.rating);
        holder.binding.rating.setVisibility(TextUtils.isEmpty(meta.rating) ? View.GONE : View.VISIBLE);
        holder.binding.title.setTextColor(0xFFFFFFFF);
        holder.binding.subtitle.setTextColor(0x99FFFFFF);
        holder.binding.rating.setTextColor(0xFFFFFFFF);
        TmdbCardFocusHelper.bind(holder.binding.getRoot(), 0xFF16202A, 0x33FFFFFF);
        ImgUtil.load(item.getTitle(), item.getPosterUrl(), holder.binding.poster);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbRailItemBinding binding;

        ViewHolder(@NonNull AdapterTmdbRailItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }

    private record CardMeta(String subtitle, String rating) {

        static CardMeta from(String subtitle) {
            if (TextUtils.isEmpty(subtitle)) return new CardMeta("", "");
            List<String> meta = new ArrayList<>();
            String rating = "";
            for (String raw : subtitle.split("[·路]")) {
                String part = raw.trim();
                if (TextUtils.isEmpty(part)) continue;
                String lower = part.toLowerCase(Locale.ROOT);
                if (part.startsWith("评分") || lower.startsWith("score")) {
                    rating = part.replace("评分", "").replace("Score", "").replace("score", "").trim();
                } else {
                    meta.add(part);
                }
            }
            if (!TextUtils.isEmpty(rating)) rating = "★ " + rating;
            return new CardMeta(TextUtils.join(" · ", meta), rating);
        }
    }
}
