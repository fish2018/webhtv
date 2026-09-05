package com.fongmi.android.tv.player;

import com.fongmi.android.tv.service.IntroSkipService;
import com.fongmi.android.tv.service.IntroSkipService.Segment;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class IntroSkipPlaybackStateTest {

    @Test
    public void cancelingConfirmationAllowsTheSameSegmentToBeAskedAgain() {
        IntroSkipPlayback playback = new IntroSkipPlayback();
        Segment segment = segment();

        assertTrue(playback.beginConfirmation(segment));
        assertTrue(playback.isConfirmationPending(segment));

        playback.cancelConfirmation(segment);

        assertFalse(playback.isSegmentHandled(segment));
        assertFalse(playback.isConfirmationPending(segment));
        assertTrue(playback.beginConfirmation(segment));
    }

    @Test
    public void completingConfirmationMarksOnlyThatStableSegmentHandled() {
        IntroSkipPlayback playback = new IntroSkipPlayback();
        Segment segment = segment();

        assertTrue(playback.beginConfirmation(segment));
        playback.completeConfirmation(segment);

        assertTrue(playback.isSegmentHandled(segment));
        assertFalse(playback.isConfirmationPending(segment));
        assertFalse(playback.beginConfirmation(segment));
    }

    @Test
    public void unknownTrailingEndCannotAdvanceBeforeDurationIsKnown() {
        Segment segment = IntroSkipService.parseTheIntroDb(
                "{\"credits\":[{\"start_ms\":1380000,\"end_ms\":null}]}", 0)
                .getEndings().get(0);

        assertFalse(IntroSkipPlayback.endsWithFile(segment, 0));
        assertTrue(IntroSkipPlayback.endsWithFile(segment, 1_500_000));
    }

    /**
     * 两家 provider 的同类片段不得共享一次性状态。
     *
     * <p>别名归一（credits→outro）只该消解同一家内部的字段名差异。若跨 provider 也折进同一个
     * id，两家对片尾给出的不同边界就共享 {@code skipped}：服务层按时间轴判为两段而各自保留时，
     * 跳过其中一段会让另一段被 {@code isSegmentHandled} 永久吞掉，用户再也跳不到真正的片尾。
     */
    @Test
    public void differentProvidersDoNotShareSegmentIdentity() {
        IntroSkipPlayback playback = new IntroSkipPlayback();
        Segment theIntroDb = IntroSkipService.parseTheIntroDb(
                "{\"duration_ms\":2700000,\"credits\":[{\"start_ms\":2640000,\"end_ms\":2695000}]}", 2_700_000)
                .getEndings().get(0);
        Segment introDb = IntroSkipService.parseIntroDb(
                "{\"duration_ms\":2700000,\"outro\":{\"start_ms\":2500000,\"end_ms\":2560000}}", 2_700_000)
                .getEndings().get(0);

        playback.beginConfirmation(theIntroDb);
        playback.completeConfirmation(theIntroDb);

        assertTrue(playback.isSegmentHandled(theIntroDb));
        assertFalse("另一家 provider 的片尾不能被连带标记为已处理", playback.isSegmentHandled(introDb));
    }

    private static Segment segment() {
        return IntroSkipService.parseTheIntroDb(
                "{\"intro\":[{\"start_ms\":0,\"end_ms\":45000}]}", 2_700_000)
                .getOpenings().get(0);
    }
}
