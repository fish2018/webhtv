package com.fongmi.android.tv.ad.feedback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.M3u8Evidence;

import org.junit.Test;

import java.util.List;

public class AdIntervalMapperTest {

    private static final List<Float> UNIFORM = List.of(10f, 10f, 10f, 10f, 10f);

    @Test
    public void mapsIntervalToOverlappingSegments() {
        // [15s, 35s) 与切片 1(10-20)、2(20-30)、3(30-40) 相交
        assertEquals(List.of(1, 2, 3), AdIntervalMapper.insideIndices(UNIFORM, 15_000, 35_000));
    }

    @Test
    public void intervalFlushWithSegmentBoundaryExcludesNeighbours() {
        // [20s, 30s) 恰好是切片 2，贴边的 1 和 3 都不算相交
        assertEquals(List.of(2), AdIntervalMapper.insideIndices(UNIFORM, 20_000, 30_000));
    }

    @Test
    public void emptyOrInvalidInputYieldsNoSegments() {
        assertTrue(AdIntervalMapper.insideIndices(null, 0, 10_000).isEmpty());
        assertTrue(AdIntervalMapper.insideIndices(List.of(), 0, 10_000).isEmpty());
        // 终点不晚于起点
        assertTrue(AdIntervalMapper.insideIndices(UNIFORM, 20_000, 20_000).isEmpty());
        assertTrue(AdIntervalMapper.insideIndices(UNIFORM, 30_000, 20_000).isEmpty());
    }

    @Test
    public void toleratesNullDurationEntries() {
        List<Float> withNull = java.util.Arrays.asList(10f, null, 10f);
        // null 记为 0 时长，切片 1 退化为零宽度不与任何区间相交
        assertEquals(List.of(2), AdIntervalMapper.insideIndices(withNull, 12_000, 18_000));
    }

    @Test
    public void segmentStartAccumulatesPrecedingDurations() {
        assertEquals(0L, AdIntervalMapper.segmentStartMs(UNIFORM, 0));
        assertEquals(30_000L, AdIntervalMapper.segmentStartMs(UNIFORM, 3));
        // 下标越界时按现有切片求和，不抛异常
        assertEquals(50_000L, AdIntervalMapper.segmentStartMs(UNIFORM, 99));
    }

    @Test
    public void prefersDiscontinuityOverCrossDomainAndWindow() {
        M3u8Evidence evidence = M3u8Evidence.create(
                List.of("a.ts", "b.ts", "c.ts", "d.ts", "e.ts"),
                List.of(2),
                UNIFORM,
                List.of(false, true, false, false, false));

        AdIntervalMapper.InferredStart start = AdIntervalMapper.inferStart(evidence, 35_000, -1, 90_000);

        assertEquals(StartOrigin.DISCONTINUITY, start.origin());
        assertEquals(20_000L, start.startMs());
    }

    @Test
    public void fallsBackToCrossDomainWhenNoDiscontinuity() {
        M3u8Evidence evidence = M3u8Evidence.create(
                List.of("a.ts", "b.ts", "c.ts", "d.ts", "e.ts"),
                List.of(),
                UNIFORM,
                List.of(false, false, false, true, false));

        AdIntervalMapper.InferredStart start = AdIntervalMapper.inferStart(evidence, 45_000, -1, 90_000);

        assertEquals(StartOrigin.CROSS_DOMAIN, start.origin());
        assertEquals(30_000L, start.startMs());
    }

    @Test
    public void ignoresBoundariesAtOrAfterClickPosition() {
        // 断点在 30s，点击位置就是 30s：不能把起点设成终点
        M3u8Evidence evidence = M3u8Evidence.create(
                List.of("a.ts", "b.ts", "c.ts", "d.ts", "e.ts"),
                List.of(3),
                UNIFORM,
                List.of(false, false, false, false, false));

        AdIntervalMapper.InferredStart start = AdIntervalMapper.inferStart(evidence, 30_000, -1, 90_000);

        assertEquals(StartOrigin.FALLBACK_WINDOW, start.origin());
        assertEquals(0L, start.startMs());
    }

    @Test
    public void usesAudioCandidateWhenPlaylistHasNoSignal() {
        AdIntervalMapper.InferredStart start = AdIntervalMapper.inferStart(
                M3u8Evidence.empty(), 60_000, 42_000, 90_000);

        assertEquals(StartOrigin.AUDIO_CANDIDATE, start.origin());
        assertEquals(42_000L, start.startMs());
    }

    @Test
    public void clampsFallbackWindowToZero() {
        AdIntervalMapper.InferredStart start = AdIntervalMapper.inferStart(null, 30_000, -1, 90_000);

        assertEquals(StartOrigin.FALLBACK_WINDOW, start.origin());
        assertEquals(0L, start.startMs());
    }

    @Test
    public void appliesDefaultWindowWhenNonPositive() {
        AdIntervalMapper.InferredStart start = AdIntervalMapper.inferStart(null, 200_000, -1, 0);

        assertEquals(200_000L - AdIntervalMapper.DEFAULT_FALLBACK_WINDOW_MS, start.startMs());
    }
}
