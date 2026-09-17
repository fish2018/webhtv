package com.fongmi.android.tv.ui.helper;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class EpisodeCardImagePolicyTest {

    @Test
    public void wideDevicePrefersBackdropAndFallsBackToPoster() {
        assertEquals("backdrop", EpisodeCardImagePolicy.fallbackFor("backdrop", "poster", true));
        assertEquals("poster", EpisodeCardImagePolicy.fallbackFor("", "poster", true));
    }

    @Test
    public void narrowDevicePrefersPosterAndFallsBackToBackdrop() {
        assertEquals("poster", EpisodeCardImagePolicy.fallbackFor("backdrop", "poster", false));
        assertEquals("backdrop", EpisodeCardImagePolicy.fallbackFor("backdrop", "", false));
    }

    @Test
    public void missingBothRatiosYieldsEmpty() {
        assertEquals("", EpisodeCardImagePolicy.fallbackFor("", "", true));
        assertEquals("", EpisodeCardImagePolicy.fallbackFor("", "", false));
    }

    /**
     * 横图槽位只允许两种来源：当前 TMDB 条目自己的剧照，以及「压根没有 TMDB 条目」时进场 intent
     * 的横图。放宽任一侧都会回归成线上见过的两个症状：退 history wall / 已匹配时退 intent 横图会
     * 让上一条匹配的背景图挡在当前条目前面；反过来完全不退横图，宽卡片就只能吃竖海报。
     */
    @Test
    public void landscapeSlotFallsBackToIntentWallOnlyWithoutTmdbEntry() throws IOException {
        for (String flavor : new String[]{"leanback", "mobile"}) {
            String source = readActivity(flavorJava(flavor), "VideoActivity.java");
            String body = code(methodBody(source, "private String getEpisodeFallbackBackdropUrl()"));

            assertTrue(flavor + " 无 TMDB 条目时必须退 intent 的 wallPic，否则宽卡片只能吃竖海报",
                    body.contains("if (mTmdbUIAdapter == null || !mTmdbUIAdapter.isLoaded()) return getWallPic();"));
            assertTrue(flavor + " 有 TMDB 条目时只能取该条目自己的剧照，退 history wall 会带回上一条匹配的横图",
                    !body.contains("mHistory") && !body.contains("getContextWall()"));
        }

        String detail = readActivity(mainJava(), "TmdbDetailActivity.java");
        String body = code(methodBody(detail, "private String episodeFallbackLandscapeUrl()"));

        assertTrue("已有匹配条目时必须就此收口，不能退 intent 的 tmdb_backdrop",
                body.indexOf("if (matchedTmdbItem != null) return") < body.indexOf("getBackdropText()"));
        assertTrue("完全没有匹配时才退 intent 的 tmdb_backdrop，宽卡片靠它免吃竖海报",
                body.contains("return TmdbImageSelector.originalUrl(getBackdropText());"));
    }

    /** 只保留可执行代码：注释里为了解释取舍会提到被禁用的调用，不能让它们触发断言。 */
    private static String code(String body) {
        StringBuilder builder = new StringBuilder();
        for (String line : body.split("\n")) {
            if (!line.trim().startsWith("//")) builder.append(line).append('\n');
        }
        return builder.toString();
    }

    private static String methodBody(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue("找不到方法：" + signature, start >= 0);
        int end = source.indexOf("\n    }", start);
        assertTrue("方法未闭合：" + signature, end > start);
        return source.substring(start, end);
    }

    private static String readActivity(Path javaRoot, String fileName) throws IOException {
        Path path = javaRoot.resolve(Path.of("com", "fongmi", "android", "tv", "ui", "activity", fileName));
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path flavorJava(String flavor) {
        Path moduleRelative = Path.of("src", flavor, "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", flavor, "java");
    }

    private static Path mainJava() {
        return flavorJava("main");
    }
}
