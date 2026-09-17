package com.github.catvod.crawler;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;

public class DebugLogStoreTest {

    @Test
    public void trimLines_keepsNewestEntries() {
        ArrayDeque<String> lines = new ArrayDeque<>();
        lines.add("one");
        lines.add("two");
        lines.add("three");
        lines.add("four");

        DebugLogStore.trimLines(lines, 2);

        assertEquals("three", lines.removeFirst());
        assertEquals("four", lines.removeFirst());
    }

    @Test
    public void readTail_discardsPartialFirstLine() throws Exception {
        File file = File.createTempFile("debug-log", ".txt");
        try {
            Files.writeString(file.toPath(), "one\ntwo\nthree\n", StandardCharsets.UTF_8);

            assertEquals("three\n", DebugLogStore.readTail(file, 8));
        } finally {
            file.delete();
        }
    }
}
