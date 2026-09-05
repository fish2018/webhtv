package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.history.HistorySourceResolver;
import com.fongmi.android.tv.playback.HistoryResumePayload;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.LightDialog;
import com.fongmi.android.tv.ui.novel.NovelRouter;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public final class HistoryResumeCoordinator {

    private static final AtomicInteger REQUESTS = new AtomicInteger();

    private HistoryResumeCoordinator() {
    }

    public static void open(Activity activity, History history) {
        if (activity == null || history == null) return;
        REQUESTS.incrementAndGet();
        if (!Setting.isGlobalHistoryEnabled() || history.isCurrentSourceAvailable()) {
            if (NovelRouter.openHistory(activity, history, VodConfig.getCid(),
                    () -> VideoActivity.startFromHistory(activity, history))) return;
            VideoActivity.startFromHistory(activity, history);
            return;
        }
        if (Setting.isGlobalHistorySearch()) {
            SearchActivity.directFromHistory(activity, history);
            return;
        }
        resolveAuto(activity, history);
    }

    public static void openSelected(Activity activity, int sourceCid, String sourceKey, int targetCid, Vod selected) {
        if (activity == null || selected == null) return;
        if (VodConfig.getCid() != targetCid) {
            Notify.show(R.string.history_config_changed);
            return;
        }
        History history = HistoryResumePayload.restore(sourceCid, sourceKey);
        if (history == null) {
            Notify.show(R.string.history_source_load_failed);
            return;
        }
        int request = REQUESTS.incrementAndGet();
        Dialog loading = showLoading(activity);
        Task.execute(() -> {
            HistorySourceResolver.Resolved resolved = HistorySourceResolver.resolveSelected(history, selected);
            App.post(() -> {
                dismiss(loading);
                if (!isCurrent(activity, request)) return;
                if (VodConfig.getCid() != targetCid) {
                    Notify.show(R.string.history_config_changed);
                    return;
                }
                History current = HistoryResumePayload.restore(sourceCid, sourceKey);
                if (current == null) {
                    Notify.show(R.string.history_record_missing);
                    return;
                }
                if (!isSameResumeVersion(history, current)) {
                    openSelected(activity, sourceCid, sourceKey, targetCid, selected);
                    return;
                }
                if (resolved == null) {
                    Notify.show(R.string.history_source_episode_missing);
                    return;
                }
                if (NovelRouter.openHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode(), targetCid,
                        () -> VideoActivity.startFromResolvedHistory(activity, current,
                                resolved.vod(), resolved.flag(), resolved.episode()))) return;
                VideoActivity.startFromResolvedHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode());
            });
        });
    }

    public static void openSearch(Activity activity, int sourceCid, String sourceKey, int targetCid, String keyword) {
        if (activity == null) return;
        if (VodConfig.getCid() != targetCid) {
            Notify.show(R.string.history_config_changed);
            return;
        }
        History history = HistoryResumePayload.restore(sourceCid, sourceKey);
        if (history == null) {
            Notify.show(R.string.history_source_load_failed);
            return;
        }
        REQUESTS.incrementAndGet();
        SearchActivity.directFromHistory(activity, history, keyword, targetCid);
    }

    private static void resolveAuto(Activity activity, History history) {
        int request = REQUESTS.incrementAndGet();
        int targetCid = VodConfig.getCid();
        String resumePayload = HistoryResumePayload.encode(history);
        Dialog loading = showLoading(activity);
        Task.execute(() -> {
            HistorySourceResolver.Resolved resolved = HistorySourceResolver.resolveAuto(history);
            App.post(() -> {
                dismiss(loading);
                if (!isCurrent(activity, request)) return;
                if (VodConfig.getCid() != targetCid) {
                    Notify.show(R.string.history_config_changed);
                    return;
                }
                History current = HistoryResumePayload.restore(history.getCid(), resumePayload);
                if (current == null) {
                    Notify.show(R.string.history_record_missing);
                    return;
                }
                if (!isSameResumeVersion(history, current)) {
                    resolveAuto(activity, current);
                    return;
                }
                if (resolved == null) {
                    showSearchFallback(activity, current, targetCid);
                    return;
                }
                if (NovelRouter.openHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode(), targetCid,
                        () -> VideoActivity.startFromResolvedHistory(activity, current,
                                resolved.vod(), resolved.flag(), resolved.episode()))) return;
                VideoActivity.startFromResolvedHistory(activity, current, resolved.vod(), resolved.flag(), resolved.episode());
            });
        });
    }

    private static Dialog showLoading(Activity activity) {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.HORIZONTAL);
        content.setGravity(Gravity.CENTER_VERTICAL);
        int padding = ResUtil.dp2px(24);
        content.setPadding(padding, padding, padding, padding);

        ProgressBar progress = new ProgressBar(activity);
        progress.setIndeterminate(true);
        int progressSize = ResUtil.dp2px(32);
        content.addView(progress, new LinearLayout.LayoutParams(progressSize, progressSize));

        TextView message = new TextView(activity);
        message.setText(R.string.history_source_searching);
        message.setTextColor(ContextCompat.getColor(activity, R.color.black_80));
        message.setTextSize(18);
        message.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.leftMargin = ResUtil.dp2px(16);
        content.addView(message, messageParams);

        AlertDialog loading = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setView(content)
                .setCancelable(false)
                .create();
        loading.setCanceledOnTouchOutside(false);
        loading.show();
        LightDialog.apply(loading);
        return loading;
    }

    private static void showSearchFallback(Activity activity, History history, int targetCid) {
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog)
                .setMessage(R.string.history_source_not_found)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.history_source_open_search, (ignored, which) -> openSearch(activity, history.getCid(), HistoryResumePayload.encode(history), targetCid, history.getVodName()))
                .create();
        dialog.show();
        LightDialog.apply(dialog);
    }

    private static void dismiss(Dialog dialog) {
        if (dialog == null) return;
        try {
            dialog.dismiss();
        } catch (RuntimeException ignored) {
        }
    }

    private static boolean isCurrent(Activity activity, int request) {
        return request == REQUESTS.get() && isAlive(activity);
    }

    private static boolean isAlive(Activity activity) {
        return !activity.isFinishing() && !activity.isDestroyed();
    }

    static boolean isSameResumeVersion(History expected, History current) {
        return expected != null && current != null
                && expected.getCid() == current.getCid()
                && expected.getTmdbId() == current.getTmdbId()
                && expected.getTmdbSeasonNumber() == current.getTmdbSeasonNumber()
                && expected.getTmdbEpisodeNumber() == current.getTmdbEpisodeNumber()
                && expected.getCreateTime() == current.getCreateTime()
                && expected.getPosition() == current.getPosition()
                && expected.getDuration() == current.getDuration()
                && Objects.equals(expected.getKey(), current.getKey())
                && Objects.equals(expected.getMediaType(), current.getMediaType())
                && Objects.equals(expected.getEpisodeUrl(), current.getEpisodeUrl())
                && Objects.equals(expected.getSourceBindingKey(), current.getSourceBindingKey());
    }
}
