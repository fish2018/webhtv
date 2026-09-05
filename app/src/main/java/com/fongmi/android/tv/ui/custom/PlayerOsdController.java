package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.TextView;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.DolbyVisionFormatLabel;
import com.fongmi.android.tv.player.GpuLoadMonitor;
import com.fongmi.android.tv.player.ijk.IjkDecodePressurePolicy;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.PlaybackPanelResourceMonitor;
import com.fongmi.android.tv.player.PlaybackDiagnosticsSourcePolicy;
import com.fongmi.android.tv.player.PlaybackSpeedMeter;
import com.fongmi.android.tv.player.PlaybackRoute;
import com.fongmi.android.tv.player.engine.PlayerEngine;
import com.fongmi.android.tv.player.exo.PlaybackAnalyticsListener;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.PreloadSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Util;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PlayerOsdController {

    public interface Source {
        PlayerManager getPlayer();

        String getTitle();
    }

    private static volatile String cachedDeviceText;
    private static volatile String cachedSystemText;
    private static volatile String cachedWebViewText;
    private static volatile String cachedHevcDecoderText;

    private final SimpleDateFormat timeFormat;
    private final TextView topLeft;
    private final TextView topRight;
    private final TextView bottomLeft;
    private final TextView bottomRight;
    private final TextView diagnostics;
    private final TextView diagnosticsExtra;
    private final View diagnosticsPanel;
    private final MiniProgressView miniProgress;
    private final Runnable update;
    private final Source source;
    private final View root;
    private final float miniSp;
    private final PlaybackPanelResourceMonitor resourceMonitor;

    private boolean suppressed;
    private final DecimalFormat frameFormat;
    private final DecimalFormat refreshFormat;
    private final DecimalFormat bitrateFormat;
    private final PlaybackSpeedMeter speedMeter = new PlaybackSpeedMeter();
    private long lastSpeedKBps;
    private String lastSpeedText;
    private boolean controlsVisible;
    private boolean diagnosticsVisible;
    private boolean persistentSuppressed;
    private boolean started;
    private PlayerManager diagnosticsSamplingPlayer;

    public PlayerOsdController(View root, TextView topLeft, TextView topRight, TextView bottomLeft, TextView bottomRight, TextView diagnostics, MiniProgressView miniProgress, Source source, float miniSp) {
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        this.bitrateFormat = new DecimalFormat("#.0");
        this.refreshFormat = new DecimalFormat("#.##");
        this.frameFormat = new DecimalFormat("#.###");
        this.miniProgress = miniProgress;
        this.bottomRight = bottomRight;
        this.bottomLeft = bottomLeft;
        this.diagnostics = diagnostics;
        this.diagnosticsExtra = root.findViewById(R.id.osdDiagnosticsExtra);
        this.diagnosticsPanel = root.findViewById(R.id.osdDiagnosticsPanel);
        this.topRight = topRight;
        this.topLeft = topLeft;
        this.miniSp = miniSp;
        this.source = source;
        this.root = root;
        this.update = this::update;
        this.resourceMonitor = new PlaybackPanelResourceMonitor(root.getContext());
        diagnosticsExtra.setVisibility(View.GONE);
        updateDiagnosticsWidth();
    }

    public void start() {
        started = true;
        if (suppressed) {
            root.setVisibility(View.GONE);
            return;
        }
        // 即使用户关闭所有 OSD，控制栏显示时也需要强制显示标题/分辨率/时间
        // 所以不能在 !isOsdEnabled() 时直接 return
        if (!PlayerSetting.isOsdEnabled() && !controlsVisible) {
            root.setVisibility(View.GONE);
            return;
        }
        resetSpeed();
        App.removeCallbacks(update);
        App.post(update, 0);
    }

    public void stop() {
        started = false;
        stopDiagnosticsSampling();
        App.removeCallbacks(update);
        root.setVisibility(View.GONE);
    }

    public void release() {
        stop();
    }

    public void setSuppressed(boolean suppressed) {
        if (this.suppressed == suppressed) return;
        this.suppressed = suppressed;
        App.removeCallbacks(update);
        if (suppressed) root.setVisibility(View.GONE);
        else if (started) start();
    }

    public void setControlsVisible(boolean controlsVisible) {
        if (this.controlsVisible == controlsVisible) return;
        this.controlsVisible = controlsVisible;
        if (started) {
            // 控制栏显示时，即使 OSD 全关也要启动更新循环（为了强制显示时间）
            if (controlsVisible && !PlayerSetting.isOsdEnabled()) {
                resetSpeed();
                App.removeCallbacks(update);
                App.post(update, 0);
            }
            render();
        }
    }

    public void setPersistentSuppressed(boolean persistentSuppressed) {
        if (this.persistentSuppressed == persistentSuppressed) return;
        this.persistentSuppressed = persistentSuppressed;
        if (started) render();
    }

    public boolean isDiagnosticsVisible() {
        return diagnosticsVisible;
    }

    public void setDiagnosticsVisible(boolean visible) {
        boolean next = visible && PlayerSetting.isOsdDiagnostics();
        if (diagnosticsVisible == next) return;
        diagnosticsVisible = next;
        if (!next) stopDiagnosticsSampling();
        if (started) render();
    }

    public void toggleDiagnostics() {
        if (!PlayerSetting.isOsdDiagnostics()) return;
        diagnosticsVisible = !diagnosticsVisible;
        if (!diagnosticsVisible) stopDiagnosticsSampling();
        if (started) render();
    }

    private void update() {
        if (render()) App.post(update, 1000);
    }

    private boolean render() {
        if (suppressed) {
            root.setVisibility(View.GONE);
            return false;
        }
        setTextSize(miniSp);
        PlayerManager player = source.getPlayer();
        updateSpeed(player);

        // 控制栏显示时的处理：
        // - leanback: suppressed=false，强制显示 OSD 的标题/分辨率/时间（因为控制栏没有自己的 title/size）
        // - mobile: suppressed=true，已在上面 return，不会执行此分支（mobile 控制栏有自己的 title/size）
        if (controlsVisible) {
            stopDiagnosticsSampling();
            setTopLeftForControls(player);
            setTopRightForControls();
            bottomLeft.setVisibility(View.GONE);
            bottomRight.setVisibility(View.GONE);
            diagnosticsPanel.setVisibility(View.GONE);
            if (miniProgress != null) miniProgress.setVisibility(View.GONE);
            // 如果标题或时间至少有一个显示，则显示 root
            boolean hasVisible = topLeft.getVisibility() == View.VISIBLE || topRight.getVisibility() == View.VISIBLE;
            root.setVisibility(hasVisible ? View.VISIBLE : View.GONE);
            return true;
        }

        // 控制栏隐藏时，若用户关闭所有 OSD 屏显，停止刷新并隐藏
        boolean enabled = PlayerSetting.isOsdEnabled();
        if (!enabled) {
            stopDiagnosticsSampling();
            root.setVisibility(View.GONE);
            return false;
        }

        // 控制栏隐藏时，按用户设置显示
        root.setVisibility(View.VISIBLE);
        if (persistentSuppressed) {
            hidePersistent();
            setDiagnosticsPanel(player);
            root.setVisibility(diagnosticsPanel.getVisibility() == View.VISIBLE ? View.VISIBLE : View.GONE);
            return true;
        }
        setTopLeft(player);
        setTopRight();
        setBottomLeft(player);
        setBottomRight();
        setDiagnosticsPanel(player);
        setMiniProgress(player);
        return true;
    }

    private void setTopLeft(PlayerManager player) {
        boolean showTitle = PlayerSetting.isOsdTitle();
        boolean showResolution = PlayerSetting.isOsdResolution();
        if ((!showTitle && !showResolution) || diagnosticsVisible) {
            topLeft.setVisibility(View.GONE);
            return;
        }
        String title = showTitle ? source.getTitle() : "";
        String size = showResolution && player != null ? player.getSizeText() : "";
        topLeft.setText(join("\n", title, size));
        topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
    }

    private void setTopLeftForControls(PlayerManager player) {
        // 控制栏显示时，强制显示标题和分辨率
        String title = source.getTitle();
        String size = player != null ? player.getSizeText() : "";
        topLeft.setText(join("\n", title, size));
        topLeft.setVisibility(TextUtils.isEmpty(topLeft.getText()) ? View.GONE : View.VISIBLE);
    }

    private void setTopRight() {
        topRight.setVisibility(PlayerSetting.isOsdTime() ? View.VISIBLE : View.GONE);
        if (PlayerSetting.isOsdTime()) topRight.setText(timeFormat.format(new Date()));
    }

    private void setTopRightForControls() {
        // 控制栏显示时，强制显示时间，无论用户设置
        topRight.setText(timeFormat.format(new Date()));
        topRight.setVisibility(View.VISIBLE);
    }

    private void setBottomLeft(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdProgress() || player == null || player.isLive()) {
            bottomLeft.setVisibility(View.GONE);
            return;
        }
        long position = Math.max(0, player.getPosition());
        long duration = Math.max(0, player.getDuration());
        if (duration <= 0) {
            bottomLeft.setVisibility(View.GONE);
            return;
        }
        bottomLeft.setText(Util.timeMs(position) + " / " + Util.timeMs(duration));
        bottomLeft.setVisibility(View.VISIBLE);
    }

    private void setBottomRight() {
        bottomRight.setVisibility(PlayerSetting.isOsdTraffic() ? View.VISIBLE : View.GONE);
        if (!PlayerSetting.isOsdTraffic()) return;
        bottomRight.setText(lastSpeedText);
        bottomRight.setVisibility(TextUtils.isEmpty(lastSpeedText) ? View.GONE : View.VISIBLE);
    }

    private void setDiagnosticsPanel(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdDiagnostics() || !diagnosticsVisible || player == null) {
            stopDiagnosticsSampling();
            diagnosticsPanel.setVisibility(View.GONE);
            return;
        }
        startDiagnosticsSampling(player);
        DiagnosticsText text = getDiagnostics(player);
        boolean land = isLandscape();
        updateDiagnosticsWidth();
        diagnostics.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
        diagnosticsExtra.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
        diagnostics.setText(land ? text.main() : text.all());
        diagnosticsExtra.setText(text.extra());
        diagnosticsExtra.setVisibility(land && !TextUtils.isEmpty(text.extra()) ? View.VISIBLE : View.GONE);
        diagnosticsPanel.setVisibility(TextUtils.isEmpty(text.all()) ? View.GONE : View.VISIBLE);
    }

    private void updateDiagnosticsWidth() {
        int rootWidth = root.getWidth() > 0 ? root.getWidth() : App.get().getResources().getDisplayMetrics().widthPixels;
        int rootHeight = root.getHeight() > 0 ? root.getHeight() : App.get().getResources().getDisplayMetrics().heightPixels;
        if (rootWidth <= 0) return;
        boolean land = rootWidth >= rootHeight;
        int width = Math.round(rootWidth * (land ? 0.98f : 0.98f));
        ViewGroup.LayoutParams params = diagnosticsPanel.getLayoutParams();
        if (params != null && params.width != width) {
            params.width = width;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            diagnosticsPanel.setLayoutParams(params);
        }
        diagnosticsPanel.setPadding(dp(land ? 8 : 5), dp(land ? 7 : 5), dp(land ? 8 : 7), dp(land ? 7 : 5));
        diagnostics.setMaxWidth(width);
        diagnosticsExtra.setMaxWidth(width);
        if (rootHeight > 0) {
            int maxHeight = Math.round(rootHeight * (land ? 0.84f : 1.0f));
            diagnostics.setMaxHeight(maxHeight);
            diagnosticsExtra.setMaxHeight(maxHeight);
        }
        diagnostics.setTextScaleX(land ? 0.96f : 0.92f);
        diagnosticsExtra.setTextScaleX(land ? 0.96f : 0.92f);
    }

    private float getDiagnosticsSp() {
        boolean land = isLandscape();
        float target = land ? 10.2f : 8.0f;
        return Math.min(miniSp, target);
    }

    private boolean isLandscape() {
        int rootWidth = root.getWidth() > 0 ? root.getWidth() : App.get().getResources().getDisplayMetrics().widthPixels;
        int rootHeight = root.getHeight() > 0 ? root.getHeight() : App.get().getResources().getDisplayMetrics().heightPixels;
        return rootWidth >= rootHeight;
    }

    private void setMiniProgress(PlayerManager player) {
        if (controlsVisible || !PlayerSetting.isOsdMini() || player == null || player.isLive()) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        long duration = Math.max(0, player.getDuration());
        if (duration <= 0) {
            miniProgress.setVisibility(View.GONE);
            return;
        }
        miniProgress.setProgress(player.getPosition(), duration);
        miniProgress.setVisibility(View.VISIBLE);
    }

    private void setTextSize(float sp) {
        topLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        topLeft.setTextColor(0xFFFFFFFF);
        topRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        topRight.setTextColor(0xFFFFFFFF);
        bottomLeft.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomLeft.setTextColor(0xFFFFFFFF);
        bottomRight.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        bottomRight.setTextColor(0xFFFFFFFF);
        diagnostics.setTextColor(0xFFFFFFFF);
        diagnostics.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
        diagnosticsExtra.setTextColor(0xFFFFFFFF);
        diagnosticsExtra.setTextSize(TypedValue.COMPLEX_UNIT_SP, getDiagnosticsSp());
    }

    private void hidePersistent() {
        topLeft.setVisibility(View.GONE);
        topRight.setVisibility(View.GONE);
        bottomLeft.setVisibility(View.GONE);
        bottomRight.setVisibility(View.GONE);
        miniProgress.setVisibility(View.GONE);
    }

    private void updateSpeed(PlayerManager player) {
        // No player means no playback to report on; app-wide traffic would mislead here.
        if (player == null) {
            resetSpeed();
            return;
        }
        speedMeter.sample(player);
        lastSpeedKBps = speedMeter.getBytesPerSecond() / 1024;
        lastSpeedText = speedMeter.getText();
    }

    private DiagnosticsText getDiagnostics(PlayerManager player) {
        GpuLoadMonitor.process().requestSample();
        resourceMonitor.requestSample();
        PlaybackAnalyticsListener.Snapshot snapshot = player.isExo() ? PlaybackAnalyticsListener.getSnapshot() : PlaybackAnalyticsListener.Snapshot.empty();
        Format video = snapshot.videoFormat() != null ? snapshot.videoFormat() : snapshot.errorFormat() != null ? snapshot.errorFormat() : player.getVideoFormat();
        Format audio = snapshot.audioFormat();
        String state = stateText(player.getPlaybackState()) + (player.isLoading() ? " / 正在加载" : "");
        String buffer = join(" / ", formatDuration(player.getBufferedDuration()), player.getBufferedPercentage() > 0 ? player.getBufferedPercentage() + "%" : "");
        String rebuffer = snapshot.rebufferCount() <= 0 ? "0 次" : snapshot.rebufferCount() + " 次 / " + formatDuration(snapshot.rebufferTotalMs());
        long stableThroughput = player.getNetworkProtectionStableThroughput();
        long consumption = player.getNetworkProtectionConsumption();
        String networkProtection = player.getNetworkProtectionText();
        String strategy = join(" / ",
                TextUtils.isEmpty(networkProtection) ? "" : networkProtection,
                "可支撑 " + new DecimalFormat("0.00x").format(player.getNetworkProtectionSupportedSpeed()),
                "当前 " + new DecimalFormat("0.00x").format(player.getEffectiveSpeed()));
        boolean localSource = PlaybackDiagnosticsSourcePolicy.isLocal(player.getUrl());
        String currentNetworkSpeed = !TextUtils.isEmpty(lastSpeedText)
                ? lastSpeedText : getBandwidthEstimateText(snapshot);
        String network = localSource ? "本地文件 / 不检测网速" : player.isExo() ? join(" / ",
                !TextUtils.isEmpty(currentNetworkSpeed) ? "当前 " + currentNetworkSpeed : "",
                consumption > 0 ? "消费需求 " + formatBitrate(consumption) : "",
                stableThroughput > 0 ? "稳定吞吐 " + formatBitrate(stableThroughput) : "",
                stableThroughput > 0 && consumption > 0 ? "网络余量 " + formatSignedBitrate(stableThroughput - consumption) : "")
                : join(" / ", "当前 " + emptyDash(lastSpeedText));
        if (TextUtils.isEmpty(network)) network = "待采样";
        String renderDiagnostics = player.isMpv() ? player.getRenderDiagnostics() : "";
        String runtimeDiagnostics = player.isMpv() ? player.getRuntimeDiagnostics() : "";
        String gpu = join(" / ", getSystemGpuText(),
                player.getGpuLoadDiagnostics());
        PlaybackPanelResourceMonitor.Snapshot resources = resourceMonitor.snapshot();
        String cpu = getCpuText(resources);
        String memory = getMemoryText(resources);
        String frameTiming = player.isExo() ? summarizeFrameTiming() : "";
        PlayerEngine.VideoPlaybackDetails videoDetails =
                player.getVideoPlaybackDetails();
        String videoText = summarizeVideo(video, player,
                snapshot.videoDecoderName(), getVideoTrackState(player),
                videoDetails);
        AudioTrackState audioTrack = getAudioTrackState(player);
        String audioText = summarizeAudio(audio, audioTrack, snapshot.audioDecoderName());
        String render = PlayerSetting.getRender() == PlayerSetting.RENDER_SURFACE ? "Surface" : "Texture";
        String tunnel = switchText(PlayerSetting.isTunnelingEnabled());
        String performance = PlaybackPerformanceSetting.getProfileName(player.getPlayerType());
        String passThrough = player.getAudioPassThroughText();
        String preload = "预载" + switchText(PreloadSetting.isPreload(player.getPlayerType()));
        String frameRateMatch = player.isExo() ? "帧率匹配 开" : "";
        String softTune = getSoftDecodeTuneText(player);
        String playerText = join(" / ", player.getPlayerText(), player.getDecodeText(), render, "隧道" + tunnel, "性能" + performance, frameRateMatch, preload, "直通" + passThrough, softTune, player.isExo() ? "兜底开" : "");
        String playback = join(" / ", state, buffer, "重缓冲 " + rebuffer, "掉帧 " + player.getDroppedFrames());
        String startup = getStartupText(player);
        String error = getErrorText(player, snapshot);
        String main = join("\n",
                TextUtils.isEmpty(error) ? "" : row("错误", error),
                row("视频", videoText),
                row("音频", audioText),
                row("网络", network),
                player.isExo() && !localSource ? row("动态网络保护", strategy) : "",
                TextUtils.isEmpty(renderDiagnostics) ? "" : row("MPV渲染", renderDiagnostics),
                TextUtils.isEmpty(runtimeDiagnostics) ? "" : row("MPV运行", runtimeDiagnostics),
                TextUtils.isEmpty(gpu) ? "" : row("GPU", gpu),
                TextUtils.isEmpty(cpu) ? "" : row("CPU", cpu),
                TextUtils.isEmpty(memory) ? "" : row("内存", memory),
                TextUtils.isEmpty(frameTiming) ? "" : row("帧调度", frameTiming),
                row("播放", playback),
                row("配置", playerText),
                TextUtils.isEmpty(startup) ? "" : row("起播", startup),
                row("结论", getDiagnosis(player, snapshot, video, audioTrack, localSource)));
        String extra = join("\n",
                row("设备", getDeviceText()),
                row("系统", getSystemText()),
                row("芯片", getChipText()),
                row("屏幕", getDisplayText()),
                row("WebView", getWebViewText()),
                row("网络环境", getNetworkEnvironmentText()));
        return new DiagnosticsText(main, extra);
    }

    /**
     * Startup timeline plus the stage that consumed the most time, so a slow start can
     * be attributed on-device instead of requiring a debug log export.
     */
    private String getStartupText(PlayerManager player) {
        String summary = player.getStartupSummary();
        if (TextUtils.isEmpty(summary)) return "";
        String slowest = player.getSlowestStartupStage();
        return TextUtils.isEmpty(slowest) ? summary : summary + "  最慢 " + slowest;
    }

    private String getDiagnosis(PlayerManager player, PlaybackAnalyticsListener.Snapshot snapshot, Format video, AudioTrackState audioTrack, boolean localSource) {
        if (isDecodeError(snapshot) && player.isHardDecode()) return "硬件解码失败：设备可能不支持该视频编码、分辨率、帧率或规格";
        if (!TextUtils.isEmpty(snapshot.errorCode())) return "播放器报错，先看错误行";
        if (audioTrack.hasTracks() && audioTrack.isUnsupported()) return "音频轨不支持：" + summarizeAudioFormat(audioTrack.format()) + " / " + supportText(audioTrack.support());
        if (player.isExo() && audioTrack.hasTracks() && !audioTrack.selected() && snapshot.audioFormat() == null && player.getPlaybackState() == androidx.media3.common.Player.STATE_READY) return "已发现音轨但未选中，可能无声";
        if (player.isExo() && audioTrack.hasTracks() && TextUtils.isEmpty(snapshot.audioDecoderName()) && player.getPlaybackState() == androidx.media3.common.Player.STATE_READY) return "已发现音轨但 decoder 未初始化，可能无声";
        if (!localSource) {
            long mediaBitrate = getMediaBitrate(video, snapshot.audioFormat() != null ? snapshot.audioFormat() : audioTrack.format());
            long availableBitrate = snapshot.bandwidthEstimate() > 0 ? snapshot.bandwidthEstimate() : lastSpeedKBps * 1024 * 8;
            if (availableBitrate > 0 && mediaBitrate > 0 && availableBitrate < mediaBitrate * 13 / 10) return "网速可能低于资源码率";
            // Must use the raw buffered duration: getBufferedDuration() folds in completed
            // disk ranges, which reads high enough that this hint could never fire.
            if (player.isLoading() && player.getNativeBufferedDuration() < 3000) return "缓冲偏少，可能是网络或源响应慢";
        }
        if (player.getDroppedFrames() >= 60) return "掉帧较多，可能是解码或渲染压力";
        if (!localSource && formatBitrateValue(video) >= 30_000_000) return "资源码率较高，对网络和解码要求高";
        if (player.isExo() && audioTrack.hasTracks() && snapshot.audioFormat() == null) return "正在等待音频轨信息";
        return "正常";
    }

    private String getSystemGpuText() {
        GpuLoadMonitor.Snapshot system = GpuLoadMonitor.process().snapshot();
        if (!system.available()) return "";
        return join(" / ",
                String.format(Locale.US, "当前 %.0f%%", system.percent()),
                String.format(Locale.US, "峰值 %.0f%%", system.peakPercent()),
                formatGpuFrequency(system.frequencyHz()));
    }

    private String getCpuText(PlaybackPanelResourceMonitor.Snapshot snapshot) {
        if (snapshot == null || !snapshot.cpuAvailable()) return "";
        return String.format(Locale.US,
                "App 当前 %.0f%% / 10秒 %.0f%% / 峰值 %.0f%%",
                snapshot.cpuPercent(), snapshot.cpuAveragePercent(),
                snapshot.cpuPeakPercent());
    }

    private String getMemoryText(PlaybackPanelResourceMonitor.Snapshot snapshot) {
        if (snapshot == null || !snapshot.memoryAvailable()) return "";
        String javaHeap = snapshot.javaHeapUsedBytes() < 0 ? ""
                : "Java " + formatMemoryBytes(snapshot.javaHeapUsedBytes())
                + (snapshot.javaHeapLimitBytes() >= 0
                ? "/" + formatMemoryBytes(snapshot.javaHeapLimitBytes()) : "");
        String system = snapshot.systemAvailableBytes() < 0 ? ""
                : "系统可用 " + formatMemoryBytes(snapshot.systemAvailableBytes())
                + (snapshot.systemTotalBytes() >= 0
                ? "/" + formatMemoryBytes(snapshot.systemTotalBytes()) : "");
        return join(" / ",
                snapshot.pssBytes() >= 0
                        ? "PSS " + formatMemoryBytes(snapshot.pssBytes()) : "",
                snapshot.graphicsPssBytes() > 0
                        ? "图形 " + formatMemoryBytes(snapshot.graphicsPssBytes()) : "",
                javaHeap,
                snapshot.nativeHeapBytes() >= 0
                        ? "Native " + formatMemoryBytes(snapshot.nativeHeapBytes()) : "",
                system);
    }

    private String formatGpuFrequency(long frequencyHz) {
        if (frequencyHz <= 0) return "";
        if (frequencyHz >= 1_000_000_000L) {
            return String.format(Locale.US, "%.2fGHz", frequencyHz / 1_000_000_000.0);
        }
        return String.format(Locale.US, "%.0fMHz", frequencyHz / 1_000_000.0);
    }

    private String formatMemoryBytes(long bytes) {
        if (bytes < 0) return "";
        double mib = bytes / (1024.0 * 1024.0);
        if (mib >= 1024) return String.format(Locale.US, "%.1fGB", mib / 1024.0);
        if (mib >= 100) return String.format(Locale.US, "%.0fMB", mib);
        return String.format(Locale.US, "%.1fMB", mib);
    }

    private void startDiagnosticsSampling(PlayerManager player) {
        if (diagnosticsSamplingPlayer != null
                && diagnosticsSamplingPlayer != player) {
            diagnosticsSamplingPlayer.setGpuLoadDiagnosticsEnabled(false);
        }
        diagnosticsSamplingPlayer = player;
        player.setGpuLoadDiagnosticsEnabled(true);
        if (!resourceMonitor.isActive()) resourceMonitor.start();
        GpuLoadMonitor.process().start();
    }

    private void stopDiagnosticsSampling() {
        if (diagnosticsSamplingPlayer != null) {
            diagnosticsSamplingPlayer.setGpuLoadDiagnosticsEnabled(false);
            diagnosticsSamplingPlayer = null;
        }
        resourceMonitor.stop();
        GpuLoadMonitor.process().stop();
    }

    private String getErrorText(PlayerManager player, PlaybackAnalyticsListener.Snapshot snapshot) {
        String raw = join(" ", snapshot.errorCode(), shortText(snapshot.errorMessage(), 72));
        String decoder = TextUtils.isEmpty(snapshot.errorDecoderName()) ? "" : "decoder " + snapshot.errorDecoderName();
        String diagnostic = TextUtils.isEmpty(snapshot.errorDiagnosticInfo()) ? "" : "diagnostic " + snapshot.errorDiagnosticInfo();
        String secure = snapshot.errorSecureDecoderRequired() ? "secure required" : "";
        String cause = TextUtils.isEmpty(snapshot.errorCause()) ? "" : "cause " + shortText(snapshot.errorCause(), 72);
        String explanation = getErrorExplanation(player, snapshot);
        return join(" / ", raw, decoder, diagnostic, secure, cause, explanation);
    }

    private String getErrorExplanation(PlayerManager player, PlaybackAnalyticsListener.Snapshot snapshot) {
        if (isDecodeError(snapshot) && player.isHardDecode()) return "中文说明：硬解失败，设备硬件解码器可能不支持当前视频规格";
        if (isDecodeError(snapshot)) return "中文说明：软解/解码流程失败，请尝试切回硬解或更换资源";
        return "";
    }

    private String getSoftDecodeTuneText(PlayerManager player) {
        // The hard-decode profile still falls back to the FFmpeg renderer for codecs
        // MediaCodec refuses, and that fallback now gets load shedding too. Hiding the status
        // whenever the profile says hardware would conceal it in exactly the case where the
        // user needs to confirm it is active.
        if (player.isHardDecode() && !player.isHardProfileRunningSoftware()) return "";
        if (player.isIjk()) {
            // Report what IJK actually applied. It forces TuneMode.OFF in the hard-decode
            // profile even with a mode configured, so claiming shedding is active whenever
            // this row is reachable would be false exactly in the fallback case.
            IjkDecodePressurePolicy.TuneMode mode = player.getAppliedIjkTuneMode();
            if (mode == null || mode == IjkDecodePressurePolicy.TuneMode.OFF) return "软解降负载 关";
            return "软解降负载 IJK跳帧/滤波 " + mode.label();
        }
        if (player.isMpv()) return "软解降负载 MPV hwdec=no";
        return PlaybackPerformanceSetting.isSoftVideoTuneEnabled() ? "软解降负载 EXO滤波/低分辨" : "软解降负载 关";
    }

    private boolean isDecodeError(PlaybackAnalyticsListener.Snapshot snapshot) {
        String code = snapshot.errorCode();
        return "ERROR_CODE_DECODER_INIT_FAILED".equals(code) || "ERROR_CODE_DECODER_QUERY_FAILED".equals(code) || "ERROR_CODE_DECODING_FAILED".equals(code);
    }

    private String summarizeVideo(Format format, PlayerManager player,
                                  String decoder,
                                  VideoTrackState videoTrack,
                                  PlayerEngine.VideoPlaybackDetails details) {
        if (videoTrack.hasTracks()) format = mergeFormat(format, videoTrack.format());
        String size = getSize(format, player);
        String fps = getFrameRate(format, player);
        String bitrate = getBitrate(format, player);
        boolean dolbyVision = details != null
                && details.hasDolbyVisionSource();
        String formatName = dolbyVision
                ? DolbyVisionFormatLabel.formatName(details)
                : getVideoCodecName(format);
        String codecValue = dolbyVision
                ? DolbyVisionFormatLabel.codecText(details)
                : format == null ? "" : format.codecs;
        String codec = TextUtils.isEmpty(codecValue)
                ? "codec -" : "codec " + codecValue;
        String color = getColor(format).replace("color ", "色彩 ");
        String support = videoTrack.hasTracks() && !videoTrack.isHandled() ? supportText(videoTrack.support()) : "";
        String decode = "decoder " + emptyDash(decoderText(player, decoder));
        return join(" / ",
                "格式 " + emptyDash(formatName),
                "分辨率 " + emptyDash(size),
                "帧率 " + emptyDash(fps),
                "码率 " + emptyDash(bitrate),
                codec,
                TextUtils.isEmpty(color) ? "色彩 -" : color,
                decode,
                support,
                videoTrack.supportSummary());
    }

    private Format mergeFormat(Format primary, Format fallback) {
        if (primary == null) return fallback;
        if (fallback == null) return primary;
        Format.Builder builder = primary.buildUpon();
        if (TextUtils.isEmpty(primary.sampleMimeType) && !TextUtils.isEmpty(fallback.sampleMimeType)) builder.setSampleMimeType(fallback.sampleMimeType);
        if (TextUtils.isEmpty(primary.codecs) && !TextUtils.isEmpty(fallback.codecs)) builder.setCodecs(fallback.codecs);
        if (primary.width <= 0 && fallback.width > 0) builder.setWidth(fallback.width);
        if (primary.height <= 0 && fallback.height > 0) builder.setHeight(fallback.height);
        if (primary.frameRate <= 0 && fallback.frameRate > 0) builder.setFrameRate(fallback.frameRate);
        if (formatBitrateValue(primary) <= 0 && formatBitrateValue(fallback) > 0) builder.setAverageBitrate(formatBitrateValue(fallback));
        if (primary.colorInfo == null && fallback.colorInfo != null) builder.setColorInfo(fallback.colorInfo);
        return builder.build();
    }

    private String summarizeAudio(Format format, AudioTrackState audioTrack, String decoder) {
        if (format == null) {
            if (!audioTrack.hasTracks()) return "未发现音轨";
            return join(" / ", summarizeAudioFormat(audioTrack.format()), supportText(audioTrack.support()), audioTrack.supportSummary(), audioTrack.selected() ? "已选中" : "未选中");
        }
        String support = audioTrack.hasTracks() && !audioTrack.isHandled() ? supportText(audioTrack.support()) : "";
        return join(" / ", join(" ", summarizeAudioFormat(format), getBitrate(format)), TextUtils.isEmpty(audioDecoderText(decoder)) ? "" : "dec " + audioDecoderText(decoder), support);
    }

    private String decoderText(PlayerManager player, String decoder) {
        if (!TextUtils.isEmpty(decoder)) return decoder;
        if (player == null) return "";
        if (player.isMpv()) {
            String hwdec = player.getVideoPlaybackDetails().hwdecCurrent();
            if (!TextUtils.isEmpty(hwdec)) return "MPV " + hwdec;
            return player.isHardDecode() ? "MPV mediacodec" : "MPV ffmpeg";
        }
        if (player.isIjk()) return player.isHardDecode() ? "IJK mediacodec" : "IJK ffmpeg";
        return "";
    }

    private String audioDecoderText(String decoder) {
        if (!TextUtils.isEmpty(decoder)) return decoder;
        return "";
    }

    private String summarizeAudioFormat(Format format) {
        if (format == null) return "";
        String channels = format.channelCount <= 0 ? "" : format.channelCount + "ch";
        String sampleRate = format.sampleRate <= 0 ? "" : format.sampleRate % 1000 == 0 ? (format.sampleRate / 1000) + "kHz" : bitrateFormat.format(format.sampleRate / 1000f) + "kHz";
        return join(" ", getAudioMime(format), channels, sampleRate, TextUtils.isEmpty(format.language) ? "" : format.language);
    }

    private AudioTrackState getAudioTrackState(PlayerManager player) {
        if (player == null) return AudioTrackState.empty();
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null) return AudioTrackState.empty();
        AudioTrackCandidate selected = null;
        AudioTrackCandidate handled = null;
        AudioTrackCandidate unsupported = null;
        AudioTrackCandidate first = null;
        int total = 0;
        int supported = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_AUDIO) continue;
            for (int i = 0; i < group.length; i++) {
                total++;
                int support = group.getTrackSupport(i);
                boolean isSelected = group.isTrackSelected(i);
                if (support == C.FORMAT_HANDLED) supported++;
                AudioTrackCandidate candidate = new AudioTrackCandidate(group.getTrackFormat(i), support, isSelected);
                if (first == null) first = candidate;
                if (isSelected) selected = candidate;
                if (handled == null && support == C.FORMAT_HANDLED) handled = candidate;
                if (unsupported == null && isUnsupportedSupport(support)) unsupported = candidate;
            }
        }
        AudioTrackCandidate candidate = selected != null ? selected : handled != null ? handled : unsupported != null ? unsupported : first;
        return candidate == null ? new AudioTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, total, supported) : new AudioTrackState(candidate.format(), candidate.support(), candidate.selected(), total, supported);
    }

    private VideoTrackState getVideoTrackState(PlayerManager player) {
        if (player == null) return VideoTrackState.empty();
        Tracks tracks = player.getCurrentTracks();
        if (tracks == null) return VideoTrackState.empty();
        VideoTrackCandidate selected = null;
        VideoTrackCandidate handled = null;
        VideoTrackCandidate exceeds = null;
        VideoTrackCandidate unsupported = null;
        VideoTrackCandidate first = null;
        int total = 0;
        int supported = 0;
        for (Tracks.Group group : tracks.getGroups()) {
            if (group.getType() != C.TRACK_TYPE_VIDEO) continue;
            for (int i = 0; i < group.length; i++) {
                total++;
                int support = group.getTrackSupport(i);
                boolean isSelected = group.isTrackSelected(i);
                if (support == C.FORMAT_HANDLED) supported++;
                VideoTrackCandidate candidate = new VideoTrackCandidate(group.getTrackFormat(i), support, isSelected);
                if (first == null) first = candidate;
                if (isSelected) selected = candidate;
                if (handled == null && support == C.FORMAT_HANDLED) handled = candidate;
                if (exceeds == null && support == C.FORMAT_EXCEEDS_CAPABILITIES) exceeds = candidate;
                if (unsupported == null && isUnsupportedSupport(support)) unsupported = candidate;
            }
        }
        VideoTrackCandidate candidate = selected != null ? selected : handled != null ? handled : exceeds != null ? exceeds : unsupported != null ? unsupported : first;
        return candidate == null ? new VideoTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, total, supported) : new VideoTrackState(candidate.format(), candidate.support(), candidate.selected(), total, supported);
    }

    private boolean isUnsupportedSupport(int support) {
        return support == C.FORMAT_UNSUPPORTED_TYPE || support == C.FORMAT_UNSUPPORTED_SUBTYPE || support == C.FORMAT_UNSUPPORTED_DRM;
    }

    private String supportText(int support) {
        return switch (support) {
            case C.FORMAT_HANDLED -> "支持";
            case C.FORMAT_EXCEEDS_CAPABILITIES -> "超出设备声明能力: EXCEEDS_CAPABILITIES，不应判定为可硬解";
            case C.FORMAT_UNSUPPORTED_DRM -> "不支持: NO_UNSUPPORTED_DRM";
            case C.FORMAT_UNSUPPORTED_SUBTYPE -> "不支持: NO_UNSUPPORTED_SUBTYPE";
            case C.FORMAT_UNSUPPORTED_TYPE -> "不支持: NO_UNSUPPORTED_TYPE";
            default -> "支持状态 " + support;
        };
    }

    private String getSize(Format format, PlayerManager player) {
        int width = format == null || format.width <= 0 ? player.getVideoWidth() : format.width;
        int height = format == null || format.height <= 0 ? player.getVideoHeight() : format.height;
        return width <= 0 || height <= 0 ? "" : width + "x" + height;
    }

    private String getFrameRate(Format format) {
        if (format == null || format.frameRate <= 0) return "";
        return frameFormat.format(format.frameRate) + "fps";
    }

    private String getFrameRate(Format format, PlayerManager player) {
        String declared = getFrameRate(format);
        if (!TextUtils.isEmpty(declared) || player == null || !player.isExo()) return declared;
        PlaybackAnalyticsListener.DisplayFrameRateEstimate estimate = PlaybackAnalyticsListener.getDisplayFrameRateEstimate();
        return estimate.frameRate() <= 0 ? "" : frameFormat.format(estimate.frameRate()) + "fps";
    }

    private String getBitrate(Format format) {
        return format == null ? "" : formatBitrate(formatBitrateValue(format));
    }

    private String getBitrate(Format format, PlayerManager player) {
        String declared = getBitrate(format);
        if (!TextUtils.isEmpty(declared) || player == null || !player.isExo()) return declared;
        PlaybackAnalyticsListener.DisplayMediaBitrateEstimate estimate = PlaybackAnalyticsListener.getDisplayMediaBitrateEstimate(format);
        return estimate.bitrateBitsPerSecond() <= 0 ? "" : formatBitrate(estimate.bitrateBitsPerSecond());
    }

    private String getBandwidthEstimateText(PlaybackAnalyticsListener.Snapshot snapshot) {
        if (snapshot.bandwidthEstimate() > 0) return formatBitrate(snapshot.bandwidthEstimate());
        long realtimeEstimate = lastSpeedKBps * 1024L * 8L;
        return realtimeEstimate > 0 ? formatBitrate(realtimeEstimate) : "";
    }

    private String formatSignedBitrate(long bitsPerSecond) {
        return (bitsPerSecond >= 0 ? "+" : "-") + formatBitrate(Math.abs(bitsPerSecond));
    }

    private String summarizeFrameTiming() {
        com.fongmi.android.tv.player.exo.ExoFrameTimingMetrics.Snapshot timing = PlaybackAnalyticsListener.getFrameTimingSnapshot();
        com.fongmi.android.tv.player.exo.ExoFrameSchedulingExperimentMetrics.Snapshot experiment = PlaybackAnalyticsListener.getFrameSchedulingExperimentSnapshot();
        String unit = experiment.experimentApplied()
                ? "帧调度A/B " + experiment.earlySchedulingThresholdUs() / 1000
                + "ms / 耗时推进"
                + (experiment.durationToProgressRequested() ? "开" : "关")
                : "";
        if (timing.frameCount() <= 0 && timing.releaseFrameCount() <= 0 && timing.codecErrorCount() <= 0) return unit;
        if (timing.codecErrorCount() > 0) return join(" / ", unit, "解码错误 " + timing.codecErrorCount());
        if (timing.lateReleaseFrameCount() > 0) {
            return join(" / ", unit, "释放滞后 " + timing.lateReleaseFrameCount() + " 帧 / 最大延迟 " + bitrateFormat.format(timing.maxLateReleaseUs() / 1000f) + "ms");
        }
        if (timing.lateBatchCount() > 0) return join(" / ", unit, "调度滞后 " + timing.lateBatchCount() + " 批");
        if (timing.releaseJitterSampleCount() > 0 && timing.averageReleaseJitterUs() >= 5_000) {
            return join(" / ", unit, "释放抖动 " + bitrateFormat.format(timing.averageReleaseJitterUs() / 1000f) + "ms");
        }
        return unit;
    }

    private String getColor(Format format) {
        if (format == null || format.colorInfo == null) return "";
        String color = format.colorInfo.toLogString();
        if (TextUtils.isEmpty(color)) return "";
        color = color.replace("Limited range", "Limited").replace("Full range", "Full").replace("SMPTE 170M", "SMPTE170M");
        return "color " + join(" ", outputHdrName(format), color);
    }

    private String outputHdrName(Format format) {
        if (format == null || format.colorInfo == null) return "";
        if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084) return "HDR10";
        if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) return "HLG";
        if (androidx.media3.common.ColorInfo.isTransferHdr(format.colorInfo)) return "HDR";
        if (format.colorInfo.colorTransfer == C.COLOR_TRANSFER_SDR
                || format.colorInfo.colorTransfer == C.COLOR_TRANSFER_SRGB
                || format.colorInfo.colorTransfer == C.COLOR_TRANSFER_LINEAR) return "SDR";
        return "";
    }

    private long getMediaBitrate(Format video, Format audio) {
        long bitrate = 0;
        if (video != null && formatBitrateValue(video) > 0) bitrate += formatBitrateValue(video);
        if (audio != null && formatBitrateValue(audio) > 0) bitrate += formatBitrateValue(audio);
        return bitrate;
    }

    private int formatBitrateValue(Format format) {
        if (format == null) return 0;
        if (format.bitrate > 0) return format.bitrate;
        if (format.averageBitrate > 0) return format.averageBitrate;
        if (format.peakBitrate > 0) return format.peakBitrate;
        return 0;
    }

    private String getMime(Format format) {
        if (format == null) return "";
        if (!TextUtils.isEmpty(format.sampleMimeType)) {
            int index = format.sampleMimeType.indexOf('/');
            return index >= 0 && index + 1 < format.sampleMimeType.length() ? format.sampleMimeType.substring(index + 1) : format.sampleMimeType;
        }
        return TextUtils.isEmpty(format.codecs) ? "" : format.codecs;
    }

    private String getVideoCodecName(Format format) {
        if (format == null) return "";
        String mime = TextUtils.isEmpty(format.sampleMimeType) ? "" : format.sampleMimeType.toLowerCase(Locale.ROOT);
        String codecs = TextUtils.isEmpty(format.codecs) ? "" : format.codecs.toLowerCase(Locale.ROOT);
        if ("video/dolby-vision".equals(mime) || codecs.startsWith("dvhe") || codecs.startsWith("dvh1")) return "Dolby Vision";
        if (MimeTypes.VIDEO_H265.equals(mime) || codecs.startsWith("hvc1") || codecs.startsWith("hev1")) return "H.265 / HEVC";
        if (MimeTypes.VIDEO_H264.equals(mime) || codecs.startsWith("avc1") || codecs.startsWith("avc3")) return "H.264 / AVC";
        if (MimeTypes.VIDEO_AV1.equals(mime) || codecs.startsWith("av01")) return "AV1";
        if (MimeTypes.VIDEO_VP9.equals(mime) || codecs.startsWith("vp09")) return "VP9";
        if (MimeTypes.VIDEO_VP8.equals(mime) || codecs.startsWith("vp08")) return "VP8";
        if (MimeTypes.VIDEO_MPEG2.equals(mime)) return "MPEG-2";
        if (MimeTypes.VIDEO_MP4V.equals(mime)) return "MPEG-4";
        return getMime(format);
    }

    private String getAudioMime(Format format) {
        if (format == null) return "";
        String mime = format.sampleMimeType;
        if (isCodec(format, MimeTypes.CODEC_DTS_HD_MA_X_IMAX)) return "DTS:X IMAX";
        if (isCodec(format, MimeTypes.CODEC_DTS_HD_MA_X)) return "DTS:X";
        if (MimeTypes.AUDIO_DTS_HD_MA.equals(mime) || MimeTypes.AUDIO_MEDIA3_DTS_HD_MA_CORELESS.equals(mime)) return "DTS-HD MA";
        if (MimeTypes.AUDIO_DTS_EXPRESS.equals(mime)) return "DTS-HD LBR";
        if (MimeTypes.AUDIO_DTS_UHD_P2.equals(mime)) return "DTS-UHD P2";
        if (MimeTypes.AUDIO_DTS_HD.equals(mime)) return "DTS-HD";
        if (MimeTypes.AUDIO_DTS.equals(mime)) return "DTS";
        if (MimeTypes.AUDIO_TRUEHD.equals(mime)) return "TrueHD";
        if (MimeTypes.AUDIO_E_AC3_JOC.equals(mime)) return "E-AC3 JOC";
        if (MimeTypes.AUDIO_E_AC3.equals(mime)) return "E-AC3";
        if (MimeTypes.AUDIO_AC3.equals(mime)) return "AC3";
        if (MimeTypes.AUDIO_AAC.equals(mime)) return "AAC";
        if (MimeTypes.AUDIO_FLAC.equals(mime)) return "FLAC";
        if (MimeTypes.AUDIO_MPEG.equals(mime)) return "MP3";
        if (MimeTypes.AUDIO_OPUS.equals(mime)) return "Opus";
        if (MimeTypes.AUDIO_AMR.equals(mime) || MimeTypes.AUDIO_AMR_NB.equals(mime)) return "AMR-NB";
        if (MimeTypes.AUDIO_AMR_WB.equals(mime)) return "AMR-WB";
        return getMime(format);
    }

    private boolean isCodec(Format format, String codec) {
        return !TextUtils.isEmpty(format.codecs) && format.codecs.contains(codec);
    }

    private String formatBitrate(long bitrate) {
        if (bitrate <= 0) return "";
        float mbps = bitrate / 1_000_000f;
        if (mbps < 1) return Math.round(bitrate / 1000f) + "Kbps";
        return bitrateFormat.format(mbps) + "Mbps";
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "";
        float kb = bytes / 1024f;
        if (kb < 1024) return Math.round(kb) + "KB";
        return bitrateFormat.format(kb / 1024f) + "MB";
    }

    private String formatDuration(long ms) {
        if (ms <= 0) return "";
        if (ms >= 60_000) return Util.timeMs(ms);
        return bitrateFormat.format(ms / 1000f) + " s";
    }

    private String getDisplayRefreshText() {
        if (root.getDisplay() == null || root.getDisplay().getRefreshRate() <= 0) return "";
        return refreshFormat.format(root.getDisplay().getRefreshRate()) + " Hz";
    }

    private String getDeviceText() {
        String value = cachedDeviceText;
        if (value != null) return value;
        return cachedDeviceText = join(" / ",
                join(" ", emptyDash(Build.MANUFACTURER), emptyDash(Build.MODEL)),
                "device " + emptyDash(Build.DEVICE),
                "abi " + String.join(",", Build.SUPPORTED_ABIS));
    }

    private String getSystemText() {
        String value = cachedSystemText;
        if (value != null) return value;
        return cachedSystemText = join(" / ",
                "Android " + emptyDash(Build.VERSION.RELEASE),
                "SDK " + Build.VERSION.SDK_INT,
                "incremental " + emptyDash(Build.VERSION.INCREMENTAL));
    }

    private String getChipText() {
        return join(" / ",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ? "soc " + emptyDash(Build.SOC_MANUFACTURER) + " " + emptyDash(Build.SOC_MODEL) : "",
                "hardware " + emptyDash(Build.HARDWARE),
                "board " + emptyDash(Build.BOARD));
    }

    private String getWebViewText() {
        String value = cachedWebViewText;
        if (value != null) return value;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return cachedWebViewText = "provider unavailable / SDK " + Build.VERSION.SDK_INT;
        try {
            PackageInfo info = WebView.getCurrentWebViewPackage();
            if (info == null) return cachedWebViewText = "provider unavailable";
            long code = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? info.getLongVersionCode() : info.versionCode;
            return cachedWebViewText = join(" / ", info.versionName, info.packageName, "code " + code);
        } catch (Throwable e) {
            return cachedWebViewText = "provider query failed: " + e.getClass().getSimpleName();
        }
    }

    private String getDisplayText() {
        Display display = root.getDisplay();
        DisplayMetrics metrics = App.get().getResources().getDisplayMetrics();
        String appSize = metrics.widthPixels > 0 && metrics.heightPixels > 0 ? "app " + metrics.widthPixels + "x" + metrics.heightPixels : "";
        String refresh = getDisplayRefreshText();
        if (display == null) return join(" / ", appSize, TextUtils.isEmpty(refresh) ? "" : refresh);
        Display.Mode mode = display.getMode();
        String modeText = mode == null ? "" : Math.max(mode.getPhysicalWidth(), mode.getPhysicalHeight()) + "x" + Math.min(mode.getPhysicalWidth(), mode.getPhysicalHeight()) + "@" + refreshFormat.format(mode.getRefreshRate()) + "Hz";
        return join(" / ", appSize, modeText, TextUtils.isEmpty(refresh) ? "" : "current " + refresh, getDisplayModesText(display));
    }

    private String getDisplayModesText(Display display) {
        try {
            Display.Mode[] modes = display.getSupportedModes();
            if (modes == null || modes.length <= 1) return "";
            StringBuilder builder = new StringBuilder("modes ");
            int count = 0;
            for (Display.Mode mode : modes) {
                String hz = refreshFormat.format(mode.getRefreshRate());
                if (builder.toString().contains(hz + "Hz")) continue;
                if (count++ > 0) builder.append("/");
                builder.append(hz).append("Hz");
                if (count >= 6) break;
            }
            return builder.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String getNetworkEnvironmentText() {
        return join(" / ", getActiveNetworkText(), getSystemProxyText(), getAppProxyText());
    }

    private String getActiveNetworkText() {
        try {
            ConnectivityManager manager = (ConnectivityManager) App.get().getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "";
            NetworkCapabilities caps = manager.getNetworkCapabilities(manager.getActiveNetwork());
            if (caps == null) return "network unavailable";
            String type = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ? "WiFi" :
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ? "Ethernet" :
                            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ? "Cellular" :
                                    caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ? "VPN" : "Other";
            String validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ? "validated" : "not validated";
            String metered = manager.isActiveNetworkMetered() ? "metered" : "unmetered";
            return join(" ", type, validated, metered);
        } catch (Throwable e) {
            return "network query failed";
        }
    }

    private String getSystemProxyText() {
        String host = System.getProperty("http.proxyHost");
        String port = System.getProperty("http.proxyPort");
        return TextUtils.isEmpty(host) ? "system proxy 关" : "system proxy " + host + (TextUtils.isEmpty(port) ? "" : ":" + port);
    }

    private String getAppProxyText() {
        if (!Setting.isShellProxy()) return "app proxy 关";
        String url = Setting.getShellProxyUrl();
        return "app proxy 开" + (TextUtils.isEmpty(url) ? "" : " " + shortText(url, 36));
    }

    private String getHevcDecoderText() {
        String value = cachedHevcDecoderText;
        if (value != null) return value;
        try {
            HevcDecoderSummary best = null;
            int count = 0;
            for (MediaCodecInfo info : new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos()) {
                if (info.isEncoder() || !isHardwareCodec(info)) continue;
                HevcDecoderSummary summary = summarizeHevcDecoder(info);
                if (summary == null) continue;
                count++;
                if (best == null || summary.score() > best.score()) best = summary;
            }
            if (best == null) return cachedHevcDecoderText = "未发现硬件 HEVC decoder";
            return cachedHevcDecoderText = best.text() + (count > 1 ? " / decoders " + count : "");
        } catch (Throwable e) {
            return cachedHevcDecoderText = "query failed: " + e.getClass().getSimpleName();
        }
    }

    private HevcDecoderSummary summarizeHevcDecoder(MediaCodecInfo info) {
        try {
            MediaCodecInfo.VideoCapabilities caps = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_HEVC).getVideoCapabilities();
            boolean uhd60 = supports(caps, 3840, 2160, 60);
            boolean uhd30 = supports(caps, 3840, 2160, 30);
            boolean qhd60 = supports(caps, 2560, 1440, 60);
            boolean fhd60 = supports(caps, 1920, 1080, 60);
            int score = (uhd60 ? 8 : 0) + (uhd30 ? 4 : 0) + (qhd60 ? 2 : 0) + (fhd60 ? 1 : 0);
            String bitrate = "";
            try {
                android.util.Range<Integer> range = caps.getBitrateRange();
                if (range != null && range.getUpper() > 0) bitrate = "bitrate<=" + formatBitrate(range.getUpper());
            } catch (Throwable ignored) {
            }
            String text = join(" / ",
                    "decoder " + info.getName(),
                    "4K60=" + yesNo(uhd60),
                    "4K30=" + yesNo(uhd30),
                    "1440p60=" + yesNo(qhd60),
                    "1080p60=" + yesNo(fhd60),
                    TextUtils.isEmpty(bitrate) ? "" : bitrate.replace("bitrate<=", "码率上限 "));
            return new HevcDecoderSummary(score, text);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean supports(MediaCodecInfo.VideoCapabilities caps, int width, int height, double fps) {
        try {
            return caps.areSizeAndRateSupported(width, height, fps) || caps.areSizeAndRateSupported(height, width, fps);
        } catch (Throwable e) {
            return false;
        }
    }

    private boolean isHardwareCodec(MediaCodecInfo info) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated();
        String name = info.getName().toLowerCase(Locale.US);
        return !name.contains("google") && !name.contains("android") && !name.contains("ffmpeg") && !name.contains("software") && !name.startsWith("c2.android");
    }

    private String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private String summarizeSource(String url) {
        if (TextUtils.isEmpty(url)) return "";
        try {
            Uri uri = Uri.parse(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            String path = uri.getPath();
            String type = sourceType(scheme, path, url);
            String ext = extension(path);
            return join(" ", type, TextUtils.isEmpty(host) ? emptyDash(scheme) : scheme + "://" + host, ext);
        } catch (Throwable ignored) {
            return shortText(url, 80);
        }
    }

    private String sourceType(String scheme, String path, String url) {
        String lower = url.toLowerCase(Locale.US);
        if ("file".equals(scheme) || "content".equals(scheme)) return "local";
        PlaybackRoute route = PlaybackRoute.classify(url);
        if (route == PlaybackRoute.APP_LOCAL_SERVICE) return "app-local-service";
        if (route == PlaybackRoute.EXTERNAL_LOOPBACK_PROXY) return "external-local-endpoint";
        if (lower.contains(".m3u8")) return "hls";
        if (lower.contains(".mpd")) return "dash";
        if (lower.startsWith("rtsp")) return "rtsp";
        if (lower.startsWith("rtp")) return "rtp";
        if (path != null && path.contains(".")) return "file";
        return TextUtils.isEmpty(scheme) ? "unknown" : scheme;
    }

    private String extension(String path) {
        if (TextUtils.isEmpty(path)) return "";
        int slash = path.lastIndexOf('/');
        int dot = path.lastIndexOf('.');
        if (dot <= slash || dot + 1 >= path.length()) return "";
        String ext = path.substring(dot + 1);
        return ext.length() > 8 ? "" : ext;
    }

    private String stateName(int state) {
        return switch (state) {
            case androidx.media3.common.Player.STATE_IDLE -> "IDLE";
            case androidx.media3.common.Player.STATE_BUFFERING -> "BUFFERING";
            case androidx.media3.common.Player.STATE_READY -> "READY";
            case androidx.media3.common.Player.STATE_ENDED -> "ENDED";
            default -> String.valueOf(state);
        };
    }

    private String stateText(int state) {
        return switch (state) {
            case androidx.media3.common.Player.STATE_IDLE -> "空闲(IDLE)";
            case androidx.media3.common.Player.STATE_BUFFERING -> "缓冲中(BUFFERING)";
            case androidx.media3.common.Player.STATE_READY -> "就绪(READY)";
            case androidx.media3.common.Player.STATE_ENDED -> "结束(ENDED)";
            default -> stateName(state);
        };
    }

    private String join(String separator, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (TextUtils.isEmpty(value)) continue;
            if (builder.length() > 0) builder.append(separator);
            builder.append(value);
        }
        return builder.toString();
    }

    private String row(String label, String value) {
        return label + "  " + (TextUtils.isEmpty(value) ? "-" : value);
    }

    private String switchText(boolean enabled) {
        return enabled ? "开" : "关";
    }

    private String emptyDash(String value) {
        return TextUtils.isEmpty(value) ? "-" : value;
    }

    private int dp(int value) {
        return Math.round(value * App.get().getResources().getDisplayMetrics().density);
    }

    private String shortText(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        return value.substring(0, Math.max(0, max - 1)) + "...";
    }

    private void resetSpeed() {
        speedMeter.reset();
        lastSpeedKBps = 0;
        lastSpeedText = "";
    }

    private record AudioTrackCandidate(Format format, int support, boolean selected) {
    }

    private record VideoTrackCandidate(Format format, int support, boolean selected) {
    }

    private record HevcDecoderSummary(int score, String text) {
    }

    private record DiagnosticsText(String main, String extra) {

        String all() {
            return TextUtils.isEmpty(extra) ? main : main + "\n" + extra;
        }
    }

    private record AudioTrackState(Format format, int support, boolean selected, int total, int supported) {

        static AudioTrackState empty() {
            return new AudioTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, 0, 0);
        }

        boolean hasTracks() {
            return total > 0;
        }

        boolean isHandled() {
            return support == C.FORMAT_HANDLED;
        }

        boolean isUnsupported() {
            return support == C.FORMAT_UNSUPPORTED_TYPE || support == C.FORMAT_UNSUPPORTED_SUBTYPE || support == C.FORMAT_UNSUPPORTED_DRM;
        }

        String supportSummary() {
            return total <= 1 ? "" : "音轨 " + supported + "/" + total + " 支持";
        }
    }

    private record VideoTrackState(Format format, int support, boolean selected, int total, int supported) {

        static VideoTrackState empty() {
            return new VideoTrackState(null, C.FORMAT_UNSUPPORTED_TYPE, false, 0, 0);
        }

        boolean hasTracks() {
            return total > 0;
        }

        boolean isHandled() {
            return support == C.FORMAT_HANDLED;
        }

        String supportSummary() {
            return total <= 1 ? "" : "视频轨 " + supported + "/" + total + " 支持";
        }
    }
}
