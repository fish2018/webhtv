package com.fongmi.android.tv.utils;

import com.fongmi.android.tv.bean.Rule;
import com.github.catvod.utils.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 为去广规则计算稳定的唯一 ID，用于标识 APP 默认规则的禁用状态。
 * ID = md5(name | sorted(hosts) | sorted(regex) | sorted(exclude))
 * 对数组排序后再计算，避免配置更新时数组顺序变化导致 ID 变化。
 */
public class RuleIdUtil {

    public static String computeRuleId(Rule rule) {
        if (rule == null) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(rule.getName()).append('|');
        appendSorted(sb, rule.getHosts());
        sb.append('|');
        appendSorted(sb, rule.getRegex());
        sb.append('|');
        appendSorted(sb, rule.getExclude());
        return Util.md5(sb.toString());
    }

    private static void appendSorted(StringBuilder sb, List<String> list) {
        if (list == null || list.isEmpty()) return;
        List<String> copy = new ArrayList<>();
        for (String value : list) {
            if (value != null) copy.add(value);
        }
        Collections.sort(copy);
        for (int i = 0; i < copy.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(copy.get(i));
        }
    }
}
