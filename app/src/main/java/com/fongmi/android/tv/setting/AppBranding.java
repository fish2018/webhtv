package com.fongmi.android.tv.setting;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.ui.activity.HomeActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.github.catvod.utils.Prefers;

/** Owns the two supported built-in app branding choices. */
public final class AppBranding {

    public static final int ICON_CURRENT = 0;
    public static final int ICON_HISTORY = 1;

    private static final String ICON_KEY = "app_branding_icon";
    private static final String ALIAS_SUFFIX_CURRENT = "Current";
    private static final String ALIAS_SUFFIX_HISTORY = "History";
    private static final int LOGO_RENDER_SIZE = 192;

    private static int cachedLogoMode = -1;
    private static Drawable cachedLogo;

    private AppBranding() {
    }

    /** Old custom mode values intentionally migrate to the default built-in icon. */
    public static int normalizeIconMode(int mode) {
        return mode == ICON_HISTORY ? ICON_HISTORY : ICON_CURRENT;
    }

    @NonNull
    public static String getName(@NonNull Context context) {
        return context.getString(R.string.app_name);
    }

    /**
     * 首页标题优先展示站点/配置自己的名字，只有两者都空时才回落到应用名。
     * 固定返回应用名会让所有站源都显示成「默影视」，看不出当前在哪个源。
     */
    @NonNull
    public static String getDisplayName(@NonNull Context context, @Nullable String homeName, @Nullable String configName) {
        if (homeName != null && !homeName.trim().isEmpty()) return homeName;
        if (configName != null && !configName.trim().isEmpty()) return configName;
        return getName(context);
    }

    public static int getIconMode() {
        return normalizeIconMode(Prefers.getInt(ICON_KEY, ICON_CURRENT));
    }

    public static int getIconMode(@NonNull Context context) {
        return getIconMode();
    }

    public static void putIconMode(int mode) {
        Prefers.getPrefers().edit().putInt(ICON_KEY, normalizeIconMode(mode)).commit();
        cachedLogo = null;
        cachedLogoMode = -1;
    }

    @NonNull
    public static String getSummary(@NonNull Context context) {
        return context.getString(iconLabelResource(getIconMode(context)));
    }

    /**
     * 首帧先落品牌图标，避免闪白；随后若线路配置带 logo 就异步覆盖上去。
     * 只用品牌图标会让配置里自带的线路图标彻底失效。
     */
    public static void applyLogo(@NonNull ImageView view) {
        view.setImageDrawable(logoDrawable(view.getContext()));
        Config config = VodConfig.get().getConfig();
        String logo = config == null ? null : config.getLogo();
        if (logo != null && !logo.trim().isEmpty()) ImgUtil.logo(view, logo);
    }

    /**
     * Resolves the branded logo synchronously so the first frame never shows the other icon.
     * Adaptive icons and vectors are rasterized once because a circular crop cannot clip them.
     */
    @Nullable
    public static Drawable logoDrawable(@NonNull Context context) {
        int mode = getIconMode(context);
        if (cachedLogo != null && cachedLogoMode == mode) return cachedLogo;
        int resource = logoResource(mode);
        Drawable source;
        try {
            // Read the same alias icon that the launcher uses, keeping desktop and in-app branding identical.
            source = context.getPackageManager().getActivityIcon(launcherIntent(context).getComponent());
        } catch (PackageManager.NameNotFoundException ignored) {
            source = ContextCompat.getDrawable(context, resource);
        }
        if (source == null) return null;
        cachedLogo = toCircle(context.getResources(), source);
        cachedLogoMode = mode;
        return cachedLogo;
    }

    static int logoResource(int mode) {
        return normalizeIconMode(mode) == ICON_HISTORY
                ? R.drawable.ic_launcher_history : R.mipmap.ic_launcher;
    }

    @NonNull
    private static Drawable toCircle(@NonNull Resources resources, @NonNull Drawable source) {
        Bitmap bitmap = Bitmap.createBitmap(LOGO_RENDER_SIZE, LOGO_RENDER_SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        source.setBounds(0, 0, LOGO_RENDER_SIZE, LOGO_RENDER_SIZE);
        source.draw(canvas);
        RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(resources, bitmap);
        drawable.setCircular(true);
        drawable.setAntiAlias(true);
        return drawable;
    }

    public static int iconLabelResource(int mode) {
        return normalizeIconMode(mode) == ICON_HISTORY
                ? R.string.app_branding_icon_history : R.string.app_branding_icon_current;
    }

    public static void applyLauncherIcon(@NonNull Context context) {
        boolean history = getIconMode(context) == ICON_HISTORY;
        String homeActivity = HomeActivity.class.getName();
        setComponentEnabled(context, launcherAliasClassName(homeActivity, ICON_CURRENT), !history);
        setComponentEnabled(context, launcherAliasClassName(homeActivity, ICON_HISTORY), history);
    }

    static String launcherAliasClassName(@NonNull String homeActivityClassName, int mode) {
        return homeActivityClassName + (normalizeIconMode(mode) == ICON_HISTORY
                ? ALIAS_SUFFIX_HISTORY : ALIAS_SUFFIX_CURRENT);
    }

    @NonNull
    public static Intent launcherIntent(@NonNull Context context) {
        String alias = launcherAliasClassName(HomeActivity.class.getName(), getIconMode(context));
        return new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(new ComponentName(context.getPackageName(), alias));
    }

    private static void setComponentEnabled(@NonNull Context context, @NonNull String className, boolean enabled) {
        try {
            context.getPackageManager().setComponentEnabledSetting(
                    new ComponentName(context.getPackageName(), className),
                    enabled ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED : PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP);
        } catch (RuntimeException ignored) {
            // The next app launch retries the alias synchronization.
        }
    }
}
