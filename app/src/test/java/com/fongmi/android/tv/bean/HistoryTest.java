package com.fongmi.android.tv.bean;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryTest {

    @Test
    public void getVodId_toleratesIncompleteKey() {
        History history = new History();
        history.setKey("incomplete");

        assertEquals("", history.getVodId());
    }

    @Test
    public void getVodId_excludesConfigIdFromPushHistoryKey() {
        History history = new History();
        history.setKey("push_agent@@@https://pan.quark.cn/s/eab19bde98be@@@48");

        assertEquals("https://pan.quark.cn/s/eab19bde98be", history.getVodId());
    }

    @Test
    public void getVodId_toleratesPreviouslyDuplicatedConfigSuffix() {
        History history = new History();
        history.setKey("push_agent@@@https://pan.quark.cn/s/eab19bde98be@@@48@@@48");

        assertEquals("https://pan.quark.cn/s/eab19bde98be", history.getVodId());
    }

    @Test
    public void isSameContent_keepsUnchangedCopy() {
        History history = coverHistory();

        assertTrue(history.isSameContent(history.copy()));
    }

    @Test
    public void isSameContent_refreshesPositionWithoutTimestampChange() {
        History history = coverHistory();
        History updated = history.copy();
        updated.setPosition(90000);

        assertFalse(history.isSameContent(updated));
    }

    @Test
    public void isSameContent_refreshesDurationWithoutTimestampChange() {
        History history = coverHistory();
        History updated = history.copy();
        updated.setDuration(60000);

        assertFalse(history.isSameContent(updated));
    }

    @Test
    public void isSameContent_refreshesEpisodeWithoutTimestampChange() {
        History history = coverHistory();
        History updated = history.copy();
        updated.setVodRemarks("Episode 2");

        assertFalse(history.isSameContent(updated));
    }

    private static History coverHistory() {
        History history = new History();
        history.setKey("test@@@cover");
        history.setVodName("Title");
        history.setVodPic("cover.jpg");
        history.setVodRemarks("Episode 1");
        history.setPosition(30000);
        history.setDuration(120000);
        history.setCreateTime(1000);
        return history;
    }
}
