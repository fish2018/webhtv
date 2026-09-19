package com.fongmi.android.tv.player.exo.subtitle;

import android.app.Instrumentation;
import android.graphics.Bitmap;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.text.Spanned;
import android.view.View;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.exoplayer.DecoderCounters;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.ui.SubtitleView;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.player.PlayerHelper;
import com.fongmi.android.tv.player.engine.PlaySpec;
import com.fongmi.android.tv.player.exo.ass.AssPrototypeActivity;
import com.fongmi.android.tv.player.exo.ass.ExoAssSession;

import junit.framework.TestCase;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

/** Existing real Exo engine/source factory, two selected streams and the physical ASS Surface. */
public class DualSubtitlePlaybackTest extends TestCase {
    private Instrumentation instrumentation;
    private AssPrototypeActivity activity;
    private ExoSubtitleSession subtitles;

    private void main(Runnable action) {
        instrumentation.runOnMainSync(action);
    }

    private void await(Callable<Boolean> condition, String message) throws Exception {
        long deadline = SystemClock.elapsedRealtime() + 15000;
        AtomicBoolean ready = new AtomicBoolean();
        while (SystemClock.elapsedRealtime() < deadline) {
            main(() -> {
                try {
                    assertNull("Playback error: " + activity.metrics(), activity.player.getPlayerError());
                    ready.set(condition.call());
                } catch (Exception e) { throw new IllegalStateException(e); }
            });
            if (ready.get()) return;
            SystemClock.sleep(30);
        }
        main(() -> android.util.Log.e("ExoDualSubtitleTest", message + " " + activity.metrics()));
        fail(message);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(AssPrototypeActivity.class.getName(), null, false);
        try (ParcelFileDescriptor fd = instrumentation.getUiAutomation().executeShellCommand(
                "am start -W -f 0x10008000 -n com.fongmi.android.tv/.player.exo.ass.AssPrototypeActivity --ez no_subtitle true");
             FileInputStream input = new FileInputStream(fd.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
            activity = (AssPrototypeActivity) monitor.waitForActivityWithTimeout(10000);
            assertNotNull(activity);
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Override
    protected void tearDown() throws Exception {
        if (activity != null) main(activity::finish);
        instrumentation.waitForIdleSync();
        super.tearDown();
    }

    public void testExternalAssAndSecondaryTextLifecycle() throws Exception {
        exercise(false);
    }

    public void testEmbeddedAssAndExternalSecondaryLifecycle() throws Exception {
        exercise(true);
    }

    private void exercise(boolean embedded) throws Exception {
        File directory = new File(instrumentation.getTargetContext().getCacheDir(), "dual-subtitle-fixture");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        File srt = new File(directory, "secondary.srt");
        Files.write(srt.toPath(), ("1\n00:00:00,000 --> 00:00:04,000\n<font color=\"red\">SECONDARY SRT ONE</font>\n\n"
                + "2\n00:00:04,000 --> 00:01:00,000\nSECONDARY SRT TWO\n").getBytes(StandardCharsets.UTF_8));
        File ass = new File(directory, "secondary.ass");
        Files.write(ass.toPath(), ("[Script Info]\nScriptType: v4.00+\nPlayResX: 960\nPlayResY: 540\n"
                + "[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
                + "Style: Default,Aileron,70,&H000000FF,&H0000FFFF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,1,2,10,10,10,1\n"
                + "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                + "Dialogue: 0,0:00:00.00,0:01:00.00,Default,,0,0,0,,{\\pos(480,510)\\frz20}SECONDARY ASS PLAIN\n").getBytes(StandardCharsets.UTF_8));
        PlaySpec spec = PlaySpec.from("E4-LIBASS-dual", "asset:///exo-ass/"
                + (embedded ? "sample-embedded.mkv" : "sample-fonts.mkv"), Collections.emptyMap(), MediaMetadata.EMPTY);
        spec.setSub(Sub.create("Secondary ASS", ass.toURI().toString(), "fr", MimeTypes.TEXT_SSA));
        spec.setSub(Sub.create("Secondary SRT", srt.toURI().toString(), "en", MimeTypes.APPLICATION_SUBRIP));
        if (!embedded) spec.setSub(Sub.create("Primary ASS", "asset:///exo-ass/animated.ass", "zh", MimeTypes.TEXT_SSA));
        main(() -> {
            subtitles = activity.engine.getSubtitleSession();
            subtitles.attach(activity.view);
            assertTrue(activity.engine.supportsSecondarySubtitle());
            assertNull("Disabled secondary must not allocate a view", secondaryView());
            activity.engine.start(spec, 1000, true);
        });
        await(() -> find("Secondary SRT") != null && findPrimary() != null, "subtitle tracks extracted");
        main(() -> activity.engine.setTrack(List.of(track(findPrimary()))));
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE
                && activity.player.getCurrentPosition() >= 1200, "primary ASS rendered");
        DecoderCounters[] videoCounters = new DecoderCounters[1];
        main(() -> {
            videoCounters[0] = activity.player.getVideoDecoderCounters();
            assertEquals(Renderer.STATE_DISABLED, secondaryRenderer().getState());
            activity.player.seekTo(1500);
            activity.engine.setSecondarySubtitleTrack(track(find("Secondary SRT")));
        });
        await(() -> secondaryText().equals("SECONDARY SRT ONE"), "secondary SRT rendered beside ASS");
        main(() -> {
            activity.player.pause();
            assertTrue(activity.engine.isPrimarySubtitleSelected(findPrimary()));
            assertTrue(activity.engine.isSecondarySubtitleSelected(find("Secondary SRT")));
            assertFalse(activity.engine.isPrimarySubtitleSelected(find("Secondary SRT")));
            assertEquals(View.INVISIBLE, activity.view.getSubtitleView().getVisibility());
            assertEquals(View.VISIBLE, secondaryView().getVisibility());
            assertSame(activity.view.getSubtitleView().getParent(), secondaryView().getParent());
            assertEquals(1, activity.session.diagnostics().fontCount());
            assertFalse(secondaryCues().get(0).text instanceof Spanned);
        });
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(activity.getExternalFilesDir(null),
                "exo-dual-" + (embedded ? "embedded" : "external") + ".png"))) {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, output);
        } finally { screenshot.recycle(); }

        main(() -> activity.player.setTextOffsetMs(5000));
        await(() -> secondaryCues().isEmpty(), "paused positive delay clears early secondary cue");
        main(() -> activity.player.setTextOffsetMs(0));
        await(() -> secondaryText().equals("SECONDARY SRT ONE"), "paused delay restoration redraws secondary");
        main(() -> { activity.player.seekTo(5500); activity.player.play(); });
        await(() -> secondaryText().equals("SECONDARY SRT TWO"), "forward seek subtitle time");
        main(() -> { activity.player.seekTo(1500); activity.player.play(); });
        await(() -> secondaryText().equals("SECONDARY SRT ONE"), "backward seek subtitle time");

        main(() -> {
            activity.player.pause();
            activity.engine.setTrack(List.of(Track.disabled(C.TRACK_TYPE_TEXT, "off")));
        });
        await(() -> !activity.session.diagnostics().nativeAlive(), "primary off releases only its ASS owner");
        main(() -> {
            assertEquals("SECONDARY SRT ONE", secondaryText());
            assertTrue(activity.engine.isSecondarySubtitleSelected(find("Secondary SRT")));
            activity.engine.setTrack(List.of(track(findPrimary())));
            activity.player.play();
        });
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE, "primary ASS restored");
        main(() -> activity.engine.setSecondarySubtitleTrack(Track.disabled(C.TRACK_TYPE_TEXT, "off")));
        await(() -> secondaryRenderer().getState() == Renderer.STATE_DISABLED && secondaryCues().isEmpty(), "secondary independently disabled");
        main(() -> {
            assertEquals(ExoAssSession.State.ACTIVE, activity.session.diagnostics().state());
            assertSame("Subtitle switching must not reinitialize video", videoCounters[0], activity.player.getVideoDecoderCounters());
            activity.engine.setSecondarySubtitleTrack(track(find("Secondary ASS")));
        });
        await(() -> secondaryText().equals("SECONDARY ASS PLAIN"), "secondary ASS follows MPV strip default");
        main(() -> {
            activity.player.pause();
            assertFalse(secondaryCues().get(0).text instanceof Spanned);
            assertEquals(Cue.ANCHOR_TYPE_START, secondaryCues().get(0).lineAnchor);
            assertTrue(secondaryCues().get(0).line < 0.1f);
            SubtitleView previous = secondaryView();
            subtitles.detach();
            assertNull(previous.getParent());
            subtitles.attach(activity.view);
            assertNotSame(previous, secondaryView());
            assertEquals("SECONDARY ASS PLAIN", secondaryText());
            activity.command("reattach", null);
        });
        await(() -> activity.session.diagnostics().state() == ExoAssSession.State.ACTIVE, "ASS and secondary survive host reattach");
        main(() -> {
            android.util.Log.i("ExoDualSubtitleTest", "passed embedded=" + embedded + " " + activity.metrics());
            activity.engine.start(PlaySpec.from("different-source", "asset:///exo-ass/sample-fonts.mkv",
                    Collections.emptyMap(), MediaMetadata.EMPTY));
            assertTrue(secondaryCues().isEmpty());
        });
        await(() -> activity.player.getCurrentTracks().getGroups().stream().noneMatch(group -> group.getType() == C.TRACK_TYPE_TEXT),
                "source change drops old subtitle selections");
        main(() -> {
            ExoSubtitleSession old = subtitles;
            SubtitleView oldView = secondaryView();
            activity.command("rebuild", null);
            subtitles = activity.engine.getSubtitleSession();
            assertNotSame(old, subtitles);
            assertNull(oldView.getParent());
            subtitles.attach(activity.view);
            assertNull("New player starts without a secondary overlay", secondaryView());
        });
    }

    private Format find(String label) {
        for (Tracks.Group group : activity.player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) if (label.equals(group.getTrackFormat(i).label)) return group.getTrackFormat(i);
        }
        return null;
    }

    private Format findPrimary() {
        for (Tracks.Group group : activity.player.getCurrentTracks().getGroups()) {
            if (group.getType() != C.TRACK_TYPE_TEXT) continue;
            for (int i = 0; i < group.length; i++) {
                Format format = group.getTrackFormat(i);
                if (MimeTypes.TEXT_SSA.equals(format.sampleMimeType)
                        && (format.label == null || !format.label.startsWith("Secondary"))) return format;
            }
        }
        return null;
    }

    private static Track track(Format format) {
        if (format == null) throw new AssertionError("Missing fixture track");
        Track track = new Track(C.TRACK_TYPE_TEXT, format.label, PlayerHelper.describeFormat(format)).playerId(format.id);
        track.setSelected(true);
        return track;
    }

    private Renderer secondaryRenderer() {
        return activity.player.getRenderer(activity.player.getRendererCount() - 1);
    }

    @SuppressWarnings("unchecked")
    private List<Cue> secondaryCues() {
        return (List<Cue>) field("cues");
    }

    private String secondaryText() {
        List<Cue> cues = secondaryCues();
        return cues.isEmpty() || cues.get(0).text == null ? "" : cues.get(0).text.toString();
    }

    private SubtitleView secondaryView() {
        return (SubtitleView) field("view");
    }

    private Object field(String name) {
        try {
            java.lang.reflect.Field field = ExoSubtitleSession.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(subtitles);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }
}
