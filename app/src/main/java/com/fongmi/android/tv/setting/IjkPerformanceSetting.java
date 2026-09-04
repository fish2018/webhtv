package com.fongmi.android.tv.setting;

import com.github.catvod.utils.Prefers;

public final class IjkPerformanceSetting {

    public static final int SCENE_AUTO = 0;
    public static final int SCENE_VOD = 1;
    public static final int SCENE_LIVE_STABLE = 2;
    public static final int SCENE_LIVE_LOW_LATENCY = 3;
    public static final int WATER_LOW = 0;
    public static final int WATER_STANDARD = 1;
    public static final int WATER_STABLE = 2;
    public static final int DROP_OFF = 0;
    public static final int DROP_STANDARD = 1;
    public static final int DROP_AGGRESSIVE = 2;
    public static final int PROBE_AUTO = 0;
    public static final int PROBE_FAST = 1;
    public static final int PROBE_FULL = 2;
    public static final int SOFT_TUNE_OFF = 0;
    public static final int SOFT_TUNE_MILD = 1;
    public static final int SOFT_TUNE_AGGRESSIVE = 2;
    public static final int RTSP_AUTO = 0;
    public static final int RTSP_TCP = 1;
    public static final int RTSP_UDP = 2;

    private static final String KEY_SCENE = "perf_ijk_scene";
    private static final String KEY_BUFFER_MB = "perf_ijk_buffer_mb";
    private static final String KEY_PACKET_BUFFERING = "perf_ijk_packet_buffering";
    private static final String KEY_WATER = "perf_ijk_water";
    private static final String KEY_PICTURE_QUEUE = "perf_ijk_picture_queue";
    private static final String KEY_DROP = "perf_ijk_drop";
    private static final String KEY_ACCURATE_SEEK = "perf_ijk_accurate_seek";
    private static final String KEY_PROBE = "perf_ijk_probe";
    private static final String KEY_SOFT_TUNE = "perf_ijk_soft_tune";
    private static final String KEY_RTSP_TRANSPORT = "perf_ijk_rtsp_transport";
    private static final String KEY_RECONNECT = "perf_ijk_reconnect";

    private IjkPerformanceSetting() {
    }

    public static int getScene() {
        return clamp(Prefers.getInt(KEY_SCENE, SCENE_AUTO), SCENE_AUTO, SCENE_LIVE_LOW_LATENCY);
    }

    public static void putScene(int value) {
        int scene = clamp(value, SCENE_AUTO, SCENE_LIVE_LOW_LATENCY);
        Prefers.put(KEY_SCENE, scene);
        boolean automaticProfile = PlaybackPerformanceSetting.isAuto(PlayerSetting.IJK);
        if (!automaticProfile && scene == SCENE_LIVE_STABLE) {
            Prefers.put(KEY_PACKET_BUFFERING, true);
            Prefers.put(KEY_WATER, WATER_STABLE);
            Prefers.put(KEY_PICTURE_QUEUE, 5);
            Prefers.put(KEY_PROBE, PROBE_FULL);
        } else if (!automaticProfile && scene == SCENE_LIVE_LOW_LATENCY) {
            Prefers.put(KEY_PACKET_BUFFERING, false);
            Prefers.put(KEY_WATER, WATER_LOW);
            Prefers.put(KEY_PICTURE_QUEUE, 3);
            Prefers.put(KEY_PROBE, PROBE_FAST);
        } else if (!automaticProfile && scene == SCENE_VOD) {
            Prefers.put(KEY_PACKET_BUFFERING, true);
            Prefers.put(KEY_WATER, WATER_STANDARD);
            Prefers.put(KEY_PICTURE_QUEUE, 3);
            Prefers.put(KEY_PROBE, PROBE_AUTO);
        }
        PlaybackPerformanceSetting.setOverride(
                PlaybackPerformanceCatalog.IJK_SCENE,
                scene != SCENE_AUTO);
    }

    public static String getSceneText() {
        return switch (getScene()) {
            case SCENE_VOD -> "点播";
            case SCENE_LIVE_STABLE -> "直播稳定";
            case SCENE_LIVE_LOW_LATENCY -> "直播低延迟";
            default -> "自动";
        };
    }

    public static int getBufferMb() {
        int value = Prefers.getInt(KEY_BUFFER_MB, 15);
        return value <= 4 ? 4 : value <= 8 ? 8 : 15;
    }

    public static void putBufferMb(int value) {
        Prefers.put(KEY_BUFFER_MB, value <= 4 ? 4 : value <= 8 ? 8 : 15);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_BUFFER);
    }

    public static boolean isPacketBuffering() {
        return Prefers.getBoolean(KEY_PACKET_BUFFERING, true);
    }

    public static void putPacketBuffering(boolean value) {
        Prefers.put(KEY_PACKET_BUFFERING, value);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_PACKET_BUFFERING);
    }

    public static int getWaterMode() {
        return clamp(Prefers.getInt(KEY_WATER, WATER_STANDARD), WATER_LOW, WATER_STABLE);
    }

    public static void putWaterMode(int value) {
        Prefers.put(KEY_WATER, clamp(value, WATER_LOW, WATER_STABLE));
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_WATER);
    }

    public static String getWaterText() {
        return switch (getWaterMode()) {
            case WATER_LOW -> "低";
            case WATER_STABLE -> "稳定";
            default -> "标准";
        };
    }

    public static int getFirstWaterMs() {
        return getWaterMode() == WATER_STABLE ? 500 : 100;
    }

    public static int getNextWaterMs() {
        return switch (getWaterMode()) {
            case WATER_LOW -> 300;
            case WATER_STABLE -> 2000;
            default -> 1000;
        };
    }

    public static int getLastWaterMs() {
        return getWaterMode() == WATER_LOW ? 1000 : 5000;
    }

    public static int getPictureQueue() {
        int value = Prefers.getInt(KEY_PICTURE_QUEUE, 3);
        return value <= 3 ? 3 : value <= 5 ? 5 : 8;
    }

    public static void putPictureQueue(int value) {
        Prefers.put(KEY_PICTURE_QUEUE, value <= 3 ? 3 : value <= 5 ? 5 : 8);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_PICTURE_QUEUE);
    }

    public static int getDropMode() {
        return clamp(Prefers.getInt(KEY_DROP, DROP_STANDARD), DROP_OFF, DROP_AGGRESSIVE);
    }

    public static void putDropMode(int value) {
        Prefers.put(KEY_DROP, clamp(value, DROP_OFF, DROP_AGGRESSIVE));
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_FRAME_DROP);
    }

    public static int getFrameDropValue() {
        return switch (getDropMode()) {
            case DROP_OFF -> 0;
            case DROP_AGGRESSIVE -> 5;
            default -> 1;
        };
    }

    public static String getDropText() {
        return switch (getDropMode()) {
            case DROP_OFF -> "关闭";
            case DROP_AGGRESSIVE -> "积极";
            default -> "标准";
        };
    }

    public static boolean isAccurateSeek() {
        return Prefers.getBoolean(KEY_ACCURATE_SEEK);
    }

    public static void putAccurateSeek(boolean value) {
        Prefers.put(KEY_ACCURATE_SEEK, value);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_ACCURATE_SEEK);
    }

    public static int getProbeMode() {
        return clamp(Prefers.getInt(KEY_PROBE, PROBE_AUTO), PROBE_AUTO, PROBE_FULL);
    }

    public static void putProbeMode(int value) {
        int mode = clamp(value, PROBE_AUTO, PROBE_FULL);
        Prefers.put(KEY_PROBE, mode);
        PlaybackPerformanceSetting.setOverride(
                PlaybackPerformanceCatalog.IJK_PROBE,
                mode != PROBE_AUTO);
    }

    public static String getProbeText() {
        return switch (getProbeMode()) {
            case PROBE_FAST -> "快速";
            case PROBE_FULL -> "完整";
            default -> "系统默认";
        };
    }

    public static int getSoftTuneMode() {
        return clamp(Prefers.getInt(KEY_SOFT_TUNE, SOFT_TUNE_MILD), SOFT_TUNE_OFF, SOFT_TUNE_AGGRESSIVE);
    }

    public static void putSoftTuneMode(int value) {
        Prefers.put(KEY_SOFT_TUNE, clamp(value, SOFT_TUNE_OFF, SOFT_TUNE_AGGRESSIVE));
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_SOFT_TUNE);
    }

    public static String getSoftTuneText() {
        return switch (getSoftTuneMode()) {
            case SOFT_TUNE_OFF -> "关闭";
            case SOFT_TUNE_AGGRESSIVE -> "积极";
            default -> "温和";
        };
    }

    public static int getRtspTransport() {
        return clamp(Prefers.getInt(KEY_RTSP_TRANSPORT, RTSP_TCP), RTSP_AUTO, RTSP_UDP);
    }

    public static void putRtspTransport(int value) {
        int transport = clamp(value, RTSP_AUTO, RTSP_UDP);
        Prefers.put(KEY_RTSP_TRANSPORT, transport);
        PlaybackPerformanceSetting.setOverride(
                PlaybackPerformanceCatalog.IJK_RTSP_TRANSPORT,
                transport != RTSP_AUTO);
    }

    public static String getRtspTransportText() {
        return switch (getRtspTransport()) {
            case RTSP_AUTO -> "自动";
            case RTSP_UDP -> "UDP";
            default -> "TCP";
        };
    }

    public static boolean isReconnect() {
        return Prefers.getBoolean(KEY_RECONNECT, true);
    }

    public static void putReconnect(boolean value) {
        Prefers.put(KEY_RECONNECT, value);
        PlaybackPerformanceSetting.markOverride(PlaybackPerformanceCatalog.IJK_RECONNECT);
    }

    public static void applyRecommended() {
        Prefers.put(KEY_SCENE, SCENE_AUTO);
        Prefers.put(KEY_BUFFER_MB, 15);
        Prefers.put(KEY_PACKET_BUFFERING, true);
        Prefers.put(KEY_WATER, WATER_STANDARD);
        Prefers.put(KEY_PICTURE_QUEUE, 3);
        Prefers.put(KEY_DROP, DROP_STANDARD);
        Prefers.put(KEY_ACCURATE_SEEK, false);
        Prefers.put(KEY_PROBE, PROBE_AUTO);
        Prefers.put(KEY_SOFT_TUNE, SOFT_TUNE_MILD);
        Prefers.put(KEY_RTSP_TRANSPORT, RTSP_TCP);
        Prefers.put(KEY_RECONNECT, true);
    }

    public static void applyCompatible() {
        applyLightweight();
    }

    public static void applyLightweight() {
        Prefers.put(KEY_SCENE, SCENE_AUTO);
        Prefers.put(KEY_BUFFER_MB, 8);
        Prefers.put(KEY_PACKET_BUFFERING, true);
        Prefers.put(KEY_WATER, WATER_STABLE);
        Prefers.put(KEY_PICTURE_QUEUE, 3);
        Prefers.put(KEY_DROP, DROP_STANDARD);
        Prefers.put(KEY_ACCURATE_SEEK, false);
        Prefers.put(KEY_PROBE, PROBE_AUTO);
        Prefers.put(KEY_SOFT_TUNE, SOFT_TUNE_MILD);
        Prefers.put(KEY_RTSP_TRANSPORT, RTSP_TCP);
        Prefers.put(KEY_RECONNECT, true);
    }

    private static int clamp(int value, int min, int max) {
        return Math.min(Math.max(value, min), max);
    }
}
