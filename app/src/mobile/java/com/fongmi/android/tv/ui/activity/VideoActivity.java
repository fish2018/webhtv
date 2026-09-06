package com.fongmi.android.tv.ui.activity;

import android.annotation.SuppressLint;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.Gravity;
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
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.graphics.Insets;
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
import androidx.palette.graphics.Palette;
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
import com.fongmi.android.tv.event.CastEvent;
import com.fongmi.android.tv.event.ConfigEvent;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.impl.CustomTarget;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.playback.PlaybackEventCollector;
import com.fongmi.android.tv.playback.PlaybackOrientation;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.engine.PlaySpec;
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
import com.fongmi.android.tv.ui.adapter.EpisodeAdapter;
import com.fongmi.android.tv.ui.adapter.EpisodeGroupAdapter;
import com.fongmi.android.tv.ui.adapter.FlagAdapter;
import com.fongmi.android.tv.ui.adapter.ParseAdapter;
import com.fongmi.android.tv.ui.adapter.QualityAdapter;
import com.fongmi.android.tv.ui.adapter.QuickAdapter;
import com.fongmi.android.tv.ui.base.ViewType;
import com.fongmi.android.tv.ui.custom.AudioPlayerBackgroundDrawable;
import com.fongmi.android.tv.ui.custom.CustomKeyDown;
import com.fongmi.android.tv.ui.custom.CustomMovement;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.KaraokeResultView;
import com.fongmi.android.tv.ui.custom.PlayerOsdController;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.ui.dialog.CastDialog;
import com.fongmi.android.tv.ui.dialog.CodecCapabilityDialog;
import com.fongmi.android.tv.ui.dialog.ControlDialog;
import com.fongmi.android.tv.ui.dialog.DanmakuDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeGridDialog;
import com.fongmi.android.tv.ui.dialog.EpisodeListDialog;
import com.fongmi.android.tv.ui.dialog.InfoDialog;
import com.fongmi.android.tv.ui.dialog.LutPanelDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.QuickSearchDialog;
import com.fongmi.android.tv.ui.dialog.ReceiveDialog;
import com.fongmi.android.tv.ui.dialog.SubtitleDialog;
import com.fongmi.android.tv.ui.dialog.TitleDialog;
import com.fongmi.android.tv.ui.dialog.TrackDialog;
import com.fongmi.android.tv.ui.dialog.VideoContentDialog;
import com.fongmi.android.tv.utils.Clock;
import com.fongmi.android.tv.utils.EpisodeTitleCompact;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PiP;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Timer;
import com.fongmi.android.tv.utils.Traffic;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoActivity extends PlaybackActivity implements Clock.Callback, CustomKeyDown.Listener, TrackDialog.Listener, ControlDialog.Listener, DanmakuDialog.Host, FlagAdapter.OnClickListener, EpisodeAdapter.OnClickListener, EpisodeGroupAdapter.OnClickListener, QualityAdapter.OnClickListener, QuickAdapter.OnClickListener, ParseAdapter.OnClickListener, CastDialog.Listener, InfoDialog.Listener {

    private static final String SIZE_TAG = "MPV_SIZE";
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
    private int mAudioArtworkColor = Color.rgb(55, 45, 68);
    private final Map<String, String> mAudioQueueFlags = new HashMap<>();
    private final Map<String, String> mAudioQueueTitles = new HashMap<>();
    private final Map<String, String> mAudioQueueArtists = new HashMap<>();
    private final Map<String, String> mAudioQueuePics = new HashMap<>();
    private final Map<String, String> mAudioQueueLyrics = new HashMap<>();
    private Map<String, View> mActionButtons;
    private final List<View> mCustomActionViews = new ArrayList<>();
    private HorizontalScrollView mCustomLeftButtons;
    private HorizontalScrollView mCustomRightButtons;
    private HorizontalScrollView mCustomPortraitButtons;
    private LinearLayout mCustomLeftButtonRow;
    private LinearLayout mCustomRightButtonRow;
    private LinearLayout mCustomPortraitButtonRow;
    private SiteViewModel mViewModel;
    private FlagAdapter mFlagAdapter;
    private PlayerOsdController mOsd;
    private CustomKeyDown mKeyDown;
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
    private boolean playerKernelSwitchRefreshing;
    private boolean decodeSwitchRefreshing;
    private int deferredFullscreenOrientation = Configuration.ORIENTATION_UNDEFINED;
    private int mEpisodeSpanCount;
    private int mStatusBarInset;
    private int mEpisodeBottomInset;
    private int mNavigationRightInset;
    private int mLyricsSearchSeq;
    private int mAudioQueueSearchSeq;
    private int mAudioPlaylistCurrentIndex = -1;
    private int mEpisodeMaxHeight;
    private int mAudioBackgroundRandomNonce;
    private Runnable mR1;
    private Runnable mR2;
    private Runnable mR3;
    private Runnable mR4;
    private Clock mClock;
    private PiP mPiP;
    private String mContextWallUrl;
    private String mContextWallLockedUrl;
    private String playHealthKey;
    private long detailStartTime;
    private long playerStartTime;
    private boolean pendingLutImport;
    private boolean skipPausePiP;

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
        if (FileChooser.isValid(activity, Uri.parse(text))) file(activity, FileChooser.getPathFromUri(Uri.parse(text)));
        else start(activity, Sniffer.getUrl(text));
    }

    public static void file(FragmentActivity activity, String path) {
        if (TextUtils.isEmpty(path)) return;
        String name = new File(path).getName();
        start(activity, SiteApi.PUSH, "file://" + path, name);
    }

    public static void cast(Activity activity, History history) {
        start(activity, history.getSiteKey(), history.getVodId(), history.getVodName(), history.getVodPic(), null, history.getWallPic());
    }

    public static void collect(Activity activity, String key, String id, String name, String pic) {
        start(activity, key, id, name, pic, null, true);
    }

    public static void collect(Activity activity, String key, String id, String name, String pic, String wallPic) {
        start(activity, key, id, name, pic, null, true, wallPic);
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
        start(activity, key, id, name, pic, mark, false);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic) {
        start(activity, key, id, name, pic, mark, false, wallPic);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, String wallPic, String content) {
        start(activity, key, id, name, pic, mark, false, wallPic, content);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect) {
        start(activity, key, id, name, pic, mark, collect, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic) {
        start(activity, key, id, name, pic, mark, collect, wallPic, null);
    }

    public static void start(Activity activity, String key, String id, String name, String pic, String mark, boolean collect, String wallPic, String content) {
        ImgUtil.preload(activity, pic);
        if (Setting.isPlaybackArtworkWall() && !TextUtils.isEmpty(wallPic) && !TextUtils.equals(wallPic, pic)) ImgUtil.preload(activity, wallPic);
        Intent intent = new Intent(activity, VideoActivity.class);
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
        return mFlagAdapter == null || mFlagAdapter.isEmpty() ? null : mFlagAdapter.getActivated();
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
        if (mEpisodeAdapter == null || mEpisodeAdapter.isEmpty()) return name;
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
        syncDesktopLyricsAudioContent();
        setPlayerKernel();
        setDecode();
        setLut();
        applyDeferredFullscreenOrientation();
        checkLand();
        if (consumePendingPlaybackResult()) return;
        checkId();
    }

    @Override
    protected void onControllerReady(Player controller) {
        mBinding.audioSeek.setPlayer(controller);
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
        String oldId = getId();
        super.onNewIntent(intent);
        String id = Objects.toString(intent.getStringExtra("id"), "");
        if (TextUtils.isEmpty(id) || id.equals(oldId)) return;
        mBinding.swipeLayout.setRefreshing(true);
        saveHistory();
        getIntent().putExtras(intent);
        setOrient();
        checkId();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        super.initView(savedInstanceState);
        mRestoringConfigurationPlayback = savedInstanceState != null;
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.getRoot(), (v, insets) -> setStatusBar(insets));
        mKeyDown = CustomKeyDown.create(this, mBinding.exo);
        mFrameParams = mBinding.video.getLayoutParams();
        mFrameHeight = mFrameParams.height;
        mBinding.swipeLayout.setEnabled(false);
        setupAudioStageOverlay();
        configureAudioLandscapeActions();
        mObserveDetail = this::setDetail;
        mObservePlayer = this::setPlayer;
        mObserveSearch = this::setSearch;
        mBroken = new ArrayList<>();
        mClock = Clock.create();
        mBinding.audioLyrics.setAudioStageMode(true);
        mBinding.audioLyrics.setSeekListener(this::onAudioLyricsSeek);
        mBinding.audioLyrics.setSuppressed(true);
        if (PlayerSetting.isImmersiveAudioMode()) ensureImmersiveAudioControllers();
        mR1 = this::hideControl;
        mR2 = this::setTraffic;
        mR3 = this::setOrient;
        mR4 = this::showEmpty;
        mPiP = new PiP();
        checkDanmakuImg();
        setRecyclerView();
        mOsd = new PlayerOsdController(mBinding.osd.getRoot(), mBinding.osd.osdTopLeft, mBinding.osd.osdTopRight, mBinding.osd.osdBottomLeft, mBinding.osd.osdBottomRight, mBinding.osd.osdDiagnostics, mBinding.osd.osdMiniProgress, new PlayerOsdController.Source() {
            @Override
            public PlayerManager getPlayer() {
                return service() == null ? null : player();
            }

            @Override
            public String getTitle() {
                return getOsdTitle();
            }
        }, 12f);
        setVideoView();
        setViewModel();
        setShortDisplay();
        if (shouldUseImmersiveAudio()) {
            setAudioStageVisible(true);
            mBinding.progressLayout.showContent();
        } else if (hasInitialPreview()) {
            showInitialPreview();
        } else {
            mBinding.progressLayout.showProgress();
        }
        showProgress();
        restoreKaraokeResultDialog(savedInstanceState);
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

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void initEvent() {
        mBinding.name.setOnClickListener(view -> onName());
        mBinding.more.setOnClickListener(view -> onMore());
        mBinding.shortDisplay.setOnClickListener(view -> onShortDisplay());
        mBinding.search.setOnClickListener(view -> onSearch());
        mBinding.castAction.setOnClickListener(view -> onCast());
        mBinding.settingAction.setOnClickListener(view -> onSetting());
        mBinding.actor.setOnClickListener(view -> onActor());
        mBinding.content.setOnClickListener(view -> onContent());
        mBinding.reverse.setOnClickListener(view -> onReverse());
        mBinding.director.setOnClickListener(view -> onDirector());
        mBinding.name.setOnLongClickListener(view -> onChange());
        mBinding.content.setOnLongClickListener(view -> onCopy());
        mBinding.control.back.setOnClickListener(view -> onBack());
        mBinding.control.cast.setOnClickListener(view -> onCast());
        mBinding.control.info.setOnClickListener(view -> onInfo());
        mBinding.control.keep.setOnClickListener(view -> onKeep());
        mBinding.control.osdDiagnostics.setOnClickListener(view -> onOsdDiagnostics());
        mBinding.control.play.setOnClickListener(view -> checkPlay());
        mBinding.control.next.setOnClickListener(view -> checkNext());
        mBinding.control.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.setting.setOnClickListener(view -> onSetting());
        mBinding.control.title.setOnLongClickListener(view -> onChange());
        mBinding.control.right.lock.setOnClickListener(view -> onLock());
        mBinding.control.right.rotate.setOnClickListener(view -> onRotate());
        mBinding.control.fullscreen.setOnClickListener(view -> onFullscreen());
        mBinding.control.danmaku.setOnClickListener(view -> onDanmakuShow());
        mBinding.control.action.text.setOnClickListener(this::onTrack);
        mBinding.control.action.audio.setOnClickListener(this::onTrack);
        mBinding.control.action.video.setOnClickListener(this::onTrack);
        mBinding.control.action.scale.setOnClickListener(view -> onScale());
        mBinding.control.action.lut.setOnClickListener(view -> onLut());
        mBinding.control.action.karaoke.setOnClickListener(view -> onKaraokeMode());
        mBinding.control.action.speed.setOnClickListener(view -> onSpeed());
        mBinding.control.action.reset.setOnClickListener(view -> onReset());
        mBinding.control.action.title.setOnClickListener(view -> onTitle());
        mBinding.control.action.player.setOnClickListener(view -> onPlayerKernel());
        mBinding.control.action.player.setOnLongClickListener(view -> onChooseLong());
        mBinding.control.action.prev.setOnClickListener(view -> checkPrev());
        mBinding.control.action.next.setOnClickListener(view -> checkNext());
        mBinding.control.action.decode.setOnClickListener(view -> onDecode());
        mBinding.control.action.playParams.setOnClickListener(view -> onPlayParams());
        mBinding.control.action.ending.setOnClickListener(view -> onEnding());
        mBinding.control.action.repeat.setOnClickListener(view -> onRepeat());
        mBinding.control.action.opening.setOnClickListener(view -> onOpening());
        mBinding.control.action.danmaku.setOnClickListener(view -> onDanmaku());
        mBinding.control.action.episodes.setOnClickListener(view -> onEpisodes());
        mBinding.audioPlay.setOnClickListener(view -> checkPlay());
        mBinding.audioNext.setOnClickListener(view -> checkNext());
        mBinding.audioPrev.setOnClickListener(view -> checkPrev());
        mBinding.audioRepeatAction.setOnClickListener(view -> onRepeat());
        mBinding.audioLyricsAction.setOnClickListener(view -> onLyricsSearch());
        mBinding.audioQueueAction.setOnClickListener(view -> onAudioQueue());
        mBinding.audioCastAction.setOnClickListener(view -> onCast());
        mBinding.audioKeepAction.setOnClickListener(view -> onKeep());
        mBinding.audioSettingAction.setOnClickListener(view -> onSetting());
        mBinding.audioKaraokeAction.setOnClickListener(view -> onKaraokeMode());
        mBinding.audioBackgroundAction.setOnClickListener(view -> randomizeAudioBackgroundMix(false));
        mBinding.audioMoreAction.setOnClickListener(view -> onAudioMore());
        mBinding.audioTrackAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_AUDIO));
        mBinding.audioSubtitleAction.setOnClickListener(view -> onTrack(C.TRACK_TYPE_TEXT));
        mBinding.audioInfoAction.setOnClickListener(view -> onInfo());
        mBinding.control.action.text.setOnLongClickListener(view -> onTextLong());
        mBinding.control.action.speed.setOnLongClickListener(view -> onSpeedLong());
        mBinding.control.action.reset.setOnLongClickListener(view -> onResetToggle());
        mBinding.control.action.ending.setOnLongClickListener(view -> onEndingReset());
        mBinding.control.action.opening.setOnLongClickListener(view -> onOpeningReset());
        mBinding.video.setOnTouchListener((view, event) -> mKeyDown.onTouchEvent(event));
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
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        lp.height = mAudioStageVisible ? 0 : top;
        mBinding.statusBar.setLayoutParams(lp);
        setEpisodeBottomInset(bottom);
        return insets;
    }

    private void applyStatusBarSpacer() {
        if (mBinding == null) return;
        ViewGroup.LayoutParams lp = mBinding.statusBar.getLayoutParams();
        int height = mAudioStageVisible ? 0 : mStatusBarInset;
        if (lp.height == height) return;
        lp.height = height;
        mBinding.statusBar.setLayoutParams(lp);
    }

    private void setEpisodeBottomInset(int bottom) {
        mEpisodeBottomInset = bottom;
        int padding = ResUtil.dp2px(12);
        mBinding.episode.setPaddingRelative(mBinding.episode.getPaddingStart(), mBinding.episode.getPaddingTop(), mBinding.episode.getPaddingEnd(), padding);
        applyAudioStageInsets();
        mBinding.episode.post(this::updateEpisodeViewportHeight);
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

    private void updateEpisodeViewportHeight() {
        if (mBinding.episode.getVisibility() != View.VISIBLE || mBinding.getRoot().getHeight() <= 0) return;
        int[] root = new int[2];
        int[] episode = new int[2];
        mBinding.getRoot().getLocationOnScreen(root);
        mBinding.episode.getLocationOnScreen(episode);
        int available = root[1] + mBinding.getRoot().getHeight() - mEpisodeBottomInset - ResUtil.dp2px(8) - episode[1];
        if (available <= 0 || available == mEpisodeMaxHeight) return;
        mEpisodeMaxHeight = available;
        mBinding.episode.setMaxHeight(available);
        mBinding.episode.requestLayout();
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

    private void setRecyclerView() {
        mBinding.flag.setHasFixedSize(true);
        mBinding.flag.setItemAnimator(null);
        mBinding.flag.addItemDecoration(new SpaceItemDecoration(8));
        mBinding.flag.setAdapter(mFlagAdapter = new FlagAdapter(this));
        mBinding.quick.setAdapter(mQuickAdapter = new QuickAdapter(this));
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

    private int getEpisodeSpanCount() {
        return EpisodeGridLayoutPolicy.getMaxSpan(isLand(), ResUtil.isPad());
    }

    private void setVideoView() {
        mBinding.control.action.danmaku.setVisibility(View.VISIBLE);
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        setupActionButtons();
        mBinding.video.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            mPiP.update(this, view);
            updateCustomButtonLayout();
            Log.d(SIZE_TAG, "video layout new=" + (right - left) + "x" + (bottom - top)
                    + " old=" + (oldRight - oldLeft) + "x" + (oldBottom - oldTop)
                    + " fullscreen=" + isFullscreen()
                    + " land=" + isLand()
                    + " scale=" + getScale()
                    + " player=" + (service() == null ? "none" : player().getPlayerText()));
        });
    }

    private void setupActionButtons() {
        mActionButtons = new HashMap<>();
        addActionButton(PlayerButtonSetting.PLAYER, mBinding.control.action.player);
        addActionButton(PlayerButtonSetting.DECODE, mBinding.control.action.decode);
        addActionButton(PlayerButtonSetting.PLAY_PARAMS, mBinding.control.action.playParams);
        addActionButton(PlayerButtonSetting.SPEED, mBinding.control.action.speed);
        addActionButton(PlayerButtonSetting.SCALE, mBinding.control.action.scale);
        addActionButton(PlayerButtonSetting.LUT, mBinding.control.action.lut);
        addActionButton(PlayerButtonSetting.RESET, mBinding.control.action.reset);
        addActionButton(PlayerButtonSetting.REPEAT, mBinding.control.action.repeat);
        addActionButton(PlayerButtonSetting.TEXT, mBinding.control.action.text);
        addActionButton(PlayerButtonSetting.AUDIO, mBinding.control.action.audio);
        addActionButton(PlayerButtonSetting.VIDEO, mBinding.control.action.video);
        addActionButton(PlayerButtonSetting.OPENING, mBinding.control.action.opening);
        addActionButton(PlayerButtonSetting.ENDING, mBinding.control.action.ending);
        addActionButton(PlayerButtonSetting.DANMAKU, mBinding.control.action.danmaku);
        addActionButton(PlayerButtonSetting.TITLE, mBinding.control.action.title);
        addActionButton(PlayerButtonSetting.PREV, mBinding.control.action.prev);
        addActionButton(PlayerButtonSetting.NEXT, mBinding.control.action.next);
        addActionButton(PlayerButtonSetting.EPISODES, mBinding.control.action.episodes);
        PlayerButtonSetting.applyOrder(mBinding.control.action.container, mActionButtons);
        setupCustomActionButtons();
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
        mCustomLeftButtonRow.removeAllViews();
        mCustomRightButtonRow.removeAllViews();
        mCustomPortraitButtonRow.removeAllViews();
        List<MpvConfigStore.CustomButton> buttons = MpvConfigStore.customButtons();
        boolean landscape = isLand();
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
            if (Util.isLeanback()) {
                view.setFocusable(true);
                view.setFocusableInTouchMode(true);
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ResUtil.dp2px(40));
            params.setMargins(ResUtil.dp2px(4), ResUtil.dp2px(2), ResUtil.dp2px(4), ResUtil.dp2px(2));
            LinearLayout target = landscape
                    ? (index < 4 ? mCustomLeftButtonRow : mCustomRightButtonRow)
                    : mCustomPortraitButtonRow;
            target.addView(view, params);
            mCustomActionViews.add(view);
        }
        updateCustomButtonLayout();
        updateCustomButtonVisibility();
    }

    private void toggleCustomButtonState(View view) {
        view.setSelected(!view.isSelected());
    }

    private void ensureCustomButtonContainers() {
        if (mCustomLeftButtons != null) return;
        mCustomLeftButtons = createCustomButtonScroll();
        mCustomRightButtons = createCustomButtonScroll();
        mCustomPortraitButtons = createCustomButtonScroll();
        mCustomLeftButtonRow = createCustomButtonRow(mCustomLeftButtons);
        mCustomRightButtonRow = createCustomButtonRow(mCustomRightButtons);
        mCustomPortraitButtonRow = createCustomButtonRow(mCustomPortraitButtons);
        mCustomRightButtons.setFillViewport(true);
        mCustomRightButtonRow.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        mCustomRightButtonRow.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.START | Gravity.TOP);
        FrameLayout.LayoutParams rightParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.TOP);
        int margin = ResUtil.dp2px(8);
        leftParams.setMargins(margin, 0, margin, 0);
        rightParams.setMargins(margin, 0, margin, 0);
        mBinding.video.addView(mCustomLeftButtons, leftParams);
        mBinding.video.addView(mCustomRightButtons, rightParams);

        RelativeLayout.LayoutParams portraitParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        portraitParams.addRule(RelativeLayout.ABOVE, R.id.bottom);
        portraitParams.setMargins(margin, 0, margin, ResUtil.dp2px(2));
        ((ViewGroup) mBinding.control.getRoot()).addView(mCustomPortraitButtons, portraitParams);
    }

    private HorizontalScrollView createCustomButtonScroll() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        return scroll;
    }

    private LinearLayout createCustomButtonRow(HorizontalScrollView scroll) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setClipChildren(false);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void updateCustomButtonLayout() {
        if (mCustomLeftButtons == null || mBinding.video.getWidth() <= 0) return;
        boolean landscape = isLand();
        if (!landscape) return;

        int margin = ResUtil.dp2px(8);
        int width = Math.max(ResUtil.dp2px(96), mBinding.video.getWidth() / 2 - margin * 2);
        int height = Math.max(mCustomLeftButtons.getMeasuredHeight(), ResUtil.dp2px(40));
        int top = Math.max(margin, Math.round(mBinding.video.getHeight() * 0.65f - height / 2f));
        FrameLayout.LayoutParams leftParams = (FrameLayout.LayoutParams) mCustomLeftButtons.getLayoutParams();
        FrameLayout.LayoutParams rightParams = (FrameLayout.LayoutParams) mCustomRightButtons.getLayoutParams();
        leftParams.width = width;
        rightParams.width = width;
        leftParams.topMargin = top;
        rightParams.topMargin = top;
        mCustomLeftButtons.setLayoutParams(leftParams);
        mCustomRightButtons.setLayoutParams(rightParams);
    }

    private void updateCustomButtonVisibility() {
        boolean visible = service() != null && player().isMpv() && isVisible(mBinding.control.getRoot());
        if (mCustomLeftButtons != null) mCustomLeftButtons.setVisibility(visible && isLand() ? View.VISIBLE : View.GONE);
        if (mCustomRightButtons != null) mCustomRightButtons.setVisibility(visible && isLand() ? View.VISIBLE : View.GONE);
        if (mCustomPortraitButtons != null) mCustomPortraitButtons.setVisibility(visible && !isLand() ? View.VISIBLE : View.GONE);
        updateCustomButtonLayout();
    }

    private void applyActionButtonVisibility() {
        if (mActionButtons != null) PlayerButtonSetting.applyVisibility(mActionButtons);
        updateCustomButtonVisibility();
    }

    private void setVideoView(boolean isInPictureInPictureMode) {
        if (isInPictureInPictureMode) {
            mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        } else {
            applyAudioStageLayout(mAudioStageVisible);
            restoreContextWall();
        }
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
        else getDetail();
    }

    private void checkLand() {
        if (isPort() && ResUtil.isLand(this)) enterFullscreen();
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
        mBinding.swipeLayout.setRefreshing(true);
        mBinding.swipeLayout.setEnabled(false);
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
        mBinding.swipeLayout.setRefreshing(false);
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
        item.checkPic(getPic());
        item.checkName(getName());
        item.checkContent(getContent());
        mBinding.name.setText(item.getName());
        mFlagAdapter.addAll(item.getFlags());
        App.removeCallbacks(mR4);
        checkHistory(item);
        setAudioStageVisible(shouldUseImmersiveAudio());
        mBinding.progressLayout.showContent();
        checkFlag(item);
        checkKeepImg();
        setText(item);
        updateKeep();
    }

    private void setText(Vod item) {
        setText(mBinding.site, R.string.detail_site, getSite().getName());
        setText(mBinding.director, R.string.detail_director, item.getDirector());
        setText(mBinding.actor, R.string.detail_actor, item.getActor());
        setText(mBinding.content, 0, item.getContent());
        setDetailLyrics(item.getContent());
        setText(mBinding.remark, 0, item.getRemarks());
        setOther(mBinding.other, item);
        updateAudioStageText();
        if (mAudioStageVisible) applyAudioPageMode(true);
    }

    private void setText(TextView view, int resId, String text) {
        if (TextUtils.isEmpty(text) && !TextUtils.isEmpty(view.getText())) return;
        view.setText(Sniffer.buildClickable(resId > 0 ? getString(resId, text) : text, this::clickableSpan), TextView.BufferType.SPANNABLE);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        if (view == mBinding.content) setContentVisible();
        view.setLinkTextColor(Color.WHITE);
        CustomMovement.bind(view);
    }

    private void setContentVisible() {
        mBinding.contentLayout.setVisibility(mBinding.content.getVisibility());
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

    private void getPlayer(Flag flag, Episode episode) {
        mBinding.control.title.setText(getString(R.string.detail_title, mBinding.name.getText(), episode.getName()));
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
        mViewModel.playerContent(getKey(), playFlag, episode.getUrl());
        mBinding.control.title.setSelected(true);
        updateHistory(episode);
        showProgress();
    }

    private void setPlayer(Result result) {
        if (isFinishing() || isDestroyed()) return;
        if (service() == null) {
            mPendingPlayerResult = result;
            return;
        }
        SpiderDebug.log("video-flow", "player finish cost=%dms useParse=%s multi=%s msg=%s", System.currentTimeMillis() - playerStartTime, result.shouldUseParse(), result.getUrl().isMulti(), result.getMsg());
        mQualityAdapter.addAll(result);
        setUseParse(result.shouldUseParse());
        mBinding.swipeLayout.setRefreshing(false);
        setQualityVisible(result.getUrl().isMulti());
        result.getUrl().set(mQualityAdapter.getPosition());
        if (result.hasArtwork() && !shouldKeepPushArtwork()) setArtwork(result.getArtwork());
        else applyPlaybackArtwork(getPlaybackEpisode());
        if (result.hasPosition()) mHistory.setPosition(result.getPosition());
        if (result.hasDesc()) {
            setText(mBinding.content, 0, result.getDesc());
            setPlaybackLyrics(result.getDesc());
        }
        updateAudioStageText();
        mBinding.control.parse.setVisibility(isUseParse() ? View.VISIBLE : View.GONE);
        List<Danmaku> siteDanmakus = result.getDanmaku();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
        if (DanmakuApi.canAutoSearch(siteDanmakus)) DanmakuApi.search(mHistory.getVodName(), getEpisode().getName(), player()::setDanmaku);
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
        if (item.isSelected()) return;
        mFlagAdapter.setSelected(item);
        scrollToPosition(mBinding.flag, mFlagAdapter.getPosition());
        setEpisodeAdapter(item.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        seamless(item);
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
        onRefresh();
    }

    @Override
    public void onItemClick(EpisodeGroupAdapter.Group item) {
        mEpisodeGroupAdapter.setSelected(item);
        scrollEpisodeToPosition(item.start);
        scrollToPosition(mBinding.episodeGroup, mEpisodeGroupAdapter.getPosition());
    }

    @Override
    public void onItemClick(Result result) {
        beginPlayHealth();
        startPlayer(getHistoryKey(), result, isUseParse(), getSite().getTimeout(), buildMetadata());
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
        int size = items.size();
        mBinding.control.action.episodes.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.action.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        applyActionButtonVisibility();
        mBinding.control.next.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.control.prev.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.reverse.setVisibility(size < 2 ? View.GONE : View.VISIBLE);
        mBinding.episode.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.more.setVisibility(View.GONE);
        List<EpisodeGroupAdapter.Group> groups = EpisodeGroupAdapter.build(size, getSelectedEpisodePosition(items), mHistory != null && mHistory.isRevSort());
        mEpisodeGroupAdapter.addAll(groups);
        mBinding.episodeGroup.setVisibility(groups.size() > 1 ? View.VISIBLE : View.GONE);
        setEpisodeItems(items);
        mBinding.episode.post(this::updateEpisodeViewportHeight);
        if (mAudioStageVisible) applyAudioPageMode(true);
        updateAudioStageControls();
    }

    private void setEpisodeItems(List<Episode> items) {
        updateEpisodeSpan(items);
        mEpisodeAdapter.addAll(items);
        selectEpisodeGroupByPosition(mEpisodeAdapter.getPosition());
    }

    private void syncEpisodeGroupByScroll() {
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

    private void updateEpisodeSpan(List<Episode> items) {
        int span = getEpisodeSpan(items);
        if (span == mEpisodeSpanCount) return;
        mEpisodeSpanCount = span;
        mBinding.episode.setLayoutManager(new GridLayoutManager(this, mEpisodeSpanCount));
        if (mEpisodeDecoration != null) mBinding.episode.removeItemDecoration(mEpisodeDecoration);
        mBinding.episode.addItemDecoration(mEpisodeDecoration = new SpaceItemDecoration(mEpisodeSpanCount, 8));
    }

    private int getEpisodeSpan(List<Episode> items) {
        EpisodeTitleCompact.apply(items);
        if (items.size() == 1) return 1;
        int maxLen = 0;
        for (Episode item : items) maxLen = Math.max(maxLen, item.getDisplayName().length());
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
        Flag flag = getFlag();
        return flag == null ? mEpisodeAdapter.getItemCount() : flag.getEpisodes().size();
    }

    private void seamless(Flag flag) {
        Episode episode = getMark().isEmpty() ? flag.find(mHistory.getEpisode(), true) : flag.find(mHistory.getVodRemarks(), false);
        setQualityVisible(episode != null && episode.isSelected() && mQualityAdapter.getItemCount() > 1);
        if (episode == null || episode.isSelected()) return;
        mHistory.setVodRemarks(episode.getName());
        mHistory.setEpisodeUrl(episode.getUrl());
        onItemClick(episode);
    }

    private void setQualityVisible(boolean visible) {
        mBinding.qualityText.setVisibility(visible && !mAudioStageVisible ? View.VISIBLE : View.GONE);
        mBinding.quality.setVisibility(visible && !mAudioStageVisible ? View.VISIBLE : View.GONE);
    }

    private void reverseEpisode(boolean scroll) {
        Flag flag = getFlag();
        if (flag == null) return;
        mFlagAdapter.reverse();
        setEpisodeAdapter(flag.getEpisodes());
        if (scroll) scrollEpisodeToSelected();
    }

    private void onName() {
        String name = mBinding.name.getText().toString();
        Notify.show(getString(R.string.detail_search, name));
        showQuickSearch(name);
        initSearch(name, false);
    }

    private void onSearch() {
        if (onLyricsSearch()) return;
        onName();
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

    private void onShortDisplay() {
        Setting.putCompactEpisodeTitle(!Setting.isCompactEpisodeTitle());
        setShortDisplay();
        refreshEpisodeTitles();
    }

    private void setShortDisplay() {
        mBinding.shortDisplay.setSelected(Setting.isCompactEpisodeTitle());
    }

    private void onMore() {
        Flag flag = getFlag();
        if (flag == null) return;
        syncSelectedEpisode(flag);
        EpisodeGridDialog.create().reverse(mHistory.isRevSort()).episodes(flag.getEpisodes()).show(this);
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
                .items(mQuickAdapter.getItems());
        mQuickSearchDialog.show(this);
    }

    private void onQuickSearch(String keyword) {
        initSearch(keyword, false);
    }

    private void onReverse() {
        mHistory.setRevSort(!mHistory.isRevSort());
        reverseEpisode(false);
    }

    private boolean onChange() {
        checkSearch(true);
        return true;
    }

    private boolean onCopy() {
        Util.copy(mBinding.content.getText().toString());
        return true;
    }

    private void onBack() {
        if (isFullscreen()) exitFullscreen();
        else finishVideoPlayback();
    }

    private void finishVideoPlayback() {
        if (showKaraokeResultIfNeeded(KARAOKE_RESULT_ACTION_FINISH)) return;
        finishVideoPlaybackNow();
    }

    private void finishVideoPlaybackNow() {
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
    }

    private void checkPlay() {
        setR1Callback();
        debugPlaybackControl("checkPlay");
        if (player().isPlaying()) onPaused();
        else if (player().isEmpty()) onRefresh();
        else onPlay();
    }

    private void checkNext() {
        checkNext(true);
    }

    private void checkNext(boolean notify) {
        setR1Callback();
        Episode item = getAdjacentEpisode(1);
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "fallback next notify=%s selected=%s name=%s adapter=%d", notify, item.isSelected(), item.getName(), mEpisodeAdapter.getItemCount());
        if (!item.isSelected()) onItemClick(item);
        else if (notify) Notify.show(R.string.error_play_next);
    }

    private void checkPrev() {
        setR1Callback();
        Episode item = getAdjacentEpisode(-1);
        if (!item.isSelected()) onItemClick(item);
        else Notify.show(R.string.error_play_prev);
    }

    private Episode getAdjacentEpisode(int offset) {
        Flag flag = getFlag();
        List<Episode> items = mAudioStageVisible ? mEpisodeAdapter.getItems() : flag == null ? mEpisodeAdapter.getItems() : flag.getEpisodes();
        if (items.isEmpty()) return new Episode();
        int position = getSelectedEpisodePosition(items) + offset;
        position = Math.max(0, Math.min(position, items.size() - 1));
        return items.get(position);
    }

    private boolean hasAdjacentEpisode(int offset) {
        Flag flag = getFlag();
        List<Episode> items = mAudioStageVisible ? mEpisodeAdapter.getItems() : flag == null ? mEpisodeAdapter.getItems() : flag.getEpisodes();
        if (items.isEmpty()) return false;
        int position = getSelectedEpisodePosition(items) + offset;
        return position >= 0 && position < items.size();
    }

    private void onSetting() {
        setTrackVisible();
        ControlDialog.create().parent(mBinding).history(mHistory).parse(isUseParse()).player(player()).show(this);
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

    private String getAudioQueueSongTitle(String collection, String episode) {
        String title = Objects.toString(episode, "").trim();
        String parent = Objects.toString(collection, "").trim();
        if (title.isEmpty() || title.matches("\\d+")) return parent;
        for (String separator : new String[]{" - ", " – ", " — "}) {
            if (!parent.isEmpty() && title.startsWith(parent + separator)) return title.substring(parent.length() + separator.length()).trim();
        }
        return title;
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

    private void onLock() {
        setLock(!isLock());
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
        if (isFullscreen()) exitFullscreen();
        else enterFullscreen();
        showControl();
    }

    private void onTrack(View view) {
        TrackDialog.create().type(Integer.parseInt(view.getTag().toString())).player(player()).show(this);
        hideControl();
    }

    private void onTrack(int type) {
        TrackDialog.create().type(type).player(player()).show(this);
        hideControl();
    }

    @Override
    public void onTrackPanel(int type) {
        TrackDialog.create().type(type).player(player()).show(this);
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
        DanmakuDialog.create().player(player()).show(this);
        hideControl();
    }

    @Override
    public void onDanmakuPanel() {
        DanmakuDialog.create().player(player()).show(this);
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
        setAudioRepeatSelected(player().isRepeatOne());
    }

    @Override
    public void onRepeatModeChanged(int repeatMode) {
        mBinding.control.action.repeat.setSelected(player().isRepeatOne());
        setAudioRepeatSelected(player().isRepeatOne());
    }

    private void onScale() {
        int index = getScale();
        String[] array = ResUtil.getStringArray(R.array.select_scale);
        if (mKeyDown.getScale() != 1.0f) mKeyDown.resetScale();
        else setScale(index == array.length - 1 ? 0 : ++index);
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
        if (mFlagAdapter.isEmpty()) return;
        if (mEpisodeAdapter.isEmpty()) return;
        Flag flag = getFlag();
        if (flag != null) getPlayer(flag, getEpisode());
    }

    private boolean onResetToggle() {
        Setting.putReset(Math.abs(Setting.getReset() - 1));
        mBinding.control.action.reset.setText(ResUtil.getStringArray(R.array.select_reset)[Setting.getReset()]);
        return true;
    }

    private void onDecode() {
        if (refreshAndSwitchDecode()) return;
        mClock.setCallback(null);
        clearLyrics();
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
        decodeSwitchRefreshing = true;
        setNextDecodeText();
        setDecodeSwitchPending(true);
        mClock.setCallback(null);
        SpiderDebug.log("video-flow", "switch decode refresh start key=%s flag=%s episode=%s", key, flag, episode);
        Task.execute(() -> {
            try {
                Result result = SiteApi.playerContent(key, flag, episode);
                App.post(() -> switchDecodeWithResult(result, position, speed, repeat, metadata));
            } catch (Throwable e) {
                App.post(() -> {
                    decodeSwitchRefreshing = false;
                    setDecodeSwitchPending(false);
                    setDecode();
                    Notify.show(e.getMessage());
                });
            }
        });
        return true;
    }

    private void switchDecodeWithResult(Result result, long position, float speed, boolean repeat, MediaMetadata metadata) {
        decodeSwitchRefreshing = false;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            player().toggleDecode();
        } else {
            player().switchDecode(result, getHistoryKey(), metadata, isUseParse(), position, speed, repeat);
        }
        setR1Callback();
        setDecodeSwitchPending(false);
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
        setEnding(0);
        return true;
    }

    private void setEnding(long ending) {
        mHistory.setEnding(ending);
        mBinding.control.action.ending.setText(ending <= 0 ? getString(R.string.play_ed) : Util.timeMs(mHistory.getEnding()));
    }

    private void onOpening() {
        long position = player().getPosition();
        long duration = player().getDuration();
        if (player().canSetOpening(position, duration)) setOpening(position);
        setR1Callback();
    }

    private boolean onOpeningReset() {
        setR1Callback();
        setOpening(0);
        return true;
    }

    private void setOpening(long opening) {
        mHistory.setOpening(opening);
        mBinding.control.action.opening.setText(opening <= 0 ? getString(R.string.play_op) : Util.timeMs(mHistory.getOpening()));
    }

    private void onEpisodes() {
        syncSelectedEpisode(getFlag());
        EpisodeListDialog.create().flags(mFlagAdapter.getItems()).reverse(mHistory.isRevSort()).show(this);
    }

    private void onChoose() {
        PlayerHelper.choose(this, player().getUrl(), player().getHeaders(), player().isVod(), player().getPosition(), mBinding.control.title.getText());
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
        setR1Callback();
    }

    private boolean refreshAndSwitchPlayerKernel(int type) {
        if (playerKernelSwitchRefreshing) return true;
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
        playerKernelSwitchRefreshing = true;
        mClock.setCallback(null);
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
                    setR1Callback();
                    Notify.show(e.getMessage());
                });
            }
        });
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

    private void switchPlayerKernelWithResult(int type, Result result, long position, float speed, boolean repeat, MediaMetadata metadata) {
        playerKernelSwitchRefreshing = false;
        if (result == null || result.hasMsg() || result.getRealUrl().isEmpty()) {
            Notify.show(result != null && result.hasMsg() ? result.getMsg() : getString(R.string.error_play_url));
        } else {
            player().switchPlayer(type, result, getHistoryKey(), metadata, isUseParse(), position, speed, repeat);
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
        logVideoFrame("enterFullscreen before");
        setFullscreen(true);
        if (isLand() && !player().isPortrait()) setTransition();
        mBinding.video.setLayoutParams(new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
        setRequestedOrientation(PlaybackOrientation.getEnterFullscreenOrientation(player().isPortrait()));
        mBinding.control.title.setVisibility(View.VISIBLE);
        setSizeText();
        setRotate(player().isPortrait());
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        hideControl();
        logVideoFrame("enterFullscreen after");
    }

    private void exitFullscreen() {
        if (!isFullscreen()) return;
        if (service() == null) {
            SpiderDebug.log("video-flow", "fullscreen exit deferred reason=player-not-ready");
            return;
        }
        logVideoFrame("exitFullscreen before");
        setFullscreen(false);
        if (isLand() && !player().isPortrait()) setTransition();
        setRequestedOrientation(PlaybackOrientation.getExitFullscreenOrientation(isPort()));
        mBinding.episodeGroup.postDelayed(() -> mBinding.episodeGroup.scrollToPosition(mEpisodeGroupAdapter.getPosition()), 100);
        mBinding.episode.postDelayed(this::scrollEpisodeToSelected, 100);
        mBinding.control.title.setVisibility(View.INVISIBLE);
        setSizeText();
        mBinding.video.setLayoutParams(mFrameParams);
        mKeyDown.resetScale();
        App.post(mR3, 2000);
        setRotate(false);
        hideControl();
        logVideoFrame("exitFullscreen after");
    }

    private void setTransition() {
        if (!shouldAnimateVideoFrameTransition()) {
            Log.d(SIZE_TAG, "video transition skipped native player=" + player().getPlayerText());
            return;
        }
        ChangeBounds transition = new ChangeBounds();
        transition.setDuration(150);
        ViewGroup parent = (ViewGroup) mBinding.video.getParent();
        TransitionManager.beginDelayedTransition(parent, transition);
    }

    private boolean shouldAnimateVideoFrameTransition() {
        return service() == null || !player().isNativePlayer();
    }

    private void showProgress() {
        mBinding.progress.getRoot().setVisibility(View.VISIBLE);
        App.post(mR2, 0);
        hideError();
    }

    private void hideProgress() {
        mBinding.progress.getRoot().setVisibility(View.GONE);
        App.removeCallbacks(mR2);
        Traffic.reset();
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

    private void showControl() {
        if (service() == null || isInPictureInPictureMode()) return;
        if (mAudioStageVisible && !isFullscreen()) {
            hideWidgetOverlay();
            hideControl();
            return;
        }
        setTrackVisible();
        hideWidgetOverlay();
        mBinding.control.danmaku.setVisibility(isLock() || !player().haveDanmaku() ? View.GONE : View.VISIBLE);
        mBinding.control.setting.setVisibility(View.GONE);
        mBinding.control.right.rotate.setVisibility(isFullscreen() && !isLock() ? View.VISIBLE : View.GONE);
        mBinding.control.fullscreen.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.keep.setVisibility(mHistory == null || isFullscreen() ? View.GONE : View.VISIBLE);
        boolean showPlayParams = PlayerButtonSetting.isVisible(PlayerButtonSetting.PLAY_PARAMS);
        mBinding.control.action.playParams.setVisibility(showPlayParams ? View.VISIBLE : View.GONE);
        mBinding.control.osdDiagnostics.setVisibility(PlayerSetting.isOsdDiagnostics() && PlayerButtonSetting.isVisible(PlayerButtonSetting.PLAY_PARAMS) && !player().isEmpty() ? View.VISIBLE : View.GONE);
        mBinding.control.osdDiagnostics.setAlpha(mOsd != null && mOsd.isDiagnosticsVisible() ? 1f : 0.30f);
        mBinding.control.action.playParams.setSelected(mOsd != null && mOsd.isDiagnosticsVisible());
        mBinding.control.parse.setVisibility(isFullscreen() && isUseParse() ? View.VISIBLE : View.GONE);
        mBinding.control.action.getRoot().setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.right.lock.setVisibility(isFullscreen() ? View.VISIBLE : View.GONE);
        mBinding.control.info.setVisibility(player().isEmpty() ? View.GONE : View.VISIBLE);
        mBinding.control.cast.setVisibility(View.GONE);
        mBinding.control.center.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.bottom.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.back.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.top.setVisibility(isLock() ? View.GONE : View.VISIBLE);
        mBinding.control.getRoot().setVisibility(View.VISIBLE);
        updateCustomButtonVisibility();
        if (mOsd != null) mOsd.setControlsVisible(true);
        checkFullscreenImg();
        setR1Callback();
    }

    private void hideControl() {
        mBinding.control.getRoot().setVisibility(View.GONE);
        updateCustomButtonVisibility();
        if (mOsd != null) mOsd.setControlsVisible(false);
        App.removeCallbacks(mR1);
    }

    private void onOsdDiagnostics() {
        if (mOsd == null) return;
        mOsd.toggleDiagnostics();
        hideControl();
    }

    private void onPlayParams() {
        if (mOsd == null) return;
        boolean visible = !mOsd.isDiagnosticsVisible();
        PlayerSetting.putOsdDiagnostics(visible);
        mOsd.setDiagnosticsVisible(visible);
        hideControl();
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
        Traffic.setSpeed(mBinding.progress.traffic);
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

    private boolean isCurrentArtworkRequest(String url, String owner) {
        return TextUtils.equals(mArtworkRequestUrl, url) && TextUtils.equals(mArtworkRequestOwner, owner);
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

    private void restoreContextWall() {
        if (!Setting.isPlaybackArtworkWall()) return;
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
        } else {
            onItemClick(mHistory.getFlag());
            if (mHistory.isRevSort()) reverseEpisode(true);
        }
        if (preservePlayback) SpiderDebug.log("karaoke-result", "configuration restore preserved playback key=%s episode=%s", player().getKey(), mHistory.getVodRemarks());
        mRestoringConfigurationPlayback = false;
    }

    private void restoreFlagSelectionWithoutPlayback() {
        mFlagAdapter.setSelected(mHistory.getFlag());
        Flag flag = getFlag();
        if (flag == null) return;
        syncSelectedEpisode(flag);
        setEpisodeAdapter(flag.getEpisodes());
        scrollEpisodeToSelected();
        setQualityVisible(false);
        if (mHistory.isRevSort()) reverseEpisode(true);
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
        setText(mBinding.content, 0, getContent());
        setDetailLyrics(getContent());
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
        Flag flag = getFlag();
        String vodFlag = flag == null ? "" : flag.getFlag();
        boolean sameEpisode = item.matchesName(mHistory.getEpisode());
        boolean sameFlag = TextUtils.equals(mHistory.getVodFlag(), vodFlag);
        if ((!sameEpisode || !sameFlag) && service() != null) {
            updatePlaybackHistoryPosition();
            PlaybackEventCollector.get().onStop(player());
        }
        mHistory.setPosition(sameEpisode ? mHistory.getPosition() : C.TIME_UNSET);
        if (!sameEpisode) mHistory.setDuration(C.TIME_UNSET);
        mHistory.setVodFlag(vodFlag);
        mHistory.setVodRemarks(item.getName());
        mHistory.setEpisodeUrl(item.getUrl());
        PlaybackEventCollector.get().updateHistory(mHistory);
    }

    private void checkControl() {
        if (isVisible(mBinding.control.getRoot())) showControl();
    }

    private void checkKeepImg() {
        boolean kept = Keep.find(getHistoryKey()) != null;
        mBinding.control.keep.setImageResource(kept ? R.drawable.ic_control_keep_on : R.drawable.ic_control_keep_off);
        mBinding.audioKeepAction.setSelected(kept);
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
        if (name) mBinding.control.title.setText(item.getName());
        if (name) updateAudioStageText();
        updateFlag(getFlag(), item.getFlags());
        if (pic) setArtwork(item.getPic());
        if (pic || name) setMetadata();
        if (pic || name) syncHistory();
        if (pic || name) updateKeep();
        if (id) updateNavigationKey();
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
            if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "activity onNext audioStage=%s episode=%s", mAudioStageVisible, getEpisode() == null ? "null" : getEpisode().getName());
            if (!mAudioStageVisible || !playNextAudioPlaylistEntry()) checkNext();
        }

        @Override
        public void onPrev() {
            checkPrev();
        }

        @Override
        public void onStop() {
            finish();
        }

        @Override
        public void onReplay() {
            VideoActivity.this.onReplay();
        }

        @Override
        public void onAudio() {
            setAudioOnly(true);
            syncPiPForPlaybackMode();
            Util.moveToBackground(VideoActivity.this);
        }
    };

    @Override
    protected String getPlaybackKey() {
        return getHistoryKey();
    }

    @Override
    protected void onPrepare() {
        setDecode();
        setLut();
        setPosition();
        refreshLyrics();
    }

    @Override
    protected void onTracksChanged() {
        updateAudioOnlyState();
        syncPiPForPlaybackMode();
        refreshLyrics();
        setTrackVisible();
        mClock.setCallback(this);
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
    }

    @Override
    protected void onError(String msg) {
        recordPlayHealth(false, msg);
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
        debugPlaybackControl("stateChanged=" + state);
        debugLyricsLoop("stateChanged=" + state, true);
        switch (state) {
            case Player.STATE_BUFFERING:
                showProgress();
                break;
            case Player.STATE_READY:
                if (mPendingKaraokeResult == null) mKaraokeResultShown = false;
                recordPlayHealth(true, "");
                hideProgress();
                checkControl();
                refreshLyrics();
                player().reset();
                break;
            case Player.STATE_ENDED:
                checkEnded(true);
                updatePlayControl(false, syncPiPForPlaybackMode());
                break;
        }
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        debugPlaybackControl("playingChanged=" + isPlaying);
        debugLyricsLoop("playingChanged=" + isPlaying, true);
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

    @Override
    public void onSubtitleClick() {
        SubtitleDialog.create().view(mBinding.exo.getSubtitleView()).player(player()).show(this);
        hideControl();
    }

    @Override
    public void onTimeChanged(long time) {
        if (!isOwner()) return;
        long position, duration;
        mHistory.setCreateTime(time);
        updatePlaybackHistoryPosition();
        syncCurrentAudioPlaylistMetadata();
        debugLyricsLoop("clock", false);
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
    public void onCastEvent(CastEvent event) {
        if (isRedirect()) return;
        ReceiveDialog.create().event(event).show(this);
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
        mParseAdapter.reload();
    }

    private void setPosition() {
        if (mHistory == null) return;
        if (mHistory.isNearEnding()) {
            SpiderDebug.log("video-flow", "reset near-end history position=%d duration=%d key=%s", mHistory.getPosition(), mHistory.getDuration(), getHistoryKey());
            mHistory.resetPlaybackPosition();
            syncHistory();
        }
        long position = Math.max(mHistory.getOpening(), mHistory.getPosition());
        if (position > 0) player().seekTo(position);
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
        if (mAudioStageVisible) return;
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
        if (SpiderDebug.isEnabled()) SpiderDebug.log("audio-auto-next", "checkEnded notify=%s audioStage=%s adapter=%d", notify, mAudioStageVisible, mEpisodeAdapter.getItemCount());
        if (showKaraokeResultIfNeeded(notify ? KARAOKE_RESULT_ACTION_NEXT : KARAOKE_RESULT_ACTION_NEXT_SILENT)) return;
        if (mAudioStageVisible && playNextAudioPlaylistEntry()) return;
        checkNext(notify);
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

    private void setTrackVisible() {
        mBinding.control.action.text.setVisibility(player().haveTrack(C.TRACK_TYPE_TEXT) || player().isVod() ? View.VISIBLE : View.GONE);
        mBinding.control.action.audio.setVisibility(player().haveTrack(C.TRACK_TYPE_AUDIO) ? View.VISIBLE : View.GONE);
        mBinding.control.action.video.setVisibility(player().haveTrack(C.TRACK_TYPE_VIDEO) ? View.VISIBLE : View.GONE);
        applyActionButtonVisibility();
        updateAudioStageControls();
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
        mBinding.quick.setVisibility(View.GONE);
        mQuickAdapter.addAll(items);
        if (isQuickSearchVisible()) mQuickSearchDialog.addAll(items);
        if (revealManualSearch && !items.isEmpty()) revealManualSearch = false;
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
        debugPlaybackControl("onPaused");
        controller().pause();
    }

    private void onPlay() {
        debugPlaybackControl("onPlay.before");
        if (mHistory != null && isEnded()) controller().seekTo(mHistory.getOpening());
        if (!player().isEmpty() && isIdle()) controller().prepare();
        controller().play();
        debugPlaybackControl("onPlay.after");
    }

    private boolean isFullscreen() {
        return fullscreen;
    }

    private void setFullscreen(boolean fullscreen) {
        Util.toggleFullscreen(this, this.fullscreen = fullscreen);
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
        view.post(() -> view.scrollToPosition(position));
    }

    @Override
    public void onCasted() {
        clearLyrics();
        clearKaraokeState();
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
        refreshEpisodeTitles();
    }

    private void refreshEpisodeTitles() {
        if (mEpisodeAdapter == null) return;
        if (mFlagAdapter == null || mFlagAdapter.isEmpty()) {
            updateEpisodeSpan(mEpisodeAdapter.getItems());
            mEpisodeAdapter.notifyItemRangeChanged(0, mEpisodeAdapter.getItemCount());
        } else {
            Flag flag = getFlag();
            if (flag != null) setEpisodeItems(flag.getEpisodes());
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
        mBinding.control.action.speed.setText(player().setSpeed(PlayerSetting.getDefaultSpeed()));
        mHistory.setSpeed(player().getSpeed());
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
        setupCustomActionButtons();
        if (isFullscreen()) Util.hideSystemUI(this);
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
        mClock.stop().start();
        if (mOsd != null) mOsd.start();
        setAudioOnly(false);
        setStop(false);
        if (service() != null) refreshLyrics();
        syncLyricsPlaybackState();
        syncKaraokePosition();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mOsd != null) mOsd.stop();
        if (mKaraoke != null) mKaraoke.clear();
        if (PlayerSetting.isBackgroundOff()) mClock.stop();
        if (!isAudioOnly()) setStop(true);
    }

    @Override
    protected void onBackInvoked() {
        if (hasLutQuick() && mBinding.lutQuick.hideIfVisible()) {
            return;
        } else if (isVisible(mBinding.control.getRoot())) {
            hideControl();
        } else if (isFullscreen() && !isLock()) {
            exitFullscreen();
        } else if (!isLock()) {
            if (showKaraokeResultIfNeeded(KARAOKE_RESULT_ACTION_SYSTEM_BACK)) return;
            finishVideoPlaybackFromSystemBack();
        }
    }

    private void finishVideoPlaybackFromSystemBack() {
        mViewModel.stopSearch();
        saveHistory(true);
        markPlaybackExiting();
        stopPlayback();
        if (isTaskRoot()) startActivity(new Intent(this, HomeActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
        super.onBackInvoked();
    }

    @Override
    protected void onDestroy() {
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
        mClock.release();
        saveHistory(true);
        Timer.get().reset();
        DanmakuApi.cancel();
        RefreshEvent.keep();
        App.removeCallbacks(mR1, mR2, mR3, mR4);
        if (mOsd != null) mOsd.release();
        mViewModel.getResult().removeObserver(mObserveDetail);
        mViewModel.getPlayer().removeObserver(mObservePlayer);
        mViewModel.getSearch().removeObserver(mObserveSearch);
        SiteHealthStore.flush();
        super.onDestroy();
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
