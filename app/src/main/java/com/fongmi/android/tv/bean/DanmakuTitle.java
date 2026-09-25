package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 弹幕搜索结果的剧名/集数解析工具。
 * <p>
 * webhtv 的 {@link Danmaku} 没有显式的 animeTitle/episode 字段，剧名与集数都嵌在 name 字符串里
 * （例如："你是迟来的欢喜(2026)【电视剧】 - 【IMGO】 第1集 ..."）。本类负责从 name 中提取：
 * <ul>
 *   <li>集数 {@link #parseEpisode(CharSequence)}（支持"第N集/S0xE0y/EP/E/Episode"）</li>
 *   <li>剧名分组键 {@link #titleKey(Danmaku)}：去掉来源标记、集数标记、年份、类型标记后的可读剧名前缀；
 *       解析失败返回 null（调用方据此把该条作为普通项平铺、不折叠）</li>
 *   <li>折叠头文案 {@link #headerTitle(String, int, boolean)}："[+/-] 剧名 (N集)"</li>
 * </ul>
 * 集数解析正则复用自 dev 原版 DanmakuAdapter。
 */
public final class DanmakuTitle {

    public static final int EPISODE_UNKNOWN = Integer.MAX_VALUE;

    private static final Pattern EPISODE_CN = Pattern.compile("第\\s*0*([0-9]{1,5})\\s*[集话話回期章节節]");
    private static final Pattern EPISODE_SEASON = Pattern.compile("\\bS\\d{1,2}\\s*E\\s*0*([0-9]{1,5})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EPISODE_EP = Pattern.compile("\\b(?:EP|E|Episode)\\s*0*([0-9]{1,5})\\b", Pattern.CASE_INSENSITIVE);

    /** "from" 来源关键字（已基于来源分组，from 及其后的来源名都属于冗余信息） */
    private static final Pattern SOURCE_FROM = Pattern.compile("\\bfrom\\b", Pattern.CASE_INSENSITIVE);
    /** "from xxx" 形式的来源内联标记（displayTitle 回退用） */
    private static final Pattern SOURCE_INLINE = Pattern.compile("\\s*\\bfrom\\s+[^\\s\\-]+\\s*-?\\s*", Pattern.CASE_INSENSITIVE);
    /** 【】或[]包裹的来源/类型标记 */
    private static final Pattern SOURCE_MARK = Pattern.compile("[【\\[]([^】\\]]{1,24})[】\\]]");
    /** URL */
    private static final Pattern URL = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    /** (2026) 形式的年份括注 */
    private static final Pattern YEAR = Pattern.compile("[(\\[（【]\\s*(?:19|20)\\d{2}\\s*[)\\]）】]");
    /** " - " 形式的分隔符（剧名与集数/来源之间） */
    private static final Pattern DASH = Pattern.compile("\\s*[-－—~～]\\s*");
    /** 连续空白 */
    private static final Pattern BLANK = Pattern.compile("\\s{2,}");

    private DanmakuTitle() {
    }

    /** 解析集数；无法解析返回 {@link #EPISODE_UNKNOWN}。 */
    public static int parseEpisode(CharSequence text) {
        int episode = matchEpisode(EPISODE_CN, text);
        if (episode == EPISODE_UNKNOWN) episode = matchEpisode(EPISODE_SEASON, text);
        if (episode == EPISODE_UNKNOWN) episode = matchEpisode(EPISODE_EP, text);
        return episode;
    }

    /** 解析弹幕项的集数。 */
    public static int getEpisode(@NonNull Danmaku item) {
        return parseEpisode(item.getName());
    }

    /**
     * 提取剧名分组键：清洗 name 中的来源/集数/年份/类型/URL 标记后取前缀。
     * 解析失败（清洗后为空或无稳定剧名）返回 null，调用方据此把该条平铺、不折叠。
     */
    @Nullable
    public static String titleKey(@NonNull Danmaku item) {
        return cleanTitle(item.getName());
    }

    /**
     * 显示用剧名：优先用 {@link #titleKey} 清洗结果；失败回退到 name 去来源标记后的文本。
     */
    @NonNull
    public static String displayTitle(@NonNull Danmaku item) {
        String title = cleanTitle(item.getName());
        if (!TextUtils.isEmpty(title)) return title;
        // 回退：仅去掉来源内联标记，保留原名
        String fallback = SOURCE_INLINE.matcher(item.getName()).replaceAll(" ");
        fallback = SOURCE_MARK.matcher(fallback).replaceAll(" ");
        fallback = BLANK.matcher(fallback).replaceAll(" ").trim();
        return fallback.isEmpty() ? item.getName() : fallback;
    }

    /** 折叠头文案："[+/-] 剧名 (N集)"。 */
    @NonNull
    public static String headerTitle(@NonNull String title, int count, boolean expanded) {
        return (expanded ? "[-] " : "[+] ") + title + " (" + count + "集)";
    }

    @Nullable
    private static String cleanTitle(String text) {
        if (TextUtils.isEmpty(text)) return null;
        String value = text;
        value = URL.matcher(value).replaceAll(" ");
        // 先截断：from 关键字或首个 " - " 分隔符（取先出现者）之前为剧名。
        // 来源已在上一层按 source 分组，from 及其后的来源名/集数都属于冗余后缀。
        int cut = findCut(value);
        if (cut >= 0) value = value.substring(0, cut);
        // 再清洗来源/类型/年份标记
        value = SOURCE_MARK.matcher(value).replaceAll(" ");
        value = YEAR.matcher(value).replaceAll(" ");
        value = BLANK.matcher(value).replaceAll(" ").trim();
        return value.isEmpty() ? null : value;
    }

    /** 返回剧名截断点索引：from 关键字或首个分隔符的起始位置；都没有返回 -1。 */
    private static int findCut(String text) {
        Matcher from = SOURCE_FROM.matcher(text);
        if (from.find()) return from.start();
        Matcher dash = DASH.matcher(text);
        if (dash.find()) return dash.start();
        return -1;
    }

    private static int matchEpisode(Pattern pattern, CharSequence text) {
        if (text == null || text.length() == 0) return EPISODE_UNKNOWN;
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) return EPISODE_UNKNOWN;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (Exception ignored) {
            return EPISODE_UNKNOWN;
        }
    }

    // ---- 相似度排序（参考 CatVodSpider DanmakuUIHelper） ----

    /** 按搜索关键词对剧名列表排序：相似度高的排前面，同分按长度升序再按字典序。 */
    public static void sortByKeyword(List<String> titles, String keyword) {
        if (titles == null || titles.size() <= 1) return;
        String norm = normalizeSearchText(keyword);
        if (TextUtils.isEmpty(norm)) { Collections.sort(titles); return; }
        Collections.sort(titles, (left, right) -> {
            int cmp = Double.compare(matchScore(right, norm), matchScore(left, norm));
            if (cmp != 0) return cmp;
            cmp = Integer.compare(safeLen(left), safeLen(right));
            if (cmp != 0) return cmp;
            return (left != null ? left : "").compareTo(right != null ? right : "");
        });
    }

    /** 按集数对弹幕列表排序（升序，无法解析集数的排末尾）。 */
    public static void sortByEpisode(List<Danmaku> items) {
        if (items == null || items.size() <= 1) return;
        Collections.sort(items, Comparator.comparingInt(DanmakuTitle::getEpisode));
    }

    /** 归一化搜索文本：去 from/括号/年份/空白，小写。 */
    public static String normalizeSearchText(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text.replaceAll("(?i)\\s*from\\s*.*$", "")
                .replaceAll("[【\\[][^】\\]]{0,24}[】\\]]", "")
                .replaceAll("[(\\[（【]\\s*(?:19|20)\\d{2}\\s*[)\\]）】]", "")
                .replaceAll("\\s+", "")
                .trim()
                .toLowerCase();
    }

    private static double matchScore(String title, String normKeyword) {
        String normTitle = normalizeSearchText(title);
        if (TextUtils.isEmpty(normTitle) || TextUtils.isEmpty(normKeyword)) return 0.0;
        if (normTitle.equals(normKeyword)) return 3.0;
        if (normTitle.contains(normKeyword)) return 2.0 + (double) normKeyword.length() / normTitle.length();
        if (normKeyword.contains(normTitle)) return 1.8 + (double) normTitle.length() / normKeyword.length();
        return similarity(normTitle, normKeyword);
    }

    private static double similarity(String left, String right) {
        if (left == null) left = "";
        if (right == null) right = "";
        String longer = left, shorter = right;
        if (left.length() < right.length()) { longer = right; shorter = left; }
        int longerLen = longer.length();
        if (longerLen == 0) return 1.0;
        return (longerLen - editDistance(longer, shorter)) / (double) longerLen;
    }

    private static int editDistance(String left, String right) {
        left = left.toLowerCase();
        right = right.toLowerCase();
        int[] costs = new int[right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= right.length(); j++) {
                if (i == 0) { costs[j] = j; }
                else if (j > 0) {
                    int newValue = costs[j - 1];
                    if (left.charAt(i - 1) != right.charAt(j - 1))
                        newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                    costs[j - 1] = lastValue;
                    lastValue = newValue;
                }
            }
            if (i > 0) costs[right.length()] = lastValue;
        }
        return costs[right.length()];
    }

    private static int safeLen(String text) { return text != null ? text.length() : 0; }
}
