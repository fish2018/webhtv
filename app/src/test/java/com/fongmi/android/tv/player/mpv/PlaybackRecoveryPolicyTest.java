package com.fongmi.android.tv.player.mpv;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlaybackRecoveryPolicyTest {
    private final PlaybackRecoveryPolicy.Target target =
            new PlaybackRecoveryPolicy.Target(100, 10123, 900, "com.example.player");

    @Test public void onlyPersistentForegroundStallsPromptOnce() {
        assertFalse(PlaybackRecoveryPolicy.shouldPrompt(8099, 100, true, false));
        assertTrue(PlaybackRecoveryPolicy.shouldPrompt(8100, 100, true, false));
        assertFalse(PlaybackRecoveryPolicy.shouldPrompt(20000, 100, false, false));
        assertFalse(PlaybackRecoveryPolicy.shouldPrompt(20000, 100, true, true));
        assertFalse(PlaybackRecoveryPolicy.shouldPrompt(20000, 0, true, false));
        assertFalse(PlaybackRecoveryPolicy.shouldPrompt(50, 100, true, false));
    }

    @Test public void exactOwnMainProcessMayBeEnded() {
        assertTrue(PlaybackRecoveryPolicy.mayTerminate(target, target, 200, 10123, "com.example.player"));
    }

    @Test public void reusedPidOtherUidOrOtherProcessCanNeverBeEnded() {
        assertFalse(PlaybackRecoveryPolicy.mayTerminate(target,
                new PlaybackRecoveryPolicy.Target(100, 10123, 901, "com.example.player"), 200, 10123, "com.example.player"));
        assertFalse(PlaybackRecoveryPolicy.mayTerminate(target, target, 200, 9999, "com.example.player"));
        assertFalse(PlaybackRecoveryPolicy.mayTerminate(target, target, 200, 10123, "other.package"));
        assertFalse(PlaybackRecoveryPolicy.mayTerminate(target, target, 100, 10123, "com.example.player"));
        assertFalse(PlaybackRecoveryPolicy.mayTerminate(target, null, 200, 10123, "com.example.player"));
        assertFalse(PlaybackRecoveryPolicy.mayTerminate(null, target, 200, 10123, "com.example.player"));
    }

    @Test public void unknownProcessGenerationIsNeverAccepted() {
        PlaybackRecoveryPolicy.Target unknown = new PlaybackRecoveryPolicy.Target(100, 10123, -1, "com.example.player");
        assertFalse(PlaybackRecoveryPolicy.mayTerminate(unknown, unknown, 200, 10123, "com.example.player"));
    }

    @Test public void procStatParserUsesField22NotCommWhitespaceOrParentheses() {
        String tail = " S" + " 0".repeat(18) + " 123456 999 888";
        assertEquals(123456, PlaybackRecoveryPolicy.processStartTicks("100 (player (main))" + tail));
        assertEquals(-1, PlaybackRecoveryPolicy.processStartTicks("100 broken"));
        assertEquals(-1, PlaybackRecoveryPolicy.processStartTicks("100 (p) S 1"));
        assertEquals(-1, PlaybackRecoveryPolicy.processStartTicks("100 (p)" + tail.replace("123456", "bad")));
    }

    @Test public void procStatusUsesRealUidWithTabsOrSpaces() {
        assertEquals(10123, PlaybackRecoveryPolicy.processUid("Name:\tplayer\nUid:\t10123\t10123\t10123\t10123\nGid:\t99"));
        assertEquals(10123, PlaybackRecoveryPolicy.processUid("Uid: 10123 10124 10125 10126\n"));
        assertEquals(0, PlaybackRecoveryPolicy.processUid("Uid:\t0\t0\t0\t0\n"));
    }

    @Test public void missingOrTruncatedUidIsNotAProcessIdentity() {
        assertEquals(-1, PlaybackRecoveryPolicy.processUid(null));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid(""));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("UidOther: 10123 10123 10123 10123\n"));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid: 10123 10123 10123\n"));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid:\n"));
    }

    @Test public void invalidUidNumbersFailClosed() {
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid: -1 10123 10123 10123"));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid: +10123 10123 10123 10123"));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid: 4294967295 10123 10123 10123"));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid: 10123 bad 10123 10123"));
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid: 10123 10123 10123 10123 extra"));
    }

    @Test public void duplicateUidFieldsFailClosed() {
        assertEquals(-1, PlaybackRecoveryPolicy.processUid("Uid: 10123 10123 10123 10123\nUid: 10123 10123 10123 10123"));
    }
}
