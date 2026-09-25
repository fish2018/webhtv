package com.fongmi.android.tv.lab;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.App;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class LabOutputDialog implements LabRunner.OutputListener {

    private final Context context;
    private final TextView output;
    private final ScrollView scroll;
    private final String key;
    private AlertDialog dialog;
    private Runnable exitAction;

    public LabOutputDialog(Context context, String key) {
        this.context = context;
        this.key = key;
        this.output = new TextView(context);
        this.output.setTextColor(Color.parseColor("#00E676"));
        this.output.setTextSize(13);
        this.output.setTypeface(android.graphics.Typeface.MONOSPACE);
        this.output.setPadding(24, 24, 24, 24);
        this.output.setTextIsSelectable(true);
        this.scroll = new ScrollView(context);
        this.scroll.addView(output);
    }

    public void show(String title) {
        dialog = new MaterialAlertDialogBuilder(context, com.fongmi.android.tv.R.style.Theme_App_Lab_Dialog)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("停止", (d, w) -> LabRunner.stop(key))
                .setPositiveButton("关闭", (d, w) -> d.dismiss())
                .setCancelable(false)
                .create();
        dialog.show();
    }

    public void setExitAction(Runnable runnable) {
        this.exitAction = runnable;
    }

    public boolean isShowing() {
        return dialog != null && dialog.isShowing();
    }

    @Override
    public void onOutput(String text) {
        if (dialog == null || !dialog.isShowing()) return;
        App.post(() -> {
            int range = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
            boolean atBottom = scroll.getScrollY() >= range - 4;
            output.append(text);
            if (atBottom) scroll.fullScroll(View.FOCUS_DOWN);
        });
    }

    @Override
    public void onExit(int code) {
        App.post(() -> {
            if (dialog != null && dialog.isShowing()) {
                output.append("\n[进程结束，退出码 " + code + "]\n");
            }
            if (exitAction != null) exitAction.run();
        });
    }
}
