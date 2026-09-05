package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link AdAudioRuntimeController#skipUserInterval} 的行为锁定。
 *
 * <p>核心契约：区间反馈不依赖音频指纹功能。指纹与语音都关闭、规则库为空时，
 * 用户框选广告依然要能跳过。
 */
public class AdAudioRuntimeControllerUserIntervalTest {

    @Test
    public void skipsIntervalWithFingerprintDisabledAndNoRules() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, emptySnapshot());
        // 功能关闭、规则为空 —— 音频通道完全不工作
        runtime.start(false);
        runtime.bindUi(new FakeUiPort());

        assertFalse(hub.isCaptureRequested(PlaybackMediaSignalHub.ConsumerKind.AD_AUDIO));
        assertTrue(runtime.skipUserInterval(1_000L, 40_000L, "fb-1"));
        assertEquals(List.of(40_000L), playback.seekTargets);

        runtime.close();
    }

    @Test
    public void requiresBoundUiBecauseUndoPromptHasNowhereToGo() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, emptySnapshot());
        runtime.start(false);

        assertFalse(runtime.skipUserInterval(1_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());

        runtime.close();
    }

    @Test
    public void showsUndoPromptAfterSkip() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, emptySnapshot());
        FakeUiPort ui = new FakeUiPort();
        runtime.start(false);
        runtime.bindUi(ui);

        runtime.skipUserInterval(1_000L, 40_000L, "fb-1");

        assertEquals(1, ui.undoShows);
        // 不经确认提示，用户框选本身即为授权
        assertEquals(0, ui.candidateShows);

        runtime.close();
    }

    @Test
    public void reusesCoordinatorCreatedByBindUi() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        // 指纹启用：bindUi 已建立 coordinator
        AdAudioRuntimeController runtime = runtime(hub, playback, goodSnapshot());
        FakeUiPort ui = new FakeUiPort();
        runtime.start(true);
        runtime.bindUi(ui);

        assertTrue(runtime.skipUserInterval(1_000L, 40_000L, "fb-1"));
        assertEquals(List.of(40_000L), playback.seekTargets);

        runtime.close();
    }

    @Test
    public void ineligiblePlaybackIsRejected() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        // eligible=false 会让快照变成 live 且不可 seek
        FakePlaybackPort playback = new FakePlaybackPort(hub, false);
        AdAudioRuntimeController runtime = runtime(hub, playback, emptySnapshot());
        runtime.start(false);
        runtime.bindUi(new FakeUiPort());

        assertFalse(runtime.skipUserInterval(1_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());

        runtime.close();
    }

    @Test
    public void closedRuntimeRejectsInterval() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, emptySnapshot());
        runtime.start(false);
        runtime.bindUi(new FakeUiPort());
        runtime.close();

        assertFalse(runtime.skipUserInterval(1_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
    }

    @Test
    public void unbindUiPreventsFurtherIntervalSkips() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, emptySnapshot());
        runtime.start(false);
        runtime.bindUi(new FakeUiPort());
        runtime.unbindUi();

        assertFalse(runtime.skipUserInterval(1_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());

        runtime.close();
    }

    @Test
    public void usesCurrentSessionSoStaleIntervalsCannotSeek() {
        PlaybackMediaSignalHub hub = new PlaybackMediaSignalHub(8);
        hub.beginSession(0L);
        FakePlaybackPort playback = new FakePlaybackPort(hub, true);
        AdAudioRuntimeController runtime = runtime(hub, playback, emptySnapshot());
        runtime.start(false);
        runtime.bindUi(new FakeUiPort());

        // 换源：session 变更后旧区间不应再套用到新内容上
        assertTrue(runtime.skipUserInterval(1_000L, 40_000L, "fb-1"));
        hub.resetTimeline(0L, PlaybackMediaSignalHub.ResetReason.SOURCE_CHANGED);

        // 新 session 下同一 feedbackId 视为新的一次提交
        assertTrue(runtime.skipUserInterval(1_000L, 50_000L, "fb-1"));
        assertEquals(List.of(40_000L, 50_000L), playback.seekTargets);

        runtime.close();
    }

    private static AdAudioRuntimeController runtime(
            PlaybackMediaSignalHub hub, FakePlaybackPort playback, AdAudioRuleSnapshot snapshot) {
        return new AdAudioRuntimeController(
                hub, new PlaybackMediaClock(500L), () -> snapshot, playback,
                Runnable::run, () -> { });
    }

    private static AdAudioRuleSnapshot emptySnapshot() {
        return new AdAudioRuleSnapshot(
                "test", "", AudioFingerprintRuleSet.empty(), List.of(), "");
    }

    private static AdAudioRuleSnapshot goodSnapshot() {
        AudioFingerprintRuleSet rules = AudioFingerprintRuleCodec.fromJson("{"
                + "\"schemaVersion\":2,\"algorithm\":{"
                + "\"id\":\"spectral-sequence-v2\",\"sampleRate\":16000,"
                + "\"windowMs\":512,\"hopMs\":256,\"bandCount\":16},"
                + "\"rules\":[{\"id\":\"ad\",\"durationMs\":10000,"
                + "\"anchorOffsetMs\":0,\"anchorDurationMs\":3000,"
                + "\"fingerprint\":[\"32f0007c\",\"35c100e0\",\"3b8b01c0\",\"d30a0380\"]}]}");
        return new AdAudioRuleSnapshot("test", "v1", rules, List.of(), "");
    }

    private static final class FakePlaybackPort implements AdAudioRuntimeController.PlaybackPort {
        private final PlaybackMediaSignalHub hub;
        private final List<Long> seekTargets = new ArrayList<>();
        private boolean eligible;

        FakePlaybackPort(PlaybackMediaSignalHub hub, boolean eligible) {
            this.hub = hub;
            this.eligible = eligible;
        }

        @Override
        public boolean isEligible(long sessionId, long generation) {
            return eligible;
        }

        @Override
        public AdSkipCoordinator.PlaybackSnapshot snapshot(long sessionId, long generation) {
            return new AdSkipCoordinator.PlaybackSnapshot(
                    sessionId, generation, 1_000L, 100_000L, eligible, !eligible,
                    new PlaybackMediaClock.Snapshot(generation, 0L, 50_000L, 1_000L, true, true));
        }

        @Override
        public AdSkipCoordinator.SeekResult seekTo(long sessionId, long generation, long positionMs) {
            PlaybackMediaSignalHub.Session session = hub.session();
            boolean applied = eligible && session.id() == sessionId
                    && session.generation() == generation;
            if (applied) seekTargets.add(positionMs);
            return new AdSkipCoordinator.SeekResult(applied, session.id(), session.generation());
        }
    }

    private static final class FakeUiPort implements AdSkipCoordinator.UiPort {
        private int candidateShows;
        private int undoShows;
        private AdSkipCoordinator.Actions actions;

        @Override
        public void showCandidate(AdSkipCoordinator.Prompt prompt, AdSkipCoordinator.Actions actions) {
            candidateShows++;
            this.actions = actions;
        }

        @Override
        public void showUndo(AdSkipCoordinator.UndoPrompt prompt, AdSkipCoordinator.Actions actions) {
            undoShows++;
            this.actions = actions;
        }

        @Override
        public void dismiss(long sessionId) {
        }
    }
}
