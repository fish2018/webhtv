package com.fongmi.android.tv.ui.holder;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeGridBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog;
import com.fongmi.android.tv.ui.helper.EpisodeCardPolicy;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EpisodeGridHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeGridBinding binding;
    private boolean useTmdbCard;
    private boolean nativeGridExpanded;
    private boolean nativeMultiLine;
    private Episode boundItem;
    private String fallbackStillUrl = "";
    private final int maxSingleWidth;
    private final int horizontalPadding;
    private final int cardMargin;
    private final int sideFileSizeMinWidth;
    private final int itemSpacing;
    private final ViewGroup recycler;

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener) {
        this(binding, listener, null);
    }

    public EpisodeGridHolder(@NonNull AdapterEpisodeGridBinding binding, EpisodeAdapter.OnClickListener listener, ViewGroup recycler) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.recycler = recycler;
        this.maxSingleWidth = ResUtil.getScreenWidth();
        this.horizontalPadding = ResUtil.dp2px(12);
        this.cardMargin = ResUtil.dp2px(6);
        // 徽标 96dp + 右内边距 + 至少 4 个字的标题空间，低于此宽度就改用纵向堆叠
        this.sideFileSizeMinWidth = ResUtil.dp2px(96 + 12 + 64);
        this.itemSpacing = ResUtil.dp2px(8);
    }

    @Override
    public void setUseTmdbCard(boolean useTmdbCard) {
        this.useTmdbCard = useTmdbCard;
    }

    @Override
    public void setFallbackStillUrl(String fallbackStillUrl) {
        this.fallbackStillUrl = TextUtils.isEmpty(fallbackStillUrl) ? "" : fallbackStillUrl;
    }

    @Override
    public void setNativeGridExpanded(boolean nativeGridExpanded) {
        this.nativeGridExpanded = nativeGridExpanded;
    }

    @Override
    public void initView(Episode item) {
        updateLayout();
        // 验证 TMDB 匹配：只有文件名有有效集号且与 TMDB 集号一致时才匹配
        TmdbEpisode episode = item.getTmdbEpisode();
        if (!TmdbEpisodeMatcher.shouldApply(item, episode)) {
            episode = null;
        }
        if (EpisodeCardPolicy.shouldShowCard(useTmdbCard, episode != null, !TextUtils.isEmpty(fallbackStillUrl))) bindCard(item, episode);
        else bindText(item);
    }

    private void bindText(Episode item) {
        binding.card.setVisibility(View.GONE);
        binding.text.setVisibility(View.VISIBLE);
        setCardMarquee(false);
        binding.text.setActivated(item.isSelected());
        boundItem = item;
        applyNativeLayout(EpisodeAdapter.getNativeFileSize(item));
        binding.text.setText(EpisodeAdapter.getNativeDisplayTitle(item));
        // 首帧宽度为 0、或列数刚变过时估算可能失准，等布局落定后按真实宽度再纠正一次。
        // 复用后 boundItem 已经指向新 item，这里必须重新取值，不能捕获旧的 fileSize。
        binding.text.post(() -> {
            if (boundItem == null) return;
            applyNativeLayout(EpisodeAdapter.getNativeFileSize(boundItem));
            // applyNativeLayout 会重置 ellipsize，之后必须补回聚焦/选中态的跑马灯
            setNativeActive(binding.text.hasFocus() || binding.text.isActivated());
        });
        setNativeActive(binding.text.hasFocus() || item.isSelected());
        binding.text.setOnFocusChangeListener((view, hasFocus) -> setNativeActive(hasFocus || binding.text.isActivated()));
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
        EpisodeAdapter.bindNativeTitlePopup(binding.getRoot(), item);
        EpisodeAdapter.bindNativeTitlePopup(binding.text, item);
    }

    private void bindCard(Episode item, TmdbEpisode episode) {
        binding.text.setVisibility(View.GONE);
        binding.nativeFileSize.setVisibility(View.GONE);
        binding.card.setVisibility(View.VISIBLE);
        binding.text.setActivated(false);
        // 置空，避免 bindText 挂起的 post 在卡片上重新显示原生徽标
        boundItem = null;
        // 卡片分支不走 configureNativeText，这里把原生按钮的多行/堆叠状态收干净，
        // 否则 holder 复用回原生按钮时会读到上一次的脏状态。
        nativeMultiLine = false;
        binding.text.setMinHeight(0);
        binding.text.setPadding(horizontalPadding, 0, horizontalPadding, 0);
        clearNativeActive();
        binding.card.setSelected(item.isSelected());
        bindCardActions(item, binding.getRoot(), binding.card, binding.imageFrame, binding.still, binding.textPanel, binding.cardTitle, binding.overview);

        String cardTitle = EpisodeAdapter.getCardTitle(item, episode);
        binding.cardTitle.setText(cardTitle);
        binding.cardTitle.setSelected(item.isSelected());

        String stillUrl = episode == null ? "" : episode.getStillUrl();
        String imageUrl = TextUtils.isEmpty(stillUrl) ? fallbackStillUrl : stillUrl;
        String errorImageUrl = TextUtils.isEmpty(stillUrl) ? "" : fallbackStillUrl;
        boolean hasStill = !TextUtils.isEmpty(imageUrl);
        binding.imageFrame.setVisibility(hasStill ? View.VISIBLE : View.GONE);
        binding.textPanel.setGravity(hasStill ? Gravity.NO_GRAVITY : Gravity.CENTER_VERTICAL);
        if (!hasStill) {
            Glide.with(binding.still.getContext()).clear(binding.still);
            binding.still.setImageDrawable(null);
        } else {
            ImgUtil.load(cardTitle, imageUrl, errorImageUrl, binding.still, true, 0, 0);
        }

        if (episode == null || TextUtils.isEmpty(episode.getOverview())) {
            binding.overview.setVisibility(View.GONE);
        } else {
            binding.overview.setVisibility(View.VISIBLE);
            binding.overview.setText(episode.getOverview());
        }

        if (episode != null && episode.getVoteAverage() > 0) {
            binding.rating.setVisibility(View.VISIBLE);
            binding.rating.setText(String.format(Locale.US, "%.1f", episode.getVoteAverage()));
        } else {
            binding.rating.setVisibility(View.GONE);
        }

        String meta = episode == null || !hasStill ? "" : getMeta(episode);
        boolean showMeta = !TextUtils.isEmpty(meta);
        binding.meta.setVisibility(showMeta ? View.VISIBLE : View.GONE);
        binding.meta.setText(meta);
        bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle), showMeta);
        setCardMarquee(true);
    }

    private String getMeta(TmdbEpisode episode) {
        List<String> values = new ArrayList<>();
        if (!TextUtils.isEmpty(episode.getDate())) values.add(episode.getDate());
        if (episode.getRuntime() > 0) values.add(episode.getRuntime() + "m");
        return TextUtils.join(" / ", values);
    }

    private void applyNativeLayout(String fileSize) {
        boolean stacked = !TextUtils.isEmpty(fileSize) && !hasRoomForSideFileSize();
        configureNativeText(stacked);
        bindNativeFileSize(fileSize, stacked);
    }

    private void bindNativeFileSize(String fileSize, boolean stacked) {
        boolean visible = !TextUtils.isEmpty(fileSize);
        binding.nativeFileSize.setText(fileSize);
        binding.nativeFileSize.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.nativeFileSize.setSelected(binding.text.isActivated() || binding.text.hasFocus());
        applyNativeFileSize(visible, stacked);
    }

    /**
     * 徽标横向占位需要 96dp，窄按钮（多列网格）放不下会把标题挤成 0 宽，
     * 这时改为徽标置顶、标题上方避让，保证集数和文件大小都能看到。
     */
    private void applyNativeFileSize(boolean visible, boolean stacked) {
        ViewGroup.LayoutParams params = binding.nativeFileSize.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams frameParams) {
            int gravity = Gravity.START | (stacked ? Gravity.TOP : Gravity.CENTER_VERTICAL);
            int topMargin = stacked ? ResUtil.dp2px(6) : 0;
            if (frameParams.gravity != gravity || frameParams.topMargin != topMargin) {
                frameParams.gravity = gravity;
                frameParams.topMargin = topMargin;
                binding.nativeFileSize.setLayoutParams(frameParams);
            }
        }
        if (stacked) binding.text.setPadding(horizontalPadding, ResUtil.dp2px(28), horizontalPadding, 0);
        else binding.text.setPadding(visible ? ResUtil.dp2px(96) : horizontalPadding, 0, horizontalPadding, 0);
        binding.text.setMinHeight(stacked ? ResUtil.dp2px(76) : nativeGridExpanded ? ResUtil.dp2px(64) : 0);
    }

    /**
     * 首帧 bind 时 item 还没被 addView，getWidth() 是 0（复用时还可能是上一种列数的旧宽度），
     * 所以以 onCreateViewHolder 传进来的 RecyclerView + 当前列数估算为准，
     * 只有估不出来时才退回自身实测宽度。否则第一屏标题会被徽标挤没。
     */
    private boolean hasRoomForSideFileSize() {
        int width = estimateItemWidth();
        if (width <= 0) width = binding.getRoot().getWidth();
        // 宽度未知时按“放不下”处理：多一帧的按钮高度可以接受，标题空白不行。
        // 真实宽度出来后 binding.text.post 的复检会把宽按钮恢复成横向排布。
        return width > 0 && width >= sideFileSizeMinWidth;
    }

    private int estimateItemWidth() {
        if (!(recycler instanceof RecyclerView view)) return 0;
        int available = view.getWidth() - view.getPaddingLeft() - view.getPaddingRight();
        if (available <= 0) return 0;
        RecyclerView.LayoutManager manager = view.getLayoutManager();
        int span = manager instanceof GridLayoutManager gridManager ? gridManager.getSpanCount() : 1;
        if (span <= 1) return available;
        // SpaceItemDecoration 每行横向吃掉 spacing*(span-1)，不减会高估单项宽度
        return Math.max(0, available - itemSpacing * (span - 1)) / span;
    }

    private void bindFileSize(String fileSize, boolean belowMeta) {
        binding.fileSize.setText(fileSize);
        binding.fileSize.setVisibility(TextUtils.isEmpty(fileSize) ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams params = binding.fileSize.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.topMargin = ResUtil.dp2px(belowMeta ? 36 : 8);
            binding.fileSize.setLayoutParams(marginParams);
        }
    }

    private void updateLayout() {
        ViewGroup.LayoutParams rootParams = binding.getRoot().getLayoutParams();
        if (rootParams instanceof ViewGroup.MarginLayoutParams marginParams) {
            int margin = useTmdbCard ? cardMargin : 0;
            if (marginParams.leftMargin != margin || marginParams.topMargin != margin
                    || marginParams.rightMargin != margin || marginParams.bottomMargin != margin) {
                marginParams.setMargins(margin, margin, margin, margin);
                binding.getRoot().setLayoutParams(marginParams);
            }
        }

        boolean single = getBindingAdapter() != null && getBindingAdapter().getItemCount() == 1;
        ViewGroup.LayoutParams params = binding.text.getLayoutParams();
        int width = single ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        if (params.width != width) {
            params.width = width;
            binding.text.setLayoutParams(params);
        }
        binding.text.setMaxWidth(single ? maxSingleWidth : Integer.MAX_VALUE);
        // 内边距随后由 bindNativeFileSize 按徽标占位方式决定，这里不设，避免两处打架
    }

    private void configureNativeText(boolean stacked) {
        boolean multiLine = nativeGridExpanded || stacked;
        this.nativeMultiLine = multiLine;
        ViewGroup.LayoutParams params = binding.text.getLayoutParams();
        int height = multiLine ? ViewGroup.LayoutParams.WRAP_CONTENT : ResUtil.dp2px(40);
        if (params.height != height) {
            params.height = height;
            binding.text.setLayoutParams(params);
        }
        binding.text.setHorizontallyScrolling(!multiLine);
        if (multiLine) {
            binding.text.setSingleLine(false);
            binding.text.setMaxLines(2);
            binding.text.setEllipsize(TextUtils.TruncateAt.END);
        } else {
            binding.text.setSingleLine(true);
            binding.text.setMaxLines(1);
            // 自行收尾，不依赖调用方随后一定会执行 setNativeActive
            binding.text.setEllipsize(TextUtils.TruncateAt.START);
        }
    }

    private void clearNativeActive() {
        binding.text.setSelected(false);
        binding.nativeFileSize.setSelected(false);
    }
    private void setNativeActive(boolean focused) {
        // 两行模式靠 END 省略号收尾，跑马灯/START 截断会把它顶掉
        if (!nativeMultiLine) {
            binding.text.setEllipsize(focused ? TextUtils.TruncateAt.MARQUEE : TextUtils.TruncateAt.START);
        }
        binding.text.setSelected(focused);
        binding.nativeFileSize.setSelected(focused);
    }

    private void setCardMarquee(boolean active) {
        binding.cardTitle.setSelected(active);
        binding.meta.setSelected(active);
        binding.fileSize.setSelected(active);
    }

    private void bindDetailLongClick(Episode item, View... views) {
        View.OnLongClickListener longClickListener = view -> {
            FragmentActivity activity = getActivity(view);
            if (activity == null) return false;
            EpisodeDetailDialog.show(activity, item);
            return true;
        };
        for (View view : views) {
            if (view == null) continue;
            view.setOnTouchListener(null);
            view.setOnLongClickListener(longClickListener);
        }
    }

    private void bindCardActions(Episode item, View... views) {
        View.OnClickListener clickListener = view -> listener.onItemClick(item);
        for (View view : views) {
            if (view == null) continue;
            view.setOnClickListener(clickListener);
        }
        bindDetailLongClick(item, views);
    }

    private FragmentActivity getActivity(View view) {
        Context context = view.getContext();
        while (context instanceof ContextWrapper) {
            if (context instanceof FragmentActivity) return (FragmentActivity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
