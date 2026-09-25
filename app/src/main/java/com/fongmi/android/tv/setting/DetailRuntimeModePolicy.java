package com.fongmi.android.tv.setting;

import java.util.Objects;

/** Resolves the detail mode for one loaded detail session without touching Android UI or network state. */
public final class DetailRuntimeModePolicy {

    public record Input(
            int configuredMode,
            boolean tmdbReady,
            boolean siteAllowed,
            TmdbSourceState sourceState
    ) {
        public Input {
            Objects.requireNonNull(sourceState, "sourceState");
        }
    }

    public record Decision(
            int runtimeMode,
            boolean sourceOnly,
            boolean networkAllowed
    ) {
    }

    public static Decision resolve(Input input) {
        Objects.requireNonNull(input, "input");
        if (!Setting.isTmdbMode(input.configuredMode())) {
            return new Decision(Setting.DETAIL_OPEN_DIRECT, false, false);
        }
        if (!input.siteAllowed()) {
            return new Decision(Setting.DETAIL_OPEN_DIRECT, false, false);
        }
        boolean renderable = input.sourceState() == TmdbSourceState.RENDERABLE;
        if (!renderable && !input.tmdbReady()) {
            return new Decision(Setting.DETAIL_OPEN_DIRECT, false, false);
        }
        return new Decision(
                input.configuredMode(),
                renderable && !input.tmdbReady(),
                input.tmdbReady()
        );
    }

    private DetailRuntimeModePolicy() {
    }
}
