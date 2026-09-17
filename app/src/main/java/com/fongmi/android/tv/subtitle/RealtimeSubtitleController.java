package com.fongmi.android.tv.subtitle;

import android.app.ActivityManager;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.Nullable;
import androidx.media3.common.C;
import androidx.media3.common.text.Cue;
import androidx.media3.ui.PlayerView;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.audio.PlaybackMediaAudioProcessor;
import com.fongmi.android.tv.player.audio.PlaybackMediaClock;
import com.fongmi.android.tv.player.audio.PlaybackMediaSignalHub;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleModelCatalog.ModelFile;
import com.fongmi.android.tv.subtitle.RealtimeSubtitleModelCatalog.ModelSpec;
import com.github.catvod.net.OkHttp;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

public final class RealtimeSubtitleController {

    public enum State { OFF, PREPARING, ON, ERROR }

    public interface Listener {
        void onStateChanged(State state, String message);
    }

    public interface ModelDownloadListener {
        void onProgress(int percent, String fileName);
    }

    private static final int AUDIO_QUEUE_CAPACITY = 16;
    private static final long CUE_TICK_MS = 20L;
    private static final long STORAGE_RESERVE_BYTES = 64L * 1024L * 1024L;
    private static final String DOWNLOAD_CANCELLED = "download_cancelled";
    private static final RealtimeSubtitleController INSTANCE = new RealtimeSubtitleController();

    private final ArrayBlockingQueue<AudioChunk> audioQueue = new ArrayBlockingQueue<>(AUDIO_QUEUE_CAPACITY);
    private final ArrayList<ScheduledCue> pendingCues = new ArrayList<>();
    private final AtomicBoolean draining = new AtomicBoolean();
    private final AtomicInteger timelineGeneration = new AtomicInteger();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "realtime-subtitle"));
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object timelineLock = new Object();
    private final Runnable cueTicker = new Runnable() {
        @Override
        public void run() {
            if (!enabled) return;
            tickCues();
            main.postDelayed(this, CUE_TICK_MS);
        }
    };

    private volatile boolean enabled;
    private volatile boolean preparing;
    private volatile boolean modelDownloading;
    private volatile boolean modelDeleting;
    private volatile int modelDownloadProgress;
    private volatile String modelDownloadFile = "";
    private volatile int generation;
    private volatile String activeModelId = "";
    private volatile long latestCapturedUs;
    private WeakReference<PlayerView> playerView = new WeakReference<>(null);
    private WeakReference<PlayerManager> playerManager = new WeakReference<>(null);
    private WeakReference<PlaybackMediaSignalHub> mediaSignals = new WeakReference<>(null);
    private WeakReference<PlaybackMediaClock> mediaClock = new WeakReference<>(null);
    private WeakReference<Listener> listener = new WeakReference<>(null);
    private PlaybackMediaSignalHub.Registration mediaRegistration;
    private PlaybackMediaSignalHub.CaptureLease mediaCaptureLease;
    private RealtimeSubtitleRecognizer recognizer;
    private RealtimeSubtitleTranslator translator;
    private long previousChunkEndUs = C.TIME_UNSET;
    private long capturedTimelineUs;
    private long cueIds;
    private ScheduledCue currentCue;

    public static RealtimeSubtitleController get() {
        return INSTANCE;
    }

    private RealtimeSubtitleController() {
    }

    public synchronized boolean requestAudioPipeline(PlayerManager player) {
        if (player == null || player.isReleased()) return false;
        PlaybackMediaSignalHub hub = player.mediaSignals();
        mediaSignals = new WeakReference<>(hub);
        mediaClock = new WeakReference<>(player.mediaClock());
        if (mediaCaptureLease == null || mediaCaptureLease.isClosed()) {
            mediaCaptureLease = hub.requestCapture(PlaybackMediaSignalHub.ConsumerKind.REALTIME_SUBTITLE);
        }
        if (mediaRegistration == null || mediaRegistration.isClosed()) {
            RealtimeSubtitleMediaConsumer consumer = new RealtimeSubtitleMediaConsumer(
                    new RealtimeSubtitleMediaConsumer.Listener() {
                        @Override
                        public boolean isListening() {
                            return enabled;
                        }

                        @Override
                        public void onPcm(PlaybackMediaSignalHub.PcmFrame frame) {
                            offerAudio(frame);
                        }

                        @Override
                        public void onReset(PlaybackMediaSignalHub.Lifecycle event) {
                            resetMediaTimeline();
                        }
                    });
            mediaRegistration = hub.register("realtime-subtitle", worker,
                    AUDIO_QUEUE_CAPACITY, consumer);
        }
        return !hub.isPipelineAttached();
    }

    public boolean isAudioPipelineReady() {
        PlaybackMediaSignalHub hub = mediaSignals.get();
        return hub != null && hub.isPipelineAttached();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPreparing() {
        return preparing;
    }

    public boolean isModelReady() {
        return isModelReady(selectedModel());
    }

    public boolean isModelReady(String modelId) {
        return isModelReady(findModel(modelId));
    }

    public boolean isModelDownloading() {
        return modelDownloading || preparing && !isModelReady();
    }

    public boolean isModelDeleting() {
        return modelDeleting;
    }

    public int getModelDownloadProgress() {
        return modelDownloadProgress;
    }

    public String getModelDownloadFile() {
        return modelDownloadFile;
    }

    public synchronized void downloadModel(Consumer<String> callback) {
        downloadModel(null, callback);
    }

    public synchronized void downloadModel(@Nullable ModelDownloadListener progress, Consumer<String> callback) {
        if (modelDownloading || modelDeleting) return;
        modelDownloading = true;
        modelDownloadProgress = 0;
        modelDownloadFile = "";
        ModelSpec spec = selectedModel();
        worker.execute(() -> {
            String error = "";
            try {
                ensureModel(spec, progress, () -> false);
            } catch (Throwable e) {
                error = message(e);
            } finally {
                modelDownloading = false;
            }
            complete(callback, error);
        });
    }

    public synchronized void deleteModel(Consumer<String> callback) {
        if (modelDownloading || modelDeleting) return;
        modelDeleting = true;
        ModelSpec spec = selectedModel();
        disable();
        worker.execute(() -> {
            String error = "";
            try {
                if (!deleteModelFiles(spec)) error = App.get().getString(R.string.subtitle_realtime_model_delete_error);
                modelDownloadProgress = 0;
                modelDownloadFile = "";
            } catch (Throwable e) {
                error = message(e);
            } finally {
                modelDeleting = false;
            }
            complete(callback, error);
        });
    }

    public void bind(PlayerView view) {
        playerView = new WeakReference<>(view);
    }

    public void unbind(PlayerView view) {
        if (playerView.get() != view) return;
        disable();
        playerView.clear();
    }

    public void setListener(@Nullable Listener listener) {
        this.listener = new WeakReference<>(listener);
        if (listener != null) listener.onStateChanged(enabled ? State.ON : preparing ? State.PREPARING : State.OFF, "");
    }

    public synchronized void enable(PlayerManager player) {
        if (enabled || preparing || player == null || player.isReleased()) return;
        playerManager = new WeakReference<>(player);
        preparing = true;
        ModelSpec spec = selectedModel();
        modelDownloadProgress = isModelReady(spec) ? 100 : 0;
        modelDownloadFile = "";
        int request = ++generation;
        resetTimeline(false);
        notifyState(request, State.PREPARING, "");
        worker.execute(() -> prepareModel(request, spec, false));
    }

    public synchronized void switchModel(PlayerManager player) {
        if (player == null || player.isReleased() || !enabled && !preparing) return;
        playerManager = new WeakReference<>(player);
        enabled = false;
        preparing = true;
        ModelSpec spec = selectedModel();
        modelDownloadProgress = isModelReady(spec) ? 100 : 0;
        modelDownloadFile = "";
        int request = ++generation;
        timelineGeneration.incrementAndGet();
        audioQueue.clear();
        main.removeCallbacks(cueTicker);
        resetTimeline(false);
        notifyState(request, State.PREPARING, "");
        worker.execute(() -> prepareModel(request, spec, true));
    }

    private void prepareModel(int request, ModelSpec spec, boolean replacing) {
        RealtimeSubtitleRecognizer previousRecognizer = replacing ? recognizer : null;
        RealtimeSubtitleTranslator previousTranslator = replacing ? translator : null;
        try {
            if (replacing) resetRecognizer(previousRecognizer, previousTranslator);
            ensureModel(spec, (percent, fileName) -> notifyState(request, State.PREPARING, String.valueOf(percent)), () -> request != generation);
            if (request != generation) return;
            validateMemory(spec);
            if (!replacing) releaseRecognizer();
            RealtimeSubtitleRecognizer createdRecognizer = RealtimeSubtitleRecognizer.create(modelDirectory(spec), vadFile(), spec, new RealtimeSubtitleRecognizer.Listener() {
                @Override
                public void onResult(String text, long startUs, long endUs, int timelineToken) {
                    if (request != generation) return;
                    translateFinalResult(spec.id(), text, startUs, endUs, timelineToken);
                }

                @Override
                public void onError(Throwable error) {
                    if (request != generation) return;
                    worker.execute(() -> {
                        if (request != generation) return;
                        failRecognizer(error, request);
                    });
                }
            });
            RealtimeSubtitleTranslator createdTranslator;
            try {
                createdTranslator = new RealtimeSubtitleTranslator(AiConfig.objectFrom(Setting.getAiConfig()));
            } catch (Throwable error) {
                createdRecognizer.release();
                throw error;
            }
            synchronized (this) {
                if (request != generation) {
                    createdRecognizer.release();
                    createdTranslator.release();
                    return;
                }
                recognizer = createdRecognizer;
                translator = createdTranslator;
                activeModelId = spec.id();
                previousChunkEndUs = C.TIME_UNSET;
                enabled = true;
                preparing = false;
            }
            releaseRecognizer(previousRecognizer, previousTranslator);
            main.post(() -> {
                if (request != generation || !enabled) return;
                disableNativeSubtitle();
                startCueTicker();
            });
            notifyState(request, State.ON, "");
        } catch (Throwable e) {
            if (request != generation || DOWNLOAD_CANCELLED.equals(e.getMessage())) return;
            if (replacing && (previousRecognizer != null || previousTranslator != null)) {
                synchronized (this) {
                    if (request != generation) return;
                    enabled = true;
                    preparing = false;
                    if (!TextUtils.isEmpty(activeModelId)) Setting.putRealtimeSubtitleModel(activeModelId);
                }
                main.post(() -> {
                    if (request != generation || !enabled) return;
                    startCueTicker();
                });
                notifyState(request, State.ON, message(e));
                return;
            }
            enabled = false;
            preparing = false;
            releaseMediaSignals();
            releaseRecognizer();
            main.post(() -> restorePlayback(true));
            notifyState(request, State.ERROR, message(e));
        }
    }

    public synchronized void disable() {
        disable(true);
    }

    private synchronized void disable(boolean rebuildAudioPipeline) {
        generation++;
        timelineGeneration.incrementAndGet();
        enabled = false;
        preparing = false;
        releaseMediaSignals();
        audioQueue.clear();
        main.removeCallbacks(cueTicker);
        runOnMain(() -> restorePlayback(rebuildAudioPipeline));
        worker.execute(this::releaseRecognizer);
        notifyState(State.OFF, "");
    }

    private void offerAudio(PlaybackMediaSignalHub.PcmFrame frame) {
        float[] samples = frame.monoSamples();
        int sampleRate = frame.sampleRate();
        if (!enabled || samples.length == 0 || sampleRate <= 0) return;
        AudioChunk chunk = createAudioChunk(frame, false);
        if (!audioQueue.offer(chunk)) {
            audioQueue.clear();
            timelineGeneration.incrementAndGet();
            resetTimeline(false);
            chunk = createAudioChunk(frame, true);
            audioQueue.offer(chunk);
        }
        drainAudio();
    }

    private AudioChunk createAudioChunk(PlaybackMediaSignalHub.PcmFrame frame, boolean discontinuity) {
        synchronized (timelineLock) {
            long startUs = millisecondsToMicroseconds(frame.captureStartTimeMs());
            long durationUs = Math.max(1L,
                    frame.monoSamples().length * 1_000_000L / frame.sampleRate());
            long endUs = startUs > Long.MAX_VALUE - durationUs ? Long.MAX_VALUE : startUs + durationUs;
            capturedTimelineUs = endUs;
            latestCapturedUs = endUs;
            return new AudioChunk(frame.monoSamples(), frame.sampleRate(), startUs, endUs,
                    generation, timelineGeneration.get(), discontinuity);
        }
    }

    private void resetMediaTimeline() {
        audioQueue.clear();
        timelineGeneration.incrementAndGet();
        resetTimeline(false);
        if (enabled) worker.execute(this::resetStream);
    }

    private void drainAudio() {
        if (!draining.compareAndSet(false, true)) return;
        int drainGeneration = generation;
        worker.execute(() -> {
            try {
                AudioChunk chunk;
                while (enabled && (chunk = audioQueue.poll()) != null) {
                    if (chunk.generation() == generation && chunk.timelineGeneration() == timelineGeneration.get()) transcribe(chunk);
                }
            } catch (Throwable e) {
                failRecognizer(e, drainGeneration);
            } finally {
                draining.set(false);
                if (enabled && !audioQueue.isEmpty()) drainAudio();
            }
        });
    }

    private void transcribe(AudioChunk chunk) {
        if (recognizer == null) return;
        if (chunk.discontinuity() || previousChunkEndUs != C.TIME_UNSET && Math.abs(chunk.startUs() - previousChunkEndUs) > 50_000L) resetStream();
        previousChunkEndUs = chunk.endUs();
        float[] samples = PlaybackMediaAudioProcessor.resample(chunk.samples(), chunk.sampleRate(), 16_000);
        if (samples.length == 0) return;
        recognizer.accept(samples, chunk.startUs(), chunk.endUs(), chunk.timelineGeneration());
    }

    private void translateFinalResult(String sourceLanguage, String text, long startUs, long endUs, int timelineToken) {
        RealtimeSubtitleTranslator current = translator;
        if (current == null) {
            enqueueCue(text, startUs, endUs, timelineToken);
            return;
        }
        current.translate(sourceLanguage, text, translated -> enqueueCue(translated, startUs, endUs, timelineToken));
    }

    private synchronized void failRecognizer(Throwable error, int expectedGeneration) {
        synchronized (this) {
            if (expectedGeneration != generation) return;
            if (!enabled && !preparing) return;
            generation++;
            timelineGeneration.incrementAndGet();
            enabled = false;
            preparing = false;
            releaseMediaSignals();
        }
        audioQueue.clear();
        releaseRecognizer();
        main.post(() -> restorePlayback(true));
        notifyState(State.ERROR, message(error));
    }

    private void enqueueCue(String text, long startUs, long endUs, int timelineToken) {
        main.post(() -> {
            if (!enabled || timelineToken != timelineGeneration.get()) return;
            long headroomUs = readAudioHeadroomUs(SystemClock.elapsedRealtime());
            if (headroomUs == C.TIME_UNSET) return;
            RealtimeSubtitleTimeline.CueWindow window = RealtimeSubtitleTimeline.planCueWindow(startUs, endUs, latestCapturedUs, headroomUs);
            if (window == null) return;
            pendingCues.add(new ScheduledCue(++cueIds, window.startUs(), window.endUs(), text));
            pendingCues.sort(Comparator.comparingLong(ScheduledCue::startUs));
            tickCues();
        });
    }

    private void startCueTicker() {
        main.removeCallbacks(cueTicker);
        main.post(cueTicker);
    }

    private void tickCues() {
        long headroomUs = readAudioHeadroomUs(SystemClock.elapsedRealtime());
        if (headroomUs == C.TIME_UNSET) return;
        long presentedUs = RealtimeSubtitleTimeline.presentedPositionUs(latestCapturedUs, headroomUs);
        if (currentCue != null && presentedUs >= currentCue.endUs()) {
            currentCue = null;
            showCue("");
        }
        pendingCues.removeIf(cue -> presentedUs >= cue.endUs());
        ScheduledCue ready = null;
        for (ScheduledCue cue : pendingCues) {
            if (cue.startUs() <= presentedUs) ready = cue;
            else break;
        }
        if (ready == null) return;
        long readyId = ready.id();
        pendingCues.removeIf(cue -> cue.startUs() <= presentedUs);
        if (currentCue != null && currentCue.id() == readyId) return;
        currentCue = ready;
        showCue(ready.text());
    }

    private long readAudioHeadroomUs(long elapsedNowMs) {
        PlaybackMediaClock clock = mediaClock.get();
        if (clock == null) return C.TIME_UNSET;
        PlaybackMediaClock.Snapshot snapshot = clock.snapshot(elapsedNowMs);
        if (!snapshot.fresh()) return C.TIME_UNSET;
        long presentedUs = snapshot.presentedCaptureMs() > Long.MAX_VALUE / 1_000L
                ? Long.MAX_VALUE : snapshot.presentedCaptureMs() * 1_000L;
        return Math.max(0L, latestCapturedUs - presentedUs);
    }

    private void resetTimeline(boolean clearClock) {
        synchronized (timelineLock) {
            capturedTimelineUs = 0L;
            latestCapturedUs = 0L;
        }
        runOnMain(this::clearCues);
    }

    private static long millisecondsToMicroseconds(long milliseconds) {
        if (milliseconds <= 0L) return 0L;
        return milliseconds > Long.MAX_VALUE / 1_000L
                ? Long.MAX_VALUE : milliseconds * 1_000L;
    }

    private void clearCues() {
        pendingCues.clear();
        currentCue = null;
        showCue("");
    }

    private void showCue(String text) {
        PlayerView view = playerView.get();
        if (view == null || view.getSubtitleView() == null) return;
        view.getSubtitleView().setCues(text.isEmpty() ? Collections.emptyList() : Collections.singletonList(new Cue.Builder().setText(text).build()));
    }

    private boolean isModelReady(ModelSpec spec) {
        for (ModelFile model : RealtimeSubtitleModelCatalog.downloads(spec)) {
            File target = modelFile(spec, model);
            if (!RealtimeSubtitleModelVerifier.isVerified(target, model.size(), model.sha256())) return false;
        }
        return true;
    }

    private File ensureModel(ModelSpec spec, @Nullable ModelDownloadListener progress, BooleanSupplier cancelled) throws IOException {
        validateMemory(spec);
        File root = modelRoot();
        if (!root.exists() && !root.mkdirs()) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_directory_error));
        File dir = modelDirectory(spec);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_directory_error));
        ModelFile[] downloads = RealtimeSubtitleModelCatalog.downloads(spec);
        for (ModelFile model : downloads) {
            File target = modelFile(spec, model);
            if (!RealtimeSubtitleModelVerifier.isVerified(target, model.size(), model.sha256()) && target.length() == model.size()) {
                RealtimeSubtitleModelVerifier.verify(target, model.size(), model.sha256());
            }
        }
        validateStorage(spec);
        long total = totalBytes(spec);
        long completed = 0L;
        for (ModelFile model : downloads) {
            File target = modelFile(spec, model);
            if (RealtimeSubtitleModelVerifier.isVerified(target, model.size(), model.sha256())) {
                completed += model.size();
                continue;
            }
            download(model.url(), target, model, completed, total, progress, cancelled);
            completed += model.size();
        }
        reportProgress(progress, 100, "");
        return dir;
    }

    private void download(String url, File target, ModelFile model, long completed, long total, @Nullable ModelDownloadListener progress, BooleanSupplier cancelled) throws IOException {
        long expectedSize = model.size();
        File part = new File(target.getPath() + ".part");
        long offset = part.isFile() ? part.length() : 0L;
        if (offset > expectedSize) {
            deletePartial(part);
            offset = 0L;
        }
        if (offset == expectedSize) {
            if (installPart(part, target, model)) {
                reportProgress(progress, (int) Math.min(99L, (completed + expectedSize) * 100L / Math.max(1L, total)), target.getName());
                return;
            }
            deletePartial(part);
            offset = 0L;
        }
        Request.Builder builder = new Request.Builder().url(url).header("User-Agent", "webhtv-realtime-subtitle");
        if (offset > 0L) builder.header("Range", "bytes=" + offset + "-");
        try (Response response = OkHttp.client().newCall(builder.build()).execute()) {
            ResponseBody body = response.body();
            boolean append = offset > 0L && response.code() == 206;
            if (!response.isSuccessful() || body == null) throw new IOException("Model download failed: HTTP " + response.code());
            if (append) {
                String contentRange = response.header("Content-Range", "");
                if (!contentRange.startsWith("bytes " + offset + "-")) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_partial_error));
            }
            if (offset > 0L && !append) {
                deletePartial(part);
                offset = 0L;
            }
            try (InputStream input = body.byteStream(); FileOutputStream output = new FileOutputStream(part, append)) {
                byte[] buffer = new byte[64 * 1024];
                long current = offset;
                int lastPercent = -1;
                int length;
                while ((length = input.read(buffer)) != -1) {
                    if (cancelled.getAsBoolean()) throw new InterruptedIOException(DOWNLOAD_CANCELLED);
                    output.write(buffer, 0, length);
                    current += length;
                    int percent = (int) Math.min(99L, (completed + current) * 100L / Math.max(1L, total));
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        reportProgress(progress, percent, target.getName());
                    }
                }
                output.getFD().sync();
            }
        }
        if (part.length() != expectedSize) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_incomplete, target.getName()));
        if (!installPart(part, target, model)) {
            deletePartial(part);
            throw new IOException(App.get().getString(R.string.subtitle_realtime_model_checksum_error, target.getName()));
        }
    }

    private boolean installPart(File part, File target, ModelFile model) throws IOException {
        if (!RealtimeSubtitleModelVerifier.verify(part, model.size(), model.sha256())) return false;
        RealtimeSubtitleModelVerifier.deleteMarker(target);
        if (target.exists() && !target.delete() || !part.renameTo(target)) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_install_error, target.getName()));
        RealtimeSubtitleModelVerifier.deleteMarker(part);
        RealtimeSubtitleModelVerifier.markVerified(target, model.size(), model.sha256());
        return true;
    }

    private void deletePartial(File part) throws IOException {
        RealtimeSubtitleModelVerifier.deleteMarker(part);
        if (part.exists() && !part.delete()) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_partial_error));
    }

    private void reportProgress(@Nullable ModelDownloadListener listener, int percent, String fileName) {
        int currentProgress = Math.max(0, Math.min(100, percent));
        String currentFile = fileName == null ? "" : fileName;
        modelDownloadProgress = currentProgress;
        modelDownloadFile = currentFile;
        if (listener != null) main.post(() -> listener.onProgress(currentProgress, currentFile));
    }

    private void validateMemory(ModelSpec spec) throws IOException {
        ActivityManager manager = (ActivityManager) App.get().getSystemService(android.content.Context.ACTIVITY_SERVICE);
        if (manager == null) return;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        long totalMb = info.totalMem / (1024L * 1024L);
        if (totalMb < spec.minRamMb()) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_memory_low, spec.minRamMb()));
        if (info.lowMemory) throw new IOException(App.get().getString(R.string.subtitle_realtime_model_memory_available_low));
    }

    private void validateStorage(ModelSpec spec) throws IOException {
        long required = missingBytes(spec);
        if (required <= 0L) return;
        StatFs stat = new StatFs(App.get().getFilesDir().getAbsolutePath());
        if (stat.getAvailableBytes() < required + STORAGE_RESERVE_BYTES) {
            long requiredMb = (required + STORAGE_RESERVE_BYTES + 1024L * 1024L - 1L) / (1024L * 1024L);
            throw new IOException(App.get().getString(R.string.subtitle_realtime_model_storage_low, requiredMb));
        }
    }

    private long missingBytes(ModelSpec spec) {
        long bytes = 0L;
        for (ModelFile model : RealtimeSubtitleModelCatalog.downloads(spec)) {
            File target = modelFile(spec, model);
            if (RealtimeSubtitleModelVerifier.isVerified(target, model.size(), model.sha256())) continue;
            File part = new File(target.getPath() + ".part");
            bytes += Math.max(0L, model.size() - Math.min(model.size(), part.length()));
        }
        return bytes;
    }

    private long totalBytes(ModelSpec spec) {
        long bytes = 0L;
        for (ModelFile model : RealtimeSubtitleModelCatalog.downloads(spec)) bytes += model.size();
        return bytes;
    }

    private void resetStream() {
        if (recognizer != null) recognizer.reset();
        if (translator != null) translator.reset();
        previousChunkEndUs = C.TIME_UNSET;
    }

    private void releaseRecognizer() {
        RealtimeSubtitleRecognizer currentRecognizer = recognizer;
        RealtimeSubtitleTranslator currentTranslator = translator;
        recognizer = null;
        translator = null;
        releaseRecognizer(currentRecognizer, currentTranslator);
    }

    private void resetRecognizer(RealtimeSubtitleRecognizer recognizer, RealtimeSubtitleTranslator translator) {
        if (translator != null) translator.reset();
        if (recognizer != null) recognizer.reset();
    }

    private void releaseRecognizer(RealtimeSubtitleRecognizer recognizer, RealtimeSubtitleTranslator translator) {
        if (translator != null) translator.release();
        if (recognizer != null) recognizer.release();
        previousChunkEndUs = C.TIME_UNSET;
    }

    private ModelSpec selectedModel() {
        return RealtimeSubtitleModelCatalog.find(Setting.getRealtimeSubtitleModel());
    }

    private ModelSpec findModel(String modelId) {
        return RealtimeSubtitleModelCatalog.find(modelId);
    }

    private File modelRoot() {
        return new File(App.get().getFilesDir(), "realtime_subtitle");
    }

    private File modelDirectory(ModelSpec spec) {
        return new File(modelRoot(), spec.directory());
    }

    private File vadFile() {
        return new File(modelRoot(), RealtimeSubtitleModelCatalog.vad().relativePath());
    }

    private File modelFile(ModelSpec spec, ModelFile model) {
        File parent = model.equals(RealtimeSubtitleModelCatalog.vad()) ? modelRoot() : modelDirectory(spec);
        return new File(parent, model.relativePath());
    }

    private boolean deleteModelFiles(ModelSpec spec) {
        File dir = modelDirectory(spec);
        boolean deleted = true;
        for (ModelFile model : spec.files()) {
            File target = modelFile(spec, model);
            File part = new File(target.getPath() + ".part");
            RealtimeSubtitleModelVerifier.deleteMarker(target);
            RealtimeSubtitleModelVerifier.deleteMarker(part);
            if (target.exists()) deleted &= target.delete();
            if (part.exists()) deleted &= part.delete();
        }
        return (!dir.exists() || dir.delete()) && deleted;
    }

    private String message(Throwable error) {
        return TextUtils.isEmpty(error.getMessage()) ? error.getClass().getSimpleName() : error.getMessage();
    }

    private void complete(Consumer<String> callback, String error) {
        if (callback != null) main.post(() -> callback.accept(error));
    }

    private void disableNativeSubtitle() {
        PlayerManager player = playerManager.get();
        if (player != null && !player.isReleased()) player.disableSubtitleTrackForRealtime();
    }

    private void restoreNativeSubtitle() {
        PlayerManager player = playerManager.get();
        if (player != null && !player.isReleased()) player.restoreSubtitleTrackAfterRealtime();
        playerManager.clear();
    }

    private synchronized void releaseMediaSignals() {
        if (mediaRegistration != null) {
            mediaRegistration.close();
            mediaRegistration = null;
        }
        if (mediaCaptureLease != null) {
            mediaCaptureLease.close();
            mediaCaptureLease = null;
        }
        mediaSignals.clear();
        mediaClock.clear();
    }

    private void restorePlayback(boolean rebuildAudioPipeline) {
        clearCues();
        PlayerManager player = playerManager.get();
        PlaybackMediaSignalHub hub = player == null ? null : player.mediaSignals();
        if (rebuildAudioPipeline && hub != null && hub.isPipelineAttached()
                && !hub.isCaptureRequested() && player != null && !player.isReleased()) {
            player.rebuildAudioPipeline();
        }
        restoreNativeSubtitle();
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(action);
    }

    private void notifyState(int expectedGeneration, State state, String message) {
        main.post(() -> {
            if (expectedGeneration != generation) return;
            Listener callback = listener.get();
            if (callback != null) callback.onStateChanged(state, message == null ? "" : message);
        });
    }

    private void notifyState(State state, String message) {
        main.post(() -> {
            Listener callback = listener.get();
            if (callback != null) callback.onStateChanged(state, message == null ? "" : message);
        });
    }

    private record AudioChunk(float[] samples, int sampleRate, long startUs, long endUs, int generation, int timelineGeneration, boolean discontinuity) {
    }

    private record ScheduledCue(long id, long startUs, long endUs, String text) {
    }
}
