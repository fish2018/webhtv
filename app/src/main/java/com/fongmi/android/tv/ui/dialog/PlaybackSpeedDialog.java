package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;

import java.util.Arrays;
import java.util.Locale;

/** Playback shortcut speeds, presented using the same chooser as player kernels. */
public final class PlaybackSpeedDialog {

    public interface Listener {
        void onSelected(float speed);
    }

    private PlaybackSpeedDialog() {
    }

    public static void show(FragmentActivity activity, float current, Listener listener) {
        if (activity.isFinishing() || activity.isDestroyed()
                || activity.getSupportFragmentManager().isStateSaved()) return;
        Choices choices = choices(current);
        ChoiceDialog.showSingleNoCancel(activity, R.string.setting_play_speed,
                choices.labels(), choices.selected(), which -> listener.onSelected(choices.speeds()[which]));
    }

    static Choices choices(float current) {
        float[] speeds = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 5f};
        // Keep a speed selected through the slider or remote fine adjustment visible.
        if (current >= 0.25f && current <= 5f && selectedIndex(speeds, current) < 0) {
            speeds = Arrays.copyOf(speeds, speeds.length + 1);
            speeds[speeds.length - 1] = current;
            Arrays.sort(speeds);
        }
        String[] labels = new String[speeds.length];
        for (int i = 0; i < speeds.length; i++) labels[i] = String.format(Locale.US, "%.2fx", speeds[i]);
        return new Choices(speeds, labels, selectedIndex(speeds, current));
    }

    private static int selectedIndex(float[] speeds, float current) {
        for (int i = 0; i < speeds.length; i++) if (Math.abs(speeds[i] - current) < 0.001f) return i;
        return -1;
    }

    record Choices(float[] speeds, String[] labels, int selected) {
    }
}
