package com.fongmi.android.tv.subtitle.source;

public interface SubtitleScriptRuntime {
    String init(String sourceKey, String scriptPath, String config) throws Exception;
    String search(String sourceKey, String request) throws Exception;
    String resolve(String sourceKey, String request) throws Exception;
    void destroy(String sourceKey);
}
