package com.fongmi.android.tv;

import android.content.Context;
import android.os.Build;
import android.view.DisplayCutout;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.MobileWindow;
import com.fongmi.android.tv.utils.ResUtil;

public class Product {

    public static int getDeviceType() {
        return 1;
    }

    /** 阅读器判定内容不归自己管时，回退到本 flavor 的播放流程。 */
    public static void registerReaderFallback() {
        com.fongmi.android.tv.ui.novel.NovelRouter.fallbackLauncher = (activity, key, id, name, pic, mark) ->
                com.fongmi.android.tv.ui.activity.VideoActivity.startSkippingDispatch(activity, key, id, name, pic, mark, false, null, null, null);
    }

    public static int getColumn(Context context) {
        int count = MobileWindow.isWide(context) ? 7 : 5;
        count = count + (ResUtil.isPad() ? 1 : 0);
        return Math.abs(PlayerSetting.getSize() - count);
    }

    public static int getColumn(Context context, Style style) {
        return style.isLand() ? getColumn(context) - 1 : getColumn(context);
    }

    public static int[] getSpec(Context context) {
        return getSpec(context, Style.rect());
    }

    public static int[] getSpec(Context context, int column) {
        Style style = Style.rect();
        int space = ResUtil.dp2px(32) + ResUtil.dp2px(16 * (column - 1)) + getCutout(context);
        return getSpec(context, space, column, style);
    }

    public static int[] getSpec(Context context, Style style) {
        int column = getColumn(context, style);
        int space = ResUtil.dp2px(32) + ResUtil.dp2px(16 * (column - 1)) + getCutout(context);
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(context, space, column, style);
    }

    private static int[] getSpec(Context context, int space, int column, Style style) {
        int base = MobileWindow.getWidth(context) - space;
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    private static int getCutout(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0;
        DisplayCutout cutout = ResUtil.getDisplay(context).getCutout();
        if (cutout == null) return 0;
        int left = cutout.getSafeInsetLeft();
        int right = cutout.getSafeInsetRight();
        return left | right;
    }
}
