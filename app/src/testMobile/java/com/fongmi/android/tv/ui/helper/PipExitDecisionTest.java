package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PipExitDecisionTest {

    @Test
    public void closeButton_whenStoppedAndNotFinishing_finishesPlayback() {
        // 点 PiP 的 ×：生命周期没回到前台，也未进入结束流程 → 应结束播放
        assertTrue(PipExitDecision.shouldFinishAfterPipExit(false, false, false));
    }

    @Test
    public void expandBackToFullscreen_keepsPlayback() {
        // 展开回全屏：生命周期已回到 STARTED 及以上 → 保留播放
        assertFalse(PipExitDecision.shouldFinishAfterPipExit(true, false, false));
    }

    @Test
    public void switchToAudioBackground_whenStopped_keepsPlayback() {
        // PiP 的“隐藏画面”动作会主动退到音频后台，不是用户点击 × 关闭。
        assertFalse(PipExitDecision.shouldFinishAfterPipExit(false, false, false, true));
    }

    @Test
    public void alreadyFinishing_doesNotRefinish() {
        // 已在结束流程中：无论前台与否都不重复触发
        assertFalse(PipExitDecision.shouldFinishAfterPipExit(false, true, false));
        assertFalse(PipExitDecision.shouldFinishAfterPipExit(true, true, false));
    }

    @Test
    public void alreadyDestroyed_doesNotRefinish() {
        // 已销毁：不再触发
        assertFalse(PipExitDecision.shouldFinishAfterPipExit(false, false, true));
        assertFalse(PipExitDecision.shouldFinishAfterPipExit(true, false, true));
    }
}
