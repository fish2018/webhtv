package com.fongmi.android.tv.api.loader;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ChaquopyRequirementsTest {

    @Test
    public void requestsCertificateDependencyIsPackagedExplicitly() throws Exception {
        Path requirements = projectRoot().resolve("chaquo/requirements.txt");
        List<String> packages = Files.readAllLines(requirements, StandardCharsets.UTF_8).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .map(line -> line.split("[<>=!~]", 2)[0].toLowerCase())
                .toList();

        assertTrue("requests must be declared", packages.contains("requests"));
        assertTrue("certifi must be explicit so Chaquopy packages requests.certs",
                packages.contains("certifi"));
    }

    private static Path projectRoot() {
        return Files.exists(Path.of("chaquo")) ? Path.of("") : Path.of("..");
    }
}
