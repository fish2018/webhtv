package com.fongmi.android.tv.player.exo.ass;

import androidx.media3.common.ParserException;
import androidx.media3.extractor.ExtractorInput;
import androidx.media3.extractor.mkv.EbmlProcessor;
import androidx.media3.extractor.mkv.MatroskaExtractor;
import androidx.media3.extractor.text.DefaultSubtitleParserFactory;
import java.io.IOException;
import java.util.Locale;

/** The libass-android attachment hook, retaining WebHTV's Matroska flags and video handling. */
public final class AssFontMatroskaExtractor extends MatroskaExtractor {
    private static final int ATTACHMENTS = 0x1941A469, ATTACHED_FILE = 0x61A7;
    private static final int FILE_NAME = 0x466E, FILE_MIME = 0x4660, FILE_DATA = 0x465C;
    private final AssFontSet fonts;
    private String name, mime;
    private byte[] data;
    private boolean rejected;

    public AssFontMatroskaExtractor(int flags, boolean dolbyVisionRpu, AssFontSet fonts) {
        super(new DefaultSubtitleParserFactory(), flags, dolbyVisionRpu);
        this.fonts = fonts;
    }

    @Override protected int getElementType(int id) {
        return switch (id) {
            case ATTACHMENTS, ATTACHED_FILE -> EbmlProcessor.ELEMENT_TYPE_MASTER;
            case FILE_NAME, FILE_MIME -> EbmlProcessor.ELEMENT_TYPE_STRING;
            case FILE_DATA -> EbmlProcessor.ELEMENT_TYPE_BINARY;
            default -> super.getElementType(id);
        };
    }

    @Override protected boolean isLevel1Element(int id) {
        return super.isLevel1Element(id) || id == ATTACHMENTS;
    }

    @Override protected void startMasterElement(int id, long position, long size) throws ParserException {
        super.startMasterElement(id, position, size);
        if (id == ATTACHED_FILE) { name = mime = null; data = null; rejected = false; }
    }

    @Override protected void endMasterElement(int id) throws ParserException {
        if (id == ATTACHED_FILE) {
            if (isFont(name, mime)) {
                if (rejected) fonts.reject();
                else if (data != null) fonts.add(name, data);
            }
            name = mime = null; data = null;
        }
        super.endMasterElement(id);
    }

    @Override protected void stringElement(int id, String value) throws ParserException {
        if (id == FILE_NAME) name = value.length() <= 512 ? value : "attachment";
        else if (id == FILE_MIME) mime = value;
        else super.stringElement(id, value);
    }

    @Override protected void binaryElement(int id, int size, ExtractorInput input) throws IOException {
        if (id != FILE_DATA) { super.binaryElement(id, size, input); return; }
        // Matroska fields may arrive in any order; decide on the completed AttachedFile.
        if ((name != null && mime != null && !isFont(name, mime)) || !fonts.canRead(size)) {
            rejected = true;
            input.skipFully(size);
        } else {
            data = new byte[size];
            input.readFully(data, 0, size);
        }
    }

    static boolean isFont(String name, String mime) {
        String type = mime == null ? "" : mime.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        if (switch (type) {
            case "font/ttf", "font/otf", "font/sfnt", "font/collection", "font/woff", "font/woff2",
                    "application/font-sfnt", "application/font-woff", "application/x-truetype-font",
                    "application/vnd.ms-opentype", "application/x-font-ttf", "application/x-font-otf" -> true;
            default -> false;
        }) return true;
        String file = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return file.endsWith(".ttf") || file.endsWith(".otf") || file.endsWith(".ttc")
                || file.endsWith(".otc") || file.endsWith(".woff") || file.endsWith(".woff2");
    }
}
