package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.net.Uri;
import android.os.Looper;
import android.os.SystemClock;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.Clock;
import androidx.media3.common.util.ParsableByteArray;
import androidx.media3.datasource.ByteArrayDataSource;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.TransferListener;
import androidx.media3.decoder.DecoderInputBuffer;
import androidx.media3.exoplayer.BaseRenderer;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.FormatHolder;
import androidx.media3.exoplayer.Renderer;
import androidx.media3.exoplayer.RendererCapabilities;
import androidx.media3.exoplayer.audio.AudioOffloadSupport;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;
import androidx.media3.exoplayer.source.MediaPeriod;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.source.SampleStream;
import androidx.media3.exoplayer.source.WrappingMediaSource;
import androidx.media3.exoplayer.upstream.Allocator;
import androidx.media3.exoplayer.MediaClock;
import androidx.media3.extractor.Extractor;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.ExtractorOutput;
import androidx.media3.extractor.PositionHolder;
import androidx.media3.extractor.SeekMap;
import androidx.media3.extractor.SeekPoint;
import androidx.media3.extractor.TrackOutput;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.robolectric.shadows.ShadowLooper;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Runs the shipped ExoPlayer/ProgressiveMediaPeriod/sample queues, with deterministic fake outputs. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
@LooperMode(LooperMode.Mode.PAUSED)
public class ExoStartupRecoveryIntegrationTest {
    private static final long SAMPLE_US = 21_333;
    private static final Format AUDIO = new Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC)
            .setCodecs("mp4a.40.2").setSampleRate(48_000).setChannelCount(2).build();
    private static final Format VIDEO = new Format.Builder().setSampleMimeType(MimeTypes.VIDEO_H264)
            .setWidth(16).setHeight(16).build();

    @Test
    public void firstSilentDirectOutputRecoversWithinExistingPeriodAndRead() throws Exception {
        runPlayback(true, 0);
    }

    @Test
    public void resumedFirstPlaybackRetainsBufferedKeyframeAndAudioSamples() throws Exception {
        runPlayback(true, 2250);
    }

    @Test
    public void healthyDirectOutputNeverReselectsOrCreatesPcm() throws Exception {
        runPlayback(false, 0);
    }

    private void runPlayback(boolean failDirect, long startMs) throws Exception {
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger opensAtDirect = new AtomicInteger();
        AtomicInteger directCreations = new AtomicInteger();
        AtomicInteger pcmCreations = new AtomicInteger();
        AtomicReference<PlaybackException> error = new AtomicReference<>();
        ExoAudioDirectFailureMemory.Route route = new ExoAudioDirectFailureMemory.Route(1, 2, 3, 4, 5);
        ExoCompressedAudioDirectPolicy policy = new ExoCompressedAudioDirectPolicy(
                (format, attributes) -> AudioOffloadSupport.DEFAULT_UNSUPPORTED,
                (format, attributes) -> true, Clock.DEFAULT,
                config -> {
                    directCreations.incrementAndGet();
                    opensAtDirect.set(opens.get());
                    return fakeOutput(failDirect);
                },
                new ExoAudioDirectFailureMemory(), new ExoCompressedAudioDirectPolicy.OutputEnvironment() {
                    @Override public ExoAudioDirectFailureMemory.Route expectedRoute(AudioAttributes attributes) { return route; }
                    @Override public ExoAudioDirectFailureMemory.Route actualRoute(AudioOutput output) { return route; }
                    @Override public long rawPlaybackHead(AudioOutput output) { return failDirect ? 0 : 1; }
                    @Override public ExoCompressedAudioDirectPolicy.StartupBuffer startupBuffer(AudioOutput output) {
                        return new ExoCompressedAudioDirectPolicy.StartupBuffer(4096, 4096, 4096);
                    }
                });
        policy.prepareForPlayback("memory:///first-playback", false);
        policy.setSelectedAudioFormat(AUDIO);
        TestAudioRenderer audio = new TestAudioRenderer(policy.wrapOutputProvider(pcmProvider(pcmCreations)));
        TestVideoRenderer video = new TestVideoRenderer();
        CountingSource source = new CountingSource(new ProgressiveMediaSource.Factory(
                () -> countingDataSource(opens), () -> new Extractor[]{new TestExtractor()})
                .createMediaSource(MediaItem.fromUri("memory:///first-playback")));
        ExoPlayer player = new ExoPlayer.Builder(RuntimeEnvironment.getApplication(),
                (handler, videoListener, audioListener, text, metadata) ->
                        new Renderer[]{video, new ExoStartupAudioRenderer(audio, policy)})
                .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(1000, 5000, 100, 100)
                        .setBackBuffer(0, true).build()).build();
        player.addListener(new Player.Listener() {
            @Override public void onPlayerError(PlaybackException failure) { error.set(failure); }
        });
        try {
            player.setMediaSource(source, startMs);
            player.prepare();
            player.play();
            long deadlineNs = System.nanoTime() + 8_000_000_000L;
            while (error.get() == null && player.getCurrentPosition() < startMs + 300
                    && System.nanoTime() < deadlineNs) {
                ShadowLooper.getShadowMainLooper().idleFor(Duration.ofMillis(10));
                Thread.sleep(2); // Let the real playback/loader threads consume their scheduled work.
            }
            assertNull(error.get());
            assertTrue("Playback did not advance", player.getCurrentPosition() >= startMs + 300);
            assertEquals(1, source.preparations);
            assertEquals(1, source.periods);
            assertTrue(opensAtDirect.get() > 0);
            assertEquals("Recovery must not reopen the source", opensAtDirect.get(), opens.get());
            assertEquals(1, directCreations.get());
            assertEquals(failDirect ? 1 : 0, pcmCreations.get());
            assertEquals(failDirect ? 2 : 1, audio.enables);
            assertEquals(failDirect ? 2 : 1, video.enables);
            if (failDirect) {
                assertTrue("Must replay the first pending audio sample",
                        Math.abs(audio.firstPcmTimeUs - audio.firstDirectTimeUs) <= SAMPLE_US);
                assertTrue("The 10 second watchdog must not drive recovery", audio.pcmCreatedAtMs - audio.directCreatedAtMs < 2000);
            }
        } finally {
            player.release();
            ShadowLooper.getShadowMainLooper().idle();
        }
    }

    private static final class CountingSource extends WrappingMediaSource {
        volatile int preparations;
        volatile int periods;
        CountingSource(MediaSource child) { super(child); }
        @Override protected void prepareSourceInternal() { preparations++; super.prepareSourceInternal(); }
        @Override public MediaPeriod createPeriod(MediaPeriodId id, Allocator allocator, long positionUs) {
            periods++;
            return super.createPeriod(id, allocator, positionUs);
        }
    }

    private static DataSource countingDataSource(AtomicInteger opens) {
        return new DataSource() {
            final ByteArrayDataSource delegate = new ByteArrayDataSource(new byte[2048]);
            @Override public void addTransferListener(TransferListener listener) { delegate.addTransferListener(listener); }
            @Override public long open(DataSpec spec) throws IOException { opens.incrementAndGet(); return delegate.open(spec); }
            @Override public int read(byte[] target, int offset, int length) throws IOException { return delegate.read(target, offset, length); }
            @Override public Uri getUri() { return delegate.getUri(); }
            @Override public void close() throws IOException { delegate.close(); }
        };
    }

    private static final class TestExtractor implements Extractor {
        TrackOutput audio;
        TrackOutput video;
        int index;
        final byte[] one = new byte[1];
        @Override public boolean sniff(ExtractorInput input) { return true; }
        @Override public void init(ExtractorOutput output) {
            audio = output.track(0, C.TRACK_TYPE_AUDIO);
            video = output.track(1, C.TRACK_TYPE_VIDEO);
            audio.format(AUDIO);
            video.format(VIDEO);
            output.endTracks();
            output.seekMap(new SeekMap() {
                @Override public boolean isSeekable() { return true; }
                @Override public long getDurationUs() { return 2048 * SAMPLE_US; }
                @Override public SeekPoints getSeekPoints(long timeUs) {
                    long sample = timeUs / SAMPLE_US / 48 * 48;
                    return new SeekPoints(new SeekPoint(sample * SAMPLE_US, sample));
                }
            });
        }
        @Override public int read(ExtractorInput input, PositionHolder position) throws IOException {
            if (!input.readFully(one, 0, 1, true)) return RESULT_END_OF_INPUT;
            audio.sampleData(new ParsableByteArray(new byte[128]), 128);
            audio.sampleMetadata(index * SAMPLE_US, C.BUFFER_FLAG_KEY_FRAME, 128, 0, null);
            video.sampleData(new ParsableByteArray(new byte[16]), 16);
            video.sampleMetadata(index * SAMPLE_US, index % 48 == 0 ? C.BUFFER_FLAG_KEY_FRAME : 0, 16, 0, null);
            index++;
            return RESULT_CONTINUE;
        }
        @Override public void seek(long position, long timeUs) { index = (int) position; }
        @Override public void release() {}
    }

    private static class TestVideoRenderer extends BaseRenderer {
        final DecoderInputBuffer input = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
        int enables;
        boolean ended;
        TestVideoRenderer() { super(C.TRACK_TYPE_VIDEO); }
        @Override public String getName() { return "test-video"; }
        @Override public int supportsFormat(Format format) { return RendererCapabilities.create(
                MimeTypes.isVideo(format.sampleMimeType) ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_TYPE); }
        @Override protected void onEnabled(boolean joining, boolean mayRender) { enables++; }
        @Override protected void onPositionReset(long position, boolean joining, boolean reset) { ended = false; }
        @Override public void render(long positionUs, long realtimeUs) {
            while (!ended && getReadingPositionUs() < positionUs + 500_000) {
                input.clear();
                int result = readSource(getFormatHolder(), input, 0);
                if (result == C.RESULT_NOTHING_READ) break;
                if (result == C.RESULT_BUFFER_READ && input.isEndOfStream()) ended = true;
            }
        }
        @Override public boolean isReady() { return true; }
        @Override public boolean isEnded() { return ended; }
    }

    private static final class TestAudioRenderer extends BaseRenderer implements MediaClock {
        final AudioOutputProvider provider;
        final DecoderInputBuffer input = new DecoderInputBuffer(DecoderInputBuffer.BUFFER_REPLACEMENT_MODE_NORMAL);
        AudioOutput output;
        Format format;
        boolean pending;
        boolean pcm;
        long positionUs;
        long firstTimeUs;
        long firstDirectTimeUs = C.TIME_UNSET;
        long firstPcmTimeUs = C.TIME_UNSET;
        long directCreatedAtMs;
        long pcmCreatedAtMs;
        int enables;
        TestAudioRenderer(AudioOutputProvider provider) { super(C.TRACK_TYPE_AUDIO); this.provider = provider; }
        @Override public String getName() { return "test-audio"; }
        @Override public int supportsFormat(Format format) { return RendererCapabilities.create(
                MimeTypes.isAudio(format.sampleMimeType) ? C.FORMAT_HANDLED : C.FORMAT_UNSUPPORTED_TYPE); }
        @Override protected void onEnabled(boolean joining, boolean mayRender) { enables++; }
        @Override protected void onPositionReset(long position, boolean joining, boolean reset) {
            positionUs = position;
            pending = false;
            if (output != null) output.flush();
        }
        @Override protected void onStarted() { if (output != null) output.play(); }
        @Override protected void onStopped() { if (output != null) output.pause(); }
        @Override protected void onDisabled() {
            if (output != null) output.release();
            output = null;
            format = null;
            pending = false;
        }
        @Override public void render(long position, long realtime) throws ExoPlaybackException {
            try {
                for (int i = 0; i < 64; i++) {
                    if (!pending) {
                        input.clear();
                        FormatHolder holder = getFormatHolder();
                        int result = readSource(holder, input, format == null ? SampleStream.FLAG_REQUIRE_FORMAT : 0);
                        if (result == C.RESULT_NOTHING_READ) return;
                        if (result == C.RESULT_FORMAT_READ) { format = holder.format; continue; }
                        if (input.isEndOfStream()) return;
                        input.flip();
                        pending = true;
                    }
                    if (output == null) {
                        AudioOutputProvider.FormatConfig requested = new AudioOutputProvider.FormatConfig.Builder(format).build();
                        pcm = provider.getFormatSupport(requested).supportLevel == AudioOutputProvider.FORMAT_UNSUPPORTED;
                        if (pcm) requested = new AudioOutputProvider.FormatConfig.Builder(format.buildUpon()
                                .setSampleMimeType(MimeTypes.AUDIO_RAW).setPcmEncoding(C.ENCODING_PCM_16BIT).build()).build();
                        output = provider.getAudioOutput(provider.getOutputConfig(requested));
                        firstTimeUs = input.timeUs;
                        if (pcm) { firstPcmTimeUs = input.timeUs; pcmCreatedAtMs = SystemClock.elapsedRealtime(); }
                        else { firstDirectTimeUs = input.timeUs; directCreatedAtMs = SystemClock.elapsedRealtime(); }
                        if (getState() == STATE_STARTED) output.play();
                    }
                    if (!output.write(input.data, 1, input.timeUs)) return;
                    pending = false;
                }
            } catch (Exception failure) {
                throw createRendererException(failure, format, PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED);
            }
        }
        @Override public boolean isReady() { return output != null || isSourceReady(); }
        @Override public boolean isEnded() { return false; }
        @Override public MediaClock getMediaClock() { return this; }
        @Override public long getPositionUs() {
            if (output != null) positionUs = Math.max(positionUs, firstTimeUs + output.getPositionUs());
            return positionUs;
        }
        @Override public PlaybackParameters getPlaybackParameters() { return PlaybackParameters.DEFAULT; }
        @Override public void setPlaybackParameters(PlaybackParameters parameters) {}
    }

    private static AudioOutputProvider pcmProvider(AtomicInteger creations) {
        return new AudioOutputProvider() {
            @Override public FormatSupport getFormatSupport(FormatConfig config) {
                return MimeTypes.AUDIO_RAW.equals(config.format.sampleMimeType) ? new FormatSupport.Builder()
                        .setFormatSupportLevel(FORMAT_SUPPORTED_DIRECTLY).build() : FormatSupport.UNSUPPORTED;
            }
            @Override public OutputConfig getOutputConfig(FormatConfig config) {
                return new OutputConfig.Builder().setEncoding(C.ENCODING_PCM_16BIT).setSampleRate(48_000)
                        .setChannelMask(12).setBufferSize(4096).build();
            }
            @Override public AudioOutput getAudioOutput(OutputConfig config) { creations.incrementAndGet(); return fakeOutput(false); }
            @Override public void addListener(Listener listener) {}
            @Override public void removeListener(Listener listener) {}
            @Override public void release() {}
        };
    }

    private static AudioOutput fakeOutput(boolean stalled) {
        long[] state = {0, C.TIME_UNSET, 0}; // written bytes, last play time, accumulated position
        return (AudioOutput) Proxy.newProxyInstance(AudioOutput.class.getClassLoader(), new Class<?>[]{AudioOutput.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "write":
                            ByteBuffer data = (ByteBuffer) args[0];
                            int count = stalled ? (int) Math.min(data.remaining(), Math.max(0, 4096 - state[0])) : data.remaining();
                            data.position(data.position() + count);
                            state[0] += count;
                            return !data.hasRemaining();
                        case "getPositionUs":
                            return stalled || state[0] == 0 ? 0L : state[2]
                                    + (state[1] == C.TIME_UNSET ? 0 : (SystemClock.elapsedRealtime() - state[1]) * 1000);
                        case "play": state[1] = SystemClock.elapsedRealtime(); return null;
                        case "pause":
                            if (state[1] != C.TIME_UNSET) state[2] += (SystemClock.elapsedRealtime() - state[1]) * 1000;
                            state[1] = C.TIME_UNSET;
                            return null;
                        case "flush": state[0] = state[2] = 0; state[1] = C.TIME_UNSET; return null;
                        default:
                            if (method.getReturnType() == boolean.class) return false;
                            if (method.getReturnType() == int.class) return 0;
                            if (method.getReturnType() == long.class) return 0L;
                            return null;
                    }
                });
    }
}
