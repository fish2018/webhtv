package com.fongmi.android.tv.theme;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemeProfileValidatorTest {

    @Test
    public void acceptsValidProfileAndEnforcesOpaqueColors() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.background.type = ThemeProfile.BACKGROUND_SOLID;
        profile.background.color = "#f8fafc";
        profile.colors.light.primary = "#155DFC";
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        assertTrue(result.valid());
        assertTrue(result.profile().colors.light.primary.equals("#155DFC"));
    }

    @Test
    public void rejectsSolidBackgroundWithoutColorAndLowContrastExplicitRoles() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.background.type = ThemeProfile.BACKGROUND_SOLID;
        ThemeProfileValidator.Result missingBackground = ThemeProfileValidator.validate(profile);
        assertFalse(missingBackground.valid());

        profile.background.color = "#FFFFFF";
        profile.colors.light.surface = "#FFFFFF";
        profile.colors.light.onSurface = "#EEEEEE";
        ThemeProfileValidator.Result lowContrast = ThemeProfileValidator.validate(profile);
        assertFalse(lowContrast.valid());
    }

    @Test
    public void infersCustomSeedWhenOnlySeedColorIsProvided() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.seedColor = "#155DFC";
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        assertTrue(result.valid());
        assertTrue(ThemeProfile.SEED_CUSTOM.equals(result.profile().seedSource));
    }

    @Test
    public void rejectsInvalidScrimRangeAndUnsafeSource() {
        ThemeProfile profile = ThemeProfile.defaultProfile();
        profile.background.scrimAlpha = 0.9f;
        profile.source.type = "url";
        profile.source.url = "http://example.com/theme.json";
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(profile);
        assertFalse(result.valid());
    }
}
