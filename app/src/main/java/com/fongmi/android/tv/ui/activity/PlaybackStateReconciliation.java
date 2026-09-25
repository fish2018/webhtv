package com.fongmi.android.tv.ui.activity;

import androidx.media3.common.Player;

final class PlaybackStateReconciliation {

    private PlaybackStateReconciliation() {
    }

    static boolean shouldReplayReady(String requestedKey, String preparedKey, String managerKey,
                                     String managerMediaId, String controllerMediaId,
                                     int managerState, int controllerState) {
        return requestedKey != null
                && requestedKey.equals(preparedKey)
                && requestedKey.equals(managerKey)
                && requestedKey.equals(managerMediaId)
                && requestedKey.equals(controllerMediaId)
                && managerState == Player.STATE_READY
                && controllerState == Player.STATE_READY;
    }
}
