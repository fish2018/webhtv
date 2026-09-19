package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;
import androidx.media3.exoplayer.audio.AudioOutput;
import androidx.media3.exoplayer.audio.AudioOutputProvider;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class ExoAudioOutputStateTest {

    @Test
    public void lateReleaseOfIdenticalConfiguration_keepsNewOutput() throws Exception {
        ExoAudioOutputState state = new ExoAudioOutputState();
        AtomicInteger oldReleases = new AtomicInteger();
        AudioOutput old = state.track(output(oldReleases::incrementAndGet), config(false, false));
        ExoAudioOutputState.Snapshot previous = state.snapshot();
        state.track(output(() -> { }), config(false, false));
        ExoAudioOutputState.Snapshot current = state.snapshot();
        assertEquals(previous, current);
        assertNotSame(previous, current);

        var executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(old::release).get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertSame(current, state.snapshot());
        assertTrue(state.snapshot().initialized());
        assertEquals(1, oldReleases.get());
    }

    @Test
    public void currentRelease_clearsStateBeforeBackendCompletes() {
        ExoAudioOutputState state = new ExoAudioOutputState();
        AtomicInteger releases = new AtomicInteger();
        AudioOutput current = state.track(output(() -> {
            assertFalse(state.snapshot().initialized());
            releases.incrementAndGet();
        }), config(false, false));

        current.release();

        assertSame(ExoAudioOutputState.Snapshot.empty(), state.snapshot());
        assertEquals(1, releases.get());
    }

    @Test
    public void newestRelease_doesNotRestoreOlderOutput() {
        ExoAudioOutputState state = new ExoAudioOutputState();
        AudioOutput old = state.track(output(() -> { }), config(false, false));
        AudioOutput current = state.track(output(() -> { }), config(true, false));

        current.release();
        assertFalse(state.snapshot().initialized());
        old.release();
        assertFalse(state.snapshot().initialized());
    }

    @Test
    public void oldReleaseAfterAnotherSeek_cannotClearThirdOutput() {
        ExoAudioOutputState state = new ExoAudioOutputState();
        AudioOutput first = state.track(output(() -> { }), config(false, false));
        AudioOutput second = state.track(output(() -> { }), config(false, false));
        state.track(output(() -> { }), config(false, true));
        ExoAudioOutputState.Snapshot third = state.snapshot();

        second.release();
        first.release();

        assertSame(third, state.snapshot());
        assertTrue(third.offload());
    }

    @Test
    public void separatePlayers_doNotShareOutputState() {
        ExoAudioOutputState first = new ExoAudioOutputState();
        ExoAudioOutputState second = new ExoAudioOutputState();
        AudioOutput firstOutput = first.track(output(() -> { }), config(false, false));
        second.track(output(() -> { }), config(true, false));
        ExoAudioOutputState.Snapshot secondSnapshot = second.snapshot();

        firstOutput.release();

        assertFalse(first.snapshot().initialized());
        assertSame(secondSnapshot, second.snapshot());
        assertTrue(secondSnapshot.tunneling());
    }

    @Test
    public void releaseFailure_doesNotLeavePublishedOutput() {
        ExoAudioOutputState state = new ExoAudioOutputState();
        AudioOutput output = state.track(output(() -> {
            throw new IllegalStateException("release failed");
        }), config(false, false));

        assertThrows(IllegalStateException.class, output::release);
        assertFalse(state.snapshot().initialized());
    }

    @Test
    public void snapshot_usesActualOutputConfiguration() {
        ExoAudioOutputState state = new ExoAudioOutputState();
        assertFalse(state.snapshot().initialized());
        state.track(output(() -> { }), config(true, false));

        ExoAudioOutputState.Snapshot snapshot = state.snapshot();
        assertEquals(C.ENCODING_PCM_16BIT, snapshot.encoding());
        assertEquals(48_000, snapshot.sampleRate());
        assertEquals(2, snapshot.channels());
        assertTrue(snapshot.tunneling());
        assertFalse(snapshot.offload());
    }

    private static AudioOutput output(Runnable release) {
        return (AudioOutput) Proxy.newProxyInstance(AudioOutput.class.getClassLoader(),
                new Class<?>[]{AudioOutput.class}, (proxy, method, args) -> {
                    if (method.getName().equals("release")) release.run();
                    return null;
                });
    }

    private static AudioOutputProvider.OutputConfig config(boolean tunneling, boolean offload) {
        return new AudioOutputProvider.OutputConfig.Builder()
                .setEncoding(C.ENCODING_PCM_16BIT)
                .setSampleRate(48_000)
                .setChannelMask(12)
                .setBufferSize(4096)
                .setIsTunneling(tunneling)
                .setIsOffload(offload)
                .build();
    }
}
