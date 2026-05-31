package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Backup;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.bean.SyncOptions;
import com.fongmi.android.tv.databinding.DialogOneKeySyncBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.SyncDeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.FileUtil;
import com.fongmi.android.tv.utils.ProgressRequestBody;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.SyncFiles;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class OneKeySyncDialog extends BaseBottomSheetDialog implements SyncDeviceAdapter.OnClickListener, ScanTask.Listener, NsdDeviceDiscovery.Listener {

    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY = 600;
    private static final MediaType ZIP = MediaType.parse("application/zip");
    private static final String TAG = "one_key_sync";

    private final OkHttpClient client;

    private DialogOneKeySyncBinding binding;
    private SyncDeviceAdapter adapter;
    private ScanTask scanTask;
    private NsdDeviceDiscovery discovery;
    private Device selected;
    private Call syncCall;
    private long progressStart;
    private long prepareStart;
    private long prepareUpdate;
    private volatile boolean syncing;
    private boolean toRemote = true;
    private boolean scanning;
    private boolean focusFirstOnNextDevice;

    public OneKeySyncDialog() {
        client = OkHttp.client(Constant.TIMEOUT_SYNC_TRANSFER);
        scanTask = new ScanTask(this);
        discovery = new NsdDeviceDiscovery(this);
    }

    public static OneKeySyncDialog create() {
        return new OneKeySyncDialog();
    }

    public void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof OneKeySyncDialog) return;
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) == null) showNow(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogOneKeySyncBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        Server.get().start();
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter = new SyncDeviceAdapter(this));
        updateDevice(binding.localName, binding.localHost, binding.localIcon, Device.get());
        updateDirection();
        getDevice();
    }

    @Override
    protected void initEvent() {
        binding.modeButton.setOnClickListener(v -> toggleMode());
        binding.refresh.setOnClickListener(v -> refresh());
        binding.changeDevice.setOnClickListener(v -> changeDevice());
        binding.selectAll.setOnClickListener(v -> toggleSelection());
        binding.spider.setOnCheckedChangeListener((buttonView, isChecked) -> updateSyncPathsVisible());
        binding.syncPaths.setOnClickListener(v -> SyncPathDialog.show(this, this::updateSyncPathSummary));
        binding.start.setOnClickListener(v -> {
            if (syncing) cancelSync();
            else startSync();
        });
    }

    private void getDevice() {
        restartScan();
        adapter.clear(() -> {
            updateVisible();
            scan();
        });
    }

    private void refresh() {
        restartScan();
        selected = null;
        binding.options.setVisibility(View.GONE);
        binding.actionBar.setVisibility(View.GONE);
        adapter.clear(() -> {
            scan();
        });
    }

    private void changeDevice() {
        restartScan();
        selected = null;
        binding.options.setVisibility(View.GONE);
        binding.actionBar.setVisibility(View.GONE);
        focusFirstOnNextDevice = true;
        updateVisible();
        focusFirstDevice();
        scan();
    }

    private void scan() {
        focusFirstOnNextDevice = true;
        setScanning(true);
        discovery.start();
        scanTask.start();
    }

    private void restartScan() {
        discovery.stop();
        scanTask.stop();
        scanTask = new ScanTask(this);
        setScanning(false);
    }

    private void toggleMode() {
        toRemote = !toRemote;
        updateDirection();
    }

    private void toggleSelection() {
        select(!allSelected());
    }

    private void select(boolean checked) {
        for (MaterialCheckBox box : boxes()) box.setChecked(checked);
        updateSelectButton();
        updateSyncPathsVisible();
    }

    private boolean allSelected() {
        for (MaterialCheckBox box : boxes()) if (!box.isChecked()) return false;
        return true;
    }

    private MaterialCheckBox[] boxes() {
        return new MaterialCheckBox[]{binding.config, binding.spider, binding.webHome, binding.search, binding.history, binding.keep, binding.settings};
    }

    private void updateSyncPathSummary() {
        binding.syncPathsSummary.setText(SyncFiles.getPathsText(SyncFiles.getPaths(Setting.getSyncPaths())));
    }

    private void updateSyncPathsVisible() {
        binding.syncPaths.setVisibility(binding.spider.isChecked() ? View.VISIBLE : View.GONE);
    }

    private SyncOptions options() {
        return SyncOptions.defaults()
                .config(binding.config.isChecked())
                .spider(binding.spider.isChecked())
                .webHome(binding.webHome.isChecked())
                .search(binding.search.isChecked())
                .history(binding.history.isChecked())
                .keep(binding.keep.isChecked())
                .settings(binding.settings.isChecked())
                .paths(Setting.getSyncPaths());
    }

    private void updateVisible() {
        if (selected != null) {
            binding.recycler.setVisibility(View.GONE);
            binding.status.setVisibility(View.GONE);
            return;
        }
        binding.recycler.setVisibility(adapter.getItemCount() > 0 ? View.VISIBLE : View.GONE);
        if (adapter.getItemCount() > 0) {
            binding.status.setVisibility(View.GONE);
        } else {
            binding.status.setVisibility(View.VISIBLE);
            binding.status.setText(scanning ? R.string.sync_discovering : R.string.sync_no_device);
        }
    }

    private void setScanning(boolean scanning) {
        this.scanning = scanning;
        updateVisible();
    }

    private void setSelected(Device device) {
        stopScan();
        selected = device;
        binding.options.setVisibility(View.VISIBLE);
        binding.actionBar.setVisibility(View.VISIBLE);
        updateDevice(binding.remoteName, binding.remoteHost, binding.remoteIcon, device);
        updateSyncPathSummary();
        updateSyncPathsVisible();
        updateDirection();
        updateSelectButton();
        updateVisible();
        binding.start.requestFocus();
    }

    private void updateDevice(TextView name, TextView host, ImageView icon, Device device) {
        name.setText(device.getName());
        host.setText(device.getHost());
        icon.setImageResource(device.isLeanback() ? R.drawable.ic_sync_tv : R.drawable.ic_sync_phone);
    }

    private void updateDirection() {
        binding.selected.setText(toRemote ? R.string.sync_mode_to_remote : R.string.sync_mode_from_remote);
        binding.mode.setText(toRemote ? R.string.sync_push : R.string.sync_pull);
        binding.mode.setCompoundDrawablesWithIntrinsicBounds(0, toRemote ? R.drawable.ic_sync_arrow_right_blue : R.drawable.ic_sync_arrow_left_blue, 0, 0);
        binding.modeButton.setText(toRemote ? R.string.sync_push : R.string.sync_pull);
        updateDirectionCard(binding.localCard, binding.localRole, toRemote);
        updateDirectionCard(binding.remoteCard, binding.remoteRole, !toRemote);
        binding.localRole.setText(toRemote ? R.string.sync_sender : R.string.sync_receiver);
        binding.remoteRole.setText(toRemote ? R.string.sync_receiver : R.string.sync_sender);
    }

    private void updateSelectButton() {
        binding.selectAll.setText(allSelected() ? R.string.sync_select_none : R.string.sync_select_all);
    }

    private void updateDirectionCard(com.google.android.material.card.MaterialCardView card, TextView role, boolean sender) {
        card.setCardBackgroundColor(Color.parseColor(sender ? "#E8F0FE" : "#F8FAFD"));
        card.setStrokeColor(Color.TRANSPARENT);
        card.setStrokeWidth(0);
        role.setTextColor(Color.parseColor(sender ? "#174EA6" : "#5F6368"));
    }

    @Override
    public void onFind(Device device) {
        if (!isRemoteApp(device)) return;
        adapter.sort(device, () -> {
            updateVisible();
            focusFirstDevice();
        });
    }

    @Override
    public void onLost(Device device) {
        adapter.remove(device, this::updateVisible);
    }

    private boolean isRemoteApp(Device device) {
        return device != null && device.isApp() && !Device.get().equals(device);
    }

    @Override
    public void onFinish() {
        setScanning(false);
    }

    @Override
    public void onServiceFound(String url) {
        scanTask.start(url);
    }

    private void stopScan() {
        discovery.stop();
        scanTask.stop();
        scanTask = new ScanTask(this);
        setScanning(false);
    }

    private void focusFirstDevice() {
        if (!focusFirstOnNextDevice || selected != null || adapter.getItemCount() == 0) return;
        focusFirstOnNextDevice = false;
        binding.recycler.scrollToPosition(0);
    }

    @Override
    public void onItemClick(Device item) {
        setSelected(item);
    }

    private void startSync() {
        if (selected == null) {
            Notify.show(R.string.sync_select_device_first);
            return;
        }
        SyncOptions options = options();
        String mode = toRemote ? "1" : "2";
        String url = String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=backup", selected.getIp(), mode);
        binding.start.setEnabled(false);
        setSyncing(true);
        updateProgress(0, toRemote ? R.string.sync_prepare_files : R.string.sync_wait_remote);
        prepareStart = System.currentTimeMillis();
        prepareUpdate = 0;
        Task.execute(() -> {
            try {
                SyncFiles.Archive archive = toRemote && options.isSpider() ? SyncFiles.createArchive(SyncFiles.getPaths(Setting.getSyncPaths()), () -> syncing, this::onPrepareProgress) : null;
                RequestBody body = buildBody(options, archive);
                if (!syncing) {
                    if (archive != null) archive.delete();
                    return;
                }
                App.post(() -> binding.start.setEnabled(true));
                request(url, body, archive, 0);
            } catch (Exception e) {
                if (!syncing) return;
                App.post(() -> {
                    setSyncing(false);
                    Notify.show(getString(R.string.sync_failed_with_reason, e.getMessage()));
                });
            }
        });
    }

    private RequestBody buildBody(SyncOptions options, SyncFiles.Archive archive) {
        if (archive == null) {
            FormBody.Builder body = new FormBody.Builder();
            body.add("options", options.toString());
            body.add("force", "false");
            if (toRemote) body.add("backup", Backup.create(options).toString());
            else body.add("device", Device.get().toString());
            return body.build();
        }
        App.post(() -> updatePrepare(archive));
        MultipartBody.Builder body = new MultipartBody.Builder().setType(MultipartBody.FORM);
        body.addFormDataPart("options", options.toString());
        body.addFormDataPart("force", "false");
        body.addFormDataPart("backup", Backup.create(options).toString());
        body.addFormDataPart(SyncFiles.PART_NAME, archive.getFile().getName(), new ProgressRequestBody(archive.getFile(), ZIP, (written, total) -> App.post(() -> updateUpload(archive, written, total))));
        return body.build();
    }

    private void request(String url, RequestBody body, SyncFiles.Archive archive, int retry) {
        syncCall = OkHttp.newCall(client, url, body);
        syncCall.enqueue(callback(url, body, archive, retry));
    }

    private void retry(String url, RequestBody body, SyncFiles.Archive archive, int retry, String msg) {
        if (!syncing) {
            if (archive != null) archive.delete();
            return;
        }
        if (retry >= MAX_RETRY) {
            if (archive != null) archive.delete();
            App.post(() -> {
                setSyncing(false);
                Notify.show(getString(R.string.sync_failed_with_reason, msg));
            });
        } else {
            Task.schedule(() -> request(url, body, archive, retry + 1), RETRY_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private Callback callback(String url, RequestBody body, SyncFiles.Archive archive, int retry) {
        return new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled() || !syncing) {
                    if (archive != null) archive.delete();
                    App.post(() -> {
                        setSyncing(false);
                        Notify.show(R.string.sync_canceled);
                    });
                    return;
                }
                retry(url, body, archive, retry, e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response res = response) {
                    if (res.isSuccessful()) App.post(() -> {
                        if (archive != null) archive.delete();
                        setSyncing(false);
                        Notify.show(R.string.sync_success);
                        dismiss();
                    });
                    else retry(url, body, archive, retry, res.message());
                }
            }
        };
    }

    private void setSyncing(boolean syncing) {
        this.syncing = syncing;
        binding.progressWrap.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.progressBox.setVisibility(syncing ? View.VISIBLE : View.GONE);
        binding.start.setText(syncing ? R.string.sync_cancel : R.string.sync_start);
        binding.start.setEnabled(true);
        if (!syncing) {
            syncCall = null;
            binding.progress.setProgress(0);
            binding.progressText.setText("");
        }
    }

    private void cancelSync() {
        syncing = false;
        if (syncCall != null) syncCall.cancel();
        else {
            setSyncing(false);
            Notify.show(R.string.sync_canceled);
        }
    }

    private void updatePrepare(SyncFiles.Archive archive) {
        if (!syncing || binding == null) return;
        progressStart = System.currentTimeMillis();
        binding.progress.setProgress(100);
        binding.progressText.setText(getString(R.string.sync_upload_prepare, archive.getCount(), FileUtil.byteCountToDisplaySize(archive.getRawSize()), FileUtil.byteCountToDisplaySize(archive.getZipSize())));
    }

    private void onPrepareProgress(int count, long size) {
        long now = System.currentTimeMillis();
        if (now - prepareUpdate < 350) return;
        prepareUpdate = now;
        long elapsed = Math.max(now - prepareStart, 1);
        long speed = size * 1000 / elapsed;
        App.post(() -> updatePrepareProgress(count, size, speed));
    }

    private void updatePrepareProgress(int count, long size, long speed) {
        if (!syncing || binding == null) return;
        binding.progress.setProgress(0);
        binding.progressText.setText(getString(R.string.sync_prepare_progress, count, FileUtil.byteCountToDisplaySize(size), FileUtil.byteCountToDisplaySize(speed)));
    }

    private void updateUpload(SyncFiles.Archive archive, long written, long total) {
        if (!syncing || binding == null) return;
        long elapsed = Math.max(System.currentTimeMillis() - progressStart, 1);
        long speed = written * 1000 / elapsed;
        int percent = total <= 0 ? 0 : (int) Math.min(100, written * 100 / total);
        binding.progress.setProgress(percent);
        binding.progressText.setText(getString(R.string.sync_upload_progress, percent, FileUtil.byteCountToDisplaySize(written), FileUtil.byteCountToDisplaySize(total), FileUtil.byteCountToDisplaySize(speed), archive.getCount()));
    }

    private void updateProgress(int progress, int text) {
        binding.progress.setProgress(progress);
        binding.progressText.setText(getString(text));
    }

    @Override
    protected boolean transparent() {
        return true;
    }

    @Override
    protected void setBehavior(BottomSheetDialog dialog) {
        super.setBehavior(dialog);
        View sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (sheet == null) return;
        int maxHeight = (int) (ResUtil.getScreenHeight(requireContext()) * 0.9f);
        sheet.getLayoutParams().height = maxHeight;
        sheet.requestLayout();
        BottomSheetBehavior.from(sheet).setPeekHeight(maxHeight);
    }

    @Override
    public void onDestroyView() {
        if (syncCall != null) syncCall.cancel();
        syncing = false;
        super.onDestroyView();
        discovery.stop();
        scanTask.stop();
    }
}
