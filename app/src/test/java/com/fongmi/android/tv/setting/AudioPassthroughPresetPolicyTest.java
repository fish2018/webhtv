package com.fongmi.android.tv.setting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AudioPassthroughPresetPolicyTest {

    @Test
    public void exoAndMpvPresetsEnableCapabilityNegotiatedPassthrough() {
        assertTrue(KernelPerformanceSetting.audioPassthroughForPreset(
                PlayerSetting.EXO));
        assertTrue(KernelPerformanceSetting.audioPassthroughForPreset(
                PlayerSetting.MPV));
        assertFalse(KernelPerformanceSetting.audioPassthroughForPreset(
                PlayerSetting.IJK));
    }

    @Test
    public void migrationPreservesExplicitUserChoices() {
        assertTrue(PlaybackPerformanceSetting
                .shouldMigrateAudioPassthroughDefault(
                        PlaybackPerformanceSetting.PROFILE_AUTO,
                        false,
                        false));
        assertFalse(PlaybackPerformanceSetting
                .shouldMigrateAudioPassthroughDefault(
                        PlaybackPerformanceSetting.PROFILE_AUTO,
                        true,
                        false));
        assertFalse(PlaybackPerformanceSetting
                .shouldMigrateAudioPassthroughDefault(
                        PlaybackPerformanceSetting.PROFILE_CUSTOM,
                        false,
                        false));
        assertFalse(PlaybackPerformanceSetting
                .shouldMigrateAudioPassthroughDefault(
                        PlaybackPerformanceSetting.PROFILE_LIGHTWEIGHT,
                        false,
                        true));
    }
}
