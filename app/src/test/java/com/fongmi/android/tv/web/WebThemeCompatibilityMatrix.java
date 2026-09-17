package com.fongmi.android.tv.web;

final class WebThemeCompatibilityMatrix {

    private WebThemeCompatibilityMatrix() {
    }

    static String markdown() {
        StringBuilder result = new StringBuilder();
        result.append("# WebTheme Host API 兼容矩阵\n\n");
        result.append("<!-- 由 WebThemeCompatibilityMatrix 从运行时注册表生成；请勿手工修改表格。 -->\n\n");
        result.append("> 运行时事实源：`WebThemeCapabilityRegistry`；页面契约事实源：`WebThemePage`。\n\n");
        result.append("- Manifest Schema：`").append(WebThemeManifest.SCHEMA_VERSION).append("`\n");
        result.append("- Host API：`").append(WebThemeManifest.HOST_API_VERSION).append("`\n\n");
        result.append("## 页面契约\n\n");
        result.append("| 页面 | Manifest Key | 基础契约 | 基础权限 |\n");
        result.append("| --- | --- | --- | --- |\n");
        for (WebThemePage page : WebThemePage.values()) {
            result.append("| `").append(page.name()).append("` | `")
                    .append(page.getKey()).append("` | `")
                    .append(page.getContract()).append("` | `")
                    .append(permission(page.getContract())).append("` |\n");
        }
        result.append("\n## Bridge 能力\n\n");
        result.append("| Method | Capability ID | Permission | Pages | Contract | V1 Legacy | Manifest Required |\n");
        result.append("| --- | --- | --- | --- | ---: | --- | --- |\n");
        for (WebThemeCapabilityRegistry.CompatibilityEntry entry
                : WebThemeCapabilityRegistry.compatibilityEntries()) {
            result.append("| `").append(entry.method()).append("` | `")
                    .append(entry.capabilityId()).append("` | ")
                    .append(entry.permission().isEmpty() ? "—" : "`" + entry.permission() + "`")
                    .append(" | ").append(pages(entry)).append(" | `")
                    .append(entry.contractVersion()).append("` | ")
                    .append(entry.legacyAllowed() ? "是" : "否").append(" | ")
                    .append(entry.manifestRequired() ? "是" : "否").append(" |\n");
        }
        return result.toString();
    }

    private static String permission(String contract) {
        return contract.substring(0, contract.lastIndexOf('@'));
    }

    private static String pages(WebThemeCapabilityRegistry.CompatibilityEntry entry) {
        StringBuilder result = new StringBuilder();
        for (WebThemePage page : entry.pages()) {
            if (result.length() > 0) result.append(", ");
            result.append('`').append(page.name()).append('`');
        }
        return result.toString();
    }
}
