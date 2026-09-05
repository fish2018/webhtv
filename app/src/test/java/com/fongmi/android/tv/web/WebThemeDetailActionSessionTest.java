package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;

import org.junit.Test;

import java.util.List;

public class WebThemeDetailActionSessionTest {

    @Test
    public void issuesStableOpaqueReferencesAndResolvesOnlyRegisteredValues() {
        WebThemeDetailActionSession session = new WebThemeDetailActionSession();
        TmdbPerson person = new TmdbPerson(9, "演员甲", "角色甲", "https://img.example/person.jpg", "Acting", "人物简介");
        TmdbItem item = recommendation(101, "推荐甲");

        String personRef = session.issuePerson(person);
        String samePersonRef = session.issuePerson(person);
        String imageRef = session.issueImage("https://img.example/photo-1.jpg");
        session.issueImage("https://img.example/photo-2.jpg");
        String recommendationRef = session.issueRecommendation(item, "ai");
        String linkRef = session.issueExternal("TMDB", "https://www.themoviedb.org/movie/101");
        Episode episode = Episode.create("第 1 集", "https://media.example/episode.m3u8");
        String episodeRef = session.issueEpisode(episode);

        assertEquals(personRef, samePersonRef);
        assertNotEquals("9", personRef);
        assertEquals(9, session.resolvePerson(personRef).getPersonId());
        assertEquals(2, session.resolveImage(imageRef).gallery().size());
        assertEquals("ai", session.resolveRecommendation(recommendationRef).source());
        assertEquals("themoviedb.org", session.resolveExternal(linkRef).host());
        assertEquals("第 1 集", session.resolveEpisode(episodeRef).getName());
        assertNull(session.resolvePerson("person-forged"));
        assertNull(session.resolveImage("image-forged"));
        assertNull(session.resolveRecommendation("recommendation-forged"));
        assertNull(session.resolveExternal("external-forged"));
        assertNull(session.resolveEpisode("episode-forged"));
    }

    @Test
    public void stableReferencesResolveLatestMetadataAndClearInvalidatesThem() {
        WebThemeDetailActionSession session = new WebThemeDetailActionSession();
        TmdbPerson originalPerson = new TmdbPerson(9, "演员甲", "角色甲", "", "Acting", "旧简介");
        TmdbPerson updatedPerson = new TmdbPerson(9, "演员甲", "角色乙", "", "Acting", "新简介");
        TmdbItem originalItem = recommendation(103, "推荐丙");
        TmdbItem updatedItem = new TmdbItem(103, "movie", "推荐丙", "2026 · 电影", "更新简介",
                "https://img.example/103-new.jpg", "https://img.example/103-new-backdrop.jpg",
                "新主演", 9.1, "zh", "CN", List.of(18), "", 9.1, 8.8, "新理由");

        String personRef = session.issuePerson(originalPerson);
        String recommendationRef = session.issueRecommendation(originalItem, "ai");
        String episodeRef = session.issueEpisode(Episode.create("第 2 集", "https://media.example/episode-2.m3u8"));

        assertEquals(personRef, session.issuePerson(updatedPerson));
        assertEquals(recommendationRef, session.issueRecommendation(updatedItem, "ai"));
        assertEquals("角色乙", session.resolvePerson(personRef).getSubtitle());
        assertEquals("更新简介", session.resolveRecommendation(recommendationRef).item().getOverview());

        session.clear();
        assertNull(session.resolvePerson(personRef));
        assertNull(session.resolveRecommendation(recommendationRef));
        assertNull(session.resolveEpisode(episodeRef));
    }

    @Test
    public void rejectsUnsafeUrlsAndTracksNotInterestedFeedback() {
        WebThemeDetailActionSession session = new WebThemeDetailActionSession();
        TmdbItem item = recommendation(102, "推荐乙");

        assertEquals("", session.issueImage("file:///sdcard/private.jpg"));
        assertEquals("", session.issueExternal("危险链接", "intent://evil"));
        assertEquals("", session.issueExternal("本地链接", "https://127.0.0.1/private"));
        assertEquals("", session.issueExternal("十六进制回环", "https://0x7f000001/private"));
        assertEquals("", session.issueExternal("八进制回环", "https://0177.0.0.1/private"));
        assertEquals("", session.issueExternal("十进制回环", "https://2130706433/private"));
        assertEquals("", session.issueExternal("缩写回环", "https://127.1/private"));
        assertEquals("", session.issueExternal("IPv6 回环", "https://[::1]/private"));
        assertEquals("", session.issueExternal("IPv6 映射回环", "https://[::ffff:127.0.0.1]/private"));
        assertEquals("", session.issueExternal("IPv6 私网", "https://[fd00::1]/private"));

        String ref = session.issueRecommendation(item, "tmdb");
        assertFalse(session.isRecommendationHidden(item, "tmdb"));
        WebThemeDetailActionSession.Recommendation resolved = session.markNotInterested(ref);
        assertNotNull(resolved);
        assertTrue(session.isRecommendationHidden(item, "tmdb"));
    }

    private static TmdbItem recommendation(int id, String title) {
        return new TmdbItem(id, "movie", title, "2026 · 电影", "简介",
                "https://img.example/poster.jpg", "https://img.example/backdrop.jpg", "", 8.0,
                "zh", "CN", List.of(), "", 8.0, 0.0, "推荐理由");
    }
}
