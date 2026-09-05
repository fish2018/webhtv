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
}
