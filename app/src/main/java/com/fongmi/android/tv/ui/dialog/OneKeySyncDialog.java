package com.fongmi.android.tv.ui.dialog;

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
import com.fongmi.android.tv.ui.adapter.SyncDeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ScanTask;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.net.OkHttp;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.OkHttpClient;
import okhttp3.Response;

public class OneKeySyncDialog extends BaseBottomSheetDialog implements SyncDeviceAdapter.OnClickListener, ScanTask.Listener, NsdDeviceDiscovery.Listener {

    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY = 600;

    private final OkHttpClient client;

    private DialogOneKeySyncBinding binding;
    private SyncDeviceAdapter adapter;
    private ScanTask scanTask;
    private NsdDeviceDiscovery discovery;
    private Device selected;
    private boolean toRemote = true;
    private boolean scanning;

    public OneKeySyncDialog() {
        client = OkHttp.client(Constant.TIMEOUT_SYNC_TRANSFER);
        scanTask = new ScanTask(this);
        discovery = new NsdDeviceDiscovery(this);
    }

    public static OneKeySyncDialog create() {
        return new OneKeySyncDialog();
    }

    public void show(FragmentActivity activity) {
        for (Fragment f : activity.getSupportFragmentManager().getFragments()) if (f instanceof OneKeySyncDialog) return;
        show(activity.getSupportFragmentManager(), null);
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
        binding.mode.setOnClickListener(v -> toggleMode());
        binding.refresh.setOnClickListener(v -> refresh());
        binding.changeDevice.setOnClickListener(v -> changeDevice());
        binding.selectAll.setOnClickListener(v -> select(true));
        binding.selectNone.setOnClickListener(v -> select(false));
        binding.start.setOnClickListener(v -> startSync());
    }

    private void getDevice() {
        adapter.clear(() -> {
            updateVisible();
            scan();
        });
    }

    private void refresh() {
        if (scanning) return;
        selected = null;
        binding.options.setVisibility(View.GONE);
        adapter.clear(() -> {
            scan();
        });
    }

    private void changeDevice() {
        selected = null;
        binding.options.setVisibility(View.GONE);
        updateVisible();
        if (!scanning) scan();
    }

    private void scan() {
        setScanning(true);
        discovery.start();
        scanTask.start();
    }

    private void toggleMode() {
        toRemote = !toRemote;
        updateDirection();
    }

    private void select(boolean checked) {
        for (MaterialCheckBox box : boxes()) box.setChecked(checked);
    }

    private MaterialCheckBox[] boxes() {
        return new MaterialCheckBox[]{binding.config, binding.spider, binding.webHome, binding.search, binding.history, binding.keep, binding.settings};
    }

    private SyncOptions options() {
        return SyncOptions.defaults()
                .config(binding.config.isChecked())
                .spider(binding.spider.isChecked())
                .webHome(binding.webHome.isChecked())
                .search(binding.search.isChecked())
                .history(binding.history.isChecked())
                .keep(binding.keep.isChecked())
                .settings(binding.settings.isChecked());
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
        selected = device;
        binding.options.setVisibility(View.VISIBLE);
        updateDevice(binding.remoteName, binding.remoteHost, binding.remoteIcon, device);
        updateDirection();
        updateVisible();
    }

    private void updateDevice(TextView name, TextView host, ImageView icon, Device device) {
        name.setText(device.getName());
        host.setText(device.getHost());
        icon.setImageResource(device.isLeanback() ? R.drawable.ic_sync_tv : R.drawable.ic_sync_phone);
    }

    private void updateDirection() {
        binding.selected.setText(toRemote ? R.string.sync_mode_to_remote : R.string.sync_mode_from_remote);
        binding.mode.setIconResource(toRemote ? R.drawable.ic_sync_arrow_right : R.drawable.ic_sync_arrow_left);
        binding.mode.setContentDescription(getString(R.string.sync_direction_change));
    }

    @Override
    public void onFind(Device device) {
        if (!isRemoteApp(device)) return;
        adapter.sort(device, this::updateVisible);
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
        FormBody.Builder body = new FormBody.Builder();
        body.add("options", options.toString());
        body.add("force", "false");
        if (toRemote) body.add("backup", Backup.create(options).toString());
        else body.add("device", Device.get().toString());
        String mode = toRemote ? "1" : "2";
        String url = String.format(Locale.getDefault(), "%s/action?do=sync&mode=%s&type=backup", selected.getIp(), mode);
        binding.start.setEnabled(false);
        Notify.progress(requireActivity());
        request(url, body.build(), 0);
    }

    private void request(String url, FormBody body, int retry) {
        OkHttp.newCall(client, url, body).enqueue(callback(url, body, retry));
    }

    private void retry(String url, FormBody body, int retry, String msg) {
        if (retry >= MAX_RETRY) {
            App.post(() -> {
                Notify.dismiss();
                binding.start.setEnabled(true);
                Notify.show(getString(R.string.sync_failed_with_reason, msg));
            });
        } else {
            Task.schedule(() -> request(url, body, retry + 1), RETRY_DELAY, TimeUnit.MILLISECONDS);
        }
    }

    private Callback callback(String url, FormBody body, int retry) {
        return new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                retry(url, body, retry, e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response res = response) {
                    if (res.isSuccessful()) App.post(() -> {
                        Notify.dismiss();
                        Notify.show(R.string.sync_success);
                        dismiss();
                    });
                    else retry(url, body, retry, res.message());
                }
            }
        };
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        discovery.stop();
        scanTask.stop();
    }
}
