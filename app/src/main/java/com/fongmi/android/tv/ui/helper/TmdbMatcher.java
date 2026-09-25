package com.fongmi.android.tv.ui.helper;

import android.text.TextUtils;

import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TMDB 匹配器
 * 负责根据视频名称搜索并匹配 TMDB 数据
 */
public class TmdbMatcher {

    private static final Pattern SOURCE_SEASON = Pattern.compile("(?i)(?:第\\s*([零〇一二三四五六七八九十两0-9]+)\\s*[季部]|season\\s*([0-9]{1,2})|s([0-9]{1,2})(?:[-._\\s]*e[0-9]{1,3})?)");

    private final TmdbService tmdbService;
    private final TmdbConfig tmdbConfig;

    public TmdbMatcher(TmdbService tmdbService, TmdbConfig tmdbConfig) {
        this.tmdbService = tmdbService;
        this.tmdbConfig = tmdbConfig;
    }

    /**
     * 搜索并匹配最佳 TMDB 项
     *
     * @param videoName 视频名称
     * @return 最佳匹配项，未找到返回 null
     */
    public TmdbItem searchAndMatch(String videoName) {
        return searchAndMatch(videoName, null);
    }

    public TmdbItem searchAndMatch(String videoName, String mediaType, int year) {
        return searchAndMatch(videoName, null, normalizeMediaType(mediaType), year);
    }

    public TmdbItem searchAndMatch(String videoName, Vod vod) {
        return searchAndMatch(videoName, vod, "", 0);
    }

    private TmdbItem searchAndMatch(String videoName, Vod vod, String expectedMediaType, int expectedYear) {
        if (TextUtils.isEmpty(videoName) || !tmdbConfig.isReady()) {
            SpiderDebug.log("TMDB 匹配跳过: name=" + videoName + " ready=" + tmdbConfig.isReady());
            return null;
        }

        try {
            String cleanName = cleanVideoName(videoName);
            SpiderDebug.log("TMDB 搜索: " + cleanName);

            List<TmdbItem> results = filterByMediaType(search(cleanName), expectedMediaType);
            int matchYear = expectedYear > 0 ? expectedYear : sourceYear(cleanName, vod);
            TmdbItem match = chooseTmdbMatch(results, cleanName, vod, matchYear);
            if (match == null) {
                SplitYearQuery split = splitYearQuery(cleanName, vod, expectedYear);
                if (split != null) {
                    SpiderDebug.log("TMDB 年份拆分重试: title=" + split.query + " year=" + split.year);
                    match = chooseTmdbMatch(filterByMediaType(search(split.query), expectedMediaType), split.query, vod, split.year);
                }
            }
            if (match == null) {
                SpiderDebug.log("TMDB 匹配失败: " + cleanName);
                return null;
            }

            SpiderDebug.log("TMDB 匹配成功: " + match.getTitle() + " [" + match.getMediaType() + "]");
            return match;

        } catch (TmdbService.AuthException | CancellationException e) {
            SpiderDebug.log("TMDB 搜索中止: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            SpiderDebug.log("TMDB 搜索失败: " + e.getMessage());
            return null;
        }
    }

    private List<TmdbItem> filterByMediaType(List<TmdbItem> items, String expectedMediaType) {
        if (items == null || items.isEmpty() || TextUtils.isEmpty(expectedMediaType)) return items;
        List<TmdbItem> filtered = new ArrayList<>();
        for (TmdbItem item : items) {
            if (item != null && expectedMediaType.equals(normalizeMediaType(item.getMediaType()))) filtered.add(item);
        }
        return filtered;
    }

    private String normalizeMediaType(String mediaType) {
        if ("tv".equalsIgnoreCase(mediaType)) return "tv";
        if ("movie".equalsIgnoreCase(mediaType)) return "movie";
        return "";
    }

    public List<TmdbItem> search(String keyword) throws Exception {
        return search(keyword, null);
    }

    public List<TmdbItem> search(String keyword, Vod vod) throws Exception {
        String query = cleanVideoName(keyword);
        List<TmdbItem> results = tmdbService.search(query, tmdbConfig);
        String raw = Objects.toString(keyword, "").trim();
        String sortKeyword = TextUtils.isEmpty(raw) ? query : raw;
        if (!results.isEmpty() || TextUtils.isEmpty(raw) || raw.equals(query)) {
            sortSearchResults(results, sortKeyword, sourceYear(sortKeyword, vod));
            return results;
        }
        results = tmdbService.search(raw, tmdbConfig);
        sortSearchResults(results, raw, sourceYear(raw, vod));
        return results;
    }

    private TmdbItem chooseTmdbMatch(List<TmdbItem> items, String keyword, Vod vod) {
        return chooseTmdbMatch(items, keyword, vod, sourceYear(keyword, vod));
    }

    private TmdbItem chooseTmdbMatch(List<TmdbItem> items, String keyword, Vod vod, int sourceYear) {
        if (items == null || items.isEmpty()) return null;
        TmdbItem strict = chooseStrictMatch(items, keyword, vod, sourceYear);
        if (strict != null) return strict;
        TmdbItem containedYear = chooseContainedYearMatch(items, keyword, vod, sourceYear);
        if (containedYear != null || !Setting.isTmdbSmartMatch()) return containedYear;
        return chooseSmartMatch(items, keyword, vod, sourceYear);
    }

    private TmdbItem chooseStrictMatch(List<TmdbItem> items, String keyword, Vod vod, int sourceYear) {
        String normalized = normalize(keyword);
        int season = sourceSeasonNumber(keyword, vod);
        List<TmdbItem> matches = new ArrayList<>();
        for (TmdbItem item : items) {
            if (!normalize(item.getTitle()).equals(normalized)) continue;
            if (sourceYear <= 0 || tmdbItemYear(item) == sourceYear || tmdbSeasonYearMatches(item, season, sourceYear)) matches.add(item);
        }
        if (matches.isEmpty()) return null;
        if (matches.size() == 1) return isUnwantedSplitSeasonMatch(matches.get(0), keyword, vod) ? null : matches.get(0);
        TmdbItem detailChoice = chooseBySplitSeasonDetails(matches, keyword, vod);
        return detailChoice == null ? matches.get(0) : detailChoice;
    }

    private TmdbItem chooseContainedYearMatch(List<TmdbItem> items, String keyword, Vod vod, int sourceYear) {
        if (sourceYear <= 0) return null;
        String normalized = normalize(keyword);
        if (normalized.length() < 4) return null;
        for (TmdbItem item : items) {
            int itemYear = tmdbItemYear(item);
            if (itemYear != sourceYear) continue;
            String title = normalize(removeYearFromTitle(item.getTitle(), itemYear));
            if (Math.min(title.length(), normalized.length()) < 4) continue;
            if (!title.contains(normalized) && !normalized.contains(title)) continue;
            if (isUnwantedSplitSeasonMatch(item, keyword, vod)) continue;
            return item;
        }
        return null;
    }

    private TmdbItem chooseSmartMatch(List<TmdbItem> items, String keyword, Vod vod, int sourceYear) {
        String normalized = normalize(keyword);
        TmdbItem sameTitle = null;
        TmdbItem closeYear = null;
        for (TmdbItem item : items) {
            String title = normalize(item.getTitle());
            if (TextUtils.isEmpty(title) || TextUtils.isEmpty(normalized)) continue;
            int itemYear = tmdbItemYear(item);
            boolean titleMatch = title.equals(normalized);
            boolean cleanedTitleMatch = normalize(removeYearFromTitle(item.getTitle(), itemYear)).equals(normalized);
            if (!titleMatch && !cleanedTitleMatch) continue;
            if (sourceYear <= 0) {
                if (isUnwantedSplitSeasonMatch(item, keyword, vod)) continue;
                return item;
            }
            if (itemYear == sourceYear) {
                if (isUnwantedSplitSeasonMatch(item, keyword, vod)) continue;
                return item;
            }
            if (itemYear > 0 && Math.abs(itemYear - sourceYear) <= 1 && closeYear == null) closeYear = item;
            if (sameTitle == null) sameTitle = item;
        }
        if (closeYear != null) return isUnwantedSplitSeasonMatch(closeYear, keyword, vod) ? null : closeYear;
        if (sourceYear <= 0 && sameTitle != null) return isUnwantedSplitSeasonMatch(sameTitle, keyword, vod) ? null : sameTitle;
        return null;
    }

    public void sortSearchResults(List<TmdbItem> items, String keyword) {
        sortSearchResults(items, keyword, sourceYear(keyword, null));
    }

    private void sortSearchResults(List<TmdbItem> items, String keyword, int sourceYear) {
        if (items == null || items.size() < 2) return;
        items.sort((a, b) -> {
            int compare = Integer.compare(titleSimilarityScore(b, keyword), titleSimilarityScore(a, keyword));
            if (compare != 0) return compare;
            compare = compareYear(a, b, sourceYear);
            if (compare != 0) return compare;
            compare = Integer.compare(localePreferenceScore(b), localePreferenceScore(a));
            if (compare != 0) return compare;
            return Double.compare(b.getRating(), a.getRating());
        });
    }

    private int titleSimilarityScore(TmdbItem item, String keyword) {
        int keywordYear = firstYear(keyword);
        String query = normalize(removeYearFromTitle(keyword, keywordYear));
        String title = normalize(removeYearFromTitle(item.getTitle(), tmdbItemYear(item)));
        if (TextUtils.isEmpty(query) || TextUtils.isEmpty(title)) return 0;
        if (title.equals(query)) return 1000;
        if (title.contains(query) || query.contains(title)) {
            int min = Math.min(title.length(), query.length());
            int max = Math.max(title.length(), query.length());
            return 800 + Math.round(200f * min / Math.max(1, max));
        }
        int max = Math.max(title.length(), query.length());
        int distance = levenshteinDistance(title, query);
        return Math.max(0, 700 - Math.round(700f * distance / Math.max(1, max)));
    }

    private int compareYear(TmdbItem first, TmdbItem second, int sourceYear) {
        int firstYear = tmdbItemYear(first);
        int secondYear = tmdbItemYear(second);
        if (sourceYear > 0) {
            int compare = Integer.compare(yearDistance(firstYear, sourceYear), yearDistance(secondYear, sourceYear));
            if (compare != 0) return compare;
        }
        int normalizedFirst = firstYear > 0 ? firstYear : -1;
        int normalizedSecond = secondYear > 0 ? secondYear : -1;
        return Integer.compare(normalizedSecond, normalizedFirst);
    }

    private int yearDistance(int year, int sourceYear) {
        return year > 0 ? Math.abs(year - sourceYear) : 9999;
    }

    private int localePreferenceScore(TmdbItem item) {
        String language = item.getOriginalLanguage().toLowerCase(Locale.ROOT);
        String country = item.getOriginCountry().toUpperCase(Locale.ROOT);
        String preferredLanguage = preferredLanguage();
        String preferredCountry = preferredCountry();
        int score = 0;
        if (!TextUtils.isEmpty(preferredCountry) && country.equals(preferredCountry)) score += 40;
        if (isPreferredRegion(country, preferredLanguage)) score += 25;
        if (!TextUtils.isEmpty(preferredLanguage) && language.equals(preferredLanguage)) score += 20;
        return score;
    }

    private boolean isPreferredRegion(String country, String preferredLanguage) {
        if (TextUtils.isEmpty(country) || TextUtils.isEmpty(preferredLanguage)) return false;
        if ("zh".equals(preferredLanguage)) return country.equals("CN") || country.equals("HK") || country.equals("TW") || country.equals("MO") || country.equals("SG");
        if ("ja".equals(preferredLanguage)) return country.equals("JP");
        if ("ko".equals(preferredLanguage)) return country.equals("KR");
        return false;
    }

    private String preferredLanguage() {
        String language = Objects.toString(tmdbConfig.getLanguage(), "").trim();
        int index = language.indexOf('-');
        return (index > 0 ? language.substring(0, index) : language).toLowerCase(Locale.ROOT);
    }

    private String preferredCountry() {
        String language = Objects.toString(tmdbConfig.getLanguage(), "").trim();
        int index = language.indexOf('-');
        return index > 0 && index < language.length() - 1 ? language.substring(index + 1).toUpperCase(Locale.ROOT) : "";
    }

    private int levenshteinDistance(String first, String second) {
        int[] previous = new int[second.length() + 1];
        int[] current = new int[second.length() + 1];
        for (int i = 0; i <= second.length(); i++) previous[i] = i;
        for (int i = 1; i <= first.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= second.length(); j++) {
                int cost = first.charAt(i - 1) == second.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[second.length()];
    }

    /**
     * 清理视频名称，移除干扰字符
     */
    public String cleanVideoName(String name) {
        if (TextUtils.isEmpty(name)) return "";

        String raw = name.trim();
        String clean = raw;

        // 移除常见标签
        clean = clean.replaceAll("(?i)\\.(mkv|mp4|avi|mov|wmv|flv|rmvb|ts|m2ts)$", "");
        clean = removeNoiseBrackets(clean);

        // 移除季集标记
        clean = clean.replaceAll("(?i)S\\d+E\\d+", "");
        clean = clean.replaceAll("(?i)\\b(S\\d{1,2}|Season\\s*\\d{1,2})\\b", "");
        clean = clean.replaceAll("(?i)第\\d+季", "");
        clean = clean.replaceAll("(?i)第\\d+集", "");
        clean = clean.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[季部]", "");
        clean = clean.replaceAll("第\\s*[一二三四五六七八九十百零〇两0-9]+\\s*[集话話]", "");

        // 移除清晰度标记
        clean = clean.replaceAll("(?i)\\b(HD|4K|8K|1080P|2160P|720P|HDR|HDR10|DV|BluRay|WEB[- ]?DL|HDTV|BDRip|Remux|HEVC|H\\.?265|H\\.?264|x265|x264)\\b", "");
        clean = clean.replaceAll("(?i)(?<!\\d)(?:24|25|30|50|60|120)\\s*(?:fps|帧)(?![\\u4e00-\\u9fffA-Za-z0-9])", "");
        clean = clean.replaceAll("(更新至|更至|连载至|連載至)\\s*$", "");
        clean = clean.replaceAll("(国语版|国配版|普通话版|粤语版|台语版|闽南语版|原声版|配音版|中字版|字幕版|台版|台灣版|台湾版|港版|港澳版|大陆版|內地版|内地版|中国版|中國版|泰版|泰国版|泰國版|韩版|韩国版|韓國版|日版|日本版|美版|美国版|美國版|英版|英国版|英國版)", "");
        clean = clean.replaceAll("(真彩|臻彩|高码|高码率|无水印|无台标|国语|国配|国粤|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减|加长版|修复版)", "");
        clean = clean.replaceAll("[#＃]+", "");
        clean = clean.replaceAll("(?i)(^|\\s)(动漫|动画|电视剧|剧集|电影|综艺)(\\s|$)", " ");

        // 移除多余空格
        clean = clean.replaceAll("[._\\-+]+", " ");
        clean = clean.trim().replaceAll("\\s+", " ");
        clean = clean.replaceAll("(?i)^[a-z]\\s+(?=.*[\\u4e00-\\u9fff])", "");
        if (clean.matches("(?i).*[\\u4e00-\\u9fff].*\\s+[a-z]")) clean = clean.replaceAll("(?i)\\s+[a-z]$", "");
        clean = clean.replaceAll("([\\u4e00-\\u9fff])\\s+([\\u4e00-\\u9fff])", "$1$2");
        clean = clean.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");

        return TextUtils.isEmpty(clean) ? raw : clean;
    }

    /**
     * 从标题中提取年份
     */
    public Integer extractYear(String title) {
        if (TextUtils.isEmpty(title)) return null;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");
        java.util.regex.Matcher matcher = pattern.matcher(title);

        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return null;
    }

    private int sourceYear(String keyword, Vod vod) {
        int year = vod == null ? 0 : firstYear(vod.getYear());
        if (year > 0) return year;
        year = vod == null ? 0 : firstYear(vod.getName());
        return year > 0 ? year : firstYear(keyword);
    }

    private int sourceSeasonNumber(String keyword, Vod vod) {
        int number = vod == null ? -1 : sourceSeasonNumber(vod.getName());
        return number > 0 ? number : sourceSeasonNumber(keyword);
    }

    private int sourceSeasonNumber(String text) {
        if (TextUtils.isEmpty(text)) return -1;
        Matcher matcher = SOURCE_SEASON.matcher(text);
        while (matcher.find()) {
            int number = normalizeSourceNumber(firstNonEmptyGroup(matcher, 1, 2, 3));
            if (number > 0) return number;
        }
        return -1;
    }

    private SplitYearQuery splitYearQuery(String keyword, Vod vod, int expectedYear) {
        int year = expectedYear > 0 ? expectedYear : sourceYear(keyword, vod);
        if (year <= 0) return null;
        String source = !TextUtils.isEmpty(keyword) && firstYear(keyword) == year ? keyword : vod == null ? "" : vod.getName();
        if (firstYear(source) != year) return null;
        String query = removeYearFromTitle(source, year);
        if (TextUtils.isEmpty(query) || normalize(query).equals(normalize(source))) return null;
        return new SplitYearQuery(query, year);
    }

    private String removeYearFromTitle(String text, int year) {
        if (TextUtils.isEmpty(text)) return "";
        String cleaned = year > 0 ? text.replaceAll("(?<!\\d)" + year + "(?!\\d)", " ") : text;
        cleaned = cleaned.replaceAll("[\\[【「『(（]\\s*[\\]】」』)）]", " ");
        cleaned = cleaned.replaceAll("[._\\-+]+", " ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("^[\\s:：,，.。·|/\\\\]+|[\\s:：,，.。·|/\\\\]+$", "");
        return cleanVideoName(cleaned);
    }

    private int tmdbItemYear(TmdbItem item) {
        int year = firstYear(item.getSubtitle());
        return year > 0 ? year : firstYear(item.getTitle());
    }

    private boolean tmdbSeasonYearMatches(TmdbItem item, int seasonNumber, int sourceYear) {
        if (item == null || seasonNumber <= 0 || sourceYear <= 0 || !"tv".equalsIgnoreCase(item.getMediaType())) return false;
        try {
            JsonObject detail = tmdbService.detail(item, tmdbConfig, false);
            return tmdbSeasonYear(detail, seasonNumber) == sourceYear;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private int tmdbSeasonYear(JsonObject detail, int seasonNumber) {
        for (JsonElement element : array(detail, "seasons")) {
            if (!element.isJsonObject()) continue;
            JsonObject object = element.getAsJsonObject();
            if (!object.has("season_number") || object.get("season_number").isJsonNull()) continue;
            if (object.get("season_number").getAsInt() != seasonNumber) continue;
            int year = firstYear(string(object, "air_date"));
            return year > 0 ? year : firstYear(string(object, "name"));
        }
        return 0;
    }

    private TmdbItem chooseBySplitSeasonDetails(List<TmdbItem> matches, String keyword, Vod vod) {
        TmdbItem best = null;
        int bestScore = Integer.MIN_VALUE;
        int secondScore = Integer.MIN_VALUE;
        String source = matchSourceText(keyword, vod);
        for (TmdbItem item : matches) {
            try {
                JsonObject detail = tmdbService.detail(item, tmdbConfig, false);
                int score = TmdbMatchPolicy.splitSeasonDetailScore(source, detail);
                if (score > bestScore) {
                    secondScore = bestScore;
                    bestScore = score;
                    best = item;
                } else if (score > secondScore) {
                    secondScore = score;
                }
            } catch (Throwable ignored) {
            }
        }
        if (best == null || bestScore <= 0) return null;
        return bestScore - secondScore >= 200 ? best : null;
    }

    private boolean isUnwantedSplitSeasonMatch(TmdbItem item, String keyword, Vod vod) {
        try {
            JsonObject detail = tmdbService.detail(item, tmdbConfig, false);
            return TmdbMatchPolicy.isUnwantedSplitSeasonVariant(matchSourceText(keyword, vod), detail);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String matchSourceText(String keyword, Vod vod) {
        StringBuilder builder = new StringBuilder(Objects.toString(keyword, ""));
        if (vod != null) {
            builder.append(' ').append(Objects.toString(vod.getName(), ""));
            builder.append(' ').append(Objects.toString(vod.getRemarks(), ""));
        }
        return builder.toString();
    }

    private int firstYear(String text) {
        Matcher matcher = Pattern.compile("(19\\d{2}|20\\d{2})").matcher(Objects.toString(text, ""));
        while (matcher.find()) {
            int year = Integer.parseInt(matcher.group(1));
            if (year >= 1900 && year <= 2099) return year;
        }
        return 0;
    }

    private String normalize(String text) {
        return Objects.toString(text, "").replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private String removeNoiseBrackets(String text) {
        Matcher matcher = Pattern.compile("[\\[【「『(（]([^\\]】」』)）]{1,40})[\\]】」』)）]").matcher(Objects.toString(text, ""));
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String value = matcher.group(1);
            matcher.appendReplacement(buffer, isNoiseTag(value) ? " " : Matcher.quoteReplacement(matcher.group()));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private boolean isNoiseTag(String text) {
        String value = normalize(text);
        if (TextUtils.isEmpty(value)) return true;
        return value.matches("(?i).*(4k|8k|1080p|2160p|720p|hdr|hdr10|dv|webdl|bluray|bdrip|remux|hevc|h265|h264|x265|x264|aac|dts|ddp|atmos|nf|netflix|amzn|dsnp).*")
                || value.matches(".*(真彩|臻彩|高码|高码率|无水印|无台标|国语|国配|国粤|粤语|中字|字幕|内封|简繁|双语|官中|杜比|合集|全集|完结|未删减|加长版|修复版).*")
                || value.matches(".*(国语版|国配版|普通话版|粤语版|台语版|闽南语版|原声版|配音版|中字版|字幕版|台版|台灣版|台湾版|港版|港澳版|大陆版|內地版|内地版|中国版|中國版|泰版|泰国版|泰國版|韩版|韩国版|韓國版|日版|日本版|美版|美国版|美國版|英版|英国版).*");
    }

    private int normalizeSourceNumber(String value) {
        if (TextUtils.isEmpty(value)) return -1;
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
        if (TextUtils.isEmpty(value)) return 0;
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

    private String firstNonEmptyGroup(Matcher matcher, int... groups) {
        for (int group : groups) {
            String value = matcher.group(group);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private JsonArray array(JsonObject object, String... keys) {
        JsonElement current = object;
        for (String key : keys) {
            if (current == null || !current.isJsonObject()) return new JsonArray();
            JsonObject currentObject = current.getAsJsonObject();
            if (!currentObject.has(key) || currentObject.get(key).isJsonNull()) return new JsonArray();
            current = currentObject.get(key);
        }
        return current != null && current.isJsonArray() ? current.getAsJsonArray() : new JsonArray();
    }

    private String string(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object != null && object.has(key) && !object.get(key).isJsonNull()) {
                String value = object.get(key).getAsString();
                if (!TextUtils.isEmpty(value)) return value.trim();
            }
        }
        return "";
    }

    private static class SplitYearQuery {

        private final String query;
        private final int year;

        private SplitYearQuery(String query, int year) {
            this.query = query;
            this.year = year;
        }
    }
}
