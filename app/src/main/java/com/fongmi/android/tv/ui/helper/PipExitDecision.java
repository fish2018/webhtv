package com.fongmi.android.tv.ui.helper;

public final class PipExitDecision {

    private PipExitDecision() {
    }

    /**
     * 退出画中画后，判断是否应结束播放。
     *
     * <p>不能依赖 onStart/onStop 与 onPictureInPictureModeChanged(false) 的回调时序——
     * Android（尤其是 14 上的国产 ROM）不保证二者先后，靠 isStop() 布尔位区分
     * “点 × 关闭”与“展开回全屏”会误判，导致点 × 后播放服务/前台通知不释放、退不出 App。
     *
     * <p>改为等生命周期 settle 后按 Activity 的最终状态判定：
     * <ul>
     *   <li>已在结束流程(finishing/destroyed)：无需重复触发。</li>
     *   <li>回到前台(atLeastStarted)：用户是“展开回全屏”，保留播放。</li>
     *   <li>仍处于 stopped 且未结束：用户点了 PiP 的 ×，应结束播放。</li>
     * </ul>
     *
     * @param atLeastStarted Activity 生命周期是否已回到 STARTED 及以上
     * @param finishing      Activity 是否正在结束(isFinishing)
     * @param destroyed      Activity 是否已销毁(isDestroyed)
     */
    public static boolean shouldFinishAfterPipExit(boolean atLeastStarted, boolean finishing, boolean destroyed) {
        return shouldFinishAfterPipExit(atLeastStarted, finishing, destroyed, false);
    }

    /**
     * @param keepPlayback 本次退出 PiP 是否为切换到音频后台等主动保留播放的流程
     */
    public static boolean shouldFinishAfterPipExit(boolean atLeastStarted, boolean finishing, boolean destroyed, boolean keepPlayback) {
        if (keepPlayback || finishing || destroyed) return false;
        return !atLeastStarted;
    }
}
