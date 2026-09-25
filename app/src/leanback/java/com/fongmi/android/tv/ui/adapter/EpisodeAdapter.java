package com.fongmi.android.tv.ui.adapter;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeBinding;
import com.fongmi.android.tv.databinding.AdapterEpisodeCardBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EpisodeAdapter extends RecyclerView.Adapter<EpisodeAdapter.ViewHolder> {

    private static final int VIEW_TYPE_TEXT = 0;
    private static final int VIEW_TYPE_CARD = 1;
    private static final int CARD_WIDTH_DP = 280;
    private static final int CARD_HEIGHT_DP = 160;
    private static final int TEXT_BUTTON_MAX_WIDTH_DP = 120;
    public static final int GRID_CARD_HEIGHT_DP = 190;
    public static final int GRID_CARD_BOTTOM_MARGIN_DP = 16;
    private static final int CARD_MARGIN_END_DP = 12;
    private static final String TMDB_IMAGE_SIZE_PATTERN = "(/t/p/)([^/]+)(/)";
    private static final String PREFERRED_STILL_SIZE = "w780";
    private static final String FALLBACK_STILL_SIZE = "original";

    private final OnClickListener mListener;
    private final OnLongClickListener mLongClickListener;
    private final List<Episode> mItems;
    private final int maxWidth;
    private final int spacing;
    private View.OnKeyListener keyListener;
    private int nextFocusDown;
    private int nextFocusUp;
    private int column;
    private boolean useTmdbCard = false;
    private boolean gridMode = false;
    private boolean verticalGridMode = false;
    private String fallbackStillUrl = "";

    public EpisodeAdapter(OnClickListener listener) {
        this(listener, null, ResUtil.getScreenWidth() - ResUtil.dp2px(48));
    }

    public EpisodeAdapter(OnClickListener listener, OnLongClickListener longClickListener) {
        this(listener, longClickListener, ResUtil.getScreenWidth() - ResUtil.dp2px(48));
    }

    public EpisodeAdapter(OnClickListener listener, int maxWidth) {
        this(listener, null, maxWidth);
    }

    public EpisodeAdapter(OnClickListener listener, OnLongClickListener longClickListener, int maxWidth) {
        mListener = listener;
        mLongClickListener = longClickListener;
        mItems = new ArrayList<>();
        this.maxWidth = Math.max(ResUtil.dp2px(240), maxWidth);
        spacing = ResUtil.dp2px(8);
        column = 1;
    }

    public void addAll(List<Episode> items) {
        EpisodeTitleCompact.apply(items);
        mItems.clear();
        mItems.addAll(items);
        notifyDataSetChanged();
    }

    public void setUseTmdbCard(boolean useTmdbCard) {
        if (this.useTmdbCard == useTmdbCard) return;
        this.useTmdbCard = useTmdbCard;
        notifyDataSetChanged();
    }

    public boolean isUsingTmdbCard() {
        return useTmdbCard;
    }

    public void setFallbackStillUrl(String fallbackStillUrl) {
        String value = TextUtils.isEmpty(fallbackStillUrl) ? "" : fallbackStillUrl;
        if (this.fallbackStillUrl.equals(value)) return;
        this.fallbackStillUrl = value;
        if (useTmdbCard) notifyDataSetChanged();
    }

    public void setGridMode(boolean gridMode) {
        if (this.gridMode == gridMode) return;
        this.gridMode = gridMode;
        notifyDataSetChanged();
    }

    public void setVerticalGridMode(boolean verticalGridMode) {
        if (this.verticalGridMode == verticalGridMode) return;
        this.verticalGridMode = verticalGridMode;
        notifyDataSetChanged();
    }

    public void clear() {
        mItems.clear();
        notifyDataSetChanged();
    }

    public int getPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return 0;
    }

    public List<Episode> getItems() {
        return mItems;
    }

    public int getSelectedPosition() {
        for (int i = 0; i < mItems.size(); i++) if (mItems.get(i).isSelected()) return i;
        return RecyclerView.NO_POSITION;
    }

    public int indexOf(Episode item) {
        return mItems.indexOf(item);
    }

    public void notifySelectionChanged(int oldPosition, int newPosition) {
        if (oldPosition != RecyclerView.NO_POSITION) notifyItemChanged(oldPosition);
        if (newPosition != RecyclerView.NO_POSITION && newPosition != oldPosition) notifyItemChanged(newPosition);
    }

    public Episode getActivated() {
        return mItems.isEmpty() ? new Episode() : mItems.get(getPosition());
    }

    public Episode getNext() {
        int current = getPosition();
        int max = getItemCount() - 1;
        current = ++current > max ? max : current;
        return mItems.get(current);
    }

    public Episode getPrev() {
        int current = getPosition();
        current = --current < 0 ? 0 : current;
        return mItems.get(current);
    }

    public void setNextFocusDown(int nextFocusDown) {
        if (this.nextFocusDown == nextFocusDown) return;
        this.nextFocusDown = nextFocusDown;
        notifyDataSetChanged();
    }

    public void setNextFocusUp(int nextFocusUp) {
        if (this.nextFocusUp == nextFocusUp) return;
        this.nextFocusUp = nextFocusUp;
        notifyDataSetChanged();
    }

    public void setOnKeyListener(View.OnKeyListener keyListener) {
        this.keyListener = keyListener;
        notifyDataSetChanged();
    }

    public void setColumn(int column) {
        column = Math.max(1, column);
        if (this.column == column) return;
        this.column = column;
        notifyDataSetChanged();
    }

    public int getColumn() {
        return column;
    }

    public static int getColumn(List<Episode> items) {
        return getColumn(items, ResUtil.getScreenWidth() - ResUtil.dp2px(48));
    }

    public static int getColumn(List<Episode> items, int maxWidth) {
        int maxTextWidth = 0;
        maxWidth = Math.max(ResUtil.dp2px(240), maxWidth);
        int spacing = ResUtil.dp2px(8);
        int padding = ResUtil.dp2px(40);
        EpisodeTitleCompact.apply(items);
        for (Episode item : items) maxTextWidth = Math.max(maxTextWidth, ResUtil.getTextWidth(getTitle(item), 16) + padding);
        for (int candidate : new int[]{8, 6, 5, 4, 3, 2}) {
            int width = (maxWidth - spacing * (candidate - 1)) / candidate;
            if (maxTextWidth <= width) return candidate;
        }
        return 2;
    }

    public static String getTitle(Episode item) {
        if (item.getTmdbEpisode() != null && Setting.getTmdbEpisodeShowScrapedName()) {
            String title = EpisodeTitleFormatter.formatTmdbTitle(item.getTmdbEpisode().getNumber(), item.getTmdbEpisode().getTitle());
            if (!title.isEmpty()) return EpisodeTitleFormatter.withSourceFileSize(item.getRawDisplayName(), title, Setting.isTmdbEpisodeFileSize());
        }
        return getNativeTitle(item);
    }

    public static String getNativeTitle(Episode item) {
        if (item == null) return "";
        String title = item.getDisplayName();
        if (TextUtils.isEmpty(item.getDesc()) || title.startsWith(item.getDesc())) return title;
        return item.getDesc().concat(title);
    }

    public static String getNativeDisplayTitle(Episode item) {
        return getNativeDisplayTitle(item, Setting.isTmdbEpisodeFileSize());
    }

    static String getNativeDisplayTitle(Episode item, boolean separateFileSize) {
        String title = getNativeTitle(item);
        if (!separateFileSize) return title;
        return EpisodeTitleFormatter.removeFileSizes(title);
    }

    public static String getNativeFileSize(Episode item) {
        return getNativeFileSize(item, Setting.isTmdbEpisodeFileSize());
    }

    static String getNativeFileSize(Episode item, boolean includeFileSize) {
        if (item == null || !includeFileSize) return "";
        return EpisodeTitleFormatter.extractFileSize(item.getRawDisplayName());
    }

    private int getWidth() {
        int width = (maxWidth - spacing * (column - 1)) / column;
        return verticalGridMode ? width : Math.min(width, ResUtil.dp2px(TEXT_BUTTON_MAX_WIDTH_DP));
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        // 卡片模式下整列统一用卡片布局：容器（EpisodeListDialog 的固定行高、episodeGrid 的 gridMode）
        // 按整体模式计算尺寸，逐集切换布局会让未刮削到的集退化成矮文本按钮，与卡片行混排。
        // 缺 TMDB 数据的集由 bindCardView 降级绑定（剧集主图兜底 + 原生标题 + 隐藏徽标）。
        return useTmdbCard ? VIEW_TYPE_CARD : VIEW_TYPE_TEXT;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_CARD) {
            // TMDB 卡片模式
            AdapterEpisodeCardBinding binding = AdapterEpisodeCardBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                binding.cardContainer.setDefaultFocusHighlightEnabled(false);
            }
            return new ViewHolder(binding);
        } else {
            // 简单文本模式
            AdapterEpisodeBinding binding = AdapterEpisodeBinding.inflate(
                    LayoutInflater.from(parent.getContext()), parent, false);
            return new ViewHolder(binding);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Episode item = mItems.get(position);

        if (getItemViewType(position) == VIEW_TYPE_CARD) {
            // TMDB 卡片模式
            bindCardView(holder, item, position);
        } else {
            // 简单文本模式
            bindTextView(holder, item, position);
        }
    }

    private void bindTextView(@NonNull ViewHolder holder, Episode item, int position) {
        TextView textView = holder.textView;
        if (textView == null) return;

        String fileSize = getNativeFileSize(item);
        boolean showFileSize = !TextUtils.isEmpty(fileSize) && holder.nativeFileSizeView != null;
        if (holder.nativeFileSizeView != null) {
            holder.nativeFileSizeView.setText(fileSize);
            holder.nativeFileSizeView.setVisibility(showFileSize ? View.VISIBLE : View.GONE);
            holder.nativeFileSizeView.setSelected(item.isSelected() || textView.hasFocus());
        }
        ViewGroup.LayoutParams params = textView.getLayoutParams();
        int width = getWidth();
        if (showFileSize && !verticalGridMode) width += ResUtil.dp2px(104);
        int horizontalPadding = ResUtil.dp2px(12);
        int titleStartPadding = showFileSize ? ResUtil.dp2px(104) : horizontalPadding;
        textView.setPaddingRelative(titleStartPadding, textView.getPaddingTop(), horizontalPadding, textView.getPaddingBottom());
        if (params.width != width) {
            params.width = width;
            textView.setLayoutParams(params);
        }
        textView.setSingleLine(true);
        textView.setMaxLines(1);
        textView.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        textView.setNextFocusUpId(isTopEdge(position) && nextFocusUp != 0 ? nextFocusUp : View.NO_ID);
        textView.setNextFocusDownId(isBottomEdge(position) && nextFocusDown != 0 ? nextFocusDown : View.NO_ID);
        textView.setSelected(item.isSelected() || textView.hasFocus());
        textView.setText(getNativeDisplayTitle(item));
        textView.setOnFocusChangeListener((view, hasFocus) -> {
            boolean active = item.isSelected() || hasFocus;
            textView.setSelected(active);
            if (holder.nativeFileSizeView != null) holder.nativeFileSizeView.setSelected(active);
        });
        textView.setOnKeyListener(keyListener);
        textView.setOnClickListener(v -> mListener.onItemClick(item));
        if (mLongClickListener != null) {
            textView.setOnLongClickListener(v -> {
                mLongClickListener.onItemLongClick(item);
                return true;
            });
        }
    }

    private void bindCardView(@NonNull ViewHolder holder, Episode item, int position) {
        AdapterEpisodeCardBinding binding = holder.cardBinding;
        if (binding == null) return;

        // 使用集号匹配 TMDB 数据，而不是直接使用 item.getTmdbEpisode()
        int episodeNumber = item.getNumber() > 0 ? item.getNumber() : position + 1;
        TmdbEpisode tmdbEpisode = item.getTmdbEpisode();
        // 跨季映射的集走两参版：源集号是扁平号（如 62），TMDB 是本季集号（如 S2E1），
        // 三参版会因 tmdbEpisode.getNumber() != episodeNumber 而否决。两参版对已带
        // mapped 标记的集只校验身份，不要求两个集号相等。
        boolean valid = item.isTmdbEpisodeMapped()
                ? TmdbEpisodeMatcher.shouldApply(item, tmdbEpisode)
                : TmdbEpisodeMatcher.shouldApply(item, tmdbEpisode, episodeNumber);
        if (!valid) {
            tmdbEpisode = null;
        }

        applyCardSize(binding);

        // 设置选中状态（用于边框颜色）
        binding.cardContainer.setSelected(item.isSelected());
        binding.cardContainer.setNextFocusUpId(isTopEdge(position) && nextFocusUp != 0 ? nextFocusUp : View.NO_ID);
        binding.cardContainer.setNextFocusDownId(isBottomEdge(position) && nextFocusDown != 0 ? nextFocusDown : View.NO_ID);
        binding.cardContainer.setOnKeyListener(keyListener);
        setCardMarquee(binding, item.isSelected() || binding.cardContainer.hasFocus());
        binding.cardContainer.setOnFocusChangeListener((view, hasFocus) -> setCardMarquee(binding, item.isSelected() || hasFocus));

        // 加载剧照
        // 匹配器否决 TMDB 数据时必须走原生标题：getCardTitle 的 null 分支会回落到 getTitle，
        // 而 getTitle 仍读 item.getTmdbEpisode()，会把刚否决的错位剧集名重新显示出来。
        String cardTitle = tmdbEpisode == null ? getNativeDisplayTitle(item) : getCardTitle(item, tmdbEpisode);
        String stillUrl = tmdbEpisode == null ? "" : tmdbEpisode.getStillUrl();
        String imageUrl = TextUtils.isEmpty(stillUrl) ? fallbackStillUrl : stillUrl;
        String errorImageUrl = TextUtils.isEmpty(stillUrl) ? "" : fallbackStillUrl;
        // 对齐详情页 TmdbEpisodeAdapter 的 showVisual：无图时隐藏图层并清掉复用的剧照，
        // 否则 still 铺满整卡会留下上一条的图，scrim 渐变也会压在纯色底上形成暗角。
        boolean showVisual = !TextUtils.isEmpty(imageUrl);
        binding.still.setVisibility(showVisual ? View.VISIBLE : View.GONE);
        binding.scrim.setVisibility(showVisual ? View.VISIBLE : View.GONE);
        if (showVisual) {
            loadStill(binding, imageUrl, errorImageUrl);
        } else {
            Glide.with(binding.still.getContext()).clear(binding.still);
            binding.still.setImageDrawable(null);
        }
        binding.textPanel.setGravity(showVisual ? Gravity.NO_GRAVITY : Gravity.CENTER_VERTICAL);

        // 设置标题
        binding.cardTitle.setText(cardTitle);

        // 网格模式展示手机版原生增强同款的日期 / 时长徽标，列表模式保持干净的横向剧照条
        String meta = tmdbEpisode == null ? "" : getMeta(tmdbEpisode);
        boolean showMeta = gridMode && !TextUtils.isEmpty(meta);
        if (showMeta) {
            binding.dateBadge.setText(meta);
            binding.dateBadge.setVisibility(View.VISIBLE);
        } else {
            binding.dateBadge.setVisibility(View.GONE);
        }
        binding.runtimeBadge.setVisibility(View.GONE);
        bindFileSize(binding, getCardFileSize(item, cardTitle), showMeta);

        // 播放器/弹层网格上下移动焦点时，长简介会放大文本重绘和视觉闪烁；完整简介保留在长按详情里。
        binding.overview.setText("");
        binding.overview.setVisibility(View.GONE);

        // 点击和长按事件
        binding.cardContainer.setOnClickListener(v -> mListener.onItemClick(item));
        if (mLongClickListener != null) {
            binding.cardContainer.setOnLongClickListener(v -> {
                mLongClickListener.onItemLongClick(item);
                return true;
            });
        }
    }

    private void setCardMarquee(AdapterEpisodeCardBinding binding, boolean active) {
        binding.cardTitle.setSelected(active);
        binding.dateBadge.setSelected(active);
        binding.fileSize.setSelected(active);
    }

    private void applyCardSize(AdapterEpisodeCardBinding binding) {
        ViewGroup.LayoutParams cardParams = binding.cardContainer.getLayoutParams();
        cardParams.width = gridMode ? ViewGroup.LayoutParams.MATCH_PARENT : ResUtil.dp2px(CARD_WIDTH_DP);
        cardParams.height = ResUtil.dp2px(gridMode ? GRID_CARD_HEIGHT_DP : CARD_HEIGHT_DP);
        binding.cardContainer.setLayoutParams(cardParams);
        if (cardParams instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMarginEnd(ResUtil.dp2px(CARD_MARGIN_END_DP));
            marginParams.bottomMargin = gridMode ? ResUtil.dp2px(GRID_CARD_BOTTOM_MARGIN_DP) : 0;
            binding.cardContainer.setLayoutParams(marginParams);
        }

        ViewGroup.LayoutParams scrimParams = binding.scrim.getLayoutParams();
        scrimParams.height = ResUtil.dp2px(104);
        binding.scrim.setLayoutParams(scrimParams);
        binding.textPanel.setPadding(
                ResUtil.dp2px(12),
                0,
                ResUtil.dp2px(12),
                ResUtil.dp2px(gridMode ? 14 : 12));
        binding.cardTitle.setTextSize(gridMode ? 18 : 18);
    }

    private void bindFileSize(AdapterEpisodeCardBinding binding, String fileSize, boolean belowMeta) {
        binding.fileSize.setText(fileSize);
        binding.fileSize.setVisibility(TextUtils.isEmpty(fileSize) ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams params = binding.fileSize.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.topMargin = ResUtil.dp2px(belowMeta ? 46 : 10);
            binding.fileSize.setLayoutParams(marginParams);
        }
    }

    private void loadStill(AdapterEpisodeCardBinding binding, String url, String errorUrl) {
        Context context = binding.still.getContext();
        int width = getCardWidth(binding);
        int height = getCardHeight(binding);
        String preferredUrl = tmdbImageUrl(url, PREFERRED_STILL_SIZE);
        String fallbackUrl = tmdbImageUrl(url, FALLBACK_STILL_SIZE);
        RequestBuilder<Drawable> errorRequest = loadStillRequest(context, TextUtils.isEmpty(errorUrl) || TextUtils.equals(url, errorUrl) ? "" : tmdbImageUrl(errorUrl, FALLBACK_STILL_SIZE), width, height);

        RequestBuilder<Drawable> request = Glide.with(context)
                .load(preferredUrl)
                .placeholder(R.color.black)
                .centerCrop()
                .override(width, height);
        if (!preferredUrl.equals(fallbackUrl)) {
            request.error(Glide.with(context)
                    .load(fallbackUrl)
                    .placeholder(R.color.black)
                    .error(errorRequest == null ? Glide.with(context).load(R.color.black) : errorRequest)
                    .centerCrop()
                    .override(width, height));
        } else {
            request.error(errorRequest == null ? Glide.with(context).load(R.color.black) : errorRequest);
        }
        request.into(binding.still);
    }

    private RequestBuilder<Drawable> loadStillRequest(Context context, String url, int width, int height) {
        if (TextUtils.isEmpty(url)) return null;
        return Glide.with(context)
                .load(url)
                .placeholder(R.color.black)
                .error(R.color.black)
                .centerCrop()
                .override(width, height);
    }

    private int getCardWidth(AdapterEpisodeCardBinding binding) {
        int width = binding.cardContainer.getWidth();
        if (width > 0) return width;
        ViewGroup.LayoutParams params = binding.cardContainer.getLayoutParams();
        if (params != null && params.width > 0) return params.width;
        return gridMode ? getGridCardWidth() : ResUtil.dp2px(CARD_WIDTH_DP);
    }

    private int getCardHeight(AdapterEpisodeCardBinding binding) {
        int height = binding.cardContainer.getHeight();
        if (height > 0) return height;
        ViewGroup.LayoutParams params = binding.cardContainer.getLayoutParams();
        if (params != null && params.height > 0) return params.height;
        return ResUtil.dp2px(gridMode ? GRID_CARD_HEIGHT_DP : CARD_HEIGHT_DP);
    }

    private int getGridCardWidth() {
        int minCardWidth = ResUtil.dp2px(CARD_WIDTH_DP);
        int available = Math.max(ResUtil.dp2px(320), ResUtil.getScreenWidth() - ResUtil.dp2px(48));
        int span = column > 1 ? column : Math.max(2, Math.min(6, available / minCardWidth));
        return Math.max(minCardWidth, available / span - ResUtil.dp2px(CARD_MARGIN_END_DP));
    }

    private static String tmdbImageUrl(String url, String size) {
        if (url == null || url.isEmpty()) return "";
        String result = url.replaceFirst(TMDB_IMAGE_SIZE_PATTERN, "$1" + size + "$3");
        return result.equals(url) ? url.replaceFirst("/(w\\d+|h\\d+|original)/", "/" + size + "/") : result;
    }

    public static String getCardTitle(Episode item) {
        return getCardTitle(item, item == null ? null : item.getTmdbEpisode());
    }

    public static String getCardTitle(Episode item, TmdbEpisode tmdbEpisode) {
        if (item == null) return "";
        if (tmdbEpisode == null) return getTitle(item);
        if (!Setting.getTmdbEpisodeShowScrapedName()) return item.getDisplayName();
        String title = EpisodeTitleFormatter.formatTmdbTitle(tmdbEpisode.getNumber(), tmdbEpisode.getTitle());
        if (title.isEmpty()) title = tmdbEpisode.getDisplayTitle();
        return title;
    }

    public static String getCardFileSize(Episode item, String title) {
        return getCardFileSize(item, title, Setting.isTmdbEpisodeFileSize());
    }

    static String getCardFileSize(Episode item, String title, boolean includeFileSize) {
        if (item == null || !includeFileSize) return "";
        String fileSize = EpisodeTitleFormatter.extractFileSize(item.getRawDisplayName());
        if (TextUtils.isEmpty(fileSize) || EpisodeTitleFormatter.containsFileSize(title)) return "";
        return fileSize;
    }

    private String getMeta(TmdbEpisode tmdbEpisode) {
        List<String> values = new ArrayList<>();
        if (!TextUtils.isEmpty(tmdbEpisode.getDate())) values.add(tmdbEpisode.getDate());
        if (tmdbEpisode.getRuntime() > 0) values.add(String.format(Locale.US, "%dm", tmdbEpisode.getRuntime()));
        return TextUtils.join(" / ", values);
    }

    private boolean isTopEdge(int position) {
        if (column <= 1) return !verticalGridMode || position == 0;
        if (verticalGridMode) return position < column;
        return position % column == 0;
    }

    private boolean isBottomEdge(int position) {
        if (column <= 1) return !verticalGridMode || position == getItemCount() - 1;
        if (verticalGridMode) {
            int lastRowCount = (getItemCount() - 1) % column + 1;
            return position >= getItemCount() - lastRowCount;
        }
        return position % column == column - 1 || position == getItemCount() - 1;
    }

    public interface OnClickListener {
        void onItemClick(Episode item);
    }

    public interface OnLongClickListener {
        void onItemLongClick(Episode item);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {

        private TextView textView;
        private TextView nativeFileSizeView;
        private AdapterEpisodeCardBinding cardBinding;

        // 简单文本模式的 ViewHolder
        ViewHolder(@NonNull AdapterEpisodeBinding binding) {
            super(binding.getRoot());
            this.textView = binding.text;
            this.nativeFileSizeView = binding.nativeFileSize;
        }

        // TMDB 卡片模式的 ViewHolder
        ViewHolder(@NonNull AdapterEpisodeCardBinding binding) {
            super(binding.getRoot());
            this.cardBinding = binding;
        }
    }
}
