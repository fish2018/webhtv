package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackDiagnosticCollector.Context;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Per-collector diagnostic snapshots only; never caches or changes decoder selection results. */
final class ExoDiagnosticCodecSnapshotCache {
    static final int MAX_QUERIES = 32;
    private static final int MAX_CANDIDATES = 128;

    record Query(String mime, boolean secure, boolean tunneling, String policy) {}
    record Profile(int profile, int level) {}
    record Candidate(String name, boolean hardwareAccelerated, boolean softwareOnly,
                     boolean vendor, int profileCount, List<Profile> profiles, boolean readable) {
        Candidate { profiles = List.copyOf(profiles); }
    }
    record Observation(long snapshotId, long queryCount, boolean fullSnapshot) {}

    private static final class Entry {
        final long id;
        final List<Candidate> candidates;
        long queries = 1;

        Entry(long id, List<Candidate> candidates) {
            this.id = id;
            this.candidates = List.copyOf(candidates);
        }
    }

    private final LinkedHashMap<Query, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private Context owner;
    private long captureGeneration = -1;
    private long nextId;

    synchronized Observation observe(Context context, long generation, Query query,
                                     List<Candidate> candidates) {
        if (!Objects.equals(owner, context) || captureGeneration != generation) {
            entries.clear();
            owner = context;
            captureGeneration = generation;
        }
        boolean cacheable = candidates.size() <= MAX_CANDIDATES;
        for (Candidate candidate : candidates) cacheable &= candidate.readable();
        Entry previous = entries.get(query);
        if (cacheable && previous != null && previous.candidates.equals(candidates)) {
            if (previous.queries < Long.MAX_VALUE) previous.queries++;
            return new Observation(previous.id, previous.queries, false);
        }
        long id = ++nextId;
        if (cacheable) {
            entries.put(query, new Entry(id, candidates));
            if (entries.size() > MAX_QUERIES) entries.remove(entries.keySet().iterator().next());
        } else {
            entries.remove(query); // A failed capabilities read must never look like a cache hit.
        }
        return new Observation(id, 1, true);
    }

    synchronized void clear() {
        entries.clear();
        owner = null;
        captureGeneration = -1;
    }
}
