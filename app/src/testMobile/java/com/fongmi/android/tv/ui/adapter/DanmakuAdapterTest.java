package com.fongmi.android.tv.ui.adapter;

import static org.junit.Assert.assertEquals;

import com.fongmi.android.tv.bean.DanmakuTitle;

import org.junit.Test;

public class DanmakuAdapterTest {

    @Test
    public void parseEpisodeSupportsCommonDanmakuNames() {
        assertEquals(3, DanmakuTitle.parseEpisode("第03集"));
        assertEquals(12, DanmakuTitle.parseEpisode("S01E12"));
        assertEquals(7, DanmakuTitle.parseEpisode("Episode 07"));
    }
}
