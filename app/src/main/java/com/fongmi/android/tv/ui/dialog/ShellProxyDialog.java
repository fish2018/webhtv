package com.fongmi.android.tv.ui.dialog;

import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.AdapterShellProxyRuleBinding;
import com.fongmi.android.tv.databinding.DialogShellProxyBinding;
import com.fongmi.android.tv.databinding.DialogShellProxyRuleEditBinding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.SettingClipboardOverlay;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.github.catvod.bean.Proxy;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Json;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.Request;
import okhttp3.Response;

public class ShellProxyDialog extends BaseAlertDialog {

    private DialogShellProxyBinding binding;
    private RuleAdapter adapter;
    private Runnable callback;
    private boolean syncing;
    private boolean proxyEnabled;
    private boolean textMode = true;
    private boolean recognizeMode;
    private boolean reverseOrder;
    private boolean beforeRecognizeTextMode = true;
    private boolean saved;
    private boolean testing;
    private SettingClipboardOverlay clipboardOverlay;
    private Dialog ruleEditorDialog;

    public static void show(Fragment fragment) {
        show(fragment, null);
    }

    public static void show(Fragment fragment, Runnable callback) {
        ShellProxyDialog dialog = new ShellProxyDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity) {
        show(activity, null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        ShellProxyDialog dialog = new ShellProxyDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogShellProxyBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        boolean land = ResUtil.isLand(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.72f : 0.92f));
        params.height = land ? (int) (screenHeight * 0.98f) : WindowManager.LayoutParams.WRAP_CONTENT;
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = land ? params.height : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.root.setLayoutParams(rootParams);
        LinearLayoutCompat.LayoutParams scrollParams = (LinearLayoutCompat.LayoutParams) binding.contentScroll.getLayoutParams();
        scrollParams.height = land ? 0 : ViewGroup.LayoutParams.WRAP_CONTENT;
        scrollParams.weight = land ? 1 : 0;
        binding.contentScroll.setLayoutParams(scrollParams);
        binding.contentScroll.setMaxHeight(land ? 0 : (int) (screenHeight * 0.52f));
        binding.proxyEnabled.requestFocus();
        if (clipboardOverlay == null) clipboardOverlay = SettingClipboardOverlay.attach(this, binding.getRoot());
    }

    @Override
    protected void initView() {
        adapter = new RuleAdapter();
        proxyEnabled = Setting.isShellProxy();
        updateProxyEnabledText();
        updateReverseText();
        binding.defaultUrl.setText(Setting.getShellProxyUrl());
        if (TextUtils.isEmpty(binding.defaultUrl.getText())) binding.defaultUrl.setText("socks5://");
        binding.defaultUrl.setSelection(binding.defaultUrl.length());
        binding.rules.setText(getRules());
        binding.rules.setSelection(binding.rules.length());
        setupEditableText(binding.defaultUrl, false);
        setupEditableText(binding.rules, true);
        setupEditableText(binding.recognizeInput, true);
        binding.ruleRecycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.ruleRecycler.setItemAnimator(null);
        binding.ruleRecycler.setAdapter(adapter);
        binding.modeGroup.check(R.id.textMode);
        attachTouchHelper();
        updateRulesFromText();
        showTextMode(true);
    }

    @Override
    protected void initEvent() {
        binding.proxyEnabled.setOnClickListener(view -> {
            proxyEnabled = !proxyEnabled;
            updateProxyEnabledText();
        });
        binding.reverse.setOnClickListener(view -> {
            reverseOrder = !reverseOrder;
            updateReverseText();
            adapter.setReverseOrder(reverseOrder);
        });
        binding.defaultUrl.setOnEditorActionListener((textView, actionId, event) -> false);
        binding.defaultUrl.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && TextUtils.isEmpty(binding.defaultUrl.getText())) {
                binding.defaultUrl.setText("socks5://");
                binding.defaultUrl.setSelection(binding.defaultUrl.length());
            }
        });
        binding.defaultUrl.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                adapter.notifyDataSetChanged();
            }
        });
        binding.rules.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
        binding.recognizeInput.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) onPositive();
            return true;
        });
        binding.rules.addTextChangedListener(new CustomTextListener() {
            @Override
            public void afterTextChanged(Editable editable) {
                if (!syncing && textMode) updateRulesFromText();
            }
        });
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.textMode) showTextMode(true);
            if (checkedId == R.id.uiMode) showTextMode(false);
        });
        binding.negative.setOnClickListener(view -> {
            if (recognizeMode) showRecognizeMode(false);
            else dismiss();
        });
        binding.positive.setOnClickListener(view -> onPositive());
        binding.addRule.setOnClickListener(view -> {
            int position = adapter.add(new Rule("", ""));
            syncTextFromRules();
            scrollToRule(position);
        });
        binding.suggestRule.setOnClickListener(view -> showSuggestSiteDialog());
        binding.recognizeRule.setOnClickListener(view -> showRecognizeMode(true));
        binding.testRule.setOnClickListener(view -> testProxy());
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        save(false);
        super.onCancel(dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (ruleEditorDialog != null) ruleEditorDialog.dismiss();
        ruleEditorDialog = null;
        if (clipboardOverlay != null) clipboardOverlay.detach();
        clipboardOverlay = null;
        save(false);
        super.onDismiss(dialog);
    }

    private void setupEditableText(EditText input, boolean multiline) {
        input.setSelectAllOnFocus(false);
        input.setHorizontallyScrolling(true);
        input.setHorizontalScrollBarEnabled(true);
        input.setVerticalScrollBarEnabled(multiline);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                view.post(() -> disallowParentIntercept(view, false));
            } else {
                disallowParentIntercept(view, true);
            }
            return false;
        });
    }

    private void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private String getRules() {
        String rules = Setting.getShellProxyRules();
        if (!TextUtils.isEmpty(rules)) return Rule.toRawJson(Rule.parse(rules));
        String url = Setting.getShellProxyUrl();
        String hosts = Setting.getShellProxyHosts();
        if (TextUtils.isEmpty(url) || TextUtils.isEmpty(hosts) || "*".equals(hosts)) return "";
        return Rule.toRawJson(List.of(new Rule(hosts, url)));
    }

    private String getDefaultUrl() {
        return ProxySetting.cleanUrl(binding.defaultUrl.getText() == null ? "" : binding.defaultUrl.getText().toString());
    }

    private String getRuleText() {
        syncTextFromRulesIfNeeded();
        return binding.rules.getText() == null ? "" : binding.rules.getText().toString().trim();
    }

    private void showTextMode(boolean text) {
        if (recognizeMode) return;
        textMode = text;
        if (textMode) syncTextFromRules();
        else {
            updateRulesFromText();
            if (adapter.getItemCount() == 0) adapter.add(new Rule("", ""));
        }
        updateModeVisibility();
    }

    private void showRecognizeMode(boolean show) {
        if (show) {
            syncTextFromRulesIfNeeded();
            beforeRecognizeTextMode = textMode;
            recognizeMode = true;
            binding.recognizeInput.setText("");
            binding.negative.setText(R.string.playback_webhook_back);
            binding.positive.setText(R.string.dialog_positive);
            updateModeVisibility();
            binding.contentScroll.scrollTo(0, 0);
            binding.recognizeInput.requestFocus();
            return;
        }
        recognizeMode = false;
        binding.negative.setText(R.string.dialog_negative);
        binding.positive.setText(R.string.dialog_positive);
        showTextMode(beforeRecognizeTextMode);
    }

    private void updateModeVisibility() {
        binding.rulesLayout.setVisibility(textMode ? View.VISIBLE : View.GONE);
        binding.ruleEditor.setVisibility(textMode ? View.GONE : View.VISIBLE);
        binding.recognizeLayout.setVisibility(recognizeMode ? View.VISIBLE : View.GONE);
        binding.rulesLayout.setVisibility(!recognizeMode && textMode ? View.VISIBLE : View.GONE);
        binding.ruleEditor.setVisibility(!recognizeMode && !textMode ? View.VISIBLE : View.GONE);
        binding.modeGroup.setVisibility(recognizeMode ? View.GONE : View.VISIBLE);
        binding.addRule.setVisibility(recognizeMode || textMode ? View.GONE : View.VISIBLE);
        binding.suggestRule.setVisibility(recognizeMode ? View.GONE : View.VISIBLE);
        binding.recognizeRule.setVisibility(recognizeMode ? View.GONE : View.VISIBLE);
        binding.testRule.setVisibility(recognizeMode ? View.GONE : View.VISIBLE);
        binding.modePanel.requestLayout();
        binding.modePanel.invalidate();
    }

    private void updateProxyEnabledText() {
        binding.proxyEnabled.setText(proxyEnabled ? R.string.setting_enable : R.string.setting_disable);
        binding.proxyEnabled.setAlpha(proxyEnabled ? 1.0f : 0.65f);
    }

    private void updateReverseText() {
        binding.reverse.setText(reverseOrder ? R.string.setting_order_normal : R.string.setting_order_reverse);
    }

    private void updateRulesFromText() {
        if (syncing) return;
        syncing = true;
        adapter.setItems(Rule.parse(binding.rules.getText() == null ? "" : binding.rules.getText().toString()));
        syncing = false;
    }

    private void syncTextFromRulesIfNeeded() {
        if (!textMode) syncTextFromRules();
    }

    private void syncTextFromRules() {
        if (syncing) return;
        syncing = true;
        String text = Rule.format(adapter.getItems());
        if (!TextUtils.equals(binding.rules.getText(), text)) binding.rules.setText(text);
        syncing = false;
    }

    private void attachTouchHelper() {
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                adapter.move(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                syncTextFromRules();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        helper.attachToRecyclerView(binding.ruleRecycler);
        adapter.setDragListener(holder -> helper.startDrag(holder));
    }

    private void showSuggestSiteDialog() {
        List<Site> sites = VodConfig.get().getSites().stream().filter(site -> !site.isEmpty()).toList();
        if (sites.isEmpty()) {
            Notify.show(R.string.setting_proxy_no_site);
            return;
        }
        String[] names = new String[sites.size()];
        for (int i = 0; i < sites.size(); i++) names[i] = getSiteName(sites.get(i));
        ChoiceDialog.showSingle(this, R.string.setting_proxy_select_site, names, -1, which -> suggestRules(sites.get(which)));
    }

    private String getSiteName(Site site) {
        return site.getDisplayName();
    }

    private void suggestRules(Site site) {
        String url = getDefaultUrl();
        if (!ProxySetting.isValid(url)) {
            Notify.show(R.string.setting_proxy_invalid);
            return;
        }
        ProxySetting.Suggestion suggestion = ProxySetting.suggest(site);
        if (suggestion.isEmpty()) {
            Notify.show(R.string.setting_proxy_no_suggest);
            return;
        }
        syncTextFromRulesIfNeeded();
        List<Rule> items = new ArrayList<>(adapter.getItems());
        Set<String> hosts = getHosts(items);
        int added = 0;
        for (String host : suggestion.hosts()) {
            String key = normalizeHost(host);
            if (TextUtils.isEmpty(key) || hosts.contains(key)) continue;
            items.add(new Rule(host, ""));
            hosts.add(key);
            added++;
        }
        int firstAdded = adapter.getItemCount();
        adapter.setItems(items);
        proxyEnabled = true;
        updateProxyEnabledText();
        syncTextFromRules();
        if (added > 0) scrollToRule(adapter.displayPosition(reverseOrder ? items.size() - 1 : firstAdded));
        Notify.show(ResUtil.getString(R.string.setting_proxy_suggest_added, added, hosts.size()));
    }

    private boolean saveRecognizedRules() {
        String text = binding.recognizeInput.getText() == null ? "" : binding.recognizeInput.getText().toString();
        if (TextUtils.isEmpty(text.trim())) {
            Notify.show(R.string.setting_proxy_recognize_empty);
            return false;
        }
        List<Rule> items = Rule.parseDetected(text);
        if (items.isEmpty()) {
            Notify.show(R.string.setting_proxy_recognize_failed);
            return false;
        }
        int firstAdded = adapter.getItemCount();
        List<Rule> next = mergeRules(adapter.getItems(), items);
        adapter.setItems(next);
        proxyEnabled = true;
        updateProxyEnabledText();
        syncTextFromRules();
        showRecognizeMode(false);
        if (next.size() > firstAdded) scrollToRule(adapter.displayPosition(reverseOrder ? next.size() - 1 : firstAdded));
        Notify.show(ResUtil.getString(R.string.setting_proxy_recognize_done, items.size()));
        return true;
    }

    private void scrollToRule(int position) {
        if (position < 0) return;
        binding.ruleRecycler.post(() -> {
            binding.ruleRecycler.scrollToPosition(position);
            binding.ruleRecycler.post(() -> {
                RecyclerView.ViewHolder holder = binding.ruleRecycler.findViewHolderForAdapterPosition(position);
                if (holder != null) holder.itemView.requestFocus();
            });
        });
    }

    private void editRule(int position) {
        if (position < 0 || position >= adapter.getItemCount()) return;
        if (ruleEditorDialog != null && ruleEditorDialog.isShowing()) return;
        int index = adapter.itemIndex(position);
        Rule item = adapter.getItems().get(index);
        DialogShellProxyRuleEditBinding editor = DialogShellProxyRuleEditBinding.inflate(getLayoutInflater());
        editor.hosts.setText(item.hosts);
        editor.url.setText(item.url);
        setupEditableText(editor.hosts, true);
        setupEditableText(editor.url, false);
        String defaultUrl = getDefaultUrl();
        editor.urlLayout.setHelperText(TextUtils.isEmpty(defaultUrl)
                ? getString(R.string.setting_proxy_rule_use_default_empty)
                : getString(R.string.setting_proxy_rule_use_default, defaultUrl));
        Dialog dialog = LightDialog.create(requireContext(), getString(R.string.setting_proxy_edit_rule, index + 1), editor.getRoot(), getString(R.string.dialog_positive), view -> {
            item.hosts = editor.hosts.getText() == null ? "" : editor.hosts.getText().toString().trim();
            item.url = editor.url.getText() == null ? "" : editor.url.getText().toString().trim();
            adapter.notifyItemChanged(adapter.displayPosition(index));
            syncTextFromRules();
            if (ruleEditorDialog != null) ruleEditorDialog.dismiss();
        }, getString(R.string.dialog_negative), null);
        dialog.setOnDismissListener(ignored -> {
            if (ruleEditorDialog == dialog) ruleEditorDialog = null;
        });
        ruleEditorDialog = dialog;
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        editor.url.setOnEditorActionListener((textView, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_DONE) return false;
            item.hosts = editor.hosts.getText() == null ? "" : editor.hosts.getText().toString().trim();
            item.url = editor.url.getText() == null ? "" : editor.url.getText().toString().trim();
            adapter.notifyItemChanged(adapter.displayPosition(index));
            syncTextFromRules();
            dialog.dismiss();
            return true;
        });
    }

    private String getRuleHostSummary(Rule item) {
        String hosts = item.hosts == null ? "" : item.hosts.trim();
        return TextUtils.isEmpty(hosts) ? "*" : hosts;
    }

    private String getRuleProxySummary(Rule item) {
        String url = item.url == null ? "" : item.url.trim();
        if (!TextUtils.isEmpty(url)) return url;
        String defaultUrl = getDefaultUrl();
        return TextUtils.isEmpty(defaultUrl)
                ? getString(R.string.setting_proxy_rule_use_default_empty)
                : getString(R.string.setting_proxy_rule_use_default, defaultUrl);
    }

    private List<Rule> mergeRules(List<Rule> current, List<Rule> incoming) {
        List<Rule> result = new ArrayList<>();
        Set<String> exists = new LinkedHashSet<>();
        for (Rule item : current) addRuleIfAbsent(result, exists, item);
        for (Rule item : incoming) addRuleIfAbsent(result, exists, item);
        if (result.isEmpty()) result.add(new Rule("", ""));
        return result;
    }

    private void addRuleIfAbsent(List<Rule> result, Set<String> exists, Rule item) {
        if (item == null) return;
        String hosts = item.hosts == null ? "" : item.hosts.trim();
        String url = item.url == null ? "" : item.url.trim();
        if (hosts.isEmpty() && url.isEmpty()) return;
        String key = hosts.toLowerCase(Locale.ROOT) + "=>" + url;
        if (exists.contains(key)) return;
        result.add(new Rule(hosts, url));
        exists.add(key);
    }

    private Set<String> getHosts(List<Rule> items) {
        Set<String> hosts = new LinkedHashSet<>();
        for (Rule item : items) {
            if (item.hosts == null) continue;
            for (String host : item.hosts.split(",")) {
                String value = normalizeHost(host);
                if (!TextUtils.isEmpty(value)) hosts.add(value);
            }
        }
        return hosts;
    }

    private String normalizeHost(String host) {
        return host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
    }

    private void testProxy() {
        if (testing) return;
        String url = getDefaultUrl();
        String rules = getRuleText();
        if (!ProxySetting.isValidRules(rules, url)) {
            Notify.show(R.string.setting_proxy_invalid);
            return;
        }
        List<Proxy> items = ProxySetting.getRules(rules, url);
        String host = ProxySetting.firstTestHost(items);
        if (TextUtils.isEmpty(host)) {
            Notify.show(R.string.setting_proxy_test_no_rule);
            return;
        }
        testing = true;
        binding.testRule.setEnabled(false);
        Notify.show(R.string.setting_proxy_test_running);
        Task.execute(() -> {
            TestResult result;
            try {
                result = runProxyTest(items, host);
            } catch (Throwable e) {
                result = new TestResult(false, getError(e));
            }
            TestResult output = result;
            App.post(() -> {
                testing = false;
                binding.testRule.setEnabled(true);
                Notify.show(ResUtil.getString(output.success ? R.string.setting_proxy_test_success : R.string.setting_proxy_test_failed, output.message));
            });
        });
    }

    private TestResult runProxyTest(List<Proxy> rules, String host) {
        Exception error = null;
        try {
            OkHttp.selector().remove("app");
            OkHttp.selector().addAll(rules);
            if (!isRouted("https://" + host + "/") && !isRouted("http://" + host + "/")) return new TestResult(false, host);
            okhttp3.OkHttpClient client = OkHttp.client(TimeUnit.SECONDS.toMillis(6));
            for (String scheme : List.of("https", "http")) {
                String url = scheme + "://" + host + "/";
                try (Response response = client.newCall(new Request.Builder().url(url).header("Range", "bytes=0-0").build()).execute()) {
                    return new TestResult(true, host + " " + response.code());
                } catch (Exception e) {
                    error = e;
                }
            }
            return new TestResult(false, host + " " + getError(error));
        } finally {
            ProxySetting.apply();
        }
    }

    private boolean isRouted(String url) {
        try {
            for (java.net.Proxy proxy : OkHttp.selector().select(URI.create(url))) {
                if (proxy.type() != java.net.Proxy.Type.DIRECT) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private String getError(Throwable error) {
        return error == null || TextUtils.isEmpty(error.getMessage()) ? "" : error.getMessage();
    }

    private void onPositive() {
        if (recognizeMode) {
            saveRecognizedRules();
            return;
        }
        if (save(true)) dismiss();
    }

    private boolean save(boolean validate) {
        if (saved) return true;
        boolean enabled = proxyEnabled;
        String url = getDefaultUrl();
        String rules = getRuleText();
        if (validate && enabled && !ProxySetting.isValidRules(rules, url)) {
            Notify.show(R.string.setting_proxy_invalid);
            return false;
        }
        if (!enabled) Setting.putShellProxy(false);
        Setting.putShellProxyConfig(url, rules);
        if (enabled) Setting.putShellProxy(true);
        if (callback != null) callback.run();
        saved = true;
        return true;
    }

    private record TestResult(boolean success, String message) {
    }

    private static class Rule {

        private String hosts;
        private String url;

        Rule(String hosts, String url) {
            this.hosts = hosts;
            this.url = url;
        }

        static List<Rule> parse(String text) {
            List<Rule> detected = parseDetected(text);
            if (!detected.isEmpty()) return detected;
            List<Rule> items = new ArrayList<>();
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+", 2);
                items.add(new Rule(parts[0].trim(), parts.length > 1 ? parts[1].trim() : ""));
            }
            return items;
        }

        static List<Rule> parseDetected(String text) {
            String raw = text == null ? "" : text.trim();
            if (TextUtils.isEmpty(raw)) return Collections.emptyList();
            List<Rule> items = parseJson(raw);
            if (!items.isEmpty()) return items;
            String proxyArray = extractNamedArray(raw, "proxy");
            if (!TextUtils.isEmpty(proxyArray)) {
                items = parseJson("{\"proxy\":" + proxyArray + "}");
                if (!items.isEmpty()) return items;
            }
            String array = extractFirstArray(raw);
            if (!TextUtils.isEmpty(array) && (array.contains("\"hosts\"") || array.contains("\"urls\""))) {
                items = parseJson(array);
                if (!items.isEmpty()) return items;
            }
            String objects = extractObjects(raw);
            return TextUtils.isEmpty(objects) ? Collections.emptyList() : parseJson("[" + objects + "]");
        }

        static List<Rule> parseJson(String text) {
            List<Rule> items = new ArrayList<>();
            try {
                JsonElement element = Json.parse(trimTrailingComma(text));
                JsonArray array = new JsonArray();
                if (element.isJsonObject() && element.getAsJsonObject().has("proxy") && element.getAsJsonObject().get("proxy").isJsonArray()) {
                    array = element.getAsJsonObject().getAsJsonArray("proxy");
                } else if (element.isJsonObject()) {
                    array.add(element);
                } else if (element.isJsonArray()) {
                    array = element.getAsJsonArray();
                }
                for (JsonElement item : array) {
                    if (!item.isJsonObject()) continue;
                    JsonObject object = item.getAsJsonObject();
                    String hosts = join(object, "hosts");
                    String urls = join(object, "urls");
                    if (!TextUtils.isEmpty(hosts) || !TextUtils.isEmpty(urls)) items.add(new Rule(hosts, urls));
                }
            } catch (Exception ignored) {
            }
            return items;
        }

        static String trimTrailingComma(String text) {
            String value = text == null ? "" : text.trim();
            while (value.endsWith(",")) value = value.substring(0, value.length() - 1).trim();
            return value;
        }

        static String extractNamedArray(String text, String key) {
            String marker = "\"" + key + "\"";
            int search = 0;
            while (search >= 0 && search < text.length()) {
                int index = text.indexOf(marker, search);
                if (index < 0) return "";
                int colon = text.indexOf(':', index + marker.length());
                if (colon < 0) return "";
                int start = nextNonSpace(text, colon + 1);
                if (start >= 0 && start < text.length() && text.charAt(start) == '[') {
                    int end = findClosing(text, start, '[', ']');
                    return end > start ? text.substring(start, end + 1) : "";
                }
                search = colon + 1;
            }
            return "";
        }

        static String extractFirstArray(String text) {
            int start = text.indexOf('[');
            while (start >= 0) {
                int end = findClosing(text, start, '[', ']');
                if (end > start) return text.substring(start, end + 1);
                start = text.indexOf('[', start + 1);
            }
            return "";
        }

        static String extractObjects(String text) {
            List<String> objects = new ArrayList<>();
            int start = text.indexOf('{');
            while (start >= 0) {
                int end = findClosing(text, start, '{', '}');
                if (end <= start) break;
                String object = text.substring(start, end + 1);
                if (object.contains("\"hosts\"") || object.contains("\"urls\"")) objects.add(object);
                start = text.indexOf('{', end + 1);
            }
            return TextUtils.join(",", objects);
        }

        static int nextNonSpace(String text, int start) {
            for (int i = start; i < text.length(); i++) if (!Character.isWhitespace(text.charAt(i))) return i;
            return -1;
        }

        static int findClosing(String text, int start, char open, char close) {
            boolean inString = false;
            boolean escaped = false;
            int depth = 0;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (inString) {
                    if (escaped) escaped = false;
                    else if (c == '\\') escaped = true;
                    else if (c == '"') inString = false;
                    continue;
                }
                if (c == '"') {
                    inString = true;
                    continue;
                }
                if (c == open) depth++;
                else if (c == close && --depth == 0) return i;
            }
            return -1;
        }

        static String join(JsonObject object, String key) {
            if (!object.has(key)) return "";
            if (object.get(key).isJsonPrimitive()) return object.get(key).getAsString();
            if (!object.get(key).isJsonArray()) return "";
            List<String> result = new ArrayList<>();
            for (JsonElement element : object.getAsJsonArray(key)) if (element.isJsonPrimitive()) result.add(element.getAsString());
            return TextUtils.join(",", result);
        }

        static String format(List<Rule> items) {
            return toRawJson(items);
        }

        static String toRawJson(List<Rule> items) {
            JsonObject root = new JsonObject();
            JsonArray proxy = new JsonArray();
            List<String> lines = new ArrayList<>();
            for (Rule item : items) {
                String hosts = item.hosts == null ? "" : item.hosts.trim();
                String url = item.url == null ? "" : item.url.trim();
                if (hosts.isEmpty() && url.isEmpty()) continue;
                if (hosts.isEmpty()) hosts = "*";
                JsonObject object = new JsonObject();
                object.add("hosts", array(hosts));
                if (!url.isEmpty()) object.add("urls", array(url));
                proxy.add(object);
            }
            root.add("proxy", proxy);
            if (proxy.isEmpty()) return "";
            return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
        }

        static JsonArray array(String text) {
            JsonArray array = new JsonArray();
            for (String item : text.split(",")) {
                String value = item.trim();
                if (!TextUtils.isEmpty(value)) array.add(value);
            }
            return array;
        }
    }

    private class RuleAdapter extends RecyclerView.Adapter<RuleAdapter.ViewHolder> {

        private final List<Rule> items = new ArrayList<>();
        private DragListener dragListener;
        private boolean reverseOrder;

        void setItems(List<Rule> items) {
            this.items.clear();
            this.items.addAll(items);
            notifyDataSetChanged();
        }

        List<Rule> getItems() {
            return items;
        }

        int add(Rule item) {
            items.add(item);
            int position = displayPosition(items.size() - 1);
            notifyItemInserted(position);
            return position;
        }

        void move(int fromPosition, int toPosition) {
            if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size() || toPosition >= items.size()) return;
            int from = itemIndex(fromPosition);
            int to = itemIndex(toPosition);
            Collections.swap(items, from, to);
            notifyItemMoved(fromPosition, toPosition);
            notifyItemRangeChanged(Math.min(fromPosition, toPosition), Math.abs(fromPosition - toPosition) + 1);
        }

        void remove(int position) {
            if (position < 0 || position >= items.size()) return;
            items.remove(itemIndex(position));
            notifyDataSetChanged();
        }

        void setReverseOrder(boolean reverseOrder) {
            if (this.reverseOrder == reverseOrder) return;
            this.reverseOrder = reverseOrder;
            notifyDataSetChanged();
        }

        int itemIndex(int position) {
            return reverseOrder ? items.size() - 1 - position : position;
        }

        int displayPosition(int index) {
            if (index < 0 || index >= items.size()) return -1;
            return reverseOrder ? items.size() - 1 - index : index;
        }

        void setDragListener(DragListener dragListener) {
            this.dragListener = dragListener;
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(AdapterShellProxyRuleBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int index = itemIndex(position);
            Rule item = items.get(index);
            holder.binding.number.setText(String.valueOf(index + 1));
            holder.binding.hosts.setText(getRuleHostSummary(item));
            holder.binding.url.setText(getRuleProxySummary(item));
            holder.binding.url.setAlpha(TextUtils.isEmpty(item.url) ? 0.68f : 1.0f);
            holder.binding.getRoot().setOnClickListener(view -> editRule(holder.getBindingAdapterPosition()));
            holder.binding.delete.setOnClickListener(view -> {
                adapter.remove(holder.getBindingAdapterPosition());
                syncTextFromRules();
            });
            holder.binding.drag.setOnTouchListener((view, event) -> {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN && dragListener != null) dragListener.onStartDrag(holder);
                return false;
            });
        }

        class ViewHolder extends RecyclerView.ViewHolder {

            private final AdapterShellProxyRuleBinding binding;

            ViewHolder(@NonNull AdapterShellProxyRuleBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
            }
        }
    }

    private interface DragListener {

        void onStartDrag(RecyclerView.ViewHolder holder);
    }
}
