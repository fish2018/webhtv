package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.JsonParser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TmdbSourceAdapterTest {

    private static final Gson GSON = new Gson();

    @Test
    public void adapterBuildsBundleWithoutNetworkConfiguration() {
        TmdbSourcePayload payload = GSON.fromJson("""
                {"schema":1,"id":1399,"media_type":"tv","season_number":2,"complete":["core","credits","images","season:2"],
                 "detail":{"id":1399,"name":"Example Show","first_air_date":"2024-01-02","overview":"Overview",
                   "vote_average":8.2,"poster_path":"/poster.jpg","backdrop_path":"/backdrop.jpg",
                   "number_of_seasons":2,"number_of_episodes":1,"genres":[{"id":18}],
                   "aggregate_credits":{"cast":[{"id":11,"name":"Actor","character":"Hero","profile_path":"https://image.test/actor.jpg"}],
                     "crew":[{"id":12,"name":"Director","job":"Director","profile_path":"/director.jpg"}]},
                   "images":{"backdrops":[{"file_path":"/backdrop.jpg"}],"posters":[{"file_path":"/poster.jpg"}]},
                   "recommendations":{"page":1,"results":[{"id":550,"media_type":"movie","title":"Related Movie",
                     "release_date":"1999-10-15","vote_average":8.4,"poster_path":"/related.jpg"}]},
                   "seasons":[{"season_number":2,"episode_count":1,"episodes":[{"episode_number":1,"name":"Episode 1","still_path":"/still.jpg"}],
                     "credits":{"cast":[{"id":21,"name":"Guest","character":"Guest"}]},"images":{"backdrops":[{"file_path":"/season.jpg"}]}}]}}
                """, TmdbSourcePayload.class);

        TmdbBundle bundle = TmdbSourceAdapter.toBundle(payload, new Vod(), new TmdbConfig());

        assertNotNull(bundle);
        assertEquals(1399, bundle.item().getTmdbId());
        assertEquals("Example Show", bundle.item().getTitle());
        assertTrue(bundle.item().getPosterUrl().contains("/poster.jpg"));
        assertTrue(bundle.item().getBackdropUrl().contains("/backdrop.jpg"));
        assertEquals(1, bundle.cast().size());
        assertEquals("Hero", bundle.cast().get(0).getSubtitle());
        assertEquals("https://image.test/actor.jpg", bundle.cast().get(0).getProfileUrl());
        assertEquals(1, bundle.creators().size());
        assertEquals("Director", bundle.creators().get(0).getName());
        assertEquals(2, bundle.photos().size());
        assertEquals(1, bundle.related().size());
        assertEquals("Related Movie", bundle.related().get(0).getTitle());
        assertEquals(Integer.valueOf(2), bundle.seasons().get(0));
        assertEquals(1, bundle.seasonEpisodes().get(2).size());
        assertEquals("Episode 1", bundle.seasonEpisodes().get(2).get(0).getTitle());
        assertEquals(1, bundle.seasonCast().get(2).size());
        assertEquals(1, bundle.seasonPhotos().get(2).size());
    }

    @Test
    public void adapterKeepsAbsoluteHttpsImagesAndRejectsInvalidPaths() {
        TmdbSourcePayload payload = GSON.fromJson("""
                {"schema":1,"id":550,"media_type":"movie","complete":["core","credits","images"],
                 "detail":{"id":550,"title":"Movie","overview":"Overview","release_date":"1999-10-15","vote_average":8.4,
                   "poster_path":"https://cdn.test/poster.jpg","backdrop_path":"http://cdn.test/backdrop.jpg","genres":[],
                   "credits":{"cast":[{"id":1,"name":"Actor","profile_path":"https://cdn.test/actor.jpg"}]},
                   "images":{"posters":[{"file_path":"https://cdn.test/p2.jpg"}]}}}
                """, TmdbSourcePayload.class);

        TmdbBundle bundle = TmdbSourceAdapter.toBundle(payload, null, new TmdbConfig());

        assertNotNull(bundle);
        assertEquals("https://cdn.test/poster.jpg", bundle.item().getPosterUrl());
        assertEquals("", bundle.item().getBackdropUrl());
        assertEquals("https://cdn.test/actor.jpg", bundle.cast().get(0).getProfileUrl());
        assertEquals("https://cdn.test/p2.jpg", bundle.photos().get(0));
    }

    @Test
    public void networkAdapterUsesSameDetailShapeWithoutRequiringApiKey() {
        com.fongmi.android.tv.bean.TmdbItem sourceItem = new com.fongmi.android.tv.bean.TmdbItem(
                550, "movie", "Fallback", "", "Fallback overview", "", "");
        var detail = JsonParser.parseString("""
                {"id":550,"title":"Movie","overview":"Network overview","release_date":"1999-10-15","vote_average":8.4,
                 "poster_path":"/poster.jpg","backdrop_path":"/backdrop.jpg","genres":[{"id":18}],
                 "credits":{"cast":[{"id":1,"name":"Actor","profile_path":"/actor.jpg"}]},
                 "images":{"backdrops":[{"file_path":"/backdrop.jpg"}],"posters":[{"file_path":"/poster.jpg"}]}}
                """).getAsJsonObject();

        TmdbBundle bundle = TmdbSourceAdapter.fromNetwork(sourceItem, detail, new TmdbConfig());

        assertNotNull(bundle);
        assertEquals("Movie", bundle.item().getTitle());
        assertEquals("Network overview", bundle.item().getOverview());
        assertEquals(1, bundle.cast().size());
        assertEquals(2, bundle.photos().size());
    }

    @Test
    public void embeddedVideoResolverKeepsTitleSeasonAndEpisodeScopes() {
        var detail = JsonParser.parseString("""
                {"videos":{"results":[{"id":"title-video","key":"title_key","site":"YouTube","type":"Trailer","name":"Title","official":true,"iso_639_1":"zh-CN"}]},
                 "seasons":[{"season_number":1,"videos":{"results":[{"id":"season-video","key":"season_key","site":"YouTube","type":"Teaser","name":"Season","official":true,"iso_639_1":"zh-CN"}]},
                   "episodes":[{"episode_number":1,"videos":{"results":[{"id":"episode-video","key":"episode_key","site":"YouTube","type":"Clip","name":"Episode","official":true,"iso_639_1":"zh-CN"}]}}]}]}
                """).getAsJsonObject();

        var videos = TmdbSourceAdapter.videos(detail, "tv", 1, 1, "zh-CN");

        assertEquals(3, videos.size());
        assertTrue(videos.stream().anyMatch(video -> video.getScope() == com.fongmi.android.tv.bean.TmdbVideo.Scope.EPISODE && "episode_key".equals(video.getKey())));
        assertTrue(videos.stream().anyMatch(video -> video.getScope() == com.fongmi.android.tv.bean.TmdbVideo.Scope.SEASON && "season_key".equals(video.getKey())));
        assertTrue(videos.stream().anyMatch(video -> video.getScope() == com.fongmi.android.tv.bean.TmdbVideo.Scope.TITLE && "title_key".equals(video.getKey())));
    }
}
