package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.SyncOptions;

import org.junit.Test;

import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SyncFilesOptionsTest {

    @Test
    public void webHomeOnlyIncludesCustomCspFiles() {
        SyncOptions options = new SyncOptions().config(false).spider(false).webHome(true);

        assertEquals(List.of(SyncFiles.CUSTOM_CSP_PATH), SyncFiles.getPaths(options));
    }

    @Test
    public void configOnlyIncludesCustomCspFiles() {
        SyncOptions options = new SyncOptions().config(true).spider(false).webHome(false);

        assertEquals(List.of(SyncFiles.CUSTOM_CSP_PATH), SyncFiles.getPaths(options));
    }

    @Test
    public void noFileBackedFeatureProducesNoArchivePaths() {
        SyncOptions options = new SyncOptions().config(false).spider(false).webHome(false);

        assertTrue(SyncFiles.getPaths(options).isEmpty());
    }

    @Test
    public void rawPathsCollapseNestedArchiveTargets() {
        assertEquals(List.of("TV", "TVBox"), SyncFiles.normalizeTargets(List.of("TV", "TV/CustomCsp", "TVBox", "TV/log")));
    }

    @Test
    public void runtimeDirectoriesAreSkippedAsWholeTrees() {
        assertTrue(SyncFiles.skipTree("TV/log"));
        assertTrue(SyncFiles.skipTree("TV/log/request"));
        assertTrue(SyncFiles.skipTree("TV/lib"));
        assertTrue(SyncFiles.skipTree("TV/LogVar/search_cache.db"));
        assertFalse(SyncFiles.skipTree("TV/logs"));
        assertFalse(SyncFiles.skipTree("TV/library"));
    }

    @Test
    public void archiveTrackerRejectsRepeatedDirectoriesAndEntries() throws Exception {
        SyncFiles.ArchiveTracker tracker = new SyncFiles.ArchiveTracker();
        java.io.File directory = Files.createTempDirectory("sync-files-tracker-").toFile();
        try {
            assertTrue(tracker.visit(directory));
            assertFalse(tracker.visit(directory));
            assertTrue(tracker.addEntry("TV/CustomCsp/"));
            assertFalse(tracker.addEntry("TV/CustomCsp/"));
        } finally {
            Files.deleteIfExists(directory.toPath());
        }
    }
}
