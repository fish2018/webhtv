package com.fongmi.android.tv.player.exo;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.Tracks;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class TrackUtilTest {

    @Test
    public void returnsTheOnlySelectedFormat() {
        Format selected = video("video/avc", 1920, 1080);

        assertEquals(selected, TrackUtil.onlySelectedFormat(List.of(selected)));
    }

    @Test
    public void adaptiveSelectionWithMultipleCandidatesStaysUnknown() {
        Format low = video("video/avc", 1280, 720);
        Format high = video("video/avc", 3840, 2160);

        assertNull(TrackUtil.onlySelectedFormat(List.of(low, high)));
    }

    @Test
    public void uniqueActiveFormat_narrowsAdaptiveSelectionToTheDecodedTrack() {
        Format low = variant("1", "video/avc", 256, 144, 290_000);
        Format high = variant("2", "video/avc", 1920, 1080, 5_420_000);
        Tracks tracks = videoTracks(new Format[]{low, high}, new boolean[]{true, true});
        // 解码格式与清单里的 variant 是两个不同实例，字段也不完全一致，必须走 sameRendition 而不是 equals。
        Format decoded = new Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).setAverageBitrate(5_420_000).build();

        assertSame(high, TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, decoded));
    }

    @Test
    public void uniqueActiveFormat_doesNotConfuseSameResolutionCodecVariants() {
        Format avc = variant("3", "video/avc", 1920, 1080, Format.NO_VALUE);
        Format hevc = variant("4", "video/hevc", 1920, 1080, Format.NO_VALUE);
        Tracks tracks = videoTracks(new Format[]{avc, hevc}, new boolean[]{true, true});
        Format decodedHevc = new Format.Builder().setSampleMimeType("video/hevc").setWidth(1920).setHeight(1080).build();

        assertSame(hevc, TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, decodedHevc));
    }

    @Test
    public void uniqueActiveFormat_staysUnknownWhenNothingDistinguishesSameResolutionTracks() {
        Format first = variant("5", null, 1920, 1080, Format.NO_VALUE);
        Format second = variant("6", null, 1920, 1080, Format.NO_VALUE);
        Tracks tracks = videoTracks(new Format[]{first, second}, new boolean[]{true, true});
        Format decoded = new Format.Builder().setWidth(1920).setHeight(1080).build();

        assertNull(TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, decoded));
    }

    @Test
    public void uniqueActiveFormat_keepsPlayerFlagsWhenOnlyOneTrackIsSelected() {
        Format low = video("video/avc", 256, 144);
        Format high = video("video/avc", 1920, 1080);
        Tracks tracks = videoTracks(new Format[]{low, high}, new boolean[]{false, true});

        assertNull(TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, high));
    }

    @Test
    public void uniqueActiveFormat_keepsPlayerFlagsWhenDecodedTrackIsUnknown() {
        Format low = variant("1", "video/avc", 256, 144, 290_000);
        Format high = variant("2", "video/avc", 1920, 1080, 5_420_000);
        Tracks tracks = videoTracks(new Format[]{low, high}, new boolean[]{true, true});

        assertNull("播放器还没报出解码格式时必须退回原始选中标记",
                TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, null));
    }

    @Test
    public void uniqueActiveFormat_staysUnknownWhenDecodedResolutionMatchesNoTrack() {
        Format low = variant("1", "video/avc", 256, 144, 290_000);
        Format high = variant("2", "video/avc", 1920, 1080, 5_420_000);
        Tracks tracks = videoTracks(new Format[]{low, high}, new boolean[]{true, true});
        Format decoded = new Format.Builder().setSampleMimeType("video/avc").setWidth(1280).setHeight(720).setAverageBitrate(2_970_000).build();

        assertNull(TrackUtil.uniqueActiveFormat(tracks, C.TRACK_TYPE_VIDEO, decoded));
    }

    @Test
    public void sameRendition_matchesTheManifestVariantBehindTheDecodedFormat() {
        Format manifest = variant("2", "video/avc", 1920, 1080, 5_420_000);
        Format decoded = new Format.Builder().setSampleMimeType("video/avc").setWidth(1920).setHeight(1080).build();

        assertTrue(TrackUtil.sameRendition(manifest, decoded));
    }

    @Test
    public void sameRendition_rejectsADifferentCodecAtTheSameResolution() {
        Format avc = variant("3", "video/avc", 1920, 1080, Format.NO_VALUE);
        Format decodedHevc = new Format.Builder().setSampleMimeType("video/hevc").setWidth(1920).setHeight(1080).build();

        assertFalse("同分辨率的 H.264 与 HEVC 是两条轨道，不能互相当成同一条",
                TrackUtil.sameRendition(avc, decodedHevc));
    }

    @Test
    public void sameRendition_rejectsADifferentCodecEvenWhenBitratesAgree() {
        Format avc = variant("3", "video/avc", 1920, 1080, 5_420_000);
        Format decodedHevc = new Format.Builder().setSampleMimeType("video/hevc").setWidth(1920).setHeight(1080).setAverageBitrate(5_420_000).build();

        // 码率相同也不能靠码率判定同一条，编码不同就是两条轨道。
        assertFalse(TrackUtil.sameRendition(avc, decodedHevc));
    }

    @Test
    public void sameRendition_rejectsWhenBothBitratesAreUnknown() {
        Format manifest = variant("5", null, 1920, 1080, Format.NO_VALUE);
        Format decoded = new Format.Builder().setWidth(1920).setHeight(1080).build();

        assertFalse(TrackUtil.sameRendition(manifest, decoded));
    }

    private static Tracks videoTracks(Format[] formats, boolean[] selected) {
        int[] support = new int[formats.length];
        for (int i = 0; i < support.length; i++) support[i] = C.FORMAT_HANDLED;
        return new Tracks(List.of(new Tracks.Group(new TrackGroup("video", formats), true, support, selected)));
    }

    private static Format variant(String id, String mimeType, int width, int height, int averageBitrate) {
        return new Format.Builder()
                .setId(id)
                .setSampleMimeType(mimeType)
                .setWidth(width)
                .setHeight(height)
                .setAverageBitrate(averageBitrate)
                .build();
    }

    private static Format video(String mimeType, int width, int height) {
        return new Format.Builder()
                .setSampleMimeType(mimeType)
                .setWidth(width)
                .setHeight(height)
                .build();
    }
}
