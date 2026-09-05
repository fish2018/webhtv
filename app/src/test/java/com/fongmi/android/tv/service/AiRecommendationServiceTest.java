package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbItem;
import com.google.gson.JsonObject;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AiRecommendationServiceTest {

    @Test
    public void extractOutputText_readsResponsesConvenienceField() {
        String body = "{\"output_text\":\"{\\\"items\\\":[{\\\"title\\\":\\\"想见你\\\"}]}\"}";

        assertEquals("{\"items\":[{\"title\":\"想见你\"}]}", AiCompletionClient.extractOutputText(body));
    }

    @Test
    public void extractOutputText_readsNestedResponsesContentText() {
        String body = "{\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"{\\\"items\\\":[{\\\"title\\\":\\\"黑暗荣耀\\\"}]}\"}]}]}";

        assertEquals("{\"items\":[{\"title\":\"黑暗荣耀\"}]}", AiCompletionClient.extractOutputText(body));
    }

    @Test
    public void extractOutputText_readsLooselyNestedResponsesText() {
        String body = "{\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"text\",\"text\":{\"value\":\"{\\\"items\\\":[{\\\"title\\\":\\\"边水往事\\\"}]}\"}}]}]}";

        assertEquals("{\"items\":[{\"title\":\"边水往事\"}]}", AiCompletionClient.extractOutputText(body));
    }

    @Test
    public void buildPrompt_includesRecommendationRangeAndStructuredViewingSignals() {
        JsonObject current = new JsonObject();
        current.addProperty("title", "南部档案");
        current.addProperty("year", 2024);
        current.addProperty("mediaType", "tv");
        current.addProperty("country", "中国大陆");
        JsonObject history = new JsonObject();
        history.addProperty("title", "爱情有烟火");
        history.addProperty("episodeName", "第08集");
        history.addProperty("watchedMinutes", 360);
        history.addProperty("completionRate", 0.82);
        history.addProperty("director", "张开宙");
        List<JsonObject> histories = new ArrayList<>();
        histories.add(history);

        String prompt = AiRecommendationService.buildPrompt(AiConfig.objectFrom("{}"), current, histories, List.of("莫离", "教父"));

        assertTrue(prompt.contains("12-24"));
        assertTrue(prompt.contains("\"currentItem\""));
        assertTrue(prompt.contains("\"playHistory\""));
        assertTrue(prompt.contains("\"watchedMinutes\":360"));
        assertTrue(prompt.contains("\"completionRate\":0.82"));
        assertTrue(prompt.contains("\"query\":\"教父\""));
        assertTrue(prompt.contains("字段说明"));
        assertTrue(prompt.contains("completionRate: 单集观看完成比例"));
        assertTrue(prompt.contains("searchHistory: 用户搜索词"));
        assertTrue(prompt.contains("actors/director: 演员与导演/主创"));
        assertTrue(prompt.contains("重复出现在多条高完成率历史"));
        assertTrue(prompt.contains("单条历史中的人员或导演只作为弱信号"));
        assertTrue(prompt.contains("最终只返回严格 JSON"));
    }

    @Test
    public void historyContextItem_includesEnrichedPreferenceSignalsButOmitsPlaybackSource() {
        History history = new History();
        history.setVodName("爱情有烟火");
        history.setVodRemarks("第08集");
        history.setVodFlag("极速线路");
        history.setTypeName("剧情,都市,电视剧");
        history.setArea("中国大陆");
        history.setActor("檀健次,王楚然");
        history.setDirector("张开宙");
        history.setYear("2025");
        history.setPosition(30 * 60 * 1000L);
        history.setDuration(45 * 60 * 1000L);

        JsonObject item = AiRecommendationService.historyContextItem(history);

        assertEquals("爱情有烟火", item.get("title").getAsString());
        assertEquals("2025", item.get("year").getAsString());
        assertEquals("中国大陆", item.get("country").getAsString());
        assertEquals("中文", item.get("language").getAsString());
        assertEquals("tv", item.get("mediaType").getAsString());
        assertEquals("张开宙", item.get("director").getAsString());
        assertEquals("剧情", item.getAsJsonArray("genres").get(0).getAsString());
        assertEquals("檀健次", item.getAsJsonArray("actors").get(0).getAsString());
        assertFalse(item.has("source"));
    }

    @Test
    public void historyContextItem_omitsUnknownEnrichedFields() {
        History history = new History();
        history.setVodName("只有标题");

        JsonObject item = AiRecommendationService.historyContextItem(history);

        assertFalse(item.has("year"));
        assertFalse(item.has("mediaType"));
        assertFalse(item.has("country"));
        assertFalse(item.has("language"));
        assertFalse(item.has("genres"));
        assertFalse(item.has("director"));
        assertFalse(item.has("actors"));
    }

    @Test
    public void historyContextItem_infersTvFromEpisodeLabelButNotResolutionText() {
        History episode = new History();
        episode.setVodName("示例剧集");
        episode.setTypeName("剧情");
        episode.setVodRemarks("第01集");

        JsonObject episodeItem = AiRecommendationService.historyContextItem(episode);

        assertEquals("tv", episodeItem.get("mediaType").getAsString());

        History resolution = new History();
        resolution.setVodName("示例电影");
        resolution.setTypeName("剧情");
        resolution.setVodRemarks("1080P");

        assertFalse(AiRecommendationService.historyContextItem(resolution).has("mediaType"));
    }

    @Test
    public void historyContextItem_preservesSpacesInsideActorNames() {
        History history = new History();
        history.setVodName("复仇者联盟");
        history.setActor("Robert Downey Jr. / Chris Evans");

        JsonObject item = AiRecommendationService.historyContextItem(history);

        assertEquals(2, item.getAsJsonArray("actors").size());
        assertEquals("Robert Downey Jr.", item.getAsJsonArray("actors").get(0).getAsString());
        assertEquals("Chris Evans", item.getAsJsonArray("actors").get(1).getAsString());
    }

    @Test
    public void historyMetadataFingerprint_filtersCurrentTitleBeforeApplyingLimit() {
        List<History> histories = new ArrayList<>();
        History current = new History();
        current.setVodName("当前作品");
        current.setDirector("当前导演");
        histories.add(current);
        for (int index = 1; index <= 24; index++) {
            History history = new History();
            history.setVodName("历史作品" + index);
            history.setDirector("导演" + index);
            histories.add(history);
        }

        String initial = AiRecommendationService.historyMetadataFingerprint(histories, "当前作品");
        histories.get(24).setDirector("变更后的导演");
        String lastHistoryChanged = AiRecommendationService.historyMetadataFingerprint(histories, "当前作品");
        assertFalse(initial.equals(lastHistoryChanged));

        histories.get(0).setDirector("不应进入历史指纹的导演");
        assertEquals(lastHistoryChanged, AiRecommendationService.historyMetadataFingerprint(histories, "当前作品"));
    }

    @Test
    public void historyMetadataFingerprint_tracksDerivedMediaTypeWithoutTrackingEpisodeNumber() {
        History history = new History();
        history.setVodName("示例作品");
        history.setTypeName("剧情");
        history.setVodRemarks("1080P");
        List<History> histories = List.of(history);

        String unknownType = AiRecommendationService.historyMetadataFingerprint(histories);
        history.setVodRemarks("第01集");
        String tvType = AiRecommendationService.historyMetadataFingerprint(histories);
        assertFalse(unknownType.equals(tvType));

        history.setVodRemarks("第02集");
        assertEquals(tvType, AiRecommendationService.historyMetadataFingerprint(histories));
    }

    @Test
    public void historyMetadataFingerprint_changesForMetadataButNotPlaybackProgress() {
        History history = new History();
        history.setVodName("爱情有烟火");
        history.setTypeName("剧情,都市,电视剧");
        history.setArea("中国大陆");
        history.setActor("檀健次,王楚然");
        history.setDirector("张开宙");
        history.setYear("2025");
        List<History> histories = List.of(history);

        String initial = AiRecommendationService.historyMetadataFingerprint(histories);
        history.setPosition(30 * 60 * 1000L);
        history.setDuration(45 * 60 * 1000L);
        assertEquals(initial, AiRecommendationService.historyMetadataFingerprint(histories));

        history.setDirector("张开宙,另一导演");
        assertFalse(initial.equals(AiRecommendationService.historyMetadataFingerprint(histories)));
    }

    @Test
    public void selectHistoryContext_ordersByRecencyBeforeApplyingLimit() {
        List<History> histories = new ArrayList<>();
        for (int index = 1; index <= 25; index++) {
            History history = new History();
            history.setVodName("历史作品" + index);
            history.setPosition((26L - index) * 60_000L);
            history.setCreateTime(index);
            histories.add(history);
        }

        List<History> selected = AiRecommendationService.selectHistoryContext(histories, "");

        assertEquals(24, selected.size());
        assertEquals("历史作品25", selected.get(0).getVodName());
        assertEquals("历史作品2", selected.get(23).getVodName());
    }

    @Test
    public void recommendationFingerprint_includesNormalizedSeedsAndFeedbackState() {
        List<String> seeds = List.of(" 想见你 ", "想见你", "大明王朝1566");

        assertEquals(
                "想见你|大明王朝1566|feedback:blocked-v1",
                PersonalRecommendationService.recommendationFingerprint(seeds, "blocked-v1"));
    }

    @Test
    public void buildPrompt_includesNotInterestedContextAndHardConstraint() {
        JsonObject disliked = new JsonObject();
        disliked.addProperty("title", "不喜欢的作品");
        disliked.addProperty("mediaType", "tv");

        String prompt = AiRecommendationService.buildPrompt(
                AiConfig.objectFrom("{}"), new JsonObject(), new ArrayList<>(), new ArrayList<>(), List.of(disliked));

        assertTrue(prompt.contains("\"notInterested\""));
        assertTrue(prompt.contains("不喜欢的作品"));
        assertTrue(prompt.contains("明确标记为不感兴趣"));
    }

    @Test
    public void requestSpec_buildsOpenAiChatRequestFromBaseEndpoint() {
        AiConfig config = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_chat\",\"endpoint\":\"https://api.example.com/v1\",\"apiKey\":\"sk-test\",\"model\":\"gpt-test\",\"customUserAgent\":\"claude-cli/2.1.161\"}");

        AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, "hello");

        assertEquals("https://api.example.com/v1/chat/completions", spec.getUrl());
        assertEquals("Bearer sk-test", spec.getHeaders().get("Authorization"));
        assertEquals("claude-cli/2.1.161", spec.getHeaders().get("User-Agent"));
        assertEquals("gpt-test", spec.getBody().get("model").getAsString());
        assertEquals("user", spec.getBody().getAsJsonArray("messages").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("hello", spec.getBody().getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void requestSpec_capsOpenAiResponsesOutput() {
        AiConfig config = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.example.com/v1\",\"apiKey\":\"sk-test\",\"model\":\"gpt-test\"}");

        AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, "hello");

        assertEquals(4096, spec.getBody().get("max_output_tokens").getAsInt());
    }

    @Test
    public void extractCompletionText_readsOpenAiChatChoiceMessage() {
        AiConfig config = AiConfig.objectFrom("{\"protocol\":\"openai_chat\"}");
        String body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{\\\"items\\\":[{\\\"title\\\":\\\"繁花\\\"}]}\"}}]}";

        assertEquals("{\"items\":[{\"title\":\"繁花\"}]}", AiCompletionClient.extractCompletionText(body, config));
    }

    @Test
    public void requestSpec_buildsAnthropicMessagesRequest() {
        AiConfig config = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"anthropic_messages\",\"endpoint\":\"https://api.anthropic.com/v1\",\"apiKey\":\"sk-ant\",\"model\":\"claude-test\"}");

        AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, "hello");

        assertEquals("https://api.anthropic.com/v1/messages", spec.getUrl());
        assertEquals("sk-ant", spec.getHeaders().get("x-api-key"));
        assertEquals("2023-06-01", spec.getHeaders().get("anthropic-version"));
        assertEquals("claude-test", spec.getBody().get("model").getAsString());
        assertEquals(4096, spec.getBody().get("max_tokens").getAsInt());
        assertEquals("hello", spec.getBody().getAsJsonArray("messages").get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    public void extractCompletionText_readsAnthropicContentText() {
        AiConfig config = AiConfig.objectFrom("{\"protocol\":\"anthropic_messages\"}");
        String body = "{\"content\":[{\"type\":\"text\",\"text\":\"{\\\"items\\\":[{\\\"title\\\":\\\"三体\\\"}]}\"}]}";

        assertEquals("{\"items\":[{\"title\":\"三体\"}]}", AiCompletionClient.extractCompletionText(body, config));
    }

    @Test
    public void requestSpec_buildsGeminiNativeRequest() {
        AiConfig config = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"gemini_native\",\"endpoint\":\"https://generativelanguage.googleapis.com/v1beta\",\"apiKey\":\"gm-key\",\"model\":\"gemini-2.5-flash\"}");

        AiCompletionClient.RequestSpec spec = AiCompletionClient.requestSpec(config, "hello");

        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent", spec.getUrl());
        assertEquals("gm-key", spec.getHeaders().get("x-goog-api-key"));
        assertEquals("user", spec.getBody().getAsJsonArray("contents").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals("hello", spec.getBody().getAsJsonArray("contents").get(0).getAsJsonObject().getAsJsonArray("parts").get(0).getAsJsonObject().get("text").getAsString());
    }

    @Test
    public void extractCompletionText_readsGeminiPartsText() {
        AiConfig config = AiConfig.objectFrom("{\"protocol\":\"gemini_native\"}");
        String body = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"items\\\":[{\\\"title\\\":\\\"沙丘\\\"}]}\"}]}}]}";

        assertEquals("{\"items\":[{\"title\":\"沙丘\"}]}", AiCompletionClient.extractCompletionText(body, config));
    }

    @Test
    public void parseRecommendations_acceptsObjectArrayAndFencedJson() {
        String text = "```json\n{\"items\":[{\"title\":\"漫长的季节\",\"year\":2023,\"mediaType\":\"tv\",\"reason\":\"悬疑气质相近\"}]}\n```";

        List<AiRecommendationService.AiRecommendation> items = AiRecommendationService.parseRecommendations(text);

        assertEquals(1, items.size());
        assertEquals("漫长的季节", items.get(0).title);
        assertEquals(2023, items.get(0).year);
        assertEquals("tv", items.get(0).mediaType);
    }

    @Test
    public void parseRecommendations_acceptsCommonAlternativeArrayKeys() {
        String text = "{\"results\":[{\"title\":\"我的阿勒泰\",\"mediaType\":\"tv\"}]}";

        List<AiRecommendationService.AiRecommendation> items = AiRecommendationService.parseRecommendations(text);

        assertEquals(1, items.size());
        assertEquals("我的阿勒泰", items.get(0).title);
    }

    @Test
    public void parseResponseRecommendations_acceptsRawJsonBodyWhenGatewaySkipsEnvelope() {
        AiConfig config = AiConfig.objectFrom("{\"protocol\":\"openai_responses\"}");
        String body = "{\"items\":[{\"title\":\"去有风的地方\",\"year\":2023,\"mediaType\":\"tv\",\"reason\":\"治愈向\"}]}";

        List<AiRecommendationService.AiRecommendation> items = AiRecommendationService.parseResponseRecommendations(body, config);

        assertEquals(1, items.size());
        assertEquals("去有风的地方", items.get(0).title);
    }

    @Test
    public void parseResponseRecommendations_acceptsRawTextWithEmbeddedJson() {
        AiConfig config = AiConfig.objectFrom("{\"protocol\":\"openai_chat\"}");
        String body = "推荐如下：\n```json\n{\"items\":[{\"title\":\"白夜追凶\",\"mediaType\":\"tv\"}]}\n```";

        List<AiRecommendationService.AiRecommendation> items = AiRecommendationService.parseResponseRecommendations(body, config);

        assertEquals(1, items.size());
        assertEquals("白夜追凶", items.get(0).title);
    }

    @Test
    public void parseResponseRecommendations_acceptsSseDataLinesFromCompatibleGateways() {
        AiConfig config = AiConfig.objectFrom("{\"protocol\":\"openai_chat\"}");
        String body = "data: {\"choices\":[{\"message\":{\"content\":\"{\\\"items\\\":[{\\\"title\\\":\\\"狂飙\\\"}]}\"}}]}\n\ndata: [DONE]";

        List<AiRecommendationService.AiRecommendation> items = AiRecommendationService.parseResponseRecommendations(body, config);

        assertEquals(1, items.size());
        assertEquals("狂飙", items.get(0).title);
    }

    @Test
    public void shouldRetryRecommendationRequest_retriesTransientHttpAndParseFailures() {
        assertTrue(AiRecommendationService.shouldRetryRecommendationRequest(408, false, null));
        assertTrue(AiRecommendationService.shouldRetryRecommendationRequest(429, false, null));
        assertTrue(AiRecommendationService.shouldRetryRecommendationRequest(503, false, null));
        assertTrue(AiRecommendationService.shouldRetryRecommendationRequest(200, true, null));
        assertFalse(AiRecommendationService.shouldRetryRecommendationRequest(401, false, null));
    }

    @Test
    public void fingerprint_ignoresCurrentTitleButChangesWhenSearchRecordsOrPromptChanges() {
        AiConfig first = AiConfig.objectFrom("{\"enabled\":true,\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"p1\"}");
        AiConfig second = AiConfig.objectFrom("{\"enabled\":true,\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"p2\"}");

        String base = AiRecommendationService.fingerprint("A", "h1", "[\"x\"]", first);
        String changedTitle = AiRecommendationService.fingerprint("B", "h1", "[\"x\"]", first);
        String changedSearch = AiRecommendationService.fingerprint("A", "h1", "[\"x\",\"y\"]", first);
        String changedPrompt = AiRecommendationService.fingerprint("A", "h1", "[\"x\"]", second);

        assertEquals(base, changedTitle);
        assertFalse(base.equals(changedSearch));
        assertFalse(base.equals(changedPrompt));
    }

    @Test
    public void fingerprint_changesWhenProtocolOrUserAgentChanges() {
        AiConfig first = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"customUserAgent\":\"ua1\"}");
        AiConfig second = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_chat\",\"endpoint\":\"https://api.openai.com/v1/chat/completions\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"customUserAgent\":\"ua1\"}");
        AiConfig third = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"customUserAgent\":\"ua2\"}");

        String base = AiRecommendationService.fingerprint("A", "h1", "[\"x\"]", first);

        assertFalse(base.equals(AiRecommendationService.fingerprint("A", "h1", "[\"x\"]", second)));
        assertFalse(base.equals(AiRecommendationService.fingerprint("A", "h1", "[\"x\"]", third)));
    }

    @Test
    public void buildModelUrlCandidates_matchesCcSwitchCompatibleRules() {
        AiConfig openAiFull = AiConfig.objectFrom("{\"protocol\":\"openai_chat\",\"endpoint\":\"https://proxy.example.com/v1/chat/completions\",\"apiKey\":\"sk\"}");
        AiConfig zhipuV4 = AiConfig.objectFrom("{\"protocol\":\"openai_chat\",\"endpoint\":\"https://open.bigmodel.cn/api/coding/paas/v4\",\"apiKey\":\"sk\"}");
        AiConfig anthropicCompat = AiConfig.objectFrom("{\"protocol\":\"anthropic_messages\",\"endpoint\":\"https://api.z.ai/api/anthropic\",\"apiKey\":\"sk\"}");

        assertEquals("https://proxy.example.com/v1/models", AiCompletionClient.buildModelUrlCandidates(openAiFull).get(0));
        assertEquals("https://open.bigmodel.cn/api/coding/paas/v4/models", AiCompletionClient.buildModelUrlCandidates(zhipuV4).get(0));
        assertEquals("https://api.z.ai/v1/models", AiCompletionClient.buildModelUrlCandidates(anthropicCompat).get(1));
    }

    @Test
    public void parseModelList_acceptsOpenAiAndGeminiResponses() {
        AiConfig openAi = AiConfig.objectFrom("{\"protocol\":\"openai_chat\"}");
        AiConfig gemini = AiConfig.objectFrom("{\"protocol\":\"gemini_native\"}");

        List<AiCompletionClient.ModelInfo> openAiModels = AiCompletionClient.parseModelList("{\"data\":[{\"id\":\"gpt-4.1-mini\",\"owned_by\":\"openai\"}]}", openAi);
        List<AiCompletionClient.ModelInfo> geminiModels = AiCompletionClient.parseModelList("{\"models\":[{\"name\":\"models/gemini-2.5-flash\",\"supportedGenerationMethods\":[\"generateContent\"]},{\"name\":\"models/embedding-001\",\"supportedGenerationMethods\":[\"embedContent\"]}]}", gemini);

        assertEquals(1, openAiModels.size());
        assertEquals("gpt-4.1-mini", openAiModels.get(0).getId());
        assertEquals("openai", openAiModels.get(0).getOwnedBy());
        assertEquals(1, geminiModels.size());
        assertEquals("gemini-2.5-flash", geminiModels.get(0).getId());
        assertEquals("Google", geminiModels.get(0).getOwnedBy());
    }

    @Test
    public void sanitizeUserAgent_ignoresControlCharactersButAllowsTab() {
        assertEquals("claude-cli/2.1.161", AiCompletionClient.sanitizeUserAgent(" claude-cli/2.1.161 "));
        assertEquals("client\tname", AiCompletionClient.sanitizeUserAgent("client\tname"));
        assertEquals("", AiCompletionClient.sanitizeUserAgent("bad\nua"));
    }

    @Test
    public void aiDebugLog_redactsSecretHeadersAndKeepsSingleLine() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer sk-test");
        headers.put("x-api-key", "sk-test");
        headers.put("User-Agent", "webhtv");

        Map<String, String> redacted = AiDebugLog.redactedHeaders(headers);

        assertEquals("<redacted>", redacted.get("Authorization"));
        assertEquals("<redacted>", redacted.get("x-api-key"));
        assertEquals("webhtv", redacted.get("User-Agent"));
        assertFalse(AiDebugLog.clip("a\nb").contains("\n"));
    }

    @Test
    public void resolvedItemCache_roundTripsOverviewReasonPosterAndRatings() {
        TmdbItem item = new TmdbItem(
                123, "tv", "大明王朝1566", "剧集 · 2007", "历史正剧内容简介",
                "poster.jpg", "backdrop.jpg", "主演", 9.7, "zh", "CN",
                Collections.singletonList(18), "导演", 9.7, 9.6, "推荐理由");

        List<TmdbItem> items = AiRecommendationService.parseResolvedItems("{\"items\":[" + AiRecommendationService.tmdbItemToJson(item) + "]}");

        assertEquals(1, items.size());
        assertEquals(123, items.get(0).getTmdbId());
        assertEquals("tv", items.get(0).getMediaType());
        assertEquals("大明王朝1566", items.get(0).getTitle());
        assertEquals("历史正剧内容简介", items.get(0).getOverview());
        assertEquals("推荐理由", items.get(0).getRecommendationReason());
        assertEquals("poster.jpg", items.get(0).getPosterUrl());
        assertEquals(9.7, items.get(0).getTmdbRating(), 0.001);
        assertEquals(9.6, items.get(0).getDoubanRating(), 0.001);
    }

    @Test
    public void withReasonPreservesMatchedOverviewAndStoresReasonSeparately() {
        TmdbItem matched = new TmdbItem(123, "movie", "指环王3", "电影 · 2003", "魔戒终章的内容简介", "poster.jpg", "backdrop.jpg", "主演", 8.5);

        TmdbItem enriched = AiRecommendationService.withReason(matched, "11项奥斯卡加持");

        assertEquals("魔戒终章的内容简介", enriched.getOverview());
        assertEquals("11项奥斯卡加持", enriched.getRecommendationReason());
    }

    @Test
    public void resolvedItemCache_recoversLegacyTmdbRatingWhenDoubanAlreadyStored() {
        List<TmdbItem> items = AiRecommendationService.parseResolvedItems(
                "{\"items\":[{\"tmdbId\":123,\"mediaType\":\"movie\",\"title\":\"旧缓存\",\"rating\":8.1,\"tmdbRating\":0,\"doubanRating\":8.7}]}");

        assertEquals(1, items.size());
        assertEquals(8.1, items.get(0).getTmdbRating(), 0.001);
        assertEquals(8.7, items.get(0).getDoubanRating(), 0.001);
    }

    @Test
    public void resolvedItemCache_treatsLegacyNegativeIdRatingAsDouban() {
        List<TmdbItem> items = AiRecommendationService.parseResolvedItems(
                "{\"items\":[{\"tmdbId\":-123,\"mediaType\":\"movie\",\"title\":\"霸王别姬\",\"rating\":9.6}]}");

        assertEquals(1, items.size());
        assertEquals(0.0, items.get(0).getTmdbRating(), 0.001);
        assertEquals(9.6, items.get(0).getDoubanRating(), 0.001);
    }

    @Test
    public void latestCacheKey_ignoresHistoryAndSearchButKeepsTitleAndPrompt() {
        AiConfig first = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"p1\"}");
        AiConfig second = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"p2\"}");

        String base = AiRecommendationService.latestCacheKey("长安的荔枝", first);

        assertEquals(base, AiRecommendationService.latestCacheKey("长安的荔枝", first));
        assertFalse(base.equals(AiRecommendationService.latestCacheKey("大明王朝1566", first)));
        assertFalse(base.equals(AiRecommendationService.latestCacheKey("长安的荔枝", second)));
    }

    @Test
    public void latestDisplayCacheKey_ignoresPromptButKeepsTitleAndModelIdentity() {
        AiConfig first = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"p1\"}");
        AiConfig second = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"p2\"}");
        AiConfig third = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1\",\"recommendPrompt\":\"p1\"}");

        String base = AiRecommendationService.latestDisplayCacheKey("长安的荔枝", first);

        assertEquals(base, AiRecommendationService.latestDisplayCacheKey("长安的荔枝", second));
        assertFalse(base.equals(AiRecommendationService.latestDisplayCacheKey("大明王朝1566", first)));
        assertFalse(base.equals(AiRecommendationService.latestDisplayCacheKey("长安的荔枝", third)));
    }

    @Test
    public void newestCacheFile_usesAnyRecentCacheAsLooseDisplayFallback() throws Exception {
        File dir = Files.createTempDirectory("ai-rec-cache").toFile();
        File oldRaw = new File(dir, "old.json");
        File newRaw = new File(dir, "display_new.json");
        File legacyResolved = new File(dir, "latest_legacy.items.json");
        File resolved = new File(dir, "latest_items.items.v2.json");
        Files.write(oldRaw.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(newRaw.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(legacyResolved.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(resolved.toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        oldRaw.setLastModified(1000);
        resolved.setLastModified(2000);
        newRaw.setLastModified(3000);
        legacyResolved.setLastModified(4000);

        assertEquals(newRaw, AiRecommendationService.newestCacheFile(dir, false));
        assertEquals(resolved, AiRecommendationService.newestCacheFile(dir, true));
    }

    @Test
    public void latestCacheKeysForRead_checksLegacySystemPromptOnlyForSystemPromptUsers() {
        AiConfig system = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"" + AiConfig.LEGACY_RECOMMEND_PROMPT_V1.replace("\"", "\\\"") + "\"}");
        AiConfig custom = AiConfig.objectFrom("{\"enabled\":true,\"protocol\":\"openai_responses\",\"endpoint\":\"https://api.openai.com/v1/responses\",\"apiKey\":\"sk-test\",\"model\":\"gpt-4.1-mini\",\"recommendPrompt\":\"请优先推荐冷门悬疑片\"}");

        List<String> systemKeys = AiRecommendationService.latestCacheKeysForRead("长安的荔枝", system);
        List<String> customKeys = AiRecommendationService.latestCacheKeysForRead("长安的荔枝", custom);

        assertEquals(3, systemKeys.size());
        assertEquals(1, customKeys.size());
    }

    @Test
    public void pageAllCandidates_keepsAllAiItemsInsteadOfDefaultPageSize() {
        List<PersonalRecommendationService.RecommendationCandidate> candidates = new ArrayList<>();
        for (int i = 0; i < PersonalRecommendationService.DEFAULT_PAGE_SIZE + 5; i++) {
            TmdbItem item = new TmdbItem(i + 1, "movie", "推荐" + i, "", "", "", "");
            candidates.add(new PersonalRecommendationService.RecommendationCandidate(item, "ai:" + i, "推荐" + i, 100 - i, i));
        }

        PersonalRecommendationService.RecommendationPage page = AiRecommendationService.pageAllCandidates(candidates, "fp");

        assertEquals(PersonalRecommendationService.DEFAULT_PAGE_SIZE + 5, page.getItems().size());
        assertFalse(page.hasMore());
    }

    @Test
    public void bestDoubanItem_prefersMatchingTypeAndYearForAiFallback() {
        List<PersonalRecommendationService.DoubanSubject> subjects = new ArrayList<>();
        subjects.add(PersonalRecommendationService.DoubanSubject.from(com.google.gson.JsonParser.parseString("{\"id\":\"1\",\"title\":\"想见你\",\"type\":\"movie\",\"year\":\"2023\",\"img\":\"movie.jpg\"}").getAsJsonObject()));
        subjects.add(PersonalRecommendationService.DoubanSubject.from(com.google.gson.JsonParser.parseString("{\"id\":\"2\",\"title\":\"想见你\",\"type\":\"tv\",\"year\":\"2019\",\"img\":\"tv.jpg\"}").getAsJsonObject()));

        TmdbItem item = PersonalRecommendationService.bestDoubanItem("想见你", "tv", 2019, subjects);

        assertEquals(-2, item.getTmdbId());
        assertEquals("tv", item.getMediaType());
        assertTrue(item.getPosterUrl().contains("tv.jpg"));
    }
}
