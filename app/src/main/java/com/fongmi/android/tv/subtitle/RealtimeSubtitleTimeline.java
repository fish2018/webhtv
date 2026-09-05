package com.fongmi.android.tv.subtitle;

final class RealtimeSubtitleTimeline {

    private static final long CUE_END_GRACE_US = 500_000L;
    private static final long MAX_PRESENTATION_ERROR_US = 120_000L;
    private static final long MAX_CUE_DURATION_US = 6_500_000L;
    private static final long MIN_CUE_VISIBLE_US = 900_000L;
    private static final int MIN_CUE_DURATION_PERCENT = 75;

    private RealtimeSubtitleTimeline() {
    }

    static CueWindow planCueWindow(long segmentStartUs, long segmentEndUs, long capturedUs, long headroomUs) {
        long startUs = Math.max(0L, segmentStartUs);
        long endUs = Math.max(startUs, segmentEndUs);
        long presentedUs = presentedPositionUs(capturedUs, headroomUs);
        if (presentedUs - startUs > MAX_PRESENTATION_ERROR_US) return null;
        long cueEndUs = Math.min(endUs + CUE_END_GRACE_US, startUs + MAX_CUE_DURATION_US);
        long desiredUs = Math.max(0L, cueEndUs - startUs);
        long visibleUs = Math.max(0L, cueEndUs - Math.max(startUs, presentedUs));
        long minimumUs = Math.min(desiredUs, Math.max(MIN_CUE_VISIBLE_US, desiredUs * MIN_CUE_DURATION_PERCENT / 100L));
        return visibleUs < minimumUs ? null : new CueWindow(startUs, cueEndUs);
    }

    static long presentedPositionUs(long capturedUs, long headroomUs) {
        return capturedUs - Math.max(0L, headroomUs);
    }

    record CueWindow(long startUs, long endUs) {
    }
}
