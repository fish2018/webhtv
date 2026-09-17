package androidx.media3.mpvplayer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** Protects the shared MPV/IJK HLS proxy from false positives and false statistics. */
public class MpvHlsAdblockGateTest {

    @Test
    public void legacyFallbackRequiresConfiguredRulesAndItsToggle() throws Exception {
        String source = readSource();

        assertTrue(source.contains("legacyFallback = !rules.isEmpty() && HlsRuleConfig.isLegacyFallbackEnabled()"));
        assertTrue(source.contains("HlsAdblockPipeline.apply(url, text, rules, legacyFallback)"));
        assertFalse(source.contains("HlsAdblockPipeline.apply(url, text, HlsRuleConfig.getRules(), HlsRuleConfig.isLegacyFallbackEnabled())"));
    }

    @Test
    public void bypassedMpvCandidateIsNotRecordedAndIjkUsesItsOwnPipelineName() throws Exception {
        String method = methodBody(readSource(), "private String applyAdblock", "private void recordAndNotifyAdblock");
        int bypass = method.indexOf("if (kernel == PlayerSetting.MPV)");
        int bypassReturn = method.indexOf("return text;", bypass);
        int record = method.indexOf("recordAndNotifyAdblock(url, outcome);");

        assertTrue("MPV bypass must return before statistics are recorded",
                bypass >= 0 && bypassReturn > bypass && record > bypassReturn);
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
