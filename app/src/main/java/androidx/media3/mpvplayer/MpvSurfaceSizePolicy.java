package androidx.media3.mpvplayer;

final class MpvSurfaceSizePolicy {

    private static final String DIRECT_MEDIACODEC_VO = "mediacodec_embed";

    private MpvSurfaceSizePolicy() {
    }

    static boolean usesAndroidSurfaceSize(String vo) {
        return !DIRECT_MEDIACODEC_VO.equals(vo);
    }

    static boolean shouldApplyOsdSize(boolean requested, boolean surfaceValid,
                                      int width, int height) {
        return requested && surfaceValid && width > 0 && height > 0;
    }

    static String sizeValue(int width, int height) {
        if (width <= 0 || height <= 0) return null;
        return width + "x" + height;
    }
}
