package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.event.RefreshEvent;
import com.fongmi.android.tv.setting.PlayerSetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

public final class AppearanceDialog extends DialogFragment implements ThemeDialog.Listener {

    private String[] uiScales;
    private String[] languages;
    private String[] imageSizes;
    private MaterialTextView uiScaleValue;
    private MaterialTextView themeValue;
    private MaterialTextView imageSizeValue;
    private MaterialTextView languageValue;

    public static void show(Fragment fragment) {
        new AppearanceDialog().show(fragment.getChildFragmentManager(), AppearanceDialog.class.getSimpleName());
    }

    public static void show(FragmentActivity activity) {
        new AppearanceDialog().show(activity.getSupportFragmentManager(), AppearanceDialog.class.getSimpleName());
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        uiScales = ResUtil.getStringArray(R.array.select_ui_scale);
        languages = ResUtil.getStringArray(R.array.select_language);
        imageSizes = ResUtil.getStringArray(R.array.select_size);
        return LightDialog.create(requireContext(), getString(R.string.setting_appearance), createContent(), getString(R.string.dialog_close), null, null, null);
    }

    private View createContent() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        uiScaleValue = addRow(content, R.string.setting_ui_scale, uiScales[Setting.getUiScaleIndex()], this::chooseUiScale);
        themeValue = addRow(content, R.string.setting_theme_color, getThemeText(), view -> ThemeDialog.show(this));
        imageSizeValue = addRow(content, R.string.setting_size, imageSizes[PlayerSetting.getSize()], this::chooseImageSize);
        languageValue = addRow(content, R.string.setting_language, languages[Setting.getLanguageIndex()], this::chooseLanguage);
        return content;
    }

    private MaterialTextView addRow(LinearLayout content, int titleRes, String value, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackgroundResource(R.drawable.selector_git_cloud_card);
        row.setClickable(true);
        row.setFocusable(true);
        row.setPadding(dp(16), 0, dp(16), 0);
        row.setOnClickListener(listener);

        MaterialTextView title = new MaterialTextView(requireContext());
        title.setText(titleRes);
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(15);
        title.setSingleLine(true);
        row.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        MaterialTextView summary = new MaterialTextView(requireContext());
        summary.setText(value);
        summary.setTextColor(Color.parseColor("#5F6368"));
        summary.setTextSize(14);
        summary.setGravity(Gravity.END);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        summaryParams.leftMargin = dp(12);
        row.addView(summary, summaryParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58));
        rowParams.bottomMargin = dp(8);
        content.addView(row, rowParams);
        return summary;
    }

    private void chooseUiScale(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_ui_scale, uiScales, Setting.getUiScaleIndex(), which -> {
            if (which == Setting.getUiScaleIndex()) return;
            uiScaleValue.setText(uiScales[which]);
            Setting.putUiScaleIndex(which);
            dismissAllowingStateLoss();
            requireActivity().recreate();
        });
    }

    private void chooseImageSize(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_size, imageSizes, PlayerSetting.getSize(), which -> {
            imageSizeValue.setText(imageSizes[which]);
            PlayerSetting.putSize(which);
            RefreshEvent.size();
        });
    }

    private void chooseLanguage(View view) {
        ChoiceDialog.showSingle(this, R.string.setting_language, languages, Setting.getLanguageIndex(), which -> {
            if (which == Setting.getLanguageIndex()) return;
            languageValue.setText(languages[which]);
            Setting.putLanguageIndex(which);
            dismissAllowingStateLoss();
            RefreshEvent.language();
        });
    }

    @Override
    public void setTheme(int color) {
        themeValue.setText(themeText(color));
        Setting.putThemeColor(color);
        dismissAllowingStateLoss();
        RefreshEvent.theme();
    }

    private String getThemeText() {
        return themeText(Setting.getThemeColor());
    }

    private String themeText(int color) {
        if (color == -1) return getString(R.string.setting_off);
        return getString(color == 0 ? R.string.setting_auto : R.string.setting_custom);
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }
}
