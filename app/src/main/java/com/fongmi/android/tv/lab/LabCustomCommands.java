package com.fongmi.android.tv.lab;

import android.content.Context;
import android.content.SharedPreferences;

import com.fongmi.android.tv.App;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public final class LabCustomCommands {

    public static class CustomCommand {
        public String id;
        public String name;
        public String description;
        public String command;
        public boolean autoExecute;

        public CustomCommand() {
        }

        public CustomCommand(String name, String description, String command, boolean autoExecute) {
            this.id = "custom_" + System.currentTimeMillis();
            this.name = name;
            this.description = description;
            this.command = command;
            this.autoExecute = autoExecute;
        }
    }

    private static final String PREF = "lab_custom";
    private static final Gson GSON = new Gson();
    private static final Type TYPE = new TypeToken<List<CustomCommand>>() {
    }.getType();

    private LabCustomCommands() {
    }

    private static SharedPreferences sp() {
        return App.get().getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static String key(String module) {
        return "cmd_" + module;
    }

    public static List<CustomCommand> list(String module) {
        String json = sp().getString(key(module), "");
        if (json.isEmpty()) return new ArrayList<>();
        try {
            List<CustomCommand> list = GSON.fromJson(json, TYPE);
            if (list == null) return new ArrayList<>();
            boolean dirty = false;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).id == null || list.get(i).id.isEmpty()) {
                    list.get(i).id = "custom_" + System.currentTimeMillis() + "_" + i;
                    dirty = true;
                }
            }
            if (dirty) save(module, list);
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void save(String module, List<CustomCommand> list) {
        sp().edit().putString(key(module), GSON.toJson(list)).apply();
    }

    public static void add(String module, String name, String description, String command, boolean autoExecute) {
        List<CustomCommand> list = list(module);
        list.add(new CustomCommand(name, description, command, autoExecute));
        save(module, list);
    }

    public static void remove(String module, int index) {
        List<CustomCommand> list = list(module);
        if (index >= 0 && index < list.size()) {
            list.remove(index);
            save(module, list);
        }
    }

    public static void clear(String module) {
        sp().edit().remove(key(module)).apply();
    }
}
