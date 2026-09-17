package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WebThemeManifestDiskCacheTest {

    private Path directory;

    @Before
    public void setUp() throws Exception {
        directory = Files.createTempDirectory("webtheme-manifest-cache");
    }

    @After
    public void tearDown() throws Exception {
        if (directory == null || !Files.exists(directory)) return;
        try (Stream<Path> paths = Files.walk(directory)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    public void cachedHistoryAndMetadataSurviveAStoreRecreationWithoutLeakingTheUrl() throws Exception {
        String cacheKey = "https://themes.example/theme.json?token=secret\nmobile";
        WebThemeManifestLoader.StoredCache expected = new WebThemeManifestLoader.StoredCache(
                stored("{\"schemaVersion\":2,\"version\":\"2\"}", "\"v2\"", 2345),
                stored("{\"schemaVersion\":2,\"version\":\"1\"}", "\"v1\"", 1234),
                true, "");
        WebThemeManifestDiskCache first = new WebThemeManifestDiskCache(directory.toFile());

        first.write(cacheKey, expected);
        WebThemeManifestDiskCache recreated = new WebThemeManifestDiskCache(directory.toFile());

        assertEquals(expected, recreated.read(cacheKey));
        List<Path> files = dataFiles();
        assertEquals(1, files.size());
        String fileName = files.get(0).getFileName().toString();
        assertTrue(fileName.matches("[0-9a-f]{64}\\.json"));
        assertFalse(fileName.contains("themes.example"));
        assertFalse(fileName.contains("secret"));
    }

    @Test
    public void blockedRollbackStateSurvivesAStoreRecreation() throws Exception {
        WebThemeManifestLoader.StoredManifest rejected = stored(
                "{\"schemaVersion\":2,\"version\":\"2\"}", "\"v2\"", 2345);
        WebThemeManifestLoader.StoredCache expected = new WebThemeManifestLoader.StoredCache(
                stored("{\"schemaVersion\":2,\"version\":\"1\"}", "\"v1\"", 3456),
                rejected, false, WebThemeManifestLoader.revision(rejected.json()));
        WebThemeManifestDiskCache cache = new WebThemeManifestDiskCache(directory.toFile());

        cache.write("key", expected);

        assertEquals(expected, new WebThemeManifestDiskCache(directory.toFile()).read("key"));
    }

    @Test
    public void legacyRawManifestRemainsAvailableAsExpiredStableVersion() throws Exception {
        String cacheKey = "https://themes.example/theme.json\nmobile";
        String json = "{\"schemaVersion\":2}";
        WebThemeManifestDiskCache cache = new WebThemeManifestDiskCache(directory.toFile());
        cache.write(cacheKey, stable(stored(json, "\"v1\"", 1234)));
        Files.writeString(dataFiles().get(0), json, StandardCharsets.UTF_8);

        WebThemeManifestLoader.StoredCache migrated = cache.read(cacheKey);

        assertEquals(json, migrated.current().json());
        assertEquals("", migrated.current().etag());
        assertEquals(0, migrated.current().validatedAt());
        assertNull(migrated.previous());
        assertFalse(migrated.activationPending());
    }

    @Test
    public void versionTwoEnvelopeMigratesAsAStableVersion() throws Exception {
        String cacheKey = "https://themes.example/theme.json\nmobile";
        String json = "{\"schemaVersion\":2}";
        WebThemeManifestDiskCache cache = new WebThemeManifestDiskCache(directory.toFile());
        cache.write(cacheKey, stable(stored(json, "\"seed\"", 1)));
        String v2 = "WEBHTV_THEME_MANIFEST_CACHE_V2\n1234\n22763122\n" + json;
        Files.writeString(dataFiles().get(0), v2, StandardCharsets.UTF_8);

        WebThemeManifestLoader.StoredCache migrated = cache.read(cacheKey);

        assertEquals(json, migrated.current().json());
        assertEquals("\"v1\"", migrated.current().etag());
        assertEquals(1234, migrated.current().validatedAt());
        assertNull(migrated.previous());
        assertFalse(migrated.activationPending());
    }

    @Test
    public void twoMaximumSizedManifestsFitInsideTheMetadataEnvelope() throws Exception {
        String current = "x".repeat(WebThemeManifest.MAX_MANIFEST_BYTES);
        String previous = "y".repeat(WebThemeManifest.MAX_MANIFEST_BYTES);
        WebThemeManifestDiskCache cache = new WebThemeManifestDiskCache(directory.toFile());
        WebThemeManifestLoader.StoredCache expected = new WebThemeManifestLoader.StoredCache(
                stored(current, "\"v2\"", 2345), stored(previous, "\"v1\"", 1234), true, "");

        cache.write("key", expected);

        assertEquals(expected, cache.read("key"));
    }

    @Test
    public void diskCachePrunesEntriesToTheSameBoundAsTheMemoryCache() throws Exception {
        WebThemeManifestDiskCache cache = new WebThemeManifestDiskCache(directory.toFile());

        for (int index = 0; index < WebThemeManifestLoader.MAX_CACHE_ENTRIES + 1; index++) {
            cache.write("https://themes.example/" + index + ".json\nmobile",
                    stable(stored("{\"id\":" + index + "}", "\"v" + index + "\"", index + 1)));
        }

        assertEquals(WebThemeManifestLoader.MAX_CACHE_ENTRIES, dataFiles().size());
    }

    @Test
    public void oversizedVersionIsRejectedBeforeItTouchesDisk() {
        WebThemeManifestDiskCache cache = new WebThemeManifestDiskCache(directory.toFile());
        String oversized = "x".repeat(WebThemeManifest.MAX_MANIFEST_BYTES + 1);
        WebThemeManifestLoader.StoredCache entry = new WebThemeManifestLoader.StoredCache(
                stored("{}", "", 1), stored(oversized, "", 1), false, "");

        assertThrows(IOException.class, () -> cache.write("key", entry));
        assertTrue(dataFilesUnchecked().isEmpty());
    }

    private static WebThemeManifestLoader.StoredCache stable(
            WebThemeManifestLoader.StoredManifest current) {
        return new WebThemeManifestLoader.StoredCache(current, null, false, "");
    }

    private static WebThemeManifestLoader.StoredManifest stored(String json, String etag, long validatedAt) {
        return new WebThemeManifestLoader.StoredManifest(json, etag, validatedAt);
    }

    private List<Path> dataFiles() throws Exception {
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
        }
    }

    private List<Path> dataFilesUnchecked() {
        try {
            return dataFiles();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
