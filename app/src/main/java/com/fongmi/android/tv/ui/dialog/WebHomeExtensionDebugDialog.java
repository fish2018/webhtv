package com.fongmi.android.tv.ui.dialog;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.databinding.DialogWebHomeExtensionDebugBinding;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.web.HomeWebController;
import com.fongmi.android.tv.web.ext.WebHomeExtensionRegistry;
import com.fongmi.android.tv.web.ext.WebHomeExtensionSourceStore;
import com.github.catvod.utils.Json;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class WebHomeExtensionDebugDialog extends BaseAlertDialog implements HomeWebController.Listener {

    private static final int DETAIL_MIN_DP = 96;
    private static final int DETAIL_MAX_DP = 360;
    private static final int DETAIL_STEP_DP = 56;

    private DialogWebHomeExtensionDebugBinding binding;
    private HomeWebController controller;
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);
    private final List<String> consoleLines = new ArrayList<>();
    private final List<NetworkEntry> networkEntries = new ArrayList<>();
    private Runnable callback;
    private WebHomeExtensionSourceStore.Entry source;
    private int selectedNetworkId;
    private String networkFilter = "ALL";

    public static void show(FragmentActivity activity, Runnable callback) {
        WebHomeExtensionDebugDialog dialog = new WebHomeExtensionDebugDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogWebHomeExtensionDebugBinding.inflate(getLayoutInflater());
    }

    @Override
    protected MaterialAlertDialogBuilder getBuilder() {
        return new MaterialAlertDialogBuilder(requireActivity(), R.style.ThemeOverlay_WebHTV_LightDialog).setView(getBinding().getRoot());
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) return;
        setCancelable(false);
        getDialog().setCanceledOnTouchOutside(false);
        Window window = getDialog().getWindow();
        if (window == null) return;
        WindowManager.LayoutParams params = window.getAttributes();
        int screenWidth = ResUtil.getScreenWidth(requireContext());
        int screenHeight = ResUtil.getScreenHeight(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (ResUtil.isLand(requireContext()) ? 0.96f : 0.98f));
        params.height = (int) (screenHeight * 0.90f);
        window.setAttributes(params);
        window.setLayout(params.width, params.height);
        ViewGroup.LayoutParams rootParams = binding.root.getLayoutParams();
        rootParams.height = params.height;
        binding.root.setLayoutParams(rootParams);
    }

    @Override
    protected void initView() {
        if (!Setting.isDebugLog()) Setting.putDebugLog(true);
        source = firstMatchedCodeSource();
        binding.codeText.setText(source == null ? "GM_log('ready');\n" : WebHomeExtensionSourceStore.code(source));
        updateScriptSelectorText();
        setupScrollableText(binding.codeText);
        setupScrollableText(binding.consoleText);
        setupScrollableText(binding.elementsText);
        setupScrollableText(binding.networkSearch);
        setupScrollableText(binding.networkDetail);
        binding.networkDetail.setKeyListener(null);
        binding.networkDetail.setTextIsSelectable(true);
        binding.networkFilterGroup.check(R.id.filterAll);
        binding.tabGroup.check(R.id.tabWeb);
        showTab(R.id.tabWeb);
        controller = new HomeWebController(requireActivity(), binding.web, this, true);
        Site site = VodConfig.get().getHome();
        if (site != null && site.hasHomePage()) controller.load(site, true);
        refreshPanel();
    }

    @Override
    protected void initEvent() {
        binding.tabGroup.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            showTab(checkedId);
            refreshPanel();
        });
        binding.inspect.setOnClickListener(view -> inspectElement());
        binding.clearPanel.setOnClickListener(view -> clearPanel());
        binding.networkFilterGroup.addOnButtonCheckedListener((group, checkedId, checked) -> {
            if (!checked) return;
            networkFilter = filter(checkedId);
            refreshNetwork();
        });
        binding.networkSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                refreshNetwork();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
        binding.detailSmaller.setOnClickListener(view -> changeDetailHeight(-DETAIL_STEP_DP));
        binding.detailBigger.setOnClickListener(view -> changeDetailHeight(DETAIL_STEP_DP));
        binding.scriptSelector.setOnClickListener(view -> showScriptSelector());
        binding.save.setOnClickListener(view -> saveCodeSource());
        binding.close.setOnClickListener(view -> dismiss());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (controller != null) controller.onResume();
    }

    @Override
    public void onPause() {
        if (controller != null) controller.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (controller != null) controller.destroy();
        controller = null;
        if (callback != null) callback.run();
        super.onDestroyView();
    }

    private void showTab(int tab) {
        if (controller != null) controller.show();
        binding.consoleLayout.setVisibility(tab == R.id.tabConsole ? View.VISIBLE : View.GONE);
        binding.elementsLayout.setVisibility(tab == R.id.tabElements ? View.VISIBLE : View.GONE);
        binding.networkLayout.setVisibility(tab == R.id.tabNetwork ? View.VISIBLE : View.GONE);
        binding.codeLayout.setVisibility(tab == R.id.tabCode ? View.VISIBLE : View.GONE);
        boolean code = tab == R.id.tabCode;
        boolean web = tab == R.id.tabWeb;
        boolean elements = tab == R.id.tabElements;
        boolean console = tab == R.id.tabConsole;
        boolean network = tab == R.id.tabNetwork;
        binding.scriptSelector.setVisibility(code ? View.VISIBLE : View.GONE);
        binding.save.setVisibility(code ? View.VISIBLE : View.GONE);
        binding.inspect.setVisibility(web || elements ? View.VISIBLE : View.GONE);
        binding.clearPanel.setVisibility(console || elements || network ? View.VISIBLE : View.GONE);
    }

    private void inspectElement() {
        binding.tabGroup.check(R.id.tabWeb);
        if (controller == null) return;
        appendConsole("INSPECT click an element in the Web tab");
        controller.evaluate("""
                (function(){
                  if(window.__fmInspectCleanup)window.__fmInspectCleanup();
                  function path(el){
                    const arr=[];
                    for(let n=el;n&&n.nodeType===1&&arr.length<10;n=n.parentElement){
                      let s=n.tagName.toLowerCase();
                      if(n.id)s+='#'+n.id;
                      if(n.className&&typeof n.className==='string')s+='.'+n.className.trim().split(/\\s+/).slice(0,3).join('.');
                      arr.unshift(s);
                    }
                    return arr.join(' > ');
                  }
                  function info(el){
                    const rect=el.getBoundingClientRect();
                    return {
                      path:path(el),
                      tag:el.tagName.toLowerCase(),
                      id:el.id||'',
                      className:typeof el.className==='string'?el.className:'',
                      text:(el.innerText||el.textContent||'').trim().replace(/\\s+/g,' ').slice(0,500),
                      html:(el.outerHTML||'').slice(0,12000),
                      x:Math.round(rect.left),y:Math.round(rect.top),w:Math.round(rect.width),h:Math.round(rect.height)
                    };
                  }
                  let style=document.getElementById('__fmInspectStyle');
                  if(!style){
                    style=document.createElement('style');
                    style.id='__fmInspectStyle';
                    style.textContent='.__fm-inspect-hover{outline:2px solid #137333!important;outline-offset:2px!important}.__fm-inspect-selected{outline:3px solid #b3261e!important;outline-offset:2px!important}';
                    document.documentElement.appendChild(style);
                  }
                  let hover=null;
                  function clearHover(){if(hover)hover.classList.remove('__fm-inspect-hover');hover=null;}
                  function cleanup(){clearHover();document.removeEventListener('mousemove',move,true);document.removeEventListener('click',click,true);window.__fmInspectCleanup=null;}
                  function move(e){
                    clearHover();
                    hover=e.target;
                    if(hover&&hover.classList)hover.classList.add('__fm-inspect-hover');
                  }
                  function click(e){
                    e.preventDefault();
                    e.stopPropagation();
                    clearHover();
                    const old=document.querySelector('.__fm-inspect-selected');
                    if(old)old.classList.remove('__fm-inspect-selected');
                    if(e.target&&e.target.classList)e.target.classList.add('__fm-inspect-selected');
                    window.__fmInspectLast=info(e.target);
                    console.log('[fm-inspect]',JSON.stringify(window.__fmInspectLast));
                    cleanup();
                  }
                  window.__fmInspectCleanup=cleanup;
                  document.addEventListener('mousemove',move,true);
                  document.addEventListener('click',click,true);
                  return 'installed';
                })();
                """, value -> Notify.show(R.string.web_home_extension_inspect_hint));
    }

    private void showScriptSelector() {
        List<WebHomeExtensionSourceStore.Entry> sources = editableCodeSources(true);
        if (sources.isEmpty()) sources = editableCodeSources(false);
        if (sources.isEmpty()) {
            Notify.show(R.string.web_home_extension_source_empty);
            return;
        }
        String[] labels = new String[sources.size()];
        int checked = 0;
        for (int i = 0; i < sources.size(); i++) {
            WebHomeExtensionSourceStore.Entry entry = sources.get(i);
            labels[i] = scriptLabel(entry);
            if (source != null && TextUtils.equals(source.getId(), entry.getId())) checked = i;
        }
        List<WebHomeExtensionSourceStore.Entry> options = sources;
        ChoiceDialog.showSingle(this, R.string.web_home_extension_select_script, labels, checked, which -> selectSource(options.get(which)));
    }

    private void selectSource(WebHomeExtensionSourceStore.Entry entry) {
        source = entry;
        binding.codeText.setText(source == null ? "GM_log('ready');\n" : WebHomeExtensionSourceStore.code(source));
        updateScriptSelectorText();
    }

    private void updateScriptSelectorText() {
        binding.scriptSelector.setText(source == null ? getString(R.string.web_home_extension_select_script) : scriptLabel(source));
    }

    private String scriptLabel(WebHomeExtensionSourceStore.Entry entry) {
        if (entry == null) return getString(R.string.web_home_extension_select_script);
        String name = entry.getName();
        return TextUtils.isEmpty(name) ? entry.getId() : name;
    }

    private void saveCodeSource() {
        String code = inputText(binding.codeText);
        if (TextUtils.isEmpty(code)) {
            Notify.show(R.string.web_home_extension_source_empty);
            return;
        }
        String id = source == null ? "" : source.getId();
        Site site = VodConfig.get().getHome();
        String siteKey = site == null ? "" : site.getKey();
        List<String> before = sourceIds();
        if (!Setting.isWebHomeExtension()) Setting.putWebHomeExtension(true);
        WebHomeExtensionSourceStore.saveCodeMeta(id, source == null ? getString(R.string.web_home_extension_local_code_default, WebHomeExtensionSourceStore.list().size() + 1) : source.getName(), runAt(source), matchText(source), code, true, source == null ? siteKey : source.getSiteKey(), WebHomeExtensionSourceStore.sourceType(source));
        source = TextUtils.isEmpty(id) ? newCodeSource(before) : codeSource(id);
        if (source == null) source = firstCodeSource();
        updateScriptSelectorText();
        if (source != null) WebHomeExtensionRegistry.get().setExtensionEnabled(source.getId(), true);
        appendConsole("EXT source saved, extension enabled, reload requested");
        WebHomeExtensionRegistry.get().clear();
        if (controller != null) controller.reloadExtensions();
        Notify.show(R.string.web_home_extension_source_saved);
    }

    private void refreshPanel() {
        if (binding.tabConsole.isChecked()) refreshConsole();
        else if (binding.tabElements.isChecked()) refreshElements();
        else if (binding.tabNetwork.isChecked()) refreshNetwork();
    }

    private void clearPanel() {
        if (binding.tabConsole.isChecked()) {
            consoleLines.clear();
            refreshConsole();
        } else if (binding.tabNetwork.isChecked()) {
            networkEntries.clear();
            selectedNetworkId = 0;
            refreshNetwork();
        } else if (binding.tabElements.isChecked()) {
            binding.elementsText.setText("");
        }
    }

    private void refreshConsole() {
        StringBuilder builder = new StringBuilder();
        for (String line : consoleLines) builder.append(line).append('\n');
        binding.consoleText.setText(builder.toString());
    }

    private void refreshNetwork() {
        List<NetworkEntry> visible = new ArrayList<>();
        for (NetworkEntry entry : networkEntries) {
            if (!matchesFilter(entry)) continue;
            visible.add(entry);
        }
        if (selectedNetworkId != 0 && findNetwork(selectedNetworkId, visible) == null) selectedNetworkId = visible.isEmpty() ? 0 : visible.get(0).id;
        if (selectedNetworkId == 0 && !visible.isEmpty()) selectedNetworkId = visible.get(0).id;
        binding.networkRows.removeAllViews();
        binding.networkRows.addView(networkRow(null, false));
        if (visible.isEmpty()) {
            MaterialTextView empty = networkText(getString(R.string.web_home_extension_network_empty), 14, Color.parseColor("#5F6368"), false);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(0, dp(22), 0, dp(22));
            binding.networkRows.addView(empty, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        } else {
            for (NetworkEntry entry : visible) binding.networkRows.addView(networkRow(entry, entry.id == selectedNetworkId));
        }
        refreshNetworkDetail();
    }

    private void refreshElements() {
        if (controller == null) return;
        controller.evaluate("""
                (function(){
                  const active=document.activeElement;
                  const path=function(el){
                    const arr=[];
                    for(let n=el;n&&n.nodeType===1&&arr.length<10;n=n.parentElement){
                      let s=n.tagName.toLowerCase();
                      if(n.id)s+='#'+n.id;
                      if(n.className&&typeof n.className==='string')s+='.'+n.className.trim().split(/\\s+/).slice(0,3).join('.');
                      arr.unshift(s);
                    }
                    return arr.join(' > ');
                  };
                  const htmlLength=(document.documentElement&&document.documentElement.outerHTML||'').length;
                  const nodes=Array.prototype.slice.call(document.querySelectorAll('body *'),0,220).map(function(n){
                    const rect=n.getBoundingClientRect();
                    const depth=(function(el){let d=0;for(let p=el.parentElement;p&&d<20;p=p.parentElement)d++;return d;})(n);
                    const attrs=[];
                    for(let i=0;i<n.attributes.length&&i<8;i++){
                      const a=n.attributes[i];
                      if(a.name==='class'||a.name==='id')continue;
                      attrs.push(a.name+'="'+String(a.value).slice(0,120)+'"');
                    }
                    return {
                      depth:depth,
                      tag:n.tagName.toLowerCase(),
                      id:n.id||'',
                      className:typeof n.className==='string'?n.className:'',
                      attrs:attrs.join(' '),
                      text:(n.innerText||n.textContent||'').trim().replace(/\\s+/g,' ').slice(0,160),
                      x:Math.round(rect.left),y:Math.round(rect.top),w:Math.round(rect.width),h:Math.round(rect.height)
                    };
                  }).filter(function(n){return n.w>0&&n.h>0;}).slice(0,120);
                  return JSON.stringify({
                    title:document.title,
                    url:location.href,
                    readyState:document.readyState,
                    active:active?path(active):'',
                    selected:window.__fmInspectLast||null,
                    bodyTextPreview:(document.body&&document.body.innerText||'').trim().replace(/\\s+/g,' ').slice(0,1200),
                    htmlLength:htmlLength,
                    totalElements:document.querySelectorAll('body *').length,
                    elements:nodes
                  },null,2);
                })();
                """, value -> {
                    if (binding != null) binding.elementsText.setText(formatElements(value));
                });
    }

    private String formatElements(String value) {
        StringBuilder builder = new StringBuilder();
        builder.append("Elements\n\n");
        try {
            JsonObject object = Json.parse(unquote(value)).getAsJsonObject();
            builder.append("Title: ").append(safe(object, "title")).append('\n');
            builder.append("URL: ").append(safe(object, "url")).append('\n');
            builder.append("Ready: ").append(safe(object, "readyState")).append('\n');
            builder.append("Active: ").append(safe(object, "active")).append("\n\n");
            builder.append("HTML chars: ").append(safe(object, "htmlLength")).append('\n');
            builder.append("Elements total: ").append(safe(object, "totalElements")).append('\n');
            builder.append("Showing visible nodes: max 120. Use Inspect Element to view a node snippet.\n\n");
            if (object.has("selected") && object.get("selected").isJsonObject()) {
                JsonObject selected = object.getAsJsonObject("selected");
                builder.append("Selected\n");
                builder.append(safe(selected, "path")).append('\n');
                builder.append("box: ").append(safe(selected, "x")).append(',').append(safe(selected, "y")).append(' ')
                        .append(safe(selected, "w")).append('x').append(safe(selected, "h")).append('\n');
                builder.append("text: ").append(safe(selected, "text")).append("\n\n");
                builder.append("html: ").append(safe(selected, "html")).append("\n\n");
            }
            builder.append("DOM\n");
            JsonArray elements = object.getAsJsonArray("elements");
            if (elements != null) for (JsonElement element : elements) appendElement(builder, element.getAsJsonObject());
            builder.append("\nBody Text Preview\n").append(safe(object, "bodyTextPreview")).append('\n');
            return builder.toString();
        } catch (Throwable e) {
            return builder.append(unquote(value)).toString();
        }
    }

    private void appendElement(StringBuilder builder, JsonObject object) {
        int depth = parseInt(safe(object, "depth"));
        for (int i = 0; i < Math.max(0, depth - 1); i++) builder.append("  ");
        builder.append('<').append(safe(object, "tag"));
        if (!TextUtils.isEmpty(safe(object, "id"))) builder.append(" id=\"").append(safe(object, "id")).append('"');
        if (!TextUtils.isEmpty(safe(object, "className"))) builder.append(" class=\"").append(safe(object, "className")).append('"');
        if (!TextUtils.isEmpty(safe(object, "attrs"))) builder.append(' ').append(safe(object, "attrs"));
        builder.append('>');
        if (!TextUtils.isEmpty(safe(object, "text"))) builder.append("  ").append(safe(object, "text"));
        builder.append("  [").append(safe(object, "x")).append(',').append(safe(object, "y")).append(' ')
                .append(safe(object, "w")).append('x').append(safe(object, "h")).append("]\n");
    }

    private String safe(JsonObject object, String key) {
        try {
            JsonElement element = object.get(key);
            return element == null || element.isJsonNull() ? "" : element.getAsString();
        } catch (Throwable e) {
            return "";
        }
    }

    private String clip(String value, int limit) {
        if (value == null) return "";
        return value.length() <= limit ? value : value.substring(0, limit) + "\n...truncated " + (value.length() - limit) + " chars";
    }

    private int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Throwable e) {
            return 0;
        }
    }

    private String unquote(String value) {
        if (TextUtils.isEmpty(value)) return "";
        try {
            JsonElement element = Json.parse(value);
            return element.isJsonPrimitive() ? element.getAsString() : value;
        } catch (Throwable e) {
            return value;
        }
    }

    private void appendConsole(String line) {
        runOnUi(() -> {
            consoleLines.add(now() + " " + line);
            trim(consoleLines);
            if (line.contains("[fm-inspect]")) {
                binding.tabGroup.check(R.id.tabElements);
                refreshElements();
            } else if (binding != null && binding.tabConsole.isChecked()) {
                refreshConsole();
            }
        });
    }

    private void appendNetwork(NetworkEntry entry) {
        runOnUi(() -> {
            NetworkEntry existing = findPending(entry);
            if (existing == null) networkEntries.add(entry);
            else existing.update(entry);
            while (networkEntries.size() > 500) networkEntries.remove(0);
            if (selectedNetworkId == 0) selectedNetworkId = existing == null ? entry.id : existing.id;
            if (binding != null && binding.tabNetwork.isChecked()) refreshNetwork();
        });
    }

    private void runOnUi(Runnable action) {
        if (binding == null) return;
        binding.root.post(action);
    }

    private void trim(List<String> lines) {
        while (lines.size() > 300) lines.remove(0);
    }

    private String now() {
        return timeFormat.format(new Date());
    }

    private WebHomeExtensionSourceStore.Entry firstCodeSource() {
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.list()) if (WebHomeExtensionSourceStore.isCodeSource(entry)) return entry;
        return null;
    }

    private WebHomeExtensionSourceStore.Entry firstMatchedCodeSource() {
        List<WebHomeExtensionSourceStore.Entry> sources = editableCodeSources(true);
        if (!sources.isEmpty()) return sources.get(0);
        return firstCodeSource();
    }

    private List<WebHomeExtensionSourceStore.Entry> editableCodeSources(boolean matchedOnly) {
        List<WebHomeExtensionSourceStore.Entry> result = new ArrayList<>();
        Site site = VodConfig.get().getHome();
        String siteKey = site == null ? "" : site.getKey();
        Set<String> matchedIds = matchedOnly ? matchedExtensionIds() : new HashSet<>();
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.list()) {
            if (!WebHomeExtensionSourceStore.isCodeSource(entry)) continue;
            if (matchedOnly && !matchedIds.isEmpty() && !matchedIds.contains(entry.getId())) continue;
            if (matchedOnly && matchedIds.isEmpty() && !entry.matches(siteKey)) continue;
            result.add(entry);
        }
        return result;
    }

    private Set<String> matchedExtensionIds() {
        Set<String> ids = new HashSet<>();
        for (WebHomeExtensionRegistry.Item item : WebHomeExtensionRegistry.get().snapshot().items) {
            if (item == null || TextUtils.isEmpty(item.id)) continue;
            if ("matched".equals(item.status) || "ready".equals(item.status) || "injected".equals(item.status)) ids.add(item.id);
        }
        return ids;
    }

    private WebHomeExtensionSourceStore.Entry codeSource(String id) {
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.list()) {
            if (TextUtils.equals(entry.getId(), id) && WebHomeExtensionSourceStore.isCodeSource(entry)) return entry;
        }
        return null;
    }

    private WebHomeExtensionSourceStore.Entry newCodeSource(List<String> before) {
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.list()) {
            if (!WebHomeExtensionSourceStore.isCodeSource(entry)) continue;
            if (!before.contains(entry.getId())) return entry;
        }
        return null;
    }

    private List<String> sourceIds() {
        List<String> ids = new ArrayList<>();
        for (WebHomeExtensionSourceStore.Entry entry : WebHomeExtensionSourceStore.list()) ids.add(entry.getId());
        return ids;
    }

    private String runAt(WebHomeExtensionSourceStore.Entry entry) {
        try {
            JsonElement element = WebHomeExtensionSourceStore.parse(entry.getRaw());
            if (!element.isJsonObject()) return "";
            JsonObject object = element.getAsJsonObject();
            if (!object.has("runAt") || !object.get("runAt").isJsonPrimitive()) return "";
            return object.get("runAt").getAsString();
        } catch (Throwable e) {
            return "";
        }
    }

    private String matchText(WebHomeExtensionSourceStore.Entry entry) {
        try {
            JsonElement element = WebHomeExtensionSourceStore.parse(entry.getRaw());
            if (!element.isJsonObject()) return "";
            JsonObject object = element.getAsJsonObject();
            if (!object.has("cspKeyRegex")) return "";
            JsonElement match = object.get("cspKeyRegex");
            if (match.isJsonPrimitive()) return match.getAsString();
            if (!match.isJsonArray()) return "";
            List<String> values = new ArrayList<>();
            for (JsonElement item : match.getAsJsonArray()) if (item.isJsonPrimitive()) values.add(item.getAsString());
            return TextUtils.join("\n", values);
        } catch (Throwable e) {
            return "";
        }
    }

    private void setupScrollableText(EditText input) {
        input.setSelectAllOnFocus(false);
        input.setHorizontallyScrolling(true);
        input.setHorizontalScrollBarEnabled(false);
        input.setVerticalScrollBarEnabled(false);
        input.setOverScrollMode(View.OVER_SCROLL_NEVER);
        input.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) view.post(() -> disallowParentIntercept(view, false));
            else disallowParentIntercept(view, true);
            return false;
        });
    }

    private void changeDetailHeight(int deltaDp) {
        ViewGroup.LayoutParams params = binding.networkDetailFrame.getLayoutParams();
        int current = params.height <= 0 ? dp(128) : params.height;
        params.height = Math.max(dp(DETAIL_MIN_DP), Math.min(dp(DETAIL_MAX_DP), current + dp(deltaDp)));
        binding.networkDetailFrame.setLayoutParams(params);
    }

    private void disallowParentIntercept(View view, boolean disallow) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
            parent = parent.getParent();
        }
    }

    private String inputText(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    @Override
    public void onWebLoading() {
        appendConsole("PAGE loading");
    }

    @Override
    public void onWebReady() {
        appendConsole("PAGE ready");
        refreshPanel();
    }

    @Override
    public void onWebError() {
        appendConsole("PAGE error");
        refreshPanel();
    }

    @Override
    public void onWebConsole(String line) {
        appendConsole(line);
    }

    @Override
    public void onWebRequest(String method, String url, boolean mainFrame) {
        appendNetwork(new NetworkEntry("RESOURCE", method, url, 0, 0, mainFrame ? "main frame" : "subresource", ""));
    }

    @Override
    public void onWebRequest(String method, String url, boolean mainFrame, Map<String, String> headers) {
        appendNetwork(new NetworkEntry("RESOURCE", method, url, 0, 0, mainFrame ? "main frame" : "subresource", headerText(headers)));
    }

    @Override
    public void onWebNetwork(String type, String method, String url, int status, long durationMs, String detail) {
        appendNetwork(new NetworkEntry(type, method, url, status, durationMs, detail, ""));
    }

    private void refreshNetworkDetail() {
        NetworkEntry selected = findNetwork(selectedNetworkId, networkEntries);
        if (selected == null) {
            binding.networkDetail.setText(R.string.web_home_extension_network_select);
            return;
        }
        StringBuilder builder = new StringBuilder();
        builder.append("General\n");
        builder.append("URL: ").append(selected.url).append('\n');
        builder.append("Type: ").append(selected.displayKind()).append('\n');
        builder.append("Raw type: ").append(selected.kind).append('\n');
        builder.append("Method: ").append(selected.method).append('\n');
        builder.append("State: ").append(selected.stateText()).append('\n');
        builder.append("Status: ").append(selected.status <= 0 ? "-" : selected.status).append('\n');
        builder.append("Duration: ").append(selected.durationMs <= 0 ? "-" : selected.durationMs + "ms").append('\n');
        builder.append("Time: ").append(selected.time).append("\n\n");
        builder.append("Detail\n").append(TextUtils.isEmpty(selected.detail) ? "-" : selected.detail).append("\n\n");
        builder.append("Request Headers\n").append(TextUtils.isEmpty(selected.headers) ? "-" : selected.headers);
        binding.networkDetail.setText(builder.toString());
    }

    private String filter(int checkedId) {
        if (checkedId == R.id.filterWeb) return "RESOURCE";
        if (checkedId == R.id.filterScript) return "SCRIPT";
        if (checkedId == R.id.filterError) return "ERROR";
        return "ALL";
    }

    private boolean matchesFilter(NetworkEntry entry) {
        if ("ERROR".equals(networkFilter)) {
            if (!entry.isError()) return false;
        } else if ("SCRIPT".equals(networkFilter)) {
            if (!entry.isScript()) return false;
        } else if (!"ALL".equals(networkFilter) && !entry.kind.startsWith(networkFilter)) {
            return false;
        }
        return matchesSearch(entry);
    }

    private boolean matchesSearch(NetworkEntry entry) {
        String query = inputText(binding.networkSearch).toLowerCase(Locale.ROOT);
        if (TextUtils.isEmpty(query)) return true;
        return entry.url.toLowerCase(Locale.ROOT).contains(query)
                || entry.method.toLowerCase(Locale.ROOT).contains(query)
                || entry.kind.toLowerCase(Locale.ROOT).contains(query)
                || entry.stateText().toLowerCase(Locale.ROOT).contains(query)
                || String.valueOf(entry.status).contains(query)
                || entry.detail.toLowerCase(Locale.ROOT).contains(query)
                || entry.headers.toLowerCase(Locale.ROOT).contains(query);
    }

    private NetworkEntry findNetwork(int id, List<NetworkEntry> entries) {
        for (NetworkEntry entry : entries) if (entry.id == id) return entry;
        return null;
    }

    private NetworkEntry findPending(NetworkEntry incoming) {
        if (incoming.isStart() || "RESOURCE".equals(incoming.kind)) return null;
        for (int i = networkEntries.size() - 1; i >= 0; i--) {
            NetworkEntry entry = networkEntries.get(i);
            if (!entry.isPending()) continue;
            if (!entry.sameRequest(incoming)) continue;
            return entry;
        }
        return null;
    }

    private View networkRow(NetworkEntry entry, boolean selected) {
        LinearLayoutCompat row = new LinearLayoutCompat(requireContext());
        row.setOrientation(LinearLayoutCompat.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8), dp(6), dp(8), dp(6));
        row.setBackgroundColor(entry == null ? Color.parseColor("#F5F6F7") : selected ? Color.parseColor("#DCEBFF") : entry.isError() ? Color.parseColor("#FFF1F1") : Color.WHITE);
        row.addView(networkCell(entry == null ? "#" : String.valueOf(entry.id), 46, color(entry), entry == null));
        row.addView(networkCell(entry == null ? "Time" : entry.time, 104, color(entry), entry == null));
        row.addView(networkCell(entry == null ? "Type" : entry.displayKind(), 82, color(entry), entry == null));
        row.addView(networkCell(entry == null ? "Method" : entry.method, 76, color(entry), entry == null));
        row.addView(networkCell(entry == null ? "Status" : entry.statusText(), 80, statusColor(entry), entry == null));
        row.addView(networkCell(entry == null ? "Cost" : entry.durationText(), 84, color(entry), entry == null));
        row.addView(networkUrlCell(entry == null ? "URL" : entry.url, entry == null));
        if (entry != null) {
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnClickListener(view -> {
                selectedNetworkId = entry.id;
                refreshNetwork();
            });
        }
        return row;
    }

    private MaterialTextView networkCell(String value, int width, int color, boolean header) {
        MaterialTextView view = networkText(value, 12, color, header);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(dp(width), ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setLayoutParams(params);
        return view;
    }

    private MaterialTextView networkUrlCell(String value, boolean header) {
        MaterialTextView view = networkText(value, 12, header ? Color.BLACK : Color.parseColor("#174EA6"), header);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        view.setMinWidth(dp(620));
        view.setLayoutParams(params);
        return view;
    }

    private MaterialTextView networkText(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setSingleLine(true);
        view.setIncludeFontPadding(false);
        return view;
    }

    private int color(NetworkEntry entry) {
        if (entry == null) return Color.BLACK;
        if (entry.isError()) return Color.parseColor("#B3261E");
        if (entry.isPending()) return Color.parseColor("#5F6368");
        return Color.parseColor("#202124");
    }

    private int statusColor(NetworkEntry entry) {
        if (entry == null) return Color.BLACK;
        if (entry.isError()) return Color.parseColor("#B3261E");
        if (entry.status >= 200 && entry.status < 400) return Color.parseColor("#137333");
        return color(entry);
    }

    private String headerText(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return "";
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (count++ > 0) builder.append('\n');
            builder.append(entry.getKey()).append(':').append(entry.getValue());
        }
        return builder.toString();
    }

    private static int nextNetworkId;

    private class NetworkEntry {
        private final int id;
        private final String time;
        private final String kind;
        private final String method;
        private final String url;
        private String state;
        private String detail;
        private String headers;
        private int status;
        private long durationMs;

        private NetworkEntry(String type, String method, String url, int status, long durationMs, String detail, String headers) {
            this.id = ++nextNetworkId;
            this.time = now();
            this.kind = kind(type);
            this.method = TextUtils.isEmpty(method) ? "GET" : method;
            this.url = url == null ? "" : url;
            this.state = state(type);
            this.status = status;
            this.durationMs = durationMs;
            this.detail = detail == null ? "" : detail;
            this.headers = headers == null ? "" : headers;
        }

        private boolean isError() {
            return "ERROR".equals(state) || status >= 400;
        }

        private boolean isScript() {
            return "FETCH".equals(kind) || "XHR".equals(kind) || "NATIVE".equals(kind);
        }

        private String displayKind() {
            return isScript() ? "SCRIPT" : kind;
        }

        private boolean isStart() {
            return "START".equals(state);
        }

        private boolean isPending() {
            return isStart() && status <= 0;
        }

        private boolean sameRequest(NetworkEntry incoming) {
            return kind.equals(incoming.kind) && method.equals(incoming.method) && url.equals(incoming.url);
        }

        private void update(NetworkEntry incoming) {
            state = incoming.state;
            status = incoming.status;
            durationMs = incoming.durationMs;
            detail = mergeDetail(detail, incoming.detail);
            if (!TextUtils.isEmpty(incoming.headers)) headers = incoming.headers;
        }

        private String statusText() {
            if (status > 0) return String.valueOf(status);
            if (isPending()) return "pending";
            return "-";
        }

        private String durationText() {
            return durationMs <= 0 ? "-" : durationMs + "ms";
        }

        private String stateText() {
            return TextUtils.isEmpty(state) ? "-" : state;
        }

        private String kind(String type) {
            if (TextUtils.isEmpty(type)) return "-";
            int index = type.indexOf('_');
            return index < 0 ? type : type.substring(0, index);
        }

        private String state(String type) {
            if (TextUtils.isEmpty(type)) return "";
            int index = type.indexOf('_');
            return index < 0 ? "" : type.substring(index + 1);
        }

        private String mergeDetail(String current, String incoming) {
            if (TextUtils.isEmpty(incoming)) return current == null ? "" : current;
            if (TextUtils.isEmpty(current)) return incoming;
            if (current.contains(incoming)) return current;
            return current + "\n\n" + incoming;
        }
    }
}
