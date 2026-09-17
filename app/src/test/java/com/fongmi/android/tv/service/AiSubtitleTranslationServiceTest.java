package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationCache;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationRequest;
import com.fongmi.android.tv.subtitle.translate.SubtitleTranslationResult;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AiSubtitleTranslationServiceTest {

    @Test
    public void translate_writesTranslatedSrtAndKeepsTiming() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,200 --> 00:00:03,400\n"
                + "Hello there\n"
                + "General Kenobi\n"
                + "\n"
                + "2\n"
                + "00:00:04,000 --> 00:00:05,250\n"
                + "Next line\n", StandardCharsets.UTF_8);
        RecordingTransport transport = new RecordingTransport("{\"items\":["
                + "{\"id\":1,\"text\":\"你好\\n克诺比将军\"},"
                + "{\"id\":2,\"text\":\"下一句\"}"
                + "]}");
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir, 1, 1);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(2, result.getCueCount());
        assertEquals(1, result.getChunkCount());
        assertNotNull(result.getTranslatedAsset());
        assertEquals("zh-Hans", result.getTranslatedAsset().getLanguage());
        assertTrue(transport.prompt.contains("Hello there"));
        assertFalse(transport.prompt.contains("00:00:01,200"));
        assertEquals("1\n"
                + "00:00:01,200 --> 00:00:03,400\n"
                + "你好\n"
                + "克诺比将军\n"
                + "\n"
                + "2\n"
                + "00:00:04,000 --> 00:00:05,250\n"
                + "下一句\n", Files.readString(new File(result.getTranslatedAsset().getLocalPath()).toPath()));
    }

    @Test
    public void translate_rejectsMissingItemsAndKeepsOriginalSubtitle() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n"
                + "\n"
                + "2\n"
                + "00:00:03,000 --> 00:00:04,000\n"
                + "Again\n", StandardCharsets.UTF_8);
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), new RecordingTransport("{\"items\":[{\"id\":1,\"text\":\"你好\"}]}"), dir, 1, 1);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.ERROR, result.getStatus());
        assertEquals("invalid_response", result.getReason());
        assertNull(result.getTranslatedAsset());
        assertEquals(source.getAbsolutePath(), result.getSourceAsset().getLocalPath());
    }

    @Test
    public void translate_retriesTransientTransportFailure() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n", StandardCharsets.UTF_8);
        SequentialTransport transport = new SequentialTransport(
                new IOException("timeout"),
                "{\"items\":[{\"id\":1,\"text\":\"你好\"}]}"
        );
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(2, transport.callCount);
        assertNotNull(result.getTranslatedAsset());
    }

    @Test
    public void translate_retriesInvalidAiResponse() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n", StandardCharsets.UTF_8);
        SequentialTransport transport = new SequentialTransport(
                "not json",
                "{\"items\":[{\"id\":1,\"text\":\"你好\"}]}"
        );
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(2, transport.callCount);
        assertNotNull(result.getTranslatedAsset());
    }

    @Test
    public void translate_usesLargerDynamicChunks() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), srt(241, "Short line"), StandardCharsets.UTF_8);
        AutoJsonTransport transport = new AutoJsonTransport();
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(241, result.getCueCount());
        assertEquals(2, result.getChunkCount());
        assertEquals(2, transport.callCount.get());
    }

    @Test
    public void translate_usesTwoConcurrentLargeChunksByDefaultAndMergesInOrder() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), srt(480, "Short line"), StandardCharsets.UTF_8);
        ConcurrentJsonTransport transport = new ConcurrentJsonTransport(2);
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(480, result.getCueCount());
        assertEquals(2, result.getChunkCount());
        assertEquals(2, transport.callCount.get());
        assertTrue("expected two simultaneous AI subtitle requests, maxActive=" + transport.maxActive.get(), transport.maxActive.get() >= 2);
        String output = Files.readString(new File(result.getTranslatedAsset().getLocalPath()).toPath());
        assertTrue(output.indexOf("译文1") < output.indexOf("译文240"));
        assertTrue(output.indexOf("译文240") < output.indexOf("译文241"));
        assertTrue(output.indexOf("译文241") < output.indexOf("译文480"));
    }

    @Test
    public void translate_usesTwoDefaultChunksForSmallMultiCueSubtitle() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), srt(2, "Short line"), StandardCharsets.UTF_8);
        ConcurrentJsonTransport transport = new ConcurrentJsonTransport(2);
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(2, result.getCueCount());
        assertEquals(2, result.getChunkCount());
        assertEquals(2, transport.callCount.get());
        assertTrue("expected two simultaneous AI subtitle requests, maxActive=" + transport.maxActive.get(), transport.maxActive.get() >= 2);
    }

    @Test
    public void translate_shrinksChunkAfterInvalidLargeBatch() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), srt(120, "Short line"), StandardCharsets.UTF_8);
        SizeLimitedTransport transport = new SizeLimitedTransport(60);
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir, 1, 1);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals("reason=" + result.getReason() + " calls=" + transport.callCount + " first=" + transport.firstRequestSize + " maxOk=" + transport.maxSuccessfulRequestSize, SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(120, result.getCueCount());
        assertEquals(2, result.getChunkCount());
        assertEquals(3, transport.callCount);
        assertEquals(120, transport.firstRequestSize);
        assertEquals(60, transport.maxSuccessfulRequestSize);
        assertNotNull(result.getTranslatedAsset());
    }

    @Test
    public void translate_treatsConsecutiveUnchangedLinesAsInvalid() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), srt(6, "Still English text"), StandardCharsets.UTF_8);
        FirstUnchangedThenJsonTransport transport = new FirstUnchangedThenJsonTransport();
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir, 1, 1);

        SubtitleTranslationResult result = service.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(6, result.getCueCount());
        assertEquals(2, result.getChunkCount());
        assertEquals(3, transport.callCount);
        assertNotNull(result.getTranslatedAsset());
    }

    @Test
    public void translate_reportsPlannedChunkProgress() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), srt(4, "Short line"), StandardCharsets.UTF_8);
        RecordingProgress progress = new RecordingProgress();
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), new AutoJsonTransport(), dir, 1, 2);

        SubtitleTranslationResult result = service.translate(request(source), progress);

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, result.getStatus());
        assertEquals(4, progress.startedCueCount);
        assertEquals(2, progress.startedChunkCount);
        assertEquals(List.of("1/2", "2/2"), progress.completed);
    }

    @Test
    public void translate_writesAiSubtitleEventsToDebugLogs() throws Exception {
        java.nio.file.Path sourcePath = findMainJavaPath().resolve(java.nio.file.Path.of("com", "fongmi", "android", "tv", "service", "AiSubtitleTranslationService.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("AI subtitle translation logs must use an ai-* tag so /debug/logs AI filter can show them",
                source.contains("AI_LOG_TAG = \"ai-subtitle\"") && source.contains("SpiderDebug.log(AI_LOG_TAG, message)"));
        assertTrue("AI subtitle translation should log the planned split before requests start",
                source.contains("ai subtitle translation plan"));
        assertTrue("AI subtitle translation should log cache hits so matching can be diagnosed without chunk requests",
                source.contains("ai subtitle cache hit"));
        assertTrue("AI subtitle translation should log cache bypasses for regenerate actions",
                source.contains("ai subtitle cache bypassed"));
        assertTrue("AI subtitle translation should log early skips with reasons",
                source.contains("ai subtitle skipped reason="));
        assertTrue("AI subtitle translation should log each chunk request",
                source.contains("ai subtitle chunk request"));
        assertTrue("AI subtitle translation should log each translated chunk",
                source.contains("ai subtitle chunk translated"));
    }

    @Test
    public void translate_usesCacheWithoutCallingAiAgain() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n", StandardCharsets.UTF_8);
        AiSubtitleTranslationService first = new AiSubtitleTranslationService(config(), new RecordingTransport("{\"items\":[{\"id\":1,\"text\":\"你好\"}]}"), dir);
        SubtitleTranslationResult translated = first.translate(request(source));
        RecordingTransport secondTransport = new RecordingTransport("{\"items\":[]}", true);
        AiSubtitleTranslationService second = new AiSubtitleTranslationService(config(), secondTransport, dir);

        SubtitleTranslationResult cached = second.translate(request(source));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, translated.getStatus());
        assertEquals(SubtitleTranslationResult.Status.CACHE_HIT, cached.getStatus());
        assertNull(secondTransport.prompt);
        assertNotNull(cached.getTranslatedAsset());
        assertEquals(translated.getTranslatedAsset().getLocalPath(), cached.getTranslatedAsset().getLocalPath());
    }

    @Test
    public void translate_bypassesCacheWhenRegenerating() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n", StandardCharsets.UTF_8);
        AiSubtitleTranslationService first = new AiSubtitleTranslationService(config(), new RecordingTransport("{\"items\":[{\"id\":1,\"text\":\"旧译文\"}]}"), dir);
        SubtitleTranslationResult translated = first.translate(request(source));
        RecordingTransport regenerateTransport = new RecordingTransport("{\"items\":[{\"id\":1,\"text\":\"新译文\"}]}");
        AiSubtitleTranslationService regenerate = new AiSubtitleTranslationService(config(), regenerateTransport, dir);

        SubtitleTranslationResult regenerated = regenerate.translate(request(source, "en", false));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, translated.getStatus());
        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, regenerated.getStatus());
        assertNotNull(regenerateTransport.prompt);
        assertNotNull(regenerated.getTranslatedAsset());
        assertEquals(translated.getTranslatedAsset().getLocalPath(), regenerated.getTranslatedAsset().getLocalPath());
        assertTrue(Files.readString(new File(regenerated.getTranslatedAsset().getLocalPath()).toPath()).contains("新译文"));
    }

    @Test
    public void translate_reusesCacheWhenSameContentIsResolvedToDifferentFile() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        String content = "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n";
        File firstSource = new File(dir, "source-first.srt");
        File secondSource = new File(dir, "source-second.srt");
        Files.writeString(firstSource.toPath(), content, StandardCharsets.UTF_8);
        Files.writeString(secondSource.toPath(), content, StandardCharsets.UTF_8);
        firstSource.setLastModified(1_700_000_000_000L);
        secondSource.setLastModified(1_800_000_000_000L);
        AiSubtitleTranslationService first = new AiSubtitleTranslationService(config(), new RecordingTransport("{\"items\":[{\"id\":1,\"text\":\"你好\"}]}"), dir);
        SubtitleTranslationResult translated = first.translate(request(firstSource));
        RecordingTransport secondTransport = new RecordingTransport("{\"items\":[]}", true);
        AiSubtitleTranslationService second = new AiSubtitleTranslationService(config(), secondTransport, dir);

        SubtitleTranslationResult cached = second.translate(request(secondSource));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, translated.getStatus());
        assertEquals(SubtitleTranslationResult.Status.CACHE_HIT, cached.getStatus());
        assertNull(secondTransport.prompt);
        assertNotNull(cached.getTranslatedAsset());
        assertEquals(translated.getTranslatedAsset().getLocalPath(), cached.getTranslatedAsset().getLocalPath());
    }

    @Test
    public void translate_reusesCacheWhenSourceLanguageMetadataChanges() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n", StandardCharsets.UTF_8);
        AiSubtitleTranslationService first = new AiSubtitleTranslationService(config(), new RecordingTransport("{\"items\":[{\"id\":1,\"text\":\"你好\"}]}"), dir);
        SubtitleTranslationResult translated = first.translate(request(source, "en"));
        RecordingTransport secondTransport = new RecordingTransport("{\"items\":[]}", true);
        AiSubtitleTranslationService second = new AiSubtitleTranslationService(config(), secondTransport, dir);

        SubtitleTranslationResult cached = second.translate(request(source, "eng"));

        assertEquals(SubtitleTranslationResult.Status.TRANSLATED, translated.getStatus());
        assertEquals(SubtitleTranslationResult.Status.CACHE_HIT, cached.getStatus());
        assertNull(secondTransport.prompt);
        assertNotNull(cached.getTranslatedAsset());
        assertEquals(translated.getTranslatedAsset().getLocalPath(), cached.getTranslatedAsset().getLocalPath());
    }

    @Test
    public void translate_migratesLegacyMetadataCacheWithoutCallingAiAgain() throws Exception {
        File dir = Files.createTempDirectory("ai-subtitle-service").toFile();
        File source = new File(dir, "source.srt");
        source.setLastModified(1_700_000_000_000L);
        Files.writeString(source.toPath(), "1\n"
                + "00:00:01,000 --> 00:00:02,000\n"
                + "Hello\n", StandardCharsets.UTF_8);
        source.setLastModified(1_700_000_000_000L);
        SubtitleTranslationRequest request = request(source, "eng");
        SubtitleTranslationCache cache = new SubtitleTranslationCache();
        File legacyOutput = new File(dir, cache.legacyMetadataKey(request, config(), "subtitleChunks=2") + ".zh-Hans.srt");
        File currentOutput = new File(dir, cache.key(request, config(), "subtitleChunks=2") + ".zh-Hans.srt");
        Files.writeString(legacyOutput.toPath(), "1\n00:00:01,000 --> 00:00:02,000\n你好\n", StandardCharsets.UTF_8);
        RecordingTransport transport = new RecordingTransport("{\"items\":[]}", true);
        AiSubtitleTranslationService service = new AiSubtitleTranslationService(config(), transport, dir);

        SubtitleTranslationResult cached = service.translate(request);

        assertEquals(SubtitleTranslationResult.Status.CACHE_HIT, cached.getStatus());
        assertNull(transport.prompt);
        assertNotNull(cached.getTranslatedAsset());
        assertEquals(currentOutput.getAbsolutePath(), cached.getTranslatedAsset().getLocalPath());
        assertTrue(currentOutput.isFile());
        assertEquals(Files.readString(legacyOutput.toPath()), Files.readString(currentOutput.toPath()));
    }


    private SubtitleTranslationRequest request(File source) {
        return request(source, "en");
    }

    private SubtitleTranslationRequest request(File source, String language) {
        return request(source, language, true);
    }

    private SubtitleTranslationRequest request(File source, String language, boolean useCache) {
        SubtitleAsset asset = new SubtitleAsset(source.getAbsolutePath(), source.getAbsolutePath(), "source.srt", "en", "application/x-subrip", 0, false, 0L);
        return SubtitleTranslationRequest.builder()
                .sourceAsset(asset)
                .sourceLanguage(language)
                .targetLanguage("zh-Hans")
                .mode(SubtitleTranslationRequest.MODE_TRANSLATED)
                .useCache(useCache)
                .build();
    }

    private AiConfig config() {
        AiConfig config = new AiConfig();
        config.setEnabled(true);
        config.setProtocol(AiConfig.PROTOCOL_OPENAI_RESPONSES);
        config.setEndpoint("https://api.example.test/v1/responses");
        config.setApiKey("test-key");
        config.setModel("gpt-4.1-mini");
        return config.sanitize();
    }

    private static java.nio.file.Path findMainJavaPath() {
        java.nio.file.Path moduleRelative = java.nio.file.Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return java.nio.file.Path.of("app", "src", "main", "java");
    }

    private static String srt(int count, String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            builder.append(i).append('\n')
                    .append("00:00:01,000 --> 00:00:02,000\n")
                    .append(text).append(' ').append(i).append("\n\n");
        }
        return builder.toString();
    }

    private static final class RecordingTransport implements AiSubtitleTranslationService.TranslationTransport {

        private final String response;
        private final boolean failOnCall;
        private String prompt;

        private RecordingTransport(String response) {
            this(response, false);
        }

        private RecordingTransport(String response, boolean failOnCall) {
            this.response = response;
            this.failOnCall = failOnCall;
        }

        @Override
        public String complete(AiConfig config, String prompt) {
            if (failOnCall) throw new AssertionError("AI transport should not be called on cache hit");
            this.prompt = prompt;
            return response;
        }
    }

    private static final class RecordingProgress implements AiSubtitleTranslationService.ProgressListener {

        private int startedCueCount;
        private int startedChunkCount;
        private final List<String> completed = new ArrayList<>();

        @Override
        public void onStarted(int cueCount, int chunkCount) {
            this.startedCueCount = cueCount;
            this.startedChunkCount = chunkCount;
        }

        @Override
        public void onChunkTranslated(int completedChunks, int totalChunks) {
            completed.add(completedChunks + "/" + totalChunks);
        }
    }

    private static final class SequentialTransport implements AiSubtitleTranslationService.TranslationTransport {

        private final Object[] outcomes;
        private int callCount;

        private SequentialTransport(Object... outcomes) {
            this.outcomes = outcomes;
        }

        @Override
        public String complete(AiConfig config, String prompt) throws Exception {
            Object outcome = outcomes[Math.min(callCount, outcomes.length - 1)];
            callCount++;
            if (outcome instanceof Exception) throw (Exception) outcome;
            return String.valueOf(outcome);
        }
    }

    private static final class AutoJsonTransport implements AiSubtitleTranslationService.TranslationTransport {

        private static final Pattern ID = Pattern.compile("\"id\":(\\d+)");
        private final AtomicInteger callCount = new AtomicInteger();

        @Override
        public String complete(AiConfig config, String prompt) {
            callCount.incrementAndGet();
            String input = prompt.substring(prompt.indexOf("输入 JSON:\n") + "输入 JSON:\n".length());
            Matcher matcher = ID.matcher(input);
            StringBuilder builder = new StringBuilder("{\"items\":[");
            boolean first = true;
            while (matcher.find()) {
                if (!first) builder.append(',');
                first = false;
                int id = Integer.parseInt(matcher.group(1));
                builder.append("{\"id\":").append(id).append(",\"text\":\"译文").append(id).append("\"}");
            }
            return builder.append("]}").toString();
        }
    }

    private static final class ConcurrentJsonTransport implements AiSubtitleTranslationService.TranslationTransport {

        private final CountDownLatch ready;
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger maxActive = new AtomicInteger();
        private final AtomicInteger callCount = new AtomicInteger();

        private ConcurrentJsonTransport(int expectedConcurrentCalls) {
            this.ready = new CountDownLatch(expectedConcurrentCalls);
        }

        @Override
        public String complete(AiConfig config, String prompt) throws Exception {
            callCount.incrementAndGet();
            int current = active.incrementAndGet();
            maxActive.updateAndGet(value -> Math.max(value, current));
            ready.countDown();
            ready.await(2, TimeUnit.SECONDS);
            active.decrementAndGet();
            return jsonForPrompt(prompt);
        }
    }

    private static final class SizeLimitedTransport implements AiSubtitleTranslationService.TranslationTransport {

        private final int maxItems;
        private int callCount;
        private int firstRequestSize = -1;
        private int maxSuccessfulRequestSize;

        private SizeLimitedTransport(int maxItems) {
            this.maxItems = maxItems;
        }

        @Override
        public String complete(AiConfig config, String prompt) {
            callCount++;
            int itemCount = itemCount(prompt);
            if (firstRequestSize < 0) firstRequestSize = itemCount;
            if (itemCount > maxItems) return "not json";
            maxSuccessfulRequestSize = Math.max(maxSuccessfulRequestSize, itemCount);
            return jsonForPrompt(prompt);
        }
    }

    private static final class FirstUnchangedThenJsonTransport implements AiSubtitleTranslationService.TranslationTransport {

        private int callCount;

        @Override
        public String complete(AiConfig config, String prompt) {
            callCount++;
            return callCount == 1 ? unchangedJsonForPrompt(prompt) : jsonForPrompt(prompt);
        }
    }

    private static int itemCount(String prompt) {
        int count = 0;
        Matcher matcher = AutoJsonTransport.ID.matcher(itemsJson(prompt));
        while (matcher.find()) count++;
        return count;
    }

    private static String unchangedJsonForPrompt(String prompt) {
        Matcher matcher = Pattern.compile("\\{\"id\":(\\d+),\"text\":\"([^\"]*)\"\\}").matcher(itemsJson(prompt));
        StringBuilder builder = new StringBuilder("{\"items\":[");
        boolean first = true;
        while (matcher.find()) {
            if (!first) builder.append(',');
            first = false;
            builder.append("{\"id\":").append(matcher.group(1)).append(",\"text\":\"").append(matcher.group(2)).append("\"}");
        }
        return builder.append("]}").toString();
    }

    private static String jsonForPrompt(String prompt) {
        Matcher matcher = AutoJsonTransport.ID.matcher(itemsJson(prompt));
        StringBuilder builder = new StringBuilder("{\"items\":[");
        boolean first = true;
        while (matcher.find()) {
            if (!first) builder.append(',');
            first = false;
            int id = Integer.parseInt(matcher.group(1));
            builder.append("{\"id\":").append(id).append(",\"text\":\"译文").append(id).append("\"}");
        }
        return builder.append("]}").toString();
    }

    private static String itemsJson(String prompt) {
        String marker = "输入 JSON:\n";
        int inputStart = prompt.indexOf(marker);
        String input = inputStart < 0 ? prompt : prompt.substring(inputStart + marker.length());
        int start = input.indexOf("\"items\":[");
        if (start < 0) return "";
        int end = input.indexOf("],\"contextAfter\"", start);
        if (end < 0) end = input.indexOf("]}", start);
        return end > start ? input.substring(start, end + 1) : input.substring(start);
    }
}
