package com.fongmi.android.tv.bean;

import java.util.Collections;
import java.util.List;

/**
 * M3U8 playlist evidence for ad detection.
 * Contains segment URLs, discontinuities, durations, and domain switches.
 */
public class M3u8Evidence {

    private final List<String> segments;
    private final List<Integer> discontinuities;
    private final List<Float> durations;
    private final List<Boolean> domainSwitches;

    private M3u8Evidence(List<String> segments, List<Integer> discontinuities, List<Float> durations, List<Boolean> domainSwitches) {
        this.segments = segments;
        this.discontinuities = discontinuities;
        this.durations = durations;
        this.domainSwitches = domainSwitches;
    }

    public static M3u8Evidence create(List<String> segments, List<Integer> discontinuities, List<Float> durations, List<Boolean> domainSwitches) {
        return new M3u8Evidence(segments, discontinuities, durations, domainSwitches);
    }

    public static M3u8Evidence empty() {
        return new M3u8Evidence(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public List<String> getSegments() {
        return segments;
    }

    public List<Integer> getDiscontinuities() {
        return discontinuities;
    }

    public List<Float> getDurations() {
        return durations;
    }

    public List<Boolean> getDomainSwitches() {
        return domainSwitches;
    }

    public boolean isEmpty() {
        return segments.isEmpty();
    }

    public int getTotalSegments() {
        return segments.size();
    }

    /**
     * Get summary for AI analysis prompt.
     */
    public String toSummary() {
        if (isEmpty()) return "No segments found";

        StringBuilder sb = new StringBuilder();
        sb.append("Total segments: ").append(segments.size()).append("\n");
        sb.append("Discontinuities at: ").append(discontinuities.isEmpty() ? "None" : discontinuities.toString()).append("\n");

        // Duration analysis
        float totalDuration = 0f;
        for (Float d : durations) totalDuration += d;
        sb.append("Total duration: ").append(String.format("%.1f", totalDuration)).append("s\n");

        // Domain switch analysis
        int switchCount = 0;
        for (Boolean s : domainSwitches) if (s) switchCount++;
        sb.append("Domain switches: ").append(switchCount).append("\n");

        // Show first 3 and last 3 segment URLs (truncated)
        sb.append("\nFirst 3 segments:\n");
        for (int i = 0; i < Math.min(3, segments.size()); i++) {
            sb.append("  [").append(i).append("] ");
            sb.append(truncateUrl(segments.get(i)));
            if (domainSwitches.get(i)) sb.append(" [DOMAIN_SWITCH]");
            if (discontinuities.contains(i)) sb.append(" [DISCONTINUITY]");
            sb.append(" (").append(String.format("%.1f", durations.get(i))).append("s)\n");
        }

        if (segments.size() > 6) {
            sb.append("  ... (").append(segments.size() - 6).append(" segments)\n");
        }

        if (segments.size() > 3) {
            sb.append("Last 3 segments:\n");
            for (int i = Math.max(3, segments.size() - 3); i < segments.size(); i++) {
                sb.append("  [").append(i).append("] ");
                sb.append(truncateUrl(segments.get(i)));
                if (domainSwitches.get(i)) sb.append(" [DOMAIN_SWITCH]");
                if (discontinuities.contains(i)) sb.append(" [DISCONTINUITY]");
                sb.append(" (").append(String.format("%.1f", durations.get(i))).append("s)\n");
            }
        }

        return sb.toString();
    }

    private String truncateUrl(String url) {
        if (url.length() <= 80) return url;
        return url.substring(0, 40) + "..." + url.substring(url.length() - 37);
    }
}
