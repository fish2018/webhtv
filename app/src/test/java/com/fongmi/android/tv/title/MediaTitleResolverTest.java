package com.fongmi.android.tv.title;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class MediaTitleResolverTest {

    @Test
    public void queryCleanedTitlesExposesCodeCleanedFallbackCandidates() {
        List<String> titles = new MediaTitleResolver().queryCleanedTitles(
                MediaTitleRequest.builder()
                        .rawTitle("玩偶 | 虎斑2 云秀行（真彩） 4K HDR 60帧 更新至第20集")
                        .build(),
                3);

        assertEquals(List.of("云秀行"), titles);
    }
}
