package com.fongmi.android.tv.player;

import androidx.media3.common.C;
import com.github.catvod.crawler.diagnostics.DiagnosticCapture;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** At most three 4096-frame windows per checkpoint/capture; no buffer is retained or modified. */
public final class PcmDiagnosticProbe {
    private String captureId;
    private int windows;
    private long nextNs;

    public void sample(PlaybackDiagnosticCollector log, PlaybackDiagnosticCollector.Context owner,
                       ByteBuffer data, int start, int end, int encoding, int channels, String checkpoint, boolean protectedMedia) {
        if (!com.github.catvod.crawler.DebugLogStore.categoryEnabled(com.github.catvod.crawler.diagnostics.DiagnosticCategories.Category.AUDIO)
                || protectedMedia || owner == null || channels < 1 || channels > 32 || end <= start) return;
        DiagnosticCapture.Session capture = DiagnosticCapture.current(owner.trace(), owner.generation(), owner.attempt());
        if (capture == null || !capture.instance().equals(log.instanceId())) return;
        if (!capture.id().equals(captureId)) { captureId = capture.id(); windows = 0; nextNs = 0; }
        long now = System.nanoTime();
        if (windows >= 3 || now < nextNs) return;
        int width = switch (encoding) {
            case C.ENCODING_PCM_8BIT -> 1;
            case C.ENCODING_PCM_16BIT -> 2;
            case C.ENCODING_PCM_24BIT -> 3;
            case C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4;
            default -> 0;
        };
        if (width == 0) return;
        int frames = Math.min(4096, (end - start) / (width * channels));
        if (frames == 0) return;
        int index = windows++; nextNs = now + 1_000_000_000;
        ByteBuffer buffer = data.duplicate().order(ByteOrder.LITTLE_ENDIAN); buffer.position(start);
        double[] squares = new double[channels], peaks = new double[channels];
        int[] zeros = new int[channels], clipped = new int[channels], invalid = new int[channels];
        for (int frame = 0; frame < frames; frame++) for (int channel = 0; channel < channels; channel++) {
            double value = switch (encoding) {
                case C.ENCODING_PCM_8BIT -> ((buffer.get() & 255) - 128) / 128.0;
                case C.ENCODING_PCM_16BIT -> buffer.getShort() / 32768.0;
                case C.ENCODING_PCM_24BIT -> ((buffer.get() & 255) | ((buffer.get() & 255) << 8) | (buffer.get() << 16)) / 8388608.0;
                case C.ENCODING_PCM_32BIT -> buffer.getInt() / 2147483648.0;
                default -> buffer.getFloat();
            };
            if (!Double.isFinite(value)) { invalid[channel]++; continue; }
            squares[channel] += value * value; peaks[channel] = Math.max(peaks[channel], Math.abs(value));
            if (value == 0) zeros[channel]++;
            if (Math.abs(value) >= 0.9999) clipped[channel]++;
        }
        for (int channel = 0; channel < channels; channel++) {
            int c = channel;
            log.emit(owner, "audio.pcm-probe", checkpoint, "capture-owner", e -> e.observed("captureId", capture.id())
                    .observed("probeIndex", index).observed("channel", c).observed("channels", channels).observed("encoding", encoding)
                    .observed("frames", frames).observed("rms", Math.sqrt(squares[c] / frames)).observed("peak", peaks[c])
                    .observed("zeroRatio", (double) zeros[c] / frames).observed("clipped", clipped[c]).observed("nonFinite", invalid[c])
                    .observed("metricScope", "bounded PCM statistics; no raw samples retained"));
        }
    }
}
