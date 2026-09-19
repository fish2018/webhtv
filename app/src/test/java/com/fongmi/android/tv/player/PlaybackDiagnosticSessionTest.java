package com.fongmi.android.tv.player;

import com.github.catvod.crawler.diagnostics.DiagnosticEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class PlaybackDiagnosticSessionTest {
    @Test public void readyAndFirstFrameRemainControllerSignals() {
        Recorder recorder = new Recorder();
        PlaybackDiagnosticSession session = new PlaybackDiagnosticSession(recorder);
        session.begin("p-a-1", 100);
        session.stage("prepare", "player=0 decode=1", 110);
        session.stage("ready", "player=0", 120);
        session.stage("first-frame", "source=mpv-playback-restart player=0", 130);
        JsonObject last = recorder.last();
        assertEquals("play.lifecycle", last.get("event").getAsString());
        assertEquals("mpv-playback-restart", last.getAsJsonObject("observed").getAsJsonObject("signalSource").get("value").getAsString());
        for (String field : List.of("physicalVideo", "audibility")) {
            JsonObject fact = last.getAsJsonObject("observed").getAsJsonObject(field);
            assertEquals("not-observable", fact.get("status").getAsString());
            assertFalse(fact.has("value"));
        }
        session.end("clear");
        assertEquals("unknown", recorder.last().getAsJsonObject("observed").getAsJsonObject("outcome").get("status").getAsString());
    }

    @Test public void enablingLateAndClearingProduceNewCaptureBaselines() {
        Recorder recorder = new Recorder(); recorder.enabled = false;
        PlaybackDiagnosticSession session = new PlaybackDiagnosticSession(recorder);
        session.begin("p-a-1", 100); session.stage("request", "player=0", 100);
        assertTrue(recorder.events.isEmpty());
        recorder.enabled = true;
        session.stage("ready", "player=0", 200);
        assertTrue(recorder.events.get(0).getAsJsonObject("observed").getAsJsonObject("captureStartedLate").get("value").getAsBoolean());
        recorder.epoch++;
        session.stage("first-frame", "source=media3", 300);
        assertEquals(2, recorder.events.stream().filter(e -> e.get("event").getAsString().equals("diag.session.begin")).count());
    }

    @Test public void controllerInstancesAndMediaGenerationsStaySeparate() {
        Recorder recorder = new Recorder();
        PlaybackDiagnosticSession first = new PlaybackDiagnosticSession(recorder), second = new PlaybackDiagnosticSession(recorder);
        first.begin("p-a-1", 10);
        String instance1 = recorder.last().get("playerInstanceId").getAsString();
        second.begin("p-a-2", 20);
        String instance2 = recorder.last().get("playerInstanceId").getAsString();
        assertNotEquals(instance1, instance2);
        first.begin("p-a-3", 30);
        assertEquals("p-a-3", recorder.last().get("trace").getAsString());
        assertEquals(2, recorder.last().get("mediaGeneration").getAsLong());
        JsonObject previousEnd = recorder.events.get(recorder.events.size() - 2);
        assertEquals("play.attempt.end", previousEnd.get("event").getAsString());
        assertEquals("p-a-1", previousEnd.get("trace").getAsString());
    }

    @Test public void repeatedPrepareCreatesSeparateDiagnosticAttempts() {
        Recorder recorder = new Recorder(); PlaybackDiagnosticSession session = new PlaybackDiagnosticSession(recorder);
        session.begin("p-a-1", 10);
        session.stage("prepare", "player=0 decode=1", 20);
        session.stage("prepare", "player=0 decode=1", 30);
        assertEquals(2, recorder.last().get("attemptId").getAsLong());
        assertEquals(1, recorder.events.stream().filter(e -> e.get("event").getAsString().equals("play.attempt.end")).count());
    }

    private static final class Recorder implements PlaybackDiagnosticSession.Sink {
        final List<JsonObject> events = new ArrayList<>();
        boolean enabled = true;
        long epoch = 1;
        public boolean enabled() { return enabled; }
        public long generation() { return epoch; }
        public void emit(DiagnosticEvent event) { events.add(JsonParser.parseString(event.json()).getAsJsonObject()); }
        JsonObject last() { return events.get(events.size() - 1); }
    }
}
