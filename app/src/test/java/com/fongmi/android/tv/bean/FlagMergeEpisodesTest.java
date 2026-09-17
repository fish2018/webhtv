package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FlagMergeEpisodesTest {

    @Test
    public void mergeEpisodesUsesSharedListFastPathAndIndexedLookup() throws Exception {
        Path sourcePath = Path.of("app", "src", "main", "java", "com", "fongmi", "android", "tv", "bean", "Flag.java");
        if (!Files.exists(sourcePath)) sourcePath = Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "bean", "Flag.java");
        String source = Files.readString(sourcePath, StandardCharsets.UTF_8);
        int start = source.indexOf("public void mergeEpisodes(List<Episode> items, boolean rev)");
        int end = source.indexOf("private void mergeEpisode", start);
        String body = start >= 0 && end > start ? source.substring(start, end) : "";

        assertTrue("merging a list with itself must be a constant-time no-op",
                body.contains("if (items == getEpisodes()) return;"));
        assertTrue("long episode lists must use indexed identity, URL, value and name lookups",
                body.contains("IdentityHashMap<Episode, Episode>")
                        && body.contains("Map<String, Episode> byUrl")
                        && body.contains("Map<Episode, Episode> byValue")
                        && body.contains("Map<String, Episode> byName"));
        assertFalse("long episode merges must not repeatedly scan the target list",
                body.contains("int index = indexOf(item);"));
    }

    @Test
    public void mergeEpisodes_updatesTmdbMetadataForExistingEpisode() {
        Flag target = new Flag();
        Episode existing = Episode.create("第1集", "http://example.test/1");
        target.getEpisodes().add(existing);

        Episode enriched = Episode.create("第1集", "http://example.test/1");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "Pilot", "", "", "https://image.test/still.jpg", 0, 0);
        enriched.setTmdbEpisode(tmdbEpisode);
        enriched.setDisplayName("第1集 Pilot");

        target.mergeEpisodes(Collections.singletonList(enriched), false);

        assertEquals(1, target.getEpisodes().size());
        assertSame(existing, target.getEpisodes().get(0));
        assertSame(tmdbEpisode, existing.getTmdbEpisode());
        assertEquals("第1集 Pilot", existing.getDisplayName());
    }

    @Test
    public void mergeEpisodes_preservesValidatedCrossSeasonMappingMarker() {
        Flag target = new Flag();
        Episode existing = Episode.create("11", "http://example.test/11");
        target.getEpisodes().add(existing);

        Episode enriched = Episode.create("11", "http://example.test/11");
        TmdbEpisode tmdbEpisode = new TmdbEpisode(1, "Season 2 Episode 1", "", "", "", 0, 0, 0, 2);
        enriched.setMappedTmdbEpisode(tmdbEpisode);

        target.mergeEpisodes(Collections.singletonList(enriched), false);

        assertSame(tmdbEpisode, existing.getTmdbEpisode());
        assertTrue(existing.isTmdbEpisodeMapped());
    }

}
