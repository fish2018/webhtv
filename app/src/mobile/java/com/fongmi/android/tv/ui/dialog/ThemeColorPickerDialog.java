package com.fongmi.android.tv.ui.dialog;

import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogThemeColorPickerBinding;
import com.fongmi.android.tv.theme.ThemeColorUtil;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/** Edits one explicit theme color without touching persisted preferences. */
public final class ThemeColorPickerDialog extends BaseAlertDialog {

    private static final String ARG_LABEL = "label";
    private static final String ARG_COLOR = "color";
    private static final int[] PRESETS = {
            0xFF6750A4, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF00897B,
            0xFF43A047, 0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF6D4C41,
            0xFF111827, 0xFFF8FAFC, 0xFFFFFFFF
    };
    private DialogThemeColorPickerBinding binding;
    private OnColorSelectedListener listener;

    public static void show(Fragment fragment, String label, String color, OnColorSelectedListener listener) {
        ThemeColorPickerDialog dialog = new ThemeColorPickerDialog();
        dialog.listener = listener;
        dialog.setArguments(new android.os.Bundle());
        dialog.requireArguments().putString(ARG_LABEL, label);
        dialog.requireArguments().putString(ARG_COLOR, color);
        dialog.show(fragment.getChildFragmentManager(), ThemeColorPickerDialog.class.getSimpleName());
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogThemeColorPickerBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        String label = requireArguments().getString(ARG_LABEL, getString(R.string.theme_color_edit));
        return builder().setTitle(label).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.input.setText(requireArguments().getString(ARG_COLOR, "#6750A4"));
        binding.buttonCancel.setOnClickListener(view -> dismiss());
        binding.buttonApply.setOnClickListener(view -> apply());
        binding.getRoot().addView(createPresetPalette(), 1);
        binding.input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                render(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        render(binding.input.getText() == null ? "" : binding.input.getText().toString());
    }

    private View createPresetPalette() {
        float density = getResources().getDisplayMetrics().density;
        int size = Math.round(44 * density);
        int margin = Math.round(4 * density);
        LinearLayout colors = new LinearLayout(requireContext());
        colors.setOrientation(LinearLayout.HORIZONTAL);
        for (int color : PRESETS) {
            View swatch = new View(requireContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMargins(margin, margin, margin, margin);
            swatch.setLayoutParams(params);
            swatch.setContentDescription(ThemeColorUtil.format(color));
            swatch.setClickable(true);
            swatch.setFocusable(true);
            GradientDrawable background = new GradientDrawable();
            background.setShape(GradientDrawable.OVAL);
            background.setColor(color);
            background.setStroke(Math.max(1, Math.round(density)), 0x55000000);
            swatch.setBackground(background);
            swatch.setOnClickListener(view -> binding.input.setText(ThemeColorUtil.format(color)));
            colors.addView(swatch);
        }
        HorizontalScrollView palette = new HorizontalScrollView(requireContext());
        palette.setHorizontalScrollBarEnabled(false);
        palette.addView(colors);
        return palette;
    }

    private void render(String raw) {
        String normalized = ThemeColorUtil.normalize(raw);
        boolean valid = normalized != null;
        binding.inputLayout.setError(valid ? null : getString(R.string.theme_color_invalid));
        binding.buttonApply.setEnabled(valid);
        if (!valid) {
            binding.contrast.setText(R.string.theme_color_contrast_unknown);
            return;
        }
        int color = ThemeColorUtil.parse(normalized, ThemeColorUtil.BLACK);
        int onColor = ThemeColorUtil.readableOn(color);
        double contrast = ThemeColorUtil.contrast(onColor, color);
        binding.swatch.setBackgroundColor(color);
        binding.contrast.setText(getString(R.string.theme_color_contrast, ThemeColorUtil.format(onColor), contrast));
    }

    private void apply() {
        String normalized = ThemeColorUtil.normalize(String.valueOf(binding.input.getText()));
        if (normalized == null) return;
        if (listener != null) listener.onColorSelected(normalized);
        dismiss();
    }

    public interface OnColorSelectedListener {

        void onColorSelected(String color);
    }
}
