package io.github.anilbeesetti.nextlib.media3ext.ffdecoder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class CompatFfmpegVideoRendererSourceTest {

    private static final String SOURCE_PATH = "app/src/main/java/io/github/anilbeesetti/nextlib/media3ext/ffdecoder/CompatFfmpegVideoRenderer.java";

    /**
     * The audio renderer may ask whether the platform supports the exact {@link
     * androidx.media3.common.Format}, but the video renderer must not: a 4K HEVC track that exceeds
     * the decoder's level reports unsupported, which would hand the track back to FFmpeg software
     * decoding and reintroduce the dropped-frame regression.
     */
    @Test
    public void platformOwnershipCheck_asksOnlyAboutMimeTypeNotExactFormat() throws Exception {
        String source = readSource();

        assertTrue(source.contains("getDecoderInfos(mimeType, false, false)"));
        assertFalse(source.contains("isFormatSupported("));
    }

    /**
     * The check must run through the selector the MediaCodec renderers were built with. Hardcoding
     * {@code MediaCodecSelector.DEFAULT} would make FFmpeg yield to a software platform decoder that
     * ExoUtil's hardware-only selector has already filtered out, leaving the track with no renderer
     * at all: a silent black screen with no PlaybackException to trigger any fallback.
     */
    @Test
    public void platformOwnershipCheck_usesTheInjectedSelectorNotTheDefaultOne() throws Exception {
        String source = readSource();

        assertTrue(source.contains("platformDecoderSelector == null ? MediaCodecSelector.DEFAULT : platformDecoderSelector"));
        assertTrue(source.contains("!selector.getDecoderInfos(mimeType, false, false).isEmpty()"));
    }

    private static String readSource() throws Exception {
        File root = new File(System.getProperty("user.dir"));
        while (root != null && !new File(root, SOURCE_PATH).isFile()) root = root.getParentFile();
        assertNotNull("Unable to locate project root from " + System.getProperty("user.dir"), root);
        return Files.readString(new File(root, SOURCE_PATH).toPath(), StandardCharsets.UTF_8);
    }
}
