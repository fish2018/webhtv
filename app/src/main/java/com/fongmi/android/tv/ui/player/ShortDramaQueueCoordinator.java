package com.fongmi.android.tv.ui.player;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Pure, bounded state machine for the short-drama Exo playlist bridge.
 *
 * <p>The coordinator owns identity and event ordering only.  It does not know about Android,
 * Media3, Activity state, or a player implementation.  A session contains one current episode;
 * at most one resolved next episode may be retained.  A caller must carry the generation from
 * {@link #begin(Session)} through parsing, queue insertion, and transition handling.</p>
 */
public final class ShortDramaQueueCoordinator {

    private static final long FIRST_GENERATION = 1L;

    private long generation;
    private Session session;
    private int currentIndex = -1;
    private QueueItem queued;

    /** Starts a new session and invalidates all work belonging to the previous one. */
    public synchronized Snapshot begin(Session nextSession) {
        if (nextSession == null) throw new IllegalArgumentException("session == null");
        generation = nextGeneration(generation);
        session = nextSession;
        currentIndex = nextSession.currentIndex();
        queued = null;
        return snapshotLocked();
    }

    /** Invalidates parsing and queued media for the current session without touching the player. */
    public synchronized long invalidate() {
        generation = nextGeneration(generation);
        session = null;
        currentIndex = -1;
        queued = null;
        return generation;
    }

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    /** Returns the only episode that may be parsed/queued next, or {@code null} at the end. */
    public synchronized ResolveRequest nextRequest() {
        return nextRequestLocked();
    }

    /**
     * Accepts a resolved next item only when it still belongs to the current generation and
     * exactly matches the expected adjacent episode.  Duplicate or ineligible results are
     * rejected and cannot replace an already queued item.
     */
    public synchronized QueueItem acceptResolved(
            long requestGeneration, Episode episode, ResolvedItem resolved) {
        ResolveRequest expected = nextRequestLocked();
        if (expected == null || queued != null) return null;
        if (requestGeneration != generation || !sameEpisode(expected.episode(), episode)) return null;
        if (resolved == null || !resolved.isEligible()) return null;
        queued = new QueueItem(generation, expected.session(), expected.episode(), resolved);
        return queued;
    }

    /**
     * Commits one natural AUTO transition.  Manual/seek/repeat transitions and stale timeline
     * callbacks are intentionally ignored.  The successful call consumes the queue item, so a
     * repeated callback cannot submit the same business transition twice.
     */
    public synchronized Transition onMediaItemTransition(
            long eventGeneration, String mediaId, TransitionReason reason) {
        if (reason != TransitionReason.AUTO || queued == null) return null;
        if (eventGeneration != generation || !Objects.equals(mediaId, queued.mediaId())) return null;
        Episode previous = currentEpisodeLocked();
        QueueItem consumed = queued;
        currentIndex = consumed.episode().index();
        queued = null;
        return new Transition(generation, previous, consumed.episode(), nextRequestLocked());
    }

    /** A queued item is present; legacy near-end auto-advance must stay suppressed. */
    public synchronized boolean shouldSuppressLegacyAutoAdvance() {
        return session != null && queued != null;
    }

    private Snapshot snapshotLocked() {
        return new Snapshot(generation, session, currentEpisodeLocked(), queued, nextRequestLocked());
    }

    private ResolveRequest nextRequestLocked() {
        if (session == null) return null;
        int nextIndex = session.nextIndex(currentIndex);
        if (nextIndex < 0 || nextIndex >= session.episodes().size()) return null;
        return new ResolveRequest(generation, session, session.episodes().get(nextIndex));
    }

    private Episode currentEpisodeLocked() {
        if (session == null || currentIndex < 0 || currentIndex >= session.episodes().size()) return null;
        return session.episodes().get(currentIndex);
    }

    private static boolean sameEpisode(Episode left, Episode right) {
        return left != null && right != null
                && left.index() == right.index()
                && Objects.equals(left.identity(), right.identity());
    }

    private static long nextGeneration(long current) {
        return current == Long.MAX_VALUE ? FIRST_GENERATION : current + 1L;
    }

    public enum TransitionReason {
        AUTO,
        MANUAL,
        SEEK,
        REPEAT,
        UNKNOWN
    }

    public enum MediaKind {
        HLS,
        PROGRESSIVE_MP4
    }

    public static final class Session {
        private final String workId;
        private final String siteKey;
        private final String lineId;
        private final String playFlag;
        private final boolean reverse;
        private final List<Episode> episodes;
        private final int currentIndex;

        public Session(
                String workId,
                String siteKey,
                String lineId,
                String playFlag,
                List<Episode> episodes,
                int currentIndex,
                boolean reverse) {
            if (episodes == null || episodes.isEmpty()) throw new IllegalArgumentException("episodes empty");
            if (currentIndex < 0 || currentIndex >= episodes.size()) {
                throw new IllegalArgumentException("currentIndex out of range");
            }
            List<Episode> copy = new ArrayList<>(episodes.size());
            for (int i = 0; i < episodes.size(); i++) {
                Episode episode = episodes.get(i);
                if (episode == null || episode.index() != i) {
                    throw new IllegalArgumentException("episodes must have contiguous indexes");
                }
                copy.add(episode);
            }
            this.workId = value(workId);
            this.siteKey = value(siteKey);
            this.lineId = value(lineId);
            this.playFlag = value(playFlag);
            this.reverse = reverse;
            this.episodes = Collections.unmodifiableList(copy);
            this.currentIndex = currentIndex;
        }

        public String workId() {
            return workId;
        }

        public String siteKey() {
            return siteKey;
        }

        public String lineId() {
            return lineId;
        }

        public String playFlag() {
            return playFlag;
        }

        public boolean reverse() {
            return reverse;
        }

        public List<Episode> episodes() {
            return episodes;
        }

        public int currentIndex() {
            return currentIndex;
        }

        private int nextIndex(int current) {
            return current + (reverse ? -1 : 1);
        }
    }

    public static final class Episode {
        private final String id;
        private final String title;
        private final int index;

        public Episode(String id, String title, int index) {
            if (index < 0) throw new IllegalArgumentException("index < 0");
            this.id = value(id);
            this.title = value(title);
            this.index = index;
        }

        public String id() {
            return id;
        }

        public String title() {
            return title;
        }

        public int index() {
            return index;
        }

        public String identity() {
            if (!id.isEmpty()) return id;
            return index + ":" + title;
        }
    }

    public static final class ResolveRequest {
        private final long generation;
        private final Session session;
        private final Episode episode;

        private ResolveRequest(long generation, Session session, Episode episode) {
            this.generation = generation;
            this.session = session;
            this.episode = episode;
        }

        public long generation() {
            return generation;
        }

        public Session session() {
            return session;
        }

        public Episode episode() {
            return episode;
        }
    }

    public static final class ResolvedItem {
        private final String uri;
        private final Map<String, String> headers;
        private final String format;
        private final MediaKind kind;
        private final boolean drm;
        private final boolean requiresSecondParse;
        private final boolean live;
        private final boolean specialEncryption;
        private final boolean concatenated;
        private final boolean engineSwitch;

        public ResolvedItem(String uri, Map<String, String> headers, String format, MediaKind kind) {
            this(uri, headers, format, kind, false, false, false, false, false, false);
        }

        public ResolvedItem(
                String uri,
                Map<String, String> headers,
                String format,
                MediaKind kind,
                boolean drm,
                boolean requiresSecondParse,
                boolean live,
                boolean specialEncryption,
                boolean concatenated,
                boolean engineSwitch) {
            this.uri = value(uri);
            this.headers = immutableHeaders(headers);
            this.format = value(format);
            this.kind = kind;
            this.drm = drm;
            this.requiresSecondParse = requiresSecondParse;
            this.live = live;
            this.specialEncryption = specialEncryption;
            this.concatenated = concatenated;
            this.engineSwitch = engineSwitch;
        }

        public String uri() {
            return uri;
        }

        public Map<String, String> headers() {
            return headers;
        }

        public String format() {
            return format;
        }

        public MediaKind kind() {
            return kind;
        }

        public boolean isEligible() {
            if (uri.isEmpty() || kind == null || drm || requiresSecondParse || live
                    || specialEncryption || concatenated || engineSwitch) return false;
            if (uri.contains("***") || uri.contains("|||")) return false;
            try {
                URI parsed = new URI(uri);
                String scheme = parsed.getScheme();
                return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            } catch (URISyntaxException e) {
                return false;
            }
        }
    }

    public static final class QueueItem {
        private final long generation;
        private final Session session;
        private final Episode episode;
        private final ResolvedItem resolved;
        private final String mediaId;

        private QueueItem(long generation, Session session, Episode episode, ResolvedItem resolved) {
            this.generation = generation;
            this.session = session;
            this.episode = episode;
            this.resolved = resolved;
            this.mediaId = generation + ":" + episode.identity();
        }

        public long generation() {
            return generation;
        }

        public Session session() {
            return session;
        }

        public Episode episode() {
            return episode;
        }

        public ResolvedItem resolved() {
            return resolved;
        }

        public String mediaId() {
            return mediaId;
        }
    }

    public static final class Transition {
        private final long generation;
        private final Episode previous;
        private final Episode current;
        private final ResolveRequest nextRequest;

        private Transition(long generation, Episode previous, Episode current, ResolveRequest nextRequest) {
            this.generation = generation;
            this.previous = previous;
            this.current = current;
            this.nextRequest = nextRequest;
        }

        public long generation() {
            return generation;
        }

        public Episode previous() {
            return previous;
        }

        public Episode current() {
            return current;
        }

        public ResolveRequest nextRequest() {
            return nextRequest;
        }
    }

    public static final class Snapshot {
        private final long generation;
        private final Session session;
        private final Episode current;
        private final QueueItem queued;
        private final ResolveRequest nextRequest;

        private Snapshot(long generation, Session session, Episode current, QueueItem queued, ResolveRequest nextRequest) {
            this.generation = generation;
            this.session = session;
            this.current = current;
            this.queued = queued;
            this.nextRequest = nextRequest;
        }

        public long generation() {
            return generation;
        }

        public Session session() {
            return session;
        }

        public Episode current() {
            return current;
        }

        public QueueItem queued() {
            return queued;
        }

        public ResolveRequest nextRequest() {
            return nextRequest;
        }
    }

    private static Map<String, String> immutableHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return Collections.emptyMap();
        Map<String, String> copy = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) copy.put(entry.getKey(), entry.getValue());
        }
        return copy.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(copy);
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }
}
