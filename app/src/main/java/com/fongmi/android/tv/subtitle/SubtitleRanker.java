package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleMatchType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class SubtitleRanker {

    public List<SubtitleCandidate> rank(List<SubtitleCandidate> candidates, SubtitleContext context) {
        List<SubtitleCandidate> ranked = new ArrayList<>();
        if (candidates == null) return ranked;
        for (SubtitleCandidate candidate : candidates) ranked.add(score(candidate, context));
        ranked.sort(Comparator.comparingInt(SubtitleCandidate::getScore).reversed().thenComparing(SubtitleCandidate::getDisplayName));
        return ranked;
    }

    public SubtitleCandidate pickBest(List<SubtitleCandidate> ranked, SubtitleContext context) {
        if (ranked == null || ranked.isEmpty()) return null;
        SubtitleCandidate best = ranked.get(0);
        int threshold = threshold(context, ranked.size());
        if (best.getScore() < threshold) return null;
        if (!context.hasTmdbIdentity() && ranked.size() > 1 && best.getScore() - ranked.get(1).getScore() < 5) return null;
        return best;
    }

    private SubtitleCandidate score(SubtitleCandidate candidate, SubtitleContext context) {
        int score = candidate.getScore();
        SubtitleMatchType matchType = SubtitleMatchType.METADATA_FUZZY;
        String haystack = normalize(candidate.getDisplayName() + " " + candidate.getReleaseInfo());
        if (contains(haystack, context.getCanonicalTitle())) score += context.hasTmdbIdentity() ? 35 : 28;
        else for (String alias : context.getAliases()) if (contains(haystack, alias)) {
            score += 14;
            break;
        }
        if (!SubtitleStrings.isEmpty(context.getOriginalTitle()) && contains(haystack, context.getOriginalTitle())) score += 18;
        if (context.getYear() > 0) {
            if (candidate.getYear() == context.getYear() || haystack.contains(String.valueOf(context.getYear()))) score += 12;
            else if (candidate.getYear() > 0) score -= 6;
        }
        if (context.getSeasonNumber() > 0 && context.getEpisodeNumber() > 0) {
            boolean seasonMatch = candidate.getSeasonNumber() == context.getSeasonNumber() || haystack.contains(String.format(Locale.US, "s%02de%02d", context.getSeasonNumber(), context.getEpisodeNumber()));
            boolean episodeMatch = candidate.getEpisodeNumber() == context.getEpisodeNumber() || haystack.contains("第" + context.getEpisodeNumber() + "集");
            if (seasonMatch && episodeMatch) {
                score += 30;
                matchType = context.hasTmdbIdentity() ? SubtitleMatchType.TMDB_EXACT : SubtitleMatchType.METADATA_STRICT;
            } else if (candidate.getSeasonNumber() > 0 || candidate.getEpisodeNumber() > 0) {
                score -= 12;
            }
        } else if (context.getEpisodeNumber() > 0 && candidate.getEpisodeNumber() == context.getEpisodeNumber()) {
            score += 12;
            matchType = SubtitleMatchType.METADATA_STRICT;
        }
        if (!SubtitleStrings.isEmpty(context.getEpisodeTitle()) && contains(haystack, context.getEpisodeTitle())) score += 10;
        score += languageScore(candidate.getLanguage(), context.getPreferredLanguage());
        score += formatScore(candidate.getFormat());
        if (matchType == SubtitleMatchType.METADATA_FUZZY && score >= (context.hasTmdbIdentity() ? 72 : 65)) matchType = SubtitleMatchType.METADATA_STRICT;
        return candidate.withScore(score, matchType);
    }

    private int threshold(SubtitleContext context, int count) {
        if (context == null) return 70;
        if (context.hasTmdbIdentity()) return "tv".equalsIgnoreCase(context.getMediaType()) ? 60 : 55;
        if ("tv".equalsIgnoreCase(context.getMediaType())) return context.getEpisodeNumber() > 0 ? 68 : 78;
        if (context.getYear() <= 0 && count > 1) return 78;
        return 70;
    }

    private int languageScore(String language, String preferredLanguage) {
        String normalized = normalizeLanguage(language);
        String preferred = normalizeLanguageCode(preferredLanguage);
        if (SubtitleStrings.isEmpty(preferred) || "any".equals(preferred)) return 0;
        if (matchesPreferredLanguage(normalized, preferred)) return "zh".equals(preferred) ? 15 : 12;
        if (preferred.startsWith("zh") && isChineseLanguage(normalized)) return 10;
        return 0;
    }

    private boolean matchesPreferredLanguage(String language, String preferred) {
        if (language.contains(preferred)) return true;
        return switch (preferred) {
            case "zh" -> isChineseLanguage(language);
            case "zhhans" -> containsAny(language, "simplified", "simplifiedchinese", "简体", "简中", "简", "chs", "sc", "gb", "zhhans");
            case "zhhant" -> containsAny(language, "traditional", "traditionalchinese", "繁体", "繁中", "繁", "cht", "tc", "big5", "zhhant");
            case "en" -> containsAny(language, "english", "英文", "英语", "英語", "eng", "en");
            case "ja" -> containsAny(language, "japanese", "日语", "日文", "日語", "jp", "ja");
            case "ko" -> containsAny(language, "korean", "韩语", "韓語", "韩文", "韓文", "kr", "ko");
            default -> false;
        };
    }

    private boolean isChineseLanguage(String language) {
        return containsAny(language, "中文", "中字", "双语", "雙語", "简", "繁", "粵", "粤", "国语", "國語", "chi", "chs", "cht", "zh");
    }

    private String normalizeLanguage(String value) {
        return normalize(value);
    }

    private String normalizeLanguageCode(String value) {
        String normalized = normalize(value).replace("traditional", "hant").replace("simplified", "hans");
        if (SubtitleStrings.isEmpty(normalized)) return "zh";
        if (normalized.startsWith("zhhans") || normalized.equals("chs") || normalized.equals("sc")) return "zhhans";
        if (normalized.startsWith("zhhant") || normalized.startsWith("zhtw") || normalized.equals("cht") || normalized.equals("tc")) return "zhhant";
        if (normalized.startsWith("zh")) return "zh";
        if (normalized.startsWith("en")) return "en";
        if (normalized.startsWith("ja") || normalized.equals("jp")) return "ja";
        if (normalized.startsWith("ko") || normalized.equals("kr")) return "ko";
        if (normalized.equals("any") || normalized.equals("all")) return "any";
        return normalized;
    }

    private boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (!SubtitleStrings.isEmpty(needle) && value.contains(needle.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private int formatScore(String format) {
        if (SubtitleStrings.isEmpty(format)) return 0;
        String lower = format.toLowerCase(Locale.ROOT);
        if (lower.contains("ssa") || lower.contains("ass")) return 10;
        if (lower.contains("srt")) return 6;
        if (lower.contains("vtt")) return 4;
        return 0;
    }

    private boolean contains(String haystack, String needle) {
        return !SubtitleStrings.isEmpty(needle) && normalize(haystack).contains(normalize(needle));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\u4e00-\u9fa5]+", "");
    }
}
