package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterTmdbEpisodeBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TmdbEpisodeAdapter extends RecyclerView.Adapter<TmdbEpisodeAdapter.ViewHolder> {

    public enum Mode {
        LIST,
        GRID
    }

    public interface Listener {
        void onItemClick(Episode item);

        void onItemLongClick(Episode item, int episodeNumber);
    }

    private final Listener listener;
    private final List<Episode> items = new ArrayList<>();
    private final Map<Integer, TmdbEpisode> tmdbItems = new HashMap<>();
    private final Map<Episode, Integer> episodeNumbers = new HashMap<>();
    private Episode selected;
    private Mode mode = Mode.LIST;
    private boolean light;
    private boolean compactPlain;
    private int activeStrokeColor = 0xFF2CC56F;

    public TmdbEpisodeAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Episode selected) {
        setItems(episodes, tmdbEpisodes, Map.of(), selected);
    }

    public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers, Episode selected) {
        items.clear();
        items.addAll(episodes);
        tmdbItems.clear();
        tmdbItems.putAll(tmdbEpisodes);
        episodeNumbers.clear();
        episodeNumbers.putAll(numbers);
        compactPlain = items.size() == 1 && tmdbItems.isEmpty() && TextUtils.isEmpty(items.get(0).getDesc());
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setSelected(Episode selected) {
        this.selected = selected;
        notifyDataSetChanged();
    }

    public void setLight(boolean light) {
        this.light = light;
        notifyDataSetChanged();
    }

    public void setActiveStrokeColor(int activeStrokeColor) {
        this.activeStrokeColor = activeStrokeColor;
        notifyDataSetChanged();
    }

    public void setMode(Mode mode) {
        this.mode = mode == null ? Mode.LIST : mode;
        notifyDataSetChanged();
    }

    public int getPosition(Episode episode) {
        return items.indexOf(episode);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbEpisodeBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode episode = items.get(position);
        int episodeNumber = episodeNumber(episode, position);
        TmdbEpisode tmdbEpisode = tmdbItems.get(episodeNumber);
        String tmdbTitle = tmdbEpisode != null ? tmdbEpisode.getTitle() : "";
        String title = episodeTitle(episode, episodeNumber, tmdbTitle);
        String date = tmdbEpisode != null ? tmdbEpisode.getDate() : "";
        String overview = tmdbEpisode != null ? tmdbEpisode.getOverview() : episode.getDesc();
        boolean activated = episode.equals(selected);
        boolean compact = compactPlain && tmdbEpisode == null && TextUtils.isEmpty(overview);
        boolean hasStill = mode == Mode.LIST && tmdbEpisode != null && !TextUtils.isEmpty(tmdbEpisode.getStillUrl());

        applyCardSize(holder, compact);
        if (mode == Mode.GRID) {
            holder.binding.index.setText(title);
            holder.binding.index.setTextSize(14f);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(date);
            holder.binding.date.setVisibility(TextUtils.isEmpty(date) ? View.GONE : View.VISIBLE);
            holder.binding.overview.setVisibility(View.GONE);
        } else if (compact) {
            holder.binding.index.setText(title);
            holder.binding.index.setTextSize(14f);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setVisibility(View.GONE);
            holder.binding.overview.setVisibility(View.GONE);
        } else {
            holder.binding.index.setText(title);
            holder.binding.index.setTextSize(12f);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(date);
            holder.binding.date.setVisibility(TextUtils.isEmpty(date) ? View.GONE : View.VISIBLE);
            holder.binding.overview.setText(overview);
            holder.binding.overview.setVisibility(TextUtils.isEmpty(overview) ? View.GONE : View.VISIBLE);
        }
        holder.binding.index.setTextColor(hasStill || !light ? 0xFFFFFFFF : 0xFF15202B);
        holder.binding.title.setTextColor(hasStill || !light ? 0xE6FFFFFF : 0xCC15202B);
        holder.binding.date.setTextColor(hasStill || !light ? 0xCCFFFFFF : 0x9915202B);
        holder.binding.overview.setTextColor(hasStill || !light ? 0xE6FFFFFF : 0xB315202B);
        holder.binding.badge.setText(episodeBadge(tmdbEpisode));
        holder.binding.badge.setVisibility(TextUtils.isEmpty(holder.binding.badge.getText()) ? View.GONE : View.VISIBLE);
        applyBadgeStyle(holder.binding.date, hasStill);
        applyBadgeStyle(holder.binding.badge, hasStill);
        TmdbCardFocusHelper.bind(
                holder.binding.getRoot(),
                activated ? (light ? 0xFFE5F7EC : 0x6630A86B) : (light ? 0xEEFFFFFF : 0xCC16202A),
                activated ? activeStrokeColor : (light ? 0x33647480 : 0x33FFFFFF),
                activated ? 2 : 1);
        if (hasStill) {
            holder.binding.stillFrame.setVisibility(View.VISIBLE);
            ImgUtil.load(title, tmdbEpisode.getStillUrl(), holder.binding.still);
        } else {
            holder.binding.stillFrame.setVisibility(View.GONE);
        }
        holder.binding.scrim.setVisibility(hasStill ? View.VISIBLE : View.GONE);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(episode));
        holder.binding.getRoot().setOnLongClickListener(view -> {
            listener.onItemLongClick(episode, episodeNumber);
            return true;
        });
    }

    private void applyCardSize(ViewHolder holder, boolean compact) {
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        if (mode == Mode.GRID) {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT;
            params.height = dp(holder.itemView, 86);
        } else {
            params.width = compact ? dp(holder.itemView, 220) : listCardWidth(holder.itemView);
            params.height = dp(holder.itemView, compact ? 78 : (isPhoneWidth(holder.itemView) ? 172 : 190));
        }
        holder.binding.getRoot().setLayoutParams(params);
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMarginEnd(dp(holder.itemView, mode == Mode.GRID ? 8 : 12));
            marginParams.bottomMargin = dp(holder.itemView, mode == Mode.GRID ? 10 : 0);
            holder.binding.getRoot().setLayoutParams(marginParams);
        }
    }

    private int listCardWidth(View view) {
        if (!isPhoneWidth(view)) return dp(view, 230);
        int screen = view.getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(view, 168), Math.min(dp(view, 230), (screen - dp(view, 56)) / 2));
    }

    private boolean isPhoneWidth(View view) {
        return view.getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private void applyBadgeStyle(TextView view, boolean hasStill) {
        boolean darkBadge = hasStill || !light;
        view.setTextColor(darkBadge ? 0xE6FFFFFF : 0xCC15202B);
        GradientDrawable background = new GradientDrawable();
        background.setColor(darkBadge ? 0xB3000000 : 0x1F15202B);
        background.setCornerRadius(dp(view, 12));
        view.setBackground(background);
    }

    private String episodeTitle(Episode episode, int number, String tmdbTitle) {
        String label = number > 0 ? String.valueOf(number) : episode.getName();
        if (TextUtils.isEmpty(tmdbTitle)) return label;
        if (label.equals(tmdbTitle) || episode.getName().equals(tmdbTitle)) return label;
        return label + ". " + tmdbTitle;
    }

    private int episodeNumber(Episode episode, int position) {
        Integer number = episodeNumbers.get(episode);
        return number == null ? position + 1 : number;
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private String episodeBadge(TmdbEpisode episode) {
        if (episode == null) return "";
        List<String> parts = new ArrayList<>();
        if (episode.getVoteAverage() > 0) parts.add("★ " + String.format(Locale.US, "%.1f", episode.getVoteAverage()));
        if (episode.getRuntime() > 0) parts.add(episode.getRuntime() + "m");
        return TextUtils.join(" · ", parts);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbEpisodeBinding binding;

        ViewHolder(@NonNull AdapterTmdbEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
