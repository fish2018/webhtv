package com.fongmi.android.tv.ui.dialog;

import android.text.InputFilter;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.setting.SiteNameStore;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;

import java.util.List;

public final class SiteNameDialog {

    private final FragmentActivity activity;
    private Runnable onChanged;

    public static SiteNameDialog create(FragmentActivity activity) {
        return new SiteNameDialog(activity);
    }

    private SiteNameDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public SiteNameDialog onChanged(Runnable callback) {
        this.onChanged = callback;
        return this;
    }

    public void show() {
        List<Site> sites = VodConfig.get().getSites().stream().filter(site -> site != null && !site.isEmpty()).toList();
        if (sites.isEmpty()) {
            Notify.show(R.string.site_name_empty);
            return;
        }
        CharSequence[] labels = new CharSequence[sites.size()];
        for (int i = 0; i < sites.size(); i++) labels[i] = label(sites.get(i));
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.setting_site_name)
                .setItems(labels, (dialog, which) -> showEditor(sites.get(which)))
                .setNeutralButton(R.string.setting_reset, (dialog, which) -> confirmReset())
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
    }

    private CharSequence label(Site site) {
        String custom = SiteNameStore.get(site);
        if (TextUtils.isEmpty(custom)) return site.getDisplayName() + "\n" + site.getKey();
        return site.getDisplayName() + "\n" + activity.getString(R.string.site_name_original, site.getName()) + " · " + site.getKey();
    }

    private void showEditor(Site site) {
        int padding = Math.round(24 * activity.getResources().getDisplayMetrics().density);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(padding, 0, padding, 0);

        MaterialTextView original = new MaterialTextView(activity);
        original.setText(activity.getString(R.string.site_name_original, site.getName()) + "\n" + activity.getString(R.string.site_name_key, site.getKey()));
        panel.addView(original, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextInputEditText input = new TextInputEditText(activity);
        input.setSingleLine(true);
        input.setImeOptions(EditorInfo.IME_ACTION_DONE);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(100)});
        input.setText(SiteNameStore.getEditableName(site));
        input.setSelection(input.length());

        TextInputLayout inputLayout = new TextInputLayout(activity);
        inputLayout.setHint(R.string.site_name_custom);
        inputLayout.setHelperText(activity.getString(R.string.site_name_group_hint));
        inputLayout.addView(input, new TextInputLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = Math.round(16 * activity.getResources().getDisplayMetrics().density);
        panel.addView(inputLayout, inputParams);

        new MaterialAlertDialogBuilder(activity)
                .setTitle(site.getDisplayName())
                .setView(panel)
                .setPositiveButton(R.string.dialog_positive, (dialog, which) -> {
                    SiteNameStore.put(site, input.getText() == null ? "" : input.getText().toString());
                    changed();
                    reopen();
                })
                .setNeutralButton(R.string.setting_reset, (dialog, which) -> {
                    SiteNameStore.put(site, "");
                    changed();
                    reopen();
                })
                .setNegativeButton(R.string.dialog_negative, (dialog, which) -> reopen())
                .show();
    }

    private void confirmReset() {
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.site_name_reset_title)
                .setMessage(R.string.site_name_reset_message)
                .setPositiveButton(R.string.setting_reset, (dialog, which) -> {
                    SiteNameStore.clear();
                    changed();
                })
                .setNegativeButton(R.string.dialog_negative, (dialog, which) -> reopen())
                .show();
    }

    private void changed() {
        if (onChanged != null) onChanged.run();
    }

    private void reopen() {
        App.post(this::show, 100);
    }
}
