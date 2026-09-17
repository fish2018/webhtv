package com.fongmi.android.tv.db;

import com.fongmi.android.tv.utils.AppBackup;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AppDatabaseBackupTest {

    @Test
    public void publishesCompletedBackupByReplacingTheTarget() throws Exception {
        Path directory = Files.createTempDirectory("webhtv-backup-");
        File temporary = directory.resolve(".backup.tmp").toFile();
        File target = directory.resolve("bak-20260720-1200.zip").toFile();
        try {
            Files.writeString(temporary.toPath(), "new", StandardCharsets.UTF_8);
            Files.writeString(target.toPath(), "old", StandardCharsets.UTF_8);

            AppDatabase.publishBackup(temporary, target);

            assertEquals("new", Files.readString(target.toPath(), StandardCharsets.UTF_8));
            assertFalse(temporary.exists());
            assertFalse(AppBackup.isBackup(directory.resolve(".backup.tmp").toFile()));
        } finally {
            Files.deleteIfExists(temporary.toPath());
            Files.deleteIfExists(target.toPath());
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void cleanOldKeepsCompleteAndPartialBackupsSeparately() throws Exception {
        Path directory = Files.createTempDirectory("webhtv-backup-retention-");
        File oldestPartial = directory.resolve("bak-20260720-1300-partial.zip").toFile();
        File newestPartial = directory.resolve("bak-20260720-1301-partial.zip").toFile();
        try {
            for (int i = 0; i < 8; i++) {
                File file = directory.resolve(String.format("bak-20260720-12%02d.zip", i)).toFile();
                Files.writeString(file.toPath(), "complete", StandardCharsets.UTF_8);
                assertTrue(file.setLastModified(i + 1L));
            }
            Files.writeString(oldestPartial.toPath(), "partial", StandardCharsets.UTF_8);
            Files.writeString(newestPartial.toPath(), "partial", StandardCharsets.UTF_8);
            assertTrue(oldestPartial.setLastModified(100L));
            assertTrue(newestPartial.setLastModified(101L));

            AppDatabase.cleanOld(directory.toFile());

            int complete = 0;
            int partial = 0;
            File[] files = directory.toFile().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (!AppBackup.isBackup(file)) continue;
                    if (AppBackup.isPartialBackupName(file.getName())) partial++;
                    else complete++;
                }
            }
            assertEquals(7, complete);
            assertEquals(1, partial);
            assertFalse(oldestPartial.exists());
            assertTrue(newestPartial.exists());
        } finally {
            File[] files = directory.toFile().listFiles();
            if (files != null) for (File file : files) Files.deleteIfExists(file.toPath());
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void autoBackupGateCoalescesPendingRequests() {
        assertTrue(AppDatabase.beginAutoBackup());
        try {
            assertFalse(AppDatabase.beginAutoBackup());
        } finally {
            AppDatabase.finishAutoBackup();
        }
        assertTrue(AppDatabase.beginAutoBackup());
        AppDatabase.finishAutoBackup();
    }
}
