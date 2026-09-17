package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** MediaItem must not enable the patched Exo ad parser without configured rules. */
public class ExoParserAdblockGateTest {

    @Test
    public void parserAdblockRequiresConfiguredRules() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java"));

        assertTrue(source.contains("Setting.isAdblock() && !HlsRuleConfig.getRules().isEmpty()")
                && source.contains("&& HlsRuleConfig.isLegacyFallbackEnabled()"));
        assertFalse(source.contains("builder.setAdblock(Setting.isAdblock())"));
    }
}
