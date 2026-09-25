package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PlaybackSpeedDialogTest {

    @Test
    public void chooserPreservesEveryExistingShortcutWithoutReintroducingOnePointTwo() throws Exception {
        String manager = read("main", "player/PlayerManager.java");
        Matcher presets = Pattern.compile("SPEED_PRESETS = new float\\[\\]\\{([^}]+)}").matcher(manager);
        assertTrue(presets.find());
        String[] values = presets.group(1).split(",\\s*");
        float[] expected = new float[values.length];
        for (int i = 0; i < values.length; i++) expected[i] = Float.parseFloat(values[i].replace("f", ""));
        assertArrayEquals(expected, PlaybackSpeedDialog.choices(1f).speeds(), 0f);
        for (float speed : expected) assertTrue(speed != 1.2f);
    }

    @Test
    public void everyPresetMapsToItsActualSpeedAndCurrentSelection() {
        float[] speeds = PlaybackSpeedDialog.choices(1f).speeds();
        for (int i = 0; i < speeds.length; i++) {
            PlaybackSpeedDialog.Choices choices = PlaybackSpeedDialog.choices(speeds[i]);
            assertEquals(i, choices.selected());
            assertEquals(speeds[i], choices.speeds()[choices.selected()], 0f);
            assertEquals(String.format(Locale.US, "%.2fx", speeds[i]), choices.labels()[i]);
        }
    }

    @Test
    public void fineAdjustedSpeedsRemainSelectedInSortedChoices() {
        for (float current : new float[]{0.25f, 1.2f, 2.25f, 4.75f}) {
            PlaybackSpeedDialog.Choices choices = PlaybackSpeedDialog.choices(current);
            assertEquals(11, choices.speeds().length);
            assertEquals(current, choices.speeds()[choices.selected()], 0f);
            for (int i = 1; i < choices.speeds().length; i++) {
                assertTrue(choices.speeds()[i - 1] < choices.speeds()[i]);
            }
        }
    }

    @Test
    public void floatNoiseDoesNotDuplicatePresetAndInvalidSpeedsAreNotOffered() {
        assertEquals(10, PlaybackSpeedDialog.choices(1.00001f).speeds().length);
        assertEquals(2, PlaybackSpeedDialog.choices(1.00001f).selected());
        for (float current : new float[]{Float.NaN, Float.POSITIVE_INFINITY, 0f, 6f}) {
            PlaybackSpeedDialog.Choices choices = PlaybackSpeedDialog.choices(current);
            assertEquals(10, choices.speeds().length);
            assertEquals(-1, choices.selected());
        }
    }

    @Test
    public void choicesAreIndependentAndLabelsDoNotDependOnDeviceLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            PlaybackSpeedDialog.Choices first = PlaybackSpeedDialog.choices(1.5f);
            assertEquals("1.50x", first.labels()[first.selected()]);
            first.speeds()[0] = 99f;
            assertEquals(0.5f, PlaybackSpeedDialog.choices(1f).speeds()[0], 0f);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void allSixPlaybackEntrypointsChooseInsteadOfCyclingAndGuardDelayedCallbacks() throws Exception {
        for (String[] host : new String[][]{
                {"mobile", "VideoActivity", "onSpeed"},
                {"leanback", "VideoActivity", "onSpeed"},
                {"mobile", "LiveActivity", "onSpeed"},
                {"leanback", "LiveActivity", "onSpeed"},
                {"leanback", "CastActivity", "onSpeed"},
                {"main", "TmdbDetailActivity", "changeInlineSpeed"}}) {
            String source = read(host[0], "ui/activity/" + host[1] + ".java");
            int start = source.indexOf("private void " + host[2] + "()");
            assertTrue(host[1], start >= 0);
            String method = source.substring(start, source.indexOf("\n    }", start));
            assertTrue(host[1], method.contains("PlaybackSpeedDialog.show(this, player().getSpeed(), speed ->"));
            assertFalse(host[1], method.contains("addSpeed("));
            assertTrue(host[1], method.contains("!isServiceReady() || !isOwner()"));
            assertTrue(host[1], method.contains("setSpeed(speed)") || method.contains("setInlineSpeed(speed)"));
            assertTrue(host[1], method.contains("saveUserSpeed()") || method.contains("setUserSpeed(")
                    || method.contains("PlayerSetting.putDefaultSpeed("));
        }
    }

    private static String read(String variant, String relative) throws Exception {
        Path root = Files.isDirectory(Path.of("src")) ? Path.of(".") : Path.of("app");
        return Files.readString(root.resolve("src/" + variant + "/java/com/fongmi/android/tv/" + relative));
    }
}
