package com.fongmi.android.tv.title;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.AppCache;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class MediaTitleLearningStore {

    private static final String KEY = AppCache.KEY_MEDIA_TITLE_LEARNING;
    private static final int MAX_ITEMS = 500;
    private static final Gson FALLBACK_GSON = new Gson();
    private static final Type ITEMS_TYPE = TypeToken.getParameterized(LinkedHashMap.class, String.class, MediaTitleLearningExample.class).getType();
    @SerializedName(value = "items", alternate = "a")
    private LinkedHashMap<String, MediaTitleLearningExample> items;

    public static MediaTitleLearningStore load() {
        return objectFrom(AppCache.get(KEY));
    }

    static MediaTitleLearningStore objectFrom(String json) {
        try {
            MediaTitleLearningStore store = new MediaTitleLearningStore();
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            JsonElement element = object.has("items") ? object.get("items") : object.get("a");
            LinkedHashMap<String, MediaTitleLearningExample> restored = gson().fromJson(element, ITEMS_TYPE);
            if (restored != null) store.items.putAll(restored);
            return store;
        } catch (Throwable e) {
            return new MediaTitleLearningStore();
        }
    }

    public MediaTitleLearningStore() {
        this.items = new LinkedHashMap<>();
    }

    public List<MediaTitleLearningExample> find(MediaTitleRequest request, int limit) {
        List<Scored> scored = new ArrayList<>();
        MediaTitleParser parser = new MediaTitleParser();
        String raw = request == null ? "" : request.getRawTitle();
        String rule = parser.cleanTitle(raw);
        String siteKey = request == null ? "" : request.getSiteKey();
        String vodId = request == null ? "" : request.getVodId();
        for (MediaTitleLearningExample example : getItems().values()) {
            if (example == null || !example.isUsable()) continue;
            double score = score(siteKey, vodId, raw, rule, example);
            if (score <= 0.15) continue;
            scored.add(new Scored(example, score));
        }
        scored.sort(Comparator
                .comparingDouble((Scored item) -> item.score).reversed()
                .thenComparing(item -> item.example.isManual(), Comparator.reverseOrder())
                .thenComparing(Comparator.comparingLong((Scored item) -> item.example.getUpdatedAt()).reversed()));
        List<MediaTitleLearningExample> result = new ArrayList<>();
        for (Scored item : scored) {
            result.add(item.example);
            if (result.size() >= limit) break;
        }
        return result;
    }

    public void putManual(String siteKey, String vodId, String rawTitle, String ruleTitle, String expectedTitle, String mediaType, int year, int seasonNumber, String source) {
        put(MediaTitleLearningExample.manual(rawTitle, ruleTitle, expectedTitle, mediaType, year, seasonNumber, source).identity(siteKey, vodId));
    }

    public void put(MediaTitleLearningExample example) {
        if (example == null || !example.isUsable()) return;
        String key = key(example);
        MediaTitleLearningExample old = getItems().get(key);
        if (old != null && sameExpected(old, example)) {
            old.bump();
            getItems().put(key, old);
        } else {
            getItems().put(key, example);
        }
        trim();
        save();
    }

    public void save() {
        AppCache.put(KEY, gson().toJson(this));
    }

    public Map<String, MediaTitleLearningExample> getItems() {
        if (items == null) items = new LinkedHashMap<>();
        return items;
    }

    private double score(String siteKey, String vodId, String rawTitle, String ruleTitle, MediaTitleLearningExample example) {
        double score = 0.0;
        MediaTitleParser parser = new MediaTitleParser();
        String rule = parser.cleanTitle(first(ruleTitle, rawTitle));
        double titleScore = Math.max(
                similarity(normalize(rule), normalize(parser.cleanTitle(example.getRuleTitle()))),
                similarity(normalize(rule), normalize(parser.cleanTitle(example.getRawTitle()))));
        if (!isBlank(siteKey) && !isBlank(vodId) && siteKey.equals(example.getSiteKey()) && vodId.equals(example.getVodId()) && (isBlank(rule) || titleScore >= 0.55)) score = 1.0;
        score = Math.max(score, titleScore);
        if (example.isManual()) score += 0.1;
        return Math.min(1.0, score);
    }

    private void trim() {
        if (getItems().size() <= MAX_ITEMS) return;
        List<Map.Entry<String, MediaTitleLearningExample>> entries = new ArrayList<>(getItems().entrySet());
        entries.sort(Comparator
                .comparing((Map.Entry<String, MediaTitleLearningExample> entry) -> entry.getValue().isManual()).reversed()
                .thenComparing(Comparator.comparingInt((Map.Entry<String, MediaTitleLearningExample> entry) -> entry.getValue().getHitCount()).reversed())
                .thenComparing(Comparator.comparingLong((Map.Entry<String, MediaTitleLearningExample> entry) -> entry.getValue().getUpdatedAt()).reversed()));
        LinkedHashMap<String, MediaTitleLearningExample> trimmed = new LinkedHashMap<>();
        for (Map.Entry<String, MediaTitleLearningExample> entry : entries) {
            if (trimmed.size() >= MAX_ITEMS) break;
            trimmed.put(entry.getKey(), entry.getValue());
        }
        items = trimmed;
    }

    private static String key(MediaTitleLearningExample example) {
        String exact = normalize(example.getSiteKey()) + "@@@" + normalize(example.getVodId());
        String title = normalize(first(example.getRawTitle(), example.getRuleTitle()));
        String source = normalize(example.getSource());
        return exact + "@@@" + title + "@@@" + source;
    }

    private static boolean sameExpected(MediaTitleLearningExample left, MediaTitleLearningExample right) {
        return normalize(left.getExpectedTitle()).equals(normalize(right.getExpectedTitle()));
    }

    private static double similarity(String left, String right) {
        if (left.isEmpty() || right.isEmpty()) return 0.0;
        if (left.equals(right)) return 1.0;
        if (left.contains(right) || right.contains(left)) return 0.85;
        int max = Math.max(left.length(), right.length());
        return (max - editDistance(left, right)) / (double) max;
    }

    private static int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int i = 0; i <= right.length(); i++) previous[i] = i;
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private static String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\s·•:：\\-_/\\\\|()（）\\[\\]【】]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private static String first(String... values) {
        for (String value : values) if (!isBlank(value)) return value;
        return "";
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }

    private static Gson gson() {
        return App.get() == null ? FALLBACK_GSON : App.gson();
    }

    private static final class Scored {

        private final MediaTitleLearningExample example;
        private final double score;

        private Scored(MediaTitleLearningExample example, double score) {
            this.example = example;
            this.score = score;
        }
    }
}
