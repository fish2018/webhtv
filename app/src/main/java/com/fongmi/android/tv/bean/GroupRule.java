package com.fongmi.android.tv.bean;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroupRule {

    public static final String SOURCE_BUILTIN = "builtin";
    public static final String SOURCE_USER = "user";
    public static final String SOURCE_INTERFACE = "interface";
    public static final String SOURCE_AI = "ai";

    private static final int MAX_AI_MATCH_TEXT_LENGTH = 256;
    private static final int MAX_AI_NESTING_DEPTH = 8;
    private static final int MAX_AI_QUANTIFIERS_PER_DEPTH = 2;

    @SerializedName("id")
    private String id;
    @SerializedName("name")
    private String name;
    @SerializedName("regex")
    private String regex;
    @SerializedName("enabled")
    private Boolean enabled;
    @SerializedName("source")
    private String source;
    @SerializedName("wrapBracket")
    private Boolean wrapBracket;

    private transient Pattern pattern;

    public GroupRule() {
    }

    public static GroupRule createUser(String name, String regex) {
        GroupRule rule = new GroupRule();
        rule.id = UUID.randomUUID().toString();
        rule.name = name;
        rule.regex = regex;
        rule.enabled = true;
        rule.source = SOURCE_USER;
        return rule;
    }

    public static GroupRule builtin(String id, String name, String regex, boolean wrapBracket) {
        GroupRule rule = new GroupRule();
        rule.id = id;
        rule.name = name;
        rule.regex = regex;
        rule.enabled = true;
        rule.source = SOURCE_BUILTIN;
        rule.wrapBracket = wrapBracket;
        return rule;
    }

    public static GroupRule createAi(String name, String regex) {
        GroupRule rule = createUser(name, regex);
        rule.source = SOURCE_AI;
        return rule;
    }

    public static List<GroupRule> arrayFrom(JsonElement element) {
        Type type = TypeToken.getParameterized(List.class, GroupRule.class).getType();
        List<GroupRule> items = new Gson().fromJson(element, type);
        return normalize(items);
    }

    public static List<GroupRule> arrayFromJson(String json) {
        Type type = TypeToken.getParameterized(List.class, GroupRule.class).getType();
        try {
            return normalize(new Gson().fromJson(json, type));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private static List<GroupRule> normalize(List<GroupRule> items) {
        if (items == null) return new ArrayList<>();
        List<GroupRule> result = new ArrayList<>();
        for (GroupRule item : items) {
            if (item == null || TextUtils.isEmpty(item.getRegex())) continue;
            if (TextUtils.isEmpty(item.id)) item.id = UUID.randomUUID().toString();
            if (TextUtils.isEmpty(item.source)) item.source = SOURCE_INTERFACE;
            if (item.enabled == null) item.enabled = true;
            result.add(item);
        }
        return result;
    }

    public String getId() {
        return id == null ? "" : id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRegex() {
        return regex == null ? "" : regex;
    }

    public void setRegex(String regex) {
        this.regex = regex;
        this.pattern = null;
    }

    public boolean isEnabled() {
        return enabled == null || enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getSource() {
        return source == null ? SOURCE_USER : source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isBuiltin() {
        return SOURCE_BUILTIN.equals(getSource());
    }

    public boolean isUser() {
        return SOURCE_USER.equals(getSource());
    }

    public boolean isAi() {
        return SOURCE_AI.equals(getSource());
    }

    public static boolean isSafeAiRegex(String regex) {
        if (TextUtils.isEmpty(regex) || regex.length() > 256 || regex.contains("\n") || regex.contains("\r")) return false;
        int index = regex.startsWith("(?i)") ? 4 : 0;
        int depth = 0;
        int captures = 0;
        int[] quantifiersByDepth = new int[MAX_AI_NESTING_DEPTH + 1];
        boolean inClass = false;
        boolean escaped = false;
        boolean hasClassContent = false;
        boolean atom = false;
        boolean groupAtom = false;
        boolean quantified = false;
        for (int i = index; i < regex.length(); i++) {
            char c = regex.charAt(i);
            if (escaped) {
                if (Character.isDigit(c)) return false;
                if (Character.isLetter(c) && "sSdDwW".indexOf(c) < 0) return false;
                escaped = false;
                if (inClass) {
                    hasClassContent = true;
                } else {
                    atom = true;
                    groupAtom = false;
                    quantified = false;
                }
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (inClass) {
                if (c == ']' && hasClassContent) {
                    inClass = false;
                    atom = true;
                    groupAtom = false;
                    quantified = false;
                } else {
                    hasClassContent = true;
                }
                continue;
            }
            if (c == '[') {
                inClass = true;
                hasClassContent = false;
                atom = false;
                groupAtom = false;
                quantified = false;
                continue;
            }
            if (c == '.' || c == '|' || c == '{' || c == '}') return false;
            if (c == '(') {
                if (i + 1 < regex.length() && regex.charAt(i + 1) == '?') {
                    if (i + 2 >= regex.length() || regex.charAt(i + 2) != ':') return false;
                    i += 2;
                } else {
                    captures++;
                }
                depth++;
                if (depth > MAX_AI_NESTING_DEPTH) return false;
                atom = false;
                groupAtom = false;
                quantified = false;
                continue;
            }
            if (c == ')') {
                if (depth <= 0) return false;
                depth--;
                atom = true;
                groupAtom = true;
                quantified = false;
                continue;
            }
            if (c == '*' || c == '+' || c == '?') {
                if (c == '?' && quantified) {
                    quantified = false;
                    continue;
                }
                if (!atom || groupAtom || quantified) return false;
                if (++quantifiersByDepth[depth] > MAX_AI_QUANTIFIERS_PER_DEPTH) return false;
                quantified = true;
                continue;
            }
            if (c == '^' || c == '$') {
                atom = false;
                groupAtom = false;
                quantified = false;
                continue;
            }
            atom = true;
            groupAtom = false;
            quantified = false;
        }
        return !escaped && !inClass && depth == 0 && captures == 1;
    }

    public boolean isWrapBracket() {
        return Boolean.TRUE.equals(wrapBracket);
    }

    public void setWrapBracket(boolean wrapBracket) {
        this.wrapBracket = wrapBracket;
    }

    public List<String> extract(String text) {
        if (TextUtils.isEmpty(text) || TextUtils.isEmpty(getRegex())) return List.of();
        if (isAi() && text.length() > MAX_AI_MATCH_TEXT_LENGTH) return List.of();
        Pattern compiled = compile();
        if (compiled == null) return List.of();
        List<String> groups = new ArrayList<>();
        Matcher matcher = compiled.matcher(text);
        while (matcher.find()) {
            String value = capture(matcher);
            if (TextUtils.isEmpty(value)) continue;
            if (isWrapBracket() && !(value.startsWith("[") && value.endsWith("]"))) {
                value = "[" + value + "]";
            }
            if (!groups.contains(value)) groups.add(value);
        }
        return groups;
    }

    public boolean isValid() {
        return compile() != null;
    }

    private String capture(Matcher matcher) {
        if (matcher.groupCount() >= 1 && matcher.group(1) != null) return matcher.group(1).trim();
        String all = matcher.group();
        return all == null ? "" : all.trim();
    }

    private Pattern compile() {
        if (isAi() && !isSafeAiRegex(getRegex())) return null;
        if (pattern != null) return pattern;
        try {
            pattern = Pattern.compile(getRegex());
            return pattern;
        } catch (Exception e) {
            return null;
        }
    }

    public String getSummary() {
        String label = isBuiltin() ? "内置" : SOURCE_INTERFACE.equals(getSource()) ? "接口" : isAi() ? "AI" : "自定义";
        return label + " · " + getRegex();
    }
}
