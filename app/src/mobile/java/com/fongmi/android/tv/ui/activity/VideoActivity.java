package com.fongmi.android.tv.ui.activity;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.ChangeBounds;
import androidx.transition.TransitionManager;
import androidx.viewbinding.ViewBinding;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.AdBlockStatsStore;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.ad.feedback.AdFeedbackController;
import com.fongmi.android.tv.ad.feedback.AdFeedbackHostAdapter;
import com.fongmi.android.tv.ad.feedback.AdFeedbackSession;
import com.fongmi.android.tv.ad.feedback.AdRulePlanApplier;
import com.fongmi.android.tv.ui.dialog.AdFeedbackDialog;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.DownloadItem;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.EpisodePositionCache;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonSnapshot;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.player.IntroSkipKinds;
import com.fongmi.android.tv.player.IntroSkipPlayback;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.MultiThreadProxySetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.TmdbSitePolicy;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbRecommendationAdapter;
import com.fongmi.android.tv.ui.audio.AudioPlaybackResolver;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.CustomKeyDown;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.AdRuleEditDialog;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.CodecCapabilityDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.DownloadEpisodeDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.LutPanelDialog;
import com.fongmi.android.tv.ui.dialog.MultiThreadProxyDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleManualSearchDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSearchDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.dialog.VideoContentDialog;
import com.fongmi.android.tv.ui.helper.DetailThemeVisibility;
import com.fongmi.android.tv.ui.helper.EpisodeDisplayPolicy;
import com.fongmi.android.tv.ui.helper.EpisodeCardImagePolicy;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonPolicy;
import com.fongmi.android.tv.ui.helper.SourceEpisodeSeasonCache;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;
import com.fongmi.android.tv.ui.helper.PlayerControlFocusHelper;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.ui.helper.VodEventGuard;
import com.fongmi.android.tv.ui.player.VodPlayerChrome;
import com.fongmi.android.tv.ui.player.VodPlayerUiController;
import com.fongmi.android.tv.ui.player.VodPlayerUiHost;
import com.fongmi.android.tv.utils.ActivityLaunch;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.DownloadManager;
import com.fongmi.android.tv.utils.EpisodeHistoryTitleResolver;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.PushParser;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.TmdbDetailCache;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.VodDetailCache;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.text.InputType;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
import androidx.palette.graphics.Palette;
import com.fongmi.android.tv.player.karaoke.KaraokeController;
import com.fongmi.android.tv.player.karaoke.KaraokePitchTrackGenerator;
import com.fongmi.android.tv.player.karaoke.KaraokeResult;
import com.fongmi.android.tv.player.karaoke.KaraokeTrackRepository;
import com.fongmi.android.tv.player.lyrics.AudioPlaylistStore;
import com.fongmi.android.tv.player.lyrics.LyricsController;
import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsRepository;
import com.fongmi.android.tv.player.lyrics.LyricsRequest;
import com.fongmi.android.tv.player.lyrics.LyricsResult;
import com.fongmi.android.tv.setting.LyricsSetting;
import com.fongmi.android.tv.ui.custom.AudioPlayerBackgroundDrawable;
import com.fongmi.android.tv.ui.custom.KaraokeResultView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoActivity extends PlaybackActivity implements Clock.Callback, CustomKeyDown.Listener, TrackDialog.Listener, ControlDialog.Listener, DanmakuDialog.Host, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener, SubtitlePlaybackSession.Host, com.fongmi.android.tv.ui.novel.NovelReaderHost {
    private static final long LYRICS_OFFSET_MIN_MS = -5000L;
    private static final long LYRICS_OFFSET_MAX_MS = 5000L;
    private static final long LYRICS_OFFSET_STEP_MS = 500L;
    private static final long KARAOKE_DELAY_MIN_MS = -1000L;
    private static final long KARAOKE_DELAY_MAX_MS = 1000L;
    private static final long KARAOKE_DELAY_STEP_MS = 100L;
    private static final int LYRICS_TAB_LYRICS = 0;
    private static final int LYRICS_TAB_KARAOKE = 1;
    private static final int LYRICS_TAB_TRACK = 2;
    private static final int AUDIO_QUEUE_TAB_CURRENT = 0;
    private static final int AUDIO_QUEUE_TAB_SEARCH = 1;
    private static final String STATE_KARAOKE_RESULT = "karaoke_result";
    private static final String STATE_KARAOKE_RESULT_ACTION = "karaoke_result_action";
    private static final int KARAOKE_RESULT_ACTION_NONE = 0;
    private static final int KARAOKE_RESULT_ACTION_NEXT = 1;
    private static final int KARAOKE_RESULT_ACTION_NEXT_SILENT = 2;
    private static final int KARAOKE_RESULT_ACTION_FINISH = 3;
    private static final int KARAOKE_RESULT_ACTION_SYSTEM_BACK = 4;
    private static final int SHEET_BUTTON_RADIUS_DP = 6;
    private static final int SHEET_SEGMENT_RADIUS_DP = 5;
    private static final int SHEET_TEXT_PRIMARY = 0xFFFFFFFF;
    private static final int SHEET_TEXT_SECONDARY = 0xD9FFFFFF;
    private static final int SHEET_TEXT_MUTED = 0x8CFFFFFF;
    private static final int SHEET_CONTROL_BG = 0x1FFFFFFF;
    private static final int SHEET_CONTROL_BG_SELECTED = 0x3DFFFFFF;
    private static final int SHEET_CONTROL_BG_SUBTLE = 0x12FFFFFF;
    private static final int SHEET_CONTROL_STROKE = 0x24FFFFFF;
    private static final int SHEET_CONTROL_STROKE_SELECTED = 0x4DFFFFFF;

private LyricsController mLyrics;
private KaraokeController mKaraoke;
private boolean mAudioStageVisible;
private boolean mAudioLightEffectAnimated;
private boolean mKaraokeResultShown;
private int mKaraokeResultAction;
private KaraokeResult mPendingKaraokeResult;
private AlertDialog mKaraokeResultDialog;
private boolean mSuppressKaraokeResultAction;
private boolean mRestoringConfigurationPlayback;
private boolean mSkipKaraokeTrackAutoLoad;
private BottomSheetDialog mLyricsResultDialog;
private BottomSheetDialog mAudioQueueDialog;
private BottomSheetDialog mKaraokePitchDialog;
private ProgressBar mKaraokePitchProgress;
private TextView mKaraokePitchMessage;
private Future<?> mKaraokePitchFuture;
private AtomicBoolean mKaraokePitchCancel;
private ObjectAnimator mAudioCoverAnimator;
private LinearLayout mLyricsResultList;
private RecyclerView mAudioQueueList;
private AudioQueueAdapter mAudioQueueAdapter;
private LinearLayout mAudioQueueSearchList;
private TextView mAudioQueueStatus;
private List<LyricsResult> mLyricsSearchResults;
private String mLyricsSearchKeyword;
private String mLyricsLastSearchSignature;
private String mLyricsLastSearchKeyword;
private String mLyricsSelectedResultKey;
private String mDetailLyrics;
private String mInlineLyrics;
private long mLyricsLoopLastPlayerPosition = C.TIME_UNSET;
private boolean mLyricsLoopLastPlaying;
private String mPlaybackEpisodeKey;
private String mArtworkRequestUrl;
private String mArtworkRequestOwner;
private Vod mPendingDetailVod;
private Result mPendingPlayerResult;
private int playerContentGeneration;
private int playerContentRequestId;
private String playerContentKey = "";
private String playerContentFlag = "";
private String playerContentEpisode = "";
private Result mAppliedPlayerResult;
private AudioPlaybackResolver.Resolved mImmersiveAudioResolved;
private int mAudioArtworkColor = Color.rgb(55, 45, 68);
private final Map<String, String> mAudioQueueFlags = new HashMap<>();
private final Map<String, String> mAudioQueueTitles = new HashMap<>();
private final Map<String, String> mAudioQueueArtists = new HashMap<>();
private final Map<String, String> mAudioQueuePics = new HashMap<>();
private final Map<String, String> mAudioQueueLyrics = new HashMap<>();
private int mStatusBarInset;
private int mNavigationRightInset;
private int mLyricsSearchSeq;
private int mAudioQueueSearchSeq;
private int mAudioPlaylistCurrentIndex = -1;
private int mAudioBackgroundRandomNonce;

    private static final int SHORT_DRAMA_SCALE = 4;
    private static final int SHORT_DRAMA_EDGE_MARGIN_DP = 12;
    private static final int FUSION_PLAYER_TOP_MARGIN_DP = 72;
    private static final int FUSION_PLAYER_SIDE_MARGIN_DP = 16;
    private static final int FUSION_PLAYER_HEIGHT_DP = 252;
    private static final int FUSION_PLAYER_BOTTOM_GAP_DP = 14;
    private static final int EPISODE_CARD_HEIGHT_DP = 190;
    private static final int EPISODE_CARD_VERTICAL_MARGIN_DP = 12;
    private static final String EXTRA_TMDB_PLAY_FLAG = "tmdb_play_flag";
    private static final String EXTRA_TMDB_PLAY_FLAG_KEY = "tmdb_play_flag_key";
    private static final String EXTRA_TMDB_PLAY_EPISODE_NAME = "tmdb_play_episode_name";
    private static final String EXTRA_TMDB_PLAY_EPISODE_URL = "tmdb_play_episode_url";
    private static final String EXTRA_TMDB_PLAY_SEASON_NUMBER = "tmdb_play_season_number";
    private static final String EXTRA_TMDB_PLAY_EPISODE_NUMBER = "tmdb_play_episode_number";
    private static final String EXTRA_RESUME_FROM_HISTORY = "resume_from_history";
    private static final String EXTRA_RESUME_HISTORY_CID = "resume_history_cid";
    private static final String EXTRA_RESUME_HISTORY_KEY = "resume_history_key";
    private static final String EXTRA_TMDB_VOD_CACHE_KEY = "tmdb_vod_cache_key";
    private static final String EXTRA_TMDB_DETAIL_THEME = "tmdb_detail_theme";
    private static final String EXTRA_IMMERSIVE_AUDIO_CACHE_KEY = "immersive_audio_cache_key";
    private static final String EXTRA_SEARCH_KEYWORD = "search_keyword";
    private static final java.util.concurrent.ConcurrentHashMap<String, AudioPlaybackResolver.Resolved> IMMERSIVE_AUDIO_LAUNCHES = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int TMDB_TABLET_PLAYER_MIN_WIDTH_DP = 440;
    private static final int TMDB_TABLET_PLAYER_MAX_WIDTH_DP = 640;
    private static final int TMDB_TABLET_PLAYER_SIDE_MARGIN_DP = 24;
    private static final int TMDB_TABLET_PLAYER_GUTTER_DP = 16;
    private static final int TMDB_TABLET_PLAYER_TOP_MARGIN_DP = 16;
    private static final int TMDB_TABLET_SUMMARY_MIN_WIDTH_DP = 280;
    private static final int TMDB_DETAIL_LOAD_TIMEOUT = 15000;
    private static final int TMDB_CACHED_DETAIL_APPLY_DELAY_MS = 16;

    private static final String SIZE_TAG = "MPV_SIZE";

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private int mFrameHeight;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private EpisodeAdapter mEpisodeAdapter;
    private EpisodeGroupAdapter mEpisodeGroupAdapter;
    private SpaceItemDecoration mEpisodeDecoration;
    private QualityAdapter mQualityAdapter;
    private QuickAdapter mQuickAdapter;
    private QuickSearchDialog mQuickSearchDialog;
    private String mQuickSearchKeyword;
    private ParseAdapter mParseAdapter;
    private TmdbRecommendationAdapter mPersonalTmdbRecommendationAdapter;
    private TmdbRecommendationAdapter mPersonalDoubanRecommendationAdapter;
    private TmdbRecommendationAdapter mPersonalAiRecommendationAdapter;
    private PersonalRecommendationService.RecommendationPage mNativePersonalTmdbPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalDoubanPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalAiPage;
    private Map<String, View> mActionButtons;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private VodPlayerUiController mPlayerUi;
    private PlayerOsdController mOsd;
    private final IntroSkipPlayback mIntroSkipPlayback = new IntroSkipPlayback();
    private final SubtitlePlaybackSession subtitlePlaybackSession = new SubtitlePlaybackSession(this);
    private androidx.appcompat.app.AlertDialog mIntroSkipConfirmDialog;
    private ValueAnimator mAnimator;
    private CustomKeyDown mKeyDown;
    private View mNightModeOverlay;
    private int mNightModeLevel = PlayerSetting.NIGHT_MODE_OFF;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean revealManualSearch;
    private boolean useParse;
    private boolean rotate;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private boolean mNativePersonalTmdbLoading;
    private boolean mNativePersonalDoubanLoading;
    private boolean mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();
    private int playerKernelSwitchRequestId;
    private int decodeSwitchRequestId;
    private int mPendingPlayerKernel = PlayerSetting.NONE;
    private boolean decodeSwitchRefreshing;
    private int deferredFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
    private int mEpisodeSpanCount;
    private int mEpisodeBottomInset;
    private int mEpisodeMaxHeight = -1;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mSeekProgressFallback;
    private Runnable mTmdbDetailTimeout;
    private Clock mClock;
    private PiP mPiP;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;
    private long pendingResumeSeekMs = C.TIME_UNSET;
    private boolean tmdbHistoryResumePending;
    private final List<ShortDramaControlItem> mShortDramaControlItems = new ArrayList<>();
    private ViewGroup mShortDramaControlDock;
    private boolean shortDramaControlsDocked;
    private boolean shortDramaSession;
    /** setQualityVisible 最近一次的结论，供短剧 dock 图标复用，避免两个真值来源。 */
    private boolean mQualityVisible;

    // TMDB 模式相关字段
    private com.fongmi.android.tv.ui.helper.TmdbUIAdapter mTmdbUIAdapter;
    private com.fongmi.android.tv.ui.custom.TmdbHeaderView mTmdbHeaderView;
    private Vod mVod;
    private String mSourceVodName = "";
    private final SourceEpisodeSeasonCache mSourceEpisodeSeasonCache = new SourceEpisodeSeasonCache();
    private boolean mTmdbContentLoaded = false;
    private boolean mTmdbFallbackToNative = false;
    private boolean mTmdbControlsMoved = false;
    private boolean mTmdbAutoDialogShown = false;
    private boolean mFusionChromeApplied = false;
    private boolean mTmdbTabletLayoutApplied = false;
    private MaterialButton mFusionThemeButton;
    private View mFusionPlayerBottomSpacer;
    private int mTmdbDialogGeneration;
    private com.fongmi.android.tv.service.AiEpisodeSeasonService mAiSeasonService;
    private AlertDialog mAiSeasonLoadingDialog;
    private TmdbItem mPendingTmdbSeasonChoice;
    private int mPersonalRecommendationGeneration;
private final Task.Scope mPersonalRecommendationTasks = new Task.Scope(Task.recommendationExecutor());
    private AdFeedbackController mAdFeedback;
    private AdFeedbackHostAdapter mAdFeedbackHost;
    private AdFeedbackDialog mAdFeedbackDialog;
    /** 标记模式下已打的起点，-1 表示未进入标记模式。 */
    private long mAdMarkStartMs = -1;
    private final List<TmdbMovedView> mTmdbMovedViews = new ArrayList<>();
    private boolean pendingLutImport;
    private boolean skipPausePiP;
    private RelativeLayout.LayoutParams mDefaultFrameParams;

    private final ActivityResultLauncher<Intent> mLutDir = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        LutStore.setUserDir(result.getData().getData(), result.getData().getFlags());
        Notify.show(R.string.lut_directory_selected);
        if (hasLutQuick()) mBinding.lutQuick.refreshList();
        if (pendingLutImport) {
            pendingLutImport = false;
            chooseLutFile();
        }
    });

    private final ActivityResultLauncher<Intent> mLutFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.lut_import_failed);
            return;
        }
        Task.execute(() -> {
            try {
                LutPreset preset = LutStore.importFile(path);
                App.post(() -> {
                    Notify.show(R.string.lut_imported);
                    if (isFullscreen() && hasLutQuick()) mBinding.lutQuick.selectImported(preset, player(), mBinding.exo, this::onLutChanged);
                    else onLutSelected(preset);
                });
            } catch (Exception e) {
                if (SpiderDebug.isEnabled()) SpiderDebug.log("lut", "import failed path=%s error=%s", path, e.getMessage());
                App.post(() -> Notify.show(Notify.getError(R.string.lut_import_failed, e)));
            }
        });
    });
    private final ActivityResultLauncher<Intent> mKaraokeTrackFile = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null || service() == null) return;
        String path = FileChooser.getPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.player_karaoke_track_import_failed);
            return;
        }
        Task.execute(() -> {
            KaraokeTrackRepository.ImportResult imported;
            try {
                File file = new File(path);
                imported = KaraokeTrackRepository.importFile(player(), file);
            } catch (Exception e) {
                imported = KaraokeTrackRepository.ImportResult.fail(e.getMessage());
            }
            KaraokeTrackRepository.ImportResult finalImported = imported;
            App.post(() -> onKaraokeTrackImported(finalImported));
        });
    });


    public static void push(FragmentActivity activity, String text) {
        PushParser.Parsed push = PushParser.fromText(text);
        Uri uri = Uri.parse(push.getUrl());
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri), push.getTitle());
        else startPush(activity, push);
    }

    @Override
    protected Vod getReaderVod() {
        // 把当前正在播放的 Vod（含章节列表）交给阅读器路由，支持小说/漫画章节导航
        return mVod;
    }

    @Override
    protected boolean customWall() {
        return true;
    }

    @Override
    protected boolean customWallMotion() {
        return false;
    }

    public static void file(FragmentActivity activity, String path) {
        file(activity, path, "");
    }

    public static void file(FragmentActivity activity, String path, String title) {
        if (TextUtils.isEmpty(path)) return;
        PushParser.Parsed push = PushParser.of("file://" + path, TextUtils.isEmpty(title) ? new File(path).getName() : title);
        start(activity, SiteApi.PUSH, push.getId(), push.getName());
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, (TmdbItem) null, wallPic);
    }

    /** 搜索结果进入详情：把用户输入的搜索关键词一路带到 TMDB 自动匹配。 */
    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic, String searchKeyword) {
        start(activity, key, id, name, pic, null, true, (TmdbItem) null, wallPic, null, searchKeyword);
    }

    private static boolean canOpenLegacyTmdbDetail(String key, String id) {
        if (TextUtils.isEmpty(key)) return false;
        if (SiteApi.PUSH.equals(key)) return TmdbSitePolicy.isEnabled(key, id);
        return !AudioUtil.isAudioSiteEnabled(key) && !isShortDramaSiteEnabled(key) && TmdbSitePolicy.isEnabled(key, id);
    }

    private static boolean isShortDramaSiteEnabled(String key) {
        Site site = VodConfig.get().getSite(key);
        return Setting.isShortDramaSiteEnabled(key, site == null ? "" : site.getName());
    }

    private static boolean shouldOpenLegacyTmdbDetail(String key, String id) {
        int mode = Setting.getDetailOpenMode();
        return canOpenLegacyTmdbDetail(key, id) && Setting.isTmdbDetailPage() && Setting.isStandaloneTmdbDetailMode(mode);
    }

    public static void start(Activity activity, String url) {
        startPush(activity, PushParser.fromText(url));
    }

    private static void startPush(Activity activity, PushParser.Parsed push) {
        if (dispatchToContentHandler(activity, push.getUrl(), push.getTitle())) return;
        start(activity, SiteApi.PUSH, push.getId(), push.getName());
    }

    private static boolean dispatchToContentHandler(Activity activity, String url) {
        return dispatchToContentHandler(activity, url, "");
    }

    private static boolean dispatchToContentHandler(Activity activity, String url, String title) {
        return com.fongmi.android.tv.content.ContentDispatcher.dispatchUrl(activity, url, title);
    }

    public static void start(Activity activity, String key, String id, String name) {
        start(activity, key, id, name, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark) {
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic) {
        start(activity, key, id, name, pic, mark, false, (TmdbItem) null, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        start(activity, key, id, name, pic, mark, collect, (TmdbItem) null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, (TmdbItem) null, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, collect, (TmdbItem) null, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, collect, tmdbItem, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, tmdbItem, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, collect, tmdbItem, wallPic, content, "");
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content, String searchKeyword) {
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        if (dispatchToContentHandler(activity, key, id, name, pic, mark)) return;
        startSkippingDispatch(activity, key, id, name, pic, mark, collect, tmdbItem, wallPic, content, searchKeyword);
    }

    /** 跳过 ContentDispatcher 的启动路径（供阅读器等 handler 判定内容不归自己管时回退）。 */
    public static void startSkippingDispatch(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content) {
        startSkippingDispatch(activity, key, id, name, pic, mark, collect, tmdbItem, wallPic, content, "");
    }

    public static void startSkippingDispatch(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content, String searchKeyword) {
        if (tmdbItem == null && shouldOpenLegacyTmdbDetail(key, id)) {
            TmdbDetailActivity.start(activity, key, id, name, pic, mark, null, Setting.getDetailOpenMode(), searchKeyword);
            return;
        }
        Intent intent = new Intent(activity, VideoActivity.class);
        if (!TextUtils.isEmpty(searchKeyword)) intent.putExtra(EXTRA_SEARCH_KEYWORD, searchKeyword);
        intent.putExtra("tmdbMode", tmdbItem != null);
        intent.putExtra("tmdbItem", tmdbItem);
        intent.putExtra("collect", collect);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        intent.putExtra("content", content);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
    }

    public static void startWithTmdb(Activity activity, String key, String id, String name, String pic, String mark, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, false, tmdbItem);
    }

    public static void startFromHistory(Activity activity, History item) {
        if (shouldOpenLegacyTmdbDetail(item.getSiteKey(), item.getVodId())) {
            TmdbDetailActivity.startFromHistory(activity, item);
            return;
        }
        startDirect(activity, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getVodRemarks(),
                item.getVodFlag(), item.getVodRemarks(), item.getEpisodeUrl(), item);
    }

    public static void startFromResolvedHistory(Activity activity, History source, Vod target, Flag flag, Episode episode) {
        if (source == null || target == null || flag == null || episode == null) return;
        if (shouldOpenLegacyTmdbDetail(target.getSiteKey(), target.getId())) {
            TmdbDetailActivity.startFromResolvedHistory(activity, source, target, flag, episode);
            return;
        }
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", false);
        intent.putExtra("mark", episode.getName());
        intent.putExtra("name", target.getName());
        intent.putExtra("pic", target.getPic());
        intent.putExtra("wallPic", source.getWallPic());
        intent.putExtra("key", target.getSiteKey());
        intent.putExtra("id", target.getId());
        intent.putExtra(EXTRA_RESUME_FROM_HISTORY, true);
        intent.putExtra(EXTRA_RESUME_HISTORY_CID, source.getCid());
        intent.putExtra(EXTRA_RESUME_HISTORY_KEY, HistoryResumePayload.encode(source));
        int flagIndex = com.fongmi.android.tv.ui.helper.TmdbUIAdapter.flagIndex(target.getFlags(), flag);
        String flagKey = flagIndex < 0 ? ""
                : com.fongmi.android.tv.ui.helper.TmdbUIAdapter.flagKey(flag, flagIndex);
        putIntentPlaybackSelection(intent, flag.getFlag(), flagKey, episode.getName(), episode.getUrl());
        putDetailVodCache(intent, target);
        activity.startActivity(intent);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic) {
        startDirect(activity, key, id, name, pic, null);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic, String mark) {
        startDirect(activity, key, id, name, pic, mark, null, null, null);
    }

    public static boolean startImmersiveAudioSite(Activity activity, String key, String id, String name, String pic, String mark) {
        if (SiteApi.PUSH.equals(key)) return false;
        if (!PlayerSetting.isImmersiveAudioMode()) return false;
        if (!AudioUtil.isAudioSiteEnabled(key)) return false;
        Notify.show("正在加载音频");
        Task.execute(() -> {
            try {
                AudioPlaybackResolver.Resolved resolved = AudioPlaybackResolver.resolveSite(key, id, name, pic, mark);
                App.post(() -> ActivityLaunch.postOnAnimation(activity, () -> startResolvedImmersiveAudio(activity, resolved, mark)));
            } catch (Throwable e) {
                App.post(() -> Notify.show(TextUtils.isEmpty(e.getMessage()) ? "音频加载失败" : e.getMessage()));
            }
        });
        return true;
    }

    private static void startResolvedImmersiveAudio(Activity activity, AudioPlaybackResolver.Resolved resolved, String mark) {
        Result result = resolved.getResult();
        Vod vod = resolved.getVod();
        Episode episode = resolved.getEpisode();
        String pic = result.hasArtwork() ? result.getArtwork() : vod.getPic();
        if (!TextUtils.isEmpty(pic)) ImgUtil.preload(activity, pic);
        String cacheKey = resolved.getSiteKey() + AppDatabase.SYMBOL + resolved.getVodId() + AppDatabase.SYMBOL + System.nanoTime();
        IMMERSIVE_AUDIO_LAUNCHES.put(cacheKey, resolved);
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", false);
        intent.putExtra("mark", mark);
        intent.putExtra("name", vod.getName());
        intent.putExtra("pic", pic);
        intent.putExtra("key", resolved.getSiteKey());
        intent.putExtra("id", resolved.getVodId());
        intent.putExtra(EXTRA_IMMERSIVE_AUDIO_CACHE_KEY, cacheKey);
        putIntentPlaybackSelection(intent, resolved.getFlag().getFlag(), episode.getName(), episode.getUrl());
        activity.startActivity(intent);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic, String mark,
            String playFlag, String playEpisodeName, String playEpisodeUrl) {
        startDirect(activity, key, id, name, pic, mark, playFlag, playEpisodeName, playEpisodeUrl, false);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic, String mark,
            String playFlag, String playEpisodeName, String playEpisodeUrl, boolean resumeFromHistory) {
        startDirect(activity, key, id, name, pic, mark, playFlag, "", playEpisodeName, playEpisodeUrl, resumeFromHistory, null);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic, String mark,
            String playFlag, String playEpisodeName, String playEpisodeUrl, History resumeHistory) {
        startDirect(activity, key, id, name, pic, mark, playFlag,
                resumeHistory == null ? "" : resumeHistory.getSourceBindingKey(),
                playEpisodeName, playEpisodeUrl, true, resumeHistory);
    }

    public static void startDirect(Activity activity, String key, String id, String name, String pic, String mark,
            String playFlag, String playFlagKey, String playEpisodeName, String playEpisodeUrl, History resumeHistory) {
        startDirect(activity, key, id, name, pic, mark, playFlag, playFlagKey,
                playEpisodeName, playEpisodeUrl, true, resumeHistory);
    }

    private static void startDirect(Activity activity, String key, String id, String name, String pic, String mark,
            String playFlag, String playFlagKey, String playEpisodeName, String playEpisodeUrl,
            boolean resumeFromHistory, History resumeHistory) {
        if (AudioActivity.startSite(activity, key, id, name, pic, mark)) return;
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", false);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra(EXTRA_RESUME_FROM_HISTORY, resumeFromHistory);
        if (resumeHistory != null) {
            intent.putExtra(EXTRA_RESUME_HISTORY_CID, resumeHistory.getCid());
            intent.putExtra(EXTRA_RESUME_HISTORY_KEY, HistoryResumePayload.encode(resumeHistory));
        }
        putIntentPlaybackSelection(intent, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl);
        activity.startActivity(intent);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod) {
        startDirectTmdb(activity, key, id, name, pic, mark, episodeTitles, item, tmdbVod, null, null, null);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        startDirectTmdb(activity, key, id, name, pic, mark, episodeTitles, item, tmdbVod, null, playFlag, playEpisodeName, playEpisodeUrl);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, Vod detailVod, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        startDirectTmdb(activity, key, id, name, pic, mark, episodeTitles, item, tmdbVod, detailVod, "", playFlag, playEpisodeName, playEpisodeUrl, -1, -1);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, Vod detailVod, String tmdbDetailCacheKey, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        startDirectTmdb(activity, key, id, name, pic, mark, episodeTitles, item, tmdbVod, detailVod, tmdbDetailCacheKey, playFlag, playEpisodeName, playEpisodeUrl, -1, -1);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, Vod detailVod, String tmdbDetailCacheKey, String playFlag, String playEpisodeName, String playEpisodeUrl, int playSeasonNumber, int playEpisodeNumber) {
        startDirectTmdb(activity, key, id, name, pic, mark, episodeTitles, item, tmdbVod, detailVod, tmdbDetailCacheKey, playFlag, playEpisodeName, playEpisodeUrl, playSeasonNumber, playEpisodeNumber, null);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, Vod detailVod, String tmdbDetailCacheKey, String playFlag, String playEpisodeName, String playEpisodeUrl, int playSeasonNumber, int playEpisodeNumber, History resumeHistory) {
        startDirectTmdb(activity, key, id, name, pic, mark, episodeTitles, item, tmdbVod, detailVod,
                tmdbDetailCacheKey, playFlag,
                resumeHistory == null ? "" : resumeHistory.getSourceBindingKey(),
                playEpisodeName, playEpisodeUrl, playSeasonNumber, playEpisodeNumber, resumeHistory);
    }

    public static void startDirectTmdb(Activity activity, String key, String id, String name, String pic, String mark, ArrayList<String> episodeTitles, TmdbItem item, Vod tmdbVod, Vod detailVod, String tmdbDetailCacheKey, String playFlag, String playFlagKey, String playEpisodeName, String playEpisodeUrl, int playSeasonNumber, int playEpisodeNumber, History resumeHistory) {
        if (AudioActivity.startSite(activity, key, id, name, pic, mark)) return;
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("tmdbMode", item != null);
        intent.putExtra(EXTRA_TMDB_DETAIL_THEME, Setting.getTmdbDetailTheme());
        intent.putExtra("tmdbItem", item);
        intent.putExtra("collect", false);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        intent.putExtra(EXTRA_RESUME_FROM_HISTORY, resumeHistory != null);
        if (resumeHistory != null) {
            intent.putExtra(EXTRA_RESUME_HISTORY_CID, resumeHistory.getCid());
            intent.putExtra(EXTRA_RESUME_HISTORY_KEY, HistoryResumePayload.encode(resumeHistory));
        }
        intent.putStringArrayListExtra("tmdb_episode_titles", episodeTitles);
        putIntentPlaybackSelection(intent, playFlag, playFlagKey, playEpisodeName, playEpisodeUrl);
        if (playEpisodeNumber > 0) {
            intent.putExtra(EXTRA_TMDB_PLAY_SEASON_NUMBER, Math.max(-1, playSeasonNumber));
            intent.putExtra(EXTRA_TMDB_PLAY_EPISODE_NUMBER, playEpisodeNumber);
        }
        putTmdbVod(intent, tmdbVod);
        putDetailVodCache(intent, detailVod);
        if (!TextUtils.isEmpty(tmdbDetailCacheKey)) intent.putExtra(TmdbDetailCache.EXTRA_KEY, tmdbDetailCacheKey);
        activity.startActivity(intent);
    }

    private static void putIntentPlaybackSelection(Intent intent, String playFlag, String playEpisodeName, String playEpisodeUrl) {
        putIntentPlaybackSelection(intent, playFlag, "", playEpisodeName, playEpisodeUrl);
    }

    private static void putIntentPlaybackSelection(Intent intent, String playFlag, String playFlagKey,
                                                   String playEpisodeName, String playEpisodeUrl) {
        if (!TextUtils.isEmpty(playFlag)) intent.putExtra(EXTRA_TMDB_PLAY_FLAG, playFlag);
        if (!TextUtils.isEmpty(playFlagKey)) intent.putExtra(EXTRA_TMDB_PLAY_FLAG_KEY, playFlagKey);
        if (!TextUtils.isEmpty(playEpisodeName)) intent.putExtra(EXTRA_TMDB_PLAY_EPISODE_NAME, playEpisodeName);
        if (!TextUtils.isEmpty(playEpisodeUrl)) intent.putExtra(EXTRA_TMDB_PLAY_EPISODE_URL, playEpisodeUrl);
    }

    private static void putTmdbVod(Intent intent, Vod vod) {
        if (vod == null) return;
        intent.putExtra("tmdb_vod_title", vod.getName());
        intent.putExtra("tmdb_vod_content", vod.getContent());
        intent.putExtra("tmdb_vod_pic", vod.getPic());
        intent.putExtra("tmdb_vod_year", vod.getYear());
        intent.putExtra("tmdb_vod_area", vod.getArea());
        intent.putExtra("tmdb_vod_type", vod.getTypeName());
        intent.putExtra("tmdb_vod_director", vod.getDirector());
        intent.putExtra("tmdb_vod_actor", vod.getActor());
        intent.putExtra("tmdb_vod_remark", vod.getRemarks());
    }

    private static void putDetailVodCache(Intent intent, Vod vod) {
        String key = VodDetailCache.put(vod);
        if (!TextUtils.isEmpty(key)) intent.putExtra(EXTRA_TMDB_VOD_CACHE_KEY, key);
    }

    private static boolean dispatchToContentHandler(Activity activity, String key, String id, String name, String pic, String mark) {
        return com.fongmi.android.tv.content.ContentDispatcher.dispatchSite(activity, key, id, name, pic, mark);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
    }

    private String getTmdbVodPic() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_pic"), "");
    }

    private String getTmdbVodContent() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_content"), "");
    }

    private String getTmdbVodYear() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_year"), "");
    }

    private String getTmdbVodArea() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_area"), "");
    }

    private String getTmdbVodType() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_type"), "");
    }

    private String getTmdbVodDirector() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_director"), "");
    }

    private String getTmdbVodActor() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_actor"), "");
    }

    private String getTmdbVodRemark() {
        return Objects.toString(getIntent().getStringExtra("tmdb_vod_remark"), "");
    }

    private void applyIntentTmdbVodRemark(Vod item) {
        if (item == null || !isIntentTmdbPlayback()) return;
        String remark = getTmdbVodRemark();
        getIntent().removeExtra("tmdb_vod_remark");
        if (!TextUtils.isEmpty(remark)) item.setRemarks(remark);
    }

    private String getWallPic() {
        return Objects.toString(getIntent().getStringExtra("wallPic"), "");
    }

    private String getContent() {
        return Objects.toString(getIntent().getStringExtra("content"), "");
    }

    private String getMark() {
        return Objects.toString(getIntent().getStringExtra("mark"), "");
    }

    private String getSearchKeyword() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_SEARCH_KEYWORD), "");
    }

    private String getIntentPlaybackFlag() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_PLAY_FLAG), "");
    }

    private String getIntentPlaybackFlagKey() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_PLAY_FLAG_KEY), "");
    }

    private String getIntentPlaybackEpisodeName() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_PLAY_EPISODE_NAME), "");
    }

    private String getIntentPlaybackEpisodeUrl() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_PLAY_EPISODE_URL), "");
    }

    private Episode withIntentTmdbEpisodeIdentity(Episode episode) {
        if (episode == null) return null;
        int number = getIntent().getIntExtra(EXTRA_TMDB_PLAY_EPISODE_NUMBER, 0);
        if (number <= 0) return episode;
        int season = getIntent().getIntExtra(EXTRA_TMDB_PLAY_SEASON_NUMBER, -1);
        TmdbEpisode current = episode.getTmdbEpisode();
        if (current != null && current.getNumber() == number && current.getSeasonNumber() == season) return episode;
        // 使用副本参与历史匹配，避免仅为续播身份而改变剧集列表的卡片/标题展示。
        Episode identity = Episode.create(episode.getName(), episode.getDesc(), episode.getUrl());
        identity.setTmdbEpisode(new TmdbEpisode(number, getEpisodeTitles().get(number), "", "", "", 0, 0, 0, season));
        return identity;
    }
    private int currentSourceSeasonNumber() {
        return currentSourceSeasonNumber(mVod);
    }

    private int currentSourceSeasonNumber(Vod item) {
        int season = getIntent().getIntExtra(EXTRA_TMDB_PLAY_SEASON_NUMBER, -1);
        if (season >= 0) return season;
        Flag sourceFlag = getFlag();
        season = EpisodeSeasonPolicy.resolveExplicitSourceSeason(sourceFlag == null ? "" : sourceFlag.getShow());
        if (season >= 0) return season;
        season = resolveSourceEpisodeSeason(sourceFlag);
        if (season >= 0) return season;
        season = SiteApi.PUSH.equals(getKey())
                ? EpisodeSeasonPolicy.resolveExplicitSourceSeason(getName(), mSourceVodName,
                item == null ? "" : item.getName(), item == null ? "" : item.getRemarks())
                : EpisodeSeasonPolicy.resolveSourceSeason(getName(), mSourceVodName,
                item == null ? "" : item.getName(), item == null ? "" : item.getRemarks());
        if (season >= 0) return season;
        season = resolveSourceEpisodeSeason(item);
        if (season >= 0) return season;
        return mTmdbUIAdapter == null ? -1 : mTmdbUIAdapter.getSourceSeasonNumber();
    }

    private int resolveSourceEpisodeSeason(Flag flag) {
        return mSourceEpisodeSeasonCache.resolve(flag);
    }

    private int resolveSourceEpisodeSeason(Vod item) {
        return mSourceEpisodeSeasonCache.resolve(item);
    }
    private Episode withSourceSeasonEpisodeIdentity(Episode episode) {
        if (episode == null) return null;
        int season = currentSourceSeasonNumber();
        if (season < 0) return episode;
        TmdbEpisode current = episode.getTmdbEpisode();
        int number = current != null && current.getNumber() > 0 ? current.getNumber() : episode.getNumber();
        if (number <= 0) return episode;
        if (current != null && current.getNumber() == number && current.getSeasonNumber() == season) return episode;
        String title = current == null ? getEpisodeTitles().get(number) : current.getTitle();
        Episode identity = Episode.create(episode.getName(), episode.getDesc(), episode.getUrl());
        identity.setTmdbEpisode(new TmdbEpisode(number, title, "", "", "", 0, 0, 0, season));
        return identity;
    }

    private void updateEpisodeSeasonContext() {
        int season = currentSourceSeasonNumber();
        mBinding.episodeTitle.setText(isTmdbSourceEnabled() && season >= 0
                ? getString(R.string.detail_episode_season_context, season)
                : getString(R.string.detail_episode));
        boolean selectable = isTmdbSourceEnabled()
                && mTmdbUIAdapter != null
                && mTmdbUIAdapter.getTmdbItem() != null
                && mTmdbUIAdapter.getTmdbItem().isTv()
                && mTmdbUIAdapter.hasSeasonOptions();
        mBinding.episodeTitle.setClickable(selectable);
        mBinding.episodeTitle.setFocusable(selectable);
        mBinding.episodeTitle.setFocusableInTouchMode(false);
        mBinding.episodeTitle.setContentDescription(selectable
                ? getString(R.string.tmdb_season_match_current, mBinding.episodeTitle.getText())
                : mBinding.episodeTitle.getText());
        Drawable icon = selectable ? getDrawable(R.drawable.ic_expand_more) : null;
        if (icon != null) icon.setTint(mBinding.episodeTitle.getCurrentTextColor());
        mBinding.episodeTitle.setCompoundDrawablePadding(selectable ? ResUtil.dp2px(4) : 0);
        mBinding.episodeTitle.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, icon, null);
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

    private boolean hasIntentResumeHistory() {
        return !TextUtils.isEmpty(getIntent().getStringExtra(EXTRA_RESUME_HISTORY_KEY));
    }

    private String getTmdbVodCacheKey() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_TMDB_VOD_CACHE_KEY), "");
    }

    private String getKey() {
        return Objects.toString(getIntent().getStringExtra("key"), "");
    }

    private String getId() {
        return Objects.toString(getIntent().getStringExtra("id"), "");
    }

    private String getHistoryKey() {
        return getKey().concat(AppDatabase.SYMBOL).concat(getId()).concat(AppDatabase.SYMBOL) + VodConfig.getCid();
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        return mFlagAdapter == null || mFlagAdapter.isEmpty() ? null : mFlagAdapter.getActivated();
    }

    private void setHistoryFlag(Flag flag) {
        if (mHistory == null || flag == null) return;
        mHistory.setVodFlag(flag.getFlag());
        int index = mFlagAdapter == null ? -1 : mFlagAdapter.indexOf(flag);
        if (index < 0 && mVod != null) index = com.fongmi.android.tv.ui.helper.TmdbUIAdapter.flagIndex(mVod.getFlags(), flag);
        String flagKey = index >= 0
                ? com.fongmi.android.tv.ui.helper.TmdbUIAdapter.flagKey(flag, index)
                : mTmdbUIAdapter == null ? "" : mTmdbUIAdapter.activeFlagKey(flag);
        mHistory.setSourceBindingKey(flagKey);
    }

    private Flag resolveHistoryPlaybackFlag(List<Flag> flags) {
        if (flags == null || flags.isEmpty()) return null;
        String flagKey = mHistory == null ? "" : mHistory.getSourceBindingKey();
        String flagName = mHistory == null ? "" : mHistory.getVodFlag();
        String episodeUrl = mHistory == null ? "" : mHistory.getEpisodeUrl();
        Flag selected = com.fongmi.android.tv.ui.helper.TmdbUIAdapter.selectPlaybackFlag(
                flags, flagKey, episodeUrl, flagName);
        return selected == null ? flags.get(0) : selected;
    }

    private Episode getEpisode() {
        Flag flag = getFlag();
        if (flag != null) {
            List<Episode> items = flag.getEpisodes();
            for (Episode item : items) if (item.isSelected()) return item;
            if (!items.isEmpty()) return items.get(0);
        }
        return mEpisodeAdapter == null || mEpisodeAdapter.isEmpty() ? null : mEpisodeAdapter.getActivated();
    }

    private String getDanmakuEpisodeName() {
        Episode episode = getEpisode();
        return episode == null ? "" : episode.getName();
    }

    private boolean isTmdbMode() {
        return getIntent().getBooleanExtra("tmdbMode", false);
    }

    private boolean isTmdbSourceEnabled() {
        if (isTmdbMode()) return true;
        if (!Setting.isTmdbMode(Setting.getDetailOpenMode())) return false;
        if (!Setting.isTmdbEnabled()) return false;
        return TmdbSitePolicy.isEnabled(getKey(), getId());
    }

    private boolean shouldUseTmdbEpisodeCards(List<Episode> items) {
        return EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(isTmdbSourceEnabled(), items);
    }

    private boolean hasTmdbDetailAdapter() {
        return isTmdbSourceEnabled() && mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
    }

    private boolean shouldLoadTmdbDetail() {
        return mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
    }

    private boolean shouldUseTmdbDetailLayout() {
        return hasTmdbDetailAdapter() && !mTmdbFallbackToNative;
    }

    private boolean shouldUseTmdbBackdropSurface() {
        return !Setting.isFusionDetailPage() && (Setting.isOriginalEnhancedDetailPage() || shouldUseTmdbDetailLayout() && (Setting.getDetailOpenMode() == Setting.DETAIL_OPEN_ENHANCED || Setting.isTmdbNativeStyle()));
    }

    private com.fongmi.android.tv.bean.TmdbItem getTmdbItem() {
        return (com.fongmi.android.tv.bean.TmdbItem) getIntent().getSerializableExtra("tmdbItem");
    }

    /**
     * 跨源聚合必须携带完整 TMDB 身份（mediaType + tmdbId），避免电影与剧集数字 ID 相同时串进度。
     */
    private TmdbItem getHistoryTmdbItem() {
        if (!Setting.isHistoryAggregationEffective()) return null;
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        if (item == null) item = getTmdbItem();
        return item;
    }

    private boolean reloadHistoryAfterTmdbMatch() {
        return reloadHistoryAfterTmdbMatch(getHistoryTmdbItem());
    }

    private boolean reloadHistoryAfterTmdbMatch(TmdbItem matched) {
        Vod item = mVod;
        if (item == null || matched == null || hasIntentResumeHistory()) return false;
        if (mHistory != null && mHistory.isCrossSourcePlayback()
                && mHistory.getTmdbId() == matched.getTmdbId()
                && TextUtils.equals(mHistory.getMediaType(), matched.getMediaType())) return false;
        History resolved = History.findPlayback(getHistoryKey(), List.of(item.getName(), getName()), item.getFlags(), matched, currentSourceSeasonNumber(item));
        if (resolved == null) return false;
        mHistory = resolved;
        return resolved.isCrossSourcePlayback();
    }

    private void resumeHistoryAfterTmdbMatch() {
        if (mHistory == null || mFlagAdapter == null || mFlagAdapter.isEmpty()) return;
        Flag targetFlag = resolveHistoryPlaybackFlag(mFlagAdapter.getItems());
        Episode targetEpisode = targetFlag.find(mHistory.getEpisode(), true);
        if (targetEpisode == null) return;

        long position = Math.max(mHistory.getOpening(), mHistory.getPosition());
        if (position > 0) {
            pendingResumeSeekMs = position;
            tmdbHistoryResumePending = true;
        }
        if (!targetFlag.isSelected() || !targetEpisode.isSelected()) {
            onItemClick(targetFlag);
            return;
        }

        alignHistoryWithSelectedEpisode(targetFlag, targetEpisode);
        if (position > 0) {
            setPosition();
            applyPendingResumeSeek();
        }
    }

    private void alignHistoryWithSelectedEpisode(Flag flag, Episode episode) {
        Episode identity = withSourceSeasonEpisodeIdentity(episode);
        setHistoryFlag(flag);
        mHistory.setVodRemarks(getHistoryEpisodeName(episode));
        mHistory.setEpisodeUrl(episode.getUrl());
        if (identity.getTmdbEpisode() != null) mHistory.setTmdbEpisodePosition(identity);
    }

    /**
     * TMDB 匹配完成后把 tmdbId/mediaType 盖章到 History，供列表去重与跨源续播使用。
     *
     * @return 是否有字段被更新（需调用方持久化）
     */
    private boolean stampHistoryTmdbId() {
        if (mHistory == null || !Setting.isHistoryAggregationEffective()) return false;
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        if (item == null) item = getTmdbItem();
        if (item == null || item.getTmdbId() <= 0) return false;
        if (mHistory.getTmdbId() == item.getTmdbId() && TextUtils.equals(mHistory.getMediaType(), item.getMediaType())) return false;
        mHistory.setTmdbId(item.getTmdbId());
        mHistory.setMediaType(item.getMediaType());
        return true;
    }

    private String getOsdTitle() {
        String title = EpisodeTitleFormatter.buildPlaybackTitle(getPlaybackName(), getCurrentEpisodeTitle());
        String episodeInfo = tmdbEpisodeCompactText();
        return TextUtils.isEmpty(episodeInfo) ? title : title + " · " + episodeInfo;
    }

    private String getPlaybackName() {
        CharSequence name = mBinding == null || mBinding.name == null ? "" : mBinding.name.getText();
        return TextUtils.isEmpty(name) ? getName() : name.toString();
    }

    private String getCurrentEpisodeTitle() {
        return mEpisodeAdapter == null || mEpisodeAdapter.isEmpty() ? "" : getEpisodeTitle(getEpisode());
    }

    private String getEpisodeTitle(Episode episode) {
        return episode == null ? "" : EpisodeAdapter.getTitle(episode);
    }

    private CharSequence getPlaybackControlTitle() {
        return getPlaybackControlTitle(mEpisodeAdapter == null || mEpisodeAdapter.isEmpty() ? null : getEpisode());
    }

    private CharSequence getPlaybackControlTitle(Episode episode) {
        String name = getPlaybackName();
        String title = getEpisodeTitle(episode);
        return TextUtils.isEmpty(title) || TextUtils.equals(name, title) ? name : getString(R.string.detail_title, name, title);
    }

    private int getScale() {
        return mHistory != null && mHistory.getScale() != -1 ? mHistory.getScale() : PlayerSetting.getScale();
    }

    private boolean isReplay() {
        return Setting.getReset() == 1;
    }

    private boolean isFromCollect() {
        return getIntent().getBooleanExtra("collect", false);
    }

    private boolean isAutoRotate() {
        return Settings.System.getInt(getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0) == 1;
    }

    private boolean isLand() {
        return mBinding.getRoot().getTag().equals("land");
    }

    private boolean isPort() {
        return mBinding.getRoot().getTag().equals("port");
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return mNavigationCallback;
    }

    @Override
    protected PlayerView getExoView() {
        return mBinding.exo;
    }

    @Override
    protected CustomSeekView getSeekView() {
        return mBinding.control.seek;
    }

    @Override
    protected void onServiceConnected() {
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        applyPendingPlayerKernel();
        syncDesktopLyricsAudioContent();
        setPlayerKernel();
        setDecode();
        setLut();
        applyDeferredFullscreenOrientation();
        checkLand();
        if (consumePendingPlaybackResult()) return;
        if (consumeImmersiveAudioLaunch()) return;
        checkId();
    }

    @Override
    protected void onPlayerRebuilt() {
        setPlayerKernel();
        setDecode();
        setLut();
        refreshControlDialog();
    }

    private void refreshControlDialog() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ControlDialog dialog) dialog.setPlayer();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldKey = getKey();
        String oldId = getId();
        super.onNewIntent(intent);
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId) && key.equals(oldKey)) return;
        mBinding.swipeLayout.setRefreshing(true);
        saveHistory();
        getIntent().removeExtra(EXTRA_TMDB_PLAY_FLAG);
        getIntent().removeExtra(EXTRA_TMDB_PLAY_FLAG_KEY);
        getIntent().removeExtra(EXTRA_TMDB_PLAY_EPISODE_NAME);
        getIntent().removeExtra(EXTRA_TMDB_PLAY_EPISODE_URL);
        getIntent().removeExtra(EXTRA_TMDB_PLAY_SEASON_NUMBER);
        getIntent().removeExtra(EXTRA_TMDB_PLAY_EPISODE_NUMBER);
        getIntent().removeExtra(EXTRA_RESUME_FROM_HISTORY);
        getIntent().removeExtra(EXTRA_RESUME_HISTORY_CID);
        getIntent().removeExtra(EXTRA_RESUME_HISTORY_KEY);
        getIntent().putExtras(intent);
        resetPlaybackOwnership();
        if (mViewModel != null) mViewModel.cancelPlayerContent();
        invalidatePlayerContent();
        mPendingDetailVod = null;
        mPendingPlayerResult = null;
        mAppliedPlayerResult = null;
        if (service() != null) {
            subtitlePlaybackSession.stop(this);
            player().reset();
            player().stop();
            player().clear();
        }
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.beginDetailRequest();
        mSourceEpisodeSeasonCache.clear();
        mSourceVodName = "";
        // 换到新条目才重置会话形态；换源(getDetail)不走这里，见 isShortDramaSession()。
        // 此处 intent 已更新（上面 putExtras），故 isShortDramaSource() 读到的是新条目的站点：
        // 新条目不再是短剧时必须交还 enterShortDramaFullscreen 锁定的竖屏方向与全屏布局参数，
        // 否则长视频会卡在竖屏全屏且无法旋转。仍是短剧则保持形态，避免无谓的进出全屏抖动。
        if (shortDramaSession && !isShortDramaSource()) exitFullscreen();
        shortDramaSession = false;
        // 形态可能仍是短剧全屏（新条目也是短剧时上面不退出），所以按当前形态重算而不是一律清掉，
        // 否则新条目在到达 STATE_READY 之前会用长视频那套手势，侧边竖滑又变成调亮度。
        syncShortDramaGesture();
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mTmdbDetailTimeout = this::showTmdbDetailFallback;
        super.initView(savedInstanceState);
        mRestoringConfigurationPlayback = savedInstanceState != null;
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> setStatusBar(insets));
        mKeyDown = CustomKeyDown.create(this, mBinding.exo);
        mFrameParams = mBinding.video.getLayoutParams();
        mFrameHeight = mFrameParams.height;
        if (mFrameParams instanceof RelativeLayout.LayoutParams params) mDefaultFrameParams = new RelativeLayout.LayoutParams(params);
        mBinding.swipeLayout.setEnabled(false);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mBroken = new ArrayList<>();
        mPlayerUi = new VodPlayerUiController(new VodPlayerUiHost() {
            @Override
            public PlayerManager player() {
                return service() == null ? null : VideoActivity.this.player();
            }

            @Override
            public String osdTitle() {
                return getOsdTitle();
            }

        }, VodPlayerChrome.fromVideo(mBinding, null, 12f), this);
        mClock = mPlayerUi.clock();
        mOsd = mPlayerUi.osd();
        mPiP = mPlayerUi.pip();
        setupAudioStageOverlay();
        android.util.Log.d("VideoActivity", "Clock started in initView");
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mSeekProgressFallback = this::hideSeekProgressIfReady;
        checkDanmakuImg();
        setRecyclerView();
        setVideoView();
        setViewModel();
        initTmdbMode();
        setShortDisplay();
        if (hasInitialPreview()) showInitialPreview();
        else mBinding.progressLayout.showProgress();
        setAnimator();
        initNightModeOverlay();
        if (isShortDramaSession()) enterShortDramaFullscreen();
        if (hasPendingImmersiveAudioLaunch()) setAudioStageVisible(true);
        setupIntroSkipConfirmListener();
    }

    private void setupIntroSkipConfirmListener() {
        mIntroSkipPlayback.setSkipConfirmListener((segment, action) -> {
            if (mIntroSkipConfirmDialog != null && mIntroSkipConfirmDialog.isShowing()) return false;
            mIntroSkipConfirmDialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.intro_skip_confirm_title)
                .setMessage(IntroSkipKinds.confirmMessage(segment))
                .setPositiveButton(android.R.string.ok, (dialog, which) -> action.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            mIntroSkipConfirmDialog.setOnDismissListener(dialog -> {
                mIntroSkipPlayback.cancelConfirmation(segment);
                if (mIntroSkipConfirmDialog == dialog) mIntroSkipConfirmDialog = null;
            });
            return true;
        });
        mIntroSkipPlayback.setSkipNoticeListener(IntroSkipKinds::notifySkipped);
        mIntroSkipPlayback.setSkipConfirmDismisser(this::dismissIntroSkipConfirm);
    }

    private void dismissIntroSkipConfirm() {
        if (mIntroSkipConfirmDialog == null) return;
        try {
            mIntroSkipConfirmDialog.dismiss();
        } catch (Throwable ignored) {
        }
        mIntroSkipConfirmDialog = null;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay());
        mBinding.search.setOnClickListener(view -> onSearch());
        mBinding.castAction.setOnClickListener(guarded(this::onCast));
        mBinding.settingAction.setOnClickListener(view -> onSetting());
        mBinding.download.setOnClickListener(view -> onDownload());
        mBinding.actor.setOnClickListener(view -> onActor());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.episodeTitle.setOnClickListener(view -> showManualTmdbSeasonDialog());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        if (mBinding.episodeFileName != null) mBinding.episodeFileName.setOnClickListener(view -> toggleEpisodeFileName());
        if (mBinding.episodeViewMode != null) mBinding.episodeViewMode.setOnClickListener(view -> toggleEpisodeViewMode());
        mBinding.director.setOnClickListener(view -> onDirector());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(guarded(this::onCast));
        mBinding.control.info.setOnClickListener(guarded(this::onInfo));
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.nightMode.setOnClickListener(view -> toggleNightMode());
        mBinding.control.osdDiagnostics.setOnClickListener(view -> onOsdDiagnostics());
        mBinding.control.play.setOnClickListener(guarded(this::checkPlay));
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.right.pip.setOnClickListener(guarded(this::onPiP));
        mBinding.control.fullscreen.setOnClickListener(guarded(this::onFullscreen));
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(guardedView(this::onTrack));
        mBinding.control.action.audio.setOnClickListener(guardedView(this::onTrack));
        mBinding.control.action.video.setOnClickListener(guardedView(this::onTrack));
        mBinding.control.action.scale.setOnClickListener(guarded(this::onScale));
        mBinding.control.action.actionQuality.setOnClickListener(guarded(this::onQuality));
        mBinding.control.action.lut.setOnClickListener(guarded(this::onLut));
        mBinding.control.action.speed.setOnClickListener(guarded(this::onSpeed));
        mBinding.control.action.reset.setOnClickListener(guarded(this::onReset));
        mBinding.control.action.title.setOnClickListener(guarded(this::onTitle));
        mBinding.control.action.player.setOnClickListener(guarded(this::onPlayerKernel));
        mBinding.control.action.player.setOnLongClickListener(view -> onPlayerKernelLong());
        mBinding.control.action.change2.setOnClickListener(view -> onChange());
        mBinding.control.shortDramaChangeSource.setOnClickListener(view -> onChange());
        mBinding.control.shortDramaQuality.setOnClickListener(guarded(this::onQuality));
        mBinding.control.shortDramaEpisodes.setOnClickListener(guarded(this::onEpisodes));
        mBinding.control.action.fullscreen.setOnClickListener(guarded(this::onFullscreen));
        mBinding.control.action.playParams.setOnClickListener(guarded(this::onPlayParams));
        mBinding.control.action.multiThreadProxy.setOnClickListener(guarded(this::onMultiThreadProxy));
        mBinding.control.action.codecCapability.setOnClickListener(guarded(this::onCodecCapabilityPanel));
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.decode.setOnClickListener(guarded(this::onDecode));
        mBinding.control.action.playParams.setOnClickListener(guarded(this::onPlayParams));
        mBinding.control.action.ending.setOnClickListener(guarded(this::onEnding));
        mBinding.control.action.repeat.setOnClickListener(guarded(this::onRepeat));
        mBinding.control.action.opening.setOnClickListener(guarded(this::onOpening));

        if (mBinding.audioPlay != null) mBinding.audioPlay.setOnClickListener(view -> checkPlay());
        if (mBinding.audioNext != null) mBinding.audioNext.setOnClickListener(view -> checkNext());
        if (mBinding.audioPrev != null) mBinding.audioPrev.setOnClickListener(view -> checkPrev());
        if (mBinding.audioRepeatAction != null) mBinding.audioRepeatAction.setOnClickListener(view -> onRepeat());
        if (mBinding.audioQueueAction != null) mBinding.audioQueueAction.setOnClickListener(view -> onAudioQueue());
        if (mBinding.audioLyricsAction != null) mBinding.audioLyricsAction.setOnClickListener(view -> onLyricsSearch());
        if (mBinding.audioKeepAction != null) mBinding.audioKeepAction.setOnClickListener(view -> onKeep());
        if (mBinding.audioCastAction != null) mBinding.audioCastAction.setOnClickListener(view -> onCast());
        if (mBinding.audioSettingAction != null) mBinding.audioSettingAction.setOnClickListener(view -> onSetting());
        if (mBinding.audioKaraokeAction != null) mBinding.audioKaraokeAction.setOnClickListener(view -> onKaraokeMode());
        if (mBinding.audioBackgroundAction != null) mBinding.audioBackgroundAction.setOnClickListener(view -> randomizeAudioBackgroundMix(false));
        if (mBinding.audioMoreAction != null) mBinding.audioMoreAction.setOnClickListener(view -> onAudioMore());
        if (mBinding.audioTrackAction != null) mBinding.audioTrackAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_AUDIO));
        if (mBinding.audioSubtitleAction != null) mBinding.audioSubtitleAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_TEXT));
        if (mBinding.audioStage != null) mBinding.audioStage.setOnClickListener(view -> { });

        mBinding.control.action.danmaku.setOnClickListener(guarded(this::onDanmaku));
        mBinding.control.action.adFeedback.setOnClickListener(view -> onAdFeedback());
        mBinding.control.action.adFeedback.setOnLongClickListener(view -> onAdFeedbackLongPress());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        // 控制层显示时会先于 video 容器接收事件，空白区域必须直接转发给手势检测器。
        mBinding.control.getRoot().setOnTouchListener(this::onPlayerControlTouch);
        mBinding.control.action.getRoot().setOnTouchListener(this::onActionTouch);
        mBinding.swipeLayout.setOnRefreshListener(this::onSwipeRefresh);
    }

    private WindowInsetsCompat setStatusBar(WindowInsetsCompat insets) {
        int top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        Insets nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
        int bottom = nav.bottom;
        mStatusBarInset = top;
        mNavigationRightInset = nav.right;
        applyStatusBarSpacer();
        setEpisodeBottomInset(bottom);
        return insets;
    }

    private void setEpisodeBottomInset(int bottom) {
        mEpisodeBottomInset = bottom;
        int padding = ResUtil.dp2px(12);
        mBinding.episode.setPaddingRelative(mBinding.episode.getPaddingStart(), mBinding.episode.getPaddingTop(), mBinding.episode.getPaddingEnd(), padding);
        applyAudioStageInsets();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void updateEpisodeViewportHeight() {
        updateEpisodeTouchHandling();
        if (mBinding.episode.getVisibility() != View.VISIBLE) return;
        int limit = ResUtil.isPad() || ResUtil.isLand(this) ? ResUtil.dp2px(328) : ResUtil.dp2px(280);
        // The episode list lives inside a scroll container, so capping it by the
        // current on-screen remainder can collapse the viewport to a single row
        // when the section is laid out below the fold. Keep a stable cap here
        // and let the parent page handle the rest of the scrolling.
        int height = usesOuterEpisodePageScroll() ? 0 : limit;
        if (!usesOuterEpisodePageScroll() && isTmdbEpisodeCardMode()) height = Math.max(height, getEpisodeCardMinHeight());
        if (height == mEpisodeMaxHeight) return;
        mEpisodeMaxHeight = height;
        mBinding.episode.setMaxHeight(height);
        mBinding.episode.requestLayout();
    }

    private void updateEpisodeTouchHandling() {
        mBinding.episode.setOnTouchListener((view, event) -> {
            if (!usesOuterEpisodePageScroll()) return false;
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(false);
            }
            return false;
        });
    }

    private boolean usesOuterEpisodePageScroll() {
        return Setting.isOriginalEnhancedDetailPage()
                || mTmdbControlsMoved && shouldUseTmdbBackdropSurface();
    }

    private boolean isTmdbEpisodeCardMode() {
        return mEpisodeAdapter != null && !mEpisodeAdapter.isEmpty() && mEpisodeAdapter.isUsingTmdbCard();
    }

    private int getEpisodeCardMinHeight() {
        return ResUtil.dp2px(EPISODE_CARD_HEIGHT_DP + EPISODE_CARD_VERTICAL_MARGIN_DP);
    }

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        if (mBinding.tmdbPersonalTmdbRecommendations != null) {
            mBinding.tmdbPersonalTmdbRecommendations.setHasFixedSize(true);
            mBinding.tmdbPersonalTmdbRecommendations.setItemAnimator(null);
            mBinding.tmdbPersonalTmdbRecommendations.addItemDecoration(new SpaceItemDecoration(8));
            mBinding.tmdbPersonalTmdbRecommendations.setAdapter(mPersonalTmdbRecommendationAdapter = new TmdbRecommendationAdapter());
            mPersonalTmdbRecommendationAdapter.setOnItemClickListener(this::onPersonalRecommendationClick);
            mPersonalTmdbRecommendationAdapter.setOnItemLongClickListener(item -> onPersonalRecommendationLongClick(item, "tmdb"));
            mBinding.tmdbPersonalTmdbRecommendations.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dx > 0 && isNearRecommendationRowEnd(recyclerView)) loadMoreNativePersonalRecommendations(true);
                }
            });
        }
        if (mBinding.tmdbPersonalDoubanRecommendations != null) {
            mBinding.tmdbPersonalDoubanRecommendations.setHasFixedSize(true);
            mBinding.tmdbPersonalDoubanRecommendations.setItemAnimator(null);
            mBinding.tmdbPersonalDoubanRecommendations.addItemDecoration(new SpaceItemDecoration(8));
            mBinding.tmdbPersonalDoubanRecommendations.setAdapter(mPersonalDoubanRecommendationAdapter = new TmdbRecommendationAdapter());
            mPersonalDoubanRecommendationAdapter.setOnItemClickListener(this::onPersonalRecommendationClick);
            mPersonalDoubanRecommendationAdapter.setOnItemLongClickListener(item -> onPersonalRecommendationLongClick(item, "douban"));
            mBinding.tmdbPersonalDoubanRecommendations.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    if (dx > 0 && isNearRecommendationRowEnd(recyclerView)) loadMoreNativePersonalRecommendations(false);
                }
            });
        }
        if (mBinding.tmdbPersonalAiRecommendations != null) {
            mBinding.tmdbPersonalAiRecommendations.setHasFixedSize(true);
            mBinding.tmdbPersonalAiRecommendations.setItemAnimator(null);
            mBinding.tmdbPersonalAiRecommendations.addItemDecoration(new SpaceItemDecoration(8));
            mBinding.tmdbPersonalAiRecommendations.setAdapter(mPersonalAiRecommendationAdapter = new TmdbRecommendationAdapter());
            mPersonalAiRecommendationAdapter.setOnItemClickListener(this::onPersonalRecommendationClick);
            mPersonalAiRecommendationAdapter.setOnItemLongClickListener(item -> onPersonalRecommendationLongClick(item, "ai"));
        }
        mBinding.episodeGroup.setHasFixedSize(true);
        mBinding.episodeGroup.setItemAnimator(null);
        mBinding.episodeGroup.setAdapter(mEpisodeGroupAdapter = new EpisodeGroupAdapter(this));
        mEpisodeSpanCount = getEpisodeSpanCount();
        mBinding.episode.setNestedScrollingEnabled(false);
        mBinding.episode.setHasFixedSize(false);
        mBinding.episode.setItemAnimator(null);
        mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        mBinding.episode.addItemDecoration(mEpisodeDecoration = new SpaceItemDecoration(mEpisodeSpanCount, 8));
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, ViewType.GRID));
        mEpisodeAdapter.setOnTitleReadyListener(this::onEpisodeTitlesReady);
        installEpisodeLongPressFallback();
        mBinding.episode.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                syncEpisodeGroupByScroll();
            }
        });
        mBinding.quality.setHasFixedSize(true);
        mBinding.quality.setItemAnimator(null);
        mBinding.quality.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.control.parse.setHasFixedSize(true);
        mBinding.control.parse.setItemAnimator(null);
        mBinding.control.parse.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this, ViewType.DARK));
    }

    private void installEpisodeLongPressFallback() {
        Handler handler = new Handler(Looper.getMainLooper());
        int slop = ViewConfiguration.get(this).getScaledTouchSlop();
        mBinding.episode.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {
            private Runnable pending;
            private float downX;
            private float downY;

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                handle(event);
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent event) {
                handle(event);
            }

            private void handle(MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        View child = mBinding.episode.findChildViewUnder(downX, downY);
                        if (child == null || mEpisodeAdapter == null) return;
                        int position = mBinding.episode.getChildAdapterPosition(child);
                        if (position == RecyclerView.NO_POSITION || position >= mEpisodeAdapter.getItems().size()) return;
                        pending = () -> {
                            if (mEpisodeAdapter == null) return;
                            int current = mBinding.episode.getChildAdapterPosition(child);
                            if (current == RecyclerView.NO_POSITION || current >= mEpisodeAdapter.getItems().size()) return;
                            EpisodeAdapter.showTitlePopup(child, mEpisodeAdapter.getItems().get(current));
                        };
                        handler.postDelayed(pending, ViewConfiguration.getLongPressTimeout());
                        break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop) clear();
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        clear();
                        break;
                    default:
                        break;
                }
            }

            private void clear() {
                if (pending == null) return;
                handler.removeCallbacks(pending);
                pending = null;
            }
        });
    }

    private int getEpisodeGridSpanCount() {
        if (ResUtil.isPad()) return ResUtil.isLand(this) ? 4 : 3;
        return ResUtil.isLand(this) ? 3 : 2;
    }

    private int getEpisodeSpanCount() {
        return EpisodeGridLayoutPolicy.getMaxSpan(isLand(), ResUtil.isPad());
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.adFeedback.setVisibility(isAdFeedbackEnabled() ? View.VISIBLE : View.GONE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        setupActionButtons();
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            mPiP.update(this, view);
            Log.d(SIZE_TAG, "video layout new=" + (right - left) + "x" + (bottom - top)
                    + " old=" + (oldRight - oldLeft) + "x" + (oldBottom - oldTop)
                    + " fullscreen=" + isFullscreen()
                    + " land=" + isLand()
                    + " scale=" + getScale()
                    + " player=" + (service() == null ? "none" : player().getPlayerText()));
        });
        // 初始化时隐藏底部控制栏，避免竖屏小窗时显示
        mBinding.control.action.getRoot().setVisibility(View.GONE);
        setPlayer();
    }

    private void setPlayer() {
        mBinding.control.action.player.setText(service() == null ? ResUtil.getStringArray(R.array.select_player)[PlayerSetting.getActivePlayer()] : player().getPlayerText());
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.CHANGE, mBinding.control.action.change2);
        addActionButton(PlayerButtonSetting.FULLSCREEN, mBinding.control.action.fullscreen);
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.PLAY_PARAMS, mBinding.control.action.playParams);
        addActionButton(PlayerButtonSetting.MULTI_THREAD_PROXY, mBinding.control.action.multiThreadProxy);
        addActionButton(PlayerButtonSetting.CODEC_CAPABILITY, mBinding.control.action.codecCapability);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.QUALITY, mBinding.control.action.actionQuality);
        addActionButton(PlayerButtonSetting.LUT, mBinding.control.action.lut);
        addActionButton(PlayerButtonSetting.RESET, mBinding.control.action.reset);
        addActionButton(PlayerButtonSetting.REPEAT, mBinding.control.action.repeat);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.opening);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.ending);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmaku);
        addActionButton(PlayerButtonSetting.KARAOKE, mBinding.control.action.karaoke);
        addActionButton(PlayerButtonSetting.AD_FEEDBACK, mBinding.control.action.adFeedback);
        addActionButton(PlayerButtonSetting.TITLE, mBinding.control.action.title);
        addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);
        addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        applyActionButtonSettings();
        setPlayParamsState();
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    private void applyActionButtonVisibility() {
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
    }

    private void applyActionButtonSettings() {
        if (mActionButtons != null) PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setLayoutParams(mFrameParams);
            restoreContextWall();
        }
    }

    private void setAnimator() {
        mAnimator = new ValueAnimator();
        mAnimator.setInterpolator(new DecelerateInterpolator());
        mAnimator.addUpdateListener(animation -> {
            if (isLand() || isFullscreen() || isInPictureInPictureMode() || mFusionChromeApplied) return;
            mFrameParams.height = (int) animation.getAnimatedValue();
            mBinding.video.setLayoutParams(mFrameParams);
        });
    }

    private void initNightModeOverlay() {
        mNightModeOverlay = new View(this);
        mNightModeOverlay.setLayoutParams(new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));
        mNightModeOverlay.setBackgroundColor(0x00000000);
        mNightModeOverlay.setClickable(false);
        mNightModeOverlay.setFocusable(false);
        // 插在 exo 之上、widget 之下(index 1),只压暗视频画面,不盖住控制栏
        ((ViewGroup) mBinding.video).addView(mNightModeOverlay, 1);

        int defaultMode = PlayerSetting.getNightModeDefault();
        if (defaultMode == PlayerSetting.NIGHT_MODE_ALWAYS_ON) {
            mNightModeLevel = PlayerSetting.getNightModeLevel();
            if (mNightModeLevel == PlayerSetting.NIGHT_MODE_OFF) {
                mNightModeLevel = PlayerSetting.NIGHT_MODE_LOW;
            }
        } else {
            mNightModeLevel = PlayerSetting.NIGHT_MODE_OFF;
        }
        applyNightMode();
    }

    private void toggleNightMode() {
        mNightModeLevel++;
        if (mNightModeLevel > PlayerSetting.NIGHT_MODE_HIGH) {
            mNightModeLevel = PlayerSetting.NIGHT_MODE_OFF;
        }
        PlayerSetting.putNightModeLevel(mNightModeLevel);
        applyNightMode();
        showNightModeToast();
    }

    private void applyNightMode() {
        if (mNightModeOverlay == null) return;
        int alpha;
        float maxBrightness;
        int iconRes;
        switch (mNightModeLevel) {
            case PlayerSetting.NIGHT_MODE_LOW:
                alpha = (int) (255 * 0.15f);
                maxBrightness = 0.6f; // 亮度上限 60%
                iconRes = R.drawable.ic_control_night_mode_on;
                break;
            case PlayerSetting.NIGHT_MODE_MEDIUM:
                alpha = (int) (255 * 0.30f);
                maxBrightness = 0.4f; // 亮度上限 40%
                iconRes = R.drawable.ic_control_night_mode_on;
                break;
            case PlayerSetting.NIGHT_MODE_HIGH:
                alpha = (int) (255 * 0.45f);
                maxBrightness = 0.25f; // 亮度上限 25%
                iconRes = R.drawable.ic_control_night_mode_on;
                break;
            default:
                alpha = 0;
                maxBrightness = -1f; // -1 表示恢复系统亮度
                iconRes = R.drawable.ic_control_night_mode_off;
                break;
        }
        // 双重策略:View 覆盖层(压 TextureView) + 窗口亮度上限(压 SurfaceView)
        mNightModeOverlay.setBackgroundColor((alpha << 24) | 0x000000);
        // 上限交给手势控制器与用户亮度合并，避免直接覆写窗口亮度吞掉手势设定
        if (mKeyDown != null) mKeyDown.setBrightLimit(maxBrightness);
        // 更新按钮图标
        mBinding.control.nightMode.setImageResource(iconRes);
    }

    private void showNightModeToast() {
        int messageId;
        switch (mNightModeLevel) {
            case PlayerSetting.NIGHT_MODE_LOW:
                messageId = R.string.night_mode_low;
                break;
            case PlayerSetting.NIGHT_MODE_MEDIUM:
                messageId = R.string.night_mode_medium;
                break;
            case PlayerSetting.NIGHT_MODE_HIGH:
                messageId = R.string.night_mode_high;
                break;
            default:
                messageId = R.string.night_mode_off;
                break;
        }
        Notify.show(messageId);
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setDecodeSwitchPending(boolean pending) {
        mBinding.control.action.decode.setEnabled(!pending);
        mBinding.control.action.decode.setAlpha(pending ? 0.65f : 1.0f);
    }

    private void setNextDecodeText() {
        int next = player().isHardDecode() ? PlayerEngine.SOFT : PlayerEngine.HARD;
        mBinding.control.action.decode.setText(ResUtil.getStringArray(R.array.select_decode)[next]);
    }

    private void setPlayerKernel() {
        mBinding.control.action.player.setText(player().getPlayerText());
    }

    /**
     * 起播前把内核切到本剧记住的选择，并返回该内核给取址用——取播放地址要按内核区分线路。
     * 没有记录时 getPlayerOrDefault 会退回设置页的全局默认。
     * 播放服务还没连上时先只记会话内核（取址在工作线程上读它），引擎由 onServiceConnected 补齐。
     */
    private int applyHistoryPlayerKernel() {
        int kernel = mHistory == null ? PlayerSetting.getPlayer() : mHistory.getPlayerOrDefault();
        PlayerSetting.putActivePlayer(kernel);
        if (service() == null) {
            mPendingPlayerKernel = kernel;
            return kernel;
        }
        mPendingPlayerKernel = PlayerSetting.NONE;
        player().preparePlayer(kernel);
        setPlayerKernel();
        setDecode();
        return kernel;
    }

    /**
     * 服务连上后补齐本页在等的内核。
     * 服务可能是上一次播放留活下来的，它建 PlayerManager 时读到的还是上一部剧的内核，
     * 所以本页在服务就绪前定下的选择要在这里落到引擎上；没有待办时不动引擎。
     */
    private void applyPendingPlayerKernel() {
        int kernel = mPendingPlayerKernel;
        mPendingPlayerKernel = PlayerSetting.NONE;
        if (!PlayerSetting.isPlayer(kernel)) return;
        player().preparePlayer(kernel);
    }

    /**
     * 把用户刚选定的内核写回本剧历史并落盘。
     * 只在用户显式换内核时调用，且用用户选的值而不是会话/引擎状态：
     * 播放页可能重叠存在（上一部剧的 Activity 还没销毁），若挂在每次存历史上，
     * 上一部剧的收尾存档会用别人的会话内核覆盖本剧记住的选择；
     * 而引擎类型还会被播放失败后的自动回退改掉，也不代表用户改了选择。
     */
    private void rememberPlayerKernel(int type) {
        if (mHistory == null || !PlayerSetting.isPlayer(type)) return;
        mHistory.setPlayer(type);
        syncHistory();
    }

    private void setScale(int scale) {
        if (mHistory != null) mHistory.setScale(scale);
        if (SiteApi.PUSH.equals(getKey())) PlayerSetting.putScale(scale);
        applyResizeMode(scale);
        mBinding.exo.post(() -> applyResizeMode(scale));
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void setLut() {
        mBinding.control.action.lut.setText(player().getLutText());
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getPlayer().observeForever(mObservePlayer);
        mViewModel.getSearch().observeForever(mObserveSearch);
    }

    private void checkId() {
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else if (!setCachedTmdbDetail()) getDetail();
    }

    private boolean setCachedTmdbDetail() {
        Vod cached = VodDetailCache.take(getTmdbVodCacheKey());
        if (cached == null) return false;
        VodEventGuard.alignCachedIdentity(cached, getKey(), getId());
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = true;
        if (!shouldRevealShellWhileLoading()) mBinding.progressLayout.showProgress();
        SpiderDebug.log("video-flow", "detail cache hit queued key=%s id=%s name=%s", getKey(), getId(), cached.getName());
        mBinding.getRoot().postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            long start = System.currentTimeMillis();
            setDetail(Result.vod(cached));
            SpiderDebug.log("video-flow", "detail cache apply cost=%dms key=%s id=%s name=%s", System.currentTimeMillis() - start, getKey(), getId(), cached.getName());
        }, TMDB_CACHED_DETAIL_APPLY_DELAY_MS);
        return true;
    }

    private void checkLand() {
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
    }

    private void getDetail() {
        getDetail(false);
    }

    private void getDetail(boolean refresh) {
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = false;
        cancelTmdbDetailFallback();
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s refresh=%s", getKey(), getId(), getName(), refresh);
        // 骨架已经揭开时不能再整页转圈：那会把刚露出的视频窗口与选集重新压成 INVISIBLE，
        // 变回「先整页转一次、再在播放器窗口里转一次」的两层加载。
        if (!shouldRevealShellWhileLoading()) mBinding.progressLayout.showProgress();
        prefetchDirectTmdbDetail();
        mViewModel.detailContent(getKey(), getId(), refresh);
    }

    private void prefetchDirectTmdbDetail() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) return;
        mTmdbUIAdapter.beginDetailRequest();
        com.fongmi.android.tv.bean.TmdbItem item = getTmdbItem();
        if (item != null) mTmdbUIAdapter.prefetch(item);
    }

    private void getDetail(Vod item) {
        revealManualSearch = false;
        if (!isAutoMode()) mViewModel.stopSearch();
        saveHistory();
        if (mViewModel != null) mViewModel.cancelPlayerContent();
        invalidatePlayerContent();
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        subtitlePlaybackSession.stop(this);
        mLyricsSearchSeq++;
        cancelKaraokePitchGeneration(false);
        dismissLyricsResultDialog();
        clearLyrics();
        clearKaraokeState();
        if (service() != null) {
            player().reset();
            player().stop();
        }
        getDetail();
    }

    private void setDetail(Result result) {
        long cost = System.currentTimeMillis() - detailStartTime;
        SpiderDebug.log("video-flow", "detail finish cost=%dms empty=%s msg=%s", cost, result.getList().isEmpty(), result.getMsg());
        recordDetailHealth(result, cost);
        mBinding.swipeLayout.setRefreshing(false);
        // 猫源设置项：点击的本意是开网页，detail 只是副产物。留在这儿会让内嵌页背后压着空白播放页，
        // 也不能走 setEmpty——它在 intent 带 name 时会拿动作名去别的站搜索。
        if (com.fongmi.android.tv.api.CatAction.shouldYieldDetail(getKey(), detailStartTime, result)) {
            SpiderDebug.log("video-flow", "detail yield to cat webview key=%s id=%s", getKey(), getId());
            finish();
            return;
        }
        if (result.getList().isEmpty()) setEmpty(result.hasMsg());
        else setDetail(result.getVod());
        Notify.show(result.getMsg());
    }

    private void setEmpty(boolean finish) {
        if (isFromCollect() || finish) {
            finish();
        } else if (getName().isEmpty()) {
            showEmpty();
        } else {
            mBinding.name.setText(getName());
            App.post(mR4, 10000);
            checkSearch(false);
        }
    }

    private void showEmpty() {
        showError(getString(R.string.error_detail));
        mBinding.swipeLayout.setEnabled(true);
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        if (service() == null) {
            mPendingDetailVod = item;
            return;
        }
        mSourceEpisodeSeasonCache.clear();
        mSourceVodName = item.getName();
        mVod = item;
        item.checkPic(getPic());
        item.checkName(getName());
        item.checkContent(getTmdbVodContent());
        item.checkContent(getContent());
        applyIntentTmdbVodRemark(item);
        boolean tmdbMode = shouldLoadTmdbDetail();
        mTmdbFallbackToNative = false;
        mTmdbContentLoaded = false;
        mTmdbAutoDialogShown = false;
        setOriginalEnhancedActionVisibility(tmdbMode);
        if (tmdbMode) {
            hideTmdbHeader();
            setNativeDetailInfoVisible(false);
            applyTmdbTabletVideoLayoutIfNeeded();
            mBinding.quick.setVisibility(View.GONE);
            mBinding.search.setVisibility(View.GONE);
            if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.GONE);
            scheduleTmdbDetailFallback();
        } else {
            cancelTmdbDetailFallback();
            restoreDefaultVideoLayout();
            restoreFlagAndEpisodeFromTmdb();
            setNativeDetailInfoVisible(true);
            mBinding.search.setVisibility(View.VISIBLE);
            if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.VISIBLE);
            android.util.Log.d("VideoActivity", "setDetail - 调用 showContent()");
            mBinding.progressLayout.showContent();
        }

        // 源站详情一旦可用就先显示当前集数页；TMDB 头部在富集完成后增量出现。
        ViewGroup scrollContainer = (ViewGroup) mBinding.scroll.getChildAt(0);
        scrollContainer.setVisibility(View.VISIBLE);

        // TMDB 集数处理：排序和应用标题
        if (isIntentTmdbPlayback()) com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(item);
        applyTmdbEpisodeTitles(item);

        // TMDB 富集完成前保留源站标题，避免详情页只剩全屏转圈。
        mBinding.name.setText(item.getName());
        mBinding.name.setVisibility(View.VISIBLE);
        mFlagAdapter.addAll(item.getFlags());
        App.removeCallbacks(mR4);
        if (!checkHistory(item)) return;
        checkFlag(item);
        checkKeepImg();
        updateTmdbKeepState();
        setText(item);
        updateKeep();
        if (tmdbMode) {
            hideNativePersonalRecommendations();
            showDetailContent();
        } else {
            loadNativePersonalRecommendations(item);
        }

        // TMDB 增强：全局开关启用或 Intent 传入 TmdbItem 时触发
        if (shouldLoadTmdbDetail()) {
            mTmdbUIAdapter.setActiveFlag(getFlag());
            com.fongmi.android.tv.bean.TmdbItem tmdbItem = getTmdbItem();
            if (tmdbItem != null) {
                // 直接使用传入的 TmdbItem
                SpiderDebug.log("tmdb-mobile", "direct load vodTitle=%s tmdbTitle=%s tmdbId=%d media=%s", item.getName(), tmdbItem.getTitle(), tmdbItem.getTmdbId(), tmdbItem.getMediaType());
                mTmdbUIAdapter.load(tmdbItem, item);
            } else {
                mTmdbUIAdapter.autoMatch(item.getName(), item, getSearchKeyword());
            }
        }
    }

    private void setText(Vod item) {
        setDetailLyrics(item.getContent());
        // 富集仍在进行时不填充会被 TMDB 覆盖的站源文本：骨架可以先揭开，
        // 但文本要等 TMDB 落定再写，避免揭开后再跳一次。
        if (isTmdbDetailEnrichmentPending()) {
            applyFusionNativeTextColors();
            return;
        }
        setText(mBinding.site, R.string.detail_site, getSite().getDisplayName());

        // 非 TMDB 模式才填充原生字段
        // 基于 TMDB 开关和配置是否就绪
        boolean tmdbMode = shouldUseTmdbDetailLayout();
        if (!tmdbMode) {
            restoreDefaultVideoLayout();
            setNativeDetailInfoVisible(true);
            setText(mBinding.director, R.string.detail_director, item.getDirector());
            setText(mBinding.actor, R.string.detail_actor, item.getActor());
            setText(mBinding.remark, 0, item.getRemarks());
            setOther(mBinding.other, item);
            setText(mBinding.content, 0, item.getContent());
        } else {
            applyTmdbTabletVideoLayoutIfNeeded();
            bindTmdbTabletTopSummary(item);
        }
        applyFusionNativeTextColors();
        updateAudioStageText();
    }

    private boolean shouldUseTmdbTabletWideLayout() {
        return canUseTmdbTabletWideLayout() && !isFullscreen() && !isInPictureInPictureMode();
    }

    private boolean canUseTmdbTabletWideLayout() {
        return isLand() && ResUtil.isPad() && shouldUseTmdbDetailLayout() && !Setting.isFusionDetailPage() && mDefaultFrameParams != null;
    }

    private void applyTmdbTabletVideoLayoutIfNeeded() {
        if (!canUseTmdbTabletWideLayout()) {
            restoreDefaultVideoLayout();
            return;
        }
        if (isFullscreen() || isInPictureInPictureMode()) return;
        int side = ResUtil.dp2px(TMDB_TABLET_PLAYER_SIDE_MARGIN_DP);
        int gutter = ResUtil.dp2px(TMDB_TABLET_PLAYER_GUTTER_DP);
        int minPlayerWidth = ResUtil.dp2px(TMDB_TABLET_PLAYER_MIN_WIDTH_DP);
        int maxPlayerWidth = ResUtil.dp2px(TMDB_TABLET_PLAYER_MAX_WIDTH_DP);
        int minSummaryWidth = ResUtil.dp2px(TMDB_TABLET_SUMMARY_MIN_WIDTH_DP);
        int screenWidth = ResUtil.getScreenWidth(App.get());  // 使用 App.get() 获取实时屏幕宽度
        int available = screenWidth - side * 2 - gutter;
        if (available < minPlayerWidth + minSummaryWidth) {
            restoreDefaultVideoLayout();
            return;
        }
        int playerWidth = Math.min(maxPlayerWidth, Math.round(screenWidth * 0.48f));
        playerWidth = Math.max(minPlayerWidth, Math.min(playerWidth, available - minSummaryWidth));
        int playerHeight = playerWidth * 9 / 16;

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(playerWidth, playerHeight);
        params.addRule(RelativeLayout.BELOW, R.id.statusBar);
        params.setMargins(side, ResUtil.dp2px(TMDB_TABLET_PLAYER_TOP_MARGIN_DP), gutter, 0);
        mFrameParams = params;
        mFrameHeight = playerHeight;
        mTmdbTabletLayoutApplied = true;
        mBinding.video.setLayoutParams(params);
    }

    private void restoreDefaultVideoLayout() {
        if (!mTmdbTabletLayoutApplied || mFusionChromeApplied || mDefaultFrameParams == null) return;
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(mDefaultFrameParams);
        if (sameFrameParams(mFrameParams, params)) {
            mTmdbTabletLayoutApplied = false;
            return;
        }
        mFrameParams = params;
        mFrameHeight = params.height;
        mTmdbTabletLayoutApplied = false;
        if (isFullscreen() || isInPictureInPictureMode()) return;
        mBinding.video.setLayoutParams(params);
    }

    private boolean sameFrameParams(ViewGroup.LayoutParams current, RelativeLayout.LayoutParams target) {
        if (!(current instanceof RelativeLayout.LayoutParams params)) return false;
        return params.width == target.width
                && params.height == target.height
                && params.leftMargin == target.leftMargin
                && params.topMargin == target.topMargin
                && params.rightMargin == target.rightMargin
                && params.bottomMargin == target.bottomMargin;
    }

    private void bindTmdbTabletTopSummary(Vod item) {
        if (!shouldUseTmdbDetailLayout()) return;
        setNativeDetailInfoVisible(false);
        if (!shouldUseTmdbTabletWideLayout()) return;
        setPlainText(mBinding.name, item.getName());
        setPlainText(mBinding.remark, item.getRemarks());
        setPlainText(mBinding.site, getString(R.string.detail_site, getSite().getDisplayName()));
        setOther(mBinding.other, item);
        mBinding.director.setVisibility(View.GONE);
        mBinding.actor.setVisibility(View.GONE);
        mBinding.contentLayout.setVisibility(View.GONE);
    }

    private void setPlainText(TextView view, String text) {
        String value = Objects.toString(text, "");
        view.setText(value);
        view.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setNativeDetailInfoVisible(boolean visible) {
        int visibility = visible ? View.VISIBLE : View.GONE;
        mBinding.name.setVisibility(visibility);
        mBinding.remark.setVisibility(visibility);
        mBinding.site.setVisibility(visibility);
        mBinding.other.setVisibility(visibility);
        mBinding.director.setVisibility(visibility);
        mBinding.actor.setVisibility(visibility);
        mBinding.contentLayout.setVisibility(visibility);
    }

    private void setOriginalEnhancedActionVisibility(boolean hide) {
        mBinding.actionRow.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (view == mBinding.content) setContentVisible();
        view.setLinkTextColor(Setting.isFusionDetailPage() && isFusionLightTheme() ? 0xFF1D8F5A : Color.WHITE);
        CustomMovement.bind(view);
    }

    private void setContentVisible() {
        // TMDB 模式下不显示原生简介区域
        boolean tmdbMode = shouldUseTmdbDetailLayout();
        if (tmdbMode) {
            mBinding.contentLayout.setVisibility(View.GONE);
        } else {
            mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
        }
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                FolderActivity.start(getActivity(), getKey(), result);
                ((TextView) view).setMaxLines(Integer.MAX_VALUE);
                setRedirect(true);
            }
        };
    }

    private void setOther(TextView view, Vod item) {
        StringBuilder sb = new StringBuilder();
        if (!item.getYear().isEmpty()) sb.append(getString(R.string.detail_year, item.getYear())).append("  ");
        if (!item.getArea().isEmpty()) sb.append(getString(R.string.detail_area, item.getArea())).append("  ");
        if (!item.getTypeName().isEmpty()) sb.append(getString(R.string.detail_type, item.getTypeName())).append("  ");
        view.setVisibility(sb.length() == 0 ? View.GONE : View.VISIBLE);
        view.setText(Util.substring(sb.toString(), 2));
    }

    private void applyTmdbEpisodeTitles(Vod vod) {
        java.util.Map<Integer, String> titles = getEpisodeTitles();
        android.util.Log.d("VideoActivity", "applyTmdbEpisodeTitles - 集数标题数量: " + titles.size());
        if (vod == null || titles.isEmpty() || vod.getFlags() == null) return;
        for (Flag flag : vod.getFlags()) {
            for (Episode episode : flag.getEpisodes()) {
                String title = titles.get(episode.getNumber());
                if (android.text.TextUtils.isEmpty(title)) continue;
                String displayName = EpisodeTitleFormatter.withSourceFileSize(episode.getName(), EpisodeTitleFormatter.formatTmdbTitle(episode.getNumber(), title), Setting.isTmdbEpisodeFileSize());
                if (android.text.TextUtils.equals(episode.getDisplayName(), displayName)) continue;
                episode.setDisplayName(displayName);
                android.util.Log.d("VideoActivity", "应用标题: " + episode.getNumber() + " -> " + title);
            }
        }
    }

    private java.util.Map<Integer, String> getEpisodeTitles() {
        java.util.Map<Integer, String> titles = new java.util.HashMap<>();
        java.util.ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        android.util.Log.d("VideoActivity", "getEpisodeTitles - values: " + (values != null ? values.size() : "null"));
        if (values == null) return titles;
        for (String value : values) {
            String[] parts = value.split("\t", 2);
            if (parts.length != 2 || android.text.TextUtils.isEmpty(parts[1])) continue;
            try {
                titles.put(Integer.parseInt(parts[0]), parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return titles;
    }

    private boolean isIntentTmdbPlayback() {
        java.util.ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        boolean result = values != null && !values.isEmpty();
        android.util.Log.d("VideoActivity", "isIntentTmdbPlayback: " + result);
        return result;
    }

    private void getPlayer(Flag flag, Episode episode) {
        resetAdFeedback();
        mBinding.control.title.setText(getPlaybackControlTitle(episode));
        playerStartTime = System.currentTimeMillis();
        beginPlayHealth();
        String playFlag = getEpisodePlayFlag(flag, episode);
        String previousEpisodeKey = Objects.toString(mPlaybackEpisodeKey, "");
        mPlaybackEpisodeKey = audioQueueEpisodeKey(episode);
        mSkipKaraokeTrackAutoLoad = isMusicLike() && !TextUtils.isEmpty(previousEpisodeKey) && !TextUtils.equals(previousEpisodeKey, mPlaybackEpisodeKey);
        SpiderDebug.log("video-flow", "player start key=%s flag=%s episode=%s url=%s", getKey(), playFlag, episode.getName(), episode.getUrl());
        mInlineLyrics = getEpisodeInlineLyrics(episode);
        applyPlaybackArtwork(episode);
        clearLyrics();
        clearKaraokeState();
        if (shouldUseImmersiveAudio()) setAudioStageVisible(true);
        beginPlayerContentRequest(getKey(), playFlag, episode.getUrl());
        mViewModel.playerContent(getKey(), playFlag, episode.getUrl(), applyHistoryPlayerKernel());
        mBinding.control.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void beginPlayerContentRequest(String key, String flag, String episode) {
        if (mViewModel != null) mViewModel.cancelPlayerContent();
        mPendingPlayerResult = null;
        invalidatePlayerContent();
        playerContentKey = key;
        playerContentFlag = flag;
        playerContentEpisode = episode;
    }

    private void invalidatePlayerContent() {
        playerContentGeneration++;
        playerContentRequestId++;
        playerKernelSwitchRequestId++;
        decodeSwitchRequestId++;
        playerContentKey = "";
        playerContentFlag = "";
        playerContentEpisode = "";
        decodeSwitchRefreshing = false;
    }

    private boolean isCurrentPlayerContentContext(String key, String flag, String episode) {
        Flag currentFlag = getFlag();
        Episode currentEpisode = getEpisode();
        return TextUtils.equals(key, getKey())
                && currentFlag != null
                && TextUtils.equals(flag, currentFlag.getFlag())
                && currentEpisode != null
                && TextUtils.equals(episode, currentEpisode.getUrl());
    }

    private int beginPlayerContentSwitch(int requestId, String key, String flag, String episode) {
        playerContentGeneration++;
        playerContentRequestId = requestId;
        playerContentKey = key;
        playerContentFlag = flag;
        playerContentEpisode = episode;
        return playerContentGeneration;
    }

    private boolean isCurrentPlayerContentRequest(int requestId, int generation,
                                                   String key, String flag, String episode) {
        return !isFinishing() && !isDestroyed()
                && requestId == playerContentRequestId
                && generation == playerContentGeneration
                && isCurrentPlayerContentContext(key, flag, episode);
    }

    private boolean canApplyPlayerContentRequest(int requestId, int generation,
                                                  String key, String flag, String episode) {
        return isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)
                && service() != null
                && player() != null
                && !player().isReleased()
                && !player().isEmpty()
                && isOwner();
    }

    private void setPlayer(Result result) {
        if (result == null || isFinishing() || isDestroyed()) return;
        if (service() == null) {
            mPendingPlayerResult = result;
            return;
        }
        SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        if (result == mAppliedPlayerResult && !player().isEmpty()) return;
        mAppliedPlayerResult = result;
        mQualityAdapter.addAll(result);
        mQualityAdapter.setPosition(mQualityAdapter.getPosition());
        setUseParse(result.shouldUseParse());
        mBinding.swipeLayout.setRefreshing(false);
        setQualityVisible(result.getUrl().isMulti());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        else applyPlaybackArtwork(getPlaybackEpisode());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        if (result.hasDesc()) {
            setText(mBinding.content, 0, result.getDesc());
            setPlaybackLyrics(result.getDesc());
        }
        applyAudioQueueMetadata(getPlaybackEpisode());
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() && PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE) ? View.VISIBLE : View.GONE);
        if (redirectToAudioIfNeeded(result)) return;
        List<Danmaku> siteDanmakus = result.getDanmaku();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
        if (DanmakuApi.canAutoSearch(siteDanmakus)) DanmakuApi.search(MediaTitleRequest.builder()
                .siteKey(getKey())
                .vodId(getId())
                .rawTitle(mHistory.getVodName())
                .rawRemarks(mHistory.getVodRemarks())
                .episodeName(getEpisode().getName())
                .source(MediaTitleLearningExample.SOURCE_DANMAKU_AUTO)
                .allowAi(true)
                .build(), danmaku -> {
            if (player() == null) return;
            if (DanmakuSetting.isSpiderFirst() && !siteDanmakus.isEmpty()) player().addDanmaku(danmaku);
            else player().setDanmaku(danmaku);
            refreshDanmakuControls();
        });
    }

    private boolean redirectToAudioIfNeeded(Result result) {
        List<Episode> episodes = getCurrentEpisodeItems();
        boolean handled = com.fongmi.android.tv.content.ContentDispatcher.dispatchResult(this, getHistoryKey(), getKey(), getFlag().getFlag(), mHistory.getVodName(), mHistory.getVodPic(), episodes, getSelectedEpisodePosition(episodes), result, getSite().getTimeout());
        if (handled) {
            stopPlayback();
            // 阅读结果接管前台，但保留本页：一次返回回到来源播放页。
        }
        return handled;
    }

    private void recordDetailHealth(Result result, long cost) {
        if (detailHealthRecorded) return;
        detailHealthRecorded = true;
        boolean success = result != null && !result.getList().isEmpty();
        String error = result == null ? "" : result.hasMsg() ? result.getMsg() : success ? "" : "empty";
        SiteHealthStore.recordDetail(getKey(), success, cost, error);
    }

    private void beginPlayHealth() {
        playHealthKey = getKey();
        playHealthRecorded = false;
    }

    private void recordPlayHealth(boolean success, String error) {
        if (playHealthRecorded) return;
        playHealthRecorded = true;
        SiteHealthStore.recordPlay(TextUtils.isEmpty(playHealthKey) ? getKey() : playHealthKey, success, error);
    }

    @Override
    public void onItemClick(Flag item) {
        if (item == null || mFlagAdapter.isEmpty()) return;
        int position = mFlagAdapter.indexOf(item);
        Flag resolved = mFlagAdapter.get(position < 0 ? 0 : position);
        boolean initialBinding = mEpisodeAdapter == null || mEpisodeAdapter.isEmpty();
        if (resolved.isSelected() && !initialBinding) return;
        Flag previous = getFlag();
        SpiderDebug.log("playback-action", "flag switch ui=mobile site=%s from=%s to=%s fullscreen=%s", getKey(), previous == null ? "" : previous.getFlag(), resolved.getFlag(), isFullscreen());
        mFlagAdapter.setSelected(resolved);
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.setActiveFlag(resolved);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
        boolean episodeChanged = seamless(resolved);
        if (!episodeChanged) setEpisodeAdapter(resolved.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        if (initialBinding && !episodeChanged) onRefresh();
        loadTmdbRelatedVideosForCurrentEpisode();
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        syncCurrentAudioPlaylistMetadata();
        Flag flag = getFlag();
        if (mFlagAdapter != null) mFlagAdapter.toggle(item);
        if (flag != null) setEpisodeAdapter(flag.getEpisodes());
        applyAudioQueueMetadata(item);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        loadTmdbRelatedVideosForCurrentEpisode();
        onRefresh();
    }

    /**
     * 小说/漫画阅读器切换章节时，由播放器执行解析任务（复用完整 playerContent 链路，
     * 含 parse=1 二次解析）。解析结果经 NovelRouter.routeReaderEngine 回传给前台阅读页。
     */
    @Override
    public boolean labPlayEpisode(String chapterUrl) {
        if (chapterUrl == null || chapterUrl.isEmpty()) return false;
        // 宿主已在销毁中：解析结果不会再回到阅读器，直接报「没发出」让它立刻收尾。
        // 另外 SiteViewModel 被清理后 playerContent 会抛 RejectedExecutionException，
        // 那个异常会顺着阅读器的 runOnUiThread 冒出去导致崩溃。
        if (isFinishing() || isDestroyed()) return false;
        Flag flag = getFlag();
        if (flag == null || flag.getEpisodes() == null) return false;
        for (Episode ep : flag.getEpisodes()) {
            if (chapterUrl.equals(ep.getUrl())) {
                getPlayer(flag, ep);
                return true;
            }
        }
        // 章节不在当前线路：阅读器的章节表跨线路合并，这种情况静默返回，
        // 告知调用方没有发出请求，让它立刻收尾在途标记。
        return false;
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        mEpisodeGroupAdapter.setSelected(item);
        if (shouldUseEpisodeRangePaging(getCurrentEpisodeItems())) {
            setEpisodeItems(getCurrentEpisodeItems());
            mBinding.episode.post(() -> scrollEpisodeToPosition(0));
        } else {
            scrollEpisodeToPosition(item.start);
        }
        scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition());
    }

    @Override
    public void onItemClick(Result result) {
        // 画质列表直点也会换 URL，且不经 changeQuality
        resetAdFeedback();
        updateActionQuality(result);
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    @Override
    public void onItemClick(Parse item) {
        setParse(item);
        onRefresh();
    }

    private void setParse(Parse item) {
        VodConfig.get().setParse(item);
        notifyItemChanged(mBinding.control.parse, mParseAdapter);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        updateEpisodeSeasonContext();
        int size = items.size();
        boolean useTmdbCard = shouldUseTmdbEpisodeCards(items);
        mBinding.control.action.episodes.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();
        // 中间悬浮的上集/下集按钮只根据集数显示，不受底部动作栏设置影响。
        mBinding.control.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        updateEpisodeReverseButton();
        if (shouldUseUpstreamNativeEpisodeModule()) {
            setUpstreamNativeEpisodeItems(items);
            return;
        }
        mEpisodeAdapter.setUseTmdbCard(useTmdbCard);
        boolean showViewMode = size > 1;
        if (showViewMode) mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();
        if (!showViewMode) mEpisodeGridMode = true;
        updateEpisodeViewModeButton();
        updateEpisodeFileNameButton();
        android.util.Log.d("VideoActivity", "setEpisodeAdapter - showViewMode=" + showViewMode + ", useTmdbCard=" + useTmdbCard + ", size=" + size);
        if (mBinding.episodeViewMode != null) mBinding.episodeViewMode.setVisibility(showViewMode ? View.VISIBLE : View.GONE);
        if (mBinding.episodeFileName != null) {
            mBinding.episodeFileName.setVisibility(showViewMode ? View.VISIBLE : View.GONE);
            android.util.Log.d("VideoActivity", "episodeFileName visibility set to " + (showViewMode ? "VISIBLE" : "GONE"));
        }
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(View.GONE);
        int maxGroupSize = shouldUseTmdbDetailLayout() ? EpisodeRangePolicy.CARD_PAGE_MAX_SIZE : 0;
        List<EpisodeGroupAdapter.Group> groups = EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort(), maxGroupSize);
        mEpisodeGroupAdapter.addAll(groups);
        updateEpisodeGroupVisibility();
        setEpisodeItems(items, useTmdbCard);
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private boolean shouldUseUpstreamNativeEpisodeModule() {
        return Setting.isDirectDetailPage() && !isTmdbMode();
    }

    private void setUpstreamNativeEpisodeItems(List<Episode> items) {
        int size = items.size();
        mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();
        mEpisodeAdapter.setUseTmdbCard(false);
        updateEpisodeViewModeButton();
        updateEpisodeFileNameButton();
        if (mBinding.episodeViewMode != null) mBinding.episodeViewMode.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mBinding.episodeFileName != null) mBinding.episodeFileName.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(View.GONE);
        List<EpisodeGroupAdapter.Group> groups = EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort());
        mEpisodeGroupAdapter.addAll(groups);
        mBinding.episodeGroup.setVisibility(groups.size() > 1 ? View.VISIBLE : View.GONE);
        setEpisodeItems(items, false);
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void updateEpisodeGroupVisibility() {
        if (mEpisodeGroupAdapter == null) return;
        boolean visible = EpisodeDisplayPolicy.shouldShowEpisodeGroup(mEpisodeGroupAdapter.getItemCount(), shouldUseTmdbDetailLayout());
        mBinding.episodeGroup.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void syncEpisodeGroupByScroll() {
        if (shouldUseEpisodeRangePaging(getCurrentEpisodeItems())) return;
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager)) return;
        int position = getEpisodeGroupSyncPosition((GridLayoutManager) manager);
        if (position == RecyclerView.NO_POSITION) return;
        selectEpisodeGroupByPosition(position);
    }

    private int getEpisodeGroupSyncPosition(GridLayoutManager manager) {
        if (!mBinding.episode.canScrollVertically(1) && mBinding.episode.canScrollVertically(-1)) {
            return manager.findLastVisibleItemPosition();
        }
        return manager.findFirstVisibleItemPosition();
    }

    private void selectEpisodeGroupByPosition(int position) {
        if (mEpisodeGroupAdapter == null || mEpisodeGroupAdapter.isEmpty()) return;
        int current = mEpisodeGroupAdapter.getPosition();
        List<EpisodeGroupAdapter.Group> groups = mEpisodeGroupAdapter.getItems();
        for (int i = 0; i < groups.size(); i++) {
            EpisodeGroupAdapter.Group group = groups.get(i);
            if (position < group.start || position >= group.end) continue;
            if (i != current) {
                mEpisodeGroupAdapter.setSelected(group);
                mBinding.episodeGroup.scrollToPosition(i);
            }
            return;
        }
    }

    private void scrollEpisodeToPosition(int position) {
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (manager instanceof GridLayoutManager) {
            int rowStart = getEpisodeRowStart((GridLayoutManager) manager, position);
            int offset = rowStart >= ((GridLayoutManager) manager).getSpanCount() ? -ResUtil.dp2px(4) : 0;
            ((GridLayoutManager) manager).scrollToPositionWithOffset(rowStart, offset);
        }
        else mBinding.episode.scrollToPosition(position);
    }

    private void scrollEpisodeToSelected() {
        mBinding.episode.post(() -> scrollEpisodeToPosition(mEpisodeAdapter.getPosition()));
    }

    private int getEpisodeRowStart(GridLayoutManager manager, int position) {
        int span = Math.max(1, manager.getSpanCount());
        return Math.max(0, position - position % span);
    }

    private void setEpisodeItems(List<Episode> items) {
        setEpisodeItems(items, shouldUseTmdbEpisodeCards(items));
    }

    private void setEpisodeItems(List<Episode> items, boolean useTmdbCard) {
        List<Episode> displayItems = getEpisodeDisplayItems(items);
        if (items.size() < 2) mEpisodeGridMode = true;
        updateEpisodeFallbackStillUrl();
        mEpisodeAdapter.setUseTmdbCard(useTmdbCard);
        mEpisodeAdapter.setNativeGridExpanded(mEpisodeGridMode && !useTmdbCard && isOriginalEnhancedEpisodeFallback());
        mEpisodeAdapter.setViewType(!mEpisodeGridMode ? ViewType.HORI : ViewType.GRID);
        mEpisodeAdapter.addAll(displayItems);
        updateEpisodeLayout(displayItems, useTmdbCard);
        if (shouldUseEpisodeRangePaging(items)) scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition());
        else selectEpisodeGroupByPosition(mEpisodeAdapter.getPosition());
        updateEpisodeViewModeButton();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void updateEpisodeLayout(List<Episode> items) {
        updateEpisodeLayout(items, shouldUseTmdbEpisodeCards(items));
    }

    private void updateEpisodeLayout(List<Episode> items, boolean useTmdbCard) {
        if (!mEpisodeGridMode) {
            RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
            if (!(manager instanceof LinearLayoutManager) || manager instanceof GridLayoutManager || ((LinearLayoutManager) manager).getOrientation() != LinearLayoutManager.HORIZONTAL) {
                mBinding.episode.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            }
            updateEpisodeDecoration(new SpaceItemDecoration(8));
            return;
        }
        int span = getEpisodeSpan(items, useTmdbCard);
        mEpisodeSpanCount = span;
        RecyclerView.LayoutManager manager = mBinding.episode.getLayoutManager();
        if (!(manager instanceof GridLayoutManager) || ((GridLayoutManager) manager).getSpanCount() != mEpisodeSpanCount) {
            mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        }
        updateEpisodeDecoration(new SpaceItemDecoration(mEpisodeSpanCount, 8));
    }

    private void updateEpisodeDecoration(SpaceItemDecoration decoration) {
        if (mEpisodeDecoration != null) mBinding.episode.removeItemDecoration(mEpisodeDecoration);
        mBinding.episode.addItemDecoration(mEpisodeDecoration = decoration);
    }

    private int getEpisodeSpan(List<Episode> items) {
        return getEpisodeSpan(items, shouldUseTmdbEpisodeCards(items));
    }

    private int getEpisodeSpan(List<Episode> items, boolean useTmdbCard) {
        if (useTmdbCard) return getEpisodeGridSpanCount();
        if (items.size() == 1) return 1;
        int maxLen = 0;
        for (Episode item : items) maxLen = Math.max(maxLen, EpisodeAdapter.getNativeDisplayTitle(item).length());
        if (isOriginalEnhancedEpisodeFallback()) {
            return EpisodeGridLayoutPolicy.getOriginalEnhancedFallbackSpan(items.size(), maxLen, PlayerSetting.getEpisodeColumn());
        }
        if (maxLen >= 12) return PlayerSetting.getEpisodeColumn();
        int ideal = maxLen >= 10 ? 130 : maxLen >= 7 ? 104 : 80;
        int width = EpisodeGridLayoutPolicy.getAvailableWidth(
                mBinding.episode.getWidth(),
                ResUtil.getScreenWidth(this),
                ResUtil.getScreenHeight(this),
                ResUtil.dp2px(32),
                isLand(),
                ResUtil.isLand(this));
        int span = width / ResUtil.dp2px(ideal);
        return Math.max(2, Math.min(getEpisodeSpanCount(), span));
    }

    private boolean isOriginalEnhancedEpisodeFallback() {
        return Setting.isOriginalEnhancedDetailPage() && isTmdbSourceEnabled();
    }

    private List<Episode> getEpisodeDisplayItems(List<Episode> items) {
        EpisodeGroupAdapter.Group group = getSelectedEpisodeGroup();
        if (!shouldUseEpisodeRangePaging(items) || group == null) return items;
        return EpisodeRangePolicy.slice(items, new EpisodeRangePolicy.Range(group.name, group.start, group.end, group.selected));
    }

    private boolean shouldUseEpisodeRangePaging(List<Episode> items) {
        return shouldUseTmdbDetailLayout() && items != null && mEpisodeGroupAdapter != null && mEpisodeGroupAdapter.getItemCount() > 1;
    }

    private EpisodeGroupAdapter.Group getSelectedEpisodeGroup() {
        if (mEpisodeGroupAdapter == null || mEpisodeGroupAdapter.isEmpty()) return null;
        int position = Math.max(0, Math.min(mEpisodeGroupAdapter.getPosition(), mEpisodeGroupAdapter.getItemCount() - 1));
        return mEpisodeGroupAdapter.getItems().get(position);
    }

    private List<Episode> getCurrentEpisodeItems() {
        if (mFlagAdapter != null && !mFlagAdapter.isEmpty()) return getFlag().getEpisodes();
        return mEpisodeAdapter == null ? List.of() : mEpisodeAdapter.getItems();
    }

    private int getSelectedEpisodePosition(List<Episode> items) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
        return 0;
    }

    private void syncSelectedEpisode(Flag flag) {
        if (flag == null || mHistory == null) return;
        Episode episode = flag.find(mHistory.getEpisode(), false);
        if (episode != null) flag.toggle(true, episode);
    }

    private int getEpisodeCount() {
        return mFlagAdapter == null || mFlagAdapter.isEmpty() ? mEpisodeAdapter.getItemCount() : getFlag().getEpisodes().size();
    }

    private boolean seamless(Flag flag) {
        Episode episode = getMark().isEmpty() ? flag.find(mHistory.getEpisode(), true) : flag.find(mHistory.getVodRemarks(), false);
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return false;
        mHistory.setVodRemarks(getHistoryEpisodeName(episode));
        mHistory.setEpisodeUrl(episode.getUrl());
        onItemClick(episode);
        return true;
    }

    private void setQualityVisible(boolean visible) {
        mQualityVisible = visible;
        mBinding.qualityText.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.control.action.actionQuality.setVisibility(visible ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
        updateActionQuality(mViewModel.getPlayer().getValue());
        // 短剧 dock 的画质图标与 action 栏按钮同源。未 dock 时这个图标还在顶部栏里，
        // 此时置 VISIBLE 会让非短剧场景多出一个图标，所以只在已 dock 时同步。
        // dock 建立时会由 syncShortDramaControlLayout 用 mQualityVisible 补齐。
        if (shortDramaControlsDocked) mBinding.control.shortDramaQuality.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void updateActionQuality(Result result) {
        String name = getQualityName(result, result == null ? 0 : result.getUrl().getPosition());
        mBinding.control.action.actionQuality.setText(TextUtils.isEmpty(name) ? getString(R.string.detail_quality) : getString(R.string.detail_quality) + " " + name);
    }

    private String[] getQualityItems(Result result) {
        int count = result.getUrl().getValues().size();
        String[] items = new String[count];
        for (int i = 0; i < count; i++) items[i] = getQualityName(result, i);
        return items;
    }

    private String getQualityName(Result result, int position) {
        if (result == null || position < 0 || position >= result.getUrl().getValues().size()) return "";
        String name = result.getUrl().n(position);
        return TextUtils.isEmpty(name) ? String.valueOf(position + 1) : name;
    }

    private void onQuality() {
        Result result = mViewModel.getPlayer().getValue();
        if (result == null || !result.getUrl().isMulti()) return;
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.detail_quality)
                .setSingleChoiceItems(getQualityItems(result), result.getUrl().getPosition(), (dialog, which) -> {
                    dialog.dismiss();
                    changeQuality(result, which);
                })
                .show();
    }

    private void changeQuality(Result result, int position) {
        if (result == null || !result.getUrl().isMulti()) return;
        if (result.getUrl().getPosition() == position) {
            updateActionQuality(result);
            return;
        }
        // 换画质同集换 URL：切片结构与在途归因都属于上一画质
        resetAdFeedback();
        mQualityAdapter.setPosition(position);
        updateActionQuality(result);
        onItemClick(result);
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes());
        if (scroll) scrollEpisodeToSelected();
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        showQuickSearch(name);
        initSearch(name, false);
    }

    private void onSearch() {
        onName();
    }

    private void onShortDisplay() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        setShortDisplay();
        refreshEpisodeTitles();
    }

    private void setShortDisplay() {
        mBinding.shortDisplay.setSelected(Setting.isCompactEpisodeTitle());
        // 原生模式下铅笔按钮就是短显开关，图标要跟着一起翻。
        updateEpisodeFileNameButton();
    }

    private void onMore() {
        syncSelectedEpisode(getFlag());
        List<Episode> episodes = getFlag().getEpisodes();
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(episodes).tmdbCard(shouldUseTmdbEpisodeCards(episodes)).show(this);
    }

    private void onActor() {
        mBinding.actor.setMaxLines(mBinding.actor.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onDirector() {
        mBinding.director.setMaxLines(mBinding.director.getMaxLines() == 1 ? Integer.MAX_VALUE : 1);
    }

    private void onContent() {
        CharSequence content = mBinding.content.getText();
        if (TextUtils.isEmpty(content)) return;
        VideoContentDialog.create().content(content).show(this);
    }

    private void showQuickSearch(String keyword) {
        mQuickSearchKeyword = TextUtils.isEmpty(mQuickSearchKeyword) ? keyword : mQuickSearchKeyword;
        mQuickSearchDialog = QuickSearchDialog.create()
                .title(getString(R.string.detail_search, mQuickSearchKeyword))
                .keyword(mQuickSearchKeyword)
                .listener(this)
                .searchListener(this::onQuickSearch)
                .dismissListener(this::onQuickSearchDismiss)
                .items(mQuickAdapter.getItems());
        mQuickSearchDialog.show(this);
    }

    private void onQuickSearch(String keyword) {
        initSearch(keyword, false);
    }

    private void onQuickSearchDismiss() {
        mViewModel.stopSearch();
        mQuickSearchKeyword = null;
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.GONE);
        revealManualSearch = false;
    }

    private void onReverse() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    private void toggleEpisodeViewMode() {
        if (mBinding.episodeViewMode == null || mBinding.episodeViewMode.getVisibility() != View.VISIBLE) {
            onEpisodes();
            return;
        }
        mEpisodeGridMode = !mEpisodeGridMode;
        Setting.putTmdbEpisodeGridMode(mEpisodeGridMode);
        setEpisodeItems(getCurrentEpisodeItems());
        scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
    }

    private void updateEpisodeReverseButton() {
        if (mHistory == null) return;
        // 图标表达当前排序状态，描述表达点击后的动作。
        boolean reversed = mHistory.isRevSort();
        mBinding.reverse.setImageResource(reversed ? R.drawable.ic_action_sort_desc : R.drawable.ic_action_sort_asc);
        mBinding.reverse.setContentDescription(getString(reversed ? R.string.detail_episode_forward : R.string.detail_episode_reverse));
        applyTmdbPlaybackControlColors();
    }


    private void updateEpisodeViewModeButton() {
        if (mBinding.episodeViewMode == null) return;
        // 图标表达当前视图状态，描述表达点击后的动作。
        boolean grid = mEpisodeGridMode;
        mBinding.episodeViewMode.setImageResource(grid ? R.drawable.ic_site_grid : R.drawable.ic_site_list);
        mBinding.episodeViewMode.setContentDescription(getString(grid ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
        applyTmdbPlaybackControlColors();
    }

    private void toggleEpisodeFileName() {
        if (mBinding.episodeFileName == null || mBinding.episodeFileName.getVisibility() != View.VISIBLE) {
            return;
        }
        // 影视原生模式没有 TMDB 刮削标题，切换刮削/原文件名不会有任何变化，改为等同「短显」
        if (shouldUseUpstreamNativeEpisodeModule()) {
            onShortDisplay();
            return;
        }
        boolean showScraped = !Setting.getTmdbEpisodeShowScrapedName();
        android.util.Log.d("VideoActivity", "toggleEpisodeFileName - showScraped=" + showScraped + ", mTmdbControlsMoved=" + mTmdbControlsMoved);
        Setting.putTmdbEpisodeShowScrapedName(showScraped);
        updateEpisodeFileNameButton();
        // Fusion 模式 reparent 后 RecyclerView 刷新失效，强制重建 adapter
        if (mTmdbControlsMoved && mEpisodeAdapter != null) {
            android.util.Log.d("VideoActivity", "Fusion mode detected, recreating adapter. Item count=" + mEpisodeAdapter.getItemCount());
            int position = mEpisodeAdapter.getPosition();
            boolean useTmdbCard = mEpisodeAdapter.isUsingTmdbCard();
            ArrayList<Episode> items = new ArrayList<>(getCurrentEpisodeItems());
            android.util.Log.d("VideoActivity", "First episode tmdbEpisode=" + (items.isEmpty() ? "empty" : (items.get(0).getTmdbEpisode() != null ? "not null" : "null")));
            int viewType = !mEpisodeGridMode ? ViewType.HORI : ViewType.GRID;
            mEpisodeAdapter = new EpisodeAdapter(this, viewType, items);
            mEpisodeAdapter.setOnTitleReadyListener(this::onEpisodeTitlesReady);
            mEpisodeAdapter.setUseTmdbCard(useTmdbCard);
            mEpisodeAdapter.setNativeGridExpanded(mEpisodeGridMode && !useTmdbCard && isOriginalEnhancedEpisodeFallback());
            updateEpisodeFallbackStillUrl();
            mBinding.episode.setAdapter(mEpisodeAdapter);
            scrollToPosition(mBinding.episode, position);
        } else {
            android.util.Log.d("VideoActivity", "Normal mode, calling setEpisodeItems");
            setEpisodeItems(getCurrentEpisodeItems());
            scrollToPosition(mBinding.episode, mEpisodeAdapter.getPosition());
        }
    }

    private void updateEpisodeFileNameButton() {
        if (mBinding.episodeFileName == null) return;
        // 图标表达当前标题状态，描述表达点击后的动作。
        if (shouldUseUpstreamNativeEpisodeModule()) {
            // 原生模式没有刮削标题，这个按钮等同「短显」。
            boolean compact = Setting.isCompactEpisodeTitle();
            mBinding.episodeFileName.setImageResource(compact ? R.drawable.ic_action_name_short : R.drawable.ic_action_name_full);
            mBinding.episodeFileName.setContentDescription(getString(R.string.play_short_display));
            applyTmdbPlaybackControlColors();
            return;
        }
        boolean showScraped = Setting.getTmdbEpisodeShowScrapedName();
        // 刮削名是「序号 + 剧集标题」的规整短标题，原文件名则是完整长文件名。
        mBinding.episodeFileName.setImageResource(showScraped ? R.drawable.ic_action_name_short : R.drawable.ic_action_name_full);
        mBinding.episodeFileName.setContentDescription(getString(showScraped ? R.string.detail_episode_file_name_original_action : R.string.detail_episode_file_name_scraped_action));
        applyTmdbPlaybackControlColors();
    }

    private boolean onChange() {
        checkSearch(true);
        return true;
    }

    private boolean onSearchGlobal() {
        SearchActivity.start(this, mBinding.name.getText().toString());
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onBack() {
        if (isFullscreen() && isShortDramaSession()) finishShortDrama();
        else if (isFullscreen()) exitFullscreen();
        else finishVideoPlayback();
    }

    private void finishVideoPlayback() {
        markPlaybackExiting();
        saveHistory(true);
        finishPlayback();
    }

    private void onCast() {
        if (mHistory == null || TextUtils.isEmpty(mHistory.getVodId()) || service() == null || player().isEmpty() || TextUtils.isEmpty(player().getUrl())) {
            Notify.show(R.string.cast_not_ready);
            return;
        }
        CastVideo video = new CastVideo(mBinding.name.getText().toString(), player().getUrl(), player().getPosition(), player().getHeaders());
        CastDialog.create().history(mHistory).video(video).fm(true).show(this);
    }

    private void onInfo() {
        InfoDialog.create().title(mBinding.control.title.getText()).headers(player().getHeaders()).url(player().getUrl()).show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
        updateTmdbKeepState();
    }

    private void checkPlay() {
        setR1Callback();
        if (player() == null) return;
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        advanceEpisode(notify);
    }

    /** @return 是否真的切走了。末集切不动，调用方据此决定要不要提示「进入下一集」。 */
    private boolean advanceEpisode(boolean notify) {
        setR1Callback();
        int offset = mHistory != null && mHistory.isRevPlay() ? -1 : 1;
        Episode item = getAdjacentEpisode(offset);
        if (!item.isSelected()) {
            onItemClick(item);
            return true;
        }
        if (notify) Notify.show(offset > 0 ? R.string.error_play_next : R.string.error_play_prev);
        return false;
    }

    private void checkPrev() {
        checkPrev(true);
    }

    private void checkPrev(boolean notify) {
        setR1Callback();
        int offset = mHistory != null && mHistory.isRevPlay() ? 1 : -1;
        Episode item = getAdjacentEpisode(offset);
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(offset > 0 ? R.string.error_play_next : R.string.error_play_prev);
    }

    private Episode getAdjacentEpisode(int offset) {
        List<Episode> items = mFlagAdapter == null || mFlagAdapter.isEmpty() ? mEpisodeAdapter.getItems() : getFlag().getEpisodes();
        if (items.isEmpty()) return new Episode();
        int position = getSelectedEpisodePosition(items) + offset;
        position = Math.max(0, Math.min(position, items.size() - 1));
        return items.get(position);
    }

    private void onSetting() {
        setTrackVisible();
        ControlDialog.create().parent(mBinding).history(mHistory).parse(isUseParse()).player(player()).show(this);
    }

    /**
     * 彈出劇集多選視窗，將選中的劇集加入下載佇列。
     */
    private void onDownload() {
        Flag flag = getFlag();
        if (flag == null || flag.getEpisodes().isEmpty()) {
            Notify.show(R.string.download_no_episode);
            return;
        }
        List<Episode> episodes = flag.getEpisodes();
        Set<Integer> downloaded = new HashSet<>();
        String key = getKey();
        String vodId = getId();
        for (int i = 0; i < episodes.size(); i++) {
            String id = DownloadItem.buildId(key, vodId, flag.getFlag(), episodes.get(i).getUrl());
            if (DownloadManager.get().find(id) != null) downloaded.add(i);
        }
        DownloadEpisodeDialog.show(this, episodes, downloaded, selected -> addDownload(flag, selected));
    }

    private void addDownload(Flag flag, List<Episode> selected) {
        Vod vod = new Vod();
        vod.setId(getId());
        vod.setName(getPlaybackName());
        vod.setPic(getPic());
        List<DownloadItem> items = new ArrayList<>();
        for (Episode episode : selected) items.add(DownloadItem.create(getKey(), vod, flag.getFlag(), episode.getName(), episode.getUrl()));
        DownloadManager.get().add(items);
        Notify.show(getString(R.string.download_added, items.size()));
    }

    private void onLock() {
        setLock(!isLock());
        setRequestedOrientation(getLockOrient());
        mKeyDown.setLock(isLock());
        checkLockImg();
        showControl();
    }

    private void onRotate() {
        setR1Callback();
        setRotate(!isRotate());
        setRequestedOrientation(PlaybackOrientation.getRotateOrientation(isRotate()));
    }

    private void onFullscreen() {
        boolean exit = isFullscreen();
        if (exit) exitFullscreen();
        else enterFullscreen();
        showControl();
        if (!exit) scheduleFullscreenControlReveal();
    }

    private void onPiP() {
        if (!canShowPiP(isShortDramaSession())) return;
        hideControl();
        mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getScale(), true);
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).search(this::showSubtitleSearch).show(this);
        hideControl();
    }

    private void onTrack(int type) {
        TrackDialog.create().type(type).player(player()).search(this::showSubtitleSearch).show(this);
        hideControl();
    }

    @Override
    public void onTrackPanel(int type) {
        TrackDialog.create().type(type).player(player()).search(this::showSubtitleSearch).show(this);
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
    }

    @Override
    public void onTitlePanel() {
        TitleDialog.create().player(player()).show(this);
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).identity(getKey(), getId(), mHistory == null ? "" : mHistory.getVodName(), getDanmakuEpisodeName()).show(this);
        hideControl();
    }

    private void onAdFeedback() {
        if (player() == null || TextUtils.isEmpty(player().getUrl())) {
            Notify.show(R.string.ad_feedback_no_url);
            return;
        }
        if (!isAdFeedbackEnabled()) {
            Notify.show(R.string.ad_interval_invalid);
            return;
        }
        hideControl();
        submitAdFeedback();
    }

    @Override
    public void onDanmakuPanel() {
        DanmakuDialog.create().player(player()).identity(getKey(), getId(), mHistory == null ? "" : mHistory.getVodName(), getDanmakuEpisodeName()).show(this);
    }

    @Override
    public void onDisplayChanged() {
        if (mOsd != null) {
            mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
            mOsd.start();
        }
        setPlayParamsState();
        if (service() == null || player() == null) return;
        mBinding.control.osdDiagnostics.setVisibility(PlayerSetting.isOsdDiagnostics() && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.osdDiagnostics.setAlpha(mOsd != null && mOsd.isDiagnosticsVisible() ? 1f : 0.72f);
    }

    @Override
    public void onImmersiveAudioModeChanged() {
        boolean enabled = PlayerSetting.isImmersiveAudioMode();
        boolean wasAudioStageVisible = mAudioStageVisible;
        updateAudioOnlyState();
        if (enabled) {
            ensureImmersiveAudioControllers();
            applyPlaybackArtwork(getPlaybackEpisode());
            refreshLyrics();
            reloadKaraokeTrack();
        } else if (wasAudioStageVisible) {
            restoreVideoTrackAfterAudioStage();
        }
        SpiderDebug.log("audio-mode", "toggle enabled=%s stage=%s artwork=%s owner=%s", enabled, mAudioStageVisible, !TextUtils.isEmpty(mArtworkRequestUrl), mPlaybackEpisodeKey);
    }

    private void restoreVideoTrackAfterAudioStage() {
        mBinding.video.postDelayed(() -> {
            if (service() == null || mAudioStageVisible || PlayerSetting.isImmersiveAudioMode() || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return;
            player().restoreVideoTrack();
            SpiderDebug.log("audio-mode", "restore video track player=%s position=%d", player().getPlayerText(), player().getPosition());
        }, 200);
    }

    @Override
    public void onKaraokeModeChanged() {
        setKaraokeActionState();
        syncKaraokeStageVisibility();
        if (PlayerSetting.isKaraokeMode()) {
            mKaraokeResultShown = false;
            refreshLyrics();
        }
        else if (mKaraoke != null) mKaraoke.clear();
    }

    private void onKaraokeMode() {
        showKaraokeModePanel();
    }

    private void setKaraokeMode(boolean enable) {
        if (PlayerSetting.isKaraokeMode() == enable) return;
        PlayerSetting.putKaraokeMode(enable);
        onKaraokeModeChanged();
        showControl();
    }

    @Override
    public void onKaraokeTrackPanel() {
        showLyricsSettingsPanel(LYRICS_TAB_TRACK);
    }

    private void showKaraokeModePanel() {
        showLyricsSettingsPanel(LYRICS_TAB_LYRICS);
    }

    private void showLyricsSettingsPanel() {
        showLyricsSettingsPanel(LYRICS_TAB_LYRICS);
    }

    private void showLyricsSettingsPanel(int selectedTab) {
        if (service() == null) return;
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        int tab = Math.max(LYRICS_TAB_LYRICS, Math.min(LYRICS_TAB_TRACK, selectedTab));
        root.addView(createLyricsSettingsTabs(dialog, tab), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        if (tab == LYRICS_TAB_KARAOKE) {
            root.addView(createKaraokeModeHeader(), lyricsSettingRowParams(8, 38));
            root.addView(createKaraokeDelayControl(), lyricsSettingRowParams(6, 42));
            root.addView(createKaraokeActionGrid(dialog, true,
                    new String[]{getString(R.string.player_karaoke_difficulty) + " " + karaokeDifficultyText()},
                    new Runnable[]{this::showKaraokeDifficultyPanel},
                    3), karaokeActionGridParams(8));
        } else if (tab == LYRICS_TAB_TRACK) {
            root.addView(createKaraokeActionGrid(dialog, true,
                    new String[]{
                            getString(R.string.player_karaoke_track_generate_pitch),
                            getString(R.string.player_karaoke_track_clear),
                            getString(R.string.player_karaoke_track_search),
                            getString(R.string.player_karaoke_track_import_file),
                            getString(R.string.player_karaoke_track_import_url),
                            getString(R.string.player_karaoke_track_sources),
                            getKaraokeBasicPitchLabel()
                    },
                    new Runnable[]{
                            this::generateKaraokePitchTrack,
                            this::clearKaraokeTrackBinding,
                            this::showKaraokeTrackSearchDialog,
                            this::chooseKaraokeTrackFile,
                            this::showKaraokeTrackUrlDialog,
                            this::showKaraokeTrackSourcesDialog,
                            this::toggleKaraokeBasicPitchTfliteFromSettings
                    },
                    new boolean[]{true, false, true, true, true, true, true},
                    3), karaokeActionGridParams(8));
        } else {
            root.addView(createLyricsOffsetControl(), lyricsSettingRowParams(8, 42));
            root.addView(createKaraokeActionGrid(dialog, true,
                    new String[]{
                            getString(R.string.player_lyrics_rows) + " " + getLyricsRowsText(),
                            getString(R.string.player_lyrics_size) + " " + lyricsSizeText(),
                            getString(R.string.player_lyrics_source) + " " + lyricsSourceText(),
                            getString(R.string.player_lyrics_search),
                            getString(R.string.player_desktop_lyrics) + " " + getSwitch(PlayerSetting.isDesktopLyrics()),
                            getString(R.string.player_lyrics_cache) + " " + getString(R.string.player_lyrics_cache_value, LyricsRepository.cacheCount())
                    },
                    new Runnable[]{
                            this::showLyricsRowsPanel,
                            this::showLyricsSizePanel,
                            this::showLyricsSourcePanel,
                            this::openLyricsSearchFromSettings,
                            this::toggleDesktopLyrics,
                            this::clearLyricsCacheFromSettings
                    },
                    3), karaokeActionGridParams(8));
        }
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private void showLyricsRowsPanel() {
        String[] items = new String[5];
        for (int i = 0; i < items.length; i++) items[i] = getString(R.string.player_lyrics_rows_value, i + 1);
        showLyricsChoicePanel(getString(R.string.player_lyrics_rows), items, PlayerSetting.getLyricsRows() - 1, which -> {
            PlayerSetting.putLyricsRows(which + 1);
            applyLyricsRuntimeSettings();
        }, LYRICS_TAB_LYRICS);
    }

    private void showLyricsSizePanel() {
        showLyricsChoicePanel(getString(R.string.player_lyrics_size), ResUtil.getStringArray(R.array.select_lyrics_size), PlayerSetting.getLyricsTextSizeOption(), which -> {
            PlayerSetting.putLyricsTextSizeOption(which);
            applyLyricsRuntimeSettings();
        }, LYRICS_TAB_LYRICS);
    }

    private void showLyricsSourcePanel() {
        showLyricsChoicePanel(getString(R.string.player_lyrics_source), ResUtil.getStringArray(R.array.select_lyrics_source), LyricsSetting.getSourceMode(), which -> {
            LyricsSetting.putSourceMode(which);
            if (mLyrics != null) mLyrics.clear();
            refreshLyrics();
        }, LYRICS_TAB_LYRICS);
    }

    private void showKaraokeDifficultyPanel() {
        showLyricsChoicePanel(getString(R.string.player_karaoke_difficulty), ResUtil.getStringArray(R.array.select_karaoke_difficulty), PlayerSetting.getKaraokeDifficulty(), which -> {
            PlayerSetting.putKaraokeDifficulty(which);
            reloadKaraokeTrack();
        }, LYRICS_TAB_KARAOKE);
    }

    private void showLyricsChoicePanel(String title, String[] items, int selected, LyricsChoiceHandler handler, int returnTab) {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createKaraokeSheetHeader(dialog, title, () -> showLyricsSettingsPanel(returnTab)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        root.addView(createLyricsChoiceGrid(dialog, items, selected, handler, returnTab), karaokeActionGridParams(8));
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private LinearLayout createLyricsChoiceGrid(BottomSheetDialog dialog, String[] items, int selected, LyricsChoiceHandler handler, int returnTab) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int columns = 3;
        for (int i = 0; i < items.length; i++) {
            if (i % columns == 0) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(40));
                if (i > 0) rowParams.topMargin = ResUtil.dp2px(6);
                grid.addView(row, rowParams);
            }
            LinearLayout row = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
            final int index = i;
            row.addView(createLyricsChoiceItem(items[i], i == selected, () -> {
                dialog.dismiss();
                handler.onChoice(index);
                showLyricsSettingsPanel(returnTab);
            }), karaokeActionButtonParams(i % columns > 0));
        }
        return grid;
    }

    private interface LyricsChoiceHandler {
        void onChoice(int which);
    }

    private LinearLayout createLyricsSettingsTabs(BottomSheetDialog dialog, int selectedTab) {
        return createSegmentedControl(
                new String[]{getString(R.string.player_audio_badge_lyrics), getString(R.string.player_karaoke_mode), getString(R.string.player_karaoke_track)},
                selectedTab,
                index -> {
                    if (index == selectedTab) return;
                    dialog.dismiss();
                    showLyricsSettingsPanel(index);
                });
    }

    private void showKaraokeTrackAdvancedPanel() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createKaraokeSheetHeader(dialog, getString(R.string.player_karaoke_track_advanced), this::showKaraokeModePanel), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        root.addView(createAudioSheetSection(getString(R.string.player_karaoke_track)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(40)));
        root.addView(createKaraokeActionGrid(dialog, true,
                new String[]{
                        getKaraokeBasicPitchLabel(),
                        getString(R.string.player_karaoke_track_search),
                        getString(R.string.player_karaoke_track_import_file),
                        getString(R.string.player_karaoke_track_import_url),
                        getString(R.string.player_karaoke_track_sources)
                },
                new Runnable[]{
                        this::toggleKaraokeBasicPitchTflite,
                        this::showKaraokeTrackSearchDialog,
                        this::chooseKaraokeTrackFile,
                        this::showKaraokeTrackUrlDialog,
                        this::showKaraokeTrackSourcesDialog
                },
                2), karaokeActionGridParams(6));
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private String getKaraokeBasicPitchLabel() {
        return getString(R.string.player_karaoke_track_basic_pitch_tflite, getString(PlayerSetting.isKaraokeBasicPitchTflite() ? R.string.player_karaoke_track_option_enabled : R.string.player_karaoke_track_option_disabled));
    }

    private void toggleKaraokeBasicPitchTflite() {
        PlayerSetting.putKaraokeBasicPitchTflite(!PlayerSetting.isKaraokeBasicPitchTflite());
        showKaraokeTrackAdvancedPanel();
    }

    private void toggleKaraokeBasicPitchTfliteFromSettings() {
        PlayerSetting.putKaraokeBasicPitchTflite(!PlayerSetting.isKaraokeBasicPitchTflite());
        showLyricsSettingsPanel(LYRICS_TAB_TRACK);
    }

    private LinearLayout createKaraokeSheetHeader(BottomSheetDialog dialog, String title, Runnable backAction) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView titleView = createAudioSheetTitle(title);
        row.addView(titleView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(createKaraokeHeaderButton(dialog, getString(R.string.player_karaoke_track_back), backAction), new LinearLayout.LayoutParams(ResUtil.dp2px(76), ResUtil.dp2px(32)));
        return row;
    }

    private TextView createKaraokeHeaderButton(BottomSheetDialog dialog, String label, Runnable action) {
        TextView view = createAudioSheetText(label, 14, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextColor(0xE6FFFFFF);
        view.setBackground(roundRect(0x12FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x24FFFFFF));
        view.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return view;
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, int columns) {
        return createKaraokeActionGrid(dialog, compact, labels, actions, columns, true);
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, int columns, boolean dismissOnClick) {
        return createKaraokeActionGrid(dialog, compact, labels, actions, null, columns, dismissOnClick);
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, boolean[] dismissOnClicks, int columns) {
        return createKaraokeActionGrid(dialog, compact, labels, actions, dismissOnClicks, columns, true);
    }

    private LinearLayout createKaraokeActionGrid(BottomSheetDialog dialog, boolean compact, String[] labels, Runnable[] actions, @Nullable boolean[] dismissOnClicks, int columns, boolean dismissOnClick) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        int safeColumns = Math.max(1, columns);
        for (int i = 0; i < labels.length; i += safeColumns) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            boolean fullRow = !compact && i + 1 == labels.length;
            for (int j = 0; j < safeColumns; j++) {
                int index = i + j;
                if (index >= labels.length) break;
                boolean dismiss = dismissOnClicks == null || index >= dismissOnClicks.length ? dismissOnClick : dismissOnClicks[index];
                row.addView(createKaraokeActionButton(dialog, labels[index], actions[index], compact, dismiss), fullRow ? karaokeActionButtonFullParams() : karaokeActionButtonParams(j > 0));
                if (fullRow) break;
            }
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(compact ? 46 : 48));
            if (i > 0) rowParams.topMargin = ResUtil.dp2px(8);
            grid.addView(row, rowParams);
        }
        return grid;
    }

    private TextView createKaraokeActionButton(BottomSheetDialog dialog, String label, Runnable action, boolean compact) {
        return createKaraokeActionButton(dialog, label, action, compact, true);
    }

    private TextView createKaraokeActionButton(BottomSheetDialog dialog, String label, Runnable action, boolean compact, boolean dismissOnClick) {
        TextView view = createAudioSheetText(label, compact ? 14 : 15, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(10), 0);
        view.setTextColor(0xF2FFFFFF);
        view.setBackground(roundRect(0x14FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x22FFFFFF));
        view.setOnClickListener(v -> {
            if (dismissOnClick) dialog.dismiss();
            action.run();
        });
        return view;
    }

    private LinearLayout.LayoutParams karaokeActionGridParams(int topMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ResUtil.dp2px(topMarginDp);
        return params;
    }

    private LinearLayout.LayoutParams karaokeActionButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(10);
        return params;
    }

    private LinearLayout.LayoutParams karaokeActionButtonFullParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private View createKaraokeModeHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 0, 0, 0);
        boolean enabled = PlayerSetting.isKaraokeMode();

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.HORIZONTAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = createAudioSheetText(getString(R.string.player_karaoke_mode), 15, true);
        TextView status = createAudioSheetText(getString(enabled ? R.string.player_karaoke_mode_enabled : R.string.player_karaoke_mode_disabled), 13, false);
        title.setTextColor(Color.WHITE);
        title.setSingleLine(true);
        status.setSingleLine(true);
        status.setEllipsize(TextUtils.TruncateAt.END);
        status.setTextColor(enabled ? SHEET_TEXT_SECONDARY : SHEET_TEXT_MUTED);
        text.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        statusParams.leftMargin = ResUtil.dp2px(10);
        text.addView(status, statusParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        FrameLayout toggle = createKaraokeModeToggle(enabled);
        row.setOnClickListener(v -> {
            boolean next = !PlayerSetting.isKaraokeMode();
            setKaraokeMode(next);
            status.setText(getString(next ? R.string.player_karaoke_mode_enabled : R.string.player_karaoke_mode_disabled));
            status.setTextColor(next ? SHEET_TEXT_SECONDARY : SHEET_TEXT_MUTED);
            updateKaraokeModeToggle(toggle, next);
        });
        row.addView(toggle, new LinearLayout.LayoutParams(ResUtil.dp2px(50), ResUtil.dp2px(28)));
        return row;
    }

    private FrameLayout createKaraokeModeToggle(boolean enabled) {
        FrameLayout toggle = new FrameLayout(this);
        updateKaraokeModeToggle(toggle, enabled);
        return toggle;
    }

    private void updateKaraokeModeToggle(FrameLayout toggle, boolean enabled) {
        toggle.removeAllViews();
        toggle.setBackground(roundRect(enabled ? SHEET_CONTROL_BG_SELECTED : 0x18FFFFFF, 8, 1, enabled ? SHEET_CONTROL_STROKE_SELECTED : 0x2EFFFFFF));
        View knob = new View(this);
        knob.setBackground(roundRect(enabled ? SHEET_TEXT_PRIMARY : 0xFFE6E8EE, 6, 0, 0));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ResUtil.dp2px(22), ResUtil.dp2px(22), enabled ? Gravity.RIGHT | Gravity.CENTER_VERTICAL : Gravity.LEFT | Gravity.CENTER_VERTICAL);
        params.leftMargin = ResUtil.dp2px(3);
        params.rightMargin = ResUtil.dp2px(3);
        toggle.addView(knob, params);
    }

    private View createLyricsOffsetControl() {
        return createLyricsStepControl(getString(R.string.player_lyrics_offset), getLyricsOffsetText(), "-0.5s", "0", "+0.5s",
                value -> PlayerSetting.putLyricsTimeOffsetMs(value),
                () -> PlayerSetting.getLyricsTimeOffsetMs(),
                LYRICS_OFFSET_MIN_MS,
                LYRICS_OFFSET_MAX_MS,
                LYRICS_OFFSET_STEP_MS,
                this::applyLyricsRuntimeSettings);
    }

    private View createKaraokeDelayControl() {
        return createLyricsStepControl(getString(R.string.player_karaoke_mic_delay), getKaraokeDelayText(), "-0.1s", "0", "+0.1s",
                value -> PlayerSetting.putKaraokeMicDelayMs(value),
                () -> PlayerSetting.getKaraokeMicDelayMs(),
                KARAOKE_DELAY_MIN_MS,
                KARAOKE_DELAY_MAX_MS,
                KARAOKE_DELAY_STEP_MS,
                this::reloadKaraokeTrack);
    }

    private View createLyricsStepControl(String label, String valueText, String minus, String reset, String plus, LyricsLongSetter setter, LyricsLongGetter getter, long min, long max, long step, Runnable afterChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(10), 0);
        row.setBackground(roundRect(0x12FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x22FFFFFF));

        LinearLayout text = new LinearLayout(this);
        text.setGravity(Gravity.CENTER_VERTICAL);
        text.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = createAudioSheetText(label, 15, false);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        TextView value = createAudioSheetText(valueText, 13, true);
        value.setSingleLine(true);
        value.setTextColor(SHEET_TEXT_SECONDARY);
        text.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.leftMargin = ResUtil.dp2px(10);
        text.addView(value, valueParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.addView(createLyricsStepButton(minus, () -> applyLyricsLongSetting(setter, getter, min, max, -step, value, afterChange)), lyricsStepButtonParams(false));
        buttons.addView(createLyricsStepButton(reset, () -> applyLyricsLongSetting(setter, () -> 0L, min, max, 0, value, afterChange)), lyricsStepButtonParams(true));
        buttons.addView(createLyricsStepButton(plus, () -> applyLyricsLongSetting(setter, getter, min, max, step, value, afterChange)), lyricsStepButtonParams(true));
        row.addView(buttons, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void applyLyricsLongSetting(LyricsLongSetter setter, LyricsLongGetter getter, long min, long max, long delta, TextView value, Runnable afterChange) {
        long next = Math.min(Math.max(getter.get() + delta, min), max);
        setter.set(next);
        value.setText(formatLyricsOffset(getter.get()));
        if (afterChange != null) afterChange.run();
    }

    private TextView createLyricsStepButton(String label, Runnable action) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextColor(0xF2FFFFFF);
        view.setBackground(roundRect(0x16FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x28FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private LinearLayout.LayoutParams lyricsStepButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(52), ResUtil.dp2px(34));
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(6);
        return params;
    }

    private TextView createLyricsChoiceItem(String label, boolean selected, Runnable action) {
        TextView item = createAudioSheetText(label, 15, selected);
        item.setGravity(Gravity.CENTER);
        item.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        item.setSingleLine(true);
        item.setEllipsize(TextUtils.TruncateAt.END);
        item.setTextColor(selected ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
        item.setBackground(lyricsResultItemBackground(selected));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private LinearLayout.LayoutParams lyricsSettingRowParams(int topDp, int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(heightDp));
        params.topMargin = ResUtil.dp2px(topDp);
        return params;
    }

    private void openLyricsSearchFromSettings() {
        if (!onLyricsSearch()) Notify.show(R.string.player_lyrics_not_found);
    }

    private void clearLyricsCacheFromSettings() {
        LyricsRepository.clearCache();
        Notify.show(R.string.player_lyrics_cache_cleared);
    }

    private void applyLyricsRuntimeSettings() {
        if (service() == null || player().isEmpty()) return;
        if (mLyrics != null) {
            mLyrics.refreshStyle();
            mLyrics.update(player());
        }
        syncKaraokePosition();
        if (mKaraoke != null) mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());
    }

    private boolean toggleDesktopLyrics() {
        boolean enabled = !PlayerSetting.isDesktopLyrics();
        PlayerSetting.putDesktopLyrics(enabled);
        if (enabled && !canDrawOverlays()) openOverlayPermission();
        return enabled;
    }

    private boolean canDrawOverlays() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void openOverlayPermission() {
        Notify.show(R.string.player_desktop_lyrics_permission);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
        }
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    private String lyricsSizeText() {
        String[] items = ResUtil.getStringArray(R.array.select_lyrics_size);
        return items[PlayerSetting.getLyricsTextSizeOption()];
    }

    private String lyricsSourceText() {
        String[] items = ResUtil.getStringArray(R.array.select_lyrics_source);
        return items[LyricsSetting.getSourceMode()];
    }

    private String karaokeDifficultyText() {
        String[] items = ResUtil.getStringArray(R.array.select_karaoke_difficulty);
        return items[PlayerSetting.getKaraokeDifficulty()];
    }

    private String getLyricsRowsText() {
        return getString(R.string.player_lyrics_rows_value, PlayerSetting.getLyricsRows());
    }

    private String getLyricsOffsetText() {
        return formatLyricsOffset(PlayerSetting.getLyricsTimeOffsetMs());
    }

    private String getKaraokeDelayText() {
        return formatLyricsOffset(PlayerSetting.getKaraokeMicDelayMs());
    }

    private String formatLyricsOffset(long valueMs) {
        if (valueMs == 0) return "0s";
        return String.format(Locale.getDefault(), "%+.1fs", valueMs / 1000f);
    }

    private interface LyricsLongSetter {
        void set(long value);
    }

    private interface LyricsLongGetter {
        long get();
    }

    private void chooseKaraokeTrackFile() {
        FileChooser.from(mKaraokeTrackFile).show("*/*", new String[]{"text/plain", "audio/midi", "audio/x-midi", "application/octet-stream", "*/*"});
    }

    private void showKaraokeTrackUrlDialog() {
        showAudioTextInputSheet(R.string.player_karaoke_track_import_url, R.string.player_karaoke_track_url_hint, "", true, 2,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                EditorInfo.IME_ACTION_DONE,
                this::importKaraokeTrackUrl);
    }

    private void showKaraokeTrackSourcesDialog() {
        showAudioTextInputSheet(R.string.player_karaoke_track_sources, R.string.player_karaoke_track_sources_hint, PlayerSetting.getKaraokeGithubSources(), true, 4,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS | InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                EditorInfo.IME_ACTION_DONE,
                this::saveKaraokeTrackSources);
    }

    private void saveKaraokeTrackSources(String sources) {
        PlayerSetting.putKaraokeGithubSources(sources);
        KaraokeTrackRepository.clearSearchCache();
        Notify.show(R.string.player_karaoke_track_sources_saved);
    }

    private void showKaraokeTrackSearchDialog() {
        showAudioTextInputSheet(R.string.player_karaoke_track_search, R.string.player_karaoke_track_keyword, KaraokeTrackRepository.defaultKeyword(player()), false, 1,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                EditorInfo.IME_ACTION_SEARCH,
                this::searchKaraokeTrack);
    }

    private void showAudioTextInputSheet(int titleRes, int hintRes, String text, boolean multiLine, int minLines, int inputType, int imeAction, AudioTextInputHandler handler) {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(titleRes)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextInputLayout layout = new TextInputLayout(this);
        styleAudioSheetInput(layout, getString(hintRes));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x70FFFFFF);
        input.setInputType(inputType);
        input.setImeOptions(imeAction);
        input.setText(Objects.toString(text, ""));
        input.setSelectAllOnFocus(!multiLine);
        if (multiLine) {
            input.setSingleLine(false);
            input.setMinLines(minLines);
            input.setMaxLines(Math.max(minLines, 4));
        } else {
            input.setSingleLine(true);
            input.setMaxLines(1);
        }
        if (input.getText() != null) input.setSelection(input.getText().length());
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(layout, audioSheetWrapTopParams(10));

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createAudioSheetButton(getString(R.string.dialog_negative), false, dialog::dismiss), audioSheetButtonParams(false));
        actions.addView(createAudioSheetButton(getString(R.string.dialog_positive), true, () -> {
            Util.hideKeyboard(input);
            dialog.dismiss();
            handler.onSubmit(input.getText() == null ? "" : input.getText().toString().trim());
        }), audioSheetButtonParams(true));
        root.addView(actions, audioSheetTopParams(10, 42));
        dialog.setContentView(root);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (multiLine || actionId != imeAction) return false;
            Util.hideKeyboard(input);
            dialog.dismiss();
            handler.onSubmit(input.getText() == null ? "" : input.getText().toString().trim());
            return true;
        });
        showLyricsSettingsSheet(dialog);
        input.post(() -> Util.showKeyboard(input));
    }

    private interface AudioTextInputHandler {
        void onSubmit(String text);
    }

    private void searchKaraokeTrack(String keyword) {
        if (service() == null || TextUtils.isEmpty(keyword)) return;
        Notify.show(R.string.player_karaoke_track_searching);
        KaraokeTrackRepository.search(player(), keyword, results -> {
            if (results == null || results.isEmpty()) {
                Notify.show(R.string.player_karaoke_track_not_found);
                return;
            }
            showKaraokeTrackResults(results);
        });
    }

    private void generateKaraokeTrack() {
        List<LyricsLine> lines = getKaraokeGenerationLines();
        android.util.Log.i("karaoke-generate", "rhythm requested lines=" + lines.size() + " inline=" + (!TextUtils.isEmpty(mInlineLyrics)) + " service=" + (service() != null));
        if (service() == null || !KaraokeTrackRepository.canGenerate(lines)) {
            Notify.show(R.string.player_karaoke_track_generate_no_lyrics);
            return;
        }
        onKaraokeTrackGenerated(KaraokeTrackRepository.importGenerated(player(), lines));
    }

    private void onKaraokeTrackGenerated(KaraokeTrackRepository.ImportResult result) {
        if (result != null && result.isSuccess()) {
            Notify.show(R.string.player_karaoke_track_generated);
            applyKaraokeTrackChange(true);
            restartKaraokePlaybackAfterGeneration();
        } else {
            String error = result == null ? "" : result.getError();
            Notify.show(getString(R.string.player_karaoke_track_generate_failed) + (TextUtils.isEmpty(error) ? "" : "\n" + error));
        }
    }

    private void generateKaraokePitchTrack() {
        List<LyricsLine> lines = getKaraokeGenerationLines();
        KaraokeTrackRepository.MediaInput input = service() == null ? null : KaraokeTrackRepository.snapshot(player());
        android.util.Log.i("karaoke-generate", "pitch requested basicPitch=" + PlayerSetting.isKaraokeBasicPitchTflite() + " lines=" + lines.size() + " input=" + (input == null ? "null" : input.getUrl()));
        if (!KaraokeTrackRepository.canGeneratePitch(input, lines)) {
            Notify.show(R.string.player_karaoke_track_generate_no_lyrics);
            return;
        }
        cancelKaraokePitchGeneration(false);
        AtomicBoolean cancel = new AtomicBoolean(false);
        mKaraokePitchCancel = cancel;
        showKaraokePitchProgress();
        mKaraokePitchFuture = Task.submit(() -> {
            KaraokeTrackRepository.ImportResult result = KaraokeTrackRepository.importGeneratedPitch(input, lines, (percent, stage, elapsedMs, remainingMs) -> {
                if (cancel.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("cancelled");
                App.post(() -> updateKaraokePitchProgress(percent, stage, remainingMs));
            });
            App.post(() -> onKaraokePitchTrackGenerated(result, cancel));
        });
    }

    private List<LyricsLine> getKaraokeGenerationLines() {
        List<LyricsLine> lines = mLyrics == null ? null : mLyrics.getLines();
        if (lines != null && !lines.isEmpty()) return lines;
        String raw = !TextUtils.isEmpty(mInlineLyrics) ? mInlineLyrics : mDetailLyrics;
        if (!LyricsController.hasTimedLyrics(raw)) return new ArrayList<>();
        LyricsResult result = new LyricsResult("Inline", getAudioStageTitle(), getAudioStageArtist(getAudioStageTitle()), "", raw, player().getDuration(), true, 100);
        return new ArrayList<>(result.getLines(player().getDuration()));
    }

    private void onKaraokePitchTrackGenerated(KaraokeTrackRepository.ImportResult result, AtomicBoolean cancel) {
        if (cancel != null && cancel.get()) {
            if (mKaraokePitchCancel == cancel) {
                mKaraokePitchFuture = null;
                mKaraokePitchCancel = null;
                dismissKaraokePitchProgress();
            }
            return;
        }
        mKaraokePitchFuture = null;
        if (mKaraokePitchCancel == cancel) mKaraokePitchCancel = null;
        dismissKaraokePitchProgress();
        if (result != null && result.isSuccess()) {
            applyKaraokeTrackChange(true);
            restartKaraokePlaybackAfterGeneration();
            showKaraokePitchResult(R.string.player_karaoke_track_generated_pitch, getString(R.string.player_karaoke_track_generated_pitch_message));
        } else {
            String error = result == null ? "" : result.getError();
            showKaraokePitchResult(R.string.player_karaoke_track_generate_pitch_failed, getString(R.string.player_karaoke_track_generate_pitch_failed_message, getKaraokePitchFailureMessage(error)));
        }
    }

    private String getKaraokePitchFailureMessage(String error) {
        if (KaraokeTrackRepository.isUnsupportedPitchSourceError(error)) return getString(R.string.player_karaoke_track_generate_pitch_unsupported_source);
        return TextUtils.isEmpty(error) ? getString(R.string.player_karaoke_track_generate_pitch_failed) : error;
    }

    private void restartKaraokePlaybackAfterGeneration() {
        if (service() == null || player().isEmpty()) return;
        player().seekTo(0);
        if (mHistory != null) mHistory.setPosition(0);
        if (mLyrics != null) mLyrics.update(0);
        syncKaraokePosition();
    }

    private void showKaraokePitchProgress() {
        dismissKaraokePitchProgress();
        if (isFinishing() || isDestroyed()) {
            Notify.show(R.string.player_karaoke_track_generating_pitch);
            return;
        }
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_karaoke_track_generating_pitch)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextView message = createAudioSheetText(getString(R.string.player_karaoke_track_generating_pitch_message), 14, false);
        message.setTextColor(0xCCFFFFFF);
        message.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(message, audioSheetTopParams(10, 46));
        mKaraokePitchMessage = createAudioSheetText("", 15, true);
        mKaraokePitchMessage.setTextColor(SHEET_TEXT_SECONDARY);
        mKaraokePitchMessage.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(mKaraokePitchMessage, audioSheetTopParams(4, 36));
        mKaraokePitchProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        mKaraokePitchProgress.setIndeterminate(false);
        mKaraokePitchProgress.setMax(100);
        mKaraokePitchProgress.setProgressTintList(ColorStateList.valueOf(0xE6FFFFFF));
        mKaraokePitchProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0x2AFFFFFF));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(6));
        params.topMargin = ResUtil.dp2px(8);
        root.addView(mKaraokePitchProgress, params);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createAudioSheetButton(getString(R.string.player_karaoke_track_generation_stop), false, () -> cancelKaraokePitchGeneration(true)), audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(12, 40));
        dialog.setContentView(root);
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(d -> cancelKaraokePitchGeneration(true));
        mKaraokePitchDialog = dialog;
        showAudioSheet(dialog, false);
        updateKaraokePitchProgress(1, KaraokePitchTrackGenerator.STAGE_PREPARE, -1);
    }

    private void updateKaraokePitchProgress(int percent, int stage, long remainingMs) {
        if (mKaraokePitchProgress == null || mKaraokePitchMessage == null) return;
        int safePercent = Math.max(0, Math.min(100, percent));
        mKaraokePitchProgress.setProgress(safePercent);
        mKaraokePitchMessage.setText(getString(R.string.player_karaoke_track_generating_pitch_progress, safePercent, getKaraokePitchStageName(stage), formatKaraokePitchRemaining(remainingMs)));
    }

    private String getKaraokePitchStageName(int stage) {
        if (stage == KaraokePitchTrackGenerator.STAGE_DECODE) return getString(R.string.player_karaoke_track_pitch_stage_decode);
        if (stage == KaraokePitchTrackGenerator.STAGE_ANALYZE) return getString(R.string.player_karaoke_track_pitch_stage_analyze);
        if (stage == KaraokePitchTrackGenerator.STAGE_WRITE) return getString(R.string.player_karaoke_track_pitch_stage_write);
        if (stage == KaraokePitchTrackGenerator.STAGE_FINISH) return getString(R.string.player_karaoke_track_pitch_stage_finish);
        return getString(R.string.player_karaoke_track_pitch_stage_prepare);
    }

    private String formatKaraokePitchRemaining(long remainingMs) {
        if (remainingMs <= 0) return getString(R.string.player_karaoke_track_pitch_remaining_unknown);
        long seconds = Math.max(1, Math.round(remainingMs / 1000.0));
        if (seconds < 60) return getString(R.string.player_karaoke_track_pitch_remaining_seconds, seconds);
        return getString(R.string.player_karaoke_track_pitch_remaining_minutes, seconds / 60, seconds % 60);
    }

    private void dismissKaraokePitchProgress() {
        if (mKaraokePitchDialog != null) {
            try {
                if (mKaraokePitchDialog.isShowing()) mKaraokePitchDialog.dismiss();
            } catch (Exception ignored) {
            }
        }
        mKaraokePitchDialog = null;
        mKaraokePitchProgress = null;
        mKaraokePitchMessage = null;
    }

    private void cancelKaraokePitchGeneration(boolean notify) {
        AtomicBoolean cancel = mKaraokePitchCancel;
        if (cancel != null) cancel.set(true);
        Future<?> future = mKaraokePitchFuture;
        if (future != null) future.cancel(true);
        mKaraokePitchFuture = null;
        mKaraokePitchCancel = null;
        dismissKaraokePitchProgress();
        if (notify) Notify.show(R.string.player_karaoke_track_generation_stopped);
    }

    private void showKaraokePitchResult(int title, String message) {
        if (isFinishing() || isDestroyed()) {
            Notify.show(message);
            return;
        }
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(title)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextView text = createAudioSheetText(message, 15, false);
        text.setTextColor(0xD9FFFFFF);
        text.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(text, audioSheetTopParams(12, 58));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.addView(createAudioSheetButton(getString(R.string.dialog_positive), true, dialog::dismiss), audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(12, 44));
        dialog.setContentView(root);
        showCompactPlaybackSheet(dialog);
    }

    private void showKaraokeTrackResults(List<KaraokeTrackRepository.SearchResult> results) {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_karaoke_track_select)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        for (int i = 0; i < results.size(); i++) {
            KaraokeTrackRepository.SearchResult result = results.get(i);
            String source = result.getSource() + (result.isLoginRequired() ? getString(R.string.player_karaoke_track_source_login) : "");
            String label = getString(R.string.player_karaoke_track_result_item, source, result.getArtist(), result.getTitle(), result.getNote());
            content.addView(createKaraokeTrackResultItem(label, () -> {
                dialog.dismiss();
                importKaraokeTrackUrl(result.getUrl());
            }), audioSheetTopParams(i == 0 ? 8 : 6, 76));
        }
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, karaokeTrackResultSheetHeight(results.size())));
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

    private void importKaraokeTrackUrl(String url) {
        if (service() == null || TextUtils.isEmpty(url)) return;
        KaraokeTrackRepository.importUrl(player(), url, this::onKaraokeTrackImported);
    }

    private void onKaraokeTrackImported(KaraokeTrackRepository.ImportResult result) {
        if (result != null && result.isSuccess()) {
            Notify.show(R.string.player_karaoke_track_imported);
            applyKaraokeTrackChange(true);
        } else {
            String error = result == null ? "" : result.getError();
            Notify.show(getString(R.string.player_karaoke_track_import_failed) + (TextUtils.isEmpty(error) ? "" : "\n" + error));
        }
    }

    private void clearKaraokeTrackBinding() {
        if (service() == null) return;
        boolean cleared = KaraokeTrackRepository.clearBinding(player());
        Notify.show(cleared ? R.string.player_karaoke_track_cleared : R.string.player_karaoke_track_none);
        applyKaraokeTrackChange(false);
    }

    private void setKaraokeActionState() {
        if (mBinding.control.action.karaoke != null) {
            mBinding.control.action.karaoke.setSelected(PlayerSetting.isKaraokeMode());
            mBinding.control.action.karaoke.setVisibility(View.GONE);
        }
        if (mBinding.audioKaraokeAction != null) mBinding.audioKaraokeAction.setSelected(PlayerSetting.isKaraokeMode());
        applyActionButtonVisibility();
    }

    private void applyKaraokeTrackChange(boolean enableMode) {
        if (enableMode && !PlayerSetting.isKaraokeMode()) {
            PlayerSetting.putKaraokeMode(true);
            setKaraokeActionState();
        }
        refreshLyrics();
        reloadKaraokeTrack();
    }

    private void reloadKaraokeTrack() {
        if (mKaraoke == null || service() == null) return;
        updateAudioOnlyState();
        mKaraoke.reload(this, player(), isAudioOnly() || isMusicLike());
    }

    private boolean showKaraokeResultIfNeeded(int action) {
        if (mKaraoke == null || !mKaraoke.isActive() || mKaraokeResultShown || isFinishing() || isDestroyed()) return false;
        KaraokeResult result = mKaraoke.getResult();
        if (result == null) return false;
        mKaraokeResultShown = true;
        mPendingKaraokeResult = result;
        mKaraokeResultAction = action;
        if (mViewModel != null) mViewModel.setKaraokeResult(result, action);
        SpiderDebug.log("karaoke-result", "show action=%d", action);
        showKaraokeResultDialog(result, action);
        return true;
    }

    private void showKaraokeResultDialog(KaraokeResult result, int action) {
        if (result == null || isFinishing() || isDestroyed()) return;
        if (mKaraokeResultDialog != null && mKaraokeResultDialog.isShowing()) return;
        KaraokeResultView view = new KaraokeResultView(this).setResult(result);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog).setView(view).create();
        view.setAction(() -> {
            dialog.dismiss();
            completeKaraokeResult(action);
        });
        dialog.setOnCancelListener(d -> {
            if (!isChangingConfigurations() && !mSuppressKaraokeResultAction) completeKaraokeResult(action);
        });
        dialog.setOnDismissListener(d -> {
            if (mKaraokeResultDialog == dialog) mKaraokeResultDialog = null;
        });
        mKaraokeResultDialog = dialog;
        configureKaraokeResultDialog(dialog, view);
        dialog.show();
    }

    private void configureKaraokeResultDialog(AlertDialog dialog, KaraokeResultView view) {
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
                WindowManager.LayoutParams params = window.getAttributes();
                if (isLandscapeAudioSheet()) {
                    params.dimAmount = 0f;
                    params.gravity = Gravity.CENTER;
                    params.x = 0;
                    params.y = 0;
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setAttributes(params);
                    window.setLayout(view.getPreferredDialogWidth(), WindowManager.LayoutParams.WRAP_CONTENT);
                } else {
                    params.dimAmount = 0.62f;
                    params.gravity = Gravity.CENTER;
                    params.y = isLand() ? -ResUtil.dp2px(12) : 0;
                    window.setAttributes(params);
                    window.setLayout(view.getPreferredDialogWidth(), WindowManager.LayoutParams.WRAP_CONTENT);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            }
            view.requestActionFocus();
        });
    }

    private void completeKaraokeResult(int action) {
        mPendingKaraokeResult = null;
        mKaraokeResultAction = KARAOKE_RESULT_ACTION_NONE;
        if (mViewModel != null) mViewModel.clearKaraokeResult();
        switch (action) {
            case KARAOKE_RESULT_ACTION_NEXT -> {
                if (!playNextAudioPlaylistEntry()) checkNext(true);
            }
            case KARAOKE_RESULT_ACTION_NEXT_SILENT -> {
                if (!playNextAudioPlaylistEntry()) checkNext(false);
            }
            case KARAOKE_RESULT_ACTION_FINISH -> finishVideoPlaybackNow();
            case KARAOKE_RESULT_ACTION_SYSTEM_BACK -> finishVideoPlaybackFromSystemBack();
            default -> {
            }
        }
    }

    @Override
    public void onCodecCapabilityPanel() {
        CodecCapabilityDialog.show(this, player());
    }

    @Override
    public void onPlayParamsPanel() {
        onPlayParams();
    }

    @Override
    public ActivityVideoBinding getControlBinding() {
        return mBinding;
    }

    @Override
    public PlayerManager getControlPlayer() {
        return service() == null ? null : player();
    }

    @Override
    public History getControlHistory() {
        return mHistory;
    }

    @Override
    public boolean isControlParseEnabled() {
        return isUseParse();
    }

    @Override
    public boolean isControlAudioContent() {
        return isAudioOnly() || isMusicLike();
    }

    @Override
    public boolean isDanmakuFullscreen() {
        return isFullscreen();
    }

    private void onDanmakuShow() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        checkDanmakuImg();
        showDanmaku();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void onScale() {
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else showResizeModeDialog(getScale(), this::setScale);
        setR1Callback();
    }

    private void onLut() {
        if (hasLutQuick()) {
            mBinding.lutQuick.toggle(player(), mBinding.exo, this::onLutChanged, new com.fongmi.android.tv.ui.custom.LutQuickPanel.ImportCallback() {
                @Override
                public void onImportLut() {
                    onLutImport();
                }

                @Override
                public void onSelectLutDir() {
                    onLutDir();
                }
            });
        }
        else LutPanelDialog.create().player(player()).show(this);
        setR1Callback();
    }

    @Override
    public void onLutPanel() {
        if (isFullscreen() && hasLutQuick()) onLut();
        else LutPanelDialog.create().player(player()).show(this);
    }

    private boolean hasLutQuick() {
        return mBinding.lutQuick != null;
    }

    private void onLutChanged() {
        setLut();
    }

    @Override
    public void onLutImport() {
        if (!LutStore.hasUserDir()) {
            pendingLutImport = true;
            chooseLutDir();
            return;
        }
        chooseLutFile();
    }

    @Override
    public void onLutDir() {
        pendingLutImport = false;
        chooseLutDir();
    }

    private void chooseLutFile() {
        skipPausePiP = true;
        FileChooser.from(mLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseLutDir() {
        skipPausePiP = true;
        FileChooser.from(mLutDir).showDirectory();
    }

    @Override
    public void onLutSelected(LutPreset preset) {
        if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "activity select preset=%s enabledBefore=%s current=%s", preset == null ? "original" : preset.getId(), LutSetting.isEnabled(), LutSetting.getPresetId());
        if (!player().selectLut(preset, preset != null)) return;
        setLut();
        setR1Callback();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        saveUserSpeed();
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed(getPlaybackSpeed()));
        saveUserSpeed();
        setR1Callback();
        return true;
    }

    private void saveUserSpeed() {
        mHistory.setUserSpeed(player().getSpeed());
    }

    private void onReset() {
        if (isReplay()) onReplay();
        else onRefresh();
    }

    private void onReplay() {
        mHistory.setPosition(C.TIME_UNSET);
        if (player().isEmpty()) onRefresh();
        else player().setMediaItem();
    }

    private void onRefresh() {
        saveHistory();
        if (mViewModel != null) mViewModel.cancelPlayerContent();
        invalidatePlayerContent();
        subtitlePlaybackSession.stop(this);
        player().stop();
        player().clear();
        mClock.setCallback(null);
        if (mFlagAdapter.isEmpty()) return;
        if (mEpisodeAdapter.isEmpty()) return;
        getPlayer(getFlag(), getEpisode());
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onDecode() {
        if (refreshAndSwitchDecode()) return;
        mClock.setCallback(null);
        player().toggleDecode();
        setR1Callback();
        setDecode();
    }

    private boolean refreshAndSwitchDecode() {
        if (decodeSwitchRefreshing) return true;
        if (getFlag() == null || getEpisode() == null) return false;
        long position = player().getPosition();
        float speed = player().getSpeed();
        boolean repeat = player().isRepeatOne();
        String key = getKey();
        String flag = getFlag().getFlag();
        String episode = getEpisode().getUrl();
        MediaMetadata metadata = buildMetadata();
        int requestId = ++decodeSwitchRequestId;
        int generation = beginPlayerContentSwitch(requestId, key, flag, episode);
        decodeSwitchRefreshing = true;
        setNextDecodeText();
        setDecodeSwitchPending(true);
        mClock.setCallback(null);
        SpiderDebug.log("video-flow", "switch decode refresh start key=%s flag=%s episode=%s", key, flag, episode);
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episode);
                App.post(() -> switchDecodeWithResult(requestId, generation, key, flag, episode, result, position, speed, repeat, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    if (!isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;
                    decodeSwitchRefreshing = false;
                    setDecodeSwitchPending(false);
                    setDecode();
                    Notify.show(e.getMessage());
                });
            }
        });
        return true;
    }

    private void switchDecodeWithResult(int requestId, int generation, String key, String flag, String episode,
                                        Result result, long position, float speed, boolean repeat, MediaMetadata metadata) {
        if (requestId != decodeSwitchRequestId
                || !isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;
        decodeSwitchRefreshing = false;
        setDecodeSwitchPending(false);
        if (!canApplyPlayerContentRequest(requestId, generation, key, flag, episode)) return;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            player().toggleDecode();
        } else {
            player().switchDecode(result, activePlaybackKey(), metadata, isUseParse(), position, speed, repeat);
        }
        setR1Callback();
        setDecode();
    }

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
        setR1Callback();
    }

    private boolean onEndingReset() {
        setR1Callback();
        mIntroSkipPlayback.suppressDetected(false);
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        setOpeningEndingText();
    }

    /**
     * 刷新片头/片尾按钮文案。手设值优先，其次显示自动探测值（带 ~ 前缀区分，不落库）。
     */
    private void setOpeningEndingText() {
        if (mBinding == null || mHistory == null) return;
        mBinding.control.action.opening.setText(openingLabel());
        mBinding.control.action.ending.setText(endingLabel());
    }

    private String openingLabel() {
        if (mHistory.getOpening() > 0) return Util.timeMs(mHistory.getOpening());
        long detected = detectedIntroSkipValue(true);
        return detected > 0 ? getString(R.string.intro_skip_detected_value, Util.timeMs(detected)) : getString(R.string.play_op);
    }

    private String endingLabel() {
        if (mHistory.getEnding() > 0) return Util.timeMs(mHistory.getEnding());
        long detected = detectedIntroSkipValue(false);
        return detected > 0 ? getString(R.string.intro_skip_detected_value, Util.timeMs(detected)) : getString(R.string.play_ed);
    }

    /** 关掉自动跳过时不显示探测值——那种情况下这个数字不会导致任何动作，显示出来是误导。 */
    private long detectedIntroSkipValue(boolean opening) {
        // isReleased 必查：服务已 release 但 Activity 还握着 mService 的窗口里，
        // PlayerManager.getDuration() 会直接对空的 player 取值抛 NPE
        if (!Setting.isIntroSkipEnabled() || player() == null || player().isReleased()) return -1;
        return opening ? mIntroSkipPlayback.getDetectedOpeningMs() : mIntroSkipPlayback.getDetectedEndingMs(player().getDuration());
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        // 长按清空是「这里不要有值」，别紧接着又把探测值渲染上去，看起来像没清掉
        mIntroSkipPlayback.suppressDetected(true);
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        setOpeningEndingText();
    }

    private void onEpisodes() {
        if (mFlagAdapter == null || mFlagAdapter.isEmpty() || mHistory == null) return;
        Flag flag = getFlag();
        syncSelectedEpisode(flag);
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).reverse(mHistory.isRevSort())
                .tmdbCard(shouldUseTmdbEpisodeCards(flag.getEpisodes()))
                .fallbackStill(getEpisodeFallbackStillUrl())
                .seasons(getEpisodeDialogSeasons(), getEpisodeDialogSeasonCounts()).show(this);
    }

    private List<Integer> getEpisodeDialogSeasons() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return List.of();
        return new ArrayList<>(getEpisodeDialogSeasonCounts().keySet());
    }

    private Map<Integer, Integer> getEpisodeDialogSeasonCounts() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return Map.of();
        return mTmdbUIAdapter.getSeasonEpisodeCounts();
    }

    private void onChoose() {
        String[] kernel = PlayerKernelDialog.kernels(getResources());
        String[] items = new String[kernel.length + 1];
        System.arraycopy(kernel, 0, items, 0, kernel.length);
        items[kernel.length] = getString(R.string.player_kernel_external);
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setItems(items, (dialog, index) -> {
            if (index < kernel.length) {
                int which = PlayerSetting.kernelAt(index);
                if (!refreshAndSwitchPlayerKernel(which)) {
                    clearLyrics();
                    player().switchPlayerManually(which);
                    setPlayer();
                    setDecode();
                    rememberPlayerKernel(which);
                }
            } else {
                playerKernelSwitchRequestId++;
                PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.control.title.getText());
                setRedirect(true);
            }
        }).show();
    }

    private void onPlayerKernel() {
        mClock.setCallback(null);
        onChoose();
        setR1Callback();
    }

    private boolean onPlayerKernelLong() {
        onPlayerKernel();
        return true;
    }

    private boolean refreshAndSwitchPlayerKernel(int type) {
        int requestId = ++playerKernelSwitchRequestId;
        Flag currentFlag = getFlag();
        Episode currentEpisode = getEpisode();
        if (currentFlag == null || currentEpisode == null || TextUtils.isEmpty(currentFlag.getFlag()) || TextUtils.isEmpty(currentEpisode.getUrl())) return false;
        int nextType = PlayerSetting.sanitizePlayer(type);
        long position = getPlayerSwitchPosition();
        float speed = player().getSpeed();
        boolean repeat = player().isRepeatOne();
        String key = getKey();
        String flag = currentFlag.getFlag();
        String episode = currentEpisode.getUrl();
        MediaMetadata metadata = buildMetadata();
        int generation = beginPlayerContentSwitch(requestId, key, flag, episode);
        mClock.setCallback(null);
        SpiderDebug.log("video-flow", "switch player refresh start type=%d key=%s flag=%s episode=%s", nextType, key, flag, episode);
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episode, nextType);
                App.post(() -> switchPlayerKernelWithResult(requestId, generation, key, flag, episode, nextType, result, position, speed, repeat, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    if (requestId != playerKernelSwitchRequestId
                            || !isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;
                    setPlayerKernel();
                    setDecode();
                    setR1Callback();
                    Notify.show(e.getMessage());
                });
            }
        });
        return true;
    }

    private void switchPlayerKernelWithResult(int requestId, int generation, String key, String flag, String episode,
                                              int type, Result result, long position, float speed, boolean repeat, MediaMetadata metadata) {
        if (requestId != playerKernelSwitchRequestId
                || !isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;
        if (!canApplyPlayerContentRequest(requestId, generation, key, flag, episode)) return;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            Notify.show(result != null && result.hasMsg() ? result.getMsg() : getString(R.string.error_play_url));
        } else {
            player().switchPlayer(type, result, activePlaybackKey(), metadata, isUseParse(), position, speed, repeat);
            rememberPlayerKernel(type);
        }
        setPlayerKernel();
        setDecode();
        setR1Callback();
    }

    private boolean onTextLong() {
        if (!player().haveTrack(C.TRACK_TYPE_TEXT)) return false;
        onSubtitleClick();
        return true;
    }

    private boolean onPlayerControlTouch(View view, MotionEvent event) {
        setR1Callback();
        return mKeyDown.onTouchEvent(event);
    }

    private boolean onActionTouch(View v, MotionEvent e) {
        setR1Callback();
        return false;
    }

    private void onSwipeRefresh() {
        if (mBinding.progressLayout.isEmpty()) getDetail();
        else onRefresh();
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        if (isFullscreen()) return;
        if (service() == null) {
            SpiderDebug.log("video-flow", "fullscreen enter deferred reason=player-not-ready");
            return;
        }
        PlayerManager current = player();
        if (current == null) return;
        logVideoFrame("enterFullscreen before");
        setFullscreen(true);
        if (isLand() && !current.isPortrait()) setTransition();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.video.bringToFront();
        setRequestedOrientation(PlaybackOrientation.getEnterFullscreenOrientation(current.isPortrait()));
        mBinding.control.title.setVisibility(View.VISIBLE);
        setSizeText();
        setRotate(current.isPortrait());
        mKeyDown.resetScale();
        // 短剧会话退出全屏后再双击/点全屏按钮进来不走 enterShortDramaFullscreen，
        // 这里也要按新形态重算，否则回到全屏时手势还是长视频那套轴向。
        syncShortDramaGesture();
        App.post(mR3, 2000);
        hideControl();
        logVideoFrame("enterFullscreen after");
    }

    private void scheduleFullscreenControlReveal() {
        mBinding.video.post(this::showControlIfFullscreen);
        mBinding.video.postDelayed(this::showControlIfFullscreen, 300);
    }

    private void showControlIfFullscreen() {
        if (!isFullscreen() || isLock() || isInPictureInPictureMode()) return;
        showControl();
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        PlayerManager current = player();
        if (service() == null) {
            SpiderDebug.log("video-flow", "fullscreen exit deferred reason=player-not-ready");
            return;
        }
        if (current == null) return;
        logVideoFrame("exitFullscreen before");
        setFullscreen(false);
        if (current != null && isLand() && !current.isPortrait()) setTransition();
        setRequestedOrientation(PlaybackOrientation.getExitFullscreenOrientation(isPort()));
        mBinding.episodeGroup.postDelayed(() -> scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition()), 100);
        mBinding.episode.postDelayed(this::scrollEpisodeToSelected, 100);
        mBinding.control.title.setVisibility(View.INVISIBLE);
        setSizeText();
        mBinding.video.setLayoutParams(mFrameParams);
        restoreEmbeddedVideoLayoutAfterFullscreen();
        mKeyDown.resetScale();
        // 退出全屏就交还短剧手势：内嵌小窗上竖滑要留给详情页滚动，不能再切集。
        syncShortDramaGesture();
        App.post(mR3, 2000);
        setRotate(false);
        hideControl();
        logVideoFrame("exitFullscreen after");
    }

    private void restoreEmbeddedVideoLayoutAfterFullscreen() {
        mBinding.video.forceLayout();
        mBinding.video.requestLayout();
        mBinding.exo.forceLayout();
        mBinding.exo.requestLayout();
        mBinding.scroll.forceLayout();
        mBinding.scroll.requestLayout();
        mBinding.progressLayout.requestLayout();
        mBinding.video.post(() -> {
            mBinding.video.setLayoutParams(mFrameParams);
            mBinding.video.requestLayout();
            mBinding.exo.requestLayout();
            mBinding.scroll.requestLayout();
        });
        mBinding.progressLayout.postDelayed(() -> {
            mBinding.video.setLayoutParams(mFrameParams);
            mBinding.video.requestLayout();
            mBinding.exo.requestLayout();
            mBinding.scroll.requestLayout();
        }, 180);
        mBinding.episode.post(this::refreshEpisodeLayoutAfterFullscreen);
        mBinding.episode.postDelayed(this::refreshEpisodeLayoutAfterFullscreen, 180);
    }

    private void refreshEpisodeLayoutAfterFullscreen() {
        // Detail data can bind holders against the temporary fullscreen width; rebind after rotation settles.
        if (isFullscreen() || mEpisodeAdapter == null || mEpisodeAdapter.isEmpty()) return;
        updateEpisodeLayout(mEpisodeAdapter.getItems());
        mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());
        mBinding.episode.requestLayout();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void setTransition() {
        PlayerManager current = player();
        if (!shouldAnimateVideoFrameTransition(current)) {
            Log.d(SIZE_TAG, "video transition skipped native player=" + current.getPlayerText());
            return;
        }
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(150);
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        TransitionManager.beginDelayedTransition(parent, transition);
    }

    private boolean shouldAnimateVideoFrameTransition(PlayerManager current) {
        return current == null || !current.isNativePlayer();
    }

    private int getLockOrient() {
        return PlaybackOrientation.getLockOrientation(this, isLock(), isRotate(), isPort() && isAutoRotate());
    }

    private void showProgress() {
        if (mAudioStageVisible) {
            hideProgress();
            hideError();
            return;
        }

        if (mSeekProgressFallback != null) App.removeCallbacks(mSeekProgressFallback);
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        if (mVod == null && shouldLoadTmdbDetail() && !mTmdbContentLoaded && !shouldRevealShellWhileLoading()) mBinding.progressLayout.showProgress();
        else if (mVod != null && !mBinding.progressLayout.isContent()) mBinding.progressLayout.showContent();
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        if (mSeekProgressFallback != null) App.removeCallbacks(mSeekProgressFallback);
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset(mBinding.progress.traffic);
    }

    private void showDetailContent() {
        if (!canRevealPlaybackContent()) return;
        View child = mBinding.scroll.getChildAt(0);
        if (child != null) child.setVisibility(View.VISIBLE);
        if (!mBinding.progressLayout.isContent()) mBinding.progressLayout.showContent();
    }

    private void showPlaybackContent() {
        hideProgress();
        showDetailContent();
    }

    private void onTmdbContentReady() {
        android.util.Log.d("VideoActivity", "TMDB 内容加载完成");
        cancelTmdbDetailFallback();
        if (shouldUseTmdbBackdropSurface() && mTmdbHeaderView != null) {
            mTmdbHeaderView.hideNativeHeroBackdrop();
        }
        mTmdbContentLoaded = true;
        mBinding.name.setVisibility(View.GONE);
        if (mVod != null) setText(mVod);
        // 选集列表在 TMDB 就绪前就已构建，那时 mTmdbUIAdapter.isLoaded() 为 false，
        // 兜底图取不到海报；此处重算，否则无 TMDB 数据的集会一直是无图卡片。
        updateEpisodeFallbackStillUrl();
        showDetailContent();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.error.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.error.setText("");
    }

    private void showDanmaku() {
        player().setDanmakuEnabled(DanmakuSetting.isShow());
    }

    private void hideDanmaku() {
        player().setDanmakuEnabled(false);
    }

    private void refreshDanmakuControls() {
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.adFeedback.setVisibility(isAdFeedbackEnabled() ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
        // 顶部弹幕图标只根据锁定状态和弹幕可用性显示。
        if (mBinding.control.getRoot().getVisibility() == View.VISIBLE) mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() ? View.GONE : View.VISIBLE);
    }

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        if (mAudioStageVisible && !isFullscreen()) {
            hideWidgetOverlay();
            hideControl();
            return;
        }
        setTrackVisible();
        setOsdSuppressed(true);
        boolean shortDrama = isShortDramaSession();
        boolean showPiP = canShowPiP(shortDrama);
        hideWidgetOverlay();
        // 顶部弹幕图标只根据锁定状态和弹幕可用性显示。
        mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(mHistory == null || (isFullscreen() && !shortDrama) ? View.GONE : View.VISIBLE);
        mBinding.control.right.getRoot().setVisibility(isFullscreen() || showPiP ? View.VISIBLE : View.GONE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.right.pip.setVisibility(showPiP ? View.VISIBLE : View.GONE);
        // 进度条旁的全屏按钮只根据锁定状态和短剧模式显示。
        mBinding.control.fullscreen.setVisibility(isLock() || shortDrama ? View.GONE : View.VISIBLE);
        mBinding.control.keep.setVisibility(mHistory == null ? View.GONE : View.VISIBLE);
        mBinding.control.nightMode.setVisibility(mHistory == null ? View.GONE : View.VISIBLE);
        boolean showPlayParams = PlayerButtonSetting.isVisible(PlayerButtonSetting.PLAY_PARAMS);
        mBinding.control.action.playParams.setVisibility(showPlayParams ? View.VISIBLE : View.GONE);
        mBinding.control.action.playParams.setSelected(mOsd != null && mOsd.isDiagnosticsVisible());
        mBinding.control.osdDiagnostics.setVisibility(PlayerSetting.isOsdDiagnostics() && showPlayParams && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.osdDiagnostics.setAlpha(mOsd != null && mOsd.isDiagnosticsVisible() ? 1f : 0.72f);
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() && PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE) ? View.VISIBLE : View.GONE);
        // 竖屏模式下隐藏底部控制栏（EXO、硬解等选项），避免界面拥挤。
        // 判定去耦：以视频方向（player().isPortrait()，即 App 期望的全屏方向）为主判据，
        // 不再依赖系统 Configuration 何时真正旋转完成——否则慢机型会在 300ms 自动唤出时踩空、
        // 锁定方向的机型会永久看不到控制台。ResUtil.isLand 作为额外兜底（竖屏视频被强制横屏时）。
        boolean isLandscapeFullscreen = isFullscreen() && (!player().isPortrait() || ResUtil.isLand(this));
        mBinding.control.action.getRoot().setVisibility(isLandscapeFullscreen || isFusionPlayerActionsDocked() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        // 顶部投屏图标只根据全屏和播放状态显示。
        mBinding.control.cast.setVisibility(isFullscreen() && mHistory != null && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        syncShortDramaControlLayout(shortDrama);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        if (mOsd != null) mOsd.setControlsVisible(true);
        checkFullscreenImg();
        mBinding.control.getRoot().post(() -> PlayerControlFocusHelper.ensureFocus(mBinding.control.getRoot(), mBinding.control.play));
        setR1Callback();
    }

    private boolean canShowPiP(boolean shortDrama) {
        return !shortDrama && !isFullscreen() && !isLock() && !player().isEmpty() && player().haveTrack(C.TRACK_TYPE_VIDEO) && !PiP.noPiP();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(false);
        App.removeCallbacks(mR1);
        setOsdSuppressed(false);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (isVisible(mBinding.control.getRoot()) && PlayerControlFocusHelper.handleKey(mBinding.control.getRoot(), mBinding.control.play, event)) return true;
        return super.dispatchKeyEvent(event);
    }

    private void setOsdSuppressed(boolean suppressed) {
        if (mOsd != null) mOsd.setSuppressed(suppressed);
    }

    private void onOsdDiagnostics() {
        if (mOsd == null) return;
        mOsd.toggleDiagnostics();
        hideControl();
    }

    private void onMultiThreadProxy() {
        MultiThreadProxyDialog.show(this, player().getUrl(), this::onMultiThreadProxySaved);
    }

    private void onMultiThreadProxySaved(boolean applyNow) {
        setPlayParamsState();
        if (applyNow && player() != null && !player().isEmpty()) player().reloadCurrentMediaItem();
    }

    private void onPlayParams() {
        if (mOsd == null) return;
        boolean visible = !mOsd.isDiagnosticsVisible();
        PlayerSetting.putOsdDiagnostics(visible);
        mOsd.setDiagnosticsVisible(visible);
        setPlayParamsState();
        hideControl();
    }

    private void setPlayParamsState() {
        boolean selected = mOsd != null && mOsd.isDiagnosticsVisible();
        mBinding.control.action.playParams.setSelected(selected);
        mBinding.control.action.multiThreadProxy.setSelected(MultiThreadProxySetting.get().enabled());
    }

    private void hideWidgetOverlay() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    private void hideSheet() {
        getSupportFragmentManager().getFragments().stream().filter(fragment -> fragment instanceof BottomSheetDialogFragment).map(fragment -> (BottomSheetDialogFragment) fragment).forEach(BottomSheetDialogFragment::dismiss);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic, service() == null ? null : player());
        hidePlaybackProgressIfStale();
        App.post(mR2, 1000);
    }

    private void setOrient() {
        if (isPort() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getPortAutoRotateOrientation());
        if (isLand() && isAutoRotate()) setRequestedOrientation(PlaybackOrientation.getLandAutoRotateOrientation());
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setArtwork(String url) {
        if (mHistory != null) mHistory.setVodPic(url);
        loadArtwork(url);
        setContextWall(getContextWall());
        updateEpisodeFallbackStillUrl();
    }

    private void setArtwork() {
        if (mHistory == null) return;
        setArtwork(mHistory.getVodPic());
    }

    private void loadArtwork(String url) {
        loadArtwork(url, mPlaybackEpisodeKey);
    }

    private void loadArtwork(String url, String owner) {
        String requestUrl = Objects.toString(url, "");
        String requestOwner = Objects.toString(owner, "");
        mArtworkRequestUrl = requestUrl;
        mArtworkRequestOwner = requestOwner;
        if (TextUtils.isEmpty(requestUrl)) {
            mBinding.exo.setDefaultArtwork(null);
            mBinding.audioCover.setImageResource(R.drawable.artwork);
            updateAudioArtworkColor(null);
            return;
        }
        mBinding.audioCover.setImageResource(R.drawable.artwork);
        ImgUtil.load(this, requestUrl, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (isFinishing() || isDestroyed()) return;
                if (!isCurrentArtworkRequest(requestUrl, requestOwner)) return;
                mBinding.exo.setDefaultArtwork(resource);
                mBinding.audioCover.setImageDrawable(resource);
                updateAudioArtworkColor(resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (isFinishing() || isDestroyed()) return;
                if (!isCurrentArtworkRequest(requestUrl, requestOwner)) return;
                mBinding.exo.setDefaultArtwork(errorDrawable);
                if (errorDrawable == null) mBinding.audioCover.setImageResource(R.drawable.artwork);
                else mBinding.audioCover.setImageDrawable(errorDrawable);
                updateAudioArtworkColor(errorDrawable);
            }
        });
    }

    private String getContextWall() {
        if (!TextUtils.isEmpty(getWallPic())) return getWallPic();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getWallPic())) return mHistory.getWallPic();
        if (mVod != null && !TextUtils.isEmpty(mVod.getPic())) return mVod.getPic();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodPic())) return mHistory.getVodPic();
        return getPic();
    }

    private String lockContextWall(String url) {
        String wall = Objects.toString(url, "");
        if (mContextWallLockedUrl == null && !TextUtils.isEmpty(wall)) mContextWallLockedUrl = wall;
        return mContextWallLockedUrl == null ? wall : mContextWallLockedUrl;
    }

    private void setContextWall(String url) {
        setContextWall(url, false);
    }

    private void setContextWall(String url, boolean skipLock) {
        if (!Setting.isPlaybackArtworkWall() && !Setting.isFusionDetailPage() && !shouldUseTmdbBackdropSurface()) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        // 轮播场景（原生增强/Fusion 的 backdrop 变化）需要跳过锁定，否则永远只显示第一张
        String wall = skipLock ? Objects.toString(url, "") : lockContextWall(url);
        if (TextUtils.isEmpty(wall)) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        if (Objects.equals(mContextWallUrl, wall)) {
            android.util.Log.d("VideoActivity", "setContextWall: URL 相同，跳过（wall=" + wall + ")");
            return;
        }
        android.util.Log.d("VideoActivity", "setContextWall: 切换背景 wall=" + wall);
        mContextWallUrl = wall;
        resetContextWallAlpha();
        if (isGone(mBinding.contextWall)) {
            mBinding.contextWall.setBackgroundColor(0xFF000000);
            mBinding.contextWall.setVisibility(View.VISIBLE);
        }
        ImgUtil.load(this, wall, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                resetContextWallAlpha();
                mBinding.contextWall.setBackgroundColor(0x00000000);
                mBinding.contextWall.setImageDrawable(resource);
                mBinding.contextWall.setVisibility(View.VISIBLE);
                android.util.Log.d("VideoActivity", "setContextWall: 图片加载完成");
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                mContextWallUrl = "";
                hideContextWall();
                android.util.Log.w("VideoActivity", "setContextWall: 图片加载失败");
            }
        });
    }

    private void resetContextWallAlpha() {
        mBinding.contextWall.animate().cancel();
        mBinding.contextWall.setAlpha(1f);
    }

    private void restoreContextWall() {
        if (!Setting.isPlaybackArtworkWall() && !Setting.isFusionDetailPage() && !shouldUseTmdbBackdropSurface()) return;
        String wall = getContextWall();
        if (TextUtils.isEmpty(wall)) {
            hideContextWall();
        } else if (Objects.equals(mContextWallUrl, wall) && mBinding.contextWall.getDrawable() != null) {
            resetContextWallAlpha();
            mBinding.contextWall.setBackgroundColor(Color.TRANSPARENT);
            mBinding.contextWall.setVisibility(View.VISIBLE);
        } else {
            mContextWallUrl = "";
            setContextWall(wall);
        }
    }

    private void hideContextWall() {
        resetContextWallAlpha();
        mBinding.contextWall.setImageDrawable(null);
        mBinding.contextWall.setBackgroundColor(0x00000000);
        mBinding.contextWall.setVisibility(View.GONE);
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        boolean preservePlayback = mRestoringConfigurationPlayback && service() != null && !player().isEmpty();
        if (empty) {
            if (!preservePlayback) startFlow();
        } else if (preservePlayback) {
            restoreFlagSelectionWithoutPlayback();
        } else if (mImmersiveAudioResolved != null) {
            applyImmersiveAudioSelection(mImmersiveAudioResolved);
            if (mHistory.isRevSort()) reverseEpisode(true);
        } else {
            onItemClick(resolveHistoryPlaybackFlag(item.getFlags()));
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
        if (preservePlayback) SpiderDebug.log("karaoke-result", "configuration restore preserved playback key=%s episode=%s", player().getKey(), mHistory.getVodRemarks());
        mRestoringConfigurationPlayback = false;
    }

    private boolean checkHistory(Vod item) {
        TmdbItem tmdbItem = getHistoryTmdbItem();
        History resumeHistory = getIntentResumeHistory();
        if (hasIntentResumeHistory() && resumeHistory == null) {
            Notify.show(R.string.history_record_missing);
            finish();
            return false;
        }
        mHistory = resumeHistory == null
                ? History.findPlayback(getHistoryKey(), List.of(item.getName(), getName()), item.getFlags(), tmdbItem, currentSourceSeasonNumber(item))
                : resumeHistory.forPlaybackKey(getHistoryKey(), VodConfig.getCid());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getWallPic())) mHistory.setWallPic(getWallPic());
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        applyIntentPlaybackSelection(item);
        if (resumeHistory == null && Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        setOpeningEndingText();
        // 如果历史记录中已有有效倍速，使用历史倍速；否则使用默认播放倍速
        float speed = getPlaybackSpeed();
        mBinding.control.action.speed.setText(player().setSpeed(speed));
        mHistory.setVodName(item.getName());
        mHistory.setVodPic(getInitialArtwork(item));
        enrichHistoryMeta(item);
        PlaybackEventCollector.get().updateHistory(mHistory);
        setArtwork(getInitialArtwork(item));
        setScale(getScale());
        return true;
    }

    /**
     * 补齐历史记录的富集元数据（题材/地区/演员/主创/年份）。
     * 炫彩详情模式优先用 TMDB extra，否则用源站 Vod。仅补空字段，新老记录统一走此路径。
     */
    private void enrichHistoryMeta(Vod item) {
        if (mHistory == null || item == null) return;
        String year = TextUtils.isEmpty(getTmdbVodYear()) ? item.getYear() : getTmdbVodYear();
        String area = TextUtils.isEmpty(getTmdbVodArea()) ? item.getArea() : getTmdbVodArea();
        String type = TextUtils.isEmpty(getTmdbVodType()) ? item.getTypeName() : getTmdbVodType();
        String director = TextUtils.isEmpty(getTmdbVodDirector()) ? item.getDirector() : getTmdbVodDirector();
        String actor = TextUtils.isEmpty(getTmdbVodActor()) ? item.getActor() : getTmdbVodActor();
        mHistory.enrichMeta(type, area, actor, director, year);
    }

    private boolean shouldKeepPushArtwork() {
        return SiteApi.PUSH.equals(getKey()) && !TextUtils.isEmpty(getPic());
    }

    private String getInitialArtwork(Vod item) {
        return shouldKeepPushArtwork() ? getPic() : item.getPic();
    }

    private void applySearchArtwork(Vod item) {
        String pic = getSearchArtworkPic();
        if (!TextUtils.isEmpty(pic)) item.setPic(pic);
    }

    private String getSearchArtworkPic() {
        if (!TextUtils.isEmpty(getPic())) return getPic();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodPic())) return mHistory.getVodPic();
        return "";
    }

    private void updateEpisodeFallbackStillUrl() {
        if (mEpisodeAdapter != null) mEpisodeAdapter.setFallbackStillUrl(getEpisodeFallbackStillUrl());
    }

    private String getEpisodeFallbackStillUrl() {
        // 分集无专属剧照时的兜底图按设备比例选：宽屏优先横向 backdrop，窄屏优先纵向海报，
        // 首选比例缺图时退到另一种，避免宽卡片被竖海报撑裂或窄卡片留大片空白。
        return EpisodeCardImagePolicy.fallbackFor(
                getEpisodeFallbackBackdropUrl(), getEpisodeFallbackPosterUrl(), isWideScreen());
    }

    private String getEpisodeFallbackPosterUrl() {
        // getPosterUrl() 内部已带 tmdbItem.getPosterUrl() 兜底。下面几项是进场 intent 的值，
        // 从 TMDB 详情页进来时可能全为空。
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            String poster = mTmdbUIAdapter.getPosterUrl();
            if (!TextUtils.isEmpty(poster)) return poster;
        }
        if (!TextUtils.isEmpty(getPic())) return getPic();
        if (!TextUtils.isEmpty(getTmdbVodPic())) return getTmdbVodPic();
        if (mVod != null && !TextUtils.isEmpty(mVod.getPic())) return mVod.getPic();
        return mHistory == null ? "" : mHistory.getVodPic();
    }

    private boolean isWideScreen() {
        return ResUtil.getScreenWidth(this) >= ResUtil.getScreenHeight(this);
    }

    private String getEpisodeFallbackBackdropUrl() {
        // 有 TMDB 条目时只认它自己的剧照，避免重新匹配后旧横图挡在当前条目的海报前面。
        // 没有 TMDB 条目（纯原生源）才退 intent 的 wallPic：它随 onNewIntent 的 putExtras 跟随
        // 条目刷新，且按约定是横图，宽卡片靠它免吃竖海报。这里不能用 getContextWall()——
        // 那条链混了 mVod.getPic()/mHistory.getVodPic() 等竖海报，与按比例选图的约定相悖。
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return getWallPic();
        java.util.List<String> photos = mTmdbUIAdapter.getPhotos();
        return photos == null || photos.isEmpty() ? "" : photos.get(0);
    }

    private boolean hasInitialPreview() {
        return !getName().isEmpty() || !getPic().isEmpty() || !getWallPic().isEmpty();
    }

    /** TMDB 富集是否仍在进行：决定要不要先填充会被 TMDB 覆盖的站源文本。 */
    private boolean isTmdbDetailEnrichmentPending() {
        return shouldLoadTmdbDetail() && !mTmdbContentLoaded && !mTmdbFallbackToNative;
    }

    private boolean shouldWaitForTmdbDetailReveal() {
        return isTmdbDetailEnrichmentPending() && !shouldRevealShellWhileLoading();
    }

    /**
     * 原生增强把详情与播放放在同一页：进入即揭开页面骨架，加载态只由播放器窗口内那一层表达，
     * 不再让详情区整块转圈与播放器转圈同屏叠出两层「加载中」。
     */
    private boolean shouldRevealShellWhileLoading() {
        return Setting.isOriginalEnhancedDetailPage();
    }

    private boolean canRevealPlaybackContent() {
        return mVod != null || !shouldWaitForTmdbDetailReveal();
    }

    private void showInitialPreview() {
        // 原生增强把详情与播放放在同一页：进入即揭开骨架并填上 intent 已知的标题，
        // 否则 ProgressLayout 停在初始态、随后被 getDetail 的整页转圈盖掉。
        if (shouldRevealShellWhileLoading()) {
            if (!mBinding.progressLayout.isContent()) mBinding.progressLayout.showContent();
            if (!getName().isEmpty()) mBinding.name.setText(getName());
        }
        if (!getPic().isEmpty()) setArtwork(getPic());
        else if (!getWallPic().isEmpty()) setContextWall(getWallPic());
    }

    private History createHistory(Vod item) {
        History history = new History();
        history.setKey(getHistoryKey());
        history.setCid(VodConfig.getCid());
        history.setVodName(item.getName());
        history.setVodPic(getInitialArtwork(item));
        history.setWallPic(getWallPic());
        history.findEpisode(item.getFlags());
        return history;
    }

    private void applyIntentPlaybackSelection(Vod item) {
        String playFlag = getIntentPlaybackFlag();
        String playFlagKey = getIntentPlaybackFlagKey();
        String playName = getIntentPlaybackEpisodeName();
        String playUrl = getIntentPlaybackEpisodeUrl();
        if (TextUtils.isEmpty(playFlag) && TextUtils.isEmpty(playFlagKey)
                && TextUtils.isEmpty(playName) && TextUtils.isEmpty(playUrl)) return;
        Flag flag = findIntentPlaybackFlag(item.getFlags(), playFlagKey, playFlag, playUrl);
        if (flag == null) return;
        Episode episode = findIntentPlaybackEpisode(flag, playName, playUrl);
        Episode historyEpisode = withSourceSeasonEpisodeIdentity(withIntentTmdbEpisodeIdentity(episode));
        // 历史续播、跨源复制或 TMDB 聚合开启时共享标准剧集进度；否则普通显式选集保持原始剧集身份。
        boolean crossSource = mHistory.isCrossSourcePlayback();
        boolean shareEpisodeProgress = crossSource || isResumeFromHistory() || Setting.isHistoryAggregationEffective();
        boolean compatibleFlag = shareEpisodeProgress || TextUtils.equals(mHistory.getVodFlag(), flag.getFlag());
        boolean sameEpisode = episode != null && (shareEpisodeProgress
                ? historyEpisode.matchesPlayback(mHistory.getEpisode())
                : episode.matches(mHistory.getEpisode()));
        if (!compatibleFlag || (episode != null && !sameEpisode)) {
            mHistory.setPosition(C.TIME_UNSET);
            mHistory.setDuration(C.TIME_UNSET);
        }
        setHistoryFlag(flag);
        if (episode == null) return;
        mHistory.setVodRemarks(getHistoryEpisodeName(historyEpisode));
        mHistory.setEpisodeUrl(episode.getUrl());
        if (historyEpisode.getTmdbEpisode() != null || !sameEpisode) mHistory.setTmdbEpisodePosition(historyEpisode);
    }

    private Flag findIntentPlaybackFlag(List<Flag> flags, String playFlagKey, String playFlag, String playUrl) {
        return com.fongmi.android.tv.ui.helper.TmdbUIAdapter.selectPlaybackFlag(
                flags, playFlagKey, playUrl, playFlag);
    }

    private Episode findIntentPlaybackEpisode(Flag flag, String playName, String playUrl) {
        if (flag == null || flag.getEpisodes().isEmpty()) return null;
        if (!TextUtils.isEmpty(playUrl)) {
            for (Episode episode : flag.getEpisodes()) if (TextUtils.equals(playUrl, episode.getUrl())) return episode;
        }
        return TextUtils.isEmpty(playName) ? null : flag.find(playName, true);
    }

    private void saveHistory() {
        saveHistory(false);
    }

    private void saveHistory(boolean exit) {
        if (mHistory == null || Setting.isIncognito()) return;
        boolean hasPlayback = service() != null && isOwner() && !player().isEmpty();
        if (hasPlayback) {
            if (!tmdbHistoryResumePending) {
                // 播放位置缓存继续使用源站集名，避免刮削展示名变化后无法恢复。
                String cacheName = getCurrentHistoryEpisodeCacheName();
                if (!TextUtils.isEmpty(cacheName) && !skipEpisodePositionCache()) {
                    EpisodePositionCache.get().put(
                        getKey(),
                        getId(),
                        getFlag().getFlag(),
                        cacheName,
                        player().getPosition(),
                        player().getDuration()
                    );
                }
            }
            updatePlaybackHistoryPosition();
            mHistory.setCreateTime(System.currentTimeMillis());
        }
        if (exit && service() != null) PlaybackEventCollector.get().onStop(player());
        if (!mHistory.canSave() && !hasPlayback) return;
        History history = mHistory.copy();
        Task.execute(() -> {
            history.save();
            // 持久化集数位置缓存
            EpisodePositionCache.get().save();
            if (exit) RefreshEvent.history();
        });
    }

    private void syncHistory() {
        if (mHistory == null || Setting.isIncognito()) return;
        History history = mHistory.copy();
        Task.execute(history::save);
    }

    private void updateHistory(Episode item) {
        // 换线路或源站刷新时同一集的 URL、集名格式可能变化，统一按播放恢复规则识别。
        Episode historyEpisode = withSourceSeasonEpisodeIdentity(item);
        boolean sameEpisode = historyEpisode.matchesPlayback(mHistory.getEpisode());
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), getFlag().getFlag());
        if (!sameEpisode || !sameFlag) mIntroSkipPlayback.reset();
        if ((!sameEpisode || !sameFlag) && service() != null) {
            if (!tmdbHistoryResumePending) {
                // 播放位置缓存继续使用源站集名，History 仅负责展示刮削后的标题。
                String cacheName = getCurrentHistoryEpisodeCacheName();
                if (!TextUtils.isEmpty(cacheName) && !skipEpisodePositionCache()) {
                    EpisodePositionCache.get().put(
                        getKey(),
                        getId(),
                        getFlag().getFlag(),
                        cacheName,
                        player().getPosition(),
                        player().getDuration()
                    );
                }
                updatePlaybackHistoryPosition();
            }
            PlaybackEventCollector.get().onStop(player());
        }

        if (!sameEpisode && !tmdbHistoryResumePending) {
            // 从缓存中恢复新集的播放位置
            EpisodePositionCache.EpisodePosition cached = skipEpisodePositionCache() ? null : EpisodePositionCache.get().get(
                getKey(),
                getId(),
                getFlag().getFlag(),
                episodePositionCacheName(item, currentSourceSeasonNumber())
            );

            if (cached != null) {
                mHistory.setPosition(cached.position);
                mHistory.setDuration(cached.duration);
            } else {
                mHistory.setPosition(C.TIME_UNSET);
                mHistory.setDuration(C.TIME_UNSET);
            }
        }

        setHistoryFlag(getFlag());
        mHistory.setVodRemarks(getHistoryEpisodeName(item));
        mHistory.setEpisodeUrl(item.getUrl());
        if (historyEpisode.getTmdbEpisode() != null || !sameEpisode) mHistory.setTmdbEpisodePosition(historyEpisode);
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkKeepImg() {
        mBinding.control.keep.setImageResource(Keep.find(getHistoryKey()) == null ? R.drawable.ic_control_keep_off : R.drawable.ic_control_keep_on);
        updateTmdbKeepState();
    }

    private void checkLockImg() {
        mBinding.control.right.lock.setImageResource(isLock() ? R.drawable.ic_control_lock_on : R.drawable.ic_control_lock_off);
    }

    private void checkFullscreenImg() {
        mBinding.control.fullscreen.setImageResource(isFullscreen() ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
    }

    private void checkDanmakuImg() {
        mBinding.control.danmaku.setImageResource(DanmakuSetting.isShow() ? R.drawable.ic_control_danmaku_on : R.drawable.ic_control_danmaku_off);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getDisplayName());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        Keep keep = Keep.find(getHistoryKey());
        if (keep != null) {
            keep.setVodName(mHistory.getVodName());
            keep.setVodPic(mHistory.getVodPic());
            keep.save();
        }
    }

    private void updateVod(Vod item) {
        if (mVod != item) mSourceEpisodeSeasonCache.clear();
        mVod = item;
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        boolean keyChanged = false;
        if (id) {
            getIntent().putExtra("id", item.getId());
            if (mHistory != null) {
                String nextKey = getHistoryKey();
                keyChanged = !TextUtils.equals(mHistory.getKey(), nextKey);
                if (keyChanged) mHistory.replace(nextKey);
            }
        }
        boolean historyReloaded = reloadHistoryAfterTmdbMatch();
        if (name) mHistory.setVodName(item.getName());
        if (name) mBinding.name.setText(item.getName());
        // 原生增强：TMDB 富集完成后回写题材/地区/演员/主创到 History（enrichVod 已填充 item，仅补空字段）
        if (mHistory != null) {
            mHistory.enrichMeta(item.getTypeName(), item.getArea(), item.getActor(), item.getDirector(), item.getYear());
        }
        // 跨源聚合：TMDB 匹配完成后把 tmdbId 盖章到 History，供列表去重与跨源续播使用（不依赖脆弱的名称回查缓存）
        boolean tmdbIdStamped = stampHistoryTmdbId();
        updateFlag(getFlag(), item.getFlags());
        if (historyReloaded) resumeHistoryAfterTmdbMatch();
        boolean episodeTitleChanged = refreshCurrentHistoryEpisodeTitle();
        mBinding.control.title.setText(getPlaybackControlTitle());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        // key 迁移后必须写回，避免 replace 删旧 key 后未 save 导致历史消失
        if (keyChanged || pic || name || episodeTitleChanged || tmdbIdStamped) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);

        // TMDB 模式：数据加载完成后填充头部面板
        if (mTmdbHeaderView != null) {
            boolean loaded = mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded();
            android.util.Log.d("VideoActivity", "updateVod - TMDB isLoaded=" + loaded);
            if (loaded) {
                bindLoadedTmdbDetail();
            } else {
                android.util.Log.d("VideoActivity", "TMDB 加载失败，回退到原生详情");
                showNativeDetailFallback(item);
                if (shouldShowAutoTmdbMatchDialog(item)) showManualTmdbMatchDialog();
            }
        }
    }

    private String tmdbEpisodeCompactText() {
        return mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()
                ? "" : mTmdbUIAdapter.getEpisodeCompactText();
    }

    private void bindLoadedTmdbDetail() {
        if (mTmdbHeaderView == null || mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        mTmdbFallbackToNative = false;
        hideNativePersonalRecommendations();
        moveFlagAndEpisodeToTmdb();
        mTmdbHeaderView.bind(mTmdbUIAdapter);
        loadTmdbRelatedVideosForCurrentEpisode();
        styleTmdbSourceInFlagTitle();
        applyTmdbPlaybackControlColors();
        applyFusionPlayerBelowSpacing();
        updateTmdbKeepState();
        requestIntroSkipPlan();
        refreshLyrics();
    }


    private void refreshTmdbRecommendations() {
        if (mTmdbHeaderView == null || mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        mTmdbHeaderView.refreshRecommendations();
    }

    private void refreshTmdbRelatedVideos() {
        if (mTmdbHeaderView == null || mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        mTmdbHeaderView.refreshRelatedVideos();
    }

    private void loadTmdbRelatedVideosForCurrentEpisode() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        Episode episode = getEpisode();
        TmdbEpisode tmdbEpisode = episode == null ? null : episode.getTmdbEpisode();
        int seasonNumber = tmdbEpisode != null && tmdbEpisode.getSeasonNumber() >= 0
                ? tmdbEpisode.getSeasonNumber() : currentSourceSeasonNumber();
        int episodeNumber = tmdbEpisode == null ? (episode == null ? -1 : episode.getNumber()) : tmdbEpisode.getNumber();
        if (episodeNumber <= 0) episodeNumber = -1;
        mTmdbUIAdapter.loadRelatedVideosAsync(seasonNumber, episodeNumber);
        if (mTmdbHeaderView != null) mTmdbHeaderView.refreshRelatedVideos();
    }

    private void refreshTmdbPersonalRecommendations() {
        if (mTmdbHeaderView == null || mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        mTmdbHeaderView.refreshPersonalRecommendationRows();
    }

    private void mergeTmdbEpisodeMetadata(Vod item) {
        if (item == null || item.getFlags() == null || mFlagAdapter == null || mFlagAdapter.isEmpty()) return;
        Flag current = getFlag();
        if (current == null) return;
        for (Flag source : item.getFlags()) {
            if (source == null || !current.equals(source) || source.getEpisodes() == null) continue;
            current.mergeEpisodes(source.getEpisodes(), mHistory != null && mHistory.isRevSort());
            return;
        }
    }
    private void refreshTmdbEpisodeTitles() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        mSourceEpisodeSeasonCache.clear();
        updateEpisodeSeasonContext();
        if (mTmdbHeaderView != null) mTmdbHeaderView.refreshEpisodeMetadata();
        if (mEpisodeAdapter == null || mEpisodeGroupAdapter == null || mFlagAdapter == null || mFlagAdapter.isEmpty()) return;
        Flag flag = getFlag();
        if (flag == null) return;
        List<Episode> items = flag.getEpisodes();
        int size = items.size();
        boolean useTmdbCard = shouldUseTmdbEpisodeCards(items);
        boolean showViewMode = size > 1;
        if (showViewMode) mEpisodeGridMode = Setting.getTmdbEpisodeGridMode();
        if (!showViewMode) mEpisodeGridMode = true;
        mBinding.control.action.episodes.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();
        mBinding.control.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        if (mBinding.episodeViewMode != null) mBinding.episodeViewMode.setVisibility(showViewMode ? View.VISIBLE : View.GONE);
        if (mBinding.episodeFileName != null) mBinding.episodeFileName.setVisibility(showViewMode ? View.VISIBLE : View.GONE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(View.GONE);
        int maxGroupSize = shouldUseTmdbDetailLayout() ? EpisodeRangePolicy.CARD_PAGE_MAX_SIZE : 0;
        List<EpisodeGroupAdapter.Group> groups = EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort(), maxGroupSize);
        mEpisodeGroupAdapter.addAll(groups);
        updateEpisodeGroupVisibility();
        List<Episode> displayItems = getEpisodeDisplayItems(items);
        updateEpisodeFallbackStillUrl();
        mEpisodeAdapter.setUseTmdbCard(useTmdbCard);
        mEpisodeAdapter.setNativeGridExpanded(mEpisodeGridMode && !useTmdbCard && isOriginalEnhancedEpisodeFallback());
        mEpisodeAdapter.setViewType(!mEpisodeGridMode ? ViewType.HORI : ViewType.GRID);
        mEpisodeAdapter.refreshMetadata(displayItems);
        updateEpisodeLayout(displayItems, useTmdbCard);
        if (shouldUseEpisodeRangePaging(items)) scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition());
        else selectEpisodeGroupByPosition(mEpisodeAdapter.getPosition());
        updateEpisodeViewModeButton();
        updateEpisodeFileNameButton();
        boolean episodeTitleChanged = refreshCurrentHistoryEpisodeTitle();
        mBinding.control.title.setText(getPlaybackControlTitle());
        if (episodeTitleChanged) syncHistory();
        if (mHistory != null) PlaybackEventCollector.get().updateHistory(mHistory);
        scrollEpisodeToSelected();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private String getHistoryEpisodeName(Episode episode) {
        return EpisodeHistoryTitleResolver.resolve(
                episode,
                getEpisodeTitles(),
                Setting.getTmdbEpisodeShowScrapedName(),
                Setting.isTmdbEpisodeFileSize());
    }

    private String getCurrentHistoryEpisodeCacheName() {
        if (mHistory == null || mFlagAdapter == null) return "";
        Episode historyEpisode = mHistory.getEpisode();
        for (Flag flag : mFlagAdapter.getItems()) {
            if (!TextUtils.equals(flag.getFlag(), mHistory.getVodFlag())) continue;
            Episode episode = flag.find(historyEpisode, true);
            if (episode != null) return episodePositionCacheName(episode, mHistory.getTmdbEpisodeNumber() > 0 ? mHistory.getTmdbSeasonNumber() : -1);
        }
        if (!TextUtils.isEmpty(historyEpisode.getUrl())) {
            for (Flag flag : mFlagAdapter.getItems()) {
                for (Episode episode : flag.getEpisodes()) {
                    if (TextUtils.equals(episode.getUrl(), historyEpisode.getUrl())) return episodePositionCacheName(episode, mHistory.getTmdbEpisodeNumber() > 0 ? mHistory.getTmdbSeasonNumber() : -1);
                }
            }
        }
        return "";
    }

    /**
     * 季号不可靠且当前线路混排多季时，放弃集数播放位置缓存。
     * 这种线路里两季都有"第01集"，裸集名会让没看过的一集读到另一季已看完的进度、直接跳到结尾。
     * 从头开始播比跳到错误位置好。写入侧一并跳过，避免继续污染这些有歧义的槽位。
     */
    private boolean skipEpisodePositionCache() {
        return currentSourceSeasonNumber() < 0 && mSourceEpisodeSeasonCache.hasMixedSeasons(getFlag());
    }

    private String episodePositionCacheName(Episode episode, int preferredSeason) {
        if (episode == null) return "";
        int season = preferredSeason;
        TmdbEpisode tmdbEpisode = episode.getTmdbEpisode();
        if (season < 0 && tmdbEpisode != null && tmdbEpisode.getNumber() > 0 && tmdbEpisode.getSeasonNumber() >= 0) {
            season = tmdbEpisode.getSeasonNumber();
        }
        if (season < 0) season = currentSourceSeasonNumber();
        return EpisodeSeasonPolicy.episodePositionCacheKey(season, episode.getName());
    }
    private boolean refreshCurrentHistoryEpisodeTitle() {
        if (mHistory == null || mFlagAdapter == null || mFlagAdapter.isEmpty()) return false;
        Flag flag = getFlag();
        if (flag == null) return false;
        Episode episode = flag.find(mHistory.getEpisode(), true);
        if (episode == null) return false;
        String title = getHistoryEpisodeName(episode);
        Episode historyEpisode = withSourceSeasonEpisodeIdentity(episode);
        boolean changed = historyEpisode.getTmdbEpisode() != null && mHistory.setTmdbEpisodePosition(historyEpisode);
        if (!TextUtils.isEmpty(title) && !TextUtils.equals(title, mHistory.getVodRemarks())) {
            mHistory.setVodRemarks(title);
            changed = true;
        }
        if (!TextUtils.equals(episode.getUrl(), mHistory.getEpisodeUrl())) {
            mHistory.setEpisodeUrl(episode.getUrl());
            changed = true;
        }
        return changed;
    }

    private void updateFlag(Flag activated, List<Flag> items) {
        items.forEach(item -> mFlagAdapter.getItems().stream()
                .filter(item::equals).findFirst().ifPresentOrElse(target -> {
                    if (target == item || target.getEpisodes() == item.getEpisodes()) return;
                    target.mergeEpisodes(item.getEpisodes(), mHistory.isRevSort());
                    if (target.equals(activated)) setEpisodeAdapter(target.getEpisodes());
                }, () -> mFlagAdapter.add(item)));
    }

    private final PlaybackService.NavigationCallback mNavigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onNext() {
            checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finishVideoPlayback();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }

        @Override
        public void onAudio() {
            setAudioOnly(true);
            syncPiPForPlaybackMode();
            moveTaskToBack(true);
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onControllerReadyReconciled() {
        showPlaybackContent();
    }

    @Override
    protected void onPrepare() {
        android.util.Log.d("VideoActivity", "onPrepare: setting Clock callback");
        setPlayerKernel();
        setDecode();
        setLut();
        setPosition();
        setSpeed();
        mClock.setCallback(this);
        requestIntroSkipPlan();
    }

    @Override
    protected void onTracksChanged() {
        updateAudioOnlyState();
        syncPiPForPlaybackMode();
        refreshLyrics();
        setTrackVisible();
        mClock.setCallback(this);
        // 轨道要等新引擎 prepare 完才回来，重建那一刻按钮还是隐藏态，弹窗必须在这里再抄一次。
        refreshControlDialog();
    }

    private void updateAudioOnlyState() {
        if (service() == null) return;
        setAudioOnly(LyricsController.isAudioOnly(player()));
        syncDesktopLyricsAudioContent();
        setAudioStageVisible(shouldUseImmersiveAudio());
        setKaraokeActionState();
    }

    private void syncDesktopLyricsAudioContent() {
        if (service() != null) service().setDesktopLyricsAudioContent(isAudioOnly() || isMusicLike());
    }

    private void setAudioStageVisible(boolean visible) {
        boolean immersiveEnabled = PlayerSetting.isImmersiveAudioMode();
        visible = visible && immersiveEnabled;
        if (visible) ensureImmersiveAudioControllers();
        if (visible && isAutoRotate() && !isLock() && !isRotate()) setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        if (mAudioStageVisible == visible) {
            syncPiPForPlaybackMode();
            updateAudioStageText();
            updateAudioStageControls();
            return;
        }
        mAudioStageVisible = visible;
        syncPiPForPlaybackMode();
        if (!visible) mAudioLightEffectAnimated = false;
        applyStatusBarSpacer();
        applyAudioStageInsets();
        mBinding.audioStage.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) mBinding.audioStage.bringToFront();
        if (visible) applyAudioBackground();
        mBinding.lyrics.setSuppressed(!immersiveEnabled || visible);
        mBinding.audioLyrics.setSuppressed(!visible);
        syncKaraokeStageVisibility();
        applyAudioStageLayout(visible);
        applyAudioPageMode(visible);
        updateAudioStageText();
        updateAudioStageControls();
    }

    private void syncKaraokeStageVisibility() {
        if (mBinding == null) return;
        if (!PlayerSetting.isImmersiveAudioMode()) {
            if (mBinding.karaoke != null) mBinding.karaoke.setVisibility(View.GONE);
            mBinding.audioKaraoke.setSpectrumMode(false);
            mBinding.audioKaraoke.setVisibility(View.GONE);
            return;
        }
        if (mAudioStageVisible) {
            if (mBinding.karaoke != null) mBinding.karaoke.setVisibility(View.GONE);
            boolean karaokeMode = PlayerSetting.isKaraokeMode();
            mBinding.audioKaraoke.setSpectrumMode(!karaokeMode);
            if (karaokeMode && mBinding.audioKaraoke.getVisibility() == View.GONE) mBinding.audioKaraoke.setVisibility(View.INVISIBLE);
        } else {
            mBinding.audioKaraoke.setSpectrumMode(false);
            mBinding.audioKaraoke.setVisibility(View.GONE);
        }
    }

    private void applyAudioStageLayout(boolean visible) {
        if (isFullscreen() || isInPictureInPictureMode()) return;
        if (!visible) {
            if (mFrameHeight > 0) mFrameParams.height = mFrameHeight;
        } else if (isPort()) {
            mFrameParams.height = 0;
        } else {
            mFrameParams.height = mFrameHeight;
        }
        mBinding.video.setLayoutParams(mFrameParams);
    }

    private void applyAudioPageMode(boolean visible) {
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.name.setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.remark.setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.site.setVisibility(visible ? View.GONE : mBinding.site.getText().length() == 0 ? View.GONE : View.VISIBLE);
        mBinding.other.setVisibility(visible ? View.GONE : mBinding.other.getText().length() == 0 ? View.GONE : View.VISIBLE);
        mBinding.director.setVisibility(visible ? View.GONE : mBinding.director.getText().length() == 0 ? View.GONE : View.VISIBLE);
        mBinding.actor.setVisibility(visible ? View.GONE : mBinding.actor.getText().length() == 0 ? View.GONE : View.VISIBLE);
        mBinding.contentLayout.setVisibility(visible ? View.GONE : mBinding.content.getText().length() == 0 ? View.GONE : View.VISIBLE);
        mBinding.actionRow.setVisibility(visible ? View.GONE : View.VISIBLE);
        mBinding.flag.setVisibility(visible || mFlagAdapter == null || mFlagAdapter.isEmpty() ? View.GONE : View.VISIBLE);
        boolean qualityVisible = mQualityAdapter != null && mQualityAdapter.getItemCount() > 1;
        boolean episodeGroupVisible = mEpisodeGroupAdapter != null && mEpisodeGroupAdapter.getItemCount() > 1;
        boolean episodeVisible = mEpisodeAdapter != null && mEpisodeAdapter.getItemCount() > 0;
        boolean quickVisible = mQuickAdapter != null && mQuickAdapter.getItemCount() > 0;
        mBinding.qualityText.setVisibility(visible || !qualityVisible ? View.GONE : View.VISIBLE);
        mBinding.quality.setVisibility(visible || !qualityVisible ? View.GONE : View.VISIBLE);
        mBinding.episodeGroup.setVisibility(visible || !episodeGroupVisible ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(visible || !episodeVisible ? View.GONE : View.VISIBLE);
        mBinding.quick.setVisibility(visible || !quickVisible ? View.GONE : View.VISIBLE);
    }

    private void updateAudioStageText() {
        if (mBinding == null) return;
        String title = getAudioStageTitle();
        String subtitle = getAudioStageArtist(title);
        mBinding.audioTitle.setText(TextUtils.isEmpty(title) ? getString(R.string.player_audio_badge_audio) : title);
        mBinding.audioSubtitle.setText(subtitle);
        mBinding.audioSubtitle.setVisibility(TextUtils.isEmpty(subtitle) ? View.GONE : View.VISIBLE);
        mBinding.audioBadgeLyrics.setText(PlayerSetting.isKaraokeMode() ? getString(R.string.player_karaoke_mode) : getString(R.string.player_audio_badge_lyrics));
    }

    private void updateAudioStageControls() {
        if (mBinding == null) return;
        if (mAudioStageVisible) applyAudioPageMode(true);
        boolean hasPrev = hasAdjacentEpisode(-1);
        boolean hasNext = hasAdjacentEpisode(1);
        mBinding.audioPrev.setEnabled(hasPrev);
        mBinding.audioPrev.setAlpha(hasPrev ? 1f : 0.35f);
        mBinding.audioNext.setEnabled(hasNext);
        mBinding.audioNext.setAlpha(hasNext ? 1f : 0.35f);
        mBinding.audioQueueAction.setEnabled(true);
        mBinding.audioQueueAction.setAlpha(1f);
        setAudioRepeatSelected(service() != null && player().isRepeatOne());
        mBinding.audioKaraokeAction.setSelected(PlayerSetting.isKaraokeMode());
        mBinding.audioKeepAction.setSelected(Keep.find(getHistoryKey()) != null);
        checkAudioPlayImg(service() != null && player().isPlaying());
        syncAudioCoverRotation();
    }

    private void applyAudioBackground() {
        if (mBinding == null) return;
        mAudioLightEffectAnimated = service() != null && player().isPlaying();
        AudioPlayerBackgroundDrawable drawable = new AudioPlayerBackgroundDrawable(PlayerSetting.getAudioBackground(), mAudioArtworkColor, PlayerSetting.isAudioBackgroundDecorated(), PlayerSetting.isAudioBackgroundLightEffect(), mAudioLightEffectAnimated, PlayerSetting.getAudioBackgroundSeed(), PlayerSetting.getAudioBackgroundDecorationSeed());
        syncAudioBackgroundHalo(drawable);
        mBinding.audioStage.setBackground(drawable);
        scheduleAudioBackgroundHaloSync(drawable);
        mBinding.audioStage.invalidate();
    }

    private void scheduleAudioBackgroundHaloSync(AudioPlayerBackgroundDrawable drawable) {
        mBinding.audioStage.post(() -> syncAudioBackgroundHalo(drawable));
        mBinding.audioStage.postDelayed(() -> syncAudioBackgroundHalo(drawable), 120);
        mBinding.audioStage.postDelayed(() -> syncAudioBackgroundHalo(drawable), 360);
    }

    private void syncAudioBackgroundHalo(AudioPlayerBackgroundDrawable drawable) {
        if (mBinding == null || drawable == null) return;
        View anchor = mBinding.audioCover != null ? mBinding.audioCover : mBinding.audioDisc;
        if (mBinding.audioStage.getWidth() <= 0 || anchor.getWidth() <= 0 || anchor.getHeight() <= 0) return;
        if (mBinding.audioStage.getBackground() != drawable) return;
        Rect bounds = new Rect(0, 0, anchor.getWidth(), anchor.getHeight());
        mBinding.audioStage.offsetDescendantRectToMyCoords(anchor, bounds);
        float cx = bounds.exactCenterX();
        float cy = bounds.exactCenterY();
        float radius = Math.max(anchor.getWidth(), anchor.getHeight()) * 0.56f;
        drawable.setRecordHaloAnchor(cx, cy, radius);
    }

    private void updateAudioArtworkColor(@Nullable Drawable drawable) {
        mAudioArtworkColor = extractAudioArtworkColor(drawable);
        if (mAudioStageVisible && PlayerSetting.getAudioBackground() == PlayerSetting.AUDIO_BACKGROUND_ARTWORK) applyAudioBackground();
    }

    private int extractAudioArtworkColor(@Nullable Drawable drawable) {
        if (drawable == null) return Color.rgb(255, 111, 145);
        Bitmap bitmap = null;
        try {
            bitmap = createPaletteBitmap(drawable);
            Palette palette = Palette.from(bitmap).maximumColorCount(8).generate();
            Palette.Swatch swatch = palette.getVibrantSwatch();
            if (swatch == null) swatch = palette.getLightVibrantSwatch();
            if (swatch == null) swatch = palette.getDominantSwatch();
            return swatch == null ? Color.rgb(255, 111, 145) : swatch.getRgb();
        } catch (Exception ignored) {
            return Color.rgb(255, 111, 145);
        } finally {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
        }
    }

    private Bitmap createPaletteBitmap(Drawable drawable) {
        int width = 72;
        int height = 72;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    private void setAudioRepeatSelected(boolean selected) {
        if (mBinding == null) return;
        mBinding.audioRepeatAction.setSelected(selected);
        mBinding.audioRepeatAction.setAlpha(selected ? 1f : 0.62f);
    }

    private void syncAudioCoverRotation() {
        if (!mAudioStageVisible || service() == null || !player().isPlaying()) {
            stopAudioCoverRotation();
            return;
        }
        if (mAudioCoverAnimator == null) {
            mAudioCoverAnimator = ObjectAnimator.ofFloat(mBinding.audioCover, View.ROTATION, mBinding.audioCover.getRotation(), mBinding.audioCover.getRotation() + 360f);
            mAudioCoverAnimator.setDuration(20000);
            mAudioCoverAnimator.setInterpolator(new LinearInterpolator());
            mAudioCoverAnimator.setRepeatCount(ObjectAnimator.INFINITE);
            mAudioCoverAnimator.setRepeatMode(ObjectAnimator.RESTART);
        }
        if (!mAudioCoverAnimator.isStarted()) mAudioCoverAnimator.start();
    }

    private void stopAudioCoverRotation() {
        if (mAudioCoverAnimator == null) return;
        mAudioCoverAnimator.cancel();
        mAudioCoverAnimator = null;
    }

    private String getAudioStageTitle() {
        String currentTrack = getCurrentTrackMetadata();
        if (!TextUtils.isEmpty(currentTrack)) return splitCurrentTrack(currentTrack)[0];
        Episode episode = getEpisode();
        String queuedTitle = mAudioQueueTitles.get(audioQueueEpisodeKey(episode));
        if (!TextUtils.isEmpty(queuedTitle)) return queuedTitle;
        AudioPlaylistStore.Entry entry = findCurrentAudioPlaylistEntry(episode);
        if (entry != null) return getAudioQueueSongTitle(entry.title, entry.name);
        if (isAudioQueueEpisode(episode) && !TextUtils.isEmpty(episode.getDisplayName())) return episode.getDisplayName();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodName())) return mHistory.getVodName();
        if (!TextUtils.isEmpty(getName())) return getName();
        CharSequence text = mBinding.name.getText();
        return text == null ? "" : text.toString();
    }

    private String getAudioStageArtist(String title) {
        String currentTrack = getCurrentTrackMetadata();
        if (!TextUtils.isEmpty(currentTrack)) return splitCurrentTrack(currentTrack)[1];
        Episode item = getEpisode();
        String queuedArtist = mAudioQueueArtists.get(audioQueueEpisodeKey(item));
        if (!TextUtils.isEmpty(queuedArtist)) return queuedArtist;
        AudioPlaylistStore.Entry entry = findCurrentAudioPlaylistEntry(item);
        if (entry != null && !TextUtils.isEmpty(entry.artist)) return entry.artist;
        String episode = item == null ? "" : item.getName();
        String artist = getArtistFromEpisode(title, cleanAudioEpisodeForArtist(episode));
        return TextUtils.equals(artist, title) ? "" : artist;
    }

    private String getCurrentTrackMetadata() {
        if (service() == null || player().getMetadata() == null) return "";
        MediaMetadata metadata = player().getMetadata();
        if (metadata.subtitle != null && !TextUtils.isEmpty(metadata.subtitle.toString().trim())) return metadata.subtitle.toString().trim();
        if (metadata.artist != null && !TextUtils.isEmpty(metadata.artist.toString().trim())) return metadata.artist.toString().trim();
        return "";
    }

    private void syncCurrentAudioPlaylistMetadata() {
        if (!mAudioStageVisible || service() == null) return;
        Episode episode = getPlaybackEpisode();
        if (episode == null) return;
        AudioPlaylistStore.Entry entry = findCurrentAudioPlaylistEntry(episode);
        String track = getCurrentTrackMetadata();
        if (TextUtils.isEmpty(track) || !isCurrentAudioQueueTrack(track, episode)) return;
        String[] parts = splitCurrentTrack(track);
        String title = parts[0];
        String artist = parts[1];
        if (!isUsefulAudioQueueTitle(title, episode)) return;
        String key = audioQueueEpisodeKey(episode);
        boolean changed = !TextUtils.equals(title, mAudioQueueTitles.get(key));
        if (!TextUtils.isEmpty(artist) && !TextUtils.equals(artist, mAudioQueueArtists.get(key))) changed = true;
        if (!changed) return;
        mAudioQueueTitles.put(key, title);
        if (!TextUtils.isEmpty(artist)) mAudioQueueArtists.put(key, artist);
        AudioPlaylistStore.putMetadata(episode.getUrl(), title, artist);
        if (entry != null) {
            entry.title = title;
            if (!TextUtils.isEmpty(artist)) entry.artist = artist;
            AudioPlaylistStore.upsertItem(entry);
        }
        if (mAudioQueueAdapter != null) mAudioQueueAdapter.notifyDataSetChanged();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-playlist", "metadata learned name=%s title=%s artist=%s", episode.getName(), title, artist);
    }

    private String[] splitCurrentTrack(String value) {
        String text = Objects.toString(value, "").replaceFirst("^\\s*\\d+[.、\\s]+", "").trim();
        for (String separator : new String[]{" - ", " – ", " — "}) {
            int index = text.lastIndexOf(separator);
            if (index > 0 && index + separator.length() < text.length()) {
                return new String[]{text.substring(0, index).trim(), text.substring(index + separator.length()).trim()};
            }
        }
        return new String[]{text, ""};
    }

    private String getAudioQueueDisplayName(Episode episode, boolean active) {
        String fallback = episode == null ? "" : episode.getDisplayName();
        if (!isAudioQueuePlaceholderName(fallback)) return fallback;
        String title = mAudioQueueTitles.get(audioQueueEpisodeKey(episode));
        String artist = mAudioQueueArtists.get(audioQueueEpisodeKey(episode));
        if (active) {
            String track = getCurrentTrackMetadata();
            if (!TextUtils.isEmpty(track) && isCurrentAudioQueueTrack(track, episode)) {
                String[] parts = splitCurrentTrack(track);
                if (isUsefulAudioQueueTitle(parts[0], episode)) {
                    title = parts[0];
                    artist = parts[1];
                }
            }
        }
        if (!isUsefulAudioQueueTitle(title, episode)) return fallback;
        if (TextUtils.isEmpty(artist) || title.contains(artist)) return title;
        return title + " - " + artist;
    }

    private boolean isUsefulAudioQueueTitle(String title, Episode episode) {
        String value = Objects.toString(title, "").trim();
        if (TextUtils.isEmpty(value) || isAudioQueuePlaceholderName(value)) return false;
        if (episode == null || !isAudioQueuePlaceholderName(episode.getDisplayName())) return true;
        String collection = mHistory == null ? "" : Objects.toString(mHistory.getVodName(), "").trim();
        if (!TextUtils.isEmpty(collection) && TextUtils.equals(value, collection)) return false;
        if (!TextUtils.isEmpty(getName()) && TextUtils.equals(value, getName())) return false;
        return !isAudioQueueCollectionTitle(value);
    }

    private boolean isAudioQueuePlaceholderName(String name) {
        String value = Objects.toString(name, "").trim();
        return value.matches("(?i)^(?:\\[?p\\s*0*\\d{1,4}\\]?|0*\\d{1,4})[.．、_\\-\\s]*$");
    }

    private boolean isCurrentAudioQueueTrack(String track, Episode episode) {
        String episodeNumber = audioQueueTrackNumber(episode == null ? "" : episode.getDisplayName(), true);
        if (TextUtils.isEmpty(episodeNumber)) return true;
        String trackNumber = audioQueueTrackNumber(track, false);
        return TextUtils.isEmpty(trackNumber) || TextUtils.equals(trackNumber, episodeNumber);
    }

    private String audioQueueTrackNumber(String text, boolean placeholder) {
        String value = Objects.toString(text, "").trim();
        String number;
        if (placeholder) {
            if (!isAudioQueuePlaceholderName(value)) return "";
            number = value.replaceAll("\\D", "");
        } else {
            number = value.replaceFirst("^\\s*(\\d{1,4})[.．、\\s]+.*$", "$1");
            if (TextUtils.equals(number, value)) return "";
        }
        number = number.replaceFirst("^0+(?!$)", "");
        return number;
    }

    private boolean isAudioQueueCollectionTitle(String title) {
        String value = Objects.toString(title, "").trim().toLowerCase(Locale.ROOT);
        return value.contains("合集")
                || value.contains("歌单")
                || value.contains("排行榜")
                || value.contains("热门歌曲")
                || value.contains("最好听")
                || value.contains("精选")
                || value.matches(".*\\d+\\s*(首|曲).*");
    }

    private AudioPlaylistStore.Entry findCurrentAudioPlaylistEntry(Episode episode) {
        String url = episode == null ? "" : Objects.toString(episode.getUrl(), "");
        if (TextUtils.isEmpty(url)) return null;
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null) return null;
        for (AudioPlaylistStore.Entry entry : playlist.items) {
            if (entry != null && TextUtils.equals(entry.url, url)) return entry;
        }
        return null;
    }

    private String getEpisodeArtwork(Episode episode) {
        String queuedPic = mAudioQueuePics.get(audioQueueEpisodeKey(episode));
        if (!TextUtils.isEmpty(queuedPic)) return queuedPic;
        return mHistory == null ? "" : mHistory.getVodPic();
    }

    private Episode getPlaybackEpisode() {
        String key = Objects.toString(mPlaybackEpisodeKey, "");
        Flag flag = getFlag();
        if (TextUtils.isEmpty(key) || flag == null) return getEpisode();
        for (Episode episode : flag.getEpisodes()) {
            if (TextUtils.equals(audioQueueEpisodeKey(episode), key)) return episode;
        }
        return getEpisode();
    }

    private String getEpisodeInlineLyrics(Episode episode) {
        if (isAudioQueueEpisode(episode)) return Objects.toString(mAudioQueueLyrics.get(audioQueueEpisodeKey(episode)), "");
        return mDetailLyrics;
    }

    private void applyPlaybackArtwork(Episode episode) {
        loadArtwork(getEpisodeArtwork(episode), audioQueueEpisodeKey(episode));
    }

    private void restorePlaybackArtwork() {
        Episode episode = getPlaybackEpisode();
        String owner = audioQueueEpisodeKey(episode);
        String url = getEpisodeArtwork(episode);
        if (TextUtils.isEmpty(url) && TextUtils.equals(owner, mArtworkRequestOwner)) url = mArtworkRequestUrl;
        SpiderDebug.log("audio-artwork", "restore owner=%s url=%s requestOwner=%s request=%s", owner, !TextUtils.isEmpty(url), mArtworkRequestOwner, !TextUtils.isEmpty(mArtworkRequestUrl));
        loadArtwork(url, owner);
    }

    private String cleanAudioEpisodeForArtist(String episode) {
        String value = Objects.toString(episode, "").trim();
        if (value.isEmpty()) return "";
        String[] parts = value.split("[|｜]");
        return parts.length == 0 ? value : parts[parts.length - 1].trim();
    }

    private void refreshLyrics() {
        if (mLyrics == null || service() == null) return;
        debugLyricsLoop("refreshLyrics", true);
        updateAudioOnlyState();
        boolean audioContent = isAudioOnly() || isMusicLike();
        if (!mLyrics.hasChoice(player()) && showInlineLyrics()) {
            refreshKaraoke(audioContent);
            return;
        }
        mLyrics.refresh(player(), audioContent);
        refreshKaraoke(audioContent);
    }

    private void refreshKaraoke(boolean audioContent) {
        if (mKaraoke == null || service() == null) return;
        boolean loadTrack = !mSkipKaraokeTrackAutoLoad;
        mKaraoke.refresh(this, player(), audioContent, loadTrack);
    }

    private void debugPlaybackControl(String event) {
        if (!SpiderDebug.isEnabled()) return;
        if (service() == null || player().isEmpty()) {
            SpiderDebug.log("playback-control", "video.%s noPlayer owner=%s service=%s", event, isOwner(), service() != null);
            return;
        }
        SpiderDebug.log("playback-control", "video.%s pos=%d dur=%d state=%d playing=%s playWhenReady=%s repeat=%s owner=%s audioStage=%s controller=%s",
                event, player().getPosition(), player().getDuration(), player().getPlaybackState(), player().isPlaying(), player().getPlayer().getPlayWhenReady(), player().isRepeatOne(), isOwner(), mAudioStageVisible, controller() != null);
    }

    private void debugLyricsLoop(String event, boolean force) {
        if (!SpiderDebug.isEnabled()) return;
        if (service() == null || player().isEmpty()) {
            if (force) SpiderDebug.log("lyrics-loop", "video.%s noPlayer owner=%s service=%s", event, isOwner(), service() != null);
            return;
        }
        long position = Math.max(0, player().getPosition());
        long duration = player().getDuration();
        boolean playing = player().isPlaying();
        boolean nearStart = position <= 5000;
        boolean nearEnd = duration > 0 && duration - position <= 5000;
        boolean backward = mLyricsLoopLastPlayerPosition != C.TIME_UNSET && position + 1200 < mLyricsLoopLastPlayerPosition;
        boolean playingChanged = playing != mLyricsLoopLastPlaying;
        if (force || nearStart || nearEnd || backward || playingChanged) {
            SpiderDebug.log("lyrics-loop", "video.%s pos=%d last=%d dur=%d state=%d playing=%s playWhenReady=%s repeat=%s nearStart=%s nearEnd=%s backward=%s lyricsLines=%d main={%s} audio={%s} karaokePos=%d",
                    event, position, mLyricsLoopLastPlayerPosition, duration, player().getPlaybackState(), playing, player().getPlayer().getPlayWhenReady(), player().isRepeatOne(), nearStart, nearEnd, backward,
                    mLyrics == null ? -1 : mLyrics.getLines().size(),
                    mBinding == null || mBinding.lyrics == null ? "null" : mBinding.lyrics.debugState(),
                    mBinding == null || mBinding.audioLyrics == null ? "null" : mBinding.audioLyrics.debugState(),
                    mKaraoke == null || mKaraoke.getSnapshot() == null ? -1 : mKaraoke.getSnapshot().getPositionMs());
        }
        mLyricsLoopLastPlayerPosition = position;
        mLyricsLoopLastPlaying = playing;
    }

    private boolean isLyricsSearchAvailable() {
        if (mLyrics == null || service() == null) return false;
        updateAudioOnlyState();
        return isAudioOnly() || isMusicLike();
    }

    private String getLyricsSearchKeyword() {
        if (service() == null) return getName();
        LyricsRequest request = LyricsRequest.from(player());
        return request.displayKeyword();
    }

    private List<String> getLyricsSearchSuggestions() {
        if (service() == null) return withLastLyricsSearchSuggestion(LyricsRequest.searchSuggestions(getName()), getName());
        LyricsRequest request = LyricsRequest.from(player());
        return withLastLyricsSearchSuggestion(request.searchSuggestions(), request.stableSignature());
    }

    private String getLyricsSearchCacheKey(String keyword) {
        if (service() == null) return keyword;
        return LyricsRequest.from(player()).withKeyword(keyword).signature();
    }

    private void rememberLyricsSearchKeyword(String keyword) {
        String value = Objects.toString(keyword, "").trim();
        if (TextUtils.isEmpty(value)) return;
        mLyricsLastSearchSignature = getLyricsSearchSignature();
        mLyricsLastSearchKeyword = value;
    }

    private String getLyricsSearchSignature() {
        if (service() == null) return getName();
        return LyricsRequest.from(player()).stableSignature();
    }

    private List<String> withLastLyricsSearchSuggestion(List<String> suggestions, String signature) {
        String keyword = Objects.toString(mLyricsLastSearchKeyword, "").trim();
        if (TextUtils.isEmpty(keyword) || !TextUtils.equals(mLyricsLastSearchSignature, signature)) return suggestions;
        List<String> values = new ArrayList<>();
        values.add(keyword);
        for (String suggestion : suggestions) {
            String value = Objects.toString(suggestion, "").trim();
            if (TextUtils.isEmpty(value) || containsLyricsSearchSuggestion(values, value)) continue;
            values.add(value);
            if (values.size() >= 8) break;
        }
        return values;
    }

    private boolean containsLyricsSearchSuggestion(List<String> suggestions, String keyword) {
        for (String suggestion : suggestions) if (suggestion.equalsIgnoreCase(keyword)) return true;
        return false;
    }

    private void searchLyrics(String keyword) {
        if (mLyrics == null || service() == null) return;
        updateAudioOnlyState();
        int seq = ++mLyricsSearchSeq;
        String cacheKey = getLyricsSearchCacheKey(keyword);
        rememberLyricsSearchKeyword(keyword);
        if (TextUtils.equals(mLyricsSearchKeyword, cacheKey) && mLyricsSearchResults != null && !mLyricsSearchResults.isEmpty()) {
            showLyricsResults(seq, cacheKey, mLyricsSearchResults, true);
            return;
        }
        showLyricsSearching(seq);
        mLyrics.search(player(), isAudioOnly() || isMusicLike(), keyword, (results, complete) -> showLyricsResults(seq, cacheKey, results, complete));
    }

    private void showLyricsSearching(int seq) {
        dismissLyricsResultDialog();
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_lyrics_search)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        TextView message = createAudioSheetText(getString(R.string.player_lyrics_searching), 15, false);
        root.addView(message, audioSheetTopParams(14, 44));
        TextView cancel = createAudioSheetButton(getString(R.string.dialog_cancel), false, () -> {
            if (seq == mLyricsSearchSeq) mLyricsSearchSeq++;
            dialog.dismiss();
        });
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.addView(cancel, audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(10, 44));
        dialog.setContentView(root);
        dialog.setOnCancelListener(d -> {
            if (seq == mLyricsSearchSeq) mLyricsSearchSeq++;
        });
        dialog.setOnDismissListener(d -> {
            if (mLyricsResultDialog == dialog) mLyricsResultDialog = null;
        });
        mLyricsResultDialog = dialog;
        showAudioSheet(dialog);
    }

    private void showLyricsResults(int seq, String cacheKey, List<LyricsResult> results, boolean complete) {
        if (seq != mLyricsSearchSeq) return;
        if (isFinishing()) return;
        if (results == null || results.isEmpty()) {
            if (complete) {
                dismissLyricsResultDialog();
                Notify.show(R.string.player_lyrics_not_found);
            }
            return;
        }
        mLyricsSearchResults = results;
        mLyricsSearchKeyword = cacheKey;
        String[] labels = new String[results.size()];
        for (int i = 0; i < results.size(); i++) labels[i] = getLyricsResultLabel(results.get(i));
        if (mLyricsResultDialog != null && mLyricsResultList != null && mLyricsResultDialog.isShowing()) {
            updateLyricsResultList(labels);
            updateLyricsResultSheetHeight(labels.length);
            return;
        }
        dismissLyricsResultDialog();
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_lyrics_select)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        mLyricsResultList = new LinearLayout(this);
        mLyricsResultList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mLyricsResultList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lyricsResultSheetHeight(labels.length)));
        dialog.setContentView(root);
        dialog.setOnCancelListener(d -> {
            if (seq == mLyricsSearchSeq) mLyricsSearchSeq++;
        });
        dialog.setOnDismissListener(d -> {
            if (mLyricsResultDialog == dialog) {
                mLyricsResultDialog = null;
                mLyricsResultList = null;
            }
        });
        mLyricsResultDialog = dialog;
        updateLyricsResultList(labels);
        showCompactPlaybackSheet(dialog, false);
    }

    private BottomSheetDialog createAudioSheet() {
        return new BottomSheetDialog(this);
    }

    private LinearLayout createAudioSheetRoot() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(ResUtil.dp2px(24), ResUtil.dp2px(10), ResUtil.dp2px(24), ResUtil.dp2px(18) + mEpisodeBottomInset);
        root.setBackground(audioSheetGlassBackground());
        View handle = new View(this);
        handle.setBackground(roundRect(0x55FFFFFF, 2, 0, 0));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(ResUtil.dp2px(38), ResUtil.dp2px(4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.bottomMargin = ResUtil.dp2px(14);
        root.addView(handle, handleParams);
        if (isLandscapeAudioSheet()) styleAudioDrawerRoot(root);
        return root;
    }

    private boolean isLandscapeAudioSheet() {
        return mAudioStageVisible && ResUtil.isLand(this);
    }

    private void styleAudioDrawerRoot(LinearLayout root) {
        root.setPadding(ResUtil.dp2px(22), ResUtil.dp2px(10), ResUtil.dp2px(22), ResUtil.dp2px(14));
        root.setMinimumHeight(audioDrawerHeight());
        root.setBackground(audioDrawerBackground());
    }

    private TextView createAudioSheetTitle(String text) {
        TextView title = createAudioSheetText(text, 17, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        return title;
    }

    private TextView createAudioSheetText(String text, int sizeSp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextColor(Color.WHITE);
        view.setTextSize(sizeSp);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        return view;
    }

    private TextView createAudioSheetItem(String label, Runnable action) {
        TextView view = createAudioSheetText(label, 15, false);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(12), 0);
        view.setBackground(audioSheetItemBackground(false));
        view.setSingleLine(false);
        view.setMaxLines(2);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView createAudioSheetButton(String label, boolean primary, Runnable action) {
        TextView view = createAudioSheetText(label, 15, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextColor(SHEET_TEXT_PRIMARY);
        view.setBackground(roundRect(primary ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG, SHEET_BUTTON_RADIUS_DP, 1, primary ? SHEET_CONTROL_STROKE_SELECTED : 0x32FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private TextView createAudioSheetMiniButton(String label, boolean primary, Runnable action) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(SHEET_TEXT_PRIMARY);
        view.setBackground(roundRect(primary ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG, SHEET_BUTTON_RADIUS_DP, 1, primary ? SHEET_CONTROL_STROKE_SELECTED : SHEET_CONTROL_STROKE));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private ImageView createAudioSheetIconButton(int resId, Runnable action) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setColorFilter(SHEET_TEXT_SECONDARY);
        view.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(12), ResUtil.dp2px(12), ResUtil.dp2px(12));
        view.setBackground(roundRect(0x16FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x32FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private ImageView createAudioSheetInlineIconButton(int resId, Runnable action) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setColorFilter(SHEET_TEXT_SECONDARY);
        view.setPadding(ResUtil.dp2px(9), ResUtil.dp2px(9), ResUtil.dp2px(9), ResUtil.dp2px(9));
        view.setBackground(roundRect(0x10FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x22FFFFFF));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private LinearLayout.LayoutParams audioSheetButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(108), ViewGroup.LayoutParams.MATCH_PARENT);
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(10);
        return params;
    }

    private LinearLayout.LayoutParams audioSheetSmallButtonParams() {
        return new LinearLayout.LayoutParams(ResUtil.dp2px(78), ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private LinearLayout.LayoutParams audioSheetMiniButtonParams(int widthDp, boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(widthDp), ResUtil.dp2px(32));
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(6);
        return params;
    }

    private LinearLayout createSegmentedControl(String[] labels, int selectedIndex, SegmentClickHandler handler) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ResUtil.dp2px(2), ResUtil.dp2px(2), ResUtil.dp2px(2), ResUtil.dp2px(2));
        row.setBackground(roundRect(0x12FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x24FFFFFF));
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            boolean selected = index == selectedIndex;
            TextView item = createAudioSheetText(labels[i], 13, true);
            item.setGravity(Gravity.CENTER);
            item.setSingleLine(true);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setPadding(ResUtil.dp2px(6), 0, ResUtil.dp2px(6), 0);
            item.setTextColor(selected ? SHEET_TEXT_PRIMARY : 0xE6FFFFFF);
            item.setBackground(roundRect(selected ? SHEET_CONTROL_BG_SELECTED : 0x00000000, SHEET_SEGMENT_RADIUS_DP, 0, 0));
            item.setOnClickListener(v -> handler.onClick(index));
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        return row;
    }

    private interface SegmentClickHandler {
        void onClick(int index);
    }

    private GradientDrawable audioSheetItemBackground(boolean selected) {
        return roundRect(selected ? SHEET_CONTROL_BG_SELECTED : 0x00000000, SHEET_BUTTON_RADIUS_DP, selected ? 1 : 0, selected ? SHEET_CONTROL_STROKE_SELECTED : 0);
    }

    private void styleAudioSheetInput(TextInputLayout layout, String hint) {
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(0x14FFFFFF);
        layout.setBoxStrokeColor(0x66FFFFFF);
        layout.setDefaultHintTextColor(ColorStateList.valueOf(0xA6FFFFFF));
        layout.setHintTextColor(ColorStateList.valueOf(0xD9FFFFFF));
        layout.setHint(hint);
    }

    private GradientDrawable roundRect(int color, int radiusDp, int strokeDp, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(ResUtil.dp2px(radiusDp));
        if (strokeDp > 0) drawable.setStroke(ResUtil.dp2px(strokeDp), strokeColor);
        return drawable;
    }

    private GradientDrawable audioDrawerBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, audioGlassColors());
        drawable.setCornerRadius(ResUtil.dp2px(18));
        drawable.setStroke(ResUtil.dp2px(1), 0x66FFFFFF);
        return drawable;
    }

    private GradientDrawable audioSheetGlassBackground() {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR, audioGlassColors());
        float radius = ResUtil.dp2px(22);
        drawable.setCornerRadii(new float[]{radius, radius, radius, radius, 0, 0, 0, 0});
        drawable.setStroke(ResUtil.dp2px(1), 0x66FFFFFF);
        return drawable;
    }

    private int[] audioGlassColors() {
        return new int[]{0xB22F315E, 0x96282955, 0x82303463};
    }

    private LinearLayout.LayoutParams audioSheetTopParams(int topDp, int heightDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(heightDp));
        params.topMargin = ResUtil.dp2px(topDp);
        return params;
    }

    private LinearLayout.LayoutParams audioSheetWrapTopParams(int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = ResUtil.dp2px(topDp);
        return params;
    }

    private int audioQueueContentHeight(int tab) {
        if (tab == AUDIO_QUEUE_TAB_SEARCH) {
            int max = isLandscapeAudioSheet() ? audioDrawerListMaxHeight() : ResUtil.getScreenHeight(this) * (ResUtil.isLand(this) ? 32 : 28) / 100;
            int desired = isLandscapeAudioSheet() ? 320 : ResUtil.isLand(this) ? 150 : 170;
            return Math.max(ResUtil.dp2px(126), Math.min(ResUtil.dp2px(desired), max));
        }
        int max = isLandscapeAudioSheet() ? audioDrawerListMaxHeight() : ResUtil.getScreenHeight(this) * (ResUtil.isLand(this) ? 46 : 56) / 100;
        Flag flag = getFlag();
        int count = flag == null ? 1 : Math.max(1, Math.min(isLandscapeAudioSheet() ? 12 : 8, flag.getEpisodes().size()));
        int desired = 8 + count * 46;
        return Math.max(ResUtil.dp2px(102), Math.min(ResUtil.dp2px(desired), max));
    }

    private int lyricsResultSheetHeight(int count) {
        if (isLandscapeAudioSheet()) {
            int rows = Math.max(1, Math.min(7, count));
            return Math.max(ResUtil.dp2px(126), Math.min(ResUtil.dp2px(rows * 64 + 8), audioDrawerListMaxHeight()));
        }
        int rows = Math.max(1, Math.min(3, count));
        return ResUtil.dp2px(rows * 64 + 8);
    }

    private int karaokeTrackResultSheetHeight(int count) {
        if (isLandscapeAudioSheet()) {
            int rows = Math.max(1, Math.min(5, count));
            return Math.max(ResUtil.dp2px(160), Math.min(ResUtil.dp2px(rows * 82 + 8), audioDrawerListMaxHeight()));
        }
        int rows = Math.max(1, Math.min(3, count));
        return ResUtil.dp2px(rows * 82 + 8);
    }

    private void showAudioSheet(BottomSheetDialog dialog) {
        showAudioSheet(dialog, true);
    }

    private void showAudioSheet(BottomSheetDialog dialog, boolean draggable) {
        showAudioSheet(dialog, draggable, false);
    }

    private void showAudioSheet(BottomSheetDialog dialog, boolean draggable, boolean drawerAtStart) {
        if (isLandscapeAudioSheet()) {
            showAudioDrawerSheet(dialog, drawerAtStart);
            return;
        }
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundColor(Color.TRANSPARENT);
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setDraggable(draggable);
        });
        dialog.show();
        applyAudioSheetWindowGlass(dialog);
    }

    private void showCompactPlaybackSheet(BottomSheetDialog dialog) {
        showCompactPlaybackSheet(dialog, true);
    }

    private void showCompactPlaybackSheet(BottomSheetDialog dialog, boolean draggable) {
        showCompactPlaybackSheet(dialog, draggable, false);
    }

    private void showCompactPlaybackSheet(BottomSheetDialog dialog, boolean draggable, boolean drawerAtStart) {
        showAudioSheet(dialog, draggable, drawerAtStart);
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void showAudioDrawerSheet(BottomSheetDialog dialog, boolean atStart) {
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundColor(Color.TRANSPARENT);
            int height = audioDrawerHeight();
            int bottomMargin = audioDrawerBottomMargin();
            ViewGroup.LayoutParams raw = sheet.getLayoutParams();
            raw.width = audioDrawerWidth();
            raw.height = height;
            if (raw instanceof CoordinatorLayout.LayoutParams params) {
                params.gravity = (atStart ? Gravity.START : Gravity.END) | Gravity.BOTTOM;
                params.setMargins(atStart ? ResUtil.dp2px(16) : 0, mStatusBarInset + ResUtil.dp2px(16), atStart ? 0 : ResUtil.dp2px(16), bottomMargin);
            } else if (raw instanceof ViewGroup.MarginLayoutParams params) {
                params.setMargins(atStart ? ResUtil.dp2px(16) : 0, mStatusBarInset + ResUtil.dp2px(16), atStart ? 0 : ResUtil.dp2px(16), bottomMargin);
            }
            sheet.setLayoutParams(raw);
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setFitToContents(false);
            behavior.setExpandedOffset(Math.max(0, ResUtil.getScreenHeight(this) - height - bottomMargin));
            behavior.setPeekHeight(height);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setDraggable(false);
        });
        dialog.show();
        applyAudioSheetWindowGlass(dialog);
    }

    private void applyAudioSheetWindowGlass(BottomSheetDialog dialog) {
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
    }

    private int audioDrawerWidth() {
        return clamp(Math.round(ResUtil.getScreenWidth(this) * 0.42f), ResUtil.dp2px(380), ResUtil.dp2px(560));
    }

    private int audioDrawerHeight() {
        int screenHeight = ResUtil.getScreenHeight(this);
        int topMargin = mStatusBarInset + ResUtil.dp2px(16);
        int bottomMargin = audioDrawerBottomMargin();
        int max = Math.max(ResUtil.dp2px(320), screenHeight - topMargin - bottomMargin);
        return clamp(Math.round(screenHeight * 0.84f), ResUtil.dp2px(320), max);
    }

    private int audioDrawerBottomMargin() {
        return ResUtil.dp2px(16) + mEpisodeBottomInset;
    }

    private int audioDrawerListMaxHeight() {
        return Math.max(ResUtil.dp2px(126), audioDrawerHeight() - ResUtil.dp2px(88));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void showLyricsSettingsSheet(BottomSheetDialog dialog) {
        showCompactPlaybackSheet(dialog, false, true);
    }

    private void showAudioBackgroundSheet(BottomSheetDialog dialog) {
        showAudioSheet(dialog);
        Window window = dialog.getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        params.dimAmount = 0f;
        window.setAttributes(params);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
    }

    private void updateLyricsResultList(String[] labels) {
        if (mLyricsResultList == null) return;
        mLyricsResultList.removeAllViews();
        int selected = getLyricsSelectedIndex();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = createLyricsResultItem(labels[i], i == selected, () -> {
                if (index >= 0 && index < mLyricsSearchResults.size()) applyLyrics(mLyricsSearchResults.get(index));
            });
            mLyricsResultList.addView(item, lyricsResultItemParams(i == 0));
        }
    }

    private void updateLyricsResultSheetHeight(int count) {
        if (mLyricsResultList == null) return;
        if (!(mLyricsResultList.getParent() instanceof View scroll)) return;
        ViewGroup.LayoutParams params = scroll.getLayoutParams();
        int height = lyricsResultSheetHeight(count);
        if (params != null && params.height != height) {
            params.height = height;
            scroll.setLayoutParams(params);
        }
        scroll.requestLayout();
        mLyricsResultList.requestLayout();
        if (mLyricsResultDialog == null) return;
        FrameLayout sheet = mLyricsResultDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        sheet.requestLayout();
        sheet.post(() -> {
            if (mLyricsResultDialog == null || !mLyricsResultDialog.isShowing()) return;
            FrameLayout current = mLyricsResultDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (current == null) return;
            BottomSheetBehavior.from(current).setState(BottomSheetBehavior.STATE_EXPANDED);
        });
    }

    private TextView createLyricsResultItem(String label, boolean selected, Runnable action) {
        TextView item = createAudioSheetText(label, 15, false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        item.setSingleLine(false);
        item.setMaxLines(2);
        item.setLineSpacing(ResUtil.dp2px(2), 1.0f);
        item.setTextColor(selected ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
        item.setBackground(lyricsResultItemBackground(selected));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private LinearLayout.LayoutParams lyricsResultItemParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(58));
        params.topMargin = ResUtil.dp2px(first ? 8 : 6);
        return params;
    }

    private TextView createKaraokeTrackResultItem(String label, Runnable action) {
        TextView item = createAudioSheetText(label, 14, false);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        item.setSingleLine(false);
        item.setMaxLines(3);
        item.setLineSpacing(ResUtil.dp2px(2), 1.0f);
        item.setTextColor(Color.WHITE);
        item.setBackground(lyricsResultItemBackground(false));
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private GradientDrawable lyricsResultItemBackground(boolean selected) {
        return roundRect(selected ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG_SUBTLE, SHEET_BUTTON_RADIUS_DP, 1, selected ? SHEET_CONTROL_STROKE_SELECTED : SHEET_CONTROL_STROKE);
    }

    private void applyLyrics(LyricsResult result) {
        if (mLyrics == null || service() == null) return;
        mLyricsSearchSeq++;
        mInlineLyrics = "";
        mLyrics.apply(player(), result, true, applied -> {
            if (applied != null) {
                mLyricsSelectedResultKey = getLyricsResultKey(applied);
            }
            updateLyricsResultSelection();
            Notify.show(applied == null ? getString(R.string.player_lyrics_not_found) : getString(R.string.player_lyrics_loaded, applied.getSource()));
        });
    }

    private void updateLyricsResultSelection() {
        if (mLyricsSearchResults == null) return;
        String[] labels = new String[mLyricsSearchResults.size()];
        for (int i = 0; i < mLyricsSearchResults.size(); i++) labels[i] = getLyricsResultLabel(mLyricsSearchResults.get(i));
        updateLyricsResultList(labels);
    }

    private int getLyricsSelectedIndex() {
        if (TextUtils.isEmpty(mLyricsSelectedResultKey) || mLyricsSearchResults == null) return -1;
        for (int i = 0; i < mLyricsSearchResults.size(); i++) {
            if (TextUtils.equals(mLyricsSelectedResultKey, getLyricsResultKey(mLyricsSearchResults.get(i)))) return i;
        }
        return -1;
    }

    private String getLyricsResultLabel(LyricsResult result) {
        String title = TextUtils.isEmpty(result.getTrackName()) ? getString(R.string.player_lyrics_unknown) : result.getTrackName();
        String artist = TextUtils.isEmpty(result.getArtistName()) ? getString(R.string.player_lyrics_unknown) : result.getArtistName();
        String type = result.hasWordTiming() ? getString(R.string.player_lyrics_word) : result.isSynced() ? getString(R.string.player_lyrics_synced) : getString(R.string.player_lyrics_plain);
        return getString(R.string.player_lyrics_result_item, result.getSource(), type, result.getScore(), title, artist);
    }

    private String getLyricsResultKey(LyricsResult result) {
        if (result == null) return "";
        return TextUtils.join("|", new String[]{
                String.valueOf(result.getSource()),
                String.valueOf(result.getTrackName()),
                String.valueOf(result.getArtistName()),
                String.valueOf(Math.round(result.getDurationMs() / 1000.0)),
                String.valueOf(result.hasWordTiming()),
                String.valueOf(result.getLyrics() == null ? 0 : result.getLyrics().hashCode())
        });
    }

    private void clearLyrics() {
        if (mLyrics != null) mLyrics.clear();
    }

    private void clearKaraokeState() {
        mKaraokeResultShown = false;
        mPendingKaraokeResult = null;
        mKaraokeResultAction = KARAOKE_RESULT_ACTION_NONE;
        if (mViewModel != null) mViewModel.clearKaraokeResult();
        if (mKaraokeResultDialog != null) {
            mSuppressKaraokeResultAction = true;
            mKaraokeResultDialog.dismiss();
            mSuppressKaraokeResultAction = false;
            mKaraokeResultDialog = null;
        }
        if (mKaraoke != null) mKaraoke.clear();
    }

    private void dismissLyricsResultDialog() {
        if (mLyricsResultDialog == null) return;
        mLyricsResultDialog.dismiss();
        mLyricsResultDialog = null;
        mLyricsResultList = null;
    }

    private void setDetailLyrics(String text) {
        mDetailLyrics = getTimedLyrics(text);
        mInlineLyrics = mDetailLyrics;
    }

    private void setPlaybackLyrics(String text) {
        String lyrics = getTimedLyrics(text);
        if (!TextUtils.isEmpty(lyrics)) mInlineLyrics = lyrics;
    }

    private String getTimedLyrics(String text) {
        return LyricsController.hasTimedLyrics(text) ? text : "";
    }

    private boolean showInlineLyrics() {
        if (TextUtils.isEmpty(mInlineLyrics) || !LyricsController.hasTimedLyrics(mInlineLyrics)) return false;
        String title = getAudioStageTitle();
        String artist = getAudioStageArtist(title);
        String signature = getHistoryKey() + "|" + getEpisode().getName();
        return mLyrics.setInlineLyrics(signature, title, artist, mInlineLyrics, player().getDuration(), player().getPosition());
    }

    private boolean isMusicLike() {
        Flag current = getFlag();
        String flag = current == null ? "" : current.getShow();
        Site site = getSite();
        String text = (getKey() + " " + (site == null ? "" : site.getKey()) + " " + (site == null ? "" : site.getName()) + " " + flag + " " + getName());
        return LyricsController.isMusicLikeText(text);
    }

    private String getLyricsArtist(String title) {
        return getArtistFromEpisode(title, getEpisode().getName());
    }

    private String getArtistFromEpisode(String title, String episode) {
        String name = Objects.toString(title, "").trim();
        String value = Objects.toString(episode, "").trim();
        if (name.isEmpty() || value.isEmpty() || TextUtils.equals(name, value)) return "";
        for (String separator : new String[]{" - ", " – ", " — ", "-"}) {
            if (value.startsWith(name + separator) && value.length() > name.length() + separator.length()) {
                return value.substring(name.length() + separator.length()).trim();
            }
            if (value.endsWith(separator + name) && value.length() > name.length() + separator.length()) {
                return value.substring(0, value.length() - name.length() - separator.length()).trim();
            }
        }
        return value;
    }

    @Override
    protected void onTitlesChanged() {
        setTitleVisible();
        refreshControlDialog();
    }

    @Override
    protected void onError(String msg) {
        recordPlayHealth(false, msg);
        subtitlePlaybackSession.stop(this);
        mBinding.swipeLayout.setEnabled(true);
        Track.delete(player().getKey());
        mClock.setCallback(null);
        clearLyrics();
        clearKaraokeState();
        player().resetTrack();
        player().reset();
        player().stop();
        showError(msg);
        startFlow();
    }

    @Override
    protected void onReload(String msg) {
        if (PlayerManager.RELOAD_LUT_WARMUP.equals(msg)) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("lut-ui", "auto refresh after lut warmup playback failure key=%s episode=%s", getKey(), getEpisode() == null ? null : getEpisode().getName());
            onRefresh();
            return;
        }
        super.onReload(msg);
    }

    @Override
    protected void onReclaim() {
        Result result = mViewModel.getPlayer().getValue();
        if (result != null) setPlayer(result);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                // 播放器缓冲时始终显示加载动画（转圈+流量），不管 TMDB 内容是否加载完成
                showProgress();
                break;
            case Player.STATE_READY:
                if (mPendingKaraokeResult == null) mKaraokeResultShown = false;
                recordPlayHealth(true, "");
                showPlaybackContent();
                boolean pendingResumeSeekApplied = applyPendingResumeSeek();
                checkControl();
                refreshLyrics();
                player().reset();
                applyShortDramaMode();
                requestIntroSkipPlan();
                if (!pendingResumeSeekApplied) applyAutoIntroSkip();
                setAdFeedbackVisible(); // 播放地址确定后按格式刷新"有广告"按钮
                recordAdFeedbackHost(); // 播放地址已确定，记入本站域名基线
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                updatePlayControl(false, syncPiPForPlaybackMode());
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        syncLyricsPlaybackState(isPlaying);
        syncKaraokePosition();
        boolean audioMode = syncPiPForPlaybackMode();
        if (isPlaying || isPaused()) updatePlayControl(isPlaying, audioMode);
    }

    private void updatePlayControl(boolean isPlaying, boolean audioMode) {
        if (!audioMode) mPiP.update(this, isPlaying);
        mBinding.control.play.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        checkAudioPlayImg(isPlaying);
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        logVideoFrame("onSizeChanged before size=" + size.width + "x" + size.height);
        mPiP.update(this, size.width, size.height, getScale());
        setSizeText();
        updateVideoHeight();
        applyResizeMode(getScale());
        checkOrientation();
        logVideoFrame("onSizeChanged after size=" + size.width + "x" + size.height);
    }

    @Override
    protected void onSurfaceAttached() {
        logVideoFrame("onSurfaceAttached before");
        applyResizeMode(getScale());
        logVideoFrame("onSurfaceAttached after");
    }

    private void hideSeekProgressIfReady() {
        if (service() == null || player() == null || player().isReleased() || player().isEmpty() || !isOwner() || player().getPlaybackState() != Player.STATE_READY) return;
        showPlaybackContent();
    }

    /**
     * 加载圈的兜底收口。
     *
     * <p>圈只在 {@code STATE_READY} 分支被收（onStateChanged），而那条回调受 isOwner() 把关。
     * 归属判定一旦因任何原因失配，圈就永久留在屏上——画面在动、圈不走。这里不依赖归属，
     * 直接读播放器状态：已在播且已 READY 就收圈。要求 {@code !isEmpty()}，避免详情尚未加载完
     * （播放器还空着）时把详情页自己的加载态误收。
     *
     * <p>挂在 mR2（网速刷新，圈可见时每秒一跳）上，圈不可见时该循环本就已停，无额外开销。
     */
    private void hidePlaybackProgressIfStale() {
        if (mBinding.progress.getRoot().getVisibility() != View.VISIBLE) return;
        if (service() == null || player() == null || player().isReleased() || player().isEmpty()) return;
        if (!isOwner()) return;
        if (player().getPlaybackState() != Player.STATE_READY) return;
        showPlaybackContent();
    }

    @Override
    protected void onSeekStarted() {
        showProgress();
        App.removeCallbacks(mSeekProgressFallback);
        App.post(mSeekProgressFallback, 500);
    }

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).player(player()).search(() -> SubtitleManualSearchDialog.show(this, subtitlePlaybackSession, this)).show(this);
        hideControl();
    }

    private void showSubtitleSearch() {
        SubtitleManualSearchDialog.show(this, subtitlePlaybackSession, this);
    }

    @Override
    public void onTimeChanged(long time) {
        android.util.Log.d("VideoActivity", "onTimeChanged: isOwner=" + isOwner() + " mHistory=" + (mHistory != null));
        if (!isOwner() || mHistory == null) return;
        long position, duration;
        mHistory.setCreateTime(time);
        updatePlaybackHistoryPosition();
        syncCurrentAudioPlaylistMetadata();
        syncKaraokePosition();
        if (mLyrics != null) mLyrics.update(player());
        if (mKaraoke != null) mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());
        position = mHistory.getPosition();
        duration = mHistory.getDuration();
        android.util.Log.d("VideoActivity", "onTimeChanged: position=" + position + " duration=" + duration + " canSave=" + mHistory.canSave());
        PlaybackEventCollector.get().onProgress(mHistory, player());
        if (mHistory.canSave() && mHistory.canSync()) syncHistory();
        if (applyAutoIntroSkip()) return;
        if (mHistory.isEndingReached(position, duration)) {
            checkEnded(false);
        }
    }

    private void updatePlaybackHistoryPosition() {
        if (mHistory == null || tmdbHistoryResumePending) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position > 0) mHistory.setPosition(position);
        if (duration > 0) mHistory.setDuration(duration);
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail(true);
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD_CORE) {
            if (!isCurrentVodEvent(event.getVod())) {
                SpiderDebug.log("tmdb-mobile", "drop stale vod event current=%s/%s event=%s/%s", getKey(), getId(), event.getVod() == null ? "" : event.getVod().getSiteKey(), event.getVod() == null ? "" : event.getVod().getId());
                return;
            }
            updateVod(event.getVod());
            maybeShowPendingTmdbSeasonDialog();
        }
        else if (event.getType() == RefreshEvent.Type.VOD_RECOMMENDATIONS) {
            if (!isCurrentVodEvent(event.getVod())) return;
            refreshTmdbRecommendations();
        }
        else if (event.getType() == RefreshEvent.Type.VOD_PERSONAL) {
            if (!isCurrentVodEvent(event.getVod())) return;
            refreshTmdbPersonalRecommendations();
        }
        else if (event.getType() == RefreshEvent.Type.VOD_EPISODE_TITLES) {
            if (!isCurrentVodEvent(event.getVod())) return;
            mergeTmdbEpisodeMetadata(event.getVod());
            refreshTmdbEpisodeTitles();
        }
        else if (event.getType() == RefreshEvent.Type.VOD_RELATED_VIDEOS) {
            if (!isCurrentVodEvent(event.getVod())) return;
            refreshTmdbRelatedVideos();
        }
        else if (event.getType() == RefreshEvent.Type.HISTORY) refreshPersonalRecommendationsForHistory();
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) {
            player().reloadDanmaku(Danmaku.from(event.getPath()));
            refreshDanmakuControls();
        }
    }

    private boolean isCurrentVodEvent(Vod item) {
        return VodEventGuard.matches(item, getKey(), getId(), mVod == null ? "" : mVod.getId());
    }


    private void requestIntroSkipPlan() {
        if (!Setting.isIntroSkipEnabled() || player() == null) {
            mIntroSkipPlayback.reset();
            setOpeningEndingText();
            return;
        }
        IntroSkipService.Query query = buildIntroSkipQuery();
        // 切集后 plan 已被 reset，这里先刷一次，避免 query 拿不到时残留上一集的探测值
        setOpeningEndingText();
        if (query == null) return;
        mIntroSkipPlayback.request(query, this::onIntroSkipPlanLoaded);
    }

    private void onIntroSkipPlanLoaded() {
        if (isFinishing() || isDestroyed() || player() == null || player().isReleased() || !isOwner()) return;
        setOpeningEndingText();
        applyAutoIntroSkip();
        preloadAdjacentIntroSkipPlans();
    }

    private boolean applyAutoIntroSkip() {
        if (!Setting.isIntroSkipEnabled() || isFinishing() || isDestroyed()
                || player() == null || player().isReleased() || !isOwner()) return false;
        // notify=true：片尾无处可跳（末集/电影）时至少要有提示，不能静默无反应
        return mIntroSkipPlayback.apply(player(), () -> advanceEpisode(true));
    }

    private IntroSkipService.Query buildIntroSkipQuery() {
        TmdbItem item = getIntroSkipTmdbItem();
        if (item == null || item.getTmdbId() <= 0) return null;
        Episode episode = getEpisode();
        int season = 0;
        int number = 0;
        if (item.isTv()) {
            TmdbEpisode tmdbEpisode = episode == null ? null : episode.getTmdbEpisode();
            season = tmdbEpisode == null ? 1 : tmdbEpisode.getSeasonNumber();
            number = tmdbEpisode == null ? (episode == null ? 0 : episode.getNumber()) : tmdbEpisode.getNumber();
            if (season < 0 || number <= 0) return null;
        }
        long duration = player() == null ? 0 : Math.max(0, player().getDuration());
        return new IntroSkipService.Query(item.getTmdbId(), getIntroSkipImdbId(), item.getMediaType(), season, number, duration);
    }

    /**
     * 预热前后各一集。查询不需要时长（IntroDB 不收，TheIntroDB 可选），所以这里传 0 即可；
     * 缓存只按剧集身份存原始段，等那一集真开播时按其实际时长折算，不会再走网络。
     */
    private void preloadAdjacentIntroSkipPlans() {
        if (!Setting.isIntroSkipEnabled()) return;
        TmdbItem item = getIntroSkipTmdbItem();
        if (item == null || item.getTmdbId() <= 0 || !item.isTv()) return;
        String imdbId = getIntroSkipImdbId();
        Episode current = getEpisode();
        for (int offset : new int[]{1, -1}) {
            Episode neighbour = getAdjacentEpisode(offset);
            // getAdjacentEpisode 越界时会夹回当前集，那样预热就是给本集重发一次请求
            if (neighbour == null || neighbour == current) continue;
            TmdbEpisode tmdbEpisode = neighbour.getTmdbEpisode();
            if (tmdbEpisode == null) continue;
            int season = tmdbEpisode.getSeasonNumber();
            int number = tmdbEpisode.getNumber();
            if (season < 0 || number <= 0) continue;
            IntroSkipService.Query query = new IntroSkipService.Query(item.getTmdbId(), imdbId, item.getMediaType(), season, number, 0);
            mIntroSkipPlayback.preload(query);
        }
    }

    private TmdbItem getIntroSkipTmdbItem() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? getTmdbItem() : item;
    }

    private String getIntroSkipImdbId() {
        JsonObject detail = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbDetail();
        JsonObject externalIds = detail != null && detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) return "";
        return externalIds.get("imdb_id").getAsString();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (isRedirect() || !event.isVod() || mParseAdapter == null) return;
        mParseAdapter.reload();
    }

    /**
     * 猫源开了内嵌设置页：这次点击的本意就是开网页，本页立刻退场。
     *
     * <p>不能等 detail 结果再判定——那份结果可能被主线程堵住好几秒，这段时间里按返回就会
     * 落回本页（空白播放页）。用请求时刻和本次 detail 起始时间比，确认是自己触发的才退。
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onCatWebEvent(com.fongmi.android.tv.event.CatWebEvent event) {
        if (isRedirect() || !event.after(detailStartTime)) return;
        SpiderDebug.log("video-flow", "detail yield to cat webview (event) key=%s id=%s", getKey(), getId());
        finish();
    }

    private boolean applyPendingResumeSeek() {
        if (pendingResumeSeekMs == C.TIME_UNSET || controller() == null) return false;
        long target = pendingResumeSeekMs;
        pendingResumeSeekMs = C.TIME_UNSET;
        tmdbHistoryResumePending = false;
        if (Math.abs(player().getPosition() - target) < 1500) return false;
        controller().seekTo(target);
        return true;
    }

    private void setPosition() {
        pendingResumeSeekMs = C.TIME_UNSET;
        if (mHistory == null) {
            tmdbHistoryResumePending = false;
            return;
        }
        if (mHistory.isNearEnding()) {
            SpiderDebug.log("video-flow", "reset near-end history position=%d duration=%d key=%s", mHistory.getPosition(), mHistory.getDuration(), getHistoryKey());
            mHistory.resetPlaybackPosition();
            syncHistory();
        }
        long position = Math.max(mHistory.getOpening(), mHistory.getPosition());
        if (position <= 0) {
            tmdbHistoryResumePending = false;
            return;
        }
        mIntroSkipPlayback.setResumePosition(position);
        if (player().isIjk()) pendingResumeSeekMs = position;
        else {
            player().seekTo(position);
            tmdbHistoryResumePending = false;
        }
    }

    private void setSpeed() {
        if (mHistory == null) return;
        mBinding.control.action.speed.setText(player().setSpeed(getPlaybackSpeed()));
    }

    private float getPlaybackSpeed() {
        return mHistory == null ? PlayerSetting.getDefaultSpeed() : mHistory.getPlaybackSpeed(PlayerSetting.getDefaultSpeed());
    }

    private void checkOrientation() {
        if (isFullscreen() && !isRotate() && player().isPortrait()) {
            setRequestedOrientation(PlaybackOrientation.getPortraitVideoSizeOrientation());
            setRotate(true);
        } else if (isFullscreen() && isRotate() && player().isLandscape()) {
            setRequestedOrientation(PlaybackOrientation.getLandscapeVideoSizeOrientation());
            setRotate(false);
        }
    }

    private void updateVideoHeight() {
        if (isLand() || isFullscreen() || isInPictureInPictureMode()) return;
        if (mFrameHeight <= 0 || mFrameParams.height == mFrameHeight) return;
        logVideoFrame("updateVideoHeight restore from=" + mFrameParams.height + " to=" + mFrameHeight);
        mFrameParams.height = mFrameHeight;
        mBinding.video.setLayoutParams(mFrameParams);
    }

    private void logVideoFrame(String step) {
        if (mBinding == null) return;
        Log.d(SIZE_TAG, "video " + step
                + " frameParam=" + (mFrameParams == null ? "null" : mFrameParams.width + "x" + mFrameParams.height)
                + " frameHeight=" + mFrameHeight
                + " video=" + viewSize(mBinding.video)
                + " exo=" + viewSize(mBinding.exo)
                + " fullscreen=" + isFullscreen()
                + " land=" + isLand()
                + " scale=" + getScale()
                + " player=" + (service() == null ? "none" : player().getPlayerText()));
    }

    private static String viewSize(View view) {
        if (view == null) return "null";
        return view.getWidth() + "x" + view.getHeight();
    }

    private void checkEnded(boolean notify) {
        checkNext(notify);
    }

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private void setTitleVisible() {
        mBinding.control.action.title.setVisibility(player().haveTitle() ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
    }

    private void setSizeText() {
        String text = player().getSizeText();
        boolean hasTitle = !TextUtils.isEmpty(mBinding.control.title.getText());
        mBinding.control.title.setVisibility(hasTitle ? View.VISIBLE : View.INVISIBLE);
        mBinding.control.size.setText(text);
        mBinding.control.size.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private MediaMetadata buildMetadata() {
        String title = mHistory.getVodName();
        String episode = getEpisode().getName();
        boolean empty = episode.isEmpty() || title.equals(episode);
        String artist = empty ? "" : episode;
        return PlayerManager.buildMetadata(title, artist, mHistory.getVodPic());
    }

    private void setMetadata() {
        player().setMetadata(buildMetadata());
    }

    private void startFlow() {
        if (!PlayerSetting.isAutoChange()) return;
        if (!getSite().isChangeable()) return;
        if (isUseParse()) checkParse();
        else checkFlag();
    }

    private void checkParse() {
        int position = mParseAdapter.getPosition();
        boolean last = position == mParseAdapter.getItemCount() - 1;
        boolean pass = position == 0 || last;
        if (last) initParse();
        if (pass) checkFlag();
        else nextParse(position);
    }

    private void initParse() {
        if (mParseAdapter.isEmpty()) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (!force && !PlayerSetting.isAutoChange()) return;
        if (mQuickAdapter.isEmpty()) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        revealManualSearch = !auto;
        startSearch(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickSearchKeyword = keyword;
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.GONE);
        if (isQuickSearchVisible()) mQuickSearchDialog.clear();
        List<Site> sites = new ArrayList<>();
        for (Site item : VodConfig.get().getSites()) if (isPass(item)) sites.add(item);
        SiteHealthStore.sortSites(sites);
        mViewModel.searchContent(sites, keyword, true);
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        items.removeIf(this::mismatch);
        boolean showQuick = !shouldUseTmdbDetailLayout() && !isQuickSearchVisible();
        mBinding.quick.setVisibility(showQuick ? View.VISIBLE : View.GONE);
        mQuickAdapter.addAll(items);
        if (isQuickSearchVisible()) mQuickSearchDialog.addAll(items);
        if (showQuick && revealManualSearch && !items.isEmpty()) {
            revealManualSearch = false;
            mBinding.quick.post(() -> mBinding.scroll.smoothScrollTo(0, mBinding.quick.getTop()));
        } else if (revealManualSearch && !items.isEmpty()) {
            revealManualSearch = false;
        }
        if (isInitAuto() && PlayerSetting.isAutoChange()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private boolean isQuickSearchVisible() {
        return mQuickSearchDialog != null && mQuickSearchDialog.isActive();
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getId())) return true;
        if (mBroken.contains(item.getId())) return true;
        String keyword = TextUtils.isEmpty(mQuickSearchKeyword) ? mBinding.name.getText().toString() : mQuickSearchKeyword;
        if (isAutoMode()) return !item.getName().equals(keyword);
        else return !item.getName().contains(keyword);
    }

    private void nextParse(int position) {
        Parse parse = mParseAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_parse, parse.getName()));
        onItemClick(parse);
    }

    private void nextFlag(int position) {
        Flag flag = mFlagAdapter.get(position + 1);
        Notify.show(getString(R.string.play_switch_flag, flag.getFlag()));
        onItemClick(flag);
    }

    private void nextSite() {
        if (mQuickAdapter.isEmpty()) return;
        int position = mQuickAdapter.getBestPosition();
        Vod item = mQuickAdapter.get(position);
        Notify.show(getString(R.string.play_switch_site, item.getSiteName()));
        mQuickAdapter.remove(position);
        mBroken.add(getId());
        setInitAuto(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    private void onPaused() {
        controller().pause();
    }

    private void onPlay() {
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
        updateFusionThemeButtonVisibility();
    }

    private boolean isShortDramaSource() {
        Site site = getSite();
        return Setting.isShortDramaSiteEnabled(site == null ? getKey() : site.getKey(), site == null ? "" : site.getName());
    }

    /**
     * 本次播放会话是否按短剧竖屏形态呈现。
     * <p>
     * 换源（onChange -> getDetail）会改写 intent 的 key，进而让 isShortDramaSource() 由 true 变 false，
     * 于是同一个竖屏会话中途退回长视频布局：右侧 dock 被拆、横屏 action 栏露出，
     * 就是用户看到的「换源后样式变了」。会话形态一旦按短剧进入就保持，
     * 直到退出播放页（finishShortDrama / onDestroy）或换到新条目（onNewIntent）为止。
     */
    private boolean isShortDramaSession() {
        if (isShortDramaSource()) shortDramaSession = true;
        return shortDramaSession;
    }

    private boolean isTmdbContentLoaded() {
        return mTmdbContentLoaded;
    }

    private void initTmdbMode() {
        // TMDB 模式：通过全局开关或 Intent 参数启用
        if (!isTmdbSourceEnabled()) return;

        mTmdbUIAdapter = new com.fongmi.android.tv.ui.helper.TmdbUIAdapter(this);
        if (!mTmdbUIAdapter.isReady()) {
            SpiderDebug.log("TMDB 增强已启用，但配置未就绪（需要 API Key）");
            return;
        }
        mTmdbUIAdapter.setPersonalAiUpdateListener(() -> {
            if (mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded() && !mTmdbFallbackToNative) {
                mTmdbHeaderView.refreshPersonalAiRecommendations();
            }
        });

        // 注入 TMDB 风格头部面板
        ViewGroup scrollContainer = (ViewGroup) mBinding.scroll.getChildAt(0);
        mTmdbHeaderView = new com.fongmi.android.tv.ui.custom.TmdbHeaderView(this, scrollContainer);
        mTmdbHeaderView.setDetailThemeMode(getFusionDetailThemeMode());
        mTmdbHeaderView.inflate();
        mTmdbHeaderView.setActionListener(new com.fongmi.android.tv.ui.custom.TmdbHeaderView.ActionListener() {
            @Override
            public void onChangeSource() {
                onChange();
            }

            @Override
            public void onChangeSourceLongClick() {
                onSearchGlobal();
            }

            @Override
            public void onRematch() {
                showManualTmdbMatchDialog();
            }

            @Override
            public void onRematchLongClick() {
                showManualTmdbSeasonDialog();
            }

            @Override
            public void onKeep() {
                VideoActivity.this.onKeep();
            }

            @Override
            public void onDownload() {
                VideoActivity.this.onDownload();
            }
        });

        // 设置图片加载完成监听器
        mTmdbHeaderView.setOnImagesLoadedListener(new com.fongmi.android.tv.ui.custom.TmdbHeaderView.OnImagesLoadedListener() {
            @Override
            public void onImagesLoaded() {
                onTmdbContentReady();
            }
        });

        // 原生增强、原生样式和 Fusion 模式：设置 Backdrop 变化监听器，同步轮播到 contextWall
        if (Setting.isFusionDetailPage() || shouldUseTmdbBackdropSurface()) {
            mTmdbHeaderView.setOnBackdropChangeListener(new com.fongmi.android.tv.ui.custom.TmdbHeaderView.OnBackdropChangeListener() {
                @Override
                public void onBackdropChanged(String imageUrl) {
                    // 将轮播的图片同步到全屏背景（跳过锁定，允许切换）
                    android.util.Log.d("VideoActivity", "接收到 backdrop 变化通知，URL=" + imageUrl);
                    setContextWall(imageUrl, true);
                }
            });
        }

        // TMDB 模式下：隐藏原生详情信息（但保持容器可见，因为 TMDB 内容也在里面）
        setNativeDetailInfoVisible(false);
        mBinding.quick.setVisibility(View.GONE);
        mBinding.search.setVisibility(View.GONE);
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.GONE);  // 隐藏播放器下方的阴影

        if (Setting.isFusionDetailPage()) {
            applyFusionDetailChrome();
        } else if (shouldUseTmdbBackdropSurface()) {
            // 原生增强模式：启用全屏背景
            applyOriginalEnhancedBackdropLayout();
            mBinding.getRoot().setBackgroundColor(enhancedBackdropBaseColor());
            mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);
            mBinding.swipeLayout.setBackgroundColor(Color.TRANSPARENT);
            mBinding.progressLayout.setBackgroundColor(Color.TRANSPARENT);
            if (mBinding.nativeContentContainer != null) {
                mBinding.nativeContentContainer.setBackgroundColor(Color.TRANSPARENT);
            }
            applyTmdbTabletVideoLayoutIfNeeded();
        } else {
            mBinding.scroll.setBackgroundColor(com.fongmi.android.tv.ui.custom.TmdbHeaderView.getThemeBackgroundColor());
            applyTmdbTabletVideoLayoutIfNeeded();
        }

        // 移除 nativeContentContainer 的 padding，避免空白间隔
        if (mBinding.nativeContentContainer != null) mBinding.nativeContentContainer.setPadding(0, 0, 0, 0);
    }

    private void applyFusionDetailChrome() {
        if (mFusionChromeApplied) return;
        RelativeLayout chromeRoot = getFusionChromeRoot();
        if (chromeRoot == null) return;
        mFusionChromeApplied = true;
        applyFusionThemeSurface();

        applyFusionBackdropLayout();

        RelativeLayout.LayoutParams videoParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, ResUtil.dp2px(FUSION_PLAYER_HEIGHT_DP));
        videoParams.addRule(RelativeLayout.BELOW, R.id.statusBar);
        videoParams.setMargins(ResUtil.dp2px(FUSION_PLAYER_SIDE_MARGIN_DP), ResUtil.dp2px(FUSION_PLAYER_TOP_MARGIN_DP), ResUtil.dp2px(FUSION_PLAYER_SIDE_MARGIN_DP), 0);
        mFrameParams = videoParams;
        mBinding.video.setLayoutParams(videoParams);
        setFusionPlayerBottomGap();
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.BLACK);
        background.setCornerRadius(ResUtil.dp2px(20));
        mBinding.video.setBackground(background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) mBinding.video.setClipToOutline(true);

        ensureFusionThemeButton();
        updateFusionThemeButton();
    }

    private void applyFusionBackdropLayout() {
        RelativeLayout.LayoutParams wallParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        mBinding.contextWall.setLayoutParams(wallParams);
        mBinding.statusBar.setBackgroundColor(Color.TRANSPARENT);
        if (mBinding.videoContextScrim != null) {
            RelativeLayout.LayoutParams scrimParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            mBinding.videoContextScrim.setLayoutParams(scrimParams);
            mBinding.videoContextScrim.setVisibility(View.VISIBLE);
            applyContextWallScrimTheme();
        }
    }

    /**
     * 原生增强靠全屏 backdrop 当背景，但 contextWall 初始是 gone、图也要等网络。
     * root 若留 TRANSPARENT，这段空窗期会直接露出 Material3 动态取色的窗口底色（设备上可能是紫色）。
     * 给 root 垫一层不透明底色即可：contextWall 是首个子视图、绘制在内容之下，
     * 图到达后它自己就盖住这层底色，不需要再改回 TRANSPARENT。
     */
    private int enhancedBackdropBaseColor() {
        return isTmdbPlaybackLightTheme() ? 0xFFF3F6F9 : 0xFF0F141A;
    }

    private void applyOriginalEnhancedBackdropLayout() {
        // 设置全屏背景布局（类似 Fusion）
        RelativeLayout.LayoutParams wallParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_START);
        wallParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        mBinding.contextWall.setLayoutParams(wallParams);
        mBinding.statusBar.setBackgroundColor(Color.TRANSPARENT);

        // 设置遮罩层为全屏
        if (mBinding.videoContextScrim != null) {
            RelativeLayout.LayoutParams scrimParams = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_START);
            scrimParams.addRule(RelativeLayout.ALIGN_PARENT_END);
            mBinding.videoContextScrim.setLayoutParams(scrimParams);
            mBinding.videoContextScrim.setBackgroundResource(R.drawable.shape_video_context_scrim);
            mBinding.videoContextScrim.setVisibility(View.VISIBLE);
        }
    }

    private void setFusionPlayerBottomGap() {
        ensureFusionPlayerBottomSpacer();
        if (mFusionPlayerBottomSpacer == null) return;
        ViewGroup.LayoutParams params = mBinding.swipeLayout.getLayoutParams();
        if (!(params instanceof RelativeLayout.LayoutParams layoutParams)) return;
        layoutParams.addRule(RelativeLayout.BELOW, mFusionPlayerBottomSpacer.getId());
        layoutParams.topMargin = 0;
        mBinding.swipeLayout.setLayoutParams(layoutParams);
    }

    private void ensureFusionPlayerBottomSpacer() {
        if (mFusionPlayerBottomSpacer != null) return;
        RelativeLayout chromeRoot = getFusionChromeRoot();
        if (chromeRoot == null) return;
        mFusionPlayerBottomSpacer = new View(this);
        mFusionPlayerBottomSpacer.setId(View.generateViewId());
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, ResUtil.dp2px(FUSION_PLAYER_BOTTOM_GAP_DP));
        params.addRule(RelativeLayout.BELOW, R.id.video);
        chromeRoot.addView(mFusionPlayerBottomSpacer, params);
    }

    private void ensureFusionThemeButton() {
        if (mFusionThemeButton != null) return;
        if (mTmdbHeaderView == null || mTmdbHeaderView.getHeaderRoot() == null) return;
        mFusionThemeButton = mTmdbHeaderView.getHeaderRoot().findViewById(R.id.tmdbThemeToggle);
        if (mFusionThemeButton != null) {
            mFusionThemeButton.setOnClickListener(view -> cycleFusionTheme());
        }
        updateFusionThemeButton();
    }

    private RelativeLayout getFusionChromeRoot() {
        if (mBinding.video.getParent() instanceof RelativeLayout parent) return parent;
        if (mBinding.getRoot() instanceof RelativeLayout root) return root;
        return null;
    }

    private void cycleFusionTheme() {
        int theme = isFusionLightTheme() ? 2 : 1;
        Setting.putTmdbDetailTheme(theme);
        getIntent().putExtra(EXTRA_TMDB_DETAIL_THEME, theme);
        applyFusionThemeSurface();
        updateFusionThemeButton();
        if (mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            mTmdbHeaderView.bind(mTmdbUIAdapter);
            styleTmdbSourceInFlagTitle();
            applyTmdbPlaybackControlColors();
            applyFusionPlayerBelowSpacing();
        }
    }

    private void applyFusionPlayerBelowSpacing() {
        if (!Setting.isFusionDetailPage() || mTmdbHeaderView == null || mTmdbHeaderView.getHeaderRoot() == null) return;
        View actions = mTmdbHeaderView.getHeaderRoot().findViewById(R.id.tmdbActionsScroll);
        if (actions == null || !(actions.getLayoutParams() instanceof ViewGroup.MarginLayoutParams params)) return;
        params.topMargin = 0;
        actions.setLayoutParams(params);
    }

    private void applyFusionThemeSurface() {
        boolean light = isFusionLightTheme();
        // 顺序有意义：TMDB 未匹配回退要透出 App 壁纸，必须优先于 backdrop surface 判定。
        // 其次是原生增强(backdrop surface)：垫不透明底色而非 TRANSPARENT，否则 backdrop 图到达前
        // 会露出窗口动态取色。contextWall 是首个子视图、绘制在 root 底色之上，图到达后自然盖住。
        int base = mTmdbFallbackToNative ? Color.TRANSPARENT
                : shouldUseTmdbBackdropSurface() ? enhancedBackdropBaseColor()
                : light ? 0xFFF3F6F9 : 0xFF0F141A;
        mBinding.getRoot().setBackgroundColor(base);
        mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);
        mBinding.swipeLayout.setBackgroundColor(Color.TRANSPARENT);
        mBinding.progressLayout.setBackgroundColor(Color.TRANSPARENT);
        if (mBinding.nativeContentContainer != null) mBinding.nativeContentContainer.setBackgroundColor(Color.TRANSPARENT);
        applyContextWallScrimTheme();
        syncFusionHeaderTheme();
        if (mFlagAdapter != null) mFlagAdapter.setTmdbLight(isTmdbPlaybackLightTheme());
        applyFusionNativeTextColors();
        styleTmdbSourceInFlagTitle();
        applyTmdbPlaybackControlColors();
    }

    private void applyContextWallScrimTheme() {
        if (mBinding.videoContextScrim == null) return;
        if (mTmdbFallbackToNative) {
            mBinding.videoContextScrim.setBackgroundResource(R.drawable.shape_video_context_scrim);
            mBinding.videoContextScrim.setVisibility(View.VISIBLE);
            return;
        }
        boolean light = Setting.isFusionDetailPage() && isFusionLightTheme();
        mBinding.videoContextScrim.setBackgroundResource(light ? R.drawable.shape_video_context_scrim_light : R.drawable.shape_video_context_scrim);
    }

    private void applyFusionNativeTextColors() {
        if ((!Setting.isFusionDetailPage() && !mTmdbFallbackToNative) || mBinding.nativeContentContainer == null) return;
        tintFusionNativeTextTree(mBinding.nativeContentContainer, !mTmdbFallbackToNative && isFusionLightTheme());
    }

    private void tintFusionNativeTextTree(View view, boolean light) {
        if (view instanceof RecyclerView) return;
        if (view instanceof TextView textView) {
            textView.setTextColor(light ? 0xFF12202D : 0xFFFFFFFF);
            textView.setLinkTextColor(light ? 0xFF1D8F5A : Color.WHITE);
            if (light) textView.setShadowLayer(0, 0, 0, 0);
            else textView.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), 0xB0000000);
        }
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintFusionNativeTextTree(group.getChildAt(i), light);
    }

    private void applyTmdbPlaybackControlColors() {
        if (mTmdbHeaderView == null || mTmdbHeaderView.getHeaderRoot() == null) return;
        boolean light = !shouldUseTmdbBackdropSurface() && isTmdbPlaybackLightTheme();
        int color = shouldUseTmdbBackdropSurface() ? Color.WHITE : tmdbPlaybackControlColor(light);
        if (mFlagAdapter != null) mFlagAdapter.setTmdbLight(light);
        tintFusionPlaybackTextTree(mBinding.flagTitleBar, color, light);
        tintFusionPlaybackTextTree(mBinding.episodeTitleBar, color, light);
        tintFusionPlaybackText(mBinding.qualityText, color, light);
        tintFusionPlaybackIcon(mBinding.reverse, color);
        if (mBinding.episodeFileName != null) tintFusionPlaybackIcon(mBinding.episodeFileName, color);
        tintFusionPlaybackIcon(mBinding.episodeViewMode, color);
        tintFusionPlaybackIcon(mBinding.more, color);
        boolean playerOverlay = isFullscreen() || mBinding.control.action.getRoot().getParent() == mBinding.control.bottom;
        tintFusionPlaybackTextTree(mBinding.control.action.getRoot(), playerOverlay ? Color.WHITE : color, !playerOverlay && light);
    }

    private boolean isTmdbPlaybackLightTheme() {
        return mTmdbHeaderView == null ? isFusionLightTheme() : mTmdbHeaderView.isCurrentDetailLightTheme();
    }

    private int tmdbPlaybackControlColor(boolean light) {
        if (mTmdbHeaderView != null) return mTmdbHeaderView.getFusionSectionTitleColor();
        return light ? 0xFF12202D : Color.WHITE;
    }

    private void tintFusionPlaybackTextTree(View view, int color, boolean light) {
        if (view == null || view instanceof RecyclerView) return;
        if (view instanceof TextView textView) tintFusionPlaybackText(textView, color, light);
        if (!(view instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) tintFusionPlaybackTextTree(group.getChildAt(i), color, light);
    }

    private void tintFusionPlaybackText(TextView textView, int color, boolean light) {
        if (textView == null) return;
        textView.setTextColor(color);
        textView.setLinkTextColor(light ? 0xFF1D8F5A : Color.WHITE);
        if (light) textView.setShadowLayer(0, 0, 0, 0);
        else textView.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), 0xB0000000);
    }

    private void tintFusionPlaybackIcon(View view, int color) {
        if (view instanceof ImageView imageView) imageView.setColorFilter(color);
    }

    private void updateFusionThemeButton() {
        if (mFusionThemeButton == null) return;
        mFusionThemeButton.setText(fusionThemeLabel());
        updateFusionThemeButtonVisibility();
    }

    private void updateFusionThemeButtonVisibility() {
        if (mFusionThemeButton == null) return;
        boolean show = DetailThemeVisibility.showFusionThemeButton(Setting.isFusionDetailPage(), isFullscreen(), isInPictureInPictureMode());
        mFusionThemeButton.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private String fusionThemeLabel() {
        return isFusionLightTheme() ? getString(R.string.detail_theme_light) : getString(R.string.detail_theme_dark);
    }

    private boolean isFusionLightTheme() {
        return Setting.resolveTmdbDetailLightTheme(getFusionDetailThemeMode(), isSystemNight());
    }

    private int getFusionDetailThemeMode() {
        return Setting.getTmdbDetailTheme() == 1 ? 1 : 2;
    }

    private void syncFusionHeaderTheme() {
        if (mTmdbHeaderView != null) mTmdbHeaderView.setDetailThemeMode(getFusionDetailThemeMode());
    }

    private boolean isSystemNight() {
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private void hideTmdbHeader() {
        if (mTmdbHeaderView == null || mTmdbHeaderView.getHeaderRoot() == null) return;
        mTmdbHeaderView.getHeaderRoot().setVisibility(View.GONE);
    }

    private void scheduleTmdbDetailFallback() {
        App.post(mTmdbDetailTimeout, TMDB_DETAIL_LOAD_TIMEOUT);
    }

    private void cancelTmdbDetailFallback() {
        App.removeCallbacks(mTmdbDetailTimeout);
    }

    private void showTmdbDetailFallback() {
        if (mTmdbContentLoaded) return;
        SpiderDebug.log("tmdb-mobile", "detail loading timeout fallback");
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            bindLoadedTmdbDetail();
            if (mTmdbContentLoaded) return;
        }
        if (mVod != null) showNativeDetailFallback(mVod);
    }

    private void showNativeDetailFallback(Vod item) {
        cancelTmdbDetailFallback();
        mTmdbFallbackToNative = true;
        mTmdbContentLoaded = true;
        hideTmdbHeader();
        applyNativeFallbackWallpaperSurface();
        restoreFlagAndEpisodeFromTmdb();
        updateEpisodeGroupVisibility();
        restoreDefaultVideoLayout();
        setNativeDetailInfoVisible(true);
        setOriginalEnhancedActionVisibility(false);
        mBinding.name.setText(item.getName());
        mBinding.name.setVisibility(View.VISIBLE);
        mBinding.search.setVisibility(View.VISIBLE);
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.VISIBLE);
        setText(item);
        mBinding.progressLayout.showContent();
        loadNativePersonalRecommendations(item);
        hideProgress();
    }

    private void applyNativeFallbackWallpaperSurface() {
        mBinding.getRoot().setBackgroundColor(Color.TRANSPARENT);
        mBinding.scroll.setBackgroundColor(Color.TRANSPARENT);
        mBinding.swipeLayout.setBackgroundColor(Color.TRANSPARENT);
        mBinding.progressLayout.setBackgroundColor(Color.TRANSPARENT);
        if (mBinding.nativeContentContainer != null) mBinding.nativeContentContainer.setBackgroundColor(Color.TRANSPARENT);
        applyContextWallScrimTheme();
    }

    private void loadNativePersonalRecommendations(Vod item) {
        if (isPlaybackExiting() || isFinishing() || isDestroyed()) return;
        mPersonalRecommendationTasks.cancelAll();
        int generation = ++mPersonalRecommendationGeneration;
        if (!Setting.isPersonalRecommendation()) {
            clearNativePersonalRecommendations();
            return;
        }
        clearNativePersonalRecommendations();
        mPersonalRecommendationTasks.submit(() -> {
            PersonalRecommendationService.RecommendationPages recommendations = PersonalRecommendationService.RecommendationPages.empty();
            PersonalRecommendationService service = new PersonalRecommendationService();
            try {
                recommendations = service.loadPage(item, null, null, 0, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "mobile native core failed error=%s", e.getMessage());
            }
            if (Thread.currentThread().isInterrupted()) return;
            PersonalRecommendationService.RecommendationPages loaded = recommendations;
            runOnUiThread(() -> {
                if (isPlaybackExiting() || isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                bindNativePersonalRecommendations(loaded);
            });
            if (Thread.currentThread().isInterrupted()) return;
            service.enrichTmdbPageRatingsAsync(loaded.getTmdb(), enriched -> applyNativePersonalTmdbRatings(enriched, generation));
        });
        mPersonalRecommendationTasks.submit(() -> {
            PersonalRecommendationService.RecommendationPage page;
            try {
                page = new PersonalRecommendationService().loadAiPage(item, null, PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "mobile native ai failed error=%s", e.getMessage());
                page = PersonalRecommendationService.RecommendationPage.empty("");
            }
            if (Thread.currentThread().isInterrupted()) return;
            PersonalRecommendationService.RecommendationPage loaded = page;
            runOnUiThread(() -> {
                if (isPlaybackExiting() || isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                bindNativePersonalAiRecommendation(loaded);
            });
        });
    }

    private void bindNativePersonalRecommendations(PersonalRecommendationService.RecommendationPages recommendations) {
        mNativePersonalTmdbPage = recommendations == null ? PersonalRecommendationService.RecommendationPage.empty("") : recommendations.getTmdb();
        mNativePersonalDoubanPage = recommendations == null ? PersonalRecommendationService.RecommendationPage.empty("") : recommendations.getDouban();
        bindNativePersonalRecommendationRow(mBinding.tmdbPersonalTmdbRecommendationsLabel, mBinding.tmdbPersonalTmdbRecommendations, mPersonalTmdbRecommendationAdapter, mNativePersonalTmdbPage.getItems());
        bindNativePersonalRecommendationRow(mBinding.tmdbPersonalDoubanRecommendationsLabel, mBinding.tmdbPersonalDoubanRecommendations, mPersonalDoubanRecommendationAdapter, mNativePersonalDoubanPage.getItems());
    }

    private void bindNativePersonalAiRecommendation(PersonalRecommendationService.RecommendationPage page) {
        mNativePersonalAiPage = page == null ? PersonalRecommendationService.RecommendationPage.empty("") : page;
        bindNativePersonalRecommendationRow(mBinding.tmdbPersonalAiRecommendationsLabel, mBinding.tmdbPersonalAiRecommendations, mPersonalAiRecommendationAdapter, mNativePersonalAiPage.getItems());
    }

    private void applyNativePersonalTmdbRatings(PersonalRecommendationService.RecommendationPage page, int generation) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration || page == null) return;
            List<TmdbItem> current = mPersonalTmdbRecommendationAdapter == null
                    ? new ArrayList<>()
                    : mPersonalTmdbRecommendationAdapter.getItems();
            if (mPersonalTmdbRecommendationAdapter == null) return;
            boolean changed = com.fongmi.android.tv.ui.helper.TmdbUIAdapter.mergeRecommendationRatings(current, page.getItems());
            mNativePersonalTmdbPage = page.withItems(current);
            if (changed && mPersonalTmdbRecommendationAdapter != null) mPersonalTmdbRecommendationAdapter.setItems(current);
        });
    }

    private void bindNativePersonalRecommendationRow(View label, View recycler, TmdbRecommendationAdapter adapter, List<TmdbItem> items) {
        if (label == null || recycler == null || adapter == null) return;
        if (items != null && !items.isEmpty()) {
            adapter.setItems(items);
            label.setVisibility(View.VISIBLE);
            recycler.setVisibility(View.VISIBLE);
        } else {
            adapter.setItems(new ArrayList<>());
            label.setVisibility(View.GONE);
            recycler.setVisibility(View.GONE);
        }
    }

    private void hideNativePersonalRecommendations() {
        mPersonalRecommendationGeneration++;
        mPersonalRecommendationTasks.cancelAll();
        clearNativePersonalRecommendations();
    }

    private void clearNativePersonalRecommendations() {
        mNativePersonalTmdbPage = null;
        mNativePersonalDoubanPage = null;
        mNativePersonalAiPage = null;
        mNativePersonalTmdbLoading = false;
        mNativePersonalDoubanLoading = false;
        if (mPersonalTmdbRecommendationAdapter != null) mPersonalTmdbRecommendationAdapter.setItems(new ArrayList<>());
        if (mPersonalDoubanRecommendationAdapter != null) mPersonalDoubanRecommendationAdapter.setItems(new ArrayList<>());
        if (mPersonalAiRecommendationAdapter != null) mPersonalAiRecommendationAdapter.setItems(new ArrayList<>());
        if (mBinding.tmdbPersonalTmdbRecommendationsLabel != null) mBinding.tmdbPersonalTmdbRecommendationsLabel.setVisibility(View.GONE);
        if (mBinding.tmdbPersonalTmdbRecommendations != null) mBinding.tmdbPersonalTmdbRecommendations.setVisibility(View.GONE);
        if (mBinding.tmdbPersonalDoubanRecommendationsLabel != null) mBinding.tmdbPersonalDoubanRecommendationsLabel.setVisibility(View.GONE);
        if (mBinding.tmdbPersonalDoubanRecommendations != null) mBinding.tmdbPersonalDoubanRecommendations.setVisibility(View.GONE);
        if (mBinding.tmdbPersonalAiRecommendationsLabel != null) mBinding.tmdbPersonalAiRecommendationsLabel.setVisibility(View.GONE);
        if (mBinding.tmdbPersonalAiRecommendations != null) mBinding.tmdbPersonalAiRecommendations.setVisibility(View.GONE);
    }

    private void onPersonalRecommendationClick(TmdbItem item) {
        TmdbNavigation.open(this, item, getSite());
    }

    private boolean onPersonalRecommendationLongClick(TmdbItem item, String source) {
        com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog.show(this, item, source, this::onRecommendationNotInterested);
        return true;
    }

    private void onRecommendationNotInterested(TmdbItem item) {
        if (mPersonalTmdbRecommendationAdapter != null) mPersonalTmdbRecommendationAdapter.removeItem(item);
        if (mPersonalDoubanRecommendationAdapter != null) mPersonalDoubanRecommendationAdapter.removeItem(item);
        if (mPersonalAiRecommendationAdapter != null) mPersonalAiRecommendationAdapter.removeItem(item);
        refreshPersonalRecommendationsForHistory();
    }

    private void refreshPersonalRecommendationsForHistory() {
        if (isPlaybackExiting() || isFinishing() || isDestroyed()) return;
        if (!Setting.isPersonalRecommendation() || mVod == null) return;
        if (mTmdbHeaderView != null && mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded() && !mTmdbFallbackToNative) {
            mTmdbHeaderView.refreshPersonalRecommendations();
        } else if (mTmdbFallbackToNative || !shouldUseTmdbDetailLayout()) {
            loadNativePersonalRecommendations(mVod);
        }
    }

    private boolean isNearRecommendationRowEnd(RecyclerView recyclerView) {
        RecyclerView.Adapter<?> adapter = recyclerView.getAdapter();
        RecyclerView.LayoutManager manager = recyclerView.getLayoutManager();
        if (adapter == null || !(manager instanceof LinearLayoutManager)) return false;
        int lastVisible = ((LinearLayoutManager) manager).findLastVisibleItemPosition();
        return lastVisible >= 0 && adapter.getItemCount() - lastVisible <= 4;
    }

    private void loadMoreNativePersonalRecommendations(boolean tmdb) {
        PersonalRecommendationService.RecommendationPage page = tmdb ? mNativePersonalTmdbPage : mNativePersonalDoubanPage;
        if (isPlaybackExiting() || isFinishing() || isDestroyed()) return;
        if (page == null || !page.hasMore() || (tmdb ? mNativePersonalTmdbLoading : mNativePersonalDoubanLoading) || mVod == null) return;
        int generation = mPersonalRecommendationGeneration;
        if (tmdb) mNativePersonalTmdbLoading = true;
        else mNativePersonalDoubanLoading = true;
        mPersonalRecommendationTasks.submit(() -> {
            PersonalRecommendationService.RecommendationPage nextPage;
            PersonalRecommendationService service = new PersonalRecommendationService();
            try {
                nextPage = tmdb
                        ? service.loadTmdbPage(mVod, null, null, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE)
                        : service.loadDoubanPage(mVod, page.getNextOffset(), PersonalRecommendationService.DEFAULT_PAGE_SIZE);
            } catch (Throwable e) {
                SpiderDebug.log("personal-rec", "native load more failed tmdb=%s error=%s", tmdb, e.getMessage());
                nextPage = page;
            }
            if (Thread.currentThread().isInterrupted()) return;
            PersonalRecommendationService.RecommendationPage loadedPage = nextPage;
            runOnUiThread(() -> {
                if (isPlaybackExiting() || isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                if (tmdb) {
                    mNativePersonalTmdbLoading = false;
                    mNativePersonalTmdbPage = loadedPage;
                    if (mPersonalTmdbRecommendationAdapter != null) mPersonalTmdbRecommendationAdapter.appendItems(loadedPage.getItems());
                } else {
                    mNativePersonalDoubanLoading = false;
                    mNativePersonalDoubanPage = loadedPage;
                    if (mPersonalDoubanRecommendationAdapter != null) mPersonalDoubanRecommendationAdapter.appendItems(loadedPage.getItems());
                }
            });
            if (tmdb && !Thread.currentThread().isInterrupted()) service.enrichTmdbPageRatingsAsync(loadedPage, enriched -> applyNativePersonalTmdbRatings(enriched, generation));
        });
    }

    private boolean shouldShowAutoTmdbMatchDialog(Vod item) {
        if (item == null || mTmdbAutoDialogShown) return false;
        if (!Setting.isTmdbMatchDialog() || getTmdbItem() != null) return false;
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) return false;
        mTmdbAutoDialogShown = true;
        return true;
    }

    private void maybeShowPendingTmdbSeasonDialog() {
        if (mPendingTmdbSeasonChoice == null || mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        TmdbItem loaded = mTmdbUIAdapter.getTmdbItem();
        if (loaded == null) return;
        boolean sameItem = loaded.getTmdbId() == mPendingTmdbSeasonChoice.getTmdbId()
                && TextUtils.equals(loaded.getMediaType(), mPendingTmdbSeasonChoice.getMediaType());
        mPendingTmdbSeasonChoice = null;
        if (!sameItem || !loaded.isTv() || mTmdbUIAdapter.getSeasonOptions().size() <= 1) return;
        mBinding.getRoot().post(this::showManualTmdbSeasonDialog);
    }

    private void showManualTmdbSeasonDialog() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded() || mTmdbUIAdapter.getTmdbItem() == null || !mTmdbUIAdapter.getTmdbItem().isTv()) {
            Notify.show(R.string.detail_tmdb_empty);
            return;
        }
        java.util.List<Integer> seasons = new java.util.ArrayList<>();
        java.util.Map<Integer, Integer> counts = new java.util.HashMap<>();
        for (com.fongmi.android.tv.ui.helper.TmdbUIAdapter.SeasonOption option : mTmdbUIAdapter.getSeasonOptions()) {
            seasons.add(option.getSeasonNumber());
            counts.put(option.getSeasonNumber(), option.getEpisodeCount());
        }
        com.fongmi.android.tv.ui.dialog.ChoiceDialog.showTmdbSeason(this, seasons, counts, mTmdbUIAdapter.getSeasonResolution(), new com.fongmi.android.tv.ui.dialog.ChoiceDialog.OnTmdbSeasonChoice() {
            @Override
            public void onAuto() {
                if (mTmdbUIAdapter.clearManualSeasonBinding()) refreshTmdbEpisodeTitles();
                Notify.show(R.string.tmdb_season_match_saved);
            }

            @Override
            public void onTmdbCounts() {
                if (mTmdbUIAdapter.applyValidatedFlatSeasonMapping()) {
                    refreshTmdbEpisodeTitles();
                    Notify.show(R.string.tmdb_season_match_saved);
                } else {
                    Notify.show(R.string.tmdb_season_auto_by_counts_failed);
                }
            }

            @Override
            public void onFlat() {
                if (mTmdbUIAdapter.keepOriginalEpisodeList()) refreshTmdbEpisodeTitles();
                Notify.show(R.string.tmdb_season_match_saved);
            }

            @Override
            public void onAi() {
                analyzeTmdbSeasonWithAi();
            }

            @Override
            public void onSeason(int seasonNumber) {
                if (mTmdbUIAdapter.applyManualSeason(seasonNumber)) refreshTmdbEpisodeTitles();
                Notify.show(R.string.tmdb_season_match_saved);
            }
        });
    }

    private void analyzeTmdbSeasonWithAi() {
        Flag flag = getFlag();
        if (!Setting.isAiConfigReady()) {
            Notify.show(R.string.tmdb_season_ai_config_required);
            return;
        }
        if (mTmdbUIAdapter == null || flag == null || flag.getEpisodes() == null || flag.getEpisodes().isEmpty()) {
            Notify.show(R.string.tmdb_season_ai_failed);
            return;
        }
        cancelAiSeasonAnalysis(false);
        com.fongmi.android.tv.bean.AiConfig config = com.fongmi.android.tv.bean.AiConfig.objectFrom(Setting.getAiConfig());
        String title = mTmdbUIAdapter.getSourceTitleForAiAnalysis();
        java.util.Map<Integer, Integer> counts = mTmdbUIAdapter.getSeasonEpisodeCounts();
        List<Episode> requestEpisodes = new ArrayList<>(flag.getEpisodes());
        String requestSnapshot = EpisodeSeasonSnapshot.fingerprint(requestEpisodes, counts);
        Flag requestFlag = flag;
        int generation = ++mTmdbDialogGeneration;
        com.fongmi.android.tv.service.AiEpisodeSeasonService service =
                new com.fongmi.android.tv.service.AiEpisodeSeasonService(config);
        mAiSeasonService = service;
        mAiSeasonLoadingDialog = com.fongmi.android.tv.ui.dialog.AiAnalysisDialog.show(
                this, () -> cancelAiSeasonAnalysis(true));
        Task.execute(() -> {
            com.fongmi.android.tv.service.AiEpisodeSeasonService.AnalysisResult result =
                    service.analyze(title, requestFlag.getShow(), requestEpisodes, counts);
            runOnUiThread(() -> {
                if (mAiSeasonService != service) return;
                finishAiSeasonAnalysis(service);
                if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration
                        || !isAiSeasonSnapshotCurrent(requestFlag, requestSnapshot)) return;
                showAiSeasonAnalysisResult(result, requestFlag, requestSnapshot);
            });
        });
    }

    private void finishAiSeasonAnalysis(com.fongmi.android.tv.service.AiEpisodeSeasonService service) {
        if (mAiSeasonService != service) return;
        mAiSeasonService = null;
        AlertDialog dialog = mAiSeasonLoadingDialog;
        mAiSeasonLoadingDialog = null;
        if (dialog != null) dialog.dismiss();
    }

    private void cancelAiSeasonAnalysis(boolean notify) {
        com.fongmi.android.tv.service.AiEpisodeSeasonService service = mAiSeasonService;
        mAiSeasonService = null;
        if (service != null) service.cancel();
        AlertDialog dialog = mAiSeasonLoadingDialog;
        mAiSeasonLoadingDialog = null;
        if (dialog != null) dialog.dismiss();
        if (notify && service != null) {
            mTmdbDialogGeneration++;
            Notify.show(R.string.tmdb_season_ai_cancelled);
        }
    }

    private boolean isAiSeasonSnapshotCurrent(Flag requestFlag, String requestSnapshot) {
        Flag currentFlag = getFlag();
        if (requestFlag != currentFlag || mTmdbUIAdapter == null) return false;
        return requestSnapshot.equals(EpisodeSeasonSnapshot.fingerprint(
                currentFlag.getEpisodes(), mTmdbUIAdapter.getSeasonEpisodeCounts()));
    }

    private void showAiSeasonAnalysisResult(
            com.fongmi.android.tv.service.AiEpisodeSeasonService.AnalysisResult result,
            Flag requestFlag,
            String requestSnapshot) {
        if (result == null || !result.isSuccess()) {
            Notify.show(R.string.tmdb_season_ai_failed);
            return;
        }
        StringBuilder message = new StringBuilder();
        message.append(result.getMode()).append(" · ").append(Math.round(result.getConfidence() * 100)).append('%');
        if (result.getSeasonNumber() >= 0) message.append("\nSeason ").append(result.getSeasonNumber());
        if (!TextUtils.isEmpty(result.getSummary())) message.append("\n\n").append(result.getSummary());
        for (String warning : result.getWarnings()) message.append("\n• ").append(warning);
        if (result.getMode() == com.fongmi.android.tv.service.AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING) {
            message.append("\n\n").append(getString(R.string.tmdb_season_ai_explicit_preview_only));
            com.fongmi.android.tv.ui.dialog.ChoiceDialog.showConfirm(this, R.string.tmdb_season_ai_preview_title, message, () -> {
            });
            return;
        }
        com.fongmi.android.tv.ui.dialog.ChoiceDialog.showConfirm(this, R.string.tmdb_season_ai_preview_title, message,
                R.string.tmdb_season_ai_apply, () -> {
                    if (!isAiSeasonSnapshotCurrent(requestFlag, requestSnapshot)) {
                        Notify.show(R.string.tmdb_season_ai_failed);
                        return;
                    }
                    applyAiSeasonAnalysis(result);
                });
    }

    private void applyAiSeasonAnalysis(com.fongmi.android.tv.service.AiEpisodeSeasonService.AnalysisResult result) {
        boolean changed = switch (result.getMode()) {
            case SINGLE_SEASON -> mTmdbUIAdapter.applyManualSeason(result.getSeasonNumber());
            case KEEP_ORIGINAL -> mTmdbUIAdapter.keepOriginalEpisodeList();
            case FLAT_BY_COUNTS -> mTmdbUIAdapter.applyValidatedFlatSeasonMapping();
            case EXPLICIT_MAPPING -> false;
        };
        if (changed) {
            refreshTmdbEpisodeTitles();
            Notify.show(R.string.tmdb_season_match_saved);
        } else {
            Notify.show(result.getMode() == com.fongmi.android.tv.service.AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING
                    ? R.string.tmdb_season_ai_explicit_preview_only : R.string.tmdb_season_ai_failed);
        }
    }
    private void showManualTmdbMatchDialog() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) {
            Notify.show(R.string.detail_tmdb_need_key);
            return;
        }
        if (!isTmdbSourceEnabled()) {
            Notify.show(R.string.detail_tmdb_site_disabled);
            return;
        }
        String query = getTmdbSearchQuery();
        if (TextUtils.isEmpty(query)) {
            Notify.show(R.string.detail_tmdb_empty);
            return;
        }
        Notify.show(R.string.detail_tmdb_searching);
        int generation = ++mTmdbDialogGeneration;
        Task.execute(() -> {
            try {
                List<TmdbItem> items = mTmdbUIAdapter.search(query, mVod);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    showTmdbMatchDialog(query, items);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void showTmdbMatchDialog(String query, List<TmdbItem> items) {
        TmdbSearchDialog.create(this)
                .title(getString(R.string.detail_tmdb_match_title))
                .query(query)
                .items(items)
                .listener(this::applyManualTmdb)
                .searchListener(this::searchTmdb)
                .show();
    }

    private void searchTmdb(String keyword, TmdbSearchDialog dialog) {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) return;
        dialog.loading();
        int generation = ++mTmdbDialogGeneration;
        Task.execute(() -> {
            try {
                List<TmdbItem> items = mTmdbUIAdapter.search(keyword, mVod);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    dialog.updateItems(items);
                });
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != mTmdbDialogGeneration) return;
                    dialog.updateItems(new ArrayList<>());
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_tmdb_empty) : e.getMessage());
                });
            }
        });
    }

    private void applyManualTmdb(TmdbItem item) {
        if (mTmdbUIAdapter == null || mVod == null || item == null) return;
        mTmdbDialogGeneration++;
        mTmdbFallbackToNative = false;
        mTmdbContentLoaded = false;
        hideTmdbHeader();
        if (mBinding.videoShadow != null) mBinding.videoShadow.setVisibility(View.GONE);
        mBinding.progressLayout.showProgress();
        scheduleTmdbDetailFallback();
        mPendingTmdbSeasonChoice = item.isTv() ? item : null;
        mTmdbUIAdapter.rememberManualMatch(mVod, item);
        if (reloadHistoryAfterTmdbMatch(item)) resumeHistoryAfterTmdbMatch();
        mTmdbUIAdapter.load(item, mVod);
        Notify.show(R.string.detail_tmdb_match_saved);
    }

    private String getTmdbSearchQuery() {
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.getTmdbItem() != null && !TextUtils.isEmpty(mTmdbUIAdapter.getTmdbItem().getTitle())) {
            return mTmdbUIAdapter.getTmdbItem().getTitle();
        }
        String name = mVod != null && !TextUtils.isEmpty(mVod.getName()) ? mVod.getName() : getName();
        return mTmdbUIAdapter == null ? name : mTmdbUIAdapter.cleanSearchQuery(name);
    }

    private void updateTmdbKeepState() {
        if (mTmdbHeaderView != null) mTmdbHeaderView.setKeepSelected(Keep.find(getHistoryKey()) != null);
    }

    private void moveFlagAndEpisodeToTmdb() {
        // 将站源、线路和选集移到 TMDB 头部的 playback controls 容器中
        if (mTmdbHeaderView == null) return;

        View tmdbRoot = mTmdbHeaderView.getHeaderRoot();
        if (tmdbRoot == null) return;

        ViewGroup playbackControls = tmdbRoot.findViewById(com.fongmi.android.tv.R.id.tmdbPlaybackControls);
        if (playbackControls == null) return;

        // 移除旧内容
        playbackControls.removeAllViews();
        setTmdbFlagStyle(true);
        moveTmdbSourceToFlagTitle(tmdbRoot);

        for (View view : getTmdbMovableViews()) {
            if (view == null) continue;
            rememberTmdbMovedView(view);
            ViewGroup parent = (ViewGroup) view.getParent();
            if (parent != null) parent.removeView(view);
            playbackControls.addView(view);
        }
        moveFusionPlayerActionsToTmdb(playbackControls);
        mTmdbControlsMoved = true;
        updateTmdbPlaybackScrollContentHeight();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
        updateEpisodeGroupVisibility();
        mTmdbHeaderView.refreshTheme();
        if (shouldUseTmdbBackdropSurface()) mTmdbHeaderView.hideNativeHeroBackdrop();
        applyFusionThemeSurface();
    }

    private void restoreFlagAndEpisodeFromTmdb() {
        if (!mTmdbControlsMoved) return;
        setTmdbFlagStyle(false);
        for (TmdbMovedView item : mTmdbMovedViews) {
            ViewGroup parent = (ViewGroup) item.view.getParent();
            if (parent != null) parent.removeView(item.view);
            item.parent.addView(item.view, Math.min(item.index, item.parent.getChildCount()), item.layoutParams);
        }
        mTmdbControlsMoved = false;
        updateTmdbPlaybackScrollContentHeight();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
        updateEpisodeGroupVisibility();
    }

    private void updateTmdbPlaybackScrollContentHeight() {
        if (mBinding == null || mBinding.scroll.getChildCount() == 0) return;
        View child = mBinding.scroll.getChildAt(0);
        if (!(child instanceof ViewGroup content)) return;
        int height = mTmdbControlsMoved && usesOuterEpisodePageScroll()
                ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        ViewGroup.LayoutParams params = content.getLayoutParams();
        if (params == null || params.height == height) return;
        params.height = height;
        content.setLayoutParams(params);
        content.requestLayout();
        mBinding.scroll.requestLayout();
    }

    private void moveTmdbSourceToFlagTitle(View tmdbRoot) {
        View source = tmdbRoot.findViewById(R.id.tmdbFusionSource);
        if (source == null) source = mBinding.flagTitleBar.findViewById(R.id.tmdbFusionSource);
        if (source == null) return;
        rememberTmdbMovedView(source);
        if (source.getParent() instanceof ViewGroup parent) parent.removeView(source);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginStart(ResUtil.dp2px(12));
        source.setLayoutParams(params);
        mBinding.flagTitleBar.addView(source);
        styleTmdbSourceInFlagTitle();
    }

    private void styleTmdbSourceInFlagTitle() {
        View source = mBinding.flagTitleBar.findViewById(R.id.tmdbFusionSource);
        if (!(source instanceof TextView textView)) return;
        TextView flagTitle = mBinding.flagText;
        boolean light = isTmdbPlaybackLightTheme();
        int titleColor = tmdbPlaybackControlColor(light);
        textView.setAlpha(1f);
        textView.setTextColor(titleColor);
        textView.setLinkTextColor(titleColor);
        if (flagTitle != null) textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, flagTitle.getTextSize());
        textView.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        textView.setSingleLine(true);
        textView.setMaxWidth(ResUtil.dp2px(260));
        if (light) textView.setShadowLayer(0, 0, 0, 0);
        else if (isLightText(titleColor)) textView.setShadowLayer(ResUtil.dp2px(2), 0, ResUtil.dp2px(1), 0xB0000000);
        else textView.setShadowLayer(0, 0, 0, 0);
    }

    private boolean isLightText(int color) {
        return Color.red(color) + Color.green(color) + Color.blue(color) > 384;
    }

    private void setTmdbFlagStyle(boolean enabled) {
        mFlagAdapter.setTmdbLight(isTmdbPlaybackLightTheme());
        mFlagAdapter.setTmdbStyle(enabled);
        mBinding.flag.setAdapter(null);
        mBinding.flag.setAdapter(mFlagAdapter);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
    }

    private void rememberTmdbMovedView(View view) {
        if (view == null) return;
        for (TmdbMovedView item : mTmdbMovedViews) if (item.view == view) return;
        if (view.getParent() instanceof ViewGroup) mTmdbMovedViews.add(new TmdbMovedView(view));
    }

    private void moveFusionPlayerActionsToTmdb(ViewGroup playbackControls) {
        if (!Setting.isFusionDetailPage()) {
            return;
        }
        View actions = mBinding.control.action.getRoot();
        rememberTmdbMovedView(actions);
        if (actions.getParent() instanceof ViewGroup parent) parent.removeView(actions);
        actions.setLayoutParams(new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        playbackControls.addView(actions);
        actions.setVisibility(View.VISIBLE);
        applyActionButtonSettings();
    }

    private boolean isFusionPlayerActionsDocked() {
        return Setting.isFusionDetailPage() && mBinding.control.action.getRoot().getParent() != mBinding.control.bottom;
    }

    private View[] getTmdbMovableViews() {
        return new View[]{
                mBinding.flagTitleBar,
                mBinding.flag,
                mBinding.qualityText,
                mBinding.quality,
                mBinding.episodeTitleBar,
                mBinding.episodeGroup,
                mBinding.episode,
        };
    }

    private void applyShortDramaMode() {
        if (!isShortDramaSession()) return;
        enterShortDramaFullscreen();
        setShortDramaScale();
        mBinding.exo.postDelayed(this::setShortDramaScale, 250);
        hideControl();
    }

    private void enterShortDramaFullscreen() {
        if (!isFullscreen()) {
            setFullscreen(true);
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
            setRequestedOrientation(isPort() ? ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT : ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
            mKeyDown.resetScale();
        }
        syncShortDramaGesture();
        setShortDramaScale();
        hideControl();
        ViewCompat.requestApplyInsets(mBinding.getRoot());
    }

    /**
     * 手势轴向跟着呈现形态走：短剧竖屏全屏时整屏上下滑切集、长按后上下滑调亮度/音量。
     * <p>
     * 必须由「当前是否处于短剧全屏」推导，不能在进入时置一次了事：横屏短剧允许自由旋转，
     * 转回竖屏会走 exitFullscreen 退回内嵌小窗，此时若手势仍是短剧那套，在详情页上
     * 竖滑就会误切集。同理换到另一部短剧时形态保持不变，标记也不该被清掉。
     */
    private void syncShortDramaGesture() {
        if (mKeyDown != null) mKeyDown.setShortDrama(isFullscreen() && isShortDramaSession());
    }

    private void setShortDramaScale() {
        int scale = (mHistory != null && mHistory.getScale() != -1) ? getScale() : SHORT_DRAMA_SCALE;
        applyResizeMode(scale);
        mBinding.control.action.scale.setText(ResUtil.getStringArray(R.array.select_scale)[scale]);
    }

    private void finishShortDrama() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        markPlaybackExiting();
        saveHistory(true);
        finishPlayback();
    }

    private void syncShortDramaControlLayout(boolean shortDrama) {
        if (!shortDrama) {
            restoreShortDramaControls();
            return;
        }
        dockShortDramaControls();
        mBinding.control.action.getRoot().setVisibility(View.GONE);
        mBinding.control.info.setVisibility(View.GONE);
        mBinding.control.shortDramaChangeSource.setVisibility(View.VISIBLE);
        mBinding.control.shortDramaEpisodes.setVisibility(View.VISIBLE);
        // 画质仍受站点是否返回多地址约束（避免弹出只有一个选项的面板）。这里直接复用
        // setQualityVisible 记下的结果，不再自行推导 Result：那些以 false 显式收起画质的
        // 调用点（如未选中集数、切线路重置）与 Result.isMulti() 结论并不一致，
        // 两个真值来源会让 dock 图标与 action 栏按钮状态相反。
        mBinding.control.shortDramaQuality.setVisibility(mQualityVisible ? View.VISIBLE : View.GONE);
        if (mShortDramaControlDock != null) mShortDramaControlDock.setVisibility(isLock() ? View.GONE : View.VISIBLE);
    }

    private void dockShortDramaControls() {
        ViewGroup dock = getShortDramaControlDock();
        if (shortDramaControlsDocked) return;
        for (ShortDramaControlItem item : getShortDramaControlItems()) {
            ViewGroup parent = (ViewGroup) item.view.getParent();
            if (parent != null) parent.removeView(item.view);
            dock.addView(item.view, item.layoutParams);
        }
        shortDramaControlsDocked = true;
    }

    private void restoreShortDramaControls() {
        if (!shortDramaControlsDocked) return;
        // 三个图标入口只服务短剧竖屏 dock，还原回顶部栏后必须重新隐藏
        mBinding.control.shortDramaChangeSource.setVisibility(View.GONE);
        mBinding.control.shortDramaQuality.setVisibility(View.GONE);
        mBinding.control.shortDramaEpisodes.setVisibility(View.GONE);
        // 先全部摘下再按记录索引升序插回：同一容器有多个搬迁项时（cast/keep/换源/画质/选集/设置同属顶部栏），
        // 按声明顺序逐个插入会让后来者挤掉前者的位置，且 PlayerButtonSetting.applyOrder 可能已重排过容器，
        // 声明顺序不保证等于索引升序。升序插入与「摘下前的原始索引」语义一致。
        List<ShortDramaControlItem> items = new ArrayList<>(getShortDramaControlItems());
        for (ShortDramaControlItem item : items) {
            ViewGroup parent = (ViewGroup) item.view.getParent();
            if (parent != null) parent.removeView(item.view);
        }
        items.sort(Comparator.comparingInt(item -> item.index));
        for (ShortDramaControlItem item : items) {
            item.parent.addView(item.view, Math.min(item.index, item.parent.getChildCount()), item.layoutParams);
        }
        if (mShortDramaControlDock != null && mShortDramaControlDock.getParent() instanceof ViewGroup) {
            ((ViewGroup) mShortDramaControlDock.getParent()).removeView(mShortDramaControlDock);
        }
        shortDramaControlsDocked = false;
    }

    private ViewGroup getShortDramaControlDock() {
        ViewGroup right = mBinding.control.right.getRoot();
        if (mShortDramaControlDock == null) {
            LinearLayoutCompat dock = new LinearLayoutCompat(this);
            dock.setGravity(android.view.Gravity.CENTER);
            dock.setOrientation(LinearLayoutCompat.VERTICAL);
            mShortDramaControlDock = dock;
        }
        if (mShortDramaControlDock.getParent() != right) {
            int index = Math.min(right.indexOfChild(mBinding.control.right.lock) + 1, right.getChildCount());
            right.addView(mShortDramaControlDock, index, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return mShortDramaControlDock;
    }

    private List<ShortDramaControlItem> getShortDramaControlItems() {
        if (mShortDramaControlItems.isEmpty()) {
            for (View view : getShortDramaControlViews()) {
                if (view.getParent() instanceof ViewGroup) mShortDramaControlItems.add(new ShortDramaControlItem(view));
            }
        }
        return mShortDramaControlItems;
    }

    /**
     * 短剧模式下搬到右侧竖排 dock 的控件。
     * <p>
     * action 栏整条被隐藏（见 syncShortDramaControlLayout），只有搬进 dock 的控件才可达，
     * 所以换源/画质/选集必须列在这里，否则短剧只能换线路、不能换站点和画质。
     * <p>
     * 这里用的是 shortDramaChangeSource/shortDramaQuality/shortDramaEpisodes 三个专用 48dp 图标，
     * 而不是 action 栏里的 MaterialTextView（change2/actionQuality/episodes）——dock 全是图标，
     * 混入文字按钮会很突兀。id 带 shortDrama 前缀是为了避开同一视图树里的 quality/episodes 重名。
     * 本列表顺序不必等于容器顺序，restoreShortDramaControls 按记录的原始索引升序插回。
     */
    private View[] getShortDramaControlViews() {
        return new View[]{
                mBinding.control.danmaku,
                mBinding.control.cast,
                mBinding.control.keep,
                mBinding.control.shortDramaChangeSource,
                mBinding.control.shortDramaQuality,
                mBinding.control.shortDramaEpisodes,
                mBinding.control.setting,
        };
    }

    private boolean isInitAuto() {
        return initAuto;
    }

    private void setInitAuto(boolean initAuto) {
        this.initAuto = initAuto;
    }

    private boolean isAutoMode() {
        return autoMode;
    }

    private void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
    }

    public boolean isUseParse() {
        return useParse;
    }

    public void setUseParse(boolean useParse) {
        this.useParse = useParse;
    }

    public boolean isRotate() {
        return rotate;
    }

    public void setRotate(boolean rotate) {
        this.rotate = rotate;
        if (fullscreen && !rotate) setPadding(mBinding.control.getRoot());
        else noPadding(mBinding.control.getRoot());
    }

    private void notifyItemChanged(RecyclerView view, RecyclerView.Adapter<?> adapter) {
        view.post(() -> adapter.notifyItemRangeChanged(0, adapter.getItemCount()));
    }

    private void scrollToPosition(RecyclerView view, int position) {
        view.post(() -> {
            RecyclerView.Adapter<?> adapter = view.getAdapter();
            if (adapter == null || position < 0 || position >= adapter.getItemCount()) return;
            view.scrollToPosition(position);
        });
    }

    @Override
    public void onCasted() {
        subtitlePlaybackSession.stop(this);
        player().stop();
    }

    @Override
    public void onScale(int tag) {
        mKeyDown.resetScale();
        setScale(tag);
    }

    @Override
    public void onEpisodeColumn(int column) {
        PlayerSetting.putEpisodeColumn(column);
        refreshEpisodeTitles();
    }

    @Override
    public void onCompactEpisodeTitleChanged() {
        // 设置面板改短显时也要同步「短显」高亮与铅笔图标，与 onShortDisplay() 对齐。
        setShortDisplay();
        refreshEpisodeTitles();
    }

    private void onEpisodeTitlesReady() {
        if (isFinishing() || isDestroyed() || mEpisodeAdapter == null || mEpisodeAdapter.isEmpty()) return;
        RecyclerView.LayoutManager previous = mBinding.episode.getLayoutManager();
        Parcelable state = previous == null ? null : previous.onSaveInstanceState();
        updateEpisodeLayout(mEpisodeAdapter.getItems(), mEpisodeAdapter.isUsingTmdbCard());
        RecyclerView.LayoutManager current = mBinding.episode.getLayoutManager();
        if (state != null && current != null) current.onRestoreInstanceState(state);
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    private void refreshEpisodeTitles() {
        updateEpisodeSeasonContext();
        if (mEpisodeAdapter == null) return;
        if ((mFlagAdapter == null || mFlagAdapter.isEmpty()) && !shouldUseEpisodeRangePaging(getCurrentEpisodeItems())) {
            mEpisodeAdapter.refreshTitles();
        } else {
            setEpisodeItems(getCurrentEpisodeItems());
        }
        scrollEpisodeToSelected();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
    }

    @Override
    public void onParse(Parse item) {
        onItemClick(item);
    }

    @Override
    public void onSpeedUp() {
        if (!player().isPlaying()) return;
        mBinding.widget.speed.setVisibility(View.VISIBLE);
        mBinding.widget.speed.startAnimation(ResUtil.getAnim(R.anim.forward));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getSpeed()));
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.control.action.speed.setText(player().setSpeed(getPlaybackSpeed()));
    }

    @Override
    public void onBright(int progress) {
        mBinding.widget.bright.setVisibility(View.VISIBLE);
        mBinding.widget.brightProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_low);
        else if (progress < 70) mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_medium);
        else mBinding.widget.brightIcon.setImageResource(R.drawable.ic_widget_bright_high);
    }

    @Override
    public void onVolume(int progress) {
        mBinding.widget.volume.setVisibility(View.VISIBLE);
        mBinding.widget.volumeProgress.setProgress(progress);
        if (progress < 35) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_low);
        else if (progress < 70) mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_medium);
        else mBinding.widget.volumeIcon.setImageResource(R.drawable.ic_widget_volume_high);
    }

    @Override
    public void onFlingUp() {
        if (getEpisodeCount() == 1) onRefresh();
        else checkNext();
    }

    @Override
    public void onFlingDown() {
        if (getEpisodeCount() == 1) onRefresh();
        else checkPrev();
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        mBinding.widget.time.setText(player().getPositionTime(time));
        mBinding.widget.seek.setVisibility(View.VISIBLE);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        seekTo(time);
    }

    @Override
    public void onSingleTap() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl();
    }

    @Override
    public void onDoubleTap() {
        if (isLock()) return;
        if (!isFullscreen()) {
            enterFullscreen();
        } else if (player().isPlaying()) {
            showControl();
            onPaused();
        } else {
            hideControl();
            onPlay();
        }
    }

    @Override
    public void onTouchEnd() {
        mBinding.widget.seek.setVisibility(View.GONE);
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.widget.bright.setVisibility(View.GONE);
        mBinding.widget.volume.setVisibility(View.GONE);
    }

    @Override
    public void onShare(CharSequence title) {
        PlayerHelper.share(this, player().getUrl(), player().getHeaders(), title);
        setRedirect(true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            preparePiP("userLeaveHint");
        } else {
            requestPiP("userLeaveHint");
        }
    }

    @Override
    public boolean onPictureInPictureRequested() {
        return requestPiP("systemRequest");
    }

    private boolean preparePiP(String reason) {
        if (isRedirect() || isPlaybackExiting()) return false;
        if (syncPiPForPlaybackMode()) return false;
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        mPiP.update(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
        return true;
    }

    private boolean requestPiP(String reason) {
        if (!preparePiP(reason)) return false;
        if (isLock()) App.post(this::onLock, 500);
        return enterPiP(reason);
    }

    private boolean enterPiP(String reason) {
        if (syncPiPForPlaybackMode()) return false;
        if (service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)) return false;
        return mPiP.enter(this, player().getVideoWidth(), player().getVideoHeight(), getScale());
    }

    private boolean syncPiPForPlaybackMode() {
        boolean audioMode = isAudioBackgroundMode();
        if (mPiP != null) mPiP.setAudioMode(this, audioMode);
        return audioMode;
    }

    private boolean isAudioBackgroundMode() {
        return mAudioStageVisible || isAudioOnly();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, @NonNull Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        updateFusionThemeButtonVisibility();
        if (!isFullscreen()) setVideoView(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            hideControl();
            hideDanmaku();
            hideSheet();
        } else {
            showDanmaku();
            restoreContextWall();
            if (isStop()) finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        restoreContextWall();
        if (mAudioStageVisible) restorePlaybackArtwork();
        if (mAudioStageVisible) applyAudioBackground();
        syncLyricsPlaybackState();
        syncKaraokePosition();
    }


    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (shouldRecreateAudioStageForOrientation(newConfig)) {
            setAudioOnly(true);
            recreate();
            return;
        }
        syncFullscreenForOrientation(newConfig.orientation);
        if (!isFullscreen()) {
            applyTmdbTabletVideoLayoutIfNeeded();
            if (mVod != null) bindTmdbTabletTopSummary(mVod);
        }
        if (!isFullscreen()) refreshEpisodeLayoutAfterFullscreen();
        applyResizeMode(getScale());
        if (isFullscreen()) {
            Util.hideSystemUI(this);
            if (isVisible(mBinding.control.getRoot())) showControl();
        }
    }

    private void syncFullscreenForOrientation(int orientation) {
        if (!isAutoRotate() || !isPort()) {
            deferredFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
            return;
        }
        if (service() == null) {
            deferredFullscreenOrientation = orientation;
            SpiderDebug.log("video-flow", "fullscreen orientation deferred orientation=%d reason=player-not-ready", orientation);
            return;
        }
        deferredFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
        if (orientation == Configuration.ORIENTATION_PORTRAIT && !isRotate() && !isLock()) exitFullscreen();
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) enterFullscreen();
    }

    private void applyDeferredFullscreenOrientation() {
        int orientation = deferredFullscreenOrientation;
        if (orientation == Configuration.ORIENTATION_UNDEFINED) return;
        SpiderDebug.log("video-flow", "fullscreen orientation resume orientation=%d", orientation);
        syncFullscreenForOrientation(orientation);
    }

    private boolean shouldRecreateAudioStageForOrientation(Configuration config) {
        if (!mAudioStageVisible || config == null) return false;
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) return isPort();
        if (config.orientation == Configuration.ORIENTATION_PORTRAIT) return isLand();
        return false;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (isFullscreen() && hasFocus) Util.hideSystemUI(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        android.util.Log.d("VideoActivity", "onStart: calling mClock.stop().start()");
        mClock.stop().start();
        mPlayerUi.onStart();
        setAudioOnly(false);
        setStop(false);
        if (service() != null) refreshLyrics();
        syncLyricsPlaybackState();
        syncKaraokePosition();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mPlayerUi.onStop();
        if (mKaraoke != null) mKaraoke.clear();
        stopAudioCoverRotation();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (hasLutQuick() && mBinding.lutQuick.hideIfVisible()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isFullscreen() && isShortDramaSession()) {
            finishShortDrama();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            mViewModel.stopSearch();
            markPlaybackExiting();
            saveHistory(true);
            stopPlayback();
            if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            super.onBackInvoked();
        }
    }

    @Override
    protected void markPlaybackExiting() {
        if (isPlaybackExiting()) return;
        super.markPlaybackExiting();
        mIntroSkipPlayback.reset();
        cancelAiSeasonAnalysis(false);
        mPersonalRecommendationGeneration++;
        mPersonalRecommendationTasks.close();
        if (mTmdbHeaderView != null) mTmdbHeaderView.onDestroy();
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.release();
    }
    @Override
    protected void onDestroy() {
        mIntroSkipPlayback.reset();
        cancelAiSeasonAnalysis(false);
        dismissKaraokeResultDialogForRecreation();
        mLyricsSearchSeq++;
        cancelKaraokePitchGeneration(false);
        dismissLyricsResultDialog();
        stopAudioCoverRotation();
        if (mLyrics != null) {
            mLyrics.setListener(null);
            mLyrics.release();
        }
        if (mKaraoke != null) mKaraoke.release();
        subtitlePlaybackSession.stop(this);
        mPlayerUi.release();
        mPersonalRecommendationGeneration++;
        mPersonalRecommendationTasks.close();
        if (mTmdbHeaderView != null) mTmdbHeaderView.onDestroy();
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.release();
        saveHistory(true);
        Timer.get().reset();
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mSeekProgressFallback, mTmdbDetailTimeout);
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        SiteHealthStore.flush();
        if (mKeyDown != null) mKeyDown.release();
        super.onDestroy();
    }

    @Override
    public String getSubtitlePlaybackKey() {
        return getHistoryKey();
    }

    @Override
    public Site getSubtitleSite() {
        return getSite();
    }

    @Override
    public Vod getSubtitleVod() {
        return mVod;
    }

    @Override
    public Episode getSubtitleEpisode() {
        return getEpisode();
    }

    @Override
    public TmdbItem getSubtitleTmdbItem() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? getTmdbItem() : item;
    }

    @Override
    public TmdbEpisode getSubtitleTmdbEpisode() {
        Episode episode = getEpisode();
        return episode == null ? null : episode.getTmdbEpisode();
    }

    @Override
    public PlayerManager getSubtitlePlayer() {
        return player();
    }

    @Override
    public boolean isSubtitleHostActive() {
        return !isFinishing() && !isDestroyed() && service() != null && player() != null && !player().isReleased() && !player().isEmpty() && isOwner();
    }

    private boolean isAdFeedbackEnabled() {
        // AI 不再是硬门槛：本地归因通道（切片结构/域名/既有规则）不依赖 AI 配置。
        // 只要求可定位的时间轴 —— 直播和时长未知的内容无法框选区间。
        if (player() == null || TextUtils.isEmpty(player().getUrl())) return false;
        return Setting.isAdblock() && !player().isLive() && player().getDuration() > 0;
    }

    private void setAdFeedbackVisible() {
        mBinding.control.action.adFeedback.setVisibility(isAdFeedbackEnabled() ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
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
        if (!isAdFeedbackEnabled()) return;
        adFeedback();
        mAdFeedbackHost.recordPlaybackHost();
    }

    /** 短按：标记模式下第二次按下确定终点，否则以当前位置为终点回溯推断起点。 */
    private void submitAdFeedback() {
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
        AdBlockStatsStore.recordFeedback(getKey());
    }

    /** 长按：打起点进入标记模式，再次长按取消。 */
    private boolean onAdFeedbackLongPress() {
        if (!isAdFeedbackEnabled()) return false;
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
        Site site = getSite();
        AdRulePlanApplier.Outcome outcome = AdRulePlanApplier.apply(
                plan, site == null ? getKey() : site.getKey());
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
            return player() != null
                    && com.fongmi.android.tv.player.exo.MediaSourceFactory.isHlsUrl(player().getUrl());
        }

        @Override
        public boolean skipInterval(long startMs, long endMs, String feedbackId) {
            return player() != null && player().skipUserAdInterval(startMs, endMs, feedbackId);
        }
    }

    private final class AdFeedbackMetadata implements AdFeedbackHostAdapter.Metadata {
        @Override
        public String siteKey() {
            Site site = getSite();
            return site == null ? getKey() : site.getKey();
        }

        @Override
        public String siteName() {
            Site site = getSite();
            return site == null ? "" : site.getName();
        }

        @Override
        public String vodName() {
            return mHistory == null ? mBinding.name.getText().toString() : mHistory.getVodName();
        }

        @Override
        public String flagName() {
            return getFlag() == null ? "" : getFlag().getFlag();
        }

        @Override
        public String episodeName() {
            return getEpisode() == null ? "" : getEpisode().getName();
        }
    }

    private final class AdFeedbackUi implements AdFeedbackHostAdapter.Ui {
        @Override
        public void runBackground(Runnable task) {
            Task.execute(task);
        }

        @Override
        public void runOnUi(Runnable task) {
            runOnUiThread(task);
        }

        @Override
        public void showSession(AdFeedbackSession session) {
            showAdFeedbackSession(session);
        }
    }

    private static class ShortDramaControlItem {
        private final View view;
        private final ViewGroup parent;
        private final ViewGroup.LayoutParams layoutParams;
        private final int index;

        private ShortDramaControlItem(View view) {
            this.view = view;
            this.parent = (ViewGroup) view.getParent();
            this.layoutParams = view.getLayoutParams();
            this.index = parent.indexOfChild(view);
        }
    }

    private static class TmdbMovedView {
        private final View view;
        private final ViewGroup parent;
        private final ViewGroup.LayoutParams layoutParams;
        private final int index;

        private TmdbMovedView(View view) {
            this.view = view;
            this.parent = (ViewGroup) view.getParent();
            this.layoutParams = view.getLayoutParams();
            this.index = parent.indexOfChild(view);
        }
    }

private class AudioQueueAdapter extends RecyclerView.Adapter<AudioQueueAdapter.Holder> {

        private final List<Episode> items = new ArrayList<>();
        private int selected = -1;

        private void setItems(List<Episode> next, int selected) {
            items.clear();
            if (next != null) items.addAll(next);
            this.selected = selected;
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return Math.max(1, items.size());
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(parent.getContext());
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(44)));

            TextView title = createAudioSheetText("", 14, false);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setSingleLine(true);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setBackground(null);
            row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            ImageView remove = createAudioSheetInlineIconButton(R.drawable.ic_action_delete, () -> {
            });
            row.addView(remove, new LinearLayout.LayoutParams(ResUtil.dp2px(36), ResUtil.dp2px(36)));
            return new Holder(row, title, remove);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (items.isEmpty()) {
                holder.title.setText(getString(R.string.player_audio_playlist_empty));
                holder.title.setTextColor(0x99FFFFFF);
                holder.remove.setVisibility(View.GONE);
                holder.row.setBackground(null);
                holder.row.setOnClickListener(null);
                holder.row.setOnLongClickListener(null);
                return;
            }
            Episode item = items.get(position);
            boolean active = position == selected || TextUtils.equals(audioQueueEpisodeKey(item), Objects.toString(mPlaybackEpisodeKey, ""));
            holder.title.setText((position + 1) + ". " + getAudioQueueDisplayName(item, active));
            holder.title.setTextColor(active ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
            holder.remove.setVisibility(View.VISIBLE);
            holder.remove.setOnClickListener(v -> removeAudioQueueEpisode(item));
            holder.row.setBackground(audioSheetItemBackground(active));
            holder.row.setOnClickListener(v -> playAudioQueueEpisode(item));
            holder.row.setOnLongClickListener(v -> {
                removeAudioQueueEpisode(item);
                return true;
            });
        }

        private class Holder extends RecyclerView.ViewHolder {

            private final LinearLayout row;
            private final TextView title;
            private final ImageView remove;

            private Holder(@NonNull LinearLayout row, TextView title, ImageView remove) {
                super(row);
                this.row = row;
                this.title = title;
                this.remove = remove;
            }
        }
    }


private String getEpisodePlayFlag(Flag flag, Episode episode) {
        String value = mAudioQueueFlags.get(audioQueueEpisodeKey(episode));
        return TextUtils.isEmpty(value) ? flag == null ? "" : flag.getFlag() : value;
    }

private boolean isAudioQueueEpisode(Episode episode) {
        return !TextUtils.isEmpty(mAudioQueueFlags.get(audioQueueEpisodeKey(episode)));
    }

private String audioQueueEpisodeKey(Episode episode) {
        if (episode == null) return "";
        return episode.getName().concat("|").concat(episode.getUrl());
    }

@Override
    protected void onControllerReady(Player controller) {
        mBinding.audioSeek.setPlayer(controller);
    }

@Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mPendingKaraokeResult == null) return;
        outState.putSerializable(STATE_KARAOKE_RESULT, mPendingKaraokeResult);
        outState.putInt(STATE_KARAOKE_RESULT_ACTION, mKaraokeResultAction);
        SpiderDebug.log("karaoke-result", "save action=%d dialog=%s changing=%s", mKaraokeResultAction, mKaraokeResultDialog != null && mKaraokeResultDialog.isShowing(), isChangingConfigurations());
    }

@SuppressWarnings("deprecation")
    private void restoreKaraokeResultDialog(Bundle state) {
        KaraokeResult result = null;
        int action = KARAOKE_RESULT_ACTION_NONE;
        if (state != null) {
            Object saved = state.getSerializable(STATE_KARAOKE_RESULT);
            if (saved instanceof KaraokeResult value) result = value;
            action = state.getInt(STATE_KARAOKE_RESULT_ACTION, KARAOKE_RESULT_ACTION_NONE);
        }
        if (result == null && mViewModel != null) {
            result = mViewModel.getKaraokeResult();
            action = mViewModel.getKaraokeResultAction();
        }
        if (result == null) return;
        mPendingKaraokeResult = result;
        mKaraokeResultAction = action;
        mKaraokeResultShown = true;
        KaraokeResult restored = result;
        int restoredAction = action;
        SpiderDebug.log("karaoke-result", "restore action=%d bundle=%s", action, state != null);
        mBinding.getRoot().post(() -> showKaraokeResultDialog(restored, restoredAction));
    }

private void ensureImmersiveAudioControllers() {
        if (!PlayerSetting.isImmersiveAudioMode() || mLyrics != null || mBinding == null) return;
        mLyrics = new LyricsController(mBinding.lyrics);
        mLyrics.setSecondaryView(mBinding.audioLyrics);
        mLyrics.setListener((result, lines) -> {
            if (service() != null) service().setDesktopLyricsSnapshot(result, lines);
        });
        mKaraoke = new KaraokeController();
        mKaraoke.setListener((status, track, sample, snapshot) -> {
            boolean playing = service() != null && player().isPlaying();
            if (mBinding.karaoke != null) mBinding.karaoke.setPlaying(playing);
            mBinding.audioKaraoke.setPlaying(playing);
            if (mBinding.karaoke != null) mBinding.karaoke.setState(status, track, sample, snapshot);
            mBinding.audioKaraoke.setState(status, track, sample, snapshot);
            syncKaraokeStageVisibility();
        });
    }

private boolean shouldUseImmersiveAudio() {
        return PlayerSetting.isImmersiveAudioMode() && (isAudioOnly() || isMusicLike());
    }

private void setupAudioStageOverlay() {
        ViewGroup parent = (ViewGroup) mBinding.audioStage.getParent();
        if (parent != null) parent.removeView(mBinding.audioStage);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        ((ViewGroup) mBinding.getRoot()).addView(mBinding.audioStage, params);
        mBinding.audioStage.bringToFront();
    }

private void configureAudioLandscapeActions() {
        if (mBinding == null || !ResUtil.isLand(this)) return;
        if (mBinding.audioTransport instanceof ViewGroup transport) {
            for (int i = 0; i < transport.getChildCount(); i++) {
                View child = transport.getChildAt(i);
                if (!(child instanceof ViewGroup group)) continue;
                for (int j = 0; j < group.getChildCount(); j++) {
                    View item = group.getChildAt(j);
                    if (item instanceof TextView) item.setVisibility(View.GONE);
                    else if (item instanceof ImageView) {
                        ViewGroup.LayoutParams params = item.getLayoutParams();
                        params.width = ResUtil.dp2px(40);
                        params.height = ResUtil.dp2px(40);
                        item.setLayoutParams(params);
                    }
                }
            }
        }
        normalizeLandscapeAudioAction(mBinding.audioLyricsAction);
        normalizeLandscapeAudioAction(mBinding.audioKaraokeAction);
        normalizeLandscapeAudioAction(mBinding.audioMoreAction);
    }

private void normalizeLandscapeAudioAction(TextView view) {
        if (view == null) return;
        view.setText("");
        view.setGravity(Gravity.CENTER);
        view.setCompoundDrawablePadding(0);
        view.setPadding(ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8), ResUtil.dp2px(8));
    }

private void applyStatusBarSpacer() {
        if (mBinding == null) return;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        int height = mAudioStageVisible ? 0 : mStatusBarInset;
        if (lp.height == height) return;
        lp.height = height;
        mBinding.statusBar.setLayoutParams(lp);
    }

private void applyAudioStageInsets() {
        if (mBinding == null) return;
        mBinding.audioStage.setPaddingRelative(mBinding.audioStage.getPaddingStart(), ResUtil.dp2px(18) + mStatusBarInset, mBinding.audioStage.getPaddingEnd(), ResUtil.dp2px(14) + mEpisodeBottomInset);
        applyAudioBackgroundActionInsets();
    }

private void applyAudioBackgroundActionInsets() {
        ViewGroup.LayoutParams raw = mBinding.audioBackgroundAction.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams params)) return;
        int top = -mBinding.audioStage.getPaddingTop();
        int end = -mBinding.audioStage.getPaddingEnd() - ResUtil.dp2px(4);
        if (params.topMargin == top && params.getMarginEnd() == end) return;
        params.topMargin = top;
        params.setMarginEnd(end);
        mBinding.audioBackgroundAction.setLayoutParams(params);
    }

@Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (mAudioStageVisible && isSystemNavigationTouch(event)) return false;
        if (dispatchAudioStageTouch(event)) return true;
        return super.dispatchTouchEvent(event);
    }

private boolean dispatchAudioStageTouch(MotionEvent event) {
        if (!mAudioStageVisible || mBinding == null || event == null) return false;
        if (!isPointInside(mBinding.audioStage, event)) return false;
        if (isAudioStageInteractiveTouch(event)) return false;
        return true;
    }

private boolean isAudioStageInteractiveTouch(MotionEvent event) {
        return mBinding.audioLyrics.isAudioStageTouchPoint(event.getRawX(), event.getRawY())
                || isPointInside(mBinding.audioSeek, event)
                || isPointInside(mBinding.audioRepeatAction, event)
                || isPointInside(mBinding.audioPrev, event)
                || isPointInside(mBinding.audioPlay, event)
                || isPointInside(mBinding.audioNext, event)
                || isPointInside(mBinding.audioQueueAction, event)
                || isPointInside(mBinding.audioLyricsAction, event)
                || isPointInside(mBinding.audioKaraokeAction, event)
                || isPointInside(mBinding.audioMoreAction, event)
                || isPointInside(mBinding.audioCastAction, event)
                || isPointInside(mBinding.audioKeepAction, event)
                || isPointInside(mBinding.audioSettingAction, event)
                || isPointInside(mBinding.audioTrackAction, event)
                || isPointInside(mBinding.audioSubtitleAction, event)
                || isPointInside(mBinding.audioInfoAction, event)
                || isPointInside(mBinding.audioBackgroundAction, event);
    }

private boolean isSystemNavigationTouch(MotionEvent event) {
        if (mNavigationRightInset <= 0 && mEpisodeBottomInset <= 0) return false;
        Rect rect = new Rect();
        if (!mBinding.getRoot().getGlobalVisibleRect(rect)) return false;
        return (mNavigationRightInset > 0 && event.getRawX() >= rect.right - mNavigationRightInset)
                || (mEpisodeBottomInset > 0 && event.getRawY() >= rect.bottom - mEpisodeBottomInset);
    }

private boolean isPointInside(View view, MotionEvent event) {
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        Rect rect = new Rect();
        return view.getGlobalVisibleRect(rect) && rect.contains((int) event.getRawX(), (int) event.getRawY());
    }

private boolean consumePendingPlaybackResult() {
        boolean consumed = false;
        Vod detail = mPendingDetailVod;
        if (detail != null) {
            mPendingDetailVod = null;
            setDetail(detail);
            consumed = true;
        }
        Result result = mPendingPlayerResult;
        if (result != null) {
            mPendingPlayerResult = null;
            if (player().isEmpty()) setPlayer(result);
            consumed = true;
        }
        if (consumed && !player().isEmpty()) {
            refreshLyrics();
            syncKaraokePosition();
        }
        return consumed;
    }

private AudioPlaybackResolver.Resolved takeImmersiveAudioLaunch() {
        String cacheKey = Objects.toString(getIntent().getStringExtra(EXTRA_IMMERSIVE_AUDIO_CACHE_KEY), "");
        return TextUtils.isEmpty(cacheKey) ? null : IMMERSIVE_AUDIO_LAUNCHES.remove(cacheKey);
    }

    private boolean hasPendingImmersiveAudioLaunch() {
        String cacheKey = Objects.toString(getIntent().getStringExtra(EXTRA_IMMERSIVE_AUDIO_CACHE_KEY), "");
        return !TextUtils.isEmpty(cacheKey) && IMMERSIVE_AUDIO_LAUNCHES.containsKey(cacheKey);
    }

private boolean consumeImmersiveAudioLaunch() {
        AudioPlaybackResolver.Resolved resolved = takeImmersiveAudioLaunch();
        if (resolved == null) return false;
        try {
            prepareImmersiveAudioPlayback(resolved);
            setDetail(resolved.getVod());
            mInlineLyrics = getEpisodeInlineLyrics(resolved.getEpisode());
            setAudioStageVisible(true);
            setPlayer(resolved.getResult());
            return true;
        } finally {
            mImmersiveAudioResolved = null;
        }
    }

private void prepareImmersiveAudioPlayback(AudioPlaybackResolver.Resolved resolved) {
        mImmersiveAudioResolved = resolved;
        Vod vod = resolved.getVod();
        Result result = resolved.getResult();
        Episode episode = resolved.getEpisode();
        String pic = result.hasArtwork() ? result.getArtwork() : vod.getPic();
        getIntent().putExtra("key", resolved.getSiteKey());
        getIntent().putExtra("id", resolved.getVodId());
        getIntent().putExtra("name", vod.getName());
        getIntent().putExtra("pic", pic);
        putIntentPlaybackSelection(getIntent(), resolved.getFlag().getFlag(), episode.getName(), episode.getUrl());
        detailStartTime = System.currentTimeMillis();
        playerStartTime = detailStartTime;
        beginPlayHealth();
        mPlaybackEpisodeKey = audioQueueEpisodeKey(episode);
        clearLyrics();
        clearKaraokeState();
        setAudioOnly(true);
    }

private void applyImmersiveAudioSelection(AudioPlaybackResolver.Resolved resolved) {
        Flag flag = resolved.getFlag();
        Episode episode = resolved.getEpisode();
        mFlagAdapter.setSelected(flag);
        mFlagAdapter.toggle(episode);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
        setEpisodeAdapter(flag.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        mBinding.control.title.setText(getPlaybackControlTitle(episode));
        mBinding.control.title.setSelected(true);
    }

private void updateEpisodeSpan(List<Episode> items) {
        int span = getEpisodeSpan(items);
        if (span == mEpisodeSpanCount) return;
        mEpisodeSpanCount = span;
        mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        if (mEpisodeDecoration != null) mBinding.episode.removeItemDecoration(mEpisodeDecoration);
        mBinding.episode.addItemDecoration(mEpisodeDecoration = new SpaceItemDecoration(mEpisodeSpanCount, 8));
    }

private boolean onLyricsSearch() {
        if (!isLyricsSearchAvailable()) return false;
        showLyricsSearchSheet(getLyricsSearchKeyword(), getLyricsSearchSuggestions());
        return true;
    }

private void showLyricsSearchSheet(String keyword, List<String> suggestions) {
        int searchSeqAtOpen = mLyricsSearchSeq;
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_lyrics_reload)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextInputLayout layout = new TextInputLayout(this);
        styleAudioSheetInput(layout, getString(R.string.player_lyrics_keyword));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x70FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setText(TextUtils.isEmpty(keyword) ? "" : keyword);
        if (input.getText() != null) input.setSelection(input.getText().length());
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ResUtil.dp2px(50), ResUtil.dp2px(50));
        searchParams.leftMargin = ResUtil.dp2px(10);
        row.addView(createAudioSheetIconButton(R.drawable.ic_action_search, () -> submitLyricsSearchSheet(dialog, input)), searchParams);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = ResUtil.dp2px(12);
        root.addView(row, inputParams);
        addLyricsSearchSuggestions(root, input, suggestions);

        dialog.setContentView(root);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            submitLyricsSearchSheet(dialog, input);
            return true;
        });
        showCompactPlaybackSheet(dialog);
        String autoKeyword = firstLyricsSearchSuggestion(suggestions);
        input.post(() -> {
            if (!dialog.isShowing() || mLyricsSearchSeq != searchSeqAtOpen) return;
            String current = input.getText() == null ? "" : input.getText().toString();
            if (!TextUtils.isEmpty(autoKeyword) && (TextUtils.isEmpty(current) || TextUtils.equals(current, keyword))) {
                input.setText(autoKeyword);
                if (input.getText() != null) input.setSelection(input.getText().length());
                Util.hideKeyboard(input);
                SpiderDebug.log("lyrics-ui", "mobile auto search suggestion=%s", autoKeyword);
                searchLyrics(autoKeyword);
            } else {
                Util.showKeyboard(input);
            }
        });
    }

private String firstLyricsSearchSuggestion(List<String> suggestions) {
        if (suggestions == null) return "";
        for (String suggestion : suggestions) {
            String value = Objects.toString(suggestion, "").trim();
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

private void addLyricsSearchSuggestions(LinearLayout root, TextInputEditText input, List<String> suggestions) {
        if (suggestions == null || suggestions.isEmpty()) return;
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int count = Math.min(8, suggestions.size());
        for (int i = 0; i < count; i++) {
            String text = suggestions.get(i);
            if (TextUtils.isEmpty(text)) continue;
            TextView chip = createLyricsSearchSuggestionChip(input, text);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(32));
            if (row.getChildCount() > 0) params.leftMargin = ResUtil.dp2px(6);
            row.addView(chip, params);
        }
        if (row.getChildCount() == 0) return;
        scroll.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(32)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32));
        params.topMargin = ResUtil.dp2px(8);
        root.addView(scroll, params);
    }

private TextView createLyricsSearchSuggestionChip(TextInputEditText input, String text) {
        TextView chip = createAudioSheetText(text, 13, false);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(10), 0);
        chip.setTextColor(SHEET_TEXT_SECONDARY);
        chip.setBackground(roundRect(SHEET_CONTROL_BG_SUBTLE, SHEET_BUTTON_RADIUS_DP, 1, SHEET_CONTROL_STROKE));
        chip.setOnClickListener(v -> {
            input.setText(text);
            if (input.getText() != null) input.setSelection(input.getText().length());
            input.requestFocus();
            Util.showKeyboard(input);
        });
        return chip;
    }

private void submitLyricsSearchSheet(BottomSheetDialog dialog, TextInputEditText input) {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            input.setError(getString(R.string.player_lyrics_keyword_required));
            return;
        }
        Util.hideKeyboard(input);
        dialog.dismiss();
        searchLyrics(keyword);
    }

private void onAudioLyricsSeek(long positionMs) {
        if (service() == null || player().isEmpty()) return;
        long duration = player().getDuration();
        long target = duration > 0 ? Math.min(Math.max(0, positionMs), Math.max(0, duration - 500)) : Math.max(0, positionMs);
        player().seekTo(target);
        if (mHistory != null) mHistory.setPosition(target);
        if (mLyrics != null) mLyrics.update(target);
    }

private void finishVideoPlaybackNow() {
        markPlaybackExiting();
        saveHistory(true);
        finishPlayback();
    }

private boolean hasAdjacentEpisode(int offset) {
        Flag flag = getFlag();
        List<Episode> items = mAudioStageVisible ? mEpisodeAdapter.getItems() : flag == null ? mEpisodeAdapter.getItems() : flag.getEpisodes();
        if (items.isEmpty()) return false;
        int position = getSelectedEpisodePosition(items) + offset;
        return position >= 0 && position < items.size();
    }

private void onAudioQueue() {
        restoreActiveAudioPlaylist();
        showAudioQueueSheet(getAudioStageTitle());
    }

private void showAudioQueueSheet(String keyword) {
        showAudioQueueSheet(keyword, AUDIO_QUEUE_TAB_CURRENT, false);
    }

    private void showAudioQueueSheet(String keyword, int selectedTab, boolean focusSearch) {
        if (mAudioQueueDialog != null && mAudioQueueDialog.isShowing()) mAudioQueueDialog.dismiss();
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        int tab = selectedTab == AUDIO_QUEUE_TAB_SEARCH ? AUDIO_QUEUE_TAB_SEARCH : AUDIO_QUEUE_TAB_CURRENT;
        if (tab == AUDIO_QUEUE_TAB_SEARCH) {
            root.addView(createAudioQueueSearchHeader(dialog), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
        } else {
            root.addView(createAudioPlaylistHeader(dialog), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(42)));
        }

        TextInputEditText input = null;
        if (tab == AUDIO_QUEUE_TAB_SEARCH) {
            ScrollView scroll = new ScrollView(this);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextInputLayout layout = new TextInputLayout(this);
            styleAudioSheetInput(layout, getString(R.string.player_audio_playlist_search_hint));
            input = new TextInputEditText(layout.getContext());
            input.setSingleLine(true);
            input.setMaxLines(1);
            input.setTextColor(Color.WHITE);
            input.setHintTextColor(0x70FFFFFF);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
            input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
            input.setText(TextUtils.isEmpty(keyword) ? "" : keyword);
            if (input.getText() != null) input.setSelection(input.getText().length());
            layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            row.addView(layout, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            TextInputEditText finalInput = input;
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ResUtil.dp2px(46), ResUtil.dp2px(46));
            searchParams.leftMargin = ResUtil.dp2px(8);
            row.addView(createAudioSheetIconButton(R.drawable.ic_action_search, () -> submitAudioQueueSearch(finalInput)), searchParams);
            root.addView(row, audioSheetWrapTopParams(8));

            mAudioQueueStatus = createAudioSheetText("", 13, false);
            mAudioQueueStatus.setTextColor(SHEET_TEXT_MUTED);
            root.addView(mAudioQueueStatus, audioSheetTopParams(4, 24));
            content.addView(createAudioSheetSection(getString(R.string.player_audio_playlist_results)));
            mAudioQueueSearchList = new LinearLayout(this);
            mAudioQueueSearchList.setOrientation(LinearLayout.VERTICAL);
            content.addView(mAudioQueueSearchList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, audioQueueContentHeight(tab)));
        } else {
            mAudioQueueList = new RecyclerView(this);
            mAudioQueueList.setOverScrollMode(View.OVER_SCROLL_NEVER);
            mAudioQueueList.setItemAnimator(null);
            mAudioQueueList.setLayoutManager(new LinearLayoutManager(this));
            mAudioQueueList.setAdapter(mAudioQueueAdapter = new AudioQueueAdapter());
            root.addView(mAudioQueueList, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, audioQueueContentHeight(tab)));
        }

        dialog.setContentView(root);
        dialog.setOnDismissListener(d -> {
            if (mAudioQueueDialog == dialog) {
                mAudioQueueDialog = null;
                mAudioQueueList = null;
                mAudioQueueAdapter = null;
                mAudioQueueSearchList = null;
                mAudioQueueStatus = null;
                mAudioQueueSearchSeq++;
            }
        });
        mAudioQueueDialog = dialog;
        renderAudioQueueList();
        if (input != null) {
            TextInputEditText finalInput = input;
            input.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
                submitAudioQueueSearch(finalInput);
                return true;
            });
        }
        showCompactPlaybackSheet(dialog);
        if (focusSearch && input != null) {
            TextInputEditText finalInput = input;
            input.post(() -> Util.showKeyboard(finalInput));
        }
    }

private TextView createAudioSheetSection(String label) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setTextColor(0xB8FFFFFF);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(ResUtil.dp2px(2), ResUtil.dp2px(8), ResUtil.dp2px(2), ResUtil.dp2px(2));
        return view;
    }

private void submitAudioQueueSearch(TextInputEditText input) {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            input.setError(getString(R.string.player_audio_playlist_search_required));
            return;
        }
        Util.hideKeyboard(input);
        searchAudioQueue(keyword);
    }

private void searchAudioQueue(String keyword) {
        int seq = ++mAudioQueueSearchSeq;
        setAudioQueueStatus(getString(R.string.search_loading));
        if (mAudioQueueSearchList != null) mAudioQueueSearchList.removeAllViews();
        Task.execute(() -> {
            try {
                Result result = SiteApi.searchContent(getSite(), keyword, false, "1");
                List<Vod> items = result.getList();
                items.removeIf(item -> TextUtils.isEmpty(item.getId()));
                App.post(() -> showAudioQueueSearchResults(seq, items));
            } catch (Exception e) {
                App.post(() -> {
                    if (seq == mAudioQueueSearchSeq) setAudioQueueStatus(Notify.getError(R.string.player_audio_playlist_search_failed, e));
                });
            }
        });
    }

private void showAudioQueueSearchResults(int seq, List<Vod> items) {
        if (seq != mAudioQueueSearchSeq || mAudioQueueSearchList == null) return;
        mAudioQueueSearchList.removeAllViews();
        if (items == null || items.isEmpty()) {
            setAudioQueueStatus(getString(R.string.player_audio_playlist_no_results));
            return;
        }
        setAudioQueueStatus(getString(R.string.player_audio_playlist_result_count, items.size()));
        for (int i = 0; i < items.size(); i++) {
            Vod item = items.get(i);
            TextView view = createAudioSheetItem(audioQueueVodLabel(item), () -> addAudioQueueVod(item));
            mAudioQueueSearchList.addView(view, audioSheetTopParams(i == 0 ? 4 : 0, 50));
        }
    }

private String audioQueueVodLabel(Vod item) {
        String name = item == null ? "" : item.getName();
        String remark = item == null ? "" : item.getRemarks();
        String site = item == null ? "" : item.getSiteName();
        String sub = TextUtils.isEmpty(remark) ? site : TextUtils.isEmpty(site) ? remark : remark + " · " + site;
        return TextUtils.isEmpty(sub) ? name : name + "\n" + sub;
    }

private void addAudioQueueVod(Vod item) {
        if (item == null || TextUtils.isEmpty(item.getId())) return;
        int seq = ++mAudioQueueSearchSeq;
        setAudioQueueStatus(getString(R.string.player_audio_playlist_adding, item.getName()));
        Task.execute(() -> {
            try {
                String key = TextUtils.isEmpty(item.getSiteKey()) ? getKey() : item.getSiteKey();
                Vod vod = SiteApi.detailContent(key, item.getId()).getVod();
                App.post(() -> appendAudioQueueVod(seq, vod));
            } catch (Exception e) {
                App.post(() -> {
                    if (seq == mAudioQueueSearchSeq) setAudioQueueStatus(Notify.getError(R.string.player_audio_playlist_add_failed, e));
                });
            }
        });
    }

private void appendAudioQueueVod(int seq, Vod vod) {
        if (seq != mAudioQueueSearchSeq || vod == null) return;
        Flag queue = getFlag();
        if (queue == null || vod.getFlags().isEmpty()) {
            setAudioQueueStatus(getString(R.string.player_audio_playlist_add_empty));
            return;
        }
        int added = 0;
        for (Flag source : vod.getFlags()) {
            for (Episode item : source.getEpisodes()) {
                if (TextUtils.isEmpty(item.getUrl())) continue;
                Episode episode = Episode.create(audioQueueEpisodeName(vod, item, source), item.getUrl());
                if (containsAudioQueueEpisode(queue.getEpisodes(), episode)) continue;
                queue.getEpisodes().add(episode);
                putAudioQueueMetadata(episode, vod, item, source);
                added++;
            }
        }
        setEpisodeAdapter(queue.getEpisodes());
        renderAudioQueueList();
        setAudioQueueStatus(added > 0 ? getString(R.string.player_audio_playlist_added, added) : getString(R.string.player_audio_playlist_exists));
    }

private View createAudioPlaylistHeader(BottomSheetDialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout titleGroup = new LinearLayout(this);
        titleGroup.setGravity(Gravity.CENTER_VERTICAL);
        titleGroup.setOrientation(LinearLayout.VERTICAL);
        TextView title = createAudioSheetText(getString(R.string.player_audio_playlist), 17, true);
        title.setSingleLine(true);
        TextView subtitle = createAudioSheetText(AudioPlaylistStore.active().name, 12, false);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setTextColor(SHEET_TEXT_MUTED);
        titleGroup.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        titleGroup.addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(titleGroup, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(createAudioSheetMiniButton(getString(R.string.play_search), false, () -> {
            dialog.dismiss();
            showAudioQueueSheet(getAudioStageTitle(), AUDIO_QUEUE_TAB_SEARCH, true);
        }), audioSheetMiniButtonParams(58, false));
        row.addView(createAudioSheetMiniButton(getString(R.string.player_audio_playlist_switch), false, this::showAudioPlaylistSwitchSheet), audioSheetMiniButtonParams(58, true));
        row.addView(createAudioSheetMiniButton(getString(R.string.player_audio_playlist_create), false, this::showAudioPlaylistCreateSheet), audioSheetMiniButtonParams(54, true));
        return row;
    }

private View createAudioQueueSearchHeader(BottomSheetDialog dialog) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(createAudioSheetTitle(getString(R.string.play_search)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        row.addView(createAudioSheetMiniButton(getString(R.string.player_audio_playlist), false, () -> {
            dialog.dismiss();
            showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
        }), audioSheetMiniButtonParams(58, false));
        return row;
    }

private void restoreActiveAudioPlaylist() {
        Flag queue = getFlag();
        if (queue == null) return;
        List<Episode> items = queue.getEpisodes();
        String selectedKey = audioQueueEpisodeKey(getEpisode());
        for (int i = items.size() - 1; i >= 0; i--) {
            Episode item = items.get(i);
            if (!isAudioQueueEpisode(item)) continue;
            items.remove(i);
            removeAudioQueueMetadata(item);
        }
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        for (AudioPlaylistStore.Entry entry : playlist.items) {
            if (entry == null || TextUtils.isEmpty(entry.url)) continue;
            Episode episode = Episode.create(TextUtils.isEmpty(entry.name) ? entry.title : entry.name, entry.url);
            if (containsAudioQueueEpisode(items, episode)) continue;
            items.add(episode);
            putAudioQueueMetadata(episode, entry);
            if (TextUtils.equals(audioQueueEpisodeKey(episode), selectedKey)) episode.setSelected(true);
        }
        setEpisodeAdapter(items);
        renderAudioQueueList();
    }

private void showAudioPlaylistSwitchSheet() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_playlist_switch)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        AudioPlaylistStore.Playlist active = AudioPlaylistStore.active();
        List<AudioPlaylistStore.Playlist> playlists = AudioPlaylistStore.list();
        for (int i = 0; i < playlists.size(); i++) {
            AudioPlaylistStore.Playlist playlist = playlists.get(i);
            TextView item = createAudioSheetItem(playlist.name + " · " + playlist.items.size(), () -> {
                AudioPlaylistStore.setActive(playlist.id);
                restoreActiveAudioPlaylist();
                dialog.dismiss();
                if (mAudioQueueDialog != null) {
                    mAudioQueueDialog.dismiss();
                    showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
                }
            });
            boolean selected = TextUtils.equals(active.id, playlist.id);
            item.setTextColor(selected ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
            item.setBackground(audioSheetItemBackground(selected));
            root.addView(item, audioSheetTopParams(i == 0 ? 8 : 0, 50));
        }
        dialog.setContentView(root);
        showCompactPlaybackSheet(dialog);
    }

private void showAudioPlaylistCreateSheet() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_playlist_create)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        TextInputLayout layout = new TextInputLayout(this);
        styleAudioSheetInput(layout, getString(R.string.player_audio_playlist_name_hint));
        TextInputEditText input = new TextInputEditText(layout.getContext());
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x70FFFFFF);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(layout, audioSheetTopParams(12, 62));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.addView(createAudioSheetButton(getString(R.string.dialog_positive), true, () -> {
            String name = input.getText() == null ? "" : input.getText().toString().trim();
            AudioPlaylistStore.create(name);
            restoreActiveAudioPlaylist();
            dialog.dismiss();
            if (mAudioQueueDialog != null) {
                mAudioQueueDialog.dismiss();
                showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
            }
        }), audioSheetButtonParams(false));
        root.addView(actions, audioSheetTopParams(12, 44));
        dialog.setContentView(root);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            AudioPlaylistStore.create(input.getText() == null ? "" : input.getText().toString().trim());
            restoreActiveAudioPlaylist();
            dialog.dismiss();
            if (mAudioQueueDialog != null) {
                mAudioQueueDialog.dismiss();
                showAudioQueueSheet("", AUDIO_QUEUE_TAB_CURRENT, false);
            }
            return true;
        });
        showCompactPlaybackSheet(dialog);
        input.post(() -> Util.showKeyboard(input));
    }

private String audioQueueEpisodeName(Vod vod, Episode episode, Flag flag) {
        String song = vod.getName();
        String name = episode.getName();
        boolean single = flag.getEpisodes().size() <= 1;
        if (TextUtils.isEmpty(song)) return name;
        if (single || TextUtils.isEmpty(name) || name.matches("\\d+")) return song;
        if (name.contains(song)) return name;
        return song + " - " + name;
    }

private boolean containsAudioQueueEpisode(List<Episode> items, Episode target) {
        for (Episode item : items) {
            if (!TextUtils.isEmpty(item.getUrl()) && item.getUrl().equals(target.getUrl())) return true;
            if (item.matches(target)) return true;
        }
        return false;
    }

private void renderAudioQueueList() {
        if (mAudioQueueAdapter == null) return;
        Flag flag = getFlag();
        List<Episode> items = flag == null ? new ArrayList<>() : flag.getEpisodes();
        restoreLearnedAudioQueueMetadata(items);
        int selected = getSelectedEpisodePosition(items);
        mAudioQueueAdapter.setItems(items, selected);
        if (mAudioQueueList != null && selected >= 0) {
            mAudioQueueList.post(() -> mAudioQueueList.scrollToPosition(selected));
        }
    }

private void restoreLearnedAudioQueueMetadata(List<Episode> items) {
        if (items == null) return;
        for (Episode item : items) {
            AudioPlaylistStore.Metadata metadata = AudioPlaylistStore.getMetadata(item.getUrl());
            if (metadata == null || TextUtils.isEmpty(metadata.title)) continue;
            String key = audioQueueEpisodeKey(item);
            mAudioQueueTitles.put(key, metadata.title);
            if (!TextUtils.isEmpty(metadata.artist)) mAudioQueueArtists.put(key, metadata.artist);
        }
    }

private void playAudioQueueEpisode(Episode item) {
        if (item == null) return;
        mAudioPlaylistCurrentIndex = findAudioPlaylistIndex(item.getUrl());
        if (mAudioQueueDialog != null) mAudioQueueDialog.dismiss();
        onItemClick(item);
    }

private int findAudioPlaylistIndex(String url) {
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null) return -1;
        for (int i = 0; i < playlist.items.size(); i++) {
            AudioPlaylistStore.Entry entry = playlist.items.get(i);
            if (entry != null && TextUtils.equals(entry.url, url)) return i;
        }
        return -1;
    }

private void removeAudioQueueEpisode(Episode target) {
        Flag queue = getFlag();
        if (queue == null || target == null) return;
        List<Episode> items = queue.getEpisodes();
        if (items.size() <= 1) {
            setAudioQueueStatus(getString(R.string.player_audio_playlist_keep_one));
            return;
        }
        int index = indexOfAudioQueueEpisode(items, target);
        if (index < 0) return;
        Episode removed = items.get(index);
        boolean selected = removed.isSelected();
        Episode next = selected ? items.get(index + 1 < items.size() ? index + 1 : index - 1) : null;
        items.remove(index);
        removeAudioQueueMetadata(removed);
        AudioPlaylistStore.removeItem(removed.getUrl());
        if (selected && next != null) onItemClick(next);
        else setEpisodeAdapter(items);
        renderAudioQueueList();
        setAudioQueueStatus(getString(R.string.player_audio_playlist_removed, removed.getDisplayName()));
    }

private int indexOfAudioQueueEpisode(List<Episode> items, Episode target) {
        for (int i = 0; i < items.size(); i++) {
            Episode item = items.get(i);
            if (!TextUtils.isEmpty(item.getUrl()) && item.getUrl().equals(target.getUrl())) return i;
            if (item.matches(target)) return i;
        }
        return -1;
    }

private void putAudioQueueMetadata(Episode episode, Vod vod, Episode sourceEpisode, Flag source) {
        String key = audioQueueEpisodeKey(episode);
        mAudioQueueFlags.put(key, source.getFlag());
        String songTitle = getAudioQueueSongTitle(vod.getName(), sourceEpisode.getName());
        mAudioQueueTitles.put(key, songTitle);
        mAudioQueuePics.put(key, vod.getPic());
        mAudioQueueLyrics.put(key, getTimedLyrics(vod.getContent()));
        String artist = TextUtils.isEmpty(vod.getActor()) ? getArtistFromEpisode(songTitle, sourceEpisode.getName()) : vod.getActor();
        if (!TextUtils.isEmpty(artist)) mAudioQueueArtists.put(key, artist);
        AudioPlaylistStore.Entry entry = new AudioPlaylistStore.Entry();
        entry.name = episode.getName();
        entry.url = episode.getUrl();
        entry.playFlag = source.getFlag();
        entry.title = songTitle;
        entry.artist = artist;
        entry.pic = vod.getPic();
        entry.lyrics = getTimedLyrics(vod.getContent());
        AudioPlaylistStore.upsertItem(entry);
    }

    private void putAudioQueueMetadata(Episode episode, AudioPlaylistStore.Entry entry) {
        String key = audioQueueEpisodeKey(episode);
        Flag flag = getFlag();
        String playFlag = TextUtils.isEmpty(entry.playFlag) && flag != null ? flag.getFlag() : entry.playFlag;
        mAudioQueueFlags.put(key, playFlag);
        String title = entry.title;
        if (TextUtils.isEmpty(title) || mHistory != null && TextUtils.equals(title, mHistory.getVodName())) title = getAudioQueueSongTitle(title, entry.name);
        mAudioQueueTitles.put(key, title);
        mAudioQueuePics.put(key, entry.pic);
        mAudioQueueLyrics.put(key, entry.lyrics);
        if (!TextUtils.isEmpty(entry.artist)) mAudioQueueArtists.put(key, entry.artist);
    }

private String getAudioQueueSongTitle(String collection, String episode) {
        String title = Objects.toString(episode, "").trim();
        String parent = Objects.toString(collection, "").trim();
        if (title.isEmpty() || title.matches("\\d+")) return parent;
        for (String separator : new String[]{" - ", " – ", " — "}) {
            if (!parent.isEmpty() && title.startsWith(parent + separator)) return title.substring(parent.length() + separator.length()).trim();
        }
        return title;
    }

private void removeAudioQueueMetadata(Episode episode) {
        String key = audioQueueEpisodeKey(episode);
        mAudioQueueFlags.remove(key);
        mAudioQueueTitles.remove(key);
        mAudioQueueArtists.remove(key);
        mAudioQueuePics.remove(key);
        mAudioQueueLyrics.remove(key);
    }

private void applyAudioQueueMetadata(Episode item) {
        if (!isAudioQueueEpisode(item)) {
            updateAudioStageText();
            return;
        }
        updateAudioStageText();
    }

private void setAudioQueueStatus(String text) {
        if (mAudioQueueStatus == null) {
            Notify.show(text);
            return;
        }
        mAudioQueueStatus.setText(Objects.toString(text, ""));
    }

private void onAudioMore() {
        ArrayList<String> items = new ArrayList<>();
        ArrayList<Runnable> actions = new ArrayList<>();
        addAudioMoreItem(items, actions, getString(R.string.keep), this::onKeep);
        addAudioMoreItem(items, actions, getString(R.string.nav_setting), this::onSetting);
        addAudioMoreItem(items, actions, getString(R.string.player_audio_background), this::showAudioBackgroundPanel);
        if (service() != null && !player().isEmpty()) addAudioMoreItem(items, actions, getString(R.string.player_osd), this::onInfo);
        if (service() != null && player().haveTrack(C.TRACK_TYPE_AUDIO)) addAudioMoreItem(items, actions, getString(R.string.play_track_audio), () -> onTrack(C.TRACK_TYPE_AUDIO));
        addAudioMoreItem(items, actions, getString(R.string.play_cast), this::onCast);
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_more)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        root.addView(createKaraokeActionGrid(dialog, true, items.toArray(new String[0]), actions.toArray(new Runnable[0]), 3), karaokeActionGridParams(10));
        dialog.setContentView(root);
        showCompactPlaybackSheet(dialog);
    }

private void addAudioMoreItem(List<String> items, List<Runnable> actions, String label, Runnable action) {
        items.add(label);
        actions.add(action);
    }

private void showAudioBackgroundPanel() {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        LinearLayout[] gridRef = new LinearLayout[1];
        String[] labels = new String[]{
                getString(PlayerSetting.isAudioBackgroundDecorated() ? R.string.player_audio_background_decorated_turn_off : R.string.player_audio_background_decorated_turn_on),
                getString(PlayerSetting.isAudioBackgroundLightEffect() ? R.string.player_audio_background_light_effect_on : R.string.player_audio_background_light_effect_off),
                getString(R.string.player_audio_background_random_plain),
                getString(R.string.player_audio_background_random_decoration),
        };
        Runnable[] actions = new Runnable[]{
                () -> {
                    toggleAudioBackgroundDecorated();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
                () -> {
                    toggleAudioBackgroundLightEffect();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
                () -> {
                    randomizeAudioPlainBackground();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
                () -> {
                    randomizeAudioBackgroundDecoration();
                    updateAudioBackgroundPanel(gridRef[0]);
                },
        };
        root.addView(createAudioSheetTitle(getString(R.string.player_audio_background)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32)));
        gridRef[0] = createKaraokeActionGrid(dialog, true, labels, actions, 2, false);
        root.addView(gridRef[0], karaokeActionGridParams(10));
        dialog.setContentView(root);
        showAudioBackgroundSheet(dialog);
    }

private void updateAudioBackgroundPanel(LinearLayout grid) {
        if (grid == null || grid.getChildCount() == 0 || !(grid.getChildAt(0) instanceof ViewGroup row)) return;
        if (row.getChildCount() > 0 && row.getChildAt(0) instanceof TextView button) button.setText(getString(PlayerSetting.isAudioBackgroundDecorated() ? R.string.player_audio_background_decorated_turn_off : R.string.player_audio_background_decorated_turn_on));
        if (row.getChildCount() > 1 && row.getChildAt(1) instanceof TextView button) button.setText(getString(PlayerSetting.isAudioBackgroundLightEffect() ? R.string.player_audio_background_light_effect_on : R.string.player_audio_background_light_effect_off));
    }

private void toggleAudioBackgroundDecorated() {
        boolean decorated = !PlayerSetting.isAudioBackgroundDecorated();
        PlayerSetting.putAudioBackgroundDecorated(decorated);
        applyAudioBackground();
        Notify.show(getString(decorated ? R.string.player_audio_background_decorated_on : R.string.player_audio_background_decorated_off));
    }

private void toggleAudioBackgroundLightEffect() {
        boolean lightEffect = !PlayerSetting.isAudioBackgroundLightEffect();
        PlayerSetting.putAudioBackgroundLightEffect(lightEffect);
        applyAudioBackground();
        Notify.show(getString(lightEffect ? R.string.player_audio_background_light_effect_on : R.string.player_audio_background_light_effect_off));
    }

private void randomizeAudioPlainBackground() {
        PlayerSetting.putAudioBackground(PlayerSetting.AUDIO_BACKGROUND_RANDOM);
        PlayerSetting.putAudioBackgroundSeed(newAudioBackgroundSeed(0, PlayerSetting.getAudioBackgroundSeed()));
        applyAudioBackground();
        Notify.show(getString(R.string.player_audio_background_random_plain_done));
    }

private void randomizeAudioBackgroundDecoration() {
        PlayerSetting.putAudioBackground(PlayerSetting.AUDIO_BACKGROUND_RANDOM);
        PlayerSetting.putAudioBackgroundDecorated(true);
        PlayerSetting.putAudioBackgroundDecorationSeed(newAudioBackgroundDecorationSeed());
        applyAudioBackground();
        Notify.show(getString(R.string.player_audio_background_random_decoration_done));
    }

private void randomizeAudioBackgroundMix(boolean notify) {
        PlayerSetting.putAudioBackground(PlayerSetting.AUDIO_BACKGROUND_RANDOM);
        PlayerSetting.putAudioBackgroundDecorated(true);
        PlayerSetting.putAudioBackgroundSeed(newAudioBackgroundSeed(2, PlayerSetting.getAudioBackgroundSeed()));
        PlayerSetting.putAudioBackgroundDecorationSeed(newAudioBackgroundDecorationSeed());
        applyAudioBackground();
        if (notify) Notify.show(getString(R.string.player_audio_background_random_mix_done));
    }

private int newAudioBackgroundDecorationSeed() {
        int previous = PlayerSetting.getAudioBackgroundDecorationSeed();
        int previousMotif = audioBackgroundDecorationMotif(previous);
        for (int i = 0; i < 8; i++) {
            int seed = newAudioBackgroundSeed(10 + i, previous);
            if (audioBackgroundDecorationMotif(seed) != previousMotif) return seed;
        }
        return newAudioBackgroundSeed(31, previous);
    }

private int newAudioBackgroundSeed(int salt, int previous) {
        int previousHue = audioBackgroundHue(previous);
        for (int i = 0; i < 8; i++) {
            int seed = mixAudioBackgroundSeed((int) System.nanoTime() ^ (int) System.currentTimeMillis() ^ (++mAudioBackgroundRandomNonce * 0x9E3779B9) ^ salt * 0x45D9F3B);
            if (seed != 0 && seed != previous && hueDistance(audioBackgroundHue(seed), previousHue) >= 36) return seed;
        }
        return mixAudioBackgroundSeed(previous ^ (++mAudioBackgroundRandomNonce * 0x7FEB352D) ^ salt * 0x846CA68B);
    }

private int audioBackgroundDecorationMotif(int seed) {
        return Math.floorMod(mixAudioBackgroundSeed(seed == 0 ? 0x5A17B3 : seed), 24);
    }

private int audioBackgroundHue(int seed) {
        return Math.floorMod(mixAudioBackgroundSeed((seed == 0 ? 0x5A17B3 : seed)), 360);
    }

private int hueDistance(int a, int b) {
        int distance = Math.abs(a - b);
        return Math.min(distance, 360 - distance);
    }

private int mixAudioBackgroundSeed(int value) {
        value ^= value >>> 16;
        value *= 0x7FEB352D;
        value ^= value >>> 15;
        value *= 0x846CA68B;
        value ^= value >>> 16;
        return value;
    }

private TextView createAudioMoreItem(BottomSheetDialog dialog, String label, Runnable action) {
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextColor(Color.WHITE);
        view.setTextSize(16);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setPadding(ResUtil.dp2px(6), 0, ResUtil.dp2px(6), 0);
        view.setBackground(audioSheetItemBackground(false));
        view.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return view;
    }

private LinearLayout.LayoutParams audioMoreItemParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(44));
        params.topMargin = ResUtil.dp2px(first ? 8 : 2);
        return params;
    }


private boolean isKaraokeActionAvailable() {
        return service() != null && (isAudioOnly() || isMusicLike());
    }

private boolean onChooseLong() {
        onChoose();
        return true;
    }

private long getPlayerSwitchPosition() {
        long position = Math.max(0, player().getPosition());
        long history = mHistory == null ? 0 : Math.max(0, mHistory.getPosition());
        if (mAudioStageVisible && position < 2000 && history > 5000) {
            SpiderDebug.log("video-flow", "switch player recover transient position current=%d history=%d", position, history);
            return history;
        }
        return position;
    }

private boolean isCurrentArtworkRequest(String url, String owner) {
        return TextUtils.equals(mArtworkRequestUrl, url) && TextUtils.equals(mArtworkRequestOwner, owner);
    }

private void restoreFlagSelectionWithoutPlayback() {
        mFlagAdapter.setSelected(resolveHistoryPlaybackFlag(mFlagAdapter.getItems()));
        Flag flag = getFlag();
        if (flag == null) return;
        syncSelectedEpisode(flag);
        setEpisodeAdapter(flag.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        if (mHistory.isRevSort()) reverseEpisode(true);
    }


@Override
    protected void onPlayerPositionDiscontinuity(Player.PositionInfo oldPosition, Player.PositionInfo newPosition, int reason) {
        debugPlaybackControl("positionDiscontinuity=" + reason + " old=" + oldPosition.positionMs + " new=" + newPosition.positionMs);
        debugLyricsLoop("positionDiscontinuity=" + reason, true);
        syncLyricsPlaybackState(player().isPlaying());
        syncKaraokePosition();
        if (mKaraoke != null) mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());
    }

private void syncLyricsPlaybackState() {
        if (mLyrics == null || service() == null || player().isEmpty()) return;
        debugLyricsLoop("syncLyricsPlaybackState", false);
        mLyrics.update(player());
    }

    private void syncLyricsPlaybackState(boolean isPlaying) {
        if (mLyrics == null || service() == null || player().isEmpty()) return;
        debugLyricsLoop("syncLyricsPlaybackState=" + isPlaying, false);
        mLyrics.update(player(), isPlaying);
    }

private void checkAudioPlayImg(boolean isPlaying) {
        mBinding.audioPlay.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        mBinding.audioKaraoke.setPlaying(isPlaying);
        updateAudioLightEffectAnimation(isPlaying);
        syncAudioCoverRotation();
    }

private void updateAudioLightEffectAnimation(boolean animated) {
        if (!mAudioStageVisible || !PlayerSetting.isAudioBackgroundLightEffect() || mAudioLightEffectAnimated == animated) return;
        mAudioLightEffectAnimated = animated;
        Drawable background = mBinding.audioStage.getBackground();
        if (background instanceof AudioPlayerBackgroundDrawable drawable) drawable.setAnimated(animated);
        else applyAudioBackground();
    }

private void syncKaraokePosition() {
        if (service() == null || player().isEmpty()) return;
        long position = Math.max(0, player().getPosition() + PlayerSetting.getLyricsTimeOffsetMs());
        boolean playing = player().isPlaying();
        debugLyricsLoop("syncKaraokePosition", false);
        if (mBinding.karaoke != null) mBinding.karaoke.syncPosition(position, playing);
        mBinding.audioKaraoke.syncPosition(position, playing);
    }

private boolean playNextAudioPlaylistEntry() {
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null || playlist.items.size() < 2) {
            if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "playlist unavailable");
            return false;
        }
        int current = mAudioPlaylistCurrentIndex;
        if (current < 0) current = findAudioPlaylistIndex(getEpisode() == null ? "" : getEpisode().getUrl());
        if (current < 0) current = findAudioPlaylistIndexByMetadata();
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "playlist=%s size=%d current=%d", playlist.name, playlist.items.size(), current);
        if (current < 0 || current + 1 >= playlist.items.size()) return false;
        int nextIndex = current + 1;
        AudioPlaylistStore.Entry next = playlist.items.get(nextIndex);
        if (next == null || TextUtils.isEmpty(next.url)) return false;
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "play next index=%d name=%s", nextIndex, next.name);
        Episode episode = Episode.create(TextUtils.isEmpty(next.name) ? next.title : next.name, next.url);
        putAudioQueueMetadata(episode, next);
        mAudioPlaylistCurrentIndex = nextIndex;
        playAudioQueueEpisode(episode);
        return true;
    }

private int findAudioPlaylistIndexByMetadata() {
        String track = getCurrentTrackMetadata();
        String title = splitCurrentTrack(track)[0];
        if (TextUtils.isEmpty(track) && TextUtils.isEmpty(title)) return -1;
        AudioPlaylistStore.Playlist playlist = AudioPlaylistStore.active();
        if (playlist == null || playlist.items == null) return -1;
        for (int i = 0; i < playlist.items.size(); i++) {
            AudioPlaylistStore.Entry entry = playlist.items.get(i);
            if (entry == null) continue;
            String name = Objects.toString(entry.name, "");
            String savedTitle = Objects.toString(entry.title, "");
            if (TextUtils.equals(name, track) || TextUtils.equals(savedTitle, title) || name.contains(title)) return i;
        }
        return -1;
    }

private boolean hasNextEpisode() {
        return !getAdjacentEpisode(1).isSelected();
    }

private void finishVideoPlaybackFromSystemBack() {
        mViewModel.stopSearch();
        markPlaybackExiting();
        saveHistory(true);
        stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        super.onBackInvoked();
    }

private void dismissKaraokeResultDialogForRecreation() {
        if (!isChangingConfigurations() || mKaraokeResultDialog == null) return;
        mSuppressKaraokeResultAction = true;
        mKaraokeResultDialog.dismiss();
        mSuppressKaraokeResultAction = false;
        mKaraokeResultDialog = null;
        SpiderDebug.log("karaoke-result", "dismiss old window for configuration change");
    }

}
