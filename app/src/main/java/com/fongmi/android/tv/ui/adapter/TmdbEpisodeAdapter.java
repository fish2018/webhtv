package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Gravity;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterTmdbEpisodeBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class TmdbEpisodeAdapter extends RecyclerView.Adapter<TmdbEpisodeAdapter.ViewHolder> {

    private static final int FOCUS_STROKE = 0xFFFFD166;
    private static final int FOCUS_STROKE_DP = 3;
    private static final int ACTIVE_STROKE_DP = 2;
    private static final int FOCUS_ELEVATION_DP = 8;

    public enum Mode {
        LIST,
        GRID
    }

    public interface Listener {
        void onItemClick(Episode item);

        void onItemLongClick(View anchor, Episode item, int episodeNumber);
    }

    private final Listener listener;
    private final List<Episode> items = new ArrayList<>();
    private final Map<Integer, TmdbEpisode> tmdbItems = new HashMap<>();
    private final Map<Episode, Integer> episodeNumbers = new HashMap<>();
    private Episode selected;
    private Mode mode = Mode.LIST;
    private boolean light;
    private boolean compactPlain;
    private boolean nativeEnhanced;
    private boolean pageHasTmdbEpisodeData;
    private boolean showScrapedName = Setting.getTmdbEpisodeShowScrapedName();
    private boolean showFileSize = Setting.isTmdbEpisodeFileSize();
    private int activeStrokeColor = 0xFF2CC56F;
    private int gridSpanCount = 2;
    private String fallbackStillUrl = "";
    private View.OnKeyListener keyListener;
    private View.OnFocusChangeListener focusChangeListener;

    public TmdbEpisodeAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Episode selected) {
        setItems(episodes, tmdbEpisodes, Map.of(), selected);
    }

    public boolean setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers, Episode selected) {
        return setItems(episodes, tmdbEpisodes, numbers, selected, false);
    }

    public boolean setItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers, Episode selected, boolean forceRefresh) {
        boolean displaySettingsChanged = updateDisplaySettings();
        if (!forceRefresh && !displaySettingsChanged && sameItems(episodes, tmdbEpisodes, numbers)) {
            if (Objects.equals(this.selected, selected)) return false;
            setSelected(selected);
            return false;
        }
        items.clear();
        items.addAll(episodes);
        tmdbItems.clear();
        tmdbItems.putAll(tmdbEpisodes);
        episodeNumbers.clear();
        episodeNumbers.putAll(numbers);
        pageHasTmdbEpisodeData = hasPageTmdbEpisodeData();
        compactPlain = items.size() == 1 && tmdbItems.isEmpty() && TextUtils.isEmpty(items.get(0).getDesc());
        this.selected = selected;
        notifyDataSetChanged();
        return true;
    }

    public void setSelected(Episode selected) {
        int oldPosition = getPosition(this.selected);
        int newPosition = getPosition(selected);
        this.selected = selected;
        if (oldPosition == newPosition) {
            if (newPosition >= 0) notifyItemChanged(newPosition);
            return;
        }
        if (oldPosition >= 0) notifyItemChanged(oldPosition);
        if (newPosition >= 0) notifyItemChanged(newPosition);
    }

    public void setLight(boolean light) {
        if (this.light == light) return;
        this.light = light;
        notifyDataSetChanged();
    }

    public void setActiveStrokeColor(int activeStrokeColor) {
        if (this.activeStrokeColor == activeStrokeColor) return;
        this.activeStrokeColor = activeStrokeColor;
        notifyDataSetChanged();
    }

    public void setTheme(boolean light, int activeStrokeColor) {
        boolean changed = this.light != light || this.activeStrokeColor != activeStrokeColor;
        this.light = light;
        this.activeStrokeColor = activeStrokeColor;
        if (changed) notifyDataSetChanged();
    }

    public void setFallbackStillUrl(String fallbackStillUrl) {
        String value = TextUtils.isEmpty(fallbackStillUrl) ? "" : fallbackStillUrl;
        if (this.fallbackStillUrl.equals(value)) return;
        this.fallbackStillUrl = value;
        if (usesFallbackStill()) notifyDataSetChanged();
    }

    public void setMode(Mode mode) {
        Mode value = mode == null ? Mode.LIST : mode;
        if (this.mode == value) return;
        this.mode = value;
        if (!items.isEmpty()) notifyDataSetChanged();
    }

    public void setGridSpanCount(int gridSpanCount) {
        int value = Math.max(1, gridSpanCount);
        if (this.gridSpanCount == value) return;
        this.gridSpanCount = value;
        if (!items.isEmpty() && mode == Mode.GRID) notifyDataSetChanged();
    }

    public void setDisplayMode(Mode mode, int gridSpanCount) {
        Mode modeValue = mode == null ? Mode.LIST : mode;
        int spanValue = Math.max(1, gridSpanCount);
        boolean changed = this.mode != modeValue || this.gridSpanCount != spanValue;
        this.mode = modeValue;
        this.gridSpanCount = spanValue;
        if (changed && !items.isEmpty()) notifyDataSetChanged();
    }

    public void setOnKeyListener(View.OnKeyListener keyListener) {
        this.keyListener = keyListener;
    }

    public void setOnFocusChangeListener(View.OnFocusChangeListener focusChangeListener) {
        this.focusChangeListener = focusChangeListener;
    }

    public void setNativeEnhanced(boolean nativeEnhanced) {
        if (this.nativeEnhanced == nativeEnhanced) return;
        this.nativeEnhanced = nativeEnhanced;
        notifyDataSetChanged();
    }

    public void refreshDisplaySettings(RecyclerView recyclerView) {
        updateDisplaySettings();
        rebindAttached(recyclerView);
    }

    /**
     * 直接重新绑定当前已附着的可见 ViewHolder，不依赖 RecyclerView 的布局遍历。
     * 用于 RecyclerView 嵌套在 NestedScrollView(wrap_content) 中、requestLayout 被祖先
     * 的 stuck layout 标志吞掉、notifyDataSetChanged 无法触发重绑的场景。
     * 用 getLayoutPosition() 而非 getBindingAdapterPosition()：后者在有未派发的适配器
     * 更新时返回 NO_POSITION，而这里数据已同步，用上一次布局的位置即为可见项的正确位置。
     */
    public void rebindAttached(RecyclerView recyclerView) {
        for (int index = 0; index < recyclerView.getChildCount(); index++) {
            RecyclerView.ViewHolder holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index));
            int position = holder.getLayoutPosition();
            if (!(holder instanceof ViewHolder) || position == RecyclerView.NO_POSITION || position >= items.size()) continue;
            onBindViewHolder((ViewHolder) holder, position);
        }
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
        if (!TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, episodeNumber)) {
            tmdbEpisode = null;
        }
        // 匹配被拒时用文件自身集号（无有效集号则回退文件名），避免 position 回退值泄漏到标题
        int titleNumber = tmdbEpisode != null ? episodeNumber : episode.getNumber();
        String tmdbTitle = tmdbEpisode != null ? tmdbEpisode.getTitle() : "";
        String cleanTitle = getCleanTitle(episode, titleNumber, tmdbTitle);
        String title = titleWithFileSize(episode, cleanTitle);
        String fileSize = episodeFileSize(episode);
        String date = tmdbEpisode != null ? tmdbEpisode.getDate() : "";
        String overview = tmdbEpisode != null ? tmdbEpisode.getOverview() : episode.getDesc();
        boolean activated = episode.equals(selected);
        boolean compact = compactPlain && tmdbEpisode == null && TextUtils.isEmpty(overview);
        String stillUrl = tmdbEpisode != null ? tmdbEpisode.getStillUrl() : "";
        boolean suppressSharedFallback = shouldSuppressSharedFallbackVisuals();
        boolean allowFallback = mode == Mode.LIST || TmdbEpisodeGridPolicy.shouldUseFallbackImage(mode == Mode.GRID, items.size(), tmdbEpisode != null);
        boolean usingSharedFallback = TextUtils.isEmpty(stillUrl) && allowFallback && !TextUtils.isEmpty(fallbackStillUrl);
        String imageUrl = !TextUtils.isEmpty(stillUrl) ? stillUrl : (allowFallback && !suppressSharedFallback ? fallbackStillUrl : "");
        String errorImageUrl = !TextUtils.isEmpty(stillUrl) && allowFallback && !suppressSharedFallback ? fallbackStillUrl : "";
        boolean hasImage = !TextUtils.isEmpty(imageUrl);
        boolean showVisual = hasImage;
        boolean darkSurface = showVisual || !light || isNativeEnhanced();

        applyCardSize(holder, compact, pageHasTmdbEpisodeData);
        holder.binding.textPanel.setGravity(showVisual ? Gravity.NO_GRAVITY : Gravity.CENTER_VERTICAL);
        if (isNativeEnhanced()) {
            boolean phoneWidth = isPhoneWidth(holder.itemView);
            holder.binding.index.setText(nativeEnhancedIndexTitle(title, cleanTitle, fileSize, phoneWidth, mode));
            holder.binding.index.setTextSize(nativeEnhancedIndexTextSize(phoneWidth, mode));
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(nativeEnhancedMeta(tmdbEpisode));
            boolean showDate = !TextUtils.isEmpty(holder.binding.date.getText()) && mode == Mode.GRID;
            holder.binding.date.setVisibility(showDate ? View.VISIBLE : View.GONE);
            bindFileSize(holder, nativeEnhancedFileSizeBadge(fileSize, cleanTitle), showDate);
            holder.binding.badge.setVisibility(View.GONE);
            holder.binding.overview.setText(overview);
            holder.binding.overview.setVisibility(mode == Mode.GRID && !TextUtils.isEmpty(overview) ? View.VISIBLE : View.GONE);
        } else if (mode == Mode.GRID) {
            holder.binding.index.setText(cleanTitle);
            holder.binding.index.setTextSize(14f);
            bindFileSize(holder, fileSize, !TextUtils.isEmpty(date));
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(date);
            holder.binding.date.setVisibility(TextUtils.isEmpty(date) ? View.GONE : View.VISIBLE);
            if (suppressSharedFallback && usingSharedFallback && !TextUtils.isEmpty(overview)) {
                holder.binding.overview.setText(overview);
                holder.binding.overview.setVisibility(View.VISIBLE);
            } else {
                holder.binding.overview.setVisibility(View.GONE);
            }
        } else if (compact) {
            holder.binding.index.setText(title);
            holder.binding.index.setTextSize(14f);
            holder.binding.fileSize.setVisibility(View.GONE);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setVisibility(View.GONE);
            holder.binding.overview.setVisibility(View.GONE);
        } else {
            holder.binding.index.setText(isPhoneWidth(holder.itemView) ? cleanTitle : title);
            holder.binding.index.setTextSize(12f);
            if (isPhoneWidth(holder.itemView)) bindFileSize(holder, fileSize, !TextUtils.isEmpty(date));
            else holder.binding.fileSize.setVisibility(View.GONE);
            holder.binding.title.setVisibility(View.GONE);
            holder.binding.date.setText(date);
            holder.binding.date.setVisibility(TextUtils.isEmpty(date) ? View.GONE : View.VISIBLE);
            holder.binding.overview.setText(overview);
            holder.binding.overview.setVisibility(TextUtils.isEmpty(overview) ? View.GONE : View.VISIBLE);
        }
        holder.binding.index.setTextColor(darkSurface ? 0xFFFFFFFF : 0xFF15202B);
        holder.binding.title.setTextColor(darkSurface ? 0xE6FFFFFF : 0xCC15202B);
        holder.binding.date.setTextColor(darkSurface ? 0xCCFFFFFF : 0x9915202B);
        holder.binding.overview.setTextColor(darkSurface ? 0xE6FFFFFF : 0xB315202B);
        if (!isNativeEnhanced()) {
            holder.binding.badge.setText(episodeBadge(tmdbEpisode));
            holder.binding.badge.setVisibility(TextUtils.isEmpty(holder.binding.badge.getText()) ? View.GONE : View.VISIBLE);
        }
        applyBadgeStyle(holder.binding.date, darkSurface);
        applyBadgeStyle(holder.binding.badge, darkSurface);
        applyBadgeStyle(holder.binding.fileSize, darkSurface);
        applyCardFocus(holder, activated);
        if (showVisual) {
            holder.binding.stillFrame.setVisibility(View.VISIBLE);
            loadStillIfChanged(holder, title, imageUrl, errorImageUrl);
        } else {
            holder.binding.stillFrame.setVisibility(View.GONE);
            holder.binding.still.setTag(R.id.tmdb_episode_still_request_key, null);
            ImgUtil.clear(holder.binding.still);
        }
        holder.binding.scrim.setVisibility(showVisual ? View.VISIBLE : View.GONE);
        holder.binding.getRoot().setOnKeyListener(keyListener);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(episode));
        holder.binding.getRoot().setOnLongClickListener(view -> {
            listener.onItemLongClick(view, episode, episodeNumber);
            return true;
        });
    }

    private void applyCardSize(ViewHolder holder, boolean compact, boolean hasTmdbEpisodeData) {
        View root = holder.binding.getRoot();
        ViewGroup.LayoutParams params = root.getLayoutParams();
        int width;
        int height;
        boolean standardGridItem = mode == Mode.GRID && !hasTmdbEpisodeData;
        if (isNativeEnhanced()) {
            width = mode == Mode.GRID ? ViewGroup.LayoutParams.MATCH_PARENT : dp(holder.itemView, 280);
            height = dp(holder.itemView, mode == Mode.GRID
                    ? nativeEnhancedGridCardHeightDp(isPhoneWidth(holder.itemView), hasTmdbEpisodeData)
                    : 160);
        } else if (mode == Mode.GRID) {
            width = ViewGroup.LayoutParams.MATCH_PARENT;
            height = dp(holder.itemView, 118);
        } else {
            width = compact ? dp(holder.itemView, 220) : listCardWidth(holder.itemView);
            height = dp(holder.itemView, compact ? 78 : (isPhoneWidth(holder.itemView) ? 172 : 190));
        }
        boolean layoutChanged = params.width != width || params.height != height;
        params.width = width;
        params.height = height;
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            int gridSpacing = dp(holder.itemView, standardGridItem ? 8 : isNativeEnhanced() ? 12 : 8);
            int marginStart = mode == Mode.GRID ? gridSpacing / 2 : 0;
            int marginEnd = mode == Mode.GRID ? gridSpacing - marginStart : dp(holder.itemView, 12);
            int bottomMargin = dp(holder.itemView, isNativeEnhanced() && mode == Mode.GRID && hasTmdbEpisodeData ? 16 : mode == Mode.GRID ? 10 : 0);
            layoutChanged |= marginParams.getMarginStart() != marginStart
                    || marginParams.getMarginEnd() != marginEnd
                    || marginParams.bottomMargin != bottomMargin;
            marginParams.setMarginStart(marginStart);
            marginParams.setMarginEnd(marginEnd);
            marginParams.bottomMargin = bottomMargin;
        }
        if (layoutChanged) root.setLayoutParams(params);
        ViewGroup.LayoutParams scrimParams = holder.binding.scrim.getLayoutParams();
        int scrimHeight = dp(holder.itemView, isNativeEnhanced() && mode == Mode.GRID && !hasTmdbEpisodeData
                ? 96
                : isNativeEnhanced() ? mode == Mode.GRID ? nativeEnhancedGridScrimHeight(holder.itemView) : 104 : 96);
        if (scrimParams.height != scrimHeight) {
            scrimParams.height = scrimHeight;
            holder.binding.scrim.setLayoutParams(scrimParams);
        }
        int horizontal = standardGridItem || nativeEnhancedMobileGrid(holder.itemView) ? 10 : isNativeEnhanced() ? 12 : 10;
        int top = standardGridItem || nativeEnhancedMobileGrid(holder.itemView) ? 18 : isNativeEnhanced() ? 0 : 18;
        int bottom = standardGridItem || nativeEnhancedMobileGrid(holder.itemView) ? 10 : isNativeEnhanced() ? mode == Mode.GRID ? 14 : 12 : 10;
        int horizontalPx = dp(holder.itemView, horizontal);
        int topPx = dp(holder.itemView, top);
        int bottomPx = dp(holder.itemView, bottom);
        if (holder.binding.textPanel.getPaddingLeft() != horizontalPx
                || holder.binding.textPanel.getPaddingTop() != topPx
                || holder.binding.textPanel.getPaddingRight() != horizontalPx
                || holder.binding.textPanel.getPaddingBottom() != bottomPx) {
            holder.binding.textPanel.setPadding(horizontalPx, topPx, horizontalPx, bottomPx);
        }
    }

    private boolean nativeEnhancedMobileGrid(View view) {
        return isNativeEnhanced() && mode == Mode.GRID && isPhoneWidth(view);
    }

    private int nativeEnhancedGridCardHeight(View view) {
        return TmdbEpisodeGridPolicy.nativeGridCardHeightDp(isPhoneWidth(view));
    }

    static int nativeEnhancedGridCardHeightDp(boolean phoneWidth, boolean hasTmdbEpisodeData) {
        return hasTmdbEpisodeData
                ? phoneWidth ? TmdbEpisodeGridPolicy.NATIVE_MOBILE_GRID_CARD_HEIGHT_DP : TmdbEpisodeGridPolicy.NATIVE_GRID_CARD_HEIGHT_DP
                : TmdbEpisodeGridPolicy.GRID_CARD_HEIGHT_DP;
    }

    private int nativeEnhancedGridScrimHeight(View view) {
        return TmdbEpisodeGridPolicy.nativeGridScrimHeightDp(isPhoneWidth(view));
    }

    private int listCardWidth(View view) {
        if (!isPhoneWidth(view)) return dp(view, 230);
        int screen = view.getResources().getDisplayMetrics().widthPixels;
        return Math.max(dp(view, 168), Math.min(dp(view, 230), (screen - dp(view, 56)) / 2));
    }

    private int imageWidth(ViewHolder holder) {
        int width = holder.binding.still.getWidth();
        if (width > 0) return width;
        width = holder.binding.getRoot().getWidth();
        if (width > 0) return width;
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        if (params != null && params.width > 0) return params.width;
        if (mode == Mode.GRID) {
            int screen = holder.itemView.getResources().getDisplayMetrics().widthPixels;
            int sidePadding = dp(holder.itemView, Util.isMobile() ? 32 : 48);
            int spacing = dp(holder.itemView, Math.max(0, gridSpanCount - 1) * 8);
            return Math.max(dp(holder.itemView, 160), (screen - sidePadding - spacing) / Math.max(1, gridSpanCount));
        }
        return listCardWidth(holder.itemView);
    }

    private int imageHeight(ViewHolder holder) {
        int height = holder.binding.still.getHeight();
        if (height > 0) return height;
        height = holder.binding.getRoot().getHeight();
        if (height > 0) return height;
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        if (params != null && params.height > 0) return params.height;
        if (isNativeEnhanced()) {
            int gridHeight = pageHasTmdbEpisodeData
                    ? nativeEnhancedGridCardHeight(holder.itemView)
                    : TmdbEpisodeGridPolicy.GRID_CARD_HEIGHT_DP;
            return ResUtil.dp2px(mode == Mode.GRID ? gridHeight : 160);
        }
        return ResUtil.dp2px(mode == Mode.GRID ? TmdbEpisodeGridPolicy.GRID_CARD_HEIGHT_DP : (isPhoneWidth(holder.itemView) ? 172 : 190));
    }

    private boolean hasPageTmdbEpisodeData() {
        for (int position = 0; position < items.size(); position++) {
            Episode episode = items.get(position);
            if (episode == null) continue;
            int number = episodeNumber(episode, position);
            TmdbEpisode tmdbEpisode = tmdbItems.get(number);
            if (TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, number)) return true;
        }
        return false;
    }

    private boolean shouldSuppressSharedFallbackVisuals() {
        return false;
    }

    private void loadStillIfChanged(ViewHolder holder, String title, String imageUrl, String errorImageUrl) {
        int width = imageWidth(holder);
        int height = imageHeight(holder);
        String key = imageUrl + '\n' + errorImageUrl + '\n' + width + 'x' + height + '\n' + title;
        Object previous = holder.binding.still.getTag(R.id.tmdb_episode_still_request_key);
        if (TextUtils.equals(key, previous instanceof String ? (String) previous : null)) return;
        holder.binding.still.setTag(R.id.tmdb_episode_still_request_key, key);
        ImgUtil.load(title, imageUrl, errorImageUrl, holder.binding.still, true, width, height);
    }

    private boolean sameItems(List<Episode> episodes, Map<Integer, TmdbEpisode> tmdbEpisodes, Map<Episode, Integer> numbers) {
        return sameEpisodes(episodes) && sameTmdbEpisodes(tmdbEpisodes) && sameEpisodeNumbers(numbers);
    }

    private boolean updateDisplaySettings() {
        boolean currentShowScrapedName = Setting.getTmdbEpisodeShowScrapedName();
        boolean currentShowFileSize = Setting.isTmdbEpisodeFileSize();
        boolean changed = showScrapedName != currentShowScrapedName || showFileSize != currentShowFileSize;
        showScrapedName = currentShowScrapedName;
        showFileSize = currentShowFileSize;
        return changed;
    }

    private boolean sameEpisodes(List<Episode> episodes) {
        if (episodes == null || episodes.size() != items.size()) return false;
        for (int i = 0; i < episodes.size(); i++) {
            if (!Objects.equals(items.get(i), episodes.get(i))) return false;
        }
        return true;
    }

    private boolean sameTmdbEpisodes(Map<Integer, TmdbEpisode> episodes) {
        if (episodes == null || episodes.size() != tmdbItems.size()) return false;
        for (Map.Entry<Integer, TmdbEpisode> entry : episodes.entrySet()) {
            if (!sameTmdbEpisode(tmdbItems.get(entry.getKey()), entry.getValue())) return false;
        }
        return true;
    }

    private boolean sameTmdbEpisode(TmdbEpisode a, TmdbEpisode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getNumber() == b.getNumber()
                && a.getRuntime() == b.getRuntime()
                && a.getTmdbId() == b.getTmdbId()
                && a.getSeasonNumber() == b.getSeasonNumber()
                && Double.compare(a.getVoteAverage(), b.getVoteAverage()) == 0
                && Objects.equals(a.getTitle(), b.getTitle())
                && Objects.equals(a.getDate(), b.getDate())
                && Objects.equals(a.getOverview(), b.getOverview())
                && Objects.equals(a.getStillUrl(), b.getStillUrl());
    }

    private boolean sameEpisodeNumbers(Map<Episode, Integer> numbers) {
        if (numbers == null || numbers.size() != episodeNumbers.size()) return false;
        for (Map.Entry<Episode, Integer> entry : numbers.entrySet()) {
            if (!Objects.equals(episodeNumbers.get(entry.getKey()), entry.getValue())) return false;
        }
        return true;
    }

    private boolean isPhoneWidth(View view) {
        return view.getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private void applyBadgeStyle(TextView view, boolean darkSurface) {
        boolean darkBadge = darkSurface;
        view.setTextColor(darkBadge ? 0xE6FFFFFF : 0xCC15202B);
        GradientDrawable background = new GradientDrawable();
        background.setColor(darkBadge ? 0xB3000000 : 0x1F15202B);
        background.setCornerRadius(dp(view, 12));
        view.setBackground(background);
    }

    private void setMarquee(ViewHolder holder, boolean activated, boolean focused) {
        boolean active = !Util.isLeanback() || activated || focused;
        holder.binding.index.setSelected(active);
        holder.binding.date.setSelected(active);
        holder.binding.badge.setSelected(active);
        holder.binding.fileSize.setSelected(active);
    }

    private void applyCardFocus(ViewHolder holder, boolean activated) {
        setMarquee(holder, activated, holder.binding.getRoot().hasFocus());
        if (isNativeEnhanced()) {
            applyNativeEnhancedCardFocus(holder, activated, holder.binding.getRoot().hasFocus());
            holder.binding.getRoot().setOnFocusChangeListener((view, focused) -> {
                applyNativeEnhancedCardFocus(holder, activated, focused);
                setMarquee(holder, activated, focused);
                if (focusChangeListener != null) focusChangeListener.onFocusChange(view, focused);
            });
            return;
        }
        TmdbCardFocusHelper.bind(
                holder.binding.getRoot(),
                activated ? (light ? 0xFFE5F7EC : 0x6630A86B) : (light ? 0xEEFFFFFF : 0xCC16202A),
                activated ? activeStrokeColor : (light ? 0x33647480 : 0x33FFFFFF),
                activated ? 2 : 1,
                focused -> {
                    setMarquee(holder, activated, focused);
                    if (focusChangeListener != null) focusChangeListener.onFocusChange(holder.binding.getRoot(), focused);
                });
    }

    private void applyNativeEnhancedCardFocus(ViewHolder holder, boolean activated, boolean focused) {
        holder.binding.getRoot().setSelected(false);
        holder.binding.getRoot().setActivated(false);
        holder.binding.getRoot().setChecked(false);
        holder.binding.getRoot().setForeground(null);
        holder.binding.getRoot().setCardBackgroundColor(0xFF141A20);
        holder.binding.getRoot().setStrokeColor(focused ? FOCUS_STROKE : activated ? activeStrokeColor : 0x00000000);
        holder.binding.getRoot().setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : activated ? ACTIVE_STROKE_DP : 0));
        holder.binding.getRoot().setCardElevation(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
        holder.binding.getRoot().setTranslationZ(ResUtil.dp2px(focused ? FOCUS_ELEVATION_DP : 0));
        Drawable foreground = focused
                ? TmdbCardFocusHelper.foregroundBorder(holder.binding.getRoot(), FOCUS_STROKE, FOCUS_STROKE_DP)
                : activated ? TmdbCardFocusHelper.foregroundBorder(holder.binding.getRoot(), activeStrokeColor, ACTIVE_STROKE_DP) : null;
        holder.binding.getRoot().setForeground(foreground);
        holder.binding.getRoot().animate().cancel();
        holder.binding.getRoot().setScaleX(1f);
        holder.binding.getRoot().setScaleY(1f);
    }

    private boolean isNativeEnhanced() {
        return nativeEnhanced;
    }

    static String nativeEnhancedIndexTitle(String title, String cleanTitle, boolean phoneWidth, Mode mode) {
        return phoneWidth && mode == Mode.GRID ? cleanTitle : title;
    }

    static String nativeEnhancedIndexTitle(String title, String cleanTitle, String fileSize, boolean phoneWidth, Mode mode) {
        return TextUtils.isEmpty(nativeEnhancedFileSizeBadge(fileSize, cleanTitle)) ? nativeEnhancedIndexTitle(title, cleanTitle, phoneWidth, mode) : cleanTitle;
    }

    static String nativeEnhancedFileSizeBadge(String fileSize, String cleanTitle) {
        if (TextUtils.isEmpty(fileSize) || EpisodeTitleFormatter.containsFileSize(cleanTitle)) return "";
        return fileSize;
    }

    static float nativeEnhancedIndexTextSize(boolean phoneWidth, Mode mode) {
        return phoneWidth && mode == Mode.GRID ? 14f : phoneWidth ? 12f : 18f;
    }

    public static String getTitle(Episode episode, int number, String tmdbTitle) {
        return titleWithFileSize(episode, getCleanTitle(episode, number, tmdbTitle));
    }

    public static String getCleanTitle(Episode episode, int number, String tmdbTitle) {
        String label = number > 0 ? String.valueOf(number) : episode.getName();
        return formatCleanTitle(label, episode.getName(), tmdbTitle);
    }

    public static String formatTitle(String label, String sourceName, String tmdbTitle) {
        return EpisodeTitleFormatter.withSourceFileSize(sourceName, formatCleanTitle(label, sourceName, tmdbTitle), Setting.isTmdbEpisodeFileSize());
    }

    public static String formatCleanTitle(String label, String sourceName, String tmdbTitle) {
        return EpisodeTitleFormatter.formatTmdbTitle(label, sourceName, tmdbTitle, Setting.getTmdbEpisodeShowScrapedName());
    }

    private static String titleWithFileSize(Episode episode, String title) {
        return EpisodeTitleFormatter.withSourceFileSize(episode.getRawDisplayName(), title, Setting.isTmdbEpisodeFileSize());
    }

    private String episodeFileSize(Episode episode) {
        if (!Setting.isTmdbEpisodeFileSize()) return "";
        return EpisodeTitleFormatter.extractFileSize(episode.getRawDisplayName());
    }

    private String nativeEnhancedMeta(TmdbEpisode episode) {
        if (episode == null) return "";
        List<String> parts = new ArrayList<>();
        if (!TextUtils.isEmpty(episode.getDate())) parts.add(episode.getDate());
        if (episode.getRuntime() > 0) parts.add(episode.getRuntime() + "m");
        return TextUtils.join(" / ", parts);
    }

    private void bindFileSize(ViewHolder holder, String fileSize, boolean belowDate) {
        holder.binding.fileSize.setText(fileSize);
        holder.binding.fileSize.setVisibility(TextUtils.isEmpty(fileSize) ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams params = holder.binding.fileSize.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.topMargin = dp(holder.itemView, belowDate ? 36 : 8);
            holder.binding.fileSize.setLayoutParams(marginParams);
        }
    }

    private int episodeNumber(Episode episode, int position) {
        Integer number = episodeNumbers.get(episode);
        return number == null ? position + 1 : number;
    }

    private boolean usesFallbackStill() {
        return !items.isEmpty();
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    private String episodeBadge(TmdbEpisode episode) {
        if (episode == null) return "";
        if (Util.isMobile()) {
            if (episode.getVoteAverage() > 0) return "★" + mobileRating(episode.getVoteAverage());
            if (episode.getRuntime() > 0) return episode.getRuntime() + "m";
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (episode.getVoteAverage() > 0) parts.add("★ " + String.format(Locale.US, "%.1f", episode.getVoteAverage()));
        if (episode.getRuntime() > 0) parts.add(episode.getRuntime() + "m");
        return TextUtils.join(" · ", parts);
    }

    private String mobileRating(double rating) {
        double rounded = Math.round(rating * 10.0) / 10.0;
        if (Math.abs(rounded - Math.rint(rounded)) < 0.001) return String.format(Locale.US, "%.0f", rounded);
        return String.format(Locale.US, "%.1f", rounded);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public void onViewRecycled(@NonNull ViewHolder holder) {
        holder.binding.still.setTag(R.id.tmdb_episode_still_request_key, null);
        ImgUtil.clear(holder.binding.still);
        super.onViewRecycled(holder);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbEpisodeBinding binding;

        ViewHolder(@NonNull AdapterTmdbEpisodeBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
