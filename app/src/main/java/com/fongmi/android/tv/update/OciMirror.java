package com.fongmi.android.tv.update;

import java.util.Arrays;

public final class OciMirror {

    public static final String DIRECT = "direct";
    public static final String DEFAULT = "dockerproxy_net";
    public static final String CUSTOM = "custom";

    private static final Preset[] PRESETS = {
            new Preset(DEFAULT, "dockerproxy.net", "https://dockerproxy.net"),
            new Preset("hubfast", "free.hubfast.cn", "https://free.hubfast.cn"),
            new Preset("jiaxin", "docker.jiaxin.site", "https://docker.jiaxin.site"),
            new Preset(DIRECT, "Registry direct", ""),
            new Preset(CUSTOM, "Custom", ""),
    };

    private OciMirror() {
    }

    public static Preset[] presets() {
        return Arrays.copyOf(PRESETS, PRESETS.length);
    }

    public static Preset find(String id) {
        for (Preset preset : PRESETS) if (preset.id.equals(id)) return preset;
        return PRESETS[0];
    }

    public static String resolve(String id, String customUrl, OciArtifact artifact) {
        Preset preset = find(id);
        if (DIRECT.equals(preset.id)) return artifact.getRegistryOrigin();
        if (CUSTOM.equals(preset.id)) return UpdateUrl.requireHttpsOrigin(customUrl);
        return preset.baseUrl;
    }

    public static final class Preset {

        public final String id;
        public final String label;
        public final String baseUrl;

        private Preset(String id, String label, String baseUrl) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
        }
    }
}
