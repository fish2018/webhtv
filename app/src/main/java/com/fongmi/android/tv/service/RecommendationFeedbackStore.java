package com.fongmi.android.tv.service;

import com.fongmi.android.tv.bean.TmdbItem;
import com.github.catvod.utils.Prefers;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RecommendationFeedbackStore {

    private static final String KEY = "recommendation_not_interested";
    private static final int MAX_ITEMS = 200;
    static final long DAY_MILLIS = TimeUnit.DAYS.toMillis(1);
    static final long RETENTION_MILLIS = TimeUnit.DAYS.toMillis(90);
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = TypeToken.getParameterized(List.class, Entry.class).getType();
    private static final Pattern YEAR = Pattern.compile("(19\\d{2}|20\\d{2})");

    private RecommendationFeedbackStore() {
    }

    public static synchronized void add(TmdbItem item, String source) {
        if (item == null || isBlank(item.getTitle())) return;
        List<Entry> entries = get();
        Entry added = Entry.from(item, source, System.currentTimeMillis());
        entries.removeIf(entry -> entry.matches(added));
        entries.add(0, added);
        if (entries.size() > MAX_ITEMS) entries = new ArrayList<>(entries.subList(0, MAX_ITEMS));
        Prefers.put(KEY, serialize(entries));
    }

    public static synchronized List<Entry> get() {
        List<Entry> entries = parse(Prefers.getString(KEY, ""));
        List<Entry> active = active(entries, System.currentTimeMillis());
        if (active.size() != entries.size()) Prefers.put(KEY, serialize(active));
        return active;
    }

    public static synchronized int size() {
        return get().size();
    }

    public static synchronized boolean remove(Entry target) {
        List<Entry> entries = get();
        List<Entry> remaining = without(entries, target);
        if (remaining.size() == entries.size()) return false;
        Prefers.put(KEY, serialize(remaining));
        return true;
    }

    public static synchronized int clear() {
        int count = get().size();
        Prefers.put(KEY, serialize(new ArrayList<>()));
        return count;
    }

    public static boolean contains(TmdbItem item) {
        return contains(get(), item);
    }

    static boolean contains(List<Entry> entries, TmdbItem item) {
        if (entries == null || item == null) return false;
        Entry target = Entry.from(item, "", 0);
        for (Entry entry : entries) if (entry != null && entry.matches(target)) return true;
        return false;
    }

    public static Set<String> blockedTitles() {
        Set<String> titles = new HashSet<>();
        for (Entry entry : get()) {
            String normalized = normalize(entry.normalizedTitle);
            if (!normalized.isEmpty()) titles.add(normalized);
        }
        return titles;
    }

    public static String fingerprint() {
        StringBuilder builder = new StringBuilder();
        for (Entry entry : get()) {
            builder.append(normalize(entry.normalizedTitle)).append('|')
                    .append(normalize(entry.mediaType)).append('|')
                    .append(normalize(entry.source)).append(';');
        }
        return builder.toString();
    }

    public static int remainingDays(Entry entry) {
        return remainingDays(entry, System.currentTimeMillis());
    }

    static List<Entry> active(List<Entry> entries, long now) {
        List<Entry> result = new ArrayList<>();
        if (entries == null) return result;
        for (Entry entry : entries) if (isActive(entry, now)) result.add(entry);
        return result;
    }

    static List<Entry> without(List<Entry> entries, Entry target) {
        List<Entry> result = new ArrayList<>();
        if (entries == null) return result;
        for (Entry entry : entries) if (!sameIdentity(entry, target)) result.add(entry);
        return result;
    }

    static int remainingDays(Entry entry, long now) {
        if (!isActive(entry, now)) return 0;
        long age = Math.max(0L, now - entry.createdAt);
        long remaining = RETENTION_MILLIS - age;
        return (int) Math.max(1L, (remaining + DAY_MILLIS - 1L) / DAY_MILLIS);
    }

    static List<Entry> parse(String value) {
        if (isBlank(value)) return new ArrayList<>();
        try {
            List<Entry> parsed = GSON.fromJson(value, LIST_TYPE);
            List<Entry> entries = new ArrayList<>();
            if (parsed != null) {
                for (Entry entry : parsed) if (entry != null) entries.add(entry);
            }
            return entries;
        } catch (RuntimeException ignored) {
            return new ArrayList<>();
        }
    }

    static String serialize(List<Entry> entries) {
        return GSON.toJson(entries == null ? new ArrayList<>() : entries, LIST_TYPE);
    }

    private static boolean isActive(Entry entry, long now) {
        if (entry == null || entry.createdAt <= 0) return false;
        if (now < entry.createdAt) return true;
        return now - entry.createdAt < RETENTION_MILLIS;
    }

    private static boolean sameIdentity(Entry first, Entry second) {
        if (first == null || second == null) return false;
        return normalize(first.normalizedTitle).equals(normalize(second.normalizedTitle))
                && normalize(first.mediaType).equals(normalize(second.mediaType))
                && first.year == second.year
                && first.tmdbId == second.tmdbId
                && first.createdAt == second.createdAt;
    }

    private static int extractYear(String value) {
        Matcher matcher = YEAR.matcher(Objects.toString(value, ""));
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static String normalize(String value) {
        return PersonalRecommendationService.normalizeTitle(Objects.toString(value, "")).toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Entry {

        private String title;
        private String normalizedTitle;
        private String mediaType;
        private int year;
        private int tmdbId;
        private String source;
        private long createdAt;

        static Entry from(TmdbItem item, String source, long createdAt) {
            Entry entry = new Entry();
            entry.title = item.getTitle();
            entry.normalizedTitle = normalize(item.getTitle());
            entry.mediaType = item.getMediaType();
            entry.year = extractYear(item.getSubtitle());
            entry.tmdbId = item.getTmdbId();
            entry.source = Objects.toString(source, "").trim();
            entry.createdAt = createdAt;
            return entry;
        }

        boolean matches(Entry other) {
            if (other == null) return false;
            if (!normalize(normalizedTitle).equals(normalize(other.normalizedTitle))) return false;
            String firstType = normalize(mediaType);
            String secondType = normalize(other.mediaType);
            return firstType.isEmpty() || secondType.isEmpty() || firstType.equals(secondType);
        }

        public String getTitle() {
            return Objects.toString(title, "");
        }

        public String getMediaType() {
            return Objects.toString(mediaType, "");
        }

        public int getYear() {
            return year;
        }

        public int getTmdbId() {
            return tmdbId;
        }

        public String getSource() {
            return Objects.toString(source, "");
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }
}
