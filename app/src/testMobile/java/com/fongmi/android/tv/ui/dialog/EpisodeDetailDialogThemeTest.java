package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EpisodeDetailDialogThemeTest {

    @Test
    public void tvEpisodeAndMovieDetailsApplyResolvedThemeBeforeShowing() throws Exception {
        String source = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));

        assertTrue("TV episode detail must resolve the configured TMDB detail theme",
                source.contains("Setting.resolveTmdbDetailLightTheme(Setting.getTmdbDetailTheme()"));

        int firstLayout = source.indexOf("View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);");
        int secondLayout = source.indexOf("View view = LayoutInflater.from(activity).inflate(R.layout.dialog_episode_detail, null);", firstLayout + 1);
        assertThemeAppliedBeforeShow(source, firstLayout, secondLayout, "episode detail");
        assertThemeAppliedBeforeShow(source, secondLayout, source.indexOf("private static void showSimpleDialog", secondLayout), "movie detail");
    }

    @Test
    public void tvEpisodeDetailThemeCoversEveryReadableSurface() throws Exception {
        String source = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "dialog_episode_detail.xml")));

        assertTrue("TV episode detail root needs an id for runtime theme application",
                layout.contains("android:id=\"@+id/root\""));
        assertTrue("TV episode overview label needs an id for runtime theme application",
                layout.contains("android:id=\"@+id/overviewLabel\""));
        assertFalse("TV episode detail must not default to a full-screen white surface",
                layout.contains("android:background=\"#FFFFFF\""));

        assertTrue(source.contains("root.setBackgroundColor(background);"));
        assertTrue(source.contains("stillCard.setCardBackgroundColor(imageBackground);"));
        assertTrue(source.contains("title.setTextColor(primary);"));
        assertTrue(source.contains("originalName.setTextColor(secondary);"));
        assertTrue(source.contains("date.setTextColor(secondary);"));
        assertTrue(source.contains("runtime.setTextColor(secondary);"));
        assertTrue(source.contains("overviewLabel.setTextColor(primary);"));
        assertTrue(source.contains("overview.setTextColor(body);"));
        assertTrue(source.contains("photosLabel.setTextColor(primary);"));
        assertTrue(source.contains("guestsLabel.setTextColor(primary);"));
    }

    @Test
    public void tvEpisodeGuestCardsReceiveResolvedTheme() throws Exception {
        String source = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));

        assertTrue("preloaded media binding must receive the resolved theme",
                source.contains("bindPreloadedMedia(activity, preloadedPhotos, preloadedGuests, light,"));
        assertTrue("asynchronous media loading must receive the resolved theme",
                source.contains("loadEpisodeMedia(activity, tmdbEpisode, site, light,"));
        assertTrue("both guest adapter creation paths must apply the resolved theme",
                countOccurrences(source, "guestAdapter.setLight(light);") >= 2);
    }

    @Test
    public void cinemaEpisodeGuestCardsReuseOuterHorizontalLayoutWithoutPhotoRounding() throws Exception {
        String adapter = read(findMainJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "adapter", "TmdbPersonAdapter.java")));
        String mobileDialog = read(findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));
        String leanbackDialog = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));
        String mobileLayout = read(findMainResPath().resolve(Path.of(
                "layout", "dialog_episode_detail.xml")));

        assertTrue("cinema guest cards must reuse the outer actor rail's horizontal card geometry",
                adapter.contains("params.width = dp(holder.itemView, cinema ? 250 : 90);")
                        && adapter.contains("holder.binding.content.setOrientation(cinema ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);")
                        && adapter.contains("photoParams.width = dp(holder.itemView, cinema ? 86 : 90);")
                        && adapter.contains("photoParams.height = dp(holder.itemView, cinema ? 86 : 118);"));
        assertFalse("guest photos must not receive an independent rounded outline",
                adapter.contains("setRoundedPhoto") || adapter.contains("ROUNDED_PHOTO_OUTLINE")
                        || adapter.contains("setOutlineProvider"));
        assertTrue("mobile episode guest cards must switch to the outer actor rail layout only in cinema style",
                mobileDialog.contains("adapter.setCinema(cinema);")
                        && mobileDialog.contains("boolean cinema = Setting.isTmdbCinemaStyle();")
                        && mobileDialog.contains("params.height = ResUtil.dp2px(104);"));
        assertTrue("both TV episode-media paths must switch guest cards to the cinema layout",
                countOccurrences(leanbackDialog, "guestAdapter.setCinema(cinema);") >= 2
                        && leanbackDialog.contains("guestsGrid.setRowHeight(ResUtil.dp2px(cinema ? 90 : 154));")
                        && leanbackDialog.contains("params.height = ResUtil.dp2px(104);"));
        assertFalse("the mobile episode detail must not render a cancel button",
                mobileLayout.contains("android:id=\"@+id/close\"")
                        || mobileDialog.contains("R.id.close"));
    }

    @Test
    public void mobileEpisodeDetailKeepsBottomGuestCardsInsideStableScrollBounds() throws Exception {
        String dialog = read(findMobileJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));
        String layout = read(findMainResPath().resolve(Path.of(
                "layout", "dialog_episode_detail.xml")));

        assertTrue("mobile detail must disable nested scrolling for its horizontal rails",
                dialog.contains("view.setNestedScrollingEnabled(false);")
                        && dialog.contains("view.setOverScrollMode(View.OVER_SCROLL_NEVER);"));
        assertTrue("mobile detail must disable parent overscroll and leave a bottom safety inset",
                layout.contains("android:overScrollMode=\"never\"")
                        && layout.contains("android:paddingBottom=\"12dp\""));
    }

    @Test
    public void tvEpisodePhotosUseUnifiedYellowFocusStrokeWithoutGrayOverlay() throws Exception {
        String adapter = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "adapter", "EpisodePhotoAdapter.java")));
        String dialog = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));

        assertTrue("episode photos should use the shared yellow focus stroke",
                adapter.contains("private final MaterialCardView card;")
                        && adapter.contains("card = (MaterialCardView) itemView;")
                        && adapter.contains("TmdbCardFocusHelper.bind(card")
                        && adapter.contains("light ? 0x33647480 : 0x33FFFFFF"));
        assertTrue("episode photos should receive the resolved dialog theme",
                adapter.contains("public void setLight(boolean light)")
                        && countOccurrences(dialog, "photoAdapter.setLight(light);") >= 2);
        assertFalse("episode photo focus must not dim or tint the image",
                adapter.contains("setAlpha(") || adapter.contains("setColorFilter("));
    }

    @Test
    public void tvEpisodeGuestCardsNavigateUpThroughPhotosBeforePoster() throws Exception {
        String source = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "dialog_episode_detail.xml")));

        String guestMarker = "if (focus != null && (focus == guestsGrid || isDescendantOf(focus, guestsGrid)))";
        String photoMarker = "if (focus != null && (focus == photosGrid || isDescendantOf(focus, photosGrid)))";
        int guestBranch = source.indexOf(guestMarker);
        int photoBranch = source.indexOf(photoMarker, guestBranch);
        assertTrue("guest cards need a dedicated upward-navigation branch", guestBranch >= 0);
        assertTrue("photo cards must be handled after guest cards", photoBranch > guestBranch);

        String guestNavigation = source.substring(guestBranch, photoBranch);
        assertTrue("guest cards should move to photos when the photo grid has data",
                guestNavigation.contains("requestGridFocus(photosGrid)"));
        assertTrue("guest cards should fall back to the poster when photos are unavailable",
                guestNavigation.contains("stillCard.requestFocus();"));

        int focusHelper = source.indexOf("private static boolean requestGridFocus");
        int focusHelperEnd = source.indexOf("private static boolean isSameOrDescendantOf", focusHelper);
        String focusHelperBody = source.substring(focusHelper, focusHelperEnd);
        assertTrue("guest-to-photo navigation must check photo visibility",
                focusHelperBody.contains("grid.getVisibility() != View.VISIBLE"));
        assertTrue("guest-to-photo navigation must check photo data",
                focusHelperBody.contains("grid.getAdapter().getItemCount() == 0"));

        int guestsGrid = layout.indexOf("android:id=\"@+id/guestsGrid\"");
        int guestsEnd = layout.indexOf("/>", guestsGrid);
        assertTrue("guest grid should declare photos as its upward focus target",
                guestsGrid >= 0 && guestsEnd > guestsGrid
                        && layout.substring(guestsGrid, guestsEnd).contains("android:nextFocusUp=\"@id/photosGrid\""));
    }

    @Test
    public void tvEpisodePosterNavigatesDownToTheFirstAvailablePhotoCard() throws Exception {
        String source = read(findLeanbackJavaPath().resolve(Path.of(
                "com", "fongmi", "android", "tv", "ui", "dialog", "EpisodeDetailDialog.java")));
        String layout = read(findLeanbackResPath().resolve(Path.of("layout", "dialog_episode_detail.xml")));

        assertTrue("poster focus must be recognized even when a nested child owns focus",
                source.contains("isSameOrDescendantOf(focus, stillCard)"));
        assertTrue("down navigation must focus an actual grid card",
                source.contains("grid.setSelectedPosition(position, holder -> holder.itemView.requestFocus());"));
        assertTrue("poster must declare the photo grid as its downward focus target",
                layout.contains("android:nextFocusDown=\"@id/photosGrid\""));
    }

    private static void assertThemeAppliedBeforeShow(String source, int start, int end, String label) {
        assertTrue("Missing " + label + " layout block", start >= 0 && end > start);
        String block = source.substring(start, end);
        int resolve = block.indexOf("boolean light = resolveLightTheme(activity);");
        int apply = block.indexOf("applyTheme(view, light);");
        int show = block.indexOf("alertDialog.show();");
        assertTrue(label + " must resolve the theme after inflating the layout", resolve > 0);
        assertTrue(label + " must apply the theme before showing the dialog", apply > resolve && show > apply);
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path findMainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }

    private static Path findMobileJavaPath() {
        Path moduleRelative = Path.of("src", "mobile", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "mobile", "java");
    }

    private static Path findMainResPath() {
        Path moduleRelative = Path.of("src", "main", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "res");
    }

    private static Path findLeanbackJavaPath() {
        Path moduleRelative = Path.of("src", "leanback", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "java");
    }

    private static Path findLeanbackResPath() {
        Path moduleRelative = Path.of("src", "leanback", "res");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "leanback", "res");
    }
}
