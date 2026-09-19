package com.fongmi.android.tv.player.exo.ass;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import androidx.media3.common.C;
import androidx.test.platform.app.InstrumentationRegistry;
import junit.framework.TestCase;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Fixed upstream PNGs + fixed fonts, with system font lookup explicitly disabled. */
public class AssNativeGoldenTest extends TestCase {
    private byte[] asset(String name) throws Exception {
        try (InputStream input = InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("exo-ass/official/" + name);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192]; int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private long create() throws Exception {
        AssNative.ensureLoaded();
        long handle = AssNative.createTestFonts(new String[]{"Aileron-Regular.otf", "Arimo-Bold.ttf", "Arimo-Regular.ttf"},
                new byte[][]{asset("Aileron-Regular.otf"), asset("Arimo-Bold.ttf"), asset("Arimo-Regular.ttf")});
        assertTrue("Native initialization with fixed fonts", handle != 0);
        return handle;
    }

    private void compare(long handle, String base, int time, int width, int height) throws Exception {
        long[] stats = new long[6];
        assertTrue("A changed frame must be submitted", AssNative.render(handle, time, width, height, width, height,
                1, 2, 2, true, stats) > 0);
        byte[] pixels = AssNative.readPixels(handle);
        assertNotNull(pixels);
        String name = base + "-" + time;
        File directory = new File(InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir(), "exo-ass-golden");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".rgba"))) { output.write(pixels); }
        byte[] png = asset(name + ".png");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPremultiplied = false;
        Bitmap reference = BitmapFactory.decodeByteArray(png, 0, png.length, options);
        assertEquals(width, reference.getWidth()); assertEquals(height, reference.getHeight());
        int[] expected = new int[width * height];
        reference.getPixels(expected, 0, width, 0, 0, width, height);
        reference.recycle();
        long error = 0, union = 0, bad = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            int offset = ((height - 1 - y) * width + x) * 4;
            int argb = expected[y * width + x];
            int a = argb >>> 24;
            int actualA = pixels[offset + 3] & 255;
            if (a == 0 && actualA == 0) continue;
            union++;
            int[] referencePixel = { ((argb >>> 16 & 255) * a + 127) / 255,
                    ((argb >>> 8 & 255) * a + 127) / 255, ((argb & 255) * a + 127) / 255, a };
            int maximum = 0;
            for (int channel = 0; channel < 4; channel++) {
                int difference = Math.abs(referencePixel[channel] - (pixels[offset + channel] & 255));
                error += difference; maximum = Math.max(maximum, difference);
            }
            if (maximum > 16) bad++;
        }
        assertTrue("Reference must contain a visible subtitle", union > 1000);
        double mean = error / (union * 4.0);
        double badRatio = bad / (double) union;
        String report = name + " foregroundMae=" + mean + " badPixelRatio=" + badRatio
                + " renderUs=" + stats[0] + " uploadUs=" + stats[1];
        android.util.Log.i("ExoAssGolden", report);
        // Frozen before inspecting candidate pixels. Transparent background does not dilute error.
        assertTrue(report, mean <= 1.5 && badRatio <= .02);
    }

    public void testOfficialBlurTransformFrames() throws Exception {
        long handle = create();
        try {
            assertTrue(AssNative.load(handle, AssInput.normalize(asset("blur+t.ass"))));
            assertTrue(AssNative.testSurface(handle, 800, 600));
            for (int time : new int[]{1000, 1500, 1900}) compare(handle, "blur+t", time, 800, 600);
        } finally { AssNative.destroy(handle); }
    }

    public void testHdrSdrRgbColorsAndVideoColorTransitions() throws Exception {
        // A solid ASS drawing gives an independent color/alpha reference without font variance.
        String script = "[Script Info]\nScriptType: v4.00+\nPlayResX: 64\nPlayResY: 64\n"
                + "YCbCr Matrix: TV.601\n[V4+ Styles]\n"
                + "Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, "
                + "Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, "
                + "Alignment, MarginL, MarginR, MarginV, Encoding\n"
                + "Style: Default,Arimo,20,&H00C08040,&H00C08040,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,0,0,7,0,0,0,1\n"
                + "[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n"
                + "Dialogue: 0,0:00:00.00,0:00:10.00,Default,,0,0,0,,"
                + "{\\an7\\pos(0,0)\\bord0\\shad0\\1a&H80&\\p1}m 0 0 l 64 0 64 64 0 64\n";
        long handle = create();
        long[] stats = new long[6];
        try {
            assertTrue(AssNative.load(handle, AssInput.normalize(script.getBytes(StandardCharsets.UTF_8))));
            assertTrue(AssNative.testSurface(handle, 64, 64));
            assertTrue(AssNative.render(handle, 1000, 64, 64, 64, 64, 1,
                    AssNative.COLOR_SPACE_SDR_RGB, C.COLOR_RANGE_FULL, false, stats) > 0);
            byte[] hdr = AssNative.readPixels(handle);
            assertSdrRgbPixel(hdr);
            assertTrue("Color-mode changes redraw even at a paused timestamp",
                    AssNative.render(handle, 1000, 64, 64, 64, 64, 1,
                            C.COLOR_SPACE_BT709, C.COLOR_RANGE_LIMITED, false, stats) > 0);
            byte[] sdr = AssNative.readPixels(handle);
            int pixel = (32 * 64 + 32) * 4;
            assertTrue("SDR retains the historical TV.601 to TV.709 conversion",
                    Math.abs((hdr[pixel] & 255) - (sdr[pixel] & 255)) >= 2);
            assertEquals(hdr[pixel + 3], sdr[pixel + 3]);
            assertTrue(AssNative.render(handle, 1000, 64, 64, 64, 64, 1,
                    AssNative.COLOR_SPACE_SDR_RGB, C.COLOR_RANGE_FULL, false, stats) > 0);
            assertSdrRgbPixel(AssNative.readPixels(handle));
        } finally { AssNative.destroy(handle); }
    }

    private void assertSdrRgbPixel(byte[] pixels) {
        assertNotNull(pixels);
        int pixel = (32 * 64 + 32) * 4;
        // ASS &H80C08040 is RGB(64,128,192), opacity 127/255, stored premultiplied.
        int[] expected = {32, 64, 96, 127};
        for (int channel = 0; channel < 4; channel++)
            assertTrue("SDR RGB channel " + channel + " actual=" + (pixels[pixel + channel] & 255),
                    Math.abs(expected[channel] - (pixels[pixel + channel] & 255)) <= 1);
    }

    public void testOfficialKaraokeFrames() throws Exception {
        long handle = create();
        try {
            assertTrue(AssNative.load(handle, AssInput.normalize(asset("357-k-and-kf-desynced.ass"))));
            assertTrue(AssNative.testSurface(handle, 1920, 1080));
            for (int time : new int[]{6798, 7170, 8170}) compare(handle, "357-k-and-kf-desynced", time, 1920, 1080);
        } finally { AssNative.destroy(handle); }
    }

    private record PacketScript(byte[] header, List<AssPacketInput.Packet> packets) { }

    private static long scriptTimeMs(String value) {
        String[] parts = value.trim().split("[:.]");
        return Long.parseLong(parts[0]) * 3_600_000 + Long.parseLong(parts[1]) * 60_000
                + Long.parseLong(parts[2]) * 1000 + Long.parseLong(parts[3]) * 10;
    }

    private PacketScript packetize(String file) throws Exception {
        String script = new String(AssInput.normalize(asset(file)), StandardCharsets.UTF_8);
        StringBuilder header = new StringBuilder();
        AssPacketInput input = new AssPacketInput();
        int order = 0;
        for (String line : script.split("\n")) {
            if (!line.startsWith("Dialogue:")) { header.append(line).append('\n'); continue; }
            String[] fields = line.substring("Dialogue:".length()).trim().split(",", 10);
            long startMs = scriptTimeMs(fields[1]), duration = scriptTimeMs(fields[2]) - startMs;
            String durationText = String.format(Locale.ROOT, "%d:%02d:%02d:%02d",
                    duration / 3_600_000, duration / 60_000 % 60, duration / 1000 % 60, duration / 10 % 100);
            String sample = "Dialogue: 0:00:00:00," + durationText + "," + order++ + "," + fields[0]
                    + "," + String.join(",", Arrays.copyOfRange(fields, 3, 10));
            assertTrue(input.add(ByteBuffer.wrap(sample.getBytes(StandardCharsets.UTF_8)),
                    1_000_000_000_000L + startMs * 1000, 1_000_000_000_000L));
        }
        return new PacketScript(AssInput.normalizeHeader(header.toString().getBytes(StandardCharsets.UTF_8)), input.after(0));
    }

    private void loadPackets(long handle, PacketScript script) {
        assertTrue(AssNative.loadHeader(handle, script.header()));
        for (AssPacketInput.Packet packet : script.packets())
            assertTrue(AssNative.chunk(handle, packet.data(), packet.startMs(), packet.durationMs()));
    }

    public void testPacketizedOfficialFramesDuplicatePrerollAndReplay() throws Exception {
        long handle = create();
        try {
            for (String base : new String[]{"blur+t", "357-k-and-kf-desynced"}) {
                PacketScript script = packetize(base + ".ass");
                loadPackets(handle, script);
                AssPacketInput.Packet first = script.packets().get(0);
                assertTrue("Duplicate ReadOrder follows libass semantics",
                        AssNative.chunk(handle, first.data(), first.startMs(), first.durationMs()));
                boolean blur = base.equals("blur+t");
                int width = blur ? 800 : 1920, height = blur ? 600 : 1080;
                assertTrue(AssNative.testSurface(handle, width, height));
                int[] times = blur ? new int[]{1900, 1000, 1500} : new int[]{8170, 6798, 7170};
                for (int time : times) compare(handle, base, time, width, height);
                // The same retained packets must reconstruct a fresh track after font changes.
                loadPackets(handle, script);
                compare(handle, base, times[1], width, height);
            }
        } finally { AssNative.destroy(handle); }
    }

    public void testPacketNativeBoundsAndScriptModeIsolation() throws Exception {
        long handle = create();
        try {
            PacketScript script = packetize("blur+t.ass");
            AssPacketInput.Packet first = script.packets().get(0);
            assertFalse(AssNative.chunk(handle, first.data(), first.startMs(), first.durationMs()));
            assertTrue(AssNative.load(handle, AssInput.normalize(asset("blur+t.ass"))));
            assertFalse("Chunk input must never mutate a full-script track",
                    AssNative.chunk(handle, first.data(), first.startMs(), first.durationMs()));
            assertTrue(AssNative.loadHeader(handle, script.header()));
            assertFalse(AssNative.chunk(handle, new byte[AssInput.MAX_INPUT_BYTES + 1], 0, 1));
            assertFalse(AssNative.chunk(handle, first.data(), Long.MAX_VALUE, 1));
            assertFalse(AssNative.chunk(handle, first.data(), 0, -1));
            assertFalse(AssNative.loadHeader(handle, new byte[AssInput.MAX_UTF8_BYTES + 1]));
            assertFalse(AssNative.loadHeader(handle, AssInput.normalize(asset("blur+t.ass"))));
        } finally { AssNative.destroy(handle); }
    }

    public void testUnchangedContextRecreationAndEmptyFrameAreDistinct() throws Exception {
        long handle = create();
        long[] stats = new long[6];
        try {
            assertTrue(AssNative.load(handle, AssInput.normalize(asset("blur+t.ass"))));
            assertFalse(AssNative.testSurface(handle, Integer.MAX_VALUE, 100));
            assertTrue(AssNative.testSurface(handle, 800, 600));
            assertTrue(AssNative.render(handle, 1000, 800, 600, 800, 600, 1, 2, 2, false, stats) > 0);
            assertEquals(0, AssNative.render(handle, 1000, 800, 600, 800, 600, 1, 2, 2, false, stats));
            assertTrue(AssNative.setSurface(handle, null));
            assertTrue(AssNative.testSurface(handle, 800, 600));
            assertTrue(AssNative.render(handle, 1000, 800, 600, 800, 600, 1, 2, 2, false, stats) > 0);
            assertEquals(2, AssNative.render(handle, 3000, 800, 600, 800, 600, 1, 2, 2, false, stats));
            byte[] pixels = AssNative.readPixels(handle);
            for (byte pixel : pixels) assertEquals(0, pixel);
        } finally { AssNative.destroy(handle); }
    }
}
