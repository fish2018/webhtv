package com.fongmi.android.tv.subtitle;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.Setting;

import java.io.File;
import java.util.Objects;

public final class RealtimeSubtitleSpeechRecognitionFactory implements SpeechRecognitionFactory {

    private final File root;
    private final RealtimeSubtitleModelCatalog.ModelSpec spec;

    public RealtimeSubtitleSpeechRecognitionFactory() {
        this(defaultRoot(), selectedModel());
    }

    private RealtimeSubtitleSpeechRecognitionFactory(
            File root, RealtimeSubtitleModelCatalog.ModelSpec spec) {
        this.root = Objects.requireNonNull(root, "root");
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    public static RealtimeSubtitleSpeechRecognitionFactory forRoot(File root) {
        return new RealtimeSubtitleSpeechRecognitionFactory(root, selectedModel());
    }

    public static boolean isSelectedModelReady() {
        return new RealtimeSubtitleSpeechRecognitionFactory().isReady();
    }

    @Override
    public boolean isReady() {
        for (RealtimeSubtitleModelCatalog.ModelFile model
                : RealtimeSubtitleModelCatalog.downloads(spec)) {
            File target = modelFile(model);
            if (!RealtimeSubtitleModelVerifier.isVerified(
                    target, model.size(), model.sha256())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public Session create(Listener listener) {
        Objects.requireNonNull(listener, "listener");
        if (!isReady()) throw new IllegalStateException("speech model is not ready");
        RealtimeSubtitleRecognizer recognizer = RealtimeSubtitleRecognizer.create(
                modelDirectory(), vadFile(), spec, new RealtimeSubtitleRecognizer.Listener() {
                    @Override
                    public void onResult(String text, long startUs, long endUs,
                                         int timelineToken) {
                        listener.onResult(text, startUs, endUs, timelineToken);
                    }

                    @Override
                    public void onError(Throwable error) {
                        listener.onError(error);
                    }
                });
        return new Session() {
            private boolean closed;

            @Override
            public void accept(float[] samples, long startUs, long endUs,
                               int timelineToken) {
                if (!closed) recognizer.accept(samples, startUs, endUs, timelineToken);
            }

            @Override
            public void reset() {
                if (!closed) recognizer.reset();
            }

            @Override
            public void close() {
                if (closed) return;
                closed = true;
                recognizer.release();
            }
        };
    }

    private File modelFile(RealtimeSubtitleModelCatalog.ModelFile model) {
        File parent = model.equals(RealtimeSubtitleModelCatalog.vad())
                ? root : modelDirectory();
        return new File(parent, model.relativePath());
    }

    private File modelDirectory() {
        return new File(root, spec.directory());
    }

    private File vadFile() {
        return new File(root, RealtimeSubtitleModelCatalog.vad().relativePath());
    }

    private static File defaultRoot() {
        return new File(App.get().getFilesDir(), "realtime_subtitle");
    }

    private static RealtimeSubtitleModelCatalog.ModelSpec selectedModel() {
        return RealtimeSubtitleModelCatalog.find(Setting.getRealtimeSubtitleModel());
    }
}