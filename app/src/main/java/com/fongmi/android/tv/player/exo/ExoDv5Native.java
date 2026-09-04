package com.fongmi.android.tv.player.exo;

import android.os.Build;
import android.view.Surface;

import androidx.annotation.Nullable;

/** JNI boundary for the independent Exo DV5 renderer. */
final class ExoDv5Native {

    static final int CAPABILITY_IMAGE_READER = 1 << 0;
    static final int CAPABILITY_VULKAN_12 = 1 << 1;
    static final int CAPABILITY_AHB_IMPORT = 1 << 2;
    static final int CAPABILITY_YCBCR_CONVERSION = 1 << 3;
    static final int CAPABILITY_FOREIGN_QUEUE = 1 << 4;
    static final int CAPABILITY_LIBPLACEBO_375 = 1 << 5;
    static final int CAPABILITY_LIBDOVI = 1 << 6;
    static final int REQUIRED_CAPABILITIES = CAPABILITY_IMAGE_READER
            | CAPABILITY_VULKAN_12
            | CAPABILITY_AHB_IMPORT
            | CAPABILITY_YCBCR_CONVERSION
            | CAPABILITY_FOREIGN_QUEUE
            | CAPABILITY_LIBPLACEBO_375
            | CAPABILITY_LIBDOVI;

    private static final LoadResult LOAD_RESULT = loadLibrary();

    private ExoDv5Native() {
    }

    static Probe probe() {
        if (Build.VERSION.SDK_INT < ExoDv5GpuMappingPolicy.MIN_GPU_MAPPING_API) {
            return new Probe(false, 0, "api-below-26");
        }
        if (!LOAD_RESULT.loaded()) {
            return new Probe(false, 0, LOAD_RESULT.reason());
        }
        try {
            int capabilities = nativeProbeCapabilities();
            boolean available = (capabilities & REQUIRED_CAPABILITIES)
                    == REQUIRED_CAPABILITIES;
            return new Probe(
                    available,
                    capabilities,
                    available ? "available" : "missing-capability");
        } catch (Throwable error) {
            return new Probe(false, 0,
                    "probe-" + error.getClass().getSimpleName());
        }
    }

    static NativeRenderer create(int width, int height) {
        if (!LOAD_RESULT.loaded() || width <= 0 || height <= 0) {
            throw new IllegalStateException("DV5 native renderer unavailable");
        }
        long handle = nativeCreate(width, height);
        if (handle == 0) throw new IllegalStateException("AImageReader creation failed");
        Surface surface = nativeGetInputSurface(handle);
        if (surface == null) {
            nativeRelease(handle);
            throw new IllegalStateException("AImageReader Surface creation failed");
        }
        return new NativeRenderer(handle, surface);
    }

    private static LoadResult loadLibrary() {
        if (Build.VERSION.SDK_INT < ExoDv5GpuMappingPolicy.MIN_GPU_MAPPING_API) {
            return new LoadResult(false, "api-below-26");
        }
        try {
            System.loadLibrary("exo_dovi_renderer");
            return new LoadResult(true, "loaded");
        } catch (Throwable error) {
            return new LoadResult(false,
                    "load-" + error.getClass().getSimpleName());
        }
    }

    record Probe(boolean available, int capabilities, String reason) {

        boolean has(int capability) {
            return (capabilities & capability) == capability;
        }
    }

    record Stats(
            long acquiredFrames,
            long ahbFrames,
            long sampledUsageFrames,
            long highDepthFrames,
            long matchedFrames,
            long unmatchedFrames,
            long expectedQueueDrops,
            long lastImageTimestampNs,
            long lastPresentationTimeUs,
            long lastAhbFormat,
            long pendingFrames,
            long parsedRpus,
            long malformedRpus,
            long rpuQueueDrops,
            long pendingRpus,
            long renderedFrames,
            long renderFailures) {

        static Stats empty() {
            return new Stats(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                    0, 0, 0, 0, 0, 0);
        }
    }

    static final class NativeRenderer implements AutoCloseable {

        private long handle;
        @Nullable private Surface inputSurface;

        NativeRenderer(long handle, Surface inputSurface) {
            this.handle = handle;
            this.inputSurface = inputSurface;
        }

        synchronized Surface inputSurface() {
            if (inputSurface == null) throw new IllegalStateException("renderer released");
            return inputSurface;
        }

        synchronized boolean queueFrame(
                long imageTimestampNs, long presentationTimeUs) {
            return handle != 0 && nativeQueueFrame(
                    handle, imageTimestampNs, presentationTimeUs);
        }

        synchronized void clearFrames() {
            if (handle != 0) nativeClearFrames(handle);
        }

        synchronized boolean queueRpu(long presentationTimeUs, byte[] rpu) {
            return handle != 0 && rpu != null && rpu.length >= 2
                    && nativeQueueRpu(handle, presentationTimeUs, rpu);
        }

        synchronized void setOutputSurface(@Nullable Surface surface) {
            if (handle != 0) nativeSetOutputSurface(handle, surface);
        }

        synchronized Stats stats() {
            if (handle == 0) return Stats.empty();
            long[] values = nativeGetStats(handle);
            if (values == null || values.length < 17) return Stats.empty();
            return new Stats(
                    values[0], values[1], values[2], values[3], values[4],
                    values[5], values[6], values[7], values[8], values[9],
                    values[10], values[11], values[12], values[13],
                    values[14], values[15], values[16]);
        }

        @Override
        public synchronized void close() {
            if (handle == 0) return;
            Surface surface = inputSurface;
            inputSurface = null;
            nativeRelease(handle);
            handle = 0;
            if (surface != null) surface.release();
        }
    }

    private record LoadResult(boolean loaded, String reason) {
    }

    private static native int nativeProbeCapabilities();

    private static native long nativeCreate(int width, int height);

    @Nullable private static native Surface nativeGetInputSurface(long handle);

    private static native boolean nativeQueueFrame(
            long handle, long imageTimestampNs, long presentationTimeUs);

    private static native void nativeClearFrames(long handle);

    private static native boolean nativeQueueRpu(
            long handle, long presentationTimeUs, byte[] rpu);

    private static native void nativeSetOutputSurface(
            long handle, @Nullable Surface surface);

    @Nullable private static native long[] nativeGetStats(long handle);

    private static native void nativeRelease(long handle);
}
