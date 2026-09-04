package com.fongmi.android.tv.update;

import java.util.Arrays;

public final class GithubProxy {

    public static final String DIRECT = "direct";
    public static final String CUSTOM = "custom";
    public static final String MODE_FULL_URL = "full_url";
    public static final String MODE_STRIP_SCHEME = "strip_scheme";

    private static final Preset[] PRESETS = {
            new Preset(DIRECT, "GitHub", "", MODE_FULL_URL),
            new Preset("github_chenc", "github.chenc.dev", "https://github.chenc.dev", MODE_STRIP_SCHEME),
            new Preset("gh_acmsz", "gh.acmsz.top", "https://gh.acmsz.top", MODE_FULL_URL),
            new Preset("ghfast", "ghfast.top", "https://ghfast.top", MODE_FULL_URL),
            new Preset("gh_monlor", "gh.monlor.com", "https://gh.monlor.com", MODE_FULL_URL),
            new Preset(CUSTOM, "Custom", "", MODE_FULL_URL),
    };

    private GithubProxy() {
    }

    public static Preset[] presets() {
        return Arrays.copyOf(PRESETS, PRESETS.length);
    }

    public static Preset find(String id) {
        for (Preset preset : PRESETS) if (preset.id.equals(id)) return preset;
        return PRESETS[0];
    }

    public static Config resolve(String id, String customUrl, String customMode) {
        Preset preset = find(id);
        if (DIRECT.equals(preset.id)) return new Config(DIRECT, "", MODE_FULL_URL);
        if (CUSTOM.equals(preset.id)) return new Config(CUSTOM, UpdateUrl.requireHttpsOrigin(customUrl), normalizeMode(customMode));
        return new Config(preset.id, preset.baseUrl, preset.mode);
    }

    public static String normalizeMode(String mode) {
        return MODE_STRIP_SCHEME.equals(mode) ? MODE_STRIP_SCHEME : MODE_FULL_URL;
    }

    public static final class Preset {

        public final String id;
        public final String label;
        public final String baseUrl;
        public final String mode;

        private Preset(String id, String label, String baseUrl, String mode) {
            this.id = id;
            this.label = label;
            this.baseUrl = baseUrl;
            this.mode = mode;
        }
    }

    public static final class Config {

        public final String id;
        public final String baseUrl;
        public final String mode;

        private Config(String id, String baseUrl, String mode) {
            this.id = id;
            this.baseUrl = baseUrl;
            this.mode = mode;
        }

        public String rewrite(String url) {
            String target = UpdateUrl.requireHttpsUrl(url);
            if (DIRECT.equals(id)) return target;
            if (MODE_STRIP_SCHEME.equals(mode)) return baseUrl + "/" + target.substring("https://".length());
            return baseUrl + "/" + target;
        }
    }
}
