package com.fongmi.android.tv.following;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.ui.activity.FollowingActivity;

public final class FollowingNotifier {

    public static final String CHANNEL_ID = "webhtv.following.updates";

    private FollowingNotifier() {
    }

    public static void createChannel() {
        NotificationManagerCompat manager = NotificationManagerCompat.from(App.get());
        manager.createNotificationChannel(new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(App.get().getString(R.string.following_channel_name))
                .setDescription(App.get().getString(R.string.following_channel_description))
                .build());
    }

    public static boolean canNotify() {
        return FollowingSettings.isEnabled() && FollowingSettings.isNotificationsEnabled()
                && ContextCompat.checkSelfPermission(App.get(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                && NotificationManagerCompat.from(App.get()).areNotificationsEnabled();
    }

    public static boolean notifyUpdate(Following item, FollowingSource source) {
        // Keep the database-derived site state fresh while the user is already looking at the app;
        // defer the system notification until the next background check.
        if (App.activity() != null) return false;
        if (item == null || !FollowingUpdatePolicy.shouldNotify(item) || !canNotify()) return false;
        createChannel();
        Intent intent = FollowingActivity.intent(App.get(), item.identityKey);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(App.get(), item.identityKey.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        String text = App.get().getString(R.string.following_notification_text,
                FollowingUpdatePolicy.releasedEpisode(item),
                source == null ? 0 : source.playableEpisode);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(App.get(), CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_home_following)
                .setContentTitle(App.get().getString(R.string.following_notification_title, item.vodName))
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        NotificationManagerCompat.from(App.get()).notify(item.identityKey.hashCode(), builder.build());
        FollowingUpdatePolicy.markNotified(item, System.currentTimeMillis());
        return true;
    }
}
