package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogThemeEditorBinding;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.theme.ThemeColorUtil;
import com.fongmi.android.tv.theme.ThemeProfile;
import com.fongmi.android.tv.theme.ThemeProfileStore;
import com.fongmi.android.tv.theme.ThemeProfileValidator;
import com.fongmi.android.tv.theme.ThemeResolver;
import com.fongmi.android.tv.theme.ThemeTokens;
import com.fongmi.android.tv.ui.adapter.ThemeAdapter;
import com.fongmi.android.tv.ui.custom.ThemePreviewView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Mobile theme editor. All changes stay in a draft until Apply is pressed. */
public final class ThemeEditorDialog extends BaseAlertDialog {

    private static final int[] PRESETS = {
            0xFF6750A4, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF00897B,
            0xFF43A047, 0xFF7CB342, 0xFFFB8C00, 0xFFE53935, 0xFFD81B60,
            0xFF8E24AA, 0xFF6D4C41
    };
    private static final int DEFAULT_PRESET = PRESETS[0];

    private DialogThemeEditorBinding binding;
    private ThemeProfile draft;
    private ThemeAdapter presetAdapter;

    public static void show(Fragment fragment) {
        new ThemeEditorDialog().show(fragment.getChildFragmentManager(), ThemeEditorDialog.class.getSimpleName());
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogThemeEditorBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.theme_editor_title).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        draft = ThemeProfileStore.load().copy();
        binding.themeEnabled.setChecked(Setting.isThemeColorEnabled());
        presetAdapter = new ThemeAdapter(this::selectPreset, PRESETS, selectedPrimary());
        binding.palette.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.palette.setAdapter(presetAdapter);
        binding.backgroundGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.backgroundWallpaper) draft.background.type = ThemeProfile.BACKGROUND_WALLPAPER;
            else if (checkedId == R.id.backgroundTinted) draft.background.type = ThemeProfile.BACKGROUND_TINTED_WALLPAPER;
            else if (checkedId == R.id.backgroundSolid) {
                draft.background.type = ThemeProfile.BACKGROUND_SOLID;
                ensureSolidBackgroundColor();
            }
            render();
        });
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.modeSystem) draft.mode = ThemeProfile.MODE_SYSTEM;
            else if (checkedId == R.id.modeLight) draft.mode = ThemeProfile.MODE_LIGHT;
            else if (checkedId == R.id.modeDark) draft.mode = ThemeProfile.MODE_DARK;
            render();
        });
        binding.reset.setOnClickListener(view -> {
            draft = ThemeProfile.defaultProfile();
            presetAdapter.setSelected(selectedPrimary());
            render();
        });
        binding.buttonCancel.setOnClickListener(view -> dismiss());
        binding.buttonApply.setOnClickListener(view -> apply());
        binding.buttonImport.setOnClickListener(view -> ThemeImportDialog.show(this, profile -> {
            draft = profile.copy();
            presetAdapter.setSelected(selectedPrimary());
            render();
        }));
        binding.buttonExport.setOnClickListener(view -> exportDraft());
        binding.buttonShare.setOnClickListener(view -> shareDraft());
        binding.primaryRow.setOnClickListener(view -> editColor(R.string.theme_color_primary, "primary"));
        binding.backgroundRow.setOnClickListener(view -> editColor(R.string.theme_color_background, "appBackground"));
        binding.surfaceRow.setOnClickListener(view -> editColor(R.string.theme_color_surface, "surface"));
        render();
    }

    private void render() {
        if (draft.background == null) draft.background = new ThemeProfile.Background();
        ThemeTokens tokens = ThemeResolver.resolve(draft, systemDark(), Setting.getWallColor());
        binding.preview.render(tokens);
        binding.primaryHex.setText(ThemeColorUtil.format(tokens.primary()));
        binding.backgroundHex.setText(ThemeColorUtil.format(tokens.appBackground()));
        binding.surfaceHex.setText(ThemeColorUtil.format(tokens.surface()));
        swatch(binding.primarySwatch, tokens.primary());
        swatch(binding.backgroundSwatch, tokens.appBackground());
        swatch(binding.surfaceSwatch, tokens.surface());
        binding.backgroundGroup.check(backgroundId());
        binding.modeGroup.check(modeId());
        ThemeProfileValidator.Result result = ThemeProfileValidator.validate(draft);
        binding.validation.setVisibility(result.valid() ? View.GONE : View.VISIBLE);
        binding.validation.setText(result.valid() ? "" : result.message());
        binding.buttonApply.setEnabled(result.valid());
    }

    private void selectPreset(int color) {
        if (color == DEFAULT_PRESET) draft.colorsFor(systemDark()).primary = null;
        else setHighlightColor(ThemeColorUtil.format(color));
        presetAdapter.setSelected(color);
        render();
    }

    private void setHighlightColor(String color) {
        draft.colorsFor(systemDark()).primary = color;
    }

    private void editColor(int label, String token) {
        ThemeProfile.ColorSet colors = draft.colorsFor(systemDark());
        String current = value(token, colors);
        ThemeColorPickerDialog.show(this, getString(label), current, color -> {
            ThemeProfile.ColorSet target = draft.colorsFor(systemDark());
            if ("primary".equals(token)) setHighlightColor(color);
            else if ("appBackground".equals(token)) {
                target.appBackground = color;
                if (ThemeProfile.BACKGROUND_SOLID.equals(draft.background.type)) draft.background.color = color;
            } else if ("surface".equals(token)) target.surface = color;
            render();
        });
    }

    private void exportDraft() {
        ThemeProfileValidator.Result checked = ThemeProfileValidator.validate(draft);
        if (!checked.valid()) {
            Toast.makeText(requireContext(), checked.message(), Toast.LENGTH_LONG).show();
            return;
        }
        exportLauncher.launch(ThemeExport.createDocumentIntent(checked.profile()));
    }

    private void shareDraft() {
        ThemeProfileValidator.Result checked = ThemeProfileValidator.validate(draft);
        if (!checked.valid()) {
            Toast.makeText(requireContext(), checked.message(), Toast.LENGTH_LONG).show();
            return;
        }
        try {
            startActivity(ThemeExport.shareIntent(checked.profile()));
        } catch (RuntimeException e) {
            Toast.makeText(requireContext(), R.string.theme_export_unavailable, Toast.LENGTH_LONG).show();
        }
    }

    private void apply() {
        ThemeProfileStore.ApplyResult result = ThemeProfileStore.apply(draft);
        if (!result.success()) {
            Toast.makeText(requireContext(), getString(R.string.theme_apply_failed, result.error()), Toast.LENGTH_LONG).show();
            return;
        }
        Setting.putThemeColorEnabled(binding.themeEnabled.isChecked());
        RefreshEvent.theme();
        dismiss();
    }

    private void ensureSolidBackgroundColor() {
        if (draft.background.color != null) return;
        ThemeProfile.ColorSet colors = draft.colorsFor(systemDark());
        draft.background.color = colors.appBackground == null
                ? (systemDark() ? "#111827" : "#F8FAFC")
                : colors.appBackground;
    }

    private String value(String token, ThemeProfile.ColorSet colors) {
        if ("primary".equals(token)) return colors.primary == null ? ThemeColorUtil.format(selectedPrimary()) : colors.primary;
        if ("appBackground".equals(token)) return colors.appBackground == null ? "#F8FAFC" : colors.appBackground;
        return colors.surface == null ? "#FFFFFF" : colors.surface;
    }

    private int selectedPrimary() {
        ThemeProfile.ColorSet colors = draft == null ? null : draft.colorsFor(systemDark());
        return ThemeColorUtil.parse(colors == null ? null : colors.primary, 0xFF6750A4);
    }

    private int backgroundId() {
        if (ThemeProfile.BACKGROUND_SOLID.equals(draft.background.type)) return R.id.backgroundSolid;
        if (ThemeProfile.BACKGROUND_TINTED_WALLPAPER.equals(draft.background.type)) return R.id.backgroundTinted;
        return R.id.backgroundWallpaper;
    }

    private int modeId() {
        if (ThemeProfile.MODE_LIGHT.equals(draft.mode)) return R.id.modeLight;
        if (ThemeProfile.MODE_DARK.equals(draft.mode)) return R.id.modeDark;
        return R.id.modeSystem;
    }

    private boolean systemDark() {
        int mode = requireContext().getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return mode == Configuration.UI_MODE_NIGHT_YES;
    }

    private final ActivityResultLauncher<Intent> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null) return;
                Uri uri = result.getData().getData();
                if (uri == null) return;
                try {
                    ThemeExport.write(requireActivity(), uri, draft);
                    Toast.makeText(requireContext(), R.string.theme_exported, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(requireContext(), R.string.theme_export_failed, Toast.LENGTH_LONG).show();
                }
            });

    private void swatch(View view, int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        drawable.setStroke(Math.max(1, Math.round(getResources().getDisplayMetrics().density)), 0x55000000);
        view.setBackground(drawable);
    }
}
