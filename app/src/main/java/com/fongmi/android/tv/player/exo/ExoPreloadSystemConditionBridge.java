package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.PlaybackAutoContext;
import com.fongmi.android.tv.player.PlaybackSystemConditionCoordinator;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Filters process-level system callbacks to one complete playback session token. */
final class ExoPreloadSystemConditionBridge implements AutoCloseable {

    private final PlaybackAutoContext.SessionToken expectedSession;
    private final Listener listener;
    private final PlaybackSystemConditionCoordinator.Registration registration;
    private final AtomicBoolean closed = new AtomicBoolean();

    ExoPreloadSystemConditionBridge(
            PlaybackAutoContext.SessionToken expectedSession,
            PlaybackSystemConditionCoordinator coordinator,
            Listener listener) {
        this.expectedSession = expectedSession == null
                ? PlaybackAutoContext.SessionToken.none() : expectedSession;
        this.listener = Objects.requireNonNull(listener);
        this.registration = this.expectedSession.active()
                ? Objects.requireNonNull(coordinator).addListener(this::onUpdate)
                : null;
    }

    private void onUpdate(PlaybackSystemConditionCoordinator.Update update) {
        if (closed.get() || update == null
                || !expectedSession.equals(update.session())) return;
        listener.onUpdate(update);
    }

    static AutoPreloadPolicy.Reason disruption(
            PlaybackSystemConditionCoordinator.Update update,
            long nowElapsedMs) {
        if (update == null) return null;
        PlaybackAutoContext.Fact<PlaybackAutoContext.NetworkSnapshot> networkFact =
                update.networkSnapshot();
        if (networkFact != null && networkFact.isUsable(nowElapsedMs)) {
            PlaybackAutoContext.NetworkSnapshot network = networkFact.value();
            if (Boolean.FALSE.equals(network.available())) {
                return AutoPreloadPolicy.Reason.NETWORK_UNAVAILABLE;
            }
            if (Boolean.FALSE.equals(network.validated())) {
                return AutoPreloadPolicy.Reason.NETWORK_UNVALIDATED;
            }
            if (network.dataSaverState() == PlaybackAutoContext.DataSaverState.ENABLED) {
                return AutoPreloadPolicy.Reason.DATA_SAVER;
            }
        }
        PlaybackAutoContext.Fact<PlaybackAutoContext.PowerState> power = update.power();
        if (power != null && power.isUsable(nowElapsedMs)
                && power.value() == PlaybackAutoContext.PowerState.POWER_SAVE) {
            return AutoPreloadPolicy.Reason.POWER_SAVE;
        }
        PlaybackAutoContext.Fact<PlaybackAutoContext.ThermalState> thermal = update.thermal();
        if (thermal != null && thermal.isUsable(nowElapsedMs)) {
            if (thermal.value() == PlaybackAutoContext.ThermalState.SEVERE
                    || thermal.value() == PlaybackAutoContext.ThermalState.CRITICAL) {
                return AutoPreloadPolicy.Reason.THERMAL_PRESSURE;
            }
        }
        return null;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (registration != null) registration.close();
    }

    interface Listener {
        void onUpdate(PlaybackSystemConditionCoordinator.Update update);
    }
}
