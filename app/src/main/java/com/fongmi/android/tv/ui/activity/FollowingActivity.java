package com.fongmi.android.tv.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.History;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.databinding.ActivityFollowingBinding;
import com.fongmi.android.tv.following.AlistSubscriptionImporter;
import com.fongmi.android.tv.following.Following;
import com.fongmi.android.tv.following.FollowingNotifier;
import com.fongmi.android.tv.following.FollowingPlaybackBridge;
import com.fongmi.android.tv.following.FollowingIdentity;
import com.fongmi.android.tv.following.FollowingMetadataSnapshot;
import com.fongmi.android.tv.following.FollowingScheduler;
import com.fongmi.android.tv.following.FollowingSettings;
import com.fongmi.android.tv.following.FollowingSource;
import com.fongmi.android.tv.following.FollowingStore;
import com.fongmi.android.tv.following.FollowingUpdateCoordinator;
import com.fongmi.android.tv.following.FollowingUpdatePolicy;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.FollowingAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.PermissionUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FollowingActivity extends AppCompatActivity implements FollowingAdapter.Listener {

    public static final String EXTRA_IDENTITY_KEY = "following_identity_key";
    private static final int FILTER_ALL = 0;
    private static final int FILTER_UPDATES = 1;
    private static final int FILTER_UNWATCHED = 2;
    private static final int FILTER_ENDED = 3;
    private static final int FILTER_FAILED = 4;
    private static final int FILTER_COUNT = 5;
    private static final Comparator<Following> LIST_ORDER = Comparator
            .comparing((Following item) -> !item.hasUpdate)
            .thenComparing(Comparator.comparingInt((Following item) -> item.unwatchedCount).reversed())
            .thenComparing(Comparator.comparingLong((Following item) -> item.updatedAt).reversed())
            .thenComparing(Comparator.comparingLong((Following item) -> item.metadataUpdatedAt).reversed())
            .thenComparing(Comparator.comparingLong((Following item) -> item.createdAt).reversed());
    private ActivityFollowingBinding binding;
    private FollowingAdapter adapter;
    private String focusIdentity;
    private String pendingNotifyIdentity;
    private int filterIndex = FILTER_ALL;
    private final ActivityResultLauncher<String> notificationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), granted -> {
                if (pendingNotifyIdentity == null) return;
                if (granted) {
                    FollowingSettings.setNotificationsEnabled(true);
                    String identityKey = pendingNotifyIdentity;
                    FollowingPlaybackBridge.setNotifyEnabledAsync(identityKey, true, error -> load());
                    pendingNotifyIdentity = null;
                    return;
                }
                pendingNotifyIdentity = null;
                load();
            });

    public static Intent intent(Context context, String identityKey) {
        Intent intent = new Intent(context, FollowingActivity.class);
        if (!TextUtils.isEmpty(identityKey)) intent.putExtra(EXTRA_IDENTITY_KEY, identityKey);
        return intent;
    }

    public static void start(Activity activity, String identityKey) {
        activity.startActivity(intent(activity, identityKey));
    }

    public static void start(Context context) {
        context.startActivity(intent(context, ""));
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        setTheme(R.style.Theme_App);
        super.onCreate(savedInstanceState);
        if (!FollowingSettings.isEnabled()) {
            Notify.show(R.string.following_enabled_hint);
            finish();
            return;
        }
        binding = ActivityFollowingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        focusIdentity = getIntent().getStringExtra(EXTRA_IDENTITY_KEY);
        FollowingNotifier.createChannel();
        FollowingScheduler.ensurePeriodic(this);
        initView();
        FollowingScheduler.enqueueDueNow(this);
    }

    private void initView() {
        binding.recycler.setLayoutManager(new LinearLayoutManager(this));
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter = new FollowingAdapter(this));
        binding.check.setOnClickListener(view -> checkAll());
        binding.readAll.setOnClickListener(view -> markAllRead());
        binding.filter.setOnClickListener(view -> {
            filterIndex = (filterIndex + 1) % FILTER_COUNT;
            load();
        });
        binding.alistImport.setOnClickListener(view -> showServerImportDialog());
        load();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (binding != null) load();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) Util.hideSystemUI(this);
    }

    private void load() {
        binding.loading.setVisibility(View.VISIBLE);
        Task.execute(() -> {
            List<Following> following = FollowingStore.list();
            List<FollowingAdapter.Row> rows = new ArrayList<>();
            for (Following item : following) {
                if (!matchesFilter(item)) continue;
                rows.add(new FollowingAdapter.Row(item, FollowingStore.preferredSource(item.identityKey)));
            }
            rows.sort((left, right) -> LIST_ORDER.compare(left.following, right.following));
            int unread = FollowingStore.unreadCount();
            App.post(() -> render(rows, unread));
        });
    }

    private void render(List<FollowingAdapter.Row> rows, int unread) {
        if (isFinishing() || binding == null) return;
        binding.loading.setVisibility(View.GONE);
        binding.empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        binding.filter.setText(filterLabel());
        binding.emptyText.setText(filterIndex == FILTER_ALL ? R.string.following_empty : R.string.following_filter_empty);
        binding.recycler.setVisibility(rows.isEmpty() ? View.GONE : View.VISIBLE);
        binding.summary.setText(getString(R.string.following_summary, rows.size(), unread));
        adapter.setItems(rows);
        int index = TextUtils.isEmpty(focusIdentity) ? -1 : adapter.indexOf(focusIdentity);
        if (index >= 0) {
            binding.recycler.post(() -> {
                binding.recycler.scrollToPosition(index);
                focusIdentity = null;
            });
        }
    }

    private void markAllRead() {
        List<String> keys = new ArrayList<>();
        for (FollowingAdapter.Row row : adapter.getItems()) {
            if (row.following.hasUpdate && !TextUtils.isEmpty(row.following.identityKey)) keys.add(row.following.identityKey);
        }
        markReadNow(keys);
    }

    private void markReadNow(List<String> keys) {
        if (keys == null || keys.isEmpty()) return;
        adapter.markReadLocally(keys);
        FollowingPlaybackBridge.markReadAllAsync(keys, error -> {
            if (error != null) load();
            else refreshUnreadSummary();
        });
    }

    private void refreshUnreadSummary() {
        FollowingPlaybackBridge.refreshUnreadCountAsync(unread -> {
            if (binding == null || isFinishing() || isDestroyed()) return;
            binding.summary.setText(getString(R.string.following_summary, adapter.getItemCount(), unread));
        });
    }

    private boolean matchesFilter(Following item) {
        return switch (filterIndex) {
            case FILTER_UPDATES -> item.hasUpdate || item.unwatchedCount > 0;
            case FILTER_UNWATCHED -> item.unwatchedCount > 0;
            case FILTER_ENDED -> FollowingMetadataSnapshot.ENDED.equals(item.officialStatus)
                    || FollowingMetadataSnapshot.CANCELED.equals(item.officialStatus);
            case FILTER_FAILED -> item.lastError != null && !item.lastError.isBlank();
            default -> true;
        };
    }

    private int filterLabel() {
        return switch (filterIndex) {
            case FILTER_UPDATES -> R.string.following_filter_updates;
            case FILTER_UNWATCHED -> R.string.following_filter_unwatched;
            case FILTER_ENDED -> R.string.following_filter_ended;
            case FILTER_FAILED -> R.string.following_filter_failed;
            default -> R.string.following_filter_all;
        };
    }

    private void checkAll() {
        binding.check.setEnabled(false);
        Notify.show(R.string.following_checking);
        Task.execute(() -> {
            List<Following> items = FollowingStore.list();
            if (items.isEmpty()) {
                App.post(() -> {
                    if (binding == null) return;
                    binding.check.setEnabled(true);
                    load();
                });
                return;
            }
            FollowingUpdateCoordinator coordinator = new FollowingUpdateCoordinator();
            int limit = Math.min(5, items.size());
            boolean success = false;
            for (int i = 0; i < limit; i++) success |= coordinator.checkNow(items.get(i).identityKey, true);
            boolean finalSuccess = success;
            App.post(() -> {
                if (binding == null) return;
                binding.check.setEnabled(true);
                Notify.show(finalSuccess ? R.string.following_check_done : R.string.following_check_failed);
                load();
            });
        });
    }

    private void showServerImportDialog() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        int horizontal = dp(20);
        form.setPadding(horizontal, dp(8), horizontal, 0);
        EditText url = field(R.string.following_server_url);
        EditText token = field(R.string.following_server_token);
        token.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(url, new LinearLayout.LayoutParams(-1, -2));
        form.addView(token, new LinearLayout.LayoutParams(-1, -2));
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.following_server_import)
                .setView(form)
                .setNegativeButton(R.string.dialog_negative, null)
                .setNeutralButton(R.string.following_server_preview, (dialog, which) -> startServerImport(url, token, true))
                .setPositiveButton(R.string.following_server_import_action, (dialog, which) -> startServerImport(url, token, false))
                .show();
    }

    private EditText field(int hint) {
        EditText field = new EditText(this);
        field.setHint(hint);
        field.setSingleLine(true);
        return field;
    }

    private void startServerImport(EditText urlView, EditText tokenView, boolean preview) {
        String url = urlView.getText().toString().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Notify.show(R.string.following_server_invalid);
            return;
        }
        String token = tokenView.getText().toString();
        FollowingSettings.setServerImportEnabled(true);
        binding.loading.setVisibility(View.VISIBLE);
        Task.execute(() -> {
            try {
                List<AlistSubscriptionImporter.Candidate> candidates = AlistSubscriptionImporter.fetch(url, token);
                if (preview) {
                    App.post(() -> {
                        if (binding == null) return;
                        binding.loading.setVisibility(View.GONE);
                        showServerPreview(candidates);
                    });
                    return;
                }
                AlistSubscriptionImporter.ImportResult result = AlistSubscriptionImporter.importCandidates(candidates);
                App.post(() -> {
                    if (binding == null) return;
                    binding.loading.setVisibility(View.GONE);
                    Notify.show(getString(R.string.following_server_result, result.created, result.updated));
                    load();
                });
            } catch (Throwable error) {
                App.post(() -> {
                    if (binding != null) binding.loading.setVisibility(View.GONE);
                    Notify.show(error.getMessage());
                });
            }
        });
    }

    private void showServerPreview(List<AlistSubscriptionImporter.Candidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            Notify.show(R.string.following_server_empty);
            return;
        }
        StringBuilder message = new StringBuilder();
        int limit = Math.min(20, candidates.size());
        for (int i = 0; i < limit; i++) {
            AlistSubscriptionImporter.Candidate candidate = candidates.get(i);
            if (i > 0) message.append('\n');
            message.append(candidate.title).append(" · S").append(candidate.season + 1);
            if (candidate.currentEpisodes > 0) message.append(" · E").append(candidate.currentEpisodes);
        }
        if (candidates.size() > limit) message.append("\n… +").append(candidates.size() - limit);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.following_server_preview_title)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.following_server_import_action, (dialog, which) -> {
                    Task.execute(() -> {
                        AlistSubscriptionImporter.ImportResult result = AlistSubscriptionImporter.importCandidates(candidates);
                        App.post(() -> {
                            Notify.show(getString(R.string.following_server_result, result.created, result.updated));
                            load();
                        });
                    });
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onOpenDetail(Following item, FollowingSource source) {
        if (item == null) return;
        StringBuilder message = new StringBuilder();
        int released = FollowingUpdatePolicy.releasedEpisode(item);
        message.append(released > 0 ? getString(R.string.following_official, released) : getString(R.string.following_official_unknown));
        message.append('\n');
        message.append(source != null && source.playableEpisode > 0
                ? getString(R.string.following_source, source.playableEpisode)
                : getString(R.string.following_source_unknown));
        message.append('\n');
        message.append(item.watchedEpisode > 0 ? getString(R.string.following_watched, item.watchedEpisode) : getString(R.string.following_source_unknown));
        if (item.unwatchedCount > 0) message.append('\n').append(getString(R.string.following_unwatched, item.unwatchedCount));
        else if (released > 0) message.append('\n').append(getString(R.string.following_unwatched_none));
        if (item.lastError != null && !item.lastError.isBlank()) message.append('\n').append(item.lastError);
        String[] actions = new String[]{
                getString(R.string.following_continue),
                getString(R.string.following_check),
                getString(R.string.following_read),
                getString(R.string.following_change_source),
                getString(R.string.following_cancel)
        };
        new MaterialAlertDialogBuilder(this)
                .setTitle(item.vodName)
                .setMessage(message)
                .setNegativeButton(R.string.dialog_close, null)
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) onContinue(item, source);
                    else if (which == 1) onCheck(item);
                    else if (which == 2) onRead(item);
                    else if (which == 3) onChangeSource(item);
                    else if (which == 4) onDelete(item);
                })
                .show();
    }

    @Override
    public void onContinue(Following item, FollowingSource source) {
        Task.execute(() -> {
            FollowingStore.reconcile(item);
            History history = FollowingStore.historyFor(item, source);
            App.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (history != null) {
                    VideoActivity.startFromFollowingHistory(this, history);
                    return;
                }
                FollowingSource target = preferred(item, source);
                if (target == null || TextUtils.isEmpty(target.siteKey) || TextUtils.isEmpty(target.vodId)) {
                    Notify.show(R.string.following_check_failed);
                    return;
                }
                TmdbItem tmdb = item.tmdbId > 0 ? new TmdbItem(item.tmdbId, item.mediaType, item.vodName,
                        "", "", item.vodPic, "", "", 0.0) : null;
                TmdbDetailActivity.start(this, target.siteKey, target.vodId, item.vodName, item.vodPic, "", tmdb, Setting.getDetailOpenMode());
            });
        });
    }

    @Override
    public void onFollowNextSeason(Following item) {
        if (item == null || item.latestReleasedSeason <= item.trackedSeason || TextUtils.isEmpty(item.seriesKey)) return;
        String identityKey = FollowingIdentity.identityKey(item.seriesKey, item.latestReleasedSeason);
        long now = System.currentTimeMillis();
        Task.execute(() -> {
            if (FollowingStore.find(identityKey) != null) {
                App.post(() -> FollowingActivity.start(this, identityKey));
                return;
            }
            Following next = item.copy();
            next.identityKey = identityKey;
            next.trackedSeason = item.latestReleasedSeason;
            next.trackedEpisode = 0;
            next.watchedSeason = item.latestReleasedSeason;
            next.watchedEpisode = 0;
            next.position = 0;
            next.duration = 0;
            next.latestReleasedSeason = item.latestReleasedSeason;
            next.latestReleasedEpisode = 0;
            next.seasonTotalEpisodes = 0;
            next.seasonReleasedEpisodes = 0;
            next.nextAirSeason = item.nextAirSeason == next.trackedSeason ? item.nextAirSeason : 0;
            next.nextAirEpisode = item.nextAirSeason == next.trackedSeason ? item.nextAirEpisode : 0;
            next.nextAirAt = item.nextAirSeason == next.trackedSeason ? item.nextAirAt : 0;
            next.readWatermarkEpisode = 0;
            next.lastNotifiedEpisode = 0;
            next.lastNotifiedAt = 0;
            next.lastObservedEpisode = 0;
            next.hasUpdate = false;
            next.unwatchedCount = 0;
            next.failureCount = 0;
            next.lastError = "";
            next.createdAt = now;
            next.updatedAt = now;
            next.nextCheckAt = now;
            FollowingSource source = FollowingStore.preferredSource(item.identityKey);
            if (source != null) {
                source = source.copy();
                source.followingKey = identityKey;
                source.playableSeason = next.trackedSeason;
                source.playableEpisode = 0;
                source.playableCount = 0;
                source.lastProbeAt = 0;
                source.lastError = "";
            }
            if (source == null) {
                source = new FollowingSource();
                source.followingKey = identityKey;
                source.cid = next.cid;
                source.siteKey = next.siteKey;
                source.vodId = next.vodId;
                source.vodName = next.vodName;
                source.vodPic = next.vodPic;
                source.preferred = true;
            }
            try {
                FollowingStore.saveNew(next, source);
                App.post(() -> {
                    FollowingScheduler.enqueueDueNow(this);
                    Notify.show(R.string.following_added);
                    load();
                });
            } catch (Throwable error) {
                App.post(() -> Notify.show(error.getMessage()));
            }
        });
    }

    @Override
    public void onCheck(Following item) {
        binding.loading.setVisibility(View.VISIBLE);
        Task.execute(() -> {
            boolean success = new FollowingUpdateCoordinator().checkNow(item.identityKey, true);
            App.post(() -> {
                if (binding == null) return;
                Notify.show(success ? R.string.following_check_done : R.string.following_check_failed);
                load();
            });
        });
    }

    @Override
    public void onRead(Following item) {
        if (item == null || TextUtils.isEmpty(item.identityKey)) return;
        markReadNow(List.of(item.identityKey));
    }

    @Override
    public void onToggleNotify(Following item) {
        boolean enabled = !item.notifyEnabled;
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingNotifyIdentity = item.identityKey;
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }
        if (enabled) FollowingSettings.setNotificationsEnabled(true);
        FollowingPlaybackBridge.setNotifyEnabledAsync(item.identityKey, enabled, error -> load());
    }

    @Override
    public void onChangeSource(Following item) {
        try {
            Class<?> type = Class.forName("com.fongmi.android.tv.ui.activity.SearchActivity");
            type.getMethod("start", Activity.class, String.class).invoke(null, this, item.vodName);
        } catch (Throwable error) {
            Notify.show(error.getMessage());
        }
    }

    @Override
    public void onDelete(Following item) {
        new MaterialAlertDialogBuilder(this, R.style.ThemeOverlay_WebHTV_FollowingConfirmDialog)
                .setTitle(R.string.following_delete_title)
                .setMessage(R.string.following_delete_message)
                .setNegativeButton(R.string.dialog_negative, null)
                .setPositiveButton(R.string.following_cancel, (dialog, which) -> {
                    FollowingScheduler.cancelNext(this, item.identityKey);
                    FollowingPlaybackBridge.deleteAsync(item.identityKey, error -> load());
                })
                .show();
    }

    private FollowingSource preferred(Following item, FollowingSource source) {
        if (source != null) return source;
        FollowingSource target = new FollowingSource();
        target.followingKey = item.identityKey;
        target.cid = item.cid;
        target.siteKey = item.siteKey;
        target.vodId = item.vodId;
        target.vodName = item.vodName;
        target.vodPic = item.vodPic;
        target.preferred = true;
        return target;
    }
}
