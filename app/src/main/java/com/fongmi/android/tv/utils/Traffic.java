package com.fongmi.android.tv.utils;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.player.PlaybackSpeedMeter;
import com.fongmi.android.tv.player.PlayerManager;

import java.util.Map;
import java.util.WeakHashMap;

public class Traffic {

    /**
     * One meter per readout. The counters behind a meter are process-wide, so a shared
     * meter would let whichever screen sampled first consume the interval and leave the
     * others measuring a near-zero elapsed time — and a reset from any screen would
     * re-base the baseline the others are still measuring against.
     */
    private static final Map<TextView, PlaybackSpeedMeter> meters = new WeakHashMap<>();

    /**
     * Renders the current download speed, preferring the playback kernel's own byte
     * accounting so the readout still works on ROMs whose per-UID TrafficStats
     * counter is missing. Pass the active player, or null when none is attached.
     */
    public static void setSpeed(TextView view, @Nullable PlayerManager player) {
        PlaybackSpeedMeter meter = meter(view);
        meter.sample(player);
        String text = meter.getText();
        view.setText(text);
        view.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    public static void reset(TextView view) {
        meter(view).reset();
    }

    private static synchronized PlaybackSpeedMeter meter(TextView view) {
        return meters.computeIfAbsent(view, key -> new PlaybackSpeedMeter());
    }
}
