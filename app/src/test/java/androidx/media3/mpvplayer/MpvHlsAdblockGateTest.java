package androidx.media3.mpvplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Protects the shared MPV/IJK HLS proxy from false positives and false statistics. */
public class MpvHlsAdblockGateTest {

    @Test
    public void legacyFallbackRequiresItsToggleWithoutStructuredRules() throws Exception {
        String source = readSource();

        assertTrue(source.contains("legacyFallback = HlsRuleConfig.isLegacyFallbackEnabled()"));
        assertFalse(source.contains("legacyFallback = !rules.isEmpty()"));
        assertTrue(source.contains("HlsAdblockPipeline.apply(url, text, rules, legacyFallback)"));
        assertFalse(source.contains("HlsAdblockPipeline.apply(url, text, HlsRuleConfig.getRules(), HlsRuleConfig.isLegacyFallbackEnabled())"));
    }

    @Test
    public void mpvCandidateNotifiesBeforeReturningAndIjkUsesItsOwnPipelineName() throws Exception {
        String method = methodBody(readSource(), "private String applyAdblock", "private void recordAndNotifyAdblock");
        int mpv = method.indexOf("if (kernel == PlayerSetting.MPV)");
        int notify = method.indexOf("recordAndNotifyAdblock(url, outcome);", mpv);
        int mpvReturn = method.indexOf("return text;", mpv);

        assertTrue("MPV must notify for a valid removal plan before preserving the source manifest",
                mpv >= 0 && notify > mpv && mpvReturn > notify);
        assertTrue(readSource().contains("kernel == PlayerSetting.IJK ? \"IJK\" : \"MPV\""));
    }

    private static String readSource() throws Exception {
        return Files.readString(Path.of("src/main/java/androidx/media3/mpvplayer/MpvHlsProxy.java"));
    }

    private static String methodBody(String source, String startToken, String endToken) {
        int start = source.indexOf(startToken);
        int end = source.indexOf(endToken, start);
        assertTrue("Missing source token: " + startToken, start >= 0);
        assertTrue("Missing source token after " + startToken + ": " + endToken, end > start);
        return source.substring(start, end);
    }
}
