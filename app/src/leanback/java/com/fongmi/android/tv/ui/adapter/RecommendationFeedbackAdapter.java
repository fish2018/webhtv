package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterRecommendationFeedbackBinding;
import com.fongmi.android.tv.service.RecommendationFeedbackStore;
import com.fongmi.android.tv.ui.custom.CustomRecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecommendationFeedbackAdapter extends RecyclerView.Adapter<RecommendationFeedbackAdapter.ViewHolder> {

    public interface OnClickListener {
        void onItemClick(RecommendationFeedbackStore.Entry item);
    }

    private final List<RecommendationFeedbackStore.Entry> items = new ArrayList<>();
    private final OnClickListener listener;

    public RecommendationFeedbackAdapter(OnClickListener listener) {
        this.listener = listener;
        reload();
    }

    public int reload() {
        items.clear();
        items.addAll(RecommendationFeedbackStore.get());
        notifyDataSetChanged();
        return getItemCount();
    }

    public int indexOf(RecommendationFeedbackStore.Entry item) {
        return items.indexOf(item);
    }

    public void focusFirst(CustomRecyclerView recycler) {
        focus(recycler, 0);
    }

    public void focus(CustomRecyclerView recycler, int position) {
        if (items.isEmpty()) return;
        int safePosition = Math.max(0, Math.min(position, items.size() - 1));
        recycler.scrollToPosition(safePosition);
        recycler.postDelayed(() -> {
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(safePosition);
            if (holder != null) holder.itemView.requestFocus();
        }, 60);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRecommendationFeedbackBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RecommendationFeedbackStore.Entry item = items.get(position);
        Context context = holder.itemView.getContext();
        holder.binding.title.setText(item.getTitle());
        holder.binding.meta.setText(meta(context, item));
        holder.binding.getRoot().setNextFocusDownId(position == getItemCount() - 1 ? R.id.clearAll : View.NO_ID);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    private String meta(Context context, RecommendationFeedbackStore.Entry item) {
        List<String> values = new ArrayList<>();
        String mediaType = item.getMediaType().toLowerCase(Locale.ROOT);
        if ("tv".equals(mediaType)) values.add(context.getString(R.string.detail_media_tv));
        else if ("movie".equals(mediaType)) values.add(context.getString(R.string.detail_media_movie));
        if (item.getYear() > 0) values.add(String.valueOf(item.getYear()));
        values.add(context.getString(source(item.getSource())));
        values.add(context.getString(R.string.recommendation_feedback_remaining_days, RecommendationFeedbackStore.remainingDays(item)));
        values.add(context.getString(R.string.recommendation_feedback_restore_hint));
        return TextUtils.join(" · ", values);
    }

    private int source(String value) {
        switch (value.toLowerCase(Locale.ROOT)) {
            case "ai":
                return R.string.recommendation_source_ai;
            case "tmdb":
                return R.string.recommendation_source_tmdb;
            case "douban":
                return R.string.recommendation_source_douban;
            default:
                return R.string.recommendation_source_related;
        }
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRecommendationFeedbackBinding binding;

        public ViewHolder(@NonNull AdapterRecommendationFeedbackBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
