package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Sub;
import com.fongmi.android.tv.bean.TmdbEpisode;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchResult;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchStatus;
import com.fongmi.android.tv.subtitle.model.SubtitleRequest;
import com.fongmi.android.tv.subtitle.model.SubtitleTrigger;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubtitlePlaybackSessionTest {

    @Test
    public void onPlaybackStarted_resolvesAutoPlaySourceSwitchAndEpisodeSwitch() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeResultApplier applier = new FakeResultApplier();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), applier);

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        host.episode = Episode.create("第1集", "ep1", "https://episode/1");
        session.onPlaybackStarted(host, result("https://play/1b.m3u8"));
        host.episode = Episode.create("第2集", "ep2", "https://episode/2");
        session.onPlaybackStarted(host, result("https://play/2.m3u8"));

        assertEquals(List.of(SubtitleTrigger.AUTO_PLAY, SubtitleTrigger.SOURCE_SWITCH, SubtitleTrigger.EPISODE_SWITCH), requestFactory.triggers);
        assertEquals(1, controller.playbackStarted.size());
        assertEquals(1, controller.sourceChanged.size());
        assertEquals(1, controller.episodeChanged.size());
        assertTrue(applier.applied.isEmpty());
    }

    @Test
    public void onPlaybackStarted_withProvidedSubtitleSkipsAutoMatchButKeepsManualSearch() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), new FakeResultApplier());
        controller.manualResult = SubtitleMatchResult.noMatch(Collections.emptyList(), "empty_result");
        List<SubtitleMatchResult> results = new ArrayList<>();

        Result result = result("https://play/1.m3u8");
        result.setSubs(Collections.singletonList(Sub.create("外挂字幕", "https://sub/1.srt", "zh", "application/x-subrip")));
        session.onPlaybackStarted(host, result);
        session.manualSearch(host, "想见你", (request, matchResult, applied) -> results.add(matchResult));

        assertTrue(controller.playbackStarted.isEmpty());
        assertEquals(List.of("playback-1"), controller.stoppedKeys);
        assertEquals(1, controller.manualSearches.size());
        assertEquals(SubtitleMatchStatus.NO_MATCH, results.get(0).getStatus());
    }

    @Test
    public void manualSearch_rebuildsRequestWhenSessionStoppedButPlaybackAlive() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), new FakeResultApplier());
        controller.manualResult = SubtitleMatchResult.noMatch(Collections.emptyList(), "empty_result");
        List<SubtitleMatchResult> results = new ArrayList<>();

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        session.stop(host);
        host.playbackResult = result("https://play/1.m3u8");
        session.manualSearch(host, "想见你", (request, result, applied) -> results.add(result));

        assertEquals(List.of(SubtitleTrigger.AUTO_PLAY, SubtitleTrigger.MANUAL_SEARCH), requestFactory.triggers);
        assertEquals(1, controller.manualSearches.size());
        assertEquals(SubtitleMatchStatus.NO_MATCH, results.get(0).getStatus());
    }

    @Test
    public void manualSearch_reportsInactiveOnlyWhenHostNotPlaying() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), new FakeResultApplier());
        List<SubtitleMatchResult> results = new ArrayList<>();

        host.hostActive = false;
        session.manualSearch(host, "想见你", (request, result, applied) -> results.add(result));

        assertTrue(controller.manualSearches.isEmpty());
        assertEquals(SubtitleMatchStatus.ERROR, results.get(0).getStatus());
        assertEquals("inactive", results.get(0).getReason());
    }

    @Test
    public void getManualSearchKeyword_returnsKeywordAfterSessionStopped() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        requestFactory.tmdbTitle = "镖人：风起大漠";
        requestFactory.vodYear = "2026";
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), new FakeResultApplier());

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        session.stop(host);
        host.playbackResult = result("https://play/1.m3u8");

        assertEquals("镖人：风起大漠 2026", session.getManualSearchKeyword(host));
    }

    @Test
    public void getManualSearchKeyword_doesNotReviveStoppedSession() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeResultApplier applier = new FakeResultApplier();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), applier);
        SubtitleAsset asset = new SubtitleAsset("file:///subtitle.srt", "/tmp/subtitle.srt", "字幕", "zh", "application/x-subrip", 0, false, 0L);

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        session.stop(host);
        host.playbackResult = result("https://play/1.m3u8");
        session.getManualSearchKeyword(host);

        // 仅读取关键词不应恢复会话，避免用户取消搜索后残留活跃状态
        assertFalse(session.applySubtitleAsset(host, asset));
        assertTrue(applier.applied.isEmpty());
    }

    @Test
    public void onSubtitleResult_appliesOnlyForCurrentActiveRequest() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeResultApplier applier = new FakeResultApplier();
        FakeResultNotifier notifier = new FakeResultNotifier();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), applier, notifier);

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        SubtitleRequest current = controller.playbackStarted.get(0);
        SubtitleAsset asset = new SubtitleAsset("file:///subtitle.srt", "/tmp/subtitle.srt", "匹配字幕", "zh", "application/x-subrip", 0, false, 0L);
        controller.emit(current, SubtitleMatchResult.matched(null, asset, Collections.emptyList()));

        assertEquals(1, applier.applied.size());
        assertEquals("/tmp/subtitle.srt", applier.applied.get(0).getUrl());
        assertEquals(List.of(SubtitleMatchStatus.MATCHED), notifier.statuses);
        assertEquals(List.of(true), notifier.applied);

        session.stop(host);
        controller.emit(current, SubtitleMatchResult.matched(null, asset, Collections.emptyList()));
        assertEquals(1, applier.applied.size());
        assertEquals(1, notifier.statuses.size());
    }

    @Test
    public void onSubtitleResult_notifiesFailureWithoutApplyingSubtitle() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeResultApplier applier = new FakeResultApplier();
        FakeResultNotifier notifier = new FakeResultNotifier();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), applier, notifier);

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        SubtitleRequest current = controller.playbackStarted.get(0);
        controller.emit(current, SubtitleMatchResult.noMatch(Collections.emptyList(), "empty_result"));

        assertTrue(applier.applied.isEmpty());
        assertEquals(List.of(SubtitleMatchStatus.NO_MATCH), notifier.statuses);
        assertEquals(List.of(false), notifier.applied);
    }

    @Test
    public void manualSearch_returnsCandidatesForCurrentActiveRequest() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeResultApplier applier = new FakeResultApplier();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), applier);
        SubtitleCandidate candidate = new SubtitleCandidate("assrt", "sub-1", "匹配字幕", "zh", "srt", "", 88, 2024, 1, 1, null, "query", true, "payload");
        controller.manualResult = SubtitleMatchResult.noMatch(List.of(candidate), "manual_candidates");
        List<SubtitleMatchResult> results = new ArrayList<>();
        List<Boolean> applied = new ArrayList<>();

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        session.manualSearch(host, (request, result, didApply) -> {
            results.add(result);
            applied.add(didApply);
        });

        assertEquals(1, controller.manualSearches.size());
        assertEquals(List.of(candidate), results.get(0).getCandidates());
        assertEquals(List.of(false), applied);
        assertTrue(applier.applied.isEmpty());
    }

    @Test
    public void manualSearch_withKeywordPassesEditableTextToController() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), new FakeResultApplier());
        controller.manualResult = SubtitleMatchResult.noMatch(Collections.emptyList(), "empty_result");

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        session.manualSearch(host, "镖人：风起大漠 2026", (request, result, didApply) -> {
        });

        assertEquals(List.of("镖人：风起大漠 2026"), controller.manualKeywords);
    }

    @Test
    public void getManualSearchKeyword_prefersTmdbTitleAndYear() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        requestFactory.tmdbTitle = "镖人：风起大漠";
        requestFactory.vodYear = "2026";
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), new FakeResultApplier());

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));

        assertEquals("镖人：风起大漠 2026", session.getManualSearchKeyword(host));
    }

    @Test
    public void resolveManual_appliesSelectedSubtitle() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeResultApplier applier = new FakeResultApplier();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), applier);
        SubtitleCandidate candidate = new SubtitleCandidate("assrt", "sub-1", "匹配字幕", "zh", "srt", "", 88, 2024, 1, 1, null, "query", true, "payload");
        SubtitleAsset asset = new SubtitleAsset("https://provider.example/manual.srt", "/tmp/manual.srt", "手动字幕", "zh", "application/x-subrip", 0, false, 0L);
        controller.resolveResult = SubtitleMatchResult.matched(candidate, asset, List.of(candidate));
        List<Boolean> applied = new ArrayList<>();

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        session.resolveManual(host, candidate, (request, result, didApply) -> applied.add(didApply));

        assertEquals(List.of(candidate), controller.resolveCandidates);
        assertEquals(1, applier.applied.size());
        assertEquals("/tmp/manual.srt", applier.applied.get(0).getUrl());
        assertEquals(List.of(true), applied);
    }

    @Test
    public void applySubtitleAsset_appliesGeneratedSubtitleForActiveSession() {
        FakeController controller = new FakeController();
        FakeRequestFactory requestFactory = new FakeRequestFactory();
        FakeResultApplier applier = new FakeResultApplier();
        FakeHost host = new FakeHost();
        SubtitlePlaybackSession session = new SubtitlePlaybackSession(host, controller, requestFactory, new SubtitleInjector(), applier);
        SubtitleAsset asset = new SubtitleAsset("file:///translated.srt", "/tmp/translated.srt", "AI 中文字幕", "zh-Hans", "application/x-subrip", 0, true, 0L);

        session.onPlaybackStarted(host, result("https://play/1.m3u8"));
        boolean applied = session.applySubtitleAsset(host, asset);

        assertTrue(applied);
        assertEquals(1, applier.applied.size());
        assertEquals("/tmp/translated.srt", applier.applied.get(0).getUrl());
    }

    private Result result(String playUrl) {
        Result result = new Result();
        result.setPlayUrl(playUrl);
        return result;
    }

    private static final class FakeController implements SubtitlePlaybackSession.Controller {

        private final List<SubtitleRequest> playbackStarted = new ArrayList<>();
        private final List<SubtitleRequest> episodeChanged = new ArrayList<>();
        private final List<SubtitleRequest> sourceChanged = new ArrayList<>();
        private final List<SubtitleRequest> manualSearches = new ArrayList<>();
        private final List<String> manualKeywords = new ArrayList<>();
        private final List<SubtitleCandidate> resolveCandidates = new ArrayList<>();
        private final List<String> stoppedKeys = new ArrayList<>();
        private SubtitleMatchResult manualResult;
        private SubtitleMatchResult resolveResult;
        private Callback callback;

        @Override
        public void bind(Callback callback) {
            this.callback = callback;
        }

        @Override
        public void onPlaybackStarted(SubtitleRequest request) {
            playbackStarted.add(request);
        }

        @Override
        public void onEpisodeChanged(SubtitleRequest request) {
            episodeChanged.add(request);
        }

        @Override
        public void onSourceChanged(SubtitleRequest request) {
            sourceChanged.add(request);
        }

        @Override
        public void onStop(String playbackKey) {
            stoppedKeys.add(playbackKey);
        }

        @Override
        public void manualSearch(SubtitleRequest request, Callback callback) {
            manualSearch(request, "", callback);
        }

        @Override
        public void manualSearch(SubtitleRequest request, String keyword, Callback callback) {
            manualSearches.add(request);
            manualKeywords.add(keyword);
            if (callback != null && manualResult != null) callback.onSubtitleResult(request, manualResult);
        }

        @Override
        public void resolve(SubtitleRequest request, SubtitleCandidate candidate, Callback callback) {
            resolveCandidates.add(candidate);
            if (callback != null && resolveResult != null) callback.onSubtitleResult(request, resolveResult);
        }

        private void emit(SubtitleRequest request, SubtitleMatchResult result) {
            if (callback != null) callback.onSubtitleResult(request, result);
        }
    }

    private static final class FakeRequestFactory extends SubtitleRequestFactory {

        private final List<SubtitleTrigger> triggers = new ArrayList<>();
        private String tmdbTitle = "";
        private String vodYear = "";

        @Override
        public SubtitleRequest create(String playbackKey, Site site, Vod vod, Episode episode, Result result, TmdbItem tmdbItem, TmdbEpisode tmdbEpisode, SubtitleTrigger trigger) {
            triggers.add(trigger);
            return SubtitleRequest.builder()
                    .playbackKey(playbackKey)
                    .siteKey(site == null ? "" : "test")
                    .vodId(vod == null ? "" : "vod-1")
                    .vodName(vod == null ? "" : "想见你")
                    .vodYear(vodYear)
                    .episodeName(episode == null ? "" : "第1集")
                    .playUrl(result == null ? "" : "https://play/test.m3u8")
                    .playHeaders(Map.of())
                    .preferredLanguage("zh")
                    .trigger(trigger)
                    .allowTmdbLookup(false)
                    .tmdbItem(tmdbTitle.isEmpty() ? null : new TmdbItem(1, "movie", tmdbTitle, "", "", "", ""))
                    .build();
        }
    }

    private static final class FakeResultApplier implements SubtitlePlaybackSession.ResultApplier {

        private final List<Sub> applied = new ArrayList<>();
        private boolean applyResult = true;

        @Override
        public boolean apply(SubtitlePlaybackSession.Host host, Sub sub) {
            applied.add(sub);
            return applyResult;
        }
    }

    private static final class FakeResultNotifier implements SubtitlePlaybackSession.ResultNotifier {

        private final List<SubtitleMatchStatus> statuses = new ArrayList<>();
        private final List<Boolean> applied = new ArrayList<>();

        @Override
        public void notify(SubtitlePlaybackSession.Host host, SubtitleRequest request, SubtitleMatchResult result, boolean applied) {
            statuses.add(result.getStatus());
            this.applied.add(applied);
        }
    }

    private static final class FakeHost implements SubtitlePlaybackSession.Host {

        private Episode episode = Episode.create("第1集", "ep1", "https://episode/1");
        private boolean hostActive = true;
        private Result playbackResult;

        @Override
        public String getSubtitlePlaybackKey() {
            return "playback-1";
        }

        @Override
        public Site getSubtitleSite() {
            return Site.get("test", "Test");
        }

        @Override
        public Vod getSubtitleVod() {
            Vod vod = new Vod();
            vod.setId("vod-1");
            vod.setName("想见你");
            return vod;
        }

        @Override
        public Episode getSubtitleEpisode() {
            return episode;
        }

        @Override
        public TmdbItem getSubtitleTmdbItem() {
            return null;
        }

        @Override
        public TmdbEpisode getSubtitleTmdbEpisode() {
            return null;
        }

        @Override
        public com.fongmi.android.tv.player.PlayerManager getSubtitlePlayer() {
            return null;
        }

        @Override
        public boolean isSubtitleHostActive() {
            return hostActive;
        }

        @Override
        public Result getSubtitlePlaybackResult() {
            return playbackResult;
        }
    }
}
