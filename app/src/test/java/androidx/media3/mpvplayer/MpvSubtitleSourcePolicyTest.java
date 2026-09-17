package androidx.media3.mpvplayer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.media3.common.C;

import org.junit.Test;

public class MpvSubtitleSourcePolicyTest {

    @Test
    public void usesFileDescriptorForAbsoluteLocalPaths() {
        assertTrue(MpvSubtitleSourcePolicy.requiresFileDescriptor(null, "/data/user/0/com.example/cache/subtitle.srt"));
        assertTrue(MpvSubtitleSourcePolicy.requiresFileDescriptor("file", "/data/user/0/com.example/cache/subtitle.srt"));
        assertTrue(MpvSubtitleSourcePolicy.requiresFileDescriptor(null, "/sdcard/Download/subtitle.srt"));
        assertTrue(MpvSubtitleSourcePolicy.requiresFileDescriptor("file", "/storage/emulated/0/Download/subtitle.srt"));
    }

    @Test
    public void leavesNetworkAndContentUrisToExistingResolvers() {
        assertFalse(MpvSubtitleSourcePolicy.requiresFileDescriptor("https", "/subtitle.srt"));
        assertFalse(MpvSubtitleSourcePolicy.requiresFileDescriptor("content", "/document/subtitle.srt"));
        assertFalse(MpvSubtitleSourcePolicy.requiresFileDescriptor(null, "relative/subtitle.srt"));
        assertFalse(MpvSubtitleSourcePolicy.requiresFileDescriptor(null, "//cdn.example.com/subtitle.srt"));
        assertFalse(MpvSubtitleSourcePolicy.requiresFileDescriptor("file", "//network/share/subtitle.srt"));
    }

    @Test
    public void selectsLavfFormatWhenDescriptorHasNoExtension() {
        assertEquals("srt", MpvSubtitleSourcePolicy.lavfFormat("/cache/subtitle.srt", null));
        assertEquals("ass", MpvSubtitleSourcePolicy.lavfFormat("/cache/subtitle.ass?download=1", null));
        assertEquals("webvtt", MpvSubtitleSourcePolicy.lavfFormat("/cache/subtitle", "text/vtt"));
        assertEquals("srt", MpvSubtitleSourcePolicy.lavfFormat("/cache/subtitle", "application/x-subrip"));
        assertNull(MpvSubtitleSourcePolicy.lavfFormat("/cache/subtitle.bin", "application/octet-stream"));
    }

    @Test
    public void explicitlySelectsDefaultSubtitle() {
        assertEquals("select", MpvSubtitleSourcePolicy.addMode(C.SELECTION_FLAG_DEFAULT));
        assertEquals("select", MpvSubtitleSourcePolicy.addMode(C.SELECTION_FLAG_DEFAULT | C.SELECTION_FLAG_FORCED));
        assertEquals("auto", MpvSubtitleSourcePolicy.addMode(C.SELECTION_FLAG_AUTOSELECT));
        assertEquals("auto", MpvSubtitleSourcePolicy.addMode(0));
    }
}
