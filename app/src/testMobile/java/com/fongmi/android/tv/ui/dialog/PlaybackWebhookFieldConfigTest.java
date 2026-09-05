package com.fongmi.android.tv.ui.dialog;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;

public class PlaybackWebhookFieldConfigTest {

    private static final int FIELD_COUNT = 23;

    @Test
    public void webhookFieldCollectionsExposeSpeedOverride() throws Exception {
        String source = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/PlaybackWebhookDialog.java");

        assertEquals(6, occurrences(source, "\"speed\", \"speedOverride\", \"completed\""));
    }

    @Test
    public void webhookFieldDescriptionsMatchSelectableFields() throws Exception {
        assertEquals(FIELD_COUNT, descriptionCount("app/src/main/res/values/strings.xml"));
        assertEquals(FIELD_COUNT, descriptionCount("app/src/main/res/values-zh-rCN/strings.xml"));
        assertEquals(FIELD_COUNT, descriptionCount("app/src/main/res/values-zh-rTW/strings.xml"));
    }

    private static int descriptionCount(String file) throws Exception {
        String source = read(file);
        String startTag = "<string-array name=\"playback_webhook_field_descriptions\">";
        int start = source.indexOf(startTag);
        int end = source.indexOf("</string-array>", start);
        return occurrences(source.substring(start, end), "<item>");
    }

    private static int occurrences(String source, String value) {
        int count = 0;
        for (int index = 0; (index = source.indexOf(value, index)) >= 0; index += value.length()) count++;
        return count;
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
