package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.widget.NestedScrollView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.api.config.UserAdRuleStore;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.ad.feedback.AdFeedbackController;
import com.fongmi.android.tv.ad.feedback.AdFeedbackHostAdapter;
import com.fongmi.android.tv.ad.feedback.AdFeedbackSession;
import com.fongmi.android.tv.ad.feedback.AdRulePlanApplier;
import com.fongmi.android.tv.ui.dialog.AdFeedbackDialog;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.EpisodePositionCache;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.FlagPreferenceCache;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonMatchCache;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.fongmi.android.tv.bean.TmdbSeasonScope;
import com.fongmi.android.tv.bean.TmdbSeasonSegment;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityTmdbDetailBinding;
import com.fongmi.android.tv.databinding.DialogTmdbEpisodeBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.ui.detail.DetailModeHost;
import com.fongmi.android.tv.ui.detail.EnhancedDetailController;
import com.fongmi.android.tv.ui.detail.FusionDetailController;
import com.fongmi.android.tv.ui.detail.PlayerDetailController;
import com.fongmi.android.tv.ui.detail.TmdbDetailModeController;
import com.fongmi.android.tv.ui.host.TmdbDetailHost;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.player.IntroSkipKinds;
import com.fongmi.android.tv.player.IntroSkipPlayback;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.exo.MediaSourceFactory;
import com.fongmi.android.tv.service.AiEpisodeSeasonService;
import com.fongmi.android.tv.service.AiRecommendationService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.service.OmdbService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.BackgroundPlaybackPolicy;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.MultiThreadProxySetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.TmdbSitePolicy;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleLearningStore;
import com.fongmi.android.tv.title.MediaTitleParser;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.title.MediaTitleResolution;
import com.fongmi.android.tv.title.MediaTitleResolver;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.InlineEpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbEpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPersonAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbPhotoAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbRailAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbVideoAdapter;
import com.fongmi.android.tv.ui.controller.VodPlayerControlController;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.helper.TmdbVideoPlayback;
import com.fongmi.android.tv.ui.custom.EpisodeTitlePopup;
import com.fongmi.android.tv.ui.custom.PlayerGesture;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.dialog.AdRulePreviewDialog;
import com.fongmi.android.tv.ui.dialog.CodecCapabilityDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DisplayDialog;
import com.fongmi.android.tv.ui.dialog.MultiThreadProxyDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleManualSearchDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSearchDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.novel.NovelRouter;
import com.fongmi.android.tv.ui.helper.DetailThemeVisibility;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.ui.helper.EpisodeCardImagePolicy;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonPolicy;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonSnapshot;
import com.fongmi.android.tv.ui.helper.PipExitDecision;
import com.fongmi.android.tv.ui.helper.PlayerControlFocusHelper;
import com.fongmi.android.tv.ui.helper.TmdbCinemaTheme;
import com.fongmi.android.tv.ui.helper.TmdbDetailLabels;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeInfo;
import com.fongmi.android.tv.ui.helper.TmdbSeasonResolver;
import com.fongmi.android.tv.history.TmdbSeasonSourceAggregator;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeMatcher;
import com.fongmi.android.tv.ui.helper.TmdbMatchPolicy;
import com.fongmi.android.tv.ui.helper.TmdbMatcher;
import com.fongmi.android.tv.ui.helper.TmdbRecommendationRows;
import com.fongmi.android.tv.ui.helper.TmdbUIAdapter;
import com.fongmi.android.tv.ui.player.VodPlayerChrome;
import com.fongmi.android.tv.ui.player.VodPlayerUiController;
import com.fongmi.android.tv.ui.player.VodPlayerUiHost;
import com.fongmi.android.tv.utils.ActivityLaunch;
import com.fongmi.android.tv.utils.BatteryUtil;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.ui.utils.TmdbDetailLayoutUtils;
import com.fongmi.android.tv.utils.TmdbDetailCache;
import com.fongmi.android.tv.utils.TmdbEpisodeSorter;
import com.fongmi.android.tv.utils.TmdbImageSelector;
import com.fongmi.android.tv.utils.TmdbImageSaver;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.web.WebHomeInlineVodStore;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.target.Target;
import com.bumptech.glide.request.transition.Transition;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TmdbDetailActivity extends PlaybackActivity implements TrackDialog.Listener, Clock.Callback, PlayerGesture.Listener, SubtitlePlaybackSession.Host, TmdbDetailHost {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault());
    private static final int FOCUS_STROKE = 0xFFFFD166;
    private static final int FOCUS_STROKE_DP = 3;
    private static final int CHIP_STROKE_DP = 1;
    private static final int CHIP_MAX_WIDTH_DP = 240;
    private static final int PHOTO_PRELOAD_RADIUS = 2;
    private static final long BACKDROP_SLIDE_DELAY_MS = 10_000L;
    private static final long BACKDROP_SLIDE_RETRY_MS = 2_500L;
    private static final int SHORT_DRAMA_SCALE = 0;
    private static final int SHORT_DRAMA_FRAME_WIDTH = 9;
    private static final int SHORT_DRAMA_FRAME_HEIGHT = 16;
    private static final int INLINE_SIDE_CONTROL_MARGIN_DP = 4;
    private static final int INLINE_SIDE_CONTROL_FULLSCREEN_MARGIN_DP = 48;
    private static final long INLINE_CONTROLS_HIDE_DELAY_MS = TimeUnit.SECONDS.toMillis(10);
    private static final int STANDALONE_MOBILE_EPISODE_CARD_PAGE_MAX_SIZE = 36;
    private static final long LEANBACK_FUSION_EXIT_DISPLAY_SUPPRESS_MS = 800;
    private static final Pattern SOURCE_SEASON = Pattern.compile("(?i)(?:第\\s*([零〇一二三四五六七八九十两0-9]+)\\s*[季部]|season\\s*([0-9]{1,2})|s([0-9]{1,2})(?:[-._\\s]*e[0-9]{1,3})?)");

    private ActivityResultLauncher<Intent> inlineLutDir;
    private ActivityResultLauncher<Intent> inlineLutFile;

    private final TmdbService tmdbService = new TmdbService();
    private final Task.Scope detailTasks = new Task.Scope(Task.executor());
    private final IntroSkipPlayback introSkipPlayback = new IntroSkipPlayback();
    private androidx.appcompat.app.AlertDialog introSkipConfirmDialog;
    private boolean introSkipListenersReady;
    private final SubtitlePlaybackSession subtitlePlaybackSession = new SubtitlePlaybackSession(this);
    private final List<TmdbPerson> detailCastItems = new ArrayList<>();
    private final List<TmdbPerson> castItems = new ArrayList<>();
    private final List<TmdbPerson> creatorItems = new ArrayList<>();
    private final List<TmdbItem> relatedItems = new ArrayList<>();
    private final List<TmdbVideo> relatedVideoItems = new ArrayList<>();
    private final List<TmdbItem> personalTmdbItems = new ArrayList<>();
    private final List<TmdbItem> personalDoubanItems = new ArrayList<>();
    private final List<TmdbItem> personalAiItems = new ArrayList<>();
    private final Map<Integer, TmdbEpisode> tmdbEpisodes = new HashMap<>();
    private final List<Integer> seasonNumbers = new ArrayList<>();
    private final Map<Integer, Integer> seasonEpisodeCounts = new HashMap<>();
    private final Map<Integer, List<TmdbEpisode>> tmdbSeasonEpisodes = new HashMap<>();
    private final Map<Integer, List<TmdbPerson>> tmdbSeasonCast = new HashMap<>();
    private final Map<Integer, List<String>> tmdbSeasonPhotos = new HashMap<>();
    private final Set<Integer> loadingSeasons = new HashSet<>();
    private final Set<Integer> refreshedSingleSeasonProbes = new HashSet<>();
    private final Set<String> brokenSources = new HashSet<>();
    private final List<String> detailTmdbPhotos = new ArrayList<>();
    private final List<String> detailTmdbPosters = new ArrayList<>();
    private final List<String> detailBackdropSlides = new ArrayList<>();
    private final List<String> tmdbEpisodePhotos = new ArrayList<>();
    private Map<Episode, Integer> episodeIndexCache = new IdentityHashMap<>();
    private List<Episode> episodeIndexSource;
    private List<Episode> explicitSeasonSource;
    private boolean explicitSeasonCache;
    private final Map<Episode, SourceEpisodeSeason> sourceEpisodeSeasonCache = new IdentityHashMap<>();
    private List<Episode> sourceSeasonCacheSource;
    private List<Integer> sourceSeasonCache = List.of();
    private List<Episode> availableSeasonCacheSource;
    private Flag availableSeasonCacheFlag;
    private List<Integer> availableSeasonCache = List.of();
    private List<Episode> visibleEpisodeSource;
    private List<Episode> visibleEpisodeCache = List.of();
    private int visibleEpisodeSeason = Integer.MIN_VALUE;
    private final List<String> backdropSlideItems = new ArrayList<>();
    private final Set<String> backdropSlideFailures = new HashSet<>();
    private final Runnable backdropSlideNext = this::loadNextBackdropSlide;

    private ActivityTmdbDetailBinding binding;
    @androidx.annotation.Keep
    private ActivityTmdbDetailBinding mBinding;
    private TmdbDetailModeController modeController;
    private Vod vod;
    private String sourceVodName;
    private History history;
    @androidx.annotation.Keep
    private History mHistory;
    private TmdbConfig tmdbConfig;
    private TmdbBundle activeTmdbBundle;
    private TmdbItem initialTmdbItem;
    private TmdbItem matchedTmdbItem;
    private JsonObject matchedTmdbDetail;
    private TmdbEpisodeInfo cachedEpisodeInfo;
    private TmdbItem cachedEpisodeInfoItem;
    private JsonObject cachedEpisodeInfoDetail;
    private int cachedEpisodeInfoSeason = Integer.MIN_VALUE;
    private Flag selectedFlag;
    private Episode selectedEpisode;
    private boolean playbackSelectionTouched;
    private Episode inlinePlaybackEpisode;
    private String inlinePlaybackKey = "";
    private String inlinePlaybackFlag = "";
    private TmdbEpisodeAdapter episodeAdapter;
    private TmdbPersonAdapter castAdapter;
    private TmdbPersonAdapter creatorAdapter;
    private TmdbPhotoAdapter episodePhotoAdapter;
    private TmdbPhotoAdapter posterAdapter;
    private TmdbRailAdapter relatedAdapter;
    private TmdbVideoAdapter relatedVideoAdapter;
    private TmdbRailAdapter personalTmdbAdapter;
    private TmdbRailAdapter personalDoubanAdapter;
    private TmdbRailAdapter personalAiAdapter;
    private boolean relatedVideoLoading;
    private String relatedVideoContextKey = "";
    private int relatedVideoGeneration;
    private boolean overviewExpanded;
    private boolean useParse;
    private boolean inlineStarted;
    private boolean inlineHttpRefreshAttempted;
    private boolean detailPlayerActive;
    private boolean autoPlayed;
    private boolean defaultPlaybackLaunchPending;
    private boolean inlineFullscreen;
    private boolean inlineShortDramaMode;
    private boolean inlinePauseInfo;
    private boolean inlinePlaybackLoading;
    private boolean inlinePlaybackReconnectPending;
    private boolean inlinePlayerSwitchLoading;
    private boolean savingTmdbPhoto;
    private boolean pendingInlineLutImport;
    private PlayerGesture inlineGestureDetector;
    private VodPlayerUiController inlinePlayerUi;
    private Clock inlineClock;
    private VodPlayerControlController inlineControlController;
    private PlayerOsdController inlineOsd;
    private InlineParseAdapter inlineParseAdapter;
    private PiP inlinePiP;
    private final Runnable inlineHideControls = this::hideInlineControlsIfIdle;
    private final Runnable inlineKeySeekEnd = this::onInlineKeySeekEnd;
    private Result pendingInlineResult;
    private Result currentInlineResult;
    // playerPanel 已提升到 root 层，全屏切换只调整 LayoutParams，不再 reparent
    private View detailControlRoot;
    private View detailActionRoot;
    private ViewGroup detailActionParent;
    private ViewGroup.LayoutParams detailActionLayoutParams;
    private int detailActionIndex;
    private View inlineControlFocus;
    private long lastInlineControlInteraction;
    private long inlineDisplaySuppressUntil;
    private long inlineKeySeekTime;
    private boolean inlineKeySpeedChanging;
    private float inlineGestureSpeed = 1.0f;
    private boolean inlineStartPositionApplied;
    private boolean inlineFirstReady;
    private boolean inlineButtonsReordered;
    private View mNightModeOverlay;
    private int mNightModeLevel = PlayerSetting.NIGHT_MODE_OFF;
    private boolean inlinePiPLayout;
    private boolean inlinePiPSourceFrozen;
    private long inlineStartPosition = C.TIME_UNSET;
    private int selectedSeasonNumber = -1;
    private TmdbSeasonMatchCache.Entry tmdbSeasonBinding;
    private TmdbItem pendingTmdbSeasonChoice;
    private int lastEpisodeMediaSeason = Integer.MIN_VALUE;
    private int requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private float inlinePiPTranslationZ;
    private int headerBarBasePaddingTop = -1;
    private int statusBarInsetTop;
    private int detailThemeMode;
    private int loadGeneration;
    /** 本次取详情的起始时刻，用来认出「猫源开内嵌页」是不是自己这次导航触发的。volatile：加载在后台线程发起，事件在主线程读。 */
    private volatile long detailLoadStart;
    private int inlinePlaybackGeneration;
    private AdFeedbackController mAdFeedback;
    private AdFeedbackHostAdapter mAdFeedbackHost;
    private AdFeedbackDialog mAdFeedbackDialog;
    /** 标记模式下已打的起点，-1 表示未进入标记模式。 */
    private long mAdMarkStartMs = -1;
    private int tmdbDialogGeneration;
    private AiEpisodeSeasonService aiSeasonService;
    private AlertDialog aiSeasonLoadingDialog;
    private int tmdbApplyGeneration;
    private int tmdbEpisodeDetailGeneration;
    private int pendingManualTmdbEpisodeRebindGeneration = -1;
    private TmdbItem pendingManualTmdbEpisodeRebindItem;
    private int sourceSearchGeneration;
    private SiteViewModel inlineSearchViewModel;
    private Object inlineQuickSearchDialog;
    private String inlineSearchKeyword = "";
    private boolean inlineSearchObserved;
    private boolean inlineSearchClosed;
    private int seasonSourceRouteGeneration;
    private int backdropSlideGeneration;
    private int backdropSlideIndex;
    private boolean episodeGridMode = Setting.getTmdbEpisodeGridMode();
    private boolean inlineEpisodeGridMode = true;
    private boolean episodeReverse;
    private boolean manualEpisodeRange;
    private boolean scrollEpisodeStartOnce;
    private int episodeRangeIndex;
    private int renderedEpisodeRangeIndex = -1;
    private int pendingEpisodeRangeFocus = -1;
    private int lastDetailEpisodeFocusRowStart = RecyclerView.NO_POSITION;
    private boolean tmdbMediaLoading;
    private boolean lightTheme;
    private boolean backdropSlideLoading;
    private String backdropSlideTitle = "";
    private String backdropSlidePrimary = "";
    private ImageView backdropSlideVisibleView;
    private ImageView backdropSlideLoadingView;

    private static final String EXTRA_RESUME_FROM_HISTORY = "resume_from_history";
    private static final String EXTRA_RESUME_HISTORY_CID = "resume_history_cid";
    private static final String EXTRA_RESUME_HISTORY_KEY = "resume_history_key";
    private static final String EXTRA_PLAY_FLAG = "tmdb_play_flag";
    private static final String EXTRA_PLAY_FLAG_KEY = "tmdb_play_flag_key";
    private static final String EXTRA_PLAY_EPISODE_NAME = "tmdb_play_episode_name";
    private static final String EXTRA_PLAY_EPISODE_URL = "tmdb_play_episode_url";
    private static final String EXTRA_PLAY_SEASON_NUMBER = "tmdb_play_season_number";
    private static final String SOURCE_SEARCH_KEYWORD = "SEARCH_KEYWORD";

    @Override
    public TmdbItem getMatchedTmdbItem() {
        return matchedTmdbItem;
    }

    @Override
    public JsonObject getMatchedTmdbDetail() {
        return matchedTmdbDetail;
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, Setting.getDetailOpenMode(), false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem, int detailMode) {
        start(activity, key, id, name, pic, mark, tmdbItem, detailMode, false);
    }

    /** 搜索结果进入独立 TMDB 详情：带上用户输入的搜索关键词，供自动匹配与 AI 兜底使用。 */
    public static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem, int detailMode, String searchKeyword) {
        start(activity, key, id, name, pic, mark, tmdbItem, detailMode, false, null, null, "", null, null, searchKeyword);
    }

    public static void startFusion(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null, Setting.DETAIL_OPEN_FUSION, false);
    }

    public static void startFusion(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, Setting.DETAIL_OPEN_FUSION, false);
    }

    public static void startCinema(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, null, Setting.DETAIL_OPEN_CINEMA, false);
    }

    public static void startCinema(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, tmdbItem, Setting.DETAIL_OPEN_CINEMA, false);
    }

    public static void startPlayback(Activity activity, String key, String id, String name, String pic, String mark, boolean fusion) {
        startPlayback(activity, key, id, name, pic, mark, fusion ? Setting.DETAIL_OPEN_FUSION : Setting.DETAIL_OPEN_ENHANCED);
    }

    public static void startPlayback(Activity activity, String key, String id, String name, String pic, String mark, int detailMode) {
        start(activity, key, id, name, pic, mark, null, detailMode, true);
    }

    public static void startFromHistory(Activity activity, History item) {
        if (item == null) return;
        start(activity, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getVodRemarks(), null, Setting.getDetailOpenMode(), false, item, item.getVodFlag(), item.getVodRemarks(), item.getEpisodeUrl());
    }

    public static void startFromResolvedHistory(Activity activity, History source, Vod target, Flag flag, Episode episode) {
        if (source == null || target == null || flag == null || episode == null) return;
        int flagIndex = TmdbUIAdapter.flagIndex(target.getFlags(), flag);
        String flagKey = flagIndex < 0 ? "" : TmdbUIAdapter.flagKey(flag, flagIndex);
        start(activity, target.getSiteKey(), target.getId(), target.getName(), target.getPic(), episode.getName(), null,
                Setting.getDetailOpenMode(), false, source, flag.getFlag(), flagKey, episode.getName(), episode.getUrl());
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem, int detailMode, boolean autoPlay) {
        start(activity, key, id, name, pic, mark, tmdbItem, detailMode, autoPlay, null, null, null, null);
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark, @Nullable TmdbItem tmdbItem, int detailMode, boolean autoPlay, @Nullable History resumeHistory, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        start(activity, key, id, name, pic, mark, tmdbItem, detailMode, autoPlay, resumeHistory,
                playFlag, resumeHistory == null ? "" : resumeHistory.getSourceBindingKey(),
                playEpisodeName, playEpisodeUrl);
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark,
                              @Nullable TmdbItem tmdbItem, int detailMode, boolean autoPlay,
                              @Nullable History resumeHistory, String playFlag, String playFlagKey,
                              String playEpisodeName, String playEpisodeUrl) {
        start(activity, key, id, name, pic, mark, tmdbItem, detailMode, autoPlay, resumeHistory,
                playFlag, playFlagKey, playEpisodeName, playEpisodeUrl, "");
    }

    private static void start(Activity activity, String key, String id, String name, String pic, String mark,
                              @Nullable TmdbItem tmdbItem, int detailMode, boolean autoPlay,
                              @Nullable History resumeHistory, String playFlag, String playFlagKey,
                              String playEpisodeName, String playEpisodeUrl, String searchKeyword) {
        if (!TextUtils.isEmpty(key) && !SiteApi.PUSH.equals(key) && AudioUtil.isAudioSiteEnabled(key)) {
            startDirectFromHistory(activity, key, id, name, pic, mark, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl, resumeHistory);
            return;
        }
        if (!TextUtils.isEmpty(key) && !SiteApi.PUSH.equals(key) && isShortDramaSiteEnabled(key)) {
            startDirectFromHistory(activity, key, id, name, pic, mark, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl, resumeHistory);
            return;
        }
        if (!TextUtils.isEmpty(key) && !SiteApi.PUSH.equals(key) && !TmdbSitePolicy.isEnabled(key, id)) {
            startDirectFromHistory(activity, key, id, name, pic, mark, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl, resumeHistory);
            return;
        }
        Intent intent = new Intent(activity, TmdbDetailActivity.class);
        boolean cinemaStyle = detailMode == Setting.DETAIL_OPEN_CINEMA;
        detailMode = normalizeDetailMode(detailMode);
        intent.putExtra("detail_mode", cinemaStyle ? Setting.DETAIL_OPEN_CINEMA : detailMode);
        intent.putExtra("fusion", detailMode == Setting.DETAIL_OPEN_FUSION);
        intent.putExtra("auto_play", autoPlay);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("mark", mark);
        if (!TextUtils.isEmpty(searchKeyword)) intent.putExtra("search_keyword", searchKeyword);
        putTmdbItem(intent, tmdbItem);
        if (resumeHistory != null) {
            intent.putExtra(EXTRA_RESUME_FROM_HISTORY, true);
            intent.putExtra(EXTRA_RESUME_HISTORY_CID, resumeHistory.getCid());
            intent.putExtra(EXTRA_RESUME_HISTORY_KEY, HistoryResumePayload.encode(resumeHistory));
        }
        putIntentPlaybackSelection(intent, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl);
        activity.startActivity(intent);
    }

    private static void startDirectFromHistory(Activity activity, String key, String id, String name, String pic,
                                               String mark, String playFlag, String playFlagKey,
                                               String playEpisodeName, String playEpisodeUrl,
                                               @Nullable History resumeHistory) {
        if (resumeHistory == null) {
            VideoActivity.startDirect(activity, key, id, name, pic, mark);
            return;
        }
        VideoActivity.startDirect(activity, key, id, name, pic, mark, playFlag, playFlagKey,
                playEpisodeName, playEpisodeUrl, resumeHistory);
    }

    private static void putIntentPlaybackSelection(Intent intent, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        putIntentPlaybackSelection(intent, playFlag, "", playEpisodeName, playEpisodeUrl);
    }

    private static void putIntentPlaybackSelection(Intent intent, String playFlag, String playFlagKey,
                                                   String playEpisodeName, String playEpisodeUrl) {
        if (!TextUtils.isEmpty(playFlag)) intent.putExtra(EXTRA_PLAY_FLAG, playFlag);
        if (!TextUtils.isEmpty(playFlagKey)) intent.putExtra(EXTRA_PLAY_FLAG_KEY, playFlagKey);
        if (!TextUtils.isEmpty(playEpisodeName)) intent.putExtra(EXTRA_PLAY_EPISODE_NAME, playEpisodeName);
        if (!TextUtils.isEmpty(playEpisodeUrl)) intent.putExtra(EXTRA_PLAY_EPISODE_URL, playEpisodeUrl);
    }

    private static int normalizeDetailMode(int detailMode) {
        return Setting.isTmdbMode(detailMode) ? detailMode : Setting.DETAIL_OPEN_ENHANCED;
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
        return mBinding = binding = ActivityTmdbDetailBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean customWall() {
        return true;
    }

    @Override
    protected boolean customWallMotion() {
        return false;
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return inlineSeek();
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
    protected boolean shouldBindPlaybackService() {
        return isFusionMode() || isPlayerMode();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        inflateMobileInlineControl();
        super.initView(savedInstanceState);
        tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        initialTmdbItem = getIntentTmdbItem();
        detailThemeMode = Setting.getTmdbDetailTheme();
        applyDetailEdgeToEdge();
        applySystemBarInsets();
        initPage();
        initModeController();
        setLoadingOnlyBeforeDefaultPlayback(shouldUseLoadingOnlyBeforeDefaultPlayback());
        loadContent(null);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resetPlaybackOwnership();
        brokenSources.clear();
        resetDetailState();
        loadContent(null);
    }

    private void resetDetailState() {
        cancelAiSeasonAnalysis(false);
        closeInlineSearch();
        tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        initialTmdbItem = getIntentTmdbItem();
        vod = null;
        matchedTmdbItem = null;
        matchedTmdbDetail = null;
        cachedEpisodeInfo = null;
        cachedEpisodeInfoItem = null;
        cachedEpisodeInfoDetail = null;
        cachedEpisodeInfoSeason = Integer.MIN_VALUE;
        history = null;
        mHistory = null;
        selectedFlag = null;
        selectedEpisode = null;
        playbackSelectionTouched = false;
        clearSourceEpisodeSeasonCache();
        clearEpisodeRenderCaches();
        resetEpisodeRange();
        inlineStarted = false;
        detailPlayerActive = false;
        autoPlayed = false;
        inlinePlaybackGeneration++;
        tmdbDialogGeneration++;
        tmdbApplyGeneration++;
        tmdbEpisodeDetailGeneration++;
        sourceSearchGeneration++;
        inlinePlaybackLoading = false;
        inlinePlayerSwitchLoading = false;
        inlineStartPosition = C.TIME_UNSET;
        pendingInlineResult = null;
        currentInlineResult = null;
        activeTmdbBundle = null;
        useParse = false;
        detailTmdbPhotos.clear();
        detailTmdbPosters.clear();
        detailBackdropSlides.clear();
        tmdbEpisodePhotos.clear();
        relatedVideoItems.clear();
        relatedVideoLoading = false;
        relatedVideoContextKey = "";
        relatedVideoGeneration++;
        personalTmdbItems.clear();
        personalDoubanItems.clear();
        personalAiItems.clear();
        if (service() != null) {
            subtitlePlaybackSession.stop(this);
            player().stop();
            player().clear();
        }
        updateNavigationKey();
        binding.loading.setVisibility(View.VISIBLE);
        setLoadingOnlyBeforeDefaultPlayback(shouldUseLoadingOnlyBeforeDefaultPlayback());
        hideInlineLoading();
        binding.playerError.setVisibility(View.GONE);
        binding.playerControls.setVisibility(View.GONE);
        binding.detailControlHost.setVisibility(View.GONE);
        hideMobileFusionPlayerActionDock();
        binding.flagContainer.removeAllViews();
        binding.seasonContainer.removeAllViews();
        clearEpisodeRanges();
        if (episodeAdapter != null) episodeAdapter.setFallbackStillUrl(episodeFallbackStillUrl());
        episodeAdapter.setItems(List.of(), Map.of(), null);
        if (episodePhotoAdapter != null) episodePhotoAdapter.setItems(List.of());
        castAdapter.setItems(new ArrayList<>());
        creatorAdapter.setItems(new ArrayList<>());
        relatedAdapter.setItems(new ArrayList<>());
        relatedVideoAdapter.setItems(new ArrayList<>());
        personalTmdbAdapter.setItems(new ArrayList<>());
        personalDoubanAdapter.setItems(new ArrayList<>());
        personalAiAdapter.setItems(new ArrayList<>());
        showAiRecommendationReason(null, false);
        clearExternalLinks();
        binding.tmdbStatus.setVisibility(View.GONE);
        bindInitialArtwork();
    }

    private void initPage() {
        binding.play.setOnClickListener(view -> {
            playbackSelectionTouched = true;
            onPlay();
        });
        binding.keep.setOnClickListener(view -> onKeep());
        binding.keepTop.setOnClickListener(view -> onKeep());
        binding.keepFusion.setOnClickListener(view -> onKeep());
        binding.rematch.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.rematchTop.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.rematchFusion.setOnClickListener(view -> showManualTmdbMatchDialog());
        binding.episodeTitle.setOnClickListener(view -> showManualTmdbSeasonDialog());
        binding.rematch.setOnLongClickListener(view -> { showManualTmdbSeasonDialog(); return true; });
        binding.rematchTop.setOnLongClickListener(view -> { showManualTmdbSeasonDialog(); return true; });
        binding.rematchFusion.setOnLongClickListener(view -> { showManualTmdbSeasonDialog(); return true; });
        binding.changeSource.setOnClickListener(view -> changeSource());
        binding.changeSourceDetail.setOnClickListener(view -> changeSource());
        binding.changeSource.setOnLongClickListener(view -> openGlobalSourceSearch());
        binding.changeSourceDetail.setOnLongClickListener(view -> openGlobalSourceSearch());
        binding.themeModeTop.setOnClickListener(view -> cycleThemeMode());
        binding.themeMode.setOnClickListener(view -> cycleThemeMode());
        binding.themeModeDetail.setOnClickListener(view -> cycleThemeMode());
        binding.episodeReverse.setOnClickListener(view -> toggleEpisodeReverse());
        binding.episodeViewMode.setOnClickListener(view -> toggleEpisodeViewMode());
        binding.episodeFileName.setOnClickListener(view -> toggleEpisodeFileName());
        binding.episodeTitle.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));
        binding.episodeReverse.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));
        binding.episodeViewMode.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));
        binding.episodeFileName.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeToolKey(view, keyCode, event));
        binding.episodeTitle.setNextFocusRightId(R.id.episodeReverse);
        binding.episodeReverse.setNextFocusLeftId(R.id.episodeTitle);
        binding.episodeReverse.setNextFocusRightId(R.id.episodeFileName);
        binding.episodeFileName.setNextFocusLeftId(R.id.episodeReverse);
        binding.episodeFileName.setNextFocusRightId(R.id.episodeViewMode);
        binding.episodeViewMode.setNextFocusLeftId(R.id.episodeFileName);
        setupOverviewInteraction();
        if (Util.isMobile()) binding.headerTitle.setText("");
        else binding.headerTitle.setText(detailModeTitle());
        binding.headerTitle.setVisibility(Util.isMobile() || !isCinemaMode() ? View.VISIBLE : View.INVISIBLE);
        binding.title.setText(getNameText());
        binding.subtitle.setText("");
        binding.sourceValue.setText(getString(R.string.detail_source_current, getKeyText()));
        binding.overviewToggle.setVisibility(View.GONE);
        binding.play.setText(R.string.detail_play_now);
        binding.keep.setText(R.string.keep);
        lightTheme = resolveLightTheme();
        updateThemeModeButtonLabels();
        binding.keepTop.setVisibility(View.GONE);
        binding.rematchTop.setVisibility(View.GONE);
        binding.headerBar.setVisibility(Util.isMobile() ? View.VISIBLE : View.GONE);
        updateDetailThemeButtonVisibility();
        applyDetailTemplate();
        initFusionPlayer();
        binding.episodeEmpty.setText(R.string.detail_source_episode_empty);
        bindInitialArtwork();
        episodeAdapter = new TmdbEpisodeAdapter(new TmdbEpisodeAdapter.Listener() {
            @Override
            public void onItemClick(Episode episode) {
                selectInlineEpisode(episode);
            }

            @Override
            public void onItemLongClick(View anchor, Episode episode, int episodeNumber) {
                anchor.setPressed(false);
                showTmdbEpisodeDetail(episode, episodeNumber, binding.episodeContainer);
            }
        });
        episodeAdapter.setNativeEnhanced(true);
        episodeAdapter.setOnFocusChangeListener(this::onDetailEpisodeFocusChange);
        episodeAdapter.setOnKeyListener(this::onDetailEpisodeKey);
        castAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        creatorAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        episodePhotoAdapter = new TmdbPhotoAdapter(this::showPhotoDialog);
        posterAdapter = new TmdbPhotoAdapter((position, url) -> showPhotoDialog(position, url, detailTmdbPosters), true);
        relatedAdapter = new TmdbRailAdapter(this::openRelatedItem);
        relatedVideoAdapter = new TmdbVideoAdapter();
        relatedVideoAdapter.setOnItemClickListener(item -> TmdbVideoPlayback.play(this, item));
        personalTmdbAdapter = new TmdbRailAdapter(this::openRelatedItem);
        personalDoubanAdapter = new TmdbRailAdapter(this::openRelatedItem);
        personalAiAdapter = new TmdbRailAdapter(this::openRelatedItem);
        relatedAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "related"));
        personalTmdbAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "tmdb"));
        personalDoubanAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "douban"));
        personalAiAdapter.setOnItemLongClickListener(item -> onRecommendationLongClick(item, "ai"));
        personalAiAdapter.setOnItemFocusListener(this::showAiRecommendationReason);
        castAdapter.setCinema(isCinemaMode());
        creatorAdapter.setCinema(isCinemaMode());
        relatedAdapter.setCinema(isCinemaMode());
        personalTmdbAdapter.setCinema(isCinemaMode());
        personalDoubanAdapter.setCinema(isCinemaMode());
        personalAiAdapter.setCinema(isCinemaMode());
        setDetailAdaptersLight(resolveLightTheme());
        updateEpisodeLayoutManager();
        binding.episodeContainer.setItemAnimator(null);
        binding.episodeContainer.setHasFixedSize(false);
        binding.episodeContainer.setNestedScrollingEnabled(false);
        binding.episodeContainer.setAdapter(episodeAdapter);
        binding.episodePhotoList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.episodePhotoList.setNestedScrollingEnabled(false);
        binding.episodePhotoList.setAdapter(episodePhotoAdapter);
        binding.posterList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.posterList.setNestedScrollingEnabled(false);
        binding.posterList.setAdapter(posterAdapter);
        binding.castList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.castList.setNestedScrollingEnabled(false);
        binding.castList.setAdapter(castAdapter);
        binding.creatorList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.creatorList.setNestedScrollingEnabled(false);
        binding.creatorList.setAdapter(creatorAdapter);
        binding.relatedList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.relatedList.setNestedScrollingEnabled(false);
        binding.relatedList.setAdapter(relatedAdapter);
        binding.relatedVideoList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.relatedVideoList.setNestedScrollingEnabled(false);
        binding.relatedVideoList.setAdapter(relatedVideoAdapter);
        binding.personalTmdbList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.personalTmdbList.setNestedScrollingEnabled(false);
        binding.personalTmdbList.setAdapter(personalTmdbAdapter);
        binding.personalDoubanList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.personalDoubanList.setNestedScrollingEnabled(false);
        binding.personalDoubanList.setAdapter(personalDoubanAdapter);
        binding.personalAiList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.personalAiList.setNestedScrollingEnabled(false);
        binding.personalAiList.setAdapter(personalAiAdapter);
        applyDetailTheme();
    }

    private void initModeController() {
        // Phase 3: 三种模式全部接入 Controller（准备删除旧逻辑）
        DetailModeHost host = new DetailModeHost() {
            @Override
            public Context context() {
                return TmdbDetailActivity.this;
            }

            @Override
            public ViewBinding binding() {
                return binding;
            }
        };

        if (isFusionMode()) {
            modeController = new FusionDetailController(host);
        } else if (isPlayerMode()) {
            modeController = new PlayerDetailController(host);
        } else {
            modeController = new EnhancedDetailController(host);
        }

        modeController.bind();
        modeController.applyInitialLayout();
    }

    private void setupOverviewInteraction() {
        if (Util.isMobile()) {
            binding.overview.setOnClickListener(view -> toggleOverview());
            binding.overviewToggle.setOnClickListener(view -> toggleOverview());
            return;
        }
        binding.overview.setOnClickListener(null);
        binding.overview.setClickable(false);
        binding.overview.setFocusable(false);
        binding.overview.setFocusableInTouchMode(false);
        binding.overviewToggle.setOnClickListener(null);
        binding.overviewToggle.setClickable(false);
        binding.overviewToggle.setFocusable(false);
        binding.overviewToggle.setFocusableInTouchMode(false);
    }

    private void applySystemBarInsets() {
        if (headerBarBasePaddingTop < 0) headerBarBasePaddingTop = binding.headerBar.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(binding.root, (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            statusBarInsetTop = top;
            binding.headerBar.setPadding(
                    binding.headerBar.getPaddingLeft(),
                    headerBarBasePaddingTop + top,
                    binding.headerBar.getPaddingRight(),
                    binding.headerBar.getPaddingBottom()
            );
            if (!isCinemaMode()) TmdbDetailLayoutUtils.setHeightDp(binding.heroSpacer, defaultHeroSpacerHeightDp());
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.root);
    }

    private void applyDetailEdgeToEdge() {
        Window window = getWindow();
        if (window == null) return;
        WindowCompat.setDecorFitsSystemWindows(window, false);
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(window, window.getDecorView());
        boolean lightBars = lightTheme && !isFusionMode();
        insets.setAppearanceLightStatusBars(lightBars);
        insets.setAppearanceLightNavigationBars(lightBars);
    }

    private void initInlineLutLaunchers() {
        inlineLutDir = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
            LutStore.setUserDir(result.getData().getData(), result.getData().getFlags());
            Notify.show(R.string.lut_directory_selected);
            binding.lutQuick.refreshList();
            if (pendingInlineLutImport) {
                pendingInlineLutImport = false;
                chooseInlineLutFile();
            }
        });

        inlineLutFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
            String path = FileChooser.getPathFromUri(result.getData().getData());
            if (TextUtils.isEmpty(path)) {
                Notify.show(R.string.lut_import_failed);
                return;
            }
            detailTasks.submit(() -> {
                try {
                    LutPreset preset = LutStore.importFile(path);
                    App.post(() -> {
                        Notify.show(R.string.lut_imported);
                        binding.lutQuick.selectImported(preset, player(), binding.exo, this::onInlineLutChanged);
                    });
                } catch (Exception e) {
                    if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "import failed path=%s error=%s", path, e.getMessage());
                    App.post(() -> Notify.show(Notify.getError(R.string.lut_import_failed, e)));
                }
            });
        });
    }

    private void initFusionPlayer() {
        initInlineLutLaunchers();
        inlinePlayerUi = new VodPlayerUiController(new VodPlayerUiHost() {
            @Override
            public PlayerManager player() {
                return service() == null ? null : TmdbDetailActivity.this.player();
            }

            @Override
            public String osdTitle() {
                return getInlineOsdTitle();
            }

            @Override
            public boolean suppressPersistentOsd() {
                // 统一使用 OSD 系统，不再使用独立的 playerDisplay* 面板
                return false;
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
                setInlineDanmakuIcon(show);
            }

            @Override
            public void playPrevious() {
                TmdbDetailActivity.this.checkInlinePrev();
            }

            @Override
            public void playNext() {
                TmdbDetailActivity.this.checkInlineNext();
            }

            @Override
            public void showQuality() {
                TmdbDetailActivity.this.showInlineQuality();
            }

            @Override
            public void cycleParse() {
                TmdbDetailActivity.this.cycleInlineParse();
            }

            @Override
            public void changeSpeed() {
                TmdbDetailActivity.this.changeInlineSpeed();
            }

            @Override
            public boolean resetSpeed() {
                return TmdbDetailActivity.this.resetInlineSpeed();
            }

            @Override
            public void cycleScale() {
                TmdbDetailActivity.this.cycleInlineScale();
            }

            @Override
            public void showLut() {
                TmdbDetailActivity.this.onInlineLut();
            }

            @Override
            public void refreshPlayback() {
                TmdbDetailActivity.this.refreshInlinePlayback();
            }

            @Override
            public void changeSource() {
                TmdbDetailActivity.this.changeSource();
            }

            @Override
            public boolean openSourceSearch() {
                return TmdbDetailActivity.this.openGlobalSourceSearch();
            }

            @Override
            public void toggleRepeat() {
                TmdbDetailActivity.this.toggleInlineRepeat();
            }

            @Override
            public void showDisplay() {
                TmdbDetailActivity.this.showInlineDisplay();
            }

            @Override
            public void toggleDecode() {
                TmdbDetailActivity.this.toggleInlineDecode();
            }

            @Override
            public void togglePlayParams() {
                TmdbDetailActivity.this.toggleInlinePlayParams();
            }

            @Override
            public void showCodecCapability() {
                TmdbDetailActivity.this.showInlineCodecCapability();
            }

            @Override
            public void showTrack(View view) {
                TmdbDetailActivity.this.showInlineTrack(view);
            }

            @Override
            public boolean showSubtitle() {
                return TmdbDetailActivity.this.showInlineSubtitle();
            }

            @Override
            public void setOpeningFromPosition() {
                TmdbDetailActivity.this.setInlineOpeningFromPosition();
            }

            @Override
            public boolean resetOpening() {
                return TmdbDetailActivity.this.resetInlineOpening();
            }

            @Override
            public void setEndingFromPosition() {
                TmdbDetailActivity.this.setInlineEndingFromPosition();
            }

            @Override
            public boolean resetEnding() {
                return TmdbDetailActivity.this.resetInlineEnding();
            }

            @Override
            public void toggleDanmaku() {
                TmdbDetailActivity.this.toggleInlineDanmaku();
            }

            @Override
            public boolean onDanmakuLongClick() {
                return TmdbDetailActivity.this.onPlayerDanmakuLongClick();
            }

            @Override
            public void showChapter() {
                TmdbDetailActivity.this.showInlineTitle();
            }

            @Override
            public boolean showPlayerChoice() {
                return TmdbDetailActivity.this.showInlinePlayerChoice();
            }

            @Override
            public void showEpisodes() {
                TmdbDetailActivity.this.showInlineEpisodes();
            }

            @Override
            public void toggleFullscreen() {
                TmdbDetailActivity.this.toggleInlineFullscreen();
            }

            @Override
            public void cast() {
                TmdbDetailActivity.this.onInlineCast();
            }

            @Override
            public void showMediaInfo() {
                TmdbDetailActivity.this.onInlineInfo();
            }

            @Override
            public boolean onControlsTouch(View view, MotionEvent event) {
                return TmdbDetailActivity.this.onInlineControlTouch(view, event);
            }
        }, VodPlayerChrome.fromTmdbDetail(binding), this);
        inlineControlController = inlinePlayerUi.controlController();
        inlinePiP = inlinePlayerUi.pip();
        inlineClock = inlinePlayerUi.clock();
        inlineOsd = inlinePlayerUi.osd();
        inlineGestureDetector = PlayerGesture.create(this, binding.playerPanel, this);
        setupPlayerPanelFocusLayer();
        binding.playerPanel.setOnTouchListener(this::onInlineTouch);
        binding.playerPanel.setOnKeyListener(this::onInlinePanelKey);
        binding.playerPanel.setOnFocusChangeListener((view, focused) -> updatePlayerPanelFocus());
        binding.playerPanel.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (!inlinePiPSourceFrozen) updateInlinePiPSource(view);
            syncInlinePlayerToSpacer();
        });
        setupInlinePlayerSpacerSync();
        setupInlineControlFocus();
        setupInlineFocusNavigation();
        binding.playerAdFeedback.setOnClickListener(guarded(this::onInlineAdFeedback));
        binding.playerAdFeedback.setOnLongClickListener(view -> onInlineAdFeedbackLongPress());
        binding.playerMultiThreadProxy.setOnClickListener(guarded(this::showInlineMultiThreadProxy));
        binding.playerSearch.setOnClickListener(view -> openInlineSourceSearch());
        binding.playerSearch.setOnLongClickListener(view -> openGlobalSourceSearch());
        inlinePlayerUi.bindInlineActions();
        setupMobileInlineControl();
        hideInlineControls();
        updateInlineButtons(false);
        focusInlinePlayerPanel();
    }

    private void setupMobileInlineControl() {
        if (!Util.isMobile()) {
            binding.detailControlHost.setVisibility(View.GONE);
            return;
        }
        inflateMobileInlineControl();
        binding.playerControls.setVisibility(View.GONE);
        detailControlView(R.id.back, View.class).setOnClickListener(view -> onInlineBack());
        detailControlView(R.id.play, View.class).setOnClickListener(guarded(this::toggleInlinePlayback));
        detailControlView(R.id.prev, View.class).setOnClickListener(view -> checkInlinePrev());
        detailControlView(R.id.next, View.class).setOnClickListener(view -> checkInlineNext());
        detailControlView(R.id.fullscreen, View.class).setOnClickListener(guarded(this::toggleInlineFullscreen));
        detailControlView(R.id.cast, View.class).setOnClickListener(guarded(this::onInlineCast));
        detailControlView(R.id.info, View.class).setOnClickListener(guarded(this::onInlineInfo));
        detailControlView(R.id.keep, View.class).setOnClickListener(view -> onKeep());
        detailControlView(R.id.nightMode, View.class).setOnClickListener(guarded(this::toggleNightMode));
        detailControlView(R.id.setting, View.class).setOnClickListener(guarded(this::showInlineControlDialog));
        detailControlView(R.id.danmaku, View.class).setOnClickListener(guarded(this::toggleInlineDanmaku));
        detailControlView(R.id.lock, View.class).setOnClickListener(guarded(this::toggleInlineLock));
        detailControlView(R.id.rotate, View.class).setOnClickListener(guarded(this::rotateInlineFullscreen));
        detailControlView(R.id.pip, View.class).setOnClickListener(guarded(() -> enterInlinePiP(true)));
        detailActionView(R.id.change2, View.class).setOnClickListener(view -> changeSource());
        detailActionView(R.id.search, View.class).setOnClickListener(view -> openInlineSourceSearch());
        detailActionView(R.id.search, View.class).setOnLongClickListener(view -> openGlobalSourceSearch());
        detailActionView(R.id.actionFullscreen, View.class).setOnClickListener(guarded(this::toggleInlineFullscreen));
        detailActionView(R.id.player, View.class).setOnClickListener(guarded(this::showInlinePlayerChoice));
        detailActionView(R.id.player, View.class).setOnLongClickListener(view -> showInlinePlayerChoice());
        detailActionView(R.id.decode, View.class).setOnClickListener(guarded(this::toggleInlineDecode));
        detailActionView(R.id.playParams, View.class).setOnClickListener(guarded(this::toggleInlinePlayParams));
        detailActionView(R.id.multiThreadProxy, View.class).setOnClickListener(guarded(this::showInlineMultiThreadProxy));
        detailActionView(R.id.codecCapability, View.class).setOnClickListener(guarded(this::showInlineCodecCapability));
        detailActionView(R.id.lut, View.class).setOnClickListener(guarded(this::onInlineLut));
        detailActionView(R.id.speed, View.class).setOnClickListener(guarded(this::changeInlineSpeed));
        detailActionView(R.id.speed, View.class).setOnLongClickListener(view -> resetInlineSpeed());
        detailActionView(R.id.scale, View.class).setOnClickListener(guarded(this::cycleInlineScale));
        detailActionView(R.id.actionQuality, View.class).setOnClickListener(guarded(this::showInlineQuality));
        detailActionView(R.id.reset, View.class).setOnClickListener(guarded(this::refreshInlinePlayback));
        detailActionView(R.id.repeat, View.class).setOnClickListener(guarded(this::toggleInlineRepeat));
        detailActionView(R.id.text, View.class).setOnClickListener(guardedView(this::showInlineTrack));
        detailActionView(R.id.text, View.class).setOnLongClickListener(view -> showInlineSubtitle());
        detailActionView(R.id.audio, View.class).setOnClickListener(guardedView(this::showInlineTrack));
        detailActionView(R.id.video, View.class).setOnClickListener(guardedView(this::showInlineTrack));
        detailActionView(R.id.opening, View.class).setOnClickListener(guarded(this::setInlineOpeningFromPosition));
        detailActionView(R.id.opening, View.class).setOnLongClickListener(view -> resetInlineOpening());
        detailActionView(R.id.ending, View.class).setOnClickListener(guarded(this::setInlineEndingFromPosition));
        detailActionView(R.id.ending, View.class).setOnLongClickListener(view -> resetInlineEnding());
        detailActionView(R.id.danmaku, View.class).setOnClickListener(guarded(this::showInlineDanmaku));
        detailActionView(R.id.adFeedback, View.class).setOnClickListener(guarded(this::onInlineAdFeedback));
        detailActionView(R.id.adFeedback, View.class).setOnLongClickListener(view -> onInlineAdFeedbackLongPress());
        detailActionView(R.id.chapter, View.class).setOnClickListener(guarded(this::showInlineTitle));
        detailActionView(R.id.episodes, View.class).setOnClickListener(guarded(this::showInlineEpisodes));
        setupMobileInlineParse();
        detailControlRoot.setOnTouchListener(this::onInlineControlTouch);
        mNightModeLevel = PlayerSetting.getNightModeLevel();
        setNightMode();
    }

    private void setupMobileInlineParse() {
        RecyclerView parse = detailControlView(R.id.parse, RecyclerView.class);
        parse.setHasFixedSize(true);
        parse.setItemAnimator(null);
        parse.setAdapter(inlineParseAdapter = new InlineParseAdapter());
    }

    private void inflateMobileInlineControl() {
        if (!Util.isMobile() || detailControlRoot != null) return;
        detailControlRoot = getLayoutInflater().inflate(R.layout.view_control_vod_tmdb, binding.detailControlHost, false);
        binding.detailControlHost.removeAllViews();
        binding.detailControlHost.addView(detailControlRoot);
        detailActionRoot = detailControlRoot.findViewById(R.id.action);
        detailActionParent = (ViewGroup) detailActionRoot.getParent();
        detailActionLayoutParams = detailActionRoot.getLayoutParams();
        detailActionIndex = detailActionParent.indexOfChild(detailActionRoot);
    }

    private <T extends View> T detailControlView(int id, Class<T> type) {
        return type.cast(detailControlRoot.findViewById(id));
    }

    private <T extends View> T detailActionView(int id, Class<T> type) {
        return type.cast(detailActionRoot.findViewById(id));
    }

    private View inlineControlsView() {
        return Util.isMobile() ? binding.detailControlHost : binding.playerControls;
    }

    private CustomSeekView inlineSeek() {
        return Util.isMobile() && detailControlRoot != null ? detailControlView(R.id.seek, CustomSeekView.class) : binding.seek;
    }

    private void setupInlineFocusNavigation() {
        if (Util.isMobile()) return;
        View timeBar = inlineSeek().findViewById(R.id.timeBar);
        if (timeBar != null) {
            timeBar.setNextFocusUpId(R.id.playerNext);
            timeBar.setNextFocusRightId(R.id.timeBar);
        }
        binding.playerNext.setNextFocusDownId(R.id.timeBar);
        binding.playerFullscreenAction.setNextFocusDownId(R.id.timeBar);
        // 手动构建横向焦点链（按照布局顺序）
        setupHorizontalFocusChain();
        // 为所有控制栏按钮设置 nextFocusUp 指向自己，防止向上键导致焦点丢失
        binding.playerFullscreenAction.setNextFocusUpId(R.id.playerFullscreenAction);
        binding.playerNext.setNextFocusUpId(R.id.playerNext);
        binding.playerPrev.setNextFocusUpId(R.id.playerPrev);
        binding.playerEpisodes.setNextFocusUpId(R.id.playerEpisodes);
        binding.playerRefresh.setNextFocusUpId(R.id.playerRefresh);
        binding.playerChangeSource.setNextFocusUpId(R.id.playerChangeSource);
        binding.playerSearch.setNextFocusUpId(R.id.playerSearch);
        binding.playerExternal.setNextFocusUpId(R.id.playerExternal);
        binding.playerDecode.setNextFocusUpId(R.id.playerDecode);
        binding.playerPlayParams.setNextFocusUpId(R.id.playerPlayParams);
        binding.playerMultiThreadProxy.setNextFocusUpId(R.id.playerMultiThreadProxy);
        binding.playerCodecCapability.setNextFocusUpId(R.id.playerCodecCapability);
        binding.playerSpeed.setNextFocusUpId(R.id.playerSpeed);
        binding.playerScale.setNextFocusUpId(R.id.playerScale);
        binding.playerLut.setNextFocusUpId(R.id.playerLut);
        binding.playerQuality.setNextFocusUpId(R.id.playerQuality);
        binding.playerParse.setNextFocusUpId(R.id.playerParse);
        binding.playerTextTrack.setNextFocusUpId(R.id.playerTextTrack);
        binding.playerAudioTrack.setNextFocusUpId(R.id.playerAudioTrack);
        binding.playerVideoTrack.setNextFocusUpId(R.id.playerVideoTrack);
        binding.playerOpening.setNextFocusUpId(R.id.playerOpening);
        binding.playerEnding.setNextFocusUpId(R.id.playerEnding);
        binding.playerDanmaku.setNextFocusUpId(R.id.playerDanmaku);
        binding.playerChapter.setNextFocusUpId(R.id.playerChapter);
        binding.playerDisplay.setNextFocusUpId(R.id.playerDisplay);
        binding.playerRepeat.setNextFocusUpId(R.id.playerRepeat);

        if (isFusionMode()) {
            // 融合模式：播放窗口浮动在按钮行上方，playerPanelSpacer 仅占位(不可获焦)。
            // 若 spacer 可获焦会卡在 playerPanel↔spacer 焦点循环，导致方向键无法切到按钮。
            // 显式用 nextFocusUp 把按钮行与播放窗口串起来，方向键可正常往返。
            binding.playerPanelSpacer.setFocusable(false);
            binding.playerPanelSpacer.setFocusableInTouchMode(false);
            binding.playerPanelSpacer.setOnFocusChangeListener(null);
            binding.fusionActions.setNextFocusUpId(R.id.playerPanel);
        } else {
            // playerPanelSpacer 作为焦点桥梁：获得焦点时立即转给 playerPanel
            binding.playerPanelSpacer.setFocusable(true);
            binding.playerPanelSpacer.setFocusableInTouchMode(false);
            binding.playerPanelSpacer.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && !inlineFullscreen) binding.playerPanel.requestFocus();
            });
        }
    }

    private void setupHorizontalFocusChain() {
        // 按钮顺序：Next → Prev → Episodes → Refresh → ChangeSource → Fullscreen →
        // External → Decode → PlayParams → Speed → Scale → Quality → Lut → Parse →
        // TextTrack → AudioTrack → VideoTrack → Opening → Ending → Danmaku → AdFeedback →
        // Chapter → Display → Repeat

        View[] buttons = {
            binding.playerNext, binding.playerPrev, binding.playerEpisodes,
            binding.playerRefresh, binding.playerChangeSource, binding.playerSearch, binding.playerFullscreenAction,
            binding.playerExternal, binding.playerDecode, binding.playerPlayParams,
            binding.playerMultiThreadProxy, binding.playerCodecCapability,
            binding.playerSpeed, binding.playerScale, binding.playerQuality,
            binding.playerLut, binding.playerParse, binding.playerTextTrack,
            binding.playerAudioTrack, binding.playerVideoTrack, binding.playerOpening,
            binding.playerEnding, binding.playerDanmaku, binding.playerAdFeedback,
            binding.playerChapter,
            binding.playerDisplay, binding.playerRepeat
        };

        for (int i = 0; i < buttons.length; i++) {
            if (buttons[i] == null) continue;

            // 设置左焦点（指向前一个按钮）
            if (i > 0) {
                int leftId = buttons[i - 1].getId();
                buttons[i].setNextFocusLeftId(leftId);
            } else {
                // 第一个按钮左边没有按钮，设置为NO_ID
                buttons[i].setNextFocusLeftId(View.NO_ID);
            }

            // 设置右焦点（指向下一个按钮）
            if (i < buttons.length - 1) {
                int rightId = buttons[i + 1].getId();
                buttons[i].setNextFocusRightId(rightId);
            } else {
                // 最后一个按钮右边没有按钮，设置为NO_ID
                buttons[i].setNextFocusRightId(View.NO_ID);
            }
        }
    }

    private void setupInlineControlFocus() {
        setupInlineControl(binding.playerCast);
        setupInlineControl(binding.playerInfo);
        setupInlineControl(binding.playerFullscreenAction);
        setupInlineControl(binding.playerPrev);
        setupInlineControl(binding.playerNext);
        setupInlineControl(binding.playerExternal);
        setupInlineControl(binding.playerDecode);
        setupInlineControl(binding.playerPlayParams);
        setupInlineControl(binding.playerMultiThreadProxy);
        setupInlineControl(binding.playerCodecCapability);
        setupInlineControl(binding.playerSpeed);
        setupInlineControl(binding.playerScale);
        setupInlineControl(binding.playerLut);
        setupInlineControl(binding.playerRefresh);
        setupInlineControl(binding.playerChangeSource);
        setupInlineControl(binding.playerSearch);
        setupInlineControl(binding.playerRepeat);
        setupInlineControl(binding.playerDisplay);
        setupInlineControl(binding.playerQuality);
        setupInlineControl(binding.playerParse);
        setupInlineControl(binding.playerTextTrack);
        setupInlineControl(binding.playerAudioTrack);
        setupInlineControl(binding.playerVideoTrack);
        setupInlineControl(binding.playerOpening);
        setupInlineControl(binding.playerEnding);
        setupInlineControl(binding.playerDanmakuToggle);
        setupInlineControl(binding.playerDanmaku);
        setupInlineControl(binding.playerChapter);
        setupInlineControl(binding.playerEpisodes);
        setupInlineControlColors();
    }

    private void setupInlineControlColors() {
        // 设置所有控制按钮的默认文字颜色为白色
        int white = 0xFFFFFFFF;
        binding.playerNext.setTextColor(white);
        binding.playerPrev.setTextColor(white);
        binding.playerEpisodes.setTextColor(white);
        binding.playerRefresh.setTextColor(white);
        binding.playerChangeSource.setTextColor(white);
        binding.playerSearch.setTextColor(white);
        binding.playerFullscreenAction.setTextColor(white);
        binding.playerExternal.setTextColor(white);
        binding.playerDecode.setTextColor(white);
        binding.playerPlayParams.setTextColor(white);
        binding.playerMultiThreadProxy.setTextColor(white);
        binding.playerCodecCapability.setTextColor(white);
        binding.playerSpeed.setTextColor(white);
        binding.playerScale.setTextColor(white);
        binding.playerLut.setTextColor(white);
        binding.playerRepeat.setTextColor(white);
        binding.playerDisplay.setTextColor(white);
        binding.playerQuality.setTextColor(white);
        binding.playerParse.setTextColor(white);
        binding.playerTextTrack.setTextColor(white);
        binding.playerAudioTrack.setTextColor(white);
        binding.playerVideoTrack.setTextColor(white);
        binding.playerOpening.setTextColor(white);
        binding.playerEnding.setTextColor(white);
        // playerDanmakuToggle和playerDanmaku是ImageView，不设置textColor
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
        if (!isInlinePlayerMode() || inlineGestureDetector == null) return false;
        inlineGestureDetector.onTouchEvent(event);
        return true;
    }

    @Override
    public void onSeeking(long time) {
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return;
        inlinePauseInfo = false;
        binding.gestureAction.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        binding.gestureTime.setText(player().getPositionTime(time));
        binding.gestureDuration.setText(player().getDurationTime());
        binding.gestureSeek.setVisibility(View.VISIBLE);
        hideInlineControls();
        updateInlineDisplayPanel();
    }

    private void showInlinePauseInfo() {
        inlinePauseInfo = true;
        binding.gestureAction.setImageResource(R.drawable.ic_widget_play);
        binding.gestureTime.setText(player().getPositionTime(0));
        binding.gestureDuration.setText(player().getDurationTime());
        binding.gestureSeek.setVisibility(View.VISIBLE);
        binding.gestureSeek.bringToFront();
        updateInlineDisplayPanel();
    }

    private void syncInlinePauseInfo(boolean playing) {
        if (binding == null) return;
        if (shouldShowInlinePauseInfo(playing)) showInlinePauseInfo();
        else hideInlinePauseInfo();
    }

    private boolean shouldShowInlinePauseInfo(boolean playing) {
        return Util.isLeanback()
                && inlineFullscreen
                && inlineStarted
                && isInlinePlayerMode()
                && service() != null
                && player() != null
                && !player().isEmpty()
                && !playing
                && isPaused();
    }

    private void hideInlinePauseInfo() {
        inlinePauseInfo = false;
        binding.gestureSeek.setVisibility(View.GONE);
        updateInlineDisplayPanel();
    }

    @Override
    public void onSeekEnd(long time) {
        if (!isInlinePlayerMode() || controller() == null || service() == null || player() == null || player().isEmpty()) return;
        seekTo(time);
    }

    @Override
    public void onSpeedUp() {
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty() || !player().isPlaying()) return;
        inlineGestureSpeed = player().getSpeed();
        binding.gestureSpeed.setVisibility(View.VISIBLE);
        binding.gestureSpeed.startAnimation(ResUtil.getAnim(R.anim.forward));
        setInlineSpeed(PlayerSetting.getSpeed());
        hideInlineControls();
    }

    @Override
    public void onSpeedEnd() {
        binding.gestureSpeed.clearAnimation();
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return;
        float speed = history == null ? inlineGestureSpeed : getInlinePlaybackSpeed();
        setInlineSpeed(speed);
    }

    @Override
    public void onBright(int progress) {
        binding.gestureBright.setVisibility(View.VISIBLE);
        binding.gestureBrightProgress.setProgress(progress);
        if (progress < 35) binding.gestureBrightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) binding.gestureBrightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else binding.gestureBrightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    private void toggleNightMode() {
        mNightModeLevel = (mNightModeLevel + 1) % 4;
        PlayerSetting.putNightModeLevel(mNightModeLevel);
        setNightMode();
    }

    private void setNightMode() {
        if (binding == null || binding.playerPanel == null) return;
        if (detailControlRoot != null) {
            ImageView nightIcon = detailControlView(R.id.nightMode, ImageView.class);
            if (nightIcon != null) {
                switch (mNightModeLevel) {
                    case PlayerSetting.NIGHT_MODE_OFF:
                        nightIcon.setImageResource(R.drawable.ic_control_night_mode_off);
                        break;
                    case PlayerSetting.NIGHT_MODE_LOW:
                        nightIcon.setImageResource(R.drawable.ic_control_night_mode_low);
                        break;
                    case PlayerSetting.NIGHT_MODE_MEDIUM:
                        nightIcon.setImageResource(R.drawable.ic_control_night_mode_medium);
                        break;
                    case PlayerSetting.NIGHT_MODE_HIGH:
                        nightIcon.setImageResource(R.drawable.ic_control_night_mode_high);
                        break;
                }
            }
        }
        if (mNightModeLevel == PlayerSetting.NIGHT_MODE_OFF) {
            if (mNightModeOverlay != null) {
                binding.playerPanel.removeView(mNightModeOverlay);
                mNightModeOverlay = null;
            }
            return;
        }
        if (mNightModeOverlay == null) {
            mNightModeOverlay = new View(this);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            mNightModeOverlay.setLayoutParams(params);
            mNightModeOverlay.setClickable(false);
            mNightModeOverlay.setFocusable(false);
            binding.playerPanel.addView(mNightModeOverlay);
        }
        int alpha = switch (mNightModeLevel) {
            case PlayerSetting.NIGHT_MODE_LOW -> (int) (255 * 0.3f);
            case PlayerSetting.NIGHT_MODE_MEDIUM -> (int) (255 * 0.5f);
            case PlayerSetting.NIGHT_MODE_HIGH -> (int) (255 * 0.7f);
            default -> 0;
        };
        mNightModeOverlay.setBackgroundColor(Color.argb(alpha, 0, 0, 0));
    }

    @Override
    public void onVolume(int progress) {
        binding.gestureVolume.setVisibility(View.VISIBLE);
        binding.gestureVolumeProgress.setProgress(progress);
        if (progress < 35) binding.gestureVolumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) binding.gestureVolumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else binding.gestureVolumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (!inlineStarted || !isInlinePlayerMode() || service() == null || player() == null || player().isEmpty() || selectedFlag == null || selectedFlag.getEpisodes() == null) return;
        if (selectedFlag.getEpisodes().size() == 1) refreshInlinePlayback();
        else checkInlineNext();
    }

    @Override
    public void onFlingDown() {
        if (!inlineStarted || !isInlinePlayerMode() || service() == null || player() == null || player().isEmpty() || selectedFlag == null || selectedFlag.getEpisodes() == null) return;
        if (selectedFlag.getEpisodes().size() == 1) refreshInlinePlayback();
        else checkInlinePrev();
    }

    @Override
    public void onSingleTap() {
        toggleInlineControls();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) {
            showInlineControls(true, false);
        } else if (!inlineFullscreen) {
            enterInlineFullscreen();
        } else if (inlineStarted) {
            toggleInlinePlayback();
        } else {
            toggleInlineControls();
        }
    }

    @Override
    public void onTouchEnd() {
        if (inlinePauseInfo) {
            hideInlineTransientGestureViews();
            binding.gestureSeek.setVisibility(View.VISIBLE);
            binding.gestureSeek.bringToFront();
            return;
        }
        hideInlineGestureOverlays();
    }

    private void hideInlineGestureOverlays() {
        if (binding == null) return;
        inlinePauseInfo = false;
        hideInlineGestureViews();
        updateInlineDisplayPanel();
    }

    private void hideInlineGestureViews() {
        binding.gestureSeek.setVisibility(View.GONE);
        hideInlineTransientGestureViews();
    }

    private void hideInlineTransientGestureViews() {
        binding.gestureSpeed.setVisibility(View.GONE);
        binding.gestureBright.setVisibility(View.GONE);
        binding.gestureVolume.setVisibility(View.GONE);
    }

    private boolean onInlineControlTouch(View view, MotionEvent event) {
        if (isInlineControlsVisible()) touchInlineControls();
        // 控制层显示时空白区域不会稳定冒泡到 playerPanel，直接继续播放器手势序列。
        return onInlineTouch(view, event);
    }

    private boolean onInlinePanelKey(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isEnterKey(event)) return false;
        if (KeyUtil.isActionUp(event)) onInlinePanelConfirm();
        return true;
    }

    private void onInlinePanelConfirm() {
        if (!isInlinePlayerMode()) return;
        if (isLock() && inlineFullscreen) {
            showInlineControls(true, false);
        } else if (isInlineControlsVisible()) {
            hideInlineControls();
        } else if (!inlineFullscreen) {
            enterInlineFullscreenOrShowControlsOnConfirm();
        } else {
            showInlineControls(true);
        }
    }

    private void enterInlineFullscreenOrShowControlsOnConfirm() {
        if (Util.isLeanback() && canEnterInlineFullscreenOnConfirm()) {
            enterInlineFullscreen();
            return;
        }
        showInlineControls(true);
    }

    private boolean canEnterInlineFullscreenOnConfirm() {
        return !inlineFullscreen && !inlinePiPLayout && !isInPictureInPictureMode();
    }

    private void cycleThemeMode() {
        detailThemeMode = Setting.nextTmdbDetailTheme(detailThemeMode);
        Setting.putTmdbDetailTheme(detailThemeMode);
        applyDetailTheme();
        refreshDetailThemeDynamicViews();
    }

    private void applyDetailTheme() {
        lightTheme = resolveLightTheme();
        applyDetailEdgeToEdge();
        ThemeColors colors = currentThemeColors();
        applyBackdropSurface(colors);
        binding.backdropFill.setAlpha(backdropSlideAlpha());
        binding.backdrop.setAlpha(backdropSlideAlpha());
        binding.backdropShade.setBackground(isCinemaMode() ? cinemaBackdropShade() : TmdbDetailLayoutUtils.colorDrawable(colors.backdropShade));
        setCard(binding.contentPanel, colors.panel, colors.line);
        setPlayerCard(colors);
        setCard(binding.tmdbPanel, colors.panel, colors.line);
        applyTemplateCardChrome(colors);
        tintTextTree(binding.getRoot(), colors);
        styleDetailRatingChips();
        setDetailActionButton(binding.keep, colors);
        setDetailActionButton(binding.keepTop, colors);
        setDetailActionButton(binding.keepFusion, colors);
        setDetailActionButton(binding.rematch, colors);
        setDetailActionButton(binding.rematchTop, colors);
        setDetailActionButton(binding.rematchFusion, colors);
        setDetailActionButton(binding.changeSource, colors);
        setDetailActionButton(binding.changeSourceDetail, colors);
        setDetailActionButton(binding.themeModeTop, colors);
        setDetailActionButton(binding.themeMode, colors);
        setDetailActionButton(binding.themeModeDetail, colors);
        setEpisodeTitleButton(binding.episodeTitle, colors);
        setEpisodeToolButton(binding.episodeReverse, colors);
        setEpisodeToolButton(binding.episodeViewMode, colors);
        setEpisodeToolButton(binding.episodeFileName, colors);
        setButton(binding.play, colors.play, colors.play, 0xFFFFFFFF);
        binding.headerTitle.setTextColor(colors.primary);
        binding.title.setTextColor(colors.primary);
        binding.subtitle.setTextColor(colors.secondary);
        binding.overview.setTextColor(colors.body);
        binding.overviewToggle.setTextColor(colors.accent);
        binding.episodeEmpty.setTextColor(colors.secondary);
        binding.tmdbStatus.setTextColor(colors.secondary);
        binding.personalAiReason.setTextColor(isCinemaMode() ? 0xE6FFFFFF : colors.secondary);
        if (isCinemaMode()) binding.personalAiReason.setShadowLayer(3f, 0f, 1.5f, 0xCC000000);
        else binding.personalAiReason.setShadowLayer(0f, 0f, 0f, 0x00000000);
        tintTmdbSectionTitles(colors);
        styleSourceValue();
        updateThemeModeButtonLabels();
        updateDetailThemeButtonVisibility();
        tintInlineGestureOverlay();
        if (isInlinePlayerMode()) {
            binding.playerError.setTextColor(0xFFFFFFFF);
            tintInlineControl(inlineControlsView());
            tintInlineDisplay();
            tintInlineLoading();
        }
        if (episodeAdapter != null) {
            episodeAdapter.setTheme(lightTheme, colors.accent);
        }
        if (episodePhotoAdapter != null) episodePhotoAdapter.setLight(lightTheme);
        if (posterAdapter != null) posterAdapter.setLight(lightTheme);
        setDetailAdaptersLight(lightTheme);
        if (isCinemaMode()) scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    private void styleSourceValue() {
        int titleColor = binding.flagTitle.getCurrentTextColor();
        binding.sourceValue.setAlpha(1f);
        binding.sourceValue.setTextColor(titleColor);
        binding.sourceValue.setLinkTextColor(titleColor);
        binding.sourceValue.setTextSize(TypedValue.COMPLEX_UNIT_PX, binding.flagTitle.getTextSize());
        binding.sourceValue.setTypeface(binding.flagTitle.getTypeface());
    }

    private void setDetailAdaptersLight(boolean light) {
        if (castAdapter != null) castAdapter.setLight(light);
        if (creatorAdapter != null) creatorAdapter.setLight(light);
        if (relatedAdapter != null) relatedAdapter.setLight(light);
        if (personalTmdbAdapter != null) personalTmdbAdapter.setLight(light);
        if (personalDoubanAdapter != null) personalDoubanAdapter.setLight(light);
        if (personalAiAdapter != null) personalAiAdapter.setLight(light);
    }

    private void tintTmdbSectionTitles(ThemeColors colors) {
        TextView[] titles = {
                binding.flagTitle,
                binding.episodeLabel,
                binding.episodePhotoTitle,
                binding.posterTitle,
                binding.castTitle,
                binding.creatorTitle,
                binding.relatedTitle,
                binding.relatedVideoTitle,
                binding.personalTmdbTitle,
                binding.personalDoubanTitle,
                binding.personalAiTitle,
                binding.externalLinksTitle
        };
        int color = isCinemaMode() ? 0xFFFFFFFF : colors.primary;
        for (TextView title : titles) {
            title.setTextColor(color);
            if (isCinemaMode()) title.setShadowLayer(3f, 0f, 1.5f, 0xCC000000);
            else title.setShadowLayer(0f, 0f, 0f, 0x00000000);
        }
        updateTmdbSeasonActionVisibility();
    }

    private void updateDetailThemeButtonVisibility() {
        if (binding == null) return;
        boolean mobile = Util.isMobile();
        boolean pictureInPicture = isInPictureInPictureMode();
        boolean showMobileButton = DetailThemeVisibility.showMobileThemeButton(mobile, inlineFullscreen, inlinePiPLayout, pictureInPicture);
        boolean showLargeScreenButton = DetailThemeVisibility.showLargeScreenThemeButton(mobile, inlineFullscreen, inlinePiPLayout, pictureInPicture);
        boolean fusionMode = isFusionMode();
        boolean playbackPage = isAutoPlayMode() || detailPlayerActive;
        // 手机版也使用底部一排的主题按钮（themeModeDetail），不再使用右上角浮动按钮（themeModeTop）
        binding.themeModeTop.setVisibility(View.GONE);
        // 融合模式：主题按钮在 fusionActions 排；其他模式：在 detail 排
        binding.themeMode.setVisibility(playbackPage ? View.GONE : (fusionMode ? (showMobileButton || showLargeScreenButton ? View.VISIBLE : View.GONE) : (showLargeScreenButton ? View.VISIBLE : View.GONE)));
        binding.themeModeDetail.setVisibility(fusionMode || playbackPage ? View.GONE : (showMobileButton || showLargeScreenButton ? View.VISIBLE : View.GONE));
    }

    private void applyTemplateCardChrome(ThemeColors colors) {
        if (isCinemaMode()) {
            binding.contentPanel.setCardBackgroundColor(0x00000000);
            binding.contentPanel.setStrokeWidth(0);
            binding.tmdbPanel.setCardBackgroundColor(0x00000000);
            binding.tmdbPanel.setStrokeWidth(0);
        } else {
            binding.contentPanel.setCardBackgroundColor(colors.panel);
            binding.contentPanel.setStrokeColor(colors.line);
            binding.contentPanel.setStrokeWidth(ResUtil.dp2px(1));
            binding.tmdbPanel.setCardBackgroundColor(colors.panel);
            binding.tmdbPanel.setStrokeColor(colors.line);
            binding.tmdbPanel.setStrokeWidth(ResUtil.dp2px(1));
        }
    }

    private void applyDetailTemplate() {
        if (isCinemaMode()) applyCinemaDetailTemplate();
        else applyDefaultDetailTemplate();
    }

    private void applyDefaultDetailTemplate() {
        TmdbDetailLayoutUtils.setPaddingDp(binding.pageContent, 0, 0, 0, 28);
        TmdbDetailLayoutUtils.setHeightDp(binding.heroSpacer, defaultHeroSpacerHeightDp());
        TmdbDetailLayoutUtils.setWidthMatch(binding.contentPanel);
        TmdbDetailLayoutUtils.setWidthMatch(binding.tmdbSection);
        TmdbDetailLayoutUtils.setMarginsDp(binding.contentPanel, 16, 0, 16, 0);
        TmdbDetailLayoutUtils.setMarginsDp(binding.playerPanel, 16, isFusionMode() ? 22 : 14, 16, isFusionMode() ? 20 : 16);
        TmdbDetailLayoutUtils.setMarginsDp(binding.tmdbSection, 16, 16, 16, 0);
        binding.contentPanel.setRadius(ResUtil.dp2px(20));
        binding.tmdbPanel.setRadius(ResUtil.dp2px(20));
        TmdbDetailLayoutUtils.setPaddingDp(binding.contentInner, 16, 16, 16, 16);
        setTmdbPanelInnerPaddingDp(16, 16, 16, 16);
        binding.heroRow.setOrientation(LinearLayout.HORIZONTAL);
        binding.heroRow.setGravity(0);
        binding.posterCard.setVisibility(View.VISIBLE);
        LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(ResUtil.dp2px(112), ResUtil.dp2px(160));
        binding.posterCard.setLayoutParams(posterParams);
        binding.posterCard.setRadius(ResUtil.dp2px(16));
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        infoParams.setMarginStart(ResUtil.dp2px(14));
        binding.detailInfo.setLayoutParams(infoParams);
        TmdbDetailLayoutUtils.setWidthMatch(binding.detailActions);
        TmdbDetailLayoutUtils.setWidthMatch(binding.flagHeader);
        TmdbDetailLayoutUtils.setWidthMatch(binding.flagScroll);
        binding.title.setTextSize(28f);
        binding.overview.setTextSize(13f);
        TmdbDetailLayoutUtils.setHeightDp(binding.episodePhotoList, 124);
        TmdbDetailLayoutUtils.setHeightDp(binding.castList, 180);
        TmdbDetailLayoutUtils.setHeightDp(binding.creatorList, 180);
        TmdbDetailLayoutUtils.setHeightDp(binding.relatedList, 262);
        TmdbDetailLayoutUtils.setHeightDp(binding.relatedVideoList, 180);
        TmdbDetailLayoutUtils.setHeightDp(binding.personalTmdbList, 262);
        TmdbDetailLayoutUtils.setHeightDp(binding.personalDoubanList, 262);
        TmdbDetailLayoutUtils.setHeightDp(binding.personalAiList, 262);
    }

    private int defaultHeroSpacerHeightDp() {
        if (isFusionMode()) return 0;
        if (!Util.isMobile()) return 102;
        int insetDp = Math.round(statusBarInsetTop / getResources().getDisplayMetrics().density);
        return Math.max(72, 102 - Math.min(insetDp, 30));
    }

    private void applyCinemaDetailTemplate() {
        boolean compact = isCompactWidth();
        int side = ResUtil.dp2px(compact ? 18 : 56);
        int topWidth = compact ? ViewGroup.LayoutParams.MATCH_PARENT : Math.min(ResUtil.dp2px(760), (int) (getResources().getDisplayMetrics().widthPixels * 0.54f));
        TmdbDetailLayoutUtils.setPaddingDp(binding.pageContent, 0, 0, 0, 44);
        TmdbDetailLayoutUtils.setHeightDp(binding.heroSpacer, compact ? 50 : 28);
        TmdbDetailLayoutUtils.setWidthMatch(binding.contentPanel);
        TmdbDetailLayoutUtils.setWidthMatch(binding.tmdbSection);
        TmdbDetailLayoutUtils.setMarginsPx(binding.contentPanel, side, compact ? 6 : 18, side, 0);
        TmdbDetailLayoutUtils.setMarginsPx(binding.tmdbSection, side, compact ? 22 : 24, side, 0);
        binding.contentPanel.setRadius(0);
        binding.tmdbPanel.setRadius(0);
        TmdbDetailLayoutUtils.setPaddingDp(binding.contentInner, 0, 0, 0, 0);
        setTmdbPanelInnerPaddingDp(0, 0, 0, 0);
        binding.heroRow.setOrientation(LinearLayout.HORIZONTAL);
        binding.heroRow.setGravity(compact ? 0 : android.view.Gravity.CENTER_VERTICAL);
        binding.posterCard.setVisibility(compact ? View.VISIBLE : View.GONE);
        if (compact) {
            LinearLayout.LayoutParams posterParams = new LinearLayout.LayoutParams(ResUtil.dp2px(92), ResUtil.dp2px(138));
            binding.posterCard.setLayoutParams(posterParams);
            binding.posterCard.setRadius(ResUtil.dp2px(14));
        }
        LinearLayout.LayoutParams infoParams = compact
                ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                : new LinearLayout.LayoutParams(topWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.setMarginStart(compact ? ResUtil.dp2px(14) : 0);
        binding.detailInfo.setLayoutParams(infoParams);
        if (compact) TmdbDetailLayoutUtils.setWidthMatch(binding.detailActions);
        else TmdbDetailLayoutUtils.setWidthPx(binding.detailActions, topWidth);
        TmdbDetailLayoutUtils.setWidthMatch(binding.flagHeader);
        TmdbDetailLayoutUtils.setWidthMatch(binding.flagScroll);
        binding.title.setTextSize(compact ? 32f : 44f);
        binding.subtitle.setTextSize(compact ? 13f : 14f);
        binding.overview.setTextSize(compact ? 14.5f : 16f);
        binding.overview.setLineSpacing(ResUtil.dp2px(compact ? 4 : 3), 1f);
        TmdbDetailLayoutUtils.setHeightDp(binding.episodePhotoList, compact ? 128 : 160);
        TmdbDetailLayoutUtils.setHeightDp(binding.castList, compact ? 90 : 90);
        TmdbDetailLayoutUtils.setHeightDp(binding.creatorList, compact ? 90 : 90);
        TmdbDetailLayoutUtils.setHeightDp(binding.relatedList, compact ? 160 : 160);
        TmdbDetailLayoutUtils.setHeightDp(binding.relatedVideoList, compact ? 128 : 160);
        TmdbDetailLayoutUtils.setHeightDp(binding.personalTmdbList, compact ? 160 : 160);
        TmdbDetailLayoutUtils.setHeightDp(binding.personalDoubanList, compact ? 160 : 160);
        TmdbDetailLayoutUtils.setHeightDp(binding.personalAiList, compact ? 160 : 160);
        if (castAdapter != null) castAdapter.setCinema(true);
        if (creatorAdapter != null) creatorAdapter.setCinema(true);
        if (relatedAdapter != null) relatedAdapter.setCinema(true);
        if (personalTmdbAdapter != null) personalTmdbAdapter.setCinema(true);
        if (personalDoubanAdapter != null) personalDoubanAdapter.setCinema(true);
        if (personalAiAdapter != null) personalAiAdapter.setCinema(true);
    }

    private boolean isCompactWidth() {
        return getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private void setTmdbPanelInnerPaddingDp(int left, int top, int right, int bottom) {
        if (binding.tmdbPanel.getChildCount() > 0) {
            View inner = binding.tmdbPanel.getChildAt(0);
            TmdbDetailLayoutUtils.setPaddingDp(inner, left, top, right, bottom);
        }
    }

    private Drawable cinemaBackdropShade() {
        if (lightTheme) return cinemaLightBackdropShade();
        boolean compact = isCompactWidth();
        GradientDrawable horizontal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, compact ? new int[]{
                0xEC090B0F, 0xD6090B0F, 0x78090B0F, 0x42090B0F, 0x96090B0F
        } : new int[]{
                0xF2090B0F, 0xDC090B0F, 0x82090B0F, 0x48090B0F, 0xA6090B0F
        });
        GradientDrawable vertical = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, compact ? new int[]{
                0x10090B0F, 0x26090B0F, 0xA6090B0F, 0xE6090B0F
        } : new int[]{
                0x12090B0F, 0x2D090B0F, 0xB8090B0F, 0xF0090B0F
        });
        return new LayerDrawable(new Drawable[]{horizontal, vertical});
    }

    private Drawable cinemaLightBackdropShade() {
        boolean compact = isCompactWidth();
        GradientDrawable horizontal = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, compact ? new int[]{
                0xB8F4F7FA, 0x99F4F7FA, 0x55F4F7FA, 0x24F4F7FA, 0x70F4F7FA
        } : new int[]{
                0x99F4F7FA, 0x80F4F7FA, 0x40F4F7FA, 0x1AF4F7FA, 0x55F4F7FA
        });
        GradientDrawable vertical = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, compact ? new int[]{
                0x0AF4F7FA, 0x18F4F7FA, 0x55F4F7FA, 0x99F4F7FA
        } : new int[]{
                0x04F4F7FA, 0x0FF4F7FA, 0x3DF4F7FA, 0x70F4F7FA
        });
        return new LayerDrawable(new Drawable[]{horizontal, vertical});
    }

    private void tintInlineControl(View view) {
        if (view instanceof TextView textView) textView.setTextColor(0xFFFFFFFF);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintInlineControl(group.getChildAt(i));
    }

    private void tintInlineDisplay() {
        binding.playerDisplayTitle.setTextColor(0xFFFFFFFF);
        binding.playerDisplaySize.setTextColor(0xFFFFFFFF);
        binding.playerDisplayClock.setTextColor(0xFFFFFFFF);
        binding.playerDisplayPosition.setTextColor(0xFFFFFFFF);
        binding.playerDisplayTraffic.setTextColor(0xFFFFFFFF);
    }

    private void tintInlineLoading() {
        binding.playerProgressTraffic.setTextColor(0xFFFFFFFF);
        binding.playerProgressTraffic.setAlpha(1f);
    }

    private void tintInlineGestureOverlay() {
        tintInlineControl(binding.gestureSeek);
        tintInlineControl(binding.gestureBright);
        tintInlineControl(binding.gestureVolume);
        binding.gestureBrightProgress.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        binding.gestureBrightProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
        binding.gestureVolumeProgress.setProgressTintList(ColorStateList.valueOf(0xFFFFFFFF));
        binding.gestureVolumeProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x66FFFFFF));
    }

    private boolean resolveLightTheme() {
        return Setting.resolveTmdbDetailLightTheme(detailThemeMode, isSystemNight());
    }

    private boolean isSystemNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private int themeModeLabel() {
        return lightTheme ? R.string.detail_theme_light : R.string.detail_theme_dark;
    }

    private void updateThemeModeButtonLabels() {
        binding.themeModeTop.setText(themeModeLabel());
        binding.themeMode.setText(themeModeLabel());
        binding.themeModeDetail.setText(themeModeLabel());
    }

    private void setCard(MaterialCardView card, int background, int stroke) {
        card.setCardBackgroundColor(background);
        card.setStrokeColor(stroke);
    }

    private void setPlayerCard(ThemeColors colors) {
        if (!isInlinePlayerMode()) return;
        binding.playerPanel.setCardBackgroundColor(0xFF000000);
        binding.playerPanel.setRadius(inlineFullscreen || inlinePiPLayout ? 0 : ResUtil.dp2px(20));
        updatePlayerPanelFocus(colors);
    }

    private void updatePlayerPanelFocus() {
        updatePlayerPanelFocus(lightTheme ? ThemeColors.light() : ThemeColors.dark());
    }

    private void updatePlayerPanelFocus(ThemeColors colors) {
        if (!isInlinePlayerMode()) return;
        if (inlineFullscreen || inlinePiPLayout) {
            binding.playerPanel.setStrokeColor(0x00000000);
            binding.playerPanel.setStrokeWidth(0);
            return;
        }
        boolean focused = binding.playerPanel.hasFocus() && !hasFocusedChild(inlineControlsView());
        binding.playerPanel.setStrokeColor(focused ? FOCUS_STROKE : colors.line);
        binding.playerPanel.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
    }

    private boolean isLeanbackInlinePlayerPanel() {
        return Util.isLeanback() && (isFusionMode() || isPlayerMode());
    }

    private void setupPlayerPanelFocusLayer() {
        if (!isLeanbackInlinePlayerPanel()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) binding.playerPanel.setDefaultFocusHighlightEnabled(false);
        binding.playerPanel.setRippleColor(ColorStateList.valueOf(0x00000000));
    }

    private void focusInlinePlayerPanel() {
        if (!isInlinePlayerMode()) return;
        binding.playerPanel.post(() -> {
            if (!isFinishing() && binding.playerPanel.getVisibility() == View.VISIBLE && !inlineFullscreen) binding.playerPanel.requestFocus();
        });
    }

    private void setDetailActionButton(MaterialButton button, ThemeColors colors) {
        if (lightTheme) {
            int[][] states = new int[][]{
                    new int[]{android.R.attr.state_selected},
                    new int[]{}
            };
            button.setAlpha(1f);
            button.setBackgroundTintList(new ColorStateList(states, new int[]{colors.chipActive, 0xFFFFFFFF}));
            button.setTextColor(new ColorStateList(states, new int[]{colors.primary, colors.primary}));
            button.setIconTint(new ColorStateList(states, new int[]{colors.primary, colors.primary}));
            button.setOnFocusChangeListener(null);
            applyButtonFocus(button, colors.lineStrong, button.hasFocus());
            button.setOnFocusChangeListener((view, focused) -> applyButtonFocus(button, colors.lineStrong, focused));
            return;
        }
        setButton(button, colors.control, colors.line, colors.primary);
    }

    private void setButton(MaterialButton button, int background, int stroke, int text) {
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(text);
        button.setIconTint(ColorStateList.valueOf(text));
        button.setOnFocusChangeListener(null);
        applyButtonFocus(button, stroke, button.hasFocus());
        button.setOnFocusChangeListener((view, focused) -> applyButtonFocus(button, stroke, focused));
    }

    private void applyButtonFocus(MaterialButton button, int stroke, boolean focused) {
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : stroke));
    }

    private void setEpisodeToolButton(MaterialButton button, ThemeColors colors) {
        button.setSelected(false);
        button.setActivated(false);
        button.setRippleColor(ColorStateList.valueOf(0x00000000));
        button.setBackgroundTintList(ColorStateList.valueOf(colors.control));
        button.setTextColor(colors.primary);
        button.setIconTint(ColorStateList.valueOf(colors.primary));
        button.setOnFocusChangeListener(null);
        applyEpisodeToolButtonsFocus();
        button.setOnFocusChangeListener((view, focused) -> applyEpisodeToolButtonsFocus());
    }

    private void setEpisodeTitleButton(MaterialButton button, ThemeColors colors) {
        button.setSelected(false);
        button.setActivated(false);
        button.setRippleColor(ColorStateList.valueOf(Color.TRANSPARENT));
        button.setOnFocusChangeListener(null);
        applyEpisodeTitleButtonFocus(button, colors);
        button.setOnFocusChangeListener((view, focused) -> applyEpisodeToolButtonsFocus());
    }

    private void applyEpisodeToolButtonsFocus() {
        if (binding == null) return;
        ThemeColors colors = currentThemeColors();
        applyEpisodeTitleButtonFocus(binding.episodeTitle, colors);
        applyEpisodeToolButtonFocus(binding.episodeReverse, colors);
        applyEpisodeToolButtonFocus(binding.episodeViewMode, colors);
        applyEpisodeToolButtonFocus(binding.episodeFileName, colors);
    }

    private void applyEpisodeTitleButtonFocus(MaterialButton button, ThemeColors colors) {
        boolean actionable = button.isFocusable() && button.isEnabled();
        boolean focused = actionable && button.hasFocus();
        button.setBackgroundTintList(ColorStateList.valueOf(focused ? colors.control : Color.TRANSPARENT));
        button.setTextColor(colors.primary);
        button.setIconTint(ColorStateList.valueOf(colors.primary));
        button.setStrokeWidth(focused ? ResUtil.dp2px(FOCUS_STROKE_DP) : 0);
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : Color.TRANSPARENT));
    }

    private void applyEpisodeToolButtonFocus(MaterialButton button, ThemeColors colors) {
        boolean focused = button.hasFocus();
        button.setSelected(false);
        button.setActivated(false);
        button.setBackgroundTintList(ColorStateList.valueOf(colors.control));
        button.setTextColor(colors.primary);
        button.setIconTint(ColorStateList.valueOf(colors.primary));
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : colors.lineStrong));
    }

    private void tintTextTree(View view, ThemeColors colors) {
        if (view instanceof RecyclerView) return;
        if (view.getId() == R.id.loading) return;
        if (view instanceof TextView textView && !(view instanceof MaterialButton)) {
            textView.setTextColor(colors.primary);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintTextTree(group.getChildAt(i), colors);
    }

    private void loadContent(@Nullable TmdbBundle reusableBundle) {
        int generation = ++loadGeneration;
        detailTasks.cancelAll();
        int mode = getDetailMode();
        String key = getKeyText();
        String id = getIdText();
        String title = getNameText();
        long loadStart = System.currentTimeMillis();
        detailLoadStart = loadStart;
        SpiderDebug.log("tmdb-detail-flow", "load start mode=%d key=%s id=%s title=%s reusable=%s", mode, key, id, title, reusableBundle != null);
        detailTasks.submit(() -> {
            if (generation != loadGeneration || Thread.currentThread().isInterrupted()) return;
            boolean tmdbAllowed = isTmdbAllowedForCurrentSite();
            Future<TmdbLoadResult> tmdbFuture = reusableBundle == null && tmdbConfig.isReady() && tmdbAllowed
                    ? detailTasks.submitCallable(Task.largeExecutor(), this::loadTmdbResult)
                    : null;
            boolean singlePassStandaloneTmdb = shouldLoadInitialStandaloneTmdbDetailInSinglePass(reusableBundle, tmdbFuture);
            SpiderDebug.log("tmdb-detail-flow", "load tasks mode=%d tmdbAllowed=%s singlePass=%s reusable=%s", mode, tmdbAllowed, singlePassStandaloneTmdb, reusableBundle != null);
            Vod loadedVod = null;
            String error = null;
            long sourceStart = System.currentTimeMillis();
            try {
                Result result = SiteApi.detailContent(key, id);
                // 猫源设置项：点击的本意是开网页，detail 只是副产物。留在这儿会让内嵌页背后压着空白详情页，
                // 用户从内嵌页返回时先落回这里。兜底路径——正常由 CatWebEvent 更早地退场。
                if (com.fongmi.android.tv.api.CatAction.shouldYieldDetail(key, loadStart, result)) {
                    SpiderDebug.log("tmdb-detail-flow", "yield to cat webview key=%s id=%s", key, id);
                    runOnAliveUi(this::finish);
                    if (tmdbFuture != null) tmdbFuture.cancel(true);
                    return;
                }
                if (result != null && !result.getList().isEmpty()) {
                    loadedVod = result.getVod();
                    if (loadedVod != null && loadedVod.getSite() == null) {
                        loadedVod.setSite(VodConfig.get().getSite(key));
                    }
                }
            } catch (Throwable e) {
                error = e.getMessage();
            }
            SpiderDebug.log("tmdb-detail-flow", "source detail cost=%dms mode=%d key=%s id=%s hit=%s error=%s", System.currentTimeMillis() - sourceStart, mode, key, id, loadedVod != null, TextUtils.isEmpty(error) ? "" : error);

            if (generation != loadGeneration || Thread.currentThread().isInterrupted()) {
                if (tmdbFuture != null) tmdbFuture.cancel(true);
                return;
            }
            Vod finalVod = loadedVod;
            String finalError = error;
            if (!singlePassStandaloneTmdb || finalVod == null) {
                runOnAliveUi(() -> {
                    if (generation != loadGeneration) return;
                    applyLoaded(finalVod, reusableBundle, new ArrayList<>(), finalError, false);
                });
            }
            if (finalVod == null || tmdbFuture == null) {
                if (tmdbFuture != null) tmdbFuture.cancel(true);
                return;
            }
            try {
                long tmdbWaitStart = System.currentTimeMillis();
                TmdbLoadResult result = tmdbFuture.get();
                if (generation != loadGeneration || Thread.currentThread().isInterrupted()) return;
                SpiderDebug.log("tmdb-detail-flow", "tmdb wait cost=%dms mode=%d bundle=%s search=%d total=%dms", System.currentTimeMillis() - tmdbWaitStart, mode, result != null && result.bundle() != null, result == null ? 0 : result.searchItems().size(), System.currentTimeMillis() - loadStart);
                if (result != null && result.bundle() == null && finalVod != null) {
                    String detailTitle = finalVod.getName();
                    String initialTitle = getTmdbRawTitle();
                    if (!TextUtils.isEmpty(detailTitle) && !normalize(detailTitle).equals(normalize(initialTitle))) {
                        logTmdbMatch("基础匹配未命中，使用站源标题重试：初始标题=%s，站源标题=%s", initialTitle, detailTitle);
                        AutoTmdbMatch detailRetry = searchResolvedTmdbMatch(detailTitle, finalVod);
                        if (detailRetry.item() != null) {
                            result = new TmdbLoadResult(loadTmdbBundle(detailRetry.item()), detailRetry.items());
                        } else if (!detailRetry.items().isEmpty()) {
                            result = new TmdbLoadResult(null, detailRetry.items());
                        }
                    }
                }
                if (generation != loadGeneration || Thread.currentThread().isInterrupted()) return;
                if (result != null && result.bundle() == null && finalVod != null) {
                    String query = cleanTmdbSearchQuery(finalVod.getName());
                    logTmdbMatch("基础匹配未命中，使用站源详情继续消歧：片名=%s，清洗后=%s，年份=%s，演员=%s，导演=%s，简介长度=%d",
                            finalVod.getName(), query, finalVod.getYear(), finalVod.getActor(), finalVod.getDirector(), finalVod.getContent().length());
                    TmdbBundle bundle = chooseTmdbBundle(result.searchItems(), query, finalVod);
                    List<TmdbItem> matchedSearchItems = result.searchItems();
                    if (bundle == null) {
                        SplitYearSearch split = searchSplitYearTmdbItems(query, finalVod);
                        if (split != null) {
                            bundle = chooseTmdbBundle(split.items(), split.query(), finalVod);
                            matchedSearchItems = split.items();
                        }
                    }
                    result = new TmdbLoadResult(bundle, matchedSearchItems);
                }
                if (generation != loadGeneration || Thread.currentThread().isInterrupted()) return;
                TmdbLoadResult loadedResult = result;
                if (singlePassStandaloneTmdb) loadedResult = preloadInitialStandaloneSeason(loadedResult, finalVod);
                if (generation != loadGeneration || Thread.currentThread().isInterrupted()) return;
                TmdbLoadResult finalResult = loadedResult;
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || (!singlePassStandaloneTmdb && vod == null)) return;
                    if (singlePassStandaloneTmdb) {
                        applyLoaded(finalVod, finalResult == null ? null : finalResult.bundle(), finalResult == null ? new ArrayList<>() : finalResult.searchItems(), finalError, true);
                    } else {
                        applyTmdbResult(finalResult);
                    }
                });
            } catch (Throwable ignored) {
                if (generation != loadGeneration || Thread.currentThread().isInterrupted()) return;
                SpiderDebug.log("tmdb-detail-flow", "tmdb wait failed mode=%d total=%dms", mode, System.currentTimeMillis() - loadStart);
                if (singlePassStandaloneTmdb) {
                    runOnAliveUi(() -> {
                        if (generation != loadGeneration) return;
                        applyLoaded(finalVod, null, new ArrayList<>(), finalError, false);
                    });
                }
            }
        });
    }

    private boolean shouldLoadInitialStandaloneTmdbDetailInSinglePass(@Nullable TmdbBundle reusableBundle, @Nullable Future<TmdbLoadResult> tmdbFuture) {
        return reusableBundle == null && tmdbFuture != null && activeTmdbBundle == null && Setting.isStandaloneTmdbDetailMode(getDetailMode());
    }

    private TmdbLoadResult preloadInitialStandaloneSeason(TmdbLoadResult result, Vod loadedVod) {
        TmdbBundle bundle = result == null ? null : result.bundle();
        if (bundle == null || loadedVod == null || bundle.item() == null || !"tv".equalsIgnoreCase(bundle.item().getMediaType()) || !canMatchTmdb()) return result;
        int seasonNumber = initialStandaloneSeasonNumber(loadedVod, bundle);
        if (seasonNumber < 0 || bundle.seasonEpisodes().containsKey(seasonNumber)) return result;
        // 源站季号可能因 TMDB 更名与 season_number 存在偏移，仅向 TMDB 请求时校正，本地仍以源季号为 key
        int tmdbSeason = EpisodeSeasonPolicy.correctTmdbSeason(seasonNumber, bundle.item().getTitle());
        try {
            JsonObject season = tmdbService.season(bundle.item(), tmdbSeason, tmdbConfig, bundle.detail(), false);
            Map<Integer, Integer> seasonCounts = new HashMap<>(bundle.seasonCounts());
            Map<Integer, List<TmdbEpisode>> seasonEpisodes = new HashMap<>(bundle.seasonEpisodes());
            Map<Integer, List<TmdbPerson>> seasonCast = new HashMap<>(bundle.seasonCast());
            Map<Integer, List<String>> seasonPhotos = new HashMap<>(bundle.seasonPhotos());
            List<TmdbEpisode> episodes = tmdbService.episodes(season, tmdbConfig, bundle.item().getTmdbId(), tmdbSeason);
            seasonCounts.put(seasonNumber, episodes.size());
            seasonEpisodes.put(seasonNumber, episodes);
            seasonCast.put(seasonNumber, tmdbService.seasonCast(season, tmdbConfig));
            seasonPhotos.put(seasonNumber, tmdbService.seasonPhotos(season, tmdbConfig));
            TmdbBundle withSeason = new TmdbBundle(bundle.item(), bundle.detail(), bundle.cast(), bundle.creators(), bundle.photos(), bundle.related(), bundle.seasons(), seasonCounts, seasonEpisodes, seasonCast, seasonPhotos);
            return new TmdbLoadResult(withSeason, result.searchItems());
        } catch (Throwable ignored) {
            return result;
        }
    }

    private int initialStandaloneSeasonNumber(Vod loadedVod, TmdbBundle bundle) {
        Flag initialFlag = initialStandaloneFlag(loadedVod);
        List<Episode> episodes = initialFlag == null ? List.of() : initialFlag.getEpisodes();
        if (episodes == null || episodes.isEmpty() || bundle == null) return -1;
        List<Integer> sourceSeasonNumbers = new ArrayList<>(episodes.size());
        for (Episode episode : episodes) sourceSeasonNumbers.add(sourceSeasonNumber(episode));
        int titleSeason = EpisodeSeasonPolicy.resolveExplicitSourceSeason(initialFlag.getShow());
        if (titleSeason < 0) titleSeason = sourceSeasonNumber(loadedVod.getName());
        int firstSeason = firstSeasonNumber(bundle.detail());
        List<Integer> availableSeasons = EpisodeSeasonPolicy.resolveAvailableSeasons(
                sourceSeasonNumbers,
                titleSeason,
                firstSeason,
                bundle.seasons(),
                bundle.seasonCounts(),
                sourceEpisodeNumbers(episodes));
        if (availableSeasons.isEmpty()) return -1;
        Episode selected = initialStandaloneEpisode(loadedVod, episodes);
        int sourceSeason = sourceSeasonNumber(selected);
        if (availableSeasons.contains(sourceSeason)) return sourceSeason;
        if (availableSeasons.size() == 1) return availableSeasons.get(0);
        EpisodeSeasonPolicy.SeasonEpisode mapped = EpisodeSeasonPolicy.mapFlatEpisodeNumber(
                selected == null ? -1 : selected.getNumber(), bundle.seasons(), bundle.seasonCounts());
        if (mapped != null && availableSeasons.contains(mapped.seasonNumber())) return mapped.seasonNumber();
        int index = selected == null ? -1 : episodes.indexOf(selected);
        if (index < 0 || !EpisodeSeasonPolicy.canSliceBySeasonCounts(episodes.size(), bundle.seasons(), bundle.seasonCounts())) return availableSeasons.get(0);
        int start = 0;
        for (Integer season : bundle.seasons()) {
            int count = Math.max(0, bundle.seasonCounts().getOrDefault(season, 0));
            int end = Math.min(episodes.size(), start + count);
            if (index >= start && index < end && availableSeasons.contains(season)) return season;
            start = end;
        }
        return availableSeasons.get(0);
    }

    private Flag initialStandaloneFlag(Vod loadedVod) {
        if (loadedVod == null) return null;
        List<Flag> flags = loadedVod.getFlags();
        if (flags == null || flags.isEmpty()) return null;
        String requestedEpisodeUrl = getIntentPlaybackEpisodeUrl();
        String requestedFlagKey = getIntentPlaybackFlagKey();
        String requestedFlag = getIntentPlaybackFlag();
        Flag requested = TmdbUIAdapter.selectPlaybackFlag(
                flags, requestedFlagKey, requestedEpisodeUrl, requestedFlag);
        if (requested != null) return requested;
        // 与 findInitialFlag 保持同一优先级，否则季度预取会按另一条线路取季，
        // 详情绑定完成后又要重新加载。
        Flag preferred = findPreferredFlag(flags);
        if (preferred != null) return preferred;
        History saved = null;
        try {
            saved = isResumeFromHistory() ? getIntentResumeHistory() : null;
            if (saved == null && !isResumeFromHistory()) saved = History.findPlayback(getHistoryKey(), List.of(loadedVod.getName(), getNameText()), flags, null, sourceTitleSeasonNumber());
        } catch (Throwable ignored) {
        }
        Flag selected = saved == null ? null : TmdbUIAdapter.selectPlaybackFlag(
                flags, saved.getSourceBindingKey(), saved.getEpisodeUrl(), saved.getVodFlag());
        return selected == null ? flags.get(0) : selected;
    }

    private Episode initialStandaloneEpisode(Vod loadedVod, List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return null;
        Episode requested = findEpisodeByUrl(getIntentPlaybackEpisodeUrl(), episodes);
        if (requested != null) return requested;
        String requestedName = getIntentPlaybackEpisodeName();
        for (Episode item : episodes) if (!TextUtils.isEmpty(requestedName) && (TextUtils.equals(requestedName, item.getName()) || TextUtils.equals(requestedName, item.getDisplayName()))) return item;
        try {
            History saved = isResumeFromHistory() ? getIntentResumeHistory() : null;
            if (saved == null && !isResumeFromHistory()) saved = History.findPlayback(getHistoryKey(), List.of(loadedVod.getName(), getNameText()), loadedVod.getFlags(), null, sourceTitleSeasonNumber());
            Episode episode = findEpisodeByUrl(saved == null ? "" : saved.getEpisodeUrl(), episodes);
            if (episode != null) return episode;
            if (saved != null) {
                String remarks = saved.getVodRemarks();
                for (Episode item : episodes) if (item.getName().equalsIgnoreCase(remarks) || item.getDisplayName().equals(remarks)) return item;
            }
        } catch (Throwable ignored) {
        }
        return episodes.get(0);
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

    private boolean isTmdbRequestCurrent(int generation, @Nullable TmdbItem item) {
        return generation == loadGeneration && isSameTmdbItem(item, matchedTmdbItem);
    }

    private boolean isSameTmdbItem(@Nullable TmdbItem first, @Nullable TmdbItem second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        return first.getTmdbId() == second.getTmdbId() && TextUtils.equals(first.getMediaType(), second.getMediaType());
    }

    private boolean isInlinePlaybackRequestCurrent(int generation, String key, String flag, String episodeUrl) {
        return generation == inlinePlaybackGeneration
                && TextUtils.equals(key, getKeyText())
                && selectedFlag != null
                && TextUtils.equals(flag, selectedFlag.getFlag())
                && selectedEpisode != null
                && TextUtils.equals(episodeUrl, selectedEpisode.getUrl());
    }

    private void cancelPendingInlinePlayback() {
        if (!inlinePlaybackLoading && !inlinePlayerSwitchLoading) return;
        inlinePlaybackGeneration++;
        inlinePlaybackLoading = false;
        inlinePlayerSwitchLoading = false;
        hideInlineLoading();
        updateInlineDisplayPanel();
    }

    private void applyLoaded(Vod loadedVod, TmdbBundle bundle, List<TmdbItem> searchItems, String error) {
        applyLoaded(loadedVod, bundle, searchItems, error, true);
    }

    private void applyLoaded(Vod loadedVod, TmdbBundle bundle, List<TmdbItem> searchItems, String error, boolean allowMatchDialog) {
        long start = System.currentTimeMillis();
        binding.loading.setVisibility(View.GONE);
        if (loadedVod == null) {
            SpiderDebug.log("tmdb-detail-flow", "apply loaded empty cost=%dms mode=%d error=%s", System.currentTimeMillis() - start, getDetailMode(), TextUtils.isEmpty(error) ? "" : error);
            if (!TextUtils.isEmpty(error)) Notify.show(error);
            tryAutoChangeSource();
            return;
        }
        vod = loadedVod;
        brokenSources.clear();
        sourceVodName = loadedVod.getName();
        TmdbEpisodeSorter.sort(vod);
        clearEpisodeRenderCaches();
        applyTmdbBundle(bundle);
        if (bundle != null) saveTmdbMatch(bundle.item());
        enrichVod();
        clearEpisodeRenderCaches();
        initHistory();
        if (history == null) return;
        bindPage();
        focusInlinePlayerPanel();
        maybeAutoPlayInline();
        if (bundle != null) loadTmdbMediaBlocks(bundle);
        if (allowMatchDialog && shouldShowAutoTmdbMatchDialog(bundle)) showTmdbMatchDialog(searchItems);
        SpiderDebug.log("tmdb-detail-flow", "apply loaded cost=%dms mode=%d bundle=%s name=%s flags=%d", System.currentTimeMillis() - start, getDetailMode(), bundle != null, loadedVod.getName(), loadedVod.getFlags().size());
    }

    private TmdbLoadResult loadTmdbResult() {
        TmdbBundle tmdbBundle = null;
        List<TmdbItem> searchItems = new ArrayList<>();
        try {
            if (initialTmdbItem != null) {
                tmdbBundle = loadTmdbBundle(initialTmdbItem);
            } else {
                boolean manual = isManualTmdbMatch();
                TmdbItem match = getCachedTmdbMatch();
                if (match != null) {
                    try {
                        tmdbBundle = loadTmdbBundle(match);
                        // 手动选择由用户拍板，分季变体检查只用于过滤自动匹配的误命中。
                        if (!manual && TmdbMatchPolicy.isUnwantedSplitSeasonVariant(getNameText(), tmdbBundle.detail())) {
                            logTmdbMatch("缓存匹配跳过：当前标题=%s，缓存标题=%s，TMDB=%d 是分季变体", getNameText(), match.getTitle(), match.getTmdbId());
                            match = null;
                            tmdbBundle = null;
                        }
                    } catch (Throwable ignored) {
                        match = null;
                        tmdbBundle = null;
                    }
                }
                if (match == null) {
                    AutoTmdbMatch autoMatch = searchResolvedTmdbMatch();
                    searchItems = autoMatch.items();
                    match = autoMatch.item();
                }
                if (match != null && tmdbBundle == null) {
                    tmdbBundle = loadTmdbBundle(match);
                    if (TmdbMatchPolicy.isUnwantedSplitSeasonVariant(getNameText(), tmdbBundle.detail())) {
                        logTmdbMatch("自动匹配跳过：当前标题=%s，候选标题=%s，TMDB=%d 是分季变体", getNameText(), match.getTitle(), match.getTmdbId());
                        tmdbBundle = null;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return new TmdbLoadResult(tmdbBundle, searchItems);
    }

    private void applyTmdbResult(TmdbLoadResult result) {
        applyTmdbResultNow(result);
    }

    private void applyTmdbResultNow(TmdbLoadResult result) {
        long start = System.currentTimeMillis();
        TmdbBundle bundle = result == null ? null : result.bundle();
        applyTmdbBundle(bundle);
        reloadHistoryAfterTmdbMatch();
        applyReloadedHistorySelection();
        if (bundle != null) saveTmdbMatch(bundle.item());
        enrichVod();
        bindBackdrop();
        bindHeader();
        bindMeta();
        bindRatings();
        bindOverview();
        renderSeasonSelection();
        lastEpisodeMediaSeason = Integer.MIN_VALUE;
        renderEpisodes();
        if (selectedFlag == null || selectedFlag.getEpisodes() == null || selectedFlag.getEpisodes().isEmpty()) bindTmdbSection();
        focusInlinePlayerPanel();
        if (bundle != null) binding.getRoot().post(() -> loadTmdbMediaBlocks(bundle));
        if (shouldShowAutoTmdbMatchDialog(bundle)) showTmdbMatchDialog(result == null ? List.of() : result.searchItems());
        SpiderDebug.log("tmdb-detail-flow", "apply tmdb result cost=%dms mode=%d bundle=%s search=%d", System.currentTimeMillis() - start, getDetailMode(), bundle != null, result == null ? 0 : result.searchItems().size());
    }

    private TmdbBundle loadTmdbBundle(TmdbItem item) throws Exception {
        long start = System.currentTimeMillis();
        JsonObject detail = tmdbService.detail(item, tmdbConfig);
        item = normalizeTmdbItemTitle(item, detail);
        List<Integer> seasons = new ArrayList<>();
        Map<Integer, Integer> seasonCounts = new HashMap<>();
        Map<Integer, List<TmdbEpisode>> seasonEpisodes = new HashMap<>();
        Map<Integer, List<TmdbPerson>> seasonCast = new HashMap<>();
        Map<Integer, List<String>> seasonPhotos = new HashMap<>();
        if ("tv".equalsIgnoreCase(item.getMediaType())) {
            seasonCounts = seasonEpisodeCounts(detail);
            seasons.addAll(seasonCounts.keySet());
        }
        SpiderDebug.log("tmdb-detail-flow", "tmdb bundle cost=%dms title=%s media=%s id=%d seasons=%d", System.currentTimeMillis() - start, item.getTitle(), item.getMediaType(), item.getTmdbId(), seasons.size());
        return new TmdbBundle(item, detail, List.of(), List.of(), List.of(), List.of(), seasons, seasonCounts, seasonEpisodes, seasonCast, seasonPhotos);
    }

    private TmdbItem normalizeTmdbItemTitle(TmdbItem item, JsonObject detail) {
        if (item == null) return null;
        String title = tmdbDetailTitle(item, detail);
        if (TextUtils.isEmpty(title) || title.equals(item.getTitle())) return item;
        logTmdbMatch("详情标题归一化：缓存标题=%s，TMDB标题=%s", item.getTitle(), title);
        return new TmdbItem(
                item.getTmdbId(),
                item.getMediaType(),
                title,
                item.getSubtitle(),
                item.getOverview(),
                item.getPosterUrl(),
                item.getBackdropUrl(),
                item.getCredit(),
                item.getRating(),
                item.getOriginalLanguage(),
                item.getOriginCountry(),
                item.getGenreIds(),
                item.getDepartment(),
                item.getTmdbRating(),
                item.getDoubanRating(),
                item.getRecommendationReason());
    }

    private String tmdbDetailTitle(TmdbItem item, JsonObject detail) {
        if (item == null || detail == null) return "";
        String primary = "movie".equalsIgnoreCase(item.getMediaType()) ? string(detail, "title") : string(detail, "name");
        if (!TextUtils.isEmpty(primary)) return primary;
        return "movie".equalsIgnoreCase(item.getMediaType()) ? string(detail, "name") : string(detail, "title");
    }

    private void loadTmdbMediaBlocks(TmdbBundle bundle) {
        if (bundle == null || bundle.item() == null || bundle.detail() == null) return;
        int generation = loadGeneration;
        Vod currentVod = vod;
        tmdbMediaLoading = true;
        bindTmdbSection();
        loadTmdbPersonalAiCache(bundle, currentVod, generation);
        detailTasks.submit(Task.recommendationExecutor(), () -> {
            List<TmdbPerson> cast = new ArrayList<>();
            List<TmdbPerson> creators = new ArrayList<>();
            List<String> backdrops = new ArrayList<>();
            List<String> posters = new ArrayList<>();
            List<String> backgroundSlides = new ArrayList<>();
            List<TmdbItem> related = new ArrayList<>();
            List<TmdbItem> personalTmdb = new ArrayList<>();
            List<TmdbItem> personalDouban = new ArrayList<>();
            PersonalRecommendationService recommendationService = new PersonalRecommendationService(tmdbService, tmdbConfig);
            try {
                cast = tmdbService.cast(bundle.detail(), tmdbConfig);
            } catch (Throwable ignored) {
            }
            try {
                creators = tmdbService.creators(bundle.detail(), tmdbConfig);
            } catch (Throwable ignored) {
            }
            try {
                backdrops = tmdbService.backdrops(bundle.detail(), tmdbConfig);
                posters = tmdbService.posters(bundle.detail(), tmdbConfig);
                backgroundSlides = tmdbService.photos(bundle.detail(), tmdbConfig, preferLandscapeBackground());
            } catch (Throwable ignored) {
            }
            try {
                List<TmdbItem> recommendations = tmdbService.recommendations(bundle.detail(), tmdbConfig);
                List<TmdbItem> similar = tmdbService.similar(bundle.detail(), tmdbConfig);
                related = TmdbRecommendationRows.rankedRelated(bundle.detail(), recommendations, similar);
            } catch (Throwable ignored) {
            }
            try {
                if (Setting.isPersonalRecommendation()) {
                    PersonalRecommendationService.RecommendationPages pages = recommendationService.loadPage(currentVod, bundle.item(), bundle.detail(), 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                    personalTmdb = TmdbRecommendationRows.personalTmdb(pages.getTmdb().getItems(), related);
                    personalDouban = TmdbRecommendationRows.personalDouban(pages.getDouban().getItems(), related, personalTmdb);
                }
            } catch (Throwable ignored) {
            }
            List<TmdbPerson> finalCast = cast;
            List<TmdbPerson> finalCreators = creators;
            List<String> finalBackdrops = backdrops;
            List<String> finalPosters = posters;
            List<String> finalBackgroundSlides = backgroundSlides;
            List<TmdbItem> finalRelated = related;
            List<TmdbItem> finalPersonalTmdb = personalTmdb;
            List<TmdbItem> finalPersonalDouban = personalDouban;
            runOnAliveUi(() -> {
                if (generation != loadGeneration || bundle.item() != matchedTmdbItem) return;
                tmdbMediaLoading = false;
                detailCastItems.clear();
                detailCastItems.addAll(finalCast);
                creatorItems.clear();
                creatorItems.addAll(finalCreators);
                relatedItems.clear();
                relatedItems.addAll(finalRelated);
                personalTmdbItems.clear();
                personalTmdbItems.addAll(finalPersonalTmdb);
                personalDoubanItems.clear();
                personalDoubanItems.addAll(finalPersonalDouban);
                detailTmdbPhotos.clear();
                detailTmdbPhotos.addAll(finalBackdrops);
                detailTmdbPosters.clear();
                detailTmdbPosters.addAll(finalPosters);
                detailBackdropSlides.clear();
                detailBackdropSlides.addAll(finalBackgroundSlides);
                refreshBackdropSlideshow();
                bindSeasonTmdbMedia(tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()));
                bindTmdbSection();
                loadTmdbPersonalAi(bundle, currentVod, finalRelated, finalPersonalTmdb, finalPersonalDouban, generation);
            });
            recommendationService.enrichTmdbRatingsAsync(finalRelated,
                    enriched -> applyTmdbRatingEnrichment(bundle, relatedItems, enriched, generation));
            recommendationService.enrichTmdbRatingsAsync(finalPersonalTmdb,
                    enriched -> applyTmdbRatingEnrichment(bundle, personalTmdbItems, enriched, generation));
        });
    }

    private void loadTmdbPersonalAiCache(TmdbBundle bundle, Vod currentVod, int generation) {
        if (!Setting.isPersonalRecommendation() || bundle == null || bundle.item() == null) return;
        detailTasks.submit(Task.recommendationExecutor(), () -> {
            try {
                long start = System.currentTimeMillis();
                PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
                AiRecommendationService.CachedPage cached = service.loadCachedAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                if (!cached.hasItems()) return;
                List<TmdbItem> cachedAi = TmdbRecommendationRows.personalAi(cached.getPage().getItems(), List.of(), List.of(), List.of());
                SpiderDebug.log("tmdb", "detail personal ai early cache hit exact=%s resolved=%s cost=%dms count=%d title=%s", cached.isExact(), cached.isResolved(), System.currentTimeMillis() - start, cachedAi.size(), bundle.item().getTitle());
                applyTmdbPersonalAi(bundle, cachedAi, generation, false);

                service.enrichTmdbPageRatingsAsync(cached.getPage(), enrichedPage -> {
                    List<TmdbItem> enrichedAi = TmdbRecommendationRows.personalAi(enrichedPage.getItems(), List.of(), List.of(), List.of());
                    SpiderDebug.log("tmdb", "detail personal ai early ratings cost=%dms count=%d title=%s", System.currentTimeMillis() - start, enrichedAi.size(), bundle.item().getTitle());
                    applyTmdbRatingEnrichment(bundle, personalAiItems, enrichedAi, generation);
                });
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "detail personal ai early cache failed error=%s", e.getMessage());
            }
        });
    }

    private void loadTmdbPersonalAi(TmdbBundle bundle, Vod currentVod, List<TmdbItem> related, List<TmdbItem> personalTmdb, List<TmdbItem> personalDouban, int generation) {
        if (!Setting.isPersonalRecommendation() || bundle == null || bundle.item() == null) return;
        detailTasks.submit(Task.recommendationExecutor(), () -> {
            long start = System.currentTimeMillis();
            PersonalRecommendationService service = new PersonalRecommendationService(tmdbService, tmdbConfig);
            AiRecommendationService.CachedPage cached = service.loadCachedAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            if (cached.hasItems()) {
                List<TmdbItem> cachedAi = TmdbRecommendationRows.personalAi(cached.getPage().getItems(), related, personalTmdb, personalDouban);
                SpiderDebug.log("tmdb", "detail personal ai cache hit exact=%s resolved=%s count=%d title=%s", cached.isExact(), cached.isResolved(), cachedAi.size(), bundle.item().getTitle());
                applyTmdbPersonalAi(bundle, cachedAi, generation, false);
            }
            PersonalRecommendationService.RecommendationPage page = cached.getPage();
            String mode = "cache";
            try {
                if (!cached.hasItems() || !cached.isExact() || !cached.isResolved()) {
                    mode = cached.isExact() ? "resolve-cache" : "refresh";
                    page = cached.isExact()
                            ? service.resolveCachedAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                            : service.refreshAiPage(currentVod, bundle.item(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
                }
            } catch (Throwable e) {
                SpiderDebug.log("tmdb", "detail personal ai failed error=%s", e.getMessage());
            }
            PersonalRecommendationService.RecommendationPage loadedPage = page;
            List<TmdbItem> personalAi = TmdbRecommendationRows.personalAi(loadedPage.getItems(), related, personalTmdb, personalDouban);
            SpiderDebug.log("tmdb", "detail personal ai async mode=%s cost=%dms count=%d title=%s", mode, System.currentTimeMillis() - start, personalAi.size(), bundle.item().getTitle());
            applyTmdbPersonalAi(bundle, personalAi, generation, !cached.hasItems());
            service.enrichTmdbPageRatingsAsync(loadedPage, enrichedPage -> {
                List<TmdbItem> enrichedAi = TmdbRecommendationRows.personalAi(enrichedPage.getItems(), related, personalTmdb, personalDouban);
                applyTmdbRatingEnrichment(bundle, personalAiItems, enrichedAi, generation);
            });
        });
    }

    private void applyTmdbRatingEnrichment(TmdbBundle bundle, List<TmdbItem> target, List<TmdbItem> enriched, int generation) {
        runOnAliveUi(() -> {
            if (generation != loadGeneration || bundle == null || bundle.item() != matchedTmdbItem) return;
            if (mergeRecommendationRatings(target, enriched)) bindTmdbSection();
        });
    }

    private void applyTmdbPersonalAi(TmdbBundle bundle, List<TmdbItem> items, int generation, boolean allowEmpty) {
        runOnAliveUi(() -> {
            if (generation != loadGeneration || bundle == null || bundle.item() != matchedTmdbItem) return;
            if (!allowEmpty && (items == null || items.isEmpty())) return;
            if (TmdbRecommendationRows.sameDisplayList(personalAiItems, items)) return;
            personalAiItems.clear();
            if (items != null) personalAiItems.addAll(items);
            bindTmdbSection();
        });
    }

    private void applyTmdbBundle(TmdbBundle bundle) {
        activeTmdbBundle = bundle;
        matchedTmdbItem = bundle == null ? null : bundle.item();
        matchedTmdbDetail = bundle == null ? null : bundle.detail();
        detailCastItems.clear();
        if (bundle != null) detailCastItems.addAll(bundle.cast());
        castItems.clear();
        castItems.addAll(detailCastItems);
        creatorItems.clear();
        if (bundle != null) creatorItems.addAll(bundle.creators());
        detailTmdbPhotos.clear();
        detailTmdbPosters.clear();
        detailBackdropSlides.clear();
        if (bundle != null && bundle.detail() != null) {
            detailTmdbPhotos.addAll(tmdbService.backdrops(bundle.detail(), tmdbConfig));
            detailTmdbPosters.addAll(tmdbService.posters(bundle.detail(), tmdbConfig));
            detailBackdropSlides.addAll(bundle.photos());
            if (detailBackdropSlides.isEmpty()) {
                detailBackdropSlides.addAll(tmdbService.photos(bundle.detail(), tmdbConfig, preferLandscapeBackground()));
            }
        }
        tmdbEpisodePhotos.clear();
        tmdbEpisodePhotos.addAll(detailTmdbPhotos);
        relatedItems.clear();
        if (bundle != null) relatedItems.addAll(bundle.related());
        personalTmdbItems.clear();
        personalDoubanItems.clear();
        personalAiItems.clear();
        tmdbEpisodes.clear();
        seasonNumbers.clear();
        if (bundle != null) seasonNumbers.addAll(bundle.seasons());
        seasonEpisodeCounts.clear();
        if (bundle != null) seasonEpisodeCounts.putAll(bundle.seasonCounts());
        tmdbSeasonEpisodes.clear();
        if (bundle != null) tmdbSeasonEpisodes.putAll(bundle.seasonEpisodes());
        tmdbSeasonCast.clear();
        if (bundle != null) tmdbSeasonCast.putAll(bundle.seasonCast());
        tmdbSeasonPhotos.clear();
        if (bundle != null) tmdbSeasonPhotos.putAll(bundle.seasonPhotos());
        loadingSeasons.clear();
        refreshedSingleSeasonProbes.clear();
        tmdbMediaLoading = false;
        clearSeasonResolutionCache();
        if (selectedFlag != null) loadTmdbSeasonBinding();
        updateTmdbSeasonActionVisibility();
    }

    private void showTmdbMatchDialog(List<TmdbItem> items) {
        showTmdbMatchDialog(items, true);
    }

    private boolean shouldShowAutoTmdbMatchDialog(TmdbBundle bundle) {
        return Setting.isTmdbMatchDialog() && canMatchTmdb() && bundle == null && initialTmdbItem == null;
    }

    private void showTmdbMatchDialog(List<TmdbItem> items, boolean skippable) {
        if (!canTouchUi() || !canMatchTmdb()) return;
        TmdbSearchDialog.create(this)
                .title(getString(R.string.detail_tmdb_match_title))
                .query(getTmdbSearchQuery())
                .items(items)
                .selectedItem(matchedTmdbItem)
                .listener(this::applyManualTmdb)
                .searchListener(this::searchTmdb)
                .skipListener(skippable ? this::onPlay : null)
                .show();
    }

    private void maybeShowPendingTmdbSeasonDialog() {
        if (pendingTmdbSeasonChoice == null || matchedTmdbItem == null) return;
        if (!isSameTmdbItem(pendingTmdbSeasonChoice, matchedTmdbItem)) {
            pendingTmdbSeasonChoice = null;
            return;
        }
        pendingTmdbSeasonChoice = null;
        if (!matchedTmdbItem.isTv() || seasonNumbers.size() <= 1) return;
        binding.getRoot().post(this::showManualTmdbSeasonDialog);
    }

    private void showManualTmdbSeasonDialog() {
        if (matchedTmdbItem == null || !matchedTmdbItem.isTv() || seasonNumbers.isEmpty()) {
            Notify.show(R.string.detail_tmdb_empty);
            return;
        }
        ChoiceDialog.showTmdbSeason(this, seasonNumbers, seasonEpisodeCounts, tmdbSeasonChoiceResolution(), new ChoiceDialog.OnTmdbSeasonChoice() {
            @Override
            public void onAuto() {
                clearTmdbSeasonBinding();
                Notify.show(R.string.tmdb_season_match_saved);
            }

            @Override
            public void onTmdbCounts() {
                List<Episode> episodes = selectedFlag == null ? List.of() : selectedFlag.getEpisodes();
                if (!canApplyValidatedFlatSeasonMapping(episodes)) {
                    Notify.show(R.string.tmdb_season_auto_by_counts_failed);
                    return;
                }
                saveTmdbSeasonBinding(null, TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE);
                selectedSeasonNumber = -1;
                refreshEpisodesAfterSeasonBinding();
                Notify.show(R.string.tmdb_season_match_saved);
            }

            @Override
            public void onFlat() {
                saveTmdbSeasonBinding(null, TmdbSeasonMatchCache.Mode.MANUAL_FLAT);
                selectedSeasonNumber = -1;
                refreshEpisodesAfterSeasonBinding();
                Notify.show(R.string.tmdb_season_match_saved);
            }

            @Override
            public void onAi() {
                analyzeTmdbSeasonWithAi();
            }

            @Override
            public void onSeason(int seasonNumber) {
                saveTmdbSeasonBinding(seasonNumber, TmdbSeasonMatchCache.Mode.MANUAL_SEASON);
                selectedSeasonNumber = seasonNumber;
                refreshEpisodesAfterSeasonBinding();
                Notify.show(R.string.tmdb_season_match_saved);
            }
        });
    }

    private void analyzeTmdbSeasonWithAi() {
        List<Episode> episodes = selectedFlag == null ? List.of() : selectedFlag.getEpisodes();
        if (!Setting.isAiConfigReady()) {
            Notify.show(R.string.tmdb_season_ai_config_required);
            return;
        }
        if (episodes == null || episodes.isEmpty() || seasonEpisodeCounts.isEmpty()) {
            Notify.show(R.string.tmdb_season_ai_failed);
            return;
        }
        cancelAiSeasonAnalysis(false);
        AiConfig config = AiConfig.objectFrom(Setting.getAiConfig());
        String title = !TextUtils.isEmpty(sourceVodName) ? sourceVodName : vod == null ? getNameText() : vod.getName();
        String flagName = selectedFlag == null ? "" : selectedFlag.getShow();
        int generation = ++tmdbDialogGeneration;
        int requestLoadGeneration = loadGeneration;
        Flag requestFlag = selectedFlag;
        List<Episode> requestEpisodes = new ArrayList<>(episodes);
        Map<Integer, Integer> requestCounts = new HashMap<>(seasonEpisodeCounts);
        String requestSnapshot = EpisodeSeasonSnapshot.fingerprint(requestEpisodes, requestCounts);
        AiEpisodeSeasonService service = new AiEpisodeSeasonService(config);
        aiSeasonService = service;
        aiSeasonLoadingDialog = com.fongmi.android.tv.ui.dialog.AiAnalysisDialog.show(
                this, () -> cancelAiSeasonAnalysis(true));
        detailTasks.submit(Task.largeExecutor(), () -> {
            AiEpisodeSeasonService.AnalysisResult result = service.analyze(title, flagName, requestEpisodes, requestCounts);
            runOnAliveUi(() -> {
                if (aiSeasonService != service) return;
                finishAiSeasonAnalysis(service);
                if (generation != tmdbDialogGeneration || requestLoadGeneration != loadGeneration
                        || !isAiSeasonSnapshotCurrent(requestFlag, requestSnapshot)) return;
                showAiSeasonAnalysisResult(result, requestFlag, requestSnapshot);
            });
        });
    }

    private void finishAiSeasonAnalysis(AiEpisodeSeasonService service) {
        if (aiSeasonService != service) return;
        aiSeasonService = null;
        AlertDialog dialog = aiSeasonLoadingDialog;
        aiSeasonLoadingDialog = null;
        if (dialog != null) dialog.dismiss();
    }

    private void cancelAiSeasonAnalysis(boolean notify) {
        AiEpisodeSeasonService service = aiSeasonService;
        aiSeasonService = null;
        if (service != null) service.cancel();
        AlertDialog dialog = aiSeasonLoadingDialog;
        aiSeasonLoadingDialog = null;
        if (dialog != null) dialog.dismiss();
        if (notify && service != null) {
            tmdbDialogGeneration++;
            Notify.show(R.string.tmdb_season_ai_cancelled);
        }
    }

    private boolean isAiSeasonSnapshotCurrent(Flag requestFlag, String requestSnapshot) {
        if (requestFlag != selectedFlag) return false;
        List<Episode> currentEpisodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        return requestSnapshot.equals(EpisodeSeasonSnapshot.fingerprint(currentEpisodes, seasonEpisodeCounts));
    }

    private void showAiSeasonAnalysisResult(
            AiEpisodeSeasonService.AnalysisResult result,
            Flag requestFlag,
            String requestSnapshot) {
        if (result == null || !result.isSuccess()) {
            Notify.show(R.string.tmdb_season_ai_failed);
            return;
        }
        String message = aiSeasonAnalysisMessage(result);
        if (result.getMode() == AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING) {
            Notify.show(R.string.tmdb_season_ai_explicit_preview_only);
            ChoiceDialog.showConfirm(this, R.string.tmdb_season_ai_preview_title, message, () -> {
            });
            return;
        }
        ChoiceDialog.showConfirm(this, R.string.tmdb_season_ai_preview_title, message, R.string.tmdb_season_ai_apply,
                () -> {
                    if (!isAiSeasonSnapshotCurrent(requestFlag, requestSnapshot)) {
                        Notify.show(R.string.tmdb_season_ai_failed);
                        return;
                    }
                    applyAiSeasonAnalysis(result);
                });
    }

    private String aiSeasonAnalysisMessage(AiEpisodeSeasonService.AnalysisResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(result.getMode()).append(" · ").append(Math.round(result.getConfidence() * 100)).append('%');
        if (result.getSeasonNumber() >= 0) builder.append("\nSeason ").append(result.getSeasonNumber());
        if (!TextUtils.isEmpty(result.getSummary())) builder.append("\n\n").append(result.getSummary());
        for (String warning : result.getWarnings()) builder.append("\n• ").append(warning);
        if (result.getMode() == AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING) {
            builder.append("\n\n").append(getString(R.string.tmdb_season_ai_explicit_preview_only));
        }
        return builder.toString();
    }

    private void applyAiSeasonAnalysis(AiEpisodeSeasonService.AnalysisResult result) {
        if (result.getMode() == AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING) {
            Notify.show(R.string.tmdb_season_ai_explicit_preview_only);
            return;
        }
        switch (result.getMode()) {
            case SINGLE_SEASON -> {
                saveTmdbSeasonBinding(result.getSeasonNumber(), TmdbSeasonMatchCache.Mode.MANUAL_SEASON);
                selectedSeasonNumber = result.getSeasonNumber();
                refreshEpisodesAfterSeasonBinding();
            }
            case KEEP_ORIGINAL -> {
                saveTmdbSeasonBinding(null, TmdbSeasonMatchCache.Mode.MANUAL_FLAT);
                selectedSeasonNumber = -1;
                refreshEpisodesAfterSeasonBinding();
            }
            case FLAT_BY_COUNTS -> {
                List<Episode> episodes = selectedFlag == null ? List.of() : selectedFlag.getEpisodes();
                if (!canApplyValidatedFlatSeasonMapping(episodes)) {
                    Notify.show(R.string.tmdb_season_ai_failed);
                    return;
                }
                saveTmdbSeasonBinding(null, TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE);
                selectedSeasonNumber = -1;
                refreshEpisodesAfterSeasonBinding();
            }
            case EXPLICIT_MAPPING -> {
            }
        }
        Notify.show(R.string.tmdb_season_match_saved);
    }

    private TmdbSeasonResolver.Resolution tmdbSeasonChoiceResolution() {
        return TmdbSeasonResolver.resolve(
                -1,
                tmdbSeasonBinding,
                List.of(),
                -1,
                seasonNumbers,
                seasonEpisodeCounts,
                selectedFlag == null || selectedFlag.getEpisodes() == null ? 0 : selectedFlag.getEpisodes().size(),
                sourceEpisodeNumbers(selectedFlag == null ? null : selectedFlag.getEpisodes()));
    }

    private void loadTmdbSeasonBinding() {
        synchronized (Setting.class) {
        tmdbSeasonBinding = null;
        if (matchedTmdbItem == null || vod == null) return;
        String sourceTitle = !TextUtils.isEmpty(sourceVodName) ? sourceVodName : vod.getName();
        TmdbSeasonMatchCache cache = Setting.getTmdbSeasonMatchCache();
        boolean allowLegacyFallback = vod.getFlags() != null && vod.getFlags().size() <= 1;
        if (cache.removeIfMediaChanged(getKeyText(), getIdText(), sourceTitle, selectedSeasonFlagKey(),
                matchedTmdbItem.getTmdbId(), matchedTmdbItem.getMediaType(), allowLegacyFallback)) {
            Setting.putTmdbSeasonMatchCache(cache);
            SpiderDebug.log("tmdb-season", "discard changed media source=%s tmdb=%d type=%s",
                    sourceTitle, matchedTmdbItem.getTmdbId(), matchedTmdbItem.getMediaType());
        }
        if (!matchedTmdbItem.isTv() || seasonNumbers.isEmpty()) return;
        TmdbSeasonMatchCache.Entry binding = cache.find(getKeyText(), getIdText(), sourceTitle,
                selectedSeasonFlagKey(), matchedTmdbItem.getTmdbId(), allowLegacyFallback);
        int bindingTmdbEpisodeCount = binding == null || binding.getSeasonNumber() == null
                ? 0 : seasonEpisodeCounts.getOrDefault(binding.getSeasonNumber(), 0);
        String fingerprint = TmdbUIAdapter.manualBindingFingerprint(
                sourceTitle, selectedFlag, selectedSeasonFlagKey());
        if (binding != null && !binding.isFresh(fingerprint,
                selectedFlag == null || selectedFlag.getEpisodes() == null ? 0 : selectedFlag.getEpisodes().size(),
                bindingTmdbEpisodeCount)) {
            cache.remove(getKeyText(), getIdText(), sourceTitle, selectedSeasonFlagKey());
            Setting.putTmdbSeasonMatchCache(cache);
            binding = null;
        }
        if (binding != null && binding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_SEASON
                && (binding.getSeasonNumber() == null || !seasonNumbers.contains(binding.getSeasonNumber()))) {
            cache.remove(getKeyText(), getIdText(), sourceTitle, selectedSeasonFlagKey());
            Setting.putTmdbSeasonMatchCache(cache);
            SpiderDebug.log("tmdb-season", "discard removed season source=%s tmdb=%d", sourceTitle, matchedTmdbItem.getTmdbId());
            return;
        }
        boolean validMultiBinding = binding == null || binding.getMode() != TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE
                || binding.getSegments().isEmpty()
                ? canApplyValidatedFlatSeasonMapping(selectedFlag == null ? null : selectedFlag.getEpisodes())
                : TmdbSeasonResolver.hasValidPersistedSegments(binding, seasonNumbers, seasonEpisodeCounts,
                selectedFlag == null || selectedFlag.getEpisodes() == null ? 0 : selectedFlag.getEpisodes().size());
        if (binding != null && binding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE
                && !validMultiBinding) {
            cache.remove(getKeyText(), getIdText(), sourceTitle, selectedSeasonFlagKey());
            Setting.putTmdbSeasonMatchCache(cache);
            SpiderDebug.log("tmdb-season", "discard unsafe multi-slice source=%s tmdb=%d", sourceTitle, matchedTmdbItem.getTmdbId());
            binding = null;
        }

        tmdbSeasonBinding = binding;
        clearSeasonResolutionCache();
        Map<String, String> routeFingerprints = new LinkedHashMap<>();
        if (vod.getFlags() != null) {
            for (int i = 0; i < vod.getFlags().size(); i++) {
                Flag flag = vod.getFlags().get(i);
                String key = TmdbUIAdapter.flagKey(flag, i);
                routeFingerprints.put(key, TmdbUIAdapter.sourceFingerprint(flag, key, seasonEpisodeCounts));
            }
        }
        boolean cacheChanged = cache.pruneRouteBindings(getKeyText(), getIdText(), routeFingerprints);
        List<Integer> available = availableSeasonNumbers(selectedFlag == null ? null : selectedFlag.getEpisodes());
        TmdbSeasonScope scope = available.size() == 1
                ? TmdbSeasonScope.known(available.get(0))
                : TmdbSeasonScope.multi(available);
        cacheChanged |= cache.recordRouteBinding(getKeyText(), getIdText(), selectedSeasonFlagKey(),
                selectedFlag == null ? "" : selectedFlag.getFlag(),
                TmdbUIAdapter.sourceFingerprint(selectedFlag, selectedSeasonFlagKey(), seasonEpisodeCounts),
                matchedTmdbItem.getTmdbId(),
                matchedTmdbItem.getMediaType(), scope);
        if (cacheChanged) Setting.putTmdbSeasonMatchCache(cache);
        }
    }

    private void updateTmdbSeasonActionVisibility() {
        boolean visible = matchedTmdbItem != null && matchedTmdbItem.isTv() && !seasonNumbers.isEmpty() && canMatchTmdb();
        binding.episodeTitle.setVisibility(visible ? View.VISIBLE : View.GONE);
        binding.episodeTitle.setClickable(visible);
        binding.episodeTitle.setFocusable(visible);
        binding.episodeTitle.setFocusableInTouchMode(false);
        if (visible) binding.episodeTitle.setText(detailSeasonButtonLabel());
        binding.episodeTitle.setContentDescription(visible
                ? getString(R.string.tmdb_season_match_current, binding.episodeTitle.getText())
                : binding.episodeTitle.getText());
        Drawable icon = visible ? getDrawable(R.drawable.ic_expand_more) : null;
        if (icon != null) icon.setTint(binding.episodeTitle.getCurrentTextColor());
        binding.episodeTitle.setCompoundDrawablePadding(visible ? ResUtil.dp2px(4) : 0);
        binding.episodeTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null);
        setEpisodeTitleButton(binding.episodeTitle, currentThemeColors());
    }

    private boolean canApplyValidatedFlatSeasonMapping(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty() || seasonNumbers.size() <= 1) return false;
        return EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(
                sourceEpisodeNumbers(episodes), seasonNumbers, seasonEpisodeCounts).size() > 1;
    }

    private static List<Integer> sourceEpisodeNumbers(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return List.of();
        List<Integer> numbers = new ArrayList<>(episodes.size());
        for (Episode episode : episodes) numbers.add(episode == null ? -1 : episode.getNumber());
        return numbers;
    }

    private void saveTmdbSeasonBinding(Integer seasonNumber, TmdbSeasonMatchCache.Mode mode) {
        if (matchedTmdbItem == null || vod == null) return;
        String sourceTitle = !TextUtils.isEmpty(sourceVodName) ? sourceVodName : vod.getName();
        int sourceEpisodeCount = selectedFlag == null || selectedFlag.getEpisodes() == null ? 0 : selectedFlag.getEpisodes().size();
        int tmdbEpisodeCount = seasonNumber == null ? 0 : seasonEpisodeCounts.getOrDefault(seasonNumber, 0);
        synchronized (Setting.class) {
            TmdbSeasonMatchCache cache = Setting.getTmdbSeasonMatchCache();
            cache.put(getKeyText(), getIdText(), sourceTitle, selectedSeasonFlagKey(), matchedTmdbItem.getTmdbId(),
                    matchedTmdbItem.getMediaType(), seasonNumber, mode,
                    TmdbUIAdapter.manualBindingFingerprint(sourceTitle, selectedFlag, selectedSeasonFlagKey()),
                    sourceEpisodeCount, tmdbEpisodeCount,
                    mode == TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE ? selectedFlagSegments() : List.of());
            Setting.putTmdbSeasonMatchCache(cache);
            tmdbSeasonBinding = cache.find(getKeyText(), getIdText(), sourceTitle, selectedSeasonFlagKey(), matchedTmdbItem.getTmdbId());
        }
        clearSeasonResolutionCache();
    }

    private void clearTmdbSeasonBinding() {
        if (matchedTmdbItem == null || vod == null) return;
        String sourceTitle = !TextUtils.isEmpty(sourceVodName) ? sourceVodName : vod.getName();
        synchronized (Setting.class) {
            TmdbSeasonMatchCache cache = Setting.getTmdbSeasonMatchCache();
            cache.remove(getKeyText(), getIdText(), sourceTitle, selectedSeasonFlagKey());
            Setting.putTmdbSeasonMatchCache(cache);
        }
        tmdbSeasonBinding = null;
        selectedSeasonNumber = -1;
        refreshEpisodesAfterSeasonBinding();
    }

    private String selectedSeasonFlagKey() {
        if (selectedFlag == null) return "";
        int index = TmdbUIAdapter.flagIndex(vod == null ? null : vod.getFlags(), selectedFlag);
        return TmdbUIAdapter.flagKey(selectedFlag, index);
    }

    private List<TmdbSeasonSegment> selectedFlagSegments() {
        return TmdbSeasonResolver.flatSeasonSegments(
                sourceEpisodeNumbers(selectedFlag == null ? null : selectedFlag.getEpisodes()),
                seasonNumbers, seasonEpisodeCounts);
    }

    private void refreshEpisodesAfterSeasonBinding() {
        clearBoundTmdbEpisodeMetadata();
        clearSeasonResolutionCache();
        lastEpisodeMediaSeason = Integer.MIN_VALUE;
        renderSeasonSelection();
        renderEpisodes();
        binding.episodeContainer.post(() -> rerenderEpisodeViewportOnly(false, true, true));
    }

    private void clearBoundTmdbEpisodeMetadata() {
        if (selectedFlag == null || selectedFlag.getEpisodes() == null) return;
        for (Episode episode : selectedFlag.getEpisodes()) {
            if (episode == null) continue;
            episode.setTmdbEpisode(null);
            episode.setDisplayName("");
        }
        tmdbEpisodes.clear();
    }

    private void showManualTmdbMatchDialog() {
        if (!tmdbConfig.isReady()) {
            Notify.show(getString(R.string.detail_tmdb_need_key));
            return;
        }
        if (!isTmdbAllowedForCurrentSite()) {
            Notify.show(R.string.detail_tmdb_site_disabled);
            return;
        }
        binding.loading.setVisibility(View.VISIBLE);
        int generation = loadGeneration;
        int dialogGeneration = ++tmdbDialogGeneration;
        String query = getTmdbSearchQuery();
        String fallback = getNameText();
        detailTasks.submit(Task.largeExecutor(), () -> {
            try {
                List<TmdbItem> items = searchTmdbItems(query, fallback);
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) {
                        binding.loading.setVisibility(View.GONE);
                        return;
                    }
                    binding.loading.setVisibility(View.GONE);
                    showTmdbMatchDialog(items, false);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) {
                        binding.loading.setVisibility(View.GONE);
                        return;
                    }
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private TmdbItem getCachedTmdbMatch() {
        if (!isTmdbAllowedForCurrentSite()) return null;
        TmdbMatchCache cache = Setting.getTmdbMatchCache();
        // 手动选择优先，且不做标题兼容性校验：用户之所以手动选，正是因为标题解析结果不对。
        TmdbItem manual = cache.findManual(getKeyText(), getIdText(), getTmdbRawTitle());
        if (manual != null) return manual;
        TmdbItem item = cache.find(getKeyText(), getIdText(), getTmdbRawTitle());
        if (!isCachedTmdbMatchCompatible(item)) return null;
        return item;
    }

    private boolean isCachedTmdbMatchCompatible(TmdbItem item) {
        if (item == null || TextUtils.isEmpty(item.getTitle())) return false;
        String parsedTitle = new MediaTitleParser().cleanTitle(getTmdbRawTitle());
        if (TextUtils.isEmpty(parsedTitle)) return true;
        boolean compatible = normalize(item.getTitle()).equals(normalize(parsedTitle));
        if (!compatible) logTmdbMatch("缓存匹配跳过：缓存标题=%s，当前解析标题=%s，原始标题=%s", item.getTitle(), parsedTitle, getTmdbRawTitle());
        return compatible;
    }

    private boolean canMatchTmdb() {
        return tmdbConfig != null && tmdbConfig.isReady() && isTmdbAllowedForCurrentSite();
    }

    private boolean isTmdbAllowedForCurrentSite() {
        if (tmdbConfig == null) return false;
        if (WebHomeInlineVodStore.KEY.equals(getKeyText())) return TmdbSitePolicy.isEnabled(tmdbConfig, getKeyText(), getIdText());
        Site site = getCurrentSite();
        if (site != null && !site.isEmpty()) return tmdbConfig.isSiteEnabled(site.getKey(), site.getName());
        return TmdbSitePolicy.isEnabled(tmdbConfig, getKeyText(), getIdText());
    }

    private boolean isManualTmdbMatch() {
        return isTmdbAllowedForCurrentSite()
                && Setting.getTmdbMatchCache().isManual(getKeyText(), getIdText(), getTmdbRawTitle());
    }

    private void saveTmdbMatch(TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return;
        // 读-改-写要整体互斥：自动匹配在后台线程写，手动选择在主线程写，
        // 不加锁会让后到的自动结果基于旧快照覆盖掉刚落盘的手动选择。
        synchronized (Setting.class) {
            TmdbMatchCache cache = Setting.getTmdbMatchCache();
            cache.put(getKeyText(), getIdText(), getTmdbRawTitle(), item);
            Setting.putTmdbMatchCache(cache);
        }
    }

    /** 记录用户手动选定的 TMDB 条目，覆盖此前的自动匹配并锁定后续自动写入。 */
    private void saveManualTmdbMatch(TmdbItem item) {
        if (item == null || item.getTmdbId() <= 0) return;
        List<String> aliases = tmdbSourceTitleAliases();
        synchronized (Setting.class) {
            TmdbMatchCache cache = Setting.getTmdbMatchCache();
            cache.putManual(getKeyText(), getIdText(), aliases, item);
            Setting.putTmdbMatchCache(cache);
        }
    }

    /**
     * 站源标题在富集后会被改写，手动绑定要覆盖所有可能作为读取键的别名。
     * 别名只取站源侧信号，且与 getTmdbRawTitle() 的优先级一一对应：
     * vod.getName() 在富集后已是"上一次"的 TMDB 标题，把它当别名写进去会留下一条
     * 指向旧条目的精确键（A→B→C 连续切换后 key(标题A) 仍指向 B），历史记录里存的
     * 正是那个旧标题，History.resolveTmdbIdentity 反查就会读回旧选择。
     * 因此只在 sourceVodName 为空、即它确实是读取键时才纳入。
     */
    private List<String> tmdbSourceTitleAliases() {
        List<String> aliases = new ArrayList<>();
        addTmdbSourceTitleAlias(aliases, sourceVodName);
        if (aliases.isEmpty()) addTmdbSourceTitleAlias(aliases, vod == null ? "" : vod.getName());
        addTmdbSourceTitleAlias(aliases, getNameText());
        return aliases;
    }

    private void addTmdbSourceTitleAlias(List<String> aliases, String title) {
        if (TextUtils.isEmpty(title) || aliases.contains(title)) return;
        aliases.add(title);
    }

    private void saveManualTmdbLearning(TmdbItem item) {
        if (item == null || item.getTitle().isEmpty()) return;
        String rawTitle = !TextUtils.isEmpty(sourceVodName) ? sourceVodName : vod != null ? vod.getName() : getNameText();
        MediaTitleParser parser = new MediaTitleParser();
        MediaTitleLearningStore.load().putManual(
                getKeyText(),
                getIdText(),
                rawTitle,
                parser.cleanTitle(rawTitle),
                item.getTitle(),
                item.getMediaType(),
                parser.firstYear(vod == null ? "" : vod.getYear()),
                parser.seasonNumber(rawTitle),
                MediaTitleLearningExample.SOURCE_TMDB_MANUAL);
    }

    private String getTmdbSearchQuery() {
        String tmdbTitle = matchedTmdbTitle();
        if (!TextUtils.isEmpty(tmdbTitle)) return tmdbTitle;
        if (vod != null && !TextUtils.isEmpty(vod.getName())) return cleanTmdbSearchQuery(vod.getName());
        return cleanTmdbSearchQuery(getNameText());
    }

    private AutoTmdbMatch searchResolvedTmdbMatch() throws Exception {
        return searchResolvedTmdbMatch(getTmdbRawTitle(), vod);
    }

    private AutoTmdbMatch searchResolvedTmdbMatch(String rawTitle, @Nullable Vod sourceVod) throws Exception {
        String searchKeyword = getTmdbSearchKeyword();
        MediaTitleRequest request = buildTmdbTitleRequest(rawTitle, sourceVod, searchKeyword, false);
        MediaTitleResolver resolver = new MediaTitleResolver();
        List<String> attempted = new ArrayList<>();
        MediaTitleResolution resolution = resolver.resolve(request);
        AutoTmdbMatch match = searchResolvedTmdbMatch(rawTitle, resolution, attempted);
        if (match.item() != null) return match;
        AutoTmdbMatch keywordMatch = searchResolvedTmdbMatch(rawTitle, searchKeyword, attempted);
        if (keywordMatch.item() != null) return keywordMatch;
        List<String> cleanedTitles = resolver.queryCleanedTitles(request, 4);
        logTmdbMatch("清洗标题兜底：原始标题=%s，候选=%s", rawTitle, cleanedTitles);
        AutoTmdbMatch cleanedMatch = searchResolvedTmdbMatch(rawTitle, cleanedTitles, MediaTitleResolution.SOURCE_CLEANED, attempted);
        if (cleanedMatch.item() != null) return cleanedMatch;
        MediaTitleRequest aiRequest = buildTmdbTitleRequest(rawTitle, sourceVod, searchKeyword, true);
        MediaTitleResolution fallback = resolver.resolveWithAiFallback(aiRequest);
        logTmdbMatch("AI 标题兜底：source=%s，原始标题=%s，候选=%s", fallback.getSource(), rawTitle, fallback.queryTitles());
        AutoTmdbMatch fallbackMatch = searchResolvedTmdbMatch(rawTitle, fallback, attempted);
        if (!fallbackMatch.items().isEmpty()) return fallbackMatch;
        if (!cleanedMatch.items().isEmpty()) return cleanedMatch;
        if (!keywordMatch.items().isEmpty()) return keywordMatch;
        return !match.items().isEmpty() ? match : fallbackMatch;
    }

    /** 搜索关键词兜底：卡片名称匹配失败后，用用户当时输入的搜索词再匹配一次。 */
    private AutoTmdbMatch searchResolvedTmdbMatch(String rawTitle, String searchKeyword, List<String> attempted) throws Exception {
        if (TextUtils.isEmpty(searchKeyword)) return new AutoTmdbMatch(null, List.of());
        logTmdbMatch("搜索词兜底：原始标题=%s，搜索关键词=%s", rawTitle, searchKeyword);
        return searchResolvedTmdbMatch(rawTitle, List.of(searchKeyword), SOURCE_SEARCH_KEYWORD, attempted);
    }

    private AutoTmdbMatch searchResolvedTmdbMatch(String rawTitle, MediaTitleResolution resolution, List<String> attempted) throws Exception {
        return searchResolvedTmdbMatch(rawTitle, automaticTmdbQueries(resolution, rawTitle), resolution.getSource(), attempted);
    }

    private AutoTmdbMatch searchResolvedTmdbMatch(String rawTitle, List<String> titles, String source, List<String> attempted) throws Exception {
        List<TmdbItem> lastItems = new ArrayList<>();
        for (String title : titles) {
            String query = cleanTmdbSearchQuery(title);
            if (TextUtils.isEmpty(query) || containsQuery(attempted, query)) continue;
            attempted.add(query);
            List<TmdbItem> items = searchTmdbItems(query, title);
            lastItems = items;
            logTmdbMatch("搜索完成：原始标题=%s，解析来源=%s，候选标题=%s，实际搜索词=%s，返回数量=%d", rawTitle, source, title, query, items.size());
            TmdbItem item = chooseTmdbMatch(items, query, null);
            if (item == null) {
                SplitYearSearch split = searchSplitYearTmdbItems(query, null);
                if (split != null) {
                    lastItems = split.items();
                    item = chooseTmdbMatch(lastItems, split.query(), null);
                }
            }
            if (item != null) return new AutoTmdbMatch(item, lastItems);
        }
        return new AutoTmdbMatch(null, lastItems);
    }

    private MediaTitleRequest buildTmdbTitleRequest(String rawTitle, @Nullable Vod sourceVod, String searchKeyword, boolean allowAi) {
        Vod detailVod = sourceVod != null ? sourceVod : vod;
        return MediaTitleRequest.builder()
                .siteKey(getKeyText())
                .vodId(getIdText())
                .rawTitle(rawTitle)
                .rawRemarks(detailVod == null ? getMarkText() : coalesce(detailVod.getRemarks(), getMarkText()))
                .searchKeyword(searchKeyword)
                .vodYear(detailVod == null ? "" : detailVod.getYear())
                .source(MediaTitleLearningExample.SOURCE_TMDB_AUTO)
                .allowAi(allowAi)
                .build();
    }

    private String getTmdbRawTitle() {
        return !TextUtils.isEmpty(sourceVodName) ? sourceVodName : vod != null && !TextUtils.isEmpty(vod.getName()) ? vod.getName() : getNameText();
    }

    private String getTmdbSearchKeyword() {
        return Objects.toString(getIntent().getStringExtra("search_keyword"), "");
    }

    private List<String> automaticTmdbQueries(MediaTitleResolution resolution, String rawTitle) {
        List<String> result = new ArrayList<>();
        for (String title : resolution.queryTitles()) {
            if (TextUtils.isEmpty(title)) continue;
            if (title.equals(rawTitle) && shouldSkipRawTmdbQuery(rawTitle, resolution)) continue;
            addQuery(result, title);
        }
        if (result.isEmpty()) addQuery(result, rawTitle);
        return result;
    }

    private boolean shouldSkipRawTmdbQuery(String rawTitle, MediaTitleResolution resolution) {
        String canonical = resolution.getCanonicalTitle();
        return !TextUtils.isEmpty(rawTitle)
                && !TextUtils.isEmpty(canonical)
                && !normalize(rawTitle).equals(normalize(canonical));
    }

    private void addQuery(List<String> queries, String query) {
        String value = Objects.toString(query, "").trim();
        if (TextUtils.isEmpty(value) || containsQuery(queries, value)) return;
        queries.add(value);
    }

    private boolean containsQuery(List<String> queries, String query) {
        for (String item : queries) if (item.equalsIgnoreCase(query)) return true;
        return false;
    }

    private void searchTmdb(String keyword, TmdbSearchDialog dialog) {
        dialog.loading();
        int generation = loadGeneration;
        int dialogGeneration = ++tmdbDialogGeneration;
        detailTasks.submit(Task.largeExecutor(), () -> {
            try {
                String query = cleanTmdbSearchQuery(keyword);
                List<TmdbItem> items = searchTmdbItems(query, keyword);
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) return;
                    dialog.updateItems(items);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || dialogGeneration != tmdbDialogGeneration) return;
                    dialog.updateItems(new ArrayList<>());
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void scheduleManualTmdbEpisodeRebind(int generation, TmdbItem item) {
        pendingManualTmdbEpisodeRebindGeneration = generation;
        pendingManualTmdbEpisodeRebindItem = item;
        if (hasWindowFocus()) flushPendingManualTmdbEpisodeRebind();
    }

    private void flushPendingManualTmdbEpisodeRebind() {
        int generation = pendingManualTmdbEpisodeRebindGeneration;
        TmdbItem item = pendingManualTmdbEpisodeRebindItem;
        if (generation < 0 || item == null) return;
        pendingManualTmdbEpisodeRebindGeneration = -1;
        pendingManualTmdbEpisodeRebindItem = null;
        if (isFinishing() || isDestroyed()) return;
        binding.episodeContainer.postOnAnimation(() -> {
            if (isFinishing() || isDestroyed()) return;
            if (generation != tmdbApplyGeneration || !isSameTmdbItem(item, matchedTmdbItem)) return;
            rerenderEpisodeViewportOnly(false, true, true);
        });
    }

    private void applyManualTmdb(TmdbItem item) {
        binding.loading.setVisibility(View.VISIBLE);
        int generation = loadGeneration;
        int applyGeneration = ++tmdbApplyGeneration;
        tmdbDialogGeneration++;
        sourceSearchGeneration++;
        pendingTmdbSeasonChoice = item != null && item.isTv() ? item : null;
        detailTasks.submit(Task.largeExecutor(), () -> {
            try {
                TmdbBundle bundle = loadTmdbBundle(item);
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || applyGeneration != tmdbApplyGeneration || vod == null) {
                        // guard 失败时也要隐藏 loading，否则覆盖层会阻挡所有界面交互
                        binding.loading.setVisibility(View.GONE);
                        return;
                    }
                    resetManualTmdbPresentation();
                    // 必须先落盘：applyTmdbResultNow 里的 enrichVod 会把 vod.getName()
                    // 改写成 TMDB 标题，之后再取别名就会把 TMDB 标题当成站源标题记进去。
                    saveManualTmdbMatch(bundle.item());
                    applyTmdbResultNow(new TmdbLoadResult(bundle, List.of()));
                    scheduleManualTmdbEpisodeRebind(applyGeneration, bundle.item());
                    saveManualTmdbLearning(bundle.item());
                    maybeShowPendingTmdbSeasonDialog();
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(R.string.detail_tmdb_match_saved);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (generation != loadGeneration || applyGeneration != tmdbApplyGeneration) {
                        binding.loading.setVisibility(View.GONE);
                        return;
                    }
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void resetManualTmdbPresentation() {
        tmdbEpisodeDetailGeneration++;
        selectedEpisode = null;
        selectedSeasonNumber = -1;
        lastEpisodeMediaSeason = Integer.MIN_VALUE;
        clearEpisodeRenderCaches();
        resetEpisodeRange();
        tmdbSeasonEpisodes.clear();
        tmdbSeasonCast.clear();
        tmdbSeasonPhotos.clear();
        loadingSeasons.clear();
        if (episodeAdapter != null) {
            episodeAdapter.setFallbackStillUrl("");
            episodeAdapter.setItems(List.of(), Map.of(), null);
        }
        if (episodePhotoAdapter != null) episodePhotoAdapter.setItems(List.of());
        if (castAdapter != null) castAdapter.setItems(List.of());
        if (creatorAdapter != null) creatorAdapter.setItems(List.of());
        if (relatedAdapter != null) relatedAdapter.setItems(List.of());
        if (relatedVideoAdapter != null) relatedVideoAdapter.setItems(List.of());
        relatedVideoItems.clear();
        relatedVideoLoading = false;
        relatedVideoContextKey = "";
        relatedVideoGeneration++;
        if (personalTmdbAdapter != null) personalTmdbAdapter.setItems(List.of());
        if (personalDoubanAdapter != null) personalDoubanAdapter.setItems(List.of());
        if (personalAiAdapter != null) personalAiAdapter.setItems(List.of());
    }

    private void enrichVod() {
        if (matchedTmdbItem != null) {
            String title = matchedTmdbTitle();
            if (!TextUtils.isEmpty(title)) vod.setName(title);
            if (!TextUtils.isEmpty(matchedTmdbItem.getPosterUrl())) vod.setPic(matchedTmdbItem.getPosterUrl());
        }
        String overview = tmdbOverview();
        if (!TextUtils.isEmpty(overview)) vod.setContent(overview);
        if (matchedTmdbDetail == null) return;
        String poster = tmdbService.image(tmdbConfig.getImageBase(), string(matchedTmdbDetail, "poster_path"));
        if (!TextUtils.isEmpty(poster)) {
            vod.setPic(poster);
        } else if ((TextUtils.isEmpty(vod.getPic()) || vod.getPic().startsWith("data:")) && matchedTmdbItem != null) {
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
        bindRatings();
        bindOverview();
        bindSource();
        bindFlags();
        loadRelatedVideosForCurrentContext();
        bindTmdbSection();
        updateKeepState();
        requestDetailPlayFocus();
    }

    private void requestDetailPlayFocus() {
        if (Util.isMobile() || isInlinePlayerMode() || binding.play.getVisibility() != View.VISIBLE) return;
        binding.play.post(() -> {
            if (!isFinishing() && binding != null && binding.play.getVisibility() == View.VISIBLE && !isInlinePlayerMode()) binding.play.requestFocus();
        });
    }

    private void bindInitialArtwork() {
        String initialTmdbPoster = getTmdbPosterText();
        ImgUtil.load(getNameText(), TextUtils.isEmpty(initialTmdbPoster) ? getPicText() : TmdbImageSelector.originalUrl(initialTmdbPoster), binding.poster);
        bindBackdropImage(getNameText(), getBackdropText(), getPicText());
    }

    private void bindBackdrop() {
        boolean wallpaperBackdrop = useAppWallpaperBackdrop();
        bindBackdropImage(vod.getName(), wallpaperBackdrop ? "" : tmdbBackdropUrl(), wallpaperBackdrop ? "" : vod.getPic());
        episodeAdapter.setFallbackStillUrl(episodeFallbackStillUrl());
        ImgUtil.load(vod.getName(), tmdbPosterUrl(), binding.poster);
    }

    private String tmdbPosterUrl() {
        String fallback = coalesce(
                matchedTmdbItem == null ? "" : matchedTmdbItem.getPosterUrl(),
                getTmdbPosterText(),
                vod == null ? "" : vod.getPic(),
                getPicText());
        return TmdbImageSelector.poster(matchedTmdbDetail, tmdbConfig == null ? "" : tmdbConfig.getImageBase(), fallback);
    }

    private void bindBackdropImage(String title, String backdrop, String fallback) {
        boolean hasBackdrop = !TextUtils.isEmpty(backdrop) && !TextUtils.equals(backdrop, fallback);
        String image = hasBackdrop ? backdrop : fallback;
        refreshBackdropSurface();
        binding.hero.setVisibility(TextUtils.isEmpty(image) && !useAppWallpaperBackdrop() ? View.GONE : View.VISIBLE);
        cancelBackdropSlideRequest();
        if (TextUtils.isEmpty(image)) {
            ImgUtil.clear(binding.backdropFill);
            ImgUtil.clear(binding.backdrop);
            binding.backdrop.setVisibility(View.INVISIBLE);
            backdropSlideVisibleView = binding.backdropFill;
            bindBackdropSlideSource(title, "");
            return;
        }
        showStaticBackdropImage(title, image);
        bindBackdropSlideSource(title, image);
    }

    private void showStaticBackdropImage(String title, String image) {
        String highResImage = highResTmdbImage(image);
        backdropSlideVisibleView = binding.backdropFill;
        binding.backdropFill.setVisibility(View.VISIBLE);
        binding.backdropFill.setAlpha(backdropSlideAlpha());
        binding.backdropFill.setScaleType(backdropScaleType());
        binding.backdropFill.setTag(R.id.image, highResImage);
        binding.backdrop.setVisibility(View.INVISIBLE);
        ImgUtil.clear(binding.backdrop);
        loadBackdropImage(title, highResImage, binding.backdropFill);
    }

    private void loadBackdropImage(String title, String image, ImageView target) {
        try {
            String highResImage = highResTmdbImage(image);
            Object model = ImgUtil.getUrl(highResImage);
            if (model == null) {
                ImgUtil.clear(target);
                return;
            }
            target.setTag(R.id.image, highResImage);
            com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(target)
                    .load(model)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate();
            request = shouldCropBackdrop() ? request.centerCrop() : request.fitCenter();
            request.into(target);
        } catch (Throwable e) {
            ImgUtil.load(title, highResTmdbImage(image), target, shouldCropBackdrop());
        }
    }

    private void bindBackdropSlideSource(String title, String image) {
        backdropSlideTitle = TextUtils.isEmpty(title) ? "" : title;
        backdropSlidePrimary = highResTmdbImage(image);
        refreshBackdropSlideshow();
    }

    private void refreshBackdropSlideshow() {
        String current = currentBackdropSlideImage();
        cancelBackdropSlideRequest();
        backdropSlideItems.clear();
        backdropSlideFailures.clear();
        for (String photo : detailBackdropSlides) addBackdropSlideItem(photo);
        if (backdropSlideItems.isEmpty()) addBackdropSlideItem(backdropSlidePrimary);
        if (!backdropSlideItems.isEmpty() && !backdropSlideItems.contains(current)) {
            current = backdropSlideItems.get(0);
            backdropSlidePrimary = current;
            binding.hero.setVisibility(View.VISIBLE);
            showStaticBackdropImage(backdropSlideTitle, current);
        }
        backdropSlideIndex = Math.max(0, backdropSlideItems.indexOf(current));
        scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    private void addBackdropSlideItem(String url) {
        String highResUrl = highResTmdbImage(url);
        if (TextUtils.isEmpty(highResUrl) || backdropSlideItems.contains(highResUrl)) return;
        backdropSlideItems.add(highResUrl);
    }

    private String currentBackdropSlideImage() {
        Object tag = visibleBackdropSlideView().getTag(R.id.image);
        return tag instanceof String ? (String) tag : backdropSlidePrimary;
    }

    private void scheduleBackdropSlide(long delayMillis) {
        App.removeCallbacks(backdropSlideNext);
        if (!canRunBackdropSlideshow()) return;
        App.post(backdropSlideNext, delayMillis);
    }

    private boolean canRunBackdropSlideshow() {
        return binding != null && canTouchUi() && Setting.isTmdbDetailBackdropSlide() && backdropSlideItems.size() > 1 && binding.hero.getVisibility() == View.VISIBLE;
    }

    private void loadNextBackdropSlide() {
        if (!canRunBackdropSlideshow() || backdropSlideLoading) return;
        int next = nextBackdropSlideIndex();
        if (next < 0 || next >= backdropSlideItems.size()) return;
        String url = highResTmdbImage(backdropSlideItems.get(next));
        Object model = ImgUtil.getUrl(url);
        if (model == null) {
            onBackdropSlideFailed(++backdropSlideGeneration, next, url);
            return;
        }
        int generation = ++backdropSlideGeneration;
        ImageView targetView = hiddenBackdropSlideView();
        prepareBackdropSlideTarget(targetView);
        backdropSlideLoading = true;
        backdropSlideLoadingView = targetView;
        try {
            com.bumptech.glide.RequestBuilder<Drawable> request = Glide.with(this)
                    .load(model)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .dontAnimate();
            request = shouldCropBackdrop() ? request.centerCrop() : request.fitCenter();
            request.listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                            targetView.post(() -> onBackdropSlideFailed(generation, next, url));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            targetView.post(() -> onBackdropSlideReady(generation, next, url, targetView));
                            return false;
                        }
                    })
                    .into(targetView);
        } catch (Throwable ignored) {
            onBackdropSlideFailed(generation, next, url);
        }
    }

    private void prepareBackdropSlideTarget(ImageView targetView) {
        targetView.setVisibility(View.INVISIBLE);
        targetView.setAlpha(backdropSlideAlpha());
        targetView.setScaleType(backdropScaleType());
        ImgUtil.clear(targetView);
    }

    private ImageView.ScaleType backdropScaleType() {
        return shouldCropBackdrop() ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.FIT_CENTER;
    }

    private void onBackdropSlideReady(int generation, int index, String url, ImageView targetView) {
        if (!isBackdropSlideRequestCurrent(generation, index, url) || targetView != backdropSlideLoadingView) return;
        ImageView oldView = visibleBackdropSlideView();
        backdropSlideLoadingView = null;
        backdropSlideLoading = false;
        backdropSlideFailures.remove(url);
        backdropSlideIndex = index;
        backdropSlideVisibleView = targetView;
        targetView.setTag(R.id.image, url);
        targetView.setAlpha(backdropSlideAlpha());
        targetView.setVisibility(View.VISIBLE);
        if (oldView != targetView) {
            oldView.setVisibility(View.INVISIBLE);
            ImgUtil.clear(oldView);
        }
        scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    private ImageView visibleBackdropSlideView() {
        return backdropSlideVisibleView == binding.backdrop ? binding.backdrop : binding.backdropFill;
    }

    private ImageView hiddenBackdropSlideView() {
        return visibleBackdropSlideView() == binding.backdrop ? binding.backdropFill : binding.backdrop;
    }

    private float backdropSlideAlpha() {
        return isCinemaMode() && !lightTheme ? 0.9f : 1f;
    }

    private int nextBackdropSlideIndex() {
        int count = backdropSlideItems.size();
        if (count <= 1) return -1;
        for (int offset = 1; offset < count; offset++) {
            int index = (backdropSlideIndex + offset) % count;
            String url = backdropSlideItems.get(index);
            if (!backdropSlideFailures.contains(url)) return index;
        }
        backdropSlideFailures.clear();
        return (backdropSlideIndex + 1) % count;
    }

    private boolean isBackdropSlideRequestCurrent(int generation, int index, String url) {
        return generation == backdropSlideGeneration && canRunBackdropSlideshow() && index >= 0 && index < backdropSlideItems.size() && TextUtils.equals(url, backdropSlideItems.get(index));
    }

    private void onBackdropSlideFailed(int generation, int index, String url) {
        if (!isBackdropSlideRequestCurrent(generation, index, url)) return;
        backdropSlideLoading = false;
        clearBackdropSlideLoadingView();
        backdropSlideFailures.add(url);
        backdropSlideIndex = index;
        scheduleBackdropSlide(BACKDROP_SLIDE_RETRY_MS);
    }

    private void cancelBackdropSlideRequest() {
        App.removeCallbacks(backdropSlideNext);
        backdropSlideGeneration++;
        backdropSlideLoading = false;
        clearBackdropSlideLoadingView();
    }

    private void clearBackdropSlideLoadingView() {
        ImageView targetView = backdropSlideLoadingView;
        backdropSlideLoadingView = null;
        if (targetView == null || targetView == visibleBackdropSlideView()) return;
        targetView.setVisibility(View.INVISIBLE);
        ImgUtil.clear(targetView);
    }

    private String tmdbBackdropUrl() {
        if (matchedTmdbDetail != null) {
            List<String> backgrounds = TmdbImageSelector.backgrounds(matchedTmdbDetail, tmdbConfig.getImageBase(), tmdbConfig.getBackdropBase(), preferLandscapeBackground(), 1);
            if (!backgrounds.isEmpty()) return backgrounds.get(0);
        }
        String backdrop = matchedTmdbItem != null ? matchedTmdbItem.getBackdropUrl() : "";
        if (TextUtils.isEmpty(backdrop) && matchedTmdbDetail != null) {
            backdrop = tmdbService.image(tmdbConfig.getBackdropBase(), string(matchedTmdbDetail, "backdrop_path"));
        }
        return TmdbImageSelector.originalUrl(backdrop);
    }

    private boolean preferLandscapeBackground() {
        return ResUtil.getScreenWidth(this) >= ResUtil.getScreenHeight(this);
    }

    private boolean shouldCropBackdrop() {
        return true;
    }

    private ThemeColors currentThemeColors() {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        return isCinemaMode() ? ThemeColors.cinema(lightTheme) : colors;
    }

    private void refreshBackdropSurface() {
        applyBackdropSurface(currentThemeColors());
    }

    private void applyBackdropSurface(ThemeColors colors) {
        int backdropBackground = useAppWallpaperBackdrop() ? Color.TRANSPARENT : backdropFallbackBackground(colors);
        binding.root.setBackgroundColor(backdropBackground);
        binding.hero.setBackgroundColor(backdropBackground);
        binding.backdropFill.setBackgroundColor(backdropBackground);
        binding.backdrop.setBackgroundColor(backdropBackground);
    }

    private boolean useAppWallpaperBackdrop() {
        return vod != null && matchedTmdbDetail == null;
    }

    private int backdropFallbackBackground(ThemeColors colors) {
        return isPlayerMode() ? 0xFF0F141A : colors.background;
    }

    private String episodeFallbackStillUrl() {
        // 分集无专属剧照时按设备比例选兜底：宽屏优先横向剧照，窄屏优先纵向海报，
        // 首选比例缺图时退到另一种，避免宽卡片塞竖海报（只能看到人脸中段）。
        return EpisodeCardImagePolicy.fallbackFor(
                episodeFallbackLandscapeUrl(), episodeFallbackPortraitUrl(), preferLandscapeBackground());
    }

    private String episodeFallbackPortraitUrl() {
        String poster = tmdbPosterUrl();
        if (!TextUtils.isEmpty(poster)) return poster;
        if (vod != null && !TextUtils.isEmpty(vod.getPic())) return vod.getPic();
        return getPicText();
    }

    /**
     * 只认横向图：tmdbBackdropUrl() 内部已按设备比例在 backdrop/poster 间选过一次，
     * 窄屏时它会回竖海报，直接复用会让「窄屏缺海报退剧照」永远拿不到横图。
     */
    private String episodeFallbackLandscapeUrl() {
        if (matchedTmdbDetail != null && tmdbConfig != null) {
            List<String> backdrops = TmdbImageSelector.backdrops(matchedTmdbDetail, tmdbConfig.getBackdropBase(), 1);
            if (!backdrops.isEmpty()) return backdrops.get(0);
        }
        if (matchedTmdbItem != null) return TmdbImageSelector.originalUrl(matchedTmdbItem.getBackdropUrl());
        // 已有匹配条目时到此为止：applyManualTmdb() 换条目不重建 intent，退 tmdb_backdrop 会让
        // 上一条匹配的横图挡在当前条目的海报前面。完全没有匹配（原生源）才退它，此时它仍是进场
        // 条目自己的横图，且换条目走 setIntent 会刷新，宽卡片靠它免吃竖海报。
        return TmdbImageSelector.originalUrl(getBackdropText());
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
        TmdbEpisodeInfo episodeInfo = tmdbEpisodeInfo();
        addMetaChip(episodeInfo.detailText(this));
        if (!episodeInfo.isSeasonScoped()) {
            addMetaChip(currentSeasonContextLabel());
        }
        addMetaChip(firstGenre());
        addMetaChip(firstCountry());
        addMetaChip(certificationLabel());
    }

    private int currentSeasonContextNumber() {
        List<Episode> episodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        List<Integer> availableSeasons = availableSeasonNumbers(episodes);
        if (availableSeasons.isEmpty()) return -1;
        if (availableSeasons.contains(selectedSeasonNumber)) return selectedSeasonNumber;
        return availableSeasons.size() == 1 ? availableSeasons.get(0) : -1;
    }

    private String currentSeasonContextLabel() {
        int season = currentSeasonContextNumber();
        return season < 0 ? "" : getString(R.string.detail_season_format, season);
    }

    private String detailSeasonButtonLabel() {
        int season = currentSeasonContextNumber();
        if (season < 0) {
            Integer resolved = tmdbSeasonChoiceResolution().getSelectedSeason();
            if (resolved != null) season = resolved;
        }
        return season < 0 ? getString(R.string.tmdb_season_button) : getString(R.string.detail_season_format, season);
    }

    private void refreshSeasonContext() {
        updateTmdbSeasonActionVisibility();
        bindMeta();
    }

    private void bindRatings() {
        String key = ratingDisplayKey();
        binding.ratingsContainer.setTag(key);
        binding.ratingsContainer.removeAllViews();
        binding.ratingsContainer.setVisibility(View.GONE);
        String tmdb = tmdbRatingValue();
        if (!TextUtils.isEmpty(tmdb)) addRatingChip(key, "TMDB", tmdb + "/10", 0xFF21D07A);
        addBoxOfficeChip(key);
        fetchDoubanRating(key);
        fetchOmdbRating(key);
        bindExternalLinks();
    }

    private void bindExternalLinks() {
        clearExternalLinks();
        if (matchedTmdbDetail == null) return;
        int count = 0;
        String mediaType = matchedTmdbItem != null && "tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) ? "tv" : "movie";
        int tmdbId = matchedTmdbItem == null ? 0 : matchedTmdbItem.getTmdbId();
        if (tmdbId > 0) count += addExternalLink("TMDB", "https://www.themoviedb.org/" + mediaType + "/" + tmdbId);
        String imdb = string(object(matchedTmdbDetail, "external_ids"), "imdb_id");
        if (!TextUtils.isEmpty(imdb)) count += addExternalLink("IMDB", "https://www.imdb.com/title/" + imdb);
        String title = ratingTitle();
        if (!TextUtils.isEmpty(title)) count += addExternalLink("豆瓣", "https://search.douban.com/movie/subject_search?search_text=" + android.net.Uri.encode(title));
        String query = externalSearchQuery(title);
        if (!TextUtils.isEmpty(query)) {
            count += addExternalLink("烂番茄", "https://www.rottentomatoes.com/search?search=" + android.net.Uri.encode(query));
            count += addExternalLink("Metacritic", "https://www.metacritic.com/search/" + android.net.Uri.encode(query) + "/");
        }
        binding.externalLinksTitle.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
        binding.externalLinksContainer.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void refreshDetailThemeDynamicViews() {
        styleMetaChips();
        styleExternalLinks();
        if (vod == null) return;
        renderFlagSelection();
        updateSeasonButtonStates();
        updateEpisodeRangeButtonStates();
    }

    private void clearExternalLinks() {
        binding.externalLinksContainer.removeAllViews();
        binding.externalLinksTitle.setVisibility(View.GONE);
        binding.externalLinksContainer.setVisibility(View.GONE);
    }

    private String externalSearchQuery(String title) {
        if (TextUtils.isEmpty(title)) return "";
        int year = ratingYear();
        return year <= 0 || title.contains(String.valueOf(year)) ? title : title + " " + year;
    }

    private int addExternalLink(String name, String url) {
        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(url)) return 0;
        ThemeColors colors = currentThemeColors();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(10), ResUtil.dp2px(12), ResUtil.dp2px(10));
        row.setClickable(true);
        row.setFocusable(true);
        row.setOnFocusChangeListener((view, focused) -> styleExternalLinkRow(view, currentThemeColors()));
        row.setOnKeyListener((view, keyCode, event) -> onDetailExternalLinksKey(view, event));
        row.setOnClickListener(view -> openExternalLink(url));

        TextView label = new TextView(this);
        label.setText(name);
        label.setTextSize(14f);
        label.setTypeface(null, android.graphics.Typeface.BOLD);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_open);
        row.addView(icon, new LinearLayout.LayoutParams(ResUtil.dp2px(20), ResUtil.dp2px(20)));
        styleExternalLinkRow(row, colors);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (binding.externalLinksContainer.getChildCount() > 0) params.topMargin = ResUtil.dp2px(8);
        binding.externalLinksContainer.addView(row, params);
        return 1;
    }

    private void styleExternalLinks() {
        if (binding == null || binding.externalLinksContainer == null) return;
        ThemeColors colors = currentThemeColors();
        for (int i = 0; i < binding.externalLinksContainer.getChildCount(); i++) {
            styleExternalLinkRow(binding.externalLinksContainer.getChildAt(i), colors);
        }
    }

    private void styleExternalLinkRow(View view, ThemeColors colors) {
        if (!(view instanceof LinearLayout row)) return;
        boolean focused = row.hasFocus();
        GradientDrawable background = new GradientDrawable();
        background.setColor(isCinemaMode() ? TmdbCinemaTheme.palette(lightTheme).ratingChip() : colors.chip);
        background.setCornerRadius(ResUtil.dp2px(10));
        background.setStroke(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP), focused ? FOCUS_STROKE : colors.line);
        row.setBackground(background);
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof TextView label) label.setTextColor(colors.primary);
            if (child instanceof ImageView icon) icon.setColorFilter(colors.secondary);
        }
    }

    private void openExternalLink(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
        } catch (Throwable e) {
            Notify.show("无法打开链接");
        }
    }

    private void addRatingChip(String key, String platform, String value, int color) {
        if (TextUtils.isEmpty(platform) || TextUtils.isEmpty(value)) return;
        if (!(binding.ratingsContainer.getTag() instanceof String) || !key.equals(binding.ratingsContainer.getTag())) return;
        TextView existing = findRatingChip(platform);
        if (existing != null) {
            existing.setTag(new RatingChipTag(platform, color));
            existing.setText(platform + "  " + value);
            styleDetailRatingChip(existing, color);
            return;
        }
        TextView chip = new TextView(this);
        chip.setTag(new RatingChipTag(platform, color));
        chip.setText(platform + "  " + value);
        chip.setTextSize(12f);
        chip.setTypeface(null, android.graphics.Typeface.BOLD);
        chip.setIncludeFontPadding(false);
        chip.setSingleLine(true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(7), ResUtil.dp2px(10), ResUtil.dp2px(7));
        styleDetailRatingChip(chip, color);
        FlexboxLayout.LayoutParams params = new FlexboxLayout.LayoutParams(FlexboxLayout.LayoutParams.WRAP_CONTENT, FlexboxLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, ResUtil.dp2px(8), ResUtil.dp2px(8));
        chip.setLayoutParams(params);
        binding.ratingsContainer.addView(chip);
        binding.ratingsContainer.setVisibility(View.VISIBLE);
    }

    private void styleDetailRatingChips() {
        if (binding == null || binding.ratingsContainer == null) return;
        for (int i = 0; i < binding.ratingsContainer.getChildCount(); i++) {
            View child = binding.ratingsContainer.getChildAt(i);
            if (child instanceof TextView text && child.getTag() instanceof RatingChipTag tag) styleDetailRatingChip(text, tag.color());
        }
    }

    private void styleDetailRatingChip(TextView chip, int color) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        GradientDrawable background = new GradientDrawable();
        background.setColor(lightTheme ? ratingChipBackground(colors) : 0x6610141A);
        background.setCornerRadius(ResUtil.dp2px(8));
        background.setStroke(ResUtil.dp2px(1), lightTheme ? colors.line : 0x33FFFFFF);
        chip.setBackground(background);
        chip.setTextColor(readableDetailRatingColor(color));
    }

    private int ratingChipBackground(ThemeColors colors) {
        return isCinemaMode() ? TmdbCinemaTheme.palette(true).ratingChip() : colors.chip;
    }

    private int readableDetailRatingColor(int color) {
        if (!lightTheme) return color;
        if (color == 0xFF21D07A || color == 0xFF00B51D) return 0xFF0F7A4A;
        if (color == 0xFFF5C518 || color == 0xFFFFCC33) return 0xFF8A5A00;
        if (color == 0xFFFA320A) return 0xFFB42318;
        return color;
    }

    private TextView findRatingChip(String platform) {
        for (int i = 0; i < binding.ratingsContainer.getChildCount(); i++) {
            View child = binding.ratingsContainer.getChildAt(i);
            if (!(child instanceof TextView text)) continue;
            Object tag = child.getTag();
            if (tag instanceof RatingChipTag chip && platform.equals(chip.platform())) return text;
            if (platform.equals(tag)) return text;
        }
        return null;
    }

    private void fetchDoubanRating(String key) {
        String title = ratingTitle();
        if (TextUtils.isEmpty(title)) return;
        String mediaType = matchedTmdbItem == null ? "" : matchedTmdbItem.getMediaType();
        int year = ratingYear();
        detailTasks.submit(Task.recommendationExecutor(), () -> {
            try {
                PersonalRecommendationService.DoubanRating rating = new PersonalRecommendationService().loadDoubanRating(title, mediaType, year);
                if (rating == null || rating.isEmpty()) return;
                String value = String.format(Locale.US, "%.1f/10", rating.getRating());
                runOnAliveUi(() -> addRatingChip(key, "豆瓣", value, 0xFF00B51D));
            } catch (Throwable ignored) {
            }
        });
    }

    private void fetchOmdbRating(String key) {
        String imdb = string(object(matchedTmdbDetail, "external_ids"), "imdb_id");
        String omdbApiKey = tmdbConfig == null ? "" : tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(imdb) || TextUtils.isEmpty(omdbApiKey)) return;
        detailTasks.submit(Task.recommendationExecutor(), () -> {
            try {
                JsonObject body = OmdbService.fetch(imdb, omdbApiKey);
                if (body == null || "False".equalsIgnoreCase(string(body, "Response"))) return;
                runOnAliveUi(() -> addOmdbRatingChips(key, body));
            } catch (Throwable ignored) {
            }
        });
    }

    private void addOmdbRatingChips(String key, JsonObject body) {
        String imdbRating = string(body, "imdbRating");
        if (!TextUtils.isEmpty(imdbRating) && !"N/A".equalsIgnoreCase(imdbRating)) addRatingChip(key, "IMDB", imdbRating + "/10", 0xFFF5C518);
        for (JsonElement element : array(body, "Ratings")) {
            if (!element.isJsonObject()) continue;
            JsonObject rating = element.getAsJsonObject();
            String source = string(rating, "Source");
            String value = string(rating, "Value");
            if (TextUtils.isEmpty(value) || "N/A".equalsIgnoreCase(value)) continue;
            if ("Rotten Tomatoes".equals(source)) addRatingChip(key, "烂番茄", value, 0xFFFA320A);
            else if ("Metacritic".equals(source)) addRatingChip(key, "Metacritic", value, 0xFFFFCC33);
        }
    }

    private void addBoxOfficeChip(String key) {
        if (matchedTmdbDetail == null || matchedTmdbItem == null) return;
        if (!"movie".equalsIgnoreCase(matchedTmdbItem.getMediaType())) return;

        long revenue = 0;
        try {
            if (matchedTmdbDetail.has("revenue") && !matchedTmdbDetail.get("revenue").isJsonNull()) {
                revenue = matchedTmdbDetail.get("revenue").getAsLong();
            }
        } catch (Exception ignored) {
        }

        if (revenue <= 0) return;

        String formatted = formatBoxOffice(revenue);
        addRatingChip(key, "票房", formatted, 0xFF9C27B0);
    }

    private String formatBoxOffice(long revenue) {
        if (revenue >= 1_000_000_000) {
            return String.format(Locale.US, "$%.2fB", revenue / 1_000_000_000.0);
        } else if (revenue >= 1_000_000) {
            return String.format(Locale.US, "$%.2fM", revenue / 1_000_000.0);
        } else {
            return String.format(Locale.US, "$%,d", revenue);
        }
    }

    private String ratingDisplayKey() {
        int tmdbId = matchedTmdbItem == null ? 0 : matchedTmdbItem.getTmdbId();
        return tmdbId + "|" + ratingTitle() + "|" + ratingYear();
    }

    private String tmdbRatingValue() {
        if (matchedTmdbDetail == null || !matchedTmdbDetail.has("vote_average") || matchedTmdbDetail.get("vote_average").isJsonNull()) return "";
        double value = matchedTmdbDetail.get("vote_average").getAsDouble();
        return value <= 0 ? "" : String.format(Locale.US, "%.1f", value);
    }

    private String ratingTitle() {
        return coalesce(matchedTmdbItem == null ? "" : matchedTmdbItem.getTitle(), vod == null ? "" : vod.getName(), getNameText());
    }

    private int ratingYear() {
        String year = yearLabel();
        if (TextUtils.isEmpty(year) || year.length() < 4) return 0;
        try {
            return Integer.parseInt(year.substring(0, 4));
        } catch (Throwable e) {
            return 0;
        }
    }

    private void bindOverview() {
        String overview = displayOverview();
        binding.overview.setText(overview);
        binding.overview.setVisibility(TextUtils.isEmpty(overview) ? View.GONE : View.VISIBLE);
        if (TextUtils.isEmpty(overview)) {
            binding.overviewToggle.setVisibility(View.GONE);
            return;
        }
        if (shouldShowFullOverview()) {
            overviewExpanded = true;
            binding.overviewToggle.setVisibility(View.GONE);
            binding.overview.setMaxLines(Integer.MAX_VALUE);
            binding.overview.setEllipsize(null);
            return;
        }
        applyOverviewState();
        binding.overview.post(() -> {
            if (isOverviewOverflowing()) {
                binding.overviewToggle.setVisibility(View.VISIBLE);
                applyOverviewState();
            } else {
                binding.overviewToggle.setVisibility(View.GONE);
                binding.overview.setMaxLines(Integer.MAX_VALUE);
                binding.overview.setEllipsize(null);
            }
        });
    }

    private List<TmdbItem> searchTmdbItems(String query, String fallback) throws Exception {
        List<TmdbItem> items = tmdbService.search(query, tmdbConfig);
        logTmdbMatch("TMDB 搜索：搜索词=%s，结果数=%d", query, items.size());
        String fallbackQuery = Objects.toString(fallback, "").trim();
        if (!items.isEmpty() || TextUtils.isEmpty(fallbackQuery) || query.equals(fallbackQuery)) {
            new TmdbMatcher(tmdbService, tmdbConfig).sortSearchResults(items, query);
            return items;
        }
        List<TmdbItem> fallbackItems = tmdbService.search(fallbackQuery, tmdbConfig);
        logTmdbMatch("TMDB 搜索回退：清洗后无结果，原始词=%s，结果数=%d", fallbackQuery, fallbackItems.size());
        new TmdbMatcher(tmdbService, tmdbConfig).sortSearchResults(fallbackItems, fallbackQuery);
        return fallbackItems;
    }

    @Nullable
    private SplitYearSearch searchSplitYearTmdbItems(String keyword, @Nullable Vod sourceVod) throws Exception {
        SplitYearQuery split = splitYearQuery(keyword, sourceVod);
        if (split == null) return null;
        List<TmdbItem> items = tmdbService.search(split.query(), tmdbConfig);
        logTmdbMatch("TMDB 拆年搜索：原始词=%s，拆分标题=%s，年份=%d，结果数=%d", keyword, split.query(), split.year(), items.size());
        return items.isEmpty() ? null : new SplitYearSearch(split.query(), split.year(), items);
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

    private boolean shouldShowFullOverview() {
        return !Util.isMobile();
    }

    private boolean isOverviewOverflowing() {
        if (binding.overview.getLineCount() > 5) return true;
        android.text.Layout layout = binding.overview.getLayout();
        return layout != null && layout.getLineCount() >= 5 && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
    }

    private void bindSource() {
        boolean hasFlags = vod != null && vod.getFlags() != null && !vod.getFlags().isEmpty();
        binding.flagHeader.setVisibility(hasFlags ? View.VISIBLE : View.GONE);
        binding.flagScroll.setVisibility(hasFlags ? View.VISIBLE : View.GONE);
    }

    private void bindFlags() {
        binding.flagContainer.removeAllViews();
        int routeGeneration = ++seasonSourceRouteGeneration;
        List<Flag> flags = vod.getFlags();
        boolean hasFlags = flags != null && !flags.isEmpty();
        if (!hasFlags) {
            binding.episodeTitle.setVisibility(View.GONE);
            binding.episodeContainer.setVisibility(View.GONE);
            binding.seasonScroll.setVisibility(View.GONE);
            clearEpisodeRanges();
            binding.episodeEmpty.setVisibility(View.VISIBLE);
            updatePlayLabel();
            return;
        }
        Flag currentFlag = findInitialFlag(flags);
        selectedFlag = currentFlag;
        loadTmdbSeasonBinding();
        selectedEpisode = null;
        selectedSeasonNumber = getIntentPlaybackSeasonNumber();
        resetEpisodeRange();
        for (Flag flag : flags) {
            MaterialButton button = createChipButton(flag.getShow());
            setChipState(button, flag == currentFlag);
            button.setNextFocusDownId(R.id.episodeReverse);
            button.setOnKeyListener((view, keyCode, event) -> onDetailFlagKey(keyCode, event));
            button.setOnClickListener(view -> {
                cancelPendingInlinePlayback();
                playbackSelectionTouched = true;
                selectedFlag = flag;
                savePreferredFlag(flag);
                loadTmdbSeasonBinding();
                selectedEpisode = null;
                selectedSeasonNumber = -1;
                resetEpisodeRange();
                renderFlagSelection();
                renderEpisodes();
                refreshSeasonSourceRoutes();
                if (isFusionMode()) onPlay();
            });
            binding.flagContainer.addView(button);
        }
        bindSeasonSourceRoutes(routeGeneration);
        renderFlagSelection();
        renderEpisodes();
    }

    private void bindSeasonSourceRoutes(int routeGeneration) {
        TmdbItem item = matchedTmdbItem != null ? matchedTmdbItem : initialTmdbItem;
        int seasonNumber = currentSeasonSourceScope();
        if (item == null || !item.isTv() || item.getTmdbId() <= 0 || seasonNumber < 0) return;
        int generation = loadGeneration;
        // 把当前站源和这次提交绑定，避免和后续换源写入的 intent 混用。
        String currentKey = getKeyText();
        detailTasks.submit(() -> {
            List<SourceMatch> sources = findSeasonHistorySources(item, seasonNumber);
            runOnAliveUi(() -> {
                if (generation != loadGeneration || routeGeneration != seasonSourceRouteGeneration || sources.isEmpty()) return;
                Set<String> usedLabels = new HashSet<>();
                for (SourceMatch source : sources) {
                    MaterialButton button = createChipButton(distinctChipLabel(seasonSourceRouteLabel(source, currentKey), usedLabels));
                    setChipState(button, false);
                    button.setNextFocusDownId(R.id.episodeReverse);
                    button.setOnKeyListener((view, keyCode, event) -> onDetailFlagKey(keyCode, event));
                    button.setOnClickListener(view -> switchSourceDetail(source, item));
                    binding.flagContainer.addView(button);
                }
            });
        });
    }

    /**
     * 这些 chip 混在线路行里，但点下去是换源而不是切线路，所以文字必须自带来源语义。
     * 同一站源的另一个入口（常见于按季拆条目）用条目名区分，避免和右侧“站源：X”重名。
     */
    private String seasonSourceRouteLabel(SourceMatch source, String currentKey) {
        String siteName = source.site().getDisplayName();
        if (!TextUtils.equals(source.site().getKey(), currentKey)) {
            return getString(R.string.detail_source_route_chip, siteName);
        }
        String entryName = source.vod() == null ? "" : source.vod().getName();
        if (TextUtils.isEmpty(entryName)) entryName = source.vod() == null ? "" : source.vod().getId();
        return getString(R.string.detail_source_route_chip_entry, TextUtils.isEmpty(entryName) ? siteName : entryName);
    }

    /**
     * 同站可能存在同名条目，重名的 chip 用户无法区分，只能盲点，所以追加序号。
     */
    private String distinctChipLabel(String label, Set<String> used) {
        if (used.add(label)) return label;
        for (int index = 2; ; index++) {
            String candidate = label + " (" + index + ")";
            if (used.add(candidate)) return candidate;
        }
    }

    private void refreshSeasonSourceRoutes() {
        int nativeFlagCount = vod == null || vod.getFlags() == null ? 0 : vod.getFlags().size();
        while (binding.flagContainer.getChildCount() > nativeFlagCount) {
            binding.flagContainer.removeViewAt(binding.flagContainer.getChildCount() - 1);
        }
        bindSeasonSourceRoutes(++seasonSourceRouteGeneration);
    }

    private void renderFlagSelection() {
        List<Flag> flags = vod.getFlags();
        for (int i = 0; i < binding.flagContainer.getChildCount() && i < flags.size(); i++) {
            View child = binding.flagContainer.getChildAt(i);
            if (child instanceof MaterialButton button) {
                setChipState(button, flags.get(i) == selectedFlag);
            }
        }
    }

    private void renderEpisodes() {
        List<Episode> episodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        boolean hasEpisodes = episodes != null && !episodes.isEmpty();
        binding.episodeHeader.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        binding.episodeContainer.setVisibility(hasEpisodes ? View.VISIBLE : View.GONE);
        binding.episodeEmpty.setVisibility(hasEpisodes ? View.GONE : View.VISIBLE);
        if (!hasEpisodes) {
            refreshSeasonContext();
            binding.seasonScroll.setVisibility(View.GONE);
            clearEpisodeRanges();
            renderedEpisodeRangeIndex = -1;
            binding.episodeSkeleton.setVisibility(View.GONE);
            episodeAdapter.setItems(List.of(), Map.of(), null);
            updatePlayLabel();
            loadRelatedVideosForCurrentContext();
            return;
        }
        if (selectedEpisode == null) {
            selectedEpisode = findIntentPlaybackEpisode(selectedFlag);
            String remarks = history != null ? history.getVodRemarks() : "";
            if (selectedEpisode == null) selectedEpisode = findEpisodeByUrl(history == null ? "" : history.getEpisodeUrl(), selectedFlag.getEpisodes());
            if (selectedEpisode == null) selectedEpisode = selectedFlag.find(remarks, getMarkText().isEmpty());
            if (selectedEpisode == null) selectedEpisode = episodes.get(0);
        }
        List<Integer> availableSeasons = availableSeasonNumbers(episodes);
        if (availableSeasons.isEmpty()) {
            selectedSeasonNumber = -1;
        } else if (selectedSeasonNumber < 0 || !availableSeasons.contains(selectedSeasonNumber)) {
            selectedSeasonNumber = seasonForEpisode(selectedEpisode, episodes);
        }
        refreshSeasonContext();
        List<Episode> visibleEpisodes = visibleEpisodes(episodes);
        if (!visibleEpisodes.isEmpty() && !visibleEpisodes.contains(selectedEpisode)) {
            selectedEpisode = visibleEpisodes.get(0);
        }
        renderSeasonSelection();
        bindSeasonEpisodes(episodes);
        refreshCurrentHistoryEpisodeTitle();
        boolean shouldRefreshTmdbSection = shouldRefreshEpisodeMediaSection(episodes);
        List<Episode> displayEpisodes = new ArrayList<>(visibleEpisodes);
        if (episodeReverse) Collections.reverse(displayEpisodes);
        List<EpisodeRangePolicy.Range> episodeRanges = buildCardEpisodeRanges(displayEpisodes, selectedEpisode);
        episodeRangeIndex = resolveEpisodeRangeIndex(episodeRanges);
        renderEpisodeRanges(episodeRanges);
        List<Episode> pagedDisplayEpisodes = episodeRanges.size() > 1 ? EpisodeRangePolicy.slice(displayEpisodes, episodeRanges.get(episodeRangeIndex)) : displayEpisodes;
        Map<Episode, Integer> episodeNumbers = episodeNumbers(pagedDisplayEpisodes, episodes);
        updateEpisodeToolButtons();
        applyEpisodeViewport(pagedDisplayEpisodes, episodeNumbers, true);
        renderedEpisodeRangeIndex = episodeRanges.size() > 1 ? episodeRangeIndex : -1;
        if (shouldRefreshTmdbSection) bindTmdbSection();
        loadRelatedVideosForCurrentContext();
    }

    private List<EpisodeRangePolicy.Range> buildCardEpisodeRanges(List<Episode> episodes, Episode selected) {
        List<EpisodeRangePolicy.Range> ranges = EpisodeRangePolicy.build(episodes.size(), episodes.indexOf(selected), episodeReverse, episodeCardPageMaxSize());
        // 修正分组范围标签：用实际集号而非列表索引
        List<Episode> allEpisodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        if (allEpisodes != null && !allEpisodes.isEmpty()) {
            Map<Episode, Integer> numbers = episodeNumbers(episodes, allEpisodes);
            List<EpisodeRangePolicy.Range> correctedRanges = new ArrayList<>();
            for (EpisodeRangePolicy.Range range : ranges) {
                List<Episode> rangeEpisodes = EpisodeRangePolicy.slice(episodes, range);
                if (rangeEpisodes.isEmpty()) {
                    correctedRanges.add(range);
                    continue;
                }
                Episode first = rangeEpisodes.get(0);
                Episode last = rangeEpisodes.get(rangeEpisodes.size() - 1);
                int firstNumber = numbers.getOrDefault(first, range.start() + 1);
                int lastNumber = numbers.getOrDefault(last, range.end());
                String correctedLabel = firstNumber == lastNumber ? String.valueOf(firstNumber) : firstNumber + "-" + lastNumber;
                correctedRanges.add(new EpisodeRangePolicy.Range(correctedLabel, range.start(), range.end(), range.selected()));
            }
            return correctedRanges;
        }
        return ranges;
    }

    private int episodeCardPageMaxSize() {
        return Util.isMobile() && Setting.isStandaloneTmdbDetailMode(getDetailMode()) ? STANDALONE_MOBILE_EPISODE_CARD_PAGE_MAX_SIZE : EpisodeRangePolicy.CARD_PAGE_MAX_SIZE;
    }

    private boolean shouldRefreshEpisodeMediaSection(List<Episode> episodes) {
        int currentSeason = tmdbEpisodeDataSeason(episodes);
        if (currentSeason != lastEpisodeMediaSeason) {
            lastEpisodeMediaSeason = currentSeason;
            return true;
        }
        return false;
    }

    private void applyEpisodeViewport(List<Episode> items, Map<Episode, Integer> episodeNumbers, boolean scrollToSelection) {
        applyEpisodeViewport(items, episodeNumbers, scrollToSelection, false);
    }

    private void applyEpisodeViewport(List<Episode> items, Map<Episode, Integer> episodeNumbers, boolean scrollToSelection, boolean forceRefresh) {
        updateEpisodeViewModeButton();
        updateEpisodeFileNameButton();
        int spanCount = episodeSpanCount();
        episodeAdapter.setDisplayMode(episodeGridMode ? TmdbEpisodeAdapter.Mode.GRID : TmdbEpisodeAdapter.Mode.LIST, spanCount);
        updateEpisodeLayoutManager(spanCount);
        updateEpisodeViewport(items.size(), spanCount);
        lastDetailEpisodeFocusRowStart = RecyclerView.NO_POSITION;
        episodeAdapter.setFallbackStillUrl(episodeFallbackStillUrl());
        boolean changed = episodeAdapter.setItems(items, tmdbEpisodes, episodeNumbers, selectedEpisode, forceRefresh);

        // 根因（日志实测锁定）：TMDB 多阶段异步 apply 会在匹配过程中先把适配器清空、再填入数据。
        // 若某帧布局遍历正好定格在“适配器为空”的瞬间，RecyclerView 会以 childCount=0 完成布局，
        // 顶层视图（DecorView 等）清除 FORCE_LAYOUT 认为布局已结束，但 RecyclerView 子树残留了
        // 未被消费的 FORCE_LAYOUT 脏标志。此后新数据到达时 requestLayout 沿父链上传，会因“父节点
        // 已是 layoutRequested”而在中途短路、到不了 ViewRootImpl，布局遍历再不被调度；同时相同数据
        // 被 setItems 去重（changed=false），恢复逻辑失效——形成“适配器有数据、屏上 0 卡片”的死锁。
        // 修复：
        // 1) changed 时对已附着的可见 ViewHolder 直接重绑，覆盖“卡片还在、仅数据变化”；
        // 2) 不论 changed，下一帧检测错位态（有数据却 0 子 View），命中则给整条祖先链逐级补上
        //    FORCE_LAYOUT 脏标志、再从顶层发起 requestLayout，绕过短路强制一次自顶向下的重新布局。
        if (changed) {
            episodeAdapter.rebindAttached(binding.episodeContainer);
            binding.episodeContainer.requestLayout();
        }
        binding.episodeContainer.post(this::recoverEpisodeViewportIfDetached);
        updateEpisodeSkeleton();
        if (scrollToSelection) scrollEpisodeToSelected();
        updatePlayLabel();
    }

    private void recoverEpisodeViewportIfDetached() {
        recoverRecyclerViewIfDetached(binding == null ? null : binding.episodeContainer);
    }

    // 恢复错位态：适配器有数据、但 RecyclerView 屏上 0 个子 View（childCount=0）。
    // 成因见 applyEpisodeViewport 注释——某帧布局定格在“空适配器”瞬间后，RecyclerView 子树残留
    // 未消费的 FORCE_LAYOUT 脏标志，导致后续 requestLayout 沿父链上传时因“父已 layoutRequested”
    // 而中途短路、到不了 ViewRootImpl，布局遍历再不被调度。
    // 破法：给整条祖先链逐级 forceLayout() 补上脏标志，再从最顶层 View 发起 requestLayout()——
    // 顶层的父是 ViewRootImpl，必定触发 scheduleTraversals，绕过中途短路强制一次自顶向下重新布局。
    private void recoverRecyclerViewIfDetached(RecyclerView rv) {
        if (rv == null) return;
        if (rv.getVisibility() != View.VISIBLE) return;
        RecyclerView.Adapter<?> adapter = rv.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) return;
        if (rv.getChildCount() > 0) return; // 已有子 View，未处于错位态
        if (rv.isComputingLayout()) {
            rv.post(() -> recoverRecyclerViewIfDetached(rv));
            return;
        }
        android.view.View root = rv;
        rv.forceLayout();
        android.view.ViewParent p = rv.getParent();
        while (p instanceof android.view.View v) {
            v.forceLayout();
            root = v;
            p = v.getParent();
        }
        root.requestLayout();
    }

    // 防御性加固：TMDB 额外信息的 7 个固定高度列表也可能在慢查询下碰到同一 requestLayout 短路，
    // 逐一检测并恢复错位态（有数据却 0 子 View）。
    private void recoverTmdbSectionListsIfDetached() {
        if (binding == null) return;
        recoverRecyclerViewIfDetached(binding.episodePhotoList);
        recoverRecyclerViewIfDetached(binding.posterList);
        recoverRecyclerViewIfDetached(binding.castList);
        recoverRecyclerViewIfDetached(binding.creatorList);
        recoverRecyclerViewIfDetached(binding.relatedList);
        recoverRecyclerViewIfDetached(binding.relatedVideoList);
        recoverRecyclerViewIfDetached(binding.personalTmdbList);
        recoverRecyclerViewIfDetached(binding.personalDoubanList);
        recoverRecyclerViewIfDetached(binding.personalAiList);
    }

    private void rerenderEpisodeViewportOnly(boolean scrollToSelection) {
        rerenderEpisodeViewportOnly(scrollToSelection, false, false);
    }

    private void rerenderEpisodeViewportOnly(boolean scrollToSelection, boolean rebuildRanges) {
        rerenderEpisodeViewportOnly(scrollToSelection, rebuildRanges, false);
    }

    private void rerenderEpisodeViewportOnly(boolean scrollToSelection, boolean rebuildRanges, boolean forceRefresh) {
        List<Episode> episodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        if (episodes == null || episodes.isEmpty()) return;
        List<Episode> visibleEpisodes = visibleEpisodes(episodes);
        List<Episode> displayEpisodes = new ArrayList<>(visibleEpisodes);
        if (episodeReverse) Collections.reverse(displayEpisodes);
        List<EpisodeRangePolicy.Range> ranges = buildCardEpisodeRanges(displayEpisodes, selectedEpisode);
        if (ranges.isEmpty()) {
            renderedEpisodeRangeIndex = -1;
            if (rebuildRanges) clearEpisodeRanges();
            applyEpisodeViewport(List.of(), Map.of(), false, forceRefresh);
            return;
        }
        episodeRangeIndex = resolveEpisodeRangeIndex(ranges);
        if (rebuildRanges) renderEpisodeRanges(ranges);
        else updateEpisodeRangeButtonStates();
        List<Episode> pageItems = ranges.size() > 1 ? EpisodeRangePolicy.slice(displayEpisodes, ranges.get(episodeRangeIndex)) : displayEpisodes;
        Map<Episode, Integer> numbers = episodeNumbers(pageItems, episodes);
        updateEpisodeReverseButton();
        applyEpisodeViewport(pageItems, numbers, scrollToSelection, forceRefresh);
        renderedEpisodeRangeIndex = ranges.size() > 1 ? episodeRangeIndex : -1;
    }

    private void updateEpisodeRangeButtonStates() {
        if (binding == null) return;
        for (int i = 0; i < binding.episodeRangeContainer.getChildCount(); i++) {
            View child = binding.episodeRangeContainer.getChildAt(i);
            if (child instanceof MaterialButton button) {
                setChipState(button, i == episodeRangeIndex);
                setEpisodeRangeFocusChange(button, i);
            }
        }
    }

    private void selectEpisodeRange(int index, boolean scrollToSelection) {
        if (binding != null && binding.episodeContainer.isComputingLayout()) {
            binding.episodeContainer.post(() -> selectEpisodeRange(index, scrollToSelection));
            return;
        }
        cancelPendingInlinePlayback();
        manualEpisodeRange = true;
        episodeRangeIndex = index;
        rerenderEpisodeViewportOnly(scrollToSelection);
    }

    private int resolveEpisodeRangeIndex(List<EpisodeRangePolicy.Range> ranges) {
        if (ranges == null || ranges.isEmpty()) {
            episodeRangeIndex = 0;
            manualEpisodeRange = false;
            return episodeRangeIndex;
        }
        if (manualEpisodeRange) episodeRangeIndex = Math.max(0, Math.min(episodeRangeIndex, ranges.size() - 1));
        else episodeRangeIndex = EpisodeRangePolicy.selectedPosition(ranges);
        return episodeRangeIndex;
    }

    private void renderEpisodeRanges(List<EpisodeRangePolicy.Range> ranges) {
        binding.episodeRangeContainer.removeAllViews();
        boolean hasRanges = ranges != null && ranges.size() > 1;
        binding.episodeRangeScroll.setVisibility(hasRanges ? View.VISIBLE : View.GONE);
        if (!hasRanges) return;
        for (int i = 0; i < ranges.size(); i++) {
            EpisodeRangePolicy.Range range = ranges.get(i);
            MaterialButton button = createChipButton(range.label());
            setChipState(button, i == episodeRangeIndex);
            button.setNextFocusUpId(R.id.episodeReverse);
            int index = i;
            button.setOnClickListener(view -> selectEpisodeRange(index, false));
            setEpisodeRangeFocusChange(button, index);
            button.setOnKeyListener((view, keyCode, event) -> onDetailEpisodeRangeKey(view, keyCode, event));
            binding.episodeRangeContainer.addView(button);
        }
        View selected = binding.episodeRangeContainer.getChildAt(episodeRangeIndex);
        if (selected != null) binding.episodeRangeScroll.post(() -> binding.episodeRangeScroll.smoothScrollTo(Math.max(0, selected.getLeft() - ResUtil.dp2px(12)), 0));
        restoreEpisodeRangeFocus();
    }

    private void setEpisodeRangeFocusChange(MaterialButton button, int index) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        button.setOnFocusChangeListener((view, focused) -> {
            applyChipFocus(button, index == episodeRangeIndex, focused, colors);
            if (!focused) return;
            activateFocusedEpisodeRange(index);
        });
    }

    private void activateFocusedEpisodeRange(int index) {
        if (index == episodeRangeIndex && index == renderedEpisodeRangeIndex) return;
        pendingEpisodeRangeFocus = index;
        binding.episodeRangeContainer.post(() -> {
            if (binding == null || pendingEpisodeRangeFocus != index) return;
            selectEpisodeRange(index, false);
        });
    }

    private void restoreEpisodeRangeFocus() {
        if (pendingEpisodeRangeFocus < 0) return;
        int target = pendingEpisodeRangeFocus;
        pendingEpisodeRangeFocus = -1;
        binding.episodeRangeContainer.post(() -> {
            if (binding == null || target >= binding.episodeRangeContainer.getChildCount()) return;
            View child = binding.episodeRangeContainer.getChildAt(target);
            if (child != null) child.requestFocus();
        });
    }

    private void clearEpisodeRanges() {
        binding.episodeRangeScroll.setVisibility(View.GONE);
        binding.episodeRangeContainer.removeAllViews();
        renderedEpisodeRangeIndex = -1;
        pendingEpisodeRangeFocus = -1;
    }

    private void resetEpisodeRange() {
        episodeRangeIndex = 0;
        renderedEpisodeRangeIndex = -1;
        manualEpisodeRange = false;
        pendingEpisodeRangeFocus = -1;
    }

    private void scrollEpisodeToSelected() {
        if (selectedEpisode == null || episodeAdapter == null) return;
        binding.episodeContainer.post(() -> {
            if (scrollEpisodeStartOnce) {
                scrollEpisodeStartOnce = false;
                scrollEpisodeToPosition(0, ResUtil.dp2px(8));
                return;
            }
            if (selectedEpisode == null) return;
            int position = episodeAdapter.getPosition(selectedEpisode);
            if (position < 0) {
                scrollEpisodeToPosition(0, ResUtil.dp2px(8));
                return;
            }
            scrollEpisodeToPosition(position, ResUtil.dp2px(12));
        });
    }

    private void scrollEpisodeToPosition(int position, int offset) {
        RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager gridLayoutManager) {
            gridLayoutManager.scrollToPositionWithOffset(position, offset);
        } else if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
            linearLayoutManager.scrollToPositionWithOffset(position, offset);
        } else {
            binding.episodeContainer.scrollToPosition(position);
        }
    }

    private void onDetailEpisodeFocusChange(View view, boolean focused) {
        if (binding == null) return;
        if (!focused) {
            clearDetailEpisodeFocusRowIfNeeded(view);
            return;
        }
        if (!episodeGridMode) return;
        int position = binding.episodeContainer.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return;
        int rowStart = detailEpisodeRowStart(position);
        boolean sameFocusedRow = rowStart == lastDetailEpisodeFocusRowStart;
        lastDetailEpisodeFocusRowStart = rowStart;
        if (binding.episodeContainer.isNestedScrollingEnabled()) return;
        if (sameFocusedRow) return;
        binding.scroll.post(() -> alignDetailEpisodeFocusedRow(view, position));
    }

    private void clearDetailEpisodeFocusRowIfNeeded(View view) {
        view.post(() -> {
            if (binding == null) return;
            View focus = getCurrentFocus();
            if (focus == null || !isFocusInside(focus, binding.episodeContainer)) lastDetailEpisodeFocusRowStart = RecyclerView.NO_POSITION;
        });
    }

    private boolean onDetailFlagKey(int keyCode, KeyEvent event) {
        View focus = getCurrentFocus();
        if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return onDetailHorizontalButtonGroupKey(binding.flagContainer, binding.flagScroll, focus, event);
        if (!KeyUtil.isDownKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (focusDetailEpisodeToolButton(View.FOCUS_DOWN)) return true;
        if (focusDetailEpisodeRangeButton()) return true;
        return focusDetailEpisode();
    }

    private boolean onDetailEpisodeRangeKey(View view, int keyCode, KeyEvent event) {
        if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return onDetailHorizontalButtonGroupKey(binding.episodeRangeContainer, binding.episodeRangeScroll, view, event);
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (KeyUtil.isUpKey(event)) return focusDetailSeasonButton() || focusDetailEpisodeToolButton(View.FOCUS_UP) || focusDetailFlagButton();
        return focusDetailEpisodeBelow(view);
    }

    private boolean onDetailEpisodeToolKey(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event) && !KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return onDetailHorizontalButtonGroupKey(binding.episodeHeader, null, view, event);
        if (KeyUtil.isUpKey(event)) return focusDetailFlagButton();
        return focusDetailSeasonButton() || focusDetailEpisodeRangeButton() || focusDetailEpisode();
    }

    private boolean onDetailEpisodeKey(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event) && !KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        int position = binding.episodeContainer.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) return true;
        return moveDetailEpisodeFocus(position, event);
    }

    private boolean moveDetailEpisodeFocus(int position, KeyEvent event) {
        if (!episodeGridMode) return moveDetailEpisodeListFocus(position, event);
        int span = detailEpisodeSpanCount();
        if (KeyUtil.isLeftKey(event)) {
            if (position % span == 0) return true;
            return focusDetailEpisode(position - 1);
        }
        if (KeyUtil.isRightKey(event)) {
            if (episodeAdapter == null || position >= episodeAdapter.getItemCount() - 1 || position % span == span - 1) return true;
            return focusDetailEpisode(position + 1);
        }
        boolean down = KeyUtil.isDownKey(event);
        int target = TmdbEpisodeGridPolicy.verticalFocusTarget(position, span, episodeAdapter.getItemCount(), down);
        if (target == TmdbEpisodeGridPolicy.NO_FOCUS_TARGET) {
            if (!down) {
                if (focusDetailEpisodeRangeButton()) return true;
                if (focusDetailEpisodeToolButton(View.FOCUS_UP)) return true;
                return focusDetailFlagButton();
            }
            return focusFirstVisibleTmdbRow();
        }
        return false;
    }

    private boolean moveDetailEpisodeListFocus(int position, KeyEvent event) {
        if (KeyUtil.isLeftKey(event)) {
            if (position <= 0) return true;
            return focusDetailEpisode(position - 1);
        }
        if (KeyUtil.isRightKey(event)) {
            if (episodeAdapter == null || position >= episodeAdapter.getItemCount() - 1) return true;
            return focusDetailEpisode(position + 1);
        }
        if (KeyUtil.isUpKey(event)) {
            if (focusDetailEpisodeRangeButton()) return true;
            if (focusDetailEpisodeToolButton(View.FOCUS_UP)) return true;
            return focusDetailFlagButton();
        }
        return focusFirstVisibleTmdbRow();
    }

    private boolean focusLastVisibleTmdbRow() {
        return focusTmdbRecycler(binding.personalAiList)
                || focusTmdbRecycler(binding.personalDoubanList)
                || focusTmdbRecycler(binding.personalTmdbList)
                || focusTmdbRecycler(binding.relatedList)
                || focusTmdbRecycler(binding.creatorList)
                || focusTmdbRecycler(binding.castList)
                || focusTmdbRecycler(binding.episodePhotoList);
    }

    private boolean focusFirstVisibleTmdbRow() {
        return focusTmdbRecycler(binding.episodePhotoList)
                || focusTmdbRecycler(binding.castList)
                || focusTmdbRecycler(binding.creatorList)
                || focusTmdbRecycler(binding.relatedList)
                || focusTmdbRecycler(binding.personalTmdbList)
                || focusTmdbRecycler(binding.personalDoubanList)
                || focusTmdbRecycler(binding.personalAiList);
    }

    private boolean focusTmdbRecycler(RecyclerView recycler) {
        if (binding == null || recycler == null || recycler.getVisibility() != View.VISIBLE) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) return false;
        binding.scroll.post(() -> {
            recycler.stopScroll();
            scrollDetailChildIntoViewNow(recycler, 12);
            recycler.scrollToPosition(0);
            RecyclerView.ViewHolder visibleHolder = recycler.findViewHolderForAdapterPosition(0);
            if (visibleHolder != null) {
                visibleHolder.itemView.requestFocus();
                scrollDetailChildIntoViewNow(recycler, 12);
                return;
            }
            recycler.post(() -> {
                RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(0);
                if (holder != null) holder.itemView.requestFocus();
                else recycler.requestFocus();
                scrollDetailChildIntoViewNow(recycler, 12);
            });
        });
        return true;
    }

    private void scrollDetailChildIntoViewNow(View child, int topPaddingDp) {
        if (binding == null || child == null) return;
        Rect rect = new Rect();
        child.getDrawingRect(rect);
        binding.scroll.offsetDescendantRectToMyCoords(child, rect);
        binding.scroll.scrollTo(0, Math.max(0, rect.top - ResUtil.dp2px(topPaddingDp)));
    }

    private boolean focusDetailEpisodeToolButton(int direction) {
        if (binding == null || binding.episodeHeader.getVisibility() != View.VISIBLE) return false;
        return focusDetailButton(binding.episodeTitle, direction)
                || focusDetailButton(binding.episodeReverse, direction)
                || focusDetailButton(binding.episodeFileName, direction)
                || focusDetailButton(binding.episodeViewMode, direction);
    }

    private boolean focusDetailButton(View button, int direction) {
        if (binding == null || button == null || button.getVisibility() != View.VISIBLE || !button.isShown() || !button.isEnabled() || !button.isFocusable()) return false;
        View previousFocus = getCurrentFocus();
        scrollDetailChildIntoViewNow(button, 12);
        button.requestFocus(direction);
        binding.scroll.requestChildFocus(button, button);
        button.postDelayed(() -> retryDetailButtonFocus(button, previousFocus), 120);
        button.postDelayed(() -> retryDetailButtonFocus(button, previousFocus), 240);
        return true;
    }

    private void retryDetailButtonFocus(View button, View previousFocus) {
        if (binding == null || isEpisodeToolFocusedOtherThan(button)) return;
        View focus = getCurrentFocus();
        if (focus == button) return;
        if (focus != null && previousFocus != null && focus != previousFocus) return;
        scrollDetailChildIntoViewNow(button, 12);
        button.requestFocus();
    }

    private boolean isEpisodeToolFocusedOtherThan(View button) {
        View focus = getCurrentFocus();
        return focus != null && focus != button && isEpisodeToolButton(focus);
    }

    private boolean isEpisodeToolButton(View view) {
        return binding != null && (view == binding.episodeTitle || view == binding.episodeReverse || view == binding.episodeFileName || view == binding.episodeViewMode);
    }

    private boolean focusDetailSeasonButton() {
        if (binding == null || binding.seasonScroll.getVisibility() != View.VISIBLE || binding.seasonContainer.getChildCount() == 0) return false;
        List<Integer> availableSeasons = availableSeasonNumbers(selectedFlag == null ? null : selectedFlag.getEpisodes());
        int target = Math.max(0, availableSeasons.indexOf(selectedSeasonNumber));
        target = Math.min(target, binding.seasonContainer.getChildCount() - 1);
        View child = binding.seasonContainer.getChildAt(target);
        if (child == null) return false;
        child.post(() -> {
            scrollDetailChildIntoViewNow(child, 12);
            child.requestFocus();
        });
        return true;
    }

    private boolean onDetailSeasonKey(View focus, KeyEvent event) {
        if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return onDetailHorizontalButtonGroupKey(binding.seasonContainer, null, focus, event);
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (KeyUtil.isUpKey(event)) return focusDetailSeasonSibling(focus, true) || focusDetailEpisodeToolButton(View.FOCUS_UP) || focusDetailFlagButton();
        return focusDetailSeasonSibling(focus, false) || focusDetailEpisodeRangeButton() || focusDetailEpisode();
    }

    private boolean focusDetailSeasonSibling(View focus, boolean up) {
        if (binding == null || focus == null) return false;
        int direction = up ? View.FOCUS_UP : View.FOCUS_DOWN;
        View target = FocusFinder.getInstance().findNextFocus(binding.seasonContainer, focus, direction);
        if (target == null) return false;
        scrollDetailChildIntoViewNow(target, 12);
        return target.requestFocus(direction);
    }
    private boolean focusDetailFlagButton() {
        if (binding == null || binding.flagContainer.getChildCount() == 0) return false;
        int target = 0;
        List<Flag> flags = vod == null ? List.of() : vod.getFlags();
        for (int i = 0; i < flags.size() && i < binding.flagContainer.getChildCount(); i++) {
            if (flags.get(i) == selectedFlag) {
                target = i;
                break;
            }
        }
        View child = binding.flagContainer.getChildAt(target);
        if (child == null) return false;
        binding.flagScroll.post(() -> {
            child.requestFocus();
            binding.flagScroll.smoothScrollTo(Math.max(0, child.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusDetailEpisodeRangeButton() {
        if (binding == null || binding.episodeRangeScroll.getVisibility() != View.VISIBLE || binding.episodeRangeContainer.getChildCount() == 0) return false;
        int target = Math.max(0, Math.min(episodeRangeIndex, binding.episodeRangeContainer.getChildCount() - 1));
        View child = binding.episodeRangeContainer.getChildAt(target);
        if (child == null) return false;
        binding.episodeRangeScroll.post(() -> {
            child.requestFocus();
            binding.episodeRangeScroll.smoothScrollTo(Math.max(0, child.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusDetailEpisodeBelow(View source) {
        if (episodeAdapter == null || episodeAdapter.getItemCount() == 0) return false;
        int target = nearestVisibleDetailEpisodePositionBelow(source);
        if (target == RecyclerView.NO_POSITION) target = firstVisibleDetailEpisodePosition();
        if (target == RecyclerView.NO_POSITION) target = 0;
        return focusDetailEpisode(target);
    }

    private int nearestVisibleDetailEpisodePositionBelow(View source) {
        if (binding == null || source == null) return RecyclerView.NO_POSITION;
        Rect sourceRect = new Rect();
        source.getDrawingRect(sourceRect);
        binding.scroll.offsetDescendantRectToMyCoords(source, sourceRect);
        int bestPosition = RecyclerView.NO_POSITION;
        int bestDy = Integer.MAX_VALUE;
        int bestDx = Integer.MAX_VALUE;
        for (int i = 0; i < binding.episodeContainer.getChildCount(); i++) {
            View child = binding.episodeContainer.getChildAt(i);
            int position = binding.episodeContainer.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) continue;
            Rect rect = new Rect();
            child.getDrawingRect(rect);
            binding.scroll.offsetDescendantRectToMyCoords(child, rect);
            if (rect.centerY() < sourceRect.centerY()) continue;
            int dy = Math.max(0, rect.top - sourceRect.bottom);
            int dx = Math.abs(rect.centerX() - sourceRect.centerX());
            if (dy > bestDy || dy == bestDy && dx >= bestDx) continue;
            bestPosition = position;
            bestDy = dy;
            bestDx = dx;
        }
        return bestPosition;
    }

    private int firstVisibleDetailEpisodePosition() {
        if (binding == null) return RecyclerView.NO_POSITION;
        RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
        if (layoutManager instanceof LinearLayoutManager linearLayoutManager) {
            int position = linearLayoutManager.findFirstVisibleItemPosition();
            if (position != RecyclerView.NO_POSITION) return position;
        }
        int first = Integer.MAX_VALUE;
        for (int i = 0; i < binding.episodeContainer.getChildCount(); i++) {
            int position = binding.episodeContainer.getChildAdapterPosition(binding.episodeContainer.getChildAt(i));
            if (position != RecyclerView.NO_POSITION) first = Math.min(first, position);
        }
        return first == Integer.MAX_VALUE ? RecyclerView.NO_POSITION : first;
    }

    private boolean focusDetailEpisode() {
        if (episodeAdapter == null || episodeAdapter.getItemCount() == 0) return false;
        int position = Math.max(0, episodeAdapter.getPosition(selectedEpisode));
        return focusDetailEpisode(position);
    }

    private boolean focusDetailEpisode(int position) {
        if (binding == null || episodeAdapter == null || episodeAdapter.getItemCount() == 0) return false;
        int target = Math.max(0, Math.min(position, episodeAdapter.getItemCount() - 1));
        int span = detailEpisodeSpanCount();
        int rowStart = Math.max(0, target - target % span);
        RecyclerView.ViewHolder visibleHolder = binding.episodeContainer.findViewHolderForAdapterPosition(target);
        if (visibleHolder != null) {
            binding.episodeContainer.stopScroll();
            visibleHolder.itemView.requestFocus();
            alignDetailEpisodeFocusedRow(visibleHolder.itemView, target);
            return true;
        }
        binding.episodeContainer.post(() -> {
            binding.episodeContainer.stopScroll();
            scrollEpisodeToPosition(rowStart, ResUtil.dp2px(8));
            binding.episodeContainer.postDelayed(() -> {
                RecyclerView.ViewHolder holder = binding.episodeContainer.findViewHolderForAdapterPosition(target);
                if (holder == null) {
                    binding.episodeContainer.requestFocus();
                    return;
                }
                holder.itemView.requestFocus();
                alignDetailEpisodeFocusedRow(holder.itemView, target);
            }, 80);
        });
        return true;
    }

    private int detailEpisodeSpanCount() {
        if (binding == null) return Math.max(1, episodeSpanCount());
        RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager gridLayoutManager) return Math.max(1, gridLayoutManager.getSpanCount());
        return Math.max(1, episodeSpanCount());
    }

    private int detailEpisodeRowStart(int position) {
        int span = detailEpisodeSpanCount();
        return Math.max(0, position - position % span);
    }

    private void alignDetailEpisodeFocusedRow(View focusedView, int position) {
        if (binding == null || focusedView == null) return;
        RecyclerView.LayoutManager layoutManager = binding.episodeContainer.getLayoutManager();
        if (!(layoutManager instanceof GridLayoutManager)) return;
        focusedView.post(() -> {
            if (binding == null || getCurrentFocus() != focusedView) return;
            if (binding.episodeContainer.getChildAdapterPosition(focusedView) != position) return;
            alignDetailEpisodeFocusedCardNow(focusedView);
        });
    }

    private void alignDetailEpisodeFocusedCardNow(View focusedView) {
        if (binding == null || binding.scroll.getHeight() <= 0) return;
        Rect rect = new Rect();
        focusedView.getDrawingRect(rect);
        binding.scroll.offsetDescendantRectToMyCoords(focusedView, rect);
        int padding = ResUtil.dp2px(8);
        int currentY = binding.scroll.getScrollY();
        int top = currentY + padding;
        int bottom = currentY + binding.scroll.getHeight() - padding;
        int targetY = currentY;
        if (rect.bottom > bottom) targetY += rect.bottom - bottom;
        else if (rect.top < top) targetY += rect.top - top;
        targetY = Math.max(0, targetY);
        if (Math.abs(currentY - targetY) <= ResUtil.dp2px(2)) return;
        binding.scroll.scrollTo(0, targetY);
    }

    private void toggleEpisodeReverse() {
        episodeReverse = !episodeReverse;
        resetEpisodeRange();
        scrollEpisodeStartOnce = episodeReverse;
        rerenderEpisodeViewportOnly(true, true);
    }

    private void toggleEpisodeViewMode() {
        episodeGridMode = !episodeGridMode;
        Setting.putTmdbEpisodeGridMode(episodeGridMode);
        rerenderEpisodeViewportOnly(false);
    }

    private void toggleEpisodeFileName() {
        boolean showScraped = !Setting.getTmdbEpisodeShowScrapedName();
        Setting.putTmdbEpisodeShowScrapedName(showScraped);
        updateEpisodeFileNameButton();
        updatePlayLabel();
        // AlertDialog 返回时 RecyclerView 可能仍在恢复布局，下一帧再补刷可见卡片。
        binding.episodeContainer.post(() -> episodeAdapter.refreshDisplaySettings(binding.episodeContainer));
    }

    private void updateEpisodeToolButtons() {
        binding.episodeReverse.setVisibility(View.VISIBLE);
        binding.episodeFileName.setVisibility(View.VISIBLE);
        binding.episodeViewMode.setVisibility(View.VISIBLE);
        updateEpisodeReverseButton();
        updateEpisodeFileNameButton();
        updateEpisodeViewModeButton();
    }

    private void updateEpisodeReverseButton() {
        // 图标表达当前排序状态，描述表达点击后的动作。
        binding.episodeReverse.setIconResource(episodeReverse ? R.drawable.ic_action_sort_desc : R.drawable.ic_action_sort_asc);
        binding.episodeReverse.setContentDescription(getString(episodeReverse ? R.string.detail_episode_forward : R.string.detail_episode_reverse));
    }

    private void updateEpisodeViewModeButton() {
        // 图标表达当前视图状态，描述表达点击后的动作。
        binding.episodeViewMode.setIconResource(episodeGridMode ? R.drawable.ic_site_grid : R.drawable.ic_site_list);
        binding.episodeViewMode.setContentDescription(getString(episodeGridMode ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
    }

    private void updateEpisodeFileNameButton() {
        // 图标表达当前标题状态，描述表达点击后的动作。
        boolean showScraped = Setting.getTmdbEpisodeShowScrapedName();
        binding.episodeFileName.setIconResource(showScraped ? R.drawable.ic_action_name_short : R.drawable.ic_action_name_full);
        binding.episodeFileName.setContentDescription(getString(showScraped ? R.string.detail_episode_file_name_original_action : R.string.detail_episode_file_name_scraped_action));
    }

    private void updateEpisodeLayoutManager() {
        updateEpisodeLayoutManager(episodeSpanCount());
    }

    private void updateEpisodeLayoutManager(int spanCount) {
        RecyclerView.LayoutManager current = binding.episodeContainer.getLayoutManager();
        if (episodeGridMode) {
            if (current instanceof GridLayoutManager grid && grid.getSpanCount() == spanCount) return;
            binding.episodeContainer.setPadding(0, 0, 0, 0);
            binding.episodeContainer.setLayoutManager(new GridLayoutManager(this, spanCount));
        } else {
            if (current instanceof LinearLayoutManager linear && linear.getOrientation() == LinearLayoutManager.HORIZONTAL) return;
            binding.episodeContainer.setPadding(0, 0, ResUtil.dp2px(8), 0);
            binding.episodeContainer.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        }
    }

    private void updateEpisodeViewport(int itemCount, int spanCount) {
        ViewGroup.LayoutParams params = binding.episodeContainer.getLayoutParams();
        if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            binding.episodeContainer.setLayoutParams(params);
        }
        binding.episodeContainer.setNestedScrollingEnabled(false);
    }

    private void updateEpisodeLayoutForCurrentItems() {
        if (episodeAdapter == null || binding == null) return;
        int spanCount = episodeSpanCount();
        episodeAdapter.setGridSpanCount(spanCount);
        updateEpisodeLayoutManager(spanCount);
        updateEpisodeViewport(episodeAdapter.getItemCount(), spanCount);
    }

    private int episodeSpanCount() {
        return nativeEnhancedInlineEpisodeSpanCount();
    }

    private Map<Episode, Integer> episodeNumbers(List<Episode> visibleEpisodes, List<Episode> allEpisodes) {
        Map<Episode, Integer> numbers = new HashMap<>();
        Map<Episode, Integer> indices = episodeIndices(allEpisodes);
        for (int i = 0; i < visibleEpisodes.size(); i++) {
            Episode episode = visibleEpisodes.get(i);
            int index = indices.getOrDefault(episode, -1);
            if (index < 0 && allEpisodes != null) index = allEpisodes.indexOf(episode);
            EpisodePosition position = episodePosition(episode, allEpisodes, index);
            // 修正：fallback 时使用 allEpisodes 的索引而不是 visibleEpisodes 的索引
            int finalNumber = position.number() > 0 ? position.number() : (index >= 0 ? index + 1 : i + 1);
            numbers.put(episode, finalNumber);
        }
        return numbers;
    }

    private Map<Episode, Integer> episodeIndices(List<Episode> episodes) {
        if (episodes == episodeIndexSource) return episodeIndexCache;
        episodeIndexSource = episodes;
        Map<Episode, Integer> indices = new IdentityHashMap<>();
        if (episodes != null) {
            for (int i = 0; i < episodes.size(); i++) indices.put(episodes.get(i), i);
        }
        episodeIndexCache = indices;
        return episodeIndexCache;
    }

    private void clearSourceEpisodeSeasonCache() {
        synchronized (sourceEpisodeSeasonCache) {
            sourceEpisodeSeasonCache.clear();
        }
    }

    private void clearEpisodeRenderCaches() {
        episodeIndexSource = null;
        episodeIndexCache = new IdentityHashMap<>();
        explicitSeasonSource = null;
        explicitSeasonCache = false;
        clearSeasonResolutionCache();
    }

    private void clearSeasonResolutionCache() {
        sourceSeasonCacheSource = null;
        sourceSeasonCache = List.of();
        availableSeasonCacheSource = null;
        availableSeasonCacheFlag = null;
        availableSeasonCache = List.of();
        clearVisibleEpisodeCache();
    }

    private void clearVisibleEpisodeCache() {
        visibleEpisodeSource = null;
        visibleEpisodeCache = List.of();
        visibleEpisodeSeason = Integer.MIN_VALUE;
    }

    private List<Integer> sourceSeasonNumbers(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return List.of();
        if (episodes == sourceSeasonCacheSource) return sourceSeasonCache;
        sourceSeasonCacheSource = episodes;
        List<Integer> sourceSeasonNumbers = new ArrayList<>(episodes.size());
        for (Episode episode : episodes) sourceSeasonNumbers.add(explicitSourceSeasonNumber(episode));
        sourceSeasonCache = List.copyOf(sourceSeasonNumbers);
        return sourceSeasonCache;
    }

    private List<Integer> availableSeasonNumbers(List<Episode> episodes) {
        if (episodes == availableSeasonCacheSource && selectedFlag == availableSeasonCacheFlag) return availableSeasonCache;
        availableSeasonCacheSource = episodes;
        availableSeasonCacheFlag = selectedFlag;
        if (tmdbSeasonBinding != null && tmdbSeasonBinding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_FLAT) {
            availableSeasonCache = List.of();
            return availableSeasonCache;
        }
        if (tmdbSeasonBinding != null && tmdbSeasonBinding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_MULTI_SLICE) {
            if (!tmdbSeasonBinding.getSegments().isEmpty()) {
                availableSeasonCache = tmdbSeasonBinding.getSegments().stream()
                        .map(TmdbSeasonSegment::getSeasonNumber)
                        .distinct()
                        .filter(seasonNumbers::contains)
                        .toList();
                return availableSeasonCache;
            }
            if (canApplyValidatedFlatSeasonMapping(episodes)) {
                availableSeasonCache = EpisodeSeasonPolicy.mappedSeasonsByEpisodeNumbers(
                        sourceEpisodeNumbers(episodes), seasonNumbers, seasonEpisodeCounts);
                return availableSeasonCache;
            }
            availableSeasonCache = List.of();
            return availableSeasonCache;
        }
        if (tmdbSeasonBinding != null
                && tmdbSeasonBinding.getMode() == TmdbSeasonMatchCache.Mode.MANUAL_SEASON
                && tmdbSeasonBinding.getSeasonNumber() != null
                && seasonNumbers.contains(tmdbSeasonBinding.getSeasonNumber())) {
            availableSeasonCache = List.of(tmdbSeasonBinding.getSeasonNumber());
            return availableSeasonCache;
        }
        availableSeasonCache = EpisodeSeasonPolicy.resolveAvailableSeasons(
                sourceSeasonNumbers(episodes),
                sourceTitleSeasonNumber(),
                firstSeasonNumber(matchedTmdbDetail),
                seasonNumbers,
                seasonEpisodeCounts,
                sourceEpisodeNumbers(episodes));
        return availableSeasonCache;
    }

    private void renderSeasonSelection() {
        List<Episode> episodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        List<Integer> availableSeasons = availableSeasonNumbers(episodes);
        boolean hasSeasons = availableSeasons.size() > 1;
        binding.seasonScroll.setVisibility(hasSeasons ? View.VISIBLE : View.GONE);
        binding.seasonContainer.removeAllViews();
        if (!hasSeasons) return;
        for (Integer season : availableSeasons) {
            MaterialButton button = createChipButton(getString(R.string.detail_season_format, season));
            setChipState(button, season == selectedSeasonNumber);
            button.setOnClickListener(view -> {
                cancelPendingInlinePlayback();
                playbackSelectionTouched = true;
                selectedSeasonNumber = season;
                List<Episode> visibleEpisodes = visibleEpisodes(episodes);
                selectedEpisode = visibleEpisodes.isEmpty() ? null : visibleEpisodes.get(0);
                resetEpisodeRange();
                renderSeasonSelection();
                fetchSeasonIfNeeded(tmdbEpisodeDataSeason(episodes));
                renderEpisodes();
                refreshSeasonSourceRoutes();
            });
            binding.seasonContainer.addView(button);
        }
    }

    private void updateSeasonButtonStates() {
        List<Episode> episodes = selectedFlag == null ? null : selectedFlag.getEpisodes();
        List<Integer> availableSeasons = availableSeasonNumbers(episodes);
        if (availableSeasons.size() <= 1) return;
        for (int i = 0; i < binding.seasonContainer.getChildCount() && i < availableSeasons.size(); i++) {
            View child = binding.seasonContainer.getChildAt(i);
            if (child instanceof MaterialButton button) setChipState(button, availableSeasons.get(i) == selectedSeasonNumber);
        }
    }

    private void bindSeasonEpisodes(List<Episode> sourceEpisodes) {
        tmdbEpisodes.clear();
        int tmdbSeason = tmdbEpisodeDataSeason(sourceEpisodes);
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(tmdbSeason);
        if (episodes != null) {
            for (TmdbEpisode episode : episodes) tmdbEpisodes.put(episode.getNumber(), episode);
        }
        bindTmdbEpisodes(sourceEpisodes, tmdbSeason);
        clearSeasonResolutionCache();
        bindSeasonTmdbMedia(tmdbSeason);
        fetchSeasonIfNeeded(tmdbSeason);
        refreshFirstSeasonIfStaleSplit(sourceEpisodes);
    }

    private void bindTmdbEpisodes(List<Episode> sourceEpisodes, int tmdbSeason) {
        if (sourceEpisodes == null || sourceEpisodes.isEmpty()) return;
        Map<Episode, Integer> indices = episodeIndices(sourceEpisodes);
        for (Episode episode : sourceEpisodes) {
            int index = indices.getOrDefault(episode, -1);
            if (index < 0) index = sourceEpisodes.indexOf(episode);
            EpisodePosition position = episodePosition(episode, sourceEpisodes, index);
            if (position.season() == tmdbSeason) {
                TmdbEpisode tmdbEpisode = tmdbEpisodes.get(position.number());
                boolean mapped = tmdbEpisode != null && episode.getNumber() > 0 && episode.getNumber() != position.number();
                boolean valid = mapped
                        ? TmdbEpisodeMatcher.shouldApplyMapped(episode, tmdbEpisode, position.season(), position.number())
                        : TmdbEpisodeMatcher.shouldApply(episode, tmdbEpisode, position.number());
                if (valid && mapped) episode.setMappedTmdbEpisode(tmdbEpisode);
                else episode.setTmdbEpisode(valid ? tmdbEpisode : null);
            }
        }
    }

    private int tmdbEpisodeDataSeason(List<Episode> sourceEpisodes) {
        List<Integer> availableSeasons = availableSeasonNumbers(sourceEpisodes);
        if (availableSeasons.size() == 1) return availableSeasons.get(0);
        if (availableSeasons.contains(selectedSeasonNumber)) return selectedSeasonNumber;
        Integer fallbackSeason = tmdbSeasonChoiceResolution().getSelectedSeason();
        return fallbackSeason == null || !seasonNumbers.contains(fallbackSeason) ? -1 : fallbackSeason;
    }

    private void fetchSeasonIfNeeded(int seasonNumber) {
        fetchSeasonIfNeeded(seasonNumber, false);
    }

    private void fetchSeasonIfNeeded(int seasonNumber, boolean refresh) {
        if (seasonNumber < 0 || (!refresh && tmdbSeasonEpisodes.containsKey(seasonNumber)) || loadingSeasons.contains(seasonNumber) || matchedTmdbItem == null || !"tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) || !canMatchTmdb()) return;
        int generation = loadGeneration;
        TmdbItem item = matchedTmdbItem;
        JsonObject detail = matchedTmdbDetail;
        TmdbConfig config = tmdbConfig;
        // 源站季号可能因 TMDB 更名与 season_number 存在偏移，仅向 TMDB 请求时使用校正后的季号，
        // 本地缓存仍以源站季号(seasonNumber)为 key，保证 UI 层无感。
        int tmdbSeason = EpisodeSeasonPolicy.correctTmdbSeason(seasonNumber, item.getTitle());
        loadingSeasons.add(seasonNumber);
        updateEpisodeSkeleton();
        detailTasks.submit(Task.largeExecutor(), () -> {
            try {
                JsonObject season = tmdbService.season(item, tmdbSeason, config, detail, refresh);
                List<TmdbEpisode> episodes = tmdbService.episodes(season, config, item.getTmdbId(), tmdbSeason);
                List<TmdbPerson> cast = tmdbService.seasonCast(season, config);
                List<String> photos = tmdbService.seasonPhotos(season, config);
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item)) return;
                    loadingSeasons.remove(seasonNumber);
                    tmdbSeasonEpisodes.put(seasonNumber, episodes);
                    seasonEpisodeCounts.put(seasonNumber, episodes.size());
                    tmdbSeasonCast.put(seasonNumber, cast);
                    tmdbSeasonPhotos.put(seasonNumber, photos);
                    clearSeasonResolutionCache();
                    lastEpisodeMediaSeason = Integer.MIN_VALUE;
                    if (seasonNumber == tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes())) {
                        renderEpisodes();
                        binding.episodeContainer.post(() -> {
                            if (!isTmdbRequestCurrent(generation, item)) return;
                            rerenderEpisodeViewportOnly(false, true, true);
                        });
                    }
                });
            } catch (Throwable ignored) {
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item)) return;
                    loadingSeasons.remove(seasonNumber);
                    updateEpisodeSkeleton();
                });
            }
        });
    }

    private void refreshFirstSeasonIfStaleSplit(List<Episode> sourceEpisodes) {
        if (sourceEpisodes == null || sourceEpisodes.isEmpty() || hasExplicitSeasonNumbers(sourceEpisodes)) return;
        int firstSeason = firstSeasonNumber(matchedTmdbDetail);
        if (firstSeason < 0 || refreshedSingleSeasonProbes.contains(firstSeason) || loadingSeasons.contains(firstSeason)) return;
        int expectedCount = Math.max(0, seasonEpisodeCounts.getOrDefault(firstSeason, 0));
        List<TmdbEpisode> cachedEpisodes = tmdbSeasonEpisodes.get(firstSeason);
        int cachedCount = cachedEpisodes == null ? 0 : cachedEpisodes.size();
        int neededCount = expectedCount > 0 ? Math.min(expectedCount, sourceEpisodes.size()) : sourceEpisodes.size();
        if (cachedCount >= neededCount) return;
        refreshedSingleSeasonProbes.add(firstSeason);
        fetchSeasonIfNeeded(firstSeason, true);
    }

    private void updateEpisodeSkeleton() {
        int tmdbSeason = tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes());
        boolean loading = tmdbSeason >= 0 && loadingSeasons.contains(tmdbSeason);
        binding.episodeSkeleton.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void bindSeasonTmdbMedia(int seasonNumber) {
        castItems.clear();
        List<TmdbPerson> seasonCast = tmdbSeasonCast.get(seasonNumber);
        castItems.addAll(seasonCast == null || seasonCast.isEmpty() ? detailCastItems : seasonCast);

        tmdbEpisodePhotos.clear();
        List<String> seasonPhotos = tmdbSeasonPhotos.get(seasonNumber);
        tmdbEpisodePhotos.addAll(seasonPhotos == null || seasonPhotos.isEmpty() ? detailTmdbPhotos : seasonPhotos);
    }

    private boolean hasExplicitSeasonNumbers(List<Episode> episodes) {
        if (episodes == explicitSeasonSource) return explicitSeasonCache;
        explicitSeasonSource = episodes;
        explicitSeasonCache = false;
        if (episodes == null) return false;
        for (Episode episode : episodes) {
            if (sourceSeasonNumber(episode) <= 0) continue;
            explicitSeasonCache = true;
            break;
        }
        return explicitSeasonCache;
    }


    private boolean hasCompleteExplicitSeasonMapping(List<Episode> episodes) {
        return EpisodeSeasonPolicy.hasCompleteExplicitSeasonMapping(sourceSeasonNumbers(episodes), seasonNumbers);
    }

    private int sourceEpisodeNumber(Episode episode) {
        return episode == null ? -1 : episode.getNumber();
    }

    private int explicitSourceSeasonNumber(Episode episode) {
        if (episode == null) return -1;
        String name = episode.getName();
        int sourceSeason;
        synchronized (sourceEpisodeSeasonCache) {
            SourceEpisodeSeason cached = sourceEpisodeSeasonCache.get(episode);
            if (cached != null && TextUtils.equals(cached.name(), name)) {
                sourceSeason = cached.season();
            } else {
                sourceSeason = EpisodeSeasonPolicy.resolveExplicitSourceSeason(episode.getName());
                sourceEpisodeSeasonCache.put(episode, new SourceEpisodeSeason(name, sourceSeason));
            }
        }
        return sourceSeason;
    }

    private int sourceSeasonNumber(Episode episode) {
        if (episode == null) return -1;
        int sourceSeason = explicitSourceSeasonNumber(episode);
        if (sourceSeason >= 0) return sourceSeason;
        TmdbEpisode tmdbEpisode = episode.getTmdbEpisode();
        return tmdbEpisode != null && tmdbEpisode.getNumber() > 0 && tmdbEpisode.getSeasonNumber() >= 0
                ? tmdbEpisode.getSeasonNumber()
                : -1;
    }

    private int sourceSeasonNumberAt(List<Episode> episodes, int index, Episode episode) {
        if (episodes != null && index >= 0 && index < episodes.size()) {
            List<Integer> sourceSeasons = sourceSeasonNumbers(episodes);
            if (index < sourceSeasons.size()) return sourceSeasons.get(index);
        }
        return sourceSeasonNumber(episode);
    }

    private int sourceTitleSeasonNumber() {
        int number = selectedFlag == null ? -1 : EpisodeSeasonPolicy.resolveExplicitSourceSeason(selectedFlag.getShow());
        if (number >= 0) return number;
        number = sourceSeasonNumber(sourceVodName);
        if (number >= 0) return number;
        number = sourceSeasonNumber(getNameText());
        if (number >= 0) return number;
        number = vod == null ? -1 : sourceSeasonNumber(vod.getName());
        if (number >= 0) return number;
        return sourceEpisodeSeasonNumber();
    }


    private int sourceEpisodeSeasonNumber() {
        List<Flag> flags = selectedFlag != null ? List.of(selectedFlag) : vod == null ? List.of() : vod.getFlags();
        Integer season = null;
        for (Flag flag : flags) {
            if (flag == null || flag.getEpisodes() == null) continue;
            List<Integer> sourceSeasons = sourceSeasonNumbers(flag.getEpisodes());
            for (int candidate : sourceSeasons) {
                if (candidate < 0) continue;
                if (season != null && season != candidate) return -1;
                season = candidate;
            }
        }
        return season == null ? -1 : season;
    }

    private int sourceSeasonNumber(String text) {
        if (TextUtils.isEmpty(text)) return -1;
        int resolved = EpisodeSeasonPolicy.resolveSourceSeason(text);
        if (resolved >= 0) return resolved;
        Matcher matcher = SOURCE_SEASON.matcher(text);
        while (matcher.find()) {
            int number = normalizeSourceNumber(firstNonEmptyGroup(matcher, 1, 2, 3));
            if (number >= 0) return number;
        }
        return -1;
    }

    private String firstNonEmptyGroup(Matcher matcher, int... groups) {
        if (matcher == null || groups == null) return "";
        for (int group : groups) {
            String value = matcher.group(group);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private int normalizeSourceNumber(String value) {
        if (TextUtils.isEmpty(value)) return -1;
        value = value.trim();
        try {
            if (value.matches("\\d+")) return Integer.parseInt(value.replaceFirst("^0+(?!$)", ""));
        } catch (Exception ignored) {
            return -1;
        }
        int number = parseSmallChineseNumber(value);
        return number > 0 ? number : -1;
    }

    private int parseSmallChineseNumber(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        value = value.replace("两", "二").replace("零", "").replace("〇", "");
        if (value.matches("[一二三四五六七八九]")) return chineseDigit(value.charAt(0));
        int tenIndex = value.indexOf("十");
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : chineseDigit(value.charAt(tenIndex - 1));
            int ones = tenIndex == value.length() - 1 ? 0 : chineseDigit(value.charAt(tenIndex + 1));
            return tens * 10 + ones;
        }
        return 0;
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }

    private List<Episode> visibleEpisodes(List<Episode> episodes) {
        if (episodes == visibleEpisodeSource && selectedSeasonNumber == visibleEpisodeSeason) return visibleEpisodeCache;
        visibleEpisodeCache = computeVisibleEpisodes(episodes);
        visibleEpisodeSource = episodes;
        visibleEpisodeSeason = selectedSeasonNumber;
        return visibleEpisodeCache;
    }

    private List<Episode> computeVisibleEpisodes(List<Episode> episodes) {
        if (episodes == null || episodes.isEmpty()) return List.of();
        List<Integer> availableSeasons = availableSeasonNumbers(episodes);
        if (availableSeasons.size() <= 1 || selectedSeasonNumber < 0) return episodes;
        if (!availableSeasons.contains(selectedSeasonNumber)) return episodes;
        if (tmdbSeasonBinding != null && !tmdbSeasonBinding.getSegments().isEmpty()) {
            TmdbSeasonSegment segment = null;
            for (TmdbSeasonSegment candidate : tmdbSeasonBinding.getSegments()) {
                if (candidate.getSeasonNumber() == selectedSeasonNumber) {
                    segment = candidate;
                    break;
                }
            }
            if (segment != null) {
                int start = Math.max(0, segment.getSourceEpisodeStartIndex());
                int end = Math.min(episodes.size() - 1, segment.getSourceEpisodeEndIndex());
                if (start <= end) return new ArrayList<>(episodes.subList(start, end + 1));
            }
        }
        if (hasCompleteExplicitSeasonMapping(episodes)) {
            List<Episode> visible = new ArrayList<>();
            List<Integer> sourceSeasons = sourceSeasonNumbers(episodes);
            for (int i = 0; i < episodes.size(); i++) if (sourceSeasons.get(i) == selectedSeasonNumber) visible.add(episodes.get(i));
            return visible.isEmpty() ? episodes : visible;
        }
        if (EpisodeSeasonPolicy.canMapFlatEpisodeKeys(sourceEpisodeNumbers(episodes), seasonNumbers, seasonEpisodeCounts)) {
            List<Episode> visible = new ArrayList<>();
            for (Episode episode : episodes) {
                EpisodeSeasonPolicy.SeasonEpisode mapped = EpisodeSeasonPolicy.mapFlatEpisodeNumber(
                        episode == null ? -1 : episode.getNumber(), seasonNumbers, seasonEpisodeCounts);
                if (mapped != null && mapped.seasonNumber() == selectedSeasonNumber) visible.add(episode);
            }
            return visible.isEmpty() ? episodes : visible;
        }
        return episodes;
    }

    private int seasonForEpisode(Episode episode, List<Episode> episodes) {
        List<Integer> availableSeasons = availableSeasonNumbers(episodes);
        if (availableSeasons.isEmpty()) return -1;
        if (availableSeasons.size() == 1) return availableSeasons.get(0);
        int index = episode == null || episodes == null ? -1 : episodes.indexOf(episode);
        int sourceSeason = sourceSeasonNumberAt(episodes, index, episode);
        if (availableSeasons.contains(sourceSeason)) return sourceSeason;
        if (index >= 0 && tmdbSeasonBinding != null) {
            for (TmdbSeasonSegment segment : tmdbSeasonBinding.getSegments()) {
                if (index >= segment.getSourceEpisodeStartIndex()
                        && index <= segment.getSourceEpisodeEndIndex()
                        && availableSeasons.contains(segment.getSeasonNumber())) return segment.getSeasonNumber();
            }
        }
        EpisodeSeasonPolicy.SeasonEpisode mapped = EpisodeSeasonPolicy.mapFlatEpisodeNumber(
                episode == null ? -1 : episode.getNumber(), seasonNumbers, seasonEpisodeCounts);
        if (mapped != null && availableSeasons.contains(mapped.seasonNumber())) return mapped.seasonNumber();
        if (selectedSeasonNumber > 0 && availableSeasons.contains(selectedSeasonNumber) && sourceEpisodeNumber(episode) > 0) return selectedSeasonNumber;
        return availableSeasons.get(0);
    }

    private boolean onRecommendationLongClick(TmdbItem item, String source) {
        com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog.show(this, item, source, this::onRecommendationNotInterested);
        return true;
    }

    private void onRecommendationNotInterested(TmdbItem item) {
        relatedItems.removeIf(candidate -> sameRecommendationItem(candidate, item));
        personalTmdbItems.removeIf(candidate -> sameRecommendationItem(candidate, item));
        personalDoubanItems.removeIf(candidate -> sameRecommendationItem(candidate, item));
        personalAiItems.removeIf(candidate -> sameRecommendationItem(candidate, item));
        relatedAdapter.removeItem(item);
        personalTmdbAdapter.removeItem(item);
        personalDoubanAdapter.removeItem(item);
        personalAiAdapter.removeItem(item);
        showAiRecommendationReason(item, false);
        bindTmdbSection();
    }

    private boolean sameRecommendationItem(TmdbItem first, TmdbItem second) {
        return TmdbRecommendationRows.sameIdentity(first, second);
    }

    private boolean mergeRecommendationRatings(List<TmdbItem> current, List<TmdbItem> enriched) {
        if (current == null || enriched == null || current.isEmpty() || enriched.isEmpty()) return false;
        boolean changed = false;
        for (TmdbItem candidate : enriched) {
            if (candidate == null || candidate.getDoubanRating() <= 0) continue;
            for (int index = 0; index < current.size(); index++) {
                TmdbItem existing = current.get(index);
                if (!sameRecommendationItem(existing, candidate)) continue;
                if (Double.compare(existing.getDoubanRating(), candidate.getDoubanRating()) != 0) {
                    current.set(index, candidate);
                    changed = true;
                }
                break;
            }
        }
        return changed;
    }

    private void loadRelatedVideosForCurrentContext() {
        if (matchedTmdbItem == null || tmdbConfig == null || !tmdbConfig.isReady() || !isTmdbAllowedForCurrentSite()) return;
        int seasonNumber = -1;
        int episodeNumber = -1;
        if (matchedTmdbItem.isTv()) {
            TmdbEpisode tmdbEpisode = selectedEpisode == null ? null : selectedEpisode.getTmdbEpisode();
            seasonNumber = tmdbEpisode != null && tmdbEpisode.getSeasonNumber() >= 0 ? tmdbEpisode.getSeasonNumber() : selectedSeasonNumber;
            episodeNumber = tmdbEpisode != null && tmdbEpisode.getNumber() > 0 ? tmdbEpisode.getNumber() : sourceEpisodeNumber(selectedEpisode);
        }
        String contextKey = matchedTmdbItem.getMediaType() + ":" + matchedTmdbItem.getTmdbId() + ":" + seasonNumber + ":" + episodeNumber + ":" + tmdbConfig.getLanguage();
        if (contextKey.equals(relatedVideoContextKey)) return;
        int generation = loadGeneration;
        int videoGeneration = ++relatedVideoGeneration;
        TmdbItem item = matchedTmdbItem;
        final int requestSeasonNumber = seasonNumber;
        final int requestEpisodeNumber = episodeNumber;
        relatedVideoContextKey = contextKey;
        relatedVideoLoading = true;
        relatedVideoItems.clear();
        bindTmdbSection();
        detailTasks.submit(() -> {
            List<TmdbVideo> loaded = new ArrayList<>();
            try {
                loaded = tmdbService.relatedVideos(item, requestSeasonNumber, requestEpisodeNumber, tmdbConfig);
            } catch (Throwable e) {
                SpiderDebug.log("tmdb-video", "standalone related failed media=%s id=%d season=%d episode=%d error=%s", item.getMediaType(), item.getTmdbId(), requestSeasonNumber, requestEpisodeNumber, e.getMessage());
            }
            List<TmdbVideo> result = loaded;
            runOnUiThread(() -> {
                if (generation != loadGeneration || videoGeneration != relatedVideoGeneration || !contextKey.equals(relatedVideoContextKey) || !isSameTmdbItem(item, matchedTmdbItem)) return;
                relatedVideoLoading = false;
                relatedVideoItems.clear();
                relatedVideoItems.addAll(result);
                bindTmdbSection();
            });
        });
    }

    private void bindTmdbSection() {
        if (!isTmdbAllowedForCurrentSite()) {
            binding.tmdbSection.setVisibility(View.GONE);
            showAiRecommendationReason(null, false);
            return;
        }
        boolean hasCast = !castItems.isEmpty();
        boolean hasCreators = !creatorItems.isEmpty();
        boolean hasPhotos = !tmdbEpisodePhotos.isEmpty();
        boolean hasPosters = !detailTmdbPosters.isEmpty();
        boolean hasRelated = !relatedItems.isEmpty();
        boolean hasRelatedVideos = !relatedVideoItems.isEmpty();
        boolean hasPersonalTmdb = !personalTmdbItems.isEmpty();
        boolean hasPersonalDouban = !personalDoubanItems.isEmpty();
        boolean hasPersonalAi = !personalAiItems.isEmpty();
        boolean hasExternalLinks = binding.externalLinksContainer.getChildCount() > 0;
        binding.tmdbSection.setVisibility(hasPhotos || hasPosters || hasCast || hasCreators || hasRelated || hasRelatedVideos || hasPersonalTmdb || hasPersonalDouban || hasPersonalAi || hasExternalLinks || matchedTmdbDetail != null || canMatchTmdb() ? View.VISIBLE : View.GONE);

        binding.episodePhotoTitle.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        binding.episodePhotoList.setVisibility(hasPhotos ? View.VISIBLE : View.GONE);
        episodePhotoAdapter.setItems(tmdbEpisodePhotos);
        episodePhotoAdapter.rebindAttached(binding.episodePhotoList);

        setTopMargin(binding.posterTitle, hasPhotos ? 20 : 0);
        binding.posterTitle.setVisibility(hasPosters ? View.VISIBLE : View.GONE);
        binding.posterList.setVisibility(hasPosters ? View.VISIBLE : View.GONE);
        posterAdapter.setItems(detailTmdbPosters);
        posterAdapter.rebindAttached(binding.posterList);

        setTopMargin(binding.relatedVideoTitle, hasPhotos || hasPosters ? 20 : 0);
        binding.relatedVideoTitle.setVisibility(hasRelatedVideos ? View.VISIBLE : View.GONE);
        binding.relatedVideoList.setVisibility(hasRelatedVideos ? View.VISIBLE : View.GONE);
        relatedVideoAdapter.setItems(relatedVideoItems);
        relatedVideoAdapter.rebindAttached(binding.relatedVideoList);

        setTopMargin(binding.castTitle, hasPhotos || hasPosters || hasRelatedVideos ? 20 : 0);
        binding.castTitle.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        binding.castList.setVisibility(hasCast ? View.VISIBLE : View.GONE);
        castAdapter.setItems(castItems);
        castAdapter.rebindAttached(binding.castList);

        setTopMargin(binding.creatorTitle, hasPhotos || hasPosters || hasRelatedVideos || hasCast ? 20 : 0);
        binding.creatorTitle.setVisibility(hasCreators ? View.VISIBLE : View.GONE);
        binding.creatorList.setVisibility(hasCreators ? View.VISIBLE : View.GONE);
        creatorAdapter.setItems(creatorItems);
        creatorAdapter.rebindAttached(binding.creatorList);

        setTopMargin(binding.relatedTitle, hasPhotos || hasPosters || hasRelatedVideos || hasCast || hasCreators ? 20 : 0);
        binding.relatedTitle.setVisibility(hasRelated ? View.VISIBLE : View.GONE);
        binding.relatedList.setVisibility(hasRelated ? View.VISIBLE : View.GONE);
        relatedAdapter.setItems(relatedItems);
        relatedAdapter.rebindAttached(binding.relatedList);

        setTopMargin(binding.personalTmdbTitle, hasPhotos || hasPosters || hasCast || hasCreators || hasRelated || hasRelatedVideos ? 20 : 0);
        binding.personalTmdbTitle.setVisibility(hasPersonalTmdb ? View.VISIBLE : View.GONE);
        binding.personalTmdbList.setVisibility(hasPersonalTmdb ? View.VISIBLE : View.GONE);
        personalTmdbAdapter.setItems(personalTmdbItems);
        personalTmdbAdapter.rebindAttached(binding.personalTmdbList);

        setTopMargin(binding.personalDoubanTitle, hasPhotos || hasPosters || hasCast || hasCreators || hasRelated || hasRelatedVideos || hasPersonalTmdb ? 20 : 0);
        binding.personalDoubanTitle.setVisibility(hasPersonalDouban ? View.VISIBLE : View.GONE);
        binding.personalDoubanList.setVisibility(hasPersonalDouban ? View.VISIBLE : View.GONE);
        personalDoubanAdapter.setItems(personalDoubanItems);
        personalDoubanAdapter.rebindAttached(binding.personalDoubanList);

        setTopMargin(binding.personalAiTitle, hasPhotos || hasPosters || hasCast || hasCreators || hasRelated || hasRelatedVideos || hasPersonalTmdb || hasPersonalDouban ? 20 : 0);
        binding.personalAiTitle.setVisibility(hasPersonalAi ? View.VISIBLE : View.GONE);
        binding.personalAiList.setVisibility(hasPersonalAi ? View.VISIBLE : View.GONE);
        personalAiAdapter.setItems(personalAiItems);
        personalAiAdapter.rebindAttached(binding.personalAiList);
        if (!hasPersonalAi) showAiRecommendationReason(null, false);

        // 防御性加固：这些列表与选集同为嵌套 RecyclerView，慢查询多阶段 apply 下理论上也可能
        // 卡成“适配器有数据、屏上 0 卡片”的错位态（实测未复现，因它们是固定高度不会塌陷，触发
        // 条件苛刻得多）。下一帧统一检测并恢复，成因与破法见 recoverRecyclerViewIfDetached。
        binding.episodePhotoList.post(this::recoverTmdbSectionListsIfDetached);

        setTopMargin(binding.externalLinksTitle, hasPhotos || hasPosters || hasCast || hasCreators || hasRelated || hasRelatedVideos || hasPersonalTmdb || hasPersonalDouban || hasPersonalAi ? 20 : 0);
        binding.externalLinksTitle.setVisibility(hasExternalLinks ? View.VISIBLE : View.GONE);
        binding.externalLinksContainer.setVisibility(hasExternalLinks ? View.VISIBLE : View.GONE);

        if (!tmdbConfig.isReady()) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_need_key);
        } else if (!isTmdbAllowedForCurrentSite()) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_site_disabled);
        } else if (!hasPhotos && !hasPosters && !hasCast && !hasCreators && !hasRelated && !hasRelatedVideos && !hasPersonalTmdb && !hasPersonalDouban && !hasPersonalAi && !hasExternalLinks) {
            binding.tmdbStatus.setVisibility(View.VISIBLE);
            binding.tmdbStatus.setText(R.string.detail_tmdb_empty);
        } else {
            binding.tmdbStatus.setVisibility(View.GONE);
        }
    }

    private void showAiRecommendationReason(TmdbItem item, boolean focused) {
        if (binding == null) return;
        String reason = item == null ? "" : item.getRecommendationReason();
        if (TextUtils.isEmpty(reason) && item != null) reason = item.getOverview();
        if (!focused || TextUtils.isEmpty(reason)) {
            binding.personalAiReason.setText("");
            binding.personalAiReason.setVisibility(View.GONE);
            return;
        }
        binding.personalAiReason.setText(getString(R.string.ai_recommendation_reason_preview, reason));
        binding.personalAiReason.setVisibility(View.VISIBLE);
        scrollAiRecommendationReasonIntoView();
    }

    private void scrollAiRecommendationReasonIntoView() {
        binding.personalAiReason.post(() -> {
            if (binding == null || binding.personalAiReason.getVisibility() != View.VISIBLE) return;
            Rect rect = new Rect(0, 0, binding.personalAiReason.getWidth(), binding.personalAiReason.getHeight());
            binding.scroll.offsetDescendantRectToMyCoords(binding.personalAiReason, rect);
            int viewportTop = binding.scroll.getScrollY();
            int viewportBottom = viewportTop + binding.scroll.getHeight() - binding.scroll.getPaddingBottom();
            int padding = ResUtil.dp2px(12);
            int bottomGap = rect.bottom - viewportBottom + padding;
            if (bottomGap > 0) {
                binding.scroll.smoothScrollBy(0, bottomGap);
            } else if (rect.top < viewportTop) {
                binding.scroll.smoothScrollBy(0, rect.top - viewportTop - padding);
            }
        });
    }

    private void initHistory() {
        History resumeHistory = getIntentResumeHistory();
        if (isResumeFromHistory() && resumeHistory == null) {
            Notify.show(R.string.history_record_missing);
            finish();
            return;
        }
        history = resumeHistory == null
                ? History.findPlayback(getHistoryKey(), List.of(vod.getName(), getNameText()), vod.getFlags(), matchedTmdbItem, sourceTitleSeasonNumber())
                : resumeHistory.forPlaybackKey(getHistoryKey(), VodConfig.getCid());
        if (history == null) {
            history = new History();
            history.setKey(getHistoryKey());
            history.setCid(VodConfig.getCid());
            history.setVodName(vod.getName());
            history.findEpisode(vod.getFlags());
        }
        if (!TextUtils.isEmpty(getMarkText())) history.setVodRemarks(getMarkText());
        syncDanmakuCompatHistory();
        updatePlayLabel();
    }

    private void reloadHistoryAfterTmdbMatch() {
        if (vod == null || matchedTmdbItem == null || isResumeFromHistory()) return;
        initHistory();
    }

    private void applyReloadedHistorySelection() {
        if (playbackSelectionTouched || detailPlayerActive || inlineStarted || vod == null || vod.getFlags() == null || vod.getFlags().isEmpty()) return;
        selectedFlag = findInitialFlag(vod.getFlags());
        selectedEpisode = null;
        selectedSeasonNumber = getIntentPlaybackSeasonNumber();
        resetEpisodeRange();
        renderFlagSelection();
    }

    private float getInlinePlaybackSpeed() {
        return history == null ? PlayerSetting.getDefaultSpeed() : history.getPlaybackSpeed(PlayerSetting.getDefaultSpeed());
    }

    private void updatePlayLabel() {
        if (selectedEpisode != null) {
            boolean canResume = history != null && isHistoryEpisode(selectedEpisode, history) && history.getPosition() > 0;
            binding.play.setText(canResume ? getString(R.string.detail_play_resume, historyEpisodeTitle(selectedEpisode)) : getString(R.string.detail_play_now));
            return;
        }
        boolean hasResume = history != null && history.getPosition() > 0 && !TextUtils.isEmpty(history.getVodRemarks());
        binding.play.setText(hasResume ? getString(R.string.detail_play_resume, history.getVodRemarks()) : getString(R.string.detail_play_now));
    }

    private void onPlay() {
        if (vod == null) return;
        if (enterInlineFullscreenIfCurrentInlinePlayback(selectedEpisode)) return;
        saveInlineHistory();
        updateInlineHistory(selectedEpisode);
        if (isFusionMode()) playInline();
        else if (isPlayerMode()) playDetailFullscreen();
        else playDefaultPlayback();
    }

    private void playDefaultPlayback() {
        if (defaultPlaybackLaunchPending) return;
        defaultPlaybackLaunchPending = true;
        ActivityLaunch.postOnAnimation(this, () -> {
            defaultPlaybackLaunchPending = false;
            if (isFinishing() || isDestroyed()) return;
            long start = System.currentTimeMillis();
            logTmdbMatch("原生增强播放标题：raw=%s，缓存标题=%s，详情标题=%s，播放标题=%s", getTmdbRawTitle(), matchedTmdbItem == null ? "" : matchedTmdbItem.getTitle(), tmdbDetailTitle(matchedTmdbItem, matchedTmdbDetail), playbackHistoryName());
            TmdbItem item = playbackTmdbItem();
            EpisodePosition position = historyEpisodePosition(selectedEpisode);
            String tmdbDetailCacheKey = TmdbDetailCache.put(item, matchedTmdbDetail, detailCastItems);
            SpiderDebug.log("tmdb-tv", "play launch prep cost=%dms title=%s", System.currentTimeMillis() - start, playbackHistoryName());
            VideoActivity.startDirectTmdb(this, getKeyText(), getIdText(), playbackHistoryName(), playbackHistoryPic(), playbackMark(), fastPlaybackEpisodeTitles(), item, playbackTmdbVod(), vod, tmdbDetailCacheKey, playbackFlag(), selectedSeasonFlagKey(), playbackEpisodeName(), playbackEpisodeUrl(), position.season(), position.number(), isResumeFromHistory() ? getIntentResumeHistory() : null);
        });
    }

    private String playbackMark() {
        if (selectedEpisode != null && !TextUtils.isEmpty(selectedEpisode.getName())) return selectedEpisode.getName();
        return getMarkText();
    }

    private String playbackFlag() {
        return selectedFlag == null ? "" : selectedFlag.getFlag();
    }

    private String playbackEpisodeName() {
        return selectedEpisode == null ? playbackMark() : selectedEpisode.getName();
    }

    private String playbackEpisodeUrl() {
        return selectedEpisode == null ? "" : selectedEpisode.getUrl();
    }

    private ArrayList<String> fastPlaybackEpisodeTitles() {
        ArrayList<String> result = new ArrayList<>(1);
        if (selectedEpisode == null) return result;
        int number = episodeNumberForHistory(selectedEpisode);
        String title = tmdbEpisodeTitle(number);
        if (number > 0 && !TextUtils.isEmpty(title)) result.add(number + "\t" + title);
        return result;
    }

    private void setTopMargin(View view, int dp) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.topMargin = ResUtil.dp2px(dp);
        view.setLayoutParams(marginParams);
    }

    private void showTmdbEpisodeDetail(Episode episode, int episodeNumber, RecyclerView returnRecycler) {
        // 对话框关闭后完整重渲染剧集列表，防止焦点状态紊乱导致按钮失效
        android.content.DialogInterface.OnDismissListener dismissListener = d -> {
            if (binding == null || binding.episodeContainer == null || returnRecycler == null) return;
            // RecyclerView 可能仍在恢复布局，下一帧再完整重渲染（参考原生增强的 render 机制）
            binding.episodeContainer.post(() -> {
                // 完整重建列表 + 分组按钮（类似原生增强 render[0].run()）
                rerenderEpisodeViewportOnly(false, true, true);
                returnRecycler.post(() -> restoreEpisodeDetailFocus(returnRecycler, episode));
            });
        };

        // 电影场景：直接展示影片详情
        if (matchedTmdbItem != null && matchedTmdbItem.isMovie()) {
            // 电影场景复用 EpisodeDetailDialog（leanback/mobile 都有实现）
            com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog.show(this, episode, null, null, null, dismissListener);
            return;
        }
        // 剧集场景：原有逻辑
        int detailSeasonNumber = tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes());
        if (matchedTmdbItem == null || !"tv".equalsIgnoreCase(matchedTmdbItem.getMediaType()) || detailSeasonNumber < 0 || episodeNumber <= 0 || !canMatchTmdb()) {
            Notify.show(R.string.detail_tmdb_empty);
            return;
        }
        binding.loading.setVisibility(View.VISIBLE);
        int generation = loadGeneration;
        int detailGeneration = ++tmdbEpisodeDetailGeneration;
        int displaySeasonNumber = detailSeasonNumber;
        int seasonNumber = detailSeasonNumber;
        TmdbItem item = matchedTmdbItem;
        JsonObject baseDetail = matchedTmdbDetail;
        TmdbConfig config = tmdbConfig;
        detailTasks.submit(Task.largeExecutor(), () -> {
            try {
                JsonObject detail = tmdbService.episode(item, seasonNumber, episodeNumber, config, baseDetail);
                List<String> photos = tmdbService.episodePhotos(detail, config);
                List<TmdbPerson> guests = tmdbService.episodeGuests(detail, config);
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item) || detailGeneration != tmdbEpisodeDetailGeneration) {
                        // guard 失败时也要隐藏 loading，否则覆盖层会阻挡所有界面交互
                        binding.loading.setVisibility(View.GONE);
                        return;
                    }
                    binding.loading.setVisibility(View.GONE);
                    if (displaySeasonNumber != selectedSeasonNumber) return;
                    // 复用 EpisodeDetailDialog，传入已拉取的 photos/guests 避免重复 API 请求
                    com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog.show(this, episode, getSite(), photos, guests, dismissListener);
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (!isTmdbRequestCurrent(generation, item) || detailGeneration != tmdbEpisodeDetailGeneration) {
                        // guard 失败时也要隐藏 loading
                        binding.loading.setVisibility(View.GONE);
                        return;
                    }
                    binding.loading.setVisibility(View.GONE);
                    if (displaySeasonNumber != selectedSeasonNumber) return;
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void restoreEpisodeDetailFocus(RecyclerView recycler, Episode episode) {
        if (recycler == null || episode == null) return;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (!(adapter instanceof TmdbEpisodeAdapter episodeAdapter)) return;
        int position = episodeAdapter.getPosition(episode);
        if (position < 0) return;
        focusTmdbRecyclerItem(recycler, position);
    }

    /**
     * 电影场景：炫彩详情长按线路展示影片 TMDB 详情
     */
    private void showMovieDialog(Episode episode) {
        DialogTmdbEpisodeBinding dialogBinding = DialogTmdbEpisodeBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(dialogBinding.getRoot()).create();
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        dialogBinding.panel.setCardBackgroundColor(colors.panel);
        dialogBinding.panel.setStrokeColor(colors.line);
        tintTextTree(dialogBinding.getRoot(), colors);

        // 标题：影片名
        String movieTitle = matchedTmdbItem.getTitle();
        dialogBinding.title.setText(TextUtils.isEmpty(movieTitle) ? episode.getName() : movieTitle);

        // 原始名称：源站文件名，完整多行展示
        String sourceName = episode == null ? "" : episode.getName();
        if (!TextUtils.isEmpty(sourceName) && !sourceName.equals(movieTitle)) {
            dialogBinding.originalName.setText(getString(R.string.detail_episode_original_name, sourceName));
            dialogBinding.originalName.setVisibility(View.VISIBLE);
        } else {
            dialogBinding.originalName.setVisibility(View.GONE);
        }

        // meta：评分 / 上映日期 / 时长
        dialogBinding.meta.setText(movieMeta(matchedTmdbItem, matchedTmdbDetail));
        dialogBinding.meta.setVisibility(TextUtils.isEmpty(dialogBinding.meta.getText()) ? View.GONE : View.VISIBLE);

        // 简介
        String movieOverview = matchedTmdbItem.getOverview();
        if (TextUtils.isEmpty(movieOverview) && matchedTmdbDetail != null) movieOverview = string(matchedTmdbDetail, "overview");
        dialogBinding.overview.setText(TextUtils.isEmpty(movieOverview) ? getString(R.string.detail_tmdb_empty) : movieOverview);

        // crew 区域：电影无分集制作人员信息，隐藏
        dialogBinding.crewTitle.setVisibility(View.GONE);
        dialogBinding.crew.setVisibility(View.GONE);

        // 剧照区域：隐藏（或改用第一张背景图）
        dialogBinding.still.setVisibility(View.GONE);

        // 剧照列表与演员：异步加载
        TmdbPhotoAdapter photoAdapter = new TmdbPhotoAdapter((position, url) -> showPhotoDialog(position, url, new ArrayList<>()));
        photoAdapter.setLight(lightTheme);
        dialogBinding.photoList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dialogBinding.photoList.setNestedScrollingEnabled(false);
        dialogBinding.photoList.setAdapter(photoAdapter);
        dialogBinding.photoTitle.setVisibility(View.GONE);
        dialogBinding.photoList.setVisibility(View.GONE);

        TmdbPersonAdapter guestAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        guestAdapter.setItems(new ArrayList<>());
        dialogBinding.guestList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dialogBinding.guestList.setNestedScrollingEnabled(false);
        dialogBinding.guestList.setAdapter(guestAdapter);
        dialogBinding.guestTitle.setText(R.string.detail_tmdb_cast); // 改成"演员"而非"客串演员"
        dialogBinding.guestTitle.setVisibility(View.GONE);
        dialogBinding.guestList.setVisibility(View.GONE);

        dialogBinding.close.setOnClickListener(view -> dialog.dismiss());
        setButton(dialogBinding.close, colors.control, colors.line, colors.primary);

        dialog.show();
        dialogBinding.close.post(dialogBinding.close::requestFocus);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.56f);
            int width = getResources().getDisplayMetrics().widthPixels;
            window.setLayout((int) (width * (width >= 1200 ? 0.78f : 0.94f)), WindowManager.LayoutParams.WRAP_CONTENT);
        }

        // 异步加载影片剧照与演员
        if (matchedTmdbDetail != null) {
            detailTasks.submit(Task.largeExecutor(), () -> {
                try {
                    List<String> photos = tmdbService.photos(matchedTmdbDetail, tmdbConfig);
                    List<TmdbPerson> cast = tmdbService.cast(matchedTmdbDetail, tmdbConfig);
                    runOnAliveUi(() -> {
                        if (!photos.isEmpty()) {
                            photoAdapter.setItems(photos);
                            dialogBinding.photoTitle.setVisibility(View.VISIBLE);
                            dialogBinding.photoList.setVisibility(View.VISIBLE);
                        }
                        if (!cast.isEmpty()) {
                            guestAdapter.setItems(cast);
                            dialogBinding.guestTitle.setVisibility(View.VISIBLE);
                            dialogBinding.guestList.setVisibility(View.VISIBLE);
                        }
                    });
                } catch (Exception e) {
                    android.util.Log.w("TmdbDetailActivity", "load movie media failed", e);
                }
            });
        }
    }

    private String movieMeta(TmdbItem movieItem, JsonObject detail) {
        List<String> metas = new ArrayList<>();
        double rating = movieItem.getRating();
        if (rating <= 0 && detail != null) rating = doubleValue(detail, "vote_average");
        if (rating > 0) metas.add(String.format(Locale.US, "%.1f", rating));
        if (detail != null) {
            String date = string(detail, "release_date");
            if (!TextUtils.isEmpty(date)) metas.add(date);
            int runtime = (int) doubleValue(detail, "runtime");
            if (runtime > 0) metas.add(getString(R.string.detail_runtime_format, runtime));
        }
        return TextUtils.join(" · ", metas);
    }

    private double doubleValue(JsonObject obj, String key) {
        try {
            if (obj != null && obj.has(key) && !obj.get(key).isJsonNull()) return obj.get(key).getAsDouble();
        } catch (Exception ignored) {
        }
        return 0;
    }

    private void showTmdbEpisodeDialog(Episode episode, int episodeNumber, JsonObject detail, List<String> photos, List<TmdbPerson> guests) {
        DialogTmdbEpisodeBinding dialogBinding = DialogTmdbEpisodeBinding.inflate(getLayoutInflater());
        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(dialogBinding.getRoot()).create();
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        dialogBinding.panel.setCardBackgroundColor(colors.panel);
        dialogBinding.panel.setStrokeColor(colors.line);
        tintTextTree(dialogBinding.getRoot(), colors);
        dialogBinding.title.setText(episodeDetailTitle(episode, episodeNumber, detail));
        String sourceName = episode == null ? "" : episode.getName();
        CharSequence titleText = dialogBinding.title.getText();
        boolean titleHasSource = !TextUtils.isEmpty(titleText) && titleText.toString().contains(sourceName);
        if (!TextUtils.isEmpty(sourceName) && !titleHasSource) {
            dialogBinding.originalName.setText(getString(R.string.detail_episode_original_name, sourceName));
            dialogBinding.originalName.setVisibility(View.VISIBLE);
        } else {
            dialogBinding.originalName.setVisibility(View.GONE);
        }
        dialogBinding.meta.setText(episodeMeta(detail));
        dialogBinding.meta.setVisibility(TextUtils.isEmpty(dialogBinding.meta.getText()) ? View.GONE : View.VISIBLE);
        String overview = string(detail, "overview");
        if (TextUtils.isEmpty(overview)) overview = episode == null ? "" : episode.getDesc();
        dialogBinding.overview.setText(TextUtils.isEmpty(overview) ? getString(R.string.detail_tmdb_empty) : overview);
        String crew = episodeCrew(detail);
        dialogBinding.crewTitle.setVisibility(TextUtils.isEmpty(crew) ? View.GONE : View.VISIBLE);
        dialogBinding.crew.setText(crew);
        dialogBinding.crew.setVisibility(TextUtils.isEmpty(crew) ? View.GONE : View.VISIBLE);
        String still = photos.isEmpty() ? "" : photos.get(0);
        dialogBinding.still.setVisibility(TextUtils.isEmpty(still) ? View.GONE : View.VISIBLE);
        if (!TextUtils.isEmpty(still)) ImgUtil.load(episodeDetailTitle(episode, episodeNumber, detail), still, dialogBinding.still);

        TmdbPhotoAdapter photoAdapter = new TmdbPhotoAdapter((position, url) -> showPhotoDialog(position, url, photos));
        photoAdapter.setLight(lightTheme);
        photoAdapter.setItems(photos);
        dialogBinding.photoList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dialogBinding.photoList.setNestedScrollingEnabled(false);
        dialogBinding.photoList.setAdapter(photoAdapter);
        dialogBinding.photoTitle.setVisibility(photos.isEmpty() ? View.GONE : View.VISIBLE);
        dialogBinding.photoList.setVisibility(photos.isEmpty() ? View.GONE : View.VISIBLE);

        TmdbPersonAdapter guestAdapter = new TmdbPersonAdapter(this::loadPersonDetail);
        guestAdapter.setItems(guests);
        dialogBinding.guestList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        dialogBinding.guestList.setNestedScrollingEnabled(false);
        dialogBinding.guestList.setAdapter(guestAdapter);
        dialogBinding.guestTitle.setVisibility(guests.isEmpty() ? View.GONE : View.VISIBLE);
        dialogBinding.guestList.setVisibility(guests.isEmpty() ? View.GONE : View.VISIBLE);
        dialogBinding.close.setOnClickListener(view -> dialog.dismiss());
        setButton(dialogBinding.close, colors.control, colors.line, colors.primary);

        // 对话框关闭后刷新界面，防止按钮失效
        dialog.setOnDismissListener(d -> {
            if (binding == null || binding.episodeContainer == null) return;
            // DEBUG: 添加 Toast 确认修复代码已执行
            Notify.show("详情对话框已关闭，正在刷新界面");
            // RecyclerView 可能仍在恢复布局，下一帧再刷新
            binding.episodeContainer.post(() -> {
                if (episodeAdapter != null) {
                    episodeAdapter.refreshDisplaySettings(binding.episodeContainer);
                }
                // 刷新分组按钮和其他控件状态
                updateEpisodeViewModeButton();
                updateEpisodeFileNameButton();
            });
        });

        dialog.show();
        dialogBinding.close.post(dialogBinding.close::requestFocus);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setDimAmount(0.56f);
            int width = getResources().getDisplayMetrics().widthPixels;
            window.setLayout((int) (width * (width >= 1200 ? 0.78f : 0.94f)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showPhotoDialog(int position, String url) {
        if (TextUtils.isEmpty(url)) return;
        showPhotoDialog(position, url, new ArrayList<>(tmdbEpisodePhotos));
    }

    private void showPhotoDialog(int position, String url, List<String> sourcePhotos) {
        if (TextUtils.isEmpty(url)) return;
        List<String> photos = new ArrayList<>(sourcePhotos);
        if (photos.isEmpty()) photos.add(url);
        int start = position >= 0 && position < photos.size() ? position : Math.max(0, photos.indexOf(url));
        int[] current = new int[]{Math.max(0, start)};

        ImageView image = new ImageView(this);
        image.setFocusable(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(0xFF000000);
        image.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ResUtil.dp2px(38), ResUtil.dp2px(38), android.view.Gravity.CENTER);
        progress.setLayoutParams(progressParams);

        Dialog dialog = new Dialog(this);
        int[] request = new int[]{0};
        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(0xFF000000);
        content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(image);
        content.addView(progress);
        View photoActions = createPhotoActions(dialog, image, progress, photos, current, request);
        content.addView(photoActions);
        int originalOrientation = getRequestedOrientation();
        boolean wasFullscreen = Util.isFullscreen(this);

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(content);
        dialog.setOnDismissListener(instance -> {
            setRequestedOrientation(originalOrientation);
            if (!wasFullscreen) Util.toggleFullscreen(this, false);
        });
        GestureDetector photoGesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (photos.size() <= 1) {
                    dialog.dismiss();
                    return true;
                }
                float x = event.getX();
                int width = image.getWidth();
                if (x < width * 0.33f) {
                    showPhotoAt(image, progress, photos, current, request, -1);
                } else if (x > width * 0.67f) {
                    showPhotoAt(image, progress, photos, current, request, 1);
                } else {
                    dialog.dismiss();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent down, MotionEvent up, float velocityX, float velocityY) {
                if (photos.size() <= 1 || down == null || up == null) return false;
                float distanceX = up.getX() - down.getX();
                if (Math.abs(distanceX) < ResUtil.dp2px(48) || Math.abs(velocityX) < 120f) return false;
                showPhotoAt(image, progress, photos, current, request, distanceX < 0 ? 1 : -1);
                return true;
            }

            @Override
            public void onLongPress(MotionEvent event) {
                if (Util.isMobile()) showPhotoActionDialog(photos.get(current[0]));
            }
        });
        image.setOnTouchListener((view, event) -> photoGesture.onTouchEvent(event));
        dialog.setOnKeyListener((instance, keyCode, event) -> {
            if (!KeyUtil.isActionUp(event)) return false;
            if (photoActions.hasFocus()) return keyCode == KeyEvent.KEYCODE_BACK && dialogCancel(dialog);
            if (keyCode == KeyEvent.KEYCODE_MENU) {
                savePhoto(photos.get(current[0]), null);
                return true;
            }
            if (KeyUtil.isLeftKey(event)) {
                showPhotoAt(image, progress, photos, current, request, -1);
                return true;
            }
            if (KeyUtil.isRightKey(event)) {
                showPhotoAt(image, progress, photos, current, request, 1);
                return true;
            }
            if (KeyUtil.isEnterKey(event)) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.black);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            Util.hideSystemUI(window);
        }
        image.requestFocus();
        loadPhotoImage(image, progress, photos.get(current[0]), request);
        preloadPhotoNeighbors(photos, current[0]);
    }

    private View createPhotoActions(Dialog dialog, ImageView image, ProgressBar progress, List<String> photos, int[] current, int[] request) {
        if (Util.isLeanback()) return createPhotoRemoteActions(dialog, image, progress, photos, current, request);
        return createPhotoMobileActions(photos, current);
    }

    private View createPhotoMobileActions(List<String> photos, int[] current) {
        FrameLayout actions = new FrameLayout(this);
        actions.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        MaterialButton rotate = createPhotoButton(R.string.detail_image_rotate, R.drawable.ic_control_rotate);
        FrameLayout.LayoutParams rotateParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(44), Gravity.TOP | Gravity.START);
        rotateParams.setMargins(ResUtil.dp2px(24), ResUtil.dp2px(28), 0, 0);
        rotate.setLayoutParams(rotateParams);
        rotate.setOnClickListener(view -> togglePhotoOrientation());
        actions.addView(rotate);

        MaterialButton save = createPhotoButton(R.string.detail_image_save, R.drawable.ic_tmdb_download);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(44), Gravity.TOP | Gravity.END);
        params.setMargins(0, ResUtil.dp2px(28), ResUtil.dp2px(24), 0);
        save.setLayoutParams(params);
        save.setOnClickListener(view -> savePhoto(photos.get(current[0]), save));
        actions.addView(save);

        return actions;
    }

    private View createPhotoRemoteActions(Dialog dialog, ImageView image, ProgressBar progress, List<String> photos, int[] current, int[] request) {
        if (image.getId() == View.NO_ID) image.setId(View.generateViewId());
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(8), ResUtil.dp2px(10), ResUtil.dp2px(8));
        bar.setBackground(createPhotoPanelBackground());
        bar.setElevation(ResUtil.dp2px(8));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(64), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.setMargins(0, 0, 0, ResUtil.dp2px(32));
        bar.setLayoutParams(params);

        MaterialButton previous = createPhotoButton(R.string.detail_image_previous, 0);
        MaterialButton save = createPhotoButton(R.string.detail_image_save, R.drawable.ic_tmdb_download);
        MaterialButton next = createPhotoButton(R.string.detail_image_next, 0);
        MaterialButton close = createPhotoButton(R.string.detail_image_close, 0);
        previous.setOnClickListener(view -> showPhotoAt(image, progress, photos, current, request, -1));
        save.setOnClickListener(view -> savePhoto(photos.get(current[0]), save));
        next.setOnClickListener(view -> showPhotoAt(image, progress, photos, current, request, 1));
        close.setOnClickListener(view -> dialog.dismiss());
        addPhotoBarButton(bar, previous, image);
        addPhotoBarButton(bar, save, image);
        addPhotoBarButton(bar, next, image);
        addPhotoBarButton(bar, close, image);
        image.setNextFocusDownId(save.getId());
        return bar;
    }

    private void addPhotoBarButton(LinearLayout bar, MaterialButton button, ImageView image) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(46));
        params.setMargins(ResUtil.dp2px(5), 0, ResUtil.dp2px(5), 0);
        button.setLayoutParams(params);
        button.setNextFocusUpId(image.getId());
        bar.addView(button);
    }

    private MaterialButton createPhotoButton(int text, int icon) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        if (button.getId() == View.NO_ID) button.setId(View.generateViewId());
        button.setText(text);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        button.setCornerRadius(ResUtil.dp2px(23));
        button.setTextColor(0xFFFFFFFF);
        button.setIconTint(ColorStateList.valueOf(0xFFFFFFFF));
        if (icon != 0) {
            button.setIconResource(icon);
            button.setIconPadding(ResUtil.dp2px(6));
        }
        button.setBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        button.setRippleColor(ColorStateList.valueOf(0x33FFFFFF));
        applyPhotoButtonFocus(button, false);
        button.setOnFocusChangeListener((view, focused) -> applyPhotoButtonFocus(button, focused));
        return button;
    }

    private GradientDrawable createPhotoPanelBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(0xB3000000);
        drawable.setCornerRadius(ResUtil.dp2px(28));
        drawable.setStroke(ResUtil.dp2px(1), 0x26FFFFFF);
        return drawable;
    }

    private void applyPhotoButtonFocus(MaterialButton button, boolean focused) {
        button.setBackgroundTintList(ColorStateList.valueOf(focused ? 0x33FFFFFF : 0x18FFFFFF));
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : CHIP_STROKE_DP));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : 0x4DFFFFFF));
    }

    private void showPhotoActionDialog(String url) {
        new MaterialAlertDialogBuilder(this)
                .setItems(new CharSequence[]{getString(R.string.detail_image_save)}, (dialog, which) -> savePhoto(url, null))
                .show();
    }

    private void savePhoto(String url, @Nullable MaterialButton button) {
        if (TextUtils.isEmpty(url) || savingTmdbPhoto) return;
        savingTmdbPhoto = true;
        if (button != null) button.setEnabled(false);
        Notify.show(R.string.detail_image_saving);
        TmdbImageSaver.save(this, highResTmdbImage(url), new TmdbImageSaver.Callback() {
            @Override
            public void success(String name) {
                finishPhotoSave(button);
                Notify.show(getString(R.string.detail_image_save_success, name));
            }

            @Override
            public void error(String message) {
                finishPhotoSave(button);
                String prefix = getString(R.string.detail_image_save_failed);
                Notify.show(TextUtils.isEmpty(message) || prefix.equals(message) ? prefix : prefix + "\n" + message);
            }
        });
    }

    private void finishPhotoSave(@Nullable MaterialButton button) {
        savingTmdbPhoto = false;
        if (button != null) button.setEnabled(true);
    }

    private boolean dialogCancel(Dialog dialog) {
        dialog.dismiss();
        return true;
    }

    private void showPhotoAt(ImageView image, ProgressBar progress, List<String> photos, int[] current, int[] request, int direction) {
        if (photos.isEmpty()) return;
        int next = (current[0] + direction + photos.size()) % photos.size();
        if (next == current[0]) return;
        current[0] = next;
        loadPhotoImage(image, progress, photos.get(current[0]), request);
        preloadPhotoNeighbors(photos, current[0]);
    }

    private void loadPhotoImage(ImageView image, ProgressBar progress, String url, int[] request) {
        int token = ++request[0];
        progress.setVisibility(View.VISIBLE);
        try {
            Glide.with(image)
                    .load(ImgUtil.getUrl(highResTmdbImage(url)))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .fitCenter()
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            if (token != request[0]) return;
                            image.setImageDrawable(resource);
                            progress.setVisibility(View.GONE);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            if (token != request[0]) return;
                            if (image.getDrawable() == null && errorDrawable != null) image.setImageDrawable(errorDrawable);
                            progress.setVisibility(View.GONE);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Throwable e) {
            progress.setVisibility(View.GONE);
            Notify.show(R.string.detail_tmdb_empty);
        }
    }

    private void preloadPhotoNeighbors(List<String> photos, int current) {
        if (photos.size() <= 1) return;
        List<Integer> positions = new ArrayList<>();
        for (int offset = 1; offset <= PHOTO_PRELOAD_RADIUS && offset < photos.size(); offset++) {
            int next = (current + offset) % photos.size();
            int previous = (current - offset + photos.size()) % photos.size();
            if (!positions.contains(next)) positions.add(next);
            if (!positions.contains(previous)) positions.add(previous);
        }
        for (Integer position : positions) {
            String url = photos.get(position);
            if (TextUtils.isEmpty(url)) continue;
            Glide.with(this).load(ImgUtil.getUrl(highResTmdbImage(url))).diskCacheStrategy(DiskCacheStrategy.ALL).preload();
        }
    }

    private void togglePhotoOrientation() {
        if (!Util.isMobile()) return;
        int actual = getResources().getConfiguration().orientation;
        int target = actual == Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        setRequestedOrientation(target);
    }

    private String highResTmdbImage(String url) {
        return TmdbImageSelector.originalUrl(url);
    }

    private String episodeDetailTitle(Episode episode, int episodeNumber, JsonObject detail) {
        String name = string(detail, "name");
        if (TextUtils.isEmpty(name)) {
            TmdbEpisode tmdbEpisode = tmdbEpisodes.get(episodeNumber);
            name = tmdbEpisode == null ? "" : tmdbEpisode.getTitle();
        }
        if (TextUtils.isEmpty(name) && episode != null) name = episode.getName();
        return getString(R.string.detail_episode_detail_title, episodeNumber, name);
    }

    private String episodeMeta(JsonObject detail) {
        List<String> parts = new ArrayList<>();
        try {
            String date = string(detail, "air_date");
            if (!TextUtils.isEmpty(date)) parts.add(date);
            if (detail != null && detail.has("runtime") && !detail.get("runtime").isJsonNull()) {
                try {
                    int runtime = detail.get("runtime").getAsInt();
                    if (runtime > 0) parts.add(getString(R.string.detail_runtime_format, runtime));
                } catch (ClassCastException e) {
                    android.util.Log.e("TmdbDetailActivity", "ClassCastException getting runtime: " + e.getMessage());
                }
            }
            if (detail != null && detail.has("vote_average") && !detail.get("vote_average").isJsonNull()) {
                try {
                    double vote = detail.get("vote_average").getAsDouble();
                    if (vote > 0) parts.add(getString(R.string.detail_score, String.format(Locale.US, "%.1f", vote)));
                } catch (ClassCastException e) {
                    android.util.Log.e("TmdbDetailActivity", "ClassCastException getting vote_average: " + e.getMessage());
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("TmdbDetailActivity", "Error in episodeMeta: " + e.getMessage(), e);
        }
        return TextUtils.join(" · ", parts);
    }

    private String episodeCrew(JsonObject detail) {
        Map<String, List<String>> jobs = new LinkedHashMap<>();
        try {
            for (JsonElement element : array(detail, "crew")) {
                try {
                    if (!element.isJsonObject()) continue;
                    JsonObject person = element.getAsJsonObject();
                    String job = string(person, "job", "department");
                    String name = string(person, "name");
                    if (TextUtils.isEmpty(job) || TextUtils.isEmpty(name)) continue;
                    List<String> names = jobs.computeIfAbsent(job, key -> new ArrayList<>());
                    if (!names.contains(name) && names.size() < 4) names.add(name);
                    if (jobs.size() >= 6) break;
                } catch (ClassCastException e) {
                    android.util.Log.e("TmdbDetailActivity", "ClassCastException in episodeCrew element: " + e.getMessage());
                } catch (Throwable e) {
                    android.util.Log.e("TmdbDetailActivity", "Error parsing crew element: " + e.getMessage());
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("TmdbDetailActivity", "Error in episodeCrew: " + e.getMessage(), e);
        }
        List<String> lines = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : jobs.entrySet()) {
            if (!entry.getValue().isEmpty()) lines.add(entry.getKey() + ": " + TextUtils.join(" / ", entry.getValue()));
        }
        return TextUtils.join("\n", lines);
    }

    private ArrayList<String> selectedTmdbEpisodeTitles() {
        Map<Integer, String> titles = new LinkedHashMap<>();
        for (Map.Entry<Integer, TmdbEpisode> entry : tmdbEpisodes.entrySet()) {
            if (!TextUtils.isEmpty(entry.getValue().getTitle())) titles.put(entry.getKey(), entry.getValue().getTitle());
        }
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()));
        if (episodes != null) {
            for (TmdbEpisode episode : episodes) {
                if (!TextUtils.isEmpty(episode.getTitle())) titles.put(episode.getNumber(), episode.getTitle());
            }
        }
        ArrayList<String> result = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : titles.entrySet()) result.add(entry.getKey() + "\t" + entry.getValue());
        return result;
    }

    private Episode findEpisodeByUrl(String url, List<Episode> episodes) {
        if (TextUtils.isEmpty(url) || episodes == null) return null;
        for (Episode episode : episodes) if (url.equals(episode.getUrl())) return episode;
        return null;
    }

    private boolean isHistoryEpisode(Episode episode, History item) {
        if (episode == null || item == null) return false;
        Episode saved = item.getEpisode();
        if (item.getTmdbEpisodeNumber() > 0 && episode.getTmdbEpisode() != null && episode.getTmdbEpisode().getNumber() > 0) {
            return episode.matchesPlayback(saved);
        }
        if (!TextUtils.isEmpty(item.getEpisodeUrl()) && item.getEpisodeUrl().equals(episode.getUrl())) return true;
        return episode.matchesName(saved) || episode.getDisplayName().equals(item.getVodRemarks()) || historyEpisodeTitle(episode).equals(item.getVodRemarks());
    }

    private String historyEpisodeTitle(Episode episode) {
        int number = episodeNumberForHistory(episode);
        String label = number > 0 ? String.valueOf(number) : episode.getDisplayName();
        String title = tmdbEpisodeTitle(number);
        return TmdbEpisodeAdapter.formatTitle(label, episode.getName(), title);
    }

    private String tmdbEpisodeTitle(int number) {
        return tmdbEpisodeTitle(tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes()), number);
    }

    private String tmdbEpisodeTitle(int seasonNumber, int number) {
        if (number <= 0) return "";
        TmdbEpisode tmdbEpisode = seasonNumber == selectedSeasonNumber || seasonNumber < 0 ? tmdbEpisodes.get(number) : null;
        if (tmdbEpisode != null && !TextUtils.isEmpty(tmdbEpisode.getTitle())) return tmdbEpisode.getTitle();
        List<TmdbEpisode> episodes = tmdbSeasonEpisodes.get(seasonNumber < 0 ? selectedSeasonNumber : seasonNumber);
        if (episodes == null) return "";
        for (TmdbEpisode episode : episodes) {
            if (episode.getNumber() == number && !TextUtils.isEmpty(episode.getTitle())) return episode.getTitle();
        }
        return "";
    }

    private void refreshCurrentHistoryEpisodeTitle() {
        if (selectedEpisode == null || history == null || Setting.isIncognito()) return;
        History saved = History.find(getHistoryKey());
        if (saved == null || !isHistoryEpisode(selectedEpisode, saved)) return;
        String title = historyEpisodeTitle(selectedEpisode);
        boolean changed = setHistoryTmdbEpisodePosition(saved, selectedEpisode);
        if (!TextUtils.isEmpty(title) && !title.equals(saved.getVodRemarks())) {
            saved.setVodRemarks(title);
            changed = true;
        }
        if (!TextUtils.equals(selectedEpisode.getUrl(), saved.getEpisodeUrl())) {
            saved.setEpisodeUrl(selectedEpisode.getUrl());
            changed = true;
        }
        if (!changed) return;
        saved.setVodName(playbackHistoryName());
        saved.setVodPic(playbackHistoryPic());
        saved.save();
        history = saved;
        syncDanmakuCompatHistory();
        RefreshEvent.history();
    }

    private EpisodePosition historyEpisodePosition(Episode episode) {
        if (episode == null || selectedFlag == null || selectedFlag.getEpisodes() == null) return new EpisodePosition(-1, -1);
        return episodePosition(episode, selectedFlag.getEpisodes());
    }

    /**
     * 播放位置缓存统一使用源站集名，与 mobile 链路的约定一致。
     * 不能用 historyEpisodeTitle()：它依赖异步加载的 TMDB 元数据，元数据未到位时退化成纯集号、
     * 到位后变成正式剧集名，同一集在不同时刻会算出不同 key —— 既读不回自己的进度，
     * 退化后的裸集号还可能命中另一季已看完的槽位，把没看过的集直接送到结尾。
     */
    private String inlineEpisodeCacheKey(Episode episode) {
        if (episode == null) return "";
        EpisodePosition position = historyEpisodePosition(episode);
        int season = position.season() >= 0 ? position.season() : sourceTitleSeasonNumber();
        return EpisodeSeasonPolicy.episodePositionCacheKey(season, episode.getName());
    }

    /**
     * 季号不可靠且当前线路混排多季时，放弃集数播放位置缓存。
     * 这种线路里两季都有"第01集"，裸集名会让没看过的一集读到另一季已看完的进度、直接跳到结尾。
     * 从头开始播比跳到错误位置好。写入侧一并跳过，避免继续污染这些有歧义的槽位。
     */
    private boolean skipEpisodePositionCache() {
        if (selectedFlag == null || selectedFlag.getEpisodes() == null) return false;
        if (sourceTitleSeasonNumber() >= 0) return false;
        Set<Integer> distinct = new HashSet<>();
        for (Integer season : sourceSeasonNumbers(selectedFlag.getEpisodes())) {
            if (season != null && season >= 0) distinct.add(season);
        }
        return distinct.size() > 1;
    }


    private String currentInlineHistoryCacheKey() {
        if (history == null || selectedFlag == null) return "";
        // season 必须取自 history 自身的持久化身份，而不是当前选中状态：切季时 selectedFlag/
        // selectedSeasonNumber 已指向新季，用它们会把上一集的进度写进新季的槽位。
        int season = history.getTmdbEpisodeNumber() > 0 ? history.getTmdbSeasonNumber() : sourceTitleSeasonNumber();
        // 集名沿用源站名（与 inlineEpisodeCacheKey 一致），不用 getVodRemarks()——后者存的是
        // 刮削标题，会随 TMDB 元数据加载状态变化，导致读写 key 对不上。
        Episode episode = selectedFlag.find(history.getEpisode(), true);
        // find() 在 strict 模式下可能返回 null（换线路后集名/编号对不上），按 URL 兜底，避免静默丢进度。
        if (episode == null && !TextUtils.isEmpty(history.getEpisodeUrl())) {
            for (Episode item : selectedFlag.getEpisodes()) {
                if (TextUtils.equals(item.getUrl(), history.getEpisodeUrl())) {
                    episode = item;
                    break;
                }
            }
        }
        return episode == null ? "" : EpisodeSeasonPolicy.episodePositionCacheKey(season, episode.getName());
    }
    private int episodeNumberForHistory(Episode episode) {
        return historyEpisodePosition(episode).number();
    }

    private boolean setHistoryTmdbEpisodePosition(History item, Episode episode) {
        if (item == null || episode == null) return false;
        if (episode.getTmdbEpisode() != null) return item.setTmdbEpisodePosition(episode);
        // 历史身份可使用当前 TMDB 列表映射；展示层仍保留 TmdbEpisodeMatcher 的保守绑定规则。
        EpisodePosition position = historyEpisodePosition(episode);
        return position.number() > 0 && item.setTmdbEpisodePosition(position.season(), position.number());
    }

    private String playbackHistoryName() {
        return coalesce(matchedTmdbTitle(),
                TmdbUIAdapter.sourceAwareTitle(sourceVodName, matchedTmdbItem, matchedTmdbTitle()),
                vod == null ? "" : vod.getName(), getNameText());
    }

    private String matchedTmdbTitle() {
        return coalesce(tmdbDetailTitle(matchedTmdbItem, matchedTmdbDetail), matchedTmdbItem == null ? "" : matchedTmdbItem.getTitle());
    }

    private String playbackHistoryPic() {
        return coalesce(matchedTmdbItem == null ? "" : matchedTmdbItem.getPosterUrl(), matchedTmdbItem == null ? "" : matchedTmdbItem.getBackdropUrl(), vod == null ? "" : vod.getPic(), getPicText());
    }

    private boolean isFusionMode() {
        return getDetailMode() == Setting.DETAIL_OPEN_FUSION || getIntent().getBooleanExtra("fusion", false);
    }

    private boolean isPlayerMode() {
        return getDetailMode() == Setting.DETAIL_OPEN_PLAYER;
    }

    private boolean isCinemaMode() {
        return getIntent().getIntExtra("detail_mode", Setting.getDetailOpenMode()) == Setting.DETAIL_OPEN_CINEMA || Setting.isTmdbCinemaStyle();
    }

    private int getDetailMode() {
        // 返回原始模式，不做 normalize，否则 isPlayerMode() 永远返回 false
        if (getIntent().hasExtra("detail_mode")) return getIntent().getIntExtra("detail_mode", Setting.DETAIL_OPEN_ENHANCED);
        return getIntent().getBooleanExtra("fusion", false) ? Setting.DETAIL_OPEN_FUSION : Setting.DETAIL_OPEN_ENHANCED;
    }

    private int detailModeTitle() {
        if (isFusionMode()) return R.string.setting_detail_open_fusion;
        if (isPlayerMode()) return R.string.setting_detail_open_player;
        return R.string.setting_detail_open_enhanced;
    }

    private boolean isAutoPlayMode() {
        return getIntent().getBooleanExtra("auto_play", false);
    }

    private boolean shouldUseLoadingOnlyBeforeDefaultPlayback() {
        return isAutoPlayMode() && !isFusionMode() && !isPlayerMode();
    }

    private void setLoadingOnlyBeforeDefaultPlayback(boolean loadingOnly) {
        binding.hero.setVisibility(loadingOnly ? View.GONE : View.VISIBLE);
        binding.scroll.setVisibility(loadingOnly ? View.GONE : View.VISIBLE);
    }

    private void revealDefaultPlaybackLoadingPage() {
        if (shouldUseLoadingOnlyBeforeDefaultPlayback()) setLoadingOnlyBeforeDefaultPlayback(false);
    }

    private boolean isInlinePlayerMode() {
        return isFusionMode() || detailPlayerActive;
    }

    private boolean isCurrentInlinePlayback(Episode episode) {
        return inlineStarted
                && currentInlineResult != null
                && selectedFlag != null
                && episode != null
                && episode.equals(inlinePlaybackEpisode)
                && TextUtils.equals(getKeyText(), inlinePlaybackKey)
                && TextUtils.equals(selectedFlag.getFlag(), inlinePlaybackFlag);
    }

    private boolean enterInlineFullscreenIfCurrentInlinePlayback(Episode episode) {
        if (!isCurrentInlinePlayback(episode)) return false;
        if (!inlineFullscreen) enterInlineFullscreen();
        return true;
    }

    private void maybeAutoPlayInline() {
        if ((!isFusionMode() && !isAutoPlayMode()) || autoPlayed) return;
        autoPlayed = true;
        binding.playerPanel.post(this::onPlay);
    }

    private void playDetailFullscreen() {
        if (selectedFlag == null || selectedEpisode == null) return;
        boolean current = isCurrentInlinePlayback(selectedEpisode);
        detailPlayerActive = true;
        binding.playerError.setTextColor(0xFFFFFFFF);
        tintInlineControl(inlineControlsView());
        setPlayerCard(lightTheme ? ThemeColors.light() : ThemeColors.dark());
        ensureInlineDanmakuController();
        binding.playerPanel.setVisibility(View.VISIBLE);
        binding.playerPanelSpacer.setVisibility(View.VISIBLE); // spacer 作为焦点桥梁需要可见
        enterInlineFullscreen();
        if (!current) playInline();
    }

    private void playInline() {
        inlineHttpRefreshAttempted = false;
        playInline(C.TIME_UNSET, "", "");
    }

    @Override
    protected Vod getReaderVod() {
        // TV 内联播放路径把当前 Vod（含章节列表）交给阅读器路由
        return vod;
    }

    private void playInline(long resumePosition, String failedUrl, String failureMessage) {
        if (selectedFlag == null || selectedEpisode == null) return;
        inlinePlaybackLoading = true;
        int generation = ++inlinePlaybackGeneration;
        inlinePlayerSwitchLoading = false;
        String key = getKeyText();
        String flag = selectedFlag.getFlag();
        String episodeUrl = selectedEpisode.getUrl();
        int playerKernel = inlineHistoryPlayerKernel();
        stopInlinePlayerForReload();
        showInlineLoading();
        updateInlineDisplayPanel();
        detailTasks.submit(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episodeUrl, playerKernel);
                runOnAliveUi(() -> {
                    if (!isInlinePlaybackRequestCurrent(generation, key, flag, episodeUrl)) return;
                    String resolvedUrl = result.getUrl() == null ? "" : result.getUrl().v();
                    if (!TextUtils.isEmpty(failedUrl) && TextUtils.equals(failedUrl, resolvedUrl)) {
                        inlinePlaybackLoading = false;
                        SpiderDebug.log("webhome-inline", "http refresh rejected unchanged url key=%s id=%s", key, episodeUrl);
                        showInlineError(failureMessage);
                        return;
                    }
                    startInlinePlayer(result, resumePosition);
                });
            } catch (Throwable e) {
                String message = e.getMessage();
                runOnAliveUi(() -> {
                    if (!isInlinePlaybackRequestCurrent(generation, key, flag, episodeUrl)) return;
                    String fallback = TextUtils.isEmpty(failureMessage) ? getString(R.string.error_play_url) : failureMessage;
                    showInlineError(TextUtils.isEmpty(message) ? fallback : message);
                });
            }
        });
    }

    private void stopInlinePlayerForReload() {
        // 换源换集：作废在途归因，避免用上一集的证据弹窗并写错规则
        resetAdFeedback();
        subtitlePlaybackSession.stop(this);
        inlineStartPosition = C.TIME_UNSET;
        inlineStartPositionApplied = false;
        pendingInlineResult = null;
        currentInlineResult = null;
        inlinePlaybackEpisode = null;
        inlinePlaybackKey = "";
        inlinePlaybackFlag = "";
        introSkipPlayback.reset();
        if (service() == null || player() == null || player().isEmpty()) {
            updateInlineButtons(false);
            return;
        }
        player().stop();
        player().clear();
        updateInlineButtons(false);
    }

    private void startInlinePlayer(Result result) {
        startInlinePlayer(result, C.TIME_UNSET);
    }

    private void startInlinePlayer(Result result, long resumePosition) {
        // 决定性拦截：play_url 协议为 novel:// / pics:// / manga:// → 直启阅读器，不再喂给内联 player
        // （选集 / quality 切换 / 重连 / resume 全走此汇聚点。）
        if (NovelRouter.guardInlinePlay(this, result,
                getKeyText(),
                selectedFlag == null ? "" : selectedFlag.getFlag(),
                vod == null ? "" : vod.getId(),
                vod == null ? "" : vod.getName(),
                vod == null ? "" : vod.getPic(),
                selectedEpisode,
                selectedFlag == null ? null : selectedFlag.getEpisodes())) {
            stopInlinePlayerForReload();
            return;
        }
        currentInlineResult = result;
        useParse = result.shouldUseParse();
        if (result.hasPosition() && history != null) history.setPosition(result.getPosition());
        if (result.hasDesc() && !hasTmdbOverview()) {
            vod.setContent(result.getDesc());
            bindOverview();
        }
        if (service() == null || controller() == null) {
            pendingInlineResult = result;
            return;
        }
        inlinePlaybackLoading = false;
        inlineStarted = true;
        inlineFirstReady = false;  // 重置标志,允许新播放首次 READY 时显示控制栏
        inlineButtonsReordered = false;  // 重置标志,允许新播放重新排序按钮
        inlinePlaybackEpisode = selectedEpisode;
        inlinePlaybackKey = getKeyText();
        inlinePlaybackFlag = selectedFlag == null ? "" : selectedFlag.getFlag();
        pendingInlineResult = null;
        hideInlineControls();
        resetInlineShortDramaMode();
        updateInlineButtons(false);
        player().stop();
        player().clear();
        if (resumePosition == C.TIME_UNSET) resetInlineHistoryIfNearEnding();
        inlineStartPosition = resumePosition == C.TIME_UNSET ? getInlineResumePosition() : Math.max(0, resumePosition);
        inlineStartPositionApplied = false;
        player().preparePlayer(inlineHistoryPlayerKernel());
        setInlineSpeed(getInlinePlaybackSpeed());
        updateInlineButtons(false);
        Site site = getCurrentSite();
        ensureInlineDanmakuController();
        startPlayer(getHistoryKey(), result, useParse, site == null ? 0 : site.getTimeout(), buildMetadata());
        updateNavigationKey();
        subtitlePlaybackSession.onPlaybackStarted(this, result);
        searchInlineDanmaku(result);
        binding.playerPanel.requestFocus();
    }

    private void searchInlineDanmaku(Result result) {
        if (!DanmakuApi.canSearch() || history == null || selectedEpisode == null) return;
        DanmakuApi.search(MediaTitleRequest.builder()
                .siteKey(getKeyText())
                .vodId(getIdText())
                .rawTitle(playbackHistoryName())
                .rawRemarks(history.getVodRemarks())
                .episodeName(historyEpisodeTitle(selectedEpisode))
                .source(MediaTitleLearningExample.SOURCE_DANMAKU_AUTO)
                .allowAi(true)
                .build(), danmaku -> applyInlineDanmaku(result, danmaku));
    }

    private void applyInlineDanmaku(Result result, Danmaku danmaku) {
        if (isFinishing() || isDestroyed() || service() == null || player() == null || !inlineStarted || result != currentInlineResult || !isOwner()) return;
        if (DanmakuSetting.isSpiderFirst() && !result.getDanmaku().isEmpty()) player().addDanmaku(danmaku);
        else player().setDanmaku(danmaku);
        refreshInlineDanmakuButtons();
    }

    private void refreshInlineDanmakuButtons() {
        if (!isInlinePlayerMode() || service() == null || player() == null || player().isEmpty()) return;
        updateInlineButtons(player().isPlaying());
    }

    private void showInlineError(String text) {
        inlinePlaybackLoading = false;
        hideInlineLoading();
        binding.playerError.setText(text);
        binding.playerError.setVisibility(View.VISIBLE);
        updateInlineDisplayPanel();
    }

    private void showInlineLoading() {
        binding.playerError.setVisibility(View.GONE);
        binding.playerProgress.setVisibility(View.VISIBLE);
        tintInlineLoading();
        updateInlineLoadingTraffic();
        hideInlineControls();
    }

    private void hideInlineLoading() {
        binding.playerProgress.setVisibility(View.GONE);
        binding.playerProgressTraffic.setVisibility(View.GONE);
        Traffic.reset(binding.playerProgressTraffic);
    }

    private void updateInlineLoadingTraffic() {
        if (binding == null || binding.playerProgress.getVisibility() != View.VISIBLE) return;
        Traffic.setSpeed(binding.playerProgressTraffic, service() == null ? null : player());
        tintInlineLoading();
    }

    private void toggleInlinePlayback() {
        if (!isInlinePlayerMode()) return;
        if (inlinePlaybackReconnectPending) return;
        if (controller() == null || service() == null || player().isEmpty()) {
            if (isSamePendingInlinePlayback(selectedEpisode)) return;
            onPlay();
            return;
        }
        if (player().isPlaying()) controller().pause();
        else controller().play();
        if (Util.isLeanback() && inlineFullscreen) hideInlineControls();
        else setInlineHideCallback();
    }

    private void toggleInlineControls() {
        if (!isInlinePlayerMode()) return;
        if (inlineControlsView().getVisibility() == View.VISIBLE) hideInlineControls();
        else showInlineControls(true, false);
    }

    private void showInlineControls(boolean show) {
        showInlineControls(show, true);
    }

    private void showInlineControls(boolean show, boolean focus) {
        if (!isInlinePlayerMode()) return;
        if (!show) {
            hideInlineControls();
            return;
        }
        updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
        inlineControlsView().setVisibility(View.VISIBLE);
        if (inlineOsd != null) {
            inlineOsd.setSuppressed(Util.isMobile());
            inlineOsd.setControlsVisible(true);
        }
        focusInlineDefaultControl();
        touchInlineControls();
        updateInlineDisplayPanel();
    }

    private void hideInlineControls() {
        if (binding == null) return;
        boolean hadControlFocus = hasFocusedChild(inlineControlsView());
        inlineControlsView().setVisibility(View.GONE);
        if (inlineOsd != null) {
            inlineOsd.setControlsVisible(false);
            inlineOsd.setSuppressed(false);
        }
        hideMobileFusionPlayerActionDock();
        App.removeCallbacks(inlineHideControls);
        if (hadControlFocus) binding.playerPanel.requestFocus();
        updateInlineDisplayPanel();
    }

    private void prepareInlinePlayerTransition() {
        if (binding == null) return;
        inlinePauseInfo = false;
        App.removeCallbacks(inlineHideControls);
        inlineControlsView().setVisibility(View.GONE);
        hideMobileFusionPlayerActionDock();
        hideInlineGestureViews();
        hideInlineDisplayPanel();
        View focus = getCurrentFocus();
        if (focus != null && isDescendant(binding.playerPanel, focus)) focus.clearFocus();
        binding.playerPanel.requestFocus();
    }

    private void hideInlineDisplayPanel() {
        // 已废弃：统一使用 PlayerOsdController（OSD）系统
    }

    private boolean suppressInlineDisplayForLeanbackFusionExit() {
        if (!Util.isLeanback() || !isFusionMode()) return false;
        inlineDisplaySuppressUntil = Math.max(inlineDisplaySuppressUntil, SystemClock.uptimeMillis() + LEANBACK_FUSION_EXIT_DISPLAY_SUPPRESS_MS);
        hideInlineDisplayPanel();
        return true;
    }

    private boolean isInlineDisplaySuppressed() {
        return SystemClock.uptimeMillis() < inlineDisplaySuppressUntil;
    }

    private long inlineControlsHideDelayMs() {
        return Util.isMobile() ? Constant.INTERVAL_HIDE : INLINE_CONTROLS_HIDE_DELAY_MS;
    }

    private void hideInlineControlsIfIdle() {
        long idle = SystemClock.uptimeMillis() - lastInlineControlInteraction;
        long delay = inlineControlsHideDelayMs();
        if (idle < delay) {
            App.post(inlineHideControls, delay - idle);
            return;
        }
        hideInlineControls();
    }

    private void setInlineHideCallback() {
        touchInlineControls();
    }

    private void touchInlineControls() {
        lastInlineControlInteraction = SystemClock.uptimeMillis();
        App.removeCallbacks(inlineHideControls);
        App.post(inlineHideControls, inlineControlsHideDelayMs());
    }

    private void focusInlineDefaultControl() {
        inlineControlsView().post(() -> {
            if (isInlineControlsVisible()) PlayerControlFocusHelper.ensureFocus(inlineControlsView(), getInlineControlFocus());
        });
    }

    private View getInlineControlFocus() {
        if (Util.isMobile()) {
            if (inlineControlFocus != null && isVisibleInHierarchy(inlineControlFocus) && inlineControlFocus.isEnabled()) return inlineControlFocus;
            return detailControlView(R.id.play, View.class);
        }
        // TV模式：按顺序查找第一个可见且启用的按钮
        if (inlineControlFocus != null && isVisibleInHierarchy(inlineControlFocus) && inlineControlFocus.isEnabled()) return inlineControlFocus;
        View[] candidates = {
            binding.playerNext, binding.playerPrev, binding.playerEpisodes,
            binding.playerRefresh, binding.playerChangeSource, binding.playerFullscreenAction
        };
        for (View candidate : candidates) {
            if (candidate != null && isVisibleInHierarchy(candidate) && candidate.isEnabled()) {
                return candidate;
            }
        }
        return binding.playerNext;
    }

    private void rememberInlineControlFocus() {
        View focus = getCurrentFocus();
        if (focus != null && isDescendant((ViewGroup) inlineControlsView(), focus)) inlineControlFocus = focus;
    }

    private boolean isVisibleInHierarchy(View view) {
        for (View current = view; current != null; current = current.getParent() instanceof View parent ? parent : null) {
            if (current.getVisibility() != View.VISIBLE) return false;
        }
        return true;
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
        return binding != null && inlineControlsView().getVisibility() == View.VISIBLE;
    }

    private void updateInlineButtons(boolean playing) {
        if (!isInlinePlayerMode() || inlineControlController == null) {
            setInlineDecodeText(getString(R.string.play_decode_idle));
            return;
        }
        boolean hasPlayer = service() != null && !player().isEmpty();
        setInlineSpeedText(service() == null || player().isEmpty() ? getString(R.string.play_speed) : player().getSpeedText());
        setInlineDecodeText(inlineDecodeText(hasPlayer));
        binding.playerExternal.setText(service() == null ? getString(R.string.play_exo) : player().getPlayerText());
        binding.playerScale.setText(scaleLabel());
        binding.playerQuality.setText(qualityLabel());
        binding.playerParse.setText(parseLabel());
        binding.playerOpening.setText(inlineOpeningLabel());
        binding.playerEnding.setText(inlineEndingLabel());
        int episodeCount = selectedFlag == null || selectedFlag.getEpisodes() == null ? 0 : selectedFlag.getEpisodes().size();
        boolean hasTitle = hasPlayer && player().haveTitle();
        boolean inlineAdFeedback = hasPlayer && isInlineAdFeedbackEnabled();
        // 上集/下集按钮始终可用，点击时如果没有相邻集数会显示提示（与影视原生模式保持一致）
        setButtonEnabled(binding.playerPrev, hasPlayer && episodeCount > 0);
        setButtonEnabled(binding.playerNext, hasPlayer && episodeCount > 0);
        boolean inlineQuality = canChangeInlineQuality();
        setButtonEnabled(binding.playerQuality, inlineQuality);
        setButtonEnabled(binding.playerParse, useParse && !VodConfig.get().getParses().isEmpty());
        setButtonEnabled(binding.playerSpeed, hasPlayer);
        setButtonEnabled(binding.playerScale, hasPlayer);
        setButtonEnabled(binding.playerRefresh, hasPlayer);
        setButtonEnabled(binding.playerChangeSource, vod != null);
        setButtonEnabled(binding.playerSearch, vod != null);
        setButtonEnabled(binding.playerRepeat, hasPlayer);
        setButtonEnabled(binding.playerDisplay, hasPlayer);
        setButtonEnabled(binding.playerDecode, hasPlayer);
        setButtonEnabled(binding.playerPlayParams, hasPlayer);
        setButtonEnabled(binding.playerLut, hasPlayer);
        setButtonEnabled(binding.playerTextTrack, hasPlayer);
        setButtonEnabled(binding.playerAudioTrack, hasPlayer);
        setButtonEnabled(binding.playerVideoTrack, hasPlayer);
        setButtonEnabled(binding.playerOpening, hasPlayer);
        setButtonEnabled(binding.playerEnding, hasPlayer);
        setButtonEnabled(binding.playerDanmaku, hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(binding.playerAdFeedback, inlineAdFeedback);
        setButtonEnabled(binding.playerDanmakuToggle, hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(binding.playerExternal, hasPlayer);
        setButtonEnabled(binding.playerChapter, hasTitle);
        setButtonEnabled(binding.playerEpisodes, episodeCount > 0);
        setButtonEnabled(binding.playerCast, hasPlayer && hasInlineCast() && PlayerButtonSetting.isVisible(PlayerButtonSetting.CAST));
        setButtonEnabled(binding.playerInfo, false); // 始终禁用信息按钮
        setButtonEnabled(binding.playerFullscreenAction, true);
        binding.playerCast.setVisibility(hasInlineCast() && PlayerButtonSetting.isVisible(PlayerButtonSetting.CAST) ? View.VISIBLE : View.GONE);
        binding.playerInfo.setVisibility(View.GONE); // 始终隐藏信息按钮
        binding.playerActionRow.setVisibility(View.VISIBLE);
        binding.playerDanmakuToggle.setVisibility(View.GONE);
        binding.playerEpisodes.setVisibility(episodeCount < 2 ? View.GONE : View.VISIBLE);
        binding.playerQuality.setVisibility(inlineQuality ? View.VISIBLE : View.GONE);
        binding.playerVideoTrack.setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        binding.playerParse.setVisibility(useParse && !VodConfig.get().getParses().isEmpty() ? View.VISIBLE : View.GONE);
        binding.playerDanmaku.setVisibility(hasPlayer && inlineControlController.hasDanmakuControl() ? View.VISIBLE : View.GONE);
        binding.playerAdFeedback.setVisibility(inlineAdFeedback ? View.VISIBLE : View.GONE);
        binding.playerChapter.setVisibility(View.GONE); // 始终隐藏信息按钮
        binding.playerRepeat.setSelected(hasPlayer && player().isRepeatOne());
        binding.playerPlayParams.setSelected(hasPlayer && inlineOsd != null && inlineOsd.isDiagnosticsVisible());
        binding.playerMultiThreadProxy.setSelected(MultiThreadProxySetting.get().enabled());
        setInlineFullscreenIcon();
        updateMobileInlineButtons(playing, hasPlayer, episodeCount, hasTitle);
        applyInlinePlayerButtonSettings();
        updateInlineDisplayPanel();
        // 更新按钮颜色
        updateInlineButtonColors();
    }

    private void updateInlineButtonColors() {
        // 确保所有按钮的文字颜色正确
        int white = 0xFFFFFFFF;
        int yellow = 0xFFFFD700;

        // 播放参数按钮：选中时黄色，否则白色
        boolean playParamsSelected = binding.playerPlayParams.isSelected();
        binding.playerPlayParams.setTextColor(playParamsSelected ? yellow : white);
        binding.playerMultiThreadProxy.setTextColor(binding.playerMultiThreadProxy.isSelected() ? yellow : white);

        // 弹幕按钮：根据弹幕启用状态设置颜色
        boolean danmakuShow = DanmakuSetting.isShow();
        binding.playerDanmaku.setTextColor(danmakuShow ? yellow : white);

        // 其他所有按钮：白色
        binding.playerNext.setTextColor(white);
        binding.playerPrev.setTextColor(white);
        binding.playerEpisodes.setTextColor(white);
        binding.playerRefresh.setTextColor(white);
        binding.playerChangeSource.setTextColor(white);
        binding.playerSearch.setTextColor(white);
        binding.playerFullscreenAction.setTextColor(white);
        binding.playerExternal.setTextColor(white);
        binding.playerDecode.setTextColor(white);
        binding.playerCodecCapability.setTextColor(white);
        binding.playerSpeed.setTextColor(white);
        binding.playerScale.setTextColor(white);
        binding.playerLut.setTextColor(white);
        binding.playerRepeat.setTextColor(white);
        binding.playerDisplay.setTextColor(white);
        binding.playerQuality.setTextColor(white);
        binding.playerParse.setTextColor(white);
        binding.playerTextTrack.setTextColor(white);
        binding.playerAudioTrack.setTextColor(white);
        binding.playerVideoTrack.setTextColor(white);
        binding.playerOpening.setTextColor(white);
        binding.playerEnding.setTextColor(white);
        binding.playerAdFeedback.setTextColor(white);
        // playerDanmakuToggle 是 ImageView，不设置 textColor
    }

    private void applyInlinePlayerButtonSettings() {
        // 排序(removeView/addView)只在首次执行,避免播放中反复重排打乱焦点导航导致左右跳按钮;
        // 隐藏偏好(applyVisibility)每次都应用,防止后续 updateMobileInlineButtons 按播放状态把用户隐藏的按钮重新显示。
        // 与原生增强模式 applyActionButtonSettings(只排一次) / applyActionButtonVisibility(每帧) 的分离逻辑一致。
        Map<String, View> inlineButtons = inlinePlayerButtonMap();
        Map<String, View> mobileButtons = Util.isMobile() && detailActionRoot != null ? mobileInlinePlayerButtonMap() : null;
        if (inlineButtonsReordered) {
            PlayerButtonSetting.applyVisibility(inlineButtons);
            if (mobileButtons != null) PlayerButtonSetting.applyVisibility(mobileButtons);
            return;
        }
        PlayerButtonSetting.applyOrder((ViewGroup) binding.playerActionRow.getChildAt(0), inlineButtons);
        if (mobileButtons != null) PlayerButtonSetting.applyOrder(detailActionRoot.findViewById(R.id.container), mobileButtons);
        inlineButtonsReordered = true;
    }

    private Map<String, View> inlinePlayerButtonMap() {
        Map<String, View> buttons = new LinkedHashMap<>();
        buttons.put(PlayerButtonSetting.NEXT, binding.playerNext);
        buttons.put(PlayerButtonSetting.PREV, binding.playerPrev);
        buttons.put(PlayerButtonSetting.EPISODES, binding.playerEpisodes);
        buttons.put(PlayerButtonSetting.RESET, binding.playerRefresh);
        buttons.put(PlayerButtonSetting.CHANGE, binding.playerChangeSource);
        buttons.put(PlayerButtonSetting.SEARCH, binding.playerSearch);
        buttons.put(PlayerButtonSetting.FULLSCREEN, binding.playerFullscreenAction);
        buttons.put(PlayerButtonSetting.PLAYER, binding.playerExternal);
        buttons.put(PlayerButtonSetting.DECODE, binding.playerDecode);
        buttons.put(PlayerButtonSetting.PLAY_PARAMS, binding.playerPlayParams);
        buttons.put(PlayerButtonSetting.MULTI_THREAD_PROXY, binding.playerMultiThreadProxy);
        buttons.put(PlayerButtonSetting.CODEC_CAPABILITY, binding.playerCodecCapability);
        buttons.put(PlayerButtonSetting.SPEED, binding.playerSpeed);
        buttons.put(PlayerButtonSetting.SCALE, binding.playerScale);
        buttons.put(PlayerButtonSetting.QUALITY, binding.playerQuality);
        buttons.put(PlayerButtonSetting.LUT, binding.playerLut);
        buttons.put(PlayerButtonSetting.PARSE, binding.playerParse);
        buttons.put(PlayerButtonSetting.TEXT, binding.playerTextTrack);
        buttons.put(PlayerButtonSetting.AUDIO, binding.playerAudioTrack);
        buttons.put(PlayerButtonSetting.VIDEO, binding.playerVideoTrack);
        buttons.put(PlayerButtonSetting.OPENING, binding.playerOpening);
        buttons.put(PlayerButtonSetting.ENDING, binding.playerEnding);
        buttons.put(PlayerButtonSetting.DANMAKU, binding.playerDanmaku);
        buttons.put(PlayerButtonSetting.AD_FEEDBACK, binding.playerAdFeedback);
        buttons.put(PlayerButtonSetting.TITLE, binding.playerChapter);
        buttons.put(PlayerButtonSetting.DISPLAY, binding.playerDisplay);
        buttons.put(PlayerButtonSetting.REPEAT, binding.playerRepeat);
        return buttons;
    }

    private Map<String, View> mobileInlinePlayerButtonMap() {
        Map<String, View> buttons = new LinkedHashMap<>();
        buttons.put(PlayerButtonSetting.CHANGE, detailActionView(R.id.change2, View.class));
        buttons.put(PlayerButtonSetting.SEARCH, detailActionView(R.id.search, View.class));
        buttons.put(PlayerButtonSetting.FULLSCREEN, detailActionView(R.id.actionFullscreen, View.class));
        buttons.put(PlayerButtonSetting.PLAYER, detailActionView(R.id.player, View.class));
        buttons.put(PlayerButtonSetting.DECODE, detailActionView(R.id.decode, View.class));
        buttons.put(PlayerButtonSetting.PLAY_PARAMS, detailActionView(R.id.playParams, View.class));
        buttons.put(PlayerButtonSetting.MULTI_THREAD_PROXY, detailActionView(R.id.multiThreadProxy, View.class));
        buttons.put(PlayerButtonSetting.CODEC_CAPABILITY, detailActionView(R.id.codecCapability, View.class));
        buttons.put(PlayerButtonSetting.LUT, detailActionView(R.id.lut, View.class));
        buttons.put(PlayerButtonSetting.SPEED, detailActionView(R.id.speed, View.class));
        buttons.put(PlayerButtonSetting.SCALE, detailActionView(R.id.scale, View.class));
        buttons.put(PlayerButtonSetting.QUALITY, detailActionView(R.id.actionQuality, View.class));
        buttons.put(PlayerButtonSetting.RESET, detailActionView(R.id.reset, View.class));
        buttons.put(PlayerButtonSetting.REPEAT, detailActionView(R.id.repeat, View.class));
        buttons.put(PlayerButtonSetting.TEXT, detailActionView(R.id.text, View.class));
        buttons.put(PlayerButtonSetting.AUDIO, detailActionView(R.id.audio, View.class));
        buttons.put(PlayerButtonSetting.VIDEO, detailActionView(R.id.video, View.class));
        buttons.put(PlayerButtonSetting.OPENING, detailActionView(R.id.opening, View.class));
        buttons.put(PlayerButtonSetting.ENDING, detailActionView(R.id.ending, View.class));
        buttons.put(PlayerButtonSetting.DANMAKU, detailActionView(R.id.danmaku, View.class));
        buttons.put(PlayerButtonSetting.AD_FEEDBACK, detailActionView(R.id.adFeedback, View.class));
        buttons.put(PlayerButtonSetting.TITLE, detailActionView(R.id.chapter, View.class));
        buttons.put(PlayerButtonSetting.EPISODES, detailActionView(R.id.episodes, View.class));
        return buttons;
    }

    private void updateMobileInlineButtons(boolean playing, boolean hasPlayer, int episodeCount, boolean hasTitle) {
        if (!Util.isMobile()) return;
        updateMobileInlineSideControlMargins();
        boolean locked = isLock();
        TextView title = detailControlView(R.id.title, TextView.class);
        TextView size = detailControlView(R.id.size, TextView.class);
        View action = detailActionRoot;
        CharSequence titleText = inlineTitleText();
        title.setText(titleText);
        title.setVisibility(!locked && hasPlayer && !TextUtils.isEmpty(titleText) ? View.VISIBLE : View.INVISIBLE);
        // 控制栏显示时显示分辨率
        String sizeText = hasPlayer && player() != null ? player().getSizeText() : "";
        size.setText(sizeText);
        size.setVisibility(!locked && hasPlayer && !TextUtils.isEmpty(sizeText) ? View.VISIBLE : View.GONE);
        updateMobileInlineControlStatus(hasPlayer);
        detailControlView(R.id.play, ImageView.class).setImageResource(playing ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        detailControlView(R.id.lock, ImageView.class).setImageResource(locked ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
        detailActionView(R.id.speed, TextView.class).setText(binding.playerSpeed.getText());
        detailActionView(R.id.player, TextView.class).setText(binding.playerExternal.getText());
        detailActionView(R.id.decode, TextView.class).setText(binding.playerDecode.getText());
        detailActionView(R.id.scale, TextView.class).setText(binding.playerScale.getText());
        detailActionView(R.id.actionQuality, TextView.class).setText(binding.playerQuality.getText());
        detailActionView(R.id.opening, TextView.class).setText(binding.playerOpening.getText());
        detailActionView(R.id.ending, TextView.class).setText(binding.playerEnding.getText());
        // 上集/下集按钮始终可用，点击时如果没有相邻集数会显示提示（与影视原生模式保持一致）
        setButtonEnabled(detailControlView(R.id.prev, View.class), hasPlayer && episodeCount > 0);
        setButtonEnabled(detailControlView(R.id.next, View.class), hasPlayer && episodeCount > 0);
        setButtonEnabled(detailControlView(R.id.fullscreen, View.class), true);
        setButtonEnabled(detailControlView(R.id.danmaku, View.class), hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(detailControlView(R.id.lock, View.class), hasPlayer);
        setButtonEnabled(detailControlView(R.id.rotate, View.class), hasPlayer);
        setButtonEnabled(detailControlView(R.id.pip, View.class), canShowInlinePiP(hasPlayer, locked));
        setButtonEnabled(detailActionView(R.id.player, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.decode, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.speed, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.scale, View.class), hasPlayer);
        boolean inlineQuality = canChangeInlineQuality();
        boolean inlineAdFeedback = hasPlayer && isInlineAdFeedbackEnabled();
        setButtonEnabled(detailActionView(R.id.actionQuality, View.class), inlineQuality);
        setButtonEnabled(detailActionView(R.id.adFeedback, View.class), inlineAdFeedback);
        setButtonEnabled(detailActionView(R.id.reset, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.repeat, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.text, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.audio, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.video, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.opening, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.ending, View.class), hasPlayer);
        setButtonEnabled(detailActionView(R.id.danmaku, View.class), hasPlayer && inlineControlController.hasDanmakuControl());
        setButtonEnabled(detailActionView(R.id.chapter, View.class), hasTitle);
        setButtonEnabled(detailActionView(R.id.episodes, View.class), selectedFlag != null && selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty());
        detailControlView(R.id.top, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.center, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.bottom, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.back, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        // 进度条旁的全屏按钮只根据锁定状态显示，不受 PlayerButtonSetting 影响。
        detailControlView(R.id.fullscreen, View.class).setVisibility(locked ? View.GONE : View.VISIBLE);
        detailControlView(R.id.lock, View.class).setVisibility(inlineFullscreen ? View.VISIBLE : View.GONE);
        detailControlView(R.id.rotate, View.class).setVisibility(inlineFullscreen && !locked && !inlineShortDramaMode ? View.VISIBLE : View.GONE);
        detailControlView(R.id.pip, View.class).setVisibility(canShowInlinePiP(hasPlayer, locked) ? View.VISIBLE : View.GONE);
        // 中间悬浮的上集/下集按钮只根据集数显示（需至少 2 集），不受 PlayerButtonSetting 影响。
        detailControlView(R.id.prev, View.class).setVisibility(!locked && hasPlayer && episodeCount >= 2 ? View.VISIBLE : View.GONE);
        detailControlView(R.id.next, View.class).setVisibility(!locked && hasPlayer && episodeCount >= 2 ? View.VISIBLE : View.GONE);
        // 顶部投屏按钮只根据功能可用性显示，不受 PlayerButtonSetting 影响。
        detailControlView(R.id.cast, View.class).setVisibility(!locked && hasInlineCast() ? View.VISIBLE : View.GONE);
        detailControlView(R.id.info, View.class).setVisibility(!locked && hasInlineInfo() ? View.VISIBLE : View.GONE);
        detailControlView(R.id.setting, View.class).setVisibility(!locked && hasPlayer ? View.VISIBLE : View.GONE);
        // 顶部弹幕按钮只根据功能可用性显示，不受 PlayerButtonSetting 影响。
        detailControlView(R.id.danmaku, View.class).setVisibility(!locked && hasPlayer && inlineControlController.hasDanmakuControl() ? View.VISIBLE : View.GONE);
        detailControlView(R.id.parse, RecyclerView.class).setVisibility(!locked && inlineFullscreen && useParse && !VodConfig.get().getParses().isEmpty() && PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE) ? View.VISIBLE : View.GONE);
        if (inlineParseAdapter != null) inlineParseAdapter.notifyDataSetChanged();
        detailActionView(R.id.player, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.decode, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.speed, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.scale, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.reset, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.repeat, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.episodes, View.class).setVisibility(selectedFlag != null && selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty() ? View.VISIBLE : View.GONE);
        detailActionView(R.id.text, View.class).setVisibility(hasPlayer && (player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod()) ? View.VISIBLE : View.GONE);
        detailActionView(R.id.audio, View.class).setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        detailActionView(R.id.video, View.class).setVisibility(hasPlayer && player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        detailActionView(R.id.opening, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.ending, View.class).setVisibility(hasPlayer ? View.VISIBLE : View.GONE);
        detailActionView(R.id.danmaku, View.class).setVisibility(hasPlayer && inlineControlController.hasDanmakuControl() ? View.VISIBLE : View.GONE);
        detailActionView(R.id.adFeedback, View.class).setVisibility(inlineAdFeedback ? View.VISIBLE : View.GONE);
        detailActionView(R.id.chapter, View.class).setVisibility(hasTitle ? View.VISIBLE : View.GONE);
        detailActionView(R.id.actionQuality, View.class).setVisibility(inlineQuality ? View.VISIBLE : View.GONE);
        detailActionView(R.id.repeat, View.class).setSelected(hasPlayer && player().isRepeatOne());
        detailActionView(R.id.multiThreadProxy, View.class).setSelected(MultiThreadProxySetting.get().enabled());
        hideMobileFusionPlayerActionDock();
        action.setVisibility(inlineFullscreen && !locked ? View.VISIBLE : View.GONE);
        inlineControlController.updateDanmakuState();
    }

    private void hideMobileFusionPlayerActionDock() {
        if (!Util.isMobile() || detailActionRoot == null || binding.mobileFusionPlayerActionDock == null) return;
        restoreMobileInlinePlayerAction();
        binding.mobileFusionPlayerActionDock.setVisibility(View.GONE);
    }

    private void restoreMobileInlinePlayerAction() {
        if (detailActionRoot == null || detailActionParent == null || detailActionRoot.getParent() == detailActionParent) return;
        if (detailActionRoot.getParent() instanceof ViewGroup parent) parent.removeView(detailActionRoot);
        int index = Math.min(Math.max(detailActionIndex, 0), detailActionParent.getChildCount());
        detailActionParent.addView(detailActionRoot, index, detailActionLayoutParams);
    }

    private CharSequence inlineDecodeText(boolean hasPlayer) {
        return hasPlayer ? player().getDecodeText() : getString(R.string.play_decode_idle);
    }

    private void setInlineDecodeText(CharSequence text) {
        binding.playerDecode.setText(text);
        if (Util.isMobile() && detailActionRoot != null) detailActionView(R.id.decode, TextView.class).setText(text);
    }

    private void updateMobileInlineControlStatus(boolean hasPlayer) {
        if (!Util.isMobile()) return;
        View batteryInfo = detailControlView(R.id.batteryInfo, View.class);
        boolean showBattery = hasPlayer && inlineFullscreen && !isLock();
        batteryInfo.setVisibility(showBattery ? View.VISIBLE : View.GONE);
        if (!showBattery) return;
        updateMobileInlineControlTime();
        updateMobileInlineBatteryIcon();
    }

    private void updateMobileInlineControlTime() {
        if (!Util.isMobile() || detailControlRoot == null) return;
        detailControlView(R.id.time, TextView.class).setText(LocalDateTime.now().format(Formatters.TIME));
    }

    private void updateMobileInlineBatteryIcon() {
        int level = BatteryUtil.getLevel(this);
        ImageView battery = detailControlView(R.id.battery, ImageView.class);
        if (level < 0) {
            battery.setVisibility(View.GONE);
            return;
        }
        battery.setVisibility(View.VISIBLE);
        battery.setImageResource(BatteryUtil.getIcon(level));
    }

    private void showInlineControlDialog() {
        try {
            Class<?> dialogClass = Class.forName("com.fongmi.android.tv.ui.dialog.ControlDialog");
            Object dialog = dialogClass.getMethod("create").invoke(null);
            dialog = dialogClass.getMethod("inline", TmdbDetailActivity.class).invoke(dialog, this);
            dialogClass.getMethod("show", androidx.fragment.app.FragmentActivity.class).invoke(dialog, this);
        } catch (Throwable ignored) {
            showInlineDisplay();
        }
    }

    public TextView inlineControlDialogAction(int id) {
        return detailActionRoot == null ? null : detailActionRoot.findViewById(id);
    }

    public View inlineControlDialogControl(int id) {
        return detailControlRoot == null ? null : detailControlRoot.findViewById(id);
    }

    public TextView inlineControlDialogLutView() {
        return binding.playerLut;
    }

    public PlayerManager inlineControlDialogPlayer() {
        return player();
    }

    public History inlineControlDialogHistory() {
        return history;
    }

    public boolean inlineControlDialogUseParse() {
        return useParse;
    }

    public void inlineControlDialogScale(int scale) {
        setInlineScale(scale);
    }

    public void inlineControlDialogParse(Parse item) {
        changeInlineParse(item);
    }

    public void inlineControlDialogLut() {
        onInlineLut();
    }

    public void inlineControlDialogTrack(int type) {
        View view = type == C.TRACK_TYPE_TEXT ? binding.playerTextTrack : type == C.TRACK_TYPE_AUDIO ? binding.playerAudioTrack : binding.playerVideoTrack;
        showInlineTrack(view);
    }

    public void inlineControlDialogTitle() {
        showInlineTitle();
    }

    public void inlineControlDialogDanmaku() {
        showInlineDanmaku();
    }

    public void inlineControlDialogDisplayChanged() {
        if (inlineOsd != null) {
            inlineOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
            inlineOsd.start();
        }
        updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
    }

    private void showInlineDisplay() {
        DisplayDialog.showPlayerOsd(this, () -> {
            inlineControlDialogDisplayChanged();
        });
    }

    private void updateInlineDisplayPanel() {
        // 已废弃：统一使用 PlayerOsdController（OSD）系统
        // 原逻辑已迁移到 PlayerOsdController.render()
        // 保留此方法避免删除所有调用点时遗漏
    }

    private void setButtonEnabled(View button, boolean enabled) {
        button.setEnabled(enabled);
        button.setFocusable(enabled);
        button.setAlpha(enabled ? 1f : 0.36f);
    }

    private boolean canShowInlinePiP(boolean hasPlayer, boolean locked) {
        return hasPlayer && !locked && !inlineFullscreen && player().haveTrack(C.TRACK_TYPE_VIDEO) && !PiP.noPiP();
    }

    private void setInlineFullscreenIcon() {
        binding.playerFullscreenAction.setText(inlineFullscreen ? R.string.play_exit_fullscreen : R.string.play_fullscreen);
        if (Util.isMobile()) detailControlView(R.id.fullscreen, ImageView.class).setImageResource(inlineFullscreen ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
    }

    private void setInlineDanmakuIcon(boolean show) {
        binding.playerDanmakuToggle.setImageResource(show ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
        if (Util.isMobile()) detailControlView(R.id.danmaku, ImageView.class).setImageResource(show ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private CharSequence inlineTitleText() {
        return getInlineOsdTitle();
    }

    protected boolean hasInlineCast() {
        // TV 设备不需要投屏功能（投屏是手机投给 TV 的，TV 本身就是播放设备）
        return !Util.isLeanback() && hasInlineMedia() && hasClass("com.fongmi.android.tv.ui.dialog.CastDialog") && hasClass("com.fongmi.android.tv.bean.CastVideo");
    }

    protected boolean hasInlineInfo() {
        return hasInlineMedia();
    }

    protected void onInlineCast() {
        if (!hasInlineCast()) return;
        try {
            Class<?> videoClass = Class.forName("com.fongmi.android.tv.bean.CastVideo");
            Object video = videoClass.getConstructor(String.class, String.class, long.class, Map.class).newInstance(inlineTitleText().toString(), player().getUrl(), player().getPosition(), player().getHeaders());
            Class<?> dialogClass = Class.forName("com.fongmi.android.tv.ui.dialog.CastDialog");
            Object dialog = dialogClass.getMethod("create").invoke(null);
            if (history != null) dialogClass.getMethod("history", History.class).invoke(dialog, history);
            dialog = dialogClass.getMethod("video", videoClass).invoke(dialog, video);
            dialog = dialogClass.getMethod("fm", boolean.class).invoke(dialog, true);
            dialogClass.getMethod("show", androidx.fragment.app.FragmentActivity.class).invoke(dialog, this);
        } catch (Throwable e) {
            Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_url) : e.getMessage());
        }
    }

    protected void onInlineInfo() {
        if (!hasInlineInfo()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(inlineTitleText())
                .setMessage(buildInlineInfoText())
                .setPositiveButton(R.string.dialog_positive, null)
                .show();
    }

    private boolean hasInlineMedia() {
        return service() != null && player() != null && !player().isEmpty() && !TextUtils.isEmpty(player().getUrl());
    }

    private boolean hasClass(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String buildInlineInfoText() {
        StringBuilder builder = new StringBuilder();
        appendInlineInfo(builder, "URL", trimDataUrl(player().getUrl()));
        appendInlineInfo(builder, "Headers", buildInlineHeaders(player().getHeaders()));
        appendInlineInfo(builder, "Params", player().getVideoParamsText());
        return builder.length() == 0 ? "-" : builder.toString();
    }

    private String trimDataUrl(String url) {
        if (TextUtils.isEmpty(url)) return "";
        return url.startsWith("data") ? url.substring(0, Math.min(url.length(), 128)).concat("...") : url;
    }

    private String buildInlineHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        for (String key : headers.keySet()) builder.append(key).append(" : ").append(headers.get(key)).append("\n");
        return Util.substring(builder.toString());
    }

    private void appendInlineInfo(StringBuilder builder, String label, String value) {
        if (TextUtils.isEmpty(value)) return;
        if (builder.length() > 0) builder.append("\n\n");
        builder.append(label).append("\n").append(value);
    }

    protected boolean showInlinePlayerInfo() {
        return false;
    }

    protected CharSequence getInlinePlayerTitle() {
        return getInlineOsdTitle();
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

    private String qualityLabel() {
        if (currentInlineResult == null || !currentInlineResult.getUrl().isMulti()) return getString(R.string.detail_quality);
        int position = currentInlineResult.getUrl().getPosition();
        String name = inlineQualityName(position);
        return TextUtils.isEmpty(name) ? getString(R.string.detail_quality) : getString(R.string.detail_quality) + " " + name;
    }

    private boolean canChangeInlineQuality() {
        return hasInlineUrlQuality();
    }

    private boolean hasInlineUrlQuality() {
        return currentInlineResult != null && currentInlineResult.getUrl().isMulti();
    }

    private String inlineQualityName(int position) {
        if (currentInlineResult == null) return getString(R.string.detail_quality);
        String name = currentInlineResult.getUrl().n(position);
        return TextUtils.isEmpty(name) ? String.valueOf(position + 1) : name;
    }

    private String parseLabel() {
        String name = VodConfig.get().getParse().getName();
        return TextUtils.isEmpty(name) ? getString(R.string.parse) : name;
    }

    private int getInlineScale() {
        return history != null && history.getScale() != -1 ? history.getScale() : PlayerSetting.getScale();
    }

    private String scaleLabel() {
        return scaleLabel(getInlineScale());
    }

    private String scaleLabel(int scale) {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        int index = Math.max(0, Math.min(scale, array.length - 1));
        return array.length == 0 ? getString(R.string.play_scale) : array[index];
    }

    private void setInlineScaleText(CharSequence text) {
        binding.playerScale.setText(text);
        if (Util.isMobile() && detailActionRoot != null) detailActionView(R.id.scale, TextView.class).setText(text);
    }

    private void setInlineScale(int scale) {
        if (history != null) history.setScale(scale);
        applyResizeMode(scale);
        setInlineScaleText(scaleLabel(scale));
    }

    private void setInlinePreviewScale(int scale) {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (scale < 0 || scale >= array.length) return;
        applyResizeMode(scale);
        setInlineScaleText(array[scale]);
    }

    private void showInlineQuality() {
        if (!canChangeInlineQuality()) return;
        int count = currentInlineResult.getUrl().getValues().size();
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) labels[i] = inlineQualityName(i);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_quality)
                .setSingleChoiceItems(labels, currentInlineResult.getUrl().getPosition(), (dialog, which) -> {
                    dialog.dismiss();
                    changeInlineQuality(which);
                })
                .show();
    }

    private void changeInlineQuality(int position) {
        if (!canChangeInlineQuality()) return;
        if (currentInlineResult.getUrl().getPosition() == position) {
            binding.playerQuality.setText(qualityLabel());
            if (Util.isMobile() && detailActionRoot != null) detailActionView(R.id.actionQuality, TextView.class).setText(binding.playerQuality.getText());
            return;
        }
        saveInlineHistory();
        // 换画质同集换 URL：切片结构与在途归因都属于上一画质。
        // 放在这里而不是 startInlinePlayer，因为后者也承担断连恢复，
        // 那条路径不该关掉用户正在看的反馈对话框。
        resetAdFeedback();
        currentInlineResult.getUrl().set(position);
        updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
        startInlinePlayer(currentInlineResult);
    }

    private void cycleInlineParse() {
        List<Parse> parses = VodConfig.get().getParses();
        if (!useParse || parses.isEmpty()) return;
        Parse current = VodConfig.get().getParse();
        int index = parses.indexOf(current);
        Parse next = parses.get(index < 0 || index == parses.size() - 1 ? 0 : index + 1);
        changeInlineParse(next);
    }

    private void changeInlineParse(Parse next) {
        if (!useParse || next == null || next.isSelected()) return;
        VodConfig.get().setParse(next);
        if (inlineParseAdapter != null) inlineParseAdapter.notifyDataSetChanged();
        Notify.show(getString(R.string.play_switch_parse, next.getName()));
        playInline();
    }

    private final class InlineParseAdapter extends RecyclerView.Adapter<InlineParseAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView text = new TextView(parent.getContext(), null, 0, R.style.Control);
            text.setGravity(Gravity.CENTER);
            return new ViewHolder(text);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Parse item = VodConfig.get().getParses().get(position);
            holder.text.setText(item.getName());
            holder.text.setSelected(item.isSelected());
            holder.text.setOnClickListener(view -> changeInlineParse(item));
        }

        @Override
        public int getItemCount() {
            return VodConfig.get().getParses().size();
        }

        private final class ViewHolder extends RecyclerView.ViewHolder {

            private final TextView text;

            private ViewHolder(@NonNull TextView text) {
                super(text);
                this.text = text;
            }
        }
    }

    private void changeInlineSpeed() {
        if (service() == null || player().isEmpty()) return;
        setInlineSpeedText(player().addSpeed());
        if (history != null) history.setUserSpeed(player().getSpeed());
    }

    private void setInlineSpeed(float speed) {
        if (service() == null || player() == null || player().isReleased()) return;
        setInlineSpeedText(player().setSpeed(normalizeInlineSpeed(speed)));
    }

    private float normalizeInlineSpeed(float speed) {
        return speed >= 0.25f && speed <= 5.0f ? speed : PlayerSetting.getDefaultSpeed();
    }

    private void setInlineSpeedText(CharSequence text) {
        binding.playerSpeed.setText(text);
        if (Util.isMobile() && detailActionRoot != null) detailActionView(R.id.speed, TextView.class).setText(text);
    }

    private boolean resetInlineSpeed() {
        if (service() == null || player().isEmpty()) return false;
        setInlineSpeedText(player().toggleSpeed(getInlinePlaybackSpeed()));
        if (history != null) history.setUserSpeed(player().getSpeed());
        return true;
    }

    private void refreshInlinePlayback() {
        if (selectedFlag == null || selectedEpisode == null) return;
        if (history != null) history.setPosition(C.TIME_UNSET);
        playInline();
    }

    private void cycleInlineScale() {
        if (service() == null || player().isEmpty()) return;
        showResizeModeDialog(getInlineScale(), this::setInlineScale);
    }

    private void toggleInlineDecode() {
        if (service() == null || player().isEmpty()) return;
        player().toggleDecode();
        setInlineDecodeText(inlineDecodeText(true));
    }

    private void toggleInlinePlayParams() {
        if (inlineOsd == null) {
            Notify.show("OSD未初始化");
            return;
        }
        boolean visible = !inlineOsd.isDiagnosticsVisible();
        PlayerSetting.putOsdDiagnostics(visible);
        inlineOsd.setDiagnosticsVisible(visible);
        binding.playerPlayParams.setSelected(visible);
        // 设置文字颜色：选中时黄色，否则白色
        binding.playerPlayParams.setTextColor(visible ? 0xFFFFD700 : 0xFFFFFFFF);
        hideInlineControls();
    }

    private void showInlineMultiThreadProxy() {
        if (service() == null || player() == null || player().isEmpty()) return;
        MultiThreadProxyDialog.show(this, player().getUrl(), this::onInlineMultiThreadProxySaved);
    }

    private void onInlineMultiThreadProxySaved(boolean applyNow) {
        if (service() == null || player() == null || player().isEmpty()) return;
        boolean playing = player().isPlaying();
        if (applyNow) player().reloadCurrentMediaItem();
        updateInlineButtons(playing);
        hideInlineControls();
    }

    private void showInlineCodecCapability() {
        if (service() == null || player().isEmpty()) return;
        CodecCapabilityDialog.show(this, player());
        hideInlineControls();
    }

    private String getInlineOsdTitle() {
        if (selectedEpisode == null) return "";
        String name = playbackHistoryName();
        String episodeTitle = historyEpisodeTitle(selectedEpisode);
        return TextUtils.isEmpty(episodeTitle) || TextUtils.equals(name, episodeTitle)
                ? name : getString(R.string.detail_title, name, episodeTitle);
    }

    private void onInlineLut() {
        if (service() == null || player().isEmpty()) {
            Notify.show("播放器未就绪");
            return;
        }
        binding.lutQuick.toggle(player(), binding.exo, this::onInlineLutChanged, new com.fongmi.android.tv.ui.custom.LutQuickPanel.ImportCallback() {
            @Override
            public void onImportLut() {
                onInlineLutImport();
            }

            @Override
            public void onSelectLutDir() {
                onInlineLutDir();
            }
        });
        focusInlineLutQuickIfVisible();
    }

    private void onInlineLutChanged() {
        // LUT变更后无需特殊处理，LutQuickPanel会自动应用
    }

    private void focusInlineLutQuickIfVisible() {
        binding.lutQuick.post(this::focusInlineLutQuickContent);
        binding.lutQuick.postDelayed(this::focusInlineLutQuickContent, 220);
        binding.lutQuick.postDelayed(this::focusInlineLutQuickContent, 420);
    }

    private boolean focusInlineLutQuickContent() {
        if (!isVisible(binding.lutQuick)) return false;
        View focus = getCurrentFocus();
        RecyclerView recycler = findRecyclerView(binding.lutQuick);
        if (focus != null && isChildOf(binding.lutQuick, focus) && focus != recycler) return true;
        if (binding.lutQuick.focusSelectedEntry()) return true;
        if (focusRecyclerItem(recycler)) return true;
        return focusFirstChild(binding.lutQuick);
    }

    private RecyclerView findRecyclerView(View view) {
        if (view instanceof RecyclerView recycler) return recycler;
        if (!(view instanceof ViewGroup group)) return null;
        for (int i = 0; i < group.getChildCount(); i++) {
            RecyclerView recycler = findRecyclerView(group.getChildAt(i));
            if (recycler != null) return recycler;
        }
        return null;
    }

    private boolean dispatchInlineLutQuickKey(KeyEvent event) {
        if (KeyUtil.isEnterKey(event)) return dispatchInlineLutQuickEnter(event);
        if (isInlineLutQuickDirectionKey(event)) return dispatchInlineLutQuickDirection(event);
        if (KeyUtil.isActionDown(event)) focusInlineLutQuickContent();
        super.dispatchKeyEvent(event);
        if (KeyUtil.isActionDown(event)) {
            View focus = getCurrentFocus();
            if (focus == null || !isChildOf(binding.lutQuick, focus)) focusInlineLutQuickContent();
        }
        return true;
    }

    private boolean isInlineLutQuickDirectionKey(KeyEvent event) {
        return KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    private boolean dispatchInlineLutQuickDirection(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return true;
        RecyclerView recycler = findRecyclerView(binding.lutQuick);
        View focus = getCurrentFocus();
        if (recycler != null && (focus == recycler || isChildOf(recycler, focus)) && moveInlineLutQuickRecycler(recycler, event)) return true;
        if (focus == null || !isChildOf(binding.lutQuick, focus) || focus == recycler) {
            focusInlineLutQuickContent();
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(binding.lutQuick, focus) && moveInlineLutQuickFocus(focus, event)) return true;
        if (recycler != null && KeyUtil.isDownKey(event) && focusRecyclerItem(recycler)) return true;
        focusInlineLutQuickContent();
        return true;
    }

    private boolean moveInlineLutQuickRecycler(RecyclerView recycler, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 0) return false;
        int current = getRecyclerFocusPosition(recycler);
        if (current == RecyclerView.NO_POSITION) return binding.lutQuick.focusSelectedEntry();
        int next = current + (KeyUtil.isDownKey(event) ? 1 : -1);
        if (next < 0 || next >= adapter.getItemCount()) return false;
        return focusRecyclerPosition(recycler, next);
    }

    private int getRecyclerFocusPosition(RecyclerView recycler) {
        View child = getRecyclerDirectChild(recycler, getCurrentFocus());
        return child == null ? RecyclerView.NO_POSITION : recycler.getChildAdapterPosition(child);
    }

    private View getRecyclerDirectChild(RecyclerView recycler, View focus) {
        for (View view = focus; view != null && view != recycler; ) {
            if (view.getParent() == recycler) return view;
            if (!(view.getParent() instanceof View next)) return null;
            view = next;
        }
        return null;
    }

    private boolean moveInlineLutQuickFocus(View focus, KeyEvent event) {
        List<View> focusables = new ArrayList<>();
        collectInlineLutQuickFocusables(binding.lutQuick, focusables);
        View target = findInlineLutQuickFocusTarget(focus, focusables, event);
        return target != null && target.requestFocus();
    }

    private void collectInlineLutQuickFocusables(View view, List<View> focusables) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return;
        if (view instanceof RecyclerView recycler) {
            for (int i = 0; i < recycler.getChildCount(); i++) collectInlineLutQuickFocusables(recycler.getChildAt(i), focusables);
            return;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectInlineLutQuickFocusables(group.getChildAt(i), focusables);
            return;
        }
        if (view.isFocusable()) focusables.add(view);
    }

    private View findInlineLutQuickFocusTarget(View focus, List<View> focusables, KeyEvent event) {
        Rect current = new Rect();
        if (focus == null || !focus.getGlobalVisibleRect(current)) return null;
        View target = null;
        long bestScore = Long.MAX_VALUE;
        for (View item : focusables) {
            if (item == focus) continue;
            Rect candidate = new Rect();
            if (!item.getGlobalVisibleRect(candidate) || !isInlineLutQuickFocusCandidate(current, candidate, event)) continue;
            long score = scoreInlineLutQuickFocusCandidate(current, candidate, event);
            if (score < bestScore) {
                bestScore = score;
                target = item;
            }
        }
        return target;
    }

    private boolean isInlineLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        int dx = candidate.centerX() - current.centerX();
        int dy = candidate.centerY() - current.centerY();
        if (KeyUtil.isLeftKey(event)) return dx < 0 && isSameInlineLutQuickFocusRow(current, candidate);
        if (KeyUtil.isRightKey(event)) return dx > 0 && isSameInlineLutQuickFocusRow(current, candidate);
        if (KeyUtil.isUpKey(event)) return dy < 0;
        if (KeyUtil.isDownKey(event)) return dy > 0;
        return false;
    }

    private boolean isSameInlineLutQuickFocusRow(Rect current, Rect candidate) {
        return Math.abs(candidate.centerY() - current.centerY()) <= Math.max(current.height(), candidate.height());
    }

    private long scoreInlineLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        long dx = Math.abs(candidate.centerX() - current.centerX());
        long dy = Math.abs(candidate.centerY() - current.centerY());
        long primary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dx : dy;
        long secondary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dy : dx;
        return primary * 1000 + secondary;
    }

    private boolean dispatchInlineLutQuickEnter(KeyEvent event) {
        if (KeyUtil.isActionDown(event)) {
            focusInlineLutQuickContent();
            return true;
        }
        if (!KeyUtil.isActionUp(event)) return true;
        View focus = getCurrentFocus();
        if (focus == null || !isChildOf(binding.lutQuick, focus) || focus instanceof RecyclerView) {
            if (!focusInlineLutQuickContent()) return true;
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(binding.lutQuick, focus) && focus.isEnabled()) focus.performClick();
        return true;
    }

    private boolean focusRecyclerItem(RecyclerView recycler) {
        return focusRecyclerPosition(recycler, 0);
    }

    private boolean focusRecyclerPosition(RecyclerView recycler, int position) {
        if (recycler == null || recycler.getVisibility() != View.VISIBLE || !recycler.isEnabled()) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 0) return false;
        if (position < 0 || position >= adapter.getItemCount()) return false;
        recycler.scrollToPosition(position);
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
        if (holder != null && focusFirstChild(holder.itemView)) return true;
        for (int i = 0; i < recycler.getChildCount(); i++) {
            View child = recycler.getChildAt(i);
            if (recycler.getChildAdapterPosition(child) == position && focusFirstChild(child)) return true;
        }
        recycler.post(() -> {
            RecyclerView.ViewHolder next = recycler.findViewHolderForAdapterPosition(position);
            if (next != null) {
                focusFirstChild(next.itemView);
                return;
            }
            for (int i = 0; i < recycler.getChildCount(); i++) {
                View child = recycler.getChildAt(i);
                if (recycler.getChildAdapterPosition(child) == position) {
                    focusFirstChild(child);
                    return;
                }
            }
        });
        return true;
    }

    private boolean focusFirstChild(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return false;
        if (view instanceof RecyclerView recycler) return focusRecyclerItem(recycler);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (focusFirstChild(group.getChildAt(i))) return true;
            }
        }
        return view.isFocusable() && view.requestFocus();
    }

    private boolean isChildOf(ViewGroup parent, View child) {
        for (View view = child; view != null; ) {
            if (view == parent) return true;
            if (!(view.getParent() instanceof View next)) return false;
            view = next;
        }
        return false;
    }

    private void onInlineLutImport() {
        if (!LutStore.hasUserDir()) {
            pendingInlineLutImport = true;
            chooseInlineLutDir();
            return;
        }
        chooseInlineLutFile();
    }

    private void onInlineLutDir() {
        pendingInlineLutImport = false;
        chooseInlineLutDir();
    }

    private void chooseInlineLutFile() {
        FileChooser.from(inlineLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseInlineLutDir() {
        FileChooser.from(inlineLutDir).showDirectory();
    }


    private boolean showInlinePlayerChoice() {
        if (service() == null || player().isEmpty()) return false;
        String[] kernels = PlayerKernelDialog.kernels(getResources());
        String[] items = Arrays.copyOf(kernels, kernels.length + 1);
        items[kernels.length] = getString(R.string.player_kernel_external);
        new MaterialAlertDialogBuilder(this).setItems(items, (dialog, which) -> onInlinePlayerChoice(kernels, which)).show();
        return true;
    }

    private void onInlinePlayerChoice(String[] kernels, int which) {
        if (which < kernels.length) switchInlinePlayer(PlayerSetting.kernelAt(which));
        else openInlineExternal();
    }

    private void switchInlinePlayer(int playerType) {
        if (service() == null || player().isEmpty()) return;
        if (!refreshAndSwitchInlinePlayer(playerType)) Notify.show(R.string.error_play_url);
    }

    private boolean refreshAndSwitchInlinePlayer(int playerType) {
        if (playerType == player().getPlayerType()) {
            cancelPendingInlinePlayerSwitch();
            return true;
        }
        if (selectedFlag == null || selectedEpisode == null) return false;
        String key = getKeyText();
        String flag = selectedFlag.getFlag();
        String episodeUrl = selectedEpisode.getUrl();
        if (TextUtils.isEmpty(flag) || TextUtils.isEmpty(episodeUrl)) return false;
        int generation = ++inlinePlaybackGeneration;
        inlinePlayerSwitchLoading = true;
        showInlineLoading();
        long position = player().getPosition();
        float speed = player().getSpeed();
        boolean repeat = player().isRepeatOne();
        MediaMetadata metadata = buildMetadata();
        SpiderDebug.log("webhome-inline", "switch player refresh start type=%d key=%s flag=%s episode=%s", playerType, key, flag, episodeUrl);
        detailTasks.submit(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episodeUrl, playerType);
                runOnAliveUi(() -> {
                    if (!isInlinePlayerSwitchRequestCurrent(generation, key, flag, episodeUrl)) return;
                    if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
                        finishInlinePlayerSwitchRequest();
                        Notify.show(result != null && result.hasMsg() ? result.getMsg() : getString(R.string.error_play_url));
                    } else {
                        // 换内核重新解析出可能不同的 URL，与换画质同理
                        resetAdFeedback();
                        currentInlineResult = result;
                        inlineHttpRefreshAttempted = false;
                        useParse = result.shouldUseParse();
                        inlinePlayerSwitchLoading = false;
                        player().switchPlayer(playerType, result, activePlaybackKey(), metadata, useParse, position, speed, repeat);
                        rememberInlinePlayerKernel(playerType);
                    }
                    finishInlinePlayerSwitch();
                });
            } catch (Throwable e) {
                runOnAliveUi(() -> {
                    if (!isInlinePlayerSwitchRequestCurrent(generation, key, flag, episodeUrl)) return;
                    finishInlinePlayerSwitchRequest();
                    finishInlinePlayerSwitch();
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.error_play_url) : e.getMessage());
                });
            }
        });
        return true;
    }

    private void cancelPendingInlinePlayerSwitch() {
        if (!inlinePlayerSwitchLoading) return;
        inlinePlaybackGeneration++;
        finishInlinePlayerSwitchRequest();
    }

    private void finishInlinePlayerSwitchRequest() {
        inlinePlayerSwitchLoading = false;
        hideInlineLoading();
        updateInlineDisplayPanel();
    }

    private boolean isInlinePlayerSwitchRequestCurrent(int generation, String key, String flag, String episodeUrl) {
        return inlinePlayerSwitchLoading
                && inlineStarted
                && isInlinePlayerMode()
                && isOwner()
                && service() != null
                && player() != null
                && !player().isEmpty()
                && isInlinePlaybackRequestCurrent(generation, key, flag, episodeUrl);
    }

    private void finishInlinePlayerSwitch() {
        syncInlineHistory();
        binding.playerExternal.setText(player().getPlayerText());
        setInlineDecodeText(inlineDecodeText(true));
        updateInlineButtons(false);
    }

    private void showInlineTrack(View view) {
        if (service() == null || player().isEmpty()) return;
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).search(this::showSubtitleSearch).show(this);
    }

    private boolean showInlineSubtitle() {
        if (service() == null || player().isEmpty() || !player().haveTrack(C.TRACK_TYPE_TEXT)) return false;
        onSubtitleClick();
        return true;
    }

    private void onInlineAdFeedback() {
        if (player() == null || TextUtils.isEmpty(player().getUrl())) {
            Notify.show(R.string.ad_feedback_no_url);
            return;
        }
        if (!isInlineAdFeedbackEnabled()) {
            Notify.show(R.string.ad_interval_invalid);
            return;
        }
        hideInlineControls();
        submitInlineAdFeedback();
    }

    private boolean isInlineAdFeedbackEnabled() {
        // AI 不再是硬门槛：本地归因通道（切片结构/域名/既有规则）不依赖 AI 配置。
        // 只要求可定位的时间轴 —— 直播和时长未知的内容无法框选区间。
        if (player() == null || TextUtils.isEmpty(player().getUrl())) return false;
        return Setting.isAdblock() && !player().isLive() && player().getDuration() > 0;
    }

    private AdFeedbackController adFeedback() {
        if (mAdFeedback == null) {
            mAdFeedbackHost = new AdFeedbackHostAdapter(
                    new AdFeedbackPlayback(), new AdFeedbackMetadata(), new AdFeedbackUi());
            mAdFeedback = new AdFeedbackController(mAdFeedbackHost);
        }
        return mAdFeedback;
    }

    /** 换源换集：作废在途归因、清标记模式与上一集的切片证据缓存。 */
    private void resetAdFeedback() {
        mAdMarkStartMs = -1;
        if (mAdFeedback != null) mAdFeedback.invalidate();
        if (mAdFeedbackHost != null) mAdFeedbackHost.invalidateEvidence();
        // 作废后归因回调不再刷新界面，对话框会永久停在「分析中」
        if (mAdFeedbackDialog != null) mAdFeedbackDialog.close();
        mAdFeedbackDialog = null;
    }

    /**
     * 起播后记录本站域名，供域名信誉通道建立基线。
     *
     * <p>必须强制初始化适配器：它原本只在用户首次按下「有广告」时懒创建，
     * 若这里跳过未初始化的情况，首次反馈时基线仍为空，域名通道会因
     * 「无基线不断言」而弃权 —— 整条通道等于没接。
     */
    private void recordAdFeedbackHost() {
        // 关掉去广告或播直播时不建基线：这些域名会挤掉 LRU 里真正有用的条目
        if (!isInlineAdFeedbackEnabled()) return;
        adFeedback();
        mAdFeedbackHost.recordPlaybackHost();
    }

    /** 短按：标记模式下第二次按下确定终点，否则以当前位置为终点回溯推断起点。 */
    private void submitInlineAdFeedback() {
        AdFeedbackController controller = adFeedback();
        AdFeedbackSession session;
        if (mAdMarkStartMs >= 0) {
            // 先取位置再清标记：播放器已释放时保留标记，用户可重试而不必重新打点
            long end = mAdFeedbackHost.safePositionMs();
            if (end < 0) {
                Notify.show(R.string.ad_interval_invalid);
                return;
            }
            long start = mAdMarkStartMs;
            mAdMarkStartMs = -1;
            session = controller.onMarkedInterval(start, end);
        } else {
            session = controller.onQuickReport(mAdFeedbackHost.cachedEvidence());
        }
        if (session == null) {
            Notify.show(R.string.ad_interval_invalid);
            return;
        }
        // 统计放在成交之后，未成立的反馈不计入
        AdBlockStatsStore.recordFeedback(getKeyText());
    }

    /** 长按：打起点进入标记模式，再次长按取消。 */
    private boolean onInlineAdFeedbackLongPress() {
        if (!isInlineAdFeedbackEnabled()) return false;
        if (mAdMarkStartMs >= 0) {
            mAdMarkStartMs = -1;
            Notify.show(R.string.ad_interval_mark_cancelled);
        } else {
            mAdMarkStartMs = player().getPosition();
            Notify.show(R.string.ad_interval_mark_start);
        }
        return true;
    }

    private void showAdFeedbackSession(AdFeedbackSession session) {
        if (isFinishing() || isDestroyed()) return;
        if (mAdFeedbackDialog == null) mAdFeedbackDialog = new AdFeedbackDialog(this, this::applyAdRulePlan);
        mAdFeedbackDialog.show(session);
    }

    private void applyAdRulePlan(com.fongmi.android.tv.ad.feedback.AdAttribution plan) {
        Site site = getCurrentSite();
        AdRulePlanApplier.Outcome outcome = AdRulePlanApplier.apply(
                plan, site == null ? getKeyText() : site.getKey());
        Notify.show(switch (outcome) {
            case APPLIED -> R.string.ad_interval_rule_saved;
            case SKIPPED -> R.string.ad_interval_rule_skipped;
            case FAILED -> R.string.ad_interval_rule_failed;
        });
    }

    private final class AdFeedbackPlayback implements AdFeedbackHostAdapter.Playback {
        @Override
        public long positionMs() {
            return player() == null ? 0 : player().getPosition();
        }

        @Override
        public long durationMs() {
            return player() == null ? 0 : player().getDuration();
        }

        @Override
        public String playUrl() {
            return player() == null ? "" : player().getUrl();
        }

        @Override
        public java.util.Map<String, String> headers() {
            return player() == null ? java.util.Map.of() : player().getHeaders();
        }

        @Override
        public boolean hls() {
            return player() != null && MediaSourceFactory.isHlsUrl(player().getUrl());
        }

        @Override
        public boolean skipInterval(long startMs, long endMs, String feedbackId) {
            return player() != null && player().skipUserAdInterval(startMs, endMs, feedbackId);
        }
    }

    private final class AdFeedbackMetadata implements AdFeedbackHostAdapter.Metadata {
        @Override
        public String siteKey() {
            Site site = getCurrentSite();
            return site == null ? getKeyText() : site.getKey();
        }

        @Override
        public String siteName() {
            Site site = getCurrentSite();
            return site == null ? "" : site.getName();
        }

        @Override
        public String vodName() {
            History currentHistory = getHistory();
            return currentHistory == null ? getNameText() : currentHistory.getVodName();
        }

        @Override
        public String flagName() {
            return selectedFlag == null ? "" : selectedFlag.getFlag();
        }

        @Override
        public String episodeName() {
            return selectedEpisode == null ? "" : selectedEpisode.getName();
        }
    }

    private final class AdFeedbackUi implements AdFeedbackHostAdapter.Ui {
        @Override
        public void runBackground(Runnable task) {
            detailTasks.submit(Task.recommendationExecutor(), task);
        }

        @Override
        public void runOnUi(Runnable task) {
            runOnAliveUi(task);
        }

        @Override
        public void showSession(AdFeedbackSession session) {
            showAdFeedbackSession(session);
        }
    }

    private void showInlineDanmaku() {
        if (service() == null || player().isEmpty()) return;
        DanmakuDialog.create().player(player()).identity(getKeyText(), getIdText(), playbackHistoryName(), selectedEpisode == null ? "" : historyEpisodeTitle(selectedEpisode)).show(this);
    }

    private void showInlineTitle() {
        if (service() == null || player().isEmpty() || !player().haveTitle()) return;
        TitleDialog.create().player(player()).show(this);
    }

    private void toggleInlineDanmaku() {
        if (inlineControlController == null) return;
        inlineControlController.onDanmakuButton();
    }

    private boolean onPlayerDanmakuLongClick() {
        if (service() == null || player().isEmpty() || !inlineControlController.hasDanmakuControl()) return false;
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        inlineControlController.applyDanmakuSetting();
        updateInlineButtonColors();
        Notify.show(DanmakuSetting.isShow() ? R.string.danmaku_show_on : R.string.danmaku_show_off);
        return true;
    }

    private void toggleInlineRepeat() {
        if (service() == null || player().isEmpty()) return;
        player().setRepeatOne(!player().isRepeatOne());
        updateInlineButtons(player().isPlaying());
    }

    private void setInlineOpeningFromPosition() {
        if (history == null || service() == null || player().isEmpty()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setInlineOpening(position);
        setInlineHideCallback();
    }

    private boolean resetInlineOpening() {
        // 长按清空是「这里不要有值」，别紧接着又把探测值渲染上去，看起来像没清掉
        introSkipPlayback.suppressDetected(true);
        setInlineOpening(0);
        setInlineHideCallback();
        return true;
    }

    private void setInlineOpening(long opening) {
        if (history == null) return;
        history.setOpening(opening);
        updateInlineOpeningEndingText();
        syncInlineHistory();
    }

    private void setInlineEndingFromPosition() {
        if (history == null || service() == null || player().isEmpty()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setInlineEnding(duration - position);
        setInlineHideCallback();
    }

    private boolean resetInlineEnding() {
        introSkipPlayback.suppressDetected(false);
        setInlineEnding(0);
        setInlineHideCallback();
        return true;
    }

    private void setInlineEnding(long ending) {
        if (history == null) return;
        history.setEnding(ending);
        updateInlineOpeningEndingText();
        syncInlineHistory();
    }

    private void updateInlineOpeningEndingText() {
        if (binding == null) return;
        binding.playerOpening.setText(inlineOpeningLabel());
        binding.playerEnding.setText(inlineEndingLabel());
        if (!Util.isMobile() || detailActionRoot == null) return;
        detailActionView(R.id.opening, TextView.class).setText(binding.playerOpening.getText());
        detailActionView(R.id.ending, TextView.class).setText(binding.playerEnding.getText());
    }

    private String inlineOpeningLabel() {
        if (history != null && history.getOpening() > 0) return Util.timeMs(history.getOpening());
        long detected = detectedIntroSkipValue(true);
        return detected > 0 ? getString(R.string.intro_skip_detected_value, Util.timeMs(detected)) : getString(R.string.play_op);
    }

    private String inlineEndingLabel() {
        if (history != null && history.getEnding() > 0) return Util.timeMs(history.getEnding());
        long detected = detectedIntroSkipValue(false);
        return detected > 0 ? getString(R.string.intro_skip_detected_value, Util.timeMs(detected)) : getString(R.string.play_ed);
    }

    /** 关掉自动跳过时不显示探测值——那种情况下这个数字不会导致任何动作，显示出来是误导。 */
    private long detectedIntroSkipValue(boolean opening) {
        // isReleased 必查：服务已 release 但 Activity 还握着 mService 的窗口里，
        // PlayerManager.getDuration() 会直接对空的 player 取值抛 NPE
        if (!Setting.isIntroSkipEnabled() || player() == null || player().isReleased()) return -1;
        return opening ? introSkipPlayback.getDetectedOpeningMs() : introSkipPlayback.getDetectedEndingMs(player().getDuration());
    }

    private void onInlineBack() {
        if (inlineFullscreen) backFromInlineFullscreen();
        else {
            prepareInlinePlayerTransition();
            finish();
        }
    }

    private void backFromInlineFullscreen() {
        // 详情直放模式（含手机版）返回时应关闭内嵌播放器回到纯详情页，
        // 否则手机版只退出全屏、播放器面板仍可见，看起来跟沉浸融合模式一样
        if (isPlayerMode()) {
            exitInlineFullscreen();
            closeDetailFullscreenPlayer();
            return;
        }
        exitInlineFullscreen();
    }

    private void finishPlaybackToHome() {
        if (isPlaybackExiting()) return;
        prepareInlinePlayerTransition();
        saveInlineHistory();
        stopInlinePlaybackSync();
        inlineStarted = false;
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        finishPlayback();
    }

    private void openInlineExternal() {
        if (service() == null || player().isEmpty()) return;
        cancelPendingInlinePlayerSwitch();
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), inlineTitleText());
        setRedirect(true);
    }

    private void showInlineEpisodes() {
        if (selectedFlag == null || selectedFlag.getEpisodes() == null || selectedFlag.getEpisodes().isEmpty()) return;
        if (!Util.isMobile() || Setting.isStandaloneTmdbDetailMode(getDetailMode())) {
            showNativeEnhancedInlineEpisodes();
            return;
        }
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable background = new GradientDrawable();
        background.setColor(colors.panel);
        background.setCornerRadius(ResUtil.dp2px(22));
        background.setStroke(ResUtil.dp2px(1), colors.line);
        content.setBackground(background);
        content.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = new TextView(this);
        title.setText(R.string.detail_episode);
        title.setTextColor(colors.primary);
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        MaterialButton mode = createInlineEpisodeModeButton();
        header.addView(mode, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(36)));
        content.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        HorizontalScrollView pageScroll = new HorizontalScrollView(this);
        pageScroll.setHorizontalScrollBarEnabled(false);
        pageScroll.setVisibility(View.GONE);
        LinearLayout pageRow = new LinearLayout(this);
        pageRow.setOrientation(LinearLayout.HORIZONTAL);
        pageScroll.addView(pageRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(48));
        pageParams.topMargin = ResUtil.dp2px(6);
        content.addView(pageScroll, pageParams);
        RecyclerView recycler = new RecyclerView(this);
        recycler.setClipToPadding(false);
        updateInlineEpisodeLayoutManager(recycler);
        int height = Math.min(ResUtil.dp2px(620), (int) (ResUtil.getScreenHeight(this) * 0.78f));
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        recyclerParams.topMargin = ResUtil.dp2px(8);
        content.addView(recycler, recyclerParams);

        AlertDialog[] holder = new AlertDialog[1];
        InlineEpisodeAdapter adapter = new InlineEpisodeAdapter(new InlineEpisodeAdapter.Listener() {
            @Override
            public void onItemClick(Episode episode) {
                if (holder[0] != null) holder[0].dismiss();
                selectInlineEpisode(episode);
            }

            @Override
            public boolean onItemLongClick(MaterialButton button, Episode episode) {
                Notify.show(inlineEpisodeTitle(episode));
                return true;
            }
        });
        adapter.setLight(lightTheme);
        recycler.setAdapter(adapter);
        final int[] pageIndex = {0};
        final boolean[] manualPage = {false};
        final List<TextView> pageButtons = new ArrayList<>();
        final Runnable[] render = new Runnable[1];
        final java.util.function.BiConsumer<Integer, Boolean> showPage = (index, focusEpisode) -> {
            List<Episode> ordered = orderedInlineEpisodes();
            List<EpisodeRangePolicy.Range> ranges = buildCardEpisodeRanges(ordered, selectedEpisode);
            if (ranges.isEmpty()) {
                adapter.setItems(List.of(), selectedEpisode, inlineEpisodeTitles(selectedFlag.getEpisodes()));
                return;
            }
            pageIndex[0] = Math.max(0, Math.min(index, ranges.size() - 1));
            List<Episode> pageItems = ranges.size() > 1 ? EpisodeRangePolicy.slice(ordered, ranges.get(pageIndex[0])) : ordered;
            adapter.setItems(pageItems, selectedEpisode, inlineEpisodeTitles(selectedFlag.getEpisodes()));
            for (int i = 0; i < pageButtons.size(); i++) applyEpisodeDialogPageState(pageButtons.get(i), i == pageIndex[0], pageButtons.get(i).hasFocus());
            if (!focusEpisode) return;
            int selected = pageItems.indexOf(selectedEpisode);
            if (selected < 0) selected = 0;
            int focus = selected;
            recycler.post(() -> {
                recycler.scrollToPosition(focus);
                recycler.post(() -> {
                    RecyclerView.ViewHolder viewHolder = recycler.findViewHolderForAdapterPosition(focus);
                    if (viewHolder != null) viewHolder.itemView.requestFocus();
                });
            });
        };
        render[0] = () -> {
            List<Episode> ordered = orderedInlineEpisodes();
            pageRow.removeAllViews();
            pageButtons.clear();
            List<EpisodeRangePolicy.Range> ranges = buildCardEpisodeRanges(ordered, selectedEpisode);
            pageScroll.setVisibility(ranges.size() > 1 ? View.VISIBLE : View.GONE);
            if (ranges.isEmpty()) return;
            if (!manualPage[0]) pageIndex[0] = EpisodeRangePolicy.selectedPosition(ranges);
            pageIndex[0] = Math.max(0, Math.min(pageIndex[0], ranges.size() - 1));
            for (int i = 0; i < ranges.size(); i++) {
                TextView button = createEpisodeDialogPageButton(ranges.get(i).label(), i == pageIndex[0]);
                int page = i;
                button.setOnClickListener(view -> {
                    manualPage[0] = true;
                    showPage.accept(page, true);
                });
                button.setOnFocusChangeListener((view, focused) -> {
                    applyEpisodeDialogPageState(button, page == pageIndex[0], focused);
                    if (focused && page != pageIndex[0]) {
                        manualPage[0] = true;
                        showPage.accept(page, false);
                    }
                });
                button.setOnKeyListener((view, keyCode, event) -> moveEpisodeDialogPageFocus(pageButtons, pageScroll, page, keyCode, event, target -> {
                    manualPage[0] = true;
                    showPage.accept(target, false);
                }));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(136), ResUtil.dp2px(34));
                params.setMargins(0, ResUtil.dp2px(5), ResUtil.dp2px(12), ResUtil.dp2px(5));
                pageRow.addView(button, params);
                pageButtons.add(button);
            }
            showPage.accept(pageIndex[0], true);
        };
        mode.setOnClickListener(view -> {
            inlineEpisodeGridMode = !inlineEpisodeGridMode;
            updateInlineEpisodeLayoutManager(recycler);
            updateInlineEpisodeModeButton(mode);
            showPage.accept(pageIndex[0], true);
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();
        holder[0] = dialog;
        dialog.setOnShowListener(value -> render[0].run());
        if (!canTouchUi()) return;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            int width = ResUtil.getScreenWidth(this);
            window.setLayout(Math.min(ResUtil.dp2px(720), (int) (width * 0.92f)), WindowManager.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showNativeEnhancedInlineEpisodes() {
        NativeEnhancedInlineEpisodeLayout layout = nativeEnhancedInlineEpisodeLayout();
        NestedScrollView scroll = new NestedScrollView(this);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.setPadding(layout.paddingHorizontal(), layout.paddingTop(), layout.paddingHorizontal(), layout.paddingBottom());
        GradientDrawable background = new GradientDrawable();
        background.setColor(0x66111820);
        background.setCornerRadius(0);
        scroll.setBackground(background);
        scroll.addView(panel, new NestedScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView flagTitle = createNativeEnhancedInlineSectionTitle(R.string.detail_flag);
        panel.addView(flagTitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        HorizontalScrollView flagScroll = new HorizontalScrollView(this);
        flagScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout flagRow = new LinearLayout(this);
        flagRow.setOrientation(LinearLayout.HORIZONTAL);
        flagScroll.addView(flagRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams flagParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        flagParams.topMargin = ResUtil.dp2px(12);
        panel.addView(flagScroll, flagParams);

        TextView episodeTitle = createNativeEnhancedInlineSectionTitle(R.string.detail_episode);
        LinearLayout.LayoutParams episodeTitleParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        episodeTitleParams.topMargin = ResUtil.dp2px(22);
        panel.addView(episodeTitle, episodeTitleParams);

        HorizontalScrollView pageScroll = new HorizontalScrollView(this);
        pageScroll.setHorizontalScrollBarEnabled(false);
        pageScroll.setVisibility(View.GONE);
        LinearLayout pageRow = new LinearLayout(this);
        pageRow.setOrientation(LinearLayout.HORIZONTAL);
        pageScroll.addView(pageRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams pageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pageParams.topMargin = ResUtil.dp2px(12);
        panel.addView(pageScroll, pageParams);

        RecyclerView recycler = new RecyclerView(this);
        recycler.setClipToPadding(false);
        recycler.setNestedScrollingEnabled(false);
        updateNativeEnhancedInlineEpisodeLayoutManager(recycler, layout.spanCount());
        TmdbEpisodeAdapter adapter = new TmdbEpisodeAdapter(new TmdbEpisodeAdapter.Listener() {
            @Override
            public void onItemClick(Episode episode) {
                Dialog dialog = (Dialog) panel.getTag();
                if (dialog != null) dialog.dismiss();
                selectInlineEpisode(episode);
            }

            @Override
            public void onItemLongClick(View anchor, Episode episode, int episodeNumber) {
                anchor.setPressed(false);
                showTmdbEpisodeDetail(episode, episodeNumber, recycler);
            }
        });
        adapter.setLight(lightTheme);
        adapter.setActiveStrokeColor(0xFF2CC56F);
        adapter.setNativeEnhanced(true);
        recycler.setAdapter(adapter);
        LinearLayout.LayoutParams recyclerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recyclerParams.topMargin = ResUtil.dp2px(10);
        panel.addView(recycler, recyclerParams);

        final int[] pageIndex = {0};
        final List<TextView> flagButtons = new ArrayList<>();
        final List<TextView> pageButtons = new ArrayList<>();
        final Runnable[] render = new Runnable[1];
        View.OnKeyListener flagKeyListener = (view, keyCode, event) -> {
            if (!KeyUtil.isDownKey(event)) return false;
            if (!KeyUtil.isActionDown(event)) return true;
            if (focusNativeEnhancedInlinePageButton(pageButtons, pageScroll, pageIndex[0])) return true;
            return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, layout.spanCount());
        };
        adapter.setOnKeyListener((view, keyCode, event) -> {
            if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
            if (!KeyUtil.isActionDown(event)) return true;
            int position = recycler.getChildAdapterPosition(view);
            if (position == RecyclerView.NO_POSITION) return true;
            if (KeyUtil.isUpKey(event)) {
                if (position < layout.spanCount()) {
                    if (focusNativeEnhancedInlinePageButton(pageButtons, pageScroll, pageIndex[0])) return true;
                    return focusNativeEnhancedInlineFlagButton(flagButtons, flagScroll, selectedFlag);
                }
                return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, position - layout.spanCount(), layout.spanCount());
            }
            int target = position + layout.spanCount();
            if (target >= adapter.getItemCount()) return true;
            return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, target, layout.spanCount());
        });
        final java.util.function.BiConsumer<Integer, Boolean> showPage = (index, focusEpisode) -> {
            List<Episode> ordered = orderedInlineEpisodes();
            List<EpisodeRangePolicy.Range> ranges = buildCardEpisodeRanges(ordered, selectedEpisode);
            if (ranges.isEmpty()) {
                adapter.setItems(List.of(), tmdbEpisodes, Map.of(), selectedEpisode);
                return;
            }
            pageIndex[0] = Math.max(0, Math.min(index, ranges.size() - 1));
            List<Episode> pageItems = EpisodeRangePolicy.slice(ordered, ranges.get(pageIndex[0]));
            adapter.setDisplayMode(TmdbEpisodeAdapter.Mode.GRID, layout.spanCount());
            adapter.setFallbackStillUrl(episodeFallbackStillUrl());
            adapter.setItems(pageItems, tmdbEpisodes, episodeNumbers(pageItems, selectedFlag.getEpisodes()), selectedEpisode);
            for (int i = 0; i < pageButtons.size(); i++) {
                applyNativeEnhancedInlineChipState(pageButtons.get(i), i == pageIndex[0], pageButtons.get(i).hasFocus());
            }
            if (!focusEpisode) return;
            int selected = pageItems.indexOf(selectedEpisode);
            if (selected < 0) selected = 0;
            focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, selected, layout.spanCount());
        };
        Runnable renderFlags = () -> {
            flagRow.removeAllViews();
            flagButtons.clear();
            List<Flag> flags = vod == null ? List.of() : vod.getFlags();
            for (Flag flag : flags) {
                TextView button = createNativeEnhancedInlineChipButton(flag.getShow());
                applyNativeEnhancedInlineChipState(button, flag == selectedFlag, false);
                button.setOnClickListener(view -> switchNativeEnhancedInlineFlag(flag, render));
                button.setOnFocusChangeListener((view, focused) -> applyNativeEnhancedInlineChipState(button, flag == selectedFlag, focused));
                button.setOnKeyListener(flagKeyListener);
                flagRow.addView(button);
                flagButtons.add(button);
            }
        };
        render[0] = () -> {
            renderFlags.run();
            List<Episode> ordered = orderedInlineEpisodes();
            pageRow.removeAllViews();
            pageButtons.clear();
            List<EpisodeRangePolicy.Range> ranges = buildCardEpisodeRanges(ordered, selectedEpisode);
            pageScroll.setVisibility(ranges.size() > 1 ? View.VISIBLE : View.GONE);
            if (ranges.isEmpty()) return;
            int selectedPage = EpisodeRangePolicy.selectedPosition(ranges);
            for (int i = 0; i < ranges.size(); i++) {
                TextView button = createNativeEnhancedInlineChipButton(ranges.get(i).label());
                applyNativeEnhancedInlineChipState(button, i == selectedPage, false);
                int page = i;
                button.setOnClickListener(view -> {
                    showPage.accept(page, false);
                    focusNativeEnhancedInlinePageButton(pageButtons, pageScroll, page);
                });
                button.setOnFocusChangeListener((view, focused) -> {
                    applyNativeEnhancedInlineChipState(button, page == pageIndex[0], focused);
                    if (focused && page != pageIndex[0]) showPage.accept(page, false);
                });
                button.setOnKeyListener((view, keyCode, event) -> {
                    if (KeyUtil.isDownKey(event)) {
                        if (!KeyUtil.isActionDown(event)) return true;
                        return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, layout.spanCount());
                    }
                    if (KeyUtil.isUpKey(event)) {
                        if (!KeyUtil.isActionDown(event)) return true;
                        return focusNativeEnhancedInlineFlagButton(flagButtons, flagScroll, selectedFlag);
                    }
                    return moveEpisodeDialogPageFocus(pageButtons, pageScroll, page, keyCode, event, target -> showPage.accept(target, false));
                });
                pageRow.addView(button);
                pageButtons.add(button);
            }
            showPage.accept(selectedPage, true);
        };

        AlertDialog dialog = new MaterialAlertDialogBuilder(this).setView(scroll).create();
        panel.setTag(dialog);
        dialog.setOnShowListener(value -> render[0].run());
        if (!canTouchUi()) return;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.getDecorView().setPadding(0, 0, 0, 0);
            window.setDimAmount(0.08f);
            window.setGravity(layout.gravity());
            window.setLayout(layout.windowWidth(), WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private TextView createNativeEnhancedInlineSectionTitle(int resId) {
        TextView title = new TextView(this);
        title.setText(resId);
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(16f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setIncludeFontPadding(false);
        return title;
    }

    private TextView createNativeEnhancedInlineChipButton(String text) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setGravity(Gravity.CENTER);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setTextSize(14f);
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setClickable(true);
        button.setMinWidth(ResUtil.dp2px(64));
        button.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(34));
        params.setMargins(0, 0, ResUtil.dp2px(8), 0);
        button.setLayoutParams(params);
        applyNativeEnhancedInlineChipState(button, false, false);
        return button;
    }

    private void applyNativeEnhancedInlineChipState(TextView button, boolean selected, boolean focused) {
        ThemeColors colors = currentThemeColors();
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(ResUtil.dp2px(4));
        background.setColor(focused ? colors.control : selected ? colors.chipActive : colors.chip);
        background.setStroke(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : selected ? 2 : CHIP_STROKE_DP), focused ? FOCUS_STROKE : selected ? colors.accent : colors.line);
        button.setSelected(selected);
        button.setActivated(selected);
        button.setTextColor(colors.primary);
        button.setBackground(background);
        button.refreshDrawableState();
    }

    private void switchNativeEnhancedInlineFlag(Flag flag, Runnable[] render) {
        if (flag == null || flag == selectedFlag) return;
        cancelPendingInlinePlayback();
        playbackSelectionTouched = true;
        selectedFlag = flag;
        savePreferredFlag(flag);
        loadTmdbSeasonBinding();
        selectedEpisode = null;
        selectedSeasonNumber = -1;
        resetEpisodeRange();
        if (selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty()) selectedEpisode = selectedFlag.find(history == null ? "" : history.getVodRemarks(), getMarkText().isEmpty());
        if (selectedEpisode == null && selectedFlag.getEpisodes() != null && !selectedFlag.getEpisodes().isEmpty()) selectedEpisode = selectedFlag.getEpisodes().get(0);
        if (selectedEpisode != null) selectedSeasonNumber = seasonForEpisode(selectedEpisode, selectedFlag.getEpisodes());
        if (render != null && render[0] != null) render[0].run();
    }

    private boolean focusNativeEnhancedInlineFlagButton(List<TextView> buttons, HorizontalScrollView scroll, Flag flag) {
        if (buttons.isEmpty()) return false;
        int target = 0;
        List<Flag> flags = vod == null ? List.of() : vod.getFlags();
        for (int i = 0; i < flags.size() && i < buttons.size(); i++) {
            if (flags.get(i) == flag) {
                target = i;
                break;
            }
        }
        TextView button = buttons.get(target);
        scroll.post(() -> {
            if (button.getParent() == null) return;
            button.requestFocus();
            scroll.smoothScrollTo(Math.max(0, button.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusNativeEnhancedInlinePageButton(List<TextView> buttons, HorizontalScrollView scroll, int position) {
        if (buttons.isEmpty() || scroll.getVisibility() != View.VISIBLE) return false;
        int target = Math.max(0, Math.min(position, buttons.size() - 1));
        TextView button = buttons.get(target);
        scroll.post(() -> {
            if (button.getParent() == null) return;
            button.requestFocus();
            scroll.smoothScrollTo(Math.max(0, button.getLeft() - ResUtil.dp2px(12)), 0);
        });
        return true;
    }

    private boolean focusNativeEnhancedInlineEpisode(NestedScrollView scroll, RecyclerView recycler, TmdbEpisodeAdapter adapter, int spanCount) {
        if (adapter.getItemCount() == 0) return false;
        int focus = Math.max(0, adapter.getPosition(selectedEpisode));
        return focusNativeEnhancedInlineEpisode(scroll, recycler, adapter, focus, spanCount);
    }

    private boolean focusNativeEnhancedInlineEpisode(NestedScrollView scroll, RecyclerView recycler, TmdbEpisodeAdapter adapter, int position, int spanCount) {
        if (adapter.getItemCount() == 0) return false;
        int focus = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        recycler.post(() -> {
            recycler.scrollToPosition(focus);
            recycler.post(() -> {
                RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(focus);
                if (holder != null) {
                    holder.itemView.requestFocus();
                    scroll.post(() -> alignNativeEnhancedInlineEpisodeRow(scroll, recycler, focus, spanCount));
                } else {
                    recycler.requestFocus();
                }
            });
        });
        return true;
    }

    private void alignNativeEnhancedInlineEpisodeRow(NestedScrollView scroll, RecyclerView recycler, int position, int spanCount) {
        int span = Math.max(1, spanCount);
        int rowStart = Math.max(0, position - position % span);
        if (rowStart == 0) {
            scroll.scrollTo(0, 0);
            return;
        }
        RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(rowStart);
        if (holder == null) return;
        int targetY = recycler.getTop() + holder.itemView.getTop() - ResUtil.dp2px(8);
        scroll.scrollTo(0, Math.max(0, targetY));
    }

    private boolean moveEpisodeDialogPageFocus(List<TextView> buttons, HorizontalScrollView scroll, int position, int keyCode, KeyEvent event, java.util.function.IntConsumer showPage) {
        if (keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return true;
        int target = position + (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ? 1 : -1);
        if (target < 0 || target >= buttons.size()) return true;
        TextView button = buttons.get(target);
        showPage.accept(target);
        button.requestFocus();
        scroll.post(() -> scroll.smoothScrollTo(Math.max(0, button.getLeft() - ResUtil.dp2px(12)), 0));
        return true;
    }

    private Map<Episode, String> inlineEpisodeTitles(List<Episode> episodes) {
        Map<Episode, String> titles = new HashMap<>();
        if (episodes == null) return titles;
        for (Episode episode : episodes) titles.put(episode, inlineEpisodeTitle(episode, episodes));
        return titles;
    }

    private String inlineEpisodeTitle(Episode episode) {
        return inlineEpisodeTitle(episode, selectedFlag == null ? null : selectedFlag.getEpisodes());
    }

    private String inlineEpisodeTitle(Episode episode, List<Episode> episodes) {
        EpisodePosition position = episodePosition(episode, episodes);
        String title = tmdbEpisodeTitle(position.season(), position.number());
        if (TextUtils.isEmpty(title) || !TmdbEpisodeMatcher.shouldApply(episode, position.number(), title)) return EpisodeAdapter.getTitle(episode);
        return TmdbEpisodeAdapter.getTitle(episode, position.number(), title);
    }

    private EpisodePosition episodePosition(Episode episode, List<Episode> episodes) {
        int index = episode == null || episodes == null ? -1 : episodes.indexOf(episode);
        return episodePosition(episode, episodes, index);
    }

    private EpisodePosition episodePosition(Episode episode, List<Episode> episodes, int index) {
        int sourceNumber = sourceEpisodeNumber(episode);
        List<Integer> availableSeasons = availableSeasonNumbers(episodes);
        if (availableSeasons.isEmpty()) return new EpisodePosition(-1, linearEpisodeNumber(sourceNumber, index));
        int sourceSeason = sourceSeasonNumberAt(episodes, index, episode);
        if (availableSeasons.contains(sourceSeason)) return new EpisodePosition(sourceSeason, linearEpisodeNumber(sourceNumber, index));
        if (availableSeasons.size() == 1) return new EpisodePosition(availableSeasons.get(0), linearEpisodeNumber(sourceNumber, index));
        if (index < 0) return new EpisodePosition(selectedSeasonNumber, -1);
        List<Integer> sourceNumbers = new ArrayList<>(episodes.size());
        for (Episode item : episodes) sourceNumbers.add(item == null ? -1 : item.getNumber());
        if (!EpisodeSeasonPolicy.canMapFlatEpisodeNumbers(sourceNumbers, seasonNumbers, seasonEpisodeCounts)) {
            return new EpisodePosition(selectedSeasonNumber, linearEpisodeNumber(sourceNumber, index));
        }
        int start = 0;
        for (Integer season : seasonNumbers) {
            int count = Math.max(0, seasonEpisodeCounts.getOrDefault(season, 0));
            if (count <= 0) continue;
            int end = Math.min(episodes.size(), start + count);
            if (index >= start && index < end && availableSeasons.contains(season)) return new EpisodePosition(season, index - start + 1);
            start = end;
        }
        return new EpisodePosition(selectedSeasonNumber, linearEpisodeNumber(sourceNumber, index));
    }

    private int linearEpisodeNumber(int sourceNumber, int index) {
        return EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);
    }

    private List<Episode> orderedInlineEpisodes() {
        List<Episode> ordered = new ArrayList<>(selectedFlag == null || selectedFlag.getEpisodes() == null ? List.of() : selectedFlag.getEpisodes());
        if (episodeReverse) Collections.reverse(ordered);
        return ordered;
    }

    private TextView createEpisodeDialogPageButton(String text, boolean selected) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextSize(15f);
        button.setSingleLine(true);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setGravity(android.view.Gravity.CENTER);
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setClickable(true);
        button.setMinWidth(ResUtil.dp2px(136));
        button.setPadding(ResUtil.dp2px(18), 0, ResUtil.dp2px(18), 0);
        applyEpisodeDialogPageState(button, selected, false);
        return button;
    }

    private void applyEpisodeDialogPageState(TextView button, boolean selected, boolean focused) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(ResUtil.dp2px(6));
        if (focused) {
            background.setColor(lightTheme ? 0x1AFFD166 : 0x55FFD166);
            background.setStroke(ResUtil.dp2px(FOCUS_STROKE_DP), FOCUS_STROKE);
            button.setTextColor(lightTheme ? colors.primary : 0xFFFFFFFF);
        } else if (selected) {
            background.setColor(lightTheme ? 0x1F20B866 : 0x332CC56F);
            background.setStroke(ResUtil.dp2px(2), colors.accent);
            button.setTextColor(colors.accent);
        } else {
            background.setColor(0x00000000);
            button.setTextColor(lightTheme ? colors.secondary : 0xFFC6D0D9);
        }
        button.setSelected(selected);
        button.setBackground(background);
    }

    private void applyEpisodeDialogIconState(ImageView icon, boolean focused) {
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(ResUtil.dp2px(6));
        if (focused) {
            background.setColor(0x55FFD166);
            background.setStroke(ResUtil.dp2px(FOCUS_STROKE_DP), FOCUS_STROKE);
        } else {
            background.setColor(0x00000000);
        }
        icon.setBackground(background);
    }

    private MaterialButton createInlineEpisodeModeButton() {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setMinWidth(ResUtil.dp2px(72));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setAllCaps(false);
        button.setSingleLine(true);
        button.setTextSize(13f);
        button.setGravity(Gravity.CENTER);
        button.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(12), 0);
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        button.setIconPadding(ResUtil.dp2px(4));
        button.setIconSize(ResUtil.dp2px(16));
        button.setFocusable(true);
        button.setFocusableInTouchMode(false);
        button.setClickable(true);
        updateInlineEpisodeModeButton(button);
        button.setOnFocusChangeListener((view, focused) -> applyInlineEpisodeModeButtonState(button, focused));
        return button;
    }

    private void updateInlineEpisodeModeButton(MaterialButton button) {
        // 文字与图标表达当前视图状态，描述表达点击后的动作。
        button.setText(inlineEpisodeGridMode ? R.string.detail_episode_view_grid : R.string.detail_episode_view_list);
        button.setIconResource(inlineEpisodeGridMode ? R.drawable.ic_site_grid : R.drawable.ic_site_list);
        button.setContentDescription(getString(inlineEpisodeGridMode ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
        applyInlineEpisodeModeButtonState(button, button.hasFocus());
    }

    private void applyInlineEpisodeModeButtonState(MaterialButton button, boolean focused) {
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        int text = focused ? (lightTheme ? colors.primary : 0xFFFFFFFF) : colors.primary;
        button.setTextColor(text);
        button.setIconTint(ColorStateList.valueOf(text));
        button.setBackgroundTintList(ColorStateList.valueOf(focused ? (lightTheme ? 0x1AFFD166 : 0x55FFD166) : colors.control));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : colors.lineStrong));
        button.setStrokeWidth(ResUtil.dp2px(focused ? 2 : 1));
    }

    private void updateInlineEpisodeModeIcon(ImageView icon) {
        // 图标表达当前视图状态，描述表达点击后的动作。
        icon.setImageResource(inlineEpisodeGridMode ? R.drawable.ic_site_grid : R.drawable.ic_site_list);
        icon.setContentDescription(getString(inlineEpisodeGridMode ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
        icon.setColorFilter(0xFFEAF2F8);
    }

    private void updateInlineEpisodeLayoutManager(RecyclerView recycler) {
        RecyclerView.LayoutManager current = recycler.getLayoutManager();
        if (inlineEpisodeGridMode) {
            int spanCount = inlineEpisodeSpanCount();
            if (current instanceof GridLayoutManager grid && grid.getSpanCount() == spanCount) return;
            recycler.setLayoutManager(new GridLayoutManager(this, spanCount));
        } else {
            if (current instanceof LinearLayoutManager linear && !(current instanceof GridLayoutManager) && linear.getOrientation() == LinearLayoutManager.VERTICAL) return;
            recycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false));
        }
    }

    private void updateNativeEnhancedInlineEpisodeLayoutManager(RecyclerView recycler, int spanCount) {
        RecyclerView.LayoutManager current = recycler.getLayoutManager();
        if (current instanceof GridLayoutManager grid && grid.getSpanCount() == spanCount) return;
        recycler.setLayoutManager(new GridLayoutManager(this, spanCount));
    }

    private NativeEnhancedInlineEpisodeLayout nativeEnhancedInlineEpisodeLayout() {
        int span = nativeEnhancedInlineEpisodeSpanCount();
        if (!Util.isMobile()) {
            return new NativeEnhancedInlineEpisodeLayout(span, 48, 34, 26, Gravity.CENTER, WindowManager.LayoutParams.MATCH_PARENT);
        }
        return new NativeEnhancedInlineEpisodeLayout(span, 36, 28, 24, Gravity.CENTER, WindowManager.LayoutParams.MATCH_PARENT);
    }

    private int nativeEnhancedInlineEpisodeSpanCount() {
        return TmdbEpisodeGridPolicy.nativeEnhancedSpanCount(Util.isMobile(), ResUtil.isPad(), ResUtil.isLand(this), getResources().getConfiguration().screenWidthDp);
    }

    private static class NativeEnhancedInlineEpisodeLayout {
        private final int spanCount;
        private final int paddingHorizontalDp;
        private final int paddingTopDp;
        private final int paddingBottomDp;
        private final int gravity;
        private final int windowWidth;

        private NativeEnhancedInlineEpisodeLayout(int spanCount, int paddingHorizontalDp, int paddingTopDp, int paddingBottomDp, int gravity, int windowWidth) {
            this.spanCount = spanCount;
            this.paddingHorizontalDp = paddingHorizontalDp;
            this.paddingTopDp = paddingTopDp;
            this.paddingBottomDp = paddingBottomDp;
            this.gravity = gravity;
            this.windowWidth = windowWidth;
        }

        private int spanCount() {
            return spanCount;
        }

        private int paddingHorizontal() {
            return ResUtil.dp2px(paddingHorizontalDp);
        }

        private int paddingTop() {
            return ResUtil.dp2px(paddingTopDp);
        }

        private int paddingBottom() {
            return ResUtil.dp2px(paddingBottomDp);
        }

        private int gravity() {
            return gravity;
        }

        private int windowWidth() {
            return windowWidth;
        }
    }

    private int inlineEpisodeSpanCount() {
        int width = ResUtil.getScreenWidth(this);
        if (width >= ResUtil.dp2px(1200)) return 5;
        if (width >= ResUtil.dp2px(720)) return 4;
        return 3;
    }

    private boolean isSamePendingInlinePlayback(Episode episode) {
        return inlinePlaybackLoading && selectedEpisode != null && selectedEpisode.equals(episode);
    }

    private void selectInlineEpisode(Episode episode) {
        if (NovelRouter.route(this, getKeyText(), episode, vod, selectedFlag, () -> inlinePlay(episode))) return;
        inlinePlay(episode);
    }

    private void inlinePlay(Episode episode) {
        if (isSamePendingInlinePlayback(episode)) return;
        cancelPendingInlinePlayback();
        playbackSelectionTouched = true;
        selectedEpisode = episode;
        selectedSeasonNumber = seasonForEpisode(episode, selectedFlag.getEpisodes());
        resetEpisodeRange();
        renderSeasonSelection();
        fetchSeasonIfNeeded(selectedSeasonNumber);
        renderEpisodes();
        updatePlayLabel();
        onPlay();
    }

    private void toggleInlineFullscreen() {
        if (!isInlinePlayerMode()) return;
        if (inlineFullscreen) exitInlineFullscreen();
        else enterInlineFullscreen();
    }

    private void enterInlinePiP(boolean force) {
        if (!canEnterInlinePiP()) return;
        if (!force && !BackgroundPlaybackPolicy.isEnabled(PlayerSetting.getBackground())) return;
        hideInlineControls();
        hideInlineGestureOverlays();
        updateInlinePiPSource(binding.playerPanel);
        if (inlinePiP != null) inlinePiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getInlineScale(), force);
    }

    private boolean canEnterInlinePiP() {
        return canUseInlineSystemPiP() && isInlinePlayerMode() && inlineStarted && service() != null && player() != null && !player().isEmpty() && player().haveTrack(C.TRACK_TYPE_VIDEO);
    }

    private boolean canUseInlineSystemPiP() {
        return Util.isMobile() && !PiP.noPiP();
    }

    private void updateInlinePiPSource(View view) {
        if (!canUseInlineSystemPiP() || inlinePiP == null || view == null) return;
        inlinePiP.update(this, view);
    }

    private void updateInlinePiPActions(boolean playing) {
        if (!canUseInlineSystemPiP() || inlinePiP == null) return;
        inlinePiP.update(this, playing);
    }

    private void toggleInlineLock() {
        if (!Util.isMobile() || !inlineFullscreen) return;
        setLock(!isLock());
        if (inlineGestureDetector != null) inlineGestureDetector.setLock(isLock());
        if (isLock()) {
            setRequestedOrientation(PlaybackOrientation.getScreenOrientation(this));
            hideInlineGestureOverlays();
        } else {
            setInlineFullscreenOrientation();
        }
        showInlineControls(true, false);
    }

    private void rotateInlineFullscreen() {
        if (!Util.isMobile() || !inlineFullscreen || isLock()) return;
        touchInlineControls();
        setRequestedOrientation(ResUtil.isLand(this) ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        scheduleMobileInlineSideControlMarginUpdate();
    }

    private void enterInlineFullscreen() {
        if (inlineFullscreen || inlinePiPLayout || isInPictureInPictureMode()) return;
        inlineFullscreen = true;
        updateDetailThemeButtonVisibility();
        requestedOrientation = getRequestedOrientation();
        // playerPanel 已提升到 root 层，全屏只需铺满整个 root，不做 reparent（避免 SurfaceView 销毁重建导致黑屏）。
        applyInlinePlayerFullscreenLayout();
        binding.playerPanel.setTranslationY(0f);
        binding.playerPanel.setTranslationZ(32f);
        binding.playerPanel.setVisibility(View.VISIBLE);
        binding.playerPanel.setRadius(0);
        updatePlayerPanelFocus();
        setInlineFullscreenIcon();
        boolean playing = service() != null && !player().isEmpty() && player().isPlaying();
        updateInlineButtons(playing);
        hideInlineControls();
        syncInlinePauseInfo(playing);
        updateInlineDisplayPanel();
        binding.playerPanel.requestFocus();
        Util.toggleFullscreen(this, true);
        setInlineFullscreenOrientation();
        // 手动点全屏按钮不走 applyInlineShortDramaMode（那条只在 STATE_READY 触发），
        // 这里也要按新形态重算手势，否则短剧进全屏后仍是长视频那套轴向。
        syncInlineShortDramaGesture();
        scheduleMobileInlineSideControlMarginUpdate();
    }

    private boolean shouldShowDetailFullscreenControlsOnReady() {
        // 详情直放模式:只在首次准备完成时显示控制栏,快进/后退导致的 STATE_READY 不显示
        return detailPlayerActive && !isFusionMode() && inlineFullscreen && !isLock() && !inlineFirstReady;
    }

    private void applyInlineShortDramaMode() {
        if (!isShortDramaSource()) {
            resetInlineShortDramaMode();
            return;
        }
        if (inlinePiPLayout || isInPictureInPictureMode()) return;
        if (!inlineFullscreen) enterInlineFullscreen();
        if (!shouldUseInlineShortDramaMode()) {
            resetInlineShortDramaMode();
            setInlineFullscreenOrientation();
            return;
        }
        inlineShortDramaMode = true;
        syncInlineShortDramaGesture();
        setInlineFullscreenOrientation();
        setInlineShortDramaVideoFrame(!shouldUseShortDramaPortrait());
        setInlinePreviewScale(SHORT_DRAMA_SCALE);
        hideInlineControls();
    }

    /**
     * 手势轴向跟着呈现形态走：短剧内嵌全屏时整屏上下滑切集、长按后上下滑调亮度/音量。
     * <p>
     * 判据不用 {@code inlineShortDramaMode}：切集时 startInlinePlayback 会先
     * resetInlineShortDramaMode 再等 STATE_READY 重新 apply，那段缓冲窗口里形态并没变，
     * 手势却会退回长视频那套，连滑两集时第二次落在侧边就被当成调亮度。
     * shouldUseInlineShortDramaMode 在尺寸未知时返回 true，正好覆盖这段窗口；
     * 横屏短剧不走竖屏铺满形态，也就不换手势。
     */
    private void syncInlineShortDramaGesture() {
        if (inlineGestureDetector != null) inlineGestureDetector.setShortDrama(inlineFullscreen && shouldUseInlineShortDramaMode());
    }

    private void resetInlineShortDramaMode() {
        boolean restoreScale = inlineShortDramaMode;
        inlineShortDramaMode = false;
        syncInlineShortDramaGesture();
        setInlineShortDramaVideoFrame(false);
        if (restoreScale && inlineStarted) setInlineScale(getInlineScale());
    }

    private void setInlineFullscreenOrientation() {
        if (!inlineFullscreen) return;
        int orientation = getInlineFullscreenOrientation();
        if (getRequestedOrientation() != orientation) setRequestedOrientation(orientation);
    }

    private int getInlineFullscreenOrientation() {
        if (shouldUseInlineShortDramaMode()) return shouldUseShortDramaPortrait() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
        boolean portrait = service() != null && !player().isEmpty() && player().isPortrait();
        return portrait ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE;
    }

    private boolean shouldUseInlineShortDramaMode() {
        if (!isShortDramaSource()) return false;
        if (!hasInlineVideoSize()) return true;
        return player().isPortrait();
    }

    private boolean shouldUseShortDramaPortrait() {
        return Util.isMobile() && !ResUtil.isPad();
    }

    private boolean hasInlineVideoSize() {
        return service() != null && player() != null && !player().isEmpty() && player().getVideoWidth() > 0 && player().getVideoHeight() > 0;
    }

    private void setInlineShortDramaVideoFrame(boolean enabled) {
        if (binding == null) return;
        if (!enabled) {
            setInlineVideoFrame(binding.exo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            setInlineVideoFrame(binding.danmaku, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            return;
        }
        int width = binding.playerPanel.getWidth();
        int height = binding.playerPanel.getHeight();
        if (width <= 0 || height <= 0) {
            binding.playerPanel.post(() -> {
                if (inlineShortDramaMode && inlineFullscreen && binding != null) setInlineShortDramaVideoFrame(true);
            });
            return;
        }
        int frameWidth;
        int frameHeight;
        if (width * SHORT_DRAMA_FRAME_HEIGHT > height * SHORT_DRAMA_FRAME_WIDTH) {
            frameHeight = height;
            frameWidth = Math.max(1, height * SHORT_DRAMA_FRAME_WIDTH / SHORT_DRAMA_FRAME_HEIGHT);
        } else {
            frameWidth = width;
            frameHeight = Math.max(1, width * SHORT_DRAMA_FRAME_HEIGHT / SHORT_DRAMA_FRAME_WIDTH);
        }
        setInlineVideoFrame(binding.exo, frameWidth, frameHeight);
        setInlineVideoFrame(binding.danmaku, frameWidth, frameHeight);
    }

    private void setInlineVideoFrame(View view, int width, int height) {
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, height);
        params.gravity = Gravity.CENTER;
        view.setLayoutParams(params);
    }

    /**
     * playerPanel 已提升到 root 层。spacer 在 scroll 内占位，playerPanel 通过 translationY 对齐 spacer，
     * 从而视觉上跟随 scroll 滚动。监听 scroll 与 spacer 布局变化，实时同步位置。
     */
    private void setupInlinePlayerSpacerSync() {
        if (binding == null || binding.playerPanelSpacer == null) return;
        // playerPanel 常驻 root，进入页面即套用内嵌尺寸；全屏时才切换到铺满布局。
        if (!inlineFullscreen && !inlinePiPLayout) applyInlinePlayerEmbeddedLayout();
        binding.scroll.setOnScrollChangeListener((androidx.core.widget.NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> syncInlinePlayerToSpacer());
        binding.playerPanelSpacer.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> syncInlinePlayerToSpacer());
    }

    /**
     * playerPanel 常驻 root(FrameLayout) 层。在内嵌模式下，我们通过 spacer 占位 + translationY 让 playerPanel
     * 视觉上跟随 scroll 滚动；全屏模式下把 LayoutParams 铺满整个 root。这样 SurfaceView 全程不被 reparent，
     * 避免 Surface 销毁重建导致的黑屏。
     */
    private void applyInlinePlayerEmbeddedLayout() {
        if (binding == null) return;
        // 融合模式上下 margin (22dp/20dp) 比普通模式 (14dp/16dp) 略大
        int topMarginDp = isFusionMode() ? 22 : 14;
        int bottomMarginDp = isFusionMode() ? 20 : 16;
        // TV 版左右贴边（margin=0），mobile 版左右留白 16dp
        int horizontalMarginDp = Util.isLeanback() ? 0 : 16;
        FrameLayout.LayoutParams params;
        ViewGroup.LayoutParams current = binding.playerPanel.getLayoutParams();
        if (current instanceof FrameLayout.LayoutParams framed) params = framed;
        else params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(252));
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ResUtil.dp2px(252);
        params.gravity = Gravity.TOP | Gravity.START;
        // 融合模式播放窗口由 translationY 浮动在按钮行上方（中上部），故 LayoutParams 顶部边距置 0，
        // 避免与 translationY 叠加；非融合模式由 XML/spacer 跟随，保留 topMarginDp。
        int playerTopDp = isFusionMode() ? 0 : topMarginDp;
        params.setMargins(ResUtil.dp2px(horizontalMarginDp), ResUtil.dp2px(playerTopDp), ResUtil.dp2px(horizontalMarginDp), ResUtil.dp2px(bottomMarginDp));
        // XML 里的 layout_marginStart/End=16dp 在 RTL 解析时会覆盖 left/right，需显式清零
        params.setMarginStart(ResUtil.dp2px(horizontalMarginDp));
        params.setMarginEnd(ResUtil.dp2px(horizontalMarginDp));
        binding.playerPanel.setLayoutParams(params);
        // 融合模式：把 scroll 区域整体下移到播放窗口下方，scroll 内容(按钮/选集)永不进入播放窗口区；
        // 非融合模式：scroll 铺满，由 scroll 内的 spacer 占位把内容顶到播放窗口下方并随滚动跟随。
        if (binding.scroll != null) {
            ViewGroup.MarginLayoutParams scrollParams = (ViewGroup.MarginLayoutParams) binding.scroll.getLayoutParams();
            if (isFusionMode()) {
                int playerOccupied = ResUtil.dp2px(topMarginDp) + ResUtil.dp2px(252) + ResUtil.dp2px(bottomMarginDp);
                scrollParams.topMargin = playerOccupied;
            } else {
                scrollParams.topMargin = 0;
            }
            binding.scroll.setLayoutParams(scrollParams);
        }
        // 内嵌 spacer 顶部对齐时，translationY 由 syncInlinePlayerToSpacer() 依据 spacer 位置更新
        alignInlinePlayerSpacerHeight();
        // post 确保在布局完成后（playerPanel 高度已知）再同步融合模式的固定位置
        binding.playerPanel.post(this::syncInlinePlayerToSpacer);
    }

    private void applyInlinePlayerFullscreenLayout() {
        if (binding == null) return;
        FrameLayout.LayoutParams params;
        ViewGroup.LayoutParams current = binding.playerPanel.getLayoutParams();
        if (current instanceof FrameLayout.LayoutParams framed) params = framed;
        else params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.width = ViewGroup.LayoutParams.MATCH_PARENT;
        params.height = ViewGroup.LayoutParams.MATCH_PARENT;
        params.gravity = Gravity.TOP | Gravity.START;
        params.setMargins(0, 0, 0, 0);
        // playerPanel 的 XML 用了 layout_marginStart/End=16dp，setMargins 只改 left/right，
        // RTL 解析时 start/end 会覆盖 left/right，导致全屏左右残留 16dp 黑边，需显式清零。
        params.setMarginStart(0);
        params.setMarginEnd(0);
        binding.playerPanel.setLayoutParams(params);
    }

    private void alignInlinePlayerSpacerHeight() {
        if (binding == null || binding.playerPanelSpacer == null) return;
        ViewGroup.LayoutParams sp = binding.playerPanelSpacer.getLayoutParams();
        if (sp == null) return;
        // 融合模式下 scroll 已整体下移到播放窗口下方，spacer 高度置 0 使按钮/选集等内容整体上移、
        // 紧贴播放窗口底部（不再与播放窗口间隔 40dp）；非融合模式 spacer 占满播放窗口高度并随滚动跟随。
        boolean fusion = isFusionMode();
        int target = ResUtil.dp2px(fusion ? 0 : 252);
        int topMargin = ResUtil.dp2px(fusion ? 0 : 14);
        int bottomMargin = ResUtil.dp2px(fusion ? 0 : 16);
        boolean changed = sp.height != target;
        if (sp instanceof ViewGroup.MarginLayoutParams marginParams) {
            if (marginParams.topMargin != topMargin || marginParams.bottomMargin != bottomMargin) {
                marginParams.topMargin = topMargin;
                marginParams.bottomMargin = bottomMargin;
                changed = true;
            }
        }
        if (changed) {
            sp.height = target;
            binding.playerPanelSpacer.setLayoutParams(sp);
        }
    }

    /**
     * 计算 spacer 顶端相对 root 的 y，把 playerPanel 的 translationY 对齐过去。全屏/PiP 时不同步。
     * 融合/沉浸模式下，播放窗口常驻置顶，不随详情滚动（translationY 固定为 0）。
     */
    private void syncInlinePlayerToSpacer() {
        if (binding == null || inlineFullscreen || inlinePiPLayout) return;
        if (binding.playerPanel.getVisibility() != View.VISIBLE) return;
        // 融合模式：播放窗口浮动在"换源/收藏/TMDB/深色"按钮行上方(mid-upper)，不随滚动移动；
        // scroll 已整体下移到播放窗口下方占位，故按钮/选集等内容永远在播放窗口之下，不会被遮挡。
        if (isFusionMode()) {
            if (binding.playerPanel.getHeight() > 0) {
                int gap = ResUtil.dp2px(0);
                int lift = ResUtil.dp2px(10); // 播放窗口整体上移 10dp
                // scroll.getTop()(含下移占位) + fusionActions.getTop() = 按钮行屏幕绝对 y，不随滚动变化
                float target = binding.scroll.getTop() + binding.fusionActions.getTop()
                        - binding.playerPanel.getHeight() - gap - lift;
                if (Math.abs(binding.playerPanel.getTranslationY() - target) > 0.5f) {
                    binding.playerPanel.setTranslationY(target);
                }
            }
            return;
        }
        View spacer = binding.playerPanelSpacer;
        if (spacer == null || spacer.getWidth() <= 0) return;
        int[] rootLoc = new int[2];
        int[] spacerLoc = new int[2];
        int[] playerLoc = new int[2];
        binding.root.getLocationInWindow(rootLoc);
        spacer.getLocationInWindow(spacerLoc);
        binding.playerPanel.getLocationInWindow(playerLoc);
        // spacer 在 root 中的 y 坐标（考虑 scroll）
        float spacerY = spacerLoc[1] - rootLoc[1];
        // playerPanel 当前 layout 位置（不含 translationY）在 root 中的 y
        float playerLayoutY = playerLoc[1] - rootLoc[1] - binding.playerPanel.getTranslationY();
        // 需要的 translationY = spacer 位置 - player layout 位置
        float target = spacerY - playerLayoutY;
        if (Math.abs(binding.playerPanel.getTranslationY() - target) > 0.5f) {
            binding.playerPanel.setTranslationY(target);
        }
    }

    private void restoreInlineDetailScrollAfterOverlay() {
        if (binding == null || !isInlinePlayerMode()) return;
        binding.scroll.scrollTo(0, 0);
    }

    private void restoreInlinePlayerPanelAfterOverlay() {
        if (binding == null) return;
        restoreInlineDetailScrollAfterOverlay();
        setInlineVideoFrame(binding.exo, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        setInlineVideoFrame(binding.danmaku, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        binding.playerPanel.setTranslationZ(0f);
        binding.playerPanel.setVisibility(View.VISIBLE);
        setPlayerCard(lightTheme ? ThemeColors.light() : ThemeColors.dark());
        binding.playerPanel.requestLayout();
        binding.exo.requestLayout();
        View surface = binding.exo.getVideoSurfaceView();
        if (surface != null) surface.requestLayout();
        binding.danmaku.requestLayout();
        binding.scroll.requestLayout();
        setNightMode();
    }

    private void scheduleInlinePlayerPanelRestoreAfterOverlay() {
        if (binding == null) return;
        binding.playerPanel.post(() -> {
            if (binding == null) return;
            restoreInlineDetailScrollAfterOverlay();
            syncInlinePlayerToSpacer();
        });
        binding.root.postDelayed(() -> {
            if (binding == null) return;
            restoreInlineDetailScrollAfterOverlay();
            syncInlinePlayerToSpacer();
        }, 180);
    }

    private void exitInlineFullscreen() {
        if (!inlineFullscreen) return;
        boolean suppressDisplay = suppressInlineDisplayForLeanbackFusionExit();
        prepareInlinePlayerTransition();
        inlineFullscreen = false;
        setInlineLock(false);
        // 恢复 root 层内嵌尺寸，不再 reparent
        applyInlinePlayerEmbeddedLayout();
        resetInlineShortDramaMode();
        restoreInlinePlayerPanelAfterOverlay();
        setInlineFullscreenIcon();
        boolean playing = service() != null && !player().isEmpty() && player().isPlaying();
        updateInlineButtons(playing);
        if (!suppressDisplay) updateInlineDisplayPanel();
        Util.toggleFullscreen(this, false);
        updateMobileInlineSideControlMargins();
        setRequestedOrientation(requestedOrientation);
        focusInlinePlayerPanel();
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        updateDetailThemeButtonVisibility();
    }

    private void enterInlinePiPLayout() {
        if (inlinePiPLayout || inlineFullscreen || !inlineStarted || binding == null) return;
        inlinePiPTranslationZ = binding.playerPanel.getTranslationZ();
        inlinePiPLayout = true;
        updateDetailThemeButtonVisibility();
        // Keep the original source rect so PiP exits back to the inline card.
        inlinePiPSourceFrozen = true;
        // 不再 reparent：playerPanel 常驻 root，PiP 时直接铺满
        applyInlinePlayerFullscreenLayout();
        binding.playerPanel.setTranslationY(0f);
        binding.playerPanel.setTranslationZ(32f);
        binding.playerPanel.setVisibility(View.VISIBLE);
        binding.playerPanel.setRadius(0);
        updatePlayerPanelFocus();
        hideInlineDisplayPanel();
    }

    private void exitInlinePiPLayout() {
        if (!inlinePiPLayout || binding == null) return;
        inlinePiPLayout = false;
        // 恢复 root 层内嵌尺寸，不再 reparent
        applyInlinePlayerEmbeddedLayout();
        restoreInlinePlayerPanelAfterOverlay();
        binding.playerPanel.setTranslationZ(inlinePiPTranslationZ);
        updateInlineDisplayPanel();
        focusInlinePlayerPanel();
        inlinePiPTranslationZ = 0f;
        binding.playerPanel.post(() -> {
            if (binding == null) return;
            inlinePiPSourceFrozen = false;
            updateInlinePiPSource(binding.playerPanel);
        });
        updateDetailThemeButtonVisibility();
    }

    private void scheduleMobileInlineSideControlMarginUpdate() {
        updateMobileInlineSideControlMargins();
        if (binding != null && detailControlRoot != null) detailControlView(R.id.danmaku, View.class).postDelayed(this::updateMobileInlineSideControlMargins, 350);
    }

    private void updateMobileInlineSideControlMargins() {
        if (!Util.isMobile() || detailControlRoot == null) return;
        View danmaku = detailControlView(R.id.danmaku, View.class);
        View danmakuParent = danmaku.getParent() instanceof View ? (View) danmaku.getParent() : danmaku;
        int margin = ResUtil.dp2px(inlineFullscreen && ResUtil.isLand(this) ? INLINE_SIDE_CONTROL_FULLSCREEN_MARGIN_DP : INLINE_SIDE_CONTROL_MARGIN_DP);
        setStartMargin(danmakuParent, margin);
        setEndMargin(detailControlView(R.id.right, View.class), margin);
    }

    private void setStartMargin(View view, int margin) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.setMarginStart(margin);
        view.setLayoutParams(marginParams);
    }

    private void setEndMargin(View view, int margin) {
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams marginParams)) return;
        marginParams.setMarginEnd(margin);
        view.setLayoutParams(marginParams);
    }

    private void setInlineLock(boolean lock) {
        setLock(lock);
        if (inlineGestureDetector != null) inlineGestureDetector.setLock(lock);
    }

    private void closeDetailFullscreenPlayer() {
        saveInlineHistory();
        stopInlinePlaybackSync();
        inlinePlaybackGeneration++;
        inlinePlaybackLoading = false;
        inlinePlayerSwitchLoading = false;
        introSkipPlayback.reset();
        subtitlePlaybackSession.stop(this);
        hideInlineControls();
        if (service() != null) {
            player().stop();
            player().clear();
        }
        hideInlineLoading();
        binding.playerError.setVisibility(View.GONE);
        updateInlineDisplayPanel();
        binding.playerPanel.setVisibility(View.GONE);
        binding.playerPanelSpacer.setVisibility(View.GONE); // 同步隐藏 spacer
        inlineStarted = false;
        detailPlayerActive = false;
        pendingInlineResult = null;
        currentInlineResult = null;
        inlinePlaybackEpisode = null;
        inlinePlaybackKey = "";
        inlinePlaybackFlag = "";
        useParse = false;
        updateInlineButtons(false);
        DanmakuApi.cancel();
        updatePlayLabel();
    }

    private void playAdjacentEpisode(int direction) {
        playAdjacentEpisode(direction, true);
    }

    private boolean playAdjacentEpisode(int direction, boolean notify) {
        if (selectedFlag == null || selectedEpisode == null || selectedFlag.getEpisodes() == null) return false;
        List<Episode> episodes = selectedFlag.getEpisodes();
        int index = episodes.indexOf(selectedEpisode);
        int next = index + direction;
        while (index >= 0 && next >= 0 && next < episodes.size() && isSameEpisodeSlot(selectedEpisode, episodes.get(next), episodes)) next += direction;
        if (index < 0 || next < 0 || next >= episodes.size()) {
            if (notify) Notify.show(direction > 0 ? R.string.error_play_next : R.string.error_play_prev);
            return false;
        }
        cancelPendingInlinePlayback();
        playbackSelectionTouched = true;
        selectedEpisode = episodes.get(next);
        selectedSeasonNumber = seasonForEpisode(selectedEpisode, episodes);
        resetEpisodeRange();
        renderEpisodes();
        onPlay();
        return true;
    }

    private boolean isSameEpisodeSlot(Episode left, Episode right, List<Episode> episodes) {
        EpisodePosition leftPosition = episodePosition(left, episodes);
        EpisodePosition rightPosition = episodePosition(right, episodes);
        return leftPosition.number() > 0
                && leftPosition.number() == rightPosition.number()
                && leftPosition.season() == rightPosition.season();
    }

    private void checkInlineNext() {
        checkInlineNext(true);
    }

    private void checkInlineNext(boolean notify) {
        advanceInlineEpisode(notify);
    }

    /** @return 是否真的切走了。末集/倒序首集切不动，调用方据此决定要不要提示「进入下一集」。 */
    private boolean advanceInlineEpisode(boolean notify) {
        return history != null && history.isRevPlay() ? onInlinePrev(notify) : onInlineNext(notify);
    }

    private void checkInlinePrev() {
        if (history != null && history.isRevPlay()) onInlineNext(true);
        else onInlinePrev(true);
    }

    private boolean onInlineNext(boolean notify) {
        if (playAdjacentEpisode(1, false)) return true;
        if (notify) Notify.show(history != null && history.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
        return false;
    }

    private boolean onInlinePrev(boolean notify) {
        if (playAdjacentEpisode(-1, false)) return true;
        if (notify) Notify.show(history != null && history.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
        return false;
    }

    private void checkInlineEnded(boolean notify) {
        checkInlineNext(notify);
    }

    private MediaMetadata buildMetadata() {
        String title = playbackHistoryName();
        String episode = selectedEpisode != null ? historyEpisodeTitle(selectedEpisode) : "";
        String artist = TextUtils.isEmpty(episode) || title.equals(episode) ? "" : episode;
        return new MediaMetadata.Builder().setTitle(title).setArtist(artist).setArtworkUri(android.net.Uri.parse(playbackHistoryPic())).build();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (binding != null && isInlineControlsVisible()) touchInlineControls();
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (binding != null && KeyUtil.isActionUp(event) && KeyUtil.isBackKey(event) && binding.lutQuick.hideIfVisible()) return true;
        if (binding != null && isVisible(binding.lutQuick)) return dispatchInlineLutQuickKey(event);
        if (handleInlineFullscreenHiddenKey(event)) return true;
        if (handleDetailEpisodeNavigationKey(event)) return true;
        if (handleInlineKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private boolean handleDetailEpisodeNavigationKey(KeyEvent event) {
        if (binding == null || event == null || !isDetailEpisodeNavigationKey(event)) return false;
        View focus = getCurrentFocus();
        if (focus == null) return false;
        if (isFocusInside(focus, binding.flagScroll)) return onDetailFlagKey(event.getKeyCode(), event);
        if (isEpisodeToolButton(focus)) return onDetailEpisodeToolKey(focus, event.getKeyCode(), event);
        if (isFocusInside(focus, binding.episodeRangeScroll)) return onDetailEpisodeRangeKey(focus, event.getKeyCode(), event);
        if (isFocusInside(focus, binding.episodeContainer)) return onDetailEpisodeContainerKey(focus, event);
        RecyclerView tmdbRow = detailTmdbRecyclerContainingFocus(focus);
        if (tmdbRow != null) return onDetailTmdbRowKey(tmdbRow, focus, event);
        if (isFocusInside(focus, binding.headerBar)) return onDetailHorizontalButtonGroupKey(binding.headerBar, null, focus, event);
        if (isFocusInside(focus, binding.fusionActions)) return onDetailHorizontalButtonGroupKey(binding.fusionActions, null, focus, event);
        if (isFocusInside(focus, binding.detailActions)) return onDetailHorizontalButtonGroupKey(binding.detailActions, null, focus, event);
        if (isFocusInside(focus, binding.seasonContainer)) return onDetailSeasonKey(focus, event);
        if (isFocusInside(focus, binding.externalLinksContainer)) return onDetailExternalLinksKey(focus, event);
        return false;
    }

    private boolean isDetailEpisodeNavigationKey(KeyEvent event) {
        return KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    private boolean onDetailHorizontalButtonGroupKey(ViewGroup group, @Nullable HorizontalScrollView scroll, View focus, KeyEvent event) {
        if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        View target = horizontalFocusTarget(group, focus, KeyUtil.isLeftKey(event));
        if (target == null) return true;
        scrollDetailChildIntoViewNow(target, 12);
        target.requestFocus(KeyUtil.isLeftKey(event) ? View.FOCUS_LEFT : View.FOCUS_RIGHT);
        scrollHorizontalChildIntoView(scroll, target);
        return true;
    }

    private boolean onDetailExternalLinksKey(View focus, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event) && !KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event)) return true;
        if (KeyUtil.isUpKey(event) && detailFocusableIndex(binding.externalLinksContainer, focus) == 0) {
            if (focusLastVisibleTmdbRow()) return true;
            return false;
        }
        return moveDetailFocusVertically(binding.externalLinksContainer, focus, KeyUtil.isUpKey(event));
    }

    private View horizontalFocusTarget(ViewGroup group, View focus, boolean left) {
        if (group == null || focus == null) return null;
        List<View> focusables = new ArrayList<>();
        collectDetailFocusableViews(group, focusables);
        View current = focusedDetailView(focusables, focus);
        if (current == null) return null;
        Rect currentRect = rectInAncestor(group, current);
        View best = null;
        int bestDistance = Integer.MAX_VALUE;
        int currentCenter = currentRect.centerX();
        for (View candidate : focusables) {
            if (candidate == current) continue;
            Rect rect = rectInAncestor(group, candidate);
            if (!isSameVisualRow(currentRect, rect)) continue;
            int delta = rect.centerX() - currentCenter;
            if (left ? delta >= 0 : delta <= 0) continue;
            int distance = Math.abs(delta);
            if (distance >= bestDistance) continue;
            bestDistance = distance;
            best = candidate;
        }
        return best;
    }

    private boolean moveDetailFocusVertically(ViewGroup group, View focus, boolean up) {
        int index = detailFocusableIndex(group, focus);
        List<View> focusables = new ArrayList<>();
        collectDetailFocusableViews(group, focusables);
        int targetIndex = index + (up ? -1 : 1);
        if (index < 0 || targetIndex < 0 || targetIndex >= focusables.size()) return true;
        View target = focusables.get(targetIndex);
        scrollDetailChildIntoViewNow(target, 12);
        target.requestFocus(up ? View.FOCUS_UP : View.FOCUS_DOWN);
        return true;
    }

    private int detailFocusableIndex(ViewGroup group, View focus) {
        if (group == null || focus == null) return -1;
        List<View> focusables = new ArrayList<>();
        collectDetailFocusableViews(group, focusables);
        View current = focusedDetailView(focusables, focus);
        return current == null ? -1 : focusables.indexOf(current);
    }

    private void collectDetailFocusableViews(View view, List<View> focusables) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isShown() || !view.isEnabled()) return;
        if (view.isFocusable()) {
            focusables.add(view);
            return;
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) collectDetailFocusableViews(group.getChildAt(i), focusables);
    }

    @Nullable
    private View focusedDetailView(List<View> focusables, View focus) {
        for (View candidate : focusables) {
            if (candidate == focus || isFocusInside(focus, candidate)) return candidate;
        }
        return null;
    }

    private Rect rectInAncestor(ViewGroup ancestor, View child) {
        Rect rect = new Rect();
        child.getDrawingRect(rect);
        ancestor.offsetDescendantRectToMyCoords(child, rect);
        return rect;
    }

    private boolean isSameVisualRow(Rect first, Rect second) {
        return first.bottom > second.top && second.bottom > first.top;
    }

    private void scrollHorizontalChildIntoView(@Nullable HorizontalScrollView scroll, View child) {
        if (scroll == null || child == null || scroll.getWidth() <= 0) return;
        Rect rect = new Rect();
        child.getDrawingRect(rect);
        scroll.offsetDescendantRectToMyCoords(child, rect);
        int padding = ResUtil.dp2px(12);
        int currentX = scroll.getScrollX();
        int left = currentX + padding;
        int right = currentX + scroll.getWidth() - padding;
        int targetX = currentX;
        if (rect.left < left) targetX += rect.left - left;
        else if (rect.right > right) targetX += rect.right - right;
        if (targetX == currentX) return;
        scroll.smoothScrollTo(Math.max(0, targetX), 0);
    }

    private RecyclerView detailTmdbRecyclerContainingFocus(View focus) {
        if (isFocusInside(focus, binding.episodePhotoList)) return binding.episodePhotoList;
        if (isFocusInside(focus, binding.castList)) return binding.castList;
        if (isFocusInside(focus, binding.creatorList)) return binding.creatorList;
        if (isFocusInside(focus, binding.relatedList)) return binding.relatedList;
        if (isFocusInside(focus, binding.personalTmdbList)) return binding.personalTmdbList;
        if (isFocusInside(focus, binding.personalDoubanList)) return binding.personalDoubanList;
        if (isFocusInside(focus, binding.personalAiList)) return binding.personalAiList;
        return null;
    }

    private boolean onDetailTmdbRowKey(RecyclerView recycler, View focus, KeyEvent event) {
        if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        RecyclerView.ViewHolder holder = recycler.findContainingViewHolder(focus);
        if (adapter == null || holder == null) return true;
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return true;
        int target = KeyUtil.isLeftKey(event) ? position - 1 : position + 1;
        if (target < 0 || target >= adapter.getItemCount()) return true;
        focusTmdbRecyclerItem(recycler, target);
        return true;
    }

    private boolean focusTmdbRecyclerItem(RecyclerView recycler, int position) {
        if (binding == null || recycler == null || recycler.getVisibility() != View.VISIBLE) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() == 0) return false;
        int target = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        recycler.stopScroll();
        RecyclerView.ViewHolder visibleHolder = recycler.findViewHolderForAdapterPosition(target);
        if (visibleHolder != null) {
            visibleHolder.itemView.requestFocus();
            return true;
        }
        recycler.scrollToPosition(target);
        recycler.post(() -> {
            RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(target);
            if (holder != null) holder.itemView.requestFocus();
        });
        return true;
    }

    private boolean onDetailEpisodeContainerKey(View focus, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event) && !KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        RecyclerView.ViewHolder holder = binding.episodeContainer.findContainingViewHolder(focus);
        if (holder == null) return true;
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return true;
        return moveDetailEpisodeFocus(position, event);
    }

    private boolean isFocusInside(View focus, View parent) {
        for (View current = focus; current != null; ) {
            if (current == parent) return true;
            Object next = current.getParent();
            current = next instanceof View ? (View) next : null;
        }
        return false;
    }

    private boolean handleInlineKey(KeyEvent event) {
        if (!isInlinePlayerMode()) return false;
        if (KeyUtil.isBackKey(event) && binding.gestureSeek.getVisibility() == View.VISIBLE) {
            if (KeyUtil.isActionUp(event)) hideInlineGestureOverlays();
            return true;
        }
        if (KeyUtil.isBackKey(event) && isLock() && inlineFullscreen) {
            if (KeyUtil.isActionUp(event)) showInlineControls(true, false);
            return true;
        }
        if (KeyUtil.isBackKey(event) && isInlineControlsVisible()) {
            if (KeyUtil.isActionUp(event)) hideInlineControls();
            return true;
        }
        if (KeyUtil.isBackKey(event) && Util.isLeanback() && inlineFullscreen) {
            if (KeyUtil.isActionUp(event)) backFromInlineFullscreen();
            return true;
        }
        if (isInlineControlsVisible()) {
            setInlineHideCallback();
            if (handleInlineControlFocusKey(event)) return true;
            rememberInlineControlFocus();
        }
        if (handleInlineSeekKey(event)) return true;
        if (KeyUtil.isMenuKey(event) && (inlineFullscreen || isInlineControlsVisible())) {
            if (isInlineControlsVisible()) hideInlineControls();
            else if (Setting.getFullscreenMenuKey() == 1) showInlineEpisodes();
            else showInlineControls(true);
            return true;
        }
        if (!inlineFullscreen || isInlineControlsVisible()) return false;
        if (isLock()) {
            if (KeyUtil.isActionUp(event)) showInlineControls(true, false);
            return true;
        }
        if (KeyUtil.isEnterKey(event)) {
            if (KeyUtil.isActionUp(event)) {
                if (Util.isLeanback()) toggleInlinePlayback();
                else showInlineControls(true);
            }
            return true;
        }
        if (!inlineStarted || service() == null || player() == null || player().isEmpty()) return false;
        if (event.isLongPress() && KeyUtil.isUpKey(event)) {
            onSpeedUp();
            inlineKeySpeedChanging = true;
            return true;
        }
        if (!KeyUtil.isActionUp(event)) return false;
        if (KeyUtil.isUpKey(event)) {
            if (inlineKeySpeedChanging) onSpeedEnd();
            else showInlineControls(true);
            inlineKeySpeedChanging = false;
            return true;
        }
        if (KeyUtil.isDownKey(event)) {
            showInlineControls(true);
            return true;
        }
        return false;
    }

    private boolean handleInlineFullscreenHiddenKey(KeyEvent event) {
        if (!isInlineFullscreenHiddenPlaybackKey(event)) return false;
        if (handleInlineKey(event)) return true;
        return true;
    }

    private boolean isInlineFullscreenHiddenPlaybackKey(KeyEvent event) {
        if (event == null || !isInlinePlayerMode() || !inlineFullscreen || isInlineControlsVisible()) return false;
        return KeyUtil.isEnterKey(event) || KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    private boolean handleInlineControlFocusKey(KeyEvent event) {
        return PlayerControlFocusHelper.handleKey(inlineControlsView(), getInlineControlFocus(), event);
    }

    private boolean handleInlineSeekKey(KeyEvent event) {
        if (!canInlineKeySeek(event)) return false;
        if (KeyUtil.isActionDown(event)) {
            inlineKeySeekTime += KeyUtil.isSeekBackKey(event) ? -Constant.INTERVAL_SEEK : Constant.INTERVAL_SEEK;
            onSeeking(inlineKeySeekTime);
            return true;
        }
        if (KeyUtil.isActionUp(event)) {
            if (inlineKeySeekTime == 0) inlineKeySeekTime = KeyUtil.isSeekBackKey(event) ? -Constant.INTERVAL_SEEK : Constant.INTERVAL_SEEK;
            App.post(inlineKeySeekEnd, 250);
            return true;
        }
        return true;
    }

    private boolean canInlineKeySeek(KeyEvent event) {
        if (!canInlineSeek() || !isInlineSeekKey(event)) return false;
        if (isInlineMediaSeekKey(event)) return true;
        if (isInlineControlsVisible()) return false;
        if (inlineFullscreen) return true;
        View focus = getCurrentFocus();
        return focus == binding.playerPanel || (inlineFullscreen && (focus == null || isFocusInside(focus, binding.playerPanel)));
    }

    private boolean canInlineSeek() {
        return isInlinePlayerMode() && inlineStarted && service() != null && controller() != null && player() != null && !player().isEmpty();
    }

    private boolean isInlineSeekKey(KeyEvent event) {
        return KeyUtil.isSeekBackKey(event) || KeyUtil.isSeekForwardKey(event);
    }

    private boolean isInlineMediaSeekKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
    }

    private void onInlineKeySeekEnd() {
        long time = inlineKeySeekTime;
        inlineKeySeekTime = 0;
        onSeekEnd(time);
        hideInlineGestureOverlays();
    }

    /**
     * 播放失败回退（软解后切核心）在引擎侧换内核/解码后只发 onPlayerRebuild，
     * 不走 onPrepare，内嵌播放器的内核与解码标签必须在这里补刷，否则会一直停在起播时的值。
     *
     * 必须放在 onPrepare 之前：TmdbDetailActivityLayoutTest 用
     * [onPrepare, getInlineResumePosition) 的源码切片断言 onPrepare 的内容，
     * 插在两者之间会把本方法并进那段切片里。
     */
    @Override
    protected void onPlayerRebuilt() {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;
        updateInlineButtons(service() != null && !player().isEmpty() && player().isPlaying());
        refreshInlineControlDialog();
    }

    /**
     * 内嵌设置弹窗只在 initView 时从宿主抄一次标签，回退期间开着弹窗就会一直显示旧的内核/解码。
     * 弹窗在 mobile flavor 里，main 源集只能反射调用，与 showInlineControlDialog 保持一致。
     */
    private void refreshInlineControlDialog() {
        try {
            Class<?> dialogClass = Class.forName("com.fongmi.android.tv.ui.dialog.ControlDialog");
            for (androidx.fragment.app.Fragment fragment : getSupportFragmentManager().getFragments()) {
                if (dialogClass.isInstance(fragment)) dialogClass.getMethod("setPlayer").invoke(fragment);
            }
        } catch (Throwable e) {
            // 只是标签刷新，失败不该影响播放；但要留痕，否则将来 setPlayer 抛异常会被永久静默成"标签不刷新"。
            SpiderDebug.log("tmdb-inline", "refresh control dialog failed errorType=%s", e.getClass().getSimpleName());
        }
    }

    @Override
    protected void onPrepare() {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;
        setInlineScale(getInlineScale());
        prepareInlineStartPosition();
        if (history != null && service() != null && !player().isEmpty()) setInlineSpeed(getInlinePlaybackSpeed());
    }

    private long getInlineResumePosition() {
        if (history == null) return C.TIME_UNSET;
        return Math.max(history.getOpening(), history.getPosition());
    }

    /**
     * 与 mobile/leanback 两条链路对齐：进度已贴近片尾时视为该集看完，重置为从头开始，
     * 否则续播会把用户直接送到结尾并立刻触发下一集。
     *
     * 只能在真正准备起播时调用一次。不可并入 getInlineResumePosition()——后者经
     * getInlineStartPosition() 被 onTimeChanged 每秒调用（见 canUpdateProgress），
     * 在查询路径上重置进度并写库会造成反复清进度和重复 IO。
     */
    private void resetInlineHistoryIfNearEnding() {
        if (history == null || !history.isNearEnding()) return;
        SpiderDebug.log("tmdb-inline", "reset near-end history position=%d duration=%d key=%s", history.getPosition(), history.getDuration(), getHistoryKey());
        history.resetPlaybackPosition();
        if (!Setting.isIncognito()) Task.execute(() -> history.save());
    }

    private long getInlineStartPosition() {
        return inlineStartPosition != C.TIME_UNSET ? inlineStartPosition : getInlineResumePosition();
    }

    private void prepareInlineStartPosition() {
        long position = getInlineStartPosition();
        if (position > 0) introSkipPlayback.setResumePosition(position);
        if (position > 0 && service() != null && player() != null && !player().isEmpty()) player().seekTo(position);
    }

    /** @return 本次是否真的下发了续播 seek——落点还没生效前不能让自动跳过介入。 */
    private boolean applyInlineStartPosition() {
        if (inlineStartPositionApplied || history == null || controller() == null) return false;
        long position = getInlineStartPosition();
        inlineStartPositionApplied = true;
        if (position <= 0) return false;
        introSkipPlayback.setResumePosition(position);
        controller().seekTo(position);
        return true;
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        if (inlineStarted && currentInlineResult != null) {
            saveInlineHistory();
            inlinePlaybackReconnectPending = true;
        }
        super.onServiceDisconnected(name);
    }

    @Override
    protected void onServiceConnected() {
        ensureInlineDanmakuController();
        restoreInlinePlaybackIfNeeded();
        startPendingInlinePlayer();
    }

    @Override
    protected void onControllerConnected() {
        restoreInlinePlaybackIfNeeded();
        startPendingInlinePlayer();
    }

    private void startPendingInlinePlayer() {
        if (pendingInlineResult == null || service() == null || controller() == null) return;
        startInlinePlayer(pendingInlineResult);
    }

    private void restoreInlinePlaybackIfNeeded() {
        if (!inlinePlaybackReconnectPending || service() == null || controller() == null || player() == null) return;
        inlinePlaybackReconnectPending = false;
        if (currentInlineResult != null && player().isEmpty()) {
            startInlinePlayer(currentInlineResult, getInlineResumePosition());
        }
    }

    private void ensureInlineDanmakuController() {
        if (service() == null || inlineControlController == null) return;
        player().setDanmakuController(binding.exo.getDanmakuController());
        inlineControlController.applyDanmakuSetting();
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;
        syncInlinePauseInfo(isPlaying);
        updateInlinePiPActions(isPlaying);
        updateInlineButtons(isPlaying);
        updateInlineDisplayPanel();
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        super.onPlayWhenReadyChanged(playWhenReady, reason);
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;
        syncInlinePauseInfo(playWhenReady);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;
        updateInlineButtons(service() != null && !player().isEmpty() && player().isPlaying());
        updateInlineDisplayPanel();
        applyResizeMode(getInlineScale());
        if (inlineStarted && (isShortDramaSource() || inlineShortDramaMode)) applyInlineShortDramaMode();
    }

    @Override
    protected void onStateChanged(int state) {
        if (!isInlinePlayerMode()) return;
        if (state == Player.STATE_BUFFERING) showInlineLoading();
        if (state == Player.STATE_READY) {
            hideInlineLoading();
            hideInlineControls();
            player().reset();
            boolean pendingResumeSeekApplied = applyInlineStartPosition();
            updateInlineButtons(player().isPlaying());
            recordAdFeedbackHost(); // 播放地址已确定，记入本站域名基线
            applyInlineShortDramaMode();
            requestIntroSkipPlan();
            if (!pendingResumeSeekApplied) applyAutoIntroSkip();
            if (shouldShowDetailFullscreenControlsOnReady()) {
                inlineFirstReady = true;  // 标记已显示过控制栏
                showInlineControls(true, false);
            }
            syncInlinePauseInfo(player().isPlaying());
        }
        if (state == Player.STATE_ENDED) checkInlineEnded(true);
        updateInlineDisplayPanel();
    }

    @Override
    protected void onTracksChanged() {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;
        updateInlineButtons(service() != null && !player().isEmpty() && player().isPlaying());
        updateInlineDisplayPanel();
        // 轨道要等新引擎 prepare 完才回来，重建那一刻宿主的音轨/字幕按钮是 GONE，
        // 弹窗抄到的是这个中间态，必须在轨道就绪后再抄一次。
        refreshInlineControlDialog();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        scheduleMobileInlineSideControlMarginUpdate();
        if (!inlineFullscreen) updateEpisodeLayoutForCurrentItems();
        if (inlineStarted) applyResizeMode(getInlineScale());
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        enterInlinePiP(false);
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        updateDetailThemeButtonVisibility();
        if (!isInlinePlayerMode()) return;
        if (isInPictureInPictureMode) {
            hideInlineControls();
            hideInlineGestureOverlays();
            // 内嵌卡片状态进入 PiP（含 Android 12+ autoEnter / 手势回桌面）必须铺满窗口，
            // 否则小窗里显示的是整页缩放（卡片圆角、描边、背景图都露出来）。全屏时布局已铺满，enterInlinePiPLayout 内部自带 guard。
            enterInlinePiPLayout();
            return;
        }
        exitInlinePiPLayout();
        updateInlineButtons(service() != null && player() != null && !player().isEmpty() && player().isPlaying());
        updateInlineDisplayPanel();
        updateDetailThemeButtonVisibility();
        App.post(this::finishIfInlinePipClosed, 0);
    }

    private void finishIfInlinePipClosed() {
        boolean atLeastStarted = getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED);
        if (!PipExitDecision.shouldFinishAfterPipExit(atLeastStarted, isFinishing(), isDestroyed())) return;
        saveInlineHistory();
        stopInlinePlaybackSync();
        inlineStarted = false;
        finishPlayback();
    }

    @Override
    protected boolean onSourceHttpError(int statusCode, String msg) {
        if (!isInlinePlayerMode() || !isOwner() || !isCurrentInlinePlayback(selectedEpisode)) return false;
        String episodeUrl = selectedEpisode.getUrl();
        if (!WebHomeInlineVodStore.shouldRefreshYoutubeEpisode(getKeyText(), episodeUrl, statusCode, inlineHttpRefreshAttempted)) return false;
        String failedUrl = currentInlineResult.getUrl() == null ? "" : currentInlineResult.getUrl().v();
        if (!WebHomeInlineVodStore.invalidateResolvedEpisode(episodeUrl)) return false;
        long position = player() == null ? C.TIME_UNSET : player().getPosition();
        inlineHttpRefreshAttempted = true;
        String status = String.valueOf(statusCode);
        String failureMessage = TextUtils.isEmpty(msg) ? "HTTP " + status : msg.contains(status) ? msg : msg + " (" + status + ")";
        SpiderDebug.log("webhome-inline", "http refresh start status=%d position=%d id=%s", statusCode, position, episodeUrl);
        playInline(position, failedUrl, failureMessage);
        return true;
    }

    @Override
    protected void onError(String msg) {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner()) return;
        showInlineError(msg);
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(binding.exo.getSubtitleView()).player(player()).search(this::showSubtitleSearch).show(this);
    }

    private void showSubtitleSearch() {
        SubtitleManualSearchDialog.show(this, subtitlePlaybackSession, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    /**
     * 猫源开了内嵌设置页：这次点击的本意就是开网页，本页立刻退场。
     *
     * <p>不能等 detail 结果再判定——那份结果还要等 TMDB 富集，主线程也可能被播放服务启动堵住，
     * 这段时间里从内嵌页按返回就会落回本页（空白详情页）。用请求时刻和本次取详情的起始时间比，
     * 确认是自己触发的才退。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCatWebEvent(com.fongmi.android.tv.event.CatWebEvent event) {
        if (!event.after(detailLoadStart)) return;
        SpiderDebug.log("tmdb-detail-flow", "yield to cat webview (event) key=%s id=%s", getKeyText(), getIdText());
        finish();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (!inlineStarted || service() == null || player() == null || player().isEmpty() || !isOwner()) return;
        if (event.getType() == RefreshEvent.Type.DANMAKU) {
            player().setDanmaku(Danmaku.from(event.getPath()));
            refreshInlineDanmakuButtons();
        } else if (event.getType() == RefreshEvent.Type.SUBTITLE) {
            player().setSub(Sub.from(event.getPath()));
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) flushPendingManualTmdbEpisodeRebind();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (vod != null && binding.loading.getVisibility() != View.VISIBLE) revealDefaultPlaybackLoadingPage();
        scheduleBackdropSlide(BACKDROP_SLIDE_DELAY_MS);
    }

    @Override
    protected void onPause() {
        cancelBackdropSlideRequest();
        super.onPause();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (inlinePlayerUi != null) inlinePlayerUi.onStart();
    }

    protected void onStop() {
        super.onStop();
        if (inlinePlayerUi != null) inlinePlayerUi.onStop();
    }

    @Override
    protected void onDestroy() {
        introSkipPlayback.reset();
        loadGeneration++;
        cancelAiSeasonAnalysis(false);
        detailTasks.close();
        subtitlePlaybackSession.stop(this);
        if (inlinePiPLayout) exitInlinePiPLayout();
        if (inlineFullscreen) exitInlineFullscreen();
        cancelBackdropSlideRequest();
        App.removeCallbacks(inlineHideControls);
        App.removeCallbacks(inlineKeySeekEnd);
        EpisodeTitlePopup.dismiss();
        saveInlineHistory();
        stopInlinePlaybackSync();
        // 确保内嵌播放退出时停止播放，避免声音继续（与 VideoActivity 保持一致）
        if (inlineStarted && isOwner() && !isPlaybackExiting()) {
            stopPlayback();
        }
        if (inlinePlayerUi != null) inlinePlayerUi.release();
        if (modeController != null) modeController.release();
        DanmakuApi.cancel();
        super.onDestroy();
    }

    @Override
    public String getSubtitlePlaybackKey() {
        return getHistoryKey();
    }

    @Override
    public Site getSubtitleSite() {
        return getCurrentSite();
    }

    @Override
    public Vod getSubtitleVod() {
        return vod;
    }

    @Override
    public Episode getSubtitleEpisode() {
        return selectedEpisode;
    }

    @Override
    public TmdbItem getSubtitleTmdbItem() {
        return matchedTmdbItem == null ? initialTmdbItem : matchedTmdbItem;
    }

    @Override
    public TmdbEpisode getSubtitleTmdbEpisode() {
        return selectedEpisode == null ? null : selectedEpisode.getTmdbEpisode();
    }

    @Override
    public PlayerManager getSubtitlePlayer() {
        return player();
    }

    @Override
    public boolean isSubtitleHostActive() {
        return !isFinishing() && !isDestroyed() && service() != null && player() != null && !player().isReleased() && inlineStarted && currentInlineResult != null && isOwner();
    }

    @Override
    protected void onBackInvoked() {
        if (binding.gestureSeek.getVisibility() == View.VISIBLE) {
            hideInlineGestureOverlays();
            return;
        }
        if (isLock() && inlineFullscreen) {
            showInlineControls(true, false);
            return;
        }
        if (isInlineControlsVisible()) {
            hideInlineControls();
            return;
        }
        if (Util.isLeanback() && inlineFullscreen) {
            backFromInlineFullscreen();
            return;
        }
        if (inlineFullscreen) {
            backFromInlineFullscreen();
            return;
        }
        super.onBackInvoked();
    }

    private void saveInlineHistory() {
        if (!isInlinePlayerMode() || !inlineStarted || !isOwner() || history == null) return;

        // 保存当前集的播放位置到缓存
        if (!TextUtils.isEmpty(history.getVodRemarks()) && !skipEpisodePositionCache() && service() != null && player() != null && !player().isReleased()) {
            EpisodePositionCache.get().put(
                getKeyText(),
                getIdText(),
                selectedFlag != null ? selectedFlag.getFlag() : "",
                currentInlineHistoryCacheKey(),
                player().getPosition(),
                player().getDuration()
            );
        }

        updateInlineHistoryProgress();
        if (history.canSave() && !Setting.isIncognito()) {
            history.merge().save();
            // 持久化集数位置缓存
            Task.execute(() -> EpisodePositionCache.get().save());
            RefreshEvent.history();
        }
    }

    private void stopInlinePlaybackSync() {
        if (!inlineStarted || !isOwner() || history == null || service() == null || player() == null || player().isReleased()) return;
        PlaybackEventCollector.get().updateHistory(history);
        PlaybackEventCollector.get().onStop(player());
    }

    private void syncInlineHistory() {
        updateInlineHistoryProgress();
        if (history != null && !Setting.isIncognito()) Task.execute(() -> history.save());
    }

    private void updateInlineHistoryProgress() {
        if (history == null || service() == null || player() == null || player().isReleased() || !isOwner()) return;
        updateInlineHistoryProgress(System.currentTimeMillis(), player().getPosition(), player().getDuration());
    }

    private void updateInlineHistoryProgress(long time, long position, long duration) {
        if (history == null) return;
        history.setCreateTime(time);
        if (position > 0) history.setPosition(position);
        if (duration > 0) history.setDuration(duration);
    }

    /**
     * 用户显式换内核后记住选定值。
     * 记的是用户选的值而不是引擎状态：引擎会被播放失败后的自动回退改掉，
     * 那不代表用户改了选择；例行的进度同步也一律不碰这个字段，
     * 否则上一部剧遗留的会话内核会覆盖本剧记住的选择。
     */
    private void rememberInlinePlayerKernel(int type) {
        if (history == null || !PlayerSetting.isPlayer(type)) return;
        history.setPlayer(type);
    }

    /** 本剧记住的内核；没有记录时退回设置页的全局默认。 */
    private int inlineHistoryPlayerKernel() {
        return history == null ? PlayerSetting.getPlayer() : history.getPlayerOrDefault();
    }

    private void requestIntroSkipPlan() {
        if (!Setting.isIntroSkipEnabled() || player() == null) {
            introSkipPlayback.reset();
            updateInlineOpeningEndingText();
            return;
        }
        ensureIntroSkipListeners();
        IntroSkipService.Query query = buildIntroSkipQuery();
        // 切集后 plan 已被 reset，这里先刷一次，避免 query 拿不到时残留上一集的探测值
        updateInlineOpeningEndingText();
        if (query == null) return;
        introSkipPlayback.request(query, this::onIntroSkipPlanLoaded);
    }

    private void onIntroSkipPlanLoaded() {
        if (isFinishing() || isDestroyed() || !isInlinePlayerMode() || !inlineStarted
                || service() == null || player() == null || player().isReleased() || !isOwner()) return;
        updateInlineOpeningEndingText();
        applyAutoIntroSkip();
        preloadAdjacentIntroSkipPlans();
    }

    /**
     * 预热前后各一集。查询不需要时长（IntroDB 不收，TheIntroDB 可选），这里传 0 即可；
     * 缓存按剧集身份存原始段，等那一集真开播时按其实际时长折算，不再走网络。
     */
    private void preloadAdjacentIntroSkipPlans() {
        if (!Setting.isIntroSkipEnabled()) return;
        TmdbItem item = matchedTmdbItem;
        if (item == null || item.getTmdbId() <= 0 || !item.isTv()) return;
        if (selectedFlag == null || selectedFlag.getEpisodes() == null || selectedEpisode == null) return;
        List<Episode> episodes = selectedFlag.getEpisodes();
        int index = episodes.indexOf(selectedEpisode);
        if (index < 0) return;
        String imdbId = introSkipImdbId();
        for (int offset : new int[]{1, -1}) {
            int next = index + offset;
            if (next < 0 || next >= episodes.size()) continue;
            TmdbEpisode tmdbEpisode = episodes.get(next).getTmdbEpisode();
            if (tmdbEpisode == null) continue;
            int season = tmdbEpisode.getSeasonNumber();
            int number = tmdbEpisode.getNumber();
            if (season < 0 || number <= 0) continue;
            introSkipPlayback.preload(new IntroSkipService.Query(item.getTmdbId(), imdbId, item.getMediaType(), season, number, 0));
        }
    }

    private boolean applyAutoIntroSkip() {
        if (!Setting.isIntroSkipEnabled() || isFinishing() || isDestroyed() || !isInlinePlayerMode()
                || !inlineStarted || service() == null || player() == null || player().isReleased() || !isOwner()) return false;
        // notify=true：片尾无处可跳（末集/电影）时至少要有提示，不能静默无反应
        return introSkipPlayback.apply(player(), () -> advanceInlineEpisode(true));
    }

    /**
     * 内嵌播放页原先没接确认监听，确认模式下什么都不会发生。这里补上，与另两个播放页一致。
     */
    private void ensureIntroSkipListeners() {
        if (introSkipListenersReady) return;
        introSkipListenersReady = true;
        introSkipPlayback.setSkipConfirmListener((segment, action) -> {
            if (isFinishing() || isDestroyed()) return false;
            if (introSkipConfirmDialog != null && introSkipConfirmDialog.isShowing()) return false;
            introSkipConfirmDialog = new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.intro_skip_confirm_title)
                    .setMessage(IntroSkipKinds.confirmMessage(segment))
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> action.run())
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            introSkipConfirmDialog.setOnDismissListener(dialog -> {
                introSkipPlayback.cancelConfirmation(segment);
                if (introSkipConfirmDialog == dialog) introSkipConfirmDialog = null;
            });
            return true;
        });
        introSkipPlayback.setSkipNoticeListener(IntroSkipKinds::notifySkipped);
        introSkipPlayback.setSkipConfirmDismisser(this::dismissIntroSkipConfirm);
    }

    private void dismissIntroSkipConfirm() {
        if (introSkipConfirmDialog == null) return;
        try {
            introSkipConfirmDialog.dismiss();
        } catch (Throwable ignored) {
        }
        introSkipConfirmDialog = null;
    }

    private IntroSkipService.Query buildIntroSkipQuery() {
        TmdbItem item = matchedTmdbItem;
        if (item == null || item.getTmdbId() <= 0) return null;
        int season = 0;
        int number = 0;
        if (item.isTv()) {
            TmdbEpisode tmdbEpisode = selectedEpisode == null ? null : selectedEpisode.getTmdbEpisode();
            season = tmdbEpisode != null && tmdbEpisode.getNumber() > 0 && tmdbEpisode.getSeasonNumber() >= 0 ? tmdbEpisode.getSeasonNumber() : selectedSeasonNumber;
            number = tmdbEpisode != null && tmdbEpisode.getNumber() > 0 ? tmdbEpisode.getNumber() : sourceEpisodeNumber(selectedEpisode);
            if (season < 0 || number <= 0) return null;
        }
        long duration = player() == null ? 0 : Math.max(0, player().getDuration());
        return new IntroSkipService.Query(item.getTmdbId(), introSkipImdbId(), item.getMediaType(), season, number, duration);
    }

    private String introSkipImdbId() {
        JsonObject externalIds = object(matchedTmdbDetail, "external_ids");
        return string(externalIds, "imdb_id");
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isInlinePlayerMode()) return;
        updateInlineLoadingTraffic();
        if (inlinePlaybackLoading || !isOwner() || history == null || service() == null || player() == null || player().isEmpty()) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        boolean canUpdateProgress = inlineStartPositionApplied || getInlineStartPosition() <= 0;
        if (canUpdateProgress) {
            updateInlineHistoryProgress(time, position, duration);
        } else {
            history.setCreateTime(time);
        }
        if (canUpdateProgress) PlaybackEventCollector.get().onProgress(history, player());
        if (canUpdateProgress && history.canSave() && history.canSync()) syncInlineHistory();
        if (canUpdateProgress && applyAutoIntroSkip()) return;
        if (canUpdateProgress && history.isEndingReached(position, duration)) checkInlineEnded(false);
        if (isInlineControlsVisible()) updateMobileInlineControlTime();
        updateInlineDisplayPanel();
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkInlineNext();
        }

        @Override
        public void onPrev() {
            checkInlinePrev();
        }

        @Override
        public void onStop() {
            finishPlaybackToHome();
        }

        @Override
        public void onReplay() {
            if (history != null) history.setPosition(C.TIME_UNSET);
            playInline();
        }
    };

    private void updateInlineHistory(Episode item) {
        if (selectedFlag == null || item == null) return;
        if (history == null) {
            history = new History();
            history.setKey(getHistoryKey());
            history.setCid(VodConfig.getCid());
        }
        boolean sameEpisode = isHistoryEpisode(item, history);
        boolean sameFlag = TextUtils.equals(history.getVodFlag(), selectedFlag.getFlag());
        if (inlineStarted && (!sameEpisode || !sameFlag)) stopInlinePlaybackSync();

        if (!sameEpisode) {
            // 保存当前集的播放位置到缓存
            boolean skipCache = skipEpisodePositionCache();
            if (!TextUtils.isEmpty(history.getVodRemarks()) && !skipCache && service() != null && player() != null && !player().isReleased()) {
                EpisodePositionCache.get().put(
                    getKeyText(),
                    getIdText(),
                    selectedFlag.getFlag(),
                    currentInlineHistoryCacheKey(),
                    player().getPosition(),
                    player().getDuration()
                );
            }

            // 从缓存中恢复新集的播放位置
            EpisodePositionCache.EpisodePosition cached = skipCache ? null : EpisodePositionCache.get().get(
                getKeyText(),
                getIdText(),
                selectedFlag.getFlag(),
                inlineEpisodeCacheKey(item)
            );

            if (cached != null) {
                history.setPosition(cached.position);
                // duration 必须与 position 成对恢复：isNearEnding() 要求 duration>0 才生效，
                // 漏设会让上一集的脏 duration 留在 history 上，接近片尾的自愈保护随之失效。
                history.setDuration(cached.duration);
            } else {
                history.setPosition(C.TIME_UNSET);
                history.setDuration(C.TIME_UNSET);
            }
        }

        history.setCid(VodConfig.getCid());
        history.setVodName(playbackHistoryName());
        history.setVodFlag(selectedFlag.getFlag());
        history.setSourceBindingKey(selectedSeasonFlagKey());
        // 起播时同步偏好，避免从历史列表跨线路续播后偏好仍指向旧线路。
        savePreferredFlag(selectedFlag);
        history.setVodRemarks(historyEpisodeTitle(item));
        history.setEpisodeUrl(item.getUrl());
        setHistoryTmdbEpisodePosition(history, item);
        history.setVodPic(playbackHistoryPic());
        // 富集字段：TMDB 优先，回退源站 Vod。仅补空字段，避免匹配失败时用空值覆盖已有数据（老记录也可补齐）
        history.enrichMeta(
                coalesce(firstGenre(), vod == null ? "" : vod.getTypeName()),
                coalesce(firstCountry(), vod == null ? "" : vod.getArea()),
                coalesce(castNames(), vod == null ? "" : vod.getActor()),
                coalesce(firstCrew("Director"), vod == null ? "" : vod.getDirector()),
                yearLabel());
        if (isFusionMode() || isPlayerMode()) PlaybackEventCollector.get().updateHistory(history);
        syncDanmakuCompatHistory();
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
        boolean kept = Keep.find(getHistoryKey()) != null;
        String text = getString(TmdbDetailLabels.keepLabel(kept));
        binding.keep.setSelected(kept);
        binding.keepTop.setSelected(kept);
        binding.keepFusion.setSelected(kept);
        binding.keep.setText(text);
        binding.keepTop.setText(text);
        binding.keepFusion.setText(text);
        updateInlineKeepImg(kept);
    }

    private void updateInlineKeepImg(boolean kept) {
        if (!Util.isMobile() || detailControlRoot == null) return;
        detailControlView(R.id.keep, ImageView.class).setImageResource(kept ? R.drawable.ic_control_keep_on : R.drawable.ic_control_keep_off);
    }

    private void changeSource() {
        if (vod == null) return;
        String keyword = getSourceSearchKeyword();
        int generation = loadGeneration;
        int searchGeneration = ++sourceSearchGeneration;
        Notify.show(getString(R.string.detail_source_searching));
        detailTasks.submit(() -> {
            SourceMatch match = searchChangeSource(keyword);
            runOnAliveUi(() -> {
                if (generation != loadGeneration || searchGeneration != sourceSearchGeneration || vod == null) return;
                if (match == null) {
                    revealDefaultPlaybackLoadingPage();
                    Notify.show(R.string.detail_source_empty);
                    return;
                }
                Notify.show(getString(R.string.play_switch_site, match.vod().getSiteName()));
                switchSourceDetail(match, matchedTmdbItem);
            });
        });
    }

    private SourceMatch searchAutoChangeSource(String keyword) {
        TmdbItem item = matchedTmdbItem != null ? matchedTmdbItem : initialTmdbItem;
        if (item != null && item.isTv() && item.getTmdbId() > 0 && currentSeasonSourceScope() >= 0) {
            return findSeasonHistorySource();
        }
        return searchChangeSource(keyword);
    }

    private void tryAutoChangeSource() {
        brokenSources.add(getKeyText());
        String keyword = getSourceSearchKeyword();
        int generation = loadGeneration;
        int searchGeneration = ++sourceSearchGeneration;
        Notify.show(getString(R.string.detail_source_searching));
        detailTasks.submit(() -> {
            SourceMatch match = searchAutoChangeSource(keyword);
            runOnAliveUi(() -> {
                if (generation != loadGeneration || searchGeneration != sourceSearchGeneration) return;
                if (match == null) {
                    revealDefaultPlaybackLoadingPage();
                    Notify.show(R.string.detail_source_empty);
                    return;
                }
                Notify.show(getString(R.string.play_switch_site, match.vod().getSiteName()));
                switchSourceDetail(match, matchedTmdbItem);
            });
        });
    }

    private boolean openGlobalSourceSearch() {
        String keyword = getSourceSearchKeyword();
        if (TextUtils.isEmpty(keyword)) return false;
        SearchActivity.direct(this, keyword);
        return true;
    }

    private String getSourceSearchKeyword() {
        if (vod != null && !TextUtils.isEmpty(vod.getName())) return vod.getName();
        String keyword = getTmdbSearchQuery();
        return TextUtils.isEmpty(keyword) ? getNameText() : keyword;
    }

    /**
     * 站内快搜：复用影视原生那套增量搜索——边搜边出结果、带站点进度，同一站源的多个命中全部列出。
     * 长按同一按钮才跳全局搜索页。
     */
    private void openInlineSourceSearch() {
        String keyword = getSourceSearchKeyword();
        if (TextUtils.isEmpty(keyword)) return;
        // 弹窗还开着就复用它重搜，否则第二次点搜索会把新结果追加到上一轮列表后面。
        if (inlineQuickSearchDialog != null && !inlineSearchClosed) {
            restartInlineSourceSearch(keyword);
            return;
        }
        closeInlineSearch();
        inlineSearchKeyword = keyword;
        inlineSearchClosed = false;
        observeInlineSearch();
        showInlineQuickSearchDialog(new ArrayList<>());
        startInlineSourceSearch(keyword);
    }

    private void startInlineSourceSearch(String keyword) {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isChangeSourceCandidate(site)) sites.add(site);
        SiteHealthStore.sortSites(sites);
        inlineSearchModel().searchContent(sites, keyword, true);
    }

    private SiteViewModel inlineSearchModel() {
        if (inlineSearchViewModel == null) inlineSearchViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        return inlineSearchViewModel;
    }

    private void observeInlineSearch() {
        if (inlineSearchObserved) return;
        inlineSearchObserved = true;
        inlineSearchModel().getSearch().observe(this, result -> {
            if (inlineSearchClosed || result == null) return;
            List<Vod> items = new ArrayList<>(result.getList());
            items.removeIf(this::inlineSearchMismatch);
            if (!items.isEmpty()) showInlineQuickSearchDialog(items);
        });
        inlineSearchModel().getSearchProgress().observe(this, progress -> {
            if (inlineSearchClosed || progress == null || inlineQuickSearchDialog == null) return;
            invokeQuiet(inlineQuickSearchDialog, "setProgress", new Class<?>[]{int.class, int.class, boolean.class},
                    progress.current(), progress.total(), progress.finished());
        });
    }

    /**
     * 站点层面的过滤（失效站源、当前站源、可否换源）已由 isChangeSourceCandidate 完成，
     * 这里只按条目过滤：搜索期间用户可能已切源，迟到结果里的"当前条目"要排掉。
     */
    private boolean inlineSearchMismatch(Vod item) {
        if (item == null || TextUtils.isEmpty(item.getSiteKey())) return true;
        if (TextUtils.equals(item.getSiteKey(), getKeyText()) && TextUtils.equals(item.getId(), getIdText())) return true;
        String name = item.getName();
        return TextUtils.isEmpty(name) || !name.contains(inlineSearchKeyword);
    }

    /**
     * QuickSearchDialog 在两个 flavor 里各有一套（TV 是卡片弹窗、手机是底部弹层），
     * main 源集只能反射调用，与 showInlineControlDialog 保持一致。
     */
    private void showInlineQuickSearchDialog(List<Vod> items) {
        if (inlineSearchClosed) return;
        if (inlineQuickSearchDialog != null) {
            invokeQuiet(inlineQuickSearchDialog, "addAll", new Class<?>[]{List.class}, items);
            return;
        }
        try {
            Class<?> dialogClass = Class.forName("com.fongmi.android.tv.ui.dialog.QuickSearchDialog");
            Class<?> listenerClass = Class.forName("com.fongmi.android.tv.ui.adapter.QuickAdapter$OnClickListener");
            Object listener = Proxy.newProxyInstance(listenerClass.getClassLoader(), new Class<?>[]{listenerClass}, (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);
                if ("onItemClick".equals(method.getName()) && args != null && args.length == 1) onInlineSearchItemClick((Vod) args[0]);
                return null;
            });
            Object dialog = dialogClass.getMethod("create").invoke(null);
            dialogClass.getMethod("listener", listenerClass).invoke(dialog, listener);
            dialogClass.getMethod("items", List.class).invoke(dialog, items);
            wireInlineQuickSearchExtras(dialogClass, dialog);
            dialogClass.getMethod("show", FragmentActivity.class).invoke(dialog, this);
            inlineQuickSearchDialog = dialog;
        } catch (Throwable e) {
            // 弹窗起不来就只能退回全局搜索页，否则用户点了搜索毫无反应。
            SpiderDebug.log("tmdb-inline", "quick search dialog failed errorType=%s", e.getClass().getSimpleName());
            inlineQuickSearchDialog = null;
            openGlobalSourceSearch();
        }
    }

    /**
     * 手机版弹层多一个标题栏和输入框（可在弹层里改关键词重搜），TV 版没有；
     * 两版的 dismiss 回调类型也不同。都按"有就接、没有就跳过"处理。
     */
    private void wireInlineQuickSearchExtras(Class<?> dialogClass, Object dialog) {
        invokeQuiet(dialog, "title", new Class<?>[]{String.class}, getString(R.string.play_search) + " " + inlineSearchKeyword);
        invokeQuiet(dialog, "keyword", new Class<?>[]{String.class}, inlineSearchKeyword);
        // 手机版：弹层内改关键词重搜 + 自有 dismiss 接口
        bindProxyListener(dialogClass, dialog, "searchListener", dialogClass.getName() + "$OnSearchListener",
                args -> restartInlineSourceSearch(args == null || args.length != 1 ? "" : String.valueOf(args[0])));
        bindProxyListener(dialogClass, dialog, "dismissListener", dialogClass.getName() + "$OnDismissListener",
                args -> closeInlineSearch());
        // TV 版：用 android 框架的 DialogInterface.OnDismissListener
        invokeQuiet(dialog, "dismissListener", new Class<?>[]{DialogInterface.OnDismissListener.class},
                (DialogInterface.OnDismissListener) d -> closeInlineSearch());
    }

    private void bindProxyListener(Class<?> dialogClass, Object dialog, String setter, String callbackClassName, Consumer<Object[]> action) {
        try {
            Class<?> callbackClass = Class.forName(callbackClassName);
            Object proxy = Proxy.newProxyInstance(callbackClass.getClassLoader(), new Class<?>[]{callbackClass}, (p, method, args) -> {
                // Object 的 toString/hashCode/equals 也会转发过来，返回 null 会在拆箱时 NPE。
                if (method.getDeclaringClass() == Object.class) return method.invoke(this, args);
                if (method.getDeclaringClass() == callbackClass) action.accept(args);
                return null;
            });
            dialogClass.getMethod(setter, callbackClass).invoke(dialog, proxy);
        } catch (Throwable ignored) {
        }
    }

    /**
     * TV 版弹窗没有 clear()，列表只能追加。所以重搜时若清不掉就整体关掉重开，
     * 否则新一轮结果会接在上一轮后面。
     */
    private void restartInlineSourceSearch(String keyword) {
        if (TextUtils.isEmpty(keyword)) return;
        inlineSearchModel().stopSearch();
        inlineSearchKeyword = keyword;
        if (invokeQuiet(inlineQuickSearchDialog, "clear", new Class<?>[0])) {
            startInlineSourceSearch(keyword);
            return;
        }
        closeInlineSearch();
        inlineSearchClosed = false;
        showInlineQuickSearchDialog(new ArrayList<>());
        startInlineSourceSearch(keyword);
    }

    private void onInlineSearchItemClick(Vod item) {
        if (item == null) return;
        closeInlineSearch();
        Site site = VodConfig.get().getSite(item.getSiteKey());
        if (site == null || site.isEmpty()) return;
        switchSourceDetail(site, item, matchedTmdbItem);
    }

    private void closeInlineSearch() {
        inlineSearchClosed = true;
        // 先摘引用再关弹窗：dismiss 会同步回调 onDismiss -> closeInlineSearch，
        // 留着引用就会重入一层。只置 null 不关弹窗则会让残留结果灌进下一轮。
        Object dialog = inlineQuickSearchDialog;
        inlineQuickSearchDialog = null;
        invokeQuiet(dialog, "dismissAllowingStateLoss", new Class<?>[0]);
        if (inlineSearchViewModel != null) inlineSearchViewModel.stopSearch();
    }

    /**
     * 两个 flavor 的弹窗 API 不完全重叠（如 setProgress 只有 TV 版、clear 只有手机版），
     * 缺方法属于预期差异，返回 false 让调用方走降级路径。
     */
    private static boolean invokeQuiet(Object target, String method, Class<?>[] types, Object... args) {
        if (target == null) return false;
        try {
            target.getClass().getMethod(method, types).invoke(target, args);
            return true;
        } catch (NoSuchMethodException absent) {
            return false;
        } catch (Throwable e) {
            SpiderDebug.log("tmdb-inline", "quick search %s failed errorType=%s", method, e.getClass().getSimpleName());
            return false;
        }
    }

    private void loadPersonDetail(TmdbPerson person) {
        if (!canMatchTmdb()) {
            Notify.show(getString(R.string.detail_tmdb_need_key));
            return;
        }
        TmdbPersonActivity.start(this, person, getKeyText(), getDetailMode());
    }

    private void openRelatedItem(TmdbItem item) {
        Site site = getCurrentSite();
        if (site == null || site.isEmpty() || !site.isSearchable()) {
            Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        int generation = loadGeneration;
        int searchGeneration = ++sourceSearchGeneration;
        Notify.show(getString(R.string.detail_work_searching, item.getTitle()));
        detailTasks.submit(() -> {
            Vod match = searchCurrentSite(item.getTitle(), site);
            runOnAliveUi(() -> {
                if (generation != loadGeneration || searchGeneration != sourceSearchGeneration) return;
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
            switchSourceDetail(site, match, item, "");
            return;
        }
        Intent intent = new Intent(this, TmdbDetailActivity.class);
        int detailMode = getDetailMode();
        intent.putExtra("detail_mode", detailMode);
        intent.putExtra("fusion", detailMode == Setting.DETAIL_OPEN_FUSION);
        intent.putExtra("key", site.getKey());
        intent.putExtra("id", match.getId());
        intent.putExtra("name", match.getName());
        intent.putExtra("pic", match.getPic());
        intent.putExtra("mark", "");
        putTmdbItem(intent, item);
        startActivity(intent);
    }

    private void switchSourceDetail(Site site, Vod match, TmdbItem item) {
        switchSourceDetail(site, match, item, sourceSwitchMark());
    }

    private void switchSourceDetail(Site site, Vod match, TmdbItem item, String mark) {
        switchSourceDetail(site, match, item, mark, "", "", "", "", -1, 0, "");
    }

    private void switchSourceDetail(SourceMatch source, TmdbItem item) {
        switchSourceDetail(source.site(), source.vod(), item,
                TextUtils.isEmpty(source.episodeName()) ? sourceSwitchMark() : source.episodeName(),
                source.sourceFlag(), source.sourceFlagKey(), source.episodeName(), source.episodeUrl(),
                source.seasonNumber(), source.resumeHistoryCid(), source.resumeHistoryPayload());
    }

    private void switchSourceDetail(
            Site site,
            Vod match,
            TmdbItem item,
            String mark,
            String playFlag,
            String playFlagKey,
            String playEpisodeName,
            String playEpisodeUrl,
            int seasonNumber,
            int resumeHistoryCid,
            String resumeHistoryPayload) {
        TmdbBundle reusableBundle = canReuseTmdbBundle(item) ? activeTmdbBundle : null;
        Intent intent = new Intent(getIntent());
        intent.putExtra("detail_mode", getDetailMode());
        intent.putExtra("fusion", isFusionMode());
        intent.putExtra("key", site.getKey());
        intent.putExtra("id", match.getId());
        intent.putExtra("name", match.getName());
        intent.putExtra("pic", match.getPic());
        intent.putExtra("mark", mark);
        intent.removeExtra(EXTRA_PLAY_SEASON_NUMBER);
        intent.removeExtra(EXTRA_PLAY_FLAG);
        intent.removeExtra(EXTRA_PLAY_FLAG_KEY);
        intent.removeExtra(EXTRA_PLAY_EPISODE_NAME);
        intent.removeExtra(EXTRA_PLAY_EPISODE_URL);
        intent.removeExtra(EXTRA_RESUME_FROM_HISTORY);
        intent.removeExtra(EXTRA_RESUME_HISTORY_CID);
        intent.removeExtra(EXTRA_RESUME_HISTORY_KEY);
        if (seasonNumber >= 0) intent.putExtra(EXTRA_PLAY_SEASON_NUMBER, seasonNumber);
        putIntentPlaybackSelection(intent, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl);
        if (!TextUtils.isEmpty(resumeHistoryPayload)
                && HistoryResumePayload.restore(resumeHistoryCid, resumeHistoryPayload) != null) {
            intent.putExtra(EXTRA_RESUME_FROM_HISTORY, true);
            intent.putExtra(EXTRA_RESUME_HISTORY_CID, resumeHistoryCid);
            intent.putExtra(EXTRA_RESUME_HISTORY_KEY, resumeHistoryPayload);
        }
        putTmdbItem(intent, item);
        setIntent(intent);
        resetDetailState();
        loadContent(reusableBundle);
    }

    private String sourceSwitchMark() {
        if (selectedEpisode != null && !TextUtils.isEmpty(selectedEpisode.getName())) return selectedEpisode.getName();
        return getMarkText();
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
        SourceMatch savedSource = findSeasonHistorySource();
        if (savedSource != null) return savedSource;
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

    private SourceMatch findSeasonHistorySource() {
        TmdbItem item = matchedTmdbItem != null ? matchedTmdbItem : initialTmdbItem;
        if (item == null || !item.isTv() || item.getTmdbId() <= 0) return null;
        int seasonNumber = currentSeasonSourceScope();
        if (seasonNumber < 0) return null;
        List<SourceMatch> sources = findSeasonHistorySources(item, seasonNumber);
        return sources.isEmpty() ? null : sources.get(0);
    }

    private List<SourceMatch> findSeasonHistorySources(TmdbItem item, int seasonNumber) {
        int cid = history == null ? VodConfig.getCid() : history.getCid();
        List<History> histories = AppDatabase.get().getHistoryDao()
                .findByTmdbIdentity(cid, item.getMediaType(), item.getTmdbId());
        List<TmdbSeasonProgress> snapshots = AppDatabase.get().getTmdbSeasonProgressDao()
                .findByMedia(cid, item.getMediaType(), item.getTmdbId());
        TmdbSeasonMatchCache seasonCache = Setting.getTmdbSeasonMatchCache();
        Map<String, List<TmdbSeasonMatchCache.RouteBinding>> bindingsByRoute =
                seasonCache.indexRouteBindings(item.getTmdbId(), item.getMediaType(), seasonNumber);
        String currentRoute = getKeyText() + AppDatabase.SYMBOL + getIdText();
        List<SourceMatch> result = new ArrayList<>();
        for (History saved : TmdbSeasonSourceAggregator.collect(
                histories, snapshots, seasonCache, cid, item.getMediaType(), item.getTmdbId(), seasonNumber, currentRoute)) {
            if (brokenSources.contains(saved.getSiteKey())) continue;
            Site site = VodConfig.get().getSite(saved.getSiteKey());
            if (site == null || site.isEmpty() || !site.isChangeable()) continue;
            Vod target = new Vod();
            target.setId(saved.getVodId());
            target.setName(saved.getVodName());
            target.setPic(saved.getVodPic());
            target.setRemarks(saved.getVodRemarks());
            target.setYear(saved.getYear());
            target.setTypeName(saved.getTypeName());
            target.setSite(site);
            List<TmdbSeasonMatchCache.RouteBinding> routeBindings = bindingsByRoute.getOrDefault(
                    TmdbSeasonMatchCache.routeIdentity(saved.getSiteKey(), saved.getVodId()), List.of());
            String sourceFlagKey = TmdbUIAdapter.isFlagKey(saved.getSourceBindingKey())
                    ? saved.getSourceBindingKey() : routeBindings
                    .stream()
                    .filter(binding -> TextUtils.isEmpty(saved.getVodFlag())
                            || TextUtils.equals(saved.getVodFlag(), binding.getSourceFlag()))
                    .map(TmdbSeasonMatchCache.RouteBinding::getFlagKey)
                    .findFirst()
                    .orElse("");
            String resumeHistoryPayload = HistoryResumePayload.encode(saved);
            if (HistoryResumePayload.restore(saved.getCid(), resumeHistoryPayload) == null) {
                resumeHistoryPayload = "";
            }
            result.add(new SourceMatch(site, target, 400,
                    saved.getTmdbSeasonNumber(), saved.getVodFlag(),
                    sourceFlagKey,
                    saved.getVodRemarks(), saved.getEpisodeUrl(),
                    saved.getCid(), resumeHistoryPayload));
        }
        return result;
    }

    private int currentSeasonSourceScope() {
        if (selectedSeasonNumber >= 0) return selectedSeasonNumber;
        int mappedSeason = tmdbEpisodeDataSeason(selectedFlag == null ? null : selectedFlag.getEpisodes());
        if (mappedSeason >= 0) return mappedSeason;
        int sourceSeason = sourceTitleSeasonNumber();
        if (sourceSeason >= 0) return sourceSeason;
        if (history != null && history.getTmdbEpisodeNumber() > 0 && history.getTmdbSeasonNumber() >= 0) {
            return history.getTmdbSeasonNumber();
        }
        return -1;
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
        if (brokenSources.contains(site.getKey())) return false;
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

    private TmdbItem chooseTmdbMatch(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        TmdbItem strict = chooseStrictTmdbMatch(items, keyword, sourceVod);
        if (strict != null || !Setting.isTmdbSmartMatch() || sourceVod == null) return strict;
        TmdbBundle bundle = chooseSmartTmdbBundle(items, keyword, sourceVod);
        return bundle == null ? null : bundle.item();
    }

    private TmdbItem chooseStrictTmdbMatch(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        // 自动匹配必须保持保守：这里只允许“标题归一化后完全一致”的候选进入评分。
        // 如果站源详情或搜索词里能提取到年份，候选的 TMDB 年份也必须完全同年。
        // 源标题明确带季数时，允许用对应 TMDB 分季首播年匹配根剧集条目。
        // 演员、导演、简介里的主创信息只用于同名候选之间消歧，不允许用来放宽标题或年份规则。
        logTmdbMatch("开始自动匹配：关键词=%s，归一化=%s，站源年份=%d，站源季=%d，是否有站源详情=%s，候选原始数量=%d",
                keyword, normalize(keyword), sourceYear(keyword, sourceVod), sourceSeasonNumber(keyword, sourceVod), sourceVod != null, items == null ? 0 : items.size());
        List<TmdbCandidate> candidates = scoreTmdbCandidates(items, keyword, sourceVod);
        if (candidates.isEmpty()) {
            logTmdbMatch("自动匹配结束：没有严格标题/年份一致的候选，交给手动选择");
            return null;
        }
        if (candidates.size() == 1 && candidates.get(0).titleScore >= 210) {
            logTmdbMatch("自动匹配成功：只有一个严格候选，标题=%s，年份=%d，评分=%d",
                    candidates.get(0).item.getTitle(), tmdbItemYear(candidates.get(0).item), candidates.get(0).score);
            return candidates.get(0).item;
        }
        TmdbCandidate best = candidates.get(0);
        TmdbCandidate second = candidates.size() > 1 ? candidates.get(1) : null;
        int gap = second == null ? best.score : best.score - second.score;
        if (shouldAcceptFirstExactTmdbCandidate(best, second, keyword, sourceVod)) {
            logTmdbMatch("自动匹配成功：同名同分候选采用 TMDB 搜索首位，标题=%s，年份=%d，评分=%d",
                    best.item.getTitle(), tmdbItemYear(best.item), best.score);
            return best.item;
        }
        boolean accepted = best.score >= 360 && gap >= 50;
        logTmdbMatch("自动匹配判定：最佳=%s(%d年, 分=%d)，第二=%s，分差=%d，结果=%s",
                best.item.getTitle(), tmdbItemYear(best.item), best.score, second == null ? "无" : second.item.getTitle() + "(" + second.score + ")", gap, accepted ? "通过" : "不通过");
        return accepted ? best.item : null;
    }

    private boolean shouldAcceptFirstExactTmdbCandidate(TmdbCandidate best, @Nullable TmdbCandidate second, String keyword, @Nullable Vod sourceVod) {
        if (sourceVod != null || best == null || second == null) return false;
        if (best.score != second.score || best.titleScore < 300 || second.titleScore < 300) return false;
        String normalized = normalize(keyword);
        return normalize(best.item.getTitle()).equals(normalized) && normalize(second.item.getTitle()).equals(normalized);
    }

    private TmdbBundle chooseTmdbBundle(List<TmdbItem> items, String keyword, Vod sourceVod) {
        // 走到这里说明仅靠搜索结果列表还不能稳定选出一个条目。
        // 此时只会对前几个“标题一致、年份一致”的候选拉取 TMDB 详情，
        // 再用站源演员、导演、简介中出现的完整姓名去命中 TMDB 演员/主创。
        // 这样可以解决同名剧缺少年份时的自动消歧，同时避免把《最佳女主角》误匹配成《主角》。
        logTmdbMatch("进入详情消歧：关键词=%s，站源演员=%s，站源导演=%s", keyword, sourceVod.getActor(), sourceVod.getDirector());
        List<TmdbCandidate> candidates = scoreTmdbCandidates(items, keyword, sourceVod);
        if (candidates.isEmpty()) {
            logTmdbMatch("详情消歧结束：没有可消歧候选");
            return Setting.isTmdbSmartMatch() ? chooseSmartTmdbBundle(items, keyword, sourceVod) : null;
        }
        TmdbCandidate best = null;
        TmdbCandidate second = null;
        int count = Math.min(6, candidates.size());
        logTmdbMatch("详情消歧开始拉取候选详情：候选数量=%d，拉取上限=%d", candidates.size(), count);
        for (int i = 0; i < count; i++) {
            TmdbCandidate candidate = candidates.get(i);
            try {
                TmdbBundle bundle = loadTmdbBundle(candidate.item);
                int peopleScore = hasSourcePeople(sourceVod) ? scoreTmdbPeople(bundle.detail(), sourceVod) : 0;
                int splitSeasonScore = TmdbMatchPolicy.splitSeasonDetailScore(tmdbMatchSourceText(keyword, sourceVod), bundle.detail());
                int score = candidate.score + peopleScore + splitSeasonScore;
                logTmdbMatch("详情消歧候选：标题=%s，年份=%d，基础分=%d，演员主创分=%d，分季分=%d，总分=%d",
                        candidate.item.getTitle(), tmdbItemYear(candidate.item), candidate.score, peopleScore, splitSeasonScore, score);
                TmdbCandidate scored = new TmdbCandidate(candidate.item, candidate.titleScore, score, bundle);
                if (best == null || scored.score > best.score) {
                    second = best;
                    best = scored;
                } else if (second == null || scored.score > second.score) {
                    second = scored;
                }
            } catch (Throwable ignored) {
                logTmdbMatch("详情消歧候选加载失败：标题=%s，错误=%s", candidate.item.getTitle(), ignored.getMessage());
            }
        }
        if (best == null || best.bundle == null) {
            logTmdbMatch("详情消歧结束：没有成功加载的候选详情");
            return Setting.isTmdbSmartMatch() ? chooseSmartTmdbBundle(items, keyword, sourceVod) : null;
        }
        int gap = second == null ? best.score : best.score - second.score;
        boolean accepted = best.score >= 420 && gap >= 50;
        logTmdbMatch("详情消歧判定：最佳=%s(%d年, 分=%d)，第二=%s，分差=%d，结果=%s",
                best.item.getTitle(), tmdbItemYear(best.item), best.score, second == null ? "无" : second.item.getTitle() + "(" + second.score + ")", gap, accepted ? "通过" : "不通过");
        if (accepted) return best.bundle;
        return Setting.isTmdbSmartMatch() ? chooseSmartTmdbBundle(items, keyword, sourceVod) : null;
    }

    private List<TmdbCandidate> scoreTmdbCandidates(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        List<TmdbCandidate> candidates = new ArrayList<>();
        if (items == null || items.isEmpty()) return candidates;
        String normalized = normalize(keyword);
        int sourceYear = sourceYear(keyword, sourceVod);
        int sourceSeason = sourceSeasonNumber(keyword, sourceVod);
        for (TmdbItem item : items) {
            // 第一层过滤只看标题是否完全一致，不做 contains、相似度、首尾包含等近似判断。
            int titleScore = scoreTmdbTitle(item, normalized);
            if (titleScore <= 0) {
                logTmdbMatch("候选过滤：标题不一致，关键词=%s，候选=%s，候选年份=%d", keyword, item.getTitle(), tmdbItemYear(item));
                continue;
            }
            int itemYear = tmdbItemYear(item);
            boolean seasonYearMatched = false;
            // 第二层过滤看年份：只要站源侧有年份，TMDB 侧必须同年；源标题带季数时，可用对应季年份命中根剧集。
            if (sourceYear > 0 && itemYear != sourceYear) {
                seasonYearMatched = tmdbSeasonYearMatches(item, sourceSeason, sourceYear);
                if (!seasonYearMatched) {
                    logTmdbMatch("候选过滤：年份不一致，关键词=%s，候选=%s，站源年份=%d，TMDB年份=%d，站源季=%d", keyword, item.getTitle(), sourceYear, itemYear, sourceSeason);
                    continue;
                }
            }
            int yearScore = seasonYearMatched ? 120 : scoreTmdbYear(item, sourceYear);
            int seasonScore = seasonYearMatched ? 60 : 0;
            int typeScore = scoreTmdbMediaType(item, sourceVod);
            int creditScore = scoreTmdbPeople(item, sourceVod);
            int score = titleScore + yearScore + seasonScore + typeScore + creditScore;
            logTmdbMatch("候选保留：标题=%s，媒体=%s，年份=%d，站源季=%d，季年匹配=%s，标题分=%d，年份分=%d，季分=%d，类型分=%d，已有职员分=%d，总分=%d",
                    item.getTitle(), item.getMediaType(), itemYear, sourceSeason, seasonYearMatched, titleScore, yearScore, seasonScore, typeScore, creditScore, score);
            candidates.add(new TmdbCandidate(item, titleScore, score, null));
        }
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        return candidates;
    }

    @Nullable
    private TmdbBundle chooseSmartTmdbBundle(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        if (items == null || items.isEmpty()) return null;
        List<TmdbCandidate> candidates = scoreSmartTmdbCandidates(items, keyword, sourceVod);
        if (candidates.isEmpty()) {
            logTmdbMatch("智能匹配结束：没有可用候选");
            return null;
        }
        List<TmdbCandidate> scored = new ArrayList<>();
        int count = Math.min(6, candidates.size());
        logTmdbMatch("智能匹配开始拉取候选详情：关键词=%s，候选数量=%d，拉取上限=%d", keyword, candidates.size(), count);
        for (int i = 0; i < count; i++) {
            TmdbCandidate candidate = candidates.get(i);
            try {
                TmdbBundle bundle = loadTmdbBundle(candidate.item);
                int detailScore = scoreTmdbCountry(bundle.detail(), sourceVod)
                        + scoreTmdbPeople(bundle.detail(), sourceVod)
                        + TmdbMatchPolicy.splitSeasonDetailScore(tmdbMatchSourceText(keyword, sourceVod), bundle.detail());
                int score = candidate.score + detailScore;
                logTmdbMatch("智能匹配候选：标题=%s，媒体=%s，年份=%d，基础分=%d，详情分=%d，总分=%d",
                        candidate.item.getTitle(), candidate.item.getMediaType(), tmdbItemYear(candidate.item), candidate.score, detailScore, score);
                scored.add(new TmdbCandidate(candidate.item, candidate.titleScore, score, bundle));
            } catch (Throwable ignored) {
                logTmdbMatch("智能匹配候选加载失败：标题=%s，错误=%s", candidate.item.getTitle(), ignored.getMessage());
            }
        }
        if (scored.isEmpty()) {
            logTmdbMatch("智能匹配结束：没有成功加载详情的候选");
            return null;
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        TmdbCandidate best = scored.get(0);
        TmdbCandidate second = scored.size() > 1 ? scored.get(1) : null;
        int sourceYear = sourceYear(keyword, sourceVod);
        int gap = second == null ? best.score : best.score - second.score;
        boolean accepted = best.score >= smartAcceptScore(sourceYear) && (gap >= 25 || isUniqueNewest(best, scored, sourceYear));
        logTmdbMatch("智能匹配判定：最佳=%s(%d年, 分=%d)，第二=%s，分差=%d，结果=%s",
                best.item.getTitle(), tmdbItemYear(best.item), best.score, second == null ? "无" : second.item.getTitle() + "(" + second.score + ")", gap, accepted ? "通过" : "不通过");
        return accepted ? best.bundle : null;
    }

    private List<TmdbCandidate> scoreSmartTmdbCandidates(List<TmdbItem> items, String keyword, @Nullable Vod sourceVod) {
        List<TmdbCandidate> candidates = new ArrayList<>();
        String normalized = normalize(keyword);
        int sourceYear = sourceYear(keyword, sourceVod);
        for (TmdbItem item : items) {
            int titleScore = scoreTmdbSmartTitle(item, normalized);
            if (titleScore <= 0) {
                logTmdbMatch("智能候选过滤：标题差异过大，关键词=%s，候选=%s，候选年份=%d", keyword, item.getTitle(), tmdbItemYear(item));
                continue;
            }
            int yearScore = scoreTmdbSmartYear(item, sourceYear);
            if (sourceYear > 0 && yearScore < 0) {
                logTmdbMatch("智能候选过滤：年份差异过大，关键词=%s，候选=%s，站源年份=%d，TMDB年份=%d", keyword, item.getTitle(), sourceYear, tmdbItemYear(item));
                continue;
            }
            int freshnessScore = sourceYear > 0 ? 0 : scoreTmdbFreshness(item);
            int typeScore = scoreTmdbMediaType(item, sourceVod);
            int creditScore = scoreTmdbPeople(item, sourceVod);
            int voteScore = scoreTmdbVote(item);
            int score = titleScore + yearScore + freshnessScore + typeScore + creditScore + voteScore;
            logTmdbMatch("智能候选保留：标题=%s，媒体=%s，年份=%d，标题分=%d，年份分=%d，近期分=%d，类型分=%d，已有职员分=%d，评分分=%d，总分=%d",
                    item.getTitle(), item.getMediaType(), tmdbItemYear(item), titleScore, yearScore, freshnessScore, typeScore, creditScore, voteScore, score);
            candidates.add(new TmdbCandidate(item, titleScore, score, null));
        }
        candidates.sort((a, b) -> Integer.compare(b.score, a.score));
        return candidates;
    }

    private int scoreTmdbSmartTitle(TmdbItem item, String normalizedKeyword) {
        int strict = scoreTmdbTitle(item, normalizedKeyword);
        if (strict > 0) return strict;
        int year = tmdbItemYear(item);
        String cleaned = year > 0 ? removeYearFromTitle(item.getTitle(), year) : cleanTmdbSearchQuery(item.getTitle());
        return normalize(cleaned).equals(normalizedKeyword) ? 280 : 0;
    }

    private int scoreTmdbSmartYear(TmdbItem item, int sourceYear) {
        if (sourceYear <= 0) return 0;
        int itemYear = tmdbItemYear(item);
        if (itemYear <= 0) return -20;
        int diff = Math.abs(itemYear - sourceYear);
        if (diff == 0) return 120;
        if (diff == 1) return 70;
        return -1;
    }

    private int scoreTmdbFreshness(TmdbItem item) {
        int year = tmdbItemYear(item);
        if (year <= 0) return 0;
        int age = LocalDateTime.now().getYear() - year;
        if (age <= 0) return 80;
        if (age == 1) return 70;
        if (age <= 3) return 55;
        if (age <= 5) return 45;
        if (age <= 10) return 30;
        return 15;
    }

    private int scoreTmdbVote(TmdbItem item) {
        Matcher matcher = Pattern.compile("评分\\s*([0-9](?:\\.[0-9])?)").matcher(item.getSubtitle());
        double vote = 0;
        if (matcher.find()) {
            try {
                vote = Double.parseDouble(matcher.group(1));
            } catch (Throwable ignored) {
            }
        }
        if (vote >= 8.0) return 20;
        if (vote >= 7.0) return 12;
        if (vote >= 6.0) return 5;
        return 0;
    }

    private int scoreTmdbCountry(JsonObject detail, @Nullable Vod sourceVod) {
        Set<String> preferred = preferredCountryCodes(sourceVod);
        Set<String> countries = tmdbCountryCodes(detail);
        if (countries.isEmpty()) return 0;
        boolean explicit = hasExplicitCountryPreference(sourceVod);
        for (String code : preferred) if (countries.contains(code)) return explicit ? 120 : 45;
        return explicit ? -60 : 0;
    }

    private Set<String> preferredCountryCodes(@Nullable Vod sourceVod) {
        Set<String> codes = explicitCountryCodes(sourceVod);
        if (!codes.isEmpty()) return codes;
        return Set.of("CN");
    }

    private boolean hasExplicitCountryPreference(@Nullable Vod sourceVod) {
        return !explicitCountryCodes(sourceVod).isEmpty();
    }

    private Set<String> explicitCountryCodes(@Nullable Vod sourceVod) {
        Set<String> codes = new HashSet<>();
        if (sourceVod == null) return codes;
        String text = normalize(sourceVod.getArea() + " " + sourceVod.getTypeName() + " " + sourceVod.getRemarks() + " " + sourceVod.getName());
        addCountryCode(codes, text, "CN", "中国", "中國", "大陆", "大陸", "内地", "內地", "国产", "國產", "华语", "華語");
        addCountryCode(codes, text, "HK", "香港", "港剧", "港劇");
        addCountryCode(codes, text, "TW", "台湾", "台灣", "台剧", "台劇");
        addCountryCode(codes, text, "JP", "日本", "日剧", "日劇", "日影", "日漫");
        addCountryCode(codes, text, "KR", "韩国", "韓國", "韩剧", "韓劇", "韩影", "韓影");
        addCountryCode(codes, text, "US", "美国", "美國", "美剧", "美劇", "欧美", "歐美");
        addCountryCode(codes, text, "GB", "英国", "英國", "英剧", "英劇");
        addCountryCode(codes, text, "TH", "泰国", "泰國", "泰剧", "泰劇");
        addCountryCode(codes, text, "IN", "印度");
        return codes;
    }

    private void addCountryCode(Set<String> codes, String text, String code, String... words) {
        for (String word : words) {
            if (!text.contains(normalize(word))) continue;
            codes.add(code);
            return;
        }
    }

    private Set<String> tmdbCountryCodes(JsonObject detail) {
        Set<String> codes = new HashSet<>();
        for (JsonElement element : array(detail, "origin_country")) {
            if (element.isJsonPrimitive()) codes.add(element.getAsString().toUpperCase(Locale.ROOT));
        }
        for (JsonElement element : array(detail, "production_countries")) {
            if (!element.isJsonObject()) continue;
            JsonObject country = element.getAsJsonObject();
            String code = string(country, "iso_3166_1").toUpperCase(Locale.ROOT);
            if (!TextUtils.isEmpty(code)) codes.add(code);
            String name = normalize(string(country, "name"));
            if (name.contains("china") || name.contains("中国") || name.contains("中國") || name.contains("大陆") || name.contains("大陸") || name.contains("内地") || name.contains("內地")) codes.add("CN");
        }
        return codes;
    }

    private int smartAcceptScore(int sourceYear) {
        return sourceYear > 0 ? 390 : 335;
    }

    private boolean isUniqueNewest(TmdbCandidate best, List<TmdbCandidate> candidates, int sourceYear) {
        if (sourceYear > 0 || best.titleScore < 300) return false;
        int bestYear = tmdbItemYear(best.item);
        if (bestYear <= 0) return false;
        for (TmdbCandidate candidate : candidates) {
            if (candidate == best) continue;
            int year = tmdbItemYear(candidate.item);
            if (year >= bestYear && candidate.score >= best.score - 10) return false;
        }
        return true;
    }

    private int scoreTmdbTitle(TmdbItem item, String normalizedKeyword) {
        // 标题分只给完全一致的结果。这里故意不支持“候选包含关键词”，防止短标题误吸附长标题。
        String title = normalize(item.getTitle());
        if (TextUtils.isEmpty(title) || TextUtils.isEmpty(normalizedKeyword)) return 0;
        if (title.equals(normalizedKeyword)) return 300;
        return 0;
    }

    private int scoreTmdbYear(TmdbItem item, int sourceYear) {
        // 年份只允许同年加分；不同年已经在候选过滤阶段剔除，不做近似年份匹配。
        if (sourceYear <= 0) return 0;
        return tmdbItemYear(item) == sourceYear ? 120 : 0;
    }

    private int tmdbItemYear(TmdbItem item) {
        // 搜索结果里的年份通常在副标题中，例如“剧集 · 2026-05-10 · 评分 9.2”。
        // 如果副标题没有年份，再从标题兜底提取，兼容站源或接口返回的特殊格式。
        int year = firstYear(item.getSubtitle());
        return year > 0 ? year : firstYear(item.getTitle());
    }

    private boolean tmdbSeasonYearMatches(TmdbItem item, int seasonNumber, int sourceYear) {
        if (item == null || seasonNumber <= 0 || sourceYear <= 0 || !"tv".equalsIgnoreCase(item.getMediaType())) return false;
        try {
            JsonObject detail = tmdbService.detail(item, tmdbConfig);
            int seasonYear = tmdbSeasonYear(detail, seasonNumber);
            boolean matched = seasonYear == sourceYear;
            logTmdbMatch("候选季年%s：标题=%s，季=%d，站源年份=%d，TMDB季年份=%d",
                    matched ? "命中" : "未命中", item.getTitle(), seasonNumber, sourceYear, seasonYear);
            return matched;
        } catch (Throwable ignored) {
            logTmdbMatch("候选季年检查失败：标题=%s，季=%d，错误=%s", item.getTitle(), seasonNumber, ignored.getMessage());
            return false;
        }
    }

    private int tmdbSeasonYear(JsonObject detail, int seasonNumber) {
        try {
            for (JsonElement element : array(detail, "seasons")) {
                try {
                    if (!element.isJsonObject()) continue;
                    JsonObject object = element.getAsJsonObject();
                    if (!object.has("season_number") || object.get("season_number").isJsonNull()) continue;
                    if (object.get("season_number").getAsInt() != seasonNumber) continue;
                    int year = firstYear(string(object, "air_date"));
                    return year > 0 ? year : firstYear(string(object, "name"));
                } catch (ClassCastException e) {
                    android.util.Log.e("TmdbDetailActivity", "ClassCastException in tmdbSeasonYear parsing element: " + e.getMessage());
                } catch (Throwable e) {
                    android.util.Log.e("TmdbDetailActivity", "Error parsing season element: " + e.getMessage());
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("TmdbDetailActivity", "Error in tmdbSeasonYear: " + e.getMessage(), e);
        }
        return 0;
    }

    private int scoreTmdbMediaType(TmdbItem item, @Nullable Vod sourceVod) {
        // 类型只做轻量加减分，不作为放宽条件：标题/年份不一致的候选不会走到这里。
        if (sourceVod == null) return 0;
        String type = normalize(sourceVod.getTypeName() + " " + sourceVod.getRemarks());
        if (TextUtils.isEmpty(type)) return 0;
        boolean tv = type.contains("剧") || type.contains("集") || type.contains("连续") || type.contains("电视剧");
        boolean movie = type.contains("电影") || type.contains("影片");
        if (tv && "tv".equalsIgnoreCase(item.getMediaType())) return 20;
        if (movie && "movie".equalsIgnoreCase(item.getMediaType())) return 20;
        if ((tv && "movie".equalsIgnoreCase(item.getMediaType())) || (movie && "tv".equalsIgnoreCase(item.getMediaType()))) return -30;
        return 0;
    }

    private int scoreTmdbPeople(TmdbItem item, @Nullable Vod sourceVod) {
        // 部分入口会带有 credit 信息，此处只做低权重加分；主要的演员/主创消歧在详情拉取后完成。
        if (sourceVod == null) return 0;
        String text = normalize(sourceVod.getActor() + " " + sourceVod.getDirector() + " " + sourceVod.getContent());
        String credit = normalize(item.getCredit());
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(credit)) return 0;
        return text.contains(credit) || credit.contains(text) ? 80 : 0;
    }

    private int scoreTmdbPeople(JsonObject detail, Vod sourceVod) {
        // 用 TMDB 详情里的演员和主创完整姓名，去站源演员、导演、简介中查找。
        // 命中越多，说明同名候选越可能是同一部作品；但它只负责同名候选排序，不负责扩大候选范围。
        if (sourceVod == null) return 0;
        String source = normalize(sourceVod.getActor() + " " + sourceVod.getDirector() + " " + sourceVod.getContent());
        if (TextUtils.isEmpty(source)) return 0;
        int score = 0;
        int hits = 0;
        for (TmdbPerson person : tmdbService.cast(detail, tmdbConfig)) {
            if (hits >= 3) break;
            String name = normalize(person.getName());
            if (name.length() < 2 || !source.contains(name)) continue;
            logTmdbMatch("演员命中：%s，角色/说明=%s，加分=80", person.getName(), person.getSubtitle());
            score += 80;
            hits++;
        }
        for (TmdbPerson person : tmdbService.creators(detail, tmdbConfig)) {
            if (hits >= 4) break;
            String name = normalize(person.getName());
            if (name.length() < 2 || !source.contains(name)) continue;
            logTmdbMatch("主创命中：%s，职位=%s，加分=70", person.getName(), person.getSubtitle());
            score += 70;
            hits++;
        }
        return score;
    }

    private boolean hasSourcePeople(@Nullable Vod sourceVod) {
        // 没有演员、导演、简介时，无法做可靠的人名消歧，继续弹出手动选择更稳妥。
        if (sourceVod == null) return false;
        return !TextUtils.isEmpty(sourceVod.getActor()) || !TextUtils.isEmpty(sourceVod.getDirector()) || !TextUtils.isEmpty(sourceVod.getContent());
    }

    private String tmdbMatchSourceText(String keyword, @Nullable Vod sourceVod) {
        StringBuilder builder = new StringBuilder(Objects.toString(keyword, ""));
        if (sourceVod != null) {
            builder.append(' ').append(Objects.toString(sourceVod.getName(), ""));
            builder.append(' ').append(Objects.toString(sourceVod.getRemarks(), ""));
        }
        builder.append(' ').append(getNameText());
        return builder.toString();
    }

    private int sourceYear(String keyword, @Nullable Vod sourceVod) {
        // 年份优先取站源详情字段；如果站源没有年份，再从标题或搜索词里提取。
        // 这样网盘/站源标题里带“2026”的情况，也能参与严格同年判断。
        int year = sourceVod == null ? 0 : firstYear(sourceVod.getYear());
        if (year > 0) return year;
        year = sourceVod == null ? 0 : firstYear(sourceVod.getName());
        if (year > 0) return year;
        year = firstYear(getNameText());
        return year > 0 ? year : firstYear(keyword);
    }

    private int sourceSeasonNumber(String keyword, @Nullable Vod sourceVod) {
        int number = sourceVod == null ? -1 : sourceSeasonNumber(sourceVod.getName());
        if (number >= 0) return number;
        number = sourceSeasonNumber(getNameText());
        return number >= 0 ? number : sourceSeasonNumber(keyword);
    }

    @Nullable
    private SplitYearQuery splitYearQuery(String keyword, @Nullable Vod sourceVod) {
        int year = sourceYear(keyword, sourceVod);
        if (year <= 0) return null;
        List<String> sources = new ArrayList<>();
        if (!TextUtils.isEmpty(keyword)) sources.add(keyword);
        if (sourceVod != null && !TextUtils.isEmpty(sourceVod.getName())) sources.add(sourceVod.getName());
        if (!TextUtils.isEmpty(getNameText())) sources.add(getNameText());
        for (String source : sources) {
            if (firstYear(source) != year) continue;
            String query = removeYearFromTitle(source, year);
            if (TextUtils.isEmpty(query) || normalize(query).equals(normalize(source))) continue;
            logTmdbMatch("标题拆年：原始=%s，标题=%s，年份=%d", source, query, year);
            return new SplitYearQuery(query, year);
        }
        return null;
    }

    private String removeYearFromTitle(String text, int year) {
        String cleaned = Objects.toString(text, "").replaceAll("(?<!\\d)" + year + "(?!\\d)", " ");
        cleaned = cleaned.replaceAll("[\\[【「『(（]\\s*[\\]】」』)）]", " ");
        cleaned = cleaned.replaceAll("[._\\-+]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
        return cleanTmdbSearchQuery(cleaned);
    }

    private int firstYear(String text) {
        // 只识别 1900-2099 的四位年份，避免把集数、清晰度、评分等数字误当年份。
        Matcher matcher = Pattern.compile("(19\\d{2}|20\\d{2})").matcher(Objects.toString(text, ""));
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year >= 1900 && year <= 2099) return year;
        }
        return 0;
    }

    private void logTmdbMatch(String format, Object... args) {
        SpiderDebug.log("tmdb-match", format, args);
    }

    private int firstSeasonNumber(JsonObject detail) {
        try {
            JsonArray seasons = array(detail, "seasons");
            int fallback = -1;
            for (JsonElement element : seasons) {
                try {
                    if (!element.isJsonObject()) continue;
                    JsonObject object = element.getAsJsonObject();
                    if (!object.has("season_number") || object.get("season_number").isJsonNull()) continue;
                    int number = object.get("season_number").getAsInt();
                    if (number > 0) return number;
                    if (fallback == -1) fallback = number;
                } catch (ClassCastException e) {
                    android.util.Log.e("TmdbDetailActivity", "ClassCastException in firstSeasonNumber: " + e.getMessage());
                } catch (Throwable e) {
                    android.util.Log.e("TmdbDetailActivity", "Error parsing season in firstSeasonNumber: " + e.getMessage());
                }
            }
            return fallback;
        } catch (Throwable e) {
            android.util.Log.e("TmdbDetailActivity", "Error in firstSeasonNumber: " + e.getMessage(), e);
            return -1;
        }
    }

    private Map<Integer, Integer> seasonEpisodeCounts(JsonObject detail) {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        JsonArray seasons = array(detail, "seasons");
        for (JsonElement element : seasons) addSeasonCount(counts, element, true);
        if (counts.isEmpty()) for (JsonElement element : seasons) addSeasonCount(counts, element, false);
        return counts;
    }

    private void addSeasonCount(Map<Integer, Integer> counts, JsonElement element, boolean regularOnly) {
        try {
            if (!element.isJsonObject()) return;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("season_number") || object.get("season_number").isJsonNull()) return;
            int number = object.get("season_number").getAsInt();
            if (regularOnly && number <= 0) return;
            int count = object.has("episode_count") && !object.get("episode_count").isJsonNull() ? object.get("episode_count").getAsInt() : 0;
            counts.put(number, count);
        } catch (ClassCastException e) {
            android.util.Log.e("TmdbDetailActivity", "ClassCastException in addSeasonCount: " + e.getMessage());
        } catch (Throwable e) {
            android.util.Log.e("TmdbDetailActivity", "Error in addSeasonCount: " + e.getMessage());
        }
    }

    private String normalize(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private String cleanTmdbSearchQuery(String text) {
        String raw = Objects.toString(text, "").trim();
        if (TextUtils.isEmpty(raw)) return "";
        String cleaned = raw;
        // TMDB 搜索词清洗只负责去掉资源站常见附加信息，不改变后面的严格匹配规则。
        // 例如“怪奇物语 第五季 [臻彩]”会搜“怪奇物语”，但候选标题仍必须等于“怪奇物语”。
        cleaned = cleaned.replaceAll("(?i)\\.(mkv|mp4|avi|mov|wmv|flv|rmvb|ts|m2ts)$", "");
        cleaned = removeNoiseBrackets(cleaned);
        cleaned = cleaned.replaceAll("(?i)\\b(S\\d{1,2}|Season\\s*\\d{1,2})\\b", " ");
        cleaned = cleaned.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[季部]", " ");
        cleaned = cleaned.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[集话話]", " ");
        cleaned = cleaned.replaceAll("(全|共|更新至|更至|连载至|連載至)\\s*[0-9一二三四五六七八九十百零〇]+\\s*[集话話]", " ");
        cleaned = cleaned.replaceAll("(?i)\\b(4K|8K|1080P|2160P|720P|HDR|HDR10|DV|WEB[- ]?DL|BluRay|BDRip|Remux|HEVC|H\\.?265|H\\.?264|x265|x264|AAC|DTS|DDP?5?\\.?1|Atmos|NF|Netflix|AMZN|DSNP)\\b", " ");
        cleaned = cleaned.replaceAll("(?i)(?<!\\d)(?:24|25|30|50|60|120)\\s*(?:fps|帧)(?![\\u4e00-\\u9fffA-Za-z0-9])", " ");
        cleaned = cleaned.replaceAll("(更新至|更至|连载至|連載至)\\s*$", " ");
        cleaned = cleaned.replaceAll("(国语版|国配版|普通话版|粤语版|台语版|闽南语版|原声版|配音版|中字版|字幕版|台版|台灣版|台湾版|港版|港澳版|大陆版|內地版|内地版|中国版|中國版|泰版|泰国版|泰國版|韩版|韩国版|韓國版|日版|日本版|美版|美国版|美國版|英版|英国版|英國版)", " ");
        cleaned = cleaned.replaceAll("(真彩|臻彩|高码|高码率|无水印|无台标|国语|国配|国粤|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减|加长版|修复版)", " ");
        cleaned = cleaned.replaceAll("[._\\-+]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
        if (TextUtils.isEmpty(cleaned)) cleaned = raw;
        if (!raw.equals(cleaned)) logTmdbMatch("搜索词清洗：原始=%s，清洗后=%s", raw, cleaned);
        return cleaned;
    }

    private String removeNoiseBrackets(String text) {
        Matcher matcher = Pattern.compile("[\\[【「『(（]([^\\]】」』)）]{1,40})[\\]】」』)）]").matcher(text);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            matcher.appendReplacement(buffer, isTmdbNoiseTag(value) ? " " : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isTmdbNoiseTag(String text) {
        String value = normalize(text);
        if (TextUtils.isEmpty(value)) return true;
        return value.matches("(?i).*(4k|8k|1080p|2160p|720p|hdr|hdr10|dv|webdl|bluray|bdrip|remux|hevc|h265|h264|x265|x264|aac|dts|ddp|atmos|nf|netflix|amzn|dsnp).*")
                || value.matches(".*(真彩|臻彩|高码|高码率|无水印|无台标|国语|国配|国粤|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减|加长版|修复版).*")
                || value.matches(".*(国语版|国配版|普通话版|粤语版|台语版|闽南语版|原声版|配音版|中字版|字幕版|台版|台灣版|台湾版|港版|港澳版|大陆版|內地版|内地版|中国版|中國版|泰版|泰国版|泰國版|韩版|韩国版|韓國版|日版|日本版|美版|美国版|美國版|英版|英国版|英國版).*");
    }

    private Flag findInitialFlag(List<Flag> flags) {
        String requestedEpisodeUrl = getIntentPlaybackEpisodeUrl();
        String requestedFlagKey = getIntentPlaybackFlagKey();
        String requestedFlag = getIntentPlaybackFlag();
        Flag requested = TmdbUIAdapter.selectPlaybackFlag(
                flags, requestedFlagKey, requestedEpisodeUrl, requestedFlag);
        if (requested != null) return requested;
        // 用户在详情页显式切过的线路优先于历史记录：History.vodFlag 只在起播且有进度时才写，
        // 「切了线路没起播」或「从播放器返回后再切」都落不到库里，重进就会退回第一条线路。
        Flag preferred = findPreferredFlag(flags);
        if (preferred != null) return preferred;
        Flag selected = history == null ? null : TmdbUIAdapter.selectPlaybackFlag(
                flags, history.getSourceBindingKey(), history.getEpisodeUrl(), history.getVodFlag());
        return selected == null ? flags.get(0) : selected;
    }

    /**
     * 读取独立落盘的线路偏好。稳定键（线路名#索引）优先，避免同名线路串号；
     * 源站线路顺序变化后稳定键失效，退化用线路名匹配。
     */
    private Flag findPreferredFlag(List<Flag> flags) {
        FlagPreferenceCache.FlagPreference preference =
                FlagPreferenceCache.get().get(getKeyText(), getIdText());
        if (preference == null) return null;
        // 稳定键精确命中同名线路中的某一条。
        String stableKey = preference.getStableKey();
        if (!TextUtils.isEmpty(stableKey)) {
            for (int i = 0; i < flags.size(); i++) {
                if (TextUtils.equals(stableKey, TmdbUIAdapter.flagKey(flags.get(i), i))) return flags.get(i);
            }
        }
        // 源站线路增删导致索引漂移时退化用线路名匹配。
        String flagName = preference.getFlagName();
        if (TextUtils.isEmpty(flagName)) return null;
        for (Flag flag : flags) {
            if (flag != null && TextUtils.equals(flagName, flag.getFlag())) return flag;
        }
        return null;
    }

    /**
     * 记录用户显式选中的线路。不依赖播放状态，切一下就落盘。
     */
    private void savePreferredFlag(Flag flag) {
        if (flag == null) return;
        int index = TmdbUIAdapter.flagIndex(vod == null ? null : vod.getFlags(), flag);
        // 索引未知时不写稳定键：Flag.stableKey 会把 -1 夹成 0，留下一个会命中首条线路的假键。
        String stableKey = index < 0 ? "" : TmdbUIAdapter.flagKey(flag, index);
        FlagPreferenceCache.get().put(getKeyText(), getIdText(), stableKey, flag.getFlag());
        Task.execute(() -> FlagPreferenceCache.get().save());
    }

    private Episode findIntentPlaybackEpisode(Flag flag) {
        if (flag == null || flag.getEpisodes() == null || flag.getEpisodes().isEmpty()) return null;
        Episode byUrl = findEpisodeByUrl(getIntentPlaybackEpisodeUrl(), flag.getEpisodes());
        if (byUrl != null) return byUrl;
        String requestedFlagKey = getIntentPlaybackFlagKey();
        int index = TmdbUIAdapter.flagIndex(vod == null ? null : vod.getFlags(), flag);
        if (!TextUtils.isEmpty(requestedFlagKey)
                && !TextUtils.equals(requestedFlagKey, TmdbUIAdapter.flagKey(flag, index))) return null;
        String requestedFlag = getIntentPlaybackFlag();
        if (TextUtils.isEmpty(requestedFlagKey) && !TextUtils.isEmpty(requestedFlag)
                && !TextUtils.equals(requestedFlag, flag.getFlag())) return null;
        String requestedName = getIntentPlaybackEpisodeName();
        return TextUtils.isEmpty(requestedName) ? null : flag.find(requestedName, false);
    }

    private MaterialButton createChipButton(String text) {
        MaterialButton button = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setCheckable(false);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setMaxWidth(ResUtil.dp2px(CHIP_MAX_WIDTH_DP));
        button.setEllipsize(TextUtils.TruncateAt.MARQUEE);
        button.setMarqueeRepeatLimit(-1);
        button.setHorizontallyScrolling(true);
        button.setSingleLine(true);
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
        button.setSelected(!Util.isLeanback() || selected || focused);
        button.setStrokeWidth(ResUtil.dp2px(focused ? FOCUS_STROKE_DP : (selected ? 2 : CHIP_STROKE_DP)));
        button.setStrokeColor(ColorStateList.valueOf(focused ? FOCUS_STROKE : (selected ? colors.accent : colors.line)));
    }

    private void styleMetaChips() {
        if (binding == null || binding.metaContainer == null) return;
        ThemeColors colors = lightTheme ? ThemeColors.light() : ThemeColors.dark();
        for (int i = 0; i < binding.metaContainer.getChildCount(); i++) {
            View child = binding.metaContainer.getChildAt(i);
            if (!(child instanceof TextView chip)) continue;
            chip.setTextColor(colors.primary);
            GradientDrawable background = new GradientDrawable();
            background.setColor(colors.chip);
            background.setCornerRadius(999f);
            background.setStroke(1, colors.line);
            chip.setBackground(background);
        }
    }

    private void addMetaChip(String text) {
        if (TextUtils.isEmpty(text)) return;
        text = normalizeImdbBrand(text);
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

    private String normalizeImdbBrand(String text) {
        if (TextUtils.isEmpty(text)) return text;
        if (text.regionMatches(true, 0, "imdb", 0, 4)) return "IMDB" + text.substring(4);
        return text.replace("IMDb", "IMDB");
    }

    private String buildSubtitle() {
        return TmdbDetailLabels.headerSubtitle(releaseDate());
    }

    private TmdbEpisodeInfo tmdbEpisodeInfo() {
        int sourceSeason = currentSeasonContextNumber();
        if (cachedEpisodeInfo == null
                || cachedEpisodeInfoItem != matchedTmdbItem
                || cachedEpisodeInfoDetail != matchedTmdbDetail
                || cachedEpisodeInfoSeason != sourceSeason) {
            String mediaType = matchedTmdbItem == null ? "" : matchedTmdbItem.getMediaType();
            cachedEpisodeInfo = TmdbEpisodeInfo.from(mediaType, matchedTmdbDetail, sourceSeason);
            cachedEpisodeInfoItem = matchedTmdbItem;
            cachedEpisodeInfoDetail = matchedTmdbDetail;
            cachedEpisodeInfoSeason = sourceSeason;
        }
        return cachedEpisodeInfo;
    }

    private String releaseDate() {
        if (matchedTmdbDetail == null) return hasTmdbOverview() ? tmdbItemYear() : vod.getYear();
        return string(matchedTmdbDetail, "first_air_date", "release_date");
    }

    private TmdbItem playbackTmdbItem() {
        if (matchedTmdbItem == null) return null;
        return new TmdbItem(
                matchedTmdbItem.getTmdbId(),
                matchedTmdbItem.getMediaType(),
                matchedTmdbTitle(),
                buildSubtitle(),
                displayOverview(),
                TextUtils.isEmpty(matchedTmdbItem.getPosterUrl()) ? vod.getPic() : matchedTmdbItem.getPosterUrl(),
                matchedTmdbItem.getBackdropUrl(),
                matchedTmdbItem.getCredit(),
                matchedTmdbItem.getRating(),
                matchedTmdbItem.getOriginalLanguage(),
                matchedTmdbItem.getOriginCountry(),
                matchedTmdbItem.getGenreIds(),
                matchedTmdbItem.getDepartment(),
                matchedTmdbItem.getTmdbRating(),
                matchedTmdbItem.getDoubanRating(),
                matchedTmdbItem.getRecommendationReason());
    }

    private Vod playbackTmdbVod() {
        if (vod == null) return null;
        Vod item = new Vod();
        item.setName(playbackHistoryName());
        item.setContent(displayOverview());
        item.setPic(playbackHistoryPic());
        item.setYear(yearLabel());
        item.setArea(coalesce(firstCountry(), vod.getArea()));
        item.setTypeName(coalesce(firstGenre(), vod.getTypeName()));
        item.setDirector(coalesce(firstCrew("Director"), vod.getDirector()));
        item.setActor(coalesce(castNames(), vod.getActor()));
        item.setRemarks(coalesce(tmdbEpisodeInfo().detailText(this), getMarkText(), vod.getRemarks()));
        return item;
    }

    private String castNames() {
        List<String> names = new ArrayList<>();
        for (TmdbPerson person : detailCastItems) {
            String name = person.getName();
            if (TextUtils.isEmpty(name) || names.contains(name)) continue;
            names.add(name);
            if (names.size() >= 8) break;
        }
        return TextUtils.join(" / ", names);
    }

    private String yearLabel() {
        String date = releaseDate();
        if (!TextUtils.isEmpty(date) && date.length() >= 4) return date.substring(0, 4);
        return vod == null ? "" : vod.getYear();
    }

    private String metaYear() {
        if (matchedTmdbDetail != null) return yearLabel();
        if (hasTmdbOverview()) return tmdbItemYear();
        return vod == null ? "" : vod.getYear();
    }

    private String ratingLabel() {
        if (matchedTmdbDetail == null || !matchedTmdbDetail.has("vote_average") || matchedTmdbDetail.get("vote_average").isJsonNull()) return "";
        return getString(R.string.detail_score, String.format(Locale.US, "%.1f", matchedTmdbDetail.get("vote_average").getAsDouble()));
    }

    private boolean hasTmdbOverview() {
        return !TextUtils.isEmpty(tmdbOverview());
    }

    private String displayOverview() {
        String overview = tmdbOverview();
        if (TextUtils.isEmpty(overview) && vod != null) overview = vod.getContent();
        return TextUtils.isEmpty(overview) ? "" : overview.trim();
    }

    private String tmdbOverview() {
        String overview = matchedTmdbDetail == null ? "" : tmdbService.translatedOverview(matchedTmdbDetail, tmdbConfig);
        if (TextUtils.isEmpty(overview) && matchedTmdbItem != null) overview = matchedTmdbItem.getOverview();
        return TextUtils.isEmpty(overview) ? "" : overview.trim();
    }

    private String tmdbItemYear() {
        if (matchedTmdbItem == null) return "";
        String subtitle = matchedTmdbItem.getSubtitle();
        return !TextUtils.isEmpty(subtitle) && subtitle.length() >= 4 ? subtitle.substring(0, 4) : "";
    }

    private String certificationLabel() {
        if (matchedTmdbDetail == null) return "";
        boolean tv = matchedTmdbItem != null && "tv".equalsIgnoreCase(matchedTmdbItem.getMediaType());
        JsonArray results = tv ? array(matchedTmdbDetail, "content_ratings", "results") : array(matchedTmdbDetail, "release_dates", "results");
        String region = regionFromLanguage(tmdbConfig == null ? "" : tmdbConfig.getLanguage());
        String value = certificationForRegion(results, region, tv);
        if (TextUtils.isEmpty(value)) value = certificationForRegion(results, "US", tv);
        if (TextUtils.isEmpty(value)) value = firstCertification(results, tv);
        return TmdbDetailLabels.certificationLabel(value);
    }

    private String certificationForRegion(JsonArray results, String region, boolean tv) {
        try {
            if (TextUtils.isEmpty(region)) return "";
            for (JsonElement element : results) {
                try {
                    if (!element.isJsonObject()) continue;
                    JsonObject object = element.getAsJsonObject();
                    if (!region.equalsIgnoreCase(string(object, "iso_3166_1"))) continue;
                    return tv ? string(object, "rating") : firstReleaseCertification(object);
                } catch (ClassCastException e) {
                    android.util.Log.e("TmdbDetailActivity", "ClassCastException in certificationForRegion element: " + e.getMessage());
                } catch (Throwable e) {
                    android.util.Log.e("TmdbDetailActivity", "Error parsing certification element: " + e.getMessage());
                }
            }
        } catch (Throwable e) {
            android.util.Log.e("TmdbDetailActivity", "Error in certificationForRegion: " + e.getMessage(), e);
        }
        return "";
    }

    private String firstCertification(JsonArray results, boolean tv) {
        for (JsonElement element : results) {
            if (!element.isJsonObject()) continue;
            String value = tv ? string(element.getAsJsonObject(), "rating") : firstReleaseCertification(element.getAsJsonObject());
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String firstReleaseCertification(JsonObject object) {
        for (JsonElement release : array(object, "release_dates")) {
            if (!release.isJsonObject()) continue;
            String value = string(release.getAsJsonObject(), "certification");
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private String regionFromLanguage(String language) {
        if (TextUtils.isEmpty(language)) return "";
        int separator = language.indexOf('-');
        return separator >= 0 && separator + 1 < language.length() ? language.substring(separator + 1).toUpperCase(Locale.ROOT) : "";
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

    private JsonObject object(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return null;
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return null;
            current = currentObject.get(key);
        }
        return current != null && current.isJsonObject() ? current.getAsJsonObject() : null;
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

    private static boolean isShortDramaSiteEnabled(String key) {
        Site site = VodConfig.get().getSite(key);
        return Setting.isShortDramaSiteEnabled(key, site == null ? "" : site.getName());
    }

    private boolean isShortDramaSource() {
        Site site = getCurrentSite();
        return Setting.isShortDramaSiteEnabled(site == null ? getKeyText() : site.getKey(), site == null ? "" : site.getName());
    }

    private String getSiteName() {
        Site site = getCurrentSite();
        return site == null ? getKeyText() : site.getDisplayName();
    }

    private String getHistoryKey() {
        return getKeyText() + AppDatabase.SYMBOL + getIdText() + AppDatabase.SYMBOL + VodConfig.getCid();
    }

    private void syncDanmakuCompatHistory() {
        mHistory = history;
    }

    @androidx.annotation.Keep
    private String getKey() {
        return getKeyText();
    }

    @androidx.annotation.Keep
    private String getId() {
        return getIdText();
    }

    @androidx.annotation.Keep
    private String getName() {
        return getNameText();
    }

    @androidx.annotation.Keep
    private String getPic() {
        return getPicText();
    }

    @androidx.annotation.Keep
    private String getMark() {
        return getMarkText();
    }

    @androidx.annotation.Keep
    private Site getSite() {
        Site site = getCurrentSite();
        return site == null ? new Site() : site;
    }

    @androidx.annotation.Keep
    private Flag getFlag() {
        return selectedFlag == null ? new Flag() : selectedFlag;
    }

    @androidx.annotation.Keep
    private Episode getEpisode() {
        return selectedEpisode == null ? new Episode() : selectedEpisode;
    }

    @androidx.annotation.Keep
    private History getHistory() {
        return mHistory == null ? history : mHistory;
    }

    private boolean isResumeFromHistory() {
        return getIntent().getBooleanExtra(EXTRA_RESUME_FROM_HISTORY, false);
    }

    private History getIntentResumeHistory() {
        String key = Objects.toString(getIntent().getStringExtra(EXTRA_RESUME_HISTORY_KEY), "");
        if (key.isEmpty()) return null;
        return HistoryResumePayload.restore(
                getIntent().getIntExtra(EXTRA_RESUME_HISTORY_CID, VodConfig.getCid()), key);
    }

    private String getIntentPlaybackFlag() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_PLAY_FLAG), "");
    }

    private String getIntentPlaybackFlagKey() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_PLAY_FLAG_KEY), "");
    }

    private String getIntentPlaybackEpisodeName() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_PLAY_EPISODE_NAME), "");
    }

    private String getIntentPlaybackEpisodeUrl() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_PLAY_EPISODE_URL), "");
    }

    private int getIntentPlaybackSeasonNumber() {
        return getIntent().getIntExtra(EXTRA_PLAY_SEASON_NUMBER, -1);
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

    private String getTmdbPosterText() {
        return TmdbImageSelector.originalUrl(Objects.toString(getIntent().getStringExtra("tmdb_poster"), ""));
    }

    private String getBackdropText() {
        return Objects.toString(getIntent().getStringExtra("tmdb_backdrop"), "");
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

    private record TmdbBundle(TmdbItem item, JsonObject detail, List<TmdbPerson> cast, List<TmdbPerson> creators, List<String> photos, List<TmdbItem> related, List<Integer> seasons, Map<Integer, Integer> seasonCounts, Map<Integer, List<TmdbEpisode>> seasonEpisodes, Map<Integer, List<TmdbPerson>> seasonCast, Map<Integer, List<String>> seasonPhotos) {
    }

    private record TmdbLoadResult(TmdbBundle bundle, List<TmdbItem> searchItems) {
    }

    private record AutoTmdbMatch(@Nullable TmdbItem item, List<TmdbItem> items) {
    }

    private record SplitYearQuery(String query, int year) {
    }

    private record SplitYearSearch(String query, int year, List<TmdbItem> items) {
    }

    private record SourceEpisodeSeason(String name, int season) {
    }

    private record EpisodePosition(int season, int number) {
    }

    private record SourceMatch(
            Site site,
            Vod vod,
            int score,
            int seasonNumber,
            String sourceFlag,
            String sourceFlagKey,
            String episodeName,
            String episodeUrl,
            int resumeHistoryCid,
            String resumeHistoryPayload) {

        private SourceMatch(Site site, Vod vod, int score) {
            this(site, vod, score, -1, "", "", "", "", 0, "");
        }
    }

    private record TmdbCandidate(TmdbItem item, int titleScore, int score, @Nullable TmdbBundle bundle) {
    }

    private record RatingChipTag(String platform, int color) {
    }

    private record ThemeColors(int background, int panel, int control, int chip, int chipActive, int line, int lineStrong, int primary, int secondary, int muted, int body, int accent, int play, int backdropShade) {

        static ThemeColors dark() {
            return new ThemeColors(
                    0xFF0F141A,
                    0xA6141B23,
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
                    0x180F141A
            );
        }

        static ThemeColors light() {
            return new ThemeColors(
                    0xFFF3F6F9,
                    0xAFFFFFFF,
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
                    0x00F7FAFC
            );
        }

        static ThemeColors cinema(boolean light) {
            TmdbCinemaTheme.Palette palette = TmdbCinemaTheme.palette(light);
            return new ThemeColors(
                    palette.background(),
                    palette.panel(),
                    palette.control(),
                    palette.chip(),
                    palette.chipActive(),
                    palette.line(),
                    palette.lineStrong(),
                    palette.primary(),
                    palette.secondary(),
                    palette.muted(),
                    palette.body(),
                    palette.accent(),
                    palette.play(),
                    palette.backdropShade()
            );
        }
    }
}
