package com.fongmi.android.tv.subtitle.source;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.fongmi.android.tv.subtitle.model.SubtitleQuerySource;
import com.fongmi.android.tv.subtitle.model.SubtitleStrictness;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.List;

public class SubtitleProtocolMapperTest {

    private final SubtitleSourceConfig source = new Gson().fromJson("{\"key\":\"test_py\",\"type\":3,\"api\":\"py\",\"ext\":\"test.py\"}", SubtitleSourceConfig.class);
    private final SubtitleQuery query = new SubtitleQuery("Movie", "movie", "zh", SubtitleQuerySource.SOURCE_TITLE, SubtitleStrictness.NORMAL, 2024, 1, 2);

    @Test
    public void createsSearchRequestWithSourceQueryAndContext() {
        JsonObject request = new Gson().fromJson(SubtitleProtocolMapper.createSearchRequest(source, query,
                SubtitleContext.builder().canonicalTitle("Movie").mediaPath("/cache/movie.mkv").year(2024).seasonNumber(1).episodeNumber(2).build()), JsonObject.class);
        assertEquals("search", request.get("action").getAsString());
        assertEquals("test_py", request.getAsJsonObject("source").get("key").getAsString());
        assertEquals("movie", request.getAsJsonObject("query").get("text").getAsString());
        assertEquals(2024, request.getAsJsonObject("context").get("year").getAsInt());
        assertEquals("/cache/movie.mkv", request.getAsJsonObject("context").get("mediaPath").getAsString());
    }

    @Test
    public void mapsCandidatesAndResolveAsset() {
        List<SubtitleCandidate> candidates = SubtitleProtocolMapper.parseCandidates(source, query,
                "{\"code\":0,\"data\":{\"items\":[{\"id\":\"42\",\"name\":\"Movie.ass\",\"language\":\"zh\",\"format\":\"ass\",\"score\":90,\"requiresResolve\":true,\"payload\":{\"fileId\":42}}]}}");
        assertEquals(1, candidates.size());
        assertEquals("test_py", candidates.get(0).getProvider());
        assertEquals("42", candidates.get(0).getCandidateId());
        assertTrue(candidates.get(0).isRequiresResolve());
        SubtitleAsset asset = SubtitleProtocolMapper.parseAsset(source, candidates.get(0),
                "{\"code\":0,\"data\":{\"url\":\"https://example.com/42.ass\",\"fileName\":\"Movie.ass\",\"language\":\"zh\",\"format\":\"ass\"}}");
        assertEquals("https://example.com/42.ass", asset.getUri());
        assertEquals("Movie.ass", asset.getDisplayName());
    }

    @Test
    public void rejectsProtocolErrors() {
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> SubtitleProtocolMapper.requireSuccess("{\"code\":7,\"message\":\"denied\"}"));
        assertEquals("denied", error.getMessage());
    }
}
