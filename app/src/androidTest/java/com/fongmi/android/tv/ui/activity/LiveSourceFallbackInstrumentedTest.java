package com.fongmi.android.tv.ui.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.setting.LiveSetting;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class LiveSourceFallbackInstrumentedTest {

    @Test
    public void unparsedMiddleSourceDoesNotSkipToLastBeforeParseCompletes() throws Exception {
        boolean originalFallback = LiveSetting.isSourceFallback();
        List<Live> originalLives = new ArrayList<>(LiveConfig.get().getLives());
        Live originalHome = LiveConfig.get().getHome();
        LiveSetting.putSourceFallback(true);
        try (PlaylistServer server = new PlaylistServer()) {
            Live first = new Live("instrumented-first", server.url());
            Live middle = new Live("instrumented-middle", server.url());
            Live last = new Live("instrumented-last", server.url());
            setCustomLives(List.of(first, middle, last), first);
            Context context = ApplicationProvider.getApplicationContext();
            Intent intent = new Intent(context, LiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try (ActivityScenario<LiveActivity> scenario = ActivityScenario.launch(intent)) {
                waitForService(scenario);
                waitUntil(() -> !first.getGroups().isEmpty(), 15_000);

                scenario.onActivity(activity -> activity.setLive(middle));

                waitUntil(() -> !middle.getGroups().isEmpty(), 15_000);
                assertFalse("the selected middle source should parse successfully", middle.getGroups().isEmpty());
                assertEquals("an unparsed selected source must not be skipped before its parse completes",
                        1, LiveConfig.get().getHomeIndex());
                assertEquals("instrumented-middle", LiveConfig.get().getHome().getName());
            } finally {
                restore(originalLives, originalHome, originalFallback);
            }
        } finally {
            LiveSetting.putSourceFallback(originalFallback);
        }
    }

    @Test
    public void invalidSourceAdvancesOnlyToNextValidSource() throws Exception {
        boolean originalFallback = LiveSetting.isSourceFallback();
        List<Live> originalLives = new ArrayList<>(LiveConfig.get().getLives());
        Live originalHome = LiveConfig.get().getHome();
        LiveSetting.putSourceFallback(true);
        try (PlaylistServer server = new PlaylistServer()) {
            Live first = new Live("instrumented-first", server.url());
            Live invalid = new Live("instrumented-invalid", server.invalidUrl());
            Live nextValid = new Live("instrumented-next", server.url());
            Live last = new Live("instrumented-last", server.url());
            setCustomLives(List.of(first, invalid, nextValid, last), first);
            Context context = ApplicationProvider.getApplicationContext();
            Intent intent = new Intent(context, LiveActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try (ActivityScenario<LiveActivity> scenario = ActivityScenario.launch(intent)) {
                waitForService(scenario);
                waitUntil(() -> !first.getGroups().isEmpty(), 15_000);

                scenario.onActivity(activity -> activity.setLive(invalid));

                waitUntil(() -> LiveConfig.get().getHomeIndex() == 2 && !nextValid.getGroups().isEmpty(), 20_000);
                assertEquals("an invalid source must advance only to the next valid source",
                        2, LiveConfig.get().getHomeIndex());
                assertEquals("instrumented-next", LiveConfig.get().getHome().getName());
            } finally {
                restore(originalLives, originalHome, originalFallback);
            }
        } finally {
            LiveSetting.putSourceFallback(originalFallback);
        }
    }

    private static void waitForService(ActivityScenario<LiveActivity> scenario) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 15_000;
        while (SystemClock.uptimeMillis() < deadline) {
            AtomicBoolean ready = new AtomicBoolean(false);
            scenario.onActivity(activity -> ready.set(activity.isServiceReady()));
            if (ready.get()) return;
            SystemClock.sleep(100);
        }
        fail("LiveActivity playback service did not become ready");
    }

    private static void waitUntil(Check check, long timeoutMs) throws Exception {
        long deadline = SystemClock.uptimeMillis() + timeoutMs;
        while (SystemClock.uptimeMillis() < deadline) {
            if (check.done()) return;
            SystemClock.sleep(25);
        }
        fail("timed out waiting for the selected live source to parse");
    }

    private static void setCustomLives(List<Live> lives, Live home) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LiveConfig.get().getLives().clear();
            LiveConfig.get().getLives().addAll(lives);
            LiveConfig.get().setHome(home);
        });
    }

    private static void restore(List<Live> lives, Live home, boolean fallback) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            LiveConfig.get().getLives().clear();
            LiveConfig.get().getLives().addAll(lives);
            LiveConfig.get().setHome(home);
            LiveSetting.putSourceFallback(fallback);
        });
    }

    private interface Check {
        boolean done();
    }

    private static final class PlaylistServer implements AutoCloseable {

        private final ServerSocket server;
        private final Thread worker;
        private final ExecutorService clients = Executors.newCachedThreadPool();

        private PlaylistServer() throws IOException {
            server = new ServerSocket(0, 20, InetAddress.getByName("127.0.0.1"));
            worker = new Thread(this::serve, "live-source-instrumented-server");
            worker.setDaemon(true);
            worker.start();
        }

        private String url() {
            return "http://127.0.0.1:" + server.getLocalPort() + "/playlist.txt";
        }

        private String invalidUrl() {
            return "http://127.0.0.1:" + server.getLocalPort() + "/invalid.txt";
        }

        private void serve() {
            while (!server.isClosed()) {
                try {
                    Socket client = server.accept();
                    clients.execute(() -> handle(client));
                } catch (IOException error) {
                    if (!server.isClosed()) error.printStackTrace();
                }
            }
        }

        private void handle(Socket client) {
            try (client) {
                client.setSoTimeout(2_000);
                String path = requestPath(client);
                byte[] body = playlist().getBytes(StandardCharsets.UTF_8);
                int status = 200;
                String type = "text/plain; charset=utf-8";
                if (path != null && path.startsWith("/invalid")) {
                    status = 404;
                    body = "not a playlist".getBytes(StandardCharsets.UTF_8);
                } else if (path != null && path.contains(".m3u8")) {
                    type = "application/vnd.apple.mpegurl";
                    body = manifest().getBytes(StandardCharsets.UTF_8);
                }
                OutputStream output = client.getOutputStream();
                output.write(("HTTP/1.1 " + status + (status == 200 ? " OK" : " Not Found") + "\r\n"
                        + "Content-Type: " + type + "\r\n"
                        + "Content-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                output.write(body);
                output.flush();
            } catch (IOException error) {
                if (!server.isClosed()) error.printStackTrace();
            }
        }

        private String requestPath(Socket client) throws IOException {
            BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.US_ASCII));
            String request = reader.readLine();
            if (request == null) return null;
            String[] parts = request.split(" ");
            return parts.length > 1 ? parts[1] : null;
        }

        private String playlist() {
            return "#EXTM3U\n"
                    + "#EXTINF:-1,Instrumented middle source\n"
                    + "http://127.0.0.1:" + server.getLocalPort() + "/live.m3u8\n";
        }

        private String manifest() {
            return "#EXTM3U\n"
                    + "#EXT-X-VERSION:3\n"
                    + "#EXT-X-TARGETDURATION:1\n"
                    + "#EXT-X-MEDIA-SEQUENCE:0\n"
                    + "#EXTINF:1.0,\n"
                    + "http://127.0.0.1:" + server.getLocalPort() + "/segment.ts\n"
                    + "#EXT-X-ENDLIST\n";
        }

        @Override
        public void close() throws IOException {
            server.close();
            clients.shutdownNow();
        }
    }
}
