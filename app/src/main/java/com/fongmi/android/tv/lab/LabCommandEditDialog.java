package com.fongmi.android.tv.lab;

import android.app.Activity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LabCommandEditDialog {

    private LabCommandEditDialog() {
    }

    public static void show(Activity activity, LabModels.Item item, LabModels.Command command, Runnable onSaved) {
        View root = LayoutInflater.from(activity).inflate(R.layout.dialog_add_command, null, false);
        EditText inputTitle = root.findViewById(R.id.inputTitle);
        EditText inputDesc = root.findViewById(R.id.inputDesc);
        EditText inputCommand = root.findViewById(R.id.inputCommand);
        MaterialSwitch switchAuto = root.findViewById(R.id.switchAutoExecute);
        LinearLayout variablesSection = root.findViewById(R.id.variablesSection);
        LinearLayout variablesContainer = root.findViewById(R.id.variablesContainer);
        Map<String, String> vars = new HashMap<>();
        boolean editing = command != null;

        if (editing) {
            inputTitle.setText(command.name);
            inputDesc.setText(command.description);
            inputCommand.setText(command.command);
            switchAuto.setChecked(command.auto_execute);
            if (command.hasVariables()) {
                variablesSection.setVisibility(View.VISIBLE);
                inputCommand.setFocusable(false);
                inputCommand.setFocusableInTouchMode(false);
                inputCommand.setCursorVisible(false);
                if (command.cachedVariableValues != null) vars.putAll(command.cachedVariableValues);
                for (LabModels.Variable variable : command.getVariables()) {
                    View row = inflateVariable(activity, variable, vars, inputCommand, command);
                    if (row != null) variablesContainer.addView(row);
                }
                updatePreview(inputCommand, item, command, vars);
            }
        }

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(activity, R.style.Theme_App_Lab_Dialog)
                .setTitle(editing ? "编辑命令" : "新建命令")
                .setView(root)
                .setNegativeButton("取消", null)
                .setPositiveButton(editing ? "保存" : "添加", null);
        if (editing) {
            builder.setNeutralButton("删除", (d, w) -> {
                deleteCommand(activity, item, command);
                if (onSaved != null) onSaved.run();
            });
        }
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String title = inputTitle.getText() == null ? "" : inputTitle.getText().toString().trim();
            String desc = inputDesc.getText() == null ? "" : inputDesc.getText().toString().trim();
            String cmd = inputCommand.getText() == null ? "" : inputCommand.getText().toString().trim();
            if (title.isEmpty()) {
                inputTitle.setError("请输入标题");
                return;
            }
            if (cmd.isEmpty()) {
                inputCommand.setError("请输入命令");
                return;
            }
            saveCommand(activity, item, command, title, desc, cmd, switchAuto.isChecked(), vars);
            if (onSaved != null) onSaved.run();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private static void saveCommand(Activity activity, LabModels.Item item, LabModels.Command command, String name, String description, String commandText, boolean autoExecute, Map<String, String> vars) {
        if (command == null) {
            LabCustomCommands.add(item.name, name, description, commandText, autoExecute);
            return;
        }
        if (command.id != null && command.id.startsWith("custom_")) {
            List<LabCustomCommands.CustomCommand> list = LabCustomCommands.list(item.name);
            for (int i = 0; i < list.size(); i++) {
                if (command.id.equals(list.get(i).id)) {
                    LabCustomCommands.CustomCommand updated = new LabCustomCommands.CustomCommand(name, description, commandText, autoExecute);
                    updated.id = command.id;
                    list.set(i, updated);
                    LabCustomCommands.save(item.name, list);
                    return;
                }
            }
            LabCustomCommands.add(item.name, name, description, commandText, autoExecute);
            return;
        }
        command.name = name;
        command.description = description;
        command.command = commandText;
        command.auto_execute = autoExecute;
        if (!vars.isEmpty()) command.cachedVariableValues = new HashMap<>(vars);
        LabConfig.get().saveCommandOverride(item.name, command.id, new com.google.gson.Gson().toJson(command));
    }

    private static void deleteCommand(Activity activity, LabModels.Item item, LabModels.Command command) {
        LabRunner.stop(item.name + "/" + command.id);
        if (command.id != null && command.id.startsWith("custom_")) {
            List<LabCustomCommands.CustomCommand> list = LabCustomCommands.list(item.name);
            for (int i = 0; i < list.size(); i++) {
                if (command.id.equals(list.get(i).id)) {
                    list.remove(i);
                    LabCustomCommands.save(item.name, list);
                    return;
                }
            }
        } else {
            LabConfig.get().removeCommandOverride(item.name, command.id);
        }
    }

    private static View inflateVariable(Activity activity, LabModels.Variable variable, Map<String, String> vars, EditText preview, LabModels.Command command) {
        int layout;
        switch (variable.type == null ? "text" : variable.type) {
            case "number":
                layout = R.layout.item_lab_var_number_dialog;
                break;
            case "select":
                layout = R.layout.item_lab_var_select_dialog;
                break;
            case "switch":
                layout = R.layout.item_lab_var_switch_dialog;
                break;
            case "file":
                layout = R.layout.item_lab_var_file_dialog;
                break;
            case "directory":
                layout = R.layout.item_lab_var_dir_dialog;
                break;
            case "text_multiline":
                layout = R.layout.item_lab_var_multiline_dialog;
                break;
            default:
                layout = R.layout.item_lab_var_text_dialog;
                break;
        }
        View row = LayoutInflater.from(activity).inflate(layout, null, false);
        if ("switch".equals(variable.type)) {
            TextView name = row.findViewById(R.id.name);
            TextView hint = row.findViewById(R.id.hint);
            MaterialSwitch sw = row.findViewById(R.id.switchView);
            name.setText(variable.name + (variable.required ? " *" : ""));
            if (TextUtils.isEmpty(variable.hint)) {
                hint.setVisibility(View.GONE);
            } else {
                hint.setText(variable.hint);
                hint.setVisibility(View.VISIBLE);
            }
            String value = vars.containsKey(variable.key) ? vars.get(variable.key) : variable.defaultValue;
            sw.setChecked("true".equals(value) || "1".equals(value));
            vars.put(variable.key, sw.isChecked() ? "true" : "false");
            sw.setOnCheckedChangeListener((button, checked) -> {
                vars.put(variable.key, checked ? "true" : "false");
                updatePreview(preview, null, command, vars);
            });
            return row;
        }
        TextInputLayout layoutContainer = row.findViewById(R.id.inputLayout);
        EditText edit = row.findViewById(R.id.input);
        if (edit == null) edit = row.findViewById(R.id.dropdown);
        if (edit == null) return row;
        final EditText finalEdit = edit;
        String label = variable.name + (variable.required ? " *" : "");
        if (variable.min != null && variable.max != null) label = label + " (" + variable.min + "-" + variable.max + ")";
        layoutContainer.setHint(label);
        if (!TextUtils.isEmpty(variable.hint)) layoutContainer.setPlaceholderText(variable.hint);
        String value = vars.containsKey(variable.key) ? vars.get(variable.key) : variable.defaultValue;
        if (value == null) value = "";
        finalEdit.setText(value);
        vars.put(variable.key, value);
        if ("select".equals(variable.type) && edit instanceof MaterialAutoCompleteTextView) {
            MaterialAutoCompleteTextView dropdown = (MaterialAutoCompleteTextView) edit;
            List<LabModels.Option> options = variable.options == null ? new ArrayList<LabModels.Option>() : variable.options;
            String[] labels = new String[options.size()];
            for (int i = 0; i < labels.length; i++) labels[i] = options.get(i).label;
            dropdown.setSimpleItems(labels);
            for (LabModels.Option option : options) {
                if (value.equals(option.value)) {
                    dropdown.setText(option.label, false);
                    break;
                }
            }
            dropdown.setOnItemClickListener((parent, view, position, id) -> {
                vars.put(variable.key, options.get(position).value);
                updatePreview(preview, null, command, vars);
            });
        } else {
            finalEdit.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                }

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    vars.put(variable.key, s == null ? "" : s.toString());
                    updatePreview(preview, null, command, vars);
                }
            });
        }
        if ("file".equals(variable.type) || "directory".equals(variable.type)) {
            layoutContainer.setEndIconOnClickListener(v -> LabFilePicker.pick(activity, "directory".equals(variable.type), variable.filter, path -> {
                if (path == null || path.isEmpty()) return;
                finalEdit.setText(path);
                vars.put(variable.key, path);
                updatePreview(preview, null, command, vars);
            }));
        }
        return row;
    }

    private static void updatePreview(EditText preview, LabModels.Item item, LabModels.Command command, Map<String, String> vars) {
        if (preview == null || command == null) return;
        String text = command.command;
        if (vars != null) {
            for (Map.Entry<String, String> entry : vars.entrySet()) {
                if (entry.getValue() != null) text = text.replace("{" + entry.getKey() + "}", entry.getValue());
            }
        }
        preview.setText(text);
    }
}
