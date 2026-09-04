package com.fongmi.android.tv.ui.dialog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.databinding.DialogDeviceBinding;
import com.fongmi.android.tv.server.Server;
import com.fongmi.android.tv.ui.adapter.SyncDeviceAdapter;
import com.fongmi.android.tv.ui.custom.SpaceItemDecoration;
import com.fongmi.android.tv.utils.NsdDeviceDiscovery;
import com.fongmi.android.tv.utils.ScanTask;

public class PushPlayDialog extends BaseBottomSheetDialog implements SyncDeviceAdapter.OnClickListener, ScanTask.Listener, NsdDeviceDiscovery.Listener {

    private static final String TAG = "push_play_discovery";

    private final NsdDeviceDiscovery discovery = new NsdDeviceDiscovery(this);
    private DialogDeviceBinding binding;
    private SyncDeviceAdapter adapter;
    private ScanTask scanTask = new ScanTask(this);
    private Listener listener;

    public static PushPlayDialog create() {
        return new PushPlayDialog();
    }

    public PushPlayDialog listener(Listener listener) {
        this.listener = listener;
        return this;
    }

    public void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) == null) show(activity.getSupportFragmentManager(), TAG);
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return binding = DialogDeviceBinding.inflate(inflater, container, false);
    }

    @Override
    protected void initView() {
        Server.get().start();
        binding.title.setText(R.string.push_play_select_device);
        binding.scan.setVisibility(View.GONE);
        binding.recycler.setHasFixedSize(false);
        binding.recycler.addItemDecoration(new SpaceItemDecoration(1, 8));
        binding.recycler.setAdapter(adapter = new SyncDeviceAdapter(this));
        refresh();
    }

    @Override
    protected void initEvent() {
        binding.refresh.setOnClickListener(view -> refresh());
    }

    private void refresh() {
        if (binding == null) return;
        discovery.stop();
        scanTask.stop();
        scanTask = new ScanTask(this);
        adapter.clear(() -> {
            if (binding == null) return;
            binding.recycler.setVisibility(View.GONE);
            discovery.start();
            scanTask.start();
        });
    }

    @Override
    public void onServiceFound(String url) {
        scanTask.start(url);
    }

    @Override
    public void onFind(Device device) {
        if (binding == null || device == null || !device.isApp() || Device.get().equals(device)) return;
        adapter.sort(device, () -> {
            if (binding != null) binding.recycler.setVisibility(View.VISIBLE);
        });
    }

    @Override
    public void onLost(Device device) {
        if (binding == null) return;
        adapter.remove(device, () -> binding.recycler.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE));
    }

    @Override
    public void onFinish() {
    }

    @Override
    public void onItemClick(Device device) {
        stopScan();
        Listener callback = listener;
        dismissAllowingStateLoss();
        if (callback != null) callback.onSelected(device);
    }

    private void stopScan() {
        discovery.stop();
        scanTask.stop();
    }

    @Override
    public void onDestroyView() {
        stopScan();
        binding = null;
        super.onDestroyView();
    }

    public interface Listener {

        void onSelected(Device device);
    }
}
