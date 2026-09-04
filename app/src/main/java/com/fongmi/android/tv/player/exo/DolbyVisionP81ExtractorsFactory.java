package com.fongmi.android.tv.player.exo;

import android.net.Uri;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.ColorInfo;
import androidx.media3.common.DataReader;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.CodecSpecificDataUtil;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo;
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.ExtractorsFactory;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.TrackOutput;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.PlaybackPerformanceSetting;
import com.github.catvod.crawler.SpiderDebug;
import com.suyashbelekar.exoplayerhdrutils.libdovi.FrameInfo;
import com.suyashbelekar.exoplayerhdrutils.libdovi.LibDovi;
import com.suyashbelekar.exoplayerhdrutils.video.transformers.DoviStrategy;
import com.suyashbelekar.exoplayerhdrutils.video.transformers.Hdr10PlusStrategy;
import com.suyashbelekar.exoplayerhdrutils.video.transformers.HevcFrameTransformer;
import com.suyashbelekar.exoplayerhdrutils.video.transformers.TransformStrategy;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Rewrites unsupported DV7 tracks to DV8.1 before Exo builds its track groups. */
@UnstableApi
final class DolbyVisionP81ExtractorsFactory implements ExtractorsFactory {

    private static final int TRANSFORM_GROWTH_BYTES = 10 * 1024;
    private static final TransformStrategy P81_STRATEGY = new TransformStrategy(
            DoviStrategy.CONVERT_TO_P8,
            DoviStrategy.CONVERT_TO_P8,
            // P8.1 already carries the DV dynamic metadata. Keeping a second
            // HDR10+ metadata stream makes some TV Dolby Vision decoders
            // renegotiate between DV and HDR10 while playing.
            Hdr10PlusStrategy.DISCARD);

    private static volatile Boolean converterAvailable;

    private final ExtractorsFactory delegate;
    @Nullable private final ExoDolbyVisionPlaybackState playbackState;

    DolbyVisionP81ExtractorsFactory(ExtractorsFactory delegate) {
        this(delegate, null);
    }

    DolbyVisionP81ExtractorsFactory(
            ExtractorsFactory delegate,
            @Nullable ExoDolbyVisionPlaybackState playbackState) {
        this.delegate = delegate;
        this.playbackState = playbackState;
    }

    @Override
    public Extractor[] createExtractors() {
        return wrap(delegate.createExtractors(), false);
    }

    @Override
    public Extractor[] createExtractors(
            Uri uri, Map<String, List<String>> responseHeaders) {
        return wrap(delegate.createExtractors(uri, responseHeaders), isRemoteUri(uri));
    }

    private Extractor[] wrap(Extractor[] extractors, boolean deferSeekForCues) {
        Extractor[] wrapped = new Extractor[extractors.length];
        boolean dv7P81Enabled = PlaybackPerformanceSetting.isDv7P81Enabled();
        for (int i = 0; i < extractors.length; i++) {
            Extractor extractor = extractors[i];
            if (extractor instanceof MatroskaExtractor
                    && (deferSeekForCues || dv7P81Enabled)) {
                int flags = MatroskaExtractor.FLAG_EMIT_RAW_SUBTITLE_DATA
                        | (deferSeekForCues ? MatroskaExtractor.FLAG_DEFER_SEEK_FOR_CUES : 0);
                extractor = new MatroskaExtractor(
                        new DefaultSubtitleParserFactory(),
                        flags,
                        dv7P81Enabled);
            }
            wrapped[i] = new DolbyVisionExtractor(extractor, playbackState);
        }
        return wrapped;
    }

    private static boolean isRemoteUri(@Nullable Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        return scheme != null
                && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
    }

    static PlaybackPath resolvePlaybackPath(
            boolean sourceSupported,
            boolean p81Supported,
            boolean hdr10Supported) {
        if (sourceSupported) return PlaybackPath.NATIVE;
        if (p81Supported) return PlaybackPath.P81;
        if (hdr10Supported) return PlaybackPath.HDR10;
        return PlaybackPath.UNSUPPORTED;
    }

    private static PlaybackPath resolvePlaybackPath(Format source) {
        if (!PlaybackPerformanceSetting.isDv7P81Enabled()
                || !isProfile7(source)
                || source.cryptoType != C.CRYPTO_TYPE_NONE) {
            return PlaybackPath.NATIVE;
        }
        Format p81 = asProfile81(source);
        Format hdr10 = asHdr10Fallback(source);
        boolean sourceSupported = hasHardwareDecoder(source);
        boolean p81Supported = isConverterAvailable() && hasHardwareDecoder(p81);
        boolean hdr10Supported = hasHardwareDecoder(hdr10);
        PlaybackPath path = resolvePlaybackPath(
                sourceSupported, p81Supported, hdr10Supported);
        if (SpiderDebug.isEnabled()) {
            SpiderDebug.log("exo-dv", "DV7 decision path=%s source=%s p81=%s sourceHw=%s p81Hw=%s hdr10Hw=%s size=%dx%d",
                    path, source.codecs, p81.codecs, sourceSupported, p81Supported,
                    hdr10Supported, source.width, source.height);
        }
        return path;
    }

    enum PlaybackPath { NATIVE, P81, HDR10, UNSUPPORTED }

    static boolean requiresAccessUnitTransformation(PlaybackPath path) {
        return path == PlaybackPath.P81;
    }

    static boolean isProfile7(@Nullable Format format) {
        if (format == null
                || !MimeTypes.VIDEO_DOLBY_VISION.equals(format.sampleMimeType)
                || format.codecs == null) return false;
        String codec = firstCodec(format.codecs).toLowerCase(Locale.US);
        return codec.startsWith("dvhe.07.") || codec.startsWith("dvh1.07.");
    }

    static Format asProfile81(Format source) {
        if (!isProfile7(source)) return source;
        Format.Builder builder = source.buildUpon()
                .setCodecs(rewriteProfile81(source.codecs));
        int level = dolbyVisionLevel(source.codecs);
        if (level >= 0) {
            byte[] dvCsd = CodecSpecificDataUtil.buildDolbyVisionInitializationData(
                    8, level, 1, 0);
            builder.setInitializationData(
                    rewriteDolbyVisionCsd(source.initializationData, dvCsd));
        }
        return builder.build();
    }

    static Format asHdr10Fallback(Format source) {
        ColorInfo color = source.colorInfo == null
                ? new ColorInfo.Builder()
                .setColorSpace(C.COLOR_SPACE_BT2020)
                .setColorRange(C.COLOR_RANGE_LIMITED)
                .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                .build()
                : source.colorInfo.buildUpon()
                .setColorSpace(C.COLOR_SPACE_BT2020)
                .setColorRange(C.COLOR_RANGE_LIMITED)
                .setColorTransfer(C.COLOR_TRANSFER_ST2084)
                .build();
        return source.buildUpon()
                .setSampleMimeType(MimeTypes.VIDEO_H265)
                .setCodecs(null)
                .setInitializationData(removeDolbyVisionCsd(source.initializationData))
                .setColorInfo(color)
                .build();
    }

    static String rewriteProfile81(@Nullable String codecs) {
        if (codecs == null || codecs.isBlank()) return codecs;
        return codecs.replaceFirst("(?i)(dvhe|dvh1)\\.07\\.", "$1.08.");
    }

    static List<byte[]> rewriteDolbyVisionCsd(
            @Nullable List<byte[]> initializationData, byte[] dvCsd) {
        List<byte[]> result = initializationData == null
                ? new ArrayList<>() : new ArrayList<>(initializationData);
        // Vendor Dolby Vision decoders commonly expect the original HEVC CSD layout for MKV
        // (usually only csd-0). Only replace an existing DV record; do not synthesize empty
        // csd-1/csd-2 entries when the container did not provide one.
        if (result.size() > 2 && isDolbyVisionCsd(result.get(2))) {
            result.set(2, dvCsd);
        }
        return result;
    }

    static List<byte[]> removeDolbyVisionCsd(
            @Nullable List<byte[]> initializationData) {
        if (initializationData == null || initializationData.isEmpty()) {
            return List.of();
        }
        List<byte[]> result = new ArrayList<>(initializationData.size());
        for (byte[] csd : initializationData) {
            if (!isDolbyVisionCsd(csd)) result.add(csd);
        }
        return result;
    }

    private static int dolbyVisionLevel(@Nullable String codecs) {
        if (codecs == null || codecs.isBlank()) return -1;
        String[] parts = firstCodec(codecs).split("\\.");
        if (parts.length < 3) return -1;
        try {
            int level = Integer.parseInt(parts[2]);
            return level >= 0 && level <= 63 ? level : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private static boolean isDolbyVisionCsd(@Nullable byte[] csd) {
        if (csd == null || csd.length < 5 || csd[0] != 1) return false;
        int profile = (csd[2] & 0xFF) >> 1;
        int level = ((csd[2] & 0x01) << 5) | ((csd[3] & 0xF8) >> 3);
        if (level <= 0 || level > 63) return false;
        return profile == 4 || profile == 5 || profile == 7
                || profile == 8 || profile == 9 || profile == 10;
    }

    private static String firstCodec(String codecs) {
        int comma = codecs.indexOf(',');
        return comma < 0 ? codecs.trim() : codecs.substring(0, comma).trim();
    }

    private static boolean hasHardwareDecoder(Format format) {
        try {
            String mimeType = format == null || format.sampleMimeType == null
                    ? MimeTypes.VIDEO_DOLBY_VISION : format.sampleMimeType;
            for (MediaCodecInfo info : MediaCodecSelector.DEFAULT.getDecoderInfos(
                    mimeType, false, false)) {
                if (info.hardwareAccelerated
                        && info.isFormatSupported(App.get(), format)) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isConverterAvailable() {
        Boolean known = converterAvailable;
        if (known != null) return known;
        synchronized (DolbyVisionP81ExtractorsFactory.class) {
            known = converterAvailable;
            if (known != null) return known;
            try {
                new HevcFrameTransformer(P81_STRATEGY);
                converterAvailable = true;
            } catch (Throwable error) {
                converterAvailable = false;
                if (SpiderDebug.isEnabled()) {
                    SpiderDebug.log("exo-dv", "DV7 P8.1 converter unavailable error=%s",
                            error.getClass().getSimpleName());
                }
            }
            return converterAvailable;
        }
    }

    private static final class DolbyVisionExtractor implements Extractor {

        private final Extractor delegate;
        @Nullable private final ExoDolbyVisionPlaybackState playbackState;

        DolbyVisionExtractor(
                Extractor delegate,
                @Nullable ExoDolbyVisionPlaybackState playbackState) {
            this.delegate = delegate;
            this.playbackState = playbackState;
        }

        @Override
        public boolean sniff(ExtractorInput input) throws IOException {
            return delegate.sniff(input);
        }

        @Override
        public void init(ExtractorOutput output) {
            delegate.init(new DolbyVisionExtractorOutput(output, playbackState));
        }

        @Override
        public int read(ExtractorInput input, PositionHolder seekPosition)
                throws IOException {
            return delegate.read(input, seekPosition);
        }

        @Override
        public void seek(long position, long timeUs) {
            delegate.seek(position, timeUs);
        }

        @Override
        public void release() {
            delegate.release();
        }

        @Override
        public Extractor getUnderlyingImplementation() {
            return delegate.getUnderlyingImplementation();
        }
    }

    private static final class DolbyVisionExtractorOutput
            implements ExtractorOutput {

        private final ExtractorOutput delegate;
        @Nullable private final ExoDolbyVisionPlaybackState playbackState;

        DolbyVisionExtractorOutput(
                ExtractorOutput delegate,
                @Nullable ExoDolbyVisionPlaybackState playbackState) {
            this.delegate = delegate;
            this.playbackState = playbackState;
        }

        @Override
        public TrackOutput track(int id, int type) {
            TrackOutput output = delegate.track(id, type);
            return type == C.TRACK_TYPE_VIDEO
                    ? new DolbyVisionTrackOutput(output, playbackState) : output;
        }

        @Override
        public void endTracks() {
            delegate.endTracks();
        }

        @Override
        public void seekMap(SeekMap seekMap) {
            delegate.seekMap(seekMap);
        }
    }

    private static final class DolbyVisionTrackOutput implements TrackOutput {

        private final TrackOutput delegate;
        private final ParsableByteArray outputData = new ParsableByteArray();
        @Nullable private final ExoDolbyVisionPlaybackState playbackState;

        private ByteBuffer pending = ByteBuffer.allocateDirect(1024 * 1024);
        private byte[] inputScratch = new byte[16 * 1024];
        private byte[] outputScratch = new byte[1024 * 1024];
        @Nullable private HevcFrameTransformer transformer;
        @Nullable private LibDovi validator;
        @Nullable private Format sourceFormat;
        private boolean converting;
        private boolean hdr10Fallback;
        private boolean formatDispatched;
        private long sampleCount;
        private long lastDiagnosticLogMs;

        DolbyVisionTrackOutput(
                TrackOutput delegate,
                @Nullable ExoDolbyVisionPlaybackState playbackState) {
            this.delegate = delegate;
            this.playbackState = playbackState;
        }

        @Override
        public void durationUs(long durationUs) {
            delegate.durationUs(durationUs);
        }

        @Override
        public void format(Format format) {
            boolean fallbackRequested = playbackState != null
                    && playbackState.isHdr10FallbackRequested()
                    && isProfile7(format);
            PlaybackPath path = fallbackRequested
                    ? PlaybackPath.HDR10 : resolvePlaybackPath(format);
            if (path == PlaybackPath.UNSUPPORTED) {
                if (SpiderDebug.isEnabled()) {
                    SpiderDebug.log("exo-dv", "DV7 unsupported: no hardware DV/P8.1/HDR10 decoder");
                }
                throw new IllegalStateException(
                        "DV7 has no supported hardware DV, P8.1, or HDR10 decoder");
            }
            if (path == PlaybackPath.HDR10 && playbackState != null) {
                playbackState.requestHdr10Fallback();
            }
            converting = requiresAccessUnitTransformation(path);
            hdr10Fallback = path == PlaybackPath.HDR10;
            sourceFormat = format;
            if (converting && playbackState != null) {
                playbackState.activateP81(format, asProfile81(format));
            }
            // HDR10 fallback changes only the decoder-visible format. Preserve the
            // original DV7 access units because affected vendor HEVC decoders fail to
            // produce a first frame after RPU/type-63 NAL units are stripped.
            formatDispatched = !(converting || hdr10Fallback);
            sampleCount = 0;
            lastDiagnosticLogMs = 0;
            pending.clear();
            transformer = converting
                    ? new HevcFrameTransformer(P81_STRATEGY) : null;
            if (hdr10Fallback) dispatchFormatIfNeeded();
            else if (!converting) delegate.format(format);
        }

        @Override
        public int sampleData(
                DataReader input,
                int length,
                boolean allowEndOfInput,
                int sampleDataPart) throws IOException {
            if (!converting
                    || sampleDataPart != SAMPLE_DATA_PART_MAIN) {
                return delegate.sampleData(
                        input, length, allowEndOfInput, sampleDataPart);
            }
            inputScratch = ensureCapacity(inputScratch, length);
            int read = input.read(inputScratch, 0, length);
            if (read == C.RESULT_END_OF_INPUT) {
                if (allowEndOfInput) return C.RESULT_END_OF_INPUT;
                throw new EOFException();
            }
            if (read > 0) {
                pending = ensureCapacity(pending, pending.position() + read);
                pending.put(inputScratch, 0, read);
            }
            return read;
        }

        @Override
        public void sampleData(
                ParsableByteArray data, int length, int sampleDataPart) {
            if (!converting
                    || sampleDataPart != SAMPLE_DATA_PART_MAIN) {
                delegate.sampleData(data, length, sampleDataPart);
                return;
            }
            pending = ensureCapacity(pending, pending.position() + length);
            pending.put(data.getData(), data.getPosition(), length);
            data.skipBytes(length);
        }

        @Override
        public void sampleMetadata(
                long timeUs,
                int flags,
                int size,
                int offset,
                @Nullable CryptoData cryptoData) {
            if (!converting || pending.position() == 0) {
                dispatchFormatIfNeeded();
                delegate.sampleMetadata(
                        timeUs, flags, size, offset, cryptoData);
                return;
            }

            int pendingLength = pending.position();
            int carrySize = Math.max(0, Math.min(offset, pendingLength));
            int sampleLength = pendingLength - carrySize;
            byte[] carry = carrySize == 0 ? null : new byte[carrySize];
            if (carry != null) {
                pending.limit(pendingLength).position(sampleLength);
                pending.get(carry);
            }

            pending = ensureCapacity(pending, sampleLength + TRANSFORM_GROWTH_BYTES);
            outputScratch = ensureCapacity(outputScratch, sampleLength);
            pending.limit(sampleLength).position(0);
            pending.duplicate().get(outputScratch, 0, sampleLength);
            NalStats sourceStats = SpiderDebug.isEnabled()
                    ? inspectNalus(outputScratch, sampleLength) : null;
            int outputLength = sampleLength;
            boolean invalidP81 = false;
            if (transformer != null && sampleLength > 0) {
                try {
                    pending.limit(sampleLength).position(0);
                    outputLength = transformer.transformFrame(
                            pending, sampleLength);
                } catch (Throwable error) {
                    invalidP81 = true;
                    outputLength = sampleLength;
                    if (SpiderDebug.isEnabled()) {
                        SpiderDebug.log("exo-dv", "DV7 P8.1 conversion failed; "
                                + "aborting the locked P8.1 session error=%s",
                                error.getClass().getSimpleName());
                    }
                }
            }

            outputScratch = ensureCapacity(outputScratch, outputLength);
            if (!invalidP81) {
                pending.limit(outputLength).position(0);
                pending.get(outputScratch, 0, outputLength);
            }
            NalStats transformedStats = SpiderDebug.isEnabled()
                    ? inspectNalus(outputScratch, outputLength) : null;
            // Matroska may contain both an in-band RPU and an RPU exposed from
            // BlockAdditional. The latter is appended last by our Media3 fork.
            // Keep that authoritative RPU and never feed multiple dynamic
            // metadata NALs for one access unit to vendor Dolby Vision codecs.
            outputLength = stripProfile81Nalus(outputScratch, outputLength);
            if (converting && !invalidP81) {
                invalidP81 = isInvalidP81Frame(outputScratch, outputLength);
            }
            if (invalidP81) {
                logAuStats(sourceStats, transformedStats,
                        inspectNalus(outputScratch, outputLength), "invalid");
                throw new IllegalStateException(
                        "DV7 P8.1 conversion produced an invalid access unit");
            }
            dispatchFormatIfNeeded();
            logAuStats(sourceStats, transformedStats,
                    inspectNalus(outputScratch, outputLength),
                    "P8.1");
            outputData.reset(outputScratch, outputLength);
            delegate.sampleData(
                    outputData, outputLength, SAMPLE_DATA_PART_MAIN);
            delegate.sampleMetadata(
                    timeUs, flags, outputLength, 0, cryptoData);

            pending.clear();
            if (carry != null) pending.put(carry);
        }

        private void dispatchFormatIfNeeded() {
            if (sourceFormat == null) return;
            if (formatDispatched) return;
            Format output = hdr10Fallback
                    ? asHdr10Fallback(sourceFormat) : asProfile81(sourceFormat);
            delegate.format(output);
            formatDispatched = true;
            if (playbackState != null) {
                if (hdr10Fallback) playbackState.activate(sourceFormat, output);
                else playbackState.activateP81(sourceFormat, output);
            }
        }

        private boolean isInvalidP81Frame(byte[] output, int length) {
            // Parameter-set-only access units do not require dynamic metadata.
            // Every access unit containing a decoded picture must carry a valid
            // P8 RPU, otherwise a vendor decoder may output its HDR10 base layer.
            if (!containsVclNal(output, length)) return false;
            if (!containsNalType(output, length, 62)) return true;
            try {
                if (validator == null) validator = new LibDovi();
                pending = ensureCapacity(pending, length);
                pending.clear();
                pending.put(output, 0, length);
                pending.limit(length).position(0);
                FrameInfo info = validator.getFrameInfo(pending, length);
                return info == null || info.getDoviProfile() != 8;
            } catch (Throwable error) {
                return true;
            }
        }

        private void logAuStats(
                @Nullable NalStats source,
                @Nullable NalStats transformed,
                NalStats output,
                String mode) {
            if (!SpiderDebug.isEnabled()) return;
            sampleCount++;
            long now = SystemClock.elapsedRealtime();
            boolean anomalousRpuCount = (source != null && source.rpuCount > 1)
                    || (transformed != null && transformed.rpuCount > 1)
                    || output.rpuCount > 1;
            boolean periodic = sampleCount <= 3 || sampleCount % 600 == 0;
            if (!periodic && !"invalid".equals(mode)
                    && (!anomalousRpuCount || now - lastDiagnosticLogMs < 5000)) return;
            lastDiagnosticLogMs = now;
            SpiderDebug.log("exo-dv", "AU sample=%d source=%s transformed=%s output=%s "
                            + "locked=%s",
                    sampleCount, source, transformed, output, mode);
        }

        private static ByteBuffer ensureCapacity(
                ByteBuffer current, int requiredCapacity) {
            if (current.capacity() >= requiredCapacity) return current;
            int capacity = current.capacity();
            while (capacity < requiredCapacity) capacity *= 2;
            ByteBuffer expanded = ByteBuffer.allocateDirect(capacity);
            current.flip();
            expanded.put(current);
            return expanded;
        }

        private static byte[] ensureCapacity(
                byte[] current, int requiredCapacity) {
            if (current.length >= requiredCapacity) return current;
            int capacity = current.length;
            while (capacity < requiredCapacity) capacity *= 2;
            return new byte[capacity];
        }
    }

    static int stripEnhancementLayerNalus(byte[] data, int length) {
        return stripNalus(data, length, false, false, false);
    }

    static int stripProfile81Nalus(byte[] data, int length) {
        return stripNalus(data, length, false, true, true);
    }

    static int stripDolbyVisionNalus(byte[] data, int length) {
        return stripNalus(data, length, true, true, false);
    }

    private static int stripNalus(
            byte[] data,
            int length,
            boolean stripRpu,
            boolean stripHdr10Plus,
            boolean keepOnlyLastRpu) {
        int firstStart = findStartCode(data, 0, length);
        if (firstStart < 0) return length;
        int lastRpuStart = keepOnlyLastRpu ? findLastNalStart(data, length, 62) : -1;
        int writeOffset = 0;
        if (firstStart > 0) {
            System.arraycopy(data, 0, data, 0, firstStart);
            writeOffset = firstStart;
        }
        int start = firstStart;
        while (start >= 0 && start < length) {
            int startCodeLength = data[start + 2] == 1 ? 3 : 4;
            int payload = start + startCodeLength;
            int next = findStartCode(data, payload + 2, length);
            int end = next < 0 ? length : next;
            boolean enhancementLayer = false;
            if (payload + 1 < end) {
                int firstHeader = data[payload] & 0xFF;
                int secondHeader = data[payload + 1] & 0xFF;
                int nalType = (firstHeader & 0x7E) >> 1;
                int layerId = ((firstHeader & 0x01) << 5)
                        | ((secondHeader >> 3) & 0x1F);
                enhancementLayer = nalType == 63 || layerId > 0
                        || (stripRpu && nalType == 62)
                        || (keepOnlyLastRpu && nalType == 62 && start != lastRpuStart)
                        || (stripHdr10Plus && nalType == 39
                        && isHdr10PlusSei(data, payload, end));
            }
            if (!enhancementLayer) {
                int count = end - start;
                System.arraycopy(data, start, data, writeOffset, count);
                writeOffset += count;
            }
            start = next;
        }
        return writeOffset;
    }

    private static int findLastNalStart(byte[] data, int length, int expectedType) {
        int last = -1;
        int start = findStartCode(data, 0, length);
        while (start >= 0 && start < length) {
            int startCodeLength = data[start + 2] == 1 ? 3 : 4;
            int payload = start + startCodeLength;
            int next = findStartCode(data, payload + 2, length);
            int end = next < 0 ? length : next;
            if (payload + 1 < end) {
                int nalType = ((data[payload] & 0xFF) & 0x7E) >> 1;
                if (nalType == expectedType) last = start;
            }
            start = next;
        }
        return last;
    }

    private static NalStats inspectNalus(byte[] data, int length) {
        NalStats stats = new NalStats();
        int start = findStartCode(data, 0, length);
        while (start >= 0 && start < length) {
            int startCodeLength = data[start + 2] == 1 ? 3 : 4;
            int payload = start + startCodeLength;
            int next = findStartCode(data, payload + 2, length);
            int end = next < 0 ? length : next;
            if (payload + 1 < end) {
                int firstHeader = data[payload] & 0xFF;
                int secondHeader = data[payload + 1] & 0xFF;
                int nalType = (firstHeader & 0x7E) >> 1;
                int layerId = ((firstHeader & 0x01) << 5)
                        | ((secondHeader >> 3) & 0x1F);
                if (nalType <= 31) stats.vclCount++;
                if (nalType == 62) stats.rpuCount++;
                if (nalType == 63) stats.type63Count++;
                if (layerId > 0) stats.enhancementLayerCount++;
                if (nalType == 39 && isHdr10PlusSei(data, payload, end)) {
                    stats.hdr10PlusCount++;
                }
            }
            start = next;
        }
        return stats;
    }

    private static final class NalStats {

        int rpuCount;
        int vclCount;
        int type63Count;
        int enhancementLayerCount;
        int hdr10PlusCount;

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "vcl:%d,rpu:%d,type63:%d,el:%d,hdr10+:%d",
                    vclCount, rpuCount, type63Count,
                    enhancementLayerCount, hdr10PlusCount);
        }
    }

    private static boolean containsNalType(byte[] data, int length, int expectedType) {
        int start = findStartCode(data, 0, length);
        while (start >= 0 && start < length) {
            int startCodeLength = data[start + 2] == 1 ? 3 : 4;
            int payload = start + startCodeLength;
            int next = findStartCode(data, payload + 2, length);
            int end = next < 0 ? length : next;
            if (payload + 1 < end) {
                int nalType = ((data[payload] & 0xFF) & 0x7E) >> 1;
                if (nalType == expectedType) return true;
            }
            start = next;
        }
        return false;
    }

    static boolean containsVclNal(byte[] data, int length) {
        int start = findStartCode(data, 0, length);
        while (start >= 0 && start < length) {
            int startCodeLength = data[start + 2] == 1 ? 3 : 4;
            int payload = start + startCodeLength;
            int next = findStartCode(data, payload + 2, length);
            int end = next < 0 ? length : next;
            if (payload + 1 < end) {
                int nalType = ((data[payload] & 0xFF) & 0x7E) >> 1;
                if (nalType <= 31) return true;
            }
            start = next;
        }
        return false;
    }

    private static boolean isHdr10PlusSei(byte[] data, int payload, int end) {
        for (int i = payload + 2; i + 5 < end; i++) {
            if ((data[i] & 0xFF) == 0xB5
                    && (data[i + 1] & 0xFF) == 0x00
                    && (data[i + 2] & 0xFF) == 0x3C
                    && (data[i + 3] & 0xFF) == 0x00
                    && (data[i + 4] & 0xFF) == 0x01
                    && (data[i + 5] & 0xFF) == 0x04) return true;
        }
        return false;
    }

    private static int findStartCode(byte[] data, int offset, int length) {
        int start = Math.max(0, offset);
        for (int i = start; i + 2 < length; i++) {
            if (data[i] != 0 || data[i + 1] != 0) continue;
            if (i + 3 < length && data[i + 2] == 0
                    && data[i + 3] == 1) return i;
            if (data[i + 2] == 1) return i;
        }
        return -1;
    }

}
