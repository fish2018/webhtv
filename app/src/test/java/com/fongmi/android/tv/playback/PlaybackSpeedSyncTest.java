package com.fongmi.android.tv.playback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.fongmi.android.tv.bean.History;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.Test;

public class PlaybackSpeedSyncTest {

    private final Gson gson = new Gson();

    @Test
    public void explicitNormalOverrideSurvivesProgressRoundTrip() {
        PlaybackProgressInput input = roundTrip(1.0f, true);
        History history = new History();

        PlaybackProgressWriter.applySpeed(history, input.speed, input.speedOverride);

        assertTrue(history.hasUserSpeed());
        assertEquals(1.0f, history.getPlaybackSpeed(2.0f), 0.001f);
    }

    @Test
    public void inheritedDefaultDoesNotBecomePerShowOverride() {
        PlaybackProgressInput input = roundTrip(2.0f, false);
        History history = new History();

        PlaybackProgressWriter.applySpeed(history, input.speed, input.speedOverride);

        assertFalse(history.hasUserSpeed());
        assertEquals(1.5f, history.getPlaybackSpeed(1.5f), 0.001f);
    }

    @Test
    public void legacyPayloadKeepsNonNormalSpeedCompatibility() {
        PlaybackProgressInput input = gson.fromJson("{\"speed\":2.0}", PlaybackProgressInput.class);
        History history = new History();

        assertNull(input.speedOverride);
        PlaybackProgressWriter.applySpeed(history, input.speed, input.speedOverride);

        assertTrue(history.hasUserSpeed());
        assertEquals(2.0f, history.getPlaybackSpeed(1.5f), 0.001f);
    }

    private PlaybackProgressInput roundTrip(float speed, boolean speedOverride) {
        PlaybackRecord record = new PlaybackRecord();
        record.speed = speed;
        record.speedOverride = speedOverride;
        PlaybackFieldPolicy policy = PlaybackFieldPolicy.apiSafe();

        assertTrue(policy.includes("speedOverride"));
        JsonObject json = record.toJson(policy);
        assertEquals(speedOverride, json.get("speedOverride").getAsBoolean());
        return gson.fromJson(json, PlaybackProgressInput.class);
    }
}
