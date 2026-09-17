package com.fongmi.android.tv.player.engine;

import androidx.media3.common.Player;

final class SystemPlayerState {

    private SystemPlayerState() {
    }

    /**
     * Returns whether the loading indicator should be shown.
     * Suppresses loading for IDLE and ENDED states to avoid visual glitches
     * when the player is not actively preparing or buffering media.
     */
    static boolean loadingFor(int playbackState, boolean loading) {
        return loading && playbackState != Player.STATE_IDLE && playbackState != Player.STATE_ENDED;
    }
}
