package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.Intent;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.InlineEpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbEpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbRailAdapter;
import com.fongmi.android.tv.ui.controller.VodPlayerControlController;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TmdbPersonDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSearchDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.Clock;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class TmdbDetailActivity extends PlaybackActivity implements TrackDialog.Listener, Clock.Callback {

    private static final int FOCUS_STROKE = 0xFFFFD166;
    private static final int FOCUS_STROKE_DP = 3;
    private static final int CHIP_STROKE_DP = 1;

    private final TmdbService tmdbService = new TmdbService();
    private final List<TmdbPerson> castItems = new ArrayList<>();
    private final List<TmdbItem> relatedItems = new ArrayList<>();
    private final Map<Integer, TmdbEpisode> tmdbEpisodes = new HashMap<>();
    private final List<Integer> seasonNumbers = new ArrayList<>();
    private final Map<Integer, Integer> seasonEpisodeCounts = new HashMap<>();
    private final Map<Integer, List<TmdbEpisode>> tmdbSeasonEpisodes = new HashMap<>();

    private ActivityTmdbDetailBinding binding;
    private Vod vod;
    private History history;
    private TmdbConfig tmdbConfig;
    private TmdbBundle activeTmdbBundle;
    private TmdbItem initialTmdbItem;
    private TmdbItem matchedTmdbItem;
    private JsonObject matchedTmdbDetail;
    private Flag selectedFlag;
    private Episode selectedEpisode;
    private TmdbEpisodeAdapter episodeAdapter;
    private TmdbPersonAdapter castAdapter;
    private TmdbRailAdapter relatedAdapter;
    private boolean overviewExpanded;
    private boolean useParse;
    private boolean inlineStarted;
    private boolean autoPlayed;
    private boolean inlineFullscreen;
    private GestureDetector inlineGestureDetector;
    private Clock inlineClock;
    private VodPlayerControlController inlineControlController;
    private final Runnable inlineHideControls = this::hideInlineControls;
    private Result pendingInlineResult;
    private Result currentInlineResult;
    private ViewGroup playerParent;
    private ViewGroup.LayoutParams playerLayoutParams;
    private View inlineControlFocus;
    private boolean inlineWakeControlsByKey;
    private int selectedSeasonNumber = -1;
    private int playerIndex = -1;
    private int requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private int detailThemeMode;
    private boolean lightTheme;

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, false);
    }

    public static void startFusion(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null, true);
    }

    public static void startFusion(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, true);
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem, boolean fusion) {
        Intent intent = new Intent(activity, TmdbDetailActivity.class);
        intent.putExtra("fusion", fusion);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("mark", mark);
        putTmdbItem(intent, tmdbItem);
        activity.startActivity(intent);
    }

    private static void putTmdbItem(Intent intent, @Nullable TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0 || TextUtils.isEmpty(item.getMediaType())) return;
        intent.putExtra("tmdb_id", item.getTmdbId());
        intent.putExtra("tmdb_media_type", item.getMediaType());
        intent.putExtra("tmdb_title", item.getTitle());
        intent.putExtra("tmdb_subtitle", item.getSubtitle());
        intent.putExtra("tmdb_overview", item.getOverview());
        intent.putExtra("tmdb_poster", item.getPosterUrl());
        intent.putExtra("tmdb_backdrop", item.getBackdropUrl());
        intent.putExtra("tmdb_credit", item.getCredit());
    }

    @Override
    protected androidx.viewbinding.ViewBinding getBinding() {
        return binding = ActivityTmdbDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return binding.seek;
    }

    @Override
    protected PlayerView getExoView() {
        return binding.exo;
    }

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        initialTmdbItem = getIntentTmdbItem();
        detailThemeMode = Setting.getTmdbDetailTheme();
        initPage();
        loadContent(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resetDetailState();
        loadContent(null);
    }

    private void resetDetailState() {
        tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        initialTmdbItem = getIntentTmdbItem();
        vod = null;
        matchedTmdbItem = null;
        matchedTmdbDetail = null;
        history = null;
        selectedFlag = null;
        selectedEpisode = null;
        inlineStarted = false;
        autoPlayed = false;
        pendingInlineResult = null;
        currentInlineResult = null;
        activeTmdbBundle = null;
        useParse = false;
        if (service() != null) {
            player().stop();
            player().clear();
        }
        binding.loading.setVisibility(View.VISIBLE);
        binding.playerProgress.setVisibility(View.GONE);
        binding.playerError.setVisibility(View.GONE);
        binding.playerControls.setVisibility(View.GONE);
        binding.flagContainer.removeAllViews();
        binding.seasonContainer.removeAllViews();
        episodeAdapter.setItems(List.of(), Map.of(), null);
        castAdapter.setItems(new ArrayList<>());
        relatedAdapter.setItems(new ArrayList<>());
        binding.tmdbStatus.setVisibility(View.GONE);
        if (!TextUtils.isEmpty(getPicText())) {
            ImgUtil.load(getNameText(), getPicText(), binding.poster);
            ImgUtil.load(getNameText(), getPicText(), binding.backdropFill);
            ImgUtil.load(getNameText(), getPicText(), binding.backdrop, false);
        }
    }

    private void initPage() {
        binding.play.setOnClickListener(view -> onPlay());
        binding.keep.setOnClickListener(view -> onKeep());
        binding.keepTop.setOnClickListener(view -> onKeep());
        binding.keepFusion.setOnClickListener(view -> onKeep());
        binding.rematch.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.rematchTop.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.rematchFusion.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.changeSource.setOnClickListener(view -> changeSource());
        binding.changeSourceDetail.setOnClickListener(view -> changeSource());
        binding.themeMode.setOnClickListener(view -> cycleThemeMode());
        binding.overview.setOnClickListener(view -> toggleOverview());
        binding.overviewToggle.setOnClickListener(view -> toggleOverview());
        binding.headerTitle.setText(R.string.detail_page_title);
        binding.title.setText(getNameText());
        binding.subtitle.setText("");
        binding.sourceValue.setText(getString(R.string.detail_source_current, getKeyText()));
        binding.overviewToggle.setVisibility(View.GONE);
        binding.play.setText(R.string.detail_play_now);
        binding.keep.setText(R.string.keep);
        if (isFusionMode()) binding.headerTitle.setText(R.string.setting_detail_open_fusion);
        binding.playerPanel.setVisibility(isFusionMode() ? View.VISIBLE : View.GONE);
        binding.heroSpacer.setVisibility(isFusionMode() ? View.GONE : View.VISIBLE);
        binding.keepTop.setVisibility(View.GONE);
        binding.rematchTop.setVisibility(View.GONE);
        binding.fusionActions.setVisibility(isFusionMode() ? View.VISIBLE : View.GONE);
        binding.detailActions.setVisibility(isFusionMode() ? View.GONE : View.VISIBLE);
        initFusionPlayer();
        binding.episodeEmpty.setText(R.string.detail_source_episode_empty);
        if (!TextUtils.isEmpty(getPicText())) {
            ImgUtil.load(getNameText(), getPicText(), binding.poster);
            ImgUtil.load(getNameText(), getPicText(), binding.backdropFill);
            ImgUtil.load(getNameText(), getPicText(), binding.backdrop, false);
        }
        episodeAdapter = new TmdbEpisodeAdapter(episode -> {
            selectedEpisode = episode;
            episodeAdapter.setSelected(episode);
            updatePlayLabel();
            onPlay();
        });
        castAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        relatedAdapter = new TmdbRailAdapter(this::openRelatedItem);
        binding.episodeContainer.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.episodeContainer.setNestedScrollingEnabled(false);
        binding.episodeContainer.setAdapter(episodeAdapter);
        binding.castList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.castList.setNestedScrollingEnabled(false);
        binding.castList.setAdapter(castAdapter);
        binding.relatedList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.relatedList.setNestedScrollingEnabled(false);
        binding.relatedList.setAdapter(relatedAdapter);
        applyDetailTheme();
    }

    private void initFusionPlayer() {
        if (!isFusionMode()) return;
        inlineControlController = new VodPlayerControlController(new VodPlayerControlController.Host() {
            @Override
            public com.fongmi.android.tv.player.PlayerManager player() {
                return service() == null ? null : TmdbDetailActivity.this.player();
            }

            @Override
            public void showDanmakuDialog() {
                showInlineDanmaku();
            }

            @Override
            public void showPlayerInfoDialog() {
                showInlinePlayerInfo();
            }

            @Override
            public void onDanmakuStateChanged(boolean show) {
                binding.playerDanmaku.setSelected(show);
            }
        });
        inlineClock = Clock.create();
        inlineClock.setCallback(this);
        inlineClock.start();
        inlineGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!inlineStarted) onPlay();
                else if (!inlineFullscreen) enterInlineFullscreen();
                else toggleInlineControls();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                if (!inlineStarted) onPlay();
                else if (!inlineFullscreen) enterInlineFullscreen();
                else toggleInlineControls();
                return true;
            }
        });
        binding.playerPanel.setOnTouchListener(this::onInlineTouch);
        binding.playerPanel.setOnKeyListener(this::onInlinePanelKey);
        binding.playerPanel.setOnFocusChangeListener((view, focused) -> updatePlayerPanelFocus());
        setupInlineControlFocus();
        setupInlineFocusNavigation();
        binding.playerToggle.setOnClickListener(view -> toggleInlinePlayback());
        binding.playerPrev.setOnClickListener(view -> playAdjacentEpisode(-1));
        binding.playerNext.setOnClickListener(view -> playAdjacentEpisode(1));
        binding.playerQuality.setOnClickListener(view -> cycleInlineQuality());
        binding.playerParse.setOnClickListener(view -> cycleInlineParse());
        binding.playerSpeed.setOnClickListener(view -> changeInlineSpeed());
        binding.playerSpeed.setOnLongClickListener(view -> resetInlineSpeed());
        binding.playerScale.setOnClickListener(view -> cycleInlineScale());
        binding.playerRefresh.setOnClickListener(view -> refreshInlinePlayback());
        binding.playerDecode.setOnClickListener(view -> toggleInlineDecode());
        binding.playerTextTrack.setOnClickListener(this::showInlineTrack);
        binding.playerTextTrack.setOnLongClickListener(view -> showInlineSubtitle());
        binding.playerAudioTrack.setOnClickListener(this::showInlineTrack);
        binding.playerVideoTrack.setOnClickListener(this::showInlineTrack);
        binding.playerDanmaku.setOnClickListener(view -> showInlineDanmaku());
        binding.playerExternal.setOnClickListener(view -> openInlineExternal());
        binding.playerExternal.setOnLongClickListener(view -> inlineControlController.showPlayerInfo());
        binding.playerEpisodes.setOnClickListener(view -> showInlineEpisodes());
        binding.playerFullscreen.setOnClickListener(view -> toggleInlineFullscreen());
        binding.playerCast.setOnClickListener(view -> onInlineCast());
        binding.playerInfo.setOnClickListener(view -> onInlineInfo());
        binding.playerControls.setOnTouchListener(this::onInlineTouch);
        hideInlineControls();
        updateInlineButtons(false);
        focusInlinePlayerPanel();
    }

    private void setupInlineFocusNavigation() {
        View timeBar = binding.seek.findViewById(R.id.timeBar);
        if (timeBar != null) {
            timeBar.setNextFocusUpId(R.id.playerToggle);
            timeBar.setNextFocusRightId(R.id.playerFullscreen);
        }
        binding.playerPrev.setNextFocusDownId(R.id.timeBar);
        binding.playerToggle.setNextFocusDownId(R.id.timeBar);
        binding.playerNext.setNextFocusDownId(R.id.timeBar);
        binding.playerFullscreen.setNextFocusLeftId(R.id.timeBar);
        binding.playerFullscreen.setNextFocusUpId(R.id.playerToggle);
        binding.playerExternal.setNextFocusUpId(R.id.playerFullscreen);
        binding.playerDecode.setNextFocusUpId(R.id.playerFullscreen);
        binding.playerEpisodes.setNextFocusUpId(R.id.playerFullscreen);
    }

    private void setupInlineControlFocus() {
        setupInlineControl(binding.playerCast);
        setupInlineControl(binding.playerInfo);
        setupInlineControl(binding.playerFullscreen);
        setupInlineControl(binding.playerPrev);
        setupInlineControl(binding.playerToggle);
        setupInlineControl(binding.playerNext);
        setupInlineControl(binding.playerExternal);
        setupInlineControl(binding.playerDecode);
        setupInlineControl(binding.playerSpeed);
        setupInlineControl(binding.playerScale);
        setupInlineControl(binding.playerRefresh);
        setupInlineControl(binding.playerQuality);
        setupInlineControl(binding.playerParse);
        setupInlineControl(binding.playerTextTrack);
        setupInlineControl(binding.playerAudioTrack);
        setupInlineControl(binding.playerVideoTrack);
        setupInlineControl(binding.playerDanmaku);
        setupInlineControl(binding.playerEpisodes);
    }

    private void setupInlineControl(View view) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnFocusChangeListener((control, focused) -> {
            if (focused) inlineControlFocus = control;
            updatePlayerPanelFocus();
        });
    }

    private boolean hasFocusedChild(View view) {
        if (view == null) return false;
        if (view.hasFocus()) return true;
        if (!(view instanceof ViewGroup group)) return false;
        for (int i = 0; i < group.getChildCount(); i++) if (hasFocusedChild(group.getChildAt(i))) return true;
        return false;
    }

    private boolean onInlineTouch(View view, MotionEvent event) {
        if (inlineGestureDetector != null) inlineGestureDetector.onTouchEvent(event);
        return true;
    }

    private boolean onInlinePanelKey(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isEnterKey(event)) return false;
        if (KeyUtil.isActionUp(event)) onInlinePanelConfirm();
        return true;
    }

    private void onInlinePanelConfirm() {
        if (!isFusionMode()) return;
        if (!inlineStarted) {
            onPlay();
        } else if (!inlineFullscreen) {
            enterInlineFullscreen();
        } else if (isInlineControlsVisible()) {
            hideInlineControls();
        } else {
            showInlineControls(true, false);
        }
    }

    private void cycleThemeMode() {
        detailThemeMode = (detailThemeMode + 1) % 3;
        Setting.putTmdbDetailTheme(detailThemeMode);
        applyDetailTheme();
        if (vod != null) {
            bindMeta();
            renderFlagSelection();
            renderSeasonSelection();
            renderEpisodes();
        }
    }

    private void applyDetailTheme() {
        lightTheme = resolveLightTheme();
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        binding.root.setBackgroundColor(colors.background);
        binding.hero.setBackgroundColor(colors.background);
        binding.backdropFill.setAlpha(lightTheme ? 0.35f : 0.5f);
        binding.backdrop.setAlpha(lightTheme ? 0.92f : 1f);
        binding.backdropShade.setBackgroundColor(colors.backdropShade);
        setCard(binding.contentPanel, colors.panel, colors.line);
        setPlayerCard(colors);
        setCard(binding.tmdbPanel, colors.panel, colors.line);
        tintTextTree(binding.getRoot(), colors);
        setButton(binding.keep, colors.control, colors.line, colors.primary);
        setButton(binding.keepTop, colors.control, colors.line, colors.primary);
        setButton(binding.keepFusion, colors.control, colors.line, colors.primary);
        setButton(binding.rematch, colors.control, colors.line, colors.primary);
        setButton(binding.rematchTop, colors.control, colors.line, colors.primary);
        setButton(binding.rematchFusion, colors.control, colors.line, colors.primary);
        setButton(binding.changeSource, colors.control, colors.line, colors.primary);
        setButton(binding.changeSourceDetail, colors.control, colors.line, colors.primary);
        setButton(binding.themeMode, colors.control, colors.line, colors.primary);
        setButton(binding.play, colors.play, colors.play, 0xFFFFFFFF);
        binding.headerTitle.setTextColor(colors.primary);
        binding.title.setTextColor(colors.primary);
        binding.subtitle.setTextColor(colors.secondary);
        binding.sourceValue.setTextColor(colors.muted);
        binding.overview.setTextColor(colors.body);
        binding.overviewToggle.setTextColor(colors.accent);
        binding.episodeEmpty.setTextColor(colors.secondary);
        binding.tmdbStatus.setTextColor(colors.secondary);
        binding.themeMode.setText(themeModeLabel());
        if (isFusionMode()) {
            binding.playerError.setTextColor(0xFFFFFFFF);
            binding.playerTitle.setTextColor(0xFFFFFFFF);
            tintInlineControl(binding.playerControls);
        }
        if (episodeAdapter != null) {
            episodeAdapter.setLight(lightTheme);
            episodeAdapter.setActiveStrokeColor(colors.accent);
        }
    }

    private void tintInlineControl(View view) {
        if (view instanceof TextView textView) textView.setTextColor(0xFFFFFFFF);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintInlineControl(group.getChildAt(i));
    }

    private boolean resolveLightTheme() {
        if (detailThemeMode == 1) return false;
        if (detailThemeMode == 2) return true;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    private int themeModeLabel() {
        if (detailThemeMode == 1) return R.string.detail_theme_dark;
        if (detailThemeMode == 2) return R.string.detail_theme_light;
        return R.string.detail_theme_auto;
    }

    private void setCard(MaterialCardView card, int background, int stroke) {
        card.setCardBackgroundColor(background);
        card.setStrokeColor(stroke);
    }

    private void setPlayerCard(ThemeColors colors) {
        if (!isFusionMode()) return;
        binding.playerPanel.setCardBackgroundColor(0xFF000000);
        binding.playerPanel.setRadius(inlineFullscreen ? 0 : ResUtil.dp2px(20));
        updatePlayerPanelFocus(colors);
    }

    private void updatePlayerPanelFocus() {
        updatePlayerPanelFocus(lightTheme ? ThemeColors.light() : ThemeColors.dark());
    }

    private void updatePlayerPanelFocus(ThemeColors colors) {
        if (!isFusionMode()) return;
        if (inlineFullscreen) {
            binding.playerPanel.setStrokeColor(0x00000000);
            binding.playerPanel.setStrokeWidth(0);
            return;
        }
        boolean focused = binding.playerPanel.hasFocus() && !hasFocusedChild(binding.playerControls);
        binding.playerPanel.setStrokeColor(focused ? FOCUS_STROKE : colors.line);
        binding.playerPanel.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
    }

    private void focusInlinePlayerPanel() {
        if (!isFusionMode()) return;
        binding.playerPanel.post(() -> {
            if (!isFinishing() && binding.playerPanel.getVisibility() == View.VISIBLE && !inlineFullscreen) binding.playerPanel.requestFocus();
        });
    }

    private void setButton(MaterialButton button, int background, int stroke, int text) {
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(text);
        button.setOnFocusChangeListener(null);
        applyButtonFocus(button, stroke, button.hasFocus());
        button.setOnFocusChangeListener((view, focused) -> applyButtonFocus(button, stroke, focused));
    }

    private void applyButtonFocus(MaterialButton button, int stroke, boolean focused) {
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : stroke));
    }

    private void tintTextTree(View view, ThemeColors colors) {
        if (view instanceof RecyclerView) return;
        if (view instanceof TextView textView && !(view instanceof MaterialButton)) {
            textView.setTextColor(colors.primary);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintTextTree(group.getChildAt(i), colors);
    }

    private void loadContent(@Nullable TmdbBundle reusableBundle) {
        Task.execute(() -> {
            Vod loadedVod = null;
            String error = null;
            TmdbBundle tmdbBundle = reusableBundle;
            List<TmdbItem> searchItems = new ArrayList<>();
            try {
                Result result = SiteApi.detailContent(getKeyText(), getIdText());
                if (result != null && !result.getList().isEmpty()) {
                    loadedVod = result.getVod();
                    if (loadedVod != null && loadedVod.getSite() == null) {
                        loadedVod.setSite(VodConfig.get().getSite(getKeyText()));
                    }
                }
            } catch (Throwable e) {
                error = e.getMessage();
            }

            if (tmdbBundle == null && tmdbConfig.isReady()) {
                try {
                    if (initialTmdbItem != null) {
                        tmdbBundle = loadTmdbBundle(initialTmdbItem);
                    } else {
                        TmdbItem match = getCachedTmdbMatch();
                        if (match != null) {
                            try {
                                tmdbBundle = loadTmdbBundle(match);
                            } catch (Throwable ignored) {
                                match = null;
                                tmdbBundle = null;
                            }
                        }
                        if (match == null) {
                            searchItems = tmdbService.search(getNameText(), tmdbConfig);
                            match = chooseTmdbMatch(searchItems, getNameText());
                        }
                        if (match != null && tmdbBundle == null) tmdbBundle = loadTmdbBundle(match);
                    }
                } catch (Throwable ignored) {
                }
            }

            Vod finalVod = loadedVod;
            String finalError = error;
            TmdbBundle finalBundle = tmdbBundle;
            List<TmdbItem> finalSearchItems = searchItems;
            runOnAliveUi(() -> applyLoaded(finalVod, finalBundle, finalSearchItems, finalError));
        });
    }

    private boolean canTouchUi() {
        return !isFinishing() && !isDestroyed();
    }

    private void runOnAliveUi(Runnable runnable) {
        runOnUiThread(() -> {
            if (!canTouchUi()) return;
            runnable.run();
        });
    }

    private void applyLoaded(Vod loadedVod, TmdbBundle bundle, List<TmdbItem> searchItems, String error) {
        binding.loading.setVisibility(View.GONE);
        if (loadedVod == null) {
            if (!TextUtils.isEmpty(error)) Notify.show(error);
            VideoActivity.startDirect(this, getKeyText(), getIdText(), getNameText(), getPicText(), getMarkText());
            finish();
            return;
        }
        vod = loadedVod;
        applyTmdbBundle(bundle);
        if (bundle != null && initialTmdbItem != null) saveTmdbMatch(bundle.item());
        enrichVod();
        initHistory();
        bindPage();
        focusInlinePlayerPanel();
        maybeAutoPlayInline();
        if (tmdbConfig.isReady() && bundle == null && initialTmdbItem == null) showTmdbMatchDialog(searchItems);
    }

    private TmdbBundle loadTmdbBundle(TmdbItem item) throws Exception {
        JsonObject detail = tmdbService.detail(item, tmdbConfig);
        List<TmdbPerson> cast = tmdbService.cast(detail, tmdbConfig);
        List<TmdbItem> related = tmdbService.recommendations(detail, tmdbConfig);
        List<Integer> seasons = new ArrayList<>();
        Map<Integer, Integer> seasonCounts = new HashMap<>();
        Map<Integer, List<TmdbEpisode>> seasonEpisodes = new HashMap<>();
        if ("tv".equalsIgnoreCase(item.getMediaType())) {
            seasonCounts = seasonEpisodeCounts(detail);
            seasons.addAll(seasonCounts.keySet());
            int seasonNumber = firstSeasonNumber(detail);
            if (seasonNumber >= 0) {
                seasonEpisodes.put(seasonNumber, tmdbService.episodes(tmdbService.season(item, seasonNumber, tmdbConfig), tmdbConfig));
            }
        }
        return new TmdbBundle(item, detail, cast, related, seasons, seasonCounts, seasonEpisodes);
    }

    private void applyTmdbBundle(TmdbBundle bundle) {
        activeTmdbBundle = bundle;
        matchedTmdbItem = bundle == null ? null : bundle.item();
        matchedTmdbDetail = bundle == null ? null : bundle.detail();
        castItems.clear();
        if (bundle != null) castItems.addAll(bundle.cast());
        relatedItems.clear();
        if (bundle != null) relatedItems.addAll(bundle.related());
        tmdbEpisodes.clear();
        seasonNumbers.clear();
        if (bundle != null) seasonNumbers.addAll(bundle.seasons());
        seasonEpisodeCounts.clear();
        if (bundle != null) seasonEpisodeCounts.putAll(bundle.seasonCounts());
        tmdbSeasonEpisodes.clear();
        if (bundle != null) tmdbSeasonEpisodes.putAll(bundle.seasonEpisodes());
    }

    private void showTmdbMatchDialog(List<TmdbItem> items) {
        showTmdbMatchDialog(items, true);
    }

    private void showTmdbMatchDialog(List<TmdbItem> items, boolean skippable) {
        if (!canTouchUi()) return;
        TmdbSearchDialog.create(this)
                .title(getString(R.string.detail_tmdb_match_title))
                .query(getTmdbSearchQuery())
                .items(items)
                .listener(this::applyManualTmdb)
                .searchListener(this::searchTmdb)
                .skipListener(skippable ? this::onPlay : null)
                .show();
    }

    private void showManualTmdbMatchDialog() {
        if (!tmdbConfig.isReady()) {
            Notify.show(getString(R.string.detail_tmdb_need_key));
            return;
        }
        binding.loading.setVisibility(View.VISIBLE);
        Task.execute(() -> {
            try {
                List<TmdbItem> items = tmdbService.search(getTmdbSearchQuery(), tmdbConfig);
                runOnAliveUi(() -> {
                    binding.loading.setVisibility(View.GONE);
                    showTmdbMatchDialog(items, false);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private TmdbItem getCachedTmdbMatch() {
        return Setting.getTmdbMatchCache().find(getKeyText(), getIdText());
    }

    private void saveTmdbMatch(TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        cache.put(getKeyText(), getIdText(), item);
        Setting.putTmdbMatchCache(cache);
    }

    private String getTmdbSearchQuery() {
        if (matchedTmdbItem != null && !TextUtils.isEmpty(matchedTmdbItem.getTitle())) return matchedTmdbItem.getTitle();
        if (vod != null && !TextUtils.isEmpty(vod.getName())) return vod.getName();
        return getNameText();
    }

    private void searchTmdb(String keyword, TmdbSearchDialog dialog) {
        dialog.loading();
        Task.execute(() -> {
            try {
                List<TmdbItem> items = tmdbService.search(keyword, tmdbConfig);
                runOnAliveUi(() -> dialog.updateItems(items));
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    dialog.updateItems(new ArrayList<>());
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void applyManualTmdb(TmdbItem item) {
        binding.loading.setVisibility(View.VISIBLE);
        Task.execute(() -> {
            try {
                TmdbBundle bundle = loadTmdbBundle(item);
                runOnAliveUi(() -> {
                    binding.loading.setVisibility(View.GONE);
                    applyTmdbBundle(bundle);
                    saveTmdbMatch(item);
                    enrichVod();
                    bindPage();
                    Notify.show(R.string.detail_tmdb_match_saved);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void enrichVod() {
        if (matchedTmdbItem != null) {
            if (!TextUtils.isEmpty(matchedTmdbItem.getTitle())) vod.setName(matchedTmdbItem.getTitle());
            if (TextUtils.isEmpty(vod.getContent())) vod.setContent(matchedTmdbItem.getOverview());
            if (TextUtils.isEmpty(vod.getPic())) vod.setPic(matchedTmdbItem.getPosterUrl());
        }
        if (matchedTmdbDetail == null) return;
        String overview = string(matchedTmdbDetail, "overview");
        if (!TextUtils.isEmpty(overview)) vod.setContent(overview);
        if ((TextUtils.isEmpty(vod.getPic()) || vod.getPic().startsWith("data:")) && matchedTmdbItem != null) {
            vod.setPic(matchedTmdbItem.getPosterUrl());
        }
        if (TextUtils.isEmpty(vod.getDirector())) {
            String director = firstCrew("Director");
            if (!TextUtils.isEmpty(director)) vod.setDirector(director);
        }
    }

    private void bindPage() {
        binding.contentPanel.setVisibility(View.VISIBLE);
        bindBackdrop();
        bindHeader();
        bindMeta();
        bindOverview();
        bindSource();
        bindFlags();
        bindTmdbSection();
        updateKeepState();
    }

    private void bindBackdrop() {
        String backdrop = matchedTmdbItem != null ? matchedTmdbItem.getBackdropUrl() : "";
        if (TextUtils.isEmpty(backdrop) && matchedTmdbDetail != null) {
            backdrop = tmdbService.image(tmdbConfig.getBackdropBase(), string(matchedTmdbDetail, "backdrop_path"));
        }
        if (TextUtils.isEmpty(backdrop)) backdrop = vod.getPic();
        binding.hero.setVisibility(TextUtils.isEmpty(backdrop) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(backdrop)) {
            ImgUtil.load(vod.getName(), backdrop, binding.backdropFill);
            ImgUtil.load(vod.getName(), backdrop, binding.backdrop, false);
        }
        ImgUtil.load(vod.getName(), vod.getPic(), binding.poster);
    }

    private void bindHeader() {
        overviewExpanded = false;
        binding.title.setText(vod.getName());
        binding.subtitle.setText(buildSubtitle());
        binding.sourceValue.setText(getString(R.string.detail_source_current, getSiteName()));
    }

    private void bindMeta() {
        binding.metaContainer.removeAllViews();
        addMetaChip(getMediaTypeLabel());
        addMetaChip(vod.getYear());
        addMetaChip(firstGenre());
        addMetaChip(firstCountry());
        addMetaChip(firstCrew("Director"));
        String rating = ratingLabel();
        if (!TextUtils.isEmpty(rating)) addMetaChip(rating);
    }

    private void bindOverview() {
        String overview = TextUtils.isEmpty(vod.getContent()) ? "" : vod.getContent().trim();
        binding.overview.setText(overview);
        binding.overview.setVisibility(TextUtils.isEmpty(overview) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(overview)) {
            binding.overviewToggle.setVisibility(View.GONE);
            return;
        }
        binding.overview.setMaxLines(Integer.MAX_VALUE);
        binding.overview.setEllipsize(null);
        binding.overview.post(() -> {
            if (binding.overview.getLineCount() > 5) {
                binding.overviewToggle.setVisibility(View.VISIBLE);
                applyOverviewState();
            } else {
                binding.overviewToggle.setVisibility(View.GONE);
            }
        });
    }

    private void toggleOverview() {
        if (binding.overviewToggle.getVisibility() != View.VISIBLE) return;
        overviewExpanded = !overviewExpanded;
        applyOverviewState();
    }

    private void applyOverviewState() {
        binding.overview.setMaxLines(overviewExpanded ? Integer.MAX_VALUE : 5);
        binding.overview.setEllipsize(overviewExpanded ? null : TextUtils.TruncateAt.END);
        binding.overviewToggle.setText(overviewExpanded ? R.string.detail_collapse : R.string.detail_expand);
    }

    private void bindSource() {
        boolean hasFlags = vod != null && vod.getFlags() != null && !vod.getFlags().isEmpty();
        binding.flagTitle.setVisibility(hasFlags ? View.VISIBLE : View.GONE);
        binding.flagScroll.setVisibility(hasFlags ? View.VISIBLE : View.GONE);
    }

    private void bindFlags() {
        binding.flagContainer.removeAllViews();
        List<Flag> flags = vod.getFlags();
        boolean hasFlags = flags != null && !flags.isEmpty();
        if (!hasFlags) {
            binding.episodeTitle.setVisibility(View.GONE);
            binding.episodeContainer.setVisibility(View.GONE);
            binding.seasonScroll.setVisibility(View.GONE);
            binding.episodeEmpty.setVisibility(View.VISIBLE);
            updatePlayLabel();
            return;
        }
        Flag currentFlag = findInitialFlag(flags);
        selectedFlag = currentFlag;
        selectedEpisode = null;
        selectedSeasonNumber = -1;
        for (Flag flag : flags) {
            MaterialButton button = createChipButton(flag.getShow());
            setChipState(button, flag.equals(currentFlag));
            button.setOnClickListener(view -> {
                selectedFlag = flag;
                selectedEpisode = null;
                selectedSeasonNumber = -1;
                renderFlagSelection();
                renderEpisodes();
                if (isFusionMode()) onPlay();
            });
            binding.flagContainer.addView(button);
        }
        renderFlagSelection();
        renderEpisodes();
    }

    private void renderFlagSelection() {
        List<Flag> flags = vod.getFlags();
        for (int i = 0; i < binding.flagContainer.getChildCount() && i < flags.size(); i++) {
            View child = binding.flagContainer.getChildAt(i);
            if (child instanceof MaterialButton button) {
                setChipState(button, flags.get(i).equals(selectedFlag));
            }
        }
    }

    private void renderEpisodes() {
        List<Episode> episodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        boolean hasEpisodes = episodes != null && !episodes.isEmpty();
        binding.episodeTitle.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        binding.episodeContainer.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        binding.episodeEmpty.setVisibility(hasEpisodes ? View.GONE : View.VISIBLE);
        if (!hasEpisodes) {
            binding.seasonScroll.setVisibility(View.GONE);
            episodeAdapter.setItems(List.of(), Map.of(), null);
            updatePlayLabel();
            return;
        }
        if (selectedEpisode == null) {
            String remarks = history != null ? history.getVodRemarks() : "";
            selectedEpisode = selectedFlag.find(remarks, getMarkText().isEmpty());
            if (selectedEpisode == null) selectedEpisode = episodes.get(0);
        }
        if (selectedSeasonNumber < 0) selectedSeasonNumber = seasonForEpisode(selectedEpisode, episodes);
        renderSeasonSelection();
        List<Episode> visibleEpisodes = visibleEpisodes(episodes);
        bindSeasonEpisodes();
        episodeAdapter.setItems(visibleEpisodes, tmdbEpisodes, selectedEpisode);
        scrollEpisodeToSelected();
        updatePlayLabel();
    }

    private void scrollEpisodeToSelected() {
        if (selectedEpisode == null || episodeAdapter == null) return;
        binding.episodeContainer.post(() -> {
            if (selectedEpisode == null) return;
            int position = episodeAdapter.getPosition(selectedEpisode);
            if (position < 0) return;
            RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
            if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
                linearLayoutManager.scrollToPositionWithOffset(position, ResUtil.dp2px(12));
            } else {
                binding.episodeContainer.scrollToPosition(position);
            }
        });
    }

    private void renderSeasonSelection() {
        boolean hasSeasons = seasonNumbers.size() > 1;
        binding.seasonScroll.setVisibility(hasSeasons ? View.VISIBLE : View.GONE);
        binding.seasonContainer.removeAllViews();
        if (!hasSeasons) return;
        for (Integer season : seasonNumbers) {
            MaterialButton button = createChipButton(getString(R.string.detail_season_format, season));
            setChipState(button, season == selectedSeasonNumber);
            button.setOnClickListener(view -> {
                selectedSeasonNumber = season;
                List<Episode> visibleEpisodes = visibleEpisodes(selectedFlag.getEpisodes());
                selectedEpisode = visibleEpisodes.isEmpty() ? null : visibleEpisodes.get(0);
                renderSeasonSelection();
                fetchSeasonIfNeeded(season);
                renderEpisodes();
            });
            binding.seasonContainer.addView(button);
        }
    }

    private void bindSeasonEpisodes() {
        tmdbEpisodes.clear();
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(selectedSeasonNumber);
        if (episodes != null) {
            for (TmdbEpisode episode : episodes) tmdbEpisodes.put(episode.getNumber(), episode);
        }
        fetchSeasonIfNeeded(selectedSeasonNumber);
    }

    private void fetchSeasonIfNeeded(int seasonNumber) {
        if (seasonNumber < 0 || tmdbSeasonEpisodes.containsKey(seasonNumber) || matchedTmdbItem == null || !"tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) || !tmdbConfig.isReady()) return;
        tmdbSeasonEpisodes.put(seasonNumber, List.of());
        Task.execute(() -> {
            try {
                List<TmdbEpisode> episodes = tmdbService.episodes(tmdbService.season(matchedTmdbItem, seasonNumber, tmdbConfig), tmdbConfig);
                runOnAliveUi(() -> {
                    tmdbSeasonEpisodes.put(seasonNumber, episodes);
                    if (seasonNumber == selectedSeasonNumber) renderEpisodes();
                });
            } catch (Throwable ignored) {
            }
        });
    }

    private List<Episode> visibleEpisodes(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return List.of();
        if (seasonNumbers.size() <= 1 || selectedSeasonNumber < 0) return episodes;
        int start = 0;
        for (int i = 0; i < seasonNumbers.size(); i++) {
            Integer season = seasonNumbers.get(i);
            int count = Math.max(0, seasonEpisodeCounts.getOrDefault(season, 0));
            if (season == selectedSeasonNumber) {
                if (count <= 0) return episodes;
                int end = i == seasonNumbers.size() - 1 ? episodes.size() : Math.min(episodes.size(), start + count);
                return start < end ? episodes.subList(start, end) : List.of();
            }
            start += count;
            if (start >= episodes.size()) break;
        }
        return episodes;
    }

    private int seasonForEpisode(Episode episode, List<Episode> episodes) {
        if (seasonNumbers.isEmpty()) return -1;
        if (seasonNumbers.size() == 1) return seasonNumbers.get(0);
        int index = episode == null ? -1 : episodes.indexOf(episode);
        if (index < 0) return firstSeasonNumber(matchedTmdbDetail);
        int start = 0;
        for (int i = 0; i < seasonNumbers.size(); i++) {
            Integer season = seasonNumbers.get(i);
            int count = Math.max(0, seasonEpisodeCounts.getOrDefault(season, 0));
            if (count <= 0) continue;
            int end = i == seasonNumbers.size() - 1 ? episodes.size() : start + count;
            if (index >= start && index < end) return season;
            start += count;
        }
        return seasonNumbers.get(seasonNumbers.size() - 1);
    }

    private void bindTmdbSection() {
        boolean hasCast = !castItems.isEmpty();
        boolean hasRelated = !relatedItems.isEmpty();
        binding.tmdbSection.setVisibility(hasCast || hasRelated || matchedTmdbDetail != null || tmdbConfig.isReady() ? View.VISIBLE : View.GONE);

        binding.castTitle.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        binding.castList.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        castAdapter.setItems(castItems);

        binding.relatedTitle.setVisibility(hasRelated ? View.VISIBLE : View.GONE);
        binding.relatedList.setVisibility(hasRelated ? View.VISIBLE : View.GONE);
        relatedAdapter.setItems(relatedItems);

        if (!tmdbConfig.isReady()) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_need_key);
        } else if (!hasCast && !hasRelated) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_empty);
        } else {
            binding.tmdbStatus.setVisibility(View.GONE);
        }
    }

    private void initHistory() {
        history = History.find(getHistoryKey());
        if (history == null) {
            history = new History();
            history.setKey(getHistoryKey());
            history.setCid(VodConfig.getCid());
            history.setVodName(vod.getName());
            history.findEpisode(vod.getFlags());
        }
        if (!TextUtils.isEmpty(getMarkText())) history.setVodRemarks(getMarkText());
        updatePlayLabel();
    }

    private void updatePlayLabel() {
        if (selectedEpisode != null) {
            boolean canResume = history != null && selectedEpisode.getName().equals(history.getVodRemarks()) && history.getPosition() > 0;
            binding.play.setText(canResume ? getString(R.string.detail_play_resume, selectedEpisode.getName()) : getString(R.string.detail_play_now));
            return;
        }
        boolean hasResume = history != null && history.getPosition() > 0 && !TextUtils.isEmpty(history.getVodRemarks());
        binding.play.setText(hasResume ? getString(R.string.detail_play_resume, history.getVodRemarks()) : getString(R.string.detail_play_now));
    }

    private void onPlay() {
        if (vod == null) return;
        persistSelection();
        if (isFusionMode()) playInline();
        else {
            Vod tmdbVod = playbackTmdbVod();
            TmdbPlaybackActivity.start(this, getKeyText(), getIdText(), vod.getName(), vod.getPic(), selectedEpisode != null ? selectedEpisode.getName() : getMarkText(), selectedTmdbEpisodeTitles(), playbackTmdbItem(), tmdbVod);
        }
    }

    private ArrayList<String> selectedTmdbEpisodeTitles() {
        Map<Integer, String> titles = new LinkedHashMap<>();
        for (Map.Entry<Integer, TmdbEpisode> entry : tmdbEpisodes.entrySet()) {
            if (!TextUtils.isEmpty(entry.getValue().getTitle())) titles.put(entry.getKey(), entry.getValue().getTitle());
        }
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(selectedSeasonNumber);
        if (episodes != null) {
            for (TmdbEpisode episode : episodes) {
                if (!TextUtils.isEmpty(episode.getTitle())) titles.put(episode.getNumber(), episode.getTitle());
            }
        }
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : titles.entrySet()) result.add(entry.getKey() + "\t" + entry.getValue());
        return result;
    }

    private boolean isFusionMode() {
        return getIntent().getBooleanExtra("fusion", false);
    }

    private void maybeAutoPlayInline() {
        if (!isFusionMode() || autoPlayed) return;
        autoPlayed = true;
        binding.playerPanel.post(this::onPlay);
    }

    private void playInline() {
        if (selectedFlag == null || selectedEpisode == null) return;
        saveInlineHistory();
        binding.playerError.setVisibility(View.GONE);
        binding.playerProgress.setVisibility(View.VISIBLE);
        showInlineControls(true);
        updateInlineTitle();
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(getKeyText(), selectedFlag.getFlag(), selectedEpisode.getUrl());
                runOnUiThread(() -> startInlinePlayer(result));
            } catch (Throwable e) {
                runOnUiThread(() -> showInlineError(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_url) : e.getMessage()));
            }
        });
    }

    private void startInlinePlayer(Result result) {
        currentInlineResult = result;
        useParse = result.shouldUseParse();
        if (result.hasPosition() && history != null) history.setPosition(result.getPosition());
        if (result.hasDesc()) {
            vod.setContent(result.getDesc());
            bindOverview();
        }
        if (service() == null || controller() == null) {
            pendingInlineResult = result;
            return;
        }
        inlineStarted = true;
        pendingInlineResult = null;
        hideInlineControls();
        updateInlineTitle();
        updateInlineButtons(false);
        player().stop();
        player().clear();
        Site site = getCurrentSite();
        startPlayer(getHistoryKey(), result, useParse, site == null ? 0 : site.getTimeout(), buildMetadata());
        binding.playerPanel.requestFocus();
    }

    private void showInlineError(String text) {
        binding.playerProgress.setVisibility(View.GONE);
        binding.playerError.setText(text);
        binding.playerError.setVisibility(View.VISIBLE);
    }

    private void toggleInlinePlayback() {
        if (!isFusionMode()) return;
        if (controller() == null || service() == null || player().isEmpty()) {
            onPlay();
            return;
        }
        if (player().isPlaying()) controller().pause();
        else controller().play();
        setInlineHideCallback();
    }

    private void toggleInlineControls() {
        if (!isFusionMode() || !inlineStarted) return;
        if (binding.playerControls.getVisibility() == View.VISIBLE) hideInlineControls();
        else showInlineControls(true, false);
    }

    private void showInlineControls(boolean show) {
        showInlineControls(show, true);
    }

    private void showInlineControls(boolean show, boolean focus) {
        if (!isFusionMode() || !inlineStarted) return;
        if (!show) {
            hideInlineControls();
            return;
        }
        binding.playerControls.setVisibility(View.VISIBLE);
        if (focus) focusInlineDefaultControl();
        setInlineHideCallback();
    }

    private void hideInlineControls() {
        if (binding == null) return;
        boolean hadControlFocus = hasFocusedChild(binding.playerControls);
        binding.playerControls.setVisibility(View.GONE);
        App.removeCallbacks(inlineHideControls);
        if (hadControlFocus) binding.playerPanel.requestFocus();
    }

    private void setInlineHideCallback() {
        App.removeCallbacks(inlineHideControls);
        App.post(inlineHideControls, Constant.INTERVAL_HIDE);
    }

    private void focusInlineDefaultControl() {
        if (hasFocusedChild(binding.playerControls)) return;
        binding.playerControls.post(() -> {
            if (isInlineControlsVisible() && !hasFocusedChild(binding.playerControls)) getInlineControlFocus().requestFocus();
        });
    }

    private void focusInlinePlaybackControl() {
        binding.playerToggle.post(() -> {
            if (isInlineControlsVisible()) binding.playerToggle.requestFocus();
        });
    }

    private View getInlineControlFocus() {
        if (inlineControlFocus != null && inlineControlFocus.getVisibility() == View.VISIBLE && inlineControlFocus.isEnabled()) return inlineControlFocus;
        return binding.playerToggle;
    }

    private void rememberInlineControlFocus() {
        View focus = getCurrentFocus();
        if (focus != null && isDescendant(binding.playerControls, focus)) inlineControlFocus = focus;
    }

    private boolean isDescendant(ViewGroup parent, View child) {
        if (parent == null || child == null) return false;
        if (parent == child) return true;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View view = parent.getChildAt(i);
            if (view == child) return true;
            if (view instanceof ViewGroup group && isDescendant(group, child)) return true;
        }
        return false;
    }

    private boolean isInlineControlsVisible() {
        return binding != null && binding.playerControls.getVisibility() == View.VISIBLE;
    }

    private void updateInlineButtons(boolean playing) {
        if (!isFusionMode() || inlineControlController == null) return;
        binding.playerToggle.setText(playing ? R.string.pause : R.string.play);
        binding.playerSpeed.setText(service() == null || player().isEmpty() ? getString(R.string.play_speed) : player().getSpeedText());
        binding.playerDecode.setText(service() == null ? getString(R.string.play_decode) : player().getDecodeText());
        binding.playerScale.setText(scaleLabel());
        binding.playerQuality.setText(qualityLabel());
        binding.playerParse.setText(parseLabel());
        boolean hasPlayer = service() != null && !player().isEmpty();
        inlineControlController.updateSize(binding.playerSize, inlineFullscreen);
        boolean hasPrev = hasAdjacentEpisode(-1);
        boolean hasNext = hasAdjacentEpisode(1);
        setButtonEnabled(binding.playerPrev, hasPrev);
        setButtonEnabled(binding.playerNext, hasNext);
        setButtonEnabled(binding.playerQuality, currentInlineResult != null && currentInlineResult.getUrl().isMulti());
        setButtonEnabled(binding.playerParse, useParse && !VodConfig.get().getParses().isEmpty());
        setButtonEnabled(binding.playerSpeed, hasPlayer);
        setButtonEnabled(binding.playerScale, hasPlayer);
        setButtonEnabled(binding.playerRefresh, hasPlayer);
        setButtonEnabled(binding.playerDecode, hasPlayer);
        setButtonEnabled(binding.playerTextTrack, hasPlayer);
        setButtonEnabled(binding.playerAudioTrack, hasPlayer);
        setButtonEnabled(binding.playerVideoTrack, hasPlayer);
        setButtonEnabled(binding.playerDanmaku, hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(binding.playerExternal, hasPlayer);
        setButtonEnabled(binding.playerEpisodes, selectedFlag != null && selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty());
        setButtonEnabled(binding.playerCast, hasPlayer && hasInlineCast());
        setButtonEnabled(binding.playerInfo, hasPlayer && hasInlineInfo());
        setButtonEnabled(binding.playerFullscreen, hasPlayer);
        binding.playerCast.setVisibility(hasInlineCast() ? View.VISIBLE : View.GONE);
        binding.playerInfo.setVisibility(hasInlineInfo() ? View.VISIBLE : View.GONE);
        binding.playerActionRow.setVisibility(inlineFullscreen ? View.VISIBLE : View.GONE);
        binding.playerQuality.setVisibility(currentInlineResult != null && currentInlineResult.getUrl().isMulti() ? View.VISIBLE : View.GONE);
        binding.playerParse.setVisibility(useParse && !VodConfig.get().getParses().isEmpty() ? View.VISIBLE : View.GONE);
        setInlineFullscreenIcon();
    }

    private void setButtonEnabled(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.36f);
    }

    private void setInlineFullscreenIcon() {
        binding.playerFullscreen.setImageResource(inlineFullscreen ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
    }

    protected boolean hasInlineCast() {
        return false;
    }

    protected boolean hasInlineInfo() {
        return false;
    }

    protected void onInlineCast() {
    }

    protected void onInlineInfo() {
    }

    protected boolean showInlinePlayerInfo() {
        return false;
    }

    protected CharSequence getInlinePlayerTitle() {
        return binding.playerTitle.getText();
    }

    protected History getInlineHistory() {
        return history;
    }

    private boolean hasAdjacentEpisode(int direction) {
        if (selectedFlag == null || selectedEpisode == null || selectedFlag.getEpisodes() == null) return false;
        int index = selectedFlag.getEpisodes().indexOf(selectedEpisode);
        int next = index + direction;
        return index >= 0 && next >= 0 && next < selectedFlag.getEpisodes().size();
    }

    private void updateInlineTitle() {
        if (!isFusionMode()) return;
        String title = vod != null ? vod.getName() : getNameText();
        String episode = selectedEpisode != null ? selectedEpisode.getName() : "";
        binding.playerTitle.setText(TextUtils.isEmpty(episode) || episode.equals(title) ? title : title + "  " + episode);
    }

    private String qualityLabel() {
        if (currentInlineResult == null || !currentInlineResult.getUrl().isMulti()) return getString(R.string.detail_quality);
        int position = currentInlineResult.getUrl().getPosition();
        String name = currentInlineResult.getUrl().n(position);
        return TextUtils.isEmpty(name) ? getString(R.string.detail_quality) + " " + (position + 1) : name;
    }

    private String parseLabel() {
        String name = VodConfig.get().getParse().getName();
        return TextUtils.isEmpty(name) ? getString(R.string.parse) : name;
    }

    private int getInlineScale() {
        return history != null && history.getScale() != -1 ? history.getScale() : PlayerSetting.getScale();
    }

    private String scaleLabel() {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        int scale = Math.max(0, Math.min(getInlineScale(), array.length - 1));
        return array.length == 0 ? getString(R.string.play_scale) : array[scale];
    }

    private void setInlineScale(int scale) {
        if (history != null) history.setScale(scale);
        binding.exo.setResizeMode(scale);
        binding.playerScale.setText(scaleLabel());
    }

    private void cycleInlineQuality() {
        if (currentInlineResult == null || !currentInlineResult.getUrl().isMulti()) return;
        saveInlineHistory();
        int count = currentInlineResult.getUrl().getValues().size();
        currentInlineResult.getUrl().set((currentInlineResult.getUrl().getPosition() + 1) % count);
        startInlinePlayer(currentInlineResult);
    }

    private void cycleInlineParse() {
        List<Parse> parses = VodConfig.get().getParses();
        if (!useParse || parses.isEmpty()) return;
        Parse current = VodConfig.get().getParse();
        int index = parses.indexOf(current);
        Parse next = parses.get(index < 0 || index == parses.size() - 1 ? 0 : index + 1);
        VodConfig.get().setParse(next);
        Notify.show(getString(R.string.play_switch_parse, next.getName()));
        playInline();
    }

    private void changeInlineSpeed() {
        if (service() == null || player().isEmpty()) return;
        binding.playerSpeed.setText(player().addSpeed());
        if (history != null) history.setSpeed(player().getSpeed());
    }

    private boolean resetInlineSpeed() {
        if (service() == null || player().isEmpty()) return false;
        binding.playerSpeed.setText(player().toggleSpeed());
        if (history != null) history.setSpeed(player().getSpeed());
        return true;
    }

    private void refreshInlinePlayback() {
        if (selectedFlag == null || selectedEpisode == null) return;
        if (history != null) history.setPosition(C.TIME_UNSET);
        playInline();
    }

    private void cycleInlineScale() {
        if (service() == null || player().isEmpty()) return;
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (array.length == 0) return;
        int scale = getInlineScale();
        setInlineScale(scale >= array.length - 1 ? 0 : scale + 1);
    }

    private void toggleInlineDecode() {
        if (service() == null || player().isEmpty()) return;
        player().toggleDecode();
        binding.playerDecode.setText(player().getDecodeText());
    }

    private void showInlineTrack(View view) {
        if (service() == null || player().isEmpty()) return;
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
    }

    private boolean showInlineSubtitle() {
        if (service() == null || player().isEmpty() || !player().haveTrack(C.TRACK_TYPE_TEXT)) return false;
        onSubtitleClick();
        return true;
    }

    private void showInlineDanmaku() {
        if (service() == null || player().isEmpty()) return;
        DanmakuDialog.create().player(player()).show(this);
    }

    private void openInlineExternal() {
        if (service() == null || player().isEmpty()) return;
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), binding.playerTitle.getText());
        setRedirect(true);
    }

    private void showInlineEpisodes() {
        if (selectedFlag == null || selectedFlag.getEpisodes() == null || selectedFlag.getEpisodes().isEmpty()) return;
        FrameLayout content = new FrameLayout(this);
        content.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
        RecyclerView recycler = new RecyclerView(this);
        recycler.setClipToPadding(false);
        recycler.setLayoutManager(new GridLayoutManager(this, inlineEpisodeSpanCount()));
        int height = Math.min(ResUtil.dp2px(520), (int) (ResUtil.getScreenHeight(this) * 0.68f));
        content.addView(recycler, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));

        AlertDialog[] holder = new AlertDialog[1];
        InlineEpisodeAdapter adapter = new InlineEpisodeAdapter(episode -> {
            if (holder[0] != null) holder[0].dismiss();
            selectInlineEpisode(episode);
        });
        recycler.setAdapter(adapter);
        adapter.setItems(selectedFlag.getEpisodes(), selectedEpisode);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_episode)
                .setView(content)
                .create();
        holder[0] = dialog;
        dialog.setOnShowListener(value -> {
            int position = selectedFlag.getEpisodes().indexOf(selectedEpisode);
            if (position < 0) return;
            recycler.scrollToPosition(position);
            recycler.post(() -> {
                RecyclerView.ViewHolder viewHolder = recycler.findViewHolderForAdapterPosition(position);
                if (viewHolder != null) viewHolder.itemView.requestFocus();
            });
        });
        if (!canTouchUi()) return;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = ResUtil.getScreenWidth(this);
            window.setLayout(Math.min(ResUtil.dp2px(720), (int) (width * 0.92f)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private int inlineEpisodeSpanCount() {
        int width = ResUtil.getScreenWidth(this);
        if (width >= ResUtil.dp2px(1200)) return 5;
        if (width >= ResUtil.dp2px(720)) return 4;
        return 3;
    }

    private void selectInlineEpisode(Episode episode) {
        selectedEpisode = episode;
        selectedSeasonNumber = seasonForEpisode(episode, selectedFlag.getEpisodes());
        renderSeasonSelection();
        fetchSeasonIfNeeded(selectedSeasonNumber);
        renderEpisodes();
        updatePlayLabel();
        onPlay();
    }

    private void toggleInlineFullscreen() {
        if (service() == null || player().isEmpty()) return;
        if (inlineFullscreen) exitInlineFullscreen();
        else enterInlineFullscreen();
    }

    private void enterInlineFullscreen() {
        if (inlineFullscreen) return;
        inlineFullscreen = true;
        requestedOrientation = getRequestedOrientation();
        playerParent = (ViewGroup) binding.playerPanel.getParent();
        playerLayoutParams = binding.playerPanel.getLayoutParams();
        playerIndex = playerParent.indexOfChild(binding.playerPanel);
        playerParent.removeView(binding.playerPanel);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        binding.root.addView(binding.playerPanel, params);
        binding.playerPanel.setTranslationZ(32f);
        binding.playerPanel.setVisibility(View.VISIBLE);
        binding.playerPanel.setRadius(0);
        updatePlayerPanelFocus();
        setInlineFullscreenIcon();
        updateInlineButtons(player().isPlaying());
        hideInlineControls();
        binding.playerPanel.requestFocus();
        Util.toggleFullscreen(this, true);
        setRequestedOrientation(player().isPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
    }

    private void exitInlineFullscreen() {
        if (!inlineFullscreen) return;
        inlineFullscreen = false;
        ((ViewGroup) binding.playerPanel.getParent()).removeView(binding.playerPanel);
        if (playerParent != null && playerLayoutParams != null) {
            int index = playerIndex < 0 || playerIndex > playerParent.getChildCount() ? playerParent.getChildCount() : playerIndex;
            playerParent.addView(binding.playerPanel, index, playerLayoutParams);
        }
        binding.playerPanel.setTranslationZ(0f);
        setPlayerCard(lightTheme ? ThemeColors.light() : ThemeColors.dark());
        focusInlinePlayerPanel();
        setInlineFullscreenIcon();
        updateInlineButtons(player().isPlaying());
        Util.toggleFullscreen(this, false);
        setRequestedOrientation(requestedOrientation);
        playerParent = null;
        playerLayoutParams = null;
        playerIndex = -1;
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    }

    private void playAdjacentEpisode(int direction) {
        if (selectedFlag == null || selectedEpisode == null || selectedFlag.getEpisodes() == null) return;
        List<Episode> episodes = selectedFlag.getEpisodes();
        int index = episodes.indexOf(selectedEpisode);
        int next = index + direction;
        if (index < 0 || next < 0 || next >= episodes.size()) {
            Notify.show(direction > 0 ? R.string.error_play_next : R.string.error_play_prev);
            return;
        }
        selectedEpisode = episodes.get(next);
        selectedSeasonNumber = seasonForEpisode(selectedEpisode, episodes);
        renderEpisodes();
        onPlay();
    }

    private MediaMetadata buildMetadata() {
        String title = vod != null ? vod.getName() : getNameText();
        String episode = selectedEpisode != null ? selectedEpisode.getName() : "";
        String artist = TextUtils.isEmpty(episode) || title.equals(episode) ? "" : episode;
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(android.net.Uri.parse(vod != null ? vod.getPic() : getPicText())).build();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (handleInlineKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean handleInlineKey(KeyEvent event) {
        if (!isFusionMode() || !inlineStarted) return false;
        if (KeyUtil.isEnterKey(event) && inlineWakeControlsByKey) {
            if (KeyUtil.isActionUp(event)) {
                inlineWakeControlsByKey = false;
                showInlineControls(true, false);
                focusInlinePlaybackControl();
            }
            return true;
        }
        if (isInlineControlsVisible()) {
            rememberInlineControlFocus();
            setInlineHideCallback();
        }
        if (!inlineFullscreen || isInlineControlsVisible() || service() == null) return false;
        if (KeyUtil.isMenuKey(event)) {
            showInlineControls(true);
            return true;
        }
        if (KeyUtil.isEnterKey(event)) {
            if (KeyUtil.isActionDown(event)) {
                inlineWakeControlsByKey = true;
            }
            return true;
        }
        if (!KeyUtil.isActionUp(event)) return false;
        if (KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event)) {
            showInlineControls(true);
            return true;
        }
        return false;
    }

    @Override
    protected void onPrepare() {
        if (history == null) return;
        long position = Math.max(history.getOpening(), history.getPosition());
        if (position > 0) controller().seekTo(position);
        setInlineScale(getInlineScale());
        if (service() != null && !player().isEmpty()) binding.playerSpeed.setText(player().setSpeed(history.getSpeed()));
    }

    @Override
    protected void onServiceConnected() {
        if (isFusionMode()) {
            player().setDanmakuController(binding.exo.getDanmakuController());
            if (inlineControlController != null) inlineControlController.applyDanmakuSetting();
        }
        if (pendingInlineResult != null) startInlinePlayer(pendingInlineResult);
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        updateInlineButtons(isPlaying);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        updateInlineButtons(service() != null && !player().isEmpty() && player().isPlaying());
    }

    @Override
    protected void onStateChanged(int state) {
        if (!isFusionMode()) return;
        if (state == Player.STATE_BUFFERING) binding.playerProgress.setVisibility(View.VISIBLE);
        if (state == Player.STATE_READY) {
            binding.playerProgress.setVisibility(View.GONE);
            hideInlineControls();
            updateInlineButtons(player().isPlaying());
        }
    }

    @Override
    protected void onTracksChanged() {
        updateInlineButtons(service() != null && !player().isEmpty() && player().isPlaying());
    }

    @Override
    protected void onError(String msg) {
        showInlineError(msg);
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(binding.exo.getSubtitleView()).show(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, () -> playAdjacentEpisode(1), controller()::seekTo);
    }

    @Override
    protected void onDestroy() {
        if (inlineFullscreen) exitInlineFullscreen();
        App.removeCallbacks(inlineHideControls);
        saveInlineHistory();
        if (inlineClock != null) inlineClock.release();
        super.onDestroy();
    }

    @Override
    protected void onBackInvoked() {
        if (inlineFullscreen) {
            exitInlineFullscreen();
            return;
        }
        super.onBackInvoked();
    }

    private void saveInlineHistory() {
        if (!isFusionMode() || history == null || service() == null || player() == null) return;
        if (!player().isEmpty()) {
            history.setPosition(player().getPosition());
            history.setDuration(player().getDuration());
            if (history.canSave() && !Setting.isIncognito()) history.merge().save();
        }
    }

    private void syncInlineHistory() {
        if (history != null && !Setting.isIncognito()) Task.execute(() -> history.save());
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isFusionMode() || !isOwner() || history == null || service() == null || player() == null || player().isEmpty()) return;
        history.setCreateTime(time);
        history.setPosition(player().getPosition());
        history.setDuration(player().getDuration());
        if (history.canSave() && history.canSync()) syncInlineHistory();
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            playAdjacentEpisode(1);
        }

        @Override
        public void onPrev() {
            playAdjacentEpisode(-1);
        }

        @Override
        public void onStop() {
            saveInlineHistory();
        }

        @Override
        public void onReplay() {
            if (history != null) history.setPosition(C.TIME_UNSET);
            playInline();
        }
    };

    private void persistSelection() {
        if (selectedFlag == null || selectedEpisode == null) return;
        History saved = History.find(getHistoryKey());
        if (saved == null) {
            saved = new History();
            saved.setKey(getHistoryKey());
            saved.setCid(VodConfig.getCid());
        }
        saved.setCid(VodConfig.getCid());
        saved.setVodName(vod.getName());
        if (!selectedEpisode.getName().equals(saved.getVodRemarks())) saved.setPosition(androidx.media3.common.C.TIME_UNSET);
        saved.setVodFlag(selectedFlag.getFlag());
        saved.setVodRemarks(selectedEpisode.getName());
        saved.setEpisodeUrl(selectedEpisode.getUrl());
        saved.setVodPic(vod.getPic());
        saved.save();
        history = saved;
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) keep.delete();
        else createKeep();
        updateKeepState();
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(vod != null ? vod.getPic() : getPicText());
        keep.setVodName(vod != null ? vod.getName() : getNameText());
        keep.setSiteName(getSiteName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeepState() {
        String text = Keep.find(getHistoryKey()) == null ? getString(R.string.keep) : getString(R.string.keep_del);
        binding.keep.setText(text);
        binding.keepTop.setText(text);
        binding.keepFusion.setText(text);
    }

    private void changeSource() {
        if (vod == null) return;
        String keyword = vod != null && !TextUtils.isEmpty(vod.getName()) ? vod.getName() : getNameText();
        Notify.show(getString(R.string.detail_source_searching));
        Task.execute(() -> {
            SourceMatch match = searchChangeSource(keyword);
            runOnAliveUi(() -> {
                if (match == null) {
                    Notify.show(R.string.detail_source_empty);
                    return;
                }
                Notify.show(getString(R.string.play_switch_site, match.vod().getSiteName()));
                switchSourceDetail(match.site(), match.vod(), matchedTmdbItem);
            });
        });
    }

    private void loadPersonDetail(TmdbPerson person) {
        if (!tmdbConfig.isReady()) {
            Notify.show(getString(R.string.detail_tmdb_need_key));
            return;
        }
        Notify.show(getString(R.string.detail_person_loading));
        Task.execute(() -> {
            try {
                JsonObject personDetail = tmdbService.person(person.getPersonId(), tmdbConfig);
                TmdbPerson profile = tmdbService.personProfile(personDetail, tmdbConfig);
                List<TmdbItem> works = tmdbService.personWorks(personDetail, tmdbConfig);
                runOnAliveUi(() -> TmdbPersonDialog.show(this, profile, works, this::openRelatedItem));
            } catch (Throwable e) {
                runOnAliveUi(() -> Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_person_empty) : e.getMessage()));
            }
        });
    }

    private void openRelatedItem(TmdbItem item) {
        Site site = getCurrentSite();
        if (site == null || site.isEmpty() || !site.isSearchable()) {
            Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        Notify.show(getString(R.string.detail_work_searching, item.getTitle()));
        Task.execute(() -> {
            Vod match = searchCurrentSite(item.getTitle(), site);
            runOnAliveUi(() -> {
                if (match == null) {
                    Notify.show(getString(R.string.detail_work_global_searching, item.getTitle()));
                    SearchActivity.direct(this, item.getTitle());
                    return;
                }
                openMatchedDetail(site, match, item);
            });
        });
    }

    private void openMatchedDetail(Site site, Vod match, TmdbItem item) {
        if (isFusionMode()) {
            switchSourceDetail(site, match, item);
            return;
        }
        Intent intent = new Intent(this, TmdbDetailActivity.class);
        intent.putExtra("fusion", false);
        intent.putExtra("key", site.getKey());
        intent.putExtra("id", match.getId());
        intent.putExtra("name", match.getName());
        intent.putExtra("pic", match.getPic());
        intent.putExtra("mark", match.getRemarks());
        putTmdbItem(intent, item);
        startActivity(intent);
    }

    private void switchSourceDetail(Site site, Vod match, TmdbItem item) {
        TmdbBundle reusableBundle = canReuseTmdbBundle(item) ? activeTmdbBundle : null;
        Intent intent = new Intent(getIntent());
        intent.putExtra("fusion", isFusionMode());
        intent.putExtra("key", site.getKey());
        intent.putExtra("id", match.getId());
        intent.putExtra("name", match.getName());
        intent.putExtra("pic", match.getPic());
        intent.putExtra("mark", match.getRemarks());
        putTmdbItem(intent, item);
        setIntent(intent);
        resetDetailState();
        loadContent(reusableBundle);
    }

    private boolean canReuseTmdbBundle(@Nullable TmdbItem item) {
        if (activeTmdbBundle == null) return false;
        if (item == null || item.getTmdbId() <= 0 || TextUtils.isEmpty(item.getMediaType())) return true;
        TmdbItem activeItem = activeTmdbBundle.item();
        return activeItem != null && activeItem.getTmdbId() == item.getTmdbId() && item.getMediaType().equals(activeItem.getMediaType());
    }

    private Vod searchCurrentSite(String keyword, Site site) {
        try {
            Result result = SiteApi.searchContent(site, keyword, false, "1");
            return bestVod(result != null ? result.getList() : List.of(), keyword);
        } catch (Throwable e) {
            return null;
        }
    }

    private SourceMatch searchChangeSource(String keyword) {
        ExecutorCompletionService<SourceMatch> completion = new ExecutorCompletionService<>(Task.searchExecutor());
        List<Future<SourceMatch>> futures = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (isChangeSourceCandidate(site)) futures.add(completion.submit(() -> searchChangeSource(site, keyword)));
        }
        SourceMatch best = null;
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Constant.TIMEOUT_SEARCH);
        try {
            for (int i = 0; i < futures.size(); i++) {
                long timeout = deadline - System.nanoTime();
                if (timeout <= 0) break;
                Future<SourceMatch> future = completion.poll(timeout, TimeUnit.NANOSECONDS);
                if (future == null) break;
                SourceMatch match = future.get();
                if (match == null) continue;
                if (best == null || match.score() > best.score()) best = match;
                if (match.score() >= 300) break;
            }
        } catch (Throwable ignored) {
        } finally {
            for (Future<SourceMatch> future : futures) future.cancel(true);
        }
        return best;
    }

    private SourceMatch searchChangeSource(Site site, String keyword) {
        int bestScore = Integer.MIN_VALUE;
        Vod best = null;
        try {
            Result result = SiteApi.searchContent(site, keyword, true, "1");
            for (Vod item : result != null ? result.getList() : List.<Vod>of()) {
                if (isSameSource(item, site)) continue;
                int score = scoreVod(item, keyword);
                if (score > bestScore) {
                    bestScore = score;
                    best = item;
                }
            }
        } catch (Throwable ignored) {
        }
        return bestScore > 0 ? new SourceMatch(site, best, bestScore) : null;
    }

    private boolean isChangeSourceCandidate(Site site) {
        if (site == null || site.isEmpty() || !site.isSearchable()) return false;
        if (!site.isChangeable()) return false;
        return !site.getKey().equals(getKeyText());
    }

    private boolean isSameSource(Vod item, Site site) {
        if (item == null) return true;
        if (getIdText().equals(item.getId()) && getKeyText().equals(site.getKey())) return true;
        return false;
    }

    private Vod bestVod(List<Vod> items, String keyword) {
        if (items == null || items.isEmpty()) return null;
        Vod best = null;
        int score = Integer.MIN_VALUE;
        for (Vod item : items) {
            int current = scoreVod(item, keyword);
            if (current > score) {
                score = current;
                best = item;
            }
        }
        return score > 0 ? best : null;
    }

    private int scoreVod(Vod item, String keyword) {
        if (item == null) return Integer.MIN_VALUE;
        String normalizedKeyword = normalize(keyword);
        String name = normalize(item.getName());
        if (name.isEmpty()) return Integer.MIN_VALUE;
        if (name.equals(normalizedKeyword)) return 300;
        if (name.contains(normalizedKeyword) || normalizedKeyword.contains(name)) return 220;
        String remarks = normalize(item.getRemarks());
        if (!remarks.isEmpty() && (remarks.contains(normalizedKeyword) || normalizedKeyword.contains(remarks))) return 120;
        return 0;
    }

    private TmdbItem chooseTmdbMatch(List<TmdbItem> items, String keyword) {
        if (items == null || items.isEmpty()) return null;
        String normalized = normalize(keyword);
        List<TmdbItem> exact = new ArrayList<>();
        for (TmdbItem item : items) {
            if (normalize(item.getTitle()).equals(normalized)) exact.add(item);
        }
        if (exact.size() == 1) return exact.get(0);
        if (exact.size() > 1) return null;
        List<TmdbItem> fuzzy = new ArrayList<>();
        for (TmdbItem item : items) {
            String title = normalize(item.getTitle());
            if (!TextUtils.isEmpty(title) && (title.contains(normalized) || normalized.contains(title))) fuzzy.add(item);
        }
        return fuzzy.size() == 1 ? fuzzy.get(0) : null;
    }

    private int firstSeasonNumber(JsonObject detail) {
        JsonArray seasons = array(detail, "seasons");
        int fallback = -1;
        for (JsonElement element : seasons) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("season_number") || object.get("season_number").isJsonNull()) continue;
            int number = object.get("season_number").getAsInt();
            if (number > 0) return number;
            if (fallback == -1) fallback = number;
        }
        return fallback;
    }

    private Map<Integer, Integer> seasonEpisodeCounts(JsonObject detail) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        JsonArray seasons = array(detail, "seasons");
        for (JsonElement element : seasons) addSeasonCount(counts, element, true);
        if (counts.isEmpty()) for (JsonElement element : seasons) addSeasonCount(counts, element, false);
        return counts;
    }

    private void addSeasonCount(Map<Integer, Integer> counts, JsonElement element, boolean regularOnly) {
        if (!element.isJsonObject()) return;
        JsonObject object = element.getAsJsonObject();
        if (!object.has("season_number") || object.get("season_number").isJsonNull()) return;
        int number = object.get("season_number").getAsInt();
        if (regularOnly && number <= 0) return;
        int count = object.has("episode_count") && !object.get("episode_count").isJsonNull() ? object.get("episode_count").getAsInt() : 0;
        counts.put(number, count);
    }

    private String normalize(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private Flag findInitialFlag(List<Flag> flags) {
        String historyFlag = history != null ? history.getVodFlag() : "";
        for (Flag flag : flags) {
            if (!TextUtils.isEmpty(historyFlag) && historyFlag.equals(flag.getFlag())) return flag;
        }
        return flags.get(0);
    }

    private MaterialButton createChipButton(String text) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setCheckable(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setTextColor(getColor(android.R.color.white));
        button.setPadding(24, 12, 24, 12);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 12, 12);
        button.setLayoutParams(params);
        return button;
    }

    private void setChipState(MaterialButton button, boolean selected) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        button.setTextColor(colors.primary);
        button.setBackgroundColor(selected ? colors.chipActive : colors.chip);
        button.setOnFocusChangeListener(null);
        applyChipFocus(button, selected, button.hasFocus(), colors);
        button.setOnFocusChangeListener((view, focused) -> applyChipFocus(button, selected, focused, colors));
    }

    private void applyChipFocus(MaterialButton button, boolean selected, boolean focused, ThemeColors colors) {
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : (selected ? 2 : CHIP_STROKE_DP)));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : (selected ? colors.accent : colors.line)));
    }

    private void addMetaChip(String text) {
        if (TextUtils.isEmpty(text)) return;
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(colors.primary);
        chip.setTextSize(11f);
        chip.setPadding(16, 8, 16, 8);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.chip);
        background.setCornerRadius(999f);
        background.setStroke(1, colors.line);
        chip.setBackground(background);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 10, 10);
        chip.setLayoutParams(params);
        binding.metaContainer.addView(chip);
    }

    private String buildSubtitle() {
        List<String> parts = new ArrayList<>();
        String date = releaseDate();
        if (!TextUtils.isEmpty(date)) parts.add(date);
        String rating = ratingLabel();
        if (!TextUtils.isEmpty(rating)) parts.add(rating);
        return TextUtils.join(" · ", parts);
    }

    private String releaseDate() {
        if (matchedTmdbDetail == null) return vod.getYear();
        return string(matchedTmdbDetail, "first_air_date", "release_date");
    }

    private TmdbItem playbackTmdbItem() {
        if (matchedTmdbItem == null) return null;
        return new TmdbItem(
                matchedTmdbItem.getTmdbId(),
                matchedTmdbItem.getMediaType(),
                TextUtils.isEmpty(vod.getName()) ? matchedTmdbItem.getTitle() : vod.getName(),
                buildSubtitle(),
                vod.getContent(),
                TextUtils.isEmpty(matchedTmdbItem.getPosterUrl()) ? vod.getPic() : matchedTmdbItem.getPosterUrl(),
                TextUtils.isEmpty(matchedTmdbItem.getBackdropUrl()) ? vod.getPic() : matchedTmdbItem.getBackdropUrl(),
                matchedTmdbItem.getCredit());
    }

    private Vod playbackTmdbVod() {
        if (vod == null) return null;
        Vod item = new Vod();
        item.setName(coalesce(vod.getName(), matchedTmdbItem == null ? "" : matchedTmdbItem.getTitle()));
        item.setContent(coalesce(vod.getContent(), matchedTmdbItem == null ? "" : matchedTmdbItem.getOverview()));
        item.setPic(coalesce(matchedTmdbItem == null ? "" : matchedTmdbItem.getBackdropUrl(), matchedTmdbItem == null ? "" : matchedTmdbItem.getPosterUrl(), vod.getPic()));
        item.setYear(yearLabel());
        item.setArea(coalesce(firstCountry(), vod.getArea()));
        item.setTypeName(coalesce(firstGenre(), vod.getTypeName()));
        item.setDirector(coalesce(firstCrew("Director"), vod.getDirector()));
        item.setRemarks(coalesce(getMarkText(), vod.getRemarks()));
        return item;
    }

    private String yearLabel() {
        String date = releaseDate();
        if (!TextUtils.isEmpty(date) && date.length() >= 4) return date.substring(0, 4);
        return vod == null ? "" : vod.getYear();
    }

    private String ratingLabel() {
        if (matchedTmdbDetail == null || !matchedTmdbDetail.has("vote_average") || matchedTmdbDetail.get("vote_average").isJsonNull()) return "";
        return getString(R.string.detail_score, String.format(Locale.US, "%.1f", matchedTmdbDetail.get("vote_average").getAsDouble()));
    }

    private String getMediaTypeLabel() {
        if (matchedTmdbItem == null) return getString(R.string.detail_media_unknown);
        return "tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) ? getString(R.string.detail_media_tv) : getString(R.string.detail_media_movie);
    }

    private String firstGenre() {
        JsonArray genres = array(matchedTmdbDetail, "genres");
        for (JsonElement element : genres) {
            if (element.isJsonObject()) return string(element.getAsJsonObject(), "name");
        }
        return "";
    }

    private String firstCountry() {
        JsonArray countries = array(matchedTmdbDetail, "production_countries");
        for (JsonElement element : countries) {
            if (element.isJsonObject()) return string(element.getAsJsonObject(), "name");
        }
        JsonArray origins = array(matchedTmdbDetail, "origin_country");
        for (JsonElement element : origins) {
            if (element.isJsonPrimitive()) return element.getAsString();
        }
        return "";
    }

    private String firstCrew(String job) {
        JsonArray crew = array(matchedTmdbDetail, "credits", "crew");
        for (JsonElement element : crew) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (job.equalsIgnoreCase(string(object, "job"))) return string(object, "name");
        }
        return "";
    }

    private JsonArray array(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return new JsonArray();
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
            current = currentObject.get(key);
        }
        return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
    }

    private String string(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                String value = object.get(key).getAsString();
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private String coalesce(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private Site getCurrentSite() {
        Site site = vod != null ? vod.getSite() : null;
        if (site != null && !site.isEmpty()) return site;
        Site fallback = VodConfig.get().getSite(getKeyText());
        return fallback.isEmpty() ? null : fallback;
    }

    private String getSiteName() {
        Site site = getCurrentSite();
        return site == null ? getKeyText() : site.getName();
    }

    private String getHistoryKey() {
        return getKeyText() + AppDatabase.SYMBOL + getIdText() + AppDatabase.SYMBOL + VodConfig.getCid();
    }

    private String getKeyText() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getIdText() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getNameText() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPicText() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getMarkText() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private TmdbItem getIntentTmdbItem() {
        int tmdbId = getIntent().getIntExtra("tmdb_id", 0);
        String mediaType = Objects.toString(getIntent().getStringExtra("tmdb_media_type"), "");
        if (tmdbId <= 0 || TextUtils.isEmpty(mediaType)) return null;
        return new TmdbItem(
                tmdbId,
                mediaType,
                Objects.toString(getIntent().getStringExtra("tmdb_title"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_subtitle"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_overview"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_poster"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_backdrop"), ""),
                Objects.toString(getIntent().getStringExtra("tmdb_credit"), ""));
    }

    private record TmdbBundle(TmdbItem item, JsonObject detail, List<TmdbPerson> cast, List<TmdbItem> related, List<Integer> seasons, Map<Integer, Integer> seasonCounts, Map<Integer, List<TmdbEpisode>> seasonEpisodes) {
    }

    private record SourceMatch(Site site, Vod vod, int score) {
    }

    private record ThemeColors(int background, int panel, int control, int chip, int chipActive, int line, int lineStrong, int primary, int secondary, int muted, int body, int accent, int play, int backdropShade) {

        static ThemeColors dark() {
            return new ThemeColors(
                    0xFF0F141A,
                    0xD9141B23,
                    0xFF2B3743,
                    0x332B3743,
                    0x6630A86B,
                    0x26FFFFFF,
                    0x4DFFFFFF,
                    0xFFFFFFFF,
                    0xCCFFFFFF,
                    0x99FFFFFF,
                    0xE6FFFFFF,
                    0xFF7EE7A2,
                    0xFF2CC56F,
                    0x7A0F141A
            );
        }

        static ThemeColors light() {
            return new ThemeColors(
                    0xFFF3F6F9,
                    0xCCFFFFFF,
                    0xFFE7EDF3,
                    0xFFEAF0F5,
                    0xFFE5F7EC,
                    0x33424B57,
                    0x66424B57,
                    0xFF12202D,
                    0xCC12202D,
                    0x9912202D,
                    0xE612202D,
                    0xFF1D8F5A,
                    0xFF20B866,
                    0x4DF7FAFC
            );
        }
    }
}
