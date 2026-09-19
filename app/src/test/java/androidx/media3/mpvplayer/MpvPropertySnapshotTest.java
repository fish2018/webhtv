package androidx.media3.mpvplayer;

import org.junit.Test;
import static org.junit.Assert.*;

public class MpvPropertySnapshotTest {
    @Test public void doesNotInterceptExistingCacheOrAudioFallbackQueries() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        for (String property : new String[]{"cache-speed", "time-pos", "demuxer-cache-state/reader-pts",
                "audio-params/format", "current-tracks/audio/codec-profile"}) {
            state.register(property);
            assertFalse(state.contains(property));
        }
        state.register("video-params/gamma");
        assertTrue(state.contains("video-params/gamma"));
    }

    @Test public void trackFieldsRetainTypesAndUnicode() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        state.update(0, "track-list", MpvPropertySnapshot.TrackList.parse(
                "[{\"id\":2,\"type\":\"audio\",\"title\":\"国语 🎵\",\"lang\":\"zho\","
                        + "\"selected\":true,\"demux-channel-count\":6,\"hls-bitrate\":8000000}]"));
        assertEquals(Integer.valueOf(1), state.integer("track-list/count"));
        assertEquals("2", state.string("track-list/0/id"));
        assertEquals("国语 🎵", state.string("track-list/0/title"));
        assertEquals("zho", state.string("track-list/0/lang"));
        assertEquals(Boolean.TRUE, state.flag("track-list/0/selected"));
        assertEquals(Integer.valueOf(6), state.integer("track-list/0/demux-channel-count"));
    }

    @Test public void missingTrackFieldsAreAuthoritativeAbsence() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        assertTrue(state.contains("track-list/0/decoder"));
        assertNull(state.string("track-list/0/decoder"));
        assertNull(state.integer("track-list/-1/id"));
        assertNull(state.integer("track-list/bad/id"));
        assertEquals(Integer.valueOf(0), state.integer("track-list/count"));
    }

    @Test public void snapshotsAreReplacedNotMerged() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        state.update(0, "track-list", MpvPropertySnapshot.TrackList.parse("[{\"id\":1},{\"id\":2}]"));
        state.update(0, "track-list", MpvPropertySnapshot.TrackList.parse("[{\"id\":3}]"));
        assertEquals("3", state.string("track-list/0/id"));
        assertNull(state.string("track-list/1/id"));
    }

    @Test public void lateOldFileEventsCannotRepopulateNewFile() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        state.update(0, "width", 3840L);
        state.beginFile(1);
        assertFalse(state.update(0, "width", 1920L));
        assertFalse(state.update(0, "track-list", MpvPropertySnapshot.TrackList.parse("[{\"id\":9}]")));
        assertNull(state.integer("width"));
        assertEquals(Integer.valueOf(0), state.integer("track-list/count"));
        assertTrue(state.update(1, "width", 1280L));
        assertEquals(Integer.valueOf(1280), state.integer("width"));
    }

    @Test public void unchangedOptionsSurviveFileBoundaryButFileDataDoesNot() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        state.register("sid");
        state.update(0, "sid", "no");
        state.update(0, "audio-device", "audiotrack/auto");
        state.update(0, "demuxer-readahead-secs", 60L);
        state.update(0, "current-tracks/sub/id", "3");
        state.update(0, "chapter-list", "[{\"time\":0}]");
        state.beginFile(1);
        assertEquals("no", state.string("sid"));
        assertEquals("audiotrack/auto", state.string("audio-device"));
        assertEquals(Integer.valueOf(60), state.integer("demuxer-readahead-secs"));
        assertNull(state.string("current-tracks/sub/id"));
        assertNull(state.string("chapter-list"));
    }

    @Test public void unavailableObserverAndAcceptedSelectionNeverNeedReadback() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        state.register("sub-visibility");
        assertTrue(state.contains("sub-visibility"));
        assertNull(state.flag("sub-visibility"));
        state.acceptedWrite("sub-visibility", true);
        assertEquals(Boolean.TRUE, state.flag("sub-visibility"));
        state.update(0, "sub-visibility", null);
        assertNull(state.flag("sub-visibility"));
        assertTrue(state.contains("sub-visibility"));
        assertFalse(state.contains("unobserved-option"));
    }

    @Test public void rejectsMalformedOversizedAndDeepJson() {
        assertFalse(MpvPropertySnapshot.TrackList.parse("[{broken]").valid());
        assertFalse(MpvPropertySnapshot.TrackList.parse("{}").valid());
        assertFalse(MpvPropertySnapshot.TrackList.parse("[null]").valid());
        assertFalse(MpvPropertySnapshot.TrackList.parse(" ".repeat(MpvPropertySnapshot.MAX_JSON_CHARS + 1)).valid());
        assertFalse(MpvPropertySnapshot.TrackList.parse("[".repeat(13) + "]".repeat(13)).valid());
        assertTrue(MpvPropertySnapshot.TrackList.parse("[{\"title\":\"{[\\\"🎬\\\"]}\"}]").valid());
    }

    @Test public void invalidAndUnavailableSnapshotsClearOldTracks() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        state.update(0, "track-list", MpvPropertySnapshot.TrackList.parse("[{\"id\":1}]"));
        state.update(0, "track-list", MpvPropertySnapshot.TrackList.parse("bad"));
        assertEquals(Integer.valueOf(0), state.integer("track-list/count"));
        assertTrue(MpvPropertySnapshot.TrackList.parse(null).valid());
    }

    @Test public void numericConversionsDoNotWrapOrInventValues() {
        MpvPropertySnapshot state = new MpvPropertySnapshot();
        state.update(0, "large", 4_000_000_000L);
        assertNull(state.integer("large"));
        assertEquals(Double.valueOf(4_000_000_000d), state.decimal("large"));
        state.update(0, "nan", Double.NaN);
        assertNull(state.decimal("nan"));
        state.update(0, "choice", "auto");
        assertNull(state.integer("choice"));
        assertNull(state.flag("choice"));
    }
}
