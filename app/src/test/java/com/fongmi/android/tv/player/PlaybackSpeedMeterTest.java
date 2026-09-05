package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The regression these cover: a projector ROM whose per-UID TrafficStats counter is
 * absent or frozen used to blank the speed readout entirely, because both readouts
 * derived speed from that counter alone. The fix counts bytes in-process (OkHttp) and
 * from the playback kernel instead, so the readout no longer depends on kernel
 * accounting that some ROMs never compiled in.
 *
 * <p>The follow-up regression: ranking the kernel's own estimate first froze the
 * readout at a constant, because every engine reports a smoothed estimate that holds
 * its last reading once transfers stop rather than decaying to zero. The measured
 * deltas now rank first and the kernel is consulted only when neither counter can see
 * the transfer at all.
 */
public class PlaybackSpeedMeterTest {

    private static final long UNSUPPORTED = -1;

    @Test
    public void kernelSampleSurvivesUnsupportedTrafficStats() {
        Fake fake = new Fake();
        fake.trafficBytes = UNSUPPORTED;
        PlaybackSpeedMeter meter = fake.meter();

        // Baseline first: a kernel reading only stands in for an interval, never for
        // the startup sample that has no interval behind it.
        meter.observe(0);
        fake.advance(1000);
        meter.observe(8_000_000);

        assertEquals(PlaybackSpeedMeter.Source.KERNEL, meter.getSource());
        assertEquals(1_000_000, meter.getBytesPerSecond());
        assertFalse(meter.isUnavailable());
        assertEquals("976 KB/s", meter.getText());
    }

    @Test
    public void kernelSampleSurvivesFrozenTrafficStatsCounter() {
        Fake fake = new Fake();
        fake.trafficBytes = 4096;
        PlaybackSpeedMeter meter = fake.meter();

        // A ROM that reports a constant never advances the delta, and a native engine
        // socket never reaches OkHttp either. Neither counter has ever been seen to
        // move, so their zeroes mean "cannot see" and the kernel must be believed.
        meter.observe(0);
        fake.advance(1000);
        meter.observe(16_000_000);

        assertEquals(PlaybackSpeedMeter.Source.KERNEL, meter.getSource());
        assertEquals(2_000_000, meter.getBytesPerSecond());
    }

    @Test
    public void javaTrafficDoesNotDisableKernelFallbackForNativeSockets() {
        Fake fake = new Fake();
        fake.trafficBytes = UNSUPPORTED;
        PlaybackSpeedMeter meter = fake.meter();

        // Posters, scraping and config fetches trickle through OkHttp on every device.
        // They must not be mistaken for proof that this ROM can account for traffic:
        // OkHttp never sees a native socket, so only TrafficStats can settle that. If a
        // stray poster download switched the kernel tier off, the fallback would be dead
        // in practice on exactly the ROMs it exists for.
        meter.observe(0);
        fake.okHttpBytes = 2_000;
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(PlaybackSpeedMeter.Source.OK_HTTP, meter.getSource());
        assertEquals(2_000, meter.getBytesPerSecond());

        // Poster finished; the native video stream is still running and still invisible
        // to both counters, so the kernel takes over again.
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(PlaybackSpeedMeter.Source.KERNEL, meter.getSource());
        assertEquals(1_000_000, meter.getBytesPerSecond());
    }

    @Test
    public void workingTrafficStatsRetiresTheKernelFallbackOnceItCounts() {
        Fake fake = new Fake();
        fake.trafficBytes = 1_000_000;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        fake.trafficBytes = 1_512_000;
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(512_000, meter.getBytesPerSecond());

        // TrafficStats has proven it counts, and it sees native sockets too. Its zero is
        // therefore the truth and the frozen kernel estimate must not resurrect.
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(PlaybackSpeedMeter.Source.TRAFFIC_STATS, meter.getSource());
        assertEquals(0, meter.getBytesPerSecond());
    }

    @Test
    public void frozenKernelEstimateDoesNotPinReadoutWhileNetworkIsIdle() {
        Fake fake = new Fake();
        fake.trafficBytes = 1_000_000;
        fake.okHttpBytes = 1_000_000;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        fake.trafficBytes = 1_512_000;
        fake.okHttpBytes = 1_512_000;
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(512_000, meter.getBytesPerSecond());

        // Buffer full: nothing more arrives, but the kernel keeps reporting the
        // estimate it last computed. The readout must follow the bytes, not the
        // estimate, or it stays pinned at 512 KB/s for the rest of playback.
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(0, meter.getBytesPerSecond());

        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(0, meter.getBytesPerSecond());
    }

    @Test
    public void kernelCoversNativeSocketWhileTrafficStatsHasNotProvenItself() {
        Fake fake = new Fake();
        fake.trafficBytes = UNSUPPORTED;
        fake.okHttpBytes = 0;
        PlaybackSpeedMeter meter = fake.meter();

        // A native engine socket on a ROM with no per-UID accounting: neither counter
        // can ever observe the transfer, so the kernel is the only witness left.
        meter.observe(0);
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(PlaybackSpeedMeter.Source.KERNEL, meter.getSource());
        assertEquals(1_000_000, meter.getBytesPerSecond());
    }

    @Test
    public void provenCounterGoingBlindLaterDoesNotReviveTheKernel() {
        Fake fake = new Fake();
        fake.trafficBytes = 1_000_000;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        fake.trafficBytes = 1_512_000;
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(512_000, meter.getBytesPerSecond());

        // A counter that has proven it counts is not expected to stop. If it does, the
        // readout stays on the in-process OkHttp count rather than reviving the frozen
        // kernel estimate this class exists to avoid — the proof flag deliberately does
        // not decay, so one blind interval cannot undo it.
        fake.trafficBytes = UNSUPPORTED;
        fake.advance(1000);
        meter.observe(8_000_000);
        assertEquals(PlaybackSpeedMeter.Source.OK_HTTP, meter.getSource());
        assertEquals(0, meter.getBytesPerSecond());
    }

    @Test
    public void okHttpCounterReportsSpeedWhenTrafficStatsIsUnsupported() {
        Fake fake = new Fake();
        fake.trafficBytes = UNSUPPORTED;
        PlaybackSpeedMeter meter = fake.meter();

        // The first sample only establishes a baseline.
        meter.observe(0);
        assertTrue(meter.isUnavailable());

        fake.okHttpBytes = 512_000;
        fake.advance(1000);
        meter.observe(0);

        assertEquals(PlaybackSpeedMeter.Source.OK_HTTP, meter.getSource());
        assertEquals(512_000, meter.getBytesPerSecond());
        assertEquals("500 KB/s", meter.getText());
    }

    @Test
    public void okHttpCounterReportsSpeedWhenTrafficStatsIsFrozen() {
        Fake fake = new Fake();
        fake.trafficBytes = 4096;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        fake.okHttpBytes = 256_000;
        fake.advance(1000);
        meter.observe(0);

        assertEquals(PlaybackSpeedMeter.Source.OK_HTTP, meter.getSource());
        assertEquals(256_000, meter.getBytesPerSecond());
    }

    @Test
    public void trafficStatsWinsWhenItSeesMoreThanOkHttp() {
        Fake fake = new Fake();
        fake.trafficBytes = 0;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        // A native engine socket bypasses Java, so only TrafficStats sees those bytes.
        fake.trafficBytes = 1_024_000;
        fake.okHttpBytes = 16_000;
        fake.advance(1000);
        meter.observe(0);

        assertEquals(PlaybackSpeedMeter.Source.TRAFFIC_STATS, meter.getSource());
        assertEquals(1_024_000, meter.getBytesPerSecond());
    }

    @Test
    public void bothCountersIdleReportsZeroRatherThanUnavailable() {
        Fake fake = new Fake();
        fake.trafficBytes = 4096;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        fake.advance(1000);
        meter.observe(0);

        // No kernel reading either, so there is nothing to fall back to. A readable
        // counter reporting nothing is an idle interval, not a missing source: show
        // zero rather than blanking the readout.
        assertEquals(PlaybackSpeedMeter.Source.TRAFFIC_STATS, meter.getSource());
        assertEquals(0, meter.getBytesPerSecond());
        assertFalse(meter.isUnavailable());
    }

    @Test
    public void firstFallbackSampleAfterKernelHandoverUsesLiveBaseline() {
        Fake fake = new Fake();
        fake.trafficBytes = 1_000_000;
        fake.okHttpBytes = 1_000_000;
        PlaybackSpeedMeter meter = fake.meter();

        // While the kernel answers, both baselines must keep tracking, or the first
        // fallback sample would bill every byte since startup to one interval.
        meter.observe(8_000_000);
        fake.trafficBytes = 1_512_000;
        fake.okHttpBytes = 1_512_000;
        fake.advance(1000);
        meter.observe(0);

        assertEquals(512_000, meter.getBytesPerSecond());
    }

    @Test
    public void counterResetIsNotReportedAsNegativeSpeed() {
        Fake fake = new Fake();
        fake.trafficBytes = 1_000_000;
        fake.okHttpBytes = 1_000_000;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        fake.trafficBytes = 4096;
        fake.okHttpBytes = 0;
        fake.advance(1000);
        meter.observe(0);

        assertEquals(PlaybackSpeedMeter.Source.NONE, meter.getSource());
        assertEquals(0, meter.getBytesPerSecond());
        assertEquals("", meter.getText());
    }

    @Test
    public void resetClearsReadoutAndRebasesBaselines() {
        Fake fake = new Fake();
        fake.trafficBytes = 1_000_000;
        fake.okHttpBytes = 1_000_000;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(8_000_000);
        meter.reset();

        assertEquals(PlaybackSpeedMeter.Source.NONE, meter.getSource());
        assertEquals(0, meter.getBytesPerSecond());
        assertEquals("", meter.getText());

        // Rebased, so the next interval bills only bytes seen since the reset.
        fake.trafficBytes = 1_512_000;
        fake.okHttpBytes = 1_512_000;
        fake.advance(1000);
        meter.observe(0);
        assertEquals(512_000, meter.getBytesPerSecond());
    }

    @Test
    public void repeatedSampleWithinSameMillisecondKeepsPreviousReadout() {
        Fake fake = new Fake();
        fake.okHttpBytes = 1_000_000;
        PlaybackSpeedMeter meter = fake.meter();

        meter.observe(0);
        fake.advance(1000);
        fake.okHttpBytes = 1_512_000;
        meter.observe(0);
        assertEquals(512_000, meter.getBytesPerSecond());

        // No elapsed time means no new sample; the last good value must stand rather
        // than divide by zero or collapse to 0.
        fake.okHttpBytes = 9_000_000;
        meter.observe(0);
        assertEquals(512_000, meter.getBytesPerSecond());
    }

    @Test
    public void formatSwitchesToMegabytesAtOneThousandKilobytes() {
        assertEquals("0 KB/s", PlaybackSpeedMeter.format(0));
        assertEquals("999 KB/s", PlaybackSpeedMeter.format(999 * 1024));
        assertEquals("1.0 MB/s", PlaybackSpeedMeter.format(1000 * 1024));
        assertEquals("0 KB/s", PlaybackSpeedMeter.format(-1));
    }

    private static final class Fake {

        private long trafficBytes;
        private long okHttpBytes;
        private long nowMs = 1_000;

        private PlaybackSpeedMeter meter() {
            return new PlaybackSpeedMeter(() -> trafficBytes, () -> okHttpBytes, () -> nowMs);
        }

        private void advance(long deltaMs) {
            nowMs += deltaMs;
        }
    }
}
