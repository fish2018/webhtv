package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
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
import androidx.palette.graphics.Palette;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.DanmakuApi;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.CastVideo;
import com.fongmi.android.tv.bean.Danmaku;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Parse;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityVideoBinding;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.model.SearchProgress;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
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
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.DanmakuSetting;
import com.fongmi.android.tv.setting.LyricsSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.adapter.ArrayAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.PartAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.custom.CustomKeyDownVod;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.AudioPlayerBackgroundDrawable;
import com.fongmi.android.tv.ui.custom.KaraokeResultView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.dialog.CodecCapabilityDialog;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.ContentDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.PanNetworkDiagnosticDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TimerDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.bassaer.library.MDColor;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class VideoActivity extends PlaybackActivity implements CustomKeyDownVod.Listener, TrackDialog.Listener, ControlDialog.Listener, ArrayAdapter.OnClickListener, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, Clock.Callback {

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

    private ActivityVideoBinding mBinding;
    private ViewGroup.LayoutParams mFrameParams;
    private Observer<Result> mObserveDetail;
    private Observer<Result> mObservePlayer;
    private Observer<Result> mObserveSearch;
    private Observer<SearchProgress> mObserveSearchProgress;
    private EpisodeAdapter mEpisodeAdapter;
    private QualityAdapter mQualityAdapter;
    private ArrayAdapter mArrayAdapter;
    private ParseAdapter mParseAdapter;
    private QuickAdapter mQuickAdapter;
    private FlagAdapter mFlagAdapter;
    private PartAdapter mPartAdapter;
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
    private Map<String, View> mActionButtons;
    private final List<View> mCustomActionViews = new ArrayList<>();
    private HorizontalScrollView mCustomPortraitButtons;
    private LinearLayout mCustomPortraitButtonRow;
    private QuickSearchDialog mQuickSearchDialog;
    private PlayerOsdController mOsd;
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
    private int mLyricsSearchSeq;
    private int mLyricsSearchSheetSeq;
    private int mLyricsRefreshSeq;
    private int mAudioQueueSearchSeq;
    private int mStatusBarInset;
    private int mEpisodeBottomInset;
    private boolean detailRequested;
    private boolean detailHealthRecorded;
    private boolean playHealthRecorded;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Runnable mAudioRefreshLyricsRunnable;
    private Runnable mApplyAudioBackgroundRunnable;
    private Runnable mHideAudioFocusRunnable;
    private Clock mClock;
    private View mFocus1;
    private View mFocus2;
    private View mDialogReturnFocus;
    private Result mPendingDetail;
    private Result mPendingPlayer;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;
    private long mInitialPlaybackPosition = C.TIME_UNSET;
    private boolean pendingLutImport;
    private boolean playerKernelSwitchRefreshing;

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

    public static void push(FragmentActivity activity, String text) {
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, false, true, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true, false);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, false, wallPic);
    }

    public static void start(Activity activity, String url) {
        start(activity, SiteApi.PUSH, url, url);
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
        start(activity, key, id, name, pic, mark, false, false, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast) {
        start(activity, key, id, name, pic, mark, collect, cast, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, cast, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, boolean cast, String wallPic, String content) {
        long launch = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "launch request key=%s id=%s name=%s collect=%s cast=%s", key, id, name, collect, cast);
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        Intent intent = new Intent(activity, VideoActivity.class);
        intent.putExtra("launchTime", launch);
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

    private boolean isCast() {
        return getIntent().getBooleanExtra("cast", false);
    }

    private String getName() {
        return Objects.toString(getIntent().getStringExtra("name"), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra("pic"), "");
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
        return mFlagAdapter.getActivated();
    }

    private Episode getEpisode() {
        return mEpisodeAdapter.getActivated();
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

    private String getOsdTitle() {
        String name = getName();
        if (mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0) return name;
        String episode = Objects.toString(getEpisode().getName(), "");
        if (TextUtils.isEmpty(episode) || TextUtils.equals(name, episode)) return name;
        return TextUtils.isEmpty(name) ? episode : name + " " + episode;
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
        setPlayerKernel();
        setDecode();
        setLut();
        if (!detailRequested) checkId();
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
    protected void onControllerReady(Player controller) {
        mBinding.audioSeek.setPlayer(controller);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        saveHistory();
        getIntent().putExtras(intent);
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        long start = System.currentTimeMillis();
        SpiderDebug.log("video-flow", "initView start sinceLaunch=%dms key=%s id=%s", getLaunchCost(start), getKey(), getId());
        if (!isCast() && hasInitialPreview()) showInitialPreview();
        super.initView(savedInstanceState);
        SpiderDebug.log("video-flow", "initView after playback cost=%dms", System.currentTimeMillis() - start);
        disableLeanbackDesktopLyrics();
        mFrameParams = mBinding.video.getLayoutParams();
        setupAudioStageOverlay();
        mClock = Clock.create(mBinding.widget.clock);
        if (PlayerSetting.isImmersiveAudioMode()) ensureImmersiveAudioControllers();
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
        mAudioRefreshLyricsRunnable = this::refreshLyricsNow;
        mApplyAudioBackgroundRunnable = this::applyAudioBackground;
        mHideAudioFocusRunnable = this::hideAudioStageFocusHighlight;
        SpiderDebug.log("video-flow", "initView state ready cost=%dms", System.currentTimeMillis() - start);
        if (shouldUseImmersiveAudio()) showInitialAudioStage();
        checkCast();
        SpiderDebug.log("video-flow", "initView preview ready cost=%dms", System.currentTimeMillis() - start);
        setRecyclerView();
        setShortDisplay();
        mOsd = new PlayerOsdController(mBinding.osd.getRoot(), mBinding.osd.osdTopLeft, mBinding.osd.osdTopRight, mBinding.osd.osdBottomLeft, mBinding.osd.osdBottomRight, mBinding.osd.osdDiagnostics, mBinding.osd.osdMiniProgress, new PlayerOsdController.Source() {
            @Override
            public PlayerManager getPlayer() {
                return service() == null ? null : player();
            }

            @Override
            public String getTitle() {
                return getOsdTitle();
            }
        }, 14f);
        SpiderDebug.log("video-flow", "initView recycler ready cost=%dms", System.currentTimeMillis() - start);
        setVideoView();
        SpiderDebug.log("video-flow", "initView video view ready cost=%dms", System.currentTimeMillis() - start);
        setViewModel();
        checkId();
        SpiderDebug.log("video-flow", "initView end cost=%dms sinceLaunch=%dms", System.currentTimeMillis() - start, getLaunchCost(System.currentTimeMillis()));
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
        ViewGroup parent = (ViewGroup) mBinding.audioStage.getParent();
        if (parent != null) parent.removeView(mBinding.audioStage);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        ((ViewGroup) mBinding.getRoot()).addView(mBinding.audioStage, params);
        mBinding.audioStage.bringToFront();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.keep.setOnClickListener(view -> onKeep());
        mBinding.search.setOnClickListener(view -> onSearch());
        mBinding.video.setOnClickListener(view -> onVideo());
        mBinding.change1.setOnClickListener(view -> onChange());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
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
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.lut.setOnClickListener(view -> onLut());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.title.setOnClickListener(view -> onTitle());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onChooseLong());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.playParams.setOnClickListener(view -> onPlayParams());
        mBinding.control.action.panDiagnostic.setOnClickListener(view -> onPanDiagnostic());
        mBinding.control.action.codecCapability.setOnClickListener(view -> onCodecCapability());
        mBinding.control.action.immersiveAudio.setOnClickListener(view -> toggleImmersiveAudioMode());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.change2.setOnClickListener(view -> onChange());
        mBinding.control.action.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.cast.setOnClickListener(view -> onCast());
        mBinding.control.action.timer.setOnClickListener(view -> onTimer());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.audioPlay.setOnClickListener(view -> checkPlay());
        mBinding.audioNext.setOnClickListener(view -> checkNext());
        mBinding.audioPrev.setOnClickListener(view -> checkPrev());
        mBinding.audioRepeatAction.setOnClickListener(view -> onRepeat());
        mBinding.audioQueueAction.setOnClickListener(view -> onAudioQueue());
        mBinding.audioLyricsAction.setOnClickListener(view -> onLyricsSearch());
        mBinding.audioKeepAction.setOnClickListener(view -> onKeep());
        mBinding.audioCastAction.setOnClickListener(view -> onCast());
        mBinding.audioSettingAction.setOnClickListener(view -> onSetting());
        mBinding.audioKaraokeAction.setOnClickListener(view -> onKaraokeMode());
        mBinding.audioBackgroundAction.setOnClickListener(view -> randomizeAudioBackgroundMix(false));
        mBinding.audioMoreAction.setOnClickListener(view -> onAudioMore());
        mBinding.audioTrackAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_AUDIO));
        mBinding.audioSubtitleAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_TEXT));
        mBinding.audioStage.setOnClickListener(view -> focusAudioStageDefault());
        mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay());
        setupAudioStageFocusFeedback();
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
        mBinding.array.addOnChildViewHolderSelectedListener(new OnChildViewHolderSelectedListener() {
            @Override
            public void onChildViewHolderSelected(@NonNull RecyclerView parent, @Nullable RecyclerView.ViewHolder child, int position, int subposition) {
                int count = mEpisodeAdapter.getItemCount();
                if (count > getEpisodeSegmentSize(count) && position > 1) scrollToEpisode(mArrayAdapter.getStart(position));
            }
        });
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
        int episodeColumn = getEpisodeColumn();
        mBinding.episode.setNumColumns(episodeColumn);
        mBinding.episode.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setVerticalSpacing(ResUtil.dp2px(8));
        mBinding.episode.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_LOW_EDGE);
        mBinding.episode.setWindowAlignmentPreferKeyLineOverLowEdge(false);
        mBinding.episode.setWindowAlignmentPreferKeyLineOverHighEdge(false);
        mBinding.episode.setWindowAlignmentOffset(0);
        mBinding.episode.setWindowAlignmentOffsetPercent(0);
        mBinding.episode.setItemAlignmentOffset(0);
        mBinding.episode.setItemAlignmentOffsetPercent(0);
        mBinding.episode.setAdapter(mEpisodeAdapter = new EpisodeAdapter(this));
        mEpisodeAdapter.setColumn(episodeColumn);
        mBinding.quality.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.quality.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.quality.setAdapter(mQualityAdapter = new QualityAdapter(this));
        mBinding.array.setHorizontalSpacing(ResUtil.dp2px(8));
        mBinding.array.setRowHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        mBinding.array.setAdapter(mArrayAdapter = new ArrayAdapter(this));
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
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(View.VISIBLE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        mBinding.control.action.karaoke.setVisibility(View.GONE);
        updateImmersiveAudioAction();
        updateAudioStageControls();
        setupActionButtons();
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);
        addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        addActionButton(PlayerButtonSetting.RESET, mBinding.control.action.reset);
        addActionButton(PlayerButtonSetting.CHANGE, mBinding.control.action.change2);
        addActionButton(PlayerButtonSetting.FULLSCREEN, mBinding.control.action.fullscreen);
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.PLAY_PARAMS, mBinding.control.action.playParams);
        addActionButton(PlayerButtonSetting.CODEC_CAPABILITY, mBinding.control.action.codecCapability);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.LUT, mBinding.control.action.lut);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.opening);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.ending);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmaku);
        addActionButton(PlayerButtonSetting.TITLE, mBinding.control.action.title);
        addActionButton(PlayerButtonSetting.REPEAT, mBinding.control.action.repeat);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
        setupCustomActionButtons();
        placePanDiagnosticAction();
        updatePanDiagnosticAction();
    }

    private void addActionButton(String id, View view) {
        mActionButtons.put(id, view);
    }

    private void setupCustomActionButtons() {
        ensureCustomButtonContainers();
        for (View view : mCustomActionViews) {
            ViewParent parent = view.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(view);
        }
        mCustomActionViews.clear();
        mCustomPortraitButtonRow.removeAllViews();
        List<MpvConfigStore.CustomButton> buttons = MpvConfigStore.customButtons();
        for (int index = 0; index < buttons.size(); index++) {
            MpvConfigStore.CustomButton button = buttons.get(index);
            if (!button.enabled) continue;
            TextView view = new TextView(this);
            view.setTextSize(13);
            view.setTextColor(Color.WHITE);
            view.setGravity(Gravity.CENTER);
            view.setMinHeight(ResUtil.dp2px(40));
            view.setMinWidth(ResUtil.dp2px(56));
            view.setPadding(ResUtil.dp2px(10), ResUtil.dp2px(4), ResUtil.dp2px(10), ResUtil.dp2px(4));
            view.setBackgroundResource(R.drawable.selector_control_sheet_button);
            view.setText(button.title);
            view.setSingleLine(true);
            view.setMaxWidth(ResUtil.dp2px(144));
            view.setEllipsize(TextUtils.TruncateAt.END);
            view.setContentDescription(button.title);
            view.setOnClickListener(item -> {
                if (player().sendMpvCustomButton(button.id, false)) {
                    toggleCustomButtonState(item);
                }
                setR1Callback();
            });
            view.setOnLongClickListener(item -> {
                if (player().sendMpvCustomButton(button.id, true)) {
                    toggleCustomButtonState(item);
                }
                setR1Callback();
                return true;
            });
            view.setFocusable(true);
            view.setFocusableInTouchMode(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(40));
            params.setMargins(ResUtil.dp2px(4), ResUtil.dp2px(2), ResUtil.dp2px(4), ResUtil.dp2px(2));
            mCustomPortraitButtonRow.addView(view, params);
            mCustomActionViews.add(view);
        }
        updateCustomButtonVisibility();
    }

    private void toggleCustomButtonState(View view) {
        view.setSelected(!view.isSelected());
    }

    private void ensureCustomButtonContainers() {
        if (mCustomPortraitButtons != null) return;
        mCustomPortraitButtons = new HorizontalScrollView(this);
        mCustomPortraitButtons.setHorizontalScrollBarEnabled(false);
        mCustomPortraitButtons.setFillViewport(false);
        mCustomPortraitButtons.setClipToPadding(false);
        mCustomPortraitButtons.setOverScrollMode(View.OVER_SCROLL_NEVER);
        mCustomPortraitButtonRow = new LinearLayout(this);
        mCustomPortraitButtonRow.setGravity(Gravity.CENTER_VERTICAL);
        mCustomPortraitButtonRow.setOrientation(LinearLayout.HORIZONTAL);
        mCustomPortraitButtonRow.setClipChildren(false);
        mCustomPortraitButtons.addView(mCustomPortraitButtonRow, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 0, ResUtil.dp2px(8));
        ((ViewGroup) mBinding.control.getRoot()).addView(mCustomPortraitButtons, 0, params);
    }

    private void updateCustomButtonVisibility() {
        boolean visible = service() != null && player().isMpv() && isVisible(mBinding.control.getRoot());
        if (mCustomPortraitButtons != null) mCustomPortraitButtons.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void applyActionButtonVisibility() {
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
        updateCustomButtonVisibility();
        mBinding.control.action.cast.setVisibility(isFullscreen() ? View.GONE : View.VISIBLE);
        updateImmersiveAudioAction();
        updatePanDiagnosticAction();
    }

    private void placePanDiagnosticAction() {
        ViewGroup container = mBinding.control.action.container;
        View diagnostic = mBinding.control.action.panDiagnostic;
        View anchor = mBinding.control.action.playParams;
        if (diagnostic.getParent() != container || anchor.getParent() != container) return;
        container.removeView(diagnostic);
        container.addView(diagnostic, Math.min(container.getChildCount(), container.indexOfChild(anchor) + 1));
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
        mBinding.control.action.immersiveAudio.setSelected(PlayerSetting.isImmersiveAudioMode());
    }

    private void toggleImmersiveAudioMode() {
        PlayerSetting.putImmersiveAudioMode(!PlayerSetting.isImmersiveAudioMode());
        onImmersiveAudioModeChanged();
    }

    private int getEpisodeColumn() {
        return mEpisodeAdapter == null ? 8 : mEpisodeAdapter.getColumn();
    }

    private void setDecode() {
        mBinding.control.action.decode.setText(player().getDecodeText());
    }

    private void setPlayerKernel() {
        mBinding.control.action.player.setText(player().getPlayerText());
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
        else getDetail();
    }

    private void getDetail() {
        detailStartTime = System.currentTimeMillis();
        detailHealthRecorded = false;
        SpiderDebug.log("video-flow", "detail start key=%s id=%s name=%s", getKey(), getId(), getName());
        mViewModel.detailContent(getKey(), getId());
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
        clearLyrics();
        updateNavigationKey();
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
        item.checkPic(getPic());
        item.checkName(getName());
        item.checkContent(getContent());
        mBinding.progressLayout.showContent();
        mBinding.name.setText(item.getName());
        mFlagAdapter.addAll(item.getFlags());
        mBinding.video.requestFocus();
        App.removeCallbacks(mR4);
        checkHistory(item);
        checkFlag(item);
        checkKeepImg();
        setText(item);
        updateKeep();
    }

    private void setText(Vod item) {
        mBinding.content.setTag(item.getContent());
        setDetailLyrics(item.getContent());
        setText(mBinding.year, R.string.detail_year, item.getYear());
        setText(mBinding.area, R.string.detail_area, item.getArea());
        setText(mBinding.type, R.string.detail_type, item.getTypeName());
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.remark, 0, item.getRemarks());
        updateAudioStageText();
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
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

    private void getPlayer(Flag flag, Episode episode) {
        mBinding.widget.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
        playerStartTime = System.currentTimeMillis();
        beginPlayHealth();
        String playFlag = getEpisodePlayFlag(flag, episode);
        String previousEpisodeKey = Objects.toString(mPlaybackEpisodeKey, "");
        mPlaybackEpisodeKey = audioQueueEpisodeKey(episode);
        mSkipKaraokeTrackAutoLoad = isMusicLike() && !TextUtils.isEmpty(previousEpisodeKey) && !TextUtils.equals(previousEpisodeKey, mPlaybackEpisodeKey);
        SpiderDebug.log("video-flow", "player start key=%s flag=%s episode=%s url=%s", getKey(), playFlag, episode.getName(), episode.getUrl());
        mInlineLyrics = getEpisodeInlineLyrics(episode);
        long step = System.currentTimeMillis();
        applyPlaybackArtwork(episode);
        SpiderDebug.log("video-flow", "player artwork cost=%dms", System.currentTimeMillis() - step);
        step = System.currentTimeMillis();
        clearLyrics();
        clearKaraokeState();
        SpiderDebug.log("video-flow", "player clear audio state cost=%dms", System.currentTimeMillis() - step);
        step = System.currentTimeMillis();
        if (shouldUseImmersiveAudio()) setAudioStageVisible(true);
        SpiderDebug.log("video-flow", "player audio stage cost=%dms visible=%s", System.currentTimeMillis() - step, isMusicLike());
        step = System.currentTimeMillis();
        mViewModel.playerContent(getKey(), playFlag, episode.getUrl());
        SpiderDebug.log("video-flow", "player content request dispatch cost=%dms", System.currentTimeMillis() - step);
        mBinding.widget.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void setPlayer(Result result) {
        if (isFinishing() || isDestroyed()) return;
        SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        if (service() == null) {
            mPendingPlayer = result;
            SpiderDebug.log("video-flow", "player pending service key=%s id=%s", getKey(), getId());
            return;
        }
        mQualityAdapter.addAll(result);
        setUseParse(result.shouldUseParse());
        setQualityVisible(result.getUrl().isMulti());
        result.getUrl().set(mQualityAdapter.getPosition());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        else applyPlaybackArtwork(getPlaybackEpisode());
        if (result.hasDesc()) {
            mBinding.content.setTag(result.getDesc());
            setPlaybackLyrics(result.getDesc());
        }
        applyAudioQueueMetadata(getPlaybackEpisode());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        List<Danmaku> siteDanmakus = result.getDanmaku();
        mInitialPlaybackPosition = resolveInitialPlaybackPosition();
        SpiderDebug.log("video-flow", "startPlayer dispatch initialPosition=%d music=%s ijk=%s", mInitialPlaybackPosition, isMusicLike(), service() != null && player().isIjk());
        long start = System.currentTimeMillis();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata(), mInitialPlaybackPosition);
        SpiderDebug.log("video-flow", "startPlayer return cost=%dms sincePlayerStart=%dms", System.currentTimeMillis() - start, System.currentTimeMillis() - playerStartTime);
        if (DanmakuApi.canAutoSearch(siteDanmakus)) DanmakuApi.search(mHistory.getVodName(), getEpisode().getName(), player()::setDanmaku);
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
        if (mFlagAdapter.getItemCount() == 0 || item.isSelected()) return;
        int oldPosition = mFlagAdapter.getSelectedPosition();
        mFlagAdapter.setSelected(item);
        int newPosition = mFlagAdapter.getSelectedPosition();
        if (newPosition != RecyclerView.NO_POSITION) mBinding.flag.setSelectedPosition(newPosition);
        notifyItemsChanged(mBinding.flag, mFlagAdapter, oldPosition, newPosition);
        setEpisodeAdapter(item.getEpisodes());
        setQualityVisible(false);
        seamless(item);
    }

    private void setEpisodeAdapter(List<Episode> items) {
        setEpisodeAdapter(items, true);
    }

    private void setEpisodeAdapter(List<Episode> items, boolean scrollToCurrent) {
        mBinding.control.action.episodes.setVisibility(items.size() < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        boolean audioList = isMusicLike();
        if (audioList) restoreAudioEpisodeDisplayNames(items);
        int column = audioList ? 1 : EpisodeAdapter.getColumn(items);
        mBinding.episode.setNumColumns(column);
        mEpisodeAdapter.setColumn(column);
        mEpisodeAdapter.addAll(items);
        setArrayAdapter(items.size());
        updateFocus();
        updateEpisodeWindow();
        if (scrollToCurrent) scrollToCurrentEpisode();
        setR2Callback();
    }

    private void restoreAudioEpisodeDisplayNames(List<Episode> items) {
        if (items == null) return;
        for (Episode item : items) {
            String title = mAudioQueueTitles.get(audioQueueEpisodeKey(item));
            item.setDisplayName(TextUtils.isEmpty(title) ? item.getRawDisplayName() : title);
        }
    }

    private void refreshEpisodeTitles() {
        if (mEpisodeAdapter == null || mFlagAdapter == null || mFlagAdapter.getItemCount() == 0) return;
        int position = mEpisodeAdapter.getSelectedPosition();
        setEpisodeAdapter(getFlag().getEpisodes(), false);
        if (position != RecyclerView.NO_POSITION) scrollToEpisode(position);
    }

    private void seamless(Flag flag) {
        Episode episode = getMark().isEmpty() ? flag.find(mHistory.getEpisode(), true) : flag.find(mHistory.getVodRemarks(), false);
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return;
        selectEpisode(episode, false);
    }

    @Override
    public void onItemClick(Episode item) {
        if (shouldEnterFullscreen(item)) return;
        selectEpisode(item, true);
    }

    private void selectEpisode(Episode item, boolean scrollToEpisode) {
        int oldPosition = mEpisodeAdapter.getSelectedPosition();
        mFlagAdapter.toggle(item);
        int newPosition = mEpisodeAdapter.indexOf(item);
        if (newPosition == RecyclerView.NO_POSITION) newPosition = mEpisodeAdapter.getSelectedPosition();
        mEpisodeAdapter.notifySelectionChanged(oldPosition, newPosition);
        SpiderDebug.log("video-episode", "select old=%s new=%s focus=%s scroll=%s name=%s", oldPosition, newPosition, mBinding.episode.hasFocus(), scrollToEpisode, item.getName());
        if (scrollToEpisode && !mBinding.episode.hasFocus()) scrollToEpisode(newPosition);
        if (isFullscreen()) Notify.show(getString(R.string.play_ready, item.getName()));
        onRefresh();
    }

    private void setQualityVisible(boolean visible) {
        mBinding.quality.setVisibility(visible ? View.VISIBLE : View.GONE);
        updateFocus();
        updateEpisodeWindow();
        setR2Callback();
    }

    @Override
    public void onItemClick(Result result) {
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
    }

    private void reverseEpisode(boolean scroll) {
        mFlagAdapter.reverse();
        setEpisodeAdapter(getFlag().getEpisodes(), scroll);
        if (scroll) scrollToCurrentEpisode();
        else scrollToFirstEpisode();
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
        mBinding.episode.post(() -> {
            updateEpisodeWindowNow();
            mBinding.episode.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                mBinding.episode.setSelectedPosition(position);
                if (requestFocus) mBinding.episode.requestFocus();
            });
        });
    }

    private void updateEpisodeWindow() {
        if (mEpisodeAdapter == null || mEpisodeAdapter.getItemCount() == 0) return;
        mBinding.episode.post(this::updateEpisodeWindowNow);
    }

    private void updateEpisodeWindowNow() {
        int height = getEpisodeWindowHeight();
        if (height <= 0) return;
        ViewGroup.LayoutParams params = mBinding.episode.getLayoutParams();
        if (params instanceof LinearLayoutCompat.LayoutParams layoutParams) {
            if (layoutParams.height == height && layoutParams.weight == 0) return;
            layoutParams.height = height;
            layoutParams.weight = 0;
            mBinding.episode.setLayoutParams(layoutParams);
        } else if (params.height != height) {
            params.height = height;
            mBinding.episode.setLayoutParams(params);
        }
    }

    private int getEpisodeWindowHeight() {
        int column = Math.max(1, mEpisodeAdapter.getColumn());
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
        int height = mBinding.scroll.getHeight();
        if (height <= 0) return 0;
        int available = height - mBinding.scroll.getPaddingTop() - mBinding.scroll.getPaddingBottom();
        ViewGroup.LayoutParams episodeParams = mBinding.episode.getLayoutParams();
        if (episodeParams instanceof ViewGroup.MarginLayoutParams margins) available -= margins.topMargin + margins.bottomMargin;
        for (int i = 0; i < mBinding.scroll.getChildCount(); i++) {
            View child = mBinding.scroll.getChildAt(i);
            if (child == mBinding.episode || child.getVisibility() == View.GONE) continue;
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
        return size <= 60 ? 20 : 40;
    }

    private int findFocusDown(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.array, R.id.episode, R.id.part, R.id.quick);
        for (int i = 0; i < orders.size(); i++) if (i > index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private int findFocusUp(int index) {
        List<Integer> orders = Arrays.asList(R.id.flag, R.id.quality, R.id.array, R.id.episode, R.id.part, R.id.quick);
        for (int i = orders.size() - 1; i >= 0; i--) if (i < index) if (isVisible(findViewById(orders.get(i)))) return orders.get(i);
        return 0;
    }

    private void updateFocus() {
        mArrayAdapter.setNextFocus(findFocusUp(2), findFocusDown(2));
        mEpisodeAdapter.setNextFocusUp(findFocusUp(3));
        mFlagAdapter.setNextFocusDown(findFocusDown(0));
        mEpisodeAdapter.setNextFocusDown(findFocusDown(3));
        mPartAdapter.setNextFocus(findFocusUp(4), findFocusDown(4));
        mQuickAdapter.setNextFocus(findFocusUp(5), findFocusDown(5));
        int searchDown = isVisible(mBinding.quick) ? R.id.quick : findFocusDown(-1);
        mBinding.search.setNextFocusDownId(searchDown == 0 ? View.NO_ID : searchDown);
    }

    private boolean onEpisodeKey(KeyEvent event) {
        if (!KeyUtil.isActionDown(event) || !KeyUtil.isUpKey(event)) return false;
        RecyclerView.ViewHolder holder = mBinding.episode.findContainingViewHolder(getCurrentFocus());
        if (holder == null) return false;
        int position = holder.getBindingAdapterPosition();
        int column = Math.max(1, mEpisodeAdapter.getColumn());
        if (position == RecyclerView.NO_POSITION || position >= column) return false;
        int target = findFocusUp(3);
        if (target == 0) return false;
        View view = findViewById(target);
        if (view == null || view.getVisibility() != View.VISIBLE) return false;
        view.requestFocus();
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
        getFocus1().requestFocus();
        mKeyDown.setFull(false);
        setFullscreen(false);
        mFocus2 = null;
        hideInfo();
    }

    private void onContent() {
        if (mBinding.content.getTag() == null) return;
        ContentDialog.create().content(mBinding.content.getTag().toString()).show(this);
    }

    private void onAudioLyricsSeek(long positionMs) {
        if (service() == null || player().isEmpty()) return;
        long duration = player().getDuration();
        long target = duration > 0 ? Math.min(Math.max(0, positionMs), Math.max(0, duration - 500)) : Math.max(0, positionMs);
        player().seekTo(target);
        if (mHistory != null) mHistory.setPosition(target);
        if (mLyrics != null) mLyrics.update(target);
    }

    private void onSearch() {
        if (onLyricsSearch()) return;
        String keyword = mBinding.name.getText().toString();
        if (TextUtils.isEmpty(keyword)) return;
        initSearch(keyword, false);
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

    private int getSelectedEpisodePosition(List<Episode> items) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).isSelected()) return i;
        return 0;
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

    private void onVideo() {
        if (mAudioStageVisible) {
            hideProgress();
            hideControl();
            hideInfo();
            focusAudioStageDefault();
            return;
        }
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
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).show(this);
    }

    private void onRepeat() {
        player().setRepeatOne(!player().isRepeatOne());
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
        setAudioRepeatSelected(player().isRepeatOne());
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

    private interface LyricsChoiceHandler {
        void onChoice(int which);
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
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
        setAudioRepeatSelected(player().isRepeatOne());
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        if (mHistory.isRevPlay()) onPrev(notify);
        else onNext(notify);
    }

    private void checkPrev() {
        if (mHistory.isRevPlay()) onNext(true);
        else onPrev(true);
    }

    private void onNext(boolean notify) {
        Episode item = mEpisodeAdapter.getNext();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_prev : R.string.error_play_next);
    }

    private void onPrev(boolean notify) {
        Episode item = mEpisodeAdapter.getPrev();
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(mHistory.isRevPlay() ? R.string.error_play_next : R.string.error_play_prev);
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        setScale(index == array.length - 1 ? 0 : ++index);
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

    public void onLutSelected(LutPreset preset) {
        if (!player().selectLut(preset, preset != null)) return;
        setLut();
        setR1Callback();
    }

    private void chooseLutFile() {
        FileChooser.from(mLutFile).show("*/*", new String[]{"application/octet-stream", "text/*", "image/*", "*/*"});
    }

    private void chooseLutDir() {
        FileChooser.from(mLutDir).showDirectory();
    }

    private void onSpeed() {
        mBinding.control.action.speed.setText(player().addSpeed());
        saveDefaultSpeed();
        setR1Callback();
    }

    private void onSpeedAdd() {
        mBinding.control.action.speed.setText(player().addSpeed(0.25f));
        saveDefaultSpeed();
        setR1Callback();
    }

    private void onSpeedSub() {
        mBinding.control.action.speed.setText(player().subSpeed(0.25f));
        saveDefaultSpeed();
        setR1Callback();
    }

    private boolean onSpeedLong() {
        mBinding.control.action.speed.setText(player().toggleSpeed());
        saveDefaultSpeed();
        setR1Callback();
        return true;
    }

    private void saveDefaultSpeed() {
        PlayerSetting.putDefaultSpeed(player().getSpeed());
        mHistory.setSpeed(player().getSpeed());
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
        player().stop();
        player().clear();
        mClock.setCallback(null);
        clearLyrics();
        clearKaraokeState();
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
        setOpening(0);
        return true;
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
        setEnding(0);
        return true;
    }

    private void onChoose() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.widget.title.getText());
        setRedirect(true);
    }

    private boolean onChooseLong() {
        onChoose();
        return true;
    }

    private void onPlayerKernel() {
        if (playerKernelSwitchRefreshing) return;
        PlayerKernelDialog.show(this, player().getPlayerType(), this::switchPlayerKernel);
    }

    private void switchPlayerKernel(int type) {
        if (refreshAndSwitchPlayerKernel(type)) return;
        mClock.setCallback(null);
        clearLyrics();
        player().switchPlayer(type);
        setPlayerKernel();
        setDecode();
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
        playerKernelSwitchRefreshing = true;
        mClock.setCallback(null);
        clearLyrics();
        SpiderDebug.log("video-flow", "switch player refresh start type=%d key=%s flag=%s episode=%s", nextType, key, flag, episode);
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episode, nextType);
                App.post(() -> switchPlayerKernelWithResult(nextType, result, position, speed, repeat, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    playerKernelSwitchRefreshing = false;
                    setPlayerKernel();
                    setDecode();
                    Notify.show(e.getMessage());
                });
            }
        });
        return true;
    }

    private void switchPlayerKernelWithResult(int type, Result result, long position, float speed, boolean repeat, MediaMetadata metadata) {
        playerKernelSwitchRefreshing = false;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            Notify.show(result != null && result.hasMsg() ? result.getMsg() : getString(R.string.error_play_url));
        } else {
            player().switchPlayer(type, result, getHistoryKey(), metadata, isUseParse(), position, speed, repeat);
        }
        setPlayerKernel();
        setDecode();
    }

    private void onDecode() {
        mClock.setCallback(null);
        clearLyrics();
        player().toggleDecode();
        setDecode();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
        hideControl();
    }

    private void onTrack(int type) {
        TrackDialog.create().type(type).player(player()).show(this);
        hideControl();
    }

    private void onTitle() {
        TitleDialog.create().player(player()).show(this);
        hideControl();
    }

    private void onDanmaku() {
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
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
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR3, 0);
        hideCenter();
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR3);
        Traffic.reset();
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
        if (mAudioStageVisible) {
            hideInfo();
            return;
        }
        showTopInfo();
        mBinding.widget.center.setVisibility(View.VISIBLE);
        mBinding.widget.duration.setText(player().getDurationTime());
        mBinding.widget.position.setText(player().getPositionTime(0));
    }

    private void showTopInfo() {
        if (mAudioStageVisible) {
            mBinding.widget.top.setVisibility(View.GONE);
            return;
        }
        mBinding.widget.top.setVisibility(View.VISIBLE);
        mBinding.widget.size.setText(player().getSizeText());
    }

    private void hideInfo() {
        mBinding.widget.top.setVisibility(View.GONE);
        mBinding.widget.center.setVisibility(View.GONE);
    }

    private void showControl(View view) {
        if (mAudioStageVisible) {
            hideControl();
            hideInfo();
            focusAudioStageDefault();
            return;
        }
        showTopInfo();
        setPlayParamsState();
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        updateCustomButtonVisibility();
        if (mOsd != null) mOsd.setControlsVisible(true);
        view.requestFocus();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        updateCustomButtonVisibility();
        if (mOsd != null) mOsd.setControlsVisible(false);
        if (player().isPlaying()) mBinding.widget.top.setVisibility(View.GONE);
        App.removeCallbacks(mR1);
    }

    private void hideCenter() {
        mBinding.widget.action.setImageResource(R.drawable.ic_widget_play);
        mBinding.widget.center.setVisibility(View.GONE);
        if (isGone(mBinding.control.getRoot())) mBinding.widget.top.setVisibility(View.GONE);
    }

    private void setTraffic() {
        Traffic.setSpeed(mBinding.progress.traffic);
        App.post(mR3, 1000);
    }

    private void setR1Callback() {
        App.post(mR1, Constant.INTERVAL_HIDE);
    }

    private void setR2Callback() {
        App.post(mR2, 500);
    }

    private void setArtwork(String url) {
        if (mHistory != null) mHistory.setVodPic(url);
        loadArtwork(url, mPlaybackEpisodeKey);
        setContextWall(getContextWall());
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
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
    }

    private void checkHistory(Vod item) {
        mHistory = History.find(getHistoryKey());
        mHistory = mHistory == null ? createHistory(item) : mHistory;
        if (!TextUtils.isEmpty(getWallPic())) mHistory.setWallPic(getWallPic());
        if (!TextUtils.isEmpty(getMark())) mHistory.setVodRemarks(getMark());
        if (Setting.isIncognito() && mHistory.getKey().equals(getHistoryKey())) mHistory.delete();
        mBinding.control.action.opening.setText(mHistory.getOpening() <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
        mBinding.control.action.ending.setText(mHistory.getEnding() <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getDefaultSpeed()));
        mHistory.setSpeed(player().getSpeed());
        mHistory.setVodName(item.getName());
        PlaybackEventCollector.get().updateHistory(mHistory);
        setArtwork(getInitialArtwork(item));
        setScale(getScale());
        setPartAdapter();
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

    private boolean hasInitialPreview() {
        return !getName().isEmpty() || !getPic().isEmpty() || !getWallPic().isEmpty();
    }

    private void showInitialPreview() {
        mBinding.progressLayout.showContent();
        mBinding.name.setText(getName());
        if (!getContent().isEmpty()) {
            mBinding.content.setTag(getContent());
            setDetailLyrics(getContent());
        }
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

    private void saveHistory() {
        saveHistory(false);
    }

    private void saveHistory(boolean exit) {
        if (mHistory == null || Setting.isIncognito()) return;
        if (service() != null && isOwner()) {
            updatePlaybackHistoryPosition();
            mHistory.setPlayerType(player().getPlayerType());
            mHistory.setCreateTime(System.currentTimeMillis());
        }
        if (exit && service() != null) PlaybackEventCollector.get().onStop(player());
        if (!mHistory.canSave()) return;
        History history = mHistory.copy();
        Task.execute(() -> {
            if (history.getDuration() > 0) history.merge().save();
            else history.save();
            if (exit) RefreshEvent.history();
        });
    }

    private void syncHistory() {
        if (mHistory == null || Setting.isIncognito()) return;
        History history = mHistory.copy();
        Task.execute(history::save);
    }

    private void updateHistory(Episode item) {
        boolean sameEpisode = item.matchesName(mHistory.getEpisode());
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), getFlag().getFlag());
        if ((!sameEpisode || !sameFlag) && service() != null) {
            updatePlaybackHistoryPosition();
            PlaybackEventCollector.get().onStop(player());
        }
        mHistory.setPosition(sameEpisode ? mHistory.getPosition() : C.TIME_UNSET);
        if (!sameEpisode) mHistory.setDuration(C.TIME_UNSET);
        mHistory.setVodFlag(getFlag().getFlag());
        mHistory.setVodRemarks(item.getName());
        mHistory.setEpisodeUrl(item.getUrl());
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    private void checkKeepImg() {
        boolean kept = Keep.find(getHistoryKey()) != null;
        mBinding.keep.setCompoundDrawablesWithIntrinsicBounds(kept ? R.drawable.ic_detail_keep_on : R.drawable.ic_detail_keep_off, 0, 0, 0);
        mBinding.audioKeepAction.setSelected(kept);
        mBinding.audioKeepAction.setCompoundDrawablesWithIntrinsicBounds(0, kept ? R.drawable.ic_detail_keep_on : R.drawable.ic_detail_keep_off, 0, 0);
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getHistoryKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(mHistory.getVodPic());
        keep.setVodName(mHistory.getVodName());
        keep.setSiteName(getSite().getName());
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
        boolean id = !item.getId().isEmpty();
        boolean pic = !item.getPic().isEmpty();
        boolean name = !item.getName().isEmpty();
        if (id) getIntent().putExtra("id", item.getId());
        if (id) mHistory.replace(getHistoryKey());
        if (name) mHistory.setVodName(item.getName());
        if (name) mBinding.name.setText(item.getName());
        if (name) mBinding.widget.title.setText(item.getName());
        if (name) updateAudioStageText();
        updateFlag(getFlag(), item.getFlags());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
        if (name) setPartAdapter();
        PlaybackEventCollector.get().updateHistory(mHistory);
        setText(item);
    }

    private void updateFlag(Flag activated, List<Flag> items) {
        items.forEach(item -> mFlagAdapter.getItems().stream()
                .filter(item::equals).findFirst().ifPresentOrElse(target -> {
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
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        long start = System.currentTimeMillis();
        setDecode();
        SpiderDebug.log("video-flow", "onPrepare decode cost=%dms sincePlayerStart=%dms", System.currentTimeMillis() - start, System.currentTimeMillis() - playerStartTime);
        long step = System.currentTimeMillis();
        setLut();
        SpiderDebug.log("video-flow", "onPrepare lut cost=%dms", System.currentTimeMillis() - step);
        step = System.currentTimeMillis();
        setPosition();
        SpiderDebug.log("video-flow", "onPrepare position cost=%dms", System.currentTimeMillis() - step);
        step = System.currentTimeMillis();
        refreshLyrics();
        SpiderDebug.log("video-flow", "onPrepare lyrics dispatch cost=%dms total=%dms", System.currentTimeMillis() - step, System.currentTimeMillis() - start);
    }

    @Override
    protected void onTracksChanged() {
        refreshLyrics();
        setTrackVisible();
        mClock.setCallback(this);
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
        if (mLyrics == null || service() == null) return;
        int seq = ++mLyricsRefreshSeq;
        setAudioOnly(LyricsController.isAudioOnly(player()));
        boolean audioContent = shouldUseImmersiveAudio();
        setAudioStageVisible(audioContent);
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
        if (mAudioStageVisible == visible) {
            syncAudioStageSurface(visible);
            updateAudioStageText();
            updateAudioStageControls();
            return;
        }
        mAudioStageVisible = visible;
        if (!visible) mAudioLightEffectAnimated = false;
        mBinding.audioStage.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (visible) {
            mBinding.audioStage.bringToFront();
            hideProgress();
            hideControl();
            hideInfo();
            Util.hideSystemUI(this);
        } else {
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
        return PlayerSetting.isImmersiveAudioMode() && (isAudioOnly() || isMusicLike());
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
        mBinding.name.setVisibility(value);
        mBinding.remark.setVisibility(visible && !TextUtils.isEmpty(mBinding.remark.getText()) ? View.VISIBLE : View.GONE);
        mBinding.row1.setVisibility(value);
        mBinding.director.setVisibility(visible && !TextUtils.isEmpty(mBinding.director.getText()) ? View.VISIBLE : View.GONE);
        mBinding.actor.setVisibility(visible && !TextUtils.isEmpty(mBinding.actor.getText()) ? View.VISIBLE : View.GONE);
        mBinding.row2.setVisibility(value);
        mBinding.scroll.setVisibility(value);
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

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
        syncHistory();
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

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
        syncHistory();
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

    private interface SegmentClickHandler {
        void onClick(int index);
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

    @Override
    protected void onTitlesChanged() {
        setTitleVisible();
    }

    @Override
    protected void onError(String msg) {
        recordPlayHealth(false, msg);
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
                showProgress();
                break;
            case Player.STATE_READY:
                mKaraokeResultShown = false;
                recordPlayHealth(true, "");
                hideProgress();
                refreshLyrics();
                player().reset();
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

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).player(player()).show(this);
        App.post(this::hideControl, 100);
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner()) return;
        long position, duration;
        mHistory.setCreateTime(time);
        updatePlaybackHistoryPosition();
        syncKaraokePosition();
        if (mLyrics != null) mLyrics.update(player());
        if (mKaraoke != null) mKaraoke.update(player(), mLyrics == null ? null : mLyrics.getLines());
        position = mHistory.getPosition();
        duration = mHistory.getDuration();
        PlaybackEventCollector.get().onProgress(mHistory, player());
        if (mHistory.canSave() && mHistory.canSync()) syncHistory();
        if (mHistory.getEnding() > 0 && duration > 0 && mHistory.getEnding() + position >= duration) {
            checkEnded(false);
        }
    }

    private void updatePlaybackHistoryPosition() {
        if (mHistory == null) return;
        long position = player().getPosition();
        long duration = player().getDuration();
        if (position > 0) mHistory.setPosition(position);
        if (duration > 0) mHistory.setDuration(duration);
        else if (mHistory.getDuration() < 0) mHistory.setDuration(0);
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onRefreshEvent(RefreshEvent event) {
        if (isRedirect()) return;
        if (event.getType() == RefreshEvent.Type.DETAIL) getDetail();
        else if (event.getType() == RefreshEvent.Type.PLAYER) onRefresh();
        else if (event.getType() == RefreshEvent.Type.VOD) updateVod(event.getVod());
        else if (event.getType() == RefreshEvent.Type.SUBTITLE) player().setSub(Sub.from(event.getPath()));
        else if (event.getType() == RefreshEvent.Type.DANMAKU) player().reloadDanmaku(Danmaku.from(event.getPath()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onConfigEvent(ConfigEvent event) {
        if (isRedirect() || !event.isVod() || mParseAdapter == null) return;
        mParseAdapter.addAll(VodConfig.get().getParses());
    }

    private void setPosition() {
        if (mHistory == null) return;
        long position = resolveInitialPlaybackPosition();
        if (position <= 0) return;
        if (mInitialPlaybackPosition == position) {
            SpiderDebug.log("video-flow", "skip duplicate restore seek position=%d key=%s", position, getHistoryKey());
            mInitialPlaybackPosition = C.TIME_UNSET;
            return;
        }
        long start = System.currentTimeMillis();
        player().seekTo(position);
        SpiderDebug.log("video-flow", "restore seek position=%d cost=%dms key=%s", position, System.currentTimeMillis() - start, getHistoryKey());
        mInitialPlaybackPosition = C.TIME_UNSET;
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

    private void checkEnded(boolean notify) {
        if (showKaraokeResultIfNeeded(() -> checkNext(notify))) return;
        checkNext(notify);
    }

    private boolean hasNextEpisode() {
        Episode item = mHistory.isRevPlay() ? mEpisodeAdapter.getPrev() : mEpisodeAdapter.getNext();
        return !item.isSelected();
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
        String title = getAudioStageTitle();
        String artist = getAudioStageArtist(title);
        return PlayerManager.buildMetadata(title, artist, getEpisodeArtwork(getEpisode()));
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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
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

    public void onScale(int tag) {
        setScale(tag);
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
    public boolean isControlAudioContent() {
        return isAudioOnly() || isMusicLike();
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
        if (PlayerSetting.isImmersiveAudioMode()) {
            ensureImmersiveAudioControllers();
            refreshLyrics();
        } else {
            setAudioStageVisible(false);
            if (service() != null && player().haveTrack(C.TRACK_TYPE_VIDEO)) player().restoreVideoTrack();
        }
        updateImmersiveAudioAction();
    }

    private View getFocus1() {
        return mFocus1 == null || mFocus1.getVisibility() != View.VISIBLE ? mBinding.video : mFocus1;
    }

    private View getFocus2() {
        return mFocus2 == null || mFocus2.getVisibility() != View.VISIBLE || mFocus2 == mBinding.control.action.opening || mFocus2 == mBinding.control.action.ending ? mBinding.control.action.next : mFocus2;
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
        if (isVisible(mBinding.lutQuick)) return dispatchLutQuickKey(event);
        if (isFullscreen() && KeyUtil.isMenuKey(event)) onToggle();
        if (isVisible(mBinding.control.getRoot())) setR1Callback();
        if (isVisible(mBinding.control.getRoot())) mFocus2 = getCurrentFocus();
        if (dispatchOpeningEndingAdjust(event)) return true;
        if (onEpisodeKey(event)) return true;
        if (mAudioStageVisible && isGone(mBinding.control.getRoot()) && dispatchAudioStageKey(event)) return true;
        if (isFullscreen() && isGone(mBinding.control.getRoot()) && mKeyDown.hasEvent(event) && service() != null) return mKeyDown.onKeyDown(event);
        if (KeyUtil.isMediaFastForward(event)) return onSeekForward();
        if (KeyUtil.isMediaRewind(event)) return onSeekBack();
        return super.dispatchKeyEvent(event);
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
        saveDefaultSpeed();
    }

    @Override
    public void onSpeedEnd() {
        mBinding.widget.speed.clearAnimation();
        mBinding.widget.speed.setVisibility(View.GONE);
        mBinding.control.action.speed.setText(player().getSpeedText());
        mHistory.setSpeed(player().getSpeed());
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
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
        hideControl();
    }

    @Override
    public void onSingleTap() {
        if (isFullscreen()) onToggle();
    }

    @Override
    public void onDoubleTap() {
        if (isFullscreen()) onKeyCenter();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && requestCode == 1001) PlayerHelper.onExternalResult(data, service()::dispatchNext, controller()::seekTo);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mClock.stop().start();
        if (mOsd != null) {
            mOsd.setDiagnosticsVisible(PlayerSetting.isOsdDiagnostics());
            setPlayParamsState();
            mOsd.start();
        }
        if (service() != null) refreshLyrics();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (mKaraoke != null) mKaraoke.clear();
        stopAudioCoverRotation();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
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
        if (showKaraokeResultIfNeeded(this::finishVideoPlaybackNow)) return;
        finishVideoPlaybackNow();
    }

    private void finishVideoPlaybackNow() {
        mViewModel.stopSearch();
        saveHistory(true);
        markPlaybackExiting();
        stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
        mLyricsSearchSeq++;
        mLyricsRefreshSeq++;
        dismissLyricsResultDialog();
        if (mLyrics != null) mLyrics.release();
        if (mKaraoke != null) mKaraoke.release();
        mClock.release();
        saveHistory(true);
        DanmakuApi.cancel();
        dismissQuickSearchDialog();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4, mAudioRefreshLyricsRunnable, mApplyAudioBackgroundRunnable, mHideAudioFocusRunnable);
        stopAudioCoverRotation();
        if (mOsd != null) mOsd.release();
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        mViewModel.getSearchProgress().removeObserver(mObserveSearchProgress);
        SiteHealthStore.flush();
        super.onDestroy();
    }
}
