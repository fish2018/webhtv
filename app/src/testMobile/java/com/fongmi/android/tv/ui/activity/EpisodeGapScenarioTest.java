package com.fongmi.android.tv.ui.activity;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.ui.helper.EpisodeSeasonPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * 测试有缺口的集数列表场景（如源站返回 [1..11, 13, 14]，缺12集）
 *
 * 问题背景：
 * - 源站返回的集数列表可能有缺口（如跳过第12集）
 * - 旧逻辑在某些分支用 index+1 作为集数，会把 index=11 的第13集当成第12集
 * - 修复后应该优先使用源文件的真实编号（从文件名提取）
 */
public class EpisodeGapScenarioTest {

    @Test
    public void linearEpisodeNumber_preservesSourceNumberWhenGapExists() {
        // 场景：源站返回 [11.mp4, 13.mkv, 14.mkv]（缺12集）
        // "13.mkv" 在 index=1，应该保留编号 13，不应该被当成 12

        int sourceNumber = 13;  // 从 "13.mkv" 提取的编号
        int index = 1;          // 在列表中的位置（0-based，前面只有11.mp4）

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 期望：保留源编号 13（因为 13 >= 1+1 = 2）
        assertEquals(13, result);
    }

    @Test
    public void linearEpisodeNumber_afterLongSequenceThenGap() {
        // 场景：[1..11, 13, 14...]，第一个13在index=11（前面有11个文件）
        int sourceNumber = 13;
        int index = 11;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 期望：13（因为 13 >= 11+1 = 12）
        assertEquals(13, result);
    }

    @Test
    public void linearEpisodeNumber_boundaryCase_sourceEqualsIndexPlusOne() {
        // 边界：sourceNumber 刚好等于 index+1（没有缺口）
        int sourceNumber = 12;
        int index = 11;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 12 >= 12 → true → 保留 12
        assertEquals(12, result);
    }

    @Test
    public void linearEpisodeNumber_trustsSourceNumberEvenWhenSmall() {
        // 新逻辑：文件名有集号时，无条件信任它
        // 真实场景：S01E19 在 index=20 时，应该返回19而非21
        int sourceNumber = 1;
        int index = 5;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // sourceNumber > 0 → 直接返回 1
        assertEquals(1, result);
    }

    @Test
    public void linearEpisodeNumber_negativeIndexDefaultsToSourceNumber() {
        // index < 0 时，只要 sourceNumber > 0 就使用它
        int sourceNumber = 42;
        int index = -1;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 42 > 0 && (-1 < 0 || ...) → true → 返回 42
        assertEquals(42, result);
    }

    @Test
    public void linearEpisodeNumber_zeroSourceWithNegativeIndex() {
        // sourceNumber <= 0 且 index < 0 时，返回 -1（无法推断）
        int sourceNumber = 0;
        int index = -1;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // sourceNumber <= 0, index < 0 → 返回 -1
        assertEquals(-1, result);
    }

    @Test
    public void linearEpisodeNumber_largeGap() {
        // 大缺口场景：[1..10, 50, 51, 52]（跳过 11-49）
        int sourceNumber = 50;
        int index = 10;  // 前面有10个文件 (1-10)

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 50 >= 10+1 = 11 → true → 保留 50
        assertEquals(50, result);
    }

    @Test
    public void linearEpisodeNumber_continuousSequence() {
        // 正常连续场景：[1, 2, 3, 4, 5]
        // 验证修复不会破坏正常场景

        for (int i = 0; i < 5; i++) {
            int sourceNumber = i + 1;
            int index = i;
            int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);
            // 每个都应该保留源编号（sourceNumber >= index+1）
            assertEquals(sourceNumber, result);
        }
    }
}
