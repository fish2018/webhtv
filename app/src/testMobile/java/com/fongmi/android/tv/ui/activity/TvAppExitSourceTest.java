package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TvAppExitSourceTest {

    @Test
    public void exitDialogExposesBackgroundAndFullExitActions() throws Exception {
        String layout = read("leanback", "res", "layout", "dialog_exit_confirm.xml");
        String dialog = read("leanback", "java", "com", "fongmi", "android", "tv", "ui", "dialog", "ExitConfirmDialog.java");

        assertTrue(layout.contains("@+id/backgroundPlay"));
        assertTrue(layout.contains("@string/exit_confirm_background"));
        assertTrue(dialog.contains("binding.backgroundPlay.setVisibility"));
        assertTrue(dialog.contains("listener.onBackgroundPlayback()"));
        assertTrue(dialog.contains("listener.onFullExit()"));
    }

    @Test
    public void homeRoutesExitThroughExplicitActions() throws Exception {
        String home = read("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "HomeActivity.java");

        assertTrue(home.contains("ExitConfirmDialog.create(PlaybackService.canContinueInBackground())"));
        assertTrue(home.contains("AppExitCoordinator.exit(this)"));
        assertTrue(home.contains("moveTaskToBack(true)"));
        assertFalse(home.contains("confirmExitHome"));
        assertFalse(home.contains("if (PlaybackService.isRunning()) moveTaskToBack(true)"));
    }

    @Test
    public void fullExitStopsOwnedServicesBeforeRemovingTheTask() throws Exception {
        String coordinator = read("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "AppExitCoordinator.java");

        assertTrue(coordinator.contains("AudioMiniPlayer.deactivateForFull(service)"));
        assertTrue(coordinator.contains("DLNARendererService.stop(activity)"));
        assertTrue(coordinator.contains("App.stopBackgroundServices()"));
        assertTrue(coordinator.contains("ManageService.stop(activity)"));
        assertTrue(coordinator.contains("PlaybackService.shutdown(activity)"));
        assertTrue(coordinator.contains("Server.get().shutdown()"));
        assertTrue(coordinator.contains("task.finishAndRemoveTask()"));
        assertTrue(coordinator.contains("activity.finishAffinity()"));
    }

    @Test
    public void delayedServerStopCannotStopARecreatedServer() throws Exception {
        String server = read("main", "java", "com", "fongmi", "android", "tv", "server", "Server.java");

        assertTrue(server.contains("Nano expected = nano;"));
        assertTrue(server.contains("if (manage || service != null || nano != expected) return;"));
    }

    @Test
    public void stoppedRemoteAgentRejectsQueuedPollsAndLateWebSocketCallbacks() throws Exception {
        String remote = read("main", "java", "com", "fongmi", "android", "tv", "remote", "RemoteAgent.java");

        assertTrue(remote.contains("private volatile boolean active;"));
        assertTrue(remote.contains("if (!active || busy) return;"));
        assertTrue(remote.contains("if (!session.onWebSocketOpen(webSocket)) return;"));
        assertTrue(remote.contains("session.onWebSocketClosed(webSocket);"));
        assertTrue(remote.contains("session.onWebSocketFailure(webSocket, t, response);"));
    }

    @Test
    public void fullExitStopsAppPulseAndPreservesARecreatedPlaybackService() throws Exception {
        String app = read("main", "java", "com", "fongmi", "android", "tv", "App.java");
        String fixer = read("main", "java", "com", "fongmi", "android", "tv", "utils", "DanmakuSearchListFocusFixer.java");
        String server = read("main", "java", "com", "fongmi", "android", "tv", "server", "Server.java");
        String playback = read("main", "java", "com", "fongmi", "android", "tv", "service", "PlaybackService.java");
        String syncer = read("main", "java", "com", "fongmi", "android", "tv", "playback", "PlaybackRemoteSyncer.java");

        assertTrue(app.contains("DanmakuSearchListFocusFixer.stop()"));
        assertTrue(app.contains("DanmakuSearchListFocusFixer.start()"));
        assertTrue(fixer.contains("HANDLER.removeCallbacks(PULSE)"));
        assertTrue(fixer.contains("if (!started) return;"));
        assertTrue(server.contains("if (service == expected) service = null;"));
        assertTrue(playback.contains("Server.get().clearService(this)"));
        assertTrue(syncer.contains("public static void syncDue(boolean startup) {\n        if (!started) return;"));
    }

    @Test
    public void backgroundResumeIsDebouncedAndWorkerStopIsVisible() throws Exception {
        String app = read("main", "java", "com", "fongmi", "android", "tv", "App.java");
        String syncer = read("main", "java", "com", "fongmi", "android", "tv", "playback", "PlaybackRemoteSyncer.java");

        assertTrue(app.contains("public static void resumeBackgroundServices() {\n        removeCallbacks(get().backgroundServicesStarter);"));
        assertTrue(syncer.contains("private static volatile boolean started;"));
    }
    @Test
    public void deferredStartupWorkIsCancelledOnFullExit() throws Exception {
        String home = read("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "HomeActivity.java");
        String app = read("main", "java", "com", "fongmi", "android", "tv", "App.java");

        assertTrue(home.contains("App.removeCallbacks(mDelayedInitConfig, mDelayedPermissionRequest, mDelayedDlnaStart)"));
        assertTrue(home.contains("App.resumeBackgroundServices()"));
        assertTrue(app.contains("removeCallbacks(get().backgroundServicesStarter)"));
        assertTrue(app.contains("PlaybackRemoteSyncer.stop()"));
        assertTrue(app.contains("RemoteAgent.get().stop()"));
        assertTrue(app.contains("NsdDeviceDiscovery.unregister()"));
    }

    private static String read(String... parts) throws Exception {
        Path path = Path.of("src");
        for (String part : parts) path = path.resolve(part);
        if (!Files.exists(path)) {
            path = Path.of("app", "src");
            for (String part : parts) path = path.resolve(part);
        }
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
