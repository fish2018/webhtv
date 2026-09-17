package com.fongmi.android.tv.ui.helper;

import android.content.Context;
import android.content.res.ColorStateList;
import android.widget.ImageView;

import com.google.android.material.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.ColorRoles;
import com.google.android.material.color.MaterialColors;

/** One palette per site dialog, including devices without platform dynamic-color support. */
public record SiteDialogTheme(int surface, int onSurface, int onSurfaceVariant,
                              ColorStateList accent, ColorStateList buttonBackground,
                              ColorStateList buttonText, ColorStateList buttonStroke) {

    public static SiteDialogTheme resolve(Context context, int seedColor) {
        int surface = color(context, R.attr.colorSurfaceContainer);
        int primary = color(context, androidx.appcompat.R.attr.colorPrimary);
        int onPrimary = color(context, R.attr.colorOnPrimary);
        int container = color(context, R.attr.colorPrimaryContainer);
        int onContainer = color(context, R.attr.colorOnPrimaryContainer);
        int onSurface = color(context, R.attr.colorOnSurface);
        int onSurfaceVariant = color(context, R.attr.colorOnSurfaceVariant);
        int outline = color(context, R.attr.colorOutline);
        if (seedColor != 0) {
            // Material 1.14.0 ColorRoles works without DynamicColors' Android 12 availability gate.
            // Use paired tonal colors, never the raw seed as a surface behind unrelated text colors.
            ColorRoles roles = MaterialColors.getColorRoles(context, seedColor);
            primary = roles.getAccent();
            onPrimary = roles.getOnAccent();
            container = roles.getAccentContainer();
            onContainer = roles.getOnAccentContainer();
            surface = MaterialColors.getSurfaceContainerFromSeed(context, seedColor);
        }
        return new SiteDialogTheme(surface, onSurface, onSurfaceVariant,
                ColorStateList.valueOf(primary),
                states(primary, container, surface),
                states(onPrimary, onContainer, onSurface),
                states(primary, primary, outline));
    }

    private static int color(Context context, int attr) {
        return MaterialColors.getColor(context, attr, SiteDialogTheme.class.getSimpleName());
    }

    private static ColorStateList states(int selected, int active, int normal) {
        // Keep selected first and disabled labels fully opaque, matching all site dialog modes.
        return new ColorStateList(new int[][]{
                {android.R.attr.state_selected},
                {android.R.attr.state_focused},
                {android.R.attr.state_pressed},
                {}
        }, new int[]{selected, active, active, normal});
    }

    public void apply(MaterialButton button) {
        button.setTextColor(buttonText);
        button.setBackgroundTintList(buttonBackground);
        button.setStrokeColor(buttonStroke);
        button.setRippleColor(accent.withAlpha(31));
    }

    public void tint(ImageView image) {
        image.setImageTintList(accent);
    }
}
