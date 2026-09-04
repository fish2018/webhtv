package com.fongmi.android.tv.player.mpv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public class MpvDirectAudioPolicyTest {

    @Test
    public void selectsSameLanguageStereoBeforeTrueHd() {
        MpvDirectAudioPolicy.Selection selection = MpvDirectAudioPolicy.select(List.of(
                track("1", "eng", "truehd", "7.1", 8),
                track("2", "en-US", "aac", "Stereo", 2)), "1");

        assertTrue(selection.changed());
        assertEquals("2", selection.id());
        assertEquals("same-language-stereo", selection.reason());
    }

    @Test
    public void selectsEfficientSameLanguageTrackWhenStereoIsUnavailable() {
        MpvDirectAudioPolicy.Selection selection = MpvDirectAudioPolicy.select(List.of(
                track("1", "zh", "dts-hd ma", "7.1", 8),
                track("2", "zho", "ac3", "5.1", 6)), "1");

        assertTrue(selection.changed());
        assertEquals("2", selection.id());
        assertEquals("same-language-efficient", selection.reason());
    }

    @Test
    public void doesNotSwitchToAnotherLanguage() {
        MpvDirectAudioPolicy.Selection selection = MpvDirectAudioPolicy.select(List.of(
                track("1", "jpn", "truehd", "7.1", 8),
                track("2", "eng", "aac", "Stereo", 2)), "1");

        assertFalse(selection.changed());
        assertEquals("1", selection.id());
    }

    @Test
    public void keepsCurrentStereoTrack() {
        MpvDirectAudioPolicy.Selection selection = MpvDirectAudioPolicy.select(List.of(
                track("1", "eng", "ac3", "Stereo", 2),
                track("2", "eng", "aac", "Stereo", 2)), "1");

        assertFalse(selection.changed());
        assertEquals("1", selection.id());
        assertEquals("current-track-stereo", selection.reason());
    }

    @Test
    public void keepsCurrentTrackWhenDeviceCanPassItThrough() {
        MpvDirectAudioPolicy.Selection selection = MpvDirectAudioPolicy.select(List.of(
                track("1", "eng", "dts", "DTS 5.1", 6),
                track("2", "eng", "aac", "Stereo", 2)), "1", "ac3,dts");

        assertFalse(selection.changed());
        assertEquals("1", selection.id());
        assertEquals("current-track-passthrough", selection.reason());
    }

    @Test
    public void fallsBackWhenCurrentTrackCannotPassThrough() {
        MpvDirectAudioPolicy.Selection selection = MpvDirectAudioPolicy.select(List.of(
                track("1", "eng", "dts", "DTS 5.1", 6),
                track("2", "eng", "aac", "Stereo", 2)), "1", "ac3,eac3");

        assertTrue(selection.changed());
        assertEquals("2", selection.id());
        assertEquals("same-language-stereo", selection.reason());
    }

    private static MpvDirectAudioPolicy.Candidate track(String id, String language,
                                                        String codec, String title,
                                                        int channels) {
        return new MpvDirectAudioPolicy.Candidate(id, language, codec, title, channels);
    }
}
