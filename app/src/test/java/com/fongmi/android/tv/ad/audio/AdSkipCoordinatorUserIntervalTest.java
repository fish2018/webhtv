package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.player.audio.PlaybackMediaClock;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * {@link AdSkipCoordinator#onUserInterval} 的行为锁定。
 *
 * <p>与音频候选路径的关键区别：用户区间是媒体时间，不经采集时钟映射；
 * 授权来自用户框选，不经 PROMPT_PENDING 确认。
 */
public class AdSkipCoordinatorUserIntervalTest {

    @Test
    public void skipsImmediatelyWithoutPromptAndKeepsUndo() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 135_000L, 1_200_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        boolean applied = coordinator.onUserInterval(7L, 2L, 135_000L, 165_000L, "fb-1");

        assertTrue(applied);
        // 不经确认对话框
        assertEquals(0, ui.candidateShows);
        assertEquals(1, ui.undoShows);
        assertEquals(List.of(165_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.UNDO_WINDOW, coordinator.state());
    }

    @Test
    public void undoReturnsToPositionBeforeSkip() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 135_000L, 1_200_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        coordinator.onUserInterval(7L, 2L, 135_000L, 165_000L, "fb-1");
        ui.actions.undo();

        assertEquals(List.of(165_000L, 135_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void usesMediaTimeDirectlyWithoutCaptureClockMapping() {
        // 采集时钟严重滞后于播放位置。若误走 mapCaptureToMediaMs 会得到别的目标值。
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 100_000L, 600_000L, true, false,
                new PlaybackMediaClock.Snapshot(1L, 0L, 20_000L, 15_000L, true, true)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        assertTrue(coordinator.onUserInterval(1L, 1L, 100_000L, 130_000L, "fb-1"));

        // 终点原样使用
        assertEquals(List.of(130_000L), playback.seekTargets);
    }

    @Test
    public void staleGenerationIsRejected() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 3L, 10_000L, 600_000L, true, false, freshClock(3L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        assertFalse(coordinator.onUserInterval(7L, 2L, 10_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void mismatchedSessionIsRejected() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 10_000L, 600_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        assertFalse(coordinator.onUserInterval(8L, 2L, 10_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
    }

    @Test
    public void livePlaybackIsRejected() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 10_000L, 600_000L, true, true, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        assertFalse(coordinator.onUserInterval(7L, 2L, 10_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
    }

    @Test
    public void unseekablePlaybackIsRejected() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 10_000L, 600_000L, false, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        assertFalse(coordinator.onUserInterval(7L, 2L, 10_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
    }

    @Test
    public void intervalAlreadyPlayedPastIsRecordedButNotSkipped() {
        // 短按场景：广告已经放完，当前位置在区间终点之后
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 200_000L, 600_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        assertFalse(coordinator.onUserInterval(7L, 2L, 135_000L, 165_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void endBeyondDurationIsClamped() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 580_000L, 600_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        assertTrue(coordinator.onUserInterval(7L, 2L, 580_000L, 900_000L, "fb-1"));

        assertEquals(List.of(600_000L), playback.seekTargets);
    }

    @Test
    public void duplicateSubmissionDoesNotSeekTwice() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 135_000L, 1_200_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        assertTrue(coordinator.onUserInterval(7L, 2L, 135_000L, 165_000L, "fb-1"));
        // 撤销窗口内连按第二次
        assertFalse(coordinator.onUserInterval(7L, 2L, 135_000L, 165_000L, "fb-1"));

        assertEquals(List.of(165_000L), playback.seekTargets);
    }

    @Test
    public void invalidIntervalIsRejected() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 10_000L, 600_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        // 终点不晚于起点
        assertFalse(coordinator.onUserInterval(7L, 2L, 40_000L, 40_000L, "fb-1"));
        assertFalse(coordinator.onUserInterval(7L, 2L, 50_000L, 40_000L, "fb-1"));
        // 负起点
        assertFalse(coordinator.onUserInterval(7L, 2L, -1_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
    }

    @Test
    public void closedCoordinatorRejectsInterval() {
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 10_000L, 600_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);
        coordinator.close();

        assertFalse(coordinator.onUserInterval(7L, 2L, 10_000L, 40_000L, "fb-1"));
        assertTrue(playback.seekTargets.isEmpty());
    }

    @Test
    public void undoAfterDeadlineDoesNotSeekBack() {
        ManualTime time = new ManualTime();
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                7L, 2L, 135_000L, 1_200_000L, true, false, freshClock(2L)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L, time);

        coordinator.onUserInterval(7L, 2L, 135_000L, 165_000L, "fb-1");
        time.now = 5_001L;
        ui.actions.undo();

        assertEquals(List.of(165_000L), playback.seekTargets);
        assertEquals(AdSkipCoordinator.State.IDLE, coordinator.state());
    }

    @Test
    public void audioCandidatePromptIsNotDisturbedByRejectedInterval() {
        // 区间被拒不应清掉正在展示的音频候选提示
        FakePlaybackPort playback = new FakePlaybackPort(snapshot(
                1L, 1L, 5_000L, 100_000L, true, false,
                new PlaybackMediaClock.Snapshot(1L, 0L, 30_000L, 5_000L, true, true)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        coordinator.onCandidate(new AdAudioConsumer.Candidate(1L, 1L,
                new AudioFingerprintMatcher.MatchEvent(
                        AudioFingerprintMatcher.Type.START_MATCHED, "ad-1", 0L, 10_000L, 0.9f, 6)));
        assertEquals(AdSkipCoordinator.State.PROMPT_PENDING, coordinator.state());

        // 用无效区间触发拒绝
        assertFalse(coordinator.onUserInterval(1L, 1L, 40_000L, 40_000L, "fb-1"));

        assertEquals(AdSkipCoordinator.State.PROMPT_PENDING, coordinator.state());
        assertEquals("ad-1", ui.lastPrompt.ruleId());
    }

    @Test
    public void failedSeekDoesNotClearSuppressedAudioCandidate() {
        // 音频候选被 ignore 后，其 suppressedKey 必须存活；
        // 用户区间 seek 失败不应把它一起清掉，否则同一广告会再次弹提示。
        RejectingSeekPort playback = new RejectingSeekPort(snapshot(
                1L, 1L, 5_000L, 100_000L, true, false,
                new PlaybackMediaClock.Snapshot(1L, 0L, 30_000L, 5_000L, true, true)));
        FakeUiPort ui = new FakeUiPort();
        AdSkipCoordinator coordinator = new AdSkipCoordinator(playback, ui, 5_000L);

        AdAudioConsumer.Candidate audio = new AdAudioConsumer.Candidate(1L, 1L,
                new AudioFingerprintMatcher.MatchEvent(
                        AudioFingerprintMatcher.Type.START_MATCHED, "ad-1", 0L, 10_000L, 0.9f, 6));
        coordinator.onCandidate(audio);
        ui.actions.ignore();
        assertEquals(AdSkipCoordinator.State.SUPPRESSED, coordinator.state());

        // 用户区间通过校验但 seekTo 被拒
        assertFalse(coordinator.onUserInterval(1L, 1L, 5_000L, 40_000L, "fb-1"));

        // 同一音频候选不得再次弹出
        int showsBefore = ui.candidateShows;
        coordinator.onCandidate(audio);
        assertEquals(showsBefore, ui.candidateShows);
    }

    private static AdSkipCoordinator.PlaybackSnapshot snapshot(
            long session, long generation, long position, long duration,
            boolean seekable, boolean live, PlaybackMediaClock.Snapshot clock) {
        return new AdSkipCoordinator.PlaybackSnapshot(
                session, generation, position, duration, seekable, live, clock);
    }

    private static PlaybackMediaClock.Snapshot freshClock(long generation) {
        return new PlaybackMediaClock.Snapshot(generation, 0L, 0L, 0L, true, true);
    }

    private static final class ManualTime implements LongSupplier {
        long now;

        @Override
        public long getAsLong() {
            return now;
        }
    }

    private static final class FakePlaybackPort implements AdSkipCoordinator.PlaybackPort {
        private AdSkipCoordinator.PlaybackSnapshot snapshot;
        private final List<Long> seekTargets = new ArrayList<>();

        FakePlaybackPort(AdSkipCoordinator.PlaybackSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public AdSkipCoordinator.PlaybackSnapshot snapshot(long sessionId, long generation) {
            return snapshot;
        }

        @Override
        public AdSkipCoordinator.SeekResult seekTo(long sessionId, long generation, long positionMs) {
            if (snapshot.sessionId() != sessionId || snapshot.generation() != generation) {
                return AdSkipCoordinator.SeekResult.rejected(sessionId, generation);
            }
            seekTargets.add(positionMs);
            long nextGeneration = generation == Long.MAX_VALUE ? 0L : generation + 1L;
            PlaybackMediaClock.Snapshot nextClock = new PlaybackMediaClock.Snapshot(
                    nextGeneration, snapshot.clock().mediaAnchorMs(), snapshot.clock().capturedUntilMs(),
                    snapshot.clock().presentedCaptureMs(), snapshot.clock().playing(), snapshot.clock().fresh());
            snapshot = new AdSkipCoordinator.PlaybackSnapshot(
                    snapshot.sessionId(), nextGeneration, positionMs, snapshot.durationMs(),
                    snapshot.seekable(), snapshot.live(), nextClock);
            return new AdSkipCoordinator.SeekResult(true, sessionId, nextGeneration);
        }
    }

    /** 快照校验通过，但 seekTo 一律拒绝。 */
    private static final class RejectingSeekPort implements AdSkipCoordinator.PlaybackPort {
        private final AdSkipCoordinator.PlaybackSnapshot snapshot;

        RejectingSeekPort(AdSkipCoordinator.PlaybackSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public AdSkipCoordinator.PlaybackSnapshot snapshot(long sessionId, long generation) {
            return snapshot;
        }

        @Override
        public AdSkipCoordinator.SeekResult seekTo(long sessionId, long generation, long positionMs) {
            return AdSkipCoordinator.SeekResult.rejected(sessionId, generation);
        }
    }

    private static final class FakeUiPort implements AdSkipCoordinator.UiPort {
        private int candidateShows;
        private int undoShows;
        private AdSkipCoordinator.Prompt lastPrompt;
        private AdSkipCoordinator.Actions actions;

        @Override
        public void showCandidate(AdSkipCoordinator.Prompt prompt, AdSkipCoordinator.Actions actions) {
            candidateShows++;
            lastPrompt = prompt;
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
