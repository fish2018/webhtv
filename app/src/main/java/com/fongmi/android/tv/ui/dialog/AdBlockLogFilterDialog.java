package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.content.res.Configuration;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AdBlockLog;
import com.fongmi.android.tv.bean.AdBlockLogFilter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 日志列的去重多选筛选器。
 * 候选项只出现一次，弹窗内的输入框只过滤候选项，不会破坏已勾选状态。
 */
public final class AdBlockLogFilterDialog {

    public interface ValueProvider {
        String value(AdBlockLog log);
    }

    public interface OnApplyListener {
        void onApply(Set<String> values);
    }

    private AdBlockLogFilterDialog() {
    }

    public static void show(Activity activity, String title, List<AdBlockLog> logs,
                            Set<String> selectedValues,
                            ValueProvider valueProvider,
                            OnApplyListener listener) {
        if (activity == null || activity.isFinishing()) return;
        List<Option> options = buildOptions(activity, logs, valueProvider);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(activity, 4), dp(activity, 4), dp(activity, 4), 0);

        EditText search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint(R.string.ad_log_filter_search_hint);
        search.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        search.setBackgroundResource(R.drawable.selector_dialog_input);
        search.setTextColor(activity.getResources().getColor(R.color.dialog_outlined_button_text));
        search.setHintTextColor(0xFF80868B);
        search.setPadding(dp(activity, 10), 0, dp(activity, 10), 0);
        root.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 48)));

        LinearLayout actionRow = new LinearLayout(activity);
        actionRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(activity, 8);
        root.addView(actionRow, actionParams);

        MaterialButton selectAll = actionButton(activity, R.string.ad_log_filter_all);
        MaterialButton clear = actionButton(activity, R.string.ad_log_filter_clear);
        actionRow.addView(selectAll);
        actionRow.addView(clear);

        TextView empty = new TextView(activity);
        empty.setText(R.string.ad_log_filter_options_empty);
        empty.setTextColor(0xFF5F6368);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(options.isEmpty() ? View.VISIBLE : View.GONE);
        LinearLayout.LayoutParams emptyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 180));
        root.addView(empty, emptyParams);

        RecyclerView recycler = new RecyclerView(activity);
        recycler.setLayoutManager(new LinearLayoutManager(activity));
        recycler.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int listHeight = Math.max(dp(activity, 180), Math.min(dp(activity, 360),
                (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.42f)));
        LinearLayout.LayoutParams listParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listHeight);
        listParams.topMargin = dp(activity, 6);
        root.addView(recycler, listParams);

        OptionAdapter adapter = new OptionAdapter(activity, options, selectedValues, empty);
        recycler.setAdapter(adapter);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.setQuery(s == null ? "" : s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        selectAll.setOnClickListener(v -> adapter.selectAll());
        clear.setOnClickListener(v -> adapter.clearSelection());

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity, R.style.ThemeOverlay_WebHTV_LightDialog)
                .setTitle(title)
                .setView(root)
                .setNegativeButton(R.string.ad_log_filter_cancel, null)
                .setPositiveButton(R.string.ad_log_filter_apply, null)
                .create();
        dialog.setOnShowListener(ignored -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                if (listener != null) listener.onApply(adapter.selectedValues());
                dialog.dismiss();
            });
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> dialog.dismiss());
            configureWindow(dialog, activity);
        });
        dialog.show();
    }

    private static List<Option> buildOptions(Activity activity, List<AdBlockLog> logs,
                                             ValueProvider provider) {
        if (logs == null || logs.isEmpty() || provider == null) return new ArrayList<>();
        String unknown = activity.getString(R.string.ad_log_unknown);
        LinkedHashMap<String, Option> byKey = new LinkedHashMap<>();
        for (AdBlockLog log : logs) {
            String value = provider.value(log);
            if (value == null || value.isBlank()) value = unknown;
            String key = normalize(value);
            Option option = byKey.get(key);
            if (option == null) byKey.put(key, new Option(value.trim(), 1));
            else option.count++;
        }
        List<Option> options = new ArrayList<>(byKey.values());
        options.sort(Comparator.comparingInt((Option option) -> option.count).reversed()
                .thenComparing(option -> option.value, String.CASE_INSENSITIVE_ORDER));
        return options;
    }

    private static MaterialButton actionButton(Activity activity, int text) {
        MaterialButton button = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(text);
        button.setTextSize(12);
        button.setMinHeight(dp(activity, 36));
        button.setMinWidth(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(activity, 36));
        params.leftMargin = dp(activity, 6);
        button.setLayoutParams(params);
        return button;
    }

    private static void configureWindow(AlertDialog dialog, Activity activity) {
        Window window = dialog.getWindow();
        if (window == null) return;
        int margin = dp(activity, 24);
        int maxWidth = dp(activity, 720);
        int width = Math.min(maxWidth,
                Math.max(dp(activity, 280), activity.getResources().getDisplayMetrics().widthPixels - margin * 2));
        window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class Option {
        private final String value;
        private int count;

        private Option(String value, int count) {
            this.value = value;
            this.count = count;
        }
    }

    private static final class OptionAdapter extends RecyclerView.Adapter<OptionAdapter.Holder> {
        private final Activity activity;
        private final List<Option> allOptions;
        private final List<Option> visibleOptions = new ArrayList<>();
        private final Set<String> selectedKeys = new LinkedHashSet<>();
        private final TextView empty;
        private String query = "";

        private OptionAdapter(Activity activity, List<Option> options, Set<String> selected,
                              TextView empty) {
            this.activity = activity;
            this.allOptions = options;
            this.empty = empty;
            if (selected != null) {
                Set<String> available = new LinkedHashSet<>();
                for (Option option : options) available.add(normalize(option.value));
                for (String value : selected) {
                    if (available.contains(normalize(value))) selectedKeys.add(normalize(value));
                }
            }
            visibleOptions.addAll(options);
            updateEmptyVisibility();
        }

        private void setQuery(String value) {
            query = normalize(value);
            visibleOptions.clear();
            for (Option option : allOptions) {
                if (query.isEmpty() || normalize(option.value).contains(query)) visibleOptions.add(option);
            }
            updateEmptyVisibility();
            notifyDataSetChanged();
        }

        private void selectAll() {
            for (Option option : allOptions) selectedKeys.add(normalize(option.value));
            notifyDataSetChanged();
        }

        private void clearSelection() {
            selectedKeys.clear();
            notifyDataSetChanged();
        }

        private Set<String> selectedValues() {
            Set<String> result = new LinkedHashSet<>();
            for (Option option : allOptions) {
                if (selectedKeys.contains(normalize(option.value))) result.add(option.value);
            }
            return result;
        }

        private void updateEmptyVisibility() {
            empty.setVisibility(visibleOptions.isEmpty() ? View.VISIBLE : View.GONE);
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            CheckBox checkBox = new CheckBox(activity);
            checkBox.setMinHeight(dp(activity, 48));
            checkBox.setGravity(Gravity.CENTER_VERTICAL);
            checkBox.setPadding(dp(activity, 8), 0, dp(activity, 8), 0);
            checkBox.setTextColor(0xFF202124);
            boolean touchDevice = activity.getResources().getConfiguration().touchscreen
                    != Configuration.TOUCHSCREEN_NOTOUCH;
            checkBox.setFocusable(!touchDevice);
            checkBox.setFocusableInTouchMode(!touchDevice);
            return new Holder(checkBox);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            Option option = visibleOptions.get(position);
            CheckBox checkBox = holder.checkBox;
            checkBox.setOnCheckedChangeListener(null);
            checkBox.setText(String.format(Locale.getDefault(), "%s (%d)", option.value, option.count));
            checkBox.setChecked(selectedKeys.contains(normalize(option.value)));
            checkBox.setOnCheckedChangeListener((button, checked) -> {
                String key = normalize(option.value);
                if (checked) selectedKeys.add(key);
                else selectedKeys.remove(key);
            });
        }

        @Override
        public int getItemCount() {
            return visibleOptions.size();
        }

        private static final class Holder extends RecyclerView.ViewHolder {
            private final CheckBox checkBox;

            private Holder(CheckBox checkBox) {
                super(checkBox);
                this.checkBox = checkBox;
            }
        }
    }
}
