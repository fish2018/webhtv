package com.fongmi.android.tv.ui.dialog;

import androidx.fragment.app.Fragment;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.DialogThemeBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.ThemeAdapter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class ThemeDialog extends BaseAlertDialog implements ThemeAdapter.OnClickListener {

    private static final int[] COLORS = {-1, 0, 0xFF6750A4, 0xFF3949AB, 0xFF1E88E5, 0xFF00ACC1, 0xFF00897B, 0xFF43A047, 0xFF7CB342, 0xFFFB8C00, 0xFFE53935, 0xFFD81B60, 0xFF8E24AA, 0xFF6D4C41,};
    private DialogThemeBinding binding;

    public static void show(Fragment fragment) {
        new ThemeDialog().show(fragment.getChildFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogThemeBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return builder().setTitle(R.string.setting_theme_color).setView(getBinding().getRoot());
    }

    @Override
    protected void initView() {
        binding.recycler.setAdapter(new ThemeAdapter(this, COLORS, Setting.getThemeColor()));
    }

    @Override
    public void onItemClick(int color) {
        ((Listener) requireParentFragment()).setTheme(color);
        dismiss();
    }

    public interface Listener {

        void setTheme(int color);
    }
}
