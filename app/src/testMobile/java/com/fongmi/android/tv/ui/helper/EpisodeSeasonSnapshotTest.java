package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.Episode;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class EpisodeSeasonSnapshotTest {

    @Test
    public void fingerprintIsStableForEquivalentInputs() {
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        counts.put(1, 2);
        counts.put(2, 2);

        String first = EpisodeSeasonSnapshot.fingerprint(episodes("1", "2", "3", "4"), counts);
        String second = EpisodeSeasonSnapshot.fingerprint(episodes("1", "2", "3", "4"), new LinkedHashMap<>(counts));

        assertEquals(first, second);
    }

    @Test
    public void fingerprintChangesWhenEpisodesOrSeasonCountsChange() {
        Map<Integer, Integer> counts = Map.of(1, 2, 2, 2);
        String original = EpisodeSeasonSnapshot.fingerprint(episodes("1", "2", "3", "4"), counts);

        assertNotEquals(original, EpisodeSeasonSnapshot.fingerprint(episodes("1", "2", "4", "5"), counts));
        assertNotEquals(original, EpisodeSeasonSnapshot.fingerprint(episodes("1", "2", "3", "4"), Map.of(1, 3, 2, 1)));
    }

    private static List<Episode> episodes(String... names) {
        return java.util.Arrays.stream(names).map(name -> Episode.create(name, "https://example.invalid/" + name)).toList();
    }
}
