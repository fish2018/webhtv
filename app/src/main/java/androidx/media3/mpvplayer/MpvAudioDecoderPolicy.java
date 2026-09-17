package androidx.media3.mpvplayer;

import java.util.List;

final class MpvAudioDecoderPolicy {

    private static final List<String> HARDWARE_FIRST_DECODERS = List.of(
            "aac_mediacodec",
            "mp3_mediacodec",
            "amrnb_mediacodec",
            "amrwb_mediacodec");
    private static final List<String> SOFTWARE_FIRST_DECODERS = List.of(
            "aac",
            "mp3",
            "amrnb",
            "amrwb");

    private MpvAudioDecoderPolicy() {
    }

    /**
     * Do not force MediaCodec when no compressed AudioTrack route is usable.
     * On affected devices the AAC MediaCodec decoder can backpressure without
     * producing audible PCM; the normal FFmpeg decoder is the safe output
     * path. Keep the hardware-first policy when the route is available.
     */
    static String decoderList(String audioSpdif) {
        return audioSpdif == null || audioSpdif.isBlank()
                ? String.join(",", SOFTWARE_FIRST_DECODERS)
                : String.join(",", HARDWARE_FIRST_DECODERS);
    }

    static String hardwareFirstDecoderList() {
        return String.join(",", HARDWARE_FIRST_DECODERS);
    }

}
