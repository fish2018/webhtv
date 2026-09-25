package com.fongmi.android.tv;

import com.fongmi.android.tv.bean.Style;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.utils.ResUtil;

public class Product {

    public static int getDeviceType() {
        return 0;
    }

    /** 阅读器判定内容不归自己管时，回退到本 flavor 的播放流程。 */
    public static void registerReaderFallback() {
        com.fongmi.android.tv.ui.novel.NovelRouter.fallbackLauncher = (activity, key, id, name, pic, mark) ->
                com.fongmi.android.tv.ui.activity.VideoActivity.startSkippingDispatch(activity, key, id, name, pic, mark, false, false, null, null, null, System.currentTimeMillis());
    }

    public static int getColumn() {
        return Math.abs(PlayerSetting.getSize() - 7);
    }

    public static int getColumn(Style style) {
        return style.isLand() ? getColumn() - 1 : getColumn();
    }

    public static int[] getSpec(Style style) {
        int column = getColumn(style);
        int space = ResUtil.dp2px(48) + ResUtil.dp2px(16 * (column - 1));
        if (style.isOval()) space += ResUtil.dp2px(column * 16);
        return getSpec(space, column, style);
    }

    public static int[] getSpec(int space, int column, Style style) {
        int base = ResUtil.getScreenWidth() - space;
        int width = base / column;
        int height = (int) (width / style.getRatio());
        return new int[]{width, height};
    }

    public static int getEms() {
        return Math.min(Math.round((float) ResUtil.getScreenWidth() / ResUtil.sp2px(24)), 35);
    }
}
