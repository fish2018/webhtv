package com.fongmi.android.tv.player;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;

import com.fongmi.android.tv.App;
import com.github.catvod.crawler.DebugLogStore;

import static com.github.catvod.crawler.diagnostics.DiagnosticEvent.Status.*;

/** Snapshot only: never requests focus or changes volume/routing. Called on the owning player looper. */
public final class SystemAudioDiagnosticCollector {
    private SystemAudioDiagnosticCollector() {}

    public static void snapshot(PlaybackDiagnosticCollector log, PlaybackDiagnosticCollector.Context owner) {
        if (!PlaybackDiagnosticCollector.enabled()) return;
        try {
            AudioManager manager = (AudioManager) App.get().getSystemService(Context.AUDIO_SERVICE);
            if (manager == null) return;
            log.emit(owner, "audio.volume", "AudioManager", "system-stream-not-output-route", e -> {
                e.observed("systemVolume", manager.getStreamVolume(AudioManager.STREAM_MUSIC))
                        .observed("systemMaxVolume", manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                        .observed("fixedVolume", manager.isVolumeFixed()).unknown("audibility", NOT_OBSERVABLE);
                if (Build.VERSION.SDK_INT >= 23) e.observed("muted", manager.isStreamMute(AudioManager.STREAM_MUSIC));
                else e.unknown("muted", NOT_SUPPORTED);
                if (Build.VERSION.SDK_INT >= 28) e.observed("systemMinVolume", manager.getStreamMinVolume(AudioManager.STREAM_MUSIC));
                else e.unknown("systemMinVolume", NOT_SUPPORTED);
            });
            if (Build.VERSION.SDK_INT >= 23) for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
                log.emit(owner, "audio.route", "AudioManager.getDevices", "advertised-only", e -> e
                        .observed("routeRole", "discovered").observed("routeId", "device-" + device.getId())
                        .observed("routeType", device.getType()).observed("encoding", java.util.Arrays.toString(device.getEncodings()))
                        .observed("channelMask", java.util.Arrays.toString(device.getChannelMasks()))
                        .observed("sampleRate", java.util.Arrays.toString(device.getSampleRates())));
            }
        } catch (RuntimeException ignored) { DebugLogStore.collectorFailure(); }
    }
}
