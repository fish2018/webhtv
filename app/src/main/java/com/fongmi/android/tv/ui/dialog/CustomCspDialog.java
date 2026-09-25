package com.fongmi.android.tv.ui.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.SystemClock;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.AppCompatImageButton;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.LiveConfig;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.databinding.AdapterCustomCspBinding;
import com.fongmi.android.tv.databinding.DialogCustomCspBinding;
import com.fongmi.android.tv.impl.Callback;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.ui.custom.CustomTextListener;
import com.fongmi.android.tv.ui.custom.SafeScrollEditText;
import com.fongmi.android.tv.ui.custom.SettingClipboardOverlay;
import com.fongmi.android.tv.utils.FileChooser;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.github.catvod.net.OkHttp;
import com.github.catvod.utils.Path;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.textview.MaterialTextView;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CustomCspDialog extends BaseAlertDialog {

    private static final int MIN_INSERT_INDEX = 0;
    private static final int MAX_INSERT_INDEX = 9;
    private static final String KIND_WEB_HOME = "webHome";
    private static final String KIND_CSP = "csp";
    private static final String KIND_LIVE = "live";
    private static final String KIND_OTHER = "other";

    private DialogCustomCspBinding binding;
    private CustomCspSetting.Registry registry;
    private CspAdapter adapter;
    private ItemTouchHelper sortTouchHelper;
    private CustomCspSetting.Item pendingImport;
    private boolean pendingExtensionImport;
    private boolean pendingFilesImport;
    private TextInputEditText pendingFileTarget;
    private Set<String> initialItemIds = new HashSet<>();
    private final Set<String> pendingDeleteIds = new HashSet<>();
    private CspEditor editor;
    private TextInputEditText recognizeInput;
    private CustomCspSetting.Item editingItem;
    private int editingPosition = -1;
    private Runnable callback;
    private boolean enabled;
    private boolean textMode;
    private boolean editMode;
    private boolean recognizeMode;
    private boolean sortMode;
    private boolean reverseOrder;
    private boolean saved;
    private boolean jsonDirty = true;
    private boolean recognizing;
    private long lastAddTime;
    private String cachedJsonText;
    private SettingClipboardOverlay clipboardOverlay;

    public static void show(Fragment fragment, Runnable callback) {
        if (fragment == null || !fragment.isAdded() || fragment.isStateSaved() || fragment.getActivity() == null || fragment.getChildFragmentManager().isStateSaved()) return;
        CustomCspDialog dialog = new CustomCspDialog();
        dialog.callback = callback;
        dialog.show(fragment.getChildFragmentManager(), null);
    }

    public static void show(FragmentActivity activity, Runnable callback) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || activity.getSupportFragmentManager().isStateSaved()) return;
        CustomCspDialog dialog = new CustomCspDialog();
        dialog.callback = callback;
        dialog.show(activity.getSupportFragmentManager(), null);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = DialogCustomCspBinding.inflate(getLayoutInflater());
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
        boolean land = ResUtil.isLand(requireContext());
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.getDecorView().setPadding(0, 0, 0, 0);
        params.width = (int) (screenWidth * (land ? 0.76f : 0.94f));
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
        binding.contentScroll.setMaxHeight(land ? 0 : (int) (screenHeight * 0.58f));
        binding.enabled.requestFocus();
        if (clipboardOverlay == null) clipboardOverlay = SettingClipboardOverlay.attach(this, binding.getRoot());
        getDialog().setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode != KeyEvent.KEYCODE_BACK || event.getAction() != KeyEvent.ACTION_UP) return false;
            if (editMode) showList();
            else if (sortMode) setSortMode(false);
            else closeAndSave(false);
            return true;
        });
    }

    @Override
    protected void initView() {
        registry = CustomCspSetting.load();
        initialItemIds = itemIds(registry.getItems());
        adapter = new CspAdapter(new ArrayList<>(registry.getItems()));
        enabled = registry.isEnabled();
        updateEnabledText();
        updateReverseText();
        setInsertIndex(registry.getInsertIndex());
        binding.recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recycler.setItemAnimator(null);
        binding.recycler.setAdapter(adapter);
        if (Util.isMobile()) attachSortTouchHelper();
        binding.modeGroup.check(R.id.uiMode);
        syncJsonFromForm(false);
        showTextMode(false);
        showList();
    }

    @Override
    protected void initEvent() {
        binding.enabled.setOnClickListener(view -> {
            enabled = !enabled;
            markJsonDirty();
            updateEnabledText();
            adapter.notifyDataSetChanged();
        });
        binding.reverse.setOnClickListener(view -> {
            reverseOrder = !reverseOrder;
            updateReverseText();
            adapter.setReverseOrder(reverseOrder);
        });
        binding.insertMinus.setOnClickListener(view -> changeInsertIndex(-1));
        binding.insertPlus.setOnClickListener(view -> changeInsertIndex(1));
        binding.modeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.textMode && !showTextMode(true)) binding.modeGroup.check(R.id.uiMode);
            if (checkedId == R.id.uiMode && !showTextMode(false)) binding.modeGroup.check(R.id.textMode);
        });
        setupScrollableText(binding.jsonText);
        binding.add.setOnClickListener(view -> addItem());
        binding.recognize.setOnClickListener(view -> showRecognizePanel());
        binding.sort.setOnClickListener(view -> setSortMode(!sortMode));
        binding.negative.setOnClickListener(view -> {
            if (editMode) showList();
            else if (sortMode) setSortMode(false);
            else closeAndSave(false);
        });
        binding.positive.setOnClickListener(view -> onPositive());
    }

    @Override
    public void onCancel(@NonNull DialogInterface dialog) {
        save(false);
        super.onCancel(dialog);
    }

    @Override
    public void onDismiss(@NonNull DialogInterface dialog) {
        if (clipboardOverlay != null) clipboardOverlay.detach();
        clipboardOverlay = null;
        save(false);
        super.onDismiss(dialog);
    }

    private void updateEnabledText() {
        binding.enabled.setText(enabled ? R.string.setting_enable : R.string.setting_disable);
        binding.enabled.setAlpha(enabled ? 1.0f : 0.65f);
    }

    private void updateReverseText() {
        binding.reverse.setText(reverseOrder ? R.string.setting_order_normal : R.string.setting_order_reverse);
    }

    private void setupScrollableText(EditText input) {
        setupScrollableText(input, true);
    }

    private void setupScrollableText(EditText input, boolean horizontal) {
        input.setSelectAllOnFocus(false);
        input.setHorizontallyScrolling(horizontal);
        if (!(input instanceof SafeScrollEditText)) {
            input.setHorizontalScrollBarEnabled(horizontal);
            input.setVerticalScrollBarEnabled(true);
        }
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

    private void changeInsertIndex(int delta) {
        int before = getInsertIndex();
        setInsertIndex(before + delta);
        if (getInsertIndex() != before) markJsonDirty();
    }

    private void setInsertIndex(int index) {
        int value = clampInsertIndex(index);
        binding.insertIndex.setText(String.valueOf(value + 1));
        binding.insertMinus.setAlpha(value > MIN_INSERT_INDEX ? 1.0f : 0.45f);
        binding.insertPlus.setAlpha(value < MAX_INSERT_INDEX ? 1.0f : 0.45f);
    }

    private boolean showTextMode(boolean text) {
        if (editMode) return false;
        if (sortMode) setSortMode(false);
        if (text == textMode) {
            updateModeVisibility();
            return true;
        }
        if (text && !syncJsonFromForm(true)) return false;
        else if (!syncFormFromJson(true)) return false;
        textMode = text;
        updateModeVisibility();
        return true;
    }

    private void updateModeVisibility() {
        boolean listMode = !textMode && !editMode;
        boolean mobileSort = Util.isMobile() && listMode;
        binding.recycler.setVisibility(listMode ? View.VISIBLE : View.GONE);
        binding.jsonLayout.setVisibility(textMode && !editMode ? View.VISIBLE : View.GONE);
        binding.editPanel.setVisibility(editMode ? View.VISIBLE : View.GONE);
        binding.add.setVisibility(listMode && !sortMode ? View.VISIBLE : View.GONE);
        binding.recognize.setVisibility(!editMode && !sortMode ? View.VISIBLE : View.GONE);
        binding.sort.setVisibility(mobileSort ? View.VISIBLE : View.GONE);
        binding.sort.setText(sortMode ? R.string.setting_custom_csp_sort_done : R.string.setting_custom_csp_sort);
        binding.enabled.setVisibility(editMode || sortMode ? View.GONE : View.VISIBLE);
        binding.reverse.setVisibility(editMode || sortMode ? View.GONE : View.VISIBLE);
        binding.insertPanel.setVisibility(sortMode ? View.INVISIBLE : View.VISIBLE);
        binding.globalPanel.setVisibility(editMode ? View.GONE : View.VISIBLE);
        binding.modeGroup.setVisibility(editMode || sortMode ? View.GONE : View.VISIBLE);
    }

    private void setSortMode(boolean sort) {
        if (sort && (!Util.isMobile() || textMode || editMode)) return;
        if (sortMode == sort) {
            updateModeVisibility();
            return;
        }
        syncAllVisibleRows();
        if (sort && reverseOrder) {
            reverseOrder = false;
            updateReverseText();
            adapter.setReverseOrder(false);
        }
        sortMode = sort;
        adapter.setSortMode(sortMode);
        updateModeVisibility();
        if (sortMode) focusSortList();
        else binding.sort.requestFocus();
    }

    private void focusSortList() {
        binding.recycler.post(() -> {
            RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(0);
            if (holder != null) holder.itemView.requestFocus();
            else binding.recycler.requestFocus();
        });
    }

    private void attachSortTouchHelper() {
        sortTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                return sortMode ? super.getMovementFlags(recyclerView, viewHolder) : 0;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder source, @NonNull RecyclerView.ViewHolder target) {
                adapter.moveDisplay(source.getBindingAdapterPosition(), target.getBindingAdapterPosition());
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }
        });
        sortTouchHelper.attachToRecyclerView(binding.recycler);
    }

    private void addItem() {
        long now = SystemClock.elapsedRealtime();
        if (now - lastAddTime < 500) return;
        lastAddTime = now;
        CustomCspSetting.Item item = CustomCspSetting.createDefaultItem();
        item.setName(nextName(KIND_WEB_HOME));
        showEdit(item, -1);
    }

    private String nextName(String kind) {
        String prefix = getKindPrefix(kind);
        int max = 0;
        for (CustomCspSetting.Item item : adapter.getItems()) {
            if (!item.getKind().equals(kind)) continue;
            String name = item.getName();
            if (name.equals(prefix)) max = Math.max(max, 1);
            else if (name.startsWith(prefix + " ")) max = Math.max(max, parseInt(name.substring(prefix.length() + 1), 0));
        }
        int next = Math.max(1, max + 1);
        return getString(KIND_OTHER.equals(kind) ? R.string.setting_custom_csp_other_name : KIND_WEB_HOME.equals(kind) ? R.string.setting_custom_csp_webhome_name : KIND_LIVE.equals(kind) ? R.string.setting_custom_csp_live_name : R.string.setting_custom_csp_common_name, next);
    }

    private String getKindPrefix(String kind) {
        return getString(KIND_OTHER.equals(kind) ? R.string.setting_custom_csp_other : KIND_WEB_HOME.equals(kind) ? R.string.setting_custom_csp_webhome : KIND_LIVE.equals(kind) ? R.string.setting_custom_csp_live : R.string.setting_custom_csp_common);
    }

    private boolean onPositive() {
        if (recognizeMode) return saveRecognize();
        if (editMode) return saveEdit();
        return closeAndSave(true);
    }

    private boolean closeAndSave(boolean validate) {
        if (!save(validate)) return false;
        focusBeforeDismiss();
        dismiss();
        return true;
    }

    private void focusBeforeDismiss() {
        if (binding == null) return;
        View focus = binding.root.findFocus();
        if (focus != null) focus.clearFocus();
        binding.positive.requestFocus();
    }

    private void showList() {
        editMode = false;
        recognizeMode = false;
        editor = null;
        recognizeInput = null;
        editingItem = null;
        editingPosition = -1;
        binding.editPanel.removeAllViews();
        binding.negative.setText(R.string.dialog_negative);
        binding.positive.setText(R.string.dialog_positive);
        updateModeVisibility();
        adapter.notifyDataSetChanged();
        if (textMode) binding.jsonText.requestFocus();
        else binding.add.requestFocus();
    }

    private void showEdit(CustomCspSetting.Item item, int position) {
        if (sortMode) setSortMode(false);
        syncAllVisibleRows();
        editMode = true;
        editingPosition = position;
        editingItem = copy(item);
        binding.editPanel.removeAllViews();
        AdapterCustomCspBinding form = AdapterCustomCspBinding.inflate(LayoutInflater.from(requireContext()), binding.editPanel, false);
        binding.editPanel.addView(form.getRoot());
        editor = new CspEditor(form);
        editor.bind(editingItem);
        binding.negative.setText(R.string.playback_webhook_back);
        binding.positive.setText(R.string.playback_webhook_save);
        updateModeVisibility();
        binding.contentScroll.scrollTo(0, 0);
        form.name.requestFocus();
    }

    private void showOtherEdit(CustomCspSetting.Item item, int position) {
        syncAllVisibleRows();
        CustomCspSetting.Item editing = copy(item);
        List<String> fields = new ArrayList<>(CustomCspSetting.otherFields());
        String initialField = TextUtils.isEmpty(editing.getOtherKey()) ? fields.get(0) : editing.getOtherKey();
        if (!fields.contains(initialField)) fields.add(0, initialField);
        String[] selectedField = {initialField};
        JsonElement[] selectedValue = {editing.getOtherValue()};
        TextInputEditText input = createInput(true);
        TextInputLayout valueLayout = createOtherValueLayout(input);
        MaterialButton fieldButton = createOtherFieldButton(selectedField[0]);
        fieldButton.setOnClickListener(view -> ChoiceDialog.showSingle(this, R.string.setting_custom_csp_other_fields, fields.toArray(new String[0]), fields.indexOf(selectedField[0]), which -> {
            if (TextUtils.equals(selectedField[0], fields.get(which))) return;
            selectedField[0] = fields.get(which);
            selectedValue[0] = CustomCspSetting.defaultOtherValue(selectedField[0]);
            fieldButton.setText(selectedField[0]);
            updateOtherInput(input, valueLayout, selectedField[0], selectedValue[0]);
        }));
        updateOtherInput(input, valueLayout, selectedField[0], selectedValue[0]);
        final Dialog[] holder = new Dialog[1];
        holder[0] = LightDialog.create(requireContext(), getString(R.string.setting_custom_csp_other), createOtherEditPanel(fieldButton, valueLayout), getString(R.string.dialog_positive), view -> {
            try {
                editing.setOther(selectedField[0], parseOtherInput(selectedField[0], input.getText().toString()));
                if (position >= 0) adapter.replace(position, editing);
                else adapter.add(editing);
                holder[0].dismiss();
            } catch (Exception e) {
                Notify.show(R.string.setting_custom_csp_json_invalid);
            }
        }, getString(R.string.dialog_negative), null);
        showManualCloseDialog(holder[0]);
    }

    private boolean saveEdit() {
        if (editor == null || editingItem == null) return false;
        editor.sync();
        if (editingItem.hasInvalidExtensions()) {
            Notify.show(R.string.setting_custom_csp_extensions_invalid);
            return false;
        }
        if (editor.hasInvalidOther()) {
            Notify.show(R.string.setting_custom_csp_json_invalid);
            return false;
        }
        int target;
        if (editingPosition >= 0) {
            adapter.replace(editingPosition, editingItem);
            target = adapter.displayPosition(editingPosition);
        } else {
            target = adapter.add(editingItem);
        }
        showList();
        scrollToItem(target);
        return true;
    }

    private void focusBeforeRemove(View removed) {
        if (binding == null || removed == null) return;
        View focus = binding.root.findFocus();
        if (isDescendant(focus, removed)) {
            focus.clearFocus();
            binding.add.requestFocus();
        }
    }

    private boolean isDescendant(View child, View parent) {
        if (child == null || parent == null) return false;
        if (child == parent) return true;
        ViewParent viewParent = child.getParent();
        while (viewParent instanceof View) {
            if (viewParent == parent) return true;
            viewParent = viewParent.getParent();
        }
        return false;
    }

    private boolean save(boolean validate) {
        if (saved) return true;
        if (textMode && !syncFormFromJson(validate)) {
            if (validate) return false;
            saved = true;
            return true;
        }
        syncAllVisibleRows();
        if (validate && adapter.hasInvalidExtensions()) {
            Notify.show(R.string.setting_custom_csp_extensions_invalid);
            return false;
        }
        registry.setEnabled(enabled);
        registry.setInsertIndex(getInsertIndex());
        registry.setItems(new ArrayList<>(adapter.getItems()));
        try {
            CustomCspSetting.save(registry);
            cleanupDeletedFiles(registry);
        } catch (Exception e) {
            Notify.show(e.getMessage());
            return false;
        }
        reloadConfigs();
        if (callback != null) callback.run();
        saved = true;
        return true;
    }

    private void reloadConfigs() {
        VodConfig.get().clear("custom-csp-reload").config(VodConfig.get().getConfig()).load(new Callback() {
        });
        if (LiveConfig.hasLoadedLives() || !LiveConfig.get().getConfig().isEmpty() || CustomCspSetting.hasLives()) LiveConfig.get().clear().config(LiveConfig.get().getConfig()).load(new Callback() {
        });
    }

    private boolean syncJsonFromForm(boolean validate) {
        syncAllVisibleRows();
        if (validate && adapter.hasInvalidExtensions()) {
            Notify.show(R.string.setting_custom_csp_extensions_invalid);
            return false;
        }
        registry.setEnabled(enabled);
        registry.setInsertIndex(getInsertIndex());
        registry.setItems(new ArrayList<>(adapter.getItems()));
        if (jsonDirty || cachedJsonText == null) {
            cachedJsonText = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(registry.normalize());
            jsonDirty = false;
        }
        setText(binding.jsonText, cachedJsonText);
        return true;
    }

    private boolean syncFormFromJson(boolean validate) {
        String text = binding.jsonText.getText() == null ? "" : binding.jsonText.getText().toString().trim();
        try {
            registry = TextUtils.isEmpty(text) ? new CustomCspSetting.Registry() : CustomCspSetting.parse(text);
        } catch (Exception e) {
            if (validate) Notify.show(R.string.setting_custom_csp_json_invalid);
            return false;
        }
        adapter.setItems(new ArrayList<>(registry.getItems()));
        enabled = registry.isEnabled();
        updateEnabledText();
        setInsertIndex(registry.getInsertIndex());
        return true;
    }

    private void showRecognizePanel() {
        if (sortMode) setSortMode(false);
        syncAllVisibleRows();
        editMode = true;
        recognizeMode = true;
        editor = null;
        editingItem = null;
        editingPosition = -1;
        binding.editPanel.removeAllViews();
        recognizeInput = createInput(true);
        recognizeInput.setMinLines(10);
        recognizeInput.setMaxLines(16);
        setupScrollableText(recognizeInput);
        binding.editPanel.addView(createRecognizePanel(recognizeInput));
        binding.negative.setText(R.string.playback_webhook_back);
        binding.positive.setText(R.string.dialog_positive);
        updateModeVisibility();
        binding.contentScroll.scrollTo(0, 0);
        recognizeInput.requestFocus();
    }

    private View createRecognizePanel(TextInputEditText input) {
        LinearLayoutCompat container = new LinearLayoutCompat(requireContext());
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(0, dp(4), 0, 0);
        MaterialTextView title = text(getString(R.string.setting_custom_csp_recognize_title), 18, Color.BLACK, true);
        container.addView(title, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(10);
        container.addView(createInputPanel(R.string.setting_custom_csp_recognize_hint, input), params);
        return container;
    }

    private boolean saveRecognize() {
        if (recognizeInput == null || recognizing) return false;
        String text = recognizeInput.getText() == null ? "" : recognizeInput.getText().toString();
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(text.trim())) {
            Notify.show(R.string.setting_custom_csp_recognize_empty);
            return false;
        }
        recognizing = true;
        binding.positive.setEnabled(false);
        Task.execute(() -> {
            try {
                List<CustomCspSetting.Item> items = recognizedItems(text);
                App.post(() -> finishRecognize(items, null));
            } catch (Exception e) {
                App.post(() -> finishRecognize(Collections.emptyList(), e));
            }
        });
        return true;
    }

    private void finishRecognize(List<CustomCspSetting.Item> items, Exception error) {
        if (!isAdded() || binding == null) return;
        recognizing = false;
        binding.positive.setEnabled(true);
        if (error != null || items.isEmpty()) {
            Notify.show(R.string.setting_custom_csp_recognize_invalid);
            return;
        }
        if (textMode && !syncFormFromJson(true)) return;
        else if (!textMode) syncAllVisibleRows();
        List<CustomCspSetting.Item> next = new ArrayList<>(adapter.getItems());
        int firstAdded = next.size();
        next.addAll(items);
        adapter.setItems(next);
        if (textMode) syncJsonFromForm(false);
        scrollToItem(adapter.displayPosition(reverseOrder ? next.size() - 1 : firstAdded));
        Notify.show(getString(R.string.setting_custom_csp_recognize_done, items.size()));
        showList();
    }

    private void markJsonDirty() {
        jsonDirty = true;
    }

    private void scrollToItem(int position) {
        if (position < 0) return;
        binding.recycler.post(() -> {
            binding.recycler.scrollToPosition(position);
            binding.recycler.post(() -> {
                RecyclerView.ViewHolder holder = binding.recycler.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    binding.contentScroll.smoothScrollTo(0, binding.recycler.getTop() + holder.itemView.getTop());
                    holder.itemView.requestFocus();
                }
            });
        });
    }

    private List<CustomCspSetting.Item> recognizedItems(String text) throws Exception {
        String value = stripRecognizeText(text);
        List<String> candidates = new ArrayList<>();
        addRecognizeCandidate(candidates, value);
        String stripped = stripTrailingSeparators(value);
        addRecognizeCandidate(candidates, stripped);
        String closed = closeUnbalancedJson(stripped);
        addRecognizeCandidate(candidates, closed);
        if (!closed.startsWith("[")) addRecognizeCandidate(candidates, "[" + closed + "]");
        Exception failure = null;
        for (String candidate : candidates) {
            try {
                CustomCspSetting.Registry parsed = CustomCspSetting.parse(candidate);
                List<CustomCspSetting.Item> items = new ArrayList<>(parsed.getItems());
                if (allowsRemoteLiveRecognition(candidate)) recognizeRemoteLive(items);
                items.removeIf(item -> item == null || !item.isValid());
                if (!items.isEmpty()) return items;
            } catch (Exception e) {
                failure = e;
            }
        }
        if (failure != null) throw failure;
        return Collections.emptyList();
    }

    private boolean allowsRemoteLiveRecognition(String candidate) {
        try {
            JsonElement element = CustomCspSetting.parseFlexible(candidate);
            if (hasExplicitKind(element)) return false;
            if (!element.isJsonObject()) return true;
            JsonObject object = element.getAsJsonObject();
            if (object.has("sites") || object.has("items")) return false;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasExplicitKind(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("kind") && object.get("kind").isJsonPrimitive() && !TextUtils.isEmpty(object.get("kind").getAsString())) return true;
            if (object.has("items") && hasExplicitKind(object.get("items"))) return true;
            return false;
        }
        if (!element.isJsonArray()) return false;
        for (JsonElement child : element.getAsJsonArray()) if (hasExplicitKind(child)) return true;
        return false;
    }

    private void recognizeRemoteLive(List<CustomCspSetting.Item> items) {
        for (CustomCspSetting.Item item : items) {
            if (item == null || item.isLive() || item.isWebHome() || item.isOther()) continue;
            String api = item.getApi();
            if (!CustomCspSetting.isRemoteScript(api)) continue;
            String script = OkHttp.string(api, 8000);
            if (!CustomCspSetting.hasLiveMethod(api, script)) continue;
            String ext = item.getExt();
            String jar = item.getJar();
            item.setKind(KIND_LIVE);
            item.setApi(api);
            item.setExt(ext);
            item.setJar(jar);
        }
    }

    private void addRecognizeCandidate(List<String> candidates, String value) {
        if (TextUtils.isEmpty(value) || candidates.contains(value)) return;
        candidates.add(value);
    }

    private String stripRecognizeText(String text) {
        String value = text == null ? "" : text.trim();
        value = value.replaceAll("(?m)^```[a-zA-Z0-9_-]*\\s*$", "");
        value = value.replaceAll("(?m)^```\\s*$", "");
        return value.trim();
    }

    private String stripTrailingSeparators(String text) {
        String value = text == null ? "" : text.trim();
        while (value.endsWith(",") || value.endsWith(";") || value.endsWith("；")) value = value.substring(0, value.length() - 1).trim();
        return value;
    }

    private String closeUnbalancedJson(String text) {
        String value = text == null ? "" : text.trim();
        if (TextUtils.isEmpty(value)) return value;
        List<Character> stack = new ArrayList<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == '{') {
                stack.add('}');
            } else if (c == '[') {
                stack.add(']');
            } else if (c == '}' || c == ']') {
                if (stack.isEmpty() || stack.remove(stack.size() - 1) != c) return value;
            }
        }
        if (inString || stack.isEmpty()) return value;
        StringBuilder builder = new StringBuilder(value);
        for (int i = stack.size() - 1; i >= 0; i--) builder.append(stack.get(i));
        return builder.toString();
    }

    private int getInsertIndex() {
        try {
            return clampInsertIndex(Integer.parseInt(binding.insertIndex.getText().toString().trim()) - 1);
        } catch (Exception e) {
            return MIN_INSERT_INDEX;
        }
    }

    private int clampInsertIndex(int index) {
        return Math.max(MIN_INSERT_INDEX, Math.min(MAX_INSERT_INDEX, index));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void syncAllVisibleRows() {
        if (editMode && editor != null) {
            editor.sync();
            return;
        }
        for (int i = 0; i < binding.recycler.getChildCount(); i++) {
            RecyclerView.ViewHolder holder = binding.recycler.getChildViewHolder(binding.recycler.getChildAt(i));
            if (holder instanceof CspAdapter.ViewHolder viewHolder) viewHolder.sync();
        }
    }

    private static void setText(EditText view, String text) {
        if (!TextUtils.equals(view.getText(), text)) view.setText(text);
    }

    private void clearPendingFlags() {
        pendingExtensionImport = false;
        pendingFilesImport = false;
        pendingFileTarget = null;
    }

    private void chooseFile(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        pendingImport = item;
        clearPendingFlags();
        FileChooser.from(launcher).show("text/html", new String[]{"text/html", "text/*", "application/octet-stream"});
    }

    private void chooseExtensionFile(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        pendingImport = item;
        clearPendingFlags();
        pendingExtensionImport = true;
        FileChooser.from(launcher).show("text/*", new String[]{"text/javascript", "application/javascript", "application/json", "text/css", "text/*", "application/octet-stream"});
    }

    private void chooseLocalFiles(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        pendingImport = item;
        clearPendingFlags();
        pendingFilesImport = true;
        pendingFileTarget = editor == null ? null : editor.getFileTarget();
        FileChooser.from(launcher).show("*/*", new String[]{"text/javascript", "application/javascript", "application/json", "application/java-archive", "application/octet-stream", "text/*", "*/*"}, true);
    }

    private void editCode(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        TextInputEditText input = createInput(true);
        input.setMinLines(8);
        input.setMaxLines(14);
        input.setText(Path.read(CustomCspSetting.file(item.getId(), "index.html")));
        setupScrollableText(input);
        final Dialog[] holder = new Dialog[1];
        holder[0] = LightDialog.create(requireContext(), getString(R.string.setting_custom_csp_code), createInputPanel(R.string.setting_custom_csp_code, input), getString(R.string.dialog_positive), view -> {
            saveCode(item, input.getText().toString());
            holder[0].dismiss();
        }, getString(R.string.dialog_negative), null);
        showManualCloseDialog(holder[0]);
    }

    private void editLink(CustomCspSetting.Item item) {
        syncAllVisibleRows();
        TextInputEditText input = createInput(false);
        input.setText(item.getHomePage());
        final Dialog[] holder = new Dialog[1];
        holder[0] = LightDialog.create(requireContext(), getString(R.string.setting_custom_csp_link), createInputPanel(R.string.setting_custom_csp_link, input), getString(R.string.dialog_positive), view -> {
            item.setHomePage(input.getText().toString().trim());
            markJsonDirty();
            if (editMode && editor != null && item == editingItem) editor.updateHomePage();
            else adapter.notifyDataSetChanged();
            holder[0].dismiss();
        }, getString(R.string.dialog_negative), null);
        showManualCloseDialog(holder[0]);
    }

    private void showManualCloseDialog(Dialog dialog) {
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);
        dialog.show();
    }

    private void showSortActions(int displayPosition) {
        if (!sortMode || displayPosition == RecyclerView.NO_POSITION) return;
        int index = adapter.itemIndex(displayPosition);
        if (index < 0 || index >= adapter.getItemCount()) return;
        String[] actions = {
                getString(R.string.setting_custom_csp_sort_top),
                getString(R.string.setting_custom_csp_sort_forward_five),
                getString(R.string.setting_custom_csp_sort_backward_five),
                getString(R.string.setting_custom_csp_sort_bottom),
                getString(R.string.setting_custom_csp_sort_move_to)
        };
        ChoiceDialog.showSingle(this, R.string.setting_custom_csp_sort_more, actions, -1, which -> {
            if (which == 0) moveSortItem(index, 0);
            else if (which == 1) moveSortItem(index, index - 5);
            else if (which == 2) moveSortItem(index, index + 5);
            else if (which == 3) moveSortItem(index, adapter.getItemCount() - 1);
            else showMoveToPosition(index);
        });
    }

    private void showMoveToPosition(int index) {
        if (!sortMode || index < 0 || index >= adapter.getItemCount()) return;
        TextInputEditText input = createInput(false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(index + 1));
        input.selectAll();
        final Dialog[] holder = new Dialog[1];
        holder[0] = LightDialog.create(requireContext(), getString(R.string.setting_custom_csp_sort_move_to_title), createInputPanel(getString(R.string.setting_custom_csp_sort_move_to_hint, adapter.getItemCount()), input), getString(R.string.dialog_positive), view -> {
            moveSortItem(index, parseInt(input.getText().toString(), index + 1) - 1);
            holder[0].dismiss();
        }, getString(R.string.dialog_negative), null);
        holder[0].show();
    }

    private void moveSortItem(int fromIndex, int toIndex) {
        int position = adapter.moveItemToIndex(fromIndex, clamp(toIndex, 0, adapter.getItemCount() - 1));
        scrollToItem(position);
    }

    private TextInputEditText createInput(boolean multiline) {
        TextInputEditText input = new TextInputEditText(requireContext());
        input.setSelectAllOnFocus(false);
        input.setSingleLine(!multiline);
        input.setTextColor(Color.BLACK);
        input.setHintTextColor(Color.parseColor("#666666"));
        input.setInputType(multiline ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setGravity(multiline ? Gravity.START | Gravity.TOP : Gravity.CENTER_VERTICAL);
        return input;
    }

    private View createInputPanel(int hint, TextInputEditText input) {
        return createInputPanel(getString(hint), input);
    }

    private View createInputPanel(String hint, TextInputEditText input) {
        LinearLayoutCompat container = new LinearLayoutCompat(requireContext());
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(ResUtil.dp2px(20), ResUtil.dp2px(8), ResUtil.dp2px(20), 0);
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setHint(hint);
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(ResUtil.getColor(R.color.dialog_outlined_button_stroke));
        layout.setHintTextColor(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(layout, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return container;
    }

    private View createOtherEditPanel(MaterialButton fieldButton, TextInputLayout valueLayout) {
        LinearLayoutCompat container = new LinearLayoutCompat(requireContext());
        container.setOrientation(LinearLayoutCompat.VERTICAL);
        container.setPadding(ResUtil.dp2px(20), ResUtil.dp2px(8), ResUtil.dp2px(20), 0);
        MaterialTextView label = text(getString(R.string.setting_custom_csp_other_fields), 12, Color.parseColor("#5F6368"), true);
        container.addView(label, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayoutCompat.LayoutParams buttonParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(40));
        buttonParams.topMargin = dp(4);
        container.addView(fieldButton, buttonParams);
        LinearLayoutCompat.LayoutParams inputParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = dp(10);
        container.addView(valueLayout, inputParams);
        return container;
    }

    private MaterialButton createOtherFieldButton(String field) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(field);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setMinHeight(dp(40));
        button.setMinimumHeight(dp(40));
        button.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_tonal_button_text));
        button.setBackgroundTintList(ContextCompat.getColorStateList(requireContext(), R.color.dialog_tonal_button_bg));
        return button;
    }

    private TextInputLayout createOtherValueLayout(TextInputEditText input) {
        TextInputLayout layout = new TextInputLayout(requireContext());
        layout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        layout.setBoxBackgroundColor(Color.WHITE);
        layout.setBoxStrokeColor(ResUtil.getColor(R.color.dialog_outlined_button_stroke));
        layout.setHintTextColor(ColorStateList.valueOf(Color.parseColor("#5F6368")));
        layout.addView(input, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return layout;
    }

    private void updateOtherInput(TextInputEditText input, TextInputLayout layout, String field, JsonElement value) {
        boolean stringField = CustomCspSetting.isOtherStringField(field);
        input.setSingleLine(stringField);
        input.setMinLines(stringField ? 1 : 8);
        input.setMaxLines(stringField ? 1 : 14);
        input.setGravity(stringField ? Gravity.CENTER_VERTICAL : Gravity.START | Gravity.TOP);
        input.setInputType(stringField ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(otherInputText(field, value));
        layout.setHint(stringField ? field : field + " JSON");
        setupScrollableText(input, !stringField);
    }

    private String otherInputText(String field, JsonElement value) {
        if (value == null || value.isJsonNull()) return "";
        if (CustomCspSetting.isOtherStringField(field) && value.isJsonPrimitive()) return value.getAsString();
        return pretty(value);
    }

    private JsonElement parseOtherInput(String field, String text) {
        String value = text == null ? "" : text.trim();
        if (CustomCspSetting.isOtherStringField(field)) return new JsonPrimitive(value);
        return TextUtils.isEmpty(value) ? CustomCspSetting.defaultOtherValue(field) : extractOtherField(field, CustomCspSetting.parseFlexible(value));
    }

    private JsonElement extractOtherField(String field, JsonElement element) {
        if (element != null && element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has(field)) return object.get(field).deepCopy();
            if (object.has("other") && object.get("other").isJsonObject()) {
                JsonObject other = object.getAsJsonObject("other");
                if (other.has(field)) return other.get(field).deepCopy();
            }
        }
        return element;
    }

    private void saveCode(CustomCspSetting.Item item, String code) {
        try {
            CustomCspSetting.writePage(item.getId(), code);
            item.setHomePage(CustomCspSetting.localUrl(item.getId(), "index.html"));
            markJsonDirty();
            if (editMode && editor != null && item == editingItem) editor.updateHomePage();
            else adapter.notifyDataSetChanged();
        } catch (Exception e) {
            Notify.show(e.getMessage());
        }
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || pendingImport == null) return;
        List<String> paths = FileChooser.getPathsFromIntent(result.getData());
        if (paths.isEmpty()) return;
        String path = paths.get(0);
        try {
            if (pendingExtensionImport) {
                importExtensionFile(pendingImport, path);
                clearPendingImport();
                return;
            }
            if (pendingFilesImport) {
                importLocalFiles(pendingImport, paths);
                clearPendingImport();
                return;
            }
            CustomCspSetting.copyPage(Path.local(path), pendingImport.getId());
            pendingImport.setHomePage(CustomCspSetting.localUrl(pendingImport.getId(), "index.html"));
            markJsonDirty();
            boolean editingImport = editMode && editor != null && pendingImport == editingItem;
            clearPendingImport();
            if (editingImport) editor.updateHomePage();
            else adapter.notifyDataSetChanged();
        } catch (Exception e) {
            clearPendingImport();
            Notify.show(e.getMessage());
        }
    });

    private void clearPendingImport() {
        pendingImport = null;
        clearPendingFlags();
    }

    private void importLocalFiles(CustomCspSetting.Item item, List<String> paths) {
        List<String> urls = new ArrayList<>();
        for (String path : paths) {
            String url = CustomCspSetting.copyFile(Path.local(path), item.getId());
            urls.add(url);
            SettingClipboardOverlay.record(url);
        }
        if (!urls.isEmpty() && pendingFileTarget != null) {
            setText(pendingFileTarget, urls.get(0));
            pendingFileTarget.setSelection(pendingFileTarget.length());
            pendingFileTarget.requestFocus();
            if (editor != null) editor.sync();
        }
        markJsonDirty();
        if (editMode && editor != null && item == editingItem) editor.updateValidity();
        else adapter.notifyDataSetChanged();
        Notify.show("已加入快捷粘贴板 " + urls.size() + " 条");
    }

    private void importExtensionFile(CustomCspSetting.Item item, String path) throws Exception {
        String content = Path.read(Path.local(path));
        if (TextUtils.isEmpty(content)) throw new IllegalArgumentException(getString(R.string.web_home_extension_source_empty));
        String text = extensionArrayText(item, path, content);
        item.setExtensionsExpanded(true);
        item.setExtensionsText(text);
        markJsonDirty();
        boolean editingImport = editMode && editor != null && item == editingItem;
        if (editingImport) editor.updateExtensions();
        else adapter.notifyDataSetChanged();
    }

    private String extensionArrayText(CustomCspSetting.Item item, String path, String content) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        String lower = name.toLowerCase();
        JsonArray array = new JsonArray();
        if (lower.endsWith(".json")) {
            JsonElement element = JsonParser.parseString(content.trim());
            if (element.isJsonObject() && element.getAsJsonObject().has("extensions")) element = element.getAsJsonObject().get("extensions");
            if (element.isJsonArray()) return pretty(element);
            array.add(element);
            return pretty(array);
        }
        JsonObject object = new JsonObject();
        object.addProperty("id", extensionId(item, name));
        object.addProperty("name", name);
        object.addProperty("runAt", "document-end");
        object.addProperty("sourceType", "file");
        object.addProperty("code", lower.endsWith(".css") ? "GM_addStyle(" + App.gson().toJson(content) + ");" : content);
        array.add(object);
        return pretty(array);
    }

    private String extensionId(CustomCspSetting.Item item, String name) {
        String base = (item == null ? "" : item.getKey()) + "-" + name;
        String value = base.toLowerCase().replaceAll("[^a-z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        return TextUtils.isEmpty(value) ? "local-extension" : value;
    }

    private Set<String> itemIds(List<CustomCspSetting.Item> items) {
        Set<String> ids = new HashSet<>();
        if (items == null) return ids;
        for (CustomCspSetting.Item item : items) if (item != null && !TextUtils.isEmpty(item.getId())) ids.add(item.getId());
        return ids;
    }

    private void cleanupDeletedFiles(CustomCspSetting.Registry current) {
        Set<String> currentIds = itemIds(current.getItems());
        Set<String> deleted = new HashSet<>(pendingDeleteIds);
        for (String id : initialItemIds) if (!currentIds.contains(id)) deleted.add(id);
        for (String id : deleted) CustomCspSetting.deleteFiles(id);
        initialItemIds = currentIds;
        pendingDeleteIds.clear();
    }

    private String pretty(JsonElement element) {
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(element);
    }

    private CustomCspSetting.Item copy(CustomCspSetting.Item item) {
        return App.gson().fromJson(App.gson().toJson(item), CustomCspSetting.Item.class).normalize();
    }

    private String primaryDetail(CustomCspSetting.Item item) {
        if (item.isOther()) return item.getOtherKey() + ": " + empty(shortText(item.getOtherText(), 120));
        if (item.isLive()) return getString(R.string.setting_custom_csp_live_url) + ": " + empty(item.getUrl());
        if (item.isWebHome()) return getString(R.string.setting_custom_csp_home_page) + ": " + empty(item.getHomePage());
        return getString(R.string.setting_custom_csp_api) + ": " + empty(item.getApi());
    }

    private String meta(CustomCspSetting.Item item) {
        boolean effective = effectiveEnabled(item);
        String status = effective ? getString(item.isValid() ? R.string.playback_webhook_active : R.string.playback_webhook_incomplete) : getString(R.string.setting_disable);
        if (item.isWebHome() && item.hasInvalidExtensions()) status += " · " + getString(R.string.setting_custom_csp_extensions_invalid);
        if (item.isOther()) return status + " · " + item.getOtherKey();
        if (item.isLive()) return status + " · " + getString(R.string.setting_custom_csp_player_type) + " " + empty(String.valueOf(item.getPlayerType()));
        if (item.isWebHome()) return status + " · " + getString(R.string.setting_custom_csp_extensions_toggle) + " " + (TextUtils.isEmpty(item.getExtensionsText()) ? getString(R.string.none) : getString(effective ? R.string.setting_enable : R.string.setting_disable));
        return status + " · " + getString(R.string.setting_custom_csp_type) + " " + item.getType();
    }

    private String kindName(CustomCspSetting.Item item) {
        return getString(item.isOther() ? R.string.setting_custom_csp_other : item.isLive() ? R.string.setting_custom_csp_live : item.isWebHome() ? R.string.setting_custom_csp_webhome : R.string.setting_custom_csp_common);
    }

    private String empty(String value) {
        return TextUtils.isEmpty(value) || "null".equals(value) ? getString(R.string.none) : value;
    }

    private String shortText(String value, int max) {
        if (TextUtils.isEmpty(value) || value.length() <= max) return value;
        return value.substring(0, max) + "...";
    }

    private int statusColor(CustomCspSetting.Item item) {
        if (!effectiveEnabled(item)) return Color.parseColor("#6F7378");
        return item.isValid() && !item.hasInvalidExtensions() ? Color.parseColor("#137333") : Color.parseColor("#B3261E");
    }

    private Drawable rowBackground(CustomCspSetting.Item item) {
        boolean disabled = !effectiveEnabled(item);
        StateListDrawable drawable = new StateListDrawable();
        drawable.addState(new int[]{android.R.attr.state_focused}, rowShape("#E8F0FE", "#1A73E8", 2, 8));
        drawable.addState(new int[]{android.R.attr.state_pressed}, rowShape("#E8F0FE", "#1A73E8", 2, 8));
        drawable.addState(new int[]{android.R.attr.state_activated}, rowShape("#E8F0FE", "#1A73E8", 2, 8));
        drawable.addState(new int[]{}, rowShape(disabled ? "#ECEFF1" : item.isValid() ? "#F5F6F7" : "#FFF7F7", disabled ? "#D5D9DE" : item.isValid() ? "#DADCE0" : "#F1C9C6", 1, 6));
        return drawable;
    }

    private GradientDrawable rowShape(String color, String stroke, int strokeDp, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.parseColor(color));
        drawable.setStroke(dp(strokeDp), Color.parseColor(stroke));
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private MaterialTextView text(String value, int sp, int color, boolean bold) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(sp);
        view.setSingleLine(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private MaterialTextView badge(String value, int color) {
        MaterialTextView view = text(value, 12, color, true);
        view.setGravity(Gravity.CENTER);
        view.setSingleLine(true);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setStroke(dp(1), color);
        drawable.setCornerRadius(dp(6));
        view.setBackground(drawable);
        return view;
    }

    private boolean effectiveEnabled(CustomCspSetting.Item item) {
        return enabled && item.isEnabled();
    }

    private int titleColor(boolean enabled) {
        return Color.parseColor(enabled ? "#202124" : "#8A8D91");
    }

    private int detailColor(boolean disabled) {
        return Color.parseColor(disabled ? "#9AA0A6" : "#5F6368");
    }

    private void addDetail(LinearLayoutCompat root, String value) {
        addDetail(root, value, false);
    }

    private void addDetail(LinearLayoutCompat root, String value, boolean disabled) {
        if (TextUtils.isEmpty(value)) return;
        MaterialTextView view = text(value, 12, detailColor(disabled), false);
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(3);
        root.addView(view, params);
    }

    private MaterialButton actionButton(int text, boolean tonal, boolean danger) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(text);
        button.setMinWidth(0);
        button.setMinHeight(dp(36));
        button.setMinimumHeight(dp(36));
        button.setPadding(dp(6), 0, dp(6), 0);
        ColorStateList bg = ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_bg : R.color.dialog_outlined_button_bg);
        ColorStateList fg = danger ? ColorStateList.valueOf(Color.parseColor("#B3261E")) : ContextCompat.getColorStateList(requireContext(), tonal ? R.color.dialog_tonal_button_text : R.color.dialog_outlined_button_text);
        button.setBackgroundTintList(bg);
        button.setTextColor(fg);
        if (!tonal) {
            button.setStrokeColor(ContextCompat.getColorStateList(requireContext(), R.color.dialog_outlined_button_stroke));
            button.setStrokeWidth(dp(1));
        }
        return button;
    }

    private LinearLayoutCompat.LayoutParams actionLayout(int marginStart) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(0, dp(36), 1);
        params.leftMargin = dp(marginStart);
        return params;
    }

    private AppCompatImageButton iconButton(int icon, int contentDescription, View.OnClickListener listener) {
        AppCompatImageButton button = new AppCompatImageButton(requireContext());
        button.setBackgroundResource(R.drawable.selector_dialog_switch);
        button.setImageResource(icon);
        button.setColorFilter(Color.parseColor("#5F6368"));
        button.setContentDescription(getString(contentDescription));
        button.setFocusable(true);
        button.setPadding(dp(8), dp(8), dp(8), dp(8));
        button.setOnClickListener(listener);
        return button;
    }

    private LinearLayoutCompat.LayoutParams iconLayout(int marginStart) {
        LinearLayoutCompat.LayoutParams params = new LinearLayoutCompat.LayoutParams(dp(40), dp(40));
        params.leftMargin = dp(marginStart);
        return params;
    }

    private void linkCardFocus(View card, View child) {
        child.setOnFocusChangeListener((view, hasFocus) -> card.setActivated(hasFocus || card.hasFocus()));
    }

    private int dp(int value) {
        return ResUtil.dp2px(value);
    }

    private class CspAdapter extends RecyclerView.Adapter<CspAdapter.ViewHolder> {

        private final List<CustomCspSetting.Item> items;
        private boolean reverseOrder;
        private boolean sortMode;

        CspAdapter(List<CustomCspSetting.Item> items) {
            this.items = items;
        }

        List<CustomCspSetting.Item> getItems() {
            return items;
        }

        int add(CustomCspSetting.Item item) {
            items.add(item);
            markJsonDirty();
            int position = displayPosition(items.size() - 1);
            notifyItemInserted(position);
            return position;
        }

        void replace(int position, CustomCspSetting.Item item) {
            if (position < 0 || position >= items.size()) return;
            CustomCspSetting.Item old = items.set(position, item);
            if (!old.isLive() && old.site().getKey().equals(registry.getHomeKey())) registry.setHomeKey(item.isLive() ? "" : item.site().getKey());
            markJsonDirty();
            notifyItemChanged(displayPosition(position));
        }

        void setItems(List<CustomCspSetting.Item> items) {
            this.items.clear();
            this.items.addAll(items);
            markJsonDirty();
            notifyDataSetChanged();
        }

        void setReverseOrder(boolean reverseOrder) {
            if (this.reverseOrder == reverseOrder) return;
            this.reverseOrder = reverseOrder;
            notifyDataSetChanged();
        }

        void setSortMode(boolean sortMode) {
            if (this.sortMode == sortMode) return;
            this.sortMode = sortMode;
            notifyDataSetChanged();
        }

        boolean hasInvalidExtensions() {
            for (CustomCspSetting.Item item : items) if (item.hasInvalidExtensions()) return true;
            return false;
        }

        void move(int fromPosition, int toPosition) {
            moveDisplay(fromPosition, toPosition);
        }

        int moveDisplay(int fromPosition, int toPosition) {
            if (fromPosition < 0 || toPosition < 0 || fromPosition >= items.size() || toPosition >= items.size()) return -1;
            return moveItemToIndex(itemIndex(fromPosition), itemIndex(toPosition));
        }

        int moveItemToIndex(int fromIndex, int toIndex) {
            if (fromIndex < 0 || toIndex < 0 || fromIndex >= items.size() || toIndex >= items.size()) return -1;
            if (fromIndex == toIndex) return displayPosition(toIndex);
            int fromPosition = displayPosition(fromIndex);
            int toPosition = displayPosition(toIndex);
            CustomCspSetting.Item item = items.remove(fromIndex);
            items.add(toIndex, item);
            markJsonDirty();
            notifyItemMoved(fromPosition, toPosition);
            notifyItemRangeChanged(Math.min(fromPosition, toPosition), Math.abs(fromPosition - toPosition) + 1);
            return toPosition;
        }

        void remove(int position, View removed) {
            if (position < 0 || position >= items.size()) return;
            int index = itemIndex(position);
            focusBeforeRemove(removed);
            CustomCspSetting.Item item = items.remove(index);
            if (!item.isLive() && item.site().getKey().equals(registry.getHomeKey())) registry.setHomeKey("");
            if (!TextUtils.isEmpty(item.getId())) pendingDeleteIds.add(item.getId());
            markJsonDirty();
            notifyDataSetChanged();
        }

        int itemIndex(int position) {
            return reverseOrder ? items.size() - 1 - position : position;
        }

        int displayPosition(int index) {
            if (index < 0 || index >= items.size()) return -1;
            return reverseOrder ? items.size() - 1 - index : index;
        }

        void setHome(CustomCspSetting.Item item) {
            if (item.isLive()) return;
            String key = item.site().getKey();
            registry.setHomeKey(key.equals(registry.getHomeKey()) ? "" : key);
            markJsonDirty();
            notifyDataSetChanged();
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayoutCompat root = new LinearLayoutCompat(parent.getContext());
            root.setOrientation(LinearLayoutCompat.VERTICAL);
            root.setPadding(dp(10), dp(9), dp(10), dp(9));
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.bottomMargin = dp(10);
            root.setLayoutParams(params);
            return new ViewHolder(root);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            int index = itemIndex(position);
            holder.bind(items.get(index), index);
        }

        private class ViewHolder extends RecyclerView.ViewHolder {

            private final LinearLayoutCompat root;
            private CustomCspSetting.Item item;

            ViewHolder(@NonNull LinearLayoutCompat root) {
                super(root);
                this.root = root;
            }

            void bind(CustomCspSetting.Item item, int position) {
                this.item = item;
                boolean effective = effectiveEnabled(item);
                root.removeAllViews();
                root.setBackground(rowBackground(item));
                root.setFocusable(true);
                root.setClickable(true);
                root.setOnFocusChangeListener((view, hasFocus) -> view.setActivated(hasFocus));
                root.setOnClickListener(view -> {
                    if (!sortMode) editCurrent();
                });

                LinearLayoutCompat header = new LinearLayoutCompat(requireContext());
                header.setGravity(Gravity.CENTER_VERTICAL);
                header.setOrientation(LinearLayoutCompat.HORIZONTAL);
                root.addView(header, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                MaterialTextView title = text((position + 1) + ". " + item.getName(), 15, titleColor(effective), true);
                header.addView(title, new LinearLayoutCompat.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                header.addView(badge(kindName(item), statusColor(item)));
                if (sortMode) {
                    AppCompatImageButton drag = iconButton(R.drawable.ic_action_drag, R.string.setting_custom_csp_sort_drag, null);
                    AppCompatImageButton more = iconButton(R.drawable.ic_action_more_vert, R.string.setting_custom_csp_sort_more, view -> showSortActions(getBindingAdapterPosition()));
                    drag.setOnTouchListener((view, event) -> {
                        if (event.getActionMasked() != MotionEvent.ACTION_DOWN || sortTouchHelper == null || getBindingAdapterPosition() == RecyclerView.NO_POSITION) return false;
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                        sortTouchHelper.startDrag(this);
                        return true;
                    });
                    linkCardFocus(root, drag);
                    linkCardFocus(root, more);
                    header.addView(drag, iconLayout(8));
                    header.addView(more, iconLayout(4));
                } else {
                    AppCompatImageButton up = iconButton(R.drawable.ic_subtitle_up, R.string.setting_custom_csp_up, view -> move(getBindingAdapterPosition(), getBindingAdapterPosition() - 1));
                    AppCompatImageButton down = iconButton(R.drawable.ic_subtitle_down, R.string.setting_custom_csp_down, view -> move(getBindingAdapterPosition(), getBindingAdapterPosition() + 1));
                    linkCardFocus(root, up);
                    linkCardFocus(root, down);
                    header.addView(up, iconLayout(8));
                    header.addView(down, iconLayout(4));
                }

                addDetail(root, primaryDetail(item), !effective);
                if (!item.isLive() && !item.isOther()) addDetail(root, getString(R.string.setting_custom_csp_key) + ": " + item.getKey(), !effective);
                addDetail(root, meta(item), !effective);

                if (sortMode) return;

                LinearLayoutCompat actions = new LinearLayoutCompat(requireContext());
                actions.setGravity(Gravity.CENTER_VERTICAL);
                actions.setOrientation(LinearLayoutCompat.HORIZONTAL);
                LinearLayoutCompat.LayoutParams actionParams = new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                actionParams.topMargin = dp(7);
                root.addView(actions, actionParams);

                MaterialButton toggle = actionButton(effective ? R.string.setting_enable : R.string.setting_disable, effective, false);
                toggle.setEnabled(enabled);
                toggle.setAlpha(enabled ? 1.0f : 0.55f);
                linkCardFocus(root, toggle);
                toggle.setOnClickListener(view -> {
                    int adapterPosition = getBindingAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) return;
                    item.setEnabled(!item.isEnabled());
                    notifyItemChanged(adapterPosition);
                });
                actions.addView(toggle, actionLayout(0));

                MaterialButton edit = actionButton(R.string.dialog_edit, false, false);
                linkCardFocus(root, edit);
                edit.setOnClickListener(view -> editCurrent());
                actions.addView(edit, actionLayout(8));

                if (!item.isLive() && !item.isOther()) {
                    boolean home = item.site().getKey().equals(registry.getHomeKey());
                    MaterialButton homeButton = actionButton(R.string.setting_custom_csp_home, home, false);
                    linkCardFocus(root, homeButton);
                    homeButton.setOnClickListener(view -> setHome(item));
                    actions.addView(homeButton, actionLayout(8));
                }

                MaterialButton delete = actionButton(R.string.setting_delete, false, true);
                linkCardFocus(root, delete);
                delete.setOnClickListener(view -> remove(getBindingAdapterPosition(), itemView));
                actions.addView(delete, actionLayout(8));
            }

            private void editCurrent() {
                int position = getBindingAdapterPosition();
                if (position == RecyclerView.NO_POSITION) return;
                int index = itemIndex(position);
                showEdit(items.get(index), index);
            }

            void sync() {
            }
        }
    }

    private class CspEditor {

        private final AdapterCustomCspBinding binding;
        private CustomCspSetting.Item item;
        private boolean bindingItem;
        private boolean autoName;
        private boolean autoKey;
        private boolean otherInvalid;
        private TextInputEditText fileTarget;

        CspEditor(@NonNull AdapterCustomCspBinding binding) {
            this.binding = binding;
            binding.name.addTextChangedListener(new TextSync(this));
            binding.key.addTextChangedListener(new TextSync(this));
            binding.type.addTextChangedListener(new TextSync(this));
            binding.api.addTextChangedListener(new TextSync(this));
            binding.homePage.addTextChangedListener(new TextSync(this));
            binding.extensions.addTextChangedListener(new TextSync(this));
            binding.ext.addTextChangedListener(new TextSync(this));
            binding.jar.addTextChangedListener(new TextSync(this));
            binding.click.addTextChangedListener(new TextSync(this));
            binding.playUrl.addTextChangedListener(new TextSync(this));
            binding.liveUrl.addTextChangedListener(new TextSync(this));
            binding.logo.addTextChangedListener(new TextSync(this));
            binding.epg.addTextChangedListener(new TextSync(this));
            binding.ua.addTextChangedListener(new TextSync(this));
            binding.referer.addTextChangedListener(new TextSync(this));
            binding.origin.addTextChangedListener(new TextSync(this));
            binding.timeZone.addTextChangedListener(new TextSync(this));
            binding.timeout.addTextChangedListener(new TextSync(this));
            binding.otherValue.addTextChangedListener(new TextSync(this));
            binding.enabled.setOnClickListener(view -> toggleEnabled());
            binding.typeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> onTypeChecked(checkedId, isChecked));
            binding.liveTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> onLiveTypeChecked(checkedId, isChecked));
            binding.playerTypeGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> onPlayerTypeChecked(checkedId, isChecked));
            binding.hide.setOnCheckedChangeListener((button, checked) -> sync());
            binding.searchable.setOnCheckedChangeListener((button, checked) -> sync());
            binding.changeable.setOnCheckedChangeListener((button, checked) -> sync());
            binding.quickSearch.setOnCheckedChangeListener((button, checked) -> sync());
            binding.importFile.setOnClickListener(view -> chooseFile(item));
            binding.localFiles.setOnClickListener(view -> chooseLocalFiles(item));
            binding.code.setOnClickListener(view -> editCode(item));
            binding.link.setOnClickListener(view -> editLink(item));
            binding.extensionsToggle.setOnClickListener(view -> toggleExtensions());
            binding.extensionsFile.setOnClickListener(view -> chooseExtensionFile(item));
            binding.otherField.setOnClickListener(view -> showOtherFieldMenu());
            trackFileTarget(binding.api, binding.ext, binding.jar, binding.homePage, binding.liveUrl, binding.logo, binding.epg, binding.click, binding.playUrl, binding.ua, binding.referer, binding.origin);
            binding.home.setVisibility(View.GONE);
            binding.up.setVisibility(View.GONE);
            binding.down.setVisibility(View.GONE);
            binding.delete.setVisibility(View.GONE);
            setupScrollableText(binding.extensions, false);
            setupScrollableText(binding.otherValue, true);
        }

        void bind(CustomCspSetting.Item item) {
            this.item = item;
            bindingItem = true;
            otherInvalid = false;
            autoName = isAutoName(item.getName(), item.getKind());
            autoKey = isAutoKey(item.getKey());
            binding.enabled.setAlpha(item.isEnabled() ? 1.0f : 0.65f);
            binding.enabled.setText(item.isEnabled() ? R.string.setting_enable : R.string.setting_disable);
            binding.typeGroup.check(item.isOther() ? R.id.otherMode : item.isLive() ? R.id.liveMode : item.isWebHome() ? R.id.webHomeMode : R.id.cspMode);
            setText(binding.name, item.getName());
            setText(binding.key, item.getKey());
            setText(binding.type, String.valueOf(item.getType()));
            setText(binding.api, item.getApi());
            setText(binding.homePage, item.getHomePage());
            setText(binding.extensions, item.getExtensionsText());
            setText(binding.ext, item.getExt());
            setText(binding.jar, item.getJar());
            setText(binding.click, item.getClick());
            setText(binding.playUrl, item.getPlayUrl());
            setText(binding.liveUrl, item.getUrl());
            setText(binding.logo, item.getLogo());
            setText(binding.epg, item.getEpg());
            setText(binding.ua, item.getUa());
            setText(binding.referer, item.getReferer());
            setText(binding.origin, item.getOrigin());
            setText(binding.timeZone, item.getTimeZone());
            setText(binding.timeout, item.getTimeout() == null ? "" : String.valueOf(item.getTimeout()));
            binding.liveTypeGroup.check(liveTypeId(item.getType()));
            binding.playerTypeGroup.check(playerTypeId(item.getPlayerType()));
            binding.hide.setChecked(item.getHide() == 1);
            binding.searchable.setChecked(item.getSearchable() == 1);
            binding.changeable.setChecked(item.getChangeable() == 1);
            binding.quickSearch.setChecked(item.getQuickSearch() == 1);
            updateOtherEditor();
            updateTypePanels();
            updateExtensionsToggle();
            updateExtensionsError();
            updateValidity();
            bindingItem = false;
        }

        void updateHomePage() {
            if (item != null) setText(binding.homePage, item.getHomePage());
        }

        void updateExtensions() {
            if (item == null) return;
            setText(binding.extensions, item.getExtensionsText());
            updateTypePanels();
            updateExtensionsToggle();
            updateExtensionsError();
            updateValidity();
        }

        private void toggleEnabled() {
            if (item == null) return;
            boolean checked = !item.isEnabled();
            item.setEnabled(checked);
            binding.enabled.setAlpha(checked ? 1.0f : 0.65f);
            binding.enabled.setText(checked ? R.string.setting_enable : R.string.setting_disable);
        }

        private void toggleExtensions() {
            if (bindingItem || item == null) return;
            item.setExtensionsExpanded(!item.isExtensionsExpanded());
            if (!item.isExtensionsExpanded()) setText(binding.extensions, "");
            updateTypePanels();
            updateExtensionsToggle();
            updateExtensionsError();
            sync();
        }

        private void onTypeChecked(int checkedId, boolean isChecked) {
            if (bindingItem || item == null || !isChecked) return;
            String oldKind = item.getKind();
            String newKind = checkedId == R.id.otherMode ? KIND_OTHER : checkedId == R.id.liveMode ? KIND_LIVE : checkedId == R.id.webHomeMode ? KIND_WEB_HOME : KIND_CSP;
            if (oldKind.equals(newKind)) return;
            item.setKind(newKind);
            if (KIND_OTHER.equals(newKind)) {
                ensureOtherValue();
                updateOtherEditor();
            }
            if (KIND_LIVE.equals(newKind) && !KIND_LIVE.equals(oldKind)) {
                item.setApi("");
                item.setExt("");
                item.setJar("");
                item.setClick("");
                setText(binding.api, "");
                setText(binding.ext, "");
                setText(binding.jar, "");
                setText(binding.click, "");
            }
            if (KIND_OTHER.equals(oldKind) && !KIND_OTHER.equals(newKind)) autoName = true;
            if (autoName) {
                String name = nextName(newKind);
                item.setName(name);
                setText(binding.name, name);
                setText(binding.key, item.getKey());
            }
            updateTypePanels();
            updateValidity();
        }

        private void onLiveTypeChecked(int checkedId, boolean isChecked) {
            if (bindingItem || item == null || !item.isLive() || !isChecked) return;
            item.setType(liveTypeFromId(checkedId));
            updateValidity();
        }

        private void onPlayerTypeChecked(int checkedId, boolean isChecked) {
            if (bindingItem || item == null || !item.isLive() || !isChecked) return;
            item.setPlayerType(playerTypeFromId(checkedId));
            updateValidity();
        }

        private void updateTypePanels() {
            boolean webHome = item != null && item.isWebHome();
            boolean live = item != null && item.isLive();
            boolean other = item != null && item.isOther();
            binding.webHomePanel.setVisibility(webHome ? View.VISIBLE : View.GONE);
            binding.home.setVisibility(View.GONE);
            binding.nameLayout.setVisibility(other ? View.GONE : View.VISIBLE);
            binding.apiLayout.setVisibility(webHome || live || other ? View.GONE : View.VISIBLE);
            binding.homePageLayout.setVisibility(webHome ? View.VISIBLE : View.GONE);
            binding.extensionsPanel.setVisibility(webHome ? View.VISIBLE : View.GONE);
            binding.extensionsFile.setVisibility(webHome && item.isExtensionsExpanded() ? View.VISIBLE : View.GONE);
            binding.extensionsLayout.setVisibility(webHome && item.isExtensionsExpanded() ? View.VISIBLE : View.GONE);
            binding.liveUrlLayout.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.liveTypePanel.setVisibility(View.GONE);
            binding.cspOptionsPanel.setVisibility(!live && !other ? View.VISIBLE : View.GONE);
            binding.keyLayout.setVisibility(!live && !other ? View.VISIBLE : View.GONE);
            binding.typeLayout.setVisibility(!webHome && !live && !other ? View.VISIBLE : View.GONE);
            binding.otherPanel.setVisibility(other ? View.VISIBLE : View.GONE);
            binding.liveMetaPanel.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.liveHeaderPanel.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.liveTunePanel.setVisibility(live ? View.VISIBLE : View.GONE);
            binding.flagsPanel.setVisibility(!webHome && !live && !other ? View.VISIBLE : View.GONE);
            binding.advancedPanel.setVisibility(!webHome && !live && !other ? View.VISIBLE : View.GONE);
            binding.localFiles.setVisibility(!webHome && !live && !other ? View.VISIBLE : View.GONE);
            binding.playPanel.setVisibility(!webHome && !live && !other ? View.VISIBLE : View.GONE);
            binding.playUrlLayout.setVisibility(live || other ? View.GONE : View.VISIBLE);
        }

        TextInputEditText getFileTarget() {
            if (fileTarget != null && fileTarget.isShown() && fileTarget.isEnabled()) return fileTarget;
            View focus = binding.getRoot().findFocus();
            return focus instanceof TextInputEditText input ? input : null;
        }

        private void trackFileTarget(TextInputEditText... inputs) {
            for (TextInputEditText input : inputs) {
                input.setOnFocusChangeListener((view, hasFocus) -> {
                    if (hasFocus) fileTarget = (TextInputEditText) view;
                });
            }
        }

        void sync() {
            if (item == null || bindingItem) return;
            if (item.isOther()) {
                syncOther();
                return;
            }
            String name = binding.name.getText().toString().trim();
            String key = binding.key.getText().toString().trim();
            if (!key.equals(item.getKey())) autoKey = false;
            autoName = autoName || isAutoName(item.getName(), item.getKind());
            if (!name.equals(item.getName())) autoName = false;
            item.setName(name);
            if (autoKey && !item.isLive() && !binding.key.getText().toString().trim().equals(item.getKey())) {
                bindingItem = true;
                setText(binding.key, item.getKey());
                bindingItem = false;
            }
            if (item.isLive()) {
                item.setUrl(binding.liveUrl.getText().toString().trim());
                item.setExtensionsExpanded(false);
                item.setApi(binding.api.getText().toString().trim());
                item.setExt(binding.ext.getText().toString().trim());
                item.setJar(binding.jar.getText().toString().trim());
                item.setClick(binding.click.getText().toString().trim());
                item.setLogo(binding.logo.getText().toString().trim());
                item.setEpg(binding.epg.getText().toString().trim());
                item.setUa(binding.ua.getText().toString().trim());
                item.setReferer(binding.referer.getText().toString().trim());
                item.setOrigin(binding.origin.getText().toString().trim());
                item.setTimeZone(binding.timeZone.getText().toString().trim());
                item.setTimeout(parseOptionalInt(binding.timeout.getText().toString()));
                item.setHomePage("");
                item.setPlayUrl("");
            } else if (!item.isWebHome()) {
                item.setKey(binding.key.getText().toString().trim());
                item.setExtensionsExpanded(false);
                item.setType(parseInt(binding.type.getText().toString(), 3));
                item.setApi(binding.api.getText().toString().trim());
                item.setHide(binding.hide.isChecked() ? 1 : 0);
                item.setSearchable(binding.searchable.isChecked() ? 1 : 0);
                item.setChangeable(binding.changeable.isChecked() ? 1 : 0);
                item.setQuickSearch(binding.quickSearch.isChecked() ? 1 : 0);
            }
            if (item.isLive()) {
                item.setHomePage("");
                item.setPlayUrl("");
            } else if (!item.isWebHome()) {
                item.setHomePage("");
                item.setExt(binding.ext.getText().toString().trim());
                item.setJar(binding.jar.getText().toString().trim());
                item.setClick(binding.click.getText().toString().trim());
                item.setPlayUrl(binding.playUrl.getText().toString().trim());
            } else {
                item.setKey(binding.key.getText().toString().trim());
                item.setHomePage(binding.homePage.getText().toString().trim());
                item.setExtensionsText(item.isExtensionsExpanded() ? binding.extensions.getText().toString() : "");
                item.setClick("");
                item.setPlayUrl("");
            }
            updateExtensionsToggle();
            updateExtensionsError();
            updateValidity();
        }

        boolean hasInvalidOther() {
            return otherInvalid;
        }

        private void showOtherFieldMenu() {
            if (item == null || !item.isOther()) return;
            ensureOtherValue();
            List<String> fields = new ArrayList<>(CustomCspSetting.otherFields());
            String initialField = item.getOtherKey();
            if (!fields.contains(initialField)) fields.add(0, initialField);
            ChoiceDialog.showSingle(CustomCspDialog.this, R.string.setting_custom_csp_other_fields, fields.toArray(new String[0]), fields.indexOf(initialField), which -> {
                String field = fields.get(which);
                if (TextUtils.equals(item.getOtherKey(), field)) return;
                item.setOther(field, CustomCspSetting.defaultOtherValue(field));
                updateOtherEditor();
                updateValidity();
            });
        }

        private void ensureOtherValue() {
            if (item == null || !item.isOther() || !TextUtils.isEmpty(item.getOtherKey())) return;
            String field = defaultOtherField();
            item.setOther(field, CustomCspSetting.defaultOtherValue(field));
        }

        private String defaultOtherField() {
            List<String> fields = CustomCspSetting.otherFields();
            if (fields.size() > 1) return fields.get(1);
            return fields.isEmpty() ? "parses" : fields.get(0);
        }

        private void updateOtherEditor() {
            if (item == null || !item.isOther()) return;
            ensureOtherValue();
            binding.otherField.setText(item.getOtherKey());
            boolean oldBinding = bindingItem;
            bindingItem = true;
            updateOtherInput(binding.otherValue, binding.otherValueLayout, item.getOtherKey(), item.getOtherValue());
            bindingItem = oldBinding;
            otherInvalid = false;
            binding.otherValueLayout.setError(null);
        }

        private void syncOther() {
            ensureOtherValue();
            String field = item.getOtherKey();
            try {
                item.setOther(field, parseOtherInput(field, binding.otherValue.getText().toString()));
                binding.otherValueLayout.setError(null);
                otherInvalid = false;
            } catch (Exception e) {
                binding.otherValueLayout.setError(getString(R.string.setting_custom_csp_json_invalid));
                otherInvalid = true;
            }
            updateValidity();
        }

        private int liveTypeId(int value) {
            if (value == 1) return R.id.liveType1;
            if (value == 2) return R.id.liveType2;
            return R.id.liveType0;
        }

        private int playerTypeId(Integer value) {
            if (value == null) return R.id.playerTypeUnset;
            if (value == 0) return R.id.playerType0;
            if (value == 1) return R.id.playerType1;
            return R.id.playerType2;
        }

        private int liveTypeFromId(int id) {
            if (id == R.id.liveType1) return 1;
            if (id == R.id.liveType2) return 2;
            return 0;
        }

        private Integer playerTypeFromId(int id) {
            if (id == R.id.playerTypeUnset) return null;
            if (id == R.id.playerType0) return 0;
            if (id == R.id.playerType1) return 1;
            return 2;
        }

        private boolean isAutoName(String name, String kind) {
            String prefix = getKindPrefix(kind);
            if (TextUtils.isEmpty(name)) return true;
            if (name.equals(prefix)) return true;
            return name.matches(java.util.regex.Pattern.quote(prefix) + " \\d+");
        }

        private boolean isAutoKey(String key) {
            return TextUtils.isEmpty(key) || key.startsWith("__custom_csp_");
        }

        private void updateValidity() {
            if (item == null) return;
            boolean invalid = item.isEnabled() && !item.isValid();
            binding.getRoot().setActivated(invalid);
        }

        private void updateExtensionsError() {
            binding.extensionsLayout.setError(item != null && item.hasInvalidExtensions() ? getString(R.string.setting_custom_csp_extensions_invalid) : null);
        }

        private void updateExtensionsToggle() {
            boolean expanded = item != null && item.isWebHome() && item.isExtensionsExpanded();
            binding.extensionsToggle.setSelected(expanded);
            binding.extensionsToggle.setAlpha(expanded ? 1.0f : 0.65f);
        }
    }

    private int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private Integer parseOptionalInt(String text) {
        String value = text == null ? "" : text.trim();
        if (value.isEmpty()) return null;
        return parseInt(value, 0);
    }

    private static class TextSync extends CustomTextListener {

        private final CspEditor editor;

        TextSync(CspEditor editor) {
            this.editor = editor;
        }

        @Override
        public void afterTextChanged(Editable editable) {
            editor.sync();
        }
    }
}
