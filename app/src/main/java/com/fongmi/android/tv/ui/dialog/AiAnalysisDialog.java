package com.fongmi.android.tv.ui.dialog;

import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public final class AiAnalysisDialog {

    private AiAnalysisDialog() {
    }

    public static AlertDialog show(FragmentActivity activity, Runnable onCancel) {
        FrameLayout container = new FrameLayout(activity);
        container.setMinimumHeight(ResUtil.dp2px(72));
        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        progress.setContentDescription(activity.getString(R.string.tmdb_season_ai_analyzing));
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ResUtil.dp2px(40), ResUtil.dp2px(40), Gravity.CENTER);
        container.addView(progress, params);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.tmdb_season_ai_loading_title)
                .setMessage(R.string.tmdb_season_ai_analyzing)
                .setView(container)
                .create();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener(ignored -> {
            if (onCancel != null) onCancel.run();
        });
        dialog.show();
        return dialog;
    }
}
