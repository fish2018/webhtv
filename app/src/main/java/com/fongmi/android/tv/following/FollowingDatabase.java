package com.fongmi.android.tv.following;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.fongmi.android.tv.App;

@Database(entities = {Following.class, FollowingSource.class}, version = 1, exportSchema = true)
public abstract class FollowingDatabase extends RoomDatabase {

    public static final String NAME = "following";
    public static final int VERSION = 1;
    private static volatile FollowingDatabase instance;

    public static FollowingDatabase get() {
        if (instance == null) {
            synchronized (FollowingDatabase.class) {
                if (instance == null) instance = create(App.get());
            }
        }
        return instance;
    }

    public static FollowingDatabase create(Context context) {
        return Room.databaseBuilder(context.getApplicationContext(), FollowingDatabase.class, NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
    }

    public abstract FollowingDao getFollowingDao();

    public abstract FollowingSourceDao getFollowingSourceDao();
}
