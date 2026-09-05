package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Episode;
import com.fongmi.android.tv.setting.Setting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class EpisodeTitleCompact {

    private static final Pattern EXTENSION = Pattern.compile("(?i)\\.(mp4|mkv|avi|mov|flv|wmv|ts|m2ts|m3u8|rmvb|webm)$");
    private static final String SIZE_VALUE = "(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?";
    private static final String SIZE_UNIT = "(?:TB|T|GB|G|MB|M)";
    private static final String SIZE_TEXT = "(" + SIZE_VALUE + ")\\s*(" + SIZE_UNIT + ")";
    private static final Pattern SIZE_PREFIX = Pattern.compile("(?i)^\\s*[\\[\\(（【]?\\s*" + SIZE_TEXT + "\\s*[\\]\\)）】]?\\s*");
    private static final Pattern SIZE_SUFFIX = Pattern.compile("(?i)\\s*[\\[\\(（【]?\\s*" + SIZE_TEXT + "\\s*[\\]\\)）】]?\\s*$");
    private static final Pattern HASH_SUFFIX = Pattern.compile("(?i)\\s*[\\[\\(（【]\\s*[A-F0-9]{8,32}\\s*[\\]\\)）】]\\s*$");
    private static final Pattern RESOLUTION_START = Pattern.compile("(?i)^\\s*[0-9]{3,4}\\s*[x×]\\s*[0-9]{3,4}(?=$|\\D)");
    private static final Pattern RESOLUTION_TOKEN = Pattern.compile("(?i)(?<![0-9])[0-9]{3,4}\\s*[x×]\\s*[0-9]{3,4}(?![0-9])");
    private static final Pattern COLLECTION_HEADER = Pattern.compile("^\\s*[\\[【]\\s*([0-9]{1,3})\\s*[._-]\\s*((?:19|20)[0-9]{2})\\s+([^\\]】]+)[\\]】]\\s*(.*)$");
    private static final Pattern CHINESE_SEASON = Pattern.compile("第\\s*[0-9一二三四五六七八九十百]+\\s*季");
    private static final Pattern ENGLISH_SEASON = Pattern.compile("(?i)(?:SEASON\\s*|S)([0-9]{1,2})(?=$|\\D)");
    private static final Pattern COLLECTION_PART = Pattern.compile("(?:动画|動畫)(?:版)?\\s*(第\\s*[0-9一二三四五六七八九十百]+\\s*部)");
    private static final Pattern COLLECTION_SPECIAL = Pattern.compile("(?i)(特别篇|特別篇|剧场版|劇場版|(?<![A-Z0-9])(?:OVA?|OAD|SP)(?![A-Z0-9]))(?:\\s*[:：._-]?\\s*)(.*)");
    private static final Pattern COLLECTION_STAGE = Pattern.compile("(?:舞台剧|舞台劇)\\s*(.*)");
    private static final Pattern COLLECTION_SIDE_STORY = Pattern.compile("(?:番外篇|番外)\\s*(.*)");
    private static final Pattern COLLECTION_SPIN_OFF = Pattern.compile("(?:外传|外傳)\\s*(.*)");
    private static final Pattern COLLECTION_MEMORIAL = Pattern.compile("(?:纪念作|紀念作)\\s*(.*)");
    private static final Pattern COLLECTION_CHAPTER = Pattern.compile("([白黑]之章|(?:前|后|後|上|下)(?:篇|章))");
    private static final Pattern NESTED_COLLECTION_HEADER = Pattern.compile("^\\s*\\[\\[(.*?)\\]\\]\\s*(.*)$");
    private static final Pattern CATEGORY_HEADER = Pattern.compile("(?i)^\\s*\\[(PV|其他|番外|花絮|舞台剧|舞台劇)\\]\\s*(.*)$");
    private static final Pattern REPEATED_BRACKET_SERIES = Pattern.compile("(?i)^\\s*[\\[【]\\s*([^\\]】]{1,24}?)\\s*[\\]】]\\s*\\1\\s*((?:S\\s*[0-9]{1,2}\\s*E\\s*[0-9]{1,4}|EP?\\s*[0-9]{1,4}|SP\\s*[0-9]{1,4}|[0-9]{1,4}(?:[.·][0-9]+)?))(?=$|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])");
    private static final Pattern BARE_STRUCTURED_HEADER = Pattern.compile("(?i)^\\s*\\[(?:DBD-Raws|[^\\]]*(?:字幕组|字幕組|字幕团|字幕團))\\]");
    private static final String CJK_NUMBER = "[0-9一二三四五六七八九十百千零〇两兩]+";
    private static final Pattern MAKU_BRACKET = Pattern.compile("^\\s*((?:第\\s*" + CJK_NUMBER + "\\s*幕)|(?:(?:最終|最终)\\s*幕))(?:\\s+.*)?$");
    private static final Pattern MAKU_EDITION = Pattern.compile("(?i)[\\[【]\\s*(?:特別版|特别版)(?:\\s*[（(]\\s*([^）)]+)\\s*[）)])?\\s*[\\]】]");
    private static final Pattern DAY_RELEASE_CONTENT = Pattern.compile("(?i)\\[DAY\\]\\s*\\[([^\\]]+)\\]");
    // Android 9's ICU 60 rejects the Java-specific IsHan alias; sc=Han works on both Android and the JVM.
    private static final Pattern INLINE_EPISODE = Pattern.compile("(?i)(?<![0-9])([0-9]{1,3}(?:\\.[0-9]+)?(?:SP)?)[\\s._-]*([\\p{sc=Han}々〆〇ヶ]+(?:[（(][^）)]{1,16}[）)])?)");
    private static final Pattern LEADING_FILE_EPISODE = Pattern.compile("(?i)^\\s*([0-9]{1,3})(?=$|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])");
    private static final Pattern SEMANTIC_FILE_TITLE = Pattern.compile("(?i)^\\s*(剧场版|劇場版|特别篇|特別篇)\\s+(.+)$");
    private static final Pattern TECHNICAL_TITLE_SUFFIX = Pattern.compile("(?i)(?:^|[\\s._\\-·])(?:4320P|2160P|1080P|720P|[48]K|HDR10(?:\\+|⁺)?|HDR|SDR|DV|DOLBY|HEVC|H[.]?26[45]|X26[45]|AVC|AV1|AAC|FLAC|WEB-?DL|WEBRIP|BLU-?RAY|BDRIP|BD|MP4|MKV|AVI|TS|(?:HI)?10-?BIT|8-?BIT|(?:19|20)[0-9]{2}[-._][0-9]{1,2}[-._][0-9]{1,2})(?=$|[\\s._\\-·])");
    private static final Pattern BRACKET_EPISODE = Pattern.compile("(?i)(?:SP[0-9]{1,3}|[0-9]{1,3}(?:[.·][0-9]+)?(?:SP)?)");
    private static final Pattern PROMO_TOKEN = Pattern.compile("(?i)^PV\\s*([0-9]{1,3})$");
    private static final Pattern STAGE_TITLE = Pattern.compile("(觉醒|覺醒|転生|转生)");
    private static final Pattern BRACKET_TOKEN = Pattern.compile("[\\[【(（]\\s*([^\\]】)）]+?)\\s*[\\]】)）]");
    private static final Pattern TECHNICAL_BRACKET = Pattern.compile("(?i)(?:[0-9]{3,4}\\s*[x×]\\s*[0-9]{3,4}|(?:4320|2160|1080|720)P|[48]K|HDR10(?:\\+|⁺)?|HDR|SDR|DV|DOLBY|HEVC|H[.]?26[45]|X26[45]|AVC|AV1|AAC|FLAC|WEB-?DL|WEBRIP|BLU-?RAY|BDRIP|BD|MP4|MKV|AVI|TS|[A-F0-9]{8,32}|(?:简|繁|中|日|英|国|粤).*(?:字幕|双语|中字))");
    private static final Pattern TECHNICAL_DETAIL = Pattern.compile("(?i).*(?:[0-9]{3,4}\\s*[x×]\\s*[0-9]{3,4}|(?:4320|2160|1080|720|576|480)P(?:60P)?|HI[0-9]+P|HDR10|HDR|SDR|DOLBY|HEVC|AVC|X26[45]|H[.]?26[45]|DIVX|AAC|AC3|EAC3|DDP|PCM|FLAC|DTS|TRUEHD|WEB-?DL|WEBRIP|BLU-?RAY|BDRIP|TVRIP|DVDRIP|HDRIP|MP4|MKV|AVI|M2TS).*");
    private static final Pattern LANGUAGE_DETAIL = Pattern.compile("(?i)(?:(?:GB|CHS|CHT|BIG5)(?:[·._\\s-]*(?:CN|JAP|JP|CHS|CHT))*|.*(?:字幕|双语|中字|外挂|内嵌|简繁|繁中|简中).*)");
    private static final Pattern COLLECTION_VARIANT_TOKEN = Pattern.compile("(?i)(?:4320|2160|1080|720)P|[48]K|HDR10(?:\\+|⁺)?|HDR|SDR|DV|HEVC|H[.]?265|X265|AVC|H[.]?264|X264|AV1");
    private static final Pattern EPISODE_START = Pattern.compile("(?i)^(?:S\\s*[0-9]{1,2}\\s*E\\s*[0-9]{1,4}(?:\\s*(?:E|[-~—–])\\s*[0-9]{1,4})?|[0-9]{1,2}\\s*x\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?|第\\s*[0-9一二三四五六七八九十百千零〇两兩]+\\s*(?:集|话|話|期|章|回|幕)|(?:最終|最终|特別|特别)\\s*幕|[0-9]{1,4}\\s*(?:集|话|話|期|章|回)|(?:EP|E)\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?|[0-9]{4}[-._][0-9]{1,2}[-._][0-9]{1,2}|[0-9]{1,4}(?:\\D|$)|[上下](?:集|部)?|前篇|后篇|後篇|正片|预告|預告|花絮)");
    private static final Pattern WEAK_EPISODE_TOKEN = Pattern.compile("(?i)[上下](?:集|部)?|前篇|后篇|後篇|正片|预告|預告|花絮");
    private static final Pattern VARIANT_TOKEN = Pattern.compile("(?i)(?:^|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])((?:4320|2160|1080|720)P|[48]K|HQ|HD|HDR10(?:\\+|⁺)?|HDR|SDR|DV|60FPS|50FPS|30FPS|25FPS|24FPS|10BITS|8BITS|HEVC|H265|H\\.265|AVC|H264|H\\.264|AV1|DDP\\s*2[.·]?0|AAC\\s*2[.·]?0)(?=$|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])");
    private static final Pattern TAIL_CODE = Pattern.compile("(?i)[._\\-·]([0-9]{8,})$");
    private static final Pattern DATE_TOKEN = Pattern.compile("(?i)[0-9]{4}[-._][0-9]{1,2}[-._][0-9]{1,2}");
    private static final Pattern[] EPISODE_TOKENS = {
            Pattern.compile("(?i)S\\s*[0-9]{1,2}\\s*E\\s*[0-9]{1,4}(?:\\s*(?:E|[-~—–])\\s*[0-9]{1,4})?"),
            Pattern.compile("(?i)(?<![0-9])[0-9]{1,2}\\s*[x×]\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?(?![0-9])"),
            Pattern.compile("(?i)(?:第\\s*[0-9一二三四五六七八九十百千零〇两兩]+\\s*幕|(?:最終|最终|特別|特别)\\s*幕)"),
            Pattern.compile("(?i)第\\s*[0-9一二三四五六七八九十百]+\\s*(?:集|话|話|期|章|回)"),
            Pattern.compile("(?i)[0-9]{1,4}\\s*(?:集|话|話|期|章|回)"),
            Pattern.compile("(?i)(?:EP|E)\\s*[0-9]{1,4}(?:\\s*[-~—–]\\s*[0-9]{1,4})?"),
            Pattern.compile("(?i)^\\s*([0-9]{1,3})(?=$|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])"),
            DATE_TOKEN,
            Pattern.compile("(?i)(?:^|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])([0-9]{1,4}v[0-9]+|[0-9]{1,3})(?=$|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》])")
    };
    private static final Pattern TECH_SUFFIX = Pattern.compile("(?i)^[\\s._\\-\\[\\]()（）【】]+(?:4K|8K|2160P|1080P|720P|HDR|HDR10|DV|DOLBY|HEVC|H265|H\\.265|H264|H\\.264|AV1|AAC|FLAC|WEB-DL|WEBRIP|BLURAY|BD|HD|国语|国配|粤语|中字|中英双字|简中|繁中|内嵌字幕|无字)(?:[\\s._\\-\\[\\]()（）【】]+(?:4K|8K|2160P|1080P|720P|HDR|HDR10|DV|DOLBY|HEVC|H265|H\\.265|H264|H\\.264|AV1|AAC|FLAC|WEB-DL|WEBRIP|BLURAY|BD|HD|国语|国配|粤语|中字|中英双字|简中|繁中|内嵌字幕|无字))*[\\s._\\-\\[\\]()（）【】]*$");
    private static final Pattern FILE_EDGE_SEPARATORS = Pattern.compile("^[\\s._\\-·|/\\\\:：,，;；]+|[\\s._\\-·|/\\\\:：,，;；]+$");
    private static final Pattern EDGE_SEPARATORS = Pattern.compile("^[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》]+|[\\s._\\-·|/\\\\:：,，;；\\[\\]()（）【】《》]+$");
    private static final int MAX_COMPACT_LENGTH = 14;

    private EpisodeTitleCompact() {
    }

    public static void apply(List<Episode> episodes) {
        apply(episodes, compute(episodes, Setting.isCompactEpisodeTitle()));
    }

    public static List<String> compute(List<Episode> episodes, boolean compactEnabled) {
        if (episodes == null || episodes.isEmpty()) return new ArrayList<>();
        List<String> rawNames = new ArrayList<>(episodes.size());
        for (Episode episode : episodes) rawNames.add(episode.getRawDisplayName());
        return computeRaw(rawNames, compactEnabled);
    }

    public static List<String> computeRaw(List<String> rawNames, boolean compactEnabled) {
        List<String> displayNames = new ArrayList<>();
        if (rawNames == null || rawNames.isEmpty()) return displayNames;
        if (!compactEnabled) {
            for (int i = 0; i < rawNames.size(); i++) displayNames.add(null);
            return displayNames;
        }
        return compact(rawNames);
    }

    public static void apply(List<Episode> episodes, List<String> displayNames) {
        if (episodes == null || episodes.isEmpty()) return;
        if (displayNames == null || displayNames.size() != episodes.size()) {
            throw new IllegalArgumentException("Display names must match episode count");
        }
        for (int i = 0; i < episodes.size(); i++) episodes.get(i).setDisplayName(displayNames.get(i));
    }

    static List<String> compact(List<String> rawNames) {
        if (rawNames == null || rawNames.isEmpty()) return new ArrayList<>();
        List<String> names = new ArrayList<>();
        List<String> sizes = new ArrayList<>();
        for (String raw : rawNames) {
            names.add(cleanFileNoise(raw));
            sizes.add(extractSize(raw));
        }
        if (rawNames.size() < 2) {
            List<String> result = new ArrayList<>();
            String structured = findCollectionDisplay(rawNames.get(0));
            result.add(appendSize(isEmpty(structured) ? names.get(0) : structured, sizes.get(0)));
            return result;
        }
        int prefix = findPrefix(names);
        int suffix = findSuffix(names, prefix);
        List<String> fallback = new ArrayList<>();
        List<String> detectedTokens = new ArrayList<>();
        List<String> collectionDisplays = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            int start = Math.min(prefix, name.length());
            int end = Math.max(Math.min(name.length() - suffix, name.length()), start);
            String slice = name.substring(start, end);
            String compact = start == 0 && end == name.length() ? cleanupFileEdge(slice) : cleanupEdge(slice);
            if (isEmpty(compact)) compact = name;
            fallback.add(compact);
            detectedTokens.add(findEpisodeToken(stripFileNoise(rawNames.get(i))));
            collectionDisplays.add(findCollectionDisplay(rawNames.get(i)));
        }
        collectionDisplays = resolveCollectionVariants(rawNames, collectionDisplays);
        List<String> compacted = new ArrayList<>();
        List<String> tokens = new ArrayList<>();
        Map<String, Integer> count = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
            String collectionDisplay = collectionDisplays.get(i);
            String token = isEmpty(collectionDisplay) ? detectedTokens.get(i) : collectionDisplay;
            String base = isEmpty(collectionDisplay) ? preferEpisodeToken(token, fallback.get(i)) : collectionDisplay;
            String display = appendSize(base, sizes.get(i));
            compacted.add(display);
            tokens.add(token);
            count.put(display, count.getOrDefault(display, 0) + 1);
        }
        compacted = resolveDuplicates(names, compacted, fallback, tokens, sizes, count);
        return compacted;
    }

    private static String cleanFileNoise(String value) {
        return cleanupFileEdge(stripFileNoise(value));
    }

    private static String cleanupFileEdge(String text) {
        String value = FILE_EDGE_SEPARATORS.matcher(text == null ? "" : text.trim()).replaceAll("");
        while (hasEntireBracketWrapper(value)) {
            value = FILE_EDGE_SEPARATORS.matcher(value.substring(1, value.length() - 1).trim()).replaceAll("");
        }
        return value;
    }

    private static boolean hasEntireBracketWrapper(String value) {
        if (value.length() < 2) return false;
        char open = value.charAt(0);
        char close = matchingBracket(open);
        if (close == 0 || value.charAt(value.length() - 1) != close) return false;
        int depth = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == open) depth++;
            if (current == close && --depth == 0 && i < value.length() - 1) return false;
        }
        return depth == 0;
    }

    private static char matchingBracket(char open) {
        if (open == '[') return ']';
        if (open == '【') return '】';
        if (open == '(') return ')';
        if (open == '（') return '）';
        if (open == '《') return '》';
        return 0;
    }

    private static String stripFileNoise(String value) {
        String text = isEmpty(value) ? "" : value.trim();
        text = cleanEdgeNoise(text);
        text = EXTENSION.matcher(text).replaceFirst("");
        text = cleanEdgeNoise(text);
        return text.trim();
    }

    private static String extractSize(String value) {
        if (isEmpty(value)) return "";
        String text = EXTENSION.matcher(value.trim()).replaceFirst("");
        String size = extractSize(SIZE_SUFFIX.matcher(text));
        return isEmpty(size) ? extractSize(SIZE_PREFIX.matcher(text)) : size;
    }

    private static String extractSize(Matcher matcher) {
        if (!matcher.find()) return "";
        String value = matcher.group(1).replace(",", "");
        String unit = matcher.group(2).toUpperCase(Locale.ROOT);
        if (unit.equals("T")) unit = "TB";
        if (unit.equals("G")) unit = "GB";
        if (unit.equals("M")) unit = "MB";
        return value + unit;
    }

    private static String appendSize(String display, String size) {
        if (isEmpty(size)) return display;
        if (isEmpty(display)) return "[" + size + "]";
        return display + " [" + size + "]";
    }

    private static String cleanEdgeNoise(String value) {
        String text = HASH_SUFFIX.matcher(value).replaceFirst("");
        text = SIZE_SUFFIX.matcher(text).replaceFirst("");
        text = SIZE_PREFIX.matcher(text).replaceFirst("");
        return text;
    }

    private static int findPrefix(List<String> names) {
        int prefix = commonPrefix(names);
        for (int i = prefix; i > 0; i--) {
            if (isUsefulPrefix(names, i)) return i;
        }
        return 0;
    }

    private static int commonPrefix(List<String> names) {
        int prefix = names.get(0).length();
        for (String name : names) {
            prefix = Math.min(prefix, name.length());
            for (int i = 0; i < prefix; i++) {
                if (names.get(0).charAt(i) != name.charAt(i)) {
                    prefix = i;
                    break;
                }
            }
        }
        return prefix;
    }

    private static boolean isUsefulPrefix(List<String> names, int index) {
        if (index < 2) return false;
        for (String name : names) {
            if (index > name.length()) return false;
            String rest = cleanupEdge(name.substring(index));
            if (isEmpty(rest)) return false;
            if (!isBoundary(name, index) && !canStartEpisodeAfterPrefix(name, index, rest)) return false;
        }
        return index >= 6 || allRestStartsEpisode(names, index);
    }

    private static boolean allRestStartsEpisode(List<String> names, int index) {
        for (String name : names) {
            if (!startsEpisode(cleanupEdge(name.substring(index)))) return false;
        }
        return true;
    }

    private static int findSuffix(List<String> names, int prefix) {
        int suffix = commonSuffix(names, prefix);
        for (int i = suffix; i > 0; i--) {
            if (isUsefulSuffix(names, i, prefix)) return i;
        }
        return 0;
    }

    private static int commonSuffix(List<String> names, int prefix) {
        int suffix = names.get(0).length() - Math.min(prefix, names.get(0).length());
        for (String name : names) {
            int max = name.length() - Math.min(prefix, name.length());
            suffix = Math.min(suffix, max);
            for (int i = 0; i < suffix; i++) {
                if (names.get(0).charAt(names.get(0).length() - 1 - i) != name.charAt(name.length() - 1 - i)) {
                    suffix = i;
                    break;
                }
            }
        }
        return suffix;
    }

    private static boolean isUsefulSuffix(List<String> names, int suffix, int prefix) {
        if (suffix < 2) return false;
        for (String name : names) {
            int start = name.length() - suffix;
            if (start <= prefix || !isBoundary(name, start)) return false;
            if (isEmpty(cleanupEdge(name.substring(prefix, start)))) return false;
        }
        String removed = names.get(0).substring(names.get(0).length() - suffix);
        return suffix >= 6 || TECH_SUFFIX.matcher(removed.toUpperCase(Locale.ROOT)).matches();
    }

    private static boolean isBoundary(String text, int index) {
        if (index <= 0 || index >= text.length()) return true;
        return isSeparator(text.charAt(index - 1)) || isSeparator(text.charAt(index));
    }

    private static boolean isSeparator(char c) {
        return Character.isWhitespace(c) || "-_.·|/\\:：,，;；[]()（）【】《》".indexOf(c) >= 0;
    }

    private static boolean startsEpisode(String text) {
        if (RESOLUTION_START.matcher(text).find()) return false;
        return EPISODE_START.matcher(text).find();
    }

    private static boolean canStartEpisodeAfterPrefix(String text, int index, String rest) {
        if (!startsEpisode(rest)) return false;
        char previous = text.charAt(index - 1);
        return !Character.isDigit(previous) && !isAsciiLetter(previous);
    }

    private static boolean isAsciiLetter(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    private static String preferEpisodeToken(String token, String compact) {
        if (compact.length() <= MAX_COMPACT_LENGTH) return compact;
        return isEmpty(token) ? compact : token;
    }

    private static String findCollectionDisplay(String text) {
        if (isEmpty(text)) return "";
        Matcher header = COLLECTION_HEADER.matcher(text);
        if (header.matches()) return findNumberedCollectionDisplay(header);
        Matcher nested = NESTED_COLLECTION_HEADER.matcher(text);
        if (nested.matches()) return findNestedCollectionDisplay(nested.group(2));
        Matcher category = CATEGORY_HEADER.matcher(text);
        if (category.matches()) return findCategoryDisplay(category.group(1), category.group(2));
        String maku = findMakuDisplay(text);
        if (!isEmpty(maku)) return maku;
        String dayRelease = findDayReleaseDisplay(text);
        if (!isEmpty(dayRelease)) return dayRelease;
        String repeated = findRepeatedBracketSeriesDisplay(text);
        if (!isEmpty(repeated)) return repeated;
        if (BARE_STRUCTURED_HEADER.matcher(text).find()) return findNestedCollectionDisplay(text);
        String fileDisplay = findLeadingFileDisplay(text);
        if (!isEmpty(fileDisplay)) return fileDisplay;
        return "";
    }

    private static String findMakuDisplay(String text) {
        Matcher bracket = BRACKET_TOKEN.matcher(stripFileNoise(text));
        while (bracket.find()) {
            Matcher maku = MAKU_BRACKET.matcher(cleanupEdge(bracket.group(1)));
            if (!maku.matches()) continue;
            String display = normalizeEpisodeToken(maku.group(1));
            Matcher edition = MAKU_EDITION.matcher(text);
            if (!edition.find()) return display;
            String detail = cleanupEdge(edition.group(1));
            return display + " " + (isEmpty(detail) ? "特別版" : normalizeFullWidthDigits(detail));
        }
        return "";
    }

    private static String findDayReleaseDisplay(String text) {
        Matcher matcher = DAY_RELEASE_CONTENT.matcher(stripFileNoise(text));
        if (!matcher.find()) return "";
        String value = normalizeDisplayText(matcher.group(1).replace('\u3000', ' '));
        int index = value.indexOf("スペシャルDVD");
        if (index >= 0) return normalizeDisplayText(value.substring(index));
        String normalizedVs = value.replace("ＶＳ", "VS");
        if (normalizedVs.contains("VS")) {
            index = normalizedVs.indexOf("エピック");
            if (index >= 0) return "VS " + normalizeDisplayText(normalizedVs.substring(index).replaceAll("\\s+特別限定版$", ""));
            index = normalizedVs.indexOf("銀幕");
            if (index >= 0) return "VS " + cleanupEdge(normalizedVs.substring(index)).replaceAll("[！!]+$", "");
            return "VS";
        }
        index = value.indexOf("ファイナルライブツアー");
        if (index >= 0) return normalizeFullWidthDigits(value.substring(index).replace("ファイナルライブツアー", "ファイナルライブ"));
        index = value.indexOf("銀幕版");
        if (index >= 0) return normalizeDisplayText(value.substring(index));
        if (value.contains("特別幕")) return "特別幕";
        if (value.contains("特别幕")) return "特别幕";
        return "";
    }

    private static String normalizeFullWidthDigits(String value) {
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            builder.append(current >= '０' && current <= '９' ? (char) ('0' + current - '０') : current);
        }
        return builder.toString();
    }

    private static String findRepeatedBracketSeriesDisplay(String text) {
        Matcher matcher = REPEATED_BRACKET_SERIES.matcher(stripFileNoise(text));
        if (!matcher.find()) return "";
        String label = normalizeDisplayText(matcher.group(1));
        if (!isEpisodeTitleCandidate(label)) return "";
        return label + " " + normalizeEpisodeToken(matcher.group(2));
    }

    private static String findLeadingFileDisplay(String text) {
        String remainder = stripLeadingBracketGroup(stripFileNoise(text));
        Matcher episode = LEADING_FILE_EPISODE.matcher(remainder);
        if (episode.find()) return buildLeadingEpisodeDisplay(remainder, normalizeEpisodeToken(episode.group(1)), episode.end());
        Matcher semantic = SEMANTIC_FILE_TITLE.matcher(remainder);
        if (!semantic.matches()) return "";
        String title = trimTechnicalTitleSuffix(semantic.group(2));
        return normalizeSpecialLabel(semantic.group(1)) + (isEmpty(title) ? "" : " " + title);
    }

    private static String stripLeadingBracketGroup(String text) {
        int start = 0;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        if (start >= text.length()) return text;
        char close = matchingBracket(text.charAt(start));
        if (close == 0) return text;
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char current = text.charAt(i);
            if (current == text.charAt(start)) depth++;
            if (current != close || --depth != 0) continue;
            return text.substring(i + 1).trim();
        }
        return text;
    }

    private static String buildLeadingEpisodeDisplay(String text, String episode, int end) {
        String remainder = text.substring(end).trim();
        if (!remainder.startsWith("纯享")) return episode;
        StringBuilder display = new StringBuilder(episode).append(" 纯享");
        Matcher bracket = BRACKET_TOKEN.matcher(remainder.substring(2));
        while (bracket.find()) {
            String value = cleanupEdge(bracket.group(1));
            if (value.matches("重置版|修复版|修復版")) display.append(' ').append(value);
        }
        return display.toString();
    }

    private static String trimTechnicalTitleSuffix(String text) {
        Matcher technical = TECHNICAL_TITLE_SUFFIX.matcher(text);
        int end = text.length();
        while (technical.find()) end = Math.min(end, technical.start());
        return cleanupEdge(text.substring(0, end)).replaceAll("[.·]+", ".");
    }

    private static String findNumberedCollectionDisplay(Matcher header) {
        String descriptor = header.group(3).trim();
        String remainder = stripFileNoise(header.group(4));
        String seriesLabel = findSeasonLabel(descriptor);
        if (isEmpty(seriesLabel)) seriesLabel = findPartLabel(descriptor);
        if (!isEmpty(seriesLabel)) {
            String episode = findStructuredEpisodeToken(remainder);
            if (isEmpty(episode)) return "";
            String title = findStructuredEpisodeTitle(remainder, episode);
            return seriesLabel + " " + episode + (isEmpty(title) ? "" : " " + title);
        }
        Matcher special = COLLECTION_SPECIAL.matcher(descriptor);
        if (special.find()) {
            String type = normalizeSpecialLabel(special.group(1));
            String title = normalizeDisplayText(special.group(2));
            String chapter = findChapterLabel(remainder);
            if (!isEmpty(chapter)) title = firstTitleWord(title) + " " + chapter;
            return type + (isEmpty(title) ? "" : " " + normalizeDisplayText(title));
        }
        Matcher stage = COLLECTION_STAGE.matcher(descriptor);
        if (stage.find()) {
            String title = normalizeDisplayText(stage.group(1));
            return "舞台剧" + (isEmpty(title) ? "" : " " + title);
        }
        Matcher sideStory = COLLECTION_SIDE_STORY.matcher(descriptor);
        if (sideStory.find()) {
            String title = normalizeDisplayText(sideStory.group(1));
            return "番外篇" + (isEmpty(title) ? "" : " " + title);
        }
        String episode = findStructuredEpisodeToken(remainder);
        if (isEmpty(episode)) return "";
        String episodeTitle = findStructuredEpisodeTitle(remainder, episode);
        Matcher spinOff = COLLECTION_SPIN_OFF.matcher(descriptor);
        if (spinOff.find()) {
            String title = firstTitleWord(spinOff.group(1));
            String label = isEmpty(title) ? "外传" : title + "外传";
            return label + " " + episode + (isEmpty(episodeTitle) ? "" : " " + episodeTitle);
        }
        Matcher memorial = COLLECTION_MEMORIAL.matcher(descriptor);
        if (!memorial.find()) return "";
        String title = normalizeDisplayText(memorial.group(1));
        return title + " " + episode + (isEmpty(episodeTitle) ? "" : " " + episodeTitle);
    }

    private static String findNestedCollectionDisplay(String value) {
        String remainder = stripFileNoise(value);
        String episode = findStructuredEpisodeToken(remainder);
        if (!isEmpty(episode)) {
            String title = findStructuredEpisodeTitle(remainder, episode);
            return episode + (isEmpty(title) ? "" : " " + title);
        }
        return findLastMeaningfulBracketToken(remainder);
    }

    private static String findCategoryDisplay(String category, String value) {
        String remainder = stripFileNoise(value);
        List<String> candidates = findMeaningfulBracketTokens(remainder);
        if (category.matches("舞台剧|舞台劇")) {
            String episode = findBracketEpisodeToken(remainder);
            if (!isEmpty(episode)) return "舞台剧 " + episode;
            String label = candidates.isEmpty() ? cleanFileNoise(remainder) : candidates.get(candidates.size() - 1);
            Matcher title = STAGE_TITLE.matcher(label);
            if (title.find()) {
                label = normalizeStageTitle(title.group(1));
            } else {
                title = STAGE_TITLE.matcher(remainder);
                if (title.find()) label = normalizeStageTitle(title.group(1));
            }
            return "舞台剧" + (isEmpty(label) ? "" : " " + normalizeDisplayText(label));
        }
        if (category.equalsIgnoreCase("PV")) {
            String label = candidates.isEmpty() ? cleanFileNoise(remainder) : candidates.get(candidates.size() - 1);
            Matcher promo = PROMO_TOKEN.matcher(label);
            return promo.matches() ? "PV" + promo.group(1) : "PV" + (isEmpty(label) ? "" : " " + label);
        }
        if (category.equals("番外")) {
            for (int i = 0; i < candidates.size(); i++) {
                if (!candidates.get(i).matches("番外篇|番外")) continue;
                String title = i + 1 < candidates.size() ? candidates.get(i + 1) : "";
                return "番外篇" + (isEmpty(title) ? "" : " " + title);
            }
            String title = candidates.isEmpty() ? cleanFileNoise(remainder) : candidates.get(candidates.size() - 1);
            return "番外" + (isEmpty(title) ? "" : " " + title);
        }
        String label = candidates.isEmpty() ? cleanFileNoise(remainder) : candidates.get(candidates.size() - 1);
        return label;
    }

    private static String findStructuredEpisodeToken(String text) {
        Matcher inline = INLINE_EPISODE.matcher(text);
        if (inline.find()) return normalizeEpisodeToken(inline.group(1));
        String bracketEpisode = findBracketEpisodeToken(text);
        if (!isEmpty(bracketEpisode)) return bracketEpisode;
        return findEpisodeToken(text);
    }

    private static String findBracketEpisodeToken(String text) {
        Matcher bracket = BRACKET_TOKEN.matcher(text);
        while (bracket.find()) {
            String value = cleanupEdge(bracket.group(1));
            if (BRACKET_EPISODE.matcher(value).matches()) return normalizeEpisodeToken(value);
        }
        return "";
    }

    private static String findStructuredEpisodeTitle(String text, String episode) {
        String title = findBracketedEpisodeTitle(text, episode);
        if (!isEmpty(title)) return title;
        Matcher inline = INLINE_EPISODE.matcher(text);
        while (inline.find()) {
            if (normalizeEpisodeToken(inline.group(1)).equals(normalizeEpisodeToken(episode))) return inline.group(2).trim();
        }
        return "";
    }

    private static String findPartLabel(String descriptor) {
        Matcher part = COLLECTION_PART.matcher(descriptor);
        if (!part.find()) return "";
        return "动画" + part.group(1).replaceAll("\\s+", "");
    }

    private static String findChapterLabel(String text) {
        Matcher chapter = COLLECTION_CHAPTER.matcher(text);
        return chapter.find() ? chapter.group(1) : "";
    }

    private static String firstTitleWord(String title) {
        String value = normalizeDisplayText(title);
        int split = value.indexOf(' ');
        return split <= 0 ? value : value.substring(0, split);
    }

    private static String normalizeDisplayText(String value) {
        return cleanupEdge(value).replaceAll("\\s+", " ");
    }

    private static String normalizeStageTitle(String value) {
        if (value.equals("覺醒")) return "觉醒";
        return value;
    }

    private static String findSeasonLabel(String descriptor) {
        Matcher chinese = CHINESE_SEASON.matcher(descriptor);
        if (chinese.find()) return chinese.group().replaceAll("\\s+", "");
        Matcher english = ENGLISH_SEASON.matcher(descriptor);
        if (!english.find()) return "";
        int season = Integer.parseInt(english.group(1));
        return String.format(Locale.ROOT, "S%02d", season);
    }

    private static String normalizeSpecialLabel(String value) {
        String label = value.toUpperCase(Locale.ROOT);
        if (label.equals("特別篇")) return "特别篇";
        if (label.equals("劇場版")) return "剧场版";
        return label;
    }

    private static String findBracketedEpisodeTitle(String text, String episode) {
        Matcher matcher = BRACKET_TOKEN.matcher(text);
        boolean episodeFound = false;
        while (matcher.find()) {
            String value = cleanupEdge(matcher.group(1));
            if (!episodeFound) {
                episodeFound = isSameEpisodeToken(value, episode);
            } else if (isEpisodeTitleCandidate(value)) {
                return value;
            }
        }
        return "";
    }

    private static boolean isSameEpisodeToken(String value, String episode) {
        String left = normalizeEpisodeToken(value);
        String right = normalizeEpisodeToken(episode);
        if (left.equals(right)) return true;
        if (!left.matches("[0-9]{1,4}") || !right.matches("[0-9]{1,4}")) return false;
        return Integer.parseInt(left) == Integer.parseInt(right);
    }

    private static boolean isEpisodeTitleCandidate(String value) {
        if (isEmpty(value) || RESOLUTION_TOKEN.matcher(value).matches()) return false;
        if (DATE_TOKEN.matcher(value).matches()) return false;
        if (isTechnicalToken(value)) return false;
        return !value.matches("(?i)(?:(?:EP?|S[0-9]{1,2}E|SP)?[0-9]{1,4}|[0-9]{1,3}(?:[.·][0-9]+)?(?:SP)?)");
    }

    private static boolean isTechnicalToken(String value) {
        String token = cleanupEdge(value);
        if (TECHNICAL_BRACKET.matcher(token).matches()) return true;
        if (TECHNICAL_DETAIL.matcher(token).matches()) return true;
        if (LANGUAGE_DETAIL.matcher(token).matches()) return true;
        return token.matches("(?i)(?:END|V[0-9]+|(?:HI)?10-?BIT|8-?BIT)");
    }

    private static String findLastMeaningfulBracketToken(String text) {
        List<String> values = findMeaningfulBracketTokens(text);
        return values.isEmpty() ? "" : values.get(values.size() - 1);
    }

    private static List<String> findMeaningfulBracketTokens(String text) {
        List<String> values = new ArrayList<>();
        Matcher matcher = BRACKET_TOKEN.matcher(text);
        while (matcher.find()) {
            String value = cleanupEdge(matcher.group(1));
            if (isEpisodeTitleCandidate(value)) values.add(value);
        }
        return values;
    }

    private static List<String> resolveCollectionVariants(List<String> names, List<String> displays) {
        List<String> result = new ArrayList<>(displays);
        Map<String, List<Integer>> groups = new HashMap<>();
        for (int i = 0; i < displays.size(); i++) {
            String display = displays.get(i);
            if (isEmpty(display)) continue;
            groups.computeIfAbsent(display, key -> new ArrayList<>()).add(i);
        }
        for (Map.Entry<String, List<Integer>> entry : groups.entrySet()) {
            List<Integer> indexes = entry.getValue();
            if (indexes.size() < 2) continue;
            List<List<String>> hints = new ArrayList<>();
            Set<String> common = null;
            for (int index : indexes) {
                List<String> values = findCollectionVariantHints(names.get(index));
                hints.add(values);
                if (common == null) common = new HashSet<>(values);
                else common.retainAll(values);
            }
            List<String> candidates = buildCollectionVariantCandidates(entry.getKey(), hints, common == null ? new HashSet<>() : common);
            if (!allDistinct(candidates)) continue;
            for (int i = 0; i < indexes.size(); i++) result.set(indexes.get(i), candidates.get(i));
        }
        return result;
    }

    private static List<String> findCollectionVariantHints(String text) {
        List<String> hints = new ArrayList<>();
        Matcher matcher = COLLECTION_VARIANT_TOKEN.matcher(text);
        while (matcher.find()) addHint(hints, normalizeVariantToken(matcher.group()));
        return hints;
    }

    private static List<String> buildCollectionVariantCandidates(String display, List<List<String>> hints, Set<String> common) {
        List<String> result = new ArrayList<>();
        for (List<String> values : hints) {
            StringBuilder builder = new StringBuilder(display);
            int added = 0;
            for (String value : values) {
                if (common.contains(value)) continue;
                builder.append(' ').append(value);
                if (++added == 2) break;
            }
            result.add(builder.toString());
        }
        return result;
    }

    private static List<String> resolveDuplicates(List<String> names, List<String> compacted, List<String> fallback, List<String> tokens, List<String> sizes, Map<String, Integer> count) {
        List<String> result = new ArrayList<>(compacted);
        for (Map.Entry<String, Integer> entry : count.entrySet()) {
            if (entry.getValue() <= 1) continue;
            List<Integer> indexes = indexesOf(compacted, entry.getKey());
            List<String> displays = buildDistinctDisplays(names, fallback, tokens, sizes, indexes);
            for (int i = 0; i < indexes.size(); i++) result.set(indexes.get(i), displays.get(i));
        }
        return ensureUnique(result);
    }

    private static List<Integer> indexesOf(List<String> values, String target) {
        List<Integer> indexes = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) if (values.get(i).equals(target)) indexes.add(i);
        return indexes;
    }

    private static List<String> buildDistinctDisplays(List<String> names, List<String> fallback, List<String> tokens, List<String> sizes, List<Integer> indexes) {
        List<String> firstCandidates = null;
        for (int level = 1; level <= 4; level++) {
            List<String> candidates = new ArrayList<>();
            for (int index : indexes) candidates.add(appendSize(buildVariantDisplay(names.get(index), tokens.get(index), fallback.get(index), level), sizes.get(index)));
            if (level == 1) firstCandidates = candidates;
            if (allDistinct(candidates)) return candidates;
        }
        return ensureUnique(firstCandidates == null ? new ArrayList<>() : firstCandidates);
    }

    private static String buildVariantDisplay(String name, String token, String fallback, int level) {
        if (isEmpty(token)) return fallback;
        List<String> hints = findVariantHints(name);
        if (hints.isEmpty()) return token;
        StringBuilder builder = new StringBuilder(token);
        for (int i = 0; i < Math.min(level, hints.size()); i++) builder.append(' ').append(hints.get(i));
        return builder.toString();
    }

    private static List<String> findVariantHints(String text) {
        List<String> hints = new ArrayList<>();
        Matcher matcher = VARIANT_TOKEN.matcher(text);
        while (matcher.find()) addHint(hints, normalizeVariantToken(matcher.group(1)));
        Matcher tail = TAIL_CODE.matcher(text);
        if (tail.find()) addHint(hints, tail.group(1));
        return hints;
    }

    private static void addHint(List<String> hints, String hint) {
        if (isEmpty(hint) || hints.contains(hint)) return;
        hints.add(hint);
    }

    private static String normalizeVariantToken(String token) {
        String value = token.replaceAll("\\s+", "").replace('⁺', '+').replace('·', '.').toUpperCase(Locale.ROOT);
        if (value.equals("H265")) return "HEVC";
        if (value.equals("H.265")) return "HEVC";
        if (value.equals("X265")) return "HEVC";
        if (value.equals("H264")) return "AVC";
        if (value.equals("H.264")) return "AVC";
        if (value.equals("X264")) return "AVC";
        if (value.matches("[0-9]{3,4}P")) return value.substring(0, value.length() - 1) + "p";
        if (value.matches("[0-9]{2}FPS")) return value.substring(0, value.length() - 3) + "fps";
        if (value.matches("[0-9]{1,2}BITS")) return value.substring(0, value.length() - 4) + "bits";
        if (value.matches("AAC2[.]?0")) return "AAC2.0";
        if (value.matches("DDP2[.]?0")) return "DDP2.0";
        return value.replace("HDR10+", "HDR10");
    }

    private static boolean allDistinct(List<String> values) {
        Map<String, Integer> count = new HashMap<>();
        for (String value : values) {
            count.put(value, count.getOrDefault(value, 0) + 1);
            if (count.get(value) > 1) return false;
        }
        return true;
    }

    private static List<String> ensureUnique(List<String> values) {
        Map<String, Integer> total = new HashMap<>();
        Map<String, Integer> used = new HashMap<>();
        List<String> result = new ArrayList<>();
        for (String value : values) total.put(value, total.getOrDefault(value, 0) + 1);
        for (String value : values) {
            if (total.get(value) <= 1) {
                result.add(value);
            } else {
                int index = used.getOrDefault(value, 0) + 1;
                used.put(value, index);
                result.add(value + "-" + index);
            }
        }
        return result;
    }

    private static String findEpisodeToken(String text) {
        for (Pattern pattern : EPISODE_TOKENS) {
            Matcher matcher = pattern.matcher(text);
            if (!matcher.find()) continue;
            String token = matcher.groupCount() > 0 && matcher.group(1) != null ? matcher.group(1) : matcher.group();
            if (isStandaloneYear(token)) continue;
            return normalizeEpisodeToken(token);
        }
        Matcher weak = WEAK_EPISODE_TOKEN.matcher(text);
        while (weak.find()) {
            if (isInsideMetadataBracket(text, weak.start())) continue;
            return normalizeEpisodeToken(weak.group());
        }
        Matcher bracket = BRACKET_TOKEN.matcher(text);
        while (bracket.find()) {
            String value = cleanupEdge(bracket.group(1));
            if (WEAK_EPISODE_TOKEN.matcher(value).matches()) return normalizeEpisodeToken(value);
        }
        return "";
    }

    private static boolean isInsideMetadataBracket(String text, int index) {
        int open = Math.max(text.lastIndexOf('[', index), text.lastIndexOf('【', index));
        int close = Math.max(text.lastIndexOf(']', index), text.lastIndexOf('】', index));
        return open > close;
    }

    private static String normalizeEpisodeToken(String token) {
        if (token == null) return "";
        String value = token.replaceAll("\\s+", "").replace('·', '.').toUpperCase(Locale.ROOT);
        if (value.matches("[0-9]{4}[-._][0-9]{1,2}[-._][0-9]{1,2}")) value = value.replace('.', '-').replace('_', '-');
        return value;
    }

    private static boolean isStandaloneYear(String token) {
        if (token == null || !token.matches("[0-9]{4}")) return false;
        int year = Integer.parseInt(token);
        return year >= 1900 && year <= 2099;
    }

    private static String cleanupEdge(String text) {
        return EDGE_SEPARATORS.matcher(text == null ? "" : text.trim()).replaceAll("");
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }
}
