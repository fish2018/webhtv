package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.api.config.WallConfig;
import com.fongmi.android.tv.bean.Config;
import com.fongmi.android.tv.db.AppDatabase;
import com.fongmi.android.tv.databinding.DialogConfigBinding;
import com.fongmi.android.tv.impl.ConfigListener;
import com.fongmi.android.tv.playback.PlaybackIdentityResolver;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

public class ConfigDialog extends BaseAlertDialog {

    private DialogConfigBinding binding;
    private Config config;
    private boolean append = true;
    private boolean edit;
    private ConfigListener listener;
    private String ori;
    private int type;

    public static ConfigDialog create() {
        return new ConfigDialog();
    }

    public ConfigDialog vod() {
        type = 0;
        return this;
    }

    public ConfigDialog live() {
        type = 1;
        return this;
    }

    public ConfigDialog wall() {
        type = 2;
        return this;
    }

    public ConfigDialog edit() {
        edit = true;
        return this;
    }

    public ConfigDialog target(Config config) {
        this.config = config;
        return this;
    }

    public void show(Fragment fragment) {
        listener = fragment instanceof ConfigListener ? (ConfigListener) fragment : null;
        show(fragment.getChildFragmentManager(), null);
    }

    public void show(FragmentActivity activity) {
        listener = activity instanceof ConfigListener ? (ConfigListener) activity : null;
        show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogConfigBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        if (config == null) config = edit ? getConfig() : Config.create(type);
        binding.title.setText(getDialogTitle());
        binding.positive.setText(edit ? R.string.dialog_edit : R.string.dialog_positive);
        binding.name.setText(config.getName());
        binding.url.setText(ori = config.getUrl());
        binding.url.setSelection(TextUtils.isEmpty(ori) ? 0 : ori.length());
        binding.addresses.setText(addressesText(config));
    }

    @Override
    protected void initEvent() {
        binding.negative.setOnClickListener(v -> dismiss());
        binding.positive.setOnClickListener(v -> onPositive());
        binding.choose.setEndIconOnClickListener(this::onChoose);
        // 猫源本地包是一整个文件夹（index.js + index.config.js），文件选择器选不到目录，
        // 所以单独给一个入口。选 zip 仍走上面那个文件选择。
        binding.choose.setStartIconVisible(type == 0);
        if (type == 0) binding.choose.setStartIconOnClickListener(this::onChooseDir);
        binding.url.addTextChangedListener(new CustomTextListener() {
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                detect(s.toString());
            }
        });
        binding.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
        binding.name.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        configureWindow();
        binding.url.requestFocus();
    }

    private Config getConfig() {
        return switch (type) {
            case 0 -> VodConfig.get().getConfig();
            case 1 -> LiveConfig.get().getConfig();
            case 2 -> WallConfig.get().getConfig();
            default -> null;
        };
    }

    private Config getStoredConfig() {
        return switch (type) {
            case 0 -> Config.vod();
            case 1 -> Config.live();
            case 2 -> Config.wall();
            default -> Config.create(type);
        };
    }

    private int getTypeName() {
        return switch (type) {
            case 0 -> R.string.setting_vod;
            case 1 -> R.string.setting_live;
            case 2 -> R.string.setting_wall;
            default -> R.string.remote_trust_config_type;
        };
    }

    private String getDialogTitle() {
        int action = edit ? R.string.remote_trust_config_edit : R.string.remote_trust_config_add;
        return getString(R.string.setting_config_dialog_title, getString(action), getString(getTypeName()));
    }

    private void onChoose(View view) {
        FileChooser.from(launcher).show();
    }

    private void onChooseDir(View view) {
        FileChooser.from(launcher).showDirectory();
    }

    private void detect(String s) {
        if (append && "h".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ttp://");
        } else if (append && "f".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ile://");
        } else if (append && "a".equalsIgnoreCase(s)) {
            append = false;
            binding.url.append("ssets://");
        } else if (s.length() > 1) {
            append = false;
        } else if (s.isEmpty()) {
            append = true;
        }
    }

    private void onPositive() {
        String url = binding.url.getText().toString().trim();
        String name = binding.name.getText().toString().trim();
        Config config = saveConfig(url, name, binding.addresses.getText().toString());
        if (config == null) {
            Notify.show(R.string.remote_trust_config_url_required);
            binding.url.requestFocus();
            return;
        }
        ConfigListener target = listener;
        if (target == null && getParentFragment() instanceof ConfigListener) target = (ConfigListener) getParentFragment();
        if (target == null && requireActivity() instanceof ConfigListener) target = (ConfigListener) requireActivity();
        if (target != null) target.setConfig(config);
        PlaybackIdentityResolver.resolveSaved(config);
        dismiss();
    }

    private Config saveConfig(String url, String name, String addresses) {
        Config saved;
        if (url.isEmpty()) {
            if (!edit) return null;
            if (!TextUtils.isEmpty(ori)) Config.delete(ori, type);
            return getStoredConfig();
        } else if (edit) {
            saved = Config.find(config.getId()).url(url).name(name).update();
        } else {
            Config exists = AppDatabase.get().getConfigDao().find(url, type);
            saved = exists != null ? exists.name(name).update() : null;
            if (saved == null) {
                return config = Config.create(type).url(url).name(name)
                        .urls(addresses(url, addresses)).insert().update();
            }
        }
        return config = saved.urls(addresses(url, addresses)).update();
    }

    private List<String> addresses(String primary, String text) {
        List<String> result = new ArrayList<>();
        result.add(primary);
        for (String item : text.split("\\R")) if (!TextUtils.isEmpty(item.trim())) result.add(item.trim());
        return result;
    }

    private String addressesText(Config config) {
        List<String> addresses = new ArrayList<>(config.getUrls());
        addresses.remove(config.getUrl());
        return TextUtils.join("\n", addresses);
    }

    private void configureWindow() {
        if (getDialog() == null || getDialog().getWindow() == null) return;
        Window window = getDialog().getWindow();
        WindowManager.LayoutParams params = window.getAttributes();
        boolean land = ResUtil.isLand(requireContext());
        int width = Math.min(Math.round(ResUtil.getScreenWidth(requireContext()) * (land ? 0.58f : 0.92f)), ResUtil.dp2px(560));
        params.width = Math.max(width, ResUtil.dp2px(320));
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.CENTER;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        window.setAttributes(params);
        window.setLayout(params.width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
        String name = binding.name.getText().toString().trim();
        String path = FileChooser.getPersistentPathFromUri(result.getData().getData());
        if (TextUtils.isEmpty(path)) {
            Notify.show(R.string.dialog_config_choose_failed);
            return;
        }
        String url = "file:/" + path.replace(Path.rootPath(), "");
        ((ConfigListener) requireParentFragment()).setConfig(saveConfig(url, name, binding.addresses.getText().toString()));
        dismiss();
    });
}
