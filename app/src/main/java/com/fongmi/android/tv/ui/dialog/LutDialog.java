package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.lut.LutPreset;
import com.fongmi.android.tv.player.lut.LutSetting;
import com.fongmi.android.tv.player.lut.LutStore;

import java.util.List;

public class LutDialog {

    private static final int[] STRENGTHS = new int[]{25, 50, 75, 100};

    public static void show(Fragment fragment, Runnable callback) {
        show(fragment.requireContext(), fragment.getChildFragmentManager(), null, callback);
    }

    public static void show(FragmentActivity activity, PlayerManager player, Runnable callback) {
        show(activity, activity.getSupportFragmentManager(), player, callback);
    }

    private static void show(Context context, FragmentManager manager, PlayerManager player, Runnable callback) {
        List<LutPreset> presets = LutStore.refreshPresets();
        ChoiceDialog.showSingle(manager, titleText(context, player, presets), labels(context, presets), checkedIndex(presets), strengthText(context), () -> {
            LutSetting.putStrength(nextStrength());
            applyChange(player, callback);
            return strengthText(context);
        }, which -> {
            LutPreset preset = which == 0 ? null : presets.get(which - 1);
            if (player != null) {
                if (!player.selectLut(preset, false)) return;
                if (callback != null) callback.run();
            } else {
                LutSetting.select(preset);
                applyChange(null, callback);
            }
        });
    }

    private static void applyChange(PlayerManager player, Runnable callback) {
        if (player != null) player.applyLut(true);
        if (callback != null) callback.run();
    }

    private static int nextStrength() {
        int current = LutSetting.getStrength();
        for (int strength : STRENGTHS) if (current < strength) return strength;
        return STRENGTHS[0];
    }

    private static CharSequence[] labels(Context context, List<LutPreset> presets) {
        CharSequence[] labels = new CharSequence[presets.size() + 1];
        labels[0] = context.getString(R.string.lut_original);
        for (int i = 0; i < presets.size(); i++) labels[i + 1] = presets.get(i).getName();
        return labels;
    }

    private static int checkedIndex(List<LutPreset> presets) {
        if (!LutSetting.isEnabled()) return 0;
        String id = LutSetting.getPresetId();
        for (int i = 0; i < presets.size(); i++) if (presets.get(i).getId().equals(id)) return i + 1;
        return 0;
    }

    private static String titleText(Context context, PlayerManager player, List<LutPreset> presets) {
        String title = context.getString(R.string.lut_title_value, context.getString(R.string.player_lut), LutSetting.getSummary());
        String message = messageText(context, player, presets);
        return TextUtils.isEmpty(message) ? title : title + "\n" + message;
    }

    private static String strengthText(Context context) {
        return context.getString(R.string.lut_strength_value, LutSetting.getStrength());
    }

    private static String messageText(Context context, PlayerManager player, List<LutPreset> presets) {
        StringBuilder builder = new StringBuilder();
        if (player != null && !TextUtils.isEmpty(player.getLutUnavailableReason())) builder.append(player.getLutUnavailableReason());
        if (presets.isEmpty()) {
            if (builder.length() > 0) builder.append('\n');
            builder.append(context.getString(R.string.lut_empty_presets));
        }
        return builder.toString();
    }
}
