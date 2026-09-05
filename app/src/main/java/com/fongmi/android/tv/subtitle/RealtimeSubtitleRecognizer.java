package com.fongmi.android.tv.subtitle;

import com.k2fsa.sherpa.onnx.EndpointConfig;
import com.k2fsa.sherpa.onnx.EndpointRule;
import com.k2fsa.sherpa.onnx.FeatureConfig;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizerResult;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWenetCtcModelConfig;
import com.k2fsa.sherpa.onnx.OnlineModelConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizer;
import com.k2fsa.sherpa.onnx.OnlineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OnlineRecognizerResult;
import com.k2fsa.sherpa.onnx.OnlineStream;
import com.k2fsa.sherpa.onnx.OnlineTransducerModelConfig;
import com.k2fsa.sherpa.onnx.SileroVadModelConfig;
import com.k2fsa.sherpa.onnx.SpeechSegment;
import com.k2fsa.sherpa.onnx.Vad;
import com.k2fsa.sherpa.onnx.VadModelConfig;

import java.io.File;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

final class RealtimeSubtitleRecognizer {

    interface Listener {
        void onResult(String text, long startUs, long endUs, int timelineToken);

        void onError(Throwable error);
    }

    private static final int SAMPLE_RATE = 16_000;
    private static final int MIN_SPEECH_SAMPLES = 3_200;
    private static final int SPEECH_QUEUE_CAPACITY = 3;
    private static final long MAX_UTTERANCE_US = 4_800_000L;
    private static final long UNSET_US = Long.MIN_VALUE;

    private final RealtimeSubtitleModelCatalog.ModelSpec spec;
    private final Listener listener;
    private final ArrayBlockingQueue<SpeechChunk> speechQueue = new ArrayBlockingQueue<>(SPEECH_QUEUE_CAPACITY);
    private final AtomicInteger decodeGeneration = new AtomicInteger();
    private OnlineRecognizer onlineRecognizer;
    private OnlineStream onlineStream;
    private OfflineRecognizer offlineRecognizer;
    private Vad vad;
    private ExecutorService recognitionExecutor;
    private Future<?> recognitionFuture;
    private volatile boolean released;
    private long streamBaseUs = UNSET_US;
    private long vadBaseUs = UNSET_US;
    private int speechSamples;

    static RealtimeSubtitleRecognizer create(File modelDir, File vadFile, RealtimeSubtitleModelCatalog.ModelSpec spec, Listener listener) {
        return new RealtimeSubtitleRecognizer(modelDir, vadFile, spec, listener);
    }

    static boolean isStreaming(RealtimeSubtitleModelCatalog.ModelSpec spec) {
        return spec.engine() == RealtimeSubtitleModelCatalog.Engine.ONLINE_TRANSDUCER;
    }

    static String onlineModelType(RealtimeSubtitleModelCatalog.ModelSpec spec) {
        return switch (spec.id()) {
            case "de", "fr", "es" -> "zipformer2";
            default -> "zipformer";
        };
    }

    static int offlineFlushSamples(RealtimeSubtitleModelCatalog.ModelSpec spec) {
        return isStreaming(spec) ? 0 : (int) (SAMPLE_RATE * 2.2f);
    }

    private RealtimeSubtitleRecognizer(File modelDir, File vadFile, RealtimeSubtitleModelCatalog.ModelSpec spec, Listener listener) {
        this.spec = spec;
        this.listener = listener;
        try {
            if (isStreaming(spec)) {
                onlineRecognizer = new OnlineRecognizer(buildOnlineRecognizer(modelDir, spec));
                onlineStream = onlineRecognizer.createStream();
            } else {
                offlineRecognizer = new OfflineRecognizer(buildOfflineRecognizer(modelDir, spec));
                vad = new Vad(buildVad(vadFile));
                recognitionExecutor = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "realtime-subtitle-offline-asr");
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                });
                recognitionFuture = recognitionExecutor.submit(this::recognizeLoop);
            }
        } catch (RuntimeException | Error error) {
            release();
            throw error;
        }
    }

    void accept(float[] samples, long startUs, long endUs, int timelineToken) {
        if (released || samples == null || samples.length == 0 || endUs <= startUs) return;
        if (isStreaming(spec)) acceptStreaming(samples, startUs, endUs, timelineToken);
        else acceptOffline(samples, startUs, timelineToken);
    }

    void reset() {
        if (released) return;
        decodeGeneration.incrementAndGet();
        speechQueue.clear();
        streamBaseUs = UNSET_US;
        vadBaseUs = UNSET_US;
        speechSamples = 0;
        if (onlineStream != null) {
            onlineStream.release();
            onlineStream = onlineRecognizer.createStream();
        }
        if (vad != null) vad.reset();
    }

    void release() {
        if (released) return;
        released = true;
        decodeGeneration.incrementAndGet();
        speechQueue.clear();
        if (recognitionFuture != null) {
            try {
                recognitionFuture.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Throwable ignored) {
            }
        }
        if (recognitionExecutor != null) recognitionExecutor.shutdownNow();
        if (onlineStream != null) onlineStream.release();
        if (onlineRecognizer != null) onlineRecognizer.release();
        if (vad != null) vad.release();
        if (offlineRecognizer != null) offlineRecognizer.release();
        onlineStream = null;
        onlineRecognizer = null;
        vad = null;
        offlineRecognizer = null;
    }

    private void acceptStreaming(float[] samples, long startUs, long endUs, int timelineToken) {
        if (streamBaseUs == UNSET_US) streamBaseUs = startUs;
        onlineStream.acceptWaveform(samples, SAMPLE_RATE);
        while (!released && onlineRecognizer.isReady(onlineStream)) onlineRecognizer.decode(onlineStream);
        boolean endpoint = onlineRecognizer.isEndpoint(onlineStream);
        boolean adaptiveEndpoint = endUs - streamBaseUs >= MAX_UTTERANCE_US;
        if (!endpoint && !adaptiveEndpoint) return;
        emitStreamingResult(onlineRecognizer.getResult(onlineStream), streamBaseUs, endUs, timelineToken);
        onlineRecognizer.reset(onlineStream);
        streamBaseUs = endUs;
    }

    private void emitStreamingResult(OnlineRecognizerResult result, long baseUs, long currentEndUs, int timelineToken) {
        if (released || result == null) return;
        String text = result.getText() == null ? "" : result.getText().trim();
        if (!containsSpeechText(text)) return;
        float[] timestamps = result.getTimestamps();
        long startUs = baseUs;
        long endUs = currentEndUs;
        if (timestamps != null && timestamps.length > 0) {
            startUs = baseUs + Math.max(0L, (long) (timestamps[0] * 1_000_000L));
            endUs = Math.min(currentEndUs, baseUs + Math.max(0L, (long) ((timestamps[timestamps.length - 1] + 0.32f) * 1_000_000L)));
        }
        if (endUs <= startUs) {
            endUs = currentEndUs;
            startUs = Math.max(baseUs, endUs - 800_000L);
        }
        listener.onResult(text, startUs, endUs, timelineToken);
    }

    private void acceptOffline(float[] samples, long startUs, int timelineToken) {
        if (vadBaseUs == UNSET_US) vadBaseUs = startUs;
        vad.acceptWaveform(samples);
        speechSamples = vad.isSpeechDetected() ? speechSamples + samples.length : 0;
        if (speechSamples >= offlineFlushSamples(spec)) {
            vad.flush();
            speechSamples = 0;
        }
        int token = decodeGeneration.get();
        while (!vad.empty() && !released) {
            SpeechSegment segment = vad.front();
            vad.pop();
            float[] speech = segment.getSamples();
            if (speech.length < MIN_SPEECH_SAMPLES) continue;
            long speechStartUs = vadBaseUs + segment.getStart() * 1_000_000L / SAMPLE_RATE;
            long speechEndUs = speechStartUs + speech.length * 1_000_000L / SAMPLE_RATE;
            offerSpeech(new SpeechChunk(speech, speechStartUs, speechEndUs, timelineToken, token));
        }
    }

    private void offerSpeech(SpeechChunk speech) {
        if (speechQueue.offer(speech)) return;
        speechQueue.poll();
        speechQueue.offer(speech);
    }

    private void recognizeLoop() {
        try {
            while (!released) {
                SpeechChunk speech = speechQueue.poll(120, TimeUnit.MILLISECONDS);
                if (speech == null) continue;
                SpeechChunk newer;
                while ((newer = speechQueue.poll()) != null) speech = newer;
                recognize(speech);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Throwable error) {
            if (!released) listener.onError(error);
        }
    }

    private void recognize(SpeechChunk speech) {
        OfflineStream stream = offlineRecognizer.createStream();
        try {
            stream.acceptWaveform(speech.samples(), SAMPLE_RATE);
            offlineRecognizer.decode(stream);
            OfflineRecognizerResult result = offlineRecognizer.getResult(stream);
            if (released || speech.decodeToken() != decodeGeneration.get() || result == null) return;
            String text = result.getText() == null ? "" : result.getText().trim();
            if (containsSpeechText(text)) listener.onResult(text, speech.startUs(), speech.endUs(), speech.timelineToken());
        } finally {
            stream.release();
        }
    }

    private static OnlineRecognizerConfig buildOnlineRecognizer(File modelDir, RealtimeSubtitleModelCatalog.ModelSpec spec) {
        RealtimeSubtitleModelCatalog.ModelFile[] files = spec.files();
        OnlineTransducerModelConfig transducer = OnlineTransducerModelConfig.builder()
                .setEncoder(new File(modelDir, files[0].relativePath()).getPath())
                .setDecoder(new File(modelDir, files[1].relativePath()).getPath())
                .setJoiner(new File(modelDir, files[2].relativePath()).getPath())
                .build();
        OnlineModelConfig model = OnlineModelConfig.builder()
                .setTransducer(transducer)
                .setTokens(new File(modelDir, files[3].relativePath()).getPath())
                .setModelType(onlineModelType(spec))
                .setNumThreads(threadCount())
                .setProvider("cpu")
                .setDebug(false)
                .build();
        EndpointConfig endpoint = EndpointConfig.builder()
                .setRule1(EndpointRule.builder().setMustContainNonSilence(false).setMinTrailingSilence(2.4f).setMinUtteranceLength(0f).build())
                .setRule2(EndpointRule.builder().setMustContainNonSilence(true).setMinTrailingSilence(0.36f).setMinUtteranceLength(0f).build())
                .setRule3(EndpointRule.builder().setMustContainNonSilence(false).setMinTrailingSilence(0f).setMinUtteranceLength(MAX_UTTERANCE_US / 1_000_000f).build())
                .build();
        return OnlineRecognizerConfig.builder()
                .setFeatureConfig(featureConfig())
                .setOnlineModelConfig(model)
                .setEndpointConfig(endpoint)
                .setEnableEndpoint(true)
                .setDecodingMethod("greedy_search")
                .setMaxActivePaths(4)
                .build();
    }

    private static OfflineRecognizerConfig buildOfflineRecognizer(File modelDir, RealtimeSubtitleModelCatalog.ModelSpec spec) {
        RealtimeSubtitleModelCatalog.ModelFile[] files = spec.files();
        OfflineModelConfig.Builder model = OfflineModelConfig.builder()
                .setNumThreads(threadCount())
                .setProvider("cpu")
                .setDebug(false);
        switch (spec.engine()) {
            case OFFLINE_WENET_CTC -> model
                    .setWenetCtc(OfflineWenetCtcModelConfig.builder()
                            .setModel(new File(modelDir, files[0].relativePath()).getPath())
                            .build())
                    .setTokens(new File(modelDir, files[1].relativePath()).getPath())
                    .setModelType("wenet_ctc");
            case OFFLINE_MOONSHINE -> model
                    .setMoonshine(OfflineMoonshineModelConfig.builder()
                            .setEncoder(new File(modelDir, files[0].relativePath()).getPath())
                            .setMergedDecoder(new File(modelDir, files[1].relativePath()).getPath())
                            .build())
                    .setTokens(new File(modelDir, files[2].relativePath()).getPath())
                    .setModelType("moonshine");
            default -> throw new IllegalArgumentException("Unsupported offline engine: " + spec.engine());
        }
        return OfflineRecognizerConfig.builder()
                .setFeatureConfig(featureConfig())
                .setOfflineModelConfig(model.build())
                .build();
    }

    private static VadModelConfig buildVad(File vadFile) {
        SileroVadModelConfig silero = SileroVadModelConfig.builder()
                .setModel(vadFile.getPath())
                .setThreshold(0.5f)
                .setMinSilenceDuration(0.3f)
                .setMinSpeechDuration(0.25f)
                .setWindowSize(512)
                .setMaxSpeechDuration(3.0f)
                .build();
        return VadModelConfig.builder()
                .setSileroVadModelConfig(silero)
                .setSampleRate(SAMPLE_RATE)
                .setNumThreads(1)
                .setProvider("cpu")
                .setDebug(false)
                .build();
    }

    private static FeatureConfig featureConfig() {
        return FeatureConfig.builder()
                .setSampleRate(SAMPLE_RATE)
                .setFeatureDim(80)
                .setDither(0)
                .build();
    }

    private static int threadCount() {
        return Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));
    }

    private static boolean containsSpeechText(String text) {
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (Character.isLetterOrDigit(codePoint)) return true;
            i += Character.charCount(codePoint);
        }
        return false;
    }

    private record SpeechChunk(float[] samples, long startUs, long endUs, int timelineToken, int decodeToken) {
    }
}
