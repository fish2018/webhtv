package com.fongmi.android.tv.update;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class OciAuthChallenge {

    private static final Pattern PARAM = Pattern.compile("([A-Za-z][A-Za-z0-9_-]*)=\"([^\"]*)\"");

    public final String realm;
    public final String service;
    public final String scope;

    private OciAuthChallenge(String realm, String service, String scope) {
        this.realm = realm;
        this.service = service;
        this.scope = scope;
    }

    public static OciAuthChallenge parse(String header) {
        String value = header == null ? "" : header.trim();
        int space = value.indexOf(' ');
        if (space <= 0 || !"bearer".equals(value.substring(0, space).toLowerCase(Locale.ROOT))) throw new IllegalArgumentException("Unsupported Registry authentication");
        String realm = "";
        String service = "";
        String scope = "";
        Matcher matcher = PARAM.matcher(value.substring(space + 1));
        while (matcher.find()) {
            String key = matcher.group(1).toLowerCase(Locale.ROOT);
            if ("realm".equals(key)) realm = matcher.group(2);
            else if ("service".equals(key)) service = matcher.group(2);
            else if ("scope".equals(key)) scope = matcher.group(2);
        }
        if (realm.isEmpty()) throw new IllegalArgumentException("Registry authentication realm is missing");
        return new OciAuthChallenge(realm, service, scope);
    }
}
