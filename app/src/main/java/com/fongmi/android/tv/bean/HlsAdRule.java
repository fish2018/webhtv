package com.fongmi.android.tv.bean;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.utils.HlsManifestCleaner;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;

public class HlsAdRule {

    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("version")
    private int version;
    @SerializedName("enabledByDefault")
    private boolean enabledByDefault;
    @SerializedName("playlistHostSuffixes")
    private List<String> playlistHostSuffixes;
    @SerializedName("playlistHostRegex")
    private List<String> playlistHostRegex;
    @SerializedName("hostSuffixes")
    private List<String> hostSuffixes;
    @SerializedName("segmentUrlRegex")
    private List<String> segmentUrlRegex;
    @SerializedName("minDuration")
    private Double minDuration;
    @SerializedName("maxDuration")
    private Double maxDuration;
    @SerializedName("requireDiscontinuity")
    private boolean requireDiscontinuity;
    @SerializedName("requireCrossDomain")
    private boolean requireCrossDomain;
    @SerializedName("minimumSignals")
    private int minimumSignals;
    @SerializedName("enabled")
    private Boolean enabled;

    public static List<HlsAdRule> arrayFrom(JsonElement element) {
        Type type = TypeToken.getParameterized(List.class, HlsAdRule.class).getType();
        List<HlsAdRule> items = App.gson().fromJson(element, type);
        return items == null ? Collections.emptyList() : items;
    }

    /**
     * 构造一条用户规则。作用域 {@code playlistHostSuffixes} 必填：
     * {@link #compile()} 会拒绝没有作用域的规则，避免规则跨站点生效。
     */
    public static HlsAdRule createUserRule(String id, String name,
                                           List<String> playlistHostSuffixes,
                                           List<String> hostSuffixes,
                                           List<String> segmentUrlRegex,
                                           Double minDuration, Double maxDuration,
                                           boolean requireDiscontinuity,
                                           boolean requireCrossDomain,
                                           int minimumSignals) {
        HlsAdRule rule = new HlsAdRule();
        rule.id = id;
        rule.name = name;
        rule.version = 1;
        rule.enabledByDefault = true;
        rule.enabled = Boolean.TRUE;
        rule.playlistHostSuffixes = playlistHostSuffixes;
        rule.hostSuffixes = hostSuffixes;
        rule.segmentUrlRegex = segmentUrlRegex;
        rule.minDuration = minDuration;
        rule.maxDuration = maxDuration;
        rule.requireDiscontinuity = requireDiscontinuity;
        rule.requireCrossDomain = requireCrossDomain;
        rule.minimumSignals = minimumSignals;
        return rule;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public String getName() { return name == null ? "" : name; }

    public int getVersion() { return version; }

    public boolean isEnabledByDefault() { return enabledByDefault; }

    public boolean isEnabled() {
        return Boolean.TRUE.equals(enabled);
    }

    public HlsManifestCleaner.Rule compile() {
        if (getId().isEmpty()) throw new IllegalArgumentException("Missing HLS rule id");
        if (!validStrings(playlistHostSuffixes) && !validStrings(playlistHostRegex)) {
            throw new IllegalArgumentException("Missing playlist host scope");
        }
        if (hostSuffixes != null && !hostSuffixes.isEmpty() && !validStrings(hostSuffixes)) throw new IllegalArgumentException("Invalid segment host");
        if (segmentUrlRegex != null && segmentUrlRegex.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("Empty HLS regex");
        }
        if ((minDuration == null) != (maxDuration == null)) throw new IllegalArgumentException("Incomplete duration range");
        if (minDuration != null && (!Double.isFinite(minDuration) || !Double.isFinite(maxDuration)
                || minDuration < 0 || maxDuration < minDuration)) throw new IllegalArgumentException("Invalid duration range");
        int signals = signalCount();
        if (signals == 0 || minimumSignals <= 0 || minimumSignals > signals) throw new IllegalArgumentException("Invalid minimum signals");
        HlsManifestCleaner.Rule.Builder builder = HlsManifestCleaner.Rule.builder()
                .playlistHostSuffixes(playlistHostSuffixes)
                .playlistHostPatterns(playlistHostRegex)
                .hostSuffixes(hostSuffixes)
                .segmentUrlPatterns(segmentUrlRegex)
                .requireDiscontinuity(requireDiscontinuity)
                .requireCrossDomain(requireCrossDomain)
                .minimumSignals(minimumSignals);
        if (minDuration != null && maxDuration != null) builder.durationRange(minDuration, maxDuration);
        return builder.build();
    }

    private int signalCount() {
        int count = 0;
        if (hostSuffixes != null && !hostSuffixes.isEmpty()) count++;
        if (segmentUrlRegex != null && !segmentUrlRegex.isEmpty()) count++;
        if (minDuration != null && maxDuration != null) count++;
        if (requireDiscontinuity) count++;
        if (requireCrossDomain) count++;
        return count;
    }

    private static boolean validStrings(List<String> values) {
        return values != null && !values.isEmpty() && values.stream().allMatch(value -> value != null && !value.isBlank());
    }
}
