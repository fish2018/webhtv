package com.fongmi.android.tv.utils;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class BackupPermissionRegressionTest {

    @Test
    public void backupAndRestoreStopWhenFilePermissionIsDenied() throws Exception {
        assertPermissionGuard(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "SettingActivity.java"));
        assertPermissionGuard(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "SettingFragment.java"));
    }

    @Test
    public void homeDestroyBacksUpOnlyWhenEnabledAndAuthorized() throws Exception {
        assertAutoBackupGuard(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "HomeActivity.java"));
        assertAutoBackupGuard(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "activity", "HomeActivity.java"));
    }

    @Test
    public void localBackupPublishesOnlyACompletedArchive() throws Exception {
        String database = read(source("main", "java", "com", "fongmi", "android", "tv", "db", "AppDatabase.java"));
        assertTrue(database.contains("new File(Path.tv(), BACKUP_TEMP_FILE)"));
        assertTrue(database.contains("AppBackup.create(temporary, progress)"));
        assertTrue(database.contains("publishBackup(temporary, target)"));
    }

    @Test
    public void autoBackupCanBeConfiguredInPersonalSettings() throws Exception {
        String setting = read(source("main", "java", "com", "fongmi", "android", "tv", "setting", "Setting.java"));
        assertTrue(setting.contains("Prefers.getBoolean(\"auto_backup\", false)"));
        assertTrue(setting.contains("Prefers.put(\"auto_backup\", autoBackup)"));

        String leanback = read(source("leanback", "java", "com", "fongmi", "android", "tv", "ui", "activity", "SettingPersonalActivity.java"));
        assertTrue(leanback.contains("mBinding.autoBackup.setOnClickListener(this::setAutoBackup)"));
        assertTrue(leanback.contains("mBinding.autoBackupText.setText(getSwitch(isAutoBackupEnabled()))"));

        String mobile = read(source("mobile", "java", "com", "fongmi", "android", "tv", "ui", "fragment", "SettingPersonalFragment.java"));
        assertTrue(mobile.contains("mBinding.autoBackup.setOnClickListener(this::setAutoBackup)"));
        assertTrue(mobile.contains("mBinding.autoBackupText.setText(getSwitch(isAutoBackupEnabled()))"));

        assertTrue(read(source("leanback", "res", "layout", "activity_setting_personal.xml")).contains("android:id=\"@+id/autoBackup\""));
        assertTrue(read(source("mobile", "res", "layout", "fragment_setting_personal.xml")).contains("android:id=\"@+id/autoBackup\""));
    }

    @Test
    public void legacyStoragePermissionRemainsAvailableOnModernBoxes() throws Exception {
        String manifest = read(source("main", "AndroidManifest.xml"));
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.READ_EXTERNAL_STORAGE\" />"));
        assertTrue(manifest.contains("<uses-permission android:name=\"android.permission.WRITE_EXTERNAL_STORAGE\" />"));
        assertFalse(manifest.contains("android.permission.READ_EXTERNAL_STORAGE\" android:maxSdkVersion"));
        assertFalse(manifest.contains("android.permission.WRITE_EXTERNAL_STORAGE\" android:maxSdkVersion"));

        String permissionUtil = read(source("main", "java", "com", "fongmi", "android", "tv", "utils", "PermissionUtil.java"));
        assertTrue(permissionUtil.contains("permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)"));
        assertTrue(permissionUtil.contains("targetSdkVersion >= Build.VERSION_CODES.R"));

        String setting = read(source("main", "java", "com", "fongmi", "android", "tv", "setting", "Setting.java"));
        assertTrue(setting.contains("Environment.isExternalStorageManager() || hasLegacyFileAccess()"));
    }

    private static void assertPermissionGuard(Path path) throws Exception {
        String backup = method(read(path), "private void onBackup(View view)", "private void onRestore");
        assertTrue(backup.contains("if (!allGranted)"));
        assertTrue(backup.contains("R.string.backup_permission_denied"));
        assertTrue(backup.indexOf("return;") > backup.indexOf("if (!allGranted)"));

        String restore = method(read(path), "private void onRestore(View view)", "private void initConfig");
        assertTrue(restore.contains("if (!allGranted)"));
        assertTrue(restore.contains("R.string.backup_permission_denied"));
        assertTrue(restore.indexOf("return;") > restore.indexOf("if (!allGranted)"));
    }

    private static void assertAutoBackupGuard(Path path) throws Exception {
        String destroy = tail(read(path), "protected void onDestroy()");
        assertTrue(destroy.contains("AutoBackupPolicy.shouldRun("));
        assertTrue(destroy.contains("isFinishing()"));
        assertTrue(destroy.contains("isChangingConfigurations()"));
    }

    private static String method(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue("Missing method: " + start, from >= 0);
        assertTrue("Missing method boundary: " + end, to > from);
        return source.substring(from, to);
    }

    private static String tail(String source, String start) {
        int from = source.indexOf(start);
        assertTrue("Missing method: " + start, from >= 0);
        return source.substring(from);
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static Path source(String... parts) {
        Path path = Path.of("src");
        for (String part : parts) path = path.resolve(part);
        if (Files.exists(path)) return path;
        path = Path.of("app", "src");
        for (String part : parts) path = path.resolve(part);
        return path;
    }
}
