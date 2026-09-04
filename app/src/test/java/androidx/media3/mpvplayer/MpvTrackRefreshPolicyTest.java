package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class MpvTrackRefreshPolicyTest {

    @Test
    public void beforeFileLoaded_usesStartupQuietWindow() {
        assertEquals(2000, MpvTrackRefreshPolicy.delayMs(false, 0, 5000));
    }

    @Test
    public void shortlyAfterFileLoaded_usesStartupQuietWindow() {
        assertEquals(2000, MpvTrackRefreshPolicy.delayMs(true, 1000, 8999));
    }

    @Test
    public void afterStartupWindow_usesNormalDebounce() {
        assertEquals(120, MpvTrackRefreshPolicy.delayMs(true, 1000, 9000));
    }
}
