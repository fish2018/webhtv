package com.fongmi.android.tv.player.exo;

import com.fongmi.android.tv.player.AudioPlaybackDiagnostics;

/** Ensures network protection never changes an output feature active in the current session. */
public final class ExoNetworkGuardEligibility {

    private ExoNetworkGuardEligibility() {
    }

    public static Decision resolve(Request request) {
        if (request == null || !request.enabled()) return Decision.blocked("disabled");
        if (!request.exo()) return Decision.blocked("exo-only");
        if (!request.vod()) return Decision.blocked("vod-only");
        if (!request.userUnitSpeed()) return Decision.blocked("user-speed");
        if (!request.speedCommandAvailable()) return Decision.blocked("speed-unsupported");
        if (request.tunnelingRequested()) return Decision.blocked("preserve-tunneling");
        if (request.audioOutputMode() == AudioPlaybackDiagnostics.OutputMode.UNKNOWN) {
            return Decision.blocked("audio-output-pending");
        }
        if (request.audioOutputMode() == AudioPlaybackDiagnostics.OutputMode.PASSTHROUGH) {
            return Decision.blocked("preserve-passthrough");
        }
        return new Decision(true, "eligible");
    }

    public record Request(boolean enabled, boolean exo, boolean vod, boolean userUnitSpeed,
                          boolean speedCommandAvailable, boolean tunnelingRequested,
                          AudioPlaybackDiagnostics.OutputMode audioOutputMode) {

        public Request {
            audioOutputMode = audioOutputMode == null
                    ? AudioPlaybackDiagnostics.OutputMode.UNKNOWN : audioOutputMode;
        }
    }

    public record Decision(boolean eligible, String reason) {

        private static Decision blocked(String reason) {
            return new Decision(false, reason);
        }
    }
}
