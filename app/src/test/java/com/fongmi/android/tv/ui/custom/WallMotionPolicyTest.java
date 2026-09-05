package com.fongmi.android.tv.ui.custom;

import static com.fongmi.android.tv.ui.custom.WallMotionPolicy.TYPE_GIF;
import static com.fongmi.android.tv.ui.custom.WallMotionPolicy.TYPE_RES;
import static com.fongmi.android.tv.ui.custom.WallMotionPolicy.TYPE_VIDEO;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WallMotionPolicyTest {

    @Test
    public void motionOffKeepsVideoWallStatic() {
        assertFalse("播放页关掉动效后视频壁纸不能起播，否则会抢前台播放器的硬解实例",
                WallMotionPolicy.isVideoMotion(false, TYPE_VIDEO));
        assertFalse(WallMotionPolicy.isGifMotion(false, TYPE_GIF));
    }

    @Test
    public void motionOnAnimatesMatchingWallTypeOnly() {
        assertTrue(WallMotionPolicy.isVideoMotion(true, TYPE_VIDEO));
        assertTrue(WallMotionPolicy.isGifMotion(true, TYPE_GIF));
        assertFalse("GIF 壁纸不该走视频解码器", WallMotionPolicy.isVideoMotion(true, TYPE_GIF));
        assertFalse("视频壁纸不该走 GIF 解码", WallMotionPolicy.isGifMotion(true, TYPE_VIDEO));
        assertFalse(WallMotionPolicy.isVideoMotion(true, TYPE_RES));
        assertFalse(WallMotionPolicy.isGifMotion(true, TYPE_RES));
    }

    @Test
    public void stoppedPageDefersVideoUntilResume() {
        assertTrue("后台收到换壁纸事件应记下待恢复，而不是白占一路解码器",
                WallMotionPolicy.shouldDeferVideo(true, TYPE_VIDEO, false));
        assertFalse("前台页面不需要延迟", WallMotionPolicy.shouldDeferVideo(true, TYPE_VIDEO, true));
        assertFalse("动效关闭时没有要恢复的视频", WallMotionPolicy.shouldDeferVideo(false, TYPE_VIDEO, false));
        assertFalse("GIF 壁纸不走视频恢复通道", WallMotionPolicy.shouldDeferVideo(true, TYPE_GIF, false));
        assertFalse("图片壁纸不走视频恢复通道", WallMotionPolicy.shouldDeferVideo(true, TYPE_RES, false));
    }
}
