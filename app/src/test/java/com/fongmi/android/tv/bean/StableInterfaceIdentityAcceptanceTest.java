package com.fongmi.android.tv.bean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class StableInterfaceIdentityAcceptanceTest {

    @Test
    public void addressChangesKeepTheExistingConfigIdentity() throws Exception {
        String model = read("app/src/main/java/com/fongmi/android/tv/bean/Config.java");
        String mobile = read("app/src/mobile/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");
        String leanback = read("app/src/leanback/java/com/fongmi/android/tv/ui/dialog/ConfigDialog.java");

        assertTrue(model.contains("public Config replaceUrl(String oldUrl, String newUrl)"));
        assertTrue(model.contains("ensureInterfaceKey();"));
        assertTrue(mobile.contains("Config.find(config.getId()).url(url)"));
        assertTrue(leanback.contains("Config.find(config.getId()).url(text)"));
        assertFalse(mobile.contains("Config.delete(ori, type);\n            return Config.find(ori, type).url(url)"));
    }

    @Test
    public void failoverAndRemoteUpdatesStayInsideOneInterface() throws Exception {
        String vod = read("app/src/main/java/com/fongmi/android/tv/api/config/VodConfig.java");
        String remote = read("app/src/main/java/com/fongmi/android/tv/remote/RemoteConfigOps.java");

        assertTrue(vod.contains("List<String> addresses = config.getUrls();"));
        assertTrue(vod.contains("round.origin.url(loaded.getUrl()).update();"));
        assertTrue(remote.contains("findByInterfaceKey(interfaceKey, type)"));
        assertTrue(remote.contains("mergeUrls(urls(payload)).url(url)"));
        assertTrue(remote.contains("if (config == null) config = Config.create(type);"));
        assertFalse(remote.contains("if (config == null) config = Config.find(url, type);"));
    }

    @Test
    public void migrationAndBackupRestorePreserveStableMapping() throws Exception {
        String migration = read("app/src/main/java/com/fongmi/android/tv/db/Migrations.java");
        String backup = read("app/src/main/java/com/fongmi/android/tv/bean/Backup.java");
        String identity = read("app/src/main/java/com/fongmi/android/tv/playback/PlaybackConfigIdentity.java");

        assertTrue(migration.contains("MIGRATION_45_46"));
        assertTrue(migration.contains("CREATE UNIQUE INDEX IF NOT EXISTS `index_Config_interfaceKey_type`"));
        assertTrue(migration.contains("UPDATE Config SET interfaceKey"));
        assertTrue(backup.contains("findByInterfaceKey(item.getInterfaceKey(), item.getType())"));
        assertTrue(backup.contains("item.interfaceKey(current.getInterfaceKey()).mergeUrls(current.getUrls())"));
        assertTrue(identity.contains("normalizeKey(config.getInterfaceKey())"));
        assertTrue(identity.contains("keyForUrl(config.getUrl())"));
    }

    @Test
    public void playbackHistoryPersistsItsStableInterfaceBinding() throws Exception {
        String history = read("app/src/main/java/com/fongmi/android/tv/bean/History.java");
        String migration = read("app/src/main/java/com/fongmi/android/tv/db/Migrations.java");
        String database = read("app/src/main/java/com/fongmi/android/tv/db/AppDatabase.java");

        assertTrue(history.contains("@SerializedName(\"sourceBindingKey\")"));
        assertTrue(history.contains("@ColumnInfo(defaultValue = \"\")\n    private String sourceBindingKey;"));
        assertTrue(migration.contains("MIGRATION_46_47"));
        assertTrue(migration.contains("ALTER TABLE History ADD COLUMN `sourceBindingKey` TEXT DEFAULT ''"));
        assertTrue(database.contains("VERSION = 47"));
        assertTrue(database.contains("addMigrations(Migrations.MIGRATION_46_47)"));
    }

    private static String read(String file) throws Exception {
        Path root = Files.exists(Path.of("app")) ? Path.of("") : Path.of("..");
        return Files.readString(root.resolve(file), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
