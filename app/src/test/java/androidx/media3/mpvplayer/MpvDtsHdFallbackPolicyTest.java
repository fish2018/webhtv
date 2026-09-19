package androidx.media3.mpvplayer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvDtsHdFallbackPolicyTest {

    @Test
    public void startupLogBurstNeverNeedsNativeAudioQueries() {
        String[] startupLogs = {
                "vo/gpu-next/libplacebo: Probing for vulkan devices:",
                "vo/gpu-next/libplacebo: Vulkan device properties:",
                "vd: Opening decoder hevc",
                "enhancement_pair: WebHTV Android FEL: software enhancement-layer decoder enabled",
                "cplayer: Failed sending hook command auto_profiles/on_load. Removing hook.",
                "ffmpeg/video: hevc_mediacodec: MediaCodec 0x0 failed to start",
                "ao/audiotrack: AudioTrack.write failed with -6"
        };
        for (int i = 0; i < 100; i++) {
            for (String line : startupLogs) {
                assertFalse(MpvDtsHdFallbackPolicy.shouldInspectAudioState("", line, false));
                assertFalse(MpvDtsHdFallbackPolicy.shouldInspectAudioState("dts,dts-hd", line, false));
            }
        }
    }

    @Test
    public void pcmOrCoreOnlyOutputNeverNeedsFallbackAudioQueries() {
        for (String codecs : new String[]{null, "", "ac3,eac3,dts,truehd"}) {
            assertFalse(MpvDtsHdFallbackPolicy.shouldInspectAudioState(
                    codecs, "ao/audiotrack: AudioTrack Init failed", false));
        }
    }

    @Test
    public void onlyUnattemptedDtsHdInitFailureCanInspectAudioState() {
        assertTrue(MpvDtsHdFallbackPolicy.shouldInspectAudioState(
                " ac3, DTS-HD ,dts", "ao/audiotrack: AudioTrack Init failed", false));
        assertTrue(MpvDtsHdFallbackPolicy.shouldInspectAudioState(
                "dts-hd", "ao/audiotrack: AudioTrack.getState failed", false));
        assertFalse(MpvDtsHdFallbackPolicy.shouldInspectAudioState(
                "dts-hd", "ao/audiotrack: AudioTrack Init failed", true));
        assertFalse(MpvDtsHdFallbackPolicy.shouldInspectAudioState("dts-hd", null, false));
    }

    @Test
    public void retriesDtsHdAudioTrackConstructorFailureAsCore() {
        MpvDtsHdFallbackPolicy.Decision decision =
                MpvDtsHdFallbackPolicy.evaluate(
                        "ac3,eac3,dts,dts-hd,truehd", "spdif-dtshd",
                        "DTS-HD MA", "ao/audiotrack: AudioTrack Init failed",
                        false);

        assertTrue(decision.retry());
        assertEquals("dts", decision.codecs());
    }

    @Test
    public void retriesDtsHdAudioTrackStateFailureAsCore() {
        MpvDtsHdFallbackPolicy.Decision decision =
                MpvDtsHdFallbackPolicy.evaluate(
                        "dts-hd", "spdif-dtshd",
                        "DTS-HD HRA",
                        "ao/audiotrack: AudioTrack.getState failed", false);

        assertTrue(decision.retry());
        assertEquals("dts", decision.codecs());
    }

    @Test
    public void ignoresRepeatedFailure() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "spdif-dtshd",
                "DTS-HD MA", "AudioTrack Init failed", true).retry());
    }

    @Test
    public void ignoresUnrelatedAudioTrackFailure() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "spdif-dtshd",
                "DTS-HD MA", "AudioTrack.write failed with -6",
                false).retry());
    }

    @Test
    public void ignoresDtsCoreAndPcmFormats() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "spdif-dts",
                "DTS", "AudioTrack Init failed", false).retry());
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "dts,dts-hd", "s16",
                "DTS", "AudioTrack Init failed", false).retry());
    }

    @Test
    public void usesDtsHdProfileWhenMpvHasAlreadyMovedToPcmFallback() {
        MpvDtsHdFallbackPolicy.Decision decision =
                MpvDtsHdFallbackPolicy.evaluate(
                        "dts,dts-hd", "s16", "DTS-HD MA",
                        "AudioTrack Init failed", false);

        assertTrue(decision.retry());
        assertEquals("dts", decision.codecs());
    }

    @Test
    public void ignoresConfigurationWithoutDtsHd() {
        assertFalse(MpvDtsHdFallbackPolicy.evaluate(
                "ac3,dts,truehd", "spdif-dtshd",
                "DTS-HD MA", "AudioTrack Init failed", false).retry());
    }
}
