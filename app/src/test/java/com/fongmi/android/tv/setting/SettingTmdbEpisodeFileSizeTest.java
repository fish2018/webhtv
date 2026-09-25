package com.fongmi.android.tv.setting;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class SettingTmdbEpisodeFileSizeTest {

    @Test
    public void tmdbEpisodeFileSize_defaultsOff() throws Exception {
        String source = read(sourcePath().resolve(Path.of("com", "fongmi", "android", "tv", "setting", "Setting.java")));

        assertTrue(source.contains("Prefers.getBoolean(\"tmdb_episode_file_size\", false)"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path sourcePath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
