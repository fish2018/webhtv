package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.databinding.ActivityAudioBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.audio.AudioHistory;
import com.fongmi.android.tv.ui.audio.AudioMiniPlayer;
import com.fongmi.android.tv.ui.audio.AudioPlaybackReconciliation;
import com.fongmi.android.tv.ui.audio.AudioPlaybackResolver;
import com.fongmi.android.tv.ui.custom.CustomSeekView;
import com.fongmi.android.tv.ui.custom.CustomWallView;
import com.fongmi.android.tv.ui.dialog.AudioCommentDialog;
import com.fongmi.android.tv.utils.ActivityLaunch;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.LyricUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.bumptech.glide.Glide;
import com.github.catvod.utils.Prefers;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AudioActivity extends PlaybackActivity {

    private static final String EXTRA_PLAYBACK_KEY = "playbackKey";
    private static final String EXTRA_SITE_KEY = "siteKey";
    private static final String EXTRA_FLAG = "flag";
    private static final String EXTRA_VOD_NAME = "vodName";
    private static final String EXTRA_VOD_PIC = "vodPic";
    private static final String EXTRA_EPISODES = "episodes";
    private static final String EXTRA_EPISODES_KEY = "episodesKey";
    private static final String EXTRA_INDEX = "index";
    private static final String EXTRA_RESULT = "result";
    private static final String EXTRA_RESULT_KEY = "resultKey";
    private static final String EXTRA_TIMEOUT = "timeout";
    private static final String EXTRA_HEADERS = "headers";
    private static final String EXTRA_TITLE = "title";
    private static final String EXTRA_SUBTITLE = "subtitle";
    private static final String EXTRA_PIC = "pic";
    private static final String PREF_LYRIC_OFFSET_PREFIX = "audio_lyric_offset_";
    private static final String PREF_COVER_BACKGROUND = "audio_cover_background";
    private static final String TITLE_PLAYLIST_DETAIL = "歌单详情";
    private static final long LYRIC_OFFSET_STEP = 500L;
    private static final long LYRIC_OFFSET_LIMIT = 30000L;
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:\\.(\\d{1,3}))?]");
    private static final Pattern LRC_OFFSET = Pattern.compile("(?i)\\[offset\\s*:\\s*([+-]?\\d+)\\s*]");
    private static final Pattern NON_SONG_EPISODE = Pattern.compile("(第\\s*\\d{1,5}\\s*[集章节回讲期])|(\\d{1,5}\\s*[集章节回讲期])|(\\d{2,5}\\s*回)");
    private static final String[] NON_SONG_KEYWORDS = {"有声", "听书", "小说", "评书", "相声", "电台", "广播", "fm", "radio", "podcast", "播客", "讲书", "讲坛", "故事", "朗读", "书场", "课程", "公开课", "脱口秀", "讲古"};
    private static final String[] SONG_KEYWORDS = {"歌曲", "音乐", "歌手", "专辑", "演唱", "主题曲", "片头曲", "片尾曲", "插曲", "ost", "cover", "live"};
    private static final java.util.concurrent.ConcurrentHashMap<String, ArrayList<Episode>> EPISODE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.concurrent.ConcurrentHashMap<String, String> RESULT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private final Random random = new Random();
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            updateLyricAndHistory();
            App.post(this, 500);
        }
    };
    private final Runnable lyricBrowseReturn = new Runnable() {
        @Override
        public void run() {
            lyricBrowsing = false;
            updateLyric(getPlayerPosition());
        }
    };

    private ActivityAudioBinding binding;
    private PlaylistAdapter playlistAdapter;
    private ArrayList<Episode> episodes;
    private List<Lyric> lyrics;
    private Result initialResult;
    private String objectCacheKey;
    private long lastHistorySync;
    private float lyricTouchY;
    private boolean started;
    private boolean coverBackground;
    private boolean lyricEnabled = true;
    private boolean lyricBrowsing;
    private boolean lyricTouchMoved;
    private boolean discMode = true;
    private boolean discSpinning = false;
    private boolean resumeApplied;
    private android.view.animation.Animation discRotateAnimation;
    private long lyricOffset;
    private int index;
    private int lyricBrowseIndex;
    private int currentLyricIndex;
    private int lyricRequest;
    private int artworkRequest;
    private int mode;

    public static void start(Activity activity, String url, String title, String subtitle, String pic) {
        Result result = new Result();
        result.setUrl(AudioUtil.cleanUrl(url));
        result.setParse(0);
        start(activity, "", "", "", title, subtitle, pic, null, 0, result, Constant.TIMEOUT_PLAY, null);
    }

    public static boolean startSite(Activity activity, String key, String id, String name, String pic, String mark) {
        if (PlayerSetting.isImmersiveAudioMode()) return VideoActivity.startImmersiveAudioSite(activity, key, id, name, pic, mark);
        if (SiteApi.PUSH.equals(key)) return false;
        if (!AudioUtil.isAudioSiteEnabled(key)) return false;
        Notify.show("正在加载音频");
        Task.execute(() -> {
            try {
                AudioPlaybackResolver.Resolved resolved = AudioPlaybackResolver.resolveSite(key, id, name, pic, mark);
                Result result = resolved.getResult();
                Vod vod = resolved.getVod();
                Flag flag = resolved.getFlag();
                Episode episode = resolved.getEpisode();
                App.post(() -> ActivityLaunch.postOnAnimation(activity, () -> start(activity, AudioHistory.buildPlaybackKey(key, id), key, flag.getFlag(), vod.getName(), episode.getDisplayName(), result.hasArtwork() ? result.getArtwork() : vod.getPic(), flag.getEpisodes(), resolved.getIndex(), result, resolved.getTimeout(), result.getHeader())));
            } catch (Throwable e) {
                App.post(() -> Notify.show(TextUtils.isEmpty(e.getMessage()) ? "音频加载失败" : e.getMessage()));
            }
        });
        return true;
    }

    public static void start(Activity activity, String key, String url, String title, String subtitle, String pic, Map<String, String> headers, String lrc) {
        Result result = new Result();
        result.setUrl(AudioUtil.cleanUrl(url));
        result.setParse(0);
        result.setHeader(headers);
        result.setLrc(lrc);
        start(activity, key, "", "", title, subtitle, pic, null, 0, result, Constant.TIMEOUT_PLAY, headers);
    }

    public static boolean startIfAudio(Activity activity, String key, Result result, String title, String subtitle, String pic) {
        if (PlayerSetting.isImmersiveAudioMode()) return false;
        if (!AudioUtil.isAudio(result)) return false;
        start(activity, key, "", "", title, subtitle, result.hasArtwork() ? result.getArtwork() : pic, null, 0, result, Constant.TIMEOUT_PLAY, result.getHeader());
        return true;
    }

    public static boolean startIfAudio(Activity activity, String playbackKey, String siteKey, String flag, String vodName, String vodPic, List<Episode> episodes, int index, Result result, long timeout) {
        if (PlayerSetting.isImmersiveAudioMode()) return false;
        if (!AudioUtil.isAudioSiteEnabled(siteKey)) return false;
        boolean audio = AudioUtil.isAudio(result);
        if (SiteApi.PUSH.equals(siteKey) && !audio) return false;
        if (!audio && (result == null || result.getUrl().isEmpty())) return false;
        String subtitle = episodes == null || episodes.isEmpty() || index < 0 || index >= episodes.size() ? "" : episodes.get(index).getDisplayName();
        start(activity, playbackKey, siteKey, flag, vodName, subtitle, result.hasArtwork() ? result.getArtwork() : vodPic, episodes, index, result, timeout, result.getHeader());
        return true;
    }

    public static void startFromMini(Activity activity, AudioMiniPlayer.State state) {
        if (state == null) return;
        deactivateMini(null);
        start(activity, state.playbackKey, state.siteKey, state.flag, state.title, state.subtitle(), state.pic, state.episodes, state.index, state.copyResult(), state.timeout, state.headers);
    }

    private static void deactivateMini(PlaybackService service) {
        AudioMiniPlayer.deactivateForFull(service);
    }

    private static void start(Activity activity, String playbackKey, String siteKey, String flag, String title, String subtitle, String pic, List<Episode> episodes, int index, Result result, long timeout, Map<String, String> headers) {
        String objectKey = "audio_" + System.nanoTime();
        if (episodes != null) EPISODE_CACHE.put(objectKey, new ArrayList<>(episodes));
        RESULT_CACHE.put(objectKey, Objects.toString(result, ""));
        Intent intent = new Intent(activity, AudioActivity.class);
        intent.putExtra(EXTRA_PLAYBACK_KEY, playbackKey);
        intent.putExtra(EXTRA_SITE_KEY, siteKey);
        intent.putExtra(EXTRA_FLAG, flag);
        intent.putExtra(EXTRA_VOD_NAME, title);
        intent.putExtra(EXTRA_VOD_PIC, pic);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_SUBTITLE, subtitle);
        intent.putExtra(EXTRA_PIC, pic);
        intent.putExtra(EXTRA_INDEX, Math.max(index, 0));
        intent.putExtra(EXTRA_TIMEOUT, timeout);
        intent.putExtra(EXTRA_RESULT_KEY, objectKey);
        intent.putExtra(EXTRA_EPISODES_KEY, objectKey);
        if (headers != null && !headers.isEmpty()) intent.putExtra(EXTRA_HEADERS, new java.util.HashMap<>(headers));
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityAudioBinding.inflate(getLayoutInflater());
    }

    @Override
    protected boolean customWall() {
        return false;
    }

    @Override
    protected boolean shouldPauseOnBackground() {
        return !shouldContinueInBackground();
    }

    private boolean shouldContinueInBackground() {
        return isNonSongAudio() ? PlayerSetting.isAudioBookNotification() : PlayerSetting.isMusicNotification();
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setAudioOnly(true);
        objectCacheKey = Objects.toString(getIntent().getStringExtra(EXTRA_RESULT_KEY), "");
        episodes = getCachedEpisodes();
        if (episodes == null) episodes = new ArrayList<>();
        index = Math.min(Math.max(getIntent().getIntExtra(EXTRA_INDEX, 0), 0), Math.max(episodes.size() - 1, 0));
        initialResult = Result.fromJson(getCachedResult());
        lyricEnabled = shouldEnableLyrics(initialResult);
        lyricOffset = 0;
        coverBackground = Prefers.getBoolean(PREF_COVER_BACKGROUND, true);
        lyrics = new ArrayList<>();
        playlistAdapter = new PlaylistAdapter();
        binding.playlist.setLayoutManager(new LinearLayoutManager(this));
        binding.playlist.setAdapter(playlistAdapter);
        playlistAdapter.setItems(episodes);
        binding.title.setSelected(true);
        discRotateAnimation = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.rotate_disc);
        discMode = true;
        binding.discCover.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setOval(0, 0, view.getWidth(), view.getHeight());
            }
        });
        binding.discCover.setClipToOutline(true);
        applyAudioInsets();
        setText();
        setCover();
        setMode(0, false);
        updateAudioToolState();
        updateKeepState();
        super.initView(savedInstanceState);
        App.post(ticker);
    }

    private void applyAudioInsets() {
        int contentLeft = binding.audioContent.getPaddingLeft();
        int contentTop = binding.audioContent.getPaddingTop();
        int contentRight = binding.audioContent.getPaddingRight();
        int contentBottom = binding.audioContent.getPaddingBottom();
        ViewGroup.MarginLayoutParams sheetBaseParams = (ViewGroup.MarginLayoutParams) binding.playlistSheet.getLayoutParams();
        int sheetBottom = sheetBaseParams.bottomMargin;
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (view, insets) -> {
            int top = insets.getInsets(WindowInsetsCompat.Type.statusBars() | WindowInsetsCompat.Type.displayCutout()).top;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            binding.audioContent.setPadding(contentLeft, contentTop + top, contentRight, contentBottom + bottom);
            ViewGroup.MarginLayoutParams sheetParams = (ViewGroup.MarginLayoutParams) binding.playlistSheet.getLayoutParams();
            int targetBottom = sheetBottom + bottom;
            if (sheetParams.bottomMargin != targetBottom) {
                sheetParams.bottomMargin = targetBottom;
                binding.playlistSheet.setLayoutParams(sheetParams);
            }
            return insets;
        });
        ViewCompat.requestApplyInsets(binding.getRoot());
        binding.getRoot().post(() -> ViewCompat.requestApplyInsets(binding.getRoot()));
    }

    @Override
    protected void initEvent() {
        binding.minimize.setOnClickListener(view -> moveTaskToBack(true));
        binding.close.setOnClickListener(view -> closeAudio());
        binding.keep.setOnClickListener(view -> onKeep());
        binding.play.setOnClickListener(view -> togglePlay());
        binding.prev.setOnClickListener(view -> playPrev(true));
        binding.next.setOnClickListener(view -> playNext(true));
        binding.prev.setOnLongClickListener(view -> seekBy(-15_000));
        binding.next.setOnLongClickListener(view -> seekBy(15_000));
        binding.repeat.setOnClickListener(view -> setMode((mode + 1) % 3, true));
        binding.playlistRepeat.setOnClickListener(view -> setMode((mode + 1) % 3, true));
        binding.lyricAdvance.setOnClickListener(view -> adjustLyricOffset(-LYRIC_OFFSET_STEP));
        binding.lyricAdvance.setOnLongClickListener(view -> {
            resetLyricOffset();
            return true;
        });
        binding.lyricDelay.setOnClickListener(view -> adjustLyricOffset(LYRIC_OFFSET_STEP));
        binding.lyricDelay.setOnLongClickListener(view -> {
            resetLyricOffset();
            return true;
        });
        binding.lyricSearch.setOnClickListener(view -> showLyricSearch());
        binding.lyricPanel.setOnTouchListener(this::onLyricTouch);
        binding.commentButton.setOnClickListener(view -> showComments());
        binding.coverBackground.setOnClickListener(view -> toggleCoverBackground());
        binding.discModeToggle.setOnClickListener(view -> toggleDiscLyricMode());
        binding.discContainer.setOnClickListener(view -> toggleDiscLyricMode());
        binding.playlistButton.setOnClickListener(view -> showPlaylist(true));
        binding.playlistClose.setOnClickListener(view -> showPlaylist(false));
        binding.playlistMask.setOnClickListener(view -> showPlaylist(false));
        binding.locate.setOnClickListener(view -> scrollPlaylistToCurrent());
    }

    @Override
    protected PlaybackService.NavigationCallback getNavigationCallback() {
        return navigationCallback;
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
        String key = getIntent().getStringExtra(EXTRA_PLAYBACK_KEY);
        return TextUtils.isEmpty(key) ? "audio:" + getTitleText() : key;
    }

    @Override
    protected boolean isOwner() {
        if (super.isOwner()) return true;
        PlayerManager manager = player();
        return manager != null && currentPlaybackMatch(manager).owned();
    }

    private AudioPlaybackReconciliation.Match currentPlaybackMatch(PlayerManager manager) {
        int count = episodes == null ? 0 : episodes.size();
        return AudioPlaybackReconciliation.reconcile(manager == null ? null : manager.getKey(), getPlaybackKey(), getFlag(), index, count);
    }

    @Override
    protected void onServiceConnected() {
        AudioMiniPlayer.deactivateForFull(service());
        setMode(mode, false);
        PlayerManager manager = player();
        if (manager != null && !manager.isReleased() && !manager.isEmpty()) {
            AudioPlaybackReconciliation.Match match = currentPlaybackMatch(manager);
            if (match.owned()) {
                reconcileCurrentPlayback(manager, match);
                service().setNavigationCallback(navigationCallback, manager.getKey());
                restoreCurrent(manager);
                return;
            }
        }
        if (!started) playCurrent(initialResult);
    }

    private void reconcileCurrentPlayback(PlayerManager manager, AudioPlaybackReconciliation.Match match) {
        index = match.index();
        getIntent().putExtra(EXTRA_INDEX, index);
        Episode episode = getCurrentEpisode();
        MediaMetadata metadata = manager.getMetadata();
        String subtitle = episode == null ? "" : episode.getDisplayName();
        if (TextUtils.isEmpty(subtitle) && metadata != null) subtitle = Objects.toString(metadata.subtitle, Objects.toString(metadata.artist, ""));
        if (!TextUtils.isEmpty(subtitle)) getIntent().putExtra(EXTRA_SUBTITLE, subtitle);
        Result current = manager.getCurrentResult();
        if (current != null && !current.getRealUrl().isEmpty()) {
            initialResult = Result.fromJson(current.toString());
            if (!TextUtils.isEmpty(objectCacheKey)) RESULT_CACHE.put(objectCacheKey, initialResult.toString());
        }
        String pic = current != null && current.hasArtwork() ? current.getArtwork() : "";
        if (TextUtils.isEmpty(pic) && metadata != null && metadata.artworkUri != null) pic = metadata.artworkUri.toString();
        if (!TextUtils.isEmpty(pic)) getIntent().putExtra(EXTRA_PIC, pic);
    }

    private void restoreCurrent(PlayerManager manager) {
        started = true;
        lyricEnabled = shouldEnableLyrics(initialResult);
        lyricOffset = getSavedLyricOffset();
        setText();
        setCover();
        updateAudioToolState();
        updateKeepState();
        updatePlaylistState();
        syncPlaybackUI();
        if (lyricEnabled) {
            loadLyrics(initialResult);
            loadArtwork();
        } else {
            disableLyrics();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        discSpinning = false;
        if (started) syncPlaybackUI();
    }

    @Override
    protected void onPrepare() {
        binding.loading.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onError(String msg) {
        binding.loading.setVisibility(View.GONE);
        Notify.show(msg);
    }

    @Override
    protected void onPlayingChanged(boolean isPlaying) {
        updatePlayIcon(isPlaying);
        updateDiscRotation();
        if (isPlaying) binding.loading.setVisibility(View.GONE);
    }

    @Override
    protected void onStateChanged(int state) {
        binding.loading.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
        PlayerManager manager = player();
        if (state == Player.STATE_READY && manager != null) {
            manager.reset();
            updateHistoryForTrack(manager);
            applyResumePositionOnce(manager);
        }
        if (state == Player.STATE_ENDED && mode != 2) playNext(false);
        updatePlayIcon();
    }

    private final PlaybackService.NavigationCallback navigationCallback = new PlaybackService.NavigationCallback() {
        @Override
        public void onPrev() {
            playPrev(true);
        }

        @Override
        public void onNext() {
            playNext(true);
        }

        @Override
        public void onStop() {
            stopAndFinish();
        }

        @Override
        public void onReplay() {
            PlayerManager manager = player();
            if (manager == null) return;
            manager.seekTo(0);
            manager.play();
        }
    };

    private void playCurrent(Result result) {
        if (isFinishing() || isDestroyed()) return;
        Result target = prepareResult(result);
        if (target == null || target.getRealUrl().isEmpty()) {
            Notify.show("音频地址为空");
            finish();
            return;
        }
        PlayerManager manager = player();
        if (manager == null) return;
        started = true;
        resumeApplied = false;
        initialResult = Result.fromJson(Objects.toString(target, ""));
        if (target.hasArtwork()) getIntent().putExtra(EXTRA_PIC, target.getArtwork());
        lyricEnabled = shouldEnableLyrics(target);
        lyricOffset = getSavedLyricOffset();
        setText();
        setCover();
        updateAudioToolState();
        updateKeepState();
        MediaMetadata metadata = buildAudioMetadata();
        updateNavigationKey();
        startPlayer(getPlaybackKey(), target, target.shouldUseParse(), getTimeout(), metadata);
        manager.play();
        if (lyricEnabled) {
            loadLyrics(target);
            loadArtwork();
        } else {
            disableLyrics();
        }
        updatePlaylistState();
    }

    private Result prepareResult(Result result) {
        if (result == null) return null;
        if (!result.getUrl().isEmpty()) result.setUrl(AudioUtil.cleanUrl(result.getUrl().v()));
        if (result.getHeader().isEmpty()) result.setHeader(getHeaders());
        return result;
    }

    private void playIndex(int target, boolean notify) {
        if (episodes.isEmpty()) {
            if (notify) Notify.show("没有更多音频");
            return;
        }
        if (target < 0 || target >= episodes.size()) {
            if (notify) Notify.show(target < 0 ? "已经是第一首" : "已经是最后一首");
            return;
        }
        index = target;
        binding.loading.setVisibility(View.VISIBLE);
        updatePlaylistState();
        if (target == getIntent().getIntExtra(EXTRA_INDEX, 0) && initialResult != null && !initialResult.getRealUrl().isEmpty()) {
            playCurrent(initialResult);
            return;
        }
        String siteKey = getSiteKey();
        String flag = getFlag();
        Episode episode = episodes.get(index);
        if (TextUtils.isEmpty(siteKey) || TextUtils.isEmpty(flag)) {
            Notify.show("缺少音频列表来源");
            binding.loading.setVisibility(View.GONE);
            return;
        }
        FluentFuture<Result> future = FluentFuture.from(Task.executor().submit(() -> SiteApi.playerContent(siteKey, flag, episode.getUrl()))).withTimeout(getTimeout(), TimeUnit.MILLISECONDS, Task.scheduler());
        future.addCallback(Task.callback(
                result -> App.post(() -> playCurrent(result)),
                error -> App.post(() -> {
                    binding.loading.setVisibility(View.GONE);
                    Notify.show("音频加载失败");
                })
        ), MoreExecutors.directExecutor());
    }

    private void playPrev(boolean notify) {
        if (mode == 1) playIndex(randomIndex(), notify);
        else playIndex(index - 1, notify);
    }

    private void playNext(boolean notify) {
        if (mode == 1) playIndex(randomIndex(), notify);
        else playIndex(index + 1, notify);
    }

    private int randomIndex() {
        if (episodes.size() <= 1) return index;
        int target;
        do {
            target = random.nextInt(episodes.size());
        } while (target == index);
        return target;
    }

    private void togglePlay() {
        if (controller() == null) return;
        if (controller().isPlaying()) {
            controller().pause();
            updatePlayIcon(false);
        } else {
            PlayerManager manager = player();
            if (manager == null) return;
            if (!manager.isEmpty() && isIdle()) controller().prepare();
            controller().play();
            updatePlayIcon(true);
        }
    }

    private boolean seekBy(long delta) {
        if (controller() == null) return true;
        seekTo(delta);
        return true;
    }

    private void stopAndFinish() {
        AudioMiniPlayer.deactivateForFull(service());
        PlayerManager manager = player();
        if (manager != null) manager.stop();
        finish();
    }

    private void closeAudio() {
        if (Util.isMobile()) minimizeToFloatingPlayer();
        else stopAndFinish();
    }

    private void minimizeToFloatingPlayer() {
        PlayerManager manager = player();
        if (manager == null || manager.isReleased() || manager.isEmpty()) {
            finish();
            return;
        }
        AudioMiniPlayer.activate(buildMiniState(), service());
        service().clearNavigationCallback(navigationCallback);
        finish();
    }

    private AudioMiniPlayer.State buildMiniState() {
        return new AudioMiniPlayer.State(
                getPlaybackKey(),
                getSiteKey(),
                getFlag(),
                getTitleText(),
                getSubtitleText(),
                getPic(),
                episodes,
                index,
                initialResult,
                getTimeout(),
                getHeaders(),
                mode
        );
    }

    private void setMode(int mode, boolean toast) {
        this.mode = mode;
        PlayerManager manager = player();
        if (manager != null) manager.setRepeatOne(mode == 2);
        int icon = mode == 1 ? R.drawable.ic_audio_shuffle : mode == 2 ? R.drawable.ic_audio_repeat_one : R.drawable.ic_audio_repeat;
        binding.repeat.setImageResource(icon);
        binding.playlistRepeat.setImageResource(icon);
        if (toast) Notify.show(mode == 1 ? "随机播放" : mode == 2 ? "单曲循环" : "顺序播放");
    }

    private void showPlaylist(boolean show) {
        binding.playlistPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) scrollPlaylistToCurrent();
    }

    private void scrollPlaylistToCurrent() {
        if (episodes.isEmpty()) return;
        binding.playlist.post(() -> binding.playlist.scrollToPosition(index));
    }

    private void updatePlaylistState() {
        binding.playlistCount.setText(String.format(Locale.getDefault(), "%d/%d", episodes.isEmpty() ? 0 : index + 1, episodes.size()));
        playlistAdapter.setCurrent(index);
    }

    private void adjustLyricOffset(long delta) {
        lyricOffset = Math.max(-LYRIC_OFFSET_LIMIT, Math.min(LYRIC_OFFSET_LIMIT, lyricOffset + delta));
        saveLyricOffset();
        updateAudioToolState();
        updateLyric(getPlayerPosition());
        Notify.show(lyricOffset == 0 ? "歌词偏移已重置" : String.format(Locale.getDefault(), "歌词%s %.1f 秒", lyricOffset > 0 ? "延后" : "提前", Math.abs(lyricOffset) / 1000f));
    }

    private void resetLyricOffset() {
        lyricOffset = 0;
        saveLyricOffset();
        updateAudioToolState();
        updateLyric(getPlayerPosition());
        Notify.show("歌词偏移已重置");
    }

    private boolean onLyricTouch(View view, MotionEvent event) {
        if (!lyricEnabled) return false;
        if (lyrics == null || lyrics.isEmpty()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                lyricTouchY = event.getY();
                lyricBrowseIndex = currentLyricIndex;
                lyricBrowsing = true;
                lyricTouchMoved = false;
                App.removeCallbacks(lyricBrowseReturn);
                return true;
            }
            case MotionEvent.ACTION_MOVE -> {
                float delta = event.getY() - lyricTouchY;
                int threshold = ResUtil.dp2px(36);
                if (Math.abs(delta) < threshold) return true;
                browseLyricLine(lyricBrowseIndex + (delta < 0 ? 1 : -1));
                lyricTouchY = event.getY();
                lyricTouchMoved = true;
                scheduleLyricBrowseReturn();
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!lyricTouchMoved) Notify.show("上下滑动可查看歌词");
                scheduleLyricBrowseReturn();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void browseLyricLine(int targetIndex) {
        if (lyrics == null || lyrics.isEmpty()) return;
        if (targetIndex < 0 || targetIndex >= lyrics.size()) {
            Notify.show(targetIndex < 0 ? "已经是第一句" : "已经是最后一句");
            return;
        }
        lyricBrowseIndex = targetIndex;
        displayLyricIndex(targetIndex);
    }

    private void scheduleLyricBrowseReturn() {
        App.removeCallbacks(lyricBrowseReturn);
        App.post(lyricBrowseReturn, 3000);
    }

    private void showLyricSearch() {
        if (!lyricEnabled || !isActivityAlive()) return;
        int request = lyricRequest;
        String title = getTitleText();
        String subtitle = getSubtitleText();
        long duration = getPlayerDuration();
        Notify.show("正在搜索歌词");
        Task.execute(() -> {
            List<LyricUtil.Option> options = LyricUtil.findOptions(title, subtitle, duration);
            App.post(() -> {
                if (!isCurrentLyricSearch(request, title, subtitle)) return;
                if (options.isEmpty()) {
                    Notify.show("没有找到可选歌词");
                    return;
                }
                String[] labels = new String[options.size()];
                for (int i = 0; i < options.size(); i++) labels[i] = options.get(i).getLabel();
                new AlertDialog.Builder(this)
                        .setTitle("选择歌词")
                        .setItems(labels, (dialog, which) -> applyManualLyric(request, options.get(which), title, subtitle, duration))
                        .show();
            });
        });
    }

    private void showComments() {
        if (!isActivityAlive()) return;
        long duration = getPlayerDuration();
        AudioCommentDialog.create(getTitleText(), getSubtitleText(), duration).show(this);
    }

    private void onKeep() {
        if (!canUseVodRecord()) return;
        Keep keep = findCurrentKeep();
        Notify.show(keep != null ? R.string.keep_del : R.string.keep_add);
        if (keep != null) keep.delete();
        else createKeep();
        updateKeepState();
        RefreshEvent.keep();
    }

    private void createKeep() {
        Keep keep = new Keep();
        keep.setKey(getPlaybackKey());
        keep.setCid(VodConfig.getCid());
        keep.setVodPic(getPic());
        keep.setVodName(getTitleText());
        keep.setSiteName(getSiteNameText());
        keep.setCreateTime(System.currentTimeMillis());
        keep.save();
    }

    private void updateKeep() {
        if (!canUseVodRecord()) return;
        String playbackKey = getPlaybackKey();
        String siteKey = getSiteKey();
        String vodId = getVodIdFromPlaybackKey();
        String title = getTitleText();
        String pic = getPic();
        String siteName = getSiteNameText();
        Task.execute(() -> {
            Keep keep = findAudioKeep(playbackKey, siteKey, vodId);
            if (keep == null) return;
            if (!playbackKey.equals(keep.getKey())) {
                keep.delete();
                keep.setKey(playbackKey);
            }
            keep.setCid(VodConfig.getCid());
            keep.setVodName(title);
            keep.setVodPic(pic);
            keep.setSiteName(siteName);
            keep.save();
        });
    }

    private void updateKeepState() {
        boolean enable = canUseVodRecord();
        binding.keep.setVisibility(enable ? View.VISIBLE : View.GONE);
        binding.keep.setFocusable(enable);
        if (enable) binding.keep.setImageResource(findCurrentKeep() == null ? R.drawable.ic_audio_keep_off : R.drawable.ic_audio_keep_on);
    }

    private void applyManualLyric(int request, LyricUtil.Option option, String title, String subtitle, long duration) {
        if (!isCurrentLyricSearch(request, title, subtitle)) return;
        LyricUtil.save(title, subtitle, duration, option.getLyric());
        lyricOffset = 0;
        saveLyricOffset();
        if (applyLyrics(option.getLyric())) rememberLyric(option.getLyric());
        updateAudioToolState();
        Notify.show("已应用歌词");
    }

    private boolean isCurrentLyricSearch(int request, String title, String subtitle) {
        return isActivityAlive() && request == lyricRequest && TextUtils.equals(title, getTitleText()) && TextUtils.equals(subtitle, getSubtitleText());
    }

    private long getSavedLyricOffset() {
        return Prefers.getLong(getLyricOffsetKey());
    }

    private void saveLyricOffset() {
        Prefers.put(getLyricOffsetKey(), lyricOffset);
    }

    private String getLyricOffsetKey() {
        return PREF_LYRIC_OFFSET_PREFIX + com.github.catvod.utils.Util.md5(getPlaybackKey() + "|" + getTitleText() + "|" + getSubtitleText());
    }

    private void toggleCoverBackground() {
        coverBackground = !coverBackground;
        Prefers.put(PREF_COVER_BACKGROUND, coverBackground);
        setCover();
        updateAudioToolState();
        Notify.show(coverBackground ? "已使用封面背景" : "已关闭封面背景");
    }

    private void toggleDiscLyricMode() {
        if (!lyricEnabled) {
            Notify.show("当前音频暂无歌词");
            return;
        }
        setDiscMode(!discMode);
    }

    private void setDiscMode(boolean disc) {
        discMode = disc;
        if (!disc && discSpinning) {
            binding.discCover.clearAnimation();
            discSpinning = false;
        }
        updateAudioToolState();
        updateDiscRotation();
    }

    private void updateDiscRotation() {
        if (discRotateAnimation == null) return;
        PlayerManager manager = player();
        boolean shouldRotate = discMode && manager != null && !manager.isReleased() && manager.isPlaying();
        if (shouldRotate) {
            if (!discSpinning) {
                binding.discCover.clearAnimation();
                binding.discCover.startAnimation(discRotateAnimation);
                discSpinning = true;
            }
        } else {
            if (discSpinning) {
                binding.discCover.clearAnimation();
                discSpinning = false;
            }
        }
    }

    private void syncPlaybackUI() {
        PlayerManager manager = player();
        if (manager == null || manager.isReleased()) return;
        int state = manager.getPlaybackState();
        binding.loading.setVisibility(state == Player.STATE_BUFFERING ? View.VISIBLE : View.GONE);
        updatePlayIcon();
        updateDiscRotation();
    }

    private void updateAudioToolState() {
        binding.discContainer.setVisibility(discMode ? View.VISIBLE : View.GONE);
        binding.discLyricPreview.setVisibility(discMode && lyricEnabled ? View.VISIBLE : View.GONE);
        binding.lyricPanel.setVisibility(!discMode && lyricEnabled ? View.VISIBLE : View.GONE);
        binding.lyricAdvance.setVisibility(lyricEnabled ? View.VISIBLE : View.GONE);
        binding.lyricDelay.setVisibility(lyricEnabled ? View.VISIBLE : View.GONE);
        binding.lyricSearch.setVisibility(lyricEnabled ? View.VISIBLE : View.GONE);
        binding.discModeToggle.setVisibility(lyricEnabled ? View.VISIBLE : View.GONE);
        binding.lyricAdvance.setFocusable(lyricEnabled);
        binding.lyricDelay.setFocusable(lyricEnabled);
        binding.lyricSearch.setFocusable(lyricEnabled);
        binding.discModeToggle.setFocusable(lyricEnabled);
        if (!lyricEnabled) return;
        binding.lyricAdvance.setAlpha(lyricOffset < 0 ? 1f : 0.72f);
        binding.lyricDelay.setAlpha(lyricOffset > 0 ? 1f : 0.72f);
        binding.lyricSearch.setAlpha(0.82f);
        binding.discModeToggle.setAlpha(discMode ? 1f : 0.72f);
        binding.discModeToggle.setImageResource(discMode ? R.drawable.ic_audio_lyric_mode : R.drawable.ic_audio_disc_mode);
        binding.coverBackground.setAlpha(coverBackground ? 1f : 0.48f);
    }

    private void setText() {
        binding.title.setText(getDisplayTitleText());
        binding.subtitle.setText(getDisplaySubtitleText());
        binding.subtitle.setVisibility(TextUtils.isEmpty(getDisplaySubtitleText()) ? View.GONE : View.VISIBLE);
        if (!lyricEnabled) {
            setLyricTexts("", "", "", "", "");
            return;
        }
        setLyricTexts("", "", lyrics == null || lyrics.isEmpty() ? "暂无歌词" : "", "", "");
    }

    private void setLyricTexts(String prev2, String prev, String current, String next, String next2) {
        binding.lyricPrev2.setText(prev2);
        binding.lyricPrev.setText(prev);
        binding.lyric.setText(current);
        binding.lyricNext.setText(next);
        binding.lyricNext2.setText(next2);
        updateDiscLyricPreview(prev, current, next);
    }

    private void updateDiscLyricPreview(String prev, String current, String next) {
        boolean show = discMode && lyricEnabled && !TextUtils.isEmpty(current);
        binding.discLyricPreview.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (show) {
            binding.discLyricPrev.setText(TextUtils.isEmpty(prev) ? "" : prev);
            binding.discLyricCurrent.setText(current);
            binding.discLyricNext.setText(TextUtils.isEmpty(next) ? "" : next);
        }
    }

    private void setCover() {
        String pic = getPic();
        if (TextUtils.isEmpty(pic)) {
            binding.cover.setImageResource(R.drawable.ic_audio_note);
            binding.discCover.setImageResource(R.drawable.ic_audio_note);
            loadDefaultBackdrop();
            return;
        }
        ImgUtil.load(getDisplayTitleText(), pic, binding.cover);
        ImgUtil.load(getDisplayTitleText(), pic, binding.discCover);
        if (coverBackground) ImgUtil.load(getDisplayTitleText(), pic, binding.backdrop);
        else loadDefaultBackdrop();
    }

    private void loadDefaultBackdrop() {
        int wall = Setting.getWall();
        int type = Setting.getWallType();

        // Built-in color
        if (type == 0 && Setting.isBuiltInColorWall(wall)) {
            binding.backdrop.setImageDrawable(new ColorDrawable(Setting.getBuiltInWallColor(wall)));
            return;
        }

        // Built-in design
        if (type == 0 && Setting.isBuiltInDesignWall(wall)) {
            int resId = CustomWallView.getDesignResId(wall);
            if (resId != 0) {
                binding.backdrop.setImageResource(resId);
                return;
            }
        }

        // Green (legacy)
        if (type == 0 && wall == Setting.WALL_GREEN) {
            binding.backdrop.setImageResource(R.drawable.wallpaper_1);
            return;
        }

        // Custom image cache
        File wallCache = FileUtil.getWallCache();
        if (wallCache.exists()) {
            try {
                Glide.with(this).load(wallCache).centerCrop().into(binding.backdrop);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Fallback
        binding.backdrop.setImageResource(R.drawable.wallpaper_4);
    }

    private void updatePlayIcon() {
        PlayerManager manager = player();
        if (manager == null || manager.isReleased()) return;
        binding.play.setImageResource(manager.isPlaying() ? R.drawable.ic_audio_pause : R.drawable.ic_audio_play);
    }

    private void updatePlayIcon(boolean isPlaying) {
        binding.play.setImageResource(isPlaying ? R.drawable.ic_audio_pause : R.drawable.ic_audio_play);
    }

    private void loadLyrics(Result result) {
        if (!lyricEnabled) return;
        int request = ++lyricRequest;
        lyricBrowsing = false;
        App.removeCallbacks(lyricBrowseReturn);
        String lrc = result == null ? "" : result.getLrc();
        String title = getTitleText();
        String subtitle = getSubtitleText();
        long duration = getPlayerDuration();
        lyrics = new ArrayList<>();
        currentLyricIndex = 0;
        updateAudioToolState();
        setLyricTexts("", "", TextUtils.isEmpty(lrc) ? "正在匹配歌词" : "正在加载歌词", "", "");
        Task.execute(() -> {
            String text = LyricUtil.loadProvided(lrc);
            if (parseLyrics(text).isEmpty()) text = LyricUtil.find(title, subtitle, duration);
            String lyric = text;
            App.post(() -> {
                if (request != lyricRequest) return;
                if (applyLyrics(lyric)) rememberLyric(lyric);
            });
        });
    }

    private void loadArtwork() {
        int request = ++artworkRequest;
        String title = getTitleText();
        String subtitle = getSubtitleText();
        long duration = getPlayerDuration();
        Task.execute(() -> {
            String artwork = LyricUtil.findArtwork(title, subtitle, duration);
            App.post(() -> {
                if (request != artworkRequest || TextUtils.isEmpty(artwork)) return;
                if (artwork.equals(getPic())) return;
                getIntent().putExtra(EXTRA_PIC, artwork);
                setCover();
                PlayerManager manager = player();
                if (manager != null) manager.setMetadata(buildAudioMetadata());
            });
        });
    }

    private MediaMetadata buildAudioMetadata() {
        return PlayerManager.buildMetadata(getDisplayTitleText(), getDisplaySubtitleText(), getPic());
    }

    private boolean applyLyrics(String lrc) {
        if (!lyricEnabled) return false;
        lyrics = parseLyrics(lrc);
        lyricBrowsing = false;
        App.removeCallbacks(lyricBrowseReturn);
        if (lyrics.isEmpty()) {
            currentLyricIndex = 0;
            setLyricTexts("", "", "暂无歌词", "", "");
            updateAudioToolState();
            return false;
        }
        updateLyric(getPlayerPosition());
        updateAudioToolState();
        return true;
    }

    private void rememberLyric(String lrc) {
        if (TextUtils.isEmpty(lrc)) return;
        if (initialResult != null) initialResult.setLrc(lrc);
    }

    private long getPlayerPosition() {
        PlayerManager manager = player();
        return manager == null || manager.isReleased() ? 0 : Math.max(0, manager.getPosition());
    }

    private long getPlayerDuration() {
        PlayerManager manager = player();
        if (manager == null || manager.isReleased()) return 0;
        long duration = manager.getDuration();
        return duration == C.TIME_UNSET ? 0 : duration;
    }

    private boolean isActivityAlive() {
        return !isFinishing() && !isDestroyed();
    }

    private List<Lyric> parseLyrics(String lrc) {
        List<Lyric> items = new ArrayList<>();
        if (TextUtils.isEmpty(lrc)) return items;
        long fileOffset = parseLyricOffset(lrc);
        for (String line : lrc.split("\\r?\\n")) {
            Matcher matcher = LRC_TIME.matcher(line);
            List<Long> times = new ArrayList<>();
            int end = 0;
            while (matcher.find()) {
                times.add(parseLyricTime(matcher));
                end = matcher.end();
            }
            String text = end <= 0 || end > line.length() ? "" : line.substring(end).trim();
            if (text.isEmpty()) continue;
            for (Long time : times) items.add(new Lyric(Math.max(0, time + fileOffset), text));
        }
        items.sort((a, b) -> Long.compare(a.time, b.time));
        return items;
    }

    private void disableLyrics() {
        lyricRequest++;
        lyricBrowsing = false;
        lyrics = new ArrayList<>();
        currentLyricIndex = 0;
        App.removeCallbacks(lyricBrowseReturn);
        setLyricTexts("", "", "", "", "");
        updateAudioToolState();
    }

    private boolean shouldEnableLyrics(Result result) {
        if (result != null && !TextUtils.isEmpty(result.getLrc())) return true;
        return !isNonSongAudio();
    }

    private boolean isNonSongAudio() {
        String text = normalizeAudioText(getSourceText() + " " + getTitleText() + " " + getSubtitleText());
        if (containsAny(text, NON_SONG_KEYWORDS)) return true;
        if (NON_SONG_EPISODE.matcher(text).find() && !containsAny(text, SONG_KEYWORDS)) return true;
        return episodes != null && episodes.size() >= 20 && looksLikeSerialAudio(text) && !containsAny(text, SONG_KEYWORDS);
    }

    private boolean looksLikeSerialAudio(String text) {
        return text.contains("第") || text.contains("集") || text.contains("章") || text.contains("回") || text.contains("期") || text.contains("讲");
    }

    private String getSourceText() {
        try {
            String siteKey = getSiteKey();
            if (TextUtils.isEmpty(siteKey)) return "";
            Site site = VodConfig.get().getSite(siteKey);
            if (site == null) return siteKey;
            return siteKey + " " + site.getName() + " " + String.join(" ", site.getCategories());
        } catch (Throwable e) {
            return "";
        }
    }

    private boolean containsAny(String text, String[] keywords) {
        if (TextUtils.isEmpty(text)) return false;
        for (String keyword : keywords) {
            if (!TextUtils.isEmpty(keyword) && text.contains(keyword.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String normalizeAudioText(String text) {
        return TextUtils.isEmpty(text) ? "" : text.trim().toLowerCase(Locale.ROOT);
    }

    private long parseLyricTime(Matcher matcher) {
        long minute = Long.parseLong(Objects.toString(matcher.group(1), "0"));
        long second = Long.parseLong(Objects.toString(matcher.group(2), "0"));
        String msText = Objects.toString(matcher.group(3), "0");
        long ms = msText.length() == 1 ? Long.parseLong(msText) * 100 : msText.length() == 2 ? Long.parseLong(msText) * 10 : Long.parseLong(msText);
        return (minute * 60 + second) * 1000 + ms;
    }

    private long parseLyricOffset(String lrc) {
        try {
            Matcher matcher = LRC_OFFSET.matcher(lrc);
            return matcher.find() ? Long.parseLong(Objects.toString(matcher.group(1), "0")) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private void updateLyricAndHistory() {
        PlayerManager manager = player();
        if (manager == null || manager.isReleased()) return;
        updateLyric(Math.max(0, manager.getPosition()));
        syncHistoryProgress(manager);
    }

    private void updateLyric(long position) {
        if (lyrics == null || lyrics.isEmpty()) return;
        if (lyricBrowsing) return;
        position = Math.max(0, position - lyricOffset);
        int current = 0;
        for (int i = 0; i < lyrics.size(); i++) {
            if (position < lyrics.get(i).time) break;
            current = i;
        }
        displayLyricIndex(current);
    }

    private void displayLyricIndex(int current) {
        currentLyricIndex = current;
        setLyricTexts(
                getLyricText(current - 2),
                getLyricText(current - 1),
                getLyricText(current),
                getLyricText(current + 1),
                getLyricText(current + 2));
    }

    private String getLyricText(int index) {
        return lyrics == null || index < 0 || index >= lyrics.size() ? "" : lyrics.get(index).text;
    }

    private void applyResumePositionOnce(PlayerManager manager) {
        if (resumeApplied) return;
        resumeApplied = true;
        if (!canUseVodRecord()) return;
        History history = AudioHistory.find(getSiteKey(), getVodIdFromPlaybackKey());
        if (history == null) return;
        Episode episode = getCurrentEpisode();
        String remarks = episode == null ? getSubtitleText() : episode.getDisplayName();
        String episodeUrl = episode == null ? "" : episode.getUrl();
        boolean sameTrack = (!TextUtils.isEmpty(episodeUrl) && TextUtils.equals(episodeUrl, history.getEpisodeUrl()))
                || (!TextUtils.isEmpty(remarks) && TextUtils.equals(remarks, history.getVodRemarks()));
        if (!sameTrack) return;
        long position = history.getPosition();
        long duration = manager.getDuration();
        if (position <= 0) return;
        if (duration > 0 && position >= duration - 5000) return;
        manager.seekTo(position);
    }

    private void updateHistoryForTrack(PlayerManager manager) {
        if (manager == null || manager.isReleased()) return;
        if (!canUseVodRecord()) return;
        AudioHistory.saveTrack(buildHistoryRecord(), manager.getPosition(), manager.getDuration());
        updateKeep();
    }

    private AudioHistory.Record buildHistoryRecord() {
        Episode episode = getCurrentEpisode();
        String vodRemarks = episode == null ? getSubtitleText() : episode.getDisplayName();
        String episodeUrl = episode == null ? "" : episode.getUrl();
        return new AudioHistory.Record(getPlaybackKey(), getSiteKey(), getFlag(), getTitleText(), getPic(), vodRemarks, episodeUrl);
    }

    private void syncHistoryProgress(PlayerManager manager) {
        if (!canUseVodRecord()) return;
        long position = manager.getPosition();
        long duration = manager.getDuration();
        if (position <= 0 || duration <= 0) return;
        long now = System.currentTimeMillis();
        if (now - lastHistorySync < 5000) return;
        lastHistorySync = now;
        AudioHistory.syncProgress(getPlaybackKey(), getSiteKey(), position, duration);
    }

    private Episode getCurrentEpisode() {
        return episodes == null || episodes.isEmpty() || index < 0 || index >= episodes.size() ? null : episodes.get(index);
    }

    private boolean canUseVodRecord() {
        return AudioHistory.canUse(getPlaybackKey(), getSiteKey());
    }

    private Keep findCurrentKeep() {
        return findAudioKeep(getPlaybackKey(), getSiteKey(), getVodIdFromPlaybackKey());
    }

    private static Keep findAudioKeep(String playbackKey, String siteKey, String vodId) {
        Keep keep = Keep.find(playbackKey);
        if (keep == null && !TextUtils.isEmpty(siteKey) && !TextUtils.isEmpty(vodId)) keep = Keep.find(VodConfig.getCid(), AudioHistory.buildLegacyPlaybackKey(siteKey, vodId));
        return keep;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getHeaders() {
        Serializable value = getIntent().getSerializableExtra(EXTRA_HEADERS);
        return value instanceof Map ? (Map<String, String>) value : null;
    }

    private ArrayList<Episode> getCachedEpisodes() {
        ArrayList<Episode> cached = TextUtils.isEmpty(objectCacheKey) ? null : EPISODE_CACHE.get(objectCacheKey);
        if (cached != null) return cached;
        ArrayList<Episode> fallback = getIntent().getParcelableArrayListExtra(EXTRA_EPISODES);
        return fallback == null ? new ArrayList<>() : fallback;
    }

    private String getCachedResult() {
        String cached = TextUtils.isEmpty(objectCacheKey) ? "" : RESULT_CACHE.get(objectCacheKey);
        if (!TextUtils.isEmpty(cached)) return cached;
        return Objects.toString(getIntent().getStringExtra(EXTRA_RESULT), "");
    }

    private String getSiteKey() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_SITE_KEY), "");
    }

    private String getSiteNameText() {
        Site site = VodConfig.get().getSite(getSiteKey());
        String name = site == null ? "" : site.getDisplayName();
        return TextUtils.isEmpty(name) ? getSiteKey() : name;
    }

    private String getVodIdFromPlaybackKey() {
        return AudioHistory.getVodId(getPlaybackKey());
    }

    private String getFlag() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_FLAG), "");
    }

    private String getTitleText() {
        String title = Objects.toString(getIntent().getStringExtra(EXTRA_TITLE), "");
        return TextUtils.isEmpty(title) ? "音频播放" : title;
    }

    private String getDisplayTitleText() {
        return getDisplayText().title;
    }

    private String getDisplaySubtitleText() {
        return getDisplayText().subtitle;
    }

    private DisplayText getDisplayText() {
        return buildDisplayText(getTitleText(), getSubtitleText());
    }

    private static DisplayText buildDisplayText(String title, String subtitle) {
        String targetTitle = Objects.toString(title, "").trim();
        String targetSubtitle = Objects.toString(subtitle, "").trim();
        if (TextUtils.isEmpty(targetTitle)) targetTitle = "音频播放";
        if (isGenericTitle(targetTitle) && !TextUtils.isEmpty(targetSubtitle)) return splitTrackText(targetSubtitle);
        if (!TextUtils.isEmpty(targetSubtitle) && targetTitle.equals(targetSubtitle)) return splitTrackText(targetTitle);
        return new DisplayText(targetTitle, targetSubtitle);
    }

    private static DisplayText splitTrackText(String text) {
        String value = Objects.toString(text, "").trim();
        String[] separators = {" - ", " -", "- ", " / ", "/", "·", "、"};
        for (String separator : separators) {
            int index = value.indexOf(separator);
            if (index <= 0) continue;
            String title = value.substring(0, index).trim();
            String artist = value.substring(index + separator.length()).trim();
            if (!TextUtils.isEmpty(title) && !TextUtils.isEmpty(artist)) return new DisplayText(title, artist);
        }
        return new DisplayText(value, "");
    }

    private static boolean isGenericTitle(String title) {
        return TITLE_PLAYLIST_DETAIL.equals(Objects.toString(title, "").trim());
    }

    private String getSubtitleText() {
        if (!episodes.isEmpty() && index >= 0 && index < episodes.size()) return episodes.get(index).getDisplayName();
        return Objects.toString(getIntent().getStringExtra(EXTRA_SUBTITLE), "");
    }

    private String getPic() {
        return Objects.toString(getIntent().getStringExtra(EXTRA_PIC), Objects.toString(getIntent().getStringExtra(EXTRA_VOD_PIC), ""));
    }

    private long getTimeout() {
        long timeout = getIntent().getLongExtra(EXTRA_TIMEOUT, Constant.TIMEOUT_PLAY);
        return timeout <= 0 ? Constant.TIMEOUT_PLAY : timeout;
    }

    @Override
    protected void onBackInvoked() {
        if (binding.playlistPanel.getVisibility() == View.VISIBLE) showPlaylist(false);
        else stopAndFinish();
    }

    @Override
    protected void onDestroy() {
        lyricRequest++;
        artworkRequest++;
        App.removeCallbacks(ticker);
        App.removeCallbacks(lyricBrowseReturn);
        if (!TextUtils.isEmpty(objectCacheKey)) {
            EPISODE_CACHE.remove(objectCacheKey);
            RESULT_CACHE.remove(objectCacheKey);
        }
        super.onDestroy();
    }

    private final class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.ViewHolder> {

        private final List<Episode> items = new ArrayList<>();
        private int current;

        void setItems(List<Episode> episodes) {
            items.clear();
            if (episodes != null) items.addAll(episodes);
            notifyDataSetChanged();
            updatePlaylistState();
        }

        void setCurrent(int current) {
            int old = this.current;
            this.current = current;
            if (old >= 0 && old < getItemCount()) notifyItemChanged(old);
            if (current >= 0 && current < getItemCount()) notifyItemChanged(current);
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            TextView view = (TextView) LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            view.setGravity(Gravity.CENTER_VERTICAL);
            view.setMinHeight(ResUtil.dp2px(48));
            view.setPadding(ResUtil.dp2px(16), 0, ResUtil.dp2px(16), 0);
            view.setTextColor(ResUtil.getColor(R.color.white));
            view.setTextSize(14);
            view.setMaxLines(1);
            view.setEllipsize(TextUtils.TruncateAt.END);
            view.setFocusable(true);
            view.setBackgroundResource(R.drawable.shape_audio_playlist_item);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Episode item = items.get(position);
            holder.text.setText(String.format(Locale.getDefault(), "%02d  %s", position + 1, item.getDisplayName()));
            holder.text.setSelected(position == current);
            holder.text.setOnClickListener(view -> {
                showPlaylist(false);
                playIndex(holder.getBindingAdapterPosition(), true);
            });
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        final class ViewHolder extends RecyclerView.ViewHolder {

            final TextView text;

            ViewHolder(@NonNull TextView itemView) {
                super(itemView);
                text = itemView;
            }
        }
    }

    private static final class Lyric {

        private final long time;
        private final String text;

        private Lyric(long time, String text) {
            this.time = time;
            this.text = text;
        }
    }

    private static final class DisplayText {

        private final String title;
        private final String subtitle;

        private DisplayText(String title, String subtitle) {
            this.title = Objects.toString(title, "");
            this.subtitle = Objects.toString(subtitle, "");
        }
    }
}
