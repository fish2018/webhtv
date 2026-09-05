package com.fongmi.android.tv.lab;

import android.content.Context;

import com.fongmi.android.tv.App;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LabAutoStart {

    private static final AtomicBoolean STARTED = new AtomicBoolean(false);

    private LabAutoStart() {
    }

    public static void start(Context context) {
        if (!STARTED.compareAndSet(false, true)) return;
        new Thread(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
            }
            try {
                LabProcManager.recover();
                LabProcManager.updateService();
                LabModels.LabRoot root = LabConfig.get().loadSync();
                if (root == null || root.lists == null) return;
                LabEnv.syncVodPlusAssets(context);
                for (LabModels.Item item : root.lists) {
                    if (item == null || item.name == null) continue;
                    if (item.auto_install && item.available && !LabEnv.installed(context, item)) {
                        LabEnv.install(context, item, null);
                    }
                }
                for (LabModels.Item item : root.lists) {
                    if (item == null) continue;
                    if (!LabEnv.installed(context, item)) continue;
                    if (item.commands != null) {
                        for (LabModels.Command command : item.commands) {
                            if (!command.auto_execute) continue;
                            if (!command.isSupported(LabEnv.appVersionCode(context))) continue;
                            String key = item.name + "/" + command.id;
                            if (LabRunner.isRunning(key)) continue;
                            LabRunner.run(context, item, command, defaults(command), noop());
                        }
                    }
                    for (LabCustomCommands.CustomCommand custom : LabCustomCommands.list(item.name)) {
                        if (!custom.autoExecute) continue;
                        String key = item.name + "/" + (custom.id != null ? custom.id : "custom_" + System.currentTimeMillis());
                        LabRunner.runCustom(context, item, custom.command, new HashMap<>(), key, noop());
                    }
                }
            } catch (Exception ignored) {
            }
        }).start();
    }

    private static Map<String, String> defaults(LabModels.Command command) {
        Map<String, String> vars = new HashMap<>();
        if (command.variables != null) {
            for (LabModels.Variable variable : command.variables) {
                if (variable.defaultValue != null) vars.put(variable.key, variable.defaultValue);
            }
        }
        return vars;
    }

    private static LabRunner.OutputListener noop() {
        return new LabRunner.OutputListener() {
            @Override
            public void onOutput(String text) {
            }

            @Override
            public void onExit(int code) {
            }
        };
    }
}
