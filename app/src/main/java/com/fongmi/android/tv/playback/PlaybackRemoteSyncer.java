package com.fongmi.android.tv.playback;

import android.text.TextUtils;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.net.OkHttp;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

public final class PlaybackRemoteSyncer {

    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(10);
    private static final Runnable PERIODIC = new Runnable() {
        @Override
        public void run() {
            if (!started) return;
            Task.execute(() -> syncDue(false));
            if (started) App.post(this, TimeUnit.MINUTES.toMillis(5));
        }
    };
    private static volatile boolean started;

    private PlaybackRemoteSyncer() {
    }

    public static void start() {
        if (started) return;
        started = true;
        App.post(PERIODIC, TimeUnit.SECONDS.toMillis(3));
        Task.execute(() -> syncDue(true));
    }

    public static void stop() {
        started = false;
        App.removeCallbacks(PERIODIC);
    }

    public static void syncDue(boolean startup) {
        if (!started) return;
        if (!ViewingRecordSyncStore.isEnabled() || Setting.isIncognito()) return;
        long now = System.currentTimeMillis();
        for (RemoteSyncConfig config : PlaybackRemoteSyncStore.list()) {
            if (!started) return;
            if (!config.shouldSyncNow(now, startup)) continue;
            sync(config.id);
        }
    }

    public static PlaybackRemoteSyncResult sync(String id) {
        RemoteSyncConfig config = PlaybackRemoteSyncStore.find(id);
        PlaybackRemoteSyncResult result;
        if (config == null) {
            result = PlaybackRemoteSyncResult.failure("远端同步源不存在");
        } else {
            result = sync(config);
        }
        if (config != null) PlaybackRemoteSyncStore.markResult(config.id, result);
        return result;
    }

    private static PlaybackRemoteSyncResult sync(RemoteSyncConfig config) {
        try {
            if (!ViewingRecordSyncStore.isEnabled()) return PlaybackRemoteSyncResult.failure("观影记录同步未开启");
            if (Setting.isIncognito()) return PlaybackRemoteSyncResult.failure("隐身模式不允许同步");
            if (!config.isUsable()) return PlaybackRemoteSyncResult.failure("远端同步源未完成配置");
            int cid = VodConfig.getCid();
            String configKey = PlaybackConfigIdentity.keyForCid(cid);
            PlaybackIdentityResolver.Result identity = PlaybackIdentityResolver.resolve(config, cid);
            PlaybackRemoteSyncStore.markIdentity(config.id, identity);
            if (identity.success && !TextUtils.isEmpty(identity.canonicalInterfaceKey)) {
                configKey = identity.canonicalInterfaceKey;
                if (identity.resetSince) config.resetCursor(configKey);
            }
            Config local = Config.find(cid);
            List<String> aliases = new ArrayList<>();
            boolean aliasRouting = identity.success && identity.capabilities;
            if (local != null && aliasRouting) {
                aliases.addAll(local.getLegacyConfigKeys());
                aliases.addAll(local.getAddressMatchAliases());
            }
            LinkedHashSet<String> uniqueAliases = new LinkedHashSet<>();
            for (String alias : aliases) if (!TextUtils.isEmpty(alias) && !TextUtils.equals(alias, configKey)) uniqueAliases.add(alias);
            List<SyncPage> pages = new ArrayList<>();
            pages.add(new SyncPage(configKey, fetch(config, configKey, new ArrayList<>(uniqueAliases))));
            // A legacy endpoint cannot resolve aliases. Read its old URL-hash spaces
            // independently, but never double-write them.
            if (!aliasRouting && local != null) {
                for (String legacy : local.getLegacyConfigKeys()) {
                    if (TextUtils.isEmpty(legacy) || TextUtils.equals(legacy, configKey)) continue;
                    pages.add(new SyncPage(legacy, fetch(config, legacy, new ArrayList<>())));
                }
            }
            PlaybackProgressBatchResult batch = new PlaybackProgressBatchResult();
            java.util.HashMap<String, String> cursors = new java.util.HashMap<>();
            boolean complete = true;
            for (SyncPage page : pages) {
                PlaybackRemoteSyncPayload payload = PlaybackRemoteSyncPayload.fromJson(page.body);
                complete &= config.maxItems <= 0 || payload.total() <= config.maxItems;
                payload.limit(config.maxItems);
                // Apply tombstones first; a newer upsert in the same response can still restore the item.
                batch.addAll(PlaybackProgressWriter.deleteFromRemoteSync(payload.deletions, config));
                batch.addAll(PlaybackProgressWriter.applyFromRemoteSync(payload.upserts, config));
                if (!TextUtils.isEmpty(payload.nextSince)) cursors.put(page.configKey, payload.nextSince);
            }
            RefreshEvent.history();
            SpiderDebug.log("playback-remote-sync", "source=%s fetched=%s applied=%s deleted=%s skipped=%s failed=%s identity=%s", config.displayName(), batch.total, batch.applied, batch.deleted, batch.skipped, batch.failed, identity.action);
            // Do not move incremental cursors past malformed or truncated pages.
            if (!complete || batch.failed > 0) cursors.clear();
            return PlaybackRemoteSyncResult.success(batch, configKey, cursors.get(configKey), cursors);
        } catch (Throwable e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            SpiderDebug.log("playback-remote-sync", e);
            return PlaybackRemoteSyncResult.failure(message);
        }
    }

    private static String fetch(RemoteSyncConfig config, String configKey, List<String> aliases) throws Exception {
        Request.Builder builder = new Request.Builder().url(config.url).get();
        builder.header("Accept", "application/json");
        PlaybackHttpHeaders.header(builder, "X-WebHTV-Config-Key", configKey);
        PlaybackHttpHeaders.header(builder, "X-WebHTV-Config-Name", PlaybackConfigIdentity.currentName());
        PlaybackHttpHeaders.header(builder, "X-WebHTV-Config-Type", "vod");
        PlaybackHttpHeaders.header(builder, "X-WebHTV-Identity-Version", PlaybackConfigIdentity.IDENTITY_VERSION);
        PlaybackHttpHeaders.header(builder, "X-WebHTV-Address-Match-Version", PlaybackConfigIdentity.ADDRESS_MATCH_VERSION);
        if (aliases != null && !aliases.isEmpty()) PlaybackHttpHeaders.header(builder, "X-WebHTV-Config-Aliases", TextUtils.join(",", aliases));
        PlaybackHttpHeaders.header(builder, "X-WebHTV-Since", config.cursor(configKey));
        if (config.maxItems > 0) builder.header("X-WebHTV-Limit", String.valueOf(config.maxItems));
        if (!TextUtils.isEmpty(config.token)) builder.header("X-WebHTV-Token", config.token);
        try (Response response = OkHttp.client(TIMEOUT_MS).newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) throw new IllegalStateException("HTTP " + response.code());
            return response.body() == null ? "" : response.body().string();
        }
    }

    private static final class SyncPage {
        final String configKey;
        final String body;

        SyncPage(String configKey, String body) {
            this.configKey = configKey;
            this.body = body;
        }
    }
}
