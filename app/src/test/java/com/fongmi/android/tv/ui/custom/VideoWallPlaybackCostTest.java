package com.fongmi.android.tv.ui.custom;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 动态壁纸（视频/GIF）会额外占一路 MediaCodec 硬解实例，并按壁纸帧率重绘整个界面。
 * 这些测试锁住「播放类页面必须关掉动效」以及「后台页面必须交还解码器」两条约束——
 * 缺了它们在电视盒子上会表现为播放卡顿。
 */
public class VideoWallPlaybackCostTest {

    @Test
    public void bothFlavorsGateMotionWallpaperPerActivity() throws Exception {
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String base = readSource(flavor, "ui/base/BaseActivity.java");
            assertTrue(flavor + " BaseActivity must expose a per-activity motion wallpaper switch",
                    base.contains("protected boolean customWallMotion()"));
            assertTrue(flavor + " BaseActivity must pass the motion switch into CustomWallView",
                    base.contains("new CustomWallView(this, null).setMotionEnabled(customWallMotion())"));
        }
    }

    @Test
    public void playbackScreensDisableMotionWallpaper() throws Exception {
        assertMotionDisabled(readSource("leanback", "ui/activity/VideoActivity.java"), "leanback VideoActivity");
        assertMotionDisabled(readSource("mobile", "ui/activity/VideoActivity.java"), "mobile VideoActivity");
        assertMotionDisabled(readSource("mobile", "ui/activity/LiveActivity.java"), "mobile LiveActivity");
        assertMotionDisabled(readMainSource("ui/activity/TmdbDetailActivity.java"), "TmdbDetailActivity");
    }

    @Test
    public void wallpaperReleasesDecoderWhileBackgrounded() throws Exception {
        String source = readMainSource("ui/custom/CustomWallView.java");
        int onStop = source.indexOf("public void onStop(@NonNull LifecycleOwner owner)");
        assertTrue("CustomWallView must handle onStop to free its decoder", onStop >= 0);
        assertTrue("onStop must release the wallpaper player, not merely pause it",
                source.substring(onStop, source.indexOf("\n    }", onStop)).contains("releasePlayer();"));

        int release = source.indexOf("private void releasePlayer()");
        assertTrue("CustomWallView is missing releasePlayer", release >= 0);
        String body = source.substring(release, source.indexOf("\n    }", release));
        assertTrue("releasePlayer must remember to restore a video wall on resume",
                body.contains("videoRestorePending = isVideoWall();"));
        assertTrue("releasePlayer must actually release the ExoPlayer", body.contains("player.release();"));
        assertTrue("releasePlayer must drop the released instance", body.contains("player = null;"));

        int resume = source.indexOf("public void onResume(@NonNull LifecycleOwner owner)");
        assertTrue("CustomWallView is missing onResume", resume >= 0);
        assertTrue("onResume must re-prepare a wallpaper released while backgrounded",
                source.substring(resume, source.indexOf("\n    }", resume)).contains("restoreVideo();"));
    }

    @Test
    public void backgroundedWallpaperChangeDefersDecoderCreation() throws Exception {
        String source = readMainSource("ui/custom/CustomWallView.java");
        int load = source.indexOf("private void loadVideo(File file)");
        assertTrue("CustomWallView is missing loadVideo", load >= 0);
        String body = source.substring(load, source.indexOf("\n    }", load));
        // 后台收到换壁纸事件时建解码器会白占一路 MediaCodec，直到用户回到该页面。
        assertTrue("loadVideo must defer starting the player while the host is stopped",
                body.contains("WallMotionPolicy.shouldDeferVideo(motionEnabled, Setting.getWallType(), started)"));
        assertTrue("the deferred wall must be remembered so onResume can rebuild it",
                body.contains("videoRestorePending = true;"));
    }

    @Test
    public void deferredWallpaperKeepsItsStaticBackdrop() throws Exception {
        String source = readMainSource("ui/custom/CustomWallView.java");
        int load = source.indexOf("private void loadVideo(File file)");
        assertTrue("CustomWallView is missing loadVideo", load >= 0);
        // startVideo 不再设置底图，缓存缺失时若直接 setImageDrawable(null) 壁纸会全空。
        assertTrue("loadVideo must fall back to a solid colour when the frame cache is missing",
                source.substring(load, source.indexOf("\n    }", load)).contains("cache != null ? cache : new ColorDrawable(DEFAULT_WALL_COLOR)"));

        int restore = source.indexOf("private void restoreVideo()");
        assertTrue("CustomWallView is missing restoreVideo", restore >= 0);
        String body = source.substring(restore, source.indexOf("\n    }", restore));
        // 尚未 ready 时清掉标志会丢掉这次恢复机会，视频壁纸要等下一次换壁纸事件才回来。
        // 守卫缺失时 indexOf 返回 -1 会让顺序断言空洞通过，所以先断言它存在。
        int guard = body.indexOf("if (!isReady()) return;");
        int clear = body.indexOf("videoRestorePending = false;");
        assertTrue("restoreVideo must bail out before touching the pending flag when not ready", guard >= 0);
        assertTrue("restoreVideo must clear the pending flag once it is ready", clear >= 0);
        assertTrue("restoreVideo must keep the pending flag until the view is ready", guard < clear);
        // 后台把视频壁纸换成图片后回到前台，不能再去 prepare 一个已经不是视频的文件。
        assertTrue("restoreVideo must not rebuild a decoder once the wall is no longer a video",
                body.contains("if (!isVideoWall()) return;"));
    }

    @Test
    public void wallpaperLoadRoutesMotionThroughPolicy() throws Exception {
        String source = readMainSource("ui/custom/CustomWallView.java");
        int load = source.indexOf("private void load()");
        assertTrue("CustomWallView is missing load", load >= 0);
        String body = source.substring(load, source.indexOf("\n    }", load));
        // 门控丢了 motionEnabled，播放页就会重新起播壁纸视频——本次修复的核心就在这两行。
        assertTrue("video wall must be gated by the per-activity motion switch",
                body.contains("WallMotionPolicy.isVideoMotion(motionEnabled, type)"));
        assertTrue("gif wall must be gated by the per-activity motion switch",
                body.contains("WallMotionPolicy.isGifMotion(motionEnabled, type)"));
    }

    @Test
    public void wallpaperTracksStartedState() throws Exception {
        String source = readMainSource("ui/custom/CustomWallView.java");
        int onStart = source.indexOf("public void onStart(@NonNull LifecycleOwner owner)");
        assertTrue("CustomWallView must observe onStart to know when a decoder may be built", onStart >= 0);
        assertTrue("onStart must mark the host as started, otherwise video walls never start",
                source.substring(onStart, source.indexOf("\n    }", onStart)).contains("started = true;"));

        int onStop = source.indexOf("public void onStop(@NonNull LifecycleOwner owner)");
        assertTrue("onStop must clear the started flag",
                source.substring(onStop, source.indexOf("\n    }", onStop)).contains("started = false;"));
    }

    @Test
    public void wallpaperResetClearsBufferingMediaItem() throws Exception {
        String source = readMainSource("ui/custom/CustomWallView.java");
        int stop = source.indexOf("private void stop()");
        assertTrue("CustomWallView is missing stop", stop >= 0);
        String body = source.substring(stop, source.indexOf("\n    }", stop));
        // buffering 时 isPlaying() 为 false，用它当前置条件会把旧 MediaItem 留在播放器里。
        assertFalse("stop must not gate clearMediaItems on isPlaying", body.contains("player.isPlaying()"));
        assertTrue("stop must still halt the wallpaper player", body.contains("player.stop();"));
        assertTrue("stop must still drop the old media item", body.contains("player.clearMediaItems();"));
    }

    @Test
    public void playerOnlyScreensKeepTheWallpaperLayerOff() throws Exception {
        // 这些页面靠 customWall()==false 完全不建壁纸视图，所以没有 customWallMotion 覆写。
        // 一旦有人把它们改回 true，动效会无声地回来，故在此加锁。
        assertWallDisabled(readSource("leanback", "ui/activity/LiveActivity.java"), "leanback LiveActivity");
        assertWallDisabled(readSource("leanback", "ui/activity/CastActivity.java"), "leanback CastActivity");
        assertWallDisabled(readMainSource("ui/activity/AudioActivity.java"), "AudioActivity");
    }

    private static void assertWallDisabled(String source, String label) {
        int method = source.indexOf("protected boolean customWall()");
        assertTrue(label + " is missing customWall", method >= 0);
        String body = source.substring(method, source.indexOf("\n    }", method));
        assertTrue(label + " must keep the wallpaper layer off, or it needs customWallMotion() == false too",
                body.contains("return false;"));
    }

    private static void assertMotionDisabled(String source, String label) {
        int method = source.indexOf("protected boolean customWallMotion()");
        assertTrue(label + " must override customWallMotion to spare the decoder", method >= 0);
        assertTrue(label + " must disable motion wallpaper while a player is on screen",
                source.substring(method, source.indexOf("\n    }", method)).contains("return false;"));
    }

    private static String readMainSource(String file) throws Exception {
        return readSource("main", file);
    }

    private static String readSource(String sourceSet, String file) throws Exception {
        Path app = Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
        Path path = app.resolve(Path.of("src", sourceSet, "java", "com", "fongmi", "android", "tv", file));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
