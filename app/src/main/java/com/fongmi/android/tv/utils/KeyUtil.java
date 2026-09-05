package com.fongmi.android.tv.utils;

import android.view.KeyEvent;

public class KeyUtil {

    public static boolean isActionDown(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_DOWN;
    }

    public static boolean isActionUp(KeyEvent event) {
        return event.getAction() == KeyEvent.ACTION_UP;
    }

    public static boolean isEnterKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_CENTER || event.getKeyCode() == KeyEvent.KEYCODE_ENTER || event.getKeyCode() == KeyEvent.KEYCODE_SPACE || event.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    public static boolean isUpKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP || event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_UP || event.getKeyCode() == KeyEvent.KEYCODE_PAGE_UP || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    }

    public static boolean isDownKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_CHANNEL_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_PAGE_DOWN || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_NEXT;
    }

    public static boolean isLeftKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT;
    }

    public static boolean isRightKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT;
    }

    public static boolean isSeekBackKey(KeyEvent event) {
        return isLeftKey(event) || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND;
    }

    public static boolean isSeekForwardKey(KeyEvent event) {
        return isRightKey(event) || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
    }

    public static boolean isBackKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_BACK;
    }

    public static boolean isDigitKey(KeyEvent event) {
        return event.getKeyCode() >= KeyEvent.KEYCODE_0 && event.getKeyCode() <= KeyEvent.KEYCODE_9 || event.getKeyCode() >= KeyEvent.KEYCODE_NUMPAD_0 && event.getKeyCode() <= KeyEvent.KEYCODE_NUMPAD_9;
    }

    public static boolean isMenuKey(KeyEvent event) {
        return isActionUp(event) && event.getKeyCode() == KeyEvent.KEYCODE_MENU;
    }

    public static boolean isMediaPlayPause(KeyEvent event) {
        return isActionUp(event) && (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE);
    }

    public static boolean isMediaStop(KeyEvent event) {
        return isActionUp(event) && event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_STOP;
    }

    public static boolean isMediaRewind(KeyEvent event) {
        return isActionUp(event) && isSeekBackKey(event) && !isLeftKey(event);
    }

    public static boolean isMediaFastForward(KeyEvent event) {
        return isActionUp(event) && isSeekForwardKey(event) && !isRightKey(event);
    }
}
