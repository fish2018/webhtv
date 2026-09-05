package com.fongmi.android.tv.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.Filter;
import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Flag;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Value;
import com.fongmi.android.tv.bean.Vod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class WebHomeVodContractTest {

    @Test
    public void home_mapsFlatSafeContractWithoutTransportSecrets() throws Exception {
        Site site = Site.get("demo", "Demo Source");
        site.setType(3);
        site.setHomePage("https://secret.example/home.html");
        setField(site, "header", Map.of("Cookie", "session=secret", "Authorization", "Bearer secret"));

        Result result = new Result();
        result.setTypes(List.of(new Gson().fromJson(
                "{\"type_id\":\"movie\",\"type_name\":\"Movies\",\"land\":1,\"ratio\":1.5,\"filter\":true}",
                com.fongmi.android.tv.bean.Class.class)));
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        filters.put("movie", List.of(new Gson().fromJson(
                "{\"key\":\"year\",\"name\":\"Year\",\"init\":\"2026\",\"value\":["
                        + "{\"n\":\"2026\",\"v\":\"2026\"},{\"n\":\"2025\",\"v\":\"2025\"}]}",
                Filter.class)));
        setField(result, "filters", filters);
        result.setList(List.of(TestVod.vod("vod-1", "Eclipse One")
                .details("https://img.example/1.jpg", "4K", "2026", "Sci-Fi", "A safe summary")
                .style(new Style("rect", 1.5f))));

        JsonObject json = WebHomeVodContract.home(site, result, false, true, 4);

        assertEquals(1, json.get("version").getAsInt());
        JsonObject source = json.getAsJsonObject("source");
        assertEquals("demo", source.get("key").getAsString());
        assertEquals("Demo Source", source.get("name").getAsString());
        assertEquals(3, source.get("type").getAsInt());
        assertFalse(source.has("header"));
        assertFalse(source.has("homePage"));
        assertFalse(json.toString().contains("session=secret"));
        assertFalse(json.toString().contains("Bearer secret"));

        JsonObject type = json.getAsJsonArray("classes").get(0).getAsJsonObject();
        assertEquals("movie", type.get("typeId").getAsString());
        assertEquals("Movies", type.get("typeName").getAsString());
        assertEquals("rect", type.getAsJsonObject("style").get("type").getAsString());
        assertEquals(1.5f, type.getAsJsonObject("style").get("ratio").getAsFloat(), 0.001f);

        JsonObject filter = json.getAsJsonObject("filters").getAsJsonArray("movie").get(0).getAsJsonObject();
        assertEquals("year", filter.get("key").getAsString());
        assertEquals("Year", filter.get("name").getAsString());
        assertEquals("2026", filter.get("init").getAsString());
        assertEquals("2026", filter.getAsJsonArray("values").get(0).getAsJsonObject().get("value").getAsString());

        JsonObject item = json.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(0, item.get("index").getAsInt());
        assertEquals("vod", item.get("kind").getAsString());
        assertEquals("vod-1", item.get("vodId").getAsString());
        assertEquals("demo", item.get("siteKey").getAsString());
        assertEquals("Eclipse One", item.get("name").getAsString());
        assertEquals("A safe summary", item.get("content").getAsString());
        assertFalse(item.has("vod_play_url"));

        JsonObject capabilities = json.getAsJsonObject("capabilities");
        assertTrue(capabilities.get("category").getAsBoolean());
        assertTrue(capabilities.get("filters").getAsBoolean());
        assertTrue(capabilities.get("recommend").getAsBoolean());
        JsonObject client = json.getAsJsonObject("client");
        assertFalse(client.get("isLeanback").getAsBoolean());
        assertTrue(client.get("isLandscape").getAsBoolean());
        assertEquals(4, client.get("suggestedColumns").getAsInt());
    }

    @Test
    public void category_mapsKindsPaginationAndRequestEcho() throws Exception {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        setField(result, "pagecount", 3);
        result.setList(List.of(
                TestVod.folder("folder-1", "Folder"),
                TestVod.action("action-1", "Action", "refresh-token"),
                TestVod.vod("vod-2", "Playable")));

        JsonObject json = WebHomeVodContract.category(site, "series", 2, true,
                Map.of("year", "2026"), result, true, false, 6);

        assertEquals("series", json.getAsJsonObject("query").get("typeId").getAsString());
        assertEquals(2, json.get("page").getAsInt());
        assertEquals(3, json.get("pageCount").getAsInt());
        assertTrue(json.get("hasMore").getAsBoolean());
        assertTrue(json.getAsJsonObject("query").get("filter").getAsBoolean());
        assertEquals("2026", json.getAsJsonObject("query").getAsJsonObject("extend").get("year").getAsString());

        JsonArray items = json.getAsJsonArray("items");
        assertEquals("folder", items.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("action", items.get(1).getAsJsonObject().get("kind").getAsString());
        assertEquals("refresh-token", items.get(1).getAsJsonObject().get("action").getAsString());
        assertEquals("vod", items.get(2).getAsJsonObject().get("kind").getAsString());
        assertTrue(json.getAsJsonObject("client").get("isLeanback").getAsBoolean());
    }

    @Test
    public void category_unknownPageCountKeepsPagingForNonEmptyPage() throws Exception {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        setField(result, "pagecount", 0);
        result.setList(List.of(TestVod.vod("vod-1", "Page one")));

        JsonObject json = WebHomeVodContract.category(site, "movie", 1, false,
                Map.of(), result, false, false, 3);

        assertEquals(0, json.get("pageCount").getAsInt());
        assertTrue(json.get("hasMore").getAsBoolean());
    }

    @Test
    public void category_unknownPageCountStopsAfterEmptyPage() throws Exception {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        setField(result, "pagecount", 0);
        result.setList(List.of());

        JsonObject json = WebHomeVodContract.category(site, "movie", 2, false,
                Map.of(), result, false, false, 3);

        assertEquals(0, json.get("pageCount").getAsInt());
        assertFalse(json.get("hasMore").getAsBoolean());
    }

    @Test
    public void category_capsRemoteItemCount() {
        Site site = Site.get("current", "Current Source");
        Result result = new Result();
        result.setList(Collections.nCopies(WebHomeVodContract.MAX_REMOTE_ITEMS + 1,
                TestVod.vod("vod-1", "Repeated")));

        JsonObject json = WebHomeVodContract.category(site, "movie", 1, false,
                Map.of(), result, false, false, 3);

        assertEquals(WebHomeVodContract.MAX_REMOTE_ITEMS, json.getAsJsonArray("items").size());
        assertTrue(json.get("truncated").getAsBoolean());
        assertTrue(json.get("hasMore").getAsBoolean());
    }

    @Test
    public void detail_exposesOpaqueEpisodeReferencesWithoutRawPlaybackUrls() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Eclipse Detail")
                .details("https://img.example/poster.jpg", "更新至 2 集", "2026", "科幻", "安全简介");
        vod.setPlayFrom("line-a$$$line-b");
        vod.setPlayUrl("第 1 集$https://media.example/a1.m3u8#第 2 集$https://media.example/a2.m3u8"
                + "$$$正片$https://media.example/b1.m3u8");
        vod.setFlags();
        WebThemePlaySession session = new WebThemePlaySession();

        JsonObject json = WebHomeVodContract.detail(site, vod, session, true, null, true, true);

        assertEquals("vod-1", json.getAsJsonObject("item").get("vodId").getAsString());
        assertEquals(2, json.getAsJsonArray("sources").size());
        JsonObject episode = json.getAsJsonArray("sources").get(0).getAsJsonObject()
                .getAsJsonArray("episodes").get(0).getAsJsonObject();
        String playRef = episode.get("playRef").getAsString();
        assertTrue(playRef.startsWith("play_"));
        assertFalse(json.toString().contains("media.example"));
        assertFalse(json.toString().contains("vod_play_url"));
        assertEquals("https://media.example/a1.m3u8",
                session.resolve(playRef, "current", "vod-1").getEpisodeUrl());
        assertTrue(json.getAsJsonObject("state").get("favorite").getAsBoolean());
        assertFalse(json.getAsJsonObject("state").has("history"));
        assertTrue(json.getAsJsonObject("capabilities").get("canPlay").getAsBoolean());
    }

    @Test
    public void detail_capsEpisodeReferencesAndMarksTruncation() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Many Episodes");
        StringBuilder urls = new StringBuilder();
        for (int i = 0; i <= WebThemePlaySession.MAX_REFERENCES; i++) {
            if (i > 0) urls.append('#');
            urls.append("第 ").append(i + 1).append(" 集$url-").append(i);
        }
        vod.setFlags(List.of(Flag.create("line-a", urls.toString())));

        JsonObject json = WebHomeVodContract.detail(site, vod, new WebThemePlaySession(), false, null, false, false);

        assertEquals(WebThemePlaySession.MAX_REFERENCES,
                json.getAsJsonArray("sources").get(0).getAsJsonObject().getAsJsonArray("episodes").size());
        assertTrue(json.get("truncated").getAsBoolean());
        assertFalse(json.getAsJsonObject("capabilities").get("canFavorite").getAsBoolean());
        assertFalse(json.getAsJsonObject("capabilities").get("canPlay").getAsBoolean());
    }

    @Test
    public void detail_incrementalRefreshKeepsExistingPlayReferencesValid() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Stable Session");
        vod.setFlags(List.of(Flag.create("line-a", "第 1 集$https://media.example/a1.m3u8")));
        WebThemePlaySession session = new WebThemePlaySession();

        JsonObject first = WebHomeVodContract.detail(site, vod, session, false, null, true, true);
        String firstRef = first.getAsJsonArray("sources").get(0).getAsJsonObject()
                .getAsJsonArray("episodes").get(0).getAsJsonObject().get("playRef").getAsString();
        JsonObject refreshed = WebHomeVodContract.detail(site, vod, session, false, null, true, true,
                true, WebThemeDetailMetadata.EMPTY);
        String refreshedRef = refreshed.getAsJsonArray("sources").get(0).getAsJsonObject()
                .getAsJsonArray("episodes").get(0).getAsJsonObject().get("playRef").getAsString();

        assertEquals(firstRef, refreshedRef);
        assertEquals("https://media.example/a1.m3u8",
                session.resolve(firstRef, "current", "vod-1").getEpisodeUrl());
    }

    @Test
    public void detail_episodeIdentitySurvivesTmdbReorderingWithinTheSameSession() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Stable Episode Identity");
        vod.setFlags(List.of(Flag.create("line-a", "第一集$url-a#第二集$url-b")));
        WebThemePlaySession session = new WebThemePlaySession();

        JsonObject first = WebHomeVodContract.detail(site, vod, session, false, null, true, true);
        String firstId = episodeByName(first, "第二集").get("episodeId").getAsString();
        Collections.swap(vod.getFlags().get(0).getEpisodes(), 0, 1);
        JsonObject refreshed = WebHomeVodContract.detail(site, vod, session, false, null, true, true);

        assertEquals(firstId, episodeByName(refreshed, "第二集").get("episodeId").getAsString());
        assertEquals("url-b", session.resolve(firstId, "current", "vod-1").getEpisodeUrl());
    }

    @Test
    public void detail_matchesHistoryByEpisodeNameWhenSignedPlaybackUrlChanges() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Refreshed Signed URL");
        vod.setFlags(List.of(Flag.create("line-a",
                "第一集$https://media.example/one?sig=new#第二集$https://media.example/two?sig=new")));
        History history = new History();
        history.setVodFlag("line-a");
        history.setVodRemarks("第二集");
        history.setEpisodeUrl("https://media.example/two?sig=expired");

        JsonObject detail = WebHomeVodContract.detail(site, vod, new WebThemePlaySession(),
                false, history, true, true);

        assertTrue(episodeByName(detail, "第二集").get("selected").getAsBoolean());
        assertFalse(episodeByName(detail, "第一集").get("selected").getAsBoolean());
    }

    @Test
    public void detail_hidesFavoriteAndHistoryWithoutReadPermissions() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Private State");
        vod.setFlags(List.of(Flag.create("line-a", "第一集$url-a#第二集$url-b")));
        History history = new History();
        history.setVodFlag("line-a");
        history.setVodRemarks("第二集");
        history.setEpisodeUrl("url-b");
        history.setPosition(12_000);
        history.setDuration(60_000);

        JsonObject detail = WebHomeVodContract.detail(site, vod, new WebThemePlaySession(), true, history,
                false, false, true, true, true, WebThemeDetailMetadata.EMPTY);

        JsonObject state = detail.getAsJsonObject("state");
        assertFalse(state.has("favorite"));
        assertFalse(state.has("history"));
        assertTrue(detail.getAsJsonArray("sources").get(0).getAsJsonObject().get("selected").getAsBoolean());
        assertFalse(episodeByName(detail, "第一集").get("selected").getAsBoolean());
        assertFalse(episodeByName(detail, "第二集").get("selected").getAsBoolean());
        assertFalse(detail.getAsJsonObject("capabilities").get("canFavorite").getAsBoolean());
    }

    @Test
    public void detail_rejectsOversizedPlaybackUrlsBeforeIssuingIntentReferences() {
        Vod vod = TestVod.vod("vod-1", "Oversized URL");
        vod.setFlags(List.of(Flag.create("line-a", "第一集$"
                + "x".repeat(WebThemePlaySession.MAX_EPISODE_URL_LENGTH + 1))));

        JsonObject json = WebHomeVodContract.detail(Site.get("current", "Current Source"), vod,
                new WebThemePlaySession(), false, null, true, true);

        assertEquals(0, json.getAsJsonArray("sources").get(0).getAsJsonObject()
                .getAsJsonArray("episodes").size());
        assertTrue(json.get("truncated").getAsBoolean());
        assertFalse(json.getAsJsonObject("capabilities").get("canPlay").getAsBoolean());
    }

    @Test
    public void detail_totalUtf8PayloadStaysBelowTheBridgeLimit() {
        Vod vod = TestVod.vod("vod-1", "Budgeted Detail")
                .details("p".repeat(4096), "r".repeat(1024), "2026", "Drama", "简".repeat(20_000));
        StringBuilder urls = new StringBuilder();
        for (int i = 0; i < WebThemePlaySession.MAX_REFERENCES; i++) {
            if (i > 0) urls.append('#');
            urls.append("第").append(i).append("集$url-").append(i);
        }
        vod.setFlags(List.of(Flag.create("line-a", urls.toString())));
        for (Episode episode : vod.getFlags().get(0).getEpisodes()) {
            episode.setTmdbEpisode(new TmdbEpisode(1, "标".repeat(512), "2026-01-01",
                    "介".repeat(4_000), "https://img.example/" + "x".repeat(4_096),
                    8, 45, 1, 1));
        }

        JsonObject json = WebHomeVodContract.detail(Site.get("current", "Current Source"), vod,
                new WebThemePlaySession(), false, null, true, true);

        assertTrue(json.toString().getBytes(StandardCharsets.UTF_8).length
                <= WebHomeVodContract.MAX_CONTRACT_BYTES);
        assertTrue(json.get("truncated").getAsBoolean());
    }

    @Test
    public void detail_addsBoundedTmdbMediaPeopleArtworkAndRecommendations() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Eclipse Detail")
                .details("https://img.example/poster.jpg", "更新至 1 集", "2026", "剧情", "安全简介");
        vod.setFlags(List.of(Flag.create("line-a", "第 1 集$https://media.example/a1.m3u8")));
        vod.getFlags().get(0).getEpisodes().get(0).setTmdbEpisode(new TmdbEpisode(
                1, "相遇", "2026-01-02", "第一集简介", "https://img.example/still.jpg", 8.4, 46, 100, 1));

        TmdbItem tmdbItem = new TmdbItem(100, "tv", "Eclipse Detail", "2026 · 剧情", "TMDB 简介",
                "https://img.example/tmdb-poster.jpg", "https://img.example/backdrop.jpg", "", 8.6);
        JsonObject tmdbDetail = new Gson().fromJson("""
                {
                  "original_name":"Original Eclipse",
                  "tagline":"光影仍在继续",
                  "first_air_date":"2026-01-02",
                  "last_air_date":"2026-02-20",
                  "status":"Returning Series",
                  "vote_average":8.6,
                  "vote_count":321,
                  "episode_run_time":[46],
                  "number_of_seasons":2,
                  "number_of_episodes":18,
                  "genres":[{"name":"剧情"},{"name":"悬疑"}]
                }
                """, JsonObject.class);
        WebThemeDetailMetadata metadata = WebThemeDetailMetadata.fromTmdb(
                tmdbItem,
                tmdbDetail,
                List.of(new TmdbPerson(1, "演员甲", "角色甲", "https://img.example/cast.jpg", "Acting", "")),
                List.of(new TmdbPerson(2, "导演乙", "导演", "https://img.example/crew.jpg", "Directing", "")),
                List.of("https://img.example/photo-1.jpg", "https://img.example/photo-2.jpg"),
                List.of(new TmdbItem(200, "movie", "推荐影片", "2025 · 电影", "推荐简介",
                        "https://img.example/recommend.jpg", "https://img.example/recommend-backdrop.jpg", "", 7.9)));

        JsonObject json = WebHomeVodContract.detail(site, vod, new WebThemePlaySession(), false, null,
                true, true, true, metadata);

        JsonObject media = json.getAsJsonObject("media");
        assertEquals(100, media.get("tmdbId").getAsInt());
        assertEquals("tv", media.get("mediaType").getAsString());
        assertEquals("Original Eclipse", media.get("originalName").getAsString());
        assertEquals("https://img.example/backdrop.jpg", media.get("backdrop").getAsString());
        assertEquals(8.6, media.get("rating").getAsDouble(), 0.001);
        assertEquals(2, media.get("seasonCount").getAsInt());
        assertEquals(18, media.get("episodeCount").getAsInt());
        assertEquals("剧情", media.getAsJsonArray("genres").get(0).getAsString());

        JsonArray people = json.getAsJsonArray("people");
        assertEquals(2, people.size());
        assertEquals("cast", people.get(0).getAsJsonObject().get("kind").getAsString());
        assertEquals("角色甲", people.get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("crew", people.get(1).getAsJsonObject().get("kind").getAsString());
        assertEquals(2, json.getAsJsonArray("gallery").size());

        JsonObject episode = json.getAsJsonArray("sources").get(0).getAsJsonObject()
                .getAsJsonArray("episodes").get(0).getAsJsonObject();
        assertEquals("相遇", episode.get("title").getAsString());
        assertEquals("https://img.example/still.jpg", episode.get("still").getAsString());
        assertEquals(46, episode.get("runtimeMinutes").getAsInt());

        JsonObject recommendation = json.getAsJsonArray("recommendations").get(0).getAsJsonObject();
        assertEquals(200, recommendation.get("tmdbId").getAsInt());
        assertEquals("推荐影片", recommendation.get("name").getAsString());
        assertEquals("https://img.example/recommend.jpg", recommendation.get("pic").getAsString());
        assertTrue(json.getAsJsonObject("capabilities").get("hasPeople").getAsBoolean());
        assertTrue(json.getAsJsonObject("capabilities").get("hasGallery").getAsBoolean());
        assertTrue(json.getAsJsonObject("capabilities").get("hasRecommendations").getAsBoolean());
        assertTrue(json.getAsJsonObject("capabilities").get("hasEpisodeMetadata").getAsBoolean());
        assertTrue(json.getAsJsonObject("capabilities").get("canSearchRecommendations").getAsBoolean());
        assertFalse(json.toString().contains("media.example"));
    }

    @Test
    public void detail_exposesTmdbActionReferencesPersonalRailsAndExternalLinks() {
        Site site = Site.get("current", "Current Source");
        Vod vod = TestVod.vod("vod-1", "Eclipse Detail")
                .details("https://img.example/poster.jpg", "更新至 1 集", "2026", "剧情", "安全简介");
        vod.setFlags(List.of(Flag.create("line-a", "第 1 集$https://media.example/a1.m3u8")));
        vod.getFlags().get(0).getEpisodes().get(0).setTmdbEpisode(new TmdbEpisode(
                1, "相遇", "2026-01-02", "第一集简介", "https://img.example/still.jpg", 8.4, 46, 100, 1));

        TmdbItem rootItem = new TmdbItem(100, "tv", "Eclipse Detail", "2026 · 剧情", "TMDB 简介",
                "https://img.example/root.jpg", "https://img.example/root-backdrop.jpg", "", 8.6);
        JsonObject detail = new Gson().fromJson("""
                {
                  "first_air_date":"2026-01-02",
                  "external_ids":{"imdb_id":"tt1234567"}
                }
                """, JsonObject.class);
        TmdbPerson person = new TmdbPerson(7, "演员甲", "角色甲", "https://img.example/person.jpg", "Acting", "人物简介");
        TmdbItem related = recommendation(201, "普通推荐", "");
        TmdbItem personalTmdb = recommendation(202, "TMDB 个性推荐", "");
        TmdbItem personalDouban = recommendation(203, "豆瓣个性推荐", "");
        TmdbItem personalAi = recommendation(204, "AI 个性推荐", "因为你最近观看了相似作品");
        WebThemeDetailMetadata metadata = WebThemeDetailMetadata.fromTmdb(
                rootItem, detail, List.of(person), List.of(),
                List.of("https://img.example/photo-1.jpg", "https://img.example/photo-2.jpg"),
                List.of(related), List.of(personalTmdb), List.of(personalDouban), List.of(personalAi));
        WebThemeDetailActionSession actions = new WebThemeDetailActionSession();
        Set<String> permissions = Set.of("person.open", "image.preview", "image.save",
                "recommendation.open", "recommendation.info", "recommendation.feedback", "external.open",
                "episode.info");

        JsonObject json = WebHomeVodContract.detail(site, vod, new WebThemePlaySession(), false, null,
                true, true, true, metadata, actions, permissions);

        JsonObject mappedPerson = json.getAsJsonArray("people").get(0).getAsJsonObject();
        String personRef = mappedPerson.get("personRef").getAsString();
        assertEquals(7, actions.resolvePerson(personRef).getPersonId());

        JsonObject image = json.getAsJsonArray("galleryItems").get(0).getAsJsonObject();
        String imageRef = image.get("imageRef").getAsString();
        assertEquals("https://img.example/photo-1.jpg", actions.resolveImage(imageRef).url());
        assertEquals(2, actions.resolveImage(imageRef).gallery().size());

        JsonObject mappedEpisode = json.getAsJsonArray("sources").get(0).getAsJsonObject()
                .getAsJsonArray("episodes").get(0).getAsJsonObject();
        String episodeRef = mappedEpisode.get("episodeRef").getAsString();
        assertEquals("第 1 集", actions.resolveEpisode(episodeRef).getName());

        JsonArray groups = json.getAsJsonArray("recommendationGroups");
        assertEquals(4, groups.size());
        assertEquals("related", groups.get(0).getAsJsonObject().get("id").getAsString());
        assertEquals("personal.tmdb", groups.get(1).getAsJsonObject().get("id").getAsString());
        assertEquals("personal.douban", groups.get(2).getAsJsonObject().get("id").getAsString());
        JsonObject aiGroup = groups.get(3).getAsJsonObject();
        assertEquals("personal.ai", aiGroup.get("id").getAsString());
        JsonObject aiItem = aiGroup.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals("因为你最近观看了相似作品", aiItem.get("reason").getAsString());
        assertEquals("ai", actions.resolveRecommendation(aiItem.get("recommendationRef").getAsString()).source());

        JsonArray externalLinks = json.getAsJsonArray("externalLinks");
        assertTrue(externalLinks.size() >= 3);
        JsonObject tmdbLink = externalLinks.get(0).getAsJsonObject();
        assertEquals("TMDB", tmdbLink.get("label").getAsString());
        assertEquals("themoviedb.org", tmdbLink.get("host").getAsString());
        assertTrue(actions.resolveExternal(tmdbLink.get("linkRef").getAsString()).url().startsWith("https://"));

        JsonObject capabilities = json.getAsJsonObject("capabilities");
        assertTrue(capabilities.get("canOpenPeople").getAsBoolean());
        assertTrue(capabilities.get("canPreviewImages").getAsBoolean());
        assertTrue(capabilities.get("canSaveImages").getAsBoolean());
        assertTrue(capabilities.get("canOpenRecommendations").getAsBoolean());
        assertTrue(capabilities.get("canInspectRecommendations").getAsBoolean());
        assertTrue(capabilities.get("canSendRecommendationFeedback").getAsBoolean());
        assertTrue(capabilities.get("canOpenExternalLinks").getAsBoolean());
        assertTrue(capabilities.get("canInspectEpisodes").getAsBoolean());
        assertTrue(capabilities.get("hasPersonalTmdbRecommendations").getAsBoolean());
        assertTrue(capabilities.get("hasPersonalDoubanRecommendations").getAsBoolean());
        assertTrue(capabilities.get("hasPersonalAiRecommendations").getAsBoolean());
        assertTrue(capabilities.get("hasExternalLinks").getAsBoolean());
    }

    @Test
    public void detail_actionCapabilitiesRequireIssuedReferences() {
        WebThemeDetailMetadata metadata = WebThemeDetailMetadata.fromTmdb(
                null, null,
                List.of(new TmdbPerson(0, "无编号人物", "角色", "", "Acting", "")),
                List.of(), List.of("file:///sdcard/private.jpg"), List.of());
        WebThemeDetailActionSession actions = new WebThemeDetailActionSession();
        Set<String> permissions = Set.of("person.open", "image.preview", "image.save", "episode.info");

        JsonObject json = WebHomeVodContract.detail(Site.get("current", "Current Source"),
                TestVod.vod("vod-1", "Detail"), new WebThemePlaySession(), false, null,
                true, true, true, metadata, actions, permissions);

        assertFalse(json.getAsJsonArray("people").get(0).getAsJsonObject().has("personRef"));
        assertFalse(json.getAsJsonArray("galleryItems").get(0).getAsJsonObject().has("imageRef"));
        JsonObject capabilities = json.getAsJsonObject("capabilities");
        assertFalse(capabilities.get("canOpenPeople").getAsBoolean());
        assertFalse(capabilities.get("canPreviewImages").getAsBoolean());
        assertFalse(capabilities.get("canSaveImages").getAsBoolean());
        assertFalse(capabilities.get("canInspectEpisodes").getAsBoolean());
    }

    @Test
    public void detail_countsRecommendationGroupPayloadOnlyOnce() {
        String overview = "介".repeat(400);
        String reason = "因".repeat(400);
        WebThemeDetailMetadata metadata = WebThemeDetailMetadata.fromTmdb(
                null, null, List.of(), List.of(), List.of(),
                recommendationPage(1000, overview, reason),
                recommendationPage(2000, overview, reason),
                recommendationPage(3000, overview, reason),
                recommendationPage(4000, overview, reason));

        JsonObject json = WebHomeVodContract.detail(Site.get("current", "Current Source"),
                TestVod.vod("vod-1", "Detail"), new WebThemePlaySession(), false, null,
                true, true, true, metadata);

        assertEquals(4, json.getAsJsonArray("recommendationGroups").size());
        assertFalse(json.get("truncated").getAsBoolean());
        assertTrue(json.toString().getBytes(StandardCharsets.UTF_8).length
                <= WebHomeVodContract.MAX_CONTRACT_BYTES);
    }

    private static List<TmdbItem> recommendationPage(int startId, String overview, String reason) {
        List<TmdbItem> values = new ArrayList<>();
        for (int index = 0; index < WebHomeVodContract.MAX_DETAIL_RECOMMENDATIONS; index++) {
            int id = startId + index;
            values.add(new TmdbItem(id, "movie", "推荐" + id, "2026 · 电影", overview,
                    "https://img.example/" + id + ".jpg", "https://img.example/" + id + "-backdrop.jpg",
                    "主演", 8.2, "zh", "CN", List.of(18), "", 8.2, 7.9, reason));
        }
        return values;
    }

    private static TmdbItem recommendation(int id, String title, String reason) {
        return new TmdbItem(id, "movie", title, "2026 · 电影", title + "简介",
                "https://img.example/" + id + ".jpg", "https://img.example/" + id + "-backdrop.jpg",
                "主演", 8.2, "zh", "CN", List.of(18), "", 8.2, 7.9, reason);
    }

    @Test
    public void detail_skipsNullTmdbEntriesWithoutDroppingLaterResults() {
        TmdbPerson firstPerson = new TmdbPerson(1, "演员甲", "角色甲", "", "Acting", "");
        TmdbPerson secondPerson = new TmdbPerson(2, "演员乙", "角色乙", "", "Acting", "");
        TmdbItem firstRecommendation = new TmdbItem(0, "movie", "推荐甲", "", "", "", "", "", 0);
        TmdbItem secondRecommendation = new TmdbItem(0, "movie", "推荐乙", "", "", "", "", "", 0);
        WebThemeDetailMetadata metadata = WebThemeDetailMetadata.fromTmdb(null, null,
                Arrays.asList(firstPerson, null, secondPerson), List.of(), List.of(),
                Arrays.asList(firstRecommendation, null, secondRecommendation));

        JsonObject json = WebHomeVodContract.detail(Site.get("current", "Current Source"),
                TestVod.vod("vod-1", "Detail"), new WebThemePlaySession(), false, null,
                true, true, true, metadata);

        assertEquals(2, json.getAsJsonArray("people").size());
        assertEquals("演员乙", json.getAsJsonArray("people").get(1).getAsJsonObject().get("name").getAsString());
        assertEquals(2, json.getAsJsonArray("recommendations").size());
        assertEquals("推荐乙", json.getAsJsonArray("recommendations").get(1).getAsJsonObject()
                .get("name").getAsString());
    }

    @Test
    public void home_boundsRemoteCollectionsAndPublicTextFields() throws Exception {
        String longText = "x".repeat(25_000);
        Site site = Site.get(longText, longText);
        Result result = new Result();
        com.fongmi.android.tv.bean.Class type = new Gson().fromJson(
                "{\"type_id\":\"" + longText + "\",\"type_name\":\"" + longText + "\"}",
                com.fongmi.android.tv.bean.Class.class);
        result.setTypes(Collections.nCopies(257, type));

        Filter oversizedFilter = new Gson().fromJson(
                "{\"key\":\"" + longText + "\",\"name\":\"" + longText
                        + "\",\"init\":\"" + longText + "\",\"value\":[]}", Filter.class);
        List<Value> filterValues = new ArrayList<>();
        for (int i = 0; i < 257; i++) filterValues.add(Value.create(longText, longText));
        setField(oversizedFilter, "value", filterValues);
        Filter emptyFilter = new Gson().fromJson("{\"value\":[]}", Filter.class);
        List<Filter> groupFilters = new ArrayList<>();
        groupFilters.add(oversizedFilter);
        groupFilters.addAll(Collections.nCopies(64, emptyFilter));
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        filters.put("group-0-" + longText, groupFilters);
        for (int i = 1; i < 257; i++) filters.put("group-" + i, List.of());
        setField(result, "filters", filters);

        result.setList(List.of(TestVod.vod(longText, longText)
                .details(longText, longText, longText, longText, longText)));

        JsonObject json = WebHomeVodContract.home(site, result, false, true, 4);

        assertEquals(256, json.getAsJsonObject("source").get("key").getAsString().length());
        assertEquals(512, json.getAsJsonObject("source").get("name").getAsString().length());
        assertTrue(json.getAsJsonArray("classes").size() > 0);
        assertTrue(json.getAsJsonArray("classes").size() <= WebHomeVodContract.MAX_REMOTE_CLASSES);
        JsonObject firstClass = json.getAsJsonArray("classes").get(0).getAsJsonObject();
        assertEquals(256, firstClass.get("typeId").getAsString().length());
        assertEquals(512, firstClass.get("typeName").getAsString().length());

        JsonObject boundedFilters = json.getAsJsonObject("filters");
        assertTrue(boundedFilters.size() > 0);
        assertTrue(boundedFilters.size() <= WebHomeVodContract.MAX_REMOTE_FILTER_GROUPS);
        Map.Entry<String, JsonElement> firstGroup = boundedFilters.entrySet().iterator().next();
        assertEquals(256, firstGroup.getKey().length());
        JsonArray mappedFilters = firstGroup.getValue().getAsJsonArray();
        assertTrue(mappedFilters.size() > 0);
        assertTrue(mappedFilters.size() <= WebHomeVodContract.MAX_REMOTE_FILTERS_PER_GROUP);
        JsonObject firstFilter = mappedFilters.get(0).getAsJsonObject();
        assertEquals(64, firstFilter.get("key").getAsString().length());
        assertEquals(256, firstFilter.get("name").getAsString().length());
        assertEquals(512, firstFilter.get("init").getAsString().length());
        assertTrue(firstFilter.getAsJsonArray("values").size() > 0);
        assertTrue(firstFilter.getAsJsonArray("values").size()
                <= WebHomeVodContract.MAX_REMOTE_FILTER_VALUES);
        assertEquals(512, firstFilter.getAsJsonArray("values").get(0).getAsJsonObject()
                .get("name").getAsString().length());
        assertEquals(512, firstFilter.getAsJsonArray("values").get(0).getAsJsonObject()
                .get("value").getAsString().length());

        JsonObject item = json.getAsJsonArray("items").get(0).getAsJsonObject();
        assertEquals(2048, item.get("vodId").getAsString().length());
        assertEquals(512, item.get("name").getAsString().length());
        assertEquals(4096, item.get("pic").getAsString().length());
        assertEquals(1024, item.get("remarks").getAsString().length());
        assertEquals(64, item.get("year").getAsString().length());
        assertEquals(256, item.get("typeName").getAsString().length());
        assertEquals(20_000, item.get("content").getAsString().length());
        assertTrue(json.toString().getBytes(StandardCharsets.UTF_8).length
                <= WebHomeVodContract.MAX_CONTRACT_BYTES);
        assertTrue(json.get("truncated").getAsBoolean());
    }

    @Test
    public void category_boundsRemoteQueryEcho() {
        String longText = "x".repeat(2_000);
        LinkedHashMap<String, String> extend = new LinkedHashMap<>();
        for (int i = 0; i < 33; i++) extend.put("key-" + i + "-" + longText, longText);

        JsonObject query = WebHomeVodContract.category(Site.get("current", "Current Source"),
                longText, 1, true, extend, new Result(), false, true, 4).getAsJsonObject("query");

        assertEquals(256, query.get("typeId").getAsString().length());
        JsonObject mappedExtend = query.getAsJsonObject("extend");
        assertEquals(32, mappedExtend.size());
        Map.Entry<String, JsonElement> first = mappedExtend.entrySet().iterator().next();
        assertEquals(64, first.getKey().length());
        assertEquals(512, first.getValue().getAsString().length());
    }

    @Test
    public void home_normalizesNullFilterGroupsAndInvalidStyles() throws Exception {
        Result result = new Result();
        LinkedHashMap<String, List<Filter>> filters = new LinkedHashMap<>();
        filters.put("null-group", null);
        setField(result, "filters", filters);
        result.setList(List.of(TestVod.vod("vod-1", "Name")
                .style(new Style("x".repeat(100), Float.NaN))));

        JsonObject json = WebHomeVodContract.home(Site.get("demo", "Demo"), result,
                false, true, 4);

        assertEquals(0, json.getAsJsonObject("filters").getAsJsonArray("null-group").size());
        JsonObject style = json.getAsJsonArray("items").get(0).getAsJsonObject()
                .getAsJsonObject("style");
        assertEquals(32, style.get("type").getAsString().length());
        assertTrue(Float.isFinite(style.get("ratio").getAsFloat()));
        assertEquals(Style.rect().getRatio(), style.get("ratio").getAsFloat(), 0.001f);
    }

    @Test
    public void home_stopsInspectingInvalidItemsAndKeepsThePayloadWithinBudget() throws Exception {
        AtomicInteger inspected = new AtomicInteger();
        List<Vod> hostile = new AbstractList<>() {
            @Override
            public Vod get(int index) {
                if (index >= WebHomeVodContract.MAX_REMOTE_ITEMS) {
                    throw new AssertionError("Mapper scanned beyond its inspection budget");
                }
                inspected.accumulateAndGet(index + 1, Math::max);
                return index == 0 ? TestVod.vod("first", "First") : null;
            }

            @Override
            public int size() {
                return 1_000_000;
            }
        };
        Result invalid = new Result();
        invalid.setList(hostile);

        JsonObject invalidJson = WebHomeVodContract.home(Site.get("demo", "Demo"), invalid,
                false, true, 4);
        assertEquals(WebHomeVodContract.MAX_REMOTE_ITEMS, inspected.get());
        assertTrue(invalidJson.get("truncated").getAsBoolean());

        Result oversized = new Result();
        oversized.setList(Collections.nCopies(WebHomeVodContract.MAX_REMOTE_ITEMS,
                TestVod.vod("v".repeat(2048), "n".repeat(512))
                        .details("p".repeat(4096), "r".repeat(1024), "2026", "type",
                                "内".repeat(20_000))));
        JsonObject oversizedJson = WebHomeVodContract.home(Site.get("demo", "Demo"), oversized,
                false, true, 4);

        assertTrue(oversizedJson.toString().getBytes(StandardCharsets.UTF_8).length
                <= WebHomeVodContract.MAX_CONTRACT_BYTES);
        assertTrue(oversizedJson.get("truncated").getAsBoolean());
    }

    private static JsonObject episodeByName(JsonObject detail, String name) {
        for (JsonElement source : detail.getAsJsonArray("sources")) {
            for (JsonElement episode : source.getAsJsonObject().getAsJsonArray("episodes")) {
                JsonObject object = episode.getAsJsonObject();
                if (name.equals(object.get("name").getAsString())) return object;
            }
        }
        throw new AssertionError("Episode not found: " + name);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class TestVod extends Vod {
        private final String id;
        private final String name;
        private String pic = "";
        private String remarks = "";
        private String year = "";
        private String typeName = "";
        private String content = "";
        private String action = "";
        private boolean folder;
        private Style style = Style.rect();

        private TestVod(String id, String name) {
            this.id = id;
            this.name = name;
        }

        static TestVod vod(String id, String name) {
            return new TestVod(id, name);
        }

        static TestVod folder(String id, String name) {
            TestVod vod = new TestVod(id, name);
            vod.folder = true;
            return vod;
        }

        static TestVod action(String id, String name, String action) {
            TestVod vod = new TestVod(id, name);
            vod.action = action;
            return vod;
        }

        TestVod details(String pic, String remarks, String year, String typeName, String content) {
            this.pic = pic;
            this.remarks = remarks;
            this.year = year;
            this.typeName = typeName;
            this.content = content;
            return this;
        }

        TestVod style(Style style) {
            this.style = style;
            return this;
        }

        @Override public String getId() { return id; }
        @Override public String getName() { return name; }
        @Override public String getPic() { return pic; }
        @Override public String getRemarks() { return remarks; }
        @Override public String getYear() { return year; }
        @Override public String getTypeName() { return typeName; }
        @Override public String getArea() { return ""; }
        @Override public String getDirector() { return ""; }
        @Override public String getActor() { return ""; }
        @Override public String getContent() { return content; }
        @Override public String getAction() { return action; }
        @Override public boolean isAction() { return !action.isEmpty(); }
        @Override public boolean isFolder() { return folder; }
        @Override public Style getStyle(Style defaultStyle) { return style == null ? defaultStyle : style; }
    }
}
