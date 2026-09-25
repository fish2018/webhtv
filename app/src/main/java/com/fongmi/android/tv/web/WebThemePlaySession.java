package com.fongmi.android.tv.web;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class WebThemePlaySession {

    public static final int MAX_REFERENCES = 500;
    static final int MAX_EPISODE_URL_LENGTH = 64 * 1024;
    private static final int MAX_SOURCE_KEY_LENGTH = 256;
    private static final int MAX_VOD_ID_LENGTH = 2048;
    private static final int MAX_FLAG_LENGTH = 512;
    private static final int MAX_EPISODE_NAME_LENGTH = 512;

    private final Map<String, Selection> selections = new LinkedHashMap<>();
    private String sourceKey = "";
    private String vodId = "";

    public synchronized void begin(String sourceKey, String vodId) {
        String safeSourceKey = limited(sourceKey, MAX_SOURCE_KEY_LENGTH);
        String safeVodId = limited(vodId, MAX_VOD_ID_LENGTH);
        if (this.sourceKey.equals(safeSourceKey) && this.vodId.equals(safeVodId)) return;
        selections.clear();
        this.sourceKey = safeSourceKey;
        this.vodId = safeVodId;
    }

    public synchronized String issue(String sourceKey, String vodId, String flag, String episodeName,
            String episodeUrl) {
        String safeSourceKey = limited(sourceKey, MAX_SOURCE_KEY_LENGTH);
        String safeVodId = limited(vodId, MAX_VOD_ID_LENGTH);
        String safeFlag = limited(flag, MAX_FLAG_LENGTH);
        String safeEpisodeName = limited(episodeName, MAX_EPISODE_NAME_LENGTH);
        String safeEpisodeUrl = value(episodeUrl);
        if (safeEpisodeUrl.isEmpty() || safeEpisodeUrl.length() > MAX_EPISODE_URL_LENGTH) {
            throw new IllegalArgumentException("Invalid episode URL length");
        }
        for (Map.Entry<String, Selection> entry : selections.entrySet()) {
            if (entry.getValue().same(safeSourceKey, safeVodId, safeFlag, safeEpisodeName, safeEpisodeUrl)) {
                return entry.getKey();
            }
        }
        if (selections.size() >= MAX_REFERENCES) throw new IllegalStateException("Too many play references");
        String ref = "play_" + UUID.randomUUID();
        selections.put(ref, new Selection(safeSourceKey, safeVodId, safeFlag, safeEpisodeName, safeEpisodeUrl));
        return ref;
    }

    public synchronized Selection resolve(String ref, String sourceKey, String vodId) {
        Selection selection = selections.get(ref);
        return selection != null && selection.matches(sourceKey, vodId) ? selection : null;
    }

    public synchronized void reset() {
        selections.clear();
        sourceKey = "";
        vodId = "";
    }

    public static final class Selection {
        private final String sourceKey;
        private final String vodId;
        private final String flag;
        private final String episodeName;
        private final String episodeUrl;

        private Selection(String sourceKey, String vodId, String flag, String episodeName, String episodeUrl) {
            this.sourceKey = limited(sourceKey, MAX_SOURCE_KEY_LENGTH);
            this.vodId = limited(vodId, MAX_VOD_ID_LENGTH);
            this.flag = limited(flag, MAX_FLAG_LENGTH);
            this.episodeName = limited(episodeName, MAX_EPISODE_NAME_LENGTH);
            this.episodeUrl = value(episodeUrl);
        }

        private boolean matches(String sourceKey, String vodId) {
            return this.sourceKey.equals(limited(sourceKey, MAX_SOURCE_KEY_LENGTH))
                    && this.vodId.equals(limited(vodId, MAX_VOD_ID_LENGTH));
        }

        private boolean same(String sourceKey, String vodId, String flag, String episodeName, String episodeUrl) {
            return matches(sourceKey, vodId)
                    && this.flag.equals(limited(flag, MAX_FLAG_LENGTH))
                    && this.episodeName.equals(limited(episodeName, MAX_EPISODE_NAME_LENGTH))
                    && this.episodeUrl.equals(value(episodeUrl));
        }

        public String getFlag() {
            return flag;
        }

        public String getEpisodeName() {
            return episodeName;
        }

        public String getEpisodeUrl() {
            return episodeUrl;
        }

    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String limited(String value, int maxLength) {
        String safe = value(value);
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }
}
