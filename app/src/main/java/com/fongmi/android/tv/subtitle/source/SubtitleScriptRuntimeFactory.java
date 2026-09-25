package com.fongmi.android.tv.subtitle.source;

public final class SubtitleScriptRuntimeFactory {

    private final SubtitleScriptRuntime pythonRuntime;

    public SubtitleScriptRuntimeFactory() {
        this(new PythonSubtitleScriptRuntime());
    }

    SubtitleScriptRuntimeFactory(SubtitleScriptRuntime pythonRuntime) {
        this.pythonRuntime = pythonRuntime;
    }

    public SubtitleScriptRuntime create(String api) {
        String value = api == null ? "" : api.trim().toLowerCase();
        if ("py".equals(value) || "python".equals(value)) return pythonRuntime;
        throw new IllegalArgumentException("Unsupported subtitle runtime: " + api);
    }
}
