package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.diagnostics.DiagnosticCategories;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

public final class DebugLogDialog {

    private DebugLogDialog() {
    }

    public static void show(Fragment fragment) {
        show(fragment.requireActivity());
    }

    public static void show(FragmentActivity activity) {
        Server.get().start();
        String localUrl = Server.get().getAddress("/debug/logs");
        String lanUrl = Server.get().getAddress(false) + "/debug/logs";
        String xbpqUrl = Server.get().getAddress("/proxy?do=log");
        SpiderDebug.log("debug", "logs service ready url=%s lan=%s xbpq=%s", localUrl, lanUrl, xbpqUrl);
        String message = activity.getString(R.string.debug_log_dialog_message, lanUrl, localUrl, xbpqUrl);

        MaterialTextView content = new MaterialTextView(activity);
        content.setText(message);
        content.setTextColor(Color.parseColor("#5F6368"));
        content.setTextSize(14);
        content.setLineSpacing(ResUtil.dp2px(2), 1f);

        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.addView(content);

        for (DiagnosticCategories.Category category : DiagnosticCategories.Category.values()) {
            SwitchCompat toggle = new SwitchCompat(activity);
            toggle.setText(category.title);
            toggle.setTextColor(Color.parseColor("#202124"));
            toggle.setBackgroundResource(R.drawable.selector_light_dialog_item);
            toggle.setPadding(ResUtil.dp2px(12), ResUtil.dp2px(8), ResUtil.dp2px(12), ResUtil.dp2px(8));
            toggle.setFocusable(true);
            toggle.setFocusableInTouchMode(Util.isLeanback());
            toggle.setChecked(DiagnosticCategories.accepts(DebugLogStore.categories(), category));
            toggle.setOnCheckedChangeListener((button, checked) -> DebugLogStore.setCategory(category, checked));
            panel.addView(toggle);
        }

        MaterialTextView captureNote = new MaterialTextView(activity);
        captureNote.setText("标准日志按容量轮转；深度统计只保留数值，不保存画面或声音。");
        captureNote.setTextSize(14);
        panel.addView(captureNote);

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton mark = outlinedButton(activity, "标记此刻故障", true, true);
        actionRow.addView(mark);
        mark.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(activity).setTitle("选择当前现象")
                .setItems(com.fongmi.android.tv.player.DiagnosticControls.SYMPTOMS, (d, which) -> {
                    try {
                        com.fongmi.android.tv.player.DiagnosticControls.mark(com.fongmi.android.tv.player.DiagnosticControls.SYMPTOMS[which]);
                        Notify.show("已标记，继续记录后 15 秒");
                    } catch (RuntimeException error) {
                        Notify.show(error.getMessage());
                    }
                }).show());

        MaterialButton depth = outlinedButton(activity, "深度统计 60 秒", true, false);
        actionRow.addView(depth);
        depth.setOnClickListener(v -> {
            androidx.appcompat.app.AlertDialog depthDialog = new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("限时深度统计")
                    .setMessage("对当前播放做少量低分辨率画面和 PCM 数值统计，不保存图像或声音。到期、切换播放或关闭诊断自动停止。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("开启 60 秒", (d, which) -> {
                        try {
                            com.fongmi.android.tv.player.DiagnosticControls.startDepth(60);
                            Notify.show("限时统计已开启");
                        } catch (RuntimeException error) {
                            Notify.show(error.getMessage());
                        }
                    })
                    .create();
            depthDialog.setOnShowListener(d -> {
                if (Util.isLeanback()) {
                    android.widget.Button negative = depthDialog.getButton(DialogInterface.BUTTON_NEGATIVE);
                    if (negative != null) negative.requestFocus();
                }
            });
            depthDialog.show();
        });

        MaterialButton stop = outlinedButton(activity, "停止深度统计", true, false);
        actionRow.addView(stop);
        stop.setOnClickListener(v -> {
            com.github.catvod.crawler.diagnostics.DiagnosticCapture.stop("user-stopped");
            Notify.show("深度统计已停止");
        });

        panel.addView(actionRow);

        ScrollView scroll = new ScrollView(activity);
        scroll.addView(panel);

        Dialog dialog = LightDialog.create(activity, activity.getString(R.string.setting_debug_log), scroll,
                activity.getString(R.string.debug_log_open_browser), v -> open(activity, localUrl),
                activity.getString(R.string.dialog_negative), null,
                activity.getString(R.string.debug_log_copy_url), v -> copy(activity, lanUrl));
        dialog.show();
    }

    private static MaterialButton outlinedButton(Context context, String text, boolean horizontal, boolean first) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setSingleLine(true);
        button.setMaxLines(1);
        button.setGravity(Gravity.CENTER);
        button.setTextSize(14);
        button.setIncludeFontPadding(false);
        button.setPadding(ResUtil.dp2px(6), 0, ResUtil.dp2px(6), 0);
        button.setMinWidth(ResUtil.dp2px(88));
        button.setMinimumWidth(0);
        button.setMinHeight(ResUtil.dp2px(40));
        button.setMinimumHeight(ResUtil.dp2px(40));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setCornerRadius(ResUtil.dp2px(6));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_bg));
        button.setStrokeColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(ResUtil.dp2px(1));
        LinearLayout.LayoutParams params;
        if (horizontal) {
            params = new LinearLayout.LayoutParams(0, ResUtil.dp2px(40), 1f);
        } else {
            params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(40));
        }
        params.topMargin = ResUtil.dp2px(8);
        params.leftMargin = (horizontal && !first) ? ResUtil.dp2px(6) : 0;
        button.setLayoutParams(params);
        return button;
    }

    private static void open(FragmentActivity activity, String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            Notify.show(R.string.debug_log_no_browser);
        }
    }

    private static void copy(FragmentActivity activity, String url) {
        ClipboardManager manager = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (manager == null) return;
        manager.setPrimaryClip(ClipData.newPlainText(activity.getString(R.string.setting_debug_log), url));
        Notify.show(R.string.debug_log_url_copied);
    }
}
