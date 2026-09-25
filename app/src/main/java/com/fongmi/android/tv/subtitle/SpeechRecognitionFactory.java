package com.fongmi.android.tv.subtitle;

public interface SpeechRecognitionFactory {

    enum ExecutionProfile {
        SUBTITLE,
        AD_AUDIO
    }

    interface Listener {
        void onResult(String text, long startUs, long endUs, int timelineToken);

        void onError(Throwable error);
    }

    interface Session extends AutoCloseable {
        void accept(float[] samples, long startUs, long endUs, int timelineToken);

        void reset();

        @Override
        void close();
    }

    boolean isReady();

    Session create(Listener listener);

    /** Creates a session with an optional native scheduling/resource profile. */
    default Session create(Listener listener, ExecutionProfile profile) {
        return create(listener);
    }
}
