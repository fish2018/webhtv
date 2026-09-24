package com.fongmi.android.tv.player.exo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.Format;
import androidx.media3.common.MimeTypes;

import org.junit.Test;

import java.util.List;

public class ExoAudioDirectFailureMemoryTest {

    @Test
    public void confirmedEntry_expiresWithoutRefreshingOnLookup() {
        ExoAudioDirectFailureMemory memory = new ExoAudioDirectFailureMemory();
        ExoAudioDirectFailureMemory.Key key = key("one", 1);
        assertTrue(memory.confirm(key, memory.generation(), 1_000));
        assertTrue(memory.contains(key, 1_000 + ExoAudioDirectFailureMemory.TTL_MS - 1));
        assertFalse(memory.contains(key, 1_000 + ExoAudioDirectFailureMemory.TTL_MS));
        assertFalse(memory.hasMedia(key.media(), 1_000 + ExoAudioDirectFailureMemory.TTL_MS));
    }

    @Test
    public void capacity_evictsOldestConfirmation() {
        ExoAudioDirectFailureMemory memory = new ExoAudioDirectFailureMemory();
        for (int i = 0; i <= ExoAudioDirectFailureMemory.MAX_ENTRIES; i++) {
            assertTrue(memory.confirm(key("media-" + i, 1), memory.generation(), i));
        }
        assertFalse(memory.contains(key("media-0", 1), 100));
        assertTrue(memory.contains(key("media-1", 1), 100));
        assertTrue(memory.contains(key("media-32", 1), 100));
    }

    @Test
    public void capabilityChange_rejectsLateConfirmation() {
        ExoAudioDirectFailureMemory memory = new ExoAudioDirectFailureMemory();
        long oldGeneration = memory.generation();
        assertTrue(memory.confirm(key("one", 1), oldGeneration, 0));
        memory.invalidate();
        assertFalse(memory.contains(key("one", 1), 1));
        assertFalse(memory.confirm(key("one", 1), oldGeneration, 2));
        assertTrue(memory.confirm(key("one", 1), memory.generation(), 2));
    }

    @Test
    public void unknownRouteAndMedia_doNotBecomeSharedEvidence() {
        ExoAudioDirectFailureMemory memory = new ExoAudioDirectFailureMemory();
        ExoAudioDirectFailureMemory.Key unknownRoute = new ExoAudioDirectFailureMemory.Key(
                "media", "format", AudioAttributes.DEFAULT, null);
        ExoAudioDirectFailureMemory.Key unknownMedia = new ExoAudioDirectFailureMemory.Key(
                null, "format", AudioAttributes.DEFAULT, key("one", 1).route());
        assertFalse(memory.confirm(unknownRoute, memory.generation(), 0));
        assertFalse(memory.confirm(unknownMedia, memory.generation(), 0));
    }

    @Test
    public void exactMediaAndRoute_areIsolated() {
        ExoAudioDirectFailureMemory memory = new ExoAudioDirectFailureMemory();
        assertTrue(memory.confirm(key("one", 1), memory.generation(), 0));
        assertFalse(memory.contains(key("two", 1), 1));
        assertFalse(memory.contains(key("one", 2), 1));
        assertFalse(memory.hasMedia(key("two", 1).media(), 1));
    }

    @Test
    public void fingerprints_keepQueryTrackAndCodecInitializationIdentity() {
        String url = "https://private.example/video?token=secret";
        String id = ExoAudioDirectFailureMemory.mediaId(url);
        assertEquals(64, id.length());
        assertFalse(id.contains("private"));
        assertFalse(id.contains("secret"));
        assertNotEquals(id, ExoAudioDirectFailureMemory.mediaId(url + "2"));
        Format format = new Format.Builder().setId("audio-1")
                .setSampleMimeType(MimeTypes.AUDIO_AAC).setCodecs("mp4a.40.2")
                .setSampleRate(44_100).setChannelCount(2)
                .setInitializationData(List.of(new byte[]{0x12, 0x10})).build();
        String formatId = ExoAudioDirectFailureMemory.formatId(format);
        assertEquals(formatId, ExoAudioDirectFailureMemory.formatId(format.buildUpon().build()));
        assertNotEquals(formatId, ExoAudioDirectFailureMemory.formatId(
                format.buildUpon().setId("audio-2").build()));
        assertNotEquals(formatId, ExoAudioDirectFailureMemory.formatId(
                format.buildUpon().setInitializationData(List.of(new byte[]{0x12, 0x11})).build()));
        assertNotEquals(formatId, ExoAudioDirectFailureMemory.formatId(
                format.buildUpon().setSampleRate(48_000).build()));
    }

    private static ExoAudioDirectFailureMemory.Key key(String media, int routeId) {
        return new ExoAudioDirectFailureMemory.Key(ExoAudioDirectFailureMemory.mediaId(media),
                "format", AudioAttributes.DEFAULT,
                new ExoAudioDirectFailureMemory.Route(routeId, 2, 3, 4, 5));
    }
}
