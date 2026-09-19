package com.github.catvod.crawler.diagnostics;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrawlerLogCategoryTest {
    @Test public void runtimeOutputUsesTheExistingNetworkSwitch() {
        for (String tag : new String[]{"quickjs", "python-spider", "SpiderDebug"}) {
            DiagnosticCategories.Category category = DiagnosticCategories.text(tag);
            assertEquals(DiagnosticCategories.Category.NETWORK, category);
            assertTrue(DiagnosticCategories.accepts(DiagnosticCategories.ALL, category));
            assertFalse(DiagnosticCategories.accepts(DiagnosticCategories.ALL & ~2, category));
            assertTrue(DiagnosticCategories.accepts(2, category));
        }
    }
}
