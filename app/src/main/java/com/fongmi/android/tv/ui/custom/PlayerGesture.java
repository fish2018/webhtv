package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.content.Context;
import android.media.AudioManager;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.setting.LiveSetting;
import com.fongmi.android.tv.utils.BrightnessPolicy;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;

public class PlayerGesture extends GestureDetector.SimpleOnGestureListener implements ScaleGestureDetector.OnScaleGestureListener {

    private static final int DISTANCE = 100;

    private final ScaleGestureDetector scaleDetector;
    private final GestureDetector detector;
    private final AudioManager manager;
    private final Listener listener;
    private final Activity activity;
    private final View videoView;
    private final int[] videoLocation;
    private boolean changeBright;
    private boolean changeVolume;
    private boolean changeSpeed;
    private boolean changeScale;
    private boolean changeTime;
    private boolean multiTouch;
    private boolean animating;
    private boolean shortDrama;
    private boolean touch;
    private boolean lock;
    private float bright;
    private float anchorY = Float.NaN;
    private float downX;
    private float downY;
    private float volume;
    private float scale;
    private long time;

    public static PlayerGesture create(Activity activity, View videoView, Listener listener) {
        return new PlayerGesture(activity, videoView, listener);
    }

    private PlayerGesture(Activity activity, View videoView, Listener listener) {
        this.manager = (AudioManager) activity.getSystemService(Context.AUDIO_SERVICE);
        this.scaleDetector = new ScaleGestureDetector(activity, this);
        this.detector = new GestureDetector(activity, this);
        this.listener = listener;
        this.videoView = videoView;
        this.activity = activity;
        this.videoLocation = new int[2];
        this.scale = 1.0f;
    }

    public boolean onTouchEvent(MotionEvent e) {
        int action = e.getActionMasked();
        boolean end = action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL;
        if (action == MotionEvent.ACTION_DOWN) multiTouch = false;
        // 起点在这里记而不是 onDown：onDown 在边缘/缩放/锁定时会提前返回，
        // 而 changeScale 会在 onScaleEnd 之后 500ms 才解除，那段窗口里按下的手势
        // 拿不到自己的起点，长按转调节时会拿上一次的起点算位移，一按就跳。
        if (action == MotionEvent.ACTION_DOWN) {
            downX = e.getX();
            downY = e.getY();
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) multiTouch = true;
        if (end) listener.onTouchEnd();
        // 短剧的亮度/音量在长按之后才生效，此时 GestureDetector 已不再回调 onScroll，
        // 只能在这里自行处理移动事件，详见 handleAdjust。用实时指数判断而不是 multiTouch：
        // 后者在 ACTION_POINTER_UP 时不会复位，会把抬起一根手指后的单指调节一起挡掉。
        if (shortDrama && action == MotionEvent.ACTION_MOVE && e.getPointerCount() == 1 && !lock && !changeScale) handleAdjust(e);
        // 被父容器拦截或来电时只收到 CANCEL：倍速同样要交还，否则会卡在长按后的速率。
        if (changeSpeed && end) listener.onSpeedEnd();
        // seek 只在正常抬手时提交：CANCEL 属于手势被打断，维持原位置更符合预期。
        if (changeTime && action == MotionEvent.ACTION_UP) listener.onSeekEnd(time);
        boolean handled = e.getPointerCount() == 2 ? scaleDetector.onTouchEvent(e) : detector.onTouchEvent(e);
        // 必须等 detector 处理完再清：onFling 是 detector 在 UP 时回调的，它要读
        // changeSpeed/changeBright/changeVolume 才能判断这次抬手是否该切集。
        if (end) clearGesture();
        return handled;
    }

    /**
     * 一次手势结束就把功能标记清干净。
     * <p>
     * {@link #reset()} 挂在 onDown 上，而 onDown 在边缘/缩放/锁定时会提前返回，
     * 于是上一次手势的 changeBright/anchorY 可能活到下一次手势——短剧的亮度/音量
     * 由 onTouchEvent 直接驱动，不再有 onScroll 的兜底，残留状态会直接造成
     * 「没长按就跳亮度」。所以收尾统一在这里做，不依赖下一次 onDown 是否被接受。
     */
    private void clearGesture() {
        changeTime = false;
        changeSpeed = false;
        changeBright = false;
        changeVolume = false;
        anchorY = Float.NaN;
        // 不动 touch：它表示「本次手势还没选定功能」，是就绪态而非残留动作。
        // 在这里清成 false 会让缩放后 500ms 内（changeScale 还没解除时）起手的那一次
        // 拖动选不到任何功能，白滑一次。它由 reset() 置真、checkFunc 置假，无需插手。
    }

    public void resetScale() {
        if (scale == 1.0f) return;
        videoView.animate().scaleX(1.0f).scaleY(1.0f).translationX(0f).translationY(0f).setDuration(250).withEndAction(() -> {
            videoView.setPivotY(videoView.getHeight() / 2.0f);
            videoView.setPivotX(videoView.getWidth() / 2.0f);
            scale = 1.0f;
        }).start();
    }

    public void setLock(boolean lock) {
        this.lock = lock;
    }

    /**
     * 短剧竖屏形态下换用另一套手势轴向，参考红果短剧、抖音等主流竖屏播放器：
     * 上下滑切上下集（全屏可用），亮度/音量改为长按后再上下滑，左半亮度右半音量。
     * <p>
     * 原来「中间竖滑切集 + 左右 1/4 竖滑调亮度音量」在铺满屏幕的竖屏短剧里极易误触
     * （用户反馈）：想切集却滑到了边缘，调亮度又常常切了集。
     */
    public void setShortDrama(boolean shortDrama) {
        this.shortDrama = shortDrama;
    }

    private boolean isMultiple(MotionEvent e) {
        return e.getPointerCount() > 1;
    }

    private boolean isEdge(MotionEvent e) {
        int width = getVideoWidth();
        int height = getVideoHeight();
        if (width <= 0 || height <= 0) return false;
        int edge = ResUtil.dp2px(24);
        float x = getVideoX(e);
        float y = getVideoY(e);
        return x < edge || x > width - edge || y < edge || y > height - edge;
    }

    private boolean isSide(MotionEvent e) {
        int width = getVideoWidth();
        if (width <= 0) return false;
        int four = width / 4;
        float x = getVideoX(e);
        return x <= four || x >= four * 3;
    }

    private int getVideoWidth() {
        int width = videoView.getWidth();
        return width > 0 ? width : videoView.getMeasuredWidth();
    }

    private int getVideoHeight() {
        int height = videoView.getHeight();
        return height > 0 ? height : videoView.getMeasuredHeight();
    }

    private float getVideoX(MotionEvent e) {
        videoView.getLocationOnScreen(videoLocation);
        return e.getRawX() - videoLocation[0];
    }

    private float getVideoY(MotionEvent e) {
        videoView.getLocationOnScreen(videoLocation);
        return e.getRawY() - videoLocation[1];
    }

    private void reset() {
        time = 0;
        touch = true;
        anchorY = Float.NaN;
        changeTime = false;
        changeSpeed = false;
        changeBright = false;
        changeVolume = false;
        bright = Util.getBrightness(activity);
        volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        if (isMultiple(e) || isEdge(e) || changeScale || lock) return true;
        reset();
        return true;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        if (multiTouch || isEdge(e) || changeScale || lock) return;
        listener.onSpeedUp();
        changeSpeed = true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, @NonNull MotionEvent e2, float distanceX, float distanceY) {
        if (isMultiple(e1) || isEdge(e1) || changeScale || lock || changeSpeed) return true;
        float deltaX = e2.getX() - e1.getX();
        float deltaY = e1.getY() - e2.getY();
        if (touch) checkFunc(Math.abs(deltaX), Math.abs(deltaY), e2);
        if (changeTime) listener.onSeeking(time = (long) (deltaX * 50));
        if (changeBright) setBright(deltaY);
        if (changeVolume) setVolume(deltaY);
        return true;
    }

    /**
     * 短剧的「长按后上下滑调亮度/音量」。
     * <p>
     * 不能挂在 {@link #onScroll} 上：GestureDetector 触发长按后会把后续 ACTION_MOVE
     * 全部吞掉（mInLongPress 分支直接 break），onScroll 再也不会回调，所以这条手势
     * 必须由 onTouchEvent 自己驱动。
     */
    private void handleAdjust(MotionEvent e) {
        // 只认「本次手势自己长按出来」的调节，避免上一次手势的残留标记把亮度带跑。
        if (!changeSpeed && Float.isNaN(anchorY)) return;
        if (changeSpeed) {
            float dx = e.getX() - downX;
            float dy = e.getY() - downY;
            if (Math.abs(dy) <= Math.abs(dx) || Math.abs(dy) < ResUtil.dp2px(20)) return;
            startAdjust(e);
        }
        // 以「长按转调节」的那一刻为基准，避免把长按期间的位移一次性算进亮度/音量。
        float deltaY = anchorY - e.getY();
        if (changeBright) setBright(deltaY);
        if (changeVolume) setVolume(deltaY);
    }

    /**
     * 长按倍速中途转为亮度/音量调节：先把倍速交还，再以当前触点为新基准。
     */
    private void startAdjust(MotionEvent e) {
        listener.onSpeedEnd();
        changeSpeed = false;
        touch = false;
        anchorY = e.getY();
        bright = Util.getBrightness(activity);
        volume = manager.getStreamVolume(AudioManager.STREAM_MUSIC);
        checkSide(e);
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent e) {
        if (isMultiple(e) || isEdge(e) || changeScale) return true;
        listener.onDoubleTap();
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        if (isMultiple(e) || isEdge(e) || changeScale) return true;
        listener.onSingleTap();
        return true;
    }

    @Override
    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
        // 短剧上下滑切集不再受左右 1/4 的亮度/音量分区限制（那两块已改为长按触发），
        // 但 isEdge 的 24dp 边框仍然保留：那里要让给系统的返回/通知栏手势。
        if (isMultiple(e1) || isEdge(e1) || (!shortDrama && isSide(e1)) || changeScale || lock || animating) return true;
        // 这次手势已经用于调亮度/音量或倍速，抬手不应再被当成切集。
        if (changeSpeed || changeBright || changeVolume) return true;
        checkFunc(e1, e2);
        return true;
    }

    private void checkFunc(float distanceX, float distanceY, MotionEvent e2) {
        if ((float) Math.sqrt(distanceX * distanceX + distanceY * distanceY) < ResUtil.dp2px(20)) return;
        // 短剧的上下滑整屏留给切集（onFling），亮度/音量改由 startAdjust（长按后再滑）接管，
        // 所以这里只保留横滑拖进度，不再按左右 1/4 分派亮度/音量。
        if (distanceX >= distanceY) changeTime = true;
        else if (!shortDrama && isSide(e2)) checkSide(e2);
        touch = false;
    }

    private void checkFunc(MotionEvent e1, MotionEvent e2) {
        float dx = e2.getX() - e1.getX();
        float dy = e2.getY() - e1.getY();
        double angle = Math.toDegrees(Math.atan2(Math.abs(dy), Math.abs(dx)));
        // 短剧的切集动画沿用同一套竖向回弹，但不跟随直播的「反转」开关：
        // 竖屏短剧里上滑=下一集是抖音/红果的既定语义，反转会让用户完全迷失。
        boolean invert = !shortDrama && LiveSetting.isInvert();
        if (angle > 70 && e1.getY() - e2.getY() > DISTANCE) {
            videoView.animate().translationYBy(ResUtil.dp2px(invert ? 24 : -24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingUp).withEndAction(() -> animating = false).start()).start();
        } else if (angle > 70 && e2.getY() - e1.getY() > DISTANCE) {
            videoView.animate().translationYBy(ResUtil.dp2px(invert ? -24 : 24)).setDuration(150).withStartAction(() -> animating = true).withEndAction(() -> videoView.animate().translationY(0).setDuration(100).withStartAction(listener::onFlingDown).withEndAction(() -> animating = false).start()).start();
        }
    }

    private void checkSide(MotionEvent e2) {
        int width = getVideoWidth();
        float x = getVideoX(e2);
        if (x > width / 2f) changeVolume = true;
        else changeBright = true;
    }

    private void setBright(float deltaY) {
        int height = Math.max(videoView.getMeasuredHeight(), 1);
        float brightness = BrightnessPolicy.scroll(bright, deltaY, height);
        WindowManager.LayoutParams attributes = activity.getWindow().getAttributes();
        attributes.screenBrightness = brightness;
        activity.getWindow().setAttributes(attributes);
        listener.onBright((int) (brightness * 100));
    }

    private void setVolume(float deltaY) {
        int height = Math.max(videoView.getMeasuredHeight(), 1);
        int maxVolume = manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        if (maxVolume <= 0) return;
        float deltaV = deltaY * 2.0f / height * maxVolume;
        float index = volume + deltaV;
        if (index > maxVolume) index = maxVolume;
        if (index < 0) index = 0;
        manager.setStreamVolume(AudioManager.STREAM_MUSIC, (int) index, 0);
        listener.onVolume((int) (index / maxVolume * 100.0f));
    }

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) {
        if (changeBright || changeVolume || changeSpeed || changeTime || lock) return changeScale = false;
        return changeScale = true;
    }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) {
        App.post(() -> changeScale = false, 500);
    }

    @Override
    public boolean onScale(@NonNull ScaleGestureDetector detector) {
        scale *= detector.getScaleFactor();
        scale = Math.max(1.0f, Math.min(scale, 5.0f));
        videoView.setPivotX(detector.getFocusX());
        videoView.setPivotY(detector.getFocusY());
        videoView.setScaleX(scale);
        videoView.setScaleY(scale);
        return true;
    }

    public interface Listener {

        void onSeeking(long time);

        void onSeekEnd(long time);

        void onSpeedUp();

        void onSpeedEnd();

        void onBright(int progress);

        void onVolume(int progress);

        void onFlingUp();

        void onFlingDown();

        void onSingleTap();

        void onDoubleTap();

        void onTouchEnd();
    }
}
