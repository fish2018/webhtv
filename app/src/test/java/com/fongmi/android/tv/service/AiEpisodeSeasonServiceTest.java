package com.fongmi.android.tv.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiEpisodeSeasonServiceTest {

    @Test
    public void parseResponse_acceptsFencedFlatMappingWhenSourceIsContinuous() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.FLAT_BY_COUNTS);
        response.addProperty("confidence", 1.5);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                "\u0060\u0060\u0060json\n" + response + "\n\u0060\u0060\u0060", episodes(1, 2, 3, 4), seasonCounts());

        assertTrue(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.Mode.FLAT_BY_COUNTS, result.getMode());
        assertEquals(1.0, result.getConfidence(), 0.0);
    }

    @Test
    public void parseResponse_acceptsFlatMappingWithGapsDuplicatesAndUnknownNumbers() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.FLAT_BY_COUNTS);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                response.toString(), episodes(4, 1, 2, 2, -1, 5), seasonCounts());

        assertTrue(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.Mode.FLAT_BY_COUNTS, result.getMode());
    }

    @Test
    public void parseResponse_rejectsFlatMappingWhenNoSourceNumberCanBeMapped() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.FLAT_BY_COUNTS);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                response.toString(), episodes(0, -1, 5), seasonCounts());

        assertFalse(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.UNSAFE_FLAT_MAPPING, result.getReason());
    }

    @Test
    public void parseResponse_acceptsCompleteExplicitMappingAndSortsBySourceIndex() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING);
        JsonArray mappings = new JsonArray();
        mappings.add(mapping(2, 2, 1));
        mappings.add(mapping(0, 1, 1));
        mappings.add(mapping(3, 2, 2));
        mappings.add(mapping(1, 1, 2));
        response.add("mappings", mappings);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                response.toString(), episodes(101, 102, 201, 202), seasonCounts());

        assertTrue(result.isSuccess());
        assertEquals(4, result.getMappings().size());
        assertEquals(0, result.getMappings().get(0).sourceIndex());
        assertEquals(2, result.getMappings().get(2).seasonNumber());
        assertEquals(1, result.getMappings().get(2).episodeNumber());
    }

    @Test
    public void parseResponse_rejectsIncompleteOrDuplicateExplicitMapping() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING);
        JsonArray mappings = new JsonArray();
        mappings.add(mapping(0, 1, 1));
        mappings.add(mapping(1, 1, 1));
        response.add("mappings", mappings);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                response.toString(), episodes(1, 2), seasonCounts());

        assertFalse(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.INVALID_MAPPING, result.getReason());
    }

    @Test
    public void parseResponse_rejectsSingleSeasonWhenSourceExceedsTmdbSeasonCount() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.SINGLE_SEASON);
        response.addProperty("seasonNumber", 1);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                response.toString(), episodes(1, 2, 3), seasonCounts());

        assertFalse(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.INVALID_SEASON, result.getReason());
    }

    @Test
    public void parseResponse_rejectsUnknownSingleSeason() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.SINGLE_SEASON);
        response.addProperty("seasonNumber", 9);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                response.toString(), episodes(1, 2), seasonCounts());

        assertFalse(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.INVALID_SEASON, result.getReason());
    }

    @Test
    public void parseResponse_rejectsTruncatedSourceForSeasonOrFlatMapping() {
        JsonObject singleSeason = response(AiEpisodeSeasonService.Mode.SINGLE_SEASON);
        singleSeason.addProperty("seasonNumber", 1);

        AiEpisodeSeasonService.AnalysisResult singleResult = AiEpisodeSeasonService.parseResponse(
                singleSeason.toString(), episodes(1, 2), seasonCounts(), 3);
        assertFalse(singleResult.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.INVALID_SEASON, singleResult.getReason());

        JsonObject flat = response(AiEpisodeSeasonService.Mode.FLAT_BY_COUNTS);
        AiEpisodeSeasonService.AnalysisResult flatResult = AiEpisodeSeasonService.parseResponse(
                flat.toString(), episodes(1, 2), seasonCounts(), 3);
        assertFalse(flatResult.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.UNSAFE_FLAT_MAPPING, flatResult.getReason());
    }

    @Test
    public void parseResponse_acceptsSafeModesForTruncatedSamplesWhenFullNumbersAreValidated() {
        JsonObject singleSeason = response(AiEpisodeSeasonService.Mode.SINGLE_SEASON);
        singleSeason.addProperty("seasonNumber", 1);
        Map<Integer, Integer> largeSeasonCounts = new LinkedHashMap<>();
        largeSeasonCounts.put(1, 4);
        largeSeasonCounts.put(2, 2);

        AiEpisodeSeasonService.AnalysisResult singleResult = AiEpisodeSeasonService.parseResponse(
                singleSeason.toString(), episodes(1, 4), largeSeasonCounts, 4, List.of(1, 2, 3, 4));
        assertTrue(singleResult.isSuccess());

        JsonObject flat = response(AiEpisodeSeasonService.Mode.FLAT_BY_COUNTS);
        AiEpisodeSeasonService.AnalysisResult flatResult = AiEpisodeSeasonService.parseResponse(
                flat.toString(), episodes(1, 4), seasonCounts(), 4, List.of(1, 2, 3, 4));
        assertTrue(flatResult.isSuccess());
    }

    @Test
    public void parseResponse_rejectsExplicitMappingForTruncatedSamples() {
        JsonObject explicit = response(AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING);
        JsonArray mappings = new JsonArray();
        mappings.add(mapping(0, 1, 1));
        mappings.add(mapping(1, 1, 2));
        explicit.add("mappings", mappings);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                explicit.toString(), episodes(1, 4), seasonCounts(), 4, List.of(1, 2, 3, 4));

        assertFalse(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.INVALID_MAPPING, result.getReason());
    }

    @Test
    public void buildPrompt_declaresTruncatedSourceAndForbidsLargeExplicitMapping() {
        String prompt = AiEpisodeSeasonService.buildPrompt(
                "航海王", "线路", episodes(1, 468), seasonCounts(), 468,
                java.util.stream.IntStream.rangeClosed(1, 468).boxed().toList());

        assertTrue(prompt.contains("\"sourceEpisodeCount\":468"));
        assertTrue(prompt.contains("\"sampledEpisodeCount\":2"));
        assertTrue(prompt.contains("\"sourceTruncated\":true"));
        assertTrue(prompt.contains("截断时禁止 EXPLICIT_MAPPING"));
    }

    @Test
    public void parseResponse_rejectsExplicitMappingWithoutSourceSamples() {
        JsonObject explicit = response(AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                explicit.toString(), null, seasonCounts(), 0);

        assertFalse(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.INVALID_MAPPING, result.getReason());
    }

    @Test
    public void parseResponse_rejectsExplicitMappingWithExtraItems() {
        JsonObject response = response(AiEpisodeSeasonService.Mode.EXPLICIT_MAPPING);
        JsonArray mappings = new JsonArray();
        mappings.add(mapping(0, 1, 1));
        mappings.add(mapping(1, 1, 2));
        mappings.add(mapping(2, 2, 1));
        response.add("mappings", mappings);

        AiEpisodeSeasonService.AnalysisResult result = AiEpisodeSeasonService.parseResponse(
                response.toString(), episodes(1, 2), seasonCounts());

        assertFalse(result.isSuccess());
        assertEquals(AiEpisodeSeasonService.FailureReason.INVALID_MAPPING, result.getReason());
    }

    private static JsonObject response(AiEpisodeSeasonService.Mode mode) {
        JsonObject object = new JsonObject();
        object.addProperty("mode", mode.name());
        object.addProperty("confidence", 0.9);
        object.addProperty("summary", "validated suggestion");
        object.add("warnings", new JsonArray());
        return object;
    }

    private static JsonObject mapping(int sourceIndex, int seasonNumber, int episodeNumber) {
        JsonObject object = new JsonObject();
        object.addProperty("sourceIndex", sourceIndex);
        object.addProperty("seasonNumber", seasonNumber);
        object.addProperty("episodeNumber", episodeNumber);
        return object;
    }

    private static List<AiEpisodeSeasonService.EpisodeSample> episodes(int... numbers) {
        List<AiEpisodeSeasonService.EpisodeSample> result = new ArrayList<>();
        for (int index = 0; index < numbers.length; index++) {
            result.add(new AiEpisodeSeasonService.EpisodeSample(index, numbers[index], "episode-" + numbers[index]));
        }
        return result;
    }

    private static Map<Integer, Integer> seasonCounts() {
        Map<Integer, Integer> result = new LinkedHashMap<>();
        result.put(1, 2);
        result.put(2, 2);
        return result;
    }
}
