package com.fongmi.android.tv.ui.holder;

import android.content.Context;
import android.content.ContextWrapper;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.databinding.AdapterEpisodeHoriBinding;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.base.BaseEpisodeHolder;
import com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog;
import com.fongmi.android.tv.ui.helper.EpisodeCardPolicy;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class EpisodeHoriHolder extends BaseEpisodeHolder {

    private final EpisodeAdapter.OnClickListener listener;
    private final AdapterEpisodeHoriBinding binding;
    private final int maxWidth;
    private boolean useTmdbCard;
    private String fallbackStillUrl = "";

    public EpisodeHoriHolder(@NonNull AdapterEpisodeHoriBinding binding, EpisodeAdapter.OnClickListener listener) {
        super(binding.getRoot());
        this.binding = binding;
        this.listener = listener;
        this.maxWidth = ResUtil.getScreenWidth() - ResUtil.dp2px(32);
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
    public void initView(Episode item) {
        // 使用集号匹配 TMDB 数据，而不是直接使用 item.getTmdbEpisode()
        int position = getBindingAdapterPosition();
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
        if (EpisodeCardPolicy.shouldShowCard(useTmdbCard, tmdbEpisode != null, !TextUtils.isEmpty(fallbackStillUrl))) bindCard(item, tmdbEpisode);
        else bindText(item);
    }

    private void bindCard(Episode item, TmdbEpisode tmdbEpisode) {
        binding.text.setVisibility(View.GONE);
        binding.nativeFileSize.setVisibility(View.GONE);
        binding.card.setVisibility(View.VISIBLE);
        binding.text.setActivated(false);
        setTextMarquee(false);

        binding.card.setSelected(item.isSelected());
        bindCardActions(item, binding.getRoot(), binding.card, binding.still, binding.cardTitle, binding.overview);

        String cardTitle = EpisodeAdapter.getCardTitle(item, tmdbEpisode);
        binding.cardTitle.setText(cardTitle);
        binding.cardTitle.setSelected(item.isSelected());

        String rawStillUrl = tmdbEpisode == null ? "" : tmdbEpisode.getStillUrl();
        String stillUrl = TextUtils.isEmpty(rawStillUrl) ? fallbackStillUrl : rawStillUrl;
        String errorStillUrl = TextUtils.isEmpty(rawStillUrl) ? "" : fallbackStillUrl;
        binding.still.setVisibility(TextUtils.isEmpty(stillUrl) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(stillUrl)) {
            ImgUtil.load(cardTitle, stillUrl, errorStillUrl, binding.still, true, 0, 0);
        } else {
            ImgUtil.clear(binding.still);
        }

        if (tmdbEpisode != null && !TextUtils.isEmpty(tmdbEpisode.getOverview())) {
            binding.overview.setVisibility(View.VISIBLE);
            binding.overview.setText(tmdbEpisode.getOverview());
        } else {
            binding.overview.setVisibility(View.GONE);
        }

        if (tmdbEpisode != null && tmdbEpisode.getVoteAverage() > 0) {
            binding.rating.setVisibility(View.VISIBLE);
            binding.rating.setText(String.format(java.util.Locale.US, "★%.1f", tmdbEpisode.getVoteAverage()));
        } else {
            binding.rating.setVisibility(View.GONE);
        }
        bindFileSize(EpisodeAdapter.getCardFileSize(item, cardTitle));
        setCardMarquee(true);
    }

    private void bindText(Episode item) {
        binding.text.setVisibility(View.VISIBLE);
        binding.card.setVisibility(View.GONE);
        setCardMarquee(false);

        binding.text.setMaxWidth(maxWidth);
        binding.text.setActivated(item.isSelected());
        setTextMarquee(binding.text.isActivated() || binding.text.hasFocus());
        binding.text.setText(EpisodeAdapter.getNativeDisplayTitle(item));
        bindNativeFileSize(EpisodeAdapter.getNativeFileSize(item));
        binding.text.setOnFocusChangeListener((view, hasFocus) -> setTextMarquee(binding.text.isActivated() || hasFocus));
        binding.text.setOnClickListener(v -> listener.onItemClick(item));
        binding.text.post(() -> setTextMarquee(binding.text.isActivated() || binding.text.hasFocus()));
        EpisodeAdapter.bindNativeTitlePopup(binding.getRoot(), item);
        EpisodeAdapter.bindNativeTitlePopup(binding.text, item);
    }

    private void setTextMarquee(boolean active) {
        binding.text.setHorizontallyScrolling(true);
        binding.text.setSelected(active);
        binding.nativeFileSize.setSelected(active);
    }

    private void setCardMarquee(boolean active) {
        binding.cardTitle.setSelected(active);
        binding.fileSize.setSelected(active);
    }

    private void bindNativeFileSize(String fileSize) {
        boolean visible = !TextUtils.isEmpty(fileSize);
        binding.nativeFileSize.setText(fileSize);
        binding.nativeFileSize.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.nativeFileSize.setSelected(binding.text.isActivated() || binding.text.hasFocus());
        // 左右必须成对给：只改左边会让 shape_video_item 的 12dp 右内边距落单，
        // 按钮里的文本就偏向一侧而不是居中。徽标占位时左边额外让出 92dp。
        int horizontal = ResUtil.dp2px(12);
        binding.text.setPadding(visible ? ResUtil.dp2px(92) : horizontal, binding.text.getPaddingTop(), horizontal, binding.text.getPaddingBottom());
    }

    private void bindFileSize(String fileSize) {
        binding.fileSize.setText(fileSize);
        binding.fileSize.setVisibility(TextUtils.isEmpty(fileSize) ? View.GONE : View.VISIBLE);
        ViewGroup.LayoutParams params = binding.fileSize.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.topMargin = ResUtil.dp2px(8);
            binding.fileSize.setLayoutParams(marginParams);
        }
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

