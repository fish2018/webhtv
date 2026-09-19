package com.fongmi.android.tv.player;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.PowerManager;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Android adapter for session-scoped network cost, power saver and thermal facts. */
public final class PlaybackSystemConditionMonitor {

    static final long PERIODIC_SAMPLE_INTERVAL_MS = 60_000;

    private static final PlaybackSystemConditionMonitor PROCESS =
            new PlaybackSystemConditionMonitor(PlaybackSystemConditionCoordinator.process());

    private final Object sessionLock;
    private final PlaybackSystemConditionCoordinator coordinator;
    private final ScheduledExecutorService executor;
    private final ConnectivityManager.NetworkCallback networkCallback;
    private final BroadcastReceiver systemReceiver;

    private volatile Context applicationContext;
    private volatile ConnectivityManager connectivityManager;
    private volatile PowerManager powerManager;
    private PlaybackAutoContext.SessionToken scheduledSession;
    private ScheduledFuture<?> periodicFuture;
    private boolean networkCallbackRegistered;
    private boolean receiverRegistered;
    private ThermalRegistration thermalRegistration;

    private PlaybackSystemConditionMonitor(PlaybackSystemConditionCoordinator coordinator) {
        this.sessionLock = new Object();
        this.coordinator = coordinator;
        this.scheduledSession = PlaybackAutoContext.SessionToken.none();
        this.executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "playback-system-condition");
            thread.setDaemon(true);
            return thread;
        });
        this.networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                requestSample(PlaybackAutoContext.SystemConditionTrigger.NETWORK_CALLBACK);
            }

            @Override
            public void onLost(@NonNull Network network) {
                requestSample(PlaybackAutoContext.SystemConditionTrigger.NETWORK_CALLBACK);
            }

            @Override
            public void onCapabilitiesChanged(
                    @NonNull Network network, @NonNull NetworkCapabilities networkCapabilities) {
                requestSample(PlaybackAutoContext.SystemConditionTrigger.NETWORK_CALLBACK);
            }

            @Override
            public void onUnavailable() {
                requestSample(PlaybackAutoContext.SystemConditionTrigger.NETWORK_CALLBACK);
            }
        };
        this.systemReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent == null ? null : intent.getAction();
                if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
                    requestSample(PlaybackAutoContext.SystemConditionTrigger.POWER_CALLBACK);
                } else if (ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED.equals(action)) {
                    requestSample(PlaybackAutoContext.SystemConditionTrigger.DATA_SAVER_CALLBACK);
                }
            }
        };
        coordinator.addListener(update -> PlaybackTrace.log(
                "playback-system-condition", update.session().traceId(), "%s", update.logSummary()));
    }

    public static PlaybackSystemConditionMonitor process() {
        return PROCESS;
    }

    public void initialize(Context context) {
        if (context == null) return;
        Context appContext = context.getApplicationContext();
        PlaybackAutoContext.SessionToken active;
        synchronized (sessionLock) {
            applicationContext = appContext == null ? context : appContext;
            connectivityManager = systemService(
                    applicationContext, Context.CONNECTIVITY_SERVICE, ConnectivityManager.class);
            powerManager = systemService(applicationContext, Context.POWER_SERVICE, PowerManager.class);
            active = scheduledSession;
            if (active.active()) registerListenersLocked(active);
        }
        if (active.active()) {
            executor.execute(() -> sampleSafely(
                    active, PlaybackAutoContext.SystemConditionTrigger.SESSION_START));
        }
    }

    public boolean beginSession(PlaybackAutoContext.SessionToken session) {
        synchronized (sessionLock) {
            if (!coordinator.beginSession(session)) return false;
            cancelPeriodicLocked();
            scheduledSession = session;
            registerListenersLocked(session);
            executor.execute(() -> sampleSafely(
                    session, PlaybackAutoContext.SystemConditionTrigger.SESSION_START));
            periodicFuture = executor.scheduleWithFixedDelay(
                    () -> sampleSafely(session, PlaybackAutoContext.SystemConditionTrigger.PERIODIC),
                    PERIODIC_SAMPLE_INTERVAL_MS,
                    PERIODIC_SAMPLE_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
            return true;
        }
    }

    public boolean endSession(PlaybackAutoContext.SessionToken session) {
        synchronized (sessionLock) {
            if (!coordinator.endSession(session)) return false;
            if (session.equals(scheduledSession)) {
                scheduledSession = PlaybackAutoContext.SessionToken.none();
                cancelPeriodicLocked();
                unregisterListenersLocked(session);
            }
            return true;
        }
    }

    public String currentNetworkIdentityDigest() {
        ConnectivityManager manager = connectivityManager;
        if (manager == null) return "";
        try {
            Network network = manager.getActiveNetwork();
            return network == null ? "" : networkIdentityDigest(network);
        } catch (Throwable ignored) {
            return "";
        }
    }

    public PlaybackAutoContext.NetworkSnapshot currentNetworkSnapshot() {
        return readNetwork(coordinator.activeSession(), connectivityManager);
    }

    private void requestSample(PlaybackAutoContext.SystemConditionTrigger trigger) {
        PlaybackAutoContext.SessionToken session = coordinator.activeSession();
        if (!session.active()) return;
        executor.execute(() -> sampleSafely(session, trigger));
    }

    private void sampleSafely(
            PlaybackAutoContext.SessionToken session,
            PlaybackAutoContext.SystemConditionTrigger trigger) {
        try {
            sample(session, trigger);
        } catch (Throwable error) {
            logFailure(session, "sample", error);
        }
    }

    private void sample(
            PlaybackAutoContext.SessionToken session,
            PlaybackAutoContext.SystemConditionTrigger trigger) {
        if (!session.equals(coordinator.activeSession())) return;
        long sampledAt = SystemClock.elapsedRealtime();
        PlaybackAutoContext.NetworkSnapshot network = readNetwork(session, connectivityManager);
        Boolean powerSave = readPowerSave(session, powerManager);
        Integer thermalStatus = readThermalStatus(session, powerManager);
        coordinator.publish(session, trigger, network, powerSave, thermalStatus,
                Build.VERSION.SDK_INT, sampledAt);
        DiagnosticControls.forTrace(session.traceId(), "play.resources", "existing-system-monitor", e -> e
                .observed("powerSave", powerSave).observed("thermalStatus", thermalStatus).observed("reason", trigger.name()));
    }

    private void registerListenersLocked(PlaybackAutoContext.SessionToken session) {
        ConnectivityManager connectivity = connectivityManager;
        if (!networkCallbackRegistered && connectivity != null) {
            try {
                connectivity.registerDefaultNetworkCallback(networkCallback);
                networkCallbackRegistered = true;
            } catch (Throwable error) {
                logFailure(session, "register-network-callback", error);
            }
        }
        Context context = applicationContext;
        if (!receiverRegistered && context != null) {
            try {
                IntentFilter filter = new IntentFilter();
                filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
                filter.addAction(ConnectivityManager.ACTION_RESTRICT_BACKGROUND_CHANGED);
                context.registerReceiver(systemReceiver, filter);
                receiverRegistered = true;
            } catch (Throwable error) {
                logFailure(session, "register-system-receiver", error);
            }
        }
        if (thermalRegistration == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && powerManager != null) {
            try {
                thermalRegistration = Api29Thermal.register(
                        powerManager, executor,
                        () -> requestSample(PlaybackAutoContext.SystemConditionTrigger.THERMAL_CALLBACK));
            } catch (Throwable error) {
                logFailure(session, "register-thermal-listener", error);
            }
        }
    }

    private void unregisterListenersLocked(PlaybackAutoContext.SessionToken session) {
        ConnectivityManager connectivity = connectivityManager;
        if (networkCallbackRegistered) {
            networkCallbackRegistered = false;
            if (connectivity != null) {
                try {
                    connectivity.unregisterNetworkCallback(networkCallback);
                } catch (Throwable error) {
                    logFailure(session, "unregister-network-callback", error);
                }
            }
        }
        Context context = applicationContext;
        if (receiverRegistered) {
            receiverRegistered = false;
            if (context != null) {
                try {
                    context.unregisterReceiver(systemReceiver);
                } catch (Throwable error) {
                    logFailure(session, "unregister-system-receiver", error);
                }
            }
        }
        ThermalRegistration registration = thermalRegistration;
        thermalRegistration = null;
        if (registration != null) {
            try {
                registration.close();
            } catch (Throwable error) {
                logFailure(session, "unregister-thermal-listener", error);
            }
        }
    }

    private static PlaybackAutoContext.NetworkSnapshot readNetwork(
            PlaybackAutoContext.SessionToken session, ConnectivityManager manager) {
        if (manager == null) return PlaybackAutoContext.NetworkSnapshot.unknown();
        PlaybackAutoContext.DataSaverState dataSaver = readDataSaver(session, manager);
        Network activeNetwork;
        try {
            activeNetwork = manager.getActiveNetwork();
        } catch (Throwable error) {
            logFailure(session, "active-network", error);
            return new PlaybackAutoContext.NetworkSnapshot(
                    null, null, null, null, PlaybackAutoContext.NetworkTransport.UNKNOWN, dataSaver);
        }
        if (activeNetwork == null) {
            return new PlaybackAutoContext.NetworkSnapshot(
                    false, false, null, null, PlaybackAutoContext.NetworkTransport.UNKNOWN, dataSaver);
        }

        NetworkCapabilities capabilities = null;
        try {
            capabilities = manager.getNetworkCapabilities(activeNetwork);
        } catch (Throwable error) {
            logFailure(session, "network-capabilities", error);
        }
        Boolean validated = capabilities == null ? null
                : capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        Boolean metered = null;
        try {
            metered = manager.isActiveNetworkMetered();
        } catch (Throwable error) {
            logFailure(session, "network-metered", error);
        }
        PlaybackAutoContext.NetworkTransport transport = networkTransport(capabilities);
        Boolean roaming = readRoaming(session, manager, capabilities, transport);
        return new PlaybackAutoContext.NetworkSnapshot(
                true,
                validated,
                metered,
                roaming,
                transport,
                dataSaver,
                networkIdentityDigest(activeNetwork));
    }

    private static String networkIdentityDigest(Network network) {
        if (network == null) return "";
        try {
            return PlaybackNetworkIdentityPolicy.digest(network.getNetworkHandle());
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static PlaybackAutoContext.DataSaverState readDataSaver(
            PlaybackAutoContext.SessionToken session, ConnectivityManager manager) {
        try {
            return PlaybackSystemConditionPolicy.dataSaverState(manager.getRestrictBackgroundStatus());
        } catch (Throwable error) {
            logFailure(session, "data-saver", error);
            return PlaybackAutoContext.DataSaverState.UNKNOWN;
        }
    }

    @SuppressWarnings("deprecation")
    private static Boolean readRoaming(
            PlaybackAutoContext.SessionToken session,
            ConnectivityManager manager,
            NetworkCapabilities capabilities,
            PlaybackAutoContext.NetworkTransport transport) {
        if (transport == PlaybackAutoContext.NetworkTransport.VPN) return null;
        if (transport == PlaybackAutoContext.NetworkTransport.WIFI
                || transport == PlaybackAutoContext.NetworkTransport.ETHERNET) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                && transport == PlaybackAutoContext.NetworkTransport.CELLULAR
                && capabilities != null) {
            return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        }
        try {
            NetworkInfo info = manager.getActiveNetworkInfo();
            return info == null ? null : info.isRoaming();
        } catch (Throwable error) {
            logFailure(session, "network-roaming", error);
            return null;
        }
    }

    private static PlaybackAutoContext.NetworkTransport networkTransport(
            NetworkCapabilities capabilities) {
        if (capabilities == null) return PlaybackAutoContext.NetworkTransport.UNKNOWN;
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            return PlaybackAutoContext.NetworkTransport.VPN;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
            return PlaybackAutoContext.NetworkTransport.WIFI;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
            return PlaybackAutoContext.NetworkTransport.ETHERNET;
        }
        if (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            return PlaybackAutoContext.NetworkTransport.CELLULAR;
        }
        return PlaybackAutoContext.NetworkTransport.OTHER;
    }

    private static Boolean readPowerSave(
            PlaybackAutoContext.SessionToken session, PowerManager manager) {
        if (manager == null) return null;
        try {
            return manager.isPowerSaveMode();
        } catch (Throwable error) {
            logFailure(session, "power-save", error);
            return null;
        }
    }

    private static Integer readThermalStatus(
            PlaybackAutoContext.SessionToken session, PowerManager manager) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || manager == null) return null;
        try {
            return Api29Thermal.currentStatus(manager);
        } catch (Throwable error) {
            logFailure(session, "thermal-status", error);
            return null;
        }
    }

    private void cancelPeriodicLocked() {
        if (periodicFuture == null) return;
        periodicFuture.cancel(false);
        periodicFuture = null;
    }

    private static <T> T systemService(Context context, String name, Class<T> type) {
        if (context == null) return null;
        try {
            Object service = context.getSystemService(name);
            return type.isInstance(service) ? type.cast(service) : null;
        } catch (Throwable error) {
            PlaybackAutoContext.SessionToken session =
                    PlaybackSystemConditionCoordinator.process().activeSession();
            logFailure(session, "system-service-" + name, error);
            return null;
        }
    }

    private static void logFailure(
            PlaybackAutoContext.SessionToken session, String action, Throwable error) {
        PlaybackTrace.log("playback-system-condition",
                session == null ? PlaybackTrace.NONE : session.traceId(),
                "action=%s result=failed exception=%s", action,
                error == null ? "unknown" : error.getClass().getSimpleName());
    }

    private interface ThermalRegistration extends AutoCloseable {
        @Override
        void close();
    }

    private static final class Api29Thermal {

        private Api29Thermal() {
        }

        static int currentStatus(PowerManager manager) {
            return manager.getCurrentThermalStatus();
        }

        static ThermalRegistration register(
                PowerManager manager, Executor executor, Runnable callback) {
            PowerManager.OnThermalStatusChangedListener listener = status -> callback.run();
            manager.addThermalStatusListener(executor, listener);
            return () -> manager.removeThermalStatusListener(listener);
        }
    }
}
