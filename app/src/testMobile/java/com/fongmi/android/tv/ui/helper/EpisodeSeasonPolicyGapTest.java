package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * 测试有缺口的集数列表（如 [11, 13, 13, 14]）的编号计算
 */
public class EpisodeSeasonPolicyGapTest {

    @Test
    public void preservesSourceNumberWhenGapExists() {
        // 场景：源站返回 [11.mp4, 13.mkv, 13_duplicate, 14.mkv]（缺12集）
        // "13.mkv" 在 index=1，应该保留编号 13，不应该被当成 12

        int sourceNumber = 13;  // 从 "13.mkv" 提取的编号
        int index = 1;          // 在列表中的位置（0-based）

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 期望：保留源编号 13（因为 13 >= 1+1）
        assertEquals(13, result);
    }

    @Test
    public void firstEpisodeAfterGap() {
        // 场景：[1..11, 13, 14...]，第一个13在index=11
        int sourceNumber = 13;
        int index = 11;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 期望：13（因为 13 >= 11+1 = 12）
        assertEquals(13, result);
    }

    @Test
    public void boundaryCase() {
        // 边界：sourceNumber 刚好等于 index+1
        int sourceNumber = 12;
        int index = 11;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // 12 >= 12 → true → 保留 12
        assertEquals(12, result);
    }

    @Test
    public void trustsSourceNumberEvenWhenSmall() {
        // 新逻辑：只要 sourceNumber > 0，就信任它（不管它和 index 的关系）
        // 场景：多线路合并后，文件在列表中的位置可能与文件名集号不一致
        int sourceNumber = 1;
        int index = 5;

        int result = EpisodeSeasonPolicy.linearEpisodeNumber(sourceNumber, index);

        // sourceNumber > 0 → 直接返回 1（文件名的集号比列表位置更可靠）
        assertEquals(1, result);
    }
}
