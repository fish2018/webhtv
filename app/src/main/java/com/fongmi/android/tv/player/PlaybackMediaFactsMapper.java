package com.fongmi.android.tv.player;

import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.Format;

import com.fongmi.android.tv.player.engine.PlayerEngine;

import java.util.Locale;

/** Pure mapping from player runtime observations to the shared, privacy-safe fact model. */
public final class PlaybackMediaFactsMapper {

    private PlaybackMediaFactsMapper() {
    }

    public static MappedEngineFacts map(
            PlayerEngine.PlaybackFactsSnapshot snapshot,
            long trackSequence,
            boolean acceptDecoder,
            long sampledAtElapsedMs) {
        PlayerEngine.PlaybackFactsSnapshot observed = snapshot == null
                ? PlayerEngine.PlaybackFactsSnapshot.empty() : snapshot;
        Format video = observed.selectedVideoFormat() != null
                ? observed.selectedVideoFormat() : observed.videoDecoderFormat();
        Format audio = observed.selectedAudioFormat() != null
                ? observed.selectedAudioFormat() : observed.audioDecoderFormat();
        PlaybackAutoContext.TrackFacts videoFacts = mapTrack(video, sampledAtElapsedMs);
        PlaybackAutoContext.TrackFacts audioFacts = mapTrack(audio, sampledAtElapsedMs);
        DecoderMapping decoder = mapDecoder(observed, video, audio, trackSequence, acceptDecoder, sampledAtElapsedMs);
        return new MappedEngineFacts(videoFacts, audioFacts, decoder.facts(),
                mapOutput(observed, sampledAtElapsedMs), decoder.rawEvidence(), decoder.synchronizedWithTracks());
    }

    public static PlaybackAutoContext.TrackFacts mapTrack(Format format, long sampledAtElapsedMs) {
        if (format == null) return PlaybackAutoContext.TrackFacts.unknown();
        String mimeType = safeLabel(format.sampleMimeType);
        String codecs = safeLabel(format.codecs);
        CodecProfileLevel profileLevel = parseCodecProfileLevel(codecs, mimeType);
        PlaybackAutoContext.ColorSnapshot color = colorSnapshot(format.colorInfo);
        HdrMapping hdr = hdrType(codecs, format.colorInfo);
        return new PlaybackAutoContext.TrackFacts(
                stringFact(mimeType, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                stringFact(codecs, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                integerFact(profileLevel.profile(), PlaybackAutoContext.ValueSource.CODEC_STRING,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                integerFact(profileLevel.level(), PlaybackAutoContext.ValueSource.CODEC_STRING,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                integerFact(positive(format.width), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                integerFact(positive(format.height), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                floatFact(positiveFinite(format.frameRate), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                hdr.type() == PlaybackAutoContext.HdrType.UNKNOWN
                        ? PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.HdrType.UNKNOWN)
                        : PlaybackAutoContext.Fact.untilReplaced(hdr.type(), hdr.source(), hdr.confidence(), sampledAtElapsedMs),
                color.hasEvidence()
                        ? PlaybackAutoContext.Fact.untilReplaced(color, PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs)
                        : PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.ColorSnapshot.unknown()),
                longFact(positiveLong(format.averageBitrate), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                longFact(positiveLong(format.peakBitrate), PlaybackAutoContext.ValueSource.PLAYER_CALLBACK,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs));
    }

    public static boolean formatsMatch(Format selected, Format decoderFormat) {
        if (selected == null || decoderFormat == null) return false;
        if (selected.equals(decoderFormat)) return true;
        int compared = 0;
        String selectedMime = normalize(selected.sampleMimeType);
        String decoderMime = normalize(decoderFormat.sampleMimeType);
        if (!selectedMime.isEmpty() && !decoderMime.isEmpty()) {
            compared++;
            if (!selectedMime.equals(decoderMime)) return false;
        }
        String selectedCodec = normalizeCodec(selected.codecs);
        String decoderCodec = normalizeCodec(decoderFormat.codecs);
        if (!selectedCodec.isEmpty() && !decoderCodec.isEmpty()) {
            compared++;
            if (!selectedCodec.equals(decoderCodec)) return false;
        }
        if (selected.width > 0 && decoderFormat.width > 0) {
            compared++;
            if (selected.width != decoderFormat.width) return false;
        }
        if (selected.height > 0 && decoderFormat.height > 0) {
            compared++;
            if (selected.height != decoderFormat.height) return false;
        }
        if (positiveFinite(selected.frameRate) != null && positiveFinite(decoderFormat.frameRate) != null) {
            compared++;
            if (Math.abs(selected.frameRate - decoderFormat.frameRate) > 0.1f) return false;
        }
        return compared > 0;
    }

    public static String safeLabel(String value) {
        if (value == null) return "";
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > 128) return "";
        boolean mimeLike = normalized.matches("[A-Za-z0-9.+_-]+/[A-Za-z0-9.+_-]+");
        if (normalized.contains("://") || (!mimeLike && normalized.indexOf('/') >= 0) || normalized.indexOf('\\') >= 0
                || normalized.indexOf('?') >= 0 || normalized.indexOf('@') >= 0) return "";
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '.' || c == '_' || c == '-' || c == '+' || (mimeLike && c == '/')
                    || c == ':' || c == ',' || c == ' ' || c == '(' || c == ')') continue;
            return "";
        }
        return normalized;
    }

    private static DecoderMapping mapDecoder(
            PlayerEngine.PlaybackFactsSnapshot observed,
            Format selectedVideo,
            Format selectedAudio,
            long trackSequence,
            boolean acceptDecoder,
            long sampledAtElapsedMs) {
        String videoName = safeLabel(observed.videoDecoderName());
        String audioName = safeLabel(observed.audioDecoderName());
        boolean videoEvidence = !videoName.isEmpty()
                || observed.videoDecoderKind() != PlayerEngine.DecoderKind.UNKNOWN
                || observed.secureVideoDecoder() != null;
        boolean audioEvidence = !audioName.isEmpty();
        boolean rawEvidence = videoEvidence || audioEvidence;
        if (!acceptDecoder) {
            return new DecoderMapping(PlaybackAutoContext.DecoderFacts.unknown(trackSequence), rawEvidence, false);
        }
        boolean videoMatch = !videoEvidence || formatsMatch(selectedVideo, observed.videoDecoderFormat());
        boolean audioMatch = !audioEvidence || formatsMatch(selectedAudio, observed.audioDecoderFormat());
        boolean synchronizedWithTracks = videoMatch && audioMatch;
        if (rawEvidence && !synchronizedWithTracks) {
            return new DecoderMapping(PlaybackAutoContext.DecoderFacts.unknown(trackSequence), true, false);
        }
        if (!rawEvidence) return new DecoderMapping(null, false, false);

        PlaybackAutoContext.Fact<String> videoDecoder = stringFact(videoName,
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME, PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs);
        PlaybackAutoContext.Fact<String> audioDecoder = stringFact(audioName,
                PlaybackAutoContext.ValueSource.NATIVE_RUNTIME, PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs);
        PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> decodeMode = decodeModeFact(
                observed.videoDecoderKind(), videoName, sampledAtElapsedMs);
        PlaybackAutoContext.Fact<Boolean> secure = secureFact(
                observed.secureVideoDecoder(), videoName, sampledAtElapsedMs);
        return new DecoderMapping(new PlaybackAutoContext.DecoderFacts(
                trackSequence, videoDecoder, audioDecoder, decodeMode, secure), true, true);
    }

    private static PlaybackAutoContext.OutputFacts mapOutput(
            PlayerEngine.PlaybackFactsSnapshot observed,
            long sampledAtElapsedMs) {
        String hwdec = safeLabel(observed.hwdecCurrent());
        String currentVo = safeLabel(observed.currentVideoOutput());
        PlaybackAutoContext.RenderPath path = renderPath(observed.tunneling(), currentVo);
        boolean configuredTunneling = path == PlaybackAutoContext.RenderPath.EXO_TUNNELING;
        PlaybackAutoContext.Fact<PlaybackAutoContext.RenderPath> pathFact = path == PlaybackAutoContext.RenderPath.UNKNOWN
                ? PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.RenderPath.UNKNOWN)
                : PlaybackAutoContext.Fact.untilReplaced(path,
                configuredTunneling ? PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST
                        : PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                configuredTunneling ? PlaybackAutoContext.Confidence.MEDIUM
                        : PlaybackAutoContext.Confidence.HIGH,
                sampledAtElapsedMs);
        PlaybackAutoContext.Fact<Boolean> tunneling = observed.tunneling() == null
                ? PlaybackAutoContext.Fact.unknown(false)
                : PlaybackAutoContext.Fact.untilReplaced(observed.tunneling(), PlaybackAutoContext.ValueSource.PLAYBACK_REQUEST,
                PlaybackAutoContext.Confidence.MEDIUM, sampledAtElapsedMs);
        return new PlaybackAutoContext.OutputFacts(
                pathFact,
                PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.RenderTarget.UNKNOWN),
                tunneling,
                stringFact(hwdec, PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs),
                stringFact(currentVo, PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                        PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs));
    }

    // Package-private so the classification can be asserted directly; it is the single source
    // of truth for soft/hard decode across all three kernels.
    static PlaybackAutoContext.Fact<PlaybackAutoContext.DecodeMode> decodeModeFact(
            PlayerEngine.DecoderKind kind,
            String decoderName,
            long sampledAtElapsedMs) {
        if (kind == PlayerEngine.DecoderKind.HARDWARE) {
            return PlaybackAutoContext.Fact.untilReplaced(PlaybackAutoContext.DecodeMode.HARDWARE,
                    PlaybackAutoContext.ValueSource.NATIVE_RUNTIME, PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs);
        }
        if (kind == PlayerEngine.DecoderKind.SOFTWARE) {
            return PlaybackAutoContext.Fact.untilReplaced(PlaybackAutoContext.DecodeMode.SOFTWARE,
                    PlaybackAutoContext.ValueSource.NATIVE_RUNTIME, PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs);
        }
        String lower = normalize(decoderName);
        if (lower.startsWith("omx.google.") || lower.startsWith("c2.android.")
                || lower.contains("ffmpeg") || lower.contains("libgav1")
                || lower.contains("libvpx") || lower.contains("dav1d")
                || lower.contains("avcodec")) {
            return PlaybackAutoContext.Fact.untilReplaced(PlaybackAutoContext.DecodeMode.SOFTWARE,
                    PlaybackAutoContext.ValueSource.ESTIMATOR, PlaybackAutoContext.Confidence.MEDIUM, sampledAtElapsedMs);
        }
        return PlaybackAutoContext.Fact.unknown(PlaybackAutoContext.DecodeMode.UNKNOWN);
    }

    private static PlaybackAutoContext.Fact<Boolean> secureFact(
            Boolean secure,
            String decoderName,
            long sampledAtElapsedMs) {
        if (secure != null) {
            return PlaybackAutoContext.Fact.untilReplaced(secure, PlaybackAutoContext.ValueSource.NATIVE_RUNTIME,
                    PlaybackAutoContext.Confidence.HIGH, sampledAtElapsedMs);
        }
        String lower = normalize(decoderName);
        if (lower.contains(".secure") || lower.contains("secure.decoder") || lower.endsWith("-secure")) {
            return PlaybackAutoContext.Fact.untilReplaced(true, PlaybackAutoContext.ValueSource.ESTIMATOR,
                    PlaybackAutoContext.Confidence.MEDIUM, sampledAtElapsedMs);
        }
        return PlaybackAutoContext.Fact.unknown(false);
    }

    private static PlaybackAutoContext.RenderPath renderPath(Boolean tunneling, String currentVo) {
        if (Boolean.TRUE.equals(tunneling)) return PlaybackAutoContext.RenderPath.EXO_TUNNELING;
        String lower = normalize(currentVo);
        if (lower.contains("mediacodec_embed")) return PlaybackAutoContext.RenderPath.MPV_SURFACE_DIRECT;
        if (lower.equals("gpu") || lower.contains("gpu-next")) return PlaybackAutoContext.RenderPath.MPV_GPU;
        return PlaybackAutoContext.RenderPath.UNKNOWN;
    }

    private static PlaybackAutoContext.ColorSnapshot colorSnapshot(ColorInfo colorInfo) {
        if (colorInfo == null) return PlaybackAutoContext.ColorSnapshot.unknown();
        return new PlaybackAutoContext.ColorSnapshot(
                colorInfo.colorSpace,
                colorInfo.colorRange,
                colorInfo.colorTransfer,
                colorInfo.hdrStaticInfo != null && colorInfo.hdrStaticInfo.length > 0);
    }

    private static HdrMapping hdrType(String codecs, ColorInfo colorInfo) {
        String codec = normalizeCodec(codecs);
        if (codec.startsWith("dvhe") || codec.startsWith("dvh1")
                || codec.startsWith("dvav") || codec.startsWith("dva1")) {
            return new HdrMapping(PlaybackAutoContext.HdrType.DOLBY_VISION,
                    PlaybackAutoContext.ValueSource.CODEC_STRING, PlaybackAutoContext.Confidence.HIGH);
        }
        if (colorInfo == null) return HdrMapping.unknown();
        if (colorInfo.colorTransfer == C.COLOR_TRANSFER_ST2084) {
            return new HdrMapping(PlaybackAutoContext.HdrType.HDR10,
                    PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH);
        }
        if (colorInfo.colorTransfer == C.COLOR_TRANSFER_HLG) {
            return new HdrMapping(PlaybackAutoContext.HdrType.HLG,
                    PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH);
        }
        if (ColorInfo.isTransferHdr(colorInfo)) {
            return new HdrMapping(PlaybackAutoContext.HdrType.HDR_OTHER,
                    PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.MEDIUM);
        }
        if (colorInfo.colorTransfer == C.COLOR_TRANSFER_SDR
                || colorInfo.colorTransfer == C.COLOR_TRANSFER_SRGB
                || colorInfo.colorTransfer == C.COLOR_TRANSFER_LINEAR) {
            return new HdrMapping(PlaybackAutoContext.HdrType.SDR,
                    PlaybackAutoContext.ValueSource.PLAYER_CALLBACK, PlaybackAutoContext.Confidence.HIGH);
        }
        return HdrMapping.unknown();
    }

    private static CodecProfileLevel parseCodecProfileLevel(String codecs, String mimeType) {
        String codec = normalizeCodec(codecs);
        if (codec.isEmpty()) return CodecProfileLevel.unknown();
        String[] parts = codec.split("\\.");
        try {
            if ((codec.startsWith("avc1") || codec.startsWith("avc3")) && parts.length >= 2) {
                if (parts[1].length() == 6) {
                    return new CodecProfileLevel(
                            Integer.parseInt(parts[1].substring(0, 2), 16),
                            Integer.parseInt(parts[1].substring(4, 6), 16));
                }
                if (parts.length >= 3) return new CodecProfileLevel(parsePositive(parts[1]), parsePositive(parts[2]));
            }
            if ((codec.startsWith("hev1") || codec.startsWith("hvc1")) && parts.length >= 2) {
                Integer profile = parsePositive(parts[1]);
                Integer level = null;
                for (String part : parts) {
                    if (part.length() > 1 && (part.charAt(0) == 'l' || part.charAt(0) == 'h')
                            && Character.isDigit(part.charAt(1))) {
                        level = parsePositive(part.substring(1));
                        if (level != null) break;
                    }
                }
                return new CodecProfileLevel(profile, level);
            }
            if ((codec.startsWith("dvhe") || codec.startsWith("dvh1")
                    || codec.startsWith("dvav") || codec.startsWith("dva1")) && parts.length >= 3) {
                return new CodecProfileLevel(parsePositive(parts[1]), parsePositive(parts[2]));
            }
            if ((codec.startsWith("vp09") || codec.startsWith("vp08")) && parts.length >= 3) {
                return new CodecProfileLevel(parsePositive(parts[1]), parsePositive(parts[2]));
            }
            if (codec.startsWith("av01") && parts.length >= 3) {
                return new CodecProfileLevel(parsePositive(parts[1]), parseLeadingPositive(parts[2]));
            }
            if (codec.startsWith("mp4a") && parts.length >= 3) {
                return new CodecProfileLevel(parsePositive(parts[2]), null);
            }
        } catch (RuntimeException ignored) {
            return CodecProfileLevel.unknown();
        }
        return CodecProfileLevel.unknown();
    }

    private static Integer parsePositive(String value) {
        if (value == null || value.isEmpty()) return null;
        int parsed = Integer.parseInt(value);
        return parsed >= 0 ? parsed : null;
    }

    private static Integer parseLeadingPositive(String value) {
        if (value == null) return null;
        int end = 0;
        while (end < value.length() && Character.isDigit(value.charAt(end))) end++;
        return end == 0 ? null : parsePositive(value.substring(0, end));
    }

    private static String normalizeCodec(String value) {
        String normalized = normalize(value);
        int comma = normalized.indexOf(',');
        return comma < 0 ? normalized : normalized.substring(0, comma).trim();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US);
    }

    private static Integer positive(int value) {
        return value > 0 ? value : null;
    }

    private static Long positiveLong(int value) {
        return value > 0 ? (long) value : null;
    }

    private static Float positiveFinite(float value) {
        return value > 0 && Float.isFinite(value) ? value : null;
    }

    private static PlaybackAutoContext.Fact<String> stringFact(
            String value,
            PlaybackAutoContext.ValueSource source,
            PlaybackAutoContext.Confidence confidence,
            long sampledAtElapsedMs) {
        return value == null || value.isEmpty()
                ? PlaybackAutoContext.Fact.unknown("")
                : PlaybackAutoContext.Fact.untilReplaced(value, source, confidence, sampledAtElapsedMs);
    }

    private static PlaybackAutoContext.Fact<Integer> integerFact(
            Integer value,
            PlaybackAutoContext.ValueSource source,
            PlaybackAutoContext.Confidence confidence,
            long sampledAtElapsedMs) {
        return value == null
                ? PlaybackAutoContext.Fact.unknown(-1)
                : PlaybackAutoContext.Fact.untilReplaced(value, source, confidence, sampledAtElapsedMs);
    }

    private static PlaybackAutoContext.Fact<Long> longFact(
            Long value,
            PlaybackAutoContext.ValueSource source,
            PlaybackAutoContext.Confidence confidence,
            long sampledAtElapsedMs) {
        return value == null
                ? PlaybackAutoContext.Fact.unknown(-1L)
                : PlaybackAutoContext.Fact.untilReplaced(value, source, confidence, sampledAtElapsedMs);
    }

    private static PlaybackAutoContext.Fact<Float> floatFact(
            Float value,
            PlaybackAutoContext.ValueSource source,
            PlaybackAutoContext.Confidence confidence,
            long sampledAtElapsedMs) {
        return value == null
                ? PlaybackAutoContext.Fact.unknown(-1f)
                : PlaybackAutoContext.Fact.untilReplaced(value, source, confidence, sampledAtElapsedMs);
    }

    public record MappedEngineFacts(
            PlaybackAutoContext.TrackFacts videoTrack,
            PlaybackAutoContext.TrackFacts audioTrack,
            PlaybackAutoContext.DecoderFacts decoder,
            PlaybackAutoContext.OutputFacts output,
            boolean rawDecoderEvidence,
            boolean decoderSynchronizedWithTracks) {
    }

    private record DecoderMapping(
            PlaybackAutoContext.DecoderFacts facts,
            boolean rawEvidence,
            boolean synchronizedWithTracks) {
    }

    private record CodecProfileLevel(Integer profile, Integer level) {
        private static CodecProfileLevel unknown() {
            return new CodecProfileLevel(null, null);
        }
    }

    private record HdrMapping(
            PlaybackAutoContext.HdrType type,
            PlaybackAutoContext.ValueSource source,
            PlaybackAutoContext.Confidence confidence) {
        private static HdrMapping unknown() {
            return new HdrMapping(PlaybackAutoContext.HdrType.UNKNOWN,
                    PlaybackAutoContext.ValueSource.UNKNOWN, PlaybackAutoContext.Confidence.UNKNOWN);
        }
    }
}
