package com.fongmi.android.tv.service;

import androidx.annotation.Nullable;

import com.fongmi.android.tv.bean.TmdbItem;
import com.github.catvod.utils.Path;
import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

final class PersonalRecommendationCache {

    private static final int VERSION = 1;
    private static final int MAX_CACHED_ITEMS = 128;
    private static final int MAX_CACHE_FILES = 32;
    private static final long TTL = TimeUnit.DAYS.toMillis(7);
    private static final Gson GSON = new Gson();

    private final File directory;

    PersonalRecommendationCache(File directory) {
        this.directory = directory;
    }

    synchronized PersonalRecommendationService.RecommendationPage read(
            String source, String key, String fingerprint) {
        File file = cacheFile(source, key);
        if (file == null) return null;
        Payload payload = readPayload(file);
        if (payload == null || !isValid(payload, fingerprint)) return null;
        return new PersonalRecommendationService.RecommendationPage(
                payload.items, payload.offset, payload.nextOffset, payload.hasMore, payload.fingerprint);
    }

    synchronized void write(
            String source, String key, String fingerprint,
            PersonalRecommendationService.RecommendationPage page) {
        if (page == null || page.getItems().isEmpty()) return;
        List<TmdbItem> items = page.getItems();
        if (items.size() > MAX_CACHED_ITEMS) items = new ArrayList<>(items.subList(0, MAX_CACHED_ITEMS));
        writePayload(cacheFile(source, key), new Payload(
                VERSION, items, page.getOffset(), page.getNextOffset(), page.hasMore(), fingerprint));
        cleanup();
    }

    private Payload readPayload(File file) {
        try {
            if (file == null || !file.exists() || file.length() <= 0) return null;
            if (System.currentTimeMillis() - file.lastModified() > TTL) return null;
            String body = Path.read(file);
            if (isBlank(body)) return null;
            return GSON.fromJson(body, Payload.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void writePayload(@Nullable File file, Payload payload) {
        try {
            if (file == null) return;
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return;
            Path.write(file, GSON.toJson(payload).getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
        }
    }

    private boolean isValid(Payload payload, String fingerprint) {
        return payload.version == VERSION
                && payload.items != null
                && !payload.items.isEmpty()
                && payload.items.size() <= MAX_CACHED_ITEMS
                && payload.offset >= 0
                && payload.nextOffset >= payload.offset
                && Objects.equals(payload.fingerprint, fingerprint);
    }

    private void cleanup() {
        try {
            if (directory == null || !directory.isDirectory()) return;
            File[] files = directory.listFiles(file -> file != null && file.isFile() && file.getName().endsWith(".json"));
            if (files == null || files.length <= MAX_CACHE_FILES) return;
            Arrays.sort(files, Comparator.comparingLong(File::lastModified).reversed());
            for (int index = MAX_CACHE_FILES; index < files.length; index++) {
                File file = files[index];
                if (file != null && !file.delete()) file.deleteOnExit();
            }
        } catch (RuntimeException ignored) {
        }
    }

    @Nullable
    private File cacheFile(String source, String key) {
        try {
            if (directory == null || isBlank(source) || isBlank(key)) return null;
            return new File(directory, source + "_" + md5(key) + ".json");
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte valueByte : bytes) builder.append(String.format("%02x", valueByte));
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class Payload {

        @SerializedName("version")
        private final int version;
        @SerializedName("items")
        private final List<TmdbItem> items;
        @SerializedName("offset")
        private final int offset;
        @SerializedName("nextOffset")
        private final int nextOffset;
        @SerializedName("hasMore")
        private final boolean hasMore;
        @SerializedName("fingerprint")
        private final String fingerprint;

        private Payload(
                int version, List<TmdbItem> items, int offset, int nextOffset,
                boolean hasMore, String fingerprint) {
            this.version = version;
            this.items = items == null ? new ArrayList<>() : new ArrayList<>(items);
            this.offset = offset;
            this.nextOffset = nextOffset;
            this.hasMore = hasMore;
            this.fingerprint = fingerprint == null ? "" : fingerprint;
        }
    }
}
