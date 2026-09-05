package com.fongmi.android.tv;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleEventObserver;

import com.fongmi.android.tv.bean.Update;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.impl.UpdateListener;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.dialog.UpdateDialog;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.Github;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.update.HttpUpdateTransfer;
import com.fongmi.android.tv.update.OciArtifact;
import com.fongmi.android.tv.update.OciMirror;
import com.fongmi.android.tv.update.OciUpdateTransfer;
import com.fongmi.android.tv.update.UpdateHttp;
import com.fongmi.android.tv.update.UpdateRoutePlanner;
import com.fongmi.android.tv.update.UpdateTarget;
import com.fongmi.android.tv.update.UpdateTransfer;
import com.fongmi.android.tv.utils.GithubProxy;
import com.github.catvod.utils.Path;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Updater implements UpdateTransfer.Callback, UpdateListener {

    private static final String TAG = "Updater";
    private static final String DEFAULT_RELEASE_NOTES = "手动触发 GitHub Actions 构建发布。";
    private static final String SOURCE_CNB = "cnb";
    private static final String SOURCE_GITHUB = "github";
    private static final long GITHUB_REQUEST_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(4);
    private static final long UPDATE_CHECK_TIMEOUT_MS = GITHUB_REQUEST_TIMEOUT_MS * 5 + TimeUnit.SECONDS.toMillis(2);
    private static final Map<String, String> GITHUB_API_HEADERS = Map.of("Accept", "application/vnd.github+json", "X-GitHub-Api-Version", "2022-11-28");
    private static final Map<String, String> GITHUB_ASSET_HEADERS = Map.of("Accept", "application/octet-stream", "X-GitHub-Api-Version", "2022-11-28");
    private static final Updater INSTANCE = new Updater();

    private final LifecycleEventObserver lifecycleObserver = (source, event) -> {
        if (!(source instanceof FragmentActivity)) return;
        FragmentActivity activity = (FragmentActivity) source;
        if (event == Lifecycle.Event.ON_DESTROY) unbind(activity);
    };

    private WeakReference<FragmentActivity> activityRef;
    private UpdateDialog dialog;
    private UpdateTransfer transfer;
    private List<UpdateTarget> routes;
    private int routeIndex;
    private Update stable;
    private Update beta;
    private Update selected;
    private boolean force;
    private boolean downloading;
    private boolean canceled;
    private int lastProgress = -1;
    private long lastBytes;
    private long lastTotal;
    private long lastSpeed;
    private long lastElapsed;

    private Updater() {
    }

    public static Updater create() {
        return INSTANCE;
    }

    private File getFile() {
        return Path.cache("update.apk");
    }

    private String getName() {
        return BuildConfig.FLAVOR_mode + "-" + BuildConfig.FLAVOR_abi;
    }

    public Updater force() {
        force = true;
        Notify.show(R.string.update_check);
        Setting.putUpdate(true);
        return this;
    }

    public void start(FragmentActivity activity) {
        bind(activity);
        boolean forceCheck = force;
        force = false;
        if (downloading) {
            restoreDialog(activity);
            return;
        }
        if (!Setting.getUpdate()) return;
        Task.execute(() -> doInBackground(activity, forceCheck));
    }

    public void resume(FragmentActivity activity) {
        bind(activity);
        restoreDialog(activity);
    }

    private void doInBackground(FragmentActivity activity, boolean forceCheck) {
        long deadline = SystemClock.elapsedRealtime() + UPDATE_CHECK_TIMEOUT_MS;
        Future<Update> stableFuture = Task.executor().submit(() -> getUpdate(Update.CHANNEL_STABLE));
        Future<Update> betaFuture = Task.executor().submit(() -> getUpdate(Update.CHANNEL_BETA));
        stable = awaitUpdate(stableFuture, Update.CHANNEL_STABLE, deadline);
        beta = awaitUpdate(betaFuture, Update.CHANNEL_BETA, deadline);
        if (!stable.hasUpdate() && !beta.hasUpdate()) {
            if (forceCheck && (stable.hasManifest() || beta.hasManifest())) {
                selected = getPreferredUpdate();
                App.post(() -> show(activity));
                return;
            }
            if (forceCheck) App.post(() -> Notify.show(hasErrorOnly() ? R.string.update_failed : R.string.update_latest));
            return;
        }
        selected = getPreferredUpdate();
        App.post(() -> show(activity));
    }

    private Update getPreferredUpdate() {
        if (stable != null && stable.hasUpdate()) return stable;
        if (beta != null && beta.hasUpdate()) return beta;
        if (stable != null && stable.hasManifest()) return stable;
        return beta;
    }

    private Update awaitUpdate(Future<Update> future, String channel, long deadline) {
        try {
            if (future.isDone()) return future.get();
            long remaining = deadline - SystemClock.elapsedRealtime();
            if (remaining <= 0) throw new TimeoutException("Update check timed out");
            return future.get(remaining, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            future.cancel(true);
            Log.w(TAG, "update_result_failed channel=" + channel + " type=" + e.getClass().getSimpleName());
            Update update = Update.empty(channel);
            update.error = e.getMessage();
            return update;
        }
    }

    private Update getUpdate(String channel) {
        String manifestName = getManifestName(channel);
        Update update = readUpdate(channel, Github.getChannelAsset(manifestName), SOURCE_GITHUB);
        if (update.hasManifest()) return update;
        if (Update.CHANNEL_BETA.equals(channel)) {
            update = readUpdate(channel, Github.getCnbMirrorAsset(manifestName), SOURCE_CNB);
            if (update.hasManifest()) return update;
            return getGithubBetaUpdate(channel);
        }
        update = readUpdate(channel, Github.getGithubLatestAsset(manifestName), SOURCE_GITHUB);
        if (update.hasManifest()) return update;
        return getGithubStableUpdate(channel);
    }

    private Update getGithubStableUpdate(String channel) {
        try {
            JSONObject release = new JSONObject(UpdateHttp.string(Github.getLatestReleaseApi(), GITHUB_API_HEADERS, GITHUB_REQUEST_TIMEOUT_MS));
            return readGithubReleaseUpdate(channel, release);
        } catch (Exception e) {
            Log.w(TAG, "release_lookup_failed channel=" + channel + " type=" + e.getClass().getSimpleName());
            return Update.empty(channel);
        }
    }

    private Update getGithubBetaUpdate(String channel) {
        String manifestName = getManifestName(channel);
        try {
            JSONArray releases = new JSONArray(UpdateHttp.string(Github.getReleasesApi(), GITHUB_API_HEADERS, GITHUB_REQUEST_TIMEOUT_MS));
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.optJSONObject(i);
                if (release == null || !isBetaRelease(release)) continue;
                if (findAsset(release.optJSONArray("assets"), manifestName) == null) continue;
                return readGithubReleaseUpdate(channel, release);
            }
            Log.w(TAG, "release_manifest_not_found channel=" + channel + " releases=" + releases.length() + " asset=" + manifestName);
        } catch (Exception e) {
            Log.w(TAG, "release_lookup_failed channel=" + channel + " type=" + e.getClass().getSimpleName());
        }
        return Update.empty(channel);
    }

    private boolean isBetaRelease(JSONObject release) {
        String tag = release.optString("tag_name");
        return release.optBoolean("prerelease") || tag.contains("-beta-");
    }

    private JSONObject findAsset(JSONArray assets, String name) {
        if (assets == null) return null;
        for (int i = 0; i < assets.length(); i++) {
            JSONObject asset = assets.optJSONObject(i);
            if (asset == null || !name.equals(asset.optString("name"))) continue;
            return asset;
        }
        return null;
    }

    private Update readGithubReleaseUpdate(String channel, JSONObject release) {
        JSONObject asset = findAsset(release.optJSONArray("assets"), getManifestName(channel));
        long assetId = asset == null ? 0 : asset.optLong("id");
        if (assetId <= 0) {
            Log.w(TAG, "release_manifest_not_found channel=" + channel + " asset=" + getManifestName(channel));
            return Update.empty(channel);
        }
        return readUpdate(channel, Github.getReleaseAssetApi(assetId), SOURCE_GITHUB, GITHUB_ASSET_HEADERS, release.optString("body"));
    }

    private Update readUpdate(String channel, String manifestUrl, String source) {
        return readUpdate(channel, manifestUrl, source, null, "");
    }

    private Update readUpdate(String channel, String manifestUrl, String source, Map<String, String> headers, String fallbackNotes) {
        Update update = Update.empty(channel);
        try {
            GithubProxy.Config config = GithubProxy.config();
            String proxiedUrl = config.rewrite(manifestUrl);
            String text = UpdateHttp.string(proxiedUrl, headers, GITHUB_REQUEST_TIMEOUT_MS);
            if (TextUtils.isEmpty(text)) throw new IllegalStateException("Empty update manifest: " + manifestUrl);
            JSONObject object = new JSONObject(text);
            update.name = object.optString("name");
            update.versionName = object.optString("versionName");
            update.desc = normalizeText(object.optString("desc"));
            update.notes = normalizeText(object.optString("notes"));
            update.channel = object.optString("channel", channel);
            update.code = object.optInt("code");
            update.apk = object.optString("apk");
            update.size = object.optLong("size");
            update.sha256 = object.optString("sha256");
            parseDownloads(object, update);
            if (TextUtils.isEmpty(update.githubUrl)) update.githubUrl = getGithubApkUrl(update);
            update.apkUrl = getApkUrl(update, source);
            if (isDefaultReleaseNotes(update.notes)) update.notes = "";
            if (TextUtils.isEmpty(update.notes) && TextUtils.isEmpty(update.desc)) {
                String notes = TextUtils.isEmpty(fallbackNotes) ? getReleaseNotes(update.name) : fallbackNotes;
                if (!TextUtils.isEmpty(notes)) update.notes = normalizeText(notes);
            }
            if (update.hasManifest()) Log.i(TAG, "manifest_loaded channel=" + channel + " source=" + source);
            else Log.w(TAG, "manifest_invalid channel=" + channel + " source=" + source);
        } catch (Exception e) {
            Log.w(TAG, "manifest_load_failed channel=" + channel + " source=" + source + " type=" + e.getClass().getSimpleName());
            update.error = e.getMessage();
        }
        return update;
    }

    private void parseDownloads(JSONObject object, Update update) {
        JSONObject downloads = object.optJSONObject("downloads");
        JSONObject github = downloads == null ? null : downloads.optJSONObject("github");
        update.githubUrl = github == null ? "" : github.optString("url");
        if (TextUtils.isEmpty(update.githubUrl)) update.githubUrl = getGithubApkUrl(update);
        update.apkUrl = update.githubUrl;
        JSONObject oci = downloads == null ? null : downloads.optJSONObject("oci");
        if (oci == null) return;
        OciArtifact artifact = new OciArtifact(
                oci.optString("registry"),
                oci.optString("repository"),
                oci.optString("reference"),
                oci.optString("manifestDigest"),
                oci.optString("layerDigest"),
                oci.optLong("size", update.size));
        String apkDigest = TextUtils.isEmpty(update.sha256) ? "" : "sha256:" + update.sha256.toLowerCase(Locale.ROOT);
        if (artifact.isValid() && (apkDigest.isEmpty() || apkDigest.equals(artifact.layerDigest)) && (update.size <= 0 || update.size == artifact.size)) update.oci = artifact;
    }

    private String normalizeText(String text) {
        if (TextUtils.isEmpty(text)) return "";
        return text
                .replace("\\r\\n", "\n")
                .replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\'", "'");
    }

    private String getManifestName(String channel) {
        return getAssetName(channel, "json");
    }

    private String getDefaultApkName(String channel) {
        return getAssetName(channel, "apk");
    }

    private String getAssetName(String channel, String ext) {
        String suffix = Update.CHANNEL_BETA.equals(channel) ? "-beta" : "";
        return getName() + suffix + "." + ext;
    }

    private String getGithubApkUrl(Update update) {
        String apk = TextUtils.isEmpty(update.apk) ? getDefaultApkName(update.channel) : update.apk;
        if (apk.startsWith("http://") || apk.startsWith("https://")) return apk;
        return TextUtils.isEmpty(update.name) ? "" : Github.getGithubReleaseAsset(update.name, getFileName(apk, update.channel));
    }

    private String getApkUrl(Update update, String source) {
        String apk = TextUtils.isEmpty(update.apk) ? getDefaultApkName(update.channel) : update.apk;
        if (SOURCE_GITHUB.equals(source) && !TextUtils.isEmpty(update.name)) return Github.getGithubReleaseAsset(update.name, getFileName(apk, update.channel));
        if (apk.startsWith("http://") || apk.startsWith("https://")) return apk;
        return Github.getCnbAsset(apk);
    }

    private String getFileName(String value, String channel) {
        int query = value.indexOf('?');
        if (query >= 0) value = value.substring(0, query);
        int slash = value.lastIndexOf('/');
        String name = slash >= 0 ? value.substring(slash + 1) : value;
        return TextUtils.isEmpty(name) ? getDefaultApkName(channel) : name;
    }

    private boolean isDefaultReleaseNotes(String notes) {
        return !TextUtils.isEmpty(notes) && DEFAULT_RELEASE_NOTES.equals(notes.trim());
    }

    private String getReleaseNotes(String tag) {
        if (TextUtils.isEmpty(tag)) return "";
        String notes = readReleaseNotes(tag);
        if (!TextUtils.isEmpty(notes) || tag.startsWith("v")) return notes;
        return readReleaseNotes("v" + tag);
    }

    private String readReleaseNotes(String tag) {
        try {
            GithubProxy.Config config = GithubProxy.config();
            String proxiedUrl = config.rewrite(Github.getReleaseApi(tag));
            return new JSONObject(UpdateHttp.string(proxiedUrl, GITHUB_API_HEADERS, GITHUB_REQUEST_TIMEOUT_MS)).optString("body");
        } catch (Exception ignored) {
            return "";
        }
    }

    private boolean hasErrorOnly() {
        return !stable.hasManifest() && !beta.hasManifest() && (!TextUtils.isEmpty(stable.error) || !TextUtils.isEmpty(beta.error));
    }

    private void show(FragmentActivity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        bind(activity);
        dismiss();
        Notify.dismissToast();
        String channel = selected == null ? Update.CHANNEL_STABLE : selected.channel;
        dialog = UpdateDialog.create().stable(stable).beta(beta).selected(channel).listener(this).show(activity);
    }

    @Override
    public void onConfirm(View view) {
        if (selected == null || !selected.hasUpdate()) {
            Notify.show(R.string.update_latest);
            return;
        }
        view.setEnabled(false);
        showBackupConfirmDialog(view);
    }

    private void showBackupConfirmDialog(View view) {
        FragmentActivity activity = activityRef == null ? null : activityRef.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(R.string.update_backup_title)
                .setMessage(R.string.update_backup_message)
                .setPositiveButton(R.string.update_backup_positive, (dialog, which) -> startBackupAndUpdate(view))
                .setNegativeButton(R.string.update_backup_negative, (dialog, which) -> startUpdate(view))
                .setNeutralButton(R.string.dialog_negative, (dialog, which) -> view.setEnabled(true))
                .setCancelable(false)
                .show();
    }

    private void startBackupAndUpdate(View view) {
        Notify.show(R.string.update_backup_running);
        PermissionUtil.requestFile(activityRef.get(), allGranted -> {
            if (!allGranted) {
                Notify.show(R.string.update_backup_permission_denied);
                startUpdate(view);
                return;
            }
            AppDatabase.backup(new com.fongmi.android.tv.impl.Callback() {
                @Override
                public void success() {
                    Notify.show(R.string.update_backup_done);
                    startUpdate(view);
                }

                @Override
                public void error() {
                    Notify.show(R.string.update_backup_failed);
                    startUpdate(view);
                }
            });
        });
    }

    private void startUpdate(View view) {
        downloading = true;
        canceled = false;
        routes = getRoutes(selected);
        routeIndex = 0;
        if (routes.isEmpty()) {
            downloading = false;
            view.setEnabled(true);
            Notify.show(R.string.update_download_source_unavailable);
            return;
        }
        resetProgress();
        Path.clear(getFile());
        setDialogProgress(0, 0, selected.size, 0, 0);
        startNextDownload();
    }

    private List<UpdateTarget> getRoutes(Update update) {
        try {
            GithubProxy.Config github = GithubProxy.config();
            String endpoint = update.oci == null ? "" : OciMirror.resolve(Setting.getUpdateOciMirror(), Setting.getUpdateOciMirrorUrl(), update.oci);
            return UpdateRoutePlanner.plan(Setting.getUpdateSource(), update.githubUrl, update.oci, github, endpoint);
        } catch (Exception e) {
            return List.of();
        }
    }

    private void startNextDownload() {
        if (routes == null || routeIndex >= routes.size()) return;
        UpdateTarget target = routes.get(routeIndex++);
        transfer = target.kind == UpdateTarget.Kind.OCI ? new OciUpdateTransfer(target, getFile()) : new HttpUpdateTransfer(target.url, getFile(), selected == null ? 0 : selected.size);
        transfer.start(this);
    }

    private boolean retryFallback() {
        if (canceled || selected == null || routes == null || routeIndex >= routes.size()) return false;
        Path.clear(getFile());
        resetProgress();
        setDialogProgress(0, 0, selected.size, 0, 0);
        startNextDownload();
        return true;
    }

    @Override
    public void onCancel(View view) {
        if (downloading) {
            canceled = true;
            downloading = false;
            if (transfer != null) transfer.cancel();
            transfer = null;
            routes = null;
            resetProgress();
            Notify.show(R.string.update_canceled);
            dismiss();
            return;
        }
        Setting.putUpdate(false);
        if (transfer != null) transfer.cancel();
        transfer = null;
        dismiss();
    }

    @Override
    public void onClose() {
        dialog = null;
    }

    @Override
    public void onChannel(String channel) {
        selected = Update.CHANNEL_BETA.equals(channel) ? beta : stable;
    }

    private void dismiss() {
        try {
            if (dialog != null) dialog.dismissAllowingStateLoss();
        } catch (Exception ignored) {
        } finally {
            dialog = null;
        }
    }

    @Override
    public void progress(int progress, long bytes, long total, long speed, long elapsed) {
        setDialogProgress(progress, bytes, total, speed, elapsed);
    }

    private void setDialogProgress(int progress, long bytes, long total, long speed, long elapsed) {
        if (canceled || !downloading) return;
        long manifestSize = selected == null ? 0 : selected.size;
        if (total <= 0 && manifestSize > 0) total = manifestSize;
        if (progress < 0 && total > 0 && bytes > 0) progress = (int) (bytes * 100.0 / total);
        lastProgress = progress;
        lastBytes = bytes;
        lastTotal = total;
        lastSpeed = speed;
        lastElapsed = elapsed;
        if (dialog == null) return;
        if (!dialog.setProgress(progress, bytes, total, speed, elapsed)) dialog = null;
    }

    @Override
    public void error(String msg) {
        if (canceled) return;
        transfer = null;
        if (retryFallback()) return;
        downloading = false;
        routes = null;
        resetProgress();
        Notify.show(msg);
        dismiss();
    }

    @Override
    public void success(File file) {
        if (canceled) return;
        transfer = null;
        Update target = selected;
        Task.execute(() -> {
            String error = validate(file, target);
            App.post(() -> {
                if (canceled) return;
                downloading = false;
                resetProgress();
                if (!TextUtils.isEmpty(error)) {
                    Path.clear(file);
                    downloading = true;
                    if (retryFallback()) return;
                    downloading = false;
                    routes = null;
                    Notify.show(error);
                    dismiss();
                    return;
                }
                routes = null;
                FileUtil.openFile(file);
                dismiss();
            });
        });
    }

    private void restoreDialog(FragmentActivity activity) {
        if (!downloading || selected == null) return;
        show(activity);
        setDialogProgress(lastProgress, lastBytes, lastTotal, lastSpeed, lastElapsed);
    }

    private String validate(File file, Update update) {
        if (file == null || !file.exists() || file.length() <= 0) return ResUtil.getString(R.string.update_download_invalid);
        if (update != null && update.size > 0 && file.length() != update.size) return ResUtil.getString(R.string.update_download_incomplete);
        boolean checksumVerified = false;
        if (update != null && !TextUtils.isEmpty(update.sha256)) {
            if (!update.sha256.equalsIgnoreCase(sha256(file))) return ResUtil.getString(R.string.update_download_checksum);
            checksumVerified = true;
        }
        if (!validatePackage(file, update, checksumVerified)) return ResUtil.getString(R.string.update_download_identity);
        return "";
    }

    private boolean validatePackage(File file, Update update, boolean checksumVerified) {
        try {
            PackageManager manager = App.get().getPackageManager();
            int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo archive = manager.getPackageArchiveInfo(file.getAbsolutePath(), flags);
            PackageInfo installed = manager.getPackageInfo(BuildConfig.APPLICATION_ID, flags);
            if (archive == null || installed == null || !BuildConfig.APPLICATION_ID.equals(archive.packageName)) return false;
            long archiveCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? archive.getLongVersionCode() : archive.versionCode;
            if (update != null && update.code > 0 && archiveCode != update.code) return false;
            if (update != null && !TextUtils.isEmpty(update.versionName) && !update.versionName.equals(archive.versionName)) return false;
            return signaturesMatch(installed, archive, checksumVerified);
        } catch (Exception e) {
            return false;
        }
    }

    static boolean canAcceptUnreadableArchiveSignature(boolean checksumVerified) {
        return checksumVerified;
    }

    private boolean signaturesMatch(PackageInfo installed, PackageInfo archive, boolean checksumVerified) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Some OEM ROMs do not populate signingInfo for getPackageArchiveInfo().
            // Keep the installed signature authoritative, but let an unreadable
            // candidate reach the OS installer, which remains the compatibility gate.
            if (installed.signingInfo == null) return false;
            if (archive.signingInfo == null) return canAcceptUnreadableArchiveSignature(checksumVerified);
            if (installed.signingInfo.hasMultipleSigners() || archive.signingInfo.hasMultipleSigners()) {
                Set<String> installedPrints = fingerprints(installed.signingInfo.getApkContentsSigners());
                Set<String> archivePrints = fingerprints(archive.signingInfo.getApkContentsSigners());
                if (installedPrints.isEmpty()) return false;
                if (archivePrints.isEmpty()) return canAcceptUnreadableArchiveSignature(checksumVerified);
                return installedPrints.equals(archivePrints);
            }
            Set<String> current = fingerprints(installed.signingInfo.getApkContentsSigners());
            Set<String> candidateHistory = fingerprints(archive.signingInfo.getSigningCertificateHistory());
            if (current.isEmpty()) return false;
            if (candidateHistory.isEmpty()) return canAcceptUnreadableArchiveSignature(checksumVerified);
            return candidateHistory.containsAll(current);
        }
        if (fingerprints(installed.signatures).isEmpty()) return false;
        if (fingerprints(archive.signatures).isEmpty()) return canAcceptUnreadableArchiveSignature(checksumVerified);
        return fingerprints(installed.signatures).equals(fingerprints(archive.signatures));
    }

    private Set<String> fingerprints(Signature[] signatures) {
        Set<String> values = new HashSet<>();
        if (signatures == null) return values;
        for (Signature signature : signatures) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                values.add(Arrays.toString(digest.digest(signature.toByteArray())));
            } catch (Exception ignored) {
            }
        }
        return values;
    }

    private String sha256(File file) {
        try (FileInputStream input = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[16384];
            int read;
            while ((read = input.read(buffer)) != -1) digest.update(buffer, 0, read);
            StringBuilder builder = new StringBuilder();
            for (byte value : digest.digest()) builder.append(String.format(Locale.ROOT, "%02x", value));
            return builder.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private void bind(FragmentActivity activity) {
        if (activity == null) return;
        FragmentActivity old = activityRef == null ? null : activityRef.get();
        if (old == activity) return;
        if (old != null) old.getLifecycle().removeObserver(lifecycleObserver);
        activityRef = new WeakReference<>(activity);
        activity.getLifecycle().addObserver(lifecycleObserver);
    }

    private void unbind(FragmentActivity activity) {
        FragmentActivity current = activityRef == null ? null : activityRef.get();
        if (current != activity) return;
        activity.getLifecycle().removeObserver(lifecycleObserver);
        activityRef = null;
        if (!downloading) dialog = null;
    }

    private void resetProgress() {
        lastProgress = -1;
        lastBytes = 0;
        lastTotal = 0;
        lastSpeed = 0;
        lastElapsed = 0;
    }
}
