package com.fongmi.android.tv.player.exo.ass;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.os.ParcelFileDescriptor;
import android.view.View;
import androidx.test.platform.app.InstrumentationRegistry;
import junit.framework.TestCase;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.io.FileInputStream;

/** Real Exo engine, existing source/renderers factory, physical SurfaceView and async native worker. */
public class AssPlaybackTest extends TestCase {
    private Instrumentation instrumentation;
    private AssPrototypeActivity activity;

    private void main(Runnable action) { instrumentation.runOnMainSync(action); }

    private void await(Callable<Boolean> condition, String description) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 12000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.elapsedRealtime() < deadline) {
            main(() -> {
                try { ready.set(condition.call()); }
                catch (Exception error) { throw new IllegalStateException(error); }
            });
            if (ready.get()) return;
            SystemClock.sleep(40);
        }
        main(() -> android.util.Log.e("ExoAssTest", "timeout " + description + " " + activity.metrics()));
        fail(description);
    }

    @Override protected void setUp() throws Exception {
        super.setUp();
        instrumentation = InstrumentationRegistry.getInstrumentation();
    }

    private void start(boolean enabled, boolean noSubtitle) throws Exception {
        start(enabled, noSubtitle, false);
    }

    private void start(boolean enabled, boolean noSubtitle, boolean embedded) throws Exception {
        // Shell launch is the same path as the device harness, avoiding the
        // runner's idle-queue wait while an animated player is being created.
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                AssPrototypeActivity.class.getName(), null, false);
        try (ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand(
                "am start -W -f 0x10008000 -n com.fongmi.android.tv/.player.exo.ass.AssPrototypeActivity"
                        + " --ez enabled " + enabled + " --ez no_subtitle " + noSubtitle
                        + " --ez embedded " + embedded);
             FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
            activity = (AssPrototypeActivity) monitor.waitForActivityWithTimeout(10000);
            assertNotNull("Prototype Activity should launch", activity);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Override protected void tearDown() throws Exception {
        if (activity != null) main(activity::finish);
        instrumentation.waitForIdleSync();
        super.tearDown();
    }

    public void testPauseDelaySurfaceFallbackSeekTracksAndRelease() throws Exception {
        exerciseLifecycle(false);
    }

    public void testContainerPacketsPauseSeekLateFontsTracksAndRelease() throws Exception {
        exerciseLifecycle(true);
    }

    private void exerciseLifecycle(boolean embedded) throws Exception {
        start(true, false, embedded);
        await(() -> activity.session != null && activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE,
                "ASS should become visible after a valid native submission");
        await(() -> activity.player.getCurrentPosition() >= 1200, "position after positive delay boundary");
        main(() -> {
            assertEquals(View.INVISIBLE, activity.view.getSubtitleView().getVisibility());
            assertEquals("Current MKV's font must reach the selected ASS", 1,
                    activity.session.diagnostics().fontCount());
            if (embedded) {
                assertEquals(0, activity.session.diagnostics().scripts());
                assertTrue(activity.session.diagnostics().packetCount() > 0);
                assertTrue(activity.player.getCurrentMediaItem().localConfiguration.subtitleConfigurations.isEmpty());
            }
            assertTrue(activity.player.getCurrentCues().cues.size() > 0);
            activity.player.pause();
        });
        await(() -> !activity.player.isPlaying(), "pause");
        SystemClock.sleep(200);
        long frozen = activity.session.diagnostics().timeMs();
        SystemClock.sleep(200);
        assertEquals(frozen, activity.session.diagnostics().timeMs());
        main(() -> activity.player.setTextOffsetMs(500));
        await(() -> Math.abs(activity.session.diagnostics().timeMs() - (frozen - 500)) <= 40, "paused positive delay");
        if (embedded) {
            java.lang.reflect.Field field = ExoAssSession.class.getDeclaredField("fonts");
            field.setAccessible(true);
            AssFontSet fonts = (AssFontSet) field.get(activity.session);
            byte[] font;
            try (java.io.InputStream input = instrumentation.getContext().getAssets().open("exo-ass/official/Arimo-Regular.ttf")) {
                font = input.readAllBytes();
            }
            long frames = activity.session.diagnostics().frames();
            int packets = activity.session.diagnostics().packetCount();
            fonts.add("late-Arimo-Regular.ttf", font);
            await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE
                    && activity.session.diagnostics().fontCount() == 2
                    && activity.session.diagnostics().frames() > frames, "late font rebuild replays packets while paused");
            assertEquals(packets, activity.session.diagnostics().packetCount());
        }
        long epoch = activity.session.diagnostics().surfaceEpoch();
        main(() -> activity.command("reattach", null));
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE
                && activity.session.diagnostics().surfaceEpoch() > epoch, "paused Surface recreation");
        main(() -> activity.session.injectFailure("test"));
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.FALLBACK
                && activity.view.getSubtitleView().getVisibility() == View.VISIBLE, "recoverable fallback");
        main(() -> {
            assertTrue("Current compatible Cue must survive fallback", !activity.player.getCurrentCues().cues.isEmpty());
            activity.session.setEnabled(false);
            activity.session.setEnabled(true);
            activity.player.play();
        });
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE, "runtime re-enable");
        long scripts = activity.session.diagnostics().scripts();
        int packets = activity.session.diagnostics().packetCount();
        for (int i = 0; i < 4; i++) {
            long previous = activity.session.diagnostics().generation();
            final long position = i % 2 == 0 ? 6000 : 2000;
            main(() -> activity.player.seekTo(position));
            await(() -> activity.session.diagnostics().generation() > previous
                    && activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE, "seek redraw");
        }
        assertEquals("Seek reuses the complete script", scripts, activity.session.diagnostics().scripts());
        if (embedded) assertEquals("Duplicate preroll must not accumulate events", packets,
                activity.session.diagnostics().packetCount());
        main(() -> activity.command("subtitle_off", null));
        await(() -> !activity.session.diagnostics().nativeAlive()
                && activity.view.getSubtitleView().getVisibility() == View.VISIBLE, "track off releases native state");
        main(() -> activity.command("subtitle_on", null));
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE, "track reselect");
        main(() -> activity.command("texture", null));
        await(() -> activity.view.getSubtitleView().getVisibility() == View.VISIBLE, "TextureView remains compatible");
        main(() -> activity.command("surface", null));
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE, "SurfaceView resumes effect mode");
        ExoAssSession previous = activity.session;
        main(() -> activity.command("rebuild", null));
        await(() -> previous.diagnostics().releaseComplete()
                && !previous.diagnostics().workerAlive()
                && activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE, "engine rebuild releases old native owner");
        ExoAssSession last = activity.session;
        main(activity::finish);
        await(() -> last.diagnostics().releaseComplete() && !last.diagnostics().nativeAlive()
                && !last.diagnostics().workerAlive(), "final native and worker release");
    }

    public void testDisabledPrototypeDoesNotCreateNativeOrOverlay() throws Exception {
        start(false, false);
        await(() -> activity.player.isPlaying() && activity.player.getCurrentPosition() > 800, "compatible playback");
        main(() -> {
            assertFalse(activity.session.diagnostics().nativeAlive());
            assertEquals(0, activity.session.diagnostics().frames());
            assertEquals(View.VISIBLE, activity.view.getSubtitleView().getVisibility());
            assertFalse(activity.player.getCurrentCues().cues.isEmpty());
        });
    }

    public void testNoSubtitleDoesNotCreateNativeOrWorkerFrames() throws Exception {
        start(true, true);
        await(() -> activity.player.isPlaying() && activity.player.getCurrentPosition() > 800, "playback without subtitles");
        assertFalse(activity.session.diagnostics().nativeAlive());
        assertEquals(0, activity.session.diagnostics().frames());
    }
}
