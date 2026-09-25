package com.fongmi.android.tv.bean;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class ClassParcelableSourceTest {

    @Test
    public void classParcelPreservesCategoryFilters() throws Exception {
        String source = read(findAppPath().resolve(Path.of("src", "main", "java", "com", "fongmi", "android", "tv", "bean", "Class.java")));

        assertTrue("parcel restore must include category filters",
                source.contains("this.filters = in.createTypedArrayList(Filter.CREATOR);"));
        assertTrue("parcel save must include category filters",
                source.contains("dest.writeTypedList(this.filters);"));
    }

    private static Path findAppPath() {
        return Files.exists(Path.of("src", "main")) ? Path.of(".") : Path.of("app");
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
