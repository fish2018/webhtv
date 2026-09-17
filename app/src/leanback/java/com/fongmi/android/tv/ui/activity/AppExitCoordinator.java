package com.fongmi.android.tv.ui.activity;

import android.app.ActivityManager;
import android.content.Context;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.service.DLNARendererService;
import com.fongmi.android.tv.service.ManageService;
import com.fongmi.android.tv.service.PlaybackService;
import com.fongmi.android.tv.ui.audio.AudioMiniPlayer;
import com.github.catvod.crawler.SpiderDebug;

import java.util.List;

final class AppExitCoordinator {

    private AppExitCoordinator() {
    }

    static void exit(HomeActivity activity) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        SpiderDebug.log("app-exit", "full exit requested playback=%s", PlaybackService.isRunning());
        PlaybackService service = Server.get().getService();
        AudioMiniPlayer.deactivateForFull(service);
        DLNARendererService.stop(activity);
        App.stopBackgroundServices();
        ManageService.stop(activity);
        PlaybackService.shutdown(activity);
        Server.get().shutdown();
        removeAppTasks(activity);
    }

    private static void removeAppTasks(HomeActivity activity) {
        try {
            ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.AppTask> tasks = manager == null ? List.of() : manager.getAppTasks();
            if (!tasks.isEmpty()) {
                for (ActivityManager.AppTask task : tasks) task.finishAndRemoveTask();
                return;
            }
        } catch (Throwable e) {
            SpiderDebug.log("app-exit", e);
        }
        activity.finishAffinity();
        activity.finishAndRemoveTask();
    }
}
