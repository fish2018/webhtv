package com.fongmi.android.tv.server.proxy;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ProxyStreamRegistration implements AutoCloseable {

    private final String url;
    private final Runnable release;
    private final AtomicBoolean closed = new AtomicBoolean();

    ProxyStreamRegistration(String url, Runnable release) {
        this.url = Objects.requireNonNull(url, "url");
        this.release = Objects.requireNonNull(release, "release");
    }

    public String url() {
        return url;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) release.run();
    }

    @Override
    public String toString() {
        return "ProxyStreamRegistration{redacted}";
    }
}
