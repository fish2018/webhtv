package com.fongmi.android.tv.ad.audio;

import java.util.Objects;
import java.util.regex.Pattern;

public final class NoopAdAudioSignalProvider implements AdAudioSignalProvider {

    private static final Pattern PROVIDER_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");

    private final String id;
    private ProviderState state = ProviderState.DISABLED;
    private boolean closed;

    public NoopAdAudioSignalProvider(String id) {
        if (id == null || !PROVIDER_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("provider id is invalid");
        }
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public synchronized void start(SessionContext context, AdAudioRuleSnapshot rules,
                                   Listener listener) {
        if (closed) return;
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(listener, "listener");
    }

    @Override
    public synchronized void onHostPosition(HostPosition position) {
        if (closed) return;
        Objects.requireNonNull(position, "position");
    }

    @Override
    public synchronized void onTimelineReset(TimelineReset reset) {
        if (closed) return;
        Objects.requireNonNull(reset, "reset");
    }

    @Override
    public synchronized void setEnabled(boolean enabled) {
        if (closed) return;
        state = enabled ? ProviderState.IDLE : ProviderState.DISABLED;
    }

    @Override
    public synchronized ProviderState state() {
        return state;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        state = ProviderState.CLOSED;
    }
}
