package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Proxy;

public class ExoCompressedAudioDirectPolicyTest {

    @Test
    public void standardOffload_isPreservedWithoutDirectProbe() {
        AtomicInteger directQueries = new AtomicInteger();
        AudioOffloadSupport standard = supported(true, true);
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> standard,
                (format, attributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });

        AudioOffloadSupport actual = policy.getAudioOffloadSupport(
                aacStereo(), AudioAttributes.DEFAULT);

        assertSame(standard, actual);
        assertTrue(actual.isFormatSupported);
        assertTrue(actual.isGaplessSupported);
        assertTrue(actual.isSpeedChangeSupported);
        assertTrue(directQueries.get() == 0);
    }

    @Test
    public void directBitstream_enablesEncodedBypassWithoutFakeOffload() {
        Format format = aacStereo();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(format));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);
        assertFalse(actual.isFormatSupportedForOffload);
        assertTrue(policy.usesVendorDirect(C.ENCODING_AAC_LC, 48_000,
                Util.getAudioTrackChannelConfig(format)));
    }

    @Test
    public void passthroughWithoutOffload_isOverriddenByVendorDirect() {
        Format format = aacStereo();
        AudioOutputProvider.FormatSupport passthrough =
                new AudioOutputProvider.FormatSupport.Builder()
                        .setFormatSupportLevel(
                                AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                        .setIsFormatSupportedForOffload(false)
                        .build();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);

        AudioOutputProvider.FormatSupport actual = policy.wrapOutputProvider(
                new FixedFormatSupportAudioOutputProvider(passthrough))
                .getFormatSupport(formatConfig(format));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);
        assertFalse(actual.isFormatSupportedForOffload);
        assertTrue(policy.usesVendorDirect(C.ENCODING_AAC_LC, 48_000,
                Util.getAudioTrackChannelConfig(format)));
    }

    @Test
    public void directBitstream_buildsNonOffloadEncodedOutput() throws Exception {
        Format format = aacStereo();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);
        AudioOutputProvider provider = wrapped(policy);
        AudioOutputProvider.FormatConfig config = formatConfig(format);
        provider.getFormatSupport(config);

        AudioOutputProvider.OutputConfig output = provider.getOutputConfig(config);

        assertTrue(output.encoding == C.ENCODING_AAC_LC);
        assertTrue(output.sampleRate == 48_000);
        assertTrue(output.channelMask == Util.getAudioTrackChannelConfig(format));
        assertTrue(output.bufferSize == 256 * 1024);
        assertFalse(output.isOffload);
        assertFalse(output.isTunneling);
        assertTrue(output.audioSessionId == 0);
        assertTrue(output.virtualDeviceId == C.INDEX_UNSET);
    }

    @Test
    public void tunneling_doesNotAdvertiseVendorOnlyBypass() {
        AtomicInteger queries = new AtomicInteger();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> { queries.incrementAndGet(); return true; });

        assertSame(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                wrapped(policy).getFormatSupport(tunnelingConfig()));
        assertEquals(0, queries.get());
        assertFalse(policy.usesVendorDirect(C.ENCODING_AAC_LC, 48_000,
                Util.getAudioTrackChannelConfig(aacStereo())));
    }

    @Test
    public void tunneling_afterCachedDirectProbe_preservesStandardConfiguration() throws Exception {
        Format format = aacStereo();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);
        wrapped(policy).getFormatSupport(formatConfig(format));
        AudioOutputProvider.OutputConfig standard = encodedOutput(true, false);
        StandardAudioOutputProvider delegate = new StandardAudioOutputProvider(standard);
        AudioOutputProvider provider = policy.wrapOutputProvider(delegate);

        // Even without a new capability query, the final request must not use the stale direct key.
        assertSame(standard, provider.getOutputConfig(tunnelingConfig()));
        assertTrue(standard.isTunneling);
        assertEquals(1234, standard.audioSessionId);
        assertSame(delegate.support, provider.getFormatSupport(tunnelingConfig()));
    }

    @Test
    public void finalStandardMode_ignoresDirectCacheAndPublishesItsActualOutput() throws Exception {
        for (boolean offload : new boolean[]{false, true}) {
            ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                    (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                    (format, attributes) -> true);
            wrapped(policy).getFormatSupport(formatConfig(aacStereo()));
            AudioOutputProvider.OutputConfig standard = encodedOutput(!offload, offload);
            StandardAudioOutputProvider delegate = new StandardAudioOutputProvider(standard);
            AudioOutput output = policy.wrapOutputProvider(delegate).getAudioOutput(standard);

            assertEquals(1, delegate.creations);
            assertEquals(!offload, policy.getAudioOutputSnapshot().tunneling());
            assertEquals(offload, policy.getAudioOutputSnapshot().offload());
            // A standard mode must not apply any vendor builder overrides, even with a cached key.
            policy.modifyAudioTrackBuilder(null, standard);
            output.release();
            assertFalse(policy.getAudioOutputSnapshot().initialized());
        }
    }

    @Test
    public void failedInitialization_doesNotPublishOutputState() {
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> false);
        assertThrows(AudioOutputProvider.InitializationException.class,
                () -> wrapped(policy).getAudioOutput(encodedOutput(false, false)));
        assertFalse(policy.getAudioOutputSnapshot().initialized());
    }

    @Test
    public void missingDirectSupport_keepsPcmFallback() {
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> false);

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(aacStereo()));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
    }

    @Test
    public void failedDirectConfig_isNotRetriedInSamePlayerSession() {
        AtomicInteger directQueries = new AtomicInteger();
        Format format = aacStereo();
        int channelMask = Util.getAudioTrackChannelConfig(format);
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });
        assertTrue(wrapped(policy).getFormatSupport(formatConfig(format))
                .supportLevel == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);

        policy.disableVendorDirect(C.ENCODING_AAC_LC, 48_000, channelMask);
        AudioOutputProvider.FormatSupport fallback = wrapped(policy)
                .getFormatSupport(formatConfig(format));

        assertTrue(fallback.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertFalse(policy.usesVendorDirect(
                C.ENCODING_AAC_LC, 48_000, channelMask));
        assertTrue(directQueries.get() == 1);
    }

    @Test
    public void failedDirectConfig_masksDelegatePassthroughForPcmFallback() {
        Format format = aacStereo();
        int channelMask = Util.getAudioTrackChannelConfig(format);
        AudioOutputProvider.FormatSupport passthrough =
                new AudioOutputProvider.FormatSupport.Builder()
                        .setFormatSupportLevel(
                                AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                        .setIsFormatSupportedForOffload(false)
                        .build();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (ignoredFormat, ignoredAttributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (ignoredFormat, ignoredAttributes) -> true);
        AudioOutputProvider provider = policy.wrapOutputProvider(
                new FixedFormatSupportAudioOutputProvider(passthrough));
        assertTrue(provider.getFormatSupport(formatConfig(format)).supportLevel
                == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY);

        policy.disableVendorDirect(C.ENCODING_AAC_LC, 48_000, channelMask);

        AudioOutputProvider.FormatSupport fallback =
                provider.getFormatSupport(formatConfig(format));
        assertTrue(fallback.supportLevel == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertTrue(policy.consumePcmFallbackRequest());
        assertFalse(policy.consumePcmFallbackRequest());
    }

    @Test
    public void unsupportedEncodedFrameType_doesNotProbeDirectPlayback() {
        AtomicInteger directQueries = new AtomicInteger();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });
        Format flac = new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_FLAC)
                .setSampleRate(48_000)
                .setChannelCount(2)
                .build();

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(flac));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertTrue(directQueries.get() == 0);
    }

    @Test
    public void incompleteFormat_doesNotProbeDirectPlayback() {
        AtomicInteger directQueries = new AtomicInteger();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> {
                    directQueries.incrementAndGet();
                    return true;
                });
        Format incomplete = new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setCodecs("mp4a.40.2")
                .build();

        AudioOutputProvider.FormatSupport actual = wrapped(policy)
                .getFormatSupport(formatConfig(incomplete));

        assertTrue(actual.supportLevel
                == AudioOutputProvider.FORMAT_UNSUPPORTED);
        assertTrue(directQueries.get() == 0);
    }

    private static AudioOutputProvider wrapped(
            ExoCompressedAudioDirectPolicy policy) {
        return policy.wrapOutputProvider(new UnsupportedAudioOutputProvider());
    }

    private static AudioOutputProvider.FormatConfig formatConfig(Format format) {
        return new AudioOutputProvider.FormatConfig.Builder(format)
                .setAudioAttributes(AudioAttributes.DEFAULT)
                .build();
    }

    private static AudioOutputProvider.FormatConfig tunnelingConfig() {
        return new AudioOutputProvider.FormatConfig.Builder(aacStereo())
                .setAudioAttributes(AudioAttributes.DEFAULT)
                .setAudioSessionId(1234)
                .setVirtualDeviceId(0)
                .setEnableTunneling(true)
                .build();
    }

    private static AudioOutputProvider.OutputConfig encodedOutput(boolean tunneling, boolean offload) {
        return new AudioOutputProvider.OutputConfig.Builder()
                .setEncoding(C.ENCODING_AAC_LC)
                .setSampleRate(48_000)
                .setChannelMask(Util.getAudioTrackChannelConfig(aacStereo()))
                .setBufferSize(4096)
                .setAudioSessionId(1234)
                .setIsTunneling(tunneling)
                .setIsOffload(offload)
                .build();
    }

    private static Format aacStereo() {
        return new Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setCodecs("mp4a.40.2")
                .setSampleRate(48_000)
                .setChannelCount(2)
                .build();
    }

    private static AudioOffloadSupport supported(
            boolean gapless, boolean speedChange) {
        return new AudioOffloadSupport.Builder()
                .setIsFormatSupported(true)
                .setIsGaplessSupported(gapless)
                .setIsSpeedChangeSupported(speedChange)
                .build();
    }

    private static class UnsupportedAudioOutputProvider
            implements AudioOutputProvider {

        @Override
        public FormatSupport getFormatSupport(FormatConfig config) {
            return FormatSupport.UNSUPPORTED;
        }

        @Override
        public OutputConfig getOutputConfig(FormatConfig config)
                throws ConfigurationException {
            throw new ConfigurationException("unsupported");
        }

        @Override
        public AudioOutput getAudioOutput(OutputConfig config)
                throws InitializationException {
            throw new InitializationException();
        }

        @Override
        public void addListener(Listener listener) {
        }

        @Override
        public void removeListener(Listener listener) {
        }

        @Override
        public void release() {
        }
    }

    private static final class FixedFormatSupportAudioOutputProvider
            extends UnsupportedAudioOutputProvider {

        private final FormatSupport formatSupport;

        FixedFormatSupportAudioOutputProvider(FormatSupport formatSupport) {
            this.formatSupport = formatSupport;
        }

        @Override
        public FormatSupport getFormatSupport(FormatConfig config) {
            return formatSupport;
        }
    }

    private static final class StandardAudioOutputProvider extends UnsupportedAudioOutputProvider {
        private final OutputConfig config;
        private final FormatSupport support = new FormatSupport.Builder()
                .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY).build();
        private int creations;

        StandardAudioOutputProvider(OutputConfig config) {
            this.config = config;
        }

        @Override public FormatSupport getFormatSupport(FormatConfig config) { return support; }

        @Override public OutputConfig getOutputConfig(FormatConfig config) { return this.config; }

        @Override public AudioOutput getAudioOutput(OutputConfig config) {
            assertSame(this.config, config);
            creations++;
            return (AudioOutput) Proxy.newProxyInstance(AudioOutput.class.getClassLoader(),
                    new Class<?>[]{AudioOutput.class}, (proxy, method, args) -> null);
        }
    }
}
