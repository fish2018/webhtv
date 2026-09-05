package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.HorizontalScrollView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.leanback.widget.ArrayObjectAdapter;
import androidx.leanback.widget.HorizontalGridView;
import androidx.leanback.widget.ItemBridgeAdapter;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.VideoSize;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
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
import com.fongmi.android.tv.bean.Danmaku;
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
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.UserAdRule;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.SearchProgress;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.player.IntroSkipKinds;
import com.fongmi.android.tv.player.IntroSkipPlayback;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.service.OmdbService;
import com.fongmi.android.tv.service.PersonalRecommendationService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.MultiThreadProxySetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.setting.TmdbSitePolicy;
import com.fongmi.android.tv.title.MediaTitleLearningExample;
import com.fongmi.android.tv.title.MediaTitleRequest;
import com.fongmi.android.tv.subtitle.SubtitlePlaybackSession;
import com.fongmi.android.tv.ui.activity.SearchActivity;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.BackdropAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.PartAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.audio.AudioPlaybackResolver;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CodecCapabilityDialog;
import com.fongmi.android.tv.ui.dialog.ContentDialog;
import com.fongmi.android.tv.ui.dialog.AdRuleEditDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.MultiThreadProxyDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleManualSearchDialog;
import com.fongmi.android.tv.ui.dialog.TmdbSearchDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.helper.EpisodeDisplayPolicy;
import com.fongmi.android.tv.ui.helper.EpisodeCardImagePolicy;
import com.fongmi.android.tv.ui.helper.EpisodeRangePolicy;

import com.fongmi.android.tv.ui.helper.EpisodeSeasonPolicy;
import com.fongmi.android.tv.ui.helper.SourceEpisodeSeasonCache;
import com.fongmi.android.tv.ui.helper.PlayerControlFocusHelper;
import com.fongmi.android.tv.ui.helper.TmdbEpisodeGridPolicy;
import com.fongmi.android.tv.ui.helper.TmdbNavigation;
import com.fongmi.android.tv.ui.helper.TmdbVideoPlayback;
import com.fongmi.android.tv.ui.helper.VodEventGuard;
import com.fongmi.android.tv.ui.player.VodPlayerChrome;
import com.fongmi.android.tv.ui.player.VodPlayerUiController;
import com.fongmi.android.tv.ui.player.VodPlayerUiHost;
import com.fongmi.android.tv.utils.ActivityLaunch;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.EpisodeHistoryTitleResolver;
import com.fongmi.android.tv.utils.EpisodeTitleFormatter;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.PushParser;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.TmdbDetailCache;
import com.fongmi.android.tv.utils.Util;
import com.fongmi.android.tv.utils.VodDetailCache;
import com.github.catvod.crawler.SpiderDebug;
import com.github.bassaer.library.MDColor;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.player.diagnostic.PanEndpointParser;
import com.fongmi.android.tv.player.karaoke.KaraokeController;
import com.fongmi.android.tv.player.karaoke.KaraokePitchTrackGenerator;
import com.fongmi.android.tv.player.karaoke.KaraokeResult;
import com.fongmi.android.tv.player.karaoke.KaraokeTrackRepository;
import com.fongmi.android.tv.player.lyrics.AudioPlaylistStore;
import com.fongmi.android.tv.player.lyrics.LyricsController;
import com.fongmi.android.tv.player.lyrics.LyricsLine;
import com.fongmi.android.tv.player.lyrics.LyricsRequest;
import com.fongmi.android.tv.player.lyrics.LyricsRepository;
import com.fongmi.android.tv.player.lyrics.LyricsResult;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.setting.LyricsSetting;
import com.fongmi.android.tv.ui.custom.AudioPlayerBackgroundDrawable;
import com.fongmi.android.tv.ui.custom.KaraokeResultView;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.PanNetworkDiagnosticDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TimerDialog;
import com.fongmi.android.tv.utils.Traffic;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class VideoActivity extends PlaybackActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, Clock.Callback, SubtitlePlaybackSession.Host, com.fongmi.android.tv.ui.host.TmdbDetailHost, ControlDialog.Listener, CastDialog.Listener, com.fongmi.android.tv.ui.novel.NovelReaderHost {
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
    private static final long AUDIO_SEEK_STEP_FINE_MS = 3000L;
    private static final long AUDIO_SEEK_STEP_NORMAL_MS = 6000L;
    private static final long AUDIO_SEEK_STEP_FAST_MS = 10000L;
    private static final long AUDIO_SEEK_STEP_MAX_MS = 15000L;

private LyricsController mLyrics;
private KaraokeController mKaraoke;
private boolean mAudioStageVisible;
private boolean mAudioLightEffectAnimated;
private boolean mKaraokeResultShown;
private boolean mSkipKaraokeTrackAutoLoad;
private BottomSheetDialog mLyricsSearchDialog;
private BottomSheetDialog mLyricsResultDialog;
private Dialog mAudioQueueDialog;
private BottomSheetDialog mKaraokePitchDialog;
private ProgressBar mKaraokePitchProgress;
private TextView mKaraokePitchMessage;
private RecyclerView mAudioQueueList;
private AudioQueueAdapter mAudioQueueAdapter;
private LinearLayout mAudioQueueSearchList;
private TextView mAudioQueueStatus;
private TextView mLyricsSearchStatus;
private LinearLayout mLyricsResultList;
private List<LyricsResult> mLyricsSearchResults;
private String mLyricsSearchKeyword;
private String mLyricsLastSearchSignature;
private String mLyricsLastSearchKeyword;
private String mLyricsSelectedResultKey;
private String mDetailLyrics;
private String mInlineLyrics;
private String mPlaybackEpisodeKey;
private String mArtworkRequestOwner;
private String mAudioArtworkColorKey;
private ObjectAnimator mAudioCoverAnimator;
private int mAudioArtworkColor = Color.rgb(55, 45, 68);
private int mAudioBackgroundRandomNonce;
private long mAudioSeekPreviewOffset;
private int mAudioSeekPreviewDirection;
private int mAudioSeekPreviewRepeat;
private boolean mAudioSeekPreviewing;
private final Map<String, String> mAudioQueueFlags = new HashMap<>();
private final Map<String, String> mAudioQueueTitles = new HashMap<>();
private final Map<String, String> mAudioQueueArtists = new HashMap<>();
private final Map<String, String> mAudioQueuePics = new HashMap<>();
private final Map<String, String> mAudioQueueLyrics = new HashMap<>();
private int mLyricsSearchSeq;
private int mLyricsSearchSheetSeq;
private int mLyricsRefreshSeq;
private int mAudioQueueSearchSeq;
private int mStatusBarInset;
private int mEpisodeBottomInset;
private Runnable mR3;
private Runnable mAudioRefreshLyricsRunnable;
private Runnable mApplyAudioBackgroundRunnable;
private Runnable mHideAudioFocusRunnable;
private long mInitialPlaybackPosition = C.TIME_UNSET;

    private static final int SHORT_DRAMA_SCALE = 0; // 0=原始(适合TV), 4=裁剪(适合手机)
    private static final int TMDB_DETAIL_LOAD_TIMEOUT = 15000;
    private static final int TMDB_CACHED_DETAIL_APPLY_DELAY_MS = 16;
    private static final int TMDB_FAST_PLAYBACK_START_DELAY_MS = 16;
    private static final int TMDB_BIND_AFTER_REVEAL_DELAY_MS = 96;
    private static final int TMDB_OVERVIEW_ROW_GAP_DP = 12;
    private static final int TMDB_OVERVIEW_BOTTOM_GUARD_DP = 6;
    private static final int OMDB_FULL_RATING_TEXT_MAX_LENGTH = 20;
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
    private static final String EXTRA_IMMERSIVE_AUDIO_CACHE_KEY = "immersive_audio_cache_key";
    private static final String EXTRA_SEARCH_KEYWORD = "search_keyword";
    private static final java.util.concurrent.ConcurrentHashMap<String, AudioPlaybackResolver.Resolved> IMMERSIVE_AUDIO_LAUNCHES = new java.util.concurrent.ConcurrentHashMap<>();

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private Observer<SearchProgress> mObserveSearchProgress;
    private EpisodeAdapter mEpisodeAdapter;
    private EpisodeAdapter mEpisodeGridAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private ParseAdapter mParseAdapter;
    private QuickAdapter mQuickAdapter;
    private FlagAdapter mFlagAdapter;
    private PartAdapter mPartAdapter;
    private BackdropAdapter mBackdropAdapter;
    private Map<String, View> mActionButtons;
    private QuickSearchDialog mQuickSearchDialog;
    private VodPlayerUiController mPlayerUi;
    private PlayerOsdController mOsd;
    private final IntroSkipPlayback mIntroSkipPlayback = new IntroSkipPlayback();
    private androidx.appcompat.app.AlertDialog mIntroSkipConfirmDialog;
    private final SubtitlePlaybackSession subtitlePlaybackSession = new SubtitlePlaybackSession(this);
    private CustomKeyDownVod mKeyDown;
    private SiteViewModel mViewModel;
    private List<String> mBroken;
    private History mHistory;
    private boolean fullscreen;
    private boolean initAuto;
    private boolean autoMode;
    private boolean revealManualSearch;
    private boolean quickSearchDialogClosed;
    private boolean useParse;
    private boolean detailRequested;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private SpaceItemDecoration episodeGridDecoration;
    private boolean episodeGridMode;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mSeekProgressFallback;
    private Runnable mTmdbDetailTimeout;
    private Runnable mTmdbEpisodeTimeout;
    private final Runnable mPendingTmdbBind = this::flushPendingTmdbBind;
    private final Runnable mDeferredTmdbDataBind = this::applyDeferredTmdbDataBind;
    private final Runnable mPendingFastTmdbPlaybackStart = this::startPendingFastTmdbPlayback;
    private boolean mTmdbDetailLoading;
    private boolean mTmdbDetailRevealed;
    private boolean mTmdbEpisodeFallbackReleased;
    private boolean mTmdbDetailFieldsApplied;
    private boolean mTmdbBindPending;
    private boolean mTmdbDataBindPending;
    private Vod mPendingTmdbBindVod;
    private Vod mPendingFastTmdbPlaybackVod;
    private TmdbDetailCache.Entry mFastTmdbDetailCache;
    private Flag mFastPlaybackFlag;
    private Episode mFastPlaybackEpisode;
    private boolean mFastTmdbPlaybackStarted;
    private boolean mFastTmdbFullDetailBound;
    private boolean mFastTmdbDetailCacheChecked;
    private int mPersonalRecommendationGeneration;
    private final Task.Scope mPersonalRecommendationTasks = new Task.Scope(Task.recommendationExecutor());
    private final Task.Scope mTmdbRatingTasks = new Task.Scope(Task.recommendationExecutor());
    private String mTmdbRatingContextKey = "";
    private AdFeedbackController mAdFeedback;
    private AdFeedbackHostAdapter mAdFeedbackHost;
    private AdFeedbackDialog mAdFeedbackDialog;
    /** 标记模式下已打的起点，-1 表示未进入标记模式。 */
    private long mAdMarkStartMs = -1;

    // TMDB 模式相关字段
    private com.fongmi.android.tv.ui.helper.TmdbUIAdapter mTmdbUIAdapter;
    private com.fongmi.android.tv.ui.custom.TmdbHeaderView mTmdbHeaderView;
    private Vod mVod;
    private String mSourceVodName = "";
    private final SourceEpisodeSeasonCache mSourceEpisodeSeasonCache = new SourceEpisodeSeasonCache();
    private boolean mTmdbAutoDialogShown;
    private int mTmdbDialogGeneration;
    private com.fongmi.android.tv.service.AiEpisodeSeasonService mAiSeasonService;
    private AlertDialog mAiSeasonLoadingDialog;
    private TmdbItem mPendingTmdbSeasonChoice;
    private Runnable mR4;
    private Runnable mBackdropRunnable;
    private String mBackdropSignature;
    private int mCurrentBackdropPage = 0;
    // TMDB 剧照幻灯片激活时，它成为背景权威并隐藏 contextWall；此时其它 setArtwork 调用
    // 不得再把 contextWall 翻回顶层遮住幻灯片。
    private boolean mBackdropSlideshowActive;
    private Clock mClock;
    private PiP mPiP;
    private View mFocus1;
    private PersonalRecommendationService.RecommendationPage mNativePersonalTmdbPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalDoubanPage;
    private PersonalRecommendationService.RecommendationPage mNativePersonalAiPage;
    private ArrayObjectAdapter mTmdbRecommendationsObjectAdapter;
    private ArrayObjectAdapter mPersonalTmdbObjectAdapter;
    private ArrayObjectAdapter mPersonalDoubanObjectAdapter;
    private ArrayObjectAdapter mPersonalAiObjectAdapter;
    private boolean mTmdbRecommendationsLoading;
    private boolean mPersonalTmdbLoading;
    private boolean mPersonalDoubanLoading;
    private boolean mPersonalAiLoading;
    private boolean mNativePersonalTmdbLoading;
    private boolean mNativePersonalDoubanLoading;
    private final Map<String, java.util.List<String[]>> mTmdbOmdbRatingCache = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> mTmdbOmdbRatingLoading = Collections.synchronizedSet(new HashSet<>());
    private final Map<String, PersonalRecommendationService.DoubanRating> mTmdbDoubanRatingCache = Collections.synchronizedMap(new HashMap<>());
    private final Set<String> mTmdbDoubanRatingLoading = Collections.synchronizedSet(new HashSet<>());
    private View mFocus2;
    private View mDialogReturnFocus;
    private Result mPendingDetail;
    private Result mPendingPlayer;
    private int playerContentGeneration;
    private int playerContentRequestId;
    private String playerContentKey = "";
    private String playerContentFlag = "";
    private String playerContentEpisode = "";
    private Result mAppliedPlayerResult;
    private AudioPlaybackResolver.Resolved mImmersiveAudioResolved;
    private boolean mImmersiveAudioRequested;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;
    private long pendingResumeSeekMs = C.TIME_UNSET;
    private boolean tmdbHistoryResumePending;
    private boolean pendingLutImport;
    private boolean playerKernelSwitchRefreshing;
    private int playerKernelSwitchRequestId;
    private int mPendingPlayerKernel = PlayerSetting.NONE;

    private final ActivityResultLauncher<Intent> mLutDir = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        LutStore.setUserDir(result.getData().getData(), result.getData().getFlags());
        Notify.show(R.string.lut_directory_selected);
        mBinding.lutQuick.refreshList();
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
                    mBinding.lutQuick.selectImported(preset, player(), mBinding.exo, this::onLutChanged);
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

    @Override
    public com.fongmi.android.tv.bean.TmdbItem getMatchedTmdbItem() {
        return mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
    }

    @Override
    public com.google.gson.JsonObject getMatchedTmdbDetail() {
        // leanback VideoActivity 不持有 detail JSON，只有 TmdbItem
        return null;
    }

    public static void push(FragmentActivity activity, String text) {
        PushParser.Parsed push = PushParser.fromText(text);
        Uri uri = Uri.parse(push.getUrl());
        if (FileChooser.isValid(activity, uri)) file(activity, FileChooser.getPathFromUri(uri), push.getTitle());
        else startPush(activity, push);
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

    private static void file(FragmentActivity activity, String path, String title) {
        if (TextUtils.isEmpty(path)) return;
        PushParser.Parsed push = PushParser.of("file://" + path, TextUtils.isEmpty(title) ? new File(path).getName() : title);
        start(activity, SiteApi.PUSH, push.getId(), push.getName());
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true, (TmdbItem) null, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, false, (TmdbItem) null, wallPic);
    }

    /** 搜索结果进入详情：把用户输入的搜索关键词一路带到 TMDB 自动匹配。 */
    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic, String searchKeyword) {
        start(activity, key, id, name, pic, null, true, false, (TmdbItem) null, wallPic, null, searchKeyword);
    }

    private static boolean canOpenLegacyTmdbDetail(String key, String id, boolean cast) {
        if (cast || TextUtils.isEmpty(key)) return false;
        if (SiteApi.PUSH.equals(key)) return TmdbSitePolicy.isEnabled(key, id);
        return !AudioUtil.isAudioSiteEnabled(key) && !isShortDramaSiteEnabled(key) && TmdbSitePolicy.isEnabled(key, id);
    }

    private static boolean isShortDramaSiteEnabled(String key) {
        Site site = VodConfig.get().getSite(key);
        return Setting.isShortDramaSiteEnabled(key, site == null ? "" : site.getName());
    }

    private static boolean shouldOpenLegacyTmdbDetail(String key, String id, boolean cast) {
        int mode = Setting.getDetailOpenMode();
        return canOpenLegacyTmdbDetail(key, id, cast) && Setting.isTmdbDetailPage() && Setting.isStandaloneTmdbDetailMode(mode);
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
        start(activity, key, id, name, pic, mark, false, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic) {
        start(activity, key, id, name, pic, mark, false, false, (TmdbItem) null, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        start(activity, key, id, name, pic, mark, collect, cast, (String) null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, cast, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, collect, cast, (TmdbItem) null, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, collect, cast, tmdbItem, null, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, cast, tmdbItem, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, collect, cast, tmdbItem, wallPic, content, "");
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content, String searchKeyword) {
        long launch = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "launch request key=%s id=%s name=%s collect=%s cast=%s", key, id, name, collect, cast);
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        if (dispatchToContentHandler(activity, key, id, name, pic, mark, cast)) {
            SpiderDebug.log("video-flow", "dispatched to content handler key=%s", key);
            return;
        }
        startSkippingDispatch(activity, key, id, name, pic, mark, collect, cast, tmdbItem, wallPic, content, launch, searchKeyword);
    }

    /** 跳过 ContentDispatcher 的启动路径（供阅读器等 handler 判定内容不归自己管时回退）。 */
    public static void startSkippingDispatch(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content, long launch) {
        startSkippingDispatch(activity, key, id, name, pic, mark, collect, cast, tmdbItem, wallPic, content, launch, "");
    }

    public static void startSkippingDispatch(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, com.fongmi.android.tv.bean.TmdbItem tmdbItem, String wallPic, String content, long launch, String searchKeyword) {
        if (tmdbItem == null && shouldOpenLegacyTmdbDetail(key, id, cast)) {
            TmdbDetailActivity.start(activity, key, id, name, pic, mark, null, Setting.getDetailOpenMode(), searchKeyword);
            return;
        }
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("launchTime", launch);
        if (!TextUtils.isEmpty(searchKeyword)) intent.putExtra(EXTRA_SEARCH_KEYWORD, searchKeyword);
        intent.putExtra("tmdbMode", tmdbItem != null);
        intent.putExtra("tmdbItem", tmdbItem);
        intent.putExtra("collect", collect);
        intent.putExtra("cast", cast);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        intent.putExtra("wallPic", wallPic);
        intent.putExtra("content", content);
        intent.putExtra("key", key);
        intent.putExtra("id", id);
        activity.startActivity(intent);
        SpiderDebug.log("video-flow", "launch dispatched cost=%dms key=%s id=%s", System.currentTimeMillis() - launch, key, id);
    }

    public static void startWithTmdb(Activity activity, String key, String id, String name, String pic, String mark, com.fongmi.android.tv.bean.TmdbItem tmdbItem) {
        start(activity, key, id, name, pic, mark, false, false, tmdbItem);
    }

    public static void startFromHistory(Activity activity, History item) {
        if (shouldOpenLegacyTmdbDetail(item.getSiteKey(), item.getVodId(), false)) {
            TmdbDetailActivity.startFromHistory(activity, item);
            return;
        }
        startDirect(activity, item.getSiteKey(), item.getVodId(), item.getVodName(), item.getVodPic(), item.getVodRemarks(),
                item.getVodFlag(), item.getVodRemarks(), item.getEpisodeUrl(), item);
    }

    public static void startFromResolvedHistory(Activity activity, History source, Vod target, Flag flag, Episode episode) {
        if (source == null || target == null || flag == null || episode == null) return;
        if (shouldOpenLegacyTmdbDetail(target.getSiteKey(), target.getId(), false)) {
            TmdbDetailActivity.startFromResolvedHistory(activity, source, target, flag, episode);
            return;
        }
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("collect", false);
        intent.putExtra("cast", false);
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
        intent.putExtra("cast", false);
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
        intent.putExtra("cast", false);
        intent.putExtra("mark", mark);
        intent.putExtra("name", vod.getName());
        intent.putExtra("pic", pic);
        intent.putExtra("key", resolved.getSiteKey());
        intent.putExtra("id", resolved.getVodId());
        intent.putExtra(EXTRA_IMMERSIVE_AUDIO_CACHE_KEY, cacheKey);
        putIntentPlaybackSelection(intent, resolved.getFlag().getFlag(), episode.getName(), episode.getUrl());
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
        intent.putExtra("tmdbItem", item);
        intent.putExtra("collect", false);
        intent.putExtra("cast", false);
        intent.putExtra("mark", mark);
        intent.putExtra("name", name);
        intent.putExtra("pic", pic);
        String wallPic = item == null ? "" : item.getBackdropUrl();
        if (!TextUtils.isEmpty(wallPic)) intent.putExtra("wallPic", wallPic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic)) ImgUtil.preload(activity, wallPic);
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

    private static boolean dispatchToContentHandler(Activity activity, String key, String id, String name, String pic, String mark, boolean cast) {
        return !cast && com.fongmi.android.tv.content.ContentDispatcher.dispatchSite(activity, key, id, name, pic, mark);
    }

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
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

    private String getSearchKeyword() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_SEARCH_KEYWORD), "");
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

    private void restoreImmersiveAudioRequest() {
        mImmersiveAudioRequested = PlayerSetting.isImmersiveAudioPlayback(getHistoryKey());
    }

    private Site getSite() {
        return VodConfig.get().getSite(getKey());
    }

    private Flag getFlag() {
        if (mFlagAdapter != null && mFlagAdapter.getItemCount() > 0) return mFlagAdapter.getActivated();
        return mFastPlaybackFlag == null ? new Flag() : mFastPlaybackFlag;
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
        if (flag != null && !flag.getEpisodes().isEmpty()) return flag.getEpisodes().get(getSelectedEpisodePosition(flag.getEpisodes()));
        if (mEpisodeAdapter != null && mEpisodeAdapter.getItemCount() > 0) return mEpisodeAdapter.getActivated();
        return mFastPlaybackEpisode == null ? new Episode() : mFastPlaybackEpisode;
    }

    private int getSelectedEpisodePosition(List<Episode> episodes) {
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).isSelected()) return i;
        if (mHistory == null) return 0;
        Episode historyEpisode = mHistory.getEpisode();
        for (int i = 0; i < episodes.size(); i++) if (episodes.get(i).matchesPlayback(historyEpisode)) return i;
        return 0;
    }

    private String getDanmakuEpisodeName() {
        Episode episode = getEpisode();
        return episode == null ? "" : episode.getName();
    }

    private boolean isTmdbMode() {
        return getIntent().getBooleanExtra("tmdbMode", false);
    }

    private boolean shouldUseUpstreamNativeEpisodeModule() {
        return Setting.isDirectDetailPage() && !isTmdbMode();
    }

    private boolean isTmdbSourceEnabled() {
        if (isTmdbMode()) return true;
        if (!Setting.isTmdbMode(Setting.getDetailOpenMode())) return false;
        if (!Setting.isTmdbEnabled()) return false;
        return TmdbSitePolicy.isEnabled(getKey(), getId());
    }

    private com.fongmi.android.tv.bean.TmdbItem getTmdbItem() {
        return (com.fongmi.android.tv.bean.TmdbItem) getIntent().getSerializableExtra("tmdbItem");
    }

    /**
     * 载入历史时返回完整 TMDB 身份，避免电影与剧集数字 ID 相同时串进度。
     */
    private TmdbItem getHistoryTmdbItem() {
        if (!Setting.isHistoryAggregationEffective()) return null;
        TmdbItem item = getMatchedTmdbItem();
        if (item == null) item = getTmdbItem();
        return item;
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
        return mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0 ? "" : getEpisodeTitle(getEpisode());
    }

    private String getEpisodeTitle(Episode episode) {
        return episode == null ? "" : EpisodeAdapter.getTitle(episode);
    }

    private CharSequence getPlaybackControlTitle() {
        return getPlaybackControlTitle(mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0 ? null : getEpisode());
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

    private long getLaunchTime() {
        return getIntent().getLongExtra("launchTime", 0);
    }

    private long getLaunchCost(long now) {
        long launchTime = getLaunchTime();
        return launchTime <= 0 ? 0 : now - launchTime;
    }

    @Override
    protected ViewBinding getBinding() {
        long start = System.currentTimeMillis();
        mBinding = ActivityVideoBinding.inflate(getLayoutInflater());
        SpiderDebug.log("video-flow", "inflate cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(start));
        return mBinding;
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
        SpiderDebug.log("video-flow", "service ready sinceLaunch=%dms key=%s id=%s", getLaunchCost(System.currentTimeMillis()), getKey(), getId());
        player().setDanmakuController(mBinding.exo.getDanmakuController());
        player().setDanmakuEnabled(DanmakuSetting.isShow());
        applyPendingPlayerKernel();
        setPlayerKernel();
        setDecode();
        setLut();
        if (consumeImmersiveAudioLaunch()) return;
        if (!detailRequested) checkId();
        flushPendingFastTmdbPlayback();
        if (mPendingDetail != null) {
            Result result = mPendingDetail;
            mPendingDetail = null;
            setDetail(result);
        }
        if (mPendingPlayer != null) {
            Result result = mPendingPlayer;
            mPendingPlayer = null;
            setPlayer(result);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldKey = getKey();
        String oldId = getId();
        super.onNewIntent(intent);
        String key = Objects.toString(intent.getStringExtra("key"), "");
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || (id.equals(oldId) && key.equals(oldKey))) return;
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
        playerKernelSwitchRequestId++;
        setAudioStageVisible(false);
        restoreImmersiveAudioRequest();
        resetDetailForNewIntent();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "initView start sinceLaunch=%dms key=%s id=%s", getLaunchCost(start), getKey(), getId());
        restoreImmersiveAudioRequest();
        mTmdbDetailTimeout = this::showTmdbDetailFallback;
        mTmdbEpisodeTimeout = this::showTmdbEpisodeFallback;
        initTmdbMode();
        super.initView(savedInstanceState);
        SpiderDebug.log("video-flow", "initView after playback cost=%dms", System.currentTimeMillis() - start);
        mFrameParams = mBinding.video.getLayoutParams();
        mPlayerUi = new VodPlayerUiController(new VodPlayerUiHost() {
            @Override
            public PlayerManager player() {
                return service() == null ? null : VideoActivity.this.player();
            }

            @Override
            public String osdTitle() {
                return getOsdTitle();
            }
        }, VodPlayerChrome.fromVideo(mBinding, mBinding.widget.clock, 14f), this);
        mClock = mPlayerUi.clock();
        mOsd = mPlayerUi.osd();
        mPiP = mPlayerUi.pip();
        setupAudioStageOverlay();
        mKeyDown = CustomKeyDownVod.create(this);
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mObserveSearchProgress = this::setSearchProgress;
        mBroken = new ArrayList<>();
        mR1 = this::hideControl;
        mR2 = this::updateFocus;
        mR3 = this::setTraffic;
        mR4 = this::showEmpty;
        mSeekProgressFallback = this::hideSeekProgressIfReady;
        mAudioRefreshLyricsRunnable = this::refreshLyricsNow;
        mApplyAudioBackgroundRunnable = this::applyAudioBackground;
        mHideAudioFocusRunnable = this::hideAudioStageFocusHighlight;
        SpiderDebug.log("video-flow", "initView state ready cost=%dms", System.currentTimeMillis() - start);
        checkCast();
        SpiderDebug.log("video-flow", "initView preview ready cost=%dms", System.currentTimeMillis() - start);
        setRecyclerView();
        setShortDisplay();
        SpiderDebug.log("video-flow", "initView recycler ready cost=%dms", System.currentTimeMillis() - start);
        setVideoView();
        SpiderDebug.log("video-flow", "initView video view ready cost=%dms", System.currentTimeMillis() - start);
        setViewModel();
        // 初始化：隐藏换源按钮
        mBinding.change1.setVisibility(View.GONE);
        checkId();
        SpiderDebug.log("video-flow", "initView end cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(System.currentTimeMillis()));
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.searchDetail.setOnClickListener(view -> onSearch());
        mBinding.searchDetail.setOnLongClickListener(view -> {
            onGlobalSearch();
            return true;
        });
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.change1.setOnLongClickListener(view -> {
            onGlobalSearch();
            return true;
        });
        mBinding.tmdbRematch.setOnClickListener(view -> showManualTmdbMatchDialog());
        mBinding.tmdbRematch.setOnLongClickListener(view -> {
            showManualTmdbSeasonDialog();
            return true;
        });
        mBinding.episodeTitle.setOnClickListener(view -> showManualTmdbSeasonDialog());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(guardedView(this::onTrack));
        mBinding.control.action.audio.setOnClickListener(guardedView(this::onTrack));
        mBinding.control.action.video.setOnClickListener(guardedView(this::onTrack));
        mBinding.control.action.speed.setUpListener(this::onSpeedAdd);
        mBinding.control.action.speed.setDownListener(this::onSpeedSub);
        mBinding.control.action.ending.setUpListener(this::onEndingAdd);
        mBinding.control.action.ending.setDownListener(this::onEndingSub);
        mBinding.control.action.opening.setUpListener(this::onOpeningAdd);
        mBinding.control.action.opening.setDownListener(this::onOpeningSub);
        mBinding.control.action.text.setUpListener(this::onSubtitleClick);
        mBinding.control.action.text.setDownListener(this::onSubtitleClick);
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.episodeReverse.setOnClickListener(view -> onRevSort());
        mBinding.episodeViewMode.setOnClickListener(view -> toggleEpisodeViewMode());
        mBinding.episodeFileName.setOnClickListener(view -> toggleEpisodeFileName());
        mBinding.episodeReverse.setOnKeyListener((view, keyCode, event) -> onEpisodeHeaderToolKey(view, keyCode, event));
        mBinding.episodeViewMode.setOnKeyListener((view, keyCode, event) -> onEpisodeHeaderToolKey(view, keyCode, event));
        mBinding.episodeFileName.setOnKeyListener((view, keyCode, event) -> onEpisodeHeaderToolKey(view, keyCode, event));
        mBinding.control.action.scale.setOnClickListener(guarded(this::onScale));
        mBinding.control.action.actionQuality.setOnClickListener(guarded(this::onQuality));
        mBinding.control.action.lut.setOnClickListener(guarded(this::onLut));
        mBinding.control.action.speed.setOnClickListener(guarded(this::onSpeed));
        mBinding.control.action.reset.setOnClickListener(guarded(this::onReset));
        mBinding.control.action.title.setOnClickListener(guarded(this::onTitle));
        mBinding.control.action.player.setOnClickListener(guarded(this::onPlayerKernel));
        mBinding.control.action.player.setOnLongClickListener(view -> onPlayerKernelLong());
        mBinding.control.action.decode.setOnClickListener(guarded(this::onDecode));
        mBinding.control.action.playParams.setOnClickListener(guarded(this::onPlayParams));
        mBinding.control.action.multiThreadProxy.setOnClickListener(guarded(this::onMultiThreadProxy));
        mBinding.control.action.panDiagnostic.setOnClickListener(guarded(this::onPanDiagnostic));
        mBinding.control.action.codecCapability.setOnClickListener(guarded(this::onCodecCapability));
        mBinding.control.action.ending.setOnClickListener(guarded(this::onEnding));
        mBinding.control.action.repeat.setOnClickListener(guarded(this::onRepeat));
        mBinding.control.action.search.setOnClickListener(view -> onSearch());
        mBinding.control.action.search.setOnLongClickListener(view -> {
            onGlobalSearch();
            return true;
        });
        mBinding.control.action.change2.setOnClickListener(view -> onChange());
        mBinding.control.action.fullscreen.setOnClickListener(guarded(this::onFullscreen));
        mBinding.control.action.danmaku.setOnClickListener(guarded(this::onDanmaku));
        mBinding.control.action.danmaku.setOnLongClickListener(view -> onDanmakuToggle());
        mBinding.control.action.adFeedback.setOnClickListener(view -> onAdFeedback());
        mBinding.control.action.adFeedback.setOnLongClickListener(view -> onAdFeedbackLongPress());
        mBinding.control.action.opening.setOnClickListener(guarded(this::onOpening));
        if (mBinding.control.action.immersiveAudio != null) mBinding.control.action.immersiveAudio.setOnClickListener(view -> toggleImmersiveAudioMode());
        if (mBinding.control.action.cast != null) mBinding.control.action.cast.setOnClickListener(view -> onCast());
        if (mBinding.control.action.timer != null) mBinding.control.action.timer.setOnClickListener(view -> onTimer());
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
        if (mBinding.audioStage != null) mBinding.audioStage.setOnClickListener(view -> focusAudioStageDefault());

        mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        setActionFocusScroll();
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
        mBinding.flag.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (mFlagAdapter.getItemCount() > 0) onItemClick(mFlagAdapter.get(position));
            }
        });
        mBinding.episode.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null && mBinding.video != mFocus1) mFocus1 = child.itemView;
            }
        });
        mBinding.episode.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        mBinding.episodeGrid.setOnKeyListener((view, keyCode, event) -> onEpisodeKey(event));
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                if (child != null) selectEpisodeSegment(position, false);
            }
        });
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

    private void setActionFocusScroll() {
        HorizontalScrollView scroll = mBinding.control.action.getRoot();
        if (scroll.getChildCount() == 0 || !(scroll.getChildAt(0) instanceof ViewGroup group)) return;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            child.setOnFocusChangeListener((view, hasFocus) -> {
                if (hasFocus) scroll.post(() -> scroll.smoothScrollTo(Math.max(0, view.getLeft() - ResUtil.dp2px(24)), 0));
            });
        }
    }

    private void setRecyclerView() {
        mBinding.flag.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.flag.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setVerticalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this, this::onEpisodeLongClick));
        mEpisodeAdapter.setColumn(1); // 横向滚动，固定1列
        int episodeGridSpan = getEpisodeGridSpanCount();
        mBinding.episodeGrid.setItemAnimator(null);
        mBinding.episodeGrid.setLayoutManager(new GridLayoutManager(this, episodeGridSpan));
        mBinding.episodeGrid.setAdapter(mEpisodeGridAdapter = new EpisodeAdapter(this, this::onEpisodeLongClick));
        mEpisodeGridAdapter.setGridMode(true);
        mEpisodeGridAdapter.setVerticalGridMode(true);
        mEpisodeGridAdapter.setColumn(episodeGridSpan);
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
        mArrayAdapter.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        mBinding.array.setOnKeyListener((view, keyCode, event) -> onArrayKey(event));
        mBinding.part.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.part.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.part.setAdapter(mPartAdapter = new PartAdapter(item -> initSearch(item, false)));
        mBinding.quick.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quick.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
        mBinding.control.parse.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.control.parse.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.control.parse.setAdapter(mParseAdapter = new ParseAdapter(this));
        mParseAdapter.addAll(VodConfig.get().getParses());
        // TMDB 相关 GridView 初始化
        setupTmdbGridViews();
    }

    private void setupTmdbGridViews() {
        mBinding.tmdbCast.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbCast.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPhotos.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPhotos.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbCrew.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbCrew.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbRelatedVideos.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbRelatedVideos.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPersonalTmdbRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPersonalTmdbRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPersonalDoubanRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPersonalDoubanRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.tmdbPersonalAiRecommendations.setHorizontalSpacing(ResUtil.dp2px(12));
        mBinding.tmdbPersonalAiRecommendations.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        attachRecommendationLazyLoader(mBinding.tmdbRecommendations, RecommendationRow.RECOMMENDATIONS);
        attachRecommendationLazyLoader(mBinding.tmdbPersonalTmdbRecommendations, RecommendationRow.PERSONAL_TMDB);
        attachRecommendationLazyLoader(mBinding.tmdbPersonalDoubanRecommendations, RecommendationRow.PERSONAL_DOUBAN);
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(DanmakuSetting.isLoad() ? View.VISIBLE : View.GONE);
        mBinding.control.action.adFeedback.setVisibility(isAdFeedbackEnabled() ? View.VISIBLE : View.GONE);
        applyDanmakuState();
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        setupActionButtons();
        setPlayer();
    }

    private void setPlayer() {
        mBinding.control.action.player.setText(service() == null ? ResUtil.getStringArray(R.array.select_player)[PlayerSetting.getActivePlayer()] : player().getPlayerText());
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);
        addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        addActionButton(PlayerButtonSetting.RESET, mBinding.control.action.reset);
        addActionButton(PlayerButtonSetting.SEARCH, mBinding.control.action.search);
        addActionButton(PlayerButtonSetting.CHANGE, mBinding.control.action.change2);
        addActionButton(PlayerButtonSetting.FULLSCREEN, mBinding.control.action.fullscreen);
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.PLAY_PARAMS, mBinding.control.action.playParams);
        addActionButton(PlayerButtonSetting.MULTI_THREAD_PROXY, mBinding.control.action.multiThreadProxy);
        addActionButton(PlayerButtonSetting.PAN_DIAGNOSTIC, mBinding.control.action.panDiagnostic);
        addActionButton(PlayerButtonSetting.CODEC_CAPABILITY, mBinding.control.action.codecCapability);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.QUALITY, mBinding.control.action.actionQuality);
        addActionButton(PlayerButtonSetting.LUT, mBinding.control.action.lut);
        addActionButton(PlayerButtonSetting.KARAOKE, mBinding.control.action.karaoke);
        addActionButton(PlayerButtonSetting.IMMERSIVE_AUDIO, mBinding.control.action.immersiveAudio);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.opening);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.ending);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmaku);
        addActionButton(PlayerButtonSetting.AD_FEEDBACK, mBinding.control.action.adFeedback);
        addActionButton(PlayerButtonSetting.TITLE, mBinding.control.action.title);
        addActionButton(PlayerButtonSetting.CAST, mBinding.control.action.cast);
        addActionButton(PlayerButtonSetting.TIMER, mBinding.control.action.timer);
        addActionButton(PlayerButtonSetting.REPEAT, mBinding.control.action.repeat);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
        applyActionButtonVisibility();
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    private void applyActionButtonVisibility() {
        mBinding.control.action.cast.setVisibility(isFullscreen() ? View.GONE : View.VISIBLE);
        updateImmersiveAudioAction();
        updatePanDiagnosticAction();
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
    }

    private void updatePanDiagnosticAction() {
        if (mBinding == null) return;
        mBinding.control.action.panDiagnostic.setVisibility(isFullscreen() && canRunPanDiagnostic() ? View.VISIBLE : View.GONE);
    }

    private boolean canRunPanDiagnostic() {
        if (service() == null || player().isEmpty()) return false;
        try {
            PanEndpointParser.parse(player().getUrl(), player().getHeaders());
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void updateImmersiveAudioAction() {
        if (mBinding == null) return;
        boolean audioContent = isAudioOnly() || isMusicLike();
        mBinding.control.action.immersiveAudio.setVisibility(isFullscreen() && audioContent ? View.VISIBLE : View.GONE);
        mBinding.control.action.immersiveAudio.setSelected(shouldUseImmersiveAudio());
    }

    private void toggleImmersiveAudioMode() {
        PlayerSetting.putImmersiveAudioMode(!shouldUseImmersiveAudio());
        onImmersiveAudioModeChanged();
    }

    private int getEpisodeColumn() {
        return mEpisodeAdapter == null ? 8 : EpisodeAdapter.getColumn(mEpisodeAdapter.getItems());
    }

    private int getEpisodeGridSpanCount() {
        return TmdbEpisodeGridPolicy.tvAdaptiveSpanCount(getResources().getConfiguration().screenWidthDp);
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
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

    private void onLutChanged() {
        setLut();
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class);
        mViewModel.getResult().observeForever(mObserveDetail);
        mViewModel.getPlayer().observeForever(mObservePlayer);
        mViewModel.getSearch().observeForever(mObserveSearch);
        mViewModel.getSearchProgress().observeForever(mObserveSearchProgress);
    }

    private void checkCast() {
        if (isCast() && !isFullscreen()) enterFullscreen();
        else if (mAudioStageVisible) mBinding.progressLayout.showContent();
        else if (hasInitialPreview()) showInitialPreview();
        else mBinding.progressLayout.showProgress();
    }

    private void checkId() {
        if (detailRequested) return;
        detailRequested = true;
        if (getId().startsWith("push://")) getIntent().putExtra("key", SiteApi.PUSH).putExtra("id", getId().substring(7));
        if (getId().isEmpty() || getId().startsWith("msearch:")) setEmpty(false);
        else if (!setCachedTmdbDetail()) getDetail();
    }

    private boolean setCachedTmdbDetail() {
        Vod cached = VodDetailCache.take(getTmdbVodCacheKey());
        VodEventGuard.alignCachedIdentity(cached, getKey(), getId());
        if (cached == null) return false;
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = true;
        if (!shouldRevealShellWhileLoading()) mBinding.progressLayout.showProgress();
        SpiderDebug.log("video-flow", "detail cache hit queued key=%s id=%s name=%s", getKey(), getId(), cached.getName());
        if (tryStartFastTmdbPlayback(cached)) {
            return true;
        }
        if (isIntentTmdbPlayback() && shouldWaitForPlaybackService()) {
            queueFastTmdbPlaybackUntilServiceReady(cached);
            return true;
        }
        applyCachedTmdbDetailNextFrame(cached);
        return true;
    }

    private boolean shouldWaitForPlaybackService() {
        return service() == null || mViewModel == null;
    }

    private void queueFastTmdbPlaybackUntilServiceReady(Vod item) {
        mPendingFastTmdbPlaybackVod = item;
        SpiderDebug.log("video-flow", "fast tmdb playback pending service key=%s id=%s name=%s", getKey(), getId(), item.getName());
    }

    private void flushPendingFastTmdbPlayback() {
        Vod item = mPendingFastTmdbPlaybackVod;
        if (item == null) return;
        mPendingFastTmdbPlaybackVod = null;
        if (mFastTmdbPlaybackStarted) {
            App.removeCallbacks(mPendingFastTmdbPlaybackStart);
            App.post(mPendingFastTmdbPlaybackStart, TMDB_FAST_PLAYBACK_START_DELAY_MS);
            return;
        }
        if (tryStartFastTmdbPlayback(item)) return;
        applyCachedTmdbDetailNextFrame(item);
    }

    private void applyCachedTmdbDetailNextFrame(Vod item) {
        mBinding.getRoot().postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            long start = System.currentTimeMillis();
            setDetail(Result.vod(item));
            SpiderDebug.log("video-flow", "detail cache apply cost=%dms key=%s id=%s name=%s", System.currentTimeMillis() - start, getKey(), getId(), item.getName());
        }, TMDB_CACHED_DETAIL_APPLY_DELAY_MS);
    }

    private boolean tryStartFastTmdbPlayback(Vod item) {
        if (!isIntentTmdbPlayback() || mFastTmdbPlaybackStarted || item == null) return false;
        long start = System.currentTimeMillis();
        mSourceEpisodeSeasonCache.clear();
        mVod = item;
        mFastTmdbPlaybackStarted = true;
        mFastTmdbFullDetailBound = false;
        takeFastTmdbDetailCache();
        prepareFastTmdbPlaybackItem(item);
        mBinding.name.setText(getFastTmdbPlaybackInitialName(item));
        mBinding.widget.title.setText(getFastTmdbPlaybackInitialTitle(item));
        mBinding.widget.title.setSelected(true);
        setText(item);
        hydrateFastTmdbPlaybackDetail(item);
        mBinding.video.requestFocus();
        showProgress();
        showFastTmdbPlaybackContent();
        SpiderDebug.log("video-flow", "fast tmdb playback reveal cost=%dms key=%s episode=%s", System.currentTimeMillis() - start, getKey(), getIntentPlaybackEpisodeName());
        if (shouldWaitForPlaybackService()) {
            queueFastTmdbPlaybackUntilServiceReady(item);
            return true;
        }
        App.removeCallbacks(mPendingFastTmdbPlaybackStart);
        App.post(mPendingFastTmdbPlaybackStart, TMDB_FAST_PLAYBACK_START_DELAY_MS);
        return true;
    }

    private String getFastTmdbPlaybackInitialName(Vod item) {
        return item == null || TextUtils.isEmpty(item.getName()) ? getName() : item.getName();
    }

    private CharSequence getFastTmdbPlaybackInitialTitle(Vod item) {
        String name = getFastTmdbPlaybackInitialName(item);
        String episode = TextUtils.isEmpty(getIntentPlaybackEpisodeName()) ? getMark() : getIntentPlaybackEpisodeName();
        return TextUtils.isEmpty(episode) || TextUtils.equals(name, episode) ? name : getString(R.string.detail_title, name, episode);
    }

    private void showFastTmdbPlaybackContent() {
        if (!mBinding.progressLayout.isContent()) {
            mBinding.progressLayout.showContent();
            SpiderDebug.log("video-flow", "fast tmdb playback content reveal key=%s id=%s", getKey(), getId());
        }
    }

    private void startPendingFastTmdbPlayback() {
        if (isFinishing() || isDestroyed() || !mFastTmdbPlaybackStarted || mVod == null) return;
        if (service() == null || mViewModel == null) return;
        long start = System.currentTimeMillis();
        Vod item = mVod;
        prepareFastTmdbPlaybackItem(item);
        Flag flag = findFastTmdbPlaybackFlag(item);
        Episode episode = findFastTmdbPlaybackEpisode(flag);
        if (flag == null || episode == null || TextUtils.isEmpty(episode.getUrl())) {
            mFastTmdbPlaybackStarted = false;
            SpiderDebug.log("video-flow", "fast tmdb playback missing playable episode key=%s id=%s", getKey(), getId());
            applyCachedTmdbDetailNextFrame(item);
            return;
        }
        mFastPlaybackFlag = flag;
        mFastPlaybackEpisode = episode;
        if (!TextUtils.equals(mBinding.name.getText(), item.getName())) {
            mBinding.name.setText(item.getName());
        }
        CharSequence playbackTitle = getPlaybackControlTitle(episode);
        if (!TextUtils.equals(mBinding.widget.title.getText(), playbackTitle)) {
            mBinding.widget.title.setText(playbackTitle);
        }
        playerStartTime = System.currentTimeMillis();
        beginPlayHealth();
        // 快速 TMDB 起播绕过 getPlayer，仍需作废上一条播放的反馈状态
        resetAdFeedback();
        prepareFastTmdbPlaybackHistory(item, flag, episode);
        SpiderDebug.log("video-flow", "fast tmdb playback start cost=%dms key=%s flag=%s episode=%s url=%s", System.currentTimeMillis() - start, getKey(), flag.getFlag(), episode.getName(), episode.getUrl());
        beginPlayerContentRequest(getKey(), flag.getFlag(), episode.getUrl());
        mViewModel.playerContent(getKey(), flag.getFlag(), episode.getUrl(), applyHistoryPlayerKernel());
        mBinding.getRoot().post(() -> hydrateFastTmdbPlaybackDetail(item));
        applyFastTmdbPlaybackFullDetailNextFrame(item);
    }

    private void applyFastTmdbPlaybackFullDetailNextFrame(Vod item) {
        if (mFastTmdbFullDetailBound) return;
        mFastTmdbFullDetailBound = true;
        mBinding.getRoot().postDelayed(() -> {
            if (isFinishing() || isDestroyed()) return;
            long start = System.currentTimeMillis();
            prepareFastTmdbPlaybackItem(item);
            setDetail(Result.vod(item));
            SpiderDebug.log("video-flow", "fast tmdb full detail bind cost=%dms key=%s id=%s name=%s", System.currentTimeMillis() - start, getKey(), getId(), item.getName());
        }, TMDB_CACHED_DETAIL_APPLY_DELAY_MS);
    }

    private void prepareFastTmdbPlaybackItem(Vod item) {
        item.checkPic(firstNonEmpty(getPic(), getTmdbVodPic()));
        item.checkName(getName());
        item.checkContent(firstNonEmpty(item.getContent(), getTmdbVodContent(), getContent()));
        setIfEmpty(item.getYear(), getTmdbVodYear(), item::setYear);
        setIfEmpty(item.getArea(), getTmdbVodArea(), item::setArea);
        setIfEmpty(item.getTypeName(), getTmdbVodType(), item::setTypeName);
        setIfEmpty(item.getDirector(), getTmdbVodDirector(), item::setDirector);
        setIfEmpty(item.getActor(), getTmdbVodActor(), item::setActor);
    }

    private void hydrateFastTmdbPlaybackDetail(Vod item) {
        if (isFinishing() || isDestroyed() || item == null) return;
        long start = System.currentTimeMillis();
        boolean cacheHit = applyFastTmdbDetailCache(item);
        String content = firstNonEmpty(item.getContent(), getTmdbVodContent(), getContent());
        String artwork = firstNonEmpty(getInitialArtwork(item), getTmdbVodPic(), getPic());
        String wall = firstNonEmpty(cachedFastTmdbBackdrop(), getWallPic(), mHistory == null ? "" : mHistory.getWallPic());
        if (!TextUtils.isEmpty(content)) {
            item.setContent(content);
            mBinding.content.setTag(content);
            if (isTmdbMode() && !isIntentTmdbPlayback()) {
                suppressTmdbNativeTextFields();
                mBinding.tmdbOverview.setSingleLine(false);
                mBinding.tmdbOverview.setHorizontallyScrolling(false);
                mBinding.tmdbOverview.setMaxLines(Integer.MAX_VALUE);
                CharSequence overview = getString(R.string.detail_content, content);
                if (!TextUtils.equals(mBinding.tmdbOverview.getText(), overview)) {
                    mBinding.tmdbOverview.setText(overview);
                }
                mBinding.tmdbOverview.setVisibility(View.VISIBLE);
                mBinding.tmdbOverview.post(this::updateTmdbOverviewButton);
            }
        }
        if (!TextUtils.isEmpty(item.getName()) && !TextUtils.equals(mBinding.name.getText(), item.getName())) {
            mBinding.name.setText(item.getName());
        }
        if (!TextUtils.isEmpty(wall)) setContextWall(wall);
        if (mHistory != null) {
            if (!TextUtils.isEmpty(item.getName())) mHistory.setVodName(item.getName());
            enrichHistoryMeta(item);
            if (!TextUtils.isEmpty(wall)) mHistory.setWallPic(wall);
            if (!TextUtils.isEmpty(artwork)) {
                mHistory.setVodPic(artwork);
                loadArtwork(artwork);
                updateEpisodeFallbackStillUrl();
            }
            setMetadata();
            PlaybackEventCollector.get().updateHistory(mHistory);
        }
        SpiderDebug.log("video-flow", "fast tmdb detail hydrate cost=%dms key=%s content=%s artwork=%s cache=%s", System.currentTimeMillis() - start, getKey(), !TextUtils.isEmpty(content), !TextUtils.isEmpty(artwork), cacheHit);
    }

    private boolean applyFastTmdbDetailCache(Vod item) {
        TmdbDetailCache.Entry cached = takeFastTmdbDetailCache();
        if (cached == null || item == null) return false;
        mFastTmdbDetailCache = cached;
        JsonObject detail = cached.getDetail();
        String overview = cachedTmdbOverview(detail);
        if (!TextUtils.isEmpty(overview) && (TextUtils.isEmpty(item.getContent()) || overview.length() > item.getContent().length())) {
            item.setContent(overview);
        }
        String title = firstNonEmpty(cachedTmdbString(detail, "title"), cachedTmdbString(detail, "name"), cached.getItem() == null ? "" : cached.getItem().getTitle());
        if (!TextUtils.isEmpty(title) && TextUtils.isEmpty(item.getName())) item.setName(title);
        String artwork = firstNonEmpty(cachedFastTmdbPoster(cached), cachedFastTmdbBackdrop(cached));
        if (!TextUtils.isEmpty(artwork) && TextUtils.isEmpty(item.getPic())) item.setPic(artwork);
        return true;
    }

    private String cachedFastTmdbBackdrop() {
        return cachedFastTmdbBackdrop(mFastTmdbDetailCache);
    }

    private String cachedFastTmdbBackdrop(TmdbDetailCache.Entry cached) {
        if (cached == null) return "";
        return firstNonEmpty(cachedTmdbImage(cached.getDetail(), "backdrop_path", true), cached.getItem() == null ? "" : cached.getItem().getBackdropUrl());
    }

    private void setIfEmpty(String current, String value, java.util.function.Consumer<String> setter) {
        if (TextUtils.isEmpty(current) && !TextUtils.isEmpty(value)) setter.accept(value);
    }

    private String cachedFastTmdbPoster(TmdbDetailCache.Entry cached) {
        if (cached == null) return "";
        return firstNonEmpty(cachedTmdbImage(cached.getDetail(), "poster_path"), cached.getItem() == null ? "" : cached.getItem().getPosterUrl());
    }

    private TmdbDetailCache.Entry takeFastTmdbDetailCache() {
        if (mFastTmdbDetailCacheChecked) return mFastTmdbDetailCache;
        mFastTmdbDetailCacheChecked = true;
        mFastTmdbDetailCache = TmdbDetailCache.take(getIntent().getStringExtra(TmdbDetailCache.EXTRA_KEY), getTmdbItem());
        if (mFastTmdbDetailCache != null) {
            TmdbItem item = mFastTmdbDetailCache.getItem();
            SpiderDebug.log("video-flow", "fast tmdb detail memory-cache hit title=%s media=%s id=%d", item == null ? "" : item.getTitle(), item == null ? "" : item.getMediaType(), item == null ? 0 : item.getTmdbId());
        }
        return mFastTmdbDetailCache;
    }

    private String cachedTmdbOverview(JsonObject detail) {
        String overview = cachedTmdbString(detail, "overview");
        if (!TextUtils.isEmpty(overview)) return overview.trim();
        JsonArray translations = cachedTmdbArray(cachedTmdbObject(detail, "translations"), "translations");
        String language = cachedTmdbLanguage();
        overview = cachedTmdbOverviewForLanguage(translations, language);
        if (!TextUtils.isEmpty(overview)) return overview;
        overview = cachedTmdbOverviewForLanguage(translations, cachedTmdbLanguageRoot(language));
        if (!TextUtils.isEmpty(overview)) return overview;
        overview = cachedTmdbOverviewForLanguage(translations, "zh-CN");
        if (!TextUtils.isEmpty(overview)) return overview;
        overview = cachedTmdbOverviewForLanguage(translations, "zh");
        if (!TextUtils.isEmpty(overview)) return overview;
        return cachedTmdbOverviewForLanguage(translations, "en");
    }

    private String cachedTmdbOverviewForLanguage(JsonArray translations, String language) {
        if (translations == null || TextUtils.isEmpty(language)) return "";
        String target = language.toLowerCase(Locale.ROOT);
        for (JsonElement element : translations) {
            if (element == null || !element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            String iso = cachedTmdbString(object, "iso_639_1");
            String name = firstNonEmpty(cachedTmdbString(object, "name"), cachedTmdbString(object, "english_name"));
            String code = cachedTmdbString(object, "iso_3166_1");
            if (!cachedTmdbLanguageMatches(target, iso, code, name)) continue;
            String overview = cachedTmdbString(cachedTmdbObject(object, "data"), "overview");
            if (!TextUtils.isEmpty(overview)) return overview.trim();
        }
        return "";
    }

    private boolean cachedTmdbLanguageMatches(String target, String iso, String code, String name) {
        String root = cachedTmdbLanguageRoot(target);
        if (target.equalsIgnoreCase(iso) || target.equalsIgnoreCase(iso + "-" + code)) return true;
        if (!TextUtils.isEmpty(root) && root.equalsIgnoreCase(iso)) return true;
        return target.equalsIgnoreCase(name);
    }

    private String cachedTmdbLanguage() {
        try {
            return com.fongmi.android.tv.bean.TmdbConfig.objectFrom(Setting.getTmdbConfig()).getLanguage();
        } catch (Throwable e) {
            return "";
        }
    }

    private String cachedTmdbLanguageRoot(String language) {
        if (TextUtils.isEmpty(language)) return "";
        int separator = language.indexOf('-');
        return separator > 0 ? language.substring(0, separator) : language;
    }

    private String cachedTmdbImage(JsonObject detail, String key) {
        return cachedTmdbImage(detail, key, false);
    }

    private String cachedTmdbImage(JsonObject detail, String key, boolean backdrop) {
        String path = cachedTmdbString(detail, key);
        if (TextUtils.isEmpty(path)) return "";
        if (path.startsWith("http://") || path.startsWith("https://")) return path;
        String base = "";
        try {
            com.fongmi.android.tv.bean.TmdbConfig config = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(Setting.getTmdbConfig());
            base = backdrop ? config.getBackdropBase() : config.getImageBase();
        } catch (Throwable ignored) {
        }
        return TextUtils.isEmpty(base) ? path : base + (path.startsWith("/") ? path : "/" + path);
    }

    private JsonObject cachedTmdbObject(JsonObject object, String key) {
        if (object == null || TextUtils.isEmpty(key) || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonObject()) return null;
        return object.getAsJsonObject(key);
    }

    private JsonArray cachedTmdbArray(JsonObject object, String key) {
        if (object == null || TextUtils.isEmpty(key) || !object.has(key) || object.get(key).isJsonNull() || !object.get(key).isJsonArray()) return null;
        return object.getAsJsonArray(key);
    }

    private String cachedTmdbString(JsonObject object, String key) {
        if (object == null || TextUtils.isEmpty(key) || !object.has(key) || object.get(key).isJsonNull()) return "";
        try {
            return object.get(key).getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private Flag findFastTmdbPlaybackFlag(Vod item) {
        if (item == null) return null;
        Flag flag = findIntentPlaybackFlag(item.getFlags(), getIntentPlaybackFlagKey(),
                getIntentPlaybackFlag(), getIntentPlaybackEpisodeUrl());
        if (flag != null) return flag;
        return item.getFlags().isEmpty() ? null : item.getFlags().get(0);
    }

    private Episode findFastTmdbPlaybackEpisode(Flag flag) {
        Episode episode = findIntentPlaybackEpisode(flag, getIntentPlaybackEpisodeName(), getIntentPlaybackEpisodeUrl());
        if (episode != null) return episode;
        return flag == null || flag.getEpisodes().isEmpty() ? null : flag.getEpisodes().get(0);
    }

    private void prepareFastTmdbPlaybackHistory(Vod item, Flag flag, Episode episode) {
        mHistory = History.findPlayback(getHistoryKey(), List.of(item.getName(), getName()), item.getFlags(), getHistoryTmdbItem(), currentSourceSeasonNumber(item));
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getWallPic())) mHistory.setWallPic(getWallPic());
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        mHistory.setVodName(item.getName());
        if (!TextUtils.isEmpty(getInitialArtwork(item))) mHistory.setVodPic(getInitialArtwork(item));
        enrichHistoryMeta(item);
        selectFastTmdbPlaybackEpisode(item, flag, episode);
        updateFastTmdbPlaybackHistory(flag, episode);
        setOpeningEndingText();
        float speed = getPlaybackSpeed();
        mBinding.control.action.speed.setText(player().setSpeed(speed));
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    private void selectFastTmdbPlaybackEpisode(Vod item, Flag selectedFlag, Episode selectedEpisode) {
        if (item == null || selectedFlag == null || selectedEpisode == null) return;
        for (Flag flag : item.getFlags()) flag.toggle(flag == selectedFlag, selectedEpisode);
    }

    private void updateFastTmdbPlaybackHistory(Flag flag, Episode episode) {
        Episode historyEpisode = withSourceSeasonEpisodeIdentity(withIntentTmdbEpisodeIdentity(episode));
        // 快速 TMDB 播放在历史续播或聚合开启时，允许同一标准季集跨线路共享进度。
        boolean crossSource = mHistory.isCrossSourcePlayback();
        boolean shareEpisodeProgress = crossSource || isResumeFromHistory() || Setting.isHistoryAggregationEffective();
        boolean sameEpisode = historyEpisode.matchesPlayback(mHistory.getEpisode());
        boolean compatibleFlag = shareEpisodeProgress || TextUtils.equals(mHistory.getVodFlag(), flag.getFlag());
        if (!sameEpisode || !compatibleFlag) mIntroSkipPlayback.reset();
        if (!sameEpisode || !compatibleFlag) {
            EpisodePositionCache.EpisodePosition cached = skipEpisodePositionCache(flag) ? null : EpisodePositionCache.get().get(getKey(), getId(), flag.getFlag(), episodePositionCacheName(episode, currentSourceSeasonNumber()));
            if (cached != null) {
                mHistory.setPosition(cached.position);
                mHistory.setDuration(cached.duration);
            } else {
                mHistory.setPosition(C.TIME_UNSET);
                mHistory.setDuration(C.TIME_UNSET);
            }
        }
        setHistoryFlag(flag);
        mHistory.setVodRemarks(getHistoryEpisodeName(historyEpisode));
        mHistory.setEpisodeUrl(episode.getUrl());
        if (historyEpisode.getTmdbEpisode() != null || !sameEpisode) mHistory.setTmdbEpisodePosition(historyEpisode);
    }

    private void resetDetailForNewIntent() {
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.beginDetailRequest();
        detailRequested = false;
        detailHealthRecorded = false;
        playHealthRecorded = false;
        revealManualSearch = false;
        episodeGridMode = false;
        mPendingDetail = null;
        mPendingPlayer = null;
        mPendingFastTmdbPlaybackVod = null;
        App.removeCallbacks(mPendingFastTmdbPlaybackStart);
        mVod = null;
        mSourceVodName = "";
        mSourceEpisodeSeasonCache.clear();
        mFastTmdbDetailCache = null;
        mFastPlaybackFlag = null;
        mFastPlaybackEpisode = null;
        mFastTmdbPlaybackStarted = false;
        mFastTmdbDetailCacheChecked = false;
        mTmdbAutoDialogShown = false;
        mTmdbDetailLoading = false;
        mTmdbDetailRevealed = false;
        mTmdbEpisodeFallbackReleased = false;
        mPersonalRecommendationGeneration++;
        mTmdbDialogGeneration++;
        App.removeCallbacks(mR4);
        App.removeCallbacks(mTmdbDetailTimeout);
        App.removeCallbacks(mTmdbEpisodeTimeout);
        resetPendingTmdbBind();
        stopBackdropAutoScroll();
        mBackdropSignature = null;
        mBackdropSlideshowActive = false;
        if (mViewModel != null) mViewModel.stopSearch();
        if (mViewModel != null) mViewModel.cancelPlayerContent();
        invalidatePlayerContent();
        if (mBroken != null) mBroken.clear();
        clearDetailAdapters();
        clearTmdbDetailViews();
        mBinding.scroll.scrollTo(0, 0);
        mBinding.name.setText(getName());
        mBinding.widget.title.setText(getName());
        mBinding.video.requestFocus();
        if (service() != null) {
            player().reset();
            player().stop();
            player().clear();
        }
        updateNavigationKey();
        mBinding.progressLayout.showProgress();
    }

    private void clearDetailAdapters() {
        clearEpisodeGridDecoration();
        if (mFlagAdapter != null) mFlagAdapter.clear();
        if (mEpisodeAdapter != null) {
            mEpisodeAdapter.setUseTmdbCard(false);
            mEpisodeAdapter.clear();
        }
        if (mEpisodeGridAdapter != null) {
            mEpisodeGridAdapter.setUseTmdbCard(false);
            mEpisodeGridAdapter.clear();
        }
        if (mArrayAdapter != null) mArrayAdapter.clear();
        if (mQualityAdapter != null) mQualityAdapter.addAll(Result.empty());
        if (mPartAdapter != null) mPartAdapter.clear();
        if (mQuickAdapter != null) mQuickAdapter.clear();
        mBinding.episodeContainer.setVisibility(View.GONE);
        mBinding.episodeHeader.setVisibility(View.GONE);
        mBinding.episodeReverse.setVisibility(View.GONE);
        mBinding.episodeViewMode.setVisibility(View.GONE);
        mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
        mBinding.quality.setVisibility(View.GONE);
    }

    private void clearTmdbDetailViews() {
        clearTmdbGrid(mBinding.tmdbCast, R.id.tmdbCastLabel);
        clearTmdbGrid(mBinding.tmdbPhotos, R.id.tmdbPhotosLabel);
        clearTmdbGrid(mBinding.tmdbPosters, R.id.tmdbPostersLabel);
        clearTmdbGrid(mBinding.tmdbCrew, R.id.tmdbCrewLabel);
        clearTmdbGrid(mBinding.tmdbRecommendations, R.id.tmdbRecommendationsLabel);
        clearTmdbGrid(mBinding.tmdbRelatedVideos, R.id.tmdbRelatedVideosLabel);
        clearNativePersonalRecommendations();
        // 重置背景幻灯片去重签名：切换条目时 setupBackdropSlideshow 靠 signature 去重，
        // 不清则新条目剧照与旧 signature 判定“相同”而跳过更新，导致背景海报不换。
        stopBackdropAutoScroll();
        mBackdropSignature = null;
        mBinding.tmdbOverview.setText("");
        mBinding.tmdbOverview.setVisibility(View.GONE);
        mTmdbDetailFieldsApplied = false;
        View ratingsLabel = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatingsLabel);
        View ratings = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatings);
        if (ratingsLabel != null) ratingsLabel.setVisibility(View.GONE);
        if (ratings != null) ratings.setVisibility(View.GONE);
        setTmdbRematchVisible(false);
    }

    private void clearTmdbGrid(RecyclerView grid, int labelId) {
        grid.setAdapter(null);
        grid.setVisibility(View.GONE);
        View label = mBinding.getRoot().findViewById(labelId);
        if (label != null) label.setVisibility(View.GONE);
    }

    private void getDetail() {
        getDetail(false);
    }

    private void getDetail(boolean refresh) {
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = false;
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s refresh=%s", getKey(), getId(), getName(), refresh);
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
        getIntent().putExtra("key", item.getSiteKey());
        getIntent().putExtra("pic", item.getPic());
        getIntent().putExtra("id", item.getId());
        mBinding.scroll.scrollTo(0, 0);
        mClock.setCallback(null);
        updateNavigationKey();
        subtitlePlaybackSession.stop(this);
        mLyricsSearchSeq++;
        mLyricsRefreshSeq++;
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
        // 猫源设置项：点击的本意是开网页，detail 只是副产物。留在这儿会让内嵌页背后压着空白播放页，
        // 也不能走 setEmpty——它在 intent 带 name 时会拿动作名去别的站搜索。
        //
        // 必须排在等播放服务之前：既然不打算播放，就没有等它的理由；等下去还会让判定所依赖的
        // 时间关联窗口过期，重投时反而露出空白页。
        if (com.fongmi.android.tv.api.CatAction.shouldYieldDetail(getKey(), detailStartTime, result)) {
            SpiderDebug.log("video-flow", "detail yield to cat webview key=%s id=%s", getKey(), getId());
            finish();
            return;
        }
        if (service() == null) {
            mPendingDetail = result;
            SpiderDebug.log("video-flow", "detail pending service key=%s id=%s", getKey(), getId());
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
        mBinding.progressLayout.showEmpty();
    }

    private void setDetail(Vod item) {
        mSourceEpisodeSeasonCache.clear();
        mSourceVodName = item.getName();
        mVod = item;
        resetPendingTmdbBind();
        mTmdbAutoDialogShown = false;
        mTmdbDetailFieldsApplied = false;
        mTmdbEpisodeFallbackReleased = false;
        App.removeCallbacks(mTmdbEpisodeTimeout);
        item.checkPic(getPic());
        item.checkName(getName());
        boolean loadTmdbDetail = shouldLoadTmdbDetail();
        item.checkContent(getContent());
        applyIntentTmdbVodRemark(item);
        setOriginalEnhancedActionVisibility(loadTmdbDetail && (Setting.isOriginalEnhancedDetailPage() || isIntentTmdbPlayback()));
        if (isIntentTmdbPlayback()) com.fongmi.android.tv.utils.TmdbEpisodeSorter.sort(item);
        applyTmdbEpisodeTitles(item);
        setTmdbRematchVisible(loadTmdbDetail);
        // 非 TMDB：立即揭开，全部内容一次性出现；TMDB：继续停在 loading，等富集完成再揭开
        if (!loadTmdbDetail) mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mFlagAdapter.addAll(item.getFlags());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        if (!checkHistory(item)) return;
        checkFlag(item);
        checkKeepImg();
        setText(item);
        updateKeep();
        if (loadTmdbDetail) App.post(mTmdbEpisodeTimeout, TMDB_DETAIL_LOAD_TIMEOUT);
        if (loadTmdbDetail) hideNativePersonalRecommendations();
        else loadNativePersonalRecommendations(item);
        if (loadTmdbDetail && shouldShowTmdbLoadingOverlay()) showTmdbDetailLoading();
        else if (loadTmdbDetail) revealShellWhileTmdbLoads();

        // TMDB 增强：自动匹配并增强 Vod
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isReady()) {
            mTmdbUIAdapter.setActiveFlag(getFlag());
            com.fongmi.android.tv.bean.TmdbItem tmdbItem = getTmdbItem();
            if (tmdbItem != null) {
                SpiderDebug.log("tmdb-tv", "direct load vodTitle=%s tmdbTitle=%s tmdbId=%d media=%s cache=%s", item.getName(), tmdbItem.getTitle(), tmdbItem.getTmdbId(), tmdbItem.getMediaType(), mFastTmdbDetailCache != null);
                mTmdbUIAdapter.load(tmdbItem, item, mFastTmdbDetailCache);
            } else {
                mTmdbUIAdapter.autoMatch(item.getName(), item, getSearchKeyword());
            }
        }
    }

    private boolean shouldLoadTmdbDetail() {
        return mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
    }

    private boolean shouldShowTmdbLoadingOverlay() {
        return !mFastTmdbPlaybackStarted && !shouldRevealShellWhileLoading();
    }

    /**
     * 原生增强把详情与播放放在同一页：进入即揭开页面骨架，加载态只由播放器窗口内那一层表达，
     * 不再先整页转圈、揭开后再转一次，避免同一次进入出现两层「加载中」。
     */
    private boolean shouldRevealShellWhileLoading() {
        return Setting.isOriginalEnhancedDetailPage();
    }

    private void setOriginalEnhancedActionVisibility(boolean hide) {
        mBinding.shortDisplay.setVisibility(hide ? View.GONE : View.VISIBLE);
        mBinding.change1.setVisibility(hide ? View.VISIBLE : View.GONE);
        mBinding.searchDetail.setVisibility(hide ? View.GONE : View.VISIBLE);
        mBinding.keep.setNextFocusLeftId(hide ? R.id.change1 : R.id.searchDetail);
    }

    private void setTmdbRematchVisible(boolean visible) {
        mBinding.tmdbRematch.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.searchDetail.setNextFocusRightId(R.id.keep);
        mBinding.change1.setNextFocusRightId(R.id.keep);
        mBinding.keep.setNextFocusRightId(visible ? R.id.tmdbRematch : View.NO_ID);
        mBinding.tmdbRematch.setNextFocusLeftId(R.id.keep);
        mBinding.tmdbRematch.setNextFocusRightId(View.NO_ID);
    }

    private void showTmdbDetailLoading() {
        mTmdbDetailLoading = true;
        mTmdbDetailRevealed = false;
        // 全屏 loading：隐藏全部内容（含视频窗口），只留转圈，等 TMDB 富集完成或超时再一次性揭开
        if (!mBinding.progressLayout.isProgress()) mBinding.progressLayout.showProgress();
        // setText 等内容填充可能把 remark/actor 等子视图改回 VISIBLE，强制压回隐藏避免泄漏
        mBinding.progressLayout.hideContent();
        App.removeCallbacks(mTmdbDetailTimeout);
        App.post(mTmdbDetailTimeout, TMDB_DETAIL_LOAD_TIMEOUT);
        SpiderDebug.log("tmdb-tv", "detail loading show (full-screen progress)");
    }

    /**
     * 不遮挡整页的加载方式：站源详情到手即揭开骨架（视频窗口、选集、线路都在位），
     * TMDB 富集在原地补齐。会被 TMDB 覆盖的站源文本先压掉，避免揭开后再跳一次文本。
     */
    private void revealShellWhileTmdbLoads() {
        boolean hiddenByLoading = !mBinding.progressLayout.isContent();
        if (!mBinding.progressLayout.isContent()) mBinding.progressLayout.showContent();
        if (shouldUseTmdbLayout()) suppressTmdbNativeTextFields();
        if (hiddenByLoading && !mBinding.getRoot().hasFocus()) {
            mBinding.video.post(() -> {
                if (!mBinding.getRoot().hasFocus()) mBinding.video.requestFocus();
            });
        }
        SpiderDebug.log("tmdb-tv", "detail shell revealed while tmdb loads (single loading layer)");
    }

    // TMDB 数据成功返回：揭开内容（仅一次）并应用 TMDB 字段（每次都应用）
    private void finishTmdbDetail() {
        revealTmdbDetail();
        if (mTmdbDetailFieldsApplied) return;
        mTmdbDetailFieldsApplied = true;
        applyTmdbDetailFields();
    }

    // 揭开全屏 loading、一次性显示全部内容，幂等（超时或数据到达都会调用，只执行一次）
    private void revealTmdbDetail() {
        if (mTmdbDetailRevealed) return;
        boolean hiddenByLoading = !mBinding.progressLayout.isContent();
        mTmdbDetailRevealed = true;
        mTmdbDetailLoading = false;
        App.removeCallbacks(mTmdbDetailTimeout);
        mBinding.progressLayout.showContent();
        // 内容从 INVISIBLE 恢复为 VISIBLE 后，焦点需要重新回到播放器。
        // 骨架已经先揭开时不能再抢焦点，否则会把用户已经移到选集上的焦点拽回播放器。
        if (hiddenByLoading) mBinding.video.post(() -> mBinding.video.requestFocus());
        SpiderDebug.log("tmdb-tv", "detail loading reveal (show content)");
    }

    private void applyTmdbDetailFields() {
        setTmdbRematchVisible(true);
        // 炫彩详情已经在播放页显示前带入完整文本，异步 TMDB 完成时不要再切换右侧布局。
        if (isIntentTmdbPlayback()) {
            mBinding.video.setNextFocusRightId(R.id.content);
            return;
        }

        // 去掉站源自带的集数/演员/导演，集数改用规范化后的 TMDB 信息。
        suppressTmdbNativeTextFields();
        String episodeInfo = mTmdbUIAdapter == null ? "" : mTmdbUIAdapter.getEpisodeDetailText();
        setText(mBinding.remark, 0, episodeInfo);
        mBinding.content.setVisibility(View.GONE);

        // 年份、地区、类型取 TMDB
        if (mTmdbUIAdapter != null) {
            String year = mTmdbUIAdapter.getYear();
            String area = mTmdbUIAdapter.getArea();
            String genres = mTmdbUIAdapter.getGenresText();
            if (!TextUtils.isEmpty(year)) setText(mBinding.year, R.string.detail_year, year);
            if (!TextUtils.isEmpty(area)) setText(mBinding.area, R.string.detail_area, area);
            if (!TextUtils.isEmpty(genres)) setText(mBinding.type, R.string.detail_type, genres);
        }

        // 简介移到站源行下方显示；内容相同时不重置 maxLines，避免播放页再次布局闪动。
        Object desc = mBinding.content.getTag();
        String overview = desc == null ? "" : desc.toString();
        if (!TextUtils.isEmpty(overview)) {
            CharSequence overviewText = getString(R.string.detail_content, overview);
            if (!TextUtils.equals(mBinding.tmdbOverview.getText(), overviewText)) {
                mBinding.tmdbOverview.setSingleLine(false);
                mBinding.tmdbOverview.setHorizontallyScrolling(false);
                mBinding.tmdbOverview.setMaxLines(Integer.MAX_VALUE);
                mBinding.tmdbOverview.setText(overviewText);
                mBinding.tmdbOverview.post(this::updateTmdbOverviewButton);
            }
            mBinding.tmdbOverview.setVisibility(View.VISIBLE);
        } else {
            mBinding.tmdbOverview.setVisibility(View.GONE);
        }

        // 简介按钮默认隐藏，焦点先把视频右移到简介按钮（按钮显示时）或搜索按钮
        mBinding.video.setNextFocusRightId(R.id.content);
    }

    private void updateTmdbOverviewButton() {
        if (isFinishing() || isDestroyed()) return;
        TextView view = mBinding.tmdbOverview;
        android.text.Layout layout = view.getLayout();
        if (layout != null) {
            int maxLines = getTmdbOverviewMaxLines(view, layout);
            if (view.getMaxLines() != maxLines) {
                view.setMaxLines(maxLines);
                view.post(this::updateTmdbOverviewButton);
                return;
            }
        }
        boolean truncated = isTextTruncated(view);
        mBinding.content.setVisibility(truncated ? View.VISIBLE : View.GONE);
        mBinding.video.setNextFocusRightId(truncated ? R.id.content : R.id.keep);
    }

    private int getTmdbOverviewMaxLines(TextView view, android.text.Layout layout) {
        int rowTop = mBinding.row2.getTop();
        int viewTop = view.getTop();
        int verticalPadding = view.getCompoundPaddingTop() + view.getCompoundPaddingBottom();
        int available = rowTop - viewTop - verticalPadding - ResUtil.dp2px(TMDB_OVERVIEW_ROW_GAP_DP + TMDB_OVERVIEW_BOTTOM_GUARD_DP);
        int maxLines = 0;
        for (int i = 0; i < layout.getLineCount(); i++) {
            if (layout.getLineBottom(i) > available) break;
            maxLines = i + 1;
        }
        return Math.max(1, maxLines);
    }

    private boolean isTextTruncated(TextView view) {
        android.text.Layout layout = view.getLayout();
        if (layout == null) return false;
        int lines = layout.getLineCount();
        if (lines <= 0) return false;
        return layout.getEllipsisCount(lines - 1) > 0;
    }

    private void showTmdbDetailFallback() {
        if (mTmdbDetailRevealed) return;
        SpiderDebug.log("tmdb-tv", "detail loading overlay timeout fallback");
        revealTmdbDetail();
        suppressTmdbNativeTextFields();
        showTmdbEpisodeFallback();
    }

    private void showTmdbEpisodeFallback() {
        if (!isTmdbSourceEnabled() || mTmdbUIAdapter == null || !mTmdbUIAdapter.isReady()) return;
        if (mTmdbUIAdapter.isEpisodeMetadataLoaded()) {
            refreshEpisodeTitles();
            return;
        }
        // 不能再以「占位符可见」为前提：选集现在会先上屏，占位符只在一集都没有时出现。
        // 超时后必须置位 fallbackReleased，否则 tmdbEpisodeEnrichmentPending 永远为真，
        // TMDB 匹配失败时表头会永久挂着。
        mTmdbEpisodeFallbackReleased = true;
        SpiderDebug.log("tmdb-tv", "episode metadata timeout fallback, reveal native list");
        if (mBinding.episodeLoadingIndicator.getVisibility() == View.VISIBLE) finishEpisodeLoading();
        else refreshEpisodeTitles();
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        setDetailLyrics(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getDisplayName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.remark, 0, item.getRemarks());
        updateAudioStageText();
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        String value = resId > 0 ? getString(resId, text) : text;
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (TextUtils.equals(view.getText(), value)) return;
        view.setText(Sniffer.buildClickable(value, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setLinkTextColor(MDColor.YELLOW_500);
        CustomMovement.bind(view);
    }

    private ClickableSpan clickableSpan(Result result) {
        return new ClickableSpan() {
            @Override
            public void onClick(@NonNull View view) {
                VodActivity.start(getActivity(), getKey(), result);
                setRedirect(true);
            }
        };
    }

    private void applyTmdbEpisodeTitles(Vod vod) {
        Map<Integer, String> titles = getEpisodeTitles();
        if (vod == null || titles.isEmpty() || vod.getFlags() == null) return;
        for (Flag flag : vod.getFlags()) {
            for (Episode episode : flag.getEpisodes()) {
                String title = titles.get(episode.getNumber());
                if (TextUtils.isEmpty(title)) continue;
                String displayName = EpisodeTitleFormatter.withSourceFileSize(episode.getName(), EpisodeTitleFormatter.formatTmdbTitle(episode.getNumber(), title), Setting.isTmdbEpisodeFileSize());
                if (TextUtils.equals(episode.getDisplayName(), displayName)) continue;
                episode.setDisplayName(displayName);
            }
        }
    }

    private Map<Integer, String> getEpisodeTitles() {
        Map<Integer, String> titles = new HashMap<>();
        ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        if (values == null) return titles;
        for (String value : values) {
            String[] parts = value.split("\t", 2);
            if (parts.length != 2 || TextUtils.isEmpty(parts[1])) continue;
            try {
                titles.put(Integer.parseInt(parts[0]), parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        return titles;
    }

    private boolean isIntentTmdbPlayback() {
        ArrayList<String> values = getIntent().getStringArrayListExtra("tmdb_episode_titles");
        return values != null && !values.isEmpty();
    }

    @Override
    protected Vod getReaderVod() {
        // 把当前正在播放的 Vod（含章节列表）交给阅读器路由，支持小说/漫画章节导航
        return mVod;
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

    private void getPlayer(Flag flag, Episode episode) {
        resetAdFeedback();
        mBinding.widget.title.setText(getPlaybackControlTitle(episode));
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
        mBinding.widget.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void setPlayer(Result result) {
        if (result == null || isFinishing() || isDestroyed()) return;
        SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        if (service() == null) {
            mPendingPlayer = result;
            SpiderDebug.log("video-flow", "player pending service key=%s id=%s", getKey(), getId());
            return;
        }
        if (result.hasMsg() || result.getRealUrl().isEmpty()) {
            onError(result.hasMsg() ? result.getMsg() : getString(R.string.error_play_url));
            return;
        }
        if (!canApplyPlayerResult()) {
            SpiderDebug.log("video-flow", "drop player result before detail ready key=%s id=%s", getKey(), getId());
            return;
        }
        if (result == mAppliedPlayerResult && !player().isEmpty()) return;
        mAppliedPlayerResult = result;
        mQualityAdapter.addAll(result);
        mQualityAdapter.setPosition(mQualityAdapter.getPosition());
        setUseParse(result.shouldUseParse());
        setQualityVisible(result.getUrl().isMulti());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        else applyPlaybackArtwork(getPlaybackEpisode());
        if (result.hasDesc()) {
            mBinding.content.setTag(result.getDesc());
            setPlaybackLyrics(result.getDesc());
        }
        applyAudioQueueMetadata(getPlaybackEpisode());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        mBinding.control.parse.setVisibility(isUseParse() && PlayerButtonSetting.isVisible(PlayerButtonSetting.PARSE) ? View.VISIBLE : View.GONE);
        if (redirectToContentHandler(result)) return;
        List<Danmaku> siteDanmakus = result.getDanmaku();
        mInitialPlaybackPosition = resolveInitialPlaybackPosition();
        SpiderDebug.log("video-flow", "startPlayer dispatch initialPosition=%d music=%s ijk=%s", mInitialPlaybackPosition, isMusicLike(), service() != null && player().isIjk());
        long start = System.currentTimeMillis();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata(), mInitialPlaybackPosition);
        SpiderDebug.log("video-flow", "startPlayer return cost=%dms sincePlayerStart=%dms", System.currentTimeMillis() - start, System.currentTimeMillis() - playerStartTime);
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
        });
    }

    private boolean redirectToContentHandler(Result result) {
        boolean handled = com.fongmi.android.tv.content.ContentDispatcher.dispatchResult(this, getHistoryKey(), getKey(), getFlag().getFlag(), mHistory.getVodName(), mHistory.getVodPic(), getFlag().getEpisodes(), getSelectedEpisodePosition(getFlag().getEpisodes()), result, getSite().getTimeout());
        if (handled) {
            stopPlayback();
            // 阅读结果接管前台，但保留本页：一次返回回到来源播放页。
        }
        return handled;
    }

    private boolean canApplyPlayerResult() {
        if (mFlagAdapter != null && mFlagAdapter.getItemCount() > 0 && mEpisodeAdapter != null && mEpisodeAdapter.getItemCount() > 0) return true;
        return mFastPlaybackFlag != null && mFastPlaybackEpisode != null && mHistory != null;
    }

    private void beginPlayerContentRequest(String key, String flag, String episode) {
        mPendingPlayer = null;
        invalidatePlayerContent();
        playerContentKey = key;
        playerContentFlag = flag;
        playerContentEpisode = episode;
    }

    private void invalidatePlayerContent() {
        playerContentGeneration++;
        playerContentRequestId++;
        playerKernelSwitchRequestId++;
        playerContentKey = "";
        playerContentFlag = "";
        playerContentEpisode = "";
        playerKernelSwitchRefreshing = false;
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

    private boolean isCurrentPlayerContentRequest(int requestId, int generation,
                                                   String key, String flag, String episode) {
        return !isFinishing() && !isDestroyed()
                && requestId == playerContentRequestId
                && generation == playerContentGeneration
                && isCurrentPlayerContentContext(key, flag, episode);
    }

    private int beginPlayerContentSwitch(int requestId, String key, String flag, String episode) {
        playerContentGeneration++;
        playerContentRequestId = requestId;
        playerContentKey = key;
        playerContentFlag = flag;
        playerContentEpisode = episode;
        return playerContentGeneration;
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
        if (mFlagAdapter.getItemCount() == 0 || item == null) return;
        int position = mFlagAdapter.indexOf(item);
        Flag resolved = mFlagAdapter.get(position < 0 ? 0 : position);
        boolean initialBinding = mEpisodeAdapter.getItemCount() == 0;
        if (resolved.isSelected() && !initialBinding) return;
        Flag previous = getFlag();
        SpiderDebug.log("playback-action", "flag switch ui=leanback site=%s from=%s to=%s fullscreen=%s", getKey(), previous == null ? "" : previous.getFlag(), resolved.getFlag(), isFullscreen());
        int oldPosition = mFlagAdapter.getSelectedPosition();
        mFlagAdapter.setSelected(resolved);
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.setActiveFlag(resolved);
        int newPosition = mFlagAdapter.getSelectedPosition();
        if (newPosition != RecyclerView.NO_POSITION) mBinding.flag.setSelectedPosition(newPosition);
        notifyItemsChanged(mBinding.flag, mFlagAdapter, oldPosition, newPosition);
        setEpisodeAdapter(resolved.getEpisodes());
        setQualityVisible(false);
        boolean episodeChanged = seamless(resolved);
        if (initialBinding && !episodeChanged) onRefresh();
        loadTmdbRelatedVideosForCurrentEpisode();
    }

    private void setEpisodeAdapter(List<Episode> items) {
        setEpisodeAdapter(items, true);
    }

    private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent) {
        updateEpisodeSeasonContext();
        boolean isEmpty = items.isEmpty();
        boolean hasMultiple = items.size() > 1;
        boolean tmdbMode = isTmdbSourceEnabled();
        boolean tmdbAdapterReady = mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
        boolean tmdbEpisodeMetadataLoaded = mTmdbUIAdapter != null && mTmdbUIAdapter.isEpisodeMetadataLoaded();
        if (tmdbEpisodeMetadataLoaded) App.removeCallbacks(mTmdbEpisodeTimeout);
        // 富集是否仍在进行中。超时后保留已经揭开的原生列表，避免稍后的核心详情刷新再次把它隐藏。
        boolean tmdbEpisodeEnrichmentPending = !mTmdbEpisodeFallbackReleased
                && (mTmdbDetailLoading || (tmdbAdapterReady && !tmdbEpisodeMetadataLoaded));
        // 站源集数已可渲染时不再隐藏列表：先出纯文本，TMDB 到达后由 updateFlag / refreshTmdbEpisodeTitles
        // 原地换成卡片。只有一集都还没有时才需要占位符。
        boolean waitTmdbEpisodes = EpisodeDisplayPolicy.shouldWaitForTmdbEpisodes(tmdbMode, tmdbEpisodeEnrichmentPending, tmdbAdapterReady, tmdbEpisodeMetadataLoaded, items);
        // chrome 跟随「富集中」而非「隐藏中」：站源选集先上屏时表头就位，TMDB 卡片到达后
        // 不再二次插入表头造成列表跳动。
        boolean showTmdbEpisodeChrome = EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(tmdbMode, tmdbEpisodeEnrichmentPending, items);
        boolean useTmdbCards = EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(tmdbMode, items);
        mBinding.episodeContainer.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();

        if (shouldUseUpstreamNativeEpisodeModule()) {
            setUpstreamNativeEpisodeItems(items, scrollToCurrent);
            return;
        }

        // 网格模式跟随「是否真有卡片数据」而非 chrome 可见性：富集中的纯文本选集若进了
        // 按卡片宽度算 spanCount 的网格会明显错位，而此时 episodeViewMode 按钮又因
        // useTmdbCards=false 被隐藏，用户无法切回列表。
        if (useTmdbCards && hasMultiple) episodeGridMode = Setting.getTmdbEpisodeGridMode();
        if (!useTmdbCards || !hasMultiple) episodeGridMode = false;
        mBinding.episodeHeader.setVisibility(showTmdbEpisodeChrome && !isEmpty ? View.VISIBLE : View.GONE);
        mBinding.episodeReverse.setVisibility(showTmdbEpisodeChrome && hasMultiple ? View.VISIBLE : View.GONE);
        mBinding.episodeViewMode.setVisibility(showTmdbEpisodeChrome && hasMultiple && useTmdbCards ? View.VISIBLE : View.GONE);
        mBinding.episodeFileName.setVisibility(showTmdbEpisodeChrome && hasMultiple ? View.VISIBLE : View.GONE);
        updateEpisodeFallbackStillUrl();
        mEpisodeAdapter.setUseTmdbCard(useTmdbCards);
        mEpisodeGridAdapter.setUseTmdbCard(useTmdbCards);
        applyActionButtonVisibility();

        // 优化：只加载当前集所在分段的数据，避免一次性渲染所有集数导致卡顿
        int size = items.size();
        int segmentSize = getEpisodeSegmentSize(size);
        int selectedPosition = getSelectedEpisodePosition(items);
        int segmentStart = EpisodeRangePolicy.segmentStart(size, selectedPosition, segmentSize);
        List<Episode> initialItems = items.subList(segmentStart, Math.min(segmentStart + segmentSize, size));
        mEpisodeAdapter.addAll(initialItems);
        mEpisodeGridAdapter.addAll(initialItems);

        applyEpisodeViewMode(false);

        // 控制加载指示器和选集列表的显示
        if (waitTmdbEpisodes) {
            mBinding.episodeLoadingIndicator.setVisibility(View.VISIBLE);
            setEpisodeContentVisible(false);
        } else {
            mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
            setEpisodeContentVisible(true);
        }

        setArrayAdapter(size);
        selectEpisodeSegmentPosition(EpisodeRangePolicy.segmentIndex(size, selectedPosition, segmentSize) + 2);
        updateFocus();
        if (scrollToCurrent) scrollToCurrentEpisode();
        setR2Callback();
    }

    private void setUpstreamNativeEpisodeItems(List<Episode> items, boolean scrollToCurrent) {
        episodeGridMode = true;
        int column = EpisodeAdapter.getColumn(items);
        mBinding.episodeHeader.setVisibility(View.GONE);
        mBinding.episodeReverse.setVisibility(View.GONE);
        mBinding.episodeViewMode.setVisibility(View.GONE);
        mBinding.episodeFileName.setVisibility(View.GONE);
        mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
        mBinding.episode.setVisibility(View.GONE);
        mBinding.episodeGrid.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager gridLayoutManager) gridLayoutManager.setSpanCount(column);
        mEpisodeAdapter.setUseTmdbCard(false);
        mEpisodeGridAdapter.setUseTmdbCard(false);
        mEpisodeAdapter.setGridMode(false);
        mEpisodeAdapter.setVerticalGridMode(true);
        mEpisodeAdapter.setColumn(column);
        mEpisodeGridAdapter.setGridMode(true);
        mEpisodeGridAdapter.setVerticalGridMode(true);
        mEpisodeGridAdapter.setColumn(column);

        // 优化：只加载当前集所在分段的数据，避免一次性渲染所有集数导致卡顿
        int size = items.size();
        int segmentSize = getEpisodeSegmentSize(size);
        int selectedPosition = getSelectedEpisodePosition(items);
        int segmentStart = EpisodeRangePolicy.segmentStart(size, selectedPosition, segmentSize);
        List<Episode> initialItems = items.subList(segmentStart, Math.min(segmentStart + segmentSize, size));
        mEpisodeAdapter.addAll(initialItems);
        mEpisodeGridAdapter.addAll(initialItems);
        updateEpisodeGridViewport();
        updateUpstreamNativeEpisodeGridViewport();
        mBinding.episodeGrid.post(this::updateUpstreamNativeEpisodeGridViewport);
        setArrayAdapter(items.size());
        selectEpisodeSegmentPosition(EpisodeRangePolicy.segmentIndex(size, selectedPosition, segmentSize) + 2);
        updateFocus();
        if (scrollToCurrent) scrollToCurrentEpisode();
        setR2Callback();
    }

    // 兜底：占位符只在站源一集都没返回时出现（有集数就直接上屏了）。这里负责在 TMDB
    // 结束或超时后把它收掉，避免「正在加载剧集信息...」永久停留。
    private void finishEpisodeLoading() {
        if (mBinding.episodeLoadingIndicator.getVisibility() != View.VISIBLE) return;
        if (isTmdbSourceEnabled()
                && mTmdbUIAdapter != null
                && mTmdbUIAdapter.isReady()
                && !mTmdbEpisodeFallbackReleased
                && !mTmdbUIAdapter.isEpisodeMetadataLoaded()) return;
        episodeGridMode = false;
        mEpisodeAdapter.setUseTmdbCard(false);
        mEpisodeGridAdapter.setUseTmdbCard(false);
        applyEpisodeViewMode(false);
        mBinding.episodeHeader.setVisibility(View.GONE);
        mBinding.episodeReverse.setVisibility(View.GONE);
        mBinding.episodeViewMode.setVisibility(View.GONE);
        mBinding.episodeFileName.setVisibility(View.GONE);
        mBinding.episodeLoadingIndicator.setVisibility(View.GONE);
        setEpisodeContentVisible(true);
        if (mEpisodeAdapter != null) mEpisodeAdapter.notifyDataSetChanged();
        if (mEpisodeGridAdapter != null) mEpisodeGridAdapter.notifyDataSetChanged();
        SpiderDebug.log("tmdb-tv", "episode loading finished without tmdb episodes, reveal plain list");
    }

    private void refreshEpisodeTitles() {
        if (mEpisodeAdapter == null || mFlagAdapter == null || mFlagAdapter.getItemCount() == 0) return;
        int position = mEpisodeAdapter.getSelectedPosition();
        setEpisodeAdapter(getFlag().getEpisodes(), false);
        if (position != RecyclerView.NO_POSITION) scrollToEpisode(position);
    }

    private boolean seamless(Flag flag) {
        Episode episode = getMark().isEmpty() ? flag.find(mHistory.getEpisode(), true) : flag.find(mHistory.getVodRemarks(), false);
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return false;
        selectEpisode(episode, false);
        return true;
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        selectEpisode(item, true);
    }

    private void onEpisodeLongClick(Episode item) {
        com.fongmi.android.tv.ui.dialog.EpisodeDetailDialog.show(this, item, getSite());
    }

    private void selectEpisode(Episode item, boolean scrollToEpisode) {
        int oldPosition = mEpisodeAdapter.getSelectedPosition();
        mFlagAdapter.toggle(item);
        int newPosition = mEpisodeAdapter.indexOf(item);
        if (newPosition == RecyclerView.NO_POSITION) newPosition = mEpisodeAdapter.getSelectedPosition();
        mEpisodeAdapter.notifySelectionChanged(oldPosition, newPosition);
        if (mEpisodeGridAdapter != null) mEpisodeGridAdapter.notifySelectionChanged(oldPosition, newPosition);
        boolean episodeFocused = isEpisodeListFocused() || isEpisodeGridFocused();
        SpiderDebug.log("video-episode", "select old=%s new=%s focus=%s scroll=%s name=%s", oldPosition, newPosition, episodeFocused, scrollToEpisode, item.getName());
        if (scrollToEpisode && !episodeFocused) scrollToEpisode(newPosition);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        loadTmdbRelatedVideosForCurrentEpisode();
        onRefresh();
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.control.action.actionQuality.setVisibility(visible ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
        updateActionQuality(mViewModel.getPlayer().getValue());
        updateFocus();
        updateEpisodeWindow();
        setR2Callback();
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

    @Override
    public void onItemClick(Result result) {
        // 画质列表直点也会换 URL，且不经 changeQuality
        resetAdFeedback();
        updateActionQuality(result);
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        subtitlePlaybackSession.onPlaybackStarted(this, result);
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes(), scroll);
        if (scroll) scrollToCurrentEpisode();
        else scrollToFirstEpisode();
    }

    private void toggleEpisodeViewMode() {
        if (mBinding.episodeViewMode.getVisibility() != View.VISIBLE) return;
        episodeGridMode = !episodeGridMode;
        Setting.putTmdbEpisodeGridMode(episodeGridMode);
        applyEpisodeViewMode(true);
    }

    private void toggleEpisodeFileName() {
        if (mBinding.episodeFileName.getVisibility() != View.VISIBLE) return;
        boolean showScraped = !Setting.getTmdbEpisodeShowScrapedName();
        Setting.putTmdbEpisodeShowScrapedName(showScraped);
        setEpisodeAdapter(getFlag().getEpisodes(), true);
    }

    private void applyEpisodeViewMode(boolean scrollToCurrent) {
        if (mEpisodeAdapter == null || mEpisodeGridAdapter == null) return;
        int spanCount = getEpisodeGridSpanCount();
        RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
        if (layoutManager instanceof GridLayoutManager gridLayoutManager && gridLayoutManager.getSpanCount() != spanCount) {
            gridLayoutManager.setSpanCount(spanCount);
        }
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setVerticalSpacing(ResUtil.dp2px(8));
        mEpisodeAdapter.setGridMode(false);
        mEpisodeAdapter.setVerticalGridMode(false);
        mEpisodeAdapter.setColumn(1);
        mEpisodeGridAdapter.setGridMode(true);
        mEpisodeGridAdapter.setVerticalGridMode(true);
        mEpisodeGridAdapter.setColumn(spanCount);
        updateEpisodeGridViewport();
        mBinding.episodeViewMode.setText(episodeGridMode ? R.string.detail_episode_view_list : R.string.detail_episode_view_grid);
        mBinding.episodeViewMode.setContentDescription(getString(episodeGridMode ? R.string.detail_episode_view_list_action : R.string.detail_episode_view_grid_action));
        updateEpisodeFileNameButton();
        updateEpisodeReverseText();
        updateFocus();
        // 占位符可见时（站源一集都没有）不能把空列表揭出来。选集有内容时占位符恒不可见，
        // 所以这里实际上总是揭开——保留判断是为了覆盖"零集占位"那条路径。
        setEpisodeContentVisible(mBinding.episodeLoadingIndicator.getVisibility() != View.VISIBLE);
        if (scrollToCurrent) scrollToCurrentEpisode();
    }

    private void updateEpisodeReverseText() {
        if (mHistory == null) return;
        mBinding.episodeReverse.setText(mHistory.isRevSort() ? R.string.detail_episode_forward : R.string.detail_episode_reverse);
    }

    private void updateEpisodeFileNameButton() {
        boolean showScraped = Setting.getTmdbEpisodeShowScrapedName();
        mBinding.episodeFileName.setText(showScraped ? R.string.detail_episode_file_name_original : R.string.detail_episode_file_name_scraped);
    }

    private void setEpisodeContentVisible(boolean visible) {
        if (!visible) {
            mBinding.episode.setVisibility(View.INVISIBLE);
            mBinding.episode.setAlpha(0f);
            mBinding.episodeGrid.setVisibility(View.GONE);
            return;
        }
        if (episodeGridMode) {
            mBinding.episode.setVisibility(View.GONE);
            mBinding.episode.setAlpha(1f);
            mBinding.episodeGrid.setVisibility(View.VISIBLE);
            mBinding.episodeGrid.setAlpha(1f);
        } else {
            mBinding.episode.setVisibility(View.VISIBLE);
            mBinding.episode.setAlpha(1f);
            mBinding.episodeGrid.setVisibility(View.GONE);
        }
    }

    private void clearEpisodeGridDecoration() {
        if (episodeGridDecoration == null) return;
        mBinding.episodeGrid.removeItemDecoration(episodeGridDecoration);
        episodeGridDecoration = null;
    }

    private void updateEpisodeGridViewport() {
        clearEpisodeGridDecoration();
        ViewGroup.LayoutParams params = mBinding.episodeGrid.getLayoutParams();
        if (params.height != ViewGroup.LayoutParams.WRAP_CONTENT) {
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            mBinding.episodeGrid.setLayoutParams(params);
        }
        mBinding.episodeGrid.setNestedScrollingEnabled(false);
    }

    private void updateUpstreamNativeEpisodeGridViewport() {
        int spacing = ResUtil.dp2px(12);
        int height = getUpstreamNativeEpisodeGridHeight(spacing);
        ViewGroup.LayoutParams params = mBinding.episodeGrid.getLayoutParams();
        if (params.height != height) {
            params.height = height;
            mBinding.episodeGrid.setLayoutParams(params);
        }
        RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
        int spanCount = layoutManager instanceof GridLayoutManager gridLayoutManager ? gridLayoutManager.getSpanCount() : 2;
        // 列数会随标题长度变化，旧 Decoration 会继续按过期的 spanCount 计算左右偏移。
        clearEpisodeGridDecoration();
        mBinding.episodeGrid.addItemDecoration(episodeGridDecoration = new SpaceItemDecoration(spanCount, 12));
        mBinding.episodeGrid.setNestedScrollingEnabled(true);
    }

    private int getUpstreamNativeEpisodeGridHeight(int spacing) {
        int available = getEpisodeAvailableHeight(mBinding.episodeGrid);
        if (available > 0) return available;
        int rows = ResUtil.getScreenHeight() < ResUtil.dp2px(560) ? 2 : 3;
        return ResUtil.dp2px(64) * rows + spacing * Math.max(0, rows - 1) + mBinding.episodeGrid.getPaddingTop() + mBinding.episodeGrid.getPaddingBottom();
    }

    private void scrollToCurrentEpisode() {
        scrollToEpisode(mEpisodeAdapter.getPosition());
    }

    private void scrollToFirstEpisode() {
        scrollToEpisode(0, true);
    }

    private void scrollToEpisode(int position) {
        scrollToEpisode(position, false);
    }

    private void scrollToEpisode(int position, boolean requestFocus) {
        if (position < 0 || position >= mEpisodeAdapter.getItemCount()) return;
        if (episodeGridMode) {
            mBinding.episodeGrid.post(() -> {
                updateEpisodeWindowNow();
                RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
                if (layoutManager instanceof GridLayoutManager gridLayoutManager) {
                    gridLayoutManager.scrollToPositionWithOffset(position, 0);
                } else {
                    mBinding.episodeGrid.scrollToPosition(position);
                }
                if (!requestFocus) return;
                mBinding.episodeGrid.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    RecyclerView.ViewHolder holder = mBinding.episodeGrid.findViewHolderForAdapterPosition(position);
                    if (holder != null) holder.itemView.requestFocus();
                    else mBinding.episodeGrid.requestFocus();
                });
            });
        } else {
            mBinding.episode.post(() -> {
                updateEpisodeWindowNow();
                mBinding.episode.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    mBinding.episode.setSelectedPosition(position);
                    if (requestFocus) mBinding.episode.requestFocus();
                });
            });
        }
    }

    private void updateEpisodeWindow() {
        if (mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0) return;
        mBinding.episode.post(this::updateEpisodeWindowNow);
    }

    private void updateEpisodeWindowNow() {
        // HorizontalGridView不需要设置固定高度
        // 已改为横向滚动，让其自动适应内容高度
    }

    private int getEpisodeWindowHeight() {
        int column = Math.max(1, EpisodeAdapter.getColumn(mEpisodeAdapter.getItems()));
        int totalRows = Math.max(1, (mEpisodeAdapter.getItemCount() + column - 1) / column);
        int rowHeight = ResUtil.dp2px(40);
        int spacing = mBinding.episode.getVerticalSpacing();
        int maxRows = getEpisodeMaxRows(rowHeight, spacing);
        int rows = Math.min(totalRows, maxRows);
        return rowHeight * rows + spacing * Math.max(0, rows - 1) + mBinding.episode.getPaddingTop() + mBinding.episode.getPaddingBottom();
    }

    private int getEpisodeMaxRows(int rowHeight, int spacing) {
        int legacyRows = ResUtil.getScreenHeight() < ResUtil.dp2px(560) ? 2 : 3;
        int available = getEpisodeAvailableHeight();
        if (available <= 0) return legacyRows;
        int content = Math.max(0, available - mBinding.episode.getPaddingTop() - mBinding.episode.getPaddingBottom());
        int rows = (content + spacing) / (rowHeight + spacing);
        return Math.max(legacyRows, rows);
    }

    private int getEpisodeAvailableHeight() {
        return getEpisodeAvailableHeight(mBinding.episode);
    }

    private int getEpisodeAvailableHeight(View episodeView) {
        int height = mBinding.scroll.getHeight();
        if (height <= 0) return 0;
        int available = height - mBinding.scroll.getPaddingTop() - mBinding.scroll.getPaddingBottom();
        if (mBinding.scroll.getChildCount() == 0 || !(mBinding.scroll.getChildAt(0) instanceof ViewGroup group)) return available;
        View target = episodeView;
        while (target.getParent() instanceof View parent && parent != group) target = parent;
        ViewGroup.LayoutParams episodeParams = target.getLayoutParams();
        if (episodeParams instanceof ViewGroup.MarginLayoutParams margins) available -= margins.topMargin + margins.bottomMargin;
        available -= group.getPaddingTop() + group.getPaddingBottom();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == target || child.getVisibility() == View.GONE) continue;
            available -= child.getMeasuredHeight();
            ViewGroup.LayoutParams params = child.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams margins) available -= margins.topMargin + margins.bottomMargin;
        }
        return available;
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

    private void setArrayAdapter(int size) {
        int segment = getEpisodeSegmentSize(size);
        List<String> items = new ArrayList<>();
        items.add(getString(R.string.play_reverse));
        items.add(getString(mHistory.getRevPlayText()));
        mBinding.array.setVisibility(size > 1 ? View.VISIBLE : View.GONE);
        if (mHistory.isRevSort()) for (int i = size; i > 0; i -= segment) items.add(i + "-" + Math.max(i - segment + 1, 1));
        else for (int i = 0; i < size; i += segment) items.add((i + 1) + "-" + Math.min(i + segment, size));
        mArrayAdapter.setSegmentSize(segment);
        mArrayAdapter.addAll(items);
        updateFocus();
    }

    private int getEpisodeSegmentSize(int size) {
        // 优化：减小分段大小，避免一次性渲染过多卡片
        if (size <= 30) return size;   // 30集以下不分段
        if (size <= 60) return 20;     // 31-60集，每段20集
        if (size <= 100) return 30;    // 61-100集，每段30集
        return 40;                     // 100集以上，每段40集
    }

    private int findFocusDown(int index) {
        List<Integer> orders = getEpisodeFocusOrders();
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isEpisodeFocusTarget(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = getEpisodeFocusOrders();
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isEpisodeFocusTarget(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int episodeFocusIndex(int id) {
        return getEpisodeFocusOrders().indexOf(id);
    }

    private boolean isEpisodeFocusTarget(View view) {
        return view != null && isVisible(view) && (view.isFocusable() || view.hasFocusable());
    }

    private List<Integer> getEpisodeFocusOrders() {
        return Arrays.asList(
                R.id.flag,
                R.id.quality,
                R.id.array,
                R.id.episodeTitle,
                R.id.episodeReverse,
                R.id.episodeViewMode,
                R.id.episodeFileName,
                R.id.episode,
                R.id.episodeGrid,
                R.id.tmdbCast,
                R.id.tmdbPhotos,
                R.id.tmdbPosters,
                R.id.tmdbRelatedVideos,
                R.id.tmdbCrew,
                R.id.tmdbRecommendations,
                R.id.tmdbPersonalTmdbRecommendations,
                R.id.tmdbPersonalDoubanRecommendations,
                R.id.tmdbPersonalAiRecommendations,
                R.id.part,
                R.id.quick);
    }

    private void updateFocus() {
        int array = episodeFocusIndex(R.id.array);
        int episode = episodeFocusIndex(R.id.episode);
        int episodeGrid = episodeFocusIndex(R.id.episodeGrid);
        int part = episodeFocusIndex(R.id.part);
        int quick = episodeFocusIndex(R.id.quick);
        mArrayAdapter.setNextFocus(findFocusUp(array), findFocusDown(array));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(episode));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(episode));
        if (mEpisodeGridAdapter != null) {
            mEpisodeGridAdapter.setNextFocusUp(findFocusUp(episodeGrid));
            mEpisodeGridAdapter.setNextFocusDown(findFocusDown(episodeGrid));
        }
        updateEpisodeHeaderFocus();
        mPartAdapter.setNextFocus(findFocusUp(part), findFocusDown(part));
        mQuickAdapter.setNextFocus(findFocusUp(quick), findFocusDown(quick));
        setDetailButtonsNextFocus(findFocusDown(-1));
    }

    private void updateEpisodeHeaderFocus() {
        int title = episodeFocusIndex(R.id.episodeTitle);
        int reverse = episodeFocusIndex(R.id.episodeReverse);
        int fileName = episodeFocusIndex(R.id.episodeFileName);
        int up = findFocusUp(isEpisodeFocusTarget(mBinding.episodeTitle) ? title : reverse);
        int down = findFocusDown(fileName);
        int upId = up == 0 ? View.NO_ID : up;
        int downId = down == 0 ? View.NO_ID : down;
        boolean titleFocusable = isEpisodeFocusTarget(mBinding.episodeTitle);
        int firstTool = firstEpisodeHeaderToolId();
        mBinding.episodeTitle.setNextFocusUpId(upId);
        mBinding.episodeTitle.setNextFocusDownId(downId);
        mBinding.episodeTitle.setNextFocusRightId(firstTool == View.NO_ID ? View.NO_ID : firstTool);
        mBinding.episodeReverse.setNextFocusUpId(upId);
        mBinding.episodeReverse.setNextFocusDownId(downId);
        mBinding.episodeReverse.setNextFocusLeftId(titleFocusable ? R.id.episodeTitle : View.NO_ID);
        mBinding.episodeReverse.setNextFocusRightId(isEpisodeFocusTarget(mBinding.episodeViewMode)
                ? R.id.episodeViewMode
                : isEpisodeFocusTarget(mBinding.episodeFileName) ? R.id.episodeFileName : View.NO_ID);
        mBinding.episodeViewMode.setNextFocusUpId(upId);
        mBinding.episodeViewMode.setNextFocusDownId(downId);
        mBinding.episodeViewMode.setNextFocusLeftId(isEpisodeFocusTarget(mBinding.episodeReverse)
                ? R.id.episodeReverse : titleFocusable ? R.id.episodeTitle : View.NO_ID);
        mBinding.episodeViewMode.setNextFocusRightId(isEpisodeFocusTarget(mBinding.episodeFileName)
                ? R.id.episodeFileName : View.NO_ID);
        mBinding.episodeFileName.setNextFocusUpId(upId);
        mBinding.episodeFileName.setNextFocusDownId(downId);
        mBinding.episodeFileName.setNextFocusLeftId(isEpisodeFocusTarget(mBinding.episodeViewMode)
                ? R.id.episodeViewMode
                : isEpisodeFocusTarget(mBinding.episodeReverse)
                ? R.id.episodeReverse : titleFocusable ? R.id.episodeTitle : View.NO_ID);
    }

    private int firstEpisodeHeaderToolId() {
        if (isEpisodeFocusTarget(mBinding.episodeReverse)) return R.id.episodeReverse;
        if (isEpisodeFocusTarget(mBinding.episodeViewMode)) return R.id.episodeViewMode;
        if (isEpisodeFocusTarget(mBinding.episodeFileName)) return R.id.episodeFileName;
        return View.NO_ID;
    }

    private boolean onEpisodeHeaderToolKey(View view, int keyCode, KeyEvent event) {
        if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (!KeyUtil.isActionDown(event)) return true;
        if (KeyUtil.isLeftKey(event) && view == mBinding.episodeReverse && isEpisodeFocusTarget(mBinding.episodeTitle)) {
            mBinding.episodeTitle.requestFocus(View.FOCUS_LEFT);
            return true;
        }
        if (KeyUtil.isRightKey(event) && view == mBinding.episodeReverse && isEpisodeFocusTarget(mBinding.episodeViewMode)) {
            mBinding.episodeViewMode.requestFocus(View.FOCUS_RIGHT);
            return true;
        }
        if (KeyUtil.isRightKey(event) && view == mBinding.episodeReverse && !isEpisodeFocusTarget(mBinding.episodeViewMode) && isEpisodeFocusTarget(mBinding.episodeFileName)) {
            mBinding.episodeFileName.requestFocus(View.FOCUS_RIGHT);
            return true;
        }
        if (KeyUtil.isRightKey(event) && view == mBinding.episodeViewMode && isEpisodeFocusTarget(mBinding.episodeFileName)) {
            mBinding.episodeFileName.requestFocus(View.FOCUS_RIGHT);
            return true;
        }
        if (KeyUtil.isLeftKey(event) && view == mBinding.episodeFileName && isEpisodeFocusTarget(mBinding.episodeViewMode)) {
            mBinding.episodeViewMode.requestFocus(View.FOCUS_LEFT);
            return true;
        }
        if (KeyUtil.isLeftKey(event) && view == mBinding.episodeFileName && !isEpisodeFocusTarget(mBinding.episodeViewMode) && isEpisodeFocusTarget(mBinding.episodeReverse)) {
            mBinding.episodeReverse.requestFocus(View.FOCUS_LEFT);
            return true;
        }
        if (KeyUtil.isLeftKey(event) && view == mBinding.episodeViewMode && isEpisodeFocusTarget(mBinding.episodeReverse)) {
            mBinding.episodeReverse.requestFocus(View.FOCUS_LEFT);
            return true;
        }
        return true;
    }

    private boolean isEpisodeListFocused() {
        return isFocusInside(mBinding.episode);
    }

    private boolean isEpisodeGridFocused() {
        return isFocusInside(mBinding.episodeGrid);
    }

    private boolean isFocusInside(View view) {
        View focus = getCurrentFocus();
        while (focus != null) {
            if (focus == view) return true;
            ViewParent parent = focus.getParent();
            focus = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private boolean onArrayKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isDownKey(event)) return false;
        int position = mBinding.array.getSelectedPosition();
        if (position <= 1) return false;
        selectEpisodeSegment(position, true);
        return true;
    }


    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return false;
        RecyclerView episodeView = episodeGridMode ? mBinding.episodeGrid : mBinding.episode;
        View focus = getCurrentFocus();
        if (focus == null) return false;
        RecyclerView.ViewHolder holder = episodeView.findContainingViewHolder(focus);
        if (holder == null) return false;
        int position = holder.getBindingAdapterPosition();
        if (position == RecyclerView.NO_POSITION) return false;
        if (episodeGridMode) {
            RecyclerView.LayoutManager layoutManager = mBinding.episodeGrid.getLayoutManager();
            int spanCount = layoutManager instanceof GridLayoutManager gridLayoutManager ? gridLayoutManager.getSpanCount() : getEpisodeGridSpanCount();
            if (KeyUtil.isDownKey(event)) {
                int target = TmdbEpisodeGridPolicy.verticalFocusTarget(position, spanCount, mEpisodeGridAdapter.getItemCount(), true);
                return target != TmdbEpisodeGridPolicy.NO_FOCUS_TARGET && focusEpisodeGridPosition(target);
            }
            if (KeyUtil.isUpKey(event)) {
                int target = TmdbEpisodeGridPolicy.verticalFocusTarget(position, spanCount, mEpisodeGridAdapter.getItemCount(), false);
                if (target != TmdbEpisodeGridPolicy.NO_FOCUS_TARGET) return focusEpisodeGridPosition(target);
            } else {
                return false;
            }
        } else if (position != 0) {
            return false;
        } else if (!KeyUtil.isUpKey(event)) {
            return false;
        }
        int target = findFocusUp(episodeFocusIndex(episodeGridMode ? R.id.episodeGrid : R.id.episode));
        if (target == 0) return false;
        View view = findViewById(target);
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        view.requestFocus();
        return true;
    }

    private boolean focusEpisodeGridPosition(int position) {
        RecyclerView.ViewHolder holder = mBinding.episodeGrid.findViewHolderForAdapterPosition(position);
        if (holder != null && holder.itemView.requestFocus()) return true;
        mBinding.episodeGrid.scrollToPosition(position);
        mBinding.episodeGrid.post(() -> {
            RecyclerView.ViewHolder next = mBinding.episodeGrid.findViewHolderForAdapterPosition(position);
            if (next != null) next.itemView.requestFocus();
        });
        return true;
    }

    @Override
    public void onRevSort() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    @Override
    public void onRevPlay(TextView view) {
        mHistory.setRevPlay(!mHistory.isRevPlay());
        view.setText(mHistory.getRevPlayText());
        Notify.show(mHistory.getRevPlayHint());
    }

    @Override
    public void onSegmentClick(int position) {
        selectEpisodeSegment(position, false);
    }

    @Override
    public void onSegmentFocus(int position) {
        selectEpisodeSegment(position, false);
    }

    private void selectEpisodeSegment(int position, boolean requestEpisodeFocus) {
        if (position <= 1) return;
        selectEpisodeSegmentPosition(position);
        showEpisodeSegment(position);
        if (requestEpisodeFocus) scrollToEpisode(getSelectedEpisodePosition(mEpisodeAdapter.getItems()), true);
    }

    private void selectEpisodeSegmentPosition(int position) {
        if (position <= 1 || position >= mArrayAdapter.getItemCount()) return;
        mArrayAdapter.setSelectedPosition(position);
        mBinding.array.setSelectedPosition(position);
        mBinding.array.scrollToPosition(position);
    }

    private void showEpisodeSegment(int position) {
        List<Episode> episodes = getFlag().getEpisodes();
        if (episodes.isEmpty()) return;
        int start = mArrayAdapter.getStart(position);
        int end = Math.min(start + getEpisodeSegmentSize(episodes.size()), episodes.size());
        int selectedPosition = getSelectedEpisodePosition(episodes);
        int positionInSegment = selectedPosition >= start && selectedPosition < end ? selectedPosition - start : 0;
        List<Episode> items = episodes.subList(start, end);
        mEpisodeAdapter.addAll(items);
        mEpisodeGridAdapter.addAll(items);
        scrollToEpisode(positionInSegment);
    }

    private boolean shouldEnterFullscreen(Episode item) {
        boolean enter = !isFullscreen() && item.isSelected();
        if (enter) enterFullscreen();
        return enter;
    }

    private void enterFullscreen() {
        mFocus1 = getCurrentFocus();
        mBinding.video.requestFocus();
        mBinding.video.setForeground(null);
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        mBinding.flag.setSelectedPosition(mFlagAdapter.getPosition());
        mKeyDown.setFull(true);
        setFullscreen(true);
        mFocus2 = null;
    }

    private void exitFullscreen() {
        if (mAudioStageVisible) {
            mBinding.video.setForeground(null);
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
            mBinding.video.setLayoutParams(mFrameParams);
        }
        restoreEmbeddedVideoLayoutAfterFullscreen();
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
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
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onSearch() {
        String keyword = mBinding.name.getText().toString();
        if (TextUtils.isEmpty(keyword)) return;
        initSearch(keyword, false);
    }

    private void onGlobalSearch() {
        String keyword = mBinding.name.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) keyword = getName();
        if (TextUtils.isEmpty(keyword)) return;
        SearchActivity.start(this, keyword);
    }

    private void onShortDisplay() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        setShortDisplay();
        refreshEpisodeTitles();
    }

    private void setShortDisplay() {
        mBinding.shortDisplay.setSelected(Setting.isCompactEpisodeTitle());
    }

    private void onCodecCapability() {
        CodecCapabilityDialog.show(this, player());
        hideControl();
    }

    private void onSetting() {
        ControlDialog.create().parent(mBinding).history(mHistory).parse(isUseParse()).player(player()).show(this);
    }

    private void onCast() {
        if (mHistory == null || TextUtils.isEmpty(mHistory.getVodId()) || service() == null || player().isEmpty() || TextUtils.isEmpty(player().getUrl())) {
            Notify.show(R.string.cast_not_ready);
            return;
        }
        CastVideo video = new CastVideo(Objects.toString(mBinding.widget.title.getText(), ""), player().getUrl(), player().getPosition(), player().getHeaders());
        CastDialog.create().history(mHistory).video(video).fm(true).show(this);
    }

    private void onTimer() {
        TimerDialog.create().show(this);
    }

    private void onKeep() {
        Keep keep = Keep.find(getHistoryKey());
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        checkKeepImg();
    }

    private void onVideo() {
        if (!isFullscreen()) enterFullscreen();
    }

    private void onChange() {
        checkSearch(true);
    }

    private void onFullscreen() {
        boolean exit = isFullscreen();
        if (exit) exitFullscreen();
        else enterFullscreen();
        showControl(exit ? mBinding.control.action.fullscreen : mBinding.control.action.player);
    }

    private void onEpisodes() {
        if (mFlagAdapter.getItemCount() == 0 || mEpisodeAdapter.getItemCount() < 2) return;
        hideControl();
        Flag flag = getFlag();
        boolean tmdbCard = flag != null && EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(isTmdbSourceEnabled(), flag.getEpisodes());
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).tmdbCard(tmdbCard)
                .fallbackStill(getEpisodeFallbackStillUrl())
                .seasons(getEpisodeDialogSeasons(), getEpisodeDialogSeasonCounts()).show(this);
    }

    private java.util.List<Integer> getEpisodeDialogSeasons() {
        return new java.util.ArrayList<>(getEpisodeDialogSeasonCounts().keySet());
    }

    private java.util.Map<Integer, Integer> getEpisodeDialogSeasonCounts() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return java.util.Map.of();
        return mTmdbUIAdapter.getSeasonEpisodeCounts();
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
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

    private void onPanDiagnostic() {
        if (!canRunPanDiagnostic()) return;
        mDialogReturnFocus = mBinding.control.action.panDiagnostic;
        App.removeCallbacks(mR1);
        PanNetworkDiagnosticDialog.show(this, player());
    }

    private void setPlayParamsState() {
        mBinding.control.action.playParams.setSelected(mOsd != null && mOsd.isDiagnosticsVisible());
        mBinding.control.action.multiThreadProxy.setSelected(MultiThreadProxySetting.get().enabled());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        if (mHistory.isRevPlay()) onPrev(notify);
        else onNext(notify);
    }

    /** @return 是否真的切走了。末集/倒序首集切不动，调用方据此决定要不要提示「进入下一集」。 */
    private boolean advanceEpisode(boolean notify) {
        return mHistory.isRevPlay() ? onPrev(notify) : onNext(notify);
    }

    private void checkPrev() {
        if (mHistory.isRevPlay()) onNext(true);
        else onPrev(true);
    }

    private boolean onNext(boolean notify) {
        Episode item = getAdjacentEpisode(1);
        if (!item.isSelected()) {
            onItemClick(item);
            return true;
        }
        if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
        return false;
    }

    private boolean onPrev(boolean notify) {
        Episode item = getAdjacentEpisode(-1);
        if (!item.isSelected()) {
            onItemClick(item);
            return true;
        }
        if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
        return false;
    }

    private Episode getAdjacentEpisode(int offset) {
        Flag flag = getFlag();
        List<Episode> episodes = flag.getEpisodes();
        if (episodes.isEmpty()) return new Episode();
        int position = getSelectedEpisodePosition(episodes);
        position = Math.max(0, Math.min(position + offset, episodes.size() - 1));
        return episodes.get(position);
    }

    private void onScale() {
        showResizeModeDialog(getScale(), this::setScale);
    }

    public void onScale(int tag) {
        setScale(tag);
    }

    private void onLut() {
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
        focusLutQuickIfVisible();
    }

    private void focusLutQuickIfVisible() {
        mBinding.lutQuick.post(this::focusLutQuickContent);
        mBinding.lutQuick.postDelayed(this::focusLutQuickContent, 220);
        mBinding.lutQuick.postDelayed(this::focusLutQuickContent, 420);
    }

    private boolean focusLutQuickContent() {
        if (!isVisible(mBinding.lutQuick)) return false;
        View focus = getCurrentFocus();
        RecyclerView recycler = findRecyclerView(mBinding.lutQuick);
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && focus != recycler) return true;
        if (mBinding.lutQuick.focusSelectedEntry()) return true;
        if (focusRecyclerItem(recycler)) return true;
        return focusFirstChild(mBinding.lutQuick);
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
        if (view.isFocusable() && view.requestFocus()) return true;
        return false;
    }

    private boolean isChildOf(ViewGroup parent, View child) {
        for (View view = child; view != null; ) {
            if (view == parent) return true;
            if (!(view.getParent() instanceof View next)) return false;
            view = next;
        }
        return false;
    }

    public void onLutImport() {
        if (!LutStore.hasUserDir()) {
            pendingLutImport = true;
            chooseLutDir();
            return;
        }
        chooseLutFile();
    }

    public void onLutDir() {
        pendingLutImport = false;
        chooseLutDir();
    }


    private void chooseLutFile() {
        FileChooser.from(mLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseLutDir() {
        FileChooser.from(mLutDir).showDirectory();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        saveUserSpeed();
        setR1Callback();
    }

    private void onSpeedAdd() {
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
        saveUserSpeed();
        setR1Callback();
    }

    private void onSpeedSub() {
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
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
        if (mFlagAdapter.getItemCount() == 0) return;
        if (mEpisodeAdapter.getItemCount() == 0) return;
        getPlayer(getFlag(), getEpisode());
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
    }

    private void onOpeningAdd() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) + 1000));
    }

    private void onOpeningSub() {
        setOpening(Math.max(0, Math.max(0, mHistory.getOpening()) - 1000));
    }

    private boolean onOpeningReset() {
        // 长按清空是「这里不要有值」，别紧接着又把探测值渲染上去，看起来像没清掉
        mIntroSkipPlayback.suppressDetected(true);
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        setOpeningEndingText();
        syncHistory();
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

    private void onEnding() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetEnding(position, duration)) setEnding(duration - position);
    }

    private void onEndingAdd() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) + 1000));
    }

    private void onEndingSub() {
        setEnding(Math.max(0, Math.max(0, mHistory.getEnding()) - 1000));
    }

    private boolean onEndingReset() {
        mIntroSkipPlayback.suppressDetected(false);
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        setOpeningEndingText();
        syncHistory();
    }

    private void onPlayerKernel() {
        if (playerKernelSwitchRefreshing) return;
        PlayerKernelDialog.show(this, player().getPlayerType(), this::switchPlayerKernel, this::onExternalPlayer);
    }

    private void onExternalPlayer() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
        setRedirect(true);
    }

    private void switchPlayerKernel(int type) {
        if (refreshAndSwitchPlayerKernel(type)) return;
        mClock.setCallback(null);
        clearLyrics();
        player().switchPlayer(type);
        setPlayerKernel();
        setDecode();
        rememberPlayerKernel(type);
    }

    private boolean onPlayerKernelLong() {
        onPlayerKernel();
        return true;
    }

    private boolean refreshAndSwitchPlayerKernel(int type) {
        if (playerKernelSwitchRefreshing) return true;
        Flag currentFlag = getFlag();
        Episode currentEpisode = getEpisode();
        if (currentFlag == null || currentEpisode == null || TextUtils.isEmpty(currentFlag.getFlag()) || TextUtils.isEmpty(currentEpisode.getUrl())) return false;
        int nextType = PlayerSetting.sanitizePlayer(type);
        long position = Math.max(0, player().getPosition());
        float speed = player().getSpeed();
        boolean repeat = player().isRepeatOne();
        String key = getKey();
        String flag = currentFlag.getFlag();
        String episode = currentEpisode.getUrl();
        MediaMetadata metadata = buildMetadata();
        int requestId = ++playerKernelSwitchRequestId;
        int generation = beginPlayerContentSwitch(requestId, key, flag, episode);
        playerKernelSwitchRefreshing = true;
        mClock.setCallback(null);
        clearLyrics();
        SpiderDebug.log("video-flow", "switch player refresh start type=%d key=%s flag=%s episode=%s", nextType, key, flag, episode);
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episode, nextType);
                App.post(() -> switchPlayerKernelWithResult(requestId, generation, key, flag, episode, nextType, result, position, speed, repeat, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    if (!isCurrentPlayerContentRequest(requestId, generation, key, flag, episode)) return;
                    playerKernelSwitchRefreshing = false;
                    setPlayerKernel();
                    setDecode();
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
        playerKernelSwitchRefreshing = false;
        if (!canApplyPlayerContentRequest(requestId, generation, key, flag, episode)) return;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            Notify.show(result != null && result.hasMsg() ? result.getMsg() : getString(R.string.error_play_url));
        } else {
            player().switchPlayer(type, result, activePlaybackKey(), metadata, isUseParse(), position, speed, repeat);
            rememberPlayerKernel(type);
        }
        setPlayerKernel();
        setDecode();
    }

    private void onDecode() {
        mClock.setCallback(null);
        player().toggleDecode();
        setDecode();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).search(this::showSubtitleSearch).show(this);
        hideControl();
    }

    private void onTrack(int type) {
        TrackDialog.create().type(type).player(player()).search(this::showSubtitleSearch).show(this);
        hideControl();
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
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
            return mHistory == null ? getName() : mHistory.getVodName();
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

    private boolean onDanmakuToggle() {
        DanmakuSetting.putShow(!DanmakuSetting.isShow());
        applyDanmakuState();
        Notify.show(DanmakuSetting.isShow() ? R.string.danmaku_show_on : R.string.danmaku_show_off);
        return true;
    }

    private void applyDanmakuState() {
        boolean show = DanmakuSetting.isShow();
        if (player() != null) player().setDanmakuEnabled(show);
        mBinding.control.action.danmaku.setSelected(show);
    }

    private void onToggle() {
        if (isVisible(mBinding.control.getRoot())) hideControl();
        else showControl(getFocus2());
    }

    private void showProgress() {
        if (mAudioStageVisible) {
            hideProgress();
            hideCenter();
            hideError();
            return;
        }

        if (mSeekProgressFallback != null) App.removeCallbacks(mSeekProgressFallback);
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        if (mSeekProgressFallback != null) App.removeCallbacks(mSeekProgressFallback);
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset(mBinding.progress.traffic);
    }

    private void showPlaybackContent() {
        if (!mBinding.progressLayout.isContent()) mBinding.progressLayout.showContent();
        hideProgress();
    }

    private void showError(String text) {
        mBinding.widget.error.setVisibility(View.VISIBLE);
        mBinding.widget.text.setText(text);
        hideProgress();
    }

    private void hideError() {
        mBinding.widget.error.setVisibility(View.GONE);
        mBinding.widget.text.setText("");
    }

    private void showInfo() {
        showTopInfo();
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(0));
    }

    private void showTopInfo() {
        // 控制栏显示时，统一由 OSD 显示标题（即使用户关闭了 OSD 设置）
        // 所以这里始终隐藏 widget.top，避免与 OSD 重复显示
        mBinding.widget.top.setVisibility(View.GONE);
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showControl(View view) {
        showTopInfo();
        setPlayParamsState();
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        if (mOsd != null) mOsd.setControlsVisible(true);
        PlayerControlFocusHelper.ensureFocus(mBinding.control.getRoot(), view);
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(false);
        if (player().isPlaying()) mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
        if (isGone(mBinding.control.getRoot())) mBinding.widget.top.setVisibility(View.GONE);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback() {
        App.post(mR2, 500);
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
        mArtworkRequestOwner = owner;
        String colorKey = Objects.toString(owner, "") + "|" + Objects.toString(url, "");
        if (TextUtils.isEmpty(url)) {
            mBinding.exo.setDefaultArtwork(null);
            mBinding.audioCover.setImageResource(R.drawable.artwork);
            updateAudioArtworkColor(colorKey, null);
            return;
        }
        mBinding.audioCover.setImageResource(R.drawable.artwork);
        int size = ResUtil.dp2px(256);
        ImgUtil.load(this, url, size, size, new CustomTarget<>() {
            @Override
            public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                if (!TextUtils.equals(mArtworkRequestOwner, owner)) return;
                mBinding.exo.setDefaultArtwork(resource);
                mBinding.audioCover.setImageDrawable(resource);
                scheduleAudioArtworkColorUpdate(owner, colorKey, resource);
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!TextUtils.equals(mArtworkRequestOwner, owner)) return;
                mBinding.exo.setDefaultArtwork(errorDrawable);
                if (errorDrawable == null) mBinding.audioCover.setImageResource(R.drawable.artwork);
                else mBinding.audioCover.setImageDrawable(errorDrawable);
                scheduleAudioArtworkColorUpdate(owner, colorKey, errorDrawable);
            }
        });
    }

    private String getContextWall() {
        if (!TextUtils.isEmpty(getWallPic())) return getWallPic();
        return mHistory == null ? "" : mHistory.getWallPic();
    }

    private String lockContextWall(String url) {
        String wall = Objects.toString(url, "");
        if (mContextWallLockedUrl == null && !TextUtils.isEmpty(wall)) mContextWallLockedUrl = wall;
        return mContextWallLockedUrl == null ? wall : mContextWallLockedUrl;
    }

    private void setContextWall(String url) {
        // TMDB 背景幻灯片激活时，contextWall 让位于底层跟随 TMDB 条目更新的 slideshow，
        // 不再翻回顶层遮挡（否则背景固定为进入页时的 wallPic，切换条目不生效）。
        if (mBackdropSlideshowActive) return;
        if (!Setting.isPlaybackArtworkWall()) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        String wall = lockContextWall(url);
        if (TextUtils.isEmpty(wall)) {
            mContextWallUrl = "";
            hideContextWall();
            return;
        }
        if (Objects.equals(mContextWallUrl, wall)) return;
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
            }

            @Override
            public void onLoadFailed(@Nullable Drawable errorDrawable) {
                if (!Objects.equals(mContextWallUrl, wall)) return;
                mContextWallUrl = "";
                hideContextWall();
            }
        });
    }

    private void resetContextWallAlpha() {
        mBinding.contextWall.animate().cancel();
        mBinding.contextWall.setAlpha(1f);
    }

    private void hideContextWall() {
        // 清空 url，使后续 setContextWall 不会因去重（Objects.equals(mContextWallUrl, wall)）
        // 而跳过重新加载——否则从“有剧照”切到“无剧照”条目时 contextWall 会保持隐藏、背景变黑。
        mContextWallUrl = "";
        resetContextWallAlpha();
        mBinding.contextWall.setImageDrawable(null);
        mBinding.contextWall.setBackgroundColor(0x00000000);
        mBinding.contextWall.setVisibility(View.GONE);
    }

    private void setPartAdapter() {
        mPartAdapter.clear();
        mBinding.part.setVisibility(View.GONE);
        updateFocus();
    }

    private void checkFlag(Vod item) {
        boolean empty = item.getFlags().isEmpty();
        mBinding.flag.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) {
            startFlow();
        } else if (mImmersiveAudioResolved != null) {
            applyImmersiveAudioSelection(mImmersiveAudioResolved);
            if (mHistory.isRevSort()) reverseEpisode(true);
        } else if (mFastTmdbPlaybackStarted && mFastPlaybackFlag != null && mFastPlaybackEpisode != null) {
            mFlagAdapter.setSelected(mFastPlaybackFlag);
            mBinding.flag.setSelectedPosition(mFlagAdapter.indexOf(mFastPlaybackFlag));
            notifyItemChanged(mBinding.flag, mFlagAdapter);
            setEpisodeAdapter(mFastPlaybackFlag.getEpisodes(), false);
            setQualityVisible(false);
            if (mHistory.isRevSort()) reverseEpisode(true);
        } else {
            onItemClick(resolveHistoryPlaybackFlag(item.getFlags()));
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
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
        setPartAdapter();
        return true;
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
        String url = getEpisodeFallbackStillUrl();
        if (mEpisodeAdapter != null) mEpisodeAdapter.setFallbackStillUrl(url);
        if (mEpisodeGridAdapter != null) mEpisodeGridAdapter.setFallbackStillUrl(url);
    }

    private String getEpisodeFallbackStillUrl() {
        // 分集无专属剧照时的兜底图按设备比例选：宽屏优先横向 backdrop，窄屏优先纵向海报，
        // 首选比例缺图时退到另一种，避免宽卡片被竖海报撑裂或窄卡片留大片空白。
        return EpisodeCardImagePolicy.fallbackFor(
                getEpisodeFallbackBackdropUrl(), getEpisodeFallbackPosterUrl(), isWideScreen());
    }

    private String getEpisodeFallbackPosterUrl() {
        // getPosterUrl() 内部已带 tmdbItem.getPosterUrl() 兜底。取当前 TMDB 条目自己的图，
        // 切换条目后兜底图才会跟随当前剧集，而非停留在进场时固定的 getPic()/tmdb_vod_pic。
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            String poster = mTmdbUIAdapter.getPosterUrl();
            if (!TextUtils.isEmpty(poster)) return poster;
        }
        if (!TextUtils.isEmpty(getPic())) return getPic();
        if (!TextUtils.isEmpty(getTmdbVodPic())) return getTmdbVodPic();
        if (mVod != null && !TextUtils.isEmpty(mVod.getPic())) return mVod.getPic();
        return mHistory == null ? "" : mHistory.getVodPic();
    }

    private String getEpisodeFallbackBackdropUrl() {
        // 有 TMDB 条目时只认它自己的剧照：mHistory 的 wall 会被 cachedFastTmdbBackdrop() 写入，
        // 重新匹配后残留的是上一条匹配的横图；退它等于让旧背景图挡在当前条目的海报前面。
        // 没有 TMDB 条目（纯原生源）才退 intent 的 wallPic —— 它随 onNewIntent 的 putExtras
        // 跟随条目刷新，且全仓只喂给 setContextWall，是这里唯一可信的横图，宽卡片靠它免吃竖海报。
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return getWallPic();
        java.util.List<String> photos = mTmdbUIAdapter.getPhotos();
        return photos == null || photos.isEmpty() ? "" : photos.get(0);
    }

    private boolean isWideScreen() {
        return ResUtil.getScreenWidth(this) >= ResUtil.getScreenHeight(this);
    }

    private boolean hasInitialPreview() {
        return !getName().isEmpty() || !getPic().isEmpty() || !getWallPic().isEmpty();
    }

    private void showInitialPreview() {
        mBinding.progressLayout.showContent();
        mBinding.name.setText(getName());
        if (!getContent().isEmpty()) mBinding.content.setTag(getContent());
        if (!getPic().isEmpty()) setArtwork(getPic());
        else if (!getWallPic().isEmpty()) setContextWall(getWallPic());
        mBinding.video.requestFocus();
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
        android.util.Log.d("VideoActivity", "saveHistory: exit=" + exit + " mHistory=" + (mHistory != null) +
            " canSave=" + (mHistory != null ? mHistory.canSave() : "null") +
            " incognito=" + Setting.isIncognito());
        if (mHistory == null || Setting.isIncognito()) return;
        if (service() != null && isOwner()) {
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
        if (!mHistory.canSave()) return;
        History history = mHistory.copy();
        Task.execute(() -> {
            if (history.getDuration() > 0) history.merge().save();
            else history.save();
            android.util.Log.d("VideoActivity", "saveHistory: saved! key=" + history.getKey());
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

    private void checkKeepImg() {
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(Keep.find(getHistoryKey()) == null ? R.drawable.ic_detail_keep_off : R.drawable.ic_detail_keep_on, 0, 0, 0);
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
        mSourceEpisodeSeasonCache.clear();
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
        if (name && !TextUtils.equals(mBinding.name.getText(), item.getName())) {
            mBinding.name.setText(item.getName());
        }
        // 原生增强：TMDB 富集完成后回写题材/地区/演员/主创到 History（enrichVod 已填充 item），仅补空字段
        if (mHistory != null) mHistory.enrichMeta(item.getTypeName(), item.getArea(), item.getActor(), item.getDirector(), item.getYear());
        // 跨源聚合：TMDB 匹配完成后把 tmdbId 盖章到 History，供列表去重与跨源续播使用（不依赖脆弱的名称回查缓存）
        boolean tmdbIdStamped = stampHistoryTmdbId();
        updateFlag(getFlag(), item.getFlags());
        if (historyReloaded) resumeHistoryAfterTmdbMatch();
        boolean episodeTitleChanged = refreshCurrentHistoryEpisodeTitle();
        CharSequence playbackTitle = getPlaybackControlTitle();
        if (!TextUtils.equals(mBinding.widget.title.getText(), playbackTitle)) mBinding.widget.title.setText(playbackTitle);
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        // key 迁移后必须写回，避免 replace 删旧 key 后未 save 导致历史消失
        if (keyChanged || pic || name || episodeTitleChanged || tmdbIdStamped) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        if (name) setPartAdapter();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);
        if (shouldUseTmdbLayout()) suppressTmdbNativeTextFields();
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
     * TMDB 匹配完成后把 tmdbId/mediaType 盖章到当前 History。
     * 直接用已匹配的 TmdbItem，绕开 enrichTmdbId 依赖的脆弱名称回查缓存，
     * 保证每条历史都能可靠打上 tmdbId，供列表去重与跨源续播使用。
     *
     * @return 是否有写入（需要 syncHistory 持久化）
     */
    private boolean stampHistoryTmdbId() {
        if (mHistory == null || !Setting.isHistoryAggregationEffective()) return false;
        TmdbItem item = getMatchedTmdbItem();
        if (item == null || item.getTmdbId() <= 0) return false;
        if (mHistory.getTmdbId() == item.getTmdbId() && TextUtils.equals(mHistory.getMediaType(), item.getMediaType())) return false;
        mHistory.setTmdbId(item.getTmdbId());
        mHistory.setMediaType(item.getMediaType());
        return true;
    }

    private boolean shouldUseTmdbLayout() {
        return mTmdbUIAdapter != null && mTmdbUIAdapter.isReady();
    }

    private String tmdbEpisodeCompactText() {
        return mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()
                ? "" : mTmdbUIAdapter.getEpisodeCompactText();
    }

    private void suppressTmdbNativeTextFields() {
        if (isIntentTmdbPlayback()) return;
        mBinding.remark.setVisibility(View.GONE);
        mBinding.actor.setVisibility(View.GONE);
        mBinding.director.setVisibility(View.GONE);
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
     * 季号不可靠且该线路混排多季时，放弃集数播放位置缓存。
     * 这种线路里两季都有"第01集"，裸集名会让没看过的一集读到另一季已看完的进度、直接跳到结尾。
     * 从头开始播比跳到错误位置好。写入侧一并跳过，避免继续污染这些有歧义的槽位。
     */
    private boolean skipEpisodePositionCache() {
        return skipEpisodePositionCache(getFlag());
    }

    private boolean skipEpisodePositionCache(Flag flag) {
        return currentSourceSeasonNumber() < 0 && mSourceEpisodeSeasonCache.hasMixedSeasons(flag);
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
        if (mHistory == null || mFlagAdapter == null || mFlagAdapter.getItemCount() == 0) return false;
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
                    target.mergeEpisodes(item.getEpisodes(), mHistory.isRevSort());
                    // 选集不再等 TMDB 才上屏，占位符只在「一集都没有」时出现，与「已有卡片数据」
                    // 互斥，原先那条淡入分支已无法命中，故只保留常规刷新。
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
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
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
        refreshLyrics();
    }

    @Override
    protected void onPlayerRebuilt() {
        setPlayerKernel();
        setDecode();
        refreshControlDialog();
    }

    // 设置弹窗自己不会跟着引擎重建刷新，回退期间开着弹窗就会一直显示旧的内核/解码。
    private void refreshControlDialog() {
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof ControlDialog dialog) dialog.setPlayer();
        }
    }

    @Override
    protected void onTracksChanged() {
        refreshLyrics();
        setTrackVisible();
        mClock.setCallback(this);
        // 轨道要等新引擎 prepare 完才回来，重建那一刻按钮还是隐藏态，弹窗必须在这里再抄一次。
        refreshControlDialog();
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
        if (!canApplyPlayerResult()) return;
        Result result = mViewModel.getPlayer().getValue();
        if (result != null) setPlayer(result);
    }

    @Override
    protected void onStateChanged(int state) {
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                mKaraokeResultShown = false;
                recordPlayHealth(true, "");
                showPlaybackContent();
                boolean pendingResumeSeekApplied = applyPendingResumeSeek();
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
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        syncKaraokePosition();
        checkAudioPlayImg(isPlaying);
        if (isPlaying) {
            hideCenter();
        } else if (isPaused()) {
            if (isFullscreen()) showInfo();
            else hideInfo();
        }
    }

    @Override
    protected void onSizeChanged(VideoSize size) {
        applyResizeMode(getScale());
        mBinding.widget.size.setText(player().getSizeText());
    }

    @Override
    protected void onSurfaceAttached() {
        applyResizeMode(getScale());
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
     * <p>挂在 mR3（网速刷新，圈可见时每秒一跳）上，圈不可见时该循环本就已停，无额外开销。
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
        App.post(this::hideControl, 100);
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
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail(true);
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD_CORE) {
            if (!isCurrentVodEvent(event.getVod())) {
                SpiderDebug.log("tmdb-tv", "drop stale vod event current=%s/%s event=%s/%s", getKey(), getId(), event.getVod() == null ? "" : event.getVod().getSiteKey(), event.getVod() == null ? "" : event.getVod().getId());
                return;
            }
            queueTmdbBind(event.getVod());
            updateEpisodeSeasonContext();
            updateFocus();
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
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().reloadDanmaku(Danmaku.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.HISTORY) refreshPersonalRecommendationsForHistory();
    }

    private boolean isCurrentVodEvent(Vod item) {
        return VodEventGuard.matches(item, getKey(), getId(), mVod == null ? "" : mVod.getId());
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
                SpiderDebug.log("personal-rec", "tv native core failed error=%s", e.getMessage());
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
                SpiderDebug.log("personal-rec", "tv native ai failed error=%s", e.getMessage());
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
        boolean hasTmdb = bindRecommendationGrid(mBinding.tmdbPersonalTmdbRecommendations, mBinding.tmdbPersonalTmdbRecommendationsLabel, mNativePersonalTmdbPage.getItems(), RecommendationRow.PERSONAL_TMDB);
        boolean hasDouban = bindRecommendationGrid(mBinding.tmdbPersonalDoubanRecommendations, mBinding.tmdbPersonalDoubanRecommendationsLabel, mNativePersonalDoubanPage.getItems(), RecommendationRow.PERSONAL_DOUBAN);
        boolean hasAi = mNativePersonalAiPage != null && !mNativePersonalAiPage.getItems().isEmpty();
        setNativePersonalRecommendationFocus(hasTmdb, hasDouban, hasAi);
    }

    private void bindNativePersonalAiRecommendation(PersonalRecommendationService.RecommendationPage page) {
        mNativePersonalAiPage = page == null ? PersonalRecommendationService.RecommendationPage.empty("") : page;
        boolean hasTmdb = mNativePersonalTmdbPage != null && !mNativePersonalTmdbPage.getItems().isEmpty();
        boolean hasDouban = mNativePersonalDoubanPage != null && !mNativePersonalDoubanPage.getItems().isEmpty();
        boolean hasAi = bindRecommendationGrid(mBinding.tmdbPersonalAiRecommendations, mBinding.tmdbPersonalAiRecommendationsLabel, mNativePersonalAiPage.getItems(), RecommendationRow.PERSONAL_AI);
        setNativePersonalRecommendationFocus(hasTmdb, hasDouban, hasAi);
    }

    private void applyNativePersonalTmdbRatings(PersonalRecommendationService.RecommendationPage page, int generation) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration || page == null) return;
            List<TmdbItem> current = new ArrayList<>();
            if (mPersonalTmdbObjectAdapter != null) {
                for (int index = 0; index < mPersonalTmdbObjectAdapter.size(); index++) {
                    Object item = mPersonalTmdbObjectAdapter.get(index);
                    if (item instanceof TmdbItem) current.add((TmdbItem) item);
                }
            }
            if (mPersonalTmdbObjectAdapter == null) return;
            boolean changed = com.fongmi.android.tv.ui.helper.TmdbUIAdapter.mergeRecommendationRatings(current, page.getItems());
            mNativePersonalTmdbPage = page.withItems(current);
            if (changed && mPersonalTmdbObjectAdapter != null) {
                for (int index = 0; index < Math.min(current.size(), mPersonalTmdbObjectAdapter.size()); index++) {
                    mPersonalTmdbObjectAdapter.replace(index, current.get(index));
                }
            }
        });
    }

    private boolean bindTmdbVideoGrid(HorizontalGridView grid, View label, List<TmdbVideo> items) {
        if (items == null || items.isEmpty()) {
            grid.setAdapter(null);
            grid.setVisibility(View.GONE);
            label.setVisibility(View.GONE);
            return false;
        }
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(new com.fongmi.android.tv.ui.presenter.TmdbVideoPresenter(this::onTmdbRelatedVideoClick));
        adapter.addAll(0, items);
        grid.setAdapter(new ItemBridgeAdapter(adapter));
        grid.setVisibility(View.VISIBLE);
        label.setVisibility(View.VISIBLE);
        return true;
    }

    private void onTmdbRelatedVideoClick(TmdbVideo item) {
        TmdbVideoPlayback.play(this, item);
    }

    private boolean bindRecommendationGrid(HorizontalGridView grid, View label, List<TmdbItem> items) {
        return bindRecommendationGrid(grid, label, items, RecommendationRow.NONE);
    }

    private boolean bindRecommendationGrid(HorizontalGridView grid, View label, List<TmdbItem> items, RecommendationRow row) {
        if (items == null || items.isEmpty()) {
            grid.setVisibility(View.GONE);
            label.setVisibility(View.GONE);
            rememberRecommendationAdapter(row, null);
            if (row == RecommendationRow.PERSONAL_AI) showAiRecommendationReason(null, false);
            return false;
        }
        com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter presenter;
        if (row == RecommendationRow.PERSONAL_AI) {
            presenter = new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick, item -> onRecommendationLongClick(item, "ai"), this::onAiRecommendationFocus);
        } else if (row == RecommendationRow.PERSONAL_TMDB) {
            presenter = new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick, item -> onRecommendationLongClick(item, "tmdb"), null);
        } else if (row == RecommendationRow.PERSONAL_DOUBAN) {
            presenter = new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick, item -> onRecommendationLongClick(item, "douban"), null);
        } else if (row == RecommendationRow.RECOMMENDATIONS) {
            presenter = new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick, item -> onRecommendationLongClick(item, "related"), null);
        } else {
            presenter = new com.fongmi.android.tv.ui.presenter.TmdbRecommendationPresenter(this::onTmdbRecommendationClick);
        }
        ArrayObjectAdapter adapter = new ArrayObjectAdapter(presenter);
        adapter.addAll(0, items);
        grid.setAdapter(new ItemBridgeAdapter(adapter));
        rememberRecommendationAdapter(row, adapter);
        grid.setVisibility(View.VISIBLE);
        label.setVisibility(View.VISIBLE);
        return true;
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
        mPersonalTmdbObjectAdapter = null;
        mPersonalDoubanObjectAdapter = null;
        mPersonalAiObjectAdapter = null;
        mBinding.tmdbPersonalTmdbRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalTmdbRecommendationsLabel.setVisibility(View.GONE);
        mBinding.tmdbPersonalDoubanRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalDoubanRecommendationsLabel.setVisibility(View.GONE);
        mBinding.tmdbPersonalAiRecommendations.setVisibility(View.GONE);
        mBinding.tmdbPersonalAiRecommendationsLabel.setVisibility(View.GONE);
        showAiRecommendationReason(null, false);
        setNativePersonalRecommendationFocus(false, false, false);
    }

    private boolean onRecommendationLongClick(TmdbItem item, String source) {
        com.fongmi.android.tv.ui.dialog.AiRecommendationInfoDialog.show(this, item, source, this::onRecommendationNotInterested);
        return true;
    }

    private void onRecommendationNotInterested(TmdbItem item) {
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.removeRecommendation(item);
        if (mTmdbRecommendationsObjectAdapter != null) mTmdbRecommendationsObjectAdapter.remove(item);
        if (mPersonalTmdbObjectAdapter != null) mPersonalTmdbObjectAdapter.remove(item);
        if (mPersonalDoubanObjectAdapter != null) mPersonalDoubanObjectAdapter.remove(item);
        if (mPersonalAiObjectAdapter != null) mPersonalAiObjectAdapter.remove(item);
        showAiRecommendationReason(item, false);
        if (mVod != null) loadNativePersonalRecommendations(mVod);
    }

    private void onAiRecommendationFocus(TmdbItem item, boolean focused) {
        showAiRecommendationReason(item, focused);
    }

    private void showAiRecommendationReason(TmdbItem item, boolean focused) {
        String text = item == null ? "" : item.getRecommendationReason();
        if (TextUtils.isEmpty(text) && item != null) text = item.getOverview();
        if (!focused || TextUtils.isEmpty(text)) {
            mBinding.tmdbPersonalAiReason.setVisibility(View.GONE);
            mBinding.tmdbPersonalAiReason.setText("");
            return;
        }
        mBinding.tmdbPersonalAiReason.setText(getString(R.string.ai_recommendation_reason_preview, text));
        mBinding.tmdbPersonalAiReason.setVisibility(View.VISIBLE);
    }

    private void setNativePersonalRecommendationFocus(boolean hasTmdb, boolean hasDouban, boolean hasAi) {
        int next = hasTmdb ? R.id.tmdbPersonalTmdbRecommendations : hasDouban ? R.id.tmdbPersonalDoubanRecommendations : hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag;
        setDetailButtonsNextFocus(next);
        mBinding.tmdbPersonalTmdbRecommendations.setNextFocusDownId(hasDouban ? R.id.tmdbPersonalDoubanRecommendations : hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        mBinding.tmdbPersonalDoubanRecommendations.setNextFocusDownId(hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        mBinding.tmdbPersonalAiRecommendations.setNextFocusDownId(R.id.flag);
    }

    private void attachRecommendationLazyLoader(HorizontalGridView grid, RecommendationRow row) {
        grid.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                RecyclerView.Adapter<?> adapter = parent.getAdapter();
                if (adapter == null || position < 0 || adapter.getItemCount() - position > 4) return;
                loadMoreRecommendationRow(row);
            }
        });
    }

    private void loadMoreRecommendationRow(RecommendationRow row) {
        if (row == RecommendationRow.RECOMMENDATIONS) {
            if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded() || mTmdbRecommendationsLoading || !mTmdbUIAdapter.hasMoreRecommendations()) return;
            mTmdbRecommendationsLoading = true;
            mTmdbUIAdapter.loadMoreRecommendations(changed -> {
                mTmdbRecommendationsLoading = false;
                if (changed) appendLeanbackItems(mTmdbRecommendationsObjectAdapter, mTmdbUIAdapter.getRecommendations());
            });
            return;
        }
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            loadMoreTmdbPersonalRecommendationRow(row);
        } else {
            loadMoreNativePersonalRecommendations(row == RecommendationRow.PERSONAL_TMDB);
        }
    }

    private void loadMoreTmdbPersonalRecommendationRow(RecommendationRow row) {
        if (row == RecommendationRow.PERSONAL_TMDB) {
            if (mPersonalTmdbLoading || !mTmdbUIAdapter.hasMorePersonalTmdbRecommendations()) return;
            mPersonalTmdbLoading = true;
            mTmdbUIAdapter.loadMorePersonalTmdbRecommendations(changed -> {
                mPersonalTmdbLoading = false;
                if (changed) appendLeanbackItems(mPersonalTmdbObjectAdapter, mTmdbUIAdapter.getPersonalTmdbRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_DOUBAN) {
            if (mPersonalDoubanLoading || !mTmdbUIAdapter.hasMorePersonalDoubanRecommendations()) return;
            mPersonalDoubanLoading = true;
            mTmdbUIAdapter.loadMorePersonalDoubanRecommendations(changed -> {
                mPersonalDoubanLoading = false;
                if (changed) appendLeanbackItems(mPersonalDoubanObjectAdapter, mTmdbUIAdapter.getPersonalDoubanRecommendations());
            });
        } else if (row == RecommendationRow.PERSONAL_AI) {
            if (mPersonalAiLoading || !mTmdbUIAdapter.hasMorePersonalAiRecommendations()) return;
            mPersonalAiLoading = true;
            mTmdbUIAdapter.loadMorePersonalAiRecommendations(changed -> {
                mPersonalAiLoading = false;
                if (changed) appendLeanbackItems(mPersonalAiObjectAdapter, mTmdbUIAdapter.getPersonalAiRecommendations());
            });
        }
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
                SpiderDebug.log("personal-rec", "tv native load more failed tmdb=%s error=%s", tmdb, e.getMessage());
                nextPage = page;
            }
            if (Thread.currentThread().isInterrupted()) return;
            PersonalRecommendationService.RecommendationPage loadedPage = nextPage;
            runOnUiThread(() -> {
                if (isPlaybackExiting() || isFinishing() || isDestroyed() || generation != mPersonalRecommendationGeneration) return;
                if (tmdb) {
                    mNativePersonalTmdbLoading = false;
                    mNativePersonalTmdbPage = loadedPage;
                    appendLeanbackItems(mPersonalTmdbObjectAdapter, loadedPage.getItems());
                } else {
                    mNativePersonalDoubanLoading = false;
                    mNativePersonalDoubanPage = loadedPage;
                    appendLeanbackItems(mPersonalDoubanObjectAdapter, loadedPage.getItems());
                }
            });
            if (tmdb && !Thread.currentThread().isInterrupted()) service.enrichTmdbPageRatingsAsync(loadedPage, enriched -> applyNativePersonalTmdbRatings(enriched, generation));
        });
    }

    private void appendLeanbackItems(ArrayObjectAdapter adapter, List<TmdbItem> items) {
        if (adapter == null || items == null || items.size() <= adapter.size()) return;
        adapter.addAll(adapter.size(), new ArrayList<>(items.subList(adapter.size(), items.size())));
    }

    private void refreshPersonalRecommendationsForHistory() {
        if (isPlaybackExiting() || isFinishing() || isDestroyed()) return;
        if (!Setting.isPersonalRecommendation() || mVod == null || mTmdbDetailLoading) return;
        if (mTmdbUIAdapter != null && mTmdbUIAdapter.isLoaded()) {
            mTmdbUIAdapter.refreshPersonalRecommendations(changed -> {
                if (!changed) return;
                bindRecommendationGrid(mBinding.tmdbPersonalTmdbRecommendations, mBinding.tmdbPersonalTmdbRecommendationsLabel, mTmdbUIAdapter.getPersonalTmdbRecommendations(), RecommendationRow.PERSONAL_TMDB);
                bindRecommendationGrid(mBinding.tmdbPersonalDoubanRecommendations, mBinding.tmdbPersonalDoubanRecommendationsLabel, mTmdbUIAdapter.getPersonalDoubanRecommendations(), RecommendationRow.PERSONAL_DOUBAN);
            });
        } else {
            loadNativePersonalRecommendations(mVod);
        }
    }

    private void refreshTmdbPersonalAiRecommendations() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        boolean hasAi = bindRecommendationGrid(mBinding.tmdbPersonalAiRecommendations, mBinding.tmdbPersonalAiRecommendationsLabel, mTmdbUIAdapter.getPersonalAiRecommendations(), RecommendationRow.PERSONAL_AI);
        if (mBinding.tmdbPersonalDoubanRecommendations.getVisibility() == View.VISIBLE) {
            mBinding.tmdbPersonalDoubanRecommendations.setNextFocusDownId(hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        } else if (mBinding.tmdbPersonalTmdbRecommendations.getVisibility() == View.VISIBLE) {
            mBinding.tmdbPersonalTmdbRecommendations.setNextFocusDownId(hasAi ? R.id.tmdbPersonalAiRecommendations : R.id.flag);
        }
        mBinding.tmdbPersonalAiRecommendations.setNextFocusDownId(R.id.flag);
    }

    // 细粒度刷新：相关推荐（“猜你喜欢”）异步到达时只重绑该列表，不触碰详情头部与集数，
    // 避免整页重绑造成的闪烁与二次全屏 loading。
    private void refreshTmdbRecommendations() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded() || mTmdbDetailLoading) return;
        bindRecommendationGrid(mBinding.tmdbRecommendations, mBinding.tmdbRecommendationsLabel, mTmdbUIAdapter.getRecommendations(), RecommendationRow.RECOMMENDATIONS);
    }

    // 细粒度刷新：个性化推荐（TMDB / 豆瓣）异步到达时只重绑这两个列表。
    private void refreshTmdbRelatedVideos() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded() || mTmdbDetailLoading) return;
        bindTmdbVideoGrid(mBinding.tmdbRelatedVideos, mBinding.tmdbRelatedVideosLabel, mTmdbUIAdapter.getRelatedVideos());
        updateFocus();
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
        refreshTmdbRelatedVideos();
    }

    private void refreshTmdbPersonalRecommendations() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded() || mTmdbDetailLoading) return;
        bindRecommendationGrid(mBinding.tmdbPersonalTmdbRecommendations, mBinding.tmdbPersonalTmdbRecommendationsLabel, mTmdbUIAdapter.getPersonalTmdbRecommendations(), RecommendationRow.PERSONAL_TMDB);
        bindRecommendationGrid(mBinding.tmdbPersonalDoubanRecommendations, mBinding.tmdbPersonalDoubanRecommendationsLabel, mTmdbUIAdapter.getPersonalDoubanRecommendations(), RecommendationRow.PERSONAL_DOUBAN);
    }

    // 细粒度刷新：集数标题异步补全。useTmdbCard 由 shouldUseTmdbEpisodeCards(hasTmdbEpisodeData)
    // 决定，首次进入时集数先以纯文本渲染，TMDB 数据是此刻才补齐的——只 notify 不会重算卡片模式，
    // 会卡在文本态。这里重算卡片模式与 chrome 可见性即可，不走完整 setEpisodeAdapter：那会把
    // 列表拽回第一个分段（它只装载 items 的首段），用户停在 81-120 段时会被拉回 1-40。
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
        mSourceEpisodeSeasonCache.clear();
        updateEpisodeSeasonContext();
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded() || mTmdbDetailLoading) return;
        if (mEpisodeAdapter == null || mEpisodeGridAdapter == null) return;
        if (mFlagAdapter == null || mFlagAdapter.getItemCount() == 0) return;
        Flag flag = getFlag();
        if (flag == null) return;
        List<Episode> items = flag.getEpisodes();
        boolean hasMultiple = items.size() > 1;
        boolean tmdbMode = isTmdbSourceEnabled();
        boolean useTmdbCards = EpisodeDisplayPolicy.shouldUseTmdbEpisodeCards(tmdbMode, items);
        boolean showTmdbEpisodeChrome = EpisodeDisplayPolicy.shouldShowTmdbEpisodeChrome(tmdbMode, false, items);
        // 与 setEpisodeAdapter 保持同一判据：网格模式只跟随「真有卡片数据」。此处 chrome
        // 参数传 false 时两者恰好等价，显式写成 useTmdbCards 是为了以后改策略时不会漏掉这里。
        if (useTmdbCards && hasMultiple) episodeGridMode = Setting.getTmdbEpisodeGridMode();
        if (!useTmdbCards || !hasMultiple) episodeGridMode = false;
        mBinding.episodeHeader.setVisibility(showTmdbEpisodeChrome && !items.isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.episodeReverse.setVisibility(showTmdbEpisodeChrome && hasMultiple ? View.VISIBLE : View.GONE);
        mBinding.episodeViewMode.setVisibility(showTmdbEpisodeChrome && hasMultiple && useTmdbCards ? View.VISIBLE : View.GONE);
        mBinding.episodeFileName.setVisibility(showTmdbEpisodeChrome && hasMultiple ? View.VISIBLE : View.GONE);
        updateEpisodeFallbackStillUrl();
        // setUseTmdbCard 内部对相同值无操作，变化时才 notifyDataSetChanged 触发卡片重绑
        mEpisodeAdapter.setUseTmdbCard(useTmdbCards);
        mEpisodeGridAdapter.setUseTmdbCard(useTmdbCards);
        applyEpisodeViewMode(false);
        applyActionButtonVisibility();
        // 卡片模式未变化时 setUseTmdbCard 不会 notify，仍需刷新标题/日期等已补齐的字段
        mEpisodeAdapter.notifyDataSetChanged();
        mEpisodeGridAdapter.notifyDataSetChanged();
        updateFocus();
    }

    private void rememberRecommendationAdapter(RecommendationRow row, ArrayObjectAdapter adapter) {
        if (row == RecommendationRow.RECOMMENDATIONS) mTmdbRecommendationsObjectAdapter = adapter;
        else if (row == RecommendationRow.PERSONAL_TMDB) mPersonalTmdbObjectAdapter = adapter;
        else if (row == RecommendationRow.PERSONAL_DOUBAN) mPersonalDoubanObjectAdapter = adapter;
        else if (row == RecommendationRow.PERSONAL_AI) mPersonalAiObjectAdapter = adapter;
    }

    private enum RecommendationRow {
        NONE, RECOMMENDATIONS, PERSONAL_TMDB, PERSONAL_DOUBAN, PERSONAL_AI
    }

    private void setDetailButtonsNextFocus(int fallback) {
        int target = isVisible(mBinding.flag) ? R.id.flag : fallback;
        if (target == 0) target = View.NO_ID;
        mBinding.content.setNextFocusDownId(target);
        mBinding.shortDisplay.setNextFocusDownId(target);
        mBinding.searchDetail.setNextFocusDownId(target);
        mBinding.keep.setNextFocusDownId(target);
        mBinding.change1.setNextFocusDownId(target);
        mBinding.tmdbRematch.setNextFocusDownId(target);
    }

    private void bindTmdbData() {
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return;
        loadTmdbRelatedVideosForCurrentEpisode();

        boolean hasTmdbContent = false;
        View lastVisibleGrid = null;

        // 演员
        java.util.List<com.fongmi.android.tv.bean.TmdbPerson> cast = mTmdbUIAdapter.getCast();
        if (!cast.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter castAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbCastPresenter(this::onTmdbPersonClick)
            );
            castAdapter.addAll(0, cast);
            mBinding.tmdbCast.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(castAdapter));
            mBinding.tmdbCast.setVisibility(View.VISIBLE);
            View castLabel = mBinding.getRoot().findViewById(R.id.tmdbCastLabel);
            if (castLabel != null) castLabel.setVisibility(View.VISIBLE);
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbCast);
            lastVisibleGrid = mBinding.tmdbCast;
            hasTmdbContent = true;
        } else {
            mBinding.tmdbCast.setVisibility(View.GONE);
            View castLabel = mBinding.getRoot().findViewById(R.id.tmdbCastLabel);
            if (castLabel != null) castLabel.setVisibility(View.GONE);
        }

        // 剧照
        java.util.List<String> photos = mTmdbUIAdapter.getPhotos();
        if (!photos.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter photosAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbPhotoPresenter(this::onTmdbPhotoClick)
            );
            photosAdapter.addAll(0, photos);
            mBinding.tmdbPhotos.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(photosAdapter));
            mBinding.tmdbPhotos.setVisibility(View.VISIBLE);
            View photosLabel = mBinding.getRoot().findViewById(R.id.tmdbPhotosLabel);
            if (photosLabel != null) photosLabel.setVisibility(View.VISIBLE);
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPhotos);

            // 动态设置上一个Grid的nextFocusDown
            if (lastVisibleGrid != null) {
                lastVisibleGrid.setNextFocusDownId(R.id.tmdbPhotos);
            }
            lastVisibleGrid = mBinding.tmdbPhotos;
            if (!hasTmdbContent) hasTmdbContent = true;
        } else {
            mBinding.tmdbPhotos.setVisibility(View.GONE);
            View photosLabel = mBinding.getRoot().findViewById(R.id.tmdbPhotosLabel);
            if (photosLabel != null) photosLabel.setVisibility(View.GONE);
        }

        // 海报
        java.util.List<String> posters = mTmdbUIAdapter.getPosters();
        if (!posters.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter postersAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbPhotoPresenter(this::onTmdbPosterClick, true)
            );
            postersAdapter.addAll(0, posters);
            mBinding.tmdbPosters.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(postersAdapter));
            mBinding.tmdbPosters.setVisibility(View.VISIBLE);
            View postersLabel = mBinding.getRoot().findViewById(R.id.tmdbPostersLabel);
            if (postersLabel != null) postersLabel.setVisibility(View.VISIBLE);
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPosters);

            if (lastVisibleGrid != null) {
                lastVisibleGrid.setNextFocusDownId(R.id.tmdbPosters);
            }
            lastVisibleGrid = mBinding.tmdbPosters;
            if (!hasTmdbContent) hasTmdbContent = true;
        } else {
            mBinding.tmdbPosters.setVisibility(View.GONE);
            View postersLabel = mBinding.getRoot().findViewById(R.id.tmdbPostersLabel);
            if (postersLabel != null) postersLabel.setVisibility(View.GONE);
        }

        // TMDB related videos
        java.util.List<TmdbVideo> relatedVideos = mTmdbUIAdapter.getRelatedVideos();
        if (bindTmdbVideoGrid(mBinding.tmdbRelatedVideos, mBinding.tmdbRelatedVideosLabel, relatedVideos)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbRelatedVideos);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbRelatedVideos);
            lastVisibleGrid = mBinding.tmdbRelatedVideos;
            if (!hasTmdbContent) hasTmdbContent = true;
        }


        // 主创团队
        java.util.List<com.fongmi.android.tv.bean.TmdbPerson> creators = mTmdbUIAdapter.getCreators();
        if (!creators.isEmpty()) {
            androidx.leanback.widget.ArrayObjectAdapter crewAdapter = new androidx.leanback.widget.ArrayObjectAdapter(
                new com.fongmi.android.tv.ui.presenter.TmdbCastPresenter(this::onTmdbPersonClick)
            );
            crewAdapter.addAll(0, creators);
            mBinding.tmdbCrew.setAdapter(new androidx.leanback.widget.ItemBridgeAdapter(crewAdapter));
            mBinding.tmdbCrew.setVisibility(View.VISIBLE);
            View crewLabel = mBinding.getRoot().findViewById(R.id.tmdbCrewLabel);
            if (crewLabel != null) crewLabel.setVisibility(View.VISIBLE);
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbCrew);

            // 动态设置上一个Grid的nextFocusDown
            if (lastVisibleGrid != null) {
                lastVisibleGrid.setNextFocusDownId(R.id.tmdbCrew);
            }
            lastVisibleGrid = mBinding.tmdbCrew;
            if (!hasTmdbContent) hasTmdbContent = true;
        } else {
            mBinding.tmdbCrew.setVisibility(View.GONE);
            View crewLabel = mBinding.getRoot().findViewById(R.id.tmdbCrewLabel);
            if (crewLabel != null) crewLabel.setVisibility(View.GONE);
        }

        // 猜你喜欢
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> recommendations = mTmdbUIAdapter.getRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbRecommendations, mBinding.tmdbRecommendationsLabel, recommendations, RecommendationRow.RECOMMENDATIONS)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbRecommendations);
            lastVisibleGrid = mBinding.tmdbRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        java.util.List<com.fongmi.android.tv.bean.TmdbItem> personalTmdbRecommendations = mTmdbUIAdapter.getPersonalTmdbRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbPersonalTmdbRecommendations, mBinding.tmdbPersonalTmdbRecommendationsLabel, personalTmdbRecommendations, RecommendationRow.PERSONAL_TMDB)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPersonalTmdbRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbPersonalTmdbRecommendations);
            lastVisibleGrid = mBinding.tmdbPersonalTmdbRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        // 个性推荐 · 豆瓣
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> personalDoubanRecommendations = mTmdbUIAdapter.getPersonalDoubanRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbPersonalDoubanRecommendations, mBinding.tmdbPersonalDoubanRecommendationsLabel, personalDoubanRecommendations, RecommendationRow.PERSONAL_DOUBAN)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPersonalDoubanRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbPersonalDoubanRecommendations);
            lastVisibleGrid = mBinding.tmdbPersonalDoubanRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        // 个性推荐 · 智能
        java.util.List<com.fongmi.android.tv.bean.TmdbItem> personalAiRecommendations = mTmdbUIAdapter.getPersonalAiRecommendations();
        if (bindRecommendationGrid(mBinding.tmdbPersonalAiRecommendations, mBinding.tmdbPersonalAiRecommendationsLabel, personalAiRecommendations, RecommendationRow.PERSONAL_AI)) {
            if (lastVisibleGrid == null) setDetailButtonsNextFocus(R.id.tmdbPersonalAiRecommendations);
            if (lastVisibleGrid != null) lastVisibleGrid.setNextFocusDownId(R.id.tmdbPersonalAiRecommendations);
            lastVisibleGrid = mBinding.tmdbPersonalAiRecommendations;
            if (!hasTmdbContent) hasTmdbContent = true;
        }

        // 设置最后一个Grid的nextFocusDown到flag
        if (lastVisibleGrid != null) {
            lastVisibleGrid.setNextFocusDownId(R.id.flag);
        }

        // 如果没有TMDB内容，确保按钮焦点指向flag
        if (!hasTmdbContent) {
            mBinding.content.setNextFocusDownId(R.id.flag);
            mBinding.keep.setNextFocusDownId(R.id.flag);
            mBinding.searchDetail.setNextFocusDownId(R.id.flag);
            mBinding.change1.setNextFocusDownId(R.id.flag);
            mBinding.tmdbRematch.setNextFocusDownId(R.id.flag);
        }

        SpiderDebug.log("tmdb-tv", "绑定完成: 演员=%d 剧照=%d 海报=%d 主创=%d 推荐=%d 个性TMDB=%d 个性豆瓣=%d 个性智能=%d", cast.size(), photos.size(), posters.size(), creators.size(), recommendations.size(), personalTmdbRecommendations.size(), personalDoubanRecommendations.size(), personalAiRecommendations.size());
        updateFocus();

        // TMDB / OMDB 多来源评分（TMDB / IMDb / 烂番茄 / Metacritic 等）
        bindTmdbOmdbRatings();

        // 设置背景幻灯片
        setupBackdropSlideshow(mTmdbUIAdapter.getBackgroundPhotos());

        // TMDB 数据全部绑定完成，揭开遮罩并应用 TMDB 字段
        finishTmdbDetail();
        requestIntroSkipPlan();
    }

    private void queueTmdbBind(Vod item) {
        if (item == null) return;
        mPendingTmdbBindVod = item;
        mTmdbBindPending = true;
        schedulePendingTmdbBindAfterContentReveal();
    }

    private void schedulePendingTmdbBindAfterContentReveal() {
        if (!mTmdbBindPending) return;
        App.removeCallbacks(mPendingTmdbBind);
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) {
            App.post(mPendingTmdbBind, 0);
            return;
        }
        App.post(mPendingTmdbBind, TMDB_BIND_AFTER_REVEAL_DELAY_MS);
        SpiderDebug.log("tmdb-tv", "detail bind scheduled after content reveal delay=%dms", TMDB_BIND_AFTER_REVEAL_DELAY_MS);
    }

    private void flushPendingTmdbBind() {
        if (!mTmdbBindPending) return;
        Vod item = mPendingTmdbBindVod;
        mTmdbBindPending = false;
        mPendingTmdbBindVod = null;
        if (!isCurrentVodEvent(item)) return;
        updateVod(item);
        if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) {
            revealTmdbDetail();
            loadNativePersonalRecommendations(item);
            if (shouldShowAutoTmdbMatchDialog(item)) showManualTmdbMatchDialog();
            finishEpisodeLoading();
            return;
        }
        finishTmdbDetail();
        finishEpisodeLoading();
        mTmdbDataBindPending = true;
        App.post(mDeferredTmdbDataBind, TMDB_BIND_AFTER_REVEAL_DELAY_MS);
        SpiderDebug.log("tmdb-tv", "detail bind reveal first, data bind delay=%dms", TMDB_BIND_AFTER_REVEAL_DELAY_MS);
    }

    private void applyDeferredTmdbDataBind() {
        if (!mTmdbDataBindPending || isFinishing() || isDestroyed()) return;
        mTmdbDataBindPending = false;
        long start = System.currentTimeMillis();
        bindTmdbData();
        SpiderDebug.log("tmdb-tv", "detail bind deferred cost=%dms", System.currentTimeMillis() - start);
    }

    private void resetPendingTmdbBind() {
        App.removeCallbacks(mPendingTmdbBind, mDeferredTmdbDataBind);
        mTmdbBindPending = false;
        mTmdbDataBindPending = false;
        mPendingTmdbBindVod = null;
    }

    /**
     * 绑定多来源评分（TMDB / IMDb / 烂番茄 / Metacritic 等）。
     * TMDB 匹配成功时优先显示 TMDB 分，OMDB 可用时再追加 IMDb 等外部来源。
     */
    private void bindTmdbOmdbRatings() {
        View label = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatingsLabel);
        ViewGroup container = mBinding.getRoot().findViewById(R.id.tmdbOmdbRatings);
        if (container == null || mTmdbUIAdapter == null) {
            hideTmdbRatingChips(label, container);
            return;
        }

        TmdbItem ratingItem = mTmdbUIAdapter.getTmdbItem();
        String ratingContextKey = ratingItem == null ? "" : ratingItem.getMediaType() + "|" + ratingItem.getTmdbId();
        if (!TextUtils.equals(mTmdbRatingContextKey, ratingContextKey)) {
            mTmdbRatingContextKey = ratingContextKey;
            mTmdbRatingTasks.cancelAll();
        }

        com.google.gson.JsonObject detail = mTmdbUIAdapter.getTmdbDetail();
        if (detail == null) {
            hideTmdbRatingChips(label, container);
            SpiderDebug.log("tmdb-omdb", "跳过：detail 为空");
            return;
        }

        java.util.List<String[]> baseChips = buildTmdbRatingChips();
        fetchTmdbDoubanRating();

        com.google.gson.JsonObject externalIds = detail.has("external_ids") && !detail.get("external_ids").isJsonNull()
                ? detail.getAsJsonObject("external_ids") : null;
        if (externalIds == null || !externalIds.has("imdb_id") || externalIds.get("imdb_id").isJsonNull()) {
            renderTmdbRatingChips(label, container, baseChips);
            SpiderDebug.log("tmdb-omdb", "跳过：无 imdb_id，detail keys=%s", detail.keySet());
            return;
        }
        String imdbId = externalIds.get("imdb_id").getAsString();
        if (TextUtils.isEmpty(imdbId)) {
            renderTmdbRatingChips(label, container, baseChips);
            return;
        }

        com.fongmi.android.tv.bean.TmdbConfig tmdbConfig = com.fongmi.android.tv.bean.TmdbConfig.objectFrom(Setting.getTmdbConfig());
        String omdbApiKey = tmdbConfig.getOmdbApiKey();
        if (TextUtils.isEmpty(omdbApiKey)) {
            renderTmdbRatingChips(label, container, baseChips);
            SpiderDebug.log("tmdb-omdb", "跳过：未配置 OMDB API Key");
            return;
        }

        String cacheKey = omdbRatingCacheKey(imdbId, omdbApiKey);
        java.util.List<String[]> cached = mTmdbOmdbRatingCache.get(cacheKey);
        if (cached != null) {
            container.setTag(cacheKey);
            renderTmdbRatingChips(label, container, mergeRatingChips(baseChips, cached));
            return;
        }
        if (mTmdbOmdbRatingLoading.contains(cacheKey)) {
            container.setTag(cacheKey);
            renderTmdbRatingChips(label, container, baseChips);
            SpiderDebug.log("tmdb-omdb", "跳过：请求进行中 imdbId=%s", imdbId);
            return;
        }

        container.setTag(cacheKey);
        renderTmdbRatingChips(label, container, baseChips);
        mTmdbOmdbRatingLoading.add(cacheKey);
        SpiderDebug.log("tmdb-omdb", "开始请求 imdbId=%s", imdbId);
        fetchTmdbOmdbRatings(imdbId, omdbApiKey, cacheKey, label, container);
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
        // 手动重新匹配是从已揭示的详情页触发的，细粒度事件（VOD_CORE/推荐/个性/集数标题）
        // 已能就地替换各区域，无需再盖全屏 loading（那会隐藏含视频窗口的全部内容、造成
        // “整页加载中”观感并重放揭示动画）。这里只清空旧的 TMDB 展示与背景 signature，
        // 让新数据到达时由 VOD_CORE 就地重绑，空区域走各自 else 分支隐藏、不残留旧数据。
        clearTmdbDetailViews();
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

    private void fetchTmdbOmdbRatings(String imdbId, String omdbApiKey, String cacheKey, View label, ViewGroup container) {
        mTmdbRatingTasks.submit(() -> {
            try {
                com.google.gson.JsonObject jsonObj = OmdbService.fetch(imdbId, omdbApiKey);
                if (jsonObj == null) {
                    SpiderDebug.log("tmdb-omdb", "请求失败");
                    return;
                }
                if (jsonObj.has("Response") && "False".equals(jsonObj.get("Response").getAsString())) {
                    SpiderDebug.log("tmdb-omdb", "返回 Response=False");
                    return;
                }

                java.util.List<String[]> omdbChips = buildOmdbRatingChips(jsonObj);
                SpiderDebug.log("tmdb-omdb", "评分卡片数=%d", omdbChips.size());
                if (omdbChips.isEmpty()) return;
                mTmdbOmdbRatingCache.put(cacheKey, omdbChips);

                runOnUiThread(() -> {
                    if (isPlaybackExiting() || isFinishing() || isDestroyed()) return;
                    if (!cacheKey.equals(container.getTag())) return;
                    renderTmdbRatingChips(label, container, mergeRatingChips(buildTmdbRatingChips(), omdbChips));
                });
            } catch (Exception e) {
                SpiderDebug.log("tmdb-omdb", "获取失败: %s", e.getMessage());
            } finally {
                mTmdbOmdbRatingLoading.remove(cacheKey);
            }
        });
    }
    private void hideTmdbRatingChips(View label, ViewGroup container) {
        if (label != null) label.setVisibility(View.GONE);
        if (container == null) return;
        container.setVisibility(View.GONE);
        container.setTag(null);
        container.removeAllViews();
    }

    private String omdbRatingCacheKey(String imdbId, String omdbApiKey) {
        return imdbId + "|" + omdbApiKey;
    }

    private void renderTmdbRatingChips(View label, ViewGroup container, java.util.List<String[]> chips) {
        if (container == null) return;
        container.removeAllViews();
        if (chips == null || chips.isEmpty()) {
            if (label != null) label.setVisibility(View.GONE);
            container.setVisibility(View.GONE);
            return;
        }
        for (String[] chip : chips) {
            container.addView(createOmdbRatingChip(chip[0], chip[1], chip[2]));
        }
        if (label != null) label.setVisibility(View.VISIBLE);
        container.setVisibility(View.VISIBLE);
    }

    private java.util.List<String[]> buildTmdbRatingChips() {
        java.util.List<String[]> chips = new java.util.ArrayList<>();
        if (mTmdbUIAdapter == null) return chips;
        String tmdbRating = mTmdbUIAdapter.getRatingText();
        if (!TextUtils.isEmpty(tmdbRating)) {
            chips.add(new String[]{"TMDB", tmdbRating + "/10", "#21D07A"});
        }
        String[] boxOffice = buildBoxOfficeChip();
        if (boxOffice != null) chips.add(boxOffice);
        PersonalRecommendationService.DoubanRating doubanRating = mTmdbDoubanRatingCache.get(currentTmdbDoubanRatingKey());
        if (doubanRating != null && !doubanRating.isEmpty()) {
            String rating = formatRating(doubanRating.getRating());
            if (!TextUtils.isEmpty(rating)) chips.add(new String[]{"豆瓣", rating + "/10", "#00B51D"});
        }
        return chips;
    }

    private String[] buildBoxOfficeChip() {
        if (mTmdbUIAdapter == null) return null;
        com.google.gson.JsonObject detail = mTmdbUIAdapter.getTmdbDetail();
        if (detail == null) return null;
        String mediaType = currentTmdbMediaType();
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

    private void fetchTmdbDoubanRating() {
        if (mTmdbUIAdapter == null || mTmdbUIAdapter.getTmdbItem() == null) return;
        String title = currentTmdbDoubanTitle();
        if (TextUtils.isEmpty(title)) return;
        String mediaType = currentTmdbMediaType();
        int year = parseTmdbYear(mTmdbUIAdapter.getYear());
        String cacheKey = tmdbDoubanRatingCacheKey(title, mediaType, year);
        if (mTmdbDoubanRatingCache.containsKey(cacheKey) || !mTmdbDoubanRatingLoading.add(cacheKey)) return;
        mTmdbRatingTasks.submit(() -> {
            try {
                PersonalRecommendationService.DoubanRating rating;
                try {
                    rating = new PersonalRecommendationService().loadDoubanRating(title, mediaType, year);
                } catch (java.util.concurrent.CancellationException e) {
                    return;
                } catch (Throwable e) {
                    SpiderDebug.log("tmdb-douban", "评分获取失败 title=%s error=%s", title, e.getMessage());
                    rating = PersonalRecommendationService.DoubanRating.empty();
                }
                if (Thread.currentThread().isInterrupted()) return;
                mTmdbDoubanRatingCache.put(cacheKey, rating);
                runOnUiThread(() -> {
                    if (isPlaybackExiting() || isFinishing() || isDestroyed()) return;
                    if (!cacheKey.equals(currentTmdbDoubanRatingKey())) return;
                    bindTmdbOmdbRatings();
                });
            } finally {
                mTmdbDoubanRatingLoading.remove(cacheKey);
            }
        });
    }
    private String currentTmdbDoubanRatingKey() {
        return tmdbDoubanRatingCacheKey(currentTmdbDoubanTitle(), currentTmdbMediaType(), parseTmdbYear(mTmdbUIAdapter == null ? "" : mTmdbUIAdapter.getYear()));
    }

    private String currentTmdbDoubanTitle() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? "" : item.getTitle();
    }

    private String currentTmdbMediaType() {
        TmdbItem item = mTmdbUIAdapter == null ? null : mTmdbUIAdapter.getTmdbItem();
        return item == null ? "" : item.getMediaType();
    }

    private String tmdbDoubanRatingCacheKey(String title, String mediaType, int year) {
        return nullToEmpty(mediaType) + "|" + nullToEmpty(title).toLowerCase(Locale.ROOT) + "|" + year;
    }

    private int parseTmdbYear(String year) {
        if (TextUtils.isEmpty(year)) return 0;
        try {
            return Integer.parseInt(year);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private String formatRating(double rating) {
        return rating <= 0 ? "" : String.format(Locale.US, "%.1f", rating);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private java.util.List<String[]> mergeRatingChips(java.util.List<String[]> baseChips, java.util.List<String[]> extraChips) {
        java.util.List<String[]> chips = new java.util.ArrayList<>();
        if (baseChips != null) chips.addAll(baseChips);
        if (extraChips != null) chips.addAll(extraChips);
        return chips;
    }

    /**
     * 从 OMDB 响应组装评分卡片数据：每项为 {平台名, 评分文本, 颜色}。
     */
    private java.util.List<String[]> buildOmdbRatingChips(com.google.gson.JsonObject jsonObj) {
        java.util.List<String[]> chips = new java.util.ArrayList<>();

        String imdbRating = optOmdbString(jsonObj, "imdbRating");
        if (!TextUtils.isEmpty(imdbRating)) {
            String votes = optOmdbString(jsonObj, "imdbVotes");
            String text = buildImdbRatingText(imdbRating, votes);
            chips.add(new String[]{"IMDB", text, "#F5C518"});
        }

        if (jsonObj.has("Ratings") && jsonObj.get("Ratings").isJsonArray()) {
            for (com.google.gson.JsonElement el : jsonObj.getAsJsonArray("Ratings")) {
                if (!el.isJsonObject()) continue;
                com.google.gson.JsonObject rating = el.getAsJsonObject();
                String source = optOmdbString(rating, "Source");
                String value = optOmdbString(rating, "Value");
                if (TextUtils.isEmpty(source) || TextUtils.isEmpty(value)) continue;
                if ("Internet Movie Database".equals(source)) continue;
                if ("Rotten Tomatoes".equals(source)) chips.add(new String[]{"烂番茄", value, "#FA320A"});
                else if ("Metacritic".equals(source)) chips.add(new String[]{"Metacritic", value, "#FFCC33"});
                else chips.add(new String[]{source, value, "#21D07A"});
            }
        }

        String metascore = optOmdbString(jsonObj, "Metascore");
        boolean hasMetacritic = false;
        for (String[] chip : chips) if ("Metacritic".equals(chip[0])) hasMetacritic = true;
        if (!TextUtils.isEmpty(metascore) && !hasMetacritic) {
            chips.add(new String[]{"Metascore", metascore + "/100", "#FFCC33"});
        }

        return chips;
    }

    private String optOmdbString(com.google.gson.JsonObject obj, String key) {
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
    private View createOmdbRatingChip(String platform, String value, String color) {
        androidx.appcompat.widget.LinearLayoutCompat chip = new androidx.appcompat.widget.LinearLayoutCompat(this);
        chip.setOrientation(androidx.appcompat.widget.LinearLayoutCompat.VERTICAL);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setMinimumWidth(ResUtil.dp2px(120));
        chip.setPadding(ResUtil.dp2px(16), ResUtil.dp2px(10), ResUtil.dp2px(16), ResUtil.dp2px(10));

        android.graphics.drawable.GradientDrawable background = new android.graphics.drawable.GradientDrawable();
        background.setColor(0x6610141A);
        background.setCornerRadius(ResUtil.dp2px(8));
        background.setStroke(ResUtil.dp2px(1), 0x33FFFFFF);
        chip.setBackground(background);

        TextView platformView = new TextView(this);
        platformView.setText(platform);
        platformView.setTextColor(0xE6FFFFFF);
        platformView.setTextSize(13);
        platformView.setGravity(android.view.Gravity.CENTER);
        platformView.setSingleLine(true);
        platformView.setIncludeFontPadding(false);
        platformView.setMinWidth(ResUtil.dp2px(56));
        chip.addView(platformView);

        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(android.graphics.Color.parseColor(color));
        valueView.setTextSize(17);
        valueView.setTypeface(null, android.graphics.Typeface.BOLD);
        valueView.setGravity(android.view.Gravity.CENTER);
        valueView.setSingleLine(true);
        valueView.setIncludeFontPadding(false);
        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams valueParams =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = ResUtil.dp2px(4);
        valueView.setLayoutParams(valueParams);
        chip.addView(valueView);

        androidx.appcompat.widget.LinearLayoutCompat.LayoutParams params =
                new androidx.appcompat.widget.LinearLayoutCompat.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMarginEnd(ResUtil.dp2px(12));
        chip.setLayoutParams(params);
        return chip;
    }

    private void setupBackdropSlideshow(java.util.List<String> photos) {
        if (photos == null || photos.isEmpty()) {
            // 无可用 TMDB 背景图：清除幻灯片激活标志并隐藏 pager，恢复 contextWall 作为后备背景。
            mBackdropSignature = null;
            mBackdropSlideshowActive = false;
            if (mBinding.backdropPager != null) {
                mBinding.backdropPager.setVisibility(View.GONE);
            }
            setContextWall(getContextWall());
            return;
        }
        // contextWall（ImageView）叠在 backdropPager 之上，其图源是进入页时固定的 wallPic，
        // 与 TMDB 条目无关。有可用 TMDB 背景图时以幻灯片为背景权威：隐藏 contextWall、露出会随
        // TMDB 切换更新的 slideshow，并置标志阻止后续 setArtwork 把 contextWall 翻回顶层。
        mBackdropSlideshowActive = true;
        hideContextWall();
        String signature = backdropSignature(photos);
        if (TextUtils.equals(mBackdropSignature, signature)) {
            if (mBinding.backdropPager != null) mBinding.backdropPager.setVisibility(View.VISIBLE);
            SpiderDebug.log("backdrop", "背景幻灯片复用: %d张图片", photos.size());
            return;
        }

        // 初始化适配器
        if (mBackdropAdapter == null) {
            mBackdropAdapter = new BackdropAdapter();
            if (mBinding.backdropPager != null) {
                mBinding.backdropPager.setAdapter(mBackdropAdapter);
                mBinding.backdropPager.setOffscreenPageLimit(1);
            }
        }

        // 设置图片数据；切换条目时重置轮播页码，避免停在越界或旧位置
        mCurrentBackdropPage = 0;
        mBackdropAdapter.setItems(photos);
        mBackdropSignature = signature;
        if (mBinding.backdropPager != null) {
            mBinding.backdropPager.setCurrentItem(0, false);
            mBinding.backdropPager.setVisibility(View.VISIBLE);
        }

        // 启动自动轮播
        startBackdropAutoScroll();

        SpiderDebug.log("backdrop", "背景幻灯片启动: %d张图片", photos.size());
    }

    private String backdropSignature(java.util.List<String> photos) {
        return TextUtils.join("\n", photos);
    }

    private void startBackdropAutoScroll() {
        stopBackdropAutoScroll();

        if (mBackdropAdapter == null || mBackdropAdapter.getItemCount() == 0) return;

        mBackdropRunnable = new Runnable() {
            @Override
            public void run() {
                if (mBackdropAdapter == null || mBackdropAdapter.getItemCount() == 0) return;

                mCurrentBackdropPage++;
                if (mCurrentBackdropPage >= mBackdropAdapter.getItemCount()) {
                    mCurrentBackdropPage = 0;
                }

                if (mBinding.backdropPager != null) {
                    mBinding.backdropPager.setCurrentItem(mCurrentBackdropPage, true);
                }

                App.post(mBackdropRunnable, 5000); // 5秒切换一次
            }
        };

        App.post(mBackdropRunnable, 5000);
    }

    private void stopBackdropAutoScroll() {
        if (mBackdropRunnable != null) {
            App.removeCallbacks(mBackdropRunnable);
            mBackdropRunnable = null;
        }
    }

    private void onTmdbPersonClick(com.fongmi.android.tv.bean.TmdbPerson person) {
        if (person == null) return;
        com.fongmi.android.tv.ui.dialog.TmdbPersonDialog.show(this, person, getSite());
    }

    private void onTmdbPhotoClick(String url, int position) {
        if (TextUtils.isEmpty(url)) return;
        java.util.List<String> photos = mTmdbUIAdapter.getPhotos();
        int selected = Math.max(0, photos.indexOf(url));
        com.fongmi.android.tv.ui.dialog.PhotoViewerDialog.show(this, photos, selected, null);
    }


    private void onTmdbPosterClick(String url, int position) {
       if (TextUtils.isEmpty(url)) return;
        java.util.List<String> posters = mTmdbUIAdapter.getPosters();
        int selected = Math.max(0, posters.indexOf(url));
        com.fongmi.android.tv.ui.dialog.PhotoViewerDialog.show(this, posters, selected, null);
   }

    private void onTmdbRecommendationClick(com.fongmi.android.tv.bean.TmdbItem item) {
        TmdbNavigation.open(this, item, getSite());
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
        mParseAdapter.addAll(VodConfig.get().getParses());
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
            mInitialPlaybackPosition = C.TIME_UNSET;
            return;
        }
        long position = resolveInitialPlaybackPosition();
        if (position == C.TIME_UNSET || position <= 0) {
            tmdbHistoryResumePending = false;
            mInitialPlaybackPosition = C.TIME_UNSET;
            return;
        }
        mIntroSkipPlayback.setResumePosition(position);
        if (mInitialPlaybackPosition == position) {
            SpiderDebug.log("video-flow", "skip duplicate restore seek position=%d key=%s", position, getHistoryKey());
            mInitialPlaybackPosition = C.TIME_UNSET;
            tmdbHistoryResumePending = false;
            return;
        }
        mInitialPlaybackPosition = C.TIME_UNSET;
        if (player().isIjk()) pendingResumeSeekMs = position;
        else {
            long start = System.currentTimeMillis();
            player().seekTo(position);
            SpiderDebug.log("video-flow", "restore seek position=%d cost=%dms key=%s", position, System.currentTimeMillis() - start, getHistoryKey());
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
        if (mParseAdapter.getItemCount() == 0) return;
        setParse(mParseAdapter.first());
    }

    private void checkFlag() {
        int position = isGone(mBinding.flag) ? -1 : mFlagAdapter.getPosition();
        if (position == mFlagAdapter.getItemCount() - 1) checkSearch(false);
        else nextFlag(position);
    }

    private void checkSearch(boolean force) {
        if (!force && !PlayerSetting.isAutoChange()) return;
        if (mQuickAdapter.getItemCount() == 0) initSearch(mBinding.name.getText().toString(), true);
        else if (isAutoMode() || force) nextSite();
    }

    private void initSearch(String keyword, boolean auto) {
        setAutoMode(auto);
        setInitAuto(auto);
        revealManualSearch = !auto;
        startSearch(keyword);
        mBinding.part.setTag(keyword);
    }

    private boolean isPass(Site item) {
        if (isAutoMode() && !item.isChangeable()) return false;
        return item.isSearchable();
    }

    private void startSearch(String keyword) {
        mQuickAdapter.clear();
        mBinding.quick.setVisibility(View.GONE);
        dismissQuickSearchDialog();
        quickSearchDialogClosed = false;
        if (!isInitAuto()) {
            revealManualSearch = false;
            showQuickSearchDialog(new ArrayList<>());
        }
        updateFocus();
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (isPass(site)) sites.add(site);
        SiteHealthStore.sortSites(sites);
        mViewModel.searchContent(sites, keyword, true);
    }

    private void setSearch(Result result) {
        List<Vod> items = result.getList();
        items.removeIf(this::mismatch);
        mQuickAdapter.addAll(items);
        mBinding.quick.setVisibility(View.GONE);
        updateFocus();
        if (!isInitAuto() && !items.isEmpty()) {
            showQuickSearchDialog(items);
        }
        if (isInitAuto() && PlayerSetting.isAutoChange()) nextSite();
        if (items.isEmpty()) return;
        App.removeCallbacks(mR4);
    }

    private void setSearchProgress(SearchProgress progress) {
        if (progress == null || isInitAuto()) return;
        showQuickSearchDialog(new ArrayList<>());
        if (mQuickSearchDialog != null) mQuickSearchDialog.setProgress(progress.current(), progress.total(), progress.finished());
    }

    private void showQuickSearchDialog(List<Vod> items) {
        if (quickSearchDialogClosed) return;
        if (mQuickSearchDialog != null) {
            mQuickSearchDialog.addAll(items);
            return;
        }
        QuickSearchDialog dialog = QuickSearchDialog.create().listener(this).items(items);
        dialog.dismissListener(d -> {
            if (mQuickSearchDialog != dialog) return;
            mQuickSearchDialog = null;
            quickSearchDialogClosed = true;
        });
        mQuickSearchDialog = dialog;
        dialog.show(this);
    }

    private void dismissQuickSearchDialog() {
        QuickSearchDialog dialog = mQuickSearchDialog;
        mQuickSearchDialog = null;
        if (dialog != null) dialog.dismissAllowingStateLoss();
    }

    @Override
    public void onItemClick(Vod item) {
        setAutoMode(false);
        applySearchArtwork(item);
        getDetail(item);
    }

    private boolean mismatch(Vod item) {
        if (getId().equals(item.getId())) return true;
        if (mBroken.contains(item.getId())) return true;
        String keyword = Objects.toString(mBinding.part.getTag(), "");
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
        if (mQuickAdapter.getItemCount() == 0) return;
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

    private boolean onSeekBack() {
        controller().seekBack();
        return true;
    }

    private boolean onSeekForward() {
        controller().seekForward();
        return true;
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
        mBinding.control.action.fullscreen.setVisibility(fullscreen ? View.GONE : View.VISIBLE);
        mBinding.control.action.fullscreen.setText(R.string.play_fullscreen);
        applyActionButtonVisibility();
    }

    private void initTmdbMode() {
        // TMDB 模式：通过全局开关或 Intent 参数启用
        if (!isTmdbSourceEnabled()) return;

        mTmdbUIAdapter = new com.fongmi.android.tv.ui.helper.TmdbUIAdapter(this);
        if (!mTmdbUIAdapter.isReady()) {
            SpiderDebug.log("TMDB 增强已启用，但配置未就绪（需要 API Key）");
            return;
        }
        mTmdbUIAdapter.setPersonalAiUpdateListener(this::refreshTmdbPersonalAiRecommendations);
        com.fongmi.android.tv.bean.TmdbItem tmdbItem = getTmdbItem();
        if (tmdbItem != null) {
            SpiderDebug.log("TMDB 模式: 使用传入的 TmdbItem");
        }
    }

    private void applyShortDramaMode() {
        Site site = getSite();
        if (!Setting.isShortDramaSiteEnabled(site == null ? getKey() : site.getKey(), site == null ? "" : site.getName())) return;
        if (!isFullscreen()) enterFullscreen();
        // 优先使用用户手动设置的格式，如果没有设置过则使用短剧默认格式
        int scale = (mHistory != null && mHistory.getScale() != -1) ? mHistory.getScale() : SHORT_DRAMA_SCALE;
        setPreviewScale(scale);
        hideInfo();
    }

    private void setPreviewScale(int scale) {
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (scale < 0 || scale >= array.length) return;
        if (mHistory != null) mHistory.setScale(scale);
        applyResizeMode(scale);
        mBinding.control.action.scale.setText(array[scale]);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (!hasFocus) mKeyDown.releaseSpeed();
        if (!hasFocus || mDialogReturnFocus == null) return;
        View target = mDialogReturnFocus;
        mDialogReturnFocus = null;
        App.post(() -> {
            if (isFinishing() || isDestroyed() || !isFullscreen() || target.getVisibility() != View.VISIBLE) return;
            showControl(target);
        }, 120);
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

    @Override
    public boolean isControlAudioContent() {
        return isAudioOnly() || isMusicLike();
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE || !PlayerControlFocusHelper.isDescendant(mBinding.control.getRoot(), mFocus2) || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.next : mFocus2;
    }

    private boolean dispatchOpeningEndingAdjust(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !isVisible(mBinding.control.getRoot())) return false;
        View focus = getCurrentFocus();
        if (focus == mBinding.control.action.opening) return dispatchOpeningAdjust(event);
        if (focus == mBinding.control.action.ending) return dispatchEndingAdjust(event);
        return false;
    }

    private boolean dispatchOpeningAdjust(KeyEvent event) {
        if (KeyUtil.isUpKey(event)) {
            onOpeningAdd();
            return true;
        } else if (KeyUtil.isDownKey(event)) {
            onOpeningSub();
            return true;
        }
        return false;
    }

    private boolean dispatchEndingAdjust(KeyEvent event) {
        if (KeyUtil.isUpKey(event)) {
            onEndingAdd();
            return true;
        } else if (KeyUtil.isDownKey(event)) {
            onEndingSub();
            return true;
        }
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (KeyUtil.isActionUp(event) && KeyUtil.isBackKey(event) && mBinding.lutQuick.hideIfVisible()) return true;
        if (mKeyDown.isChangingSpeed() && KeyUtil.isActionUp(event)) {
            mKeyDown.releaseSpeed();
            if (isVisible(mBinding.lutQuick)) return dispatchLutQuickKey(event);
        }
        if (isVisible(mBinding.lutQuick)) return dispatchLutQuickKey(event);
        if (isFullscreen() && KeyUtil.isMenuKey(event)) {
            if (Setting.getFullscreenMenuKey() == 1) onEpisodes();
            else onToggle();
        }
        if (isVisible(mBinding.control.getRoot())) {
            setR1Callback();
            if (dispatchOpeningEndingAdjust(event)) return true;
            if (PlayerControlFocusHelper.handleKey(mBinding.control.getRoot(), getFocus2(), event)) return true;
            if (PlayerControlFocusHelper.containsFocus(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        }
        if (onEpisodeKey(event)) return true;
        if (handleEpisodeLongPress(event)) return true;
        if (mAudioStageVisible && isGone(mBinding.control.getRoot()) && dispatchAudioStageKey(event)) return true;
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event) && service() != null) return mKeyDown.onKeyDown(event);
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        return super.dispatchKeyEvent(event);
    }

    private boolean dispatchLutQuickKey(KeyEvent event) {
        if (KeyUtil.isEnterKey(event)) return dispatchLutQuickEnter(event);
        if (isLutQuickDirectionKey(event)) return dispatchLutQuickDirection(event);
        if (KeyUtil.isActionDown(event)) focusLutQuickContent();
        boolean handled = super.dispatchKeyEvent(event);
        if (KeyUtil.isActionDown(event)) {
            View focus = getCurrentFocus();
            if (focus == null || !isChildOf(mBinding.lutQuick, focus)) focusLutQuickContent();
        }
        return true;
    }

    private boolean isLutQuickDirectionKey(KeyEvent event) {
        return KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

    private boolean dispatchLutQuickDirection(KeyEvent event) {
        if (!KeyUtil.isActionDown(event)) return true;
        RecyclerView recycler = findRecyclerView(mBinding.lutQuick);
        View focus = getCurrentFocus();
        if (recycler != null && (focus == recycler || isChildOf(recycler, focus)) && moveLutQuickRecycler(recycler, event)) return true;
        if (focus == null || !isChildOf(mBinding.lutQuick, focus) || focus == recycler) {
            focusLutQuickContent();
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && moveLutQuickFocus(focus, event)) return true;
        if (recycler != null && KeyUtil.isDownKey(event) && focusRecyclerItem(recycler)) return true;
        focusLutQuickContent();
        return true;
    }

    private boolean moveLutQuickRecycler(RecyclerView recycler, KeyEvent event) {
        if (!KeyUtil.isUpKey(event) && !KeyUtil.isDownKey(event)) return false;
        RecyclerView.Adapter<?> adapter = recycler.getAdapter();
        if (adapter == null || adapter.getItemCount() <= 0) return false;
        int current = getRecyclerFocusPosition(recycler);
        if (current == RecyclerView.NO_POSITION) return mBinding.lutQuick.focusSelectedEntry();
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

    private boolean moveLutQuickFocus(View focus, KeyEvent event) {
        List<View> focusables = new ArrayList<>();
        collectLutQuickFocusables(mBinding.lutQuick, focusables);
        View target = findLutQuickFocusTarget(focus, focusables, event);
        return target != null && target.requestFocus();
    }

    private void collectLutQuickFocusables(View view, List<View> focusables) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return;
        if (view instanceof RecyclerView recycler) {
            for (int i = 0; i < recycler.getChildCount(); i++) collectLutQuickFocusables(recycler.getChildAt(i), focusables);
            return;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectLutQuickFocusables(group.getChildAt(i), focusables);
            return;
        }
        if (view.isFocusable()) focusables.add(view);
    }

    private View findLutQuickFocusTarget(View focus, List<View> focusables, KeyEvent event) {
        Rect current = new Rect();
        if (focus == null || !focus.getGlobalVisibleRect(current)) return null;
        View target = null;
        long bestScore = Long.MAX_VALUE;
        for (View item : focusables) {
            if (item == focus) continue;
            Rect candidate = new Rect();
            if (!item.getGlobalVisibleRect(candidate) || !isLutQuickFocusCandidate(current, candidate, event)) continue;
            long score = scoreLutQuickFocusCandidate(current, candidate, event);
            if (score < bestScore) {
                bestScore = score;
                target = item;
            }
        }
        return target;
    }

    private boolean isLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        int dx = candidate.centerX() - current.centerX();
        int dy = candidate.centerY() - current.centerY();
        if (KeyUtil.isLeftKey(event)) return dx < 0 && isSameFocusRow(current, candidate);
        if (KeyUtil.isRightKey(event)) return dx > 0 && isSameFocusRow(current, candidate);
        if (KeyUtil.isUpKey(event)) return dy < 0;
        if (KeyUtil.isDownKey(event)) return dy > 0;
        return false;
    }

    private boolean isSameFocusRow(Rect current, Rect candidate) {
        return Math.abs(candidate.centerY() - current.centerY()) <= Math.max(current.height(), candidate.height());
    }

    private long scoreLutQuickFocusCandidate(Rect current, Rect candidate, KeyEvent event) {
        long dx = Math.abs(candidate.centerX() - current.centerX());
        long dy = Math.abs(candidate.centerY() - current.centerY());
        long primary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dx : dy;
        long secondary = KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event) ? dy : dx;
        return primary * 1000 + secondary;
    }

    private boolean dispatchLutQuickEnter(KeyEvent event) {
        if (KeyUtil.isActionDown(event)) {
            focusLutQuickContent();
            return true;
        }
        if (!KeyUtil.isActionUp(event)) return true;
        View focus = getCurrentFocus();
        if (focus == null || !isChildOf(mBinding.lutQuick, focus) || focus instanceof RecyclerView) {
            if (!focusLutQuickContent()) return true;
            focus = getCurrentFocus();
        }
        if (focus != null && isChildOf(mBinding.lutQuick, focus) && focus.isEnabled()) focus.performClick();
        return true;
    }

    @Override
    public void onSeeking(long time) {
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(time));
        mBinding.widget.action.setImageResource(time > 0 ? R.drawable.ic_widget_forward : R.drawable.ic_widget_rewind);
        hideProgress();
    }

    @Override
    public void onSeekEnd(long time) {
        if (seekTo(time)) hideCenter();
        mKeyDown.reset();
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
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.control.action.speed.setText(player().setSpeed(getPlaybackSpeed()));
    }

    @Override
    public void onKeyUp() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) {
            showControl(mBinding.control.action.opening);
        } else if (player().canSetEnding(position, duration)) {
            showControl(mBinding.control.action.ending);
        } else {
            showControl(getFocus2());
        }
    }

    @Override
    public void onKeyDown() {
        showControl(getFocus2());
    }

    @Override
    public void onKeyCenter() {
        if (player() == null) return;
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
        hideControl();
    }

    private boolean handleEpisodeLongPress(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_DPAD_CENTER && event.getKeyCode() != KeyEvent.KEYCODE_ENTER) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
        if (!event.isLongPress()) return false;
        View focused = getCurrentFocus();
        if (focused == null) return false;
        // 检查焦点是否在选集卡片上
        if (focused.getId() != R.id.cardContainer) return false;
        RecyclerView episodeView = episodeGridMode ? mBinding.episodeGrid : mBinding.episode;
        RecyclerView.ViewHolder holder = episodeView.findContainingViewHolder(focused);
        if (holder == null) return false;
        int pos = holder.getBindingAdapterPosition();
        if (pos >= 0 && pos < mEpisodeAdapter.getItemCount()) {
            Episode item = mEpisodeAdapter.getItems().get(pos);
            onEpisodeLongClick(item);
            return true;
        }
        return false;
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        mPlayerUi.onStart();
        if (service() != null) refreshLyrics();
        setPlayParamsState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mKeyDown.releaseSpeed();
        mPlayerUi.onStop();
        if (mKaraoke != null) mKaraoke.clear();
        stopAudioCoverRotation();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        // 取消延迟播放，防止 Activity 进入后台后才触发播放导致声音残留
        App.removeCallbacks(mPendingFastTmdbPlaybackStart);
    }

    @Override
    protected void onBackInvoked() {
        if (mBinding.lutQuick.hideIfVisible()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isVisible(mBinding.widget.center)) {
            hideCenter();
        } else if (isFullscreen()) {
            exitFullscreen();
        } else {
            finishVideoPlayback();
        }
    }

    private void finishVideoPlayback() {
        if (isPlaybackExiting()) return;
        mViewModel.stopSearch();
        markPlaybackExiting();
        saveHistory(true);
        stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        super.onBackInvoked();
    }

    @Override
    protected void markPlaybackExiting() {
        if (isPlaybackExiting()) return;
        super.markPlaybackExiting();
        mIntroSkipPlayback.reset();
        cancelAiSeasonAnalysis(false);
        mPersonalRecommendationGeneration++;
        mPersonalRecommendationTasks.close();
        mTmdbRatingTasks.close();
        if (mTmdbHeaderView != null) mTmdbHeaderView.onDestroy();
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.release();
    }
    @Override
    protected void onDestroy() {
        mIntroSkipPlayback.reset();
        cancelAiSeasonAnalysis(false);
        mLyricsSearchSeq++;
        mLyricsRefreshSeq++;
        dismissLyricsResultDialog();
        if (mLyrics != null) mLyrics.release();
        if (mKaraoke != null) mKaraoke.release();
        subtitlePlaybackSession.stop(this);
        mPlayerUi.release();
        mPersonalRecommendationGeneration++;
        mPersonalRecommendationTasks.close();
        mTmdbRatingTasks.close();
        if (mTmdbHeaderView != null) mTmdbHeaderView.onDestroy();
        if (mTmdbUIAdapter != null) mTmdbUIAdapter.release();
        saveHistory(true);
        DanmakuApi.cancel();
        stopBackdropAutoScroll();
        dismissQuickSearchDialog();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mSeekProgressFallback, mAudioRefreshLyricsRunnable, mApplyAudioBackgroundRunnable, mHideAudioFocusRunnable);
        App.removeCallbacks(mPendingFastTmdbPlaybackStart);
        App.removeCallbacks(mTmdbDetailTimeout);
        App.removeCallbacks(mTmdbEpisodeTimeout);
        resetPendingTmdbBind();
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        mViewModel.getSearchProgress().removeObserver(mObserveSearchProgress);
        SiteHealthStore.flush();
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

private class AudioQueueAdapter extends RecyclerView.Adapter<AudioQueueAdapter.Holder> {

        private final List<Episode> items = new ArrayList<>();
        private int selected = -1;

        private void setItems(List<Episode> next, int selected) {
            items.clear();
            if (next != null) items.addAll(next);
            this.selected = selected;
            notifyDataSetChanged();
        }

        private int getSelectedPosition() {
            return selected;
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
            row.setFocusable(true);
            row.setFocusableInTouchMode(false);
            row.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(44)));

            TextView title = createAudioSheetText("", 14, false);
            title.setGravity(Gravity.CENTER_VERTICAL);
            title.setSingleLine(true);
            title.setMaxLines(1);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setBackground(null);
            row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

            ImageView remove = createAudioSheetInlineIconButton(R.drawable.ic_action_delete, () -> {});
            row.addView(remove, new LinearLayout.LayoutParams(ResUtil.dp2px(36), ResUtil.dp2px(36)));
            return new Holder(row, title, remove);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            if (items.isEmpty()) {
                holder.title.setText(getString(R.string.player_audio_playlist_empty));
                holder.title.setTextColor(0x99FFFFFF);
                holder.remove.setVisibility(View.GONE);
                holder.remove.setFocusable(false);
                holder.row.setBackground(null);
                holder.row.setOnClickListener(null);
                holder.row.setOnLongClickListener(null);
                holder.row.setOnKeyListener(null);
                holder.remove.setOnKeyListener(null);
                return;
            }
            Episode item = items.get(position);
            boolean active = position == selected;
            holder.title.setText((position + 1) + ". " + item.getDisplayName());
            holder.title.setTextColor(active ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
            holder.remove.setVisibility(View.VISIBLE);
            holder.remove.setFocusable(true);
            holder.remove.setOnClickListener(v -> removeAudioQueueEpisode(item));
            holder.remove.setOnKeyListener((v, keyCode, event) -> {
                if (!KeyUtil.isLeftKey(event)) return false;
                if (KeyUtil.isActionDown(event)) holder.row.requestFocus();
                return true;
            });
            holder.row.setBackground(audioSheetItemBackground(active));
            holder.row.setOnClickListener(v -> playAudioQueueEpisode(item));
            holder.row.setOnLongClickListener(v -> {
                removeAudioQueueEpisode(item);
                return true;
            });
            holder.row.setOnKeyListener((v, keyCode, event) -> {
                if (!KeyUtil.isRightKey(event) || holder.remove.getVisibility() != View.VISIBLE) return false;
                if (KeyUtil.isActionDown(event)) holder.remove.requestFocus();
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

private interface LyricsChoiceHandler {
        void onChoice(int which);
    }

private interface AudioTextInputHandler {
        void onSubmit(String text);
    }

private interface LyricsLongSetter {
        void set(long value);
    }

private interface LyricsLongGetter {
        long get();
    }

private interface SegmentClickHandler {
        void onClick(int index);
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

private void ensureImmersiveAudioControllers() {
        if (mLyrics != null && mKaraoke != null) return;
        mLyrics = new LyricsController(mBinding.lyrics);
        mLyrics.setSecondaryView(mBinding.audioLyrics);
        mBinding.audioLyrics.setAudioStageMode(true);
        mBinding.audioLyrics.setSeekListener(this::onAudioLyricsSeek);
        mBinding.audioLyrics.setSuppressed(true);
        mKaraoke = new KaraokeController();
        mKaraoke.setListener((status, track, sample, snapshot) -> {
            boolean playing = service() != null && player().isPlaying();
            mBinding.karaoke.setPlaying(playing);
            mBinding.audioKaraoke.setPlaying(playing);
            mBinding.karaoke.setState(status, track, sample, snapshot);
            mBinding.audioKaraoke.setState(status, track, sample, snapshot);
            syncKaraokeStageVisibility();
        });
    }

private void disableLeanbackDesktopLyrics() {
        if (PlayerSetting.isDesktopLyrics()) PlayerSetting.putDesktopLyrics(false);
    }

private void setupAudioStageOverlay() {
        mBinding.audioStage.setSaveFromParentEnabled(false);
        ViewGroup parent = (ViewGroup) mBinding.audioStage.getParent();
        if (parent != null) parent.removeView(mBinding.audioStage);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        mBinding.progressLayout.addOverlayView(mBinding.audioStage, params);
        mBinding.audioStage.bringToFront();
        // TV 歌曲舞台自动判断已暂时关闭，重挂 overlay 时也必须保持隐藏。
        mAudioStageVisible = false;
        mBinding.audioStage.setVisibility(View.GONE);
    }

private void setupAudioStageFocusFeedback() {
        View.OnFocusChangeListener listener = (view, hasFocus) -> {
            if (hasFocus) showAudioStageFocusHighlight(view);
            else if (isAudioStageIconButton(view)) view.setBackground(ResUtil.getDrawable(R.drawable.selector_audio_action_icon));
        };
        for (View view : audioStageFocusButtons()) view.setOnFocusChangeListener(listener);
    }

private View[] audioStageFocusButtons() {
        return new View[]{
                mBinding.audioRepeatAction, mBinding.audioPrev, mBinding.audioPlay, mBinding.audioNext, mBinding.audioQueueAction,
                mBinding.audioLyricsAction, mBinding.audioKaraokeAction, mBinding.audioMoreAction,
                mBinding.audioCastAction, mBinding.audioKeepAction, mBinding.audioSettingAction, mBinding.audioTrackAction, mBinding.audioSubtitleAction, mBinding.audioInfoAction,
                mBinding.audioBackgroundAction
        };
    }

private void showAudioStageFocusHighlight(@Nullable View target) {
        if (target == null) return;
        App.removeCallbacks(mHideAudioFocusRunnable);
        resetAudioStageIconBackgrounds();
        if (isAudioStageIconButton(target)) target.setBackground(ResUtil.getDrawable(R.drawable.shape_audio_action_icon_focused));
        App.post(mHideAudioFocusRunnable, 3000);
    }

private void hideAudioStageFocusHighlight() {
        resetAudioStageIconBackgrounds();
    }

private void resetAudioStageIconBackgrounds() {
        for (View view : audioStageIconButtons()) view.setBackground(ResUtil.getDrawable(R.drawable.selector_audio_action_icon));
    }

private boolean isAudioStageIconButton(View view) {
        for (View item : audioStageIconButtons()) if (view == item) return true;
        return false;
    }

private View[] audioStageIconButtons() {
        return new View[]{
                mBinding.audioRepeatAction, mBinding.audioPrev, mBinding.audioPlay, mBinding.audioNext, mBinding.audioQueueAction,
                mBinding.audioLyricsAction, mBinding.audioKaraokeAction, mBinding.audioMoreAction
        };
    }

private void restoreAudioEpisodeDisplayNames(List<Episode> items) {
        if (items == null) return;
        for (Episode item : items) {
            String title = mAudioQueueTitles.get(audioQueueEpisodeKey(item));
            item.setDisplayName(TextUtils.isEmpty(title) ? item.getRawDisplayName() : title);
        }
    }

private void onAudioLyricsSeek(long positionMs) {
        if (service() == null || player().isEmpty()) return;
        long duration = player().getDuration();
        long target = duration > 0 ? Math.min(Math.max(0, positionMs), Math.max(0, duration - 500)) : Math.max(0, positionMs);
        player().seekTo(target);
        if (mHistory != null) mHistory.setPosition(target);
        if (mLyrics != null) mLyrics.update(target);
    }

private boolean onLyricsSearch() {
        if (!isLyricsSearchAvailable()) return false;
        LyricsRequest request = service() == null ? null : LyricsRequest.from(player());
        String keyword = request == null ? getName() : request.displayKeyword();
        String signature = getLyricsSearchSuggestionSignature(request, getName());
        showLyricsSearchSheet(keyword, request, signature, ++mLyricsSearchSheetSeq);
        return true;
    }

private void showLyricsSearchSheet(String keyword, @Nullable LyricsRequest request, String signature, int sheetSeq) {
        BottomSheetDialog dialog = createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        if (ResUtil.isLand(this)) {
            int height = audioDrawerHeight();
            root.setMinimumHeight(height);
            root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        }
        root.addView(createAudioSheetTitle(getString(R.string.player_lyrics_reload)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setMaxLines(1);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(0x70FFFFFF);
        input.setHint(getString(R.string.player_lyrics_keyword));
        input.setTextSize(15);
        input.setPadding(ResUtil.dp2px(14), 0, ResUtil.dp2px(14), 0);
        input.setBackground(audioSheetControlBackground(0x14FFFFFF, 0x32FFFFFF));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT | InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setText(TextUtils.isEmpty(keyword) ? "" : keyword);
        if (input.getText() != null) input.setSelection(input.getText().length());
        row.addView(input, new LinearLayout.LayoutParams(0, ResUtil.dp2px(50), 1));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(ResUtil.dp2px(50), ResUtil.dp2px(50));
        searchParams.leftMargin = ResUtil.dp2px(10);
        View searchButton = createAudioSheetIconButton(R.drawable.ic_action_search, () -> submitLyricsSearchSheet(input, request));
        row.addView(searchButton, searchParams);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = ResUtil.dp2px(12);
        root.addView(row, inputParams);

        dialog.setContentView(root);
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            submitLyricsSearchSheet(input, request);
            return true;
        });
        LinearLayout suggestionsRoot = new LinearLayout(this);
        suggestionsRoot.setOrientation(LinearLayout.VERTICAL);
        root.addView(suggestionsRoot, audioSheetWrapTopParams(8));

        TextView status = createAudioSheetText("", 13, false);
        status.setTextColor(SHEET_TEXT_MUTED);
        status.setVisibility(View.GONE);
        root.addView(status, audioSheetTopParams(8, 26));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        scroll.setVisibility(View.GONE);
        mLyricsResultList = new LinearLayout(this);
        mLyricsResultList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(mLyricsResultList, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, ResUtil.isLand(this)
                ? new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)
                : lyricsResultSheetParams(1));

        showLyricsSearchSheetDialog(dialog);
        focusLyricsSearchTarget(input, searchButton);
        dialog.setOnCancelListener(d -> mLyricsSearchSeq++);
        dialog.setOnDismissListener(d -> {
            if (mLyricsSearchDialog == dialog) {
                mLyricsSearchDialog = null;
                mLyricsSearchSheetSeq++;
            }
            if (mLyricsResultDialog == dialog) {
                mLyricsResultDialog = null;
                mLyricsResultList = null;
                mLyricsSearchStatus = null;
            }
        });
        mLyricsSearchDialog = dialog;
        mLyricsResultDialog = dialog;
        mLyricsSearchStatus = status;
        loadLyricsSearchSuggestions(dialog, suggestionsRoot, input, searchButton, request, keyword, signature, sheetSeq, mLyricsSearchSeq);
    }

private void focusLyricsSearchTarget(EditText input, View focusTarget) {
        if (input == null || focusTarget == null) return;
        input.clearFocus();
        Util.hideKeyboard(input);
        focusTarget.requestFocus();
    }

private void loadLyricsSearchSuggestions(BottomSheetDialog dialog, LinearLayout root, EditText input, View searchButton, @Nullable LyricsRequest request, String keyword, String signature, int sheetSeq, int searchSeqAtOpen) {
        Task.execute(() -> {
            List<String> suggestions = request == null ? LyricsRequest.searchSuggestions(keyword) : request.searchSuggestions();
            List<String> values = withLastLyricsSearchSuggestion(suggestions, signature);
            App.post(() -> {
                if (sheetSeq != mLyricsSearchSheetSeq || !dialog.isShowing()) return;
                View firstSuggestion = addLyricsSearchSuggestions(root, input, searchButton, request, values);
                View focusTarget = firstSuggestion == null ? searchButton : firstSuggestion;
                focusLyricsSearchTarget(input, focusTarget);
                focusTarget.postDelayed(() -> focusLyricsSearchTarget(input, focusTarget), 220);
                focusTarget.postDelayed(() -> focusLyricsSearchTarget(input, focusTarget), 420);
                String current = input.getText() == null ? "" : input.getText().toString();
                String autoKeyword = firstLyricsSearchSuggestion(values);
                if (!TextUtils.isEmpty(autoKeyword) && mLyricsSearchSeq == searchSeqAtOpen && (TextUtils.isEmpty(current) || TextUtils.equals(current, keyword))) {
                    searchLyrics(autoKeyword, request, true);
                }
            });
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

@Nullable
    private View addLyricsSearchSuggestions(LinearLayout root, EditText input, View searchButton, @Nullable LyricsRequest request, List<String> suggestions) {
        root.removeAllViews();
        if (suggestions == null || suggestions.isEmpty()) return null;
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(HorizontalScrollView.OVER_SCROLL_NEVER);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        View first = null;
        int count = Math.min(8, suggestions.size());
        for (int i = 0; i < count; i++) {
            String text = suggestions.get(i);
            if (TextUtils.isEmpty(text)) continue;
            TextView chip = createLyricsSearchSuggestionChip(input, searchButton, request, text);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(32));
            if (row.getChildCount() > 0) params.leftMargin = ResUtil.dp2px(6);
            row.addView(chip, params);
            if (first == null) first = chip;
        }
        if (row.getChildCount() == 0) return null;
        scroll.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(32)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(32));
        params.topMargin = ResUtil.dp2px(8);
        root.addView(scroll, params);
        return first;
    }

private TextView createLyricsSearchSuggestionChip(EditText input, View searchButton, @Nullable LyricsRequest request, String text) {
        TextView chip = createAudioSheetText(text, 13, false);
        chip.setGravity(Gravity.CENTER);
        chip.setSingleLine(true);
        chip.setEllipsize(TextUtils.TruncateAt.END);
        chip.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(10), 0);
        chip.setTextColor(SHEET_TEXT_SECONDARY);
        chip.setBackground(audioSheetControlBackground(SHEET_CONTROL_BG_SUBTLE, SHEET_CONTROL_STROKE));
        setAudioSheetFocusable(chip);
        chip.setOnClickListener(v -> {
            input.setText(text);
            if (input.getText() != null) input.setSelection(input.getText().length());
            focusLyricsSearchTarget(input, searchButton);
            searchLyrics(text, request, false);
        });
        return chip;
    }

private void submitLyricsSearchSheet(EditText input, @Nullable LyricsRequest request) {
        String keyword = input.getText() == null ? "" : input.getText().toString().trim();
        if (TextUtils.isEmpty(keyword)) {
            input.setError(getString(R.string.player_lyrics_keyword_required));
            return;
        }
        Util.hideKeyboard(input);
        searchLyrics(keyword, request, false);
    }

private void checkPlay() {
        setR1Callback();
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
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
        int tab = selectedTab == AUDIO_QUEUE_TAB_SEARCH ? AUDIO_QUEUE_TAB_SEARCH : AUDIO_QUEUE_TAB_CURRENT;
        boolean queueDrawer = tab == AUDIO_QUEUE_TAB_CURRENT && isLandscapeAudioSheet();
        Dialog dialog = queueDrawer ? createAudioQueueDialog() : createAudioSheet();
        LinearLayout root = createAudioSheetRoot();
        if (tab == AUDIO_QUEUE_TAB_CURRENT && isLandscapeAudioSheet()) styleAudioQueueDrawerRoot(root);
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
        if (queueDrawer) showAudioQueueDrawerDialog(dialog);
        else showCompactPlaybackSheet((BottomSheetDialog) dialog);
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

private View createAudioPlaylistHeader(Dialog dialog) {
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

private View createAudioQueueSearchHeader(Dialog dialog) {
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
        int selected = getSelectedEpisodePosition(items);
        mAudioQueueAdapter.setItems(items, selected);
        if (mAudioQueueList != null && selected >= 0) {
            mAudioQueueList.post(() -> {
                mAudioQueueList.scrollToPosition(selected);
                mAudioQueueList.post(() -> focusAudioQueueItem(selected));
            });
        }
    }

private void focusAudioQueueItem(int position) {
        if (mAudioQueueList == null) return;
        RecyclerView.ViewHolder holder = mAudioQueueList.findViewHolderForAdapterPosition(position);
        if (holder != null) holder.itemView.requestFocus();
    }

private void focusAudioQueueSelectedItem() {
        if (mAudioQueueList == null || mAudioQueueAdapter == null) return;
        int selected = mAudioQueueAdapter.getSelectedPosition();
        if (selected < 0) return;
        mAudioQueueList.post(() -> {
            mAudioQueueList.scrollToPosition(selected);
            mAudioQueueList.post(() -> focusAudioQueueItem(selected));
        });
    }

private void playAudioQueueEpisode(Episode item) {
        if (item == null) return;
        if (mAudioQueueDialog != null) mAudioQueueDialog.dismiss();
        onItemClick(item);
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
        mAudioQueueTitles.put(key, vod.getName());
        mAudioQueuePics.put(key, vod.getPic());
        mAudioQueueLyrics.put(key, getTimedLyrics(vod.getContent()));
        String artist = getArtistFromEpisode(vod.getName(), sourceEpisode.getName());
        if (!TextUtils.isEmpty(artist)) mAudioQueueArtists.put(key, artist);
        AudioPlaylistStore.Entry entry = new AudioPlaylistStore.Entry();
        entry.name = episode.getName();
        entry.url = episode.getUrl();
        entry.playFlag = source.getFlag();
        entry.title = vod.getName();
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
        mAudioQueueTitles.put(key, entry.title);
        mAudioQueuePics.put(key, entry.pic);
        mAudioQueueLyrics.put(key, entry.lyrics);
        if (!TextUtils.isEmpty(entry.artist)) mAudioQueueArtists.put(key, entry.artist);
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
        addAudioMoreItem(items, actions, getString(R.string.home_setting), this::onSetting);
        addAudioMoreItem(items, actions, getString(R.string.play_cast), this::onCast);
        addAudioMoreItem(items, actions, getString(R.string.play_timer), this::onTimer);
        addAudioMoreItem(items, actions, getString(R.string.player_audio_background), this::showAudioBackgroundPanel);
        if (service() != null && !player().isEmpty()) addAudioMoreItem(items, actions, getString(R.string.player_osd), this::onPlayParams);
        if (service() != null && player().haveTrack(C.TRACK_TYPE_AUDIO)) addAudioMoreItem(items, actions, getString(R.string.play_track_audio), () -> onTrack(C.TRACK_TYPE_AUDIO));
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

private void setAudioToolRowVisible(boolean visible, boolean requestFocus) {
        mBinding.audioToolRow.setVisibility(View.GONE);
        mBinding.audioMoreAction.setSelected(false);
    }

private void onKaraokeMode() {
        showKaraokeModePanel();
    }

private void setKaraokeMode(boolean enable) {
        if (PlayerSetting.isKaraokeMode() == enable) return;
        PlayerSetting.putKaraokeMode(enable);
        onKaraokeModeChanged();
        showControl(mBinding.control.action.karaoke);
    }

public boolean onKaraokeTrackPanel() {
        showLyricsSettingsPanel(LYRICS_TAB_TRACK);
        return true;
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
        renderLyricsSettingsPanel(dialog, root, selectedTab);
        dialog.setContentView(root);
        showLyricsSettingsSheet(dialog);
    }

private void renderLyricsSettingsPanel(BottomSheetDialog dialog, LinearLayout root, int selectedTab) {
        while (root.getChildCount() > 1) root.removeViewAt(1);
        int tab = Math.max(LYRICS_TAB_LYRICS, Math.min(LYRICS_TAB_TRACK, selectedTab));
        root.addView(createLyricsSettingsTabs(dialog, root, tab), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(34)));
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
                            getString(R.string.player_lyrics_cache) + " " + getString(R.string.player_lyrics_cache_value, LyricsRepository.cacheCount())
                    },
                    new Runnable[]{
                            this::showLyricsRowsPanel,
                            this::showLyricsSizePanel,
                            this::showLyricsSourcePanel,
                            this::openLyricsSearchFromSettings,
                            this::clearLyricsCacheFromSettings
                    },
                    3), karaokeActionGridParams(8));
        }
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

private LinearLayout createLyricsSettingsTabs(BottomSheetDialog dialog, LinearLayout root, int selectedTab) {
        return createSegmentedControl(
                new String[]{getString(R.string.player_audio_badge_lyrics), getString(R.string.player_karaoke_mode), getString(R.string.player_karaoke_track)},
                selectedTab,
                index -> {
                    if (index == selectedTab) return;
                    FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
                    int height = sheet == null ? 0 : sheet.getHeight();
                    renderLyricsSettingsPanel(dialog, root, index);
                    root.requestLayout();
                    root.post(() -> {
                        preserveLyricsSettingsSheetHeight(dialog, height);
                        focusFirstChild(root);
                    });
                });
    }

private void preserveLyricsSettingsSheetHeight(BottomSheetDialog dialog, int height) {
        if (height <= 0) return;
        FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        ViewGroup.LayoutParams params = sheet.getLayoutParams();
        params.height = height;
        sheet.setLayoutParams(params);
        BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
        behavior.setPeekHeight(height);
        behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
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
        if (service() == null || mLyrics == null || !KaraokeTrackRepository.canGenerate(mLyrics.getLines())) {
            Notify.show(R.string.player_karaoke_track_generate_no_lyrics);
            return;
        }
        onKaraokeTrackGenerated(KaraokeTrackRepository.importGenerated(player(), mLyrics.getLines()));
    }

private void onKaraokeTrackGenerated(KaraokeTrackRepository.ImportResult result) {
        if (result != null && result.isSuccess()) {
            Notify.show(R.string.player_karaoke_track_generated);
            applyKaraokeTrackChange(true);
        } else {
            String error = result == null ? "" : result.getError();
            Notify.show(getString(R.string.player_karaoke_track_generate_failed) + (TextUtils.isEmpty(error) ? "" : "\n" + error));
        }
    }

private void generateKaraokePitchTrack() {
        List<LyricsLine> lines = mLyrics == null ? null : mLyrics.getLines();
        KaraokeTrackRepository.MediaInput input = service() == null ? null : KaraokeTrackRepository.snapshot(player());
        if (!KaraokeTrackRepository.canGeneratePitch(input, lines)) {
            Notify.show(R.string.player_karaoke_track_generate_no_lyrics);
            return;
        }
        showKaraokePitchProgress();
        Task.execute(() -> {
            KaraokeTrackRepository.ImportResult result = KaraokeTrackRepository.importGeneratedPitch(input, lines, (percent, stage, elapsedMs, remainingMs) -> App.post(() -> updateKaraokePitchProgress(percent, stage, remainingMs)));
            App.post(() -> onKaraokePitchTrackGenerated(result));
        });
    }

private void onKaraokePitchTrackGenerated(KaraokeTrackRepository.ImportResult result) {
        dismissKaraokePitchProgress();
        if (result != null && result.isSuccess()) {
            applyKaraokeTrackChange(true);
            showKaraokePitchResult(R.string.player_karaoke_track_generated_pitch, getString(R.string.player_karaoke_track_generated_pitch_message));
        } else {
            String error = result == null ? "" : result.getError();
            showKaraokePitchResult(R.string.player_karaoke_track_generate_pitch_failed, getString(R.string.player_karaoke_track_generate_pitch_failed_message, TextUtils.isEmpty(error) ? getString(R.string.player_karaoke_track_generate_pitch_failed) : error));
        }
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
        dialog.setContentView(root);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
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

private void applyKaraokeTrackChange(boolean enableMode) {
        if (enableMode && !PlayerSetting.isKaraokeMode()) {
            PlayerSetting.putKaraokeMode(true);
        }
        refreshLyrics();
        reloadKaraokeTrack();
    }

private void reloadKaraokeTrack() {
        if (mKaraoke == null || service() == null) return;
        setAudioOnly(LyricsController.isAudioOnly(player()));
        mKaraoke.reload(this, player(), shouldUseImmersiveAudio());
    }

private boolean showKaraokeResultIfNeeded() {
        return showKaraokeResultIfNeeded(null);
    }

private boolean showKaraokeResultIfNeeded(@Nullable Runnable after) {
        if (mKaraoke == null || !mKaraoke.isActive() || mKaraokeResultShown || isFinishing() || isDestroyed()) return false;
        KaraokeResult result = mKaraoke.getResult();
        if (result == null) return false;
        mKaraokeResultShown = true;
        KaraokeResultView view = new KaraokeResultView(this).setLeanbackLandscapeExpanded(true).setResult(result);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_LightDialog).setView(view).create();
        view.setAction(() -> {
            dialog.dismiss();
            runAfterKaraokeResult(after);
        });
        dialog.setOnCancelListener(d -> runAfterKaraokeResult(after));
        configureKaraokeResultDialog(dialog, view);
        dialog.show();
        return true;
    }

private void configureKaraokeResultDialog(AlertDialog dialog, KaraokeResultView view) {
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
                WindowManager.LayoutParams params = window.getAttributes();
                if (isLandscapeAudioSheet()) {
                    params.dimAmount = 0f;
                    params.gravity = Gravity.CENTER;
                    params.x = 0;
                    params.y = 0;
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                    window.setAttributes(params);
                    window.setLayout(view.getPreferredDialogWidth(), WindowManager.LayoutParams.WRAP_CONTENT);
                    Util.hideSystemUI(window);
                    Util.hideSystemUI(this);
                } else {
                    params.dimAmount = 0.62f;
                    params.gravity = Gravity.CENTER;
                    window.setAttributes(params);
                    window.setLayout(view.getPreferredDialogWidth(), WindowManager.LayoutParams.WRAP_CONTENT);
                    window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                }
            }
            view.requestActionFocus();
        });
    }

private void runAfterKaraokeResult(@Nullable Runnable after) {
        if (after != null) after.run();
    }

public void onLutSelected(LutPreset preset) {
        LutSetting.select(preset);
        if (preset == null) player().applyLut(true);
        else player().applyLutPreview(true);
        setLut();
        setR1Callback();
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic, service() == null ? null : player());
        hidePlaybackProgressIfStale();
        App.post(mR3, 1000);
    }

private void refreshLyrics() {
        if (isMusicLike() && mAudioRefreshLyricsRunnable != null) {
            App.removeCallbacks(mAudioRefreshLyricsRunnable);
            App.post(mAudioRefreshLyricsRunnable, mAudioStageVisible ? 320 : 120);
            return;
        }
        refreshLyricsNow();
    }

private void refreshLyricsNow() {
        boolean audioContent = shouldUseImmersiveAudio();
        setAudioStageVisible(audioContent);
        if (mLyrics == null || service() == null) return;
        int seq = ++mLyricsRefreshSeq;
        setAudioOnly(LyricsController.isAudioOnly(player()));
        if (!audioContent) {
            mLyrics.refresh(player(), false);
            scheduleRefreshKaraoke(seq, false, 0);
            return;
        }
        LyricsRequest request = LyricsRequest.from(player());
        String playbackKey = Objects.toString(mPlaybackEpisodeKey, "");
        Task.execute(() -> {
            boolean hasChoice = mLyrics.hasChoice(request);
            App.post(() -> {
                if (seq != mLyricsRefreshSeq || service() == null || !TextUtils.equals(playbackKey, Objects.toString(mPlaybackEpisodeKey, ""))) return;
                if (!hasChoice && showInlineLyrics()) {
                    scheduleRefreshKaraoke(seq, true, 420);
                    return;
                }
                mLyrics.refresh(player(), true);
                scheduleRefreshKaraoke(seq, true, 420);
            });
        });
    }

private void scheduleRefreshKaraoke(int seq, boolean audioContent, long delayMs) {
        App.post(() -> {
            if (seq != mLyricsRefreshSeq) return;
            refreshKaraoke(audioContent);
        }, delayMs);
    }

private void setAudioStageVisible(boolean visible) {
        visible = visible && PlayerSetting.isImmersiveAudioMode();
        if (visible) ensureImmersiveAudioControllers();
        // Android may restore the View visibility independently of this cached flag.
        mBinding.audioStage.setVisibility(visible ? View.VISIBLE : View.GONE);
        // Song changes can expose progress/control layers without changing the cached stage state.
        if (visible) {
            mBinding.audioStage.bringToFront();
            hideProgress();
            hideControl();
            hideInfo();
            Util.hideSystemUI(this);
        }
        if (mAudioStageVisible == visible) {
            syncAudioStageSurface(visible);
            updateAudioStageText();
            updateAudioStageControls();
            return;
        }
        mAudioStageVisible = visible;
        if (!visible) {
            mAudioLightEffectAnimated = false;
            setAudioToolRowVisible(false, false);
        }
        if (visible) scheduleAudioBackground(96);
        syncAudioStageSurface(visible);
        applyAudioBackgroundActionInsets();
        applyAudioStageLayout(visible);
        updateAudioStageText();
        updateAudioStageControls();
        if (visible) mBinding.audioStage.post(this::focusAudioStageDefault);
    }

    private boolean shouldUseImmersiveAudio() {
        // TV 端只接受音频源入口或用户手动选择，不能再根据标题和早期轨道状态猜测。
        return PlayerSetting.isImmersiveAudioMode() && mImmersiveAudioRequested;
    }

private AudioPlaybackResolver.Resolved takeImmersiveAudioLaunch() {
        String cacheKey = Objects.toString(getIntent().getStringExtra(EXTRA_IMMERSIVE_AUDIO_CACHE_KEY), "");
        return TextUtils.isEmpty(cacheKey) ? null : IMMERSIVE_AUDIO_LAUNCHES.remove(cacheKey);
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
        mImmersiveAudioRequested = true;
        mImmersiveAudioResolved = resolved;
        Vod vod = resolved.getVod();
        Result result = resolved.getResult();
        Episode episode = resolved.getEpisode();
        String pic = result.hasArtwork() ? result.getArtwork() : vod.getPic();
        getIntent().putExtra("key", resolved.getSiteKey());
        getIntent().putExtra("id", resolved.getVodId());
        PlayerSetting.putImmersiveAudioPlayback(getHistoryKey());
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
        int position = mFlagAdapter.indexOf(flag);
        if (position != RecyclerView.NO_POSITION) mBinding.flag.setSelectedPosition(position);
        notifyItemChanged(mBinding.flag, mFlagAdapter);
        setEpisodeAdapter(flag.getEpisodes(), false);
        setQualityVisible(false);
        mBinding.widget.title.setText(getPlaybackControlTitle(episode));
        mBinding.widget.title.setSelected(true);
    }

private void syncAudioStageSurface(boolean visible) {
        boolean immersiveEnabled = PlayerSetting.isImmersiveAudioMode();
        mBinding.lyrics.setAudioStageMode(visible);
        mBinding.lyrics.setSuppressed(visible || !immersiveEnabled);
        mBinding.audioLyrics.setSuppressed(!visible);
        syncKaraokeStageVisibility();
        setVideoDetailsVisible(!visible);
    }

private void showInitialAudioStage() {
        mAudioStageVisible = true;
        mBinding.audioStage.setVisibility(View.VISIBLE);
        mBinding.audioStage.bringToFront();
        hideProgress();
        mBinding.control.getRoot().setVisibility(View.GONE);
        if (mOsd != null) mOsd.setControlsVisible(false);
        App.removeCallbacks(mR1);
        hideInfo();
        syncAudioStageSurface(true);
        applyAudioBackgroundActionInsets();
        applyAudioStageLayout(true);
        mBinding.audioTitle.setText(TextUtils.isEmpty(getName()) ? getString(R.string.player_audio_badge_audio) : getName());
        mBinding.audioSubtitle.setVisibility(View.GONE);
        Util.hideSystemUI(this);
    }

private void syncKaraokeStageVisibility() {
        if (mBinding == null) return;
        if (!PlayerSetting.isImmersiveAudioMode()) {
            mBinding.karaoke.setVisibility(View.GONE);
            mBinding.audioKaraoke.setVisibility(View.GONE);
            return;
        }
        if (mAudioStageVisible) {
            mBinding.karaoke.setVisibility(View.GONE);
            if (PlayerSetting.isKaraokeMode() && mBinding.audioKaraoke.getVisibility() == View.GONE) mBinding.audioKaraoke.setVisibility(View.INVISIBLE);
        } else {
            mBinding.audioKaraoke.setVisibility(View.GONE);
        }
    }

private void applyAudioStageLayout(boolean visible) {
        if (isFullscreen()) return;
        if (visible) {
            mBinding.video.setForeground(null);
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            mBinding.video.setForeground(ResUtil.getDrawable(R.drawable.selector_video));
            mBinding.video.setLayoutParams(mFrameParams);
        }
    }

private void setVideoDetailsVisible(boolean visible) {
        int value = visible ? View.VISIBLE : View.GONE;
        boolean showNativeMetadata = visible && (!shouldUseTmdbLayout() || isIntentTmdbPlayback());
        mBinding.name.setVisibility(value);
        mBinding.remark.setVisibility(shouldShowVideoDetailRemark(visible) ? View.VISIBLE : View.GONE);
        mBinding.row1.setVisibility(value);
        mBinding.director.setVisibility(showNativeMetadata && !TextUtils.isEmpty(mBinding.director.getText()) ? View.VISIBLE : View.GONE);
        mBinding.actor.setVisibility(showNativeMetadata && !TextUtils.isEmpty(mBinding.actor.getText()) ? View.VISIBLE : View.GONE);
        mBinding.row2.setVisibility(value);
        mBinding.scroll.setVisibility(value);
    }

private boolean shouldShowVideoDetailRemark(boolean visible) {
        if (!visible || TextUtils.isEmpty(mBinding.remark.getText())) return false;
        if (!shouldUseTmdbLayout() || isIntentTmdbPlayback()) return true;
        if (mTmdbUIAdapter == null) return false;
        String episodeInfo = mTmdbUIAdapter.getEpisodeDetailText();
        return !TextUtils.isEmpty(episodeInfo) && TextUtils.equals(mBinding.remark.getText(), episodeInfo);
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
        if (mAudioStageVisible) setVideoDetailsVisible(false);
        applyAudioBackgroundActionInsets();
        boolean hasPrev = hasAudioAdjacent(mHistory != null && mHistory.isRevPlay() ? 1 : -1);
        boolean hasNext = hasAudioAdjacent(mHistory != null && mHistory.isRevPlay() ? -1 : 1);
        mBinding.audioPrev.setEnabled(hasPrev);
        mBinding.audioPrev.setAlpha(hasPrev ? 1f : 0.35f);
        mBinding.audioNext.setEnabled(hasNext);
        mBinding.audioNext.setAlpha(hasNext ? 1f : 0.35f);
        mBinding.audioQueueAction.setEnabled(true);
        mBinding.audioQueueAction.setAlpha(1f);
        setAudioToolEnabled(mBinding.audioTrackAction, service() != null && player().haveTrack(C.TRACK_TYPE_AUDIO));
        setAudioToolEnabled(mBinding.audioSubtitleAction, service() != null && player().haveTrack(C.TRACK_TYPE_TEXT));
        setAudioToolEnabled(mBinding.audioInfoAction, service() != null && !player().isEmpty());
        setAudioRepeatSelected(service() != null && player().isRepeatOne());
        mBinding.audioKaraokeAction.setSelected(false);
        checkKeepImg();
        checkAudioPlayImg(service() != null && player().isPlaying());
        syncAudioCoverRotation();
    }

private void applyAudioBackgroundActionInsets() {
        if (mBinding == null || mBinding.audioBackgroundAction == null) return;
        ViewGroup.LayoutParams raw = mBinding.audioBackgroundAction.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams params)) return;
        int top = -mBinding.audioStage.getPaddingTop();
        int end = -mBinding.audioStage.getPaddingEnd();
        if (params.topMargin == top && params.getMarginEnd() == end) return;
        params.topMargin = top;
        params.setMarginEnd(end);
        mBinding.audioBackgroundAction.setLayoutParams(params);
    }

private boolean hasAudioAdjacent(int offset) {
        if (mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() <= 0) return false;
        int position = mEpisodeAdapter.getSelectedPosition();
        if (position == RecyclerView.NO_POSITION) position = mEpisodeAdapter.getPosition();
        int target = position + offset;
        return target >= 0 && target < mEpisodeAdapter.getItemCount();
    }

private void setAudioToolEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.35f);
    }

private void setAudioRepeatSelected(boolean selected) {
        if (mBinding == null) return;
        mBinding.audioRepeatAction.setSelected(selected);
        mBinding.audioRepeatAction.setAlpha(selected ? 1f : 0.62f);
    }

private void applyAudioBackground() {
        if (mBinding == null || !mAudioStageVisible) return;
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

private void scheduleAudioBackground(long delayMs) {
        if (mApplyAudioBackgroundRunnable == null) return;
        App.post(mApplyAudioBackgroundRunnable, delayMs);
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
        return Math.floorMod(mixAudioBackgroundSeed(seed == 0 ? 0x5A17B3 : seed), 360);
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

private void updateAudioArtworkColor(String key, @Nullable Drawable drawable) {
        if (TextUtils.equals(mAudioArtworkColorKey, key)) return;
        mAudioArtworkColorKey = key;
        mAudioArtworkColor = extractAudioArtworkColor(drawable);
        if (mAudioStageVisible && PlayerSetting.getAudioBackground() == PlayerSetting.AUDIO_BACKGROUND_ARTWORK && mApplyAudioBackgroundRunnable != null) {
            App.removeCallbacks(mApplyAudioBackgroundRunnable);
            App.post(mApplyAudioBackgroundRunnable, 240);
        }
    }

private void scheduleAudioArtworkColorUpdate(String owner, String key, @Nullable Drawable drawable) {
        App.post(() -> {
            if (!TextUtils.equals(mArtworkRequestOwner, owner)) return;
            updateAudioArtworkColor(key, drawable);
        }, 180);
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

private void checkAudioPlayImg(boolean isPlaying) {
        if (mBinding == null) return;
        mBinding.audioPlay.setImageResource(isPlaying ? androidx.media3.ui.R.drawable.exo_icon_pause : androidx.media3.ui.R.drawable.exo_icon_play);
        updateAudioLightEffectAnimation(isPlaying);
        syncAudioCoverRotation();
    }

private void updateAudioLightEffectAnimation(boolean animated) {
        if (!mAudioStageVisible || !PlayerSetting.isAudioBackgroundLightEffect() || mAudioLightEffectAnimated == animated) return;
        mAudioLightEffectAnimated = animated;
        Drawable background = mBinding.audioStage.getBackground();
        if (background instanceof AudioPlayerBackgroundDrawable drawable) drawable.setAnimated(animated);
        else scheduleAudioBackground(96);
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

private void syncKaraokePosition() {
        if (service() == null || player().isEmpty()) return;
        long position = Math.max(0, player().getPosition() + PlayerSetting.getLyricsTimeOffsetMs());
        boolean playing = player().isPlaying();
        mBinding.karaoke.syncPosition(position, playing);
        mBinding.audioKaraoke.syncPosition(position, playing);
    }

private String getAudioStageTitle() {
        Episode episode = getEpisode();
        String queuedTitle = mAudioQueueTitles.get(audioQueueEpisodeKey(episode));
        if (!TextUtils.isEmpty(queuedTitle)) return queuedTitle;
        if (isAudioQueueEpisode(episode) && !TextUtils.isEmpty(episode.getDisplayName())) return episode.getDisplayName();
        if (mHistory != null && !TextUtils.isEmpty(mHistory.getVodName())) return mHistory.getVodName();
        if (!TextUtils.isEmpty(getName())) return getName();
        CharSequence text = mBinding.name.getText();
        return text == null ? "" : text.toString();
    }

private String getAudioStageArtist(String title) {
        Episode item = getEpisode();
        String queuedArtist = mAudioQueueArtists.get(audioQueueEpisodeKey(item));
        if (!TextUtils.isEmpty(queuedArtist)) return queuedArtist;
        String episode = item == null ? "" : item.getName();
        String artist = getArtistFromEpisode(title, cleanAudioEpisodeForArtist(episode));
        return TextUtils.equals(artist, title) ? "" : artist;
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
        for (Episode episode : flag.getEpisodes()) if (TextUtils.equals(audioQueueEpisodeKey(episode), key)) return episode;
        return getEpisode();
    }

private String getEpisodeInlineLyrics(Episode episode) {
        if (isAudioQueueEpisode(episode)) return Objects.toString(mAudioQueueLyrics.get(audioQueueEpisodeKey(episode)), "");
        return mDetailLyrics;
    }

private void applyPlaybackArtwork(Episode episode) {
        loadArtwork(getEpisodeArtwork(episode), audioQueueEpisodeKey(episode));
    }

private String cleanAudioEpisodeForArtist(String episode) {
        String value = Objects.toString(episode, "").trim();
        if (value.isEmpty()) return "";
        String[] parts = value.split("[|｜]");
        return parts.length == 0 ? value : parts[parts.length - 1].trim();
    }

private void refreshKaraoke(boolean audioContent) {
        if (mKaraoke == null || service() == null) return;
        boolean loadTrack = !mSkipKaraokeTrackAutoLoad;
        mKaraoke.refresh(this, player(), audioContent, loadTrack);
    }

private boolean isLyricsSearchAvailable() {
        if (mLyrics == null || service() == null) return false;
        setAudioOnly(LyricsController.isAudioOnly(player()));
        return shouldUseImmersiveAudio();
    }

private String getLyricsSearchKeyword() {
        if (service() == null) return getName();
        LyricsRequest request = LyricsRequest.from(player());
        return request.displayKeyword();
    }

private List<String> getLyricsSearchSuggestions() {
        if (service() == null) return withLastLyricsSearchSuggestion(LyricsRequest.searchSuggestions(getName()), getLyricsSearchSuggestionSignature(null, getName()));
        LyricsRequest request = LyricsRequest.from(player());
        return withLastLyricsSearchSuggestion(request.searchSuggestions(), getLyricsSearchSuggestionSignature(request, getName()));
    }

private String getLyricsSearchCacheKey(String keyword) {
        if (service() == null) return keyword;
        return LyricsRequest.from(player()).withKeyword(keyword).signature();
    }

private void rememberLyricsSearchKeyword(String keyword) {
        rememberLyricsSearchKeyword(keyword, getLyricsSearchSignature());
    }

private void rememberLyricsSearchKeyword(String keyword, String signature) {
        String value = Objects.toString(keyword, "").trim();
        if (TextUtils.isEmpty(value)) return;
        mLyricsLastSearchSignature = signature;
        mLyricsLastSearchKeyword = value;
    }

private String getLyricsSearchSignature() {
        if (service() == null) return getLyricsSearchSuggestionSignature(null, getName());
        return getLyricsSearchSuggestionSignature(LyricsRequest.from(player()), getName());
    }

private String getLyricsSearchSuggestionSignature(@Nullable LyricsRequest request, String fallback) {
        if (request == null || TextUtils.isEmpty(request.getTitle())) return Objects.toString(fallback, "");
        return "lyrics-ui|" + request.getTitle() + "|" + request.getArtist() + "|" + request.getDurationSec();
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
        searchLyrics(keyword, null, false);
    }

private void searchLyrics(String keyword, @Nullable LyricsRequest baseRequest, boolean automatic) {
        if (mLyrics == null || service() == null) return;
        setAudioOnly(LyricsController.isAudioOnly(player()));
        int seq = ++mLyricsSearchSeq;
        LyricsRequest request = createLyricsSearchRequest(baseRequest, keyword);
        String cacheKey = request == null ? Objects.toString(keyword, "") : request.signature();
        rememberLyricsSearchKeyword(keyword, getLyricsSearchSuggestionSignature(request, keyword));
        if (TextUtils.equals(mLyricsSearchKeyword, cacheKey) && mLyricsSearchResults != null && !mLyricsSearchResults.isEmpty()) {
            showLyricsResults(seq, cacheKey, mLyricsSearchResults, true);
            return;
        }
        boolean audioContent = shouldUseImmersiveAudio();
        if (request == null || !audioContent) {
            showLyricsResults(seq, cacheKey, List.of(), true);
            return;
        }
        if (isLyricsSearchSheetShowing()) {
            if (!automatic) clearLyricsInlineResults();
            setLyricsSearchStatus(getString(R.string.player_lyrics_searching), true);
        } else {
            showLyricsSearching(seq);
        }
        mLyrics.search(request, (results, complete) -> showLyricsResults(seq, cacheKey, results, complete));
    }

@Nullable
    private LyricsRequest createLyricsSearchRequest(@Nullable LyricsRequest baseRequest, String keyword) {
        try {
            LyricsRequest request = baseRequest != null ? baseRequest : LyricsRequest.from(player());
            return request == null ? null : request.withKeyword(keyword);
        } catch (Throwable e) {
            SpiderDebug.log("lyrics-ui", "search request failed keyword=%s error=%s", keyword, e.getMessage());
            return null;
        }
    }

private boolean isLyricsSearchSheetShowing() {
        return mLyricsSearchDialog != null && mLyricsSearchDialog.isShowing();
    }

private void setLyricsSearchStatus(String text, boolean visible) {
        if (mLyricsSearchStatus == null) return;
        mLyricsSearchStatus.setText(Objects.toString(text, ""));
        mLyricsSearchStatus.setVisibility(visible && !TextUtils.isEmpty(text) ? View.VISIBLE : View.GONE);
    }

private void clearLyricsInlineResults() {
        mLyricsSearchResults = null;
        mLyricsSearchKeyword = "";
        updateLyricsResultList(new String[0]);
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
                if (isLyricsSearchSheetShowing()) {
                    clearLyricsInlineResults();
                    setLyricsSearchStatus(getString(R.string.player_lyrics_not_found), true);
                } else {
                    dismissLyricsResultDialog();
                    Notify.show(R.string.player_lyrics_not_found);
                }
            }
            return;
        }
        mLyricsSearchResults = results;
        mLyricsSearchKeyword = cacheKey;
        if (isLyricsSearchSheetShowing()) setLyricsSearchStatus(getString(R.string.player_lyrics_select), true);
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
        root.addView(scroll, lyricsResultSheetParams(labels.length));
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
        if (mKaraoke != null) mKaraoke.clear();
    }

private void dismissLyricsResultDialog() {
        if (mLyricsResultDialog == null) return;
        if (mLyricsSearchDialog == mLyricsResultDialog) mLyricsSearchDialog = null;
        mLyricsResultDialog.dismiss();
        mLyricsResultDialog = null;
        mLyricsResultList = null;
        mLyricsSearchStatus = null;
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
        view.setBackground(audioSheetControlBackground(0x12FFFFFF, 0x24FFFFFF));
        setAudioSheetFocusable(view);
        view.setOnClickListener(v -> {
            dialog.dismiss();
            action.run();
        });
        return view;
    }

private View createKaraokeModeHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        boolean enabled = PlayerSetting.isKaraokeMode();
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.HORIZONTAL);
        text.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = createAudioSheetText(getString(R.string.player_karaoke_mode), 15, true);
        TextView status = createAudioSheetText(getString(enabled ? R.string.player_karaoke_mode_enabled : R.string.player_karaoke_mode_disabled), 13, false);
        title.setTextColor(Color.WHITE);
        status.setTextColor(enabled ? SHEET_TEXT_SECONDARY : SHEET_TEXT_MUTED);
        text.addView(title);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        statusParams.leftMargin = ResUtil.dp2px(10);
        text.addView(status, statusParams);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        FrameLayout toggle = createKaraokeModeToggle(enabled);
        row.setFocusable(true);
        row.setBackground(audioSheetItemBackground(false));
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
        return createLyricsStepControl(getString(R.string.player_lyrics_offset), formatLyricsOffset(PlayerSetting.getLyricsTimeOffsetMs()), "-0.5s", "0", "+0.5s",
                PlayerSetting::putLyricsTimeOffsetMs, PlayerSetting::getLyricsTimeOffsetMs,
                LYRICS_OFFSET_MIN_MS, LYRICS_OFFSET_MAX_MS, LYRICS_OFFSET_STEP_MS, this::applyLyricsRuntimeSettings);
    }

private View createKaraokeDelayControl() {
        return createLyricsStepControl(getString(R.string.player_karaoke_mic_delay), getKaraokeDelayText(), "-0.1s", "0", "+0.1s",
                PlayerSetting::putKaraokeMicDelayMs, PlayerSetting::getKaraokeMicDelayMs,
                KARAOKE_DELAY_MIN_MS, KARAOKE_DELAY_MAX_MS, KARAOKE_DELAY_STEP_MS, this::reloadKaraokeTrack);
    }

private View createLyricsStepControl(String label, String valueText, String minus, String reset, String plus, LyricsLongSetter setter, LyricsLongGetter getter, long min, long max, long step, Runnable afterChange) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ResUtil.dp2px(12), 0, ResUtil.dp2px(10), 0);
        row.setBackground(roundRect(0x12FFFFFF, SHEET_BUTTON_RADIUS_DP, 1, 0x22FFFFFF));
        TextView title = createAudioSheetText(label, 15, false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView value = createAudioSheetText(valueText, 13, true);
        value.setTextColor(SHEET_TEXT_SECONDARY);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout buttons = new LinearLayout(this);
        buttons.addView(createLyricsStepButton(minus, () -> applyLyricsLongSetting(setter, getter, min, max, -step, value, afterChange)), lyricsStepButtonParams(false));
        buttons.addView(createLyricsStepButton(reset, () -> applyLyricsLongSetting(setter, () -> 0L, min, max, 0, value, afterChange)), lyricsStepButtonParams(true));
        buttons.addView(createLyricsStepButton(plus, () -> applyLyricsLongSetting(setter, getter, min, max, step, value, afterChange)), lyricsStepButtonParams(true));
        row.addView(buttons);
        return row;
    }

private void applyLyricsLongSetting(LyricsLongSetter setter, LyricsLongGetter getter, long min, long max, long delta, TextView value, Runnable afterChange) {
        setter.set(Math.min(Math.max(getter.get() + delta, min), max));
        value.setText(formatLyricsOffset(getter.get()));
        if (afterChange != null) afterChange.run();
    }

private TextView createLyricsStepButton(String label, Runnable action) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(audioSheetControlBackground(0x16FFFFFF, 0x28FFFFFF));
        setAudioSheetFocusable(view);
        view.setOnClickListener(v -> action.run());
        return view;
    }

private LinearLayout.LayoutParams lyricsStepButtonParams(boolean margin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(52), ResUtil.dp2px(34));
        if (margin) params.leftMargin = ResUtil.dp2px(6);
        return params;
    }

private TextView createLyricsChoiceItem(String label, boolean selected, Runnable action) {
        TextView item = createAudioSheetText(label, 15, selected);
        item.setGravity(Gravity.CENTER);
        item.setSingleLine(true);
        item.setTextColor(selected ? SHEET_TEXT_PRIMARY : SHEET_TEXT_SECONDARY);
        item.setBackground(lyricsResultItemBackground(selected));
        setAudioSheetFocusable(item);
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

private String getKaraokeDelayText() {
        return formatLyricsOffset(PlayerSetting.getKaraokeMicDelayMs());
    }

private String formatLyricsOffset(long valueMs) {
        if (valueMs == 0) return "0s";
        return String.format(Locale.getDefault(), "%+.1fs", valueMs / 1000f);
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

private TextView createKaraokeActionButton(BottomSheetDialog dialog, String label, Runnable action, boolean compact, boolean dismissOnClick) {
        TextView view = createAudioSheetText(label, compact ? 14 : 15, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(ResUtil.dp2px(10), 0, ResUtil.dp2px(10), 0);
        view.setTextColor(0xF2FFFFFF);
        view.setBackground(audioSheetControlBackground(0x14FFFFFF, 0x22FFFFFF));
        setAudioSheetFocusable(view);
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

private BottomSheetDialog createAudioSheet() {
        return new BottomSheetDialog(this);
    }

private Dialog createAudioQueueDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
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
        int height = audioDrawerHeight();
        root.setMinimumHeight(height);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
        root.setBackground(audioDrawerBackground());
    }

private void styleAudioQueueDrawerRoot(LinearLayout root) {
        root.setPadding(ResUtil.dp2px(22), ResUtil.dp2px(10), ResUtil.dp2px(22), ResUtil.dp2px(14));
        int height = audioQueueDrawerHeight();
        root.setMinimumHeight(height);
        root.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
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
        setAudioSheetFocusable(view);
        view.setOnClickListener(v -> action.run());
        return view;
    }

private TextView createAudioSheetButton(String label, boolean primary, Runnable action) {
        TextView view = createAudioSheetText(label, 15, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setTextColor(SHEET_TEXT_PRIMARY);
        view.setBackground(audioSheetControlBackground(primary ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG, primary ? SHEET_CONTROL_STROKE_SELECTED : 0x32FFFFFF));
        setAudioSheetFocusable(view);
        view.setOnClickListener(v -> action.run());
        return view;
    }

private TextView createAudioSheetMiniButton(String label, boolean primary, Runnable action) {
        TextView view = createAudioSheetText(label, 13, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setTextColor(SHEET_TEXT_PRIMARY);
        view.setBackground(audioSheetControlBackground(primary ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG, primary ? SHEET_CONTROL_STROKE_SELECTED : SHEET_CONTROL_STROKE));
        setAudioSheetFocusable(view);
        view.setOnClickListener(v -> action.run());
        return view;
    }

private ImageView createAudioSheetIconButton(int resId, Runnable action) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setColorFilter(SHEET_TEXT_SECONDARY);
        view.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(12), ResUtil.dp2px(12), ResUtil.dp2px(12));
        view.setBackground(audioSheetControlBackground(0x16FFFFFF, 0x32FFFFFF));
        setAudioSheetFocusable(view);
        view.setOnClickListener(v -> action.run());
        return view;
    }

private ImageView createAudioSheetInlineIconButton(int resId, Runnable action) {
        ImageView view = new ImageView(this);
        view.setImageResource(resId);
        view.setColorFilter(SHEET_TEXT_SECONDARY);
        view.setPadding(ResUtil.dp2px(9), ResUtil.dp2px(9), ResUtil.dp2px(9), ResUtil.dp2px(9));
        view.setBackground(audioSheetControlBackground(0x10FFFFFF, 0x22FFFFFF));
        setAudioSheetFocusable(view);
        view.setOnClickListener(v -> action.run());
        return view;
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
            item.setBackground(audioSheetSegmentBackground(selected));
            setAudioSheetFocusable(item);
            item.setOnClickListener(v -> handler.onClick(index));
            row.addView(item, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        }
        return row;
    }

private void setAudioSheetFocusable(View view) {
        view.setFocusable(true);
        view.setFocusableInTouchMode(false);
    }

private LinearLayout.LayoutParams audioSheetButtonParams(boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(108), ViewGroup.LayoutParams.MATCH_PARENT);
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(10);
        return params;
    }

private LinearLayout.LayoutParams audioSheetMiniButtonParams(int widthDp, boolean withStartMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ResUtil.dp2px(widthDp), ResUtil.dp2px(32));
        if (withStartMargin) params.leftMargin = ResUtil.dp2px(6);
        return params;
    }

private Drawable audioSheetItemBackground(boolean selected) {
        return audioSheetSelectableBackground(selected ? SHEET_CONTROL_BG_SELECTED : 0x00000000, selected ? SHEET_CONTROL_STROKE_SELECTED : 0, SHEET_CONTROL_BG_SELECTED, SHEET_CONTROL_STROKE_SELECTED, SHEET_BUTTON_RADIUS_DP);
    }

private Drawable audioSheetControlBackground(int normalColor, int normalStroke) {
        return audioSheetSelectableBackground(normalColor, normalStroke, 0x3DFFFFFF, 0x80FFFFFF, SHEET_BUTTON_RADIUS_DP);
    }

private Drawable audioSheetSegmentBackground(boolean selected) {
        return audioSheetSelectableBackground(selected ? SHEET_CONTROL_BG_SELECTED : 0x00000000, 0, selected ? SHEET_CONTROL_BG_SELECTED : 0x2AFFFFFF, selected ? SHEET_CONTROL_STROKE_SELECTED : 0x66FFFFFF, SHEET_SEGMENT_RADIUS_DP);
    }

private Drawable audioSheetSelectableBackground(int normalColor, int normalStroke, int focusedColor, int focusedStroke, int radiusDp) {
        android.graphics.drawable.StateListDrawable drawable = new android.graphics.drawable.StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_pressed}, roundRect(focusedColor, radiusDp, 1, focusedStroke));
        drawable.addState(new int[]{android.R.attr.state_focused}, roundRect(focusedColor, radiusDp, 1, focusedStroke));
        drawable.addState(new int[]{android.R.attr.state_selected}, roundRect(focusedColor, radiusDp, 1, focusedStroke));
        drawable.addState(new int[]{}, roundRect(normalColor, radiusDp, normalStroke == 0 ? 0 : 1, normalStroke));
        return drawable;
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
        if (isLandscapeAudioSheet()) return audioQueueDrawerListMaxHeight();
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

private LinearLayout.LayoutParams lyricsResultSheetParams(int count) {
        if (isLandscapeAudioSheet()) return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lyricsResultSheetHeight(count));
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
            hideSystemBarsForAudioSheet(dialog);
        });
        dialog.show();
        applyAudioSheetWindowGlass(dialog);
        hideSystemBarsForAudioSheet(dialog);
        focusAudioSheetContent(dialog);
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

private void showLyricsSearchSheetDialog(BottomSheetDialog dialog) {
        if (!ResUtil.isLand(this)) {
            showCompactPlaybackSheet(dialog);
            return;
        }
        dialog.setOnShowListener(d -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundColor(Color.TRANSPARENT);
            int height = audioDrawerHeight();
            ViewGroup.LayoutParams params = sheet.getLayoutParams();
            params.height = height;
            sheet.setLayoutParams(params);
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setFitToContents(false);
            behavior.setExpandedOffset(Math.max(0, ResUtil.getScreenHeight(this) - height));
            behavior.setPeekHeight(height);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            behavior.setDraggable(false);
            hideSystemBarsForAudioSheet(dialog);
        });
        dialog.show();
        applyAudioSheetWindowGlass(dialog);
        hideSystemBarsForAudioSheet(dialog);
        focusAudioSheetContent(dialog);
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
            hideSystemBarsForAudioSheet(dialog);
        });
        dialog.show();
        applyAudioSheetWindowGlass(dialog);
        hideSystemBarsForAudioSheet(dialog);
        focusAudioSheetContent(dialog);
    }

private void showAudioQueueDrawerDialog(Dialog dialog) {
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        int height = audioQueueDrawerHeight();
        int top = Math.max(0, audioQueueDrawerScreenHeight() - height - audioDrawerBottomMargin());
        window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = audioDrawerWidth();
        params.height = height;
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = ResUtil.dp2px(16);
        params.y = top;
        params.dimAmount = 0f;
        params.windowAnimations = 0;
        window.setAttributes(params);
        window.setLayout(audioDrawerWidth(), height);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        hideSystemBarsForAudioDialog(dialog);
        focusAudioQueueSelectedItem();
    }

private void focusAudioSheetContent(BottomSheetDialog dialog) {
        if (dialog == null) return;
        Window window = dialog.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        decor.post(() -> focusFirstChild(decor));
        decor.postDelayed(() -> focusFirstChild(decor), 160);
        decor.postDelayed(() -> focusFirstChild(decor), 360);
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

private void hideSystemBarsForAudioSheet(BottomSheetDialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            Util.hideSystemUI(window);
            window.getDecorView().post(() -> Util.hideSystemUI(window));
        }
        Util.hideSystemUI(this);
        mBinding.getRoot().post(() -> Util.hideSystemUI(this));
    }

private void hideSystemBarsForAudioDialog(Dialog dialog) {
        Window window = dialog.getWindow();
        if (window != null) {
            Util.hideSystemUI(window);
            window.getDecorView().post(() -> Util.hideSystemUI(window));
        }
        Util.hideSystemUI(this);
        mBinding.getRoot().post(() -> Util.hideSystemUI(this));
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

private int audioQueueDrawerHeight() {
        int screenHeight = audioQueueDrawerScreenHeight();
        int topMargin = mStatusBarInset + ResUtil.dp2px(16);
        int bottomMargin = audioDrawerBottomMargin();
        int max = Math.max(ResUtil.dp2px(320), screenHeight - topMargin - bottomMargin);
        return clamp(Math.round(screenHeight * 0.84f), ResUtil.dp2px(320), max);
    }

private int audioQueueDrawerScreenHeight() {
        int height = ResUtil.getScreenHeight(this);
        if (mBinding != null && mBinding.getRoot().getHeight() > 0) height = Math.max(height, mBinding.getRoot().getHeight());
        android.view.Display.Mode mode = getWindowManager().getDefaultDisplay().getMode();
        int modeHeight = ResUtil.isLand(this) ? Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight()) : Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight());
        return Math.max(height, modeHeight);
    }

private int audioDrawerBottomMargin() {
        return ResUtil.dp2px(16) + mEpisodeBottomInset;
    }

private int audioDrawerListMaxHeight() {
        return Math.max(ResUtil.dp2px(126), audioDrawerHeight() - ResUtil.dp2px(88));
    }

private int audioQueueDrawerListMaxHeight() {
        return Math.max(ResUtil.dp2px(126), audioQueueDrawerHeight() - ResUtil.dp2px(88));
    }

private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
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

private void showLyricsSettingsSheet(BottomSheetDialog dialog) {
        showCompactPlaybackSheet(dialog, false, true);
    }

private void updateLyricsResultList(String[] labels) {
        if (mLyricsResultList == null) return;
        mLyricsResultList.removeAllViews();
        if (mLyricsResultList.getParent() instanceof View scroll) scroll.setVisibility(labels.length == 0 ? View.GONE : View.VISIBLE);
        int selected = getLyricsSelectedIndex();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView item = createLyricsResultItem(labels[i], i == selected, () -> {
                if (mLyricsSearchResults != null && index >= 0 && index < mLyricsSearchResults.size()) applyLyrics(mLyricsSearchResults.get(index));
            });
            mLyricsResultList.addView(item, lyricsResultItemParams(i == 0));
        }
    }

private void updateLyricsResultSheetHeight(int count) {
        if (mLyricsResultList == null) return;
        if (!(mLyricsResultList.getParent() instanceof View scroll)) return;
        if (mLyricsSearchDialog == mLyricsResultDialog) return;
        ViewGroup.LayoutParams params = scroll.getLayoutParams();
        int height = isLandscapeAudioSheet() ? 0 : lyricsResultSheetHeight(count);
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
        setAudioSheetFocusable(item);
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

private Drawable lyricsResultItemBackground(boolean selected) {
        return audioSheetSelectableBackground(selected ? SHEET_CONTROL_BG_SELECTED : SHEET_CONTROL_BG_SUBTLE, selected ? SHEET_CONTROL_STROKE_SELECTED : SHEET_CONTROL_STROKE, SHEET_CONTROL_BG_SELECTED, SHEET_CONTROL_STROKE_SELECTED, SHEET_BUTTON_RADIUS_DP);
    }

private boolean showInlineLyrics() {
        if (TextUtils.isEmpty(mInlineLyrics) || !LyricsController.hasTimedLyrics(mInlineLyrics)) return false;
        Episode episode = getPlaybackEpisode();
        String title = getAudioStageTitle();
        String artist = getAudioStageArtist(title);
        String signature = getHistoryKey() + "|" + audioQueueEpisodeKey(episode);
        return mLyrics.setInlineLyrics(signature, title, artist, mInlineLyrics, player().getDuration(), player().getPosition());
    }

private boolean isMusicLike() {
        String flag = mFlagAdapter == null || mFlagAdapter.getItemCount() == 0 ? "" : getFlag().getShow();
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

private long resolveInitialPlaybackPosition() {
        if (mHistory == null) return C.TIME_UNSET;
        if (mHistory.isNearEnding()) {
            SpiderDebug.log("video-flow", "reset near-end history position=%d duration=%d key=%s", mHistory.getPosition(), mHistory.getDuration(), getHistoryKey());
            mHistory.resetPlaybackPosition();
            syncHistory();
        }
        long position = Math.max(mHistory.getOpening(), mHistory.getPosition());
        return position > 0 ? position : C.TIME_UNSET;
    }

private boolean hasNextEpisode() {
        Episode item = mHistory.isRevPlay() ? mEpisodeAdapter.getPrev() : mEpisodeAdapter.getNext();
        return !item.isSelected();
    }

public void onEpisodeColumn(int column) {
        PlayerSetting.putEpisodeColumn(column);
        refreshEpisodeTitles();
    }

public void onCompactEpisodeTitleChanged() {
        refreshEpisodeTitles();
    }

public void onParse(Parse item) {
        onItemClick(item);
    }

public void onLutPanel() {
        onLut();
    }

public void onTrackPanel(int type) {
        onTrack(type);
    }

public void onTitlePanel() {
        onTitle();
    }

public void onDanmakuPanel() {
        onDanmaku();
    }

@Override
public void onDisplayChanged() {
        if (mOsd == null) return;
        mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
        setPlayParamsState();
        mOsd.start();
    }

public void onKaraokeModeChanged() {
        mBinding.control.action.karaoke.setSelected(false);
        mBinding.audioKaraokeAction.setSelected(false);
        updateAudioStageText();
        if (PlayerSetting.isKaraokeMode()) {
            mKaraokeResultShown = false;
            refreshLyrics();
        } else if (mKaraoke != null) {
            mKaraoke.clear();
        }
    }

@Override
    public void onImmersiveAudioModeChanged() {
        boolean enabled = PlayerSetting.isImmersiveAudioMode();
        mImmersiveAudioRequested = enabled;
        PlayerSetting.putImmersiveAudioPlayback(enabled ? getHistoryKey() : "");
        if (enabled) {
            ensureImmersiveAudioControllers();
            refreshLyrics();
        } else {
            setAudioStageVisible(false);
            if (service() != null && player().haveTrack(C.TRACK_TYPE_VIDEO)) player().restoreVideoTrack();
        }
        updateImmersiveAudioAction();
    }

private boolean dispatchAudioStageKey(KeyEvent event) {
        if (!isAudioStageNavigationKey(event)) return false;
        View focus = getCurrentFocus();
        if (KeyUtil.isActionDown(event) && focus != null && isChildOf(mBinding.audioStage, focus)) showAudioStageFocusHighlight(focus);
        if (KeyUtil.isEnterKey(event)) {
            if (focus != null && isChildOf(mBinding.audioStage, focus) && focus.isEnabled() && focus.isClickable()) {
                if (KeyUtil.isActionUp(event)) focus.performClick();
                return true;
            }
            return false;
        }
        if (dispatchAudioSeekKey(focus, event)) return true;
        if (!KeyUtil.isActionDown(event)) return true;
        if (focus == null || !isChildOf(mBinding.audioStage, focus) || focus == mBinding.audioStage || focus == mBinding.video) return focusAudioStageDefault();
        moveAudioStageFocus(focus, event);
        return true;
    }

private boolean dispatchAudioSeekKey(View focus, KeyEvent event) {
        if (focus == null || !isChildOf(mBinding.audioSeek, focus)) return false;
        if (!KeyUtil.isLeftKey(event) && !KeyUtil.isRightKey(event)) return false;
        if (KeyUtil.isActionUp(event)) {
            finishAudioSeekPreview();
            return true;
        }
        if (!KeyUtil.isActionDown(event)) return true;
        previewAudioSeek(KeyUtil.isRightKey(event) ? 1 : -1, event.getRepeatCount());
        return true;
    }

private void previewAudioSeek(int direction, int repeatCount) {
        if (service() == null || player().isEmpty()) return;
        if (mAudioSeekPreviewDirection != direction) {
            mAudioSeekPreviewOffset = 0;
            mAudioSeekPreviewRepeat = 0;
            mAudioSeekPreviewDirection = direction;
        }
        mAudioSeekPreviewing = true;
        mAudioSeekPreviewRepeat = Math.max(mAudioSeekPreviewRepeat + 1, repeatCount + 1);
        long current = player().getPosition();
        long duration = Math.max(0, player().getDuration());
        long next = current + mAudioSeekPreviewOffset + direction * getAudioSeekStep(mAudioSeekPreviewRepeat);
        long target = duration > 0 ? Math.min(Math.max(0, next), duration) : Math.max(0, next);
        mAudioSeekPreviewOffset = target - current;
        mBinding.audioSeek.previewSeekPosition(target);
        onSeeking(mAudioSeekPreviewOffset);
    }

private long getAudioSeekStep(int repeat) {
        if (repeat <= 2) return AUDIO_SEEK_STEP_FINE_MS;
        if (repeat <= 6) return AUDIO_SEEK_STEP_NORMAL_MS;
        if (repeat <= 12) return AUDIO_SEEK_STEP_FAST_MS;
        return AUDIO_SEEK_STEP_MAX_MS;
    }

private void finishAudioSeekPreview() {
        if (!mAudioSeekPreviewing) return;
        long offset = mAudioSeekPreviewOffset;
        long current = service() == null || player().isEmpty() ? 0 : player().getPosition();
        long duration = service() == null || player().isEmpty() ? 0 : Math.max(0, player().getDuration());
        long target = duration > 0 ? Math.min(Math.max(0, current + offset), duration) : Math.max(0, current + offset);
        mAudioSeekPreviewOffset = 0;
        mAudioSeekPreviewDirection = 0;
        mAudioSeekPreviewRepeat = 0;
        mAudioSeekPreviewing = false;
        if (offset != 0) onSeekEnd(offset);
        mBinding.audioSeek.commitSeekPreview(target);
    }

private boolean isAudioStageNavigationKey(KeyEvent event) {
        return KeyUtil.isEnterKey(event) || KeyUtil.isUpKey(event) || KeyUtil.isDownKey(event) || KeyUtil.isLeftKey(event) || KeyUtil.isRightKey(event);
    }

private boolean focusAudioStageDefault() {
        if (mBinding == null || !mAudioStageVisible) return false;
        if (mBinding.audioPlay.isEnabled() && mBinding.audioPlay.requestFocus()) {
            showAudioStageFocusHighlight(mBinding.audioPlay);
            return true;
        }
        return focusFirstChild(mBinding.audioStage);
    }

private boolean moveAudioStageFocus(View focus, KeyEvent event) {
        List<View> focusables = new ArrayList<>();
        collectAudioStageFocusables(mBinding.audioStage, focusables);
        View target = findAudioStageFocusTarget(focus, focusables, event);
        if (target == null || !target.requestFocus()) return false;
        showAudioStageFocusHighlight(target);
        return true;
    }

private void collectAudioStageFocusables(View view, List<View> focusables) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collectAudioStageFocusables(group.getChildAt(i), focusables);
            return;
        }
        if (view.isFocusable()) focusables.add(view);
    }

private View findAudioStageFocusTarget(View focus, List<View> focusables, KeyEvent event) {
        Rect current = new Rect();
        if (focus == null || !focus.getGlobalVisibleRect(current)) return null;
        View target = null;
        long bestScore = Long.MAX_VALUE;
        for (View item : focusables) {
            if (item == focus) continue;
            Rect candidate = new Rect();
            if (!item.getGlobalVisibleRect(candidate) || !isLutQuickFocusCandidate(current, candidate, event)) continue;
            long score = scoreLutQuickFocusCandidate(current, candidate, event);
            if (score < bestScore) {
                bestScore = score;
                target = item;
            }
        }
        return target;
    }

public void onCasted() {
        clearLyrics();
        clearKaraokeState();
        player().stop();
    }

public void onShare(CharSequence title) {
        PlayerHelper.share(this, player().getUrl(), player().getHeaders(), title);
        setRedirect(true);
    }

private void finishVideoPlaybackNow() {
        mViewModel.stopSearch();
        markPlaybackExiting();
        saveHistory(true);
        stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        super.onBackInvoked();
    }

}
