package com.fongmi.android.tv.ad.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SpeechAdMatcherTest {

    @Test
    public void matchesOrderedSegmentsAcrossRecognitionsAndClampsWindow() {
        SpeechAdMatcher matcher = matcher("广告之后*>马上回来，[2,30]");

        assertTrue(matcher.accept("广告之后有精彩内容", 10_000_000L,
                12_000_000L, 7).isEmpty());
        SpeechAdMatcher.Match match = matcher.accept("马上回来", 13_000_000L,
                15_000_000L, 7).get(0);

        assertEquals(8_000L, match.candidateStartMs());
        assertEquals(6_000L, match.candidateStartMs(6_000L));
        assertEquals(45_000L, match.candidateEndMs(-1L));
        assertEquals(40_000L, match.candidateEndMs(40_000L));
        assertEquals(6_000L, match.candidateEndMs(6_000L));
    }

    @Test
    public void wildcardLayoutHandlesRepeatedLiteralsAndAdjacentWildcards() {
        assertEquals(1, matcher("a*a,1").accept("a中a", 0L,
                1_000_000L, 1).size());
        assertEquals(1, matcher("a**a,1").accept("a中a", 0L,
                1_000_000L, 1).size());
        assertEquals(1, matcher("a*a*a,1").accept("a中a再a", 0L,
                1_000_000L, 1).size());
    }

    @Test
    public void doesNotMatchWrongOrderAndExpiresPendingSequence() {
        SpeechAdMatcher wrongOrder = matcher("广告之后>马上回来,1");
        assertTrue(wrongOrder.accept("马上回来", 0L, 1_000_000L, 1).isEmpty());
        assertTrue(wrongOrder.accept("广告之后", 2_000_000L,
                3_000_000L, 1).isEmpty());

        SpeechAdMatcher expired = matcher("广告之后>马上回来,1");
        assertTrue(expired.accept("广告之后", 0L, 1_000_000L, 1).isEmpty());
        assertTrue(expired.accept("马上回来", 31_000_000L,
                32_000_000L, 1).isEmpty());
        assertTrue(expired.accept("广告之后", 40_000_000L,
                41_000_000L, 1).isEmpty());
        assertEquals(1, expired.accept("马上回来", 42_000_000L,
                43_000_000L, 1).size());
    }

    @Test
    public void timelineChangeClearsPendingSequence() {
        SpeechAdMatcher matcher = matcher("广告之后>马上回来,1");

        assertTrue(matcher.accept(new SpeechAdMatcher.Recognition(
                "广告之后", 0L, 1_000_000L, 1)).isEmpty());
        assertTrue(matcher.accept("马上回来", 1_000_000L,
                2_000_000L, 2).isEmpty());
        assertTrue(matcher.accept("广告之后", 3_000_000L,
                4_000_000L, 2).isEmpty());
        assertEquals(1, matcher.accept("马上回来", 4_000_000L,
                5_000_000L, 2).size());
    }

    @Test
    public void enforcesCooldownForRepeatedMatches() {
        SpeechAdMatcher matcher = matcher("广告,1");

        assertEquals(1, matcher.accept("广告", 0L, 1_000_000L, 1).size());
        assertTrue(matcher.accept("广告", 20_000_000L,
                21_000_000L, 1).isEmpty());
        assertEquals(1, matcher.accept("广告", 32_000_000L,
                33_000_000L, 1).size());
    }

    @Test
    public void ignoresInvalidIntervalsAndOversizedRecognition() {
        SpeechAdMatcher matcher = matcher("广告,1");

        assertTrue(matcher.accept("广告", -1L, 1_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("广告", 1_000_000L,
                1_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("a".repeat(
                SpeechAdMatcher.MAX_RECOGNIZED_TEXT_CODE_POINTS + 1),
                2_000_000L, 3_000_000L, 1).isEmpty());
    }

    @Test
    public void firstLiteralSplitAcrossResultsKeepsOriginalTimestamp() {
        SpeechAdMatcher matcher = matcher("广告之后*>马上回来,[2,30]");
        assertTrue(matcher.accept("广告", 10_000_000L, 11_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("之后", 12_000_000L, 13_000_000L, 1).isEmpty());
        SpeechAdMatcher.Match match = matcher.accept("马上回来", 14_000_000L,
                15_000_000L, 1).get(0);
        assertEquals(10_000_000L, match.firstStartUs());
        assertEquals(8_000L, match.candidateStartMs());
    }

    @Test
    public void unfinishedFirstSegmentCannotSurviveTheSequenceWindow() {
        SpeechAdMatcher matcher = matcher("广告*回来,30");
        assertTrue(matcher.accept("广告", 0L, 1_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("回来", 31_000_000L, 32_000_000L, 1).isEmpty());
    }

    @Test
    public void sequenceWindowIncludesTheEndOfTheLastRecognition() {
        SpeechAdMatcher matcher = matcher("广告>回来,30");
        assertTrue(matcher.accept("广告", 0L, 1_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("回来", 29_000_000L, 31_000_000L, 1).isEmpty());
    }

    @Test
    public void staleOverlappingCallbacksCannotCompleteASequence() {
        SpeechAdMatcher matcher = matcher("广告>回来,30");
        assertTrue(matcher.accept("广告", 10_000_000L, 12_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("回来", 11_000_000L, 13_000_000L, 1).isEmpty());
        assertEquals(1, matcher.accept("回来", 13_000_000L, 14_000_000L, 1).size());
    }

    @Test
    public void anyTimelineTokenIncludingMinValueIsARealGeneration() {
        SpeechAdMatcher matcher = matcher("广告>回来,30");
        assertTrue(matcher.accept("广告", 0L, 1_000_000L, Integer.MIN_VALUE).isEmpty());
        assertTrue(matcher.accept("回来", 2_000_000L, 3_000_000L, 5).isEmpty());
    }

    @Test
    public void discardedRecognitionBreaksTextContinuity() {
        SpeechAdMatcher matcher = matcher("广告*回来,30");
        assertTrue(matcher.accept("广告", 0L, 1_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("x".repeat(4_097), 2_000_000L, 3_000_000L, 1).isEmpty());
        assertTrue(matcher.accept("回来", 4_000_000L, 5_000_000L, 1).isEmpty());
    }

    @Test
    public void wildcardAllowsEmptyAndMultilineGapsButLiteralOrderMatters() {
        assertEquals(1, matcher("本片*冠名,25").accept("本片冠名", 0, 1_000, 1).size());
        assertEquals(1, matcher("本片*冠名,25").accept("本片\n由品牌\n冠名", 0, 1_000, 1).size());
        assertTrue(matcher("本片*冠名,25").accept("冠名本片", 0, 1_000, 1).isEmpty());
        assertTrue(matcher("本片冠名,25").accept("本片由品牌冠名", 0, 1_000, 1).isEmpty());
        assertEquals(1, matcher("广告>回来,30").accept("广告之后马上回来", 0, 1_000, 1).size());
    }

    @Test
    public void resetClearsCooldownAndSequenceState() {
        SpeechAdMatcher matcher = matcher("广告,1");
        assertEquals(1, matcher.accept("广告", 0, 1_000, 1).size());
        matcher.reset();
        assertEquals(1, matcher.accept("广告", 0, 1_000, 1).size());
        matcher.reset(2);
        assertEquals(2, matcher.timelineToken());
        assertEquals(1, matcher.accept("广告", 0, 1_000, 2).size());
    }

    @Test
    public void cooldownBoundaryIsInclusiveAndPerRule() {
        SpeechAdMatcher matcher = matcher("广告,1\n赞助,1");
        assertEquals(1, matcher.accept("广告", 0, 1_000_000L, 1).size());
        assertEquals(1, matcher.accept("赞助", 2_000_000L, 3_000_000L, 1).size());
        assertTrue(matcher.accept("广告", 30_000_000L, 30_999_999L, 1).isEmpty());
        assertEquals(1, matcher.accept("广告", 31_000_000L, 32_000_000L, 1).size());
    }

    @Test
    public void boundedHistoryDropsOldTextAndStillFindsNewMatches() {
        SpeechAdMatcher matcher = matcher("广告*回来,30");
        assertTrue(matcher.accept("广告", 0, 1_000, 1).isEmpty());
        for (int i = 1; i <= 4; i++) {
            assertTrue(matcher.accept("x".repeat(4_096), i * 1_000L,
                    (i + 1) * 1_000L, 1).isEmpty());
        }
        assertTrue(matcher.accept("回来", 5_000, 6_000, 1).isEmpty());
        assertEquals(1, matcher.accept("广告马上回来", 6_000, 7_000, 1).size());
    }

    private static SpeechAdMatcher matcher(String rule) {
        return new SpeechAdMatcher(SpeechAdRuleCodec.parse(rule));
    }
}
