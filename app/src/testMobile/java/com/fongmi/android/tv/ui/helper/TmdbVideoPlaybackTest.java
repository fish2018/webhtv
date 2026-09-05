package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.bean.TmdbVideo;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class TmdbVideoPlaybackTest {

    @Test
    public void createLaunchUsesPushWithExplicitYoutubeEpisodeAndNoHistoryResume() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.SIMPLIFIED_CHINESE);
        try {
            JsonObject object = new JsonObject();
            object.addProperty("id", "video-id");
            object.addProperty("key", "abc_DEF-1");
            object.addProperty("site", "YouTube");
            object.addProperty("name", "Official Trailer");
            object.addProperty("type", "Trailer");
            TmdbVideo video = TmdbVideo.from(object, TmdbVideo.Scope.EPISODE, 2, 3);

            TmdbVideoPlayback.Launch launch = TmdbVideoPlayback.create(video, "推送");

            assertEquals(SiteApi.PUSH, launch.getKey());
            assertEquals("https://www.youtube.com/watch?v=abc_DEF-1|Official Trailer", launch.getId());
            assertEquals("Official Trailer", launch.getName());
            assertEquals("https://i.ytimg.com/vi/abc_DEF-1/hqdefault.jpg", launch.getPic());
            assertEquals("预告片 · 当前集", launch.getMark());
            assertEquals("推送", launch.getPlayFlag());
            assertEquals("Official Trailer", launch.getPlayEpisodeName());
            assertEquals("https://www.youtube.com/watch?v=abc_DEF-1", launch.getPlayEpisodeUrl());
            assertFalse(launch.isResumeFromHistory());
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void createLaunchRejectsMissingVideo() {
        assertNull(TmdbVideoPlayback.create(null, "推送"));
    }

    @Test
    public void playOpensWindowedPopupInsteadOfTransientActivity() throws Exception {
        Path root = Files.exists(Path.of("src", "main")) ? Path.of("") : Path.of("app");
        String source = Files.readString(root.resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "ui", "helper", "TmdbVideoPlayback.java")), StandardCharsets.UTF_8);

        assertTrue(source.contains("if (!(activity instanceof FragmentActivity)) return false;"));
        assertTrue(source.contains("TmdbVideoPlayerDialog.show((FragmentActivity) activity, launch)"));
        assertFalse(source.contains("VideoActivity.createTransientIntent"));
        assertFalse(source.contains("launchTransientPlayback"));
        assertFalse(source.contains("TransientVideoActivity"));
    }
}
