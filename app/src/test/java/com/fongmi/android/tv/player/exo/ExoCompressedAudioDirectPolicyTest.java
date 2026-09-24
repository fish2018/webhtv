package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.media.AudioManager;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.StuckPlayerException;
import androidx.media3.common.util.Util;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.analytics.PlayerId;

import org.junit.Test;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;

public class ExoCompressedAudioDirectPolicyTest {

    @Test
    public void firstPlayback_fullStartupBufferRecoversAt800MsOnlyOnce() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.write(4096, 10);
        assertNull(f.poll(0, true));
        assertNull(f.poll(799, true));
        assertSame(f.memory.format, f.poll(1, true));
        assertNull(f.poll(10_000, true));
        assertSame(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                f.memory.provider.getFormatSupport(formatConfig(f.memory.format)));
        assertTrue(f.memory.policy.consumePcmFallbackRequest());
        assertFalse(f.memory.policy.consumePcmFallbackRequest());
    }

    @Test
    public void startupWaitBeginsAtSufficientData_notFirstWrite() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.write(4095, 1);
        assertNull(f.poll(10_000, true));
        f.write(1, 1);
        assertNull(f.poll(0, true));
        assertNull(f.poll(799, true));
        assertSame(f.memory.format, f.poll(1, true));
    }

    @Test
    public void completeAccessUnitsLowerStartupThresholdWithoutShrinkingCapacity() throws Exception {
        StartupFixture f = new StartupFixture(256 * 1024);
        f.memory.environment.adjustThreshold = true;
        f.write(1024, 8); // 185 ms at 44.1 kHz: not enough yet.
        assertEquals(0, f.memory.environment.requestedThreshold);
        f.write(128, 1); // Nine complete AAC LC AUs cover 209 ms.
        assertEquals(1152, f.memory.environment.requestedThreshold);
        assertEquals(256 * 1024, f.memory.environment.buffer.capacityBytes());
        assertEquals(256 * 1024, f.memory.environment.buffer.sizeBytes());
        assertNull(f.poll(0, true));
        assertSame(f.memory.format, f.poll(800, true));
    }

    @Test
    public void clampedStartupThresholdNeedsMoreDataAndReadbackFailureKeepsOriginal() throws Exception {
        StartupFixture f = new StartupFixture(8192);
        f.memory.environment.adjustThreshold = true;
        f.memory.environment.minimumThreshold = 4096;
        f.write(1024, 10);
        assertNull(f.poll(2000, true));
        f.write(3072, 10);
        assertNull(f.poll(0, true));
        assertSame(f.memory.format, f.poll(800, true));

        StartupFixture unsupported = new StartupFixture(8192);
        unsupported.write(1024, 10);
        assertNull(unsupported.poll(10_000, true));
        assertFalse(unsupported.memory.policy.consumePcmFallbackRequest());
    }

    @Test
    public void rejectedAndPartialWritesDoNotCountWholeAccessUnits() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.memory.environment.adjustThreshold = true;
        f.memory.directRaw.acceptWrites = false;
        f.write(4096, 10);
        assertNull(f.poll(10_000, true));
        assertEquals(0, f.memory.environment.requestedThreshold);
        f.memory.directRaw.acceptWrites = true;
        f.memory.directRaw.maxWriteBytes = 512;
        ByteBuffer buffer = ByteBuffer.allocateDirect(1024);
        assertFalse(f.output.write(buffer, 10, 0));
        assertEquals(0, f.memory.environment.requestedThreshold);
        assertTrue(f.output.write(buffer, 10, 0));
        assertEquals(1024, f.memory.environment.requestedThreshold);
    }

    @Test
    public void unknownThresholdOrRawHeadDoesNotDeclareStartupFailure() throws Exception {
        StartupFixture f = new StartupFixture(0);
        f.write(256 * 1024, 32);
        assertNull(f.poll(0, true));
        assertNull(f.poll(10_000, true));
        StartupFixture unknownHead = new StartupFixture(4096);
        unknownHead.memory.environment.rawHead = C.LENGTH_UNSET;
        unknownHead.write(4096, 10);
        assertNull(unknownHead.poll(0, true));
        assertNull(unknownHead.poll(800, true));
    }

    @Test
    public void validPositionOrRawHeadRetiresStartupProbe() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.write(4096, 10);
        assertNull(f.poll(0, true));
        f.memory.directRaw.positionUs = 1;
        assertNull(f.poll(800, true));
        assertEquals(Long.MAX_VALUE, f.memory.policy.startupProgressIntervalUs());
        f.memory.directRaw.positionUs = 0;
        assertNull(f.poll(10_000, true));

        StartupFixture timestampLag = new StartupFixture(4096);
        timestampLag.write(4096, 10);
        assertNull(timestampLag.poll(0, true));
        timestampLag.memory.environment.rawHead = 128;
        assertNull(timestampLag.poll(800, true));
        assertEquals(Long.MAX_VALUE, timestampLag.memory.policy.startupProgressIntervalUs());
    }

    @Test
    public void pauseAndBufferingRestartObservationWithoutCountingSuspendedTime() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.write(4096, 10);
        assertNull(f.poll(0, true));
        f.output.pause();
        assertNull(f.poll(5000, false));
        f.output.play();
        assertNull(f.poll(0, true));
        assertNull(f.poll(799, true));
        assertNull(f.poll(1, false));
        assertNull(f.poll(0, true));
        assertSame(f.memory.format, f.poll(800, true));
    }

    @Test
    public void flushStopReleaseAndNewAttemptCannotUseOldStartupEvidence() throws Exception {
        for (String operation : new String[]{"flush", "stop", "release", "prepare"}) {
            StartupFixture f = new StartupFixture(4096);
            f.write(4096, 10);
            assertNull(f.poll(0, true));
            switch (operation) {
                case "flush" -> f.output.flush();
                case "stop" -> f.output.stop();
                case "release" -> f.output.release();
                case "prepare" -> f.memory.policy.prepareForPlayback("new", false);
            }
            f.memory.nowMs.addAndGet(800);
            assertNull(operation, f.memory.policy.maybeRequestStartupPcmFallback(true));
        }
    }

    @Test
    public void routeOrCapabilityChangeInvalidatesStartupEvidence() throws Exception {
        StartupFixture routeChange = new StartupFixture(4096);
        routeChange.write(4096, 10);
        assertNull(routeChange.poll(0, true));
        routeChange.memory.environment.actual = route(2);
        assertNull(routeChange.poll(800, true));
        StartupFixture capabilities = new StartupFixture(4096);
        capabilities.write(4096, 10);
        assertNull(capabilities.poll(0, true));
        capabilities.memory.memory.invalidate();
        assertNull(capabilities.poll(800, true));
    }

    @Test
    public void growingRealThresholdIsRecheckedBeforeRecovery() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.write(4096, 10);
        assertNull(f.poll(0, true));
        f.memory.environment.buffer = new ExoCompressedAudioDirectPolicy.StartupBuffer(8192, 8192, 8192);
        assertNull(f.poll(800, true));
        f.write(4096, 10);
        assertNull(f.poll(0, true));
        assertSame(f.memory.format, f.poll(800, true));
    }

    @Test
    public void internalPcmRecoveryConfirmsWithoutNewMediaPreparation() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.memory.policy.setSelectedAudioFormat(f.memory.format);
        f.write(4096, 10);
        assertNull(f.poll(0, true));
        assertSame(f.memory.format, f.poll(800, true));
        f.output.release();
        f.memory.createPcm(); // No prepareForPlayback, as in Media3's internal recovery.
        f.memory.advancePcm();
        assertFalse(f.memory.policy.consumePcmFallbackRequest());
        assertFalse(f.memory.freshEngineUsesDirect(f.memory.url, f.memory.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void earlyFailureIsNotSharedBeforePcmProgressAndRetiredAttemptCannotDisableNewOutput() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        f.write(4096, 10);
        assertNull(f.poll(0, true));
        f.memory.environment.onExpectedRoute = () -> f.memory.policy.prepareForPlayback("new", false);
        assertNull(f.poll(800, true));
        f.memory.environment.onExpectedRoute = null;
        assertFalse(f.memory.policy.consumePcmFallbackRequest());
        assertTrue(f.memory.freshEngineUsesDirect(f.memory.url, f.memory.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void rendererEmitsTypedRecoverableErrorAndPreservesOriginalRenderCall() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        AtomicInteger renders = new AtomicInteger();
        Renderer delegate = renderer(Renderer.STATE_STARTED, renders);
        Renderer wrapped = new ExoStartupAudioRenderer(delegate, f.memory.policy);
        wrapped.init(3, PlayerId.UNSET, Clock.DEFAULT);
        f.write(4096, 10);
        f.output.getPositionUs();
        wrapped.render(0, 0);
        assertEquals(50_000, wrapped.getDurationToProgressUs(0, 0));
        f.memory.nowMs.set(800);
        f.output.getPositionUs();
        ExoPlaybackException error = assertThrows(ExoPlaybackException.class, () -> wrapped.render(0, 800_000));
        assertTrue(isRecoverable(error));
        assertEquals(ExoPlaybackException.TYPE_RENDERER, error.type);
        assertEquals(PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED, error.errorCode);
        assertTrue(error.getCause() instanceof ExoStartupAudioRenderer.StartupStallException);
        assertEquals(3, error.rendererIndex);
        assertSame(f.memory.format, error.rendererFormat);
        wrapped.render(0, 900_000);
        assertEquals(3, renders.get());
        assertEquals(Long.MAX_VALUE, wrapped.getDurationToProgressUs(0, 0));
    }

    @Test
    public void rendererDoesNotEmitStartupErrorWhileEnabledButNotPlaying() throws Exception {
        StartupFixture f = new StartupFixture(4096);
        Renderer wrapped = new ExoStartupAudioRenderer(renderer(Renderer.STATE_ENABLED, new AtomicInteger()),
                f.memory.policy);
        f.write(4096, 10);
        f.output.getPositionUs();
        wrapped.render(0, 0);
        f.memory.nowMs.set(10_000);
        f.output.getPositionUs();
        wrapped.render(0, 10_000_000);
        assertFalse(f.memory.policy.consumePcmFallbackRequest());
    }

    private static Renderer renderer(int state, AtomicInteger renders) {
        return (Renderer) Proxy.newProxyInstance(Renderer.class.getClassLoader(), new Class<?>[]{Renderer.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "render" -> { renders.incrementAndGet(); yield null; }
                    case "getState" -> state;
                    case "isReady" -> true;
                    case "isEnded" -> false;
                    case "getName" -> "original-audio-renderer";
                    case "getDurationToProgressUs" -> Long.MAX_VALUE;
                    default -> null;
                });
    }

    @Test
    public void passthroughDisabled_doesNotProbeOrCreateVendorOutput() {
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> { throw new AssertionError("Offload query while disabled"); },
                (format, attributes) -> { throw new AssertionError("Direct query while disabled"); },
                Clock.DEFAULT,
                config -> { throw new AssertionError("Vendor output while disabled"); });
        policy.setAudioPassthroughEnabled(false);

        assertSame(AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                policy.getAudioOffloadSupport(aacStereo(), AudioAttributes.DEFAULT));
        AudioOutputProvider provider = wrapped(policy);
        assertSame(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                provider.getFormatSupport(formatConfig(aacStereo())));
        assertThrows(AudioOutputProvider.ConfigurationException.class,
                () -> provider.getOutputConfig(formatConfig(aacStereo())));
        assertThrows(AudioOutputProvider.InitializationException.class,
                () -> provider.getAudioOutput(encodedOutput(false, false)));
        assertFalse(policy.getAudioOutputSnapshot().initialized());
        assertFalse(policy.consumePcmFallbackRequest());
    }

    @Test
    public void passthroughDisabled_overridesStandardEncodedSupportIncludingOffload() {
        AudioOutputProvider.FormatSupport standard = new AudioOutputProvider.FormatSupport.Builder()
                .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                .setIsFormatSupportedForOffload(true).build();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> supported(true, true), (format, attributes) -> true);
        policy.setAudioPassthroughEnabled(false);
        AudioOutputProvider provider = policy.wrapOutputProvider(
                new FixedFormatSupportAudioOutputProvider(standard));

        for (String mime : new String[]{MimeTypes.AUDIO_AAC, MimeTypes.AUDIO_MPEG,
                MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_DTS,
                MimeTypes.AUDIO_TRUEHD}) {
            Format format = aacStereo().buildUpon().setSampleMimeType(mime).build();
            assertSame(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                    provider.getFormatSupport(formatConfig(format)));
        }
        assertSame(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                provider.getFormatSupport(tunnelingConfig()));
        assertFalse(policy.getAudioOffloadSupport(aacStereo(), AudioAttributes.DEFAULT).isFormatSupported);
    }

    @Test
    public void passthroughDisabled_rejectsPreviouslySelectedEncodedConfigs() throws Exception {
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> true);
        AudioOutputProvider vendor = wrapped(policy);
        vendor.getFormatSupport(formatConfig(aacStereo()));
        AudioOutputProvider.OutputConfig cached = vendor.getOutputConfig(formatConfig(aacStereo()));
        policy.setAudioPassthroughEnabled(false);

        assertFalse(policy.usesVendorDirect(C.ENCODING_AAC_LC, cached.sampleRate, cached.channelMask));
        assertThrows(AudioOutputProvider.ConfigurationException.class,
                () -> vendor.getOutputConfig(formatConfig(aacStereo())));
        for (AudioOutputProvider.OutputConfig config : new AudioOutputProvider.OutputConfig[]{
                cached, encodedOutput(false, true), encodedOutput(true, false)}) {
            StandardAudioOutputProvider delegate = new StandardAudioOutputProvider(config);
            assertThrows(AudioOutputProvider.InitializationException.class,
                    () -> policy.wrapOutputProvider(delegate).getAudioOutput(config));
            assertEquals(0, delegate.creations);
        }
    }

    @Test
    public void passthroughDisabled_preservesPcmAndTunneledPcmOutputOwnership() throws Exception {
        for (boolean tunneling : new boolean[]{false, true}) {
            ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                    (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                    (format, attributes) -> { throw new AssertionError("PCM direct probe"); });
            policy.setAudioPassthroughEnabled(false);
            Format pcm = aacStereo().buildUpon().setSampleMimeType(MimeTypes.AUDIO_RAW)
                    .setPcmEncoding(C.ENCODING_PCM_16BIT).build();
            AudioOutputProvider.FormatConfig format = new AudioOutputProvider.FormatConfig.Builder(pcm)
                    .setEnableTunneling(tunneling).build();
            AudioOutputProvider.OutputConfig config = new AudioOutputProvider.OutputConfig.Builder()
                    .setEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48_000)
                    .setChannelMask(Util.getAudioTrackChannelConfig(pcm))
                    .setBufferSize(4096).setIsTunneling(tunneling).build();
            StandardAudioOutputProvider delegate = new StandardAudioOutputProvider(config);
            AudioOutputProvider provider = policy.wrapOutputProvider(delegate);

            assertSame(delegate.support, provider.getFormatSupport(format));
            assertSame(config, provider.getOutputConfig(format));
            AudioOutput output = provider.getAudioOutput(config);
            assertEquals(1, delegate.creations);
            assertTrue(policy.getAudioOutputSnapshot().initialized());
            assertEquals(C.ENCODING_PCM_16BIT, policy.getAudioOutputSnapshot().encoding());
            assertEquals(tunneling, policy.getAudioOutputSnapshot().tunneling());
            assertFalse(policy.getAudioOutputSnapshot().offload());
            output.release();
            assertFalse(policy.getAudioOutputSnapshot().initialized());
        }
    }

    @Test
    public void passthroughReenabled_restoresDirectAndStandardOffloadCandidates() {
        AudioOffloadSupport offload = supported(true, true);
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> offload, (format, attributes) -> true);
        policy.setAudioPassthroughEnabled(false);
        assertSame(AudioOutputProvider.FormatSupport.UNSUPPORTED,
                wrapped(policy).getFormatSupport(formatConfig(aacStereo())));

        policy.setAudioPassthroughEnabled(true);
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                wrapped(policy).getFormatSupport(formatConfig(aacStereo())).supportLevel);
        assertSame(offload, policy.getAudioOffloadSupport(aacStereo(), AudioAttributes.DEFAULT));
    }

    @Test
    public void offloadOnlyFlag_doesNotAuthorizeNonOffloadedBitstream() {
        assertFalse(ExoCompressedAudioDirectPolicy.supportsBitstream(0));
        assertFalse(ExoCompressedAudioDirectPolicy.supportsBitstream(
                AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED));
        assertFalse(ExoCompressedAudioDirectPolicy.supportsBitstream(
                AudioManager.DIRECT_PLAYBACK_OFFLOAD_GAPLESS_SUPPORTED));
        assertTrue(ExoCompressedAudioDirectPolicy.supportsBitstream(
                AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED));
        assertTrue(ExoCompressedAudioDirectPolicy.supportsBitstream(
                AudioManager.DIRECT_PLAYBACK_BITSTREAM_SUPPORTED | AudioManager.DIRECT_PLAYBACK_OFFLOAD_SUPPORTED));
    }

    @Test
    public void capabilityQueryAndOutput_useTheSameEffectiveAttributes() throws Exception {
        AtomicReference<AudioAttributes> queried = new AtomicReference<>();
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> { queried.set(attributes); return true; });
        AudioAttributes original = AudioAttributes.DEFAULT.buildUpon()
                .setHapticChannelsMuted(false).setIsContentSpatialized(true).build();
        AudioOutputProvider.FormatConfig config = new AudioOutputProvider.FormatConfig.Builder(aacStereo())
                .setAudioAttributes(original).build();
        AudioOutputProvider provider = wrapped(policy);
        provider.getFormatSupport(config);
        AudioOutputProvider.OutputConfig output = provider.getOutputConfig(config);
        assertEquals(C.AUDIO_CONTENT_TYPE_MUSIC, queried.get().contentType);
        assertEquals(queried.get(), output.audioAttributes);
        assertEquals(original.usage, output.audioAttributes.usage);
        assertEquals(original.flags, output.audioAttributes.flags);
        assertEquals(original.hapticChannelsMuted, output.audioAttributes.hapticChannelsMuted);
        assertEquals(original.isContentSpatialized, output.audioAttributes.isContentSpatialized);
        assertEquals(C.AUDIO_CONTENT_TYPE_UNKNOWN, original.contentType);
    }

    @Test
    public void cachedDirectCapability_doesNotCrossAudioAttributes() {
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> true);
        AudioOutputProvider provider = wrapped(policy);
        provider.getFormatSupport(formatConfig(aacStereo()));
        AudioOutputProvider.FormatConfig speech = new AudioOutputProvider.FormatConfig.Builder(aacStereo())
                .setAudioAttributes(AudioAttributes.DEFAULT.buildUpon()
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()).build();
        assertThrows(AudioOutputProvider.ConfigurationException.class,
                () -> provider.getOutputConfig(speech));
    }

    @Test
    public void startupThreshold_preservesCompressedByteUnitsAndUnknownState() {
        assertEquals(256 * 1024, new ExoCompressedAudioDirectPolicy.StartupBuffer(
                512 * 1024, 256 * 1024, 512 * 1024).effectiveThresholdBytes());
        assertEquals(4096, new ExoCompressedAudioDirectPolicy.StartupBuffer(
                256 * 1024, 256 * 1024, 4096).effectiveThresholdBytes());
        assertEquals(0, ExoCompressedAudioDirectPolicy.StartupBuffer.UNKNOWN.effectiveThresholdBytes());
        assertEquals(0, new ExoCompressedAudioDirectPolicy.StartupBuffer(4096, 4096, 8192)
                .effectiveThresholdBytes());
    }

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

    @Test
    public void acceptedDirectAudio_stuckPlaying_requestsOnePcmRetry() throws Exception {
        for (int sampleRate : new int[]{44_100, 48_000}) {
            DirectOutputFixture fixture = new DirectOutputFixture(sampleRate);
            fixture.stall();

            assertTrue(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
            assertTrue(fixture.policy.consumePcmFallbackRequest());
            assertFalse(fixture.policy.consumePcmFallbackRequest());
            assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
            // Even a delegate advertising generic passthrough must now force decoder + PCM.
            assertEquals(AudioOutputProvider.FORMAT_UNSUPPORTED,
                    fixture.provider.getFormatSupport(formatConfig(fixture.format)).supportLevel);
            assertFalse(fixture.policy.usesVendorDirect(C.ENCODING_AAC_LC, sampleRate,
                    Util.getAudioTrackChannelConfig(fixture.format)));
        }
    }

    @Test
    public void otherTimeoutsAndErrors_doNotDisableStalledDirectOutput() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.stall();
        for (int type : new int[]{StuckPlayerException.STUCK_BUFFERING_NOT_LOADING,
                StuckPlayerException.STUCK_BUFFERING_NO_PROGRESS,
                StuckPlayerException.STUCK_PLAYING_NOT_ENDING,
                StuckPlayerException.STUCK_SUPPRESSED}) {
            assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuck(type)));
        }
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(
                new PlaybackException("timeout", new IllegalStateException(),
                        PlaybackException.ERROR_CODE_TIMEOUT)));
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(
                new PlaybackException("I/O", new StuckPlayerException(
                        StuckPlayerException.STUCK_PLAYING_NO_PROGRESS, 10_000),
                        PlaybackException.ERROR_CODE_IO_UNSPECIFIED)));
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(null));
        assertFalse(fixture.policy.consumePcmFallbackRequest());
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                fixture.provider.getFormatSupport(formatConfig(fixture.format)).supportLevel);
    }

    @Test
    public void recentAudioProgress_doesNotBlameDirectOutput() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.stall();
        fixture.raw.positionUs = 4_000_000;
        assertEquals(4_000_000, fixture.output.getPositionUs());

        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
        assertFalse(fixture.policy.consumePcmFallbackRequest());
    }

    @Test
    public void outputStallsAfterProgress_canRecoverAtNonzeroPosition() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.writeAndPlay();
        fixture.output.getPositionUs();
        fixture.nowMs.addAndGet(5_000);
        fixture.raw.positionUs = 5_000_000;
        fixture.output.getPositionUs();
        fixture.nowMs.addAndGet(10_000);
        fixture.output.getPositionUs();

        assertTrue(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
    }

    @Test
    public void zeroWritesOrUnknownPosition_doNotProveOutputStall() throws Exception {
        DirectOutputFixture noData = new DirectOutputFixture(44_100);
        noData.raw.acceptWrites = false;
        noData.stall();
        assertFalse(noData.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));

        DirectOutputFixture unknown = new DirectOutputFixture(44_100);
        unknown.raw.positionUs = C.TIME_UNSET;
        unknown.stall();
        assertFalse(unknown.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
    }

    @Test
    public void pauseAndResume_restartObservationWindow() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.writeAndPlay();
        fixture.output.getPositionUs();
        fixture.output.pause();
        fixture.nowMs.addAndGet(20_000);
        fixture.output.getPositionUs();
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));

        fixture.output.play();
        fixture.output.getPositionUs();
        fixture.nowMs.addAndGet(1_000);
        fixture.output.getPositionUs();
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
        fixture.nowMs.addAndGet(1_000);
        fixture.output.getPositionUs();
        assertTrue(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
    }

    @Test
    public void flush_requiresFreshInputAndProgressEvidence() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.stall();
        fixture.output.flush();
        fixture.nowMs.addAndGet(10_000);
        fixture.output.getPositionUs();
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));

        fixture.stall();
        assertTrue(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
    }

    @Test
    public void naturalStop_discardsStallEvidence() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.stall();
        fixture.output.stop();
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
    }

    @Test
    public void pauseAndReleaseBeforeError_preserveObservedEvidence() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.stall();
        fixture.output.pause();
        fixture.output.release();

        assertFalse(fixture.policy.getAudioOutputSnapshot().initialized());
        assertTrue(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
        assertTrue(fixture.policy.consumePcmFallbackRequest());
    }

    @Test
    public void oldRelease_doesNotContaminateNewOutput() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.stall();
        AudioOutput old = fixture.output;
        fixture.createOutput();
        old.release();
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));

        fixture.stall();
        assertTrue(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
    }

    @Test
    public void newPlaybackAttempt_rejectsOldAndLateInitializationEvidence() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.stall();
        fixture.policy.resetOutputProgress();
        fixture.output.getPositionUs();
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));

        fixture.duringCreation = fixture.policy::resetOutputProgress;
        fixture.createOutput();
        fixture.stall();
        assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));

        fixture.duringCreation = null;
        fixture.createOutput();
        fixture.stall();
        assertTrue(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
    }

    @Test
    public void standardPcmOffloadAndTunneling_replaceVendorEvidence() throws Exception {
        AudioOutputProvider.OutputConfig pcm = new AudioOutputProvider.OutputConfig.Builder()
                .setEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44_100)
                .setChannelMask(12).setBufferSize(4096).build();
        for (AudioOutputProvider.OutputConfig config : new AudioOutputProvider.OutputConfig[]{
                pcm, encodedOutput(false, true), encodedOutput(true, false)}) {
            DirectOutputFixture fixture = new DirectOutputFixture(44_100);
            fixture.stall();
            AudioOutput standard = fixture.policy.wrapOutputProvider(
                    new StandardAudioOutputProvider(config)).getAudioOutput(config);

            assertFalse(fixture.policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
            assertFalse(fixture.policy.consumePcmFallbackRequest());
            standard.release();
        }
    }

    @Test
    public void writeFailure_stillRequestsRecoverablePcmFallback() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.raw.writeFailure = new AudioOutput.WriteException(-6, false);
        AudioOutput.WriteException error = assertThrows(AudioOutput.WriteException.class,
                fixture::writeAndPlay);

        assertEquals(-6, error.errorCode);
        assertTrue(error.isRecoverable);
        assertTrue(fixture.policy.consumePcmFallbackRequest());
        assertEquals(AudioOutputProvider.FORMAT_UNSUPPORTED,
                fixture.provider.getFormatSupport(formatConfig(fixture.format)).supportLevel);
    }

    private static boolean isRecoverable(ExoPlaybackException error) throws ReflectiveOperationException {
        Field field = ExoPlaybackException.class.getDeclaredField("isRecoverable");
        field.setAccessible(true);
        return field.getBoolean(error);
    }

    @Test
    public void failedDirect_thenStableSameTrackPcm_isRememberedAcrossEngines() throws Exception {
        MemoryFixture fixture = new MemoryFixture();
        fixture.failDirect();
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
        fixture.preparePcm(true);
        fixture.advancePcm();
        assertFalse(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
        assertTrue(fixture.freshEngineUsesDirect(fixture.url + "&token=other", fixture.format,
                AudioAttributes.DEFAULT));
        assertTrue(fixture.freshEngineUsesDirect(fixture.url,
                fixture.format.buildUpon().setId("different-track").build(), AudioAttributes.DEFAULT));
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format,
                AudioAttributes.DEFAULT.buildUpon().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build()));
        fixture.environment.expected = route(2);
        fixture.environment.actual = route(2);
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void pcmWithoutProgress_doesNotConfirmDirectFailure() throws Exception {
        MemoryFixture fixture = new MemoryFixture();
        fixture.failDirect();
        fixture.preparePcm(true);
        fixture.pcm.play();
        fixture.pcm.write(ByteBuffer.allocateDirect(32), 1, 0);
        fixture.pcm.getPositionUs();
        fixture.nowMs.addAndGet(20_000);
        fixture.pcm.getPositionUs();
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void unrelatedRestartOrNewMedia_doesNotCarryPendingConfirmation() throws Exception {
        for (boolean changeMedia : new boolean[]{false, true}) {
            MemoryFixture fixture = new MemoryFixture();
            fixture.failDirect();
            if (changeMedia) fixture.url += "&episode=next";
            fixture.preparePcm(changeMedia);
            fixture.advancePcm();
            assertTrue(fixture.freshEngineUsesDirect(MemoryFixture.URL, fixture.format, AudioAttributes.DEFAULT));
        }
    }

    @Test
    public void differentSelectedTrackOrActualRoute_doesNotConfirm() throws Exception {
        for (boolean changeTrack : new boolean[]{false, true}) {
            MemoryFixture fixture = new MemoryFixture();
            fixture.failDirect();
            fixture.preparePcm(true);
            if (changeTrack) {
                fixture.policy.setSelectedAudioFormat(fixture.format.buildUpon().setId("other").build());
            } else {
                fixture.environment.actual = route(2);
            }
            fixture.advancePcm();
            assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
        }
    }

    @Test
    public void unknownRoute_doesNotShareFailureButStillRecoversLocally() throws Exception {
        MemoryFixture fixture = new MemoryFixture();
        fixture.environment.expected = null;
        fixture.failDirect();
        fixture.preparePcm(true);
        fixture.advancePcm();
        fixture.environment.expected = route(1);
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void flushStopReleaseAndNewAttempt_cancelPendingPcmConfirmation() throws Exception {
        for (int action = 0; action < 4; action++) {
            MemoryFixture fixture = new MemoryFixture();
            fixture.failDirect();
            fixture.preparePcm(true);
            switch (action) {
                case 0 -> fixture.pcm.flush();
                case 1 -> fixture.pcm.stop();
                case 2 -> fixture.pcm.release();
                case 3 -> fixture.policy.resetOutputProgress();
            }
            if (action != 2) fixture.advancePcm();
            assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
        }
    }

    @Test
    public void pausedPcm_doesNotConfirmUntilFreshContinuousProgress() throws Exception {
        MemoryFixture fixture = new MemoryFixture();
        fixture.failDirect();
        fixture.preparePcm(true);
        fixture.pcm.write(ByteBuffer.allocateDirect(32), 1, 0);
        fixture.pcm.play();
        fixture.pcm.getPositionUs();
        fixture.pcm.pause();
        fixture.nowMs.addAndGet(20_000);
        fixture.pcmRaw.positionUs = 2_000_000;
        fixture.pcm.getPositionUs();
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
        fixture.pcmRaw.positionUs = 0;
        fixture.advancePcm();
        assertFalse(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void capabilityNotification_invalidatesConfirmedAndPendingFailures() throws Exception {
        MemoryFixture fixture = new MemoryFixture();
        AtomicReference<AudioOutputProvider.Listener> observer = new AtomicReference<>();
        AudioOutputProvider watched = fixture.policy.wrapOutputProvider(new UnsupportedAudioOutputProvider() {
            @Override public void addListener(Listener listener) { observer.compareAndSet(null, listener); }
        });
        watched.addListener(() -> {});
        fixture.failDirect();
        fixture.preparePcm(true);
        observer.get().onFormatSupportChanged();
        fixture.advancePcm();
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));

        MemoryFixture confirmed = new MemoryFixture();
        confirmed.failDirect();
        confirmed.preparePcm(true);
        confirmed.advancePcm();
        confirmed.memory.invalidate();
        assertTrue(confirmed.freshEngineUsesDirect(confirmed.url, confirmed.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void staleWriteFailure_cannotRequestPcmForNewPlayback() throws Exception {
        DirectOutputFixture fixture = new DirectOutputFixture(44_100);
        fixture.policy.resetOutputProgress();
        fixture.raw.writeFailure = new AudioOutput.WriteException(-6, false);
        assertThrows(AudioOutput.WriteException.class, fixture::writeAndPlay);
        assertFalse(fixture.policy.consumePcmFallbackRequest());
        assertEquals(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY,
                fixture.provider.getFormatSupport(formatConfig(fixture.format)).supportLevel);
    }

    @Test
    public void newAttemptDuringFinalRouteQuery_rejectsLatePcmConfirmation() throws Exception {
        MemoryFixture fixture = new MemoryFixture();
        fixture.failDirect();
        fixture.preparePcm(true);
        fixture.environment.onExpectedRoute = fixture.policy::resetOutputProgress;
        fixture.advancePcm();
        fixture.environment.onExpectedRoute = null;
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
    }

    @Test
    public void replacedPcmOutput_cannotConfirmTheRetiredOutput() throws Exception {
        MemoryFixture fixture = new MemoryFixture();
        fixture.failDirect();
        fixture.preparePcm(true);
        AudioOutputProvider.OutputConfig config = new AudioOutputProvider.OutputConfig.Builder()
                .setEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44_100)
                .setChannelMask(12).setBufferSize(4096).build();
        fixture.policy.wrapOutputProvider(new StandardAudioOutputProvider(config, new FakeAudioOutput().output))
                .getAudioOutput(config);
        fixture.advancePcm();
        assertTrue(fixture.freshEngineUsesDirect(fixture.url, fixture.format, AudioAttributes.DEFAULT));
    }

    private static ExoAudioDirectFailureMemory.Route route(int id) {
        return new ExoAudioDirectFailureMemory.Route(id, 2, 3, 4, 5);
    }

    private static final class TestEnvironment implements ExoCompressedAudioDirectPolicy.OutputEnvironment {
        ExoAudioDirectFailureMemory.Route expected = route(1);
        ExoAudioDirectFailureMemory.Route actual = route(1);
        Runnable onExpectedRoute;
        ExoCompressedAudioDirectPolicy.StartupBuffer buffer = ExoCompressedAudioDirectPolicy.StartupBuffer.UNKNOWN;
        boolean adjustThreshold;
        int requestedThreshold;
        int minimumThreshold;
        long rawHead;
        @Override public ExoAudioDirectFailureMemory.Route expectedRoute(AudioAttributes attributes) {
            if (onExpectedRoute != null) onExpectedRoute.run();
            return expected;
        }
        @Override public ExoAudioDirectFailureMemory.Route actualRoute(AudioOutput output) { return actual; }
        @Override public ExoCompressedAudioDirectPolicy.StartupBuffer startupBuffer(AudioOutput output) { return buffer; }
        @Override public long rawPlaybackHead(AudioOutput output) { return rawHead; }
        @Override public ExoCompressedAudioDirectPolicy.StartupBuffer setStartThresholdBytes(AudioOutput output, int bytes) {
            if (!adjustThreshold) return ExoCompressedAudioDirectPolicy.StartupBuffer.UNKNOWN;
            requestedThreshold = bytes;
            buffer = new ExoCompressedAudioDirectPolicy.StartupBuffer(buffer.capacityBytes(),
                    buffer.sizeBytes(), Math.max(bytes, minimumThreshold));
            return buffer;
        }
    }

    private static final class StartupFixture {
        final MemoryFixture memory = new MemoryFixture();
        final AudioOutput output;
        StartupFixture(int threshold) throws Exception {
            memory.environment.buffer = new ExoCompressedAudioDirectPolicy.StartupBuffer(
                    threshold, threshold, threshold);
            memory.policy.prepareForPlayback(memory.url, false);
            memory.provider.getFormatSupport(formatConfig(memory.format));
            output = memory.provider.getAudioOutput(memory.provider.getOutputConfig(formatConfig(memory.format)));
            output.play();
        }
        void write(int bytes, int units) throws Exception { output.write(ByteBuffer.allocateDirect(bytes), units, 0); }
        Format poll(long elapsedMs, boolean playing) {
            memory.nowMs.addAndGet(elapsedMs);
            output.getPositionUs();
            return memory.policy.maybeRequestStartupPcmFallback(playing);
        }
    }

    private static final class MemoryFixture {
        static final String URL = "https://example.invalid/video?token=private";
        final ExoAudioDirectFailureMemory memory = new ExoAudioDirectFailureMemory();
        final TestEnvironment environment = new TestEnvironment();
        final AtomicLong nowMs = new AtomicLong();
        final Format format = aacStereo().buildUpon().setSampleRate(44_100).build();
        final FakeAudioOutput directRaw = new FakeAudioOutput();
        final ExoCompressedAudioDirectPolicy policy = newPolicy();
        final AudioOutputProvider provider = wrapped(policy);
        String url = URL;
        AudioOutput pcm;
        FakeAudioOutput pcmRaw;

        private ExoCompressedAudioDirectPolicy newPolicy() {
            Clock clock = (Clock) Proxy.newProxyInstance(Clock.class.getClassLoader(),
                    new Class<?>[]{Clock.class}, (proxy, method, args) -> {
                        if (method.getName().equals("elapsedRealtime")) return nowMs.get();
                        throw new AssertionError("Unexpected Clock call: " + method.getName());
                    });
            return new ExoCompressedAudioDirectPolicy(
                    (ignored, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                    (ignored, attributes) -> true, clock, config -> directRaw.output, memory, environment);
        }

        void failDirect() throws Exception {
            policy.prepareForPlayback(url, false);
            provider.getFormatSupport(formatConfig(format));
            AudioOutput output = provider.getAudioOutput(provider.getOutputConfig(formatConfig(format)));
            output.write(ByteBuffer.allocateDirect(32), 1, 0);
            output.play();
            output.getPositionUs();
            nowMs.addAndGet(10_000);
            output.getPositionUs();
            assertTrue(policy.requestPcmFallbackForStuckPlayback(stuckPlaying()));
            assertTrue(policy.consumePcmFallbackRequest());
            output.release();
        }

        void preparePcm(boolean retry) throws Exception {
            policy.prepareForPlayback(url, retry);
            policy.setSelectedAudioFormat(format);
            createPcm();
        }

        void createPcm() throws Exception {
            pcmRaw = new FakeAudioOutput();
            AudioOutputProvider.OutputConfig config = new AudioOutputProvider.OutputConfig.Builder()
                    .setEncoding(C.ENCODING_PCM_16BIT).setSampleRate(44_100)
                    .setChannelMask(12).setBufferSize(4096).build();
            pcm = policy.wrapOutputProvider(new StandardAudioOutputProvider(config, pcmRaw.output))
                    .getAudioOutput(config);
        }

        void advancePcm() throws Exception {
            pcm.write(ByteBuffer.allocateDirect(32), 1, 0);
            pcm.play();
            pcm.getPositionUs();
            for (int i = 1; i <= 2; i++) {
                nowMs.addAndGet(1_000);
                pcmRaw.positionUs = i * 1_000_000L;
                pcm.getPositionUs();
            }
        }

        boolean freshEngineUsesDirect(String media, Format input, AudioAttributes attributes) {
            ExoCompressedAudioDirectPolicy fresh = newPolicy();
            fresh.prepareForPlayback(media, false);
            return wrapped(fresh).getFormatSupport(new AudioOutputProvider.FormatConfig.Builder(input)
                    .setAudioAttributes(attributes).build()).supportLevel
                    == AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY;
        }
    }

    private static PlaybackException stuckPlaying() {
        return stuck(StuckPlayerException.STUCK_PLAYING_NO_PROGRESS);
    }

    private static PlaybackException stuck(int type) {
        return new PlaybackException("stuck", new StuckPlayerException(type, 10_000),
                PlaybackException.ERROR_CODE_TIMEOUT);
    }

    private static final class DirectOutputFixture {
        final AtomicLong nowMs = new AtomicLong();
        final Format format;
        final ExoCompressedAudioDirectPolicy policy;
        final AudioOutputProvider provider;
        AudioOutput output;
        FakeAudioOutput raw;
        Runnable duringCreation;

        DirectOutputFixture(int sampleRate) throws Exception {
            format = aacStereo().buildUpon().setSampleRate(sampleRate).build();
            Clock clock = (Clock) Proxy.newProxyInstance(Clock.class.getClassLoader(),
                    new Class<?>[]{Clock.class}, (proxy, method, args) -> {
                        if (method.getName().equals("elapsedRealtime")) return nowMs.get();
                        throw new AssertionError("Unexpected Clock call: " + method.getName());
                    });
            policy = new ExoCompressedAudioDirectPolicy(
                    (ignoredFormat, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                    (ignoredFormat, attributes) -> true, clock, config -> {
                        if (duringCreation != null) duringCreation.run();
                        return raw.output;
                    });
            provider = policy.wrapOutputProvider(new FixedFormatSupportAudioOutputProvider(
                    new AudioOutputProvider.FormatSupport.Builder()
                            .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY)
                            .build()));
            provider.getFormatSupport(formatConfig(format));
            createOutput();
        }

        void createOutput() throws Exception {
            raw = new FakeAudioOutput();
            output = provider.getAudioOutput(provider.getOutputConfig(formatConfig(format)));
        }

        void writeAndPlay() throws AudioOutput.WriteException {
            ByteBuffer buffer = ByteBuffer.allocateDirect(32);
            output.write(buffer, 1, 1_027_599_000L);
            output.play();
        }

        void stall() throws AudioOutput.WriteException {
            writeAndPlay();
            output.getPositionUs();
            nowMs.addAndGet(10_000);
            output.getPositionUs();
        }
    }

    private static final class FakeAudioOutput {
        long positionUs;
        boolean acceptWrites = true;
        int maxWriteBytes = Integer.MAX_VALUE;
        boolean released;
        AudioOutput.WriteException writeFailure;
        final AudioOutput output = (AudioOutput) Proxy.newProxyInstance(
                AudioOutput.class.getClassLoader(), new Class<?>[]{AudioOutput.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getPositionUs":
                            if (released) throw new AssertionError("Queried released output");
                            return positionUs;
                        case "write":
                            if (writeFailure != null) throw writeFailure;
                            ByteBuffer buffer = (ByteBuffer) args[0];
                            if (acceptWrites) buffer.position(buffer.position() + Math.min(buffer.remaining(), maxWriteBytes));
                            return !buffer.hasRemaining();
                        case "release":
                            released = true;
                            return null;
                        default:
                            return null;
                    }
                });
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
        private final AudioOutput output;
        private final FormatSupport support = new FormatSupport.Builder()
                .setFormatSupportLevel(AudioOutputProvider.FORMAT_SUPPORTED_DIRECTLY).build();
        private int creations;

        StandardAudioOutputProvider(OutputConfig config) {
            this(config, (AudioOutput) Proxy.newProxyInstance(AudioOutput.class.getClassLoader(),
                    new Class<?>[]{AudioOutput.class}, (proxy, method, args) -> null));
        }

        StandardAudioOutputProvider(OutputConfig config, AudioOutput output) {
            this.config = config;
            this.output = output;
        }

        @Override public FormatSupport getFormatSupport(FormatConfig config) { return support; }

        @Override public OutputConfig getOutputConfig(FormatConfig config) { return this.config; }

        @Override public AudioOutput getAudioOutput(OutputConfig config) {
            assertSame(this.config, config);
            creations++;
            return output;
        }
    }
}
