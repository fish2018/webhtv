package com.fongmi.android.tv.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.Keep;
import com.fongmi.android.tv.bean.Live;
import com.fongmi.android.tv.bean.PlaybackDeleteTombstone;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.bean.TmdbSeasonProgress;
import com.fongmi.android.tv.db.dao.ConfigDao;
import com.fongmi.android.tv.db.dao.DeviceDao;
import com.fongmi.android.tv.db.dao.HistoryDao;
import com.fongmi.android.tv.db.dao.KeepDao;
import com.fongmi.android.tv.db.dao.LiveDao;
import com.fongmi.android.tv.db.dao.PlaybackDeleteTombstoneDao;
import com.fongmi.android.tv.db.dao.SiteDao;
import com.fongmi.android.tv.db.dao.TrackDao;
import com.fongmi.android.tv.db.dao.TmdbSeasonProgressDao;
import com.fongmi.android.tv.utils.AppBackup;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Database(entities = {Keep.class, Site.class, Live.class, Track.class, Config.class, Device.class, History.class, PlaybackDeleteTombstone.class, TmdbSeasonProgress.class}, version = AppDatabase.VERSION)
public abstract class AppDatabase extends RoomDatabase {

    public static final int VERSION = 44;
    public static final String NAME = "tv";
    public static final String SYMBOL = "@@@";
    private static final int BACKUP_KEEP_COUNT = 7;
    private static final int PARTIAL_BACKUP_KEEP_COUNT = 1;
    private static final String BACKUP_TEMP_FILE = ".backup.tmp";
    private static final Object BACKUP_LOCK = new Object();
    private static final AtomicBoolean AUTO_BACKUP_PENDING = new AtomicBoolean();

    private static volatile AppDatabase instance;

    public static synchronized AppDatabase get() {
        if (instance == null) instance = create(App.get());
        return instance;
    }

    public static void backup() {
        backup(new com.fongmi.android.tv.impl.Callback());
    }

    public static void autoBackup() {
        if (!beginAutoBackup()) return;
        try {
            backup(new com.fongmi.android.tv.impl.Callback(), null, true);
        } catch (RuntimeException e) {
            finishAutoBackup();
            throw e;
        }
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback) {
        backup(callback, null);
    }

    public static void backup(com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress) {
        backup(callback, progress, false);
    }

    private static void backup(com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress, boolean automatic) {
        Task.execute(() -> {
            try {
                synchronized (BACKUP_LOCK) {
                    File temporary = new File(Path.tv(), BACKUP_TEMP_FILE);
                    try {
                        AppBackup.CreateResult result = AppBackup.create(temporary, progress);
                        File target = new File(Path.tv(), AppBackup.fileName(result.hasWarning()));
                        publishBackup(temporary, target);
                        App.post(() -> {
                            callback.success();
                            if (result.hasWarning()) Notify.show(result.warning);
                        });
                        cleanOld();
                    } catch (Exception e) {
                        SpiderDebug.log("backup", "local create failed error=%s", e.getMessage());
                        SpiderDebug.log("backup", e);
                        App.post(callback::error);
                    } finally {
                        Path.clear(temporary);
                    }
                }
            } finally {
                if (automatic) finishAutoBackup();
            }
        });
    }

    static boolean beginAutoBackup() {
        return AUTO_BACKUP_PENDING.compareAndSet(false, true);
    }

    static void finishAutoBackup() {
        AUTO_BACKUP_PENDING.set(false);
    }

    static void publishBackup(File temporary, File target) throws IOException {
        if (temporary.renameTo(target)) return;
        if (target.exists() && !target.delete()) throw new IOException("Unable to replace backup: " + target.getAbsolutePath());
        if (!temporary.renameTo(target)) throw new IOException("Unable to publish backup: " + target.getAbsolutePath());
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback) {
        restore(file, callback, null);
    }

    public static void restore(File file, com.fongmi.android.tv.impl.Callback callback, AppBackup.Progress progress) {
        Task.execute(() -> {
            try {
                AppBackup.RestoreResult result = AppBackup.restore(file, progress);
                App.post(() -> {
                    callback.success();
                    if (result.hasWarning()) Notify.show(result.warning);
                });
            } catch (Exception e) {
                SpiderDebug.log("backup", "local restore failed file=%s error=%s", file == null ? "" : file.getAbsolutePath(), e.getMessage());
                App.post(callback::error);
            }
        });
    }

    private static void cleanOld() {
        cleanOld(Path.tv());
    }

    static void cleanOld(File directory) {
        List<File> complete = new ArrayList<>();
        List<File> partial = new ArrayList<>();
        File[] files = directory == null ? null : directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!AppBackup.isBackup(file)) continue;
            if (AppBackup.isPartialBackupName(file.getName())) partial.add(file);
            else complete.add(file);
        }
        trimBackups(complete, BACKUP_KEEP_COUNT);
        trimBackups(partial, PARTIAL_BACKUP_KEEP_COUNT);
    }

    private static void trimBackups(List<File> items, int keepCount) {
        items.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        for (int i = keepCount; i < items.size(); i++) Path.clear(items.get(i));
    }

    private static AppDatabase create(Context context) {
        return Room.databaseBuilder(context, AppDatabase.class, NAME)
                .addMigrations(Migrations.MIGRATION_30_31)
                .addMigrations(Migrations.MIGRATION_31_32)
                .addMigrations(Migrations.MIGRATION_32_33)
                .addMigrations(Migrations.MIGRATION_33_34)
                .addMigrations(Migrations.MIGRATION_34_35)
                .addMigrations(Migrations.MIGRATION_35_36)
                .addMigrations(Migrations.MIGRATION_36_37)
                .addMigrations(Migrations.MIGRATION_37_38)
                .addMigrations(Migrations.MIGRATION_38_39)
                .addMigrations(Migrations.MIGRATION_39_40)
                .addMigrations(Migrations.MIGRATION_40_41)
                .addMigrations(Migrations.MIGRATION_41_42)
                .addMigrations(Migrations.MIGRATION_42_43)
                .addMigrations(Migrations.MIGRATION_43_44)
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries().build();
    }

    public abstract KeepDao getKeepDao();

    public abstract SiteDao getSiteDao();

    public abstract LiveDao getLiveDao();

    public abstract TrackDao getTrackDao();

    public abstract ConfigDao getConfigDao();

    public abstract DeviceDao getDeviceDao();

    public abstract HistoryDao getHistoryDao();

    public abstract PlaybackDeleteTombstoneDao getPlaybackDeleteTombstoneDao();

    public abstract TmdbSeasonProgressDao getTmdbSeasonProgressDao();
}
