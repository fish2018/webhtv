package androidx.media3.mpvplayer;

import android.view.KeyEvent;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MpvDiscMenuPolicyTest {

    @Test
    public void touchCarriesCurrentWindowPositionInOneCommand() {
        assertArrayEquals(new String[]{"discnav", "mouse-click", "1200", "640", "window"},
                MpvDiscMenuPolicy.pointerCommand(1200, 640, true));
        assertArrayEquals(new String[]{"discnav", "mouse-move", "75", "300", "window"},
                MpvDiscMenuPolicy.pointerCommand(75, 300, false));
    }

    @Test
    public void pointerCoordinatesAreNotNormalizedOrClampedBeforeNativeTransform() {
        assertArrayEquals(new String[]{"discnav", "mouse-click", "0", "0", "window"},
                MpvDiscMenuPolicy.pointerCommand(0, 0, true));
        assertArrayEquals(new String[]{"discnav", "mouse-click", "-1", "2160", "window"},
                MpvDiscMenuPolicy.pointerCommand(-1, 2160, true));
    }

    @Test
    public void startedNavigationSavesVisitWithoutInventingMovieProgress() {
        assertTrue(MpvDiscMenuPolicy.canSaveHistory(false, true));
        assertTrue(MpvDiscMenuPolicy.canSaveHistory(true, true));
        assertTrue(MpvDiscMenuPolicy.canSaveHistory(true, false));
        assertFalse(MpvDiscMenuPolicy.canSaveHistory(false, false));
        assertFalse(MpvDiscMenuPolicy.hasSinglePlaybackTimeline(
                "webhtv-dvdiso://1005/raw", true, true));
    }

    @Test
    public void rawIsoDefersHistoryUntilNativeModeIsKnown() {
        assertTrue(MpvDiscMenuPolicy.usesRawIso("webhtv-dvdiso://1005/raw"));
        assertFalse(MpvDiscMenuPolicy.hasSinglePlaybackTimeline(
                "webhtv-dvdiso://1005/raw", false, false));
    }

    @Test
    public void hdmvSessionDoesNotUseMovieHistoryEvenBetweenMenus() {
        assertFalse(MpvDiscMenuPolicy.hasSinglePlaybackTimeline(
                "webhtv-dvdiso://1005/raw", true, true));
    }

    @Test
    public void menuBackgroundEofDoesNotEndNavigationSession() {
        assertFalse(MpvDiscMenuPolicy.isTerminalEof(true, true));
    }

    @Test
    public void navigatingAcrossClipsKeepsPlaybackActive() {
        // The native EOF property can toggle several times without an end-file
        // event, including while the menu overlay is temporarily invisible.
        for (boolean eof : new boolean[]{false, true, false, true, false}) {
            assertFalse(MpvDiscMenuPolicy.isTerminalEof(eof, true));
        }
    }

    @Test
    public void ordinaryTitlesAndBdjFallbackKeepTerminalEof() {
        assertFalse(MpvDiscMenuPolicy.isTerminalEof(false, false));
        assertTrue(MpvDiscMenuPolicy.isTerminalEof(true, false));
    }

    @Test
    public void bdjAndNoMenuFallbackKeepNormalHistoryAfterOpening() {
        assertTrue(MpvDiscMenuPolicy.hasSinglePlaybackTimeline(
                "webhtv-dvdiso://1005/raw", true, false));
    }

    @Test
    public void disabledMenuAndOrdinaryMediaKeepImmediateHistory() {
        for (String uri : new String[]{null, "webhtv-dvdiso://1005/longest",
                "https://example.test/movie.iso", "https://example.test/movie.mkv"}) {
            assertFalse(MpvDiscMenuPolicy.usesRawIso(uri));
            assertTrue(MpvDiscMenuPolicy.hasSinglePlaybackTimeline(uri, false, false));
        }
    }

    @Test
    public void disabledKeepsLongestTitle() {
        assertEquals("webhtv-dvdiso://1005/longest",
                MpvDiscMenuPolicy.isoUri("webhtv-dvdiso://1005/longest", false));
    }

    @Test
    public void enabledUsesRawIsoWithoutChangingSession() {
        assertEquals("webhtv-dvdiso://1005/raw",
                MpvDiscMenuPolicy.isoUri("webhtv-dvdiso://1005/longest", true));
    }

    @Test
    public void negativeOpaqueIsoProbeRemainsOrdinaryMedia() {
        assertNull(MpvDiscMenuPolicy.isoUri(null, true));
        assertNull(MpvDiscMenuPolicy.isoUri(null, false));
    }

    @Test
    public void unrelatedSourcesAndRawUrisAreNotRewritten() {
        assertEquals("https://example.test/longest/movie.mkv",
                MpvDiscMenuPolicy.isoUri("https://example.test/longest/movie.mkv", true));
        assertEquals("webhtv-dvdiso://1005/raw",
                MpvDiscMenuPolicy.isoUri("webhtv-dvdiso://1005/raw", true));
    }

    @Test
    public void menuKeysMatchMpvActions() {
        assertEquals("up", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_DPAD_UP));
        assertEquals("down", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_DPAD_DOWN));
        assertEquals("left", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_DPAD_LEFT));
        assertEquals("right", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_DPAD_RIGHT));
        assertEquals("select", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_DPAD_CENTER));
        assertEquals("select", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_ENTER));
        assertEquals("prev", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_BACK));
        assertEquals("popup", MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_MENU));
    }

    @Test
    public void volumeAndMediaKeysRemainOwnedByAndroid() {
        assertNull(MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_VOLUME_UP));
        assertNull(MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_VOLUME_DOWN));
        assertNull(MpvDiscMenuPolicy.keyAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
    }
}
