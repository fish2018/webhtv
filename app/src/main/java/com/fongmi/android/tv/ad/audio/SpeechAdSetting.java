package com.fongmi.android.tv.ad.audio;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.github.catvod.utils.Prefers;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class SpeechAdSetting {
    public static final String KEY_RULES = "speech_ad_rules_v1";
    public static final String KEY_RULE_SOURCE = "speech_ad_rules_source";
    public static final String KEY_BUILTIN_ENABLED = "speech_ad_builtin_enabled";

    private static final String KEY_ENABLED = "speech_ad_enabled";
    private static final String KEY_KEYWORDS = "speech_ad_keywords";
    private static final String KEY_SKIP_SECONDS = "speech_ad_skip_seconds";
    private static final String KEY_SKIP_MODE = "speech_ad_skip_mode";
    private static final List<String> RULE_KEYS = List.of(KEY_RULES, KEY_RULE_SOURCE, KEY_BUILTIN_ENABLED);
    private static final SpeechAdRuleSet EMPTY = SpeechAdRuleSet.empty();
    private static final RuleCache CACHE = new RuleCache();
    private static volatile SpeechAdRuleSet builtinRules;
    private static volatile String lastValidationError = "";

    public enum RuleSource {
        NONE(""), USER("user"), IMPORTED("imported");

        private final String persisted;

        RuleSource(String persisted) {
            this.persisted = persisted;
        }

        static RuleSource fromPersisted(String value) {
            for (RuleSource source : values()) if (source.persisted.equals(value)) return source;
            return NONE;
        }
    }

    public record RuleSnapshot(SpeechAdRuleSet rules, SpeechAdRuleSet customRules,
                               SpeechAdRuleSet builtinRules, boolean builtinEnabled,
                               RuleSource source, String error) {
        public boolean hasError() {
            return !error.isEmpty();
        }
    }

    public static SpeechAdConfig snapshot() {
        return SpeechAdConfig.create(
                Prefers.getBoolean(KEY_ENABLED, false),
                Prefers.getString(KEY_KEYWORDS, SpeechAdConfig.DEFAULT_KEYWORDS),
                ruleSnapshot().rules(),
                Prefers.getInt(KEY_SKIP_SECONDS, 15),
                Prefers.getString(KEY_SKIP_MODE, AdSkipPolicyController.Mode.PROMPT.name()));
    }

    public static RuleSnapshot ruleSnapshot() {
        boolean enabled = isBuiltinEnabled();
        SpeechAdRuleSet builtin = EMPTY;
        if (enabled) {
            try {
                builtin = loadBuiltinRules();
            } catch (RuleDocumentException error) {
                builtin = null;
            }
        }
        RuleSnapshot result = CACHE.resolve(Prefers.getString(KEY_RULES, ""), enabled,
                RuleSource.fromPersisted(Prefers.getString(KEY_RULE_SOURCE, "")), builtin);
        String error = result.hasError() ? result.error() : lastValidationError;
        return error.isEmpty() ? result : new RuleSnapshot(result.rules(), result.customRules(),
                result.builtinRules(), result.builtinEnabled(), result.source(), error);
    }

    public static String customRulesText() {
        String text = Prefers.getString(KEY_RULES, "");
        // Corrupt external preferences must not populate an unbounded EditText.
        return text.length() <= SpeechAdRuleCodec.MAX_INPUT_BYTES
                && text.getBytes(StandardCharsets.UTF_8).length <= SpeechAdRuleCodec.MAX_INPUT_BYTES
                ? text : "";
    }

    public static String builtinRulesText() {
        return loadBuiltinRules().toText();
    }

    public static int builtinRuleCount() {
        try {
            return loadBuiltinRules().rules().size();
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public static boolean isBuiltinEnabled() {
        return Prefers.getBoolean(KEY_BUILTIN_ENABLED, false);
    }

    public static void setBuiltinEnabled(boolean enabled) {
        SharedPreferences preferences = Prefers.getPrefers();
        try {
            setBuiltinEnabled(preferences, enabled, enabled ? loadBuiltinRules() : EMPTY);
            lastValidationError = "";
        } catch (RuntimeException error) {
            recordValidationFailure(error);
            throw error;
        }
    }

    static void setBuiltinEnabled(SharedPreferences preferences, boolean enabled, SpeechAdRuleSet builtin) {
        if (enabled) merge(builtin, validatedRules(preferences.getString(KEY_RULES, "")));
        preferences.edit().putBoolean(KEY_BUILTIN_ENABLED, enabled).apply();
    }

    public static void setRulesText(String value, RuleSource source) {
        SharedPreferences preferences = Prefers.getPrefers();
        try {
            setRulesText(preferences, value, source,
                    preferences.getBoolean(KEY_BUILTIN_ENABLED, false) ? loadBuiltinRules() : EMPTY);
            lastValidationError = "";
        } catch (RuntimeException error) {
            recordValidationFailure(error);
            throw error;
        }
    }

    static void setRulesText(SharedPreferences preferences, String value, RuleSource source,
                             SpeechAdRuleSet builtin) {
        Objects.requireNonNull(source, "source");
        if (source == RuleSource.NONE) throw new RuleDocumentException("rule source is required");
        SpeechAdRuleSet custom = validatedRules(value);
        if (source == RuleSource.IMPORTED && custom.isEmpty()) {
            throw new RuleDocumentException("document has no rules; use Clear to remove rules");
        }
        if (preferences.getBoolean(KEY_BUILTIN_ENABLED, false)) merge(builtin, custom);
        // Text and provenance become visible together. apply() does not acknowledge disk durability.
        preferences.edit().putString(KEY_RULES, custom.toText())
                .putString(KEY_RULE_SOURCE, custom.isEmpty() ? "" : source.persisted).apply();
    }

    public static void clearRules() {
        Prefers.getPrefers().edit().remove(KEY_RULES).remove(KEY_RULE_SOURCE).apply();
        lastValidationError = "";
    }

    public static String canonicalize(String value) {
        return validatedRules(value).toText();
    }

    /** Called on a background worker; do not retain the URI or its permission. */
    public static void importUri(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null || uri == null) throw new RuleDocumentException("rule URI is required");
        if (!isTextSource(resolver.getType(uri), displayName(resolver, uri))) {
            throw new RuleDocumentException("rule file must be plain text (.txt)");
        }
        final String text;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IOException("rule file cannot be opened");
            text = readUtf8(input);
        }
        // A provider close failure must not be reported after publishing a new document.
        setRulesText(text, RuleSource.IMPORTED);
    }

    public static String recordValidationFailure(Throwable error) {
        return lastValidationError = safeError(error);
    }

    public static String safeError(Throwable error) {
        // Only messages originating in the bounded codec are safe. Provider exceptions may contain URIs.
        return error instanceof RuleDocumentException ? error.getMessage() : "cannot read or save rule settings";
    }

    /** Only the three V2 keys are normalized; other preference and legacy keyword contracts stay intact. */
    public static Map<String, ?> sanitizePreferences(Map<String, ?> values,
                                                      Map<String, ?> previous, boolean clear) {
        Map<String, Object> result = new HashMap<>(values);
        if (RULE_KEYS.stream().noneMatch(values::containsKey)) return result;
        Map<String, Object> candidate = new HashMap<>();
        if (!clear) candidate.putAll(previous);
        candidate.putAll(values);
        try {
            copyValidatedPreferences(candidate, result);
            lastValidationError = "";
        } catch (RuntimeException error) {
            // A malformed backup must not replace the last valid document or enable new rules.
            String reason = safeError(error);
            try {
                copyValidatedPreferences(previous, result);
            } catch (RuntimeException ignored) {
                result.put(KEY_RULES, "");
                result.put(KEY_RULE_SOURCE, "");
                result.put(KEY_BUILTIN_ENABLED, false);
            }
            lastValidationError = reason;
        }
        return result;
    }

    private static void copyValidatedPreferences(Map<String, ?> values, Map<String, Object> target) {
        Object raw = values.get(KEY_RULES);
        Object enabledValue = values.get(KEY_BUILTIN_ENABLED);
        Object sourceValue = values.get(KEY_RULE_SOURCE);
        if (raw != null && !(raw instanceof String)
                || enabledValue != null && !(enabledValue instanceof Boolean)
                || sourceValue != null && !(sourceValue instanceof String)) {
            throw new RuleDocumentException("invalid rule preference type");
        }
        SpeechAdRuleSet custom = validatedRules(raw == null ? "" : (String) raw);
        boolean enabled = Boolean.TRUE.equals(enabledValue);
        if (enabled) merge(loadBuiltinRules(), custom);
        RuleSource source = RuleSource.fromPersisted(sourceValue == null ? "" : (String) sourceValue);
        if (!custom.isEmpty() && source == RuleSource.NONE) source = RuleSource.USER;
        target.put(KEY_RULES, custom.toText());
        target.put(KEY_RULE_SOURCE, custom.isEmpty() ? "" : source.persisted);
        target.put(KEY_BUILTIN_ENABLED, enabled);
    }

    public static void setEnabled(boolean value) {
        Prefers.put(KEY_ENABLED, value);
    }

    public static void setKeywords(String value) {
        Prefers.put(KEY_KEYWORDS, String.join(",", SpeechAdKeywordSet.parse(value).values()));
    }

    public static void setSkipSeconds(int value) {
        Prefers.put(KEY_SKIP_SECONDS, Math.max(1, Math.min(120, value)));
    }

    public static void setMode(AdSkipPolicyController.Mode value) {
        Prefers.put(KEY_SKIP_MODE, Objects.requireNonNull(value, "mode").name());
    }

    private static SpeechAdRuleSet loadBuiltinRules() {
        SpeechAdRuleSet cached = builtinRules;
        if (cached != null) return cached;
        synchronized (SpeechAdSetting.class) {
            if (builtinRules != null) return builtinRules;
            try (InputStream input = App.get().getResources().openRawResource(R.raw.speech_ad_rules_v1)) {
                return builtinRules = validatedRules(readUtf8(input));
            } catch (IOException | RuntimeException error) {
                throw new RuleDocumentException("built-in rule resource is unavailable");
            }
        }
    }

    private static SpeechAdRuleSet validatedRules(String text) {
        if (text == null) throw new RuleDocumentException("rule document is required");
        try {
            return merge(EMPTY, SpeechAdRuleCodec.parse(text));
        } catch (IllegalArgumentException error) {
            throw new RuleDocumentException(error.getMessage());
        }
    }

    static SpeechAdRuleSet merge(SpeechAdRuleSet builtin, SpeechAdRuleSet custom) {
        LinkedHashMap<String, SpeechAdRule> ids = new LinkedHashMap<>();
        Map<String, SpeechAdRule> bodies = new HashMap<>();
        Map<String, String> locations = new HashMap<>();
        addRules(custom, "custom", ids, bodies, locations);
        addRules(builtin, "built-in", ids, bodies, locations);
        try {
            SpeechAdRuleSet result = ids.isEmpty() ? EMPTY : new SpeechAdRuleSet(new ArrayList<>(ids.values()));
            result.toText(); // The merged snapshot, not just each input, must fit the wire/document limit.
            return result;
        } catch (IllegalArgumentException error) {
            throw new RuleDocumentException("combined rules exceed 256 rules or 64 KiB");
        }
    }

    private static void addRules(SpeechAdRuleSet rules, String source, Map<String, SpeechAdRule> ids,
                                 Map<String, SpeechAdRule> bodies, Map<String, String> locations) {
        for (int i = 0; i < rules.rules().size(); i++) {
            SpeechAdRule rule = rules.rules().get(i);
            SpeechAdRule sameId = ids.get(rule.id());
            if (sameId != null && !sameId.canonical().equals(rule.canonical())) {
                throw new RuleDocumentException("conflicting rule id");
            }
            String body = String.join(">", rule.segments().stream().map(SpeechAdRule.Segment::canonical).toList());
            SpeechAdRule sameBody = bodies.get(body);
            String location = source + " rule " + (i + 1);
            if (sameBody != null && !sameBody.canonical().equals(rule.canonical())) {
                throw new RuleDocumentException(locations.get(body) + " / " + location
                        + ": conflicting windows; edit the document or disable built-in rules");
            }
            bodies.put(body, rule);
            locations.putIfAbsent(body, location);
            ids.putIfAbsent(rule.id(), rule);
        }
    }

    static String readUtf8(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
        byte[] buffer = new byte[4096];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) continue;
            if (count > SpeechAdRuleCodec.MAX_INPUT_BYTES - output.size()) {
                throw new RuleDocumentException("rule document is too large (maximum 64 KiB)");
            }
            output.write(buffer, 0, count);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray())).toString();
        } catch (CharacterCodingException error) {
            throw new RuleDocumentException("rule document must be UTF-8");
        }
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) {
        }
        return uri.getLastPathSegment();
    }

    static boolean isTextSource(String mimeType, String name) {
        return "text/plain".equalsIgnoreCase(mimeType)
                || name != null && name.toLowerCase(Locale.ROOT).endsWith(".txt");
    }

    static final class RuleCache {
        private String document;
        private RuleSource source;
        private boolean enabled;
        private SpeechAdRuleSet builtin;
        private RuleSnapshot snapshot;
        private RuleSnapshot good = new RuleSnapshot(EMPTY, EMPTY, EMPTY, false, RuleSource.NONE, "");

        synchronized RuleSnapshot resolve(String text, boolean useBuiltin, RuleSource origin, SpeechAdRuleSet defaults) {
            if (snapshot != null && Objects.equals(document, text) && source == origin
                    && enabled == useBuiltin && builtin == defaults) return snapshot;
            document = text;
            source = origin;
            enabled = useBuiltin;
            builtin = defaults;
            try {
                if (useBuiltin && defaults == null) throw new RuleDocumentException("built-in rule resource is unavailable");
                SpeechAdRuleSet custom = validatedRules(text);
                SpeechAdRuleSet included = useBuiltin ? defaults : EMPTY;
                RuleSource actual = custom.isEmpty() ? RuleSource.NONE
                        : origin == RuleSource.NONE ? RuleSource.USER : origin;
                good = snapshot = new RuleSnapshot(merge(included, custom), custom, included, useBuiltin, actual, "");
            } catch (RuleDocumentException error) {
                // Keep the last valid custom document on malformed external input. A built-in
                // toggle is only retained when the previous snapshot already had it enabled;
                // an explicit disable must never leave old built-ins running.
                boolean keepBuiltin = useBuiltin && good.builtinEnabled();
                SpeechAdRuleSet fallbackBuiltin = keepBuiltin ? good.builtinRules() : EMPTY;
                RuleSnapshot fallback = new RuleSnapshot(
                        merge(fallbackBuiltin, good.customRules()), good.customRules(), fallbackBuiltin,
                        keepBuiltin, good.source(), "");
                snapshot = new RuleSnapshot(fallback.rules(), fallback.customRules(), fallback.builtinRules(),
                        fallback.builtinEnabled(), fallback.source(), safeError(error));
            }
            return snapshot;
        }
    }

    private static final class RuleDocumentException extends IllegalArgumentException {
        RuleDocumentException(String message) {
            super(message);
        }
    }

    private SpeechAdSetting() {
    }
}
