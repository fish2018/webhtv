package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.Constant;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Device;
import com.fongmi.android.tv.databinding.DialogRemoteTrustTextCommandBinding;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.Sniffer;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Response;

public class PushPlayUrlDialog extends BaseAlertDialog {

    private final Device device;
    private final okhttp3.OkHttpClient client = OkHttp.client(Constant.TIMEOUT_SYNC);
    private DialogRemoteTrustTextCommandBinding binding;
    private Call call;

    private PushPlayUrlDialog(Device device) {
        this.device = device;
    }

    public static PushPlayUrlDialog create(Device device) {
        return new PushPlayUrlDialog(device);
    }

    public void show(androidx.fragment.app.FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        if (activity.getSupportFragmentManager().findFragmentByTag("push_play_url") == null) show(activity.getSupportFragmentManager(), "push_play_url");
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogRemoteTrustTextCommandBinding.inflate(LayoutInflater.from(requireActivity()));
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.title.setText(getString(R.string.push_play_target, device == null ? "" : device.getName()));
        binding.inputLayout.setHint(R.string.remote_trust_push_url);
        binding.positive.setText(R.string.remote_trust_send_push);
        CharSequence clip = Util.getClipText();
        if (!TextUtils.isEmpty(clip)) binding.input.setText(Sniffer.getUrl(clip.toString()));
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.input.requestFocus();
    }

    @Override
    protected void initEvent() {
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> send());
        binding.input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                send();
                return true;
            }
            return false;
        });
    }

    private void send() {
        String url = binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Notify.show(R.string.push_play_url_required);
            binding.input.requestFocus();
            return;
        }
        if (device == null || TextUtils.isEmpty(device.getIp())) {
            Notify.show(R.string.device_offline);
            dismissAllowingStateLoss();
            return;
        }
        binding.positive.setEnabled(false);
        call = OkHttp.newCall(client, device.getIp().concat("/action?do=push"), new FormBody.Builder().add("url", url).build());
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                App.post(() -> failed(e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response res = response) {
                    String body = res.body() == null ? "" : res.body().string().trim();
                    App.post(() -> {
                        if (res.isSuccessful() && "OK".equals(body)) {
                            Notify.show(R.string.push_play_success);
                            dismissAllowingStateLoss();
                        } else failed(res.isSuccessful() ? null : "HTTP " + res.code());
                    });
                } catch (IOException e) {
                    App.post(() -> failed(e.getMessage()));
                }
            }
        });
    }

    private void failed(String reason) {
        if (binding == null) return;
        binding.positive.setEnabled(true);
        Notify.show(TextUtils.isEmpty(reason) ? getString(R.string.push_play_failed) : getString(R.string.push_play_failed_reason, reason));
    }

    @Override
    public void onDestroyView() {
        if (call != null) call.cancel();
        binding = null;
        super.onDestroyView();
    }
}
