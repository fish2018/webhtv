package com.fongmi.android.tv.player;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DecodeLabelPolicyTest {

    @Test
    public void mismatchOnlyWhenHardwareProfileRunsSoftware() {
        assertTrue(DecodeLabelPolicy.isHardwareProfileRunningSoftware(
                true, PlaybackAutoContext.DecodeMode.SOFTWARE));
        assertFalse(DecodeLabelPolicy.isHardwareProfileRunningSoftware(
                true, PlaybackAutoContext.DecodeMode.HARDWARE));
        // A soft-decode profile running software is not a mismatch.
        assertFalse(DecodeLabelPolicy.isHardwareProfileRunningSoftware(
                false, PlaybackAutoContext.DecodeMode.SOFTWARE));
    }

    @Test
    public void unknownModeNeverProducesAClaim() {
        assertFalse(DecodeLabelPolicy.isHardwareProfileRunningSoftware(
                true, PlaybackAutoContext.DecodeMode.UNKNOWN));
        assertFalse(DecodeLabelPolicy.isHardwareProfileRunningSoftware(true, null));
        assertEquals("硬解", DecodeLabelPolicy.decodeLabel("硬解", "软解", true, null));
        assertEquals("硬解", DecodeLabelPolicy.decodeLabel(
                "硬解", "软解", true, PlaybackAutoContext.DecodeMode.UNKNOWN));
    }

    @Test
    public void labelShowsBothSidesOnlyOnMismatch() {
        assertEquals("硬解→软解", DecodeLabelPolicy.decodeLabel(
                "硬解", "软解", true, PlaybackAutoContext.DecodeMode.SOFTWARE));
        assertEquals("硬解", DecodeLabelPolicy.decodeLabel(
                "硬解", "软解", true, PlaybackAutoContext.DecodeMode.HARDWARE));
        assertEquals("软解", DecodeLabelPolicy.decodeLabel(
                "软解", "软解", false, PlaybackAutoContext.DecodeMode.SOFTWARE));
    }

    @Test
    public void softLabelComesFromTheCallerSoTheTextStaysInOneLanguage() {
        // The configured label is localized (select_decode array), so a hardcoded suffix would
        // emit mixed-language text. Both sides must come from the same locale.
        assertEquals("Hard→Soft", DecodeLabelPolicy.decodeLabel(
                "Hard", "Soft", true, PlaybackAutoContext.DecodeMode.SOFTWARE));
        assertEquals("硬解→軟解", DecodeLabelPolicy.decodeLabel(
                "硬解", "軟解", true, PlaybackAutoContext.DecodeMode.SOFTWARE));
    }

    @Test
    public void missingSoftLabelFallsBackToTheConfiguredLabel() {
        assertEquals("硬解", DecodeLabelPolicy.decodeLabel(
                "硬解", null, true, PlaybackAutoContext.DecodeMode.SOFTWARE));
        assertEquals("硬解", DecodeLabelPolicy.decodeLabel(
                "硬解", "", true, PlaybackAutoContext.DecodeMode.SOFTWARE));
        assertEquals("硬解", DecodeLabelPolicy.decodeLabel(
                "硬解", "   ", true, PlaybackAutoContext.DecodeMode.SOFTWARE));
    }

    @Test
    public void configuredLabelIsReturnedUnchangedWhenNoClaim() {
        // Any engine's label text passes through untouched, so this stays kernel-agnostic.
        assertEquals("硬解", DecodeLabelPolicy.decodeLabel(
                "硬解", "软解", true, PlaybackAutoContext.DecodeMode.HARDWARE));
        assertEquals("软解", DecodeLabelPolicy.decodeLabel(
                "软解", "软解", false, PlaybackAutoContext.DecodeMode.HARDWARE));
        assertEquals("", DecodeLabelPolicy.decodeLabel(
                "", "软解", true, PlaybackAutoContext.DecodeMode.HARDWARE));
    }
}
