package com.fongmi.android.tv.player;

import android.app.Activity;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.service.IntroSkipService.Segment;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.crawler.SpiderDebug;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * 片段类型（回顾/片头/片尾/预告）的名称、提示与多选框，三个播放页共用一份。
 */
public final class IntroSkipKinds {

    /** 与多选框行序一致的位掩码，改动顺序时两处要一起改。 */
    private static final int[] FLAGS = {
            Setting.INTRO_SKIP_KIND_RECAP,
            Setting.INTRO_SKIP_KIND_INTRO,
            Setting.INTRO_SKIP_KIND_OUTRO,
            Setting.INTRO_SKIP_KIND_PREVIEW,
    };

    private static final int[] LABELS = {
            R.string.intro_skip_kind_recap,
            R.string.intro_skip_kind_intro,
            R.string.intro_skip_kind_outro,
            R.string.intro_skip_kind_preview,
    };

    private IntroSkipKinds() {
    }

    public static String name(Segment segment) {
        if (segment == null) return "";
        switch (segment.getKind()) {
            case RECAP: return ResUtil.getString(R.string.intro_skip_kind_recap);
            case INTRO: return ResUtil.getString(R.string.intro_skip_kind_intro);
            case OUTRO: return ResUtil.getString(R.string.intro_skip_kind_outro);
            case PREVIEW: return ResUtil.getString(R.string.intro_skip_kind_preview);
            default: return "";
        }
    }

    /** 确认弹框的文案。 */
    public static int confirmMessage(Segment segment) {
        if (segment == null) return R.string.intro_skip_confirm_intro;
        switch (segment.getKind()) {
            case RECAP: return R.string.intro_skip_confirm_recap;
            case OUTRO: return R.string.intro_skip_confirm_outro;
            case PREVIEW: return R.string.intro_skip_confirm_preview;
            default: return R.string.intro_skip_confirm_intro;
        }
    }

    /**
     * 自动跳过后的提示。seeked 为假表示该段一直放到文件结束、按「本集看完」处理，
     * 提示要说明去了下一集，否则用户只看到画面突然换集。
     */
    public static void notifySkipped(Segment segment, boolean seeked) {
        String name = name(segment);
        String text = name.isEmpty() ? "" : ResUtil.getString(seeked ? R.string.intro_skip_notice_seeked : R.string.intro_skip_notice_ended, name);
        SpiderDebug.log("intro-skip", "notice kind=%s seeked=%s text=%s", segment == null ? null : segment.getKind(), seeked, text);
        if (!text.isEmpty()) Notify.show(text);
    }

    /** 当前选择的摘要，用于设置页那一行的右侧文字。 */
    public static String summary() {
        int kinds = Setting.getIntroSkipKinds();
        if (kinds == 0) return ResUtil.getString(R.string.intro_skip_kinds_none);
        if (kinds == Setting.INTRO_SKIP_KIND_ALL) return ResUtil.getString(R.string.intro_skip_kinds_all);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < FLAGS.length; i++) {
            if ((kinds & FLAGS[i]) == 0) continue;
            if (text.length() > 0) text.append(" / ");
            text.append(ResUtil.getString(LABELS[i]));
        }
        return text.toString();
    }

    /**
     * 弹出多选框。改动缓存在本地数组里，只有点确定才落库，取消即真的取消。
     */
    public static void show(Activity activity, Runnable onChanged) {
        if (activity == null || activity.isFinishing()) return;
        int kinds = Setting.getIntroSkipKinds();
        String[] labels = new String[FLAGS.length];
        boolean[] checked = new boolean[FLAGS.length];
        for (int i = 0; i < FLAGS.length; i++) {
            labels[i] = ResUtil.getString(LABELS[i]);
            checked[i] = (kinds & FLAGS[i]) != 0;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setTitle(R.string.setting_intro_skip_kinds)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> {
                    int result = 0;
                    for (int i = 0; i < FLAGS.length; i++) if (checked[i]) result |= FLAGS[i];
                    Setting.putIntroSkipKinds(result);
                    if (onChanged != null) onChanged.run();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .create();
        dialog.show();
        LightDialog.apply(dialog); // 必须在 show() 之后：它要量列表高度，并接上 TV 的 D-pad 焦点
    }
}
