package com.fongmi.android.tv.utils;

import android.net.Uri;

import com.fongmi.android.tv.bean.M3u8Evidence;
import com.github.catvod.net.OkHttp;

import java.io.BufferedReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.Headers;
import okhttp3.Request;
import okhttp3.Response;

/**
 * M3U8 playlist parser for ad detection evidence extraction.
 * Parses HLS playlists to detect discontinuities and domain switches.
 */
public class M3u8Parser {

    /**
     * Parse m3u8 URL and extract ad detection evidence.
     *
     * @param url     m3u8 URL
     * @param headers HTTP headers
     * @return Evidence object with segments, discontinuities, durations, and domain switches
     */
    public static M3u8Evidence parse(String url, Map<String, String> headers) {
        try {
            String content = download(url, headers);
            if (content == null || content.isEmpty()) return M3u8Evidence.empty();

            // If master playlist, resolve to first variant
            if (content.contains("#EXT-X-STREAM-INF")) {
                String variantUrl = extractFirstVariant(content, url);
                if (variantUrl != null) {
                    content = download(variantUrl, headers);
                    if (content == null || content.isEmpty()) return M3u8Evidence.empty();
                    url = variantUrl; // Update base URL for relative resolution
                }
            }

            return parseMediaPlaylist(content, url);
        } catch (Exception e) {
            return M3u8Evidence.empty();
        }
    }

    private static String download(String url, Map<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder().url(url).get();
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    builder.header(entry.getKey(), entry.getValue());
                }
            }
            try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;
                return response.body().string();
            }
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractFirstVariant(String masterContent, String baseUrl) {
        try (BufferedReader reader = new BufferedReader(new StringReader(masterContent))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#EXT-X-STREAM-INF")) {
                    String nextLine = reader.readLine();
                    if (nextLine != null && !nextLine.startsWith("#")) {
                        return resolveUrl(nextLine, baseUrl);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private static M3u8Evidence parseMediaPlaylist(String content, String baseUrl) {
        List<String> segments = new ArrayList<>();
        List<Integer> discontinuities = new ArrayList<>();
        List<Float> durations = new ArrayList<>();
        List<Boolean> domainSwitches = new ArrayList<>();

        String baseDomain = extractDomain(baseUrl);
        int segmentIndex = 0;
        float currentDuration = 0f;

        try (BufferedReader reader = new BufferedReader(new StringReader(content))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("#EXTINF:")) {
                    // Extract duration
                    try {
                        String durationStr = line.substring(8, line.indexOf(','));
                        currentDuration = Float.parseFloat(durationStr);
                    } catch (Exception e) {
                        currentDuration = 0f;
                    }
                } else if (line.startsWith("#EXT-X-DISCONTINUITY")) {
                    discontinuities.add(segmentIndex);
                } else if (!line.isEmpty() && !line.startsWith("#")) {
                    // Segment URL
                    String segmentUrl = resolveUrl(line, baseUrl);
                    segments.add(segmentUrl);
                    durations.add(currentDuration);

                    // Detect domain switch
                    String segmentDomain = extractDomain(segmentUrl);
                    domainSwitches.add(!segmentDomain.equals(baseDomain));

                    segmentIndex++;
                    currentDuration = 0f;
                }
            }
        } catch (Exception e) {
            // Ignore parsing errors
        }

        return M3u8Evidence.create(segments, discontinuities, durations, domainSwitches);
    }

    private static String resolveUrl(String url, String baseUrl) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return url;
            }
            // Resolve relative URL
            URL base = new URL(baseUrl);
            return new URL(base, url).toString();
        } catch (Exception e) {
            return url;
        }
    }

    private static String extractDomain(String url) {
        try {
            Uri uri = Uri.parse(url);
            return uri.getHost() != null ? uri.getHost() : "";
        } catch (Exception e) {
            return "";
        }
    }
}
