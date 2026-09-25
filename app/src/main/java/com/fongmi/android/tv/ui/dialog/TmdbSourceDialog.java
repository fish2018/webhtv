package com.fongmi.android.tv.ui.dialog;

import android.content.Context;
import android.graphics.Rect;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.service.TmdbConfigTestService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.TmdbProxy;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TMDB 元数据增强配置弹窗。
 *
 * 仿照 {@link ShortDramaSourceDialog} 的交互：
 * - 配置 TMDB API Key（或 v4 Access Token）
 * - 通过 Chip 标签维护启用站点规则与排除站点黑名单
 * 点"确定"才统一保存。
 */
public class TmdbSourceDialog {

    private final FragmentActivity activity;
    private Context dialogContext;
    private AlertDialog dialog;
    private ChipGroup enabledChips;
    private ChipGroup disabledChips;
    private TextView disabledLabel;
    private EditText apiKeyInput;
    private EditText languageInput;
    private MaterialAutoCompleteTextView apiHostInput;
    private MaterialAutoCompleteTextView imageHostInput;
    private EditText omdbApiKeyInput;
    private Runnable onDismiss;

    private List<String> tempEnabledRules;
    private List<String> tempDisabledSites;
    private List<String> tempAllowedSites;
    private Runnable routeFocusPicker;

    public static TmdbSourceDialog create(FragmentActivity activity) {
        return new TmdbSourceDialog(activity);
    }

    private TmdbSourceDialog(FragmentActivity activity) {
        this.activity = activity;
    }

    public TmdbSourceDialog onDismiss(Runnable callback) {
        this.onDismiss = callback;
        return this;
    }

    public void show() {
        MaterialAlertDialogBuilder builder = builder();
        dialogContext = builder.getContext();
        View view = LayoutInflater.from(dialogContext).inflate(R.layout.dialog_tmdb_source, null);
        enabledChips = view.findViewById(R.id.enabledChips);
        disabledChips = view.findViewById(R.id.disabledChips);
        disabledLabel = view.findViewById(R.id.disabledLabel);
        apiKeyInput = view.findViewById(R.id.apiKeyInput);
        languageInput = view.findViewById(R.id.languageInput);
        apiHostInput = view.findViewById(R.id.apiHostInput);
        setupRouteDropdown(apiHostInput, apiOptionLabels(), activity.getString(R.string.dialog_tmdb_api_host_label));
        imageHostInput = view.findViewById(R.id.imageHostInput);
        setupRouteDropdown(imageHostInput, imageOptionLabels(), activity.getString(R.string.dialog_tmdb_image_host_label));
        omdbApiKeyInput = view.findViewById(R.id.omdbApiKeyInput);
        EditText ruleInput = view.findViewById(R.id.ruleInput);
        EditText disabledRuleInput = view.findViewById(R.id.disabledRuleInput);
        TextView addBtn = view.findViewById(R.id.add);
        TextView addDisabledBtn = view.findViewById(R.id.addDisabled);
        TextView manageBtn = view.findViewById(R.id.manage);
        TextView testBtn = view.findViewById(R.id.testConfig);
        TextView resetBtn = view.findViewById(R.id.resetDefault);
        addBtn.setText(R.string.dialog_tmdb_add);
        addDisabledBtn.setText(R.string.dialog_tmdb_add);
        manageBtn.setText(R.string.dialog_tmdb_site_manage);
        testBtn.setText(R.string.dialog_tmdb_test_config);
        resetBtn.setText(R.string.dialog_tmdb_reset_default);
        addBtn.setAllCaps(false);
        addDisabledBtn.setAllCaps(false);
        manageBtn.setAllCaps(false);
        testBtn.setAllCaps(false);
        resetBtn.setAllCaps(false);

        TmdbConfig config = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        tempEnabledRules = new ArrayList<>(config.getEnabledSites());
        tempDisabledSites = new ArrayList<>(config.getDisabledSites());
        tempAllowedSites = new ArrayList<>(config.getAllowedSites());
        apiKeyInput.setText(TextUtils.isEmpty(config.getAccessToken()) ? config.getApiKey() : config.getAccessToken());
        languageInput.setText(config.getLanguage());
        apiHostInput.setText(apiDisplayFor(config), false);
        imageHostInput.setText(imageDisplayFor(config), false);
        omdbApiKeyInput.setText(config.getOmdbApiKey());
        updateChipsDisplay();

        addBtn.setOnClickListener(v -> addRule(ruleInput));
        addDisabledBtn.setOnClickListener(v -> addDisabledRule(disabledRuleInput));
        ruleInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addRule(ruleInput);
                return true;
            }
            return false;
        });
        disabledRuleInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addDisabledRule(disabledRuleInput);
                return true;
            }
            return false;
        });
        manageBtn.setOnClickListener(v -> showSiteManage());
        testBtn.setOnClickListener(v -> testConfig(testBtn));
        resetBtn.setOnClickListener(v -> resetToDefault());

        dialog = builder
                .setTitle(R.string.setting_tmdb_source)
                .setView(view)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> onSave())
                .setNegativeButton(R.string.dialog_negative, null)
                .setOnDismissListener(d -> {
                    clearRouteFocusPickers();
                    if (onDismiss != null) onDismiss.run();
                })
                .create();
        dialog.show();
        wireConfigDialogFocus(dialog, ruleInput, addBtn, disabledRuleInput, addDisabledBtn, manageBtn, resetBtn);
        LightDialog.apply(dialog);
    }

    private void clearRouteFocusPickers() {
        clearRouteFocusPicker(apiHostInput);
        clearRouteFocusPicker(imageHostInput);
    }

    private void clearRouteFocusPicker(MaterialAutoCompleteTextView input) {
        if (input == null || routeFocusPicker == null) return;
        input.removeCallbacks(routeFocusPicker);
    }

    private void testConfig(View testButton) {
        String credential = inputText(apiKeyInput);
        String apiHost = apiValueFor(inputText(apiHostInput));
        String imageHost = imageValueFor(inputText(imageHostInput));
        String omdbApiKey = inputText(omdbApiKeyInput);
        AlertDialog sourceDialog = dialog;
        testButton.setEnabled(false);
        Task.execute(() -> {
            TmdbConfigTestService.Result result = TmdbConfigTestService.test(credential, apiHost, imageHost, "", omdbApiKey);
            activity.runOnUiThread(() -> {
                testButton.setEnabled(true);
                if (activity.isFinishing() || activity.isDestroyed() || sourceDialog == null
                        || dialog != sourceDialog || !sourceDialog.isShowing()) return;
                String apiResult = resultText(result.api, R.string.dialog_tmdb_test_api_success, R.string.dialog_tmdb_test_api_failed);
                String imageResult = resultText(result.image, R.string.dialog_tmdb_test_image_success, R.string.dialog_tmdb_test_image_failed);
                String omdbResult = resultText(result.omdb, R.string.dialog_tmdb_test_omdb_success, R.string.dialog_tmdb_test_omdb_failed);
                new MaterialAlertDialogBuilder(dialogContext, R.style.Theme_WebHTV_LightDialog)
                        .setTitle(R.string.dialog_tmdb_test_result_title)
                        .setMessage(apiResult + "\n" + imageResult + "\n" + omdbResult)
                        .setPositiveButton(R.string.dialog_positive, null)
                        .show();
            });
        });
    }

    private String resultText(TmdbConfigTestService.Check check, int successText, int failureText) {
        return check.success
                ? activity.getString(successText, check.latencyMillis)
                : activity.getString(failureText, check.message, check.latencyMillis);
    }

    private static String inputText(EditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void setupRouteDropdown(MaterialAutoCompleteTextView input, String[] labels, String title) {
        input.setKeyListener(null);
        input.setAdapter(null);
        input.setOnClickListener(v -> showRoutePicker(input, labels, title));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) return;
            input.removeCallbacks(routeFocusPicker);
            routeFocusPicker = () -> {
                if (input.hasFocus() && !activity.isFinishing() && !activity.isDestroyed()) {
                    showRoutePicker(input, labels, title);
                }
            };
            input.post(routeFocusPicker);
        });
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                input.removeCallbacks(routeFocusPicker);
                showRoutePicker(input, labels, title);
                return true;
            }
            return false;
        });
    }

    private void wireRouteDpadFocus(MaterialAutoCompleteTextView input, String[] labels, String title, View up, View down) {
        input.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                input.removeCallbacks(routeFocusPicker);
                showRoutePicker(input, labels, title);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            return false;
        });
    }

    private void showRoutePicker(MaterialAutoCompleteTextView input, String[] labels, String title) {
        clearRouteFocusPickers();
        String current = inputText(input);
        int checked = -1;
        for (int i = 0; i < labels.length; i++) if (labels[i].equals(current)) checked = i;
        AlertDialog picker = new MaterialAlertDialogBuilder(dialogContext, R.style.Theme_WebHTV_LightDialog)
                .setTitle(title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    input.setText(labels[which], false);
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
        LightDialog.apply(picker);
    }

    private String[] apiOptionLabels() {
        return new String[]{
                activity.getString(R.string.dialog_tmdb_api_auto),
                activity.getString(R.string.dialog_tmdb_api_direct),
                activity.getString(R.string.dialog_tmdb_api_itv666)
        };
    }

    private String[] imageOptionLabels() {
        return new String[]{
                activity.getString(R.string.dialog_tmdb_image_auto),
                activity.getString(R.string.dialog_tmdb_image_direct),
                activity.getString(R.string.dialog_tmdb_image_itv666),
                activity.getString(R.string.dialog_tmdb_image_wsrv)
        };
    }

    private String apiDisplayFor(TmdbConfig config) {
        if (config != null && (config.isApiAuto() || config.isApiRouteDefault())) return activity.getString(R.string.dialog_tmdb_api_auto);
        String value = config == null ? TmdbProxy.OFFICIAL_API : config.getApiHost();
        return routeDisplay(value, TmdbProxy.apiValues(), apiOptionLabels());
    }

    private String imageDisplayFor(TmdbConfig config) {
        if (config != null && (config.isImageAuto() || config.isImageRouteDefault())) return activity.getString(R.string.dialog_tmdb_image_auto);
        String value = config == null ? TmdbProxy.OFFICIAL_IMAGE : config.getConfiguredImageBase();
        return routeDisplayImage(value, imageOptionLabels());
    }

    private String apiValueFor(String value) {
        return routeValue(value, TmdbProxy.apiValues(), apiOptionLabels(), TmdbProxy.OFFICIAL_API);
    }

    private String imageValueFor(String value) {
        String text = value == null ? "" : value.trim();
        String[] labels = imageOptionLabels();
        if (labels[0].equals(text)) return TmdbProxy.AUTO;
        if (labels[1].equals(text)) return TmdbProxy.OFFICIAL_IMAGE;
        if (labels[2].equals(text)) return TmdbProxy.ITV666;
        if (labels[3].equals(text)) return TmdbProxy.WSRV_IMAGE;
        String normalized = TmdbProxy.normalizeImageConfig(text);
        return TextUtils.isEmpty(normalized) ? TmdbProxy.OFFICIAL_IMAGE : normalized;
    }

    private String routeDisplay(String value, List<String> values, String[] labels) {
        String normalized = TmdbProxy.normalizeConfig(value);
        for (int i = 0; i < values.size() && i < labels.length; i++) {
            if (values.get(i).equals(normalized)) return labels[i];
        }
        return normalized;
    }

    private String routeDisplayImage(String value, String[] labels) {
        String normalized = TmdbProxy.normalizeImageConfig(value);
        List<String> values = TmdbProxy.imageValues();
        for (int i = 0; i < values.size() && i < labels.length; i++) {
            if (values.get(i).equals(normalized)) return labels[i];
        }
        return normalized;
    }

    private String routeValue(String value, List<String> values, String[] labels, String fallback) {
        String text = value == null ? "" : value.trim();
        for (int i = 0; i < values.size() && i < labels.length; i++) {
            if (labels[i].equals(text)) return values.get(i);
        }
        String normalized = TmdbProxy.normalizeConfig(text);
        return TextUtils.isEmpty(normalized) ? fallback : normalized;
    }

    private MaterialAlertDialogBuilder builder() {
        return new MaterialAlertDialogBuilder(activity, R.style.Theme_WebHTV_LightDialog);
    }

    private void wireConfigDialogFocus(AlertDialog dialog, EditText ruleInput, View addBtn, EditText disabledRuleInput, View addDisabledBtn, View manageBtn, View resetBtn) {
        wireConfigDialogFocus(dialog, ruleInput, addBtn, disabledRuleInput, addDisabledBtn, manageBtn, dialog.findViewById(R.id.testConfig), resetBtn);
    }

    private void wireConfigDialogFocus(AlertDialog dialog, EditText ruleInput, View addBtn, EditText disabledRuleInput, View addDisabledBtn, View manageBtn, View testBtn, View resetBtn) {
        View positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        View negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
        wireTextDpadFocus(apiKeyInput, null, languageInput, null, null);
        wireTextDpadFocus(languageInput, apiKeyInput, apiHostInput, null, null);
        wireRouteDpadFocus(apiHostInput, apiOptionLabels(),
                activity.getString(R.string.dialog_tmdb_api_host_label), languageInput, imageHostInput);
        wireRouteDpadFocus(imageHostInput, imageOptionLabels(),
                activity.getString(R.string.dialog_tmdb_image_host_label), apiHostInput, omdbApiKeyInput);
        wireTextDpadFocus(omdbApiKeyInput, imageHostInput, ruleInput, null, null);
        wireTextDpadFocus(ruleInput, omdbApiKeyInput, disabledRuleInput, null, addBtn);
        wireDpadFocus(addBtn, omdbApiKeyInput, addDisabledBtn, ruleInput, null);
        wireTextDpadFocus(disabledRuleInput, ruleInput, manageBtn, null, addDisabledBtn);
        wireDpadFocus(addDisabledBtn, addBtn, testBtn, disabledRuleInput, null);
        wireDpadFocus(manageBtn, disabledRuleInput, positive, null, testBtn);
        wireDpadFocus(testBtn, addDisabledBtn, positive, manageBtn, resetBtn);
        wireDpadFocus(resetBtn, addDisabledBtn, positive, testBtn, null);
        wireDpadFocus(negative, manageBtn, null, null, positive);
        wireDpadFocus(positive, testBtn, null, negative, null);
    }

    private static void wireDpadFocus(View view, View up, View down, View left, View right) {
        if (view == null) return;
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null) return requestFocus(right);
            return false;
        });
    }

    private static void wireTextDpadFocus(EditText view, View up, View down, View left, View right) {
        if (view == null) return;
        view.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP && up != null) return requestFocus(up);
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN && down != null) return requestFocus(down);
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && left != null && isCursorAtStart(view)) return requestFocus(left);
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && right != null && isCursorAtEnd(view)) return requestFocus(right);
            return false;
        });
    }

    private static boolean requestFocus(View view) {
        if (view == null || view.getVisibility() != View.VISIBLE || !view.isEnabled()) return false;
        boolean focused = view.requestFocus();
        if (focused) {
            view.post(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), false));
        }
        return focused;
    }

    private static boolean isCursorAtStart(EditText view) {
        return Math.max(0, view.getSelectionStart()) <= 0;
    }

    private static boolean isCursorAtEnd(EditText view) {
        int length = view.getText() == null ? 0 : view.getText().length();
        return Math.max(view.getSelectionStart(), view.getSelectionEnd()) >= length;
    }

    private void onSave() {
        String apiKey = text(apiKeyInput);
        String language = text(languageInput);
        String apiHost = apiValueFor(text(apiHostInput));
        String imageHost = imageValueFor(text(imageHostInput));
        String omdbApiKey = text(omdbApiKeyInput);
        boolean isToken = apiKey.split("\\.").length >= 3;
        StringBuilder sb = new StringBuilder("{");
        if (isToken) sb.append("\"accessToken\":\"").append(escape(apiKey)).append("\",");
        else sb.append("\"apiKey\":\"").append(escape(apiKey)).append("\",");
        if (!TextUtils.isEmpty(language)) {
            sb.append("\"language\":\"").append(escape(language)).append("\",");
        }
        boolean apiAuto = TmdbProxy.isAuto(apiHost);
        boolean imageAuto = TmdbProxy.isAuto(imageHost);
        sb.append("\"apiBase\":\"").append(escape(apiAuto ? TmdbProxy.OFFICIAL_API : apiHost)).append("\",");
        sb.append("\"apiAuto\":").append(apiAuto).append(',');
        sb.append("\"apiRouteConfigured\":true,");
        sb.append("\"apiRouteMode\":\"").append(apiAuto ? "auto" : TmdbProxy.isOfficialApiHost(apiHost) ? "direct" : "custom").append("\",");
        sb.append("\"imageBase\":\"").append(escape(imageAuto ? TmdbProxy.OFFICIAL_IMAGE : imageHost)).append("\",");
        sb.append("\"imageAuto\":").append(imageAuto).append(',');
        sb.append("\"imageRouteConfigured\":true,");
        sb.append("\"imageRouteMode\":\"").append(imageAuto ? "auto" : TmdbProxy.isOfficialImageHost(imageHost) ? "direct" : "custom").append("\",");
        if (!TextUtils.isEmpty(omdbApiKey)) {
            sb.append("\"omdbApiKey\":\"").append(escape(omdbApiKey)).append("\",");
        }
        sb.append("\"excludeKeywordsConfigured\":true,");
        sb.append("\"enabledSites\":").append(toJsonArray(tempEnabledRules)).append(',');
        sb.append("\"allowedSites\":").append(toJsonArray(tempAllowedSites)).append(',');
        sb.append("\"disabledSites\":").append(toJsonArray(tempDisabledSites));
        sb.append('}');
        String savedConfig = TmdbConfig.objectFrom(sb.toString()).toJson();
        Setting.putTmdbConfig(savedConfig);
        if (apiAuto || imageAuto) {
            String warmupApi = apiAuto ? TmdbProxy.AUTO : apiHost;
            String warmupImage = imageAuto ? TmdbProxy.AUTO : imageHost;
            Task.execute(() -> TmdbConfigTestService.test(apiKey, warmupApi, warmupImage, "", ""));
        }
    }

    private static String text(EditText input) {
        return input == null || input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void showSiteManage() {
        List<Site> sites = VodConfig.get().getSites().stream().filter(s -> s != null && !s.isEmpty()).toList();
        if (sites.isEmpty()) return;

        List<String> enabledRules = new ArrayList<>(tempEnabledRules);
        List<String> disabledSites = new ArrayList<>(tempDisabledSites);
        List<String> allowedSites = new ArrayList<>(tempAllowedSites);
        boolean enableAll = enabledRules.isEmpty();

        String[] labels = new String[sites.size()];
        boolean[] checked = new boolean[sites.size()];

        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            labels[i] = site.getDisplayName() + "  " + site.getKey();
            boolean exactDisabled = matchesExactRule(disabledSites, site);
            boolean matchedByRule = enableAll || matchesRule(enabledRules, site);
            boolean forcedEnabled = matchesExactRule(enabledRules, site) || matchesExactRule(allowedSites, site);
            boolean matchedByExcludeKeyword = matchesKeywordRule(disabledSites, site);
            checked[i] = !exactDisabled && (forcedEnabled || (matchedByRule && !matchedByExcludeKeyword));
        }

        AlertDialog manageDialog = builder()
                .setTitle(R.string.dialog_tmdb_site_manage)
                .setMultiChoiceItems(labels, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.dialog_positive, (d, w) -> applySiteManage(sites, enabledRules, disabledSites, allowedSites, checked, enableAll))
                .setNegativeButton(R.string.dialog_negative, null)
                .show();
        LightDialog.apply(manageDialog);
    }

    private void applySiteManage(List<Site> sites, List<String> enabledRules, List<String> disabledSites, List<String> allowedSites, boolean[] checked, boolean enableAll) {
        List<String> newEnabled = new ArrayList<>();
        List<String> newAllowed = new ArrayList<>(allowedSites);
        List<String> newDisabled = new ArrayList<>(disabledSites);
        for (String rule : enabledRules) {
            if (findSite(rule) == null) newEnabled.add(rule);
        }

        for (int i = 0; i < sites.size(); i++) {
            Site site = sites.get(i);
            String key = site.getKey();
            boolean nowChecked = checked[i];
            boolean matchedByKeyword = false;
            boolean matchedByExcludeKeyword = matchesKeywordRule(disabledSites, site);

            for (String rule : enabledRules) {
                if (findSite(rule) == null && matchesRule(List.of(rule), site)) {
                    matchedByKeyword = true;
                    break;
                }
            }

            if (nowChecked) {
                removeExactRule(newDisabled, site);
                if (matchedByExcludeKeyword) {
                    if (!newAllowed.contains(key)) newAllowed.add(key);
                    continue;
                }
                newAllowed.remove(key);
                if (!enableAll && !matchedByKeyword && !newEnabled.contains(key)) {
                    newEnabled.add(key);
                }
            } else {
                newAllowed.remove(key);
                if (matchedByExcludeKeyword) continue;
                if ((enableAll || matchedByKeyword) && !newDisabled.contains(key)) {
                    newDisabled.add(key);
                }
            }
        }

        tempEnabledRules = newEnabled;
        tempAllowedSites = newAllowed;
        tempDisabledSites = newDisabled;
        updateChipsDisplay();
    }

    private boolean matchesRule(List<String> rules, Site site) {
        String key = site.getKey() == null ? "" : site.getKey().toLowerCase(Locale.ROOT);
        String name = site.getName() == null ? "" : site.getName().toLowerCase(Locale.ROOT);
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            String r = rule.trim().toLowerCase(Locale.ROOT);
            if (key.equals(r) || name.equals(r)) return true;
            if (key.contains(r) || name.contains(r)) return true;
        }
        return false;
    }

    private boolean matchesKeywordRule(List<String> rules, Site site) {
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule) || findSite(rule) != null) continue;
            if (matchesRule(List.of(rule), site)) return true;
        }
        return false;
    }

    private boolean matchesExactRule(List<String> rules, Site site) {
        String key = site.getKey() == null ? "" : site.getKey();
        String name = site.getName() == null ? "" : site.getName();
        for (String rule : rules) {
            if (TextUtils.isEmpty(rule)) continue;
            String r = rule.trim();
            if (key.equalsIgnoreCase(r) || name.equalsIgnoreCase(r)) return true;
        }
        return false;
    }

    private Site findSite(String value) {
        if (TextUtils.isEmpty(value)) return null;
        String target = value.trim();
        for (Site site : VodConfig.get().getSites()) {
            if (site == null || site.isEmpty()) continue;
            if (target.equalsIgnoreCase(site.getKey())) return site;
            if (!TextUtils.isEmpty(site.getName()) && target.equalsIgnoreCase(site.getName())) return site;
        }
        return null;
    }

    private void removeExactRule(List<String> rules, Site site) {
        if (site == null) return;
        rules.removeIf(rule -> !TextUtils.isEmpty(rule) && (rule.trim().equalsIgnoreCase(site.getKey()) || (!TextUtils.isEmpty(site.getName()) && rule.trim().equalsIgnoreCase(site.getName()))));
    }

    private String displayName(Site site) {
        return site.getDisplayName();
    }

    private String toJsonArray(List<String> values) {
        if (values == null || values.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(values.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // Chip 相关
    private void updateChipsDisplay() {
        enabledChips.removeAllViews();
        disabledChips.removeAllViews();

        if (tempEnabledRules.isEmpty()) {
            Chip chip = new Chip(activity);
            chip.setText(R.string.dialog_tmdb_all_sites);
            chip.setCheckable(false);
            chip.setCloseIconVisible(false);
            chip.setOnClickListener(v -> enableAllSites());
            enabledChips.addView(chip);
        } else {
            for (String rule : tempEnabledRules) {
                if (TextUtils.isEmpty(rule)) continue;
                enabledChips.addView(createChip(rule.trim(), false));
            }
        }
        for (String key : tempAllowedSites) {
            Site site = findSite(key);
            String name = site != null ? displayName(site) : key;
            Chip chip = createChip(name, false);
            chip.setTag(key);
            enabledChips.addView(chip);
        }

        disabledLabel.setVisibility(View.VISIBLE);
        if (tempDisabledSites.isEmpty()) {
            disabledChips.setVisibility(View.GONE);
        } else {
            disabledChips.setVisibility(View.VISIBLE);
            for (String key : tempDisabledSites) {
                Site site = findSite(key);
                String name = site != null ? displayName(site) : key;
                Chip chip = createChip(name, true);
                chip.setTag(key);
                disabledChips.addView(chip);
            }
        }
    }

    private Chip createChip(String text, boolean isDisabled) {
        Chip chip = new Chip(dialogContext == null ? activity : dialogContext);
        Site site = isDisabled ? null : findSite(text);
        chip.setText(site != null ? displayName(site) : text);
        chip.setCloseIconVisible(true);
        chip.setCheckable(false);

        if (isDisabled) {
            chip.setChipBackgroundColorResource(android.R.color.transparent);
            chip.setChipStrokeColorResource(android.R.color.holo_red_light);
            chip.setChipStrokeWidth(2f);
        }

        chip.setOnCloseIconClickListener(v -> {
            if (isDisabled) removeFromBlacklist((String) chip.getTag());
            else removeEnabledRule(chip.getTag() instanceof String ? (String) chip.getTag() : text);
        });

        return chip;
    }

    private void removeFromBlacklist(String key) {
        tempDisabledSites.remove(key);
        updateChipsDisplay();
    }

    private void removeEnabledRule(String rule) {
        Site site = findSite(rule);
        if (site != null) {
            tempEnabledRules.remove(site.getKey());
            tempAllowedSites.remove(site.getKey());
            tempEnabledRules.remove(displayName(site));
        } else {
            tempEnabledRules.remove(rule);
            tempAllowedSites.remove(rule);
        }
        updateChipsDisplay();
    }

    private void resetToDefault() {
        tempEnabledRules.clear();
        tempDisabledSites = TmdbConfig.getDefaultDisabledRules();
        tempAllowedSites.clear();
        updateChipsDisplay();
    }

    private void enableAllSites() {
        tempEnabledRules.clear();
        tempDisabledSites.clear();
        tempAllowedSites.clear();
        updateChipsDisplay();
    }

    private void addRule(EditText input) {
        String rule = input.getText().toString().trim();
        if (TextUtils.isEmpty(rule)) return;
        Site site = findSite(rule);
        String toAdd = site != null ? site.getKey() : rule;
        if (tempEnabledRules.contains(toAdd)) {
            input.setText("");
            return;
        }
        tempEnabledRules.add(toAdd);
        tempAllowedSites.remove(toAdd);
        tempDisabledSites.remove(toAdd);
        if (site != null) removeExactRule(tempDisabledSites, site);
        input.setText("");
        updateChipsDisplay();
    }

    private void addDisabledRule(EditText input) {
        String rule = input.getText().toString().trim();
        if (TextUtils.isEmpty(rule)) return;
        Site site = findSite(rule);
        String toAdd = site != null ? site.getKey() : rule;
        tempEnabledRules.remove(toAdd);
        tempAllowedSites.remove(toAdd);
        if (site != null) removeExactRule(tempEnabledRules, site);
        else removeAllowedSitesByKeyword(toAdd);
        if (!tempDisabledSites.contains(toAdd)) tempDisabledSites.add(toAdd);
        input.setText("");
        updateChipsDisplay();
    }

    private void removeAllowedSitesByKeyword(String rule) {
        if (TextUtils.isEmpty(rule)) return;
        List<String> allowed = new ArrayList<>(tempAllowedSites);
        for (String key : allowed) {
            Site site = findSite(key);
            if (site != null && matchesRule(List.of(rule), site)) tempAllowedSites.remove(key);
        }
    }

}
