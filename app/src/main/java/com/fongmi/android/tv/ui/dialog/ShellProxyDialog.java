package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterShellProxyRuleBinding;
import com.fongmi.android.tv.databinding.DialogShellProxyBinding;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Json;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShellProxyDialog extends BaseAlertDialog {

    private DialogShellProxyBinding binding;
    private RuleAdapter adapter;
    private Runnable callback;
    private boolean syncing;
    private boolean textMode = true;

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
    }

    @Override
    protected void initView() {
        adapter = new RuleAdapter();
        binding.defaultUrl.setText(Setting.getShellProxyUrl());
        if (TextUtils.isEmpty(binding.defaultUrl.getText())) binding.defaultUrl.setText("socks5://");
        binding.defaultUrl.setSelection(binding.defaultUrl.length());
        binding.rules.setText(getRules());
        binding.rules.setSelection(binding.rules.length());
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
        binding.defaultUrl.setOnEditorActionListener((textView, actionId, event) -> false);
        binding.defaultUrl.setOnFocusChangeListener((view, hasFocus) -> {
            if (hasFocus && TextUtils.isEmpty(binding.defaultUrl.getText())) {
                binding.defaultUrl.setText("socks5://");
                binding.defaultUrl.setSelection(binding.defaultUrl.length());
            }
        });
        binding.rules.setOnEditorActionListener((textView, actionId, event) -> {
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
        binding.negative.setOnClickListener(view -> dismiss());
        binding.positive.setOnClickListener(view -> onPositive());
        binding.addRule.setOnClickListener(view -> {
            adapter.add(new Rule("", ""));
            syncTextFromRules();
            binding.ruleRecycler.scrollToPosition(adapter.getItemCount() - 1);
        });
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
        return binding.defaultUrl.getText() == null ? "" : binding.defaultUrl.getText().toString().trim();
    }

    private String getRuleText() {
        syncTextFromRulesIfNeeded();
        return binding.rules.getText() == null ? "" : binding.rules.getText().toString().trim();
    }

    private void showTextMode(boolean text) {
        textMode = text;
        if (textMode) syncTextFromRules();
        else {
            updateRulesFromText();
            if (adapter.getItemCount() == 0) adapter.add(new Rule("", ""));
        }
        binding.rulesLayout.setVisibility(textMode ? View.VISIBLE : View.GONE);
        binding.ruleEditor.setVisibility(textMode ? View.GONE : View.VISIBLE);
        binding.addRule.setVisibility(textMode ? View.GONE : View.VISIBLE);
        binding.modePanel.requestLayout();
        binding.modePanel.invalidate();
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
        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                adapter.move(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                syncTextFromRules();
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                adapter.remove(viewHolder.getBindingAdapterPosition());
                syncTextFromRules();
            }
        });
        helper.attachToRecyclerView(binding.ruleRecycler);
        adapter.setDragListener(holder -> helper.startDrag(holder));
    }

    private void onPositive() {
        String url = getDefaultUrl();
        String rules = getRuleText();
        if (!ProxySetting.isValidRules(rules, url)) {
            Notify.show(R.string.setting_proxy_invalid);
            return;
        }
        Setting.putShellProxyConfig(url, rules);
        if (callback != null) callback.run();
        dismiss();
    }

    private static class Rule {

        private String hosts;
        private String url;

        Rule(String hosts, String url) {
            this.hosts = hosts;
            this.url = url;
        }

        static List<Rule> parse(String text) {
            if (Json.isObj(text) || Json.isArray(text)) return parseJson(text);
            List<Rule> items = new ArrayList<>();
            for (String raw : text.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+", 2);
                items.add(new Rule(parts[0].trim(), parts.length > 1 ? parts[1].trim() : ""));
            }
            return items;
        }

        static List<Rule> parseJson(String text) {
            List<Rule> items = new ArrayList<>();
            try {
                JsonElement element = Json.parse(text);
                JsonArray array = element.isJsonObject() && element.getAsJsonObject().has("proxy") ? element.getAsJsonObject().getAsJsonArray("proxy") : element.getAsJsonArray();
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

        static String join(JsonObject object, String key) {
            if (!object.has(key) || !object.get(key).isJsonArray()) return "";
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
                object.add("urls", array(url));
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

        void setItems(List<Rule> items) {
            this.items.clear();
            this.items.addAll(items);
            notifyDataSetChanged();
        }

        List<Rule> getItems() {
            return items;
        }

        void add(Rule item) {
            items.add(item);
            notifyItemInserted(items.size() - 1);
        }

        void move(int from, int to) {
            if (from < 0 || to < 0 || from >= items.size() || to >= items.size()) return;
            Collections.swap(items, from, to);
            notifyItemMoved(from, to);
        }

        void remove(int position) {
            if (position < 0 || position >= items.size()) return;
            items.remove(position);
            notifyItemRemoved(position);
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
            Rule item = items.get(position);
            holder.binding.hosts.setText(item.hosts);
            holder.binding.url.setText(item.url);
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
                binding.hosts.addTextChangedListener(new RuleTextListener(this, true));
                binding.url.addTextChangedListener(new RuleTextListener(this, false));
            }
        }
    }

    private class RuleTextListener extends CustomTextListener {

        private final RuleAdapter.ViewHolder holder;
        private final boolean hosts;

        RuleTextListener(RuleAdapter.ViewHolder holder, boolean hosts) {
            this.holder = holder;
            this.hosts = hosts;
        }

        @Override
        public void afterTextChanged(Editable editable) {
            int position = holder.getBindingAdapterPosition();
            if (position < 0 || position >= adapter.getItems().size()) return;
            Rule item = adapter.getItems().get(position);
            if (hosts) item.hosts = editable.toString();
            else item.url = editable.toString();
        }
    }

    private interface DragListener {

        void onStartDrag(RecyclerView.ViewHolder holder);
    }
}
