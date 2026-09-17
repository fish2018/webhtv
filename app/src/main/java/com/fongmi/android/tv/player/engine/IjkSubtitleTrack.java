package com.fongmi.android.tv.player.engine;

import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import androidx.media3.extractor.text.Subtitle;
import androidx.media3.extractor.text.SubtitleParser;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.player.PlayerHelper;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

@UnstableApi
final class IjkSubtitleTrack {

    static final IjkSubtitleTrack EMPTY = new IjkSubtitleTrack(Collections.emptyList());

    private static final SubtitleParser.Factory PARSER_FACTORY = new DefaultSubtitleParserFactory();
    private static final int BUFFER_SIZE = 16 * 1024;

    private final List<Subtitle> subtitles;

    private IjkSubtitleTrack(List<Subtitle> subtitles) {
        this.subtitles = subtitles;
    }

    static IjkSubtitleTrack load(List<MediaItem.SubtitleConfiguration> configs, Map<String, String> headers) {
        if (configs == null || configs.isEmpty()) return EMPTY;
        for (MediaItem.SubtitleConfiguration config : configs) {
            try {
                return parse(loadBytes(config.uri, headers), config);
            } catch (Exception e) {
                SpiderDebug.log("ijk-subtitle", "load failed uri=%s mime=%s error=%s", summarizeUri(config.uri), config.mimeType, e.toString());
            }
        }
        return EMPTY;
    }

    static IjkSubtitleTrack parse(byte[] data, MediaItem.SubtitleConfiguration config) {
        return parse(data, mimeType(config), config.language, config.label, config.selectionFlags, config.roleFlags);
    }

    static IjkSubtitleTrack parse(byte[] data, @Nullable String mimeType, @Nullable String language, @Nullable String label, int selectionFlags, int roleFlags) {
        Format format = buildFormat(mimeType, language, label, selectionFlags, roleFlags);
        if (!PARSER_FACTORY.supportsFormat(format)) throw new IllegalArgumentException("Unsupported subtitle MIME type: " + mimeType);
        SubtitleParser parser = PARSER_FACTORY.create(format);
        Subtitle subtitle = parser.parseToLegacySubtitle(data, 0, data.length);
        return new IjkSubtitleTrack(Collections.singletonList(subtitle));
    }

    CueGroup getCueGroup(long positionMs) {
        if (subtitles.isEmpty()) return CueGroup.EMPTY_TIME_ZERO;
        long positionUs = Math.max(0, positionMs) * 1000L;
        List<Cue> cues = new ArrayList<>();
        for (Subtitle subtitle : subtitles) cues.addAll(subtitle.getCues(positionUs));
        return cues.isEmpty() ? CueGroup.EMPTY_TIME_ZERO : new CueGroup(cues, positionUs);
    }

    boolean isEmpty() {
        return subtitles.isEmpty();
    }

    private static Format buildFormat(@Nullable String mimeType, @Nullable String language, @Nullable String label, int selectionFlags, int roleFlags) {
        Format.Builder builder = new Format.Builder()
                .setSampleMimeType(mimeType)
                .setLanguage(language)
                .setLabel(label)
                .setSelectionFlags(selectionFlags)
                .setRoleFlags(roleFlags);
        Format format = builder.build();
        if (PARSER_FACTORY.supportsFormat(format)) builder.setCueReplacementBehavior(PARSER_FACTORY.getCueReplacementBehavior(format));
        return builder.build();
    }

    private static String mimeType(MediaItem.SubtitleConfiguration config) {
        if (config.mimeType != null && !config.mimeType.isEmpty()) return config.mimeType;
        return PlayerHelper.getSubtitleMimeType(config.uri.toString());
    }

    private static byte[] loadBytes(Uri uri, Map<String, String> headers) throws IOException {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return loadHttp(uri, headers);
        if ("file".equalsIgnoreCase(scheme) || scheme == null || scheme.isEmpty()) return loadFile(uri);
        try (InputStream input = App.get().getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("Unable to open subtitle URI: " + uri);
            return readAll(input);
        }
    }

    private static byte[] loadHttp(Uri uri, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(uri.toString());
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;
                String key = entry.getKey().trim();
                if (!key.isEmpty()) builder.header(key, entry.getValue().trim());
            }
        }
        try (Response response = OkHttp.player().newBuilder().callTimeout(Constant.TIMEOUT_PLAY, TimeUnit.MILLISECONDS).build().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IOException("HTTP " + response.code());
            ResponseBody body = response.body();
            if (body == null) throw new IOException("Empty subtitle response");
            return body.bytes();
        }
    }

    private static byte[] loadFile(Uri uri) throws IOException {
        String path = "file".equalsIgnoreCase(uri.getScheme()) ? uri.getPath() : uri.toString();
        if (path == null || path.isEmpty()) throw new IOException("Empty subtitle path");
        try (InputStream input = new FileInputStream(new File(path))) {
            return readAll(input);
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toByteArray();
    }

    private static String summarizeUri(Uri uri) {
        String text = uri.toString();
        return text.length() > 96 ? text.substring(0, 96) + "..." : text;
    }
}
