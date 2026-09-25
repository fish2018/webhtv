package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Vod;
import com.github.catvod.utils.Trans;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SearchResultFilter {

    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)(?:第\\s*([0-9零〇一二三四五六七八九十百两]+)\\s*(?:季|部)|season\\s*0*([0-9]{1,3})|\\bs\\s*0*([0-9]{1,3})(?=\\s*(?:e\\s*[0-9]{1,4})?\\b))");
    private static final Pattern NON_TOKEN = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private SearchResultFilter() {
    }

    public static boolean matches(String keyword, String title, int similarityPercent) {
        if (similarityPercent <= 0) return true;
        return score(normalize(keyword), normalize(title)) >= clamp(similarityPercent);
    }

    public static List<Vod> filter(List<Vod> items, String keyword, int similarityPercent) {
        if (items == null || items.isEmpty()) return new ArrayList<>();
        int threshold = clamp(similarityPercent);
        if (threshold == 0) return new ArrayList<>(items);
        Normalized query = normalize(keyword);
        List<Vod> result = new ArrayList<>();
        for (Vod item : items) {
            if (item != null && score(query, normalize(item.getName())) >= threshold) result.add(item);
        }
        return result;
    }

    private static int score(Normalized query, Normalized candidate) {
        if (query.compact.isEmpty() || candidate.compact.isEmpty()) return 0;
        if (candidate.compact.equals(query.compact)) return 100;
        if (canContain(query) && contains(candidate, query)) {
            int coverage = Math.round(query.compact.length() * 100f / candidate.compact.length());
            return Math.min(99, coverage);
        }
        if (!numbersCompatible(candidate.digits, query.digits)) return 0;
        int maxLength = Math.max(candidate.compact.length(), query.compact.length());
        if (maxLength < 4) return 0;
        int maxGap = Math.max(2, (int) Math.floor(maxLength * 0.2));
        if (Math.abs(candidate.compact.length() - query.compact.length()) > maxGap) return 0;
        return (int) Math.round(similarity(candidate.compact, query.compact) * 100);
    }

    private static int clamp(int similarityPercent) {
        return Math.max(0, Math.min(100, similarityPercent));
    }

    private static boolean canContain(Normalized query) {
        return query.compact.length() >= 2;
    }

    private static boolean contains(Normalized candidate, Normalized query) {
        if (hasHan(query.compact) || query.compact.length() >= 3) {
            int start = candidate.compact.indexOf(query.compact);
            while (start >= 0) {
                if (!cutsNumericToken(candidate, query, start)) return true;
                start = candidate.compact.indexOf(query.compact, start + 1);
            }
            return false;
        }
        String paddedCandidate = " " + candidate.tokens + " ";
        String paddedQuery = " " + query.tokens + " ";
        return paddedCandidate.contains(paddedQuery);
    }

    private static boolean cutsNumericToken(Normalized candidate, Normalized query, int start) {
        int end = start + query.compact.length();
        boolean cutsLeadingNumber = Character.isDigit(query.compact.charAt(0)) && start > 0 && Character.isDigit(candidate.compact.charAt(start - 1)) && sameToken(candidate.tokens, start - 1, start);
        boolean cutsTrailingNumber = Character.isDigit(query.compact.charAt(query.compact.length() - 1)) && end < candidate.compact.length() && Character.isDigit(candidate.compact.charAt(end)) && sameToken(candidate.tokens, end - 1, end);
        return cutsLeadingNumber || cutsTrailingNumber;
    }

    private static boolean sameToken(String tokens, int leftCompactIndex, int rightCompactIndex) {
        int leftToken = tokenIndex(tokens, leftCompactIndex);
        return leftToken >= 0 && leftToken == tokenIndex(tokens, rightCompactIndex);
    }

    private static int tokenIndex(String tokens, int compactIndex) {
        int compactPosition = 0;
        int tokenIndex = 0;
        for (int i = 0; i < tokens.length(); i++) {
            if (tokens.charAt(i) == ' ') {
                tokenIndex++;
            } else if (compactPosition++ == compactIndex) {
                return tokenIndex;
            }
        }
        return -1;
    }


    private static boolean numbersCompatible(String candidateDigits, String queryDigits) {
        if (queryDigits.isEmpty()) return true;
        return queryDigits.equals(candidateDigits);
    }

    static double similarity(String left, String right) {
        int maxLength = Math.max(left.length(), right.length());
        if (maxLength == 0) return 1.0;
        return (maxLength - editDistance(left, right)) / (double) maxLength;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int replace = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                int insert = current[j - 1] + 1;
                int delete = previous[j] + 1;
                current[j] = Math.min(replace, Math.min(insert, delete));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[right.length()];
    }

    private static Normalized normalize(String text) {
        String value = text == null ? "" : text;
        value = Normalizer.normalize(Trans.t2s(false, value), Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        value = value.replace('馀', '余');
        value = normalizeSeason(value);
        String tokens = SPACES.matcher(NON_TOKEN.matcher(value).replaceAll(" ").trim()).replaceAll(" ");
        String compact = tokens.replace(" ", "");
        return new Normalized(tokens, compact, digits(compact));
    }

    private static String normalizeSeason(String value) {
        Matcher matcher = SEASON_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String raw = firstNonEmpty(matcher.group(1), matcher.group(2), matcher.group(3));
            int number = parseNumber(raw);
            matcher.appendReplacement(result, Matcher.quoteReplacement(number > 0 ? String.valueOf(number) : raw));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) if (value != null && !value.isEmpty()) return value;
        return "";
    }

    private static int parseNumber(String value) {
        if (value == null || value.isEmpty()) return 0;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            int total = 0;
            int current = 0;
            for (int i = 0; i < value.length(); i++) {
                char ch = value.charAt(i);
                if (ch == '十') {
                    total += (current == 0 ? 1 : current) * 10;
                    current = 0;
                } else if (ch == '百') {
                    total += (current == 0 ? 1 : current) * 100;
                    current = 0;
                } else {
                    current = chineseDigit(ch);
                }
            }
            return total + current;
        }
    }

    private static int chineseDigit(char value) {
        return switch (value) {
            case '一' -> 1;
            case '二', '两' -> 2;
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

    private static String digits(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isDigit(ch)) result.append(ch);
        }
        return result.toString();
    }

    private static boolean hasHan(String value) {
        return value.codePoints().anyMatch(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
    }

    private record Normalized(String tokens, String compact, String digits) {
    }
}
