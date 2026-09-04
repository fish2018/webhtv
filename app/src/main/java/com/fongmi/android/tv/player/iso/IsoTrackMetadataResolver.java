package com.fongmi.android.tv.player.iso;

import androidx.media3.common.CacheDataReader;
import androidx.media3.extractor.iso.IsoFileEntry;
import androidx.media3.extractor.iso.bdmv.ClpiInfo;
import androidx.media3.extractor.iso.bdmv.ClpiParser;
import androidx.media3.extractor.iso.bdmv.MplsParser;
import androidx.media3.extractor.iso.bdmv.MplsStreamEntry;
import androidx.media3.extractor.iso.bdmv.PlayItem;
import androidx.media3.extractor.iso.bdmv.Playlist;
import androidx.media3.extractor.iso.bdmv.StreamInfo;
import androidx.media3.extractor.iso.udf.UdfFileSystem;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class IsoTrackMetadataResolver {

    static final int TRACK_TYPE_AUDIO = 1;
    static final int TRACK_TYPE_SUBTITLE = 2;

    private static final int MAX_METADATA_FILE_BYTES = 8 * 1024 * 1024;
    private static final int STREAM_GROUP_PRIMARY_AUDIO = 2;
    private static final int STREAM_GROUP_PRESENTATION_GRAPHICS = 3;
    private static final int STREAM_GROUP_SECONDARY_AUDIO = 5;

    private IsoTrackMetadataResolver() {
    }

    static Snapshot resolve(RemoteIsoSource source, int playlistNumber) throws IOException {
        CacheDataReader reader = new SourceReader(source);
        UdfFileSystem udf = new UdfFileSystem();
        udf.open(reader);
        String playlistName = String.format(Locale.US, "%05d", playlistNumber);
        IsoFileEntry mplsEntry = udf.findFile("BDMV/PLAYLIST/" + playlistName + ".mpls");
        if (mplsEntry == null) throw new IOException("Blu-ray playlist not found: " + playlistName);
        Playlist playlist = MplsParser.parse(readEntry(reader, mplsEntry), playlistName);

        List<MutableTrack> playlistTracks = collectPlaylistTracks(playlist);
        List<StreamInfo> firstClipStreams = new ArrayList<>();
        Map<String, Boolean> parsedClips = new HashMap<>();
        for (PlayItem item : playlist.playItems) {
            if (parsedClips.put(item.clipName, Boolean.TRUE) != null) continue;
            IsoFileEntry clpiEntry = udf.findFile("BDMV/CLIPINF/" + item.clipName + ".clpi");
            if (clpiEntry == null) continue;
            try {
                ClpiInfo clpi = ClpiParser.parse(readEntry(reader, clpiEntry), item.clipName);
                firstClipStreams.addAll(clpi.streams);
                if (!firstClipStreams.isEmpty()) break;
            } catch (IOException ignored) {
            }
        }

        List<Track> playlistSnapshot = new ArrayList<>(playlistTracks.size());
        for (MutableTrack track : playlistTracks) {
            playlistSnapshot.add(new Track(track.type, track.pid, track.streamGroup, track.codingType, track.language));
        }
        return snapshotFromClpi(firstClipStreams, playlistSnapshot);
    }

    static Snapshot snapshotFromClpi(List<StreamInfo> streams, List<Track> playlistTracks) {
        if (streams.isEmpty()) return new Snapshot(playlistTracks);
        Map<Long, Track> playlistByPid = new HashMap<>();
        for (Track track : playlistTracks) {
            if (track.pid > 0) playlistByPid.putIfAbsent(trackKey(track.type, track.pid), track);
        }
        List<Track> clpiTracks = new ArrayList<>();
        for (StreamInfo stream : streams) {
            int type = trackTypeForCoding(stream.streamType);
            if (type == 0) continue;
            long key = trackKey(type, stream.pid);
            Track playlistTrack = playlistByPid.get(key);
            String language = !isEmpty(stream.languageCode) ? stream.languageCode : playlistTrack == null ? null : playlistTrack.language;
            int streamGroup = playlistTrack == null ? 0 : playlistTrack.streamGroup;
            clpiTracks.add(new Track(type, stream.pid, streamGroup, stream.streamType, language));
        }
        return clpiTracks.isEmpty() ? new Snapshot(playlistTracks) : new Snapshot(clpiTracks);
    }

    private static List<MutableTrack> collectPlaylistTracks(Playlist playlist) {
        List<MutableTrack> result = new ArrayList<>();
        Map<Long, MutableTrack> byPid = new LinkedHashMap<>();
        for (PlayItem item : playlist.playItems) {
            for (MplsStreamEntry stream : item.stnStreams) {
                int type = trackTypeForGroup(stream.streamType);
                if (type == 0) continue;
                if (stream.pid <= 0) {
                    result.add(new MutableTrack(type, stream.pid, stream.streamType, stream.codingType, stream.languageCode));
                    continue;
                }
                long key = (((long) type) << 32) | (stream.pid & 0xffffffffL);
                MutableTrack existing = byPid.get(key);
                if (existing == null) {
                    existing = new MutableTrack(type, stream.pid, stream.streamType, stream.codingType, stream.languageCode);
                    byPid.put(key, existing);
                    result.add(existing);
                } else if (isEmpty(existing.language) && !isEmpty(stream.languageCode)) {
                    existing.language = stream.languageCode;
                }
            }
        }
        return result;
    }

    private static int trackTypeForGroup(int group) {
        if (group == STREAM_GROUP_PRIMARY_AUDIO || group == STREAM_GROUP_SECONDARY_AUDIO) return TRACK_TYPE_AUDIO;
        if (group == STREAM_GROUP_PRESENTATION_GRAPHICS) return TRACK_TYPE_SUBTITLE;
        return 0;
    }

    private static int trackTypeForCoding(int codingType) {
        if (codingType == 0x03 || codingType == 0x04 || (codingType >= 0x80 && codingType <= 0x86) || codingType == 0xa1 || codingType == 0xa2) {
            return TRACK_TYPE_AUDIO;
        }
        if (codingType == 0x90 || codingType == 0x91 || codingType == 0x92 || codingType == 0xa0) {
            return TRACK_TYPE_SUBTITLE;
        }
        return 0;
    }

    static byte[] readEntry(CacheDataReader reader, IsoFileEntry entry) throws IOException {
        if (entry.length < 0 || entry.length > MAX_METADATA_FILE_BYTES) {
            throw new IOException("Blu-ray metadata file is too large: " + entry.name + " size=" + entry.length);
        }
        byte[] data = new byte[(int) entry.length];
        int position = 0;
        for (int i = 0; i < entry.extentOffsets.length && position < data.length; i++) {
            int extentLength = (int) Math.min(entry.extentLengths[i], data.length - position);
            if (entry.extentOffsets[i] == IsoFileEntry.UNRECORDED_EXTENT_OFFSET) {
                Arrays.fill(data, position, position + extentLength, (byte) 0);
                position += extentLength;
                continue;
            }
            int extentRead = 0;
            while (extentRead < extentLength) {
                int count = reader.read(entry.extentOffsets[i] + extentRead, data, position, extentLength - extentRead);
                if (count <= 0) throw new IOException("Unexpected EOF reading " + entry.name);
                position += count;
                extentRead += count;
            }
        }
        if (position != data.length) throw new IOException("Unexpected EOF reading " + entry.name);
        return data;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

    private static long trackKey(int type, int pid) {
        return (((long) type) << 32) | (pid & 0xffffffffL);
    }

    static final class Snapshot {

        static final Snapshot EMPTY = new Snapshot(List.of());

        private final List<Track> audioTracks;
        private final List<Track> subtitleTracks;
        private final Map<Long, Track> tracksByPid;

        Snapshot(List<Track> tracks) {
            audioTracks = new ArrayList<>();
            subtitleTracks = new ArrayList<>();
            tracksByPid = new HashMap<>();
            for (Track track : tracks) {
                List<Track> typed = track.type == TRACK_TYPE_AUDIO ? audioTracks : track.type == TRACK_TYPE_SUBTITLE ? subtitleTracks : null;
                if (typed == null) continue;
                typed.add(track);
                if (track.pid > 0) tracksByPid.putIfAbsent(pidKey(track.type, track.pid), track);
            }
        }

        String language(int type, int demuxId, int ordinal) {
            if (demuxId > 0) {
                Track byPid = tracksByPid.get(pidKey(type, demuxId));
                if (byPid != null && !isEmpty(byPid.language)) return byPid.language;
            }
            List<Track> typed = type == TRACK_TYPE_AUDIO ? audioTracks : type == TRACK_TYPE_SUBTITLE ? subtitleTracks : List.of();
            if (ordinal < 0 || ordinal >= typed.size()) return null;
            String language = typed.get(ordinal).language;
            return isEmpty(language) ? null : language;
        }

        int audioCount() {
            return audioTracks.size();
        }

        int subtitleCount() {
            return subtitleTracks.size();
        }

        private static long pidKey(int type, int pid) {
            return trackKey(type, pid);
        }
    }

    static final class Track {
        final int type;
        final int pid;
        final int streamGroup;
        final int codingType;
        final String language;

        Track(int type, int pid, int streamGroup, int codingType, String language) {
            this.type = type;
            this.pid = pid;
            this.streamGroup = streamGroup;
            this.codingType = codingType;
            this.language = language;
        }
    }

    private static final class MutableTrack {
        final int type;
        final int pid;
        final int streamGroup;
        final int codingType;
        String language;

        MutableTrack(int type, int pid, int streamGroup, int codingType, String language) {
            this.type = type;
            this.pid = pid;
            this.streamGroup = streamGroup;
            this.codingType = codingType;
            this.language = language;
        }
    }

    private static final class SourceReader implements CacheDataReader {
        private final RemoteIsoSource source;

        SourceReader(RemoteIsoSource source) {
            this.source = source;
        }

        @Override
        public int read(long byteOffset, byte[] buf, int offset, int length) throws IOException {
            return source.readAt(byteOffset, buf, offset, length);
        }

        @Override
        public long length() throws IOException {
            return source.length();
        }
    }
}
