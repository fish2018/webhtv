package com.fongmi.android.tv.db;

import android.database.Cursor;

import androidx.annotation.NonNull;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
