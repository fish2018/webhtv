package com.fongmi.android.tv.subtitle.translate;

import com.fongmi.android.tv.bean.AiConfig;
import com.fongmi.android.tv.subtitle.model.SubtitleAsset;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.Objects;

public final class SubtitleTranslationCache {

    static final int PARSER_VERSION = 1;
    static final int WRITER_VERSION = 1;
    static final int PROMPT_VERSION = 2;

    public String key(SubtitleTranslationRequest request, AiConfig config) {
        return key(request, config, "");
    }

    public String key(SubtitleTranslationRequest request, AiConfig config, String variant) {
        return buildKey(request, config, variant, false, false);
    }

    public String sourceLanguageKey(SubtitleTranslationRequest request, AiConfig config, String variant) {
        return buildKey(request, config, variant, true, false);
    }

    public String legacyMetadataKey(SubtitleTranslationRequest request, AiConfig config, String variant) {
        return buildKey(request, config, variant, true, true);
    }

    private String buildKey(SubtitleTranslationRequest request, AiConfig config, String variant, boolean includeSourceLanguage, boolean includeFileMetadata) {
        if (request == null || request.getSourceAsset() == null) return "";
        AiConfig safe = config == null ? new AiConfig().sanitize() : config.sanitize();
        SubtitleAsset asset = request.getSourceAsset();
        File source = sourceFile(asset);
        String sourceHash = sourceHash(source, asset);
        StringBuilder value = new StringBuilder("v1|").append(sourceHash).append('|');
        if (includeFileMetadata) value.append(sourceSize(source)).append('|').append(sourceModified(source)).append('|');
        if (includeSourceLanguage) value.append(Objects.toString(request.getSourceLanguage(), "")).append('|');
        value.append(Objects.toString(request.getTargetLanguage(), "")).append('|')
                .append(Objects.toString(request.getMode(), "")).append('|')
                .append(PARSER_VERSION).append('|')
                .append(WRITER_VERSION).append('|')
                .append(PROMPT_VERSION).append('|')
                .append(Objects.toString(safe.getProtocol(), "")).append('|')
                .append(sha256(Objects.toString(safe.getEndpoint(), ""))).append('|')
                .append(Objects.toString(safe.getModel(), "")).append('|')
                .append(Objects.toString(variant, ""));
        return sha256(value.toString());
    }

    private static File sourceFile(SubtitleAsset asset) {
        String path = asset.getLocalPath();
        if (path == null || path.trim().isEmpty()) return null;
        return new File(path);
    }

    private static String sourceHash(File file, SubtitleAsset asset) {
        if (file != null && file.isFile()) {
            try {
                return sha256(Files.readAllBytes(file.toPath()));
            } catch (Throwable ignored) {
            }
        }
        String fallback = Objects.toString(asset.getUri(), "") + "|"
                + Objects.toString(asset.getLocalPath(), "") + "|"
                + Objects.toString(asset.getDisplayName(), "") + "|"
                + Objects.toString(asset.getLanguage(), "") + "|"
                + Objects.toString(asset.getMimeType(), "");
        return sha256(fallback);
    }

    private static long sourceSize(File file) {
        return file != null && file.isFile() ? file.length() : 0L;
    }

    private static long sourceModified(File file) {
        return file != null && file.isFile() ? file.lastModified() : 0L;
    }

    private static String sha256(String text) {
        return sha256(Objects.toString(text, "").getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(bytes == null ? new byte[0] : bytes);
            StringBuilder builder = new StringBuilder();
            for (byte value : hashed) builder.append(String.format(Locale.US, "%02x", value));
            return builder.toString();
        } catch (Throwable e) {
            return Integer.toHexString(java.util.Arrays.hashCode(bytes));
        }
    }
}
