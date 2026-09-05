package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.content.res.Configuration;
import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;

import com.fongmi.android.tv.BuildConfig;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.service.OmdbService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.ui.adapter.TmdbCastAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbVideoAdapter;
import com.fongmi.android.tv.ui.helper.TmdbUIAdapter;
import com.fongmi.android.tv.ui.helper.TmdbVideoPlayback;
import com.fongmi.android.tv.ui.helper.TmdbDetailLabels;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.ui.helper.TmdbCinemaTheme;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TMDB 风格详情页头部面板控制器。
 *
 * 职责：
 * 1. 将 view_tmdb_header.xml 注入到 VideoActivity 滚动区顶部
 * 2. 从 TmdbUIAdapter 读取数据并填充视图
 * 3. 加载背景图、海报、评分、演员等
 *
 * 使用方式：
 *   TmdbHeaderView headerView = new TmdbHeaderView(activity, scrollContainer);
 *   headerView.bind(tmdbUIAdapter);
 */
public class TmdbHeaderView {

    private static final int OMDB_FULL_RATING_TEXT_MAX_LENGTH = 14;
    private static final int COLOR_NATIVE_BACKGROUND = 0xFF0F141A;
    private static final int COLOR_CINEMA_BACKGROUND = 0xFF0F141A;
    private static final int COLOR_PROFILE_BACKGROUND = 0xFFEAF5F1;
    private static final int COLOR_FUSION_BACKDROP_TEXT = 0xFFFFFFFF;
    private static final int COLOR_FUSION_BACKDROP_TEXT_SECONDARY = 0xE6FFFFFF;
    private static final int COLOR_FUSION_BACKDROP_WATERMARK = 0x99FFFFFF;
    private static final int COLOR_FUSION_LINK_RATING = 0xFFFFD35C;
    private static final int COLOR_FUSION_LINK_ICON = 0xD9FFFFFF;
    private static final int COLOR_FUSION_LINK_ICON_BACKGROUND = 0x66000000;
    private static final int COLOR_FUSION_TEXT_SHADOW = 0xB0000000;
    private static final int[] BACKDROP_SECTION_LABELS = {
            R.id.tmdbCastLabel,
            R.id.tmdbCrewLabel,
            R.id.tmdbPhotosLabel,
            R.id.tmdbPostersLabel,
            R.id.tmdbExternalLinksLabel,
            R.id.tmdbRecommendationsLabel,
            R.id.tmdbRelatedVideosLabel,
            R.id.tmdbPersonalTmdbRecommendationsLabel,
            R.id.tmdbPersonalDoubanRecommendationsLabel,
            R.id.tmdbPersonalAiRecommendationsLabel,
            R.id.tmdbOmdbRatingsLabel
    };

    /**
     * 内容渲染完成回调接口
     */
    public interface OnImagesLoadedListener {
        void onImagesLoaded();
    }

    /**
     * Backdrop 图片变化回调接口（用于同步到外部 contextWall）
     */
    public interface OnBackdropChangeListener {
        void onBackdropChanged(String imageUrl);
    }

    public interface ActionListener {
        void onChangeSource();

        void onChangeSourceLongClick();

        void onRematch();

        default void onRematchLongClick() { onRematch(); }

        void onKeep();

        void onDownload();
    }

    private final Activity activity;
    private final ViewGroup scrollContainer;
    private final Task.Scope backgroundTasks = new Task.Scope(Task.recommendationExecutor());
    private View headerRoot;
    private TmdbCastAdapter castAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter photoAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter posterAdapter;
    private TmdbCastAdapter crewAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter personalTmdbRecommendationAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter personalDoubanRecommendationAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter personalAiRecommendationAdapter;
    private com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter recommendationAdapter;
    private TmdbVideoAdapter relatedVideoAdapter;
    private TmdbUIAdapter boundAdapter;
    private boolean loadingRecommendations;
    private boolean loadingPersonalTmdbRecommendations;
    private boolean loadingPersonalDoubanRecommendations;
    private final java.util.Map<String, java.util.List<String[]>> omdbRatingCache = new java.util.HashMap<>();
    private final java.util.Map<String, PersonalRecommendationService.DoubanRating> doubanRatingCache = new java.util.HashMap<>();
    private final java.util.Map<String, java.util.List<String[]>> ratingDisplayChips = new java.util.HashMap<>();

    private OnImagesLoadedListener imagesLoadedListener;
    private OnBackdropChangeListener backdropChangeListener;
    private ActionListener actionListener;

    // 幻灯片相关
    private ImageView backdropView;
    private java.util.List<String> backdropPhotos = new java.util.ArrayList<>();
    private int currentBackdropIndex = 0;
    private int detailThemeModeOverride;
    private boolean backdropSurfaceMode;
    private android.os.Handler backdropHandler;
    private Runnable backdropRunnable;
    private static final int COLOR_BACKDROP_SURFACE_CONTROL_BG = 0x6610141A;
    private static final int COLOR_BACKDROP_SURFACE_CONTROL_STROKE = 0x33FFFFFF;

    public TmdbHeaderView(Activity activity, ViewGroup scrollContainer) {
        this.activity = activity;
        this.scrollContainer = scrollContainer;
    }

    /**
     * 设置内容渲染完成监听器
     */
    public void setOnImagesLoadedListener(OnImagesLoadedListener listener) {
        this.imagesLoadedListener = listener;
    }

    /**
     * 设置 Backdrop 变化监听器（用于同步到外部 contextWall）
     */
    public void setOnBackdropChangeListener(OnBackdropChangeListener listener) {
        this.backdropChangeListener = listener;
    }

    public void setActionListener(ActionListener listener) {
        this.actionListener = listener;
    }

    public void setDetailThemeMode(int detailThemeMode) {
        int normalized = detailThemeMode == 1 ? 1 : 2;
        detailThemeModeOverride = normalized;
        applyTheme();
    }

    public void refreshTheme() {
        applyTheme();
    }

    public boolean isCurrentDetailLightTheme() {
        if (backdropSurfaceMode) return false;
        return isLightDetailChrome();
    }

    public int getFusionSectionTitleColor() {
        if (backdropSurfaceMode) return COLOR_FUSION_BACKDROP_TEXT;
        if (Setting.isFusionDetailPage()) return isCurrentDetailLightTheme() ? 0xFF12202D : COLOR_FUSION_BACKDROP_TEXT;
        return isCurrentDetailLightTheme() ? 0xFF15222B : COLOR_FUSION_BACKDROP_TEXT;
    }

    /**
     * 获取头部根视图
     */
    public View getHeaderRoot() {
        return headerRoot;
    }

    public static int getThemeBackgroundColor() {
        if (Setting.isTmdbCinemaStyle()) {
            boolean systemNight = (App.get().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            return TmdbCinemaTheme.palette(TmdbCinemaTheme.resolveLight(Setting.getTmdbDetailTheme(), systemNight)).background();
        }
        return Setting.isTmdbNativeStyle() ? COLOR_NATIVE_BACKGROUND : COLOR_PROFILE_BACKGROUND;
    }

    /**
     * 注入头部面板到滚动区顶部（仅调用一次）。
     */
    public void inflate() {
        if (headerRoot != null) return;
        headerRoot = LayoutInflater.from(activity).inflate(R.layout.view_tmdb_header, scrollContainer, false);
        scrollContainer.addView(headerRoot, 0);
        // 初始隐藏，等 bind 完成后再显示
        headerRoot.setVisibility(View.GONE);
        setupRecyclerViews();
        setupActions();
        updateOriginalEnhancedActionVisibility();
        applyTheme();
    }

    public void setKeepSelected(boolean selected) {
        if (headerRoot == null) return;
        MaterialButton keep = headerRoot.findViewById(R.id.tmdbKeep);
        if (keep == null) return;
        keep.setSelected(selected);
        keep.setText(TmdbDetailLabels.keepLabel(selected));
    }

    /**
     * 绑定 TMDB 数据并填充视图。
     */
    public void bind(TmdbUIAdapter adapter) {
        if (headerRoot == null || adapter == null || !adapter.isLoaded()) {
            android.util.Log.w("TmdbHeaderView", "bind() 跳过：headerRoot=" + (headerRoot != null) + " adapter=" + (adapter != null) + " isLoaded=" + (adapter != null && adapter.isLoaded()));
            return;
        }

        TmdbItem item = adapter.getTmdbItem();
        JsonObject detail = adapter.getTmdbDetail();
        if (item == null || detail == null) {
            android.util.Log.w("TmdbHeaderView", "bind() 跳过：item=" + (item != null) + " detail=" + (detail != null));
            return;
        }

        backgroundTasks.cancelAll();
        android.util.Log.d("TmdbHeaderView", "bind() 开始，标题=" + item.getTitle());
        boundAdapter = adapter;
        applyTheme();

        // 背景图（backdrop）- 改为幻灯片模式
        backdropView = headerRoot.findViewById(R.id.tmdbBackdrop);
        setupBackdropSlideshow(adapter);

        // 海报
        ImageView poster = headerRoot.findViewById(R.id.tmdbPoster);
        String posterUrl = adapter.getPosterUrl();
        ImgUtil.load(item.getTitle(), posterUrl, poster);
        ImageView fusionPoster = headerRoot.findViewById(R.id.tmdbFusionPoster);
        if (fusionPoster != null) ImgUtil.load(item.getTitle(), posterUrl, fusionPoster);

        // 标题
        TextView title = headerRoot.findViewById(R.id.tmdbTitle);
        title.setText(item.getTitle());
        TextView fusionTitle = headerRoot.findViewById(R.id.tmdbFusionTitle);
        if (fusionTitle != null) fusionTitle.setText(item.getTitle());

        // 评分徽章（隐藏，已在简介上方显示详细评分）
        TextView rating = headerRoot.findViewById(R.id.tmdbRating);
        rating.setVisibility(View.GONE);

        // 元数据（类型 · 年份），移除左边距因为评分已隐藏
        TextView meta = headerRoot.findViewById(R.id.tmdbMeta);
        String mediaType = "tv".equals(item.getMediaType())
                ? activity.getString(R.string.detail_media_tv)
                : activity.getString(R.string.detail_media_movie);
        String year = extractYear(detail);
        String episodeInfo = adapter.getEpisodeDetailText();
        List<String> metaParts = new ArrayList<>();
        metaParts.add(mediaType);
        if (!TextUtils.isEmpty(year)) metaParts.add(year);
        if (!TextUtils.isEmpty(episodeInfo)) metaParts.add(episodeInfo);
        meta.setText(TextUtils.join(" · ", metaParts));
        // 评分徽章已隐藏，移除元数据左边距使其左对齐
        ViewGroup.MarginLayoutParams metaParams = (ViewGroup.MarginLayoutParams) meta.getLayoutParams();
        metaParams.setMarginStart(0);
        meta.setLayoutParams(metaParams);
        TextView fusionSubtitle = headerRoot.findViewById(R.id.tmdbFusionSubtitle);
        if (fusionSubtitle != null) fusionSubtitle.setText(buildFusionSubtitle(detail, adapter.getRatingText(), episodeInfo));

        // 类型标签
        TextView genres = headerRoot.findViewById(R.id.tmdbGenres);
        String genresText = adapter.getGenresText();
        if (!TextUtils.isEmpty(genresText)) {
            genres.setText(genresText);
            genres.setVisibility(View.VISIBLE);
        } else {
            genres.setVisibility(View.GONE);
        }

        // 简介
        TextView overview = headerRoot.findViewById(R.id.tmdbOverview);
        String overviewText = detail.has("overview") && !detail.get("overview").isJsonNull()
                ? detail.get("overview").getAsString() : "";
        android.util.Log.d("TmdbHeaderView", "简介长度=" + overviewText.length() + " 内容=" + (overviewText.length() > 20 ? overviewText.substring(0, 20) : overviewText));
        if (!TextUtils.isEmpty(overviewText)) {
            overview.setText(overviewText);
            overview.setVisibility(View.VISIBLE);
            // 点击展开/收起
            overview.setOnClickListener(v -> {
                int currentMaxLines = overview.getMaxLines();
                overview.setMaxLines(currentMaxLines == 4 ? Integer.MAX_VALUE : 4);
            });
        } else {
            overview.setVisibility(View.GONE);
        }
        bindFusionPanel(adapter, item, detail, overviewText);

        // 演员
        if (!adapter.getCast().isEmpty()) {
            headerRoot.findViewById(R.id.tmdbCastLabel).setVisibility(View.VISIBLE);
            RecyclerView castRv = headerRoot.findViewById(R.id.tmdbCast);
            castRv.setVisibility(View.VISIBLE);
            castAdapter.setItems(adapter.getCast());
        } else {
            headerRoot.findViewById(R.id.tmdbCastLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbCast).setVisibility(View.GONE);
        }

        // 剧照
        java.util.List<String> photos = adapter.getPhotos();
        if (!photos.isEmpty()) {
            headerRoot.findViewById(R.id.tmdbPhotosLabel).setVisibility(View.VISIBLE);
            RecyclerView photosRv = headerRoot.findViewById(R.id.tmdbPhotos);
            photosRv.setVisibility(View.VISIBLE);
            photoAdapter.setItems(photos);
        } else {
            headerRoot.findViewById(R.id.tmdbPhotosLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbPhotos).setVisibility(View.GONE);
        }

        // 海报
        java.util.List<String> posters = adapter.getPosters();
        if (!posters.isEmpty()) {
            headerRoot.findViewById(R.id.tmdbPostersLabel).setVisibility(View.VISIBLE);
            RecyclerView postersRv = headerRoot.findViewById(R.id.tmdbPosters);
            postersRv.setVisibility(View.VISIBLE);
            posterAdapter.setItems(posters);
        } else {
            headerRoot.findViewById(R.id.tmdbPostersLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbPosters).setVisibility(View.GONE);
        }

        // 主创团队
        if (!adapter.getCreators().isEmpty()) {
            headerRoot.findViewById(R.id.tmdbCrewLabel).setVisibility(View.VISIBLE);
            RecyclerView crewRv = headerRoot.findViewById(R.id.tmdbCrew);
            crewRv.setVisibility(View.VISIBLE);
            crewAdapter.setItems(adapter.getCreators());
        } else {
            headerRoot.findViewById(R.id.tmdbCrewLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbCrew).setVisibility(View.GONE);
        }

        // 外部链接
        setupExternalLinks(adapter);

        // 猜你喜欢
        if (!adapter.getRecommendations().isEmpty()) {
            headerRoot.findViewById(R.id.tmdbRecommendationsLabel).setVisibility(View.VISIBLE);
            RecyclerView recommendationsRv = headerRoot.findViewById(R.id.tmdbRecommendations);
            recommendationsRv.setVisibility(View.VISIBLE);
            recommendationAdapter.setItems(adapter.getRecommendations());
        } else {
            headerRoot.findViewById(R.id.tmdbRecommendationsLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbRecommendations).setVisibility(View.GONE);
        }

        // 个性推荐
        bindRelatedVideoRow(adapter.getRelatedVideos());

        bindRecommendationRow(R.id.tmdbPersonalTmdbRecommendationsLabel, R.id.tmdbPersonalTmdbRecommendations, personalTmdbRecommendationAdapter, adapter.getPersonalTmdbRecommendations());
        bindRecommendationRow(R.id.tmdbPersonalDoubanRecommendationsLabel, R.id.tmdbPersonalDoubanRecommendations, personalDoubanRecommendationAdapter, adapter.getPersonalDoubanRecommendations());
        bindPersonalAiRecommendationRow(adapter.getPersonalAiRecommendations());

        // 内容填充完成，显示头部容器
        applyTheme();
        headerRoot.setVisibility(View.VISIBLE);

        // bind 完成，通知监听器
        android.util.Log.d("TmdbHeaderView", "bind 完成，显示容器并通知监听器");
        if (imagesLoadedListener != null) {
            imagesLoadedListener.onImagesLoaded();
        }
    }

    /**
     * 移除头部面板（切换回普通模式时）。
     */
    public void remove() {
        backgroundTasks.cancelAll();
        stopBackdropSlideshow();
        if (headerRoot != null && headerRoot.getParent() == scrollContainer) {
            scrollContainer.removeView(headerRoot);
            headerRoot = null;
        }
    }

    /**
     * 强制显示头部容器（用于 TMDB 加载失败时显示播放控件）。
     */
    public void show() {
        if (headerRoot != null) headerRoot.setVisibility(View.VISIBLE);
    }


    public void refreshRecommendations() {
        if (boundAdapter == null || headerRoot == null || recommendationAdapter == null) return;
        bindRecommendationRow(R.id.tmdbRecommendationsLabel, R.id.tmdbRecommendations, recommendationAdapter, boundAdapter.getRecommendations());
    }

    public void refreshRelatedVideos() {
        if (boundAdapter == null || headerRoot == null || relatedVideoAdapter == null) return;
        bindRelatedVideoRow(boundAdapter.getRelatedVideos());
    }

    public void refreshPersonalRecommendationRows() {
        if (boundAdapter == null || headerRoot == null) return;
        bindRecommendationRow(R.id.tmdbPersonalTmdbRecommendationsLabel, R.id.tmdbPersonalTmdbRecommendations, personalTmdbRecommendationAdapter, boundAdapter.getPersonalTmdbRecommendations());
        bindRecommendationRow(R.id.tmdbPersonalDoubanRecommendationsLabel, R.id.tmdbPersonalDoubanRecommendations, personalDoubanRecommendationAdapter, boundAdapter.getPersonalDoubanRecommendations());
        bindPersonalAiRecommendationRow(boundAdapter.getPersonalAiRecommendations());
    }

    public void refreshEpisodeMetadata() {
        if (boundAdapter == null || headerRoot == null) return;
        TmdbItem item = boundAdapter.getTmdbItem();
        JsonObject detail = boundAdapter.getTmdbDetail();
        if (item == null || detail == null) return;
        TextView meta = headerRoot.findViewById(R.id.tmdbMeta);
        String mediaType = "tv".equals(item.getMediaType())
                ? activity.getString(R.string.detail_media_tv)
                : activity.getString(R.string.detail_media_movie);
        String year = extractYear(detail);
        String episodeInfo = boundAdapter.getEpisodeDetailText();
        List<String> metaParts = new ArrayList<>();
        metaParts.add(mediaType);
        if (!TextUtils.isEmpty(year)) metaParts.add(year);
        if (!TextUtils.isEmpty(episodeInfo)) metaParts.add(episodeInfo);
        meta.setText(TextUtils.join(" · ", metaParts));
        TextView fusionSubtitle = headerRoot.findViewById(R.id.tmdbFusionSubtitle);
        if (fusionSubtitle != null) fusionSubtitle.setText(buildFusionSubtitle(detail, boundAdapter.getRatingText(), episodeInfo));
    }

    public void refreshPersonalRecommendations() {
        if (boundAdapter == null || headerRoot == null) return;
        boundAdapter.refreshPersonalRecommendations(changed -> {
            if (!changed || headerRoot == null) return;
            bindRecommendationRow(R.id.tmdbPersonalTmdbRecommendationsLabel, R.id.tmdbPersonalTmdbRecommendations, personalTmdbRecommendationAdapter, boundAdapter.getPersonalTmdbRecommendations());
            bindRecommendationRow(R.id.tmdbPersonalDoubanRecommendationsLabel, R.id.tmdbPersonalDoubanRecommendations, personalDoubanRecommendationAdapter, boundAdapter.getPersonalDoubanRecommendations());
        });
    }

    public void refreshPersonalAiRecommendations() {
        if (boundAdapter == null || headerRoot == null) return;
        bindPersonalAiRecommendationRow(boundAdapter.getPersonalAiRecommendations());
    }

    private void setupRecyclerViews() {
        RecyclerView castRv = headerRoot.findViewById(R.id.tmdbCast);
        castAdapter = new TmdbCastAdapter();
        castAdapter.setOnItemClickListener(this::onPersonClick);
        castRv.setAdapter(castAdapter);

        RecyclerView photosRv = headerRoot.findViewById(R.id.tmdbPhotos);
        photoAdapter = new com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter();
        photoAdapter.setOnItemClickListener(this::onPhotoClick);
        photosRv.setAdapter(photoAdapter);

        RecyclerView postersRv = headerRoot.findViewById(R.id.tmdbPosters);
        posterAdapter = new com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter(true);
        posterAdapter.setOnItemClickListener(this::onPosterClick);
        postersRv.setAdapter(posterAdapter);

        RecyclerView crewRv = headerRoot.findViewById(R.id.tmdbCrew);
        crewAdapter = new TmdbCastAdapter();
        crewAdapter.setOnItemClickListener(this::onPersonClick);
        crewRv.setAdapter(crewAdapter);

        RecyclerView personalTmdbRecommendationsRv = headerRoot.findViewById(R.id.tmdbPersonalTmdbRecommendations);
        personalTmdbRecommendationAdapter = new com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter();
        personalTmdbRecommendationAdapter.setOnItemClickListener(this::onRecommendationClick);
        personalTmdbRecommendationAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "tmdb"));
        personalTmdbRecommendationsRv.setAdapter(personalTmdbRecommendationAdapter);
        attachLazyLoader(personalTmdbRecommendationsRv, RecommendationRow.PERSONAL_TMDB);

        RecyclerView personalDoubanRecommendationsRv = headerRoot.findViewById(R.id.tmdbPersonalDoubanRecommendations);
        personalDoubanRecommendationAdapter = new com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter();
        personalDoubanRecommendationAdapter.setOnItemClickListener(this::onRecommendationClick);
        personalDoubanRecommendationAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "douban"));
        personalDoubanRecommendationsRv.setAdapter(personalDoubanRecommendationAdapter);
        attachLazyLoader(personalDoubanRecommendationsRv, RecommendationRow.PERSONAL_DOUBAN);

        RecyclerView personalAiRecommendationsRv = headerRoot.findViewById(R.id.tmdbPersonalAiRecommendations);
        personalAiRecommendationAdapter = new com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter();
        personalAiRecommendationAdapter.setOnItemClickListener(this::onRecommendationClick);
        personalAiRecommendationAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "ai"));
        personalAiRecommendationAdapter.setOnItemFocusListener(this::showAiRecommendationReason);
        personalAiRecommendationsRv.setAdapter(personalAiRecommendationAdapter);

        RecyclerView relatedVideosRv = headerRoot.findViewById(R.id.tmdbRelatedVideos);
        relatedVideoAdapter = new TmdbVideoAdapter();
        relatedVideoAdapter.setOnItemClickListener(this::onRelatedVideoClick);
        relatedVideosRv.setAdapter(relatedVideoAdapter);

        RecyclerView recommendationsRv = headerRoot.findViewById(R.id.tmdbRecommendations);
        recommendationAdapter = new com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter();
        recommendationAdapter.setOnItemClickListener(this::onRecommendationClick);
        recommendationAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "related"));
        recommendationsRv.setAdapter(recommendationAdapter);
        attachLazyLoader(recommendationsRv, RecommendationRow.RECOMMENDATIONS);
    }

    private boolean onRecommendationLongClick(TmdbItem item, String source) {
        com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog.show(activity, item, source, this::onRecommendationNotInterested);
        return true;
    }

    private void onRecommendationNotInterested(TmdbItem item) {
        if (boundAdapter != null) boundAdapter.removeRecommendation(item);
        recommendationAdapter.removeItem(item);
        personalTmdbRecommendationAdapter.removeItem(item);
        personalDoubanRecommendationAdapter.removeItem(item);
        personalAiRecommendationAdapter.removeItem(item);
        showAiRecommendationReason(item, false);
        refreshPersonalRecommendations();
    }

    private void bindRelatedVideoRow(List<TmdbVideo> items) {
        if (items != null && !items.isEmpty()) {
            headerRoot.findViewById(R.id.tmdbRelatedVideosLabel).setVisibility(View.VISIBLE);
            headerRoot.findViewById(R.id.tmdbRelatedVideos).setVisibility(View.VISIBLE);
            relatedVideoAdapter.setItems(items);
        } else {
            relatedVideoAdapter.setItems(new ArrayList<>());
            headerRoot.findViewById(R.id.tmdbRelatedVideosLabel).setVisibility(View.GONE);
            headerRoot.findViewById(R.id.tmdbRelatedVideos).setVisibility(View.GONE);
        }
    }

    private void bindRecommendationRow(int labelId, int recyclerId, com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter adapter, List<TmdbItem> items) {
        if (items != null && !items.isEmpty()) {
            headerRoot.findViewById(labelId).setVisibility(View.VISIBLE);
            RecyclerView recyclerView = headerRoot.findViewById(recyclerId);
            recyclerView.setVisibility(View.VISIBLE);
            adapter.setItems(items);
        } else {
            adapter.setItems(new ArrayList<>());
            headerRoot.findViewById(labelId).setVisibility(View.GONE);
            headerRoot.findViewById(recyclerId).setVisibility(View.GONE);
        }
    }

    private void bindPersonalAiRecommendationRow(List<TmdbItem> items) {
        bindRecommendationRow(R.id.tmdbPersonalAiRecommendationsLabel, R.id.tmdbPersonalAiRecommendations, personalAiRecommendationAdapter, items);
        if (items == null || items.isEmpty()) showAiRecommendationReason(null, false);
    }

    private void showAiRecommendationReason(TmdbItem item, boolean focused) {
        if (headerRoot == null) return;
        TextView reason = headerRoot.findViewById(R.id.tmdbPersonalAiReason);
        if (reason == null) return;
        String text = item == null ? "" : item.getRecommendationReason();
        if (TextUtils.isEmpty(text) && item != null) text = item.getOverview();
        if (!focused || TextUtils.isEmpty(text)) {
            reason.setText("");
            reason.setVisibility(View.GONE);
            return;
        }
        reason.setText(activity.getString(R.string.ai_recommendation_reason_preview, text));
        reason.setVisibility(View.VISIBLE);
    }

    private void attachLazyLoader(RecyclerView recyclerView, RecommendationRow row) {
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (dx <= 0 || !isNearRowEnd(recyclerView)) return;
                loadMore(row);
            }
        });
    }

    private boolean isNearRowEnd(RecyclerView recyclerView) {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (adapter == null || !(manager instanceof LinearLayoutManager)) return false;
        int lastVisible = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        return lastVisible >= 0 && adapter.getItemCount() - lastVisible <= 4;
    }

    private void loadMore(RecommendationRow row) {
        if (boundAdapter == null) return;
        if (row == RecommendationRow.RECOMMENDATIONS) {
            if (loadingRecommendations || !boundAdapter.hasMoreRecommendations()) return;
            loadingRecommendations = true;
            boundAdapter.loadMoreRecommendations(changed -> {
                loadingRecommendations = false;
                if (changed) recommendationAdapter.setItems(boundAdapter.getRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_TMDB) {
            if (loadingPersonalTmdbRecommendations || !boundAdapter.hasMorePersonalTmdbRecommendations()) return;
            loadingPersonalTmdbRecommendations = true;
            boundAdapter.loadMorePersonalTmdbRecommendations(changed -> {
                loadingPersonalTmdbRecommendations = false;
                if (changed) personalTmdbRecommendationAdapter.setItems(boundAdapter.getPersonalTmdbRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_DOUBAN) {
            if (loadingPersonalDoubanRecommendations || !boundAdapter.hasMorePersonalDoubanRecommendations()) return;
            loadingPersonalDoubanRecommendations = true;
            boundAdapter.loadMorePersonalDoubanRecommendations(changed -> {
                loadingPersonalDoubanRecommendations = false;
                if (changed) personalDoubanRecommendationAdapter.setItems(boundAdapter.getPersonalDoubanRecommendations());
            });
        }
    }

    private enum RecommendationRow {
        RECOMMENDATIONS, PERSONAL_TMDB, PERSONAL_DOUBAN
    }

    private void setupActions() {
        View tmdbChangeSource = headerRoot.findViewById(R.id.tmdbChangeSource);
        tmdbChangeSource.setOnClickListener(view -> {
            if (actionListener != null) actionListener.onChangeSource();
        });
        tmdbChangeSource.setOnLongClickListener(view -> {
            if (actionListener != null) actionListener.onChangeSourceLongClick();
            return true;
        });
        View rematch = headerRoot.findViewById(R.id.tmdbRematch);
        rematch.setOnClickListener(view -> {
            if (actionListener != null) actionListener.onRematch();
        });
        rematch.setOnLongClickListener(view -> {
            if (actionListener == null) return false;
            actionListener.onRematchLongClick();
            return true;
        });
        headerRoot.findViewById(R.id.tmdbKeep).setOnClickListener(view -> {
            if (actionListener != null) actionListener.onKeep();
        });
        View downloadBtn = headerRoot.findViewById(R.id.tmdbDownload);
        if (downloadBtn != null) {
            // 下载按钮仅在 mobile 端可用（下载功能仅 mobile 源码集提供）
            downloadBtn.setVisibility("mobile".equals(BuildConfig.FLAVOR_mode) ? View.VISIBLE : View.GONE);
            downloadBtn.setOnClickListener(view -> {
                if (actionListener != null) actionListener.onDownload();
            });
        }
    }

    private void updateOriginalEnhancedActionVisibility() {
        if (headerRoot == null) return;
        View changeSource = headerRoot.findViewById(R.id.tmdbChangeSource);
        if (changeSource == null) return;
        // 原来：原生增强模式隐藏换源按钮
        // 现在：所有模式都显示换源按钮
        changeSource.setVisibility(View.VISIBLE);
    }

    /**
     * 点击剧照：使用 PhotoViewerDialog 查看。
     */
    private void onPhotoClick(String url, int position) {
        if (TextUtils.isEmpty(url)) return;
        java.util.List<String> photos = photoAdapter.getItems();
        com.fongmi.android.tv.ui.dialog.PhotoViewerDialog.show(activity, photos, position, null);
    }


    private void onPosterClick(String url, int position) {
        if (TextUtils.isEmpty(url)) return;
        java.util.List<String> posters = posterAdapter.getItems();
        com.fongmi.android.tv.ui.dialog.PhotoViewerDialog.show(activity, posters, position, null);
    }

    /**
     * 点击演员/主创：显示简介弹窗。
     */
    private void onPersonClick(com.fongmi.android.tv.bean.TmdbPerson person) {
        if (person == null) return;
        com.fongmi.android.tv.ui.dialog.TmdbPersonDialog.show(activity, person, currentSite());
    }

    /**
     * 点击"猜你喜欢"卡片：以新的 TMDB 条目打开详情页。
     */
    private void onRecommendationClick(TmdbItem item) {
        TmdbNavigation.open(activity, item, currentSite());
    }

    private void onRelatedVideoClick(TmdbVideo item) {
        TmdbVideoPlayback.play(activity, item);
    }

    private com.fongmi.android.tv.bean.Site currentSite() {
        String key = activity == null || activity.getIntent() == null ? "" : activity.getIntent().getStringExtra("key");
        return com.fongmi.android.tv.api.config.VodConfig.get().getSite(key);
    }

    private String extractYear(JsonObject detail) {
        if (detail == null) return "";
        // 电影用 release_date，剧集用 first_air_date
        String dateField = detail.has("release_date") ? "release_date" : "first_air_date";
        if (!detail.has(dateField) || detail.get(dateField).isJsonNull()) return "";
        String date = detail.get(dateField).getAsString();
        if (date.length() >= 4) return date.substring(0, 4);
        return "";
    }

    private String extractDate(JsonObject detail) {
        if (detail == null) return "";
        String dateField = detail.has("release_date") ? "release_date" : "first_air_date";
        if (!detail.has(dateField) || detail.get(dateField).isJsonNull()) return "";
        return detail.get(dateField).getAsString();
    }

    private String buildFusionSubtitle(JsonObject detail, String rating, String episodeInfo) {
        List<String> parts = new ArrayList<>();
        String date = extractDate(detail);
        if (!TextUtils.isEmpty(date)) parts.add(date);
        if (!TextUtils.isEmpty(rating)) parts.add("评分 " + rating);
        if (!TextUtils.isEmpty(episodeInfo)) parts.add(episodeInfo);
        return TextUtils.join(" · ", parts);
    }

    private void bindFusionPanel(TmdbUIAdapter adapter, TmdbItem item, JsonObject detail, String overviewText) {
        if (headerRoot == null) return;
        TextView overview = headerRoot.findViewById(R.id.tmdbFusionOverview);
        if (overview != null) {
            overview.setText(overviewText);
            overview.setVisibility(TextUtils.isEmpty(overviewText) ? View.GONE : View.VISIBLE);
            overview.setOnClickListener(v -> overview.setMaxLines(overview.getMaxLines() == 5 ? Integer.MAX_VALUE : 5));
        }
        TextView source = headerRoot.findViewById(R.id.tmdbFusionSource);
        if (source != null) {
            String siteName = currentSite() == null ? "" : currentSite().getDisplayName();
            source.setText(TextUtils.isEmpty(siteName) ? "" : "站源： " + siteName);
            source.setVisibility(TextUtils.isEmpty(siteName) ? View.GONE : View.VISIBLE);
        }
        FlexboxLayout meta = headerRoot.findViewById(R.id.tmdbFusionMeta);
        if (meta != null) {
            meta.removeAllViews();
            boolean tv = "tv".equals(item.getMediaType());
            addFusionChip(meta, tv ? "剧集" : "电影");
            int sourceSeason = adapter.getSourceSeasonNumber();
            if (tv && sourceSeason >= 0 && !adapter.getEpisodeInfo().isSeasonScoped()) addFusionChip(meta, activity.getString(R.string.detail_season_format, sourceSeason));
            addFusionChip(meta, extractYear(detail));
            addFusionChips(meta, adapter.getGenresText(), 3);
            addFusionChip(meta, firstCountry(detail));
            addFusionChip(meta, firstCreator(detail));
            meta.setVisibility(meta.getChildCount() == 0 ? View.GONE : View.VISIBLE);
        }
    }

    private void addFusionChips(FlexboxLayout container, String text, int limit) {
        if (TextUtils.isEmpty(text)) return;
        String[] values = text.split("[·•/、,，]");
        int count = 0;
        for (String value : values) {
            String chip = value == null ? "" : value.trim();
            if (TextUtils.isEmpty(chip)) continue;
            addFusionChip(container, chip);
            if (++count >= limit) break;
        }
    }

    private void addFusionChip(FlexboxLayout container, String text) {
        if (container == null || TextUtils.isEmpty(text)) return;
        boolean dark = Setting.isFusionDetailPage() ? isDarkDetailTheme() : Setting.isTmdbCinemaStyle() || Setting.isTmdbNativeStyle();
        com.google.android.material.textview.MaterialTextView chip = new com.google.android.material.textview.MaterialTextView(activity);
        chip.setText(text.trim());
        chip.setTextColor(dark ? 0xE6FFFFFF : 0xFF12202D);
        chip.setTextSize(12);
        chip.setSingleLine(true);
        chip.setIncludeFontPadding(false);
        chip.setPadding(ResUtil.dp2px(8), ResUtil.dp2px(5), ResUtil.dp2px(8), ResUtil.dp2px(5));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(dark ? 0x26FFFFFF : 0xDDEAF0F5);
        background.setCornerRadius(ResUtil.dp2px(12));
        background.setStroke(ResUtil.dp2px(1), dark ? 0x33FFFFFF : 0x33424B57);
        chip.setBackground(background);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, ResUtil.dp2px(6), ResUtil.dp2px(6));
        chip.setLayoutParams(params);
        container.addView(chip);
    }

    private String firstCountry(JsonObject detail) {
        JsonArray countries = jsonArray(detail, "production_countries");
        for (JsonElement element : countries) {
            if (!element.isJsonObject()) continue;
            String code = jsonString(element.getAsJsonObject(), "iso_3166_1");
            String name = jsonString(element.getAsJsonObject(), "name");
            if (!TextUtils.isEmpty(code)) return code;
            if (!TextUtils.isEmpty(name)) return name;
        }
        return "";
    }

    private String firstCreator(JsonObject detail) {
        JsonArray creators = jsonArray(detail, "created_by");
        for (JsonElement element : creators) {
            if (!element.isJsonObject()) continue;
            String name = jsonString(element.getAsJsonObject(), "name");
            if (!TextUtils.isEmpty(name)) return name;
        }
        return "";
    }

    private int parseYear(String year) {
        if (TextUtils.isEmpty(year)) return 0;
        try {
            return Integer.parseInt(year);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    /**
     * 设置背景图幻灯片模式
     */
    private void setupBackdropSlideshow(TmdbUIAdapter adapter) {
        TmdbItem item = adapter.getTmdbItem();
        if (item == null) return;

        // 收集所有可用的背景图，优先使用已按设备方向与清晰度筛选的图片。
        backdropPhotos.clear();
        java.util.List<String> photos = adapter.getBackgroundPhotos();
        android.util.Log.d("TmdbHeaderView", "setupBackdropSlideshow: photos count=" + (photos != null ? photos.size() : 0));
        if (photos != null) {
            for (String photo : photos) {
                String highResPhoto = TmdbImageSelector.originalUrl(photo);
                if (!TextUtils.isEmpty(highResPhoto) && !backdropPhotos.contains(highResPhoto)) {
                    backdropPhotos.add(highResPhoto);
                }
            }
        }
        android.util.Log.d("TmdbHeaderView", "setupBackdropSlideshow: total backdropPhotos=" + backdropPhotos.size());

        // 如果只有一张图，直接加载
        if (backdropPhotos.isEmpty()) {
            android.util.Log.w("TmdbHeaderView", "setupBackdropSlideshow: 没有可用的背景图");
            return;
        }

        if (backdropPhotos.size() == 1) {
            android.util.Log.d("TmdbHeaderView", "setupBackdropSlideshow: 只有一张图，不启动轮播");
            loadBackdropIntoView(backdropPhotos.get(0));
            // 通知外部监听器第一张图片
            if (backdropChangeListener != null) {
                backdropChangeListener.onBackdropChanged(backdropPhotos.get(0));
            }
            return;
        }

        // 多张图片，启动幻灯片
        android.util.Log.d("TmdbHeaderView", "setupBackdropSlideshow: 启动轮播，共 " + backdropPhotos.size() + " 张图片");
        startBackdropSlideshow();
    }

    /**
     * 启动背景图幻灯片自动切换
     */
    private void startBackdropSlideshow() {
        stopBackdropSlideshow();

        backdropHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        backdropRunnable = new Runnable() {
            @Override
            public void run() {
                if (backdropPhotos == null || backdropPhotos.isEmpty()) return;

                // 预加载下一张图片
                int nextIndex = (currentBackdropIndex + 1) % backdropPhotos.size();
                String nextUrl = TmdbImageSelector.originalUrl(backdropPhotos.get(nextIndex));
                Object model = ImgUtil.getUrl(nextUrl);
                if (model == null) {
                    currentBackdropIndex = nextIndex;
                    if (backdropHandler != null && backdropRunnable != null) backdropHandler.postDelayed(backdropRunnable, 500);
                    return;
                }

                // 使用 Glide 预加载，加载完成后切换
                Glide.with(activity)
                        .load(model)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .dontAnimate()
                        .centerCrop()
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(com.bumptech.glide.load.engine.GlideException e, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, boolean isFirstResource) {
                                // 加载失败，跳过这张，继续下一张
                                currentBackdropIndex = nextIndex;
                                if (backdropHandler != null && backdropRunnable != null) {
                                    backdropHandler.postDelayed(backdropRunnable, 500);
                                }
                                return true;
                            }

                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model, com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target, com.bumptech.glide.load.DataSource dataSource, boolean isFirstResource) {
                                // 图片加载完成，切换到这张图
                                currentBackdropIndex = nextIndex;
                                backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                                backdropView.setImageDrawable(resource);

                                android.util.Log.d("TmdbHeaderView", "轮播切换到第 " + currentBackdropIndex + " 张，URL=" + backdropPhotos.get(nextIndex));

                                // 通知外部监听器（用于同步到 contextWall）
                                if (backdropChangeListener != null) {
                                    android.util.Log.d("TmdbHeaderView", "通知 backdropChangeListener");
                                    backdropChangeListener.onBackdropChanged(backdropPhotos.get(nextIndex));
                                } else {
                                    android.util.Log.w("TmdbHeaderView", "backdropChangeListener 为 null，无法通知");
                                }

                                // 5秒后切换下一张
                                if (backdropHandler != null && backdropRunnable != null) {
                                    backdropHandler.postDelayed(backdropRunnable, 5000);
                                }
                                return true;
                            }
                        })
                        .preload();
            }
        };

        // 加载第一张
        if (!backdropPhotos.isEmpty()) {
            loadBackdropIntoView(backdropPhotos.get(0));
            currentBackdropIndex = 0;
            // 通知外部监听器第一张图片
            if (backdropChangeListener != null) {
                backdropChangeListener.onBackdropChanged(backdropPhotos.get(0));
            }
            // 5秒后开始切换
            backdropHandler.postDelayed(backdropRunnable, 5000);
        }
    }

    private void loadBackdropIntoView(String url) {
        if (TextUtils.isEmpty(url) || backdropView == null) return;
        Object model = ImgUtil.getUrl(TmdbImageSelector.originalUrl(url));
        if (model == null) return;
        backdropView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(activity)
                .load(model)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .dontAnimate()
                .centerCrop()
                .into(backdropView);
    }

    /**
     * 停止背景图幻灯片
     */
    private void stopBackdropSlideshow() {
        if (backdropHandler != null && backdropRunnable != null) {
            backdropHandler.removeCallbacks(backdropRunnable);
        }
    }

    /**
     * 添加评分展示区域（在简介上方）
     */
    private void addRatingsDisplay(ViewGroup container, String tmdbRating, com.google.gson.JsonObject externalIds, int tmdbId, String mediaType, String title, int year, com.google.android.material.textview.MaterialTextView doubanRatingView, com.google.gson.JsonObject detail) {
        container.removeAllViews();
        String displayKey = ratingDisplayKey(title, mediaType, year, tmdbId);
        container.setTag(displayKey);

        List<String[]> baseChips = new ArrayList<>();
        if (!TextUtils.isEmpty(tmdbRating)) {
            baseChips.add(new String[]{"TMDB", tmdbRating + "/10", "#21D07A"});
        }
        String[] boxOffice = boxOfficeChip(detail, mediaType);
        if (boxOffice != null) baseChips.add(boxOffice);
        setRatingDisplayChips(displayKey, baseChips);
        renderRatingChips(container, getRatingDisplayChips(displayKey));
        fetchDoubanRatingForDisplay(title, mediaType, year, displayKey, container, doubanRatingView);

        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) return;
        String imdbId = externalIds.get("imdb_id").getAsString();
        if (TextUtils.isEmpty(imdbId)) return;

        com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(com.fongmi.android.tv.setting.Setting.getTmdbConfig());
        String omdbApiKey = tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(omdbApiKey)) return;

        String cacheKey = omdbRatingCacheKey(imdbId, omdbApiKey);
        java.util.List<String[]> cachedChips = getCachedOmdbRatingChips(cacheKey);
        if (cachedChips != null) {
            putRatingDisplayChips(displayKey, cachedChips);
            renderRatingChips(container, getRatingDisplayChips(displayKey));
            return;
        }

        fetchRatingChipsForDisplay(imdbId, omdbApiKey, cacheKey, displayKey, container);
    }

    /**
     * 创建评分 Chip
     */
    private com.google.android.material.textview.MaterialTextView createRatingChip(String platform, String value, String color) {
        boolean fusion = Setting.isFusionDetailPage();
        boolean lightChrome = isLightDetailChrome() || (fusion && !isDarkDetailTheme());
        boolean lightSurface = !backdropSurfaceMode && lightChrome;
        com.google.android.material.textview.MaterialTextView chip = new com.google.android.material.textview.MaterialTextView(activity);
        chip.setText(platform + " ★ " + value);
        chip.setTag(color);
        chip.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT : resolveRatingChipTextColor(color, lightSurface));
        chip.setTextSize(12);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setSingleLine(true);
        chip.setIncludeFontPadding(false);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(ResUtil.dp2px(8), ResUtil.dp2px(5), ResUtil.dp2px(8), ResUtil.dp2px(5));

        // 设置圆角背景
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setCornerRadius(ResUtil.dp2px(6));  // 圆角
        chip.setBackground(background);
        styleRatingChip(chip, fusion, lightChrome, lightSurface);

        com.google.android.flexbox.FlexboxLayout.LayoutParams params =
                new com.google.android.flexbox.FlexboxLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(ResUtil.dp2px(8));
        params.setMargins(0, 0, ResUtil.dp2px(8), ResUtil.dp2px(8));
        chip.setLayoutParams(params);
        return chip;
    }

    private int resolveRatingChipTextColor(String color, boolean useLightSurface) {
        if (!useLightSurface) return android.graphics.Color.parseColor(color);
        if ("#21D07A".equalsIgnoreCase(color) || "#00B51D".equalsIgnoreCase(color)) return 0xFF0F7A4A;
        if ("#F5C518".equalsIgnoreCase(color) || "#FFCC33".equalsIgnoreCase(color)) return 0xFF8A5A00;
        if ("#FA320A".equalsIgnoreCase(color)) return 0xFFB42318;
        return android.graphics.Color.parseColor(color);
    }

    private void fetchRatingChipsForDisplay(String imdbId, String omdbApiKey, String cacheKey, String displayKey, ViewGroup container) {
        backgroundTasks.submit(() -> {
            try {
                JsonObject jsonObj = OmdbService.fetch(imdbId, omdbApiKey);
                if (jsonObj == null || jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) return;

                List<String[]> omdbChips = buildRatingChips(jsonObj);
                putCachedOmdbRatingChips(cacheKey, omdbChips);
                if (omdbChips.isEmpty()) return;

                activity.runOnUiThread(() -> {
                    if (headerRoot == null) return;
                    if (!(container.getTag() instanceof String) || !displayKey.equals(container.getTag())) return;
                    putRatingDisplayChips(displayKey, omdbChips);
                    renderRatingChips(container, getRatingDisplayChips(displayKey));
                });
            } catch (Exception e) {
                android.util.Log.w("TmdbHeaderView", "获取顶部评分失败: " + e.getMessage());
            }
        });
    }

    private void fetchDoubanRatingForDisplay(String title, String mediaType, int year, String displayKey, ViewGroup container, com.google.android.material.textview.MaterialTextView externalRatingView) {
        if (TextUtils.isEmpty(title)) return;
        String cacheKey = doubanRatingCacheKey(title, mediaType, year);
        PersonalRecommendationService.DoubanRating cached = getCachedDoubanRating(cacheKey);
        if (cached != null) {
            applyDoubanRating(displayKey, container, externalRatingView, cached);
            return;
        }
        backgroundTasks.submit(() -> {
            PersonalRecommendationService.DoubanRating rating = PersonalRecommendationService.DoubanRating.empty();
            try {
                rating = new PersonalRecommendationService().loadDoubanRating(title, mediaType, year);
            } catch (Throwable e) {
                android.util.Log.w("TmdbHeaderView", "获取豆瓣评分失败: " + e.getMessage());
            }
            if (Thread.currentThread().isInterrupted()) return;
            putCachedDoubanRating(cacheKey, rating);
            PersonalRecommendationService.DoubanRating finalRating = rating;
            activity.runOnUiThread(() -> {
                if (headerRoot == null) return;
                if (!(container.getTag() instanceof String) || !displayKey.equals(container.getTag())) return;
                applyDoubanRating(displayKey, container, externalRatingView, finalRating);
            });
        });
    }

    private void applyDoubanRating(String displayKey, ViewGroup container, com.google.android.material.textview.MaterialTextView externalRatingView, PersonalRecommendationService.DoubanRating rating) {
        if (rating == null || rating.isEmpty()) return;
        String value = formatRating(rating.getRating());
        if (TextUtils.isEmpty(value)) return;
        putRatingDisplayChips(displayKey, java.util.Collections.singletonList(new String[]{"豆瓣", value + "/10", "#00B51D"}));
        renderRatingChips(container, getRatingDisplayChips(displayKey));
        setExternalRating(externalRatingView, value);
    }

    private void renderRatingChips(ViewGroup container, List<String[]> chips) {
        if (container == null) return;
        container.removeAllViews();
        if (chips == null || chips.isEmpty()) {
            setRatingContainerVisible(container, false);
            return;
        }
        for (String[] chip : chips) {
            container.addView(createRatingChip(chip[0], chip[1], chip[2]));
        }
        setRatingContainerVisible(container, true);
    }

    private void setRatingContainerVisible(ViewGroup container, boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        container.setVisibility(visibility);
    }

    private void updateFusionRatingsVisibility() {
        if (headerRoot == null) return;
        ViewGroup container = headerRoot.findViewById(R.id.tmdbRatingsContainer);
        if (container == null) return;
        setRatingContainerVisible(container, container.getChildCount() > 0 && isCurrentRatingDisplay(container));
    }

    private boolean isCurrentRatingDisplay(ViewGroup container) {
        if (!(container.getTag() instanceof String) || boundAdapter == null) return false;
        TmdbItem item = boundAdapter.getTmdbItem();
        JsonObject detail = boundAdapter.getTmdbDetail();
        if (item == null || detail == null) return false;
        int tmdbId = item.getTmdbId();
        String mediaType = detail.has("first_air_date") ? "tv" : "movie";
        String title = item.getTitle();
        int year = parseYear(extractYear(detail));
        return TextUtils.equals((String) container.getTag(), ratingDisplayKey(title, mediaType, year, tmdbId));
    }

    private void setRatingDisplayChips(String displayKey, List<String[]> chips) {
        synchronized (ratingDisplayChips) {
            ratingDisplayChips.put(displayKey, chips == null ? new ArrayList<>() : new ArrayList<>(chips));
        }
    }

    private void putRatingDisplayChips(String displayKey, List<String[]> chips) {
        if (TextUtils.isEmpty(displayKey) || chips == null || chips.isEmpty()) return;
        synchronized (ratingDisplayChips) {
            List<String[]> current = ratingDisplayChips.get(displayKey);
            if (current == null) current = new ArrayList<>();
            for (String[] chip : chips) replaceRatingChip(current, chip);
            current.sort(java.util.Comparator.comparingInt(chip -> ratingChipOrder(chip[0])));
            ratingDisplayChips.put(displayKey, current);
        }
    }

    private List<String[]> getRatingDisplayChips(String displayKey) {
        synchronized (ratingDisplayChips) {
            List<String[]> chips = ratingDisplayChips.get(displayKey);
            return chips == null ? new ArrayList<>() : new ArrayList<>(chips);
        }
    }

    private void replaceRatingChip(List<String[]> chips, String[] chip) {
        if (chips == null || chip == null || chip.length < 3 || TextUtils.isEmpty(chip[0]) || TextUtils.isEmpty(chip[1])) return;
        for (int i = 0; i < chips.size(); i++) {
            if (chip[0].equals(chips.get(i)[0])) {
                chips.set(i, chip);
                return;
            }
        }
        chips.add(chip);
    }

    private int ratingChipOrder(String platform) {
        if ("TMDB".equals(platform)) return 0;
        if ("票房".equals(platform)) return 1;
        if ("豆瓣".equals(platform)) return 2;
        if ("IMDB".equals(platform)) return 3;
        if ("烂番茄".equals(platform)) return 4;
        if ("Metacritic".equals(platform) || "Metascore".equals(platform)) return 5;
        return 6;
    }

    /**
     * 构建票房 Chip（仅电影，有票房数据时）。返回 {platform, value, color} 或 null。
     */
    private String[] boxOfficeChip(com.google.gson.JsonObject detail, String mediaType) {
        if (detail == null) return null;
        if (!"movie".equalsIgnoreCase(mediaType)) return null;
        long revenue = 0;
        try {
            if (detail.has("revenue") && !detail.get("revenue").isJsonNull()) {
                revenue = detail.get("revenue").getAsLong();
            }
        } catch (Exception ignored) {
        }
        if (revenue <= 0) return null;
        return new String[]{"票房", formatBoxOffice(revenue), "#9C27B0"};
    }

    private String formatBoxOffice(long revenue) {
        if (revenue >= 1_000_000_000L) {
            return String.format(java.util.Locale.US, "$%.2fB", revenue / 1_000_000_000.0);
        } else if (revenue >= 1_000_000L) {
            return String.format(java.util.Locale.US, "$%.2fM", revenue / 1_000_000.0);
        } else {
            return String.format(java.util.Locale.US, "$%,d", revenue);
        }
    }

    private String ratingDisplayKey(String title, String mediaType, int year, int tmdbId) {
        return tmdbId + "|" + nullToEmpty(mediaType) + "|" + nullToEmpty(title) + "|" + year;
    }

    private String doubanRatingCacheKey(String title, String mediaType, int year) {
        return nullToEmpty(mediaType) + "|" + nullToEmpty(title).toLowerCase(Locale.ROOT) + "|" + year;
    }

    private PersonalRecommendationService.DoubanRating getCachedDoubanRating(String key) {
        synchronized (doubanRatingCache) {
            return doubanRatingCache.get(key);
        }
    }

    private void putCachedDoubanRating(String key, PersonalRecommendationService.DoubanRating rating) {
        if (TextUtils.isEmpty(key) || rating == null) return;
        synchronized (doubanRatingCache) {
            doubanRatingCache.put(key, rating);
        }
    }

    private String formatRating(double rating) {
        return rating <= 0 ? "" : String.format(Locale.US, "%.1f", rating);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 设置 OMDB 多来源评分区域。
     *
     * 仅当配置了 OMDB API Key、能拿到 IMDb ID 且 OMDB 返回有效数据时显示，
     * 否则隐藏整个区域。展示 IMDb 评分（含投票数）、烂番茄、Metacritic 等多来源评分。
     */
    private void setupOmdbRatings(TmdbUIAdapter adapter) {
        com.google.android.material.textview.MaterialTextView label = headerRoot.findViewById(R.id.tmdbOmdbRatingsLabel);
        View scroll = headerRoot.findViewById(R.id.tmdbOmdbRatingsScroll);
        ViewGroup container = headerRoot.findViewById(R.id.tmdbOmdbRatings);
        label.setVisibility(View.GONE);
        scroll.setVisibility(View.GONE);
        container.removeAllViews();
        container.setTag(null);

        JsonObject detail = adapter.getTmdbDetail();
        if (detail == null) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：detail 为空");
            return;
        }

        // 取 IMDb ID
        JsonObject externalIds = detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：无 external_ids/imdb_id，detail keys=" + detail.keySet());
            return;
        }
        String imdbId = externalIds.get("imdb_id").getAsString();
        if (TextUtils.isEmpty(imdbId)) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：imdb_id 为空");
            return;
        }

        // 必须配置 OMDB API Key
        com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(com.fongmi.android.tv.setting.Setting.getTmdbConfig());
        String omdbApiKey = tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(omdbApiKey)) {
            android.util.Log.d("TmdbHeaderView", "OMDB 评分跳过：未配置 OMDB API Key");
            return;
        }

        String cacheKey = omdbRatingCacheKey(imdbId, omdbApiKey);
        java.util.List<String[]> cachedChips = getCachedOmdbRatingChips(cacheKey);
        if (cachedChips != null) {
            renderSourceRatingChips(label, scroll, container, cachedChips);
            return;
        }

        container.setTag(cacheKey);
        android.util.Log.d("TmdbHeaderView", "OMDB 评分开始请求，imdbId=" + imdbId);
        fetchOmdbRatings(imdbId, omdbApiKey, cacheKey, label, scroll, container);
    }

    /**
     * 异步请求 OMDB 并渲染多来源评分。匹配不到数据时保持隐藏。
     */
    private void fetchOmdbRatings(String imdbId, String omdbApiKey, String cacheKey, com.google.android.material.textview.MaterialTextView label, View scroll, ViewGroup container) {
        backgroundTasks.submit(() -> {
            try {
                JsonObject jsonObj = OmdbService.fetch(imdbId, omdbApiKey);
                if (jsonObj == null) {
                    android.util.Log.w("TmdbHeaderView", "OMDB 请求失败");
                    return;
                }
                if (jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) {
                    android.util.Log.w("TmdbHeaderView", "OMDB 返回 Response=False");
                    return;
                }

                final java.util.List<String[]> chips = buildRatingChips(jsonObj);
                putCachedOmdbRatingChips(cacheKey, chips);
                android.util.Log.d("TmdbHeaderView", "OMDB 评分卡片数=" + chips.size());
                if (chips.isEmpty()) return;

                activity.runOnUiThread(() -> {
                    if (headerRoot == null) return;
                    if (!(container.getTag() instanceof String) || !cacheKey.equals(container.getTag())) return;
                    renderSourceRatingChips(label, scroll, container, chips);
                });
            } catch (Exception e) {
                android.util.Log.w("TmdbHeaderView", "获取 OMDB 评分失败: " + e.getMessage());
            }
        });
    }

    private void renderSourceRatingChips(com.google.android.material.textview.MaterialTextView label, View scroll, ViewGroup container, java.util.List<String[]> chips) {
        container.removeAllViews();
        if (chips == null || chips.isEmpty()) {
            label.setVisibility(View.GONE);
            scroll.setVisibility(View.GONE);
            return;
        }
        for (String[] chip : chips) {
            container.addView(createSourceRatingChip(chip[0], chip[1], chip[2]));
        }
        label.setVisibility(View.VISIBLE);
        scroll.setVisibility(View.VISIBLE);
    }

    private String omdbRatingCacheKey(String imdbId, String omdbApiKey) {
        return imdbId + "|" + omdbApiKey;
    }

    private java.util.List<String[]> getCachedOmdbRatingChips(String key) {
        synchronized (omdbRatingCache) {
            java.util.List<String[]> chips = omdbRatingCache.get(key);
            return chips == null ? null : new ArrayList<>(chips);
        }
    }

    private void putCachedOmdbRatingChips(String key, java.util.List<String[]> chips) {
        if (TextUtils.isEmpty(key) || chips == null || chips.isEmpty()) return;
        synchronized (omdbRatingCache) {
            omdbRatingCache.put(key, new ArrayList<>(chips));
        }
    }

    /**
     * 从 OMDB 响应组装评分卡片数据：每项为 {平台名, 评分文本, 颜色}。
     */
    private java.util.List<String[]> buildRatingChips(JsonObject jsonObj) {
        java.util.List<String[]> chips = new java.util.ArrayList<>();

        // IMDb 评分（附投票数）
        String imdbRating = optString(jsonObj, "imdbRating");
        if (!TextUtils.isEmpty(imdbRating)) {
            String votes = optString(jsonObj, "imdbVotes");
            String text = buildImdbRatingText(imdbRating, votes);
            chips.add(new String[]{"IMDB", text, "#F5C518"});
        }

        // Ratings 数组中的烂番茄、Metacritic 等来源
        if (jsonObj.has("Ratings") && jsonObj.get("Ratings").isJsonArray()) {
            for (com.google.gson.JsonElement el : jsonObj.getAsJsonArray("Ratings")) {
                if (!el.isJsonObject()) continue;
                JsonObject rating = el.getAsJsonObject();
                String source = optString(rating, "Source");
                String value = optString(rating, "Value");
                if (TextUtils.isEmpty(source) || TextUtils.isEmpty(value)) continue;
                if ("Internet Movie Database".equals(source)) continue; // 已由 imdbRating 展示
                if ("Rotten Tomatoes".equals(source)) {
                    chips.add(new String[]{"烂番茄", value, "#FA320A"});
                } else if ("Metacritic".equals(source)) {
                    chips.add(new String[]{"Metacritic", value, "#FFCC33"});
                } else {
                    chips.add(new String[]{source, value, "#21D07A"});
                }
            }
        }

        // Metascore（若 Ratings 中没有 Metacritic 则补充）
        String metascore = optString(jsonObj, "Metascore");
        if (!TextUtils.isEmpty(metascore) && !hasChip(chips, "Metacritic")) {
            chips.add(new String[]{"Metascore", metascore + "/100", "#FFCC33"});
        }

        return chips;
    }

    private boolean hasChip(java.util.List<String[]> chips, String platform) {
        for (String[] chip : chips) if (platform.equals(chip[0])) return true;
        return false;
    }

    /**
     * 读取 OMDB 字段，过滤空值与 "N/A"。
     */
    private String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) return "";
        String value = obj.get(key).getAsString();
        return (TextUtils.isEmpty(value) || "N/A".equals(value)) ? "" : value.trim();
    }

    private String buildImdbRatingText(String rating, String votes) {
        if (TextUtils.isEmpty(votes)) return rating;
        String fullText = rating + " (" + votes + ")";
        if (fullText.length() <= OMDB_FULL_RATING_TEXT_MAX_LENGTH) return fullText;
        String compactVotes = compactOmdbVoteCount(votes);
        return rating + " (" + (TextUtils.isEmpty(compactVotes) ? votes : compactVotes) + ")";
    }

    private String compactOmdbVoteCount(String votes) {
        if (TextUtils.isEmpty(votes)) return "";
        String digits = votes.replaceAll("[^0-9]", "");
        if (TextUtils.isEmpty(digits)) return "";
        try {
            long count = Long.parseLong(digits);
            if (count >= 1_000_000_000L) return formatOmdbCompactCount(count / 1_000_000_000d, "B");
            if (count >= 1_000_000L) return formatOmdbCompactCount(count / 1_000_000d, "M");
            if (count >= 1_000L) return formatOmdbCompactCount(count / 1_000d, "K");
        } catch (NumberFormatException ignored) {
            return "";
        }
        return votes;
    }

    private String formatOmdbCompactCount(double value, String suffix) {
        String text = String.format(Locale.US, "%.1f", value);
        if (text.endsWith(".0")) text = text.substring(0, text.length() - 2);
        return text + suffix;
    }

    /**
     * 创建多来源评分卡片：平台名在上，评分在下。
     */
    private View createSourceRatingChip(String platform, String value, String color) {
        androidx.appcompat.widget.LinearLayoutCompat chip = new androidx.appcompat.widget.LinearLayoutCompat(activity);
        chip.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setPadding(28, 14, 28, 14);

        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setCornerRadius(ResUtil.dp2px(6));
        chip.setBackground(background);

        com.google.android.material.textview.MaterialTextView platformView = new com.google.android.material.textview.MaterialTextView(activity);
        platformView.setText(platform);
        platformView.setTextSize(11);
        chip.addView(platformView);

        com.google.android.material.textview.MaterialTextView valueView = new com.google.android.material.textview.MaterialTextView(activity);
        valueView.setText(value);
        valueView.setTag(color);
        valueView.setTextSize(15);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams valueParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = 4;
        valueView.setLayoutParams(valueParams);
        chip.addView(valueView);

        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams params =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(12);
        chip.setLayoutParams(params);
        boolean fusion = Setting.isFusionDetailPage();
        boolean lightChrome = isLightDetailChrome() || (fusion && !isDarkDetailTheme());
        boolean lightSurface = !backdropSurfaceMode && lightChrome;
        styleSourceRatingChip(chip, fusion, lightChrome, lightSurface);
        return chip;
    }

    private void styleRatingChipContainer(ViewGroup container) {
        if (container == null) return;
        boolean fusion = Setting.isFusionDetailPage();
        boolean lightChrome = isLightDetailChrome() || (fusion && !isDarkDetailTheme());
        boolean lightSurface = !backdropSurfaceMode && lightChrome;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof TextView textView) styleRatingChip(textView, fusion, lightChrome, lightSurface);
            else if (child instanceof ViewGroup group) styleSourceRatingChip(group, fusion, lightChrome, lightSurface);
        }
    }

    private void styleRatingChip(TextView chip, boolean fusion, boolean lightChrome, boolean lightSurface) {
        String color = chip.getTag() instanceof String ? (String) chip.getTag() : "#21D07A";
        chip.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT : resolveRatingChipTextColor(color, lightSurface));
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(backdropSurfaceMode ? COLOR_BACKDROP_SURFACE_CONTROL_BG : fusion ? (lightSurface ? 0xDDEAF0F5 : 0xB314171C) : lightSurface ? 0xD9FFFFFF : 0x30FFFFFF);
        background.setCornerRadius(ResUtil.dp2px(6));
        background.setStroke(ResUtil.dp2px(1), backdropSurfaceMode ? COLOR_BACKDROP_SURFACE_CONTROL_STROKE : lightSurface ? 0x33424B57 : 0x33FFFFFF);
        chip.setBackground(background);
        if (backdropSurfaceMode || fusion && !lightChrome) applyFusionTextShadow(chip);
        else clearTextShadow(chip);
    }

    private void styleSourceRatingChip(ViewGroup chip, boolean fusion, boolean lightChrome, boolean lightSurface) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(backdropSurfaceMode ? COLOR_BACKDROP_SURFACE_CONTROL_BG : fusion ? (lightSurface ? 0xDDEAF0F5 : 0xB314171C) : lightSurface ? 0xD9FFFFFF : 0x26FFFFFF);
        background.setCornerRadius(ResUtil.dp2px(6));
        background.setStroke(ResUtil.dp2px(1), backdropSurfaceMode ? COLOR_BACKDROP_SURFACE_CONTROL_STROKE : lightSurface ? 0x33424B57 : 0x33FFFFFF);
        chip.setBackground(background);
        for (int i = 0; i < chip.getChildCount(); i++) {
            View child = chip.getChildAt(i);
            if (!(child instanceof TextView textView)) continue;
            if (textView.getTag() instanceof String color) {
                textView.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT : resolveRatingChipTextColor(color, lightSurface));
            } else {
                textView.setTextColor(backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT : lightSurface ? 0xCC12202D : 0xFF9AA7B4);
            }
            if (backdropSurfaceMode || fusion && !lightChrome) applyFusionTextShadow(textView);
            else clearTextShadow(textView);
        }
    }

    private void applyTheme() {
        if (headerRoot == null) return;
        int style = Setting.getTmdbDetailStyle();
        if (Setting.isFusionDetailPage()) {
            applyFusionTheme(style);
            return;
        }
        boolean cinema = style == Setting.DETAIL_STYLE_CINEMA;
        boolean light = cinema ? resolveLightTheme() : style == Setting.DETAIL_STYLE_PROFILE;
        boolean dark = style == Setting.DETAIL_STYLE_NATIVE || (cinema && !light);
        setCinemaRows(cinema, cinema, light);
        int background = cinema ? TmdbCinemaTheme.palette(light).background() : dark ? COLOR_NATIVE_BACKGROUND : COLOR_PROFILE_BACKGROUND;
        int primary = dark ? 0xFFFFFFFF : 0xFF15222B;
        int secondary = dark ? 0xFFC8D2DC : 0xFF40555E;
        int watermark = dark ? 0xFF6B7785 : 0xFF7E938A;
        headerRoot.setBackgroundColor(background);
        setTextColor(R.id.tmdbOverview, secondary);
        setTextColor(R.id.tmdbMeta, dark ? 0xFFC8D2DC : 0xFFE4F3EE);
        setTextColor(R.id.tmdbGenres, dark ? 0xFF9AA7B4 : 0xFFD5EAE3);
        setTextColor(R.id.tmdbCastLabel, primary);
        setTextColor(R.id.tmdbCrewLabel, primary);
        setTextColor(R.id.tmdbPhotosLabel, primary);
        setTextColor(R.id.tmdbPostersLabel, primary);
        setTextColor(R.id.tmdbExternalLinksLabel, primary);
        setTextColor(R.id.tmdbRecommendationsLabel, primary);
        setTextColor(R.id.tmdbPersonalTmdbRecommendationsLabel, primary);
        setTextColor(R.id.tmdbPersonalDoubanRecommendationsLabel, primary);
        setTextColor(R.id.tmdbPersonalAiRecommendationsLabel, primary);
        setTextColor(R.id.tmdbPersonalAiReason, secondary);
        clearTextShadow(R.id.tmdbPersonalAiReason);
        setTextColor(R.id.tmdbOmdbRatingsLabel, primary);
        TextView powered = findPoweredBy();
        if (powered != null) powered.setTextColor(watermark);
        clearBackdropTextShadows();
        clearFusionActionStyling();
        styleTmdbPlaybackControls(primary);
        styleRatingChipContainer(headerRoot.findViewById(R.id.tmdbRatingsContainer));
        styleRatingChipContainer(headerRoot.findViewById(R.id.tmdbOmdbRatings));
        tintActions(style);
    }

    private void applyFusionTheme(int style) {
        headerRoot.setBackgroundColor(0x00000000);
        setVisibility(R.id.tmdbNativeHero, View.GONE);
        setVisibility(R.id.tmdbFusionInfoCard, View.VISIBLE);
        setVisibility(R.id.tmdbOverview, View.GONE);
        updateFusionRatingsVisibility();
        moveActionsForFusion();

        boolean cinema = style == Setting.DETAIL_STYLE_CINEMA;
        boolean light = cinema ? resolveLightTheme() : !isDarkDetailTheme();
        boolean dark = !light;
        int panel = dark ? 0xC914171C : 0xAFFFFFFF;
        int line = dark ? 0x42FFFFFF : 0x33424B57;
        int primary = dark ? 0xFFFFFFFF : 0xFF12202D;
        int secondary = dark ? 0xD9FFFFFF : 0xCC12202D;
        int muted = dark ? 0x99FFFFFF : 0x9912202D;
        int body = dark ? 0xE6FFFFFF : 0xE612202D;

        boolean cinemaRows = cinema;
        if (castAdapter != null) {
            castAdapter.setCinema(cinemaRows);
            castAdapter.setLight(light);
        }
        if (crewAdapter != null) {
            crewAdapter.setCinema(cinemaRows);
            crewAdapter.setLight(light);
        }
        setRecommendationCinema(cinemaRows, light);
        styleFusionActions();

        MaterialCardView card = headerRoot.findViewById(R.id.tmdbFusionInfoCard);
        if (card != null) {
            card.setCardBackgroundColor(panel);
            card.setStrokeColor(line);
            card.setStrokeWidth(ResUtil.dp2px(1));
        }
        setTextColor(R.id.tmdbFusionTitle, primary);
        setTextColor(R.id.tmdbFusionSubtitle, secondary);
        setTextColor(R.id.tmdbFusionOverview, body);
        styleFusionBackdropLabels(dark);
        styleFusionAiReason(dark);
        styleFusionExternalLinks(dark);
        styleFusionMetaChips(dark);
        styleFusionPlaybackControls(panel, line, primary);
        styleFusionSource(primary, muted);
        styleFusionSpacing();
        TextView powered = findPoweredBy();
        if (powered != null) styleFusionBackdropText(powered, COLOR_FUSION_BACKDROP_WATERMARK);
        styleRatingChipContainer(headerRoot.findViewById(R.id.tmdbRatingsContainer));
        styleRatingChipContainer(headerRoot.findViewById(R.id.tmdbOmdbRatings));
        tintActions(dark ? Setting.DETAIL_STYLE_CINEMA : Setting.DETAIL_STYLE_PROFILE);
    }

    private void styleFusionSource(int titleColor, int mutedColor) {
        TextView source = headerRoot.findViewById(R.id.tmdbFusionSource);
        if (source == null) return;
        boolean inPlaybackControls = isDescendantOf(headerRoot.findViewById(R.id.tmdbPlaybackControls), source);
        source.setTextColor(inPlaybackControls ? titleColor : mutedColor);
        source.setTextSize(inPlaybackControls ? 16 : 12);
        source.setGravity(inPlaybackControls ? Gravity.CENTER_VERTICAL | Gravity.END : Gravity.END);
        source.setMaxWidth(ResUtil.dp2px(inPlaybackControls ? 260 : 220));
    }

    private boolean isDescendantOf(View ancestor, View child) {
        if (ancestor == null || child == null) return false;
        for (ViewParent parent = child.getParent(); parent instanceof View; parent = parent.getParent()) {
            if (parent == ancestor) return true;
        }
        return false;
    }

    private boolean isDarkDetailTheme() {
        return !resolveLightTheme();
    }

    private boolean isSystemNight() {
        return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private boolean resolveLightTheme() {
        return Setting.resolveTmdbDetailLightTheme(currentDetailThemeMode(), isSystemNight());
    }

    private int currentDetailThemeMode() {
        return detailThemeModeOverride == 1 || detailThemeModeOverride == 2 ? detailThemeModeOverride : Setting.getTmdbDetailTheme();
    }

    private void setCinemaRows(boolean peopleCinema, boolean recommendationCinema, boolean light) {
        if (castAdapter != null) {
            castAdapter.setCinema(peopleCinema);
            castAdapter.setLight(light);
        }
        if (crewAdapter != null) {
            crewAdapter.setCinema(peopleCinema);
            crewAdapter.setLight(light);
        }
        setRecommendationCinema(recommendationCinema, light);
    }

    private void setRecommendationCinema(boolean cinema, boolean light) {
        if (recommendationAdapter != null) {
            recommendationAdapter.setCinema(cinema);
            recommendationAdapter.setLight(light);
        }
        if (personalTmdbRecommendationAdapter != null) {
            personalTmdbRecommendationAdapter.setCinema(cinema);
            personalTmdbRecommendationAdapter.setLight(light);
        }
        if (personalDoubanRecommendationAdapter != null) {
            personalDoubanRecommendationAdapter.setCinema(cinema);
            personalDoubanRecommendationAdapter.setLight(light);
        }
        if (personalAiRecommendationAdapter != null) {
            personalAiRecommendationAdapter.setCinema(cinema);
            personalAiRecommendationAdapter.setLight(light);
        }
    }

    private void setVisibility(int id, int visibility) {
        View view = headerRoot.findViewById(id);
        if (view != null) view.setVisibility(visibility);
    }

    private void moveActionsForFusion() {
        View actions = headerRoot.findViewById(R.id.tmdbActionsScroll);
        if (actions == null || actions.getParent() != headerRoot) return;
        ViewGroup root = (ViewGroup) headerRoot;
        int targetIndex = 0;
        if (root.indexOfChild(actions) == targetIndex) return;
        root.removeView(actions);
        root.addView(actions, targetIndex);
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) actions.getLayoutParams();
        params.setMargins(0, ResUtil.dp2px(24), 0, ResUtil.dp2px(22));
        actions.setLayoutParams(params);
        actions.setPadding(ResUtil.dp2px(18), 0, ResUtil.dp2px(18), 0);
    }

    private void styleFusionActions() {
        ViewGroup row = headerRoot.findViewById(R.id.tmdbActions);
        MaterialButton rematch = headerRoot.findViewById(R.id.tmdbRematch);
        // 已在布局里按正确顺序排列（换源|收藏|TMDB|主题），不再动态重排
        if (row != null) {
            row.setBackground(null);
            row.setPadding(0, 0, 0, 0);
        }
        if (rematch != null) rematch.setText("重新匹配");
    }

    private void clearFusionActionStyling() {
        ViewGroup row = headerRoot.findViewById(R.id.tmdbActions);
        if (row == null) return;
        row.setBackground(null);
        row.setPadding(0, 0, 0, 0);
    }

    private void styleFusionPlaybackControls(int panel, int line, int textColor) {
        ViewGroup controls = headerRoot.findViewById(R.id.tmdbPlaybackControls);
        if (controls == null) return;
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(panel);
        background.setCornerRadius(ResUtil.dp2px(20));
        background.setStroke(ResUtil.dp2px(1), line);
        controls.setBackground(background);
        controls.setPadding(ResUtil.dp2px(16), ResUtil.dp2px(16), ResUtil.dp2px(16), ResUtil.dp2px(16));
        ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) controls.getLayoutParams();
        params.setMargins(ResUtil.dp2px(16), ResUtil.dp2px(22), ResUtil.dp2px(16), ResUtil.dp2px(20));
        controls.setLayoutParams(params);
        tintTree(controls, textColor);
    }

    private void styleTmdbPlaybackControls(int textColor) {
        ViewGroup controls = headerRoot.findViewById(R.id.tmdbPlaybackControls);
        if (controls == null) return;
        controls.setBackground(null);
        controls.setPadding(0, 0, 0, 0);
        if (controls.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params) {
            params.setMargins(0, ResUtil.dp2px(16), 0, 0);
            controls.setLayoutParams(params);
        }
        tintTree(controls, textColor);
    }

    private void styleFusionSpacing() {
        setMargins(R.id.tmdbFusionInfoCard, 16, 0, 16, 22);
        setTopMargin(R.id.tmdbCastLabel, 24);
        setTopMargin(R.id.tmdbCast, 12);
        setTopMargin(R.id.tmdbCrewLabel, 24);
        setTopMargin(R.id.tmdbCrew, 12);
        setTopMargin(R.id.tmdbPhotosLabel, 24);
        setTopMargin(R.id.tmdbPhotos, 12);
        setTopMargin(R.id.tmdbPostersLabel, 24);
        setTopMargin(R.id.tmdbPosters, 12);
        setTopMargin(R.id.tmdbRelatedVideosLabel, 24);
        setTopMargin(R.id.tmdbRelatedVideos, 12);
        setTopMargin(R.id.tmdbExternalLinksLabel, 24);
        setTopMargin(R.id.tmdbExternalLinks, 12);
        setTopMargin(R.id.tmdbRecommendationsLabel, 24);
        setTopMargin(R.id.tmdbRecommendations, 12);
        setTopMargin(R.id.tmdbPersonalTmdbRecommendationsLabel, 24);
        setTopMargin(R.id.tmdbPersonalTmdbRecommendations, 12);
        setTopMargin(R.id.tmdbPersonalDoubanRecommendationsLabel, 24);
        setTopMargin(R.id.tmdbPersonalDoubanRecommendations, 12);
        setTopMargin(R.id.tmdbPersonalAiRecommendationsLabel, 24);
        setTopMargin(R.id.tmdbPersonalAiRecommendations, 12);
        setTopMargin(R.id.tmdbPersonalAiReason, 8);
        setTopMargin(R.id.tmdbOmdbRatingsLabel, 24);
        setTopMargin(R.id.tmdbOmdbRatingsScroll, 12);
    }

    private void styleFusionAiReason(boolean dark) {
        TextView reason = headerRoot.findViewById(R.id.tmdbPersonalAiReason);
        if (reason == null) return;
        styleFusionBackdropText(reason, dark ? COLOR_FUSION_BACKDROP_TEXT_SECONDARY : 0xCC12202D, dark);
    }

    private void styleFusionMetaChips(boolean dark) {
        FlexboxLayout meta = headerRoot.findViewById(R.id.tmdbFusionMeta);
        if (meta == null) return;
        for (int i = 0; i < meta.getChildCount(); i++) {
            View child = meta.getChildAt(i);
            if (child instanceof TextView text) {
                text.setTextColor(dark ? 0xE6FFFFFF : 0xFF12202D);
            }
            android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
            background.setColor(dark ? 0x26FFFFFF : 0xDDEAF0F5);
            background.setCornerRadius(ResUtil.dp2px(12));
            background.setStroke(ResUtil.dp2px(1), dark ? 0x33FFFFFF : 0x33424B57);
            child.setBackground(background);
        }
    }

    private void styleFusionBackdropLabels(boolean dark) {
        for (int id : BACKDROP_SECTION_LABELS) styleFusionSectionLabel(id, dark);
    }

    private void styleFusionSectionLabel(int id, boolean dark) {
        TextView view = headerRoot.findViewById(id);
        if (view == null) return;
        view.setTextColor(dark ? COLOR_FUSION_BACKDROP_TEXT : 0xFF12202D);
        if (dark) applyFusionTextShadow(view);
        else clearTextShadow(view);
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(dark ? 0x6610141A : 0xDDEAF0F5);
        background.setCornerRadius(ResUtil.dp2px(12));
        background.setStroke(ResUtil.dp2px(1), dark ? 0x33FFFFFF : 0x55FFFFFF);
        view.setBackground(background);
        view.setPadding(ResUtil.dp2px(9), ResUtil.dp2px(3), ResUtil.dp2px(9), ResUtil.dp2px(3));
    }

    private void styleFusionExternalLinks(boolean dark) {
        ViewGroup container = headerRoot.findViewById(R.id.tmdbExternalLinks);
        if (container == null) return;
        boolean surfaceDark = true;
        styleFusionExternalLinksPanel(container, surfaceDark);
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            styleFusionExternalLinkRow(child, surfaceDark);
            styleFusionExternalLink(child, surfaceDark);
        }
    }

    private void styleFusionExternalLinksPanel(ViewGroup container, boolean dark) {
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(dark ? 0x7310141A : 0x99EAF0F5);
        background.setCornerRadius(ResUtil.dp2px(14));
        background.setStroke(ResUtil.dp2px(1), dark ? 0x33FFFFFF : 0x66FFFFFF);
        container.setBackground(background);
        container.setPadding(ResUtil.dp2px(6), ResUtil.dp2px(4), ResUtil.dp2px(6), ResUtil.dp2px(4));
    }

    private void styleFusionExternalLinkRow(View view, boolean dark) {
        if (!(view instanceof ViewGroup)) return;
        int horizontal = ResUtil.dp2px(10);
        int vertical = ResUtil.dp2px(9);
        view.setPadding(horizontal, vertical, horizontal, vertical);

        android.graphics.drawable.GradientDrawable content = new android.graphics.drawable.GradientDrawable();
        content.setColor(0x00000000);
        content.setCornerRadius(ResUtil.dp2px(10));
        view.setBackground(new android.graphics.drawable.RippleDrawable(
                ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x1A12202D),
                content,
                null
        ));
    }

    private void styleFusionExternalLink(View view, boolean dark) {
        if (view instanceof TextView textView) {
            styleFusionBackdropText(textView, isExternalRatingText(textView) ? (dark ? COLOR_FUSION_LINK_RATING : 0xFF1D8F5A) : (dark ? COLOR_FUSION_BACKDROP_TEXT : 0xFF12202D), dark);
        } else if (view instanceof ImageView imageView) {
            styleFusionLinkIcon(imageView, dark);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) styleFusionExternalLink(group.getChildAt(i), dark);
    }

    private void styleFusionLinkIcon(ImageView imageView) {
        styleFusionLinkIcon(imageView, true);
    }

    private void styleFusionLinkIcon(ImageView imageView, boolean dark) {
        imageView.setColorFilter(dark ? COLOR_FUSION_LINK_ICON : 0xCC12202D);
        int size = ResUtil.dp2px(22);
        ViewGroup.LayoutParams params = imageView.getLayoutParams();
        if (params != null) {
            params.width = size;
            params.height = size;
            imageView.setLayoutParams(params);
        }
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        background.setColor(dark ? COLOR_FUSION_LINK_ICON_BACKGROUND : 0x1A12202D);
        imageView.setBackground(background);
        int padding = ResUtil.dp2px(4);
        imageView.setPadding(padding, padding, padding, padding);
    }

    private boolean isExternalRatingText(TextView textView) {
        CharSequence text = textView.getText();
        return text != null && text.toString().startsWith("★");
    }

    private void styleFusionBackdropText(int id, int color) {
        TextView view = headerRoot.findViewById(id);
        styleFusionBackdropText(view, color);
    }

    private void styleFusionBackdropText(int id, int color, boolean shadow) {
        TextView view = headerRoot.findViewById(id);
        styleFusionBackdropText(view, color, shadow);
    }

    private void styleFusionBackdropText(TextView view, int color) {
        styleFusionBackdropText(view, color, true);
    }

    private void styleFusionBackdropText(TextView view, int color, boolean shadow) {
        if (view == null) return;
        view.setTextColor(color);
        if (shadow) applyFusionTextShadow(view);
        else if (Setting.isFusionDetailPage() && isLightDetailChrome()) applyFusionLightTextShadow(view);
        else clearTextShadow(view);
    }

    private void applyFusionTextShadow(TextView view) {
        if (view == null) return;
        view.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), COLOR_FUSION_TEXT_SHADOW);
    }

    private void applyFusionLightTextShadow(TextView view) {
        if (view == null) return;
        view.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), 0xCCFFFFFF);
    }

    private void clearBackdropTextShadows() {
        for (int id : BACKDROP_SECTION_LABELS) clearFusionSectionLabel(id);
        clearTextShadow(findPoweredBy());
        ViewGroup container = headerRoot.findViewById(R.id.tmdbExternalLinks);
        if (container != null) {
            clearFusionExternalLinksPanel(container);
            clearFusionExternalLinkStyling(container);
        }
    }

    private void clearFusionExternalLinksPanel(ViewGroup container) {
        container.setBackground(null);
        container.setPadding(0, 0, 0, 0);
    }

    private void clearFusionSectionLabel(int id) {
        TextView view = headerRoot.findViewById(id);
        if (view == null) return;
        clearTextShadow(view);
        view.setBackground(null);
        view.setPadding(0, 0, 0, 0);
    }

    private void clearFusionExternalLinkStyling(View view) {
        if (view instanceof TextView textView) clearTextShadow(textView);
        if (view instanceof ImageView imageView) {
            imageView.setColorFilter(0xFF9E9E9E);
            imageView.setBackground(null);
            imageView.setPadding(0, 0, 0, 0);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) clearFusionExternalLinkStyling(group.getChildAt(i));
    }

    private void clearTextShadow(int id) {
        TextView view = headerRoot.findViewById(id);
        clearTextShadow(view);
    }

    private void clearTextShadow(TextView view) {
        if (view != null) view.setShadowLayer(0, 0, 0, 0);
    }

    private boolean isLightDetailChrome() {
        int style = Setting.getTmdbDetailStyle();
        if (style == Setting.DETAIL_STYLE_CINEMA) return resolveLightTheme();
        return style == Setting.DETAIL_STYLE_PROFILE && !Setting.isFusionDetailPage();
    }

    private void setTopMargin(int id, int topDp) {
        View view = headerRoot.findViewById(id);
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)) return;
        params.topMargin = ResUtil.dp2px(topDp);
        view.setLayoutParams(params);
    }

    private void setMargins(int id, int startDp, int topDp, int endDp, int bottomDp) {
        View view = headerRoot.findViewById(id);
        if (view == null || !(view.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)) return;
        params.setMargins(ResUtil.dp2px(startDp), ResUtil.dp2px(topDp), ResUtil.dp2px(endDp), ResUtil.dp2px(bottomDp));
        view.setLayoutParams(params);
    }

    private void tintTree(View view, int color) {
        if (view instanceof RecyclerView) return;
        if (view instanceof TextView textView) {
            textView.setTextColor(color);
            clearTextShadow(textView);
        }
        if (view instanceof ImageView imageView) imageView.setColorFilter(color);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintTree(group.getChildAt(i), color);
    }

    private void setTextColor(int id, int color) {
        TextView view = headerRoot.findViewById(id);
        if (view != null) view.setTextColor(color);
    }

    private TextView findPoweredBy() {
        return findText(headerRoot, activity.getString(R.string.tmdb_powered_by));
    }

    private TextView findText(View view, String text) {
        if (view instanceof TextView textView && TextUtils.equals(textView.getText(), text)) return textView;
        if (!(view instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            TextView result = findText(group.getChildAt(i), text);
            if (result != null) return result;
        }
        return null;
    }

    private void tintActions(int style) {
        tintAction(R.id.tmdbChangeSource, style);
        tintAction(R.id.tmdbKeep, style);
        tintAction(R.id.tmdbRematch, style);
        tintAction(R.id.tmdbThemeToggle, style);
    }

    private void tintAction(int id, int style) {
        MaterialButton button = headerRoot.findViewById(id);
        if (button == null) return;
        button.setAlpha(1f);
        if (backdropSurfaceMode) {
            button.setTextColor(COLOR_FUSION_BACKDROP_TEXT);
            button.setStrokeColor(ColorStateList.valueOf(COLOR_BACKDROP_SURFACE_CONTROL_STROKE));
            button.setBackgroundTintList(ColorStateList.valueOf(COLOR_BACKDROP_SURFACE_CONTROL_BG));
            return;
        }
        if (Setting.isFusionDetailPage()) {
            boolean dark = style == Setting.DETAIL_STYLE_CINEMA;
            button.setTextColor(0xFFFFFFFF);
            button.setStrokeColor(ColorStateList.valueOf(dark ? 0x66FFFFFF : 0x55FFFFFF));
            button.setBackgroundTintList(ColorStateList.valueOf(dark ? 0xB314171C : 0xCC2B3036));
            return;
        }
        if (style == Setting.DETAIL_STYLE_NATIVE) {
            button.setTextColor(ContextCompat.getColor(activity, R.color.tmdb_action_button_text));
            button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.tmdb_action_button_stroke)));
            button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(activity, R.color.tmdb_action_button_bg)));
            return;
        }
        boolean cinema = style == Setting.DETAIL_STYLE_CINEMA;
        if (cinema && resolveLightTheme()) {
            TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(true);
            button.setTextColor(palette.primary());
            button.setStrokeColor(ColorStateList.valueOf(palette.lineStrong()));
            button.setBackgroundTintList(ColorStateList.valueOf(palette.card()));
            return;
        }
        button.setTextColor(cinema ? 0xFFE9F0F5 : 0xFF16372F);
        button.setStrokeColor(ColorStateList.valueOf(cinema ? 0x66FFFFFF : 0x663C7D6B));
        button.setBackgroundTintList(ColorStateList.valueOf(cinema ? 0x26FFFFFF : 0xD9FFFFFF));
    }

    /**
     * 设置外部链接（TMDB、IMDb、豆瓣、烂番茄等）
     */
    private void setupExternalLinks(TmdbUIAdapter adapter) {
        com.google.android.material.textview.MaterialTextView label = headerRoot.findViewById(R.id.tmdbExternalLinksLabel);
        ViewGroup container = headerRoot.findViewById(R.id.tmdbExternalLinks);

        JsonObject detail = adapter.getTmdbDetail();
        if (detail == null) {
            label.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
            return;
        }

        container.removeAllViews();
        container.setTag(null);
        int linkCount = 0;

        TmdbItem item = adapter.getTmdbItem();
        int tmdbId = item != null ? item.getTmdbId() : 0;
        String mediaType = detail.has("first_air_date") ? "tv" : "movie";

        // 获取 external_ids
        JsonObject externalIds = detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        String imdbId = externalIds != null && externalIds.has("imdb_id") && !externalIds.get("imdb_id").isJsonNull()
                ? externalIds.get("imdb_id").getAsString() : "";
        String westernSearchQuery = buildWesternSearchQuery(detail, item, mediaType);
        com.google.android.material.textview.MaterialTextView imdbRatingView = null;
        com.google.android.material.textview.MaterialTextView doubanRatingView = null;
        com.google.android.material.textview.MaterialTextView rottenRatingView = null;
        com.google.android.material.textview.MaterialTextView metacriticRatingView = null;

        // TMDB 链接（始终显示）
        if (tmdbId > 0) {
            String tmdbUrl = "https://www.themoviedb.org/" + mediaType + "/" + tmdbId;
            addExternalLink(container, "TMDB", tmdbUrl, adapter.getRatingText());
            linkCount++;
        }

        // IMDB 链接
        if (!TextUtils.isEmpty(imdbId)) {
            String imdbUrl = "https://www.imdb.com/title/" + imdbId;
            android.util.Log.d("TmdbHeaderView", "Adding IMDB link: " + imdbId);
            imdbRatingView = addExternalLink(container, "IMDB", imdbUrl, null);
            linkCount++;
        }

        // 豆瓣链接（通过标题搜索）
        if (item != null && !TextUtils.isEmpty(item.getTitle())) {
            String doubanUrl = "https://search.douban.com/movie/subject_search?search_text=" +
                    android.net.Uri.encode(item.getTitle());
            doubanRatingView = addExternalLink(container, "豆瓣", doubanUrl, null);
            linkCount++;
        }

        // 烂番茄（Rotten Tomatoes）- 优先使用英文标题搜索
        if (!TextUtils.isEmpty(westernSearchQuery)) {
            String rtUrl = "https://www.rottentomatoes.com/search?search=" +
                    android.net.Uri.encode(westernSearchQuery);
            rottenRatingView = addExternalLink(container, "烂番茄", rtUrl, null);
            linkCount++;
        }

        // Metacritic（优先使用英文标题搜索）
        if (!TextUtils.isEmpty(westernSearchQuery)) {
            String metacriticUrl = "https://www.metacritic.com/search/" +
                    android.net.Uri.encode(westernSearchQuery) + "/";
            metacriticRatingView = addExternalLink(container, "Metacritic", metacriticUrl, null);
            linkCount++;
        }

        if (!TextUtils.isEmpty(imdbId)) {
            com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(com.fongmi.android.tv.setting.Setting.getTmdbConfig());
            String omdbApiKey = tmdbConfig.getOmdbApiKey();
            if (!TextUtils.isEmpty(omdbApiKey)) {
                container.setTag(imdbId);
                fetchExternalLinkRatings(imdbId, omdbApiKey, container, imdbRatingView, rottenRatingView, metacriticRatingView);
            }
        }

        // 评分展示区域（在简介区域）
        ViewGroup ratingsContainer = headerRoot.findViewById(R.id.tmdbRatingsContainer);
        String doubanTitle = item == null ? detailTitle(detail, mediaType, false) : item.getTitle();
        addRatingsDisplay(ratingsContainer, adapter.getRatingText(), externalIds, tmdbId, mediaType, doubanTitle, parseYear(extractYear(detail)), doubanRatingView, detail);

        if (linkCount > 0) {
            label.setVisibility(View.VISIBLE);
            container.setVisibility(View.VISIBLE);
        } else {
            label.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
        }
    }

    private String buildWesternSearchQuery(JsonObject detail, TmdbItem item, String mediaType) {
        String title = preferredWesternSearchTitle(detail, item, mediaType);
        if (TextUtils.isEmpty(title)) return "";
        String year = extractYear(detail);
        return TextUtils.isEmpty(year) || title.contains(year) ? title : title + " " + year;
    }

    private String preferredWesternSearchTitle(JsonObject detail, TmdbItem item, String mediaType) {
        String english = englishTranslationTitle(detail, mediaType);
        if (!TextUtils.isEmpty(english)) return english;

        String original = detailTitle(detail, mediaType, true);
        if (hasLatinLetter(original)) return original;

        String localized = detailTitle(detail, mediaType, false);
        if (hasLatinLetter(localized)) return localized;

        String itemTitle = item == null ? "" : item.getTitle();
        if (hasLatinLetter(itemTitle)) return itemTitle;

        if (!TextUtils.isEmpty(original)) return original;
        if (!TextUtils.isEmpty(localized)) return localized;
        return itemTitle;
    }

    private String englishTranslationTitle(JsonObject detail, String mediaType) {
        JsonArray translations = jsonArray(detail, "translations", "translations");
        String fallback = "";
        for (JsonElement element : translations) {
            if (!element.isJsonObject()) continue;
            JsonObject translation = element.getAsJsonObject();
            if (!"en".equalsIgnoreCase(jsonString(translation, "iso_639_1"))) continue;
            JsonObject data = jsonObject(translation, "data");
            String title = "tv".equals(mediaType) ? jsonString(data, "name", "title") : jsonString(data, "title", "name");
            if (TextUtils.isEmpty(title)) continue;
            if ("US".equalsIgnoreCase(jsonString(translation, "iso_3166_1"))) return title;
            if (TextUtils.isEmpty(fallback)) fallback = title;
        }
        return fallback;
    }

    private String detailTitle(JsonObject detail, String mediaType, boolean original) {
        if (original) {
            return "tv".equals(mediaType)
                    ? jsonString(detail, "original_name", "original_title")
                    : jsonString(detail, "original_title", "original_name");
        }
        return "tv".equals(mediaType)
                ? jsonString(detail, "name", "title")
                : jsonString(detail, "title", "name");
    }

    private boolean hasLatinLetter(String text) {
        if (TextUtils.isEmpty(text)) return false;
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if ((value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z')) return true;
        }
        return false;
    }

    private String jsonString(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object == null || !object.has(key) || object.get(key).isJsonNull()) continue;
            String value = object.get(key).getAsString();
            if (!TextUtils.isEmpty(value)) return value.trim();
        }
        return "";
    }

    private JsonArray jsonArray(JsonObject object, String... keys) {
        com.google.gson.JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return new JsonArray();
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
            current = currentObject.get(key);
        }
        return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
    }

    private JsonObject jsonObject(JsonObject object, String... keys) {
        com.google.gson.JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return null;
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return null;
            current = currentObject.get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
    }

    private void fetchExternalLinkRatings(String imdbId, String omdbApiKey, ViewGroup container,
                                          com.google.android.material.textview.MaterialTextView imdbRatingView,
                                          com.google.android.material.textview.MaterialTextView rottenRatingView,
                                          com.google.android.material.textview.MaterialTextView metacriticRatingView) {
        backgroundTasks.submit(() -> {
            try {
                JsonObject jsonObj = OmdbService.fetch(imdbId, omdbApiKey);
                if (jsonObj == null || jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) return;

                String imdbRating = "";
                String rottenRating = "";
                String metacriticRating = "";
                for (String[] chip : buildRatingChips(jsonObj)) {
                    if ("IMDB".equals(chip[0])) imdbRating = chip[1];
                    else if ("烂番茄".equals(chip[0])) rottenRating = chip[1];
                    else if ("Metacritic".equals(chip[0]) || "Metascore".equals(chip[0])) metacriticRating = chip[1];
                }

                final String finalImdbRating = imdbRating;
                final String finalRottenRating = rottenRating;
                final String finalMetacriticRating = metacriticRating;
                activity.runOnUiThread(() -> {
                    if (headerRoot == null) return;
                    if (!(container.getTag() instanceof String) || !imdbId.equals(container.getTag())) return;
                    setExternalRating(imdbRatingView, finalImdbRating);
                    setExternalRating(rottenRatingView, finalRottenRating);
                    setExternalRating(metacriticRatingView, finalMetacriticRating);
                });
            } catch (Exception e) {
                android.util.Log.w("TmdbHeaderView", "获取外部链接评分失败: " + e.getMessage());
            }
        });
    }

    private void setExternalRating(com.google.android.material.textview.MaterialTextView view, String rating) {
        if (view == null) return;
        if (TextUtils.isEmpty(rating)) {
            view.setVisibility(View.GONE);
            return;
        }
        view.setText("★ " + rating);
        if (Setting.isFusionDetailPage()) styleFusionBackdropText(view, COLOR_FUSION_LINK_RATING);
        else view.setTextColor(0xFFFFC107);
        view.setVisibility(View.VISIBLE);
    }

    /**
     * 添加一个外部链接按钮，返回评分 TextView 以便后续更新
     */
    private com.google.android.material.textview.MaterialTextView addExternalLink(ViewGroup container, String name, String url, String rating) {
        // 创建链接项布局
        boolean fusion = Setting.isFusionDetailPage();
        boolean lightChrome = isLightDetailChrome() || (fusion && !isDarkDetailTheme());
        androidx.appcompat.widget.LinearLayoutCompat linkItem = new androidx.appcompat.widget.LinearLayoutCompat(activity);
        linkItem.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.HORIZONTAL);
        linkItem.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int horizontalPadding = fusion ? ResUtil.dp2px(12) : 0;
        int verticalPadding = fusion ? ResUtil.dp2px(9) : 12;
        linkItem.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);
        linkItem.setClickable(true);
        linkItem.setFocusable(true);
        android.graphics.drawable.GradientDrawable contentBackground = null;
        if (fusion) {
            contentBackground = new android.graphics.drawable.GradientDrawable();
            contentBackground.setColor(0x00000000);
            contentBackground.setCornerRadius(ResUtil.dp2px(10));
        } else if (lightChrome) {
            contentBackground = new android.graphics.drawable.GradientDrawable();
            contentBackground.setColor(0x40FFFFFF);
            contentBackground.setCornerRadius(ResUtil.dp2px(12));
        }
        android.graphics.drawable.Drawable background = new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(lightChrome ? 0x1A12202D : 0x33FFFFFF),
                contentBackground,
                null
        );
        linkItem.setBackground(background);

        // 平台名称
        com.google.android.material.textview.MaterialTextView nameView = new com.google.android.material.textview.MaterialTextView(activity);
        nameView.setText(name);
        nameView.setTextColor(lightChrome ? 0xFF12202D : 0xFFFFFFFF);
        nameView.setTextSize(14);
        if (Setting.isFusionDetailPage()) styleFusionBackdropText(nameView, lightChrome ? 0xFF12202D : COLOR_FUSION_BACKDROP_TEXT, !lightChrome);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams nameParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        linkItem.addView(nameView, nameParams);

        // 评分视图（始终创建，初始可能为空）
        com.google.android.material.textview.MaterialTextView ratingView = new com.google.android.material.textview.MaterialTextView(activity);
        ratingView.setTextColor(lightChrome ? 0xFF1D8F5A : Setting.isFusionDetailPage() ? COLOR_FUSION_LINK_RATING : 0xFFFFC107);
        ratingView.setTextSize(13);
        ratingView.setGravity(android.view.Gravity.END);
        if (Setting.isFusionDetailPage() && !lightChrome) applyFusionTextShadow(ratingView);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams ratingParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ratingParams.setMarginEnd(12);
        linkItem.addView(ratingView, ratingParams);

        // 如果已有评分，立即显示
        if (!TextUtils.isEmpty(rating)) {
            ratingView.setText("★ " + rating);
            ratingView.setVisibility(View.VISIBLE);
        } else {
            ratingView.setVisibility(View.GONE);
        }

        // 打开图标
        ImageView iconView = new ImageView(activity);
        iconView.setImageResource(R.drawable.ic_open);
        iconView.setColorFilter(lightChrome ? 0xCC12202D : 0xFF9E9E9E);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams iconParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(ResUtil.dp2px(22), ResUtil.dp2px(22));
        linkItem.addView(iconView, iconParams);
        if (Setting.isFusionDetailPage()) styleFusionLinkIcon(iconView, !lightChrome);

        // 点击事件
        linkItem.setOnClickListener(v -> {
            try {
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(url));
                activity.startActivity(intent);
            } catch (Exception e) {
                android.widget.Toast.makeText(activity, "无法打开链接", android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        container.addView(linkItem);
        return ratingView;  // 返回评分视图，以便后续更新
    }

    /**
     * 清理资源（Activity 销毁时调用）
     */
    public void onDestroy() {
        backgroundTasks.close();
        stopBackdropSlideshow();
        backdropHandler = null;
        backdropRunnable = null;
        boundAdapter = null;
        imagesLoadedListener = null;
        backdropChangeListener = null;
        actionListener = null;
    }

    /**
     * 隐藏原生 Hero 区域的独立 backdrop（用于原生增强模式的全屏背景）
     */
    public void hideNativeHeroBackdrop() {
        if (headerRoot == null) return;
        backdropSurfaceMode = true;

        // 将 TmdbHeaderView 的根视图背景设为透明，让下方的 contextWall 透出来
        headerRoot.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        View nativeHero = headerRoot.findViewById(R.id.tmdbNativeHero);
        if (nativeHero == null) return;

        // 不隐藏 backdrop 图片视图，而是设为完全透明，这样轮播回调仍然会触发
        // 但用户看不到它（因为 alpha=0 且有 contextWall 在底层）
        View backdropView = nativeHero.findViewById(R.id.tmdbBackdrop);
        if (backdropView != null) {
            backdropView.setAlpha(0f);  // 完全透明，但保持可见状态
            backdropView.setVisibility(View.VISIBLE);
            ViewGroup.LayoutParams params = backdropView.getLayoutParams();
            if (params != null) {
                params.height = 1;
                backdropView.setLayoutParams(params);
            }
        }

        View poster = nativeHero.findViewById(R.id.tmdbPoster);
        if (poster != null && poster.getParent() instanceof View posterCard) {
            posterCard.setVisibility(View.GONE);
        }

        // 隐藏渐变遮罩（backdrop 下方的第二个 View）
        if (nativeHero instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout heroFrame = (android.widget.FrameLayout) nativeHero;
            if (heroFrame.getChildCount() > 1) {
                View scrim = heroFrame.getChildAt(1);
                // 确保是遮罩层（不是 LinearLayout 容器）
                if (scrim instanceof View && !(scrim instanceof ViewGroup)) {
                    scrim.setVisibility(View.GONE);
                }
            }
        }

        // 调整 Hero 容器为透明背景，移除固定高度
        nativeHero.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        ViewGroup.LayoutParams heroParams = nativeHero.getLayoutParams();
        if (heroParams != null) {
            heroParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            nativeHero.setLayoutParams(heroParams);
        }

        // 调整内部海报+文字容器的上边距，因为不再需要为 backdrop 留空间
        if (nativeHero instanceof android.widget.FrameLayout) {
            android.widget.FrameLayout heroFrame = (android.widget.FrameLayout) nativeHero;
            for (int i = 0; i < heroFrame.getChildCount(); i++) {
                View child = heroFrame.getChildAt(i);
                if (child instanceof androidx.appcompat.widget.LinearLayoutCompat) {
                    ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) child.getLayoutParams();
                    params.topMargin = ResUtil.dp2px(16);  // 减小顶部边距
                    child.setLayoutParams(params);
                    child.setPadding(child.getPaddingLeft(), ResUtil.dp2px(16), child.getPaddingRight(), child.getPaddingBottom());
                    androidx.appcompat.widget.LinearLayoutCompat row = (androidx.appcompat.widget.LinearLayoutCompat) child;
                    if (row.getChildCount() > 1 && row.getChildAt(1).getLayoutParams() instanceof ViewGroup.MarginLayoutParams textParams) {
                        textParams.setMarginStart(0);
                        row.getChildAt(1).setLayoutParams(textParams);
                    }
                    break;
                }
            }
        }
        applyBackdropSurfaceTextColors();
    }

    private void applyBackdropSurfaceTextColors() {
        headerRoot.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        tintBackdropSurfaceTextTree(headerRoot);
        styleFusionBackdropLabels(true);
        styleFusionAiReason(true);
        styleFusionExternalLinks(true);
        styleBackdropSurfaceRatingChips();
        tintActions(Setting.DETAIL_STYLE_CINEMA);
        styleTmdbPlaybackControls(COLOR_FUSION_BACKDROP_TEXT);
        TextView powered = findPoweredBy();
        if (powered != null) styleFusionBackdropText(powered, COLOR_FUSION_BACKDROP_WATERMARK);
    }

    private void styleBackdropSurfaceRatingChips() {
        styleBackdropSurfaceRatingChipContainer(headerRoot.findViewById(R.id.tmdbRatingsContainer));
        styleBackdropSurfaceRatingChipContainer(headerRoot.findViewById(R.id.tmdbOmdbRatings));
    }

    private void styleBackdropSurfaceRatingChipContainer(ViewGroup container) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) styleBackdropSurfaceRatingChip(container.getChildAt(i));
    }

    private void styleBackdropSurfaceRatingChip(View chip) {
        if (chip == null) return;
        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(COLOR_BACKDROP_SURFACE_CONTROL_BG);
        background.setCornerRadius(ResUtil.dp2px(6));
        background.setStroke(ResUtil.dp2px(1), COLOR_BACKDROP_SURFACE_CONTROL_STROKE);
        chip.setBackground(background);
        tintBackdropSurfaceTextTree(chip);
    }

    private void tintBackdropSurfaceTextTree(View view) {
        if (view == null || view instanceof RecyclerView) return;
        if (view instanceof TextView textView && !(view instanceof MaterialButton)) {
            styleFusionBackdropText(textView, COLOR_FUSION_BACKDROP_TEXT);
            textView.setLinkTextColor(android.graphics.Color.WHITE);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintBackdropSurfaceTextTree(group.getChildAt(i));
    }
}
