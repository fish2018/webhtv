package com.fongmi.android.tv.ui.helper;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbSourcePayload;
import com.google.gson.Gson;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TmdbSourceCapabilityPlannerTest {

    private static final Gson GSON = new Gson();

    @Test
    public void completeInitialCapabilitiesNeedNoNetwork() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie","complete":["core","credits","images"],
                 "detail":{"id":550,"title":"Movie","overview":"Overview","release_date":"1999","vote_average":8.4,"genres":[],
                   "poster_path":"/p.jpg","backdrop_path":"/b.jpg","credits":{"cast":[]},"images":{"posters":[]}}}
                """);

        TmdbSourceCapabilityPlanner.Plan plan = TmdbSourceCapabilityPlanner.plan(null, payload, TmdbSourceCapabilityPlanner.UiState.initialScreen());

        assertEquals(3, plan.required().size());
        assertTrue(plan.available().containsAll(plan.required()));
        assertFalse(plan.needsNetwork());
    }

    @Test
    public void partialInitialCapabilitiesExposeOnlyMissingGroups() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie","complete":["core"],
                 "detail":{"id":550,"title":"Movie","overview":"Overview","release_date":"1999","vote_average":8.4,"genres":[]}}
                """);

        TmdbSourceCapabilityPlanner.Plan plan = TmdbSourceCapabilityPlanner.plan(null, payload, TmdbSourceCapabilityPlanner.UiState.initialScreen());

        assertEquals(3, plan.required().size());
        assertEquals(2, plan.missing().size());
        assertTrue(plan.missing().contains("credits"));
        assertTrue(plan.missing().contains("images"));
        assertTrue(plan.hasInitialNetworkGaps());
    }

    @Test
    public void seasonAndEpisodeCapabilitiesAreIsolated() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":1399,"media_type":"tv","season_number":2,"complete":["core","season:2"],
                 "detail":{"id":1399,"season_number":2,"name":"Show","overview":"Overview","first_air_date":"2024","vote_average":8,
                   "number_of_seasons":2,"number_of_episodes":1,"genres":[],"seasons":[{"season_number":2,"episodes":[{"episode_number":1}]}]}}
                """);

        TmdbBundle bundle = TmdbSourceAdapter.toBundle(payload, null, new TmdbConfig());
        TmdbSourceCapabilityPlanner.Plan season1 = TmdbSourceCapabilityPlanner.plan(bundle, payload, TmdbSourceCapabilityPlanner.UiState.initialScreen().withSeason(1));
        TmdbSourceCapabilityPlanner.Plan season2 = TmdbSourceCapabilityPlanner.plan(bundle, payload, TmdbSourceCapabilityPlanner.UiState.initialScreen().withEpisode(2, 1));

        assertTrue(season1.missing().contains("season:1"));
        assertFalse(season2.missing().contains("season:2"));
        assertFalse(season2.missing().contains("episode:2:1"));
    }

    @Test
    public void incompatibleLanguageMarksCompleteCoreMissing() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie","language":"en-US","complete":["core"],
                 "detail":{"id":550,"title":"Movie","overview":"Overview","release_date":"1999","vote_average":8.4,"genres":[]}}
                """);

        TmdbBundle bundle = TmdbSourceAdapter.toBundle(payload, null, new TmdbConfig());
        TmdbSourceCapabilityPlanner.Plan plan = TmdbSourceCapabilityPlanner.plan(bundle, payload,
                TmdbSourceCapabilityPlanner.UiState.initialScreen(), "zh-CN");

        assertTrue(plan.missing().contains(TmdbSourceCapabilityPlanner.CORE));
    }

    @Test
    public void emptyCompleteRecommendationsDoNotRequestNetwork() {
        TmdbSourcePayload payload = payload("""
                {"schema":1,"id":550,"media_type":"movie","complete":["recommendations"],
                 "detail":{"id":550,"recommendations":{"page":1,"results":[]}}}
                """);

        assertTrue(TmdbSourceCapabilityPlanner.isAvailable(null, payload, "recommendations", -1, -1));
    }

    private static TmdbSourcePayload payload(String json) {
        return GSON.fromJson(json, TmdbSourcePayload.class);
    }
}
