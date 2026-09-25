package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogThemeImportBinding;
import com.fongmi.android.tv.theme.ThemeProfile;
import com.fongmi.android.tv.theme.ThemeProfileValidator;
import com.fongmi.android.tv.theme.ThemeTweakCnAdapter;
import com.fongmi.android.tv.theme.ThemeTransfer;
import com.fongmi.android.tv.utils.Task;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.io.InputStream;
import java.util.List;

/** Imports a theme into an editor draft; no preference is changed until the editor is applied. */
public final class ThemeImportDialog extends BaseAlertDialog {

    private DialogThemeImportBinding binding;
    private Listener listener;
    private ThemeProfile pending;
    private List<String> warnings = List.of();

    public static void show(Fragment fragment, Listener listener) {
        ThemeImportDialog dialog = new ThemeImportDialog();
        dialog.listener = listener;
        dialog.show(fragment.getChildFragmentManager(), ThemeImportDialog.class.getSimpleName());
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogThemeImportBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.theme_import_title).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.buttonOpenFile.setOnClickListener(view -> openFileLauncher.launch(new String[]{"application/json", "text/plain", "*/*"}));
        binding.buttonCommunity.setOnClickListener(view -> openCommunity());
        binding.buttonPreview.setOnClickListener(view -> previewInput());
        binding.buttonUse.setOnClickListener(view -> usePending());
        binding.buttonCancel.setOnClickListener(view -> dismiss());
        binding.buttonUse.setEnabled(false);
    }

    private void previewInput() {
        String value = binding.input.getText() == null ? "" : binding.input.getText().toString().trim();
        if (value.isEmpty()) {
            showError(getString(R.string.theme_import_empty));
        } else if (ThemeTransfer.isHttps(value)) {
            fetch(value);
        } else {
            inspect(value, getString(R.string.theme_import_source_paste));
        }
    }

    private void fetch(String url) {
        setBusy(true);
        Task.execute(() -> {
            try {
                String json = ThemeTransfer.fetch(url);
                App.post(() -> {
                    if (!isAdded() || binding == null) return;
                    setBusy(false);
                    inspect(json, ThemeTransfer.host(url));
                });
            } catch (Exception e) {
                App.post(() -> {
                    if (!isAdded() || binding == null) return;
                    setBusy(false);
                    showError(e.getMessage() == null ? getString(R.string.theme_import_failed) : e.getMessage());
                });
            }
        });
    }

    private void inspect(String json, String source) {
        try {
            ThemeTweakCnAdapter.Result result = ThemeTweakCnAdapter.parse(json);
            ThemeProfileValidator.Result checked = ThemeProfileValidator.validate(result.profile());
            if (!checked.valid()) throw new IllegalArgumentException(checked.message());
            pending = checked.profile();
            warnings = result.warnings();
            String label = TextUtils.isEmpty(source) ? pending.displayName() : pending.displayName() + " · " + source;
            binding.preview.setText(getString(R.string.theme_import_preview, label));
            binding.warning.setText(warnings.isEmpty() ? getString(R.string.theme_import_no_warnings)
                    : getString(R.string.theme_import_warnings, String.join("\n", warnings)));
            binding.warning.setVisibility(warnings.isEmpty() ? View.GONE : View.VISIBLE);
            binding.error.setVisibility(View.GONE);
            binding.buttonUse.setEnabled(true);
        } catch (RuntimeException e) {
            pending = null;
            warnings = List.of();
            binding.buttonUse.setEnabled(false);
            binding.preview.setText("");
            binding.warning.setVisibility(View.GONE);
            showError(e.getMessage() == null ? getString(R.string.theme_import_failed) : e.getMessage());
        }
    }

    private void usePending() {
        if (pending == null || listener == null) return;
        listener.onImported(pending.copy());
        dismiss();
    }

    private void openCommunity() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://tweakcn.com/community")));
        } catch (RuntimeException e) {
            showError(getString(R.string.theme_import_browser_unavailable));
        }
    }

    private void setBusy(boolean busy) {
        binding.buttonPreview.setEnabled(!busy);
        binding.buttonOpenFile.setEnabled(!busy);
        binding.buttonUse.setEnabled(!busy && pending != null);
        binding.error.setVisibility(View.GONE);
    }

    private void showError(String message) {
        binding.error.setText(message);
        binding.error.setVisibility(View.VISIBLE);
    }

    private final ActivityResultLauncher<String[]> openFileLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::readFile);

    private void readFile(Uri uri) {
        if (uri == null) return;
        setBusy(true);
        Task.execute(() -> {
            try (InputStream input = requireContext().getContentResolver().openInputStream(uri)) {
                String json = ThemeTransfer.read(input);
                App.post(() -> {
                    if (!isAdded() || binding == null) return;
                    setBusy(false);
                    binding.input.setText(json);
                    inspect(json, getString(R.string.theme_import_source_file));
                });
            } catch (Exception e) {
                App.post(() -> {
                    if (!isAdded() || binding == null) return;
                    setBusy(false);
                    showError(e.getMessage() == null ? getString(R.string.theme_import_failed) : e.getMessage());
                });
            }
        });
    }

    public interface Listener {

        void onImported(@NonNull ThemeProfile profile);
    }
}
