package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.VideoAspectMode;
import com.fongmi.android.tv.utils.ResUtil;

import java.util.function.IntConsumer;

public final class VideoAspectModeDialog {

    private VideoAspectModeDialog() {
    }

    public static void show(FragmentActivity activity, int currentMode, IntConsumer onSelect) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        ChoiceDialog.showSingle(activity, R.string.player_scale, labels(), VideoAspectMode.displayIndex(currentMode),
                which -> select(activity, which, onSelect));
    }

    public static void show(Fragment fragment, int currentMode, IntConsumer onSelect) {
        if (fragment == null || !fragment.isAdded()) return;
        ChoiceDialog.showSingle(fragment, R.string.player_scale, labels(), VideoAspectMode.displayIndex(currentMode),
                which -> select(fragment.requireActivity(), which, onSelect));
    }

    private static CharSequence[] labels() {
        String[] source = ResUtil.getStringArray(R.array.select_scale);
        int[] order = VideoAspectMode.displayOrder();
        CharSequence[] labels = new CharSequence[order.length];
        for (int index = 0; index < order.length; index++) {
            int mode = order[index];
            labels[index] = mode >= 0 && mode < source.length ? source[mode] : "";
        }
        return labels;
    }

    private static void select(FragmentActivity activity, int displayIndex, IntConsumer onSelect) {
        if (onSelect == null || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        int mode = VideoAspectMode.modeAtDisplayIndex(displayIndex);
        if (VideoAspectMode.isCustom(mode)) {
            activity.getWindow().getDecorView().post(() ->
                    VideoAspectRatioDialog.show(activity, () -> onSelect.accept(mode)));
        } else {
            onSelect.accept(mode);
        }
    }
}
