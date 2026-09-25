package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class WebThemeCompatibilityMatrixTest {

    @Test
    public void generatedRowsExerciseEveryPagePermissionAndLegacyCombination() {
        Set<String> methods = new HashSet<>();

        for (WebThemeCapabilityRegistry.CompatibilityEntry entry
                : WebThemeCapabilityRegistry.compatibilityEntries()) {
            assertTrue("Duplicate method: " + entry.method(), methods.add(entry.method()));
            assertEquals(entry.legacyAllowed(),
                    WebThemeCapabilityRegistry.allowsLegacyMethod(entry.method()));
            assertEquals(entry.manifestRequired(), !entry.permission().isEmpty());
            assertEquals((entry.manifestRequired() ? entry.permission() : entry.method())
                    + "@" + entry.contractVersion(), entry.capabilityId());

            Set<String> granted = entry.manifestRequired()
                    ? Set.of(entry.permission())
                    : Set.of();
            for (WebThemePage page : WebThemePage.values()) {
                boolean supported = entry.pages().contains(page);
                assertEquals(entry.method() + " on " + page, supported,
                        WebThemeCapabilityRegistry.allowsMethod(page, granted, entry.method()));
                assertEquals(entry.capabilityId() + " on " + page, supported,
                        WebThemeCapabilityRegistry.capabilities(page, granted).contains(entry.capabilityId()));
                if (entry.manifestRequired()) {
                    assertFalse(WebThemeCapabilityRegistry.allowsMethod(page, Set.of(), entry.method()));
                }
            }
        }
    }

    @Test
    public void checkedDocumentationMatchesTheGeneratedRuntimeMatrix() throws Exception {
        String checked = Files.readString(repoPath("docs/webtheme-compatibility-matrix.md"),
                StandardCharsets.UTF_8).replace("\r\n", "\n");

        assertEquals(checked, WebThemeCompatibilityMatrix.markdown());
    }

    private static Path repoPath(String relative) {
        Path direct = Path.of(relative);
        if (Files.exists(direct)) return direct;
        Path parent = Path.of("..").resolve(relative);
        if (Files.exists(parent)) return parent;
        throw new IllegalStateException("Missing repository file: " + relative);
    }
}
