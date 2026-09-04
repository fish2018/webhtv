package com.fongmi.android.tv.player;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackDiagnosticsSourcePolicyTest {

    @Test
    public void recognizesAndroidLocalSources() {
        assertTrue(PlaybackDiagnosticsSourcePolicy.isLocal("file:///storage/movie.mkv"));
        assertTrue(PlaybackDiagnosticsSourcePolicy.isLocal("content://media/external/video/1"));
        assertTrue(PlaybackDiagnosticsSourcePolicy.isLocal("/storage/emulated/0/movie.mkv"));
    }

    @Test
    public void doesNotTreatHttpOrLoopbackAsLocalFiles() {
        assertFalse(PlaybackDiagnosticsSourcePolicy.isLocal("https://example.com/movie.mkv"));
        assertFalse(PlaybackDiagnosticsSourcePolicy.isLocal("http://127.0.0.1:9978/proxy"));
    }
}
