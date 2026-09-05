package com.fongmi.android.tv.ui.adapter;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.ui.helper.TmdbCinemaTheme;
import com.fongmi.android.tv.ui.helper.TmdbRatingFormatter;
import com.fongmi.android.tv.ui.helper.TmdbRecommendationRows;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.google.android.material.card.MaterialCardView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class TmdbRailAdapter extends RecyclerView.Adapter<TmdbRailAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbItem item);
    }

    public interface LongClickListener {
        boolean onItemLongClick(TmdbItem item);
    }

    public interface FocusListener {
        void onItemFocus(TmdbItem item, boolean focused);
    }

    private final Listener listener;
    private final List<TmdbItem> items = new ArrayList<>();
    private LongClickListener longClickListener;
    private FocusListener focusListener;
    private boolean cinema;
    private boolean light;

    public TmdbRailAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setOnItemLongClickListener(LongClickListener listener) {
        this.longClickListener = listener;
    }

    public void setOnItemFocusListener(FocusListener listener) {
        this.focusListener = listener;
    }

    public void setItems(List<TmdbItem> values) {
        if (sameItems(values)) return;
        items.clear();
        if (values != null) items.addAll(values);
        notifyDataSetChanged();
    }

    public void removeItem(TmdbItem target) {
        for (int index = items.size() - 1; index >= 0; index--) {
            if (!sameIdentity(items.get(index), target)) continue;
            items.remove(index);
            notifyItemRemoved(index);
        }
    }

    /**
     * 直接重新绑定当前已附着的可见 ViewHolder，不依赖 RecyclerView 的布局遍历。
     * 用于 RecyclerView 嵌套在 NestedScrollView(wrap_content) 中、requestLayout 被祖先的
     * stuck layout 标志吞掉、notifyDataSetChanged 无法触发重绑的场景。用 getLayoutPosition()
     * 而非 getBindingAdapterPosition()：后者在有未派发的适配器更新时返回 NO_POSITION。
     */
    public void rebindAttached(RecyclerView recyclerView) {
        for (int index = 0; index < recyclerView.getChildCount(); index++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index));
            int position = holder.getLayoutPosition();
            if (!(holder instanceof ViewHolder) || position == RecyclerView.NO_POSITION || position >= items.size()) continue;
            onBindViewHolder((ViewHolder) holder, position);
        }
    }

    public void setCinema(boolean cinema) {
        this.cinema = cinema;
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return cinema ? 1 : 0;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layout = viewType == 1 ? R.layout.adapter_tmdb_rail_landscape : R.layout.adapter_tmdb_rail_item;
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(layout, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbItem item = items.get(position);
        CardMeta meta = CardMeta.from(item.getSubtitle());
        holder.title.setText(item.getTitle());
        holder.subtitle.setText(meta.subtitle);
        holder.subtitle.setVisibility(TextUtils.isEmpty(meta.subtitle) ? View.GONE : View.VISIBLE);
        TmdbRatingFormatter.Ratings ratings = TmdbRatingFormatter.completeRatings(item, meta.rating);
        holder.tmdbRating.setText(ratings.getTmdb());
        holder.tmdbRating.setVisibility(View.VISIBLE);
        holder.tmdbRating.setAlpha(ratings.hasTmdbRating() ? 1.0f : 0.55f);
        holder.doubanRating.setText(ratings.getDouban());
        holder.doubanRating.setVisibility(View.VISIBLE);
        holder.doubanRating.setAlpha(ratings.hasDoubanRating() ? 1.0f : 0.55f);
        holder.ratingGroup.setVisibility(View.VISIBLE);
        TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(light);
        holder.title.setTextColor(0xFFFFFFFF);
        holder.subtitle.setTextColor(cinema ? 0xB3FFFFFF : 0x99FFFFFF);
        holder.tmdbRating.setTextColor(0xFFFFD35C);
        holder.doubanRating.setTextColor(0xFF78E08F);
        TmdbCardFocusHelper.bind(holder.root, cinema ? 0xB314202A : 0xFF16202A, cinema ? palette.cardStroke() : 0x33FFFFFF, 1, focused -> {
            if (focusListener != null) focusListener.onItemFocus(item, focused);
        });
        String image = TmdbImageSelector.cardImage(item, cinema);
        String fallbackImage = TmdbImageSelector.cardImage(item, !cinema);
        ImgUtil.load(item.getTitle(), image, fallbackImage, holder.poster, true, cinema ? 552 : 300, cinema ? 312 : 450);
        holder.root.setOnClickListener(view -> listener.onItemClick(item));
        holder.root.setOnLongClickListener(view -> longClickListener != null && longClickListener.onItemLongClick(item));
        if (holder.root.hasFocus() && focusListener != null) focusListener.onItemFocus(item, true);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private boolean sameItems(List<TmdbItem> values) {
        if (values == null) return items.isEmpty();
        if (items.size() != values.size()) return false;
        for (int i = 0; i < items.size(); i++) {
            if (!sameContent(items.get(i), values.get(i))) return false;
        }
        return true;
    }

    static boolean sameContent(TmdbItem first, TmdbItem second) {
        if (first == second) return true;
        if (!sameIdentity(first, second)) return false;
        return first.getTmdbId() == second.getTmdbId()
                && Objects.equals(first.getMediaType(), second.getMediaType())
                && Objects.equals(first.getTitle(), second.getTitle())
                && Objects.equals(first.getSubtitle(), second.getSubtitle())
                && Objects.equals(first.getOverview(), second.getOverview())
                && Objects.equals(first.getRecommendationReason(), second.getRecommendationReason())
                && Objects.equals(first.getPosterUrl(), second.getPosterUrl())
                && Objects.equals(first.getBackdropUrl(), second.getBackdropUrl())
                && Objects.equals(first.getCredit(), second.getCredit())
                && Double.compare(first.getRating(), second.getRating()) == 0
                && Double.compare(first.getTmdbRating(), second.getTmdbRating()) == 0
                && Double.compare(first.getDoubanRating(), second.getDoubanRating()) == 0
                && Objects.equals(first.getOriginalLanguage(), second.getOriginalLanguage())
                && Objects.equals(first.getOriginCountry(), second.getOriginCountry())
                && Objects.equals(first.getGenreIds(), second.getGenreIds())
                && Objects.equals(first.getDepartment(), second.getDepartment());
    }

    private static boolean sameIdentity(TmdbItem first, TmdbItem second) {
        return TmdbRecommendationRows.sameIdentity(first, second);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final MaterialCardView root;
        private final AppCompatImageView poster;
        private final TextView title;
        private final TextView subtitle;
        private final View ratingGroup;
        private final TextView tmdbRating;
        private final TextView doubanRating;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            root = (MaterialCardView) itemView;
            poster = itemView.findViewById(R.id.poster);
            title = itemView.findViewById(R.id.title);
            subtitle = itemView.findViewById(R.id.subtitle);
            ratingGroup = itemView.findViewById(R.id.ratingGroup);
            tmdbRating = itemView.findViewById(R.id.tmdbRating);
            doubanRating = itemView.findViewById(R.id.doubanRating);
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
            return new CardMeta(TextUtils.join(" · ", meta), rating);
        }
    }
}
