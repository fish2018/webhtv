package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

/** MediaItem enables the patched Exo ad parser only when both ad blocking and its fallback toggle are enabled. */
public class ExoParserAdblockGateTest {

    @Test
    public void parserAdblockRequiresMasterSwitchAndFallbackToggle() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/fongmi/android/tv/player/exo/ExoUtil.java"));

        assertTrue(source.contains("builder.setAdblock(Setting.isAdblock() && HlsRuleConfig.isLegacyFallbackEnabled())"));
        assertFalse(source.contains("!HlsRuleConfig.getRules().isEmpty()"));
        assertFalse(source.contains("builder.setAdblock(Setting.isAdblock())"));
    }
}
