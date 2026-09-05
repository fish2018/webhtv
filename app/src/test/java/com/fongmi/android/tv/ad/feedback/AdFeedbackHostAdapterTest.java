package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * {@link AdFeedbackHostAdapter} 的可测部分：配置读取的容错、站点基线记录、
 * 以及不依赖网络的字段映射。{@code fetchEvidence} 会发起真实请求，此处不覆盖。
 */
public class AdFeedbackHostAdapterTest {

    @Test
    public void mapsMetadataAndPlaybackIntoContext() {
        FakePlayback playback = new FakePlayback();
        AdFeedbackHostAdapter adapter = adapter(playback, deps());

        AdEvidenceCollector.Context context = adapter.context();

        assertEquals("site", context.siteKey());
        assertEquals("剧名", context.vodName());
        assertEquals("https://v.example.com/play/index.m3u8", context.playUrl());
        assertTrue(context.hls());
        assertEquals(45_000L, adapter.positionMs());
        assertEquals(600_000L, adapter.durationMs());
    }

    @Test
    public void recordsPlaybackHostIntoBaseline() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());
        AdFeedbackHostAdapter adapter = adapter(new FakePlayback(), deps(baseline));

        adapter.recordPlaybackHost();

        assertEquals(List.of("v.example.com"), baseline.hosts("site"));
        assertEquals(List.of("v.example.com"), adapter.siteBaselineHosts());
    }

    @Test
    public void skipsBaselineRecordWhenUrlHasNoHost() {
        SitePlaylistHostBaseline baseline =
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage());
        FakePlayback playback = new FakePlayback();
        playback.url = "relative/path.m3u8";
        AdFeedbackHostAdapter adapter = adapter(playback, deps(baseline));

        adapter.recordPlaybackHost();

        assertEquals(0, baseline.siteCount());
    }

    @Test
    public void configReadFailuresDegradeToEmptyInsteadOfCrashing() {
        AdFeedbackHostAdapter.Deps throwing = new AdFeedbackHostAdapter.Deps(
                () -> { throw new IllegalStateException("prefs unavailable"); },
                () -> { throw new IllegalStateException("prefs unavailable"); },
                () -> { throw new IllegalStateException("prefs unavailable"); },
                () -> { throw new IllegalStateException("prefs unavailable"); },
                () -> { throw new IllegalStateException("prefs unavailable"); },
                () -> { throw new IllegalStateException("prefs unavailable"); },
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage()));
        AdFeedbackHostAdapter adapter = adapter(new FakePlayback(), throwing);

        // 归因是辅助功能，读配置失败必须降级而不是把异常抛给播放中的用户
        assertTrue(adapter.blacklistedHosts().isEmpty());
        assertTrue(adapter.interfaceCandidateHosts().isEmpty());
        assertEquals("", adapter.interfaceSourceName());
        assertTrue(adapter.protectingExcludes().isEmpty());
        assertTrue(adapter.hlsRuleStates().isEmpty());
        // 唯一的例外：已启用规则读不到时必须是 null 而非空列表 —— 空列表意味着
        // 「站内确无规则生效」，会让自检跳过叠加校验，是 fail-open。
        assertNull("读取失败必须传播为『无法确定』", adapter.activeHlsRules());
    }

    @Test
    public void nullConfigValuesBecomeEmptyLists() {
        AdFeedbackHostAdapter.Deps nulls = new AdFeedbackHostAdapter.Deps(
                () -> null, () -> null, () -> null, () -> null, () -> null, () -> null,
                new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage()));
        AdFeedbackHostAdapter adapter = adapter(new FakePlayback(), nulls);

        assertTrue(adapter.blacklistedHosts().isEmpty());
        assertEquals("", adapter.interfaceSourceName());
        assertTrue(adapter.hlsRuleStates().isEmpty());
        // activeHlsRules 的 null 是有语义的，不能被折成空列表
        assertNull("数据源的『无法确定』必须原样透传", adapter.activeHlsRules());
    }

    @Test
    public void nonHlsPlaybackSkipsEvidenceFetchAndLegacyFlag() {
        FakePlayback playback = new FakePlayback();
        playback.hls = false;
        AdFeedbackHostAdapter adapter = adapter(playback, deps());

        // 不是 HLS 就不发请求
        assertNull(adapter.fetchEvidence());
        // 旧启发式只在点播 HLS 上生效
        assertFalse(adapter.legacyHeuristicActive());
    }

    @Test
    public void legacyHeuristicIsActiveForHlsPlayback() {
        AdFeedbackHostAdapter adapter = adapter(new FakePlayback(), deps());

        assertTrue(adapter.legacyHeuristicActive());
    }

    @Test
    public void blankUrlSkipsEvidenceFetch() {
        FakePlayback playback = new FakePlayback();
        playback.url = "  ";
        AdFeedbackHostAdapter adapter = adapter(playback, deps());

        assertNull(adapter.fetchEvidence());
    }

    @Test
    public void invalidateEvidenceClearsCache() {
        AdFeedbackHostAdapter adapter = adapter(new FakePlayback(), deps());

        adapter.invalidateEvidence();

        assertNull(adapter.cachedEvidence());
    }

    @Test
    public void delegatesSkipAndAudioCandidateToPlayback() {
        FakePlayback playback = new FakePlayback();
        playback.audioStart = 12_000L;
        AdFeedbackHostAdapter adapter = adapter(playback, deps());

        assertTrue(adapter.skipInterval(10_000, 40_000, "fb-1"));
        assertEquals(List.of("10000-40000/fb-1"), playback.skipCalls);
        assertEquals(12_000L, adapter.audioCandidateStartMs());
    }

    private static AdFeedbackHostAdapter adapter(FakePlayback playback,
                                                 AdFeedbackHostAdapter.Deps deps) {
        return new AdFeedbackHostAdapter(playback, new FakeMetadata(), new FakeUi(), deps);
    }

    private static AdFeedbackHostAdapter.Deps deps() {
        return deps(new SitePlaylistHostBaseline(new SitePlaylistHostBaseline.MemoryStorage()));
    }

    private static AdFeedbackHostAdapter.Deps deps(SitePlaylistHostBaseline baseline) {
        return new AdFeedbackHostAdapter.Deps(
                List::of, List::of, () -> "", List::of, List::of, List::of, baseline);
    }

    private static final class FakePlayback implements AdFeedbackHostAdapter.Playback {
        private final List<String> skipCalls = new java.util.ArrayList<>();
        private String url = "https://v.example.com/play/index.m3u8";
        private boolean hls = true;
        private long audioStart = -1L;

        @Override
        public long positionMs() {
            return 45_000L;
        }

        @Override
        public long durationMs() {
            return 600_000L;
        }

        @Override
        public String playUrl() {
            return url;
        }

        @Override
        public Map<String, String> headers() {
            return Map.of();
        }

        @Override
        public boolean hls() {
            return hls;
        }

        @Override
        public boolean skipInterval(long startMs, long endMs, String feedbackId) {
            skipCalls.add(startMs + "-" + endMs + "/" + feedbackId);
            return true;
        }

        @Override
        public long audioCandidateStartMs() {
            return audioStart;
        }
    }

    private static final class FakeMetadata implements AdFeedbackHostAdapter.Metadata {
        @Override
        public String siteKey() {
            return "site";
        }

        @Override
        public String siteName() {
            return "站点";
        }

        @Override
        public String vodName() {
            return "剧名";
        }

        @Override
        public String flagName() {
            return "线路";
        }

        @Override
        public String episodeName() {
            return "第 1 集";
        }
    }

    private static final class FakeUi implements AdFeedbackHostAdapter.Ui {
        @Override
        public void runBackground(Runnable task) {
            task.run();
        }

        @Override
        public void runOnUi(Runnable task) {
            task.run();
        }

        @Override
        public void showSession(AdFeedbackSession session) {
        }
    }
}
