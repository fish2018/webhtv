package com.fongmi.android.tv.subtitle.provider;

import com.fongmi.android.tv.subtitle.model.SubtitleAsset;
import com.fongmi.android.tv.subtitle.model.SubtitleCandidate;
import com.fongmi.android.tv.subtitle.model.SubtitleContext;
import com.fongmi.android.tv.subtitle.model.SubtitleQuery;
import com.fongmi.android.tv.subtitle.source.SubtitleProtocolMapper;
import com.fongmi.android.tv.subtitle.source.SubtitleScriptRuntime;
import com.fongmi.android.tv.subtitle.source.SubtitleSourceConfig;
import com.fongmi.android.tv.subtitle.source.SubtitleSourceEnvironment;

import java.util.List;

public final class T3SubtitleProvider implements SubtitleProvider {

    private final SubtitleSourceConfig source;
    private final SubtitleScriptRuntime runtime;
    private final String runtimeKey;
    private final com.google.gson.JsonObject templateParams;

    public T3SubtitleProvider(SubtitleSourceConfig source, SubtitleScriptRuntime runtime) {
        this(source, runtime, source == null ? "" : source.getKey());
    }

    public T3SubtitleProvider(SubtitleSourceConfig source, SubtitleScriptRuntime runtime, String runtimeKey) {
        if (source == null) throw new IllegalArgumentException("source is null");
        if (runtime == null) throw new IllegalArgumentException("runtime is null");
        this.source = source;
        this.runtime = runtime;
        this.runtimeKey = runtimeKey == null ? "" : runtimeKey;
        this.templateParams = source.getParams() == null ? new com.google.gson.JsonObject() : source.getParams().deepCopy();
    }

    public void initialize(String scriptPath) throws Exception {
        refreshSensitiveParams();
        SubtitleProtocolMapper.requireSuccess(runtime.init(runtimeKey, scriptPath, source.getParams().toString()));
    }

    public void destroy() {
        runtime.destroy(runtimeKey);
    }

    @Override
    public String getName() {
        return source.getKey();
    }

    @Override
    public String getKey() {
        return source.getKey();
    }

    @Override
    public boolean isEnabled() {
        return source.isEnabled();
    }

    @Override
    public boolean isQueryIndependent() {
        return source.isQueryIndependent();
    }

    @Override
    public List<SubtitleCandidate> search(SubtitleQuery query, SubtitleContext context) throws Exception {
        refreshSensitiveParams();
        String request = SubtitleProtocolMapper.createSearchRequest(source, query, context);
        return SubtitleProtocolMapper.parseCandidates(source, query, runtime.search(runtimeKey, request));
    }

    @Override
    public SubtitleAsset resolve(SubtitleCandidate candidate, SubtitleContext context) throws Exception {
        refreshSensitiveParams();
        String request = SubtitleProtocolMapper.createResolveRequest(source, candidate, context);
        return SubtitleProtocolMapper.parseAsset(source, candidate, runtime.resolve(runtimeKey, request));
    }

    private void refreshSensitiveParams() {
        source.setParams(SubtitleSourceEnvironment.resolve(source.getKey(), templateParams));
    }
}
