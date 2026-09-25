package com.fongmi.android.tv.player;

import java.net.URI;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative, side-effect-free classification of the resource and the two
 * observable legs of a playback request.  The classifier never resolves DNS
 * and never keeps a URL in its result.  A missing or conflicting fact remains
 * UNKNOWN instead of being guessed from a proxy URL.
 */
public final class PlaybackResourceClassifier {

    private static final Pattern HLS_TARGET_DURATION = Pattern.compile("#EXT-X-TARGETDURATION\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HLS_PLAYLIST_TYPE = Pattern.compile("^#EXT-X-PLAYLIST-TYPE\\s*:\\s*([^\\r\\n]+)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern HLS_PART = Pattern.compile("#EXT-X-PART(?:\\s*:|\\b)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HLS_PART_TARGET = Pattern.compile("^#EXT-X-PART-INF\\s*:[^\\r\\n]*\\bPART-TARGET\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern HLS_PART_DURATION = Pattern.compile("^#EXT-X-PART\\s*:[^\\r\\n]*\\bDURATION\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern HLS_PART_HOLD_BACK = Pattern.compile("^#EXT-X-SERVER-CONTROL\\s*:[^\\r\\n]*\\bPART-HOLD-BACK\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern HLS_HOLD_BACK = Pattern.compile("^#EXT-X-SERVER-CONTROL\\s*:[^\\r\\n]*(?<!PART-)\\bHOLD-BACK\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
    private static final Pattern DASH_MPD = Pattern.compile("<MPD\\b([^>]*)>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern DASH_ATTRIBUTE = Pattern.compile("\\b([A-Za-z][A-Za-z0-9:-]*)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')", Pattern.CASE_INSENSITIVE);

    private PlaybackResourceClassifier() {
    }

    public static boolean isHlsUrl(String url) {
        String lower = lower(url);
        if (lower.contains("m3u8") || lower.contains("type=hls") || lower.contains("format=hls")) return true;
        String path = urlPath(lower);
        return path.endsWith("/live.php") || path.contains("/live/");
    }

    public static Classification classifyRequest(String playerUri, String mimeType, String format) {
        return classify(playerUri, null, mimeType, format, Map.of(), null);
    }

    public static Classification classifyRequest(String playerUri, String mimeType, String format, Map<String, List<String>> headers) {
        return classify(playerUri, null, mimeType, format, headers, null);
    }

    public static Classification classifyDataSource(String requestedUri, String resolvedUri, String mimeType,
                                                     Map<String, List<String>> headers) {
        return classify(requestedUri, resolvedUri, mimeType, null, headers, null)
                .withSource(ValueSource.DATA_SOURCE, null);
    }

    public static Classification classify(String playerUri,
                                          String upstreamUri,
                                          String mimeType,
                                          String format,
                                          Map<String, List<String>> headers,
                                          String manifest) {
        return classify(playerUri, upstreamUri, mimeType, format, headers, manifest, -1);
    }

    private static Classification classify(String playerUri,
                                           String upstreamUri,
                                           String mimeType,
                                           String format,
                                           Map<String, List<String>> headers,
                                           String manifest,
                                           long observedAtElapsedMs) {
        PathObservation playerPath = classifyPath(playerUri);
        PathObservation upstreamPath = classifyUpstreamPath(playerPath, upstreamUri);
        ProtocolGuess requestProtocol = protocolFromRequest(playerUri, mimeType, format);
        PlaybackAutoContext.RangeSupport range = rangeFromHeaders(headers);
        Classification base = new Classification(
                requestProtocol.protocol(),
                requestProtocol.streamKind(),
                range,
                transferFor(requestProtocol.protocol(), false),
                playerPath.kind(),
                upstreamPath.kind(),
                upstreamState(playerPath, upstreamUri, upstreamPath),
                PlaybackAutoContext.ManifestFacts.unknown(),
                List.copyOf(evidence(requestProtocol.evidence(), playerPath.evidence(), range == PlaybackAutoContext.RangeSupport.UNKNOWN ? Evidence.UNKNOWN : Evidence.HTTP_HEADERS)),
                requestProtocol.confidence(),
                ValueSource.PLAYBACK_REQUEST,
                false,
                -1);
        if (manifest == null || manifest.isBlank()) return base;
        Classification parsed = parseManifest(playerUri, upstreamUri, manifest, headers, observedAtElapsedMs);
        return merge(base, parsed);
    }

    public static Classification classifyHls(String playerUri, String upstreamUri, String playlist) {
        return classify(playerUri, upstreamUri, "application/vnd.apple.mpegurl", "hls", Map.of(), playlist);
    }

    public static Classification classifyHls(String playerUri, String upstreamUri, String playlist, long observedAtElapsedMs) {
        return classify(playerUri, upstreamUri, "application/vnd.apple.mpegurl", "hls", Map.of(), playlist, observedAtElapsedMs);
    }

    public static Classification classifyDash(String playerUri, String upstreamUri, String manifest) {
        return classify(playerUri, upstreamUri, "application/dash+xml", "dash", Map.of(), manifest);
    }

    public static Classification classifyDash(String playerUri, String upstreamUri, String manifest, long observedAtElapsedMs) {
        return classify(playerUri, upstreamUri, "application/dash+xml", "dash", Map.of(), manifest, observedAtElapsedMs);
    }

    /** Adds a player callback observation without replacing stronger manifest facts. */
    public static Classification observePlayer(Classification base, boolean live, long durationMs) {
        if (base == null) return classifyRequest(null, null, null);
        if (base.streamKind() != PlaybackAutoContext.StreamKind.UNKNOWN) return base;
        PlaybackAutoContext.StreamKind kind = live ? PlaybackAutoContext.StreamKind.LIVE
                : durationMs > 0 ? PlaybackAutoContext.StreamKind.VOD : PlaybackAutoContext.StreamKind.UNKNOWN;
        if (kind == PlaybackAutoContext.StreamKind.UNKNOWN) return base;
        return base.withStream(kind, PlaybackAutoContext.Confidence.MEDIUM, ValueSource.PLAYER_CALLBACK, Evidence.PLAYER_CALLBACK);
    }

    /** Merges a newer observation while retaining known facts from the older one. */
    public static Classification merge(Classification older, Classification newer) {
        if (older == null) return newer == null ? classifyRequest(null, null, null) : newer;
        if (newer == null) return older;
        boolean dataSourceSegmentDetail = newer.source() == ValueSource.DATA_SOURCE
                && isSegmentedProtocol(older.protocol())
                && newer.protocol() != older.protocol()
                && !newer.actualManifest();
        PlaybackAutoContext.Protocol protocol = !dataSourceSegmentDetail && newer.protocol() != PlaybackAutoContext.Protocol.UNKNOWN
                ? newer.protocol() : older.protocol();
        PlaybackAutoContext.StreamKind stream = newer.streamKind() != PlaybackAutoContext.StreamKind.UNKNOWN ? newer.streamKind() : older.streamKind();
        PlaybackAutoContext.RangeSupport range = newer.rangeSupport() != PlaybackAutoContext.RangeSupport.UNKNOWN ? newer.rangeSupport() : older.rangeSupport();
        PlaybackAutoContext.TransferUnit transfer = !dataSourceSegmentDetail && newer.transferUnit() != PlaybackAutoContext.TransferUnit.UNKNOWN
                ? newer.transferUnit() : older.transferUnit();
        PlaybackAutoContext.PathKind playerPath = newer.playerPath() != PlaybackAutoContext.PathKind.UNKNOWN ? newer.playerPath() : older.playerPath();
        PlaybackAutoContext.PathKind upstreamPath = newer.upstreamPath() != PlaybackAutoContext.PathKind.UNKNOWN ? newer.upstreamPath() : older.upstreamPath();
        PlaybackAutoContext.UpstreamState upstreamState = newer.upstreamState() != PlaybackAutoContext.UpstreamState.UNKNOWN ? newer.upstreamState() : older.upstreamState();
        boolean useNewManifest = newer.manifest().kind() != PlaybackAutoContext.ManifestKind.UNKNOWN
                && newer.manifest().kind() != PlaybackAutoContext.ManifestKind.NONE;
        PlaybackAutoContext.ManifestFacts manifest = useNewManifest ? newer.manifest() : older.manifest();
        LinkedHashSet<Evidence> evidence = new LinkedHashSet<>(older.evidence());
        evidence.addAll(newer.evidence());
        PlaybackAutoContext.Confidence confidence = dataSourceSegmentDetail
                ? older.confidence() : strongerConfidence(older.confidence(), newer.confidence());
        boolean actualManifest = older.actualManifest() || newer.actualManifest();
        ValueSource source = actualManifest ? ValueSource.MANIFEST
                : dataSourceSegmentDetail ? older.source()
                : (newer.protocol() != PlaybackAutoContext.Protocol.UNKNOWN
                || newer.streamKind() != PlaybackAutoContext.StreamKind.UNKNOWN
                || newer.rangeSupport() != PlaybackAutoContext.RangeSupport.UNKNOWN)
                ? newer.source() : older.source();
        long observedAtElapsedMs = useNewManifest ? newer.observedAtElapsedMs() : older.observedAtElapsedMs();
        return new Classification(protocol, stream, range, transfer, playerPath, upstreamPath, upstreamState, manifest,
                List.copyOf(evidence), confidence, source, actualManifest, observedAtElapsedMs);
    }

    public record Classification(
            PlaybackAutoContext.Protocol protocol,
            PlaybackAutoContext.StreamKind streamKind,
            PlaybackAutoContext.RangeSupport rangeSupport,
            PlaybackAutoContext.TransferUnit transferUnit,
            PlaybackAutoContext.PathKind playerPath,
            PlaybackAutoContext.PathKind upstreamPath,
            PlaybackAutoContext.UpstreamState upstreamState,
            PlaybackAutoContext.ManifestFacts manifest,
            List<Evidence> evidence,
            PlaybackAutoContext.Confidence confidence,
            ValueSource source,
            boolean actualManifest,
            long observedAtElapsedMs) {

        public Classification {
            protocol = protocol == null ? PlaybackAutoContext.Protocol.UNKNOWN : protocol;
            streamKind = streamKind == null ? PlaybackAutoContext.StreamKind.UNKNOWN : streamKind;
            rangeSupport = rangeSupport == null ? PlaybackAutoContext.RangeSupport.UNKNOWN : rangeSupport;
            transferUnit = transferUnit == null ? PlaybackAutoContext.TransferUnit.UNKNOWN : transferUnit;
            playerPath = playerPath == null ? PlaybackAutoContext.PathKind.UNKNOWN : playerPath;
            upstreamPath = upstreamPath == null ? PlaybackAutoContext.PathKind.UNKNOWN : upstreamPath;
            upstreamState = upstreamState == null ? PlaybackAutoContext.UpstreamState.UNKNOWN : upstreamState;
            manifest = manifest == null ? PlaybackAutoContext.ManifestFacts.unknown() : manifest;
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
            confidence = confidence == null ? PlaybackAutoContext.Confidence.UNKNOWN : confidence;
            source = source == null ? ValueSource.UNKNOWN : source;
            observedAtElapsedMs = Math.max(-1, observedAtElapsedMs);
        }

        public boolean known() {
            return protocol != PlaybackAutoContext.Protocol.UNKNOWN
                    || streamKind != PlaybackAutoContext.StreamKind.UNKNOWN
                    || playerPath != PlaybackAutoContext.PathKind.UNKNOWN;
        }

        private Classification withStream(PlaybackAutoContext.StreamKind stream,
                                          PlaybackAutoContext.Confidence streamConfidence,
                                          ValueSource streamSource,
                                          Evidence streamEvidence) {
            LinkedHashSet<Evidence> merged = new LinkedHashSet<>(evidence);
            merged.add(streamEvidence);
            return new Classification(protocol, stream, rangeSupport, transferUnit, playerPath, upstreamPath,
                    upstreamState, manifest, List.copyOf(merged), strongerConfidence(confidence, streamConfidence), streamSource,
                    actualManifest, observedAtElapsedMs);
        }

        private Classification withSource(ValueSource newSource, Evidence newEvidence) {
            LinkedHashSet<Evidence> merged = new LinkedHashSet<>(evidence);
            if (newEvidence != null && newEvidence != Evidence.UNKNOWN) merged.add(newEvidence);
            return new Classification(protocol, streamKind, rangeSupport, transferUnit, playerPath, upstreamPath,
                    upstreamState, manifest, List.copyOf(merged), confidence, newSource, actualManifest, observedAtElapsedMs);
        }

        public PlaybackAutoContext.ResourceFacts toResourceFacts(long sampledAtElapsedMs) {
            long sampled = Math.max(0, sampledAtElapsedMs);
            long resourceSampled = actualManifest && observedAtElapsedMs >= 0 ? observedAtElapsedMs : sampled;
            PlaybackAutoContext.ValueSource factSource = source.contextSource();
            PlaybackAutoContext.ValueSource streamSource = actualManifest ? PlaybackAutoContext.ValueSource.MANIFEST
                    : evidence.contains(Evidence.PLAYER_CALLBACK) ? PlaybackAutoContext.ValueSource.PLAYER_CALLBACK : factSource;
            PlaybackAutoContext.ValueSource rangeSource = evidence.contains(Evidence.HTTP_HEADERS)
                    ? PlaybackAutoContext.ValueSource.DATA_SOURCE : factSource;
            PlaybackAutoContext.Confidence streamConfidence = !actualManifest && evidence.contains(Evidence.PLAYER_CALLBACK)
                    ? PlaybackAutoContext.Confidence.MEDIUM : confidence;
            PlaybackAutoContext.Confidence rangeConfidence = rangeSupport == PlaybackAutoContext.RangeSupport.UNKNOWN
                    ? PlaybackAutoContext.Confidence.UNKNOWN : evidence.contains(Evidence.HTTP_HEADERS)
                    ? PlaybackAutoContext.Confidence.HIGH : confidence;
            boolean volatileManifest = actualManifest && (streamKind == PlaybackAutoContext.StreamKind.LIVE
                    || streamKind == PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE
                    || manifest.kind() == PlaybackAutoContext.ManifestKind.DASH_DYNAMIC);
            long manifestTtlMs = manifestTtlMs(manifest);
            return new PlaybackAutoContext.ResourceFacts(
                    fact(protocol, factSource, resourceSampled),
                    resourceFact(streamKind, streamSource, streamConfidence, resourceSampled, volatileManifest, manifestTtlMs),
                    fact(rangeSupport, rangeSource, rangeConfidence, resourceSampled),
                    resourceFact(transferUnit, factSource, resourceSampled, volatileManifest, manifestTtlMs),
                    resourceFact(manifest, factSource, resourceSampled, volatileManifest, manifestTtlMs));
        }

        public PlaybackAutoContext.PathFacts toPathFacts(PlaybackRoute.Resolution observedRoute, long sampledAtElapsedMs) {
            PlaybackRoute.Resolution route = observedRoute == null ? PlaybackRoute.resolve(null) : observedRoute;
            PlaybackRouteCapabilities capabilities = PlaybackRouteCapabilities.resolve(route);
            PlaybackRouteCapabilities.UpstreamVisibility visibility = upstreamState == PlaybackAutoContext.UpstreamState.OPAQUE
                    ? PlaybackRouteCapabilities.UpstreamVisibility.OPAQUE_EXTERNAL_PROCESS
                    : capabilities.upstreamVisibility();
            long sampled = Math.max(0, sampledAtElapsedMs);
            PlaybackAutoContext.ValueSource factSource = source.contextSource();
            PlaybackAutoContext.Confidence routeConfidence = routeConfidence(route.confidence());
            PlaybackAutoContext.Confidence playerConfidence = pathConfidence(playerPath);
            PlaybackAutoContext.Confidence upstreamConfidence = pathConfidence(upstreamPath);
            return new PlaybackAutoContext.PathFacts(
                    fact(route.route(), PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER, routeConfidence, sampled),
                    fact(route.owner(), PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER, routeConfidence, sampled),
                    fact(route.loopback(), PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER, routeConfidence, sampled),
                    fact(capabilities.observedLeg(), PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER, routeConfidence, sampled),
                    fact(visibility, factSource, strongerConfidence(routeConfidence, upstreamConfidence), sampled),
                    fact(capabilities.controlScope(), PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER, routeConfidence, sampled),
                    fact(playerPath, PlaybackAutoContext.ValueSource.ROUTE_CLASSIFIER, playerConfidence, sampled),
                    fact(upstreamPath, factSource, upstreamConfidence, sampled),
                    fact(upstreamState, factSource, upstreamState == PlaybackAutoContext.UpstreamState.UNKNOWN
                            ? PlaybackAutoContext.Confidence.UNKNOWN : strongerConfidence(playerConfidence, upstreamConfidence), sampled));
        }

        public String logSummary() {
            return "protocol=" + protocol.label()
                    + " stream=" + streamKind.label()
                    + " range=" + rangeSupport.label()
                    + " transfer=" + transferUnit.label()
                    + " playerPath=" + playerPath.label()
                    + " upstreamPath=" + upstreamPath.label()
                    + " upstream=" + upstreamState.label()
                    + " manifest=" + manifest.kind().label()
                    + " endList=" + safeBoolean(manifest.endList())
                    + " targetMs=" + safeLong(manifest.targetDurationMs())
                    + " partMs=" + safeLong(manifest.partDurationMs())
                    + " holdBackMs=" + safeLong(manifest.holdBackMs())
                    + " variants=" + safeInt(manifest.variantCount())
                    + " source=" + source.label()
                    + " evidence=" + evidenceLabels()
                    + " confidence=" + confidence.label();
        }

        private String evidenceLabels() {
            if (evidence.isEmpty()) return "none";
            StringBuilder builder = new StringBuilder();
            for (Evidence item : evidence) {
                if (builder.length() > 0) builder.append(',');
                builder.append(item.label());
            }
            return builder.toString();
        }

        private <T> PlaybackAutoContext.Fact<T> fact(T value, PlaybackAutoContext.ValueSource factSource, long sampled) {
            return fact(value, factSource, confidence, sampled);
        }

        private <T> PlaybackAutoContext.Fact<T> fact(T value, PlaybackAutoContext.ValueSource factSource,
                                                     PlaybackAutoContext.Confidence factConfidence, long sampled) {
            if (value == null || value.equals(PlaybackAutoContext.Protocol.UNKNOWN)
                    || value.equals(PlaybackAutoContext.StreamKind.UNKNOWN)
                    || value.equals(PlaybackAutoContext.RangeSupport.UNKNOWN)
                    || value.equals(PlaybackAutoContext.TransferUnit.UNKNOWN)
                    || value.equals(PlaybackAutoContext.PathKind.UNKNOWN)
                    || value.equals(PlaybackAutoContext.UpstreamState.UNKNOWN)
                    || value.equals(PlaybackAutoContext.ManifestFacts.unknown())) {
                return PlaybackAutoContext.Fact.unknown(value);
            }
            return PlaybackAutoContext.Fact.forSession(value, factSource, factConfidence, sampled);
        }

        private <T> PlaybackAutoContext.Fact<T> resourceFact(T value, PlaybackAutoContext.ValueSource factSource,
                                                             long sampled, boolean ttl, long validForMs) {
            return resourceFact(value, factSource, confidence, sampled, ttl, validForMs);
        }

        private <T> PlaybackAutoContext.Fact<T> resourceFact(T value, PlaybackAutoContext.ValueSource factSource,
                                                             PlaybackAutoContext.Confidence factConfidence,
                                                             long sampled, boolean ttl, long validForMs) {
            PlaybackAutoContext.Fact<T> session = fact(value, factSource, factConfidence, sampled);
            if (!ttl || !session.hasValue()) return session;
            return PlaybackAutoContext.Fact.withTtl(value, factSource, factConfidence, sampled, validForMs);
        }
    }

    public enum Evidence {
        URI_SCHEME("uri-scheme"),
        MIME("mime"),
        FORMAT("format"),
        EXTENSION("extension"),
        HTTP_HEADERS("http-headers"),
        CONTENT_RANGE("content-range"),
        HLS_TAG("hls-tag"),
        DASH_ATTRIBUTE("dash-attribute"),
        PLAYER_CALLBACK("player-callback"),
        CONFLICT("conflict"),
        UNKNOWN("unknown");

        private final String label;

        Evidence(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public enum ValueSource {
        PLAYBACK_REQUEST("playback-request"),
        DATA_SOURCE("data-source"),
        MANIFEST("manifest"),
        PLAYER_CALLBACK("player-callback"),
        UNKNOWN("unknown");

        private final String label;

        ValueSource(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        PlaybackAutoContext.ValueSource contextSource() {
            return switch (this) {
                case PLAYBACK_REQUEST -> PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST;
                case DATA_SOURCE -> PlaybackAutoContext.ValueSource.DATA_SOURCE;
                case MANIFEST -> PlaybackAutoContext.ValueSource.MANIFEST;
                case PLAYER_CALLBACK -> PlaybackAutoContext.ValueSource.PLAYER_CALLBACK;
                case UNKNOWN -> PlaybackAutoContext.ValueSource.UNKNOWN;
            };
        }
    }

    private static Classification parseManifest(String playerUri, String upstreamUri, String manifest,
                                                Map<String, List<String>> headers, long observedAtElapsedMs) {
        String lower = manifest.toLowerCase(Locale.US);
        if (lower.contains("#extm3u") || lower.contains("#ext-x-")) {
            return parseHls(playerUri, upstreamUri, manifest, headers, observedAtElapsedMs);
        }
        if (lower.contains("<mpd")) return parseDash(playerUri, upstreamUri, manifest, headers, observedAtElapsedMs);
        return new Classification(PlaybackAutoContext.Protocol.UNKNOWN, PlaybackAutoContext.StreamKind.UNKNOWN,
                rangeFromHeaders(headers), PlaybackAutoContext.TransferUnit.UNKNOWN,
                classifyPath(playerUri).kind(), classifyUpstreamPath(classifyPath(playerUri), upstreamUri).kind(),
                upstreamState(classifyPath(playerUri), upstreamUri, classifyUpstreamPath(classifyPath(playerUri), upstreamUri)),
                PlaybackAutoContext.ManifestFacts.unknown(), List.of(Evidence.UNKNOWN), PlaybackAutoContext.Confidence.UNKNOWN,
                ValueSource.UNKNOWN, false, -1);
    }

    private static Classification parseHls(String playerUri, String upstreamUri, String text,
                                           Map<String, List<String>> headers, long observedAtElapsedMs) {
        String upper = text.toUpperCase(Locale.US);
        boolean master = upper.contains("#EXT-X-STREAM-INF") || upper.contains("#EXT-X-I-FRAME-STREAM-INF");
        boolean media = upper.contains("#EXTINF") || upper.contains("#EXT-X-TARGETDURATION") || upper.contains("#EXT-X-MEDIA-SEQUENCE") || upper.contains("#EXT-X-PART");
        boolean endList = upper.contains("#EXT-X-ENDLIST");
        String playlistType = firstText(HLS_PLAYLIST_TYPE, text).trim();
        boolean explicitVod = "VOD".equalsIgnoreCase(playlistType);
        boolean hasPart = HLS_PART.matcher(text).find() || upper.contains("#EXT-X-PRELOAD-HINT") || upper.contains("PART-HOLD-BACK") || HLS_PART_TARGET.matcher(text).find();
        long targetMs = firstDecimalMs(HLS_TARGET_DURATION, text);
        long partMs = firstDecimalMs(HLS_PART_TARGET, text);
        if (partMs < 0) partMs = firstDecimalMs(HLS_PART_DURATION, text);
        long partHoldBackMs = firstDecimalMs(HLS_PART_HOLD_BACK, text);
        long holdBackMs = partHoldBackMs >= 0 ? partHoldBackMs : firstDecimalMs(HLS_HOLD_BACK, text);
        int variants = countOccurrences(upper, "#EXT-X-STREAM-INF");
        boolean byteRange = upper.contains("#EXT-X-BYTERANGE") || upper.contains("BYTERANGE=");
        PlaybackAutoContext.ManifestKind manifestKind = master ? PlaybackAutoContext.ManifestKind.HLS_MASTER
                : media ? PlaybackAutoContext.ManifestKind.HLS_MEDIA : PlaybackAutoContext.ManifestKind.UNKNOWN;
        PlaybackAutoContext.StreamKind stream = master ? PlaybackAutoContext.StreamKind.UNKNOWN
                : explicitVod && !endList ? PlaybackAutoContext.StreamKind.UNKNOWN
                : endList ? PlaybackAutoContext.StreamKind.VOD
                : hasPart ? PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE
                : media ? PlaybackAutoContext.StreamKind.LIVE : PlaybackAutoContext.StreamKind.UNKNOWN;
        PlaybackAutoContext.TransferUnit transfer = hasPart ? PlaybackAutoContext.TransferUnit.PART
                : media || master ? PlaybackAutoContext.TransferUnit.SEGMENT : PlaybackAutoContext.TransferUnit.UNKNOWN;
        PlaybackAutoContext.RangeSupport headerRange = rangeFromHeaders(headers);
        PlaybackAutoContext.RangeSupport range = byteRange ? PlaybackAutoContext.RangeSupport.SUPPORTED : headerRange;
        PlaybackAutoContext.Confidence confidence = manifestKind == PlaybackAutoContext.ManifestKind.UNKNOWN
                ? PlaybackAutoContext.Confidence.UNKNOWN : PlaybackAutoContext.Confidence.HIGH;
        PathObservation playerPath = classifyPath(playerUri);
        PathObservation upstreamPath = classifyUpstreamPath(playerPath, upstreamUri);
        return new Classification(PlaybackAutoContext.Protocol.HLS, stream, range, transfer,
                playerPath.kind(), upstreamPath.kind(), upstreamState(playerPath, upstreamUri, upstreamPath),
                new PlaybackAutoContext.ManifestFacts(manifestKind, media ? endList : null, targetMs < 0 ? null : targetMs,
                        partMs < 0 ? null : partMs, holdBackMs < 0 ? null : holdBackMs,
                        master ? variants : null, byteRange, hasPart),
                List.copyOf(evidence(Evidence.HLS_TAG,
                        headerRange == PlaybackAutoContext.RangeSupport.UNKNOWN ? Evidence.UNKNOWN : Evidence.HTTP_HEADERS)),
                confidence, ValueSource.MANIFEST, true, observedAtElapsedMs);
    }

    private static Classification parseDash(String playerUri, String upstreamUri, String text,
                                            Map<String, List<String>> headers, long observedAtElapsedMs) {
        Matcher matcher = DASH_MPD.matcher(text);
        String attributes = matcher.find() ? matcher.group(1) : "";
        String type = attribute(attributes, "type");
        boolean dynamic = "dynamic".equalsIgnoreCase(type);
        boolean staticManifest = "static".equalsIgnoreCase(type);
        boolean lowLatency = containsIgnoreCase(text, "availabilityTimeOffset")
                || containsIgnoreCase(text, "availabilityTimeComplete=\"false\"")
                || containsIgnoreCase(text, "availabilityTimeComplete='false'")
                || containsIgnoreCase(text, "urn:mpeg:dash:ll");
        boolean range = containsIgnoreCase(text, "<SegmentBase") || containsIgnoreCase(text, "indexRange") || containsIgnoreCase(text, "mediaRange");
        PlaybackAutoContext.StreamKind stream = dynamic
                ? (lowLatency ? PlaybackAutoContext.StreamKind.LOW_LATENCY_LIVE : PlaybackAutoContext.StreamKind.LIVE)
                : staticManifest ? PlaybackAutoContext.StreamKind.VOD : PlaybackAutoContext.StreamKind.UNKNOWN;
        PlaybackAutoContext.ManifestKind kind = dynamic ? PlaybackAutoContext.ManifestKind.DASH_DYNAMIC
                : staticManifest ? PlaybackAutoContext.ManifestKind.DASH_STATIC : PlaybackAutoContext.ManifestKind.UNKNOWN;
        PlaybackAutoContext.RangeSupport headerRange = rangeFromHeaders(headers);
        PlaybackAutoContext.RangeSupport rangeSupport = range ? PlaybackAutoContext.RangeSupport.SUPPORTED : headerRange;
        PlaybackAutoContext.TransferUnit transfer = lowLatency ? PlaybackAutoContext.TransferUnit.PART : PlaybackAutoContext.TransferUnit.SEGMENT;
        PathObservation playerPath = classifyPath(playerUri);
        PathObservation upstreamPath = classifyUpstreamPath(playerPath, upstreamUri);
        return new Classification(PlaybackAutoContext.Protocol.DASH, stream, rangeSupport, transfer,
                playerPath.kind(), upstreamPath.kind(), upstreamState(playerPath, upstreamUri, upstreamPath),
                new PlaybackAutoContext.ManifestFacts(kind, null, null, null, null, null, range, lowLatency),
                List.copyOf(evidence(Evidence.DASH_ATTRIBUTE,
                        headerRange == PlaybackAutoContext.RangeSupport.UNKNOWN ? Evidence.UNKNOWN : Evidence.HTTP_HEADERS)),
                kind == PlaybackAutoContext.ManifestKind.UNKNOWN
                ? PlaybackAutoContext.Confidence.UNKNOWN : PlaybackAutoContext.Confidence.HIGH,
                ValueSource.MANIFEST, true, observedAtElapsedMs);
    }

    private static ProtocolGuess protocolFromRequest(String uri, String mimeType, String format) {
        String normalizedUri = lower(uri);
        String mime = mediaToken(mimeType);
        String fmt = mediaToken(format);
        PlaybackAutoContext.Protocol byUri = protocolFromUri(normalizedUri);
        PlaybackAutoContext.Protocol byMime = protocolFromToken(mime);
        PlaybackAutoContext.Protocol byFormat = protocolFromToken(fmt);
        EnumSet<Evidence> evidence = EnumSet.noneOf(Evidence.class);
        if (byUri != PlaybackAutoContext.Protocol.UNKNOWN) evidence.add(Evidence.URI_SCHEME);
        if (byMime != PlaybackAutoContext.Protocol.UNKNOWN) evidence.add(Evidence.MIME);
        if (byFormat != PlaybackAutoContext.Protocol.UNKNOWN) evidence.add(Evidence.FORMAT);
        PlaybackAutoContext.Protocol selected = firstKnown(byUri, byMime, byFormat);
        boolean conflict = conflicts(byMime, byFormat, byUri);
        if (conflict) return new ProtocolGuess(PlaybackAutoContext.Protocol.UNKNOWN, PlaybackAutoContext.StreamKind.UNKNOWN,
                PlaybackAutoContext.Confidence.UNKNOWN, add(evidence, Evidence.CONFLICT));
        if (selected == PlaybackAutoContext.Protocol.UNKNOWN && (normalizedUri.startsWith("http://") || normalizedUri.startsWith("https://"))) {
            if (hasProgressiveExtension(normalizedUri)) {
                selected = PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP;
                evidence.add(Evidence.EXTENSION);
            }
        }
        PlaybackAutoContext.StreamKind stream = selected == PlaybackAutoContext.Protocol.RTSP || selected == PlaybackAutoContext.Protocol.RTMP
                ? PlaybackAutoContext.StreamKind.LIVE : PlaybackAutoContext.StreamKind.UNKNOWN;
        PlaybackAutoContext.Confidence confidence = selected == PlaybackAutoContext.Protocol.UNKNOWN
                ? PlaybackAutoContext.Confidence.UNKNOWN : evidence.contains(Evidence.MIME) || evidence.contains(Evidence.FORMAT)
                ? PlaybackAutoContext.Confidence.HIGH : PlaybackAutoContext.Confidence.MEDIUM;
        return new ProtocolGuess(selected, stream, confidence, evidence);
    }

    private static PlaybackAutoContext.Protocol protocolFromToken(String value) {
        String token = lower(value);
        if (token.isEmpty()) return PlaybackAutoContext.Protocol.UNKNOWN;
        if (token.startsWith("rtsp:")) return PlaybackAutoContext.Protocol.RTSP;
        if (token.startsWith("rtmp:")) return PlaybackAutoContext.Protocol.RTMP;
        if (token.contains("m3u8") || token.contains("mpegurl") || token.equals("hls") || token.contains("format=hls") || token.contains("type=hls")) return PlaybackAutoContext.Protocol.HLS;
        if (token.contains(".mpd") || token.contains("dash+xml") || token.equals("dash") || token.contains("format=dash") || token.contains("type=mpd")) return PlaybackAutoContext.Protocol.DASH;
        if (token.startsWith("file:") || token.startsWith("content:") || token.startsWith("asset:") || token.startsWith("data:") || token.startsWith("android.resource:")) return PlaybackAutoContext.Protocol.LOCAL;
        if (isProgressiveMime(token) || hasProgressiveExtension(token)) return PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP;
        return PlaybackAutoContext.Protocol.UNKNOWN;
    }

    private static PlaybackAutoContext.Protocol protocolFromUri(String value) {
        String token = lower(value);
        if (token.startsWith("rtsp:")) return PlaybackAutoContext.Protocol.RTSP;
        if (token.startsWith("rtmp:")) return PlaybackAutoContext.Protocol.RTMP;
        if (token.startsWith("file:") || token.startsWith("content:") || token.startsWith("asset:") || token.startsWith("data:") || token.startsWith("android.resource:")) return PlaybackAutoContext.Protocol.LOCAL;
        if (token.startsWith("http:") || token.startsWith("https:")) {
            if (token.contains("m3u8") || token.contains("mpegurl") || token.contains("format=hls") || token.contains("type=hls")) return PlaybackAutoContext.Protocol.HLS;
            if (token.contains(".mpd") || token.contains("format=dash") || token.contains("type=mpd")) return PlaybackAutoContext.Protocol.DASH;
            if (hasProgressiveExtension(token)) return PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP;
        }
        return PlaybackAutoContext.Protocol.UNKNOWN;
    }

    private static PlaybackAutoContext.Protocol firstKnown(PlaybackAutoContext.Protocol... values) {
        for (PlaybackAutoContext.Protocol value : values) if (value != null && value != PlaybackAutoContext.Protocol.UNKNOWN) return value;
        return PlaybackAutoContext.Protocol.UNKNOWN;
    }

    private static boolean conflicts(PlaybackAutoContext.Protocol... values) {
        PlaybackAutoContext.Protocol selected = PlaybackAutoContext.Protocol.UNKNOWN;
        for (PlaybackAutoContext.Protocol value : values) {
            if (value == null || value == PlaybackAutoContext.Protocol.UNKNOWN) continue;
            if (selected == PlaybackAutoContext.Protocol.UNKNOWN) selected = value;
            else if (selected != value && !compatibleTransportHints(selected, value)) return true;
        }
        return false;
    }

    private static boolean compatibleTransportHints(PlaybackAutoContext.Protocol first, PlaybackAutoContext.Protocol second) {
        if (first == PlaybackAutoContext.Protocol.LOCAL && second == PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP) return true;
        if (second == PlaybackAutoContext.Protocol.LOCAL && first == PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP) return true;
        return (first == PlaybackAutoContext.Protocol.RTSP || first == PlaybackAutoContext.Protocol.RTMP)
                && second == PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP
                || (second == PlaybackAutoContext.Protocol.RTSP || second == PlaybackAutoContext.Protocol.RTMP)
                && first == PlaybackAutoContext.Protocol.PROGRESSIVE_HTTP;
    }

    private static boolean isSegmentedProtocol(PlaybackAutoContext.Protocol protocol) {
        return protocol == PlaybackAutoContext.Protocol.HLS || protocol == PlaybackAutoContext.Protocol.DASH;
    }

    private static PlaybackAutoContext.TransferUnit transferFor(PlaybackAutoContext.Protocol protocol, boolean lowLatency) {
        if (lowLatency) return PlaybackAutoContext.TransferUnit.PART;
        return switch (protocol) {
            case HLS, DASH -> PlaybackAutoContext.TransferUnit.SEGMENT;
            case PROGRESSIVE_HTTP, LOCAL, RTSP, RTMP -> PlaybackAutoContext.TransferUnit.CONTINUOUS;
            default -> PlaybackAutoContext.TransferUnit.UNKNOWN;
        };
    }

    private static PlaybackAutoContext.RangeSupport rangeFromHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) return PlaybackAutoContext.RangeSupport.UNKNOWN;
        boolean sawRange = false;
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            String key = lower(entry.getKey());
            if ("accept-ranges".equals(key)) {
                sawRange = true;
                for (String value : safeList(entry.getValue())) if ("bytes".equalsIgnoreCase(value == null ? "" : value.trim())) return PlaybackAutoContext.RangeSupport.SUPPORTED;
            }
            if ("content-range".equals(key)) {
                sawRange = true;
                for (String value : safeList(entry.getValue())) if (validContentRange(value)) return PlaybackAutoContext.RangeSupport.SUPPORTED;
            }
        }
        return sawRange ? PlaybackAutoContext.RangeSupport.UNSUPPORTED : PlaybackAutoContext.RangeSupport.UNKNOWN;
    }

    private static boolean validContentRange(String value) {
        if (value == null) return false;
        Matcher matcher = Pattern.compile("bytes\\s+(\\d+)\\s*-\\s*(\\d+)\\s*/\\s*(\\d+|\\*)", Pattern.CASE_INSENSITIVE).matcher(value.trim());
        if (!matcher.matches()) return false;
        try {
            long start = Long.parseLong(matcher.group(1));
            long end = Long.parseLong(matcher.group(2));
            return end >= start;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static PathObservation classifyPath(String value) {
        if (value == null || value.isBlank()) return new PathObservation(PlaybackAutoContext.PathKind.UNKNOWN, Evidence.UNKNOWN);
        PlaybackRoute.Resolution resolution = PlaybackRoute.resolve(value);
        boolean loopbackAlias = resolution.location() == PlaybackRoute.Location.LAN_PRIVATE && isIpv4LoopbackLiteral(value);
        PlaybackAutoContext.PathKind kind = switch (resolution.location()) {
            case LOCAL -> PlaybackAutoContext.PathKind.LOCAL;
            case LAN_PRIVATE -> loopbackAlias ? PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK : PlaybackAutoContext.PathKind.LAN_PRIVATE;
            case REMOTE -> PlaybackAutoContext.PathKind.REMOTE;
            case APP_INTERNAL_SERVICE -> PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE;
            case EXTERNAL_LOOPBACK -> PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK;
            case UNKNOWN -> fallbackPath(value);
        };
        Evidence evidence = switch (resolution.evidence()) {
            case LOCAL_SCHEME -> Evidence.URI_SCHEME;
            case PRIVATE_HOST -> Evidence.URI_SCHEME;
            case REGISTERED_APP_PORT, UNREGISTERED_LOOPBACK_PORT -> Evidence.URI_SCHEME;
            case REMOTE_HOST -> Evidence.URI_SCHEME;
            default -> Evidence.UNKNOWN;
        };
        return new PathObservation(kind, evidence);
    }

    private static boolean isIpv4LoopbackLiteral(String value) {
        try {
            String host = URI.create(value).getHost();
            if (host == null) return false;
            String[] parts = host.split("\\.");
            if (parts.length != 4) return false;
            for (String part : parts) {
                int octet = Integer.parseInt(part);
                if (octet < 0 || octet > 255) return false;
            }
            return Integer.parseInt(parts[0]) == 127;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static PlaybackAutoContext.PathKind fallbackPath(String value) {
        String lower = lower(value);
        if (lower.startsWith("file:") || lower.startsWith("content:") || lower.startsWith("asset:") || lower.startsWith("data:") || lower.startsWith("android.resource:")) return PlaybackAutoContext.PathKind.LOCAL;
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host == null || host.isBlank()) return PlaybackAutoContext.PathKind.UNKNOWN;
            String authority = host.contains(":") ? "[" + host + "]" : host;
            if (uri.getPort() >= 0) authority += ":" + uri.getPort();
            PlaybackRoute.Location location = PlaybackRoute.resolve("http://" + authority + "/").location();
            return switch (location) {
                case LAN_PRIVATE -> PlaybackAutoContext.PathKind.LAN_PRIVATE;
                case REMOTE -> PlaybackAutoContext.PathKind.REMOTE;
                case APP_INTERNAL_SERVICE -> PlaybackAutoContext.PathKind.APP_INTERNAL_SERVICE;
                case EXTERNAL_LOOPBACK -> PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK;
                case LOCAL -> PlaybackAutoContext.PathKind.LOCAL;
                case UNKNOWN -> PlaybackAutoContext.PathKind.UNKNOWN;
            };
        } catch (RuntimeException ignored) {
            return PlaybackAutoContext.PathKind.UNKNOWN;
        }
    }

    private static PathObservation classifyUpstreamPath(PathObservation player, String upstreamUri) {
        if (player.kind() == PlaybackAutoContext.PathKind.LOCAL) return player;
        if (player.kind() == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK) {
            return new PathObservation(PlaybackAutoContext.PathKind.UNKNOWN, Evidence.UNKNOWN);
        }
        if (upstreamUri != null && !upstreamUri.isBlank()) return classifyPath(upstreamUri);
        if (player.kind() == PlaybackAutoContext.PathKind.REMOTE || player.kind() == PlaybackAutoContext.PathKind.LAN_PRIVATE) return player;
        return new PathObservation(PlaybackAutoContext.PathKind.UNKNOWN, Evidence.UNKNOWN);
    }

    private static PlaybackAutoContext.UpstreamState upstreamState(PathObservation player, String upstreamUri, PathObservation upstream) {
        if (player.kind() == PlaybackAutoContext.PathKind.LOCAL) return PlaybackAutoContext.UpstreamState.NOT_APPLICABLE;
        if (player.kind() == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK) return PlaybackAutoContext.UpstreamState.OPAQUE;
        if (upstreamUri != null && !upstreamUri.isBlank()) {
            if (upstream.kind() == PlaybackAutoContext.PathKind.EXTERNAL_LOOPBACK) return PlaybackAutoContext.UpstreamState.OPAQUE;
            return upstream.kind() == PlaybackAutoContext.PathKind.UNKNOWN ? PlaybackAutoContext.UpstreamState.UNKNOWN : PlaybackAutoContext.UpstreamState.VISIBLE;
        }
        if (player.kind() == PlaybackAutoContext.PathKind.REMOTE || player.kind() == PlaybackAutoContext.PathKind.LAN_PRIVATE) return PlaybackAutoContext.UpstreamState.VISIBLE;
        return PlaybackAutoContext.UpstreamState.UNKNOWN;
    }

    private static Set<Evidence> evidence(Evidence... values) {
        LinkedHashSet<Evidence> result = new LinkedHashSet<>();
        for (Evidence value : values) if (value != null && value != Evidence.UNKNOWN) result.add(value);
        return result;
    }

    private static Set<Evidence> evidence(Set<Evidence> values, Evidence... additional) {
        LinkedHashSet<Evidence> result = new LinkedHashSet<>();
        if (values != null) result.addAll(values);
        if (additional != null) for (Evidence value : additional) if (value != null && value != Evidence.UNKNOWN) result.add(value);
        return result;
    }

    private static Set<Evidence> add(Set<Evidence> values, Evidence item) {
        LinkedHashSet<Evidence> result = new LinkedHashSet<>(values);
        result.add(item);
        return result;
    }

    private static PlaybackAutoContext.Confidence strongerConfidence(PlaybackAutoContext.Confidence first, PlaybackAutoContext.Confidence second) {
        return rank(second) >= rank(first) ? second : first;
    }

    private static PlaybackAutoContext.Confidence routeConfidence(PlaybackRoute.Confidence confidence) {
        if (confidence == null) return PlaybackAutoContext.Confidence.UNKNOWN;
        return switch (confidence) {
            case CONFIRMED -> PlaybackAutoContext.Confidence.HIGH;
            case INFERRED -> PlaybackAutoContext.Confidence.MEDIUM;
            case UNKNOWN -> PlaybackAutoContext.Confidence.UNKNOWN;
        };
    }

    private static PlaybackAutoContext.Confidence pathConfidence(PlaybackAutoContext.PathKind path) {
        if (path == null) return PlaybackAutoContext.Confidence.UNKNOWN;
        return switch (path) {
            case LOCAL, LAN_PRIVATE, REMOTE, APP_INTERNAL_SERVICE -> PlaybackAutoContext.Confidence.HIGH;
            case EXTERNAL_LOOPBACK -> PlaybackAutoContext.Confidence.MEDIUM;
            case UNKNOWN -> PlaybackAutoContext.Confidence.UNKNOWN;
        };
    }

    private static int rank(PlaybackAutoContext.Confidence value) {
        return switch (value) {
            case HIGH -> 3;
            case MEDIUM -> 2;
            case LOW -> 1;
            case UNKNOWN -> 0;
        };
    }

    private static String mediaToken(String value) {
        String token = lower(value);
        int semicolon = token.indexOf(';');
        if (semicolon >= 0) token = token.substring(0, semicolon);
        return token.trim();
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.US);
    }

    private static String urlPath(String value) {
        try {
            String path = URI.create(value).getPath();
            if (path != null) return path;
        } catch (IllegalArgumentException ignored) {
        }
        int end = value.length();
        int query = value.indexOf('?');
        int fragment = value.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        return value.substring(0, end);
    }

    private static boolean hasProgressiveExtension(String value) {
        String token = lower(value);
        int query = token.indexOf('?');
        int fragment = token.indexOf('#');
        int end = token.length();
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        token = token.substring(0, end);
        String[] extensions = {".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".flv", ".wmv", ".mpg", ".mpeg", ".3gp", ".ogv", ".mp3", ".flac", ".wav", ".ogg"};
        for (String extension : extensions) if (token.endsWith(extension)) return true;
        return false;
    }

    private static boolean isProgressiveMime(String value) {
        String token = mediaToken(value);
        return token.startsWith("video/mp4") || token.startsWith("video/x-matroska") || token.startsWith("video/webm")
                || token.startsWith("video/quicktime") || token.startsWith("video/x-msvideo") || token.startsWith("video/mpeg")
                || token.startsWith("video/x-flv") || token.startsWith("video/3gpp") || token.startsWith("audio/mpeg")
                || token.startsWith("audio/mp4") || token.startsWith("audio/ogg") || token.startsWith("audio/flac")
                || token.startsWith("audio/wav") || token.startsWith("audio/x-wav");
    }

    private static long firstDecimalMs(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        if (!matcher.find()) return -1;
        try {
            double seconds = Double.parseDouble(matcher.group(1));
            if (!(seconds >= 0) || seconds > Long.MAX_VALUE / 1000d) return -1;
            return Math.max(0, Math.round(seconds * 1000d));
        } catch (RuntimeException ignored) {
            return -1;
        }
    }

    private static String firstText(Pattern pattern, String text) {
        Matcher matcher = pattern.matcher(text == null ? "" : text);
        return matcher.find() && matcher.group(1) != null ? matcher.group(1) : "";
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (from >= 0 && from < text.length()) {
            int index = text.indexOf(needle, from);
            if (index < 0) break;
            count++;
            from = index + needle.length();
        }
        return count;
    }

    private static String attribute(String text, String name) {
        Matcher matcher = DASH_ATTRIBUTE.matcher(text == null ? "" : text);
        while (matcher.find()) {
            if (!name.equalsIgnoreCase(matcher.group(1))) continue;
            return matcher.group(2) == null ? matcher.group(3) : matcher.group(2);
        }
        return "";
    }

    private static boolean containsIgnoreCase(String text, String needle) {
        return lower(text).contains(lower(needle));
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static String safeBoolean(Boolean value) {
        return value == null ? "unknown" : value.toString();
    }

    private static long safeLong(Long value) {
        return value == null ? -1 : value;
    }

    private static int safeInt(Integer value) {
        return value == null ? -1 : value;
    }

    public static long manifestTtlMs(PlaybackAutoContext.ManifestFacts manifest) {
        if (manifest == null) return 30_000;
        long base = manifest.targetDurationMs() == null ? 0 : manifest.targetDurationMs();
        if (base <= 0 && manifest.partDurationMs() != null) {
            long part = manifest.partDurationMs();
            base = part > Long.MAX_VALUE / 3 ? Long.MAX_VALUE : part * 3;
        }
        if (base <= 0 && manifest.holdBackMs() != null) base = manifest.holdBackMs();
        if (base <= 0) return 30_000;
        long ttl = base > Long.MAX_VALUE / 3 ? Long.MAX_VALUE : base * 3;
        return Math.max(5_000, Math.min(60_000, ttl));
    }

    private record PathObservation(PlaybackAutoContext.PathKind kind, Evidence evidence) {
    }

    private record ProtocolGuess(PlaybackAutoContext.Protocol protocol,
                                 PlaybackAutoContext.StreamKind streamKind,
                                 PlaybackAutoContext.Confidence confidence,
                                 Set<Evidence> evidence) {
    }
}
