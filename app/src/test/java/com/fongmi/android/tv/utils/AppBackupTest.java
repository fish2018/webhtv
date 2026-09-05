package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class AppBackupTest {

    @Test
    public void newBackupsUseShortPrefix() {
        String name = AppBackup.fileName();
        assertTrue(name.matches("bak-\\d{8}-\\d{4}\\.zip"));
    }

    @Test
    public void recognizesCurrentAndLegacyBackupNames() {
        assertTrue(AppBackup.isBackupZipName("bak-20260718-2226.zip"));
        assertTrue(AppBackup.isBackupZipName("webhtv-backup-20260718-2226.zip"));
        assertTrue(AppBackup.isBackupManifestName("bak-20260718-2226.json"));
        assertTrue(AppBackup.isBackupManifestName("webhtv-backup-20260718-2226.json"));
        assertFalse(AppBackup.isBackupZipName("backup-20260718-2226.zip"));
    }

    @Test
    public void sortsCurrentAndLegacyNamesByTimestamp() {
        assertEquals("20260718-2226", AppBackup.backupSortKey("bak-20260718-2226.zip"));
        assertEquals("20260718-2225", AppBackup.backupSortKey("webhtv-backup-20260718-2225.zip"));
    }

    @Test
    public void partialBackupsAreMarkedAndRecognized() {
        String name = AppBackup.fileName(true);

        assertTrue(name.matches("bak-\\d{8}-\\d{4}-partial\\.zip"));
        assertTrue(AppBackup.isBackupZipName(name));
        assertTrue(AppBackup.isPartialBackupName(name));
        assertFalse(AppBackup.isPartialBackupName("bak-20260718-2226.zip"));
    }

    @Test
    public void partialSortKeyUsesTimestampAndCompleteWinsTies() {
        String complete = "bak-20260718-2226.zip";
        String partial = "bak-20260718-2226-partial.zip";

        assertEquals("20260718-2226", AppBackup.backupSortKey(partial));
        assertTrue(AppBackup.compareBackupNames(complete, partial) > 0);
        assertTrue(AppBackup.compareBackupNames("bak-20260718-2227-partial.zip", complete) > 0);
    }

    @Test
    public void createdBackupExposesFileAndWarningState() {
        File file = new File("backup.zip");
        AppBackup.CreatedBackup complete = new AppBackup.CreatedBackup(file, new AppBackup.CreateResult(""));
        AppBackup.CreatedBackup partial = new AppBackup.CreatedBackup(file, new AppBackup.CreateResult("warning"));

        assertSame(file, complete.getFile());
        assertFalse(complete.hasWarning());
        assertTrue(partial.hasWarning());
    }

}
