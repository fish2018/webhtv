package com.fongmi.android.tv.remote;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.remote.RemoteModels.PollResponse;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommand;
import com.fongmi.android.tv.remote.RemoteModels.RemoteCommandResult;
import com.fongmi.android.tv.remote.RemoteModels.RemoteProfile;
import com.fongmi.android.tv.remote.RemoteModels.RemoteStoreFile;
import com.fongmi.android.tv.remote.RemoteModels.ServerCapabilities;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public final class RemoteAgent {

    private static final long POLL_INTERVAL_MS = 4_000L;
    private static final long REGISTER_INTERVAL_MS = 60_000L;
    private static final long WEBSOCKET_RETRY_MS = 10_000L;

    private static volatile RemoteAgent instance;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private RemoteAgent() {
    }

    public static RemoteAgent get() {
        if (instance == null) {
            synchronized (RemoteAgent.class) {
                if (instance == null) instance = new RemoteAgent();
            }
        }
        return instance;
    }

    public void start() {
        start(true);
    }

    void startFromService() {
        start(false);
    }

    public synchronized void stop() {
        for (Session session : sessions.values()) session.stop();
        sessions.clear();
        RemoteAgentService.stop(App.get());
    }

    public boolean isRunning() {
        return !sessions.isEmpty();
    }

    private synchronized void start(boolean manageService) {
        try {
            RemoteStore.reloadFromFile();
            RemoteStoreFile store = RemoteStore.get();
            Set<String> active = new HashSet<>();
            boolean keepOnline = false;
            for (RemoteProfile profile : store.profiles) {
                if (!RemoteStore.shouldStart(profile)) continue;
                active.add(profile.serverOrigin);
                keepOnline |= profile.enabled && profile.keepOnline;
                Session session = sessions.get(profile.serverOrigin);
                if (session == null) {
                    session = new Session(profile.serverOrigin);
                    sessions.put(profile.serverOrigin, session);
                }
                session.start();
            }
            for (Iterator<Map.Entry<String, Session>> it = sessions.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<String, Session> entry = it.next();
                if (active.contains(entry.getKey())) continue;
                entry.getValue().stop();
                it.remove();
            }
            if (manageService) {
                if (keepOnline) RemoteAgentService.start(App.get());
                else RemoteAgentService.stop(App.get());
            }
        } catch (Throwable e) {
            log("agent start failed error=%s", e.getMessage());
        }
    }

    private static void log(String format, Object... args) {
        RemoteLog.log(format, args);
    }

    private static final class Session {
        private final String serverOrigin;
        private volatile ScheduledFuture<?> future;
        private volatile WebSocket webSocket;
        private volatile boolean active;
        private volatile boolean busy;
        private volatile boolean webSocketSupported;
        private volatile boolean webSocketConnected;
        private volatile long lastRegister;
        private volatile long lastErrorLog;
        private volatile long lastWebSocketAttempt;

        private Session(String serverOrigin) {
            this.serverOrigin = serverOrigin;
        }

        private synchronized void start() {
            active = true;
            if (future != null && !future.isCancelled()) return;
            future = Task.scheduler().scheduleWithFixedDelay(() -> Task.execute(this::pollSafely), 0, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
            log("session started origin=%s", serverOrigin);
        }

        private synchronized void stop() {
            active = false;
            if (future != null) future.cancel(false);
            future = null;
            closeWebSocket();
            log("session stopped origin=%s", serverOrigin);
        }

        private void pollSafely() {
            if (!active || busy) return;
            busy = true;
            try {
                RemoteProfile profile = RemoteStore.getProfileByOrigin(serverOrigin);
                if (!RemoteStore.shouldStart(profile)) return;
                if (App.activity() == null) {
                    closeWebSocket();
                    lastRegister = 0;
                    return;
                }
                RemoteClient client = new RemoteClient(profile);
                long now = System.currentTimeMillis();
                if (lastRegister <= 0 || now - lastRegister > REGISTER_INTERVAL_MS) {
                    ServerCapabilities capabilities = client.capabilities();
                    if (!active) return;
                    client.register();
                    if (!active) return;
                    profile.updatedAt = now;
                    RemoteStore.upsertProfile(profile);
                    webSocketSupported = capabilities != null && capabilities.capabilities != null && capabilities.capabilities.webSocket;
                    lastRegister = now;
                }
                if (!active) return;
                if (webSocketSupported) {
                    ensureWebSocket(profile);
                    if (!active || webSocketConnected) return;
                }
                PollResponse response = client.poll();
                if (!active) return;
                RemoteCommand command = response == null ? null : response.command;
                if (command == null || TextUtils.isEmpty(command.id)) return;
                log("command received origin=%s id=%s type=%s", serverOrigin, command.id, command.type);
                executeCommand(profile, command);
            } catch (Throwable e) {
                if (!active) return;
                if (System.currentTimeMillis() - lastErrorLog > 30_000L) {
                    lastErrorLog = System.currentTimeMillis();
                    log("poll failed origin=%s error=%s", serverOrigin, e.getMessage());
                }
                lastRegister = 0;
            } finally {
                busy = false;
            }
        }

        private synchronized void ensureWebSocket(RemoteProfile profile) {
            if (!active || webSocketConnected || webSocket != null) return;
            long now = System.currentTimeMillis();
            if (now - lastWebSocketAttempt < WEBSOCKET_RETRY_MS) return;
            lastWebSocketAttempt = now;
            try {
                RemoteClient client = new RemoteClient(profile);
                webSocket = OkHttp.client().newBuilder()
                        .readTimeout(0, TimeUnit.MILLISECONDS)
                        .pingInterval(25, TimeUnit.SECONDS)
                        .build()
                        .newWebSocket(client.webSocketRequest(), new Listener(this, profile.serverOrigin, client.webSocketHello()));
            } catch (Throwable e) {
                closeWebSocket();
                log("websocket start failed origin=%s error=%s", serverOrigin, e.getMessage());
            }
        }

        private void executeCommand(RemoteProfile profile, RemoteCommand command) {
            if (!active || !RemoteStore.shouldStart(profile) || command == null || TextUtils.isEmpty(command.id)) return;
            try {
                if (App.activity() == null) {
                    if (!active) return;
                    new RemoteClient(profile).commandResult(command.id, RemoteCommandResult.failure("App is not open"));
                    return;
                }
                RemoteCommandResult result = RemoteCommandExecutor.execute(profile, command);
                if (!active) return;
                new RemoteClient(profile).commandResult(command.id, result);
            } catch (Throwable e) {
                if (active) log("command execute failed origin=%s id=%s error=%s", serverOrigin, command.id, e.getMessage());
            }
        }

        private synchronized boolean onWebSocketOpen(WebSocket socket) {
            if (!active || webSocket != socket) {
                socket.close(1000, "stop");
                return false;
            }
            webSocketConnected = true;
            log("websocket connected origin=%s", serverOrigin);
            return true;
        }

        private synchronized boolean isCurrentWebSocket(WebSocket socket) {
            return active && webSocket == socket;
        }

        private synchronized void onWebSocketClosed(WebSocket socket) {
            if (webSocket != socket) return;
            webSocketConnected = false;
            webSocket = null;
            log("websocket closed origin=%s", serverOrigin);
        }

        private synchronized void onWebSocketFailure(WebSocket socket, Throwable t, Response response) {
            if (webSocket != socket) return;
            webSocketSupported = false;
            onWebSocketClosed(socket);
            int code = response == null ? 0 : response.code();
            if (code == 426) {
                log("websocket unavailable origin=%s code=%s fallback=poll", serverOrigin, code);
            } else {
                log("websocket failed origin=%s error=%s", serverOrigin, t == null ? "" : t.getMessage());
            }
        }

        private synchronized void closeWebSocket() {
            WebSocket current = webSocket;
            webSocket = null;
            webSocketConnected = false;
            if (current != null) current.close(1000, "stop");
        }
    }

    private static final class Listener extends WebSocketListener {
        private final Session session;
        private final String serverOrigin;
        private final String hello;

        private Listener(Session session, String serverOrigin, String hello) {
            this.session = session;
            this.serverOrigin = serverOrigin;
            this.hello = hello;
        }

        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            if (!session.onWebSocketOpen(webSocket)) return;
            webSocket.send(hello);
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            if (!session.isCurrentWebSocket(webSocket)) return;
            try {
                JsonObject object = App.gson().fromJson(text, JsonObject.class);
                if (object == null || !object.has("command")) return;
                JsonElement element = object.get("command");
                if (element == null || !element.isJsonObject()) return;
                RemoteCommand command = App.gson().fromJson(element, RemoteCommand.class);
                if (command == null || TextUtils.isEmpty(command.id)) return;
                RemoteProfile profile = RemoteStore.getProfileByOrigin(serverOrigin);
                if (!RemoteStore.shouldStart(profile)) {
                    session.closeWebSocket();
                    return;
                }
                log("websocket command received origin=%s id=%s type=%s", serverOrigin, command.id, command.type);
                Task.execute(() -> session.executeCommand(profile, command));
            } catch (Throwable e) {
                log("websocket message failed origin=%s error=%s", serverOrigin, e.getMessage());
            }
        }

        @Override
        public void onClosed(WebSocket webSocket, int code, String reason) {
            session.onWebSocketClosed(webSocket);
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable t, Response response) {
            session.onWebSocketFailure(webSocket, t, response);
        }
    }
}
