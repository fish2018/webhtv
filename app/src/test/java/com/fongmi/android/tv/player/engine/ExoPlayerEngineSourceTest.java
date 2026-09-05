package com.fongmi.android.tv.player.engine;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ExoPlayerEngineSourceTest {

    @Test
    public void everyPrepareUsesTheGenerationAwareLifecycleBridge() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "player", "engine", "ExoPlayerEngine.java");
        String startInternal = methodBody(source, "private void startInternal(long position, boolean playWhenReady)");
        String seekToDefault = methodBody(source, "private ErrorAction seekToDefaultPosition()");
        String preparePlayer = methodBody(source, "private void preparePlayer()");

        assertEquals("ExoPlayerEngine must keep the raw prepare call behind one lifecycle bridge",
                1, count(source, "player.prepare();"));
        assertTrue("normal starts and format retries must use the lifecycle bridge",
                startInternal.contains("preparePlayer();"));
        assertTrue("behind-live-window recovery must use the lifecycle bridge",
                seekToDefault.contains("preparePlayer();"));
        assertTrue("the bridge must announce the generation before preparing",
                preparePlayer.indexOf("int generation = beginPrepare();") >= 0
                        && preparePlayer.indexOf("prepareListener.onPrepareStarted(generation);") > preparePlayer.indexOf("int generation = beginPrepare();")
                        && preparePlayer.indexOf("player.prepare();") > preparePlayer.indexOf("prepareListener.onPrepareStarted(generation);"));
    }

    @Test
    public void readyAndCancellationCallbacksCarryTheCapturedGeneration() throws Exception {
        String source = readMainJava("com", "fongmi", "android", "tv", "player", "engine", "ExoPlayerEngine.java");
        String beginPrepare = methodBody(source, "private int beginPrepare()");
        String cancelPrepare = methodBody(source, "public void cancelPendingPrepare()");
        String release = methodBody(source, "public void release()");
        String rebuild = methodBody(source, "public Player rebuild(Player.Listener listener)");
        String restart = methodBody(source, "public void restart(PlaySpec spec, long position, boolean playWhenReady)");
        String stop = methodBody(source, "public void stop()");

        assertTrue("prepare generations must remain unique across engine replacements",
                source.contains("static final AtomicInteger PREPARE_GENERATION")
                        && beginPrepare.contains("PREPARE_GENERATION.incrementAndGet()"));
        assertTrue("READY must complete only the listener generation that is still active",
                beginPrepare.contains("generation != pendingPrepareGeneration")
                        && beginPrepare.contains("prepareListener.onPrepareReady(generation);"));
        assertTrue("cancellation must invalidate and report the exact active generation",
                cancelPrepare.contains("int generation = pendingPrepareGeneration;")
                        && cancelPrepare.contains("prepareListener.onPrepareCanceled(generation);"));
        assertTrue("replacement, stop, rebuild, and release paths must cancel stale prepare listeners",
                beginPrepare.contains("cancelPendingPrepare();")
                        && restart.contains("cancelPendingPrepare();")
                        && stop.contains("cancelPendingPrepare();")
                        && rebuild.contains("cancelPendingPrepare();")
                        && release.contains("cancelPendingPrepare();"));
    }

    private static int count(String source, String value) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(value, index)) >= 0) {
            count++;
            index += value.length();
        }
        return count;
    }

    private static String methodBody(String source, String signature) {
        int method = source.indexOf(signature);
        assertTrue("Missing method: " + signature, method >= 0);
        int start = source.indexOf('{', method);
        int depth = 0;
        for (int i = start; i < source.length(); i++) {
            char value = source.charAt(i);
            if (value == '{') depth++;
            if (value == '}' && --depth == 0) return source.substring(start + 1, i);
        }
        throw new AssertionError("Unclosed method: " + signature);
    }

    private static String readMainJava(String... parts) throws Exception {
        Path path = Path.of("src", "main", "java");
        for (String part : parts) path = path.resolve(part);
        if (!Files.exists(path)) {
            path = Path.of("app", "src", "main", "java");
            for (String part : parts) path = path.resolve(part);
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}
