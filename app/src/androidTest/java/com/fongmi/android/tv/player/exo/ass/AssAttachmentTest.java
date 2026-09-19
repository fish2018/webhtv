package com.fongmi.android.tv.player.exo.ass;

import androidx.media3.common.C;
import androidx.media3.extractor.DefaultExtractorInput;
import androidx.media3.extractor.DiscardingTrackOutput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.test.platform.app.InstrumentationRegistry;
import junit.framework.TestCase;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

/** Real font bytes and production JNI/provider selection; no guessed substitute typeface. */
public class AssAttachmentTest extends TestCase {
    private byte[] font() throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext()
                .getAssets().open("exo-ass/official/Aileron-Regular.otf")) { return input.readAllBytes(); }
    }

    public void testUnorderedAttachmentDeduplicationAndClosedMedia() throws Exception {
        AtomicInteger changes = new AtomicInteger();
        AssFontSet set = new AssFontSet(ignored -> changes.incrementAndGet());
        AssFontMatroskaExtractor extractor = new AssFontMatroskaExtractor(
                androidx.media3.extractor.mkv.MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA, false, set);
        extractor.init(new ExtractorOutput() {
            @Override public TrackOutput track(int id, int type) { return new DiscardingTrackOutput(); }
            @Override public void endTracks() { }
            @Override public void seekMap(SeekMap map) { }
        });
        byte[] data = font();
        for (int pass = 0; pass < 2; pass++) {
            extractor.startMasterElement(0x61A7, 0, data.length + 100);
            ByteArrayInputStream bytes = new ByteArrayInputStream(data);
            extractor.binaryElement(0x465C, data.length,
                    new DefaultExtractorInput(bytes::read, 0, data.length));
            extractor.stringElement(0x4660, "application/octet-stream");
            extractor.stringElement(0x466E, "renamed-container-attachment.otf");
            extractor.endMasterElement(0x61A7);
        }
        assertEquals(1, changes.get());
        assertEquals(1, set.snapshot().names().length);
        assertTrue(Arrays.equals(data, set.snapshot().data()[0]));
        assertFalse(set.canRead(AssFontSet.MAX_FONT_BYTES + 1));
        set.close();
        set.add("stale.otf", data);
        assertEquals(0, set.snapshot().names().length);
        assertEquals(1, changes.get());
    }

    private byte[] pixels(long handle) {
        String script = "[Script Info]\nScriptType: v4.00+\nPlayResX: 640\nPlayResY: 360\n"
                + "[V4+ Styles]\nFormat: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n"
                + "Style: Default,Aileron,38,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,1,0,2,10,10,20,1\n"
                + "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                + "Dialogue: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,Attachment faces ABC xyz\n";
        assertTrue(handle != 0);
        assertTrue(AssNative.load(handle, script.getBytes(StandardCharsets.UTF_8)));
        assertTrue(AssNative.testSurface(handle, 640, 360));
        assertTrue(AssNative.render(handle, 1000, 640, 360, 640, 360, 1,
                C.COLOR_SPACE_BT709, C.COLOR_RANGE_LIMITED, true, new long[6]) > 0);
        return AssNative.readPixels(handle);
    }

    public void testAttachedFaceWinsOverSystemFallback() throws Exception {
        AssNative.ensureLoaded();
        String[] names = {"unrelated-filename.otf"};
        byte[][] fonts = {font()};
        String config = AssFonts.prepare(InstrumentationRegistry.getInstrumentation().getTargetContext());
        long reference = 0, attached = 0, fallback = 0;
        try {
            reference = AssNative.createTestFonts(names, fonts);
            byte[] expected = pixels(reference);
            AssNative.destroy(reference); reference = 0;
            attached = AssNative.create(config, names, fonts);
            byte[] actual = pixels(attached);
            AssNative.destroy(attached); attached = 0;
            fallback = AssNative.create(config, new String[0], new byte[0][]);
            byte[] substitute = pixels(fallback);
            boolean visible = false;
            for (byte channel : substitute) if (channel != 0) { visible = true; break; }
            assertTrue("Unavailable script fonts still require a visible system fallback", visible);
            assertTrue("Internal family must select the attached face even with a different filename",
                    Arrays.equals(expected, actual));
            assertFalse("System fallback must not masquerade as the script's original font",
                    Arrays.equals(expected, substitute));
        } finally {
            if (reference != 0) AssNative.destroy(reference);
            if (attached != 0) AssNative.destroy(attached);
            if (fallback != 0) AssNative.destroy(fallback);
            new File(config).delete();
        }
    }
}
