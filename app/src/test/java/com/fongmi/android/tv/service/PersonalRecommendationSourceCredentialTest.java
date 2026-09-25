package com.fongmi.android.tv.service;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PersonalRecommendationSourceCredentialTest {

    @Test
    public void defaultPersonalRecommendationUsesCurrentEffectiveCredential() throws Exception {
        String source = read("src/main/java/com/fongmi/android/tv/service/PersonalRecommendationService.java");

        assertTrue(source.contains("this(new TmdbService(), TmdbConfig.effectiveCurrent());"));
    }

    @Test
    public void personAndEpisodeDetailRequestsUseEffectiveCredential() throws Exception {
        String person = read("src/main/java/com/fongmi/android/tv/ui/activity/TmdbPersonActivity.java");
        String personDialog = read("src/main/java/com/fongmi/android/tv/ui/dialog/TmdbPersonDialog.java");
        String mobileEpisode = read("src/mobile/java/com/fongmi/android/tv/ui/dialog/EpisodeDetailDialog.java");
        String leanbackEpisode = read("src/leanback/java/com/fongmi/android/tv/ui/dialog/EpisodeDetailDialog.java");

        assertTrue(person.contains("tmdbConfig = TmdbConfig.effectiveCurrent();"));
        assertTrue(personDialog.contains("this.tmdbConfig = TmdbConfig.effectiveCurrent();"));
        assertTrue(mobileEpisode.contains("TmdbConfig config = TmdbConfig.effectiveCurrent();"));
        assertTrue(leanbackEpisode.contains("TmdbConfig config = TmdbConfig.effectiveCurrent();"));
        assertFalse(mobileEpisode.contains("TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());"));
        assertFalse(leanbackEpisode.contains("TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());"));
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }
}
