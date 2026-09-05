package com.fongmi.android.tv.lab;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LabVariableDialog {

    public interface Callback {
        void onConfirm(Map<String, String> values);
    }

    public static void show(Context context, String title, List<LabModels.Variable> variables, Callback callback) {
        if (variables == null || variables.isEmpty()) {
            if (callback != null) callback.onConfirm(new HashMap<>());
            return;
        }
        ScrollView scroll = new ScrollView(context);
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(48, 32, 48, 16);
        scroll.addView(container, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        List<EditText> inputs = new ArrayList<>();
        List<SwitchMaterial> switches = new ArrayList<>();
        for (LabModels.Variable variable : variables) {
            TextView label = new TextView(context);
            label.setText(variable.name + (variable.required ? " *" : ""));
            label.setTextColor(Color.WHITE);
            label.setTextSize(15);
            container.addView(label);

            if ("switch".equals(variable.type)) {
                SwitchMaterial sw = new SwitchMaterial(context);
                sw.setChecked(!"false".equalsIgnoreCase(variable.defaultValue));
                sw.setMinimumHeight(96);
                container.addView(sw);
                switches.add(sw);
                inputs.add(null);
                continue;
            }

            String type = variable.type == null ? "text" : variable.type;
            boolean select = "select".equals(type);
            EditText edit = select ? new MaterialAutoCompleteTextView(context) : new EditText(context);
            edit.setSingleLine(!"text_multiline".equals(type));
            edit.setTextColor(Color.WHITE);
            edit.setHintTextColor(Color.parseColor("#66FFFFFF"));
            edit.setTextSize(15);
            if (variable.defaultValue != null) edit.setText(variable.defaultValue);
            if (variable.hint != null) edit.setHint(variable.hint);
            if ("number".equals(type)) {
                edit.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
            } else if ("text_multiline".equals(type)) {
                edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
                edit.setMinLines(3);
                edit.setMaxLines(6);
            } else if ("select".equals(type)) {
                String[] labels = new String[variable.options == null ? 0 : variable.options.size()];
                for (int i = 0; i < labels.length; i++) labels[i] = variable.options.get(i).label;
                if (labels.length > 0) ((MaterialAutoCompleteTextView) edit).setSimpleItems(labels);
            }
            TextInputLayout layout = new TextInputLayout(context);
            layout.addView(edit);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 24;
            container.addView(layout, lp);
            if ("file".equals(type) || "directory".equals(type)) {
                layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
                layout.setEndIconDrawable(android.R.drawable.ic_menu_myplaces);
                layout.setEndIconOnClickListener(v -> {
                    if (context instanceof Activity) {
                        LabFilePicker.pick((Activity) context, "directory".equals(type), variable.filter, path -> {
                            if (path != null && !path.isEmpty()) edit.setText(path);
                        });
                    }
                });
            }
            inputs.add(edit);
            switches.add(null);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context, com.fongmi.android.tv.R.style.Theme_App_Lab_Dialog)
                .setTitle(title)
                .setView(scroll)
                .setNegativeButton("取消", null)
                .setPositiveButton("运行", null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            Map<String, String> values = new HashMap<>();
            for (int i = 0; i < variables.size(); i++) {
                LabModels.Variable variable = variables.get(i);
                SwitchMaterial sw = switches.get(i);
                EditText edit = inputs.get(i);
                if (sw != null) {
                    values.put(variable.key, String.valueOf(sw.isChecked()));
                } else if (edit != null) {
                    String text = edit.getText() == null ? "" : edit.getText().toString().trim();
                    if (variable.required && text.isEmpty()) {
                        Toast.makeText(context, "请填写必填项: " + variable.name, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if ("select".equals(variable.type) && variable.options != null) {
                        String matched = null;
                        for (LabModels.Option option : variable.options) {
                            if (option.label != null && option.label.equals(text)) {
                                matched = option.value;
                                break;
                            }
                        }
                        values.put(variable.key, matched != null ? matched : text);
                    } else {
                        values.put(variable.key, text);
                    }
                }
            }
            if (callback != null) callback.onConfirm(values);
            dialog.dismiss();
        }));
        dialog.show();
    }
}
