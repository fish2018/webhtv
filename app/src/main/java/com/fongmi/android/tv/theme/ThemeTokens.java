package com.fongmi.android.tv.theme;

/** Immutable runtime semantic colors resolved from a profile. */
public record ThemeTokens(
        int primary,
        int onPrimary,
        int primaryContainer,
        int onPrimaryContainer,
        int appBackground,
        int surface,
        int surfaceElevated,
        int onSurface,
        int onSurfaceVariant,
        int outline,
        int focus,
        int error,
        String mode,
        String backgroundType,
        float scrimAlpha
) {
}
