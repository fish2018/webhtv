package com.fongmi.android.tv.db.dao;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HistoryDaoTest {

    @Test
    public void findReturnsHistoryWithoutTimeOrCountLimits() throws Exception {
        String source = read(mainJavaPath().resolve(Path.of("com", "fongmi", "android", "tv", "db", "dao", "HistoryDao.java")));
        String compact = source.replaceAll("\\s+", " ");
        String lower = source.toLowerCase(Locale.ROOT);

        assertTrue(compact.contains("SELECT * FROM History WHERE cid = :cid ORDER BY createTime DESC"));
        assertTrue(compact.contains("public abstract List<History> find(int cid);"));
        assertFalse(lower.contains("createtime >= :createtime"));
        assertFalse(lower.contains("limit"));
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path mainJavaPath() {
        Path moduleRelative = Path.of("src", "main", "java");
        if (Files.exists(moduleRelative)) return moduleRelative;
        return Path.of("app", "src", "main", "java");
    }
}
