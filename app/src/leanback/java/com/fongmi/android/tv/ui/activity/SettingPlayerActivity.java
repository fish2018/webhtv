package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.ActivitySettingPlayerBinding;
import com.fongmi.android.tv.impl.BufferListener;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.impl.UaListener;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.mpv.MpvConfigStore;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerButtonSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.BufferDialog;
import com.fongmi.android.tv.ui.dialog.ChoiceDialog;
import com.fongmi.android.tv.ui.dialog.LutDialog;
import com.fongmi.android.tv.ui.dialog.MpvConfigDialog;
import com.fongmi.android.tv.ui.dialog.PlaybackPerformanceDialog;
import com.fongmi.android.tv.ui.dialog.PlayerOsdDialog;
import com.fongmi.android.tv.ui.dialog.PlayerButtonConfigDialog;
import com.fongmi.android.tv.ui.dialog.PlayerKernelDialog;
import com.fongmi.android.tv.ui.dialog.SpeedDialog;
import com.fongmi.android.tv.ui.dialog.UaDialog;
import com.fongmi.android.tv.ui.dialog.VideoAspectModeDialog;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ResUtil;

import java.text.DecimalFormat;

import is.xyz.mpv.MPVLib;

public class SettingPlayerActivity extends BaseActivity implements UaListener, BufferListener, SpeedListener {

    private ActivitySettingPlayerBinding mBinding;
    private DecimalFormat format;
    private String[] backBuffer;
    private String[] bufferBytes;
    private String[] caption;
    private String[] failureFallback;
    private String[] kernel;
    private String[] mpvRender;
    private String[] playCache;
    private String[] render;
    private String[] scale;
    private String[] osd;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingPlayerActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_on : R.string.setting_off);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingPlayerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        setVisible();
        format = new DecimalFormat("0.#");
        PlaybackPerformanceSetting.ensureInitialized();
        mBinding.exo4kCompat.requestFocus();
        mBinding.uaText.setText(Setting.getUa());
        mBinding.aacText.setText(getSwitch(PlayerSetting.isPreferAAC()));
        mBinding.tunnelText.setText(getSwitch(PlayerSetting.isTunnel()));
        setPerformanceText();
        setPlayerButtonsText();
        mBinding.speedText.setText(format.format(PlayerSetting.getSpeed()));
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.bufferBytesText.setText((bufferBytes = ResUtil.getStringArray(R.array.select_buffer_bytes))[PlayerSetting.getBufferBytesOption()]);
        mBinding.backBufferText.setText((backBuffer = ResUtil.getStringArray(R.array.select_back_buffer))[PlayerSetting.getBackBufferOption()]);
        mBinding.playCacheText.setText((playCache = ResUtil.getStringArray(R.array.select_play_cache))[PlayerSetting.getPlayCacheOption()]);
        setPreloadText();
        mBinding.autoPlayText.setText(getSwitch(PlayerSetting.isAutoPlay()));
        mBinding.autoChangeText.setText(getSwitch(PlayerSetting.isAutoChange()));
        mBinding.failureFallbackText.setText((failureFallback = ResUtil.getStringArray(R.array.select_player_failure_fallback))[PlayerSetting.getFailureFallback()]);
        mBinding.backgroundText.setText(getSwitch(PlayerSetting.isBackgroundOn()));
        mBinding.musicNotificationText.setText(getSwitch(PlayerSetting.isMusicNotification()));
        mBinding.audioBookNotificationText.setText(getSwitch(PlayerSetting.isAudioBookNotification()));
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
        mBinding.audioPassThroughText.setText(getSwitch(PlayerSetting.isAudioPassThrough()));
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
        mBinding.ffmpegModeText.setText(getFFmpegModeText());
        mBinding.osdText.setText(getOsdText(osd = ResUtil.getStringArray(R.array.select_player_osd)));
        mBinding.kernelText.setText((kernel = ResUtil.getStringArray(R.array.select_player_kernel))[PlayerSetting.getPlayer()]);
        mpvRender = ResUtil.getStringArray(R.array.select_mpv_render);
        mBinding.scaleText.setText((scale = ResUtil.getStringArray(R.array.select_scale))[PlayerSetting.getScale()]);
        mBinding.lutText.setText(LutSetting.getSummary());
        setMpvRows();
        setFfmpegModeVisibility();
        mBinding.renderText.setText((render = ResUtil.getStringArray(R.array.select_render))[PlayerSetting.getRender()]);
        mBinding.captionText.setText((caption = ResUtil.getStringArray(R.array.select_caption))[PlayerSetting.isCaption() ? 1 : 0]);
        hidePerformanceRows();
    }

    @Override
    protected void initEvent() {
        mBinding.ua.setOnClickListener(this::onUa);
        mBinding.aac.setOnClickListener(this::setAAC);
        mBinding.kernel.setOnClickListener(this::setKernel);
        mBinding.scale.setOnClickListener(this::setScale);
        mBinding.lut.setOnClickListener(this::onLut);
        mBinding.mpvConfig.setOnClickListener(view -> MpvConfigDialog.show(this, () -> mBinding.mpvConfigText.setText(MpvConfigStore.summary())));
        mBinding.mpvRender.setOnClickListener(this::setMpvRender);
        mBinding.osd.setOnClickListener(this::onOsd);
        mBinding.playerButtons.setOnClickListener(view -> PlayerButtonConfigDialog.show(this, this::setPlayerButtonsText));
        mBinding.speed.setOnClickListener(this::onSpeed);
        mBinding.buffer.setOnClickListener(this::onBuffer);
        mBinding.bufferBytes.setOnClickListener(this::setBufferBytes);
        mBinding.backBuffer.setOnClickListener(this::setBackBuffer);
        mBinding.playCache.setOnClickListener(this::setPlayCache);
        mBinding.preload.setOnClickListener(this::setPreload);
        mBinding.preloadThread.setOnClickListener(this::setPreloadThread);
        mBinding.preloadSize.setOnClickListener(this::setPreloadSize);
        mBinding.preloadTime.setOnClickListener(this::setPreloadTime);
        mBinding.preloadAhead.setOnClickListener(this::setPreloadAhead);
        mBinding.preloadPause.setOnClickListener(this::setPreloadPause);
        mBinding.autoPlay.setOnClickListener(this::setAutoPlay);
        mBinding.autoChange.setOnClickListener(this::setAutoChange);
        mBinding.failureFallback.setOnClickListener(this::setFailureFallback);
        mBinding.render.setOnClickListener(this::setRender);
        mBinding.tunnel.setOnClickListener(this::setTunnel);
        mBinding.exo4kCompat.setOnClickListener(this::onPerformance);
        mBinding.caption.setOnClickListener(this::setCaption);
        mBinding.caption.setOnLongClickListener(this::onCaption);
        mBinding.background.setOnClickListener(this::onBackground);
        mBinding.musicNotification.setOnClickListener(this::setMusicNotification);
        mBinding.audioBookNotification.setOnClickListener(this::setAudioBookNotification);
        mBinding.audioDecode.setOnClickListener(this::setAudioDecode);
        mBinding.audioPassThrough.setOnClickListener(this::setAudioPassThrough);
        mBinding.videoDecode.setOnClickListener(this::setVideoDecode);
        mBinding.ffmpegMode.setOnClickListener(this::setFfmpegMode);
    }

    private void setVisible() {
        mBinding.caption.setVisibility(PlayerSetting.hasCaption() ? View.VISIBLE : View.GONE);
    }

    private void onUa(View view) {
        UaDialog.show(this);
    }

    @Override
    public void setUa(String ua) {
        mBinding.uaText.setText(ua);
        Setting.putUa(ua);
    }

    private void setAAC(View view) {
        PlayerSetting.putPreferAAC(!PlayerSetting.isPreferAAC());
        PlaybackPerformanceSetting.markCustom();
        mBinding.aacText.setText(getSwitch(PlayerSetting.isPreferAAC()));
        setPerformanceText();
    }

    private void setKernel(View view) {
        PlayerKernelDialog.show(this, PlayerSetting.getPlayer(), index -> {
            mBinding.kernelText.setText(kernel[index]);
            PlayerSetting.putPlayer(index);
            setMpvRows();
            setFfmpegModeVisibility();
            setPerformanceText();
        });
    }

    private void setScale(View view) {
        VideoAspectModeDialog.show(this, PlayerSetting.getScale(), mode -> {
            mBinding.scaleText.setText(scale[mode]);
            PlayerSetting.putScale(mode);
        });
    }

    private void onLut(View view) {
        LutDialog.show(this, null, () -> mBinding.lutText.setText(LutSetting.getSummary()));
    }

    private void setMpvRows() {
        boolean visible = PlayerSetting.getPlayer() == PlayerSetting.MPV;
        mBinding.mpvConfig.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.mpvRender.setVisibility(visible ? View.VISIBLE : View.GONE);
        mBinding.mpvConfigText.setText(MpvConfigStore.summary());
        mBinding.mpvRenderText.setText(getMpvRenderText());
    }

    private void setFfmpegModeVisibility() {
        boolean visible = PlayerSetting.getPlayer() == PlayerSetting.EXO;
        mBinding.ffmpegMode.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private void setMpvRender(View view) {
        int index = (PlayerSetting.getMpvRender() + 1) % mpvRender.length;
        PlayerSetting.putMpvRender(index);
        mBinding.mpvRenderText.setText(getMpvRenderText());
    }

    private String getMpvRenderText() {
        int render = PlayerSetting.getMpvRender();
        String text = mpvRender[render];
        if (render == PlayerSetting.MPV_RENDER_VULKAN && !MPVLib.isVulkanRendererAvailable(this)) {
            text += " (" + getString(R.string.mpv_render_native_unavailable) + ")";
        }
        return text;
    }

    private void onOsd(View view) {
        PlayerOsdDialog.show(this, osd, getOsdChecked(), checked -> {
            setOsdChecked(checked);
            mBinding.osdText.setText(getOsdText(osd));
        });
    }

    private void setPlayerButtonsText() {
        mBinding.playerButtonsText.setText(getString(R.string.player_button_config_summary, PlayerButtonSetting.getVisibleCount(), PlayerButtonSetting.getTotalCount()));
    }

    private boolean[] getOsdChecked() {
        return PlayerSetting.getDisplayChecked();
    }

    private void setOsdChecked(boolean[] checked) {
        PlayerSetting.putDisplayChecked(checked);
    }

    private String getOsdText(String[] items) {
        boolean[] checked = getOsdChecked();
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < checked.length; i++) {
            if (!checked[i]) continue;
            if (builder.length() > 0) builder.append(" / ");
            builder.append(items[i]);
        }
        return builder.length() == 0 ? getString(R.string.setting_off) : builder.toString();
    }

    private void onSpeed(View view) {
        SpeedDialog.show(this);
    }

    @Override
    public void setSpeed(float speed) {
        mBinding.speedText.setText(format.format(speed));
        PlayerSetting.putSpeed(speed);
    }

    private void onBuffer(View view) {
        BufferDialog.show(this);
    }

    @Override
    public void setBuffer(int times) {
        mBinding.bufferText.setText(String.valueOf(times));
        PlayerSetting.putBuffer(times);
        PlaybackPerformanceSetting.markCustom();
        setPerformanceText();
    }

    private void setBufferBytes(View view) {
        int index = (PlayerSetting.getBufferBytesOption() + 1) % bufferBytes.length;
        mBinding.bufferBytesText.setText(bufferBytes[index]);
        PlayerSetting.putBufferBytesOption(index);
        PlaybackPerformanceSetting.markCustom();
        setPerformanceText();
    }

    private void setBackBuffer(View view) {
        int index = (PlayerSetting.getBackBufferOption() + 1) % backBuffer.length;
        mBinding.backBufferText.setText(backBuffer[index]);
        PlayerSetting.putBackBufferOption(index);
        PlaybackPerformanceSetting.markCustom();
        setPerformanceText();
    }

    private void setPlayCache(View view) {
        int index = (PlayerSetting.getPlayCacheOption() + 1) % playCache.length;
        mBinding.playCacheText.setText(playCache[index]);
        PlayerSetting.putPlayCacheOption(index);
        PlaybackPerformanceSetting.markCustom();
        setPerformanceText();
    }

    private void setPreload(View view) {
        PreloadSetting.putPreload(!PreloadSetting.isPreload());
        PlaybackPerformanceSetting.markCustom();
        setPreloadText();
        setPerformanceText();
    }

    private void setPreloadThread(View view) {
        int value = PreloadSetting.getPreloadThreads() + 1;
        if (value > PreloadSetting.MAX_THREADS) value = PreloadSetting.MIN_THREADS;
        PreloadSetting.putPreloadThreads(value);
        PlaybackPerformanceSetting.markCustom();
        setPreloadText();
        setPerformanceText();
    }

    private void setPreloadSize(View view) {
        PreloadSetting.putPreloadSizeMb(PreloadSetting.getNextPreloadSizeMb());
        PlaybackPerformanceSetting.markCustom();
        setPreloadText();
        setPerformanceText();
    }

    private void setPreloadTime(View view) {
        int value = PreloadSetting.getPreloadTimeSeconds() + PreloadSetting.STEP_TIME_SECONDS;
        if (value > PreloadSetting.MAX_TIME_SECONDS) value = PreloadSetting.MIN_TIME_SECONDS;
        PreloadSetting.putPreloadTimeSeconds(value);
        PlaybackPerformanceSetting.markCustom();
        setPreloadText();
        setPerformanceText();
    }

    private void setPreloadAhead(View view) {
        PreloadSetting.putPreloadAheadSeconds(PreloadSetting.getNextPreloadAheadSeconds());
        PlaybackPerformanceSetting.markCustom();
        setPreloadText();
        setPerformanceText();
    }

    private void setPreloadPause(View view) {
        PreloadSetting.putPausePreloadPolicy(PreloadSetting.getNextPausePreloadPolicy());
        PlaybackPerformanceSetting.markCustom();
        setPreloadText();
        setPerformanceText();
    }

    private void setPreloadText() {
        boolean preload = PreloadSetting.isPreload();
        mBinding.preloadText.setText(getSwitch(preload));
        mBinding.preloadThread.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadSize.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadTime.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadAhead.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadPause.setVisibility(preload ? View.VISIBLE : View.GONE);
        mBinding.preloadThreadText.setText(getString(R.string.player_preload_threads_value, PreloadSetting.getPreloadThreads()));
        mBinding.preloadSizeText.setText(FileUtil.byteCountToDisplaySize(PreloadSetting.getPreloadSizeBytes()));
        mBinding.preloadTimeText.setText(getString(R.string.player_preload_time_value, PreloadSetting.getPreloadTimeSeconds()));
        mBinding.preloadAheadText.setText(getPreloadAheadText());
        mBinding.preloadPauseText.setText(getPreloadPauseText());
    }

    private String getPreloadAheadText() {
        int seconds = PreloadSetting.getPreloadAheadSeconds();
        return seconds == PreloadSetting.WHOLE_MEDIA_AHEAD_SECONDS
                ? getString(R.string.player_preload_ahead_whole)
                : getString(R.string.player_preload_ahead_value, seconds / 60);
    }

    private String getPreloadPauseText() {
        return getString(switch (PreloadSetting.getPausePreloadPolicy()) {
            case PreloadSetting.PAUSE_PRELOAD_ALWAYS -> R.string.player_preload_pause_always;
            default -> R.string.player_preload_pause_wifi;
        });
    }

    private void setAutoPlay(View view) {
        PlayerSetting.putAutoPlay(!PlayerSetting.isAutoPlay());
        mBinding.autoPlayText.setText(getSwitch(PlayerSetting.isAutoPlay()));
    }

    private void setAutoChange(View view) {
        PlayerSetting.putAutoChange(!PlayerSetting.isAutoChange());
        mBinding.autoChangeText.setText(getSwitch(PlayerSetting.isAutoChange()));
    }

    private void setFailureFallback(View view) {
        ChoiceDialog.showSingle(this, R.string.player_failure_fallback, failureFallback, PlayerSetting.getFailureFallback(), which -> {
            PlayerSetting.putFailureFallback(which);
            mBinding.failureFallbackText.setText(failureFallback[which]);
        });
    }

    private void setRender(View view) {
        if (PlayerSetting.isTunnel() && PlayerSetting.getRender() == 0) setTunnel(view);
        int index = (PlayerSetting.getRender() + 1) % render.length;
        mBinding.renderText.setText(render[index]);
        PlayerSetting.putRender(index);
        PlaybackPerformanceSetting.markCustom();
        setPerformanceText();
    }

    private void setTunnel(View view) {
        PlayerSetting.putTunnel(!PlayerSetting.isTunnel());
        PlaybackPerformanceSetting.markCustom();
        mBinding.tunnelText.setText(getSwitch(PlayerSetting.isTunnel()));
        if (PlayerSetting.isTunnel() && PlayerSetting.getRender() == 1) setRender(view);
        setPerformanceText();
    }

    private void onPerformance(View view) {
        PlaybackPerformanceDialog.show(this, this::refreshPerformanceSettings);
    }

    private void refreshPerformanceSettings() {
        mBinding.bufferText.setText(String.valueOf(PlayerSetting.getBuffer()));
        mBinding.bufferBytesText.setText(bufferBytes[PlayerSetting.getBufferBytesOption()]);
        mBinding.backBufferText.setText(backBuffer[PlayerSetting.getBackBufferOption()]);
        mBinding.playCacheText.setText(playCache[PlayerSetting.getPlayCacheOption()]);
        mBinding.renderText.setText(render[PlayerSetting.getRender()]);
        mBinding.tunnelText.setText(getSwitch(PlayerSetting.isTunnel()));
        mBinding.aacText.setText(getSwitch(PlayerSetting.isPreferAAC()));
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
        mBinding.audioPassThroughText.setText(getSwitch(PlayerSetting.isAudioPassThrough()));
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
        setPreloadText();
        setPerformanceText();
        hidePerformanceRows();
    }

    private void setPerformanceText() {
        mBinding.exo4kCompatText.setText(PlaybackPerformanceSetting.getSummary());
    }

    private void hidePerformanceRows() {
        mBinding.render.setVisibility(View.GONE);
        mBinding.buffer.setVisibility(View.GONE);
        mBinding.bufferBytes.setVisibility(View.GONE);
        mBinding.backBuffer.setVisibility(View.GONE);
        mBinding.playCache.setVisibility(View.GONE);
        mBinding.preload.setVisibility(View.GONE);
        mBinding.preloadThread.setVisibility(View.GONE);
        mBinding.preloadSize.setVisibility(View.GONE);
        mBinding.preloadTime.setVisibility(View.GONE);
        mBinding.preloadAhead.setVisibility(View.GONE);
        mBinding.preloadPause.setVisibility(View.GONE);
        mBinding.tunnel.setVisibility(View.GONE);
        mBinding.audioDecode.setVisibility(View.GONE);
        mBinding.audioPassThrough.setVisibility(View.GONE);
        mBinding.videoDecode.setVisibility(View.GONE);
        mBinding.aac.setVisibility(View.GONE);
    }

    private void setCaption(View view) {
        PlayerSetting.putCaption(!PlayerSetting.isCaption());
        mBinding.captionText.setText(caption[PlayerSetting.isCaption() ? 1 : 0]);
    }

    private boolean onCaption(View view) {
        if (PlayerSetting.isCaption()) startActivity(new Intent(Settings.ACTION_CAPTIONING_SETTINGS));
        return PlayerSetting.isCaption();
    }

    private void setMusicNotification(View view) {
        PlayerSetting.putMusicNotification(!PlayerSetting.isMusicNotification());
        mBinding.musicNotificationText.setText(getSwitch(PlayerSetting.isMusicNotification()));
    }

    private void setAudioBookNotification(View view) {
        PlayerSetting.putAudioBookNotification(!PlayerSetting.isAudioBookNotification());
        mBinding.audioBookNotificationText.setText(getSwitch(PlayerSetting.isAudioBookNotification()));
    }

    private void setAudioDecode(View view) {
        PlayerSetting.putAudioPrefer(!PlayerSetting.isAudioPrefer());
        PlaybackPerformanceSetting.markCustom();
        mBinding.audioDecodeText.setText(getSwitch(PlayerSetting.isAudioPrefer()));
        setPerformanceText();
    }

    private void setAudioPassThrough(View view) {
        PlayerSetting.putAudioPassThrough(!PlayerSetting.isAudioPassThrough());
        PlaybackPerformanceSetting.markCustom();
        mBinding.audioPassThroughText.setText(getSwitch(PlayerSetting.isAudioPassThrough()));
        setPerformanceText();
    }

    private void setVideoDecode(View view) {
        PlayerSetting.putVideoPrefer(!PlayerSetting.isVideoPrefer());
        PlaybackPerformanceSetting.markCustom();
        mBinding.videoDecodeText.setText(getSwitch(PlayerSetting.isVideoPrefer()));
        setPerformanceText();
    }

    private void setFfmpegMode(View view) {
        int mode = (PlayerSetting.getFFmpegMode() + 1) % 4;
        PlayerSetting.putFFmpegMode(mode);
        mBinding.ffmpegModeText.setText(getFFmpegModeText());
    }

    private String getFFmpegModeText() {
        return switch (PlayerSetting.getFFmpegMode()) {
            case PlayerSetting.FFMPEG_MODE_OFFICIAL -> "Official";
            case PlayerSetting.FFMPEG_MODE_SIMPLE -> "Simple";
            case PlayerSetting.FFMPEG_MODE_AUTO -> "自动";
            default -> "NextLib";
        };
    }

    private void onBackground(View view) {
        PlayerSetting.putBackground(PlayerSetting.isBackgroundOn() ? 0 : 1);
        mBinding.backgroundText.setText(getSwitch(PlayerSetting.isBackgroundOn()));
    }

}
