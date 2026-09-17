package com.fongmi.android.tv.playback;

import com.fongmi.android.tv.bean.PlaybackDeleteTombstone;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.db.dao.PlaybackDeleteTombstoneDao;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/** Persistent delete markers used to make remote playback sync monotonic. */
public final class PlaybackDeleteTombstoneStore {

    private static final long RETENTION_MS = TimeUnit.DAYS.toMillis(90);

    private PlaybackDeleteTombstoneStore() {
    }

    public static synchronized List<PlaybackDeleteTombstone> snapshot() {
        PlaybackDeleteTombstoneDao dao = dao();
        dao.deleteBefore(System.currentTimeMillis() - RETENTION_MS);
        return new ArrayList<>(dao.findAll());
    }

    public static synchronized void record(PlaybackProgressDeleteInput input, int cid, RemoteSyncConfig filter) {
        if (input == null) return;
        List<PlaybackDeleteTombstone> items = new ArrayList<>();
        append(items, input, cid, filter);
        persist(items);
    }

    public static synchronized void recordAll(List<PlaybackProgressDeleteInput> inputs, int cid, RemoteSyncConfig filter) {
        if (inputs == null || inputs.isEmpty()) return;
        List<PlaybackDeleteTombstone> items = new ArrayList<>();
        for (PlaybackProgressDeleteInput input : inputs) append(items, input, cid, filter);
        persist(items);
    }

    public static long latest(List<PlaybackDeleteTombstone> tombstones, String configKey, int cid,
                              String historyKey, String siteKey, String vodId) {
        return latest(tombstones, configKey, cid, historyKey, siteKey, vodId, "", 0, -1);
    }

    public static long latest(List<PlaybackDeleteTombstone> tombstones, String configKey, int cid,
                              String historyKey, String siteKey, String vodId,
                              String mediaType, int tmdbId, int seasonNumber) {
        if (tombstones == null || tombstones.isEmpty()) return 0;
        String identity = configIdentity(configKey, cid);
        String normalizedHistory = safe(historyKey);
        String normalizedSite = RemoteSyncConfig.normalize(siteKey);
        String normalizedVod = safe(vodId);
        long latest = 0;
        for (PlaybackDeleteTombstone tombstone : tombstones) {
            if (tombstone == null || !Objects.equals(identity, safe(tombstone.configKey))) continue;
            if (!matches(tombstone, normalizedHistory, normalizedSite, normalizedVod,
                    mediaType, tmdbId, seasonNumber)) continue;
            latest = Math.max(latest, tombstone.deletedAt);
        }
        return latest;
    }

    public static String configIdentity(String configKey, int cid) {
        String value = PlaybackConfigIdentity.normalizeKey(configKey);
        if (!empty(value)) return value;
        value = PlaybackConfigIdentity.keyForCid(cid);
        return empty(value) ? "cid:" + Math.max(0, cid) : value;
    }

    private static void append(List<PlaybackDeleteTombstone> items, PlaybackProgressDeleteInput input,
                               int cid, RemoteSyncConfig filter) {
        input.normalize();
        if (filter != null && (input.isAllScope() || input.isSeasonScope())
                && filter.siteKeys != null && !filter.siteKeys.isEmpty()) {
            for (String site : filter.siteKeys) {
                String normalized = RemoteSyncConfig.normalize(site);
                if (empty(normalized)) continue;
                PlaybackProgressDeleteInput scoped = input.copy();
                scoped.scope = input.isSeasonScope() ? "season" : "site";
                scoped.siteKey = normalized;
                scoped.vodId = "";
                scoped.historyKey = "";
                items.add(create(scoped, cid));
            }
            return;
        }
        items.add(create(input, cid));
    }

    static PlaybackDeleteTombstone create(PlaybackProgressDeleteInput input, int cid) {
        PlaybackDeleteTombstone tombstone = new PlaybackDeleteTombstone();
        tombstone.configKey = configIdentity(input.configKey, cid);
        tombstone.scope = input.isAllScope() ? "all" : input.isSiteScope() ? "site"
                : input.isSeasonScope() ? "season" : "item";
        tombstone.historyKey = safe(input.historyKey);
        tombstone.siteKey = RemoteSyncConfig.normalize(input.siteKey);
        tombstone.vodId = safe(input.vodId);
        tombstone.mediaType = input.isSeasonScope() ? safe(input.mediaType).toLowerCase(Locale.ROOT) : "";
        tombstone.tmdbId = input.isSeasonScope() ? input.tmdbId : 0;
        tombstone.seasonNumber = input.isSeasonScope() ? input.seasonNumber : -1;
        if ("all".equals(tombstone.scope)) {
            tombstone.historyKey = "";
            tombstone.siteKey = "";
            tombstone.vodId = "";
        } else if ("site".equals(tombstone.scope)) {
            tombstone.historyKey = "";
            tombstone.vodId = "";
        } else if ("season".equals(tombstone.scope)) {
            tombstone.historyKey = "";
            tombstone.vodId = "";
        } else if (!empty(tombstone.siteKey) && !empty(tombstone.vodId)) {
            // siteKey + vodId is portable across devices; historyKey may contain a local cid suffix.
            tombstone.historyKey = "";
        }
        tombstone.deletedAt = input.deletedAt > 0 ? input.deletedAt : System.currentTimeMillis();
        tombstone.id = digest(join(tombstone.configKey, tombstone.scope, tombstone.historyKey,
                tombstone.siteKey, tombstone.vodId, tombstone.mediaType,
                String.valueOf(tombstone.tmdbId), String.valueOf(tombstone.seasonNumber)));
        return tombstone;
    }

    private static boolean matches(PlaybackDeleteTombstone tombstone, String historyKey, String siteKey, String vodId,
                                   String mediaType, int tmdbId, int seasonNumber) {
        if ("all".equals(tombstone.scope)) return true;
        if ("site".equals(tombstone.scope)) return Objects.equals(tombstone.siteKey, siteKey);
        if ("season".equals(tombstone.scope)) {
            return Objects.equals(safe(tombstone.mediaType), safe(mediaType).toLowerCase(Locale.ROOT))
                    && tombstone.tmdbId == tmdbId && tombstone.seasonNumber == seasonNumber
                    && (empty(tombstone.siteKey) || Objects.equals(tombstone.siteKey, siteKey));
        }
        if (!empty(tombstone.siteKey) && !empty(tombstone.vodId)) {
            return Objects.equals(tombstone.siteKey, siteKey) && Objects.equals(tombstone.vodId, vodId);
        }
        return !empty(tombstone.historyKey) && Objects.equals(tombstone.historyKey, historyKey);
    }

    private static void persist(List<PlaybackDeleteTombstone> items) {
        if (items == null || items.isEmpty()) return;
        PlaybackDeleteTombstoneDao dao = dao();
        for (PlaybackDeleteTombstone item : items) {
            PlaybackDeleteTombstone existing = dao.find(item.id);
            if (newest(existing, item) == item) dao.insertOrUpdate(item);
        }
        dao.deleteBefore(System.currentTimeMillis() - RETENTION_MS);
    }

    static PlaybackDeleteTombstone newest(PlaybackDeleteTombstone existing, PlaybackDeleteTombstone incoming) {
        if (existing == null) return incoming;
        if (incoming == null || incoming.deletedAt <= existing.deletedAt) return existing;
        return incoming;
    }

    private static PlaybackDeleteTombstoneDao dao() {
        return AppDatabase.get().getPlaybackDeleteTombstoneDao();
    }

    private static String join(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) builder.append(safe(value)).append('\n');
        return builder.toString();
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) builder.append(String.format(Locale.ROOT, "%02x", item));
            return builder.toString();
        } catch (Exception e) {
            return value;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static boolean empty(String value) {
        return value == null || value.isEmpty();
    }
}
