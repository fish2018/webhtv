package androidx.media3.mpvplayer;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MpvAudioDecoderPolicyTest {

    @Test
    public void noCompressedOutputRouteUsesStableSoftwareAudioDecoders() {
        assertEquals(
                "aac,mp3,amrnb,amrwb",
                MpvAudioDecoderPolicy.decoderList(""));
        assertFalse(MpvAudioDecoderPolicy.decoderList("").contains("mediacodec"));
    }

    @Test
    public void compressedOutputRouteKeepsHardwareAudioPriority() {
        assertEquals(
                "aac_mediacodec,mp3_mediacodec,amrnb_mediacodec,amrwb_mediacodec",
                MpvAudioDecoderPolicy.decoderList("aac,mp3"));
        assertEquals(MpvAudioDecoderPolicy.hardwareFirstDecoderList(),
                MpvAudioDecoderPolicy.decoderList("aac,mp3"));
    }

    @Test
    public void performancePriorityManagesAudioDecoderOrder() {
        Map<String, String> candidates = new LinkedHashMap<>();
        candidates.put("ad", MpvAudioDecoderPolicy.decoderList(""));

        assertTrue(MpvOptionPriorityPolicy.isPerformanceManaged("ad"));
        assertEquals(candidates,
                MpvOptionPriorityPolicy.selectPerformanceOverlay(true, candidates));
        assertTrue(MpvOptionPriorityPolicy.selectPerformanceOverlay(
                false, candidates).isEmpty());
    }
}
