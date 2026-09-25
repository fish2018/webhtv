package com.fongmi.android.tv.theme;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.media3.ui.DefaultTimeBar;

import com.fongmi.android.tv.setting.Setting;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;

import java.util.Locale;

/**
 * Applies the validated semantic palette at the Activity boundary.
 *
 * Resource attributes remain the primary path. This binder is the low-API fallback:
 * it binds only semantic Material controls and leaves video content and black player
 * scrims untouched. A failed or missing profile therefore falls back to the existing
 * Material resources without blocking the Activity.
 */
public final class ThemeController {

    private static final int[][] CONTROL_ICON_STATES = new int[][]{
            {android.R.attr.state_focused},
            {android.R.attr.state_selected},
            {android.R.attr.state_activated},
            {}
    };

    private static final int[][] LEANBACK_TEXT_STATES = new int[][]{
            {android.R.attr.state_focused},
            {android.R.attr.state_selected},
            {android.R.attr.state_activated},
            {-android.R.attr.state_enabled},
            {}
    };

    private ThemeController() {
    }

    public static void applyNightMode(Context context) {
        if (!isCustomThemeEnabled()) {
            if (AppCompatDelegate.getDefaultNightMode() != AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            }
            return;
        }
        ThemeProfile profile = ThemeProfileStore.load();
        int mode = ThemeProfile.MODE_DARK.equals(profile.mode)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : ThemeProfile.MODE_LIGHT.equals(profile.mode)
                ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != mode) AppCompatDelegate.setDefaultNightMode(mode);
    }

    public static ThemeTokens resolve(Context context) {
        if (!isCustomThemeEnabled()) return disabledTokens();
        boolean systemDark = (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        return ThemeResolver.resolve(ThemeProfileStore.load(), systemDark, Setting.getWallColor());
    }

    /** Returns zero when the profile deliberately disables content-based dynamic color. */
    public static int dynamicColor(Context context) {
        if (!isCustomThemeEnabled()) return 0;
        ThemeProfile profile = ThemeProfileStore.load();
        if (ThemeProfile.SEED_NONE.equals(profile.seedSource)) return 0;
        return resolve(context).primary();
    }

    public static void apply(Activity activity) {
        if (!isCustomThemeEnabled()) return;
        apply(activity.getWindow().getDecorView(), resolve(activity));
    }

    /** Applies stateful colors to leanback selectors without touching player surfaces. */
    public static void applyLeanback(Activity activity) {
        if (!isCustomThemeEnabled()) return;
        applyLeanback(activity.getWindow().getDecorView(), resolve(activity));
    }

    public static void apply(View root, ThemeTokens tokens) {
        if (!isCustomThemeEnabled() || root == null || tokens == null) return;
        ThemeProfile profile = ThemeProfileStore.load();
        if (isHighlightOnly(profile)) {
            applyHighlightView(root, tokens);
            return;
        }
        if (ThemeProfile.BACKGROUND_SOLID.equals(profile.background.type)) {
            root.setBackgroundColor(tokens.appBackground());
        }
        applyView(root, tokens);
    }

    public static void applyLeanback(View root, ThemeTokens tokens) {
        if (!isCustomThemeEnabled() || root == null || tokens == null) return;
        applyLeanbackView(root, tokens);
    }

    /**
     * Returns the opaque canvas color for the wallpaper overlay. The overlay is kept
     * separate from the wallpaper renderer so video/GIF lifecycle and cache behavior
     * remain unchanged.
     */
    public static int wallpaperScrim(ThemeTokens tokens) {
        if (!isCustomThemeEnabled()) return 0;
        if (ThemeProfile.BACKGROUND_SOLID.equals(tokens.backgroundType())) return tokens.appBackground();
        float alpha = ThemeProfile.BACKGROUND_TINTED_WALLPAPER.equals(tokens.backgroundType())
                ? tokens.scrimAlpha()
                : ThemeProfile.MODE_DARK.equals(tokens.mode()) ? 0.30f : 0.18f;
        int channel = Math.round(255f * Math.max(0f, Math.min(0.85f, alpha)));
        int tint = ThemeProfile.MODE_DARK.equals(tokens.mode()) ? Color.BLACK : tokens.primary();
        return (channel << 24) | (tint & 0x00FFFFFF);
    }

    private static boolean isCustomThemeEnabled() {
        if (!Setting.isThemeColorEnabled()) return false;
        ThemeProfile profile = ThemeProfileStore.load();
        if (!ThemeProfile.MODE_SYSTEM.equals(profile.mode)) return true;
        if (profile.background == null
                || !ThemeProfile.BACKGROUND_WALLPAPER.equals(profile.background.type)
                || profile.background.color != null
                || profile.background.scrimAlpha != 0f) return true;
        if (!ThemeProfile.SEED_NONE.equals(profile.seedSource) || profile.seedColor != null) return true;
        return !isEmpty(profile.colors == null ? null : profile.colors.light)
                || !isEmpty(profile.colors == null ? null : profile.colors.dark);
    }

    private static boolean isHighlightOnly(ThemeProfile profile) {
        if (profile == null || !ThemeProfile.MODE_SYSTEM.equals(profile.mode)) return false;
        if (profile.background == null
                || !ThemeProfile.BACKGROUND_WALLPAPER.equals(profile.background.type)
                || profile.background.color != null
                || profile.background.scrimAlpha != 0f) return false;
        if (!ThemeProfile.SEED_NONE.equals(profile.seedSource) || profile.seedColor != null) return false;
        return hasOnlyPrimary(profile.colors == null ? null : profile.colors.light)
                && isEmpty(profile.colors == null ? null : profile.colors.dark)
                || hasOnlyPrimary(profile.colors == null ? null : profile.colors.dark)
                && isEmpty(profile.colors == null ? null : profile.colors.light);
    }

    private static boolean hasOnlyPrimary(ThemeProfile.ColorSet colors) {
        if (colors == null || colors.primary == null) return false;
        ThemeProfile.ColorSet copy = colors.copy();
        copy.primary = null;
        return isEmpty(copy);
    }

    private static boolean isEmpty(ThemeProfile.ColorSet colors) {
        if (colors == null) return true;
        return colors.primary == null
                && colors.onPrimary == null
                && colors.primaryContainer == null
                && colors.onPrimaryContainer == null
                && colors.appBackground == null
                && colors.surface == null
                && colors.surfaceElevated == null
                && colors.onSurface == null
                && colors.onSurfaceVariant == null
                && colors.outline == null
                && colors.focus == null
                && colors.error == null;
    }

    private static ThemeTokens disabledTokens() {
        return new ThemeTokens(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                ThemeProfile.MODE_SYSTEM, ThemeProfile.BACKGROUND_WALLPAPER, 0f);
    }

    private static void applyHighlightView(View view, ThemeTokens tokens) {
        if (view == null) return;
        if (isPlayerRoot(view)) {
            applyPlayerControls(view, tokens);
            return;
        }
        if (view instanceof BottomNavigationView navigation) {
            ColorStateList currentIcons = navigation.getItemIconTintList();
            ColorStateList currentText = navigation.getItemTextColor();
            navigation.setItemIconTintList(highlightColors(tokens.primary(), currentIcons));
            navigation.setItemTextColor(highlightColors(tokens.primary(), currentText));
        } else if (view instanceof TabLayout tabs) {
            tabs.setSelectedTabIndicatorColor(tokens.primary());
            tabs.setTabTextColors(tabs.getTabTextColors().getDefaultColor(), tokens.primary());
        } else if (view instanceof FloatingActionButton fab) {
            fab.setBackgroundTintList(ColorStateList.valueOf(tokens.primaryContainer()));
            fab.setImageTintList(ColorStateList.valueOf(tokens.onPrimaryContainer()));
        } else if (view instanceof MaterialButton button) {
            applyButton(button, tokens);
        } else if (view instanceof ImageView image && isSemanticIcon(image)) {
            image.setImageTintList(controlIconColors(tokens));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) applyHighlightView(group.getChildAt(i), tokens);
        }
    }

    private static ColorStateList highlightColors(int selected, ColorStateList current) {
        int normal = current == null ? Color.WHITE : current.getDefaultColor();
        return new ColorStateList(new int[][]{{android.R.attr.state_checked}, {}},
                new int[]{selected, normal});
    }

    private static void applyView(View view, ThemeTokens tokens) {
        if (view == null) return;
        if (isPlayerRoot(view)) {
            applyPlayerControls(view, tokens);
            return;
        }
        if (view instanceof MaterialToolbar toolbar) {
            toolbar.setTitleTextColor(tokens.onSurface());
            toolbar.setSubtitleTextColor(tokens.onSurfaceVariant());
            toolbar.setBackgroundTintList(ColorStateList.valueOf(tokens.surface()));
        } else if (view instanceof BottomNavigationView navigation) {
            navigation.setBackgroundTintList(null);
            ColorStateList navigationColors = new ColorStateList(
                    new int[][]{{android.R.attr.state_checked}, {}},
                    new int[]{tokens.primary(), Color.WHITE});
            navigation.setItemIconTintList(navigationColors);
            navigation.setItemTextColor(navigationColors);
            return;
        } else if (view instanceof TabLayout tabs) {
            tabs.setSelectedTabIndicatorColor(tokens.primary());
            tabs.setTabTextColors(tokens.onSurfaceVariant(), tokens.primary());
        } else if (view instanceof FloatingActionButton fab) {
            fab.setBackgroundTintList(ColorStateList.valueOf(tokens.primaryContainer()));
            fab.setImageTintList(ColorStateList.valueOf(tokens.onPrimaryContainer()));
        } else if (view instanceof MaterialButton button) {
            applyButton(button, tokens);
        } else if (view instanceof MaterialCardView card) {
            card.setCardBackgroundColor(tokens.surface());
            card.setStrokeColor(tokens.outline());
        } else if (view instanceof ImageView image && isSemanticIcon(image)) {
            image.setImageTintList(controlIconColors(tokens));
        } else if (view instanceof TextView text && isSemanticText(text)) {
            text.setTextColor(textColor(text, tokens));
        } else if (isClickableSurface(view)) {
            ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(tokens.surface()));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) applyView(group.getChildAt(i), tokens);
        }
    }

    private static void applyLeanbackView(View view, ThemeTokens tokens) {
        if (view == null) return;
        if (isPlayerRoot(view)) {
            applyPlayerControls(view, tokens);
            return;
        }
        if (view instanceof TextView text && isSemanticText(text)) {
            text.setTextColor(leanbackTextColors(tokens));
        } else if (view instanceof ImageView image && isSemanticIcon(image)) {
            image.setImageTintList(leanbackIconColors(tokens));
        }
        if (isLeanbackSelectable(view)) {
            ViewCompat.setBackgroundTintList(view, leanbackBackgroundColors(tokens));
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) applyLeanbackView(group.getChildAt(i), tokens);
        }
    }

    private static void applyButton(MaterialButton button, ThemeTokens tokens) {
        String name = resourceName(button);
        boolean primary = name.contains("apply") || name.contains("confirm") || name.contains("save")
                || name.contains("submit") || name.contains("positive");
        boolean secondary = name.contains("cancel") || name.contains("reset") || name.contains("close")
                || name.contains("outline") || name.contains("more");
        if (!primary && !secondary) return;
        int background = primary ? tokens.primary() : tokens.surfaceElevated();
        int foreground = primary ? tokens.onPrimary() : tokens.primary();
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(foreground);
        button.setIconTint(ColorStateList.valueOf(foreground));
        button.setStrokeColor(ColorStateList.valueOf(tokens.outline()));
    }

    private static void applyPlayerControls(View root, ThemeTokens tokens) {
        if (root instanceof ImageView image) image.setImageTintList(playerIconColors(tokens));
        if (root instanceof DefaultTimeBar timeBar) {
            timeBar.setPlayedColor(tokens.primary());
            timeBar.setScrubberColor(tokens.focus());
            timeBar.setBufferedColor(Color.argb(150, ThemeColorUtil.red(tokens.primary()),
                    ThemeColorUtil.green(tokens.primary()), ThemeColorUtil.blue(tokens.primary())));
            timeBar.setUnplayedColor(Color.argb(90, 255, 255, 255));
        }
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) applyPlayerControls(group.getChildAt(i), tokens);
        }
    }

    private static ColorStateList controlIconColors(ThemeTokens tokens) {
        return new ColorStateList(CONTROL_ICON_STATES,
                new int[]{tokens.focus(), tokens.primary(), tokens.primary(), tokens.onSurfaceVariant()});
    }

    private static ColorStateList playerIconColors(ThemeTokens tokens) {
        return new ColorStateList(CONTROL_ICON_STATES,
                new int[]{tokens.focus(), tokens.primary(), tokens.primary(), Color.WHITE});
    }

    private static ColorStateList leanbackTextColors(ThemeTokens tokens) {
        return new ColorStateList(LEANBACK_TEXT_STATES,
                new int[]{tokens.onPrimary(), tokens.primary(), tokens.primary(), tokens.onSurfaceVariant(), tokens.onSurface()});
    }

    private static ColorStateList leanbackIconColors(ThemeTokens tokens) {
        return new ColorStateList(LEANBACK_TEXT_STATES,
                new int[]{tokens.onPrimary(), tokens.primary(), tokens.primary(), tokens.onSurfaceVariant(), tokens.onSurface()});
    }

    private static ColorStateList leanbackBackgroundColors(ThemeTokens tokens) {
        int focus = withAlpha(tokens.focus(), 0x66);
        int selected = withAlpha(tokens.primary(), 0x66);
        int disabled = withAlpha(tokens.outline(), 0x36);
        int normal = withAlpha(tokens.surface(), 0x22);
        return new ColorStateList(new int[][]{
                {android.R.attr.state_focused},
                {android.R.attr.state_pressed},
                {android.R.attr.state_activated},
                {android.R.attr.state_selected},
                {-android.R.attr.state_enabled},
                {}
        }, new int[]{focus, focus, selected, selected, disabled, normal});
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static boolean isLeanbackSelectable(View view) {
        return view.getBackground() != null && (view.isFocusable() || view.isClickable());
    }

    private static boolean isPlayerRoot(View view) {
        String name = resourceName(view);
        return "playercontrolroot".equals(name) || "detailcontrolhost".equals(name);
    }

    private static boolean isSemanticIcon(ImageView image) {
        String name = resourceName(image);
        return !name.isEmpty() && !name.contains("logo") && !name.contains("backdrop")
                && !name.contains("poster") && !name.contains("cover") && !name.equals("image")
                && (name.contains("nav") || name.contains("action") || name.contains("setting")
                || name.contains("search") || name.contains("filter") || name.contains("link")
                || name.contains("home") || name.contains("history") || name.contains("refresh")
                || name.contains("more") || name.contains("type"));
    }

    private static boolean isSemanticText(TextView text) {
        String name = resourceName(text);
        return !name.contains("player") && !name.contains("osd") && !name.contains("subtitle")
                && !name.contains("backdrop") && !name.contains("time") && !name.contains("duration");
    }

    private static int textColor(TextView text, ThemeTokens tokens) {
        String name = resourceName(text);
        if (name.contains("error") || name.contains("warning")) return tokens.error();
        if (name.contains("subtitle") || name.contains("summary") || name.contains("remark")
                || name.contains("hint") || name.contains("status")) return tokens.onSurfaceVariant();
        return tokens.onSurface();
    }

    private static boolean isClickableSurface(View view) {
        return view.isClickable() && view.getBackground() != null && !(view instanceof ViewGroup
                && (resourceName(view).contains("player") || resourceName(view).contains("control")));
    }

    private static String resourceName(View view) {
        if (view.getId() == View.NO_ID) return "";
        try {
            return view.getResources().getResourceEntryName(view.getId()).toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            return "";
        }
    }
}
