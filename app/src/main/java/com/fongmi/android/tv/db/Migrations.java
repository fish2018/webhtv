package com.fongmi.android.tv.db;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.fongmi.android.tv.playback.PlaybackConfigIdentity;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class Migrations {

    public static final Migration MIGRATION_30_31 = new Migration(30, 31) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL, `adaptive` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_31_32 = new Migration(31, 32) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE History_Backup (`key` TEXT NOT NULL, `vodPic` TEXT, `vodName` TEXT, `vodFlag` TEXT, `vodRemarks` TEXT, `episodeUrl` TEXT, `revSort` INTEGER NOT NULL, `revPlay` INTEGER NOT NULL, `createTime` INTEGER NOT NULL, `opening` INTEGER NOT NULL, `ending` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `speed` REAL NOT NULL, `scale` INTEGER NOT NULL, `cid` INTEGER NOT NULL, PRIMARY KEY(`key`))");
            database.execSQL("INSERT INTO History_Backup SELECT `key`, `vodPic`, `vodName`, `vodFlag`, `vodRemarks`, `episodeUrl`, `revSort`, `revPlay`, `createTime`, `opening`, `ending`, `position`, `duration`, `speed`, `scale`, `cid` FROM History");
            database.execSQL("DROP TABLE History");
            database.execSQL("ALTER TABLE History_Backup RENAME to History");
        }
    };

    public static final Migration MIGRATION_32_33 = new Migration(32, 33) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE Live ADD COLUMN keep TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_33_34 = new Migration(33, 34) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, `track` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `selected` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_34_35 = new Migration(34, 35) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("DROP TABLE Track");
            database.execSQL("CREATE TABLE Track (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `type` INTEGER NOT NULL, `key` TEXT, `name` TEXT, `format` TEXT, `selected` INTEGER NOT NULL)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Track_key_type` ON `Track` (`key`, `type`)");
        }
    };

    public static final Migration MIGRATION_35_36 = new Migration(35, 36) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN wallPic TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_36_37 = new Migration(36, 37) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN typeName TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN area TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN actor TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN director TEXT DEFAULT NULL");
            database.execSQL("ALTER TABLE History ADD COLUMN year TEXT DEFAULT NULL");
        }
    };

    public static final Migration MIGRATION_37_38 = new Migration(37, 38) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN speedOverride INTEGER NOT NULL DEFAULT 0");
            database.execSQL("UPDATE History SET speedOverride = 1 WHERE speed > 0 AND ABS(speed - 1.0) > 0.001");
        }
    };

    public static final Migration MIGRATION_38_39 = new Migration(38, 39) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN tmdbId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE History ADD COLUMN mediaType TEXT DEFAULT ''");
            database.execSQL("ALTER TABLE History ADD COLUMN legacyKey TEXT DEFAULT ''");
            database.execSQL("UPDATE History SET legacyKey = `key`");
        }
    };

    public static final Migration MIGRATION_39_40 = new Migration(39, 40) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE History ADD COLUMN tmdbSeasonNumber INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE History ADD COLUMN tmdbEpisodeNumber INTEGER NOT NULL DEFAULT 0");
        }
    };

    public static final Migration MIGRATION_40_41 = new Migration(40, 41) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS PlaybackDeleteTombstone (`id` TEXT NOT NULL, `configKey` TEXT NOT NULL, `scope` TEXT NOT NULL, `historyKey` TEXT NOT NULL, `siteKey` TEXT NOT NULL, `vodId` TEXT NOT NULL, `deletedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_PlaybackDeleteTombstone_deletedAt` ON `PlaybackDeleteTombstone` (`deletedAt`)");
        }
    };

    public static final Migration MIGRATION_41_42 = new Migration(41, 42) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE PlaybackDeleteTombstone ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT ''");
            database.execSQL("ALTER TABLE PlaybackDeleteTombstone ADD COLUMN `tmdbId` INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE PlaybackDeleteTombstone ADD COLUMN `seasonNumber` INTEGER NOT NULL DEFAULT -1");
            database.execSQL("CREATE TABLE IF NOT EXISTS TmdbSeasonProgress (`cid` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `sourceFlag` TEXT NOT NULL, `sourceEpisodeName` TEXT NOT NULL, `sourceEpisodeUrl` TEXT NOT NULL, `sourceHistoryKey` TEXT NOT NULL, `sourceBindingKey` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`cid`, `mediaType`, `tmdbId`, `seasonNumber`))");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_TmdbSeasonProgress_cid_mediaType_tmdbId_sourceHistoryKey_updatedAt` ON `TmdbSeasonProgress` (`cid`, `mediaType`, `tmdbId`, `sourceHistoryKey`, `updatedAt`)");
        }
    };

    public static final Migration MIGRATION_42_43 = new Migration(42, 43) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "PlaybackDeleteTombstone", "mediaType",
                    "ALTER TABLE PlaybackDeleteTombstone ADD COLUMN `mediaType` TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(database, "PlaybackDeleteTombstone", "tmdbId",
                    "ALTER TABLE PlaybackDeleteTombstone ADD COLUMN `tmdbId` INTEGER NOT NULL DEFAULT 0");
            addColumnIfMissing(database, "PlaybackDeleteTombstone", "seasonNumber",
                    "ALTER TABLE PlaybackDeleteTombstone ADD COLUMN `seasonNumber` INTEGER NOT NULL DEFAULT -1");
            database.execSQL("CREATE TABLE IF NOT EXISTS TmdbSeasonProgress (`cid` INTEGER NOT NULL, `mediaType` TEXT NOT NULL, `tmdbId` INTEGER NOT NULL, `seasonNumber` INTEGER NOT NULL, `episodeNumber` INTEGER NOT NULL, `position` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `sourceFlag` TEXT NOT NULL, `sourceEpisodeName` TEXT NOT NULL, `sourceEpisodeUrl` TEXT NOT NULL, `sourceHistoryKey` TEXT NOT NULL, `sourceBindingKey` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`cid`, `mediaType`, `tmdbId`, `seasonNumber`))");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_TmdbSeasonProgress_cid_mediaType_tmdbId_sourceHistoryKey_updatedAt` ON `TmdbSeasonProgress` (`cid`, `mediaType`, `tmdbId`, `sourceHistoryKey`, `updatedAt`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_TmdbSeasonProgress_cid_sourceHistoryKey` ON `TmdbSeasonProgress` (`cid`, `sourceHistoryKey`)");
        }
    };

    /**
     * 播放内核回到「按剧集记住」：History 新增 player 列。
     * -1 表示这条记录没有自己的内核偏好，播放时沿用设置页的全局默认。
     */
    public static final Migration MIGRATION_43_44 = new Migration(43, 44) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "History", "player",
                    "ALTER TABLE History ADD COLUMN `player` INTEGER NOT NULL DEFAULT -1");
        }
    };

    /**
     * 外挂字幕随历史恢复：History 新增 subtitleSource 列。
     * 空串表示这条记录没有外部字幕偏好，起播时不注入。
     *
     * <p>列声明必须与 Room 导出的 45.json 一致——实体里是普通 String，
     * 所以是可空列。写成 NOT NULL 会让迁移后的 validateMigration 失败。
     */
    public static final Migration MIGRATION_44_45 = new Migration(44, 45) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "History", "subtitleSource",
                    "ALTER TABLE History ADD COLUMN `subtitleSource` TEXT DEFAULT ''");
        }
    };

    public static final Migration MIGRATION_45_46 = new Migration(45, 46) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "Config", "interfaceKey",
                    "ALTER TABLE Config ADD COLUMN `interfaceKey` TEXT");
            addColumnIfMissing(database, "Config", "urlsJson",
                    "ALTER TABLE Config ADD COLUMN `urlsJson` TEXT");
            database.execSQL("DROP INDEX IF EXISTS `index_Config_url_type`");
            database.execSQL("UPDATE Config SET interfaceKey = lower(hex(randomblob(4))) || '-' || lower(hex(randomblob(2))) || '-4' || substr(lower(hex(randomblob(2))), 2) || '-' || substr('89ab', abs(random()) % 4 + 1, 1) || substr(lower(hex(randomblob(2))), 2) || '-' || lower(hex(randomblob(6))) WHERE interfaceKey IS NULL OR trim(interfaceKey) = ''");
            database.execSQL("UPDATE Config SET urlsJson = '[\"' || replace(replace(url, '\\\\', '\\\\\\\\'), '\"', '\\\"') || '\"]' WHERE (urlsJson IS NULL OR trim(urlsJson) = '') AND url IS NOT NULL AND trim(url) != ''");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_Config_interfaceKey_type` ON `Config` (`interfaceKey`, `type`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_Config_url_type` ON `Config` (`url`, `type`)");
        }
    };

    public static final Migration MIGRATION_46_47 = new Migration(46, 47) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "History", "sourceBindingKey",
                    "ALTER TABLE History ADD COLUMN `sourceBindingKey` TEXT DEFAULT ''");
        }
    };

    /** Initializes compatibility clues without changing the stable interfaceKey. */
    public static final Migration MIGRATION_47_48 = new Migration(47, 48) {
        private final Gson gson = new Gson();
        private final Type listType = TypeToken.getParameterized(List.class, String.class).getType();

        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            addColumnIfMissing(database, "Config", "legacyConfigKeysJson",
                    "ALTER TABLE Config ADD COLUMN `legacyConfigKeysJson` TEXT DEFAULT '[]'");
            addColumnIfMissing(database, "Config", "addressMatchAliasesJson",
                    "ALTER TABLE Config ADD COLUMN `addressMatchAliasesJson` TEXT DEFAULT '[]'");
            addColumnIfMissing(database, "Config", "identityResolutionState",
                    "ALTER TABLE Config ADD COLUMN `identityResolutionState` TEXT DEFAULT 'unresolved'");
            try (Cursor cursor = database.query("SELECT id, type, url, urlsJson FROM Config")) {
                int id = cursor.getColumnIndex("id");
                int type = cursor.getColumnIndex("type");
                int url = cursor.getColumnIndex("url");
                int urlsJson = cursor.getColumnIndex("urlsJson");
                while (cursor.moveToNext()) {
                    List<String> urls = urls(cursor, url >= 0 ? cursor.getString(url) : "", urlsJson >= 0 ? cursor.getString(urlsJson) : "");
                    Set<String> legacy = new LinkedHashSet<>();
                    for (String item : urls) {
                        String key = PlaybackConfigIdentity.keyForUrl(item);
                        if (!key.isEmpty()) legacy.add(key);
                    }
                    List<String> aliases = new ArrayList<>();
                    aliases.addAll(PlaybackConfigIdentity.strictAddressKeys(type >= 0 ? cursor.getInt(type) : 0, urls));
                    aliases.addAll(PlaybackConfigIdentity.endpointMatchKeys(type >= 0 ? cursor.getInt(type) : 0, urls));
                    aliases.addAll(PlaybackConfigIdentity.hostMatchKeys(type >= 0 ? cursor.getInt(type) : 0, urls));
                    ContentValues values = new ContentValues();
                    values.put("legacyConfigKeysJson", gson.toJson(new ArrayList<>(legacy)));
                    values.put("addressMatchAliasesJson", gson.toJson(aliases));
                    values.put("identityResolutionState", "unresolved");
                    if (id >= 0) database.update("Config", 0, values, "id = ?", new String[]{String.valueOf(cursor.getInt(id))});
                }
            }
        }

        private List<String> urls(Cursor cursor, String primary, String json) {
            LinkedHashSet<String> result = new LinkedHashSet<>();
            if (primary != null && !primary.trim().isEmpty()) result.add(primary.trim());
            try {
                List<String> values = gson.fromJson(json, listType);
                if (values != null) for (String value : values) if (value != null && !value.trim().isEmpty()) result.add(value.trim());
            } catch (Exception ignored) {
                // A malformed legacy urlsJson must not abort the database upgrade.
            }
            return new ArrayList<>(result);
        }
    };

    private static void addColumnIfMissing(
            SupportSQLiteDatabase database,
            String table,
            String column,
            String statement) {
        try (Cursor cursor = database.query("PRAGMA table_info(`" + table + "`)")) {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (nameIndex >= 0 && column.equals(cursor.getString(nameIndex))) return;
            }
        }
        database.execSQL(statement);
    }
}
