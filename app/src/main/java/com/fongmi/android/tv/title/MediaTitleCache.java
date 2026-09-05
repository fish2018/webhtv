package com.fongmi.android.tv.title;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.AiConfig;
import com.github.catvod.utils.Path;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public final class MediaTitleCache {

    private static final int PROMPT_VERSION = AiConfig.DEFAULT_TITLE_EXTRACTION_PROMPT_VERSION;
    private static final long TTL_MS = TimeUnit.DAYS.toMillis(30);

    public MediaTitleResolution read(MediaTitleRequest request, AiConfig config) {
        File file = file(request, config);
        if (file == null || !file.exists() || System.currentTimeMillis() - file.lastModified() > TTL_MS) return null;
        try {
            Entry entry = App.gson().fromJson(Files.readString(file.toPath()), Entry.class);
            return entry == null ? null : entry.resolution;
        } catch (Throwable e) {
            return null;
        }
    }

    public void write(MediaTitleRequest request, AiConfig config, MediaTitleResolution resolution) {
        if (resolution == null || resolution.getCanonicalTitle().isEmpty()) return;
        File file = file(request, config);
        if (file == null) return;
        Entry entry = new Entry();
        entry.createdAt = System.currentTimeMillis();
        entry.resolution = resolution;
        Path.write(file, App.gson().toJson(entry).getBytes(StandardCharsets.UTF_8));
    }

    private File file(MediaTitleRequest request, AiConfig config) {
        String key = key(request, config);
        if (key.isEmpty()) return null;
        File dir = new File(Path.cache(), "ai_title");
        if (!dir.exists() && !dir.mkdirs()) return null;
        return new File(dir, key + ".json");
    }

    String key(MediaTitleRequest request, AiConfig config) {
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        String rawTitle = request == null ? "" : request.getRawTitle();
        if (rawTitle.trim().isEmpty()) return "";
        String value = "v1|"
                + Objects.toString(request.getSiteKey(), "") + "|"
                + Objects.toString(request.getVodId(), "") + "|"
                + Objects.toString(rawTitle, "") + "|"
                + Objects.toString(request.getSearchKeyword(), "") + "|"
                + Objects.toString(request.getEpisodeName(), "") + "|"
                + Objects.toString(safe.getProtocol(), "") + "|"
                + Objects.toString(safe.getEndpoint(), "") + "|"
                + Objects.toString(safe.getModel(), "") + "|"
                + Objects.toString(safe.getTitleExtractionPrompt(), "") + "|"
                + PROMPT_VERSION;
        return md5(value);
    }

    private static String md5(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] bytes = digest.digest(Objects.toString(text, "").getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : bytes) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        } catch (Throwable e) {
            return Integer.toHexString(Objects.toString(text, "").hashCode());
        }
    }

    private static final class Entry {

        private long createdAt;
        private MediaTitleResolution resolution;
    }
}
