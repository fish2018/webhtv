package com.fongmi.android.tv.ad.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class SignedRulePackageSource implements AdAudioRuleSource {

    private static final String SOURCE_PREFIX = "signed:";
    private static final String CACHE_RECOVERED = "CACHE_RECOVERED";
    private static final String PROBE_CACHE_RECOVERED = "PROBE_SIDECAR_CACHE_RECOVERED";
    private static final String PROBE_CACHE_WARNING = "PROBE_SIDECAR_CACHE_WARNING";
    private static final String PROBE_MISMATCH = "PROBE_SIDECAR_MISMATCH";
    private static final String PROBE_UNAVAILABLE = "PROBE_SIDECAR_UNAVAILABLE";

    private final String sourceId;
    private final Supplier<SignedRulePackageStore.LoadResult> loader;
    private final Supplier<SignedProbeRuleSidecarStore.LoadResult> sidecarLoader;

    public SignedRulePackageSource(SignedRulePackageStore store) {
        this(requireStore(store).packageId(), store::load, null);
    }

    public SignedRulePackageSource(SignedRulePackageStore store,
                                   SignedProbeRuleSidecarStore sidecarStore) {
        this(requireStore(store).packageId(), store::load,
                requireSidecarStore(sidecarStore)::load);
    }

    SignedRulePackageSource(
            String packageId, Supplier<SignedRulePackageStore.LoadResult> loader) {
        this(packageId, loader, null);
    }

    SignedRulePackageSource(
            String packageId, Supplier<SignedRulePackageStore.LoadResult> loader,
            Supplier<SignedProbeRuleSidecarStore.LoadResult> sidecarLoader) {
        if (packageId == null || packageId.isEmpty() || loader == null) {
            throw new IllegalArgumentException("package id and loader are required");
        }
        this.sourceId = SOURCE_PREFIX + packageId;
        this.loader = loader;
        this.sidecarLoader = sidecarLoader;
    }

    @Override
    public AdAudioRuleSnapshot load() {
        SignedRulePackageStore.LoadResult loaded = loader.get();
        if (!loaded.hasPackage()) {
            SignedRulePackageException.Code error = loaded.errorCode();
            String errorCode = error == null
                    ? SignedRulePackageException.Code.NO_VALID_SIGNED_PACKAGE.name()
                    : error.name();
            return new AdAudioRuleSnapshot(
                    sourceId, "", AudioFingerprintRuleSet.empty(), List.of(), errorCode);
        }

        VerifiedRulePackage verified = loaded.verifiedPackage();
        List<String> warnings = new ArrayList<>(loaded.warnings());
        if (loaded.recoveredPrevious() && !warnings.contains(CACHE_RECOVERED)) {
            warnings.add(CACHE_RECOVERED);
        }
        ProbeRuleSidecar probeSidecar = loadProbeSidecar(verified, warnings);
        String version = verified.revision() + ":" + verified.payloadSha256().substring(0, 16);
        return new AdAudioRuleSnapshot(
                sourceId, version, verified.ruleSet(), warnings, "", probeSidecar);
    }

    private ProbeRuleSidecar loadProbeSidecar(VerifiedRulePackage main,
                                               List<String> warnings) {
        if (sidecarLoader == null) return null;
        SignedProbeRuleSidecarStore.LoadResult loaded;
        try {
            loaded = sidecarLoader.get();
        } catch (RuntimeException e) {
            addWarning(warnings, PROBE_UNAVAILABLE);
            return null;
        }
        if (loaded == null || !loaded.hasPackage()) {
            addWarning(warnings, PROBE_UNAVAILABLE);
            return null;
        }
        if (loaded.recoveredPrevious()) {
            addWarning(warnings, PROBE_CACHE_RECOVERED);
        }
        if (!loaded.warnings().isEmpty()) {
            addWarning(warnings, PROBE_CACHE_WARNING);
        }
        ProbeRuleSidecar sidecar = loaded.verifiedPackage().sidecar();
        try {
            sidecar.requireBoundTo(main.packageId(), main.revision(), main.payloadDigest());
            return sidecar;
        } catch (IllegalArgumentException e) {
            addWarning(warnings, PROBE_MISMATCH);
            return null;
        }
    }

    private static void addWarning(List<String> warnings, String warning) {
        if (!warnings.contains(warning)) warnings.add(warning);
    }

    private static SignedRulePackageStore requireStore(SignedRulePackageStore store) {
        if (store == null) throw new IllegalArgumentException("store is required");
        return store;
    }

    private static SignedProbeRuleSidecarStore requireSidecarStore(
            SignedProbeRuleSidecarStore store) {
        if (store == null) throw new IllegalArgumentException("sidecar store is required");
        return store;
    }
}
