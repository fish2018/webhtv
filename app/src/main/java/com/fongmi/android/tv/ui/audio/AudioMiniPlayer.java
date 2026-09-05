package com.fongmi.android.tv.ui.audio;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Rect;
import android.os.IBinder;
import android.text.TextUtils;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.widget.NestedScrollView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.drm.FrameworkMediaDrm;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.activity.AudioActivity;
import com.fongmi.android.tv.utils.AudioUtil;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.utils.Prefers;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public final class AudioMiniPlayer implements ServiceConnection {

    private static final int MINI_PLAYER_MAX_WIDTH_DP = 520;
    private static final int MINI_PLAYER_SIDE_MARGIN_DP = 16;
    private static final int MINI_PLAYER_BOTTOM_MARGIN_DP = 28;
    private static final int MINI_PLAYER_BOTTOM_OBSTRUCTION_GAP_DP = 8;
    private static final int MINI_PLAYER_MAX_OBSTRUCTION_HEIGHT_DP = 112;
    private static final long HISTORY_READY_CHECK_DELAY = 1000L;
    private static final String TITLE_PLAYLIST_DETAIL = "歌单详情";
    private static final String PREF_POSITIONED = "audio_mini_player_positioned";
    private static final String PREF_POSITION_X = "audio_mini_player_x";
    private static final String PREF_POSITION_Y = "audio_mini_player_y";
    private static State state;
    private static boolean active;

    private final Runnable historyReadyChecker = this::ensureHistoryForReady;
    private final Activity activity;
    private final Random random;
    private PlaybackService service;
    private Player currentPlayer;
    private View root;
    private ImageView backdrop;
    private ImageView cover;
    private ImageView prev;
    private ImageView play;
    private ImageView next;
    private TextView title;
    private TextView subtitle;
    private TextView status;
    private float downRawX;
    private float downRawY;
    private float downViewX;
    private float downViewY;
    private int dragLeft;
    private int dragTop;
    private int dragRight;
    private int dragBottom;
    private int touchSlop;
    private boolean bound;
    private boolean loading;
    private boolean dragging;
    private boolean dragMoved;
    private String loadedPic;
    private String savedHistoryTrack;
    private Insets systemInsets = Insets.NONE;
    private int playRequest;

    public AudioMiniPlayer(Activity activity) {
        this.activity = activity;
        this.random = new Random();
    }

    public static boolean isActive() {
        return active && state != null;
    }

    public static void activate(State target, PlaybackService service) {
        state = target;
        active = target != null;
        if (service == null) return;
        service.setKeepAlive(active);
        if (active) service.setAudioHistoryRecord(target.historyRecord());
        else service.clearAudioHistoryRecord();
    }

    public static void deactivateForFull(PlaybackService service) {
        active = false;
        if (service == null) return;
        service.syncAudioHistoryProgress(true);
        service.clearAudioHistoryRecord();
        service.setKeepAlive(false);
    }

    public void onResume() {
        if (activity instanceof AudioActivity) return;
        ensureView();
        syncVisibility();
        if (isActive()) bind();
    }

    public void onPause() {
        unbind();
    }

    public void onDestroy() {
        unbind();
        removeView();
    }

    private void ensureView() {
        if (root != null) return;
        ViewGroup content = activity.findViewById(android.R.id.content);
        root = LayoutInflater.from(activity).inflate(R.layout.view_audio_mini_player, content, false);
        content.addView(root);
        touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        backdrop = root.findViewById(R.id.audioMiniBackdrop);
        cover = root.findViewById(R.id.audioMiniCover);
        prev = root.findViewById(R.id.audioMiniPrev);
        play = root.findViewById(R.id.audioMiniPlay);
        next = root.findViewById(R.id.audioMiniNext);
        title = root.findViewById(R.id.audioMiniTitle);
        subtitle = root.findViewById(R.id.audioMiniSubtitle);
        status = root.findViewById(R.id.audioMiniState);
        root.setOnClickListener(view -> openFullPlayer());
        root.setOnLongClickListener(this::startDrag);
        root.setOnTouchListener(this::onTouch);
        prev.setOnClickListener(view -> playPrev(true));
        play.setOnClickListener(view -> togglePlay());
        next.setOnClickListener(view -> playNext(true));
        root.findViewById(R.id.audioMiniClose).setOnClickListener(view -> close());
        applyInsets();
    }

    private void removeView() {
        if (root == null) return;
        ViewGroup parent = (ViewGroup) root.getParent();
        if (parent != null) parent.removeView(root);
        root = null;
    }

    private void applyInsets() {
        root.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateWindowBounds());
        if (root.getParent() instanceof View) {
            ((View) root.getParent()).addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> updateWindowBounds());
        }
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            systemInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            updateWindowBounds();
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
        root.post(() -> ViewCompat.requestApplyInsets(root));
    }

    private void updateWindowBounds() {
        if (root == null || !(root.getParent() instanceof View)) return;
        View parent = (View) root.getParent();
        int width = Math.max(0, parent.getWidth());
        int side = ResUtil.dp2px(MINI_PLAYER_SIDE_MARGIN_DP);
        int maxWidth = ResUtil.dp2px(MINI_PLAYER_MAX_WIDTH_DP);
        int bottomObstruction = findBottomObstruction(parent);
        dragLeft = side + systemInsets.left;
        dragTop = side + systemInsets.top;
        dragRight = side + systemInsets.right;
        dragBottom = ResUtil.dp2px(MINI_PLAYER_BOTTOM_MARGIN_DP) + systemInsets.bottom + bottomObstruction;
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) root.getLayoutParams();
        int targetWidth = width > 0 ? Math.min(maxWidth, Math.max(0, width - dragLeft - dragRight)) : params.width;
        boolean changed = params.leftMargin != dragLeft || params.rightMargin != dragRight || params.bottomMargin != dragBottom || params.width != targetWidth;
        if (changed) {
            params.leftMargin = dragLeft;
            params.rightMargin = dragRight;
            params.bottomMargin = dragBottom;
            params.width = targetWidth;
            root.setLayoutParams(params);
        }
        root.post(this::applySavedPosition);
    }

    private int findBottomObstruction(View parent) {
        if (!(parent instanceof ViewGroup)) return 0;
        Rect parentRect = new Rect();
        if (!parent.getGlobalVisibleRect(parentRect)) return 0;
        int maxHeight = ResUtil.dp2px(MINI_PLAYER_MAX_OBSTRUCTION_HEIGHT_DP);
        int threshold = systemInsets.bottom + ResUtil.dp2px(MINI_PLAYER_BOTTOM_OBSTRUCTION_GAP_DP);
        return findBottomObstruction((ViewGroup) parent, parentRect, maxHeight, threshold);
    }

    private int findBottomObstruction(ViewGroup group, Rect parentRect, int maxHeight, int threshold) {
        if (isScrollableContent(group)) return 0;
        int result = 0;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == root || !child.isShown() || child.getAlpha() <= 0f) continue;
            result = Math.max(result, getBottomObstructionHeight(child, parentRect, maxHeight, threshold));
            if (child instanceof ViewGroup) result = Math.max(result, findBottomObstruction((ViewGroup) child, parentRect, maxHeight, threshold));
        }
        return result;
    }

    private int getBottomObstructionHeight(View view, Rect parentRect, int maxHeight, int threshold) {
        if (view.getHeight() <= 0 || view.getHeight() > maxHeight) return 0;
        Rect rect = new Rect();
        if (!view.getGlobalVisibleRect(rect)) return 0;
        if (rect.width() < parentRect.width() / 3) return 0;
        int bottomGap = Math.max(0, parentRect.bottom - rect.bottom);
        if (bottomGap > threshold) return 0;
        return Math.min(rect.height(), maxHeight);
    }

    private boolean isScrollableContent(View view) {
        return view instanceof RecyclerView || view instanceof NestedScrollView || view instanceof ScrollView || view instanceof HorizontalScrollView;
    }

    private boolean startDrag(View view) {
        dragging = true;
        dragMoved = false;
        downViewX = root.getX();
        downViewY = root.getY();
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        if (view.getParent() != null) view.getParent().requestDisallowInterceptTouchEvent(true);
        view.animate().scaleX(1.03f).scaleY(1.03f).setDuration(90).start();
        return true;
    }

    private boolean onTouch(View view, MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN -> {
                downRawX = event.getRawX();
                downRawY = event.getRawY();
                downViewX = view.getX();
                downViewY = view.getY();
                dragMoved = false;
                return false;
            }
            case MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false;
                float dx = event.getRawX() - downRawX;
                float dy = event.getRawY() - downRawY;
                dragMoved = dragMoved || Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop;
                moveTo(downViewX + dx, downViewY + dy);
                return true;
            }
            case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false;
                finishDrag(event.getActionMasked() == MotionEvent.ACTION_UP);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void finishDrag(boolean save) {
        dragging = false;
        root.animate().scaleX(1f).scaleY(1f).setDuration(120).start();
        if (root.getParent() != null) root.getParent().requestDisallowInterceptTouchEvent(false);
        if (!save || !dragMoved) return;
        Prefers.put(PREF_POSITIONED, true);
        Prefers.put(PREF_POSITION_X, root.getX());
        Prefers.put(PREF_POSITION_Y, root.getY());
    }

    private void moveTo(float x, float y) {
        if (root == null) return;
        root.setX(clampX(x));
        root.setY(clampY(y));
    }

    private void applySavedPosition() {
        if (root == null || dragging) return;
        if (!Prefers.getBoolean(PREF_POSITIONED)) {
            root.setTranslationX(0f);
            root.setTranslationY(0f);
            return;
        }
        moveTo(Prefers.getFloat(PREF_POSITION_X, root.getX()), Prefers.getFloat(PREF_POSITION_Y, root.getY()));
    }

    private float clampX(float x) {
        if (root == null || root.getParent() == null) return x;
        int parentWidth = ((View) root.getParent()).getWidth();
        int width = root.getWidth();
        if (parentWidth <= 0 || width <= 0) return x;
        float min = dragLeft;
        float max = Math.max(min, parentWidth - dragRight - width);
        return Math.max(min, Math.min(max, x));
    }

    private float clampY(float y) {
        if (root == null || root.getParent() == null) return y;
        int parentHeight = ((View) root.getParent()).getHeight();
        int height = root.getHeight();
        if (parentHeight <= 0 || height <= 0) return y;
        float min = dragTop;
        float max = Math.max(min, parentHeight - dragBottom - height);
        return Math.max(min, Math.min(max, y));
    }

    private void bind() {
        if (bound || !PlaybackService.isRunning()) return;
        Intent intent = new Intent(activity, PlaybackService.class).setAction(PlaybackService.LOCAL_BIND_ACTION);
        activity.bindService(intent, this, Context.BIND_AUTO_CREATE);
        bound = true;
    }

    private void unbind() {
        if (!bound) return;
        syncHistoryProgress(true);
        App.removeCallbacks(historyReadyChecker);
        detachPlayer();
        if (service != null) {
            service.removePlayerCallback(playerCallback);
            service.clearNavigationCallback(navigationCallback);
        }
        activity.unbindService(this);
        service = null;
        bound = false;
    }

    private void syncVisibility() {
        if (root == null) return;
        if (isActive()) {
            root.setVisibility(View.VISIBLE);
            updateWindowBounds();
            root.setAlpha(0f);
            root.setScaleX(0.94f);
            root.setScaleY(0.94f);
            root.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
            updateViews();
        } else {
            root.setVisibility(View.GONE);
        }
    }

    private void updateViews() {
        if (root == null || !isActive()) return;
        PlayerManager manager = service == null ? null : service.player();
        boolean playing = manager != null && !manager.isReleased() && manager.isPlaying();
        DisplayText text = state.displayText();
        title.setText(text.title);
        subtitle.setText(text.subtitle);
        subtitle.setVisibility(TextUtils.isEmpty(text.subtitle) ? View.GONE : View.VISIBLE);
        status.setText(loading ? "正在加载" : playing ? "正在播放" : "已暂停");
        play.setImageResource(playing ? R.drawable.ic_audio_pause : R.drawable.ic_audio_play);
        float enabled = state.episodes.size() > 1 ? 1f : 0.45f;
        prev.setAlpha(enabled);
        next.setAlpha(enabled);
        updateArtwork();
    }

    private void updateArtwork() {
        if (root == null || state == null || TextUtils.equals(loadedPic, state.pic)) return;
        loadedPic = state.pic;
        if (TextUtils.isEmpty(state.pic)) {
            cover.setImageResource(R.drawable.ic_audio_note);
            backdrop.setImageResource(R.drawable.wallpaper_4);
        } else {
            ImgUtil.load(state.displayTitle(), state.pic, cover);
            ImgUtil.load(state.displayTitle(), state.pic, backdrop);
        }
    }

    private void setLoading(boolean loading) {
        this.loading = loading;
        updateViews();
    }

    private void togglePlay() {
        if (service == null || service.player() == null || service.player().isReleased()) return;
        Player player = service.player().getPlayer();
        if (player.isPlaying()) {
            service.player().pause();
        } else {
            if (!service.player().isEmpty() && player.getPlaybackState() == Player.STATE_IDLE) player.prepare();
            service.player().play();
        }
        updateViews();
    }

    private void playPrev(boolean notify) {
        if (state == null) return;
        if (state.mode == 1) playIndex(randomIndex(), notify);
        else playIndex(state.index - 1, notify);
    }

    private void playNext(boolean notify) {
        if (state == null) return;
        if (state.mode == 1) playIndex(randomIndex(), notify);
        else playIndex(state.index + 1, notify);
    }

    private int randomIndex() {
        if (state == null || state.episodes.size() <= 1) return state == null ? 0 : state.index;
        int target;
        do {
            target = random.nextInt(state.episodes.size());
        } while (target == state.index);
        return target;
    }

    private void playIndex(int target, boolean notify) {
        if (service == null || state == null) return;
        if (state.episodes.isEmpty()) {
            if (notify) Notify.show("没有更多音频");
            return;
        }
        if (target < 0 || target >= state.episodes.size()) {
            if (notify) Notify.show(target < 0 ? "已经是第一首" : "已经是最后一首");
            return;
        }
        int request = ++playRequest;
        state.index = target;
        setLoading(true);
        Result cached = target == state.resultIndex ? state.copyResult() : null;
        if (cached != null && !cached.getRealUrl().isEmpty()) {
            playCurrent(cached, target);
            return;
        }
        if (TextUtils.isEmpty(state.siteKey) || TextUtils.isEmpty(state.flag)) {
            setLoading(false);
            Notify.show("缺少音频列表来源");
            return;
        }
        Episode episode = state.episodes.get(target);
        FluentFuture<Result> future = FluentFuture.from(Task.executor().submit(() -> SiteApi.playerContent(state.siteKey, state.flag, episode.getUrl()))).withTimeout(state.timeout, TimeUnit.MILLISECONDS, Task.scheduler());
        future.addCallback(Task.callback(
                result -> App.post(() -> {
                    if (isCurrentRequest(request, target)) playCurrent(result, target);
                }),
                error -> App.post(() -> {
                    if (!isCurrentRequest(request, target)) return;
                    setLoading(false);
                    Notify.show("音频加载失败");
                })
        ), MoreExecutors.directExecutor());
    }

    private boolean isCurrentRequest(int request, int target) {
        return state != null && playRequest == request && state.index == target;
    }

    private void playCurrent(Result result, int targetIndex) {
        if (service == null || state == null) return;
        Result target = prepareResult(result);
        if (target == null || target.getRealUrl().isEmpty()) {
            setLoading(false);
            Notify.show("音频地址为空");
            return;
        }
        state.index = targetIndex;
        if (target.hasArtwork()) {
            state.pic = target.getArtwork();
            loadedPic = null;
        }
        state.result = cloneResult(target);
        state.resultIndex = state.index;
        MediaMetadata metadata = PlayerManager.buildMetadata(state.displayTitle(), state.displaySubtitle(), state.pic);
        if (!startPlayer(target, metadata)) {
            setLoading(false);
            return;
        }
        service.player().play();
        service.setAudioHistoryRecord(buildHistoryRecord());
        scheduleHistoryReadyCheck();
        setLoading(false);
    }

    private Result prepareResult(Result result) {
        if (result == null) return null;
        Result target = cloneResult(result);
        if (!target.getUrl().isEmpty()) target.setUrl(AudioUtil.cleanUrl(target.getUrl().v()));
        if (target.getHeader().isEmpty()) target.setHeader(state.headers);
        return target;
    }

    private boolean startPlayer(Result result, MediaMetadata metadata) {
        if (result.getDrm() != null && !FrameworkMediaDrm.isCryptoSchemeSupported(result.getDrm().getUUID())) {
            Notify.show(ResUtil.getString(R.string.error_play_drm));
            return false;
        } else if (result.hasMsg()) {
            Notify.show(result.getMsg());
            return false;
        } else if (result.getRealUrl().isEmpty()) {
            Notify.show(ResUtil.getString(R.string.error_play_url));
            return false;
        } else if (result.needParse() || result.shouldUseParse()) {
            service.player().parse(state.playbackKey, result, result.shouldUseParse(), metadata);
        } else {
            service.player().start(PlaySpec.from(result, state.playbackKey, metadata), state.timeout);
        }
        return true;
    }

    private void updateHistoryForReady() {
        PlayerManager manager = service == null ? null : service.player();
        if (manager == null || manager.isReleased() || state == null || state.index != state.resultIndex) return;
        savedHistoryTrack = currentHistoryTrack();
        service.setAudioHistoryRecord(buildHistoryRecord());
    }

    private void ensureHistoryForReady() {
        PlayerManager manager = service == null ? null : service.player();
        if (manager == null || manager.isReleased() || manager.getPlaybackState() != Player.STATE_READY) return;
        String track = currentHistoryTrack();
        if (TextUtils.isEmpty(track) || TextUtils.equals(track, savedHistoryTrack)) return;
        updateHistoryForReady();
    }

    private void syncHistoryProgress(boolean force) {
        if (service != null) service.syncAudioHistoryProgress(force);
    }

    private AudioHistory.Record buildHistoryRecord() {
        return state == null ? null : state.historyRecord();
    }

    private String currentHistoryTrack() {
        return state == null || state.index != state.resultIndex ? "" : state.historyRecord().trackKey();
    }

    private void scheduleHistoryReadyCheck() {
        App.post(historyReadyChecker, HISTORY_READY_CHECK_DELAY);
    }

    private void openFullPlayer() {
        if (state == null) return;
        syncCurrentPlayback();
        State copy = state.copy();
        syncHistoryProgress(true);
        AudioActivity.startFromMini(activity, copy);
    }

    private void close() {
        syncHistoryProgress(true);
        App.removeCallbacks(historyReadyChecker);
        active = false;
        state = null;
        savedHistoryTrack = null;
        if (root != null) root.setVisibility(View.GONE);
        if (service != null) {
            service.clearAudioHistoryRecord();
            service.setKeepAlive(false);
            service.clearNavigationCallback(navigationCallback);
            service.shutdown();
        }
        unbind();
    }

    private void attachPlayer() {
        detachPlayer();
        if (service == null || service.player() == null || service.player().isReleased()) return;
        currentPlayer = service.player().getPlayer();
        currentPlayer.addListener(playerListener);
        syncCurrentPlayback();
    }

    private boolean syncCurrentPlayback() {
        PlayerManager manager = service == null ? null : service.player();
        if (manager == null || manager.isReleased() || state == null) return false;
        MediaItem item = manager.getCurrentMediaItem();
        String currentKey = manager.getKey();
        if (TextUtils.isEmpty(currentKey) && item != null) currentKey = item.mediaId;
        AudioPlaybackReconciliation.Match match = AudioPlaybackReconciliation.reconcile(currentKey, state.playbackKey, state.flag, state.index, state.episodes.size());
        if (!match.owned()) return false;
        boolean trackChanged = state.index != match.index();
        state.index = match.index();
        Result current = manager.getCurrentResult();
        if (current != null && !current.getRealUrl().isEmpty()) {
            state.result = cloneResult(current);
            state.resultIndex = state.index;
        }
        String artwork = current != null && current.hasArtwork() ? current.getArtwork() : "";
        MediaMetadata metadata = item == null ? manager.getMetadata() : item.mediaMetadata;
        if (TextUtils.isEmpty(artwork) && metadata != null && metadata.artworkUri != null) artwork = metadata.artworkUri.toString();
        if (!TextUtils.isEmpty(artwork) && !TextUtils.equals(state.pic, artwork)) {
            state.pic = artwork;
            loadedPic = null;
        }
        if (trackChanged) savedHistoryTrack = null;
        service.setNavigationCallback(navigationCallback, currentKey);
        service.setAudioHistoryRecord(buildHistoryRecord());
        updateViews();
        updateHistoryForReady();
        return true;
    }

    private void detachPlayer() {
        if (currentPlayer != null) currentPlayer.removeListener(playerListener);
        currentPlayer = null;
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((PlaybackService.LocalBinder) binder).getService();
        if (!isActive() || service.player() == null || service.player().isReleased() || service.player().isEmpty()) {
            close();
            return;
        }
        service.setKeepAlive(true);
        service.setNavigationCallback(navigationCallback, state.playbackKey);
        service.addPlayerCallback(playerCallback);
        service.setAudioHistoryRecord(buildHistoryRecord());
        attachPlayer();
        updateViews();
        updateHistoryForReady();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        App.removeCallbacks(historyReadyChecker);
        detachPlayer();
        service = null;
    }

    private final Player.Listener playerListener = new Player.Listener() {
        @Override
        public void onIsPlayingChanged(boolean isPlaying) {
            if (!isPlaying) syncHistoryProgress(true);
            updateViews();
        }

        @Override
        public void onMediaItemTransition(MediaItem mediaItem, int reason) {
            syncCurrentPlayback();
        }

        @Override
        public void onMediaMetadataChanged(MediaMetadata mediaMetadata) {
            syncCurrentPlayback();
        }

        @Override
        public void onPlaybackStateChanged(int playbackState) {
            setLoading(playbackState == Player.STATE_BUFFERING);
            if (playbackState == Player.STATE_READY) updateHistoryForReady();
            if (playbackState == Player.STATE_ENDED) syncHistoryProgress(true);
        }
    };

    private final PlaybackService.PlayerCallback playerCallback = new PlaybackService.PlayerCallback() {
        @Override
        public void onPrepare() {
            setLoading(true);
        }

        @Override
        public void onError(String msg) {
            setLoading(false);
            Notify.show(msg);
        }

        @Override
        public void onPlayerRebuild(Player player, boolean resetVideoSurface) {
            attachPlayer();
        }
    };

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
            close();
        }

        @Override
        public void onReplay() {
            if (service == null || service.player() == null) return;
            service.player().seekTo(0);
            service.player().play();
        }

        @Override
        public void onAudio() {
            openFullPlayer();
        }
    };

    private static Result cloneResult(Result result) {
        return result == null ? Result.empty() : Result.fromJson(Objects.toString(result, ""));
    }

    public static final class State {
        public final String playbackKey;
        public final String siteKey;
        public final String flag;
        public final String title;
        public final String fallbackSubtitle;
        public final ArrayList<Episode> episodes;
        public final Map<String, String> headers;
        public String pic;
        public Result result;
        public long timeout;
        public int index;
        public int resultIndex;
        public int mode;

        public State(String playbackKey, String siteKey, String flag, String title, String subtitle, String pic, ArrayList<Episode> episodes, int index, Result result, long timeout, Map<String, String> headers, int mode) {
            this.playbackKey = TextUtils.isEmpty(playbackKey) ? "audio:" + title : playbackKey;
            this.siteKey = Objects.toString(siteKey, "");
            this.flag = Objects.toString(flag, "");
            this.title = TextUtils.isEmpty(title) ? "音频播放" : title;
            this.fallbackSubtitle = Objects.toString(subtitle, "");
            this.pic = Objects.toString(pic, "");
            this.episodes = episodes == null ? new ArrayList<>() : new ArrayList<>(episodes);
            this.index = Math.max(0, Math.min(index, Math.max(this.episodes.size() - 1, 0)));
            this.result = cloneResult(result);
            this.resultIndex = this.index;
            this.timeout = timeout <= 0 ? Constant.TIMEOUT_PLAY : timeout;
            this.headers = headers == null ? new HashMap<>() : new HashMap<>(headers);
            this.mode = mode;
        }

        public String subtitle() {
            if (!episodes.isEmpty() && index >= 0 && index < episodes.size()) return episodes.get(index).getDisplayName();
            return fallbackSubtitle;
        }

        public Episode currentEpisode() {
            return episodes.isEmpty() || index < 0 || index >= episodes.size() ? null : episodes.get(index);
        }

        private AudioHistory.Record historyRecord() {
            Episode episode = currentEpisode();
            String vodRemarks = episode == null ? subtitle() : episode.getDisplayName();
            String episodeUrl = episode == null ? "" : episode.getUrl();
            return new AudioHistory.Record(playbackKey, siteKey, flag, title, pic, vodRemarks, episodeUrl);
        }

        private DisplayText displayText() {
            return buildDisplayText(title, subtitle());
        }

        private String displayTitle() {
            return displayText().title;
        }

        private String displaySubtitle() {
            return displayText().subtitle;
        }

        public Result copyResult() {
            return cloneResult(result);
        }

        public State copy() {
            State copy = new State(playbackKey, siteKey, flag, title, fallbackSubtitle, pic, episodes, index, result, timeout, headers, mode);
            copy.resultIndex = resultIndex;
            return copy;
        }

        public String playlistText() {
            return String.format(Locale.getDefault(), "%d/%d", episodes.isEmpty() ? 0 : index + 1, episodes.size());
        }
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

    private static final class DisplayText {

        private final String title;
        private final String subtitle;

        private DisplayText(String title, String subtitle) {
            this.title = Objects.toString(title, "");
            this.subtitle = Objects.toString(subtitle, "");
        }
    }
}
