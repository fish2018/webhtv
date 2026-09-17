package com.fongmi.android.tv.ui.detail;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 详情页模式 Controller 源码结构测试。
 * <p>
 * 用源码字符串断言锁定三种模式的布局差异实现。
 */
public class DetailModeControllerTest {

    private Path findMainJavaPath() throws IOException {
        Path current = Paths.get(".").toAbsolutePath().normalize();
        Path mainJava = current.resolve("app/src/main/java");
        if (!Files.exists(mainJava)) mainJava = current.resolve("src/main/java");
        if (!Files.exists(mainJava)) throw new IOException("Cannot find main java source path from " + current);
        return mainJava;
    }

    @Test
    public void fusionDetailController_hasCorrectVisibilityLogic() throws Exception {
        Path controllerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "detail", "FusionDetailController.java"));
        String source = new String(Files.readAllBytes(controllerPath), StandardCharsets.UTF_8);

        // 沉浸融合模式应显示 playerPanel 和 fusionActions，隐藏 heroSpacer 和 detailActions
        assertTrue("FusionDetailController must show playerPanel",
                source.contains("binding.playerPanel.setVisibility(View.VISIBLE)"));
        assertTrue("FusionDetailController must hide heroSpacer",
                source.contains("binding.heroSpacer.setVisibility(View.GONE)"));
        assertTrue("FusionDetailController must show fusionActions",
                source.contains("binding.fusionActions.setVisibility(View.VISIBLE)"));
        assertTrue("FusionDetailController must hide detailActions",
                source.contains("binding.detailActions.setVisibility(View.GONE)"));
    }

    @Test
    public void enhancedDetailController_hasCorrectVisibilityLogic() throws Exception {
        Path controllerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "detail", "EnhancedDetailController.java"));
        String source = new String(Files.readAllBytes(controllerPath), StandardCharsets.UTF_8);

        // 炫彩详情模式应隐藏 playerPanel 和 fusionActions，显示 heroSpacer 和 detailActions
        assertTrue("EnhancedDetailController must hide playerPanel",
                source.contains("binding.playerPanel.setVisibility(View.GONE)"));
        assertTrue("EnhancedDetailController must show heroSpacer",
                source.contains("binding.heroSpacer.setVisibility(View.VISIBLE)"));
        assertTrue("EnhancedDetailController must hide fusionActions",
                source.contains("binding.fusionActions.setVisibility(View.GONE)"));
        assertTrue("EnhancedDetailController must show detailActions",
                source.contains("binding.detailActions.setVisibility(View.VISIBLE)"));
    }

    @Test
    public void playerDetailController_hasCorrectVisibilityLogic() throws Exception {
        Path controllerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "detail", "PlayerDetailController.java"));
        String source = new String(Files.readAllBytes(controllerPath), StandardCharsets.UTF_8);

        // 详情直放模式应隐藏 playerPanel 和 fusionActions，显示 heroSpacer 和 detailActions（与炫彩相同）
        assertTrue("PlayerDetailController must hide playerPanel",
                source.contains("binding.playerPanel.setVisibility(View.GONE)"));
        assertTrue("PlayerDetailController must show heroSpacer",
                source.contains("binding.heroSpacer.setVisibility(View.VISIBLE)"));
        assertTrue("PlayerDetailController must hide fusionActions",
                source.contains("binding.fusionActions.setVisibility(View.GONE)"));
        assertTrue("PlayerDetailController must show detailActions",
                source.contains("binding.detailActions.setVisibility(View.VISIBLE)"));
    }

    @Test
    public void playerDetailController_bindsPlaybackServiceWithoutAutoPlay() throws Exception {
        Path controllerPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "detail", "PlayerDetailController.java"));
        String source = Files.readString(controllerPath, StandardCharsets.UTF_8);

        assertTrue("detail direct-play must bind PlaybackService before the explicit play click",
                source.contains("public boolean shouldBindPlaybackService()")
                        && source.contains("return true;"));
        assertTrue("binding PlaybackService must not enable automatic playback",
                source.contains("protected boolean autoPlay()")
                        && source.contains("return false; // 详情直放必须由用户点击播放"));
    }

    @Test
    public void tmdbDetailActivity_delegatesToModeController() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = new String(Files.readAllBytes(activityPath), StandardCharsets.UTF_8);

        // Activity 应创建三种 Controller 并调用它们的 applyInitialLayout
        assertTrue("TmdbDetailActivity must have initModeController method",
                source.contains("private void initModeController()"));
        assertTrue("TmdbDetailActivity must create FusionDetailController",
                source.contains("new FusionDetailController(host)"));
        assertTrue("TmdbDetailActivity must create EnhancedDetailController",
                source.contains("new EnhancedDetailController(host)"));
        assertTrue("TmdbDetailActivity must create PlayerDetailController",
                source.contains("new PlayerDetailController(host)"));
        assertTrue("TmdbDetailActivity must call applyInitialLayout",
                source.contains("modeController.applyInitialLayout()"));

        // Activity 不应在 initPage 里直接判断模式设置可见性（已迁移到 Controller）
        int initPageStart = source.indexOf("private void initPage()");
        int initPageEnd = source.indexOf("private void initModeController()", initPageStart);
        if (initPageEnd < 0) initPageEnd = source.indexOf("private void setupOverviewInteraction()", initPageStart);
        String initPageBody = source.substring(initPageStart, initPageEnd);

        // 这些旧的模式判断应该已从 initPage 删除
        assertTrue("initPage should not set playerPanel visibility based on mode (delegated to Controller)",
                !initPageBody.contains("binding.playerPanel.setVisibility(isFusionMode()"));
        assertTrue("initPage should not set heroSpacer visibility based on mode (delegated to Controller)",
                !initPageBody.contains("binding.heroSpacer.setVisibility(isFusionMode()"));
        assertTrue("initPage should not set fusionActions visibility based on mode (delegated to Controller)",
                !initPageBody.contains("binding.fusionActions.setVisibility(isFusionMode()"));
        assertTrue("initPage should not set detailActions visibility based on mode (delegated to Controller)",
                !initPageBody.contains("binding.detailActions.setVisibility(isFusionMode()"));
    }

    @Test
    public void playerDetailMode_keepsFullscreenInlinePlayback() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(activityPath, StandardCharsets.UTF_8);
        String onPlayBody = methodBody(source, "private void onPlay()");
        String detailModeBody = methodBody(source, "private int getDetailMode()");

        // 详情直放必须进入内嵌全屏播放器，不能因为播放载入界面误判成融合模式。
        assertTrue("detail-player mode must keep fullscreen inline playback",
                onPlayBody.contains("modeController.play();")
                        && !onPlayBody.contains("isFusionMode()")
                        && !onPlayBody.contains("isPlayerMode()"));
        assertTrue("detail-player mode must restore the selected mode when no intent mode marker exists",
                detailModeBody.contains("return Setting.getDetailOpenMode();")
                        && !detailModeBody.contains("getIntent().getBooleanExtra(\"fusion\", false) ? Setting.DETAIL_OPEN_FUSION : Setting.DETAIL_OPEN_ENHANCED"));
    }

    @Test
    public void playerDetailMode_entersFullscreenBeforeStartingPlayback() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(activityPath, StandardCharsets.UTF_8);
        String playBody = methodBody(source, "private void playDetailFullscreen()");

        int enterFullscreen = playBody.indexOf("enterInlineFullscreen();");
        int startPlayback = playBody.indexOf("if (!current) playInline();");
        assertTrue("new detail-player playback must enter fullscreen before playback starts",
                enterFullscreen >= 0 && startPlayback > enterFullscreen);
        assertTrue("detail-player must not retain a first-frame pending transition",
                playBody.contains("detailPlayerFullscreenPending = false;")
                        && !playBody.contains("detailPlayerFullscreenPending = !current;"));
    }

    @Test
    public void playerDetailMode_reusesCurrentPlaybackWithoutReloading() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(activityPath, StandardCharsets.UTF_8);
        String playBody = methodBody(source, "private void playDetailFullscreen()");
        String inlineBody = methodBody(source, "private void playInline(long resumePosition, String failedUrl, String failureMessage)");

        int enterFullscreen = playBody.indexOf("enterInlineFullscreen();");
        int startPlayback = playBody.indexOf("if (!current) playInline();");
        assertTrue("current playback may enter fullscreen immediately while new playback resolves once",
                enterFullscreen >= 0
                        && startPlayback > enterFullscreen
                        && !inlineBody.contains("stopInlinePlayerForReload();"));
    }

    @Test
    public void playerDetailMode_waitsForExplicitPlaybackAction() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(activityPath, StandardCharsets.UTF_8);
        String autoPlayBody = methodBody(source, "private void maybeAutoPlayInline()");

        // 详情直放只定义点击播放后的全屏行为，进入详情页本身不能触发播放。
        assertTrue("auto playback eligibility must be delegated to the active mode controller",
                autoPlayBody.contains("modeController.shouldAutoPlay()")
                        && !autoPlayBody.contains("isFusionMode()")
                        && !autoPlayBody.contains("isAutoPlayMode()")
                        && autoPlayBody.contains("binding.playerPanel.post(this::onPlay);"));
    }

    @Test
    public void playbackHistoryPublishing_isDelegatedToModeController() throws Exception {
        Path activityPath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", "TmdbDetailActivity.java"));
        String source = Files.readString(activityPath, StandardCharsets.UTF_8);
        String body = methodBody(source, "private void updateInlineHistory(Episode item)");

        assertTrue("history publishing eligibility must be delegated to the active mode controller",
                body.contains("modeController.shouldPublishPlaybackHistory()")
                        && !body.contains("isFusionMode() || isPlayerMode()"));
    }

    private String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature + " is missing from TmdbDetailActivity", start >= 0);
        int end = source.indexOf("\n    private void ", start + signature.length());
        if (end < 0) end = source.length();
        return source.substring(start, end);
    }
}
