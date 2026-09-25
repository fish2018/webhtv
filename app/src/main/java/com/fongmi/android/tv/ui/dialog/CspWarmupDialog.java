package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.CspWarmup;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class CspWarmupDialog {

    private CspWarmupDialog() {
    }

    public static void show(Fragment fragment, Runnable callback) {
        if (fragment == null || !fragment.isAdded()) return;
        show(fragment.requireContext(), fragment.getChildFragmentManager(), callback);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        show(activity, activity.getSupportFragmentManager(), callback);
    }

    private static void show(Context context, FragmentManager manager, Runnable callback) {
        if (manager == null || manager.isStateSaved()) return;
        Dialog[] holder = new Dialog[1];
        holder[0] = LightDialog.create(context, null, createPanel(context, manager, callback, holder));
        holder[0].show();
    }

    private static LinearLayout createPanel(Context context, FragmentManager manager, Runnable callback, Dialog[] holder) {
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        MaterialTextView title = new MaterialTextView(context);
        title.setText(R.string.setting_csp_warmup);
        title.setTextColor(Color.parseColor("#202124"));
        title.setTextSize(18);
        title.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        titleParams.rightMargin = dp(context, 16);
        header.addView(title, titleParams);

        MaterialButton enabled = topSwitch(context);
        enabled.setOnClickListener(view -> {
            Setting.putCspWarmup(!Setting.isCspWarmup());
            styleTopSwitch(enabled);
            run(callback);
        });
        header.addView(enabled, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(context, 36)));

        LinearLayout modes = new LinearLayout(context);
        modes.setGravity(Gravity.CENTER_VERTICAL);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modesParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        modesParams.topMargin = dp(context, 18);
        root.addView(modes, modesParams);

        MaterialButton defaultButton = modeButton(context, R.string.setting_csp_warmup_default, Setting.getCspWarmupSelectedMode() == Setting.CSP_WARMUP_DEFAULT);
        defaultButton.setOnClickListener(view -> {
            Setting.putCspWarmupMode(Setting.CSP_WARMUP_DEFAULT);
            run(callback);
            if (holder[0] != null) holder[0].dismiss();
        });
        modes.addView(defaultButton, modeParams(context, 0));

        MaterialButton customButton = modeButton(context, R.string.setting_csp_warmup_custom, Setting.getCspWarmupSelectedMode() == Setting.CSP_WARMUP_CUSTOM);
        customButton.setOnClickListener(view -> {
            if (holder[0] != null) holder[0].dismiss();
            App.post(() -> showSitePicker(context, manager, callback), 120);
        });
        modes.addView(customButton, modeParams(context, 8));

        return root;
    }

    private static void showSitePicker(Context context, FragmentManager manager, Runnable callback) {
        if (manager == null || manager.isStateSaved()) return;
        List<Site> sites = warmableSites();
        if (sites.isEmpty()) {
            Notify.show(R.string.setting_csp_warmup_no_site);
            return;
        }
        CharSequence[] labels = new CharSequence[sites.size()];
        boolean[] checked = checkedSites(sites);
        for (int i = 0; i < sites.size(); i++) labels[i] = siteLabel(sites.get(i));
        ChoiceDialog.showMulti(manager, context.getString(R.string.setting_csp_warmup_select_site), labels, checked, (which, state) -> itemEnabled(sites, which, state), result -> {
            List<String> keys = selectedKeys(sites, result);
            if (keys.isEmpty()) {
                Notify.show(R.string.setting_csp_warmup_select_required);
                return;
            }
            Setting.putCspWarmupSites(keys);
            Setting.putCspWarmupMode(Setting.CSP_WARMUP_CUSTOM);
            run(callback);
        });
    }

    private static List<Site> warmableSites() {
        List<Site> sites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) if (CspWarmup.isWarmable(site)) sites.add(site);
        return sites;
    }

    private static boolean[] checkedSites(List<Site> sites) {
        Set<String> keys = new LinkedHashSet<>(Setting.getCspWarmupSites());
        Set<String> jars = new HashSet<>();
        boolean[] checked = new boolean[sites.size()];
        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            if (!keys.contains(site.getKey())) continue;
            if (!jars.add(CspWarmup.jarKey(site))) continue;
            checked[i] = true;
        }
        return checked;
    }

    private static boolean itemEnabled(List<Site> sites, int which, boolean[] checked) {
        if (which < 0 || which >= sites.size()) return false;
        if (checked != null && which < checked.length && checked[which]) return true;
        String jar = CspWarmup.jarKey(sites.get(which));
        for (int i = 0; checked != null && i < checked.length && i < sites.size(); i++) {
            if (checked[i] && TextUtils.equals(jar, CspWarmup.jarKey(sites.get(i)))) return false;
        }
        return true;
    }

    private static List<String> selectedKeys(List<Site> sites, boolean[] checked) {
        List<String> keys = new ArrayList<>();
        Set<String> jars = new HashSet<>();
        for (int i = 0; checked != null && i < checked.length && i < sites.size(); i++) {
            Site site = sites.get(i);
            if (!checked[i] || !jars.add(CspWarmup.jarKey(site))) continue;
            keys.add(site.getKey());
        }
        return keys;
    }

    private static CharSequence siteLabel(Site site) {
        String name = site.getDisplayName();
        return TextUtils.isEmpty(site.getApi()) ? name : name + " · " + site.getApi();
    }

    private static void run(Runnable callback) {
        if (callback != null) callback.run();
    }

    private static MaterialButton topSwitch(Context context) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setMinWidth(dp(context, 84));
        button.setMinimumWidth(dp(context, 84));
        button.setMinHeight(dp(context, 36));
        button.setMinimumHeight(dp(context, 36));
        button.setPadding(dp(context, 10), 0, dp(context, 10), 0);
        button.setCornerRadius(dp(context, 6));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        styleTopSwitch(button);
        return button;
    }

    private static void styleTopSwitch(MaterialButton button) {
        Context context = button.getContext();
        boolean enabled = Setting.isCspWarmup();
        button.setText(enabled ? R.string.setting_enable : R.string.setting_disable);
        button.setAlpha(enabled ? 1.0f : 0.65f);
        button.setTextColor(ContextCompat.getColorStateList(context, R.color.dialog_tonal_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(context, R.color.dialog_tonal_button_bg));
        button.setStrokeWidth(0);
    }

    private static MaterialButton modeButton(Context context, int text, boolean selected) {
        MaterialButton button = new MaterialButton(context);
        button.setAllCaps(false);
        button.setText(text);
        button.setTextSize(15);
        button.setSingleLine(true);
        button.setGravity(Gravity.CENTER);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(context, 40));
        button.setMinimumHeight(dp(context, 40));
        button.setPadding(dp(context, 8), 0, dp(context, 8), 0);
        button.setCornerRadius(dp(context, 6));
        button.setFocusable(true);
        button.setFocusableInTouchMode(Util.isLeanback());
        styleModeButton(button, selected);
        return button;
    }

    private static void styleModeButton(MaterialButton button, boolean selected) {
        Context context = button.getContext();
        ColorStateList bg = ContextCompat.getColorStateList(context, selected ? R.color.dialog_tonal_button_bg : R.color.dialog_outlined_button_bg);
        ColorStateList fg = ContextCompat.getColorStateList(context, selected ? R.color.dialog_tonal_button_text : R.color.dialog_outlined_button_text);
        button.setBackgroundTintList(bg);
        button.setTextColor(fg);
        button.setStrokeColor(ContextCompat.getColorStateList(context, R.color.dialog_outlined_button_stroke));
        button.setStrokeWidth(selected ? 0 : dp(context, 1));
    }

    private static LinearLayout.LayoutParams modeParams(Context context, int marginStart) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(context, 40), 1);
        params.leftMargin = dp(context, marginStart);
        return params;
    }

    private static int dp(Context context, int value) {
        return ResUtil.dp2px(value);
    }
}
