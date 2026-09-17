package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

/**
 * 选集弹层季度行的接线约束。TV 端有两套并行的焦点机制：声明式的 setNextFocus*(ID) 与按键处理
 * onXxxKey()。两套不一致会让遥控在某一行卡住，那比没有季度按钮严重得多——选集界面会直接不可用。
 */
public class EpisodeListSeasonRowTest {

    @Test
    public void seasonRowIsWiredIntoBothFocusMechanisms() throws IOException {
        String dialog = read(findLeanbackJavaPath().resolve(
                Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java")));

        assertTrue("季度行必须有独立的按键处理，否则上下键会被吞掉",
                dialog.contains("private boolean onSeasonKey(KeyEvent event)")
                        && dialog.contains("seasonAdapter.setOnKeyListener")
                        && dialog.contains("binding.season.setOnKeyListener"));
        assertTrue("季度行向上必须回线路、向下必须落分段或选集",
                dialog.contains("focusFlag();")
                        && dialog.contains("private void focusSeason()"));
        assertTrue("线路向下必须优先落季度行，否则季度行会被跳过无法聚焦",
                dialog.contains("if (isVisible(binding.season) && seasonAdapter.getItemCount() > 0) focusSeason();"));
        assertTrue("分段行向上必须落季度行而不是直接跳回线路",
                dialog.contains("private void focusUpperFromArray()")
                        && dialog.contains("focusUpperFromArray();"));
        assertTrue("声明式焦点 ID 必须与按键处理同口径：线路->季度->分段->选集",
                dialog.contains("flagAdapter.setNextFocusDown(hasSeason ? R.id.season")
                        && dialog.contains("seasonAdapter.setNextFocus(R.id.flag,")
                        && dialog.contains("arrayAdapter.setNextFocus(hasSeason ? R.id.season : R.id.flag, R.id.episode);"));
    }

    @Test
    public void seasonRowReusesSegmentLabelsAndGlobalEpisodeNumbers() throws IOException {
        String dialog = read(findLeanbackJavaPath().resolve(
                Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java")));

        assertTrue("分段标签必须加季偏移，与详情页的全局集号一致",
                dialog.contains("int offset = seasonOffset();")
                        && dialog.contains("items.add((offset + i + 1) + \"-\" + (offset + end));"));
        assertTrue("弹层自建 adapter 必须收到兜底图，否则无 TMDB 数据的集是无图卡片",
                dialog.contains("episodeAdapter.setFallbackStillUrl(fallbackStillUrl);"));
        assertTrue("季度切分必须复用共享策略，与手机版和详情页同口径",
                dialog.contains("EpisodeSeasonSegments.build(")
                        && dialog.contains("EpisodeSeasonSegments.slice(")
                        && dialog.contains("EpisodeSeasonSegments.isOther("));
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "leanback", "java");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        return Files.exists(moduleRelative) ? moduleRelative : Path.of("app", "src", "mobile", "java");
    }

    /**
     * 手机版分段标签加季偏移时必须区分正/倒序。EpisodeRangePolicy 在 reverse 下用 size-start
     * 口径生成倒序标签，若偏移换算只按正向 start+1 计算，会把倒序标签覆盖成错误的正序编号。
     */
    @Test
    public void mobileSeasonOffsetLabelsRespectReverseOrder() throws IOException {
        String dialog = read(findMobileJavaPath().resolve(
                Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java")));

        assertTrue("季偏移标签必须按 reverse 分别换算，且需要 size 参与倒序计算",
                dialog.contains("int labelStart = offset + (reverse ? size - group.start : group.start + 1);")
                        && dialog.contains("int labelEnd = offset + (reverse ? size - group.end + 1 : group.end);"));
        assertTrue("调用处必须把当前季的集数传入，倒序换算依赖它",
                dialog.contains("withGlobalEpisodeLabels(groups, seasonOffset(), episodes.size())"));
    }

    /**
     * 切不出内容而回退成完整列表时，必须同步清掉选中季。否则 seasonOffset() 仍返回该季起点，
     * 会在已是全局编号的列表上再叠一次偏移，分段标签与实际内容错位。
     */
    @Test
    public void fallbackToFullListAlsoClearsSelectedSeason() throws IOException {
        String mobile = read(findMobileJavaPath().resolve(
                Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java")));
        String leanback = read(findLeanbackJavaPath().resolve(
                Path.of("com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeListDialog.java")));

        for (String source : new String[]{mobile, leanback}) {
            assertTrue("回退完整列表时必须重置选中季，避免偏移与内容错位",
                    source.contains("if (!sliced.isEmpty()) return sliced;")
                            && source.contains("selectedSeason = Integer.MIN_VALUE;")
                            && source.contains("return sourceEpisodes;"));
        }
    }

    private static String read(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
