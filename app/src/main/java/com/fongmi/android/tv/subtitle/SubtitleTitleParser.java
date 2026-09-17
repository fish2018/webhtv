package com.fongmi.android.tv.subtitle;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SubtitleTitleParser {

    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)(?:s(\\d{1,2})[-._\\s]*e(\\d{1,3})|第\\s*([0-9零〇一二三四五六七八九十两百]+)\\s*[集话話])");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)(?:第\\s*([0-9零〇一二三四五六七八九十两百]+)\\s*[季部]|season\\s*([0-9]{1,2})|s([0-9]{1,2})(?:[-._\\s]*e[0-9]{1,3})?)");

    public String cleanTitle(String text) {
        if (SubtitleStrings.isEmpty(text)) return "";

        String raw = text.trim();
        String clean = raw;
        clean = clean.replaceAll("(?i)\\.(mkv|mp4|avi|mov|wmv|flv|rmvb|ts|m2ts)$", "");
        clean = clean.replaceAll("[\\[【「『(（][^\\]】」』)）]{1,40}[\\]】」』)）]", " ");
        clean = clean.replaceAll("(?i)S\\d+E\\d+", " ");
        clean = clean.replaceAll("(?i)\\b(S\\d{1,2}|Season\\s*\\d{1,2})\\b", " ");
        clean = clean.replaceAll("(?i)第\\d+季", " ");
        clean = clean.replaceAll("(?i)第\\d+集", " ");
        clean = clean.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[季部]", " ");
        clean = clean.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[集话話]", " ");
        clean = clean.replaceAll("(?i)\\b(HD|4K|8K|1080P|2160P|720P|HDR|HDR10|DV|BluRay|WEB[- ]?DL|HDTV|BDRip|Remux|HEVC|H\\.?265|H\\.?264|x265|x264)\\b", " ");
        clean = clean.replaceAll("(?<!\\d)(19\\d{2}|20\\d{2})(?!\\d)", " ");
        clean = clean.replaceAll("(国语版|国配版|普通话版|粤语版|原声版|配音版|中字版|字幕版|台版|台湾版|港版|大陆版|内地版|中国版|泰版|韩版|日版|美版|英版)", " ");
        clean = clean.replaceAll("(臻彩|高码|高码率|无水印|无台标|国语|国配|国粤|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减|加长版|修复版)", " ");
        clean = clean.replaceAll("[._\\-+]+", " ");
        clean = clean.trim().replaceAll("\\s+", " ");
        clean = clean.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
        return SubtitleStrings.isEmpty(clean) ? raw : clean;
    }

    public int firstYear(String text) {
        if (SubtitleStrings.isEmpty(text)) return 0;
        Matcher matcher = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    public int seasonNumber(String text) {
        if (SubtitleStrings.isEmpty(text)) return -1;
        Matcher matcher = SEASON_PATTERN.matcher(text);
        while (matcher.find()) {
            int number = normalizeSourceNumber(firstNonEmptyGroup(matcher, 1, 2, 3));
            if (number > 0) return number;
        }
        return -1;
    }

    public int episodeNumber(String text) {
        if (SubtitleStrings.isEmpty(text)) return -1;
        Matcher matcher = EPISODE_PATTERN.matcher(text);
        while (matcher.find()) {
            String sxeEpisode = matcher.group(2);
            if (!SubtitleStrings.isEmpty(sxeEpisode)) return Integer.parseInt(sxeEpisode.replaceFirst("^0+(?!$)", ""));
            int number = normalizeSourceNumber(firstNonEmptyGroup(matcher, 3));
            if (number > 0) return number;
        }
        return -1;
    }

    public List<String> aliases(String... values) {
        Set<String> aliases = new LinkedHashSet<>();
        for (String value : values) {
            if (SubtitleStrings.isEmpty(value)) continue;
            String clean = cleanTitle(value);
            if (!SubtitleStrings.isEmpty(clean)) aliases.add(clean);
            int year = firstYear(value);
            if (year > 0) {
                String trimmed = removeYear(value, year);
                if (!SubtitleStrings.isEmpty(trimmed)) aliases.add(trimmed);
            }
        }
        return new ArrayList<>(aliases);
    }

    public String removeYear(String text, int year) {
        if (SubtitleStrings.isEmpty(text) || year <= 0) return cleanTitle(text);
        String cleaned = text.replaceAll("(?<!\\d)" + year + "(?!\\d)", " ");
        cleaned = cleaned.replaceAll("[._\\-+]+", " ").replaceAll("\\s+", " ").trim();
        return cleanTitle(cleaned);
    }

    private String firstNonEmptyGroup(Matcher matcher, int... groups) {
        for (int group : groups) {
            String value = matcher.group(group);
            if (!SubtitleStrings.isEmpty(value)) return value;
        }
        return "";
    }

    private int normalizeSourceNumber(String value) {
        if (SubtitleStrings.isEmpty(value)) return -1;
        value = value.trim();
        try {
            if (value.matches("\\d+")) return Integer.parseInt(value.replaceFirst("^0+(?!$)", ""));
        } catch (Exception ignored) {
            return -1;
        }
        int number = parseSmallChineseNumber(value);
        return number > 0 ? number : -1;
    }

    private int parseSmallChineseNumber(String value) {
        if (SubtitleStrings.isEmpty(value)) return 0;
        value = value.replace("两", "二").replace("零", "").replace("〇", "");
        if (value.matches("[一二三四五六七八九]")) return chineseDigit(value.charAt(0));
        int tenIndex = value.indexOf("十");
        if (tenIndex >= 0) {
            int tens = tenIndex == 0 ? 1 : chineseDigit(value.charAt(tenIndex - 1));
            int ones = tenIndex == value.length() - 1 ? 0 : chineseDigit(value.charAt(tenIndex + 1));
            return tens * 10 + ones;
        }
        return 0;
    }

    private int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }
}
