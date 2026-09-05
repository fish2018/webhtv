package com.fongmi.android.tv.ui.activity;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LiveActivityLayoutTest {

    @Test
    public void explicitLivePiPPreparesVideoBeforeEnteringSystemPiP() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        int onPip = source.indexOf("private void onPiP()");
        int onPipEnd = source.indexOf("private void prepareLivePiPView()", onPip);
        String onPipBody = onPip >= 0 && onPipEnd > onPip ? source.substring(onPip, onPipEnd) : "";
        assertTrue(sourcePath + " is missing onPiP", onPip >= 0);
        assertTrue("explicit live PiP must prepare the entry path before scheduling system PiP",
                onPipBody.contains("preparePiP(\"panel\")")
                        && onPipBody.contains("prepareLivePiPView();")
                        && onPipBody.contains("scheduleLivePiPEntry();"));
        assertTrue("explicit live PiP preparation must run before the deferred entry is scheduled",
                onPipBody.indexOf("preparePiP(\"panel\")")
                        < onPipBody.indexOf("prepareLivePiPView();")
                        && onPipBody.indexOf("prepareLivePiPView();")
                        < onPipBody.indexOf("pipEntryPending = true;")
                        && onPipBody.indexOf("pipEntryPending = true;")
                        < onPipBody.indexOf("scheduleLivePiPEntry();"));
        assertFalse("explicit live PiP must not enter before the video layout is prepared",
                onPipBody.contains("mPiP.enter("));

        int prepare = source.indexOf("private void prepareLivePiPView()");
        int prepareEnd = source.indexOf("private void scheduleLivePiPEntry()", prepare);
        String prepareBody = prepare >= 0 && prepareEnd > prepare ? source.substring(prepare, prepareEnd) : "";
        assertTrue("live PiP preparation must expand the video container before entry",
                prepareBody.contains("setVideoView(true);")
                        && prepareBody.contains("mBinding.video.requestLayout();"));
        assertTrue("live PiP preparation must hide the embedded live chrome before entry",
                prepareBody.contains("mBinding.recycler.setVisibility(View.GONE);")
                        && prepareBody.contains("mBinding.navigation.setVisibility(View.GONE);"));

        int schedule = source.indexOf("private void scheduleLivePiPEntry()");
        int enter = source.indexOf("private void enterPreparedLivePiP()", schedule);
        String scheduleBody = schedule >= 0 && enter > schedule ? source.substring(schedule, enter) : "";
        assertTrue("live PiP entry must wait for a completed layout pass",
                scheduleBody.contains("OneShotPreDrawListener.add"));

        int enterEnd = source.indexOf("private void restoreAfterFailedLivePiP()", enter);
        String enterBody = enter >= 0 && enterEnd > enter ? source.substring(enter, enterEnd) : "";
        assertTrue("executing the deferred entry must release its completed listener handle",
                enterBody.contains("pipEntryListener = null;")
                        && enterBody.indexOf("pipEntryListener = null;")
                        < enterBody.indexOf("pipEntryPending = false;"));
        assertTrue("live PiP must update the source view before entering",
                enterBody.contains("mPiP.update(this, mBinding.video)")
                        && enterBody.indexOf("mPiP.update(this, mBinding.video)")
                        < enterBody.indexOf("mPiP.enter("));
        assertTrue("failed live PiP entry must restore the pre-entry layout",
                enterBody.contains("restoreAfterFailedLivePiP();"));
    }

    @Test
    public void livePipAudioActionKeepsBackgroundPlayback() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        String panelBody = section(source, "public void onLiveBackgroundPanel()", "public void onLiveListStylePanel(boolean classic)");
        String navigationBody = section(source, "public void onAudio()", "};");
        String switchBody = section(source, "private void switchToAudioBackground()", "private void setVideoView(");
        String prepareBody = section(source, "private boolean preparePiP(String reason)", "private boolean requestPiP(String reason)");
        String pipModeBody = section(source, "public void onPictureInPictureModeChanged(", "private void setVideoView(");
        String pipExitBody = section(source, "private void finishIfPipClosed()", "private void dismissLiveControlDialog()");
        String startBody = section(source, "protected void onStart()", "protected void onStop()");

        assertTrue("both live background entry points must share the guarded audio transition",
                panelBody.contains("switchToAudioBackground();")
                        && navigationBody.contains("switchToAudioBackground();"));
        int captureAudioMode = switchBody.indexOf("boolean audioOnly = isAudioOnly();");
        int markIntentionalExit = switchBody.indexOf("mKeepPlaybackAfterPipExit = isInPictureInPictureMode();");
        int setAudioMode = switchBody.indexOf("setAudioOnly(true);");
        int syncPipMode = switchBody.indexOf("syncPiPForPlaybackMode();");
        int moveToBackground = switchBody.indexOf("moveTaskToBack(true)");
        assertTrue("live audio background must preserve, mark, and synchronize state before moving the task",
                captureAudioMode >= 0
                        && markIntentionalExit > captureAudioMode
                        && setAudioMode > markIntentionalExit
                        && syncPipMode > setAudioMode
                        && moveToBackground > syncPipMode);
        assertTrue("a failed live background transition must restore audio and PiP state",
                switchBody.contains("mKeepPlaybackAfterPipExit = false;")
                        && switchBody.contains("setAudioOnly(audioOnly);")
                        && switchBody.lastIndexOf("syncPiPForPlaybackMode();") > moveToBackground);

        int pipEntered = pipModeBody.indexOf("if (isInPictureInPictureMode)");
        int resetStaleIntent = pipModeBody.indexOf("mKeepPlaybackAfterPipExit = false;", pipEntered);
        assertTrue("a new live PiP session must clear stale keep-playback state",
                pipEntered >= 0 && resetStaleIntent > pipEntered);
        assertTrue("live PiP exit detection must consume the intentional background marker",
                pipExitBody.contains("boolean keepPlayback = mKeepPlaybackAfterPipExit;")
                        && pipExitBody.contains("mKeepPlaybackAfterPipExit = false;")
                        && pipExitBody.contains("PipExitDecision.shouldFinishAfterPipExit(atLeastStarted, isFinishing(), isDestroyed(), keepPlayback)"));
        int resetAudioState = startBody.indexOf("setAudioOnly(false);");
        int resetPipState = startBody.indexOf("mPiP.resetAudioMode();");
        assertTrue("returning to live video must reset PiP mode without applying premature system parameters",
                resetAudioState >= 0
                        && resetPipState > resetAudioState
                        && !startBody.contains("syncPiPForPlaybackMode();"));
        int validateVideoTrack = prepareBody.indexOf("service() == null || !player().haveTrack(C.TRACK_TYPE_VIDEO)");
        int applyPipMode = prepareBody.indexOf("syncPiPForPlaybackMode()");
        assertTrue("live PiP parameters must only be applied after video eligibility is confirmed",
                validateVideoTrack >= 0 && applyPipMode > validateVideoTrack);
    }

    @Test
    public void pendingLivePiPEntryIsCancelledWhenTheActivityStops() throws Exception {
        Path sourcePath = findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("the pending pre-draw listener must be retained so it can be cancelled",
                source.contains("private OneShotPreDrawListener pipEntryListener;"));

        int schedule = source.indexOf("private void scheduleLivePiPEntry()");
        int enter = source.indexOf("private void enterPreparedLivePiP()", schedule);
        String scheduleBody = schedule >= 0 && enter > schedule ? source.substring(schedule, enter) : "";
        assertTrue("live PiP scheduling must retain the one-shot listener",
                scheduleBody.contains("pipEntryListener = OneShotPreDrawListener.add"));

        int cancel = source.indexOf("private void cancelPendingLivePiP(boolean restoreUi)");
        int cancelEnd = source.indexOf("private void enterFullscreenLive()", cancel);
        String cancelBody = cancel >= 0 && cancelEnd > cancel ? source.substring(cancel, cancelEnd) : "";
        assertTrue("live PiP cancellation must remove the listener and clear pending state",
                cancelBody.contains("pipEntryListener.removeListener();")
                        && cancelBody.contains("pipEntryListener = null;")
                        && cancelBody.contains("pipEntryPending = false;"));
        assertTrue("cancelled live PiP must restore the prepared layout when requested",
                cancelBody.contains("if (restoreUi && !isInPictureInPictureMode()) restoreAfterFailedLivePiP();"));

        int onStop = source.indexOf("protected void onStop()");
        int onStopEnd = source.indexOf("protected void onBackInvoked()", onStop);
        String onStopBody = onStop >= 0 && onStopEnd > onStop ? source.substring(onStop, onStopEnd) : "";
        assertTrue("stopping the activity must cancel and restore a pending live PiP entry",
                onStopBody.contains("cancelPendingLivePiP(true);"));

        int onDestroy = source.indexOf("protected void onDestroy()");
        String onDestroyBody = onDestroy >= 0 ? source.substring(onDestroy) : "";
        assertTrue("destroying the activity must remove any pending live PiP listener without restoring UI",
                onDestroyBody.contains("cancelPendingLivePiP(false);"));

        int onPipChanged = source.indexOf("public void onPictureInPictureModeChanged(");
        int onPipChangedEnd = source.indexOf("private void setVideoView(", onPipChanged);
        String onPipChangedBody = onPipChanged >= 0 && onPipChangedEnd > onPipChanged
                ? source.substring(onPipChanged, onPipChangedEnd) : "";
        int enteredPiP = onPipChangedBody.indexOf("if (isInPictureInPictureMode)");
        int cancelPending = onPipChangedBody.indexOf("cancelPendingLivePiP(false);", enteredPiP);
        assertTrue("a successful system PiP transition must cancel any still-pending manual entry",
                enteredPiP >= 0 && cancelPending > enteredPiP);
    }

    @Test
    public void playbackEndStaysOnCurrentChannelForMobileAndLeanback() throws Exception {
        assertPlaybackEndStaysOnCurrentChannel(findMobileJavaPath());
        assertPlaybackEndStaysOnCurrentChannel(findLeanbackJavaPath());
    }

    private static void assertPlaybackEndStaysOnCurrentChannel(Path javaRoot) throws Exception {
        Path sourcePath = javaRoot.resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "activity", "LiveActivity.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        String checkEndedBody = section(source, "private void checkEnded()", "private void setTrackVisible()");

        assertFalse(sourcePath + " is missing checkEnded", checkEndedBody.isEmpty());
        assertTrue("live playback end must continue within the current channel", source.contains("mEndRetry = this::checkNext;"));
        assertFalse("live playback end must not advance to the next channel", checkEndedBody.contains("nextChannel();"));
        assertFalse("channel navigation must not depend on player live classification", checkEndedBody.contains("player().isLive()"));
        assertTrue("natural playback end must use a bounded retry delay",
                source.contains("private static final long PLAYBACK_END_RETRY_DELAY = 500;")
                        && source.contains("private Runnable mEndRetry;"));
        assertTrue("natural playback end must debounce before reloading the current channel",
                checkEndedBody.contains("App.removeCallbacks(mEndRetry);")
                        && checkEndedBody.contains("App.post(mEndRetry, PLAYBACK_END_RETRY_DELAY);"));

        String checkNextBody = section(source, "private void checkNext()", "\n    private void");
        assertTrue("delayed end retry must tolerate an activity without a selected channel",
                checkNextBody.contains("if (mChannel == null) return;"));

        String fetchBody = section(source, "private void fetch()", "private void start(Result result)");
        assertTrue("a new fetch must cancel a stale delayed end retry",
                fetchBody.contains("App.removeCallbacks(mEndRetry);"));

        int onDestroy = source.indexOf("protected void onDestroy()");
        assertTrue("destroying the activity must cancel a delayed end retry",
                onDestroy >= 0 && source.indexOf("App.removeCallbacks(mEndRetry);", onDestroy) >= onDestroy);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = start < 0 ? -1 : source.indexOf(endMarker, start + startMarker.length());
        return start >= 0 && end > start ? source.substring(start, end) : "";
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }
}
