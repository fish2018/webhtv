package com.fongmi.android.tv.server.process;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class DebugLogsPageTest {

    @Test
    public void logsPageHasBackToTopButton() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "server", "process", "DebugLogs.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("debug logs page must render a back-to-top button",
                source.contains("id=\\\"topBtn\\\"") && source.contains("class=\\\"backtop\\\""));
        assertTrue("debug logs page must expose AI diagnosis",
                source.contains("/debug/diagnose") && source.contains("AI诊断"));
        assertTrue("back-to-top button must stay fixed near the lower-right corner",
                source.contains(".backtop{") && source.contains("position:fixed") && source.contains("right:"));
        assertTrue("back-to-top button must scroll the page to the top",
                source.contains("function scrollToTop()") && source.contains("scrollTo({top:0,behavior:'smooth'})"));
        assertTrue("back-to-top button must opt out of auto-stick-to-bottom after clicking",
                source.contains("topBtn.onclick=scrollToTop") && source.contains("stick=false"));
    }

    @Test
    public void logsPageKeepsAiTaggedFailuresInAiGroup() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "server", "process", "DebugLogs.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int aiTagged = source.indexOf("if(r.tag&&r.tag.startsWith('ai-'))");
        int genericError = source.indexOf("if(low.includes('error')||low.includes('exception')||low.includes('failed')");

        assertTrue("debug logs page must recognize ai-* tags",
                aiTagged >= 0);
        assertTrue("ai-* failures should remain in the AI group instead of being swallowed by the generic error group",
                genericError >= 0 && aiTagged < genericError);
        assertTrue("AI grouped failures should still render as error state",
                source.contains("e.kind='ai';e.state=low.includes('failed')"));
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
