package com.fongmi.android.tv.web;

/**
 * Owns the generation and opaque-reference stores for one WebTheme runtime.
 *
 * <p>Snapshots keep all reference stores on the same generation. Callers must still validate the
 * captured generation before publishing asynchronous results.</p>
 */
final class WebThemeSession {

    private int generation;
    private WebThemePlaySession playSession = new WebThemePlaySession();
    private WebThemeAccessSession accessSession = new WebThemeAccessSession();
    private WebThemeDetailActionSession detailActionSession = new WebThemeDetailActionSession();

    synchronized int generation() {
        return generation;
    }

    synchronized boolean isCurrent(int expectedGeneration) {
        return generation == expectedGeneration;
    }

    synchronized int cancelPending() {
        return ++generation;
    }

    synchronized int invalidate() {
        generation++;
        resetReferencesLocked();
        return generation;
    }

    synchronized void replacePlaySession(WebThemePlaySession replacement) {
        playSession = replacement == null ? new WebThemePlaySession() : replacement;
    }

    synchronized WebThemePlaySession getPlaySession() {
        return playSession;
    }

    synchronized WebThemeAccessSession getAccessSession() {
        return accessSession;
    }

    synchronized WebThemeDetailActionSession getDetailActionSession() {
        return detailActionSession;
    }

    synchronized Snapshot snapshot() {
        return new Snapshot(generation, playSession, accessSession, detailActionSession);
    }

    private void resetReferencesLocked() {
        playSession = new WebThemePlaySession();
        accessSession = new WebThemeAccessSession();
        detailActionSession = new WebThemeDetailActionSession();
    }

    record Snapshot(int generation, WebThemePlaySession playSession, WebThemeAccessSession accessSession,
            WebThemeDetailActionSession detailActionSession) {
    }
}
