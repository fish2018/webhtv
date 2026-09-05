package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.res.Configuration;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.datasource.HttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.ExoUtil;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.ui.activity.PlaybackActivity;
import com.fongmi.android.tv.ui.helper.TmdbVideoPlayback;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;

import java.util.concurrent.Future;

public final class TmdbVideoPlayerDialog extends DialogFragment implements Player.Listener {

    private static final String TAG = "TmdbVideoPlayerDialog";
    private static final String ARG_LAUNCH = "launch";
    private static final String ARG_RESUME_PARENT = "resume_parent";
    private static final String STATE_FULLSCREEN = "fullscreen";
    private static final int UNSET_SYSTEM_UI = Integer.MIN_VALUE;
    private static final long UNSET_RETRY_POSITION = C.TIME_UNSET;

    private TmdbVideoPlayback.Launch launch;
    private View container;
    private View surface;
    private PlayerView playerView;
    private ProgressBar loading;
    private TextView error;
    private TextView title;
    private ImageButton fullscreenButton;
    private ExoPlayer player;
    private Future<?> resolveFuture;
    private int generation;
    private int previousSystemUi = UNSET_SYSTEM_UI;
    private long retryPosition = UNSET_RETRY_POSITION;
    private boolean fullscreen;
    private boolean resumeParent;
    private boolean parentResumed;
    private boolean sourceRefreshAttempted;

    public static boolean show(FragmentActivity activity, TmdbVideoPlayback.Launch launch) {
        if (activity == null || launch == null || activity.isFinishing() || activity.isDestroyed()) return false;
        FragmentManager manager = activity.getSupportFragmentManager();
        Fragment current = manager.findFragmentByTag(TAG);
        if (current instanceof TmdbVideoPlayerDialog) {
            ((TmdbVideoPlayerDialog) current).replaceLaunch(launch);
            return true;
        }
        if (manager.isStateSaved()) return false;
        PlaybackActivity playback = activity instanceof PlaybackActivity ? (PlaybackActivity) activity : null;
        boolean resumeParent = playback != null && playback.pauseForOverlayPlayback();
        TmdbVideoPlayerDialog dialog = newInstance(launch, resumeParent);
        try {
            dialog.showNow(manager, TAG);
            return true;
        } catch (RuntimeException e) {
            if (playback != null) playback.resumeAfterOverlayPlayback(resumeParent);
            SpiderDebug.log("tmdb-video", e);
            return false;
        }
    }

    private static TmdbVideoPlayerDialog newInstance(TmdbVideoPlayback.Launch launch, boolean resumeParent) {
        TmdbVideoPlayerDialog dialog = new TmdbVideoPlayerDialog();
        Bundle args = new Bundle();
        args.putSerializable(ARG_LAUNCH, launch);
        args.putBoolean(ARG_RESUME_PARENT, resumeParent);
        dialog.setArguments(args);
        return dialog;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NO_TITLE, R.style.DialogFullScreen);
        Bundle state = savedInstanceState == null ? getArguments() : savedInstanceState;
        if (state != null) {
            launch = (TmdbVideoPlayback.Launch) state.getSerializable(ARG_LAUNCH);
            resumeParent = state.getBoolean(ARG_RESUME_PARENT, false);
            fullscreen = state.getBoolean(STATE_FULLSCREEN, false);
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnKeyListener((ignored, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) return false;
            if (fullscreen) setFullscreen(false);
            else dismissAllowingStateLoss();
            return true;
        });
        return dialog;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.dialog_tmdb_video_player, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        container = view.findViewById(R.id.tmdbVideoContainer);
        surface = view.findViewById(R.id.tmdbVideoSurface);
        playerView = view.findViewById(R.id.tmdbVideoPlayer);
        loading = view.findViewById(R.id.tmdbVideoLoading);
        error = view.findViewById(R.id.tmdbVideoError);
        title = view.findViewById(R.id.tmdbVideoTitle);
        ImageButton close = view.findViewById(R.id.tmdbVideoClose);
        fullscreenButton = view.findViewById(R.id.tmdbVideoFullscreen);
        close.setOnClickListener(v -> dismissAllowingStateLoss());
        fullscreenButton.setOnClickListener(v -> setFullscreen(!fullscreen));
        renderLaunch();
        if (launch == null) showError(ResUtil.getString(R.string.error_play_url));
        else resolve();
    }

    @Override
    public void onStart() {
        super.onStart();
        applyWindowMode();
        if (playerView != null) playerView.post(playerView::requestFocus);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(ARG_LAUNCH, launch);
        outState.putBoolean(ARG_RESUME_PARENT, resumeParent);
        outState.putBoolean(STATE_FULLSCREEN, fullscreen);
    }

    private void replaceLaunch(TmdbVideoPlayback.Launch next) {
        if (next == null) return;
        launch = next;
        retryPosition = UNSET_RETRY_POSITION;
        sourceRefreshAttempted = false;
        Bundle args = getArguments();
        if (args != null) args.putSerializable(ARG_LAUNCH, next);
        renderLaunch();
        if (getView() != null) resolve();
    }

    private void renderLaunch() {
        if (title != null) title.setText(launch == null ? "" : launch.getName());
    }

    private void resolve() {
        TmdbVideoPlayback.Launch launch = this.launch;
        if (launch == null) {
            showError(ResUtil.getString(R.string.error_play_url));
            return;
        }
        cancelResolve();
        releasePlayer();
        showLoading();
        int requestGeneration = ++generation;
        resolveFuture = Task.submit(() -> {
            Result result = null;
            Throwable failure = null;
            try {
                result = SiteApi.playerContentIsolated(launch.getKey(), launch.getPlayFlag(), launch.getPlayEpisodeUrl(), PlayerSetting.EXO);
            } catch (Throwable e) {
                failure = e;
            }
            Result resolved = result;
            Throwable error = failure;
            App.post(() -> {
                if (requestGeneration != generation || !isAdded() || getView() == null) return;
                resolveFuture = null;
                if (error != null) {
                    SpiderDebug.log("tmdb-video", error);
                    showError(message(error));
                    return;
                }
                if (resolved == null || TextUtils.isEmpty(resolved.getRealUrl())) {
                    showError(resolved != null && resolved.hasMsg() ? resolved.getMsg() : ResUtil.getString(R.string.error_play_url));
                    return;
                }
                preparePlayer(resolved, launch);
            });
        });
    }

    private void preparePlayer(Result result, TmdbVideoPlayback.Launch current) {
        try {
            MediaMetadata.Builder metadata = new MediaMetadata.Builder().setTitle(current.getName());
            if (!TextUtils.isEmpty(current.getPic())) metadata.setArtworkUri(Uri.parse(current.getPic()));
            PlaySpec spec = PlaySpec.from(result, current.getKey(), metadata.build()).checkUa();
            player = ExoUtil.buildPlayer(PlayerEngine.HARD, this);
            playerView.setPlayer(player);
            MediaItem item = ExoUtil.getMediaItem(spec, PlayerEngine.HARD);
            player.setMediaItem(item);
            long resumePosition = retryPosition;
            retryPosition = UNSET_RETRY_POSITION;
            if (resumePosition > 0) player.seekTo(resumePosition);
            player.prepare();
            player.play();
        } catch (RuntimeException e) {
            SpiderDebug.log("tmdb-video", e);
            releasePlayer();
            showError(message(e));
        }
    }

    private void showLoading() {
        if (loading != null) loading.setVisibility(View.VISIBLE);
        if (error != null) error.setVisibility(View.GONE);
    }

    private void showError(String message) {
        if (loading != null) loading.setVisibility(View.GONE);
        if (error == null) return;
        error.setText(TextUtils.isEmpty(message) ? ResUtil.getString(R.string.error_play_url) : message);
        error.setVisibility(View.VISIBLE);
    }

    private String message(Throwable error) {
        String message = error == null ? "" : error.getMessage();
        return TextUtils.isEmpty(message) ? ResUtil.getString(R.string.error_play_url) : message;
    }

    @Override
    public void onPlaybackStateChanged(int state) {
        if (state == Player.STATE_BUFFERING) {
            if (loading != null) loading.setVisibility(View.VISIBLE);
        } else if (state == Player.STATE_READY || state == Player.STATE_ENDED) {
            if (loading != null) loading.setVisibility(View.GONE);
        }
    }

    @Override
    public void onPlayerError(@NonNull PlaybackException error) {
        if (retryExpiredSource(error)) return;
        showError(message(error));
    }

    private boolean retryExpiredSource(PlaybackException error) {
        int status = httpStatus(error);
        if (status != 403 && status != 410) return false;
        if (sourceRefreshAttempted || launch == null || resolveFuture != null) return false;
        sourceRefreshAttempted = true;
        retryPosition = player == null ? C.TIME_UNSET : player.getCurrentPosition();
        SpiderDebug.log("tmdb-video", "refresh expired source status=%d position=%d", status, retryPosition);
        resolve();
        return true;
    }

    private static int httpStatus(Throwable error) {
        int depth = 0;
        for (Throwable cause = error; cause != null && depth++ < 8; cause = cause.getCause()) {
            if (cause instanceof HttpDataSource.InvalidResponseCodeException response) return response.responseCode;
        }
        return 0;
    }

    private void setFullscreen(boolean fullscreen) {
        if (this.fullscreen == fullscreen) return;
        this.fullscreen = fullscreen;
        applyWindowMode();
    }

    private void applyWindowMode() {
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        View decor = window.getDecorView();
        FragmentActivity activity = getActivity();
        if (previousSystemUi == UNSET_SYSTEM_UI) previousSystemUi = activity == null ? decor.getSystemUiVisibility() : activity.getWindow().getDecorView().getSystemUiVisibility();
        window.setBackgroundDrawableResource(android.R.color.transparent);
        window.setGravity(Gravity.CENTER);
        if (fullscreen) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            decor.setSystemUiVisibility(fullscreenSystemUi());
            if (container != null) container.setBackgroundColor(Color.BLACK);
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.72f);
            DisplayMetrics metrics = getResources().getDisplayMetrics();
            int margin = dp(24);
            int availableWidth = Math.max(dp(240), metrics.widthPixels - margin * 2);
            int maxWidth = dp(isTelevision() ? 1120 : 960);
            int width = Math.min(availableWidth, maxWidth);
            int height = Math.min(metrics.heightPixels - margin * 2, Math.round(width * 9f / 16f) + dp(52));
            window.setLayout(width, Math.max(dp(220), height));
            decor.setSystemUiVisibility(previousSystemUi);
            if (container != null) container.setBackgroundResource(R.drawable.shape_dialog_glass_panel);
        }
        if (surface != null && surface.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) surface.getLayoutParams();
            params.topMargin = fullscreen ? 0 : dp(52);
            surface.setLayoutParams(params);
        }
        if (fullscreenButton != null) {
            fullscreenButton.setImageResource(fullscreen ? R.drawable.ic_control_fullscreen_exit : R.drawable.ic_control_fullscreen);
            fullscreenButton.setContentDescription(ResUtil.getString(fullscreen ? R.string.play_exit_fullscreen : R.string.play_fullscreen));
        }
    }

    private int fullscreenSystemUi() {
        return View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
    }

    private boolean isTelevision() {
        int type = getResources().getConfiguration().uiMode & Configuration.UI_MODE_TYPE_MASK;
        return type == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void cancelResolve() {
        Future<?> future = resolveFuture;
        resolveFuture = null;
        if (future != null) future.cancel(true);
    }

    private void releasePlayer() {
        if (playerView != null) playerView.setPlayer(null);
        if (player == null) return;
        player.removeListener(this);
        player.release();
        player = null;
    }

    private void restoreWindowUi() {
        Dialog dialog = getDialog();
        Window window = dialog == null ? null : dialog.getWindow();
        if (window == null) return;
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (previousSystemUi != UNSET_SYSTEM_UI) window.getDecorView().setSystemUiVisibility(previousSystemUi);
    }

    private void dispatchParentResume() {
        if (parentResumed) return;
        parentResumed = true;
        FragmentActivity activity = getActivity();
        if (activity == null || activity.isChangingConfigurations()) return;
        if (activity instanceof PlaybackActivity) ((PlaybackActivity) activity).resumeAfterOverlayPlayback(resumeParent);
    }

    @Override
    public void onDestroyView() {
        generation++;
        cancelResolve();
        releasePlayer();
        restoreWindowUi();
        container = null;
        surface = null;
        playerView = null;
        loading = null;
        error = null;
        title = null;
        fullscreenButton = null;
        super.onDestroyView();
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        releasePlayer();
        dispatchParentResume();
        super.onDismiss(dialog);
    }
}
