package com.fongmi.android.tv.ui.custom;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class TmdbHeaderViewLayoutTest {

    @Test
    public void headerShowsSmartRecommendationReasonOnCardFocus() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("Smart recommendation row must listen for focus changes",
                source.contains("personalAiRecommendationAdapter.setOnItemFocusListener(this::showAiRecommendationReason);"));
        assertTrue("Smart recommendation focus must update the visible reason text",
                source.contains("private void showAiRecommendationReason(TmdbItem item, boolean focused)"));
        assertTrue("Smart recommendation reason should use the localized preview string",
                source.contains("getString(R.string.ai_recommendation_reason_preview"));
        assertTrue("Smart recommendation reason must be cleared when the row is hidden",
                source.contains("showAiRecommendationReason(null, false);"));
    }

    @Test
    public void headerLayoutPlacesSmartRecommendationReasonBelowSmartRow() throws Exception {
        Path layoutPath = findMainResPath().resolve(Path.of("layout", "view_tmdb_header.xml"));
        String layout = new String(Files.readAllBytes(layoutPath), StandardCharsets.UTF_8);
        int smartList = layout.indexOf("@+id/tmdbPersonalAiRecommendations");
        int reason = layout.indexOf("@+id/tmdbPersonalAiReason");
        int nextSection = layout.indexOf("@+id/tmdbOmdbRatingsLabel");

        assertTrue("Header layout must contain the smart recommendation row", smartList >= 0);
        assertTrue("Header layout must contain the smart recommendation reason text", reason > smartList);
        assertTrue("Smart recommendation reason should appear before the next section", nextSection > reason);
    }

    @Test
    public void recommendationAdapterDispatchesFocusForAlreadyFocusedCards() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "adapter", "TmdbRecommendationAdapter.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);

        assertTrue("Recommendation adapter must dispatch focus when a focused card is rebound",
                source.contains("if (itemView.hasFocus() && focusListener != null) focusListener.onItemFocus(item, true);"));
    }

    @Test
    public void fusionPlaybackControlsRethemeMovedTextAndIcons() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int refreshMethod = source.indexOf("public void refreshTheme()");
        int tintMethod = source.indexOf("private void tintTree(View view, int color)");

        assertTrue("Header view must expose a theme refresh for playback controls moved in later", refreshMethod >= 0);
        assertTrue("refreshTheme must re-run the current header theme", source.indexOf("applyTheme();", refreshMethod) > refreshMethod);
        assertTrue(sourcePath + " is missing tintTree", tintMethod >= 0);
        assertTrue("Tinting moved playback labels must clear old white text shadows",
                source.indexOf("clearTextShadow(textView);", tintMethod) > tintMethod);
        assertTrue("Tinting moved playback controls must recolor image buttons",
                source.indexOf("imageView.setColorFilter(color)", tintMethod) > tintMethod);
        assertTrue("Non-fusion cinema playback controls must match section title color after header theme refresh",
                source.contains("styleTmdbPlaybackControls(primary);"));
        assertTrue("Non-fusion playback control styling must tint moved labels and icons",
                source.contains("private void styleTmdbPlaybackControls(int textColor)")
                        && source.contains("tintTree(controls, textColor);"));
        assertTrue("Playback controls must follow the actual detail chrome, not only the dark/light theme value",
                source.contains("return isLightDetailChrome();"));
        assertTrue("Non-fusion translucent profile playback titles should match the profile section title color",
                source.contains("return isCurrentDetailLightTheme() ? 0xFF15222B : COLOR_FUSION_BACKDROP_TEXT;"));
    }

    @Test
    public void cinemaLightPlaybackActionsUseReadableLightPalette() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int tintMethod = source.indexOf("private void tintAction(int id, int style)");
        int lightCinemaBranch = source.indexOf("if (cinema && resolveLightTheme())", tintMethod);

        assertTrue(sourcePath + " is missing tintAction", tintMethod >= 0);
        assertTrue("Light cinema playback actions need their own high-contrast branch", lightCinemaBranch > tintMethod);
        assertTrue("Light cinema playback actions should use the cinema light palette",
                source.indexOf("TmdbCinemaTheme.palette(true)", lightCinemaBranch) > lightCinemaBranch);
        assertTrue("Light cinema playback actions should use dark text",
                source.indexOf("button.setTextColor(palette.primary());", lightCinemaBranch) > lightCinemaBranch);
        assertTrue("Light cinema playback actions need a visible border on light backgrounds",
                source.indexOf("button.setStrokeColor(ColorStateList.valueOf(palette.lineStrong()));", lightCinemaBranch) > lightCinemaBranch);
        assertTrue("Light cinema playback actions need a solid readable surface",
                source.indexOf("button.setBackgroundTintList(ColorStateList.valueOf(palette.card()));", lightCinemaBranch) > lightCinemaBranch);
        assertTrue("Playback action buttons should not inherit a translucent state",
                source.indexOf("button.setAlpha(1f);", tintMethod) > tintMethod);
    }

    @Test
    public void ratingChipsUseReadableLightAndBackdropSurfaces() throws Exception {
        Path sourcePath = findMainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "ui", "custom", "TmdbHeaderView.java"));
        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        int applyTheme = source.indexOf("private void applyTheme()");
        int createRating = source.indexOf("private com.google.android.material.textview.MaterialTextView createRatingChip");
        int createSource = source.indexOf("private View createSourceRatingChip");
        int style = source.indexOf("private void styleRatingChipContainer");
        int resolve = source.indexOf("private int resolveRatingChipTextColor");

        assertTrue(sourcePath + " is missing rating chip creation and theme refresh helpers",
                applyTheme >= 0 && createRating >= 0 && createSource >= 0 && style >= 0 && resolve >= 0);
        assertTrue("theme refresh must restyle already-created rating chips after dark/light switches",
                source.indexOf("styleRatingChipContainer(headerRoot.findViewById(R.id.tmdbRatingsContainer));", applyTheme) > applyTheme
                        && source.indexOf("styleRatingChipContainer(headerRoot.findViewById(R.id.tmdbOmdbRatings));", applyTheme) > applyTheme);
        assertTrue("rating chip creation should tag the source color so refresh can keep a readable brand color",
                source.indexOf("chip.setTag(color);", createRating) > createRating
                        && source.indexOf("valueView.setTag(color);", createSource) > createSource);
        assertTrue("light rating chip surfaces must use darkened brand colors instead of yellow/green on white",
                source.indexOf("boolean lightSurface = !backdropSurfaceMode && lightChrome;", style) > style
                        && source.indexOf("resolveRatingChipTextColor(color, lightSurface)", style) > style
                        && source.indexOf("return 0xFF0F7A4A;", resolve) > resolve
                        && source.indexOf("return 0xFF8A5A00;", resolve) > resolve
                        && source.indexOf("return 0xFFB42318;", resolve) > resolve);
        assertTrue("backdrop rating chip surfaces must remain dark glass with white text",
                source.indexOf("backdropSurfaceMode ? COLOR_BACKDROP_SURFACE_CONTROL_BG", style) > style
                        && source.indexOf("backdropSurfaceMode ? COLOR_FUSION_BACKDROP_TEXT", style) > style);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }
}
