package com.fongmi.android.tv.player.engine;

import androidx.media3.common.C;

import com.fongmi.android.tv.bean.Track;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
public class MpvTrackSelectionTest {

    @Test
    public void restoresAc3TrackWhenLabelAndBitrateChangeAfterRebuild() {
        String persisted = "1:1,Audio 1,ac3,audio/ac3,48000,6,64000";
        List<MpvPlayerEngine.PersistedTrackCandidate> candidates = List.of(
                new MpvPlayerEngine.PersistedTrackCandidate(
                        "1:1,Surround 7.1,truehd,audio/true-hd,48000,8,0",
                        "1:1", "audio/true-hd", "truehd", 48000, 8,
                        "eng", "Surround 7.1"),
                new MpvPlayerEngine.PersistedTrackCandidate(
                        "1:2,Surround 5.1,ac3,audio/ac3,48000,6,0",
                        "1:2", "audio/ac3", "ac3", 48000, 6,
                        "eng", "Surround 5.1"));

        int matched = MpvPlayerEngine.findPersistedMpvTrackIndex(
                persisted, candidates);

        assertEquals(1, matched);
    }

    @Test
    public void extractsPersistedSubtitleIdForPreselection() {
        Track track = new Track(C.TRACK_TYPE_TEXT, "简体中文",
                "3:2,简体中文,subrip,zh,application/x-subrip,-1");
        track.setSelected(true);

        assertEquals("2", MpvPlayerEngine.persistedSubtitleTrackId(track));

        track.setSelected(false);
        assertNull(MpvPlayerEngine.persistedSubtitleTrackId(track));
    }
}
