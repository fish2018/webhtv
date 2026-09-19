package com.fongmi.android.tv.player.exo.ass;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.text.TextRenderer;
import androidx.media3.ui.PlayerView;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

/**
 * One engine-owned, optional ASS session. Playback callbacks only publish bounded input or time.
 * A single lazy worker owns libass, EGL and all native teardown. UI detach retains the full script.
 */
public final class ExoAssSession implements TextRenderer.Observer {
    public enum State { COMPAT, PREPARING, ACTIVE, FALLBACK, DISABLED }

    private static final String TAG = "ExoAss";
    private static final long FRAME_INTERVAL_NS = 16_666_667L;
    private final Context context;
    private final boolean tunneling;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Runnable workerTask = this::drain;
    private final Runnable uiTask = this::refreshHost;
    private final long[] renderTimes = new long[4096];
    private final long[] uploadTimes = new long[4096];
    private final long[] swapTimes = new long[4096];
    @Nullable private TextRenderer.Stream stream;
    @Nullable private TextRenderer.Stream displayingStream;
    @Nullable private Format videoFormat;
    @Nullable private byte[] script;
    @Nullable private AssPacketInput packets;
    @Nullable private AssFontSet fonts;
    @Nullable private AssSurfaceHost host;
    @Nullable private Surface surface;
    @Nullable private HandlerThread thread;
    @Nullable private Handler worker;
    private int width, height;
    private long generation, surfaceEpoch, layoutEpoch, policyEpoch, revision, clockVersion;
    private long positionUs = C.TIME_UNSET, textOffsetUs;
    private long handledRevision = -1, handledClock = -1;
    private boolean enabled = true, ended, released, releaseComplete, posted, running, uiPosted;
    private boolean nativeAlive;
    private State state = State.COMPAT;
    private String failure = "";
    private long frames, scripts, staleFrames, maxRenderUs, renderedTimeMs;

    // Worker-only state. None of these fields is accessed by the UI/playback threads.
    private long handle, loadedStream = -1, connectedSurfaceEpoch = -1, renderedRevision = -1;
    private long submittedRevision = -1;
    private volatile long lastStartNs;
    private String fontConfig;
    private AssFontSet.Snapshot loadedFonts;
    private int loadedPacketCount;
    private int slowFrames;
    private final long[] nativeStats = new long[6];

    @Nullable
    public static ExoAssSession createIfEnabled(Context context, boolean tunneling) {
        return Process.is64Bit() ? new ExoAssSession(context, tunneling) : null;
    }

    ExoAssSession(Context context, boolean tunneling) {
        this.context = context.getApplicationContext();
        this.tunneling = tunneling;
    }

    /** Called for each foreground source; preload factories do not have this session. */
    @Nullable public AssFontSet beginMediaFonts() {
        synchronized (lock) {
            if (fonts != null) fonts.close();
            fonts = enabled && !released ? new AssFontSet(this::onFontsChanged) : null;
            stream = displayingStream = null;
            script = null;
            packets = null;
            videoFormat = null;
            failure = "";
            invalidateLocked();
            return fonts;
        }
    }

    private void onFontsChanged(AssFontSet changed) {
        synchronized (lock) {
            if (released || fonts != changed) return;
            if (!changed.failure().isEmpty()) failLocked(changed.failure());
            else invalidateLocked();
        }
    }

    public void attach(PlayerView view) {
        checkMain();
        synchronized (lock) {
            if (released || host != null && host.view() == view) return;
        }
        detach();
        synchronized (lock) {
            host = new AssSurfaceHost(this, view);
            postUiLocked();
        }
    }

    public void detach() {
        checkMain();
        AssSurfaceHost previous;
        synchronized (lock) { previous = host; }
        if (previous != null) previous.release();
        synchronized (lock) {
            host = null;
            surface = null;
            surfaceEpoch++;
            invalidateLocked();
        }
    }

    /** Session kill switch; immediately restores compatible Cue output without recreating Exo. */
    public void setEnabled(boolean enabled) {
        synchronized (lock) {
            if (released || this.enabled == enabled) return;
            this.enabled = enabled;
            policyEpoch++;
            if (enabled) failure = fonts == null ? "" : fonts.failure();
            invalidateLocked();
        }
    }

    public void release() {
        checkMain();
        detach();
        synchronized (lock) {
            if (released) return;
            released = true;
            if (fonts != null) fonts.close();
            fonts = null;
            script = null;
            packets = null;
            stream = null;
            invalidateLocked();
            if (worker == null) releaseComplete = true;
        }
    }

    /** Called by a multicast of the existing video metadata listener, without replacing analytics. */
    public void onVideoFrame(Format format) {
        synchronized (lock) {
            if (released || format.equals(videoFormat)) return;
            videoFormat = format;
            layoutEpoch++;
            invalidateLocked();
        }
    }

    @Override
    public void onStream(TextRenderer.Stream stream, long generation) {
        synchronized (lock) {
            if (released) return;
            this.stream = stream;
            this.generation = generation;
            displayingStream = null;
            script = null;
            packets = isPacketized(stream.format) ? new AssPacketInput() : null;
            ended = false;
            positionUs = C.TIME_UNSET;
            failure = fonts == null ? "" : fonts.failure();
            Log.i(TAG, "input stream=" + stream.sequence + " kind="
                    + (packets != null ? "media3-ssa" : isExternal(stream.format) ? "full-ass" : "compat"));
            invalidateLocked();
        }
    }

    @Override
    public void onReset(TextRenderer.Stream stream, long generation, long positionUs) {
        synchronized (lock) {
            if (released || this.stream == null || this.stream.sequence != stream.sequence) return;
            this.generation = generation;
            this.positionUs = positionUs;
            displayingStream = stream;
            ended = false;
            // Complete scripts and already received packets remain available across seeks.
            // Repeated Matroska preroll is deduplicated by its original ReadOrder.
            invalidateLocked();
        }
    }

    @Override
    public void onSample(TextRenderer.Stream stream, long generation, Format format, ByteBuffer data, long timeUs) {
        synchronized (lock) {
            if (released || this.stream == null || this.stream.sequence != stream.sequence
                    || generation != this.generation || !failure.isEmpty()) return;
            try {
                if (packets != null && isPacketized(format)) {
                    if (data.hasRemaining() && packets.add(data, timeUs, stream.offsetUs)) {
                        // Events are retained in order, not coalesced like clock notifications.
                        // Keep the last presented frame visible while appending the next event.
                        clockVersion++;
                        scheduleLocked(true);
                    }
                    return;
                }
                if (!isExternal(format) || script != null) return;
                // At most one complete file is retained per selected stream. Seeks may redeliver it.
                // No event/header/font queue uses a latest-wins policy.
                script = AssInput.copy(data);
                scripts++;
                invalidateLocked();
            } catch (IllegalArgumentException | ArithmeticException error) {
                failLocked(error.getMessage());
            }
        }
    }

    @Override
    public void onClock(TextRenderer.Stream readingStream, @Nullable TextRenderer.Stream displayingStream,
                        long generation, long positionUs, long textOffsetUs, boolean started, boolean ended) {
        synchronized (lock) {
            if (released || stream == null || stream.sequence != readingStream.sequence || generation != this.generation) return;
            boolean control = this.ended != ended || this.displayingStream != displayingStream
                    || this.textOffsetUs != textOffsetUs;
            boolean changed = this.positionUs != positionUs || control;
            this.displayingStream = displayingStream;
            this.positionUs = positionUs;
            this.textOffsetUs = textOffsetUs;
            this.ended = ended;
            if (control) invalidateLocked();
            if (changed) {
                clockVersion++;
                // Actual renderer media time freezes on pause/buffering and already includes speed.
                // There is no UI position polling or independent extrapolating timer.
                scheduleLocked(false);
            }
        }
    }

    @Override
    public void onCues(@Nullable TextRenderer.Stream displayingStream, long generation, CueGroup cues) {
        // The original TextRenderer still delivers every CueGroup to PlayerView. Its SubtitleView
        // stays current while hidden, so restoration needs no seek, network reload or reparse.
    }

    @Override
    public void onDisabled(long generation) {
        synchronized (lock) {
            if (released) return;
            this.generation = generation;
            stream = displayingStream = null;
            script = null;
            packets = null;
            ended = false;
            invalidateLocked();
        }
    }

    void setSurface(AssSurfaceHost owner, @Nullable Surface surface, int width, int height, boolean recreated) {
        checkMain();
        synchronized (lock) {
            if (host != owner || released) return;
            boolean replaced = this.surface != surface || recreated;
            boolean resized = this.width != width || this.height != height;
            if (!replaced && !resized) return;
            this.surface = surface;
            this.width = width;
            this.height = height;
            if (replaced) surfaceEpoch++;
            if (resized) layoutEpoch++;
            invalidateLocked();
        }
    }

    private static boolean isExternal(Format format) {
        return MimeTypes.TEXT_SSA.equals(format.sampleMimeType) && format.cryptoType == C.CRYPTO_TYPE_NONE
                && format.initializationData.isEmpty() && AssInput.isExternalId(format.id);
    }

    private static boolean isPacketized(Format format) {
        return MimeTypes.TEXT_SSA.equals(format.sampleMimeType) && format.cryptoType == C.CRYPTO_TYPE_NONE
                && AssPacketInput.matches(format.initializationData);
    }

    private boolean admittedLocked() {
        if (!enabled || released || ended || stream == null
                || !(isExternal(stream.format) || isPacketized(stream.format))
                || !failure.isEmpty() || tunneling || videoFormat == null) return false;
        return AssVideoPolicy.supports(videoFormat);
    }

    private boolean renderableLocked() {
        return admittedLocked() && (script != null || packets != null && packets.size() > 0)
                && surface != null && width > 0 && height > 0
                && positionUs != C.TIME_UNSET && displayingStream != null
                && displayingStream.sequence == stream.sequence;
    }

    private void invalidateLocked() {
        revision++;
        state = released || !enabled || stream == null ? State.DISABLED
                : !failure.isEmpty() ? State.FALLBACK : admittedLocked() ? State.PREPARING : State.COMPAT;
        postUiLocked();
        scheduleLocked(true);
    }

    private void failLocked(String reason) {
        failure = reason == null ? "unknown" : reason;
        Log.w(TAG, "fallback reason=" + failure + " generation=" + generation);
        invalidateLocked();
    }

    private void scheduleLocked(boolean control) {
        if (!control && !renderableLocked()) return;
        if (worker == null) {
            if (!renderableLocked()) return;
            // Rendering is foreground playback work. BACKGROUND also applies Android's
            // background scheduling policy; it is not just a label for a non-UI thread.
            thread = new HandlerThread("ExoAss", Process.THREAD_PRIORITY_DEFAULT);
            thread.start();
            worker = new Handler(thread.getLooper());
        }
        if (running || posted) return;
        posted = true;
        long delayMs = control ? 0 : Math.max(0, (lastStartNs + FRAME_INTERVAL_NS - System.nanoTime() + 999_999) / 1_000_000);
        worker.postDelayed(workerTask, delayMs);
    }

    private void postUiLocked() {
        if (uiPosted) return;
        uiPosted = true;
        main.post(uiTask);
    }

    private void refreshHost() {
        checkMain();
        AssSurfaceHost current;
        boolean admitted;
        boolean active;
        synchronized (lock) {
            uiPosted = false;
            current = host;
            admitted = admittedLocked();
            active = state == State.ACTIVE;
        }
        if (current != null) current.update(admitted, active);
    }

    private record Request(long revision, long clockVersion, long stream, long generation,
                           long surfaceEpoch, long layoutEpoch, long policyEpoch,
                           boolean admitted, boolean renderable, boolean released,
                           byte[] script, boolean packetized, List<AssPacketInput.Packet> chunks, int packetCount,
                           Surface surface, int width, int height,
                           Format video, long positionUs, long streamOffsetUs, long textOffsetUs,
                           AssFontSet.Snapshot fonts) { }

    private Request requestLocked() {
        AssFontSet.Snapshot currentFonts = fonts == null ? AssFontSet.EMPTY : fonts.snapshot();
        boolean replay = handle == 0 || stream == null || loadedStream != stream.sequence || loadedFonts != currentFonts;
        List<AssPacketInput.Packet> chunks = packets == null ? List.of() : packets.after(replay ? 0 : loadedPacketCount);
        return new Request(revision, clockVersion, stream == null ? -1 : stream.sequence, generation,
                surfaceEpoch, layoutEpoch, policyEpoch, admittedLocked(), renderableLocked(), released,
                packets == null ? script : stream.format.initializationData.get(1), packets != null,
                chunks, packets == null ? 0 : packets.size(),
                surface, width, height, videoFormat, positionUs, stream == null ? 0 : stream.offsetUs, textOffsetUs,
                currentFonts);
    }

    private void drain() {
        Request request;
        synchronized (lock) {
            posted = false;
            running = true;
            request = requestLocked();
        }
        try {
            if (request.released || !request.admitted
                    || handle != 0 && (loadedStream != request.stream || loadedFonts != request.fonts)) {
                destroyNative();
            }
            if (request.released) return;
            if (!request.renderable) {
                if (handle != 0 && connectedSurfaceEpoch != request.surfaceEpoch) {
                    AssNative.setSurface(handle, null);
                    connectedSurfaceEpoch = request.surfaceEpoch;
                }
                return;
            }
            if (handle == 0) {
                AssNative.ensureLoaded();
                fontConfig = AssFonts.prepare(context);
                handle = AssNative.create(fontConfig, request.fonts.names(), request.fonts.data());
                if (handle == 0) throw new IllegalStateException("native-init");
                loadedFonts = request.fonts;
                synchronized (lock) { nativeAlive = true; }
            }
            if (loadedStream != request.stream) {
                boolean loaded = request.packetized
                        ? AssNative.loadHeader(handle, AssInput.normalizeHeader(request.script))
                        : AssNative.load(handle, AssInput.normalize(request.script));
                if (!loaded) throw new IllegalStateException("script-rejected");
                loadedStream = request.stream;
                loadedPacketCount = 0;
            }
            for (AssPacketInput.Packet packet : request.chunks)
                if (!AssNative.chunk(handle, packet.data(), packet.startMs(), packet.durationMs()))
                    throw new IllegalStateException("packet-rejected");
            loadedPacketCount = request.packetCount;
            if (connectedSurfaceEpoch != request.surfaceEpoch) {
                if (!AssNative.setSurface(handle, request.surface)) throw new IllegalStateException("surface-init");
                connectedSurfaceEpoch = request.surfaceEpoch;
            }
            long timeMs = AssInput.timeMs(request.positionUs, request.streamOffsetUs, request.textOffsetUs);
            int space = AssVideoPolicy.colorSpace(request.video);
            int range = AssVideoPolicy.colorRange(request.video);
            double aspect = request.video.pixelWidthHeightRatio > 0 ? request.video.pixelWidthHeightRatio : 1.0;
            lastStartNs = System.nanoTime();
            int result = AssNative.render(handle, timeMs, request.width, request.height,
                    request.video.width, request.video.height, aspect, space, range,
                    renderedRevision != request.revision, nativeStats);
            if (result < 0) throw new IllegalStateException(result == -2 ? "render-budget" : "render-surface");
            renderedRevision = request.revision;
            synchronized (lock) {
                int slot = (int) (frames % renderTimes.length);
                renderTimes[slot] = nativeStats[0]; uploadTimes[slot] = nativeStats[1]; swapTimes[slot] = nativeStats[2];
                frames++;
                maxRenderUs = Math.max(maxRenderUs, nativeStats[0]);
                renderedTimeMs = timeMs;
                if (revision != request.revision || released) { staleFrames++; return; }
                slowFrames = nativeStats[0] + nativeStats[1] > 33_334 ? slowFrames + 1 : 0;
                if (nativeStats[0] > 100_000 || slowFrames >= 3) {
                    failLocked("render-time-budget");
                    return;
                }
            }
            if (result > 0 && submittedRevision != request.revision) {
                submittedRevision = request.revision;
                main.post(() -> {
                    synchronized (lock) {
                        if (released || revision != request.revision || !renderableLocked()) return;
                        state = State.ACTIVE;
                        Log.i(TAG, "active stream=" + request.stream + " generation=" + request.generation
                                + " surface=" + request.surfaceEpoch + " layout=" + request.layoutEpoch
                                + " input=" + (request.packetized ? "media3-ssa" : "full-ass")
                                + " packets=" + request.packetCount + " fonts=" + request.fonts.names().length);
                        postUiLocked();
                    }
                });
            }
        } catch (Exception | LinkageError error) {
            synchronized (lock) {
                if (!released && revision == request.revision) failLocked(error.getMessage());
            }
        } finally {
            synchronized (lock) {
                running = false;
                handledRevision = request.revision;
                handledClock = request.clockVersion;
                if (request.released) {
                    releaseComplete = true;
                    if (thread != null) thread.quitSafely();
                    Log.i(TAG, "released frames=" + frames + " stale=" + staleFrames);
                } else if (revision != handledRevision || clockVersion != handledClock) {
                    scheduleLocked(revision != handledRevision);
                }
            }
        }
    }

    private void destroyNative() {
        if (handle != 0) AssNative.destroy(handle);
        handle = 0;
        loadedStream = connectedSurfaceEpoch = renderedRevision = submittedRevision = -1;
        loadedFonts = null;
        loadedPacketCount = 0;
        slowFrames = 0;
        if (fontConfig != null) new File(fontConfig).delete();
        fontConfig = null;
        synchronized (lock) { nativeAlive = false; }
    }

    /** Deterministic recoverable failure hook for the prototype test harness. */
    void injectFailure(String reason) {
        synchronized (lock) { if (!released) failLocked("injected-" + reason); }
    }

    public record Diagnostics(State state, String failure, long frames, long renderP95Us,
                              long uploadP95Us, long swapP95Us, long maxRenderUs, long staleFrames,
                              long scripts, long timeMs, long generation, long surfaceEpoch,
                              long layoutEpoch, long stream, boolean nativeAlive, boolean releaseComplete,
                              boolean workerAlive, int workerTid,
                              int fontCount, int fontBytes, int packetCount, int packetBytes) { }

    public Diagnostics diagnostics() {
        synchronized (lock) {
            int count = (int) Math.min(frames, renderTimes.length);
            return new Diagnostics(state, failure, frames, percentile(renderTimes, count),
                    percentile(uploadTimes, count), percentile(swapTimes, count), maxRenderUs, staleFrames,
                    scripts, renderedTimeMs, generation, surfaceEpoch, layoutEpoch,
                    stream == null ? -1 : stream.sequence, nativeAlive, releaseComplete,
                    thread != null && thread.isAlive(), thread == null ? -1 : thread.getThreadId(),
                    fonts == null ? 0 : fonts.snapshot().names().length,
                    fonts == null ? 0 : fonts.snapshot().bytes(),
                    packets == null ? 0 : packets.size(), packets == null ? 0 : packets.bytes());
        }
    }

    private static long percentile(long[] values, int count) {
        if (count == 0) return 0;
        long[] copy = Arrays.copyOf(values, count);
        Arrays.sort(copy);
        return copy[Math.min(count - 1, (int) Math.ceil(count * .95) - 1)];
    }

    private static void checkMain() {
        if (Looper.myLooper() != Looper.getMainLooper()) throw new IllegalStateException("ASS host requires main thread");
    }
}
