package androidx.media3.mpvplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MpvRenderLogPolicyTest {

    private static final String SOURCE_PATH = "app/src/main/java/androidx/media3/mpvplayer/MpvPlayer.java";

    /**
     * The first two lines are what the native library actually emits. The third is deliberately
     * case-mangled: without it, removing the policy's own lower-casing would keep every test green,
     * because the substrings being matched happen to be lowercase in the real log lines.
     */
    @Test
    public void recognisesTheRenderPipelineTimeoutWarningsTheNativeLibraryEmits() {
        assertTrue(MpvRenderLogPolicy.isRenderPipelineTimeout("[vo/gpu-next] AImageReader frame acquisition timed out (timeouts=3, no-buffer=0)"));
        assertTrue(MpvRenderLogPolicy.isRenderPipelineTimeout("[vo/gpu-next] Vulkan conversion fence timed out output=2"));
        assertTrue(MpvRenderLogPolicy.isRenderPipelineTimeout("[VO/GPU-NEXT] Vulkan Conversion Fence Timed Out output=2"));
    }

    @Test
    public void leavesRealNetworkTimeoutsAlone() {
        assertFalse(MpvRenderLogPolicy.isRenderPipelineTimeout("[ffmpeg/demuxer] Connection timed out"));
        assertFalse(MpvRenderLogPolicy.isRenderPipelineTimeout("[stream] Operation timed out"));
    }

    @Test
    public void recognisesTheConversionFallbacksThatAlreadyRecovered() {
        assertTrue(MpvRenderLogPolicy.isRecoveredRenderFallback("[vo/gpu-next] Stable Vulkan conversion initialization failed; trying the newer conversion backend"));
        assertTrue(MpvRenderLogPolicy.isRecoveredRenderFallback("[vo/gpu-next] Vulkan GPU conversion initialization failed; falling back to direct AHardwareBuffer sampling"));
        assertTrue(MpvRenderLogPolicy.isRecoveredRenderFallback("[VO/GPU-NEXT] Stable Vulkan Conversion Initialization Failed; trying the newer conversion backend"));
    }

    @Test
    public void leavesRealVideoOutputFailuresAlone() {
        assertFalse(MpvRenderLogPolicy.isRecoveredRenderFallback("[vo/gpu] Video output failed"));
        assertFalse(MpvRenderLogPolicy.isRecoveredRenderFallback("[vo/gpu] Could not create EGL context"));
    }

    @Test
    public void treatsMissingLineAsNoSignal() {
        assertFalse(MpvRenderLogPolicy.isRenderPipelineTimeout(null));
        assertFalse(MpvRenderLogPolicy.isRecoveredRenderFallback(null));
    }

    /**
     * Both guards have to run before their keyword lists: the network list matches a bare "timed out"
     * and the video-output list matches "vo/" plus "failed", so a transient GPU stall or an already
     * recovered conversion fallback would otherwise be reported as a fatal error — the video-output
     * one even triggers forceMediaCodecCopy(), the per-frame readback this native update avoids.
     */
    @Test
    public void bothClassifiersConsultThePolicyFirst() throws Exception {
        String source = readPlayerSource();

        assertTrue(guardPrecedesKeywords(source, "private boolean isNetworkFailureLog(String lower) {", "lower.contains(\"http error\")", "if (MpvRenderLogPolicy.isRenderPipelineTimeout(lower)) return false;"));
        assertTrue(guardPrecedesKeywords(source, "private boolean isVideoOutputFailureLog(String lower) {", "lower.contains(\"video output failed\")", "if (MpvRenderLogPolicy.isRecoveredRenderFallback(lower)) return false;"));
    }

    private static boolean guardPrecedesKeywords(String source, String signature, String firstKeyword, String guard) {
        int start = source.indexOf(signature);
        int end = source.indexOf(firstKeyword, start);
        assertTrue("cannot locate " + signature, start >= 0 && end > start);
        return source.substring(start, end).contains(guard);
    }

    private static String readPlayerSource() throws Exception {
        File root = new File(System.getProperty("user.dir"));
        while (root != null && !new File(root, SOURCE_PATH).isFile()) root = root.getParentFile();
        assertNotNull("Unable to locate project root from " + System.getProperty("user.dir"), root);
        return Files.readString(new File(root, SOURCE_PATH).toPath(), StandardCharsets.UTF_8);
    }
}
