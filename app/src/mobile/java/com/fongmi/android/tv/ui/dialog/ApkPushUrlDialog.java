package com.fongmi.android.tv.ui.dialog;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
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
import okhttp3.HttpUrl;
import okhttp3.Response;

public class ApkPushUrlDialog extends BaseAlertDialog {

    private static final String ACK = "APK URL accepted";
    private static final String TAG = "apk_push_url";

    private final okhttp3.OkHttpClient client = OkHttp.client(Constant.TIMEOUT_SYNC);
    private final Device device;
    private DialogRemoteTrustTextCommandBinding binding;
    private Call call;

    private ApkPushUrlDialog(Device device) {
        this.device = device;
    }

    public static ApkPushUrlDialog create(Device device) {
        return new ApkPushUrlDialog(device);
    }

    public void show(FragmentActivity activity) {
        if (activity.getSupportFragmentManager().isStateSaved()) return;
        if (activity.getSupportFragmentManager().findFragmentByTag(TAG) == null) show(activity.getSupportFragmentManager(), TAG);
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
        binding.title.setText(getString(R.string.apk_push_url_target, device == null ? "" : device.getName()));
        binding.inputLayout.setHint(R.string.apk_push_url_hint);
        binding.positive.setText(R.string.apk_push_url_send);
        CharSequence clip = Util.getClipText();
        if (!TextUtils.isEmpty(clip)) {
            String url = Sniffer.getUrl(clip.toString());
            if (isValid(url)) binding.input.setText(url);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        binding.input.requestFocus();
    }

    @Override
    protected void initEvent() {
        binding.negative.setOnClickListener(view -> dismiss());
        binding.positive.setOnClickListener(view -> send());
        binding.input.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            send();
            return true;
        });
    }

    private void send() {
        String url = binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
        if (TextUtils.isEmpty(url)) {
            Notify.show(R.string.apk_push_url_required);
            binding.input.requestFocus();
            return;
        }
        if (!isValid(url)) {
            Notify.show(R.string.apk_push_url_invalid);
            binding.input.requestFocus();
            return;
        }
        if (device == null || TextUtils.isEmpty(device.getIp())) {
            Notify.show(R.string.device_offline);
            dismissAllowingStateLoss();
            return;
        }
        binding.positive.setEnabled(false);
        FormBody body = new FormBody.Builder().add("device", Device.get().toString()).add("url", url).build();
        call = OkHttp.newCall(client, device.getIp().concat("/action?do=apk_url"), body);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                if (call.isCanceled()) return;
                App.post(() -> failed(e.getMessage()));
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response res = response) {
                    String value = res.body() == null ? "" : res.body().string().trim();
                    boolean successful = res.isSuccessful();
                    int code = res.code();
                    App.post(() -> complete(successful, code, value));
                } catch (IOException e) {
                    App.post(() -> failed(e.getMessage()));
                }
            }
        });
    }

    private void complete(boolean successful, int code, String body) {
        if (binding == null) return;
        if (successful && ACK.equals(body)) {
            Notify.show(R.string.apk_push_url_sent);
            dismissAllowingStateLoss();
        } else if (successful) {
            binding.positive.setEnabled(true);
            Notify.show(R.string.apk_push_url_unsupported);
        } else {
            failed(TextUtils.isEmpty(body) ? "HTTP " + code : body);
        }
    }

    private void failed(String reason) {
        if (binding == null) return;
        binding.positive.setEnabled(true);
        Notify.show(TextUtils.isEmpty(reason) ? getString(R.string.apk_push_url_failed) : getString(R.string.apk_push_url_failed_reason, reason));
    }

    private boolean isValid(String value) {
        HttpUrl url = HttpUrl.parse(value);
        return url != null && "https".equals(url.scheme()) && url.username().isEmpty() && url.password().isEmpty();
    }

    @Override
    public void onDestroyView() {
        if (call != null) call.cancel();
        binding = null;
        super.onDestroyView();
    }
}
