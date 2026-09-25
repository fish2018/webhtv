package com.fongmi.android.tv.ui.custom;

/**
 * 动态壁纸（视频/GIF）的起播决策。抽成纯 Java 是为了能直接单测——动态壁纸多占一路
 * MediaCodec 硬解实例并按壁纸帧率触发全窗口重绘，在电视盒子上会拖慢前台播放器。
 */
public final class WallMotionPolicy {

    public static final int TYPE_RES = 0;
    public static final int TYPE_GIF = 1;
    public static final int TYPE_VIDEO = 2;

    private WallMotionPolicy() {
    }

    /** 视频壁纸是否该起播。motion 关闭时降级为首帧静态图，不建解码器。 */
    public static boolean isVideoMotion(boolean motionEnabled, int wallType) {
        return motionEnabled && wallType == TYPE_VIDEO;
    }

    /** GIF 壁纸是否该动起来。 */
    public static boolean isGifMotion(boolean motionEnabled, int wallType) {
        return motionEnabled && wallType == TYPE_GIF;
    }

    /**
     * 页面已 onStop（例如后台收到换壁纸事件）时是否要记下"回来后重建视频壁纸"。
     * 此时不建解码器——白占的一路 MediaCodec 要等用户回到该页面才用得上。
     */
    public static boolean shouldDeferVideo(boolean motionEnabled, int wallType, boolean started) {
        return isVideoMotion(motionEnabled, wallType) && !started;
    }
}
