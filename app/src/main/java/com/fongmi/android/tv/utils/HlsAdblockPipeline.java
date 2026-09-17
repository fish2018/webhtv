package com.fongmi.android.tv.utils;

import androidx.media3.exoplayer.hls.playlist.HlsAdsParser;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HlsAdblockPipeline {

    private HlsAdblockPipeline() {}

    public static Outcome apply(String url, String manifest, List<HlsManifestCleaner.Rule> rules, boolean legacyFallback) {
        if (!legacyFallback) {
            HlsManifestCleaner.Result clean = HlsManifestCleaner.clean(url, manifest, rules);
            return new Outcome(clean.manifest(), clean.changed(), false,
                    clean.removedSegments(), clean.removedDurationSec(), clean.ruleCounts(),
                    clean.removedSegmentDetails());
        }
        HlsManifestCleaner.Result clean = HlsManifestCleaner.clean(url, manifest, rules);
        if (clean.changed()) {
            return new Outcome(clean.manifest(), true, false, clean.removedSegments(), clean.removedDurationSec(),
                    clean.ruleCounts(), clean.removedSegmentDetails());
        }
        if (!legacyFallback || clean.fallback() || manifest == null || !manifest.contains("#EXT-X-ENDLIST")) {
            return new Outcome(manifest, false, false, 0, 0);
        }
        try {
            String filtered = HlsAdsParser.process(manifest);
            if (filtered.equals(manifest)) return new Outcome(manifest, false, false, 0, 0);
            List<HlsManifestCleaner.RemovedSegment> removed = legacyRemovedSegments(url, manifest, filtered);
            double duration = removed.stream().mapToDouble(HlsManifestCleaner.RemovedSegment::durationSec).sum();
            return new Outcome(filtered, false, true, removed.size(), duration,
                    Map.of(), List.copyOf(removed));
        } catch (Throwable ignored) {
            return new Outcome(manifest, false, false, 0, 0);
        }
    }

    static List<HlsManifestCleaner.RemovedSegment> legacyRemovedSegments(
            String baseUrl, String original, String filtered) {
        List<SegmentInfo> source = parseSegments(baseUrl, original);
        Map<String, Integer> kept = new HashMap<>();
        for (SegmentInfo segment : parseSegments(baseUrl, filtered)) kept.merge(segment.uri(), 1, Integer::sum);
        List<HlsManifestCleaner.RemovedSegment> removed = new ArrayList<>();
        for (SegmentInfo segment : source) {
            int count = kept.getOrDefault(segment.uri(), 0);
            if (count > 0) {
                kept.put(segment.uri(), count - 1);
                continue;
            }
            removed.add(new HlsManifestCleaner.RemovedSegment(
                    segment.host(), "hls.legacy-fallback", segment.startSeconds(), segment.durationSeconds()));
        }
        return removed;
    }

    private static List<SegmentInfo> parseSegments(String baseUrl, String manifest) {
        List<SegmentInfo> result = new ArrayList<>();
        if (manifest == null) return result;
        double start = 0;
        double duration = -1;
        for (String raw : manifest.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF:")) {
                try {
                    String value = line.substring(8);
                    int comma = value.indexOf(',');
                    duration = Double.parseDouble(comma < 0 ? value : value.substring(0, comma));
                } catch (RuntimeException e) {
                    duration = -1;
                }
            } else if (!line.isEmpty() && !line.startsWith("#") && duration >= 0) {
                try {
                    URI resolved = URI.create(baseUrl).resolve(line);
                    result.add(new SegmentInfo(line, resolved.getHost() == null ? "" : resolved.getHost(), start, duration));
                } catch (RuntimeException e) {
                    result.add(new SegmentInfo(line, "", start, duration));
                }
                start += duration;
                duration = -1;
            }
        }
        return result;
    }

    private record SegmentInfo(String uri, String host, double startSeconds, double durationSeconds) {}

    public static boolean isCoreM3u8Proxy(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            boolean loopback = "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
            return loopback && "/m3u8".equals(uri.getPath());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public record Outcome(String manifest, boolean structured, boolean legacy, int removedSegments,
                          double removedDurationSec, Map<String, Long> ruleCounts,
                          List<HlsManifestCleaner.RemovedSegment> removedSegmentDetails) {
        public Outcome(String manifest, boolean structured, boolean legacy, int removedSegments, double removedDurationSec) {
            this(manifest, structured, legacy, removedSegments, removedDurationSec, Map.of(), List.of());
        }
    }
}
